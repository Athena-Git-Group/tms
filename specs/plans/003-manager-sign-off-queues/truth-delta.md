# Truth Delta: 003-manager-sign-off-queues

**Plan Package**: `specs/plans/003-manager-sign-off-queues`
**Truth Root**: `specs/truth`

> 本檔由 truth owner skill（`/api-plan`、`/data-plan`、`/dsl-refine`）透過 `/truth-delta` 追加。
> `/specify` 只建立骨架，不得寫入任何 `specs/truth/**` 的實際改動。

## /technical-research

_尚未執行。_

## /api-plan

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `specs/truth/contracts/groups/現場作業/主管工作台.yaml` -> `getManagerQueues`（`GET /me/manager-queues`） | 新 api group「主管工作台 API」與一支查詢：三格數字 `counts` 與三份清單 `inspection`、`review`、`verify` 同一次往返回完；只開放 `manager`（其他角色 403）；排除呼叫者本人清的房；每類依 `waitingSince` 由舊到新由後端排好，同時刻依房號再依單號；不帶日期條件。三條判定公式與排序鍵來源照 `/data-plan` 在 `tasks`、`task_events`、`repair_tickets` Note 的結論，待審核排序鍵取任務事件最後一筆 `created` 或 `repair_resubmitted` 的時間。description 明寫不碰 `listInspections` 與 `listRepairTickets` 的既有語意。 | FR-003～FR-006、FR-011、FR-015、FR-020、FR-027、FR-028、FR-033、NFR-001、SC-002。三種形狀裡選「一支、整份回」而不是「一支帶類別」或「三支」：三格數字與各清單列數必須同源（FR-004、SC-002），同一個回應裡 `counts` 就是三個陣列的長度，連「格子寫 2、點進去 3 張」的可能都沒有；一家飯店同時等主管的單只有個位數到十位數（`/data-plan`），整份回的多餘流量可忽略；三支各一會讓首頁為三個數字打三次（違 NFR-001）或另立第四支算數字。放寬既有查詢被否決：`listInspections` 回的是查房紀錄、待查房的單還沒有紀錄；`listRepairTickets` 是任務頁同房工務單的唯一來源且 `roomId` 必填。路徑掛在 `/me/` 下與 `getMyToday` 同一慣例，因為內容依呼叫者而異。 |
| ADD | `specs/truth/contracts/components/schemas/主管工作台.yaml` -> `ManagerQueues`、`QueueItemBase`、`InspectionQueueItem`、`ReviewQueueItem`、`VerifyQueueItem` | 清單每列欄位照 ui-plan 介面 2 的表：共用 `taskId`、`roomNo`（nullable）、`isPublicArea`、`title`、`waitingSince`、`waitingSinceIsToday`；待查房加 `cleanerName`、`redoCount`；待審核加 `ticketNo`、`category`、`description`、`reporterName`、`photoCount`；待驗收加 `ticketNo`、`category`、`description`、`reporterName`（nullable）、`assigneeName`、`origin`、`resolutionType`（必有值）。 | 「是否今天」定案為**後端給布林** `waitingSinceIsToday`：系統沒有每租戶時區，App 拿不到飯店那把尺，後端 `Clock` 是唯一與 `todayTasks` 同口徑的日界線；並要求 App 顯示時刻時直接用 `waitingSince` 字串裡的時區位移、不轉手機時區（與 `PoolItem.waitedMinutes` 由後端算的理由相同）。公共區域的 `roomNo` 定案為 **null 加 `isPublicArea=true`、地點在 `title`**，沿用 `EngineeringTicket` 既有做法，不讓資料側佔位房號「公共區域」漏到畫面。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/查房.yaml` -> `createInspections` | 加 `Idempotency-Key`（必帶，回放第一次回應）；request 改為 `CreateInspectionRequest`（只帶 `taskId` 與 `items` 陣列，`tenantId`、`inspectorId` 由權杖推導、`result` 由後端算，三者不再收）；201 回應改為 `InspectionOutcome`；補 403（自清自查、角色不符）、404、409 `state_conflict`（單已不是待查房，兩位主管同時送出只接受先到的）、422 `inspection_items_mismatch`（缺項或多項都算）。description 寫明全過與退回的既有後端規則不變、飯店沒有啟用項目時送空陣列視為全部通過。 | FR-007～FR-011、FR-030、邊界情況第 1、3、4 條、spec「交給 /api-plan 的備忘」前兩點、ui-plan 介面 3 的兩種拒絕表。兩種拒絕用**不同 HTTP 狀態加不同 `Error.code`** 表達：狀態衝突是 409 `state_conflict`，與工務單四個主管動作同一個 code，App 一個對話框處理四種動作加查房；項目集合不符是 422 `inspection_items_mismatch`，`context` 帶 `missing`、`unexpected` 與 `currentItems`（現行集合），滿足 ui-plan「回應直接帶現行項目集合可少一次往返」的偏好。這是改既有 operation 的請求形狀，依據是 system-analysis handoff 明寫「收斂為由權杖推導」；牽動後端 controller 的 request record 與三處 stepdef，見下方破壞性變更。 |
| ADD | `specs/truth/contracts/components/schemas/查房.yaml` -> `InspectionItemJudgement`、`CreateInspectionRequest`、`InspectionOutcome` | `InspectionItemJudgement` 是 `{key, pass}`；`CreateInspectionRequest` 是 `{taskId, items[]}`，`items` 可為空陣列；`InspectionOutcome` 帶 `inspectionId`、`taskId`、`roomNo`、`result`（passed／failed）、`destination`（closed／cleaner／pool）、`cleanerName`、`failedItems[{key,label}]`、`createdAt`。 | FR-009 要求結果畫面說明是結案還是退回、退回時去了哪裡；`destination` 由後端依既有換人規則判定，App 不自己看班表（ui-plan 介面 3）。`failedItems` 就是退回原因，沒有備註欄（FR-010）。 |
| DELETE | `specs/truth/contracts/components/schemas/查房.yaml` -> `inspectionsRequest` | 移除舊的 request schema（含 `tenantId`、`inspectorId`、`result`、`items` 型別為 string），根檔 `components.schemas` 的 `$ref` 一併改為新 schema。 | 被 `CreateInspectionRequest` 取代；依據同上一列（handoff 明寫收斂）。後端 controller 本來就不採信 `tenantId` 與 `inspectorId`、`result` 由 `items` 推導，契約只是對齊事實。 |
| NOOP | `specs/truth/contracts/components/schemas/查房.yaml` -> `inspections`（查房紀錄）與 `listInspections` | 不動。`inspections.items` 的型別仍是 string（實際是 `[{key, pass}]` 陣列的 JSON 字串），是既有漂移。 | spec 影響表明寫「不動 `listInspections`」；本輪 App 不讀查房紀錄。漂移記在這裡，待另一輪處理。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `readRepairTicket` 的 description | 可查看者加主管；寫明主管從任何入口、任何狀態都能打開，主管動作只在 `pending_review` 與 `done` 且未驗收兩種狀態出現；公共區域的單 `roomNo` 為 null。 | FR-032、FR-016、FR-021。後端 `RepairService.read` 本來就沒有擋主管，契約補上事實。只動說明，不改行為。 |
| MODIFY | `specs/truth/contracts/components/schemas/工務單.yaml` -> `RepairTicket` | 新增 `resolutionType`（nullable，fixed／temporary／cannot_fix）、`doneAt`（nullable，完修時間）、`reviewRequestedAt`（nullable，最近一次進入等主管審核的時間）、`origin`（必填，reported／scheduled）；`description` 改 nullable 並補說明；`resolutionNote`、`rejectReason`、`verifiedBy`、`verifiedAt`、`originTicketNo` 的說明拿掉「待 /data-plan 補欄位」的過期註記（欄位已落地）。 | ui-plan 介面 4 的缺欄位表：待驗收清單與主管視角狀態列要顯示完修結果、完修時間、送出時間、是否定期保養。`resolutionType` 的值沿用 truth 的 `fixed`，不用 ui-plan 口語的 `repaired`。欄位來源：`doneAt`＝`tasks.done_at`；`reviewRequestedAt`＝`task_events` 最後一筆 `created` 或 `repair_resubmitted`；`origin`＝`repair_tickets.origin`；`resolutionType`＝`repair_tickets.resolution_type`（資料來源由 Wave 3 `/data-plan` 定為 `repair_tickets.resolution_type`，done 後必有值）。 |
| MODIFY | `specs/truth/contracts/components/schemas/shared.yaml` -> `RepairTicketSummary` | `roomNo` 改 nullable（公共區域為 null）、新增必填 `isPublicArea`、`reporterName` 改 nullable（定期保養單沒有報修人）。 | 公共區域地點的定案要在工務單詳情與清單一致，而 `RepairTicketSummary` 是 `RepairTicket`、`listRepairTickets`、`precheck.duplicate` 共用的摘要；與 `EngineeringTicket` 既有的 null 做法對齊。`listRepairTickets` 依 `roomId` 查，回的一定是客房單，`roomNo` 實際不會是 null，任務頁不受影響。 |
| MODIFY | `specs/truth/contracts/components/schemas/shared.yaml` -> `Error` 錯誤碼清單 | 新增 `inspection_items_mismatch`（422，context 帶 `missing`、`unexpected`、`currentItems`）；`state_conflict` 補一句：工務單核准、退回、驗收與查房送出撞到單已不在預期狀態都回它，App 顯示「這張單已被處理」。 | FR-030、邊界情況第 1 至 3 條。不為「已被處理」另立 code，四種動作加查房共用一個，App 只寫一套處理。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `getMyToday` 的 description；`specs/truth/contracts/components/schemas/員工工作台.yaml` -> `MyToday.poolBadgeCount` 的 description | 寫明主管呼叫時的語意：`todayTasks`、`nextTasks` 照常回本人名下今天的單；六個打掃進度數字依同公式對本人名下計算（通常為 0，主管首頁不顯示）；`poolBadgeCount` 一律 0；`shift`、`floors`、`engineeringSummary` 為 null；三格待辦不在這支，走 `getManagerQueues`。 | ui-plan 介面 1 與介面 2 要求寫明；後端 `TaskExecutionService.poolBadgeCount` 已對主管回 0，契約補上事實。選 MODIFY（只補說明）而不是 NOOP，因為原契約「呼叫者現在搶得到的待認領單數」對沒有部門的主管沒有定義值。 |
| NOOP | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `approveRepairTicket`、`rejectRepairTicket`、`verifyRepairTicket` | 請求、`Idempotency-Key`、403／404／409／422 都已完整，不動。 | FR-017、FR-018、FR-022、FR-030：先到先處理就是既有的「其他狀態回 409 `state_conflict`」（後到者看到的單已是 pool／rejected／cancelled／已驗收）；離線由 App 擋在送出之前（FR-031），契約不涉及。 |
| MODIFY | `specs/truth/contracts/components/schemas/shared.yaml` -> `PushPayload.event` 枚舉與 description；`specs/truth/contracts/groups/現場作業/工務單.yaml` -> `rejectRepairTicket` 的 description | 枚舉加第 18 值 `repair_rejected`（與 `task_events.type` 既有值同名）：觸發＝主管退回 `pending_review` 的房務報修工務單；收件人＝報修人；title 帶房號、單號與「主管未核准」，body 帶退回原因；target `kind=ticket` 指向該工務單詳情。`rejectRepairTicket` 補「同一交易推播報修人」。`NotificationItem.event` 以 `$ref` 共用同一枚舉，自動涵蓋，不另改。 | FR-019，使用者 2026-11-10 裁決新增。`notifications.event` 的 CHECK 值集由 `/data-plan` 同名加值。牽動後端 `RepairService` 退回流程要寫 outbox push、backend 推播 feature 待 `/dsl-refine` 補場景。 |
| NOOP | `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `listDeptPool`；`specs/truth/contracts/components/schemas/shared.yaml` -> `PushTarget` | 主管待認領切換部門走既有 `dept` 參數，`PushTarget.dept` 在 `kind=pool` 已必填，不動。 | FR-024～FR-026 全是 App 端行為與本機記住部門，不進契約。 |
| NOOP | `specs/truth/contracts/groups/知識與通知/SOP與查房項目.yaml` -> `listSopItems` | 回應已含 `appliesTo` 與 `active`，App 可自行篩 `inspection` 且啟用。 | ui-plan 依賴 ⑥ 只要求確認。被拒時 App 不必再打它，`inspection_items_mismatch` 的 `currentItems` 已帶現行集合。 |

**牽動既有後端 stepdef 或 App 的破壞性變更**：
- `createInspections` 請求形狀：後端 `InspectionApiController.CreateInspectionRequest` record 要去掉 `tenantId`、`inspectorId`、`result`，並開始讀 `Idempotency-Key`；三處 stepdef 送舊 body 且沒帶 `Idempotency-Key`：`tms/src/test/java/com/athena/tms/bdd/steps/common/CommonWhenSteps.java:85-91`、`steps/push/PushManagerActionSteps.java:95-101`、`steps/handover/HandoverGivenSteps.java:156-162`；App BDD fixture `app/bdd/src/test/java/com/athena/app/bdd/BackendChannel.java:545` 同樣走 `POST /inspections`。
- `createInspections` 201 回應由 `inspections` 改為 `InspectionOutcome`；422 由單一「缺項」改為 `inspection_items_mismatch`（且多項也要拒）；後端 `InspectionService.validateCoverage` 目前只拒缺項、不拒多項、不回現行集合。
- `RepairTicketSummary.roomNo` 改 nullable、新增必填 `isPublicArea`：後端 `RepairService` 組回應時公共區域要把佔位房號換成 null 並補 `isPublicArea`（`TaskExecutionService.engineeringTickets` 已有同一套做法可沿用）；App `app/mobile/lib/core/api_client.dart` 多處 `json['roomNo'] as String`，工務單相關的解析要改成可為 null。
- `RepairTicket` 新增必填 `origin` 與三個 nullable 欄位：後端 `RepairService.read` 要補組裝；`resolutionType` 讀 Wave 3 新增的 `repair_tickets.resolution_type`，`resolveOnComplete` 要同交易寫入。

**待決與既有漂移**：
- FR-028 飯店時區（已定案）：本輪不加每租戶時區，沿用後端單一 `Clock`，`waitingSinceIsToday` 用同一把尺。

## /data-plan

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| NOOP | `specs/truth/data/data-model.dbml` -> `tasks`、`repair_tickets`、`inspections`、`sop_items` 的欄位與索引 | 不新增資料表、欄位或索引。待查房由 `tasks.type`、`state`、`assignee_id`、`done_at` 推導；待審核由 `tasks.type`、`state` 推導；待驗收由 `tasks.type`、`state`、`done_at` 與 `repair_tickets.verified_at` 推導。三類篩選都走既有索引 `(tenant_id, dept, state)`，待驗收以 `task_id` 主鍵 JOIN `repair_tickets`。FR-011 的「排除查詢者本人清的房」是查詢期的 `assignee_id <> 查看者` 條件，免查房的單完成時已落在 `inspected`，不需另加條件。 | 關鍵實體寫明「主管待辦（新，衍生）」不新增資料表；三條判定公式與 FR-027 的兩個排序鍵（待查房與待驗收都是 `done_at`）全部可由既有欄位取得；SC-002 要求三格數字、清單列數與直接查資料三者一致，存成欄位反而會多一套要同步的來源。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `tasks` 的 `Note` | 增補「主管待辦的三類判定」一段：三條公式、三個排序鍵、排除規則（本人清的房、免查房、急件不經審核、維修中與已驗收不在待驗收）、走哪個索引、「今天」只用來決定標不標日期且沿用 Clock 時區；並寫明待審核的排序鍵不能拿 `updated_at` 代替，正確來源是 `task_events`，以及若日後契約要求直接存欄位時的落點（`tasks` 一個 nullable `timestamptz`，在進入 `pending_review` 的兩個守衛 UPDATE 同句寫入）。 | 讓三條公式在 data truth 有唯一出處，防止實作各自解讀；待審核的排序鍵最容易被誤取成 `updated_at` 或建單時間（補資料再送出會重新進入），必須在資料層明寫。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `task_events` 的 `Note` | 增補一句：待審核排序鍵「最近一次進入等主管審核的時間」取自本表該任務 `type` 為 `created` 或 `repair_resubmitted` 的最後一筆 `created_at`，走既有唯一索引 `(tenant_id, task_id, seq)`。 | 這是本輪唯一不在 `tasks` 上的取值來源，必須在來源表留下指引，供 Wave 2 `/api-plan` 定回應欄位與實作寫查詢時對齊。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_tickets` 的 `Note` | 增補一段：待審核與待驗收兩類清單由 `tasks` 與本表既有欄位即時算出，待驗收含 `origin=scheduled` 與完修結果 `temporary`、`cannot_fix` 的單，驗收人顯示名稱由 `verified_by` JOIN `staff_accounts` 取得。 | 待驗收的判定條件橫跨兩張表，兩邊的 Note 都要能找到彼此；驗收人名稱的取法是介面 8 詳情與待驗收清單都會用到的欄位來源。 |
| NOOP | `specs/truth/data/data-model.dbml` -> `tenant_config.key` 封閉鍵集 | 不新增每租戶時區設定鍵。FR-028「今天以飯店所在時區認定」在資料層沒有對應欄位，目前後端的「今天」一律用注入的 `Clock` 時區（`TaskExecutionService.belongsToToday` 同一把尺）。 | 使用者於 2026-11-10 裁決：本輪沿用單一注入的 `Clock` 時區，不加租戶時區設定；`tasks` Note 已寫明沿用現況，此項定案不再待決。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `notifications.event` 的 CHECK 值集與 `Note` | CHECK 值集由十七值加為十八值，新增 `repair_rejected`（與 `/api-plan` 的 `PushPayload.event` 同名）；Note 補一句觸發時機（主管退回 `pending_review` 的房務報修工務單）、收件人（`repair_tickets.reporter_id`）、body 帶 `reject_reason`、target 與其他修繕事件一致為工務單詳情（`kind=ticket`）。同時把 `notifications.target` 的 Note 由 `kind=task|pool|home` 補齊為 `task|ticket|pool|home`，對齊 contracts `PushTarget.kind`。`task_events.type` 已有 `repair_rejected`，不動。 | FR-019 由使用者於 2026-11-10 裁決：退回時要推播給報修人。通知中心每列的分類圖示靠這一欄，不加值的話退回通知會掉回「一般」。 |
| NOOP | `scripts/seed-demo.sh` 所需欄位（示範資料本身不是 truth） | FR-034 的三處調整都能用既有欄位種出：前一天完成的待查房單是一列 `tasks`（`type=cleaning`、`state=done`、`work_date` 為昨天、`done_at` 為昨天、`assignee_id` 不是王主管；partial UNIQUE `(tenant_id, room_id, work_date)` 排除 `done`，不會撞）；等主管審核的一般件是一列 `tasks`（`type=repair`、`dept=engineering`、`source=staff`、`state=pending_review`、`urgent=false`、`assignee_id` 為空）加一列 `repair_tickets`（`is_urgent=false`、`origin=reported`、`reporter_id` 為阿芳）加一筆 `task_events.created`（seed 既有的整批補 `created` 事件就能提供排序鍵）；W-0108 的完修照片是一列 `repair_photos`（`ticket_task_id` 指向 W-0108、`purpose=completion`、`status=uploaded`、`storage_ref` 必填、`uploaded_by` 為阿強）。 | 確認欄位夠用即可；seed 目前完全沒有 `repair_photos` 列，`storage_ref` 要指到一個 App 真的抓得到的照片物件，這是實作與環境問題，不是資料模型缺口。 |

### Wave 3（條件式，已完成）

Wave 2 的 `/api-plan` 把 `RepairTicket.resolutionType` 列為契約欄位後，回頭查證資料來源：`task_events.completed` 的 payload 在 data truth 寫著帶 `resolutionType`，但後端 `TaskEngine.complete` 只帶 `skipped_items`，`RepairService.resolveOnComplete` 拿它分支（建追蹤單、房轉 `out_of_order`）之後就丟掉，資料庫任何欄位與任何 payload 都沒有這個值。裁決如下。

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `specs/truth/data/data-model.dbml` -> `repair_tickets.resolution_type` | 新增 nullable `text`，CHECK `fixed|temporary|cannot_fix`。尚未完修（`tasks.state` 不是 `done`）為 NULL；完修守衛成功後由 `RepairService.resolveOnComplete` 在同一交易寫入，請求省略 `resolutionType` 時寫 `fixed`，所以 `done` 之後不會是 NULL；只在完修那一刻寫一次，之後不改。不新增索引（待驗收清單以 `task_id` 主鍵 JOIN 本表取它，不拿它篩選或排序）。 | 它是工務單自己的延伸狀態，與既有 `resolution_note`、`reject_reason`、`verified_by`／`verified_at` 同一種風格：動作結果落在 `repair_tickets`，清單與詳情直接讀欄位。若留在事件，待驗收清單每列都要逐單撈 `task_events` 再解 JSON 取一個純顯示欄位，查詢成本與程式複雜度都不划算；而且 `temporary`／`cannot_fix` 的副作用（追蹤單、`out_of_order`）已經落地，結果本身卻讀不回來，詳情無法解釋房為什麼停用。低影響 ADD（nullable、單一寫入點、不改既有行為），不需 `/clarify`。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `task_events.payload` 的 `Note`（`completed`） | 由「帶 resolutionType」改寫為明確要求：`type=repair` 的 `completed` 必帶 `resolutionType`（省略時仍寫 `fixed`），`temporary` 另帶 `originTicketTaskId`；並補寫打掃類 `completed` 帶 `skipped_items`。 | 事件仍是真相、欄位是投影，兩者必須同一交易寫入才能重放重建；後端目前沒帶這個值是實作缺口，truth 要寫成可驗證的必帶條件，不是描述現況。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_tickets` 的 `Note`（完修三選一段落） | 補寫結果一律落地到 `resolution_type`、payload 同值同交易、清單與詳情讀欄位不翻 JSON。 | 讓欄位與事件的分工在同一段有唯一出處。 |

對 `/api-plan` 契約的連帶：`RepairTicket.resolutionType` 維持 nullable 正確，但語意應收斂為「null 等價於尚未完修（`state` 不是 `done`）；完修後必有值，省略送出的就是 `fixed`」，description 中「來源是完修事件 payload」改為「來源是 `repair_tickets.resolution_type`」。待驗收清單每列的完修結果可直接定為非 nullable（清單只收 `done` 的單）。實作待辦（不在 truth）：`resolveOnComplete` 補寫欄位與事件 payload、Flyway 新增欄位、seed 為 W-0108 與 W-0109 補 `resolution_type='fixed'`。

## /dsl-refine

路徑縮寫：`ios/`＝`specs/truth/features/ios-app/`；`be/`＝`specs/truth/features/backend/`。語意單元層級；依據欄引用 acceptance 檔:行或使用者 2026-11-10 裁決。

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `ios/主管工作台/dsl.md` | 新模組 DSL：Given 9 row（已打開主管首頁／已打開 "{清單}"／查房項目如下／沒有任何啟用項目／已從待查房清單打開 X 還沒判／判定如下／送出前新增項目／剛被另一位主管查完／剛送出查房結果畫面停著）、When 6 row（點格／從待查房清單打開／把項目判／判定後送出／送出查房／從結果畫面回清單）、Then 22 row（三格、清單、日期時刻、房號內容單號、標籤、空狀態、名下的單、沒有問候列、沒有三格、底部分頁、查房項目、送出鈕、無備註欄、無項目說明、結果畫面三種說法、重新載入項目） | 主管首頁、三類清單、查房畫面是全新畫面，不屬任何既有 ios-app 模組；模組名與 contracts group「主管工作台 API」及 backend 新模組同名 |
| ADD | `ios/主管工作台/主管首頁.feature` | 8 Rule：三格等於清單列數（2／1／3）、某類 0 顯示 0、點 0 的格進去是說明、四分頁外殼、名下的單沿用任務卡、沒有問候列與今日進度、名下沒單一句說明、房務員工首頁沒有三格 | acceptance `主管登入後進自己的首頁.feature` Rule L29、L37、L76-82、L99、L107 |
| ADD | `ios/主管工作台/三類清單.feature` | 9 Rule：待查房排除本人與免查房、另一位主管看得到本人清的房、待審核不含急件、待驗收含暫時處理／無法修復／定期保養不含維修中／已驗收（含標籤）、最舊在前不分日期、非今天標日期（日界線以系統時區）、每列房號內容與單號、補資料再送出排在後面且只顯示時刻、從結果畫面回清單該單消失格數減一 | acceptance `主管登入後進自己的首頁.feature` L37-97；`從待審核清單核准或退回報修.feature` L41-43、L50；`從待驗收清單驗收完修工務單.feature` L28-43；`從待查房清單當場查房.feature` L30-32、L59-69 |
| ADD | `ios/主管工作台/查房.feature` | 8 Rule：打開列全部項目未判送出鈕禁用無備註欄、每項判過才能送出、全通過結案（房況已查房、推播）、一項不過退回原清房者（房況還沒打掃好、推播）、原清房者下班回待認領、項目調整重新載入且不推播、無項目直接送出、單剛被另一位主管查完顯示已被處理 | acceptance `從待查房清單當場查房.feature` L15-57、L71-92；`主管動作先到先處理且離線一律擋下.feature` L15-21 |
| ADD | `ios/工務單/主管視角.feature` | 11 Rule：任何狀態都看得到報修內容、主管動作六狀態對照（場景大綱）、核准回清單、退回原因空白禁用、退回成功、退回時報修人收到推播、待驗收詳情只有一個驗收動作無不通過／退回／備註欄、驗收一鍵記人與時間、驗收無法修復房況仍停用、另一位主管先驗收顯示已被處理、報修人已撤回時核准顯示已被處理 | acceptance `從待審核清單核准或退回報修.feature` L14-34、L52-60；`從待驗收清單驗收完修工務單.feature` L4-43；`主管動作先到先處理且離線一律擋下.feature` L4-13、L53-73；FR-019 裁決：退回推播報修人 |
| ADD | `ios/工務單/dsl.md` | Given 3 row（已在 App 打開 X 的工務單詳情／X 剛被主管驗收畫面還沒更新／X 剛被報修人撤回畫面還沒更新）、When 4 row（按核准／按退回寫原因送出／打開退回面板不寫原因／按驗收）、Then 7 row（主管動作為／顯示報修內容／顯示完修照片與處理說明／沒有驗收不通過退回與備註欄／退回面板送出鈕／後端沒有收到這次退回／已由主管驗收驗收時間為現在） | 支撐 `主管視角.feature` |
| ADD | `ios/dsl.md`（介面根） | Given `"{員工}" 上次在待認領停在 "{部門}"`（待認領、推播兩模組共用）；Then `後端已對 "{員工}" 送出推播 "{標題}"`、`後端 "{房號}" 的房況為 "{房況}"`（主管工作台、工務單共用）、`畫面回到 "{清單}"，"{單}" 不在清單上`、`畫面顯示 "這張單已被處理"，並回到 "{清單}"，"{單}" 不在清單上`（主管工作台、工務單共用） | 跨 2 模組且契約一致，依唯一歸屬判準放根 |
| ADD | `ios/待認領/dsl.md` | Given `"{主管}" 第一次在這台手機打開待認領`；When `"{主管}" 在待認領切到 "{部門}"`；Then `待認領目前的部門為 "{部門}"`、`"{任務}" 的單卡沒有搶單鈕` | FR-024～FR-026 |
| ADD | `ios/待認領/待認領列表.feature` | 4 Rule：第一次打開顯示房務、單卡與員工一致但沒有搶單鈕（房務一例、工務一例）、可切到另一個部門、重開停在上次部門 | acceptance `待認領切換房務與工務只看不搶.feature` L12-24 |
| ADD | `ios/推播/點開推播的去處.feature` | Rule「修繕任務推播開工務單詳情」加一例（阿美點開主管未核准推播停在 W-0201 詳情）；Rule「去處為首頁依角色進首頁」（工務員工停工務首頁、主管冷啟動停主管首頁）；Rule「主管點開帶部門的待認領推播切到該部門並標亮」（房務未接提醒、工務急件各一例）；背景加王主管 | acceptance `主管登入後進自己的首頁.feature` L14-22；`待認領切換房務與工務只看不搶.feature` L26-38；FR-019 裁決 |
| ADD | `ios/帳號與登入/登入與登出守衛.feature` | Rule「登入成功後依角色進首頁，主管一律進主管首頁」（場景大綱四列：主管、帶部門的主管、工務、房務）；Rule「強制改密完成後依角色進首頁」（場景大綱兩列） | acceptance `主管登入後進自己的首頁.feature` L4-27 |
| ADD | `ios/系統層/離線模式與連線異常.feature` | 既有 Rule 下第二個場景大綱「王主管離線時嘗試 <動作>」四列；新 Rule「離線被擋下的主管動作不留在手機上等補送」；新 Rule「恢復連線後單的狀態不變」（場景大綱四列） | acceptance `主管動作先到先處理且離線一律擋下.feature` L23-43；D-4 裁決採同 Rule 第二場景大綱 |
| ADD | `ios/系統層/dsl.md` | Given `"{員工}" 在離線時嘗試過 "{動作}" 並被擋下`；Given `"{員工}" 已在 App 登入，停在首頁`（稽核顯示只有系統層使用，依判準下放到模組） | 支撐離線恢復連線案例 |
| ADD | `be/主管工作台/dsl.md` | 新模組 DSL：When `"{員工}" 查看主管待辦`；Then 11 row（三格數字／三類清單依序為／三類清單不包含／完修結果與來源／標為不是今天／標為今天／查詢被拒因為不是主管） | `getManagerQueues` 是新 api group |
| ADD | `be/主管工作台/主管待辦三類清單.feature` | 9 Rule：待查房排除本人、免查房不在、另一位主管看得到、待審核不含急件、待驗收含三種結果與定期保養不含維修中／已驗收、三格等於列數、由舊到新不分日期、非今天標記（日界線以系統時區）、重送的單排在更早送出的後面 | acceptance `主管登入後進自己的首頁.feature` L37-97；`從待驗收清單驗收完修工務單.feature` L28-43；FR-028 裁決：沿用系統時區 |
| ADD | `be/主管工作台/主管待辦空清單與權限.feature` | 2 Rule：沒有單的類別回 0 與空清單、非主管 403 | acceptance `主管登入後進自己的首頁.feature` L76-82、L99-105 |
| ADD | `be/查房/逐項判定與退回去向.feature` | 10 Rule：全通過房況已查房、任一項不過退回原清房者記重做、SOP 清空房況還沒打掃好、原清房者下班回待認領、不能查自己清的房、項目集合不符（少項／多項）422 帶現行集合不推播、無啟用項目空清單通過、單非待查房 409 只接受先到、後到不多推播、冪等重送只判一次 | acceptance `從待查房清單當場查房.feature` L34-92；`主管動作先到先處理且離線一律擋下.feature` L15-21 |
| ADD | `be/查房/dsl.md` | Given 4 row（查房項目如下／停用全部項目／新增了查房項目／已判定全部通過）；When 3 row（判定查房如下／以空的項目清單判定／全部通過同一請求送出 N 次）；Then 5 row（結果為去向為／被拒原因是項目集合不符帶現行項目／在房務待認領／查房紀錄只有 N 筆／沒有收到查房退回的推播） | 支撐新 feature |
| ADD | `be/工務單/主管審核與驗收的狀態守衛.feature` | 7 Rule：退回原因空白 422、撤回後核准 409、已核准再核准 409、兩位主管先後驗收只接受先到、維修中不能驗收、驗收後狀態仍已完修時間為當下、驗收無法修復房況仍停用 | acceptance `從待審核清單核准或退回報修.feature` L25-34、L52-60；`主管動作先到先處理且離線一律擋下.feature` L4-13；`從待驗收清單驗收完修工務單.feature` L19-23、L40-43 |
| ADD | `be/工務單/開立工務單與急件.feature` | Rule「主管退回一般件時推播報修人，內容帶退回原因，點開進工務單詳情」；Rule「主管核准時不推播報修人」；Rule「補資料再送出後開始等主管的時間是重送時間」 | FR-019 翻案為新增（`PushPayload.event`=repair_rejected，title「{房號} {單號} 主管未核准」、body 退回原因、去處工務單詳情）；acceptance `從待審核清單核准或退回報修.feature` L34、L41-43 |
| ADD | `be/工務單/dsl.md` | When `"{主管}" 退回 "{單號}" 但沒有寫原因`；Given `"{主管}" 已先驗收通過 "{單號}"`、`"{員工}" 已撤回 "{單號}"`；Then `"{單號}" 的驗收時間為現在`、`"{單號}" 的驗收人只有 "{主管}" 一人，驗收只記下一次`、`"{單號}" 開始等主管的時間為現在` | 支撐狀態守衛 feature 與排序鍵 Rule |
| ADD | `be/dsl.md`（介面根） | Then `這次操作被拒絕，錯誤碼為 "{錯誤碼}"`（查房、工務單、主管工作台共用）；Then `"{房號}" 的房況為 "{房況}"`（查房、工務單共用） | D-12 裁決；409／422 必須可分辨 |
| MODIFY | `be/dsl.md` 狀態用語對照「等主管審核」「主管未核准」 | 拿掉「新增狀態值，待 /data-plan 補列」註記 | 狀態值已落地 |
| MODIFY | `be/dsl.md` Given `飯店有以下員工：` | DataTable 加選填 `角色`（主管／員工；可表達被設了部門的主管） | spec 邊界情況「主管帳號帶有部門時仍依角色進主管首頁」 |
| MODIFY | `be/dsl.md` Given `"{員工}" 名下有以下任務：` | DataTable 加選填 `完成時間`（`今天 HH:mm`／`昨天 HH:mm`，寫 `done_at`） | 待查房排序鍵與跨日標日期需要可指定完成時間 |
| MODIFY | `be/dsl.md` Given `已有以下工務單：` | 加選填 `送出時間`、`處理結果`、`處理說明`、`完修照片張數`、`完修時间` 改名為 `完修時間`、`來源` 加 `定期保養`、`報修人` 改選填、`房號` 可為「公共區域」；拿掉「待 /data-plan 補」註記 | 待驗收清單與主管視角需要三種完修結果、定期保養、完修時間；欄位已落地 |
| MODIFY | `be/dsl.md` When `"{員工}" 判定 "{任務}" 查房全部項目通過` | 實作語意改為 `CreateInspectionRequest{taskId, items}`＋`Idempotency-Key`，回應 `InspectionOutcome`；補 403 自清自查、409 非待查房、房況 inspected | 對齊 /api-plan 改過的 `createInspections` 契約 |
| MODIFY | `be/dsl.md` Then `"{員工}" 收到以下推播：` | DataTable 加選填 `內容`（`PushPayload.body`） | 退回推播要驗 body 帶退回原因 |
| MODIFY | `be/查房/dsl.md` | 無既有 row 改動（只 ADD） | |
| MODIFY | `be/工務單/dsl.md` 裁決段 L13 | 「待回交 /api-plan、/data-plan」改寫為已落地事實；補主管在 App 執行動作、退回推播報修人、核准與驗收不推播 | 契約與資料已落地；FR-019 裁決 |
| MODIFY | `be/工務單/dsl.md` When 核准／退回／補資料再送出／驗收通過 | 去掉「待 /api-plan 補」假設，改寫實際 operationId、`Idempotency-Key`、409 `state_conflict`、422 `reject_reason_required`、退回同交易推播報修人、驗收不改房況 | 同上 |
| MODIFY | `be/工務單/dsl.md` Then 退回原因／已由主管驗收／尚未驗收 | 去掉「待 /data-plan 補」，寫實際欄位 `reject_reason`、`verified_by`、`verified_at` | 欄位已落地 |
| MODIFY | `be/推播/dsl.md` 模組說明 | 推播歸屬清單「工務完修、急件」加「主管退回報修單」歸 `工務單` 模組 | FR-019 推播在觸發它的模組驗證，沿用既有分工 |
| MODIFY | `be/推播/dsl.md` When `"{主管}" 查房 "{任務}"，判定 "{項目}" 不合格` | 實作語意改新 body 與 `Idempotency-Key`，語意不變 | 契約對齊 |
| MODIFY | `ios/dsl.md` Given `飯店有以下員工：` | 加 `角色`、`班次結束`；部門值集寫明 | 同後端 |
| MODIFY | `ios/dsl.md` Given `"{員工}" 名下有以下任務：` | 實作語意：操作者是別人時只建資料、不換登入者 | 主管看別人清好的房的案例 |
| MODIFY | `ios/dsl.md` Given `手機目前離線` | 「劇本中的員工」改「劇本中的操作者」 | 主管也會離線 |
| MODIFY | `ios/dsl.md` When `"{員工}" 在 App 打開首頁` | 實作語意改為依角色分流的首頁與各自的載入完成條件 | FR-001 |
| MODIFY | `ios/dsl.md` Then `"{任務}" 在 App 顯示為 "{狀態}"` | 必查處加「工務單詳情」 | 離線恢復後驗工務單狀態 |
| MODIFY | `ios/帳號與登入/dsl.md` Then `"{員工}" 登入成功，進入首頁` | 句型改為 `"{員工}" 登入成功，進入 "{首頁}"`（房務首頁／工務首頁／主管首頁），必查寫明三入口共用的分流判定 | FR-001；spec 影響表已宣告 |
| MODIFY | `ios/帳號與登入/登入與登出守衛.feature` 既有 3 個 Example | `登入成功，進入首頁` 改為 `登入成功，進入 "房務首頁"` | 跟著句型改 |
| MODIFY | `ios/推播/點開推播的去處.feature` 場景大綱例子「310 已改派給小陳」 | 畫面「首頁」改「房務首頁」 | 去處為首頁依角色 |
| MODIFY | `ios/推播/dsl.md` Given `"{員工}" 收到一則推播 "{標題}"，去處為 "{去處}"` | `去處` 值集加「房務待認領並標亮 X」「工務待認領並標亮 X」；寫明 `kind`=pool 帶 `dept`、`kind`=home 依 session 角色、冷啟動；標題不驗權威文案 | FR-026；待認領推播文案裁決：沿用 truth 句型，急件文案不斷言 |
| MODIFY | `ios/推播/dsl.md` Then `App 停在 "{畫面}"` | 畫面值集加主管首頁／工務首頁／房務首頁／帶部門的待認領；必查加部門切換與本機選擇、外殼與角色一致、主管視角詳情 | 同上 |
| MODIFY | `ios/工務單/dsl.md` 裁決段 L9 | 「主管審核與驗收本身都是主管端（後台／情境模擬面板）的動作，不是這個模組要承接的 App 互動」翻案為「主管在 App 的工務單詳情核准、退回、驗收（`TicketView.manager`，以工務版為底換底部動作列）」；房務端與工務端呈現不變 | spec 影響表 MODIFY；ui-plan 介面 4 |
| MODIFY | `ios/工務單/dsl.md` Then `工務單詳情顯示狀態摘要卡 "{摘要卡文案}"` | 文案清單加主管視角三句；必查改對 `RepairTicket.verifiedBy`、`reviewRequestedAt`，拿掉「待 /data-plan 補」 | 欄位已落地 |
| MODIFY | `ios/工務單/dsl.md` Then `工務單詳情顯示退回原因 "{原因}"` | 拿掉「待 /data-plan 補」，對 `RepairTicket.rejectReason` | 同上 |
| MODIFY | `ios/待認領/dsl.md` When `"{員工}" 在 App 打開房務待認領` | 實作語意補主管時是切到待認領分頁並把部門切到房務 | FR-024 |
| MODIFY | `ios/待認領/dsl.md` Then `待認領列表依序為 {房號清單}` | 參數說明加「工務池寫工務單號」，必查改為目前部門的列表、`listDeptPool` 帶目前部門 | D-10 裁決：主管切工務的場景沿用此句型，不動工務單模組 |
| MODIFY | `ios/系統層/離線模式與連線異常.feature` Rule L19 標題 | 「離線時開始、認領、延後、開新工務單一律被擋」加「以及主管的查房送出、核准、退回、驗收」 | FR-031；spec 影響表已宣告 |
| MODIFY | `ios/系統層/dsl.md` When `"{員工}" 在 App 離線時嘗試 "{動作}"` | `動作` 值集加主管四動作；實作語意逐一寫對應畫面操作與不呼叫的端點 | 同上 |
| MODIFY | `ios/dsl.md` Then `這次操作沒有留在手機上等補送`（搬入後） | 用途清單加主管四動作，措辭改「使用者」 | 搬移時順帶擴充用途說明，契約不變 |
| NOOP | `be/工務單/完修與回到原任務.feature`、`開立工務單與急件.feature` 既有核准／退回／重送／驗收 Rule | 不動 | 行為不變，acceptance 直接由既有 Rule 承接 |
| NOOP | `be/查房/免查房鎖定與結案.feature`、`免查抽查與合格率.feature` | 不動 | 全通過與抽查規則不變 |
| NOOP | `be/推播/員工推播事件.feature` | 不動 | 查房通過／退回推播不變；退回報修單的推播依既有分工放 `工務單` 模組 |
| NOOP | `be/帳號與登入/*`、`be/今日進度/*`（D-14）、`be/待認領/*`、`be/任務執行/*`、`be/房間交辦/*` | 不動 | 既有能力足夠；`getMyToday` 主管語意由契約 description 承接 |
| NOOP | `ios/工務單/工務視角.feature`、`工務單房務端生命週期.feature` | 不動 | 工務端與房務端呈現不變 |
| NOOP | `ios/今日進度/*`、`ios/任務執行/*.feature`、`ios/房間交辦/*`、`ios/查一下/*` | 不動 | 主管做名下的單沿用既有任務頁／完成畫面 Rule，不複製 |

**DELETE**：無獨立 DELETE。下列三個搬移的舊位置 row 已刪除。

### DSL row 搬移（語意不變）

| 動作 | 句型 | 舊位置 | 新位置 | 說明 |
| --- | --- | --- | --- | --- |
| MOVE | `這次操作沒有留在手機上等補送` | `ios/任務執行/dsl.md`（Then 表第 2 列） | `ios/dsl.md` Then 表 | 語意不變；系統層的主管離線 Rule 與任務執行的離線恢復 Rule 共用，契約（本機佇列筆數不變、後端狀態不變）完全一致，依唯一歸屬判準上提到根，舊位置已刪 |
| MOVE | `後端沒有對 "{員工}" 送出任何推播` | `ios/任務執行/dsl.md`（Then 表） | `ios/dsl.md` Then 表 | 語意不變；主管工作台查房「項目不符時不推播」與任務執行「超時提示不推播」共用，舊位置已刪 |
| MOVE（下放） | `"{員工}" 已在 App 登入，停在首頁` | 本輪先建在 `ios/dsl.md`（根） | `ios/系統層/dsl.md` Given 表 | 本輪新建 row；稽核指出只有系統層使用，依判準下放，根的 row 已刪 |

### 順手補的既有缺口（不在 003 範圍）

讓 ios-app 拓樸稽核從 FAILED 變 PASSED；不屬 003 需求，單純補既有 feature step 缺的 DSL row。

| 動作 | Truth 規格 | 改動摘要 |
| --- | --- | --- |
| ADD | `ios/推播/dsl.md` Then `通知中心沒有「全部標為已讀」` | 既有 `推播/通知中心.feature:76` 的 step 原本沒有任何 DSL row |
| ADD | `ios/任務執行/dsl.md` Then `"{任務}" 的面板換成工務單表單，原任務還沒有被中斷` | 既有 `任務執行/延後與中斷原因面板.feature:48` 的 step 原本沒有任何 DSL row |

### 刻意不承接

- acceptance `從待審核清單核准或退回報修.feature:39-40` 阿芳在 App 按「補資料再送出」：ios-app 端沒有這個 When，屬既有缺口，依指揮官裁決不在 003 補；後端由 `be/工務單/開立工務單與急件.feature` 既有 Rule 承接。
- acceptance 的後設斷言（「與房務員工一致」「App 裡沒有任何入口」「通知與我的內容一致」）縮成可機械驗的 Then，見盤點檔 C 節六項。
- acceptance 用語「退房打掃」在 truth 統一寫既有 DSL 的「退房清潔」。
