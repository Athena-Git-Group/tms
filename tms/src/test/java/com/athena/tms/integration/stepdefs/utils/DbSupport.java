package com.athena.tms.integration.stepdefs.utils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public class DbSupport {

    private static final String DEFAULT_SHIFT_TIMES = """
            {"morning": {"start": "08:00", "end": "16:00"}, "afternoon": {"start": "16:00", "end": "23:00"}}""";

    private final JdbcTemplate appJdbc;
    private final JdbcTemplate adminJdbc;

    public DbSupport(JdbcTemplate appJdbc, String adminUrl, String adminUser, String adminPassword) {
        this.appJdbc = appJdbc;
        this.adminJdbc = new JdbcTemplate(new DriverManagerDataSource(adminUrl, adminUser, adminPassword));
    }

    public JdbcTemplate app() {
        return appJdbc;
    }

    public JdbcTemplate admin() {
        return adminJdbc;
    }

    public void cleanBusinessTables() {
        adminJdbc.update("DELETE FROM task_sop_checks");
        adminJdbc.update("DELETE FROM inspections");
        adminJdbc.update("DELETE FROM task_events");
        adminJdbc.update("DELETE FROM handover_reads");
        deleteOutboxRetryingOnDeadlock();
        adminJdbc.update("DELETE FROM processed_ops");
        adminJdbc.update("DELETE FROM tasks");
        adminJdbc.update("DELETE FROM guest_sessions");
        adminJdbc.update("DELETE FROM room_qr_tokens");
        adminJdbc.update("DELETE FROM staff_devices");
        adminJdbc.update("DELETE FROM leaves");
        adminJdbc.update("DELETE FROM integration_api_keys");
        adminJdbc.update("UPDATE staff_accounts SET active = true WHERE active = false");
        adminJdbc.update("UPDATE tenant_config SET value = '10' WHERE tenant_id = 1 AND key = 'scan_fail_threshold' AND value <> '10'");
        adminJdbc.update("UPDATE tenant_config SET value = ?::jsonb WHERE tenant_id = 1 AND key = 'shift_times'", DEFAULT_SHIFT_TIMES);
    }

    private void deleteOutboxRetryingOnDeadlock() {
        try {
            adminJdbc.update("DELETE FROM outbox");
        } catch (PessimisticLockingFailureException deadlock) {
            adminJdbc.update("DELETE FROM outbox");
        }
    }

    public long countAll(String table) {
        Long count = adminJdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return count == null ? -1 : count;
    }

    public String outboxStatus(long outboxId) {
        return adminJdbc.queryForObject("SELECT status FROM outbox WHERE id = ?", String.class, outboxId);
    }

    public int outboxAttempts(long outboxId) {
        Integer attempts = adminJdbc.queryForObject("SELECT attempts FROM outbox WHERE id = ?", Integer.class, outboxId);
        return attempts == null ? -1 : attempts;
    }

    public int outboxUnsentCount(String aggregateKey) {
        Integer unsent = adminJdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE aggregate_key = ? AND status <> 'sent'", Integer.class, aggregateKey);
        return unsent == null ? -1 : unsent;
    }

    public int outboxCount(String aggregateKey) {
        Integer count = adminJdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE aggregate_key = ?", Integer.class, aggregateKey);
        return count == null ? -1 : count;
    }

    public int processedOpsCount(long tenantId, String opId) {
        Integer count = adminJdbc.queryForObject(
                "SELECT count(*) FROM processed_ops WHERE tenant_id = ? AND op_id = ?", Integer.class, tenantId, opId);
        return count == null ? -1 : count;
    }

    public List<String> eventTypes(UUID taskId) {
        return adminJdbc.queryForList("SELECT type FROM task_events WHERE task_id = ? ORDER BY seq", String.class, taskId);
    }

    public List<Integer> eventSeqs(UUID taskId) {
        return adminJdbc.queryForList("SELECT seq FROM task_events WHERE task_id = ? ORDER BY seq", Integer.class, taskId);
    }

    public int eventCountOfType(UUID taskId, String type) {
        Integer count = adminJdbc.queryForObject(
                "SELECT count(*) FROM task_events WHERE task_id = ? AND type = ?", Integer.class, taskId, type);
        return count == null ? -1 : count;
    }

    public String eventPayload(UUID taskId, int seq) {
        return adminJdbc.queryForObject(
                "SELECT payload FROM task_events WHERE task_id = ? AND seq = ?", String.class, taskId, seq);
    }

    public String eventPayloadOfType(UUID taskId, String type) {
        return adminJdbc.queryForObject(
                "SELECT payload FROM task_events WHERE task_id = ? AND type = ? ORDER BY seq DESC LIMIT 1", String.class, taskId, type);
    }

    public Map<String, Object> taskRow(UUID taskId) {
        return adminJdbc.queryForMap(
                "SELECT state, assignee_id, event_seq, redo_count, done_at, updated_at FROM tasks WHERE id = ?", taskId);
    }
}
