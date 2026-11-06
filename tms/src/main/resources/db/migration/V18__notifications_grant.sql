-- V18：補 V16__notifications.sql 漏掉的存取控制。
-- V16 建了 notifications 表卻整檔沒有 GRANT／RLS／policy，執行期 tms_app（非超級使用者）
-- 連 GET /notifications 都 permission denied；而且這張表帶 tenant_id，不開 RLS 就是跨租戶可見。
-- 比照 V5__staff_app_increment.sql 的慣例：GRANT 接 ENABLE ROW LEVEL SECURITY 再接 tenant_isolation policy。
-- 不改 V16 既有的表結構、索引與註解——已套用的 migration 改了會壞 checksum。

GRANT SELECT, INSERT, UPDATE, DELETE ON notifications TO tms_app;

ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_notifications ON notifications
    USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
