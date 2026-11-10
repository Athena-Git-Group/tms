package com.athena.tms.push;

import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 依 {@code tms.push.fcm.credentials-path} 決定推播送出的實作：設定有值而且檔案存在就用
 * {@link FcmPushSender}，否則用 {@link LoggingPushSender}。路徑填了但檔案不存在時不悄悄降級，
 * 直接啟動失敗，否則正式環境可能以為推播在送、其實只在寫日誌。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PushProperties.class)
public class PushSenderConfig {

    private static final Logger log = LoggerFactory.getLogger(PushSenderConfig.class);

    @Bean
    PushSender pushSender(PushProperties properties) {
        String configured = properties.getFcm().getCredentialsPath();
        if (configured == null || configured.isBlank()) {
            log.warn("tms.push.fcm.credentials-path 未設定，推播只寫日誌與送達紀錄，不會打到手機");
            return new LoggingPushSender();
        }
        Path path = Path.of(configured.trim());
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("tms.push.fcm.credentials-path 指向的檔案不存在：" + path);
        }
        return new FcmPushSender(path);
    }
}
