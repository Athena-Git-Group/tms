package com.athena.web.bdd.steps.guest.entry;

import com.athena.bddsupport.BackendChannel.RotatedQr;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.guest.GuestEntryPage;
import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.support.GuestApp;
import com.athena.web.bdd.support.GuestPhone;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.GuestStay;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Page;
import io.cucumber.java.zh_tw.假設;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 「進入層」模組的 Given 句型（{@code specs/truth/features/guest-web/進入層/dsl.md}）。
 *
 * <p>「掃碼被限流」與「後端出錯」都在這個分頁的換 session 請求上用網路攔截做出來（測試策略：不靠真的限流器）；
 * 「連不上」用 {@link Network#cutOffBackend(Page)}，它比換 session 的攔截後裝，所以兩者同時在時是連不上。
 */
public class EntryGivenSteps {

    private final WebWorld world;

    private final GuestScenario scenario;

    private final GuestPhone phone;

    private final GuestStay stay;

    private final EntryScenario entry;

    private final GuestFrame frame;

    private final GuestEntryPage entryPage;

    public EntryGivenSteps(WebWorld world, GuestScenario scenario, GuestPhone phone, GuestStay stay,
            EntryScenario entry) {
        this.world = world;
        this.scenario = scenario;
        this.phone = phone;
        this.stay = stay;
        this.entry = entry;
        this.frame = new GuestFrame(world);
        this.entryPage = new GuestEntryPage(world);
    }

    /**
     * 以主管身分換這間房的 QR；後端把舊代碼作廢、撤銷這間房所有住客 session。記下新舊代碼：之後「QR Code 的網址」
     * 用新的、「舊代碼的網址」用舊的。已經開著的畫面不通知。
     */
    @假設("主管在後台換掉了 {string} 房的 QR Code")
    public void managerRotatedQr(String roomNo) {
        RotatedQr rotated = world.backend().rotateRoomQr(roomNo);
        entry.rememberOldQrToken(roomNo, rotated.oldToken());
        scenario.rememberQrToken(roomNo, rotated.newToken());
    }

    /** 清掉這個分頁儲存空間裡的住客憑證，網址不變；後端那邊的 session 仍有效。 */
    @假設("這支手機上的住客憑證已經不見")
    public void guestCredentialGone() {
        Object removed = world.page().evaluate("""
                prefix => {
                  const keys = [];
                  for (let i = 0; i < sessionStorage.length; i++) {
                    const key = sessionStorage.key(i);
                    if (key.startsWith(prefix)) keys.push(key);
                  }
                  keys.forEach(key => sessionStorage.removeItem(key));
                  return keys.length;
                }
                """, GuestPhone.SESSION_KEY_PREFIX);
        if (((Number) removed).intValue() == 0) {
            throw new IllegalStateException("這個分頁的儲存空間本來就沒有住客憑證，沒有東西可以清");
        }
    }

    /** 把這個分頁手上那枚 session 在後端標成撤銷；分頁裡的憑證還在，網址裡的代碼仍換得到新 session。 */
    @假設("這支手機上的住客憑證已經失效")
    public void guestCredentialRevoked() {
        world.backend().revokeGuestSession(currentSession());
    }

    /** 把後端與瀏覽器的時鐘撥到這筆訂房的退房時刻之後；session 過期，代碼也換不到新的。 */
    @假設("{string} 房這筆訂房已經過了退房時刻")
    public void pastCheckout(String roomNo) {
        UUID reservation = stay.reservationId(roomNo)
                .orElseThrow(() -> new IllegalStateException(roomNo + " 房現在沒有人住，沒有退房時刻可以過"));
        Object checkout = world.backend().queryRows(
                "SELECT checkout_at FROM reservations_cache WHERE tenant_id = ? AND id = ?",
                world.defaults().tenantId(), reservation).get(0).get("checkout_at");
        OffsetDateTime checkoutAt = checkout instanceof OffsetDateTime odt ? odt
                : ((java.sql.Timestamp) checkout).toInstant().atOffset(world.clocks().now().getOffset());
        world.clocks().moveTo(checkoutAt.plusMinutes(1));
    }

    /**
     * 規格允許的退路：真的讓後端數到失敗門檻要用系統時鐘、而且整台測試機共用，所以改以網路攔截讓這個分頁的換 session
     * 都回 {@code 429}。
     */
    @假設("這支手機短時間內多次掃碼失敗，後端暫時不受理它的掃碼")
    public void scanRateLimited() {
        world.network().respondWithError(world.page(), Network.GUEST_SCAN, 429, "rate_limited",
                "掃碼失敗太多次，請稍後再試", Map.of());
    }

    /** 這個分頁的換 session 請求都回 {@code 500}，其他請求不動，直到「後端已恢復」。 */
    @假設("後端暫時出錯，任何掃碼都失敗")
    public void scanFails() {
        world.network().respondWithError(world.page(), Network.GUEST_SCAN, 500, "internal_error",
                "後端暫時出錯", Map.of());
    }

    /** 先讓這個分頁連不上後端，再開這間房的網址，等重試狀態畫完；網路維持斷線，由之後的句子放行。 */
    @假設("{string} 房的住客的畫面停在重試狀態")
    public void guestOnRetry(String roomNo) {
        world.network().cutOffBackend(world.page());
        phone.open(roomNo, GuestApp.HOME);
        waitUntil("畫面沒有停在重試狀態", entryPage::retryShown);
    }

    @假設("這支手機已恢復連線")
    public void phoneBackOnline() {
        world.network().restoreBackend(world.page());
    }

    @假設("後端已恢復")
    public void backendRecovered() {
        world.network().passThrough(world.page(), Network.GUEST_SCAN);
    }

    /** 先開首頁、讓憑證失效、讓換 session 出錯，再開我的需求清單的網址；等重試狀態畫完，網址仍是清單那一頁。 */
    @假設("{string} 房的住客打開清單時因為後端出錯停在重試狀態")
    public void requestsStuckOnRetry(String roomNo) {
        phone.ensureHome(roomNo);
        guestCredentialRevoked();
        scanFails();
        phone.open(roomNo, GuestApp.REQUESTS);
        String requestsPath = GuestApp.path(phone.qrToken(roomNo), GuestApp.REQUESTS);
        waitUntil("打開清單之後沒有停在重試狀態，或網址不是清單那一頁",
                () -> entryPage.retryShown() && frame.path().equals(requestsPath));
    }

    /**
     * 在這個瀏覽器環境的一個分頁以該員工登入櫃檯 Web、停在總覽看板，登入憑證留在那個分頁；
     * 之後的住客用同一個瀏覽器環境的新分頁。
     */
    @假設("{string} 已在這支手機上登入過櫃檯 Web，登入狀態還在")
    public void staffLoggedInOnPhone(String staffName) {
        Page desk = world.page();
        new ShellFrame(world).loginAndOpen(world.staff(staffName), "總覽看板");
        entry.rememberDeskTab(desk);
        world.openTab();
    }

    /** 在住客的分頁（不是櫃檯 Web 那個分頁）用代碼開這間房的首頁。 */
    @假設("{string} 已在這支手機上打開過 {string} 房的住客頁")
    public void staffOpenedGuestPage(String staffName, String roomNo) {
        if (entry.deskTab().filter(desk -> desk == world.page()).isPresent()) {
            world.openTab();
        }
        phone.openHome(roomNo);
    }

    private String currentSession() {
        Page page = world.page();
        phone.rememberSession(scenario.phoneRoom());
        return scenario.session(page).orElseThrow(() -> new IllegalStateException(
                "這個分頁沒有住客憑證：住客還沒用代碼打開過這間房的網址"));
    }

    private void waitUntil(String failure, Supplier<Boolean> condition) {
        Page page = world.page();
        frame.waitAtMost(page, condition);
        if (!condition.get()) {
            throw new AssertionError(failure + "：網址 " + page.url() + "，畫面上的文字是 " + entryPage.visibleLines());
        }
    }
}
