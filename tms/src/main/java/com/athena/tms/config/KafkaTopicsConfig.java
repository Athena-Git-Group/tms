package com.athena.tms.config;

import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * 應用啟動時宣告自己會寫入的 topic，讓分區數與保留期限寫在程式裡，不依賴 broker 的自動建立。
 *
 * <p>共用的 SaaS 叢集通常關掉自動建立。沒宣告的 topic 會讓每次送出卡在等 metadata，再整批失敗；
 * pms-status 雖然這個 repo 裡沒有消費者，仍是給 PMS 整合讀的回寫，所以照樣宣告。
 * 副本數不寫，交給 broker 的預設值，因為 Kafka 不會替既有 topic 補副本，寫死 1 會一路帶進正式環境。
 */
@Configuration(proxyBeanMethods = false)
public class KafkaTopicsConfig {

    @Bean
    KafkaAdmin.NewTopics tmsTopics(KafkaTopicsProperties topics) {
        KafkaTopicsProperties.Retention retention = topics.getRetention();
        return new KafkaAdmin.NewTopics(
                topic(topics.getTaskEvents(), topics.getPartitions(), retention.getTaskEvents()),
                topic(topics.getPush(), topics.getPartitions(), retention.getPush()),
                topic(topics.getPmsStatus(), topics.getPartitions(), retention.getPmsStatus()));
    }

    private static NewTopic topic(String name, int partitions, Duration retention) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(retention.toMillis()))
                .build();
    }
}
