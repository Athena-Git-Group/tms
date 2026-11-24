package com.athena.web.bdd.steps.guest.cancelrate;

import com.athena.bddsupport.GuestSeed;
import com.athena.bddsupport.GuestSeed.Situation;
import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.RequestPage;
import com.athena.web.bdd.support.GuestPhone;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.WebWorld;
import io.cucumber.java.zh_tw.假設;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 「取消與評分」模組的 Given 句型（{@code specs/truth/features/guest-web/取消與評分/dsl.md}）。
 *
 * <p>「住客的畫面還沒跟上」這兩句先讓這個分頁的事件串流維持連線、但後端送來的訊號一則都不交給畫面
 * （{@code Network#holdLiveSignals}），再在後端改這張需求。連線仍算正常，畫面就不會開始定時重讀；
 * 住客按下按鈕之後，畫面因為被後端拒絕而重讀這張需求的請求照常送出，結果句看得到畫面更新成真正的狀態。
 */
public class CancelRateGivenSteps {

    static final String CONFIRM_CANCEL = "取消需求";

    static final String CANCELLED_TOAST = "已取消需求";

    static final String THANKS_TOAST = "謝謝您的評分";

    private final WebWorld world;

    private final GuestScenario scenario;

    private final GuestPhone phone;

    private final GuestFrame frame;

    private final RequestPage request;

    public CancelRateGivenSteps(WebWorld world, GuestScenario scenario, GuestPhone phone) {
        this.world = world;
        this.scenario = scenario;
        this.phone = phone;
        this.frame = new GuestFrame(world);
        this.request = new RequestPage(world);
    }

    /** 開這張需求的進度頁，按「取消這個需求」，等確認框出現。 */
    @假設("{string} 房的住客開著 {string} 的進度頁，取消確認框開著")
    public void cancelDialogOpen(String roomNo, String title) {
        openRequest(roomNo, title);
        frame.press(RequestPage.CANCEL_BUTTON);
        frame.waitAtMost(world.page(), () -> request.dialog().isPresent());
        require(request.dialog().isPresent(), "按了「" + RequestPage.CANCEL_BUTTON + "」之後沒有出現確認框");
    }

    /** 開這張需求的進度頁，點評分卡的第 N 顆星，等字樣更新、「送出評分」可以按。 */
    @假設("{string} 房的住客開著 {string} 的進度頁，已點第 {int} 顆星")
    public void starPicked(String roomNo, String title, int stars) {
        openRequest(roomNo, title);
        CancelRateWhenSteps.pickStar(world, frame, request, stars);
        frame.waitAtMost(world.page(), () -> request.button(RequestPage.RATING_SUBMIT).count() > 0
                && request.button(RequestPage.RATING_SUBMIT).first().isEnabled());
    }

    /**
     * 先讓畫面收不到即時訊號，再以這位員工的身分在後端認領這張需求。畫面仍把它顯示成還沒有人接手，
     * 確認框也還開著。
     */
    @假設("住客的畫面還沒跟上，{string} 已在手機上認領了 {string}")
    public void staffClaimedBehindScreen(String staff, String title) {
        world.network().holdLiveSignals(world.page());
        UUID taskId = scenario.request(title);
        world.backend().postTaskAction(taskId, "claim", world.staff(staff.strip()));
        require("accepted".equals(state(taskId)), "「" + staff + "」認領「" + title + "」之後後端不是已接：" + state(taskId));
    }

    /** 先讓畫面收不到即時訊號，再以同一筆訂房的另一個住客工作階段在後端替這張需求評分。畫面上評分卡還在。 */
    @假設("住客的畫面還沒跟上，另一支手機已替 {string} 送出了 {int} 顆星")
    public void otherPhoneRatedBehindScreen(String title, int stars) {
        world.network().holdLiveSignals(world.page());
        UUID taskId = scenario.request(title);
        String otherSession = world.backend().guestScan(phone.qrToken(scenario.phoneRoom())).sessionToken();
        world.backend().guestRateRequest(otherSession, taskId, stars);
    }

    /**
     * 開進度頁、按「取消這個需求」、按「取消需求」，等「已取消需求」浮出又消失、標籤變成「已取消」。
     * 進度頁是直接開網址進來的，返回算從清單進來的。這句之前沒有種過這張需求時，先替這間房種一張房務待認領的備品，
     * 才有東西可以取消。
     */
    @假設("{string} 房的住客剛取消了 {string}，停在它的進度頁")
    public void justCancelled(String roomNo, String title) {
        if (!scenario.knowsRequest(title)) {
            UUID taskId = world.backend().seedGuestRequest(roomNo, new GuestSeed(Situation.HOUSEKEEPING_POOL, title,
                    "amenity", world.clocks().now(), null, null, null, null));
            scenario.rememberRequest(title, taskId);
            scenario.rememberPhoneRoomIfAbsent(roomNo);
        }
        cancelDialogOpen(roomNo, title);
        frame.press(CONFIRM_CANCEL);
        waitToastCameAndWent(CANCELLED_TOAST);
        frame.waitAtMost(world.page(), this::showsCancelled);
        require(showsCancelled(), "取消之後「" + title + "」的標籤沒有變成「已取消」");
    }

    /**
     * 開進度頁、點第 N 顆星、按「送出評分」，等「謝謝您的評分」浮出又消失、評分卡消失。
     * 這句之前沒有種過這張需求時，先替這間房種一張阿美完成、還沒評過的備品，才有評分卡可以評。
     */
    @假設("{string} 房的住客剛替 {string} 送出了 {int} 顆星，停在它的進度頁")
    public void justRated(String roomNo, String title, int stars) {
        if (!scenario.knowsRequest(title)) {
            OffsetDateTime now = world.clocks().now();
            UUID taskId = world.backend().seedGuestRequest(roomNo, new GuestSeed(Situation.DONE, title, "amenity",
                    now.minusMinutes(2), world.staff("阿美"), now.minusMinutes(1), now, null));
            scenario.rememberRequest(title, taskId);
            scenario.rememberPhoneRoomIfAbsent(roomNo);
        }
        starPicked(roomNo, title, stars);
        frame.press(RequestPage.RATING_SUBMIT);
        waitToastCameAndWent(THANKS_TOAST);
        frame.waitAtMost(world.page(), () -> request.ratingCard().isEmpty());
        require(request.ratingCard().isEmpty(), "送出評分之後評分卡還在");
    }

    private void openRequest(String roomNo, String title) {
        phone.ensureHome(roomNo);
        phone.openRequest(roomNo, title);
    }

    private void waitToastCameAndWent(String text) {
        frame.waitAtMost(world.page(), () -> frame.toasts().allInnerTexts().stream()
                .anyMatch(shown -> shown.strip().equals(text)));
        frame.waitAtMost(world.page(), () -> frame.toasts().count() == 0);
    }

    private boolean showsCancelled() {
        String language = frame.language();
        return request.card(language).map(card -> GuestFrame.tagsIn(card, language).equals(List.of("已取消")))
                .orElse(false);
    }

    private String state(UUID taskId) {
        return String.valueOf(world.backend().queryRows("SELECT state FROM tasks WHERE tenant_id = ? AND id = ?",
                world.defaults().tenantId(), taskId).get(0).get("state"));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
