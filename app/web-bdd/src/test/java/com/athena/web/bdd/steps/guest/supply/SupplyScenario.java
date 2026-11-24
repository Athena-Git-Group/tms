package com.athena.web.bdd.steps.guest.supply;

import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebWorld;
import com.athena.web.bdd.support.WhenStart;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class SupplyScenario {

    public record CreateEvent(boolean started, Request request) {
    }

    private static final String SUPPLY_FEATURES = "specs/truth/features/guest-web/備品補充/";

    private final WebWorld world;

    private final GuestScenario scenario;

    private final WhenStart whenStart;

    private final List<CreateEvent> createEvents = new ArrayList<>();

    private Page cutPage;

    private Predicate<String> cutUrl;

    private Consumer<Route> cutHandler;

    private Boolean disabledAtSecondPress;

    public SupplyScenario(WebWorld world, GuestScenario scenario, WhenStart whenStart) {
        this.world = world;
        this.scenario = scenario;
        this.whenStart = whenStart;
    }

    @Before(order = 3)
    public void recordCreatesFromWhen(Scenario cucumberScenario) {
        if (cucumberScenario.getUri().getPath().contains(SUPPLY_FEATURES)) {
            whenStart.alsoCapture(this::listenToCreates);
        }
    }

    public List<CreateEvent> createEvents() {
        return List.copyOf(createEvents);
    }

    public void disconnectAfterFirstCreate(Page page) {
        boolean[] firstCreateSent = {false};
        cutPage = page;
        cutUrl = SupplyScenario::isBackendUrl;
        cutHandler = route -> {
            if (firstCreateSent[0]) {
                route.abort("connectionfailed");
                return;
            }
            Request request = route.request();
            if (Network.GUEST_CREATE.method().equals(request.method())
                    && Network.GUEST_CREATE.path().equals(URI.create(request.url()).getPath())) {
                firstCreateSent[0] = true;
            }
            route.fallback();
        };
        page.route(cutUrl, cutHandler);
    }

    public void reconnect() {
        if (cutPage != null && !cutPage.isClosed()) {
            cutPage.unroute(cutUrl, cutHandler);
        }
        cutPage = null;
    }

    public void rememberDisabledAtSecondPress(boolean disabled) {
        disabledAtSecondPress = disabled;
    }

    public java.util.Optional<Boolean> disabledAtSecondPress() {
        return java.util.Optional.ofNullable(disabledAtSecondPress);
    }

    private void listenToCreates() {
        if (!scenario.active()) {
            return;
        }
        BrowserContext context = world.page().context();
        context.onRequest(request -> {
            if (isCreate(request)) {
                createEvents.add(new CreateEvent(true, request));
            }
        });
        Consumer<Request> ended = request -> {
            if (isCreate(request)) {
                createEvents.add(new CreateEvent(false, request));
            }
        };
        context.onRequestFinished(ended);
        context.onRequestFailed(ended);
    }

    private static boolean isCreate(Request request) {
        return Network.GUEST_CREATE.method().equals(request.method())
                && Network.GUEST_CREATE.path().equals(URI.create(request.url()).getPath());
    }

    private static boolean isBackendUrl(String url) {
        String path = URI.create(url).getPath();
        return path != null && (path.startsWith("/api/") || path.startsWith("/guest/") || path.startsWith("/staff/"));
    }
}
