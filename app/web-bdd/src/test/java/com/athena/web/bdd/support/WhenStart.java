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

/**
 * 這個場景的前置走完、第一個「當」或「那麼」步驟開始那一刻的樣子，給要和「When 之前」比的結果句用。
 *
 * <p>記下的東西有五樣，各自對應一種結果句：
 * <ul>
 *   <li>到那一刻為止分頁送出過幾個請求：之後送出的就是這個案例的動作造成的，用來看有沒有送出寫入、有沒有整頁重新載入。</li>
 *   <li>後端三張表 {@code tasks}、{@code task_events}、{@code staff_accounts} 的內容摘要：比對「後端沒有任何資料被改動」。</li>
 *   <li>每張單那時最後一筆事件的序號：之後多出來的事件就是這個案例的動作留下的。</li>
 *   <li>還在待派單或待認領的單，那時給人看的歷程：比對「歷程沒有多任何一筆」。</li>
 *   <li>目前的分頁上留一個記號，並開始數使用者按了幾下：分頁整頁重新載入時記號就不見了。</li>
 * </ul>
 * 另外從那一刻起聽目前分頁的瀏覽器內建提示框（{@code confirm}、{@code alert}），記下來後關掉，
 * 給「畫面上沒有任何對話框，也沒有確認提示」用。
 *
 * <p>記錄時出錯不會讓場景在那一步失敗，因為大部分場景根本用不到這份紀錄；
 * 錯誤留著，等用到的結果句再一起報出來。
 */
public final class WhenStart {

    /** 讀後端給人看的歷程與部門待認領用的帳號：主管兩種都看得到，而且不屬於任何一個部門。 */
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

    /** 結果句開始時停止計數、回到「當」時恢復；結果句為了看畫面自己做的點按不算使用者按的。 */
    private static final String PAUSE_COUNTING = "paused => { window.__webBddPressesPaused = paused; }";

    private final WebWorld world;

    private final List<String> nativeDialogs = new ArrayList<>();

    private Snapshot snapshot;

    private RuntimeException failure;

    private final List<Runnable> alsoAtCapture = new ArrayList<>();

    public WhenStart(WebWorld world) {
        this.world = world;
    }

    /**
     * 別的類別要在同一刻另外記下自己要的東西（例如詳情面板當下的歷程）時，在前置走完之前登記在這裡，
     * 通常是在自己的 {@code @Before} 裡登記。登記的事出錯不影響這裡的紀錄，由登記的一方自己留著錯誤。
     */
    public void alsoCapture(Runnable action) {
        alsoAtCapture.add(action);
    }

    /**
     * 那一刻的紀錄。
     *
     * @param requestCount 到那一刻為止這個場景所有分頁送出過幾個請求
     * @param page 那一刻目前的分頁；還沒開過分頁時是 {@code null}
     * @param pageToken 留在那個分頁上的記號；分頁不在櫃檯 Web 底下時是 {@code null}
     * @param tableDigests 三張表各自的內容摘要
     * @param lastSeqByTask 每張單那時最後一筆事件的序號，沒有事件的單不在裡面
     * @param timelineByTask 還在待派單或待認領的單那時給人看的歷程；讀不到的單不在裡面
     */
    public record Snapshot(int requestCount, Page page, String pageToken, Map<String, String> tableDigests,
            Map<UUID, Integer> lastSeqByTask, Map<UUID, JsonNode> timelineByTask) {
    }

    @Before(order = 10)
    public void listen() {
        StepPhaseListener.onLeavingGiven(this::capture);
        StepPhaseListener.onThenPhase(this::pauseCounting);
    }

    @After(order = 10)
    public void stopListening() {
        StepPhaseListener.clear();
    }

    /** 那一刻的紀錄；沒有記到或記錄時出錯就直接失敗，訊息說明原因。 */
    public Snapshot snapshot() {
        if (failure != null) {
            throw new AssertionError("前置走完時沒記下當下的樣子，無法和 When 之前比較：" + failure, failure);
        }
        if (snapshot == null) {
            throw new AssertionError("這個場景沒有記到 When 之前的樣子：前置之後沒有任何「當」或「那麼」步驟，"
                    + "或是跑的時候沒有掛上 " + StepPhaseListener.class.getName() + " 這個外掛");
        }
        return snapshot;
    }

    /** 從那一刻起目前分頁跳出過的瀏覽器內建提示框，每一筆是「種類：內文」。 */
    public List<String> nativeDialogs() {
        return List.copyOf(nativeDialogs);
    }

    /** 從那一刻起，這個場景的分頁送出過的請求。 */
    public List<Network.SentRequest> requestsSince() {
        return world.network().sentRequestsSince(snapshot().requestCount());
    }

    /** 後端三張表現在的內容摘要，和 {@link Snapshot#tableDigests()} 同一種算法。 */
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

    /** 這張單現在給人看的歷程（單筆詳情回應的 {@code timeline}），以主管的身分讀。 */
    public JsonNode timelineNow(UUID taskId) {
        return world.backend().getJson("/desk/tasks/" + taskId, world.staff(BACKEND_READER)).path("timeline");
    }

    /** 那一刻這張單給人看的歷程；那時不在待派單或待認領、或讀不到時是空的。 */
    public Optional<JsonNode> timelineBefore(UUID taskId) {
        return Optional.ofNullable(snapshot().timelineByTask().get(taskId));
    }

    /** 那一刻這張單最後一筆事件的序號，沒有事件時是 0。 */
    public int lastSeqBefore(UUID taskId) {
        return snapshot().lastSeqByTask().getOrDefault(taskId, 0);
    }

    /** 那個分頁從那一刻起有沒有整頁重新載入過：記號還在就是沒有。 */
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

    /**
     * 那個分頁從那一刻起被按過幾下：滑鼠按下、觸控、點擊、按鍵、輸入都算。
     * 只算「當」步驟期間的；「那麼」步驟為了看畫面自己做的點按（例如打開側欄看徽章、看帳號卡）不算。
     */
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
                // 登記的一方自己記錯誤；這裡吞掉是為了不讓它擋住其他登記的事。
            }
        }
    }

    /** 進入「那麼」時停止計數，回到「當」時恢復。分頁已經關掉或正在換頁時不管：換過頁的話記號也不在了，結果句會以重新整理報出來。 */
    private void pauseCounting(boolean inThen) {
        if (snapshot == null || snapshot.page() == null || snapshot.pageToken() == null || snapshot.page().isClosed()) {
            return;
        }
        try {
            snapshot.page().evaluate(PAUSE_COUNTING, inThen);
        } catch (RuntimeException navigating) {
            // 理由同上。
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
                // 單筆詳情讀不到時不記；用到這張單的結果句會說 When 之前沒有讀到它的歷程。
            }
        }
        return new Snapshot(requestCount, page, token, tableDigestsNow(), lastSeq, timelines);
    }
}
