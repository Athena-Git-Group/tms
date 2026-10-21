# Handoff: class_diagram

依 docs/11 模組表產三層物件設計：6 Controller×12 Service×worker 模組，4 個需求觸發的 OCP 介面。

## Stage
class_diagram（結構層 2a，物件設計）

## Inputs Used
- specs/tms-poc-to-backend/data_model/data-model.md
- specs/tms-poc-to-backend/specify/spec.md（STATUS: READY）

## Artifacts Produced
- specs/tms-poc-to-backend/class_diagram/class-diagram.mmd

## Gate Verdict
PASS

## Risks / Unresolved Issues
- 拆分理由：TaskEngine 獨立（docs/11「狀態變更唯一入口」硬規則）；Dispatch/Scheduling/Inspection/GuestAccess 各對應 docs/11 模組邊界；OCP 介面只開在需求明示變化點（SchedulingAlgorithm v2 預告、PmsAdapter 多家 PMS、LlmClient 僅輔助可換、PushSender MVP 日誌→真推播），其餘單一實作不預抽
- IdempotencyFilter/TenantContext 為橫切（servlet filter＋資料層 scope），非 Service
- worker 類 MVP 與應用同程序（spec 假設 A3），圖中已按可拆模組畫

## Next Recommended Stage
db_table
