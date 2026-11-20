package com.athena.web.bdd.steps.guest.requests;

import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.GuestHomePage;
import com.athena.web.bdd.pages.guest.SupplyPage;
import com.athena.web.bdd.support.GuestPhone;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.GuestScreen;
import com.athena.web.bdd.support.GuestStay;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.cucumber.java.zh_tw.假設;

/**
 * 「我的需求」模組的 Given 句型（{@code specs/truth/features/guest-web/我的需求/dsl.md}）。
 *
 * <p>這兩句真的經畫面在備品補充送出一項，每一筆都經過送出需求的限流替身，算在這間房 3 筆的預算裡。
 */
public class RequestsGivenSteps {

    private static final String VIEW_PROGRESS = "查看進度";

    private final WebWorld world;

    private final GuestScenario scenario;

    private final GuestPhone phone;

    private final GuestStay stay;

    private final GuestFrame frame;

    private final GuestHomePage home;

    private final SupplyPage supply;

    public RequestsGivenSteps(WebWorld world, GuestScenario scenario, GuestPhone phone, GuestStay stay) {
        this.world = world;
        this.scenario = scenario;
        this.phone = phone;
        this.stay = stay;
        this.frame = new GuestFrame(world);
        this.home = new GuestHomePage(world);
        this.supply = new SupplyPage(world);
    }

    /** 在另一支手機開備品補充頁、把這一項調到 1、送出，等它的成功頁畫完；記下建立的需求。之後的「住客」仍指這支手機。 */
    @假設("另一支手機已在備品補充送出 {string}")
    public void secondPhoneSubmitted(String item) {
        String roomNo = scenario.phoneRoom();
        Page first = world.page();
        world.switchTo(scenario.secondPhone());
        try {
            submitOne(roomNo, item);
        } finally {
            world.switchTo(first);
            scenario.rememberPhoneRoom(roomNo);
        }
    }

    /**
     * 從這個入口進這張需求的進度頁：首頁的進度卡是開首頁按卡上的「查看」；我的需求清單是開清單點這一列；
     * 送出成功頁是在備品補充送出這一項（標題就是項目名稱）再按「查看進度」。等進度頁畫完。
     */
    @假設("{string} 房的住客從 {string} 進了 {string} 的進度頁")
    public void enteredRequestFrom(String roomNo, String entry, String title) {
        switch (entry.strip()) {
            case "首頁的進度卡" -> {
                phone.openHome(roomNo);
                frame.settleAfter(() -> tap(home.progressCardView()));
            }
            case "我的需求清單" -> {
                phone.ensureHome(roomNo);
                phone.openScreen(roomNo, GuestScreen.REQUESTS);
                frame.press(title);
            }
            case "送出成功頁" -> {
                phone.ensureHome(roomNo);
                submitOne(roomNo, title);
                frame.press(VIEW_PROGRESS);
            }
            default -> throw new IllegalArgumentException(
                    "入口只有首頁的進度卡、我的需求清單、送出成功頁，收到「" + entry + "」");
        }
        phone.waitUntilRequest(roomNo, title);
    }

    /** 在目前的分頁開備品補充頁、把這一項調到 1、按頁底的送出，等成功頁畫完，記下建立的需求。 */
    private void submitOne(String roomNo, String item) {
        phone.openScreen(roomNo, GuestScreen.SUPPLY);
        frame.settleAfter(() -> tap(supply.increase(item)));
        frame.press(SupplyPage.submitLabel(1));
        phone.waitUntilShown(roomNo, GuestScreen.SENT);
        scenario.rememberRequest(item.strip(), stay.newest(roomNo, item).id());
    }

    private void tap(Locator target) {
        if (world.profile().touch()) {
            target.tap();
        } else {
            target.click();
        }
    }
}
