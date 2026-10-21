package com.athena.tms.integration.stepdefs.utils;

import com.athena.tms.integration.stepdefs.CucumberSpringConfiguration;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 每次用全新 group 從頭讀整個 topic：場景彼此共用 topic，靠 key 過濾、不靠 offset 管理。 */
public final class KafkaProbe {

    public static List<ConsumerRecord<String, String>> consumeAll(String topic, Duration window) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                CucumberSpringConfiguration.KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "bdd-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
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

    private KafkaProbe() {
    }
}
