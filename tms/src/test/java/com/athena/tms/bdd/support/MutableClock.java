package com.athena.tms.bdd.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import com.athena.tms.dto.ShiftTimes;
import tools.jackson.databind.JsonNode;

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
 * 不需要額外同步；多個 Cucumber 執行緒共用同一個 singleton 時鐘不會壞資料。
 * 情境之間靠 {@code ResetHooks} 在開場呼叫 {@link #reset()} 還原，避免互相汙染。
 */
public final class MutableClock extends Clock {

    public static final LocalDate DEFAULT_TEST_DATE = LocalDate.of(2026, 10, 19);

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Taipei");

    private final ZoneId initialZone;
    private volatile Instant instant;
    private volatile ZoneId zone;

    public MutableClock() {
        this(DEFAULT_ZONE);
    }

    public MutableClock(ZoneId zone) {
        this.initialZone = zone;
        this.zone = zone;
        this.instant = initialInstant(zone);
    }

    private MutableClock(Instant instant, ZoneId zone) {
        this.initialZone = zone;
        this.instant = instant;
        this.zone = zone;
    }

    private static Instant initialInstant(ZoneId zone) {
        return ZonedDateTime.of(DEFAULT_TEST_DATE, LocalTime.NOON, zone).toInstant();
    }

    /**
     * 把時鐘還原成剛建立時的狀態：建立時的時區、{@link #DEFAULT_TEST_DATE} 當天中午。
     * 時鐘是單例，前一個情境撥過的時間會留到下一個情境，每個情境開場都要先還原。
     */
    public void reset() {
        this.zone = initialZone;
        this.instant = initialInstant(initialZone);
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

    public void advanceMinutes(long minutes) {
        this.instant = this.instant.plusSeconds(minutes * 60);
    }

    /**
     * 從目前的「現在」往後找，第一個落在飯店所有班別時段之外的時刻。
     *
     * <p>「某人今天的班已經結束」這類情境要把時鐘撥到換人判定認定為下班的時間。
     * 判定用的就是產品碼的 {@link ShiftTimes#coversTime}，這裡逐分鐘往後試同一個函式，
     * 測試與產品對「班別之外」的認定就不會各算各的。現在已經在班別之外時直接回現在。
     *
     * <p>最多往後找一整天。飯店沒有設定班別，或班別首尾相接涵蓋全天時，
     * {@code coversTime} 對任何時刻都回 true，這裡直接噴錯，提醒情境先給出班別時段，
     * 而不是默默把時鐘撥到一個其實還在班的時刻。
     *
     * @param shiftTimes 飯店班別設定（tenant_config 的 shift_times），形狀是 {@code {班別: {start, end}}}
     */
    public Instant nextInstantOutsideShifts(JsonNode shiftTimes) {
        ZonedDateTime now = ZonedDateTime.ofInstant(instant, zone);
        if (!ShiftTimes.coversTime(shiftTimes, now.toLocalTime())) {
            return instant;
        }
        ZonedDateTime candidate = now.truncatedTo(ChronoUnit.MINUTES);
        for (int minute = 1; minute <= 24 * 60; minute++) {
            candidate = candidate.plusMinutes(1);
            if (!ShiftTimes.coversTime(shiftTimes, candidate.toLocalTime())) {
                return candidate.toInstant();
            }
        }
        throw new IllegalStateException("飯店班別時段涵蓋全天或沒有設定，找不到班別之外的時刻：shift_times=" + shiftTimes);
    }
}
