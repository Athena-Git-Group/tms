package com.athena.tms.config;

import lombok.Generated;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix="tms.kafka.topic")
public class KafkaTopicsProperties {
    private String taskEvents = "tms.task-events";
    private String push = "tms.push";
    private String pmsStatus = "tms.pms-status";
    /** 推播消費者重試用盡後把訊息改送到這裡，等人工查看，不再卡住後面的訊息。 */
    private String pushDeadLetter = "tms.push.DLT";

    public String topicFor(String kind) {
        return switch (kind) {
            case "task_event" -> this.taskEvents;
            case "push" -> this.push;
            case "pms_status" -> this.pmsStatus;
            default -> throw new IllegalArgumentException("未知 outbox kind: " + kind);
        };
    }

    public String getPushDeadLetter() {
        return this.pushDeadLetter;
    }

    public void setPushDeadLetter(String pushDeadLetter) {
        this.pushDeadLetter = pushDeadLetter;
    }

    public String getPmsStatus() {
        return this.pmsStatus;
    }

    public void setPmsStatus(String pmsStatus) {
        this.pmsStatus = pmsStatus;
    }

    @Generated
    public String getTaskEvents() {
        return this.taskEvents;
    }

    @Generated
    public String getPush() {
        return this.push;
    }

    @Generated
    public void setTaskEvents(String taskEvents) {
        this.taskEvents = taskEvents;
    }

    @Generated
    public void setPush(String push) {
        this.push = push;
    }
}
