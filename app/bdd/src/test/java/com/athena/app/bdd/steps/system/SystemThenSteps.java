package com.athena.app.bdd.steps.system;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.zh_tw.那麼;

import java.time.Duration;
import java.util.Map;

public class SystemThenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    private static final Duration SYNC_TIMEOUT = Duration.ofSeconds(15);

    private final AppiumWorld world;

    public SystemThenSteps(AppiumWorld world) {
        this.world = world;
    }

    @那麼("{string} 的完成已保存在手機，畫面標示待同步")
    public void completionSavedOnPhonePendingSync(String task) {
        world.waitForLabelOf("task.pending-sync-label", "已離線暫存，連線後自動同步", SCREEN_TIMEOUT);

        String stateWhileOffline = String.valueOf(world.taskRowByRoomNo(task).get("state"));
        if (!"doing".equals(stateWhileOffline)) {
            throw new AssertionError("離線完成不該改到後端，但「" + task + "」在後端已經是「"
                    + stateWhileOffline + "」");
        }

        world.goOnline();
        String state = waitForBackendState(task);
        if (!"done".equals(state) && !"inspected".equals(state)) {
            throw new AssertionError("恢復連線後「" + task + "」的完成沒有送出去，後端仍是「"
                    + state + "」");
        }
    }

    @那麼("離線列顯示 {string} 與待同步筆數 {int}")
    public void offlineBarShowsTextAndPendingCount(String text, int pendingCount) {
        assertLabel("system.offline-bar", text);
        assertLabel("system.offline-bar.pending-count", String.valueOf(pendingCount));
    }

    @那麼("連線異常列顯示 {string}")
    public void connectionIssueBarShowsText(String text) {
        assertLabel("system.connection-issue-bar", text);
    }

    private void assertLabel(String id, String expected) {
        String actual = world.waitForLabelOf(id, expected, SCREEN_TIMEOUT);
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    "「" + id + "」的文字不符：預期「" + expected + "」，實際「" + actual + "」");
        }
    }

    private String waitForBackendState(String roomNo) {
        long deadline = System.nanoTime() + SYNC_TIMEOUT.toNanos();
        String state = "";
        while (System.nanoTime() < deadline) {
            Map<String, Object> row = world.taskRowByRoomNo(roomNo);
            state = String.valueOf(row.get("state"));
            if (!"doing".equals(state)) {
                return state;
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等後端收到離線保存的完成時被中斷", e);
            }
        }
        return state;
    }
}
