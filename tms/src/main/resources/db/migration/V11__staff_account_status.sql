-- DSL 員工狀態用語有三態：在職 active、待開通 invited、停用 inactive。
-- 基線只有 active boolean，invited 跟 inactive 擠不進同一個布林；status 承載三態，
-- active 保留為「可登入、可被挑人、收推播」的衍生旗標（只有 status=active 為 true），
-- 既有以 active 過濾收件人／登入的查詢不需要跟著改。
ALTER TABLE staff_accounts
    ADD COLUMN status text NOT NULL DEFAULT 'active'
    CONSTRAINT chk_staff_accounts_status CHECK (status IN ('invited','active','inactive'));

UPDATE staff_accounts SET status = CASE WHEN active THEN 'active' ELSE 'inactive' END;
