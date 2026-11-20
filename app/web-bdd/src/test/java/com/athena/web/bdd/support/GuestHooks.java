package com.athena.web.bdd.support;

import com.athena.web.bdd.WebProfile;
import io.cucumber.java.After;
import io.cucumber.java.AfterStep;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 住客掃碼頁的場景在櫃檯 Web 的共同準備之後，再補上住客自己要的東西：預設訂房與資訊頁、
 * 送出需求的限流替身，以及手機的瀏覽器設定。
 *
 * <p>只對 {@code specs/truth/features/guest-web/} 底下的場景做事。櫃檯 Web 的規格也用 311 房，
 * 而且有「住客：空房」的說法；住客的預設訂房若每個場景都建，會改掉櫃檯場景的前提。
 */
public class GuestHooks {

    private static final String GUEST_FEATURE_ROOT = "specs/truth/features/guest-web/";

    private final WebWorld world;

    private final GuestRequestLimitStub limitStub;

    private final GuestScenario scenario;

    private boolean guestScenario;

    public GuestHooks(WebWorld world, GuestRequestLimitStub limitStub, GuestScenario scenario) {
        this.world = world;
        this.limitStub = limitStub;
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
        separateScanFailures();
        limitStub.install();
    }

    /**
     * 後端把掃碼失敗按來源 IP 記在記憶體裡、照系統時鐘算 10 分鐘（{@code ScanIpRateLimiter}），超過門檻後同一個 IP
     * 連有效的代碼也回 429。瀏覽器都從本機連進來，打開無法使用頁的場景一多，之後的場景就換不到 session。
     * 每個場景的手機以 {@code X-Forwarded-For}（後端認的來源）報一個自己的位址，失敗次數不會跨場景累積。
     */
    private void separateScanFailures() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String address = "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
        world.onEveryNewContext(context -> context.setExtraHTTPHeaders(Map.of("X-Forwarded-For", address)));
    }

    /** 「後端回覆很慢」扣著、還沒交給畫面的回應，在關掉瀏覽器之前全部放行。 */
    @After(order = 10)
    public void releaseHeldReplies() {
        if (guestScenario) {
            limitStub.releaseAllReplies();
        }
    }

    @AfterStep
    public void failWhenOverRequestBudget() {
        if (guestScenario) {
            limitStub.requireWithinBudget();
        }
    }
}
