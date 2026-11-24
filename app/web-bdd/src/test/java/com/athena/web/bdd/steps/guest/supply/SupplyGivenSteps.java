package com.athena.web.bdd.steps.guest.supply;

import com.athena.bddsupport.GuestSeed;
import com.athena.bddsupport.GuestSeed.Situation;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.SupplyPage;
import com.athena.web.bdd.steps.guest.common.GuestCommonGivenSteps;
import com.athena.web.bdd.support.GuestApp;
import com.athena.web.bdd.support.GuestPhone;
import com.athena.web.bdd.support.GuestCreateRecorder;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.GuestScreen;
import com.athena.web.bdd.support.GuestStay;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Page;
import io.cucumber.datatable.DataTable;
import io.cucumber.datatable.DataTableTypeRegistry;
import io.cucumber.datatable.DataTableTypeRegistryTableConverter;
import io.cucumber.java.zh_tw.假設;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SupplyGivenSteps {

    private static final String REQUEST_MENU = "request_menu";

    private static final String LIMIT_NOTICE = "10 分鐘內最多送出 3 項";

    private static final Pattern TITLE = Pattern.compile("^(.+?) ×(\\d+)$");

    private final WebWorld world;

    private final GuestScenario scenario;

    private final GuestPhone phone;

    private final GuestStay stay;

    private final SupplyScenario supply;

    private final GuestCommonGivenSteps common;

    private final GuestFrame frame;

    private final SupplyPage supplyPage;

    private final GuestCreateRecorder createRecorder;

    public SupplyGivenSteps(WebWorld world, GuestScenario scenario, GuestPhone phone, GuestStay stay,
            SupplyScenario supply, GuestCommonGivenSteps common, GuestCreateRecorder createRecorder) {
        this.world = world;
        this.scenario = scenario;
        this.phone = phone;
        this.stay = stay;
        this.supply = supply;
        this.common = common;
        this.frame = new GuestFrame(world);
        this.supplyPage = new SupplyPage(world);
        this.createRecorder = createRecorder;
    }

    @假設("飯店沒有另外設定服務項目")
    public void noCustomMenu() {
        world.backend().clearTenantConfig(world.defaults().tenantId(), REQUEST_MENU);
    }

    @假設("飯店設定了以下服務項目：")
    public void customMenu(DataTable table) {
        List<Map<String, String>> menu = new ArrayList<>();
        List<Map<String, String>> rows = table.asMaps();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("key", "bdd-item-" + (i + 1));
            item.put("label", rows.get(i).get("名稱").strip());
            item.put("dept", "housekeeping");
            item.put("type", "amenity");
            menu.add(item);
        }
        world.backend().setTenantConfig(world.defaults().tenantId(), REQUEST_MENU, menu);
    }

    @假設("{string} 房的住客在備品補充頁，所有數量是 0")
    public void supplyAllZero(String roomNo) {
        phone.ensureHome(roomNo);
        phone.openScreen(roomNo, GuestScreen.SUPPLY);
        waitUntil("備品補充頁的清單沒有畫完或數量不是 0", () -> {
            List<String> items = supplyPage.itemNames();
            return !items.isEmpty() && items.stream().allMatch(item -> supplyPage.quantity(item) == 0);
        });
    }

    @假設("{string} 房的住客剛在備品補充送出以下項目，停在送出成功頁：")
    public void justSentOnSuccessPage(String roomNo, DataTable table) {
        Map<String, Integer> quantities = quantities(table);
        common.supplyQuantities(roomNo, quantityTable(quantities));
        frame.press(SupplyPage.submitLabel(quantities.size()));
        phone.waitUntilShown(roomNo, GuestScreen.SENT);
        rememberCreated(roomNo, quantities);
    }

    @假設("{string} 房的住客剛在備品補充送出以下項目，已回到首頁：")
    public void justSentBackHome(String roomNo, DataTable table) {
        justSentOnSuccessPage(roomNo, table);
        frame.press("回首頁");
        phone.waitUntilShown(roomNo, GuestScreen.HOME);
    }

    @假設("{string} 房的住客在備品補充頁，剛被額度估算擋下，提示框顯示中")
    public void blockedByEstimate(String roomNo) {
        OffsetDateTime now = world.clocks().now();
        seedPool(roomNo, "礦泉水 ×2", now.minusMinutes(2));
        seedPool(roomNo, "毛巾", now.minusMinutes(1));
        Map<String, Integer> quantities = new LinkedHashMap<>();
        quantities.put("衣架", 1);
        quantities.put("牙刷組", 1);
        quantities.put("吹風機", 1);
        common.supplyQuantities(roomNo, quantityTable(quantities));
        frame.press(SupplyPage.submitLabel(quantities.size()));
        waitUntil("按送出之後沒有出現提示框「" + LIMIT_NOTICE + "」", () -> frame.notices().all().stream()
                .anyMatch(notice -> frame.noticeLines(notice).contains(LIMIT_NOTICE)));
    }

    @假設("這支手機會在第一張需求建立之後斷線")
    public void disconnectAfterFirstCreate() {
        supply.disconnectAfterFirstCreate(world.page());
    }

    @假設("後端這時回覆很慢")
    public void slowReplies() {
        createRecorder.slowRepliesOn(world.page());
    }

    @假設("{string} 房的住客剛才送到一半斷線，{string} 已建立、{string} 沒有建立，手機已恢復連線")
    public void halfSentThenReconnected(String roomNo, String createdTitle, String notCreatedTitle) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        for (String title : List.of(createdTitle, notCreatedTitle)) {
            Matcher matcher = TITLE.matcher(title.strip());
            if (matcher.matches()) {
                quantities.put(matcher.group(1), Integer.parseInt(matcher.group(2)));
            } else {
                quantities.put(title.strip(), 1);
            }
        }
        phone.ensureHome(roomNo);
        common.supplyQuantities(roomNo, quantityTable(quantities));
        supply.disconnectAfterFirstCreate(world.page());
        frame.press(SupplyPage.submitLabel(quantities.size()));
        waitUntil("後端應該只有「" + createdTitle + "」、沒有「" + notCreatedTitle + "」", () -> {
            List<String> titles = stay.requests(roomNo).stream().map(GuestStay.Request::title).toList();
            return titles.contains(createdTitle.strip()) && !titles.contains(notCreatedTitle.strip());
        });
        supply.reconnect();
        rememberCreated(roomNo, Map.of(createdTitleItem(createdTitle), quantityOf(createdTitle)));
        String supplyPath = GuestApp.path(phone.qrToken(roomNo), GuestApp.SUPPLY);
        waitUntil("送到一半斷線之後畫面不在備品補充頁", () -> frame.path().equals(supplyPath) && !frame.loading());
    }

    private void seedPool(String roomNo, String title, OffsetDateTime createdAt) {
        UUID taskId = world.backend().seedGuestRequest(roomNo,
                new GuestSeed(Situation.HOUSEKEEPING_POOL, title, "amenity", createdAt, null, null, null, null));
        scenario.rememberRequest(title, taskId);
    }

    private void rememberCreated(String roomNo, Map<String, Integer> quantities) {
        List<GuestStay.Request> requests = stay.requests(roomNo);
        for (Map.Entry<String, Integer> item : quantities.entrySet()) {
            String title = item.getValue() > 1 ? item.getKey() + " ×" + item.getValue() : item.getKey();
            GuestStay.Request created = requests.stream()
                    .filter(request -> request.title().equals(title))
                    .reduce((older, newer) -> newer)
                    .orElseThrow(() -> new AssertionError("送出之後後端沒有「" + title + "」：" + requests.stream()
                            .map(GuestStay.Request::title).toList()));
            scenario.rememberRequest(title, created.id());
        }
    }

    private static String createdTitleItem(String title) {
        Matcher matcher = TITLE.matcher(title.strip());
        return matcher.matches() ? matcher.group(1) : title.strip();
    }

    private static int quantityOf(String title) {
        Matcher matcher = TITLE.matcher(title.strip());
        return matcher.matches() ? Integer.parseInt(matcher.group(2)) : 1;
    }

    private static Map<String, Integer> quantities(DataTable table) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        for (Map<String, String> row : table.asMaps()) {
            int quantity = Integer.parseInt(row.get("數量").strip());
            if (quantity > 0) {
                quantities.put(row.get("項目").strip(), quantity);
            }
        }
        return quantities;
    }

    private static final DataTable.TableConverter TABLE_CONVERTER =
            new DataTableTypeRegistryTableConverter(new DataTableTypeRegistry(Locale.ROOT));

    private static DataTable quantityTable(Map<String, Integer> quantities) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("項目", "數量"));
        quantities.forEach((item, quantity) -> rows.add(List.of(item, String.valueOf(quantity))));
        return DataTable.create(rows, TABLE_CONVERTER);
    }

    private void waitUntil(String failure, Supplier<Boolean> condition) {
        Page page = world.page();
        frame.waitAtMost(page, () -> {
            try {
                return condition.get();
            } catch (RuntimeException notYet) {
                return false;
            }
        });
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
