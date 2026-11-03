package com.athena.tms.bdd.steps.common;

import io.cucumber.java.Before;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 每個 scenario 開始前清空業務資料，避免跨 feature 的唯一鍵碰撞（例如兩個 feature 都用 305 房）。
 * 保留 flyway_schema_history 與 tenants：租戶由 TenantFixtures 快取重用，schema 歷史不可動。
 * 以 information_schema 動態列表，之後加表不必回來改這裡。
 */
public class ResetHooks {

    private final JdbcClient jdbc;

    public ResetHooks(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Before(order = 0)
    public void resetDatabase() {
        List<String> tables = jdbc.sql("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                  AND table_name NOT IN ('flyway_schema_history', 'tenants')
                """).query(String.class).list();
        if (!tables.isEmpty()) {
            jdbc.sql("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE").update();
        }
    }
}
