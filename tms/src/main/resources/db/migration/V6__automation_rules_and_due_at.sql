-- SLA/自動化規則增補的 data 側補遺：
-- dsl.md（待接池/系統準備工單）點名的 automation_rules 表、tasks.due_at、tasks.rule_hit
-- 與 tenant_config 的 SLA 鍵，在 dbml 補記後由本檔落地。esc_level 沿用 V1 的保留欄位。

ALTER TABLE tasks ADD COLUMN due_at   timestamptz;  -- 到期時間；排序與逾時判定優先於 SLA 時限
ALTER TABLE tasks ADD COLUMN rule_hit text;         -- 自動化規則命中的規則名

-- 自動化規則：命中建準備工單（source=system、pool_source=system、rule_hit=name），
-- 冪等鍵＝房號｜規則｜當地日期；刪除/停用不回收已建任務。
CREATE TABLE automation_rules (
    id                uuid        PRIMARY KEY,
    tenant_id         bigint      NOT NULL,
    name              text        NOT NULL,
    trigger_source    text        NOT NULL CONSTRAINT chk_automation_rules_trigger
                                  CHECK (trigger_source IN ('reservation','schedule')),
    condition         text        NOT NULL, -- 訂房屬性值（如 VIP）或每日時點 HH:mm
    category          text        NOT NULL CONSTRAINT chk_automation_rules_category
                                  CHECK (category IN ('cleaning','amenity','other')),
    room_no           text,                 -- 排程規則可空＝公共區域
    due_after_minutes int,                  -- 建單後 N 分鐘到期，寫入 tasks.due_at
    enabled           boolean     NOT NULL DEFAULT true,
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    CONSTRAINT fk_automation_rules_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
CREATE UNIQUE INDEX uq_automation_rules_name ON automation_rules (tenant_id, name);

-- tenant_config.key 擴充 SLA/升級鏈鍵（逾時升級設定 Given 的落地鍵）
ALTER TABLE tenant_config DROP CONSTRAINT chk_tenant_config_key;
ALTER TABLE tenant_config ADD CONSTRAINT chk_tenant_config_key
    CHECK (key IN ('standard_cleaning_minutes','shift_capacity','inspection_items',
                   'sop_items','guest_info_pages','request_menu','minutes_per_credit',
                   'classification_rules','scan_fail_threshold','low_rating_threshold',
                   'shift_times','retention_days','inspection_waived_cleaning_types',
                   'sla_minutes_cleaning','sla_minutes_amenity','sla_minutes_other',
                   'escalation_step_minutes','escalation_chain_enabled'));

GRANT SELECT, INSERT, UPDATE, DELETE ON automation_rules TO tms_app;
ALTER TABLE automation_rules ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_automation_rules ON automation_rules USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
