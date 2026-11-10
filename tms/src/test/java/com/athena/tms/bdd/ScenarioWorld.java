package com.athena.tms.bdd;

import java.util.HashMap;
import java.util.Map;

/**
 * 情境上下文：一個 scenario 一份，記錄名稱到識別碼的對照與最後一次互動結果。
 * 只做資料持有，不含任何 Given/When/Then 語意。
 */
public class ScenarioWorld {

    private final Map<String, String> staffIds = new HashMap<>();
    private final Map<String, String> staffJwts = new HashMap<>();
    /** 房號（或無房號時的內容） -> task id */
    private final Map<String, String> taskIds = new HashMap<>();
    /** 工務單號（W-NNNN） -> task id */
    private final Map<String, String> repairTicketTaskIds = new HashMap<>();
    private final java.util.List<String> photoIds = new java.util.ArrayList<>();

    private ApiResponse lastResponse;
    private String lastAnswerId;
    private String lastEscalationId;
    private String lastIdempotencyKey;

    public void rememberStaff(String name, String staffId, String jwt) {
        staffIds.put(name, staffId);
        staffJwts.put(name, jwt);
    }

    public String staffId(String name) {
        return required(staffIds, name, "員工");
    }

    public String jwt(String name) {
        return required(staffJwts, name, "員工 JWT");
    }

    public void rememberTask(String key, String taskId) {
        taskIds.put(key, taskId);
    }

    public String taskId(String key) {
        return required(taskIds, key, "任務");
    }

    public boolean hasTask(String key) {
        return taskIds.containsKey(key);
    }

    public void rememberRepairTicket(String ticketNo, String taskId) {
        repairTicketTaskIds.put(ticketNo, taskId);
    }

    public String repairTicketTaskId(String ticketNo) {
        return required(repairTicketTaskIds, ticketNo, "工務單");
    }

    public boolean hasRepairTicket(String ticketNo) {
        return repairTicketTaskIds.containsKey(ticketNo);
    }

    public java.util.List<String> photoIds() {
        return photoIds;
    }

    public void setLastResponse(ApiResponse response) {
        this.lastResponse = response;
    }

    public ApiResponse lastResponse() {
        if (lastResponse == null) {
            throw new IllegalStateException("本 scenario 尚未發出任何 API 呼叫");
        }
        return lastResponse;
    }

    public String lastAnswerId() {
        return lastAnswerId;
    }

    public void setLastAnswerId(String id) {
        this.lastAnswerId = id;
    }

    public String lastEscalationId() {
        return lastEscalationId;
    }

    public void setLastEscalationId(String id) {
        this.lastEscalationId = id;
    }

    public String lastIdempotencyKey() {
        return lastIdempotencyKey;
    }

    public void setLastIdempotencyKey(String key) {
        this.lastIdempotencyKey = key;
    }

    private static String required(Map<String, String> map, String key, String kind) {
        String v = map.get(key);
        if (v == null) {
            throw new IllegalStateException("找不到" + kind + "「" + key + "」，請先在 Given 建立");
        }
        return v;
    }

    public record ApiResponse(int status, String body, tools.jackson.databind.JsonNode json) {
    }
}
