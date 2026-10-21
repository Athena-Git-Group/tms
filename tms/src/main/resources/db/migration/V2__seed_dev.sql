-- 開發/測試種子資料，照 spec §5.2 逐筆承載。
-- 兩個租戶是刻意的：跨租戶隔離（RLS、複合外鍵、E-12 回 404）沒有第二個租戶
-- 根本測不到。tasks/task_events 不在這裡種——那是任務引擎的產物，
-- 種了反而讓重放驗證（SC-006）出現來路不明的事件。
-- UUID 用固定值而非隨機：測試要能按 id 斷言，重建資料庫結果不變。
-- 員工密碼一律 demo1234（bcrypt），僅供開發環境。

INSERT INTO tenants (id, name, created_at) OVERRIDING SYSTEM VALUE VALUES
    (1, '示範飯店', '2026-10-01 09:00:00+08'),
    (2, '示範二店', '2026-10-01 09:00:00+08');
-- 手動指定 id 後要撥序列，否則下一個自動取號會撞 1
SELECT setval(pg_get_serial_sequence('tenants', 'id'), 2, true);

-- spec §5.2 staff_accounts：hk1/mt1/mg1，外加 frontdesk 與 owner 各一補齊
-- 角色×動作矩陣測試；租戶 2 也用 login_id=hk1 以證明唯一性是租戶內不是全域
INSERT INTO staff_accounts (id, tenant_id, name, login_id, password_hash, role, dept, active, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-00000000a001', 1, '小美',   'hk1', '$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW', 'housekeeping', 'housekeeping', true, '2026-10-01 09:00:00+08', '2026-10-01 09:00:00+08'),
    ('00000000-0000-7000-8000-00000000a002', 1, '阿強',   'mt1', '$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW', 'engineering',  'engineering',  true, '2026-10-01 09:00:00+08', '2026-10-01 09:00:00+08'),
    ('00000000-0000-7000-8000-00000000a003', 1, '王主管', 'mg1', '$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW', 'manager',      NULL,           true, '2026-10-01 09:00:00+08', '2026-10-01 09:00:00+08'),
    ('00000000-0000-7000-8000-00000000a004', 1, '林櫃檯', 'fd1', '$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW', 'frontdesk',    'frontdesk',    true, '2026-10-01 09:00:00+08', '2026-10-01 09:00:00+08'),
    ('00000000-0000-7000-8000-00000000a005', 1, '張老闆', 'ow1', '$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW', 'owner',        NULL,           true, '2026-10-01 09:00:00+08', '2026-10-01 09:00:00+08'),
    ('00000000-0000-7000-8000-00000002a001', 2, '小芳',   'hk1', '$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW', 'housekeeping', 'housekeeping', true, '2026-10-01 09:00:00+08', '2026-10-01 09:00:00+08');

-- spec §5.2 rooms（tenant 1：301/305/502）；租戶 2 一間就夠跨租戶測試
INSERT INTO rooms (id, tenant_id, room_no, floor, room_type, hk_state, occupancy, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000301', 1, '301', 3, 'standard_double', 'dirty',   'occupied', '2026-10-19 06:00:00+08'),
    ('00000000-0000-7000-8000-000000000305', 1, '305', 3, 'standard_double', 'cleaned', 'occupied', '2026-10-19 06:00:00+08'),
    ('00000000-0000-7000-8000-000000000502', 1, '502', 5, 'suite',           'dirty',   'vacant',   '2026-10-19 06:00:00+08'),
    ('00000000-0000-7000-8000-000000020101', 2, '101', 1, 'standard_double', 'dirty',   'vacant',   '2026-10-19 06:00:00+08');

-- spec §5.2 reservations_cache 三筆。住期帶時刻（15:00 入住／11:00 退房）：
-- EXCLUDE 用 tstzrange，純日期界線會讓「當天退房＋當天入住」被誤判重疊
INSERT INTO reservations_cache (id, tenant_id, reservation_ref, room_id, guest_name, lang, attrs, checkin_at, checkout_at, source, synced_at, superseded_at) VALUES
    ('00000000-0000-7000-8000-00000000b001', 1, 'PMS-88321',            '00000000-0000-7000-8000-000000000305', NULL, NULL, '{"vip": true}', '2026-10-18 15:00:00+08', '2026-10-20 11:00:00+08', 'pms',   '2026-10-19 06:00:00+08', NULL),
    ('00000000-0000-7000-8000-00000000b002', 1, 'excel:2026-10-19:301', '00000000-0000-7000-8000-000000000301', NULL, NULL, NULL,            '2026-10-19 00:00:00+08', '2026-10-19 11:00:00+08', 'excel', '2026-10-19 06:00:00+08', NULL),
    ('00000000-0000-7000-8000-00000000b003', 1, 'PMS-88347',            '00000000-0000-7000-8000-000000000502', NULL, NULL, '{"pet": true}', '2026-10-17 15:00:00+08', '2026-10-19 11:00:00+08', 'pms',   '2026-10-19 06:00:00+08', NULL);

-- tenant_config 封閉鍵集初值。刻意不種 inspection_items / sop_items 兩鍵：
-- 這兩鍵只是 settings API 的門面，config 裡再放一份會跟 sop_items 表漂移成雙真相。
-- updated_by 一律 NULL＝系統初始化，不是漏填
INSERT INTO tenant_config (tenant_id, key, value, updated_at, updated_by) VALUES
    (1, 'standard_cleaning_minutes', '30', '2026-10-01 09:00:00+08', NULL),
    (1, 'shift_capacity',            '{"morning": 960, "afternoon": 480}', '2026-10-01 09:00:00+08', NULL),
    (1, 'guest_info_pages',          '[{"key": "wifi", "title": "Wi-Fi 使用說明", "body": "SSID: TMS-Hotel，密碼見房卡套"}, {"key": "breakfast", "title": "早餐時間與地點", "body": "07:00–10:00 於 2 樓餐廳"}, {"key": "checkout", "title": "退房時間", "body": "中午 11:00 前退房"}]', '2026-10-01 09:00:00+08', NULL),
    (1, 'request_menu',              '[{"key": "towel", "label": "加毛巾", "dept": "housekeeping", "type": "amenity"}, {"key": "water", "label": "礦泉水", "dept": "housekeeping", "type": "amenity"}, {"key": "toothbrush", "label": "牙刷組", "dept": "housekeeping", "type": "amenity"}, {"key": "cleaning", "label": "清潔服務", "dept": "housekeeping", "type": "cleaning"}]', '2026-10-01 09:00:00+08', NULL),
    (1, 'minutes_per_credit',        '30', '2026-10-01 09:00:00+08', NULL),
    (1, 'classification_rules',      '[{"rule_id": "r-aircon", "keywords": ["冷氣", "空調", "air con"], "dept": "engineering", "type": "repair"}, {"rule_id": "r-towel", "keywords": ["毛巾", "towel"], "dept": "housekeeping", "type": "amenity"}, {"rule_id": "r-noise", "keywords": ["吵", "noise"], "dept": "frontdesk", "type": "other"}]', '2026-10-01 09:00:00+08', NULL),
    (1, 'scan_fail_threshold',       '10', '2026-10-01 09:00:00+08', NULL),
    (1, 'low_rating_threshold',      '2', '2026-10-01 09:00:00+08', NULL),
    (1, 'shift_times',               '{"morning": {"start": "08:00", "end": "16:00"}, "afternoon": {"start": "16:00", "end": "23:00"}}', '2026-10-01 09:00:00+08', NULL),
    (1, 'retention_days',            '365', '2026-10-01 09:00:00+08', NULL),
    (2, 'standard_cleaning_minutes', '30', '2026-10-01 09:00:00+08', NULL),
    (2, 'shift_times',               '{"morning": {"start": "08:00", "end": "16:00"}}', '2026-10-01 09:00:00+08', NULL);

-- spec 卡片點名：sop_items 要 cleaning＋inspection 兩類（查房項目同構共表）
INSERT INTO sop_items (id, tenant_id, applies_to, item_key, label, sort_order, active, created_at) VALUES
    ('00000000-0000-7000-8000-00000000c001', 1, 'cleaning',   'bed_sheets', '更換床單被套',   1, true, '2026-10-01 09:00:00+08'),
    ('00000000-0000-7000-8000-00000000c002', 1, 'cleaning',   'towels',     '補齊毛巾浴巾',   2, true, '2026-10-01 09:00:00+08'),
    ('00000000-0000-7000-8000-00000000c003', 1, 'cleaning',   'amenities',  '補齊備品',       3, true, '2026-10-01 09:00:00+08'),
    ('00000000-0000-7000-8000-00000000c004', 1, 'cleaning',   'bathroom',   '浴室清潔',       4, true, '2026-10-01 09:00:00+08'),
    ('00000000-0000-7000-8000-00000000c005', 1, 'cleaning',   'trash',      '倒垃圾',         5, true, '2026-10-01 09:00:00+08'),
    ('00000000-0000-7000-8000-00000000c006', 1, 'cleaning',   'floor',      '地板吸塵拖地',   6, true, '2026-10-01 09:00:00+08'),
    ('00000000-0000-7000-8000-00000000c011', 1, 'inspection', 'bed',        '床鋪平整',       1, true, '2026-10-01 09:00:00+08'),
    ('00000000-0000-7000-8000-00000000c012', 1, 'inspection', 'bathroom',   '浴室無水痕',     2, true, '2026-10-01 09:00:00+08'),
    ('00000000-0000-7000-8000-00000000c013', 1, 'inspection', 'amenities',  '備品齊全',       3, true, '2026-10-01 09:00:00+08'),
    ('00000000-0000-7000-8000-00000000c014', 1, 'inspection', 'floor',      '地面乾淨',       4, true, '2026-10-01 09:00:00+08'),
    ('00000000-0000-7000-8000-00000000c015', 1, 'inspection', 'odor',       '無異味',         5, true, '2026-10-01 09:00:00+08');
