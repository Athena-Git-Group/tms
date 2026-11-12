package com.athena.tms.push;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tms.push")
public class PushProperties {

    private final Fcm fcm = new Fcm();
    private final Retry retry = new Retry();

    public Fcm getFcm() {
        return fcm;
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
