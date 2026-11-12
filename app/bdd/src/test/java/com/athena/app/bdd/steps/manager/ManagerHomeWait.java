package com.athena.app.bdd.steps.manager;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;

import java.time.Duration;
import java.util.List;

public final class ManagerHomeWait {

    private ManagerHomeWait() {
    }

    public static List<String> queuesFor(BackendChannel.CreatedStaff manager) {
        if ("engineering".equals(manager.dept())) {
            return List.of("verify");
        }
        return List.of("inspection", "verify");
    }

    public static void await(AppiumWorld world, String staffName, Duration timeout) {
        BackendChannel.CreatedStaff manager = world.staff(staffName);
        for (String queue : queuesFor(manager)) {
            world.waitForTestId("manager.home.tile." + queue + ".count", timeout);
        }
        world.waitForTestId("manager.home.mine", timeout);
    }

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
