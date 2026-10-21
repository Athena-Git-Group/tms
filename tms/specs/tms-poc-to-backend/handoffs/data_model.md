# Handoff: data_model

以 spec.md 萃取 27 個實體（docs/12 既定 17＋本階段定案 9 張待補表＋tenant_config），含屬性/關係/生命週期/erDiagram/覆蓋帳本/詞彙表。

## Stage
data_model（結構層，實體集合真相）

## Inputs Used
- specs/tms-poc-to-backend/specify/spec.md（STATUS: READY）

## Artifacts Produced
- specs/tms-poc-to-backend/data_model/data-model.md

## Gate Verdict
PASS — 覆蓋帳本無未處理；待補 9 表已定案

## Risks / Unresolved Issues
- 設計決定：SopItem 表（applies_to 區分 SOP/查房項目）為儲存真相，`/admin/settings` 的 `sop_items`/`inspection_items` 鍵作門面——docs/12 兩處並載的裁決，db_table/api 需照此對齊
- TaskSopCheck 採「現值歸零、歷史在事件流」模型（不留 round 欄）
- 掃碼 IP 限流為程序內計數器，非持久實體（多實例部署時需改集中式，記入 Risks）

## Next Recommended Stage
class_diagram
