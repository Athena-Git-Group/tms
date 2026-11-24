package com.athena.web.bdd.steps.guest.home;

import com.athena.web.bdd.support.WebWorld;
import io.cucumber.java.zh_tw.假設;
import java.time.OffsetDateTime;

public class HomeGivenSteps {

    private static final int CHECKOUT_HOUR = 12;

    private final WebWorld world;

    public HomeGivenSteps(WebWorld world) {
        this.world = world;
    }

    @假設("{string} 房的訂房住客是 {string}，今天入住、後天退房")
    public void reservationGuest(String roomNo, String guestName) {
        OffsetDateTime now = world.backend().backendNow();
        OffsetDateTime startOfToday = now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
        world.backend().createReservation(roomNo, guestName, false, startOfToday,
                startOfToday.plusDays(2).withHour(CHECKOUT_HOUR));
    }
}
