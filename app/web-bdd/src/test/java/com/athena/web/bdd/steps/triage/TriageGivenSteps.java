package com.athena.web.bdd.steps.triage;

import com.athena.bddsupport.BackendChannel;
import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.bddsupport.TaskSituation;
import com.athena.bddsupport.TaskSituationSpec;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.TriagePage;
import com.athena.web.bdd.support.Clocks;
import com.athena.web.bdd.support.ScenarioTasks;
import com.athena.web.bdd.support.WebWorld;
import io.cucumber.java.zh_tw.假設;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TriageGivenSteps {

    private static final List<String> GUEST_TEXTS = List.of(
            "房間有怪聲", "電視遙控器沒有反應", "想多一顆枕頭", "檯燈不亮", "窗簾拉不上");

    private static final String GUEST_NAME = "王小明";

    private static final String ANSWER = "已經回覆住客了，請住客稍候。";

    private static final String ASSIGN_DEPT = "housekeeping";

    private final WebWorld world;

    private final ShellFrame shell;

    private final TriagePage triage;

    public TriageGivenSteps(WebWorld world) {
        this.world = world;
        this.shell = new ShellFrame(world);
        this.triage = new TriagePage(world);
    }

    @假設("{string} 剛在 {string} 把 {string} 派給 {string}，派單浮條還在")
    public void justAssigned(String staffName, String pageName, String roomNo, String deptTerm) {
        if (!"待派單頁".equals(pageName.strip())) {
            throw new IllegalArgumentException("按鈕派單只在待派單頁的列上，收到「" + pageName + "」");
        }
        if (!shell.loggedIn()) {
            shell.loginAndOpen(world.staff(staffName), pageName);
        } else if (!shell.onPage(pageName)) {
            shell.openAndWait(pageName);
        }
        shell.waitUntilLoaded();
        triage.assignAndWaitForToast(roomNo, world.tasks().idOf(roomNo), deptTerm);
    }

    @假設("{string} 已在手機上認領 {string}，{string} 的畫面還沒跟上")
    public void claimedOnPhoneUnseen(String colleague, String roomNo, String staffName) {
        world.network().pauseLiveUpdates(world.page());
        world.backend().postTaskAction(world.tasks().idOf(roomNo), "claim", world.staff(colleague));
    }

    @假設("{string} 今天已派出 {int} 張單、當場回答 {int} 張單，待派單現在是空的")
    public void handledToday(String staffName, int assignedCount, int answeredCount) {
        CreatedStaff staff = world.staff(staffName);
        for (int i = 0; i < assignedCount; i++) {
            ScenarioTasks.Entry created = createGuestTriage();
            world.backend().deskAssign(created.taskId(), staff, ASSIGN_DEPT, "button");
            register(created, "待認領");
        }
        for (int i = 0; i < answeredCount; i++) {
            ScenarioTasks.Entry created = createGuestTriage();
            world.backend().deskAnswer(created.taskId(), staff, ANSWER);
            register(created, "已回答");
        }
        requireNoTriageLeft();
    }

    @假設("{string} 今天另有 {int} 次派單被她自己復原，那張單後來由 {string} 派走")
    public void undoneThenTakenByColleague(String staffName, int times, String colleagueName) {
        CreatedStaff staff = world.staff(staffName);
        CreatedStaff colleague = world.staff(colleagueName);
        for (int i = 0; i < times; i++) {
            ScenarioTasks.Entry created = createGuestTriage();
            world.backend().deskAssign(created.taskId(), staff, ASSIGN_DEPT, "button");
            world.backend().deskUndoAssign(created.taskId(), staff);
            world.backend().deskAssign(created.taskId(), colleague, ASSIGN_DEPT, "button");
            register(created, "待認領");
        }
    }

    private ScenarioTasks.Entry createGuestTriage() {
        String roomNo = unusedRoom();
        String text = GUEST_TEXTS.get(world.tasks().all().size() % GUEST_TEXTS.size());
        LocalDate today = world.clocks().today();
        UUID reservationId = world.backend().createReservation(roomNo, GUEST_NAME, false,
                Clocks.at(today.minusDays(1), LocalTime.of(15, 0)),
                Clocks.at(today.plusDays(1), LocalTime.of(11, 0)));
        world.tasks().rememberReservation(roomNo, reservationId);
        TaskSituationSpec spec = TaskSituationSpec.of(roomNo, BackendChannel.taskType("其他"), "guest")
                .dept("frontdesk")
                .guestText(text, "zh-TW", null)
                .reservationId(reservationId);
        UUID taskId = world.backend().createTaskInSituation(TaskSituation.TRIAGE, spec);
        return new ScenarioTasks.Entry(roomNo, text, taskId, TaskSituation.TRIAGE.term());
    }

    private void register(ScenarioTasks.Entry created, String situation) {
        world.tasks().add(new ScenarioTasks.Entry(created.roomNo(), created.title(), created.taskId(), situation));
    }

    private String unusedRoom() {
        for (int n = 201; n <= 299; n++) {
            String roomNo = String.valueOf(n);
            if (world.tasks().entriesIn(roomNo).isEmpty() && world.tasks().reservationOf(roomNo).isEmpty()) {
                return roomNo;
            }
        }
        throw new IllegalStateException("2 樓的房都已經有單，找不到空的房來建需求");
    }

    private void requireNoTriageLeft() {
        List<Map<String, Object>> left = world.backend().queryRows(
                "SELECT id FROM tasks WHERE tenant_id = ? AND state = 'triage'", world.defaults().tenantId());
        if (!left.isEmpty()) {
            throw new IllegalStateException("待派單應該是空的，後端還有 " + left.size() + " 張待派單：" + left);
        }
    }
}
