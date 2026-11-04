package com.athena.app.bdd.steps.pool;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.假設;

/** 待認領模組的 Given。 */
public class PoolGivenSteps {

    private final AppiumWorld world;

    public PoolGivenSteps(AppiumWorld world) {
        this.world = world;
    }

    /** 怎麼做：透過後端測試資料建立待認領任務。權威狀態落地：後端有這些 pool 的任務。 */
    @假設("房務待認領有以下任務：")
    public void housekeepingPoolHasTasks(DataTable table) {
        throw new PendingException();
    }

    /** 怎麼做：透過後端測試資料把該員工停用。權威狀態落地：後端 staff 為停用，名下已接任務不變。 */
    @假設("{string} 已停用")
    public void staffDeactivated(String staffName) {
        throw new PendingException();
    }

    /** 怎麼做：以該員工登入後切到待認領分頁，等列表載入完成。 */
    @假設("{string} 已在 App 打開待認領")
    public void alreadyOpenedPool(String staffName) {
        throw new PendingException();
    }
}
