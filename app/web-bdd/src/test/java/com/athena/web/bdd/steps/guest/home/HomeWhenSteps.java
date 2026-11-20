package com.athena.web.bdd.steps.guest.home;

import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.guest.GuestHomePage;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import io.cucumber.java.zh_tw.當;

/**
 * 「首頁」模組的 When 句型（{@code specs/truth/features/guest-web/首頁/dsl.md}）。
 *
 * <p>房號膠囊與即將開放的入口都不是按鈕，所以不經過「住客按」的找法，直接點它的字；點完不等任何東西，等 1 秒就往下驗。
 */
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

    /** 點該入口的整條（點它的字就是點在那一條上）；點之前記下網址與畫面文字。 */
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
