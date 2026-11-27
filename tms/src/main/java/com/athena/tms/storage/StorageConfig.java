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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final int CONFLICT = 409;

    private static final String EMULATOR_PROJECT = "tms-local";

    @Bean(destroyMethod = "close")
    Storage storage(
            @Value("${tms.storage.bucket}") String bucket,
            @Value("${tms.storage.emulator-host:}") String emulatorHost) {
        StorageOptions.Builder options = StorageOptions.newBuilder()
                .setTransportOptions(HttpTransportOptions.newBuilder()
                        .setConnectTimeout((int) TIMEOUT.toMillis())
                        .setReadTimeout((int) TIMEOUT.toMillis())
                        .build())
                .setRetrySettings(StorageOptions.getDefaultRetrySettings().toBuilder()
                        .setTotalTimeoutDuration(TIMEOUT)
                        .build());
        if (emulatorHost == null || emulatorHost.isBlank()) {
            log.info("照片儲存：雲端儲存，儲存空間 {}", bucket);
            return options.build().getService();
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
            closeQuietly(storage);
            throw new IllegalStateException(
                    "照片儲存模擬服務 " + emulatorHost + " 建立儲存空間 " + bucket
                            + " 失敗；本機請先 docker compose up -d，正式環境要以 prod 設定組啟動", e);
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

    private static void closeQuietly(Storage storage) {
        try {
            storage.close();
        } catch (Exception ignored) {
        }
    }
}
