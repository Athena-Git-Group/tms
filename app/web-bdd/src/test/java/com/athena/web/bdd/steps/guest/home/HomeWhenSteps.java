package com.athena.web.bdd.steps.guest.home;

import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.guest.GuestHomePage;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import io.cucumber.java.zh_tw.當;

public class HomeWhenSteps {

    private static final double AFTER_TAP_MS = 1_000;

    private final WebWorld world;

    private final GuestScenario scenario;

    private final HomeScenario home;

    private final GuestHomePage homePage;

    public HomeWhenSteps(WebWorld world, GuestScenario scenario, HomeScenario home) {
        this.world = world;
        this.scenario = scenario;
        this.home = home;
        this.homePage = new GuestHomePage(world);
    }

    @當("住客點房號膠囊")
    public void guestTapsRoomCapsule() {
        tapAndWait(homePage.roomCapsule(scenario.phoneRoom()));
    }

    @當("住客點即將開放的入口 {string}")
    public void guestTapsComingSoon(String entry) {
        Locator text = homePage.main().getByText(entry.strip(), new Locator.GetByTextOptions().setExact(true))
                .filter(new Locator.FilterOptions().setVisible(true)).first();
        tapAndWait(text);
    }

    private void tapAndWait(Locator target) {
        home.rememberBeforeTap(world.page().url(), ShellFrame.linesOf(world.page().locator("body").innerText()));
        if (world.profile().touch()) {
            target.tap();
        } else {
            target.click();
        }
        scenario.countTap();
        world.page().waitForTimeout(AFTER_TAP_MS);
    }
}
