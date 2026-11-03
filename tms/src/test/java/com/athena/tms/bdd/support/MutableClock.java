package com.athena.tms.bdd.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * BDD 情境用的可調測試時鐘：預設固定在 {@link #DEFAULT_TEST_DATE} 這天的中午
 * （{@link #DEFAULT_ZONE} 當地時間），情境可透過 {@link #setInstant(Instant)}、
 * {@link #setTodayAt(String)}、{@link #advanceMinutes(long)} 操控「現在」，
 * 用來驅動排程 job 或任何依賴 {@link Clock} 的時間判斷（例如逾時、班別切換）。
 *
 * <p>main 的 {@code com.athena.tms.config.ClockConfig} 提供
 * {@code Clock.systemDefaultZone()}、沒有鎖定時區；測試環境改用本類別當
 * {@code @Primary} 的 {@link Clock} bean 蓋過它，並固定 {@link #DEFAULT_ZONE}
 * 讓「今天」「班末」這類語意在測試裡可預期、可重現。
 *
 * <p>執行緒安全：目前的 instant／zone 存在 volatile 欄位，讀寫都是單一欄位賦值，
 * 不需要額外同步；多個 Cucumber 執行緒共用同一個 singleton 時鐘不會壞資料，
 * 但情境彼此之間仍應呼叫 {@link #setInstant(Instant)} 等方法重設時間，避免互相汙染。
 */
public final class MutableClock extends Clock {

    /** 固定測試基準日，和舊 {@code CucumberSpringConfiguration.HarnessConfig#fixedClock} 用的 2026-10-19 一致。 */
    public static final LocalDate DEFAULT_TEST_DATE = LocalDate.of(2026, 10, 19);

    /** ClockConfig 用 {@code systemDefaultZone()}、沒有鎖定時區；測試固定用 Asia/Taipei 讓「今天」可預期。 */
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Taipei");

    private volatile Instant instant;
    private volatile ZoneId zone;

    public MutableClock() {
        this(DEFAULT_ZONE);
    }

    public MutableClock(ZoneId zone) {
        this.zone = zone;
        this.instant = ZonedDateTime.of(DEFAULT_TEST_DATE, LocalTime.NOON, zone).toInstant();
    }

    private MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(this.instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    /** 直接把「現在」設成指定的 instant。 */
    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    /**
     * 把「現在」設成固定測試日（{@link #DEFAULT_TEST_DATE}）的某個時刻，時區用這個時鐘目前的 zone。
     *
     * @param hhmm 24 小時制時間，格式 {@code "HH:mm"}（例如 "23:30"）
     */
    public void setTodayAt(String hhmm) {
        LocalTime time = LocalTime.parse(hhmm);
        this.instant = ZonedDateTime.of(DEFAULT_TEST_DATE, time, zone).toInstant();
    }

    /** 把「現在」往前推進指定分鐘數（負數則往回調）。 */
    public void advanceMinutes(long minutes) {
        this.instant = this.instant.plusSeconds(minutes * 60);
    }
}
