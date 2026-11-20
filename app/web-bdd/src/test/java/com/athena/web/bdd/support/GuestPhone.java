package com.athena.web.bdd.support;

import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.guest.GuestFrame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 住客的手機：用房間的 QR 代碼開住客頁、等畫面畫完。句子只寫房號、頁名與需求標題，換成網址都經過這裡。
 *
 * <p>「畫完」只看和語言無關的結構：網址是那一頁、首頁的 {@code main} 或子頁標題列的 {@code h1} 出現、
 * 畫面上沒有標成載入中（{@code aria-busy="true"}）的東西，而且開頁引發的請求都回來了。
 * 頁面的字對不對由結果句判斷，介面換成別的語言時這裡一樣等得到。
 *
 * <p>規格裡的「住客」永遠指第一支手機，所以開另一支手機的方法在回傳前把目前的分頁換回第一支。
 */
public final class GuestPhone {

    /** 產品把住客 session 存在分頁 {@code sessionStorage} 的鍵的前綴，後面接 QR 代碼。 */
    public static final String SESSION_KEY_PREFIX = "tms.guest.session.";

    private final WebWorld world;

    private final GuestScenario scenario;

    private final GuestFrame frame;

    public GuestPhone(WebWorld world, GuestScenario scenario) {
        this.world = world;
        this.scenario = scenario;
        this.frame = new GuestFrame(world);
    }

    /**
     * 這間房現在的 QR 代碼。這個場景第一次問時以主管身分向後端讀（後端沒有有效代碼時會在讀的當下發一枚），
     * 之後用記下的；換過 QR 的場景要自己用 {@link GuestScenario#rememberQrToken} 蓋掉。
     */
    public String qrToken(String roomNo) {
        try {
            return scenario.qrToken(roomNo);
        } catch (IllegalStateException notYet) {
            String token = world.backend().roomQrToken(roomNo);
            scenario.rememberQrToken(roomNo, token);
            return token;
        }
    }

    /** 這支手機現在是不是開著這間房的住客頁（任何一頁）。 */
    public boolean onRoom(String roomNo) {
        return world.hasPage() && frame.path().startsWith(GuestApp.path(qrToken(roomNo), GuestApp.HOME));
    }

    /** 在目前的分頁開這間房住客頁的某一段（{@link GuestApp} 的常數），等開頁引發的請求都回來；不檢查畫的是什麼。 */
    public void open(String roomNo, String sub) {
        String url = GuestApp.url(qrToken(roomNo), sub);
        scenario.rememberPhoneRoom(roomNo);
        frame.settleAfter(() -> world.page().navigate(url));
    }

    /** 開這間房的首頁，等首頁畫完，記下這個分頁換到的住客 session。 */
    public void openHome(String roomNo) {
        open(roomNo, GuestApp.HOME);
        waitUntilShown(roomNo, GuestScreen.HOME);
        rememberSession(roomNo);
    }

    /** 這支手機還沒開著這間房的住客頁時先開首頁；已經開著就不動。 */
    public void ensureHome(String roomNo) {
        if (!onRoom(roomNo)) {
            openHome(roomNo);
        }
        scenario.rememberPhoneRoom(roomNo);
    }

    /** 直接開某一頁的網址（{@link GuestScreen#openable()} 的頁），等它畫完。 */
    public void openScreen(String roomNo, GuestScreen screen) {
        if (!screen.openable()) {
            throw new IllegalArgumentException("「" + screen.term() + "」沒有可以直接開的網址");
        }
        open(roomNo, screen.sub().orElseThrow());
        waitUntilShown(roomNo, screen);
    }

    /** 直接開這張需求的進度頁網址，等進度頁畫完。這樣進來沒有來處的紀錄，返回算從清單進來的。 */
    public void openRequest(String roomNo, String title) {
        open(roomNo, GuestApp.request(scenario.request(title)));
        waitUntilRequest(roomNo, title);
    }

    /** 等這張需求的進度頁畫完：網址是它的進度頁、標題列與第一張卡的標題都出現、沒有載入中。 */
    public void waitUntilRequest(String roomNo, String title) {
        String path = GuestApp.path(qrToken(roomNo), GuestApp.request(scenario.request(title)));
        waitUntil("「" + title + "」的進度頁沒有畫完", () -> frame.path().equals(path) && subpageDrawn()
                && mainHeading(2).count() > 0);
    }

    /** 整頁重新載入，網址不變；等畫面畫完（任何一種住客畫面都算）。 */
    public void reload() {
        frame.settleAfter(() -> world.page().reload());
        waitUntilAnyScreen();
    }

    /** 等首頁、子頁、送出成功頁、無法使用頁或重試狀態任何一種畫完：沒有載入中，而且有 {@code main} 或標題。 */
    public void waitUntilAnyScreen() {
        Page page = world.page();
        waitUntil("住客頁一直沒有畫完", () -> !frame.loading()
                && (page.getByRole(AriaRole.MAIN).count() > 0 || page.getByRole(AriaRole.HEADING).count() > 0));
    }

    /** 等這間房的某一頁畫完，判斷方式見類別說明。 */
    public void waitUntilShown(String roomNo, GuestScreen screen) {
        String token = qrToken(roomNo);
        switch (screen) {
            case HOME -> waitUntil("首頁沒有畫完", () -> frame.path().equals(GuestApp.path(token, GuestApp.HOME))
                    && world.page().getByRole(AriaRole.MAIN).count() > 0 && !frame.loading());
            case SUPPLY, REPAIR, REQUESTS, SENT -> {
                String path = GuestApp.path(token, screen.sub().orElseThrow());
                waitUntil(screen.term() + "沒有畫完", () -> frame.path().equals(path)
                        && (screen == GuestScreen.SENT ? !frame.loading() : subpageDrawn()));
            }
            case UNAVAILABLE, RETRY -> waitUntilAnyScreen();
        }
    }

    /** 等某一頁資訊頁畫完：網址在這間房的 {@code guide/} 底下、標題列的頁標題出現。 */
    public void waitUntilInfoPage(String roomNo) {
        String prefix = GuestApp.path(qrToken(roomNo), "guide/");
        waitUntil("資訊頁沒有畫完", () -> frame.path().startsWith(prefix) && subpageDrawn());
    }

    /** 開「另一支手機」：同一組手機設定的另一個瀏覽器環境，用同一枚代碼開首頁，儲存空間與第一支完全分開。 */
    public Page openSecondPhone(String roomNo) {
        Page first = world.page();
        Page second = world.openContext();
        openHome(roomNo);
        scenario.rememberSecondPhone(second);
        world.switchTo(first);
        scenario.rememberPhoneRoom(roomNo);
        return second;
    }

    /** 從這個分頁的 {@code sessionStorage} 讀出畫面換到的住客 session（鍵是 {@link #SESSION_KEY_PREFIX} 接代碼）。 */
    public void rememberSession(String roomNo) {
        Object stored = world.page().evaluate(
                "key => { const raw = sessionStorage.getItem(key); return raw ? JSON.parse(raw).sessionToken : null; }",
                SESSION_KEY_PREFIX + qrToken(roomNo));
        if (stored instanceof String token) {
            scenario.rememberSession(world.page(), token);
        }
    }

    private boolean subpageDrawn() {
        Locator title = frame.titleBar().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setLevel(1));
        return title.count() > 0 && !frame.loading();
    }

    private Locator mainHeading(int level) {
        return world.page().getByRole(AriaRole.MAIN)
                .getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setLevel(level).setName(Pattern.compile(".+")));
    }

    private void waitUntil(String failure, Supplier<Boolean> condition) {
        Page page = world.page();
        frame.waitAtMost(page, condition);
        boolean done;
        try {
            done = condition.get();
        } catch (RuntimeException e) {
            done = false;
        }
        if (!done) {
            throw new AssertionError(failure + "：網址 " + page.url() + "，畫面上的文字是 "
                    + ShellFrame.linesOf(page.locator("body").innerText()));
        }
    }
}
