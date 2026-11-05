package com.athena.app.bdd.steps.common;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.java.zh_tw.那麼;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.Map;

/** 介面根共通句的 Then（dsl.md Then 句型區）。 */
public class CommonThenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 狀態用語 → 那個用語額外要求的 {@code tasks.inspection}。只有查房結果會讓同一個
     * {@code state} 分成兩種用語（{@code inspected} 分查房通過與完成 · 免查房），其餘
     * 用語光看 state 就能分辨。
     */
    private static final Map<String, String> REQUIRED_INSPECTION = Map.of(
            "查房通過", "passed",
            "完成 · 免查房", "waived");

    /**
     * 狀態用語 → 那個用語額外要求的 {@code tasks.type} 是不是打掃類。{@code done} 在
     * 打掃類叫「待查房」、在其他類叫「已送達」。
     */
    private static final Map<String, Boolean> REQUIRED_CLEANING = Map.of(
            "待查房", Boolean.TRUE,
            "已送達", Boolean.FALSE);

    private final AppiumWorld world;

    public CommonThenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 必查：畫面出現包含該文字的提示（Alert 或 Toast）。
     *
     * <p>提示如果是 Alert，它是蓋在畫面上的 modal——Flutter 會把底下整頁的 semantics
     * 擋掉，後續對同一畫面的斷言會什麼都找不到。所以驗完文字就順手關掉提示，讓情境
     * 接著驗「被擋下的那一間仍然是待開始」這種畫面狀態（使用者的實際動線也是看完
     * 提示就把它關掉）。沒有可關的提示（Toast 自己會消失）就什麼都不做。
     */
    @那麼("畫面提示 {string}")
    public void screenPrompts(String message) {
        world.waitForLabelContaining(message, SCREEN_TIMEOUT);
        if (!world.driver().findElements(world.byTestId("task.busy-alert.dismiss-button")).isEmpty()) {
            world.tapTestId("task.busy-alert.dismiss-button", SCREEN_TIMEOUT);
        }
    }

    /**
     * 必查：任務卡或任務頁顯示該狀態文字（accessibility id {@code task.status-chip}），
     * 且後端權威狀態與「狀態用語對照」一致。兩邊都要對上才算通過：只看畫面會讓一個
     * 寫死文字的 chip 過關，只看後端則驗不到使用者看見什麼。
     */
    @那麼("{string} 在 App 顯示為 {string}")
    public void taskDisplayedAs(String task, String state) {
        assertBackendState(task, state);

        world.waitForTestId("task.status-chip", SCREEN_TIMEOUT);
        String actual = world.waitForLabelOf("task.status-chip", state, SCREEN_TIMEOUT);
        if (!state.equals(actual)) {
            throw new AssertionError(
                    "「" + task + "」的狀態 chip 不符：預期「" + state + "」，實際「" + actual + "」");
        }
    }

    /** 權威半邊：{@code tasks.state}（必要時加上 inspection／type）符合狀態用語對照。 */
    private void assertBackendState(String task, String state) {
        Map<String, Object> row = world.taskRowByRoomNo(task);
        String expectedState = BackendChannel.taskState(state);
        String actualState = String.valueOf(row.get("state"));
        if (!expectedState.equals(actualState)) {
            throw new AssertionError("「" + task + "」的後端狀態不符：預期 " + expectedState
                    + "（" + state + "），實際 " + actualState);
        }
        String requiredInspection = REQUIRED_INSPECTION.get(state);
        if (requiredInspection != null && !requiredInspection.equals(String.valueOf(row.get("inspection")))) {
            throw new AssertionError("「" + task + "」的後端查房結果不符：預期 " + requiredInspection
                    + "，實際 " + row.get("inspection"));
        }
        Boolean requiredCleaning = REQUIRED_CLEANING.get(state);
        if (requiredCleaning != null
                && requiredCleaning != "cleaning".equals(String.valueOf(row.get("type")))) {
            throw new AssertionError("「" + task + "」的任務類別與狀態用語「" + state + "」不搭：實際 "
                    + row.get("type"));
        }
    }
}
