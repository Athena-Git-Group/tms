package com.athena.tms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.athena.tms.config.KafkaTopicsProperties;

/**
 * Outbox relay：定期把 outbox 資料表裡 pending 的訊息送上 Kafka。
 *
 * <h2>這個類別要解決什麼問題</h2>
 *
 * 業務交易把事件寫進 outbox 表（和業務資料同一筆交易），這個類別負責「事後」
 * 把它們搬到 Kafka。搬運過程必須同時滿足三件事：
 * <ol>
 *   <li><b>同一個 aggregate_key 的訊息，送達 Kafka 的順序必須和寫入順序一致。</b>
 *       例如同一間房先後產生「dirty → inspected」兩筆 PMS 回寫，如果順序顛倒，
 *       PMS 的房況會被舊狀態蓋掉，而且事後無法自動修正。順序錯誤是不可逆的，
 *       延遲則可以等，所以設計上一律用「寧可慢、不可亂」取捨。</li>
 *   <li><b>訊息不能遺失</b>（at-least-once）。寧可重複送，由下游用 idempotency_key
 *       去重，也不要漏送。</li>
 *   <li><b>多個 instance 同時跑</b>（水平擴展）時，上面兩點依然成立。</li>
 * </ol>
 *
 * <h2>為了達成這些保證，做了哪些設計決策</h2>
 *
 * <h3>1. 同鍵串行（per-key serialization）</h3>
 * 同一個 aggregate_key 的訊息按 id 排隊，排在前面的那筆還沒 sent（包含送失敗、
 * 正在等退避重試的），後面的全部不送。不同 key 之間彼此獨立，互不影響。
 * <p>
 * 為什麼合適：Kafka 本身只保證同一個 partition 內有序，而我們用 aggregate_key
 * 當 Kafka key，所以同鍵會落在同一 partition；但這只保證「送進去的順序」會被保留，
 * 不保證「我們送進去的順序是對的」。如果前一筆失敗了、後一筆照送，就等於把順序
 * 送反。串行化是在 relay 這一側把順序釘死的唯一方法。
 *
 * <h3>2. 標成 failed 的訊息會讓整個 key 停擺，直到人工處理</h3>
 * 重試次數用盡後訊息標 failed，之後同 key 排在它後面的訊息也不會再送
 * （fetchPending 的 NOT EXISTS 子查詢負責過濾），直到有人把那筆 failed 處理掉
 * （修資料後讓它變回 pending，或確認可丟棄後直接刪掉）。其他 key 不受影響。
 * <p>
 * 為什麼合適：這是第 1 點的直接推論。一筆訊息會持續失敗多半是資料本身有問題，
 * 自動重試只會刷 log；而放任後面的訊息「跳過它」先送，就是放棄順序保證。
 * 兩害相權，寧可整個 key 卡住、讓問題被看見，也不要默默把房況弄亂。
 *
 * <h3>3. 逐租戶輪詢</h3>
 * outbox 表有 RLS（row-level security），而 pending 用的 partial index
 * idx_outbox_pending_by_key 本身沒有 tenant 前綴，所以查詢不帶 tenant 條件，
 * 改由 TenantScopedExecutor 逐租戶開交易執行。aggregate_key 不會跨租戶，
 * 因此逐租戶處理不會破壞同鍵順序。
 *
 * <h3>4. 交易切得很短，送 Kafka 完全不在交易裡</h3>
 * 撈 pending、搶鎖（claim）、標記結果各自是一個獨立的短交易；呼叫
 * KafkaTemplate.send 並等待結果這段（最長等到 sendTimeout）不在任何交易內。
 * <p>
 * 為什麼合適：一個租戶一輪可能有上百筆待送，Kafka 一旦變慢，這些等待時間會
 * 全部累積在同一次方法呼叫裡。如果把等待包在交易裡，就是拿一條 DB 連線陪 Kafka
 * 卡住；少數幾個租戶就能把連線池耗盡，連帶讓所有需要 DB 的請求（包含 RLS 查詢）
 * 一起停擺。短交易用完立刻歸還，Kafka 的故障就不會擴散到資料庫。
 *
 * <h3>5. 租約（claim-lease）必須大於 producer 可能卡住的最長時間</h3>
 * 多 instance 互斥靠「租約」：claim 到一筆訊息後，它在租約期間內對所有 instance
 * 都看起來像「正在等重試」，沒人會碰。問題是 Kafka producer 內部可能卡得比我們
 * 預期久（例如 broker 恢復中、拿不到 metadata，producer.send() 可以卡到
 * max.block.ms，預設 60 秒），如果租約比這個時間短，租約會在這台 instance 還在
 * 送的途中就過期，另一台 instance 搶走同一筆重送——兩台的送出順序完全失控，
 * 同鍵順序保證就在這裡破功。
 * <p>
 * 為什麼不是「把租約設長一點」：那是治標，因為你不知道該設多長。正確做法是先把
 * producer 能卡多久的上限釘死（在 application.properties 顯式設定 max.block.ms
 * 和 delivery.timeout.ms），讓「一筆訊息最長占用多久」變成確定的數字，再在建構子
 * 裡強制租約大於這個數字。如此租約只會在 instance 真的死掉、不會再回來的情況下
 * 過期，活著的 instance 永遠不會被半路搶走手上的訊息。
 *
 * <h2>已知但刻意不修的殘餘風險</h2>
 *
 * 上面第 5 點的前提是「活著的 instance 會持續在執行」。如果 JVM 遇到超過
 * claim-lease 的行程級停頓（GC stop-the-world 異常地久、或整個行程被 OS swap /
 * 凍結），行程沒死但暫停期間租約照樣在資料庫裡過期、被別人搶走；它恢復後手上那筆
 * 訊息的 send 可能還在進行，就回到「兩台 instance 送出順序不受控」的狀況。
 * <p>
 * 徹底解法是 fencing token：claim 時發一個遞增序號，標記結果時用序號做條件式更新，
 * 序號已經不是最新的就放棄這次標記。但這需要加欄位、改 schema。目前評估這種超長
 * 停頓的發生率遠低於「producer 卡住」這種日常會發生的狀況，先把高發的那個修好，
 * 這個列為已知風險接受。
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    /**
     * 退避倍數的上限（2^10 ≈ 1024 倍）。退避是指數成長，不封頂的話幾次失敗後就
     * 變成「幾小時後才重試」，失去短週期重試的意義。
     */
    private static final int MAX_BACKOFF_SHIFT = 10;

    private static final RowMapper<PendingMessage> PENDING_MAPPER = (rs, rowNum) -> new PendingMessage(
            rs.getLong("id"),
            rs.getLong("tenant_id"),
            rs.getString("aggregate_key"),
            rs.getString("kind"),
            rs.getString("payload"),
            rs.getInt("attempts"),
            rs.getObject("next_retry_at", OffsetDateTime.class));

    private static final RowMapper<ClaimResult> CLAIM_MAPPER = (rs, rowNum) -> {
        boolean exhausted = "failed".equals(rs.getString("status"));
        return new ClaimResult(!exhausted, exhausted, rs.getInt("attempts"));
    };

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTopicsProperties topics;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final int maxAttempts;
    private final Duration backoffBase;
    private final Duration sendTimeout;
    private final Duration claimLease;

    /**
     * 除了注入依賴，建構子還負責在啟動時驗證三個時間參數之間的關係。
     * 這些關係一旦不成立，順序保證就會在特定故障情境下悄悄失效，
     * 而且不會有任何錯誤訊息——所以寧可啟動就直接失敗。
     *
     * <p><b>檢查一：send-timeout ≥ producer 的 delivery.timeout.ms</b><br>
     * sendTimeout 是我們等 Future 的上限；delivery.timeout.ms 是 producer 自己
     * 放棄重送的上限。如果我們先放棄（sendTimeout 較短），我們會把這筆當失敗、
     * 排進退避重試，但 producer 還在背景默默重送。它之後若真的送成功了，這筆訊息
     * 就同時是「我們認為失敗」和「實際已送達」——接著我們的重送會在 Kafka 上留下
     * 一筆更晚到、但內容更舊的重複，破壞順序。
     * 用 >= 而不是 >：兩者相等時雙方幾乎同時放棄，不構成「背景還在重送」的縫隙。
     *
     * <p><b>檢查二：claim-lease > max.block.ms + send-timeout</b><br>
     * 這是一筆訊息從開始送到我們確定放棄，最長可能占用的時間（producer 取
     * metadata 可能卡住的時間 + 我們等待的時間）。因為檢查一已經保證 sendTimeout
     * 涵蓋了 producer 內部的送出與重試，這個總和就是真正的 worst case。
     * 租約必須大於它，活著的 instance 才不會被誤判為當掉、被別人搶走正在處理的訊息。
     * 詳細推理見類別註解第 5 點。
     */
    public OutboxRelay(JdbcTemplate jdbcTemplate,
                       KafkaTemplate<String, String> kafkaTemplate,
                       KafkaTopicsProperties topics,
                       TenantScopedExecutor tenantScopedExecutor,
                       @Value("${tms.outbox.relay.max-attempts:8}") int maxAttempts,
                       @Value("${tms.outbox.relay.backoff-base:1s}") String backoffBase,
                       @Value("${tms.outbox.relay.send-timeout:10s}") String sendTimeout,
                       @Value("${tms.outbox.relay.claim-lease:60s}") String claimLease,
                       @Value("${spring.kafka.producer.properties.max.block.ms:5000}") long producerMaxBlockMs,
                       @Value("${spring.kafka.producer.properties.delivery.timeout.ms:120000}")
                       long producerDeliveryTimeoutMs) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.maxAttempts = maxAttempts;
        this.backoffBase = DurationStyle.detectAndParse(backoffBase);
        this.sendTimeout = DurationStyle.detectAndParse(sendTimeout);
        this.claimLease = DurationStyle.detectAndParse(claimLease);

        // 檢查一：我們不能比 producer 更早放棄
        if (this.sendTimeout.toMillis() < producerDeliveryTimeoutMs) {
            throw new IllegalStateException(
                    "tms.outbox.relay.send-timeout（" + this.sendTimeout + "）必須大於等於 producer 的 "
                    + "delivery.timeout.ms（" + producerDeliveryTimeoutMs + "ms）。send-timeout 比它短，"
                    + "我們會在 producer 真正放棄之前就先認輸、把訊息排去重試，但 producer 可能之後"
                    + "還是把它送到了 Kafka，留下一筆我們自己不知道的重複送出。");
        }

        // 檢查二：租約必須撐過單筆訊息最長可能占用的時間
        Duration worstCaseSend = Duration.ofMillis(producerMaxBlockMs).plus(this.sendTimeout);
        if (this.claimLease.compareTo(worstCaseSend) <= 0) {
            throw new IllegalStateException(
                    "tms.outbox.relay.claim-lease（" + this.claimLease + "）必須大於 producer max.block.ms + "
                    + "relay send-timeout 的總和（目前是 " + worstCaseSend + "）。租約撐不過單筆訊息"
                    + "最長可能卡住的時間，活著的 instance 會被誤判成當掉、同一筆被別的 instance 搶走重送，"
                    + "破壞同 aggregate_key 的送出順序。");
        }
    }

    /**
     * 輪詢入口。
     *
     * <p>用 fixedDelay 而不是 fixedRate：fixedDelay 是「上一輪跑完後再等 N 秒」，
     * fixedRate 是「每 N 秒開一輪，不管上一輪完了沒」。Kafka 變慢時一輪可能跑很久，
     * fixedRate 會讓多輪疊在一起同時跑；fixedDelay 保證永遠只有一輪在跑。
     *
     * <p>這裡只向 TenantScopedExecutor 要租戶清單，而不是用它的 forEachTenant：
     * forEachTenant 會把傳入的 work 整個包在一個交易裡，而 Spring 的
     * TransactionTemplate 預設遇到既有交易是「加入」而不是「另開」——
     * relayTenant 裡想切出的那些短交易就會全部被併成一個大交易，
     * 類別註解第 4 點講的「送 Kafka 不在交易裡」就不成立了。
     * 交易邊界必須由 relayTenant 自己逐段決定，外層不能先框住。
     */
    @Scheduled(fixedDelayString = "${tms.outbox.relay.poll-interval:2s}")
    public void poll() {
        for (long tenantId : tenantScopedExecutor.tenantIds()) {
            try {
                relayTenant(tenantId);
            } catch (Exception e) {
                // 租戶之間故障隔離：一個租戶的 DB 抖動或意外例外，不該讓同一輪
                // 的其他租戶跟著不跑。記 log、跳過、下一輪再試。
                log.error("租戶 {} 這輪 relay 出錯，跳過、下一輪再試: {}", tenantId, e.getMessage(), e);
            }
        }
    }

    /**
     * 處理單一租戶的一輪：撈出所有 pending，按 aggregate_key 分組，每組依序送。
     *
     * <p>每個 key 的迴圈裡有四個會 {@code break} 的地方，它們代表同一個決策：
     * 「這個 key 這一輪到此為止，後面的訊息不送，換下一個 key」。
     * 觸發條件分別是：
     * <ul>
     *   <li>這筆還在等退避重試 / 租約未到期 → 整鍵等下一輪</li>
     *   <li>這筆重試次數用盡、已標 failed → 整鍵停擺，等人工</li>
     *   <li>這筆被別的 instance 搶走了 → 整鍵讓給對方</li>
     *   <li>這筆送失敗 → 整鍵等退避到期</li>
     * </ul>
     * 不論哪一種，都絕不讓後面的訊息越過前面的先送——這就是同鍵串行的實作。
     */
    private void relayTenant(long tenantId) {
        List<PendingMessage> pending = fetchPending(tenantId);
        if (pending.isEmpty()) {
            return;
        }
        for (List<PendingMessage> sameKey : groupByKeyKeepingOrder(pending).values()) {
            for (PendingMessage message : sameKey) {
                OffsetDateTime now = OffsetDateTime.now();
                if (message.nextRetryAt() != null && message.nextRetryAt().isAfter(now)) {
                    break;
                }
                ClaimResult claim = claim(tenantId, message);
                if (claim.exhausted()) {
                    log.error("outbox {} 重試次數用盡，標 failed 等人工處理（aggregate_key={}）",
                            message.id(), message.aggregateKey());
                    break;
                }
                if (!claim.claimed()) {
                    break;
                }
                if (!sendOne(tenantId, message, claim.attempts())) {
                    break;
                }
            }
        }
    }

    /**
     * 短交易：撈出這個租戶目前全部 pending 的訊息（包含還在等重試的），
     * 按 (aggregate_key, id) 排序，供 relayTenant 判斷同鍵順序。
     *
     * <p><b>為什麼要撈「還在等重試的」</b>：relayTenant 需要知道一個 key 的
     * <i>第一筆</i>是否還沒送出，才能決定要不要擋住後面的。如果只撈「現在可以送的」，
     * 第一筆正在退避時會被濾掉，後面那筆就會被誤認為是第一筆、直接送出去。
     *
     * <p><b>NOT EXISTS 子查詢</b>：只要同一個 aggregate_key 有任何一筆 failed，
     * 這個 key 的所有 pending 全部濾掉。效果是這個 key 每一輪都空手而回，直到人工
     * 把那筆 failed 處理掉（狀態不再是 failed）為止。
     * 理由見類別註解第 2 點：failed 代表「放棄自動重試」，後面的訊息若跳過它照送，
     * 就是把一筆早該先處理的狀態跳過去，順序保證直接破功。
     */
    private List<PendingMessage> fetchPending(long tenantId) {
        return tenantScopedExecutor.callAsTenant(tenantId, () -> jdbcTemplate.query("""
                SELECT o.id, o.tenant_id, o.aggregate_key, o.kind, o.payload, o.attempts, o.next_retry_at
                  FROM outbox o
                 WHERE o.status = 'pending'
                   AND NOT EXISTS (
                       SELECT 1 FROM outbox f
                        WHERE f.aggregate_key = o.aggregate_key AND f.status = 'failed'
                   )
                 ORDER BY o.aggregate_key, o.id
                """, PENDING_MAPPER));
    }

    /**
     * 多 instance 互斥：送 Kafka 之前先用一個條件式 UPDATE 把這筆訊息「租」下來。
     *
     * <p>這一個 UPDATE 同時做了三件事，全部在同一個原子操作裡完成：
     * <ol>
     *   <li><b>搶鎖</b>：WHERE 條件只允許「還是 pending、而且沒人租走（next_retry_at
     *       為空或已過期）」的列被更新。更新到 1 列就是搶到；0 列就是別人先搶了。</li>
     *   <li><b>記錄嘗試</b>：attempts + 1，並把 next_retry_at 設成 now() + 租約時間。
     *       租約期間這筆看起來像「正在等重試」，所有 instance（含自己）都不會再碰。</li>
     *   <li><b>判斷是否用盡</b>：如果 attempts 已經達到上限，直接標 failed，
     *       不再嘗試送出。</li>
     * </ol>
     *
     * <p><b>為什麼用條件式 UPDATE，不用 SELECT ... FOR UPDATE SKIP LOCKED</b>：
     * row lock 只在交易存活期間有效，交易提交鎖就釋放。但我們真正需要排他的時間段
     * （送 Kafka、等到 sendTimeout）根本不在交易裡——鎖撐不到那時候。
     * 條件式 UPDATE 把「已被搶走」寫進資料本身（next_retry_at），交易提交後保護
     * 依然存在，不依賴鎖的生命週期。
     *
     * <p><b>為什麼 attempts 在送之前就 +1，而不是送失敗後才 +1</b>：
     * 如果 instance 在送的途中整個行程被砍（不是乾淨的例外，是直接死掉），
     * sendOne 的 catch 永遠不會執行，attempts 就不會增加。租約過期後這筆被重新撈到，
     * 又用「全新的」一次機會再試——一筆毒訊息可以永遠卡在重試迴圈，maxAttempts
     * 形同虛設。先在交易裡把「這是一次嘗試」記下來再去冒險，就算中途當掉這次也算數。
     * 連帶地，attempts 滿了就直接在這裡標 failed、連這次都不送：如果放手送完再判斷，
     * 實際嘗試次數會比 maxAttempts 多一次，數字對不上。
     *
     * <p><b>為什麼租約到期時間用資料庫的 now() 算，不用 JVM 的時間</b>：
     * 不同 instance 的系統時鐘有偏差是常態。用 JVM 時間算好再寫進去，換另一台對時
     * 不準的機器來判讀，可能一寫入就已經「過期」，租約等於沒設。寫入和判斷都用
     * 同一個資料庫的 now()，就沒有時鐘不一致的問題。
     *
     * <p><b>租約過期後會發生什麼</b>：代表 instance 真的當掉、不會回來了。下一輪
     * 任何 instance 都會重新看到這筆 pending、重新搶、重新送。這是刻意允許的
     * at-least-once 重送，由下游靠 idempotency_key / envelope 的 (task_id, seq) 去重。
     * 只要租約確實大於 producer 卡住的 worst case（建構子有檢查），活著的 instance
     * 不會被誤判成當掉。
     */
    private ClaimResult claim(long tenantId, PendingMessage message) {
        double leaseSeconds = claimLease.toMillis() / 1000.0;
        List<ClaimResult> rows = tenantScopedExecutor.callAsTenant(tenantId, () -> jdbcTemplate.query("""
                UPDATE outbox
                   SET attempts = CASE WHEN attempts >= ? THEN attempts ELSE attempts + 1 END,
                       next_retry_at = CASE WHEN attempts >= ? THEN next_retry_at
                                            ELSE now() + make_interval(secs => ?) END,
                       status = CASE WHEN attempts >= ? THEN 'failed' ELSE status END
                 WHERE id = ?
                   AND status = 'pending'
                   AND (next_retry_at IS NULL OR next_retry_at <= now())
                RETURNING attempts, status
                """, CLAIM_MAPPER, maxAttempts, maxAttempts, leaseSeconds, maxAttempts, message.id()));
        return rows.isEmpty() ? ClaimResult.notClaimed() : rows.get(0);
    }

    /**
     * 送一筆訊息上 Kafka，並標記結果。回傳 false 表示送失敗，呼叫端會把整個 key 擋住。
     *
     * <p><b>為什麼「送 Kafka」和「標記 sent」分成兩個獨立的 try</b>：
     * 這兩步會因為完全不相干的原因失敗（Kafka 收不收是一回事，DB 這次 UPDATE
     * 寫不寫得進去是另一回事）。如果共用一個 catch，單純的 DB 抖動會被誤判為
     * 「送失敗」而排進退避重試；更糟的是當 attempts 累到上限時，一筆其實已經確實
     * 送達 Kafka 的訊息會被標成 failed——下游已經收到事件了，但簿記上說「沒送到、
     * 放棄了」，人工介入時會完全找不到問題在哪。
     *
     * <p><b>送失敗時</b>：用指數退避算出下次重試時間，寫回 next_retry_at。
     * 和 claim 一樣用資料庫的 now() 算：如果這台 instance 時鐘落後，用 JVM 時間
     * 寫出的 next_retry_at 可能一寫入就已過期，退避形同虛設——Kafka 真的掛掉時
     * 會變成毫無間隔的熱迴圈，瞬間灌爆重試。
     *
     * <p><b>送成功但標記 sent 失敗時</b>：維持 pending 不動。租約到期後會自動重送，
     * 由下游去重。這就是 at-least-once 的「寧可重複、不可遺失」。
     */
    private boolean sendOne(long tenantId, PendingMessage message, int attempts) {
        try {
            kafkaTemplate.send(topics.topicFor(message.kind()), message.aggregateKey(), message.payload())
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Duration retryDelay = backoff(attempts);
            double delaySeconds = retryDelay.toMillis() / 1000.0;
            tenantScopedExecutor.runAsTenant(tenantId, () -> jdbcTemplate.update(
                    "UPDATE outbox SET next_retry_at = now() + make_interval(secs => ?) WHERE id = ?",
                    delaySeconds, message.id()));
            log.warn("outbox {} 送出失敗（第 {} 次），{} 後重試: {}",
                    message.id(), attempts, retryDelay, e.getMessage());
            return false;
        }
        try {
            tenantScopedExecutor.runAsTenant(tenantId, () -> jdbcTemplate.update(
                    "UPDATE outbox SET status = 'sent', sent_at = now() WHERE id = ?", message.id()));
        } catch (Exception e) {
            log.error("outbox {} 已經送達 Kafka，但標記 sent 失敗；維持 pending，"
                      + "租約到期後會自動重送、由下游去重收尾: {}", message.id(), e.getMessage(), e);
        }
        return true;
    }

    /** 指數退避：第 n 次失敗後等 backoffBase × 2^(n-1)，倍數封頂在 2^MAX_BACKOFF_SHIFT。 */
    private Duration backoff(int attempts) {
        return backoffBase.multipliedBy(1L << Math.min(attempts - 1, MAX_BACKOFF_SHIFT));
    }

    /**
     * 按 aggregate_key 分組。輸入已按 (aggregate_key, id) 排序，用 LinkedHashMap
     * 保留插入順序，所以每組內部仍然是 id 升冪——relayTenant 的串行判定依賴這一點。
     */
    private static Map<String, List<PendingMessage>> groupByKeyKeepingOrder(List<PendingMessage> pending) {
        Map<String, List<PendingMessage>> byKey = new LinkedHashMap<>();
        for (PendingMessage message : pending) {
            byKey.computeIfAbsent(message.aggregateKey(), key -> new ArrayList<>()).add(message);
        }
        return byKey;
    }

    private record PendingMessage(long id, long tenantId, String aggregateKey, String kind,
                                  String payload, int attempts, OffsetDateTime nextRetryAt) {
    }

    /**
     * claim 的結果。
     * <ul>
     *   <li>claimed：搶到了，可以送</li>
     *   <li>exhausted：attempts 已滿，claim 這一步直接標成 failed，不用送</li>
     *   <li>兩者皆 false：被別的 instance 搶走</li>
     * </ul>
     */
    private record ClaimResult(boolean claimed, boolean exhausted, int attempts) {
        static ClaimResult notClaimed() {
            return new ClaimResult(false, false, -1);
        }
    }
}