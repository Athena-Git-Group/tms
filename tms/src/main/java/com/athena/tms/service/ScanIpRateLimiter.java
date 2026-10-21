package com.athena.tms.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 掃碼入口的 IP 失敗計數器。程序內而非 Redis/DB：spec 把「多實例下
 * 各實例各算各的」列為已知限制，MVP 是單程序部署，
 * 程序內就是正確範圍。為一個已知限制先引外部依賴是過度設計。
 *
 * <p>只記失敗不記成功：限流的目標是擋「拿著一把撿來的/猜的 token 反覆試」
 * 的人，正常住客掃一次就成功、永遠碰不到門檻，成功計數只會誤傷。
 */
@Component
public class ScanIpRateLimiter {

    /** A7 字面：10 分鐘窗。門檻（預設 10 次）由呼叫端讀 tenant_config 傳入。 */
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final Map<String, Deque<Instant>> failuresByIp = new ConcurrentHashMap<>();

    /**
     * 該 IP 最近一次「解析得出租戶」的失敗所適用的門檻。門檻是租戶可調的（A7），
     * 但查不到 token 的請求解析不出租戶——若一律退回預設值，攻擊者只要混入
     * 猜測 token 就能繞開租戶調低的門檻。記住上次已知門檻，讓同一 IP 的
     * 後續請求沿用。
     */
    private final Map<String, Integer> thresholdByIp = new ConcurrentHashMap<>();

    /** @return 該 IP 於 10 分鐘窗內的失敗次數（順手汰舊，窗外紀錄不佔記憶體）。 */
    public int recentFailures(String ip) {
        Deque<Instant> failures = failuresByIp.get(ip);
        if (failures == null) {
            return 0;
        }
        synchronized (failures) {
            prune(failures);
            if (failures.isEmpty()) {
                // deque 清空就把 map 項也移走：掃一次失敗的路人 IP 成千上萬，
                // 不回收的話這張表只進不出
                failuresByIp.remove(ip, failures);
                return 0;
            }
            return failures.size();
        }
    }

    public void recordFailure(String ip) {
        Deque<Instant> failures = failuresByIp.computeIfAbsent(ip, key -> new ArrayDeque<>());
        synchronized (failures) {
            prune(failures);
            failures.addLast(Instant.now());
        }
    }

    /** 記失敗並一併記住當時適用的租戶門檻，供後續解析不出租戶的請求沿用。 */
    public void recordFailure(String ip, int threshold) {
        thresholdByIp.put(ip, threshold);
        recordFailure(ip);
    }

    /** @return 該 IP 最近一次已知的租戶門檻；沒有紀錄時回傳 fallback（預設門檻）。 */
    public int lastKnownThreshold(String ip, int fallback) {
        return thresholdByIp.getOrDefault(ip, fallback);
    }

    /** 測試隔離用：失敗計數跨請求存活，不清掉會讓測試彼此污染。 */
    public void reset() {
        failuresByIp.clear();
        thresholdByIp.clear();
    }

    private static void prune(Deque<Instant> failures) {
        Instant cutoff = Instant.now().minus(WINDOW);
        while (!failures.isEmpty() && failures.peekFirst().isBefore(cutoff)) {
            failures.removeFirst();
        }
    }
}
