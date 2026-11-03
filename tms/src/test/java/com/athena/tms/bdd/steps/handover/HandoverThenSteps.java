package com.athena.tms.bdd.steps.handover;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.MutableClock;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * 房間交辦模組的 Then（dsl.md Then 句型區）。「交辦如下／不包含主管交代／退回資訊為」
 * 三句都讀最近一次呼叫（前一個 When「打開 … 的任務」留下的）{@code readTasks} 回應，
 * 不重新發一次請求；「已讀」兩句則直接查權威狀態 {@code handover_reads}，其中一句
 * 另外用 {@code readTasks} 做跨視角再讀確認（dsl.md 的「再讀確認」）。
 */
public class HandoverThenSteps {

    private static final Map<String, String> SOURCE_KIND = Map.of(
            "交班單", "shift_handover",
            "櫃檯備註", "desk_note",
            "接手進度", "requeue_progress",
            "主管交代", "manager_note");

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final MutableClock clock;

    public HandoverThenSteps(ScenarioWorld world, BddApiClient apiClient, DbProbe dbProbe, MutableClock clock) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.clock = clock;
    }

    @那麼("{string} 的交辦如下：")
    public void handoversAre(String taskKey, DataTable table) {
        JsonNode json = requireJson(world.lastResponse());
        JsonNode handovers = json.path("handovers");
        if (!handovers.isArray()) {
            throw new AssertionError("「" + taskKey + "」的 TaskDetail.handovers 預期是陣列，實際：" + handovers);
        }

        List<Map<String, String>> expectedRows = table.asMaps(String.class, String.class);
        List<JsonNode> actual = new ArrayList<>();
        handovers.forEach(actual::add);
        if (actual.size() != expectedRows.size()) {
            throw new AssertionError("「" + taskKey + "」的交辦筆數預期 " + expectedRows.size() + " 筆，實際 "
                    + actual.size() + " 筆：" + handovers);
        }

        List<JsonNode> remaining = new ArrayList<>(actual);
        for (Map<String, String> row : expectedRows) {
            String kind = sourceKind(requireCell(row, "來源"));
            String author = requireCell(row, "作者");
            String content = requireCell(row, "內容");
            String expectedHHmm = row.get("時間");

            JsonNode match = remaining.stream()
                    .filter(node -> kind.equals(node.path("kind").asText(null))
                            && author.equals(node.path("author").asText(null))
                            && content.equals(node.path("content").asText(null))
                            && (expectedHHmm == null
                                    || expectedHHmm.isBlank()
                                    || expectedHHmm.equals(formatAtHHmm(node.path("at").asText(null)))))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("「" + taskKey + "」的交辦找不到符合的項目：來源="
                            + row.get("來源") + "、作者=" + author + "、內容=" + content
                            + (expectedHHmm != null && !expectedHHmm.isBlank() ? "、時間=" + expectedHHmm : "")
                            + "（實際清單=" + handovers + "）"));
            remaining.remove(match);
        }
    }

    @那麼("{string} 的交辦對 {string} 為 {string}")
    public void handoverReadStatusIs(String taskKey, String staffName, String statusChinese) {
        boolean expectedRead = readStatus(statusChinese);
        UUID taskId = UUID.fromString(world.taskId(taskKey));
        UUID staffId = UUID.fromString(world.staffId(staffName));

        long count = dbProbe.count(
                "SELECT count(*) FROM handover_reads WHERE task_id = ? AND staff_id = ?", taskId, staffId);
        boolean actualRead = count > 0;
        if (actualRead != expectedRead) {
            throw new AssertionError("「" + taskKey + "」交辦對「" + staffName + "」預期為「" + statusChinese
                    + "」，實際 handover_reads 筆數=" + count);
        }

        ScenarioWorld.ApiResponse resp = apiClient.get("/tasks/" + taskId, BddApiClient.Auth.staff(world.jwt(staffName)));
        JsonNode json = requireJson(resp);
        boolean fieldValue = json.path("handoverRead").asBoolean(false);
        if (fieldValue != expectedRead) {
            throw new AssertionError(
                    "readTasks.handoverRead 預期 " + expectedRead + "，實際 " + fieldValue);
        }
    }

    @那麼("{string} 對 {string} 的已讀紀錄只有 {int} 筆")
    public void handoverReadCountIs(String taskKey, String staffName, int expectedCount) {
        UUID taskId = UUID.fromString(world.taskId(taskKey));
        UUID staffId = UUID.fromString(world.staffId(staffName));
        long actual = dbProbe.count(
                "SELECT count(*) FROM handover_reads WHERE task_id = ? AND staff_id = ?", taskId, staffId);
        if (actual != expectedCount) {
            throw new AssertionError(
                    "「" + taskKey + "」對「" + staffName + "」的已讀紀錄預期 " + expectedCount + " 筆，實際 " + actual + " 筆");
        }
    }

    @那麼("{string} 的退回資訊為 {string}")
    public void returnInfoIs(String taskKey, String expectedSummary) {
        JsonNode json = requireJson(world.lastResponse());
        JsonNode returnInfo = json.path("returnInfo");
        String actual = returnInfo.path("summary").asText(null);
        if (!expectedSummary.equals(actual)) {
            throw new AssertionError(
                    "「" + taskKey + "」的 TaskDetail.returnInfo.summary 預期「" + expectedSummary + "」，實際「" + actual + "」");
        }
    }

    @那麼("{string} 的交辦不包含主管交代")
    public void handoversExcludeManagerNote(String taskKey) {
        JsonNode json = requireJson(world.lastResponse());
        JsonNode handovers = json.path("handovers");
        for (JsonNode node : handovers) {
            if ("manager_note".equals(node.path("kind").asText(null))) {
                throw new AssertionError("「" + taskKey + "」的交辦預期不包含主管交代，實際找到一筆：" + node);
            }
        }
    }

    private String formatAtHHmm(String isoDateTime) {
        if (isoDateTime == null) {
            return null;
        }
        return OffsetDateTime.parse(isoDateTime)
                .atZoneSameInstant(clock.getZone())
                .format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private static boolean readStatus(String chinese) {
        return switch (chinese) {
            case "已讀" -> true;
            case "未讀" -> false;
            default -> throw new IllegalArgumentException("找不到「" + chinese + "」在已讀狀態對照表中的權威值");
        };
    }

    private static String sourceKind(String chinese) {
        String v = SOURCE_KIND.get(chinese);
        if (v == null) {
            throw new IllegalArgumentException("找不到「" + chinese + "」在交辦來源對照表中的權威值");
        }
        return v;
    }

    private static JsonNode requireJson(ScenarioWorld.ApiResponse resp) {
        if (resp.status() != 200 || resp.json() == null) {
            throw new AssertionError("預期 200 且有 JSON body，實際 status=" + resp.status() + "，body=" + resp.body());
        }
        return resp.json();
    }

    private static String requireCell(Map<String, String> row, String column) {
        String v = row.get(column);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return v;
    }
}
