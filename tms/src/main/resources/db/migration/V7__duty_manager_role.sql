-- SLA/自動化規則增補的 data 側補遺（續 V6）：
-- DSL 部門用語有「值班經理 role duty_manager」（逾時升級 L3 收件人），
-- 但基線 role CHECK 沒有這個值。dbml 已同步補記。
ALTER TABLE staff_accounts DROP CONSTRAINT chk_staff_accounts_role;
ALTER TABLE staff_accounts ADD CONSTRAINT chk_staff_accounts_role
    CHECK (role IN ('frontdesk','housekeeping','engineering','manager','duty_manager','owner'));
