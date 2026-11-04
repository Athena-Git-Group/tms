package com.athena.app.bdd.steps.pool;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.當;

/** 待認領模組的 When。 */
public class PoolWhenSteps {

    private final AppiumWorld world;

    public PoolWhenSteps(AppiumWorld world) {
        this.world = world;
    }

    /** 怎麼做：以該員工登入後切到待認領分頁，等列表載入完成。 */
    @當("{string} 在 App 打開待認領")
    public void openPool(String staffName) {
        throw new PendingException();
    }

    /** 怎麼做：以該員工（可為主管）登入後打開房務部門的待認領，等載入完成。 */
    @當("{string} 在 App 打開房務待認領")
    public void openHousekeepingPool(String staffName) {
        throw new PendingException();
    }

    /**
     * 怎麼做：點該分類標籤，等列表更新。
     * accessibility id：{@code pool.category.{category}.chip}。
     */
    @當("{string} 點選待認領分類 {string}")
    public void selectPoolCategory(String staffName, String category) {
        throw new PendingException();
    }
}
