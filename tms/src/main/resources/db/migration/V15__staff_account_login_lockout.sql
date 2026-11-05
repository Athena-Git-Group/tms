-- 登入憑證的三個狀態欄：臨時密碼強制改密、連續錯誤計數、鎖定到期。
-- 清零／累加／達 5 次鎖 15 分鐘的規則在應用層（登入服務），資料層只承載狀態。
ALTER TABLE staff_accounts
    ADD COLUMN must_change_password boolean NOT NULL DEFAULT false,
    ADD COLUMN failed_login_count integer NOT NULL DEFAULT 0,
    ADD COLUMN locked_until timestamptz;
