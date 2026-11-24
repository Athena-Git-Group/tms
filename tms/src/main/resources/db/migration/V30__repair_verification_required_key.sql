-- 需要工務主管驗收（boolean，未設定時視為 true）。
-- 設為 false 時，工務按完成就結案，不進主管的待驗收清單。工務按完成那一刻才讀這個值。
ALTER TABLE tenant_config DROP CONSTRAINT chk_tenant_config_key;
ALTER TABLE tenant_config ADD CONSTRAINT chk_tenant_config_key
    CHECK (key IN ('standard_cleaning_minutes','shift_capacity','inspection_items',
                   'sop_items','guest_info_pages','request_menu','minutes_per_credit',
                   'classification_rules','scan_fail_threshold','low_rating_threshold',
                   'shift_times','retention_days','inspection_waived_cleaning_types',
                   'inspection_waived_enabled','pms.writeback.room_status','pms.provider',
                   'unclaimed_reminder_minutes','notification_retention_days',
                   'completion_celebration_enabled','self_inspection_allowed',
                   'repair_verification_required'));
