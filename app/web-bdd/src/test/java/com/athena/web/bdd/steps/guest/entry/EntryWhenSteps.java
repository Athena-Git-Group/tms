package com.athena.web.bdd.steps.guest.entry;

import com.athena.web.bdd.pages.LoginPage;
import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.support.GuestApp;
import com.athena.web.bdd.support.GuestPhone;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.WebApp;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.zh_tw.當;
import java.security.SecureRandom;

public class EntryWhenSteps {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final int QR_TOKEN_LENGTH = 22;

    private static final double DESK_LOAD_TIMEOUT_MS = 10_000;

    private final WebWorld world;

    private final GuestScenario scenario;

    private final GuestPhone phone;

    private final EntryScenario entry;

    private final GuestFrame frame;

    public EntryWhenSteps(WebWorld world, GuestScenario scenario, GuestPhone phone, EntryScenario entry) {
        this.world = world;
        this.scenario = scenario;
        this.phone = phone;
        this.entry = entry;
        this.frame = new GuestFrame(world);
    }

    @當("住客打開 {string} 房舊代碼的網址")
    public void guestOpensOldQrUrl(String roomNo) {
        scenario.rememberPhoneRoom(roomNo);
        openGuestUrl(GuestApp.url(entry.oldQrToken(roomNo), GuestApp.HOME));
    }

    @當("住客打開一個不存在的代碼的網址")
    public void guestOpensUnknownQrUrl() {
        SecureRandom random = new SecureRandom();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < QR_TOKEN_LENGTH; i++) {
            token.append(BASE62.charAt(random.nextInt(BASE62.length())));
        }
        openGuestUrl(GuestApp.url(token.toString(), GuestApp.HOME));
    }

    @當("住客把網頁關掉，再從歷史記錄打開同一個網址")
    public void guestReopensFromHistory() {
        Page closing = world.page();
        String url = closing.url();
        world.openTab();
        closing.close();
        openGuestUrl(url);
    }

    @當("住客在同一支手機上打開櫃檯 Web 的網址")
    public void openDeskWeb() {
        Page desk = entry.deskTab().orElseGet(world::openTab);
        world.switchTo(desk);
        entry.rememberDeskTab(desk);
        desk.navigate(WebApp.url(WebApp.underHotel(world.defaults().tenantCode(), "/")));
        LoginPage login = new LoginPage(world);
        try {
            desk.waitForCondition(() -> login.loginButton().isVisible() || boardTitleShown(desk),
                    new Page.WaitForConditionOptions().setTimeout(DESK_LOAD_TIMEOUT_MS));
        } catch (PlaywrightException notShown) {
        }
    }

    private void openGuestUrl(String url) {
        frame.settleAfter(() -> world.page().navigate(url));
        phone.waitUntilAnyScreen();
    }

    private static boolean boardTitleShown(Page page) {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("總覽看板").setExact(true).setLevel(1))
                .isVisible();
    }
}
