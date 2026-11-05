package com.athena.app.bdd.steps.push;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.zh_tw.當;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** 推播模組的 When。 */
public class PushWhenSteps {

    private final AppiumWorld world;

    public PushWhenSteps(AppiumWorld world) {
        this.world = world;
    }

    /** 怎麼做：在通知中心點這則推播，等 App 切到前景並完成導頁。 */
    @當("{string} 點開這則推播")
    public void tapThisPush(String staffName) {
        world.tapPushNotification();
    }

    // ──── 通知中心（T309–T311 落點） ────

    /** 等候畫面元素的統一逾時：導頁有轉場動畫，用輪詢等待取代固定 sleep，逾時才當失敗。 */
    private static final Duration UI_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 怎麼做：以該員工登入後點 Tab bar「通知」，等列表載入完成。
     *
     * <p>「登入」走 {@code AppiumWorld.ensureLoggedInAs}（App 一律從登入畫面
     * 開始）。「通知」入口目前是首頁骨架上的文字按鈕，沒有配 accessibility id，
     * 所以入口用文字 label 定位；通知中心本體有 {@code notification.screen}，
     * 用它確認列表畫面真的立住，而不是點完就往下走。
     */
    @當("{string} 在 App 打開通知中心")
    public void openNotificationCenter(String staffName) {
        var staff = world.staff(staffName);
        world.setNotificationViewer(staff);
        world.ensureLoggedInAs(staff);
        world.waitForLabelContaining("通知", UI_TIMEOUT).click();
        world.waitForTestId("notification.screen", UI_TIMEOUT);
    }

    /**
     * 怎麼做：在通知中心點該項目，等 App 完成導頁。權威狀態落地：該通知標為已讀
     * （後端 {@code notifications.read_at} 不再是 NULL）。
     *
     * <p>列表項的 accessibility id 是 {@code notification.item.{id}.title}，{id} 是
     * 後端通知紀錄的 id，但劇本只給人看得懂的標題，所以要先回後端把標題換成 id
     * 才有辦法定位，不能用畫面文字搜尋充當定位（文字可能同時出現在別的區塊）。
     */
    @當("{string} 在通知中心點選 {string}")
    public void tapNotificationItem(String staffName, String title) {
        // 用 tapTestId 而不是 waitForTestId().click()：前者會等元素位置定格
        // 再點，避免頁面轉場動畫還沒跑完就點到移動中的座標（點了沒效果）。
        world.tapTestId("notification.item." + notificationId(staffName, title) + ".title", UI_TIMEOUT);
    }

    /** 怎麼做：點 {@code notification.mark-all-read-button}。權威狀態落地：該員工所有通知都標為已讀。 */
    @當("{string} 在通知中心點「全部標為已讀」")
    public void tapMarkAllNotificationsRead(String staffName) {
        world.tapTestId("notification.mark-all-read-button", UI_TIMEOUT);
    }

    /**
     * 把通知標題換成後端 notifications 紀錄的 id，給列表項的 accessibility id 拼字串用。
     * 不是剛好一筆就直接噴錯：同標題多筆代表 Given 的通知表建錯，此時隨便挑一筆
     * 會讓後面的已讀斷言驗到錯的那一筆，寧可在這裡停下來。
     */
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
