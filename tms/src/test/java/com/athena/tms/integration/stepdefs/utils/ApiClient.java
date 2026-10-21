package com.athena.tms.integration.stepdefs.utils;

import com.athena.tms.filter.IdempotencyFilter;
import com.athena.tms.integration.stepdefs.ScenarioContext.HttpResult;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 真 HTTP 客戶端（走隨機埠、過完整 filter chain——security chain＋冪等 filter
 * 都是 servlet 層，MockMvc 驗不到 request attribute）。
 * RestTemplate 掛 no-throw error handler＋JDK client（AuthSecurityIT 的同款
 * 理由：HttpURLConnection 在 POST＋401 會吃掉 error body）。
 *
 * <p>JWT TTL 12 小時、context 整個 Suite 共用——token 快取在 (tenant, loginId) 鍵上。
 */
public class ApiClient {

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final RestTemplate rest = buildNoThrowRestTemplate();
    private final Map<String, String> tokenCache = new ConcurrentHashMap<>();

    public ApiClient(Environment environment, ObjectMapper objectMapper) {
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    /** 登入並快取 JWT。 */
    public String staffToken(long tenantId, String loginId) {
        return tokenCache.computeIfAbsent(tenantId + ":" + loginId,
                key -> login(tenantId, loginId));
    }

    private String login(long tenantId, String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = objectMapper.writeValueAsString(Map.of(
                "tenantId", tenantId, "loginId", loginId, "password", TestData.PASSWORD));
        ResponseEntity<String> response = rest.exchange(url("/auth/login"),
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        if (response.getStatusCode().value() != 200) {
            throw new IllegalStateException("登入失敗（" + loginId + "）: "
                    + response.getStatusCode().value() + " " + response.getBody());
        }
        return objectMapper.readTree(response.getBody()).get("token").asText();
    }

    /**
     * 原始登入：錯誤分流（401/422）與裝置登記場景用。
     * body 允許 null 欄位（HashMap），與 LoginRequest 的選填欄位對齊。
     */
    public HttpResult loginRaw(Long tenantId, String loginId, String password,
                               String deviceFingerprint, String pushToken) {
        Map<String, Object> body = new HashMap<>();
        body.put("tenantId", tenantId);
        body.put("loginId", loginId);
        body.put("password", password);
        body.put("deviceFingerprint", deviceFingerprint);
        body.put("pushToken", pushToken);
        return postJson("/auth/login", null, body, null);
    }

    public HttpResult get(String path, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        ResponseEntity<String> response = rest.exchange(url(path),
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return new HttpResult(response.getStatusCode().value(), response.getBody(),
                response.getHeaders().getFirst(IdempotencyFilter.REPLAYED_HEADER));
    }

    /**
     * 自組 header 的 GET：X-API-Key 這類非 bearer 認證走這裡——
     * {@link #get(String, String)} 的第二參數語意固定是 Authorization bearer，
     * 塞別種憑證會變成「同時帶兩種身分」的誤用。
     */
    public HttpResult getWithHeaders(String path, Map<String, String> extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        extraHeaders.forEach(headers::set);
        ResponseEntity<String> response = rest.exchange(url(path),
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return new HttpResult(response.getStatusCode().value(), response.getBody(),
                response.getHeaders().getFirst(IdempotencyFilter.REPLAYED_HEADER));
    }

    /** 帶 JSON body 的 POST（guest／login 端點）；extraHeaders 可為 null（X-Forwarded-For 類）。 */
    public HttpResult postJson(String path, String bearerToken, Object body,
                               Map<String, String> extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        if (extraHeaders != null) {
            extraHeaders.forEach(headers::set);
        }
        String json = body == null ? "{}" : objectMapper.writeValueAsString(body);
        ResponseEntity<String> response = rest.exchange(url(path),
                HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
        return new HttpResult(response.getStatusCode().value(), response.getBody(),
                response.getHeaders().getFirst(IdempotencyFilter.REPLAYED_HEADER));
    }

    /** 無 body 的 POST（claim/start/complete 類動作端點）。 */
    public HttpResult post(String path, String bearerToken, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        if (idempotencyKey != null) {
            headers.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, idempotencyKey);
        }
        ResponseEntity<String> response = rest.exchange(url(path),
                HttpMethod.POST, new HttpEntity<>(headers), String.class);
        return new HttpResult(response.getStatusCode().value(), response.getBody(),
                response.getHeaders().getFirst(IdempotencyFilter.REPLAYED_HEADER));
    }

    /** 動態讀port：local.server.port 在 web server 起來後才進 Environment。 */
    private String url(String path) {
        return "http://localhost:" + environment.getRequiredProperty("local.server.port") + path;
    }

    private static RestTemplate buildNoThrowRestTemplate() {
        RestTemplate template = new RestTemplate();
        template.setRequestFactory(new JdkClientHttpRequestFactory());
        template.setErrorHandler(response -> false);
        return template;
    }
}
