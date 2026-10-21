package com.athena.tms.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 把 Boot 自動建的 Hikari DataSource 包進 TenantAwareDataSource。
 * 用 BeanPostProcessor 而不是自建 DataSource bean：自建會整組接手
 * spring.datasource.* 的繫結與 metrics 整合，包一層則全部保留
 * （DelegatingDataSource 支援 unwrap，actuator 的池監控照常）。
 * Flyway 走 spring.flyway.url 自建的管理連線不是 bean、不會被包——
 * 這是刻意的：migration 以管理身分建表/建 policy，若被注入租戶變數
 * 反而讓 RLS 把 migration 自己要讀的列遮掉。
 */
@Configuration(proxyBeanMethods = false)
public class TenantAwareDataSourceConfig {

    // static：BeanPostProcessor 必須早於一般 bean 實例化，非 static 會連帶提早整個設定類
    @Bean
    static BeanPostProcessor tenantAwareDataSourceWrapper() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource && !(bean instanceof TenantAwareDataSource)) {
                    return new TenantAwareDataSource(dataSource);
                }
                return bean;
            }
        };
    }
}
