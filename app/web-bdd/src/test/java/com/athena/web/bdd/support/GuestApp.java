package com.athena.web.bdd.support;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 住客掃碼頁的網址。每一頁都在 {@code /g/{代碼}} 底下，代碼是房間 QR Code 上的那一枚；
 * 住客沒有飯店識別代碼，也不經過登入頁。下面的常數是代碼之後的那一段，和產品 {@code guest/paths.ts} 一致。
 */
public final class GuestApp {

    /** 首頁。 */
    public static final String HOME = "";

    /** 備品補充頁。 */
    public static final String SUPPLY = "supply";

    /** 設備報修頁。 */
    public static final String REPAIR = "repair";

    /** 送出成功頁。 */
    public static final String SENT = "sent";

    /** 我的需求清單。 */
    public static final String REQUESTS = "requests";

    private GuestApp() {
    }

    /** 某一筆需求的進度頁那一段。 */
    public static String request(Object requestId) {
        return REQUESTS + "/" + requestId;
    }

    /** 某一頁資訊頁那一段，{@code key} 是飯店設定的資訊頁鍵。 */
    public static String guide(String key) {
        return "guide/" + URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** 這枚代碼底下某一頁的路徑，例如 {@code /g/abc/supply}；{@code sub} 給 {@link #HOME} 就是首頁。 */
    public static String path(String qrToken, String sub) {
        String root = "/g/" + URLEncoder.encode(qrToken, StandardCharsets.UTF_8).replace("+", "%20");
        return sub.isEmpty() ? root : root + "/" + sub;
    }

    /** 同上，接成完整網址。 */
    public static String url(String qrToken, String sub) {
        return WebApp.url(path(qrToken, sub));
    }
}
