package com.athena.app.bdd.steps.push;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PushMomentParsingTest {

    private static final LocalDate DEVICE_TODAY = LocalDate.of(2027, 1, 2);

    @Test
    void relativeWordsFollowDeviceDate() {
        assertEquals(DEVICE_TODAY, PushGivenSteps.parseDateToken("今天", DEVICE_TODAY));
        assertEquals(LocalDate.of(2027, 1, 1), PushGivenSteps.parseDateToken("昨天", DEVICE_TODAY));
        assertEquals(LocalDate.of(2026, 12, 31), PushGivenSteps.parseDateToken("前天", DEVICE_TODAY));
        assertEquals(LocalDate.of(2027, 1, 3), PushGivenSteps.parseDateToken("明天", DEVICE_TODAY));
    }

    @Test
    void monthDayAfterDeviceDateBelongsToPreviousYear() {
        assertEquals(LocalDate.of(2026, 12, 31), PushGivenSteps.parseDateToken("12/31", DEVICE_TODAY));
        assertEquals(LocalDate.of(2027, 1, 2), PushGivenSteps.parseDateToken("1/2", DEVICE_TODAY));
        assertEquals(LocalDate.of(2026, 11, 2), PushGivenSteps.parseDateToken("2026-11-02", DEVICE_TODAY));
    }

    @Test
    void momentCombinesDeviceDateWithClockTime() {
        OffsetDateTime expected = ZonedDateTime
                .of(LocalDate.of(2027, 1, 1), LocalTime.of(9, 5), ZoneId.systemDefault())
                .toOffsetDateTime();
        assertEquals(expected, PushGivenSteps.parseMoment("昨天 09:05", DEVICE_TODAY));
        assertThrows(IllegalArgumentException.class,
                () -> PushGivenSteps.parseMoment("09:05", DEVICE_TODAY));
    }
}
