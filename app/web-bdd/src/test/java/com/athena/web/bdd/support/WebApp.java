package com.athena.web.bdd.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WebApp {

    public static final String BASE_URL_PROPERTY = "web.baseUrl";

    private static final Pattern PATHS_BLOCK = Pattern.compile("export const PATHS\\s*=\\s*\\{([^}]*)\\}");

    private static final Pattern PATH_ENTRY = Pattern.compile("(\\w+)\\s*:\\s*'([^']*)'");

    private static final Path PATHS_SOURCE = Path.of("..", "web", "src", "lib", "paths.ts");

    private static final Map<String, String> PATHS = readPaths(PATHS_SOURCE);

    public static final String LOGIN = path("login");

    public static final String CHANGE_PASSWORD = path("changePassword");

    public static final String BOARD = path("board");

    public static final String TRIAGE = path("triage");

    private WebApp() {
    }

    public static String baseUrl() {
        return System.getProperty(BASE_URL_PROPERTY, "http://localhost:5173").replaceAll("/+$", "");
    }

    public static String underHotel(String hotelCode, String path) {
        return "/" + java.net.URLEncoder.encode(hotelCode, java.nio.charset.StandardCharsets.UTF_8)
                + (path.startsWith("/") ? path : "/" + path);
    }

    public static String url(String path) {
        return baseUrl() + (path.startsWith("/") ? path : "/" + path);
    }

    /** 路徑直接讀櫃檯 Web 的 {@code paths.ts}，畫面改了路徑測試就跟著改，不會有兩份各自漂移。 */
    private static Map<String, String> readPaths(Path source) {
        String text;
        try {
            text = Files.readString(source);
        } catch (IOException e) {
            throw new UncheckedIOException("讀不到櫃檯 Web 的路徑定義 " + source.toAbsolutePath()
                    + "：測試要在 app/web-bdd 目錄下執行", e);
        }
        Matcher block = PATHS_BLOCK.matcher(text);
        if (!block.find()) {
            throw new IllegalStateException(source + " 裡找不到 export const PATHS = { ... }");
        }
        Map<String, String> paths = new LinkedHashMap<>();
        Matcher entry = PATH_ENTRY.matcher(block.group(1));
        while (entry.find()) {
            paths.put(entry.group(1), entry.group(2));
        }
        return Map.copyOf(paths);
    }

    private static String path(String key) {
        String value = PATHS.get(key);
        if (value == null) {
            throw new IllegalStateException(PATHS_SOURCE + " 的 PATHS 沒有「" + key + "」，現有的是 " + PATHS.keySet());
        }
        return value;
    }
}
