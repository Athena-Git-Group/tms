package com.athena.web.bdd.spike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.athena.bddsupport.BackendChannel;
import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.bddsupport.GuestSeed;
import com.athena.web.bdd.WebProfile;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebApp;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 代開單要驗收「後端已經開好單、畫面卻沒收到回應」之後的重送。這支測試先確認 Playwright 做得出這個情況：
 * 請求照常送到真的後端並提交，畫面上的 fetch 卻以網路錯誤收場，同一把冪等鍵重送時後端回放。
 * 電腦設定（Chromium）與 iPad 設定（WebKit）各跑一次，因為兩種瀏覽器回報網路錯誤的方式不同。
 *
 * <p>會清空 5433 資料庫的業務資料，要 8080 後端與開發伺服器都開著；和其他 spike 一樣被 pom 排除在平常的 mvn test 之外。
 * 跑法：{@code mvn -q test -Dtest=RouteFetchAbortSpikeTest -Dweb.baseUrl=http://localhost:5199}。
 */
class RouteFetchAbortSpikeTest {

    private static final String ROOM = "311";
    private static final String ASSIGN_ROUTE = "**" + Network.DESK_ASSIGN.path();
    private static final String FETCH_SCRIPT = """
            async ([url, jwt, key]) => {
              try {
                const response = await fetch(url, {
                  method: 'POST',
                  headers: { 'Authorization': 'Bearer ' + jwt, 'Idempotency-Key': key, 'Content-Type': 'application/json' },
                  body: JSON.stringify({ dept: 'housekeeping' })
                });
                return 'status:' + response.status;
              } catch (error) {
                return 'error:' + error.name + ':' + error.message;
              }
            }
            """;

    private final BackendChannel channel = new BackendChannel();
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final String backend = System.getProperty("backend.base.url", "http://127.0.0.1:8080");

    @Test
    void 電腦設定_送到後端之後中斷() throws Exception {
        runFor(WebProfile.DESKTOP);
    }

    @Test
    void iPad設定_送到後端之後中斷() throws Exception {
        runFor(WebProfile.IPAD);
    }

    private void runFor(WebProfile profile) throws Exception {
        channel.resetBusinessData();
        CreatedStaff clerk = channel.ensureFrontdeskDefaults().staff("林佩君");
        channel.ensureGuestDefaults();
        String jwt = channel.jwt(clerk);

        try (Playwright playwright = Playwright.create();
                Browser browser = profile.launch(playwright);
                BrowserContext context = profile.newContext(browser)) {
            Page page = context.newPage();
            page.navigate(WebApp.url("/"));

            for (String abortCode : new String[] {"failed", null}) {
                UUID taskId = triageTask();
                String key = UUID.randomUUID().toString();
                AtomicInteger backendStatus = new AtomicInteger();
                page.route(ASSIGN_ROUTE, route -> {
                    APIResponse real = route.fetch();
                    backendStatus.set(real.status());
                    if (abortCode == null) {
                        route.abort();
                    } else {
                        route.abort(abortCode);
                    }
                });

                String url = "/api/v1/desk/tasks/" + taskId + "/assign";
                String result = (String) page.evaluate(FETCH_SCRIPT, List.of(url, jwt, key));
                page.unroute(ASSIGN_ROUTE);

                System.out.printf("[spike T003] %s abort(%s)：後端 %d，畫面 %s%n",
                        profile.id(), abortCode == null ? "" : "\"" + abortCode + "\"", backendStatus.get(), result);
                assertEquals(200, backendStatus.get(), "請求確實送到後端並成功");
                assertTrue(result.startsWith("error:TypeError"), "畫面上的 fetch 以網路錯誤收場：" + result);
                assertEquals("pool", channel.taskRow(taskId).get("state"), "後端已經提交，單已派出");

                HttpResponse<String> replay = resend(url, jwt, key);
                System.out.printf("[spike T003] %s 同一把鍵重送：%d Idempotency-Replayed=%s%n",
                        profile.id(), replay.statusCode(), replay.headers().firstValue("Idempotency-Replayed").orElse("-"));
                assertEquals(200, replay.statusCode());
                assertEquals("true", replay.headers().firstValue("Idempotency-Replayed").orElse(null));
            }
        }
    }

    private UUID triageTask() {
        return channel.seedGuestRequest(ROOM, new GuestSeed(GuestSeed.Situation.TRIAGE,
                "spike 要多一條毛巾", "amenity", channel.backendNow().minusMinutes(1), null, null, null, null));
    }

    private HttpResponse<String> resend(String path, String jwt, String key) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(backend + path))
                .header("Authorization", "Bearer " + jwt)
                .header("Idempotency-Key", key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"dept\":\"housekeeping\"}"))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
