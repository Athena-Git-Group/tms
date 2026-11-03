/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.dao.PessimisticLockingFailureException
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.jdbc.datasource.DriverManagerDataSource
 */
package com.athena.tms.integration.stepdefs.utils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public class DbSupport {
    private final JdbcTemplate appJdbc;
    private final JdbcTemplate adminJdbc;

    public DbSupport(JdbcTemplate appJdbc, String adminUrl, String adminUser, String adminPassword) {
        this.appJdbc = appJdbc;
        DriverManagerDataSource adminDataSource = new DriverManagerDataSource(adminUrl, adminUser, adminPassword);
        this.adminJdbc = new JdbcTemplate((DataSource)adminDataSource);
    }

    public JdbcTemplate app() {
        return this.appJdbc;
    }

    public JdbcTemplate admin() {
        return this.adminJdbc;
    }

    public void cleanBusinessTables() {
        this.adminJdbc.update("DELETE FROM task_sop_checks");
        this.adminJdbc.update("DELETE FROM inspections");
        this.adminJdbc.update("DELETE FROM task_events");
        this.deleteOutboxRetryingOnDeadlock();
        this.adminJdbc.update("DELETE FROM processed_ops");
        this.adminJdbc.update("DELETE FROM tasks");
        this.adminJdbc.update("DELETE FROM guest_sessions");
        this.adminJdbc.update("DELETE FROM room_qr_tokens");
        this.adminJdbc.update("DELETE FROM staff_devices");
        this.adminJdbc.update("DELETE FROM leaves");
        this.adminJdbc.update("DELETE FROM integration_api_keys");
        this.adminJdbc.update("UPDATE staff_accounts SET active = true WHERE active = false");
        this.adminJdbc.update("UPDATE tenant_config SET value = '10' WHERE tenant_id = 1 AND key = 'scan_fail_threshold' AND value <> '10'");
        this.adminJdbc.update("UPDATE tenant_config SET value = ?::jsonb WHERE tenant_id = 1 AND key = 'shift_times'", new Object[]{"{\"morning\": {\"start\": \"08:00\", \"end\": \"16:00\"}, \"afternoon\": {\"start\": \"16:00\", \"end\": \"23:00\"}}"});
    }

    private void deleteOutboxRetryingOnDeadlock() {
        try {
            this.adminJdbc.update("DELETE FROM outbox");
        }
        catch (PessimisticLockingFailureException deadlock) {
            this.adminJdbc.update("DELETE FROM outbox");
        }
    }

    public long countAll(String table) {
        Long count = (Long)this.adminJdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return count == null ? -1L : count;
    }

    public String outboxStatus(long outboxId) {
        return (String)this.adminJdbc.queryForObject("SELECT status FROM outbox WHERE id = ?", String.class, new Object[]{outboxId});
    }

    public int outboxAttempts(long outboxId) {
        Integer attempts = (Integer)this.adminJdbc.queryForObject("SELECT attempts FROM outbox WHERE id = ?", Integer.class, new Object[]{outboxId});
        return attempts == null ? -1 : attempts;
    }

    public int outboxUnsentCount(String aggregateKey) {
        Integer unsent = (Integer)this.adminJdbc.queryForObject("SELECT count(*) FROM outbox WHERE aggregate_key = ? AND status <> 'sent'", Integer.class, new Object[]{aggregateKey});
        return unsent == null ? -1 : unsent;
    }

    public int outboxCount(String aggregateKey) {
        Integer count = (Integer)this.adminJdbc.queryForObject("SELECT count(*) FROM outbox WHERE aggregate_key = ?", Integer.class, new Object[]{aggregateKey});
        return count == null ? -1 : count;
    }

    public int processedOpsCount(long tenantId, String opId) {
        Integer count = (Integer)this.adminJdbc.queryForObject("SELECT count(*) FROM processed_ops WHERE tenant_id = ? AND op_id = ?", Integer.class, new Object[]{tenantId, opId});
        return count == null ? -1 : count;
    }

    public List<String> eventTypes(UUID taskId) {
        return this.adminJdbc.queryForList("SELECT type FROM task_events WHERE task_id = ? ORDER BY seq", String.class, new Object[]{taskId});
    }

    public List<Integer> eventSeqs(UUID taskId) {
        return this.adminJdbc.queryForList("SELECT seq FROM task_events WHERE task_id = ? ORDER BY seq", Integer.class, new Object[]{taskId});
    }

    public int eventCountOfType(UUID taskId, String type) {
        Integer count = (Integer)this.adminJdbc.queryForObject("SELECT count(*) FROM task_events WHERE task_id = ? AND type = ?", Integer.class, new Object[]{taskId, type});
        return count == null ? -1 : count;
    }

    public String eventPayload(UUID taskId, int seq) {
        return (String)this.adminJdbc.queryForObject("SELECT payload FROM task_events WHERE task_id = ? AND seq = ?", String.class, new Object[]{taskId, seq});
    }

    public String eventPayloadOfType(UUID taskId, String type) {
        return (String)this.adminJdbc.queryForObject("SELECT payload FROM task_events WHERE task_id = ? AND type = ? ORDER BY seq DESC LIMIT 1", String.class, new Object[]{taskId, type});
    }

    public Map<String, Object> taskRow(UUID taskId) {
        return this.adminJdbc.queryForMap("SELECT state, assignee_id, event_seq, redo_count, done_at, updated_at FROM tasks WHERE id = ?", new Object[]{taskId});
    }
}
