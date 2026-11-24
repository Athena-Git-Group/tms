package com.athena.web.bdd.steps.guest.cancelrate;

import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.RequestPage;
import com.athena.web.bdd.pages.guest.StarShape;
import com.athena.web.bdd.support.GuestPhone;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.GuestStay;
import com.athena.web.bdd.support.GuestStay.Request;
import com.athena.web.bdd.support.WebApp;
import com.athena.web.bdd.support.WebWorld;
import com.athena.web.bdd.support.WhenStart;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.RequestOptions;
import io.cucumber.java.zh_tw.那麼;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class CancelRateThenSteps {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String RATE_PROMPT = "點星星評分";

    private static final Set<String> UNCLAIMED = Set.of("待派單", "房務待認領", "工務待認領");

    private static final Map<String, String> CLAIMED_DEPT = Map.of("房務已接", "housekeeping", "工務已接", "engineering");

    private static final String GUEST_WITHDREW = "已取消（住客收回）";

    private final WebWorld world;

    private final GuestScenario scenario;

    private final GuestPhone phone;

    private final GuestStay stay;

    private final WhenStart whenStart;

    private final GuestFrame frame;

    private final RequestPage request;

    public CancelRateThenSteps(WebWorld world, GuestScenario scenario, GuestPhone phone, GuestStay stay,
            WhenStart whenStart) {
        this.world = world;
        this.scenario = scenario;
        this.phone = phone;
        this.stay = stay;
        this.whenStart = whenStart;
        this.frame = new GuestFrame(world);
        this.request = new RequestPage(world);
    }

    @那麼("畫面出現確認框，標題 {string}、內文 {string}、按鈕 {string} 與 {string}")
    public void confirmDialogShown(String title, String body, String first, String second) {
        frame.eventually(() -> {
            Optional<Locator> dialog = request.dialog();
            if (dialog.isEmpty()) {
                return "畫面上沒有確認框";
            }
            Locator d = dialog.get();
            if (!RequestPage.dialogTitle(d).equals(title.strip())) {
                return "確認框的標題是「" + RequestPage.dialogTitle(d) + "」，不是「" + title + "」";
            }
            if (!RequestPage.dialogBody(d).equals(body.strip())) {
                return "確認框的內文是「" + RequestPage.dialogBody(d) + "」，不是「" + body + "」";
            }
            List<String> buttons = RequestPage.dialogButtons(d);
            if (!buttons.equals(List.of(first.strip(), second.strip()))) {
                return "確認框的按鈕是 " + buttons + "，應該剛好是「" + first + "」「" + second + "」";
            }
            if (!RequestPage.scrimBehind(d)) {
                return "確認框後面沒有遮罩";
            }
            return RequestPage.focusOnFirstButton(d) ? null : "焦點不在確認框的第一顆按鈕「" + first + "」上";
        });
    }

    @那麼("畫面上沒有任何確認框")
    public void noConfirmDialog() {
        frame.eventually(() -> {
            if (request.dialog().isPresent()) {
                return "畫面上還有確認框：" + request.dialog().get().innerText();
            }
            return frame.scrimShown() ? "畫面上還有遮罩" : null;
        });
    }

    @那麼("畫面頂端浮出提示 {string}，幾秒後自己消失")
    public void toastCameAndWent(String text) {
        String wanted = text.strip();
        frame.eventually(() -> {
            List<String> shown = frame.toasts().allInnerTexts().stream().map(String::strip).toList();
            if (!shown.contains(wanted)) {
                return "畫面上沒有浮出「" + wanted + "」，浮著的是 " + shown;
            }
            Locator toast = frame.toasts().filter(new Locator.FilterOptions().setHasText(wanted)).first();
            BoundingBox box = toast.boundingBox();
            int viewportHeight = world.page().viewportSize().height;
            if (box == null || box.y > viewportHeight / 4.0) {
                return "「" + wanted + "」不在畫面頂端";
            }
            return backStillPressable() ? null : "「" + wanted + "」浮出時擋住了返回鈕";
        });
        frame.eventually(() -> frame.toasts().count() == 0 ? null
                : "提示浮層沒有自己消失：" + frame.toasts().allInnerTexts());
    }

    @那麼("後端 {string} 的需求是 {string}")
    public void backendSituationIs(String title, String situation) {
        UUID taskId = scenario.request(title);
        String wanted = situation.strip();
        frame.eventually(() -> {
            Request now = stay.request(taskId);
            if (!now.situation().equals(wanted)) {
                return "後端「" + title + "」是「" + now.situation() + "」，不是「" + wanted + "」";
            }
            if ("待派單".equals(wanted) && !"frontdesk".equals(now.dept())) {
                return "後端「" + title + "」在待派單，部門卻是 " + now.dept();
            }
            Optional<String> assigneeDept = assigneeDept(taskId);
            if (UNCLAIMED.contains(wanted) && assigneeDept.isPresent()) {
                return "後端「" + title + "」是「" + wanted + "」，卻有負責人";
            }
            if (CLAIMED_DEPT.containsKey(wanted) && !assigneeDept.equals(Optional.of(CLAIMED_DEPT.get(wanted)))) {
                return "後端「" + title + "」是「" + wanted + "」，負責人的部門卻是 " + assigneeDept.orElse("（沒有負責人）");
            }
            return null;
        });
        Request now = stay.request(taskId);
        if (GUEST_WITHDREW.equals(wanted)) {
            boolean fieldDept = "housekeeping".equals(now.dept()) || "engineering".equals(now.dept());
            assertThat(!fieldDept || !inDeptPool(now.dept(), taskId), "「" + title + "」還在 " + now.dept() + " 的待認領清單上");
            assertThat(withdrewPushedTo(now.dept(), taskId), now.dept() + " 的同仁沒有收到「" + title + "」住客收回的推播");
        }
        if (UNCLAIMED.contains(wanted) || CLAIMED_DEPT.containsKey(wanted)) {
            int before = whenStart.lastSeqBefore(taskId);
            int latest = lastSeq(taskId);
            assertThat(latest == before, "這次 When 在後端替「" + title + "」留下了事件（序號 " + before + " → " + latest + "）");
        }
    }

    @那麼("後端 {string} 的星等是 {int}")
    public void backendRatingIs(String title, int stars) {
        UUID taskId = scenario.request(title);
        frame.eventually(() -> {
            Integer rating = stay.request(taskId).rating();
            return Integer.valueOf(stars).equals(rating) ? null : "後端「" + title + "」的星等是 " + rating + "，不是 " + stars;
        });
        JsonNode shown = guestView(taskId);
        assertThat(shown.path("rating").asInt(0) == stars,
                "住客介面讀回來的「" + title + "」星等是 " + shown.path("rating") + "，不是 " + stars);
    }

    @那麼("五顆星都是空心的")
    public void allStarsEmpty() {
        frame.eventually(() -> {
            Optional<Locator> card = request.ratingCard();
            if (card.isEmpty()) {
                return "進度頁上沒有評分卡";
            }
            if (request.stars() != 5) {
                return "評分卡上看得到 " + request.stars() + " 顆星，不是五顆";
            }
            if (!request.checkedStars().isEmpty()) {
                return "評分卡上已經選了第 " + request.checkedStars() + " 顆星";
            }
            List<StarShape> shapes = request.starShapes();
            if (!shapes.equals(StarShape.pattern(0))) {
                return "五顆星畫成 " + shapes + "，應該五顆都是空心";
            }
            return card.get().innerText().contains(RATE_PROMPT) ? null : "評分卡上沒有「" + RATE_PROMPT + "」";
        });
    }

    @那麼("只有前 {int} 顆星是實心的")
    public void firstStarsFilled(int stars) {
        frame.eventually(() -> {
            List<Integer> checked = request.checkedStars();
            if (!checked.equals(List.of(stars))) {
                return "選中的星是 " + checked + "，應該是第 " + stars + " 顆";
            }
            List<StarShape> shapes = request.starShapes();
            return shapes.equals(StarShape.pattern(stars)) ? null
                    : "五顆星畫成 " + shapes + "，應該是前 " + stars + " 顆實心、其餘空心";
        });
    }

    @那麼("評分卡上沒有留言欄")
    public void noCommentField() {
        frame.eventually(() -> {
            Optional<Locator> card = request.ratingCard();
            if (card.isEmpty()) {
                return "進度頁上沒有評分卡";
            }
            int inputs = RequestPage.textInputsIn(card.get());
            return inputs == 0 ? null : "評分卡裡有 " + inputs + " 個文字輸入框";
        });
    }

    @那麼("進度頁有評分卡")
    public void ratingCardShown() {
        frame.eventually(() -> {
            Optional<Locator> card = request.ratingCard();
            if (card.isEmpty()) {
                return "進度頁上沒有「" + RequestPage.RATING_QUESTION + "」的評分卡";
            }
            if (request.stars() != 5) {
                return "評分卡上看得到 " + request.stars() + " 顆星，不是五顆";
            }
            if (request.button(RequestPage.RATING_SUBMIT).count() == 0) {
                return "評分卡上沒有「" + RequestPage.RATING_SUBMIT + "」";
            }
            Optional<Locator> steps = request.fourSteps(frame.language());
            if (steps.isPresent()) {
                BoundingBox stepsBox = steps.get().boundingBox();
                BoundingBox cardBox = card.get().boundingBox();
                if (cardBox.y < stepsBox.y + stepsBox.height - 1) {
                    return "評分卡不在四步進度的下方";
                }
            }
            return null;
        });
        Request backend = stay.request(onScreenRequest());
        assertThat(backend.ratable(),
                "後端「" + backend.title() + "」是 " + backend.state() + "、星等 " + backend.rating() + "、回覆 "
                        + backend.answer() + "，不該有評分卡");
    }

    private UUID onScreenRequest() {
        String path = frame.path();
        return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
    }

    private boolean backStillPressable() {
        Locator back = frame.backControl(frame.titleBar().first());
        try {
            back.first().click(new Locator.ClickOptions().setTrial(true).setTimeout(1_000));
            return true;
        } catch (PlaywrightException covered) {
            return false;
        }
    }

    private Optional<String> assigneeDept(UUID taskId) {
        List<Map<String, Object>> rows = world.backend().queryRows("""
                SELECT s.dept FROM tasks t JOIN staff_accounts s ON s.tenant_id = t.tenant_id AND s.id = t.assignee_id
                 WHERE t.tenant_id = ? AND t.id = ?
                """, world.defaults().tenantId(), taskId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(String.valueOf(rows.get(0).get("dept")));
    }

    private int lastSeq(UUID taskId) {
        Object seq = world.backend().queryRows(
                "SELECT coalesce(max(seq), 0) AS seq FROM task_events WHERE tenant_id = ? AND task_id = ?",
                world.defaults().tenantId(), taskId).get(0).get("seq");
        return ((Number) seq).intValue();
    }

    private boolean inDeptPool(String dept, UUID taskId) {
        JsonNode pool = world.backend().getJson("/pool?dept=" + dept, world.staff(WhenStart.BACKEND_READER));
        for (JsonNode item : pool.path("items")) {
            if (taskId.toString().equals(item.path("taskId").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean withdrewPushedTo(String dept, UUID taskId) {
        return !world.backend().queryRows("""
                SELECT 1 FROM outbox o
                  JOIN staff_accounts s ON s.tenant_id = o.tenant_id
                   AND s.id::text = coalesce(o.payload->'payload'->>'recipientStaffId', o.payload->>'recipientStaffId')
                 WHERE o.tenant_id = ? AND o.kind = 'push' AND o.idempotency_key LIKE ? AND s.dept = ?
                """, world.defaults().tenantId(), "guest_withdrew:" + taskId + ":%", dept).isEmpty();
    }

    private JsonNode guestView(UUID taskId) {
        String session = scenario.session(world.page()).orElseGet(() ->
                world.backend().guestScan(phone.qrToken(scenario.phoneRoom())).sessionToken());
        APIResponse response = world.page().request().get(WebApp.url("/guest/requests"),
                RequestOptions.create().setHeader("X-Guest-Session", session));
        assertThat(response.ok(), "住客讀自己的需求：GET /guest/requests -> HTTP " + response.status() + " " + response.text());
        try {
            for (JsonNode item : JSON.readTree(response.text())) {
                if (taskId.toString().equals(item.path("id").asText())) {
                    return item;
                }
            }
        } catch (Exception e) {
            throw new AssertionError("讀不懂 GET /guest/requests 的回應：" + response.text(), e);
        }
        throw new AssertionError("GET /guest/requests 沒有這張需求：" + taskId);
    }

    private static void assertThat(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
