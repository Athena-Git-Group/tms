package com.athena.tms.service;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 同一房任何 10 分鐘內最多 3 筆需求。張數一律從資料庫的 tasks 現數，不另外記一份：
 * 只數住客送的（勾選單與自由文字），櫃檯代開的不算；已取消的也算，
 * 時間看 created_at，以注入的營業時鐘為準；created_at 剛好滿 10 分鐘的那一筆不再佔名額。
 */
@Component
public class RoomRequestRateLimiter {
    static final Duration WINDOW = Duration.ofMinutes(10L);
    static final int MAX_REQUESTS_PER_WINDOW = 3;

    private static final String COUNTED = """
            SELECT count(*) AS taken, min(created_at) AS oldest
              FROM tasks
             WHERE tenant_id = ? AND %s = ?
               AND source = 'guest'
               AND created_at > ? AND created_at <= ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public RoomRequestRateLimiter(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    /** 還能送幾筆，以及再過多久會釋出最早被佔用的那一筆名額；沒有名額被佔用時是零。 */
    public record Quota(int limit, int remaining, Duration nextReleaseIn) {
    }

    /**
     * 這間房還有名額就回 true。必須在建單的同一個交易裡呼叫：先鎖住這間房再數，
     * 鎖到交易結束，同一房同時送來的兩筆不會都看到「還剩一筆」。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryAcquire(long tenantId, UUID roomId) {
        this.jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (ResultSet rs) -> null, "room-request:" + tenantId + ":" + roomId);
        return this.count("room_id", tenantId, roomId).remaining() > 0;
    }

    /** 這間房目前的名額，不分是哪一筆訂房；再送會不會回 429 以這個為準。 */
    public Quota roomQuota(long tenantId, UUID roomId) {
        return this.count("room_id", tenantId, roomId);
    }

    /**
     * 住客這次住宿自己看得到的名額：只數這筆訂房的需求（含已取消的），
     * 同一房別的訂房送的不在這裡扣，所以這個數字可能比 {@link #roomQuota} 寬。
     */
    public Quota stayQuota(long tenantId, UUID reservationId) {
        return this.count("reservation_id", tenantId, reservationId);
    }

    private Quota count(String scopeColumn, long tenantId, UUID scopeId) {
        Instant now = this.clock.instant();
        OffsetDateTime nowAt = OffsetDateTime.ofInstant(now, this.clock.getZone());
        return this.jdbcTemplate.queryForObject(COUNTED.formatted(scopeColumn), (rs, rowNum) -> {
            int taken = rs.getInt("taken");
            Timestamp oldest = rs.getTimestamp("oldest");
            Duration nextRelease = oldest == null ? Duration.ZERO : Duration.between(now, oldest.toInstant().plus(WINDOW));
            return new Quota(MAX_REQUESTS_PER_WINDOW, Math.max(0, MAX_REQUESTS_PER_WINDOW - taken), nextRelease);
        }, tenantId, scopeId, nowAt.minus(WINDOW), nowAt);
    }
}
