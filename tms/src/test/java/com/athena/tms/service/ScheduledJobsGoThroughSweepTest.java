package com.athena.tms.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.athena.tms.common.PerInstance;
import com.athena.tms.service.TenantScopedExecutor.TenantSweep;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * 新增的排程工作若直接逐個租戶跑、或忘了加跨 instance 鎖，一個租戶出錯就會中斷其他租戶，
 * 多個 instance 也會重複執行。這裡把規則寫成檢查：排程方法要嘛標 {@link PerInstance}，
 * 要嘛加 {@link SchedulerLock} 並回傳 {@link TenantScopedExecutor#forEachTenant} 的結果。
 */
class ScheduledJobsGoThroughSweepTest {

    @Test
    void everyScheduledMethodIsPerInstanceOrALockedIsolatedSweep() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        List<String> scheduled = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.athena.tms")) {
            for (Method method : Class.forName(candidate.getBeanClassName()).getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Scheduled.class)) {
                    continue;
                }
                String name = method.getDeclaringClass().getSimpleName() + "." + method.getName();
                scheduled.add(name);
                boolean perInstance = method.isAnnotationPresent(PerInstance.class);
                boolean lockedSweep = method.isAnnotationPresent(SchedulerLock.class) && method.getReturnType() == TenantSweep.class;
                if (!perInstance && !lockedSweep) {
                    violations.add(name);
                }
            }
        }

        assertThat(scheduled).as("掃描要真的找到排程方法，否則這個檢查形同虛設").contains("ShiftEndScanJob.onSchedule", "StreamHeartbeat.beat");
        assertThat(violations).as("這些排程方法沒有經過逐家飯店隔離加跨 instance 鎖，也沒標 @PerInstance").isEmpty();
    }
}
