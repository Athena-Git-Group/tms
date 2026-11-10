package com.athena.tms.push;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code tms.push.*}：FCM 憑證與消費者重試參數。 */
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
        /** Firebase service account JSON 的檔案路徑；空白表示不啟用 FCM、改用只寫日誌的替身。 */
        private String credentialsPath = "";

        public String getCredentialsPath() {
            return credentialsPath;
        }

        public void setCredentialsPath(String credentialsPath) {
            this.credentialsPath = credentialsPath;
        }
    }

    public static class Retry {
        /** 第一次重試前等多久，之後每次加倍。 */
        private Duration initialInterval = Duration.ofSeconds(1);
        /** 暫時性錯誤最多重試幾次，用盡後送 dead letter topic。 */
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
