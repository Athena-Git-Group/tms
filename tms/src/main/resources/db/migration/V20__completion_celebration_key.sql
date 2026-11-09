-- V20：完成慶祝的氣氛呈現開關（plan 002-celebrate-and-resume，Foundational T401）。
-- 只動 CHECK，不建新表、不回填資料：
--   completion_celebration_enabled  完成慶祝的氣氛呈現開關（boolean，未設定時視為 true）
--
-- 關掉的是氣氛不是接力：false 時完成畫面仍然出現、接續按鈕仍然可用，只是不呈現
-- 飾帶、彩屑、入場動態與今日進度。這一鍵不影響 tasksComplete 回應的 nextStep 與
-- cleaningDoneToday 的計算（見 specs/truth/data/data-model.dbml 的 tenant_config.key 註記）。
ALTER TABLE tenant_config DROP CONSTRAINT chk_tenant_config_key;
ALTER TABLE tenant_config ADD CONSTRAINT chk_tenant_config_key
    CHECK (key IN ('standard_cleaning_minutes','shift_capacity','inspection_items',
                   'sop_items','guest_info_pages','request_menu','minutes_per_credit',
                   'classification_rules','scan_fail_threshold','low_rating_threshold',
                   'shift_times','retention_days','inspection_waived_cleaning_types',
                   'inspection_waived_enabled','pms.writeback.room_status','pms.provider',
                   'unclaimed_reminder_minutes','notification_retention_days',
                   'completion_celebration_enabled'));
