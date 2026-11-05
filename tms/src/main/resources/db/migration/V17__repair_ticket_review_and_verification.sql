-- V17：工務單加入主管審核與驗收流程（以原型為準的翻案定案），資料側補兩塊：
--   1) tasks.state 擴充兩個審核中狀態：pending_review（等主管審核）、rejected（主管未核准，
--      等報修人補資料重送）。只有 type=repair 的一般件（非急件、房務報修來源）會走進這兩個
--      狀態；急件、工務自開、櫃檯交辦直接進 pool。走向判定在應用層，資料層只擴值。
--   2) repair_tickets 補審核與驗收的延伸欄位。驗收不開新狀態值（狀態仍是 done），只記驗收人
--      與時間；「暫時處理」完修會自動開一張追蹤單，追蹤單用 origin_ticket_task_id 指回原單。
--
-- 既有資料列沒有審核／驗收歷史可回填，新欄一律 NULL 落地，不另外跑 UPDATE 灌業務值，
-- 沿用 V5／V14 的既有慣例。
--
-- task_events.type 不需要新增 CHECK：V1 建表時刻意不對 type 設 CHECK（「加值不改表」），
-- 本輪新增的 repair_approved／repair_rejected／repair_resubmitted／repair_verified
-- 純粹是文件記載的擴充，沒有對應的資料庫結構變動。
ALTER TABLE tasks DROP CONSTRAINT chk_tasks_state;
ALTER TABLE tasks ADD CONSTRAINT chk_tasks_state
    CHECK (state IN ('triage','pool','accepted','doing','done',
                     'inspected','deferred','interrupted','cancelled',
                     'pending_review','rejected'));

ALTER TABLE repair_tickets
    ADD COLUMN reject_reason        text,        -- 主管退回時必附的原因；未被退回過為 NULL
    ADD COLUMN verified_by          uuid,        -- 驗收通過的主管；尚未驗收為 NULL
    ADD COLUMN verified_at          timestamptz, -- 驗收通過時間；尚未驗收為 NULL
    ADD COLUMN origin_ticket_task_id uuid;       -- 追蹤單指回的原工務單；非追蹤單為 NULL

-- 自參照 FK 要有租戶內唯一落點：task_id 已是 PK，補上租戶複合唯一鍵讓 FK 沿用
-- 全專案「FK 一律帶 tenant_id」的慣例，跨租戶指錯單直接被約束擋下
ALTER TABLE repair_tickets
    ADD CONSTRAINT uq_repair_tickets_tenant_task UNIQUE (tenant_id, task_id);

ALTER TABLE repair_tickets
    ADD CONSTRAINT fk_repair_tickets_verified_by FOREIGN KEY (tenant_id, verified_by)
        REFERENCES staff_accounts (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_repair_tickets_origin_ticket FOREIGN KEY (tenant_id, origin_ticket_task_id)
        REFERENCES repair_tickets (tenant_id, task_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
