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

/**
 * BDD 情境發 API 請求用的共用元件：把「組 URL、帶認證 header、序列化 body、
 * 讀回應」這套機械動作集中在一處，讓 stepdef 只需要描述業務語意。
 *
 * <p>伺服器以 RANDOM_PORT 啟動，實際埠號要到 Spring context 準備好之後才能從
 * {@link Environment} 讀到，所以 base URL 每次呼叫才組，不在建構子先算好。
 *
 * <p>任何狀態碼（含 4xx/5xx）都視為正常回應、不拋例外：BDD 情境經常要驗證失敗路徑
 * （例如 409 狀態衝突、401 未授權），若遇到非 2xx 就拋例外，這些情境就沒辦法跟
 * 成功路徑共用同一套「發請求、讀狀態碼與 body」的程式碼，得另外寫例外攔截邏輯。
 * 每次呼叫都會把結果存進 {@link ScenarioWorld#setLastResponse}，寫入類呼叫
 * （POST/PATCH）另外會把實際送出的 Idempotency-Key 存進
 * {@link ScenarioWorld#setLastIdempotencyKey}，供後續步驟重送同一個鍵或斷言。
 *
 * <p>本類別只做請求/回應的機械轉換，不含任何 operation 的業務斷言。
 */
public class BddApiClient {

    private static final String BASE_PATH = "/api/v1";

    /** 冪等鍵 header 名稱，定義見 specs/truth/contracts/components/parameters.yaml 的 IdempotencyKey。 */
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    /** 住客 session header 名稱，定義見 parameters.yaml 的 GuestSession。 */
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

    /**
     * 員工與住客兩種身分用不同 header 認證（Authorization: Bearer 對 X-Guest-Session），
     * 用 sealed interface 固定成這三種型態，讓 exchange() 能用 instanceof 窮舉處理，
     * 不開放呼叫端自己組 header 字串，避免哪天 header 名稱改了，散落各處的呼叫都要跟著改。
     */
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

    /** 帶 body 的 POST，自動產生一個新的 UUID 當 Idempotency-Key。 */
    public ScenarioWorld.ApiResponse post(String path, Auth auth, Object body) {
        return post(path, auth, body, UUID.randomUUID().toString());
    }

    public ScenarioWorld.ApiResponse post(String path, Auth auth, Object body, String idempotencyKey) {
        return exchange(HttpMethod.POST, path, auth, body, idempotencyKey);
    }

    /** 帶 body 的 PATCH，自動產生一個新的 UUID 當 Idempotency-Key。 */
    public ScenarioWorld.ApiResponse patch(String path, Auth auth, Object body) {
        return patch(path, auth, body, UUID.randomUUID().toString());
    }

    public ScenarioWorld.ApiResponse patch(String path, Auth auth, Object body, String idempotencyKey) {
        return exchange(HttpMethod.PATCH, path, auth, body, idempotencyKey);
    }

    /**
     * 通用版本，供 get/post/patch 無法涵蓋的情境使用（例如 DELETE、或刻意把
     * idempotencyKey 傳 null 來驗證缺少這個 header 時 API 的行為）。
     */
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
