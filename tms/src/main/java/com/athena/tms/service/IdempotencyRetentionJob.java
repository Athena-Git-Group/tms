package com.athena.tms.service;

import com.athena.tms.service.TenantScopedExecutor.TenantSweep;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.athena.tms.service.TenantScopedExecutor;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyRetentionJob.class);
    private static final int PURGE_BATCH = 5000;
    private final JdbcTemplate jdbcTemplate;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final Duration retention;

    public IdempotencyRetentionJob(JdbcTemplate jdbcTemplate, TenantScopedExecutor tenantScopedExecutor, @Value(value="${tms.idempotency.retention:7d}") String retention) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.retention = DurationStyle.detectAndParse((String)retention);
    }

    @Scheduled(fixedDelayString="${tms.idempotency.sweep-interval:1h}", initialDelayString="${tms.idempotency.sweep-initial-delay:10m}")
    @SchedulerLock(name="idempotency-retention", lockAtMostFor="55m", lockAtLeastFor="50m")
    public TenantSweep onSchedule() {
        return this.purgeExpiredOps();
    }

    public TenantSweep purgeExpiredOps() {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(this.retention);
        return this.tenantScopedExecutor.forEachTenant("idempotency-retention", tenantId -> {
            // 分批刪的理由同 OutboxRetentionJob：單一 SQL 有執行時間上限。
            int purged = 0;
            int batch;
            do {
                batch = this.jdbcTemplate.update("DELETE FROM processed_ops WHERE (tenant_id, op_id) IN (SELECT tenant_id, op_id FROM processed_ops WHERE created_at < ? LIMIT ?)", new Object[]{cutoff, PURGE_BATCH});
                purged += batch;
            } while (batch == PURGE_BATCH);
            if (purged > 0) {
                log.info("租戶 {} 清掉 {} 筆過保留期的 processed_ops", (Object)tenantId, (Object)purged);
            }
        });
    }
}
