package com.athena.web.bdd.support;

import com.athena.bddsupport.BackendChannel;
import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.bddsupport.BackendChannel.FrontdeskDefaults;
import com.athena.web.bdd.WebProfile;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class WebWorld {

    private static final Path FAILURE_SCREENSHOT_DIR = Path.of("target", "failure-screenshots");

    private static final BackendChannel BACKEND = new BackendChannel();

    private final Clocks clocks = new Clocks(BACKEND);

    private final Network network = new Network(this::page);

    private final SessionInjector session = new SessionInjector(BACKEND, this::page, () -> defaults().tenantCode());

    private final ScenarioTasks tasks = new ScenarioTasks();

    private final List<BrowserContext> contexts = new ArrayList<>();

    private final List<java.util.function.Consumer<BrowserContext>> contextSetups = new ArrayList<>();

    private final List<Runnable> waitingChores = new ArrayList<>();

    private WebProfile profile = BrowserProfiles.defaultProfile();

    private FrontdeskDefaults defaults;

    private Page page;

    @Before(order = 0)
    public void prepareScenario() {
        BACKEND.removeFaultTriggers();
        BACKEND.resetBusinessData();
        defaults = BACKEND.ensureFrontdeskDefaults();
        clocks.startAtDefault();
    }

    @After(order = 0)
    public void finishScenario(Scenario scenario) {
        try {
            if (scenario.isFailed()) {
                captureOpenPages(scenario);
            }
        } finally {
            try {
                clocks.releasePaused();
            } catch (RuntimeException alreadyGone) {
            }
            try {
                closeContexts();
            } finally {
                try {
                    clocks.reset();
                } finally {
                    BACKEND.removeFaultTriggers();
                }
            }
        }
    }

    public BackendChannel backend() {
        return BACKEND;
    }

    public FrontdeskDefaults defaults() {
        if (defaults == null) {
            throw new IllegalStateException("預設資料還沒建好：這個方法只能在場景開始之後呼叫");
        }
        return defaults;
    }

    public CreatedStaff staff(String name) {
        return defaults().staff(name);
    }

    public Clocks clocks() {
        return clocks;
    }

    public Network network() {
        return network;
    }

    public SessionInjector session() {
        return session;
    }

    public ScenarioTasks tasks() {
        return tasks;
    }

    public WebProfile profile() {
        return profile;
    }

    public Page page() {
        if (page == null || page.isClosed()) {
            page = openContext();
        }
        return page;
    }

    public boolean hasPage() {
        return page != null && !page.isClosed();
    }

    public void open(String path) {
        openPath(WebApp.underHotel(defaults().tenantCode(), path));
    }

    public void openPath(String fullPath) {
        page().navigate(WebApp.url(fullPath));
    }

    public Page openTab() {
        page = page().context().newPage();
        return page;
    }

    public Page openContext() {
        return openContext(options -> { });
    }

    public Page openContext(java.util.function.Consumer<com.microsoft.playwright.Browser.NewContextOptions> customize) {
        BrowserContext context = BrowserProfiles.newContext(profile, customize);
        contexts.add(context);
        clocks.install(context);
        network.watch(context);
        contextSetups.forEach(setup -> setup.accept(context));
        page = context.newPage();
        return page;
    }

    public void onEveryNewContext(java.util.function.Consumer<BrowserContext> setup) {
        contextSetups.add(setup);
    }

    public void whileWaiting(Runnable chore) {
        waitingChores.add(chore);
    }

    public void doWaitingChores() {
        waitingChores.forEach(Runnable::run);
    }

    public void switchTo(Page target) {
        if (target.isClosed()) {
            throw new IllegalArgumentException("這個分頁已經關掉了");
        }
        page = target;
    }

    public void useProfile(WebProfile next) {
        if (next == profile) {
            return;
        }
        closeContexts();
        profile = next;
    }

    public void closeBrowser() {
        closeContexts();
    }

    private void closeContexts() {
        RuntimeException firstFailure = null;
        for (BrowserContext context : List.copyOf(contexts)) {
            clocks.forget(context);
            try {
                context.close();
            } catch (RuntimeException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        contexts.clear();
        page = null;
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    private void captureOpenPages(Scenario scenario) {
        int index = 0;
        for (BrowserContext context : contexts) {
            for (Page open : context.pages()) {
                if (open.isClosed()) {
                    continue;
                }
                index++;
                String name = screenshotName(scenario, index);
                try {
                    byte[] png = open.screenshot(new Page.ScreenshotOptions().setFullPage(true));
                    Files.createDirectories(FAILURE_SCREENSHOT_DIR);
                    Files.write(FAILURE_SCREENSHOT_DIR.resolve(name), png);
                    scenario.attach(png, "image/png", name);
                } catch (RuntimeException | IOException e) {
                    scenario.log("截圖失敗（" + name + "）：" + e);
                }
            }
        }
    }

    private String screenshotName(Scenario scenario, int index) {
        String title = scenario.getName().replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        if (title.length() > 80) {
            title = title.substring(0, 80);
        }
        return title + "-L" + scenario.getLine() + "-" + profile.id() + "-" + index + ".png";
    }
}
