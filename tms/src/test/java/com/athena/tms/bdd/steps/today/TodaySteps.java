package com.athena.tms.bdd.steps.today;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.TenantFixtures;
import io.cucumber.java.zh_tw.當;
import io.cucumber.java.zh_tw.那麼;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * 今日進度模組（dsl.md：specs/truth/features/backend/今日進度/dsl.md）。
 *
 * <p>When／Then 合併在同一個類別：dsl.md 的「必查」要求 Then 句另外用 tasks 以
 * FR-028、FR-029 的公式重算權威狀態來跟呈現結果對賬，但 Then 句本身的 Gherkin
 * 參數（項目／值）沒有員工欄位可以指出要查誰的 tasks。合理的身分來源只有同一個
 * scenario 裡「查看今日進度」用的那位員工，而每個 scenario 的 glue 實例都是獨立的
 * （cucumber-spring scenario-scoped），用本類別自己的欄位記住即可，不必為此擴充
 * {@link ScenarioWorld} 的公開 API（本輪邊界也不允許改 bdd/support/）。
 */
public class TodaySteps {

    /** FR-029：預估剩餘工時只計這四種狀態的本人任務。 */
    private static final Set<String> REMAINING_STATES = Set.of("accepted", "doing", "deferred", "interrupted");

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final TenantFixtures tenantFixtures;
    private final MutableClock clock;

    /** 最近一次「查看今日進度」的員工姓名，供後續 Then 句重算權威狀態用。 */
    private String lastQueriedStaffName;

    public TodaySteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            DbProbe dbProbe,
            TenantFixtures tenantFixtures,
            MutableClock clock) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.tenantFixtures = tenantFixtures;
        this.clock = clock;
    }

    @當("{string} 查看今日進度")
    public void staffViewsToday(String staffName) {
        lastQueriedStaffName = staffName;
        apiClient.get("/me/today", BddApiClient.Auth.staff(world.jwt(staffName)));
    }

    @那麼("今日進度的 {string} 為 {string}")
    public void todayFieldIs(String item, String expectedValue) {
        JsonNode json = world.lastResponse().json();
        TodayTotals totals = recomputeTotals(requireQueriedStaffId());

        switch (item) {
            case "打掃完成／總數" -> {
                String[] parts = expectedValue.split("/");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("「打掃完成／總數」的值預期形如「2 / 7」，實際 " + expectedValue);
                }
                int expectedDone = Integer.parseInt(parts[0].trim());
                int expectedTotal = Integer.parseInt(parts[1].trim());
                checkField(item, "cleaningDone", json, expectedDone);
                checkField(item, "cleaningTotal", json, expectedTotal);
                check("tasks 重算 cleaningDone", expectedDone, totals.cleaningDone());
                check("tasks 重算 cleaningTotal", expectedTotal, totals.cleaningTotal());
            }
            case "今天完成單數" -> {
                int expected = Integer.parseInt(expectedValue.trim());
                checkField(item, "completedCount", json, expected);
                check("tasks 重算 completedCount", expected, totals.completedCount());
            }
            case "臨時需求完成" -> {
                int expected = Integer.parseInt(expectedValue.trim());
                checkField(item, "adHocCompleted", json, expected);
                check("tasks 重算 adHocCompleted", expected, totals.adHocCompleted());
            }
            case "預估剩餘工時" -> {
                int expected = parseMinutes(expectedValue);
                checkField(item, "remainingMinutes", json, expected);
                check("tasks 重算 remainingMinutes", expected, totals.remainingMinutes());
            }
            default -> throw new IllegalArgumentException(
                    "今日進度未知項目「" + item + "」（不在 dsl.md 對照範圍內）");
        }
    }

    @那麼("^待開始的單推薦順序為 (.+)$")
    public void nextTaskOrderIs(String roomListText) {
        List<String> expected = Arrays.asList(roomListText.split("、"));
        JsonNode json = world.lastResponse().json();
        List<String> actual = new ArrayList<>();
        if (json != null && json.has("nextTasks")) {
            for (JsonNode node : json.get("nextTasks")) {
                actual.add(node.path("roomNo").asText(null));
            }
        }
        if (!expected.equals(actual)) {
            throw new AssertionError("nextTasks 房號順序預期 " + expected + "，實際 " + actual);
        }

        for (String roomNo : expected) {
            UUID taskId = UUID.fromString(world.taskId(roomNo));
            Map<String, Object> row = dbProbe.taskRow(taskId)
                    .orElseThrow(() -> new AssertionError("tasks 查無此任務：" + roomNo));
            String state = String.valueOf(row.get("state"));
            if (!"accepted".equals(state)) {
                throw new AssertionError(roomNo + " 預期狀態為 accepted，實際 " + state);
            }
        }
    }

    @那麼("今日進度包含連續零退回天數")
    public void hasZeroReturnStreak() {
        JsonNode json = world.lastResponse().json();
        Integer value = intOrNull(json, "zeroReturnStreakDays");
        if (value == null || value < 0) {
            throw new AssertionError("zeroReturnStreakDays 預期為 0 以上的整數，實際 "
                    + (json == null ? "無回應內容" : json.get("zeroReturnStreakDays")));
        }
    }

    private UUID requireQueriedStaffId() {
        if (lastQueriedStaffName == null) {
            throw new IllegalStateException("尚未呼叫過「查看今日進度」，無法判斷要以哪位員工的任務重算權威狀態");
        }
        return UUID.fromString(world.staffId(lastQueriedStaffName));
    }

    /** FR-028、FR-029 的公式重算（權威狀態），不依賴 API 回應。 */
    private TodayTotals recomputeTotals(UUID staffId) {
        long tenantId = tenantFixtures.testTenantId();
        List<Map<String, Object>> rows = dbProbe.queryMaps(
                "SELECT type, state, est_min, worked_sec, segment_started_at"
                        + " FROM tasks WHERE tenant_id = ? AND assignee_id = ?",
                tenantId, staffId);

        int cleaningDone = 0;
        int cleaningTotal = 0;
        int completedCount = 0;
        int adHocCompleted = 0;
        int remainingMinutes = 0;

        for (Map<String, Object> row : rows) {
            String type = String.valueOf(row.get("type"));
            String state = String.valueOf(row.get("state"));
            boolean isCleaning = "cleaning".equals(type);
            boolean isDoneLike = "done".equals(state) || "inspected".equals(state);

            if (isCleaning && !"cancelled".equals(state)) {
                cleaningTotal++;
            }
            if (isCleaning && isDoneLike) {
                cleaningDone++;
            }
            if (isDoneLike) {
                completedCount++;
            }
            if (!isCleaning && "done".equals(state)) {
                adHocCompleted++;
            }
            if (REMAINING_STATES.contains(state)) {
                int estMin = row.get("est_min") == null ? 0 : ((Number) row.get("est_min")).intValue();
                int workedMinutes = workedMinutesOf(state, row);
                remainingMinutes += Math.max(0, estMin - workedMinutes);
            }
        }
        return new TodayTotals(cleaningDone, cleaningTotal, completedCount, adHocCompleted, remainingMinutes);
    }

    /**
     * FR-029 的「已做分鐘」：accepted／deferred 為 0；doing 為累計進行時間
     * （worked_sec ＋ now − segment_started_at）；interrupted 為中斷前累計時間（worked_sec）。
     */
    private int workedMinutesOf(String state, Map<String, Object> row) {
        if ("accepted".equals(state) || "deferred".equals(state)) {
            return 0;
        }
        long workedSec = row.get("worked_sec") == null ? 0L : ((Number) row.get("worked_sec")).longValue();
        if ("doing".equals(state)) {
            Object segmentStarted = row.get("segment_started_at");
            if (segmentStarted != null) {
                workedSec += Duration.between(toInstant(segmentStarted), clock.instant()).getSeconds();
            }
        }
        return (int) (workedSec / 60);
    }

    private static int parseMinutes(String displayValue) {
        String digits = displayValue.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            throw new IllegalArgumentException("無法解析分鐘數：" + displayValue);
        }
        return Integer.parseInt(digits);
    }

    private static void checkField(String item, String field, JsonNode json, int expected) {
        Integer actual = intOrNull(json, field);
        if (actual == null || actual != expected) {
            throw new AssertionError("今日進度「" + item + "」呈現結果 " + field + " 預期 " + expected + "，實際 "
                    + (json == null ? "無回應內容" : actual));
        }
    }

    private static Integer intOrNull(JsonNode json, String field) {
        if (json == null || !json.has(field) || json.get(field).isNull()) {
            return null;
        }
        return json.get(field).asInt();
    }

    private static void check(String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + " 預期 " + expected + "，實際 " + actual);
        }
    }

    private static Instant toInstant(Object v) {
        if (v instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (v instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (v instanceof Instant i) {
            return i;
        }
        throw new IllegalStateException("預期時間型別，實際 " + (v == null ? "null" : v.getClass()));
    }

    private record TodayTotals(
            int cleaningDone, int cleaningTotal, int completedCount, int adHocCompleted, int remainingMinutes) {
    }
}
