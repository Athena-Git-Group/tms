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

public class AnswerThenSteps {

    private static final double ASSERT_TIMEOUT_MS = 5_000;

    private static final String QUICK_REPLY_READER = "林佩君";

    private final WebWorld world;

    private final AnswerPanel answer;

    public AnswerThenSteps(WebWorld world) {
        this.world = world;
        this.answer = new AnswerPanel(world);
    }

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

    private String replyValue() {
        Locator box = answer.replyBox();
        return box.count() == 1 ? box.inputValue() : null;
    }

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
