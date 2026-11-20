package com.athena.web.bdd.steps.guest.supply;

import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.SupplyPage;
import com.athena.web.bdd.support.GuestRequestLimitStub;
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

/** 「備品補充」模組的 When 句型（{@code specs/truth/features/guest-web/備品補充/dsl.md}）。 */
public class SupplyWhenSteps {

    /** 「連按兩下」之後等全部請求結束的上限：每一張建立請求可能被扣 3 秒，兩項就是 6 秒，再留一些給畫面。 */
    private static final long DOUBLE_PRESS_SETTLE_MS = 20_000;

    private static final double POLL_MS = 100;

    private static final double SECOND_PRESS_TIMEOUT_MS = 2_000;

    private final WebWorld world;

    private final GuestScenario scenario;

    private final SupplyScenario supply;

    private final GuestFrame frame;

    private final SupplyPage supplyPage;

    private final GuestRequestLimitStub limitStub;

    public SupplyWhenSteps(WebWorld world, GuestScenario scenario, SupplyScenario supply,
            GuestRequestLimitStub limitStub) {
        this.world = world;
        this.scenario = scenario;
        this.supply = supply;
        this.frame = new GuestFrame(world);
        this.supplyPage = new SupplyPage(world);
        this.limitStub = limitStub;
    }

    /** 按名稱「增加{項目}」的按鈕這麼多次，停用時也照按（不會有效果）；等數字與頁底按鈕的字更新。 */
    @當("住客按 {string} 的加號 {int} 次")
    public void pressIncrease(String item, int times) {
        pressRepeatedly(item, times, +1);
    }

    /** 同上，按名稱「減少{項目}」的按鈕。 */
    @當("住客按 {string} 的減號 {int} 次")
    public void pressDecrease(String item, int times) {
        pressRepeatedly(item, times, -1);
    }

    /**
     * 按頁底這顆按鈕，不等回應立刻再按一次，記下第二下按下時它是不是停用的；
     * 再等兩下引發的請求全部結束（期間放行扣滿 3 秒的回應）、畫面不再標成載入中。
     */
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
                limitStub.releaseDueReplies();
                if (limitStub.heldReplies() == 0 && pending.isEmpty()) {
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

    /** 停用的按鈕也照點：瀏覽器不會把點在停用按鈕上的動作當成一次點按。 */
    private void tapForced(Locator button) {
        supplyPage.tapEvenIfDisabled(button);
    }
}
