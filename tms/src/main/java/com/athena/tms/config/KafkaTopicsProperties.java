/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 *  org.springframework.boot.context.properties.ConfigurationProperties
 *  org.springframework.stereotype.Component
 */
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

    public String topicFor(String kind) {
        return switch (kind) {
            case "task_event" -> this.taskEvents;
            case "push" -> this.push;
            case "pms_status" -> this.pmsStatus;
            default -> throw new IllegalArgumentException("\u672a\u77e5 outbox kind: " + kind);
        };
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
