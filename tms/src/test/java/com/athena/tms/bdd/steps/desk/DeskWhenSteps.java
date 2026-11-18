package com.athena.tms.bdd.steps.desk;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.DeskApi;
import com.athena.tms.bdd.support.DeskContext;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import io.cucumber.java.zh_tw.當;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 本模組 {@code 櫃檯看板與派單/dsl.md} 的 When 句型。
 *
 * <p>每一句只把請求送出去，不判斷成功或失敗；同一句有的案例要它成功、有的要它被拒絕，判斷留給 Then。
 * 會改單的三種寫入（派單、復原、當場回答）送出前先記下這張單的現況，
 * 讓「這次操作被拒絕」之類的 Then 能比對單與事件有沒有被動到。
 */
public class DeskWhenSteps {

    /** 派單方式的契約值：拖曳。 */
    private static final String VIA_DRAG = "drag";

    /** 按鈕派單的契約值，用在「不帶防重複鍵」與「重送」這兩句；介面根的派單句型預設也是按鈕。 */
    private static final String VIA_BUTTON = "button";

    /** 「以 N 個字的回覆當場回答」用來湊字數的字：中文字、不是空白，去掉頭尾空白後字數不變。 */
    private static final String ANSWER_FILLER_CHAR = "好";

    /** 「以只有空白的回覆當場回答」送出的回覆：3 個半形空白。 */
    private static final String BLANK_ANSWER = "   ";

    private final ScenarioWorld world;
    private final DeskApi deskApi;
    private final DeskContext deskContext;
    private final LastOperationGuard lastOperationGuard;
    private final DbProbe dbProbe;
    private final BddApiClient apiClient;
    private final TenantFixtures tenantFixtures;

    public DeskWhenSteps(
            ScenarioWorld world,
            DeskApi deskApi,
            DeskContext deskContext,
            LastOperationGuard lastOperationGuard,
            DbProbe dbProbe,
            BddApiClient apiClient,
            TenantFixtures tenantFixtures) {
        this.world = world;
        this.deskApi = deskApi;
        this.deskContext = deskContext;
        this.lastOperationGuard = lastOperationGuard;
        this.dbProbe = dbProbe;
        this.apiClient = apiClient;
        this.tenantFixtures = tenantFixtures;
    }

    @當("{string} 以拖曳把 {string} 派給 {string}")
    public void assignByDrag(String deskStaff, String taskKey, String deptTerm) {
        UUID taskId = resolveTaskId(taskKey);
        lastOperationGuard.captureFor(taskId);
        deskApi.assign(deskStaff, taskId, Terms.dept(deptTerm), VIA_DRAG);
    }

    @當("{string} 不帶防重複鍵把 {string} 派給 {string}")
    public void assignWithoutIdempotencyKey(String deskStaff, String taskKey, String deptTerm) {
        UUID taskId = resolveTaskId(taskKey);
        lastOperationGuard.captureFor(taskId);
        deskApi.assignWithoutKey(deskStaff, taskId, Terms.dept(deptTerm), VIA_BUTTON);
    }

    /**
     * 模擬網路逾時後拿同一把鍵重送：鍵取自前面那次派單，請求內容也要相同，
     * 而前面那次是介面根的 {@code 已把…派給…}，派單方式是按鈕。
     */
    @當("{string} 用同一把鍵把 {string} 派給 {string} 重送一次")
    public void resendAssignWithSameKey(String deskStaff, String taskKey, String deptTerm) {
        UUID taskId = resolveTaskId(taskKey);
        String idempotencyKey = deskContext.lastAssignIdempotencyKey();
        ScenarioWorld.ApiResponse first = deskContext.lastAssignResponse();
        lastOperationGuard.captureFor(taskId);
        ScenarioWorld.ApiResponse resent =
                deskApi.assign(deskStaff, taskId, Terms.dept(deptTerm), VIA_BUTTON, idempotencyKey);
        deskContext.rememberAssignResend(first, resent);
    }

    @當("{string} 不帶防重複鍵復原 {string} 的派單")
    public void undoAssignWithoutIdempotencyKey(String deskStaff, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        lastOperationGuard.captureFor(taskId);
        deskApi.undoAssignWithoutKey(deskStaff, taskId);
    }

    /** 回覆照字面送出、含前後空白，去掉頭尾空白是後端的規則。 */
    @當("{string} 當場回答 {string}，回覆 {string}")
    public void answerOnTheSpot(String staffName, String taskKey, String answer) {
        UUID taskId = resolveTaskId(taskKey);
        lastOperationGuard.captureFor(taskId);
        deskContext.rememberCompletedCountsBeforeAnswer(fieldStaffCompletedCounts());
        deskApi.answer(staffName, taskId, answer);
    }

    /** 「不算任何部門做掉的單」要比回答前後的完成數，所以回答前先讀一次每位房務、工務同仁的今日進度。 */
    private Map<String, Integer> fieldStaffCompletedCounts() {
        Map<String, Integer> counts = new HashMap<>();
        List<Map<String, Object>> staffRows = dbProbe.queryMaps(
                "SELECT id, name FROM staff_accounts WHERE tenant_id = ? AND active = true"
                        + " AND dept IN ('housekeeping', 'engineering')",
                tenantFixtures.testTenantId());
        for (Map<String, Object> staff : staffRows) {
            String name = String.valueOf(staff.get("name"));
            ScenarioWorld.ApiResponse response = apiClient.get("/me/today", BddApiClient.Auth.staff(world.jwt(name)));
            if (response.status() != 200 || response.json() == null) {
                throw new AssertionError("當場回答前讀 " + name + " 的 getMyToday 失敗，狀態碼=" + response.status()
                        + "，body=" + response.body());
            }
            counts.put(String.valueOf(staff.get("id")), response.json().path("completedCount").asInt());
        }
        return counts;
    }

    @當("{string} 以 {int} 個字的回覆當場回答 {string}")
    public void answerWithLength(String deskStaff, int length, String taskKey) {
        String answer = ANSWER_FILLER_CHAR.repeat(length);
        UUID taskId = resolveTaskId(taskKey);
        lastOperationGuard.captureFor(taskId);
        deskContext.rememberSentAnswer(answer);
        deskApi.answer(deskStaff, taskId, answer);
    }

    @當("{string} 以只有空白的回覆當場回答 {string}")
    public void answerWithBlank(String deskStaff, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        lastOperationGuard.captureFor(taskId);
        deskApi.answer(deskStaff, taskId, BLANK_ANSWER);
    }

    @當("{string} 不帶防重複鍵當場回答 {string}，回覆 {string}")
    public void answerWithoutIdempotencyKey(String deskStaff, String taskKey, String answer) {
        UUID taskId = resolveTaskId(taskKey);
        lastOperationGuard.captureFor(taskId);
        deskApi.answerWithoutKey(deskStaff, taskId, answer);
    }

    /**
     * 住客只打一段字、不選品項，這張單要先過分類規則才知道落在待派單還是某個部門的待認領。
     * 之後的句子用房號指這張單，所以送出成功後要記下房號對應的單。
     */
    @當("{string} 的住客打字送出 {string}")
    public void guestSubmitsText(String roomNo, String text) {
        ScenarioWorld.ApiResponse response = deskApi.createGuestRequest(roomNo, text, null);
        if (response.status() < 200 || response.status() > 299 || response.json() == null
                || !response.json().hasNonNull("id")) {
            // 送不出去就沒有單可以讓後面的句子檢查，當場停下比讓後面報「找不到任務」好懂。
            throw new AssertionError(
                    "「" + roomNo + "」的住客送出需求失敗，狀態碼=" + response.status() + "，body=" + response.body());
        }
        world.rememberTask(roomNo, response.json().get("id").asText());
    }

    @當("{string} 讀取 {string} 的櫃檯詳情")
    public void readDeskDetail(String staffName, String taskKey) {
        deskApi.readTask(staffName, resolveTaskId(taskKey));
    }

    @當("{string} 讀取一張不存在的單的櫃檯詳情")
    public void readMissingDeskDetail(String staffName) {
        UUID missingTaskId = UUID.randomUUID();
        lastOperationGuard.captureFor(missingTaskId);
        deskApi.readTask(staffName, missingTaskId);
    }

    @當("{string} 讀取常用回覆")
    public void readQuickReplies(String staffName) {
        lastOperationGuard.captureWithoutTask();
        deskApi.listQuickReplies(staffName);
    }

    /** 任務用房號指；修繕單用工務單號（{@code W-} 開頭）指。 */
    private UUID resolveTaskId(String key) {
        if (key.startsWith("W-")) {
            return UUID.fromString(world.repairTicketTaskId(key));
        }
        return UUID.fromString(world.taskId(key));
    }
}
