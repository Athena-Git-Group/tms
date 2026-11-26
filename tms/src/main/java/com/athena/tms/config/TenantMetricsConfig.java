package com.athena.tms.config;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 依租戶分開的指標讓營運看得出是哪一個租戶慢或壞，而不是只看到全體平均。
 * 每個租戶都會多出一組時間序列，所以設上限：看過的租戶數達到 tms.metrics.tenant-tag-max 之後，
 * 新租戶的這類指標不再記錄，已記錄的租戶照常更新，監控系統的時間序列不會無限增加。
 * 租戶記在名為 tenant 的 tag。
 */
@Configuration(proxyBeanMethods = false)
public class TenantMetricsConfig {

    public static final String TENANT_TAG = "tenant";

    @Bean
    MeterFilter tenantTagCap(@Value("${tms.metrics.tenant-tag-max:1000}") int maxTenants) {
        return MeterFilter.maximumAllowableTags("tms.", TENANT_TAG, maxTenants, MeterFilter.deny());
    }
}
