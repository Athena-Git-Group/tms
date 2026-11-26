package com.athena.tms.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 多個 instance 都排了同一個掃描工作時，同一時間只讓一個 instance 跑，避免每家飯店被同時掃好幾次。
 * 鎖記在資料庫的 shedlock 表，到期時間用資料庫的時鐘算，各 instance 的時鐘不準也不影響。
 * 鎖只加在排程器呼叫的方法上，測試直接呼叫的掃描方法不經過鎖，連續觸發也不會被略過。
 */
@Configuration(proxyBeanMethods = false)
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class SchedulerLockConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
