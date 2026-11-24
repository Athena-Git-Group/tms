package com.athena.web.bdd.steps.createrequest;

import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.web.bdd.support.ScenarioTasks;
import com.athena.web.bdd.support.WebWorld;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CreateRequestTable {

    public static final String OTHER_REQUEST = "其他需求";

    private static final Set<String> COLUMNS = Set.of("品項", "數量", "內容", "給");

    private static final Map<String, String> DEPT = Map.of("房務", "housekeeping", "工務", "engineering");

    public record Payload(List<Map<String, Object>> items, Map<String, Object> other) {
    }

    private CreateRequestTable() {
    }

    public static Payload parse(List<Map<String, String>> rows) {
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> other = null;
        for (Map<String, String> row : rows) {
            for (String column : row.keySet()) {
                if (!COLUMNS.contains(column)) {
                    throw new IllegalArgumentException("代開的表格不認得「" + column + "」這一欄，可以用的欄：" + COLUMNS);
                }
            }
            String item = blankToNull(row.get("品項"));
            if (item == null) {
                throw new IllegalArgumentException("代開的表格每一列都要有「品項」");
            }
            if (OTHER_REQUEST.equals(item)) {
                if (other != null) {
                    throw new IllegalArgumentException("一次代開只有一段其他需求");
                }
                String text = blankToNull(row.get("內容"));
                if (text == null) {
                    throw new IllegalArgumentException("其他需求那一列要寫「內容」");
                }
                String deptTerm = blankToNull(row.get("給")) == null ? "房務" : row.get("給").strip();
                String dept = DEPT.get(deptTerm);
                if (dept == null) {
                    throw new IllegalArgumentException("其他需求的「給」只接受房務、工務，收到「" + deptTerm + "」");
                }
                other = new LinkedHashMap<>();
                other.put("text", text);
                other.put("dept", dept);
                continue;
            }
            String quantity = blankToNull(row.get("數量"));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("menuKey", item);
            entry.put("quantity", quantity == null ? 1 : Integer.parseInt(quantity));
            items.add(entry);
        }
        return new Payload(List.copyOf(items), other);
    }

    public static JsonNode submit(WebWorld world, CreatedStaff actor, String roomNo, List<Map<String, String>> rows) {
        Payload payload = parse(rows);
        JsonNode result = world.backend().deskCreateRequests(actor, roomNo.strip(), payload.items(), payload.other(),
                UUID.randomUUID().toString());
        for (JsonNode task : result.path("tasks")) {
            world.tasks().add(new ScenarioTasks.Entry(roomNo.strip(), task.path("title").asText(),
                    UUID.fromString(task.path("id").asText()), "待認領"));
        }
        return result;
    }

    public static List<UUID> taskIds(JsonNode result) {
        List<UUID> ids = new ArrayList<>();
        for (JsonNode task : result.path("tasks")) {
            ids.add(UUID.fromString(task.path("id").asText()));
        }
        return ids;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
