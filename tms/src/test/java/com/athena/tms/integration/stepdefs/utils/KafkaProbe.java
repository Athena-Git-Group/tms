package com.athena.tms.integration.stepdefs.utils;

import com.athena.tms.integration.stepdefs.CucumberSpringConfiguration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

public final class KafkaProbe {

    private KafkaProbe() {
    }

    public static List<ConsumerRecord<String, String>> consumeAll(String topic, Duration window) {
        Map<String, Object> props = Map.of(
                "bootstrap.servers", CucumberSpringConfiguration.KAFKA.getBootstrapServers(),
                "group.id", "bdd-" + UUID.randomUUID(),
                "auto.offset.reset", "earliest",
                "key.deserializer", StringDeserializer.class,
                "value.deserializer", StringDeserializer.class);
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + window.toMillis();
            while (System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(250)).forEach(records::add);
            }
        }
        return records;
    }

    public static int partitionCount(String topic) {
        try (AdminClient admin = AdminClient.create(
                Map.of("bootstrap.servers", CucumberSpringConfiguration.KAFKA.getBootstrapServers()))) {
            return admin.describeTopics(List.of(topic)).allTopicNames().get().get(topic).partitions().size();
        } catch (Exception e) {
            throw new IllegalStateException("查 topic " + topic + " 的分區數失敗", e);
        }
    }
}
