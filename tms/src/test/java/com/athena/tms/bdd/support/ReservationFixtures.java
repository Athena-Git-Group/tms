package com.athena.tms.bdd.support;

import com.athena.tms.common.UuidV7;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

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

    public UUID upsertReservation(String roomNo, List<String> attrs, LocalDate checkinDate, String guestName) {
        return upsertReservation(roomNo, attrs, checkinDate, LocalTime.of(15, 0), guestName);
    }

    public UUID upsertReservation(
            String roomNo, List<String> attrs, LocalDate checkinDate, LocalTime checkinTime, String guestName) {
        long tenantId = tenantFixtures.testTenantId();
        UUID roomId = roomFixtures.roomId(roomNo);
        String reservationRef = "fixture:" + roomNo + ":" + checkinDate;

        OffsetDateTime checkinAt = checkinDate.atTime(checkinTime).atZone(clock.getZone()).toOffsetDateTime();
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
