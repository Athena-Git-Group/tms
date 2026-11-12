package com.athena.tms.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * 可從外部撥動的時鐘，只在啟動參數 {@code tms.test-clock.enabled=true} 時取代系統時鐘，
 * 讓 App 端的端到端測試能把跑著的後端「現在」設到劇本要的時刻（例如員工下班之後）。
 *
 * <p>沒被撥過時照系統時鐘走；{@link #fixAt(Instant)} 之後「現在」固定停在該時刻、不會往前走，
 * 直到再撥一次或 {@link #reset()} 回系統時鐘。選固定而不是從該時刻繼續走，是為了和後端自己
 * 的 BDD 測試時鐘同一種語意：同一個劇本在兩邊跑，判定「現在幾點」的結果才會一樣，
 * 也不會因為測試跑得慢而讓時間悄悄跨過班別結束之類的分界。
 *
 * <p>時區沿用系統預設，與關閉時的 {@code Clock.systemDefaultZone()} 相同，
 * 所以「今天」「班末」的換算規則不因開關而改變。
 */
public final class AdjustableClock extends Clock {

    private final ZoneId zone;
    private volatile Instant fixedInstant;

    public AdjustableClock() {
        this(ZoneId.systemDefault());
    }

    public AdjustableClock(ZoneId zone) {
        this.zone = zone;
    }

    /** 把「現在」固定在指定時刻，直到下一次撥動或 {@link #reset()}。 */
    public void fixAt(Instant instant) {
        this.fixedInstant = instant;
    }

    /** 回到系統時鐘。 */
    public void reset() {
        this.fixedInstant = null;
    }

    /** 目前是否固定在某個時刻（而不是跟著系統時鐘走）。 */
    public boolean isFixed() {
        return fixedInstant != null;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    /**
     * 換時區後的時鐘仍要跟著這一個撥動，否則呼叫端拿到的副本會停在呼叫當下的設定。
     */
    @Override
    public Clock withZone(ZoneId newZone) {
        if (newZone.equals(zone)) {
            return this;
        }
        AdjustableClock parent = this;
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return newZone;
            }

            @Override
            public Clock withZone(ZoneId z) {
                return parent.withZone(z);
            }

            @Override
            public Instant instant() {
                return parent.instant();
            }
        };
    }

    @Override
    public Instant instant() {
        Instant fixed = fixedInstant;
        return fixed != null ? fixed : Instant.now();
    }
}
