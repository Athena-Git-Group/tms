# Handoff: api

intent DSL（integration.intent.yaml，2 operations）經 transpiler 編譯出 openapi.yaml（2 paths、9 schemas、$ref 全解析），openapi-spec-validator 驗證通過。

## Inputs Used
- specs/pms-sse-integration/specify/spec.md（STATUS: READY）
- specs/pms-sse-integration/db_table/erm.dbml
- specs/pms-sse-integration/data_model/data-model.md

## Artifacts Produced
- specs/pms-sse-integration/api/integration.intent.yaml
- specs/pms-sse-integration/api/openapi.yaml（build artifact，不可手改）

## 端點
- GET /integration/pms/events（operationId: integrationPmsEvents）
- GET /integration/pms/stream（operationId: integrationPmsStream）

## 關鍵 intent 決策
- base 用單數 `integration`：/integration 前綴已是 SecurityConfig 矩陣契約，不複數化。
- entity 省略：回應是 wire 契約（包裝陣列＋next_cursor / SSE 流），綁 DBML Table 會產生誤導性 response schema。
- 不開 integration_api_keys 的任何 CRUD 端點（spec 假設 A1：營運 SQL 開立，無管理 API）。

## 範圍外待後續（本版 transpiler 能力外，不靜默丟失）
- 認證（X-API-Key securityScheme）——契約真源 spec.md FR-103
- 查詢參數 after/limit/cursor 與 400 判準——spec.md FR-201/205
- room_status.v1 response schema 與 text/event-stream 內容型別——spec.md FR-001/304（§5.1 表）
- openapi.yaml 的 9 schemas 含 dbml 示意用既有表（tenants/task_events/outbox）DTO，屬 transpiler 全表生成的副產物，非對外承諾

## Verdict
完成（openapi.yaml 已驗證）
