package com.athena.tms.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 產生簽進 JWT 的密碼指紋，密碼一改，舊權杖的指紋就對不上而失效。
 * JWT 的內容任何持有者都讀得出來，所以簽進去的是密碼雜湊的指紋，不是雜湊本身。
 */
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
