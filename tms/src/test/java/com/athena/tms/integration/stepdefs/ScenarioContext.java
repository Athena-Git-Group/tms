package com.athena.tms.integration.stepdefs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScenarioContext {

    private static final Map<String, String> DEFAULT_STAFF_LOGINS = Map.of(
            "小美", "hk1",
            "小華", "hk2",
            "阿強", "mt1",
            "林櫃檯", "fd1",
            "櫃檯", "fd1",
            "王主管", "mg1",
            "張老闆", "ow1",
            "老闆", "ow1",
            "小芳", "fang1");

    private UUID taskId;
    private String opId;
    private final Map<String, UUID> entityIds = new HashMap<>();
    private final Map<String, String> guestSessionTokens = new HashMap<>();
    private final Map<String, String> staffLogins = new HashMap<>();
    private final Map<String, String> sopItemKeys = new HashMap<>();
    private final Map<String, UUID> sopItemIds = new HashMap<>();
    private HttpResult firstResponse;
    private HttpResult lastResponse;
    private final List<HttpResult> replays = new ArrayList<>();
    private Integer returnedSeq;
    private final List<String> claimOutcomes = new ArrayList<>();
    private final List<String> rejectionCodes = new ArrayList<>();
    private final List<Integer> rejectionStatuses = new ArrayList<>();
    private TaskSnapshot taskSnapshot;
    private final Map<String, Object> data = new HashMap<>();

    public void reset() {
        taskId = null;
        opId = null;
        firstResponse = null;
        lastResponse = null;
        replays.clear();
        returnedSeq = null;
        claimOutcomes.clear();
        rejectionCodes.clear();
        rejectionStatuses.clear();
        taskSnapshot = null;
        entityIds.clear();
        guestSessionTokens.clear();
        staffLogins.clear();
        sopItemKeys.clear();
        sopItemIds.clear();
        data.clear();
    }

    public void registerEntity(String symbol, UUID id) {
        entityIds.put(symbol, id);
    }

    public UUID entityId(String symbol) {
        UUID id = entityIds.get(symbol);
        if (id == null) {
            throw new IllegalStateException("表格代號「" + symbol + "」沒有對應的 UUID——前置資料漏了");
        }
        return id;
    }

    public boolean hasEntity(String symbol) {
        return entityIds.containsKey(symbol);
    }

    public String resolveCell(String cell) {
        if (cell == null || cell.isBlank()) {
            return null;
        }
        UUID id = entityIds.get(cell);
        return id == null ? cell : id.toString();
    }

    public void registerGuestSession(String symbol, String realToken) {
        guestSessionTokens.put(symbol, realToken);
    }

    public String guestSession(String symbol) {
        String token = guestSessionTokens.get(symbol);
        if (token == null) {
            throw new IllegalStateException("session 代號「" + symbol + "」沒有對應權杖——前置資料漏了");
        }
        return token;
    }

    public void registerStaffLogin(String name, String loginId) {
        staffLogins.put(name, loginId);
    }

    public String staffLogin(String name) {
        String loginId = staffLogins.getOrDefault(name, DEFAULT_STAFF_LOGINS.get(name));
        if (loginId == null) {
            throw new IllegalStateException("沒有員工「" + name + "」的 login 對照");
        }
        return loginId;
    }

    public void registerSopItem(String label, String itemKey, UUID id) {
        sopItemKeys.put(label, itemKey);
        sopItemIds.put(label, id);
    }

    public String sopItemKey(String label) {
        String key = sopItemKeys.get(label);
        if (key == null) {
            throw new IllegalStateException("SOP 項目「" + label + "」沒有對應 item_key——前置資料漏了");
        }
        return key;
    }

    public UUID sopItemId(String label) {
        UUID id = sopItemIds.get(label);
        if (id == null) {
            throw new IllegalStateException("SOP 項目「" + label + "」沒有對應 id——前置資料漏了");
        }
        return id;
    }

    public UUID taskId() {
        if (taskId == null) {
            throw new IllegalStateException("場景尚未建立任務——Given 步驟漏了");
        }
        return taskId;
    }

    public void taskId(UUID value) {
        taskId = value;
    }

    public String opId() {
        return opId;
    }

    public void opId(String value) {
        opId = value;
    }

    public HttpResult firstResponse() {
        if (firstResponse == null) {
            throw new IllegalStateException("場景尚未發出第一個 HTTP 請求");
        }
        return firstResponse;
    }

    public void firstResponse(HttpResult value) {
        firstResponse = value;
    }

    public HttpResult lastResponse() {
        if (lastResponse == null) {
            throw new IllegalStateException("場景尚未留下任何 HTTP 回應——When 步驟漏了");
        }
        return lastResponse;
    }

    public void lastResponse(HttpResult value) {
        lastResponse = value;
    }

    public List<HttpResult> replays() {
        return replays;
    }

    public Integer returnedSeq() {
        return returnedSeq;
    }

    public void returnedSeq(int value) {
        returnedSeq = value;
    }

    public List<String> claimOutcomes() {
        return claimOutcomes;
    }

    public List<String> rejectionCodes() {
        return rejectionCodes;
    }

    public List<Integer> rejectionStatuses() {
        return rejectionStatuses;
    }

    public TaskSnapshot taskSnapshot() {
        return taskSnapshot;
    }

    public void taskSnapshot(TaskSnapshot value) {
        taskSnapshot = value;
    }

    public void put(String key, Object value) {
        data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    public record HttpResult(int status, String body, String replayedHeader) {
    }

    public record TaskSnapshot(String state, int eventSeq, Object updatedAt, int eventCount) {
    }
}
