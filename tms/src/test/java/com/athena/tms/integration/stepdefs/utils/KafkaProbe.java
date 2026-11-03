/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.kafka.clients.consumer.ConsumerRecord
 *  org.apache.kafka.clients.consumer.KafkaConsumer
 *  org.apache.kafka.common.serialization.StringDeserializer
 */
package com.athena.tms.integration.stepdefs.utils;

import com.athena.tms.integration.stepdefs.CucumberSpringConfiguration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

public final class KafkaProbe {
    public static List<ConsumerRecord<String, String>> consumeAll(String topic, Duration window) {
        Map<String, Object> props = Map.of("bootstrap.servers", CucumberSpringConfiguration.KAFKA.getBootstrapServers(), "group.id", "bdd-" + String.valueOf(UUID.randomUUID()), "auto.offset.reset", "earliest", "key.deserializer", StringDeserializer.class, "value.deserializer", StringDeserializer.class);
        ArrayList<ConsumerRecord<String, String>> records = new ArrayList<ConsumerRecord<String, String>>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);){
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + window.toMillis();
            while (System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(250L)).forEach(records::add);
            }
        }
        return records;
    }

    private KafkaProbe() {
    }
}
