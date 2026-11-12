package com.athena.app.bdd.steps.pool;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
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

    /**
     * 怎麼做：以該員工（可為主管）登入後打開房務部門的待認領，等載入完成。
     *
     * <p>現場員工的待認領一律是自己部門的池（契約 listDeptPool 省略 {@code dept} 就依登入
     * 身分分流），走同一個待認領入口即可。主管不屬於任何部門，待認領多了一個部門切換：
     * 切到待認領分頁之後再把部門切到房務（{@code pool.dept-switch.housekeeping}）。
     */
    @當("{string} 在 App 打開房務待認領")
    public void openHousekeepingPool(String staffName) {
        BackendChannel.CreatedStaff staff = world.staff(staffName);
        PoolDept.ensureOnPool(world, staff);
        if (PoolDept.isManager(staff)) {
            PoolDept.switchTo(world, PoolDept.HOUSEKEEPING);
        }
    }

    /**
     * 怎麼做：在已開著的待認領點標題下方的部門切換（{@code pool.dept-switch.{housekeeping|engineering}}），
     * 等列表以該部門重新載入（App 叫 listDeptPool 明帶 {@code dept}）。
     * 權威狀態落地：不改後端；App 本機的部門選擇寫成該部門。回寫：列表與分類磚。
     */
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

    /**
     * 怎麼做：點空狀態底下那顆文字按鈕，等列表更新。
     * accessibility id：{@code pool.empty-state.action}。
     *
     * <p>待認領的空狀態只有一顆按鈕，按下去就是把分類切回全部，所以情境裡選中
     * 的分類也跟著回到全部——後面的列表斷言要拿不帶分類的查詢去讀回。
     */
    @當("{string} 點待認領空狀態的動作鈕")
    public void tapPoolEmptyStateAction(String staffName) {
        world.ensureOnPool(world.staff(staffName));
        world.tapTestId("pool.empty-state.action", SCREEN_TIMEOUT);
        context.selectCategory("all");
    }
}
