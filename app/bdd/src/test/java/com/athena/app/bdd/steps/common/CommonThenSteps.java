package com.athena.app.bdd.steps.common;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.那麼;

/** 介面根共通句的 Then（dsl.md Then 句型區）。 */
public class CommonThenSteps {

    private final AppiumWorld world;

    public CommonThenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 必查：畫面出現包含該文字的提示（Alert 或 Toast）。如果提示代表操作被拒，後端權威狀態
     * 沒有被改動。accessibility id：TODO（Alert／Toast 容器尚未在 ui-plan.md 逐一命名；
     * 例外是 {@code task.busy-alert}，已在表中列出，用於「一次處理一間」提示）。
     */
    @那麼("畫面提示 {string}")
    public void screenPrompts(String message) {
        throw new PendingException();
    }

    /**
     * 必查：任務卡或任務頁顯示該狀態文字。後端權威狀態與對照表一致。重新進入畫面後仍一致。
     * accessibility id：{@code task.status-chip}（配合 {@code --state} 修飾）。
     */
    @那麼("{string} 在 App 顯示為 {string}")
    public void taskDisplayedAs(String task, String state) {
        throw new PendingException();
    }
}
