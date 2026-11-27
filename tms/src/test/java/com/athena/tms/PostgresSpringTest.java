package com.athena.tms;

import com.athena.tms.bdd.support.MutableClock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 只需要資料庫的 JUnit Spring 測試共用的基底。子類別不要再加自己的 {@code @TestConfiguration} 或 profile，
 * 否則 Spring 會為它另起一份 context，失去共用的意義。
 *
 * <p>業務時鐘換成 {@link MutableClock}，預設停在 2026-10-19 中午（台北），要別的時刻就在案例裡撥。
 * 撥過的案例收尾要 {@link MutableClock#reset()}，同一份 context 的下一個案例才會從預設時刻開始。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(PostgresSpringTest.MutableClockConfiguration.class)
public abstract class PostgresSpringTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MutableClockConfiguration {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @DynamicPropertySource
    static void wirePostgres(DynamicPropertyRegistry registry) {
        wireDatabase(registry, "junit");
    }

    public static void wireDatabase(DynamicPropertyRegistry registry, String database) {
        PostgreSQLContainer<?> postgres = SharedContainers.postgres();
        String url = SharedContainers.database(database);
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "tms_app");
        registry.add("spring.datasource.password", () -> "tms_app");
        registry.add("spring.kafka.admin.auto-create", () -> "false");
        SharedContainers.wireStorage(registry, database.replace('_', '-') + "-photos");
    }
}
