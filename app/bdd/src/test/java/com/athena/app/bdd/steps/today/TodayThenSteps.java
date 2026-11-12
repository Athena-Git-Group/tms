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

    private static final String TASK_CARD_PREFIX = "home.task.";
    private static final String TASK_CARD_SUFFIX = ".card";

    private final AppiumWorld world;

    public TodayThenSteps(AppiumWorld world) {
        this.world = world;
    }

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

    private List<String> roomNosOnScreen() {
        return world.findTestIdsWithPrefix(TASK_CARD_PREFIX).stream()
                .filter(id -> id.endsWith(TASK_CARD_SUFFIX))
                .map(id -> id.substring(TASK_CARD_PREFIX.length(), id.length() - TASK_CARD_SUFFIX.length()))
                .filter(roomNo -> !roomNo.contains("."))
                .toList();
    }

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
