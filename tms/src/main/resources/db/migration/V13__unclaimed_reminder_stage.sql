-- V13：裁決 B 未接提醒兩段模型的資料側落點（plan 001-staff-app-increment /dsl-refine，T205）。
-- tasks 新增 reminder_stage：排程依 V12 的 tenant_config.unclaimed_reminder_minutes 掃描
-- state=pool 任務，到門檻寫入該階段並各發一次推播，離開 pool（認領/改派/撤回等）時清空為
-- NULL。只記「目前到哪個階段」，不另開新表或新 task_events 型別——既有 outbox.kind=push
-- 已承載推播證據。對應 contracts PoolItem.reminderStage（見 data-model.dbml Table tasks note）。
ALTER TABLE tasks ADD COLUMN reminder_stage text
    CONSTRAINT chk_tasks_reminder_stage CHECK (reminder_stage IN ('staff_reminded','manager_notified'));

-- 待認領排序鍵調整：reminder_stage 併入既有池排序索引（原 V5 的
-- idx_tasks_pool_order），排序以「是否為 NULL」排最前，其餘維持既有排序欄序。
DROP INDEX idx_tasks_pool_order;
CREATE INDEX idx_tasks_pool_order ON tasks (tenant_id, dept, state, reminder_stage, urgent, priority_notify);
