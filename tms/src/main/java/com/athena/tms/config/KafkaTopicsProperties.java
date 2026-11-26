package com.athena.tms.config;

import java.time.Duration;
import lombok.Generated;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix="tms.kafka.topic")
public class KafkaTopicsProperties {
    private String taskEvents = "tms.task-events";
    private String push = "tms.push";
    private String pmsStatus = "tms.pms-status";
    private int partitions = 12;
    private final Retention retention = new Retention();

    public String topicFor(String kind) {
        return switch (kind) {
            case "task_event" -> this.taskEvents;
            case "push" -> this.push;
            case "pms_status" -> this.pmsStatus;
            default -> throw new IllegalArgumentException("未知 outbox kind: " + kind);
        };
    }

    public String getPushDeadLetter() {
        return this.push + ".DLT";
    }

    public int getPartitions() {
        return this.partitions;
    }

    public void setPartitions(int partitions) {
        this.partitions = partitions;
    }

    public Retention getRetention() {
        return this.retention;
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

    public static class Retention {
        private Duration taskEvents = Duration.ofDays(3);
        private Duration push = Duration.ofDays(3);
        private Duration pmsStatus = Duration.ofDays(7);

        public Duration getTaskEvents() {
            return this.taskEvents;
        }

        public void setTaskEvents(Duration taskEvents) {
            this.taskEvents = taskEvents;
        }

        public Duration getPush() {
            return this.push;
        }

        public void setPush(Duration push) {
            this.push = push;
        }

        public Duration getPmsStatus() {
            return this.pmsStatus;
        }

        public void setPmsStatus(Duration pmsStatus) {
            this.pmsStatus = pmsStatus;
        }
    }
}
