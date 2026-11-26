package com.athena.tms.service;

import com.athena.tms.dao.TenantRepository;
import com.athena.tms.entity.Tenant;
import java.time.Duration;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TenantScopedExecutor {
    private final TenantRepository tenantRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public TenantScopedExecutor(TenantRepository tenantRepository, JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.tenantRepository = tenantRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public <T> T callAsTenant(long tenantId, Supplier<T> work) {
        return (T)this.transactionTemplate.execute(status -> {
            this.jdbcTemplate.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, new Object[]{String.valueOf(tenantId)});
            return work.get();
        });
    }

    public <T> T callAsTenant(long tenantId, TxLimits limits, Supplier<T> work) {
        return (T)this.transactionTemplate.execute(status -> {
            this.jdbcTemplate.query("SELECT set_config('app.tenant_id', ?, true), set_config('lock_timeout', ?, true), set_config('statement_timeout', ?, true)",
                    rs -> {},
                    String.valueOf(tenantId), String.valueOf(limits.lockTimeout().toMillis()), String.valueOf(limits.statementTimeout().toMillis()));
            return work.get();
        });
    }

    public void runAsTenant(long tenantId, Runnable work) {
        this.callAsTenant(tenantId, () -> {
            work.run();
            return null;
        });
    }

    public void forEachTenant(LongConsumer work) {
        for (Tenant tenant : this.tenantRepository.findAll()) {
            this.runAsTenant(tenant.getId(), () -> work.accept(tenant.getId()));
        }
    }

    public List<Long> tenantIds() {
        return this.tenantRepository.findAll().stream().map(Tenant::getId).toList();
    }

    public List<Long> tenantsWithDueOutbox() {
        return this.jdbcTemplate.queryForList("SELECT tenant_id FROM outbox_tenants_with_due_work()", Long.class);
    }

    /**
     * 一家飯店的交易最多能等鎖多久、單一 SQL 最多能跑多久。設成零代表不限。
     * 用來讓一家飯店的鎖或慢查詢只拖住它自己那一輪，不會無限期佔住共用的執行緒與連線。
     */
    public record TxLimits(Duration lockTimeout, Duration statementTimeout) {
    }
}
