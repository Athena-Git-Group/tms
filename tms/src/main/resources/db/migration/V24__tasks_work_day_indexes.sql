-- 櫃檯任務清單依「工作日」查一段日期範圍。既有索引都以 dept、assignee_id、room_id 或
-- reservation_id 開頭，只給 tenant_id 時只能掃整個租戶的任務再逐列過濾；一年約五萬筆，
-- 每次打開清單都會讀遍。工作日的定義分兩種，所以建兩個索引：
-- 有 work_date 的單（打掃）直接依 work_date 查。
CREATE INDEX idx_tasks_work_date ON tasks (tenant_id, work_date);
-- 沒有 work_date 的單（備品、其他、修繕）另建 partial index，只收這些列。依狀態分兩路：還沒結束的單用前兩欄找出，
-- 已結束或已取消的單再依完成時間（取消的單用最後異動時間）落在範圍內查。
-- 運算式必須和查詢裡的 COALESCE(done_at, updated_at) 一字不差，planner 才認得出來。
CREATE INDEX idx_tasks_undated_state_ended_at ON tasks (tenant_id, state, (COALESCE(done_at, updated_at)))
    WHERE work_date IS NULL;
