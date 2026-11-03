package com.athena.tms.bdd.support;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import tools.jackson.databind.ObjectMapper;

/**
 * 等待「outbox 落庫」「relay 送出」「推播消費者寫出資料列」這幾件非同步的事
 * 穩定下來再查，取代 stepdef 自己手刻輪詢迴圈。查一次就斷言會因為交易提交、
 * relay 輪詢、消費者處理都有延遲而偶發失敗，所以一律帶 deadline 重試，
 * 逾時才視為真的沒發生、丟 {@link AssertionError}。
 *
 * <p>預設每 200ms 查一次、逾時 10s；{@link #withPollInterval(Duration)} 與
 * {@link #withTimeout(Duration)} 回傳新的不可變實例，用來在個別情境調整，
 * 不會動到共用 bean 的預設值。
 */
public final class OutboxAwait {

    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(200);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final DbProbe dbProbe;
    private final ObjectMapper objectMapper;
    private final Duration pollInterval;
    private final Duration timeout;

    public OutboxAwait(DbProbe dbProbe, ObjectMapper objectMapper) {
        this(dbProbe, objectMapper, DEFAULT_POLL_INTERVAL, DEFAULT_TIMEOUT);
    }

    private OutboxAwait(DbProbe dbProbe, ObjectMapper objectMapper, Duration pollInterval, Duration timeout) {
        this.dbProbe = dbProbe;
        this.objectMapper = objectMapper;
        this.pollInterval = pollInterval;
        this.timeout = timeout;
    }

    public OutboxAwait withPollInterval(Duration pollInterval) {
        return new OutboxAwait(dbProbe, objectMapper, pollInterval, timeout);
    }

    public OutboxAwait withTimeout(Duration timeout) {
        return new OutboxAwait(dbProbe, objectMapper, pollInterval, timeout);
    }

    /** 等待某種 kind 的 outbox 列出現且符合 predicate，回傳那一列整欄。 */
    public Map<String, Object> waitForOutboxRow(String kind, Predicate<Map<String, Object>> predicate, String what) {
        return pollUntilFound(
                () -> dbProbe.outboxRows(kind).stream().filter(predicate).findFirst(),
                what);
    }

    /**
     * 等待指定 outbox 列被 relay 送出。dbml 同時留了 status（CHECK
     * pending|sent|failed）與 sent_at 兩個欄位，這裡兩個都接受，任一個顯示
     * 「已送」就算數，避免日後只挑其中一個欄位判斷而漏掉另一種寫法。
     */
    public Map<String, Object> waitForSent(long outboxId) {
        return pollUntilFound(
                () -> dbProbe.queryOne(
                        "SELECT * FROM outbox WHERE id = ? AND (status = 'sent' OR sent_at IS NOT NULL)",
                        outboxId),
                "outbox id=" + outboxId + " 已被 relay 送出（status=sent 或 sent_at 非空）");
    }

    /** 等待某種 kind、符合 predicate 的 outbox 列被送出，見 {@link #waitForSent(long)} 的送達判斷。 */
    public Map<String, Object> waitForSent(String kind, Predicate<Map<String, Object>> predicate, String what) {
        return pollUntilFound(
                () -> dbProbe.outboxRows(kind).stream()
                        .filter(predicate)
                        .filter(OutboxAwait::isSent)
                        .findFirst(),
                what + "（已送出）");
    }

    /**
     * 等待推播消費者產生的 outbox（kind=push）列出現並符合 predicate，解析成
     * {@link PushPayloadView} 回傳。解析失敗（payload 形狀跟契約對不上）直接
     * 丟 AssertionError，不要讓呼叫端拿著錯誤的 view 繼續斷言。
     */
    public PushPayloadView waitForPush(Predicate<PushPayloadView> predicate, String what) {
        return pollUntilFound(
                () -> dbProbe.outboxRows("push").stream()
                        .map(this::parsePush)
                        .filter(predicate)
                        .findFirst(),
                what);
    }

    /** {@link #waitForPush(Predicate, String)} 最常見的用法：等待送給某個員工的推播。 */
    public PushPayloadView waitForPushTo(UUID recipientStaffId) {
        return waitForPush(
                view -> recipientStaffId.equals(view.recipientStaffId()),
                "推播送給 staffId=" + recipientStaffId);
    }

    private PushPayloadView parsePush(Map<String, Object> outboxRow) {
        String json = asJsonText(outboxRow.get("payload"));
        try {
            return objectMapper.readValue(json, PushPayloadView.class);
        } catch (RuntimeException e) {
            throw new AssertionError("解析 push payload 失敗：" + json, e);
        }
    }

    private static boolean isSent(Map<String, Object> outboxRow) {
        Object status = outboxRow.get("status");
        Object sentAt = outboxRow.get("sent_at");
        return "sent".equals(status) || sentAt != null;
    }

    /**
     * jsonb 欄位經 JDBC 讀出來不保證是 String（常見是驅動自帶的 json 包裝型別），
     * 但那些型別的 toString() 就是原始 JSON 文字，所以直接轉字串即可丟給
     * ObjectMapper 解析，不需要為特定驅動型別另外寫轉換。
     */
    private static String asJsonText(Object payload) {
        if (payload == null) {
            throw new IllegalStateException("outbox 列沒有 payload");
        }
        return payload instanceof String s ? s : payload.toString();
    }

    /**
     * 統一的輪詢迴圈：每次呼叫 probe 嘗試找出結果，找到就立刻回傳，逾時仍找
     * 不到就丟 AssertionError。等待期間被中斷時保留中斷旗標並結束等待，
     * 不吞掉中斷、也不讓呼叫端誤以為是逾時。
     */
    private <T> T pollUntilFound(Supplier<Optional<T>> probe, String what) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Optional<T> found = probe.get();
            if (found.isPresent()) {
                return found.get();
            }
            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待被中斷：" + what, e);
            }
        }
        throw new AssertionError(
                "等待逾時（" + timeout.toSeconds() + "s，每 " + pollInterval.toMillis() + "ms 查一次）：" + what);
    }
}
