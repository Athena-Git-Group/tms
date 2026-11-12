package com.athena.tms.service;

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
    public void scan() {
        tenantScopedExecutor.forEachTenant(automationRuleService::scanSchedules);
    }
}
