package com.athena.web.bdd.support;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class GuestApp {

    public static final String HOME = "";

    public static final String SUPPLY = "supply";

    public static final String REPAIR = "repair";

    public static final String SENT = "sent";

    public static final String REQUESTS = "requests";

    private GuestApp() {
    }

    public static String request(Object requestId) {
        return REQUESTS + "/" + requestId;
    }

    public static String guide(String key) {
        return "guide/" + URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static String path(String qrToken, String sub) {
        String root = "/g/" + URLEncoder.encode(qrToken, StandardCharsets.UTF_8).replace("+", "%20");
        return sub.isEmpty() ? root : root + "/" + sub;
    }

    public static String url(String qrToken, String sub) {
        return WebApp.url(path(qrToken, sub));
    }
}
