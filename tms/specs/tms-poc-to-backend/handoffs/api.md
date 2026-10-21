# Handoff: api

18 份 intent DSL 經 transpiler 編成 openapi.yaml（48 paths、54 operations、55 schemas），openapi-spec-validator 驗證通過。

## Stage
api（契約層，編譯器模型）

## Inputs Used
- specs/tms-poc-to-backend/specify/spec.md（STATUS: READY，行為真相）
- specs/tms-poc-to-backend/db_table/erm.dbml（型別來源，27 表）
- specs/tms-poc-to-backend/data_model/data-model.md、class_diagram/class-diagram.mmd（操作線索）

## Artifacts Produced
- specs/tms-poc-to-backend/api/*.intent.yaml（18 份：tasks、task-events、guest-sessions、room-qr-tokens、rooms、reservations、cleaning-plans、inspections、leaves、notices、lost-items、handovers、staff-accounts、tenant-configs、daily-stats、room-memories、sop-items、alerts）
- specs/tms-poc-to-backend/api/openapi.yaml（transpiler 產出，不可手改）

## Gate Verdict
PASS — transpiler $ref 全解＋validator 通過

## Risks / Unresolved Issues
- **角色前綴路由待後續**：docs/11 的 `/guest|/staff|/desk|/admin|/owner` 前綴屬認證/授權面（本版 DSL 明文不含 access 區塊）。openapi.yaml 以資源為 base（/tasks、/cleaning-plans…），前綴 × 資源操作的對照=spec.md FR-041 授權矩陣；build 時以路由層映射，gherkin 以 spec.md 的路徑為準
- **機械 DTO 的已知偏差（不可手改 openapi.yaml，列此避免靜默）**：①request schema 含 tenant_id（實際由憑證注入，伺服器忽略）；②自訂操作一律掛 {Entity}Request body（claim/start/complete 實際無 body；triage/requeue/rate 的實際欄位以 spec.md FR 為準）；③`PUT /cleaning-plans/{id}` 的 If-Match header 與所有 POST 的 Idempotency-Key header 無法入 DSL，契約見 spec.md GR-002/E-08
- **SSE 端點不在 openapi.yaml**（/guest/stream、/staff/stream）——OpenAPI 不適表長連線，契約在 spec.md GR-005
- enum 以 text＋CHECK 落 DB（docs/12 明文不用原生 ENUM），故 OpenAPI schema 為 string＋description 載值集，未展開 enum 陣列
- 認證/授權（securitySchemes）、分頁/filter/sorting：本版範圍外，待後續
- redocly 不可用，驗證以 openapi-spec-validator 為準（已通過）

## Next Recommended Stage
gherkin
