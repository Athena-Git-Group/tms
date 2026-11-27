package com.athena.tms.storage;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GcsPhotoStorage implements PhotoStorage {

    private static final int NOT_FOUND = 404;

    private final Storage storage;
    private final String bucket;

    public GcsPhotoStorage(Storage storage, @Value("${tms.storage.bucket}") String bucket) {
        this.storage = storage;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] jpeg) {
        storage.create(BlobInfo.newBuilder(bucket, key).setContentType("image/jpeg").build(), jpeg);
    }

    @Override
    public Optional<byte[]> open(String key) {
        try {
            return Optional.of(storage.readAllBytes(bucket, key));
        } catch (StorageException e) {
            if (e.getCode() != NOT_FOUND) {
                throw e;
            }
            return Optional.empty();
        }
    }
}
