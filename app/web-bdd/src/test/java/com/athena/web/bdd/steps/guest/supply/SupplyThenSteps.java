package com.athena.web.bdd.steps.guest.supply;

import com.athena.bddsupport.BackendChannel.GuestMenuItem;
import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.SupplyPage;
import com.athena.web.bdd.steps.guest.supply.SupplyScenario.CreateEvent;
import com.athena.web.bdd.support.GuestPhone;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.GuestStay;
import com.athena.web.bdd.support.WebWorld;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import io.cucumber.java.zh_tw.那麼;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SupplyThenSteps {

    private static final double AFTER_FORCED_TAP_MS = 300;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebWorld world;

    private final GuestScenario scenario;

    private final GuestPhone phone;

    private final GuestStay stay;

    private final SupplyScenario supply;

    private final GuestFrame frame;

    private final SupplyPage supplyPage;

    public SupplyThenSteps(WebWorld world, GuestScenario scenario, GuestPhone phone, GuestStay stay,
            SupplyScenario supply) {
        this.world = world;
        this.scenario = scenario;
        this.phone = phone;
        this.stay = stay;
        this.supply = supply;
        this.frame = new GuestFrame(world);
        this.supplyPage = new SupplyPage(world);
    }

    @那麼("備品清單由上到下是 {string}")
    public void itemsInOrder(String itemList) {
        List<String> expected = split(itemList);
        frame.eventually(() -> {
            List<String> names = supplyPage.itemNames();
            if (!names.equals(expected)) {
                return "備品清單是 " + names + "，應該是 " + expected;
            }
            for (String name : names) {
                List<String> lines = supplyPage.rowLines(name);
                if (lines.isEmpty() || !lines.get(0).equals(name)) {
                    return "「" + name + "」那一列第一行不是名稱：" + lines;
                }
            }
            return null;
        });
        List<String> backendMenu = world.backend().guestScan(phone.qrToken(scenario.phoneRoom())).menu().stream()
                .map(GuestMenuItem::label)
                .toList();
        assertThat(backendMenu.equals(expected), "後端給的服務項目是 " + backendMenu + "，應該是 " + expected);
    }

    @那麼("每一列的提示都是 {string}、數量都是 0")
    public void everyRowZero(String hint) {
        frame.eventually(() -> {
            List<String> names = supplyPage.itemNames();
            if (names.isEmpty()) {
                return "備品補充頁沒有任何一列";
            }
            for (String name : names) {
                if (!supplyPage.rowLines(name).contains(hint.strip())) {
                    return "「" + name + "」那一列沒有「" + hint + "」：" + supplyPage.rowLines(name);
                }
                if (supplyPage.quantity(name) != 0) {
                    return "「" + name + "」的數量是 " + supplyPage.quantity(name) + "，不是 0";
                }
                if (!supplyPage.decrease(name).isDisabled()) {
                    return "「" + name + "」的減號不是停用的";
                }
            }
            return null;
        });
    }

    @那麼("{string} 的數量是 {int}")
    public void quantityIs(String item, int quantity) {
        frame.eventually(() -> supplyPage.quantity(item) == quantity ? null
                : "「" + item + "」的數量是 " + supplyPage.quantity(item) + "，應該是 " + quantity
                        + "；那一列是 " + supplyPage.rowLines(item));
    }

    @那麼("{string} 的加號不能按")
    public void increaseDisabled(String item) {
        assertDisabledAndInert(item, supplyPage.increase(item), "加號");
    }

    @那麼("{string} 的減號不能按")
    public void decreaseDisabled(String item) {
        assertDisabledAndInert(item, supplyPage.decrease(item), "減號");
    }

    @那麼("{string} 的減號可以按")
    public void decreaseEnabled(String item) {
        frame.eventually(() -> {
            Locator decrease = supplyPage.decrease(item);
            if (!decrease.isVisible()) {
                return "看不到「減少" + item + "」";
            }
            return decrease.isEnabled() ? null : "「減少" + item + "」是停用的";
        });
    }

    @那麼("後端送出的順序是 {string}")
    public void backendOrder(String titleList) {
        List<String> expected = split(titleList);
        List<CreateEvent> events = supply.createEvents();
        int open = 0;
        int sent = 0;
        for (CreateEvent event : events) {
            if (event.started()) {
                sent++;
                assertThat(open == 0, "第 " + sent + " 張的建立請求在前一張回覆之前就送出了");
                open++;
            } else {
                open--;
            }
        }
        Map<UUID, Integer> sentOrder = new HashMap<>();
        List<Request> creates = events.stream().filter(CreateEvent::started).map(CreateEvent::request).toList();
        for (int i = 0; i < creates.size(); i++) {
            createdId(creates.get(i)).ifPresent(id -> sentOrder.putIfAbsent(id, sentOrder.size()));
        }
        List<GuestStay.Request> added = new ArrayList<>(stay.addedSinceWhen(scenario.phoneRoom()));
        added.sort(Comparator.comparing(GuestStay.Request::createdAt)
                .thenComparing(request -> sentOrder.getOrDefault(request.id(), Integer.MAX_VALUE)));
        List<String> titles = added.stream().map(GuestStay.Request::title).toList();
        assertThat(titles.equals(expected), "後端這次建立的需求依先後是 " + titles + "，應該是 " + expected);
    }

    private java.util.Optional<UUID> createdId(Request request) {
        Response response = request.response();
        if (response == null || response.status() / 100 != 2) {
            return java.util.Optional.empty();
        }
        try {
            JsonNode body = JSON.readTree(response.text());
            return java.util.Optional.of(UUID.fromString(body.path("id").asText()));
        } catch (Exception notATask) {
            return java.util.Optional.empty();
        }
    }

    private void assertDisabledAndInert(String item, Locator button, String which) {
        frame.eventually(() -> button.isVisible() && button.isDisabled() ? null : "「" + item + "」的" + which + "不是停用的");
        int before = supplyPage.quantity(item);
        supplyPage.tapEvenIfDisabled(button);
        world.page().waitForTimeout(AFTER_FORCED_TAP_MS);
        int after = supplyPage.quantity(item);
        assertThat(after == before, "硬按「" + item + "」的" + which + "之後數量從 " + before + " 變成 " + after);
    }

    private static List<String> split(String list) {
        return Arrays.stream(list.split("、")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static void assertThat(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
