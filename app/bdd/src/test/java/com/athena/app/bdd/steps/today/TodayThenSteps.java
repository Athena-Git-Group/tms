package com.athena.app.bdd.steps.today;

import com.athena.app.bdd.AppiumWorld;
import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 今日進度模組的 Then（Rule：首頁顯示後端算出的今日進度／依推薦順序列出待開始的單／顯示連續零退回天數）。 */
public class TodayThenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    /** 首頁待開始區每張任務卡的 accessibility id 是 {@code home.task.{房號}.card}。 */
    private static final String TASK_CARD_PREFIX = "home.task.";
    private static final String TASK_CARD_SUFFIX = ".card";

    private final AppiumWorld world;

    public TodayThenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 必查：首頁進度區逐項顯示表格中的值，並與後端今日進度回應一致。
     * accessibility id：{@code home.progress.cleaned-ratio}、{@code home.progress.remaining-minutes}、
     * {@code home.progress.inspected-count}、{@code home.progress.ad-hoc-count}。
     *
     * <p>兩件事分開斷言：先確認後端算出來的就是表上的值（數字的真相在後端，App 不自己算），
     * 再確認畫面顯示的就是那個值。只比畫面會讓「App 自己湊出正確數字」也算過；只比後端則
     * 完全沒驗到畫面。
     */
    @那麼("首頁的今日進度顯示如下：")
    public void homeProgressShows(DataTable table) {
        JsonNode today = todayResponse();
        for (Map<String, String> row : table.asMaps()) {
            String item = requireCell(row, "項目");
            String expected = requireCell(row, "值");
            String fromBackend = backendValueOf(today, item);
            if (!expected.equals(fromBackend)) {
                throw new AssertionError("後端 GET /me/today 的「" + item + "」與劇本不符：預期 "
                        + expected + "，實際 " + fromBackend);
            }
            String id = progressIdOf(item);
            String onScreen = world.waitForLabelOf(id, expected, SCREEN_TIMEOUT);
            if (!expected.equals(onScreen)) {
                throw new AssertionError("首頁今日進度的「" + item + "」（" + id + "）不符：預期 "
                        + expected + "，實際 " + onScreen);
            }
        }
    }

    /** 表上的項目名稱 → 畫面節點 id（ui-plan F1 R1 的四格）。 */
    private static String progressIdOf(String item) {
        return switch (item) {
            case "打掃完成／總數" -> "home.progress.cleaned-ratio";
            case "查房通過" -> "home.progress.inspected-count";
            case "臨時需求完成" -> "home.progress.ad-hoc-count";
            case "預估剩餘工時" -> "home.progress.remaining-minutes";
            default -> throw new IllegalArgumentException("今日進度沒有「" + item + "」這一格");
        };
    }

    /** 表上的項目名稱 → 後端 {@code MyToday} 的對應值，組成跟表上同一種寫法的字串。 */
    private static String backendValueOf(JsonNode today, String item) {
        return switch (item) {
            case "打掃完成／總數" ->
                    today.path("cleaningDone").asInt() + " / " + today.path("cleaningTotal").asInt();
            case "查房通過" -> String.valueOf(today.path("inspectedCount").asInt());
            case "臨時需求完成" -> String.valueOf(today.path("adHocCompleted").asInt());
            case "預估剩餘工時" -> today.path("remainingMinutes").asInt() + " 分";
            default -> throw new IllegalArgumentException("今日進度沒有「" + item + "」這一格");
        };
    }

    /**
     * 必查：後端推薦順序剛好等於清單，且清單上這幾間在首頁任務列由上而下的相對順序與清單一致。
     * accessibility id：{@code home.task.{房號}.card}。房號清單以「、」分隔，純文字（非 quoted
     * string），故用 regex 形式的 step definition 擷取。
     *
     * <p>分兩段比。後端那一段比 {@code nextTasks} 的房號順序——推薦順序的真相在後端（重做多的
     * 先、非打掃先、再依房號），App 只負責照收到的順序畫。畫面那一段比的是**子序列**：任務列
     * 收的是今天名下未取消的全部單（含進行中、已延後、已中斷、已完成），所以把畫面上不在清單
     * 裡的房號濾掉之後，剩下的順序要與清單一致——不驗「任務列只有這幾張」。
     */
    @那麼("^首頁任務列中待開始的單依序為 (.+)$")
    public void homeNextTasksOrder(String roomListText) {
        List<String> expected = Arrays.stream(roomListText.split("、"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        List<String> fromBackend = new ArrayList<>();
        todayResponse().path("nextTasks").forEach(next -> fromBackend.add(next.path("roomNo").asText()));
        if (!expected.equals(fromBackend)) {
            throw new AssertionError("後端 GET /me/today 的 nextTasks 順序與劇本不符：預期 "
                    + expected + "，實際 " + fromBackend);
        }

        List<String> onScreen = roomNosOnScreen();
        List<String> subsequence = onScreen.stream().filter(expected::contains).toList();
        if (!expected.equals(subsequence)) {
            throw new AssertionError("首頁任務列上這幾間的相對順序不符：預期 " + expected
                    + "，實際 " + subsequence + "（任務列完整順序 " + onScreen + "）");
        }
    }

    /**
     * 畫面上任務卡的房號，依由上而下排序（{@link AppiumWorld#findTestIdsWithPrefix}
     * 已照 Y 座標排好）。卡內若有子節點，id 會比 {@code home.task.{房號}.card} 更長，
     * 所以只收剛好以 {@code .card} 收尾的那一層。
     */
    private List<String> roomNosOnScreen() {
        return world.findTestIdsWithPrefix(TASK_CARD_PREFIX).stream()
                .filter(id -> id.endsWith(TASK_CARD_SUFFIX))
                .map(id -> id.substring(TASK_CARD_PREFIX.length(), id.length() - TASK_CARD_SUFFIX.length()))
                .filter(roomNo -> !roomNo.contains("."))
                .toList();
    }

    /**
     * 必查：首頁有連續零退回天數，數值與後端回應一致。計算定義待 clarify（見 feature 內
     * 「[need clarification]」註記：連續 N 天零退回怎麼算）。
     * accessibility id：{@code home.streak-badge}（ui-plan.md 標記「待補畫面後才掛」）。
     */
    @那麼("首頁顯示連續零退回天數")
    public void homeShowsZeroReturnStreak() {
        String expected = String.valueOf(todayResponse().path("zeroReturnStreakDays").asInt());
        String onScreen = world.waitForLabelOf("home.streak-badge", expected, SCREEN_TIMEOUT);
        if (!expected.equals(onScreen)) {
            throw new AssertionError("首頁連續零退回天數（home.streak-badge）不符：預期 "
                    + expected + "，實際 " + onScreen);
        }
    }

    /** 權威讀回：以目前登入者的身分讀 {@code GET /me/today}，就是首頁那一份資料的來源。 */
    private JsonNode todayResponse() {
        return world.backend().getJson("/me/today", world.loggedInStaff());
    }

    private static String requireCell(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return value;
    }
}
