package com.athena.web.bdd.steps.answer;

import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.web.bdd.pages.AnswerPanel;
import com.athena.web.bdd.support.WebWorld;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import io.cucumber.java.zh_tw.那麼;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 「當場回答」模組的 Then 句型：回覆框、字數、常用回覆的樣子，面板關了沒有，以及後端記下的回答。
 *
 * <p>畫面更新要時間，所以每一句都在 5 秒內反覆檢查，最後一次還不成立才算失敗。
 * 寫「後端」的那一句另外以 {@code BackendChannel} 讀資料庫與住客端點，畫面以外的權威狀態也要成立。
 */
public class AnswerThenSteps {

    /** 畫面斷言最多等這麼久，是共通實作約定的輪詢上限。 */
    private static final double ASSERT_TIMEOUT_MS = 5_000;

    /** 常用回覆是飯店設定，全館共用；讀後端那一份時用預設的櫃檯帳號，誰讀都一樣。 */
    private static final String QUICK_REPLY_READER = "林佩君";

    private final WebWorld world;

    private final AnswerPanel answer;

    public AnswerThenSteps(WebWorld world) {
        this.world = world;
        this.answer = new AnswerPanel(world);
    }

    /** 輸入框的值是空字串，提示字與右下角的字數照字面相符。 */
    @那麼("{string} 輸入框是空的，提示字是 {string}，字數是 {string}")
    public void fieldIsEmpty(String label, String placeholder, String countText) {
        eventually("「" + label + "」輸入框不是預期的樣子", () -> {
            Locator field = answer.field(label);
            if (field.count() != 1) {
                return "面板上找不到名稱是「" + label + "」的輸入框（找到 " + field.count() + " 個）";
            }
            String value = field.inputValue();
            if (!value.isEmpty()) {
                return "輸入框應該是空的，實際是「" + value + "」";
            }
            String shownPlaceholder = field.getAttribute("placeholder");
            if (!placeholder.equals(shownPlaceholder)) {
                return "提示字應該是「" + placeholder + "」，實際是「" + shownPlaceholder + "」";
            }
            return countProblem(countText);
        });
    }

    /**
     * 常用回覆剛好五則，名稱與順序相符，而且就是後端常用回覆（{@code listDeskQuickReplies}）回應的標題順序：
     * 畫面不是自己寫死一份，而是照飯店的設定排。
     */
    @那麼("常用回覆依序是 {string}、{string}、{string}、{string}、{string}")
    public void quickRepliesInOrder(String one, String two, String three, String four, String five) {
        List<String> expected = List.of(one, two, three, four, five);
        eventually("常用回覆的名稱或順序不對", () -> {
            List<String> shown = shownQuickReplyTitles();
            return shown.equals(expected) ? null : "畫面上依序是 " + shown + "，應該是 " + expected;
        });
        CreatedStaff reader = world.staff(QUICK_REPLY_READER);
        List<String> fromBackend = new ArrayList<>();
        for (JsonNode reply : world.backend().getJson("/desk/quick-replies", reader)) {
            fromBackend.add(reply.path("title").asText());
        }
        if (!fromBackend.equals(expected)) {
            throw new AssertionError("後端常用回覆的標題依序是 " + fromBackend + "，畫面應該照它排，規格寫的是 " + expected);
        }
    }

    /** 回覆框的值與字數照字面相符，回覆前後的空白也要一樣。 */
    @那麼("回覆框的內容是 {string}，字數是 {string}")
    public void replyBoxShows(String reply, String countText) {
        eventually("回覆框不是預期的樣子", () -> {
            String value = replyValue();
            if (value == null) {
                return "面板上找不到唯一的回覆框「回覆住客」";
            }
            if (!reply.equals(value)) {
                return "回覆框的內容應該是「" + reply + "」，實際是「" + value + "」";
            }
            return countProblem(countText);
        });
    }

    /** 回覆框的字數剛好是上限，多打的字沒有進去；字數顯示也相符。 */
    @那麼("回覆框裡只有 {int} 個字，字數是 {string}")
    public void replyBoxHoldsOnly(int length, String countText) {
        eventually("回覆框的字數不對", () -> {
            String value = replyValue();
            if (value == null) {
                return "面板上找不到唯一的回覆框「回覆住客」";
            }
            int actual = value.codePointCount(0, value.length());
            if (actual != length) {
                return "回覆框裡應該只有 " + length + " 個字，實際有 " + actual + " 個";
            }
            return countProblem(countText);
        });
    }

    /** 那一則是選中的樣子，其餘四則不是。 */
    @那麼("常用回覆 {string} 是選中的樣子")
    public void quickReplySelected(String title) {
        eventually("常用回覆選中的樣子不對", () -> {
            List<String> titles = shownQuickReplyTitles();
            if (!titles.contains(title)) {
                return "面板上沒有常用回覆「" + title + "」，看得到的是 " + titles;
            }
            List<String> selected = selectedQuickReplyTitles();
            return selected.equals(List.of(title)) ? null
                    : "只有「" + title + "」應該是選中的樣子，實際選中的是 " + selected;
        });
    }

    /** 沒有任何一則常用回覆是選中的樣子。 */
    @那麼("五則常用回覆都不是選中的樣子")
    public void noQuickReplySelected() {
        eventually("還有常用回覆是選中的樣子", () -> {
            int count = answer.quickReplies().count();
            if (count != 5) {
                return "面板上應該有五則常用回覆，實際有 " + count + " 則";
            }
            List<String> selected = selectedQuickReplyTitles();
            return selected.isEmpty() ? null : "選中的是 " + selected;
        });
    }

    /** 畫面上沒有當場回答面板，也沒有它蓋住畫面的遮罩。 */
    @那麼("當場回答面板是關的")
    public void panelClosed() {
        eventually("當場回答面板沒有關", () -> {
            int panels = answer.panel().count();
            if (panels > 0) {
                return "畫面上還有 " + panels + " 個當場回答面板";
            }
            List<String> overlays = answer.coveringOverlays();
            return overlays.isEmpty() ? null : "畫面還被遮罩蓋著：" + overlays;
        });
    }

    /**
     * 後端的單：狀態是 {@code done}、{@code tasks.answer} 完全等於回覆（沒有前後空白）、{@code dept} 仍是櫃檯，
     * 當場回答的事件（{@code answered}）剛好一筆、操作者是這位員工；住客的進度頁也讀得到同一段回覆。
     * 畫面送出的請求可能還在路上，所以後端也在 5 秒內反覆讀。
     */
    @那麼("後端 {string} 的單已由 {string} 當場回答，回覆是 {string}")
    public void backendAnswered(String roomNo, String staffName, String reply) {
        UUID taskId = world.tasks().idOf(roomNo);
        CreatedStaff staff = world.staff(staffName);
        eventually("後端 " + roomNo + " 的單不是由" + staffName + "當場回答的樣子", () -> {
            Map<String, Object> task = world.backend().taskRow(taskId);
            if (!"done".equals(task.get("state"))) {
                return "這張單的狀態應該是 done，實際是 " + task.get("state");
            }
            if (!reply.equals(task.get("answer"))) {
                return "tasks.answer 應該是「" + reply + "」，實際是「" + task.get("answer") + "」";
            }
            if (!"frontdesk".equals(task.get("dept"))) {
                return "dept 應該仍是 frontdesk，實際是 " + task.get("dept");
            }
            List<Map<String, Object>> answered = world.backend().queryRows("""
                    SELECT actor_id, actor_role FROM task_events
                     WHERE tenant_id = ? AND task_id = ? AND type = 'answered'
                    """, world.backend().ensureTenant(), taskId);
            if (answered.size() != 1) {
                return "answered 事件應該剛好一筆，實際有 " + answered.size() + " 筆";
            }
            if (!Objects.equals(staff.id(), answered.get(0).get("actor_id"))) {
                return "answered 事件的操作者應該是" + staffName + "（" + staff.id() + "），實際是 "
                        + answered.get(0).get("actor_id") + "（" + answered.get(0).get("actor_role") + "）";
            }
            return guestSeesProblem(roomNo, taskId, reply);
        });
    }

    /** 住客的進度頁（{@code listGuestRequests}）讀到的這張單要帶著同一段回覆。 */
    private String guestSeesProblem(String roomNo, UUID taskId, String reply) {
        for (JsonNode request : world.backend().guestRequests(roomNo)) {
            if (taskId.toString().equals(request.path("id").asText())) {
                String guestSees = request.path("answer").isNull() ? null : request.path("answer").asText(null);
                return reply.equals(guestSees) ? null
                        : "住客的進度頁應該看到回覆「" + reply + "」，實際是「" + guestSees + "」";
            }
        }
        return "住客的進度頁讀不到這張單（" + taskId + "）";
    }

    /** 回覆框現在的值；面板上找不到唯一的回覆框時是 null，不等它出現，交給外層的輪詢再讀一次。 */
    private String replyValue() {
        Locator box = answer.replyBox();
        return box.count() == 1 ? box.inputValue() : null;
    }

    /** 字數顯示照字面相符；不相符時回原因。 */
    private String countProblem(String countText) {
        Locator count = answer.charCount();
        if (count.count() != 1) {
            return "面板上找不到唯一的字數顯示（找到 " + count.count() + " 個）";
        }
        String shown = count.innerText().strip();
        return countText.equals(shown) ? null : "字數應該是「" + countText + "」，實際是「" + shown + "」";
    }

    private List<String> shownQuickReplyTitles() {
        return answer.quickReplies().allInnerTexts().stream().map(String::strip).toList();
    }

    private List<String> selectedQuickReplyTitles() {
        Locator replies = answer.quickReplies();
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < replies.count(); i++) {
            Locator reply = replies.nth(i);
            if (AnswerPanel.selected(reply)) {
                selected.add(reply.innerText().strip());
            }
        }
        return selected;
    }

    private void eventually(String what, Supplier<String> problem) {
        String[] last = {null};
        try {
            world.page().waitForCondition(() -> (last[0] = problem.get()) == null,
                    new Page.WaitForConditionOptions().setTimeout(ASSERT_TIMEOUT_MS));
        } catch (TimeoutError timeout) {
            throw new AssertionError(what + "：" + last[0], timeout);
        }
    }
}
