package com.athena.app.bdd.steps.pool;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import com.athena.app.bdd.steps.common.CommonGivenSteps;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.Set;

/**
 * 主管待認領的「部門」這件事，集中在這一個地方處理。
 *
 * <p>現場員工的待認領只有自己部門那一池，後端照登入身分分流（契約 listDeptPool 省略
 * {@code dept}）。主管不屬於任何部門，待認領標題下方多了一個部門切換
 * （{@code pool.dept-switch.{housekeeping|engineering}}），App 把停留的部門記在這台手機
 * 上（{@link CommonGivenSteps#POOL_DEPT_PREFERENCE_KEY} 那一把鍵，沒有紀錄視為房務），
 * 叫 listDeptPool 時明帶 {@code dept}。測試要跟畫面比對的「目前部門」因此有兩個來源：
 * 員工看自己的部門，主管看手機上的那把鍵。
 */
final class PoolDept {

    static final String HOUSEKEEPING = "housekeeping";
    static final String ENGINEERING = "engineering";

    /** 主管待認領畫面的根節點（manager_ids.dart 的 {@code ManagerIds.poolDeptSwitch}）。 */
    static final String DEPT_SWITCH_ID = "pool.dept-switch";

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    private static final Set<String> POOL_DEPTS = Set.of(HOUSEKEEPING, ENGINEERING);

    private PoolDept() {
    }

    /** 劇本的部門中文 → 契約 {@code dept} 的值；待認領只有房務、工務兩池，櫃檯沒有池。 */
    static String deptKey(String chinese) {
        String key = BackendChannel.dept(chinese.trim());
        if (!POOL_DEPTS.contains(key)) {
            throw new IllegalArgumentException("待認領只有房務、工務兩池，收到：" + chinese);
        }
        return key;
    }

    static boolean isManager(BackendChannel.CreatedStaff staff) {
        return "manager".equals(staff.role());
    }

    /** 部門切換上某一側的 accessibility id（{@code ManagerIds.poolDeptSwitchSide}）。 */
    static String switchSideId(String dept) {
        return DEPT_SWITCH_ID + "." + dept;
    }

    /**
     * 該部門那一組分類磚的「全部」：房務池的磚是 {@code pool.category.*}，工務池沿用
     * 工務待認領的 {@code engPool.category.*}。列表以該部門重新載入時這顆磚一定在
     * （切過去的池可能一張單都沒有，單卡不一定在），所以拿它當載入完成的訊號。
     */
    static String allChipId(String dept) {
        return ENGINEERING.equals(dept) ? "engPool.category.all.chip" : "pool.category.all.chip";
    }

    /** 單卡 id 的前綴：房務池 {@code pool.card.{房號}}，工務池 {@code engPool.item.{單號}}。 */
    static String cardPrefix(String dept) {
        return ENGINEERING.equals(dept) ? "engPool.item." : "pool.card.";
    }

    /** 劇本用來指涉單卡的鍵：房務池寫房號，工務池寫工務單號。 */
    static String itemKey(String dept, JsonNode item) {
        return ENGINEERING.equals(dept) ? item.path("ticketNo").asText() : item.path("roomNo").asText();
    }

    /**
     * 確保 App 已以這位員工的身分登入並停在待認領。主管登入後落在主管首頁
     * （{@code manager.home}），待認領要再點一次分頁，到了以部門切換出現為準；
     * 現場員工走 {@link AppiumWorld#ensureOnPool} 既有的那一條。已經在待認領上時不重複點。
     */
    static void ensureOnPool(AppiumWorld world, BackendChannel.CreatedStaff staff) {
        if (!isManager(staff)) {
            world.ensureOnPool(staff);
            return;
        }
        world.ensureLoggedInAs(staff);
        if (!world.driver().findElements(world.byTestId(DEPT_SWITCH_ID)).isEmpty()) {
            return;
        }
        world.waitForTestId("manager.home", SCREEN_TIMEOUT);
        world.waitForLabelContaining("待認領", SCREEN_TIMEOUT).click();
        world.waitForTestId(DEPT_SWITCH_ID, SCREEN_TIMEOUT);
    }

    /** App 本機記住的待認領部門；這台手機沒有任何紀錄時回 {@code null}。 */
    static String localSelection(AppiumWorld world) {
        return world.readLocalPreference(CommonGivenSteps.POOL_DEPT_PREFERENCE_KEY);
    }

    /**
     * 待認領目前是哪個部門的池：主管看手機上記住的部門（沒有紀錄視為房務），現場員工
     * 就是自己的部門。列表與分類磚的斷言都拿這個去決定要比哪一組 id、讀回要帶哪個
     * {@code dept}。
     */
    static String currentDept(AppiumWorld world) {
        BackendChannel.CreatedStaff viewer = world.loggedInStaff();
        if (!isManager(viewer)) {
            return viewer.dept();
        }
        String selected = localSelection(world);
        return selected == null ? HOUSEKEEPING : selected;
    }

    /**
     * 在已開著的主管待認領把部門切到 {@code dept}：點切換上那一側，等該部門那組分類磚
     * 出現代表列表已以該部門重新載入。已經停在該部門時不重複點。
     */
    static void switchTo(AppiumWorld world, String dept) {
        if (world.driver().findElements(world.byTestId(allChipId(dept))).isEmpty()) {
            world.tapTestId(switchSideId(dept), SCREEN_TIMEOUT);
        }
        world.waitForTestId(allChipId(dept), SCREEN_TIMEOUT);
    }
}
