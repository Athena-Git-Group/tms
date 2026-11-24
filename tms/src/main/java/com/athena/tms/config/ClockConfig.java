package com.athena.tms.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 系統裡有兩種時鐘。一般注入的 {@link Clock} 是業務時鐘，所有業務時間都從它讀，測試可以把它撥到劇本指定的時刻。
 * {@link #WALL_CLOCK} 是真實時間，給要和外部系統的時間戳比較的程式用，例如 Kafka 紀錄的時間戳；
 * 這類比較若改用業務時鐘，業務時鐘一被撥走就會算出好幾小時的落後。這個 bean 不參與一般注入，要用 {@code @Qualifier(WALL_CLOCK)} 指名。
 */
@Configuration
public class ClockConfig {

    public static final String WALL_CLOCK = "wallClock";

    @Bean
    @Profile("!" + TestSupportConfig.PROFILE)
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean(name = WALL_CLOCK, defaultCandidate = false)
    public Clock wallClock() {
        return Clock.systemUTC();
    }
}
