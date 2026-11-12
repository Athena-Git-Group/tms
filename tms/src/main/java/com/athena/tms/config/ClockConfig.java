package com.athena.tms.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全系統判斷「現在」都注入這裡提供的同一個 {@link Clock}。
 *
 * <p>預設是系統時鐘。啟動參數 {@code tms.test-clock.enabled=true} 時改成
 * {@link AdjustableClock}，並由 {@code TestClockController} 開放撥動端點；
 * 兩個 bean 由同一個屬性互斥決定，正式環境不帶這個參數就與只有系統時鐘時完全相同。
 */
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
