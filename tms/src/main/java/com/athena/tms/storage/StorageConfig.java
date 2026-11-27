package com.athena.tms.storage;

import com.google.cloud.NoCredentials;
import com.google.cloud.http.HttpTransportOptions;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageSettings.class)
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    private static final Duration STORAGE_CALL_TIMEOUT = Duration.ofSeconds(10);

    private static final int CONFLICT = 409;

    private static final String EMULATOR_PROJECT = "tms-local";

    @Bean(destroyMethod = "close")
    Storage storage(
            @Value("${tms.storage.bucket}") String bucket,
            StorageSettings settings) {
        StorageOptions.Builder options = StorageOptions.newBuilder()
                .setTransportOptions(HttpTransportOptions.newBuilder()
                        .setConnectTimeout((int) STORAGE_CALL_TIMEOUT.toMillis())
                        .setReadTimeout((int) STORAGE_CALL_TIMEOUT.toMillis())
                        .build())
                .setRetrySettings(StorageOptions.getDefaultRetrySettings().toBuilder()
                        .setTotalTimeoutDuration(STORAGE_CALL_TIMEOUT)
                        .build());
        return switch (settings.mode()) {
            case GCS -> cloudStorage(options, bucket);
            case EMULATOR -> emulatorStorage(options, bucket, settings.emulatorHost());
        };
    }

    private static Storage cloudStorage(StorageOptions.Builder options, String bucket) {
        log.info("照片儲存：雲端儲存，儲存空間 {}", bucket);
        return options.build().getService();
    }

    private static Storage emulatorStorage(StorageOptions.Builder options, String bucket, String emulatorHost) {
        if (emulatorHost.isBlank()) {
            throw new IllegalStateException(
                    "tms.storage.mode=emulator 要設 tms.storage.emulator-host（環境變數 TMS_STORAGE_EMULATOR_HOST）；"
                            + "正式環境要以 prod 設定組啟動");
        }
        Storage storage = options
                .setHost(emulatorHost)
                .setProjectId(EMULATOR_PROJECT)
                .setCredentials(NoCredentials.getInstance())
                .build()
                .getService();
        try {
            ensureBucket(storage, bucket);
        } catch (RuntimeException e) {
            IllegalStateException startupFailure = new IllegalStateException(
                    "照片儲存模擬服務 " + emulatorHost + " 建立儲存空間 " + bucket
                            + " 失敗；本機請先 docker compose up -d，正式環境要以 prod 設定組啟動", e);
            try {
                storage.close();
            } catch (Exception closeFailure) {
                startupFailure.addSuppressed(closeFailure);
            }
            throw startupFailure;
        }
        log.info("照片儲存：模擬服務 {}，儲存空間 {}", emulatorHost, bucket);
        return storage;
    }

    private static void ensureBucket(Storage storage, String bucket) {
        if (storage.get(bucket) != null) {
            return;
        }
        try {
            storage.create(BucketInfo.of(bucket));
        } catch (StorageException e) {
            if (e.getCode() != CONFLICT) {
                throw e;
            }
        }
    }
}
