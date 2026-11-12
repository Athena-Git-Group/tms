package com.athena.app.bdd.steps.manager;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;

import java.time.Duration;
import java.util.List;

/**
 * 等主管首頁載好：該主管種類的每一格數字與「我名下的單」區都出現。
 *
 * <p>主管首頁有幾格要看主管種類：房務主管有待查房與待驗收兩格，工務主管只有待驗收一條寬磚
 * （id 同樣是 {@code manager.home.tile.verify}）。種類照後端員工資料的 role 與 dept 判定，
 * 與 App 的 {@code managerKindOf} 同一條口徑：role 是 manager 且 dept 是 engineering 才是工務主管，
 * 其他主管都是房務主管。各句不要自己寫死要等哪幾格，否則換一種主管就會卡在一個永遠不會出現的格子上。
 */
public final class ManagerHomeWait {

    private ManagerHomeWait() {
    }

    /** 這位主管的首頁有哪幾格；值與 {@code manager.home.tile.{queue}} 的 queue 段相同。 */
    public static List<String> queuesFor(BackendChannel.CreatedStaff manager) {
        if ("engineering".equals(manager.dept())) {
            return List.of("verify");
        }
        return List.of("inspection", "verify");
    }

    /** 等這位主管首頁上屬於他的每一格數字，以及「我名下的單」區出現。不負責登入與導頁。 */
    public static void await(AppiumWorld world, String staffName, Duration timeout) {
        BackendChannel.CreatedStaff manager = world.staff(staffName);
        for (String queue : queuesFor(manager)) {
            world.waitForTestId("manager.home.tile." + queue + ".count", timeout);
        }
        world.waitForTestId("manager.home.mine", timeout);
    }

    /**
     * 同 {@link #await}，另外確認不屬於這種主管的格子不在畫面上（工務主管沒有待查房格）。
     * 給必查「依主管種類顯示格子」的 Then 句用；Given 只需要等首頁載好，用 {@link #await}。
     */
    public static void awaitExactly(AppiumWorld world, String staffName, Duration timeout) {
        await(world, staffName, timeout);
        List<String> mine = queuesFor(world.staff(staffName));
        for (String queue : List.of("inspection", "verify")) {
            if (!mine.contains(queue)
                    && !world.driver().findElements(world.byTestId("manager.home.tile." + queue)).isEmpty()) {
                throw new AssertionError("「" + staffName + "」的主管首頁只該有 " + mine
                        + "，卻找得到 manager.home.tile." + queue);
            }
        }
    }
}
