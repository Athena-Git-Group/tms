package com.athena.tms.config;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 依飯店分開的指標（tag tenant）讓營運看得出是哪一家飯店慢或壞，而不是只看到全體平均。
 * 每家飯店都會多出一組時間序列，所以設上限：看過的飯店數達到 tms.metrics.tenant-tag-max 之後，
 * 新飯店的這類指標不再記錄，已記錄的飯店照常更新，監控系統的時間序列不會無限增加。
 */
@Configuration(proxyBeanMethods = false)
public class TenantMetricsConfig {

    public static final String TENANT_TAG = "tenant";

    @Bean
    MeterFilter tenantTagCap(@Value("${tms.metrics.tenant-tag-max:1000}") int maxTenants) {
        return MeterFilter.maximumAllowableTags("tms.", TENANT_TAG, maxTenants, MeterFilter.deny());
    }
}
