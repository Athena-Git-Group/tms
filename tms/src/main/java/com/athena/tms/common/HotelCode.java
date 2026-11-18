package com.athena.tms.common;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 飯店識別代碼的格式規則，程式裡只在這一處定義。
 *
 * <p>飯店識別代碼是放在發給飯店的網址裡、對外辨認飯店的那一段。合格的代碼長度 3 到 32，
 * 只用小寫英文字母、數字與連字號，第一個字是英文字母，不以連字號結尾、不連用兩個連字號，
 * 也不能是會和前端路徑、後端路徑或常見子網域撞名的保留字。
 *
 * <p>同一條規則也寫在資料庫的 tenants 表上，是 migration {@code V24__tenant_code.sql} 的
 * {@code chk_tenants_code_format} 與 {@code chk_tenants_code_reserved} 兩條 CHECK。
 * 兩處要一起改：資料庫已經上線的 migration 不能再修改，要放寬或收緊規則就寫新的 migration
 * 改 CHECK，同時改這裡。{@code HotelCodeTest} 直接讀那支 migration 比對長度、格式與保留字，
 * 只改一邊測試就會失敗。兩邊不一致時，程式可能把資料庫存得進去的代碼當成不合格而回查不到，
 * 或反過來放行資料庫根本不會有的代碼。
 */
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

    /**
     * 讓呼叫端在查資料庫之前，先排除資料庫裡不可能存在的代碼。
     *
     * <p>資料庫比對代碼時一字不差，所以這裡也不替呼叫端修剪空白或轉小寫；
     * 要是這裡先轉小寫，帶大寫的代碼就會被當成另一家飯店的代碼查到。
     *
     * <p>格式只收英文字母、數字與連字號，合格的代碼每個字都只佔一個字元單位，
     * 所以這裡算的長度和資料庫用 {@code char_length} 算的一樣。
     */
    public static boolean isWellFormed(String code) {
        return code != null
                && code.length() >= MIN_LENGTH
                && code.length() <= MAX_LENGTH
                && FORMAT.matcher(code).matches()
                && !RESERVED.contains(code);
    }
}
