package com.athena.tms.bdd.support;

import com.athena.tms.common.UuidV7;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

/**
 * 交班單 snapshot 測試資料入口（{@code handovers}，欄位依 data-model.dbml {@code Table handovers}）。
 *
 * <p>{@code snapshot} 定格交班當下的任務清單；這裡直接把 {@link HandoverItem} 原樣序列化成
 * {@code [{target, content, authorName}, …]}，不做任何欄位轉譯。{@code generated_by} 固定寫
 * {@code system}（班末掃描產生是預設路徑，見 dbml Note；需要 {@code manager} 手動情境的呼叫端
 * 請直接用 JdbcClient 另外處理，不在這個入口的範圍內）。
 */
public class HandoverFixtures {

    private final JdbcClient jdbcClient;
    private final TenantFixtures tenantFixtures;
    private final ObjectMapper objectMapper;

    public HandoverFixtures(JdbcClient jdbcClient, TenantFixtures tenantFixtures, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.tenantFixtures = tenantFixtures;
        this.objectMapper = objectMapper;
    }

    public UUID freezeHandover(String deptCode, String shiftCode, OffsetDateTime frozenAt, List<HandoverItem> items) {
        long tenantId = tenantFixtures.testTenantId();
        UUID id = UuidV7.generate();
        String snapshotJson = toSnapshotJson(items);

        jdbcClient
                .sql(
                        """
                        INSERT INTO handovers
                            (id, tenant_id, dept, shift, work_date, snapshot, generated_by, created_at)
                        VALUES (?, ?, ?, ?, ?, ?::jsonb, 'system', ?)
                        """)
                .params(id, tenantId, deptCode, shiftCode, frozenAt.toLocalDate(), snapshotJson, frozenAt)
                .update();
        return id;
    }

    private String toSnapshotJson(List<HandoverItem> items) {
        List<Map<String, String>> rows = items.stream()
                .map(item -> {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("target", item.target());
                    row.put("content", item.content());
                    row.put("authorName", item.authorName());
                    return row;
                })
                .toList();
        return objectMapper.writeValueAsString(rows);
    }

    /** 交班單一筆項目：target 是房號或樓層層級的識別字，content 是交辦內容，authorName 是撰寫人。 */
    public record HandoverItem(String target, String content, String authorName) {}
}
