package com.athena.tms.service;

import com.athena.tms.common.error.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TaskRowLookup {

    private final JdbcTemplate jdbcTemplate;

    public TaskRowLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> require(long tenantId, UUID taskId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT t.*, r.room_no
                  FROM tasks t
                  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                 WHERE t.tenant_id = ? AND t.id = ?
                """,
                tenantId, taskId);
        if (rows.isEmpty()) {
            throw new NotFoundException("查無任務: " + taskId);
        }
        return rows.get(0);
    }
}
