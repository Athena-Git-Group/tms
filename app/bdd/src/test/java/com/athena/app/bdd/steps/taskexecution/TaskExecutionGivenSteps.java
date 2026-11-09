package com.athena.app.bdd.steps.taskexecution;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.java.zh_tw.假設;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 任務執行模組的 Given。 */
public class TaskExecutionGivenSteps {

    /** 這一句自己建的任務用什麼內容與類別：SOP 清單只掛在打掃類任務上。 */
    private static final String TASK_TITLE = "退房清潔";

    private static final String CLEANING_CATEGORY = "打掃";

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    /**
     * App 各頁的根 accessibility id。給「畫面沒有跟著後端一起更新」這種前提用：要知道畫面
     * 停在哪，得先知道有哪幾層頁面可能掛在畫面樹上。
     */
    private static final List<String> SCREEN_ROOT_IDS = List.of(
            "home.screen", "task.screen", "pool.screen", "ticket.screen",
            "celebrate.screen", "notification.screen", "me.screen");

    private final AppiumWorld world;

    public TaskExecutionGivenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 怎麼做：把這個 SOP 項標記成跳過，讓 App 以該員工身分登入後停在該任務頁。
     * 權威狀態落地：後端 {@code task_sop_checks} 該項為 skipped。
     *
     * <p>這一句沒有搭配的「名下有以下任務」，所以受測任務由這裡自己建：一張該員工
     * 名下、進行中的打掃任務。跳過走後端正式的 SOP 勾記端點，不直接改資料列，這樣
     * 勾記事件，以及從事件整理出來、方便查詢的那份目前勾記狀態，才跟員工自己在畫面上
     * 跳過一模一樣。
     *
     * <p>場景點名的 SOP 項不一定在租戶的打掃 SOP 項裡，所以先補一筆再跳過，
     * 作法見 {@link BackendChannel#ensureCleaningSopItemId}。
     */
    @假設("{string} 已在 App 跳過 {string} 的 SOP 項 {string}")
    public void alreadySkippedSop(String staffName, String task, String sopItem) {
        world.backend().ensureCleaningSopItemId(sopItem);
        world.seedTasks(staffName, List.of(new AppiumWorld.TaskFixture(
                task, TASK_TITLE, CLEANING_CATEGORY, null, "進行中", 0, null, null, false)));

        BackendChannel.CreatedStaff staff = world.staff(staffName);
        world.ensureOnTaskDetail(staff, task);
        UUID taskId = (UUID) world.taskRowByRoomNo(task).get("id");
        world.backend().skipSopItem(taskId, staff, sopItem);
    }

    /**
     * 怎麼做：以該員工身分登入，打開該任務的任務頁並點主按鈕「完成」，停在完成畫面
     * 不再操作。權威狀態落地：後端該任務已依 {@code tasksComplete} 語意轉為 done 或
     * inspected。accessibility id：{@code task.primary-button}、{@code celebrate.screen}。
     *
     * <p>這一句是**線上**完成，所以後端狀態真的翻了；同檔那句「已在離線時完成…還押在
     * 手機上」後端狀態不變，兩句不可互換。
     *
     * <p>有幾條 Rule 的受測動作是「從完成畫面再往下走」（按下一步、回首頁），完成本身
     * 因此降為前置狀態。這裡只按下「完成」那一下，按完只等畫面與後端落地，不碰
     * {@code celebrate.next-button} 或 {@code celebrate.back-home-button}——那兩顆是後面
     * 的 When 要按的，這裡先按掉就等於偷跑掉受測動作。
     *
     * <p>按完要等後端真的翻成 done／inspected 才往下走：完成是一次 HTTP 來回，畫面
     * 先疊出完成覆蓋層、後端稍後才落地的話，接著的「下一步」會算在一張還是進行中的
     * 任務上。
     */
    @假設("{string} 剛在 App 完成 {string}，完成畫面停在畫面上")
    public void justCompletedTaskStayingOnCelebrate(String staffName, String task) {
        world.ensureOnTaskDetail(world.staff(staffName), task);
        world.tapTestId("task.primary-button", SCREEN_TIMEOUT);
        world.waitForTestId("celebrate.screen", SCREEN_TIMEOUT);

        String state = awaitCompletedState(task, SCREEN_TIMEOUT);
        if (!"done".equals(state) && !"inspected".equals(state)) {
            throw new AssertionError("在 App 按下「完成」後，後端沒有把「" + task
                    + "」轉為 done／inspected，目前是「" + state + "」（逾時 "
                    + SCREEN_TIMEOUT.getSeconds() + "s）");
        }

        // 停在完成畫面不再操作：這裡只確認覆蓋層還在，不按它上面任何按鈕。
        world.waitForTestId("celebrate.screen", SCREEN_TIMEOUT);
    }

    /**
     * 怎麼做：以該員工身分登入，從首頁點該房的任務卡進入任務頁，停在任務頁。
     * 回寫：畫面停在任務頁。accessibility id：{@code task.screen}。
     *
     * <p>這是 Arrange——給「畫面已開著、世界在背後改變」的情境鋪底（例如任務在背後被
     * 主管作廢）。開頁本身是受測動作時用同名的 When（{@code 在 App 打開 ... 的任務頁}，
     * 在 steps/common），兩句句型只差一個「已」字、刻意不共用 annotation；進頁的路
     * 則是同一條（{@link AppiumWorld#ensureOnTaskDetail}）。
     */
    @假設("{string} 已在 App 打開 {string} 的任務頁")
    public void alreadyOnTaskDetail(String staffName, String task) {
        world.ensureOnTaskDetail(world.staff(staffName), task);
    }

    /**
     * 怎麼做：在該員工的任務頁已開著之後，透過等效的主管作廢操作（正式的作廢端點
     * {@code tasksCancel}，原因 {@code manager_void}）把該任務轉為已取消；不點開任何
     * 推播、不觸發畫面刷新。
     * 權威狀態落地：後端該任務 cancelled（{@code cancel_reason} = manager_void），
     * 該員工畫面仍停在作廢前的任務頁（{@code task.screen}）。
     *
     * <p>作廢走正式端點而不是直接 UPDATE 資料列，狀態轉換、事件，以及從事件整理出來的
     * {@code cancel_reason}，才跟主管真的在後台作廢一模一樣（打法同 {@link BackendChannel}
     * 鋪「已取消」fixture 的那一段）。作廢會照產品碼往 push outbox 排一筆「已被主管作廢」，但
     * harness 的推播要靠測試後門才會送進裝置，這裡不送——「畫面還沒更新」靠的就是
     * 裝置什麼都沒收到，App 不會偷偷跟上權威狀態。
     *
     * <p>這一句只碰後端、完全不碰 App：畫面那一側只被動確認 {@code task.screen} 還在，
     * 不重進任務頁（重進會觸發重新抓資料，畫面就跟上了，受測的「完成被後端拒收」
     * 情境會失去前提）。
     */
    @假設("{string} 剛被主管作廢，{string} 的畫面還沒更新")
    public void justVoidedByManagerWhileScreenStale(String task, String staffName) {
        UUID taskId = (UUID) world.taskRowByRoomNo(task).get("id");
        world.backend().postTaskAction(
                taskId, "cancel", world.staff(staffName), Map.of("reason", "manager_void"));
        // 端點目前沒把 cancel_reason 寫進 tasks 列，先用測試資料補上
        // （缺口說明見 BackendChannel#supplementCancelReason）。
        world.backend().supplementCancelReason(taskId, "manager_void");

        Map<String, Object> row = world.taskRowByRoomNo(task);
        String state = String.valueOf(row.get("state"));
        String cancelReason = String.valueOf(row.get("cancel_reason"));
        if (!"cancelled".equals(state) || !"manager_void".equals(cancelReason)) {
            throw new AssertionError("主管作廢後，後端「" + task
                    + "」應為 cancelled（cancel_reason=manager_void），實際 state=" + state
                    + "、cancel_reason=" + cancelReason);
        }

        // 畫面仍停在作廢前的任務頁：只確認元素還在，不做任何會觸發刷新的操作。
        world.waitForTestId("task.screen", SCREEN_TIMEOUT);
    }

    /**
     * 怎麼做：在該員工的畫面已經停在某一頁之後，另建一位同部門員工，以主管身分呼叫正式的
     * 改派端點（{@code tasksReassign}，{@code POST /tasks/{id}/reassign}，body 帶
     * {@code assigneeId}）把該任務改派過去；不點開任何推播、不觸發畫面刷新。
     * 權威狀態落地：後端該任務的 {@code assignee_id} 已換成新的人、{@code state} 與改派前
     * 相同；原本那位員工的畫面仍停在改派前的內容。
     *
     * <p>這一句跟同檔的「剛被主管作廢」是兩件事，不共用 annotation 也不共用落地碼：作廢把
     * 任務推到 cancelled（還要補 {@code cancel_reason}），改派只換負責人、狀態一個字都不動。
     * 受測的行為也差在這裡——作廢讓員工手上的動作被後端拒收，改派讓員工以為還在名下的
     * 下一間其實已經換人。
     *
     * <p>接手人另外建、不挑劇本裡現成的員工：句型的參數只有任務與原負責人，劇本沒有指名
     * 接手人。建成同部門（照原負責人的 {@code dept}）才符合改派的業務前提，跨部門的人接不了
     * 這張單。主管同樣另外建，而且刻意不登記進 {@link AppiumWorld#staff} 的名冊——那份名冊
     * 是劇本宣告過的員工，混進一位測試自己造的主管會讓 {@link AppiumWorld#onlyManager}
     * 從此數不準。
     *
     * <p>改派走正式端點而不是直接 UPDATE 資料列，狀態前提（後端只讓 accepted 的單換人）、
     * {@code reassigned} 事件與兩邊的推播才跟主管真的在後台改派一模一樣。改派會照產品碼往
     * push outbox 排「已改派走」與「改派給您」各一筆，但 harness 的推播要靠測試後門才會送進
     * 裝置，這裡不送——「畫面還沒更新」靠的就是裝置什麼都沒收到。
     *
     * <p>這一句只碰後端、完全不碰 App：畫面那一側只被動確認原本那幾層畫面還在，不重進任何
     * 頁（重進會觸發重新抓資料，畫面就跟上了，受測的「按下一步才發現已經不在名下」情境會
     * 失去前提）。
     */
    @假設("{string} 剛被主管改派給別人，{string} 的畫面還沒更新")
    public void justReassignedByManagerWhileScreenStale(String task, String staffName) {
        Map<String, Object> before = world.taskRowByRoomNo(task);
        UUID taskId = (UUID) before.get("id");
        String stateBefore = String.valueOf(before.get("state"));
        String assigneeBefore = String.valueOf(before.get("assignee_id"));
        List<String> screensBefore = visibleScreens();
        if (screensBefore.isEmpty()) {
            throw new AssertionError("改派前在「" + staffName + "」的畫面上一層頁面都認不出來"
                    + "（認得的頁面根 accessibility id 列在 SCREEN_ROOT_IDS）。畫面停在哪都不知道，"
                    + "就無從確認它有沒有跟著後端一起更新；改派前後都是空清單時這一句會真空通過");
        }

        BackendChannel backend = world.backend();
        BackendChannel.CreatedStaff successor = backend.createFieldStaff(
                "改派接手人", world.staff(staffName).dept(), "active");
        BackendChannel.CreatedStaff manager = backend.createManagerLikeStaff(
                "改派主管", BackendChannel.role("主管"), "active");
        backend.postTaskAction(taskId, "reassign", manager, Map.of("assigneeId", successor.id()));

        Map<String, Object> after = world.taskRowByRoomNo(task);
        String assigneeAfter = String.valueOf(after.get("assignee_id"));
        String stateAfter = String.valueOf(after.get("state"));
        if (!successor.id().toString().equalsIgnoreCase(assigneeAfter)) {
            throw new AssertionError("主管改派後，後端「" + task + "」的 assignee_id 應換成新負責人 "
                    + successor.id() + "，實際是 " + assigneeAfter + "（改派前是 " + assigneeBefore + "）");
        }
        if (!stateBefore.equals(stateAfter)) {
            throw new AssertionError("改派只換負責人、不動狀態，但後端「" + task + "」的 state 從 "
                    + stateBefore + " 變成 " + stateAfter);
        }

        // 畫面仍停在改派前的內容：只被動確認原本那幾層畫面還在，不做任何會觸發刷新的操作。
        List<String> screensAfter = visibleScreens();
        if (!screensBefore.equals(screensAfter)) {
            throw new AssertionError("改派不該讓「" + staffName + "」的畫面跟著更新，但畫面從 "
                    + screensBefore + " 變成 " + screensAfter);
        }
    }

    /**
     * 目前畫面樹上掛著哪幾層頁面，由 App 的頁面根 accessibility id 判斷。只用
     * {@code findElements} 被動探測，不等待、不捲動、不點任何東西——這支的用途就是確認
     * 「畫面沒跟著後端一起變」，探測本身一旦碰到畫面就毀掉自己要驗的前提。
     */
    private List<String> visibleScreens() {
        return SCREEN_ROOT_IDS.stream()
                .filter(id -> !world.driver().findElements(world.byTestId(id)).isEmpty())
                .toList();
    }

    /**
     * 怎麼做：透過後端測試資料把租戶設定 {@code completion_celebration_enabled} 設為 false。
     * 權威狀態落地：{@code tenant_config} 該鍵為 false；{@code tasksComplete} 回應的
     * {@code celebrationEnabled} 為 false。
     *
     * <p>這一鍵只關氣氛，不關接力。接續去向與今日進度的計算不受它影響，所以這裡只寫
     * 這一個鍵，不碰任務資料。受測任務由同場景的「名下有以下任務」自己鋪。
     *
     * <p>設定沒有員工視角的端點可走，所以照 {@link BackendChannel#setTenantConfig} 直接
     * 落地設定列。每個場景開場會 TRUNCATE 全部業務表（{@code tenant_config} 也在其中），
     * 這一鍵不必在場景結束時收回。
     */
    @假設("飯店關閉了完成慶祝的氣氛呈現")
    public void celebrationDisabled() {
        BackendChannel backend = world.backend();
        backend.setTenantConfig(backend.ensureTenant(), "completion_celebration_enabled", false);
    }

    /** 等後端把該房的任務翻成完成態，回傳最後讀到的狀態（逾時就回最後一次讀到的值）。 */
    private String awaitCompletedState(String roomNo, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        String state = String.valueOf(world.taskRowByRoomNo(roomNo).get("state"));
        while (!"done".equals(state) && !"inspected".equals(state)
                && System.nanoTime() < deadline) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等後端落地完成狀態時被中斷", e);
            }
            state = String.valueOf(world.taskRowByRoomNo(roomNo).get("state"));
        }
        return state;
    }

    /**
     * 怎麼做：把裝置切到無網路（測試後門的 {@code /offline}），以該員工身分打開該任務的
     * 任務頁並點主按鈕「完成」，停在完成畫面。
     * 權威狀態落地：App 本機佇列有這筆完成動作，<b>後端該任務狀態還沒有改變</b>。
     *
     * <p>這一句和「{@code 剛在 App 完成 ...，完成畫面停在畫面上}」的差別就在後端狀態有
     * 沒有變，兩句不可互換：那一句是連著線完成，後端已經依 {@code tasksComplete} 語意轉成
     * done 或 inspected；這一句完成只押在手機上，後端還停在 doing，受測動作是之後恢復連線
     * 時這一筆有沒有真的送出去。所以離線切換要在點「完成」<b>之前</b>就生效，不是事後補切。
     *
     * <p>切離線之前先登入，再把測試資料欠的「在 App 按開始」補完
     * （{@code settleDeferredStarts}）：App 拒絕離線登入，而開工也是離線時被擋下來的
     * 動作，兩件事在裝置離線之後都再也做不成。這一句的參數裡就有當事員工，所以直接
     * 指名登入她，不交給離線切換那一側去推定。
     *
     * <p>停在完成畫面以覆蓋層 {@code celebrate.screen} 出現為準，再回頭讀一次後端那一列，
     * 確認狀態仍是 {@code doing}。不查這一項的話，App 要是其實偷偷送出去了（或根本沒離線），
     * 這個前置狀態會變成「後端已完成」，後面那句「恢復連線後真的送到後端」就算 App 什麼都
     * 沒送也會過關。
     */
    @假設("{string} 已在離線時完成 {string}，這一筆還押在手機上")
    public void alreadyCompletedWhileOffline(String staffName, String task) {
        BackendChannel.CreatedStaff staff = world.staff(staffName);
        world.ensureLoggedInAs(staff);
        world.settleDeferredStarts();
        world.goOffline();

        world.ensureOnTaskDetail(staff, task);
        world.tapTestId("task.primary-button", SCREEN_TIMEOUT);
        world.waitForTestId("celebrate.screen", SCREEN_TIMEOUT);

        assertBackendStateUnchanged(task);
    }

    /** 權威讀回：離線完成不該動到後端，該任務這時仍必須是進行中（{@code doing}）。 */
    private void assertBackendStateUnchanged(String task) {
        String state = String.valueOf(world.taskRowByRoomNo(task).get("state"));
        if (!"doing".equals(state)) {
            throw new AssertionError("離線完成不該改到後端，但「" + task + "」在後端已經是「"
                    + state + "」");
        }
    }

    /**
     * 怎麼做：把這張單的中斷事件與任務時間戳整批往前推該分鐘數，裝置與後端的時鐘都不動。
     * 權威狀態落地：目前時刻離那筆中斷事件已過該分鐘數；{@code worked_sec} 與
     * {@code segment_started_at} 不變——中斷期間不累計工時。
     *
     * <p>為什麼不撥時鐘。撥裝置時鐘（測試後門的 {@code POST /clock}）只動得到 App 那一邊，
     * 後端的恢復端點寫 {@code segment_started_at} 用的是後端自己的系統時鐘。恢復之後裝置
     * 時間比後端的起算點快了該分鐘數，中斷的那一段就全被算成已做時間。注入值還是固定的
     * 一個時刻，畫面上的計時從此不再前進，連「計時正在走」都驗不出來。把資料往前推沒有
     * 這兩個問題：兩邊的時鐘都還在正常走，差距只存在於資料本身。
     *
     * <p>整張單的事件推同一個量，所以中斷事件也跟著後退該分鐘數，從現在回看就是「N 分鐘
     * 前中斷的」，誤差只有鋪資料到這一句之間的那不到一秒。推之前先讀那筆中斷事件
     * （{@code task_events.type = 'interrupted'}）確認它在：劇本在還沒中斷的單上說
     * 「已中斷 N 分鐘」時，往前推時間不會失敗，只會悄悄推一張狀態不對的單。
     */
    @假設("{string} 已中斷 {int} 分鐘")
    public void taskInterruptedForMinutes(String task, int minutes) {
        UUID taskId = (UUID) world.taskRowByRoomNo(task).get("id");
        lastInterruptedAt(taskId);
        world.backend().backdateTaskTimeline(taskId, minutes);
    }

    /**
     * 權威讀回：這張單最後一筆中斷事件的時刻。查不到就噴錯——那代表劇本在還沒中斷的
     * 單上說「已中斷 N 分鐘」，沒有基準時刻可以往前推。
     */
    private OffsetDateTime lastInterruptedAt(UUID taskId) {
        List<Map<String, Object>> rows = world.backend().queryRows("""
                SELECT created_at FROM task_events
                 WHERE tenant_id = ? AND task_id = ? AND type = 'interrupted'
                 ORDER BY seq DESC
                 LIMIT 1
                """, world.backend().ensureTenant(), taskId);
        if (rows.isEmpty()) {
            throw new IllegalStateException(
                    "後端查無這張單的中斷事件（task_events.type=interrupted），task_id=" + taskId
                            + "——場景是否在還沒中斷的單上說「已中斷 N 分鐘」？");
        }
        Object createdAt = rows.get(0).get("created_at");
        return createdAt instanceof Timestamp timestamp
                ? timestamp.toLocalDateTime().atZone(ZoneId.systemDefault()).toOffsetDateTime()
                : (OffsetDateTime) createdAt;
    }
}
