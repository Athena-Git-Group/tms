package com.athena.app.bdd.steps.taskexecution;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.zh_tw.那麼;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 任務執行模組的 Then。 */
public class TaskExecutionThenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    /** 要確認「計時沒有在走」時，兩次讀畫面之間隔多久。 */
    private static final int TIMER_STILLNESS_WAIT_SECONDS = 5;

    private final AppiumWorld world;

    public TaskExecutionThenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 必查：任務頁的已做時間顯示該分鐘數；等待一段時間後數字不變。後端累計時間一致。
     * accessibility id：{@code task.hero.paused-elapsed}。
     */
    @那麼("任務頁顯示已做 {int} 分鐘，且計時沒有在走")
    public void showsWorkedMinutesPausedTimer(int minutes) {
        String expected = "已做 " + minutes + " 分";
        WebElement element = world.waitForTestId("task.hero.paused-elapsed", SCREEN_TIMEOUT);
        String actual = element.getAttribute("label");
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    "任務頁的已做時間不符：預期「" + expected + "」，實際「" + actual + "」");
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

        // 停住的單在畫面上也不該有秒數在跳：隔一段時間再讀一次，數字要跟剛才一樣。
        sleepSeconds(TIMER_STILLNESS_WAIT_SECONDS);
        String later = world.waitForTestId("task.hero.paused-elapsed", SCREEN_TIMEOUT)
                .getAttribute("label");
        if (!expected.equals(later)) {
            throw new AssertionError("任務頁的已做時間還在走：" + TIMER_STILLNESS_WAIT_SECONDS
                    + " 秒後從「" + expected + "」變成「" + later + "」");
        }
    }

    /**
     * 必查：以 accessibility id {@code task.step-track} 定位，打掃類步驟條
     * （已接→進行中→完成→查房／免查房）目前標示的步驟與後端 {@code tasks.state}／
     * {@code inspection} 對照一致。
     */
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

    /**
     * 必查：以 accessibility id {@code task.step-track} 定位，備品類步驟條
     * （已接→進行中→送達）目前標示的步驟與後端 {@code tasks.state} 對照一致。
     */
    @那麼("任務頁顯示備品步驟條，目前停在 {string}")
    public void showsAmenityStepTrack(String step) {
        Map<String, Object> row = world.taskRowByRoomNo(world.currentTaskRoomNo());
        if (!"amenity".equals(String.valueOf(row.get("type")))) {
            throw new AssertionError("這一筆不是備品類任務，實際類別 " + row.get("type"));
        }
        assertStepTrack(List.of("已接", "進行中", "送達"), step, row);
    }

    /**
     * 步驟條兩邊都要對上：畫面上有這幾段、目前標示的是這一段，而且這一段和後端的
     * {@code state}／{@code inspection} 對照一致。
     *
     * <p>只讀畫面的話，一條把目前步驟寫死的步驟條也會過關；只讀後端的話，則驗不到
     * 員工在任務頁上究竟看見停在哪一步。
     */
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

    /**
     * 後端狀態對照到步驟條的哪一段。待開始與已延後都還沒開工，算在第一段；已中斷是
     * 開工之後停住的，仍算在「進行中」那一段；查房通過與免查結案都停在最後一段。
     */
    private static String stepOf(String state, List<String> steps) {
        return switch (state) {
            case "doing", "interrupted" -> "進行中";
            case "done" -> steps.get(2);
            case "inspected" -> steps.get(steps.size() - 1);
            default -> steps.get(0);
        };
    }

    /**
     * 必查：以 accessibility id {@code task.primary-button} 定位，畫面上只有一個置底主按鈕，
     * 文字與目前狀態對照表相符；延後／中斷是次要按鈕，不算在此主按鈕判定內。
     */
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

    /**
     * 必查：任務頁 SOP 進度文字（{@code x / y · 跳過不擋流程}）包含該關鍵字；勾選與跳過都
     * 計入 x，y 為 SOP 總項數。accessibility id：{@code task.sop.progress}。
     */
    @那麼("{string} 的 SOP 進度顯示 {string}")
    public void sopProgressShows(String task, String keyword) {
        WebElement element = world.waitForTestId("task.sop.progress", SCREEN_TIMEOUT);
        String actual = element.getAttribute("label");
        if (actual == null || !actual.contains(keyword)) {
            throw new AssertionError("「" + task + "」的 SOP 進度文字不含「" + keyword
                    + "」：實際「" + actual + "」");
        }

        // 分子是「這張單已經處理掉幾項」，勾選與跳過都算；兩邊對不上代表畫面數的
        // 跟後端記的不是同一回事。
        UUID taskId = (UUID) world.taskRowByRoomNo(task).get("id");
        long handled = ((Number) world.backend().queryRows(
                "SELECT count(*) AS handled FROM task_sop_checks"
                        + " WHERE tenant_id = ? AND task_id = ? AND (checked OR skipped)",
                world.backend().ensureTenant(), taskId).get(0).get("handled")).longValue();
        long shown = Long.parseLong(actual.substring(0, actual.indexOf('/')).trim());
        if (shown != handled) {
            throw new AssertionError("「" + task + "」的 SOP 進度分子不符：畫面上是 " + shown
                    + "，後端已處理的是 " + handled + " 項");
        }
    }

    /**
     * 必查：任務頁計時 hero 區顯示該文字；超時分鐘數＝目前已做分鐘數減去預估分鐘，只在超過
     * 時顯示，純提示不觸發推播。accessibility id：{@code task.hero.overtime-chip}。
     */
    @那麼("任務頁顯示 {string}")
    public void taskDetailShows(String text) {
        WebElement element = world.waitForTestId("task.hero.overtime-chip", SCREEN_TIMEOUT);
        String actual = element.getAttribute("label");
        if (!text.equals(actual)) {
            throw new AssertionError(
                    "任務頁計時區的超時提示不符：預期「" + text + "」，實際「" + actual + "」");
        }

        Map<String, Object> row = world.taskRowByRoomNo(world.currentTaskRoomNo());
        long estMinutes = ((Number) row.get("est_min")).longValue();
        long workedMinutes = workedMinutesOf(row);
        String expected = "超時 " + (workedMinutes - estMinutes) + " 分";
        if (!expected.equals(text)) {
            throw new AssertionError("超時分鐘數與後端算出來的不符：後端已做 " + workedMinutes
                    + " 分、預估 " + estMinutes + " 分，應該是「" + expected + "」");
        }
    }

    /**
     * 後端這一筆到現在為止做了幾分鐘：累計秒數加上還在走的那一段。
     *
     * <p>進行中的單有一段計時還沒結算進 {@code worked_sec}，只看那一欄會少算這一段。
     */
    private static long workedMinutesOf(Map<String, Object> row) {
        long seconds = ((Number) row.get("worked_sec")).longValue();
        Object segmentStartedAt = row.get("segment_started_at");
        if (segmentStartedAt instanceof java.sql.Timestamp timestamp) {
            seconds += Duration.between(timestamp.toInstant(), java.time.Instant.now()).getSeconds();
        } else if (segmentStartedAt instanceof java.time.OffsetDateTime offsetDateTime) {
            seconds += Duration.between(offsetDateTime.toInstant(), java.time.Instant.now()).getSeconds();
        }
        return seconds / 60;
    }

    /**
     * 必查：完成慶祝頁（accessibility id {@code celebrate.title} 所在畫面）顯示的實際與預估
     * 分鐘數，與後端 {@code tasks.worked_sec} 換算分鐘、預估分鐘一致。
     * accessibility id：{@code celebrate.actual-minutes}、{@code celebrate.estimate-minutes}。
     */
    @那麼("完成慶祝頁顯示實際 {int} 分、預估 {int} 分")
    public void celebrationShowsActualAndEstimate(int actualMinutes, int estimatedMinutes) {
        world.waitForTestId("celebrate.title", SCREEN_TIMEOUT);
        assertLabel("celebrate.actual-minutes", "實際 " + actualMinutes + " 分");
        assertLabel("celebrate.estimate-minutes", "預估 " + estimatedMinutes + " 分");

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

    /** 這個節點的文字要剛好等於這一句。 */
    private void assertLabel(String testId, String expected) {
        String actual = world.waitForTestId(testId, SCREEN_TIMEOUT).getAttribute("label");
        if (!expected.equals(actual)) {
            throw new AssertionError(testId + " 的文字不符：預期「" + expected
                    + "」，實際「" + actual + "」");
        }
    }

    /**
     * 必查：透過後端測試資料查核，該員工的 {@code outbox} 沒有任何 kind {@code push} 的資料；
     * 用於確認超時提示這種純 App 端顯示沒有連帶觸發推播。
     */
    @那麼("後端沒有對 {string} 送出任何推播")
    public void noPushSentTo(String staffName) {
        long tenantId = world.backend().ensureTenant();
        UUID staffId = world.staff(staffName).id();

        // 推播在 outbox 裡掛在任務上（aggregate_key 是 task:{任務 id}），所以查法是
        // 「這位員工名下的任務有沒有長出推播」。
        List<Map<String, Object>> pushes = world.backend().queryRows(
                """
                SELECT o.id FROM outbox o
                  JOIN tasks t ON o.aggregate_key = 'task:' || t.id::text
                 WHERE o.tenant_id = ? AND o.kind = 'push' AND t.assignee_id = ?
                """, tenantId, staffId);
        if (!pushes.isEmpty()) {
            throw new AssertionError("「" + staffName + "」名下的任務送出了 " + pushes.size()
                    + " 筆推播，這個情境不該有任何一筆");
        }

        // 推播送到員工手上之後會留下一則通知，所以收件匣也要是空的。
        List<Map<String, Object>> notifications = world.backend().queryRows(
                "SELECT id FROM notifications WHERE tenant_id = ? AND recipient_staff_id = ?",
                tenantId, staffId);
        if (!notifications.isEmpty()) {
            throw new AssertionError("「" + staffName + "」收到了 " + notifications.size()
                    + " 則通知，這個情境不該有任何一則");
        }
    }

    /**
     * 必查：任務頁狀態列顯示的停住原因文字與後端 {@code tasks.hold_reason} 一致。
     * accessibility id：{@code task.status-row.hold-reason}。
     *
     * <p>兩邊都要對上才算通過。只讀畫面的話，一個把原因寫死在版面上的元件也會過關；
     * 只讀後端的話，則驗不到員工在任務頁上究竟看見什麼。
     */
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

    /**
     * 必查：延後面板送出鈕的可互動狀態符合；禁用時無法真的送出。
     * accessibility id：{@code hold.confirm-button}。
     *
     * <p>讀的是 accessibility 樹上的 enabled 屬性，不是按鈕的顏色。App 端要把這個狀態
     * 顯式掛上去，沒掛的按鈕在這裡一律讀成可用。
     */
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

    /**
     * 必查：延後面板「其他原因」欄位下方即時字數提示等於該文字。
     * accessibility id：{@code hold.char-count}。
     */
    @那麼("延後面板顯示字數 {string}")
    public void deferPanelCharCount(String countText) {
        WebElement element = world.waitForTestId("hold.char-count", SCREEN_TIMEOUT);
        String actual = element.getAttribute("label");
        if (!countText.equals(actual)) {
            throw new AssertionError(
                    "延後面板的字數提示不符：預期「" + countText + "」，實際「" + actual + "」");
        }
    }

    /** 等指定秒數，用來確認畫面上的數字在這段時間內沒有變。 */
    private static void sleepSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待畫面維持不變時被中斷", e);
        }
    }
}
