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
    private ApiResponse lastEngineeringPoolResponse;
    private ApiResponse lastInspectionResponse;
    private java.util.List<Map<String, Object>> lastInspectionItems;
    private ApiResponse lastVerifyResponse;
    private String lastVerifyManagerName;
    private String lastVerifyReason;
    private String lastAnswerId;
    private String lastEscalationId;
    private String lastIdempotencyKey;
    private java.util.Set<String> taskIdsBeforeGuestRequest;

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

    private final Map<String, String> staffPasswords = new HashMap<>();
    private final Map<String, String> temporaryPasswords = new HashMap<>();
    private final Map<String, Long> tenantIdsByCode = new HashMap<>();

    public void rememberStaffPassword(String name, String password) {
        staffPasswords.put(name, password);
    }

    public java.util.Optional<String> staffPassword(String name) {
        return java.util.Optional.ofNullable(staffPasswords.get(name));
    }

    public void rememberTemporaryPassword(String name, String password) {
        temporaryPasswords.put(name, password);
    }

    public String temporaryPassword(String name) {
        return required(temporaryPasswords, name, "臨時密碼");
    }

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

    private final Map<String, PhotoLabel> photoLabels = new HashMap<>();

    /** 照片標籤對到的照片；{@code content} 是最後一次成功上傳的位元組，沒有成功上傳過是 null。 */
    public record PhotoLabel(String photoId, byte[] content, java.time.OffsetDateTime uploadedAt) {
    }

    public void rememberPhoto(String label, PhotoLabel photo) {
        photoLabels.put(label, photo);
    }

    public PhotoLabel photo(String label) {
        PhotoLabel photo = photoLabels.get(label);
        if (photo == null) {
            throw new IllegalStateException("本案例沒有準備照片「" + label + "」，已知的照片：" + photoLabels.keySet());
        }
        return photo;
    }

    public java.util.Optional<PhotoLabel> findPhoto(String label) {
        return java.util.Optional.ofNullable(photoLabels.get(label));
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

    public void setLastEngineeringPoolResponse(ApiResponse response) {
        this.lastEngineeringPoolResponse = response;
    }

    public java.util.Optional<ApiResponse> lastEngineeringPoolResponse() {
        return java.util.Optional.ofNullable(lastEngineeringPoolResponse);
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

    public java.util.Set<String> taskIdsBeforeGuestRequest() {
        if (taskIdsBeforeGuestRequest == null) {
            throw new IllegalStateException("沒有住客送出需求之前的任務快照：前一個 When 要先記下");
        }
        return taskIdsBeforeGuestRequest;
    }

    public void setTaskIdsBeforeGuestRequest(java.util.Set<String> ids) {
        this.taskIdsBeforeGuestRequest = java.util.Set.copyOf(ids);
    }

    private static String required(Map<String, String> map, String key, String kind) {
        String v = map.get(key);
        if (v == null) {
            throw new IllegalStateException("找不到" + kind + "「" + key + "」，請先在 Given 建立");
        }
        return v;
    }

    public record ApiResponse(
            int status, String body, tools.jackson.databind.JsonNode json,
            org.springframework.http.HttpHeaders headers, byte[] bytes) {

        public ApiResponse(
                int status, String body, tools.jackson.databind.JsonNode json,
                org.springframework.http.HttpHeaders headers) {
            this(status, body, json, headers, null);
        }

        public ApiResponse(int status, String body, tools.jackson.databind.JsonNode json) {
            this(status, body, json, new org.springframework.http.HttpHeaders());
        }

        public String header(String name) {
            return headers == null ? null : headers.getFirst(name);
        }
    }
}
