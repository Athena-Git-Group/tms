# Handoff: clarify

score 5 項缺口全為工程慣例可定的實作細節，headless 指示下以 codebase 慣例為錨採保守預設（D1–D5），STATUS: RESOLVED。

## Inputs Used
- specs/pms-sse-integration/source/requirement.md
- specs/pms-sse-integration/score/score-report.md
- repo 實地核對：BearerTokenAuthFilter / GuestSessionTokens / TenantAwareDataSource /
  OutboxWriter / KafkaTopicsProperties / OutboxKind / InspectionService / TaskEventsSseConsumer /
  StreamConnectionRegistry / V1__schema.sql / application.properties / 3 個測試檔＋feature 檔

## Artifacts Produced
- specs/pms-sse-integration/clarify/clarified.md（STATUS: RESOLVED）
- questions.md 未建立（無高影響缺口升級）

## Verdict
RESOLVED

## Notes（Assumptions 一覽，供最終 handoff Risks 轉錄）
- A1 API key 由營運 SQL 開立，無管理端點（比照 tenants 人工開設）
- A2 key hash 用 SHA-256 而非 BCrypt（高熵隨機值＋每請求驗證成本）
- A3 憑證走 X-API-Key header，不共用 Authorization Bearer
- A4 cursor 與 Last-Event-ID 同時給時 query param 優先
- A5 v1 不做 rate limiting（內網單一 M2M 客戶端）
- A6 同租戶同 name 多把 key 合法（輪替重疊期需要）
- A7 DB chk_outbox_kind CHECK 不收緊；V3 刪除殘留 pms_status outbox 列
