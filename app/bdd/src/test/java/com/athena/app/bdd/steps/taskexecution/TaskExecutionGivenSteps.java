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

public class TaskExecutionGivenSteps {

    private static final String TASK_TITLE = "退房清潔";

    private static final String CLEANING_CATEGORY = "打掃";

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    private static final List<String> SCREEN_ROOT_IDS = List.of(
            "home.screen", "task.screen", "pool.screen", "ticket.screen",
            "celebrate.screen", "notification.screen", "me.screen");

    private final AppiumWorld world;

    public TaskExecutionGivenSteps(AppiumWorld world) {
        this.world = world;
    }

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

        world.waitForTestId("celebrate.screen", SCREEN_TIMEOUT);
    }

    @假設("{string} 已在 App 打開 {string} 的任務頁")
    public void alreadyOnTaskDetail(String staffName, String task) {
        world.ensureOnTaskDetail(world.staff(staffName), task);
    }

    @假設("{string} 剛被主管作廢，{string} 的畫面還沒更新")
    public void justVoidedByManagerWhileScreenStale(String task, String staffName) {
        UUID taskId = (UUID) world.taskRowByRoomNo(task).get("id");
        world.backend().postTaskAction(
                taskId, "cancel", world.staff(staffName), Map.of("reason", "manager_void"));

        Map<String, Object> row = world.taskRowByRoomNo(task);
        String state = String.valueOf(row.get("state"));
        String cancelReason = String.valueOf(row.get("cancel_reason"));
        if (!"cancelled".equals(state) || !"manager_void".equals(cancelReason)) {
            throw new AssertionError("主管作廢後，後端「" + task
                    + "」應為 cancelled（cancel_reason=manager_void），實際 state=" + state
                    + "、cancel_reason=" + cancelReason);
        }

        world.waitForTestId("task.screen", SCREEN_TIMEOUT);
    }

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

        List<String> screensAfter = visibleScreens();
        if (!screensBefore.equals(screensAfter)) {
            throw new AssertionError("改派不該讓「" + staffName + "」的畫面跟著更新，但畫面從 "
                    + screensBefore + " 變成 " + screensAfter);
        }
    }

    private List<String> visibleScreens() {
        return SCREEN_ROOT_IDS.stream()
                .filter(id -> !world.driver().findElements(world.byTestId(id)).isEmpty())
                .toList();
    }

    @假設("飯店關閉了完成慶祝的氣氛呈現")
    public void celebrationDisabled() {
        BackendChannel backend = world.backend();
        backend.setTenantConfig(backend.ensureTenant(), "completion_celebration_enabled", false);
    }

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

    private void assertBackendStateUnchanged(String task) {
        String state = String.valueOf(world.taskRowByRoomNo(task).get("state"));
        if (!"doing".equals(state)) {
            throw new AssertionError("離線完成不該改到後端，但「" + task + "」在後端已經是「"
                    + state + "」");
        }
    }

    @假設("{string} 已中斷 {int} 分鐘")
    public void taskInterruptedForMinutes(String task, int minutes) {
        UUID taskId = (UUID) world.taskRowByRoomNo(task).get("id");
        lastInterruptedAt(taskId);
        world.backend().backdateTaskTimeline(taskId, minutes);
    }

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
