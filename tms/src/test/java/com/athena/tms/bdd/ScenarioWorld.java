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

    // 帳號與登入：案例裡看得到的明文密碼、臨時密碼與飯店內部編號，給登入、改密碼與查飯店的步驟互相取用。
    private final Map<String, String> staffPasswords = new HashMap<>();
    private final Map<String, String> temporaryPasswords = new HashMap<>();
    private final Map<String, Long> tenantIdsByCode = new HashMap<>();

    /** 記下這位員工目前的明文密碼；案例裡改過密碼就再記一次。 */
    public void rememberStaffPassword(String name, String password) {
        staffPasswords.put(name, password);
    }

    /** 這位員工目前的明文密碼；案例沒有指定過時是空的，呼叫端自己決定用建立帳號時的預設密碼。 */
    public java.util.Optional<String> staffPassword(String name) {
        return java.util.Optional.ofNullable(staffPasswords.get(name));
    }

    /** 記下這位員工在設定新密碼之前用的那組臨時密碼。 */
    public void rememberTemporaryPassword(String name, String password) {
        temporaryPasswords.put(name, password);
    }

    public String temporaryPassword(String name) {
        return required(temporaryPasswords, name, "臨時密碼");
    }

    /** 記下用識別代碼建立的飯店的內部編號 {@code tenants.id}。 */
    public void rememberTenant(String code, long tenantId) {
        tenantIdsByCode.put(code, tenantId);
    }

    public long tenantId(String code) {
        Long id = tenantIdsByCode.get(code);
        if (id == null) {
            throw new IllegalStateException("找不到識別代碼「" + code + "」的飯店，請先在 Given 建立");
        }
        return id;
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

    // 有些 Then 要知道上一個請求打了哪條路徑、送了什麼（例如拿同一組員工編號再登入一次來比對回應），所以和回應一起記。
    private ApiRequest lastRequest;

    public void setLastRequest(ApiRequest request) {
        this.lastRequest = request;
    }

    public ApiRequest lastRequest() {
        if (lastRequest == null) {
            throw new IllegalStateException("本 scenario 尚未發出任何 API 呼叫");
        }
        return lastRequest;
    }

    /** 送出去的請求：路徑不含 {@code /api/v1} 前綴；{@code body} 是序列化前的物件，沒有內容時是 null。 */
    public record ApiRequest(String method, String path, Object body) {
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
