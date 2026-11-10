package com.athena.tms.integration.stepdefs.utils;

import java.time.Duration;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;

public final class Awaits {
    public static void waitUntil(Duration timeout, String what, Supplier<Boolean> condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(condition.get())) {
                return;
            }
            try {
                Thread.sleep(100L);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Assertions.fail((String)("\u7b49\u5f85\u88ab\u4e2d\u65b7: " + what));
            }
        }
        Assertions.fail((String)("\u7b49\u5f85\u903e\u6642\uff08" + timeout.toSeconds() + "s\uff09: " + what));
    }

    private Awaits() {
    }
}
