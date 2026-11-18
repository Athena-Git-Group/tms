package com.athena.tms.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RoomRequestRateLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(10L);
    private static final int MAX_REQUESTS_PER_WINDOW = 3;
    private final Map<String, Deque<Instant>> grantsByRoom = new ConcurrentHashMap<String, Deque<Instant>>();

    public boolean tryAcquire(long tenantId, UUID roomId) {
        Deque grants;
        Deque deque = grants = this.grantsByRoom.computeIfAbsent(RoomRequestRateLimiter.key(tenantId, roomId), key -> new ArrayDeque());
        synchronized (deque) {
            Instant cutoff = Instant.now().minus(WINDOW);
            while (!grants.isEmpty() && ((Instant)grants.peekFirst()).isBefore(cutoff)) {
                grants.removeFirst();
            }
            if (grants.size() >= 3) {
                return false;
            }
            grants.addLast(Instant.now());
            return true;
        }
    }

    public void release(long tenantId, UUID roomId) {
        Deque<Instant> grants = this.grantsByRoom.get(RoomRequestRateLimiter.key(tenantId, roomId));
        if (grants == null) {
            return;
        }
        Deque<Instant> deque = grants;
        synchronized (deque) {
            grants.pollLast();
        }
    }

    public void reset() {
        this.grantsByRoom.clear();
    }

    private static String key(long tenantId, UUID roomId) {
        return tenantId + ":" + String.valueOf(roomId);
    }
}
