package com.athena.app.bdd.steps.pool;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.zh_tw.當;

import java.time.Duration;

/** 待認領模組的 When。 */
public class PoolWhenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    private final AppiumWorld world;
    private final PoolContext context;

    public PoolWhenSteps(AppiumWorld world, PoolContext context) {
        this.world = world;
        this.context = context;
    }

    /** 怎麼做：以該員工登入後切到待認領分頁，等列表載入完成。 */
    @當("{string} 在 App 打開待認領")
    public void openPool(String staffName) {
        world.ensureOnPool(world.staff(staffName));
    }

    /**
     * 怎麼做：以該員工（可為主管）登入後打開房務部門的待認領，等載入完成。
     *
     * <p>App 沒有「選部門再看待認領」的入口，待認領一律是登入者自己部門的池
     * （契約 listDeptPool 只依登入身分的部門分流，沒有部門查詢參數），所以這裡
     * 走的是同一個待認領入口。
     */
    @當("{string} 在 App 打開房務待認領")
    public void openHousekeepingPool(String staffName) {
        world.ensureOnPool(world.staff(staffName));
    }

    /**
     * 怎麼做：點該分類標籤，等列表更新。
     * accessibility id：{@code pool.category.{category}.chip}。
     */
    @當("{string} 點選待認領分類 {string}")
    public void selectPoolCategory(String staffName, String category) {
        world.ensureOnPool(world.staff(staffName));
        String key = PoolThenSteps.categoryKey(category);
        world.tapTestId("pool.category." + key + ".chip", SCREEN_TIMEOUT);
        context.selectCategory(key);
    }
}
