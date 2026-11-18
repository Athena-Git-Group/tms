package com.athena.web.bdd.pages;

import com.athena.web.bdd.support.BrowserRegex;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 介面語言的四種說法，以及帳號與登入相關畫面上幾個固定字句在各語言的寫法。
 *
 * <p>登入頁與帳號面板會隨介面語言換字，按鈕、欄名與面板標題都跟著變。page object 找這些元素時用
 * {@link #anyLanguage(String)}，不管畫面現在是哪一種語言都找得到；要確認畫面已經換成某一種語言時用
 * {@link #in(Language, String)} 取那一種語言的寫法。
 *
 * <p>譯文取自雛形 {@code ui/desk.js} 的字典，規格只寫了繁體中文；產品的翻譯定案後要改的只有這張表。
 */
public final class UiWords {

    /**
     * 介面語言。
     *
     * @param name 規格句型裡的說法，例如「日文」
     * @param option 語言選單與語言頁上的選項名稱，用該語言自己的寫法，例如「日本語」
     * @param code 存在這台裝置上的語言代碼
     */
    public enum Language {
        TRADITIONAL_CHINESE("繁體中文", "繁體中文", "zh-TW"),
        ENGLISH("英文", "English", "en"),
        JAPANESE("日文", "日本語", "ja"),
        VIETNAMESE("越南文", "Tiếng Việt", "vi");

        private final String name;
        private final String option;
        private final String code;

        Language(String name, String option, String code) {
            this.name = name;
            this.option = option;
            this.code = code;
        }

        public String displayName() {
            return name;
        }

        public String option() {
            return option;
        }

        public String code() {
            return code;
        }

        /** 規格句型裡的說法（「日文」）或選項名稱（「日本語」）都認得；認不得就直接失敗。 */
        public static Language of(String nameOrOption) {
            String key = nameOrOption.strip();
            return Arrays.stream(values())
                    .filter(language -> language.name.equals(key) || language.option.equals(key))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "介面語言只接受繁體中文、英文、日文、越南文或它們的選項名稱，收到「" + nameOrOption + "」"));
        }
    }

    /**
     * 繁體中文的字句對到英文、日文、越南文的寫法。
     *
     * <p>「打開側欄」「收合側欄」「櫃檯功能」「詳情」四句雛形的字典沒有，譯文是暫定的，等母語人士審稿；
     * 產品實際用的寫法不同時，改這裡就好。
     */
    private static final Map<String, List<String>> TRANSLATIONS = Map.of(
            "員工編號", List.of("Employee ID", "社員番号", "Mã nhân viên"),
            "密碼", List.of("Password", "パスワード", "Mật khẩu"),
            "登入", List.of("Sign In", "ログイン", "Đăng nhập"),
            "登出", List.of("Sign Out", "ログアウト", "Đăng xuất"),
            "語言", List.of("Language", "言語", "Ngôn ngữ"),
            "帳號", List.of("Account", "アカウント", "Tài khoản"),
            "打開側欄", List.of("Open sidebar", "サイドバーを開く", "Mở thanh bên"),
            "收合側欄", List.of("Collapse sidebar", "サイドバーを閉じる", "Thu gọn thanh bên"),
            "櫃檯功能", List.of("Front desk menu", "フロントメニュー", "Menu lễ tân"),
            "詳情", List.of("Details", "詳細", "Chi tiết"));

    private UiWords() {
    }

    /** 這句繁體中文在某一種語言的寫法。 */
    public static String in(Language language, String traditionalChinese) {
        List<String> others = translationsOf(traditionalChinese);
        return switch (language) {
            case TRADITIONAL_CHINESE -> traditionalChinese;
            case ENGLISH -> others.get(0);
            case JAPANESE -> others.get(1);
            case VIETNAMESE -> others.get(2);
        };
    }

    /** 整句比對：畫面上是這句在四種語言裡任何一種的寫法都算。 */
    public static Pattern anyLanguage(String traditionalChinese) {
        String alternatives = Arrays.stream(Language.values())
                .map(language -> BrowserRegex.literal(in(language, traditionalChinese)))
                .collect(Collectors.joining("|"));
        return Pattern.compile("^(?:" + alternatives + ")$");
    }

    /**
     * 結尾比對：以這句在四種語言裡任何一種的寫法結尾都算，不分大小寫。
     * 給名稱前面接著別的字的元素用，例如詳情面板的名稱是「{單號} 詳情」。
     */
    public static Pattern endsWithAnyLanguage(String traditionalChinese) {
        String alternatives = Arrays.stream(Language.values())
                .map(language -> BrowserRegex.literal(in(language, traditionalChinese)))
                .collect(Collectors.joining("|"));
        return Pattern.compile("(?:" + alternatives + ")$", Pattern.CASE_INSENSITIVE);
    }

    private static List<String> translationsOf(String traditionalChinese) {
        List<String> others = TRANSLATIONS.get(traditionalChinese);
        if (others == null) {
            throw new IllegalArgumentException("「" + traditionalChinese + "」還沒有收進 UiWords 的譯文表");
        }
        return others;
    }
}
