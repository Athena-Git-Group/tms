package com.athena.tms.integration.stepdefs.utils;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

public class ApiClient {

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final RestTemplate rest = buildNoThrowRestTemplate();
    private final Map<String, String> tokenCache = new ConcurrentHashMap<>();

    public ApiClient(Environment environment, ObjectMapper objectMapper) {
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    public String staffToken(long tenantId, String loginId) {
        return tokenCache.computeIfAbsent(tenantId + ":" + loginId, key -> login(tenantId, loginId));
    }

    private String login(long tenantId, String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = objectMapper.writeValueAsString(
                Map.of("tenantId", tenantId, "loginId", loginId, "password", "demo1234"));
        ResponseEntity<String> response = rest.exchange(
                url("/auth/login"), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        if (response.getStatusCode().value() != 200) {
            throw new IllegalStateException("登入失敗（" + loginId + "）: "
                    + response.getStatusCode().value() + " " + response.getBody());
        }
        return objectMapper.readTree(response.getBody()).get("token").asText();
    }

    public ScenarioContext.HttpResult loginRaw(Long tenantId, String loginId, String password, String deviceFingerprint, String pushToken) {
        Map<String, Object> body = new HashMap<>();
        body.put("tenantId", tenantId);
        body.put("loginId", loginId);
        body.put("password", password);
        body.put("deviceFingerprint", deviceFingerprint);
        body.put("pushToken", pushToken);
        return postJson("/auth/login", null, body, null);
    }

    public ScenarioContext.HttpResult get(String path, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return toResult(rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class));
    }

    public ScenarioContext.HttpResult getWithHeaders(String path, Map<String, String> extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        extraHeaders.forEach(headers::set);
        return toResult(rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class));
    }

    public ScenarioContext.HttpResult postJson(String path, String bearerToken, Object body, Map<String, String> extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        if (extraHeaders != null) {
            extraHeaders.forEach(headers::set);
        }
        String json = body == null ? "{}" : objectMapper.writeValueAsString(body);
        return toResult(rest.exchange(url(path), HttpMethod.POST, new HttpEntity<>(json, headers), String.class));
    }

    public ScenarioContext.HttpResult post(String path, String bearerToken, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return toResult(rest.exchange(url(path), HttpMethod.POST, new HttpEntity<>(headers), String.class));
    }

    private static ScenarioContext.HttpResult toResult(ResponseEntity<String> response) {
        return new ScenarioContext.HttpResult(
                response.getStatusCode().value(),
                response.getBody(),
                response.getHeaders().getFirst("Idempotency-Replayed"));
    }

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
