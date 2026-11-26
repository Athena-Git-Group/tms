package com.athena.tms.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.athena.tms.PostgresSpringTest;
import com.athena.tms.SharedContainers;
import com.athena.tms.service.TenantScopedExecutor.TenantSweep;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 排程器呼叫的入口要搶跨 instance 的鎖，直接呼叫的掃描方法不經過鎖。
 * 用保留期清理工作來驗，因為它在測試資料上沒有副作用。
 */
class SchedulerLockTest extends PostgresSpringTest {

    private static final String LOCK = "idempotency-retention";

    @Autowired
    private IdempotencyRetentionJob job;

    private final JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(
            SharedContainers.database("junit"), SharedContainers.postgres().getUsername(), SharedContainers.postgres().getPassword()));

    // 用讓鎖過期的方式釋放，不刪列：ShedLock 記得鎖的那一列已存在，之後只會 UPDATE，列被刪掉就再也搶不到。
    @BeforeEach
    @AfterEach
    void releaseLock() {
        admin.update("UPDATE shedlock SET lock_until = timezone('utc', now()) - interval '1 second' WHERE name = ?", LOCK);
    }

    @Test
    void whileAnotherInstanceHoldsTheLockTheScheduledRunIsSkippedButADirectCallStillRuns() {
        admin.update("INSERT INTO shedlock (name, lock_until, locked_at, locked_by) "
                + "VALUES (?, timezone('utc', now()) + interval '1 hour', timezone('utc', now()), 'other-instance') "
                + "ON CONFLICT (name) DO UPDATE SET lock_until = EXCLUDED.lock_until, locked_by = EXCLUDED.locked_by", LOCK);

        assertThat(job.onSchedule()).as("別的 instance 正在跑，排程入口應該略過").isNull();
        assertThat(job.purgeExpiredOps()).as("直接呼叫不經過鎖").isNotNull();
    }

    @Test
    void instancesFiringAtTheSameTimeRunTheJobOnce() throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService instances = Executors.newFixedThreadPool(3);
        try {
            List<Future<TenantSweep>> runs = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                runs.add(instances.submit(() -> {
                    go.await();
                    return job.onSchedule();
                }));
            }
            go.countDown();
            List<TenantSweep> results = new ArrayList<>();
            for (Future<TenantSweep> run : runs) {
                results.add(run.get());
            }
            assertThat(results.stream().filter(Objects::nonNull)).as("同時觸發只有一個真的執行").hasSize(1);
        } finally {
            instances.shutdownNow();
        }

        assertThat(job.onSchedule()).as("剛跑完，鎖至少保留到下一個排程週期前，別的 instance 不會馬上重跑").isNull();
    }
}
