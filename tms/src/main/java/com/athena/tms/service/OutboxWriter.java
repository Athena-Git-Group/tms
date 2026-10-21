package com.athena.tms.service;

import com.athena.tms.common.codes.OutboxKind;
import com.athena.tms.entity.OutboxMessage;
import com.athena.tms.entity.TaskEvent;
import com.athena.tms.dao.OutboxMessageRepository;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Outbox 寫側：呼叫者必須已在交易中（TaskEngine 的狀態變更交易、
 * 之後的 PMS 回寫交易），這裡只負責把訊息落進同一筆交易——落匣與業務變更
 * 同生共死，應用在 commit 後當掉也只是「送出延遲」，不會掉事件。
 *
 * <p>aggregate_key 同時是 Kafka 分區鍵與「同鍵訊息依序送出」的串行邊界，跟一致性邊界走：
 * task_event/push 的順序敏感範圍是單一任務（task:&lt;uuid&gt;）。
 *
 * <p>payload 一律包 envelope 且帶 tenant_id：topic 全租戶共用，
 * 消費端只有靠事件自述租戶才能做授權過濾。
 */
@Component
public class OutboxWriter {

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxMessageRepository outboxMessageRepository, ObjectMapper objectMapper) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 任務事件上輸送帶。idempotency_key 取 task:&lt;uuid&gt;:seq:&lt;n&gt;——
     * seq 在 (tenant, task) 內唯一且轉移失敗不佔號，同一筆事件不管重試幾次
     * 都是同一把鍵，(tenant, kind, idempotency_key) 唯一約束因此能擋重複落匣。
     */
    public OutboxMessage enqueueTaskEvent(TaskEvent event) {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("tenant_id", event.getTenantId());
        envelope.put("task_id", event.getTaskId().toString());
        envelope.put("seq", event.getSeq());
        envelope.put("type", event.getType());
        envelope.put("actor_role", event.getActorRole());
        if (event.getActorId() != null) {
            envelope.put("actor_id", event.getActorId().toString());
        }
        if (event.getPayload() != null) {
            envelope.set("payload", readTree(event.getPayload()));
        }
        envelope.put("occurred_at", event.getCreatedAt().toString());
        return enqueue(event.getTenantId(), OutboxKind.TASK_EVENT,
                "task:" + event.getTaskId(),
                "task:" + event.getTaskId() + ":seq:" + event.getSeq(),
                envelope.toString());
    }

    /** 推播訊息。MVP 只有 producer 側，消費者屬下一期。idempotency_key 由呼叫端給——重送語意只有業務知道。 */
    public OutboxMessage enqueuePush(long tenantId, UUID taskId, String idempotencyKey, String payloadJson) {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("tenant_id", tenantId);
        envelope.put("task_id", taskId.toString());
        envelope.set("payload", readTree(payloadJson));
        return enqueue(tenantId, OutboxKind.PUSH, "task:" + taskId, idempotencyKey, envelope.toString());
    }

    private OutboxMessage enqueue(long tenantId, String kind, String aggregateKey,
                                  String idempotencyKey, String payload) {
        OutboxMessage message = new OutboxMessage();
        message.setTenantId(tenantId);
        message.setKind(kind);
        message.setAggregateKey(aggregateKey);
        message.setIdempotencyKey(idempotencyKey);
        message.setPayload(payload);
        message.setCreatedAt(OffsetDateTime.now());
        return outboxMessageRepository.save(message);
    }

    /** 已是 JSON 的字串以節點嵌入 envelope，不然會被二次編碼成帶跳脫的字串欄位。 */
    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new IllegalStateException("outbox payload 不是合法 JSON", e);
        }
    }
}
