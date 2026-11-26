-- 排程工作跨 instance 同一時間只跑一份，ShedLock 用這張表搶鎖。這不是飯店資料，不加 RLS。
-- 欄位不帶時區：ShedLock 以資料庫時間寫入 UTC，帶時區的欄位會被連線的時區設定位移。
CREATE TABLE shedlock (
    name       varchar(64)  NOT NULL PRIMARY KEY,
    lock_until timestamp    NOT NULL,
    locked_at  timestamp    NOT NULL,
    locked_by  varchar(255) NOT NULL
);
GRANT SELECT, INSERT, UPDATE ON shedlock TO tms_app;
