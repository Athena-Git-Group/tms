package com.athena.tms.bdd.steps.pool;

import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.common.UuidV7;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 待接池模組的 Given（待接池/dsl.md Given 句型區）。只建立/確認權威狀態，不含任何斷言。
 *
 * <p>自動化規則直接用 {@link JdbcClient} 寫 {@code automation_rules} 表
 * （欄位依 {@code V6__automation_rules_and_due_at.sql}），比照 {@code RoomFixtures}／
 * {@code StaffFixtures} 的慣例：純資料落地不經任何 production 服務，不需要
 * {@code TenantContext}（RLS 在測試連線為資料庫 superuser 時本就不生效，
 * {@code TenantContext} 只有呼叫 production 服務碼時才需要）。
 */
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
        // 怎麼做：直接把時鐘從開始時刻跳到結束時刻，期間不執行任何排程掃描（不呼叫 JobTrigger），
        // 也不寫資料庫；只有結束時刻之後的狀態對後續步驟有意義，因此 startHHmm 只作為句型參數，
        // 不需要額外落地。
        clock.setTodayAt(endHHmm);
    }

    private static String triggerSource(String chinese) {
        return switch (chinese) {
            case "訂房屬性" -> "reservation";
            case "每日排程" -> "schedule";
            default -> throw new IllegalArgumentException("未知的自動化規則觸發源: " + chinese);
        };
    }

    // 來源：specs/truth/contracts/components/schemas/員工工作台.yaml 的 PoolCategory enum，
    // 與 automation_rules.category 的 CHECK（cleaning|amenity|other）交集。
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
