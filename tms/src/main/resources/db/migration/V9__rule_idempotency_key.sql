-- 自動化規則建單的冪等鍵（data-model.dbml Table automation_rules Note：
-- 冪等鍵＝房號｜規則｜當地日期；排程規則沒有房號，用 規則｜當地日期）。
-- 同一天對同一房、同一規則重複比對（重拉房態、匯入 CSV、停機補建）時，
-- 唯一索引保證資料庫層最多只會留下一張工單，先查再建的競態也會被擋下。
ALTER TABLE tasks ADD COLUMN rule_idem_key text;
CREATE UNIQUE INDEX uq_tasks_rule_idem_key
    ON tasks (tenant_id, rule_idem_key)
 WHERE rule_idem_key IS NOT NULL;
