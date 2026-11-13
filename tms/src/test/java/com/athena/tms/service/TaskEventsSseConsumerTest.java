package com.athena.tms.service;

import com.athena.tms.dto.TaskChange;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import tools.jackson.databind.ObjectMapper;

/**
 * 每個 instance 的 SSE 消費者都會讀到所有租戶的事件。這裡驗證它只在這個 instance
 * 有該租戶的連線時才查資料庫，沒有連線的租戶不產生任何查詢。
 * 推送內容與對象由 14-sse-stream.feature 的場景覆蓋。
 */
class TaskEventsSseConsumerTest {

    private static final long CONNECTED_TENANT = 1L;
    private static final long OTHER_TENANT = 2L;

    private StreamConnectionRegistry registry;
    private TenantScopedExecutor tenantScopedExecutor;
    private TaskEventsSseConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        registry = new StreamConnectionRegistry(objectMapper, "30m");
        tenantScopedExecutor = Mockito.mock(TenantScopedExecutor.class);
        consumer = new TaskEventsSseConsumer(registry, tenantScopedExecutor, Mockito.mock(JdbcTemplate.class),
                objectMapper, "10s", Mockito.mock(KafkaListenerEndpointRegistry.class));
    }

    @Test
    @DisplayName("沒有任何連線時，事件不查資料庫")
    void noConnectionsNoQuery() {
        consumer.onRecord(event(CONNECTED_TENANT, "claimed"));

        Mockito.verifyNoInteractions(tenantScopedExecutor);
    }

    @Test
    @DisplayName("只有別的租戶有連線時，這個租戶的事件不查資料庫")
    void otherTenantsConnectionDoesNotTriggerQuery() {
        registry.connectStaff(CONNECTED_TENANT, UUID.randomUUID(), "housekeeping");

        consumer.onRecord(event(OTHER_TENANT, "claimed"));

        Mockito.verifyNoInteractions(tenantScopedExecutor);
    }

    @Test
    @DisplayName("租戶有員工連線但沒有整合連線時，只查推送對象、不查房況")
    void staffConnectionQueriesRoutingOnly() {
        registry.connectStaff(CONNECTED_TENANT, UUID.randomUUID(), "housekeeping");
        routingFindsTask();

        consumer.onRecord(event(CONNECTED_TENANT, "inspection_passed"));

        Mockito.verify(tenantScopedExecutor, Mockito.times(1))
                .callAsTenant(ArgumentMatchers.eq(CONNECTED_TENANT), ArgumentMatchers.any());
    }

    @Test
    @DisplayName("租戶有整合連線時，查房況推給整合端")
    void integrationConnectionQueriesRoomStatus() {
        registry.connectIntegration(CONNECTED_TENANT, List::of);
        routingFindsTask();

        consumer.onRecord(event(CONNECTED_TENANT, "inspection_passed"));

        Mockito.verify(tenantScopedExecutor, Mockito.times(2))
                .callAsTenant(ArgumentMatchers.eq(CONNECTED_TENANT), ArgumentMatchers.any());
    }

    private void routingFindsTask() {
        TaskChange change = new TaskChange(CONNECTED_TENANT, UUID.randomUUID(), 1, "inspection_passed", null,
                "housekeeping", null, null);
        Mockito.when(tenantScopedExecutor.callAsTenant(ArgumentMatchers.anyLong(), ArgumentMatchers.<Supplier<Object>>any()))
                .thenReturn(change, (Object) null);
    }

    private static ConsumerRecord<String, String> event(long tenantId, String type) {
        UUID taskId = UUID.randomUUID();
        String envelope = """
                {"tenant_id":%d,"task_id":"%s","seq":1,"type":"%s"}
                """.formatted(tenantId, taskId, type);
        return new ConsumerRecord<>("tms.task-events", 0, 0L, "task:" + taskId, envelope);
    }
}
