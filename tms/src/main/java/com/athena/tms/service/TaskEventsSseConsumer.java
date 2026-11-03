/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.kafka.clients.consumer.ConsumerRecord
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.annotation.Value
 *  org.springframework.boot.context.event.ApplicationReadyEvent
 *  org.springframework.boot.convert.DurationStyle
 *  org.springframework.context.event.EventListener
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.kafka.annotation.KafkaListener
 *  org.springframework.kafka.config.KafkaListenerEndpointRegistry
 *  org.springframework.kafka.listener.MessageListenerContainer
 *  org.springframework.stereotype.Component
 *  tools.jackson.databind.JsonNode
 *  tools.jackson.databind.ObjectMapper
 */
package com.athena.tms.service;

import com.athena.tms.dto.IntegrationRoomStatusEvent;
import com.athena.tms.dto.TaskChange;
import com.athena.tms.service.IntegrationRoomStatusWhitelist;
import com.athena.tms.service.StreamConnectionRegistry;
import com.athena.tms.service.TenantScopedExecutor;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class TaskEventsSseConsumer {
    private static final Logger log = LoggerFactory.getLogger(TaskEventsSseConsumer.class);
    private static final int DEDUP_CAPACITY = 10000;
    private final StreamConnectionRegistry registry;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Duration degradedThreshold;
    private final KafkaListenerEndpointRegistry kafkaListenerRegistry;
    private final AtomicBoolean degraded = new AtomicBoolean(false);
    private final Map<UUID, Integer> deliveredSeqByTask = new LinkedHashMap<UUID, Integer>(256, 0.75f, true){

        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Integer> eldest) {
            return this.size() > 10000;
        }
    };

    public TaskEventsSseConsumer(StreamConnectionRegistry registry, TenantScopedExecutor tenantScopedExecutor, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, @Value(value="${tms.stream.degraded-threshold:10s}") String degradedThreshold, KafkaListenerEndpointRegistry kafkaListenerRegistry) {
        this.registry = registry;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.degradedThreshold = DurationStyle.detectAndParse((String)degradedThreshold);
        this.kafkaListenerRegistry = kafkaListenerRegistry;
    }

    @EventListener(value={ApplicationReadyEvent.class})
    void logResolvedGroupId() {
        MessageListenerContainer container = this.kafkaListenerRegistry.getListenerContainer("sse-task-events");
        if (container != null) {
            log.info("SSE consumer \u9019\u500b instance \u5be6\u969b\u4f7f\u7528\u7684 Kafka consumer group id\uff1a{}", (Object)container.getGroupId());
        }
    }

    @KafkaListener(id="sse-task-events", topics={"${tms.kafka.topic.task-events}"}, groupId="${tms.stream.consumer-group:sse}-#{T(java.util.UUID).randomUUID()}")
    public void onRecord(ConsumerRecord<String, String> record) {
        try {
            this.trackConsumerLag(record.timestamp());
            this.dispatch((String)record.value());
        }
        catch (Exception e) {
            log.warn("SSE \u5206\u767c\u5931\u6557\uff0c\u7565\u904e\u6b64\u4e8b\u4ef6\uff08offset={}\uff0c\u6389\u9234\u53ef\u63a5\u53d7\u3001\u771f\u76f8\u5728 DB\uff09: {}", (Object)record.offset(), (Object)e.getMessage());
        }
    }

    private void trackConsumerLag(long recordTimestamp) {
        if (recordTimestamp <= -1L) {
            return;
        }
        long lagMillis = System.currentTimeMillis() - recordTimestamp;
        if (lagMillis > this.degradedThreshold.toMillis()) {
            if (this.degraded.compareAndSet(false, true)) {
                this.registry.broadcastDegraded();
            }
        } else {
            this.degraded.set(false);
        }
    }

    private void dispatch(String envelopeJson) {
        int eventSeq;
        JsonNode envelope = this.objectMapper.readTree(envelopeJson);
        JsonNode tenantId = envelope.get("tenant_id");
        JsonNode taskId = envelope.get("task_id");
        JsonNode seq = envelope.get("seq");
        JsonNode type = envelope.get("type");
        if (tenantId == null || taskId == null || seq == null || type == null) {
            log.debug("envelope \u7f3a\u5fc5\u8981\u6b04\u4f4d\uff0c\u7565\u904e: {}", (Object)envelopeJson);
            return;
        }
        UUID task = UUID.fromString(taskId.asText());
        if (this.alreadyDelivered(task, eventSeq = seq.asInt())) {
            return;
        }
        JsonNode occurredAt = envelope.get("occurred_at");
        TaskChange change = this.loadRouting(tenantId.asLong(), task, eventSeq, type.asText(), occurredAt == null ? null : occurredAt.asText());
        if (change == null) {
            log.debug("\u4efb\u52d9 {} \u67e5\u7121\u6295\u5f71\u5217\uff0c\u7565\u904e\u6b64\u9234", (Object)task);
            return;
        }
        this.registry.broadcastTask(change);
        this.pushIntegration(tenantId.asLong(), task, eventSeq, type.asText());
    }

    private void pushIntegration(long tenantId, UUID taskId, int seq, String type) {
        String hkState = IntegrationRoomStatusWhitelist.hkStateFor(type);
        if (hkState == null) {
            return;
        }
        try {
            IntegrationRoomStatusEvent event = this.loadRoomStatus(tenantId, taskId, seq, hkState);
            if (event == null) {
                log.debug("\u4efb\u52d9 {} seq {} \u67e5\u7121\u4e8b\u4ef6\u5217\u6216\u623f\u865f\uff0c\u6574\u5408\u63a8\u9001\u7565\u904e\uff08\u56de\u653e\u515c\u5e95\uff09", (Object)taskId, (Object)seq);
                return;
            }
            this.registry.broadcastRoomStatus(tenantId, event);
        }
        catch (Exception e) {
            log.warn("\u6574\u5408\u63a8\u9001\u67e5\u8a62\u5931\u6557\uff0c\u7565\u904e\u6b64\u4e8b\u4ef6\uff08task={} seq={}\uff0c\u56de\u653e\u515c\u5e95\uff09: {}", new Object[]{taskId, seq, e.getMessage()});
        }
    }

    private IntegrationRoomStatusEvent loadRoomStatus(long tenantId, UUID taskId, int seq, String hkState) {
        return this.tenantScopedExecutor.callAsTenant(tenantId, () -> {
            List rows = this.jdbcTemplate.query("SELECT te.id, te.created_at, r.room_no\n  FROM task_events te\n  JOIN tasks t ON t.tenant_id = te.tenant_id AND t.id = te.task_id\n  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id\n WHERE te.tenant_id = ? AND te.task_id = ? AND te.seq = ?\n", (rs, rowNum) -> new IntegrationRoomStatusEvent("room_status.v1", Long.toString(rs.getLong("id")), rs.getString("room_no"), hkState, taskId, seq, rs.getObject("created_at", OffsetDateTime.class)), new Object[]{tenantId, taskId, seq});
            return rows.isEmpty() ? null : (IntegrationRoomStatusEvent)rows.getFirst();
        });
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private boolean alreadyDelivered(UUID taskId, int seq) {
        Map<UUID, Integer> map = this.deliveredSeqByTask;
        synchronized (map) {
            Integer seen = this.deliveredSeqByTask.get(taskId);
            if (seen != null && seen >= seq) {
                return true;
            }
            this.deliveredSeqByTask.put(taskId, seq);
            return false;
        }
    }

    private TaskChange loadRouting(long tenantId, UUID taskId, int seq, String type, String occurredAt) {
        return this.tenantScopedExecutor.callAsTenant(tenantId, () -> {
            List rows = this.jdbcTemplate.query("SELECT dept, assignee_id, reservation_id\n  FROM tasks\n WHERE tenant_id = ? AND id = ?\n", (rs, rowNum) -> new TaskChange(tenantId, taskId, seq, type, occurredAt, rs.getString("dept"), rs.getObject("assignee_id", UUID.class), rs.getObject("reservation_id", UUID.class)), new Object[]{tenantId, taskId});
            return rows.isEmpty() ? null : (TaskChange)rows.getFirst();
        });
    }
}
