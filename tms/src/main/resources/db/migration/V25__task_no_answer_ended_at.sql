-- V25：櫃檯看板要用到的三個任務欄位，以及單號的取號表。
--   1) task_counters：非工務單的單號序號，每家飯店一列。
--   2) tasks.task_no：給人看、電話裡唸得出來的單號（T- 加 4 位數）。工務單不領，它的單號只有 repair_tickets.ticket_no。
--   3) tasks.answer：櫃檯當場回答給住客的回覆全文。有值就代表這張單是當場回答結案的。
--   4) tasks.ended_at：這張單走到終點的時間；還沒結束是 NULL。
--      櫃檯看板列出「還沒結束的單」加上「今天結束的單」，靠的就是這一欄。
--
-- 既有的單必須在這一支裡補齊，因為讀這三欄的程式不分新單舊單：
-- 看板把 ended_at 是 NULL 的單一律當成還沒結束，舊單沒補就會全部出現在看板上；
-- 詳情面板直接顯示 task_no，看板用 answer 有沒有值分辨「已回答」與一般的完成。
-- 每一段都是先加可為空的欄位、補資料、最後才加索引。
-- 整支在同一個交易裡執行，中途失敗時什麼都不會留下，排除原因後可以原樣重跑。

-- 1) 單號序號表 ----------------------------------------------------------
-- 與 repair_ticket_counters 各自計數、互不影響。
-- 取號：INSERT … VALUES (tenant_id, 1) ON CONFLICT (tenant_id) DO UPDATE SET last_no = last_no + 1 RETURNING last_no，
-- 一句完成、與建單同交易；還沒有列的飯店第一次取號時自動建出第一列並拿到 1 號。
CREATE TABLE task_counters (
    tenant_id bigint PRIMARY KEY,
    last_no   int    NOT NULL DEFAULT 0,
    CONSTRAINT fk_task_counters_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

GRANT SELECT, INSERT, UPDATE, DELETE ON task_counters TO tms_app;

ALTER TABLE task_counters ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_task_counters ON task_counters USING (tenant_id = current_setting('app.tenant_id', true)::bigint);

-- 2) 三個新欄位，先都可為空 ----------------------------------------------
ALTER TABLE tasks
    ADD COLUMN task_no  text,        -- 非工務單的單號，例如 T-1040；建單時取號，寫入後不改。type=repair 為 NULL
    ADD COLUMN answer   text,        -- 櫃檯當場回答的回覆全文（1–120 字）；其他單一律 NULL。個資去識別化範圍
    ADD COLUMN ended_at timestamptz; -- 走到終點的時間；還沒結束為 NULL。抽查退回時清空

-- 3) 替既有的非工務單補單號 ----------------------------------------------
-- 每家飯店各自從 1 號編起，依建立時間由舊到新；建立時間相同的再依 id，順序才固定。
-- 不足 4 位數補零，超過 9999 照實際位數。工務單維持 NULL。
WITH numbered AS (
    SELECT tenant_id, id,
           row_number() OVER (PARTITION BY tenant_id ORDER BY created_at, id) AS no
      FROM tasks
     WHERE type <> 'repair'
)
UPDATE tasks t
   SET task_no = 'T-' || lpad(n.no::text, greatest(4, length(n.no::text)), '0')
  FROM numbered n
 WHERE n.tenant_id = t.tenant_id AND n.id = t.id;

-- 序號接在各家飯店補到的最後一號之後，之後新建的單才不會和舊單撞號。
-- 沒有非工務單的飯店不建列，第一次取號時自己會建。
INSERT INTO task_counters (tenant_id, last_no)
SELECT tenant_id, count(*)
  FROM tasks
 WHERE type <> 'repair'
 GROUP BY tenant_id;

-- 單號在同一家飯店內不重複；工務單的 NULL 不受限。
CREATE UNIQUE INDEX uq_tasks_tenant_task_no ON tasks (tenant_id, task_no);

-- 4) 替既有的當場回答單補回覆 --------------------------------------------
-- 回覆的原始紀錄在 answered 事件的 payload.answer。
-- 這些舊單的 note 當時被回覆蓋掉了，這裡不還原：住客原文要看 created 事件的建立快照。
UPDATE tasks t
   SET answer = a.answer
  FROM (
        SELECT DISTINCT ON (tenant_id, task_id)
               tenant_id, task_id, payload ->> 'answer' AS answer
          FROM task_events
         WHERE type = 'answered'
         ORDER BY tenant_id, task_id, seq DESC
       ) a
 WHERE a.tenant_id = t.tenant_id AND a.task_id = t.id;

-- 5) 替既有已結束的單補結束時間 ------------------------------------------
-- 哪些單算已經結束：
--   inspected（查房通過或免查結案）、cancelled（取消）、closed（工務單結案）；
--   done 只有「完成就結束」的才算：type 是 amenity 或 other，或是當場回答的單（有 answered 事件）。
--   打掃單與工務單的 done 還在等查房或驗收，不補。
-- 時間取哪一個：
--   inspected、cancelled、closed 取進入該狀態那一筆事件的時間。
--     inspected：查房通過（inspection_passed）；免查結案的單是完成（completed）當下直接結束，沒有查房事件。
--     cancelled：cancelled。
--     closed：主管驗收通過（repair_verified）；判斷不需處理的單是完成（completed）當下直接結案。
--     同一張單可能進出不只一次（抽查退回後重做再通過），所以取 seq 最大的那一筆。
--   done 取 done_at；沒有 done_at 時取完成或當場回答那一筆事件的時間。
-- 找不到對應事件的舊單（例如直接寫進資料庫的示範資料）用 updated_at 代替：
-- 留成 NULL 的話，它會被當成還沒結束，永遠掛在看板上。
UPDATE tasks t
   SET ended_at = COALESCE(
           CASE WHEN t.state = 'done' THEN t.done_at END,
           (SELECT e.created_at
              FROM task_events e
             WHERE e.tenant_id = t.tenant_id AND e.task_id = t.id
               AND e.type = ANY (CASE t.state
                                     WHEN 'inspected' THEN ARRAY['inspection_passed', 'inspection_waived', 'completed']
                                     WHEN 'cancelled' THEN ARRAY['cancelled']
                                     WHEN 'closed'    THEN ARRAY['repair_verified', 'completed']
                                     ELSE                  ARRAY['completed', 'answered']
                                 END)
             ORDER BY e.seq DESC
             LIMIT 1),
           t.updated_at)
 WHERE t.state IN ('inspected', 'cancelled', 'closed')
    OR (t.state = 'done'
        AND (t.type IN ('amenity', 'other')
             OR EXISTS (SELECT 1
                          FROM task_events a
                         WHERE a.tenant_id = t.tenant_id AND a.task_id = t.id
                           AND a.type = 'answered')));

-- 存取樣式：櫃檯看板（還沒結束的單，加上今天結束的單）
CREATE INDEX idx_tasks_ended_at ON tasks (tenant_id, ended_at);
