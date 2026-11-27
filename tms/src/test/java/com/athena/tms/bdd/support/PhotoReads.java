package com.athena.tms.bdd.support;

import com.athena.tms.bdd.ScenarioWorld;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

public final class PhotoReads {

    private PhotoReads() {
    }

    public static ScenarioWorld.ApiResponse assertReadableAsStored(
            BddApiClient apiClient, PhotoStorageProbe photoStorage, DbProbe dbProbe, String jwt, String photoId) {
        Map<String, Object> row = dbProbe
                .queryOne("SELECT storage_ref FROM repair_photos WHERE id = ?", UUID.fromString(photoId))
                .orElseThrow(() -> new AssertionError("repair_photos 查無照片 " + photoId));
        Object storageRef = row.get("storage_ref");
        if (storageRef == null) {
            throw new AssertionError("照片 " + photoId + " 的 storage_ref 為空");
        }
        byte[] stored = photoStorage.read(String.valueOf(storageRef))
                .orElseThrow(() -> new AssertionError("照片儲存裡沒有照片 " + photoId + " 的物件 " + storageRef))
                .bytes();

        ScenarioWorld.ApiResponse response = apiClient.exchangeBytes(
                HttpMethod.GET, "/repair-photos/" + photoId + "/content", BddApiClient.Auth.staff(jwt), null, null);
        if (response.status() != 200) {
            throw new AssertionError("讀照片 " + photoId + " 預期 200，實際 " + response.status() + "，body=" + response.body());
        }
        assertJpegContentType("讀照片 " + photoId, response);
        if (!Arrays.equals(stored, response.bytes())) {
            throw new AssertionError("讀照片 " + photoId + " 拿到的位元組與照片儲存裡 " + storageRef + " 的內容不同（"
                    + lengthOf(response.bytes()) + " vs " + stored.length + " 位元組）");
        }
        return response;
    }

    public static void assertJpegContentType(String where, ScenarioWorld.ApiResponse response) {
        String contentType = response.header(HttpHeaders.CONTENT_TYPE);
        if (contentType == null || !MediaType.IMAGE_JPEG.equalsTypeAndSubtype(MediaType.parseMediaType(contentType))) {
            throw new AssertionError(where + " 的 Content-Type 預期 image/jpeg，實際 " + contentType);
        }
    }

    public static int lengthOf(byte[] bytes) {
        return bytes == null ? 0 : bytes.length;
    }
}
