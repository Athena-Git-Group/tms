package com.athena.tms.service;

import com.athena.tms.dto.IntegrationRoomStatusEvent;
import com.athena.tms.dto.TaskChange;
import java.util.UUID;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

final class StreamConnection {
    private final Audience audience;
    private final long tenantId;
    private final UUID reservationId;
    private final UUID staffId;
    private final String dept;
    private final SseEmitter emitter;
    private long streamPos = 0L;
    private volatile boolean closed = false;

    private StreamConnection(Audience audience, long tenantId, UUID reservationId, UUID staffId, String dept, SseEmitter emitter) {
        this.audience = audience;
        this.tenantId = tenantId;
        this.reservationId = reservationId;
        this.staffId = staffId;
        this.dept = dept;
        this.emitter = emitter;
    }

    static StreamConnection guest(long tenantId, UUID reservationId, SseEmitter emitter) {
        return new StreamConnection(Audience.GUEST, tenantId, reservationId, null, null, emitter);
    }

    static StreamConnection staff(long tenantId, UUID staffId, String dept, SseEmitter emitter) {
        return new StreamConnection(Audience.STAFF, tenantId, null, staffId, dept, emitter);
    }

    static StreamConnection integration(long tenantId, SseEmitter emitter) {
        return new StreamConnection(Audience.INTEGRATION, tenantId, null, null, null, emitter);
    }

    SseEmitter emitter() {
        return this.emitter;
    }

    boolean wants(TaskChange change) {
        if (change.tenantId() != this.tenantId) {
            return false;
        }
        return switch (this.audience.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> {
                if (this.reservationId != null && this.reservationId.equals(change.reservationId())) {
                    yield true;
                }
                yield false;
            }
            case 1 -> {
                if (this.dept != null && this.dept.equals(change.dept()) || this.staffId != null && this.staffId.equals(change.assigneeId())) {
                    yield true;
                }
                yield false;
            }
            case 2 -> false;
        };
    }

    boolean wantsIntegration(long tenantId) {
        return this.audience == Audience.INTEGRATION && this.tenantId == tenantId;
    }

    synchronized boolean sendInit(ObjectMapper mapper) {
        ObjectNode data = mapper.createObjectNode();
        data.put("stream_pos", this.streamPos);
        return this.write("init", data.toString());
    }

    synchronized boolean sendResync(ObjectMapper mapper) {
        ObjectNode data = mapper.createObjectNode();
        data.put("reason", "connected");
        data.put("refetch", this.audience == Audience.GUEST ? "/guest/home" : "/staff/pool");
        return this.write("resync", data.toString());
    }

    synchronized boolean sendTask(TaskChange change, ObjectMapper mapper) {
        if (this.closed) {
            return false;
        }
        ObjectNode data = mapper.createObjectNode();
        data.put("stream_pos", ++this.streamPos);
        data.put("task_id", change.taskId().toString());
        data.put("seq", change.seq());
        data.put("type", change.type());
        if (change.occurredAt() != null) {
            data.put("occurred_at", change.occurredAt());
        }
        return this.write("task", data.toString());
    }

    synchronized boolean sendRoomStatus(IntegrationRoomStatusEvent event, ObjectMapper mapper) {
        if (this.closed) {
            return false;
        }
        return this.write("room_status", event.eventId(), mapper.writeValueAsString((Object)event));
    }

    synchronized boolean sendHeartbeat(ObjectMapper mapper) {
        if (this.closed) {
            return false;
        }
        ObjectNode data = mapper.createObjectNode();
        if (this.audience != Audience.INTEGRATION) {
            data.put("stream_pos", this.streamPos);
        }
        return this.write("heartbeat", data.toString());
    }

    synchronized boolean sendDegraded(ObjectMapper mapper) {
        if (this.closed) {
            return false;
        }
        ObjectNode data = mapper.createObjectNode();
        data.put("reason", "kafka_consumer_lag");
        return this.write("degraded", data.toString());
    }

    private boolean write(String event, String json) {
        return this.write(event, null, json);
    }

    private boolean write(String event, String id, String json) {
        try {
            SseEmitter.SseEventBuilder builder = SseEmitter.event().name(event);
            if (id != null) {
                builder.id(id);
            }
            this.emitter.send(builder.data((Object)json));
            return true;
        }
        catch (Exception e) {
            this.closed = true;
            return false;
        }
    }

    void completeQuietly() {
        this.closed = true;
        try {
            this.emitter.complete();
        }
        catch (Exception exception) {
        }
    }

    static enum Audience {
        GUEST,
        STAFF,
        INTEGRATION;

    }
}
