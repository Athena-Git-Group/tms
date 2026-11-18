package com.athena.tms.service;

import com.athena.tms.dto.IntegrationRoomStatusEvent;
import com.athena.tms.dto.TaskChange;
import java.util.UUID;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

final class StreamConnection {
    private final StreamSubscriber subscriber;
    private final SseEmitter emitter;
    private long streamPos = 0L;
    private volatile boolean closed = false;

    private StreamConnection(StreamSubscriber subscriber, SseEmitter emitter) {
        this.subscriber = subscriber;
        this.emitter = emitter;
    }

    static StreamConnection guest(long tenantId, UUID reservationId, SseEmitter emitter) {
        return new StreamConnection(StreamSubscriber.guest(tenantId, reservationId), emitter);
    }

    static StreamConnection staff(long tenantId, UUID staffId, String role, String dept, SseEmitter emitter) {
        return new StreamConnection(StreamSubscriber.staff(tenantId, staffId, role, dept), emitter);
    }

    static StreamConnection integration(long tenantId, SseEmitter emitter) {
        return new StreamConnection(StreamSubscriber.integration(tenantId), emitter);
    }

    SseEmitter emitter() {
        return this.emitter;
    }

    boolean wants(TaskChange change) {
        return this.subscriber.wants(change);
    }

    boolean isStaff() {
        return this.subscriber.audience() == StreamSubscriber.Audience.STAFF;
    }

    boolean wantsIntegration(long tenantId) {
        return this.subscriber.audience() == StreamSubscriber.Audience.INTEGRATION && this.subscriber.tenantId() == tenantId;
    }

    synchronized boolean sendInit(ObjectMapper mapper) {
        ObjectNode data = mapper.createObjectNode();
        data.put("stream_pos", this.streamPos);
        return this.write("init", data.toString());
    }

    synchronized boolean sendResync(ObjectMapper mapper) {
        ObjectNode data = mapper.createObjectNode();
        data.put("reason", "connected");
        data.put("refetch", this.subscriber.refetchPath());
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

    synchronized boolean sendHeartbeat(ObjectMapper mapper, boolean degraded) {
        if (this.closed) {
            return false;
        }
        ObjectNode data = mapper.createObjectNode();
        if (this.subscriber.audience() != StreamSubscriber.Audience.INTEGRATION) {
            data.put("stream_pos", this.streamPos);
        }
        if (this.isStaff()) {
            data.put("degraded", degraded);
        }
        return this.write("heartbeat", data.toString());
    }

    synchronized boolean sendDegraded(String reason, ObjectMapper mapper) {
        if (this.closed) {
            return false;
        }
        ObjectNode data = mapper.createObjectNode();
        data.put("reason", reason);
        return this.write("degraded", data.toString());
    }

    synchronized boolean sendRecovered(ObjectMapper mapper) {
        if (this.closed) {
            return false;
        }
        return this.write("recovered", mapper.createObjectNode().toString());
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
}
