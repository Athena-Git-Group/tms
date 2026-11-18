-- V5：plan 001-staff-app-increment 的持久化增量。只動 schema，不寫狀態轉移／查詢／API
-- 行為，也不回填既有資料以外的業務值（新欄位一律用 DEFAULT 落地、不跑 UPDATE 灌業務值）。
-- 範圍依 specs/truth/data/data-model.dbml「本 plan（001-staff-app-increment）新增」段：
--   1) tasks 新增欄位、partial UNIQUE uq_tasks_one_doing_per_staff、待接池排序索引
--   2) CHECK 更新：tasks.source 加 staff、tenant_config.key 加 inspection_waived_cleaning_types
--   3) 8 張新表：repair_tickets、repair_ticket_counters、repair_ticket_contributors、
--      repair_photos、desk_notes、handover_reads、kb_audit_logs、kb_escalations，與全部新 Ref
--
-- task_events.type 刻意不動：V1 建表時明訂「type 不設 CHECK：加值不改表」，dbml 本輪把
-- type 的合法值從 18 個擴到 21 個（加 resumed、inspection_waived、repair_photos_appended）
-- 純粹是文件記載的擴充，資料庫從來沒有對應 CHECK 可以/需要 DROP+ADD——與 V1 的設計意圖一致，
-- 本檔不新增這個 CHECK。

-- ───────── 1) tasks 新增欄位 ─────────
ALTER TABLE tasks
    ADD COLUMN worked_sec          int         NOT NULL DEFAULT 0, -- 已結束的 doing 段落累計秒數，不含中斷期間
    ADD COLUMN segment_started_at  timestamptz,                    -- 目前這段 doing 的開始時間，只在 doing 時有值
    ADD COLUMN inspection          text        CONSTRAINT chk_tasks_inspection
                                    CHECK (inspection IN ('passed','waived','failed')), -- 只有 cleaning 有值
    ADD COLUMN inspection_waived   boolean     NOT NULL DEFAULT false, -- 開工當下依租戶設定鎖定，之後設定變更不影響
    ADD COLUMN hold_reason         text,                           -- 停住原因：本人延後／中斷原因，或工務連動文案
    ADD COLUMN deferred_by_self    boolean     NOT NULL DEFAULT false, -- state=deferred 時，true=本人延後、false=主管延後
    ADD COLUMN linked_repair_task_id uuid,                         -- 中斷模式開工務單時，連結到那張工務單（type=repair）
    ADD COLUMN cancel_reason       text        CONSTRAINT chk_tasks_cancel_reason
                                    CHECK (cancel_reason IN ('guest_withdrew','manager_void','plan_superseded','staff_withdrew')),
    ADD COLUMN urgent              boolean     NOT NULL DEFAULT false, -- 只有 repair 會是 true；待接池排在非急件之前
    ADD COLUMN priority_notify     boolean     NOT NULL DEFAULT false, -- 請假退回、主管附交代退池時為 true
    ADD COLUMN pool_source         text        CONSTRAINT chk_tasks_pool_source
                                    CHECK (pool_source IN ('guest_scan','desk_forward','manager_requeue',
                                                            'leave_return','shift_end_return','inspection_return',
                                                            'staff_report','system')),
    ADD COLUMN return_reason       text,                           -- 最近一次退池的原因
    ADD COLUMN requeue_note        text;                           -- 接手進度交代，PII 去識別化範圍

-- linked_repair_task_id 自我參照，複合欄位必須table-level ADD CONSTRAINT（inline 欄位約束做不到複合 FK）
ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_linked_repair_task FOREIGN KEY (tenant_id, linked_repair_task_id)
        REFERENCES tasks (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT;

-- 落地義務（FR-001）：一位員工同時最多一筆 doing；守衛 UPDATE 違反時回 409 doing_task_exists
CREATE UNIQUE INDEX uq_tasks_one_doing_per_staff
    ON tasks (tenant_id, assignee_id) WHERE state = 'doing';

-- 待接池排序（急件→優先通知→同樓層→房號；同樓層與房號要 JOIN rooms，索引本身只覆蓋 tasks 欄位）
CREATE INDEX idx_tasks_pool_order ON tasks (tenant_id, dept, state, urgent, priority_notify);

-- ───────── 2) CHECK 更新 ─────────

-- tasks.source 新增 staff（房務開立的工務單）
ALTER TABLE tasks DROP CONSTRAINT chk_tasks_source;
ALTER TABLE tasks ADD CONSTRAINT chk_tasks_source
    CHECK (source IN ('guest','desk','system','staff'));

-- tenant_config.key 新增 inspection_waived_cleaning_types（免查房的打掃種類，值為打掃種類陣列，預設 ["stayover"]）
ALTER TABLE tenant_config DROP CONSTRAINT chk_tenant_config_key;
ALTER TABLE tenant_config ADD CONSTRAINT chk_tenant_config_key
    CHECK (key IN ('standard_cleaning_minutes','shift_capacity','inspection_items',
                   'sop_items','guest_info_pages','request_menu','minutes_per_credit',
                   'classification_rules','scan_fail_threshold','low_rating_threshold',
                   'shift_times','retention_days','inspection_waived_cleaning_types'));

-- ───────── 3) 8 張新表 ─────────

-- 工務單（FR-011–021）。與 tasks 一對一：task_id 即 tasks.id（type=repair、dept=engineering、
-- source=staff），狀態與負責人一律看 tasks，本表只存工務單專屬的延伸資料
CREATE TABLE repair_tickets (
    task_id         uuid        PRIMARY KEY,
    tenant_id       bigint      NOT NULL,
    ticket_no       text        NOT NULL, -- 租戶內遞增單號，格式 W- 加 4 位數，例如 W-0410
    room_id         uuid        NOT NULL, -- 冗餘自 tasks.room_id，供重複單查詢使用
    category        text        NOT NULL CONSTRAINT chk_repair_tickets_category
                    CHECK (category IN ('air_conditioning','plumbing_electrical','furniture_equipment','door_lock','other')),
    description     text,                 -- 報修說明，選填。PII 去識別化範圍
    reporter_id     uuid        NOT NULL, -- 報修人；撤回與補照片的授權依據
    source_task_id  uuid,                 -- 開單時所在的原任務
    resolution_note text,                 -- 工務處理說明；判斷不需維修時在這裡註明
    created_at      timestamptz NOT NULL,
    CONSTRAINT fk_repair_tickets_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_repair_tickets_task FOREIGN KEY (tenant_id, task_id)
        REFERENCES tasks (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_repair_tickets_room FOREIGN KEY (tenant_id, room_id)
        REFERENCES rooms (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_repair_tickets_reporter FOREIGN KEY (tenant_id, reporter_id)
        REFERENCES staff_accounts (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_repair_tickets_source_task FOREIGN KEY (tenant_id, source_task_id)
        REFERENCES tasks (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uq_repair_tickets_ticket_no UNIQUE (tenant_id, ticket_no)
);
CREATE INDEX idx_repair_tickets_room_category ON repair_tickets (tenant_id, room_id, category); -- 重複單檢查，JOIN tasks 過濾 state

-- 工務單號序號（每租戶一列）。取號：UPDATE … SET last_no=last_no+1 RETURNING，與建單同交易
CREATE TABLE repair_ticket_counters (
    tenant_id bigint PRIMARY KEY,
    last_no   int    NOT NULL DEFAULT 0,
    CONSTRAINT fk_repair_ticket_counters_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- 可以補照片的非報修人（FR-018）。報修人本身不需要列在這裡
CREATE TABLE repair_ticket_contributors (
    tenant_id      bigint      NOT NULL,
    ticket_task_id uuid        NOT NULL,
    staff_id       uuid        NOT NULL,
    granted_at     timestamptz NOT NULL, -- 在開單前檢查時觸發這張單重複提示的時間
    CONSTRAINT pk_repair_ticket_contributors PRIMARY KEY (tenant_id, ticket_task_id, staff_id),
    CONSTRAINT fk_repair_ticket_contributors_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_repair_ticket_contributors_ticket FOREIGN KEY (tenant_id, ticket_task_id)
        REFERENCES tasks (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_repair_ticket_contributors_staff FOREIGN KEY (tenant_id, staff_id)
        REFERENCES staff_accounts (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- 工務單照片（兩段式：先建 pending 列取得 id，再上傳內容）。本體外存，沿用 lost_items.photo_ref 慣例
CREATE TABLE repair_photos (
    id             uuid        PRIMARY KEY,
    tenant_id      bigint      NOT NULL,
    ticket_task_id uuid,                 -- 開單或補照片之前為空；綁定後不可改
    purpose        text        NOT NULL CONSTRAINT chk_repair_photos_purpose
                   CHECK (purpose IN ('report','completion')),
    status         text        NOT NULL DEFAULT 'pending' CONSTRAINT chk_repair_photos_status
                   CHECK (status IN ('pending','uploaded')),
    storage_ref    text,                 -- 照片外存引用；uploaded 時必填（程式層擋）
    taken_at       timestamptz NOT NULL, -- 拍攝時間，與 App 浮水印一致
    uploaded_by    uuid        NOT NULL,
    created_at     timestamptz NOT NULL,
    uploaded_at    timestamptz,
    CONSTRAINT fk_repair_photos_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_repair_photos_ticket FOREIGN KEY (tenant_id, ticket_task_id)
        REFERENCES tasks (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_repair_photos_uploaded_by FOREIGN KEY (tenant_id, uploaded_by)
        REFERENCES staff_accounts (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
CREATE INDEX idx_repair_photos_ticket_purpose ON repair_photos (tenant_id, ticket_task_id, purpose);

-- 櫃檯房間備註（FR-037），併入該房任務的交辦。有效期間尚未決定（NEEDS CLARIFICATION），
-- 確定前不加失效欄位——這裡刻意不建任何失效相關欄位
CREATE TABLE desk_notes (
    id         uuid        PRIMARY KEY,
    tenant_id  bigint      NOT NULL,
    room_id    uuid        NOT NULL,
    content    text        NOT NULL, -- PII 去識別化範圍
    author_id  uuid        NOT NULL, -- 櫃檯員工
    created_at timestamptz NOT NULL,
    CONSTRAINT fk_desk_notes_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_desk_notes_room FOREIGN KEY (tenant_id, room_id)
        REFERENCES rooms (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_desk_notes_author FOREIGN KEY (tenant_id, author_id)
        REFERENCES staff_accounts (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
CREATE INDEX idx_desk_notes_room_created ON desk_notes (tenant_id, room_id, created_at);

-- 交辦已讀（FR-038），員工 × 任務。展開交辦、開工、延後時記錄；重複動作不重記（ON CONFLICT DO NOTHING）
CREATE TABLE handover_reads (
    tenant_id bigint      NOT NULL,
    task_id   uuid        NOT NULL,
    staff_id  uuid        NOT NULL,
    read_at   timestamptz NOT NULL, -- 第一次已讀的時間
    CONSTRAINT pk_handover_reads PRIMARY KEY (tenant_id, task_id, staff_id),
    CONSTRAINT fk_handover_reads_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_handover_reads_task FOREIGN KEY (tenant_id, task_id)
        REFERENCES tasks (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_handover_reads_staff FOREIGN KEY (tenant_id, staff_id)
        REFERENCES staff_accounts (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

-- 查一下稽核紀錄（FR-045）。每次提問一列，回饋與轉問在同一列更新
CREATE TABLE kb_audit_logs (
    id              uuid        PRIMARY KEY, -- = API 的 answerId
    tenant_id       bigint      NOT NULL,
    asker_id        uuid        NOT NULL,
    task_id         uuid        NOT NULL,
    question        text        NOT NULL, -- PII 去識別化範圍
    cited_kind      text        NOT NULL CONSTRAINT chk_kb_audit_logs_cited_kind
                    CHECK (cited_kind IN ('sop','room_memory','none')),
    cited_refs      jsonb,                -- 引用來源清單 [{kind, id, title, updated_on}]；查不到時為空
    answer          text,                 -- 查不到時為空；只由原文組成，不得含住客身分
    feedback        text        CONSTRAINT chk_kb_audit_logs_feedback
                    CHECK (feedback IN ('helpful','unhelpful')), -- 未回饋時為空
    escalated       boolean     NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL,
    CONSTRAINT fk_kb_audit_logs_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_kb_audit_logs_asker FOREIGN KEY (tenant_id, asker_id)
        REFERENCES staff_accounts (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_kb_audit_logs_task FOREIGN KEY (tenant_id, task_id)
        REFERENCES tasks (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uq_kb_audit_logs_tenant_id_id UNIQUE (tenant_id, id) -- 複合外鍵落點
);
CREATE INDEX idx_kb_audit_logs_created ON kb_audit_logs (tenant_id, created_at);
CREATE INDEX idx_kb_audit_logs_task    ON kb_audit_logs (tenant_id, task_id);

-- 問主管（FR-043）。主管從哪個入口回覆尚未決定（NEEDS CLARIFICATION），兩種入口都寫這張表；
-- 一則回答只能轉問一次
CREATE TABLE kb_escalations (
    id           uuid        PRIMARY KEY,
    tenant_id    bigint      NOT NULL,
    audit_log_id uuid        NOT NULL, -- 對應的查一下稽核紀錄
    question     text        NOT NULL,
    asked_by     uuid        NOT NULL,
    status       text        NOT NULL DEFAULT 'pending' CONSTRAINT chk_kb_escalations_status
                 CHECK (status IN ('pending','answered')),
    reply        text,                 -- 主管回覆；回覆時推播提問員工
    replied_by   uuid,
    replied_at   timestamptz,
    created_at   timestamptz NOT NULL,
    CONSTRAINT fk_kb_escalations_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_kb_escalations_audit_log FOREIGN KEY (tenant_id, audit_log_id)
        REFERENCES kb_audit_logs (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_kb_escalations_asked_by FOREIGN KEY (tenant_id, asked_by)
        REFERENCES staff_accounts (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_kb_escalations_replied_by FOREIGN KEY (tenant_id, replied_by)
        REFERENCES staff_accounts (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uq_kb_escalations_audit_log UNIQUE (tenant_id, audit_log_id)
);
CREATE INDEX idx_kb_escalations_status_created ON kb_escalations (tenant_id, status, created_at);

GRANT SELECT, INSERT, UPDATE, DELETE ON
    repair_tickets, repair_ticket_counters, repair_ticket_contributors, repair_photos,
    desk_notes, handover_reads, kb_audit_logs, kb_escalations
TO tms_app;

ALTER TABLE repair_tickets             ENABLE ROW LEVEL SECURITY;
ALTER TABLE repair_ticket_counters     ENABLE ROW LEVEL SECURITY;
ALTER TABLE repair_ticket_contributors ENABLE ROW LEVEL SECURITY;
ALTER TABLE repair_photos              ENABLE ROW LEVEL SECURITY;
ALTER TABLE desk_notes                 ENABLE ROW LEVEL SECURITY;
ALTER TABLE handover_reads             ENABLE ROW LEVEL SECURITY;
ALTER TABLE kb_audit_logs              ENABLE ROW LEVEL SECURITY;
ALTER TABLE kb_escalations             ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_repair_tickets             ON repair_tickets             USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_repair_ticket_counters     ON repair_ticket_counters     USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_repair_ticket_contributors ON repair_ticket_contributors USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_repair_photos              ON repair_photos              USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_desk_notes                 ON desk_notes                 USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_handover_reads             ON handover_reads             USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_kb_audit_logs              ON kb_audit_logs              USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_kb_escalations             ON kb_escalations             USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
