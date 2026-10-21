package com.athena.tms.integration.stepdefs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 場景內共用狀態（steps 之間唯一的傳遞通道；Spring 單例 bean，場景開始時由
 * hooks reset）。常用狀態給 typed accessor，少用的走泛型 put/get——向 crs 的
 * 版本看齊但不複製它的業務欄位。
 */
public class ScenarioContext {

    /** HTTP 呼叫結果的最小快照：狀態碼＋body＋冪等回放 header。 */
    public record HttpResult(int status, String body, String replayedHeader) {
    }

    /** tasks 列在某時點的快照（「下游不回寫」場景的比對基準）。 */
    public record TaskSnapshot(String state, int eventSeq, Object updatedAt, int eventCount) {
    }

    /**
     * feature 檔沒寫對照時的員工中文名 → login_id 預設值（V2 種子帳號）。
     * 場景的「系統中存在以下員工」表格會以表內 login_id 覆蓋同名項。
     */
    private static final Map<String, String> DEFAULT_STAFF_LOGINS = Map.of(
            "小美", "hk1", "小華", "hk2", "阿強", "mt1",
            "林櫃檯", "fd1", "櫃檯", "fd1",
            "王主管", "mg1", "張老闆", "ow1", "老闆", "ow1",
            "小芳", "fang1");

    private UUID taskId;
    private String opId;
    private final Map<String, UUID> entityIds = new HashMap<>();
    private final Map<String, String> guestSessionTokens = new HashMap<>();
    private final Map<String, String> staffLogins = new HashMap<>();
    private final Map<String, String> sopItemKeys = new HashMap<>();
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
        data.clear();
    }

    /**
     * 登記一筆「表格代號 → 實際 UUID」對照（房間、員工、訂單、任務等共用）。
     * feature 檔的前置資料表用人讀得懂的代號（「r-301」「t-305-1」）當 id，
     * 實際落庫時換成真 UUID，之後的步驟憑代號找回真 id。
     */
    public void registerEntity(String symbol, UUID id) {
        entityIds.put(symbol, id);
    }

    /** 以表格代號取回實際 UUID；查無即佈景漏登記，直接失敗。 */
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

    /**
     * 把表格 cell 轉成可與資料庫比對的字串：空值回 null、
     * 已登記的代號回其 UUID 字串、其餘原樣回傳。
     */
    public String resolveCell(String cell) {
        if (cell == null || cell.isBlank()) {
            return null;
        }
        UUID id = entityIds.get(cell);
        return id == null ? cell : id.toString();
    }

    /**
     * 登記「表格的 session_token 代號 → 實際簽發的住客權杖」。住客權杖的
     * 格式帶租戶前綴（驗證端靠它路由），表格裡的「sess-305」只能當代號用。
     */
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

    /** 登記員工中文名 → login_id（來自「系統中存在以下員工」表格）。 */
    public void registerStaffLogin(String name, String loginId) {
        staffLogins.put(name, loginId);
    }

    /** 員工中文名換 login_id；表格沒登記的名字退回種子帳號對照。 */
    public String staffLogin(String name) {
        String loginId = staffLogins.getOrDefault(name, DEFAULT_STAFF_LOGINS.get(name));
        if (loginId == null) {
            throw new IllegalStateException("沒有員工「" + name + "」的 login 對照");
        }
        return loginId;
    }

    /** 登記 SOP 項目中文 label → item_key（來自「系統中存在以下SOP項目」表格）。 */
    public void registerSopItem(String label, String itemKey) {
        sopItemKeys.put(label, itemKey);
    }

    public String sopItemKey(String label) {
        String key = sopItemKeys.get(label);
        if (key == null) {
            throw new IllegalStateException("SOP 項目「" + label + "」沒有對應 item_key——前置資料漏了");
        }
        return key;
    }

    public UUID taskId() {
        if (taskId == null) {
            throw new IllegalStateException("場景尚未建立任務——Given 步驟漏了");
        }
        return taskId;
    }

    public void taskId(UUID value) {
        this.taskId = value;
    }

    public String opId() {
        return opId;
    }

    public void opId(String value) {
        this.opId = value;
    }

    public HttpResult firstResponse() {
        if (firstResponse == null) {
            throw new IllegalStateException("場景尚未發出第一個 HTTP 請求");
        }
        return firstResponse;
    }

    public void firstResponse(HttpResult value) {
        this.firstResponse = value;
    }

    /** 最近一次 HTTP 呼叫結果（共用的「回應為 X」斷言步驟吃它）。 */
    public HttpResult lastResponse() {
        if (lastResponse == null) {
            throw new IllegalStateException("場景尚未留下任何 HTTP 回應——When 步驟漏了");
        }
        return lastResponse;
    }

    public void lastResponse(HttpResult value) {
        this.lastResponse = value;
    }

    public List<HttpResult> replays() {
        return replays;
    }

    public Integer returnedSeq() {
        return returnedSeq;
    }

    public void returnedSeq(int value) {
        this.returnedSeq = value;
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
        this.taskSnapshot = value;
    }

    public void put(String key, Object value) {
        data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }
}
