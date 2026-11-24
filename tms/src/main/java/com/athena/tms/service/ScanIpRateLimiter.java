package com.athena.tms.service;

import java.time.Clock;
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
    private final Clock clock;

    public ScanIpRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public int recentFailures(String ip) {
        Deque<Instant> failures = this.failuresByIp.get(ip);
        if (failures == null) {
            return 0;
        }
        Deque<Instant> deque = failures;
        synchronized (deque) {
            this.prune(failures);
            if (failures.isEmpty()) {
                this.failuresByIp.remove(ip, failures);
                return 0;
            }
            return failures.size();
        }
    }

    public void recordFailure(String ip) {
        Deque<Instant> failures = this.failuresByIp.computeIfAbsent(ip, key -> new ArrayDeque<Instant>());
        synchronized (failures) {
            this.prune(failures);
            failures.addLast(this.clock.instant());
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

    private void prune(Deque<Instant> failures) {
        Instant cutoff = this.clock.instant().minus(WINDOW);
        while (!failures.isEmpty() && failures.peekFirst().isBefore(cutoff)) {
            failures.removeFirst();
        }
    }
}
