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

/** 「待派單」模組的 Given 句型。 */
public class TriageGivenSteps {

    /** 規格只說要有這麼多張住客打字的需求，內容不影響結果；輪流用這幾句，同房不會撞標題。 */
    private static final List<String> GUEST_TEXTS = List.of(
            "房間有怪聲", "電視遙控器沒有反應", "想多一顆枕頭", "檯燈不亮", "窗簾拉不上");

    /** 建這些需求時替房間編的住客。 */
    private static final String GUEST_NAME = "王小明";

    /** 當場回答的內容由測試自編。 */
    private static final String ANSWER = "已經回覆住客了，請住客稍候。";

    /** 這幾句建的單一律派給房務；規格只數張數，派去哪個部門不影響結果。 */
    private static final String ASSIGN_DEPT = "housekeeping";

    private final WebWorld world;

    private final ShellFrame shell;

    private final TriagePage triage;

    public TriageGivenSteps(WebWorld world) {
        this.world = world;
        this.shell = new ShellFrame(world);
        this.triage = new TriagePage(world);
    }

    /**
     * 還沒登入時先登入並開該頁，已登入但不在該頁時換過去；再按該房那一列的「派給{部門}」，
     * 等派單請求成功、浮條出現。
     *
     * <p>浮條 5 秒後自己消失，後面的步驟靠的是步驟本身跑得比 5 秒快。這裡不把瀏覽器時鐘停住：
     * 假時鐘接管了 {@code setTimeout} 與 {@code requestAnimationFrame}，停住時畫面框架排在 {@code setTimeout(0)}
     * 的畫面更新不會跑，等兩個 {@code requestAnimationFrame} 的「等畫面畫完」也等不到，後面按「復原」之類的步驟會卡住。
     */
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

    /**
     * 先讓這個分頁停止跟上後端，再以同仁的身分在後端認領。
     * 順序不能反過來：先認領的話，畫面可能在暫停之前就收到即時訊號，就不是「還沒跟上」了。
     */
    @假設("{string} 已在手機上認領 {string}，{string} 的畫面還沒跟上")
    public void claimedOnPhoneUnseen(String colleague, String roomNo, String staffName) {
        world.network().pauseLiveUpdates(world.page());
        world.backend().postTaskAction(world.tasks().idOf(roomNo), "claim", world.staff(colleague));
    }

    /**
     * 在後端建好這麼多張住客打字的待派單，以這位員工的身分逐張派出或當場回答，
     * 走的是櫃檯畫面用的同一組後端端點，今天的張數就是後端照真的動作算出來的。
     * 做完確認全館沒有剩下待派單，否則空狀態不會出現，後面的檢查會錯在別的地方。
     */
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

    /**
     * 再建這麼多張住客打字的待派單：每一張先由這位員工派出、立刻復原，再由同事派出。
     * 後端的時鐘停在場景的時刻不會走，所以復原一定落在可以復原的 5 秒內。
     */
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

    /**
     * 在一間這個場景還沒用過的房建一張住客打字的待派單，掛在一筆今天在住的訂房上。
     * 回傳的單還沒登記，等呼叫端做完動作再以最後的狀況登記，別的步驟找「待派單」時才不會找到它。
     */
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

    /** 2 樓由 201 往上找第一間這個場景還沒有單、也還沒有訂房的房。 */
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
