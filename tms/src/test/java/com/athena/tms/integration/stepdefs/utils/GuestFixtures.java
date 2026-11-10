package com.athena.tms.integration.stepdefs.utils;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import java.util.Map;
import java.util.UUID;

public class GuestFixtures {
    private static final Map<String, String> MENU_KEYS_BY_LABEL = Map.of("加毛巾", "towel", "礦泉水", "water", "牙刷組", "toothbrush", "清潔服務", "cleaning");
    private final ApiClient api;

    public GuestFixtures(ApiClient api) {
        this.api = api;
    }

    public static String menuKeyOf(String label) {
        String key = MENU_KEYS_BY_LABEL.get(label);
        if (key == null) {
            throw new IllegalArgumentException("測試資料裡沒有「" + label + "」");
        }
        return key;
    }

    public ScenarioContext.HttpResult createRequest(String sessionToken, Map<String, Object> body) {
        return this.api.postJson("/guest/requests", sessionToken, body, null);
    }

    public ScenarioContext.HttpResult createRating(String sessionToken, UUID taskId, int rating) {
        return this.api.postJson("/guest/requests/" + String.valueOf(taskId) + "/rating", sessionToken, Map.of("rating", rating), null);
    }

    public ScenarioContext.HttpResult cancelRequest(String sessionToken, UUID taskId) {
        return this.api.postJson("/guest/requests/" + String.valueOf(taskId) + "/cancel", sessionToken, Map.of(), null);
    }

    public ScenarioContext.HttpResult scan(String qrToken, String ip) {
        return this.api.postJson("/guest/scan", null, Map.of("token", qrToken), ip == null ? null : Map.of("X-Forwarded-For", ip));
    }
}
