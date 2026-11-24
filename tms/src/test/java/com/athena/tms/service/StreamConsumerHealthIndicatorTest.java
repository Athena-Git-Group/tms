package com.athena.tms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

class StreamConsumerHealthIndicatorTest {

    private final KafkaListenerEndpointRegistry registry = mock(KafkaListenerEndpointRegistry.class);
    private final MessageListenerContainer container = mock(MessageListenerContainer.class);
    private final StreamConsumerHealthIndicator indicator = new StreamConsumerHealthIndicator(registry);

    @Test
    @DisplayName("即時推送的消費者沒註冊時是 DOWN")
    void downWhenListenerMissing() {
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "listener_not_registered");
    }

    @Test
    @DisplayName("消費者在跑但還沒分到 partition 時是 DOWN")
    void downBeforeAssignment() {
        when(registry.getListenerContainer("sse-task-events")).thenReturn(container);
        when(container.isRunning()).thenReturn(true);
        when(container.getAssignedPartitions()).thenReturn(List.of());

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "no_partitions_assigned");
    }

    @Test
    @DisplayName("消費者停了就算之前分過 partition 也是 DOWN")
    void downWhenStopped() {
        when(registry.getListenerContainer("sse-task-events")).thenReturn(container);
        when(container.isRunning()).thenReturn(false);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("分到 partition 之後是 UP，並帶分到的數量")
    void upOnceAssigned() {
        when(registry.getListenerContainer("sse-task-events")).thenReturn(container);
        when(container.isRunning()).thenReturn(true);
        when(container.getAssignedPartitions()).thenReturn(List.of(
                new TopicPartition("tms.task-events", 0), new TopicPartition("tms.task-events", 1)));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("assignedPartitions", 2);
    }
}
