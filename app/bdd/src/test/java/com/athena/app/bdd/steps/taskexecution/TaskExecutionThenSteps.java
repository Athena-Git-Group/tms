package com.athena.app.bdd.steps.taskexecution;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.java.zh_tw.那麼;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TaskExecutionThenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    private static final int TIMER_STILLNESS_WAIT_SECONDS = 5;

    private final AppiumWorld world;

    public TaskExecutionThenSteps(AppiumWorld world) {
        this.world = world;
    }

    @那麼("任務頁顯示已做 {int} 分鐘，且計時沒有在走")
    public void showsWorkedMinutesPausedTimer(int minutes) {
        WebElement element = world.waitForTestId("task.hero.paused-elapsed", SCREEN_TIMEOUT);
        String expected = element.getAttribute("label");
        long shownMinutes = timerSecondsOf(expected) / 60;
        if (shownMinutes != minutes) {
            throw new AssertionError("任務頁的已做時間不符：預期 " + minutes
                    + " 分鐘，實際讀到「" + expected + "」");
        }

        Map<String, Object> row = world.taskRowByRoomNo(world.currentTaskRoomNo());
        long workedSeconds = ((Number) row.get("worked_sec")).longValue();
        if (workedSeconds / 60 != minutes) {
            throw new AssertionError("後端累計的已做時間不符：預期 " + minutes
                    + " 分鐘，實際 " + workedSeconds + " 秒");
        }
        if (row.get("segment_started_at") != null) {
            throw new AssertionError("後端這一筆還有一段計時在走（segment_started_at 不是空的），"
                    + "停住的單不該有正在累計的段落");
        }

        sleepSeconds(TIMER_STILLNESS_WAIT_SECONDS);
        String later = world.waitForTestId("task.hero.paused-elapsed", SCREEN_TIMEOUT)
                .getAttribute("label");
        if (!expected.equals(later)) {
            throw new AssertionError("任務頁的已做時間還在走：" + TIMER_STILLNESS_WAIT_SECONDS
                    + " 秒後從「" + expected + "」變成「" + later + "」");
        }
    }

    @那麼("任務頁顯示打掃步驟條，目前停在 {string}")
    public void showsCleaningStepTrack(String step) {
        Map<String, Object> row = world.taskRowByRoomNo(world.currentTaskRoomNo());
        if (!"cleaning".equals(String.valueOf(row.get("type")))) {
            throw new AssertionError("這一筆不是打掃類任務，實際類別 " + row.get("type"));
        }
        boolean waived = Boolean.TRUE.equals(row.get("inspection_waived"))
                || "waived".equals(String.valueOf(row.get("inspection")));
        assertStepTrack(List.of("已接", "進行中", "完成", waived ? "免查房" : "查房"), step, row);
    }

    @那麼("任務頁顯示備品步驟條，目前停在 {string}")
    public void showsAmenityStepTrack(String step) {
        Map<String, Object> row = world.taskRowByRoomNo(world.currentTaskRoomNo());
        if (!"amenity".equals(String.valueOf(row.get("type")))) {
            throw new AssertionError("這一筆不是備品類任務，實際類別 " + row.get("type"));
        }
        assertStepTrack(List.of("已接", "進行中", "送達"), step, row);
    }

    private void assertStepTrack(List<String> expectedSteps, String step, Map<String, Object> row) {
        if (!expectedSteps.contains(step)) {
            throw new IllegalArgumentException(
                    "步驟「" + step + "」不在這種任務的步驟條裡：" + expectedSteps);
        }
        WebElement track = world.waitForTestId("task.step-track", SCREEN_TIMEOUT);
        String current = track.getAttribute("label");
        if (!step.equals(current)) {
            throw new AssertionError(
                    "步驟條目前停的步驟不符：預期「" + step + "」，實際「" + current + "」");
        }
        for (String expected : expectedSteps) {
            world.waitForTestId("task.step-track." + expected, SCREEN_TIMEOUT);
        }
        String state = String.valueOf(row.get("state"));
        String stepFromBackend = stepOf(state, expectedSteps);
        if (!step.equals(stepFromBackend)) {
            throw new AssertionError("步驟條與後端狀態對不上：後端 state 是 " + state
                    + "，對照出來的步驟是「" + stepFromBackend + "」，畫面上卻是「" + step + "」");
        }
    }

    private static String stepOf(String state, List<String> steps) {
        return switch (state) {
            case "doing", "interrupted" -> "進行中";
            case "done" -> steps.get(2);
            case "inspected" -> steps.get(steps.size() - 1);
            default -> steps.get(0);
        };
    }

    @那麼("任務頁只顯示一個主按鈕，文字為 {string}")
    public void showsOnlyOnePrimaryButton(String buttonText) {
        world.waitForTestId("task.primary-button", SCREEN_TIMEOUT);
        List<WebElement> buttons = world.driver().findElements(world.byTestId("task.primary-button"));
        if (buttons.size() != 1) {
            throw new AssertionError("任務頁的主按鈕應該只有一顆，實際有 " + buttons.size() + " 顆");
        }
        String actual = buttons.get(0).getAttribute("label");
        if (!buttonText.equals(actual)) {
            throw new AssertionError("主按鈕文字不符：預期「" + buttonText + "」，實際「" + actual + "」");
        }
    }

    @那麼("{string} 的 SOP 進度顯示 {string}")
    public void sopProgressShows(String task, String keyword) {
        WebElement element = world.waitForTestId("task.sop.progress", SCREEN_TIMEOUT);
        String actual = element.getAttribute("label");
        if (actual == null || !actual.contains(keyword)) {
            throw new AssertionError("「" + task + "」的 SOP 進度文字不含「" + keyword
                    + "」：實際「" + actual + "」");
        }

        UUID taskId = (UUID) world.taskRowByRoomNo(task).get("id");
        long handled = ((Number) world.backend().queryRows(
                "SELECT count(*) AS handled FROM task_sop_checks"
                        + " WHERE tenant_id = ? AND task_id = ? AND checked",
                world.backend().ensureTenant(), taskId).get(0).get("handled")).longValue();
        long shown = Long.parseLong(actual.substring(0, actual.indexOf('/')).trim());
        if (shown != handled) {
            throw new AssertionError("「" + task + "」的 SOP 進度分子不符：畫面上是 " + shown
                    + "，後端已勾選的是 " + handled + " 項");
        }
    }

    @那麼("任務頁顯示 {string}")
    public void taskDetailShows(String text) {
        world.waitForTestId("task.hero.overtime-chip", SCREEN_TIMEOUT);
        String actual = world.waitForLabelOf("task.hero.overtime-chip", text, SCREEN_TIMEOUT);
        if (!text.equals(actual)) {
            throw new AssertionError("任務頁計時區的超時提示沒有在 " + SCREEN_TIMEOUT.getSeconds()
                    + " 秒內變成「" + text + "」，最後讀到「" + actual
                    + "」——徽章是不是停在進頁或上次向後端取資料那一刻的已做時間？");
        }

        Map<String, Object> row = world.taskRowByRoomNo(world.currentTaskRoomNo());
        long estMinutes = ((Number) row.get("est_min")).longValue();
        long workedMinutes = workedMinutesOf(row);
        String expected = "超時 " + (workedMinutes - estMinutes) + " 分";
        if (!expected.equals(text)) {
            throw new AssertionError("超時分鐘數與後端算出來的不符：目前已做 " + workedMinutes
                    + " 分、預估 " + estMinutes + " 分，應該是「" + expected + "」");
        }
    }

    private long workedMinutesOf(Map<String, Object> row) {
        return workedSecondsOf(row) / 60;
    }

    private long workedSecondsOf(Map<String, Object> row) {
        java.time.OffsetDateTime injected = world.injectedDeviceClock();
        java.time.Instant now = injected == null ? java.time.Instant.now() : injected.toInstant();
        long seconds = ((Number) row.get("worked_sec")).longValue();
        Object segmentStartedAt = row.get("segment_started_at");
        if (segmentStartedAt instanceof java.sql.Timestamp timestamp) {
            seconds += Duration.between(timestamp.toInstant(), now).getSeconds();
        } else if (segmentStartedAt instanceof java.time.OffsetDateTime offsetDateTime) {
            seconds += Duration.between(offsetDateTime.toInstant(), now).getSeconds();
        }
        return seconds;
    }

    @那麼("完成畫面顯示這次用時 {int} 分、當初預估 {int} 分")
    public void celebrationShowsActualAndEstimate(int actualMinutes, int estimatedMinutes) {
        world.waitForTestId("celebrate.title", SCREEN_TIMEOUT);
        String tile = world.labelOf("celebrate.actual-minutes");
        if (tile == null || !tile.contains(String.valueOf(actualMinutes))
                || !tile.contains(String.valueOf(estimatedMinutes))) {
            throw new AssertionError("完成畫面的用時區塊應同時含這次用時 " + actualMinutes
                    + " 與當初預估 " + estimatedMinutes + "，實際讀到「" + tile + "」");
        }

        Map<String, Object> row = world.taskRowByRoomNo(world.currentTaskRoomNo());
        long workedMinutes = ((Number) row.get("worked_sec")).longValue() / 60;
        if (workedMinutes != actualMinutes) {
            throw new AssertionError("後端記的實際時間不符：預期 " + actualMinutes
                    + " 分，實際 " + workedMinutes + " 分");
        }
        long estMinutes = ((Number) row.get("est_min")).longValue();
        if (estMinutes != estimatedMinutes) {
            throw new AssertionError("後端記的預估時間不符：預期 " + estimatedMinutes
                    + " 分，實際 " + estMinutes + " 分");
        }
    }

    private void assertLabel(String testId, String expected) {
        String actual = world.waitForTestId(testId, SCREEN_TIMEOUT).getAttribute("label");
        if (!expected.equals(actual)) {
            throw new AssertionError(testId + " 的文字不符：預期「" + expected
                    + "」，實際「" + actual + "」");
        }
    }

    @那麼("後端沒有對 {string} 送出任何推播")
    public void noPushSentTo(String staffName) {
        UUID staffId = world.staff(staffName).id();

        List<String> pushes = world.backend().pushTitlesSentTo(staffId);
        if (!pushes.isEmpty()) {
            throw new AssertionError("後端對「" + staffName + "」送出了 " + pushes.size()
                    + " 筆推播（" + pushes + "），這個情境不該有任何一筆");
        }
    }

    @那麼("任務頁顯示停住原因 {string}")
    public void showsHoldReason(String reason) {
        List<Map<String, Object>> rows = world.backend().queryRows(
                "SELECT id FROM tasks WHERE tenant_id = ? AND hold_reason = ?",
                world.backend().ensureTenant(), reason);
        if (rows.size() != 1) {
            throw new AssertionError("後端停住原因是「" + reason + "」的任務應該剛好一筆，實際 "
                    + rows.size() + " 筆");
        }
        WebElement element = world.waitForTestId("task.status-row.hold-reason", SCREEN_TIMEOUT);
        String actual = element.getAttribute("label");
        if (!reason.equals(actual)) {
            throw new AssertionError(
                    "任務頁的停住原因不符：預期「" + reason + "」，實際「" + actual + "」");
        }
    }

    @那麼("延後面板的送出鈕為 {string}")
    public void deferPanelSubmitButtonState(String enabledOrDisabled) {
        boolean expected = switch (enabledOrDisabled) {
            case "啟用" -> true;
            case "禁用" -> false;
            default -> throw new IllegalArgumentException(
                    "送出鈕狀態只收「啟用」或「禁用」，收到：" + enabledOrDisabled);
        };
        WebElement button = world.waitForTestId("hold.confirm-button", SCREEN_TIMEOUT);
        boolean actual = Boolean.parseBoolean(button.getAttribute("enabled"));
        if (expected != actual) {
            throw new AssertionError("延後面板送出鈕的狀態不符：預期「" + enabledOrDisabled
                    + "」，實際 enabled=" + actual);
        }
    }

    @那麼("延後面板顯示字數 {string}")
    public void deferPanelCharCount(String countText) {
        WebElement element = world.waitForTestId("hold.char-count", SCREEN_TIMEOUT);
        String actual = element.getAttribute("label");
        if (!countText.equals(actual)) {
            throw new AssertionError(
                    "延後面板的字數提示不符：預期「" + countText + "」，實際「" + actual + "」");
        }
    }

    private static void sleepSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待畫面維持不變時被中斷", e);
        }
    }

    @那麼("完成畫面只顯示這次用時 {int} 分")
    public void celebrationShowsActualMinutesOnly(int actualMinutes) {
        world.waitForTestId("celebrate.title", SCREEN_TIMEOUT);
        String tile = world.labelOf("celebrate.actual-minutes");
        if (tile == null || !tile.contains(String.valueOf(actualMinutes))) {
            throw new AssertionError("完成畫面的用時區塊應含這次用時 " + actualMinutes
                    + " 分，實際讀到「" + tile + "」");
        }
        if (tile.contains("預估")) {
            throw new AssertionError("這一筆沒有預估分鐘，完成畫面的用時區塊不該出現預估欄位，"
                    + "實際讀到「" + tile + "」");
        }
        List<WebElement> legacyEstimate =
                world.driver().findElements(world.byTestId("celebrate.estimate-minutes"));
        if (!legacyEstimate.isEmpty()) {
            throw new AssertionError("完成畫面上出現了 celebrate.estimate-minutes 這一塊（"
                    + legacyEstimate.size() + " 個）；沒有預估分鐘時這個欄位必須整個不存在，"
                    + "不是留著顯示空白或 0");
        }

        Map<String, Object> row = world.taskRowByRoomNo(world.currentTaskRoomNo());
        long workedMinutes = ((Number) row.get("worked_sec")).longValue() / 60;
        if (workedMinutes != actualMinutes) {
            throw new AssertionError("後端記的實際時間不符：預期 " + actualMinutes
                    + " 分，實際 " + workedMinutes + " 分");
        }
        if (row.get("est_min") != null) {
            throw new AssertionError("這一句只用於沒有預估分鐘的任務，後端這一筆的預估卻是 "
                    + row.get("est_min") + " 分");
        }
    }

    @那麼("完成畫面說明 {string}")
    public void celebrationSublineIs(String subline) {
        List<String> allowed = List.of("免查房，直接完成", "已通知主管查房", "住客會收到完成通知");
        if (!allowed.contains(subline)) {
            throw new IllegalArgumentException(
                    "完成畫面的說明只有三種說法：" + allowed + "，收到：" + subline);
        }

        world.waitForTestId("celebrate.title", SCREEN_TIMEOUT);
        world.waitForTestId("celebrate.subline", SCREEN_TIMEOUT);
        String actual = world.labelOf("celebrate.subline");
        if (!subline.equals(actual)) {
            throw new AssertionError(
                    "完成畫面的說明不符：預期「" + subline + "」，實際「" + actual + "」");
        }

        Map<String, Object> row = world.taskRowByRoomNo(world.currentTaskRoomNo());
        String fromBackend = sublineOf(row);
        if (!subline.equals(fromBackend)) {
            throw new AssertionError("完成畫面的說明與後端對不上：後端 type 是 " + row.get("type")
                    + "、inspection_waived 是 " + row.get("inspection_waived")
                    + "，對照出來的說明是「" + fromBackend + "」，畫面上卻是「" + subline + "」");
        }
    }

    private static String sublineOf(Map<String, Object> row) {
        if (!"cleaning".equals(String.valueOf(row.get("type")))) {
            return "住客會收到完成通知";
        }
        boolean waived = Boolean.TRUE.equals(row.get("inspection_waived"))
                || "waived".equals(String.valueOf(row.get("inspection")));
        return waived ? "免查房，直接完成" : "已通知主管查房";
    }

    @那麼("完成畫面的標題帶出房號 {string}")
    public void celebrationTitleCarriesRoomNo(String roomNo) {
        String completedRoomNo = world.currentTaskRoomNo();
        if (!completedRoomNo.equals(roomNo)) {
            throw new IllegalArgumentException("句子裡的房號「" + roomNo
                    + "」不是這次完成的那一間，剛完成的是「" + completedRoomNo + "」");
        }

        Map<String, Object> row = world.taskRowByRoomNo(completedRoomNo);
        String state = String.valueOf(row.get("state"));
        if (!"done".equals(state) && !"inspected".equals(state)) {
            throw new AssertionError("房號「" + completedRoomNo + "」的任務在後端還沒結案（state 是 "
                    + state + "），標題帶出的房號無從對應剛完成的那張單");
        }

        world.waitForTestId("celebrate.title", SCREEN_TIMEOUT);
        String title = world.labelOf("celebrate.title");
        if (title == null || !title.contains(completedRoomNo)) {
            throw new AssertionError("完成畫面的標題沒有帶出房號「" + completedRoomNo
                    + "」：實際讀到「" + title + "」");
        }
    }

    @那麼("完成畫面的下一步指向 {string}")
    public void celebrationNextStepPointsToRoom(String roomNo) {
        world.waitForTestId("celebrate.screen", SCREEN_TIMEOUT);
        String label = world.waitForTestId("celebrate.next-button", SCREEN_TIMEOUT)
                .getAttribute("label");
        if (label == null || !label.contains(roomNo)) {
            throw new AssertionError("完成畫面的實心主按鈕文字應該含下一間的房號「" + roomNo
                    + "」，實際讀到「" + label + "」");
        }

        List<Map<String, Object>> accepted = world.backend().queryRows(
                """
                SELECT r.room_no AS room_no FROM tasks t
                  JOIN rooms r ON r.id = t.room_id AND r.tenant_id = t.tenant_id
                 WHERE t.tenant_id = ? AND t.assignee_id = ? AND t.state = 'accepted'
                 ORDER BY t.redo_count DESC,
                          CASE WHEN t.type = 'cleaning' THEN 1 ELSE 0 END,
                          r.room_no
                """,
                world.backend().ensureTenant(), world.loggedInStaff().id());

        String expected;
        String group;
        if (!accepted.isEmpty()) {
            expected = String.valueOf(accepted.get(0).get("room_no"));
            group = "待開始";
        } else {
            List<Map<String, Object>> unfinished = world.backend().queryRows(
                    """
                    SELECT r.room_no AS room_no FROM tasks t
                      JOIN rooms r ON r.id = t.room_id AND r.tenant_id = t.tenant_id
                     WHERE t.tenant_id = ? AND t.assignee_id = ?
                       AND t.state IN ('doing', 'interrupted', 'deferred')
                       AND (t.work_date IS NULL OR t.work_date = ?)
                     ORDER BY array_position(
                                  ARRAY['doing', 'interrupted', 'deferred'], t.state),
                              r.room_no
                    """,
                    world.backend().ensureTenant(), world.loggedInStaff().id(),
                    java.sql.Date.valueOf(java.time.LocalDate.now()));
            if (unfinished.isEmpty()) {
                throw new AssertionError("後端這位員工名下既沒有待開始、也沒有還沒做完的單，"
                        + "下一步不該指向「" + roomNo + "」");
            }
            expected = String.valueOf(unfinished.get(0).get("room_no"));
            group = "還沒做完";
        }
        if (!roomNo.equals(expected)) {
            throw new AssertionError("下一步指向的房號與後端對不上：後端" + group
                    + "的第一張是「" + expected + "」，畫面上卻指向「" + roomNo + "」");
        }
    }

    @那麼("完成畫面提示今天排定的房都清完了，下一步是去待認領")
    public void celebrationSaysAllClearAndNextIsPool() {
        world.waitForTestId("celebrate.screen", SCREEN_TIMEOUT);

        String note = world.waitForTestId("celebrate.all-done-note", SCREEN_TIMEOUT)
                .getAttribute("label");
        if (note == null || !note.contains("都清完了")) {
            throw new AssertionError("完成畫面的提示句應該講「都清完了」，實際讀到「" + note + "」");
        }

        WebElement nextButton = world.waitForTestId("celebrate.next-button", SCREEN_TIMEOUT);
        if (!Boolean.parseBoolean(nextButton.getAttribute("enabled"))) {
            throw new AssertionError("前往待認領的主按鈕應該還能按，實際是禁用的——"
                    + "這一版不是把主按鈕拿掉，而是把它改成去待認領");
        }
        String buttonText = nextButton.getAttribute("label");
        if (buttonText == null || !buttonText.contains("待認領")) {
            throw new AssertionError(
                    "主按鈕文字應該講「待認領」，實際讀到「" + buttonText + "」");
        }
        if (java.util.regex.Pattern.compile("\\d{2,}").matcher(buttonText).find()) {
            throw new AssertionError("主按鈕文字裡出現了房號「" + buttonText
                    + "」——這一版的下一步是待認領，不該指向某一間房");
        }

        assertNoUnfinishedTasksToday();
    }

    private void assertNoUnfinishedTasksToday() {
        List<Map<String, Object>> leftovers = world.backend().queryRows(
                """
                SELECT t.state AS state, r.room_no AS room_no
                  FROM tasks t JOIN rooms r ON r.id = t.room_id AND r.tenant_id = t.tenant_id
                 WHERE t.tenant_id = ? AND t.assignee_id = ?
                   AND t.state IN ('accepted', 'doing', 'interrupted', 'deferred')
                   AND (t.work_date IS NULL OR t.work_date = ?)
                 ORDER BY r.room_no
                """,
                world.backend().ensureTenant(),
                world.loggedInStaff().id(),
                java.sql.Date.valueOf(java.time.LocalDate.now()));
        if (!leftovers.isEmpty()) {
            StringBuilder detail = new StringBuilder();
            for (Map<String, Object> row : leftovers) {
                if (detail.length() > 0) {
                    detail.append("、");
                }
                detail.append(row.get("room_no")).append("（").append(row.get("state")).append("）");
            }
            throw new AssertionError("完成畫面說今天排定的房都清完了，但這位員工今天還有沒做完的任務："
                    + detail);
        }
    }

    @那麼("完成畫面不顯示今日進度")
    public void celebrationHidesTodayProgress() {
        world.waitForTestId("celebrate.screen", SCREEN_TIMEOUT);
        world.waitForTestId("celebrate.title", SCREEN_TIMEOUT);
        List<WebElement> todayProgress =
                world.driver().findElements(world.byTestId("celebrate.today-progress"));
        if (!todayProgress.isEmpty()) {
            throw new AssertionError("完成畫面上出現了 celebrate.today-progress 這一塊（"
                    + todayProgress.size() + " 個）；這一句要求整塊不存在，"
                    + "不是留著顯示 0 或空白");
        }
    }

    @那麼("完成畫面底部有回到首頁的出口，且它不是最強調的動作")
    public void celebrationHasBackHomeExitBelowPrimaryAction() {
        world.waitForTestId("celebrate.screen", SCREEN_TIMEOUT);

        WebElement nextButton = world.waitForTestId("celebrate.next-button", SCREEN_TIMEOUT);
        WebElement backHomeButton =
                world.waitForTestId("celebrate.back-home-button", SCREEN_TIMEOUT);

        if (!Boolean.parseBoolean(nextButton.getAttribute("enabled"))) {
            throw new AssertionError("完成畫面的實心主按鈕應該能按，實際是禁用的");
        }
        if (!Boolean.parseBoolean(backHomeButton.getAttribute("enabled"))) {
            throw new AssertionError("完成畫面的「回到首頁」出口應該能按，實際是禁用的");
        }

        String backHomeLabel = backHomeButton.getAttribute("label");
        if (backHomeLabel == null || !backHomeLabel.contains("回到首頁")) {
            throw new AssertionError(
                    "完成畫面底部那顆出口的文字應該是「回到首頁」，實際讀到「" + backHomeLabel + "」");
        }

        org.openqa.selenium.Rectangle nextFrame = nextButton.getRect();
        org.openqa.selenium.Rectangle backHomeFrame = backHomeButton.getRect();
        int nextBottom = nextFrame.getY() + nextFrame.getHeight();
        if (backHomeFrame.getY() < nextBottom) {
            throw new AssertionError("「回到首頁」應該收在實心主按鈕下方，實際主按鈕底在 y="
                    + nextBottom + "、「回到首頁」頂在 y=" + backHomeFrame.getY()
                    + "——這樣它跟主按鈕並排或在它上面，就不是底部的次要出口了");
        }
    }

    @那麼("完成畫面不出現慶祝的氣氛")
    public void celebrationHasNoFestiveMood() {
        world.waitForTestId("celebrate.screen", SCREEN_TIMEOUT);

        List<WebElement> mood =
                world.driver().findElements(world.byTestId("celebrate.celebration"));
        if (!mood.isEmpty()) {
            throw new AssertionError("完成畫面上出現了 celebrate.celebration 這一組氣氛元素（"
                    + mood.size() + " 個）；關閉氣氛呈現時頂部飾帶、彩屑與入場動態必須整個不存在");
        }

        String roomNo = world.currentTaskRoomNo();
        String title = world.waitForTestId("celebrate.title", SCREEN_TIMEOUT).getAttribute("label");
        if (title == null || !title.contains(roomNo)) {
            throw new AssertionError("關閉氣氛呈現後，完成畫面的標題仍要帶出房號「" + roomNo
                    + "」，實際讀到「" + title + "」");
        }
        for (String keptId : List.of("celebrate.subline", "celebrate.actual-minutes")) {
            String label = world.waitForTestId(keptId, SCREEN_TIMEOUT).getAttribute("label");
            if (label == null || label.isBlank()) {
                throw new AssertionError("關閉氣氛呈現後，" + keptId
                        + " 這一塊仍要有文字，實際讀到「" + label + "」");
            }
        }
        WebElement next = world.waitForTestId("celebrate.next-button", SCREEN_TIMEOUT);
        if (!Boolean.parseBoolean(next.getAttribute("enabled"))) {
            throw new AssertionError("關閉氣氛呈現後，下一步按鈕仍要能按，實際是禁用的——"
                    + "這一鍵只關氣氛，不關接力");
        }

        List<Map<String, Object>> configRows = world.backend().queryRows(
                "SELECT value #>> '{}' AS value FROM tenant_config"
                        + " WHERE tenant_id = ? AND key = 'completion_celebration_enabled'",
                world.backend().ensureTenant());
        Object configured = configRows.isEmpty() ? null : configRows.get(0).get("value");
        if (!"false".equals(String.valueOf(configured))) {
            throw new AssertionError("租戶設定 completion_celebration_enabled 應該落地為 false，"
                    + "實際是 " + configured + "；設定沒關卻沒有氣氛，不是這一句要驗的事");
        }
    }

    @那麼("完成畫面說明這一筆還沒送出")
    public void celebrationSaysNotSentYet() {
        world.waitForTestId("celebrate.screen", SCREEN_TIMEOUT);

        String note = world.waitForTestId("celebrate.queued-note", SCREEN_TIMEOUT)
                .getAttribute("label");
        if (note == null || !note.contains("手機")) {
            throw new AssertionError("完成畫面的說明應該講這一筆已先記錄在手機，實際讀到「"
                    + note + "」");
        }
        if (!note.contains("自動送出") || !note.contains("連線")) {
            throw new AssertionError("完成畫面的說明應該講恢復連線後會自動送出，實際讀到「"
                    + note + "」");
        }

        String task = world.currentTaskRoomNo();
        String stateWhileOffline = String.valueOf(world.taskRowByRoomNo(task).get("state"));
        if (!"doing".equals(stateWhileOffline)) {
            throw new AssertionError("離線完成不該改到後端，但「" + task + "」在後端已經是「"
                    + stateWhileOffline + "」");
        }

        String pending = world.labelOf("system.offline-bar.pending-count");
        int pendingCount;
        try {
            pendingCount = Integer.parseInt(pending == null ? "" : pending.trim());
        } catch (NumberFormatException e) {
            throw new AssertionError("讀不出離線列的待同步筆數，實際讀到「" + pending + "」");
        }
        if (pendingCount < 1) {
            throw new AssertionError("完成畫面說這一筆已記在手機，但離線列的待同步筆數是 "
                    + pendingCount + "——本機佇列裡沒有這筆完成動作");
        }
    }


    private static final int OFFLINE_QUEUE_SETTLE_SECONDS = 5;

    @那麼("這次操作沒有留在手機上等補送")
    public void actionNotQueuedForLaterSend() {
        int before = world.offlineWindowPendingCount().orElseThrow(() -> new AssertionError(
                "切到離線的那一刻沒有量到本機待送出佇列的筆數，無從比對操作前後"));
        int after = before;
        for (int elapsed = 0; elapsed < OFFLINE_QUEUE_SETTLE_SECONDS; elapsed++) {
            after = world.pendingSyncCountInApp();
            if (after != before) {
                break;
            }
            if (elapsed < OFFLINE_QUEUE_SETTLE_SECONDS - 1) {
                sleepSeconds(1);
            }
        }
        if (after != before) {
            throw new AssertionError("這次操作被擋在送出之前，本機待送出佇列的筆數就不該變："
                    + "操作前是 " + before + " 筆，現在是 " + after + " 筆");
        }

        if (world.hasCurrentTaskRoomNo()) {
            String task = world.currentTaskRoomNo();
            String stateBefore = world.offlineWindowTaskState(task);
            String stateNow = String.valueOf(world.taskRowByRoomNo(task).get("state"));
            if (!stateBefore.equals(stateNow)) {
                throw new AssertionError("這次操作被擋在送出之前，後端「" + task + "」的狀態就不該變："
                        + "操作前是「" + stateBefore + "」，現在是「" + stateNow + "」");
            }
        }

        Map<String, String> unitsBefore = world.offlineWindowUnitStates();
        Map<String, String> unitsNow = world.backendUnitStatesNow();
        for (Map.Entry<String, String> entry : unitsBefore.entrySet()) {
            String now = unitsNow.get(entry.getKey());
            if (!entry.getValue().equals(now)) {
                throw new AssertionError("這次操作被擋在送出之前，後端「" + entry.getKey()
                        + "」就不該變：操作前是「" + entry.getValue() + "」，現在是「" + now + "」");
            }
        }
    }

    private static final int TIMER_TICK_WAIT_SECONDS = 5;

    private static final long DISPLAY_BACKEND_SECONDS_TOLERANCE = 30;

    @那麼("任務頁顯示已做 {int} 分鐘，且計時正在走")
    public void showsWorkedMinutesRunningTimer(int minutes) {
        String first = world.waitForTestId("task.hero.running-elapsed", SCREEN_TIMEOUT)
                .getAttribute("label");
        long firstSeconds = timerSecondsOf(first);
        long shownMinutes = firstSeconds / 60;
        if (Math.abs(shownMinutes - minutes) > 1) {
            throw new AssertionError("任務頁的已做時間不符：預期 " + minutes
                    + " 分鐘（容忍跨整分的 ±1），實際讀到「" + first + "」——顯示約兩倍"
                    + "代表正在跑的這一段被重複累計，公式基準只能用 accumulatedSeconds，"
                    + "不能拿已含本段的 elapsedMinutes 再加一次本段");
        }

        Map<String, Object> row = world.taskRowByRoomNo(world.currentTaskRoomNo());
        String state = String.valueOf(row.get("state"));
        if (!"doing".equals(state)) {
            throw new AssertionError("計時要在走，後端這一筆的狀態就得是 doing，實際是 " + state);
        }
        if (row.get("segment_started_at") == null) {
            throw new AssertionError("後端這一筆沒有正在累計的段落（segment_started_at 是空的），"
                    + "計時不可能在走");
        }
        long backendSeconds = workedSecondsOf(row);
        long backendMinutes = backendSeconds / 60;
        if (Math.abs(backendMinutes - minutes) > 1) {
            throw new AssertionError("後端算出的已做時間不符：預期 " + minutes
                    + " 分鐘（容忍跨整分的 ±1），實際 " + backendSeconds
                    + " 秒（worked_sec 加上正在走的這一段）——恢復之後要從中斷前的累計"
                    + "接著走，中斷那段時間不計入");
        }
        if (Math.abs(firstSeconds - backendSeconds) > DISPLAY_BACKEND_SECONDS_TOLERANCE) {
            throw new AssertionError("畫面的已做時間與後端公式對不上：畫面「" + first
                    + "」（" + firstSeconds + " 秒）、後端 worked_sec + (現在 − segment_started_at) = "
                    + backendSeconds + " 秒——差超過 " + DISPLAY_BACKEND_SECONDS_TOLERANCE
                    + " 秒，最常見的原因是正在跑的這一段被重複累計（顯示約兩倍）");
        }

        sleepSeconds(TIMER_TICK_WAIT_SECONDS);
        String later = world.waitForTestId("task.hero.running-elapsed", SCREEN_TIMEOUT)
                .getAttribute("label");
        long laterSeconds = timerSecondsOf(later);
        if (laterSeconds <= firstSeconds) {
            throw new AssertionError("任務頁的計時沒有在走：" + TIMER_TICK_WAIT_SECONDS
                    + " 秒後還是「" + later + "」（先前是「" + first + "」）");
        }
    }

    private static long timerSecondsOf(String label) {
        String[] parts = label == null ? new String[0] : label.trim().split(":");
        if (parts.length != 2 && parts.length != 3) {
            throw new AssertionError("計時主角的文字不是「分:秒」或「時:分:秒」："
                    + "讀到「" + label + "」");
        }
        long seconds = 0;
        for (String part : parts) {
            try {
                seconds = seconds * 60 + Long.parseLong(part.trim());
            } catch (NumberFormatException notANumber) {
                throw new AssertionError("計時主角的文字裡有不是數字的一段："
                        + "讀到「" + label + "」", notANumber);
            }
        }
        return seconds;
    }

    @那麼("任務頁不再顯示停住原因")
    public void noLongerShowsHoldReason() {
        world.waitForTestId("task.screen", SCREEN_TIMEOUT);
        world.waitForTestIdGone("task.status-row.hold-reason", SCREEN_TIMEOUT);

        Map<String, Object> row = world.taskRowByRoomNo(world.currentTaskRoomNo());
        Object holdReason = row.get("hold_reason");
        if (holdReason != null) {
            throw new AssertionError("後端這一筆的停住原因還留著「" + holdReason
                    + "」，恢復處理之後這一欄要清成空的");
        }
    }

    @那麼("完成畫面蓋在 {string} 的任務頁上")
    public void celebrationOverlaysTaskScreen(String task) {
        String completedRoomNo = world.currentTaskRoomNo();
        if (completedRoomNo != null && !completedRoomNo.equals(task)) {
            throw new IllegalArgumentException("句子裡的房號「" + task
                    + "」不是這次完成的那一間，剛完成的是「" + completedRoomNo + "」");
        }

        world.waitForTestId("celebrate.screen", SCREEN_TIMEOUT);

        List<WebElement> underlying =
                world.driver().findElements(world.byTestId("task.screen"));
        if (underlying.isEmpty()) {
            throw new AssertionError("完成畫面出現了，但底下那張「" + task
                    + "」的任務頁（task.screen）已經從畫面上消失——"
                    + "半透明遮罩被推上不透明路由（opaque: true）時就是這樣：覆蓋層在，"
                    + "底下卻是一片黑。完成畫面必須疊在任務頁之上，不是取代它");
        }
        if (!underlying.get(0).isDisplayed()) {
            throw new AssertionError("底下那張「" + task
                    + "」的任務頁還在元素樹上，但回報為不可見——遮罩蓋住的那一頁必須仍然看得到");
        }

        WebElement navBarOnTaskScreen =
                world.waitForTestId("task.nav." + task, SCREEN_TIMEOUT);
        if (!navBarOnTaskScreen.isDisplayed()) {
            throw new AssertionError("遮罩底下那一頁的房號「" + task
                    + "」讀得到，但回報為不可見——這一條要的就是房號仍然看得到");
        }

        // XCUITest 對自己不畫東西、面積被子節點蓋滿的 XCUIElementTypeOther 一律回報不可見，
        // 所以可見性驗在標題上。
        WebElement headline = world.waitForTestId("celebrate.title", SCREEN_TIMEOUT);
        if (!headline.isDisplayed()) {
            throw new AssertionError("完成畫面的標題回報為不可見，覆蓋層沒有真的疊上去");
        }
    }

    @那麼("畫面停在 {string} 的任務頁")
    public void screenStaysOnTaskDetailOf(String task) {
        world.waitForTestId("task.screen", SCREEN_TIMEOUT);
        if (!world.waitForTestId("task.nav." + task, SCREEN_TIMEOUT).isDisplayed()) {
            throw new AssertionError("「" + task + "」的任務頁導覽列讀得到，但回報為不可見");
        }
        assertScreenAbsent("celebrate.screen", "「" + task + "」的任務頁");
    }

    @那麼("畫面停在待認領")
    public void screenStaysOnPool() {
        world.waitForTestId("pool.screen", SCREEN_TIMEOUT);
        assertScreenAbsent("celebrate.screen", "待認領分頁");
        assertScreenAbsent("task.screen", "待認領分頁");
    }

    @那麼("畫面停在首頁")
    public void screenStaysOnHome() {
        world.waitForTestId("home.screen", SCREEN_TIMEOUT);
        assertScreenAbsent("celebrate.screen", "首頁");
        assertScreenAbsent("task.screen", "首頁");
    }

    private void assertScreenAbsent(String testId, String expectedScreen) {
        List<WebElement> found = world.driver().findElements(world.byTestId(testId));
        if (!found.isEmpty()) {
            throw new AssertionError("預期畫面停在" + expectedScreen + "，但畫面上還找得到 "
                    + testId + "（" + found.size() + " 個）");
        }
    }

    @那麼("完成畫面顯示今日進度為第 {int} 單")
    public void celebrationShowsTodayProgressOrdinal(int ordinal) {
        String roomNo = world.currentTaskRoomNo();
        Map<String, Object> row = world.taskRowByRoomNo(roomNo);
        if (!"cleaning".equals(String.valueOf(row.get("type")))) {
            throw new AssertionError("今日進度只數打掃單，「" + roomNo + "」的類別是 "
                    + row.get("type") + "，這一句不適用；非打掃類走「完成畫面不顯示今日進度」");
        }

        world.waitForTestId("celebrate.screen", SCREEN_TIMEOUT);
        world.waitForTestId("celebrate.today-progress", SCREEN_TIMEOUT);
        String tile = world.labelOf("celebrate.today-progress");
        if (tile == null || !java.util.regex.Pattern.compile("(?<!\\d)" + ordinal + "(?!\\d)")
                .matcher(tile).find()) {
            throw new AssertionError("完成畫面的今日進度磚應該顯示第 " + ordinal
                    + " 單，實際讀到「" + tile + "」");
        }

        String state = String.valueOf(row.get("state"));
        boolean landedOnBackend = "done".equals(state) || "inspected".equals(state);
        int expectedOnBackend = landedOnBackend ? ordinal : ordinal - 1;
        Object workDate = row.get("work_date");
        if (workDate == null) {
            throw new AssertionError("「" + roomNo + "」這一筆打掃單沒有工作日（work_date 是空的），"
                    + "無從重算今天的已結案張數");
        }

        long recounted = ((Number) world.backend().queryRows(
                "SELECT count(*) AS cleaning_done FROM tasks"
                        + " WHERE tenant_id = ? AND assignee_id = ? AND work_date = ?"
                        + " AND type = 'cleaning' AND state IN ('done', 'inspected')",
                world.backend().ensureTenant(), world.loggedInStaff().id(), workDate)
                .get(0).get("cleaning_done")).longValue();
        if (recounted != expectedOnBackend) {
            throw new AssertionError("完成畫面說今天做到第 " + ordinal + " 單，但後端重算今天（work_date="
                    + workDate + "）已結案的打掃單是 " + recounted + " 張"
                    + (landedOnBackend ? "" : "（「" + roomNo + "」還押在待送出佇列裡，state 是 "
                            + state + "，所以後端這時候應該剛好是 " + expectedOnBackend + " 張）"));
        }

        com.fasterxml.jackson.databind.JsonNode today =
                world.backend().getJson("/me/today", world.loggedInStaff());
        int cleaningDone = today.path("cleaningDone").asInt(Integer.MIN_VALUE);
        if (cleaningDone != expectedOnBackend) {
            throw new AssertionError("完成畫面的今日進度與首頁的已清完間數對不上：完成畫面說第 "
                    + ordinal + " 單，同一時刻首頁那一份資料（GET /me/today）的分子 cleaningDone 是 "
                    + cleaningDone + "，應該是 " + expectedOnBackend
                    + (landedOnBackend ? "" : "（押著的「" + roomNo + "」還沒送到後端）"));
        }
    }

    @那麼("完成被擋下，畫面顯示後端拒絕的原因")
    public void completionBlockedShowsBackendRejection() {
        world.waitForTestId("task.busy-alert", SCREEN_TIMEOUT);
        String shown = world.labelOf("task.busy-alert");
        if (shown == null || shown.isBlank()) {
            throw new AssertionError("拒絕提示出現了，但上面沒有任何文字——員工還是不知道為什麼完不成");
        }

        String roomNo = world.currentTaskRoomNo();
        UUID taskId = (UUID) world.taskRowByRoomNo(roomNo).get("id");
        com.fasterxml.jackson.databind.JsonNode error =
                world.backend().taskActionRejection(taskId, "complete", world.loggedInStaff());
        String backendMessage = error.path("message").asText("");
        if (backendMessage.isBlank()) {
            throw new AssertionError("後端的拒絕回應沒有 message，無從比對提示文字：" + error);
        }
        if (!backendMessage.trim().equals(shown.trim())) {
            throw new AssertionError("提示文字與後端的拒絕原因不一致：後端說「" + backendMessage
                    + "」，畫面上是「" + shown + "」——原因是後端說了算，App 不自己湊話");
        }

        world.tapTestId("task.busy-alert.dismiss-button", SCREEN_TIMEOUT);
        List<WebElement> celebrate =
                world.driver().findElements(world.byTestId("celebrate.screen"));
        if (!celebrate.isEmpty()) {
            throw new AssertionError("完成被後端拒收了，畫面上卻出現完成畫面（celebrate.screen）——"
                    + "被拒的完成不該慶祝");
        }

        Map<String, Object> row = world.taskRowByRoomNo(roomNo);
        String state = String.valueOf(row.get("state"));
        if ("done".equals(state) || "inspected".equals(state)) {
            throw new AssertionError("後端把「" + roomNo + "」標成完成了（state=" + state
                    + "），被拒的完成不該落地");
        }
        long completedEvents = ((Number) world.backend().queryRows(
                "SELECT count(*) AS completed FROM task_events"
                        + " WHERE tenant_id = ? AND task_id = ? AND type = 'completed'",
                world.backend().ensureTenant(), taskId).get(0).get("completed")).longValue();
        if (completedEvents != 0) {
            throw new AssertionError("後端長出了 " + completedEvents
                    + " 筆 completed 事件，被拒的完成不該留下任何完成事件");
        }
    }

    @那麼("{string} 的面板換成工務單表單，原任務還沒有被中斷")
    public void sheetSwitchedToRepairForm(String roomNo) {
        world.waitForTestId("repair.sheet", SCREEN_TIMEOUT);
        if (!world.driver().findElements(world.byTestId("hold.sheet")).isEmpty()) {
            throw new AssertionError("工務單表單出現了，中斷原因清單卻還在畫面上");
        }
        Map<String, Object> row = world.taskRowByRoomNo(roomNo);
        String state = String.valueOf(row.get("state"));
        if (!"doing".equals(state)) {
            throw new AssertionError("選「需要工務協助」只是換一張表單，原任務不該在這一步就被動到"
                    + "（實際 state=" + state + "）");
        }
    }
}
