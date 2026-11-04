package com.athena.app.bdd.steps.system;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.當;

/** 系統層模組的 When。 */
public class SystemWhenSteps {

    private final AppiumWorld world;

    public SystemWhenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 怎麼做：在離線狀態下觸發對應的畫面操作（點開始／點認領這單／點延後確認／點開工單
     * 送出）。權威狀態落地：操作一律被 App 擋下，不會呼叫後端對應 API；後端任務與工務單
     * 資料不變。回寫：畫面提示。
     */
    @當("{string} 在 App 離線時嘗試 {string}")
    public void attemptActionWhileOffline(String staffName, String action) {
        throw new PendingException();
    }
}
