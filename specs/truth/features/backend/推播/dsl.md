# 推播 DSL

這個模組的專屬句型。跨模組共用句型見 [`../dsl.md`](../dsl.md)。

本模組負責觸發動作屬於基線既有行為的推播事件（例如住客掃碼、查房、作廢、改派、住客撤回、請假），以及逾時升級鏈、自動化規則建單、住客節流與收件人資格（待開通、停用不收推播）。其他推播在觸發它的模組驗證：
- 班末退池、班末仍中斷：`班末掃描`
- 工務完修、急件：`工務單`
- 抽查退回：`查房`
- 問主管回覆：`查一下`

## Given 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `住客 10 分鐘內已在 "{房號}" 送出 {筆數} 筆需求` | `房號`：字串。<br>`筆數`：整數。 | 不支援 | 節流預設為同一房 10 分鐘內最多 3 筆（營業參數可調，勾選單與自由文字合併計算）。 | `怎麼做`：以該房的住客 session 在目前 `Clock` 的 10 分鐘內送出該筆數的需求。`權威狀態落地`：這些需求已建單。 |

## When 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `住客在 "{房號}" 掃碼要 "{品項}"` | `房號`：字串。<br>`品項`：字串；勾選單品項與數量，例如 `衣架 ×4`。 | 不支援 | 無 | `怎麼做`：先以 `guestSessionsScan`（`POST /guest-sessions/scan`）取得住客 session，再以該 session（header `X-Guest-Session`）呼叫 `createGuestRequest`（`POST /guest-requests`，帶勾選單 itemId 與 quantity），品項預先對應到房務。`權威狀態落地`：建立 dept `housekeeping`、狀態 `pool`、source `guest`、`pool_source`=guest_scan 的任務，並為房務員寫入 push outbox（`PushPayload.event`=pool_new_request）。`回寫`：任務 id。 |
| `"{主管}" 查房 "{任務}"，判定 "{項目}" 不合格` | `主管`：字串。<br>`任務`：字串；待查房的房號。<br>`項目`：字串；查房項目的顯示名稱。 | 不支援 | 無 | `怎麼做`：`{項目}` 即租戶查房項目（`sop_items`，applies_to=inspection）的顯示名稱 label，先解析成對應的 item key，再以主管身分呼叫 `createInspections`（`POST /inspections`），該項 pass=false。`權威狀態落地`：依基線查房退回規則，任務轉為 accepted，redo 加 1，inspection `failed`；寫入負責人的 push outbox，推播標題原樣引用該 label（「{房號} 查房退回 · {項目}不合格」）。`回寫`：回應。 |
| `"{主管}" 以原因 "{原因}" 作廢 "{任務}"` | `主管`：字串。<br>`原因`：字串；作廢說明。<br>`任務`：字串；房號。 | 不支援 | `作廢原因代碼`：`manager_void`。 | `怎麼做`：以主管身分呼叫 `tasksCancel`（`POST /tasks/{id}/cancel`，body 為 `CancelTaskRequest`，note 為原因文字）。`權威狀態落地`：任務轉為 cancelled，並寫入負責人的 push outbox。`回寫`：回應。 |
| `"{主管}" 把 "{任務}" 改派給 "{員工}"` | `主管`：字串。<br>`任務`：字串；房號。<br>`員工`：字串；新負責人。 | 不支援 | 無 | `怎麼做`：以主管身分呼叫 `tasksReassign`（`POST /tasks/{id}/reassign`）。`權威狀態落地`：負責人改為新員工，並寫入原負責人與新負責人的 push outbox。`回寫`：回應。 |
| `住客撤回 "{房號}" 的需求` | `房號`：字串。 | 不支援 | 無 | `怎麼做`：以該房的住客 session 呼叫 `cancelGuestRequest`（`POST /guest-requests/{id}/cancel`）撤回其在待接池的需求。`權威狀態落地`：任務轉為 cancelled，`cancel_reason`=guest_withdrew，並寫入本部門的 push outbox（`PushPayload.event`=guest_withdrew）。`回寫`：回應。 |
| `"{員工}" 請假` | `員工`：字串。 | 不支援 | 無 | `怎麼做`：以主管身分呼叫 `createLeaves`（`POST /leaves`）登記該員工今日請假，觸發基線請假連鎖。`權威狀態落地`：該員工未完成的任務轉為 pool，`priority_notify`=true、`pool_source`=leave_return；寫入同樓層房務員的 push outbox。`回寫`：回應。 |

## Then 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `這次請求被擋下，提示住客洽櫃檯` | 無 | 不支援 | 無 | `必查`：`呈現結果`：回應為 4xx，錯誤代碼 `rate_limited`，訊息提示住客洽櫃檯；同次送出多項只回一次提示。`權威狀態`：`tasks`、`outbox` 與這次請求前一樣。 |
| `"{房號}" 今天沒有 "{品項}" 的需求單` | `房號`：字串。<br>`品項`：字串。 | 不支援 | 無 | `必查`：`權威狀態`：該房今天沒有內容為該品項的任務。 |
| `"{員工}" 只收到一次推播 "{標題}"` | `員工`：字串。<br>`標題`：字串；推播標題全文。 | 不支援 | 無 | `必查`：`權威狀態`：`outbox` 中該員工、該標題的 kind `push` 資料剛好一筆，重複掃描不新增。 |
