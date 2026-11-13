package com.athena.tms.push;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tms.push")
public class PushProperties {

    private final Fcm fcm = new Fcm();
    private final Retry retry = new Retry();
    /**
     * 每個 instance 開幾條消費者執行緒讀推播 topic。重試是阻塞式的，一條執行緒卡在某筆的退避上，
     * 它負責的分區全部跟著等；多開幾條，其他分區的推播照常送。上限是 topic 的分區數，多開的閒置。
     */
    private int consumerConcurrency = 3;

    public Fcm getFcm() {
        return fcm;
    }

    public int getConsumerConcurrency() {
        return consumerConcurrency;
    }

    public void setConsumerConcurrency(int consumerConcurrency) {
        this.consumerConcurrency = consumerConcurrency;
    }

    public Retry getRetry() {
        return retry;
    }

    public static class Fcm {
        private String credentialsPath = "";

        public String getCredentialsPath() {
            return credentialsPath;
        }

        public void setCredentialsPath(String credentialsPath) {
            this.credentialsPath = credentialsPath;
        }
    }

    public static class Retry {
        private Duration initialInterval = Duration.ofSeconds(1);
        private int maxAttempts = 3;

        public Duration getInitialInterval() {
            return initialInterval;
        }

        public void setInitialInterval(Duration initialInterval) {
            this.initialInterval = initialInterval;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
    }
}
