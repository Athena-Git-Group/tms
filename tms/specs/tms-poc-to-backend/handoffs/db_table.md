# Handoff: db_table

把 data-model.md 的 27 個實體落成 DBML：全表業務 Note＋不變條件、enum CHECK 註記、partial unique/EXCLUDE 標注、複合外鍵 30 條、索引按存取樣式宣告。

## Stage
db_table（結構層 2b，持久化）

## Inputs Used
- specs/tms-poc-to-backend/data_model/data-model.md（主輸入）
- specs/tms-poc-to-backend/specify/spec.md（STATUS: READY；資料維度回查）

## Artifacts Produced
- specs/tms-poc-to-backend/db_table/erm.dbml

## Gate Verdict
PASS

## Risks / Unresolved Issues
- DBML 無法原生表達三處：partial unique（tasks 一房一天、QR 一房一枚 active、計畫一班一份 published）、reservations_cache 的 EXCLUDE 區間約束、task_events 的 REVOKE UPDATE/DELETE——全部已以 index note/Table Note 載明，migration（build 階段）必須照 note 落地
- PII 標注齊：staff name/login、guest_name、task.note、lost_items.returned_to、room_memory.summary、phrase_counter.phrase——去識別化排程的作用範圍
- est_min 用 int 分鐘；Q1（credit 換算）未決，tenant_config 留 minutes_per_credit 鍵
- 樂觀鎖：僅 cleaning_plans.version（需求明文 If-Match）；其他表靠狀態機守衛，不加 version（allowlist 內的取捨）

## Next Recommended Stage
api
