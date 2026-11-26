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
            // 儲存空間不在也回 404，這裡分不出來；不再查儲存空間，因為正式環境只給物件權限，查了反而 403。
            // 儲存空間名稱設錯時第一次上傳就會失敗，不會只停在讀不到照片
            if (e.getCode() != NOT_FOUND) {
                throw e;
            }
            return Optional.empty();
        }
    }
}
