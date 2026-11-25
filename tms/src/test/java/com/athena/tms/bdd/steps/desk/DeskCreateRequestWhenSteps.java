package com.athena.tms.bdd.steps.desk;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DeskApi;
import com.athena.tms.bdd.support.DeskContext;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.TenantFixtures;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.當;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

public class DeskCreateRequestWhenSteps {

    private static final String REPEATED_CHARACTER = "字";

    private final DeskApi deskApi;
    private final DeskContext deskContext;
    private final LastOperationGuard lastOperationGuard;
    private final ScenarioWorld world;
    private final TenantFixtures tenantFixtures;
    private final Environment environment;
    private final ObjectMapper objectMapper;

    public DeskCreateRequestWhenSteps(
            DeskApi deskApi,
            DeskContext deskContext,
            LastOperationGuard lastOperationGuard,
            ScenarioWorld world,
            TenantFixtures tenantFixtures,
            Environment environment,
            ObjectMapper objectMapper) {
        this.deskApi = deskApi;
        this.deskContext = deskContext;
        this.lastOperationGuard = lastOperationGuard;
        this.world = world;
        this.tenantFixtures = tenantFixtures;
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    @當("{string} 代開 {string}，其他需求是 {string}，給 {string}")
    public void createOtherRequest(String staffName, String roomNo, String text, String deptChinese) {
        send(staffName, roomNo, List.of(), DeskCreateRequestRows.other(text, deptChinese), newKey());
    }

    @當("{string} 代開 {string}，其他需求是 {int} 個字，給 {string}")
    public void createOtherRequestOfLength(String staffName, String roomNo, int length, String deptChinese) {
        String text = REPEATED_CHARACTER.repeat(length);
        send(staffName, roomNo, List.of(), DeskCreateRequestRows.other(text, deptChinese), newKey());
    }

    @當("{string} 代開 {string}，沒有選任何品項也沒有其他需求")
    public void createNothing(String staffName, String roomNo) {
        send(staffName, roomNo, List.of(), null, newKey());
    }

    @當("{string} 不帶防重複鍵代開 {string} 的以下單：")
    public void createWithoutIdempotencyKey(String staffName, String roomNo, DataTable table) {
        DeskCreateRequestRows.Request request = DeskCreateRequestRows.parse(table);
        send(staffName, roomNo, request.items(), request.other(), null);
    }

    @當("{string} 用同一把鍵、同樣的內容再送一次剛才那次代開")
    @SuppressWarnings("unchecked")
    public void resendSameRequest(String staffName) {
        Map<String, Object> body = deskContext.lastCreateRequestBody();
        String idempotencyKey = deskContext.lastCreateIdempotencyKey();
        if (idempotencyKey == null) {
            throw new IllegalStateException("剛才那次代開沒有帶防重複鍵，無法用同一把鍵重送");
        }
        send(staffName,
                (String) body.get("roomNo"),
                (List<Map<String, Object>>) body.get("items"),
                (Map<String, Object>) body.get("other"),
                idempotencyKey);
    }

    @當("{string} 用同一把鍵同時送出兩次代開 {string} 的以下單：")
    public void sendTwiceConcurrently(String staffName, String roomNo, DataTable table) throws Exception {
        DeskCreateRequestRows.Request request = DeskCreateRequestRows.parse(table);
        String idempotencyKey = newKey();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roomNo", roomNo);
        body.put("items", request.items());
        body.put("other", request.other());
        BddApiClient.Auth auth = BddApiClient.Auth.staff(world.jwt(staffName));
        lastOperationGuard.captureCreateRequest(tenantFixtures.testTenantId());
        deskContext.rememberCreateRequest(staffName, idempotencyKey, body);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<ScenarioWorld.ApiResponse> responses = new ArrayList<>();
        try {
            List<Future<ScenarioWorld.ApiResponse>> sent = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                BddApiClient client = new BddApiClient(environment, objectMapper, new ScenarioWorld());
                sent.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return client.post("/desk/requests", auth, body, idempotencyKey);
                }));
            }
            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("兩條送出代開的執行緒沒有在 10 秒內就緒");
            }
            go.countDown();
            for (Future<ScenarioWorld.ApiResponse> future : sent) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        for (ScenarioWorld.ApiResponse response : responses) {
            deskContext.rememberCreateResponse(response);
            world.setLastResponse(response);
            DeskCreateRequestRows.rememberCreated(world, roomNo, response);
        }
    }

    @當("{string} 讀取代開單的房間清單")
    public void listRooms(String staffName) {
        lastOperationGuard.captureCreateRequest(tenantFixtures.testTenantId());
        deskContext.rememberRoomsResponse(deskApi.listRooms(staffName));
    }

    private void send(
            String staffName, String roomNo, List<Map<String, Object>> items, Map<String, Object> other,
            String idempotencyKey) {
        lastOperationGuard.captureCreateRequest(tenantFixtures.testTenantId());
        ScenarioWorld.ApiResponse response = deskApi.createRequests(staffName, roomNo, items, other, idempotencyKey);
        DeskCreateRequestRows.rememberCreated(world, roomNo, response);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }
}
