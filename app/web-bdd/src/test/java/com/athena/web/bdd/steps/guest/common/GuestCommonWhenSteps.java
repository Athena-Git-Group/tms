package com.athena.web.bdd.steps.guest.common;

import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.RepairPage;
import com.athena.web.bdd.support.GuestApp;
import com.athena.web.bdd.support.GuestPhone;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.GuestScreen;
import com.athena.web.bdd.support.WebWorld;
import io.cucumber.java.zh_tw.當;

/** 住客掃碼頁跨模組共用的 When 句型（{@code specs/truth/features/guest-web/dsl.md}）。 */
public class GuestCommonWhenSteps {

    private final GuestScenario scenario;

    private final GuestPhone phone;

    private final GuestFrame frame;

    public GuestCommonWhenSteps(WebWorld world, GuestScenario scenario, GuestPhone phone) {
        this.scenario = scenario;
        this.phone = phone;
        this.frame = new GuestFrame(world);
    }

    /**
     * 用這間房目前有效的代碼開 {@code /g/{代碼}}，不輸入也不點任何東西；等首頁、無法使用頁或重試狀態其中之一畫完。
     * 記下這段期間測試替住客點按與輸入的次數，給「不用輸入任何東西」的結果句比對。
     */
    @當("住客打開 {string} 房 QR Code 的網址")
    public void guestOpensQrUrl(String roomNo) {
        int tapsBefore = scenario.taps();
        int inputsBefore = scenario.inputs();
        phone.open(roomNo, GuestApp.HOME);
        phone.waitUntilAnyScreen();
        phone.rememberSession(roomNo);
        scenario.rememberOpening(scenario.taps() - tapsBefore, scenario.inputs() - inputsBefore);
    }

    /** 在最上面那一層（對話框或選單、提示框、頁面）點名稱相符的那一個；等這一下引發的請求與畫面變化都結束。 */
    @當("住客按 {string}")
    public void guestPresses(String name) {
        frame.press(RepairPage.pressableIn(frame.language(), name));
        scenario.countTap();
    }

    /** 直接開該頁的網址（帶代碼），等該頁畫完、資料讀完。 */
    @當("住客打開 {string}")
    public void guestOpensScreen(String pageName) {
        GuestScreen screen = GuestScreen.ofTerm(pageName);
        if (!screen.openable() || screen == GuestScreen.HOME) {
            throw new IllegalArgumentException("「住客打開」只接受備品補充頁、設備報修頁、我的需求清單，收到「" + pageName + "」");
        }
        phone.openScreen(scenario.phoneRoom(), screen);
    }

    /** 直接開這張需求的進度頁網址，等進度頁畫完、這張需求的資料讀完。 */
    @當("住客打開 {string} 的進度頁")
    public void guestOpensRequest(String title) {
        phone.openRequest(scenario.phoneRoom(), title);
    }

    @當("住客重新整理網頁")
    public void guestReloads() {
        phone.reload();
    }
}
