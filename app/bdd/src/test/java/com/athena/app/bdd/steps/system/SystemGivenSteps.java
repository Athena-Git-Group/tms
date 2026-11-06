package com.athena.app.bdd.steps.system;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.zh_tw.假設;

/** 系統層模組的 Given。 */
public class SystemGivenSteps {

    private final AppiumWorld world;

    public SystemGivenSteps(AppiumWorld world) {
        this.world = world;
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
