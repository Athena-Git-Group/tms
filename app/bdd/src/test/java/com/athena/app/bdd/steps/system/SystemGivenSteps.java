package com.athena.app.bdd.steps.system;

import com.athena.app.bdd.AppiumWorld;
import com.athena.bddsupport.BackendChannel;
import com.athena.app.bdd.steps.manager.ManagerHomeWait;
import io.cucumber.java.zh_tw.假設;
import org.openqa.selenium.WebElement;

import java.time.Duration;

public class SystemGivenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    private static final String OFFLINE_BLOCKED_ALERT = "system.offline-blocked-alert";
    private static final String OFFLINE_BLOCKED_ALERT_DISMISS = "system.offline-blocked-alert.dismiss-button";

    /** 面板左上角那顆關閉鈕沒有 test id，XCUITest 認得到的是圖示的無障礙標籤。 */
    private static final String SHEET_CLOSE_BUTTON = "關閉";

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
        world.waitForTestIdGone(OFFLINE_BLOCKED_ALERT, SCREEN_TIMEOUT);
        closeOpenSheet();
        int before = world.offlineWindowPendingCount().orElseThrow(() -> new IllegalStateException(
                "切到離線的那一刻沒有量到本機待送出佇列的筆數，無從確認這次操作沒有進佇列"));
        int after = world.pendingSyncCountInApp();
        if (after != before) {
            throw new AssertionError("「" + action + "」被離線擋下了，本機待送出佇列卻多了："
                    + "斷網時 " + before + " 筆，現在 " + after + " 筆");
        }
    }

    /**
     * 驗收不通過被擋下之後，不通過面板會留在畫面上（原因要保留），而面板是蓋住整頁的，
     * 沒有返回鈕可以退。後面的斷言要回主管首頁重開單，所以這裡先把面板關掉。
     */
    private void closeOpenSheet() {
        for (WebElement close : world.driver().findElements(world.byTestId(SHEET_CLOSE_BUTTON))) {
            close.click();
            world.waitForTestIdGone(SHEET_CLOSE_BUTTON, SCREEN_TIMEOUT);
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
