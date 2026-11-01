# 任務執行 DSL

這個模組的專屬句型。跨模組共用句型見 [`../dsl.md`](../dsl.md)。

## Given 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `打掃 SOP 共有以下項目：` | 無 | 支援：`順序`（必填，整數）、`項目`（必填，字串） | 無 | `怎麼做`：依順序建立租戶的打掃 SOP 項目。`權威狀態落地`：`sop_items` 已有這些項目，`applies_to`=cleaning。`回寫`：項目名稱對應的 id。 |
| `"{員工}" 已勾選 "{任務}" 的以下 SOP 項：` | `員工`：字串。<br>`任務`：字串；房號。 | 支援：`項目`（必填，字串） | 無 | `怎麼做`：以該員工身分逐項呼叫 `tasksSopCheck`（`POST /tasks/{id}/sop-check`，result=checked）。`權威狀態落地`：`task_sop_checks.checked`=true，並有 `sop_checked` 事件。 |
| `"{任務}" 已中斷 {分鐘} 分鐘` | `任務`：字串；房號。<br>`分鐘`：整數。 | 不支援 | 無 | `怎麼做`：把測試 `Clock` 往前撥到中斷事件之後的該分鐘數。`權威狀態落地`：中斷事件時間不變，目前時刻離中斷已過該分鐘數。`不必查`：不寫資料庫。 |
| `"{任務}" 的進行歷程如下：` | `任務`：字串；房號。 | 支援：`段落`（必填，整數）、`狀態`（必填，字串；進行中或已中斷）、`分鐘`（必填，整數） | 無 | `怎麼做`：依段落順序寫入 started、interrupted、resumed 事件，各段長度等於分鐘數，最後一段的結束時刻就是目前 `Clock`。`權威狀態落地`：`task_events` 與 `tasks.worked_sec`、`segment_started_at` 一致，任務狀態等於最後一段的狀態。 |

## When 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `"{員工}" 以原因 "{原因}" 延後 "{任務}"` | `員工`：字串。<br>`原因`：字串；預設原因或自填文字。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `tasksDefer`（`POST /tasks/{id}/defer`，body 為 `HoldReasonRequest`）。`權威狀態落地`：成功時 accepted 轉為 deferred，`deferred_by_self`=true，assignee 保留，原因寫入 `hold_reason`，並寫入 `deferred` 事件（payload by_self=true）；失敗時不變。`回寫`：回應。 |
| `"{員工}" 以 {字數} 字的自填原因延後 "{任務}"` | `員工`：字串。<br>`字數`：整數；自填原因的字數。<br>`任務`：字串；房號。 | 不支援 | `原因內容`：重複「住」字到指定字數。 | `怎麼做`：產生指定字數的自填原因，呼叫 `tasksDefer`。`權威狀態落地`：同上一列。`回寫`：回應。 |
| `"{員工}" 以原因 "{原因}" 中斷 "{任務}"` | `員工`：字串。<br>`原因`：字串。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `tasksInterrupt`（`POST /tasks/{id}/interrupt`，body 為 `HoldReasonRequest`）。`權威狀態落地`：成功時 doing 轉為 interrupted，目前段落併入 `worked_sec` 並清空 `segment_started_at`，勾記與 assignee 保留，原因寫入 `hold_reason`，並寫入 `interrupted` 事件。`回寫`：回應。 |

## Then 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `房務待接池不包含 "{任務}"` | `任務`：字串；房號。 | 不支援 | 無 | `必查`：`權威狀態`：該任務不是 `pool`。`跨視角`：其他房務員呼叫 `listDeptPool`（`GET /pool`）時看不到這筆。 |
| `"{任務}" 已勾選的 SOP 項為：` | `任務`：字串；房號。 | 支援：`項目`（必填，字串） | 無 | `必查`：`權威狀態`：`task_sop_checks` 中 checked=true 的項目剛好等於表格。`再讀確認`：`readTasks` 回應的 `checkedSopItems` 一致。 |
| `"{任務}" 的實際工時為 {分鐘} 分鐘` | `任務`：字串；房號。<br>`分鐘`：整數。 | 不支援 | 無 | `必查`：`權威狀態`：完成後 `tasks.worked_sec` 換算成分鐘等於該分鐘數，也等於 `task_events` 中各段 doing 期間的總和。`再讀確認`：`readTasks` 回應的 `accumulatedMinutes` 一致。 |
| `"{任務}" 記錄為跳過的 SOP 項為：` | `任務`：字串；房號。 | 支援：`項目`（必填，字串） | 無 | `必查`：`權威狀態`：`task_sop_checks` 中 skipped=true 的項目剛好等於表格，`completed` 事件的 payload 也記錄了這些跳過項。 |
