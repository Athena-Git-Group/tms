/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Component
 *  tools.jackson.core.JacksonException
 *  tools.jackson.databind.JsonNode
 *  tools.jackson.databind.ObjectMapper
 *  tools.jackson.databind.node.ObjectNode
 */
package com.athena.tms.service;

import com.athena.tms.dao.OutboxMessageRepository;
import com.athena.tms.entity.OutboxMessage;
import com.athena.tms.entity.TaskEvent;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class OutboxWriter {
    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxMessageRepository outboxMessageRepository, ObjectMapper objectMapper) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.objectMapper = objectMapper;
    }

    public OutboxMessage enqueueTaskEvent(TaskEvent event) {
        ObjectNode envelope = this.objectMapper.createObjectNode();
        envelope.put("tenant_id", event.getTenantId());
        envelope.put("task_id", event.getTaskId().toString());
        envelope.put("seq", event.getSeq());
        envelope.put("type", event.getType());
        envelope.put("actor_role", event.getActorRole());
        if (event.getActorId() != null) {
            envelope.put("actor_id", event.getActorId().toString());
        }
        if (event.getPayload() != null) {
            envelope.set("payload", this.readTree(event.getPayload()));
        }
        envelope.put("occurred_at", event.getCreatedAt().toString());
        return this.enqueue(event.getTenantId(), "task_event", "task:" + String.valueOf(event.getTaskId()), "task:" + String.valueOf(event.getTaskId()) + ":seq:" + event.getSeq(), envelope.toString());
    }

    public OutboxMessage enqueuePush(long tenantId, UUID taskId, String idempotencyKey, String payloadJson) {
        ObjectNode envelope = this.objectMapper.createObjectNode();
        envelope.put("tenant_id", tenantId);
        envelope.put("task_id", taskId.toString());
        envelope.set("payload", this.readTree(payloadJson));
        return this.enqueue(tenantId, "push", "task:" + String.valueOf(taskId), idempotencyKey, envelope.toString());
    }

    private OutboxMessage enqueue(long tenantId, String kind, String aggregateKey, String idempotencyKey, String payload) {
        OutboxMessage message = new OutboxMessage();
        message.setTenantId(tenantId);
        message.setKind(kind);
        message.setAggregateKey(aggregateKey);
        message.setIdempotencyKey(idempotencyKey);
        message.setPayload(payload);
        message.setCreatedAt(OffsetDateTime.now());
        return (OutboxMessage)this.outboxMessageRepository.save(message);
    }

    private JsonNode readTree(String json) {
        try {
            return this.objectMapper.readTree(json);
        }
        catch (JacksonException e) {
            throw new IllegalStateException("outbox payload \u4e0d\u662f\u5408\u6cd5 JSON", e);
        }
    }
}
