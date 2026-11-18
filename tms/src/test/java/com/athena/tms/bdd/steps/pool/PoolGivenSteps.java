package com.athena.tms.bdd.steps.pool;

import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.common.UuidV7;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;

public class PoolGivenSteps {

    private final JdbcClient jdbcClient;
    private final TenantFixtures tenantFixtures;
    private final MutableClock clock;

    public PoolGivenSteps(JdbcClient jdbcClient, TenantFixtures tenantFixtures, MutableClock clock) {
        this.jdbcClient = jdbcClient;
        this.tenantFixtures = tenantFixtures;
        this.clock = clock;
    }

    @假設("飯店有以下自動化規則：")
    public void hotelHasAutomationRules(DataTable table) {
        long tenantId = tenantFixtures.testTenantId();
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), clock.getZone());

        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String name = requireCell(row, "規則名");
            String triggerSource = triggerSource(requireCell(row, "觸發源"));
            String condition = requireCell(row, "條件");
            String category = poolCategory(requireCell(row, "類別"));
            String roomNo = blankToNull(row.get("房號"));
            Integer dueAfterMinutes = parseIntOrNull(row.get("建單後到期分鐘"));

            jdbcClient
                    .sql(
                            """
                            INSERT INTO automation_rules
                                (id, tenant_id, name, trigger_source, condition, category,
                                 room_no, due_after_minutes, enabled, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, true, ?, ?)
                            ON CONFLICT (tenant_id, name) DO UPDATE SET
                                trigger_source = excluded.trigger_source,
                                condition = excluded.condition,
                                category = excluded.category,
                                room_no = excluded.room_no,
                                due_after_minutes = excluded.due_after_minutes,
                                enabled = true,
                                updated_at = excluded.updated_at
                            """)
                    .params(
                            UuidV7.generate(), tenantId, name, triggerSource, condition, category,
                            roomNo, dueAfterMinutes, now, now)
                    .update();
        }
    }

    @假設("系統從 {string} 停機到 {string}")
    public void systemDownFromTo(String startHHmm, String endHHmm) {
        clock.setTodayAt(endHHmm);
    }

    private static String triggerSource(String chinese) {
        return switch (chinese) {
            case "訂房屬性" -> "reservation";
            case "每日排程" -> "schedule";
            default -> throw new IllegalArgumentException("未知的自動化規則觸發源: " + chinese);
        };
    }

    private static String poolCategory(String chinese) {
        return switch (chinese) {
            case "打掃" -> "cleaning";
            case "備品" -> "amenity";
            case "其他" -> "other";
            default -> throw new IllegalArgumentException("未知的自動化規則類別: " + chinese);
        };
    }

    private static Integer parseIntOrNull(String s) {
        return (s == null || s.isBlank()) ? null : Integer.parseInt(s.trim());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String requireCell(Map<String, String> row, String column) {
        String v = row.get(column);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return v;
    }
}
