# 工務單 DSL

這個模組的專屬句型。跨模組共用句型見 [`../dsl.md`](../dsl.md)。

工務單在權威狀態裡是工務部門的修繕任務：dept `engineering`、type `repair`、source `staff`。工務單狀態用語見共用 DSL 的對照表。

## Given 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `本飯店下一張工務單號為 "{單號}"` | `單號`：字串；`W-` 加 4 位數字。 | 不支援 | 無 | `怎麼做`：把租戶的工務單序號設定成下一張會是該單號。`權威狀態落地`：`repair_ticket_counters.last_no` 等於單號減 1。 |
| `已有以下工務單：` | 無 | 支援：`單號`（必填，字串）、`房號`（必填，字串）、`類別`（必填，字串；冷氣、水電、家具／設備、門鎖、其他）、`狀態`（必填，字串）、`報修人`（必填，字串）、`說明`（選填，字串）、`處理人`（選填，字串）、`照片張數`（選填，整數）、`急件`（選填，是或否）、`連結任務`（選填，字串；原任務房號） | `照片張數`：1。<br>`急件`：否。 | `怎麼做`：逐列建立修繕任務（`tasks`，type=repair）、`repair_tickets`、已上傳的 `repair_photos`，以及符合狀態的事件歷程。有處理人時，assignee 就是處理人。`權威狀態落地`：`tasks`、`repair_tickets`、`repair_photos` 與 `task_events` 一致。`回寫`：單號對應的 task id。 |
| `"{房號}" 今天是 VIP 房，住客 "{時刻}" 入住` | `房號`：字串。<br>`時刻`：字串 `HH:mm`。 | 不支援 | 無 | `怎麼做`：在訂房快取寫入該房今日入住、`vip=true` 的訂單。`權威狀態落地`：`reservations_cache` 已有該訂單。 |
| `"{員工}" 已對 "{房號}" 選擇工務單類別 "{類別}" 並看到 "{單號}" 的重複提示` | `員工`：字串。<br>`房號`：字串。<br>`類別`：字串。<br>`單號`：字串；既有工務單號。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `precheckRepairTicket`（`POST /repair-tickets/precheck`），並確認 `duplicate.ticketNo` 是該單號。`權威狀態落地`：`repair_ticket_contributors` 已有該員工與這張單的資料列。 |
| `"{員工}" 為 "{房號}" 的工務單準備了 {張數} 張照片，其中 {失敗數} 張上傳失敗` | `員工`：字串。<br>`房號`：字串。<br>`張數`：整數。<br>`失敗數`：整數。 | 不支援 | 無 | `怎麼做`：以該員工身分對每張照片呼叫 `createRepairPhoto`（`POST /repair-photos`，purpose=report）取得 id；成功的再呼叫 `uploadRepairPhotoContent`（`PUT /repair-photos/{photoId}/content`），失敗的不上傳內容，模擬上傳中斷。`權威狀態落地`：成功的 `repair_photos.status`=uploaded，失敗的仍是 pending。`回寫`：全部照片 id。 |
| `"{員工}" 已重新上傳失敗的那張照片並成功` | `員工`：字串。 | 不支援 | 無 | `怎麼做`：用原本的照片 id 呼叫 `uploadRepairPhotoContent`。`權威狀態落地`：所有照片的 `repair_photos.status` 都是 uploaded。 |
| `"{單號}" 已由 "{工務}" 完修` | `單號`：字串。<br>`工務`：字串。 | 不支援 | 無 | `怎麼做`：以工務身分建立並上傳 1 張 purpose=completion 的照片，再呼叫 `tasksComplete` 帶 `photoIds`。`權威狀態落地`：工務單為 done，連結原任務的停住原因已改為「工務已修好：<類別>」。 |

## When 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `"{員工}" 中斷 "{任務}" 並開立以下工務單：` | `員工`：字串。<br>`任務`：字串；原任務房號。 | 支援：`類別`（必填，字串）、`說明`（選填，字串）、`照片張數`（必填，整數）、`急件`（必填，是或否） | 無 | `怎麼做`：逐張呼叫 `createRepairPhoto` 與 `uploadRepairPhotoContent`，再呼叫 `createRepairTicket`（`POST /repair-tickets`，`interruptSourceTask`=true），房號由原任務帶入。`權威狀態落地`：成功時建立 pool 的修繕任務、`repair_tickets`、綁定的 `repair_photos`，以及 `created` 事件（payload 只記 photo_count）；原任務轉為 interrupted，`hold_reason`=「等工務：<類別>」，`linked_repair_task_id` 指向新單。失敗時兩者都不變。`回寫`：回應與新單號。 |
| `"{員工}" 從 "{任務}" 開立以下工務單：` | `員工`：字串。<br>`任務`：字串；原任務房號。 | 支援：`類別`（必填，字串）、`說明`（選填，字串）、`照片張數`（必填，整數）、`急件`（必填，是或否） | 無 | `怎麼做`：同上一列，但 `interruptSourceTask`=false。`權威狀態落地`：成功時建立工務單；急件會寫入全體工務與主管的 push outbox；原任務不變。`回寫`：回應與新單號。 |
| `"{員工}" 從 "{任務}" 開立以下工務單，同一請求共送出 {次數} 次：` | `員工`：字串。<br>`任務`：字串。<br>`次數`：整數；包含第一次。 | 支援：同 `從 … 開立以下工務單：` | 無 | `怎麼做`：照片上傳後，用同一個 `Idempotency-Key` 連續呼叫 `createRepairTicket`，內容相同。`權威狀態落地`：只建立一張工務單，後續請求由 `processed_ops` 回放第一次的回應。`回寫`：每次回應。 |
| `"{員工}" 中斷 "{任務}" 並以已準備的照片開立水電工務單` | `員工`：字串。<br>`任務`：字串。 | 不支援 | `類別`：水電。<br>`急件`：否。 | `怎麼做`：用 Given 準備的全部照片 id 呼叫 `createRepairTicket`（`interruptSourceTask`=true）。`權威狀態落地`：只有每張照片都有內容時才建立工務單並中斷原任務；否則都不變。`回寫`：回應。 |
| `"{員工}" 對 "{房號}" 選擇工務單類別 "{類別}"` | `員工`：字串。<br>`房號`：字串。<br>`類別`：字串。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `precheckRepairTicket`（`POST /repair-tickets/precheck`）。`權威狀態落地`：查詢不建立工務單；觸發重複提示時，在 `repair_ticket_contributors` 記錄該員工。`回寫`：重複單號與狀態、急件建議與提示文字。 |
| `"{員工}" 補 {張數} 張照片到 "{單號}"` | `員工`：字串。<br>`張數`：整數。<br>`單號`：字串。 | 不支援 | 無 | `怎麼做`：以該員工身分建立並上傳照片，再呼叫 `appendRepairTicketPhotos`（`POST /repair-tickets/{ticketId}/photos`）。`權威狀態落地`：工務單照片增加，事件只記增加的張數。`回寫`：回應。 |
| `"{員工}" 撤回 "{單號}"` | `員工`：字串。<br>`單號`：字串。 | 不支援 | 無 | `怎麼做`：以報修人身分呼叫 `withdrawRepairTicket`（`POST /repair-tickets/{ticketId}/withdraw`）。`權威狀態落地`：只有 pool 能轉為 cancelled，`cancel_reason`=staff_withdrew，並寫入 `cancelled` 事件；其他狀態都不變。`回寫`：回應。 |
| `"{工務}" 附 {張數} 張完修照片、處理說明 "{說明}" 完成 "{單號}"` | `工務`：字串。<br>`張數`：整數。<br>`說明`：字串。<br>`單號`：字串。 | 不支援 | 無 | `怎麼做`：以工務身分建立並上傳 purpose=completion 的照片，再呼叫 `tasksComplete`（`POST /tasks/{id}/complete`，body 為 `CompleteTaskRequest`）。`權威狀態落地`：工務單轉為 done，綁定完修照片並寫入 `repair_tickets.resolution_note`；連結原任務的 `hold_reason` 改為「工務已修好：<類別>」，但狀態不變；寫入報修人的 push outbox。`回寫`：回應。 |
| `"{工務}" 不附完修照片完成 "{單號}"` | `工務`：字串。<br>`單號`：字串。 | 不支援 | 無 | `怎麼做`：以工務身分呼叫 `tasksComplete`，`photoIds` 是空的。`權威狀態落地`：工務單維持 doing。`回寫`：回應。 |

## Then 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `系統建立工務單 "{單號}"，房號 "{房號}"、報修人 "{員工}"` | `單號`：字串。<br>`房號`：字串。<br>`員工`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：`RepairTicket.ticketNo` 是該單號。`權威狀態`：`repair_tickets` 有一筆，ticket_no、room_id、reporter_id、source_task_id 相符；對應的 `tasks` 是 source `staff`、dept `engineering`、type `repair`。 |
| `系統沒有建立工務單` | 無 | 不支援 | 無 | `必查`：`不該發生`：本案例執行前後，`repair_tickets` 與 type=repair 的 `tasks` 筆數不變。 |
| `工務待接池包含 "{單號}"` | `單號`：字串。 | 不支援 | 無 | `必查`：`權威狀態`：該單是 pool。`跨視角`：工務員工呼叫 `listDeptPool`（`GET /pool`）時看得到這張單；它的 `task_events` 沒有 triaged，也沒有任何主管審核事件。 |
| `工務待接池不包含 "{單號}"` | `單號`：字串。 | 不支援 | 無 | `必查`：`跨視角`：工務員工呼叫 `listDeptPool` 時看不到這張單。 |
| `工務待接池順序為 {單號清單}` | `單號清單`：字串；以 `、` 分隔的單號。 | 不支援 | 無 | `必查`：`跨視角`：工務員工呼叫 `listDeptPool`，`items[].ticketNo` 的順序剛好等於清單。 |
| `系統建議設為急件，提示 "{提示}"` | `提示`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：`urgentSuggestion.suggested`=true，`message` 相等。`不該發生`：沒有建立工務單。 |
| `以下員工都收到 "{單號}" 的急件推播：` | `單號`：字串。 | 支援：`員工`（必填，字串） | 無 | `必查`：每位員工都有一筆 kind `push` 的 outbox，`PushPayload.event`=repair_urgent，`target.highlightTaskId` 是該單。`跨視角`：推播消費者已寫入對應資料列。 |
| `系統提示同房已有未結案的工務單 "{單號}"，狀態為 "{狀態}"` | `單號`：字串。<br>`狀態`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：`duplicate.ticketNo` 與 `duplicate.state` 相符（狀態用語見對照表）。`不該發生`：沒有建立工務單。 |
| `"{單號}" 共有 {張數} 張照片` | `單號`：字串。<br>`張數`：整數。 | 不支援 | 無 | `必查`：`權威狀態`：該工務單 purpose=report 的 `repair_photos` 筆數等於張數。`跨視角`：處理人呼叫 `readRepairTicket`（`GET /repair-tickets/{ticketId}`）時 `photoCount` 相同。 |
| `"{房號}" 未結案的 "{類別}" 工務單只有 {張數} 張` | `房號`：字串。<br>`類別`：字串。<br>`張數`：整數。 | 不支援 | 無 | `必查`：`權威狀態`：`repair_tickets` JOIN `tasks`，該房該類別、state 不是 done 也不是 cancelled 的筆數等於張數。`跨視角`：工務待接池裡該房該類別的單也一致。 |
| `"{單號}" 的處理說明為 "{說明}"` | `單號`：字串。<br>`說明`：字串。 | 不支援 | 無 | `必查`：`權威狀態`：`repair_tickets.resolution_note` 相等。`再讀確認`：`readRepairTicket` 的 `resolutionNote` 一致。 |
