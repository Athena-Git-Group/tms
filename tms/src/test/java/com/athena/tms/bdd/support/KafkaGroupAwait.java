package com.athena.tms.bdd.support;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

/**
 * 等某個 consumer group 把指定 topic 讀到底（已提交的 offset 追上最新 offset）。
 *
 * <p>用在兩處：情境開始前，等推播消費者把上一個情境留下的訊息處理完，再清空資料庫，
 * 否則消費者會對著已經被清掉的員工寫送達紀錄、撞外鍵、進入重試而拖慢下一個情境；
 * 以及「重送同一筆推播」的情境，要確定消費者確實看過那筆重送的訊息，才能斷言它被跳過。
 * topic 在測試環境只有一個 partition。
 */
public class KafkaGroupAwait {

    private final AdminClient admin;

    public KafkaGroupAwait(String bootstrapServers) {
        this.admin = AdminClient.create(Map.of("bootstrap.servers", bootstrapServers));
    }

    /** 追上回 true；逾時回 false，由呼叫端決定要不要當成失敗。 */
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
