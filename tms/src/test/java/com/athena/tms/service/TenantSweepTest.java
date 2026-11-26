package com.athena.tms.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.athena.tms.PostgresSpringTest;
import com.athena.tms.dao.TenantRepository;
import com.athena.tms.service.TenantScopedExecutor.TenantSweep;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class TenantSweepTest extends PostgresSpringTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private TenantScopedExecutor executor;
    private List<Long> hotels;

    @BeforeEach
    void oneSecondStatementTimeout() {
        executor = new TenantScopedExecutor(tenantRepository, jdbcTemplate, transactionManager, meters, "1s");
        hotels = executor.tenantIds().stream().sorted().toList();
        assertThat(hotels).as("至少要兩家飯店，才看得出一家出錯時另一家有沒有照跑").hasSizeGreaterThanOrEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void oneHotelThrowingDoesNotStopTheOthers(int brokenIndex) {
        long broken = hotels.get(brokenIndex);
        Set<Long> ran = ConcurrentHashMap.newKeySet();

        TenantSweep sweep = executor.forEachTenant("test-job", tenantId -> {
            if (tenantId == broken) {
                throw new IllegalStateException("這家飯店的資料壞了");
            }
            ran.add(tenantId);
        });

        assertThat(ran).containsExactlyInAnyOrderElementsOf(hotels.stream().filter(id -> id != broken).toList());
        assertThat(sweep.failedTenants()).containsExactly(broken);
        assertThat(sweep.succeeded()).isEqualTo(hotels.size() - 1);
        assertThat(meters.get("tms.scheduled.tenant.failures").tag("job", "test-job").tag("tenant", Long.toString(broken))
                .counter().count()).isEqualTo(1.0);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void oneHotelsSlowQueryIsCutOffAndTheOthersStillRun(int slowIndex) {
        long slow = hotels.get(slowIndex);
        Set<Long> ran = ConcurrentHashMap.newKeySet();
        long started = System.nanoTime();

        TenantSweep sweep = executor.forEachTenant("test-job", tenantId -> {
            if (tenantId == slow) {
                jdbcTemplate.queryForList("SELECT pg_sleep(5)");
            }
            ran.add(tenantId);
        });

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(4));
        assertThat(sweep.failedTenants()).containsExactly(slow);
        assertThat(ran).containsExactlyInAnyOrderElementsOf(hotels.stream().filter(id -> id != slow).toList());
    }
}
