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

public class GuestCommonGivenSteps {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("H:mm");

    private static final Set<String> SEED_COLUMNS =
            Set.of("標題", "狀況", "送出時間", "類型", "負責人", "回覆", "星等", "到手時間", "開始時間");

    private static final Map<String, String> TYPE_BY_TERM =
            Map.of("備品", "amenity", "修繕", "repair", "打掃", "cleaning", "其他", "other");

    private static final Set<Situation> ACCEPTED = Set.of(Situation.HOUSEKEEPING_ACCEPTED,
            Situation.ENGINEERING_ACCEPTED, Situation.DEFERRED, Situation.DOING, Situation.INTERRUPTED,
            Situation.DONE_AWAITING_INSPECTION, Situation.DONE_AWAITING_VERIFICATION,
            Situation.DONE, Situation.INSPECTION_PASSED, Situation.CLOSED);

    private static final Set<Situation> STARTED = Set.of(Situation.DOING, Situation.INTERRUPTED,
            Situation.DONE_AWAITING_INSPECTION, Situation.DONE_AWAITING_VERIFICATION,
            Situation.DONE, Situation.INSPECTION_PASSED, Situation.CLOSED);

    private static final Set<Situation> SETTLED_NOW = Set.of(Situation.DEFERRED, Situation.INTERRUPTED,
            Situation.DONE_AWAITING_INSPECTION, Situation.DONE_AWAITING_VERIFICATION,
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

    @假設("{string} 房的住客已打開首頁")
    public void guestOpenedHome(String roomNo) {
        phone.openHome(roomNo);
    }

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

    @假設("{string} 房的住客這次住宿還沒送過任何需求")
    public void stayHasNoRequests(String roomNo) {
        List<GuestStay.Request> requests = stay.requests(roomNo);
        if (!requests.isEmpty()) {
            throw new IllegalStateException(roomNo + " 房這次住宿已經有需求：" + requests.stream().map(GuestStay.Request::title).toList());
        }
        scenario.rememberPhoneRoomIfAbsent(roomNo);
    }

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

    @假設("{string} 房的住客開著 {string} 的進度頁")
    public void guestOnRequest(String roomNo, String title) {
        phone.ensureHome(roomNo);
        phone.openRequest(roomNo, title);
    }

    @假設("{string} 房的住客開著 {string} 的資訊頁")
    public void guestOnInfoPage(String roomNo, String title) {
        if (!frame.path().equals(GuestApp.path(phone.qrToken(roomNo), GuestApp.HOME))) {
            phone.openHome(roomNo);
        }
        frame.press(title);
        phone.waitUntilInfoPage(roomNo);
    }

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

    @假設("{string} 房的另一支手機也打開了首頁")
    public void secondPhoneOpenedHome(String roomNo) {
        phone.openSecondPhone(roomNo);
    }

    @假設("這支手機的介面語言是 {string}")
    public void phoneLanguage(String language) {
        String code = GuestTerms.languageCode(language);
        boolean onGuestPage = world.hasPage() && frame.path().startsWith("/g/");
        world.session().putLocalItem(world.page(), GuestFrame.LANGUAGE_KEY, code);
        if (onGuestPage) {
            phone.reload();
        }
    }

    @假設("這支手機暫時連不上後端")
    public void phoneOffline() {
        world.network().cutOffBackend(world.page());
    }

    @假設("櫃檯在 {string} 替 {string} 房代開了一張單")
    public void deskOpenedTask(String time, String roomNo) {
        // 照 dsl：後端時鐘撥到代開的時刻開單，再撥回情境現在的時間；瀏覽器的時鐘不動。
        world.backend().setBackendClock(timeOr(time, null));
        try {
            world.backend().seedDeskTask(roomNo, world.backend().backendNow());
        } finally {
            world.backend().setBackendClock(world.clocks().now());
        }
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

    private static String defaultType(Situation situation) {
        return switch (situation) {
            case TRIAGE, ANSWERED -> "other";
            case INSPECTION_PASSED, DONE_AWAITING_INSPECTION -> "cleaning";
            case CLOSED, DONE_AWAITING_VERIFICATION, ENGINEERING_POOL, ENGINEERING_ACCEPTED -> "repair";
            default -> "amenity";
        };
    }

    private static String defaultAssignee(Situation situation, String type) {
        boolean engineering = situation.term().contains("工務") || "repair".equals(type);
        return engineering ? "阿強" : "阿美";
    }

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
