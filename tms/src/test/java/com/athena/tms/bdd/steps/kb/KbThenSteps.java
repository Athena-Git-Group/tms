package com.athena.tms.bdd.steps.kb;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.OutboxAwait;
import com.athena.tms.bdd.support.PushPayloadView;
import com.athena.tms.bdd.support.TenantFixtures;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class KbThenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final OutboxAwait outboxAwait;
    private final TenantFixtures tenantFixtures;
    private final MutableClock clock;
    private final ObjectMapper objectMapper;

    public KbThenSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            DbProbe dbProbe,
            OutboxAwait outboxAwait,
            TenantFixtures tenantFixtures,
            MutableClock clock,
            ObjectMapper objectMapper) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.outboxAwait = outboxAwait;
        this.tenantFixtures = tenantFixtures;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @那麼("推薦問題依序為：")
    public void suggestionsInOrder(DataTable table) {
        List<Map<String, String>> rows = table.asMaps(String.class, String.class);
        JsonNode suggestions = requireArrayResponse();
        if (suggestions.size() != rows.size()) {
            throw new AssertionError(
                    "推薦問題數量預期 " + rows.size() + "，實際 " + suggestions.size() + "：" + world.lastResponse().body());
        }
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            JsonNode item = suggestions.get(i);
            check("推薦問題[" + i + "].question", requireCell(row, "問題"), textOrNull(item, "question"));
            check("推薦問題[" + i + "].scope", requireCell(row, "範圍"), textOrNull(item, "scope"));
        }
    }

    @那麼("第一個推薦問題為 {string}")
    public void firstSuggestionIs(String question) {
        JsonNode suggestions = requireArrayResponse();
        if (suggestions.isEmpty()) {
            throw new AssertionError("預期至少一則推薦問題，實際為空：" + world.lastResponse().body());
        }
        check("第一個推薦問題", question, textOrNull(suggestions.get(0), "question"));
    }

    @那麼("回答為 {string}")
    public void answerIs(String expectedAnswer) {
        JsonNode answer = requireAnswerJson();
        check("KbAnswer.answer", expectedAnswer, textOrNull(answer, "answer"));

        Map<String, Object> row = requireAuditRow(UUID.fromString(requireField(answer, "answerId")));
        check("kb_audit_logs.answer", expectedAnswer, row.get("answer"));
    }

    @那麼("回答附以下出處：")
    public void answerHasSources(DataTable table) {
        List<Map<String, String>> rows = table.asMaps(String.class, String.class);
        JsonNode answer = requireAnswerJson();
        JsonNode sources = answer.get("sources");
        if (sources == null || !sources.isArray()) {
            throw new AssertionError("KbAnswer.sources 預期為陣列，實際：" + answer);
        }
        if (sources.size() != rows.size()) {
            throw new AssertionError("出處筆數預期 " + rows.size() + "，實際 " + sources.size() + "：" + sources);
        }
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            JsonNode src = sources.get(i);
            check("sources[" + i + "].kind", sourceKind(requireCell(row, "類型")), textOrNull(src, "kind"));
            check("sources[" + i + "].title", requireCell(row, "標題"), textOrNull(src, "title"));
            check("sources[" + i + "].excerpt", requireCell(row, "內容"), textOrNull(src, "excerpt"));
            check(
                    "sources[" + i + "].updatedOn",
                    formatExpectedDate(requireCell(row, "日期")),
                    textOrNull(src, "updatedOn"));
        }

        Map<String, Object> auditRow = requireAuditRow(UUID.fromString(requireField(answer, "answerId")));
        JsonNode citedRefs = parseJsonColumn(auditRow.get("cited_refs"));
        if (citedRefs == null || !citedRefs.isArray() || citedRefs.size() != rows.size()) {
            throw new AssertionError(
                    "kb_audit_logs.cited_refs 筆數或型別與呈現結果不一致：" + auditRow.get("cited_refs"));
        }
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            JsonNode ref = citedRefs.get(i);
            check("cited_refs[" + i + "].kind", sourceKind(requireCell(row, "類型")), textOrNull(ref, "kind"));
            check("cited_refs[" + i + "].title", requireCell(row, "標題"), textOrNull(ref, "title"));
        }
    }

    @那麼("系統回覆查不到 {string} 的相關記錄，且沒有任何回答內容")
    public void notFoundMessage(String roomNo) {
        JsonNode answer = requireAnswerJson();
        boolean found = answer.path("found").asBoolean(true);
        if (found) {
            throw new AssertionError("預期 found=false，實際 found=" + found);
        }
        String message = textOrNull(answer, "notFoundMessage");
        if (message == null || !message.contains(roomNo)) {
            throw new AssertionError("notFoundMessage 預期包含「" + roomNo + "」，實際=" + message);
        }
        JsonNode answerField = answer.get("answer");
        if (answerField != null && !answerField.isNull()) {
            throw new AssertionError("found=false 時 answer 預期為 null，實際=" + answerField);
        }
        JsonNode sources = answer.get("sources");
        if (sources != null && sources.isArray() && !sources.isEmpty()) {
            throw new AssertionError("找不到時 sources 預期為空，實際=" + sources);
        }
    }

    @那麼("查一下稽核紀錄有以下一筆：")
    public void auditLogHasOneRow(DataTable table) {
        List<Map<String, String>> rows = table.asMaps(String.class, String.class);
        if (rows.size() != 1) {
            throw new AssertionError("本句設計為剛好一筆，Data Table 卻有 " + rows.size() + " 列");
        }
        Map<String, String> row = rows.get(0);
        String askerName = requireCell(row, "提問者");
        String taskLabel = requireCell(row, "任務");
        String question = requireCell(row, "問題");
        String citedSourceChinese = requireCell(row, "引用來源");
        String feedbackChinese = blankToNull(row.get("回饋"));

        UUID askerId = UUID.fromString(world.staffId(askerName));
        List<Map<String, Object>> matches =
                dbProbe.queryMaps("SELECT * FROM kb_audit_logs WHERE asker_id = ? AND question = ?", askerId, question);
        if (matches.size() != 1) {
            throw new AssertionError("kb_audit_logs 預期剛好一筆（提問者=" + askerName + "，問題=" + question + "），實際 "
                    + matches.size() + " 筆");
        }
        Map<String, Object> logRow = matches.get(0);

        Map<String, Object> taskRow = requireTaskWithRoom(uuidOf(logRow.get("task_id")));
        String actualTaskLabel = taskRow.get("room_no") + " " + taskRow.get("title");
        check("查一下稽核紀錄.任務", taskLabel, actualTaskLabel);

        check("kb_audit_logs.cited_kind", auditCitedKind(citedSourceChinese), logRow.get("cited_kind"));

        String expectedFeedback = feedbackChinese == null ? null : auditFeedback(feedbackChinese);
        check("kb_audit_logs.feedback", expectedFeedback, logRow.get("feedback"));
    }

    @那麼("{string} 收到員工提問 {string}")
    public void managerReceivesQuestion(String managerName, String question) {
        long tenantId = tenantFixtures.testTenantId();
        List<Map<String, Object>> matches = dbProbe.queryMaps(
                "SELECT * FROM kb_escalations WHERE tenant_id = ? AND status = 'pending' AND question = ?",
                tenantId, question);
        if (matches.isEmpty()) {
            throw new AssertionError("kb_escalations 查無待回覆的提問：" + question);
        }

        ScenarioWorld.ApiResponse resp = apiClient.get(
                "/room-memories/escalations?status=pending", BddApiClient.Auth.staff(world.jwt(managerName)));
        if (resp.status() != 200 || resp.json() == null || !resp.json().isArray()) {
            throw new AssertionError(managerName + " 呼叫 listKbEscalations 預期 200 陣列，實際 status=" + resp.status()
                    + "，body=" + resp.body());
        }
        boolean visible = false;
        for (int i = 0; i < resp.json().size(); i++) {
            if (question.equals(textOrNull(resp.json().get(i), "question"))) {
                visible = true;
                break;
            }
        }
        if (!visible) {
            throw new AssertionError(managerName + " 呼叫 listKbEscalations 看不到提問「" + question + "」");
        }
    }

    @那麼("{string} 收到推播內容 {string}")
    public void staffReceivesPushContent(String staffName, String content) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        outboxAwait.waitForPush(
                v -> staffId.equals(v.recipientStaffId())
                        && "kb_reply".equals(v.event())
                        && v.body() != null
                        && v.body().contains(content),
                staffName + " 收到 kb_reply 推播，內容包含「" + content + "」");
    }

    @那麼("回答與出處都不包含 {string}")
    public void answerAndSourcesDoNotContain(String text) {
        JsonNode answer = requireAnswerJson();
        String answerText = textOrNull(answer, "answer");
        if (answerText != null && answerText.contains(text)) {
            throw new AssertionError("回答不應包含「" + text + "」，實際=" + answerText);
        }
        JsonNode sources = answer.get("sources");
        if (sources != null && sources.isArray()) {
            for (int i = 0; i < sources.size(); i++) {
                JsonNode src = sources.get(i);
                for (String field : List.of("kind", "title", "excerpt", "updatedOn")) {
                    String v = textOrNull(src, field);
                    if (v != null && v.contains(text)) {
                        throw new AssertionError("sources[" + i + "]." + field + " 不應包含「" + text + "」，實際=" + v);
                    }
                }
            }
        }

        Map<String, Object> row = requireAuditRow(UUID.fromString(requireField(answer, "answerId")));
        Object dbAnswer = row.get("answer");
        if (dbAnswer != null && String.valueOf(dbAnswer).contains(text)) {
            throw new AssertionError("kb_audit_logs.answer 不應包含「" + text + "」，實際=" + dbAnswer);
        }
        Object citedRefs = row.get("cited_refs");
        if (citedRefs != null && String.valueOf(citedRefs).contains(text)) {
            throw new AssertionError("kb_audit_logs.cited_refs 不應包含「" + text + "」，實際=" + citedRefs);
        }
    }

    @那麼("SOP 管理者 {string} 收到通知，需要檢查 {string}")
    public void sopManagerNotified(String managerName, String sopTitle) {
        UUID managerId = UUID.fromString(world.staffId(managerName));
        outboxAwait.waitForPush(
                v -> managerId.equals(v.recipientStaffId())
                        && "kb_unhelpful".equals(v.event())
                        && v.title() != null
                        && v.title().contains(sopTitle),
                "SOP 管理者 " + managerName + " 收到 kb_unhelpful 通知，標題包含「" + sopTitle + "」");
    }

    private JsonNode requireArrayResponse() {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        if (resp.status() != 200 || resp.json() == null || !resp.json().isArray()) {
            throw new AssertionError("預期 200 且回傳陣列，實際 status=" + resp.status() + "，body=" + resp.body());
        }
        return resp.json();
    }

    private JsonNode requireAnswerJson() {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        if (resp.status() != 200 || resp.json() == null) {
            throw new AssertionError("預期 200 的 KbAnswer，實際 status=" + resp.status() + "，body=" + resp.body());
        }
        return resp.json();
    }

    private Map<String, Object> requireAuditRow(UUID answerId) {
        return dbProbe
                .queryOne("SELECT * FROM kb_audit_logs WHERE id = ?", answerId)
                .orElseThrow(() -> new AssertionError("kb_audit_logs 查無 id=" + answerId));
    }

    private Map<String, Object> requireTaskWithRoom(UUID taskId) {
        return dbProbe
                .queryOne(
                        """
                        SELECT t.*, r.room_no AS room_no
                          FROM tasks t JOIN rooms r ON r.id = t.room_id AND r.tenant_id = t.tenant_id
                         WHERE t.id = ?
                        """,
                        taskId)
                .orElseThrow(() -> new AssertionError("tasks 查無此任務（或找不到對應房間）：" + taskId));
    }

    private JsonNode parseJsonColumn(Object column) {
        if (column == null) {
            return null;
        }
        String json = column instanceof String s ? s : column.toString();
        return objectMapper.readTree(json);
    }

    private String formatExpectedDate(String mmdd) {
        String[] parts = mmdd.split("/");
        int month = Integer.parseInt(parts[0]);
        int day = Integer.parseInt(parts[1]);
        int year = clock.instant().atZone(clock.getZone()).getYear();
        return LocalDate.of(year, month, day).toString();
    }

    private static UUID uuidOf(Object dbValue) {
        return UUID.fromString(String.valueOf(dbValue));
    }

    private static String sourceKind(String chinese) {
        return switch (chinese) {
            case "房間記錄" -> "room_memory";
            case "SOP" -> "sop";
            default -> throw new IllegalArgumentException("出處類型只支援 房間記錄｜SOP，收到: " + chinese);
        };
    }

    private static String auditCitedKind(String chinese) {
        return switch (chinese) {
            case "房間記錄" -> "room_memory";
            case "SOP" -> "sop";
            case "無" -> "none";
            default -> throw new IllegalArgumentException("引用來源只支援 房間記錄｜SOP｜無，收到: " + chinese);
        };
    }

    private static String auditFeedback(String chinese) {
        return switch (chinese) {
            case "有幫助" -> "helpful";
            case "沒幫助" -> "unhelpful";
            default -> throw new IllegalArgumentException("回饋只支援 有幫助｜沒幫助，收到: " + chinese);
        };
    }

    private static void check(String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + " 預期 " + expected + "，實際 " + actual);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private static String requireField(JsonNode node, String field) {
        String v = textOrNull(node, field);
        if (v == null) {
            throw new AssertionError("預期欄位「" + field + "」存在且非 null：" + node);
        }
        return v;
    }

    private static String requireCell(Map<String, String> row, String column) {
        String v = row.get(column);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return v;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
