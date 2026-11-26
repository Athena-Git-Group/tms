package com.athena.tms.bdd.support;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** 測試用的照片內容；DSL「內容」參數的對照表在 {@code 工務單/dsl.md} 的上傳句。 */
public final class PhotoFixtures {

    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private static final byte[] JPEG_HEAD = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] JPEG_TAIL = {(byte) 0xFF, (byte) 0xD9};
    private static final byte[] PNG_HEAD = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private PhotoFixtures() {
    }

    public record PhotoContent(byte[] bytes, String contentType) {
    }

    public static PhotoContent of(String label, String contentName) {
        return switch (contentName) {
            case "一張 JPEG 照片" -> new PhotoContent(jpeg(label), JPEG);
            case "另一張 JPEG 照片" -> new PhotoContent(jpeg(label + "#另一張"), JPEG);
            case "剛好 5 MB 的 JPEG" -> new PhotoContent(jpegOfSize(MAX_BYTES), JPEG);
            case "超過 5 MB 的 JPEG" -> new PhotoContent(jpegOfSize(MAX_BYTES + 1), JPEG);
            case "PNG 圖片" -> new PhotoContent(png(label), PNG);
            case "標成 JPEG 的 PNG 圖片" -> new PhotoContent(png(label), JPEG);
            case "標成 PNG 的 JPEG 照片" -> new PhotoContent(jpeg(label), PNG);
            case "空白內容" -> new PhotoContent(new byte[0], JPEG);
            default -> throw new IllegalArgumentException("不認得的照片內容：" + contentName);
        };
    }

    /** 合法 JPEG：{@code FF D8 FF E0} 開頭、{@code FF D9} 結尾，中間是種子，種子不同內容就不同。 */
    public static byte[] jpeg(String seed) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(JPEG_HEAD);
        out.writeBytes(seed.getBytes(StandardCharsets.UTF_8));
        out.writeBytes(JPEG_TAIL);
        return out.toByteArray();
    }

    private static byte[] jpegOfSize(int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) 0x20);
        System.arraycopy(JPEG_HEAD, 0, bytes, 0, JPEG_HEAD.length);
        System.arraycopy(JPEG_TAIL, 0, bytes, size - JPEG_TAIL.length, JPEG_TAIL.length);
        return bytes;
    }

    private static byte[] png(String seed) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(PNG_HEAD);
        out.writeBytes(seed.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}
