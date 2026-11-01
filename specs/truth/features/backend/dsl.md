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
| 待接 | `pool` |
| 待開始 | `accepted` |
| 進行中 | `doing` |
| 已延後 | `deferred`，有負責人（本人延後） |
| 已中斷 | `interrupted` |
| 待查房 | 打掃類 `done` |
| 已送達 | 非打掃類 `done` |
| 查房通過 | `inspected`，inspection `passed` |
| 完成 · 免查房 | `inspected`，inspection `waived` |
| 已取消 | `cancelled` |
| 已認領（工務單） | `accepted` |
| 處理中（工務單） | `doing` |
| 已完修（工務單） | `done` |
| 已撤回（工務單） | `cancelled`，原因 `staff_withdrew` |

| 部門用語 | 權威值 |
| --- | --- |
| 房務 | dept `housekeeping` |
| 工務 | dept `engineering` |
| 櫃檯 | dept `frontdesk` |
| 主管 | role `manager` |

## Given 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `飯店有以下員工：` | 無 | 支援：`姓名`（必填，字串）、`部門`（必填，字串；房務、工務、櫃檯、主管）、`負責樓層`（選填，整數；本班排房負責的樓層）、`班次結束`（選填，字串 `HH:mm`） | `班次結束`：17:00。 | `怎麼做`：逐列建立員工、登入帳號，以及今日班次與負責樓層。`權威狀態落地`：staff 與今日班次資料已存在。`回寫`：姓名對應的 staff id，以及用 `staffAccountsLogin`（`POST /staff-accounts/login`）取得的 JWT。 |
| `現在時間是 "{時刻}"` | `時刻`：字串 `HH:mm`；今日的時刻。 | 不支援 | 無 | `怎麼做`：把測試 `Clock` 設為今日該時刻。`權威狀態落地`：之後所有計時、排序與排程都以此時刻為準。`不必查`：不寫資料庫。 |
| `"{員工}" 名下有以下任務：` | `員工`：字串；負責人姓名。 | 支援：`房號`（必填，字串）、`內容`（必填，字串；如 退房清潔、續住整理、毛巾 ×2）、`類別`（選填，字串；打掃、備品、其他）、`預估分鐘`（選填，整數）、`狀態`（必填，字串；見狀態用語對照）、`已做分鐘`（選填，整數）、`停住原因`（選填，字串）、`重做次數`（選填，整數） | `類別`：內容為退房清潔或續住整理時是打掃，其餘是備品。<br>`預估分鐘`：退房清潔 35、續住整理 20、其他 5。<br>`已做分鐘`：0。<br>`重做次數`：0。 | `怎麼做`：逐列建立今日任務，並寫入能推導出該狀態的事件歷程。進行中與已中斷的單，事件時間要讓累計進行時間在目前 `Clock` 下等於 `已做分鐘`。`權威狀態落地`：`tasks` 的 state、assignee_id、worked_sec 與 segment_started_at、hold_reason、deferred_by_self（已延後時為 true）、redo_count，與 `task_events` 一致。`回寫`：房號對應的 task id。 |
| `房務待接池有以下任務：` | 無 | 支援：`房號`（必填，字串）、`內容`（必填，字串）、`類別`（選填，字串）、`樓層`（選填，整數）、`來源`（選填，字串；住客掃碼、櫃檯轉單、主管退回、請假退回、班末退池、查房退回、房務報修）、`標記`（選填，字串；優先通知）、`交代或原因`（選填，字串） | `樓層`：房號第一位數字。<br>`來源`：住客掃碼。 | `怎麼做`：逐列建立 dept `housekeeping`、狀態 `pool` 的任務，並寫入 `pool_source`、`priority_notify`、`return_reason`、`requeue_note`。`權威狀態落地`：`tasks` 與對應的 `created` 或 `returned_to_pool` 事件。`回寫`：房號對應的 task id。 |

## When 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `"{員工}" 開始 "{任務}"` | `員工`：字串。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `tasksStart`（`POST /tasks/{id}/start`），適用 accepted 與本人延後的 deferred。`權威狀態落地`：成功時 `tasks.state`=doing、`segment_started_at`=目前 `Clock`、依租戶設定寫入 `inspection_waived`，寫入 `started` 事件，並在 `handover_reads` 記一筆；失敗時任務與事件都不變。`回寫`：回應狀態碼與內容。 |
| `"{員工}" 恢復 "{任務}"` | `員工`：字串。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `tasksResume`（`POST /tasks/{id}/resume`）。`權威狀態落地`：成功時 interrupted 轉為 doing，`worked_sec` 保留，`segment_started_at`=目前 `Clock`，並寫入 `resumed` 事件。`回寫`：回應。 |
| `"{員工}" 完成 "{任務}"` | `員工`：字串。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `tasksComplete`（`POST /tasks/{id}/complete`），不帶 body。`權威狀態落地`：成功時依 `inspection_waived` 轉為 done，或 inspected 且 `inspection`=waived（寫入 `inspection_waived` 事件與 pms_status outbox）；最後一段併入 `worked_sec`；未勾的 `task_sop_checks` 寫為 skipped；寫入 `completed` 事件。`回寫`：回應。 |
| `"{主管}" 判定 "{任務}" 查房全部項目通過` | `主管`：字串。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：以主管身分呼叫 `createInspections`（`POST /inspections`），items 涵蓋查房項目全集而且都通過。`權威狀態落地`：任務轉為 inspected，`tasks.inspection`=passed；寫入 `inspections` 一列、`inspection_passed` 事件、`pms_status`=vacant_clean 的 outbox，以及負責人的 push outbox。`回寫`：回應。 |

## Then 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `這次操作被拒絕` | 無 | 不支援 | 無 | `必查`：`呈現結果`：回應為 4xx，body 符合 contracts `Error`；違反狀態守衛時是 409，違反輸入驗證時是 422。`權威狀態`：`tasks`、`task_events`、`outbox` 與本 plan 新增的資料筆數和內容，都跟這次操作前一樣。 |
| `這次操作被拒絕，提示 "{提示}"` | `提示`：字串；回應錯誤訊息必須包含的業務文字。 | 不支援 | 無 | `必查`：`呈現結果`：回應為 4xx，contracts `Error.message` 包含提示文字。`權威狀態`：同 `這次操作被拒絕`，失敗操作沒有改動任何權威資料。 |
| `"{任務}" 的狀態為 "{狀態}"` | `任務`：字串；房號或工務單號。<br>`狀態`：字串；見狀態用語對照。 | 不支援 | 無 | `必查`：`權威狀態`：`tasks.state`，以及必要的 `tasks.inspection` 或 `tasks.cancel_reason`，符合對照表。`再讀確認`：以負責人或主管身分呼叫 `readTasks`（`GET /tasks/{id}`），看到的狀態一致。 |
| `"{任務}" 的負責人是 "{員工}"` | `任務`：字串；房號或工務單號。<br>`員工`：字串。 | 不支援 | 無 | `必查`：`權威狀態`：`tasks.assignee` 是該員工。`跨視角`：該員工呼叫 `getMyToday` 或 `readTasks` 時，看得到這筆是自己的單。 |
| `"{任務}" 的停住原因為 "{原因}"` | `任務`：字串；房號。<br>`原因`：字串；完整原因文字。 | 不支援 | 無 | `必查`：`權威狀態`：`tasks.hold_reason` 完全等於原因文字。`再讀確認`：`readTasks` 回應的 `holdReason` 一致。 |
| `"{任務}" 的已做時間為 {分鐘} 分鐘` | `任務`：字串；房號。<br>`分鐘`：整數。 | 不支援 | 無 | `必查`：`權威狀態`：`tasks.worked_sec` 加上進行中段落（`segment_started_at` 到目前 `Clock`）換算成分鐘，等於該分鐘數。`再讀確認`：`readTasks` 回應的 `elapsedMinutes` 一致。 |
| `"{員工}" 收到推播 "{標題}"` | `員工`：字串；收件人。<br>`標題`：字串；推播標題全文。 | 不支援 | 無 | `必查`：`權威狀態`：`outbox` 有一筆 kind `push` 的資料，payload 符合 contracts `PushPayload`，`recipientStaffId` 是該員工、`title` 等於標題文字。`跨視角`：relay 送出後，推播消費者已為該員工寫入一筆資料列。`不該發生`：同一事件不會對該員工推播兩次。 |
| `"{員工}" 收到以下推播：` | `員工`：字串；收件人。 | 支援：`標題`（選填，字串；推播標題全文）、`去處`（選填，字串；X 的任務頁、待接池、待接池並標亮 X、首頁） | 無 | `必查`：每一列都對應該員工的一筆 kind `push` outbox。有給標題時標題必須相等。去處對應 `PushPayload.target`：任務頁為 `kind`=task 並帶 `taskId`；待接池為 `kind`=pool 並帶 `dept`；標亮再帶 `highlightTaskId`；首頁為 `kind`=home。`跨視角`：推播消費者已寫入對應資料列。 |
