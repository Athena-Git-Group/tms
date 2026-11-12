# Truth Delta: 003-manager-sign-off-queues

**Plan Package**: `specs/plans/003-manager-sign-off-queues`
**Truth Root**: `specs/truth`

> 本檔由 truth owner skill（`/api-plan`、`/data-plan`、`/dsl-refine`）透過 `/truth-delta` 追加。
> `/specify` 只建立骨架，不得寫入任何 `specs/truth/**` 的實際改動。

## /technical-research

_尚未執行。_

## /api-plan

本段的目標：契約收回整條主管審核，驗收補上不通過，完修補上不需處理，主管待辦依主管種類只回看得到的類別。列舉值逐字沿用 `/data-plan` 定案：任務狀態 `closed`（收回 `pending_review`、`rejected`）、完修結果 `no_action_needed`、推播事件 `repair_verification_failed`（收回 `repair_rejected`）、驗收不通過的去處 `assignee`｜`pool`。

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| DELETE | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `approveRepairTicket`、`rejectRepairTicket`、`resubmitRepairTicket` | 三支 operation 移除。 | FR-016、FR-017；spec 影響表 DELETE 列與「交給 /api-plan 的備忘」第一點，指揮官 handoff 明列收回。 |
| DELETE | `specs/truth/contracts/openapi.yaml` -> `paths./repair-tickets/{ticketId}/approve`、`/reject`、`/resubmit` | 三條路徑移除。 | 同上；路徑留著會指向已刪的 operation。 |
| DELETE | `specs/truth/contracts/components/schemas/工務單.yaml` -> `RejectRepairTicketRequest`；`openapi.yaml` -> `components.schemas.RejectRepairTicketRequest` | schema 與根檔索引移除。 | 唯一使用者 `rejectRepairTicket` 已刪。 |
| DELETE | `specs/truth/contracts/components/schemas/工務單.yaml` -> `RepairTicket.reviewRequestedAt`、`RepairTicket.rejectReason` | 兩個欄位移除。 | 沒有「進入等主管審核的時間」與主管退回原因可回；資料側 `reject_reason` 已由 `/data-plan` 移除。 |
| DELETE | `specs/truth/contracts/components/schemas/主管工作台.yaml` -> `ReviewQueueItem`、`ManagerQueues.review`、`ManagerQueues.counts.review`；`openapi.yaml` -> `components.schemas.ReviewQueueItem` | 待審核的列型別、清單與數字移除。 | FR-004、FR-039；沒有待審核這一類。 |
| DELETE | `specs/truth/contracts/components/schemas/shared.yaml` -> `Error` 的 `reject_reason_required` | 錯誤碼移除。 | 沒有主管退回報修這個動作。 |
| MODIFY | `specs/truth/contracts/components/schemas/shared.yaml` -> `PushPayload.event` | 以 `repair_verification_failed` 取代 `repair_rejected`（總數仍十八）。description 改寫：主管驗收不通過且單回到原處理人名下時發給原處理人；title 帶房號、單號與「驗收不通過」，body 是原因；target `kind=ticket`。單回到工務待認領時不發這一封，也不發 `pool_new_request`。`notifications.event` 以 `$ref` 共用，自動跟著改。 | FR-017、FR-027；spec 假設「單回到工務待認領時不推播個人」；指揮官裁決「驗收不通過退回工務待認領時不推播工務池」。值與 `notifications.event`、`task_events.type` 逐字相同。 |
| MODIFY | `specs/truth/contracts/components/schemas/shared.yaml` -> `RepairTicketSummary.state` | 值集改為 `pool｜accepted｜doing｜done｜closed｜cancelled`：收回 `pending_review`、`rejected`，新增 `closed`。說明改寫為 `done` 是已完修、等主管驗收，驗收通過轉 `closed`，不通過退回 `accepted` 或 `pool`，不需處理直接 `closed`。 | FR-016、FR-019、FR-020、FR-024、FR-025；`/data-plan` 的 `tasks.state` 十值。 |
| MODIFY | `specs/truth/contracts/components/schemas/任務.yaml` -> `tasks.state`、`tasksRequest.state` 的 description | CHECK 值集說明改為十值，含 `closed`、不含 `pending_review`、`rejected`。 | 與 `data-model.dbml` 的 `chk_tasks_state` 對齊。 |
| MODIFY | `specs/truth/contracts/components/schemas/shared.yaml` -> `Error` 錯誤碼清單 | 新增 `verification_reason_required`（422）；`state_conflict` 改寫為驗收（通過或不通過）與查房送出共用；`ticket_closed` 說明涵蓋已完修、已結案、已撤回；補一段：角色或身分不符一律 403 `forbidden`，App 直接顯示 message、不依 code 分辨。 | FR-025、FR-036；spec 影響表「`state_conflict` 說明」列；ui-plan 第 7 點要求寫明兩種 403 共用碼時 App 不需分辨。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `verifyRepairTicket` | 改為一支帶結果：請求 `VerifyRepairTicketRequest`（必帶），200 回應改為 `RepairVerificationOutcome`，新增 422。只開放 `role=manager`，房務主管與工務主管都能呼叫、含自己完修的單，其他角色 403 `forbidden`；只接受 `state=done`，其他狀態 409 `state_conflict`；通過 `done→closed` 寫驗收人與時間；不通過原因空白 422 `verification_reason_required`，成立時寫最近一次不通過三欄、清空完修時間與結果與說明、依換人判定退回 `accepted`（推播原處理人）或 `pool`（不推播）；必帶 `Idempotency-Key`。拿掉「本輪不支援驗收不通過」與「state 仍維持 done」。 | FR-021、FR-023～FR-027、FR-029、FR-036、FR-040、SC-005。一支帶結果而不是兩支：兩種結果共用同一個前置狀態與同一道先到先決的守衛，兩位主管一個按通過一個按不通過也只撞同一個 409（FR-036「不論送的是通過還是不通過」）；App 只寫一套送出、離線阻擋與冪等重送，結果卡讀同一個形狀；與 `createInspections` 的「請求帶判定、回應帶 `result`＋`destination`」同一個樣式。代價是多一個必填 `result`。 |
| ADD | `specs/truth/contracts/components/schemas/工務單.yaml` -> `VerifyRepairTicketRequest`、`RepairVerificationOutcome`；`openapi.yaml` -> 兩者的 `components.schemas` 索引 | 請求 `{result: passed｜failed, reason}`，`failed` 時 `reason` 必填。回應 `{taskId, ticketNo, roomNo, isPublicArea, result, destination: closed｜assignee｜pool, assigneeName, reason, decidedBy, decidedAt}`；`destination=pool` 時仍帶原處理人姓名。 | FR-026、ui-plan 第 3 點：結果卡要寫「回到阿強名下」「阿強已下班」，而退回 `pool` 時工務單的處理人已清空，只有回應帶得出原處理人。`assignee`／`pool` 與 `task_events.repair_verification_failed` 的 `payload.destination` 同值。 |
| MODIFY | `specs/truth/contracts/components/schemas/工務單.yaml` -> `RepairTicket` | `resolutionType` 加 `no_action_needed` 成四值，nullable 語意改為「`done`、`closed` 有值，其他狀態為 null，退回時清空」；`resolutionNote`、`doneAt` 寫明退回時清空；`completionPhotos` 只回最近一次不通過之後的這一輪；`verifiedBy`／`verifiedAt` 定義為通過的那一次、不需處理結案為 null；新增 `lastVerifyFailedReason`、`lastVerifyFailedBy`（顯示名）、`lastVerifyFailedAt`（nullable、不清空）；新增必填 `viewerIsAssignee`；`assigneeName` 寫明退回 `pool` 時為 null；description 寫明三種狀態（等驗收、已結案兩種、退回重修中）怎麼從欄位組出來；`origin` 說明拿掉審核字樣。 | FR-018、FR-019、FR-024、FR-028、FR-038；ui-plan 第 2 點。不另加「結案方式」欄位：`state=closed` 時用 `resolutionType` 是不是 `no_action_needed` 就分得出來，再加一個欄位只會多一個要對齊的來源。`viewerIsAssignee` 由後端比對，不讓 App 拿顯示名稱比（可能重名）。上一輪完修照片不對外提供，因為 `/data-plan` 退回時已清空上一輪的完修結果與說明，只留照片會變成一段沒頭沒尾的「上一次完修」。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `readRepairTicket` 的 description | 可查看者寫明含所有主管、不分部門；主管動作只在 `state=done` 出現（通過、不通過）；說明 `viewerIsAssignee` 的用途。 | FR-021、FR-038。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `createRepairTicket` 的 description；`components/schemas/工務單.yaml` -> `CreateRepairTicketRequest.assigneeId` | 送出後的去向改為：有 `assigneeId` 直接 `accepted`，其餘一律 `pool`，不分一般件與急件、不分三條開單路徑；「略過工務待認領與審核」拿掉「與審核」。 | FR-016；spec 影響表「直接指定處理人的說明」列。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `withdrawRepairTicket`、`appendRepairTicketPhotos`、`precheckRepairTicket` 的 description | 撤回只剩 `pool→cancelled`；補照片在 `done`、`closed`、`cancelled` 時回 409 `ticket_closed`；重複提示排除 `done`、`closed`、`cancelled`。 | FR-017；`/data-plan` 重複單檢查的過濾改為 `state NOT IN (done, closed, cancelled)`。 |
| MODIFY | `specs/truth/contracts/components/schemas/任務.yaml` -> `CompleteTaskRequest` | `resolutionType` 加 `no_action_needed`；`photoIds` 的 `minItems` 由 1 改為 0，規則寫在欄位說明：fixed、temporary、cannot_fix（含省略）這一次請求至少 1 張，否則 422 `completion_photo_required`，退回重修後再完修也一樣；`no_action_needed` 可省略或空陣列。`resolutionNote` 拿掉「判斷不需維修時在這裡註明」。 | FR-018、FR-019、SC-012。照片規則放在 schema 欄位說明而不是只寫在 operation：條件必填在 OpenAPI 3.0 寫不成 schema 約束，`minItems` 只能放寬到 0，條件留在離欄位最近的地方。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/任務.yaml` -> `tasksComplete` 的 description；`components/schemas/任務.yaml` -> `TaskCompletion` 的 description | 修繕任務四選一完修結果；三種轉 `done` 等驗收，`no_action_needed` 直接 `closed`、不進待驗收；四種都推播報修人；拿掉「等主管驗收子狀態，不開新狀態值」。回應 `TaskCompletion` 的 `state` 直接說明是 `done`（進入待驗收）還是 `closed`（直接結案）。 | FR-019、FR-020；ui-plan 第 4 點要回應讓 App 知道是直接結案還是進入待驗收。沿用既有 `state` 欄位而不另加結果欄位：`TaskCompletion` 本來就帶任務詳情的 `state`，而它就是權威值。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/主管工作台.yaml` -> `getManagerQueues`；`components/schemas/主管工作台.yaml` -> `ManagerQueues` | 依主管種類回類別：房務主管（`dept` 不是 `engineering`，含 null）回 `inspection` 與 `verify`；工務主管（`dept=engineering`）只回 `verify`，`inspection` 與 `counts.inspection` 兩個鍵不出現。`required` 只剩 `counts`、`verify`。待驗收公式改為 `type=repair` 且 `state=done` 且完修結果不是 `no_action_needed`，不再看 `verifiedAt`，含呼叫者本人完修的單；`waitingSince` 是最近一次完修時間。待審核段落全部刪除。 | FR-004、FR-007、FR-021、FR-022、FR-039、SC-002；指揮官裁決的主管種類規則。三種表達裡選「省略鍵」：回空陣列等於「待查房 0 張」，會跟房務主管的 0 格混在一起；另帶主管種類欄位則是 ui-plan 明說不需要，App 已用同一條規則自己算。 |
| MODIFY | `specs/truth/contracts/components/schemas/主管工作台.yaml` -> `VerifyQueueItem`、`QueueItemBase` | `VerifyQueueItem` 新增必填 `wasVerificationFailed`（`last_verify_failed_at` 有值）；`resolutionType` 改 nullable（舊單可能為 null）且寫明不會出現 `no_action_needed`；`QueueItemBase` 的 `waitingSince` 說明拿掉待審核、`taskId` 說明改為可用在 `readRepairTicket` 與 `verifyRepairTicket`。 | ui-plan 第 1 點「是否曾被驗收不通過」畫「重修後再驗」；`/data-plan` 交接「舊完修單可能為 NULL，契約要容納」。給布林而不是時間：清單只畫標籤，詳細的原因與時間進詳情才取。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/查房.yaml` -> `createInspections` 的 description；`openapi.yaml` -> `tags[查房 API]` | 只有房務主管能送出：工務主管呼叫回 403 `forbidden`（message「你沒有查房的權限」），即使單停在 `done`；與自清自查、無權限角色共用 `forbidden`，App 直接顯示 message。409 說明改為與 `verifyRepairTicket` 同一個碼。 | FR-007、指揮官裁決；ui-plan 第 7 點。共用碼不另立：三種 403 對 App 都是「顯示 message、回首頁」，分碼沒有畫面差別。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `getMyToday`；`components/schemas/員工工作台.yaml` -> `MyToday.engineeringSummary`、`completedCount`、`todayTasks` | `engineeringSummary` 對部門是工務的呼叫者都有值，含工務主管；主管首頁「我名下的單」房務主管讀 `todayTasks`、工務主管讀 `engineeringSummary.tickets`。已結束狀態都加 `closed`：今日完成單數、臨時需求完成數、`todayTasks` 收錄與排序。`completedToday` 改為今天完修且 `done` 或 `closed`。 | FR-013、FR-014；ui-plan 第 6 點；`/data-plan` 讀取端清單（`TaskExecutionService` 的 `TODAY_TASK_STATES`、`doneLike`、今天已完修）。後端 `TaskExecutionService` 以 `actor.dept()` 判斷是否回 `engineeringSummary`，工務主管已會拿到，契約補上事實。 |
| MODIFY | `specs/truth/contracts/components/schemas/員工工作台.yaml` -> `TodayTask.state`、`EngineeringTicket.state` | 兩者的 enum 加 `closed`；拿掉等審核、被退回的說明。`tickets` 收錄今天完修的 `done` 與 `closed`，排序與筆數關係跟著改。 | 結案的工務單若不收，驗收通過或不需處理結案後會從首頁與今日統計消失。 |
| ADD | `specs/truth/contracts/components/schemas/員工工作台.yaml` -> `EngineeringTicket.verificationReturnReason`、`PoolItem.verificationReturnReason` | nullable：單正處於驗收退回、還沒再完修時，帶最近一次不通過原因；其他情況為 null。`listDeptPool` 說明補一句。 | ui-plan 第 6 點：工務首頁手上的單與工務待認領單卡要畫「驗收退回」與原因。只在重修中有值，App 不必自己拿狀態與時間推「現在算不算退回中」。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `listDeptPool` 的 description | 寫明主管不論部門都能帶 `housekeeping` 或 `engineering`，只看不搶；被驗收退回的單照常在工務池，`source` 仍依開單路徑換算。 | FR-030、FR-031；ui-plan 第 6 點。 |
| NOOP | `specs/truth/contracts/components/schemas/員工工作台.yaml` -> `PoolSource` | 不加 `verification_return`。 | 工務池的 `PoolItem.source` 由 `tasks.source` 換算（`TaskExecutionService.java:556-589`），不讀 `tasks.pool_source`；加了也不會出現，反而會讓工務池分流磚的「房務報修」「櫃檯」張數對不上。驗收退回改由 `verificationReturnReason` 表達。 |
| MODIFY | `specs/truth/contracts/openapi.yaml` -> `tags[主管工作台 API]`、`tags[工務單 API]` | 主管工作台標籤改為房務主管兩類、工務主管一類，主管動作指向 `createInspections` 與 `verifyRepairTicket`；工務單標籤加驗收、寫明開單直接進工務待認領。 | spec 影響表「主管工作台 API 標籤說明」列。 |
| NOOP | `specs/truth/contracts/components/schemas/shared.yaml` -> `PushTarget`；`groups/現場作業/員工工作台.yaml` -> `listDeptPool` 的 `dept` 參數 | 不動。 | `kind=ticket` 已涵蓋驗收不通過的去處；主管切換部門走既有 `dept`。 |

**破壞性變更清單**（會牽動既有後端 controller、stepdef 與 App）：
- 後端 controller 與服務：`tms/src/main/java/com/athena/tms/repair/RepairController.java` 刪 `approveRepairTicket`、`rejectRepairTicket`、`resubmitRepairTicket` 與 `RejectRepairTicketRequest` record；`verifyRepairTicket` 改收 `VerifyRepairTicketRequest` body、回 `RepairVerificationOutcome`。`repair/RepairService.java` 刪 `approve`、`reject`、`resubmit`，`verify` 加不通過分支與推播，`read` 組 `RepairTicket` 時移除 `reviewRequestedAt`、`rejectReason`，新增 `lastVerifyFailed*`、`viewerIsAssignee`，`completionPhotos` 依 `last_verify_failed_at` 過濾。`controller/InspectionApiController.java` 的權限檢查要擋 `dept=engineering` 的主管。`service/ManagerQueueService.java` 刪 `review`，依主管種類省略 `inspection`，`verify` 加 `wasVerificationFailed`。`service/TaskExecutionService.java` 的 `PoolItem` 與 `EngineeringTicket` 組裝加 `verificationReturnReason`。完成任務的照片檢查改為 `no_action_needed` 放行。
- 後端 stepdef：`tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairWhenSteps.java` 的 `approveTicket`、`postReject`（及其呼叫者）、`resubmitTicket` 刪除，`verifyTicket` 要帶 `{"result":"passed"}` body；`steps/repair/RepairGivenSteps.java` 的 `managerAlreadyVerifiedTicket` 同樣要帶 body，表格的「退回原因」欄與 `pending_review` 分支、寫 `reject_reason` 的 INSERT 要拿掉；`steps/repair/RepairThenSteps.java` 對 `rejectReason`、`repair_rejected` 的斷言；`steps/managerqueue/ManagerQueueThenSteps.java` 的 `reviewQueueIs`、`reviewQueueExcludes`、`lastEnteredPendingReview` 與 `"待審核"→"review"` 對照；`bdd/support/TaskFixtures.java` 的 `pending_review`、`rejected` 建單分支；`bdd/support/Terms.java`、`steps/common/CommonThenSteps.java` 的狀態對照。
- App BDD：`app/bdd/src/test/java/com/athena/app/bdd/BackendChannel.java` 的 `rejectRepairTicket` 刪除、`verifyRepairTicket` 要帶 `result` body、狀態對照的「主管未核准」刪除、帶退回原因建單的 `createRepairTicket` 多載；`steps/manager/ManagerGivenSteps.java`、`ManagerWhenSteps.java`、`ManagerThenSteps.java`、`ManagerContext.java` 與 `steps/push/PushThenSteps.java` 的 `review` 鍵；`steps/repair/RepairGivenSteps.java` 的 `pending_review`、`rejected` 對照；`steps/repair/RepairWhenSteps.java` 的退回面板步驟。
- App：`app/mobile/lib/core/api_client.dart` 刪 `resubmitRepairTicket`、`ReviewQueueItem`、`ManagerQueues.review` 與 `ManagerQueueCounts.review`；`RepairTicketDetail` 刪 `reviewRequestedAt`、`rejectReason`，加 `lastVerifyFailedReason`、`lastVerifyFailedBy`、`lastVerifyFailedAt`、`viewerIsAssignee`；`VerifyQueueItem.resolutionType` 改可為 null（目前 `?? 'fixed'` 會把舊單誤標為已修好）並加 `wasVerificationFailed`；`completeTask` 的 `photoIds` 在 `no_action_needed` 時可為空；新增驗收方法（目前 `api_client.dart` 沒有 `verifyRepairTicket`）。狀態註解（`api_client.dart:1100`、`:1982`）的 `pending_review｜rejected` 換成 `closed`。`EngineeringTicket`、`PoolItem` 解析加 `verificationReturnReason`。
- `createInspections` 對工務主管新增 403，現行 `InspectionApiController` 只看角色，部門為工務的主管目前送得出去。

**待決**：
- 驗收不通過後再完修是否再推播報修人：`tasksComplete` 寫的是「四種結果都推播報修人」，但完修推播的冪等鍵是 `"repair_resolved:" + ticketTaskId`（`repair/RepairService.java:424`），重修後再完修會被當成重送而不發。若要每次完修都發，鍵要帶事件序號；若只發第一次，`tasksComplete` 的說明要補一句。交使用者決定。
- `VerifyRepairTicketRequest.reason` 沒有長度上限。資料層只規定去頭尾空白後不得為空；若要限制長度，需要 `/data-plan` 與畫面一起定。
- 工務人員沒有個人班次，換人判定對工務實際只看請假與飯店班別時段（`/data-plan` 待決同一件），契約的 `destination` 照判定結果回，不受影響。
- 不需觸發 Wave 3：本輪契約用到的欄位與值（`closed`、`no_action_needed`、`repair_verification_failed`、`last_verify_failed_*`、`assignee`｜`pool`）都在 `/data-plan` 定案內；`viewerIsAssignee`、`wasVerificationFailed`、`verificationReturnReason`、`RepairVerificationOutcome.assigneeName` 都由既有欄位或事件推得。

## /data-plan

本段的目標：工務單沒有主管審核，報修送出直接成立；結案改由獨立狀態 `closed` 表達，只有兩條路進得去，一是主管驗收通過，二是完修結果為不需處理（`no_action_needed`）時完成當下直接結案；驗收不通過寫原因、退回原處理人或工務待認領。下表以目前 `specs/truth/data/data-model.dbml` 為準，列出本輪對它的改動。

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| MODIFY | `specs/truth/data/data-model.dbml` -> `tasks.state` 的 CHECK 與 note | 值集改為十值 `triage｜pool｜accepted｜doing｜done｜inspected｜closed｜deferred｜interrupted｜cancelled`：新增 `closed`（已結案，只有 `type=repair` 會出現），移除 `pending_review`、`rejected`。`type=repair` 的 `done` 定義為「已完修、等主管驗收」，不再是終點。 | FR-016、FR-017、FR-020、FR-024、FR-040；spec 關鍵實體「任務……多一個已結案」。選新值而不沿用 `inspected`：`inspected` 牽動 `rooms.hk_state`、`tasks.inspection`、查房合格率與飯店房態系統回寫，借用會讓「僅 cleaning 走 inspected」失效；不沿用 `done` 加 `verified_at` 判斷，因為驗收通過、不需處理結案、等驗收三種情況要在狀態本身就分得開，主管待辦與步驟條才不必每處都 JOIN `repair_tickets`。命名 `closed` 與 `InspectionOutcome.destination` 的 `closed`（結案）同一個詞。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `tasks` 的 `Note`（工務單狀態轉移段） | 整段改寫：開單一律 `pool`（直開帶處理人為 `accepted`），不分一般件與急件；`doing→done`（fixed、temporary、cannot_fix）；`doing→closed`（no_action_needed，完成那一句守衛 UPDATE 直接寫 `closed`）；`done→closed`（驗收通過，`task_events.type=repair_verified`）；`done→accepted`（不通過、原處理人在班）；`done→pool`（不通過、原處理人下班或請假，`pool_source=verification_return`）。不通過的兩條路同一交易清空 `done_at`、`resolution_type`、`resolution_note`，寫 `last_verify_failed_*`，只寫一筆 `repair_verification_failed`；`worked_sec`、`started_at`、`redo_count` 不動。驗收兩種結果都以 `state='done'` 為守衛；`closed` 是終點。開單去向判定段與審核轉移段刪除。 | FR-016、FR-019、FR-024、FR-025、FR-036、FR-040、SC-005。`no_action_needed` 比照免查房打掃 `doing→inspected` 一步落地，不經 `done`，主管待辦就不可能短暫看到它。不通過只寫一筆事件，SC-005 要求「對應的任務事件只有一筆」，查房退回到待認領那種「退回再退池」兩筆事件的寫法不沿用。重修工時接著累計，因為工務首頁的已做分鐘要反映這張單實際花的工；`redo_count` 留給查房，避免工務單卡出現「重做」字樣。 |
| ADD | `specs/truth/data/data-model.dbml` -> `tasks` 的 `Note`（驗收不通過的下班或請假判定段） | 寫明沿用查房退回的換人判定：請假中、現在落在飯店所有班別時段之外、已過他最近一份排房計畫班次的下班時間，任一成立即視為接不了；都判定不了時視為在班、退回他名下。註明工務人員沒有排房計畫，對工務實際只看前兩項。 | FR-025、spec 假設「比照查房退回換人規則」。查房的判定函式 `InspectionService.cleanerUnavailable`（`tms/src/main/java/com/athena/tms/service/InspectionService.java:218`）本身不限部門，可直接套到工務人員；它的第三項讀 `plan_rooms`，只有房務有資料，工務會落到「視為在班」。資料層不為工務另建排班，理由見下方待決。 |
| ADD | `specs/truth/data/data-model.dbml` -> `tasks` 的 `Note`（已結束的狀態段） | 寫明 cleaning 以外的 `done`、`inspected`、`closed` 都算完成；工務單的 `done` 只是已完修，主管待辦、詳情步驟條與狀態列要分辨 `done` 與 `closed`；把 `done` 當已結束的讀取端一律要同時認得 `closed`。「今天」判定段的「已結束」同步列出三個值。 | 新增狀態值會波及所有讀 `tasks.state` 的地方（plan 介面 7 風險說明）；讀取端清單見下方。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `tasks.done_at` 的 note | 工務單是最近一次完修的時間：驗收不通過時清空，再完修時重寫；驗收通過與不需處理結案時保留。 | 待驗收排序鍵是「最近一次完修的時間」（spec 判定公式表）；再完修後要依新時間排，不能沿用上一輪。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `tasks.pool_source` 的 CHECK 與 note | 加值 `verification_return`：驗收不通過時原處理人下班或請假，單回工務待認領。 | `pool_source` 是「最近一次進池的來源」，資料庫有 CHECK；借用 `inspection_return` 會讓查房退回的統計混進工務單。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `tasks` 的 `Note`（主管待辦判定段） | 三類改為兩類並刪除待審核段（含它的排序鍵來源與「日後存欄位」的落點）。新增「誰看得到」：房務主管看待查房與待驗收，工務主管只看待驗收，查房送出只開放房務主管。待查房公式與排序鍵不變。待驗收改為 `type=repair` 且 `state=done` 且 `repair_tickets.resolution_type IS DISTINCT FROM 'no_action_needed'`，不再看 `verified_at`，不排除查看者本人完修的單；排序鍵 `done_at`；同一時刻依房號再依單號。 | FR-004、FR-007、FR-020、FR-021、FR-022、FR-033、FR-039、SC-002、spec 判定公式表與其下說明。用 `IS DISTINCT FROM` 而不是 `<>`：`resolution_type` 出現前就完修的舊單為 NULL，`<>` 會把它們漏掉。驗收通過即離開 `done`，`verified_at` 條件變成多餘。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `staff_accounts.dept` 的 note | 補主管種類：`role=manager` 且 `dept=engineering` 為工務主管，其餘（含 NULL、housekeeping、frontdesk）為房務主管；衍生值、不存欄位；只決定看得到哪幾類待辦與能不能送出查房，不影響誰能驗收。 | FR-002、FR-021、spec 關鍵實體「主管種類（新，衍生）」。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_tickets.resolution_type` 的 CHECK 與 note | 值集加 `no_action_needed`，成為四值 `fixed｜temporary｜cannot_fix｜no_action_needed`。寫入規則由「只在完修那一刻寫一次，之後不改」改為「每次完修寫入，驗收不通過時清空，再完修寫這一次的結果」；`done`、`closed` 必有值（欄位出現前的舊單例外）。no_action_needed 不要求照片、不建追蹤單、不改房況、直接 `closed`。 | FR-018、FR-019、SC-012。清空而不是保留上一輪：重修中的單不該帶著上一輪的完修結果，否則「NULL 等於目前不是已完修或已結案」這條讀法就破了；歷次結果仍在 `task_events.completed` 的 payload。命名用 spec 備忘的 `no_action_needed`。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_tickets.resolution_note` 的 note | 改為完修時寫入、選填（不需處理也一樣）；驗收不通過時清空，再完修時重寫。拿掉「判斷不需維修時在這裡註明」。 | spec 影響表 `CompleteTaskRequest` 列拿掉「判斷不需維修也用完成結案並在說明註明」；與 `resolution_type` 同步清空，避免再完修沒寫說明時顯示上一輪的說明。 |
| DELETE | `specs/truth/data/data-model.dbml` -> `repair_tickets.reject_reason` | 欄位移除。 | FR-017：沒有主管退回報修這個動作。驗收不通過的原因另立欄位，不重用它，因為兩者的觸發者、收件人與生命週期都不同，重用會讓舊資料的退回原因被讀成驗收不通過。 |
| ADD | `specs/truth/data/data-model.dbml` -> `repair_tickets.last_verify_failed_reason`、`last_verify_failed_by`、`last_verify_failed_at`；`Ref: repair_tickets.(tenant_id, last_verify_failed_by) > staff_accounts.(tenant_id, id)` | 三欄 nullable，同一句 UPDATE 寫入、同為 NULL 或同有值。每次不通過覆寫；重修、再完修、驗收通過都不清空。`last_verify_failed_at` 也是完修照片分輪次的界線。原因去頭尾空白後不得為空。 | FR-025、FR-028、SC-011；spec 關鍵實體「另記最近一次驗收不通過的原因、判定人與時間」。存法定為「欄位加事件」，見下方說明。不清空：FR-028 要退回重修中顯示最近一次的原因與判定人，再完修與結案後保留也讓詳情能說明「這張單曾被退回過一次」，顯示與否交 UI 決定。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_tickets.verified_by`、`verified_at` 的 note | 定義為「驗收通過的那一次」，與 `done→closed` 同一交易寫入、之後不改；房務主管或工務主管皆可，含驗收自己完修的單；以不需處理結案的單為 NULL。 | FR-021、FR-024、spec 關鍵實體「驗收人與驗收時間記通過的那一次」。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_tickets` 的 `Note` | 整段改寫為現行流程：轉移全集、開單一律直接進工務待認領、完修四選一（含退回重修後再完修以這一次為準：temporary 再建追蹤單、cannot_fix 再轉停用）、驗收通過與不通過的寫入內容、推播誰、照片不解綁、為什麼不通過同時存欄位與事件、定期保養同樣要驗收、「今天已完修」以 `done` 或 `closed` 計、待驗收公式與名稱 JOIN。順手修正既有錯字「櫃檮」。 | 舊段落寫的是審核流程與「驗收不開新狀態值」，與 FR-016、FR-017、FR-020 衝突。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_tickets` 索引 `(tenant_id, room_id, category)` 的 note | 重複單檢查的過濾由 `state NOT IN (done, cancelled)` 改為 `state NOT IN (done, closed, cancelled)`。 | 不加 `closed` 的話，已結案的單會被當成還沒處理，報修人被引導去補照片到一張已結案的單。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_photos` 的 `Note` | 完修照片不變條件改為「每次以 fixed、temporary、cannot_fix 完修，這次請求至少帶 1 張」，no_action_needed 不要求；驗收不通過不解綁已綁定的照片，分輪次看 `uploaded_at` 與 `last_verify_failed_at`。 | FR-018；acceptance「阿強開始重修，附 1 張完修照片再次完修」。「這次請求」是要讓舊照片不能抵用重修的證據。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `task_events.type` 的 note | 值集改為二十四值：移除 `repair_approved`、`repair_rejected`、`repair_resubmitted`；新增 `repair_verification_failed`；`repair_verified` 的語意寫明為 `done→closed`。註明資料庫對本欄沒有 CHECK，停用值的歷史列讀法見表 Note。 | FR-017、FR-025。新值與推播事件值同名，沿用 `repair_completed`、`inspection_failed` 事件與推播同名的慣例。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `task_events.payload` 的 note | 刪除 `repair_rejected` 帶 reason；新增 `repair_verification_failed` 帶 `reason`、`destination`（`assignee`｜`pool`）、`assigneeId`；`repair_verified` 不帶 payload；`type=repair` 的 `completed` 的 `resolutionType` 加 `no_action_needed`，且寫明它就是結案事件、不另寫 `repair_verified`。 | 事件是真相，重放要能還原去處與原處理人；去到 pool 時 `tasks.assignee_id` 已清空，原處理人只剩事件記得。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `task_events` 的 `Note` | 刪除待審核排序鍵段。新增：驗收歷程以本表為準；三個停用型別不再寫入、歷史列保留不刪；重放時 `created` 快照起點為 `pending_review` 視為 `pool`，三種停用事件只推進 `seq` 不改欄位。 | 本表只進不改（E-13），收回值不能刪歷史；這條重放規則讓重放結果與下方既有資料處置一致，影子庫重放比對（SC-006）不會因收回而失敗。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `notifications.event` 的 CHECK 值集與 `Note` | 十八值中以 `repair_verification_failed` 取代 `repair_rejected`（總數仍十八）。Note 改寫：驗收不通過且單回到原處理人名下時觸發，收件人是原處理人；回工務待認領時不發；文案帶房號、單號與原因，target 為 `kind=ticket`。刪除 `repair_rejected` 段。 | FR-017、FR-027、spec 假設「單回到工務待認領時不推播個人」；acceptance「阿強已經下班……阿強沒有收到驗收不通過的推播」。值與 `/api-plan` 的 `PushPayload.event` 必須逐字相同。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `automation_rules` 的 `Note` | 「保養單不經主管審核」改為「直接進工務待認領，完修後一樣要驗收」。 | FR-020、spec 假設「所有來源的工務單都要驗收」。 |
| NOOP | `specs/truth/data/data-model.dbml` -> 所有索引 | 不新增索引。待查房與待驗收都走 `(tenant_id, dept, state)`；`closed` 列跟其他已結束列一樣只被個人在手索引 `(tenant_id, assignee_id, state)` 掃到。 | 一家飯店同時在等主管的單是個位數到十位數。 |
| NOOP | `specs/truth/data/data-model.dbml` -> `tenant_config.key` 封閉鍵集 | 不新增每租戶時區，也不新增工務班別的設定鍵。 | 時區沿用使用者 2026-11-10 裁決；工務排班見待決。 |
| NOOP | `scripts/seed-demo.sh` 所需欄位（示範資料本身不是 truth） | FR-041 的情境都能用定案欄位種出：陳主管是一列 `staff_accounts`（`role=manager`、`dept=engineering`）；W-0108 維持 `done`、`resolution_type=fixed`、一列 `repair_photos`（`purpose=completion`、`status=uploaded`）；W-0109 為 `closed`、`verified_by` 指王主管、`verified_at` 有值；不需處理的結案單為 `closed`、`resolution_type=no_action_needed`、沒有 completion 照片、`verified_by` 為 NULL；驗收不通過退回中的單為 `accepted`、`done_at` 與 `resolution_type` 為 NULL、`last_verify_failed_*` 三欄有值，另配一筆 `task_events.type=repair_verification_failed`。202 窗簾掛勾那筆 `pending_review` 列要拿掉。 | 確認欄位夠用；seed 的 SQL 改寫屬實作。 |

**驗收不通過的存法**：欄位與事件兩者都存。`task_events.repair_verification_failed` 是歷次紀錄的真相，可重放、可稽核；`repair_tickets.last_verify_failed_reason`／`_by`／`_at` 是最近一次的投影，給工務單詳情、推播文案與 `/api-plan` 的 `RepairTicket` 直接讀。只存事件的話，詳情每次要逐單撈事件、解 JSON、再自己挑最近一筆；只存欄位的話，第二次不通過會蓋掉第一次，歷程就沒了。這與既有 `verified_by`／`verified_at`、`resolution_type` 的分工相同。重修再完修時不清空，下一次不通過才覆寫。

**既有資料處置**（給實作寫新的 Flyway migration，不改 V17、V22；順序是先拿掉舊 CHECK、處理資料列、再加新 CHECK）：
- `tasks`：`type=repair` 且 `state` 為 `pending_review` 或 `rejected` 的列一律轉 `pool`，`assignee_id` 為 NULL、`pool_source=staff_report`、`reminder_stage` 為 NULL。被退回的單也進 `pool` 而不是取消：新規則下誤報由工務以不需處理結單，直接取消會把可能真實存在的故障丟掉。`type=repair` 且 `state=done` 且 `repair_tickets.verified_at` 有值的列轉 `closed`。之後換上十值的 `chk_tasks_state`，`chk_tasks_pool_source` 加 `verification_return`。
- `repair_tickets`：移除 `reject_reason`（舊原因仍在 `task_events.repair_rejected` 的 payload）；新增三個 `last_verify_failed_*` 欄位與 `last_verify_failed_by` 的複合外鍵，舊列一律 NULL；`chk_repair_tickets_resolution_type` 加 `no_action_needed`。舊完修單的 `resolution_type` 不回填，待驗收公式用 `IS DISTINCT FROM` 容納 NULL。
- `notifications`：刪除 `event='repair_rejected'` 的列（三十天就清的短期清單，「主管未核准」對應的狀態已不存在），再換上新的 `chk_notifications_event`。`outbox` 裡還沒送出、payload 事件為 `repair_rejected` 的 push 一併刪除，避免舊推播在收回後才送達。
- `task_events`：不動。資料庫對 `type` 沒有 CHECK，停用值的歷史列依表 Note 的重放規則解讀。

**把 `done` 當作已結束、要認得 `closed` 的讀取端**（後端 `tms/src/main/java/com/athena/tms/`，給 `/api-plan` 與實作）：
- `common/codes/TaskState.java:15`：`ALL` 加 `closed`。
- `service/TaskExecutionService.java:295`：`openAndDoneRowsOf` 的 `state IN` 要加 `closed`，否則結案的單從首頁與今日統計消失。
- `service/TaskExecutionService.java:822`、`:837`：今日完成數 `doneLike` 與臨時需求完成數的 `"done"` 要含 `closed`。
- `service/TaskExecutionService.java:871`：工務「今天已完修」要以 `done` 或 `closed` 計，否則驗收通過或不需處理結案後今天的計數會往下掉。
- `service/TaskExecutionService.java:1019`（說明在 `:1015`）：`TODAY_TASK_STATES` 加 `closed`，說明拿掉 `pending_review`、`rejected`。
- `service/TaskExecutionService.java:1061`：工務單列排序的「已完修組」要含 `closed`。
- `service/TaskExecutionService.java:1087`：`isFinishedState` 加 `closed`。
- `repair/RepairService.java:182`：重複報修檢查 `NOT IN ('done', 'cancelled')` 加 `closed`。
- `repair/RepairService.java:257`：`listByRoom` 說明列的狀態清單改寫；`:274` 的 `<> 'cancelled'` 本來就收 `closed`，邏輯不用改。
- `service/TaskEngine.java:177`：完成的守衛 UPDATE 要多一支 `type='repair'` 且完修結果為 `no_action_needed` 時寫 `closed`（完修結果要傳進這句）。
- `service/TaskEngine.java:505-513`、`repair/RepairService.java:854-876`：驗收由「`done` 內補 `verified_*`」改為 `done→closed`，並新增不通過分支。
- `service/ManagerQueueService.java:149-169`：待驗收公式改寫；`:106-129` 待審核整段收回。
- 不必改：`service/TaskExecutionService.java:380`、`service/ManagerQueueService.java:89`（只看打掃單）；`service/TaskEngine.java:430`（評分只給住客需求）；`service/LeaveService.java:87`、`service/ShiftEndScanJob.java`（只動未結束的單）；`specs/truth/data/data-model.dbml` 的 `tasks` partial UNIQUE（只限打掃單）。
- 收回相關、與 `done` 無關但同一批要動：`service/TaskEngine.java:36`（`CREATABLE_STATES` 拿掉 `pending_review`）、`:418`（撤回只剩 `pool`）、`:459-499`（核准、退回、補資料再送出）；`repair/RepairService.java:508-518`（`reject_reason` 與 `review_requested_at`）。

**交給 Wave 2 `/api-plan` 的要點**：
- 逐字沿用的值：任務狀態 `closed`（`pending_review`、`rejected` 收回）；完修結果 `no_action_needed`；推播與事件 `repair_verification_failed`（`repair_rejected` 收回）；驗收不通過的去處 `assignee`｜`pool`；`PoolSource` 若對外暴露 `pool_source` 要加 `verification_return`（工務池的來源目前由 `tasks.source` 換算，確認不經它就不必加）。
- `RepairTicket` 對應欄位：`verifiedBy`／`verifiedAt`＝通過的那一次；最近一次不通過＝`last_verify_failed_reason`／`_by`／`_at`（不清空，顯示時機交 UI）；`resolutionType` 的 nullable 語意改為「`done`、`closed` 必有值，其他狀態為 NULL」。
- `verifyRepairTicket` 的不通過回應要帶去處（`assignee` 或 `pool`），由後端依換人判定算出，App 不自己看班表。
- `CompleteTaskRequest`：`no_action_needed` 允許 `photoIds` 為空；其他三種每次完修都要帶這次的照片。
- 完修推播報修人的冪等鍵目前是 `"repair_resolved:" + ticketTaskId`（`repair/RepairService.java:424`），退回重修後再完修會被當成重送而不發；若再完修也要推播報修人，鍵要帶事件序號。
- 待驗收清單列的 `resolutionType` 可定為非 nullable 的前提是舊單已有值；舊完修單可能為 NULL，契約要容納或由 seed 補齊。

**待決**：
- 工務人員沒有個人班次。現行判定對工務只看請假與飯店班別時段，所以早班工務在晚班時段內被判不通過，單會退回他名下；而班末掃描（`ShiftEndScanJob`）也只掃有排房計畫的房務，這張單會留在他名下到隔天。acceptance「阿強今天的班已經結束」的場景要用請假或飯店所有班別之外的時間才種得出來。要真正判定工務下班，需要另立工務排班資料，本輪不做，請使用者確認可接受。
- 退回工務待認領時是否推播工務池（比照新單進池的 `pool_new_request`），spec 只說「不推播個人」，交 `/api-plan` 與使用者確認。

## /dsl-refine

路徑縮寫：`ios/`＝`specs/truth/features/ios-app/`；`be/`＝`specs/truth/features/backend/`。本段以 commit 71cb9ab 的 interface truth（主管審核版）為比對基準，記錄依使用者 2026-11-11 裁決改寫後的語意單元。裁決內容：取消主管審核，報修直接成立；驗收一次、先到先決，`verifyRepairTicket` 帶 `{result, reason}`，不通過退回原處理人或工務待認領並推播 `repair_verification_failed`；`no_action_needed` 不用照片、直接 `closed`；工務主管首頁只有待驗收，查房只開放房務主管；每次完修都推播報修人。依據欄引用 acceptance 檔:行、FR 編號或 ui-plan「對 truth 的變更清單」。

### 後端

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| MODIFY | `be/dsl.md` -> 狀態用語對照 | 刪「等主管審核（工務單）＝`pending_review`」「主管未核准（工務單）＝`rejected`」；「已完修（工務單）」寫明是等主管驗收；新增「已結案（工務單）＝`closed`」。 | FR-017、FR-020；spec 影響表 `be/dsl.md` 列。 |
| MODIFY | `be/dsl.md` -> 部門用語對照 | 新增「房務主管＝role manager 且 dept 不是 engineering（含沒有部門）」「工務主管＝role manager 且 dept engineering」。 | FR-002、FR-039；`staff_accounts.dept` note。 |
| MODIFY | `be/dsl.md` -> Given `飯店有以下員工：` | `角色` 說明補「部門工務、角色主管就是工務主管」。 | 工務主管的案例都用這一句建帳號。 |
| MODIFY | `be/dsl.md` -> Given `已有以下工務單：` | `狀態` 值集加已結案、待開始，拿掉等主管審核與主管未核准；刪 `送出時間`、`退回原因` 欄；`處理結果` 加不需處理；`驗收人` 改為已結案（非不需處理）必填並落地 `closed`＋`repair_verified`；新增 `不通過原因`、`不通過判定人`、`不通過時間` 三欄，寫 `last_verify_failed_*` 與 `repair_verification_failed` 事件，退回重修中的單清空完修欄位，待認領時 `pool_source`=verification_return。 | FR-019、FR-024、FR-025、FR-028；data-plan `last_verify_failed_*`、`pool_source` 加值。 |
| MODIFY | `be/dsl.md` -> When `"{員工}" 判定 "{任務}" 查房全部項目通過` | 403 條件加「呼叫者是工務主管」，錯誤碼寫明 `forbidden`。 | FR-007；`createInspections` 契約。 |
| MODIFY | `be/dsl.md` -> Then `這次操作被拒絕，錯誤碼為 "{錯誤碼}"` | 舉例以 `verification_reason_required` 取代 `reject_reason_required`，另列 `completion_photo_required`、`forbidden`。 | spec 影響表 `be/dsl.md` 列。 |
| MODIFY | `be/dsl.md` -> Then `"{員工}" 收到以下推播：` | `內容` 舉例改為驗收不通過的原因。 | 退回推播收回。 |
| MODIFY | `be/主管工作台/dsl.md` -> 模組說明、When `"{員工}" 查看主管待辦` | 三類改為依主管種類：房務主管待查房與待驗收，工務主管只有待驗收、回應沒有 `inspection` 鍵；待驗收公式改為 `done` 且完修結果不是 `no_action_needed`、含本人完修。 | FR-004、FR-021、FR-039；`getManagerQueues` 契約。 |
| MODIFY | `be/主管工作台/dsl.md` -> Then `主管待辦的三格數字為：` → `主管待辦的格子數字為：` | 句型改名；`counts` 必須剛好是表格列出的格子，`格` 值刪待審核。 | FR-005、FR-039。 |
| ADD | `be/主管工作台/dsl.md` -> Then `主管待辦沒有待查房這一類` | 回應沒有 `inspection` 與 `counts.inspection` 鍵。 | FR-039；契約選「省略鍵」。 |
| MODIFY | `be/主管工作台/dsl.md` -> Then `待驗收清單依序為 {單號清單}`、`待驗收清單不包含 "{單號}"`、`待驗收清單中 "{單號}" 的完修結果為 …` | 權威狀態改為 `done` 且完修結果不是不需處理，不看 `verified_at`；每列多帶 `wasVerificationFailed`；`waitingSince` 是最近一次完修時間；不包含的用途改為維修中、已結案、等工務接單。 | FR-020、FR-022、FR-033。 |
| ADD | `be/主管工作台/dsl.md` -> Then `待驗收清單中 "{單號}" 標為重修後再驗` | 對 `VerifyQueueItem.wasVerificationFailed`。 | ui-plan 介面 2；契約欄位。 |
| DELETE | `be/主管工作台/dsl.md` -> Then `待審核清單依序為 {單號清單}`、`待審核清單不包含 "{單號}"` | 待審核不存在。 | FR-004、FR-017。 |
| MODIFY | `be/主管工作台/dsl.md` -> Then `這次查詢被拒絕，因為呼叫者不是主管` | 補 `code`=forbidden。 | 契約 403 共用碼。 |
| MODIFY | `be/主管工作台/主管待辦三類清單.feature` → `be/主管工作台/主管待辦清單.feature` | 檔名改名；背景刪 W-0201，W-0109 改已結案，加 W-0114（不需處理結案）與陳主管。Rule 調整：待驗收含三種結果與定期保養、不含維修中／等工務接單／已結案；格數改「房務主管兩格 2、3」。新增 Rule：以不需處理結案的單不在待驗收、工務主管只有待驗收一格、工務主管看不到待查房、兩種主管待驗收相同、工務主管自己完修的單也在待驗收（兩例）、重修再完修以最近一次完修時間排序、重修再完修標為重修後再驗。刪除 Rule：待審核不含急件、待審核排序鍵是重送時間。待查房三條、排序、跨日標記照舊。 | acceptance `主管登入後進自己的首頁.feature:42-115`；`從待驗收清單驗收完修工務單.feature:4-14、46-49、62-73`；`報修直接進工務待認領且不需處理直接結案.feature:43`。 |
| MODIFY | `be/主管工作台/主管待辦空清單與權限.feature` | 0 的格子改兩格（房務主管）並新增工務主管唯一一格為 0 的例子；Rule 名「三類待辦」改「主管待辦」。 | acceptance `主管登入後進自己的首頁.feature:86-94`。 |
| MODIFY | `be/工務單/dsl.md` -> 模組說明 | 「主管審核與驗收」裁決段改寫為「報修與驗收」：沒有審核、完修四選一、每次完修推播報修人、驗收一支端點帶結果、先到先決、不通過的寫入與兩種去處、推播規則；「完修三種處理結果」改四種。 | FR-016～FR-029；使用者 2026-11-11 裁決。 |
| DELETE | `be/工務單/dsl.md` -> 模組說明「已知的 truth 領先現況」段 | 整段移除。段內列的落差（完修只支援已修好、`createRepairTicket` 必帶 `sourceTaskId`、沒有工務今日統計、工務池沒有急件與來源篩選）都已不存在：`CompleteTaskRequest.resolutionType` 四選一含 `no_action_needed`、`createRepairTicket` 改收 `roomId`／`isPublicArea`／`assigneeId`、`getMyToday.engineeringSummary`、`listDeptPool` 的 `urgent`／`source` 參數都已在契約定案。 | 段落寫「三種完修結果、待 /api-plan」與 003 定案的四種完修結果衝突；指揮官 2026-11-11 指示。 |
| MODIFY | `be/工務單/dsl.md` -> When `"{員工}" 中斷 "{任務}" 並開立以下工務單：`、`"{員工}" 從 "{任務}" 開立以下工務單：`、`"{員工}" 中斷 "{任務}" 並以已準備的照片開立水電工務單` | 一般件不再進 `pending_review`，不分一般件或急件一律進 pool。 | FR-016。 |
| DELETE | `be/工務單/dsl.md` -> When `"{主管}" 核准 "{單號}"`、`"{主管}" 以原因 "{原因}" 退回 "{單號}"`、`"{主管}" 退回 "{單號}" 但沒有寫原因`、`"{員工}" 補資料再送出 "{單號}"`、Given `"{員工}" 已撤回 "{單號}"` | 審核三動作收回；「已撤回」只服務撤回後核准的撞單案例。 | FR-017；`approveRepairTicket`、`rejectRepairTicket`、`resubmitRepairTicket` 已由 /api-plan 刪除。 |
| MODIFY | `be/工務單/dsl.md` -> When `"{主管}" 驗收通過 "{單號}"` | body `VerifyRepairTicketRequest{result: passed}`；成功 `done→closed`；非主管 403 `forbidden`；回應 `RepairVerificationOutcome`。 | FR-021、FR-024、FR-040；`verifyRepairTicket` 契約。 |
| ADD | `be/工務單/dsl.md` -> When `"{主管}" 以原因 "{原因}" 判定 "{單號}" 驗收不通過` | `result=failed`；寫 `last_verify_failed_*`、清空完修三欄、一筆 `repair_verification_failed`；在班回 `accepted` 並推播原處理人，接不了回 `pool`、`pool_source`=verification_return、不推播。 | FR-025、FR-026、FR-027。 |
| ADD | `be/工務單/dsl.md` -> When `"{主管}" 判定 "{單號}" 驗收不通過但沒有寫原因` | 422 `verification_reason_required`，單仍 `done`。 | FR-025。 |
| MODIFY | `be/工務單/dsl.md` -> Given `"{主管}" 已先驗收通過 "{單號}"` | 落地改為 `closed`。 | FR-024。 |
| ADD | `be/工務單/dsl.md` -> Given `"{主管}" 已先以原因 "{原因}" 判定 "{單號}" 驗收不通過` | 先送出不通過的撞單 Arrange。 | FR-036；acceptance `主管動作先到先處理且離線一律擋下.feature:4-14`。 |
| ADD | `be/工務單/dsl.md` -> Given `"{工務}" 今天的班已經結束`、`"{單號}" 已由 "{工務}" 以不需處理結案` | 班已結束以 `Clock` 落在飯店所有班別時段之外表達（工務沒有排房班次）；不需處理結案的 Arrange。 | FR-025；FR-019。 |
| MODIFY | `be/工務單/dsl.md` -> When `"{員工}" 撤回 "{單號}"` | 只剩 pool 能撤回。 | FR-017。 |
| MODIFY | `be/工務單/dsl.md` -> When `"{工務}" 附 {張數} 張完修照片、處理說明 "{說明}" 完成 "{單號}"` | 明帶 `resolutionType: fixed`、照片是這一次上傳的；寫 `done_at`、`resolution_type`；退回重修後再完修也推播報修人。 | FR-018、FR-020；使用者裁決「每次完修都推播報修人」。 |
| MODIFY | `be/工務單/dsl.md` -> When `"{工務}" 不附完修照片完成 "{單號}"` → `"{工務}" 以完修結果 "{結果}" 完成 "{單號}"，處理說明 "{說明}"，不附照片` | 句型改為四選一：不需處理直接 `closed`、不建追蹤單、不改房況、推播報修人；其他三種 422 `completion_photo_required`。 | FR-018、FR-019。 |
| DELETE | `be/工務單/dsl.md` -> Then `"{單號}" 的退回原因為 "{原因}"`、`"{單號}" 開始等主管的時間為現在` | `reject_reason`、`reviewRequestedAt`、待審核排序鍵收回。 | FR-017。 |
| MODIFY | `be/工務單/dsl.md` -> Then `"{單號}" 已由 "{主管}" 驗收`、`"{單號}" 的驗收人只有 "{主管}" 一人，驗收只記下一次`、`"{單號}" 尚未驗收` | 驗收後狀態為 `closed`；撞單時不得有 `repair_verification_failed`；尚未驗收另查 state 不是 closed。 | FR-024、FR-036。 |
| ADD | `be/工務單/dsl.md` -> Then `"{單號}" 的驗收不通過只記下一次`、`這次驗收的結果為 "{結果}"，去處為 "{去處}"`、`"{單號}" 最近一次驗收不通過的原因為 "{原因}"，判定人為 "{主管}"` | 對 `RepairVerificationOutcome.result`／`destination`／`assigneeName`、`last_verify_failed_*`；先到的不通過只寫一筆事件與至多一封推播。 | FR-025、FR-026、FR-028、FR-036。 |
| ADD | `be/工務單/dsl.md` -> Then `"{單號}" 的完修結果為 "{結果}"`、`系統沒有為 "{單號}" 建立追蹤單`、`"{房號}" 的房況沒有改變` | 不需處理結案的三個斷言。 | FR-019。 |
| ADD | `be/工務單/dsl.md` -> Then `工務待認領中 "{單號}" 帶驗收退回原因 "{原因}"`、`主管待辦的任何一類都不包含 "{單號}"` | 對 `PoolItem.verificationReturnReason`；開單不經主管的跨視角斷言。 | ui-plan 介面 6；FR-016。 |
| MODIFY | `be/工務單/dsl.md` -> Then `工務待認領包含 "{單號}"`、`"{房號}" 未結案的 "{類別}" 工務單只有 {張數} 張` | 拿掉「沒有主管審核事件」；未結案排除 `closed`。 | FR-017；重複單過濾加 `closed`。 |
| MODIFY | `be/工務單/dsl.md` -> Then `"{工務}" 的工務視角今日進度為：` | 今天已完修改為今天完修且現在 `done` 或 `closed`；呈現結果改對 `getMyToday.engineeringSummary`。 | `completedToday` 契約；結案後計數不往下掉。 |
| MODIFY | `be/工務單/主管審核與驗收的狀態守衛.feature` → `be/工務單/主管驗收的狀態守衛.feature` | 檔名改名。刪除 Rule：退回原因空白、撤回後核准、已核准再核准。改寫：驗收後單結案、時間為當下；兩位主管先後通過只接受先到。新增 Rule：工務主管可驗收、工務主管可驗自己完修的單、員工不能驗收（報修人與處理人兩例）、先到不通過時後到通過被拒且單維持退回、先到通過時後到不通過被拒、後到被拒不另推播、以不需處理結案的單不能驗收、不通過原因空白被拒。維修中不能驗收、無法修復不改房況照舊。 | acceptance `主管動作先到先處理且離線一律擋下.feature:4-14`；`從待驗收清單驗收完修工務單.feature:62-101`；FR-021、FR-025、FR-040。 |
| ADD | `be/工務單/驗收不通過與重修.feature` | 9 Rule：在班回原處理人名下待開始、記下最近一次原因與判定人、推播原處理人（標題、內容、去處）、下班回工務待認領、回工務待認領不推播、工務待認領帶驗收退回原因、重修再完修回到已完修等驗收、重修再完修推播報修人、重修再完修仍要附這一次照片。 | acceptance `從待驗收清單驗收完修工務單.feature:29-60`；FR-025～FR-027；使用者裁決「每次完修都推播報修人」。 |
| MODIFY | `be/工務單/開立工務單與急件.feature` | 刪除 7 條審核 Rule（一般件進等主管審核、核准才進池、退回須附原因、退回推播報修人、核准不推播、補資料再送出、重送時間排序）。Rule「房務報修的急件直接進工務待認領」改為不分一般件或急件（兩例）；新增 Rule「報修送出後不會出現在任何主管的待辦」；原本附在一般件 Rule 裡的「系統建立工務單並記下報修人」獨立成 Rule。急件判定、排序、推播、降級不動。 | FR-016、FR-017；acceptance `報修直接進工務待認領且不需處理直接結案.feature:4-22`。 |
| DELETE | `be/工務單/重複報修補照片與撤回.feature` -> Rule「等主管審核時，報修人也可以撤回工務單」 | 狀態不存在。 | spec 影響表 DELETE 列。 |
| MODIFY | `be/工務單/完修與回到原任務.feature` | Rule「工務判斷不需維修時，同樣以完修結案並在說明註明」改為「不附照片也能完成，單直接結案」；新增 Rule：保留處理說明、不建追蹤單、不改房況、同樣推播報修人、原任務停住原因同樣改為工務已修好、結案後原任務可以恢復且工時接續。Rule「主管驗收通過後記錄驗收人並結案」的 Then 改為斷言已結案。 | FR-019；acceptance `報修直接進工務待認領且不需處理直接結案.feature:30-45`；acceptance 審查小修「FR-019 原任務可以恢復」。 |
| MODIFY | `be/工務單/照片完整性與冪等送出.feature` -> Rule「工務完成時必須附完修照片」 | 改為場景大綱：已修好、暫時處理、無法修復不附照片被拒（`completion_photo_required`）；不需處理的例外在完修 feature。 | FR-018。 |
| ADD | `be/工務單/工務視角維修與開單.feature` -> Rule「工務開單時沒有指定處理人，單直接進工務待認領，不經主管」 | 工務自開單、未指定處理人的去向。 | acceptance 審查小修「故事 4 AC3」；FR-016。 |
| MODIFY | `be/查房/dsl.md` -> When `"{主管}" 判定 "{任務}" 查房如下：` | 403 條件加工務主管。 | FR-007。 |
| ADD | `be/查房/逐項判定與退回去向.feature` -> Rule「工務主管不能送出查房，即使單停在待查房」、Rule「工務主管的查房送出被拒時，清房者沒有收到任何推播」 | 403 `forbidden`、單仍待查房、小美沒有推播。 | acceptance `從待查房清單當場查房.feature:71-79`。 |
| MODIFY | `be/推播/dsl.md` -> 模組說明的推播歸屬清單 | 「主管退回報修單」改為「主管驗收不通過退回原處理人」，完修推播寫明四種結果與重修後再完修，仍歸 `工務單` 模組。 | FR-027；沿用推播在觸發它的模組驗證的分工，所以 spec 影響表說的 `員工推播事件.feature` 不加場景。 |
| NOOP | `be/推播/員工推播事件.feature`、`推播送達手機.feature`、`通知中心.feature` | 不動。 | 驗收不通過推播在 `be/工務單/驗收不通過與重修.feature` 驗；查房推播不變。 |
| NOOP | `be/查房/免查房鎖定與結案.feature`、`免查抽查與合格率.feature`；`be/帳號與登入/*`、`be/今日進度/*`、`be/待認領/*`、`be/任務執行/*`、`be/房間交辦/*`、`be/查一下/*`、`be/班末掃描/*` | 不動。 | 行為不受本輪裁決影響。 |

### iOS App

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| MODIFY | `ios/dsl.md` -> Given `飯店有以下員工：`、When `"{員工}" 在 App 打開首頁` | 角色說明補工務主管；主管首頁載入條件改為依主管種類的格子與各自的名下單來源。 | FR-001、FR-013。 |
| MODIFY | `ios/dsl.md` -> Then `"{任務}" 在 App 顯示為 "{狀態}"` | 寫明主管視角看哪個畫面：工務單看主管視角工務單詳情的狀態列；房務任務看查房結果畫面、查房畫面，或到待查房清單確認在不在清單上。員工照舊看任務卡、任務頁或工務單詳情。 | /implement 回交事項；主管沒有別人那張單的任務頁。 |
| MODIFY | `ios/dsl.md` -> Then `這次操作沒有留在手機上等補送`、`後端已對 "{員工}" 送出推播 "{標題}"` | 主管動作清單改為查房送出、驗收通過、驗收不通過。 | FR-037。 |
| MODIFY | `ios/dsl.md` -> Then `畫面回到 "{清單}"，"{單}" 不在清單上`、`畫面顯示 "這張單已被處理"，並回到 "{清單}"，"{單}" 不在清單上` | `清單` 值刪待審核清單；用途改為按通過、查房結果畫面與驗收結果卡回清單；衝突對話框由驗收通過、不通過與查房送出共用，不通過面板一起收起。 | FR-035、FR-036。 |
| MODIFY | `ios/主管工作台/dsl.md` -> 模組說明 | 三格三類改為房務主管兩格、工務主管一格寬磚與兩類清單；主管在工務單詳情的動作改為驗收通過與不通過；查房只開放房務主管。 | ui-plan 變更清單 `主管工作台/dsl.md` 第 5 行。 |
| MODIFY | `ios/主管工作台/dsl.md` -> Given `"{主管}" 已在 App 打開主管首頁`、`"{主管}" 已在 App 打開 "{清單}"`、When `"{主管}" 在主管首頁點 "{格}"` | `清單`、`格` 值刪待審核，accessibility id 刪 `review`。 | ui-plan 第 14、27 行。 |
| MODIFY | `ios/主管工作台/dsl.md` -> Then `三格依序顯示：` → `首頁的格子依序顯示：` | 句型改名，格子必須剛好是表格列出的這幾格。 | ui-plan 第 38 行。 |
| ADD | `ios/主管工作台/dsl.md` -> Then `首頁只有待驗收一格，數字為 {數字}`、`首頁沒有待查房格，也沒有任何進待查房清單的入口`、`「我名下的單」列出 "{單號}"，卡片與工務首頁的工務單卡一致`、`App 開啟 "{單號}" 的工務單詳情，底部動作與工務員工相同`；When `"{主管}" 在「我名下的單」點 "{單號}"` | 工務主管首頁、查房入口、名下工務單卡與點開後的動作。 | ui-plan 介面 2、3；acceptance `主管登入後進自己的首頁.feature:76-80、142-148`。 |
| MODIFY | `ios/主管工作台/dsl.md` -> Then `"{格}" 格顯示 {數字}`、`清單說明目前沒有等他處理的單，畫面不是空白`、`「我名下的單」依序列出 …`、`「我名下的單」說明目前沒有名下的單`、`主管首頁沒有問候列與今日進度` | `格` 值刪待審核；空狀態刪待審核那句；名下的單區分房務主管與工務主管的資料來源；沒有問候列也涵蓋工務首頁的問候、進度與急件保養區。 | ui-plan 第 39、45-51 行。 |
| MODIFY | `ios/主管工作台/dsl.md` -> Then `每一列都看得到工務單號` → `每一列都看得到工務單號與完修結果`；`"{單號}" 那一列標示 "{標籤}"` | 待驗收每列加完修結果；標籤值加已修好、重修後再驗（`wasVerificationFailed`）。 | FR-034；ui-plan 第 45、46 行。 |
| MODIFY | `ios/主管工作台/dsl.md` -> Then `首頁沒有待查房、待審核、待驗收三格` → `首頁沒有主管的格子` | 句型改名。 | ui-plan 第 51 行。 |
| MODIFY | `ios/主管工作台/主管首頁.feature` | 背景加陳主管；工務單表刪 W-0201、W-0109 改已結案、加 W-0114；格子改 2、3。新增 Rule：工務主管只有待驗收一格、工務主管名下的單用工務單卡、工務主管點名下的單開工務單詳情且動作與工務員工一致；0 格、四分頁、沒有問候列各加陳主管一例；員工首頁沒有主管格子加阿強一例；「三格」措辭全改「格子」。 | acceptance `主管登入後進自己的首頁.feature:34-155`；ui-plan 主管首頁列。 |
| MODIFY | `ios/主管工作台/三類清單.feature` → `ios/主管工作台/主管清單.feature` | 檔名改名（對齊 ui-plan 畫面名「主管清單」）。刪除 Rule：待審核不含急件、補資料再送出排序。改寫：待驗收不含已結案與等工務接單；每一列另外看得到單號與完修結果。新增 Rule：以不需處理結案的單不在待驗收、工務主管與房務主管待驗收相同、重修再完修重新出現並標重修後再驗。其餘照舊。 | acceptance `主管登入後進自己的首頁.feature:71-115`；`從待驗收清單驗收完修工務單.feature:46-49`；ui-plan 三類清單列。 |
| ADD | `ios/主管工作台/查房.feature` -> Rule「查房只開放給房務主管，工務主管的首頁沒有待查房格，也沒有任何進待查房清單的入口」 | 背景加陳主管（帶 `角色` 欄）。 | acceptance `從待查房清單當場查房.feature:71-76`；ui-plan 查房列。 |
| MODIFY | `ios/工務單/dsl.md` -> 模組說明 | 主管審核與驗收段改為報修、驗收與三種視角：沒有審核、完修四選一、主管動作通過與不通過、結果卡、三種視角對各狀態的呈現表。 | ui-plan 介面 4、5；spec 影響表 `ios/工務單/dsl.md` 列。 |
| MODIFY | `ios/工務單/dsl.md` -> Given `"{員工}" 已在 App 打開 "{單號}" 的工務單詳情`、`"{單號}" 剛被 "{主管}" 驗收，"{員工}" 的畫面還沒更新` | 主管入口改為待驗收清單；驗收撞單 Arrange 改帶 `result: passed`、後端已結案。 | ui-plan 第 27、28 行。 |
| ADD | `ios/工務單/dsl.md` -> Given `"{單號}" 剛被 "{主管}" 以原因 "{原因}" 判定驗收不通過，"{員工}" 的畫面還沒更新`、`"{工務}" 今天的班已經結束`、`"{主管}" 剛送出 "{單號}" 的驗收不通過，結果卡停在畫面上` | 先不通過的撞單、處理人下班、結果卡回清單的 Arrange。 | FR-025、FR-026、FR-036。 |
| DELETE | `ios/工務單/dsl.md` -> Given `"{單號}" 剛被 "{員工}" 撤回，"{主管}" 的畫面還沒更新`；When `"{主管}" 在工務單詳情按核准`、`"{主管}" 在工務單詳情按退回，寫下原因 "{原因}" 並送出`、`"{主管}" 在工務單詳情打開退回面板，不寫原因`；Then `工務單詳情顯示退回原因 "{原因}"`、`工務單詳情顯示「補資料再送出」按鈕`、`工務單詳情沒有驗收不通過、退回與備註欄`、`退回面板的送出鈕為 "{按鈕狀態}"`、`後端沒有收到這次退回`、`工務單詳情顯示撤回按鈕` | 審核相關句型全部收回；撤回按鈕那句只服務已刪的等主管審核撤回 Rule。 | FR-017；ui-plan 第 29、47-49、77、78、92、94 行。 |
| MODIFY | `ios/工務單/dsl.md` -> When `"{主管}" 在工務單詳情按驗收` → `"{主管}" 在工務單詳情按通過` | 句型改名；id 改 `managerTicket.pass-button`；成功落地為已結案。 | ui-plan 第 50 行。 |
| ADD | `ios/工務單/dsl.md` -> When `"{主管}" 在工務單詳情按不通過，寫下原因 "{原因}" 並送出`、`"{主管}" 在工務單詳情打開不通過面板，不寫原因`、`"{主管}" 從驗收結果卡回到待驗收清單` | 不通過面板與結果卡。 | FR-025、FR-026。 |
| MODIFY | `ios/工務單/dsl.md` -> When `"{工務}" 在工務單詳情完修，回報結果 "{結果}"，附 {張數} 張照片`、`"{工務}" 在工務單詳情打開完修回報，選結果 "{結果}"，附 {張數} 張照片`；Then `完修回報的送出鈕為 "{按鈕狀態}"` | 結果值加不需處理、張數可為 0；送出鈕可按條件依結果區分。 | FR-018、FR-019；ui-plan 介面 5。 |
| MODIFY | `ios/工務單/dsl.md` -> Then `工務單詳情顯示狀態摘要卡 "{摘要卡文案}"` | 文案清單改為 ui-plan 三種視角表；必查對 `state`、`lastVerifyFailedAt`、`verifiedBy`、`resolutionType`，拿掉 `reviewRequestedAt`。 | FR-028；ui-plan 介面 4 三視角表。 |
| MODIFY | `ios/工務單/dsl.md` -> Then `工務單詳情的主管動作為 "{動作}"`、`工務單詳情的步驟條依序為 …`、`"{單號}" 已由 "{主管}" 驗收，驗收時間為現在` | 動作值改為「通過、不通過」與「無」；步驟條對照改為已結案才四步全完成；驗收後狀態為已結案。 | ui-plan 第 88、89、95 行。 |
| ADD | `ios/工務單/dsl.md` -> Then `工務單詳情的步驟條第四步「驗收」為 "{完成狀態}"`、`工務單詳情顯示最近一次不通過的原因 "{原因}" 與判定人 "{主管}"`、`工務單詳情沒有任何主管審核的狀態或動作`、`工務單詳情沒有通過或不通過的動作`、`工務待認領中 "{單號}" 標示驗收退回，原因 "{原因}"`、`不通過面板的送出鈕為 "{按鈕狀態}"`、`後端沒有收到這次驗收不通過`、`驗收結果卡說明 "{單號}" 退回重修，回到 "{處理人}" 名下`、`驗收結果卡說明 "{單號}" 退回重修，"{處理人}" 已下班，回到工務待認領` | 驗收、退回重修與 FR-017 的畫面斷言。 | FR-017、FR-025、FR-026、FR-028；ui-plan 介面 4、6。 |
| MODIFY | `ios/工務單/主管視角.feature` | 背景加陳主管。刪除 Rule：核准、退回原因空白、退回、退回推播、撤回時按核准。改寫：報修內容改從待驗收清單打開 W-0108；主管動作只在已完修出現（例子改為已完修、待認領、維修中、退回重修中、已結案兩種）；待驗收詳情看得到完修回報；按通過拆成「單結案記下驗收人」與「回到清單」兩條；無法修復改用通過；已被處理涵蓋三種撞單。新增 Rule：主管視角沒有核准退回補資料、工務主管驗收自己完修的單、不通過原因空白不能送出、結果卡回到原處理人、不通過推播原處理人、原處理人下班回工務待認領、結果卡回清單、已結案詳情看得到完修回報與驗收人、退回重修中看得到原因與判定人。 | acceptance `從待驗收清單驗收完修工務單.feature:4-111`；`主管動作先到先處理且離線一律擋下.feature:4-14、52-70`；acceptance 審查小修「FR-017」。 |
| MODIFY | `ios/工務單/工務單房務端生命週期.feature` | 摘要卡場景大綱刪等主管審核，已完修改「已修好 · 等主管驗收」，加退回重修中、已結案（驗收人）、已結案 · 不需處理。刪除 Rule：被主管退回補資料、等主管審核時可撤回。新增 Rule：報修送出後沒有任何主管審核的狀態或動作、步驟條停在待開始、報修人沒有通過或不通過的動作、報修人看得到最近一次不通過的原因與判定人。 | acceptance `報修直接進工務待認領且不需處理直接結案.feature:17-19`；`從待驗收清單驗收完修工務單.feature:75-81`；FR-028。 |
| MODIFY | `ios/工務單/工務視角.feature` | Rule「完修回報沒附照片時送出鈕禁用」改為已修好、暫時處理、無法修復三例的場景大綱；「主管驗收通過後顯示驗收人」改為已結案 · 王主管驗收。新增 Rule：不需處理不附照片可以送出、不需處理送出後顯示已結案、第四步驗收在結案時才完成（四例）、處理人沒有通過或不通過的動作、退回重修時狀態列寫明判定人且步驟條回到待開始、退回重修時看得到原因與判定人、退回工務待認領的單卡標示驗收退回。 | acceptance `報修直接進工務待認領且不需處理直接結案.feature:30-59`；`從待驗收清單驗收完修工務單.feature:25-27、42-45、82-84`；ui-plan 介面 5、6。 |
| MODIFY | `ios/推播/點開推播的去處.feature` | 刪除場景「阿美點開主管未核准的推播」；Rule「修繕任務推播開工務單詳情」加「阿強點開驗收不通過推播」；Rule「去處為首頁依角色進首頁」加陳主管進主管首頁一例；背景加阿強、陳主管。 | acceptance `從待驗收清單驗收完修工務單.feature:42-43`；`主管登入後進自己的首頁.feature:18-19`。 |
| MODIFY | `ios/推播/dsl.md` -> Then `App 停在 "{畫面}"` | 主管首頁的必查改為依主管種類的格子。 | FR-001。 |
| NOOP | `ios/推播/通知中心.feature` | 不動。 | 目前沒有通知分類的場景，ui-plan 列為由本步判斷；驗收不通過的通知歸新單一類屬畫面樣式，不另建 Rule。 |
| MODIFY | `ios/帳號與登入/dsl.md` -> Then `"{員工}" 登入成功，進入 "{首頁}"` | 主管首頁必查改為依主管種類：房務主管兩格、工務主管一格。 | ui-plan 第 40 行；spec 影響表。 |
| MODIFY | `ios/帳號與登入/登入與登出守衛.feature` | 登入與強制改密兩個場景大綱各加陳主管（工務、主管）一列；強制改密大綱補 `角色` 欄。 | acceptance `主管登入後進自己的首頁.feature:4-20`。 |
| MODIFY | `ios/系統層/離線模式與連線異常.feature` | Rule 標題被擋的主管動作改為查房送出、驗收通過、驗收不通過；三個主管場景大綱的工務單表刪 W-0201、加 W-0110，例子改為送出查房結果、驗收通過、驗收不通過；恢復連線後的例子改 301 待查房、W-0108 已完修、W-0110 已完修。 | acceptance `主管動作先到先處理且離線一律擋下.feature:24-42`；FR-037。 |
| MODIFY | `ios/系統層/dsl.md` -> When `"{員工}" 在 App 離線時嘗試 "{動作}"` | 主管動作值改為送出查房結果、驗收通過、驗收不通過；不呼叫的端點只剩 `createInspections`、`verifyRepairTicket`；不通過面板的原因保留。 | ui-plan 第 17 行。 |
| MODIFY | `ios/待認領/待認領列表.feature` | Rule「主管第一次打開待認領時顯示房務的池」加陳主管一例；Rule「主管看到的單卡沒有搶單鈕」加陳主管切到工務一例。 | acceptance `待認領切換房務與工務只看不搶.feature:26-32`；FR-031。 |
| MODIFY | `ios/待認領/dsl.md` -> Then `"{任務}" 的單卡沒有搶單鈕` | 說明改為主管不論有沒有部門（含工務主管）都只看不搶。 | FR-031。 |
| NOOP | `ios/今日進度/*`、`ios/任務執行/*`、`ios/房間交辦/*`、`ios/查一下/*`、`ios/工務單/報修照片與送出.feature`、`開單提示.feature` | 不動。 | 主管做名下的單沿用既有任務頁與完成畫面；報修表單本身不變。 |

### DSL row 搬移（語意不變）

本輪沒有 DSL row 只搬移權威位置。以下 row 句型改名但權威位置不變，已列在上表 MODIFY：`be/主管工作台/dsl.md` 的 `主管待辦的格子數字為：`；`ios/主管工作台/dsl.md` 的 `首頁的格子依序顯示：`、`每一列都看得到工務單號與完修結果`、`首頁沒有主管的格子`；`ios/工務單/dsl.md` 的 `"{主管}" 在工務單詳情按通過`；`be/工務單/dsl.md` 的 `"{工務}" 以完修結果 "{結果}" 完成 "{單號}"，處理說明 "{說明}"，不附照片`。

### /implement 回饋修正

| 動作 | 位置 | 改了什麼 | 依據 |
|---|---|---|---|
| MODIFY | `specs/truth/features/ios-app/待認領/待認領列表.feature` -> Rule「主管第一次在這台手機打開待認領時顯示房務的池」第二個 Then | 預期順序改為 317、215、313、314、418：主管沒有負責樓層，不適用同樓層優先，依優先通知與房號排序。 | `/implement` 4L 實跑發現劇本照抄三樓員工的順序，與契約 `員工工作台.yaml` 待認領排序規則不一致；使用者授權指揮官依建議修正（2026-11-11）。 |
| MODIFY | `specs/truth/features/ios-app/dsl.md` -> Then `"{任務}" 在 App 顯示為 "{狀態}"` | 寫明主管視角看哪個畫面：工務單看工務單詳情狀態列；房務任務看查房結果畫面、查房畫面，或到待查房清單確認在不在清單上。 | `/implement` 回交：主管沒有別人那張單的任務頁，原句只寫任務卡、任務頁或工務單詳情，step definition 無從落地（2026-11-11）。 |

### 刻意不承接
| MODIFY | `specs/truth/features/ios-app/工務單/dsl.md` -> `工務單詳情的步驟條依序為 "{步驟清單}"，目前在 "{目前步驟}"` 必查 | 「待開始」的狀態對照補上待認領。 | `/implement` Phase 3 發現 `工務單房務端生命週期.feature` 的待認領單停在待開始，DSL 對照漏列；指揮官依使用者授權修正（2026-11-11）。 |
| MODIFY | `specs/truth/features/backend/工務單/dsl.md` -> `"{單號}" 尚未驗收` 必查 | 「tasks.state 不是 closed」改為「若為 closed，必須是不需處理結案」。 | `/implement` 4C 發現 `主管驗收的狀態守衛.feature:121` 對不需處理結案的 W-0114 斷言尚未驗收，與舊必查矛盾；指揮官依使用者授權修正（2026-11-12）。 |
| MODIFY | `specs/truth/features/ios-app/主管工作台/dsl.md` -> `首頁只有待驗收一格，數字為 {數字}` 必查 | 工務主管首頁的待驗收寬磚拿掉副文「與房務主管看同一份」，只留類別名與數字。 | 使用者 2026-11-12 看雛形後要求拿掉。 |

- ui-plan 介面 3 的「查房送出收到 403 時彈沒有權限並回首頁」：工務主管在 App 沒有任何進查房畫面的入口，這個防禦分支無法用 App 操作安排；403 本身由 `be/查房/逐項判定與退回去向.feature` 承接，入口不存在由 `ios/主管工作台/查房.feature` 承接。
- acceptance `主管登入後進自己的首頁.feature:136-140`、`主管動作先到先處理且離線一律擋下.feature:44-50`（主管完成自己名下的單）：沿用 `ios/任務執行/*` 與 `ios/系統層` 既有的完成與離線完成 Rule，不另複製。
- acceptance `報修直接進工務待認領且不需處理直接結案.feature:20-22` 的急件推播：由 `be/工務單/開立工務單與急件.feature` 既有 Rule「急件推播給所有工務與主管」承接。
- acceptance 的跨視角後設斷言（「與房務員工一致」「通知與我的內容一致」）縮成可機械驗的 Then，沿用上一輪的處理。
- acceptance 用語「退房打掃」在 truth 統一寫既有 DSL 的「退房清潔」。
