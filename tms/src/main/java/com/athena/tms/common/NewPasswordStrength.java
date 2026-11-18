package com.athena.tms.common;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 新密碼的強度規則，程式裡只在這一處定義。
 *
 * <p>新密碼要至少 8 碼、含英文字母、含數字。員工 App 與櫃檯畫面設定新密碼都走同一個後端，
 * 所以規則放在後端；沒過的規則全部列出來，畫面可以一次告訴使用者要改哪些。
 *
 * <p>規則代碼（{@link #MIN_LENGTH}、{@link #LETTER_REQUIRED}、{@link #DIGIT_REQUIRED}）會放進
 * 422 {@code new_password_too_weak} 的 {@code context.failedRules}，畫面靠這些字串對照要顯示哪條提示。
 * 改代碼或加規則時，API 契約與櫃檯 Web、員工 App 的對照要一起改。
 */
public final class NewPasswordStrength {

    public static final int MIN_PASSWORD_LENGTH = 8;

    public static final String MIN_LENGTH = "min_length";

    public static final String LETTER_REQUIRED = "letter_required";

    public static final String DIGIT_REQUIRED = "digit_required";

    // 只認 ASCII 的英文字母與數字；全形字、中文、帶重音的字母都不算。
    // 用 find() 找密碼裡有沒有出現一個，密碼裡有換行字元也照樣判斷得出來。
    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");

    private static final Pattern DIGIT = Pattern.compile("[0-9]");

    private NewPasswordStrength() {
    }

    /**
     * 回傳這組新密碼沒過的規則代碼，順序固定是長度、英文字母、數字；全部都過時回空清單。
     *
     * <p>長度以 Java 字串的字元單位計，一個表情符號會算成兩碼。
     * 傳入的密碼不可為 null，空白與 null 由呼叫端先擋下。
     */
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
