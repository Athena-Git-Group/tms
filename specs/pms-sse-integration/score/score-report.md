VERDICT: PASS-WITH-GAPS

# score · 可譯性評分（target: backend）

需求來源：`specs/pms-sse-integration/source/requirement.md`（= `points/pms-sse-integration.md`，
Point verdict `PASS-SPEC-FIRST`）。target 無 `specs/arguments.yml`，由需求推斷為 `backend`
（需求全為 API / 認證 / Kafka / outbox / SSE，無任何畫面、UI、表單字樣）。

## 評分表（rubric-backend，0–3）

| # | 維度 | 必備 | 分數 | 證據 |
|---|------|------|------|------|
| 1 | Entity Identifiability | ✅ | 2 | 「新增 per-租戶 M2M API key 認證（新表＋RLS）」——新實體可辨識，但屬性（key 格式、hash 欄位、撤銷欄位）未給；既有實體 task_events / outbox / rooms 可由 repo 直接核對（需求自述「經 repo 實地核對」） |
| 2 | Relationship & State | — | 2 | 新表與 tenants 的關聯可推（per-租戶）；API key 生命週期（建立/撤銷/輪替）僅在 Risks 點名「需在 spec 明確」，狀態流轉未定 |
| 3 | API Boundary | ✅ | 3 | 兩個 endpoint 邊界明確：「cursor 事件 API（`GET /integration/pms/events?after=`）」「SSE 整合流（`GET /integration/pms/stream`…重連帶 cursor 回放）」，輸入（cursor）與輸出（room_status payload）點名 |
| 4 | Business Rule Completeness | ✅ | 2 | happy path 與退場清單完整；unhappy/edge 部分缺：Risks 自列「at-least-once 語意與去重鍵」「驗證順序」「過濾規則若寫太寬」待定 |
| 5 | Validation & Acceptance | — | 1 | 驗收判準零星：「重連窗口內的事件可能漏推或重複，需定義」——cursor 編碼、分頁/回放上限明文「應在 spec 定案」，欄位驗證未給 |
| 6 | Scope & Integration | ✅ | 3 | in/out 明確：做「對方出站訂閱」、退場 `tms.pms-status` 全鏈（含 OutboxWriter.enqueuePmsStatus、KafkaTopicsProperties 對映、InspectionService 落匣、OutboxKind.PMS_STATUS、properties、測試）；不動 `tms.push` 與 guest/staff 既有流（Impact radius 說明） |
| 7 | Terminology Consistency | — | 3 | 術語前後一致（cursor / room_status / integration / API key / 回放），與 repo 現名對齊，未找到矛盾 |

## 裁決

無必備維度 = 0；非全 3 → `PASS-WITH-GAPS`。

## 缺口清單（clarify 議程）

1. **(維度 1/2) API key 實體屬性與生命週期**：key 明文格式、hash 演算法與儲存欄位、
   撤銷/輪替機制、啟用狀態檢查順序。
2. **(維度 4) 驗證順序與租戶注入**：新 filter 與既有 BearerTokenAuthFilter 的關係、
   TenantAwareDataSource 的 tenant 注入順序、「絕不接受 client 自帶 tenant_id」強制點。
3. **(維度 5) cursor 具體編碼與語意**：after 參數形狀、at-least-once 去重鍵、
   回放分頁上限、events 與 stream 共用讀取邏輯的一致性。
4. **(維度 4/5) room_status payload 欄位契約與版本策略**：對外欄位集合、
   版本演進方式。
5. **(維度 4) 整合連線過濾規則收斂範圍**：目前只放行「查房通過→inspected」，
   規則的封閉式表達方式（白名單 vs 條件式）。
