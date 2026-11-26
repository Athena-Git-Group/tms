package com.athena.tms.service;

import com.athena.tms.service.TenantScopedExecutor.TenantSweep;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduleRuleScanJob {

    private final TenantScopedExecutor tenantScopedExecutor;
    private final AutomationRuleService automationRuleService;

    public ScheduleRuleScanJob(
            TenantScopedExecutor tenantScopedExecutor, AutomationRuleService automationRuleService) {
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.automationRuleService = automationRuleService;
    }

    @Scheduled(
            fixedDelayString = "${tms.schedule-rule.scan-interval:1m}",
            initialDelayString = "${tms.schedule-rule.scan-initial-delay:1m}")
    @SchedulerLock(name = "schedule-rule-scan", lockAtMostFor = "10m", lockAtLeastFor = "45s")
    public TenantSweep onSchedule() {
        return scan();
    }

    public TenantSweep scan() {
        return tenantScopedExecutor.forEachTenant("schedule-rule-scan", automationRuleService::scanSchedules);
    }
}
