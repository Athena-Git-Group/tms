package com.athena.web.bdd.steps.createrequest;

import com.athena.bddsupport.BackendChannel;
import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.web.bdd.pages.BoardPage;
import com.athena.web.bdd.pages.CreateRequestPanel;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.Clocks;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebWorld;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CreateRequestGivenSteps {

    private static final Set<String> GUEST_TABLE_COLUMNS = Set.of("房號", "住客", "VIP", "入住日", "退房日");

    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})/(\\d{1,2})");

    private static final LocalTime CHECKIN_TIME = LocalTime.MIDNIGHT;

    private static final LocalTime CHECKOUT_TIME = LocalTime.of(11, 0);

    private static final double WAIT_MS = 10_000;

    private final WebWorld world;

    private final CreateRequestScenario scenario;

    private final CreateRequestPanel panel;

    private final ShellFrame shell;

    private final BoardPage board;

    public CreateRequestGivenSteps(WebWorld world, CreateRequestScenario scenario) {
        this.world = world;
        this.scenario = scenario;
        this.panel = new CreateRequestPanel(world);
        this.shell = new ShellFrame(world);
        this.board = new BoardPage(world);
    }

    @假設("飯店的客房與現在的住客如下：")
    public void roomsAndGuests(DataTable table) {
        BackendChannel backend = world.backend();
        OffsetDateTime now = world.clocks().now();
        LocalDate today = world.clocks().today();
        for (Map<String, String> row : table.asMaps()) {
            for (String column : row.keySet()) {
                if (!GUEST_TABLE_COLUMNS.contains(column)) {
                    throw new IllegalArgumentException("「飯店的客房與現在的住客如下：」不認得「" + column + "」這一欄，可以用的欄："
                            + GUEST_TABLE_COLUMNS);
                }
            }
            String roomNo = required(row, "房號");
            backend.ensureRoom(roomNo, BackendChannel.floorFromRoomNo(roomNo));
            String guest = cell(row, "住客");
            if (guest == null) {
                backend.supersedeReservations(roomNo, now, now.plusSeconds(1));
                continue;
            }
            LocalDate checkinDate = cell(row, "入住日") == null ? today : monthDay(cell(row, "入住日"));
            LocalDate checkoutDate = cell(row, "退房日") == null ? today.plusDays(1) : monthDay(cell(row, "退房日"));
            OffsetDateTime checkin = Clocks.at(checkinDate, CHECKIN_TIME);
            OffsetDateTime checkout = Clocks.at(checkoutDate, CHECKOUT_TIME);
            if (checkin.isAfter(now) || !checkout.isAfter(now)) {
                throw new IllegalArgumentException(roomNo + " 的住期 " + checkinDate + "–" + checkoutDate
                        + " 沒有涵蓋現在（" + now + "），不是在住的訂房");
            }
            backend.supersedeReservations(roomNo, checkin, checkout);
            UUID reservationId = backend.createReservation(roomNo, guest, "是".equals(cell(row, "VIP")), checkin, checkout);
            world.tasks().rememberReservation(roomNo, reservationId);
        }
    }

    @假設("飯店沒有 {string} 這間房")
    public void noSuchRoom(String roomNo) {
        world.backend().removeRoom(roomNo.strip());
    }

    @假設("{string} 已打開代開單面板")
    public void panelOpened(String staffName) {
        openPanel(staffName);
    }

    @假設("{string} 已打開代開單面板，房號是 {string}")
    public void panelOpenedWithRoom(String staffName, String roomNo) {
        openPanel(staffName);
        panel.enterRoom(roomNo);
    }

    @假設("{string} 已打開代開單面板，房號是 {string}，選了以下品項：")
    public void panelOpenedWithRoomAndItems(String staffName, String roomNo, DataTable table) {
        openPanel(staffName);
        panel.enterRoom(roomNo);
        pickItems(table);
    }

    @假設("{string} 已打開代開單面板，還沒打房號，選了以下品項：")
    public void panelOpenedWithoutRoomWithItems(String staffName, DataTable table) {
        openPanel(staffName);
        pickItems(table);
    }

    @假設("代開單面板的其他需求是 {string}，要給 {string}")
    public void otherRequestIs(String text, String dept) {
        requirePanelOpen();
        panel.typeOtherRequest(text);
        panel.chooseDept(dept);
    }

    @假設("{string} 已按 {string}")
    public void pressed(String staffName, String name) {
        Locator target = shell.topmostPressable(name);
        shell.pressAndSettle(target);
        if ("代開單".equals(name.strip())) {
            panel.waitUntilReady();
        }
        panel.settle();
    }

    @假設("{string} 已點代開單面板外面的暗色區域")
    public void clickedBackdrop(String staffName) {
        requirePanelOpen();
        panel.clickBackdrop();
        panel.waitUntilClosed();
    }

    @假設("{string} 剛代開了 {string} 的以下單：")
    public void justCreated(String staffName, String roomNo, DataTable table) {
        CreatedStaff staff = world.staff(staffName);
        JsonNode result = CreateRequestTable.submit(world, staff, roomNo, table.asMaps());
        scenario.rememberCreatedTasks(CreateRequestTable.taskIds(result));
        if (!shell.loggedIn()) {
            shell.loginAndOpen(staff, "總覽看板");
            shell.waitUntilLoaded();
        }
        List<String> titles = new ArrayList<>();
        result.path("tasks").forEach(task -> titles.add(task.path("title").asText()));
        world.page().waitForCondition(() -> {
            List<String> inPool = board.readCardsIn(board.column("待認領")).stream()
                    .filter(card -> card.roomNo().equals(roomNo.strip()))
                    .map(BoardPage.CardReading::middle)
                    .toList();
            return titles.stream().allMatch(title -> inPool.stream().anyMatch(middle -> middle.startsWith(title)));
        }, new Page.WaitForConditionOptions().setTimeout(WAIT_MS));
    }

    @假設("{string} 這個房號在面板打開之後被從飯店的房間清單移除")
    public void roomRemovedAfterOpen(String roomNo) {
        requirePanelOpen();
        world.backend().removeRoom(roomNo.strip());
    }

    @假設("{string} 的帳號在面板打開之後被改成不能代開單的角色")
    public void roleChangedAfterOpen(String staffName) {
        requirePanelOpen();
        world.backend().changeRoleToHousekeeping(world.staff(staffName));
    }

    @假設("{string} 的登入在面板打開之後逾時")
    public void loginExpiredAfterOpen(String staffName) {
        requirePanelOpen();
        world.backend().invalidateLogin(world.staff(staffName));
    }

    @假設("開 {string} 這張單時後端會出錯")
    public void failWhenCreating(String title) {
        world.backend().failTaskTitled(title.strip());
    }

    @假設("接下來的代開單送出會被後端判定內容不符合規定")
    public void nextSubmitRejectedAsInvalid() {
        String body = """
                {"code":"validation_failed","message":"內容不符合規定","details":[{"field":"items[0].quantity","issue":"out_of_range"}]}""";
        world.network().handle(world.page(), Network.DESK_CREATE_REQUESTS, route -> route.fulfill(new Route.FulfillOptions()
                .setStatus(422)
                .setContentType("application/json")
                .setBody(body)));
    }

    @假設("{string} 剛才按送出時網路中斷，「沒有送出」的提示開著")
    public void submitLostToNetwork(String staffName) {
        submitWhileDisconnected();
    }

    @假設("{string} 剛才按送出時網路中斷，已按「好」關掉提示，網路已經恢復")
    public void submitLostToNetworkDismissed(String staffName) {
        submitWhileDisconnected();
        dismissNotSent();
    }

    @假設("{string} 剛才按送出時沒有收到回應，但這次的單其實已經開好，已按「好」關掉提示")
    public void submitDeliveredButResponseLost(String staffName) {
        requirePanelOpen();
        Page page = world.page();
        CreateRequestScenario.PanelSnapshot before = CreateRequestScenario.PanelSnapshot.of(panel.readContent());
        Set<UUID> tasksBefore = scenario.readBackend().taskIds();
        world.network().deliverThenLoseResponse(page, Network.DESK_CREATE_REQUESTS);
        panel.submit();
        waitForNotSentDialog();
        scenario.rememberSnapshot(before);
        scenario.rememberIdempotencyKey(world.network().lostResponseKey(page, Network.DESK_CREATE_REQUESTS)
                .orElseThrow(() -> new AssertionError("代開單的請求沒有送到後端，無從丟掉它的回應")));
        List<UUID> created = new ArrayList<>(scenario.readBackend().taskIds());
        created.removeAll(tasksBefore);
        if (created.isEmpty()) {
            throw new AssertionError("後端這次應該已經開好單，卻沒有多出任何一張");
        }
        scenario.rememberCreatedTasks(created);
        dismissNotSent();
    }

    private void submitWhileDisconnected() {
        requirePanelOpen();
        Page page = world.page();
        CreateRequestScenario.PanelSnapshot before = CreateRequestScenario.PanelSnapshot.of(panel.readContent());
        AtomicReference<String> key = new AtomicReference<>();
        Consumer<Request> listener = request -> {
            if (Network.DESK_CREATE_REQUESTS.method().equals(request.method())
                    && request.url().split("[?#]")[0].endsWith(Network.DESK_CREATE_REQUESTS.path())) {
                key.compareAndSet(null, request.headers().get("idempotency-key"));
            }
        };
        page.onRequest(listener);
        world.network().disconnect(page, Network.DESK_CREATE_REQUESTS);
        try {
            panel.submit();
            waitForNotSentDialog();
        } finally {
            page.offRequest(listener);
            world.network().passThrough(page, Network.DESK_CREATE_REQUESTS);
        }
        scenario.rememberSnapshot(before);
        if (key.get() == null) {
            throw new AssertionError("按送出鈕之後畫面沒有送出代開單的請求（或請求沒有帶 Idempotency-Key）");
        }
        scenario.rememberIdempotencyKey(key.get());
    }

    private Locator notSentDialog() {
        return world.page().getByRole(AriaRole.ALERTDIALOG).filter(new Locator.FilterOptions().setHasText("沒有送出"));
    }

    private void waitForNotSentDialog() {
        notSentDialog().waitFor(new Locator.WaitForOptions().setTimeout(WAIT_MS));
    }

    private void dismissNotSent() {
        shell.pressAndSettle(notSentDialog().getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("好").setExact(true)));
        notSentDialog().waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN).setTimeout(WAIT_MS));
    }

    private void openPanel(String staffName) {
        if (!shell.loggedIn()) {
            shell.loginAndOpen(world.staff(staffName), "總覽看板");
            shell.waitUntilLoaded();
        }
        if (!panel.isOpen()) {
            panel.openFromToolbar();
        }
    }

    private void pickItems(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            String item = required(row, "品項");
            String quantity = cell(row, "數量");
            panel.tapItem(item, quantity == null ? 1 : Integer.parseInt(quantity));
        }
        int expected = (int) table.asMaps().stream().map(row -> row.get("品項").strip()).distinct().count()
                + (panel.otherRequestBox().inputValue().isBlank() ? 0 : 1);
        Pattern count = Pattern.compile("(^|\\D)" + expected + "(\\D|$)");
        world.page().waitForCondition(() -> count.matcher(panel.submitButton().innerText()).find(),
                new Page.WaitForConditionOptions().setTimeout(WAIT_MS));
    }

    private void requirePanelOpen() {
        if (!panel.isOpen()) {
            throw new IllegalStateException("代開單面板沒有開著；這一句要寫在打開面板之後");
        }
    }

    private static LocalDate monthDay(String text) {
        Matcher matcher = MONTH_DAY.matcher(text.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("日期要寫成 M/D，收到「" + text + "」");
        }
        return LocalDate.of(2026, Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    private static String required(Map<String, String> row, String column) {
        String value = cell(row, column);
        if (value == null) {
            throw new IllegalArgumentException("「" + column + "」必填：" + row);
        }
        return value;
    }

    private static String cell(Map<String, String> row, String column) {
        String value = row.get(column);
        return value == null || value.isBlank() ? null : value.strip();
    }
}
