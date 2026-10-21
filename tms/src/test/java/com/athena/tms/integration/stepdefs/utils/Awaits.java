package com.athena.tms.integration.stepdefs.utils;

import java.time.Duration;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 反覆輪詢直到條件成立的測試工具。
 *
 * <p>outbox relay 由排程器在背景執行緒驅動，測試無法同步觸發它，
 * 只能定期查詢結果是否出現。等待超過上限時測試失敗，
 * 失敗訊息會附上呼叫端描述的等待條件，方便定位紅燈原因。
 */
public final class Awaits {

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
                fail("等待被中斷: " + what);
            }
        }
        fail("等待逾時（" + timeout.toSeconds() + "s）: " + what);
    }

    private Awaits() {
    }
}
