-- relay 一次只服務一家飯店，認領時照 id 由舊到新掃這家飯店的 pending。
-- INCLUDE 讓掃描只讀索引就知道每筆屬於哪個隊伍，被擋住的隊伍不必回表。
CREATE INDEX idx_outbox_pending_by_tenant
    ON outbox (tenant_id, id) INCLUDE (kind, aggregate_key) WHERE status = 'pending';

-- 隊伍最前面那筆正在送或在等重試時，整個隊伍先不動。只有隊伍最前面那筆會帶 next_retry_at，所以這個索引一直很小。
CREATE INDEX idx_outbox_pending_claimed_by_tenant
    ON outbox (tenant_id, kind, aggregate_key) WHERE status = 'pending' AND next_retry_at IS NOT NULL;

-- 重試用完的隊伍整隊停送。照飯店分開建，一家飯店的 failed 列不會拖慢別家飯店的認領。
CREATE INDEX idx_outbox_failed_by_tenant
    ON outbox (tenant_id, kind, aggregate_key) WHERE status = 'failed';
DROP INDEX idx_outbox_failed_by_key;

-- relay 的定時補漏只需要知道哪幾家飯店有到期的 pending，不需要讀別家的內容。
-- 函式以擁有者身分執行、只回傳飯店編號，應用帳號不必繞過 RLS 就能一次問完所有飯店。
CREATE FUNCTION outbox_tenants_with_due_work() RETURNS TABLE (tenant_id bigint)
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
    SELECT DISTINCT o.tenant_id
      FROM outbox o
     WHERE o.status = 'pending'
       AND (o.next_retry_at IS NULL OR o.next_retry_at <= now())
$$;
REVOKE ALL ON FUNCTION outbox_tenants_with_due_work() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION outbox_tenants_with_due_work() TO tms_app;
