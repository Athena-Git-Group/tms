package com.athena.app.bdd.steps.push;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.zh_tw.當;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class PushWhenSteps {

    private final AppiumWorld world;

    public PushWhenSteps(AppiumWorld world) {
        this.world = world;
    }

    @當("{string} 點開這則推播")
    public void tapThisPush(String staffName) {
        world.tapPushNotification();
    }

    private static final Duration UI_TIMEOUT = Duration.ofSeconds(10);

    @當("{string} 在 App 打開通知中心")
    public void openNotificationCenter(String staffName) {
        var staff = world.staff(staffName);
        world.setNotificationViewer(staff);
        world.ensureLoggedInAs(staff);
        world.waitForLabelContaining("通知", UI_TIMEOUT).click();
        world.waitForTestId("notification.screen", UI_TIMEOUT);
    }

    @當("{string} 在通知中心點選 {string}")
    public void tapNotificationItem(String staffName, String title) {
        world.tapTestId("notification.item." + notificationId(staffName, title) + ".title", UI_TIMEOUT);
    }

    @當("{string} 在通知中心點「全部標為已讀」")
    public void tapMarkAllNotificationsRead(String staffName) {
        world.tapTestId("notification.mark-all-read-button", UI_TIMEOUT);
    }

    private Object notificationId(String staffName, String title) {
        List<Map<String, Object>> rows = world.backend().queryRows(
                "SELECT id FROM notifications WHERE tenant_id = ? AND recipient_staff_id = ? AND title = ?",
                world.backend().ensureTenant(), world.staff(staffName).id(), title);
        if (rows.size() != 1) {
            throw new IllegalStateException("預期「" + staffName + "」有剛好一則標題為「" + title
                    + "」的通知，實際 " + rows.size() + " 則。Given 的通知表是否漏建或標題重複？");
        }
        return rows.get(0).get("id");
    }
}
