# Handoff: data_model

萃取完成：1 新實體 IntegrationApiKey（含生命週期 active→revoked）、RoomStatusEvent 定為 wire-only 非持久化、TaskEvent/Room 唯讀消費零改動，覆蓋帳本無未處理。

## Inputs Used
- specs/pms-sse-integration/specify/spec.md（STATUS: READY）

## Artifacts Produced
- specs/pms-sse-integration/data_model/data-model.md

## 關鍵決策
- 明文 key 定為瞬態值非屬性；active 定為 derived 不落欄。
- RoomStatusEvent 不落表——它可由 task_events+rooms 完全推導，落表=第二真相。
- TaskEvent 的 3 秒遲滯歸入「讀取樣式」，非 schema 變更。

## Verdict
完成（無待釐清）
