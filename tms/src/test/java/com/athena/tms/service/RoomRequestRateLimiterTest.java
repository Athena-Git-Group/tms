package com.athena.tms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.athena.tms.PostgresSpringTest;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.error.TooManyRequestsException;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.GuestRequestCreate;
import com.athena.tms.dto.TaskCreateCommand;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 每間房 10 分鐘 3 筆的名額從 tasks 現數：只數住客送的，已取消的也算，櫃檯代開的不算，時間以營業時鐘為準。 */
class RoomRequestRateLimiterTest extends PostgresSpringTest {

    private static final long TENANT_ID = 1L;
    private static final UUID ROOM_305 = UUID.fromString("00000000-0000-7000-8000-000000000305");
    private static final UUID RESERVATION_B001 = UUID.fromString("00000000-0000-7000-8000-00000000b001");

    @Autowired
    GuestRequestService guestRequestService;

    @Autowired
    RoomRequestRateLimiter limiter;

    @Autowired
    TaskEngine taskEngine;

    @Autowired
    MutableClock clock;

    @Autowired
    Clock injectedClock;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void bindGuest() {
        TenantContext.set(TENANT_ID);
        CurrentActor.set(CurrentActor.guest(TENANT_ID, UUID.randomUUID(), ROOM_305, RESERVATION_B001));
        // 每個案例放在當天不同的整點，彼此的 10 分鐘窗不重疊，不必清資料。
    }

    @AfterEach
    void unbind() {
        CurrentActor.clear();
        TenantContext.clear();
        clock.reset();
    }

    @Test
    void 營業時鐘就是限流器用的時鐘() {
        assertThat(injectedClock).isSameAs(clock);
    }

    @Test
    void 櫃檯替這間房代開的單不佔住客的名額() {
        clock.setTodayAt("09:00");
        guest("毛巾");
        clock.setTodayAt("09:02");
        guest("衣架");
        clock.setTodayAt("09:03");
        deskOpened();
        clock.setTodayAt("09:05");

        assertThat(limiter.roomQuota(TENANT_ID, ROOM_305).remaining()).isEqualTo(1);
        assertThat(limiter.stayQuota(TENANT_ID, RESERVATION_B001).remaining()).isEqualTo(1);
        guest("牙刷組");
        assertThat(guestTasksAt("09:05")).isEqualTo(1);
        assertThat(limiter.roomQuota(TENANT_ID, ROOM_305).remaining()).isZero();
    }

    @Test
    void 已取消的需求也佔名額() {
        clock.setTodayAt("10:00");
        guest("毛巾");
        clock.setTodayAt("10:02");
        guest("衣架");
        clock.setTodayAt("10:03");
        UUID withdrawn = guest("吹風機");
        guestRequestService.cancel(withdrawn);
        clock.setTodayAt("10:08");

        assertThat(limiter.stayQuota(TENANT_ID, RESERVATION_B001).remaining()).isZero();
        assertThatThrownBy(() -> guest("牙刷組")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void 最早那筆滿10分鐘就釋出一個名額() {
        clock.setTodayAt("11:00");
        guest("毛巾");
        clock.setTodayAt("11:02");
        guest("衣架");
        clock.setTodayAt("11:03");
        guest("吹風機");

        clock.setInstant(at("11:10").minusSeconds(1));
        assertThatThrownBy(() -> guest("牙刷組")).isInstanceOf(TooManyRequestsException.class);

        clock.setTodayAt("11:10");
        guest("牙刷組");
        assertThat(limiter.stayQuota(TENANT_ID, RESERVATION_B001).remaining()).isZero();
        assertThatThrownBy(() -> guest("礦泉水")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void 釋出時間是最早被佔用那一筆離開窗口的剩餘時間() {
        clock.setTodayAt("12:00");
        assertThat(limiter.stayQuota(TENANT_ID, RESERVATION_B001))
                .isEqualTo(new RoomRequestRateLimiter.Quota(3, 3, Duration.ZERO));

        guest("毛巾");
        clock.setTodayAt("12:04");
        guest("衣架");
        clock.setInstant(at("12:06").plusMillis(500));

        RoomRequestRateLimiter.Quota quota = limiter.stayQuota(TENANT_ID, RESERVATION_B001);
        assertThat(quota.limit()).isEqualTo(3);
        assertThat(quota.remaining()).isEqualTo(1);
        assertThat(quota.nextReleaseIn()).isEqualTo(Duration.ofMinutes(4).minusMillis(500));

        clock.setTodayAt("12:10");
        quota = limiter.stayQuota(TENANT_ID, RESERVATION_B001);
        assertThat(quota.remaining()).isEqualTo(2);
        assertThat(quota.nextReleaseIn()).isEqualTo(Duration.ofMinutes(4));
    }

    @Test
    void 時鐘撥回之前_之後才建的單不算() {
        clock.setTodayAt("13:05");
        guest("毛巾");
        clock.setTodayAt("13:04");
        assertThat(limiter.roomQuota(TENANT_ID, ROOM_305).remaining()).isEqualTo(3);
    }

    private UUID guest(String text) {
        return guestRequestService.create(new GuestRequestCreate(null, null, text, "zh-TW")).id();
    }

    private void deskOpened() {
        taskEngine.create(new TaskCreateCommand(TENANT_ID, "amenity", "desk", ROOM_305, null, "housekeeping", "pool",
                null, "櫃檯代開", null, null, null, null, Actor.system()));
    }

    private Instant at(String hhmm) {
        MutableClock probe = new MutableClock();
        probe.setTodayAt(hhmm);
        return probe.instant();
    }

    private long guestTasksAt(String hhmm) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tasks WHERE tenant_id = ? AND room_id = ? AND source = 'guest' AND created_at = ?",
                Long.class, TENANT_ID, ROOM_305, java.time.OffsetDateTime.ofInstant(at(hhmm), clock.getZone()));
        return count == null ? 0 : count;
    }
}
