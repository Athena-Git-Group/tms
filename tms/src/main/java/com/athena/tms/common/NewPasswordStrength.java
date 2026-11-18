package com.athena.tms.common;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class NewPasswordStrength {

    public static final int MIN_PASSWORD_LENGTH = 8;

    public static final String MIN_LENGTH = "min_length";

    public static final String LETTER_REQUIRED = "letter_required";

    public static final String DIGIT_REQUIRED = "digit_required";

    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");

    private static final Pattern DIGIT = Pattern.compile("[0-9]");

    private NewPasswordStrength() {
    }

    public static List<String> failedRules(String newPassword) {
        List<String> failed = new ArrayList<>();
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            failed.add(MIN_LENGTH);
        }
        if (!LETTER.matcher(newPassword).find()) {
            failed.add(LETTER_REQUIRED);
        }
        if (!DIGIT.matcher(newPassword).find()) {
            failed.add(DIGIT_REQUIRED);
        }
        return List.copyOf(failed);
    }
}
