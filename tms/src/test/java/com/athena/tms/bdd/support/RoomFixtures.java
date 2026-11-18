package com.athena.tms.bdd.support;

import com.athena.tms.common.UuidV7;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class RoomFixtures {

    private final JdbcClient jdbcClient;
    private final TenantFixtures tenantFixtures;

    public RoomFixtures(JdbcClient jdbcClient, TenantFixtures tenantFixtures) {
        this.jdbcClient = jdbcClient;
        this.tenantFixtures = tenantFixtures;
    }

    public UUID ensureRoom(String roomNo, int floor) {
        long tenantId = tenantFixtures.testTenantId();
        Optional<UUID> existing = jdbcClient
                .sql("SELECT id FROM rooms WHERE tenant_id = ? AND room_no = ?")
                .params(tenantId, roomNo)
                .query(UUID.class)
                .optional();
        if (existing.isPresent()) {
            return existing.get();
        }
        UUID id = UuidV7.generate();
        OffsetDateTime now = OffsetDateTime.now();
        jdbcClient
                .sql(
                        """
                        INSERT INTO rooms (id, tenant_id, room_no, floor, room_type, hk_state, occupancy, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, roomNo, floor, "standard_double", "dirty", "vacant", now)
                .update();
        return id;
    }

    public static int floorFromRoomNo(String roomNo) {
        if (roomNo == null || roomNo.isEmpty() || !Character.isDigit(roomNo.charAt(0))) {
            return 0;
        }
        return Character.getNumericValue(roomNo.charAt(0));
    }

    public UUID roomId(String roomNo) {
        long tenantId = tenantFixtures.testTenantId();
        return jdbcClient
                .sql("SELECT id FROM rooms WHERE tenant_id = ? AND room_no = ?")
                .params(tenantId, roomNo)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new IllegalStateException(
                        "房號「" + roomNo + "」尚未建立，請先呼叫 ensureRoom()"));
    }

    public UUID addRoomMemory(String roomNo, String kind, String title, String content, LocalDate occurredOn) {
        long tenantId = tenantFixtures.testTenantId();
        UUID roomId = roomId(roomNo);
        UUID id = UuidV7.generate();
        String summary = (title == null || title.isBlank()) ? content : title + "：" + content;
        OffsetDateTime occurredAt = occurredOn.atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        jdbcClient
                .sql(
                        """
                        INSERT INTO room_memory (id, tenant_id, room_id, kind, summary, occurred_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)
                .params(id, tenantId, roomId, kind, summary, occurredAt)
                .update();
        return id;
    }
}
