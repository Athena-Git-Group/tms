STATUS: READY

# spec · pms-sse-integration

把從未有 consumer 的 `tms.pms-status` Kafka 外送鏈，替換成 PMS 出站訂閱的整合 API：
per-租戶 M2M API key 認證 + cursor 事件查詢 + SSE 整合流，並退場舊鏈全部殘件。

## 1. 使用者故事

### 使用者故事 1 — PMS 以 API key 取得整合身分 (Priority: P1)

作為 PMS 整合客戶端（M2M），我希望用租戶專屬的 API key 通過認證，以便在正確的
租戶邊界內呼叫整合 API。

**為何為此優先級**: 其餘故事全部依賴這道認證與租戶注入；安全邊界必須先立。

**獨立驗證方式**: 只實作本故事時，用有效/無效/撤銷/跨租戶 key 打任一 `/integration/**`
路徑，驗 401/403 行為與 TenantContext 注入正確（可用臨時 echo 端點驗）。

**驗收情境**:
1. **Given** 租戶 1 存在未撤銷 key，**When** 以 `X-API-Key: tmsik_1_<正確明文>` 呼叫
   `/integration/pms/events`，**Then** 200 且只見租戶 1 資料。
2. **Given** key 已撤銷（revoked_at 非空），**When** 呼叫同端點，**Then** 401。
3. **Given** 員工 JWT，**When** 打 `/integration/pms/events`，**Then** 403（非 401）。

**功能需求（FR）**:
- **FR-101**: 系統 MUST 新增 `integration_api_keys` 表（V3 migration），含 RLS policy
  （`tenant_id = current_setting('app.tenant_id', true)::bigint`，比照 V1 既有 16 表）與
  `tms_app` grant（SELECT 必要；開立由營運 SQL 以 admin 角色執行）。
- **FR-102**: API key 明文格式 MUST 為 `tmsik_<tenantId>_<43 字 base64url 隨機>`
  （32 bytes entropy）；儲存 MUST 僅存 `key_hash = SHA-256(明文全字串) hex` 與
  `key_prefix`（明文前 16 字，僅供對帳，不參與驗證）；明文只在開立當下出現一次。
- **FR-103**: 新 `IntegrationApiKeyAuthFilter` MUST 只處理 `/integration/**` 請求的
  `X-API-Key` header，驗證順序釘死：①格式解析 → ②取 tenantId → ③ `TenantContext.set`
  → ④ RLS 下以 key_hash 查表 → ⑤驗列存在、列 tenant_id 與 key 自述一致、
  `revoked_at IS NULL` → ⑥ establishAuthentication(`ROLE_INTEGRATION`)。
  任一步失敗：清 TenantContext、回 401（ApiErrorResponses 格式）、不進授權層；
  finally MUST 清 TenantContext / CurrentActor ThreadLocal（比照 BearerTokenAuthFilter）。
- **FR-104**: tenant_id MUST 一律由 key 推導；API 面不存在任何 client 自帶租戶參數。
- **FR-105**: SecurityConfig 矩陣 MUST 新增 `/integration/**` → `hasRole(INTEGRATION)`；
  新 filter 掛在 `AuthorizationFilter` 之前，與 BearerTokenAuthFilter 互不攔截對方 header
  （bearer filter 看不到 X-API-Key、照常放行進 chain）。
- **FR-106**: TenantAwareDataSource MUST 不改動——租戶注入由「filter 先 set 再查 DB」
  保證（與 staff 認證流同構）。
- **FR-107**: 撤銷 MUST 以 `revoked_at` 落印，不硬刪；輪替 = 開新 key 再撤舊 key，
  同租戶多把未撤銷 key MUST 同時有效（重疊窗口）。

**非功能需求（NFR）**:
- **NFR-101**: key 驗證走 SHA-256（非 BCrypt）：高熵隨機值無暴力破解面，
  每請求驗證成本須為 µs 級。

### 使用者故事 2 — PMS 以 cursor 拉取房況事件 (Priority: P1)

作為 PMS 整合客戶端，我希望用 cursor 增量拉取房況變化事件，以便斷線或批次對帳時
不漏任何一間已查房通過的房。

**為何為此優先級**: 這是房況傳遞的基載路徑（SSE 只是即時性加速）；回放邏輯也被
故事 3 複用。

**獨立驗證方式**: 造 inspection_passed 與其他種類的 task_events，打
`GET /integration/pms/events?after=`，驗轉換、過濾、分頁、租戶隔離。

**驗收情境**:
1. **Given** 租戶 1 有 3 筆 inspection_passed 事件（id 10234/10257/10311），
   **When** `GET /integration/pms/events?after=10234`，**Then** 200 回 10257、10311
   兩筆 room_status，`next_cursor` 為 `"10311"`。
2. **Given** 同窗口有 inspection_failed 事件，**When** 拉取，**Then** 該事件不出現。
3. **Given** `after=abc`，**When** 拉取，**Then** 400。

**功能需求（FR）**:
- **FR-201**: `GET /integration/pms/events` MUST 接受 `after`（cursor，可省略=從最早）
  與 `limit`（預設 100、最小 1、最大 500），回傳 room_status 事件陣列＋`next_cursor`
  （本頁最後一筆的 event_id；空頁時回傳入的 `after` 原值，省略 after 的空頁回 `"0"`）。
- **FR-202**: cursor MUST 編碼為 `task_events.id`（bigint）的十進位字串，對 PMS 為
  不透明值；查詢語意 `id > :after ORDER BY id ASC`。
- **FR-203**: 回放查詢 MUST 套 3 秒安全遲滯：只回傳 `created_at <= now() - 3s` 的事件
  （封堵 IDENTITY 取號先於 commit 的亂序可見窗口，防跳號漏推）。
- **FR-204**: 事件轉換 MUST 走封閉白名單對映（v1 僅 `inspection_passed → hk_state
  "inspected"`）；不在對映的 type 一律不出現在整合 API 面。
- **FR-205**: `after` 非十進位或負數 → 400；`limit` 越界（0 或 501）→ 400；
  `after` 指向不存在的 id → 200 合法（回其後事件或空陣列）。
- **FR-206**: 讀取 MUST 在 RLS 租戶邊界內（tenant 來自 key）；另一租戶的事件零列。

### 使用者故事 3 — PMS 以 SSE 即時訂閱房況 (Priority: P2)

作為 PMS 整合客戶端，我希望維持一條 SSE 連線即時收到房況變化、重連時帶 cursor 補齊
斷線期間的事件，以便房況秒級同步且不漏推。

**為何為此優先級**: 即時性是體驗增量；故事 2 的拉取已保證不漏，SSE 建立在其上。

**獨立驗證方式**: 建 SSE 連線 → 觸發查房通過 → 驗收到 room_status 事件；斷線重連帶
cursor → 驗斷線期間事件被回放。

**驗收情境**:
1. **Given** 整合 SSE 連線已建立，**When** 查房通過使任務 inspected，**Then** 連線
   收到 event name `room_status`、`id:` 為 event_id 的事件。
2. **Given** 斷線期間發生 1 筆 inspection_passed，**When** 帶 `cursor=<斷線前最後
   event_id>` 重連，**Then** 回放補推該筆（可能與斷線前已收事件重疊——at-least-once）。
3. **Given** guest/staff SSE 連線同時在線，**When** 發生 inspection_failed，
   **Then** guest/staff 照送、整合連線不送。

**功能需求（FR）**:
- **FR-301**: `GET /integration/pms/stream` MUST 回 `text/event-stream`，沿用
  StreamConnectionRegistry（含逾時、心跳、degraded 廣播、死連線清掃既有機制）；
  StreamConnection MUST 新增整合連線型別（綁 tenant，wants = 白名單對映命中）。
- **FR-302**: 建線 MUST 先回放 `cursor` 之後的事件（與 FR-201/202/203 同一讀取邏輯與
  安全遲滯），再進入即時推送；`cursor` 取 query param 或 `Last-Event-ID` header。
- **FR-303**: 即時推送路徑：TaskEventsSseConsumer 收 Kafka envelope 後，type 命中
  白名單才產整合事件；路由查詢 MUST 以 (tenant_id, task_id) join rooms 取 room_no、
  以 (tenant_id, task_id, seq)（uq_task_events_task_seq）取 task_events.id 當 event_id。
- **FR-304**: SSE wire 格式 MUST 為 `event: room_status`、`id: <event_id>`、
  `data: <room_status.v1 JSON>`；傳遞語意 at-least-once，PMS 去重鍵 = `(task_id, seq)`。
- **FR-305**: guest/staff 既有 SSE 行為（過濾、init/resync、心跳）MUST 不受影響。

**非功能需求（NFR）**:
- **NFR-301**: 消費延遲降級（degraded）廣播 MUST 同樣送達整合連線——PMS 收到後
  應轉輪詢 events API（即故事 2 路徑）。

### 使用者故事 4 — 退場 pms_status 舊外送鏈 (Priority: P1)

作為 TMS 維運者，我希望徹底移除無 consumer 的 pms_status outbox→Kafka 鏈，
以便不再付無人消費的輸送帶成本、也不留假訊號測試。

**為何為此優先級**: 不退場則查房交易持續寫無人讀的 outbox 列，且殘留列會讓改碼後的
OutboxRelay 的 topicFor 拋錯（crash loop 風險）——退場與新鏈必須同釋出。

**獨立驗證方式**: 全文搜 `pms` 相關符號為零引用；查房通過交易後 outbox 無
pms_status 列；全測試綠。

**驗收情境**:
1. **Given** 查房全項通過，**When** 交易提交，**Then** outbox 僅有 task_event
   （inspection_passed）落匣，無 pms_status 列；rooms.hk_state 照常轉 inspected。
2. **Given** V3 migration 執行，**When** 檢查 outbox，**Then** 殘留 kind='pms_status'
   列已刪除。

**功能需求（FR）**:
- **FR-401**: 退場清單 MUST 完整執行，缺一不可：
  1. `OutboxWriter.enqueuePmsStatus` 方法刪除（類別保留，task_event/push 續用）
  2. `KafkaTopicsProperties.pmsStatus` 欄位＋`topicFor` 的 `PMS_STATUS` case 刪除
  3. `InspectionService` 的 `outboxWriter.enqueuePmsStatus(...)` 呼叫（約 L128）＋
     `pmsStatusPayload` 方法（約 L257）刪除；OutboxWriter 依賴保留（push 續用）
  4. `OutboxKind.PMS_STATUS` 常數刪除＋自 `ALL` 移除
  5. `application.properties` 的 `tms.kafka.topic.pms-status=tms.pms-status` 行刪除
  6. 測試清理：`CucumberSpringConfiguration.java:87` 的 `NewTopic("tms.pms-status",…)`、
     `BackboneThenSteps.java:181-192` 兩個 pms step、`InspectionQrThenSteps.java:78+`
     的 pms_status step、`04-inspection.feature:78` 該 Then 行、
     `13-core-backbone.feature` 的 pms_status 資料列（L81-83、L118-119；場景改用
     task_event/push kind 或刪列）
- **FR-402**: V3 migration MUST 含 `DELETE FROM outbox WHERE kind = 'pms_status'`
  （該 topic 從無 consumer，刪除零風險）。
- **FR-403**: DB 的 `chk_outbox_kind` CHECK MUST NOT 收緊（留值無害；改 CHECK 鎖表）。
- **FR-404**: 查房通過交易的其餘行為（TaskEngine 守衛、hk_state 回寫、inspections
  記錄、退回連動與 push 落匣）MUST 不變。

## 2. 邊界情況（帶觸發值）

1. `X-API-Key` 缺失或空 → 401
2. key 格式非 `tmsik_<digits>_<base64url>`（如 `tmsik_abc_x`、`Bearer xxx`）→ 401
3. key 格式正確但 SHA-256 查無列 → 401
4. key 命中列但 `revoked_at = 2026-10-20T00:00:00Z`（早於現在）→ 401
5. key 自述 tenantId=2、hash 命中列 tenant_id=1 → 401（自述與列不符）
6. 有效整合 key 打 `/staff/tasks` → 403（矩陣 denyAll）
7. 員工 JWT 打 `/integration/pms/events` → 403（身分有效、矩陣不放行；非 401）
8. `limit=0` → 400；`limit=501` → 400；`limit` 省略 → 100
9. `after=abc` → 400；`after=-1` → 400；`after=999999999`（不存在）→ 200 空陣列
10. 租戶 1 的 key 查詢 → 租戶 2 的 task_events 零列（RLS）
11. `inspection_failed` 事件 → 整合連線不送、guest/staff 照送
12. 事件 `created_at = now() - 2.9s` → events API 不回傳；`= now() - 3.1s` → 回傳
13. SSE 重連帶 `cursor=上次 event_id` → 回放其後事件，可與斷線前已收重疊
    （at-least-once；PMS 以 (task_id, seq) 去重）
14. cursor query param 與 `Last-Event-ID` 同時給 → query param 優先（假設 A4）
15. 查房全項通過 → outbox 無 pms_status 列、僅 task_event 落匣（退場驗證）

## 3. 全域需求

- **FR-001**: room_status 對外 payload MUST 遵守 v1 封閉欄位集合（見 5.1 D4 表）；
  版本策略：加欄位=向後相容不升版；改語意/刪欄位/改型別=升 `room_status.v2`，
  `schema` 欄位是 PMS 分流依據。payload MUST NOT 含 tenant_id。
- **FR-002**: 整合 API 整體傳遞語意 MUST 為 at-least-once；去重鍵統一 `(task_id, seq)`
  （events 與 stream 共用）。
- **NFR-001**: 整合端點錯誤格式沿用 ApiErrorResponses 的既有 JSON 形狀。
- **NFR-002**: v1 不做 rate limiting（假設 A5，內網單一 M2M 客戶端）。

## 4. 關鍵實體

- **IntegrationApiKey**（新）：租戶簽發給 PMS 的長效 M2M 憑證，只存 hash。
- **TaskEvent**（既有，唯讀消費）：任務事件流水，id 兼任整合 cursor。
- **Room**（既有，唯讀 join）：取 room_no 供對外 payload。
- **RoomStatusEvent**（wire 契約，非持久化）：room_status.v1 對外事件形狀。

## 5. 資料維度與範例資料

### 5.1 資料維度

#### IntegrationApiKey（integration_api_keys）

| 維度 | 內容 |
|------|------|
| 身分與唯一性 | 代理鍵 `id uuid`（UuidV7 慣例）；`key_hash` 全域 UNIQUE；(tenant_id, name) **不設唯一**——同名多 key 合法（輪替重疊期必然同名，假設 A6） |
| 關係與基數 | tenants 1:N integration_api_keys；上層必須存在（複合 FK 慣例不適用——本表無 uuid 業務鍵可複合，單欄 FK tenant_id REFERENCES tenants RESTRICT） |
| optionality | tenant_id/name/key_hash/key_prefix/created_at 必填；revoked_at 可空 |
| 值域 | key_hash：64 字 hex（SHA-256）；key_prefix：16 字文字；name：文字；時間 timestamptz 含時區 |
| 狀態 | 兩態：active（revoked_at IS NULL）→ revoked（落印 revoked_at）；不可逆、不復活 |
| 保存 | 不硬刪（FK RESTRICT 慣例）；撤銷列永久保留當稽核軌跡 |
| PII | 無 PII；key_hash 非敏感（不可逆） |
| 量級 | 每租戶個位數；總量 < 100，無索引/分區議題（key_hash UNIQUE 索引即查詢索引） |
| 多租戶 | RLS policy 同 V1 模式；filter 先從 key 自述取 tenant 立 context 才查得到列 |
| 存取樣式 | 唯一熱查詢：`WHERE key_hash = ?`（每請求一次）；無分頁、無排序 |

#### TaskEvent（task_events，既有——本案新增的讀取樣式）

| 維度 | 內容 |
|------|------|
| 身分 | `id bigint IDENTITY`——本案起兼任整合 cursor（V1「id 非訂閱書籤」註解隨本案更新） |
| 存取樣式（新） | `WHERE id > :after AND created_at <= now()-3s AND type IN (白名單) ORDER BY id ASC LIMIT :n`，RLS 租戶過濾；需評估 (tenant_id, id) 或既有索引足否（db_table 定） |
| 其餘維度 | 不變（既有表，schema 零改動） |

#### RoomStatusEvent（wire 契約 v1，封閉欄位集合）

| 欄位 | 型別 | 必填 | 說明 |
|------|------|------|------|
| `schema` | text | ✓ | 固定 `room_status.v1` |
| `event_id` | text | ✓ | cursor（task_events.id 十進位） |
| `room_no` | text | ✓ | PMS 認房號不認 uuid |
| `hk_state` | text | ✓ | v1 唯一值 `inspected`（值域=白名單對映右側） |
| `task_id` | uuid 字串 | ✓ | 去重鍵成分 |
| `seq` | int | ✓ | 去重鍵成分 |
| `occurred_at` | ISO-8601 含時區 | ✓ | task_events.created_at |

### 5.2 範例資料

#### 範例資料 · IntegrationApiKey

| id | tenant_id | name | key_prefix | key_hash | created_at | revoked_at |
|----|-----------|------|------------|----------|------------|------------|
| ik-1 | 1 | pms-prod | tmsik_1_Ab3dEf5g | 9f86d08…(64 hex) | 2026-10-01T00:00:00Z | NULL |
| ik-2 | 1 | pms-prod-old | tmsik_1_Zz9yXw7v | 60303ae…(64 hex) | 2026-06-01T00:00:00Z | 2026-10-02T00:00:00Z |
| ik-3 | 2 | pms-prod | tmsik_2_Qq8rSt6u | fd61a03…(64 hex) | 2026-10-05T00:00:00Z | NULL |

#### 範例資料 · RoomStatusEvent（v1 wire）

| event_id | room_no | hk_state | task_id | seq | occurred_at |
|----------|---------|----------|---------|-----|-------------|
| 10234 | 305 | inspected | 018f…a1 | 7 | 2026-10-20T08:12:00+08:00 |
| 10257 | 301 | inspected | 018f…b2 | 5 | 2026-10-20T09:30:00+08:00 |
| 10311 | 512 | inspected | 018f…c3 | 9 | 2026-10-20T11:02:00+08:00 |

#### 範例資料 · 白名單對映（task_events.type → 外流）

| task_events.type | 外流 | hk_state |
|------------------|------|----------|
| inspection_passed | ✓ | inspected |
| inspection_failed | ✗ | — |
| completed | ✗ | — |

## 6. 成功標準

- **SC-001**: PMS 不接 Kafka、僅以 HTTPS 即可取得「查房通過」房況變化，
  任一事件自發生起 ≤ 3+N 秒（N=安全遲滯後首次拉取/推送）可被取得。
- **SC-002**: 任一租戶的整合憑證無法讀到其他租戶的任何事件（跨租戶讀取 = 0 列）。
- **SC-003**: 斷線任意時長後重連，斷線期間的白名單事件 0 漏推（允許重複，
  以 (task_id, seq) 可去重）。
- **SC-004**: 退場後 codebase 無任何 `pms_status` / `tms.pms-status` 殘留引用，
  CI 全綠、無假訊號測試。
- **SC-005**: guest/staff 既有 SSE 與 push 行為回歸 0 變化。

## 7. 假設

- **A1**: API key 由營運 SQL 開立（比照 tenants 人工開設），本案無管理端點。
- **A2**: key hash 用 SHA-256 而非 BCrypt（32-byte 高熵隨機值＋每請求驗證成本）。
- **A3**: 憑證走 `X-API-Key` header，不共用 Authorization Bearer（避免動既有分流）。
- **A4**: cursor query param 與 Last-Event-ID 同時給時 query param 優先。
- **A5**: v1 不做 rate limiting（內網單一 M2M 客戶端）。
- **A6**: 同租戶同 name 多把 key 合法（輪替重疊期需要）。
- **A7**: `chk_outbox_kind` CHECK 不收緊；V3 刪殘留 pms_status outbox 列。
- **A8**: target=backend 由需求推斷（無 arguments.yml；需求無 UI 字樣）。
- 範圍外：`tms.push`、guest/staff SSE 行為、minibar、API key 管理端點。

## 8. 承載覆蓋帳本

| clarified.md 來源項 | 類型 | spec.md 去向 |
|---------------------|------|--------------|
| 「業務目標」段 | 標題段 | 故事 1–4 敘述 + SC-001 |
| 「角色」段（M2M 客戶端） | 標題段 | 故事 1/2/3 的「作為」 |
| 「角色」段（營運 provisioning） | 標題段 | 假設 A1 + FR-107 |
| B1 events API | 行為 | 故事 2 全部 FR |
| B2 SSE 整合流 | 行為 | 故事 3 全部 FR |
| B3 認證 filter | 行為 | 故事 1 FR-103/104/105 |
| B4 退場 | 行為 | 故事 4 FR-401–404 |
| D1 key 格式與儲存 | 規則 | FR-102、NFR-101、假設 A2 |
| D2 驗證順序與租戶注入 | 規則 | FR-103/104/105/106 |
| D3 cursor 編碼與語意 | 規則 | FR-202/203、FR-002、邊界 9/12/13 |
| D4 payload 契約與版本 | 規則 | FR-001、5.1 RoomStatusEvent 表 |
| D5 白名單過濾 | 規則 | FR-204/303、邊界 11、5.2 對映表 |
| 「範圍」In/Out/退場清單 | 標題段 | FR-401 清單（逐項）、假設範圍外條 |
| 邊界 12 條（帶觸發值） | 邊界 | 節 2 第 1–15 條（逐條，含觸發值） |
| 範例 integration_api_keys ×3 | 範例資料 | 5.2 IntegrationApiKey（逐筆） |
| 範例 room_status ×3 | 範例資料 | 5.2 RoomStatusEvent（逐筆） |
| 範例 白名單對映 ×3 | 範例資料 | 5.2 白名單對映（逐筆） |
| 「完成判準自檢」段 | 標題段 | 5.1 各維度表（唯一性/值域/保存/PII/量級） |
