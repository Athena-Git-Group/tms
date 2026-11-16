package com.athena.web.bdd.support;

/**
 * 櫃檯 Web 在測試裡的位址：開發伺服器在哪裡、四個畫面各是哪一條路徑。
 *
 * <p>開發伺服器的位址用系統屬性 {@code web.baseUrl} 給，沒給時是 Vite 的預設埠。
 * 四條路徑與 {@code app/web/src/lib/paths.ts} 相同，那邊改了這裡要跟著改。
 */
public final class WebApp {

    public static final String BASE_URL_PROPERTY = "web.baseUrl";

    /** 登入頁。 */
    public static final String LOGIN = "/login";

    /** 改密碼畫面。 */
    public static final String CHANGE_PASSWORD = "/change-password";

    /** 總覽看板。 */
    public static final String BOARD = "/board";

    /** 待派單頁。 */
    public static final String TRIAGE = "/triage";

    private WebApp() {
    }

    /** 開發伺服器的位址，結尾不帶斜線。 */
    public static String baseUrl() {
        return System.getProperty(BASE_URL_PROPERTY, "http://localhost:5173").replaceAll("/+$", "");
    }

    /** 把 {@code /board} 這樣的路徑接成完整網址。 */
    public static String url(String path) {
        return baseUrl() + (path.startsWith("/") ? path : "/" + path);
    }
}
