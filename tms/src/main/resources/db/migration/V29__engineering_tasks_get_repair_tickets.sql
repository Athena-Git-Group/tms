-- 部門是工務的單一律是工務單（任務恰好一張 repair_tickets 列、類型 repair）。
-- 這版之前有兩條路會把單放進工務卻不開工務單：櫃檯把待派單派給工務，以及住客打字命中工務的分類規則直接進工務待認領。
-- 這支遷移只補還沒結束的單；已結束（done、inspected、closed、cancelled）的歷史單不動。
-- 只認得上面兩種，其他來歷的單一張都不猜，整支遷移以例外停下，什麼都不改。

-- 櫃檯派來的單要有派單的人，因為補出的工務單以他為報修人；住客直接進來的單只可能還在待認領，
-- 一旦有人認領或開工，退回待派單會把正在做的工作收走，所以不在此列，交給下面的檢查停下。
CREATE TEMP TABLE v29_missing ON COMMIT DROP AS
SELECT t.tenant_id, t.id, t.source, t.state, t.task_no, t.room_id, t.created_at, t.event_seq,
       last_triaged.dept       AS triaged_dept,
       last_triaged.actor_id   AS triaged_by,
       last_triaged.created_at AS triaged_at,
       CASE
           WHEN last_triaged.dept = 'engineering' AND last_triaged.actor_id IS NOT NULL
                AND t.state IN ('pool', 'accepted', 'doing', 'deferred', 'interrupted')
               THEN 'desk_forwarded'
           WHEN t.source = 'guest' AND last_triaged.seq IS NULL AND t.state = 'pool'
               THEN 'guest_direct'
       END AS kind
  FROM tasks t
  LEFT JOIN LATERAL (
        SELECT e.seq, e.payload ->> 'dept' AS dept, e.actor_id, e.created_at
          FROM task_events e
         WHERE e.tenant_id = t.tenant_id AND e.task_id = t.id AND e.type = 'triaged'
         ORDER BY e.seq DESC
         LIMIT 1
       ) last_triaged ON true
 WHERE t.dept = 'engineering'
   AND t.state NOT IN ('done', 'inspected', 'closed', 'cancelled')
   AND NOT EXISTS (SELECT 1 FROM repair_tickets rt WHERE rt.tenant_id = t.tenant_id AND rt.task_id = t.id);

DO $$
DECLARE
    unknown_count int;
    unknown_sample text;
BEGIN
    SELECT count(*) INTO unknown_count FROM v29_missing WHERE kind IS NULL;
    SELECT string_agg(id::text || '(source=' || source || ', state=' || state
                      || ', 最近派單部門=' || COALESCE(triaged_dept, '無') || ')', ', ')
      INTO unknown_sample
      FROM (SELECT * FROM v29_missing WHERE kind IS NULL ORDER BY created_at, id LIMIT 10) sample;
    IF unknown_count > 0 THEN
        RAISE EXCEPTION 'V29: % 張部門是工務、沒有工務單的單不是櫃檯派來的、也不是住客經規則直接進來的，前幾張：%',
            unknown_count, unknown_sample;
    END IF;
END $$;

-- 住客經規則直接進來的單退回待派單，而不是補成工務單：工務單要有報修人，住客不是員工；
-- 照現在的規則，這種單要由櫃檯派給工務時才開工務單。
-- 退回寫成一筆 triage_undone（系統所為），讓事件重放、Kafka 與員工事件串流都看得到這張單離開工務待認領。
-- 這種單多半是類型 repair、建單時沒取 T- 號，照建單時的順序接著各飯店的流水號補號；
-- 計數列不在或落後已用掉的號碼時，從已用掉的最大號接下去，不撞號。
CREATE TEMP TABLE v29_guest_direct ON COMMIT DROP AS
SELECT m.tenant_id, m.id, m.event_seq + 1 AS seq,
       COALESCE(m.task_no,
                'T-' || lpad(n.no::text, greatest(4, length(n.no::text)), '0')) AS task_no
  FROM v29_missing m
  LEFT JOIN (
        SELECT g.tenant_id, g.id,
               greatest(COALESCE(c.last_no, 0), COALESCE(used.last_no, 0))
                   + row_number() OVER (PARTITION BY g.tenant_id ORDER BY g.created_at, g.id) AS no
          FROM v29_missing g
          LEFT JOIN task_counters c ON c.tenant_id = g.tenant_id
          LEFT JOIN (SELECT tenant_id, max(substring(task_no FROM 3)::int) AS last_no
                       FROM tasks WHERE task_no ~ '^T-[0-9]+$' GROUP BY tenant_id) used
                 ON used.tenant_id = g.tenant_id
         WHERE g.kind = 'guest_direct' AND g.task_no IS NULL
       ) n ON n.tenant_id = m.tenant_id AND n.id = m.id
 WHERE m.kind = 'guest_direct';

INSERT INTO task_counters (tenant_id, last_no)
SELECT g.tenant_id, max(substring(g.task_no FROM 3)::int)
  FROM v29_guest_direct g
 GROUP BY g.tenant_id
ON CONFLICT (tenant_id) DO UPDATE SET last_no = greatest(task_counters.last_no, EXCLUDED.last_no);

UPDATE tasks t
   SET state = 'triage', dept = 'frontdesk', type = 'other', task_no = g.task_no,
       pool_source = NULL, reminder_stage = NULL, priority_notify = false,
       event_seq = g.seq, updated_at = now()
  FROM v29_guest_direct g
 WHERE t.tenant_id = g.tenant_id AND t.id = g.id;

INSERT INTO task_events (tenant_id, task_id, seq, type, actor_role, actor_id, payload, created_at)
SELECT g.tenant_id, g.id, g.seq, 'triage_undone', 'system', NULL,
       jsonb_build_object('dept', 'engineering', 'type', 'other', 'task_no', g.task_no,
                          'reason', 'guest_not_direct_backfill'),
       now()
  FROM v29_guest_direct g;

INSERT INTO outbox (tenant_id, aggregate_key, kind, payload, idempotency_key, created_at)
SELECT e.tenant_id, 'task:' || e.task_id, 'task_event',
       jsonb_build_object('tenant_id', e.tenant_id, 'task_id', e.task_id::text, 'seq', e.seq, 'type', e.type,
                          'actor_role', e.actor_role, 'payload', e.payload,
                          'occurred_at', to_char(e.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
       'task:' || e.task_id || ':seq:' || e.seq,
       now()
  FROM task_events e
  JOIN v29_guest_direct g ON g.tenant_id = e.tenant_id AND g.id = e.task_id AND g.seq = e.seq;

-- 櫃檯派來的單補一張工務單，照派單的先後接著各飯店的工務單流水號取號。
-- 不另寫事件：那筆 triaged 重放時本來就把類型設成 repair，任務列補上 repair 之後就跟重放一致。
CREATE TEMP TABLE v29_desk_forwarded ON COMMIT DROP AS
SELECT m.tenant_id, m.id, m.room_id, m.triaged_by, m.triaged_at,
       greatest(COALESCE(c.last_no, 0), COALESCE(used.last_no, 0))
           + row_number() OVER (PARTITION BY m.tenant_id ORDER BY m.triaged_at, m.id) AS no
  FROM v29_missing m
  LEFT JOIN repair_ticket_counters c ON c.tenant_id = m.tenant_id
  LEFT JOIN (SELECT tenant_id, max(substring(ticket_no FROM 3)::int) AS last_no
               FROM repair_tickets WHERE ticket_no ~ '^W-[0-9]+$' GROUP BY tenant_id) used
         ON used.tenant_id = m.tenant_id
 WHERE m.kind = 'desk_forwarded';

UPDATE tasks t
   SET type = 'repair', updated_at = now()
  FROM v29_desk_forwarded d
 WHERE t.tenant_id = d.tenant_id AND t.id = d.id;

INSERT INTO repair_tickets (task_id, tenant_id, ticket_no, room_id, category, reporter_id, created_at, is_urgent)
SELECT d.id, d.tenant_id, 'W-' || lpad(d.no::text, greatest(4, length(d.no::text)), '0'), d.room_id, 'other',
       d.triaged_by, d.triaged_at, false
  FROM v29_desk_forwarded d;

INSERT INTO repair_ticket_counters (tenant_id, last_no)
SELECT d.tenant_id, max(d.no)
  FROM v29_desk_forwarded d
 GROUP BY d.tenant_id
ON CONFLICT (tenant_id) DO UPDATE SET last_no = greatest(repair_ticket_counters.last_no, EXCLUDED.last_no);

DO $$
DECLARE
    remaining int;
BEGIN
    SELECT count(*) INTO remaining
      FROM tasks t
     WHERE t.dept = 'engineering'
       AND t.state NOT IN ('done', 'inspected', 'closed', 'cancelled')
       AND NOT EXISTS (SELECT 1 FROM repair_tickets rt WHERE rt.tenant_id = t.tenant_id AND rt.task_id = t.id);
    IF remaining > 0 THEN
        RAISE EXCEPTION 'V29: 修補後仍有 % 張部門是工務、沒結束、沒有工務單的單', remaining;
    END IF;
END $$;
