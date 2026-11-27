package com.athena.tms.repair;

import com.athena.tms.common.error.PayloadTooLargeException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.common.error.UnsupportedMediaTypeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;

final class RepairPhotoContent {

    static final int MAX_BYTES = 5 * 1024 * 1024;

    private static final String JPEG = "image/jpeg";

    private RepairPhotoContent() {
    }

    static byte[] read(String contentType, long contentLength, InputStream body) {
        if (contentLength > MAX_BYTES) {
            throw tooLarge();
        }
        byte[] bytes;
        try {
            bytes = body.readNBytes(MAX_BYTES + 1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (bytes.length > MAX_BYTES) {
            throw tooLarge();
        }
        if (bytes.length == 0) {
            throw new UnprocessableEntityException("photo_empty", "照片內容是空的");
        }
        if (!isJpegType(contentType) || !startsLikeJpeg(bytes)) {
            throw new UnsupportedMediaTypeException("photo_type_unsupported", "照片只收 JPEG");
        }
        return bytes;
    }

    private static PayloadTooLargeException tooLarge() {
        return new PayloadTooLargeException("photo_too_large", "照片超過 5 MB");
    }

    private static boolean isJpegType(String contentType) {
        if (contentType == null) {
            return false;
        }
        int parameters = contentType.indexOf(';');
        String type = parameters < 0 ? contentType : contentType.substring(0, parameters);
        return JPEG.equals(type.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean startsLikeJpeg(byte[] bytes) {
        return bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
    }
}
