package com.athena.web.bdd.support;

import com.athena.web.bdd.support.ScenarioTasks.Entry;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Page;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class WhenStart {

    public static final String BACKEND_READER = "張主管";

    private static final String MARK_PAGE = """
            token => {
              window.__webBddWhenStart = token;
              window.__webBddPresses = 0;
              window.__webBddPressesPaused = false;
              if (!window.__webBddCountingPresses) {
                window.__webBddCountingPresses = true;
                for (const type of ['pointerdown', 'touchstart', 'click', 'keydown', 'input']) {
                  document.addEventListener(type, () => {
                    if (!window.__webBddPressesPaused) {
                      window.__webBddPresses++;
                    }
                  }, true);
                }
              }
            }
            """;

    private static final String PAUSE_COUNTING = "paused => { window.__webBddPressesPaused = paused; }";

    private final WebWorld world;

    private final List<String> nativeDialogs = new ArrayList<>();

    private Snapshot snapshot;

    private RuntimeException failure;

    private final List<Runnable> alsoAtCapture = new ArrayList<>();

    public WhenStart(WebWorld world) {
        this.world = world;
    }

    public void alsoCapture(Runnable action) {
        alsoAtCapture.add(action);
    }

    public record Snapshot(int requestCount, Page page, String pageToken, Map<String, String> tableDigests,
            Map<UUID, Integer> lastSeqByTask, Map<UUID, JsonNode> timelineByTask) {
    }

    @Before(order = 10)
    public void listen() {
        StepPhaseListener.requireInstalled();
        StepPhaseListener.onLeavingGiven(this::capture);
        StepPhaseListener.onThenPhase(this::pauseCounting);
    }

    @After(order = 10)
    public void stopListening() {
        StepPhaseListener.clear();
    }

    public Snapshot snapshot() {
        if (failure != null) {
            throw new AssertionError("前置走完時沒記下當下的樣子，無法和 When 之前比較：" + failure, failure);
        }
        if (snapshot == null) {
            throw new AssertionError("這個場景沒有記到 When 之前的樣子：前置之後沒有任何「當」或「那麼」步驟");
        }
        return snapshot;
    }

    public List<String> nativeDialogs() {
        return List.copyOf(nativeDialogs);
    }

    public List<Network.SentRequest> requestsSince() {
        return world.network().sentRequestsSince(snapshot().requestCount());
    }

    public Map<String, String> tableDigestsNow() {
        long tenantId = world.defaults().tenantId();
        Map<String, String> digests = new LinkedHashMap<>();
        for (String table : List.of("tasks", "task_events", "staff_accounts")) {
            Object digest = world.backend().queryRows(
                    "SELECT md5(coalesce(string_agg(x::text, '|' ORDER BY x.id), '')) AS digest FROM " + table
                            + " x WHERE x.tenant_id = ?", tenantId).get(0).get("digest");
            digests.put(table, String.valueOf(digest));
        }
        return digests;
    }

    public JsonNode timelineNow(UUID taskId) {
        return world.backend().getJson("/desk/tasks/" + taskId, world.staff(BACKEND_READER)).path("timeline");
    }

    public Optional<JsonNode> timelineBefore(UUID taskId) {
        return Optional.ofNullable(snapshot().timelineByTask().get(taskId));
    }

    public int lastSeqBefore(UUID taskId) {
        return snapshot().lastSeqByTask().getOrDefault(taskId, 0);
    }

    public boolean pageReloadedSince() {
        Snapshot before = snapshot();
        if (before.page() == null || before.pageToken() == null) {
            throw new AssertionError("前置走完時還沒有開著櫃檯 Web 的分頁，無法判斷之後有沒有重新整理");
        }
        boolean documentLoad = requestsSince().stream()
                .anyMatch(request -> request.page() == before.page() && request.documentLoad());
        Object token = before.page().evaluate("() => window.__webBddWhenStart ?? null");
        return documentLoad || !before.pageToken().equals(token);
    }

    public int pressesSince() {
        Snapshot before = snapshot();
        Object count = before.page().evaluate("() => window.__webBddPresses ?? 0");
        return ((Number) count).intValue();
    }

    private void capture() {
        try {
            snapshot = takeSnapshot();
        } catch (RuntimeException e) {
            failure = e;
        }
        for (Runnable action : alsoAtCapture) {
            try {
                action.run();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void pauseCounting(boolean inThen) {
        if (snapshot == null || snapshot.page() == null || snapshot.pageToken() == null || snapshot.page().isClosed()) {
            return;
        }
        try {
            snapshot.page().evaluate(PAUSE_COUNTING, inThen);
        } catch (RuntimeException navigating) {
        }
    }

    private Snapshot takeSnapshot() {
        int requestCount = world.network().sentRequestCount();
        Page page = world.hasPage() ? world.page() : null;
        String token = null;
        if (page != null && page.url().startsWith(WebApp.baseUrl() + "/")) {
            token = UUID.randomUUID().toString();
            page.evaluate(MARK_PAGE, token);
            page.onDialog(dialog -> {
                nativeDialogs.add(dialog.type() + "：" + dialog.message());
                dialog.dismiss();
            });
        }

        Map<UUID, Integer> lastSeq = new LinkedHashMap<>();
        for (Map<String, Object> row : world.backend().queryRows(
                "SELECT task_id, max(seq) AS last_seq FROM task_events WHERE tenant_id = ? GROUP BY task_id",
                world.defaults().tenantId())) {
            lastSeq.put((UUID) row.get("task_id"), ((Number) row.get("last_seq")).intValue());
        }

        Map<UUID, JsonNode> timelines = new LinkedHashMap<>();
        for (Entry entry : world.tasks().all()) {
            Object state = world.backend().taskRow(entry.taskId()).get("state");
            if (!"triage".equals(state) && !"pool".equals(state)) {
                continue;
            }
            try {
                timelines.put(entry.taskId(), timelineNow(entry.taskId()));
            } catch (RuntimeException unreadable) {
            }
        }
        return new Snapshot(requestCount, page, token, tableDigestsNow(), lastSeq, timelines);
    }
}
