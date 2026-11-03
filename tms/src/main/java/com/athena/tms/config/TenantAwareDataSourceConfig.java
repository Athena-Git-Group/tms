/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.beans.factory.config.BeanPostProcessor
 *  org.springframework.context.annotation.Bean
 *  org.springframework.context.annotation.Configuration
 */
package com.athena.tms.config;

import com.athena.tms.config.TenantAwareDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
public class TenantAwareDataSourceConfig {
    @Bean
    static BeanPostProcessor tenantAwareDataSourceWrapper() {
        return new BeanPostProcessor(){

            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource) {
                    DataSource dataSource = (DataSource)bean;
                    if (!(bean instanceof TenantAwareDataSource)) {
                        return new TenantAwareDataSource(dataSource);
                    }
                }
                return bean;
            }
        };
    }
}
