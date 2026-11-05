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

/**
 * 通知中心 API，對應 contracts 通知中心.yaml 的 listNotifications、
 * markNotificationRead、markAllNotificationsRead 三個端點。通知紀錄的建立
 * 不在這裡——outbox push 一人一封展開時由 {@link PushOutboxWriter#enqueue}
 * 一併寫入，本類別只處理員工自己查詢與標記已讀，兩邊職責分開才不會有「誰負責
 * 寫通知」要互相猜的問題。
 */
@Service
public class NotificationService {

    /**
     * 單筆通知的查詢欄位，清單查詢與標記已讀後的重查共用同一份，避免兩處各自
     * 維護一份欄位清單、改了一邊漏改另一邊。
     */
    private static final String SELECT_NOTIFICATION_COLUMNS =
            "SELECT id, title, body, target, created_at, read_at FROM notifications";

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

    /**
     * 誰能標成已讀只認通知本人：這則通知的收件人若不是目前呼叫者，代表呼叫者
     * 想去動別人的已讀狀態，所以用 403 擋下，不是單純查無資料的 404。前端斷線
     * 重送同一個標記已讀請求是常見情況，所以這個動作要能安全重複呼叫，不報錯。
     */
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

    /**
     * 一次性全部已讀是批次版的 {@link #markRead}，但不逐筆檢查收件人——範圍本身
     * 就鎖在呼叫者名下，不會動到別人的通知，所以不需要像單筆標記那樣擋 403。
     */
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

    /**
     * target 落在 jsonb 欄位，但回應需要還原成物件（不是逃脫過的字串）才符合
     * contracts PushTarget 的形狀；壞掉的 JSON 沒有合理的物件可還原，寧可讓
     * 這筆通知的 target 掉成 null，也不要整個請求因為一筆舊資料而炸掉。
     */
    private static NotificationResponse mapRow(ResultSet rs, ObjectMapper objectMapper) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        String title = rs.getString("title");
        String body = rs.getString("body");
        JsonNode target = parseTarget(rs.getString("target"), objectMapper);
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        OffsetDateTime readAt = rs.getObject("read_at", OffsetDateTime.class);
        return new NotificationResponse(id, title, body, target, createdAt, readAt);
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
