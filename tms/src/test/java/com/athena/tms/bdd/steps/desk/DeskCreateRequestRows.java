package com.athena.tms.bdd.steps.desk;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.Terms;
import io.cucumber.datatable.DataTable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

final class DeskCreateRequestRows {

    static final String OTHER_REQUEST = "其他需求";

    private DeskCreateRequestRows() {
    }

    record Request(List<Map<String, Object>> items, Map<String, Object> other) {
    }

    static Request parse(DataTable table) {
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> other = null;
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String menuKey = row.get("品項");
            if (menuKey == null || menuKey.isEmpty()) {
                throw new IllegalArgumentException("代開單表格的「品項」必填：" + row);
            }
            if (OTHER_REQUEST.equals(menuKey)) {
                if (other != null) {
                    throw new IllegalArgumentException("代開單表格只能有一列其他需求：" + table);
                }
                String text = row.get("內容");
                other = other(text == null ? "" : text, blankToNull(row.get("給")) == null ? "房務" : row.get("給"));
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("menuKey", menuKey);
            String quantity = blankToNull(row.get("數量"));
            item.put("quantity", quantity == null ? 1 : Integer.parseInt(quantity.trim()));
            items.add(item);
        }
        return new Request(items, other);
    }

    static Map<String, Object> other(String text, String deptChinese) {
        Map<String, Object> other = new LinkedHashMap<>();
        other.put("text", text);
        other.put("dept", Terms.dept(deptChinese));
        return other;
    }

    static void rememberCreated(ScenarioWorld world, String roomNo, ScenarioWorld.ApiResponse response) {
        if (response.status() != 201 || response.json() == null) {
            return;
        }
        JsonNode tasks = response.json().path("tasks");
        for (JsonNode task : tasks) {
            String id = task.path("id").asString(null);
            if (id == null) {
                continue;
            }
            String title = task.path("title").asString(null);
            if (title != null) {
                world.rememberTask(title, id);
            }
            String displayNo = task.path("displayNo").asString(null);
            if (displayNo != null && displayNo.startsWith("W-")) {
                world.rememberRepairTicket(displayNo, id);
            }
            if (tasks.size() == 1) {
                world.rememberTask(roomNo, id);
            }
        }
    }

    static void requireStatus(ScenarioWorld.ApiResponse response, int expected, String what) {
        if (response.status() != expected) {
            throw new AssertionError(what + " 預期回 " + expected + "，實際狀態碼=" + response.status()
                    + "，body=" + response.body());
        }
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
