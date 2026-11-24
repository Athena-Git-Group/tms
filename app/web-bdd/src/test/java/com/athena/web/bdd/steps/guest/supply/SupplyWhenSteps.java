package com.athena.web.bdd.steps.guest.supply;

import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.SupplyPage;
import com.athena.web.bdd.support.GuestCreateRecorder;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Request;
import io.cucumber.java.zh_tw.當;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class SupplyWhenSteps {

    private static final long DOUBLE_PRESS_SETTLE_MS = 20_000;

    private static final double POLL_MS = 100;

    private static final double SECOND_PRESS_TIMEOUT_MS = 2_000;

    private final WebWorld world;

    private final GuestScenario scenario;

    private final SupplyScenario supply;

    private final GuestFrame frame;

    private final SupplyPage supplyPage;

    private final GuestCreateRecorder createRecorder;

    public SupplyWhenSteps(WebWorld world, GuestScenario scenario, SupplyScenario supply,
            GuestCreateRecorder createRecorder) {
        this.world = world;
        this.scenario = scenario;
        this.supply = supply;
        this.frame = new GuestFrame(world);
        this.supplyPage = new SupplyPage(world);
        this.createRecorder = createRecorder;
    }

    @當("住客按 {string} 的加號 {int} 次")
    public void pressIncrease(String item, int times) {
        pressRepeatedly(item, times, +1);
    }

    @當("住客按 {string} 的減號 {int} 次")
    public void pressDecrease(String item, int times) {
        pressRepeatedly(item, times, -1);
    }

    @當("住客連按兩下 {string}")
    public void doublePress(String name) {
        Page page = world.page();
        Set<Request> pending = new HashSet<>();
        Consumer<Request> started = request -> {
            if (!"/guest/stream".equals(URI.create(request.url()).getPath())) {
                pending.add(request);
            }
        };
        Consumer<Request> ended = pending::remove;
        page.onRequest(started);
        page.onRequestFinished(ended);
        page.onRequestFailed(ended);
        try {
            Locator button = frame.waitForPressable(name);
            tap(button);
            scenario.countTap();
            boolean disabled;
            try {
                disabled = button.isDisabled(new Locator.IsDisabledOptions().setTimeout(SECOND_PRESS_TIMEOUT_MS));
                tapForced(button);
            } catch (PlaywrightException gone) {
                disabled = true;
            }
            supply.rememberDisabledAtSecondPress(disabled);
            scenario.countTap();
            long deadline = System.nanoTime() + DOUBLE_PRESS_SETTLE_MS * 1_000_000;
            while (System.nanoTime() < deadline) {
                createRecorder.releaseDueReplies();
                if (createRecorder.heldReplies() == 0 && pending.isEmpty()) {
                    break;
                }
                page.waitForTimeout(POLL_MS);
            }
        } finally {
            page.offRequest(started);
            page.offRequestFinished(ended);
            page.offRequestFailed(ended);
        }
        frame.waitAtMost(page, () -> !frame.loading());
    }

    private void pressRepeatedly(String item, int times, int step) {
        Locator button = step > 0 ? supplyPage.increase(item) : supplyPage.decrease(item);
        frame.waitAtMost(world.page(), button::isVisible);
        int expected = Math.max(0, Math.min(SupplyPage.MAX_QUANTITY, supplyPage.quantity(item) + step * times));
        for (int i = 0; i < times; i++) {
            frame.settleAfter(() -> tapForced(button));
            scenario.countTap();
        }
        frame.waitAtMost(world.page(), () -> supplyPage.quantity(item) == expected
                && supplyPage.bottomButton().filter(new Locator.FilterOptions().setVisible(true)).allInnerTexts()
                        .stream().anyMatch(text -> text.strip().equals(supplyPage.expectedBottomLabel())));
    }

    private void tap(Locator button) {
        if (world.profile().touch()) {
            button.tap();
        } else {
            button.click();
        }
    }

    private void tapForced(Locator button) {
        supplyPage.tapEvenIfDisabled(button);
    }
}
