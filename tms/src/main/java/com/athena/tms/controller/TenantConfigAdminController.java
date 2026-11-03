package com.athena.tms.controller;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主管調整租戶設定的 API，對應 contracts 的 updateTenantConfig。同一個鍵重複寫入
 * 會覆蓋前值；鍵名不在資料庫 CHECK 的封閉鍵集內時寫入直接失敗，不會靜默建出新鍵。
 */
@RestController
@RequestMapping("/api/v1/tenant-configs")
public class TenantConfigAdminController {

    private final JdbcTemplate jdbcTemplate;

    public TenantConfigAdminController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PutMapping("/{key}")
    public Map<String, Object> update(@PathVariable String key, @RequestBody Map<String, Object> body) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        String value = String.valueOf(body.get("value"));
        jdbcTemplate.update(
                """
                INSERT INTO tenant_config (tenant_id, key, value, updated_at, updated_by)
                VALUES (?, ?, to_jsonb(?::text), now(), ?)
                ON CONFLICT (tenant_id, key)
                DO UPDATE SET value = EXCLUDED.value, updated_at = now(), updated_by = EXCLUDED.updated_by
                """,
                tenantId, key, value, actor.staffId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantId", tenantId);
        result.put("key", key);
        result.put("value", value);
        return result;
    }
}
