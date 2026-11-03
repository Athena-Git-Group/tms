-- V8：免查房鎖定與結案（plan 001 Phase 4E）需要的 tenant_config 鍵集擴充。
-- 只動 CHECK，不建新表、不回填資料：
--   inspection_waived_enabled   免查房全域開關（開工當下鎖定到 tasks.inspection_waived）
--   pms.writeback.room_status   房態回寫開關（關閉時照樣合格結案，但不寫 outbox pms_status）
--   pms.provider                PMS 供應商；manual（手動匯入模式）時沒有回寫對象，不寫 outbox
ALTER TABLE tenant_config DROP CONSTRAINT chk_tenant_config_key;
ALTER TABLE tenant_config ADD CONSTRAINT chk_tenant_config_key
    CHECK (key IN ('standard_cleaning_minutes','shift_capacity','inspection_items',
                   'sop_items','guest_info_pages','request_menu','minutes_per_credit',
                   'classification_rules','scan_fail_threshold','low_rating_threshold',
                   'shift_times','retention_days','inspection_waived_cleaning_types',
                   'sla_minutes_cleaning','sla_minutes_amenity','sla_minutes_other',
                   'escalation_step_minutes','escalation_chain_enabled',
                   'inspection_waived_enabled','pms.writeback.room_status','pms.provider'));
