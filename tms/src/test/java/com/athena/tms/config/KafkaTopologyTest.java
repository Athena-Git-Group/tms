package com.athena.tms.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.athena.tms.SharedContainers;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 應用啟動後，它會寫入或讀取的每個 topic 都已照宣告的分區數存在，不靠 broker 自動建立。
 * push topic 事先只建 1 個分區，驗證既有 topic 會被補到宣告的分區數。
 */
@SpringBootTest(properties = "tms.kafka.topic.partitions=3")
@ActiveProfiles("test")
class KafkaTopologyTest {

    private static final String TASK_EVENTS = "topology.tms.task-events";
    private static final String PUSH = "topology.tms.push";
    private static final String PMS_STATUS = "topology.tms.pms-status";

    static {
        SharedContainers.createTopics(PUSH);
    }

    @DynamicPropertySource
    static void wireContainers(DynamicPropertyRegistry registry) {
        PostgreSQLContainer<?> postgres = SharedContainers.postgres();
        String url = SharedContainers.database("topology");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "tms_app");
        registry.add("spring.datasource.password", () -> "tms_app");
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", SharedContainers.kafka()::getBootstrapServers);
        registry.add("tms.kafka.topic.task-events", () -> TASK_EVENTS);
        registry.add("tms.kafka.topic.push", () -> PUSH);
        registry.add("tms.kafka.topic.pms-status", () -> PMS_STATUS);
        SharedContainers.wireStorage(registry, "kafkatopology-photos");
    }

    @Autowired
    private KafkaListenerEndpointRegistry listeners;

    @Test
    void everyTopicAListenerReadsExistsWithTheDeclaredPartitions() throws Exception {
        Set<String> subscribed = listeners.getListenerContainers().stream()
                .flatMap(container -> Arrays.stream(container.getContainerProperties().getTopics()))
                .collect(Collectors.toSet());
        assertThat(subscribed).contains(TASK_EVENTS, PUSH, PUSH + ".retry-0", PUSH + ".retry-1", PUSH + ".retry-2", PUSH + ".DLT");

        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", SharedContainers.kafka().getBootstrapServers()))) {
            assertThat(partitionsOnceSettled(admin, subscribed)).allSatisfy((topic, count) -> assertThat(count).as(topic).isEqualTo(3));
        }
    }

    @Test
    void everyTopicTheAppWritesToExistsWithTheDeclaredPartitionsAndRetention() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", SharedContainers.kafka().getBootstrapServers()))) {
            assertThat(partitionsOnceSettled(admin, List.of(TASK_EVENTS, PUSH, PMS_STATUS)))
                    .containsOnly(Map.entry(TASK_EVENTS, 3), Map.entry(PUSH, 3), Map.entry(PMS_STATUS, 3));

            assertThat(retentionOf(admin, TASK_EVENTS)).isEqualTo(Duration.ofDays(3));
            assertThat(retentionOf(admin, PUSH)).isEqualTo(Duration.ofDays(3));
            assertThat(retentionOf(admin, PMS_STATUS)).isEqualTo(Duration.ofDays(7));
        }
    }

    // broker 的 metadata 是最終一致的：補分區的請求已經成功，負載重時 describe 仍可能短暫看到舊的分區數。
    private static Map<String, Integer> partitionsOnceSettled(AdminClient admin, Collection<String> topics) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (true) {
            Map<String, Integer> partitions = admin.describeTopics(topics).allTopicNames().get().values().stream()
                    .collect(Collectors.toMap(TopicDescription::name, d -> d.partitions().size()));
            if (partitions.values().stream().allMatch(count -> count == 3) || System.nanoTime() > deadline) {
                return partitions;
            }
            Thread.sleep(200);
        }
    }

    private static Duration retentionOf(AdminClient admin, String topic) throws Exception {
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topic);
        Config config = admin.describeConfigs(List.of(resource)).all().get().get(resource);
        return Duration.ofMillis(Long.parseLong(config.get(TopicConfig.RETENTION_MS_CONFIG).value()));
    }
}
