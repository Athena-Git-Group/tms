package com.athena.tms.common;

import java.util.Set;
import java.util.regex.Pattern;

public final class HotelCode {

    public static final int MIN_LENGTH = 3;

    public static final int MAX_LENGTH = 32;

    public static final String FORMAT_REGEX = "^[a-z][a-z0-9]*(-[a-z0-9]+)*$";

    public static final Set<String> RESERVED = Set.of(
            "api", "public", "login", "logout", "password", "www", "admin", "app", "desk", "staff",
            "guest", "board", "triage", "static", "assets", "auth", "health", "actuator", "status", "docs",
            "help", "support", "mail", "cdn", "dev", "test", "staging", "tms", "index", "change-password");

    private static final Pattern FORMAT = Pattern.compile(FORMAT_REGEX);

    private HotelCode() {
    }

    public static boolean isWellFormed(String code) {
        return code != null
                && code.length() >= MIN_LENGTH
                && code.length() <= MAX_LENGTH
                && FORMAT.matcher(code).matches()
                && !RESERVED.contains(code);
    }
}
