-- 通知中心的持久通知清單：每筆對應一次推播事件的單一收件人紀錄。
-- outbox kind=push 展開一人一封時，同一交易一併寫入一筆本表；outbox 本身仍是一次性 relay。
-- 「今天／更早」分組依呼叫者時區在應用層計算，不在本表落地。
CREATE TABLE notifications (
    id                 uuid        PRIMARY KEY,
    tenant_id          bigint      NOT NULL,
    recipient_staff_id uuid        NOT NULL,
    title              text        NOT NULL, -- 推播標題全文，與 PushPayload.title 相同文案
    body               text,                 -- 選填
    target             jsonb       NOT NULL, -- 導頁去處，形狀＝contracts PushTarget（kind=task|pool|home）
    created_at         timestamptz NOT NULL, -- 收到時間；通知中心依此新到舊排序
    read_at            timestamptz,          -- 標記已讀的時間；未讀為 NULL
    CONSTRAINT fk_notifications_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (tenant_id, recipient_staff_id)
        REFERENCES staff_accounts (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- 通知中心清單查詢走「某收件人的通知、新到舊」一種路徑，先建好對應索引
CREATE INDEX idx_notifications_recipient_created
    ON notifications (tenant_id, recipient_staff_id, created_at);

-- 通知不能無限堆：各租戶可用 notification_retention_days 決定通知保留幾天，
-- 清除排程讀這個鍵刪逾期列。租戶沒設定時，應用層以 30 天為預設，資料層不塞初始值。
ALTER TABLE tenant_config DROP CONSTRAINT chk_tenant_config_key;
ALTER TABLE tenant_config ADD CONSTRAINT chk_tenant_config_key
    CHECK (key IN ('standard_cleaning_minutes','shift_capacity','inspection_items',
                   'sop_items','guest_info_pages','request_menu','minutes_per_credit',
                   'classification_rules','scan_fail_threshold','low_rating_threshold',
                   'shift_times','retention_days','inspection_waived_cleaning_types',
                   'inspection_waived_enabled','pms.writeback.room_status','pms.provider',
                   'unclaimed_reminder_minutes','notification_retention_days'));
