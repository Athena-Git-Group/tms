package com.athena.tms.bdd.support;

import com.athena.tms.common.UuidV7;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

public class TenantFixtures {

    private static final String TEST_TENANT_NAME = "測試飯店";

    /**
     * 「測試飯店」的識別代碼，與 V24 回填既有「測試飯店」用的是同一個值。
     *
     * <p>代碼的命名規則：後端測試預設的那一家固定用這個值；另外再建的飯店由呼叫端自己給代碼
     * （通常就是 feature 裡寫的那個字），這裡不替它編。代碼要符合資料庫的限制：
     * 長度 3 到 32、只有小寫英文字母、數字與連字號、第一個字是英文字母、不以連字號結尾、
     * 不連用兩個連字號，而且不能是保留字（例如 {@code test}、{@code admin}、{@code desk}）。
     * 每家飯店的代碼都不能和別家相同。
     */
    public static final String TEST_TENANT_CODE = "test-hotel";

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
        cachedTenantId = insertTenant(TEST_TENANT_NAME, TEST_TENANT_CODE);
        return cachedTenantId;
    }

    /**
     * 在「測試飯店」之外另建一家飯店，回傳它的 id。
     *
     * <p>測試用的資料庫在案例之間不會清掉飯店，所以同一個代碼第二次呼叫時直接回傳已經建好的那一家；
     * 但名稱若和先前不同就視為測試資料寫錯而失敗，因為代碼寫入後不能改、也不能兩家共用。
     * 代碼不合格式或是保留字時由資料庫拒絕。
     */
    public synchronized long createTenant(String name, String code) {
        Optional<String> existingName = jdbcClient
                .sql("SELECT name FROM tenants WHERE code = ?")
                .params(code)
                .query(String.class)
                .optional();
        if (existingName.isEmpty()) {
            return insertTenant(name, code);
        }
        if (!existingName.get().equals(name)) {
            throw new IllegalStateException(
                    "飯店代碼 " + code + " 已經給了「" + existingName.get() + "」，不能再給「" + name + "」");
        }
        return jdbcClient
                .sql("SELECT id FROM tenants WHERE code = ?")
                .params(code)
                .query(Long.class)
                .single();
    }

    private long insertTenant(String name, String code) {
        return jdbcClient
                .sql("INSERT INTO tenants (name, code, created_at) VALUES (?, ?, ?) RETURNING id")
                .params(name, code, OffsetDateTime.now())
                .query(Long.class)
                .single();
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
