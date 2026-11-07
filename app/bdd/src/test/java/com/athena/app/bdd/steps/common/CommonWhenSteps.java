package com.athena.app.bdd.steps.common;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.zh_tw.當;

import java.time.Duration;

/** 介面根共通句的 When（dsl.md When 句型區）。 */
public class CommonWhenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    private final AppiumWorld world;

    public CommonWhenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 怎麼做：以該員工登入後進入首頁，等今日進度載入完成。accessibility id：{@code home.*}。
     *
     * <p>「載入完成」以進度第一格（{@code home.progress.cleaned-ratio}）出現為準：首頁一進去
     * 先是空的，{@code GET /me/today} 回來才渲染，等畫面殼（{@code home.screen}）出現就往下
     * 斷言會讀到還沒填值的畫面。
     *
     * <p>「打開首頁」是真的把畫面帶回首頁（{@link AppiumWorld#ensureOnHome}），不是原地等：
     * 員工可能正停在任務頁上，那時要先按返回退回來。
     */
    @當("{string} 在 App 打開首頁")
    public void openHome(String staffName) {
        world.ensureLoggedInAs(world.staff(staffName));
        world.ensureOnHome(SCREEN_TIMEOUT);
        world.waitForTestId("home.progress.cleaned-ratio", SCREEN_TIMEOUT);
    }

    /**
     * 怎麼做：在首頁或任務頁點該房的開始按鈕。權威狀態落地：成功時後端任務為 doing；
     * 被拒時後端不變。回寫：畫面上的提示與任務卡。accessibility id：{@code task.primary-button}。
     */
    @當("{string} 在 App 開始 {string}")
    public void startTask(String staffName, String task) {
        world.ensureOnTaskDetail(world.staff(staffName), task);
        world.tapTestId("task.primary-button", SCREEN_TIMEOUT);
    }

    /** 怎麼做：在首頁點該房的任務卡，進入任務頁。回寫：任務頁內容。 */
    @當("{string} 在 App 打開 {string} 的任務頁")
    public void openTaskDetail(String staffName, String task) {
        world.ensureOnTaskDetail(world.staff(staffName), task);
    }

    /**
     * 怎麼做：在任務頁點主按鈕「完成」。權威狀態落地：成功時後端任務依 {@code tasksComplete}
     * 語意轉為 done 或 inspected；失敗時後端不變。回寫：畫面提示、任務頁或完成慶祝頁內容。
     * accessibility id：{@code task.primary-button}。
     */
    @當("{string} 在 App 完成 {string}")
    public void completeTask(String staffName, String task) {
        world.ensureOnTaskDetail(world.staff(staffName), task);
        world.tapTestId("task.primary-button", SCREEN_TIMEOUT);
    }
}
