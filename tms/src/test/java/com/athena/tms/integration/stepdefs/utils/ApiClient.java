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
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

public class ApiClient {
    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final RestTemplate rest = ApiClient.buildNoThrowRestTemplate();
    private final Map<String, String> tokenCache = new ConcurrentHashMap<String, String>();

    public ApiClient(Environment environment, ObjectMapper objectMapper) {
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    public String staffToken(long tenantId, String loginId) {
        return this.tokenCache.computeIfAbsent(tenantId + ":" + loginId, key -> this.login(tenantId, loginId));
    }

    private String login(long tenantId, String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = this.objectMapper.writeValueAsString(Map.of("tenantId", tenantId, "loginId", loginId, "password", "demo1234"));
        ResponseEntity response = this.rest.exchange(this.url("/auth/login"), HttpMethod.POST, new HttpEntity((Object)body, headers), String.class, new Object[0]);
        if (response.getStatusCode().value() != 200) {
            throw new IllegalStateException("\u767b\u5165\u5931\u6557\uff08" + loginId + "\uff09: " + response.getStatusCode().value() + " " + (String)response.getBody());
        }
        return this.objectMapper.readTree((String)response.getBody()).get("token").asText();
    }

    public ScenarioContext.HttpResult loginRaw(Long tenantId, String loginId, String password, String deviceFingerprint, String pushToken) {
        HashMap<String, Object> body = new HashMap<String, Object>();
        body.put("tenantId", tenantId);
        body.put("loginId", loginId);
        body.put("password", password);
        body.put("deviceFingerprint", deviceFingerprint);
        body.put("pushToken", pushToken);
        return this.postJson("/auth/login", null, body, null);
    }

    public ScenarioContext.HttpResult get(String path, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        ResponseEntity response = this.rest.exchange(this.url(path), HttpMethod.GET, new HttpEntity(headers), String.class, new Object[0]);
        return new ScenarioContext.HttpResult(response.getStatusCode().value(), (String)response.getBody(), response.getHeaders().getFirst("Idempotency-Replayed"));
    }

    public ScenarioContext.HttpResult getWithHeaders(String path, Map<String, String> extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        extraHeaders.forEach((arg_0, arg_1) -> ((HttpHeaders)headers).set(arg_0, arg_1));
        ResponseEntity response = this.rest.exchange(this.url(path), HttpMethod.GET, new HttpEntity(headers), String.class, new Object[0]);
        return new ScenarioContext.HttpResult(response.getStatusCode().value(), (String)response.getBody(), response.getHeaders().getFirst("Idempotency-Replayed"));
    }

    public ScenarioContext.HttpResult postJson(String path, String bearerToken, Object body, Map<String, String> extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        if (extraHeaders != null) {
            extraHeaders.forEach((arg_0, arg_1) -> ((HttpHeaders)headers).set(arg_0, arg_1));
        }
        String json = body == null ? "{}" : this.objectMapper.writeValueAsString(body);
        ResponseEntity response = this.rest.exchange(this.url(path), HttpMethod.POST, new HttpEntity((Object)json, headers), String.class, new Object[0]);
        return new ScenarioContext.HttpResult(response.getStatusCode().value(), (String)response.getBody(), response.getHeaders().getFirst("Idempotency-Replayed"));
    }

    public ScenarioContext.HttpResult post(String path, String bearerToken, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        ResponseEntity response = this.rest.exchange(this.url(path), HttpMethod.POST, new HttpEntity(headers), String.class, new Object[0]);
        return new ScenarioContext.HttpResult(response.getStatusCode().value(), (String)response.getBody(), response.getHeaders().getFirst("Idempotency-Replayed"));
    }

    private String url(String path) {
        return "http://localhost:" + this.environment.getRequiredProperty("local.server.port") + path;
    }

    private static RestTemplate buildNoThrowRestTemplate() {
        RestTemplate template = new RestTemplate();
        template.setRequestFactory((ClientHttpRequestFactory)new JdkClientHttpRequestFactory());
        template.setErrorHandler(response -> false);
        return template;
    }
}
