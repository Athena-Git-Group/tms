package com.athena.tms.service;

import com.athena.tms.common.error.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 各服務在動一筆任務之前，先用這裡確認任務存在並拿到整列資料（含房號）。
 * 查無任務時直接丟 404，讓「任務不存在」在進入後續查詢或狀態轉移前就被
 * 擋下來；不先擋，這個情況會被之後的狀態不符 409 或其他錯誤蓋掉，
 * App 端就分不出「單不見了」和「單還在但狀態不對」。
 */
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
