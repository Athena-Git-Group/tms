package com.athena.tms.integration.stepdefs.utils;

import com.athena.tms.integration.stepdefs.ScenarioContext.HttpResult;

import java.util.Map;
import java.util.UUID;

/**
 * 住客視角-整合測試的共用工具：呼叫住客 API 的捷徑。
 *
 * <p>測試資料（房間、訂單、QR Code、住客工作階段）由各場景的 「系統中存在以下〈實體〉」表格步驟建立，本類別只負責發請求。
 */
public class GuestFixtures {

    /** 測試資料 request_menu 的 label → key（feature 內文用 label，API 收 key）。 */
    private static final Map<String, String> MENU_KEYS_BY_LABEL = Map.of(
            "加毛巾", "towel", "礦泉水", "water", "牙刷組", "toothbrush", "清潔服務", "cleaning");

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

    public HttpResult createRequest(String sessionToken, Map<String, Object> body) {
        return api.postJson("/guest/requests", sessionToken, body, null);
    }

    public HttpResult createRating(String sessionToken, UUID taskId, int rating) {
        return api.postJson("/guest/requests/" + taskId + "/rating", sessionToken,
                Map.of("rating", rating), null);
    }

    public HttpResult cancelRequest(String sessionToken, UUID taskId) {
        return api.postJson("/guest/requests/" + taskId + "/cancel", sessionToken, Map.of(), null);
    }

    public HttpResult scan(String qrToken, String ip) {
        return api.postJson("/guest/scan", null, Map.of("token", qrToken),
                ip == null ? null : Map.of("X-Forwarded-For", ip));
    }
}
