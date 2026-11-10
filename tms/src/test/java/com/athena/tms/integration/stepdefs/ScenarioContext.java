package com.athena.tms.integration.stepdefs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScenarioContext {
    private static final Map<String, String> DEFAULT_STAFF_LOGINS = Map.of("\u5c0f\u7f8e", "hk1", "\u5c0f\u83ef", "hk2", "\u963f\u5f37", "mt1", "\u6797\u6ac3\u6aaf", "fd1", "\u6ac3\u6aaf", "fd1", "\u738b\u4e3b\u7ba1", "mg1", "\u5f35\u8001\u95c6", "ow1", "\u8001\u95c6", "ow1", "\u5c0f\u82b3", "fang1");
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
            throw new IllegalStateException("\u8868\u683c\u4ee3\u865f\u300c" + symbol + "\u300d\u6c92\u6709\u5c0d\u61c9\u7684 UUID\u2014\u2014\u524d\u7f6e\u8cc7\u6599\u6f0f\u4e86");
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
            throw new IllegalStateException("session \u4ee3\u865f\u300c" + symbol + "\u300d\u6c92\u6709\u5c0d\u61c9\u6b0a\u6756\u2014\u2014\u524d\u7f6e\u8cc7\u6599\u6f0f\u4e86");
        }
        return token;
    }

    public void registerStaffLogin(String name, String loginId) {
        this.staffLogins.put(name, loginId);
    }

    public String staffLogin(String name) {
        String loginId = this.staffLogins.getOrDefault(name, DEFAULT_STAFF_LOGINS.get(name));
        if (loginId == null) {
            throw new IllegalStateException("\u6c92\u6709\u54e1\u5de5\u300c" + name + "\u300d\u7684 login \u5c0d\u7167");
        }
        return loginId;
    }

    public void registerSopItem(String label, String itemKey) {
        this.sopItemKeys.put(label, itemKey);
    }

    public String sopItemKey(String label) {
        String key = this.sopItemKeys.get(label);
        if (key == null) {
            throw new IllegalStateException("SOP \u9805\u76ee\u300c" + label + "\u300d\u6c92\u6709\u5c0d\u61c9 item_key\u2014\u2014\u524d\u7f6e\u8cc7\u6599\u6f0f\u4e86");
        }
        return key;
    }

    public UUID taskId() {
        if (this.taskId == null) {
            throw new IllegalStateException("\u5834\u666f\u5c1a\u672a\u5efa\u7acb\u4efb\u52d9\u2014\u2014Given \u6b65\u9a5f\u6f0f\u4e86");
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
            throw new IllegalStateException("\u5834\u666f\u5c1a\u672a\u767c\u51fa\u7b2c\u4e00\u500b HTTP \u8acb\u6c42");
        }
        return this.firstResponse;
    }

    public void firstResponse(HttpResult value) {
        this.firstResponse = value;
    }

    public HttpResult lastResponse() {
        if (this.lastResponse == null) {
            throw new IllegalStateException("\u5834\u666f\u5c1a\u672a\u7559\u4e0b\u4efb\u4f55 HTTP \u56de\u61c9\u2014\u2014When \u6b65\u9a5f\u6f0f\u4e86");
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
