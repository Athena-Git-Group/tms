package com.athena.tms.bdd.support;

import com.athena.tms.common.UuidV7;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

public class TenantFixtures {

    private static final String TEST_TENANT_NAME = "測試飯店";

    private final JdbcClient jdbcClient;
    private volatile Long cachedTenantId;

    public TenantFixtures(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public synchronized long testTenantId() {
        if (cachedTenantId != null) {
            return cachedTenantId;
        }
        Optional<Long> existing = jdbcClient
                .sql("SELECT id FROM tenants WHERE name = ?")
                .params(TEST_TENANT_NAME)
                .query(Long.class)
                .optional();
        if (existing.isPresent()) {
            cachedTenantId = existing.get();
            return cachedTenantId;
        }
        Long id = jdbcClient
                .sql("INSERT INTO tenants (name, created_at) VALUES (?, ?) RETURNING id")
                .params(TEST_TENANT_NAME, OffsetDateTime.now())
                .query(Long.class)
                .single();
        cachedTenantId = id;
        return id;
    }

    private static final List<String[]> INSPECTION_SOP_ITEMS = List.of(
            new String[] {"bed", "床鋪"},
            new String[] {"bathroom", "浴室"},
            new String[] {"amenities", "備品"},
            new String[] {"floor", "地面"},
            new String[] {"odor", "異味"});

    public void ensureInspectionSopItems() {
        long tenantId = testTenantId();
        int sortOrder = 1;
        for (String[] item : INSPECTION_SOP_ITEMS) {
            jdbcClient
                    .sql(
                            """
                            INSERT INTO sop_items (id, tenant_id, applies_to, item_key, label, sort_order, active, created_at)
                            VALUES (?, ?, 'inspection', ?, ?, ?, true, ?)
                            ON CONFLICT (tenant_id, applies_to, item_key) DO NOTHING
                            """)
                    .params(UuidV7.generate(), tenantId, item[0], item[1], sortOrder++, OffsetDateTime.now())
                    .update();
        }
    }
}
