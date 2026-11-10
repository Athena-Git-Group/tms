package com.athena.tms.bdd.support;

import com.athena.tms.common.UuidV7;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * BDD 情境共用的單一測試租戶（「測試飯店」）。所有其他 fixture（員工、房間、任務……）
 * 都掛在這個租戶底下，和 V2__seed_dev.sql 種的示範租戶（id=1「示範飯店」、id=2「示範二店」）
 * 互不相干，避免 fixture 資料跟種子資料的斷言彼此汙染。
 *
 * <p>「ensure」語意：同一個測試 JVM 內重複呼叫只會建立一次，之後都回傳快取的 id——
 * 本類別以單例 bean 註冊（見 BddSpringConfiguration），所以快取在整個套件執行期間有效。
 */
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

    /**
     * 查房項目（applies_to=inspection）的 item_key → label。label 用推播標題會原樣
     * 引用的短名稱（例如「{房號} 查房退回 · 床鋪不合格」引用「床鋪」），feature 的
     * 期望標題才組得出來；鍵集與 V2__seed_dev.sql 示範租戶的清單同構。
     */
    private static final List<String[]> INSPECTION_SOP_ITEMS = List.of(
            new String[] {"bed", "床鋪"},
            new String[] {"bathroom", "浴室"},
            new String[] {"amenities", "備品"},
            new String[] {"floor", "地面"},
            new String[] {"odor", "異味"});

    /**
     * 確保「測試飯店」有一套查房項目（sop_items，applies_to=inspection）。V2__seed_dev.sql
     * 只替示範租戶（id=1）種查房項目，而且 ResetHooks 每個 scenario 都會 TRUNCATE sop_items，
     * 所以需要查房項目全集的 stepdef（例如 createInspections 的 items 必須涵蓋全集）要先呼叫
     * 這裡落地。以 uq_sop_items_key 做 ON CONFLICT DO NOTHING，同一 scenario 內重複呼叫無害。
     */
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
