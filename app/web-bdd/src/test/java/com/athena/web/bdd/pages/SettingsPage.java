package com.athena.web.bdd.pages;

import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import java.util.Map;

public class SettingsPage extends PageObject {

    public static final Map<String, String> FIELD_BY_SWITCH = Map.of(
            "需要房務主管查房", "inspectionRequired",
            "需要工務主管驗收", "repairVerificationRequired");

    public SettingsPage(WebWorld world) {
        super(world);
    }

    public static String fieldOf(String switchName) {
        String field = FIELD_BY_SWITCH.get(switchName.strip());
        if (field == null) {
            throw new IllegalArgumentException("開關只有 " + FIELD_BY_SWITCH.keySet() + "，收到「" + switchName + "」");
        }
        return field;
    }

    public static boolean isOn(String state) {
        return switch (state.strip()) {
            case "開" -> true;
            case "關" -> false;
            default -> throw new IllegalArgumentException("開關的狀態只有開、關，收到「" + state + "」");
        };
    }

    public Locator switches() {
        return page().locator("[data-page='settings']").getByRole(AriaRole.SWITCH);
    }

    public Locator switchNamed(String name) {
        return page().locator("[data-page='settings']").getByRole(AriaRole.SWITCH,
                new Locator.GetByRoleOptions().setName(name.strip()).setExact(true));
    }

    public void waitUntilLoaded() {
        page().locator("[data-page='settings']:not([aria-busy='true'])").waitFor();
    }
}
