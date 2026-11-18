# 查一下 DSL

這個模組的專屬句型。跨模組共用句型見 [`../dsl.md`](../dsl.md)。

MVP 不接 LLM：回答由 SOP 或房間記錄的原文組成，並套用固定的回答模板。

## Given 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `"{房號}" 的房間記錄有以下項目：` | `房號`：字串。 | 支援：`類型`（必填，字串；工務單、常客需求）、`標題`（必填，字串）、`內容`（必填，字串）、`日期`（必填，字串 `MM/DD`） | 無 | `怎麼做`：逐列寫入該房的 `room_memory`（工務單→kind repair、常客需求→kind preference）。`權威狀態落地`：`room_memory` 已有這些項目。 |
| `"{房號}" 沒有任何房間記錄` | `房號`：字串。 | 不支援 | 無 | `怎麼做`：確認該房沒有 room_memory。`權威狀態落地`：`room_memory` 中該房沒有資料。 |
| `"{員工}" 在 "{任務}" 剛跳過 SOP 項 "{項目}"` | `員工`：字串。<br>`任務`：字串；房號。<br>`項目`：字串。 | 不支援 | 無 | `怎麼做`：建立該 SOP 項，並以該員工身分呼叫 `tasksSopCheck`（result=skipped）。`權威狀態落地`：任務的跳過集合有該項，並有對應事件。 |
| `"{房號}" 是今天的 VIP 房` | `房號`：字串。 | 不支援 | 無 | `怎麼做`：在 `reservations_cache` 寫入該房今日入住、attrs 含 `vip=true` 的訂單。`權威狀態落地`：`reservations_cache` 已有該訂單。 |
| `SOP "{標題}" 的內容為 "{內容}"` | `標題`：字串。<br>`內容`：字串。 | 不支援 | `更新者`：房務部。 | `怎麼做`：建立或更新該 SOP 項。`權威狀態落地`：`sop_items` 已有該標題與內容。 |
| `"{房號}" 目前的住客姓名為 "{姓名}"` | `房號`：字串。<br>`姓名`：字串。 | 不支援 | 無 | `怎麼做`：在 `reservations_cache.guest_name` 寫入該房目前的住客姓名。`權威狀態落地`：`reservations_cache` 有該姓名，這是 PII。 |
| `"{員工}" 已在 "{任務}" 問過 "{問題}"` | `員工`：字串。<br>`任務`：字串；房號。<br>`問題`：字串。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `roomMemoryAsk`（`POST /room-memories/ask`）。`權威狀態落地`：`kb_audit_logs` 有一筆。`回寫`：回答 id，供回饋或問主管使用。 |
| `"{員工}" 已把 "{問題}" 轉問 "{主管}"` | `員工`：字串。<br>`問題`：字串。<br>`主管`：字串。 | 不支援 | 無 | `怎麼做`：以該員工身分先呼叫 `roomMemoryAsk`，再呼叫 `escalateKbQuestion`（`POST /room-memories/answers/{answerId}/escalate`）。`權威狀態落地`：`kb_audit_logs.escalated`=true，`kb_escalations` 有一筆 pending。`回寫`：提問 id。 |

## When 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `"{員工}" 在 "{任務}" 打開查一下` | `員工`：字串。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `listKbSuggestions`（`GET /room-memories/suggestions?taskId=`）。`權威狀態落地`：查詢不寫資料。`回寫`：推薦問題清單。 |
| `"{員工}" 在 "{任務}" 問 "{問題}"` | `員工`：字串。<br>`任務`：字串；房號。<br>`問題`：字串。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `roomMemoryAsk`。`權威狀態落地`：寫入一筆 `kb_audit_logs`，包含 asker_id、task_id、question、cited_kind、answer。`回寫`：回答、出處、回答 id。 |
| `"{員工}" 對這則回答回饋 "{回饋}"` | `員工`：字串。<br>`回饋`：字串；有幫助或沒幫助。 | 不支援 | 無 | `怎麼做`：對 Given 留下的回答 id 呼叫 `submitKbFeedback`（`POST /room-memories/answers/{answerId}/feedback`，有幫助＝helpful true）。`權威狀態落地`：`kb_audit_logs.feedback` 寫入；選沒幫助時，寫入給 SOP 管理者的 push outbox。`回寫`：回應。 |
| `"{員工}" 對這個問題按問主管` | `員工`：字串。 | 不支援 | 無 | `怎麼做`：對 Given 留下的回答 id 呼叫 `escalateKbQuestion`。`權威狀態落地`：`kb_escalations` 新增一筆 pending，`kb_audit_logs.escalated`=true。`回寫`：提問 id。 |
| `"{主管}" 回覆這個問題 "{回覆}"` | `主管`：字串。<br>`回覆`：字串。 | 不支援 | 無 | `怎麼做`：以主管身分對 Given 留下的提問 id 呼叫 `replyKbEscalation`（`POST /room-memories/escalations/{escalationId}/reply`）。`權威狀態落地`：`kb_escalations` 轉為 answered 並寫入 reply，並寫入提問員工的 push outbox。`回寫`：回應。 |

## Then 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `推薦問題依序為：` | 無 | 支援：`問題`（必填，字串）、`範圍`（必填，字串） | 無 | `必查`：`呈現結果`：`KbSuggestion[]` 的 question 與 scope 順序剛好等於表格。 |
| `第一個推薦問題為 "{問題}"` | `問題`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：回應的第一個推薦問題相等。 |
| `回答為 "{回答}"` | `回答`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：`KbAnswer.answer` 全文相等。`權威狀態`：`kb_audit_logs.answer` 一致。 |
| `回答附以下出處：` | 無 | 支援：`類型`（必填，字串；SOP、房間記錄）、`標題`（必填，字串）、`內容`（必填，字串）、`日期`（必填，字串） | 無 | `必查`：`呈現結果`：`KbAnswer.sources` 剛好等於表格（房間記錄＝room_memory、SOP＝sop）。`權威狀態`：`kb_audit_logs.cited_refs` 一致。 |
| `系統回覆查不到 "{房號}" 的相關記錄，且沒有任何回答內容` | `房號`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：`found`=false，`notFoundMessage` 包含該房號，`answer` 為 null，`sources` 為空。`不該發生`：沒有任何沒有出處的回答文字。 |
| `查一下稽核紀錄有以下一筆：` | 無 | 支援：`提問者`（必填，字串）、`任務`（必填，字串；房號加內容）、`問題`（必填，字串）、`引用來源`（必填，字串；SOP、房間記錄、無）、`回饋`（選填，字串） | 無 | `必查`：`權威狀態`：`kb_audit_logs` 剛好有一筆符合表格的資料（引用來源：房間記錄＝room_memory、無＝none；回饋：有幫助＝helpful）。 |
| `"{主管}" 收到員工提問 "{問題}"` | `主管`：字串。<br>`問題`：字串。 | 不支援 | 無 | `必查`：`權威狀態`：`kb_escalations` 有一筆 pending，question 相等。`跨視角`：主管呼叫 `listKbEscalations`（status=pending）時看得到這筆。 |
| `"{員工}" 收到推播內容 "{內容}"` | `員工`：字串。<br>`內容`：字串。 | 不支援 | 無 | `必查`：`權威狀態`：該員工有一筆 kind `push` 的 outbox，`PushPayload.event`=kb_reply，body 包含主管回覆全文。 |
| `回答與出處都不包含 "{文字}"` | `文字`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：回答與每筆出處的所有欄位都不包含該文字。`權威狀態`：`kb_audit_logs.answer` 與 `cited_refs` 也不包含。 |
| `SOP 管理者 "{主管}" 收到通知，需要檢查 "{SOP 標題}"` | `主管`：字串；推定的 SOP 管理者。<br>`SOP 標題`：字串。 | 不支援 | 無 | `必查`：`權威狀態`：該主管有一筆 kind `push` 的 outbox，`PushPayload.event`=kb_unhelpful，title 包含該 SOP 標題。 |
