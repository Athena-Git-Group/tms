package com.athena.tms.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同房需求限流：10 分鐘窗 3 筆，第 4 筆 429。程序內計數器，
 * 多實例限制同 ScanIpRateLimiter（spec 已知限制，不先引外部依賴）。
 *
 * <p>按房不按 session 計：換瀏覽器重掃就有新 session，按 session 計等於
 * 沒有限流；房才是資源的單位。限流只數「成功建立」的單，所以用
 * 先佔位後補償（建單失敗退還名額）而不是事後登記——check-then-act 分兩步
 * 會讓兩個併發請求同時看到「還剩一位」雙雙通過。
 */
@Component
public class RoomRequestRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final int MAX_REQUESTS_PER_WINDOW = 3;

    private final Map<String, Deque<Instant>> grantsByRoom = new ConcurrentHashMap<>();

    /** @return false＝本窗名額已滿，呼叫端回 429、不得建單。 */
    public boolean tryAcquire(long tenantId, UUID roomId) {
        Deque<Instant> grants = grantsByRoom.computeIfAbsent(key(tenantId, roomId), key -> new ArrayDeque<>());
        synchronized (grants) {
            Instant cutoff = Instant.now().minus(WINDOW);
            while (!grants.isEmpty() && grants.peekFirst().isBefore(cutoff)) {
                grants.removeFirst();
            }
            if (grants.size() >= MAX_REQUESTS_PER_WINDOW) {
                return false;
            }
            grants.addLast(Instant.now());
            return true;
        }
    }

    /** 佔了位但建單失敗（422 等）時退還：失敗的請求不該吃掉住客的名額。 */
    public void release(long tenantId, UUID roomId) {
        Deque<Instant> grants = grantsByRoom.get(key(tenantId, roomId));
        if (grants == null) {
            return;
        }
        synchronized (grants) {
            grants.pollLast();
        }
    }

    /** 測試隔離用：計數跨請求存活，不清掉會讓測試彼此污染。 */
    public void reset() {
        grantsByRoom.clear();
    }

    private static String key(long tenantId, UUID roomId) {
        return tenantId + ":" + roomId;
    }
}
