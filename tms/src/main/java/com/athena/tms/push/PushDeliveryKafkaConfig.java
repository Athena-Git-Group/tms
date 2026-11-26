package com.athena.tms.push;

import com.athena.tms.config.KafkaTopicsProperties;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * 推播消費者的接線。暫時失敗的推播改送到重試 topic 等候，不在原分區原地重試，
 * 否則一個租戶的 FCM 出狀況時，同分區排在後面、其他租戶的推播都要跟著等。
 * 重試用完才進 DLT。重試 topic 與 DLT 由 Spring Kafka 依 push topic 名稱推導並宣告，分區數與 push 相同。
 * <p>資料庫連不上也算暫時失敗。連線池用盡或資料庫切換主機通常幾秒就恢復，這時直接進 DLT 等於把推播丟掉。
 */
@Configuration(proxyBeanMethods = false)
@EnableKafkaRetryTopic
public class PushDeliveryKafkaConfig {

    public static final String LISTENER_FACTORY = "pushDeliveryListenerContainerFactory";

    @Bean(LISTENER_FACTORY)
    ConcurrentKafkaListenerContainerFactory<String, String> pushDeliveryListenerContainerFactory(
            ConsumerFactory<?, ?> defaultConsumerFactory,
            KafkaTopicsProperties topics,
            PushProperties pushProperties) {
        Map<String, Object> config = new HashMap<>(defaultConsumerFactory.getConfigurationProperties());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setConcurrency(pushProperties.getConsumer().getConcurrency());
        // DLT 的消費者只負責記錄與計數。第一次上線時它還沒有讀取進度，從最早開始讀會把 DLT 裡累積的舊訊息全部重新記一次。
        factory.setContainerCustomizer(container -> {
            String[] subscribed = container.getContainerProperties().getTopics();
            if (subscribed != null && Arrays.asList(subscribed).contains(topics.getPushDeadLetter())) {
                container.getContainerProperties().getKafkaConsumerProperties()
                        .setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
            }
        });
        return factory;
    }

    @Bean
    RetryTopicConfiguration pushRetryTopics(
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaTopicsProperties topics,
            PushProperties pushProperties) {
        PushProperties.Retry retry = pushProperties.getRetry();
        return RetryTopicConfigurationBuilder.newInstance()
                .includeTopic(topics.getPush())
                .listenerFactory(LISTENER_FACTORY)
                .maxAttempts(retry.getMaxAttempts() + 1)
                .exponentialBackoff(retry.getInitialInterval().toMillis(), 2.0, retry.getMaxInterval().toMillis())
                .retryOn(List.of(
                        PushTransientException.class,
                        TransientDataAccessException.class,
                        RecoverableDataAccessException.class,
                        CannotGetJdbcConnectionException.class,
                        CannotCreateTransactionException.class,
                        SQLTransientException.class,
                        SQLRecoverableException.class))
                .traversingCauses()
                .retryTopicSuffix(".retry")
                .suffixTopicsWithIndexValues()
                .sameIntervalTopicReuseStrategy(SameIntervalTopicReuseStrategy.MULTIPLE_TOPICS)
                .dltSuffix(".DLT")
                .autoCreateTopicsWith(topics.getPartitions(), (short) -1)
                .dltHandlerMethod("pushDeliveryConsumer", "onDeadLetter")
                .dltProcessingFailureStrategy(DltStrategy.FAIL_ON_ERROR)
                .create(kafkaTemplate);
    }
}
