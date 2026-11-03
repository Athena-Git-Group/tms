/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.annotation.Value
 *  org.springframework.boot.convert.DurationStyle
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.scheduling.annotation.Scheduled
 *  org.springframework.stereotype.Component
 */
package com.athena.tms.service;

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
    private final JdbcTemplate jdbcTemplate;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final Duration retention;

    public IdempotencyRetentionJob(JdbcTemplate jdbcTemplate, TenantScopedExecutor tenantScopedExecutor, @Value(value="${tms.idempotency.retention:7d}") String retention) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.retention = DurationStyle.detectAndParse((String)retention);
    }

    @Scheduled(fixedDelayString="${tms.idempotency.sweep-interval:1h}", initialDelayString="${tms.idempotency.sweep-initial-delay:10m}")
    public void purgeExpiredOps() {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(this.retention);
        this.tenantScopedExecutor.forEachTenant(tenantId -> {
            int purged = this.jdbcTemplate.update("DELETE FROM processed_ops WHERE created_at < ?", new Object[]{cutoff});
            if (purged > 0) {
                log.info("\u79df\u6236 {} \u6e05\u6389 {} \u7b46\u904e\u4fdd\u7559\u671f\u7684 processed_ops", (Object)tenantId, (Object)purged);
            }
        });
    }
}
