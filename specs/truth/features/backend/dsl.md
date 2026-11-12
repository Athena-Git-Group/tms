# 後端共用 DSL

這裡只放跨模組共用的句型。模組專屬句型寫在 `{模組}/dsl.md`。

**BDD techstack**：Cucumber-JVM ＋ Spring Boot Test（`@SpringBootTest(webEnvironment = RANDOM_PORT)`）。Postgres 與 Kafka 用 Testcontainers 啟動。完整定義見 `specs/truth/techstack.md`。

**共通實作約定**（每個 row 都適用，不再逐列重複）：

- **租戶**：預設只有一個租戶「測試飯店」。員工的姓名就是登入識別，每位員工呼叫 API 時都用自己的 JWT。
- **時間**：系統時間取自注入的可調 `Clock`。`現在時間是` 會設定這個時鐘；沒設定時預設為 `10:00`。
- **任務識別**：句中的 `任務` 參數寫房號（例如 `305`），就代表該房在本案例 Given 中唯一的那筆任務；寫工務單號（例如 `W-0412`）就代表那張工務單。同一案例不得出現同房兩筆無法區分的任務。
- **權威狀態**：以 Postgres 的 `tasks`、`task_events`、`outbox`、`processed_ops` 與本 plan 新增的資料為準，不以 API 回應為準。
- **寫入**：每次寫入都帶新的 `Idempotency-Key`；只有句型明確要求重送時，才重用同一個 key。

## 狀態用語對照

| 用語 | 權威值 |
| --- | --- |
| 待認領 | `pool` |
| 待開始 | `accepted` |
| 進行中 | `doing` |
| 已延後 | `deferred`，有負責人（本人延後） |
| 已中斷 | `interrupted`（App／原型內部命名為 `paused`，是同一個使用者可見狀態的工程端命名差異，不是另一個狀態；不影響本節任何驗收句型） |
| 待查房 | 打掃類 `done` |
| 已送達 | 非打掃類 `done` |
| 查房通過 | `inspected`，inspection `passed` |
| 完成 · 免查房 | `inspected`，inspection `waived` |
| 已取消 | `cancelled` |
| 已接單（工務單） | `accepted` |
| 維修中（工務單） | `doing` |
| 已完修（工務單） | `done`，已完修、等主管驗收 |
| 已結案（工務單） | `closed`；主管驗收通過，或以不需處理（`no_action_needed`）完修時直接結案 |
| 已撤回（工務單） | `cancelled`，原因 `staff_withdrew` |

| 部門用語 | 權威值 |
| --- | --- |
| 房務 | dept `housekeeping` |
| 工務 | dept `engineering` |
| 櫃檯 | dept `frontdesk` |
| 主管 | role `manager` |
| 房務主管 | role `manager`，dept 不是 `engineering`（含沒有部門） |
| 工務主管 | role `manager`，dept `engineering` |
| 值班經理 | role `duty_manager` |

| 員工狀態用語 | 權威值 |
| --- | --- |
| 在職 | `active`，已開通 |
| 待開通 | `invited`，邀請尚未開通 |
| 停用 | `inactive` |

待開通與停用的員工不出現在任何挑人名單（排房、改派對象、查房者、搶單），也不收任何推播；停用不動其名下已接的任務。

## Given 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `飯店有以下員工：` | 無 | 支援：`姓名`（必填，字串）、`部門`（必填，字串；房務、工務、櫃檯、主管、值班經理）、`角色`（選填，字串；主管、員工。部門寫「主管」時角色一定是主管；部門寫房務或工務而角色寫主管，代表這位主管帳號被設了部門，`staff_accounts.role`=manager、`dept` 仍寫該部門；部門工務、角色主管就是工務主管，其餘主管都是房務主管）、`負責樓層`（選填，整數；本班排房負責的樓層）、`班次結束`（選填，字串 `HH:mm`）、`狀態`（選填，字串；在職、待開通、停用） | `角色`：部門為主管時是主管，其餘是員工。<br>`班次結束`：17:00。<br>`狀態`：在職。 | `怎麼做`：逐列建立員工、登入帳號，以及今日班次與負責樓層；狀態依員工狀態用語對照寫入；角色寫入 `staff_accounts.role`。`權威狀態落地`：staff 與今日班次資料已存在。`回寫`：姓名對應的 staff id，以及用 `staffAccountsLogin`（`POST /staff-accounts/login`）取得的 JWT。 |
| `"{員工}" 已停用` | `員工`：字串。 | 不支援 | 無 | `怎麼做`：以主管身分停用該員工。`權威狀態落地`：staff 狀態為 `inactive`，名下已接任務不變。 |
| `飯店的未接提醒門檻如下：` | 無 | 支援：`項目`（必填，字串；一般單提醒同仁、一般單通知主管、急件提醒同仁、急件通知主管）、`值`（必填，字串；如 `10 分鐘`） | 一般單：提醒同仁 10 分鐘、通知主管 20 分鐘。急件：提醒同仁 3 分鐘、通知主管 8 分鐘。 | `怎麼做`：以主管身分更新租戶的未接提醒門檻設定。`權威狀態落地`：`tenant_config` 的門檻分鐘數已更新，一般單與急件各自獨立。 |
| `現在時間是 "{時刻}"` | `時刻`：字串 `HH:mm`；今日的時刻。 | 不支援 | 無 | `怎麼做`：把測試 `Clock` 設為今日該時刻。`權威狀態落地`：之後所有計時、排序與排程都以此時刻為準。`不必查`：不寫資料庫。 |
| `"{員工}" 名下有以下任務：` | `員工`：字串；負責人姓名。 | 支援：`房號`（必填，字串）、`內容`（必填，字串；如 退房清潔、續住整理、毛巾 ×2）、`類別`（選填，字串；打掃、備品、其他）、`預估分鐘`（選填，整數）、`狀態`（必填，字串；見狀態用語對照）、`已做分鐘`（選填，整數）、`停住原因`（選填，字串）、`重做次數`（選填，整數）、`免查房`（選填，字串；是／否，預設否）、`完成時間`（選填，字串；`今天 HH:mm` 或 `昨天 HH:mm`；只用於待查房、已送達、查房通過、完成 · 免查房這幾個已完成的狀態，寫入 `tasks.done_at` 與 `completed` 事件時間；寫「昨天」時 `work_date` 也是昨天） | `類別`：內容為退房清潔或續住整理時是打掃，其餘是備品。<br>`預估分鐘`：退房清潔 35、續住整理 20、其他 5。<br>`已做分鐘`：0。<br>`重做次數`：0。<br>`免查房`：否。<br>`完成時間`：目前 `Clock`。 | `怎麼做`：逐列建立今日任務，並寫入能推導出該狀態的事件歷程。已做分鐘的落地方式依狀態固定：進行中＝`worked_sec` 0、`segment_started_at`＝目前 `Clock` 減該分鐘數（整段累計都在正在跑的這一段，計時與超時的測項靠這一點分辨「本段有沒有被重複累計」）；已中斷＝`worked_sec`＝該分鐘數換算秒、`segment_started_at` NULL；待查房／已送達／查房通過／完成 · 免查房＝`worked_sec`＝該分鐘數換算秒、`segment_started_at` NULL（終局狀態的已做分鐘是完成當下的累計，口徑依 `員工工作台.yaml:216-221` 的 `workedMinutes`）。三種落地方式在目前 `Clock` 下的累計進行時間都等於 `已做分鐘`。 `免查房` 為是時，在開工那一步把租戶設定 `inspection_waived_enabled` 打開再開工、開工後收回，讓產品碼自己把 `tasks.inspection_waived` 鎖成 true（這個鎖定值只在開工那一刻寫得進去，事後改欄位會被開工覆寫）。待認領、待開始、已延後與已取消不支援此欄：依同一段契約，待開始與已延後的已做分鐘一律是 0，填非 0 是劇本寫錯。`權威狀態落地`：`tasks` 的 state、assignee_id、worked_sec 與 segment_started_at、hold_reason、deferred_by_self（已延後時為 true）、redo_count，與 `task_events` 一致。`回寫`：房號對應的 task id。 |
| `房務待認領有以下任務：` | 無 | 支援：`房號`（選填，字串；來源為自動化規則的排程單可留空＝公共區域）、`內容`（必填，字串）、`類別`（選填，字串）、`樓層`（選填，整數）、`來源`（選填，字串；住客掃碼、櫃檯轉單、主管退回、請假退回、班末退池、查房退回、房務報修、自動化規則）、`標記`（選填，字串；優先通知）、`交代或原因`（選填，字串；來源為自動化規則時寫 `規則引擎命中：{規則名}`）、`提醒狀態`（選填，字串；已提醒同仁、已通知主管；直接標記未接提醒已到達的階段，不需要實際推進 Clock） | `樓層`：房號第一位數字，無房號時為空。<br>`來源`：住客掃碼。 | `怎麼做`：逐列建立 dept `housekeeping`、狀態 `pool` 的任務，並寫入 `pool_source`（自動化規則＝system）、`priority_notify`、`return_reason`、`requeue_note`、`rule_hit`；有 `提醒狀態` 時補寫到該階段為止的未接提醒紀錄與對應推播。`權威狀態落地`：`tasks` 與對應的 `created` 或 `returned_to_pool` 事件。`回寫`：房號（無房號時用內容）對應的 task id。 |
| `已有以下工務單：` | 無 | 支援：`單號`（必填，字串）、`房號`（必填，字串；客房房號或「公共區域」）、`類別`（必填，字串；冷氣、水電、家具／設備、門鎖、其他）、`狀態`（必填，字串；待認領、已接單、待開始、維修中、已完修、已結案、已撤回）、`報修人`（選填，字串；來源為定期保養時留空，其餘必填）、`說明`（選填，字串）、`處理人`（選填，字串）、`照片張數`（選填，整數）、`急件`（選填，是或否）、`連結任務`（選填，字串；原任務房號）、`來源`（選填，字串；房務報修、櫃檯、定期保養）、`處理結果`（選填，字串；已修好、暫時處理、無法修復、不需處理；只用於已完修與已結案的單，寫入 `repair_tickets.resolution_type`，無法修復時該房 `rooms.hk_state` 一併寫成 `out_of_order`；已完修的單不會是不需處理）、`處理說明`（選填，字串；`repair_tickets.resolution_note`）、`完修照片張數`（選填，整數；purpose=completion 的照片）、`完修時間`（選填，字串；`今天 HH:mm` 或 `昨天 HH:mm`；已完修與已結案的單寫入 `tasks.done_at`）、`驗收人`（選填，字串；狀態為已結案且處理結果不是不需處理時必填，寫入 `repair_tickets.verified_by`，`verified_at` 寫目前 `Clock`）、`不通過原因`（選填，字串；代表這張單曾被主管驗收不通過，寫入 `repair_tickets.last_verify_failed_reason`）、`不通過判定人`（選填，字串；有 `不通過原因` 時必填，寫入 `last_verify_failed_by`）、`不通過時間`（選填，字串；`今天 HH:mm` 或 `昨天 HH:mm`，寫入 `last_verify_failed_at`） | `照片張數`：1。<br>`急件`：否。<br>`來源`：房務報修。<br>`完修時間`：目前 `Clock`。<br>`處理結果`：已完修與已結案的單是已修好。<br>`完修照片張數`：已完修與已結案的單是 1，處理結果為不需處理時是 0。<br>`不通過時間`：狀態為已完修時是完修時間前 30 分鐘（代表重修後再完修），其他狀態是目前 `Clock` 前 30 分鐘。 | `怎麼做`：逐列建立修繕任務（`tasks`，type=repair）、`repair_tickets`、已上傳的 `repair_photos`，以及符合狀態的事件歷程。有處理人時，assignee 就是處理人；`來源`決定 `tasks.source` 與 `repair_tickets.origin`（房務報修＝staff／reported、櫃檯＝desk／reported、定期保養＝system／scheduled）與部門待認領回應 `PoolItem.source`（房務報修＝staff_report、櫃檯＝desk_forward，顯示標籤分別為「房務報修」「櫃檯」）；房號為「公共區域」時掛在公共區域佔位房，`isPublicArea`=true。狀態為已結案時 `tasks.state`=closed：處理結果為不需處理時 `verified_by`、`verified_at` 為空，`completed` 事件即結案事件；其他處理結果寫入驗收人並補一筆 `repair_verified` 事件。有 `不通過原因` 時三個 `last_verify_failed_*` 欄位同時寫入，並在 `task_events` 補一筆 `repair_verification_failed`（payload 帶 reason 與 destination：狀態為待認領時是 pool，其他是 assignee）；狀態是待開始、已接單、維修中或待認領時代表單正在退回重修，`done_at`、`resolution_type`、`resolution_note` 為空；狀態為待認領時 `pool_source`=verification_return、assignee 為空。`權威狀態落地`：`tasks`、`repair_tickets`、`repair_photos`、`rooms.hk_state` 與 `task_events` 一致。`回寫`：單號對應的 task id。 |
| `"{房號}" 今天沒有打掃負責人` | `房號`：字串。 | 不支援 | 無 | `怎麼做`：確認該房今日沒有任何已指派的打掃任務，需要時建立 `rooms` 資料列。`權威狀態落地`：該房今日沒有 assignee 不為空的打掃任務。 |

## When 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `"{員工}" 認領 "{任務}"` | `員工`：字串。<br>`任務`：字串；房號或工務單號。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `tasksClaim`（`POST /tasks/{id}/claim`）。`權威狀態落地`：成功時 pool 轉為 accepted，assignee 為該員工，寫入 `claimed` 事件；失敗時（已被他人認領）任務與事件不變。`回寫`：回應。 |
| `時間來到 "{時刻}"` | `時刻`：字串 `HH:mm`；今日的時刻。 | 不支援 | 無 | `怎麼做`：把測試 `Clock` 推進到今日該時刻，並讓引擎完成到該時刻為止的週期掃描（排程建單、待認領未接提醒）。`權威狀態落地`：期間命中的排程工單、未接提醒事件與 push outbox 都已寫入；每個待認領單的每個提醒階段只發一次。`回寫`：無。 |
| `"{員工}" 開始 "{任務}"` | `員工`：字串。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `tasksStart`（`POST /tasks/{id}/start`），適用 accepted 與本人延後的 deferred。`權威狀態落地`：成功時 `tasks.state`=doing、`segment_started_at`=目前 `Clock`、依租戶設定寫入 `inspection_waived`，寫入 `started` 事件，並在 `handover_reads` 記一筆；失敗時任務與事件都不變。`回寫`：回應狀態碼與內容。 |
| `"{員工}" 恢復 "{任務}"` | `員工`：字串。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `tasksResume`（`POST /tasks/{id}/resume`）。`權威狀態落地`：成功時 interrupted 轉為 doing，`worked_sec` 保留，`segment_started_at`=目前 `Clock`，並寫入 `resumed` 事件。`回寫`：回應。 |
| `"{員工}" 完成 "{任務}"` | `員工`：字串。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `tasksComplete`（`POST /tasks/{id}/complete`），不帶 body。`權威狀態落地`：成功時依 `inspection_waived` 轉為 done，或 inspected 且 `inspection`=waived（寫入 `inspection_waived` 事件；回寫房態開關開啟且非手動匯入模式時另寫入 `pms_status`=inspected 的 outbox 與 `pms_writeback` 事件，否則同步紀錄記一筆未回寫）；最後一段併入 `worked_sec`；未勾的 `task_sop_checks` 寫為 skipped；寫入 `completed` 事件。`回寫`：回應。 |
| `"{員工}" 判定 "{任務}" 查房全部項目通過` | `員工`：字串；通常為房務主管，也可用工務主管或非主管驗證權限。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `createInspections`（`POST /inspections`），body 為 `CreateInspectionRequest`：只帶 `taskId` 與 `items`（每個現行啟用的查房項目一筆 `{key, pass=true}`），不帶 tenantId、inspectorId 與 result（租戶與查房者由權杖推導，結果由後端算）；帶新的 `Idempotency-Key`。`權威狀態落地`：有 `inspect_submit` 權限且不是自己清的房時，任務轉為 inspected，`tasks.inspection`=passed，`rooms.hk_state`=inspected；寫入 `inspections` 一列與 `inspection_passed` 事件；回寫房態開關開啟且非手動匯入模式時，另寫入 `pms_status`=inspected（已整理）的 outbox 與 `pms_writeback` 事件，否則同步紀錄記一筆未回寫；並寫入負責人的 push outbox。沒有權限、查房者就是清房者本人，或呼叫者是工務主管（role manager 且 dept engineering）時回 403 `forbidden`，單不是待查房時回 409 `state_conflict`，任務與事件都不變。`回寫`：回應 `InspectionOutcome`。 |
| `從 PMS 拉今日房態，"{房號}" 的訂房帶 "{屬性}" 屬性` | `房號`：字串。<br>`屬性`：字串；訂房屬性，如 VIP、親子、寵物、紀念日。 | 不支援 | `入住日`：今天。 | `怎麼做`：對模擬 PmsAdapter 執行今日房態的批次拉取，該房的訂房帶該屬性，系統在拉取當下逐筆比對自動化規則，並等處理完。`權威狀態落地`：`reservations_cache` 已更新；命中規則時建立 source `system`、`pool_source`=system、`rule_hit`=規則名的準備工單，冪等鍵＝房號＋規則＋當地日期；該房今天有打掃負責人時工單直接派給他（accepted），否則進部門待認領（pool），並為該部門在職成員寫入 push outbox（`PushPayload.event`=pool_new_request，標題比照新需求格式，含同樓層標示）。`回寫`：準備工單 id。 |

## Then 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `這次操作被拒絕` | 無 | 不支援 | 無 | `必查`：`呈現結果`：回應為 4xx，body 符合 contracts `Error`；違反狀態守衛時是 409，違反輸入驗證時是 422。`權威狀態`：`tasks`、`task_events`、`outbox` 與本 plan 新增的資料筆數和內容，都跟這次操作前一樣。 |
| `這次操作被拒絕，提示 "{提示}"` | `提示`：字串；回應錯誤訊息必須包含的業務文字。 | 不支援 | 無 | `必查`：`呈現結果`：回應為 4xx，contracts `Error.message` 包含提示文字。`權威狀態`：同 `這次操作被拒絕`，失敗操作沒有改動任何權威資料。 |
| `這次操作被拒絕，錯誤碼為 "{錯誤碼}"` | `錯誤碼`：字串；contracts `Error.code` 的值，例如 `state_conflict`（409，單已不在預期狀態）、`verification_reason_required`（422，驗收不通過的原因空白）、`completion_photo_required`（422，需要完修照片卻沒有附）、`inspection_items_mismatch`（422，查房項目集合與現行不符）、`forbidden`（403，角色或主管種類不符）。 | 不支援 | 無 | `必查`：`呈現結果`：回應為 4xx，contracts `Error.code` 等於該錯誤碼，HTTP 狀態碼與 `shared.yaml` 的 `Error` 說明一致。`權威狀態`：同 `這次操作被拒絕`，失敗操作沒有改動任何權威資料。用在同一個 When 可能被兩種以上原因拒絕、而拒絕原因本身就是驗收面向的案例；只在乎有沒有被拒時用 `這次操作被拒絕`。 |
| `"{任務}" 的狀態為 "{狀態}"` | `任務`：字串；房號或工務單號。<br>`狀態`：字串；見狀態用語對照。 | 不支援 | 無 | `必查`：`權威狀態`：`tasks.state`，以及必要的 `tasks.inspection` 或 `tasks.cancel_reason`，符合對照表。`再讀確認`：以負責人或主管身分呼叫 `readTasks`（`GET /tasks/{id}`），看到的狀態一致。 |
| `"{任務}" 的負責人是 "{員工}"` | `任務`：字串；房號或工務單號。<br>`員工`：字串。 | 不支援 | 無 | `必查`：`權威狀態`：`tasks.assignee` 是該員工。`跨視角`：該員工呼叫 `getMyToday` 或 `readTasks` 時，看得到這筆是自己的單。 |
| `"{任務}" 的停住原因為 "{原因}"` | `任務`：字串；房號。<br>`原因`：字串；完整原因文字。 | 不支援 | 無 | `必查`：`權威狀態`：`tasks.hold_reason` 完全等於原因文字。`再讀確認`：`readTasks` 回應的 `holdReason` 一致。 |
| `"{任務}" 的已做時間為 {分鐘} 分鐘` | `任務`：字串；房號。<br>`分鐘`：整數。 | 不支援 | 無 | `必查`：`權威狀態`：`tasks.worked_sec` 加上進行中段落（`segment_started_at` 到目前 `Clock`）換算成分鐘，等於該分鐘數。`再讀確認`：`readTasks` 回應的 `elapsedMinutes` 一致。 |
| `"{員工}" 收到推播 "{標題}"` | `員工`：字串；收件人。<br>`標題`：字串；推播標題全文。 | 不支援 | 無 | `必查`：`權威狀態`：`outbox` 有一筆 kind `push` 的資料，payload 符合 contracts `PushPayload`，`recipientStaffId` 是該員工、`title` 等於標題文字。`跨視角`：relay 送出後，推播消費者已為該員工寫入一筆資料列。`不該發生`：同一事件不會對該員工推播兩次。 |
| `"{員工}" 沒有收到任何推播` | `員工`：字串。 | 不支援 | 無 | `必查`：`權威狀態`：本案例觸發動作之後，`outbox` 沒有任何 `recipientStaffId` 是該員工的 kind `push` 資料。 |
| `"{房號}" 的房況為 "{房況}"` | `房號`：字串。<br>`房況`：字串；還沒打掃好、已查房、停用。對照 `rooms.hk_state`：還沒打掃好＝dirty、已查房＝inspected、停用＝out_of_order。 | 不支援 | 無 | `必查`：`權威狀態`：該房 `rooms.hk_state` 等於對照值。`再讀確認`：房況查詢回應一致。用於查房結案、查房退回與工務單驗收後確認房況有沒有被改；「轉為停用待確認」那一句是完修無法修復當下的轉換，這一句是任何時點的現況。 |
| `"{員工}" 收到以下推播：` | `員工`：字串；收件人。 | 支援：`標題`（選填，字串；推播標題全文）、`內容`（選填，字串；推播內文 `PushPayload.body` 全文，例如驗收不通過的原因）、`去處`（選填，字串；X 的任務頁、待認領、待認領並標亮 X、首頁、X 的工務單詳情（X＝單號）） | 無 | `必查`：每一列都對應該員工的一筆 kind `push` outbox。有給標題時標題必須相等；有給內容時 `body` 必須相等。去處對應 `PushPayload.target`：任務頁為 `kind`=task 並帶 `taskId`（只用於打掃／備品任務）；工務單詳情為 `kind`=ticket 並帶該修繕任務的 `taskId`（本人的修繕任務一律用這種，不用 task）；待認領為 `kind`=pool 並帶 `dept`；標亮再帶 `highlightTaskId`；首頁為 `kind`=home。`跨視角`：推播消費者已寫入對應資料列。 |
