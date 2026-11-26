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
public class OutboxRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(OutboxRetentionJob.class);
    private static final int PURGE_BATCH = 5000;
    private final JdbcTemplate jdbcTemplate;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final Duration retention;

    public OutboxRetentionJob(JdbcTemplate jdbcTemplate, TenantScopedExecutor tenantScopedExecutor, @Value(value="${tms.outbox.sent-retention:3d}") String retention) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.retention = DurationStyle.detectAndParse((String)retention);
    }

    @Scheduled(fixedDelayString="${tms.outbox.sent-sweep-interval:1h}", initialDelayString="${tms.outbox.sent-sweep-initial-delay:10m}")
    @SchedulerLock(name="outbox-retention", lockAtMostFor="55m", lockAtLeastFor="50m")
    public TenantSweep onSchedule() {
        return this.purgeSentMessages();
    }

    public TenantSweep purgeSentMessages() {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(this.retention);
        return this.tenantScopedExecutor.forEachTenant("outbox-retention", tenantId -> {
            // 分批刪：每個租戶的單一 SQL 有執行時間上限，積了好幾天的大租戶一次刪不完會每小時逾時、永遠清不掉。
            int purged = 0;
            int batch;
            do {
                batch = this.jdbcTemplate.update("DELETE FROM outbox WHERE id IN (SELECT id FROM outbox WHERE status = 'sent' AND sent_at < ? LIMIT ?)", new Object[]{cutoff, PURGE_BATCH});
                purged += batch;
            } while (batch == PURGE_BATCH);
            if (purged > 0) {
                log.info("租戶 {} 清掉 {} 筆過保留期的 sent outbox", (Object)tenantId, (Object)purged);
            }
        });
    }
}
