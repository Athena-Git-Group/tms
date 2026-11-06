package com.athena.tms.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 給簽發權杖的一方與驗證權杖的一方共用：把員工帳號當下的密碼雜湊折成一個短字串，
 * 讓驗證端能認出「這張權杖是用現在這組密碼換的嗎」。
 *
 * <p>折法是 SHA-256 取前 16 個十六進位字元；傳 null 回 null。
 *
 * <p>JWT 的內容任何持有者都讀得出來，所以簽進權杖的是這個短字串，不是密碼雜湊本身。
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
