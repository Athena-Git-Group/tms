package com.athena.tms.bdd.support;

import com.athena.tms.service.UnclaimedReminderScanJob;
import com.athena.tms.service.ShiftEndScanJob;
import com.athena.tms.service.IdempotencyRetentionJob;
import com.athena.tms.service.OutboxRetentionJob;
import com.athena.tms.service.StreamHeartbeat;
import com.athena.tms.service.TenantScopedExecutor.TenantSweep;
import java.util.Set;
import org.springframework.context.ApplicationContext;

public class JobTrigger {

    private static final Set<String> KNOWN_JOBS = Set.of(
            "outbox-retention", "idempotency-retention", "stream-heartbeat",
            "unclaimed-reminder-scan", "shift-end-scan", "schedule-rule-scan");

    private final ApplicationContext applicationContext;

    public JobTrigger(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void run(String jobName) {
        TenantSweep sweep = switch (jobName) {
            case "outbox-retention" -> applicationContext.getBean(OutboxRetentionJob.class).purgeSentMessages();
            case "idempotency-retention" ->
                    applicationContext.getBean(IdempotencyRetentionJob.class).purgeExpiredOps();
            case "stream-heartbeat" -> {
                applicationContext.getBean(StreamHeartbeat.class).beat();
                yield null;
            }
            case "shift-end-scan" -> applicationContext.getBean(ShiftEndScanJob.class).scan();
            case "schedule-rule-scan" ->
                    applicationContext.getBean(com.athena.tms.service.ScheduleRuleScanJob.class).scan();
            case "unclaimed-reminder-scan" -> applicationContext.getBean(UnclaimedReminderScanJob.class).scan();
            default -> throw new IllegalArgumentException(
                    "未知的排程工作名稱：" + jobName + "；已知名稱：" + KNOWN_JOBS);
        };
        // 排程工作會吞掉單一飯店的例外繼續跑下一家，測試要把它翻回失敗，否則壞掉的步驟會被當成通過。
        if (sweep != null && !sweep.failedTenants().isEmpty()) {
            throw new IllegalStateException("排程工作 " + jobName + " 處理這些飯店時失敗：" + sweep.failedTenants());
        }
    }
}
