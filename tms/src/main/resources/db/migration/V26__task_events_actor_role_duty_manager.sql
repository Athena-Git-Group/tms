-- V26：值班主管的操作要記得進歷程；櫃檯的「今天派了幾張」要查得快。

-- 1) actor_role 認得 duty_manager ----------------------------------------
-- 值班主管會在櫃檯畫面派單、復原、當場回答，事件記的是操作者當下的角色，不借用 manager；
-- 資料庫不認得這個值的話，這三種操作會在寫事件那一步失敗。
-- CHECK 的內容不能就地修改，只能拿掉再用同一個名字加回來。
-- 拿掉與加回寫在同一句，這一欄沒有任何一刻是不受限制的。
-- 加回時資料庫會把整張表檢查一遍；表裡只會有 V1 允許的六個值，它們都還在清單裡，所以檢查不會因為既有資料而失敗。
ALTER TABLE task_events
    DROP CONSTRAINT chk_task_events_actor_role,
    ADD CONSTRAINT chk_task_events_actor_role
        CHECK (actor_role IN ('guest','frontdesk','housekeeping','engineering','manager','duty_manager','system'));

-- 2) 依操作者與時間查事件的索引 ------------------------------------------
-- 櫃檯的今日已派單與當場回答張數都是從事件現算的，不另外存計數，
-- 因為計數欄位要在復原時減回、跨日歸零，會和事件變成兩份數字。
-- 這張表其他的索引都不是以操作者開頭，少了這一個，每次算張數都得掃過整家飯店的事件。
-- 存取樣式：某位員工在一段時間內做過的事
CREATE INDEX idx_task_events_actor_created_at ON task_events (tenant_id, actor_id, created_at);
