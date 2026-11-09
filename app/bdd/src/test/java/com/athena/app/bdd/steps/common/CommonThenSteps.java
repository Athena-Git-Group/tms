package com.athena.app.bdd.steps.common;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.java.zh_tw.那麼;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** 介面根共通句的 Then（dsl.md Then 句型區）。 */
public class CommonThenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 狀態用語 → 那個用語額外要求的 {@code tasks.inspection}。只有查房結果會讓同一個
     * {@code state} 分成兩種用語（{@code inspected} 分查房通過與完成 · 免查房），其餘
     * 用語光看 state 就能分辨。
     */
    private static final Map<String, String> REQUIRED_INSPECTION = Map.of(
            "查房通過", "passed",
            "完成 · 免查房", "waived");

    /**
     * 狀態用語 → 那個用語額外要求的 {@code tasks.type} 是不是打掃類。{@code done} 在
     * 打掃類叫「待查房」、在其他類叫「已送達」。
     */
    private static final Map<String, Boolean> REQUIRED_CLEANING = Map.of(
            "待查房", Boolean.TRUE,
            "已送達", Boolean.FALSE);

    /**
     * 任務頁上寫出一個狀態的那一塊：要定位哪一個節點、那個節點該讀到什麼字、
     * 以及步驟條該停在哪一步。後兩者留空就只查節點在不在。
     */
    private record TaskPageEvidence(String testId, String title, String step) {}

    /**
     * 狀態用語 → 任務頁上寫出這個狀態的那一塊。
     *
     * <p>設計原型的任務頁沒有狀態 chip：這張單走到哪一步由標題區底下的步驟條表達，
     * 停住、作廢、等查房這類例外交給狀態列（`Main.dc.html:1165`）。所以畫面這一半
     * 查這張表決定讀哪一個節點、期望讀到什麼字。
     *
     * <p>狀態列的標題掛在容器底下的 {@code .title} 節點上：容器本身用
     * explicitChildNodes 把說明與文字鍵留在各自的節點，自己沒有可讀的文字。
     *
     * <p>已延後與已中斷的狀態列標題寫的是後端記下的停住原因（自由文字），不是狀態
     * 用語，所以那兩個只查「停住原因那一塊在畫面上」，兩者的分別交給步驟條。
     */
    private static final Map<String, TaskPageEvidence> TASK_PAGE_EVIDENCE = Map.ofEntries(
            Map.entry("待開始", new TaskPageEvidence("task.step-track", null, "已接")),
            Map.entry("退回重做", new TaskPageEvidence("task.status-row.return-info", null, "已接")),
            Map.entry("進行中", new TaskPageEvidence("task.step-track", null, "進行中")),
            Map.entry("已延後", new TaskPageEvidence("task.status-row.hold-reason", null, "已接")),
            Map.entry("已中斷", new TaskPageEvidence("task.status-row.hold-reason", null, "進行中")),
            Map.entry("待查房", new TaskPageEvidence("task.status-row.awaiting-inspection", "等主管查房", "完成")),
            Map.entry("已送達", new TaskPageEvidence("task.status-row.awaiting-inspection", "已送達", "送達")),
            Map.entry("查房通過", new TaskPageEvidence("task.status-row.finished", "查房通過", null)),
            Map.entry("完成 · 免查房", new TaskPageEvidence("task.status-row.finished", "完成 · 免查房", null)),
            Map.entry("已取消", new TaskPageEvidence("task.status-row.cancelled", "這單已取消", null)));

    /**
     * 任務頁上那些提示的關閉鈕。它們不會同時出現（後端拒絕這次操作、離線擋下這次
     * 操作、接力的那一間已經改派給別人），所以逐個試、在畫面上的那個就關掉。
     */
    private static final List<String> ALERT_DISMISS_BUTTONS = List.of(
            "task.busy-alert.dismiss-button",
            "system.offline-blocked-alert.dismiss-button",
            "celebrate.reassigned-alert.dismiss-button");

    private final AppiumWorld world;

    public CommonThenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 必查：畫面出現包含該文字的提示（Alert 或 Toast）。
     *
     * <p>驗完文字就順手把提示關掉（見 {@link #dismissAlertsCoveringTaskPage()}），
     * 讓情境接著驗「被擋下的那一間仍然是待開始」這種畫面狀態。
     */
    @那麼("畫面提示 {string}")
    public void screenPrompts(String message) {
        world.waitForLabelContaining(message, SCREEN_TIMEOUT);
        dismissAlertsCoveringTaskPage();
    }

    /**
     * 關掉蓋在任務頁上的提示。
     *
     * <p>提示如果是 Alert，它是蓋在畫面上的 modal——Flutter 會把底下整頁的 semantics
     * 擋掉，對同一畫面的斷言會什麼都找不到，看起來像畫面根本沒更新。使用者的實際動線
     * 也是看完提示就把它關掉。沒有可關的提示（Toast 自己會消失）就什麼都不做。
     */
    private void dismissAlertsCoveringTaskPage() {
        for (String dismissButton : ALERT_DISMISS_BUTTONS) {
            // 點的是剛找到的那一顆，不是再定位一次：先問在不在、再用 id 等一次的寫法，
            // 中間那一瞬間提示被關掉（上一句的斷言順手關掉、或動畫收完）就會等滿逾時，
            // 紅在「等不到關閉鈕」而不是劇本真的要驗的事。
            for (WebElement dismiss : world.driver().findElements(world.byTestId(dismissButton))) {
                dismiss.click();
            }
        }
    }

    /**
     * 必查：任務頁上寫出該狀態的那一塊符合 {@link #TASK_PAGE_EVIDENCE}，且後端權威狀態
     * 與「狀態用語對照」一致。兩邊都要對上才算通過：只看畫面會讓一塊寫死文字的版面
     * 過關，只看後端則驗不到使用者看見什麼。
     *
     * <p>讀畫面之前先關掉蓋在任務頁上的提示（見 {@link #dismissAlertsCoveringTaskPage()}）。
     * 這一句用在「動作被擋下之後，那一間沒有任何改變」這種情境上，而被擋下來的時候畫面
     * 正蓋著一個說明原因的提示。
     */
    @那麼("{string} 在 App 顯示為 {string}")
    public void taskDisplayedAs(String task, String state) {
        assertBackendState(task, state);

        dismissAlertsCoveringTaskPage();
        returnToTaskDetailFromCelebration(task);
        TaskPageEvidence evidence = TASK_PAGE_EVIDENCE.get(state);
        if (evidence == null) {
            throw new IllegalArgumentException(
                    "找不到「" + state + "」在任務頁狀態呈現對照表中的權威值");
        }
        world.waitForTestId(evidence.testId(), SCREEN_TIMEOUT);
        if (evidence.title() != null) {
            String titleId = evidence.testId() + ".title";
            String actual = world.waitForLabelOf(titleId, evidence.title(), SCREEN_TIMEOUT);
            if (!evidence.title().equals(actual)) {
                throw new AssertionError("「" + task + "」的狀態列標題不符：預期「"
                        + evidence.title() + "」，實際「" + actual + "」");
            }
        }
        if (evidence.step() != null) {
            String actual = world.waitForLabelOf("task.step-track", evidence.step(), SCREEN_TIMEOUT);
            if (!evidence.step().equals(actual)) {
                throw new AssertionError("「" + task + "」的步驟條不符：狀態「" + state
                        + "」應該停在「" + evidence.step() + "」，實際停在「" + actual + "」");
            }
        }
    }

    /**
     * 任務完成之後，App 會蓋上完成畫面，狀態 chip 被它擋住。
     * 這時照員工自己的動線走回任務頁：按「回到首頁」，再從首頁點那一間的任務卡。
     *
     * <p>完成畫面是**疊在剛完成的那張任務頁上的覆蓋層**，不是取代任務頁的另一頁
     * （見 `specs/truth/features/ios-app/任務執行/完成畫面.feature` 的 Rule
     * 「完成後出現的完成畫面，蓋在剛做完的那張任務頁上」）。因此底下那一頁仍然在
     * widget tree 裡，狀態列與步驟條就算被遮住也**找得到**。這裡必須先走完這段
     * 動線再去讀：少了它，斷言會在員工什麼都看不到的畫面上通過。
     *
     * <p>「回到首頁」是完成畫面底部的文字按鈕。視覺層級最高的是往下一步走的實心主按鈕
     * {@code celebrate.next-button}。這裡刻意點前者——走的是「先回首頁
     * 再進那一間」的動線，不是接力去下一間，否則會離開要斷言的那張單。
     *
     * <p>不在完成畫面上時什麼都不做。改成在完成畫面上也露出狀態是另一種解法，但那會
     * 讓這個斷言在員工根本看不到狀態的畫面上通過。
     */
    private void returnToTaskDetailFromCelebration(String task) {
        if (world.driver().findElements(world.byTestId("celebrate.screen")).isEmpty()) {
            return;
        }
        world.tapTestId("celebrate.back-home-button", SCREEN_TIMEOUT);
        world.waitForTestId("home.screen", SCREEN_TIMEOUT);
        world.tapTestId("home.task." + task + ".card", SCREEN_TIMEOUT);
        world.waitForTestId("task.screen", SCREEN_TIMEOUT);
    }

    /** 權威半邊：{@code tasks.state}（必要時加上 inspection／type）符合狀態用語對照。 */
    private void assertBackendState(String task, String state) {
        Map<String, Object> row = world.taskRowByRoomNo(task);
        String expectedState = BackendChannel.taskState(state);
        String actualState = String.valueOf(row.get("state"));
        if (!expectedState.equals(actualState)) {
            throw new AssertionError("「" + task + "」的後端狀態不符：預期 " + expectedState
                    + "（" + state + "），實際 " + actualState);
        }
        String requiredInspection = REQUIRED_INSPECTION.get(state);
        if (requiredInspection != null && !requiredInspection.equals(String.valueOf(row.get("inspection")))) {
            throw new AssertionError("「" + task + "」的後端查房結果不符：預期 " + requiredInspection
                    + "，實際 " + row.get("inspection"));
        }
        Boolean requiredCleaning = REQUIRED_CLEANING.get(state);
        if (requiredCleaning != null
                && requiredCleaning != "cleaning".equals(String.valueOf(row.get("type")))) {
            throw new AssertionError("「" + task + "」的任務類別與狀態用語「" + state + "」不搭：實際 "
                    + row.get("type"));
        }
    }
}
