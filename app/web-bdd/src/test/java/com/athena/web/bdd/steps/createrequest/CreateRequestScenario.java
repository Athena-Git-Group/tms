package com.athena.web.bdd.steps.createrequest;

import com.athena.web.bdd.pages.CreateRequestPanel;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.StepPhaseListener;
import com.athena.web.bdd.support.WebWorld;
import com.athena.web.bdd.support.WhenStart;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Response;
import io.cucumber.java.Before;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CreateRequestScenario {

    public record PanelSnapshot(String roomNo, Map<String, Integer> quantities, String otherText, String otherDept) {

        public PanelSnapshot {
            quantities = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(quantities));
        }

        @SuppressWarnings("unchecked")
        static PanelSnapshot of(Map<String, Object> content) {
            return new PanelSnapshot((String) content.get("roomNo"), (Map<String, Integer>) content.get("quantities"),
                    (String) content.get("otherText"), (String) content.get("otherDept"));
        }
    }

    public record BackendBefore(Set<UUID> taskIds, int repairTickets) {
    }

    public record RapidPresses(int requestsSent, List<Boolean> disabledAtEachPress, boolean busyWhileWaiting) {
    }

    private static final List<String> NEEDS_CREATE_RESPONSE = List.of(
            "{string} 欄多了以下這次開出的卡片，外框高亮 {double} 秒後恢復：", "這次開出的卡片如下：");

    private static final List<String> NEEDS_BACKEND_BEFORE = List.of(
            "後端這次開出的單如下：", "後端這次一張單都沒有開出");

    private final WebWorld world;

    private final WhenStart whenStart;

    private PanelSnapshot snapshot;
    private String idempotencyKey;
    private final List<UUID> createdTaskIds = new ArrayList<>();
    private BackendBefore backendBefore;
    private RuntimeException backendBeforeFailure;
    private RapidPresses rapidPresses;
    private String typedOtherText;
    private Response lastCreateResponse;

    public CreateRequestScenario(WebWorld world, WhenStart whenStart) {
        this.world = world;
        this.whenStart = whenStart;
    }

    @Before(order = 20)
    public void captureAtWhenStart() {
        if (NEEDS_CREATE_RESPONSE.stream().anyMatch(StepPhaseListener::currentScenarioUses)) {
            world.onEveryNewContext(context -> context.onResponse(response -> {
                if (response.ok() && "POST".equals(response.request().method())
                        && response.url().split("[?#]")[0].endsWith(Network.DESK_CREATE_REQUESTS.path())) {
                    lastCreateResponse = response;
                }
            }));
        }
        boolean needsBackend = NEEDS_BACKEND_BEFORE.stream().anyMatch(StepPhaseListener::currentScenarioUses);
        whenStart.alsoCapture(() -> {
            if (needsBackend) {
                try {
                    backendBefore = readBackend();
                } catch (RuntimeException e) {
                    backendBeforeFailure = e;
                }
            }
            if (snapshot == null) {
                CreateRequestPanel panel = new CreateRequestPanel(world);
                if (panel.isOpen()) {
                    snapshot = PanelSnapshot.of(panel.readContent());
                }
            }
        });
    }

    BackendBefore readBackend() {
        long tenantId = world.defaults().tenantId();
        Set<UUID> ids = new HashSet<>();
        for (Map<String, Object> row : world.backend().queryRows("SELECT id FROM tasks WHERE tenant_id = ?", tenantId)) {
            ids.add((UUID) row.get("id"));
        }
        int tickets = ((Number) world.backend().queryRows(
                "SELECT count(*) AS n FROM repair_tickets WHERE tenant_id = ?", tenantId).get(0).get("n")).intValue();
        return new BackendBefore(Set.copyOf(ids), tickets);
    }

    public BackendBefore backendBefore() {
        if (backendBeforeFailure != null) {
            throw new AssertionError("前置走完時沒記下後端有哪些單：" + backendBeforeFailure, backendBeforeFailure);
        }
        if (backendBefore == null) {
            throw new AssertionError("這個場景沒有記到 When 之前後端有哪些單");
        }
        return backendBefore;
    }

    public void rememberSnapshot(PanelSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public PanelSnapshot snapshot() {
        if (snapshot == null) {
            throw new IllegalStateException("本場景還沒記下送出前的面板內容");
        }
        return snapshot;
    }

    public void rememberIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String idempotencyKey() {
        if (idempotencyKey == null) {
            throw new IllegalStateException("本場景還沒記下代開單送出用的冪等鍵");
        }
        return idempotencyKey;
    }

    public void rememberCreatedTasks(List<UUID> taskIds) {
        createdTaskIds.clear();
        createdTaskIds.addAll(taskIds);
    }

    public List<UUID> createdTaskIds() {
        return List.copyOf(createdTaskIds);
    }

    public void rememberRapidPresses(RapidPresses presses) {
        this.rapidPresses = presses;
    }

    public RapidPresses rapidPresses() {
        if (rapidPresses == null) {
            throw new IllegalStateException("本場景沒有連按送出鈕");
        }
        return rapidPresses;
    }

    public JsonNode lastCreateResult() {
        if (lastCreateResponse == null) {
            throw new AssertionError("這個場景的畫面沒有收到任何成功的代開單回應");
        }
        try {
            return new ObjectMapper().readTree(lastCreateResponse.text());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AssertionError("代開單的回應不是 JSON：" + lastCreateResponse.text(), e);
        }
    }

    public void rememberTypedOtherText(String text) {
        this.typedOtherText = text;
    }

    public String typedOtherText() {
        if (typedOtherText == null) {
            throw new IllegalStateException("本場景沒有在其他需求逐字輸入過");
        }
        return typedOtherText;
    }
}
