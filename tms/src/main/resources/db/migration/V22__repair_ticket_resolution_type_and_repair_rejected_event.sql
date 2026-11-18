-- V22：主管簽核佇列（待查房／待審核／待驗收）需要的兩個資料側補強。
--   1) repair_tickets.resolution_type：完修結果三選一（fixed／temporary／cannot_fix）。
--      暫時處理會自動開追蹤單、無法修復會把房轉 out_of_order，這些副作用早已落地，
--      但結果本身只留在 task_events.completed 的 payload 裡；待驗收清單每列都要顯示它，
--      逐單翻 JSON 不划算，改成與 resolution_note、verified_by 同一種風格落在本表。
--      尚未完修（tasks.state 不是 done）為 NULL；完修時由應用層在同一交易寫入，
--      省略時寫 fixed，之後不改。不加索引：待驗收清單以 task_id 主鍵 JOIN 取它，不拿它篩選或排序。
--   2) notifications.event 加第十八個值 repair_rejected：主管退回等審核的房務報修一般件時
--      推播報修人，通知中心靠這個值分類圖示；值域照 contracts shared.yaml 的 PushPayload.event。
--
-- 既有資料列不回填：舊的完修單沒有可信的結果值可查，示範資料由 seed 自己補。
ALTER TABLE repair_tickets
    ADD COLUMN resolution_type text; -- 完修結果；尚未完修為 NULL

ALTER TABLE repair_tickets
    ADD CONSTRAINT chk_repair_tickets_resolution_type
    CHECK (resolution_type IS NULL OR resolution_type IN ('fixed','temporary','cannot_fix'));

ALTER TABLE notifications DROP CONSTRAINT chk_notifications_event;
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_event
    CHECK (event IN ('pool_new_request','inspection_passed','inspection_failed',
                     'spot_check_failed','task_cancelled','reassigned_away',
                     'reassigned_to_me','shift_end_returned','guest_withdrew',
                     'leave_takeover','repair_completed','repair_urgent',
                     'repair_rejected','kb_reply','kb_unhelpful',
                     'shift_end_still_interrupted',
                     'unclaimed_reminder_staff','unclaimed_reminder_manager'));
