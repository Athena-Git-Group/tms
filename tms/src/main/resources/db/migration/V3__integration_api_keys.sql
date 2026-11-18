-- V3：PMS 整合的持久化增量。
-- 1) 新表 integration_api_keys——租戶簽發給 PMS 的長效 M2M 憑證，只存 hash
-- 2) RLS policy 與 tms_app grant（沿用 V1 既有 16 表的 tenant_isolation 模式）
-- 3) 清 outbox 殘留的 pms_status 列（該 kind 自本版起不再產生，topic 從無 consumer）
-- 4) 更新 task_events.id 的欄位註解（自本版起兼任整合 cursor）

-- API key 明文格式 tmsik_<tenantId>_<43 字 base64url>，只在開立當下出現一次、
-- 不落庫；庫裡只存 SHA-256 全字串 hash（資料庫外洩不可反推明文）與前 16 字
-- 的 key_prefix（僅供營運肉眼對帳，不參與驗證）。
-- 生命週期兩態單向：active（revoked_at IS NULL）→ revoked（落印 revoked_at），
-- 不可逆、不復活；撤銷列不硬刪，永久保留當稽核軌跡。
-- 無競態更新面（撤銷是冪等的單欄落印），故不設 updated_at / version。
CREATE TABLE integration_api_keys (
    id         uuid        PRIMARY KEY, -- 程式產 UUIDv7（團隊代理鍵慣例）
    tenant_id  bigint      NOT NULL,
    name       text        NOT NULL,    -- 營運命名；同租戶可重名（輪替重疊期新舊 key 必然同名），不設 (tenant_id, name) 唯一
    key_prefix text        NOT NULL,
    key_hash   text        NOT NULL,    -- SHA-256(明文全字串) 的 64 字 hex；唯一熱查詢鍵
    created_at timestamptz NOT NULL,
    revoked_at timestamptz,             -- NULL = active；非空 = revoked
    CONSTRAINT fk_integration_api_keys_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
-- key_hash 全域唯一，UNIQUE 索引同時就是唯一熱查詢索引：WHERE key_hash = ?（每請求一次）
CREATE UNIQUE INDEX uq_integration_api_keys_hash ON integration_api_keys (key_hash);
-- 營運盤點用；總量 < 100，聊備
CREATE INDEX idx_integration_api_keys_tenant ON integration_api_keys (tenant_id);

-- 應用角色只驗證（SELECT）與撤銷（UPDATE 單欄 revoked_at）；
-- 開立 INSERT 由營運以 admin 角色執行，應用不給
GRANT SELECT, UPDATE (revoked_at) ON integration_api_keys TO tms_app;

-- RLS：本表不豁免——驗證 filter 先由 key 明文自述段取 tenant 建 session 變數，
-- 之後才查得到本表的列（與 V1 既有表同一道牆）
ALTER TABLE integration_api_keys ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_integration_api_keys ON integration_api_keys
    USING (tenant_id = current_setting('app.tenant_id', true)::bigint);

-- 房況改走整合 API 拉取/串流，outbox 不再寫 pms_status：清掉殘留列，
-- 防中繼程式對未知 kind 取 topic 時拋錯。chk_outbox_kind CHECK 刻意不收緊
-- ——留值無害，改 CHECK 會鎖表
DELETE FROM outbox WHERE kind = 'pms_status';

-- 推翻 V1 建表時「id 非訂閱書籤」的行內註解：id 自本版起兼任整合 cursor，
-- 對外是不透明的十進位字串，增量讀取樣式 WHERE id > :after ORDER BY id ASC
COMMENT ON COLUMN task_events.id IS
    '整合 cursor：PMS 增量拉取以 id 為書籤（對外為不透明十進位字串）';
