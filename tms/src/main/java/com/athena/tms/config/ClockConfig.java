package com.athena.tms.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    @Bean
    @ConditionalOnProperty(name = "tms.test-clock.enabled", havingValue = "false", matchIfMissing = true)
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    @ConditionalOnProperty(name = "tms.test-clock.enabled", havingValue = "true")
    public AdjustableClock adjustableClock() {
        return new AdjustableClock();
    }
}
