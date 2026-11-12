#!/usr/bin/env bash
# 用法： ./scripts/seed-demo.sh
#   只需要 docker compose 的 postgres 起著，後端不必起。
#   連線帳號要用管理帳號 tms：業務表有 row level security，tms_app 沒設 app.tenant_id 時讀寫不到任何一列。

set -euo pipefail

DB_HOST="${DEMO_DB_HOST:-127.0.0.1}"
DB_PORT="${DEMO_DB_PORT:-5433}"
DB_NAME="${DEMO_DB_NAME:-tms}"
DB_USER="${DEMO_DB_USER:-tms}"
DB_PASSWORD="${DEMO_DB_PASSWORD:-tms}"

if ! command -v psql >/dev/null 2>&1; then
  echo "[seed-demo] 找不到 psql。brew install postgresql@15，或把它的 bin 加進 PATH。" >&2
  exit 1
fi

echo "[seed-demo] 目標資料庫：postgresql://${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"

PGPASSWORD="$DB_PASSWORD" psql \
  --host "$DB_HOST" --port "$DB_PORT" --username "$DB_USER" --dbname "$DB_NAME" \
  --quiet --no-psqlrc --set ON_ERROR_STOP=1 <<'SQL'
BEGIN;

INSERT INTO tenants (id, name, created_at) OVERRIDING SYSTEM VALUE
VALUES (1, '示範飯店', now())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;
DO $$ BEGIN PERFORM setval(pg_get_serial_sequence('tenants', 'id'), GREATEST(1, (SELECT max(id) FROM tenants)), true); END $$;

DELETE FROM task_sop_checks            WHERE tenant_id = 1;
DELETE FROM task_events                WHERE tenant_id = 1;
DELETE FROM inspections                WHERE tenant_id = 1;
DELETE FROM handover_reads             WHERE tenant_id = 1;
DELETE FROM handovers                  WHERE tenant_id = 1;
DELETE FROM kb_audit_logs              WHERE tenant_id = 1;
DELETE FROM kb_escalations             WHERE tenant_id = 1;
DELETE FROM repair_photos              WHERE tenant_id = 1;
DELETE FROM repair_ticket_contributors WHERE tenant_id = 1;
DELETE FROM repair_tickets             WHERE tenant_id = 1;
DELETE FROM repair_ticket_counters     WHERE tenant_id = 1;
DELETE FROM room_memory                WHERE tenant_id = 1;
DELETE FROM plan_rooms                 WHERE tenant_id = 1;
DELETE FROM push_deliveries            WHERE tenant_id = 1;
DELETE FROM notifications              WHERE tenant_id = 1;
DELETE FROM desk_notes                 WHERE tenant_id = 1;
DELETE FROM guest_sessions             WHERE tenant_id = 1;
DELETE FROM room_qr_tokens             WHERE tenant_id = 1;
DELETE FROM leaves                     WHERE tenant_id = 1;
DELETE FROM outbox                     WHERE tenant_id = 1;
DELETE FROM tasks                      WHERE tenant_id = 1;
DELETE FROM cleaning_plans             WHERE tenant_id = 1;
DELETE FROM reservations_cache         WHERE tenant_id = 1;
DELETE FROM automation_rules           WHERE tenant_id = 1;
DELETE FROM sop_items                  WHERE tenant_id = 1;
DELETE FROM tenant_config              WHERE tenant_id = 1;
DELETE FROM staff_devices              WHERE tenant_id = 1;
DELETE FROM staff_accounts             WHERE tenant_id = 1;
DELETE FROM rooms                      WHERE tenant_id = 1;

INSERT INTO staff_accounts (id, tenant_id, name, login_id, password_hash, role, dept, active, status, created_at, updated_at) VALUES
  ('00000000-0000-7000-8000-00000000a001', 1, '小美',   'hk1', '$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW', 'housekeeping', 'housekeeping', true, 'active', now(), now()),
  ('00000000-0000-7000-8000-00000000a002', 1, '阿強',   'mt1', '$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW', 'engineering',  'engineering',  true, 'active', now(), now()),
  ('00000000-0000-7000-8000-00000000a003', 1, '王主管', 'mg1', '$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW', 'manager',      NULL,           true, 'active', now(), now()),
  ('00000000-0000-7000-8000-00000000a004', 1, '林櫃檯', 'fd1', '$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW', 'frontdesk',    'frontdesk',    true, 'active', now(), now()),
  ('00000000-0000-7000-8000-00000000a005', 1, '阿芳',   'hk2', '$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW', 'housekeeping', 'housekeeping', true, 'active', now(), now()),
  ('00000000-0000-7000-8000-00000000a006', 1, '阿義',   'mt2', '$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW', 'engineering',  'engineering',  true, 'active', now(), now()),
  ('00000000-0000-7000-8000-00000000a007', 1, '陳主管', 'mg2', '$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW', 'manager',      'engineering',  true, 'active', now(), now());

INSERT INTO rooms (id, tenant_id, room_no, floor, room_type, hk_state, occupancy, updated_at) VALUES
  ('00000000-0000-7000-8000-000000000202', 1, '202', 2, 'standard_double', 'dirty',     'vacant',   now()),
  ('00000000-0000-7000-8000-000000000203', 1, '203', 2, 'standard_double', 'dirty',     'vacant',   now()),
  ('00000000-0000-7000-8000-000000000205', 1, '205', 2, 'standard_double', 'cleaned',   'occupied', now()),
  ('00000000-0000-7000-8000-000000000301', 1, '301', 3, 'standard_double', 'cleaned',   'vacant',   now()),
  ('00000000-0000-7000-8000-000000000302', 1, '302', 3, 'standard_double', 'dirty',     'occupied', now()),
  ('00000000-0000-7000-8000-000000000303', 1, '303', 3, 'standard_double', 'dirty',     'vacant',   now()),
  ('00000000-0000-7000-8000-000000000305', 1, '305', 3, 'suite',           'dirty',     'occupied', now()),
  ('00000000-0000-7000-8000-000000000306', 1, '306', 3, 'standard_double', 'cleaned',   'occupied', now()),
  ('00000000-0000-7000-8000-000000000308', 1, '308', 3, 'standard_double', 'dirty',     'vacant',   now()),
  ('00000000-0000-7000-8000-000000000401', 1, '401', 4, 'standard_double', 'inspected', 'vacant',   now()),
  ('00000000-0000-7000-8000-000000000402', 1, '402', 4, 'standard_double', 'inspected', 'vacant',   now()),
  ('00000000-0000-7000-8000-000000000403', 1, '403', 4, 'standard_double', 'dirty',     'vacant',   now()),
  ('00000000-0000-7000-8000-000000000404', 1, '404', 4, 'standard_double', 'dirty',     'vacant',   now()),
  ('00000000-0000-7000-8000-000000000405', 1, '405', 4, 'standard_double', 'cleaned',   'occupied', now()),
  ('00000000-0000-7000-8000-000000000407', 1, '407', 4, 'standard_double', 'cleaned',   'occupied', now()),
  ('00000000-0000-7000-8000-000000000502', 1, '502', 5, 'suite',           'dirty',     'vacant',   now()),
  ('00000000-0000-7000-8000-000000000508', 1, '508', 5, 'suite',           'cleaned',   'occupied', now()),
  ('00000000-0000-7000-8000-000000000900', 1, '公共區域', 0, 'public_area', 'cleaned',  'vacant',   now());

INSERT INTO tenant_config (tenant_id, key, value, updated_at, updated_by) VALUES
  (1, 'standard_cleaning_minutes', '30', now(), NULL),
  (1, 'shift_capacity',            '{"morning": 960, "afternoon": 480}', now(), NULL),
  (1, 'minutes_per_credit',        '30', now(), NULL),
  (1, 'scan_fail_threshold',       '10', now(), NULL),
  (1, 'low_rating_threshold',      '2',  now(), NULL),
  (1, 'retention_days',            '365', now(), NULL),
  (1, 'shift_times',               '{"morning": {"start": "08:00", "end": "16:00"}, "afternoon": {"start": "16:00", "end": "23:00"}}', now(), NULL),
  (1, 'guest_info_pages',          '[{"key": "wifi", "title": "Wi-Fi 使用說明", "body": "SSID: TMS-Hotel，密碼見房卡套"}, {"key": "breakfast", "title": "早餐時間與地點", "body": "07:00–10:00 於 2 樓餐廳"}]', now(), NULL),
  (1, 'request_menu',              '[{"key": "towel", "label": "加毛巾", "dept": "housekeeping", "type": "amenity"}, {"key": "water", "label": "礦泉水", "dept": "housekeeping", "type": "amenity"}, {"key": "cleaning", "label": "清潔服務", "dept": "housekeeping", "type": "cleaning"}]', now(), NULL);

INSERT INTO sop_items (id, tenant_id, applies_to, item_key, label, sort_order, active, created_at) VALUES
  ('00000000-0000-7000-8000-00000000c001', 1, 'cleaning',   'bed_sheets', '更換床單被套', 1, true, now()),
  ('00000000-0000-7000-8000-00000000c002', 1, 'cleaning',   'towels',     '補齊毛巾浴巾', 2, true, now()),
  ('00000000-0000-7000-8000-00000000c003', 1, 'cleaning',   'amenities',  '補齊備品',     3, true, now()),
  ('00000000-0000-7000-8000-00000000c004', 1, 'cleaning',   'bathroom',   '浴室清潔',     4, true, now()),
  ('00000000-0000-7000-8000-00000000c005', 1, 'cleaning',   'trash',      '倒垃圾',       5, true, now()),
  ('00000000-0000-7000-8000-00000000c006', 1, 'cleaning',   'floor',      '地板吸塵拖地', 6, true, now()),
  ('00000000-0000-7000-8000-00000000c011', 1, 'inspection', 'bed',        '床鋪平整',     1, true, now()),
  ('00000000-0000-7000-8000-00000000c012', 1, 'inspection', 'bathroom',   '浴室無水痕',   2, true, now()),
  ('00000000-0000-7000-8000-00000000c013', 1, 'inspection', 'amenities',  '備品齊全',     3, true, now()),
  ('00000000-0000-7000-8000-00000000c014', 1, 'inspection', 'floor',      '地面乾淨',     4, true, now()),
  ('00000000-0000-7000-8000-00000000c015', 1, 'inspection', 'odor',       '無異味',       5, true, now());

INSERT INTO cleaning_plans (id, tenant_id, work_date, shift, status, version, generated_by, algo_version, generated_at, published_at) VALUES
  ('00000000-0000-7000-8000-00000000f001', 1, current_date, '早班', 'published', 1, 'system', 'v1', now(), now());

INSERT INTO plan_rooms (id, tenant_id, plan_id, room_id, staff_id, est_min, route_seq) VALUES
  ('00000000-0000-7000-8000-00000000f101', 1, '00000000-0000-7000-8000-00000000f001', '00000000-0000-7000-8000-000000000301', '00000000-0000-7000-8000-00000000a001', 30, 1),
  ('00000000-0000-7000-8000-00000000f102', 1, '00000000-0000-7000-8000-00000000f001', '00000000-0000-7000-8000-000000000302', '00000000-0000-7000-8000-00000000a001', 30, 2),
  ('00000000-0000-7000-8000-00000000f103', 1, '00000000-0000-7000-8000-00000000f001', '00000000-0000-7000-8000-000000000303', '00000000-0000-7000-8000-00000000a001', 30, 3),
  ('00000000-0000-7000-8000-00000000f104', 1, '00000000-0000-7000-8000-00000000f001', '00000000-0000-7000-8000-000000000305', '00000000-0000-7000-8000-00000000a001', 45, 4),
  ('00000000-0000-7000-8000-00000000f105', 1, '00000000-0000-7000-8000-00000000f001', '00000000-0000-7000-8000-000000000308', '00000000-0000-7000-8000-00000000a001', 30, 5),
  ('00000000-0000-7000-8000-00000000f106', 1, '00000000-0000-7000-8000-00000000f001', '00000000-0000-7000-8000-000000000401', '00000000-0000-7000-8000-00000000a005', 30, 1),
  ('00000000-0000-7000-8000-00000000f107', 1, '00000000-0000-7000-8000-00000000f001', '00000000-0000-7000-8000-000000000402', '00000000-0000-7000-8000-00000000a005', 30, 2);

INSERT INTO reservations_cache (id, tenant_id, reservation_ref, room_id, guest_name, lang, attrs, checkin_at, checkout_at, source, synced_at, superseded_at) VALUES
  ('00000000-0000-7000-8000-00000000b001', 1, 'PMS-88321', '00000000-0000-7000-8000-000000000305', NULL, NULL, '{"vip": true}', date_trunc('day', now()) + interval '15 hours', date_trunc('day', now()) + interval '35 hours', 'pms', now(), NULL),
  ('00000000-0000-7000-8000-00000000b002', 1, 'PMS-88347', '00000000-0000-7000-8000-000000000302', NULL, NULL, '{}',             date_trunc('day', now()) + interval '16 hours', date_trunc('day', now()) + interval '35 hours', 'pms', now(), NULL);

INSERT INTO tasks (id, tenant_id, type, source, room_id, dept, state, event_seq, assignee_id, title, note, est_min,
                   started_at, done_at, work_date, created_at, updated_at, worked_sec, segment_started_at,
                   inspection, inspection_waived, hold_reason, deferred_by_self, redo_count, urgent, priority_notify,
                   pool_source, return_reason, requeue_note, rule_hit, reminder_stage) VALUES
  ('00000000-0000-7000-8000-00000000d001', 1, 'cleaning', 'system', '00000000-0000-7000-8000-000000000302', 'housekeeping', 'doing', 1,
   '00000000-0000-7000-8000-00000000a001', '302 退房打掃', '房客有帶寵物，地毯多吸一次', 30,
   now() - interval '25 minutes', NULL, current_date, now() - interval '3 hours', now(), 0, now() - interval '18 minutes',
   NULL, false, NULL, false, 0, false, false, NULL, NULL, NULL, NULL, NULL),
  ('00000000-0000-7000-8000-00000000d002', 1, 'cleaning', 'system', '00000000-0000-7000-8000-000000000303', 'housekeeping', 'accepted', 1,
   '00000000-0000-7000-8000-00000000a001', '303 退房打掃', NULL, 30,
   NULL, NULL, current_date, now() - interval '3 hours', now(), 0, NULL,
   NULL, false, NULL, false, 0, false, false, NULL, NULL, NULL, NULL, NULL),
  ('00000000-0000-7000-8000-00000000d003', 1, 'cleaning', 'system', '00000000-0000-7000-8000-000000000305', 'housekeeping', 'deferred', 1,
   '00000000-0000-7000-8000-00000000a001', '305 續住打掃', NULL, 45,
   NULL, NULL, current_date, now() - interval '3 hours', now(), 0, NULL,
   NULL, false, '住客掛「請勿打擾」，下午再來', true, 0, false, false, NULL, NULL, NULL, NULL, NULL),
  ('00000000-0000-7000-8000-00000000d004', 1, 'amenity', 'guest', '00000000-0000-7000-8000-000000000308', 'housekeeping', 'interrupted', 1,
   '00000000-0000-7000-8000-00000000a001', '308 補備品', NULL, 15,
   now() - interval '70 minutes', NULL, NULL, now() - interval '2 hours', now(), 900, NULL,
   NULL, false, '備品車缺枕頭套，等補貨', false, 0, false, false, NULL, NULL, NULL, NULL, NULL),
  ('00000000-0000-7000-8000-00000000d005', 1, 'cleaning', 'system', '00000000-0000-7000-8000-000000000301', 'housekeeping', 'done', 1,
   '00000000-0000-7000-8000-00000000a001', '301 退房打掃', NULL, 30,
   now() - interval '3 hours', now() - interval '150 minutes', current_date, now() - interval '4 hours', now(), 1800, NULL,
   NULL, false, NULL, false, 0, false, false, NULL, NULL, NULL, NULL, NULL),
  ('00000000-0000-7000-8000-00000000d006', 1, 'cleaning', 'system', '00000000-0000-7000-8000-000000000401', 'housekeeping', 'inspected', 1,
   '00000000-0000-7000-8000-00000000a001', '401 退房打掃', NULL, 30,
   now() - interval '5 hours', now() - interval '4 hours', current_date, now() - interval '6 hours', now(), 1680, NULL,
   'waived', true, NULL, false, 0, false, false, NULL, NULL, NULL, NULL, NULL),
  ('00000000-0000-7000-8000-00000000d007', 1, 'cleaning', 'system', '00000000-0000-7000-8000-000000000402', 'housekeeping', 'inspected', 1,
   '00000000-0000-7000-8000-00000000a001', '402 退房打掃', NULL, 30,
   now() - interval '4 hours', now() - interval '3 hours', current_date, now() - interval '6 hours', now(), 2100, NULL,
   'passed', false, NULL, false, 1, false, false, NULL, NULL, NULL, NULL, NULL),

  ('00000000-0000-7000-8000-00000000d010', 1, 'cleaning', 'system', '00000000-0000-7000-8000-000000000502', 'housekeeping', 'pool', 1,
   NULL, '502 重做打掃', NULL, 30,
   NULL, NULL, current_date, now() - interval '12 minutes', now(), 0, NULL,
   NULL, false, NULL, false, 1, false, false, 'inspection_return', '浴室鏡面有水痕，請重做', NULL, NULL, NULL),
  ('00000000-0000-7000-8000-00000000d011', 1, 'amenity', 'guest', '00000000-0000-7000-8000-000000000405', 'housekeeping', 'pool', 1,
   NULL, '405 加毛巾 ×2', NULL, 10,
   NULL, NULL, NULL, now() - interval '5 minutes', now(), 0, NULL,
   NULL, false, NULL, false, 0, false, true, 'guest_scan', NULL, NULL, NULL, NULL),
  ('00000000-0000-7000-8000-00000000d012', 1, 'other', 'system', '00000000-0000-7000-8000-000000000900', 'housekeeping', 'pool', 1,
   NULL, '大廳垃圾桶清理', NULL, 15,
   NULL, NULL, NULL, now() - interval '20 minutes', now(), 0, NULL,
   NULL, false, NULL, false, 0, false, false, 'system', NULL, NULL, '大廳定時巡檢', NULL),
  ('00000000-0000-7000-8000-00000000d013', 1, 'repair', 'desk', '00000000-0000-7000-8000-000000000202', 'housekeeping', 'pool', 1,
   NULL, '202 浴室置物架鬆動', NULL, 20,
   NULL, NULL, NULL, now() - interval '45 minutes', now(), 0, NULL,
   NULL, false, NULL, false, 0, false, false, 'desk_forward', NULL, NULL, NULL, 'staff_reminded'),
  ('00000000-0000-7000-8000-00000000d014', 1, 'cleaning', 'system', '00000000-0000-7000-8000-000000000203', 'housekeeping', 'pool', 1,
   NULL, '203 加派打掃', NULL, 30,
   NULL, NULL, current_date, now() - interval '75 minutes', now(), 0, NULL,
   NULL, false, NULL, false, 0, false, false, 'manager_requeue', NULL, '團客提早入住，請優先處理', NULL, 'manager_notified'),
  ('00000000-0000-7000-8000-00000000d015', 1, 'amenity', 'desk', '00000000-0000-7000-8000-000000000306', 'housekeeping', 'pool', 1,
   NULL, '306 補礦泉水 ×4', NULL, 10,
   NULL, NULL, NULL, now() - interval '30 minutes', now(), 0, NULL,
   NULL, false, NULL, false, 0, false, false, 'leave_return', NULL, NULL, NULL, NULL),

  ('00000000-0000-7000-8000-00000000d020', 1, 'repair', 'staff', '00000000-0000-7000-8000-000000000306', 'engineering', 'pool', 1,
   NULL, '306 冷氣不冷', NULL, 40,
   NULL, NULL, NULL, now() - interval '8 minutes', now(), 0, NULL,
   NULL, false, NULL, false, 0, true, false, 'staff_report', NULL, NULL, NULL, NULL),
  ('00000000-0000-7000-8000-00000000d021', 1, 'repair', 'desk', '00000000-0000-7000-8000-000000000407', 'engineering', 'pool', 1,
   NULL, '407 洗手台排水不順', NULL, 30,
   NULL, NULL, NULL, now() - interval '35 minutes', now(), 0, NULL,
   NULL, false, NULL, false, 0, false, false, 'desk_forward', NULL, NULL, NULL, NULL),
  ('00000000-0000-7000-8000-00000000d022', 1, 'repair', 'staff', '00000000-0000-7000-8000-000000000508', 'engineering', 'pool', 1,
   NULL, '508 房門鎖卡住', NULL, 25,
   NULL, NULL, NULL, now() - interval '55 minutes', now(), 0, NULL,
   NULL, false, NULL, false, 0, false, false, 'staff_report', NULL, NULL, NULL, 'staff_reminded'),
  ('00000000-0000-7000-8000-00000000d023', 1, 'repair', 'staff', '00000000-0000-7000-8000-000000000302', 'engineering', 'pool', 1,
   NULL, '302 書桌椅腳鬆動', NULL, 20,
   NULL, NULL, NULL, now() - interval '15 minutes', now(), 0, NULL,
   NULL, false, NULL, false, 0, false, false, 'staff_report', NULL, NULL, NULL, NULL),
  ('00000000-0000-7000-8000-00000000d024', 1, 'repair', 'system', '00000000-0000-7000-8000-000000000900', 'engineering', 'pool', 1,
   NULL, '4 樓走廊燈管閃爍', NULL, 15,
   NULL, NULL, NULL, now() - interval '70 minutes', now(), 0, NULL,
   NULL, false, NULL, false, 0, false, false, 'system', NULL, NULL, '公區巡檢規則', 'manager_notified'),

  ('00000000-0000-7000-8000-00000000d030', 1, 'repair', 'staff', '00000000-0000-7000-8000-000000000305', 'engineering', 'accepted', 1,
   '00000000-0000-7000-8000-00000000a002', '305 冷氣滴水', NULL, 40,
   NULL, NULL, NULL, now() - interval '50 minutes', now(), 0, NULL,
   NULL, false, NULL, false, 0, true, false, NULL, NULL, NULL, NULL, NULL),
  ('00000000-0000-7000-8000-00000000d031', 1, 'repair', 'staff', '00000000-0000-7000-8000-000000000502', 'engineering', 'doing', 1,
   '00000000-0000-7000-8000-00000000a002', '502 馬桶沖水不停', NULL, 30,
   now() - interval '30 minutes', NULL, NULL, now() - interval '2 hours', now(), 0, now() - interval '25 minutes',
   NULL, false, NULL, false, 0, false, false, NULL, NULL, NULL, NULL, NULL),
  ('00000000-0000-7000-8000-00000000d032', 1, 'repair', 'staff', '00000000-0000-7000-8000-000000000301', 'engineering', 'done', 1,
   '00000000-0000-7000-8000-00000000a002', '301 門鎖電池更換', NULL, 20,
   now() - interval '4 hours', now() - interval '100 minutes', NULL, now() - interval '5 hours', now(), 2400, NULL,
   NULL, false, NULL, false, 0, false, false, NULL, NULL, NULL, NULL, NULL),
  ('00000000-0000-7000-8000-00000000d033', 1, 'repair', 'desk', '00000000-0000-7000-8000-000000000405', 'engineering', 'closed', 1,
   '00000000-0000-7000-8000-00000000a002', '405 電視遙控器失靈', NULL, 15,
   now() - interval '70 minutes', now() - interval '40 minutes', NULL, now() - interval '3 hours', now(), 1200, NULL,
   NULL, false, NULL, false, 0, false, false, NULL, NULL, NULL, NULL, NULL),
  ('00000000-0000-7000-8000-00000000d034', 1, 'repair', 'staff', '00000000-0000-7000-8000-000000000305', 'engineering', 'closed', 2,
   '00000000-0000-7000-8000-00000000a002', '305 浴室燈不亮', NULL, 15,
   now() - interval '2 hours', now() - interval '110 minutes', NULL, now() - interval '150 minutes', now(), 600, NULL,
   NULL, false, NULL, false, 0, false, false, NULL, NULL, NULL, NULL, NULL),

  ('00000000-0000-7000-8000-00000000d040', 1, 'cleaning', 'system', '00000000-0000-7000-8000-000000000403', 'housekeeping', 'doing', 1,
   '00000000-0000-7000-8000-00000000a003', '403 退房打掃', NULL, 30,
   now() - interval '15 minutes', NULL, current_date, now() - interval '2 hours', now(), 0, now() - interval '10 minutes',
   NULL, false, NULL, false, 0, false, false, NULL, NULL, NULL, NULL, NULL),
  ('00000000-0000-7000-8000-00000000d041', 1, 'cleaning', 'system', '00000000-0000-7000-8000-000000000404', 'housekeeping', 'accepted', 1,
   '00000000-0000-7000-8000-00000000a003', '404 退房打掃', NULL, 30,
   NULL, NULL, current_date, now() - interval '2 hours', now(), 0, NULL,
   NULL, false, NULL, false, 0, false, false, NULL, NULL, NULL, NULL, NULL),

  ('00000000-0000-7000-8000-00000000d042', 1, 'cleaning', 'system', '00000000-0000-7000-8000-000000000205', 'housekeeping', 'done', 1,
   '00000000-0000-7000-8000-00000000a005', '205 退房打掃', NULL, 30,
   now() - interval '1 day 3 hours', now() - interval '1 day 150 minutes', current_date - 1, now() - interval '1 day 4 hours', now() - interval '1 day', 1800, NULL,
   NULL, false, NULL, false, 0, false, false, NULL, NULL, NULL, NULL, NULL);

INSERT INTO task_events (tenant_id, task_id, seq, type, actor_role, actor_id, payload, created_at)
SELECT 1, id, 1, 'created', 'system', NULL,
       jsonb_build_object('type', type, 'source', source, 'room_id', room_id::text, 'dept', dept,
                          'state', 'pool', 'title', title, 'est_min', est_min,
                          'work_date', work_date),
       created_at
FROM tasks WHERE tenant_id = 1;

INSERT INTO task_events (tenant_id, task_id, seq, type, actor_role, actor_id, payload, created_at) VALUES
  (1, '00000000-0000-7000-8000-00000000d034', 2, 'completed', 'engineering', '00000000-0000-7000-8000-00000000a002',
   '{"resolutionType": "no_action_needed"}'::jsonb, now() - interval '110 minutes');

INSERT INTO repair_tickets (task_id, tenant_id, ticket_no, room_id, category, description, reporter_id, source_task_id, resolution_note, created_at, is_urgent) VALUES
  ('00000000-0000-7000-8000-00000000d020', 1, 'W-0101', '00000000-0000-7000-8000-000000000306', 'air_conditioning',    '房客反映冷氣只出風不冷，設到 18 度也一樣',   '00000000-0000-7000-8000-00000000a001', NULL, NULL, now() - interval '8 minutes',  true),
  ('00000000-0000-7000-8000-00000000d021', 1, 'W-0102', '00000000-0000-7000-8000-000000000407', 'plumbing_electrical', '洗手台排水很慢，積水退不掉',                 '00000000-0000-7000-8000-00000000a004', NULL, NULL, now() - interval '35 minutes', false),
  ('00000000-0000-7000-8000-00000000d022', 1, 'W-0103', '00000000-0000-7000-8000-000000000508', 'door_lock',           '房卡刷了會亮綠燈但把手轉不動',               '00000000-0000-7000-8000-00000000a005', NULL, NULL, now() - interval '55 minutes', false),
  ('00000000-0000-7000-8000-00000000d023', 1, 'W-0104', '00000000-0000-7000-8000-000000000302', 'furniture_equipment', '書桌椅右後腳鬆動，坐下去會晃',               '00000000-0000-7000-8000-00000000a001', '00000000-0000-7000-8000-00000000d001', NULL, now() - interval '15 minutes', false),
  ('00000000-0000-7000-8000-00000000d024', 1, 'W-0105', '00000000-0000-7000-8000-000000000900', 'other',               '4 樓走廊中段燈管一直閃',                     '00000000-0000-7000-8000-00000000a005', NULL, NULL, now() - interval '70 minutes', false),
  ('00000000-0000-7000-8000-00000000d030', 1, 'W-0106', '00000000-0000-7000-8000-000000000305', 'air_conditioning',    '出風口會滴水到地毯，今天有 VIP 入住',        '00000000-0000-7000-8000-00000000a005', NULL, NULL, now() - interval '50 minutes', true),
  ('00000000-0000-7000-8000-00000000d031', 1, 'W-0107', '00000000-0000-7000-8000-000000000502', 'plumbing_electrical', '馬桶水箱一直補水停不下來',                   '00000000-0000-7000-8000-00000000a001', NULL, NULL, now() - interval '2 hours',    false),
  ('00000000-0000-7000-8000-00000000d032', 1, 'W-0108', '00000000-0000-7000-8000-000000000301', 'door_lock',           '門鎖低電量警示',                             '00000000-0000-7000-8000-00000000a001', NULL, '更換四顆三號電池，刷卡測試三次正常', now() - interval '5 hours', false),
  ('00000000-0000-7000-8000-00000000d033', 1, 'W-0109', '00000000-0000-7000-8000-000000000405', 'furniture_equipment', '電視遙控器按了沒反應',                       '00000000-0000-7000-8000-00000000a004', NULL, '更換遙控器與電池，頻道測試正常',     now() - interval '3 hours', false),
  ('00000000-0000-7000-8000-00000000d034', 1, 'W-0110', '00000000-0000-7000-8000-000000000305', 'plumbing_electrical', '浴室燈按了不亮',                             '00000000-0000-7000-8000-00000000a005', NULL, '燈正常，開關在門邊',                 now() - interval '150 minutes', false);

INSERT INTO repair_ticket_counters (tenant_id, last_no) VALUES (1, 110);

UPDATE repair_tickets
   SET resolution_type = 'fixed'
 WHERE tenant_id = 1 AND ticket_no IN ('W-0108', 'W-0109');
UPDATE repair_tickets
   SET resolution_type = 'no_action_needed'
 WHERE tenant_id = 1 AND ticket_no = 'W-0110';

UPDATE repair_tickets
   SET verified_by = '00000000-0000-7000-8000-00000000a003', verified_at = now() - interval '30 minutes'
 WHERE tenant_id = 1 AND ticket_no = 'W-0109';

INSERT INTO repair_photos (id, tenant_id, ticket_task_id, purpose, status, storage_ref, taken_at, uploaded_by, created_at, uploaded_at) VALUES
  ('00000000-0000-7000-8000-00000000e601', 1, '00000000-0000-7000-8000-00000000d032', 'completion', 'uploaded',
   'upload:00000000-0000-7000-8000-00000000e601', now() - interval '105 minutes', '00000000-0000-7000-8000-00000000a002',
   now() - interval '105 minutes', now() - interval '104 minutes');

INSERT INTO task_sop_checks (id, tenant_id, task_id, item_key, label, checked, skipped, checked_by, checked_at) VALUES
  ('00000000-0000-7000-8000-00000000e201', 1, '00000000-0000-7000-8000-00000000d001', 'bed_sheets', '更換床單被套', true,  false, '00000000-0000-7000-8000-00000000a001', now() - interval '15 minutes'),
  ('00000000-0000-7000-8000-00000000e202', 1, '00000000-0000-7000-8000-00000000d001', 'towels',     '補齊毛巾浴巾', true,  false, '00000000-0000-7000-8000-00000000a001', now() - interval '12 minutes'),
  ('00000000-0000-7000-8000-00000000e203', 1, '00000000-0000-7000-8000-00000000d001', 'amenities',  '補齊備品',     true,  false, '00000000-0000-7000-8000-00000000a001', now() - interval '8 minutes'),
  ('00000000-0000-7000-8000-00000000e204', 1, '00000000-0000-7000-8000-00000000d001', 'bathroom',   '浴室清潔',     false, true,  '00000000-0000-7000-8000-00000000a001', now() - interval '5 minutes');

INSERT INTO handovers (id, tenant_id, dept, shift, work_date, snapshot, generated_by, created_at) VALUES
  ('00000000-0000-7000-8000-00000000e301', 1, 'housekeeping', 'morning', current_date,
   '[{"target": "302", "content": "房客說浴缸排水慢，順便看一下", "authorName": "阿芳"},
     {"target": "302", "content": "床頭櫃抽屜有住客留下的充電線，收到櫃檯", "authorName": "林櫃檯"},
     {"target": "303", "content": "窗簾軌道有異音，先不用理，工務排了", "authorName": "阿芳"}]'::jsonb,
   'system', now() - interval '4 hours'),
  ('00000000-0000-7000-8000-00000000e302', 1, 'engineering', 'morning', current_date,
   '[{"target": "502", "content": "水箱零件昨天到貨，放在工務倉第二層", "authorName": "阿義"}]'::jsonb,
   'system', now() - interval '4 hours');

INSERT INTO room_memory (id, tenant_id, room_id, kind, summary, occurred_at) VALUES
  ('00000000-0000-7000-8000-00000000e401', 1, '00000000-0000-7000-8000-000000000302', 'repair', '冷氣異音：更換室內機風扇軸承，已觀察兩週無異常', now() - interval '21 days'),
  ('00000000-0000-7000-8000-00000000e402', 1, '00000000-0000-7000-8000-000000000302', 'repair', '浴室排水慢：清理毛髮濾網，建議每月定期清', now() - interval '9 days'),
  ('00000000-0000-7000-8000-00000000e403', 1, '00000000-0000-7000-8000-000000000305', 'repair', '冷氣滴水：排水管坡度不足，已墊高固定', now() - interval '35 days');

INSERT INTO notifications (id, tenant_id, recipient_staff_id, title, body, target, event, created_at, read_at) VALUES
  ('00000000-0000-7000-8000-00000000e501', 1, '00000000-0000-7000-8000-00000000a001', '306 冷氣不冷已判定為急件', '住客今天入住，請優先處理',
   '{"kind": "pool", "dept": "engineering", "highlightTaskId": "00000000-0000-7000-8000-00000000d020"}'::jsonb, 'repair_urgent',             now() - interval '8 minutes',  NULL),
  ('00000000-0000-7000-8000-00000000e502', 1, '00000000-0000-7000-8000-00000000a001', '202 浴室置物架鬆動還沒有人接', '進池 45 分鐘了',
   '{"kind": "pool", "dept": "housekeeping", "highlightTaskId": "00000000-0000-7000-8000-00000000d013"}'::jsonb, 'unclaimed_reminder_staff',  now() - interval '15 minutes', NULL),
  ('00000000-0000-7000-8000-00000000e503', 1, '00000000-0000-7000-8000-00000000a001', '405 加毛巾 ×2 進了待認領', '住客掃碼送出的需求',
   '{"kind": "pool", "dept": "housekeeping", "highlightTaskId": "00000000-0000-7000-8000-00000000d011"}'::jsonb, 'pool_new_request',          now() - interval '5 minutes',  NULL),
  ('00000000-0000-7000-8000-00000000e504', 1, '00000000-0000-7000-8000-00000000a001', '402 查房通過', '辛苦了，這間一次過',
   '{"kind": "task", "taskId": "00000000-0000-7000-8000-00000000d007"}'::jsonb, 'inspection_passed',                                         now() - interval '3 hours',    now() - interval '3 hours'),
  ('00000000-0000-7000-8000-00000000e505', 1, '00000000-0000-7000-8000-00000000a001', '王主管回覆了您的提問', '「冷氣遙控器鎖定怎麼解」已有回覆',
   '{"kind": "task", "taskId": "00000000-0000-7000-8000-00000000d001"}'::jsonb, 'kb_reply',                                                  now() - interval '50 minutes', now() - interval '45 minutes'),
  ('00000000-0000-7000-8000-00000000e506', 1, '00000000-0000-7000-8000-00000000a001', '206 加備品已被住客取消', '不用再過去了',
   '{"kind": "home"}'::jsonb, 'task_cancelled',                                                                                             now() - interval '5 hours',    now() - interval '5 hours'),

  ('00000000-0000-7000-8000-00000000e511', 1, '00000000-0000-7000-8000-00000000a002', '305 冷氣滴水是急件', 'VIP 今天 15:00 入住',
   '{"kind": "ticket", "taskId": "00000000-0000-7000-8000-00000000d030"}'::jsonb, 'repair_urgent',                                             now() - interval '50 minutes', NULL),
  ('00000000-0000-7000-8000-00000000e512', 1, '00000000-0000-7000-8000-00000000a002', '306 冷氣不冷進了工務待認領', '房務報修',
   '{"kind": "pool", "dept": "engineering", "highlightTaskId": "00000000-0000-7000-8000-00000000d020"}'::jsonb, 'pool_new_request',          now() - interval '8 minutes',  NULL),
  ('00000000-0000-7000-8000-00000000e513', 1, '00000000-0000-7000-8000-00000000a002', '508 房門鎖卡住還沒有人接', '進池 55 分鐘了',
   '{"kind": "pool", "dept": "engineering", "highlightTaskId": "00000000-0000-7000-8000-00000000d022"}'::jsonb, 'unclaimed_reminder_staff',  now() - interval '25 minutes', NULL),
  ('00000000-0000-7000-8000-00000000e514', 1, '00000000-0000-7000-8000-00000000a002', 'W-0109 405 電視遙控器已完修', '主管已驗收',
   '{"kind": "ticket", "taskId": "00000000-0000-7000-8000-00000000d033"}'::jsonb, 'repair_completed',                                          now() - interval '40 minutes', now() - interval '35 minutes'),
  ('00000000-0000-7000-8000-00000000e515', 1, '00000000-0000-7000-8000-00000000a002', '502 馬桶沖水不停轉到您名下', '阿義今天請假',
   '{"kind": "ticket", "taskId": "00000000-0000-7000-8000-00000000d031"}'::jsonb, 'reassigned_to_me',                                          now() - interval '2 hours',    now() - interval '2 hours'),
  ('00000000-0000-7000-8000-00000000e516', 1, '00000000-0000-7000-8000-00000000a002', '407 洗手台的單已改由他人處理', '純告知，不用動作',
   '{"kind": "home"}'::jsonb, 'reassigned_away',                                                                                            now() - interval '4 hours',    now() - interval '4 hours'),

  ('00000000-0000-7000-8000-00000000e521', 1, '00000000-0000-7000-8000-00000000a003', '203 加派打掃超過 1 小時沒人接', '要不要指派給誰',
   '{"kind": "pool", "dept": "housekeeping", "highlightTaskId": "00000000-0000-7000-8000-00000000d014"}'::jsonb, 'unclaimed_reminder_manager', now() - interval '15 minutes', NULL),
  ('00000000-0000-7000-8000-00000000e522', 1, '00000000-0000-7000-8000-00000000a003', '306 冷氣不冷已判定為急件', '系統依今日入住判定',
   '{"kind": "pool", "dept": "engineering", "highlightTaskId": "00000000-0000-7000-8000-00000000d020"}'::jsonb, 'repair_urgent',              now() - interval '8 minutes',  NULL),
  ('00000000-0000-7000-8000-00000000e523', 1, '00000000-0000-7000-8000-00000000a003', '502 查房未過，已退回重做', '浴室鏡面有水痕',
   '{"kind": "pool", "dept": "housekeeping", "highlightTaskId": "00000000-0000-7000-8000-00000000d010"}'::jsonb, 'inspection_failed',         now() - interval '12 minutes', now() - interval '10 minutes'),
  ('00000000-0000-7000-8000-00000000e524', 1, '00000000-0000-7000-8000-00000000a003', '有人覺得一則回答沒有幫助', '「製冰機跳電怎麼處理」',
   '{"kind": "home"}'::jsonb, 'kb_unhelpful',                                                                                                now() - interval '90 minutes', now() - interval '80 minutes'),
  ('00000000-0000-7000-8000-00000000e525', 1, '00000000-0000-7000-8000-00000000a003', '阿芳班末還有 2 單沒開始，已退回待認領', '交給下一班',
   '{"kind": "pool", "dept": "housekeeping"}'::jsonb, 'shift_end_returned',                                                                  now() - interval '4 hours',    now() - interval '4 hours');

COMMIT;
SQL

PGPASSWORD="$DB_PASSWORD" psql \
  --host "$DB_HOST" --port "$DB_PORT" --username "$DB_USER" --dbname "$DB_NAME" \
  --quiet --no-psqlrc --set ON_ERROR_STOP=1 --tuples-only <<'SQL'
\echo '[seed-demo] 種好了。清點：'
SELECT '  員工 ' || count(*) || ' 位' FROM staff_accounts WHERE tenant_id = 1;
SELECT '  房間 ' || count(*) || ' 間' FROM rooms WHERE tenant_id = 1;
SELECT '  任務 ' || count(*) || ' 張（房務待認領 '
       || count(*) FILTER (WHERE state = 'pool' AND dept = 'housekeeping') || '、工務待認領 '
       || count(*) FILTER (WHERE state = 'pool' AND dept = 'engineering') || '）'
  FROM tasks WHERE tenant_id = 1;
SELECT '  工務單 ' || count(*) || ' 張' FROM repair_tickets WHERE tenant_id = 1;
SELECT '  主管首頁：王主管 待查房 '
       || (SELECT count(*) FROM tasks WHERE tenant_id = 1 AND type = 'cleaning' AND state = 'done'
            AND assignee_id IS DISTINCT FROM '00000000-0000-7000-8000-00000000a003')
       || '、待驗收 ' || v.n || '；陳主管 待驗收 ' || v.n
  FROM (SELECT count(*) AS n FROM tasks t JOIN repair_tickets r ON r.task_id = t.id AND r.tenant_id = t.tenant_id
         WHERE t.tenant_id = 1 AND t.type = 'repair' AND t.state = 'done'
           AND r.resolution_type IS DISTINCT FROM 'no_action_needed') v;
SELECT '  工務單已結案 ' || count(*) FILTER (WHERE r.resolution_type IS DISTINCT FROM 'no_action_needed')
       || ' 張、不需處理結案 ' || count(*) FILTER (WHERE r.resolution_type = 'no_action_needed') || ' 張'
  FROM tasks t JOIN repair_tickets r ON r.task_id = t.id AND r.tenant_id = t.tenant_id
 WHERE t.tenant_id = 1 AND t.state = 'closed';
SELECT '  通知 ' || count(*) || ' 則（未讀 ' || count(*) FILTER (WHERE read_at IS NULL) || '）'
  FROM notifications WHERE tenant_id = 1;
SQL

cat <<'EOF'
[seed-demo] 可以登入的帳號（密碼都是 demo1234）：
  hk1  小美    房務員工
  mt1  阿強    工務員工
  mg1  王主管  房務主管
  mg2  陳主管  工務主管
[seed-demo] 跑過 app/bdd 的 BDD 之後這份資料會被清掉，想再玩就再跑一次這支腳本。
EOF
