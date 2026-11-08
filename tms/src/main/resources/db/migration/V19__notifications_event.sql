-- 通知中心的分類圖示要分得出「急件、超時、完成、交辦、新單、保養、一般」，
-- 靠的是這筆通知當初是從哪一個推播事件落地的。V16 建表時沒把事件值存下來，
-- 通知清單因此只有標題、內文、去處與時間，分不出類別——App 只能把每一則都
-- 畫成同一種樣子。這裡補上 event 欄位，值域與寫法比照 contracts shared.yaml
-- 的 PushPayload.event，不另立一套。
--
-- 既有列的處理：通知是 outbox kind=push 展開一人一封時同交易寫下的，所以
-- 同一筆推播的事件值還留在 outbox 的 payload 裡——先照「同租戶、同收件人、
-- 同標題」把事件值接回來，這是唯一有根據的回填來源，不是猜的。接不回來的
-- 列（只有測試資料與手動造的資料會這樣）沒有任何可信的事件值可填，與其
-- 替它編一個會在畫面上畫錯顏色的值，不如刪掉：通知本來就是 retention
-- （notification_retention_days，預設 30 天）會定期清掉的短期紀錄，不是帳本。

ALTER TABLE notifications ADD COLUMN event text;

-- 回填：outbox 的 push payload 帶 recipientStaffId／title／event 三個欄位，
-- 與 notifications 的收件人、標題一一對應。同一組條件撈到多列時取最早那筆
-- （同一個人、同一個標題的重複推播本來就被冪等鍵擋掉，實務上只會有一列）。
--
-- 三個舊事件名在回填時順手正規化成契約的寫法：寫入端當時用的
-- repair_resolved／shift_end_interrupted／returned_to_pool 不在
-- PushPayload.event 的值域裡，對應的契約值分別是 repair_completed（工務完修）、
-- shift_end_still_interrupted（班末仍中斷）、pool_new_request（池裡多了一張單）。
UPDATE notifications n
   SET event = CASE src.event
                   WHEN 'repair_resolved' THEN 'repair_completed'
                   WHEN 'shift_end_interrupted' THEN 'shift_end_still_interrupted'
                   WHEN 'returned_to_pool' THEN 'pool_new_request'
                   ELSE src.event
               END
  FROM (
        SELECT DISTINCT ON (o.tenant_id, o.payload ->> 'recipientStaffId', o.payload ->> 'title')
               o.tenant_id,
               o.payload ->> 'recipientStaffId' AS recipient_staff_id,
               o.payload ->> 'title'            AS title,
               o.payload ->> 'event'            AS event
          FROM outbox o
         WHERE o.kind = 'push' AND o.payload ->> 'event' IS NOT NULL
         ORDER BY o.tenant_id, o.payload ->> 'recipientStaffId', o.payload ->> 'title', o.id
       ) src
 WHERE src.tenant_id = n.tenant_id
   AND src.recipient_staff_id = n.recipient_staff_id::text
   AND src.title = n.title;

DELETE FROM notifications WHERE event IS NULL;

ALTER TABLE notifications ALTER COLUMN event SET NOT NULL;

-- 值域照 contracts shared.yaml 的 PushPayload.event 十七種事件值。鎖在資料層
-- 是因為 App 的分類圖示直接對這個值分色：值跑掉時寧可在寫入當下就失敗，
-- 也不要等到員工看到一片灰鈴鐺才發現送進來的是沒見過的事件名。
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_event
    CHECK (event IN ('pool_new_request','inspection_passed','inspection_failed',
                     'spot_check_failed','task_cancelled','reassigned_away',
                     'reassigned_to_me','shift_end_returned','guest_withdrew',
                     'leave_takeover','repair_completed','repair_urgent',
                     'kb_reply','kb_unhelpful','shift_end_still_interrupted',
                     'unclaimed_reminder_staff','unclaimed_reminder_manager'));
