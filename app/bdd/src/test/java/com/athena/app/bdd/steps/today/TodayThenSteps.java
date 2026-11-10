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

    private static String progressIdOf(String item) {
        return switch (item) {
            case "打掃完成／總數" -> "home.progress.cleaned-ratio";
            case "查房通過" -> "home.progress.inspected-count";
            case "臨時需求完成" -> "home.progress.ad-hoc-count";
            case "預估剩餘工時" -> "home.progress.remaining-minutes";
            default -> throw new IllegalArgumentException("今日進度沒有「" + item + "」這一格");
        };
    }

    private static String backendValueOf(JsonNode today, String item) {
        return switch (item) {
            case "打掃完成／總數" ->
                    today.path("cleaningDone").asInt() + " / " + today.path("cleaningTotal").asInt();
            case "查房通過" -> String.valueOf(today.path("inspectedCount").asInt());
            case "臨時需求完成" -> today.path("adHocCompleted").asInt() + " 單";
            case "預估剩餘工時" -> today.path("remainingMinutes").asInt() + " 分鐘";
            default -> throw new IllegalArgumentException("今日進度沒有「" + item + "」這一格");
        };
    }

    /**
     * 必查：後端推薦順序剛好等於清單，且清單上這幾間在首頁任務列由上而下的相對順序與清單一致。
     * accessibility id：{@code home.task.{房號}.card}。房號清單以「、」分隔，純文字（非 quoted
     * string），故用 regex 形式的 step definition 擷取。
     *
     * <p>分兩段比，但只有後端那一段驗順序。推薦順序的真相在後端 {@code nextTasks}（重做多的
     * 先、非打掃先、再依房號）。畫面那一段只驗「清單中的每一間都在任務列上」：任務列的排序由
     * {@code MyToday.todayTasks} 定（未結束優先、進行中最前、其餘依房號遞增，App 不重排），
     * 與推薦順序本就是兩套——備品單在推薦順序裡靠前、在房號遞增裡靠後，畫面上強求相對順序
     * 相符會與契約打架。任務列也收已延後、已中斷、已完成的單，所以不驗「只有這幾張」。
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
        List<String> missing = expected.stream().filter(roomNo -> !onScreen.contains(roomNo)).toList();
        if (!missing.isEmpty()) {
            throw new AssertionError("首頁任務列上少了待開始的單：缺 " + missing
                    + "（任務列實際為 " + onScreen + "）");
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
     * 必查：首頁主卡的位置是空狀態，標題與說明剛好是這兩句，而且後端確實沒有可以開始的單。
     * accessibility id：{@code home.empty-state}，它的可見文字是「標題。說明」。
     *
     * <p>兩段都比：空狀態有三種說法（手上沒有任務、今天的任務都完成了、目前沒有可以開始的
     * 單），只比標題的話，挑錯分支時說明會跟著錯而測試仍然通過——員工看到的下一步就指錯地方。
     * 後端那一段確認的是「畫面該進空狀態」：進行中與推薦清單都空，主卡本來就沒有東西可畫。
     */
    @那麼("首頁空狀態顯示標題 {string}、說明 {string}")
    public void homeShowsEmptyState(String title, String description) {
        JsonNode today = todayResponse();
        if (!today.path("nextTasks").isEmpty()) {
            throw new AssertionError("後端 GET /me/today 還有推薦的下一間，首頁不該是空狀態");
        }
        String expected = title + "。" + description;
        String onScreen = world.waitForLabelOf("home.empty-state", expected, SCREEN_TIMEOUT);
        if (!expected.equals(onScreen)) {
            throw new AssertionError("首頁空狀態（home.empty-state）不符：預期 "
                    + expected + "，實際 " + onScreen);
        }
    }

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
