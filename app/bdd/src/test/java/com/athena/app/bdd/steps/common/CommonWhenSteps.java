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
