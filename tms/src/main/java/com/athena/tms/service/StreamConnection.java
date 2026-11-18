package com.athena.tms.service;

import com.athena.tms.dto.IntegrationRoomStatusEvent;
import com.athena.tms.dto.TaskChange;
import com.athena.tms.dto.desk.DeskBoardChange;
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

    boolean seesDeskBoard() {
        return this.subscriber.seesDeskBoard();
    }

    boolean seesDeskBoardOf(long tenantId) {
        return this.subscriber.seesDeskBoard() && this.subscriber.tenantId() == tenantId;
    }

    /**
     * {@code boardFields} 是 {@link #boardFields} 組好的看板內容，同一則事件的每條連線共用同一份；
     * 是 null 時表示後端沒組出來，訊號照送、不帶看板內容，畫面就會重抓整份看板。
     */
    synchronized boolean sendTask(TaskChange change, ObjectNode boardFields, ObjectMapper mapper) {
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
        if (boardFields != null && this.seesDeskBoard()) {
            data.setAll(boardFields);
        }
        return this.write("task", data.toString());
    }

    static ObjectNode boardFields(DeskBoardChange change, ObjectMapper mapper) {
        ObjectNode fields = mapper.createObjectNode();
        switch (change) {
            case DeskBoardChange.Upsert upsert -> {
                fields.put("board", "upsert");
                fields.set("card", mapper.valueToTree(upsert.card()));
            }
            case DeskBoardChange.Remove remove -> fields.put("board", "remove");
        }
        fields.put("card_seq", change.cardSeq());
        fields.put("business_date", change.businessDate().toString());
        return fields;
    }

    synchronized boolean sendRoomStatus(IntegrationRoomStatusEvent event, ObjectMapper mapper) {
        if (this.closed) {
            return false;
        }
        return this.write("room_status", event.eventId(), mapper.writeValueAsString((Object)event));
    }

    // 員工的心跳另外帶後端目前是不是降級，漏接 degraded 或 recovered 的客戶端靠它對齊；住客與對接系統的心跳維持原樣。
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
