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
 * 冪等簿 processed_ops 7 天清：弱網重送的時間尺度是秒到小時，7 天已是
 * 極寬的回放窗；過期後同鍵重送會被當成新請求重算，此時業務層本身的
 * 條件式更新（狀態機）與唯一約束接手擋重複效果，所以清掉是安全的——
 * 留著只是讓每個 POST 的查表越來越慢。
 */
@Component
public class IdempotencyRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyRetentionJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final Duration retention;

    public IdempotencyRetentionJob(JdbcTemplate jdbcTemplate,
                                   TenantScopedExecutor tenantScopedExecutor,
                                   @Value("${tms.idempotency.retention:7d}") String retention) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.retention = DurationStyle.detectAndParse(retention);
    }

    @Scheduled(fixedDelayString = "${tms.idempotency.sweep-interval:1h}",
            initialDelayString = "${tms.idempotency.sweep-initial-delay:10m}")
    public void purgeExpiredOps() {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(retention);
        tenantScopedExecutor.forEachTenant(tenantId -> {
            int purged = jdbcTemplate.update(
                    "DELETE FROM processed_ops WHERE created_at < ?", cutoff);
            if (purged > 0) {
                log.info("租戶 {} 清掉 {} 筆過保留期的 processed_ops", tenantId, purged);
            }
        });
    }
}
