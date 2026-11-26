package com.athena.tms.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.athena.tms.SharedContainers;
import com.google.cloud.NoCredentials;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 官方程式庫對 fake-gcs-server 的寫入、讀出、覆寫都成立。
 * 帶 {@code -Dfakegcs.url=http://localhost:4443} 時改打本機 docker compose 起的模擬服務。
 */
class FakeGcsSmokeTest {

    @Test
    void writesReadsAndOverwritesOnTheEmulator() throws Exception {
        String host = System.getProperty("fakegcs.url", "");
        if (host.isBlank()) {
            host = SharedContainers.fakeGcsUrl();
        }
        String bucket = "smoke-" + UUID.randomUUID();
        try (Storage storage = StorageOptions.newBuilder()
                .setHost(host)
                .setProjectId("tms-smoke")
                .setCredentials(NoCredentials.getInstance())
                .build()
                .getService()) {
            storage.create(BucketInfo.of(bucket));
            BlobInfo blob = BlobInfo.newBuilder(bucket, "repair-photos/1/smoke.jpg").setContentType("image/jpeg").build();

            storage.create(blob, "first".getBytes(StandardCharsets.UTF_8));
            assertThat(storage.readAllBytes(bucket, blob.getName())).asString(StandardCharsets.UTF_8).isEqualTo("first");

            storage.create(blob, "second".getBytes(StandardCharsets.UTF_8));
            assertThat(storage.readAllBytes(bucket, blob.getName())).asString(StandardCharsets.UTF_8).isEqualTo("second");
            assertThat(storage.get(bucket, blob.getName()).getContentType()).isEqualTo("image/jpeg");

            assertThatThrownBy(() -> storage.readAllBytes(bucket, "repair-photos/1/missing.jpg"))
                    .isInstanceOfSatisfying(StorageException.class, e -> assertThat(e.getCode()).isEqualTo(404));
        }
    }
}
