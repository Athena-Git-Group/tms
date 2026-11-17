package com.athena.app.bdd.steps.pool;

import com.athena.app.bdd.AppiumWorld;
import com.athena.bddsupport.BackendChannel;
import io.cucumber.java.zh_tw.當;

import java.time.Duration;

public class PoolWhenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    private final AppiumWorld world;
    private final PoolContext context;

    public PoolWhenSteps(AppiumWorld world, PoolContext context) {
        this.world = world;
        this.context = context;
    }

    @當("{string} 在 App 打開待認領")
    public void openPool(String staffName) {
        PoolDept.ensureOnPool(world, world.staff(staffName));
    }

    @當("{string} 在 App 打開房務待認領")
    public void openHousekeepingPool(String staffName) {
        BackendChannel.CreatedStaff staff = world.staff(staffName);
        PoolDept.ensureOnPool(world, staff);
        if (PoolDept.isManager(staff)) {
            PoolDept.switchTo(world, PoolDept.HOUSEKEEPING);
        }
    }

    @當("{string} 在待認領切到 {string}")
    public void switchPoolDept(String managerName, String deptChinese) {
        BackendChannel.CreatedStaff manager = world.staff(managerName);
        if (!PoolDept.isManager(manager)) {
            throw new IllegalArgumentException("只有主管的待認領有部門切換，「" + managerName + "」的角色是 " + manager.role());
        }
        PoolDept.ensureOnPool(world, manager);
        PoolDept.switchTo(world, PoolDept.deptKey(deptChinese));
        context.selectCategory("all");
    }

    @當("{string} 點選待認領分類 {string}")
    public void selectPoolCategory(String staffName, String category) {
        world.ensureOnPool(world.staff(staffName));
        String key = PoolThenSteps.categoryKey(category);
        world.tapTestId("pool.category." + key + ".chip", SCREEN_TIMEOUT);
        context.selectCategory(key);
    }

    @當("{string} 在 App 認領房務待認領的 {string}")
    public void claimHousekeepingPoolTask(String staffName, String roomNo) {
        world.staff(staffName);
        world.tapTestId("pool.card." + roomNo + ".claim-button", SCREEN_TIMEOUT);
    }

    @當("{string} 在待認領點開 {string} 的單卡")
    public void openPoolCard(String staffName, String roomNo) {
        world.staff(staffName);
        world.tapTestId("pool.card." + roomNo, SCREEN_TIMEOUT);
        world.waitForTestId("task.step-track", SCREEN_TIMEOUT);
        world.enteredTaskDetail(roomNo);
    }

    @當("{string} 點待認領空狀態的動作鈕")
    public void tapPoolEmptyStateAction(String staffName) {
        world.ensureOnPool(world.staff(staffName));
        world.tapTestId("pool.empty-state.action", SCREEN_TIMEOUT);
        context.selectCategory("all");
    }
}
