# 主管工作台 DSL

這個模組的專屬句型。跨模組共用句型見 [`../dsl.md`](../dsl.md)。

主管工作台是主管首頁三類待辦（待查房、待審核、待驗收）的查詢能力：`getManagerQueues`（`GET /me/manager-queues`）一次回三格數字與三份清單。三類的判定公式與排序鍵以 `specs/truth/data/data-model.dbml` 的 `tasks` Note 為唯一出處：待查房＝打掃單停在 done 且負責人不是呼叫者本人；待審核＝修繕任務停在 pending_review；待驗收＝修繕任務停在 done 且 `repair_tickets.verified_at` 為空。主管動作本身（查房送出、核准、退回、驗收）在 `查房` 與 `工務單` 模組驗證，本模組只驗「哪些單在等主管、排什麼順序、數字對不對」。

「今天」的日界線以注入的 `Clock` 時區認定，全租戶同一時區，目前沒有每家飯店各自的時區設定；`waitingSinceIsToday` 由後端算好，App 不比對。

## When 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `"{員工}" 查看主管待辦` | `員工`：字串；通常為主管，也可用其他角色驗證權限。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `getManagerQueues`（`GET /me/manager-queues`）。`權威狀態落地`：查詢不寫資料。`回寫`：回應 `ManagerQueues`（`counts` 與 `inspection`、`review`、`verify` 三個陣列）。 |

## Then 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `主管待辦的三格數字為：` | 無 | 支援：`格`（必填，字串；待查房、待審核、待驗收）、`數字`（必填，整數） | 無 | `必查`：`呈現結果`：`ManagerQueues.counts` 的 inspection、review、verify 逐格相等，且每格都等於對應陣列的長度。`權威狀態`：用 `tasks` 與 `repair_tickets` 依 `data-model.dbml` tasks Note 的三條公式重算，結果一致。 |
| `待查房清單依序為 {房號清單}` | `房號清單`：字串；以 `、` 分隔的房號。 | 不支援 | 無 | `必查`：`呈現結果`：`ManagerQueues.inspection[].roomNo` 的順序剛好等於清單，沒有多也沒有少；每列 `cleanerName` 是該單負責人、`title` 是任務內容。`權威狀態`：這些任務都是 type cleaning、state done，且 `assignee_id` 不是呼叫者；`waitingSince` 等於各自的 `done_at`，由舊到新。 |
| `待查房清單不包含 "{任務}"` | `任務`：字串；房號。 | 不支援 | 無 | `必查`：`呈現結果`：`inspection[]` 沒有該房。用於本人清的房與免查房結案的單。 |
| `待審核清單依序為 {單號清單}` | `單號清單`：字串；以 `、` 分隔的工務單號。 | 不支援 | 無 | `必查`：`呈現結果`：`ManagerQueues.review[].ticketNo` 的順序剛好等於清單；每列帶 `roomNo`、`category`、`reporterName`、`photoCount`。`權威狀態`：這些修繕任務都是 state pending_review；`waitingSince` 等於 `task_events` 最後一筆 `created` 或 `repair_resubmitted` 的時間，由舊到新。 |
| `待審核清單不包含 "{單號}"` | `單號`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：`review[]` 沒有該單。用於急件、已核准、已退回的單。 |
| `待驗收清單依序為 {單號清單}` | `單號清單`：字串；以 `、` 分隔的工務單號。 | 不支援 | 無 | `必查`：`呈現結果`：`ManagerQueues.verify[].ticketNo` 的順序剛好等於清單；每列帶 `roomNo`（公共區域為 null 且 `isPublicArea`=true）、`category`、`origin`、`resolutionType`、`assigneeName`。`權威狀態`：這些修繕任務都是 state done 且 `repair_tickets.verified_at` 為空；`waitingSince` 等於 `done_at`，由舊到新。 |
| `待驗收清單不包含 "{單號}"` | `單號`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：`verify[]` 沒有該單。用於維修中與已驗收的單。 |
| `待驗收清單中 "{單號}" 的完修結果為 "{結果}"、來源為 "{來源}"` | `單號`：字串。<br>`結果`：字串；已修好、暫時處理、無法修復（對照 fixed、temporary、cannot_fix）。<br>`來源`：字串；報修、定期保養（對照 reported、scheduled）。 | 不支援 | 無 | `必查`：`呈現結果`：該列 `resolutionType` 與 `origin` 等於對照值。`權威狀態`：與 `repair_tickets.resolution_type`、`origin` 一致。 |
| `"{單}" 在清單上標為不是今天開始等` | `單`：字串；房號或工務單號。 | 不支援 | 無 | `必查`：`呈現結果`：該列 `waitingSinceIsToday`=false，且 `waitingSince` 落在 `Clock` 時區的前一個或更早的日曆日。 |
| `"{單}" 在清單上標為今天開始等` | `單`：字串；房號或工務單號。 | 不支援 | 無 | `必查`：`呈現結果`：該列 `waitingSinceIsToday`=true，且 `waitingSince` 與目前 `Clock` 同一個日曆日。 |
| `這次查詢被拒絕，因為呼叫者不是主管` | 無 | 不支援 | 無 | `必查`：`呈現結果`：回應 403，body 符合 contracts `Error`。`權威狀態`：查詢不寫資料。 |
