-- V23：工務單收回主管審核，改成「完修後主管驗收，通過或不通過」，資料側跟著調整。
--   1) tasks.state 收回 pending_review、rejected，新增 closed（已結案）。
--      工務單的 done 從此只代表「已完修、等主管驗收」，驗收通過或完修結果是
--      no_action_needed 才進 closed。tasks.pool_source 加 verification_return：
--      驗收不通過時原處理人已下班或請假，單回到工務待認領。
--   2) repair_tickets 刪 reject_reason，加最近一次驗收不通過的原因、判定人與時間；
--      resolution_type 加第四個值 no_action_needed（判斷不需處理）。
--   3) notifications.event 以 repair_verification_failed 取代 repair_rejected，總數仍是十八值。
--
-- 順序固定為先拿掉舊 CHECK、再處理既有資料列、最後加新 CHECK：
-- 新 CHECK 不認得 pending_review、rejected 與 repair_rejected，資料沒轉完就加會直接失敗。
--
-- task_events 不動：資料庫對 type 沒有 CHECK，停用的審核事件是歷史紀錄，只進不改。

-- 1) tasks ----------------------------------------------------------------
ALTER TABLE tasks DROP CONSTRAINT chk_tasks_state;
ALTER TABLE tasks DROP CONSTRAINT chk_tasks_pool_source;

-- 等審核與被退回的報修一律回工務待認領，而不是取消：
-- 新流程下誤報由工務以「不需處理」結單，直接取消會把可能真實存在的故障丟掉。
UPDATE tasks
   SET state = 'pool', assignee_id = NULL, pool_source = 'staff_report', reminder_stage = NULL
 WHERE type = 'repair' AND state IN ('pending_review', 'rejected');

-- 已驗收通過的完修單轉已結案；還沒驗收的維持 done，進主管的待驗收清單。
UPDATE tasks t
   SET state = 'closed'
  FROM repair_tickets rt
 WHERE rt.tenant_id = t.tenant_id AND rt.task_id = t.id
   AND t.type = 'repair' AND t.state = 'done' AND rt.verified_at IS NOT NULL;

ALTER TABLE tasks ADD CONSTRAINT chk_tasks_state
    CHECK (state IN ('triage','pool','accepted','doing','done',
                     'inspected','closed','deferred','interrupted','cancelled'));
ALTER TABLE tasks ADD CONSTRAINT chk_tasks_pool_source
    CHECK (pool_source IN ('guest_scan','desk_forward','manager_requeue',
                           'leave_return','shift_end_return','inspection_return',
                           'staff_report','system','verification_return'));

-- 2) repair_tickets -------------------------------------------------------
-- 舊的退回原因仍留在 task_events.repair_rejected 的 payload，刪欄位不會丟歷史。
-- 驗收不通過的原因另立欄位，不重用 reject_reason：兩者的觸發者、收件人與生命週期都不同，
-- 重用會讓舊資料的退回原因被讀成驗收不通過。
ALTER TABLE repair_tickets DROP COLUMN reject_reason;

-- 三欄同一句 UPDATE 寫入，同為 NULL 或同有值；每次不通過覆寫，重修與結案都不清空。
-- last_verify_failed_at 也是完修照片分輪次的界線。舊列一律 NULL。
ALTER TABLE repair_tickets
    ADD COLUMN last_verify_failed_reason text,        -- 最近一次驗收不通過的原因
    ADD COLUMN last_verify_failed_by     uuid,        -- 最近一次判不通過的主管
    ADD COLUMN last_verify_failed_at     timestamptz; -- 最近一次判不通過的時間

ALTER TABLE repair_tickets
    ADD CONSTRAINT fk_repair_tickets_last_verify_failed_by FOREIGN KEY (tenant_id, last_verify_failed_by)
        REFERENCES staff_accounts (tenant_id, id) ON DELETE RESTRICT ON UPDATE RESTRICT;

-- 舊完修單的 resolution_type 不回填：沒有可信的結果值可查，待驗收查詢用 IS DISTINCT FROM 容納 NULL。
ALTER TABLE repair_tickets DROP CONSTRAINT chk_repair_tickets_resolution_type;
ALTER TABLE repair_tickets ADD CONSTRAINT chk_repair_tickets_resolution_type
    CHECK (resolution_type IS NULL
           OR resolution_type IN ('fixed','temporary','cannot_fix','no_action_needed'));

-- 3) notifications 與 outbox ----------------------------------------------
ALTER TABLE notifications DROP CONSTRAINT chk_notifications_event;

-- 「主管未核准」的通知對應的狀態已不存在，留著點開會找不到去處；通知清單本來就只保留三十天。
DELETE FROM notifications WHERE event = 'repair_rejected';
-- 還沒送出的同類推播一併刪掉，避免收回之後才送到手機上。
DELETE FROM outbox
 WHERE kind = 'push' AND status <> 'sent' AND payload ->> 'event' = 'repair_rejected';

ALTER TABLE notifications ADD CONSTRAINT chk_notifications_event
    CHECK (event IN ('pool_new_request','inspection_passed','inspection_failed',
                     'spot_check_failed','task_cancelled','reassigned_away',
                     'reassigned_to_me','shift_end_returned','guest_withdrew',
                     'leave_takeover','repair_completed','repair_urgent',
                     'repair_verification_failed','kb_reply','kb_unhelpful',
                     'shift_end_still_interrupted',
                     'unclaimed_reminder_staff','unclaimed_reminder_manager'));
