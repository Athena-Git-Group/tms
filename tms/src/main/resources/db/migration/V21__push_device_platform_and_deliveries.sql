-- 推播真正送出所需的兩件事：裝置要記平台（APNS 與 FCM 的訊息包法不同），
-- 以及每一筆推播要有一列送達紀錄，讓消費者能認出重複投遞、也讓人查得到為什麼沒送到。

-- 裝置平台。舊列（登入時順手登記、還沒帶平台的）維持 NULL，送出時當作 FCM 一般裝置處理。
ALTER TABLE staff_devices ADD COLUMN platform text;
ALTER TABLE staff_devices ADD CONSTRAINT chk_staff_devices_platform
    CHECK (platform IS NULL OR platform IN ('ios', 'android'));

-- 推播送達紀錄：一列＝一把冪等鍵（也就是 outbox 的一筆 push）。
-- 消費者處理每一筆 Kafka 訊息前先查這張表，已有「送出」或「無可用裝置」的列就跳過，
-- 所以同一筆推播被 relay 或 Kafka 重送時裝置最多只響一次。
-- status：
--   sent              至少有一台裝置送出成功
--   skipped_no_token  收件人沒有任何未撤銷、有推播地址的裝置
--   failed            每台裝置都送失敗（含暫時性錯誤等待重試中、以及重試用盡）
CREATE TABLE push_deliveries (
    id                 bigserial   PRIMARY KEY,
    tenant_id          bigint      NOT NULL,
    idempotency_key    text        NOT NULL, -- 與 outbox.idempotency_key 同一把鍵
    recipient_staff_id uuid        NOT NULL,
    status             text        NOT NULL,
    attempts           integer     NOT NULL DEFAULT 0, -- 消費者處理這把鍵的次數（含重試）
    last_error         text,                           -- 最後一次失敗的原因，成功後保留供查閱
    created_at         timestamptz NOT NULL,
    sent_at            timestamptz,                    -- 第一台裝置送出成功的時間
    CONSTRAINT fk_push_deliveries_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_push_deliveries_recipient FOREIGN KEY (tenant_id, recipient_staff_id)
        REFERENCES staff_accounts (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uq_push_deliveries_key UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT chk_push_deliveries_status CHECK (status IN ('sent', 'skipped_no_token', 'failed'))
);

CREATE INDEX idx_push_deliveries_recipient_created
    ON push_deliveries (tenant_id, recipient_staff_id, created_at);

GRANT SELECT, INSERT, UPDATE, DELETE ON push_deliveries TO tms_app;
GRANT USAGE, SELECT ON SEQUENCE push_deliveries_id_seq TO tms_app;

ALTER TABLE push_deliveries ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_push_deliveries ON push_deliveries
    USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
