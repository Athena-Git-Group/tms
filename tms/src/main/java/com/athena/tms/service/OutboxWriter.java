package com.athena.tms.service;

import com.athena.tms.dao.OutboxMessageRepository;
import com.athena.tms.entity.OutboxMessage;
import com.athena.tms.entity.TaskEvent;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class OutboxWriter {
    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public OutboxWriter(OutboxMessageRepository outboxMessageRepository, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate, ApplicationEventPublisher eventPublisher) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.eventPublisher = eventPublisher;
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
        return this.enqueue(tenantId, "push", OutboxWriter.pushKey("task:" + String.valueOf(taskId)), idempotencyKey, envelope.toString());
    }

    /**
     * 推播的排隊鍵：推播對象的鍵前面加 {@code push:}，例如 {@code push:task:<id>}。
     *
     * <p>relay 依排隊鍵一筆一筆按順序送，同一把鍵有一筆重試用完就整把鍵停送。推播若和單的事件共用 {@code task:<id>}，
     * 某一則推播送不出去就會連帶擋住這張單之後的每一則事件，員工事件串流從此收不到這張單的訊號。
     * 推播有自己的鍵，卡住時只擋住後面的推播。所有寫推播的程式都經過這裡。
     */
    static String pushKey(String subject) {
        return "push:" + subject;
    }

    private OutboxMessage enqueue(long tenantId, String kind, String aggregateKey, String idempotencyKey, String payload) {
        OutboxMessage message = new OutboxMessage();
        message.setTenantId(tenantId);
        message.setKind(kind);
        message.setAggregateKey(aggregateKey);
        message.setIdempotencyKey(idempotencyKey);
        message.setPayload(payload);
        message.setCreatedAt(OffsetDateTime.now());
        OutboxMessage saved = (OutboxMessage)this.outboxMessageRepository.save(message);
        this.eventPublisher.publishEvent(new OutboxEnqueued());
        return saved;
    }

    /**
     * 以 idempotency_key 去重寫入一筆 outbox，同鍵已存在就不寫。
     *
     * <p>所有寫入 outbox 的程式都要走這個類別，因為 relay 靠這裡發出的 {@link OutboxEnqueued} 才會在交易提交後立刻送出；
     * 繞過這裡直接 INSERT 的訊息只能等 relay 的定時輪詢。
     */
    public boolean enqueueIfAbsent(long tenantId, String kind, String aggregateKey, String payloadJson, String idempotencyKey, OffsetDateTime createdAt) {
        int inserted = this.jdbcTemplate.update("INSERT INTO outbox (tenant_id, aggregate_key, kind, payload, idempotency_key, status, attempts, created_at)\nVALUES (?, ?, ?, ?::jsonb, ?, 'pending', 0, ?)\nON CONFLICT (tenant_id, kind, idempotency_key) DO NOTHING\n", tenantId, aggregateKey, kind, payloadJson, idempotencyKey, createdAt);
        if (inserted > 0) {
            this.eventPublisher.publishEvent(new OutboxEnqueued());
        }
        return inserted > 0;
    }

    private JsonNode readTree(String json) {
        try {
            return this.objectMapper.readTree(json);
        }
        catch (JacksonException e) {
            throw new IllegalStateException("outbox payload 不是合法 JSON", e);
        }
    }
}
