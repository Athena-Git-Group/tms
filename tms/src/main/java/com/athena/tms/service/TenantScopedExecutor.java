package com.athena.tms.service;

import com.athena.tms.config.TenantMetricsConfig;
import com.athena.tms.dao.TenantRepository;
import com.athena.tms.entity.Tenant;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TenantScopedExecutor {
    private static final Logger log = LoggerFactory.getLogger(TenantScopedExecutor.class);
    private final TenantRepository tenantRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;
    private final TxLimits sweepLimits;

    public TenantScopedExecutor(TenantRepository tenantRepository, JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager, MeterRegistry meterRegistry, @Value(value="${tms.scheduled.tenant-statement-timeout:30s}") String tenantStatementTimeout) {
        this.tenantRepository = tenantRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.meterRegistry = meterRegistry;
        this.sweepLimits = new TxLimits(Duration.ZERO, DurationStyle.detectAndParse(tenantStatementTimeout));
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

    /**
     * 排程工作逐家飯店執行，一家飯店出錯或查詢逾時，只跳過那家飯店，其他飯店照常跑完。
     * 每家飯店各自一個交易，單一 SQL 最多跑 tms.scheduled.tenant-statement-timeout，一家飯店卡住最多拖慢這一輪這麼久。
     */
    public TenantSweep forEachTenant(String jobName, LongConsumer work) {
        int succeeded = 0;
        List<Long> failed = new ArrayList<>();
        for (long tenantId : this.tenantIds().stream().sorted().toList()) {
            try {
                this.callAsTenant(tenantId, this.sweepLimits, () -> {
                    work.accept(tenantId);
                    return null;
                });
                succeeded++;
            }
            catch (RuntimeException e) {
                failed.add(tenantId);
                Counter.builder("tms.scheduled.tenant.failures").tag("job", jobName).tag(TenantMetricsConfig.TENANT_TAG, Long.toString(tenantId)).register(this.meterRegistry).increment();
                log.error("排程 {} 處理飯店 {} 失敗，跳過這家飯店、繼續下一家: {}", new Object[]{jobName, tenantId, e.getMessage(), e});
            }
        }
        return new TenantSweep(jobName, succeeded, List.copyOf(failed));
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

    public record TenantSweep(String jobName, int succeeded, List<Long> failedTenants) {
    }
}
