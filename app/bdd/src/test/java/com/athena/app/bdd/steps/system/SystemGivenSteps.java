package com.athena.app.bdd.steps.system;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import com.athena.app.bdd.steps.manager.ManagerHomeWait;
import io.cucumber.java.zh_tw.假設;

import java.time.Duration;

public class SystemGivenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    /** 離線擋下的那個提示與它的關閉鈕（{@code app/mobile/lib/core/system_status.dart}）。 */
    private static final String OFFLINE_BLOCKED_ALERT = "system.offline-blocked-alert";
    private static final String OFFLINE_BLOCKED_ALERT_DISMISS = "system.offline-blocked-alert.dismiss-button";

    private final AppiumWorld world;
    private final SystemWhenSteps whenSteps;

    public SystemGivenSteps(AppiumWorld world, SystemWhenSteps whenSteps) {
        this.world = world;
        this.whenSteps = whenSteps;
    }

    /**
     * 怎麼做：以該員工身分登入 App，停在依角色分流的首頁，等首頁載入完成。這是 Arrange，
     * 用在接下來要切離線的案例（離線後不能再登入）；登入本身是受測動作時用「帳號與登入」
     * 模組的句型。
     *
     * <p>首頁依角色分流（{@code landing.dart}）：主管停在主管首頁，等該主管種類的格子數字
     * 與名下的單（{@link ManagerHomeWait}：房務主管兩格、工務主管一格）；不是主管的工務停在
     * 工務首頁，等工務今日進度；其他部門停在房務首頁，等今日進度。
     * 一律等房務首頁會讓工務與主管的登入永遠逾時。
     */
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

    /**
     * 怎麼做：以該員工身分登入，把裝置切到無網路，觸發該動作並等離線提示出現後關掉提示，
     * 停在原畫面，裝置仍離線。這是 Arrange，給「之後恢復連線」的案例鋪底。
     * 權威狀態落地：後端該單狀態不變；App 本機佇列沒有這筆。
     *
     * <p>動作的值集與入口同「在 App 離線時嘗試」那一句（員工：開始任務、認領待認領的單、
     * 延後任務、開立新工務單；主管：送出查房結果、驗收通過、驗收不通過），所以直接呼叫它，
     * 不另外複製一份動線。切離線走與「手機目前離線」相同的兩步：先把測試資料欠的「在 App 按開始」補完，
     * 再斷網並量下離線視窗的開場基準。
     *
     * <p>「App 本機佇列沒有這筆」在這裡就先核對：關掉提示後佇列筆數要與斷網那一刻相同。
     * 這一句鋪底的案例驗的是恢復連線後不補送，佇列若在這裡就多了一筆，後面那句會紅在
     * 「單的狀態變了」，看起來像補送的問題，其實是擋下的那一刻就沒擋乾淨。
     */
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

    /**
     * 怎麼做：把裝置切到「連得上網、但即時推送不可靠」。後端資料不變，只有 App 自己
     * 判定連線不穩定，並改用每 5 秒重查一次的降級方式取資料。
     */
    @假設("{string} 的連線目前不穩定")
    public void connectionUnstable(String staffName) {
        world.staff(staffName);
        world.postBackdoorJson("/flaky-connection", "{\"flaky\":true}");
    }

    /**
     * 怎麼做：讓 App 的離線佇列帶著指定筆數開場。權威狀態落地：後端還沒有這些筆對應
     * 的資料。
     *
     * <p>這個起始狀態沒辦法用真實操作堆出來：離線時唯一還能做的動作是完成進行中的單，
     * 而後端保證同一位員工最多只有一筆進行中，所以真實操作最多只累積得到一筆。測試後門
     * 的 {@code /offline-queue} 把指定筆數放進同一條佇列，離線列顯示的仍然是佇列長度
     * 本身，不是另外一個數字。
     */
    @假設("{string} 的手機有 {int} 筆待同步的項目")
    public void phoneHasPendingSyncItems(String staffName, int count) {
        world.staff(staffName);
        world.postBackdoorJson("/offline-queue", "{\"count\":" + count + "}");
    }
}
