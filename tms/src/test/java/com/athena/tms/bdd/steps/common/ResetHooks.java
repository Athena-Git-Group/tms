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

/**
 * 每個 scenario 開始前還原測試時鐘並清空業務資料，避免跨 feature 的唯一鍵碰撞（例如兩個 feature 都用 305 房）。
 * 保留 flyway_schema_history 與 tenants：租戶由 TenantFixtures 快取重用，schema 歷史不可動。
 * 以 information_schema 動態列表，之後加表不必回來改這裡。
 */
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

    /**
     * 清資料庫之前先等推播消費者把上一個情境的推播處理完：消費者是非同步的，若員工資料先被
     * 清掉，它寫送達紀錄會撞外鍵、進入重試，拖慢下一個情境。追不上也只記錄、不讓情境失敗。
     */
    @Before(order = 0)
    public void drainPushConsumer() {
        awaitNoPendingPushOutbox(Duration.ofSeconds(10));
        kafkaGroupAwait.awaitCaughtUp("tms-push-sender", topics.getPush(), Duration.ofSeconds(15));
        recordingPushSender.reset();
    }

    /**
     * 測試時鐘是單例，前一個情境撥過的時間（例如撥到班別之外）不還原就會帶進下一個情境，
     * 讓依賴「現在」的判定隨執行順序改變。每個情境開場還原成預設的測試日中午。
     */
    @Before(order = 1)
    public void resetClock() {
        clock.reset();
    }

    /**
     * 清表時若剛好撞上推播消費者的寫入而被判成死結，重試一次。消費者每句 SQL 各自一個交易，
     * 死結本來就罕見，重試一次足以收尾，不讓偶發的時序問題變成情境失敗。
     */
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

    /** 等 relay 把上一個情境留下的 push outbox 都送上 Kafka；送不完（例如卡在重試）就不再等。 */
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
