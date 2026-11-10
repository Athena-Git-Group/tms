package com.athena.tms.dto;

import java.security.SecureRandom;
import java.util.Base64;

public final class GuestSessionTokens {
    private static final SecureRandom RANDOM = new SecureRandom();

    private GuestSessionTokens() {
    }

    public static String issue(long tenantId) {
        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        return tenantId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public static Long parseTenantId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        int separator = token.indexOf(46);
        if (separator <= 0 || token.indexOf(46, separator + 1) >= 0 || separator == token.length() - 1) {
            return null;
        }
        try {
            return Long.parseLong(token.substring(0, separator));
        }
        catch (NumberFormatException e) {
            return null;
        }
    }
}
