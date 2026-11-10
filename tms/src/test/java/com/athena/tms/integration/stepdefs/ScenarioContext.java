package com.athena.tms.integration.stepdefs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScenarioContext {
    private static final Map<String, String> DEFAULT_STAFF_LOGINS = Map.of("小美", "hk1", "小華", "hk2", "阿強", "mt1", "林櫃檯", "fd1", "櫃檯", "fd1", "王主管", "mg1", "張老闆", "ow1", "老闆", "ow1", "小芳", "fang1");
    private UUID taskId;
    private String opId;
    private final Map<String, UUID> entityIds = new HashMap<String, UUID>();
    private final Map<String, String> guestSessionTokens = new HashMap<String, String>();
    private final Map<String, String> staffLogins = new HashMap<String, String>();
    private final Map<String, String> sopItemKeys = new HashMap<String, String>();
    private HttpResult firstResponse;
    private HttpResult lastResponse;
    private final List<HttpResult> replays = new ArrayList<HttpResult>();
    private Integer returnedSeq;
    private final List<String> claimOutcomes = new ArrayList<String>();
    private final List<String> rejectionCodes = new ArrayList<String>();
    private final List<Integer> rejectionStatuses = new ArrayList<Integer>();
    private TaskSnapshot taskSnapshot;
    private final Map<String, Object> data = new HashMap<String, Object>();

    public void reset() {
        this.taskId = null;
        this.opId = null;
        this.firstResponse = null;
        this.lastResponse = null;
        this.replays.clear();
        this.returnedSeq = null;
        this.claimOutcomes.clear();
        this.rejectionCodes.clear();
        this.rejectionStatuses.clear();
        this.taskSnapshot = null;
        this.entityIds.clear();
        this.guestSessionTokens.clear();
        this.staffLogins.clear();
        this.sopItemKeys.clear();
        this.data.clear();
    }

    public void registerEntity(String symbol, UUID id) {
        this.entityIds.put(symbol, id);
    }

    public UUID entityId(String symbol) {
        UUID id = this.entityIds.get(symbol);
        if (id == null) {
            throw new IllegalStateException("表格代號「" + symbol + "」沒有對應的 UUID——前置資料漏了");
        }
        return id;
    }

    public boolean hasEntity(String symbol) {
        return this.entityIds.containsKey(symbol);
    }

    public String resolveCell(String cell) {
        if (cell == null || cell.isBlank()) {
            return null;
        }
        UUID id = this.entityIds.get(cell);
        return id == null ? cell : id.toString();
    }

    public void registerGuestSession(String symbol, String realToken) {
        this.guestSessionTokens.put(symbol, realToken);
    }

    public String guestSession(String symbol) {
        String token = this.guestSessionTokens.get(symbol);
        if (token == null) {
            throw new IllegalStateException("session 代號「" + symbol + "」沒有對應權杖——前置資料漏了");
        }
        return token;
    }

    public void registerStaffLogin(String name, String loginId) {
        this.staffLogins.put(name, loginId);
    }

    public String staffLogin(String name) {
        String loginId = this.staffLogins.getOrDefault(name, DEFAULT_STAFF_LOGINS.get(name));
        if (loginId == null) {
            throw new IllegalStateException("沒有員工「" + name + "」的 login 對照");
        }
        return loginId;
    }

    public void registerSopItem(String label, String itemKey) {
        this.sopItemKeys.put(label, itemKey);
    }

    public String sopItemKey(String label) {
        String key = this.sopItemKeys.get(label);
        if (key == null) {
            throw new IllegalStateException("SOP 項目「" + label + "」沒有對應 item_key——前置資料漏了");
        }
        return key;
    }

    public UUID taskId() {
        if (this.taskId == null) {
            throw new IllegalStateException("場景尚未建立任務——Given 步驟漏了");
        }
        return this.taskId;
    }

    public void taskId(UUID value) {
        this.taskId = value;
    }

    public String opId() {
        return this.opId;
    }

    public void opId(String value) {
        this.opId = value;
    }

    public HttpResult firstResponse() {
        if (this.firstResponse == null) {
            throw new IllegalStateException("場景尚未發出第一個 HTTP 請求");
        }
        return this.firstResponse;
    }

    public void firstResponse(HttpResult value) {
        this.firstResponse = value;
    }

    public HttpResult lastResponse() {
        if (this.lastResponse == null) {
            throw new IllegalStateException("場景尚未留下任何 HTTP 回應——When 步驟漏了");
        }
        return this.lastResponse;
    }

    public void lastResponse(HttpResult value) {
        this.lastResponse = value;
    }

    public List<HttpResult> replays() {
        return this.replays;
    }

    public Integer returnedSeq() {
        return this.returnedSeq;
    }

    public void returnedSeq(int value) {
        this.returnedSeq = value;
    }

    public List<String> claimOutcomes() {
        return this.claimOutcomes;
    }

    public List<String> rejectionCodes() {
        return this.rejectionCodes;
    }

    public List<Integer> rejectionStatuses() {
        return this.rejectionStatuses;
    }

    public TaskSnapshot taskSnapshot() {
        return this.taskSnapshot;
    }

    public void taskSnapshot(TaskSnapshot value) {
        this.taskSnapshot = value;
    }

    public void put(String key, Object value) {
        this.data.put(key, value);
    }

    public <T> T get(String key) {
        return (T)this.data.get(key);
    }

    public record HttpResult(int status, String body, String replayedHeader) {
    }

    public record TaskSnapshot(String state, int eventSeq, Object updatedAt, int eventCount) {
    }
}
