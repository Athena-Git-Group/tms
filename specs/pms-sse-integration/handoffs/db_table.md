# Handoff: db_table

持久化落地完成：新表 integration_api_keys（RLS＋grant＋uq_integration_api_keys_hash），V3 migration 另含 outbox 殘留列 DELETE 與 task_events.id 註解更新；既有表零 schema 改動。

## Inputs Used
- specs/pms-sse-integration/data_model/data-model.md
- specs/pms-sse-integration/specify/spec.md（STATUS: READY）
- src/main/resources/db/migration/V1__schema.sql（RLS/grant/命名慣例比對）

## Artifacts Produced
- specs/pms-sse-integration/db_table/erm.dbml

## 關鍵決策
- 不補 updated_at/version：兩態單向生命週期（撤銷=冪等單欄落印）無競態面，比例原則。
- 不設 (tenant_id, name) 唯一（A6 輪替重疊同名）。
- task_events 不預建 (tenant_id, id) 複合索引——PK 範圍掃描足；build 階段 EXPLAIN 驗證，慢再補。
- grant 面：tms_app 僅 SELECT + UPDATE(revoked_at)；INSERT 走營運 admin（A1）。
- PII：無；保存政策：key 列永久保留（稽核），已承載非延後。

## Verdict
完成
