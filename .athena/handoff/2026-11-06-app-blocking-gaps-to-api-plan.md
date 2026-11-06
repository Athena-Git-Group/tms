# Handoff：員工 App 實跑打出來的阻塞缺口（#9–#24）

**來源**：`specs/plans/001-staff-app-increment` 的 Phase 3N–3X（T316–T336），iOS simulator＋Appium＋真後端端到端實跑
**更新**：2026-11-06
**完整清單**：`tasks.md` 末節「上游缺口」#9–#24（#1–#8 是原型與環境項，不在此文範圍）

## 一句話

108 個場景跑到 62 綠、39 紅（另有 7 個正在跑）。**39 個紅裡有 38 個卡在 App 邊界外**——後端有資料但不回、有路由但沒實作、有欄位但寫死；只有 1 個（任務頁步驟條）是 App 自己還沒做完。16 項缺口依「誰該動、動多少」分成四類，建議照 B → A → C → D 的順序處理，B 類最小、解鎖最多。

## A. 要先動契約（`/api-plan`），再由後端實作

| # | 缺口 | 擋住 | 建議 |
| --- | --- | --- | --- |
| #12 | **App 無法列舉「進行中（doing）」的任務**。`MyToday.nextTasks` 只收 `accepted`（`TaskExecutionService.java:498`），`/pool` 只回 `state=pool` | 24 個場景（房務端 18、任務執行 6） | `MyToday` 補「進行中的單」。ui-plan 畫面 1 的「繼續處理」本來就假定有這份清單 |
| #10 | **工務看不到自己手上的工務單**。同族問題，工務的 doing／今日 done 單無端點可列舉 | 7 個場景 | `MyToday.engineeringSummary` 補手上單清單，或新增 `GET /repair-tickets?assignee=me` |
| #14 | **「設定新密碼」端點缺席**。後端在 `must_change_password` 清掉前對業務端點一律 403，App 無合法路徑完成改密 | 1 個場景 | 補端點（重雜湊密碼＋清旗標）。App 側畫面與流程已完成，只差這條 |
| #16 | **主管看不到任何部門池，且無「部門沒有在職員工」訊號**。主管 `dept` 是 NULL，`deptPool:357` 直接 422；後端也沒有地方分辨「沒有單」與「沒有人可以搶單」 | 1 個場景 | 補部門查詢參數（或主管視角端點）＋在回應帶出部門可搶單人力狀態 |

## B. 契約已經寫好，只差後端補實作（**不必動契約，最小、解鎖最多**）

| # | 缺口 | 擋住 | 改動大小 |
| --- | --- | --- | --- |
| #15 | `GET /pool` 不回 `reminderStage`。契約 `員工工作台.yaml:150` 有定義、`tasks.reminder_stage` 也有值，但 `deptPool` 組回應時沒放（只拿它當排序旗標） | 2 個場景 | 補一行 `item.put` |
| #17 | `readTasks` 回應漏 `estMin`／`skippedSopItems`／`inspectionWaived`。契約 `TaskDetail` 有，`toDetail:584-607` 逐欄組裝時沒放 | 2 個場景（超時提示、慶祝頁預估分鐘） | 補三欄 |
| #13 | `listRepairTickets` 回 501（`RepairController.java:118` 明寫佔位） | 與 #12 疊在房務端 18 個場景上 | 實作既有契約 |
| #18 | `GET /sop-items`（契約 `openapi.yaml:119` 的 `listSopItems`）後端沒有任何 controller。`TaskDetail.checkedSopItems` 只給 id 不給名稱，App 畫不出 SOP 清單 | 2 個場景 | 實作既有契約 |
| #23 | `room_memory` 沒有任何寫入來源。schema 註解明寫「工務單完修以 kind=repair 寫入」，但全後端只有讀、沒有寫 | 0（測試用 fixture 繞過），但正式流程下「查一下」永遠查不到房間記錄 | 完修流程補寫入 |
| #21 | 班末換人無任何可走路徑（班末掃描 job 不存在），且 `returnToPool` 不寫 `pool_source`，換人原因永遠 null | 0（fixture 繞過），但該功能實際不存在 | 需要後端設計，非一行 |
| #9 | `V16__notifications.sql` 漏 `GRANT ... TO tms_app`，執行期 `GET /notifications` 500 | 0（已手動 GRANT），但**環境一重建就再炸** | 補一支 GRANT migration |

## C. 要先定規格口徑（`/dsl-refine` 或產品決議）

| # | 缺口 | 說明 |
| --- | --- | --- |
| #11 | 撞單場景的 Given 順序產生不出被測狀態 | 「小李剛接走」的 Given 先把單建成已接單才打開待認領，該單此時已不在池中、依規格本就不該顯示，無法表達 Rule 要的「列表過期」。Given 應改為先進池、開池後才被他人接走。擋 1 個場景 |
| #20 | `MyToday.zeroReturnStreakDays` 後端寫死 0 | feature 自己帶著 `[need clarification]`：以出勤工作日連續計還是日曆日？當天有一筆退回就歸零嗎？場景只驗「畫面與後端一致」所以綠燈成立，但數字目前沒有業務意義 |
| #19 | 延後／中斷預設原因文字兩份規格不一致 | `ui-plan.md:110` 寫「缺備品去拿」「其他」，truth feature 與 dsl 寫「缺備品，去拿」「其他原因」。這段文字同時是送到後端的 `hold_reason` 內容與測試指認選項的依據，兩份不同會讓實作與驗收各挑一份。本輪以 truth 為準落地 |

## D. 文件追認（`/ui-plan`，不擋任何場景）

`ui-plan` 的 accessibility id 約定表缺三處，App 已依 truth 的逐欄驗法自行命名，需要回頭追認：#19（延後／中斷面板全部 id）、#22（交辦與退回資訊的分欄 id）、#24（查一下的分欄 id 與回答全文節點）。

共同成因是 truth 的驗法是「逐欄分別驗」，而 ui-plan 只給了整塊一個 id——整塊共用一個 id 時各段文字會被讀成同一串，驗不出是哪一欄不對。

## 補完後的收益

| 補哪些 | 解鎖 |
| --- | --- |
| **B 類的 #15＋#17**（補四個欄位） | 4 個場景，`待認領列表` 直接收 9/9 |
| **A#12＋B#13** | 房務端 18 個場景（整個 Phase 3Q）＋任務執行 6 個 |
| **A#10＋C#11** | 工務視角 8 個，T320 收 13/13，T321 重構解鎖 |
| **B#18** | SOP 2 個場景 |
| **A#14** | 登入收 10/10 |
| **A#16** | 待認領最後 1 個 |

全部補完後，App 側剩下的只有 1 個場景（任務頁步驟條）是自己的工作，兩支暫緩的重構（T321／T323）也一併解鎖。

## 驗收方式

每項補完後 App 側不需要改 stepdef——49 句 step definitions 與畫面實作都已就位，直接跑：

- `app/bdd/run-module.sh <模組>`（帳號與登入／推播／工務單／待認領／任務執行／房間交辦／查一下／系統層）
- 全量：`mvn -f app/bdd/pom.xml test -Dtest=RunTruthFeaturesTest`
