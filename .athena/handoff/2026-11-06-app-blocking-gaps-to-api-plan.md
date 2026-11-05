# Handoff → `/api-plan`：員工 App 實跑打出來的阻塞缺口

**來源**：`specs/plans/001-staff-app-increment` 的 Phase 3N–3R（T316–T325），iOS simulator＋Appium＋真後端端到端實跑
**日期**：2026-11-06
**完整清單**：`specs/plans/001-staff-app-increment/tasks.md` 末節「上游缺口」#9–#16（#1–#8 是原型／環境項，與本次無關）

## 一句話

已開工的 5 支 feature 目前綠 38 個場景、紅 30 個（另有 40 個場景屬尚未開工的 Phase 3S–3X），**紅的沒有一個是 App 實作問題**——全部卡在「後端有資料或有能力，但 App 沒有合法途徑拿到」。其中 5 項需要你動契約，3 項契約已經寫好、只是後端沒照做，1 項是 feature 寫法問題。分流如下。

## A. 需要 `/api-plan` 動契約的 5 項

### A1. App 無法列舉「進行中（doing）」的任務 — 擋 18 個場景

`MyToday.nextTasks` 只收 `accepted`（`tms/src/main/java/com/athena/tms/service/TaskExecutionService.java:498`），`/pool` 只回 `state=pool`。房務端三支 feature（報修照片與送出、開單提示、工務單房務端生命週期）的受測任務全是 doing，App 連任務頁都進不去，Phase 3Q 全部 18 個場景一個都跑不起來。

`ui/ui-plan.md` 畫面 1 的「繼續處理」本來就需要這份清單，等於契約漏了一個畫面已經假定存在的能力。

**建議**：`MyToday` 補「進行中的單」（schema `員工工作台.yaml` 的 `MyToday`）。要新開欄位還是放寬 `nextTasks` 的狀態範圍由你裁決——後者會改變既有語意（`nextTasks` 目前是「下一間推薦」，有明確排序規則），我傾向新開欄位。

### A2. 工務看不到自己手上的工務單 — 擋 7 個場景

`工務視角.feature` 有 7 個場景要從工務首頁點進維修中／已完修的工務單詳情。現有端點全部到不了：`/pool` 只回 pool 狀態；`MyToday.nextTasks` 同 A1 只收 accepted；契約的 `listRepairTickets` 是「依房間列出同房工務單」（必帶 `roomId`，非本人視角）；`listTasks` 實際掛在 `/desk`，僅限櫃檯／主管。

**建議**：`MyToday.engineeringSummary` 補手上單清單（它已經在回傳張數統計，補清單最自然），或新增本人視角的 `GET /repair-tickets?assignee=me`（需涵蓋 doing 與今日 done）。與 A1 同一族，建議一起裁決形狀。

### A3. 「設定新密碼」端點缺席 — 擋 1 個場景

`員工帳號與登入.yaml` 只有 `staffAccountsLogin`。臨時密碼登入後後端在 `must_change_password` 清掉前對業務端點一律 403（`BearerTokenAuthFilter`），App 端沒有任何合法路徑完成改密，`登入與登出守衛.feature:59` 永遠紅。App 側的強制改密畫面與守衛已實作完成，只差這條端點。

**建議**：補「設定新密碼」端點（重雜湊密碼＋清 `must_change_password`），回應形狀需讓 App 知道可以接著進首頁。

### A4. 主管看不到任何部門的待認領池 — 擋 1 個場景

`listDeptPool` 只依登入者自己的 `dept` 分流、沒有部門查詢參數；主管的 `dept` 是 NULL，`TaskExecutionService.deptPool:357` 直接回 422「此帳號未設部門」。`待認領列表.feature:88` 要王主管打開房務待認領。

**建議**：補部門查詢參數，或開主管視角的部門池端點。

### A5. 沒有「部門沒有在職員工」的訊號 — 與 A4 同一個場景

後端沒有任何地方判斷這件事，`GET /pool` 對這個情況只回一般的空 `items`，App 無從分辨「沒有單」與「沒有人可以搶單」——但 feature 要求這兩種空狀態長得不一樣。

**建議**：在池回應帶出部門可搶單人力的狀態（例如 `activeStaffCount` 或一個明確的空狀態原因），讓空狀態的語意由後端決定而非 App 猜。

## B. 契約已經寫好、只需後端補實作的 3 項（**不要**進 `/api-plan`）

| # | 缺口 | 證據 | 影響 |
| --- | --- | --- | --- |
| #15 | `GET /pool` 不回 `reminderStage` | 契約 `員工工作台.yaml:150` 的 `PoolItem.reminderStage` 已定義、`tasks.reminder_stage` 已落地（V13），但 `TaskExecutionService` 組 `PoolItem` 時（:405-419）沒放進回應，只拿它當排序旗標（:369） | 未接超時兩段標籤畫不出來，擋 2 個場景。補一行 `item.put` |
| #13 | `listRepairTickets` 回 501 | `tms/src/main/java/com/athena/tms/repair/RepairController.java:118` 明寫佔位 | 房務端進不了工務單詳情；即使 A1 解除仍擋著 |
| #9 | `V16__notifications.sql` 漏 `GRANT ... TO tms_app` | 執行期 `GET /notifications` 500 permission denied；後端 BDD 以超級使用者跑所以沒抓到 | 已對跑著的 DB 手動 GRANT 解鎖，但需補一支 GRANT migration，否則重建環境就再炸 |

B 類請直接走後端實作（`/implement` on `tms/`）。#13 若順便要加本人視角的篩選參數，那部分才需要 `/api-plan`（見 A2）。

## C. 回交 `/dsl-refine` 的 1 項

**#11 撞單場景的 Given 順序產生不出被測狀態**：`工務視角.feature` 的「小李剛接走」場景，Given 先把單建成「已接單／小李」才打開待認領——該單此時已不在池中，依規格本就不該顯示，無法表達 Rule 要的「列表過期」語意。Given 應改為先進池、開池後才被他人接走。

## 補完後的預期收益

| 補哪些 | 解鎖 |
| --- | --- |
| A1＋B#13 | Phase 3Q 全部 18 個場景（T322／T323） |
| A2 | 工務視角 7 個場景（T320 由 5/13 → 12/13） |
| C | 工務視角最後 1 個（T320 收全綠，T321 解鎖） |
| A3 | 登入模組收 10/10（T316） |
| A4＋A5 | 待認領 1 個場景 |
| B#15 | 待認領 2 個場景（T324 收 9/9） |

全部補完，已開工的 5 支 feature 可從「38 綠 30 紅」收到全綠，剩餘 phase（3S–3X，40 個場景）也能全速往下跑，兩支暫緩的重構（T321／T323）也一併解鎖。

## 驗收方式

每項補完後，App 側不需要改 stepdef——49 句 step definitions 與畫面實作都已就位，直接跑：

- `app/bdd/run-module.sh <模組>`（帳號與登入／推播／工務單／待認領）
- 全量：`mvn -f app/bdd/pom.xml test -Dtest=RunTruthFeaturesTest`
