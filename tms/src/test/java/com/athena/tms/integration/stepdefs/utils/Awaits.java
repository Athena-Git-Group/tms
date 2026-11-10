package com.athena.tms.integration.stepdefs.utils;

import java.time.Duration;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;

public final class Awaits {

    private Awaits() {
    }

    public static void waitUntil(Duration timeout, String what, Supplier<Boolean> condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(condition.get())) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Assertions.fail("等待被中斷: " + what);
            }
        }
        Assertions.fail("等待逾時（" + timeout.toSeconds() + "s）: " + what);
    }
}
