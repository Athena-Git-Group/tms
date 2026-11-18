package com.athena.tms.service;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SelfInspectionSetting {

    private final JdbcTemplate jdbcTemplate;

    public SelfInspectionSetting(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean allowed(long tenantId) {
        List<String> values = jdbcTemplate.queryForList(
                "SELECT value #>> '{}' FROM tenant_config WHERE tenant_id = ? AND key = 'self_inspection_allowed'",
                String.class, tenantId);
        return values.isEmpty() || !"false".equals(values.get(0));
    }
}
