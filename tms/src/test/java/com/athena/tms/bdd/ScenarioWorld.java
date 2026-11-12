package com.athena.tms.bdd;

import java.util.HashMap;
import java.util.Map;

public class ScenarioWorld {

    private final Map<String, String> staffIds = new HashMap<>();
    private final Map<String, String> staffJwts = new HashMap<>();
    private final Map<String, String> taskIds = new HashMap<>();
    private final Map<String, String> repairTicketTaskIds = new HashMap<>();
    private final java.util.List<String> photoIds = new java.util.ArrayList<>();

    private ApiResponse lastResponse;
    private ApiResponse lastManagerQueuesResponse;
    private ApiResponse lastInspectionResponse;
    private java.util.List<Map<String, Object>> lastInspectionItems;
    private ApiResponse lastVerifyResponse;
    private String lastVerifyManagerName;
    private String lastVerifyReason;
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

    public void setLastManagerQueuesResponse(ApiResponse response) {
        this.lastManagerQueuesResponse = response;
    }

    public ApiResponse lastManagerQueuesResponse() {
        if (lastManagerQueuesResponse == null) {
            throw new IllegalStateException("本 scenario 尚未查看主管待辦");
        }
        return lastManagerQueuesResponse;
    }

    public void setLastInspectionResponse(ApiResponse response) {
        this.lastInspectionResponse = response;
    }

    public ApiResponse lastInspectionResponse() {
        if (lastInspectionResponse == null) {
            throw new IllegalStateException("本 scenario 尚未送出查房");
        }
        return lastInspectionResponse;
    }

    public void setLastInspectionItems(java.util.List<Map<String, Object>> items) {
        this.lastInspectionItems = items;
    }

    public java.util.List<Map<String, Object>> lastInspectionItems() {
        if (lastInspectionItems == null) {
            throw new IllegalStateException("本 scenario 尚未送出查房");
        }
        return lastInspectionItems;
    }

    public void setLastVerifyResponse(ApiResponse response) {
        this.lastVerifyResponse = response;
    }

    public void setLastVerifyRequest(String managerName, String reason) {
        this.lastVerifyManagerName = managerName;
        this.lastVerifyReason = reason;
    }

    public String lastVerifyManagerName() {
        if (lastVerifyManagerName == null) {
            throw new IllegalStateException("本 scenario 尚未送出驗收");
        }
        return lastVerifyManagerName;
    }

    public String lastVerifyReason() {
        return lastVerifyReason;
    }

    public ApiResponse lastVerifyResponse() {
        if (lastVerifyResponse == null) {
            throw new IllegalStateException("本 scenario 尚未送出驗收");
        }
        return lastVerifyResponse;
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
