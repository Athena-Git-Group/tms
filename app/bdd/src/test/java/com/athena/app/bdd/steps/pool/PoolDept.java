package com.athena.app.bdd.steps.pool;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import com.athena.app.bdd.steps.common.CommonGivenSteps;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.Set;

final class PoolDept {

    static final String HOUSEKEEPING = "housekeeping";
    static final String ENGINEERING = "engineering";

    static final String DEPT_SWITCH_ID = "pool.dept-switch";

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    private static final Set<String> POOL_DEPTS = Set.of(HOUSEKEEPING, ENGINEERING);

    private PoolDept() {
    }

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

    static String switchSideId(String dept) {
        return DEPT_SWITCH_ID + "." + dept;
    }

    static String allChipId(String dept) {
        return ENGINEERING.equals(dept) ? "engPool.category.all.chip" : "pool.category.all.chip";
    }

    static String cardPrefix(String dept) {
        return ENGINEERING.equals(dept) ? "engPool.item." : "pool.card.";
    }

    static String itemKey(String dept, JsonNode item) {
        return ENGINEERING.equals(dept) ? item.path("ticketNo").asText() : item.path("roomNo").asText();
    }

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

    static String localSelection(AppiumWorld world) {
        return world.readLocalPreference(CommonGivenSteps.POOL_DEPT_PREFERENCE_KEY);
    }

    static String currentDept(AppiumWorld world) {
        BackendChannel.CreatedStaff viewer = world.loggedInStaff();
        if (!isManager(viewer)) {
            return viewer.dept();
        }
        String selected = localSelection(world);
        return selected == null ? HOUSEKEEPING : selected;
    }

    static void switchTo(AppiumWorld world, String dept) {
        if (world.driver().findElements(world.byTestId(allChipId(dept))).isEmpty()) {
            world.tapTestId(switchSideId(dept), SCREEN_TIMEOUT);
        }
        world.waitForTestId(allChipId(dept), SCREEN_TIMEOUT);
    }
}
