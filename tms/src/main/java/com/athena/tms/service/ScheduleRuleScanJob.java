package com.athena.tms.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日排程規則的週期掃描：讓「每日 14:00 大廳巡檢」這類規則在時點到了之後
 * 的下一輪掃描建單進待接池。掃描間隔內的延遲與停機後的補建都由
 * {@link AutomationRuleService#scanSchedules} 的「時點已到就補建、冪等鍵擋重複」
 * 規則吸收，所以掃描本身不需要對齊整點。
 */
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
