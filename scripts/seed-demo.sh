#!/usr/bin/env bash
#
# 給人手動試玩 App 用的種子資料。
#
# 用途
#   把「示範飯店」這個租戶的業務資料整組重種，讓人可以用固定帳密登入 App，
#   每個畫面都看得到東西：進度環有數字、首頁有進行中的單、六種狀態 chip 的
#   顏色都出現、待認領分類磚不是零、通知中心有多種顏色的分類圖示、工務三格
#   有數字、任務頁有 SOP 項與同房工務單與交辦。這份資料是給人看的，不是給
#   測試看的——app/bdd 的 BDD 不讀它，它也不碰 app/bdd 的任何東西。
#
# 怎麼跑
#   ./scripts/seed-demo.sh
#
#   前置只有一個：docker compose 的 postgres 要起著（對外埠 5433）。後端不必
#   起，這支腳本只寫資料庫。不設任何環境變數時會連
#   postgresql://tms@127.0.0.1:5433/tms；要連別台或別個資料庫就覆寫
#   DEMO_DB_HOST／DEMO_DB_PORT／DEMO_DB_NAME／DEMO_DB_USER／DEMO_DB_PASSWORD。
#
#   連線帳號一定要用管理帳號 tms，不能用後端自己用的 tms_app：業務表都有
#   row level security，tms_app 沒有先設 app.tenant_id 的話一列都讀不到、也
#   寫不進去。
#
# 帳密（密碼全部都是 demo1234）
#   hk1   小美     房務員工，登入後進房務首頁
#   mt1   阿強     工務員工，登入後進工務首頁
#   mg1   王主管   主管，帳號沒有部門，登入後進房務首頁
#   另外種了阿芳（hk2）、阿義（mt2）、林櫃檯（fd1）三個配角帳號，密碼同上，
#   用來當交辦的作者與工務單的報修人，不是給人登入的主角。
#
# 會種出什麼
#   租戶「示範飯店」（tenant_id = 1）底下：6 位員工、18 間房、今天的排房計畫、
#   打掃與查房 SOP 項、小美名下 7 張涵蓋六種狀態的今日任務、房務待認領 6 張、
#   工務待認領 5 張、阿強手上 2 張加今天已完修 2 張、王主管名下 2 張、三位
#   主角各 5–6 則通知（各留幾則未讀撐出分頁列徽章），以及 302 房那張進行中的
#   單的配套（SOP 勾記、同房工務單、房務交班的交辦）。
#
# 重複執行
#   腳本開頭會先刪掉 tenant_id = 1 的全部業務資料再重種，所以跑幾次結果都一樣，
#   不會越跑越多。它只刪租戶 1，不會動到 app/bdd 用的「測試飯店」。
#   app/bdd 的 BDD 每個情境開場會清掉全部業務表，跑過 BDD 之後這份示範資料就
#   沒了，想再玩就再跑一次。
#   所有時間都以執行當下為基準，「今天的單」就是執行當天，所以隔天要玩也要再跑。
#
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

-- tenants 是唯一不會被 BDD 清掉的業務表，所以這裡補建而不是重建：租戶 1 多半
-- 已經在（V2 的開發種子建的），只有在全新的資料庫上才真的插進去。
INSERT INTO tenants (id, name, created_at) OVERRIDING SYSTEM VALUE
VALUES (1, '示範飯店', now())
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;
DO $$ BEGIN PERFORM setval(pg_get_serial_sequence('tenants', 'id'), GREATEST(1, (SELECT max(id) FROM tenants)), true); END $$;

-- 先把上一次種的那一份整組刪掉，這支腳本才能重複執行而不是越跑越多。順序是
-- 由被參照的葉子往根走，中間任何一張漏掉都會被外鍵擋下來。只刪租戶 1，
-- app/bdd 用的「測試飯店」不受影響。
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

-- 員工帳號。password_hash 是 demo1234 的 BCrypt 雜湊（$2y$10$ 前綴，Spring 的
-- BCryptPasswordEncoder 吃得下）。
-- 後端每次請求都拿資料庫這一列的 role 與 dept 判權限，不信任權杖裡的值，所以
-- 房務員工的 role 與 dept 兩欄都要是 housekeeping，少一邊待認領就會回 422。
-- must_change_password 留 false（欄位預設），否則登入後每一支 API 都會回 403
-- 要求先改密碼，畫面會整片空白。
INSERT INTO staff_accounts (id, tenant_id, name, login_id, password_hash, role, dept, active, status, created_at, updated_at) VALUES
  ('00000000-0000-7000-8000-00000000a001', 1, '小美',   'hk1', '$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW', 'housekeeping', 'housekeeping', true, 'active', now(), now()),
  ('00000000-0000-7000-8000-00000000a002', 1, '阿強',   'mt1', '$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW', 'engineering',  'engineering',  true, 'active', now(), now()),
  ('00000000-0000-7000-8000-00000000a003', 1, '王主管', 'mg1', '$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW', 'manager',      NULL,           true, 'active', now(), now()),
  ('00000000-0000-7000-8000-00000000a004', 1, '林櫃檯', 'fd1', '$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW', 'frontdesk',    'frontdesk',    true, 'active', now(), now()),
  ('00000000-0000-7000-8000-00000000a005', 1, '阿芳',   'hk2', '$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW', 'housekeeping', 'housekeeping', true, 'active', now(), now()),
  ('00000000-0000-7000-8000-00000000a006', 1, '阿義',   'mt2', '$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW', 'engineering',  'engineering',  true, 'active', now(), now());

-- 房間。每一張單都 JOIN 房間取房號，房間不在單就整張從畫面上消失。
-- 最後那間「公共區域」是佔位房：tasks.room_id 不可為空，沒有房號的單（大廳、
-- 走廊）一律掛在它身上，欄位與產品碼 AutomationRuleService 建的那間同形狀。
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

-- 租戶設定。guest_info_pages 與 request_menu 撐住客掃碼那條路；shift_times 是
-- 班次設定，班末掃描會拿它把排房計畫上的班次翻成結束時刻。
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

-- 打掃 SOP 項是任務頁步驟條的全部內容，查房項目則是查房流程要勾的那一份。
-- 兩類共用這張表，用 applies_to 分。
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

-- 今天的排房計畫。首頁問候列的班別與樓層只認 status = 'published' 的那一份，
-- 草稿讀不到；plan_rooms 同時也是待認領單卡「同樓層」標示的依據。
-- shift 刻意填「早班」這個中文字串：班末掃描會先把它當 HH:mm 解析、再去
-- shift_times 設定裡找同名的班次，兩邊都對不上就整個跳過這位員工。填成
-- '17:00' 或 'morning' 的話，時鐘一過那個點，掃描就會把員工名下待開始與本人
-- 延後的單整批退回待認領，示範資料會自己消失。
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

-- 今天入住的訂房。急件判定只看這張表（同一間房今天有人入住就判急件），
-- 查一下的住客資訊也是從這裡來。
INSERT INTO reservations_cache (id, tenant_id, reservation_ref, room_id, guest_name, lang, attrs, checkin_at, checkout_at, source, synced_at, superseded_at) VALUES
  ('00000000-0000-7000-8000-00000000b001', 1, 'PMS-88321', '00000000-0000-7000-8000-000000000305', NULL, NULL, '{"vip": true}', date_trunc('day', now()) + interval '15 hours', date_trunc('day', now()) + interval '35 hours', 'pms', now(), NULL),
  ('00000000-0000-7000-8000-00000000b002', 1, 'PMS-88347', '00000000-0000-7000-8000-000000000302', NULL, NULL, '{}',             date_trunc('day', now()) + interval '16 hours', date_trunc('day', now()) + interval '35 hours', 'pms', now(), NULL);

-- 下面這一整段 INSERT 是全部的任務，分五群：小美名下的今日任務、房務待認領、
-- 工務待認領、阿強名下的工務單、王主管名下的房務單。
--
-- 第一群是小美（hk1）的今日任務，七張單把六種狀態 chip 湊齊：進行中、待開始、
-- 已延後、已中斷、待查房、完成·免查房、查房通過。進度環數的是今天、打掃類、
-- 掛在她名下的單，分子是其中做完的，所以這七張裡的打掃單決定環上的數字。
-- 進行中那張的已做時間是把 segment_started_at 往前推出來的——後端拿它跟現在
-- 相減算計時，直接寫 worked_sec 是停住的單才用的欄位。
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
  -- 「完成 · 免查房」與「查房通過」在狀態欄上都是 inspected，差別只在 inspection
  -- 是 waived 還是 passed，今日進度的「查房通過」那一格也只數 passed 的。
  ('00000000-0000-7000-8000-00000000d006', 1, 'cleaning', 'system', '00000000-0000-7000-8000-000000000401', 'housekeeping', 'inspected', 1,
   '00000000-0000-7000-8000-00000000a001', '401 退房打掃', NULL, 30,
   now() - interval '5 hours', now() - interval '4 hours', current_date, now() - interval '6 hours', now(), 1680, NULL,
   'waived', true, NULL, false, 0, false, false, NULL, NULL, NULL, NULL, NULL),
  ('00000000-0000-7000-8000-00000000d007', 1, 'cleaning', 'system', '00000000-0000-7000-8000-000000000402', 'housekeeping', 'inspected', 1,
   '00000000-0000-7000-8000-00000000a001', '402 退房打掃', NULL, 30,
   now() - interval '4 hours', now() - interval '3 hours', current_date, now() - interval '6 hours', now(), 2100, NULL,
   'passed', false, NULL, false, 1, false, false, NULL, NULL, NULL, NULL, NULL),

  -- 第二群是房務待認領池。四種分類（打掃／備品／修繕／其他）都放一張，分類磚
  -- 的數字才不是零；來源與未接提醒階段也各鋪開一遍，單卡上那幾顆彩色標籤
  -- （住客掃碼、櫃檯轉單、主管退回、請假退回、查房退回、自動化規則、未接超時）
  -- 才有東西可畫。待認領的單沒有負責人，assignee_id 一律留空。
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
   NULL, '202 窗簾掛勾鬆脫', NULL, 20,
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

  -- 第三群是工務待認領池。工務池的分類磚另外數急件與來源，所以急件放一張、
  -- 來源是櫃檯轉單的放一張；兩種未接提醒階段也各一張，排序會把它們頂到最前面。
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
  -- 這一張的房號刻意跟小美進行中的 302 同房：任務頁的「同房工務單」就是靠
  -- 房號去查的，沒有這張那一區會是空的。
  ('00000000-0000-7000-8000-00000000d023', 1, 'repair', 'staff', '00000000-0000-7000-8000-000000000302', 'engineering', 'pool', 1,
   NULL, '302 書桌椅腳鬆動', NULL, 20,
   NULL, NULL, NULL, now() - interval '15 minutes', now(), 0, NULL,
   NULL, false, NULL, false, 0, false, false, 'staff_report', NULL, NULL, NULL, NULL),
  ('00000000-0000-7000-8000-00000000d024', 1, 'repair', 'system', '00000000-0000-7000-8000-000000000900', 'engineering', 'pool', 1,
   NULL, '4 樓走廊燈管閃爍', NULL, 15,
   NULL, NULL, NULL, now() - interval '70 minutes', now(), 0, NULL,
   NULL, false, NULL, false, 0, false, false, 'system', NULL, NULL, '公區巡檢規則', 'manager_notified'),

  -- 第四群是阿強（mt1）的工務單。工務首頁三格的算法是：手上＝待開始加進行中、
  -- 今天已完修＝今天完修的、急件＝手上那些裡標急件的，所以這四張各占一格。
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
  ('00000000-0000-7000-8000-00000000d033', 1, 'repair', 'desk', '00000000-0000-7000-8000-000000000405', 'engineering', 'done', 1,
   '00000000-0000-7000-8000-00000000a002', '405 電視遙控器失靈', NULL, 15,
   now() - interval '70 minutes', now() - interval '40 minutes', NULL, now() - interval '3 hours', now(), 1200, NULL,
   NULL, false, NULL, false, 0, false, false, NULL, NULL, NULL, NULL, NULL),

  -- 第五群是王主管（mg1）名下的兩張房務單。主管帳號沒有部門，登入後進的是房務
  -- 首頁，而首頁只列自己名下的單——不給他兩張，主管一登入就是一片空白。
  ('00000000-0000-7000-8000-00000000d040', 1, 'cleaning', 'system', '00000000-0000-7000-8000-000000000403', 'housekeeping', 'doing', 1,
   '00000000-0000-7000-8000-00000000a003', '403 退房打掃', NULL, 30,
   now() - interval '15 minutes', NULL, current_date, now() - interval '2 hours', now(), 0, now() - interval '10 minutes',
   NULL, false, NULL, false, 0, false, false, NULL, NULL, NULL, NULL, NULL),
  ('00000000-0000-7000-8000-00000000d041', 1, 'cleaning', 'system', '00000000-0000-7000-8000-000000000404', 'housekeeping', 'accepted', 1,
   '00000000-0000-7000-8000-00000000a003', '404 退房打掃', NULL, 30,
   NULL, NULL, current_date, now() - interval '2 hours', now(), 0, NULL,
   NULL, false, NULL, false, 0, false, false, NULL, NULL, NULL, NULL, NULL);

-- 每張單補第一筆 created 事件。待認領單卡的「等了幾分鐘」是從最後一筆 created
-- 或 returned_to_pool 事件算起的，沒有事件就退回用 tasks.created_at，所以這一段
-- 其實是讓歷程頁有東西看。
INSERT INTO task_events (tenant_id, task_id, seq, type, actor_role, actor_id, payload, created_at)
SELECT 1, id, 1, 'created', 'system', NULL,
       jsonb_build_object('type', type, 'source', source, 'room_id', room_id::text, 'dept', dept,
                          'state', 'pool', 'title', title, 'est_min', est_min,
                          'work_date', work_date),
       created_at
FROM tasks WHERE tenant_id = 1;

-- 工務單延伸列。每一張修繕任務都要有一列，單號、分類、報修人、急件旗標都在
-- 這裡；少了它，工務首頁的單列沒有單號、任務頁的同房工務單也查不到。
-- room_id 要跟任務的房間一致，同房工務單是用房號查的。
-- 單號格式是 W-%04d，與產品自己取號的格式同一套。
INSERT INTO repair_tickets (task_id, tenant_id, ticket_no, room_id, category, description, reporter_id, source_task_id, resolution_note, created_at, is_urgent) VALUES
  ('00000000-0000-7000-8000-00000000d020', 1, 'W-0101', '00000000-0000-7000-8000-000000000306', 'air_conditioning',    '房客反映冷氣只出風不冷，設到 18 度也一樣',   '00000000-0000-7000-8000-00000000a001', NULL, NULL, now() - interval '8 minutes',  true),
  ('00000000-0000-7000-8000-00000000d021', 1, 'W-0102', '00000000-0000-7000-8000-000000000407', 'plumbing_electrical', '洗手台排水很慢，積水退不掉',                 '00000000-0000-7000-8000-00000000a004', NULL, NULL, now() - interval '35 minutes', false),
  ('00000000-0000-7000-8000-00000000d022', 1, 'W-0103', '00000000-0000-7000-8000-000000000508', 'door_lock',           '房卡刷了會亮綠燈但把手轉不動',               '00000000-0000-7000-8000-00000000a005', NULL, NULL, now() - interval '55 minutes', false),
  ('00000000-0000-7000-8000-00000000d023', 1, 'W-0104', '00000000-0000-7000-8000-000000000302', 'furniture_equipment', '書桌椅右後腳鬆動，坐下去會晃',               '00000000-0000-7000-8000-00000000a001', '00000000-0000-7000-8000-00000000d001', NULL, now() - interval '15 minutes', false),
  ('00000000-0000-7000-8000-00000000d024', 1, 'W-0105', '00000000-0000-7000-8000-000000000900', 'other',               '4 樓走廊中段燈管一直閃',                     '00000000-0000-7000-8000-00000000a005', NULL, NULL, now() - interval '70 minutes', false),
  ('00000000-0000-7000-8000-00000000d030', 1, 'W-0106', '00000000-0000-7000-8000-000000000305', 'air_conditioning',    '出風口會滴水到地毯，今天有 VIP 入住',        '00000000-0000-7000-8000-00000000a005', NULL, NULL, now() - interval '50 minutes', true),
  ('00000000-0000-7000-8000-00000000d031', 1, 'W-0107', '00000000-0000-7000-8000-000000000502', 'plumbing_electrical', '馬桶水箱一直補水停不下來',                   '00000000-0000-7000-8000-00000000a001', NULL, NULL, now() - interval '2 hours',    false),
  ('00000000-0000-7000-8000-00000000d032', 1, 'W-0108', '00000000-0000-7000-8000-000000000301', 'door_lock',           '門鎖低電量警示',                             '00000000-0000-7000-8000-00000000a001', NULL, '更換四顆三號電池，刷卡測試三次正常', now() - interval '5 hours', false),
  ('00000000-0000-7000-8000-00000000d033', 1, 'W-0109', '00000000-0000-7000-8000-000000000405', 'furniture_equipment', '電視遙控器按了沒反應',                       '00000000-0000-7000-8000-00000000a004', NULL, '更換遙控器與電池，頻道測試正常',     now() - interval '3 hours', false);

-- 取號計數器撥到最後一號，在 App 裡開的新工務單才不會跟上面這幾張撞號。
INSERT INTO repair_ticket_counters (tenant_id, last_no) VALUES (1, 109);

-- 其中一張完修單標成主管已驗收，驗收狀態才不是一片空白。
UPDATE repair_tickets
   SET verified_by = '00000000-0000-7000-8000-00000000a003', verified_at = now() - interval '30 minutes'
 WHERE tenant_id = 1 AND ticket_no = 'W-0109';

-- 302 那張進行中的單的 SOP 勾記：三項已勾、一項跳過、兩項還沒做，步驟條才有
-- 進度可看。勾記與 SOP 項是用 item_key 對起來的，值要跟上面 sop_items 一致。
INSERT INTO task_sop_checks (id, tenant_id, task_id, item_key, label, checked, skipped, checked_by, checked_at) VALUES
  ('00000000-0000-7000-8000-00000000e201', 1, '00000000-0000-7000-8000-00000000d001', 'bed_sheets', '更換床單被套', true,  false, '00000000-0000-7000-8000-00000000a001', now() - interval '15 minutes'),
  ('00000000-0000-7000-8000-00000000e202', 1, '00000000-0000-7000-8000-00000000d001', 'towels',     '補齊毛巾浴巾', true,  false, '00000000-0000-7000-8000-00000000a001', now() - interval '12 minutes'),
  ('00000000-0000-7000-8000-00000000e203', 1, '00000000-0000-7000-8000-00000000d001', 'amenities',  '補齊備品',     true,  false, '00000000-0000-7000-8000-00000000a001', now() - interval '8 minutes'),
  ('00000000-0000-7000-8000-00000000e204', 1, '00000000-0000-7000-8000-00000000d001', 'bathroom',   '浴室清潔',     false, true,  '00000000-0000-7000-8000-00000000a001', now() - interval '5 minutes');

-- 任務頁的「交辦」是從同部門交班單裡挑出對象等於這間房房號的項目併出來的，
-- 所以 dept 要跟任務同部門、target 要是房號字串，兩者任一對不上就一則也看不到。
INSERT INTO handovers (id, tenant_id, dept, shift, work_date, snapshot, generated_by, created_at) VALUES
  ('00000000-0000-7000-8000-00000000e301', 1, 'housekeeping', 'morning', current_date,
   '[{"target": "302", "content": "房客說浴缸排水慢，順便看一下", "authorName": "阿芳"},
     {"target": "302", "content": "床頭櫃抽屜有住客留下的充電線，收到櫃檯", "authorName": "林櫃檯"},
     {"target": "303", "content": "窗簾軌道有異音，先不用理，工務排了", "authorName": "阿芳"}]'::jsonb,
   'system', now() - interval '4 hours'),
  ('00000000-0000-7000-8000-00000000e302', 1, 'engineering', 'morning', current_date,
   '[{"target": "502", "content": "水箱零件昨天到貨，放在工務倉第二層", "authorName": "阿義"}]'::jsonb,
   'system', now() - interval '4 hours');

-- 房間往事是「查一下」回答時引用的出處，沒有這幾列出處卡會是空的。
INSERT INTO room_memory (id, tenant_id, room_id, kind, summary, occurred_at) VALUES
  ('00000000-0000-7000-8000-00000000e401', 1, '00000000-0000-7000-8000-000000000302', 'repair', '冷氣異音：更換室內機風扇軸承，已觀察兩週無異常', now() - interval '21 days'),
  ('00000000-0000-7000-8000-00000000e402', 1, '00000000-0000-7000-8000-000000000302', 'repair', '浴室排水慢：清理毛髮濾網，建議每月定期清', now() - interval '9 days'),
  ('00000000-0000-7000-8000-00000000e403', 1, '00000000-0000-7000-8000-000000000305', 'repair', '冷氣滴水：排水管坡度不足，已墊高固定', now() - interval '35 days');

-- 通知中心。event 這一欄決定通知左邊那顆圖示的顏色：repair_urgent 是急件紅、
-- unclaimed_reminder_staff 與 unclaimed_reminder_manager 與
-- shift_end_still_interrupted 是超時橘、inspection_passed 與 repair_completed
-- 是完成綠、pool_new_request 與 reassigned_to_me 與 leave_takeover 與
-- inspection_failed 與 spot_check_failed 是新單藍、kb_reply 與 kb_unhelpful 是
-- 交辦藍，其餘落一般灰。保養紫沒有對應的事件值，目前產生不出來。
-- read_at 留空就是未讀，分頁列的通知徽章數的就是未讀則數。
INSERT INTO notifications (id, tenant_id, recipient_staff_id, title, body, target, event, created_at, read_at) VALUES
  ('00000000-0000-7000-8000-00000000e501', 1, '00000000-0000-7000-8000-00000000a001', '306 冷氣不冷已判定為急件', '住客今天入住，請優先處理',
   '{"kind": "pool", "dept": "engineering", "highlightTaskId": "00000000-0000-7000-8000-00000000d020"}'::jsonb, 'repair_urgent',             now() - interval '8 minutes',  NULL),
  ('00000000-0000-7000-8000-00000000e502', 1, '00000000-0000-7000-8000-00000000a001', '202 窗簾掛勾鬆脫還沒有人接', '進池 45 分鐘了',
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
SELECT '  通知 ' || count(*) || ' 則（未讀 ' || count(*) FILTER (WHERE read_at IS NULL) || '）'
  FROM notifications WHERE tenant_id = 1;
SQL

cat <<'EOF'
[seed-demo] 可以登入的帳號（密碼都是 demo1234）：
  hk1  小美    房務員工
  mt1  阿強    工務員工
  mg1  王主管  主管
[seed-demo] 跑過 app/bdd 的 BDD 之後這份資料會被清掉，想再玩就再跑一次這支腳本。
EOF
