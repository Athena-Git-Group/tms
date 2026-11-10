package com.athena.tms.service;

import com.athena.tms.common.TenantContext;
import com.athena.tms.dto.IntegrationEventsResponse;
import com.athena.tms.dto.IntegrationRoomStatusEvent;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class IntegrationEventsService {
    static final int DEFAULT_LIMIT = 100;
    static final int MAX_LIMIT = 500;
    static final long SAFETY_LAG_SECONDS = 3L;
    private final EntityManager entityManager;

    public IntegrationEventsService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public IntegrationEventsResponse pullEvents(String after, String limit) {
        long afterId = IntegrationEventsService.parseAfter(after);
        int pageSize = IntegrationEventsService.parseLimit(limit);
        List<IntegrationRoomStatusEvent> events = this.replayAfter(afterId, pageSize);
        return new IntegrationEventsResponse(events, IntegrationEventsService.nextCursor(events, after));
    }

    public List<IntegrationRoomStatusEvent> replayAfter(long afterId, int limit) {
        long tenantId = TenantContext.require();
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(3L);
        List<Object[]> rows = this.entityManager.createQuery("SELECT te.id, te.type, te.taskId, te.seq, te.createdAt, r.roomNo\nFROM TaskEvent te, Task t, Room r\nWHERE t.tenantId = te.tenantId AND t.id = te.taskId\n  AND r.tenantId = t.tenantId AND r.id = t.roomId\n  AND te.tenantId = :tenantId\n  AND te.id > :afterId\n  AND te.createdAt <= :cutoff\n  AND te.type IN :types\nORDER BY te.id ASC\n", Object[].class).setParameter("tenantId", (Object)tenantId).setParameter("afterId", (Object)afterId).setParameter("cutoff", (Object)cutoff).setParameter("types", IntegrationRoomStatusWhitelist.OUTBOUND_TYPES).setMaxResults(limit).getResultList();
        return rows.stream().map(IntegrationEventsService::toRoomStatus).toList();
    }

    private static IntegrationRoomStatusEvent toRoomStatus(Object[] row) {
        Long id = (Long)row[0];
        String type = (String)row[1];
        return new IntegrationRoomStatusEvent("room_status.v1", Long.toString(id), (String)row[5], IntegrationRoomStatusWhitelist.hkStateFor(type), (UUID)row[2], (Integer)row[3], (OffsetDateTime)row[4]);
    }

    private static String nextCursor(List<IntegrationRoomStatusEvent> events, String after) {
        if (!events.isEmpty()) {
            return events.getLast().eventId();
        }
        return after == null ? "0" : after;
    }

    private static long parseAfter(String after) {
        if (after == null) {
            return 0L;
        }
        try {
            long value = Long.parseLong(after);
            if (value < 0L) {
                throw new IntegrationBadRequestException("after 必須是非負十進位整數");
            }
            return value;
        }
        catch (NumberFormatException e) {
            throw new IntegrationBadRequestException("after 必須是非負十進位整數");
        }
    }

    private static int parseLimit(String limit) {
        int value;
        if (limit == null) {
            return 100;
        }
        try {
            value = Integer.parseInt(limit);
        }
        catch (NumberFormatException e) {
            throw new IntegrationBadRequestException("limit 必須是 1 到 500 的整數");
        }
        if (value < 1 || value > 500) {
            throw new IntegrationBadRequestException("limit 必須是 1 到 500 的整數");
        }
        return value;
    }
}
