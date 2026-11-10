package com.athena.tms.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ScanIpRateLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(10L);
    private final Map<String, Deque<Instant>> failuresByIp = new ConcurrentHashMap<String, Deque<Instant>>();
    private final Map<String, Integer> thresholdByIp = new ConcurrentHashMap<String, Integer>();

    public int recentFailures(String ip) {
        Deque<Instant> failures = this.failuresByIp.get(ip);
        if (failures == null) {
            return 0;
        }
        Deque<Instant> deque = failures;
        synchronized (deque) {
            ScanIpRateLimiter.prune(failures);
            if (failures.isEmpty()) {
                this.failuresByIp.remove(ip, failures);
                return 0;
            }
            return failures.size();
        }
    }

    public void recordFailure(String ip) {
        Deque failures;
        Deque deque = failures = this.failuresByIp.computeIfAbsent(ip, key -> new ArrayDeque());
        synchronized (deque) {
            ScanIpRateLimiter.prune(failures);
            failures.addLast(Instant.now());
        }
    }

    public void recordFailure(String ip, int threshold) {
        this.thresholdByIp.put(ip, threshold);
        this.recordFailure(ip);
    }

    public int lastKnownThreshold(String ip, int fallback) {
        return this.thresholdByIp.getOrDefault(ip, fallback);
    }

    public void reset() {
        this.failuresByIp.clear();
        this.thresholdByIp.clear();
    }

    private static void prune(Deque<Instant> failures) {
        Instant cutoff = Instant.now().minus(WINDOW);
        while (!failures.isEmpty() && failures.peekFirst().isBefore(cutoff)) {
            failures.removeFirst();
        }
    }
}
