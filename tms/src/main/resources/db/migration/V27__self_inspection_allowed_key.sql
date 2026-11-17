-- V27：允許主管查自己清的房（boolean，未設定時視為 true）。
-- 只動 CHECK，不建新表、不回填資料：
--   self_inspection_allowed  允許主管查自己清的房
--
-- 未設定或 true 時，房務主管可以查自己清的房，查房判定與待查房清單都把自己清的房當
-- 一般的待查房處理；只有一位房務主管的飯店不必設定，他自己清的房也等得到人查。
-- 飯店設為 false 時，清房者不能查自己清的房：查房判定回 403，自己清的房也不出現在
-- 自己的待查房清單與格數裡。不論設定如何，沒有查房權限的角色都不能送出查房。
-- 版本號從 V27 起跳，V24 到 V26 已保留給另一條開發分支的遷移。
ALTER TABLE tenant_config DROP CONSTRAINT chk_tenant_config_key;
ALTER TABLE tenant_config ADD CONSTRAINT chk_tenant_config_key
    CHECK (key IN ('standard_cleaning_minutes','shift_capacity','inspection_items',
                   'sop_items','guest_info_pages','request_menu','minutes_per_credit',
                   'classification_rules','scan_fail_threshold','low_rating_threshold',
                   'shift_times','retention_days','inspection_waived_cleaning_types',
                   'inspection_waived_enabled','pms.writeback.room_status','pms.provider',
                   'unclaimed_reminder_minutes','notification_retention_days',
                   'completion_celebration_enabled','self_inspection_allowed'));
