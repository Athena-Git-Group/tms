package com.athena.tms.push;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PushDeliveryRecorder {

    public static final String STATUS_SENT = "sent";
    public static final String STATUS_SKIPPED_NO_TOKEN = "skipped_no_token";
    public static final String STATUS_FAILED = "failed";

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public PushDeliveryRecorder(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public Optional<String> statusOf(long tenantId, String idempotencyKey) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT status FROM push_deliveries WHERE tenant_id = ? AND idempotency_key = ?",
                String.class, tenantId, idempotencyKey);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<PushDeviceAddress> activeDevices(long tenantId, UUID staffId) {
        return jdbcTemplate.query(
                """
                SELECT id, push_token, platform
                  FROM staff_devices
                 WHERE tenant_id = ? AND staff_id = ? AND revoked_at IS NULL AND push_token IS NOT NULL
                 ORDER BY registered_at
                """,
                (rs, rowNum) -> new PushDeviceAddress(
                        rs.getObject("id", UUID.class), rs.getString("push_token"), rs.getString("platform")),
                tenantId, staffId);
    }

    public void revokeDevice(long tenantId, UUID deviceId) {
        jdbcTemplate.update(
                "UPDATE staff_devices SET revoked_at = ? WHERE tenant_id = ? AND id = ? AND revoked_at IS NULL",
                OffsetDateTime.now(clock), tenantId, deviceId);
    }

    public void record(long tenantId, String idempotencyKey, UUID recipientStaffId, String status, String lastError) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime sentAt = STATUS_SENT.equals(status) ? now : null;
        jdbcTemplate.update(
                """
                INSERT INTO push_deliveries
                    (tenant_id, idempotency_key, recipient_staff_id, status, attempts, last_error, created_at, sent_at)
                VALUES (?, ?, ?, ?, 1, ?, ?, ?)
                ON CONFLICT (tenant_id, idempotency_key) DO UPDATE
                   SET status = EXCLUDED.status,
                       attempts = push_deliveries.attempts + 1,
                       last_error = EXCLUDED.last_error,
                       sent_at = COALESCE(push_deliveries.sent_at, EXCLUDED.sent_at)
                """,
                tenantId, idempotencyKey, recipientStaffId, status, lastError, now, sentAt);
    }
}
