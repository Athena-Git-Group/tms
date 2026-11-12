package com.athena.tms.bdd.support;

import com.athena.tms.bdd.ScenarioWorld;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class BddApiClient {

    private static final String BASE_PATH = "/api/v1";

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    public static final String GUEST_SESSION_HEADER = "X-Guest-Session";

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final ScenarioWorld world;
    private final RestClient restClient;

    public BddApiClient(Environment environment, ObjectMapper objectMapper, ScenarioWorld world) {
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.world = world;
        this.restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
    }

    public sealed interface Auth {
        record Staff(String jwt) implements Auth {
        }

        record Guest(String sessionToken) implements Auth {
        }

        record None() implements Auth {
        }

        static Auth staff(String jwt) {
            return new Staff(jwt);
        }

        static Auth guest(String sessionToken) {
            return new Guest(sessionToken);
        }

        static Auth none() {
            return new None();
        }
    }

    public ScenarioWorld.ApiResponse get(String path, Auth auth) {
        return exchange(HttpMethod.GET, path, auth, null, null);
    }

    public ScenarioWorld.ApiResponse post(String path, Auth auth, Object body) {
        return post(path, auth, body, UUID.randomUUID().toString());
    }

    public ScenarioWorld.ApiResponse post(String path, Auth auth, Object body, String idempotencyKey) {
        return exchange(HttpMethod.POST, path, auth, body, idempotencyKey);
    }

    public ScenarioWorld.ApiResponse patch(String path, Auth auth, Object body) {
        return patch(path, auth, body, UUID.randomUUID().toString());
    }

    public ScenarioWorld.ApiResponse patch(String path, Auth auth, Object body, String idempotencyKey) {
        return exchange(HttpMethod.PATCH, path, auth, body, idempotencyKey);
    }

    public ScenarioWorld.ApiResponse exchange(HttpMethod method, String path, Auth auth, Object body, String idempotencyKey) {
        RestClient.RequestBodySpec spec = restClient.method(method).uri(url(path));

        if (auth instanceof Auth.Staff staff) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + staff.jwt());
        } else if (auth instanceof Auth.Guest guest) {
            spec = spec.header(GUEST_SESSION_HEADER, guest.sessionToken());
        }

        if (idempotencyKey != null) {
            spec = spec.header(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
            world.setLastIdempotencyKey(idempotencyKey);
        }

        if (body != null) {
            String json = objectMapper.writeValueAsString(body);
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(json);
        }

        ScenarioWorld.ApiResponse result = spec.exchange((request, response) -> {
            int status = response.getStatusCode().value();
            String raw = readBody(response);
            JsonNode json = raw.isBlank() ? null : objectMapper.readTree(raw);
            return new ScenarioWorld.ApiResponse(status, raw, json);
        });

        world.setLastResponse(result);
        return result;
    }

    private String url(String path) {
        String port = environment.getRequiredProperty("local.server.port");
        return "http://localhost:" + port + BASE_PATH + path;
    }

    private static String readBody(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
