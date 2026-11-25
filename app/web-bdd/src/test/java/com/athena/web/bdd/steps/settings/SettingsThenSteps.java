package com.athena.web.bdd.steps.settings;

import com.athena.web.bdd.pages.SettingsPage;
import com.athena.web.bdd.support.WebWorld;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class SettingsThenSteps {

    private static final double ASSERT_TIMEOUT_MS = 5_000;

    private final WebWorld world;

    private final SettingsPage settings;

    public SettingsThenSteps(WebWorld world) {
        this.world = world;
        this.settings = new SettingsPage(world);
    }

    @那麼("設定頁的開關如下：")
    public void switchesAre(DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        eventually("設定頁的開關不是預期的樣子", () -> {
            int shown = settings.switches().count();
            if (shown != rows.size()) {
                return "設定頁有 " + shown + " 個開關，應該是 " + rows.size() + " 個";
            }
            for (Map<String, String> row : rows) {
                String name = row.get("開關");
                Locator toggle = settings.switchNamed(name);
                if (toggle.count() != 1) {
                    return "找不到名稱是「" + name + "」的開關";
                }
                if (toggle.isDisabled()) {
                    return "「" + name + "」還在送出中";
                }
                boolean expected = SettingsPage.isOn(row.get("狀態"));
                boolean on = "true".equals(toggle.getAttribute("aria-checked"));
                if (on != expected) {
                    return "「" + name + "」是" + (on ? "開" : "關") + "的，應該是" + row.get("狀態");
                }
            }
            return null;
        });
    }

    @那麼("後端的設定是需要房務主管查房 {string}、需要工務主管驗收 {string}")
    public void backendSettingsAre(String inspection, String verification) {
        JsonNode saved = world.backend().getJson("/desk/settings", world.staff(SettingsGivenSteps.MANAGER));
        boolean inspectionOn = saved.path("inspectionRequired").asBoolean();
        boolean verificationOn = saved.path("repairVerificationRequired").asBoolean();
        if (inspectionOn != SettingsPage.isOn(inspection) || verificationOn != SettingsPage.isOn(verification)) {
            throw new AssertionError("後端的設定是需要房務主管查房「" + (inspectionOn ? "開" : "關") + "」、需要工務主管驗收「"
                    + (verificationOn ? "開" : "關") + "」，應該是「" + inspection + "」「" + verification + "」：" + saved);
        }
    }

    private void eventually(String what, Supplier<String> problem) {
        String[] last = {null};
        try {
            world.page().waitForCondition(() -> (last[0] = problem.get()) == null,
                    new Page.WaitForConditionOptions().setTimeout(ASSERT_TIMEOUT_MS));
        } catch (TimeoutError timeout) {
            throw new AssertionError(what + "：" + last[0], timeout);
        }
    }
}
