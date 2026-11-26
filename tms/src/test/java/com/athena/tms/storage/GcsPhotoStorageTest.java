package com.athena.tms.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.athena.tms.SharedContainers;
import com.google.cloud.NoCredentials;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GcsPhotoStorageTest {

    private Storage storage;

    @BeforeEach
    void connect() {
        storage = StorageOptions.newBuilder()
                .setHost(SharedContainers.fakeGcsUrl())
                .setProjectId("tms-test")
                .setCredentials(NoCredentials.getInstance())
                .build()
                .getService();
    }

    @AfterEach
    void close() throws Exception {
        storage.close();
    }

    @Test
    void missingObjectReadsAsAbsent() {
        String bucket = "photos-" + UUID.randomUUID();
        storage.create(BucketInfo.of(bucket));

        assertThat(new GcsPhotoStorage(storage, bucket).open("repair-photos/1/missing.jpg")).isEmpty();
    }

    @Test
    void missingBucketIsAnErrorNotAMissingPhoto() {
        String bucket = "never-created-" + UUID.randomUUID();

        assertThatThrownBy(() -> new GcsPhotoStorage(storage, bucket).open("repair-photos/1/any.jpg"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(bucket);
    }
}
