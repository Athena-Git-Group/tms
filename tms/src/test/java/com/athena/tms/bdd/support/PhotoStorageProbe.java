package com.athena.tms.bdd.support;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import java.util.Optional;

public class PhotoStorageProbe {

    private final Storage storage;
    private final String bucket;

    public PhotoStorageProbe(Storage storage, String bucket) {
        this.storage = storage;
        this.bucket = bucket;
    }

    public record StoredObject(byte[] bytes, String contentType) {
    }

    public Optional<StoredObject> read(String key) {
        Blob blob = storage.get(bucket, key);
        if (blob == null) {
            return Optional.empty();
        }
        return Optional.of(new StoredObject(blob.getContent(), blob.getContentType()));
    }

    public void put(String key, byte[] bytes, String contentType) {
        storage.create(BlobInfo.newBuilder(bucket, key).setContentType(contentType).build(), bytes);
    }

    public void delete(String key) {
        storage.delete(bucket, key);
    }

    public static String key(long tenantId, String photoId) {
        return "repair-photos/" + tenantId + "/" + photoId + ".jpg";
    }
}
