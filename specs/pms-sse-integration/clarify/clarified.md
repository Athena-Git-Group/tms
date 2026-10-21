STATUS: RESOLVED

# clarified · pms-sse-integration（target: backend）

score 的 5 項缺口均屬「需求自己點名要在 spec 定案」的工程實作細節——不改變業務目標
（PMS 改以出站訂閱取得房況）、角色（唯一新角色是 M2M 整合方）、主流程（查房通過→
PMS 得知 inspected）或驗收方向。依 headless 執行指示，逐項以 codebase 慣例為錨
採保守預設、標 `Assumption`，不升級 questions.md。`clarify/answers.md` 不存在。

## 業務目標

`tms.pms-status` Kafka topic 從未有 consumer（PMS 在內網、不接 Kafka）。改成
「對方出站訂閱」：PMS 主動以 HTTPS 連 TMS 的整合 API 拉取/訂閱房況變化，TMS
退場整條 pms_status outbox→Kafka 鏈。

## 角色

- **PMS 整合客戶端（M2M）**：持 per-租戶 API key，可呼叫 `/integration/pms/**`
  兩個唯讀端點。無人類使用者介面。
- **營運（provisioning）**：Assumption——API key 由營運以 SQL 直接開立
  （比照 tenants「v1 由營運人工開設」），本需求不含管理端點；輪替=開新 key
  再撤舊 key，允許重疊有效窗口。

## 行為（command/query 邊界）

### B1 `GET /integration/pms/events?after=<cursor>&limit=<n>` — cursor 事件查詢
- 輸入：`after`（cursor，可省略=從最早）、`limit`（預設 100，上限 500）。
- 輸出：room_status 事件陣列（schema 見「對外契約」）＋ `next_cursor`。
- 讀 `task_events`（RLS 下限本租戶），只轉換白名單事件種類。

### B2 `GET /integration/pms/stream?cursor=<cursor>` — SSE 整合流
- 建線即回放 cursor 之後的事件（與 B1 同一讀取邏輯），接著轉即時推送。
- 重連用 query param `cursor` 或標準 `Last-Event-ID` header（兩者同義，
  Assumption：同時給時 query param 優先）。
- 沿用 StreamConnectionRegistry；新增整合連線型別。

### B3 認證 — 新 `IntegrationApiKeyAuthFilter`
- 只攔 `/integration/**`；憑證走獨立 header `X-API-Key`（Assumption：不共用
  Authorization Bearer，避免動既有 BearerTokenAuthFilter 的格式分流）。
- tenant_id 一律由 key 推導；client 自帶的任何租戶參數不存在於 API 面。

### B4 退場 pms_status 全鏈（清單見「範圍」）

## 關鍵決策（原 score 缺口 → 定案）

### D1 API key 格式與儲存（缺口 1）
- 明文格式：`tmsik_<tenantId>_<43 字 base64url 隨機>`（32 bytes entropy）。
  比照 GuestSessionTokens「租戶.隨機」的自述租戶設計——filter 先從 key 取
  tenantId 立 TenantContext，RLS 查表才查得到列。分隔用 `_` 不用 `.`，
  且走不同 header，與 guest token / JWT 零混淆。
- 儲存：**SHA-256(全字串) 的 hex** 存 `key_hash`，明文只在開立當下出現一次。
  Assumption：不用 BCrypt——key 是 32-byte 隨機值（非低熵密碼），SHA-256 足以
  防資料庫外洩反推，且 M2M 每請求驗證不能吃 BCrypt 的百毫秒級成本。
- 另存 `key_prefix`（明文前 16 字）供營運肉眼對帳，不參與驗證。
- 撤銷：`revoked_at timestamptz NULL`，非空即失效。不做硬刪（FK RESTRICT 慣例）。

### D2 驗證順序與租戶注入（缺口 2）
順序釘死：①格式解析（前綴＋三段）→ ②取 tenantId → ③ `TenantContext.set` →
④ RLS 下以 key_hash 查 `integration_api_keys` → ⑤驗列存在、`tenant_id` 與
key 自述一致、`revoked_at IS NULL` → ⑥ `establishAuthentication(ROLE_INTEGRATION)`。
任一步失敗：清 TenantContext、回 401、不進授權層。finally 清 ThreadLocal
（比照 BearerTokenAuthFilter）。TenantAwareDataSource 無需改動——它讀
TenantContext，注入順序由「filter 先 set 再查 DB」保證（與 staff 流完全同構）。
SecurityConfig 矩陣加一行：`/integration/**` → `hasRole(INTEGRATION)`；
filter 掛在 AuthorizationFilter 之前（與 bearer filter 同位、互不攔截對方 header）。

### D3 cursor 編碼與 at-least-once 語意（缺口 3）
- cursor = `task_events.id`（bigint IDENTITY）的十進位字串；對 PMS 為不透明字串。
  （V1 註解「id 非訂閱書籤（增量一律走 Kafka）」是本需求要推翻的前提——
  pms 增量改走 HTTP，id 即成書籤；該註解隨本案更新。）
- 亂序提交窗口：IDENTITY 取號先於 commit，id 大者可能先可見。防漏推：
  B1/B2 回放查詢加**3 秒安全遲滯**——`created_at <= now() - interval '3 seconds'`
  （邊界值：事件 created_at = now()-2.9s 不回傳、= now()-3.1s 回傳）。
  查房交易遠短於 3 秒，窗口足夠。
- 語意：**at-least-once**。SSE 即時推送可能與重連回放重疊；PMS 去重鍵 =
  `(task_id, seq)`（與站內 SSE 去重同鍵，envelope 既有欄位）。
- `after` 非法（非十進位、負數）→ 400；指向不存在的 id → 合法（回其後事件）。

### D4 room_status 對外契約與版本策略（缺口 4）
欄位（封閉集合，v1）：

| 欄位 | 型別 | 必填 | 說明 |
|------|------|------|------|
| `schema` | text | ✓ | 固定 `room_status.v1` |
| `event_id` | text | ✓ | cursor（task_events.id 十進位） |
| `room_no` | text | ✓ | PMS 認房號不認 uuid（沿用 pmsStatusPayload 的理由） |
| `hk_state` | text | ✓ | v1 唯一值 `inspected`（值域=白名單對映的右側） |
| `task_id` | uuid 字串 | ✓ | 去重鍵成分 |
| `seq` | int | ✓ | 去重鍵成分 |
| `occurred_at` | ISO-8601 含時區 | ✓ | task_events.created_at |

版本策略：加欄位=向後相容、不升版；改語意/刪欄位/改型別=升 `room_status.v2`，
`schema` 欄位是 PMS 的分流依據。SSE event name 固定 `room_status`，
SSE `id:` 填 `event_id`（餵 Last-Event-ID）。tenant_id **不出現**在 payload——
連線本身已是租戶身分，外送多餘租戶欄位只會誘使對方信任錯的來源。

### D5 整合連線過濾規則（缺口 5）
以**封閉白名單對映**表達（非條件排除）：`INSPECTION_PASSED → hk_state "inspected"`，
v1 僅此一條。TaskEventsSseConsumer 分發時：事件 type 不在對映 → 整合連線
**一律不送**（guest/staff 分發不受影響）。新增事件種類預設不外流，
要外流必須顯式加對映——這就是「規則寫太寬」風險的結構性解。
整合分發需 room_no 與 event_id：路由查詢按 (tenant_id, task_id) join rooms 取
room_no、按 uq_task_events_task_seq 取 task_events.id。

## 範圍（in / out）

**In**：新表 `integration_api_keys`（V3 migration＋RLS＋grant）、
IntegrationApiKeyAuthFilter、SecurityConfig 一行、兩個 `/integration/pms/*` 端點、
StreamConnection 整合型別＋wants 規則、TaskEventsSseConsumer 整合分發、
退場清單（下）、V3 內 `DELETE FROM outbox WHERE kind='pms_status'`
（防 OutboxRelay 對殘留列 topicFor 拋錯；該 topic 從無 consumer，刪除零風險）。

**退場清單（完整）**：
1. `OutboxWriter.enqueuePmsStatus`（方法整個刪；類別保留——push/task_event 續用）
2. `KafkaTopicsProperties`：`pmsStatus` 欄位＋`topicFor` 的 `PMS_STATUS` case
3. `InspectionService`：`outboxWriter.enqueuePmsStatus(...)` 呼叫（約 L128）＋
   `pmsStatusPayload` 方法（約 L257）；OutboxWriter 依賴保留（push 續用）
4. `OutboxKind.PMS_STATUS` 常數＋自 `ALL` 移除
5. `application.properties`：`tms.kafka.topic.pms-status=tms.pms-status` 行
6. 測試：`CucumberSpringConfiguration.java:87` 的 `NewTopic("tms.pms-status",…)`、
   `BackboneThenSteps.java:181-192` 兩個 pms step、
   `InspectionQrThenSteps.java:78+` 的 pms_status step、
   `04-inspection.feature:78` 該 Then 行、
   `13-core-backbone.feature` 的 pms_status 資料列（L81-83、L118-119 場景改用
   task_event/push kind 或刪列）

**Out**：`tms.push` 與 guest/staff SSE 既有行為不動；API key 管理端點（營運 SQL
開立）；rate limiting（Assumption：PMS 內網單一客戶端，v1 不做，記 Risks）；
DB `chk_outbox_kind` CHECK 不收緊（留值無害、改 CHECK 要鎖表）；minibar。

## 邊界（帶觸發值）

- `X-API-Key` 缺失或空 → 401；格式非 `tmsik_<digits>_<base64url>` → 401
- key 正確格式但 hash 查無列 → 401；`revoked_at` 非空（如 2026-10-20T00:00:00Z，早於現在）→ 401
- key 的 tenantId 段=2、但 hash 命中列 tenant_id=1 → 401（自述與列不符）
- 有效 key 打 `/staff/tasks` → 403（矩陣 denyAll；ROLE_INTEGRATION 只開 /integration）
- 員工 JWT 打 `/integration/pms/events` → 403（身分有效、矩陣不放行；非 401）
- `limit=0` 或 `limit=501` → 400；`limit` 省略 → 100
- `after=abc` → 400；`after=-1` → 400；`after=999999999`（不存在）→ 200 空陣列
- 租戶 1 的 key 查詢：租戶 2 的 task_events **零列**（RLS）
- `inspection_failed` 事件 → 整合連線不送、guest/staff 照送
- `inspection_passed` 事件 created_at 距今 2 秒 → events API 不回傳；5 秒 → 回傳
- SSE 重連帶 `cursor=上次 event_id` → 回放該 id 之後的事件，可能與斷線前已收
  事件重疊（at-least-once），PMS 以 (task_id, seq) 去重

## 範例資料

**integration_api_keys**（key_hash 為示意 hex）：

| id | tenant_id | name | key_prefix | key_hash | created_at | revoked_at |
|----|-----------|------|------------|----------|------------|------------|
| ik-1 | 1 | pms-prod | tmsik_1_Ab3dEf5g | 9f86d08…(64 hex) | 2026-10-01T00:00:00Z | NULL |
| ik-2 | 1 | pms-prod-old | tmsik_1_Zz9yXw7v | 60303ae…(64 hex) | 2026-06-01T00:00:00Z | 2026-10-02T00:00:00Z |
| ik-3 | 2 | pms-prod | tmsik_2_Qq8rSt6u | fd61a03…(64 hex) | 2026-10-05T00:00:00Z | NULL |

**room_status 事件（v1 wire 範例）**：

| event_id | room_no | hk_state | task_id | seq | occurred_at |
|----------|---------|----------|---------|-----|-------------|
| 10234 | 305 | inspected | 018f…a1 | 7 | 2026-10-20T08:12:00+08:00 |
| 10257 | 301 | inspected | 018f…b2 | 5 | 2026-10-20T09:30:00+08:00 |
| 10311 | 512 | inspected | 018f…c3 | 9 | 2026-10-20T11:02:00+08:00 |

**白名單對映**：

| task_events.type | 外流 | hk_state |
|------------------|------|----------|
| inspection_passed | ✓ | inspected |
| inspection_failed | ✗ | — |
| completed | ✗ | — |

## 完成判準自檢

通用三條達成（行為有輸入/輸出/規則、術語單義——cursor/room_status/integration key
全文一致、無阻斷矛盾）。資料維度：★四條達成（唯一性=key_hash UNIQUE＋
(tenant_id,name) 不設唯一【Assumption：同名多 key 合法，輪替重疊期必然同名】；
欄位語意與封閉值域如上；範例資料各 3 筆）。量級：task_events 既有表、
API key 每租戶個位數——無分區需求。PII：無（key_hash 非 PII；room_status 不含住客資料）。
保存：key 不硬刪；task_events 既有保留政策不變。
