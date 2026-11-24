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
import com.athena.web.bdd.support.WebApp;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.RequestOptions;
import io.cucumber.java.zh_tw.假設;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

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

    @假設("主管在後台換掉了 {string} 房的 QR Code")
    public void managerRotatedQr(String roomNo) {
        RotatedQr rotated = world.backend().rotateRoomQr(roomNo);
        entry.rememberOldQrToken(roomNo, rotated.oldToken());
        scenario.rememberQrToken(roomNo, rotated.newToken());
    }

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

    @假設("這支手機上的住客憑證已經失效")
    public void guestCredentialRevoked() {
        world.backend().revokeGuestSession(currentSession());
    }

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

    @假設("這支手機短時間內多次掃碼失敗，後端暫時不受理它的掃碼")
    public void scanRateLimited() {
        // 從這支手機（同一個瀏覽器、經同一個開發伺服器轉送，後端看到的來源相同）連續用不存在的代碼換 session，
        // 直到後端的掃碼限流器開始回 429；之後這個來源的換 session 都會被擋。
        APIRequestContext phoneRequests = world.page().request();
        RequestOptions badScan = RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData(Map.of("token", "bdd-no-such-qr-" + UUID.randomUUID()));
        for (int attempt = 1; attempt <= MAX_SCAN_ATTEMPTS_TO_RATE_LIMIT; attempt++) {
            APIResponse response = phoneRequests.post(WebApp.url(Network.GUEST_SCAN.path()), badScan);
            if (response.status() == 429) {
                return;
            }
        }
        throw new IllegalStateException("連續用不存在的代碼換 session " + MAX_SCAN_ATTEMPTS_TO_RATE_LIMIT
                + " 次，後端的掃碼限流器還是沒有回 429");
    }

    private static final int MAX_SCAN_ATTEMPTS_TO_RATE_LIMIT = 50;

    @假設("後端暫時出錯，任何掃碼都失敗")
    public void scanFails() {
        world.network().respondWithError(world.page(), Network.GUEST_SCAN, 500, "internal_error",
                "後端暫時出錯", Map.of());
    }

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

    @假設("{string} 已在這支手機上登入過櫃檯 Web，登入狀態還在")
    public void staffLoggedInOnPhone(String staffName) {
        Page desk = world.page();
        new ShellFrame(world).loginAndOpen(world.staff(staffName), "總覽看板");
        entry.rememberDeskTab(desk);
        world.openTab();
    }

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
