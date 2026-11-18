package com.athena.tms.bdd.support;

import com.athena.tms.common.UuidV7;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

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

    public record HandoverItem(String target, String content, String authorName) {}
}
