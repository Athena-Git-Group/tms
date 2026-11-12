package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.error.ForbiddenException;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.dto.NotificationResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class NotificationService {

    private static final String SELECT_NOTIFICATION_COLUMNS =
            "SELECT id, event, title, body, target, created_at, read_at FROM notifications";

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final RowMapper<NotificationResponse> rowMapper;

    public NotificationService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.rowMapper = (rs, rowNum) -> mapRow(rs, objectMapper);
    }

    public List<NotificationResponse> list() {
        CurrentActor actor = CurrentActor.require();
        return jdbcTemplate.query(
                SELECT_NOTIFICATION_COLUMNS + " WHERE tenant_id = ? AND recipient_staff_id = ? ORDER BY created_at DESC",
                rowMapper,
                actor.tenantId(),
                actor.staffId());
    }

    public int unreadCount(long tenantId, UUID staffId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM notifications
                 WHERE tenant_id = ? AND recipient_staff_id = ? AND read_at IS NULL
                """,
                Integer.class, tenantId, staffId);
        return count == null ? 0 : count;
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId) {
        CurrentActor actor = CurrentActor.require();
        UUID recipientStaffId = jdbcTemplate
                .queryForList(
                        "SELECT recipient_staff_id FROM notifications WHERE tenant_id = ? AND id = ?",
                        UUID.class,
                        actor.tenantId(),
                        notificationId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("找不到通知 " + notificationId));
        if (!recipientStaffId.equals(actor.staffId())) {
            throw new ForbiddenException("這則通知不是你的，不能標記已讀");
        }

        jdbcTemplate.update(
                "UPDATE notifications SET read_at = ? WHERE tenant_id = ? AND id = ? AND read_at IS NULL",
                OffsetDateTime.now(clock),
                actor.tenantId(),
                notificationId);

        return jdbcTemplate.queryForObject(
                SELECT_NOTIFICATION_COLUMNS + " WHERE tenant_id = ? AND id = ?",
                rowMapper,
                actor.tenantId(),
                notificationId);
    }

    @Transactional
    public List<NotificationResponse> markAllRead() {
        CurrentActor actor = CurrentActor.require();
        jdbcTemplate.update(
                "UPDATE notifications SET read_at = ? WHERE tenant_id = ? AND recipient_staff_id = ? AND read_at IS NULL",
                OffsetDateTime.now(clock),
                actor.tenantId(),
                actor.staffId());
        return list();
    }

    private static NotificationResponse mapRow(ResultSet rs, ObjectMapper objectMapper) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        String event = rs.getString("event");
        String title = rs.getString("title");
        String body = rs.getString("body");
        JsonNode target = parseTarget(rs.getString("target"), objectMapper);
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        OffsetDateTime readAt = rs.getObject("read_at", OffsetDateTime.class);
        return new NotificationResponse(id, event, title, body, target, createdAt, readAt);
    }

    private static JsonNode parseTarget(String json, ObjectMapper objectMapper) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException e) {
            return null;
        }
    }
}
