package com.athena.tms.service;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 讀取飯店的「允許主管查自己清的房」設定，查房判定與待查房清單共用同一個判斷。
 *
 * <p>預設允許：租戶設定 {@code self_inspection_allowed} 沒有設定時，主管可以查自己清的房，
 * 只有一位房務主管的飯店不必另外設定，他自己清的房也等得到人查。
 * 飯店把這個設定明確設為 false 時才擋：清房者不能查自己清的房，查房判定回 403，
 * 自己清的房也不進自己的待查房清單。
 *
 * <p>查房判定與清單必須讀同一個值。兩邊各自判斷的話，主管可能在清單上看到自己清的房，
 * 點進去送出卻被拒，或者反過來送得出去卻在清單上找不到。
 */
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
