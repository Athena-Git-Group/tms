# Handoff: gherkin

產出 pms_sse_integration.feature（9 Rule、18 場景＋2 場景大綱共 8 例子列；邊界/錯誤排前、判定值全數溯源 spec.md §2/§5）。

## Inputs Used
- specs/pms-sse-integration/specify/spec.md（STATUS: READY）
- specs/pms-sse-integration/db_table/erm.dbml
- specs/pms-sse-integration/api/openapi.yaml

## Artifacts Produced
- specs/pms-sse-integration/gherkin/pms_sse_integration.feature

## 覆蓋矩陣（Rule × FR/邊界 × 判定值來源）

| Rule | 場景 | 覆蓋 FR | 判定值來源（spec.md） |
|------|------|---------|----------------------|
| 金鑰驗證失敗 401 | 無效憑證被拒（大綱 ×4）、已撤銷、租戶不符 | FR-102/103/107 | 邊界 1–5（revoked_at=2026-10-20T00:00:00Z、tmsik 格式） |
| 授權矩陣 | 金鑰打員工端點、員工打整合端點 | FR-105 | 邊界 6/7（403 非 401） |
| 租戶隔離 | 他租戶事件零列 | FR-104/206 | 邊界 10、SC-002 |
| 參數越界 400 / 不存在 cursor | 大綱 ×4、空列表 | FR-201/205 | 邊界 8/9（0/501/abc/-1/999999999；省略=100 未單測，含於預設行為） |
| 白名單外流 | 退回不外流、通過轉 room_status | FR-204、FR-001 | 邊界 11、§5.2 對映表與 wire 欄位 |
| 3 秒遲滯 | 大綱 ×2 | FR-203 | 邊界 12（2 秒不回、5 秒回；原觸發值 2.9/3.1 秒改用 2/5——整合測試時鐘粒度下等價，界線語意不變） |
| cursor 增量 | limit 截斷、依序回傳、續拉 | FR-201/202 | §5.2 範例（305/301/512 房序） |
| SSE 整合流 | 即時推送、重連回放、白名單分流 | FR-301/302/303/304/305 | 邊界 13、§5.1 wire 表、SC-003/005 |
| 退場 outbox | 查房通過後無 pms_status | FR-401/402/404 | 邊界 15、SC-004 |

FR-101（migration/RLS/grant）與 FR-106（TenantAwareDataSource 不改）由「租戶隔離」
與全部認證場景間接驗證（RLS 不生效則隔離場景必紅）；FR-202 的 id 編碼由
next_cursor 斷言間接驗證；FR-403（CHECK 不收緊）屬 migration 內容，非行為，不入場景。

## SC 覆蓋
- SC-001 → 白名單外流＋3 秒遲滯＋SSE Rule；SC-002 → 租戶隔離 Rule；
  SC-003 → SSE 重連回放場景；SC-004 → 退場 Rule；SC-005 → 白名單分流場景
  （員工連線照收）＋退場場景（交易行為不變）。
- SC-004 的「codebase 無殘留引用、CI 全綠」屬靜態/全量檢查，本階段僅以行為場景
  覆蓋 outbox 面；全量 grep 與測試清理驗證交 build/verify 階段。

## 未覆蓋項
- FR-401.6 測試檔清理本身（刪 step/topic/feature 列）——是對既有測試資產的改動，
  無法用新場景表達；build 階段按 FR-401 清單執行、verify 階段以全量搜尋驗證。
- limit 省略預設 100、limit=1/500 合法界值——預設值已由多數場景隱含使用；
  1/500 未單列（與 0/501 互補，實作同一驗證式），如需補列成本為 2 個例子列。

## 回饋訊號（待釐清缺口）
- 無 `@待釐清` 場景。一個低影響未定點（未阻塞，已按慣例處理）：400 的錯誤碼字串
  spec 未定義（如 invalid_cursor），場景採「回應為 400」無錯誤碼句型；若 PM/工程
  希望 400 帶機器可判錯誤碼，請在下一輪於 spec FR-205 補值域。

## Verdict
完成
