package com.athena.web.bdd.steps.guest.entry;

import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.web.bdd.pages.AccountPanel;
import com.athena.web.bdd.pages.LoginPage;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.guest.GuestEntryPage;
import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.GuestHomePage;
import com.athena.web.bdd.steps.guest.entry.ScreenWatch.Seen;
import com.athena.web.bdd.support.GuestApp;
import com.athena.web.bdd.support.GuestPhone;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.SessionInjector;
import com.athena.web.bdd.support.WebApp;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.zh_tw.那麼;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 「進入層」模組的 Then 句型（{@code specs/truth/features/guest-web/進入層/dsl.md}）。
 *
 * <p>「過程中」的兩句看 {@link ScreenWatch} 從 When 開始記下的每一個畫面樣子，不只看最後的畫面。
 */
public class EntryThenSteps {

    /** 「錯誤訊息」的字：重試狀態的標題與任何寫著錯誤、失敗的字。 */
    private static final List<String> ERROR_WORDS = List.of("連線失敗", "錯誤", "失敗");

    /** 要住客做事的字：登入、同意。 */
    private static final List<String> ASKING_WORDS = List.of("登入", "同意");

    private static final String EMPTY_ROOM = "308";

    private static final String GUEST_SESSION_HEADER = "x-guest-session";

    private final WebWorld world;

    private final GuestScenario scenario;

    private final GuestPhone phone;

    private final ScreenWatch watch;

    private final EntryScenario entry;

    private final GuestFrame frame;

    private final GuestEntryPage entryPage;

    public EntryThenSteps(WebWorld world, GuestScenario scenario, GuestPhone phone, ScreenWatch watch,
            EntryScenario entry) {
        this.world = world;
        this.scenario = scenario;
        this.phone = phone;
        this.watch = watch;
        this.entry = entry;
        this.frame = new GuestFrame(world);
        this.entryPage = new GuestEntryPage(world);
    }

    /** When 記下的點按與輸入都是 0；過程中這個分頁沒有出現過輸入欄、登入頁或要按「同意」的東西。 */
    @那麼("從打開網址到看到首頁，住客沒有輸入或按過任何東西")
    public void nothingTypedOrPressed() {
        assertThat(scenario.openingTaps() == 0 && scenario.openingInputs() == 0,
                "打開網址的過程中替住客點按了 " + scenario.openingTaps() + " 次、輸入了 " + scenario.openingInputs() + " 次");
        assertThat(!seenOnThisTab().isEmpty(), "從 When 開始沒有記到這個分頁的任何畫面");
        for (Seen each : seenOnThisTab()) {
            assertThat(each.inputs() == 0, "過程中出現過輸入欄：" + each.path() + " " + each.lines());
            assertThat(!each.path().endsWith(WebApp.LOGIN), "過程中出現過登入頁：" + each.path());
            for (String word : ASKING_WORDS) {
                assertThat(each.lines().stream().noneMatch(line -> line.contains(word)),
                        "過程中出現過要住客「" + word + "」的字：" + each.lines());
            }
        }
    }

    /**
     * 從 When 開始網址裡的代碼沒有變、沒有出現重試狀態或錯誤字、沒有輸入欄；現在畫面上沒有提示浮層。
     *
     * <p>住客在重試狀態按「重試」時，重試狀態的字會一直留到下一個畫面畫出來；這段期間的字是 When 開始時就在畫面上的，
     * 所以不算過程中出現的錯誤。畫面離開重試狀態之後再出現錯誤字就算。
     */
    @那麼("過程中沒有要住客再掃一次 QR Code，也沒有出現錯誤訊息")
    public void noRescanNoError() {
        Set<String> codes = new LinkedHashSet<>();
        String startCode = codeOf(watch.pathAtStart());
        if (startCode != null) {
            codes.add(startCode);
        }
        List<String> errorsAtStart = errorLines(watch.linesAtStart());
        boolean leftStartScreen = errorsAtStart.isEmpty();
        assertThat(!seenOnThisTab().isEmpty(), "從 When 開始沒有記到這個分頁的任何畫面");
        for (Seen each : seenOnThisTab()) {
            String code = codeOf(each.path());
            if (code != null) {
                codes.add(code);
            }
            assertThat(each.inputs() == 0, "過程中出現過要住客輸入的東西：" + each.path() + " " + each.lines());
            List<String> errors = errorLines(each.lines());
            if (leftStartScreen) {
                assertThat(errors.isEmpty(), "過程中出現過錯誤訊息 " + errors + "：" + each.lines());
            } else {
                assertThat(errorsAtStart.containsAll(errors), "過程中出現過錯誤訊息 " + errors + "：" + each.lines());
                leftStartScreen = !errors.containsAll(errorsAtStart);
            }
        }
        String now = codeOf(frame.path());
        if (now != null) {
            codes.add(now);
        }
        assertThat(codes.size() <= 1, "過程中網址裡的代碼變過：" + codes);
        assertThat(errorLines(entryPage.visibleLines()).isEmpty(), "現在畫面上有錯誤訊息：" + entryPage.visibleLines());
        assertThat(entryPage.toasts().isEmpty(), "現在畫面上有提示浮層：" + entryPage.toasts());
    }

    /** 畫面上的全部可見文字，與另一個瀏覽器環境用 308 房（空房）的代碼開出來的無法使用頁逐字相同。 */
    @那麼("無法使用頁的文字與空房那一頁完全相同")
    public void sameAsEmptyRoomPage() {
        Page phonePage = world.page();
        frame.eventually(() -> entryPage.unavailableShown() && !frame.loading() ? null
                : "畫面不是無法使用頁：" + entryPage.visibleLines());
        List<String> mine = entryPage.visibleLines();
        Page emptyRoom = world.openContext();
        try {
            frame.settleAfter(() -> emptyRoom.navigate(GuestApp.url(phone.qrToken(EMPTY_ROOM), GuestApp.HOME)));
            frame.eventually(() -> entryPage.unavailableShown() && !frame.loading() ? null
                    : "308 房的代碼開出來不是無法使用頁：" + entryPage.visibleLines());
            List<String> reference = entryPage.visibleLines();
            assertThat(mine.equals(reference), "無法使用頁的字是 " + mine + "，空房那一頁是 " + reference);
        } finally {
            world.switchTo(phonePage);
        }
    }

    /** 畫面上沒有按鈕、連結、可點的列或輸入欄；「櫃檯（24 小時）／房內電話撥 9」那張卡也不是連結。 */
    @那麼("畫面上沒有任何可以點的東西")
    public void nothingClickable() {
        phone.waitUntilAnyScreen();
        List<String> clickables = entryPage.clickables();
        assertThat(clickables.isEmpty(), "畫面上有可以點的東西：" + clickables);
    }

    /**
     * 這個分頁從 When 開始對後端的每一次請求都沒有 {@code Authorization} 標頭，換 session 以外的都帶 {@code X-Guest-Session}；
     * 帶的住客 session 都是這間房的，首頁的房號膠囊也是這間房。
     */
    @那麼("住客頁向後端的請求沒有帶員工的登入憑證")
    public void guestRequestsCarryNoStaffCredential() {
        List<Request> requests = watch.backendRequests(world.page());
        assertThat(!requests.isEmpty(), "這個分頁從 When 開始沒有向後端送過任何請求");
        Set<String> sessions = new LinkedHashSet<>();
        List<String> problems = new ArrayList<>();
        for (Request request : requests) {
            Map<String, String> headers = request.allHeaders();
            String path = URI.create(request.url()).getPath();
            String line = request.method() + " " + path;
            if (headers.containsKey("authorization")) {
                problems.add(line + " 帶了 Authorization");
            }
            String session = headers.get(GUEST_SESSION_HEADER);
            if (session != null && !session.isBlank()) {
                sessions.add(session);
            } else if (!isScan(request.method(), path)) {
                problems.add(line + " 沒有帶 X-Guest-Session");
            }
        }
        assertThat(problems.isEmpty(), "住客頁的請求：" + problems);
        String roomNo = scenario.phoneRoom();
        for (String session : sessions) {
            List<Map<String, Object>> rows = world.backend().queryRows("""
                    SELECT m.room_no FROM guest_sessions s JOIN rooms m ON m.id = s.room_id AND m.tenant_id = s.tenant_id
                     WHERE s.session_token = ?
                    """, session);
            assertThat(rows.size() == 1 && roomNo.equals(rows.get(0).get("room_no")),
                    "住客頁帶的 session 不是 " + roomNo + " 房的：" + rows);
        }
        assertThat(new GuestHomePage(world).roomCapsule(roomNo).count() > 0, "首頁看不到 " + roomNo + " 房的房號膠囊");
    }

    /** 櫃檯 Web 那個分頁仍存著這位員工的登入憑證，畫面是總覽看板、帳號卡是他，沒有回到登入頁。 */
    @那麼("櫃檯 Web 仍是 {string} 的登入狀態，畫面是總覽看板")
    public void deskStillLoggedIn(String staffName) {
        CreatedStaff staff = world.staff(staffName);
        Page desk = switchToDeskTab();
        ShellFrame shell = new ShellFrame(world);
        shell.waitForPage("總覽看板");
        assertThat(!new LoginPage(world).onLoginPath(), "櫃檯 Web 回到了登入頁");
        Object stored = desk.evaluate("key => sessionStorage.getItem(key)", SessionInjector.SESSION_KEY);
        assertThat(stored instanceof String text && text.contains("\"" + staff.loginId() + "\""),
                "櫃檯 Web 那個分頁存的登入憑證不是 " + staffName + " 的：" + stored);
        Locator card = new AccountPanel(world).accountCardOf(staff.name(), staff.loginId());
        if (card.count() == 0 && !shell.sidebarOpen()) {
            shell.openSidebar();
        }
        assertThat(card.count() == 1, "帳號卡不是 " + staffName + "（" + staff.loginId() + "）");
    }

    /** 櫃檯 Web 的分頁畫的是登入頁（有「登入」鈕），沒有總覽看板；它的儲存空間裡沒有員工的登入憑證。 */
    @那麼("櫃檯 Web 顯示登入頁")
    public void deskShowsLogin() {
        Page desk = switchToDeskTab();
        new LoginPage(world).waitUntilShown();
        assertThat(desk.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("總覽看板").setExact(true))
                .count() == 0, "櫃檯 Web 畫著總覽看板");
        Object found = desk.evaluate("""
                key => {
                  const found = [];
                  for (const store of [sessionStorage, localStorage]) {
                    for (let i = 0; i < store.length; i++) {
                      const k = store.key(i);
                      const v = store.getItem(k) || '';
                      if (k === key || v.includes('"token"')) found.push(k);
                    }
                  }
                  return found;
                }
                """, SessionInjector.SESSION_KEY);
        assertThat(((List<?>) found).isEmpty(), "櫃檯 Web 的儲存空間有員工的登入憑證：" + found);
    }

    private static boolean isScan(String method, String path) {
        return Network.GUEST_SCAN.method().equals(method) && Network.GUEST_SCAN.path().equals(path);
    }

    private Page switchToDeskTab() {
        Page desk = entry.deskTab().orElseThrow(() -> new AssertionError("這個場景沒有開過櫃檯 Web 的分頁"));
        world.switchTo(desk);
        return desk;
    }

    private List<Seen> seenOnThisTab() {
        Page page = world.page();
        return watch.seen().stream().filter(each -> each.page() == page).toList();
    }

    private static List<String> errorLines(List<String> lines) {
        return lines.stream().filter(line -> ERROR_WORDS.stream().anyMatch(line::contains)).toList();
    }

    /** {@code /g/{代碼}/…} 裡的代碼；不在住客頁時是 {@code null}。 */
    private static String codeOf(String path) {
        if (path == null || !path.startsWith("/g/")) {
            return null;
        }
        String rest = path.substring(3);
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    private static void assertThat(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
