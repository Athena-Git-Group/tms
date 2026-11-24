package com.athena.web.bdd.support;

import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.guest.GuestFrame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class GuestPhone {

    public static final String SESSION_KEY_PREFIX = "tms.guest.session.";

    private final WebWorld world;

    private final GuestScenario scenario;

    private final GuestFrame frame;

    public GuestPhone(WebWorld world, GuestScenario scenario) {
        this.world = world;
        this.scenario = scenario;
        this.frame = new GuestFrame(world);
    }

    public String qrToken(String roomNo) {
        try {
            return scenario.qrToken(roomNo);
        } catch (IllegalStateException notYet) {
            String token = world.backend().roomQrToken(roomNo);
            scenario.rememberQrToken(roomNo, token);
            return token;
        }
    }

    public boolean onRoom(String roomNo) {
        return world.hasPage() && frame.path().startsWith(GuestApp.path(qrToken(roomNo), GuestApp.HOME));
    }

    public void open(String roomNo, String sub) {
        String url = GuestApp.url(qrToken(roomNo), sub);
        scenario.rememberPhoneRoom(roomNo);
        frame.settleAfter(() -> world.page().navigate(url));
    }

    public void openHome(String roomNo) {
        open(roomNo, GuestApp.HOME);
        waitUntilShown(roomNo, GuestScreen.HOME);
        rememberSession(roomNo);
    }

    public void ensureHome(String roomNo) {
        if (!onRoom(roomNo)) {
            openHome(roomNo);
        }
        scenario.rememberPhoneRoom(roomNo);
    }

    public void openScreen(String roomNo, GuestScreen screen) {
        if (!screen.openable()) {
            throw new IllegalArgumentException("「" + screen.term() + "」沒有可以直接開的網址");
        }
        open(roomNo, screen.sub().orElseThrow());
        waitUntilShown(roomNo, screen);
    }

    public void openRequest(String roomNo, String title) {
        open(roomNo, GuestApp.request(scenario.request(title)));
        waitUntilRequest(roomNo, title);
    }

    public void waitUntilRequest(String roomNo, String title) {
        String path = GuestApp.path(qrToken(roomNo), GuestApp.request(scenario.request(title)));
        waitUntil("「" + title + "」的進度頁沒有畫完", () -> frame.path().equals(path) && subpageDrawn()
                && mainHeading(2).count() > 0);
    }

    public void reload() {
        frame.settleAfter(() -> world.page().reload());
        waitUntilAnyScreen();
    }

    public void waitUntilAnyScreen() {
        Page page = world.page();
        waitUntil("住客頁一直沒有畫完", () -> !frame.loading()
                && (page.getByRole(AriaRole.MAIN).count() > 0 || page.getByRole(AriaRole.HEADING).count() > 0));
    }

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

    public void waitUntilInfoPage(String roomNo) {
        String prefix = GuestApp.path(qrToken(roomNo), "guide/");
        waitUntil("資訊頁沒有畫完", () -> frame.path().startsWith(prefix) && subpageDrawn());
    }

    public Page openSecondPhone(String roomNo) {
        Page first = world.page();
        Page second = world.openContext();
        openHome(roomNo);
        scenario.rememberSecondPhone(second);
        world.switchTo(first);
        scenario.rememberPhoneRoom(roomNo);
        return second;
    }

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
