package com.athena.tms.push;

import com.athena.tms.config.KafkaTopicsProperties;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration(proxyBeanMethods = false)
public class PushDeliveryKafkaConfig {

    public static final String LISTENER_FACTORY = "pushDeliveryListenerContainerFactory";

    private static final Logger log = LoggerFactory.getLogger(PushDeliveryKafkaConfig.class);

    @Bean(LISTENER_FACTORY)
    ConcurrentKafkaListenerContainerFactory<String, String> pushDeliveryListenerContainerFactory(
            ConsumerFactory<?, ?> defaultConsumerFactory,
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaTopicsProperties topics,
            PushProperties pushProperties) {
        Map<String, Object> config = new HashMap<>(defaultConsumerFactory.getConfigurationProperties());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setConcurrency(pushProperties.getConsumerConcurrency());

        DeadLetterPublishingRecoverer deadLetter = new DeadLetterPublishingRecoverer(
                kafkaTemplate, (record, ex) -> new TopicPartition(topics.getPushDeadLetter(), -1));
        ConsumerRecordRecoverer recoverer = (record, ex) -> {
            log.error("push 訊息重試用盡，改送 {}（offset={} key={}）：{}",
                    topics.getPushDeadLetter(), record.offset(), record.key(), ex.getMessage());
            deadLetter.accept(record, ex);
        };

        ExponentialBackOff backOff = new ExponentialBackOff(
                pushProperties.getRetry().getInitialInterval().toMillis(), 2.0);
        backOff.setMaxAttempts(pushProperties.getRetry().getMaxAttempts());
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
