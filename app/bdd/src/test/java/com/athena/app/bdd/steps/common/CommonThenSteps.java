package com.athena.app.bdd.steps.common;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import com.athena.app.bdd.steps.manager.ManagerContext;
import com.athena.app.bdd.steps.repair.RepairContext;
import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.zh_tw.那麼;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Map;

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

    /**
     * 工務單狀態用語 → 工務單詳情狀態列（{@code ticket.status-summary-card}）該讀到的字。
     * 工務單詳情沒有任務頁那種步驟條對照，狀態寫在摘要卡／狀態列上；主管視角與工務視角
     * 同一塊（dsl 工務單「狀態摘要卡」那一列）。
     *
     * <p>「退回重修」不是狀態用語，是待開始、已接單或維修中的單被驗收不通過退回之後
     * 狀態列的說法（{@code last_verify_failed_at} 有值）；見 {@link #expectedTicketStatusText}。
     * 主管視角的已完修另有完整說法，見 {@link #MANAGER_TICKET_STATUS_TEXT}。
     */
    private static final Map<String, String> TICKET_STATUS_TEXT = Map.ofEntries(
            Map.entry("待認領", "等工務接單"),
            Map.entry("待開始", "已接單"),
            Map.entry("已接單", "已接單"),
            Map.entry("維修中", "維修中"),
            Map.entry("已完修", "已完修"),
            Map.entry("已結案", "已結案"),
            Map.entry("退回重修", "退回重修"));

    /** 主管視角工務單詳情狀態列上，和員工視角說法不同的狀態。 */
    private static final Map<String, String> MANAGER_TICKET_STATUS_TEXT = Map.of(
            "已完修", "已完修 · 等主管驗收");

    /** 房務端（報修人）摘要卡上已完修的說法（dsl 工務單模組說明「三種視角對各狀態的呈現」）。 */
    private static final String REPORTER_DONE_TEXT = "已修好 · 等主管驗收";

    /** 驗收不通過面板的原因欄；面板收起就找不到它。 */
    private static final String FAIL_SHEET_REASON_FIELD = "managerTicket.fail-sheet.reason-field";

    /** 工務單號的形狀（契約 {@code ^W-\d{4}$}）；用來分辨「任務」參數是房號還是單號。 */
    private static final java.util.regex.Pattern TICKET_NO = java.util.regex.Pattern.compile("^W-\\d{4}$");

    /** 清單中文 → 契約 ManagerQueues 的鍵；清單畫面導覽列標題要讀到的字。 */
    private static final Map<String, String> QUEUE_KEY = Map.of(
            "待查房清單", "inspection",
            "待驗收清單", "verify");

    private final AppiumWorld world;
    private final ManagerContext managerContext;
    private final RepairContext repairContext;

    public CommonThenSteps(AppiumWorld world, ManagerContext managerContext, RepairContext repairContext) {
        this.world = world;
        this.managerContext = managerContext;
        this.repairContext = repairContext;
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
        if (TICKET_NO.matcher(task).matches()) {
            ticketDisplayedAs(task, state);
            return;
        }
        assertBackendState(task, state);
        if (world.loggedInStaff() != null && "manager".equals(world.loggedInStaff().role())) {
            managerSeesTaskAs(task, state);
            return;
        }

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

    /**
     * 主管在主管工作台上看到的那一半。主管沒有房務的任務頁可進，這間房的狀態在主管這邊
     * 只寫在他當下停留的那一頁：
     * <ul>
     *   <li>查房結果頁（{@code inspect.result}）：結果的說法就是這張單的去向——查房通過寫
     *       「查房通過」；退回原清房者名下（待開始）寫「退回重做」與「名下」；回到房務待認領
     *       （待認領）寫「退回重做」與「房務待認領」。</li>
     *   <li>查房畫面（{@code inspect}）：還停在這張單的查房畫面上，代表它仍在等查房（待查房）。</li>
     *   <li>待查房清單（{@code manager.queue}）：單被別人處理掉之後主管被送回清單，該列已不在
     *       清單上，代表它已不在等查房；權威狀態以後端為準（上面已驗）。</li>
     * </ul>
     * 停在其他畫面、或該畫面的說法對不上這個狀態，就紅。
     */
    private void managerSeesTaskAs(String task, String state) {
        if (!world.driver().findElements(world.byTestId("inspect.result")).isEmpty()) {
            List<String> expected = MANAGER_RESULT_TEXT.get(state);
            if (expected == null) {
                throw new AssertionError("查房結果頁不會出現狀態「" + state + "」的單「" + task + "」");
            }
            String actual = world.waitForLabelOf("inspect.result", expected.get(expected.size() - 1), SCREEN_TIMEOUT);
            if (actual == null || !actual.contains(task) || !expected.stream().allMatch(actual::contains)) {
                throw new AssertionError("「" + task + "」的查房結果頁說法不符：預期含 " + expected
                        + "，實際「" + actual + "」");
            }
            return;
        }
        if (!world.driver().findElements(world.byTestId("inspect")).isEmpty()) {
            if (!"待查房".equals(state)) {
                throw new AssertionError("主管還停在「" + task + "」的查房畫面，但預期狀態是「" + state + "」");
            }
            return;
        }
        world.waitForTestId("manager.queue", SCREEN_TIMEOUT);
        if ("待查房".equals(state)) {
            world.waitForTestId("manager.queue.row." + task, SCREEN_TIMEOUT);
        } else if (!world.driver().findElements(world.byTestId("manager.queue.row." + task)).isEmpty()) {
            throw new AssertionError("「" + task + "」預期為「" + state + "」，卻還列在待查房清單上");
        }
    }

    /** 查房結果頁上，各狀態對應的說法：結果頁的字要全部含有這些片段。 */
    private static final Map<String, List<String>> MANAGER_RESULT_TEXT = Map.of(
            "查房通過", List.of("查房通過"),
            "待開始", List.of("退回重做", "名下"),
            "待認領", List.of("退回重做", "房務待認領"));

    /**
     * 「任務」是工務單號時的那一半：後端 {@code tasks.state} 照工務單狀態對照；畫面在工務單
     * 詳情的狀態列（{@code ticket.status-summary-card}）讀到該狀態的字。蓋在上面的提示先關掉，
     * 理由同任務頁那一半。
     *
     * <p>主管看工務單時一律在主管視角的工務單詳情讀狀態列。驗收不通過送出後畫面停在驗收結果
     * 卡上，卡底下那張詳情被蓋住、主管看不到，所以先按卡上唯一的「回到待驗收」離開，再照
     * {@link RepairContext#openTicketDetail} 的入口重新打開這張單，讀的是重新進入後的畫面。
     */
    private void ticketDisplayedAs(String ticketNo, String state) {
        Map<String, Object> row = world.backend().repairTicketRow(ticketNo);
        String expectedState = BackendChannel.repairTicketState(state);
        String actualState = String.valueOf(row.get("task_state"));
        if (!expectedState.equals(actualState)) {
            throw new AssertionError("「" + ticketNo + "」的後端狀態不符：預期 " + expectedState
                    + "（" + state + "），實際 " + actualState);
        }
        dismissAlertsCoveringTaskPage();
        BackendChannel.CreatedStaff viewer = world.loggedInStaff();
        boolean manager = viewer != null && "manager".equals(viewer.role());
        if (manager) {
            openManagerTicketDetailFresh(viewer, ticketNo);
        }
        world.waitForTestId("ticket.screen", SCREEN_TIMEOUT);
        boolean reporterView = !manager && viewer != null && !"engineering".equals(viewer.dept());
        String expectedText = reporterView && "已完修".equals(state)
                ? REPORTER_DONE_TEXT
                : expectedTicketStatusText(state, row, manager);
        String actual = world.waitForLabelOf("ticket.status-summary-card", expectedText, SCREEN_TIMEOUT);
        boolean matches = actual != null && ("已結案".equals(state) && manager
                ? actual.startsWith(expectedText)
                : actual.contains(expectedText));
        if (!matches) {
            throw new AssertionError("「" + ticketNo + "」的工務單詳情狀態列不符：預期"
                    + ("已結案".equals(state) && manager ? "開頭為" : "含") + "「" + expectedText
                    + "」，實際「" + actual + "」");
        }
    }

    /**
     * 這張單在詳情狀態列上該讀到的字。待開始、已接單、維修中的單若曾被驗收不通過
     * （{@code last_verify_failed_at} 有值），代表它正在退回重修，狀態列寫「退回重修」
     * （dsl 工務單「狀態摘要卡」那一列：退回重修＝accepted 或 doing 且最近一次不通過有值）。
     */
    private static String expectedTicketStatusText(String state, Map<String, Object> row, boolean manager) {
        if (row.get("last_verify_failed_at") != null && List.of("待開始", "已接單", "維修中").contains(state)) {
            return TICKET_STATUS_TEXT.get("退回重修");
        }
        if (manager && MANAGER_TICKET_STATUS_TEXT.containsKey(state)) {
            return MANAGER_TICKET_STATUS_TEXT.get(state);
        }
        String text = TICKET_STATUS_TEXT.get(state);
        if (text == null) {
            throw new IllegalArgumentException("找不到「" + state + "」在工務單詳情狀態呈現對照表中的權威值");
        }
        return text;
    }

    /**
     * 主管重新打開這張單的主管視角詳情。停在驗收結果卡上時先按「回到待驗收」離開；
     * 已經停在詳情上也照樣重開一次，讀到的才是重新向後端取過的畫面。
     */
    private void openManagerTicketDetailFresh(BackendChannel.CreatedStaff manager, String ticketNo) {
        for (WebElement back : world.driver().findElements(world.byTestId("managerTicket.result.back-button"))) {
            back.click();
            world.waitForTestId("manager.queue", SCREEN_TIMEOUT);
        }
        world.ensureOnScreenRoot("manager.home", SCREEN_TIMEOUT);
        repairContext.openTicketDetail(manager, ticketNo);
    }

    /**
     * 必查：該員工的 {@code outbox} 有一筆 kind push、title 等於標題的資料，且同一事件只有一筆。
     * 給 App 端的主管動作（查房送出、退回）用：當事人有沒有被通知到，只有後端查得出來；
     * 推播內容與去處的細節由後端 truth 驗。
     *
     * <p>「同一事件只有一筆」以標題為準：同一張單同一個事件的標題相同，App 重送（同一把
     * Idempotency-Key）要是被後端當成兩次，這裡會看到兩筆同標題。
     */
    @那麼("後端已對 {string} 送出推播 {string}")
    public void backendSentPushTo(String staffName, String title) {
        List<String> titles = world.backend().pushTitlesSentTo(world.staff(staffName).id());
        long matching = titles.stream().filter(title::equals).count();
        if (matching == 0) {
            throw new AssertionError("後端沒有對「" + staffName + "」送出標題為「" + title
                    + "」的推播，實際送出的標題：" + titles);
        }
        if (matching > 1) {
            throw new AssertionError("後端對「" + staffName + "」送出了 " + matching
                    + " 筆標題為「" + title + "」的推播，同一事件只該有一筆");
        }
    }

    /**
     * 必查：該房 {@code rooms.hk_state} 等於對照值（還沒打掃好＝dirty、已查房＝inspected、
     * 停用＝out_of_order）。給主管在 App 查房或驗收之後用：房況有沒有跟著走，畫面上看不到。
     */
    @那麼("後端 {string} 的房況為 {string}")
    public void backendRoomHkStateIs(String roomNo, String hkStateChinese) {
        String expected = BackendChannel.hkState(hkStateChinese);
        String actual = world.backend().roomHkState(roomNo);
        if (!expected.equals(actual)) {
            throw new AssertionError("「" + roomNo + "」的後端房況不符：預期 " + expected
                    + "（" + hkStateChinese + "），實際 " + actual);
        }
    }

    /**
     * 必查：目前畫面是主管的該類清單（導覽列標題相符），清單已重新向 {@code getManagerQueues}
     * 取過，該單不在列上；同一次回應的 {@code counts} 對應格數比操作前少 1，回到主管首頁時該格
     * 顯示同一個數字。給主管在工務單詳情按通過後 App 自動回待驗收清單、查房結果畫面按
     * 「回到待查房」，以及驗收結果卡按「回到待驗收」之後用。
     *
     * <p>畫面有沒有重取，看的是那一列還在不在：沒重取的話列會留著。「比操作前少 1」需要操作前
     * 的數字，由打開清單的句子記在 {@link ManagerContext}；沒有記過就略過這一項。最後按返回回
     * 主管首頁對格子的數字，再點格子回清單，讓情境接下去的句子仍停在清單上。
     */
    @那麼("畫面回到 {string}，{string} 不在清單上")
    public void backOnQueueWithoutItem(String queueChinese, String unit) {
        String queueKey = queueKey(queueChinese);
        world.waitForTestId("manager.queue", SCREEN_TIMEOUT);
        world.waitForLabelContaining(queueChinese.replace("清單", ""), SCREEN_TIMEOUT);
        world.waitForTestIdGone("manager.queue.row." + unit, SCREEN_TIMEOUT);

        int count = assertQueueConsistentWithout(queueKey, unit);
        Integer before = managerContext.queueCountBefore(queueKey);
        if (before != null && count != before - 1) {
            throw new AssertionError("「" + queueChinese + "」的張數應比操作前少 1：操作前 " + before
                    + "，現在 getManagerQueues 回 " + count);
        }
        assertHomeTileShows(queueKey, count);
    }

    /**
     * 必查：後端這次回 409 {@code state_conflict}，App 彈出含「這張單已被處理」的對話框
     * （{@code manager.conflict-alert}），按「知道了」後回到來源清單，清單已重新取過且該單不在
     * 列上；後端該單仍是別人處理完的那個狀態。驗收通過、驗收不通過與查房送出共用同一個對話框；
     * 不通過面板開著時收到衝突，面板一起收起。
     *
     * <p>後端那一半查「別人處理完、而且只處理了一次」：查房與驗收都只能做一次，這位主管那一下
     * 要是也被收下，查房紀錄會多出第二筆、驗收紀錄（通過或不通過）會多出一筆。
     */
    @那麼("畫面顯示 {string}，並回到 {string}，{string} 不在清單上")
    public void conflictAlertThenBackOnQueue(String message, String queueChinese, String unit) {
        String queueKey = queueKey(queueChinese);
        WebElement alert = world.waitForTestId("manager.conflict-alert", SCREEN_TIMEOUT);
        String text = alert.getText();
        if (text == null || !text.contains(message)) {
            throw new AssertionError("manager.conflict-alert 應顯示「" + message + "」，實際「" + text + "」");
        }
        world.waitForLabelContaining("知道了", SCREEN_TIMEOUT).click();

        world.waitForTestId("manager.queue", SCREEN_TIMEOUT);
        world.waitForTestIdGone(FAIL_SHEET_REASON_FIELD, SCREEN_TIMEOUT);
        world.waitForLabelContaining(queueChinese.replace("清單", ""), SCREEN_TIMEOUT);
        world.waitForTestIdGone("manager.queue.row." + unit, SCREEN_TIMEOUT);
        int count = assertQueueConsistentWithout(queueKey, unit);
        assertProcessedExactlyOnce(queueKey, unit);
        assertHomeTileShows(queueKey, count);
    }

    private static String queueKey(String queueChinese) {
        String key = QUEUE_KEY.get(queueChinese);
        if (key == null) {
            throw new IllegalArgumentException("找不到「" + queueChinese + "」在清單對照表中的權威值（合法值："
                    + QUEUE_KEY.keySet() + "）");
        }
        return key;
    }

    /**
     * 權威讀回 {@code getManagerQueues}：該類陣列裡沒有這張單（房號或單號），{@code counts}
     * 的數字等於陣列長度。回傳那個數字。
     */
    private int assertQueueConsistentWithout(String queueKey, String unit) {
        JsonNode queues = world.backend().managerQueues(world.loggedInStaff());
        JsonNode items = queues.path(queueKey);
        for (JsonNode item : items) {
            if (unit.equals(item.path("roomNo").asText()) || unit.equals(item.path("ticketNo").asText())) {
                throw new AssertionError("「" + unit + "」仍在 getManagerQueues 的 " + queueKey + " 陣列裡");
            }
        }
        int count = queues.path("counts").path(queueKey).asInt(-1);
        if (count != items.size()) {
            throw new AssertionError("getManagerQueues 的 counts." + queueKey + "（" + count
                    + "）與 " + queueKey + " 陣列長度（" + items.size() + "）不一致");
        }
        return count;
    }

    /** 回主管首頁對該格的數字，再點同一格回到清單（情境接下去的句子仍停在清單上）。 */
    private void assertHomeTileShows(String queueKey, int count) {
        world.ensureOnScreenRoot("manager.home", SCREEN_TIMEOUT);
        String tileId = "manager.home.tile." + queueKey + ".count";
        String shown = world.waitForLabelOf(tileId, String.valueOf(count), SCREEN_TIMEOUT);
        if (!String.valueOf(count).equals(shown)) {
            throw new AssertionError("主管首頁 " + tileId + " 應顯示 " + count + "，實際「" + shown + "」");
        }
        world.tapUntilVisible("manager.home.tile." + queueKey, "manager.queue", SCREEN_TIMEOUT);
    }

    /** 那張單在後端已被別人處理完，而且只處理了一次。 */
    private void assertProcessedExactlyOnce(String queueKey, String unit) {
        long tenantId = world.backend().ensureTenant();
        switch (queueKey) {
            case "inspection" -> {
                Map<String, Object> task = world.taskRowByRoomNo(unit);
                if ("done".equals(String.valueOf(task.get("state")))) {
                    throw new AssertionError("「" + unit + "」在後端仍是待查房，不是被別人處理過的狀態");
                }
                List<Map<String, Object>> inspections = world.backend().queryRows(
                        "SELECT id FROM inspections WHERE tenant_id = ? AND task_id = ?", tenantId, task.get("id"));
                if (inspections.size() != 1) {
                    throw new AssertionError("「" + unit + "」的查房紀錄應剛好一筆（別人那一次），實際 "
                            + inspections.size() + " 筆");
                }
            }
            case "verify" -> {
                Map<String, Object> ticket = world.backend().repairTicketRow(unit);
                if ("done".equals(String.valueOf(ticket.get("task_state")))) {
                    throw new AssertionError("「" + unit + "」在後端仍是已完修、等主管驗收，不是被別人處理過的狀態");
                }
                List<Map<String, Object>> verifications = world.backend().queryRows("""
                        SELECT type FROM task_events
                        WHERE tenant_id = ? AND task_id = ?
                          AND type IN ('repair_verified', 'repair_verification_failed')
                        """, tenantId, ticket.get("task_id"));
                if (verifications.size() != 1) {
                    throw new AssertionError("「" + unit + "」的驗收紀錄（通過或不通過）應剛好一筆（別人那一次），實際 "
                            + verifications.size() + " 筆：" + verifications);
                }
            }
            default -> throw new IllegalArgumentException("未知的清單鍵：" + queueKey);
        }
    }

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
