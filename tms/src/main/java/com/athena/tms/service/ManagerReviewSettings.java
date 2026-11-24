package com.athena.tms.service;

import com.athena.tms.dto.desk.DeskSettings;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 飯店要不要主管把關的兩個開關：打掃完要不要房務主管查房、工務完修要不要工務主管驗收。
 * 兩個都是沒設定時為需要，升級前就在用的飯店行為不變。
 * 需要查房存成 inspection_waived_enabled，意思相反，是沿用既有的免查房鍵。
 */
@Component
public class ManagerReviewSettings {

    private static final String INSPECTION_WAIVED_KEY = "inspection_waived_enabled";
    private static final String REPAIR_VERIFICATION_REQUIRED_KEY = "repair_verification_required";

    private final JdbcTemplate jdbcTemplate;

    public ManagerReviewSettings(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DeskSettings read(long tenantId) {
        return new DeskSettings(inspectionRequired(tenantId), repairVerificationRequired(tenantId));
    }

    public boolean inspectionRequired(long tenantId) {
        return !"true".equals(value(tenantId, INSPECTION_WAIVED_KEY));
    }

    public boolean repairVerificationRequired(long tenantId) {
        return !"false".equals(value(tenantId, REPAIR_VERIFICATION_REQUIRED_KEY));
    }

    public void update(long tenantId, UUID staffId, Boolean inspectionRequired, Boolean repairVerificationRequired) {
        if (inspectionRequired != null) {
            upsert(tenantId, INSPECTION_WAIVED_KEY, !inspectionRequired, staffId);
        }
        if (repairVerificationRequired != null) {
            upsert(tenantId, REPAIR_VERIFICATION_REQUIRED_KEY, repairVerificationRequired, staffId);
        }
    }

    private String value(long tenantId, String key) {
        List<String> values = jdbcTemplate.queryForList(
                "SELECT value #>> '{}' FROM tenant_config WHERE tenant_id = ? AND key = ?", String.class, tenantId, key);
        return values.isEmpty() ? null : values.get(0);
    }

    private void upsert(long tenantId, String key, boolean value, UUID staffId) {
        jdbcTemplate.update(
                """
                INSERT INTO tenant_config (tenant_id, key, value, updated_at, updated_by)
                VALUES (?, ?, to_jsonb(?::boolean), now(), ?)
                ON CONFLICT (tenant_id, key)
                DO UPDATE SET value = EXCLUDED.value, updated_at = now(), updated_by = EXCLUDED.updated_by
                """,
                tenantId, key, value, staffId);
    }
}
