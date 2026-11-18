package com.athena.tms.bdd.support;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

public class KafkaGroupAwait {

    private final AdminClient admin;

    public KafkaGroupAwait(String bootstrapServers) {
        this.admin = AdminClient.create(Map.of("bootstrap.servers", bootstrapServers));
    }

    public boolean awaitCaughtUp(String groupId, String topic, Duration timeout) {
        TopicPartition partition = new TopicPartition(topic, 0);
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                long end = endOffset(partition);
                if (end == 0) {
                    return true;
                }
                OffsetAndMetadata committed = admin.listConsumerGroupOffsets(groupId)
                        .partitionsToOffsetAndMetadata().get().get(partition);
                if (committed != null && committed.offset() >= end) {
                    return true;
                }
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (ExecutionException e) {
                throw new IllegalStateException("查詢 Kafka offset 失敗", e);
            }
        }
        return false;
    }

    private long endOffset(TopicPartition partition) throws ExecutionException, InterruptedException {
        ListOffsetsResult.ListOffsetsResultInfo info =
                admin.listOffsets(Map.of(partition, OffsetSpec.latest())).all().get().get(partition);
        return info.offset();
    }

    public void close() {
        admin.close();
    }
}
