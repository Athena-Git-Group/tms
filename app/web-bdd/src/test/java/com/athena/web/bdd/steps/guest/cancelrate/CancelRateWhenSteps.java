package com.athena.web.bdd.steps.guest.cancelrate;

import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.RequestPage;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.WebWorld;
import io.cucumber.java.zh_tw.當;
import java.util.List;

/** 「取消與評分」模組的 When 句型（{@code specs/truth/features/guest-web/取消與評分/dsl.md}）。 */
public class CancelRateWhenSteps {

    private final WebWorld world;

    private final GuestScenario scenario;

    private final GuestFrame frame;

    private final RequestPage request;

    public CancelRateWhenSteps(WebWorld world, GuestScenario scenario) {
        this.world = world;
        this.scenario = scenario;
        this.frame = new GuestFrame(world);
        this.request = new RequestPage(world);
    }

    /** 點評分卡上名稱「{N} 顆星」的那一顆；等選中的星與字樣跟上，最多 5 秒，等不到也往下走。 */
    @當("住客點第 {int} 顆星")
    public void guestPicksStar(int stars) {
        pickStar(world, frame, request, stars);
        scenario.countTap();
    }

    static void pickStar(WebWorld world, GuestFrame frame, RequestPage request, int stars) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("星等是 1 到 5，收到 " + stars);
        }
        frame.settleAfter(() -> request.tapStar(stars));
        frame.waitAtMost(world.page(), () -> request.checkedStars().equals(List.of(stars)));
    }
}
