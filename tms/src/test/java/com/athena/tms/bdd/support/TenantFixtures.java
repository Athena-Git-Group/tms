package com.athena.tms.bdd.support;

import java.time.OffsetDateTime;
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

    /** 確保「測試飯店」租戶存在，回傳其 tenant id（tenants.id）。 */
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
}
