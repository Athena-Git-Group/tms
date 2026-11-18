-- V12：裁決 B 廢除逾時升級鏈，改「未接提醒」兩段門檻（plan 001-staff-app-increment /dsl-refine）。
-- tenant_config.key 封閉鍵集變動：
--   新增 unclaimed_reminder_minutes  待認領未接提醒門檻（一般單／急件各自的提醒同仁／通知主管分鐘數）
--                                    值如 {"cleaning":{"staffReminded":10,"managerNotified":20},
--                                         "urgent":{"staffReminded":3,"managerNotified":8}}
--   移除 sla_minutes_cleaning、sla_minutes_amenity、sla_minutes_other、
--        escalation_step_minutes、escalation_chain_enabled（隨裁決 B 廢除的逾時升級鏈設定）
-- 先清掉這五鍵既有資料列，否則新 CHECK 加不上去。
DELETE FROM tenant_config
    WHERE key IN ('sla_minutes_cleaning','sla_minutes_amenity','sla_minutes_other',
                  'escalation_step_minutes','escalation_chain_enabled');

ALTER TABLE tenant_config DROP CONSTRAINT chk_tenant_config_key;
ALTER TABLE tenant_config ADD CONSTRAINT chk_tenant_config_key
    CHECK (key IN ('standard_cleaning_minutes','shift_capacity','inspection_items',
                   'sop_items','guest_info_pages','request_menu','minutes_per_credit',
                   'classification_rules','scan_fail_threshold','low_rating_threshold',
                   'shift_times','retention_days','inspection_waived_cleaning_types',
                   'inspection_waived_enabled','pms.writeback.room_status','pms.provider',
                   'unclaimed_reminder_minutes'));
