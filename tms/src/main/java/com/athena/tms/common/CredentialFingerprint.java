package com.athena.tms.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** JWT 的內容任何持有者都讀得出來，所以簽進權杖的是密碼雜湊的指紋，不是雜湊本身。 */
public final class CredentialFingerprint {

    private static final int HEX_LENGTH = 16;

    private CredentialFingerprint() {}

    public static String of(String passwordHash) {
        if (passwordHash == null) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HEX_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
