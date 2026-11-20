package com.athena.web.bdd.steps.guest.common;

import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.bddsupport.GuestSeed;
import com.athena.bddsupport.GuestSeed.Situation;
import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.SupplyPage;
import com.athena.web.bdd.support.Clocks;
import com.athena.web.bdd.support.GuestApp;
import com.athena.web.bdd.support.GuestPhone;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.GuestScreen;
import com.athena.web.bdd.support.GuestStay;
import com.athena.web.bdd.support.GuestTerms;
import com.athena.web.bdd.support.WebWorld;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 住客掃碼頁跨模組共用的 Given 句型（{@code specs/truth/features/guest-web/dsl.md}）。
 *
 * <p>「現在時間是 "{時刻}"」和櫃檯 Web 的句子一字不差，同一個 glue 只能有一個定義，由
 * {@code steps.common.CommonGivenSteps#nowIs} 擔任：兩邊的語意都是後端時鐘與瀏覽器時鐘一起撥到今天該時刻。
 */
public class GuestCommonGivenSteps {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("H:mm");

    private static final Set<String> SEED_COLUMNS =
            Set.of("標題", "狀況", "送出時間", "類型", "負責人", "回覆", "星等", "到手時間", "開始時間");

    private static final Map<String, String> TYPE_BY_TERM =
            Map.of("備品", "amenity", "修繕", "repair", "打掃", "cleaning", "其他", "other");

    /** 走到「已接」才有負責人與到手時間的狀況。 */
    private static final Set<Situation> ACCEPTED = Set.of(Situation.HOUSEKEEPING_ACCEPTED,
            Situation.ENGINEERING_ACCEPTED, Situation.DEFERRED, Situation.DOING, Situation.INTERRUPTED,
            Situation.DONE, Situation.INSPECTION_PASSED, Situation.CLOSED);

    /** 走到「開始」才有開始時間的狀況。 */
    private static final Set<Situation> STARTED = Set.of(Situation.DOING, Situation.INTERRUPTED,
            Situation.DONE, Situation.INSPECTION_PASSED, Situation.CLOSED);

    /**
     * 停在「現在」的狀況：後端把延後、中斷、完成、驗收的那一刻記成現在，之前的到手與開始時間不能晚於它。
     * 已接與進行中停在到手、開始的那一刻本身，所以不在裡面。
     */
    private static final Set<Situation> SETTLED_NOW = Set.of(Situation.DEFERRED, Situation.INTERRUPTED,
            Situation.DONE, Situation.INSPECTION_PASSED, Situation.CLOSED);

    private final WebWorld world;

    private final GuestScenario scenario;

    private final GuestPhone phone;

    private final GuestStay stay;

    private final GuestFrame frame;

    private final SupplyPage supply;

    public GuestCommonGivenSteps(WebWorld world, GuestScenario scenario, GuestPhone phone, GuestStay stay) {
        this.world = world;
        this.scenario = scenario;
        this.phone = phone;
        this.stay = stay;
        this.frame = new GuestFrame(world);
        this.supply = new SupplyPage(world);
    }

    /**
     * 逐列在後端替這間房目前的訂房建好停在該狀況的需求，記下標題對應的 task id。
     *
     * <p>不經住客送出需求的端點（測試策略：真的限流器每間房 10 分鐘只收 3 筆、看系統時鐘），
     * 改用 {@code BackendChannel#seedGuestRequest} 直接寫，住客讀清單時看到的和真的送出過的一樣。
     * 表格以外沒有其他需求，靠的是每個場景開頭清空業務資料；同一個場景再寫一次是追加。
     */
    @假設("{string} 房的住客這次住宿有以下需求：")
    public void stayHasRequests(String roomNo, DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        OffsetDateTime now = world.clocks().now();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            requireKnownColumns(row);
            OffsetDateTime createdAt = timeOr(row.get("送出時間"), now.minusMinutes(rows.size() - 1 - i));
            seed(roomNo, row, createdAt, now);
        }
        scenario.rememberPhoneRoomIfAbsent(roomNo);
    }

    /** 用這間房目前的代碼開 {@code /g/{代碼}}，讓畫面自己換 session；等首頁畫完、需求清單讀完。 */
    @假設("{string} 房的住客已打開首頁")
    public void guestOpenedHome(String roomNo) {
        phone.openHome(roomNo);
    }

    /**
     * 這間房現在沒有任何涵蓋現在的訂房，房內的 QR 代碼仍然有效，所以後端對它換 session 回 {@code room_vacant}。
     * 預設的 308 房本來就是空房；換成有人住的房時，把涵蓋現在的訂房標成已被取代。
     */
    @假設("{string} 房今天沒有人住")
    public void roomVacantToday(String roomNo) {
        OffsetDateTime now = world.backend().backendNow();
        world.backend().queryRows("""
                UPDATE reservations_cache r SET superseded_at = ?
                  FROM rooms m
                 WHERE m.id = r.room_id AND r.tenant_id = ? AND m.tenant_id = r.tenant_id AND m.room_no = ?
                   AND r.superseded_at IS NULL AND r.checkin_at <= ? AND r.checkout_at > ?
                RETURNING r.id
                """, now, world.defaults().tenantId(), roomNo, now, now);
        if (stay.reservationId(roomNo).isPresent()) {
            throw new IllegalStateException(roomNo + " 房仍有涵蓋現在的訂房");
        }
        phone.qrToken(roomNo);
    }

    /** 後端這筆訂房名下沒有任何任務。 */
    @假設("{string} 房的住客這次住宿還沒送過任何需求")
    public void stayHasNoRequests(String roomNo) {
        List<GuestStay.Request> requests = stay.requests(roomNo);
        if (!requests.isEmpty()) {
            throw new IllegalStateException(roomNo + " 房這次住宿已經有需求：" + requests.stream().map(GuestStay.Request::title).toList());
        }
        scenario.rememberPhoneRoomIfAbsent(roomNo);
    }

    /** 還沒開這間房的住客頁時先開首頁，再直接開該頁的網址；等該頁畫完、資料讀完。 */
    @假設("{string} 房的住客停在 {string}")
    public void guestStaysOn(String roomNo, String pageName) {
        GuestScreen screen = GuestScreen.ofTerm(pageName);
        if (!screen.openable()) {
            throw new IllegalArgumentException("「停在」只接受首頁、備品補充頁、設備報修頁、我的需求清單，收到「" + pageName + "」");
        }
        phone.ensureHome(roomNo);
        if (screen != GuestScreen.HOME) {
            phone.openScreen(roomNo, screen);
        }
    }

    /** 還沒開這間房的住客頁時先開首頁，再直接開這張需求的進度頁網址；返回的去處因此算從清單進來的。 */
    @假設("{string} 房的住客開著 {string} 的進度頁")
    public void guestOnRequest(String roomNo, String title) {
        phone.ensureHome(roomNo);
        phone.openRequest(roomNo, title);
    }

    /** 在首頁的住宿指南點這一列；不在首頁時先開首頁。等資訊頁畫完。 */
    @假設("{string} 房的住客開著 {string} 的資訊頁")
    public void guestOnInfoPage(String roomNo, String title) {
        if (!frame.path().equals(GuestApp.path(phone.qrToken(roomNo), GuestApp.HOME))) {
            phone.openHome(roomNo);
        }
        frame.press(title);
        phone.waitUntilInfoPage(roomNo);
    }

    /** 開備品補充頁，每一列按加號到表格的數量（表格沒列的是 0）；等頁底按鈕的字反映選了幾項。 */
    @假設("{string} 房的住客在備品補充頁，已把以下項目調到：")
    public void supplyQuantities(String roomNo, DataTable table) {
        phone.openScreen(roomNo, GuestScreen.SUPPLY);
        int chosen = 0;
        for (Map<String, String> row : table.asMaps()) {
            String item = required(row, "項目");
            int quantity = Integer.parseInt(required(row, "數量"));
            for (int i = 0; i < quantity; i++) {
                frame.settleAfter(() -> supply.increase(item).tap());
            }
            if (quantity > 0) {
                chosen++;
            }
        }
        String bottom = chosen == 0 ? SupplyPage.CHOOSE_QUANTITY : SupplyPage.submitLabel(chosen);
        frame.waitForPressable(bottom);
    }

    /** 同一間房、同一枚代碼的另一個瀏覽器環境打開首頁；之後的「住客」仍指原本那支手機。 */
    @假設("{string} 房的另一支手機也打開了首頁")
    public void secondPhoneOpenedHome(String roomNo) {
        phone.openSecondPhone(roomNo);
    }

    /** 把這支手機記住的住客介面語言設成該語言；畫面已經開著時重新載入讓它生效。 */
    @假設("這支手機的介面語言是 {string}")
    public void phoneLanguage(String language) {
        String code = GuestTerms.languageCode(language);
        boolean onGuestPage = world.hasPage() && frame.path().startsWith("/g/");
        world.session().putLocalItem(world.page(), GuestFrame.LANGUAGE_KEY, code);
        if (onGuestPage) {
            phone.reload();
        }
    }

    /** 這個分頁接下來對後端的每一次請求都斷線，直到「恢復連線」的句子以 {@code Network#restoreBackend} 放行。 */
    @假設("這支手機暫時連不上後端")
    public void phoneOffline() {
        world.network().cutOffBackend(world.page());
    }

    /**
     * 櫃檯替這間房開一張不綁訂房的備品單，建立時間就是該時刻。住客的清單看不到它，但它算在這間房 10 分鐘內的張數裡。
     * 規格寫「把後端時鐘撥過去再開單」，這裡直接寫入建立時間，結果相同而不必動時鐘。
     */
    @假設("櫃檯在 {string} 替 {string} 房代開了一張單")
    public void deskOpenedTask(String time, String roomNo) {
        world.backend().seedDeskTask(roomNo, timeOr(time, null));
    }

    private void seed(String roomNo, Map<String, String> row, OffsetDateTime createdAt, OffsetDateTime now) {
        String title = required(row, "標題");
        Situation situation = Situation.ofTerm(required(row, "狀況"));
        String type = blank(row.get("類型")) ? defaultType(situation) : typeOf(row.get("類型"));
        CreatedStaff assignee = null;
        OffsetDateTime acceptedAt = null;
        OffsetDateTime startedAt = null;
        if (ACCEPTED.contains(situation)) {
            assignee = world.staff(blank(row.get("負責人")) ? defaultAssignee(situation, type) : row.get("負責人").strip());
            acceptedAt = timeOr(row.get("到手時間"), notAfterNow(situation, createdAt.plusMinutes(1), now));
        }
        if (STARTED.contains(situation)) {
            startedAt = timeOr(row.get("開始時間"), notAfterNow(situation, createdAt.plusMinutes(2), now));
        }
        String answer = blank(row.get("回覆")) ? null : row.get("回覆");
        UUID taskId = world.backend().seedGuestRequest(roomNo,
                new GuestSeed(situation, title, type, createdAt, assignee, acceptedAt, startedAt, answer));
        scenario.rememberRequest(title, taskId);
        if (!blank(row.get("星等"))) {
            world.backend().setTaskRating(taskId, Integer.parseInt(row.get("星等").strip()));
        }
    }

    /** 規格的預設：待派單與已回答是其他，查房通過是打掃，已結案是修繕，其餘是備品。 */
    private static String defaultType(Situation situation) {
        return switch (situation) {
            case TRIAGE, ANSWERED -> "other";
            case INSPECTION_PASSED -> "cleaning";
            case CLOSED -> "repair";
            default -> "amenity";
        };
    }

    /** 規格的預設：房務的單是阿美、工務的單是阿強。 */
    private static String defaultAssignee(Situation situation, String type) {
        boolean engineering = situation.term().contains("工務") || "repair".equals(type);
        return engineering ? "阿強" : "阿美";
    }

    /**
     * 到手、開始時間的預設是送出之後 1、2 分鐘；但已經停在「現在」的狀況，這兩個時刻不能晚於現在，
     * 否則後端記的先後會顛倒。最後一列的送出時間就是現在，所以這種狀況下兩者都落在現在。
     */
    private static OffsetDateTime notAfterNow(Situation situation, OffsetDateTime moment, OffsetDateTime now) {
        return SETTLED_NOW.contains(situation) && moment.isAfter(now) ? now : moment;
    }

    private OffsetDateTime timeOr(String hhmm, OffsetDateTime fallback) {
        if (blank(hhmm)) {
            return fallback;
        }
        return Clocks.at(world.clocks().today(), LocalTime.parse(hhmm.strip(), HH_MM));
    }

    private static String typeOf(String term) {
        String type = TYPE_BY_TERM.get(term.strip());
        if (type == null) {
            throw new IllegalArgumentException("類型只有 " + TYPE_BY_TERM.keySet() + "，收到「" + term + "」");
        }
        return type;
    }

    private static void requireKnownColumns(Map<String, String> row) {
        for (String column : row.keySet()) {
            if (!SEED_COLUMNS.contains(column)) {
                throw new IllegalArgumentException("「這次住宿有以下需求」的表格沒有「" + column + "」這一欄，可以用的是 " + SEED_COLUMNS);
            }
        }
    }

    private static String required(Map<String, String> row, String column) {
        String value = row.get(column);
        if (blank(value)) {
            throw new IllegalArgumentException("「這次住宿有以下需求」的「" + column + "」欄必填");
        }
        return value.strip();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
