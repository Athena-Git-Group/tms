package com.athena.web.bdd.steps.guest.home;

import com.athena.web.bdd.support.WebWorld;
import io.cucumber.java.zh_tw.假設;
import java.time.OffsetDateTime;

/** 「首頁」模組的 Given 句型（{@code specs/truth/features/guest-web/首頁/dsl.md}）。 */
public class HomeGivenSteps {

    /** 預設訂房的退房時刻：後天中午。 */
    private static final int CHECKOUT_HOUR = 12;

    private final WebWorld world;

    public HomeGivenSteps(WebWorld world) {
        this.world = world;
    }

    /**
     * 把這間房目前的訂房改成這位住客、今天入住、後天中午退房。入住時刻放在今天零點，和預設訂房相同，
     * 場景把時間撥到今天任何一刻都換得到住客 session。同一間房同一個入住日是改寫同一筆訂房。
     */
    @假設("{string} 房的訂房住客是 {string}，今天入住、後天退房")
    public void reservationGuest(String roomNo, String guestName) {
        OffsetDateTime now = world.backend().backendNow();
        OffsetDateTime startOfToday = now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
        world.backend().createReservation(roomNo, guestName, false, startOfToday,
                startOfToday.plusDays(2).withHour(CHECKOUT_HOUR));
    }
}
