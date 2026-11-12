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

    public void setTodayAt(String hhmm) {
        LocalTime time = LocalTime.parse(hhmm);
        this.instant = ZonedDateTime.of(DEFAULT_TEST_DATE, time, zone).toInstant();
    }

    public void advanceMinutes(long minutes) {
        this.instant = this.instant.plusSeconds(minutes * 60);
    }

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
