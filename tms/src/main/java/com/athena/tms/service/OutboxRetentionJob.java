package com.athena.tms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * sent 訊息 3 天清：下游以 idempotency_key 去重可能需要回看近期
 * 訊息，留 3 天當對帳窗；再久 outbox 只會無限長胖——partial index 雖然不含
 * sent 列，但全表掃描與備份成本仍在。failed 不清，等人工處理完才有資格走。
 */
@Component
public class OutboxRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxRetentionJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final Duration retention;

    public OutboxRetentionJob(JdbcTemplate jdbcTemplate,
                              TenantScopedExecutor tenantScopedExecutor,
                              @Value("${tms.outbox.sent-retention:3d}") String retention) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.retention = DurationStyle.detectAndParse(retention);
    }

    /** initialDelay 拉開：啟動瞬間先讓 relay 消化積壓，清理不搶第一口連線池。 */
    @Scheduled(fixedDelayString = "${tms.outbox.sent-sweep-interval:1h}",
            initialDelayString = "${tms.outbox.sent-sweep-initial-delay:10m}")
    public void purgeSentMessages() {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(retention);
        tenantScopedExecutor.forEachTenant(tenantId -> {
            int purged = jdbcTemplate.update(
                    "DELETE FROM outbox WHERE status = 'sent' AND sent_at < ?", cutoff);
            if (purged > 0) {
                log.info("租戶 {} 清掉 {} 筆過保留期的 sent outbox", tenantId, purged);
            }
        });
    }
}
