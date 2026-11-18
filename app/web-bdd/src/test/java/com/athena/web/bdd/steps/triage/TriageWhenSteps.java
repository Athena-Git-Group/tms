package com.athena.web.bdd.steps.triage;

import com.athena.bddsupport.BackendChannel;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.ScenarioTasks;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import io.cucumber.java.zh_tw.當;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** 「待派單」模組的 When 句型。 */
public class TriageWhenSteps {

    /** 勾選的那一張落在哪間房；背景表格沒有用到這間房，不會和案例的單混在一起。 */
    private static final String MENU_ITEM_ROOM = "601";

    /** 打字的那一張落在哪間房，理由同上。 */
    private static final String RULE_HIT_ROOM = "602";

    private static final String MENU_ITEM_KEY = "fx_bath_towel";

    private static final String MENU_ITEM_LABEL = "浴巾";

    /** 規則的關鍵字；案例裡其他住客原話都不含這兩個字，不會被這條規則接走。 */
    private static final String RULE_KEYWORD = "冷氣";

    private static final String RULE_HIT_TEXT = "冷氣一直滴水";

    /** 送出之後最久等這麼久讓畫面收到即時訊號並重抓到這兩張單。 */
    private static final long REFETCH_TIMEOUT_MS = 15_000;

    private final WebWorld world;

    public TriageWhenSteps(WebWorld world) {
        this.world = world;
    }

    /**
     * 只動時間、不碰畫面：從瀏覽器此刻的時間再往後推該秒數，後端跟著撥到同一個時刻。
     *
     * <p>瀏覽器時鐘照真實速度走，派單浮條出現時瀏覽器已經比場景的「現在」快了好幾秒；
     * 從場景的「現在」往後推會推不滿，所以以瀏覽器此刻的時間為起點。浮條出現之後才讀這個時間，
     * 推完時離浮條出現一定至少過了該秒數。
     */
    @當("派單浮條出現後過了 {int} 秒")
    public void secondsAfterAssignToast(int seconds) {
        long browserNow = ((Number) world.page().evaluate("Date.now()")).longValue();
        OffsetDateTime from = OffsetDateTime.ofInstant(Instant.ofEpochMilli(browserNow), ZoneId.systemDefault());
        world.clocks().moveTo(from.plusSeconds(seconds));
    }

    /**
     * 以住客身分在後端送出兩張需求：一張從需求選單勾選，一張打字且命中分類規則，兩張都直接進部門的待認領。
     * 送完等畫面收到即時訊號、重抓到含這兩張單的看板資料並畫完。
     *
     * <p>需求選單與分類規則是這個場景自己寫進飯店設定的；每個場景開始時業務資料會清空，不會留給別的場景。
     */
    @當("一張住客勾選的需求與一張命中規則的打字需求進來")
    public void menuItemAndRuleHitArrive() {
        BackendChannel backend = world.backend();
        long tenantId = backend.ensureTenant();
        backend.setTenantConfig(tenantId, "request_menu", List.of(menuItem()));
        backend.setTenantConfig(tenantId, "classification_rules", List.of(classificationRule()));

        Page page = world.page();
        List<Response> boardReads = new ArrayList<>();
        Consumer<Response> seen = response -> {
            if (isBoardResponse(response)) {
                boardReads.add(response);
            }
        };
        page.onResponse(seen);
        try {
            UUID menuTask = backend.guestSubmitMenuItem(MENU_ITEM_ROOM, MENU_ITEM_KEY, 1);
            UUID ruleTask = backend.guestSubmitRequest(RULE_HIT_ROOM, RULE_HIT_TEXT, null);
            requireInPool(menuTask, "勾選的「" + MENU_ITEM_LABEL + "」");
            requireInPool(ruleTask, "命中規則的「" + RULE_HIT_TEXT + "」");
            world.tasks().add(new ScenarioTasks.Entry(MENU_ITEM_ROOM, MENU_ITEM_LABEL, menuTask, "待認領"));
            world.tasks().add(new ScenarioTasks.Entry(RULE_HIT_ROOM, RULE_HIT_TEXT, ruleTask, "待認領"));
            waitForBoardReadWith(page, boardReads, List.of(menuTask, ruleTask));
        } finally {
            page.offResponse(seen);
        }
        new ShellFrame(world).settleAfter(() -> { });
    }

    private static Map<String, Object> menuItem() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", MENU_ITEM_KEY);
        item.put("label", MENU_ITEM_LABEL);
        item.put("dept", "housekeeping");
        item.put("type", "amenity");
        return item;
    }

    private static Map<String, Object> classificationRule() {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("rule_id", "fx-rule-" + UUID.randomUUID());
        rule.put("keywords", List.of(RULE_KEYWORD));
        rule.put("dept", "engineering");
        rule.put("type", "repair");
        return rule;
    }

    /** 這張單要真的直接進了部門的待認領，否則案例要驗的「不會出現在待派單頁」沒有意義。 */
    private void requireInPool(UUID taskId, String label) {
        Map<String, Object> row = world.backend().taskRow(taskId);
        if (!"pool".equals(row.get("state")) || "frontdesk".equals(row.get("dept"))) {
            throw new IllegalStateException(label + "沒有直接進部門待認領：state=" + row.get("state")
                    + "、dept=" + row.get("dept"));
        }
    }

    /**
     * 一筆一筆看畫面讀到的看板資料，直到有一筆含這兩張單；兩張單各自觸發一次重抓，
     * 第一次重抓可能只拿到其中一張，所以不能等到任一筆就停。
     * 回應的內容在事件處理之外才讀，事件處理裡只記下回應。
     */
    private static void waitForBoardReadWith(Page page, List<Response> boardReads, List<UUID> taskIds) {
        long deadline = System.currentTimeMillis() + REFETCH_TIMEOUT_MS;
        int checked = 0;
        while (true) {
            for (; checked < boardReads.size(); checked++) {
                Response read = boardReads.get(checked);
                if (read.ok() && containsAll(read.text(), taskIds)) {
                    return;
                }
            }
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) {
                throw new AssertionError("送出兩張直接進待認領的需求後，畫面沒有在 " + REFETCH_TIMEOUT_MS / 1_000
                        + " 秒內重抓到含這兩張單的看板資料（期間讀到 " + boardReads.size() + " 次看板）");
            }
            int seenSoFar = checked;
            try {
                page.waitForCondition(() -> boardReads.size() > seenSoFar,
                        new Page.WaitForConditionOptions().setTimeout(left));
            } catch (TimeoutError noNewRead) {
                // 時間到了還沒有新的讀取，回到迴圈開頭由上面的期限檢查報出好懂的訊息。
            }
        }
    }

    private static boolean containsAll(String body, List<UUID> taskIds) {
        return taskIds.stream().allMatch(id -> body.contains(id.toString()));
    }

    private static boolean isBoardResponse(Response response) {
        Network.Api board = Network.DESK_BOARD;
        String path = response.url().split("[?#]")[0];
        return board.method().equals(response.request().method()) && path.endsWith(board.path());
    }
}
