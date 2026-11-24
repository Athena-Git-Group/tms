package com.athena.tms.bdd.steps.desk;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.DeskApi;
import com.athena.tms.bdd.support.DeskContext;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import com.athena.tms.service.GuestTenantConfigReader;
import io.cucumber.java.zh_tw.當;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DeskWhenSteps {

    private static final String VIA_DRAG = "drag";

    private static final String VIA_BUTTON = "button";

    private static final String ANSWER_FILLER_CHAR = "好";

    private static final String BLANK_ANSWER = "   ";

    private final ScenarioWorld world;
    private final DeskApi deskApi;
    private final DeskContext deskContext;
    private final LastOperationGuard lastOperationGuard;
    private final DbProbe dbProbe;
    private final BddApiClient apiClient;
    private final TenantFixtures tenantFixtures;
    private final GuestTenantConfigReader guestConfigReader;

    public DeskWhenSteps(
            ScenarioWorld world,
            DeskApi deskApi,
            DeskContext deskContext,
            LastOperationGuard lastOperationGuard,
            DbProbe dbProbe,
            BddApiClient apiClient,
            TenantFixtures tenantFixtures,
            GuestTenantConfigReader guestConfigReader) {
        this.world = world;
        this.deskApi = deskApi;
        this.deskContext = deskContext;
        this.lastOperationGuard = lastOperationGuard;
        this.dbProbe = dbProbe;
        this.apiClient = apiClient;
        this.tenantFixtures = tenantFixtures;
        this.guestConfigReader = guestConfigReader;
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

    @當("{string} 當場回答 {string}，回覆 {string}")
    public void answerOnTheSpot(String staffName, String taskKey, String answer) {
        UUID taskId = resolveTaskId(taskKey);
        lastOperationGuard.captureFor(taskId);
        deskContext.rememberCompletedCountsBeforeAnswer(fieldStaffCompletedCounts());
        deskApi.answer(staffName, taskId, answer);
    }

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

    @當("{string} 的住客打字送出 {string}")
    public void guestSubmitsText(String roomNo, String text) {
        ScenarioWorld.ApiResponse response = deskApi.createGuestRequest(roomNo, text, null);
        if (response.status() < 200 || response.status() > 299 || response.json() == null
                || !response.json().hasNonNull("id")) {
            throw new AssertionError(
                    "「" + roomNo + "」的住客送出需求失敗，狀態碼=" + response.status() + "，body=" + response.body());
        }
        world.rememberTask(roomNo, response.json().get("id").asText());
    }

    @當("{string} 的住客勾選服務項目 {string} 送出")
    public void guestSubmitsMenuItem(String roomNo, String label) {
        String itemId = guestConfigReader.requestMenu(tenantFixtures.testTenantId()).stream()
                .filter(item -> label.equals(item.label()))
                .map(GuestTenantConfigReader.MenuItem::key)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("住客頁的勾選清單沒有「" + label + "」"));
        ScenarioWorld.ApiResponse response = deskApi.createGuestMenuRequest(roomNo, itemId);
        if (response.status() < 200 || response.status() > 299 || response.json() == null
                || !response.json().hasNonNull("id")) {
            throw new AssertionError(
                    "「" + roomNo + "」的住客勾選「" + label + "」送出失敗，狀態碼=" + response.status() + "，body=" + response.body());
        }
        world.rememberTask(roomNo, response.json().get("id").asString());
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

    private UUID resolveTaskId(String key) {
        if (key.startsWith("W-")) {
            return UUID.fromString(world.repairTicketTaskId(key));
        }
        return UUID.fromString(world.taskId(key));
    }
}
