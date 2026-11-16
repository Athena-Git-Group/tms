# 櫃檯看板與派單 DSL

這個模組的專屬句型。跨模組共用句型見 [`../dsl.md`](../dsl.md)。

櫃檯 Web 的讀寫都在這裡驗：看板一次讀取（`getDeskBoard`）、單筆詳情與歷程（`readDeskTask`）、常用回覆（`listDeskQuickReplies`）、派單（`deskAssignTask`）、派出後復原（`deskUndoAssign`）、當場回答並結案（`deskAnswerTask`）。這六個端點只有櫃檯、主管、值班主管三種角色可以呼叫。派單、復原、當場回答的 When 與「已…」Given 中，被派去的部門值是 房務（`housekeeping`）、工務（`engineering`）。

**用語對照**（本模組的表格與句型共用）：

| 用語 | 權威值 |
| --- | --- |
| 待派單 | `tasks.state`=`triage`，`dept`=`frontdesk` |
| 未分部門 | `dept`=`frontdesk` |
| 已當場回答 | `state`=`done` 且 `tasks.answer` 有值；回應的 `answered`=true |
| 到手方式：班前排房、自己認領、主管指派、被轉派 | `acceptedVia`：`plan_assigned`、`self_claimed`、`manager_assigned`、`reassigned` |
| 派單方式：按鈕、拖曳 | `triaged` 事件 payload 的 `via`：`button`、`drag` |
| 取消原因：住客收回、主管取消、排房被取代、同仁撤回 | `cancel_reason`：`guest_withdrew`、`manager_void`、`plan_superseded`、`staff_withdrew` |
| 來源：住客掃碼、櫃檯開單、系統排房、同仁報修 | `source`：`guest`、`desk`、`system`、`staff` |
| 歷程事件：建立、派單、復原派單、當場回答、認領、開始、完成、勾選清潔項目 | `task_events.type`：`created`、`triaged`、`triage_undone`、`answered`、`claimed`、`started`、`completed`、`sop_checked` |

## Given 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `看板範圍內外有以下的單：` | 無 | 支援：`房號`（必填，字串）、`內容`（必填，字串）、`類別`（必填，字串；打掃、備品、修繕、其他）、`現況`（必填，字串；待派單、待認領、待開始、進行中、待查房、已送達、已完修、查房通過、已當場回答、已取消（主管取消）、已取消（住客收回））、`負責人`（選填，字串）、`時間`（選填，字串；`今天 HH:mm`、`昨天 HH:mm`、`前天 HH:mm`、`N 天前 HH:mm`。現況是待派單或待認領時是建立時間；待開始時是到手時間；其餘是完成或結束的時間）、`單號`（選填，字串；`T-` 開頭，只用於非修繕單）、`到手方式`（選填，字串；見用語對照，只用於待開始）、`經手主管`（選填，字串；指派、轉派或取消的主管）、`原負責人`（選填，字串；只用於被轉派） | `時間`：目前 `Clock`。<br>`單號`：由系統取號。<br>`到手方式`：自己認領。 | `怎麼做`：逐列建立任務與能推導出該現況的事件歷程，事件時間照 `時間` 欄。已送達、已當場回答、查房通過、已取消在結束當下寫 `tasks.ended_at`；待查房與已完修不寫 `ended_at`。已當場回答另寫 `tasks.answer` 與 `answered` 事件、`dept` 維持 `frontdesk`。到手方式決定最近一筆是系統操作的 `assigned`、`claimed`、主管的 `assigned` 或 `reassigned`。`權威狀態落地`：`tasks`（含 `task_no`、`ended_at`、`done_at`、`cancel_reason`）與 `task_events` 一致。`回寫`：房號對應的 task id。 |
| `"{任務}" 的單在 "{時刻}" 被同仁退回待認領` | `任務`：字串；房號。<br>`時刻`：字串 `HH:mm`。 | 不支援 | 無 | `怎麼做`：在該時刻為這張待認領的單補一筆認領與一筆 `returned_to_pool` 事件。`權威狀態落地`：單仍是 `pool`、沒有負責人，最新一筆 `returned_to_pool` 的時間是該時刻。 |
| `以下房間現在有住客：` | 無 | 支援：`房號`（必填，字串）、`住客`（必填，字串）、`VIP`（選填，是或否）、`入住日`（選填，字串 `M/D`）、`退房日`（選填，字串 `M/D`） | `VIP`：否。<br>`入住日`：昨天。<br>`退房日`：明天。 | `怎麼做`：在 `reservations_cache` 建立該房入住中的訂房（入住時間已到、退房時間未到、沒有被取代）。`權威狀態落地`：訂房已存在；沒有列出的房間沒有入住中的訂房。 |
| `飯店的客房樓層為 {樓層清單}` | `樓層清單`：以 `、` 分隔的整數。 | 不支援 | 無 | `怎麼做`：在房間主檔為每個樓層各建至少一間客房，其他樓層沒有客房。`權威狀態落地`：`rooms` 的樓層集合等於清單。 |
| `飯店的分類規則把含 "{關鍵字}" 的文字分給 "{部門}"` | `關鍵字`：字串。<br>`部門`：字串；房務、工務。 | 不支援 | 無 | `怎麼做`：在租戶設定的文字分類規則加一條關鍵字規則。`權威狀態落地`：規則已啟用；其他文字不命中任何規則。 |
| `"{櫃檯}" 已復原 "{任務}" 的派單` | `櫃檯`：字串。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `deskUndoAssign` 並確認成功。`權威狀態落地`：單回到 `triage`、`dept`=`frontdesk`，多一筆 `triage_undone`。 |
| `"{櫃檯}" 已當場回答 "{任務}"，回覆 "{回覆}"` | `櫃檯`：字串。<br>`任務`：字串；房號。<br>`回覆`：字串。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `deskAnswerTask` 並確認成功。`權威狀態落地`：單為 `done`、`tasks.answer` 等於回覆、有一筆 `answered` 事件。 |
| `"{房號}" 的住客已自己收回這張需求` | `房號`：字串。 | 不支援 | 無 | `怎麼做`：以該房的住客 session 呼叫 `cancelGuestRequest`（`POST /guest-requests/{id}/cancel`）。`權威狀態落地`：單為 `cancelled`、`cancel_reason`=`guest_withdrew`、`ended_at` 已寫入。 |
| `"{員工}" 已認領 "{任務}"` | `員工`：字串。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `tasksClaim` 並確認成功。`權威狀態落地`：單為 `accepted`、負責人是該員工、有一筆 `claimed`。 |
| `"{員工}" 已開始處理 "{任務}"` | `員工`：字串。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `tasksStart` 並確認成功。`權威狀態落地`：單為 `doing`、有一筆 `started`。 |
| `"{任務}" 被 "{員工}" 認領後因請假退回待認領` | `任務`：字串；房號。<br>`員工`：字串。 | 不支援 | 無 | `怎麼做`：先讓該員工認領，再以主管身分登記他今日請假，觸發既有的請假退回。`權威狀態落地`：單為 `pool`、沒有負責人、`pool_source`=`leave_return`，最新一筆事件不是 `triaged`。 |
| `派出後過了 {秒數} 秒` | `秒數`：整數。 | 不支援 | 無 | `怎麼做`：把測試 `Clock` 往後推該秒數，不執行排程掃描。`權威狀態落地`：無。 |
| `"{員工}" 已在 "{任務}" 勾選 {項數} 項清潔項目` | `員工`：字串。<br>`任務`：字串；房號。<br>`項數`：整數。 | 不支援 | 飯店啟用的清潔 SOP 共 8 項。 | `怎麼做`：以該員工身分逐項勾選。`權威狀態落地`：`task_sop_checks` 有該項數的勾選，並各有一筆 `sop_checked` 事件。 |
| `飯店的住客資訊頁依序如下：` | 無 | 支援：`標題`（必填，字串）、`全文`（必填，字串） | 無 | `怎麼做`：把租戶設定的 `guest_info_pages` 寫成表格的內容與順序，每頁的鍵由測試自編。`權威狀態落地`：`tenant_config` 已更新。 |
| `飯店沒有設定住客資訊頁` | 無 | 不支援 | 無 | `怎麼做`：清空租戶設定的 `guest_info_pages`。`權威狀態落地`：設定為空。 |

## When 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `"{櫃檯}" 以拖曳把 "{任務}" 派給 "{部門}"` | `櫃檯`：字串。<br>`任務`：字串；房號。<br>`部門`：字串；房務、工務。 | 不支援 | 無 | `怎麼做`：同介面根的 `"{櫃檯}" 把 "{任務}" 派給 "{部門}"`，但 `DeskAssignRequest.via`=`drag`。`權威狀態落地`：同該句型，`triaged` 事件的 `via`=`drag`。`回寫`：回應。 |
| `"{櫃檯}" 不帶防重複鍵把 "{任務}" 派給 "{部門}"` | 同上。 | 不支援 | 無 | `怎麼做`：呼叫 `deskAssignTask` 但不帶 `Idempotency-Key`。`權威狀態落地`：回 400，單不變。`回寫`：回應。 |
| `"{櫃檯}" 用同一把鍵把 "{任務}" 派給 "{部門}" 重送一次` | 同上。 | 不支援 | 無 | `怎麼做`：重用 Given 那次派單的 `Idempotency-Key` 與相同的請求內容，再呼叫一次 `deskAssignTask`。`權威狀態落地`：不新增事件，單不變。`回寫`：兩次的回應。 |
| `"{櫃檯}" 不帶防重複鍵復原 "{任務}" 的派單` | `櫃檯`：字串。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：呼叫 `deskUndoAssign` 但不帶 `Idempotency-Key`。`權威狀態落地`：回 400，單不變。`回寫`：回應。 |
| `"{員工}" 當場回答 "{任務}"，回覆 "{回覆}"` | `員工`：字串。<br>`任務`：字串；房號。<br>`回覆`：字串；照字面送出，含前後空白。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `deskAnswerTask`（`POST /desk/tasks/{id}/answer`），body `DeskAnswerRequest{answer}`，帶新的 `Idempotency-Key`。`權威狀態落地`：成功時 `triage`→`done`，`tasks.answer` 寫入去掉頭尾空白的回覆，`done_at`、`ended_at` 寫入目前 `Clock`，`dept` 維持 `frontdesk`，`tasks.note` 不動，寫入一筆 `answered` 事件（payload 帶 `answer`，操作者與角色是該員工）；失敗時單與事件不變。`回寫`：回應。 |
| `"{櫃檯}" 以 {字數} 個字的回覆當場回答 "{任務}"` | `櫃檯`：字串。<br>`字數`：整數；0 代表空字串。<br>`任務`：字串；房號。 | 不支援 | 回覆內容是重複的中文字，沒有空白。 | `怎麼做`：同上一句，回覆由測試產生該字數的字串。`回寫`：回應與送出的字串。 |
| `"{櫃檯}" 以只有空白的回覆當場回答 "{任務}"` | `櫃檯`：字串。<br>`任務`：字串；房號。 | 不支援 | 回覆是 3 個半形空白。 | `怎麼做`：同上，回覆只有空白。`回寫`：回應。 |
| `"{櫃檯}" 不帶防重複鍵當場回答 "{任務}"，回覆 "{回覆}"` | `櫃檯`：字串。<br>`任務`：字串；房號。<br>`回覆`：字串。 | 不支援 | 無 | `怎麼做`：呼叫 `deskAnswerTask` 但不帶 `Idempotency-Key`。`權威狀態落地`：回 400，單不變。`回寫`：回應。 |
| `"{房號}" 的住客打字送出 "{內容}"` | `房號`：字串。<br>`內容`：字串。 | 不支援 | 無 | `怎麼做`：以該房的住客 session 呼叫 `createGuestRequest`（`POST /guest-requests`），只帶 `text`。`權威狀態落地`：先過分類規則，命中單一部門就建成該部門的 `pool`，否則建成 `triage`、`dept`=`frontdesk`；`tasks.note` 記住客原文。`回寫`：任務 id，之後可用房號指這張單。 |
| `"{員工}" 讀取 "{任務}" 的櫃檯詳情` | `員工`：字串。<br>`任務`：字串；房號或工務單號。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `readDeskTask`（`GET /desk/tasks/{id}`）。`權威狀態落地`：查詢不寫資料。`回寫`：`DeskTaskDetail`。 |
| `"{員工}" 讀取一張不存在的單的櫃檯詳情` | `員工`：字串。 | 不支援 | 無 | `怎麼做`：以隨機產生的 id 呼叫 `readDeskTask`。`回寫`：回應（404 `not_found`）。 |
| `"{員工}" 讀取常用回覆` | `員工`：字串。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `listDeskQuickReplies`（`GET /desk/quick-replies`）。`回寫`：`DeskQuickReply` 陣列。 |

## Then 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `櫃檯看板剛好有這些房號的單：{房號清單}` | `房號清單`：以 `、` 分隔的房號。 | 不支援 | 無 | `必查`：`呈現結果`：回應 200，`DeskBoard.tasks[].roomNo` 的集合剛好等於清單，沒有多也沒有少。`權威狀態`：清單內每張單的 `ended_at` 是空的或落在 `businessDate`；清單外、本案例建立的單 `ended_at` 都早於 `businessDate`。 |
| `櫃檯看板上 "{任務}" 的資料如下：` | `任務`：字串；房號或工務單號。 | 支援：`項目`（必填，字串；狀態、已當場回答、部門、單號）、`值`（必填，字串） | 無 | `必查`：`呈現結果`：該單的 `DeskTask.state`（依介面根狀態用語對照）、`answered`（是＝true）、`dept`（未分部門＝`frontdesk`）、`displayNo` 與表格相符。`權威狀態`：與 `tasks.state`、`tasks.answer` 是否有值、`tasks.dept`、`tasks.task_no` 或 `repair_tickets.ticket_no` 一致；`state` 的值在既有十個值之內。 |
| `櫃檯看板上 "{任務}" 的等待從 "{時刻}" 起算` | `任務`：字串；房號。<br>`時刻`：字串 `HH:mm`。 | 不支援 | 無 | `必查`：`呈現結果`：該單的 `waitingSince` 等於今日該時刻；`triage` 的單另確認它等於 `createdAt`。`權威狀態`：等於該單 `created` 與 `returned_to_pool` 兩種事件中最新一筆的時間，`triaged` 事件不影響。`跨視角`：`pool` 的單，該部門同仁呼叫 `listDeptPool` 看到的等待起算相同。 |
| `櫃檯看板上各單的到手方式如下：` | 無 | 支援：`房號`（必填，字串）、`到手方式`（必填，字串；見用語對照） | 無 | `必查`：`呈現結果`：每列的 `acceptedVia` 相符，`acceptedAt` 有值。`權威狀態`：與該單最近一筆 `claimed`、`assigned` 或 `reassigned` 事件的種類與操作者一致。 |
| `櫃檯看板上 "{任務}" 沒有任何急件標記` | `任務`：字串；工務單號。 | 不支援 | 無 | `必查`：`呈現結果`：該單在 `DeskBoard.tasks` 內，物件上沒有 `urgent` 或任何表示急件的欄位。`不該發生`：急件單的 `waitingSince` 算法與一般單不同。 |
| `櫃檯看板上住客打字的需求如下：` | 無 | 支援：`房號`（必填，字串）、`原文`（必填，字串）、`原文語言`（選填，字串；日文＝`ja`、英文＝`en`）、`翻譯`（選填，字串；留空代表沒有） | 無 | `必查`：`呈現結果`：每列的 `guestTyped`=true，`guestText.original`、`lang`、`translated` 相符，留空的翻譯是 null。`權威狀態`：與 `tasks.note` 的原文與 `llm_translated` 一致；後端沒有自己產生翻譯。 |
| `櫃檯看板上各單的住客如下：` | 無 | 支援：`房號`（必填，字串）、`住客`（選填，字串；留空代表空房）、`VIP`（必填，是或否） | 無 | `必查`：`呈現結果`：每列的 `guestName`（留空＝null）與 `vip` 相符。`權威狀態`：與該房入住中的訂房一致。 |
| `櫃檯看板帶的後端時間是今天 "{時刻}"，今天的日期與後端時鐘的日期相同` | `時刻`：字串 `HH:mm`。 | 不支援 | 無 | `必查`：`呈現結果`：`serverTime` 等於目前 `Clock`（該時刻），`businessDate` 等於 `Clock` 在伺服器時區的日期。 |
| `櫃檯看板帶的樓層依序為 {樓層清單}` | `樓層清單`：以 `、` 分隔的整數。 | 不支援 | 無 | `必查`：`呈現結果`：`DeskBoard.floors` 剛好等於清單，由低到高、不重複。`權威狀態`：等於房間主檔的客房樓層。 |
| `櫃檯看板帶的本人今日張數是已派單 {派單張數} 張、當場回答 {回答張數} 張` | `派單張數`、`回答張數`：整數。 | 不支援 | 無 | `必查`：`呈現結果`：`myToday.assignedCount`、`answeredCount` 相符。`權威狀態`：已派單等於呼叫者今天的 `triaged` 事件扣掉緊接著被 `triage_undone` 的；當場回答等於呼叫者今天的 `answered` 事件。`不該發生`：別人的事件被算進來。 |
| `"{任務}" 仍是待派單，歷程沒有任何派單紀錄` | `任務`：字串；房號。 | 不支援 | 無 | `必查`：`權威狀態`：`tasks.state`=`triage`、`dept`=`frontdesk`、沒有負責人、`answer` 為空；`task_events` 沒有 `triaged`、`answered`。`跨視角`：房務與工務同仁呼叫 `listDeptPool` 都看不到這張單。 |
| `"{任務}" 在 "{部門}" 的待認領，沒有負責人` | `任務`：字串；房號。<br>`部門`：字串；房務、工務。 | 不支援 | 無 | `必查`：`權威狀態`：`tasks.state`=`pool`、`dept` 是該部門、沒有負責人。`跨視角`：該部門同仁呼叫 `listDeptPool` 看得到這張單；另一個部門的同仁看不到。`再讀確認`：`getDeskBoard` 回的這張單 `state`=`pool`、`dept` 相同。 |
| `全館沒有因為這次派單多出任何一張單` | 無 | 不支援 | 無 | `必查`：`權威狀態`：`tasks` 的總筆數與這次派單前相同，沒有新建的任務或工務單。 |
| `"{任務}" 的歷程最新一筆是 "{操作者}" 以 "{方式}" 派給 "{部門}"` | `任務`：字串；房號。<br>`操作者`：字串。<br>`方式`：字串；按鈕、拖曳。<br>`部門`：字串；房務、工務。 | 不支援 | 無 | `必查`：`權威狀態`：最新一筆 `task_events` 是 `triaged`，`actor_id` 是該員工，payload 的 `dept`、`via` 相符。`再讀確認`：`readDeskTask` 的 `timeline` 最後一筆 `type`=`triaged`、`actor.name` 是該員工、`detail.dept`、`detail.via` 相符。 |
| `"{任務}" 那筆派單記的操作者角色是 "{角色}"` | `任務`：字串；房號。<br>`角色`：字串；櫃檯、主管、值班主管。 | 不支援 | 無 | `必查`：`權威狀態`：那筆 `triaged` 的 `actor_role` 是該員工當下的角色（值班主管＝`duty_manager`，不借用 `manager`）。`再讀確認`：`timeline` 那一筆的 `actor.role` 相同。 |
| `"{任務}" 只被派出一次，派單的人是 "{操作者}"` | `任務`：字串；房號。<br>`操作者`：字串。 | 不支援 | 無 | `必查`：`權威狀態`：這張單的 `triaged` 事件剛好一筆，`actor_id` 是該員工；沒有 `answered`。 |
| `重送拿到與第一次相同的成功回應` | 無 | 不支援 | 無 | `必查`：`呈現結果`：重送回 200，body 與第一次的 `DeskTask` 相同。`權威狀態`：`processed_ops` 只有這把鍵的一筆紀錄。 |
| `這次操作被拒絕，因為 "{任務}" 已被 "{操作者}" 派給 "{部門}"` | `任務`：字串；房號。<br>`操作者`：字串。<br>`部門`：字串；房務、工務。 | 不支援 | 無 | `必查`：`呈現結果`：回 409，`Error.code`=`task_not_in_triage`，`context.resolution`=`assigned`、`actorName` 是該員工、`dept` 相符、`roomNo` 是該房號、`state`=`pool`。`權威狀態`：這次請求沒有新增事件，單維持先送到那一次的結果。 |
| `這次操作被拒絕，因為 "{任務}" 已被 "{操作者}" 當場回答` | `任務`：字串；房號。<br>`操作者`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：回 409 `task_not_in_triage`，`context.resolution`=`answered`、`actorName` 是該員工、`roomNo` 相符、`state`=`done`。`權威狀態`：沒有新增事件，`tasks.answer` 仍是先送到的那一段。 |
| `這次操作被拒絕，因為 "{任務}" 已被住客收回` | `任務`：字串；房號。 | 不支援 | 無 | `必查`：`呈現結果`：回 409 `task_not_in_triage`，`context.resolution`=`guest_withdrew`、`roomNo` 相符、`state`=`cancelled`。`權威狀態`：沒有新增事件，單仍是 `cancelled`。 |
| `這次操作被拒絕，因為 "{任務}" 已經不是待派單` | `任務`：字串；房號。 | 不支援 | 無 | `必查`：`呈現結果`：回 409 `task_not_in_triage`，`context.resolution`=`other`，帶 `state` 與 `roomNo`。`權威狀態`：沒有新增事件，單不變。 |
| `"{任務}" 回到待派單，還沒分部門` | `任務`：字串；房號。 | 不支援 | 無 | `必查`：`呈現結果`：回 200，`DeskTask.state`=`triage`、`dept`=`frontdesk`。`權威狀態`：`tasks.state`=`triage`、`dept`=`frontdesk`、`reminder_stage` 與 `pool_source` 為 NULL、沒有負責人。`跨視角`：原本被派去的部門呼叫 `listDeptPool` 已看不到這張單。 |
| `"{任務}" 給人看的歷程沒有派單這一步` | `任務`：字串；房號。 | 不支援 | 無 | `必查`：`再讀確認`：`readDeskTask` 的 `timeline` 沒有 `triaged`、也沒有 `triage_undone`，其餘各筆與派單前相同。 |
| `"{任務}" 的事件紀錄保留派給 "{部門}" 與復原各一筆，兩筆相鄰` | `任務`：字串；房號。<br>`部門`：字串；房務、工務。 | 不支援 | 無 | `必查`：`權威狀態`：`task_events` 有一筆 `triaged`（payload `dept` 是該部門）與緊接其後、`seq` 相差 1 的 `triage_undone`（payload `dept` 是被收回的部門），兩筆都沒有被刪除或改寫。 |
| `這次復原被拒絕，因為已經超過可以復原的時間` | 無 | 不支援 | 無 | `必查`：`呈現結果`：回 409，`Error.code`=`undo_expired`，`context` 帶 `roomNo`、`dept`。`權威狀態`：沒有新增事件，單不變。 |
| `這次復原被拒絕，因為 "{員工}" 已經認領，這張單留在 "{部門}"` | `員工`：字串。<br>`部門`：字串；房務、工務。 | 不支援 | 無 | `必查`：`呈現結果`：回 409 `undo_claimed`，`context.assigneeName` 是該員工、`dept` 相符、帶 `roomNo`。`權威狀態`：沒有新增事件；單與負責人不變，該員工那邊沒有任何變化。 |
| `這次復原被拒絕，因為住客已經收回這張需求` | 無 | 不支援 | 無 | `必查`：`呈現結果`：回 409 `undo_guest_withdrew`，`context` 帶 `roomNo`；`code` 與被同仁認領時不同。`權威狀態`：沒有新增事件，單仍是 `cancelled`。 |
| `這次復原被拒絕，因為這張單已經不是剛派出的樣子` | 無 | 不支援 | 無 | `必查`：`呈現結果`：回 409 `undo_unavailable`，`context` 帶 `state`、`roomNo`。`權威狀態`：沒有新增事件，單不變。 |
| `"{任務}" 已由 "{櫃檯}" 當場回答結案，回覆為 "{回覆}"` | `任務`：字串；房號。<br>`櫃檯`：字串。<br>`回覆`：字串。 | 不支援 | 無 | `必查`：`權威狀態`：`tasks.state`=`done`、`tasks.answer` 完全等於回覆、`done_at` 與 `ended_at` 已寫入；有一筆 `answered` 事件，操作者是該員工。`再讀確認`：`readDeskTask` 的 `answered`=true、`answer` 等於回覆、`answeredByName` 是該員工。 |
| `"{任務}" 的回覆長度是 {字數} 個字` | `任務`：字串；房號。<br>`字數`：整數。 | 不支援 | 無 | `必查`：`呈現結果`：回 200。`權威狀態`：`tasks.state`=`done`，`tasks.answer` 的字數等於該數字，內容等於送出的字串。 |
| `"{任務}" 的住客原文仍是 "{原文}"` | `任務`：字串；房號。<br>`原文`：字串。 | 不支援 | 無 | `必查`：`權威狀態`：`tasks.note` 與當場回答前一字不差。`再讀確認`：`readDeskTask` 的 `guestText.original` 等於原文。 |
| `"{任務}" 仍未分部門，沒有負責人，不算任何部門做掉的單` | `任務`：字串；房號。 | 不支援 | 無 | `必查`：`權威狀態`：`tasks.dept`=`frontdesk`、沒有負責人、沒有 `completed` 事件。`跨視角`：房務與工務同仁的今日進度（`getMyToday`）完成數與回答前相同。 |
| `"{任務}" 的歷程最新一筆是 "{操作者}" 的當場回答，帶回覆 "{回覆}"` | `任務`：字串；房號。<br>`操作者`：字串。<br>`回覆`：字串。 | 不支援 | 無 | `必查`：`權威狀態`：最新一筆 `task_events` 是 `answered`，`actor_id` 是該員工，payload `answer` 等於回覆。`再讀確認`：`timeline` 最後一筆 `type`=`answered`、`detail.answer` 相同。 |
| `"{房號}" 的住客在進度頁讀到回覆 "{回覆}"` | `房號`：字串。<br>`回覆`：字串。 | 不支援 | 無 | `必查`：`跨視角`：以該房的住客 session 呼叫 `listGuestRequests`（`GET /guest-requests`），這張需求的 `GuestRequest.answer` 等於回覆。 |
| `櫃檯詳情的資料如下：` | 無 | 支援：`項目`（必填，字串；住客、入住日、退房日、來源、開單人、回覆、回答的人、到手方式、到手時間、經手的人、原負責人、取消原因、取消的人、結束時間、完修結果、驗收主管、SOP 進度）、`值`（選填，字串；留空代表沒有） | 無 | `必查`：`呈現結果`：`DeskTaskDetail` 對應欄位相符：`guest.name`／`checkinDate`／`checkoutDate`（空房時 `guest` 為 null）、`source`、`createdByName`、`answer`、`answeredByName`、`accepted.via`／`at`／`byName`／`previousAssigneeName`、`cancellation.reason`／`byName`、`endedAt`、`repair.resolutionType`（已修好＝`fixed`、不需處理＝`no_action_needed`）／`verifiedByName`、`sop`（`done/total`）。回應不帶住客人數。`權威狀態`：與 `reservations_cache`、`tasks`、`repair_tickets`、對應的 `task_events` 一致。 |
| `櫃檯詳情的歷程依序為：` | 無 | 支援：`事件`（必填，字串；見用語對照）、`操作者`（必填，字串）、`操作者身分`（必填，字串；住客、系統、櫃檯、房務、工務、主管、值班主管） | 無 | `必查`：`呈現結果`：`timeline` 的筆數、順序與表格相同，由舊到新；每筆的 `type`、`actor.name`、`actor.kind`（住客＝`guest`、系統＝`system`，其餘＝`staff` 並對 `actor.role`）相符，都帶 `at`。`權威狀態`：與 `task_events` 依 `seq` 排列的結果一致。 |
| `"{任務}" 的歷程比這一步之前多一筆 "{事件}"，原有各筆都沒有改動` | `任務`：字串；房號。<br>`事件`：字串；見用語對照。 | 不支援 | 無 | `必查`：`權威狀態`：`task_events` 比 When 之前多一筆該種事件，之前每一筆的 `seq`、`type`、操作者、payload、時間都與 When 之前的快照相同。`再讀確認`：`readDeskTask` 的 `timeline` 前幾筆不變，最後多一筆。 |
| `櫃檯詳情的歷程沒有 "{事件}" 這種事件` | `事件`：字串；見用語對照。 | 不支援 | 無 | `必查`：`呈現結果`：`timeline` 沒有任何一筆是該種事件。`權威狀態`：`task_events` 確實有這種事件，是讀取時略過，不是沒有記。 |
| `常用回覆的標題依序為 {標題清單}` | `標題清單`：以 `、` 分隔的標題。 | 不支援 | 無 | `必查`：`呈現結果`：回 200，陣列每筆 `title` 的順序剛好等於清單，每筆都帶 `key` 與 `body`。 |
| `常用回覆 "{標題}" 的全文是 "{全文}"` | `標題`：字串。<br>`全文`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：該標題那一筆的 `body` 完全等於全文。`權威狀態`：等於租戶設定 `guest_info_pages` 的那一頁。 |
| `常用回覆是空的` | 無 | 不支援 | 無 | `必查`：`呈現結果`：回 200，body 是空陣列。 |
