package com.athena.tms.service;

import java.util.Collection;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * 即時推送的 Kafka 消費者（{@code sse-task-events}）分到 partition 之後才算 UP。
 * 在這之前送出的任務事件不會推到 SSE 連線上，所以這個指標放進 readiness 群組：
 * {@code /actuator/health/readiness} 回 UP 時，測試與部署就能放心開始送事件。
 */
@Component
public class StreamConsumerHealthIndicator implements HealthIndicator {

    static final String LISTENER_ID = "sse-task-events";

    private final KafkaListenerEndpointRegistry kafkaListenerRegistry;

    public StreamConsumerHealthIndicator(KafkaListenerEndpointRegistry kafkaListenerRegistry) {
        this.kafkaListenerRegistry = kafkaListenerRegistry;
    }

    @Override
    public Health health() {
        MessageListenerContainer container = kafkaListenerRegistry.getListenerContainer(LISTENER_ID);
        if (container == null) {
            return Health.down().withDetail("reason", "listener_not_registered").build();
        }
        if (!container.isRunning()) {
            return Health.down().withDetail("reason", "listener_not_running").build();
        }
        Collection<TopicPartition> assigned = container.getAssignedPartitions();
        if (assigned == null || assigned.isEmpty()) {
            return Health.down().withDetail("reason", "no_partitions_assigned").build();
        }
        return Health.up().withDetail("assignedPartitions", assigned.size()).build();
    }
}
