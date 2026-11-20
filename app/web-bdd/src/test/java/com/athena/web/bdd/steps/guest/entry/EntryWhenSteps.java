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

/** 「進入層」模組的 When 句型（{@code specs/truth/features/guest-web/進入層/dsl.md}）。 */
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

    /** 用換 QR 之前的舊代碼開 {@code /g/{舊代碼}}，等任何一種住客畫面畫完。 */
    @當("住客打開 {string} 房舊代碼的網址")
    public void guestOpensOldQrUrl(String roomNo) {
        scenario.rememberPhoneRoom(roomNo);
        openGuestUrl(GuestApp.url(entry.oldQrToken(roomNo), GuestApp.HOME));
    }

    /** 開一個 22 個 base62 字元、後端查無的代碼。 */
    @當("住客打開一個不存在的代碼的網址")
    public void guestOpensUnknownQrUrl() {
        SecureRandom random = new SecureRandom();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < QR_TOKEN_LENGTH; i++) {
            token.append(BASE62.charAt(random.nextInt(BASE62.length())));
        }
        openGuestUrl(GuestApp.url(token.toString(), GuestApp.HOME));
    }

    /** 記下目前網址，關掉這個分頁，在同一個瀏覽器環境開新分頁（沒有住客憑證）再開同一個網址。 */
    @當("住客把網頁關掉，再從歷史記錄打開同一個網址")
    public void guestReopensFromHistory() {
        Page closing = world.page();
        String url = closing.url();
        world.openTab();
        closing.close();
        openGuestUrl(url);
    }

    /**
     * 在同一個瀏覽器環境開預設飯店的櫃檯 Web（{@code /{飯店代碼}/}），等登入頁或總覽看板畫完。
     *
     * <p>員工登入櫃檯 Web 的那個分頁還在時，網址開在那個分頁上；沒有時開一個新分頁。
     * 櫃檯 Web 的登入狀態只存在登入的那個分頁裡，別的分頁讀不到，所以要在那個分頁上才看得出住客頁有沒有動到它。
     */
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
            // 畫的是什麼由結果句說明。
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
