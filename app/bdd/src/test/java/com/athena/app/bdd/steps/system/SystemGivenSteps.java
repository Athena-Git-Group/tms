package com.athena.app.bdd.steps.system;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.假設;

/** 系統層模組的 Given。 */
public class SystemGivenSteps {

    private final AppiumWorld world;

    public SystemGivenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 怎麼做：裝置仍連網，但攔截網路層讓即時查詢（如今日進度、待認領）逾時或間歇失敗，
     * 觸發 App 的輪詢降級模式。權威狀態落地：後端資料不變，只是 App 端判定連線不穩定。
     */
    @假設("{string} 的連線目前不穩定")
    public void connectionUnstable(String staffName) {
        throw new PendingException();
    }

    /**
     * 怎麼做：在離線狀態下對任一支援離線暫存的動作（開立工務單或完成任務）各執行若干次，
     * 讓 App 本機待送出佇列累計到指定筆數，不要求筆數來源的動作種類一致。權威狀態落地：
     * 後端還沒有這些筆對應的資料。
     */
    @假設("{string} 的手機有 {int} 筆待同步的項目")
    public void phoneHasPendingSyncItems(String staffName, int count) {
        throw new PendingException();
    }
}
