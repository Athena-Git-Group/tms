package com.athena.tms.dto;

import java.security.SecureRandom;

public final class QrTokens {
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int LENGTH = 22;
    private static final SecureRandom RANDOM = new SecureRandom();

    private QrTokens() {
    }

    public static String generate() {
        StringBuilder token = new StringBuilder(22);
        for (int i = 0; i < 22; ++i) {
            token.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return token.toString();
    }
}
