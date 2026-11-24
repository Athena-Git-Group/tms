package com.athena.web.bdd.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 Playwright {@code evaluate}／{@code evaluateAll} 回傳的 {@code Object} 轉成有型別的值。
 * 每個元素都在這裡逐一檢查型別，瀏覽器腳本回錯形狀時，例外會說出收到什麼，而不是等到之後取用時才在別處爆掉。
 */
public final class JsResult {

    private JsResult() {
    }

    public static List<Object> list(Object raw) {
        if (!(raw instanceof List<?> items)) {
            throw mismatch("陣列", raw);
        }
        return new ArrayList<>(items);
    }

    public static Map<String, Object> object(Object raw) {
        if (!(raw instanceof Map<?, ?> fields)) {
            throw mismatch("物件", raw);
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        fields.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    public static String string(Object raw) {
        if (raw == null || raw instanceof String) {
            return (String) raw;
        }
        throw mismatch("字串", raw);
    }

    public static Number number(Object raw) {
        if (raw == null || raw instanceof Number) {
            return (Number) raw;
        }
        throw mismatch("數字", raw);
    }

    public static Map<String, Number> numberFields(Object raw) {
        Map<String, Number> out = new LinkedHashMap<>();
        object(raw).forEach((key, value) -> out.put(key, number(value)));
        return out;
    }

    public static List<String> strings(Object raw) {
        List<String> out = new ArrayList<>();
        for (Object item : list(raw)) {
            out.add(string(item));
        }
        return out;
    }

    public static List<Number> numbers(Object raw) {
        List<Number> out = new ArrayList<>();
        for (Object item : list(raw)) {
            out.add(number(item));
        }
        return out;
    }

    public static List<Map<String, Object>> objects(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list(raw)) {
            out.add(object(item));
        }
        return out;
    }

    public static List<List<String>> stringRows(Object raw) {
        List<List<String>> out = new ArrayList<>();
        for (Object row : list(raw)) {
            out.add(strings(row));
        }
        return out;
    }

    private static IllegalStateException mismatch(String expected, Object raw) {
        String actual = raw == null ? "null" : raw.getClass().getSimpleName() + " " + raw;
        return new IllegalStateException("瀏覽器回傳的值應該是" + expected + "，收到 " + actual);
    }
}
