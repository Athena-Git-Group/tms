package com.athena.tms.bdd.support;

import com.athena.tms.common.UuidV7;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

/**
 * 訂房快取測試資料入口（{@code reservations_cache}，欄位依 data-model.dbml
 * {@code Table reservations_cache}）。
 *
 * <p>一房、一個入住日只會有一筆：reservation_ref 由房號＋入住日組出，重複呼叫走
 * {@code ON CONFLICT ... DO UPDATE}，呼叫端不必自己先查有沒有、也不會因為重跑
 * 同一個情境而撞 unique constraint。
 */
public class ReservationFixtures {

    private final JdbcClient jdbcClient;
    private final TenantFixtures tenantFixtures;
    private final RoomFixtures roomFixtures;
    private final MutableClock clock;
    private final ObjectMapper objectMapper;

    public ReservationFixtures(
            JdbcClient jdbcClient,
            TenantFixtures tenantFixtures,
            RoomFixtures roomFixtures,
            MutableClock clock,
            ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.tenantFixtures = tenantFixtures;
        this.roomFixtures = roomFixtures;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    /**
     * @param attrs 屬性列表（例如 "vip"、"pet"），落地成 {@code {"vip": true, "pet": true}}，
     *              對齊 dbml 對 reservations_cache.attrs 的註記（VIP/親子/寵物/紀念日）。
     * @param checkinDate 入住日；退房日固定是入住隔天 11:00（比照 V2__seed_dev.sql 的住期慣例），
     *              需要多晚或跨房重疊情境的呼叫端請直接用 JdbcClient 另外處理，不在這個入口的範圍內。
     * @param guestName 可空：reservations_cache.guest_name 本身就是可空欄位。
     */
    public UUID upsertReservation(String roomNo, List<String> attrs, LocalDate checkinDate, String guestName) {
        long tenantId = tenantFixtures.testTenantId();
        UUID roomId = roomFixtures.roomId(roomNo);
        String reservationRef = "fixture:" + roomNo + ":" + checkinDate;

        OffsetDateTime checkinAt = checkinDate.atTime(15, 0).atZone(clock.getZone()).toOffsetDateTime();
        OffsetDateTime checkoutAt = checkinDate.plusDays(1).atTime(11, 0).atZone(clock.getZone()).toOffsetDateTime();
        OffsetDateTime syncedAt = OffsetDateTime.now(clock);
        String attrsJson = toAttrsJson(attrs);

        return jdbcClient
                .sql(
                        """
                        INSERT INTO reservations_cache
                            (id, tenant_id, reservation_ref, room_id, guest_name, lang, attrs,
                             checkin_at, checkout_at, source, synced_at, superseded_at)
                        VALUES (?, ?, ?, ?, ?, NULL, ?::jsonb, ?, ?, 'excel', ?, NULL)
                        ON CONFLICT (tenant_id, reservation_ref) DO UPDATE SET
                            room_id = excluded.room_id,
                            guest_name = excluded.guest_name,
                            attrs = excluded.attrs,
                            checkin_at = excluded.checkin_at,
                            checkout_at = excluded.checkout_at,
                            synced_at = excluded.synced_at,
                            superseded_at = NULL
                        RETURNING id
                        """)
                .params(
                        UuidV7.generate(),
                        tenantId,
                        reservationRef,
                        roomId,
                        guestName,
                        attrsJson,
                        checkinAt,
                        checkoutAt,
                        syncedAt)
                .query(UUID.class)
                .single();
    }

    private String toAttrsJson(List<String> attrs) {
        if (attrs == null || attrs.isEmpty()) {
            return "{}";
        }
        Map<String, Boolean> map = attrs.stream()
                .collect(java.util.stream.Collectors.toMap(k -> k, k -> Boolean.TRUE, (a, b) -> a, java.util.LinkedHashMap::new));
        return objectMapper.writeValueAsString(map);
    }
}
