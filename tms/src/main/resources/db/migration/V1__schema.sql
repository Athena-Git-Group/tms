-- P1 基線 schema，照 specs/tms-poc-to-backend/db_table/erm.dbml 落地。
-- 慣例與理由：
--   * 實體表 PK=uuid（UUIDv7 應用層產號，避免 DB 取號成為多實例瓶頸）；
--     task_events/outbox/tenants 用 bigint（id 不作訂閱書籤、租戶量少省索引）
--   * enum 用 text + CHECK 而非原生 ENUM：加值不必 ALTER TYPE 鎖表
--   * 跨表引用一律複合外鍵 (tenant_id, xxx_id)：外鍵檢查不經 RLS，
--     這是擋跨租戶關聯的第三道牆，單欄 FK 做不到
--   * FK 一律 RESTRICT：業務不硬刪（停用/作廢/去識別化代替刪除），
--     誤刪在 DB 層直接炸掉而不是級聯清資料

-- EXCLUDE 約束要在 gist 索引混用 bigint/uuid 的等值運算子，沒有 btree_gist 建不起來
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- 租戶主檔。v1 由營運人工開設，應用不寫（grants 區段只給 SELECT）
CREATE TABLE tenants (
    id         bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       text        NOT NULL,
    created_at timestamptz NOT NULL
);

-- 員工帳號。角色×動作矩陣在程式/設定，不在表裡（docs/11 需求 15）
CREATE TABLE staff_accounts (
    id            uuid        PRIMARY KEY,
    tenant_id     bigint      NOT NULL,
    name          text        NOT NULL, -- PII：離職去識別化換不可回推代號
    login_id      text        NOT NULL, -- PII
    password_hash text        NOT NULL, -- bcrypt/argon2，絕不存明文
    role          text        NOT NULL CONSTRAINT chk_staff_accounts_role
                  CHECK (role IN ('frontdesk','housekeeping','engineering','manager','owner')),
    dept          text        CONSTRAINT chk_staff_accounts_dept
                  CHECK (dept IN ('housekeeping','engineering','frontdesk')), -- 主管/老闆可空
    active        boolean     NOT NULL DEFAULT true, -- 停用=不能登入；不刪列是為了歷史單追責
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    CONSTRAINT fk_staff_accounts_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uq_staff_accounts_login UNIQUE (tenant_id, login_id),
    CONSTRAINT uq_staff_accounts_tenant_id_id UNIQUE (tenant_id, id) -- 複合外鍵落點
);
CREATE INDEX idx_staff_accounts_dept ON staff_accounts (tenant_id, dept);

-- 「什麼都可調」的落地。key 設 CHECK 讓鍵集封閉：打錯鍵名在寫入時就炸，
-- 而不是讀取端拿到 null 靜默走預設值
CREATE TABLE tenant_config (
    tenant_id  bigint      NOT NULL,
    key        text        NOT NULL CONSTRAINT chk_tenant_config_key
               CHECK (key IN ('standard_cleaning_minutes','shift_capacity','inspection_items',
                              'sop_items','guest_info_pages','request_menu','minutes_per_credit',
                              'classification_rules','scan_fail_threshold','low_rating_threshold',
                              'shift_times','retention_days')),
    value      jsonb       NOT NULL, -- 結構依 key 而異
    updated_at timestamptz NOT NULL,
    updated_by uuid,                 -- 可空：seed 與系統初始化沒有操作者；人為變更由應用層帶人以利追責
    CONSTRAINT fk_tenant_config_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_tenant_config_updated_by FOREIGN KEY (tenant_id, updated_by)
        REFERENCES staff_accounts (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uq_tenant_config_key UNIQUE (tenant_id, key)
);

-- 推播地址簿：一列=一台裝置。登入不驗裝置、多機合法（fingerprint 只做去重不做綁定）
CREATE TABLE staff_devices (
    id                 uuid        PRIMARY KEY,
    tenant_id          bigint      NOT NULL,
    staff_id           uuid        NOT NULL,
    device_fingerprint text        NOT NULL,
    push_token         text,                 -- APNS/FCM 地址；權限被關即空
    registered_at      timestamptz NOT NULL,
    revoked_at         timestamptz,          -- 登出或推播服務回報失效時標記
    CONSTRAINT fk_staff_devices_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_staff_devices_staff FOREIGN KEY (tenant_id, staff_id)
        REFERENCES staff_accounts (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uq_staff_devices_fingerprint UNIQUE (tenant_id, staff_id, device_fingerprint)
);

-- 房間主檔，兩系統各管一半：hk_state 僅由任務引擎改、occupancy 僅由 PMS 同步改，
-- 分兩欄而不是一欄就是為了讓兩邊寫入永不打架
CREATE TABLE rooms (
    id         uuid        PRIMARY KEY,
    tenant_id  bigint      NOT NULL,
    room_no    text        NOT NULL,
    floor      int         NOT NULL, -- v1 排房演算法排序鍵
    room_type  text        NOT NULL, -- 平均打掃分鐘按它統計
    hk_state   text        NOT NULL CONSTRAINT chk_rooms_hk_state
               CHECK (hk_state IN ('dirty','cleaned','inspected','out_of_order')),
    occupancy  text        NOT NULL CONSTRAINT chk_rooms_occupancy
               CHECK (occupancy IN ('occupied','vacant')),
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_rooms_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uq_rooms_room_no UNIQUE (tenant_id, room_no),
    CONSTRAINT uq_rooms_tenant_id_id UNIQUE (tenant_id, id) -- 複合外鍵落點
);

-- 房間 QR 憑證。輪替=同一交易「舊 token→revoked＋新列 version+1＋該房全部
-- 未撤銷 guest_sessions 標 revoked_at」（E-18 連坐撤銷，防舊貼紙換房客續用）
CREATE TABLE room_qr_tokens (
    id         uuid        PRIMARY KEY,
    tenant_id  bigint      NOT NULL,
    room_id    uuid        NOT NULL,
    -- 全域 UNIQUE 而非租戶內：掃碼入口沒有租戶脈絡，token 本身就是查找鍵
    token      text        NOT NULL CONSTRAINT uq_room_qr_tokens_token UNIQUE,
    version    int         NOT NULL, -- 重產 +1
    status     text        NOT NULL CONSTRAINT chk_room_qr_tokens_status
               CHECK (status IN ('active','revoked')),
    created_at timestamptz NOT NULL,
    revoked_at timestamptz,
    CONSTRAINT fk_room_qr_tokens_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_room_qr_tokens_room FOREIGN KEY (tenant_id, room_id)
        REFERENCES rooms (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
-- 落地義務：一房同時僅一枚現行 QR——partial unique 讓歷史 revoked 列不佔名額
CREATE UNIQUE INDEX uq_room_qr_tokens_one_active
    ON room_qr_tokens (tenant_id, room_id) WHERE status = 'active';

-- 訂房檔唯讀副本（真相在 PMS）。superseded_at 標作廢而非刪列：PMS 恢復比對要留痕
CREATE TABLE reservations_cache (
    id              uuid        PRIMARY KEY,
    tenant_id       bigint      NOT NULL,
    reservation_ref text        NOT NULL, -- PMS 訂單編號；Excel 匯入列合成 excel:日期:房號
    room_id         uuid        NOT NULL,
    guest_name      text,                 -- PII：去識別化範圍
    lang            text,                 -- 住客端語言
    attrs           jsonb,                -- VIP/親子/寵物/紀念日——自動開準備工單依據
    checkin_at      timestamptz NOT NULL,
    checkout_at     timestamptz NOT NULL,
    source          text        NOT NULL CONSTRAINT chk_reservations_cache_source
                    CHECK (source IN ('pms','excel')),
    synced_at       timestamptz NOT NULL,
    superseded_at   timestamptz,          -- 當前訂單查詢一律 IS NULL
    CONSTRAINT fk_reservations_cache_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_reservations_cache_room FOREIGN KEY (tenant_id, room_id)
        REFERENCES rooms (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uq_reservations_cache_ref UNIQUE (tenant_id, reservation_ref),
    CONSTRAINT uq_reservations_cache_tenant_id_id UNIQUE (tenant_id, id), -- 複合外鍵落點
    -- 落地義務（E-10）：同房、住期重疊、皆未作廢→DB 拒絕；
    -- 應用層比對有競態，唯一可靠的擋法是 EXCLUDE 約束
    CONSTRAINT excl_reservations_cache_no_overlap EXCLUDE USING gist (
        tenant_id WITH =,
        room_id   WITH =,
        tstzrange(checkin_at, checkout_at) WITH &&
    ) WHERE (superseded_at IS NULL)
);
CREATE INDEX idx_reservations_cache_room_checkout
    ON reservations_cache (tenant_id, room_id, checkout_at); -- 存取樣式：今日退房與當前訂單

-- 住客 session。驗證=expires_at > now() AND revoked_at IS NULL，缺一即 401（E-17）
CREATE TABLE guest_sessions (
    id             uuid        PRIMARY KEY,
    tenant_id      bigint      NOT NULL,
    -- 全域 UNIQUE：同 QR token，住客請求沒有租戶脈絡
    session_token  text        NOT NULL CONSTRAINT uq_guest_sessions_token UNIQUE,
    room_id        uuid        NOT NULL,
    reservation_id uuid        NOT NULL, -- 綁簽發當下訂單——換住客舊 session 自然失效
    expires_at     timestamptz NOT NULL, -- =退房時間
    revoked_at     timestamptz,          -- QR 輪替/提前退房/訂單取消時整批標上
    created_at     timestamptz NOT NULL,
    last_seen_at   timestamptz,
    CONSTRAINT fk_guest_sessions_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_guest_sessions_room FOREIGN KEY (tenant_id, room_id)
        REFERENCES rooms (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_guest_sessions_reservation FOREIGN KEY (tenant_id, reservation_id)
        REFERENCES reservations_cache (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
CREATE INDEX idx_guest_sessions_room ON guest_sessions (tenant_id, room_id);

-- 排房計畫。P1 僅建表當 tasks.plan_id 的外鍵落點（排房 API 屬 P2），
-- 現在建而不是 P2 再建，是因為 tasks 的 FK 無法指向不存在的表
CREATE TABLE cleaning_plans (
    id           uuid        PRIMARY KEY,
    tenant_id    bigint      NOT NULL,
    work_date    date        NOT NULL,
    shift        text        NOT NULL, -- 不設 CHECK：值域從租戶設定 shift_times 來，每家不同
    status       text        NOT NULL CONSTRAINT chk_cleaning_plans_status
                 CHECK (status IN ('draft','published','superseded')),
    version      int         NOT NULL DEFAULT 0, -- 每次調整 +1；PUT 帶 If-Match（E-08）
    generated_by text        NOT NULL CONSTRAINT chk_cleaning_plans_generated_by
                 CHECK (generated_by IN ('system','manager')),
    algo_version text        NOT NULL, -- v1=樓層×工時一層一人；留欄位供 Q4 v2 對照
    generated_at timestamptz NOT NULL,
    published_at timestamptz,
    CONSTRAINT fk_cleaning_plans_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uq_cleaning_plans_tenant_id_id UNIQUE (tenant_id, id) -- 複合外鍵落點
);
-- 落地義務：一班同時僅一份已發佈；草稿不限（重產對照要能與 published 並存）
CREATE UNIQUE INDEX uq_cleaning_plans_one_published
    ON cleaning_plans (tenant_id, work_date, shift) WHERE status = 'published';

-- 任務投影（可由 task_events 重放重建）。狀態轉移由程式擋（守衛=一句條件式
-- UPDATE，0 列→409）、值域由 CHECK 擋——「值域由 DB 擋、轉移由程式擋」分工
CREATE TABLE tasks (
    id             uuid        PRIMARY KEY,
    tenant_id      bigint      NOT NULL,
    type           text        NOT NULL CONSTRAINT chk_tasks_type
                   CHECK (type IN ('amenity','repair','cleaning','other')),
    source         text        NOT NULL CONSTRAINT chk_tasks_source
                   CHECK (source IN ('guest','desk','system')),
    room_id        uuid        NOT NULL,
    reservation_id uuid,                 -- guest 來源必填；住客查詢一律以它過濾（不按房號）
    dept           text        NOT NULL CONSTRAINT chk_tasks_dept
                   CHECK (dept IN ('housekeeping','engineering','frontdesk')),
    state          text        NOT NULL CONSTRAINT chk_tasks_state
                   CHECK (state IN ('triage','pool','accepted','doing','done',
                                    'inspected','deferred','interrupted','cancelled')),
    -- 事件取號器：守衛 UPDATE 同句 +1 RETURNING，禁止 SELECT max(seq)+1 的競態寫法
    event_seq      int         NOT NULL DEFAULT 0,
    assignee_id    uuid,                 -- 池中為空
    title          text        NOT NULL,
    note           text,                 -- 住客原文/翻譯/交代——PII 去識別化範圍
    est_min        int,                  -- 排房帶入
    started_at     timestamptz,          -- 查房退回清空重計
    done_at        timestamptz,          -- 實際分鐘=done_at−started_at（derived，不落欄）
    redo_count     int         NOT NULL DEFAULT 0,
    rating         int         CONSTRAINT chk_tasks_rating CHECK (rating BETWEEN 1 AND 5),
    plan_id        uuid,                 -- 打掃任務回鏈排房計畫
    work_date      date,                 -- 打掃必填（發佈抄自計畫日）——支撐一房一天約束與班末掃描
    sla_min        int,                  -- 保留欄位，SLA 升級鏈啟用前不寫
    esc_level      int,                  -- 保留欄位
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,
    CONSTRAINT fk_tasks_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_tasks_room FOREIGN KEY (tenant_id, room_id)
        REFERENCES rooms (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_tasks_reservation FOREIGN KEY (tenant_id, reservation_id)
        REFERENCES reservations_cache (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_tasks_assignee FOREIGN KEY (tenant_id, assignee_id)
        REFERENCES staff_accounts (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_tasks_plan FOREIGN KEY (tenant_id, plan_id)
        REFERENCES cleaning_plans (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uq_tasks_tenant_id_id UNIQUE (tenant_id, id) -- 複合外鍵落點
);
CREATE INDEX idx_tasks_dept_state          ON tasks (tenant_id, dept, state);                 -- 存取樣式：待接池與看板
CREATE INDEX idx_tasks_assignee_state      ON tasks (tenant_id, assignee_id, state);          -- 存取樣式：個人在手
CREATE INDEX idx_tasks_room_created        ON tasks (tenant_id, room_id, created_at);         -- 存取樣式：單房歷程
CREATE INDEX idx_tasks_reservation_created ON tasks (tenant_id, reservation_id, created_at);  -- 存取樣式：住客本次住宿需求
-- 落地義務（E-09）：一房一天僅一筆未完成打掃——完成態/取消態不佔名額，
-- 退回重開同一天才不會被自己的歷史單擋住
CREATE UNIQUE INDEX uq_tasks_one_open_cleaning
    ON tasks (tenant_id, room_id, work_date)
    WHERE type = 'cleaning' AND state NOT IN ('done','inspected','cancelled');

-- 唯一真相，只進不改（E-13 由下方 grants 收走 UPDATE/DELETE）。
-- type 不設 CHECK：spec 明定「可擴充，加值不改表」；actor_role 是封閉集合照設
CREATE TABLE task_events (
    id         bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY, -- 非訂閱書籤（增量一律走 Kafka）
    tenant_id  bigint      NOT NULL,
    task_id    uuid        NOT NULL,
    seq        int         NOT NULL, -- 這個任務的第幾步；取自 tasks.event_seq 的 RETURNING
    type       text        NOT NULL,
    actor_role text        NOT NULL CONSTRAINT chk_task_events_actor_role
               CHECK (actor_role IN ('guest','frontdesk','housekeeping','engineering','manager','system')),
    actor_id   uuid,                 -- 住客與系統為空
    payload    jsonb,                -- 會改 tasks 欄位的事件必填帶齊（created 帶建立快照）
    created_at timestamptz NOT NULL,
    CONSTRAINT fk_task_events_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_task_events_task FOREIGN KEY (tenant_id, task_id)
        REFERENCES tasks (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uq_task_events_task_seq UNIQUE (tenant_id, task_id, seq)
);
CREATE INDEX idx_task_events_tenant_id ON task_events (tenant_id, id); -- 存取樣式：離線全表重放

-- 待送匣=交易護欄：與狀態變更同交易落庫，壞掉的只會是「送出延遲」不會是「掉事件」
CREATE TABLE outbox (
    id              bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       bigint      NOT NULL,
    -- Kafka 分區鍵：task_event/push 用 task:<uuid>；pms_status 一律 room:<tenant_id>:<uuid>
    -- （跟一致性邊界走，E-14 同鍵串行靠它）
    aggregate_key   text        NOT NULL,
    kind            text        NOT NULL CONSTRAINT chk_outbox_kind
                    CHECK (kind IN ('task_event','push','pms_status')),
    payload         jsonb       NOT NULL,
    idempotency_key text        NOT NULL, -- 下游去重：重送不重做
    status          text        NOT NULL DEFAULT 'pending' CONSTRAINT chk_outbox_status
                    CHECK (status IN ('pending','sent','failed')),
    attempts        int         NOT NULL DEFAULT 0,
    next_retry_at   timestamptz,          -- 指數退避
    created_at      timestamptz NOT NULL,
    sent_at         timestamptz,          -- sent 保留 3 天後排程清除
    CONSTRAINT fk_outbox_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uq_outbox_idempotency UNIQUE (tenant_id, kind, idempotency_key)
);
-- partial index：relay 只關心 pending，sent/failed 不進索引才撐得住事件量
CREATE INDEX idx_outbox_pending_by_key ON outbox (aggregate_key, id) WHERE status = 'pending';

-- 弱網重送去重簿（GR-002/E-11）。7 天後排程清
CREATE TABLE processed_ops (
    tenant_id  bigint      NOT NULL,
    op_id      text        NOT NULL, -- 客戶端 Idempotency-Key（UUID）
    result_ref jsonb       NOT NULL, -- 狀態碼＋精簡回應，重送原樣回放
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_processed_ops PRIMARY KEY (tenant_id, op_id),
    CONSTRAINT fk_processed_ops_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- SOP/查房項目同構共表（applies_to 區分）：/admin/settings 的 sop_items 與
-- inspection_items 兩鍵是本表門面，儲存真相在表不在 tenant_config
CREATE TABLE sop_items (
    id         uuid        PRIMARY KEY,
    tenant_id  bigint      NOT NULL,
    applies_to text        NOT NULL CONSTRAINT chk_sop_items_applies_to
               CHECK (applies_to IN ('cleaning','inspection')),
    item_key   text        NOT NULL,
    label      text        NOT NULL,
    sort_order int         NOT NULL,
    active     boolean     NOT NULL DEFAULT true, -- 下架用停用不刪：歷史單快照要能對回來源
    created_at timestamptz NOT NULL,
    CONSTRAINT fk_sop_items_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uq_sop_items_key UNIQUE (tenant_id, applies_to, item_key)
);

-- 任務的 SOP 勾記（投影）；查房退回時歸零重來，「誰在幾點勾過什麼」的歷史在
-- task_events（sop_checked），所以這裡可以放心覆寫
CREATE TABLE task_sop_checks (
    id         uuid        PRIMARY KEY,
    tenant_id  bigint      NOT NULL,
    task_id    uuid        NOT NULL,
    item_key   text        NOT NULL,
    label      text        NOT NULL, -- 建單當下快照：項目日後改名不影響歷史單
    checked    boolean     NOT NULL DEFAULT false,
    skipped    boolean     NOT NULL DEFAULT false, -- 跳過不擋流程但留痕
    checked_by uuid,
    checked_at timestamptz,
    -- 勾了又跳過是資料矛盾，寫入端 bug 要在 DB 層就炸
    CONSTRAINT chk_task_sop_checks_exclusive CHECK (NOT (checked AND skipped)),
    CONSTRAINT fk_task_sop_checks_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_task_sop_checks_task FOREIGN KEY (tenant_id, task_id)
        REFERENCES tasks (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_task_sop_checks_checked_by FOREIGN KEY (tenant_id, checked_by)
        REFERENCES staff_accounts (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uq_task_sop_checks_item UNIQUE (tenant_id, task_id, item_key)
);

-- 查房記錄（history）：一次查房一列、退回重查多列，所以 task_id 不設唯一。
-- 「inspector ≠ 原清房者」是跨表規則，DB 擋不了，由程式層回 403
CREATE TABLE inspections (
    id           uuid        PRIMARY KEY,
    tenant_id    bigint      NOT NULL,
    task_id      uuid        NOT NULL,
    inspector_id uuid        NOT NULL,
    result       text        NOT NULL CONSTRAINT chk_inspections_result
                 CHECK (result IN ('passed','failed')),
    items        jsonb       NOT NULL, -- [{key, pass}]；鍵集=租戶查房項目全集（缺項 422，程式層擋）
    created_at   timestamptz NOT NULL,
    CONSTRAINT fk_inspections_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_inspections_task FOREIGN KEY (tenant_id, task_id)
        REFERENCES tasks (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_inspections_inspector FOREIGN KEY (tenant_id, inspector_id)
        REFERENCES staff_accounts (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
CREATE INDEX idx_inspections_task ON inspections (tenant_id, task_id);

-- 請假。P1 僅供故事 4 退回連動讀「已請假」（登記 API 屬 P2 故事 8）；
-- 連鎖效果（accepted→pool、doing→interrupted）記事件，不回寫本表
CREATE TABLE leaves (
    id         uuid        PRIMARY KEY,
    tenant_id  bigint      NOT NULL,
    staff_id   uuid        NOT NULL,
    from_at    timestamptz NOT NULL,
    to_at      timestamptz NOT NULL,
    created_by uuid        NOT NULL, -- 登記的主管
    created_at timestamptz NOT NULL,
    CONSTRAINT fk_leaves_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_leaves_staff FOREIGN KEY (tenant_id, staff_id)
        REFERENCES staff_accounts (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_leaves_created_by FOREIGN KEY (tenant_id, created_by)
        REFERENCES staff_accounts (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
CREATE INDEX idx_leaves_staff ON leaves (tenant_id, staff_id, from_at);

-- 應用角色 tms_app：migration 以管理身分跑、應用以最小權限連線，
-- 這個落差就是 E-13（收走 UPDATE/DELETE）與 RLS 生效的前提——
-- 表擁有者與 superuser 天然繞過 RLS，應用絕不能用那種身分連。
-- CREATE ROLE 是叢集層物件，條件式建立讓同叢集重跑/多庫共用不炸。
-- 開發密碼 'tms_app' 只供本機與測試：正式環境部署後必須 ALTER ROLE 改密
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'tms_app') THEN
        CREATE ROLE tms_app LOGIN PASSWORD 'tms_app';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO tms_app;

-- tenants 由營運人工開設：應用只讀，連 INSERT 都不給
GRANT SELECT ON tenants TO tms_app;

GRANT SELECT, INSERT, UPDATE, DELETE ON
    tenant_config, staff_accounts, staff_devices, rooms, room_qr_tokens,
    guest_sessions, reservations_cache, tasks, outbox, processed_ops,
    sop_items, task_sop_checks, inspections, cleaning_plans, leaves
TO tms_app;

-- 落地義務（E-13）：唯一真相只進不改——應用角色只拿 SELECT/INSERT。
-- 唯一例外=受控去識別化函式，屬 P2+，本輪不建
GRANT SELECT, INSERT ON task_events TO tms_app;

-- identity 欄位取號走序列：沒這行 tms_app 連合法 INSERT 都會炸
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO tms_app;

-- 落地義務（FR-043 第二道牆）：RLS。policy 用 current_setting('app.tenant_id', true)
-- ——missing_ok=true 讓「忘了注入 session 變數」退化成一列都看不到，
-- 而不是整句 SQL 報錯把內部細節洩給呼叫端（注入時機由 Phase 03 接）。
-- 省略 WITH CHECK 時 USING 同時管寫入，跨租戶 INSERT 一樣被擋。
-- tenants 表無 tenant_id 欄（平台層主檔），不在 RLS 範圍
ALTER TABLE tenant_config      ENABLE ROW LEVEL SECURITY;
ALTER TABLE staff_accounts     ENABLE ROW LEVEL SECURITY;
ALTER TABLE staff_devices      ENABLE ROW LEVEL SECURITY;
ALTER TABLE rooms              ENABLE ROW LEVEL SECURITY;
ALTER TABLE room_qr_tokens     ENABLE ROW LEVEL SECURITY;
ALTER TABLE guest_sessions     ENABLE ROW LEVEL SECURITY;
ALTER TABLE reservations_cache ENABLE ROW LEVEL SECURITY;
ALTER TABLE tasks              ENABLE ROW LEVEL SECURITY;
ALTER TABLE task_events        ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox             ENABLE ROW LEVEL SECURITY;
ALTER TABLE processed_ops      ENABLE ROW LEVEL SECURITY;
ALTER TABLE sop_items          ENABLE ROW LEVEL SECURITY;
ALTER TABLE task_sop_checks    ENABLE ROW LEVEL SECURITY;
ALTER TABLE inspections        ENABLE ROW LEVEL SECURITY;
ALTER TABLE cleaning_plans     ENABLE ROW LEVEL SECURITY;
ALTER TABLE leaves             ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_tenant_config      ON tenant_config      USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_staff_accounts     ON staff_accounts     USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_staff_devices      ON staff_devices      USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_rooms              ON rooms              USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_room_qr_tokens     ON room_qr_tokens     USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_guest_sessions     ON guest_sessions     USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_reservations_cache ON reservations_cache USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_tasks              ON tasks              USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_task_events        ON task_events        USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_outbox             ON outbox             USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_processed_ops      ON processed_ops      USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_sop_items          ON sop_items          USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_task_sop_checks    ON task_sop_checks    USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_inspections        ON inspections        USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_cleaning_plans     ON cleaning_plans     USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_leaves             ON leaves             USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
