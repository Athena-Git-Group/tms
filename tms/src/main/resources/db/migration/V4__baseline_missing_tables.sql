-- V4：補 V1 漏建的 3 張基線表。
-- specs/truth/data/data-model.dbml 的 baseline 段（tenants…leaves 之前）定義了 plan_rooms、
-- handovers、room_memory，但 V1（照 specs/tms-poc-to-backend/db_table/erm.dbml 落地時）漏刻；
-- truth-delta.md 的 /data-plan 把這 3 張表標成 NOOP（誤判「已存在」），與資料庫現況不符。
-- 本 plan（001-staff-app-increment）的 fixture（StaffFixtures 的 plan_rooms 班次／樓層、
-- HandoverFixtures 的交班單 snapshot、RoomFixtures 的 room_memory）都依賴這 3 張表存在，
-- 這裡先補齊，純 schema、不含本 plan 新增的任何欄位。
-- 慣例沿用 V1：tenant_id 首欄、UUIDv7 應用層產號、複合外鍵 (tenant_id, xxx_id)、
-- FK 一律 ON DELETE/UPDATE RESTRICT、RLS + tms_app 全 CRUD grant。

-- 排房計畫明細（Plan×Room×Staff junction）。task_id 發佈後回填，P1 建表時先留空位；
-- 這裡只建表，不回填任何既有 cleaning_plans 的對應資料
CREATE TABLE plan_rooms (
    id        uuid   PRIMARY KEY,
    tenant_id bigint NOT NULL,
    plan_id   uuid   NOT NULL,
    room_id   uuid   NOT NULL,
    staff_id  uuid   NOT NULL, -- 誰在哪天負責哪些房查這張
    est_min   int    NOT NULL, -- 抄自 cleaning_time_stats，樣本<20 用標準工時
    route_seq int    NOT NULL, -- 建議打掃順序（v1=照樓層）
    task_id   uuid,            -- 發佈後回填對應打掃任務
    CONSTRAINT fk_plan_rooms_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_plan_rooms_plan FOREIGN KEY (tenant_id, plan_id)
        REFERENCES cleaning_plans (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_plan_rooms_room FOREIGN KEY (tenant_id, room_id)
        REFERENCES rooms (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_plan_rooms_staff FOREIGN KEY (tenant_id, staff_id)
        REFERENCES staff_accounts (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_plan_rooms_task FOREIGN KEY (tenant_id, task_id)
        REFERENCES tasks (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uq_plan_rooms_plan_room UNIQUE (tenant_id, plan_id, room_id)
);
CREATE INDEX idx_plan_rooms_plan_staff ON plan_rooms (tenant_id, plan_id, staff_id); -- 存取樣式：個人當日清單

-- 交班單凍結快照（history，永不 UPDATE）。snapshot 定格交班當下任務清單，之後任務再變不影響
CREATE TABLE handovers (
    id           uuid        PRIMARY KEY,
    tenant_id    bigint      NOT NULL,
    dept         text        NOT NULL CONSTRAINT chk_handovers_dept
                 CHECK (dept IN ('housekeeping','engineering','frontdesk')),
    shift        text        NOT NULL,
    work_date    date        NOT NULL,
    snapshot     jsonb       NOT NULL,
    generated_by text        NOT NULL CONSTRAINT chk_handovers_generated_by
                 CHECK (generated_by IN ('system','manager')), -- 班末掃描產生，主管可手動（假設 A8）
    created_at   timestamptz NOT NULL,
    CONSTRAINT fk_handovers_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uq_handovers_tenant_id_id UNIQUE (tenant_id, id) -- 複合外鍵落點（handover_signs 等未來表）
);
CREATE INDEX idx_handovers_work_date ON handovers (tenant_id, work_date);

-- 房間往事投影（可重算）：工務單完修以 kind=repair 寫入（類別、處理說明、處理人）；
-- 餵「查一下」，回答與出處不得帶出住客身分（FR-046，程式層落地，非本表責任）
CREATE TABLE room_memory (
    id             uuid        PRIMARY KEY,
    tenant_id      bigint      NOT NULL,
    room_id        uuid        NOT NULL,
    kind           text        NOT NULL CONSTRAINT chk_room_memory_kind
                   CHECK (kind IN ('repair','preference','incident')),
    summary        text        NOT NULL, -- 可能含住客線索——去識別化範圍
    source_task_id uuid,                 -- 追溯來源任務
    occurred_at    timestamptz NOT NULL,
    CONSTRAINT fk_room_memory_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_room_memory_room FOREIGN KEY (tenant_id, room_id)
        REFERENCES rooms (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_room_memory_source_task FOREIGN KEY (tenant_id, source_task_id)
        REFERENCES tasks (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
CREATE INDEX idx_room_memory_room_occurred ON room_memory (tenant_id, room_id, occurred_at);

GRANT SELECT, INSERT, UPDATE, DELETE ON plan_rooms, handovers, room_memory TO tms_app;

ALTER TABLE plan_rooms  ENABLE ROW LEVEL SECURITY;
ALTER TABLE handovers   ENABLE ROW LEVEL SECURITY;
ALTER TABLE room_memory ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_plan_rooms  ON plan_rooms  USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_handovers   ON handovers   USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_room_memory ON room_memory USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
