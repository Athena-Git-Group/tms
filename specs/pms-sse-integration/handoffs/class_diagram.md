# Handoff: class_diagram

三層物件設計完成：2 新 Controller、IntegrationEventReader（SRP：events 與 stream 回放共用讀取）、RoomStatusMapper（白名單封閉點）、認證鏈 3 類、既有類別僅標改動點。

## Inputs Used
- specs/pms-sse-integration/data_model/data-model.md
- specs/pms-sse-integration/specify/spec.md（STATUS: READY）

## Artifacts Produced
- specs/pms-sse-integration/class_diagram/class-diagram.mmd

## 關鍵決策
- **IntegrationEventReader 抽出**：FR-201 與 FR-302 明文「同一讀取邏輯」——不抽則兩處重複 3 秒遲滯/白名單/cursor 查詢，日後漂移。
- **RoomStatusMapper 持有白名單常數**：D5 的結構性收斂點集中一處，consumer 與 reader 共用。
- **不抽介面/策略**（YAGNI）：v1 僅單一對映，無變化點訊號；版本演進走 schema 欄位非多型。
- **IntegrationApiKeys 靜態工具**比照 GuestSessionTokens 慣例。
- 既有類別（StreamConnectionRegistry/StreamConnection/TaskEventsSseConsumer/SecurityConfig）只加方法/分支，guest/staff 路徑零改動（FR-305）。

## Verdict
完成
