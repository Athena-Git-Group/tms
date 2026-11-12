package com.athena.tms.bdd.steps.common;

import com.athena.tms.bdd.support.KafkaGroupAwait;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.RecordingPushSender;
import com.athena.tms.config.KafkaTopicsProperties;
import io.cucumber.java.Before;
import java.time.Duration;
import java.util.List;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

public class ResetHooks {

    private final JdbcClient jdbc;
    private final KafkaGroupAwait kafkaGroupAwait;
    private final KafkaTopicsProperties topics;
    private final RecordingPushSender recordingPushSender;
    private final MutableClock clock;

    public ResetHooks(
            JdbcClient jdbc,
            KafkaGroupAwait kafkaGroupAwait,
            KafkaTopicsProperties topics,
            RecordingPushSender recordingPushSender,
            MutableClock clock) {
        this.jdbc = jdbc;
        this.kafkaGroupAwait = kafkaGroupAwait;
        this.topics = topics;
        this.recordingPushSender = recordingPushSender;
        this.clock = clock;
    }

    @Before(order = 0)
    public void drainPushConsumer() {
        awaitNoPendingPushOutbox(Duration.ofSeconds(10));
        kafkaGroupAwait.awaitCaughtUp("tms-push-sender", topics.getPush(), Duration.ofSeconds(15));
        recordingPushSender.reset();
    }

    @Before(order = 1)
    public void resetClock() {
        clock.reset();
    }

    @Before(order = 1)
    public void resetDatabase() {
        List<String> tables = jdbc.sql("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                  AND table_name NOT IN ('flyway_schema_history', 'tenants')
                """).query(String.class).list();
        if (tables.isEmpty()) {
            return;
        }
        String truncate = "TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE";
        try {
            jdbc.sql(truncate).update();
        } catch (PessimisticLockingFailureException deadlock) {
            jdbc.sql(truncate).update();
        }
    }

    private void awaitNoPendingPushOutbox(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Long pending = jdbc.sql("SELECT count(*) FROM outbox WHERE kind = 'push' AND status = 'pending'")
                    .query(Long.class).single();
            if (pending == 0) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
