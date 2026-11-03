/*
 * Decompiled with CFR 0.152.
 */
package com.athena.tms.integration.stepdefs.utils;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import java.util.Map;
import java.util.UUID;

public class GuestFixtures {
    private static final Map<String, String> MENU_KEYS_BY_LABEL = Map.of("\u52a0\u6bdb\u5dfe", "towel", "\u7926\u6cc9\u6c34", "water", "\u7259\u5237\u7d44", "toothbrush", "\u6e05\u6f54\u670d\u52d9", "cleaning");
    private final ApiClient api;

    public GuestFixtures(ApiClient api) {
        this.api = api;
    }

    public static String menuKeyOf(String label) {
        String key = MENU_KEYS_BY_LABEL.get(label);
        if (key == null) {
            throw new IllegalArgumentException("\u6e2c\u8a66\u8cc7\u6599\u88e1\u6c92\u6709\u300c" + label + "\u300d");
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
