package com.athena.web.bdd.support;

import java.util.List;
import java.util.Map;

public final class GuestTerms {

    public static final List<String> LANGUAGES = List.of("zh-TW", "en", "ja", "vi");

    public static final Map<String, String> LANGUAGE_BY_NAME = Map.of(
            "繁體中文", "zh-TW", "English", "en", "日本語", "ja", "Tiếng Việt", "vi");

    public static final List<String> TAGS = List.of("已送出", "已受理", "處理中", "已完成", "已取消");

    public static final List<String> STEPS = List.of("已送出", "已受理", "處理中", "已完成");

    private static final Map<String, List<String>> TRANSLATIONS = Map.of(
            "已送出", List.of("Sent", "送信済み", "Đã gửi"),
            "已受理", List.of("Received", "受付済み", "Đã tiếp nhận"),
            "處理中", List.of("In progress", "対応中", "Đang xử lý"),
            "已完成", List.of("Done", "完了", "Hoàn tất"),
            "已取消", List.of("Cancelled", "キャンセル済み", "Đã hủy"),
            "進行中", List.of("Active", "対応中", "Đang xử lý"),
            "已結束", List.of("Finished", "完了", "Đã kết thúc"),
            "返回", List.of("Back", "戻る", "Quay lại"));

    private GuestTerms() {
    }

    public static String in(String language, String zhTw) {
        int index = LANGUAGES.indexOf(language);
        if (index < 0) {
            throw new IllegalArgumentException("住客介面只有 " + LANGUAGES + "，收到語言「" + language + "」");
        }
        if (index == 0) {
            return zhTw;
        }
        List<String> others = TRANSLATIONS.get(zhTw);
        if (others == null) {
            throw new IllegalArgumentException("「" + zhTw + "」不是會跟著語言換字的狀態、段落、步名或返回鈕");
        }
        return others.get(index - 1);
    }

    public static String languageCode(String name) {
        String code = LANGUAGE_BY_NAME.get(name.strip());
        if (code == null) {
            throw new IllegalArgumentException("住客介面語言只有 " + LANGUAGE_BY_NAME.keySet() + "，收到「" + name + "」");
        }
        return code;
    }
}
