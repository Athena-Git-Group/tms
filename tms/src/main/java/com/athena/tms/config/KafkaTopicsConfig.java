package com.athena.tms.config;

import java.util.Optional;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * 應用程式用到的 topic 一律在這裡宣告分區數與複本數，啟動時由 {@link KafkaAdmin}
 * 建立不存在的 topic，既有 topic 分區數不足時往上加。
 *
 * <p>不宣告的話，topic 會在第一次送訊息時由 broker 自動建立，只有一個分區。
 * 一個分區同一時間只能被同一個消費者群組裡的一個消費者讀，所有租戶的訊息
 * 就只能排成一列處理，加開 instance 或消費者執行緒也分不到工作。
 *
 * <p>分區數只能加不能減，而且加了以後同一個訊息鍵可能落到新的分區。
 * 調大分區數的那一次部署，同一個鍵在調整前後的訊息分別在新舊分區，
 * 兩邊的消費進度不同步，短時間內可能不照順序到達。
 *
 * <p>複本數讓維運人員依 Kafka 叢集的大小決定，因為單機開發環境只能放一份，正式叢集需要多份。
 * 沒有設定時，系統沿用 broker 的 {@code default.replication.factor}。
 * Kafka 不會替已經存在的 topic 補複本，所以程式不寫死 1：寫死的話，正式環境只要漏設一次，
 * topic 就永遠只有一份資料，那台 broker 壞掉時訊息會全部遺失。
 */
@Configuration(proxyBeanMethods = false)
public class KafkaTopicsConfig {

    private final KafkaTopicsProperties topics;
    private final int partitions;
    private final Optional<Short> replicationFactor;

    public KafkaTopicsConfig(
            KafkaTopicsProperties topics,
            @Value("${tms.kafka.partitions:12}") int partitions,
            @Value("${tms.kafka.replication-factor:}") String replicationFactor) {
        this.topics = topics;
        this.partitions = partitions;
        this.replicationFactor = replicationFactor.isBlank()
                ? Optional.empty()
                : Optional.of(Short.parseShort(replicationFactor.trim()));
    }

    @Bean
    KafkaAdmin.NewTopics tmsTopics() {
        return new KafkaAdmin.NewTopics(
                topic(topics.getTaskEvents()),
                topic(topics.getPush()),
                topic(topics.getPushDeadLetter()));
    }

    private NewTopic topic(String name) {
        TopicBuilder builder = TopicBuilder.name(name).partitions(partitions);
        replicationFactor.ifPresent(builder::replicas);
        return builder.build();
    }
}
