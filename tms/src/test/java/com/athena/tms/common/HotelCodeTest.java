package com.athena.tms.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 確認程式裡的飯店識別代碼規則和資料庫 tenants 表上的 CHECK 是同一條。
 *
 * <p>規則的來源是 migration {@code V24__tenant_code.sql}，這裡從 classpath 讀那支 SQL，
 * 取出長度上下限、正規式與保留字清單，逐項和 {@link HotelCode} 比對，測試本身不另抄一份規則。
 * 之後若用新的 migration 改了這兩條 CHECK，要把讀取的檔名改成新的那一支。
 */
class HotelCodeTest {

    private static final String MIGRATION = "db/migration/V24__tenant_code.sql";

    private static int sqlMinLength;
    private static int sqlMaxLength;
    private static String sqlRegex;
    private static Set<String> sqlReserved;

    @BeforeAll
    static void readMigration() throws IOException {
        String sql = readClasspath(MIGRATION);

        String formatCheck = constraintBody(sql, "chk_tenants_code_format");
        Matcher between = Pattern.compile("char_length\\(code\\)\\s+BETWEEN\\s+(\\d+)\\s+AND\\s+(\\d+)").matcher(formatCheck);
        assertThat(between.find()).as("V24 的格式 CHECK 裡找得到長度上下限").isTrue();
        sqlMinLength = Integer.parseInt(between.group(1));
        sqlMaxLength = Integer.parseInt(between.group(2));

        Matcher regex = Pattern.compile("code\\s+~\\s+'([^']*)'").matcher(formatCheck);
        assertThat(regex.find()).as("V24 的格式 CHECK 裡找得到正規式").isTrue();
        sqlRegex = regex.group(1);

        String reservedCheck = constraintBody(sql, "chk_tenants_code_reserved");
        Matcher word = Pattern.compile("'([^']*)'").matcher(reservedCheck);
        sqlReserved = new LinkedHashSet<>();
        while (word.find()) {
            sqlReserved.add(word.group(1));
        }
        assertThat(sqlReserved).as("V24 的保留字 CHECK 裡找得到保留字").isNotEmpty();
    }

    @Test
    void 長度上下限和資料庫一致() {
        assertThat(HotelCode.MIN_LENGTH).isEqualTo(sqlMinLength);
        assertThat(HotelCode.MAX_LENGTH).isEqualTo(sqlMaxLength);
    }

    @Test
    void 正規式和資料庫逐字相同() {
        assertThat(HotelCode.FORMAT_REGEX).isEqualTo(sqlRegex);
    }

    @Test
    void 保留字清單和資料庫逐字相同() {
        assertThat(HotelCode.RESERVED).containsExactlyInAnyOrderElementsOf(sqlReserved);
    }

    @Test
    void 每個保留字都不合格_加上後綴就合格() {
        for (String reserved : sqlReserved) {
            assertThat(HotelCode.isWellFormed(reserved)).as(reserved).isFalse();
            assertThat(HotelCode.isWellFormed(reserved + "-x")).as(reserved + "-x").isTrue();
        }
    }

    @Test
    void 邊界值的判斷和照資料庫規則算出來的一樣() {
        List<String> samples = new ArrayList<>(List.of(
                "ab", "abc", "a".repeat(32), "a".repeat(33),
                "athena", "demo-hotel", "demo-hotel-2", "test-hotel", "a1-b2-c3",
                "Athena", "ATHENA", " athena", "athena ", "athena\n",
                "1abc", "-abc", "abc-", "ab--c", "ab_c", "ab.c", "ab/c", "飯店名",
                "1", "42", ""));
        samples.addAll(sqlReserved);

        for (String sample : samples) {
            assertThat(HotelCode.isWellFormed(sample)).as("「%s」", sample).isEqualTo(passesDatabaseCheck(sample));
        }
    }

    @Test
    void 幾個明確的例子() {
        assertThat(HotelCode.isWellFormed("athena")).isTrue();
        assertThat(HotelCode.isWellFormed("abc")).isTrue();
        assertThat(HotelCode.isWellFormed("a".repeat(32))).isTrue();
        assertThat(HotelCode.isWellFormed("ab")).isFalse();
        assertThat(HotelCode.isWellFormed("a".repeat(33))).isFalse();
        assertThat(HotelCode.isWellFormed("Athena")).isFalse();
        assertThat(HotelCode.isWellFormed("abc-")).isFalse();
        assertThat(HotelCode.isWellFormed("ab--c")).isFalse();
        assertThat(HotelCode.isWellFormed("1abc")).isFalse();
        assertThat(HotelCode.isWellFormed("api")).isFalse();
        assertThat(HotelCode.isWellFormed(null)).isFalse();
    }

    private static boolean passesDatabaseCheck(String code) {
        int length = code.codePointCount(0, code.length());
        return length >= sqlMinLength
                && length <= sqlMaxLength
                && Pattern.compile(sqlRegex).matcher(code).matches()
                && !sqlReserved.contains(code);
    }

    /** 取出某條 CHECK 從名稱到下一個分號之間的內容，讓比對不會讀到別條限制或註解裡的字。 */
    private static String constraintBody(String sql, String constraintName) {
        int start = sql.indexOf("ADD CONSTRAINT " + constraintName);
        assertThat(start).as("V24 裡找得到 %s", constraintName).isGreaterThanOrEqualTo(0);
        int end = sql.indexOf(';', start);
        return sql.substring(start, end);
    }

    private static String readClasspath(String path) throws IOException {
        try (InputStream in = HotelCodeTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("classpath 上找得到 %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
