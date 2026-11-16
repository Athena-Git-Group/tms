# 003 /implement 回交上游清單（重建版）

2026-11-16 重建：原紀錄放在 session scratchpad（/private/tmp），系統清理 tmp 後遺失。
本檔只保留「要回交上游決定」與「跨 phase 須知」的部分；逐題執行紀錄不重建。

## 已完成並驗證（4K–4Q）

| Phase | Feature | 結果 |
|---|---|---|
| 4K | 主管工作台/主管首頁 | 18/18 |
| 4L | 主管工作台/主管清單 | 15/15（後續回歸 13/13，情境數有調整） |
| 4M | 主管工作台（格子順序） | 13/13 |
| 4N | 主管工作台/查房 | 11/11 |
| 4O | 工務單/主管視角 | 23/23（開工前 17 紅） |
| 4P | 工務單/工務視角 | 27/27 |
| 4Q | 工務單/工務單房務端生命週期 | 13/13 |

後端 truth：`RunTruthFeaturesTest` 224/224。legacy `RunCucumberTest` 維持 5 紅基準（003 之前就有）。
`flutter test` 最後一次 484 綠、`flutter analyze` 0 error。

## 實作期間改過的 truth（已登記在 truth-delta 的「/implement 回饋修正」）

1. `ios-app/工務單/dsl.md` 步驟條「待開始」含 pool。
2. `backend/工務單/dsl.md`「尚未驗收」：已結案時必須是 no_action_needed。
3. `ios-app/主管工作台/dsl.md` 寬磚沒有副文（使用者 2026-11-12 要求移除「與房務主管看同一份」）。
4. `ios-app/待認領/待認領列表.feature:105` 主管排序修正。

## 後端 bug 修正（契約未改）

- `RepairService.ticketView` 漏填契約既有的 `assigneeName`，補 `LEFT JOIN staff_accounts`；`create` 的回應同樣補上。
  症狀：房務版「工務 阿德 已接單」一直退成「已接單」。已隨 4Q 修復並重跑 224/224。

## 待使用者決定（回交上游）

1. `repair_tickets.origin` 沒有 DB 欄位，目前由 `tasks.source` 推導。
2. 排程保養單沒有報修人，目前以處理人充當報修人。
3. 工務員工沒有個人班次（known risk 4）。
4. `forbidden_dept_pool` 錯誤碼的歸屬。
5. 公區工務單的推播對象。
6. 驗收退回後 `waitedMinutes` 是否重新計算。
7. truth 有暫停按鈕，App 沒有。
8. `no_action_needed` 的推播文案。
9. 後端 `readRepairTicket` 只回 `photoCount`，沒有 `photos` 明細 → 房務版詳情照片恆 0（無情境驗）。
10. 工務單還沒人接時，工務／主管狀態列寫「待認領」，ui-plan 寫「等工務接單」（無情境驗，交 4V）。
11. `ui/ticket.html` 房務版沒有步驟條，但 truth 要求 `ticket.stepper`；實作依 truth 放進既有時間軸。
12. 後端 403 時畫面顯示「你沒有查房的權限」未做：truth-delta 列為刻意不承接、App 到不了、無情境驗。

## 指揮官已裁決、併入 4U 的兩件

1. `me_screen.dart` 登出被攔的「前往」：主管改用主管視角開工務單。
2. `ticket_complete_sheet.dart` 照片提示「修繕完成必須拍照…」：選「不需處理」時改用 `repair_labels` 的照片規則換句或隱藏。

## 環境須知

- App BDD 需要 Docker（Postgres 5434 + Kafka 9094 + 隔離後端 8081）。原本的 `bdd-backend.sh`／`run-4i.sh` 放在 scratchpad，已隨 tmp 清理遺失，需重建。
- simulator udid `79F32D5C-7A71-47D6-AA8D-8142E68CFF48`，一次只能跑一個測試。
- 絕不停止或重啟使用者的 8080。
