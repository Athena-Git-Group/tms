package com.athena.app.bdd.steps.taskexecution;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.java.zh_tw.那麼;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
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
     *
     * <p>停住的單上那串字寫成「分:秒」（`Main.dc.html:1189`），和進行中的計時同一種
     * 寫法，所以這裡把字串換算回秒再比分鐘。停住的單沒有正在跑的段落，分鐘數不會在
     * 讀取之間跳動，因此要求完全相等。
     */
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
     * 必查：任務頁 SOP 進度文字（{@code x / y · 跳過不擋流程}）包含該關鍵字；只有勾選
     * 計入 x，跳過不算，y 為 SOP 總項數。accessibility id：{@code task.sop.progress}。
     */
    @那麼("{string} 的 SOP 進度顯示 {string}")
    public void sopProgressShows(String task, String keyword) {
        WebElement element = world.waitForTestId("task.sop.progress", SCREEN_TIMEOUT);
        String actual = element.getAttribute("label");
        if (actual == null || !actual.contains(keyword)) {
            throw new AssertionError("「" + task + "」的 SOP 進度文字不含「" + keyword
                    + "」：實際「" + actual + "」");
        }

        // 分子是「這張單已經勾起來幾項」，跳過的不算（`Main.dc.html:4291`）：跳過的
        // 意思是這一項這次不做，把它算進分子會讓進度講出一個沒做過的數字。兩邊對不
        // 上代表畫面數的跟後端記的不是同一回事。
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

    /**
     * 必查：任務頁計時 hero 區顯示該文字；超時分鐘數＝<b>目前</b>已做分鐘數減去預估分鐘
     * ——畫面開著不操作時要隨時間跟著變大，不是停在進頁或上次向後端取資料那一刻；
     * 只在超過時顯示，純提示不觸發推播。accessibility id：{@code task.hero.overtime-chip}。
     *
     * <p>等到文字等於期待值才算過，不是讀一次就比：場景會先把裝置時鐘往前撥
     * （「過了 N 分鐘」）再說這句，而徽章要等 App 的計時下一次跳動才重算。讀一次就判
     * 的話，抓到還沒重算的舊值會把「有在跟著走」誤報成壞掉；反過來，抓到舊值就放行
     * 的寫法，又會讓凍結在進頁那一刻的徽章蒙混過關。所以這裡輪詢到逾時，仍不等於
     * 期待值就是失敗，並把最後讀到的值報出來。
     */
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

    /**
     * 後端這一筆到「目前」為止做了幾分鐘：累計秒數加上還在走的那一段。
     *
     * <p>進行中的單有一段計時還沒結算進 {@code worked_sec}，只看那一欄會少算這一段。
     */
    private long workedMinutesOf(Map<String, Object> row) {
        return workedSecondsOf(row) / 60;
    }

    /**
     * 後端這一筆到「目前」為止做了幾秒：契約 {@code accumulatedSeconds + (現在 − segmentStartedAt)}
     * 那條公式——{@code worked_sec} 是進入目前段落之前的累計，正在走的這一段用
     * {@code segment_started_at} 到現在補上，而且只補這一次。
     *
     * <p>「現在」以裝置生效的時間為準：場景撥過裝置時鐘（「過了 N 分鐘」「已中斷
     * N 分鐘」）時要用注入值；拿 harness 的真實當下算會把撥快的那一段整個漏掉，
     * 期待值就跟畫面上跟著注入時鐘走的計時與超時徽章對不上。沒撥過時注入值是
     * {@code null}，照常用真實當下。
     */
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

    /**
     * 必查：完成畫面（accessibility id {@code celebrate.title} 所在畫面）**同一處**顯示的
     * 這次用時與當初預估，與後端 {@code tasks.worked_sec} 換算分鐘、預估分鐘一致。
     *
     * <p>完成畫面上這兩個數字合併成一塊，accessibility id 是
     * {@code celebrate.actual-minutes}；畫面上沒有只放預估的獨立節點。這裡斷言同一個
     * 節點的文字同時含兩個數字，避免有人把它拆成兩塊還讓測試通過。
     *
     * <p>「實際是主要數字、預估是附註，兩者不是同一級」這條是字級與排版，
     * accessibility label 讀不到，由人眼對照設計稿的視覺驗收關卡檢查，這裡不假裝驗得到。
     */
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

    /**
     * 必查：完成畫面（accessibility id {@code celebrate.title} 所在畫面）顯示這次用時，
     * 而且畫面上沒有預估欄位——不是顯示成空白或 0，是整個欄位不存在。用於沒有預估分鐘的任務。
     *
     * <p>「不存在」用 {@code findElements} 的空清單判定（找不到就是空清單，不必等逾時）：
     * 畫面上不得有 {@code celebrate.estimate-minutes} 這個獨立節點，而同時承載兩個數字的
     * {@code celebrate.actual-minutes} 這一塊，其文字也不得出現「預估」兩字——這樣「預估 0 分」
     * 或「預估 -- 分」這種把欄位留著只是沒值的做法會被擋下來。
     *
     * <p>後端那一筆的預估分鐘也要真的是空的；有估時的單走的是另一句
     * 「完成畫面顯示這次用時 {@code {int}} 分、當初預估 {@code {int}} 分」。
     */
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

    /**
     * 必查：完成畫面標題下方的說明文字（accessibility id {@code celebrate.subline}）等於該文字。
     *
     * <p>三種說法由這次完成的任務決定，不是由畫面自己挑：鎖定免查房的打掃＝
     * 「免查房，直接完成」；需查房的打掃＝「已通知主管查房」；其他類別＝
     * 「住客會收到完成通知」。所以這裡除了比畫面字串，還要用後端
     * {@code tasks.type}／{@code inspection_waived} 反推一次同一句——
     * 只比畫面的話，一個把說明寫死的元件在三個例子裡會有兩個碰巧過關。
     */
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

    /**
     * 後端這一筆任務對照到完成畫面的哪一句說明。
     *
     * <p>免查房是開工當下依租戶設定鎖在 {@code tasks.inspection_waived} 的，之後設定變更
     * 不影響，所以這裡讀的是任務上的鎖定值，不是租戶設定。
     */
    private static String sublineOf(Map<String, Object> row) {
        if (!"cleaning".equals(String.valueOf(row.get("type")))) {
            return "住客會收到完成通知";
        }
        boolean waived = Boolean.TRUE.equals(row.get("inspection_waived"))
                || "waived".equals(String.valueOf(row.get("inspection")));
        return waived ? "免查房，直接完成" : "已通知主管查房";
    }

    /**
     * 必查：以 accessibility id {@code celebrate.title} 定位，標題文字包含該房號，且該房號
     * 與這次完成的那一張任務一致。
     *
     * <p>只比字串會讓一個把房號寫死在標題上的版面過關，所以房號取自「剛才按下完成的那一頁
     * 任務」，再回頭讀那一列確認它在後端真的已結案——這樣標題上的房號才確實是剛做完的那張單。
     */
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

    /**
     * 必查：完成畫面的實心主按鈕（accessibility id {@code celebrate.next-button}）文字包含
     * 該房號，且與 {@code tasksComplete} 回應的 {@code nextStep.roomNo} 一致。
     *
     * <p>App 端讀不到那份回應，所以改以同一份權威資料核對：{@code nextStep.kind} 為
     * {@code next_task} 時指的就是這位員工今天待開始（{@code accepted}）的單裡房號最小的
     * 那一張。兩邊都要對上才算通過——只讀畫面的話，一個把房號寫死的按鈕也會過關。
     *
     * <p>這一句只驗按鈕上的呈現，不按下去。「按下去會開啟那一間的任務頁」由
     * 「在 App 從完成畫面前往下一步」搭配「畫面停在某房號的任務頁」驗。
     */
    @那麼("完成畫面的下一步指向 {string}")
    public void celebrationNextStepPointsToRoom(String roomNo) {
        world.waitForTestId("celebrate.screen", SCREEN_TIMEOUT);
        String label = world.waitForTestId("celebrate.next-button", SCREEN_TIMEOUT)
                .getAttribute("label");
        if (label == null || !label.contains(roomNo)) {
            throw new AssertionError("完成畫面的實心主按鈕文字應該含下一間的房號「" + roomNo
                    + "」，實際讀到「" + label + "」");
        }

        // 房號在 rooms，tasks 只有 room_id——直接 SELECT room_no FROM tasks 會丟 SQL 例外，
        // 那是環境錯誤不是斷言失敗。JOIN 的寫法比照同檔 assertNoUnfinishedTasksToday()。
        // work_date 也要限今天：跨日還沒做的 accepted 單不是「今天的下一間」。
        List<Map<String, Object>> accepted = world.backend().queryRows(
                "SELECT r.room_no AS room_no FROM tasks t"
                        + " JOIN rooms r ON r.id = t.room_id AND r.tenant_id = t.tenant_id"
                        + " WHERE t.tenant_id = ? AND t.assignee_id = ? AND t.state = 'accepted'"
                        + " AND (t.work_date IS NULL OR t.work_date = ?)"
                        + " ORDER BY r.room_no",
                world.backend().ensureTenant(), world.loggedInStaff().id(),
                java.sql.Date.valueOf(java.time.LocalDate.now()));
        if (accepted.isEmpty()) {
            throw new AssertionError("後端這位員工名下一張待開始的單都沒有，下一步不該指向「"
                    + roomNo + "」");
        }
        String expected = String.valueOf(accepted.get(0).get("room_no"));
        if (!roomNo.equals(expected)) {
            throw new AssertionError("下一步指向的房號與後端對不上：後端待開始的第一張是「"
                    + expected + "」，畫面上卻指向「" + roomNo + "」");
        }
    }

    /**
     * 必查：完成畫面出現「都清完了」的提示句（accessibility id
     * {@code celebrate.all-done-note}）與前往待認領的實心主按鈕
     * （{@code celebrate.next-button}），且這次完成回報算出來的去向是
     * {@code nextStep.kind = all_clear}。
     *
     * <p>這一條必須與「下一步指向某房號」在畫面上分得出來，而且不可以只靠「主按鈕消失」
     * 判定。所以這裡要的是兩個正面證據：①畫面上多出一個專門的提示句節點，內容講
     * 「都清完了」；②主按鈕還在、還能按，文字講的是「待認領」而不是某個房號。
     * 第 ② 點順便把房號擋掉——按鈕文字裡出現任何像房號的數字，就是把指向某房號的那一版
     * 誤認成這一版。
     *
     * <p>App 端讀不到 {@code tasksComplete} 的回應本體，所以 {@code all_clear} 這個
     * 判定改查它的權威條件：這位員工今天名下既沒有待開始（{@code accepted}），
     * 也沒有沒做完的（{@code doing}／{@code interrupted}／{@code deferred}）。
     * 條件不成立卻顯示這一版，代表 App 把去向算錯了。
     */
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

    /**
     * 完成回報算出 {@code all_clear} 的權威條件：這位員工今天名下沒有待開始、進行中、
     * 已中斷或已延後的任務。打掃單的 {@code work_date} 是今天，其他類別不帶這一欄，
     * 兩種都算在今天。
     */
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

    /**
     * 必查：完成畫面上沒有今日進度那一塊——accessibility id
     * {@code celebrate.today-progress} 的節點整個不存在，不是顯示 0 或空白。用於備品等
     * 非打掃類任務，以及租戶把完成慶祝的氣氛呈現關掉時。
     *
     * <p>「不存在」用 {@code findElements} 的空清單判定（找不到就是空清單，不必等逾時）；
     * 先等 {@code celebrate.screen} 出現，否則完成畫面還沒畫出來時什麼都找不到，這一句
     * 會假通過。
     */
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

    /**
     * 必查：完成畫面底部有「回到首頁」的文字按鈕（accessibility id
     * {@code celebrate.back-home-button}），可點；畫面上視覺層級最高的是前往下一步的
     * 實心主按鈕（{@code celebrate.next-button}），兩者不是同一種樣式。
     *
     * <p>這裡只驗機械驗得到的那幾項：兩顆按鈕都在完成畫面上、兩顆都還能按（accessibility
     * 樹上的 {@code enabled}），以及回到首頁那顆的位置在實心主按鈕**下方**——用元素框的
     * y 值比較，取「回到首頁」的框頂不低於主按鈕的框底，確保它真的是收在底部的次要出口，
     * 而不是跟主按鈕並排或擺在它上面。
     *
     * <p>「兩者不是同一種樣式、主按鈕才是視覺層級最高的那個」這條是填色、字級與邊框，
     * accessibility label 與座標都讀不到，由人眼對照設計稿的視覺驗收關卡檢查，
     * 這裡不假裝驗得到。
     */
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

    /**
     * 必查：完成畫面上沒有頂部飾帶、彩屑與入場動態——這組氣氛元素掛在 accessibility id
     * {@code celebrate.celebration} 這個群組上，整個節點必須不存在；同時房號標題
     * （{@code celebrate.title}）、說明（{@code celebrate.subline}）、這次用時
     * （{@code celebrate.actual-minutes}）與下一步按鈕（{@code celebrate.next-button}）仍在。
     *
     * <p>兩邊都要驗。只驗「氣氛不在」的話，整張完成卡沒畫出來、或完成畫面根本沒出現，
     * 也會過關；只驗「該留的還在」則驗不到氣氛是否真的關掉了。
     *
     * <p>「不存在」用 {@code findElements} 的空清單判定（找不到就是空清單，不必等逾時）；
     * 「仍在」用 {@code waitForTestId} 等到節點出現，並要求文字不是空的。
     *
     * <p>權威設定也核一次：租戶設定 {@code completion_celebration_enabled} 落地為 false
     * （{@code tasksComplete} 回應的 {@code celebrationEnabled} 同源於它，App 端讀不到
     * 那份回應本體，所以查它的來源）。設定沒關卻沒有氣氛，那是氣氛元素壞掉，不是這一句要的。
     */
    @那麼("完成畫面不出現慶祝的氣氛")
    public void celebrationHasNoFestiveMood() {
        world.waitForTestId("celebrate.screen", SCREEN_TIMEOUT);

        List<WebElement> mood =
                world.driver().findElements(world.byTestId("celebrate.celebration"));
        if (!mood.isEmpty()) {
            throw new AssertionError("完成畫面上出現了 celebrate.celebration 這一組氣氛元素（"
                    + mood.size() + " 個）；關閉氣氛呈現時頂部飾帶、彩屑與入場動態必須整個不存在");
        }

        // 氣氛關掉不等於整張卡消失：房號標題、說明、這次用時與下一步按鈕都還要在。
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

    /**
     * 必查：完成畫面出現「已先記錄在手機、恢復連線後自動送出」意思的說明文字
     * （accessibility id {@code celebrate.queued-note}）；後端該任務狀態尚未改變
     * （仍是 {@code doing}）；App 本機佇列有這筆完成動作。
     *
     * <p>句型不帶房號，對照的是剛才按下完成的那一頁任務（{@code currentTaskRoomNo}）。
     *
     * <p>說明文字不比死字串，只查它講到「手機」與「連線後自動送出」兩件事——文案還會
     * 改，這一條在意的是有沒有把「這筆還沒送出」講清楚。
     *
     * <p>「本機佇列真的有這筆」不能只看那句說明：一個把說明寫死、卻什麼都沒排進佇列的
     * 版面會過關，而那正是這條 Rule 最在意的失敗方式——員工以為做完了，後端卻一直不知道。
     * 所以另外讀離線列的待同步筆數（{@code system.offline-bar.pending-count}，它顯示的就是
     * {@code ConnectionStatus.pendingSyncCount} 本身），要求至少 1。
     *
     * <p>**這一句刻意不把連線切回來。** 切回去等後端收下，等於在 Then 裡執行另一條 Rule
     * （「恢復連線後，離線時押著的那一筆完成真的送到後端」）的 Act，也會讓這個場景的結束
     * 狀態不是 dsl 說的「還押在手機上」。那條 Rule 自己有獨立的 When 負責，這裡只驗現況。
     */
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


    /**
     * 確認離線被擋下的動作沒有排進待送出佇列時，盯著筆數看多久。
     *
     * <p>App 是先把動作交給佇列、佇列再回頭更新畫面上的筆數，點完按鈕立刻讀一次
     * 會讀到還沒更新的舊筆數。
     */
    private static final int OFFLINE_QUEUE_SETTLE_SECONDS = 5;

    /**
     * 盯待送出筆數時，每一次讀畫面給多久。
     *
     * <p>讀不到離線列就是這一句失敗，所以這個時限要給得夠寬。剛點完按鈕的那一下
     * 畫面可能還在轉場或蓋著提示，離線列要等它落定才找得到。給一兩秒會把「還沒畫好」
     * 誤判成「這一頁沒有離線列」。
     */
    private static final Duration PENDING_COUNT_READ_TIMEOUT = Duration.ofSeconds(3);

    /**
     * 必查：App 本機的待送出佇列沒有因這次操作而增加，離線列的待同步筆數
     * （accessibility id {@code system.offline-bar.pending-count}）與操作前相同；
     * 後端該任務的狀態也與操作前一樣。
     *
     * <p><b>這一句唯一要擋的失敗方式：提示出現、動作卻偷偷進了佇列。</b>那樣的版本
     * 畫面上一樣會顯示離線提示，等連線恢復就送出一個員工以為沒有發生的操作。所以只驗
     * 畫面提示不夠，這裡讀的是佇列筆數本身，而且盯一段時間才下結論。
     *
     * <p>操作前的筆數與狀態，由把手機切到離線的那一句先量好，讀法見
     * {@link com.athena.app.bdd.AppiumWorld#goOffline()}。句型不帶房號，對照的是目前
     * 打開的那一頁任務。
     *
     * <p>用於離線時被擋在送出之前的動作，也就是恢復處理、開始、認領與延後。離線完成
     * 剛好相反，它會押進佇列等補送，兩者的斷言不可互相沿用。
     *
     * <p>讀不到離線列一樣算失敗，不當成「佇列是空的」，操作前那一次與操作後那一次都是。
     * 離線列只要在畫面上就一定帶著筆數節點，連筆數 0 也帶，但那一列只掛在少數幾個頁面。
     * 離線期間讀不到，代表這一頁沒有那一列，筆數根本沒量到；沒量到就不能宣告這次操作
     * 沒有進佇列。這兩種失敗各有各的訊息：一個是操作前的基準沒量到，一個是現在這一頁
     * 讀不到，不能混成同一句，否則訊息會把讀不到說成筆數變了。
     */
    @那麼("這次操作沒有留在手機上等補送")
    public void actionNotQueuedForLaterSend() {
        OptionalInt baseline = world.offlineWindowPendingCount();
        if (baseline.isEmpty()) {
            throw new AssertionError("切到離線的那一刻，當時那一頁上找不到離線列"
                    + "（system.offline-bar.pending-count），操作前本機待送出佇列有幾筆"
                    + "根本沒量到，無從比對操作前後，這一句不算通過。"
                    + "這不是筆數變了，是基準缺了");
        }
        int before = baseline.getAsInt();
        OptionalInt after = OptionalInt.of(before);
        for (int elapsed = 0; elapsed < OFFLINE_QUEUE_SETTLE_SECONDS; elapsed++) {
            after = world.pendingSyncCountOnScreen(PENDING_COUNT_READ_TIMEOUT);
            if (after.isPresent() && after.getAsInt() != before) {
                break;
            }
            if (elapsed < OFFLINE_QUEUE_SETTLE_SECONDS - 1) {
                sleepSeconds(1);
            }
        }
        if (after.isEmpty()) {
            throw new AssertionError("離線期間這一頁上找不到離線列"
                    + "（system.offline-bar.pending-count），本機待送出佇列有幾筆無從得知，"
                    + "這一句不算通過");
        }
        if (after.getAsInt() != before) {
            throw new AssertionError("這次操作被擋在送出之前，本機待送出佇列的筆數就不該變："
                    + "操作前是 " + before + " 筆，現在是 " + after.getAsInt() + " 筆");
        }

        String task = world.currentTaskRoomNo();
        String stateBefore = world.offlineWindowTaskState(task);
        String stateNow = String.valueOf(world.taskRowByRoomNo(task).get("state"));
        if (!stateBefore.equals(stateNow)) {
            throw new AssertionError("這次操作被擋在送出之前，後端「" + task + "」的狀態就不該變："
                    + "操作前是「" + stateBefore + "」，現在是「" + stateNow + "」");
        }
    }

    /** 要確認「計時正在走」時，兩次讀畫面之間隔多久。 */
    private static final int TIMER_TICK_WAIT_SECONDS = 5;

    /**
     * 畫面上的已做秒數與「後端 worked_sec + 正在走的這一段」允許差多少秒：讀畫面與查
     * 資料庫之間隔了一次網路來回，加上畫面整秒跳動，給 30 秒緩衝。重複累計本段的
     * 兩倍錯誤至少差出整段本段時長（情境裡是十幾二十分鐘），遠超這個緩衝，必定紅。
     */
    private static final long DISPLAY_BACKEND_SECONDS_TOLERANCE = 30;

    /**
     * 必查：任務頁的已做時間顯示該分鐘數；等待一段時間後數字會往上跳。後端
     * {@code tasks.state} 為 doing 且 {@code segment_started_at} 有值。
     * accessibility id：{@code task.hero.running-elapsed}。
     *
     * <p>用於恢復之後——中斷期間不計入，所以數字是從中斷前的累計接著走，不是從零重新
     * 起算。後端的已做時間照契約的公式算：{@code accumulatedSeconds +
     * (現在 − segmentStartedAt)}，也就是 {@code worked_sec} 加上正在走的這一段——只看
     * {@code worked_sec} 會漏掉本段（進行中的 fixture 是 worked_sec 0、
     * segment_started_at 往前推整段），把中斷那幾分鐘算進去則會比預期多。
     *
     * <p>顯示值要**等於**期望分鐘——只容忍測試執行期間跨過一個整分的 ±1，
     * 絕不容忍「正在跑的這一段被重複累計」的約兩倍（拿 elapsedMinutes 當基準再加本段
     * 就會這樣）。另外畫面秒數與後端公式算出的秒數也要對得上：兩倍錯誤在秒這一級
     * 差出整段本段時長，比分鐘級的 ±1 更藏不住。
     *
     * <p>計時正在走的單，畫面上的數字寫成「分:秒」（超過一小時才多一段小時），
     * 所以這裡把字串換算回秒再比：先比分鐘數對不對，隔一段時間再讀一次，秒數要比剛才
     * 大。只比分鐘的話，一支停住的計時在五秒內也不會露出破綻。
     */
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

        // 隔一段時間再讀一次，秒數要往上跳；沒跳就是畫面上的計時其實停著。
        sleepSeconds(TIMER_TICK_WAIT_SECONDS);
        String later = world.waitForTestId("task.hero.running-elapsed", SCREEN_TIMEOUT)
                .getAttribute("label");
        long laterSeconds = timerSecondsOf(later);
        if (laterSeconds <= firstSeconds) {
            throw new AssertionError("任務頁的計時沒有在走：" + TIMER_TICK_WAIT_SECONDS
                    + " 秒後還是「" + later + "」（先前是「" + first + "」）");
        }
    }

    /**
     * 計時主角上那串字換算成秒。格式是「分:秒」，超過一小時才多出前面的小時那一段。
     *
     * <p>讀不出來就當失敗，不回 0——回 0 會讓「計時有沒有在走」那一比變成比較兩個 0，
     * 於是一支壞掉的計時反而過關。
     */
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

    /**
     * 必查：任務頁上沒有停住原因那一塊（accessibility id
     * {@code task.status-row.hold-reason} 整個不存在）；後端 {@code tasks.hold_reason}
     * 為 NULL。
     *
     * <p>用於恢復之後——那張單已經接回來做了，停住原因留著會讓員工看著一個已經不成立
     * 的理由繼續做。按下恢復到畫面真的更新之間有一段網路來回，所以用等待消失而不是
     * 讀一次就判定。
     *
     * <p>後端那一欄也要一起清掉。只看畫面的話，一個把那一塊藏起來、資料還留著的做法
     * 會過關，下一次進任務頁又冒出來。
     */
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

    /**
     * 必查：完成畫面是疊在剛完成的那張任務頁之上的半透明覆蓋層——遮罩底下那一頁的房號
     * 仍然看得到。
     *
     * <p><b>不可只檢查完成畫面存在。</b>把半透明遮罩推上不透明路由（Flutter 的
     * {@code opaque: true}）時，覆蓋層一樣在、{@code celebrate.screen} 一樣找得到，但底下
     * 那一頁已經被判為 offstage、整個不再繪製，員工看到的是一片黑。所以這裡要的是
     * 「同一時刻的兩個正面證據」：①覆蓋層在；②底下那張任務頁也還在。
     *
     * <p>底下那一頁「還在」怎麼證明：
     * <ol>
     *   <li>{@code task.screen} 必須在覆蓋層出現的同一時刻仍然找得到。不透明路由把下層
     *       推成 offstage 時，那一層的 semantics 節點會整批從 accessibility 樹上消失，
     *       {@code findElements} 回空清單——這一點就是本條要擋的 bug 與正確實作之間
     *       唯一的機械差別。</li>
     *   <li>任務頁的凍結導覽列（{@code task.nav.{房號}}）必須仍然讀得到。這個節點只有
     *       任務頁有，而且房號就在 id 裡，所以讀到它等於讀到底下那一頁的房號本體，
     *       而不只是「有個下層存在」。完成畫面自己的標題也帶房號，但它不是這個節點，
     *       混不過去。</li>
     *   <li>房號必須就是剛按下完成的那一間（{@link AppiumWorld#currentTaskRoomNo()}），
     *       不是別的某一頁；覆蓋層蓋錯一頁跟蓋在黑底上一樣算失敗。</li>
     *   <li>兩個節點都再問一次是否 displayed：被半透明遮罩蓋著的節點在螢幕上仍有實際
     *       位置，應該回報為可見；被換成不透明路由而拔掉的節點不會走到這一步。</li>
     * </ol>
     *
     * <p>能力限制（誠實交代）：Appium 讀的是 accessibility 樹，讀不到像素，所以「遮罩是
     * 半透明的」這件事沒有直接量到——這裡量的是它的必要條件「底下那一頁還被繪製、
     * 房號還在樹上且回報可見」。真正把遮罩做成不透明純色（下層仍繪製、只是被色塊蓋住）
     * 的做法逃得過這一條；而本條針對的真實 bug（不透明路由讓下層不再繪製）擋得住。
     * 像素級的對照留給視覺快照，不在這一句。
     *
     * <p>這一句刻意不碰後端狀態：離線完成那條情境也用它，而離線時後端那一筆還沒有變。
     */
    @那麼("完成畫面蓋在 {string} 的任務頁上")
    public void celebrationOverlaysTaskScreen(String task) {
        String completedRoomNo = world.currentTaskRoomNo();
        if (completedRoomNo != null && !completedRoomNo.equals(task)) {
            throw new IllegalArgumentException("句子裡的房號「" + task
                    + "」不是這次完成的那一間，剛完成的是「" + completedRoomNo + "」");
        }

        WebElement overlay = world.waitForTestId("celebrate.screen", SCREEN_TIMEOUT);

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

        if (!overlay.isDisplayed()) {
            throw new AssertionError("完成畫面本身回報為不可見，覆蓋層沒有真的疊上去");
        }
    }

    /**
     * 必查：目前畫面是該房號的任務頁——{@code task.screen} 在畫面上，凍結導覽列
     * （{@code task.nav.{房號}}）帶的是這個房號，而且完成畫面那張覆蓋層已經收掉。
     *
     * <p>房號驗在導覽列節點上，因為它的 id 自帶房號，是這一頁唯一不必讀文案就認得出
     * 房號的節點。
     *
     * <p>「中途沒有經過首頁」驗不到：Appium 事後只讀得到當下的元素樹，證不了路上
     * 停過哪幾頁，所以這裡驗的是終點狀態，不假裝驗到路徑。真的被丟回首頁而停在那裡
     * 的話，這一句等不到任務頁、會紅在這裡；若 App 先閃一下首頁再開任務頁，這一句
     * 看不出來——那要靠錄影比對，不是這一層。
     *
     * <p>覆蓋層（{@code celebrate.screen}）不得存在這一條是必要的：完成畫面蓋在剛做完
     * 那張單的任務頁上，不檢查它的話，「按了下一步卻什麼都沒換」也會因為底下那張任務頁
     * 還在而過關。
     */
    @那麼("畫面停在 {string} 的任務頁")
    public void screenStaysOnTaskDetailOf(String task) {
        world.waitForTestId("task.screen", SCREEN_TIMEOUT);
        if (!world.waitForTestId("task.nav." + task, SCREEN_TIMEOUT).isDisplayed()) {
            throw new AssertionError("「" + task + "」的任務頁導覽列讀得到，但回報為不可見");
        }
        assertScreenAbsent("celebrate.screen", "「" + task + "」的任務頁");
    }

    /**
     * 必查：目前畫面是待認領分頁（{@code pool.screen}）。
     *
     * <p>分頁列的四個分頁只有當下那一個在元素樹上（非當前分頁被收起來、連同語意一起
     * 不存在），所以 {@code pool.screen} 找得到就代表停在待認領這一頁。另外要求覆蓋層
     * 與推進去的任務頁都不存在：那兩者會蓋住整個螢幕（含分頁列），底下的待認領還在樹上，
     * 只查 {@code pool.screen} 會讓「畫面其實還停在完成畫面上」過關。
     */
    @那麼("畫面停在待認領")
    public void screenStaysOnPool() {
        world.waitForTestId("pool.screen", SCREEN_TIMEOUT);
        assertScreenAbsent("celebrate.screen", "待認領分頁");
        assertScreenAbsent("task.screen", "待認領分頁");
    }

    /**
     * 必查：目前畫面是首頁（{@code home.screen}）。
     *
     * <p>與「畫面停在待認領」同一套判法：首頁分頁在元素樹上，而且沒有任何覆蓋層或推進去
     * 的任務頁蓋在它上面。
     */
    @那麼("畫面停在首頁")
    public void screenStaysOnHome() {
        world.waitForTestId("home.screen", SCREEN_TIMEOUT);
        assertScreenAbsent("celebrate.screen", "首頁");
        assertScreenAbsent("task.screen", "首頁");
    }

    /**
     * 這個畫面不得出現在元素樹上。
     *
     * <p>用 {@code findElements} 的空清單判定（找不到就是空清單，不必等逾時）：蓋在上面的
     * 覆蓋層或推進去的頁還在的話，這裡就會以「還找得到它」失敗。
     */
    private void assertScreenAbsent(String testId, String expectedScreen) {
        List<WebElement> found = world.driver().findElements(world.byTestId(testId));
        if (!found.isEmpty()) {
            throw new AssertionError("預期畫面停在" + expectedScreen + "，但畫面上還找得到 "
                    + testId + "（" + found.size() + " 個）");
        }
    }

    /**
     * 必查：完成畫面的今日進度磚（accessibility id {@code celebrate.today-progress}）顯示的
     * 單數就是這一句的數字，與 {@code tasksComplete} 回應的 {@code cleaningDoneToday} 一致；
     * 同一時刻首頁「已清完間數」的分子也是同一個數字。
     *
     * <p>App 端讀不到完成回報的本體，所以 {@code cleaningDoneToday} 改以同一份權威資料
     * 重算：這位員工今天（剛完成那一筆的 {@code work_date}）名下已結案的打掃單張數，
     * 已結案指 {@code done} 或 {@code inspected}。開工時鎖定免查房而直接結案的單狀態就是
     * {@code done}，本來就算在裡面，不必再依 {@code inspection_waived} 加總一次。
     *
     * <p>跨視角那一段打的是首頁那一份資料的來源 {@code GET /me/today}：它的
     * {@code cleaningDone} 就是首頁「X / Y 間房已清完」的分子 X。兩個畫面給出不同數字就是
     * 不一致，所以除了磚上的數字，首頁的分子也要對上——只比磚上的數字的話，一個自己
     * 算一套的完成畫面也會過關。
     *
     * <p>離線完成時這一筆還押在手機的待送出佇列裡，後端那一邊還不知道它，完成畫面是以
     * 手機上已知的完成數推算，比後端多了押著的這一筆。所以基準取自「剛完成的那一列在
     * 後端是否已結案」：已結案就該等於這一句的數字，還沒結案就該剛好少一張——少的就是
     * 押著的那一筆，多一或少更多都是算錯。
     */
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

    /**
     * 必查：畫面出現提示（Alert，比照 {@code task.busy-alert} 的語意），文字就是後端
     * 這次 4xx 回應的 message——App 不自己湊文案；不進完成畫面、任務沒被標成完成；
     * 後端該任務維持被拒前的狀態（沒有新的 {@code completed} 事件）。
     * <b>禁止無聲：提示沒有出現就算失敗，即使後端狀態正確。</b>
     *
     * <p>比對基準怎麼取：App 當下收到的那份 4xx 回應事後讀不到，所以以同一位員工對
     * 同一筆任務重送一次完成（{@link BackendChannel#taskActionRejection}），取回後端
     * 的同一句拒絕訊息。被拒的動作在後端沒有副作用，重送不改狀態——這不是用講的，
     * 最後那段權威查核就是在重送之後做的，狀態要是被重送改掉了會當場紅。
     *
     * <p>文字用<b>相等</b>比對（只容頭尾空白差異）：Alert 的 content 只有後端 message
     * 一句，dismiss 鈕是自己的 semantics 節點不會併進 {@code task.busy-alert} 的
     * label，所以讀到的 label 就該一字不差。若未來 Alert 的語意合併把別的字拖進來，
     * 正解是在 App 端給 alert 節點顯式 label（TmsSemantics 的 label 參數），
     * 不是把這裡放寬成 contains。
     */
    @那麼("完成被擋下，畫面顯示後端拒絕的原因")
    public void completionBlockedShowsBackendRejection() {
        // 禁止無聲擺最前面：提示沒出現，後端狀態再正確也直接紅在這裡。
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

        // Alert 是 modal，底下整頁的 semantics 都被它擋掉，得先關掉提示才能驗
        // 「沒有進完成畫面」（員工的動線也是看完提示就關掉）。
        world.tapTestId("task.busy-alert.dismiss-button", SCREEN_TIMEOUT);
        List<WebElement> celebrate =
                world.driver().findElements(world.byTestId("celebrate.screen"));
        if (!celebrate.isEmpty()) {
            throw new AssertionError("完成被後端拒收了，畫面上卻出現完成畫面（celebrate.screen）——"
                    + "被拒的完成不該慶祝");
        }

        // 權威查核（在重送之後做）：任務沒被標成完成、維持被拒前的狀態——
        // state 不在結案那幾個值裡，task_events 也沒有長出 completed。
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

    /**
     * 必查：畫面上是工務單表單而不是原因清單，而且這一步還沒有中斷任何東西——原任務
     * 仍是進行中，後端也還沒有這張單。accessibility id：{@code repair.sheet}、
     * {@code hold.sheet}。
     */
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
