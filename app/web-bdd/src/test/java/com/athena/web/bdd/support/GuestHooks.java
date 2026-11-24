package com.athena.web.bdd.support;

import com.athena.web.bdd.WebProfile;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;

public class GuestHooks {

    private static final String GUEST_FEATURE_ROOT = "specs/truth/features/guest-web/";

    private final WebWorld world;

    private final GuestCreateRecorder createRecorder;

    private final GuestScenario scenario;

    private boolean guestScenario;

    public GuestHooks(WebWorld world, GuestCreateRecorder createRecorder, GuestScenario scenario) {
        this.world = world;
        this.createRecorder = createRecorder;
        this.scenario = scenario;
    }

    @Before(order = 1)
    public void prepareGuestScenario(Scenario scenario) {
        guestScenario = scenario.getUri().toString().contains(GUEST_FEATURE_ROOT);
        if (!guestScenario) {
            return;
        }
        this.scenario.markActive();
        world.backend().ensureGuestDefaults();
        world.useProfile(WebProfile.PHONE);
        world.backend().resetRateLimits();
        createRecorder.install();
    }


    @After(order = 10)
    public void releaseHeldReplies() {
        if (guestScenario) {
            createRecorder.releaseAllReplies();
        }
    }
}
