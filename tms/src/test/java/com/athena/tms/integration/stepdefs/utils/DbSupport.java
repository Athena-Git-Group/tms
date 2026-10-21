package com.athena.tms.integration.stepdefs.utils;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 兩條 DB 通道：
 * <ul>
 *   <li>{@link #app()}——應用 datasource（tms_app，RLS 生效：資料庫會自動
 *       濾掉別租戶的資料列）：只用在「要驗
 *       權限／RLS 本身」的斷言（如 task_events 不可改）。</li>
 *   <li>{@link #admin()}——容器管理身分直連：場景間清理與一般斷言。RLS 對
 *       owner 不生效，讀寫不用租戶脈絡，斷言程式碼不會被 RLS 的「靜默零列」
 *       誤導成綠燈。</li>
 * </ul>
 */
public class DbSupport {

    private final JdbcTemplate appJdbc;
    private final JdbcTemplate adminJdbc;

    public DbSupport(JdbcTemplate appJdbc, String adminUrl, String adminUser, String adminPassword) {
        this.appJdbc = appJdbc;
        DriverManagerDataSource adminDataSource =
                new DriverManagerDataSource(adminUrl, adminUser, adminPassword);
        this.adminJdbc = new JdbcTemplate(adminDataSource);
    }

    public JdbcTemplate app() {
        return appJdbc;
    }

    public JdbcTemplate admin() {
        return adminJdbc;
    }

    /**
     * 清業務表、保種子（tenants/staff_accounts/rooms/reservations_cache/
     * sop_items/tenant_config 等設定與主檔不動）。FK 順序：先清 tasks 的子表。
     * 另還原種子的「可變欄位」：停用帳號與掃碼門檻兩個場景會改種子列，
     * 還原放開場清理（而非場景收尾）——場景中途炸掉也不會污染下一景。
     */
    public void cleanBusinessTables() {
        adminJdbc.update("DELETE FROM task_sop_checks");
        adminJdbc.update("DELETE FROM inspections");
        adminJdbc.update("DELETE FROM task_events");
        deleteOutboxRetryingOnDeadlock();
        adminJdbc.update("DELETE FROM processed_ops");
        adminJdbc.update("DELETE FROM tasks");
        adminJdbc.update("DELETE FROM guest_sessions");
        // QR 權杖每個場景由 feature 的「房間QR」表格自行宣告；留著上一景簽的
        // 權杖會撞「一房僅一枚現行」的 partial unique
        adminJdbc.update("DELETE FROM room_qr_tokens");
        adminJdbc.update("DELETE FROM staff_devices");
        adminJdbc.update("DELETE FROM leaves");
        // 整合金鑰由各場景自行簽發（別名→明文對照存 ScenarioContext，場景結束即失效）；
        // 留著上一景的列雖不致撞唯一鍵（明文高熵隨機），但「租戶只剩本景金鑰」
        // 這類盤點斷言會被殘留列污染
        adminJdbc.update("DELETE FROM integration_api_keys");
        adminJdbc.update("UPDATE staff_accounts SET active = true WHERE active = false");
        adminJdbc.update("UPDATE tenant_config SET value = '10'"
                + " WHERE tenant_id = 1 AND key = 'scan_fail_threshold' AND value <> '10'");
        // 查房連動場景會改班次時間窗，還原回 V2 種子值（jsonb 欄要顯式轉型）
        adminJdbc.update("UPDATE tenant_config SET value = ?::jsonb"
                        + " WHERE tenant_id = 1 AND key = 'shift_times'",
                "{\"morning\": {\"start\": \"08:00\", \"end\": \"16:00\"},"
                        + " \"afternoon\": {\"start\": \"16:00\", \"end\": \"23:00\"}}");
    }

    /**
     * 清 outbox 可能撞上 relay 背景輪詢正鎖著的列（兩邊各持部分列鎖＝偶發
     * deadlock，Postgres 會砍掉其中一方）。清理這邊輸了就重試一次：
     * relay 的那筆交易已結束，第二次必然成功，場景不必因此陪葬。
     */
    private void deleteOutboxRetryingOnDeadlock() {
        try {
            adminJdbc.update("DELETE FROM outbox");
        } catch (org.springframework.dao.PessimisticLockingFailureException deadlock) {
            adminJdbc.update("DELETE FROM outbox");
        }
    }

    /** 全表列數（跨租戶、admin 連線）：「不簽 session／不建單」類零增量斷言用。 */
    public long countAll(String table) {
        Long count = adminJdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return count == null ? -1 : count;
    }

    public String outboxStatus(long outboxId) {
        return adminJdbc.queryForObject("SELECT status FROM outbox WHERE id = ?", String.class, outboxId);
    }

    public int outboxAttempts(long outboxId) {
        Integer attempts = adminJdbc.queryForObject(
                "SELECT attempts FROM outbox WHERE id = ?", Integer.class, outboxId);
        return attempts == null ? -1 : attempts;
    }

    public int outboxUnsentCount(String aggregateKey) {
        Integer unsent = adminJdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE aggregate_key = ? AND status <> 'sent'",
                Integer.class, aggregateKey);
        return unsent == null ? -1 : unsent;
    }

    public int outboxCount(String aggregateKey) {
        Integer count = adminJdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE aggregate_key = ?", Integer.class, aggregateKey);
        return count == null ? -1 : count;
    }

    public int processedOpsCount(long tenantId, String opId) {
        Integer count = adminJdbc.queryForObject(
                "SELECT count(*) FROM processed_ops WHERE tenant_id = ? AND op_id = ?",
                Integer.class, tenantId, opId);
        return count == null ? -1 : count;
    }

    public List<String> eventTypes(UUID taskId) {
        return adminJdbc.queryForList(
                "SELECT type FROM task_events WHERE task_id = ? ORDER BY seq", String.class, taskId);
    }

    public List<Integer> eventSeqs(UUID taskId) {
        return adminJdbc.queryForList(
                "SELECT seq FROM task_events WHERE task_id = ? ORDER BY seq", Integer.class, taskId);
    }

    public int eventCountOfType(UUID taskId, String type) {
        Integer count = adminJdbc.queryForObject(
                "SELECT count(*) FROM task_events WHERE task_id = ? AND type = ?",
                Integer.class, taskId, type);
        return count == null ? -1 : count;
    }

    public String eventPayload(UUID taskId, int seq) {
        return adminJdbc.queryForObject(
                "SELECT payload FROM task_events WHERE task_id = ? AND seq = ?",
                String.class, taskId, seq);
    }

    /** 某型別最新一筆事件的 payload（created/cancelled/rated 快照斷言用）。 */
    public String eventPayloadOfType(UUID taskId, String type) {
        return adminJdbc.queryForObject(
                "SELECT payload FROM task_events WHERE task_id = ? AND type = ?"
                        + " ORDER BY seq DESC LIMIT 1",
                String.class, taskId, type);
    }

    /** tasks 列的原始欄位（state/assignee_id/event_seq/redo_count/done_at/updated_at）。 */
    public Map<String, Object> taskRow(UUID taskId) {
        return adminJdbc.queryForMap(
                "SELECT state, assignee_id, event_seq, redo_count, done_at, updated_at "
                        + "FROM tasks WHERE id = ?", taskId);
    }
}
