-- OutboxRelay 撈 pending 時用 NOT EXISTS 排除「同 aggregate_key 已有 failed」的鍵。
-- failed 沒有索引可用時，這個探測會退化成全表掃描（含保留 3 天的 sent 列）；
-- failed 幾乎永遠是零筆，partial index 近乎空、寫入成本趨近零，讓 anti-join 走空索引探測。
CREATE INDEX idx_outbox_failed_by_key ON outbox (aggregate_key) WHERE status = 'failed';
