package com.athena.app.bdd.steps.system;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import com.athena.app.bdd.steps.manager.ManagerHomeWait;
import io.cucumber.java.zh_tw.假設;

import java.time.Duration;

public class SystemGivenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    private static final String OFFLINE_BLOCKED_ALERT = "system.offline-blocked-alert";
    private static final String OFFLINE_BLOCKED_ALERT_DISMISS = "system.offline-blocked-alert.dismiss-button";

    private final AppiumWorld world;
    private final SystemWhenSteps whenSteps;

    public SystemGivenSteps(AppiumWorld world, SystemWhenSteps whenSteps) {
        this.world = world;
        this.whenSteps = whenSteps;
    }

    @假設("{string} 已在 App 登入，停在首頁")
    public void loggedInAndOnHome(String staffName) {
        BackendChannel.CreatedStaff staff = world.staff(staffName);
        world.ensureLoggedInAs(staff);
        if ("manager".equals(staff.role())) {
            world.waitForTestId("manager.home", SCREEN_TIMEOUT);
            ManagerHomeWait.await(world, staffName, SCREEN_TIMEOUT);
        } else if ("engineering".equals(staff.dept())) {
            world.waitForTestId("engHome.screen", SCREEN_TIMEOUT);
            world.waitForTestId("engHome.progress.in-hand-count", SCREEN_TIMEOUT);
        } else {
            world.waitForTestId("home.screen", SCREEN_TIMEOUT);
            world.waitForTestId("home.progress.cleaned-ratio", SCREEN_TIMEOUT);
        }
    }

    @假設("{string} 在離線時嘗試過 {string} 並被擋下")
    public void attemptedWhileOfflineAndBlocked(String staffName, String action) {
        loggedInAndOnHome(staffName);
        world.settleDeferredStarts();
        world.goOffline();
        whenSteps.attemptActionWhileOffline(staffName, action);
        world.waitForTestId(OFFLINE_BLOCKED_ALERT, SCREEN_TIMEOUT);
        world.tapTestId(OFFLINE_BLOCKED_ALERT_DISMISS, SCREEN_TIMEOUT);
        int before = world.offlineWindowPendingCount().orElseThrow(() -> new IllegalStateException(
                "切到離線的那一刻沒有量到本機待送出佇列的筆數，無從確認這次操作沒有進佇列"));
        int after = world.pendingSyncCountInApp();
        if (after != before) {
            throw new AssertionError("「" + action + "」被離線擋下了，本機待送出佇列卻多了："
                    + "斷網時 " + before + " 筆，現在 " + after + " 筆");
        }
    }

    @假設("{string} 的連線目前不穩定")
    public void connectionUnstable(String staffName) {
        world.staff(staffName);
        world.postBackdoorJson("/flaky-connection", "{\"flaky\":true}");
    }

    @假設("{string} 的手機有 {int} 筆待同步的項目")
    public void phoneHasPendingSyncItems(String staffName, int count) {
        world.staff(staffName);
        world.postBackdoorJson("/offline-queue", "{\"count\":" + count + "}");
    }
}
