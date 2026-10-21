# Point Result

- Report path: `points/pms-sse-integration.md`
- Summary: 以「對方出站訂閱」模式取代無 consumer 的 `tms.pms-status` Kafka topic：
  新增 per-租戶 M2M API key 認證（新表＋RLS＋TenantContext 注入＋SecurityConfig
  `/integration/**` 規則）、cursor 事件 API（`GET /integration/pms/events?after=`
  讀 task_events 轉 room_status payload）、SSE 整合流（`GET /integration/pms/stream`
  沿用 StreamConnectionRegistry，重連帶 cursor 回放）、TaskEventsSseConsumer 廣播
  路徑新增整合連線路由/轉格式，並退場 OutboxWriter.enqueuePmsStatus、
  KafkaTopicsProperties pms-status 對映、InspectionService 落 outbox 呼叫、
  OutboxKind.PMS_STATUS、application.properties 設定與相關測試。
- Knowledge base needed: no
- Knowledge sources checked: none（`.athena/knowledge/` 不存在；需求自帶完整程式碼佐證，
  經 repo 實地核對：outbox/OutboxWriter.java:78、outbox/KafkaTopicsProperties.java:22,28、
  admin/InspectionService.java:128,257、common/codes/OutboxKind.java:10,12、
  stream/ 與 auth/ 相關檔案均存在且與需求描述一致）
- Codemap consulted: no (graphify-out/ absent)
- Route: Spec First
- Gate verdict: `PASS-SPEC-FIRST`
- Allowed next commands: `/spec`
- Required follow-up gates: spec → plan → build(phase loop) → verify → review → ship（Full Weight）

## Scorecard

- Requirement clarity: 1/5
- Domain rule complexity: 4/5
- Impact radius: 4/5
- Contract/schema change: 5/5
- Regression risk: 4/5
- Knowledge dependency: 1/5
- Total: 19/30

## Why

- **為什麼一定要 spec**：總分 19 落在 Spec First 帶（15-30），且同時命中三條硬性 Gate——
  (1) 新增 API contract（兩個新 endpoint `/integration/pms/events`、`/integration/pms/stream`
  與 room_status payload 契約，是對外部 PMS 的公開介面，一旦發出就難改）、
  (2) 新增資料 schema（API key 憑證新表＋RLS policy＋migration）、
  (4) 牽涉權限/認證（M2M API key 驗證、tenant_id 解析注入、SecurityConfig 授權矩陣、
  租戶隔離——Domain≥4 override）。任一條單獨命中即 Spec First，此處三條齊發。
- Requirement clarity 1：需求極詳盡、附檔案級佐證且經核對屬實；僅剩少量實作細節未定
  （key 格式與 provisioning/rotation 流程、cursor 的具體編碼、stream 與 events API 的
  分頁/回放上限），不影響方向判斷但應在 spec 定案。
- Domain rule complexity 4：API key 認證＋租戶隔離（RLS＋TenantContext）＋授權矩陣，
  屬「權限」類高風險規則；但規則本身有明確比照對象（BearerTokenAuthFilter 模式），
  不到 5。
- Impact radius 4：橫跨 auth、stream、outbox、admin(InspectionService)、common/codes、
  domain(新 entity)、resources(migration＋properties)、test(cucumber 場景) 多模組，
  且輸出面向外部系統（PMS），影響多方；未達 5 因不動 `tms.push` 與 guest/staff 既有流。
- Contract/schema change 5：新 endpoint ×2＋新 entity/表＋schema migration＋新對外
  payload 契約，rubric 滿分條件全中（本身即 override）。
- Regression risk 4：(a) 退場 pms_status outbox 路徑會動到 InspectionService 查房通過的
  交易內行為與既有 BDD 測試（BackboneThenSteps、InspectionQrThenSteps、
  CucumberSpringConfiguration 的 topic 建置）；(b) TaskEventsSseConsumer 廣播路徑加
  路由/過濾規則，直接碰 guest/staff SSE 核心流；(c) OutboxKind.ALL 列表縮減可能牽動
  relay/retention 的 kind 分支。可測但面廣。
- Knowledge dependency 1：知識庫為空，但本需求不依賴外部規格查證——所有判斷依據
  （既有程式、topic 現況、已否決的方案）都在需求內且可由 repo 直接驗證，故不因
  知識庫缺席而調高（調高規則適用於「需要查但查不到」，此處不需要查）。
- Codemap：`graphify-out/graph.json` 不存在，未做 codemap 微調，照原 rubric 評分。

## Risks

- **對外契約一次定生死**：PMS 是外部內網系統，events/stream 的 payload 與 cursor 語意
  發布後難以演進，spec 階段必須定清楚版本策略與欄位契約。
- **認證邊界**：API key 長效憑證的 hash 演算法、撤銷/輪替、啟用狀態檢查順序，以及
  「絕不接受 client 自帶 tenant_id」的強制點（filter 層 vs service 層）需在 spec 明確。
- **RLS 與 M2M 流的交互**：TenantAwareDataSource 的 tenant 注入時機若與新 filter 順序
  不合，會出現跨租戶讀取或全空結果的隱性 bug。
- **回放一致性**：SSE 即時推送與 cursor 回放共用 task_events 讀取邏輯時，重連窗口內
  的事件可能漏推或重複，需定義 at-least-once 語意與去重鍵。
- **退場的測試連鎖**：移除 pms_status 後至少 3 個測試檔（CucumberSpringConfiguration、
  BackboneThenSteps、InspectionQrThenSteps）與 application.properties 需同步清理，
  漏清會造成 CI 假訊號。
- **廣播過濾範圍**：目前只含「查房通過 → inspected」，TaskEventsSseConsumer 的過濾
  規則若寫太寬，未來新增事件種類會意外外流到整合連線。
