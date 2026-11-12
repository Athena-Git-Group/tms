package com.athena.app.bdd.steps.common;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import com.athena.app.bdd.steps.manager.ManagerHomeWait;
import io.cucumber.java.zh_tw.當;

import java.time.Duration;

public class CommonWhenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    private final AppiumWorld world;

    public CommonWhenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 怎麼做：以該員工登入後進入依角色分流的首頁，等首頁資料載入完成：房務首頁等今日進度、
     * 工務首頁等工務今日進度、主管首頁等格子數字（{@code getManagerQueues}；房務主管待查房與
     * 待驗收兩格，工務主管只有待驗收一格）與名下的單（{@code getMyToday}）。哪位主管有哪幾格由
     * {@link ManagerHomeWait} 決定。
     *
     * <p>「載入完成」以資料填進去的那個節點出現為準（{@code home.progress.cleaned-ratio}、
     * {@code engHome.progress.in-hand-count}、{@code manager.home.tile.*.count}）：首頁一進去
     * 先是空的，後端回來才渲染，等畫面殼出現就往下斷言會讀到還沒填值的畫面。
     *
     * <p>「打開首頁」是真的把畫面帶回首頁（{@link AppiumWorld#ensureOnScreenRoot}），不是原地等：
     * 員工可能正停在任務頁或清單上，那時要先按返回退回來。分流看員工的角色與部門，與
     * {@code landing.dart} 同一條判定；不得假設一律是房務首頁。
     */
    @當("{string} 在 App 打開首頁")
    public void openHome(String staffName) {
        BackendChannel.CreatedStaff staff = world.staff(staffName);
        world.ensureLoggedInAs(staff);
        if ("manager".equals(staff.role())) {
            world.ensureOnScreenRoot("manager.home", SCREEN_TIMEOUT);
            ManagerHomeWait.await(world, staffName, SCREEN_TIMEOUT);
        } else if ("engineering".equals(staff.dept())) {
            world.ensureOnScreenRoot("engHome.screen", SCREEN_TIMEOUT);
            world.waitForTestId("engHome.progress.in-hand-count", SCREEN_TIMEOUT);
        } else {
            world.ensureOnHome(SCREEN_TIMEOUT);
            world.waitForTestId("home.progress.cleaned-ratio", SCREEN_TIMEOUT);
        }
    }

    @當("{string} 在 App 開始 {string}")
    public void startTask(String staffName, String task) {
        world.ensureOnTaskDetail(world.staff(staffName), task);
        world.tapTestId("task.primary-button", SCREEN_TIMEOUT);
    }

    @當("{string} 在 App 打開 {string} 的任務頁")
    public void openTaskDetail(String staffName, String task) {
        world.ensureOnTaskDetail(world.staff(staffName), task);
    }

    @當("{string} 在 App 完成 {string}")
    public void completeTask(String staffName, String task) {
        world.ensureOnTaskDetail(world.staff(staffName), task);
        world.tapTestId("task.primary-button", SCREEN_TIMEOUT);
    }
}
