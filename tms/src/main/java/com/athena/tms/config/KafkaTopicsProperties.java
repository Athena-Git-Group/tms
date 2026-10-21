package com.athena.tms.config;

import com.athena.tms.common.codes.OutboxKind;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * kind→topic 對映走設定鍵（tms.kafka.topic.*，預設值在
 * application.properties）而不是寫死：topic 名是跨服務契約，環境間
 * （dev/staging/prod）要能不改碼改名。
 */
@Component
@ConfigurationProperties(prefix = "tms.kafka.topic")
@Getter
@Setter
public class KafkaTopicsProperties {

    private String taskEvents = "tms.task-events";
    private String push = "tms.push";

    public String topicFor(String kind) {
        return switch (kind) {
            case OutboxKind.TASK_EVENT -> taskEvents;
            case OutboxKind.PUSH -> push;
            default -> throw new IllegalArgumentException("未知 outbox kind: " + kind);
        };
    }
}
