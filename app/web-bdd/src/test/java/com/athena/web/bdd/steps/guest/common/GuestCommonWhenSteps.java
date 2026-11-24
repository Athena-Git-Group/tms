package com.athena.web.bdd.steps.guest.common;

import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.RepairPage;
import com.athena.web.bdd.support.GuestApp;
import com.athena.web.bdd.support.GuestPhone;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.GuestScreen;
import com.athena.web.bdd.support.WebWorld;
import io.cucumber.java.zh_tw.當;

public class GuestCommonWhenSteps {

    private final GuestScenario scenario;

    private final GuestPhone phone;

    private final GuestFrame frame;

    public GuestCommonWhenSteps(WebWorld world, GuestScenario scenario, GuestPhone phone) {
        this.scenario = scenario;
        this.phone = phone;
        this.frame = new GuestFrame(world);
    }

    @當("住客打開 {string} 房 QR Code 的網址")
    public void guestOpensQrUrl(String roomNo) {
        int tapsBefore = scenario.taps();
        int inputsBefore = scenario.inputs();
        phone.open(roomNo, GuestApp.HOME);
        phone.waitUntilAnyScreen();
        phone.rememberSession(roomNo);
        scenario.rememberOpening(scenario.taps() - tapsBefore, scenario.inputs() - inputsBefore);
    }

    @當("住客按 {string}")
    public void guestPresses(String name) {
        frame.press(RepairPage.pressableIn(frame.language(), name));
        scenario.countTap();
    }

    @當("住客打開 {string}")
    public void guestOpensScreen(String pageName) {
        GuestScreen screen = GuestScreen.ofTerm(pageName);
        if (!screen.openable() || screen == GuestScreen.HOME) {
            throw new IllegalArgumentException("「住客打開」只接受備品補充頁、設備報修頁、我的需求清單，收到「" + pageName + "」");
        }
        phone.openScreen(scenario.phoneRoom(), screen);
    }

    @當("住客打開 {string} 的進度頁")
    public void guestOpensRequest(String title) {
        phone.openRequest(scenario.phoneRoom(), title);
    }

    @當("住客重新整理網頁")
    public void guestReloads() {
        phone.reload();
    }
}
