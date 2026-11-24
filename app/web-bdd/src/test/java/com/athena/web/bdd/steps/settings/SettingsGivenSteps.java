package com.athena.web.bdd.steps.settings;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.athena.web.bdd.pages.SettingsPage;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Response;
import io.cucumber.java.zh_tw.假設;
import java.util.Map;

public class SettingsGivenSteps {

    static final String MANAGER = "張主管";

    private final WebWorld world;

    private final ShellFrame shell;

    private final SettingsPage settings;

    public SettingsGivenSteps(WebWorld world) {
        this.world = world;
        this.shell = new ShellFrame(world);
        this.settings = new SettingsPage(world);
    }

    @假設("後端的設定已把 {string} 關掉")
    public void backendSwitchedOff(String switchName) {
        world.backend().patchDeskSettings(world.staff(MANAGER), Map.of(SettingsPage.fieldOf(switchName), false));
    }

    @假設("{string} 已在設定頁關掉 {string}")
    public void switchedOffOnPage(String staffName, String switchName) {
        Locator toggle = settings.switchNamed(switchName);
        assertThat(toggle).hasAttribute("aria-checked", "true");
        world.page().waitForResponse(SettingsGivenSteps::isSavedSettings, () -> shell.press(toggle));
        assertThat(toggle).hasAttribute("aria-checked", "false");
        assertThat(toggle).isEnabled();
    }

    private static boolean isSavedSettings(Response response) {
        Network.Api update = Network.DESK_SETTINGS_UPDATE;
        return update.method().equals(response.request().method())
                && response.url().split("[?#]")[0].endsWith(update.path()) && response.ok();
    }
}
