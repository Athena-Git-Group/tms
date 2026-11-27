package com.athena.web.bdd.pages;

import com.athena.web.bdd.support.BrowserRegex;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class UiWords {

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

        public static Language of(String nameOrOption) {
            String key = nameOrOption.strip();
            return Arrays.stream(values())
                    .filter(language -> language.name.equals(key) || language.option.equals(key))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "介面語言只接受繁體中文、英文、日文、越南文或它們的選項名稱，收到「" + nameOrOption + "」"));
        }
    }

    private static final Map<String, List<String>> TRANSLATIONS = Map.ofEntries(
            Map.entry("員工編號", List.of("Employee ID", "社員番号", "Mã nhân viên")),
            Map.entry("密碼", List.of("Password", "パスワード", "Mật khẩu")),
            Map.entry("登入", List.of("Sign In", "ログイン", "Đăng nhập")),
            Map.entry("登出", List.of("Sign Out", "ログアウト", "Đăng xuất")),
            Map.entry("語言", List.of("Language", "言語", "Ngôn ngữ")),
            Map.entry("帳號", List.of("Account", "アカウント", "Tài khoản")),
            Map.entry("打開側欄", List.of("Open sidebar", "サイドバーを開く", "Mở thanh bên")),
            Map.entry("收合側欄", List.of("Collapse sidebar", "サイドバーを閉じる", "Thu gọn thanh bên")),
            Map.entry("櫃檯功能", List.of("Front desk menu", "フロントメニュー", "Menu lễ tân")),
            Map.entry("詳情", List.of("Details", "詳細", "Chi tiết")),
            Map.entry("報修照片", List.of("Reported photos", "報告写真", "Ảnh báo hỏng")),
            Map.entry("完修照片", List.of("Completion photos", "完了写真", "Ảnh hoàn tất sửa chữa")),
            Map.entry("照片無法顯示", List.of("Photo unavailable", "写真を表示できません", "Không thể hiển thị ảnh")));

    private UiWords() {
    }

    public static String in(Language language, String traditionalChinese) {
        List<String> others = translationsOf(traditionalChinese);
        return switch (language) {
            case TRADITIONAL_CHINESE -> traditionalChinese;
            case ENGLISH -> others.get(0);
            case JAPANESE -> others.get(1);
            case VIETNAMESE -> others.get(2);
        };
    }

    public static Pattern anyLanguage(String traditionalChinese) {
        String alternatives = Arrays.stream(Language.values())
                .map(language -> BrowserRegex.literal(in(language, traditionalChinese)))
                .collect(Collectors.joining("|"));
        return Pattern.compile("^(?:" + alternatives + ")$");
    }

    public static Pattern endsWithAnyLanguage(String traditionalChinese) {
        String alternatives = Arrays.stream(Language.values())
                .map(language -> BrowserRegex.literal(in(language, traditionalChinese)))
                .collect(Collectors.joining("|"));
        return Pattern.compile("(?:" + alternatives + ")$", Pattern.CASE_INSENSITIVE);
    }

    public static String traditionalChineseOf(String anyLanguage) {
        String key = anyLanguage.strip();
        if (TRANSLATIONS.containsKey(key)) {
            return key;
        }
        return TRANSLATIONS.entrySet().stream()
                .filter(entry -> entry.getValue().contains(key))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("「" + anyLanguage + "」不在 UiWords 的譯文表裡"));
    }

    private static List<String> translationsOf(String traditionalChinese) {
        List<String> others = TRANSLATIONS.get(traditionalChinese);
        if (others == null) {
            throw new IllegalArgumentException("「" + traditionalChinese + "」還沒有收進 UiWords 的譯文表");
        }
        return others;
    }
}
