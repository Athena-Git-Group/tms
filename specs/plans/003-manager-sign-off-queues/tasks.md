# Tasks: 003-manager-sign-off-queues（主管在 App 上處理待查房、待審核、待驗收）

**Plan Package**: `specs/plans/003-manager-sign-off-queues`
**Core Inputs**: `spec.md`, `plan.md`, `truth-delta.md`, `ui/ui-plan.md`, `ui/*.html`, `ui/manager.css`, `ui/manager.js`, `specs/truth/techstack.md`, `specs/truth/contracts/**`, `specs/truth/data/data-model.dbml`, `specs/truth/features/backend/**`, `specs/truth/features/ios-app/**`

本輪沒有 `research.md`（`/technical-research` 未執行，本輪不引入新技術）。
本檔承接 `truth-delta.md` 三節：`/api-plan`（新 api group 主管工作台、`createInspections` 請求形狀收斂、`RepairTicket` 補欄位、`repair_rejected` 推播事件）、`/data-plan`（含 Wave 3 的 `repair_tickets.resolution_type`、`notifications.event` 加值、三張表的 Note）、`/dsl-refine`（backend 新模組 `主管工作台` 與三支新 feature、ios-app 新模組 `主管工作台` 與六支新增或修改的 feature、兩個介面根共用 DSL 的 ADD 與 MODIFY、三條 row 搬移）。

## Task Binding Contract

- 每個**開發任務**都必須對應 `truth-delta.md` 的 ADD / MODIFY / DELETE / NOOP 語意。
- 本輪沒有新增技術，**省略 Setup phase**。待認領記住部門用既有的 `SharedPreferences`（`core/firebase_push_messaging.dart` 已在用），不是新技術。
- Phase 2 `Foundational` 只建立後續實作與測試共用的元件、入口、fixture、helper 與落點骨架；每則寫「只做／不做」。
- Phase 3 `Test Alignment & Implementation` 在寫產品碼之前，把本輪所有受影響 DSL 的自動化測試對齊最新版 truth。
- Truth 參照使用 `specs/truth/**`；plan 參照才使用本 plan package 內相對路徑。
- Feature `Shared Must Read` 除 feature、同模組 DSL、對應 `truth-delta` 列外，還必須列入回追到的 planner 產物。
- 後端 Test Scope 以 `tms/src/test/java/com/athena/tms/bdd/RunTruthFeaturesTest.java` 跑（JDK 25、Testcontainers、Docker Desktop）；App Test Scope 以 `app/bdd/src/test/java/com/athena/app/bdd/RunModuleFeatureTest.java` 跑（Appium、iOS 模擬器）。寫法同 002：Test Scope 只列 feature 路徑。

## Scope Note（停止範圍、判斷與回交）

1. **沒有回交 `/dsl-refine` 的範圍。** 本輪 ADD 與 MODIFY 的每一句都在唯一的 `dsl.md` 裡有定義；六支 acceptance 旅程都已被 truth feature 承接（含 `truth-delta.md`「刻意不承接」三條的處理）。
2. **`contracts` 與 `data` 不加 quality gate。** 回追到的 `specs/truth/contracts/**` 與 `specs/truth/data/data-model.dbml` 都在 `specs/truth/**`，依 `Feature約束從plan介面回追` Rule 3 以 Feature 的 Test Scope 全綠當閘門，不另加題。但有三處契約 MODIFY 沒有後端 feature 直接驗（`RepairTicket` 的 `origin`、`doneAt`、`reviewRequestedAt`、`resolutionType`；`RepairTicketSummary` 的 `roomNo` 可為 null 與 `isPublicArea`；`getMyToday` 主管呼叫時的語意），它們由 App 端 Feature 的 Test Scope 間接驗，並寫進 Phase 4D 的 `Boundary` 明列為該 phase 必做。只有 `ui/` 家族是 plan-side 產物，在最後消費者之後加一題 gate（Phase 4N）。
3. **`features/acceptance/` 六支旅程不列入 `Shared Must Read`。** 它們是 `/spec-by-example` 的 plan-side 產物，不是 `/ui-plan`、`/api-plan`、`/data-plan` 的產物，回追順序不經過它們；truth feature 已逐條承接，Feature phase 讀 truth 就夠。
4. **`truth-delta.md` `/dsl-refine`「順手補的既有缺口」兩句不產生任務。** `通知中心沒有「全部標為已讀」`（`app/bdd/.../steps/push/PushThenSteps.java:225`）與 `"{任務}" 的面板換成工務單表單，原任務還沒有被中斷`（`.../steps/taskexecution/TaskExecutionThenSteps.java:1273`）的 stepdef 本來就在，只是 `dsl.md` 缺 row；不屬 003，也沒有測試層或產品碼影響。
5. **`be/工務單/dsl.md` 三句 Then（`"{單號}" 的退回原因為 "{原因}"`、`"{單號}" 已由 "{主管}" 驗收`、`"{單號}" 尚未驗收`）的 MODIFY 不列 Phase 3。** 改動只是拿掉「待 /data-plan 補」註記、寫出實際欄位 `reject_reason`、`verified_by`、`verified_at`；既有 stepdef（`tms/.../steps/repair/RepairThenSteps.java:408、425、455`）本來就查這些欄位，語意沒變且 stepdef 已在。同理 `be/工務單/dsl.md` 裁決段 L13、`be/dsl.md` 狀態用語對照兩句、`be/推播/dsl.md` 模組說明、`ios/工務單/dsl.md` 裁決段 L9 都是說明文字的 MODIFY，沒有句型可對齊。
6. **`ios/dsl.md` 的 `已有以下工務單：` 列 Phase 3 `[BDD-ALIGN]`。** 這一列的 ios 表沒有 MODIFY，但它的 Data Table 寫「同後端共用 DSL 的同名句型」，而後端那一列本輪 MODIFY 加了 `送出時間`、`處理結果`、`處理說明`、`完修照片張數`、`完修時間`、`驗收人`、`來源` 加 `定期保養`、`房號` 可為「公共區域」、`報修人` 改選填。ios 六支 feature 的工務單表已經在用這些欄（例如 `主管首頁.feature:29`、`主管視角.feature:91`），App 的 fixture（`app/bdd/.../steps/repair/RepairGivenSteps.java` 與 `BackendChannel.createRepairTicket`）不補就會在 Given 就掛掉。
7. **App fixture `BackendChannel.java:545` 不產生任務。** `truth-delta.md` 把它列為 `createInspections` 破壞性變更的牽動點，但現況已送新 body（`{taskId, items}`）並帶 `Idempotency-Key`（`BackendChannel.java:532-550` 的 `failInspection`），已與新契約一致；後端改完 record 之後它直接通。
8. **`HandoverGivenSteps.java:156-162` 不是 DSL 的 MODIFY，放 Foundational。** 它是 `"{房號}" 的退房清潔由 "{員工}" 清完，"{主管}" 判定 "{項目}" 不合格`（`HandoverGivenSteps.java:134`）的落地碼，句型語意沒變，只是送舊 body 會被收斂後的 record 拒收。T003 建立共用的查房送出 helper 時一併把它切過去；`CommonWhenSteps.java:85-91` 與 `PushManagerActionSteps.java:95-101` 兩處是 DSL MODIFY，留在 Phase 3。
9. **示範資料不是 truth，但 FR-034 與 `plan.md` 結構決策要求列為任務**，放 Foundational T002；它不是任何 Feature 的 Test Scope，驗法是 `scripts/seed-demo.sh` 跑完後王主管登入三格都有單。

## Phase 2: Foundational

**Goal**: 只建立 Phase 3 與 Phase 4 會用到的資料欄位、示範資料、測試共用 helper、後端查詢入口骨架與 App 的分流函式、外殼、畫面檔案與 API client 落點。不寫三條判定公式、不寫查房逐項判定、不寫任何主管動作的畫面行為。

- [ ] T001 新增 Flyway migration `V10`，加 `repair_tickets.resolution_type` 與 `notifications.event` 的 `repair_rejected`
  - Read:
    - `truth-delta.md` -> `/data-plan` Wave 3 ADD `repair_tickets.resolution_type`、`/data-plan` MODIFY `notifications.event` 的 CHECK 值集
    - `specs/truth/data/data-model.dbml` -> `Table repair_tickets` 的 `resolution_type` 欄位說明、`Table notifications` 的 `event` 十八值
    - `tms/src/main/resources/db/migration/` -> `V9__rule_idempotency_key.sql` 的命名與既有 CHECK 重建慣例（`DROP CONSTRAINT` 再 `ADD CONSTRAINT` 整個值集）
  - 只做：一支 migration 做兩件事。①`repair_tickets` 加 nullable `text` 欄 `resolution_type`，CHECK `fixed|temporary|cannot_fix`，不加索引；②重建 `notifications.event` 的 CHECK 為 dbml 現行十八值（加 `repair_rejected`）。
  - 不做：不回填既有列（seed 在 T002 自己補）；不寫 `resolveOnComplete` 的寫入（Phase 4D）；不動 `task_events.type`（已有 `repair_rejected`）。

- [ ] T002 調整 `scripts/seed-demo.sh`，讓王主管登入三格都有單
  - Read:
    - `spec.md` -> FR-034、「舉例說明用的人物與示範資料」
    - `truth-delta.md` -> `/data-plan` NOOP `scripts/seed-demo.sh 所需欄位` 那一列（三處調整各用哪些欄位、partial UNIQUE 不會撞、`repair_photos.storage_ref` 必填）、Wave 3 段末「seed 為 W-0108 與 W-0109 補 `resolution_type='fixed'`」
    - `scripts/seed-demo.sh` -> 第 193 行起的五群 `tasks`、第 323 行 `task_events`、第 335 行 `repair_tickets`、第 387 行 `notifications`、第 76 與 82 行的 `DELETE`
    - `tms/src/main/java/com/athena/tms/repair/RepairService.java` -> 完修照片 `storage_ref` 的既有存放慣例（App 要抓得到）
  - 只做：①加一列前一天完成的待查房打掃單（`type=cleaning`、`state=done`、`work_date` 與 `done_at` 為昨天、`assignee_id` 不是王主管）；②加一張等主管審核的一般件（`tasks` 一列 `type=repair`、`dept=engineering`、`source=staff`、`state=pending_review`、`urgent=false`、`assignee_id` 為空，`repair_tickets` 一列 `is_urgent=false`、`origin=reported`、`reporter_id` 為阿芳，`task_events` 一筆 `created` 當排序鍵）；③為 W-0108 加一列 `repair_photos`（`purpose=completion`、`status=uploaded`、`uploaded_by` 為阿強、`storage_ref` 指到 App 真的抓得到的物件）；④W-0108 與 W-0109 寫 `resolution_type='fixed'`；⑤把第 27 行與第 309 行「主管登入後進房務首頁」的過期註解改成主管首頁。
  - 不做：不改既有單的狀態與時間；不加 ui-plan 假資料裡 W-0110、W-0111 這類 FR-034 沒要求的單（要加也等 Phase 4 全綠後另議）；不碰 truth。

- [ ] T003 建立後端測試共用的查房送出 helper，並把交班 fixture 切過去
  - Read:
    - `truth-delta.md` -> `/api-plan` MODIFY `createInspections`、「牽動既有後端 stepdef 或 App 的破壞性變更」第一點
    - `specs/truth/contracts/components/schemas/查房.yaml` -> `CreateInspectionRequest`、`InspectionItemJudgement`
    - `tms/src/test/java/com/athena/tms/bdd/support/BddApiClient.java`、`tms/src/test/java/com/athena/tms/bdd/ScenarioWorld.java` -> 既有呼叫與保存回應的方式
    - `tms/src/test/java/com/athena/tms/bdd/steps/handover/HandoverGivenSteps.java:134、156-162` -> 送舊 body 的 fixture
  - 只做：在 `support/` 加一個 helper（例如 `InspectionFixtures.submit(world, inspectorName, taskId, items)`），送 `{taskId, items[{key, pass}]}` 並帶 `Idempotency-Key`，回傳整個 HTTP 回應讓呼叫端自己決定要不要斷言；把 `HandoverGivenSteps.java:156-162` 改成呼叫它（這一句語意沒變，見 Scope Note 第 8 條）。
  - 不做：不動 `CommonWhenSteps.java:85-91` 與 `PushManagerActionSteps.java:95-101`（Phase 3 的 ALIGN）；不寫任何 `InspectionOutcome` 的斷言。

- [ ] T004 建立後端主管待辦查詢的入口骨架
  - Read:
    - `truth-delta.md` -> `/api-plan` ADD `getManagerQueues`、ADD `components/schemas/主管工作台.yaml`
    - `specs/truth/contracts/groups/現場作業/主管工作台.yaml` -> `GET /me/manager-queues`
    - `specs/truth/contracts/components/schemas/主管工作台.yaml` -> `ManagerQueues`、`QueueItemBase`、三種 item
    - `tms/src/main/java/com/athena/tms/controller/TaskExecutionController.java` -> `getMyToday` 掛在 `/me/` 下的寫法與 `CurrentActor` 取法
  - 只做：新 controller 與 service（例如 `controller/ManagerQueueController` 與 `service/ManagerQueueService`），端點路徑與回應 DTO 欄位名照契約，先回 `counts` 三個 0 與三個空陣列。
  - 不做：不寫三條判定公式、排序、排除本人、`waitingSinceIsToday`、403（全部 Phase 4A／4B）。

- [ ] T005 把 `createInspections` 的請求 record 與回應 DTO 對齊契約形狀
  - Read:
    - `truth-delta.md` -> `/api-plan` MODIFY `createInspections`、ADD `InspectionOutcome`、DELETE `inspectionsRequest`
    - `specs/truth/contracts/components/schemas/查房.yaml` -> `CreateInspectionRequest`、`InspectionOutcome`
    - `tms/src/main/java/com/athena/tms/controller/InspectionApiController.java:39` -> `record CreateInspectionRequest`
    - `tms/src/main/java/com/athena/tms/service/InspectionService.java` -> `InspectionRequest` 內部型別、`processed_ops` 冪等機制的既有接法（參考 `RepairController` 的 `Idempotency-Key`）
  - 只做：record 改成只收 `taskId` 與 `items[{key, pass}]`，controller 方法簽章加 `@RequestHeader("Idempotency-Key")` 參數位置（先收不接）；定義 `InspectionOutcome` 回應 DTO 類別（`inspectionId`、`taskId`、`roomNo`、`result`、`destination`、`cleanerName`、`failedItems`、`createdAt`）。
  - 不做：不改 `validateCoverage` 的判定（缺項、多項、`currentItems`）、不加 409、不算 `destination`、不把 `Idempotency-Key` 接進 `processed_ops` 回放（全部 Phase 4C）；controller 回應內容先維持既有，DTO 只定義不接。

- [ ] T006 預留後端 `主管工作台` 與 `查房` 新句的 stepdef 落點
  - Read:
    - `specs/truth/features/backend/主管工作台/dsl.md`、`specs/truth/features/backend/查房/dsl.md` -> 本輪 ADD 的句
    - `tms/src/test/java/com/athena/tms/bdd/steps/inspection/` -> 既有查房 stepdef 的檔案分法（Given／When／Then 三檔加 `package-info.java`）
    - `tms/src/test/java/com/athena/tms/bdd/ScenarioWorld.java`
  - 只做：建 `steps/managerqueue/`（`ManagerQueueWhenSteps`、`ManagerQueueThenSteps`、`package-info.java`）空殼；`ScenarioWorld` 加保存「上一次主管待辦回應」與「上一次查房送出回應」的欄位；查房新句落在既有 `steps/inspection/` 三檔。
  - 不做：不寫任何句的實作語意（Phase 3）。

- [ ] T007 建立 App 的首頁分流純函式與主管外殼空殼
  - Read:
    - `ui/ui-plan.md` -> 介面 1 的判定順序表與「主管外殼」段
    - `app/mobile/lib/features/shell/main_shell.dart` -> `_MainShell(homeTab, poolTab)`、`HousekeepingShell`、`EngineeringShell`
    - `app/mobile/lib/core/api_client.dart` -> 登入 session 的 `role` 與 `dept`
  - 只做：`core/landing.dart` 的 `landingShellFor(session)`（先看 `role == 'manager'`，再看 `dept == 'engineering'`，其餘房務）；`main_shell.dart` 加 `ManagerShell`（`homeTab` 與 `poolTab` 先放空殼畫面，通知與我的兩分頁沿用）。
  - 不做：不把三個入口（`login_screen.dart:122`、`force_password_change_screen.dart:138`、`push_gateway.dart:194`）切過去（Phase 4F）；不寫主管首頁內容。

- [ ] T008 建立 `features/manager/` 畫面檔案骨架、`TicketView.manager` 與本輪 accessibility id 落點
  - Read:
    - `ui/ui-plan.md` -> 「前端實作切分建議」的頁面／容器切分與共用元件
    - `specs/truth/features/ios-app/主管工作台/dsl.md`、`specs/truth/features/ios-app/工務單/dsl.md`、`specs/truth/features/ios-app/待認領/dsl.md` -> 各句實作語意裡點名的 id（`manager.home.tile.{inspection|review|verify}`、`manager.queue.row.{房號}`、`inspect.item.{key}.pass-button`／`.fail-button`、`inspect.submit-button`、`inspect.result.back-button` 等）
    - `app/mobile/lib/features/ticket/ticket_screen.dart:21` -> `enum TicketView`
    - `app/mobile/lib/core/tms_semantics.dart` -> id 掛法
  - 只做：建 `features/manager/manager_home_screen.dart`、`manager_queue_screen.dart`、`inspection_screen.dart`、`inspection_result_screen.dart`、`manager_pool_screen.dart` 空殼（可空白）；`TicketView` 加 `manager` 值，`ticket_screen.dart` 的 switch 先讓它落到工務版；把三個 dsl 點名的 id 常數集中成一份清單掛在對應空殼元件上。
  - 不做：不接任何資料、不寫版面、不寫動作列（Phase 4G～4L）。

- [ ] T009 擴充 App `api_client.dart` 的主管待辦、查房送出與工務單欄位解析
  - Read:
    - `truth-delta.md` -> `/api-plan` ADD `getManagerQueues`、MODIFY `createInspections`、MODIFY `RepairTicket`、MODIFY `RepairTicketSummary`、MODIFY `Error` 錯誤碼清單、「牽動既有後端 stepdef 或 App 的破壞性變更」第三、四點
    - `specs/truth/contracts/components/schemas/主管工作台.yaml`、`查房.yaml`、`工務單.yaml`、`shared.yaml`
    - `app/mobile/lib/core/api_client.dart` -> `json['roomNo'] as String`（第 964、1083、1228、1336 行）與 `ApiException` 的 `code`／`context`
  - 只做：加 `getManagerQueues()` 與三種 item 模型（含 `waitingSinceIsToday`、`isPublicArea`、`roomNo` 可為 null）；加 `createInspection(taskId, items, idempotencyKey)` 回 `InspectionOutcome` 模型；`RepairTicket` 模型加 `origin`、`doneAt`、`reviewRequestedAt`、`resolutionType`，`description` 與 `reporterName` 可為 null；工務單相關解析的 `roomNo` 改成可為 null 並補 `isPublicArea`；`ApiException` 能讀到 `inspection_items_mismatch` 的 `context.currentItems`。
  - 不做：不接畫面、不寫導頁；不動打掃任務的 `roomNo` 解析（任務一定有房號）。

- [ ] T010 預留 App BDD 的 `steps/manager/` 落點與 `BackendChannel` 的主管用 fixture 殼
  - Read:
    - `specs/truth/features/ios-app/主管工作台/dsl.md` -> Given 句的 `怎麼做`（整批換查房項目、全部停用、新增一項、另一位主管查完）
    - `app/bdd/src/test/java/com/athena/app/bdd/BackendChannel.java` -> `createManagerLikeStaff`（第 403 行）、`failInspection`（第 532 行）、`createRepairTicket`（第 835 行）、`markRepairTicketVerified`（第 967 行）
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/pool/PoolContext.java` -> 模組 context 的寫法
  - 只做：建 `steps/manager/`（`ManagerGivenSteps`、`ManagerWhenSteps`、`ManagerThenSteps`、`ManagerContext`、`package-info.java`）空殼；`BackendChannel` 加方法殼：整批替換啟用的查房項目、全部停用、追加一項、以指定主管送出查房、把別人的打掃單寫成指定 `done_at`、工務單 fixture 收新欄位（`送出時間`、`處理結果`、`處理說明`、`完修照片張數`、`完修時間`、`驗收人`、`來源=定期保養`、公共區域）。
  - 不做：不寫任何句的斷言或操作（Phase 3）。

## Phase 3: Test Alignment & Implementation

**Goal**: 把本輪 Feature 用到的 DSL 自動化測試對齊最新版 truth；含 truth-delta 有改的句，以及本輪 Feature 用到、尚無 stepdef 的句。不寫產品行為。

**DSL 參照**:
- 每一句只屬於一個權威 `dsl.md`：同模組 `specs/truth/features/{介面}/{模組}/dsl.md`，或介面根 `specs/truth/features/{介面}/dsl.md`。不得掃其他模組。
- backend 的句分四處：`specs/truth/features/backend/主管工作台/dsl.md`（新模組）、`specs/truth/features/backend/查房/dsl.md`、`specs/truth/features/backend/工務單/dsl.md`、`specs/truth/features/backend/推播/dsl.md`；**另有七句在介面根** `specs/truth/features/backend/dsl.md`（五句 MODIFY、兩句 ADD），見下方「backend / 介面根共用 DSL」。
- ios-app 的句分六處：`specs/truth/features/ios-app/主管工作台/dsl.md`（新模組，38 句全部 ADD：Given 9、When 6、Then 23）、`specs/truth/features/ios-app/工務單/dsl.md`、`specs/truth/features/ios-app/待認領/dsl.md`、`specs/truth/features/ios-app/系統層/dsl.md`、`specs/truth/features/ios-app/推播/dsl.md`、`specs/truth/features/ios-app/帳號與登入/dsl.md`；**另有十三句在介面根** `specs/truth/features/ios-app/dsl.md`（八句 MODIFY 含兩句搬移、五句 ADD）。
- 三條搬移的 row（`這次操作沒有留在手機上等補送`、`後端沒有對 "{員工}" 送出任何推播` 上提到 `ios-app/dsl.md`；`"{員工}" 已在 App 登入，停在首頁` 下放到 `ios-app/系統層/dsl.md`）語意不變：前兩句既有 stepdef 在 `steps/taskexecution/`，只對齊精確參照與用途（`[BDD-ALIGN]`），不拆成移除再新增；第三句是本輪新建，`[BDD-RED]` 落在 `steps/system/`。
- 讀法：用 task title 的句型對到該檔那一列，以 `StepDef 實作語意`（ios-app 為 `App 實作語意（Appium）`）當作測試程式碼語意。Given／When 讀 `怎麼做`、`權威狀態落地`、`回寫`；Then 讀 `必查`。
- `truth-delta.md` 只告訴這句是 ADD / MODIFY / DELETE。語意以 `dsl.md` 那一列為準，不得用 feature 措辭或舊 stepdef 自行發明。

**Markers**:
- `[BDD-ALIGN]`：`MODIFY`。既有 stepdef 還在，但語意是舊 truth（Data Table 少欄、送舊 body、沒帶 `Idempotency-Key`、值集沒有主管的去處或畫面、首頁不分角色），或這句的唯一權威位置被搬移。依 `dsl.md` 該列改測試。
- `[BDD-RED]`：`ADD`，或本輪 Feature 用到、尚無 stepdef 的句。依 `dsl.md` 該列寫出 stepdef。完成時這句可被跑到，失敗只能是 assertion 或產品行為，不能是 undefined step。
- 本輪沒有 `DELETE` 的 DSL 句（唯一的 DELETE 是契約 schema `inspectionsRequest`，沒有句型綁它），不使用 `[BDD-REMOVE]`。
- 兩個 marker 都只動測試層，不寫產品碼。

**Shared Must Read**:
- `specs/truth/features/backend/dsl.md` -> `飯店有以下員工：`、`"{員工}" 名下有以下任務：`、`已有以下工務單：`、`"{員工}" 判定 "{任務}" 查房全部項目通過`、`"{員工}" 收到以下推播：`、`這次操作被拒絕，錯誤碼為 "{錯誤碼}"`、`"{房號}" 的房況為 "{房況}"`
- `specs/truth/features/backend/主管工作台/dsl.md` -> 本檔 When／Then 全部
- `specs/truth/features/backend/查房/dsl.md` -> 本輪 ADD 的四個 Given、三個 When、五個 Then
- `specs/truth/features/backend/工務單/dsl.md` -> 四個主管動作 When、本輪 ADD 的三個 Given／When 與三個 Then
- `specs/truth/features/backend/推播/dsl.md` -> `"{主管}" 查房 "{任務}"，判定 "{項目}" 不合格`
- `specs/truth/features/ios-app/dsl.md` -> 本檔 Given／When／Then 本輪 ADD 與 MODIFY 的十三句
- `specs/truth/features/ios-app/主管工作台/dsl.md` -> 本檔全部
- `specs/truth/features/ios-app/工務單/dsl.md` -> 本輪 ADD 的十四句、MODIFY 的兩句 Then
- `specs/truth/features/ios-app/待認領/dsl.md`、`specs/truth/features/ios-app/系統層/dsl.md`、`specs/truth/features/ios-app/推播/dsl.md`、`specs/truth/features/ios-app/帳號與登入/dsl.md` -> 本輪 ADD 與 MODIFY 的句
- `truth-delta.md` -> `/dsl-refine` 全表與「DSL row 搬移」表
- `tms/src/test/java/com/athena/tms/bdd/steps/`（`common/`、`inspection/`、`repair/`、`push/`、`managerqueue/`）、`tms/src/test/java/com/athena/tms/bdd/support/`
- `app/bdd/src/test/java/com/athena/app/bdd/steps/`（`common/`、`manager/`、`repair/`、`pool/`、`system/`、`push/`、`account/`、`taskexecution/`）、`app/bdd/src/test/java/com/athena/app/bdd/BackendChannel.java`

**Boundary**:
- 一條 DSL 一個 task。
- 只改該句的 stepdef / assertion / 直接依賴的 helper。
- 不寫產品碼。
- review 啟動 subagent；本輪所有 Test Scope 不得再有 undefined step，失敗只能是 assertion 或產品行為。有 issues 就修正再 review，直到沒有任何問題。通過前不解鎖 Phase 4。

**Parallel Hint**:
- backend（T011–T052）與 ios-app（T053–T131）落在不同 repo 目錄，兩邊可同時開工；T132 等全部回來再啟動 subagent 來 review。
- backend：T011、T012 都動 `steps/common/CommonGivenSteps.java`，T015、T016、T017 都動 `steps/common/CommonThenSteps.java`，T014 與 T003 的 helper 相依；同檔的題不要同時派。T018–T029（`steps/managerqueue/`）、T030–T041（`steps/inspection/`）、T042–T051（`steps/repair/`）三組各自獨立，可平行。T052 動 `steps/push/PushManagerActionSteps.java`，與其他組不衝突。
- ios-app：T053–T060 多半在 `steps/common/`，後寫入者自己 merge；T061–T065 的根 Given／Then 建議放 `steps/common/` 既有檔案附近，與 T053–T058 同檔，一併排序。T069–T106（`steps/manager/` 三檔）分 Given（T069–T077）／When（T078–T083）／Then（T084–T106）三組派；T107–T122（`steps/repair/`）一組；T123–T128（`steps/pool/`）一組；T129–T131（`steps/system/`）一組；T066（`steps/account/`）、T067–T068（`steps/push/`）各自獨立。

### backend / 介面根共用 DSL

- [ ] T011 [P] [BDD-ALIGN] `飯店有以下員工：`
  - Read:
    - `specs/truth/features/backend/dsl.md` -> 該列 Data Table 新增的選填 `角色`（主管／員工；可表達被設了部門的主管）
    - `tms/src/test/java/com/athena/tms/bdd/steps/common/CommonGivenSteps.java`、`tms/src/test/java/com/athena/tms/bdd/support/StaffFixtures.java`
  - 沒給 `角色` 時行為不變；給「主管」時 `role=manager`，`dept` 照表（可為空也可帶部門）。

- [ ] T012 [P] [BDD-ALIGN] `"{員工}" 名下有以下任務：`
  - Read:
    - `specs/truth/features/backend/dsl.md` -> 該列新增的選填 `完成時間`（`今天 HH:mm`／`昨天 HH:mm`，寫 `done_at`）
    - `tms/src/test/java/com/athena/tms/bdd/steps/common/CommonGivenSteps.java`、`tms/src/test/java/com/athena/tms/bdd/support/TaskFixtures.java`、`support/MutableClock.java`
  - `昨天` 以測試時鐘的當天往前一天算，`work_date` 跟著落在昨天。

- [ ] T013 [P] [BDD-ALIGN] `已有以下工務單：`
  - Read:
    - `specs/truth/features/backend/dsl.md` -> 該列全部新增與改動的欄（`送出時間`、`處理結果`、`處理說明`、`完修照片張數`、`完修時間`、`驗收人`、`來源` 加 `定期保養`、`報修人` 改選填、`房號` 可為「公共區域」）
    - `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairGivenSteps.java`
  - `處理結果` 直接寫 `repair_tickets.resolution_type`（T001 的欄位），`無法修復` 時該房 `rooms.hk_state` 一併寫 `out_of_order`；`送出時間` 落成 `task_events` 的 `created` 或 `repair_resubmitted` 時間；`驗收人` 寫 `verified_by` 與 `verified_at`。

- [ ] T014 [P] [BDD-ALIGN] `"{員工}" 判定 "{任務}" 查房全部項目通過`
  - Read:
    - `specs/truth/features/backend/dsl.md` -> 該列改寫後的實作語意（`CreateInspectionRequest{taskId, items}` 加 `Idempotency-Key`，回應 `InspectionOutcome`；403 自清自查、409 非待查房、房況 `inspected`）
    - `tms/src/test/java/com/athena/tms/bdd/steps/common/CommonWhenSteps.java:85-91`
    - T003 的 helper
  - 改成呼叫 T003 的 helper 並把回應存進 `ScenarioWorld`，讓後續 `這次操作被拒絕，錯誤碼為` 與 `這次查房的結果為` 讀得到。

- [ ] T015 [P] [BDD-ALIGN] `"{員工}" 收到以下推播：`
  - Read:
    - `specs/truth/features/backend/dsl.md` -> 該列新增的選填 `內容`（`PushPayload.body`）
    - `tms/src/test/java/com/athena/tms/bdd/steps/common/CommonThenSteps.java`、`tms/src/test/java/com/athena/tms/bdd/support/PushPayloadView.java`
  - 表格有 `內容` 欄才比對 body，沒有時行為不變。

- [ ] T016 [P] [BDD-RED] `這次操作被拒絕，錯誤碼為 "{錯誤碼}"`
  - 驗上一次操作的 HTTP 狀態是 4xx 且 `Error.code` 等於值（`state_conflict`、`inspection_items_mismatch`、`reject_reason_required` 等）；與既有 `這次操作被拒絕` 共用保存的回應，不共用 annotation。

- [ ] T017 [P] [BDD-RED] `"{房號}" 的房況為 "{房況}"`
  - 查 `rooms.hk_state`（已查房、還沒打掃好、停用待確認等用語對照見 `be/dsl.md` 狀態用語對照）。

### backend / 主管工作台

- [ ] T018 [P] [BDD-RED] `"{員工}" 查看主管待辦`
- [ ] T019 [P] [BDD-RED] `主管待辦的三格數字為：`
- [ ] T020 [P] [BDD-RED] `待查房清單依序為 {房號清單}`
- [ ] T021 [P] [BDD-RED] `待查房清單不包含 "{任務}"`
- [ ] T022 [P] [BDD-RED] `待審核清單依序為 {單號清單}`
- [ ] T023 [P] [BDD-RED] `待審核清單不包含 "{單號}"`
- [ ] T024 [P] [BDD-RED] `待驗收清單依序為 {單號清單}`
- [ ] T025 [P] [BDD-RED] `待驗收清單不包含 "{單號}"`
- [ ] T026 [P] [BDD-RED] `待驗收清單中 "{單號}" 的完修結果為 "{結果}"、來源為 "{來源}"`
- [ ] T027 [P] [BDD-RED] `"{單}" 在清單上標為不是今天開始等`
- [ ] T028 [P] [BDD-RED] `"{單}" 在清單上標為今天開始等`
- [ ] T029 [P] [BDD-RED] `這次查詢被拒絕，因為呼叫者不是主管`

### backend / 查房

- [ ] T030 [P] [BDD-RED] `飯店目前設定的查房項目如下：`
- [ ] T031 [P] [BDD-RED] `飯店停用了全部查房項目`
- [ ] T032 [P] [BDD-RED] `飯店新增了查房項目 "{項目}"`
- [ ] T033 [P] [BDD-RED] `"{主管}" 已判定 "{任務}" 查房全部項目通過`
- [ ] T034 [P] [BDD-RED] `"{主管}" 判定 "{任務}" 查房如下：`
- [ ] T035 [P] [BDD-RED] `"{主管}" 以空的項目清單判定 "{任務}" 查房`
- [ ] T036 [P] [BDD-RED] `"{主管}" 判定 "{任務}" 查房全部項目通過，同一請求共送出 {次數} 次`
- [ ] T037 [P] [BDD-RED] `這次查房的結果為 "{結果}"，去向為 "{去向}"`
- [ ] T038 [P] [BDD-RED] `這次查房被拒絕，原因是項目集合不符，回應帶現行項目 {項目清單}`
- [ ] T039 [P] [BDD-RED] `"{任務}" 在房務待認領`
- [ ] T040 [P] [BDD-RED] `"{任務}" 的查房紀錄只有 {筆數} 筆`
- [ ] T041 [P] [BDD-RED] `"{員工}" 沒有收到查房退回的推播`

### backend / 工務單

- [ ] T042 [P] [BDD-ALIGN] `"{主管}" 核准 "{單號}"`
  - Read:
    - `specs/truth/features/backend/工務單/dsl.md` -> 該列改寫後的實作語意（`approveRepairTicket`、`Idempotency-Key`、409 `state_conflict`）
    - `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairWhenSteps.java:204`
  - 現況 `post(..., null)` 沒帶 `Idempotency-Key`；補上並保存回應供 `這次操作被拒絕，錯誤碼為` 讀取。
- [ ] T043 [P] [BDD-ALIGN] `"{主管}" 以原因 "{原因}" 退回 "{單號}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairWhenSteps.java:237`；同上，另注意退回同交易推播報修人的語意由 Then 驗、這句不斷言。
- [ ] T044 [P] [BDD-ALIGN] `"{員工}" 補資料再送出 "{單號}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairWhenSteps.java:296`；同上。
- [ ] T045 [P] [BDD-ALIGN] `"{主管}" 驗收通過 "{單號}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairWhenSteps.java:247`；同上，驗收不改房況。
- [ ] T046 [P] [BDD-RED] `"{主管}" 退回 "{單號}" 但沒有寫原因`
- [ ] T047 [P] [BDD-RED] `"{主管}" 已先驗收通過 "{單號}"`
- [ ] T048 [P] [BDD-RED] `"{員工}" 已撤回 "{單號}"`
- [ ] T049 [P] [BDD-RED] `"{單號}" 的驗收時間為現在`
- [ ] T050 [P] [BDD-RED] `"{單號}" 的驗收人只有 "{主管}" 一人，驗收只記下一次`
- [ ] T051 [P] [BDD-RED] `"{單號}" 開始等主管的時間為現在`

### backend / 推播

- [ ] T052 [P] [BDD-ALIGN] `"{主管}" 查房 "{任務}"，判定 "{項目}" 不合格`
  - Read:
    - `specs/truth/features/backend/推播/dsl.md` -> 該列（新 body 與 `Idempotency-Key`，語意不變）
    - `tms/src/test/java/com/athena/tms/bdd/steps/push/PushManagerActionSteps.java:95-101`
  - 改成呼叫 T003 的 helper；推播斷言不動。

### ios-app / 介面根共用 DSL

- [ ] T053 [P] [BDD-ALIGN] `飯店有以下員工：`
  - Read:
    - `specs/truth/features/ios-app/dsl.md` -> 該列新增的 `角色`、`班次結束` 與部門值集
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/common/CommonGivenSteps.java`、`BackendChannel.createManagerLikeStaff`
- [ ] T054 [P] [BDD-ALIGN] `"{員工}" 名下有以下任務：`
  - Read:
    - `specs/truth/features/ios-app/dsl.md` -> 該列實作語意（操作者是別人時只建資料、不換登入者；`完成時間` 寫 `done_at`）
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/common/CommonGivenSteps.java`
  - 主管看別人清好的房的案例：表格的人不是劇本操作者時，不可因此重新登入成那個人。
- [ ] T055 [P] [BDD-ALIGN] `已有以下工務單：`
  - Read:
    - `specs/truth/features/ios-app/dsl.md` -> 該列「支援：同後端共用 DSL 的同名句型」
    - `specs/truth/features/backend/dsl.md` -> 該列新增的欄（見 Scope Note 第 6 條）
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairGivenSteps.java`、`BackendChannel.createRepairTicket`（第 835、853 行）、`markRepairTicketVerified`、`completeRepairTask`
  - 補收 `送出時間`、`處理結果`、`處理說明`、`完修照片張數`、`完修時間`、`驗收人`、`來源=定期保養`、公共區域與選填報修人；`處理結果` 寫 `repair_tickets.resolution_type`。
- [ ] T056 [P] [BDD-ALIGN] `手機目前離線`
  - Read: `specs/truth/features/ios-app/dsl.md` -> 「劇本中的員工」改「劇本中的操作者」；`app/bdd/.../steps/common/CommonGivenSteps.java`。主管當操作者時也要切得了離線。
- [ ] T057 [P] [BDD-ALIGN] `"{員工}" 在 App 打開首頁`
  - Read:
    - `specs/truth/features/ios-app/dsl.md` -> 該列改寫後的實作語意（依角色分流的首頁與各自的載入完成條件）
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/common/CommonWhenSteps.java`
  - 主管等 `manager.home.tile.*` 載入完成，工務等工務首頁，房務等既有首頁；不得假設一律是房務首頁。
- [ ] T058 [P] [BDD-ALIGN] `"{任務}" 在 App 顯示為 "{狀態}"`
  - Read: `specs/truth/features/ios-app/dsl.md` -> 必查處新增「工務單詳情」；`app/bdd/.../steps/common/CommonThenSteps.java`。任務是工務單號時到工務單詳情的狀態列驗。
- [ ] T059 [P] [BDD-ALIGN] `這次操作沒有留在手機上等補送`
  - Read:
    - `truth-delta.md` -> 「DSL row 搬移」表第一列（舊 `ios-app/任務執行/dsl.md`，新 `ios-app/dsl.md`，用途加主管四動作）
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/taskexecution/TaskExecutionThenSteps.java`
  - 只對齊精確參照與用途說明；待同步筆數不增加、後端狀態不變的兩面必查不變。
- [ ] T060 [P] [BDD-ALIGN] `後端沒有對 "{員工}" 送出任何推播`
  - Read: `truth-delta.md` -> 「DSL row 搬移」表第二列；`app/bdd/.../steps/taskexecution/TaskExecutionThenSteps.java`。只對齊精確參照，主管工作台「項目不符時不推播」場景可用。
- [ ] T061 [P] [BDD-RED] `"{員工}" 上次在待認領停在 "{部門}"`
  - 透過測試後門或直接寫 App 本機的部門選擇（`SharedPreferences` 那一把鍵），不經畫面。
- [ ] T062 [P] [BDD-RED] `後端已對 "{員工}" 送出推播 "{標題}"`
- [ ] T063 [P] [BDD-RED] `後端 "{房號}" 的房況為 "{房況}"`
- [ ] T064 [P] [BDD-RED] `畫面回到 "{清單}"，"{單}" 不在清單上`
- [ ] T065 [P] [BDD-RED] `畫面顯示 "這張單已被處理"，並回到 "{清單}"，"{單}" 不在清單上`

### ios-app / 帳號與登入

- [ ] T066 [P] [BDD-ALIGN] `"{員工}" 登入成功，進入 "{首頁}"`
  - Read:
    - `specs/truth/features/ios-app/帳號與登入/dsl.md` -> 該列（房務首頁／工務首頁／主管首頁；三入口共用的分流判定）
    - `truth-delta.md` -> `/dsl-refine` MODIFY 該句與既有 3 個 Example
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/account/AccountThenSteps.java` -> `@那麼("{string} 登入成功，進入首頁")`
  - 句型帶參數後舊 annotation 不再匹配，改寫而不是另加一個；三個值各自驗對應外殼的首頁元素。

### ios-app / 推播

- [ ] T067 [P] [BDD-ALIGN] `"{員工}" 收到一則推播 "{標題}"，去處為 "{去處}"`
  - Read:
    - `specs/truth/features/ios-app/推播/dsl.md` -> `去處` 值集加「房務待認領並標亮 X」「工務待認領並標亮 X」；`kind=pool` 帶 `dept`、`kind=home` 依 session 角色、冷啟動；標題不驗權威文案
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/push/PushGivenSteps.java`
- [ ] T068 [P] [BDD-ALIGN] `App 停在 "{畫面}"`
  - Read:
    - `specs/truth/features/ios-app/推播/dsl.md` -> 畫面值集加主管首頁／工務首頁／房務首頁／帶部門的待認領；必查加部門切換與本機選擇、外殼與角色一致、主管視角詳情
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/push/PushThenSteps.java`

### ios-app / 主管工作台 / Given

- [ ] T069 [P] [BDD-RED] `"{主管}" 已在 App 打開主管首頁`
- [ ] T070 [P] [BDD-RED] `"{主管}" 已在 App 打開 "{清單}"`
- [ ] T071 [P] [BDD-RED] `飯店目前設定的查房項目如下：`
- [ ] T072 [P] [BDD-RED] `飯店目前沒有任何啟用的查房項目`
- [ ] T073 [P] [BDD-RED] `"{主管}" 已從待查房清單打開 "{任務}"，還沒判任何一項`
- [ ] T074 [P] [BDD-RED] `"{主管}" 已從待查房清單打開 "{任務}"，判定如下：`
- [ ] T075 [P] [BDD-RED] `飯店在 "{主管}" 送出前新增了查房項目 "{項目}"`
- [ ] T076 [P] [BDD-RED] `"{任務}" 剛被 "{另一位主管}" 查完，"{主管}" 的畫面還沒更新`
  - 不經 App，以另一位主管呼叫 `createInspections` 全部通過（可用 `BackendChannel.failInspection` 旁邊再開一個全通過版本）；不觸發畫面刷新。
- [ ] T077 [P] [BDD-RED] `"{主管}" 剛送出 "{任務}" 的查房，結果畫面停在畫面上`

### ios-app / 主管工作台 / When

- [ ] T078 [P] [BDD-RED] `"{主管}" 在主管首頁點 "{格}"`
- [ ] T079 [P] [BDD-RED] `"{主管}" 從待查房清單打開 "{任務}"`
- [ ] T080 [P] [BDD-RED] `"{主管}" 把 "{項目}" 判 "{判定}"`
- [ ] T081 [P] [BDD-RED] `"{主管}" 把以下項目判定後送出：`
- [ ] T082 [P] [BDD-RED] `"{主管}" 送出查房`
- [ ] T083 [P] [BDD-RED] `"{主管}" 從結果畫面回到待查房清單`

### ios-app / 主管工作台 / Then

- [ ] T084 [P] [BDD-RED] `三格依序顯示：`
  - 必查有兩面：畫面三個磚的數字逐格等於表格，且等於 `getManagerQueues` 回應的 `counts` 與各陣列長度；只驗畫面不算數。
- [ ] T085 [P] [BDD-RED] `"{格}" 格顯示 {數字}`
- [ ] T086 [P] [BDD-RED] `清單依序為 {清單}`
- [ ] T087 [P] [BDD-RED] `清單上沒有 "{單}"`
- [ ] T088 [P] [BDD-RED] `"{單}" 那一列標出日期 "{日期}" 與時刻 "{時刻}"`
- [ ] T089 [P] [BDD-RED] `"{單}" 那一列只顯示時刻 "{時刻}"`
- [ ] T090 [P] [BDD-RED] `每一列都看得到房號與內容`
- [ ] T091 [P] [BDD-RED] `每一列都看得到工務單號`
- [ ] T092 [P] [BDD-RED] `"{單號}" 那一列標示 "{標籤}"`
- [ ] T093 [P] [BDD-RED] `清單說明目前沒有等他處理的單，畫面不是空白`
- [ ] T094 [P] [BDD-RED] `「我名下的單」依序列出 {房號清單}，卡片與房務首頁的任務卡一致`
- [ ] T095 [P] [BDD-RED] `主管首頁沒有問候列與今日進度`
- [ ] T096 [P] [BDD-RED] `「我名下的單」說明目前沒有名下的單`
- [ ] T097 [P] [BDD-RED] `首頁沒有待查房、待審核、待驗收三格`
- [ ] T098 [P] [BDD-RED] `底部分頁依序是 {分頁清單}`
- [ ] T099 [P] [BDD-RED] `查房畫面列出以下項目，每一項都還沒判：`
- [ ] T100 [P] [BDD-RED] `查房送出鈕為 "{按鈕狀態}"`
  - 禁用時要確認真的按不下去（與 `完修回報的送出鈕為` 同一套語意）。
- [ ] T101 [P] [BDD-RED] `查房畫面沒有備註欄`
- [ ] T102 [P] [BDD-RED] `查房畫面說明目前沒有查房項目，送出即視為全部通過`
- [ ] T103 [P] [BDD-RED] `查房結果畫面說明 "{任務}" 查房通過、已結案`
- [ ] T104 [P] [BDD-RED] `查房結果畫面說明 "{任務}" 退回重做，回到 "{員工}" 名下，不合格項為 "{項目}"`
- [ ] T105 [P] [BDD-RED] `查房結果畫面說明 "{任務}" 退回重做，"{員工}" 已下班，回到房務待認領`
- [ ] T106 [P] [BDD-RED] `查房畫面重新載入以下項目並清空判定：`

### ios-app / 工務單

- [ ] T107 [P] [BDD-ALIGN] `工務單詳情顯示狀態摘要卡 "{摘要卡文案}"`
  - Read:
    - `specs/truth/features/ios-app/工務單/dsl.md` -> 文案清單加主管視角三句；必查改對 `RepairTicket.verifiedBy`、`reviewRequestedAt`
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairThenSteps.java`
- [ ] T108 [P] [BDD-ALIGN] `工務單詳情顯示退回原因 "{原因}"`
  - Read: `specs/truth/features/ios-app/工務單/dsl.md` -> 對 `RepairTicket.rejectReason`；`app/bdd/.../steps/repair/RepairThenSteps.java`。
- [ ] T109 [P] [BDD-RED] `"{員工}" 已在 App 打開 "{單號}" 的工務單詳情`
  - Arrange 版：停在詳情；開頁是受測動作時用同名 When（`RepairWhenSteps` 既有），不共用 annotation。
- [ ] T110 [P] [BDD-RED] `"{單號}" 剛被 "{主管}" 驗收，"{員工}" 的畫面還沒更新`
- [ ] T111 [P] [BDD-RED] `"{單號}" 剛被 "{員工}" 撤回，"{主管}" 的畫面還沒更新`
- [ ] T112 [P] [BDD-RED] `"{主管}" 在工務單詳情按核准`
- [ ] T113 [P] [BDD-RED] `"{主管}" 在工務單詳情按退回，寫下原因 "{原因}" 並送出`
- [ ] T114 [P] [BDD-RED] `"{主管}" 在工務單詳情打開退回面板，不寫原因`
- [ ] T115 [P] [BDD-RED] `"{主管}" 在工務單詳情按驗收`
- [ ] T116 [P] [BDD-RED] `工務單詳情的主管動作為 "{動作}"`
- [ ] T117 [P] [BDD-RED] `工務單詳情顯示報修內容：`
- [ ] T118 [P] [BDD-RED] `工務單詳情顯示完修照片 {張數} 張與處理說明 "{說明}"`
- [ ] T119 [P] [BDD-RED] `工務單詳情沒有驗收不通過、退回與備註欄`
- [ ] T120 [P] [BDD-RED] `退回面板的送出鈕為 "{按鈕狀態}"`
- [ ] T121 [P] [BDD-RED] `後端沒有收到這次退回`
- [ ] T122 [P] [BDD-RED] `"{單號}" 已由 "{主管}" 驗收，驗收時間為現在`

### ios-app / 待認領

- [ ] T123 [P] [BDD-ALIGN] `"{員工}" 在 App 打開房務待認領`
  - Read:
    - `specs/truth/features/ios-app/待認領/dsl.md` -> 實作語意補主管時是切到待認領分頁並把部門切到房務
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/pool/PoolWhenSteps.java`
- [ ] T124 [P] [BDD-ALIGN] `待認領列表依序為 {房號清單}`
  - Read:
    - `specs/truth/features/ios-app/待認領/dsl.md` -> 參數加「工務池寫工務單號」；必查改為目前部門的列表、`listDeptPool` 帶目前部門
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/pool/PoolThenSteps.java` -> `@那麼("^待認領列表依序為 (.+)$")`
- [ ] T125 [P] [BDD-RED] `"{主管}" 第一次在這台手機打開待認領`
  - 先清掉本機的部門選擇再開；與 `"{員工}" 上次在待認領停在` 相反，不共用落地碼。
- [ ] T126 [P] [BDD-RED] `"{主管}" 在待認領切到 "{部門}"`
- [ ] T127 [P] [BDD-RED] `待認領目前的部門為 "{部門}"`
- [ ] T128 [P] [BDD-RED] `"{任務}" 的單卡沒有搶單鈕`
  - 單卡存在（`pool.card.{roomNo}` 或 `engPool.item.{單號}`）但沒有 `.claim-button` 子節點；兩個條件缺一不可。

### ios-app / 系統層

- [ ] T129 [P] [BDD-ALIGN] `"{員工}" 在 App 離線時嘗試 "{動作}"`
  - Read:
    - `specs/truth/features/ios-app/系統層/dsl.md` -> `動作` 值集加主管四動作（送出查房結果、核准工務單、退回工務單、驗收工務單）與各自對應的畫面操作、不呼叫的端點
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/system/SystemWhenSteps.java`
- [ ] T130 [P] [BDD-RED] `"{員工}" 已在 App 登入，停在首頁`
  - Read: `truth-delta.md` -> 「DSL row 搬移」表第三列（下放到系統層）。停在依角色分流後的首頁，不是固定房務首頁。
- [ ] T131 [P] [BDD-RED] `"{員工}" 在離線時嘗試過 "{動作}" 並被擋下`
  - Arrange 版：切離線、做該動作、確認提示出現，停在原畫面；用於驗恢復連線後不補送。

- [ ] T132 subagent review (phase quality gate)

## Phase 4A: ADD Feature File - backend/主管工作台/主管待辦三類清單.feature

**Goal**: `getManagerQueues` 依三條判定公式回三格數字與三份清單，排除呼叫者本人清的房，由舊到新排好，並以後端 `Clock` 標記是不是今天。

**Shared Must Read**:
- `specs/truth/features/backend/主管工作台/主管待辦三類清單.feature` -> `功能: 主管待辦三類清單`（9 個 Rule）
- `specs/truth/features/backend/主管工作台/dsl.md` -> 本檔 When／Then 全部
- `specs/truth/features/backend/dsl.md` -> 本檔實際用到的介面根 rows：`飯店有以下員工：`、`"{員工}" 名下有以下任務：`、`已有以下工務單：`、`現在時間是 "{時刻}"`、`"{員工}" 補資料再送出 "{單號}"`（工務單模組）
- `truth-delta.md` -> `/dsl-refine` ADD `be/主管工作台/dsl.md`、ADD `主管待辦三類清單.feature`；`/api-plan` ADD `getManagerQueues`、ADD `components/schemas/主管工作台.yaml`；`/data-plan` NOOP 第一列、MODIFY `tasks` Note、MODIFY `task_events` Note、MODIFY `repair_tickets` Note、Wave 3 ADD `resolution_type`
- `specs/truth/contracts/groups/現場作業/主管工作台.yaml` -> `getManagerQueues` 的 description（排除本人、排序鍵、不帶日期條件、不碰 `listInspections` 與 `listRepairTickets`）
- `specs/truth/contracts/components/schemas/主管工作台.yaml` -> `ManagerQueues`、`QueueItemBase`、`InspectionQueueItem`、`ReviewQueueItem`、`VerifyQueueItem`
- `specs/truth/data/data-model.dbml` -> `Table tasks` Note「主管待辦的三類判定」、`Table task_events` Note 的待審核排序鍵、`Table repair_tickets` Note 的待審核與待驗收段
- `tms/src/main/java/com/athena/tms/service/TaskExecutionService.java:1020` -> `belongsToToday` 的同一把 `Clock`

**Boundary**:
- 不新增資料表、欄位或索引；三類都是查詢期由 `tasks`、`task_events`、`repair_tickets` 算出，走既有 `(tenant_id, dept, state)` 索引。
- 待審核的排序鍵一律取 `task_events` 最後一筆 `created` 或 `repair_resubmitted` 的時間，不得拿 `updated_at` 或建單時間代替。
- `waitingSinceIsToday` 用注入的 `Clock` 時區判，App 不判；`waitingSince` 帶時區位移。
- 公共區域 `roomNo` 回 null 加 `isPublicArea=true`、地點在 `title`；不得把佔位房號「公共區域」漏出去。
- 不動 `listInspections` 與 `listRepairTickets`。

**Test Scope**:
- `specs/truth/features/backend/主管工作台/主管待辦三類清單.feature`

- [ ] T133 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T134 [BDD-REFACTOR] 在綠燈下把三條判定公式收成三個可讀的查詢方法，`counts` 直接取三個陣列的長度，不另算一次

## Phase 4B: ADD Feature File - backend/主管工作台/主管待辦空清單與權限.feature

**Goal**: 沒有單的類別回 0 與空陣列（欄位不缺），非主管角色呼叫回 403。

**Shared Must Read**:
- `specs/truth/features/backend/主管工作台/主管待辦空清單與權限.feature` -> `功能: 主管待辦空清單與權限`（2 個 Rule）
- `specs/truth/features/backend/主管工作台/dsl.md` -> `"{員工}" 查看主管待辦`、`主管待辦的三格數字為：`、三個 `…清單依序為`、`這次查詢被拒絕，因為呼叫者不是主管`
- `specs/truth/features/backend/dsl.md` -> `飯店有以下員工：`（`角色` 欄）
- `truth-delta.md` -> `/dsl-refine` ADD `主管待辦空清單與權限.feature`；`/api-plan` ADD `getManagerQueues`（只開放 `manager`，其他角色 403）
- `specs/truth/contracts/groups/現場作業/主管工作台.yaml` -> 403 回應
- `specs/truth/contracts/components/responses.yaml` -> 既有 403 的 `Error` 形狀

**Boundary**:
- 403 判的是 `role`，不是 `dept`；被設了部門的主管仍是主管。
- 空清單回 `[]` 不回 null，`counts` 三個鍵都在。

**Test Scope**:
- `specs/truth/features/backend/主管工作台/主管待辦空清單與權限.feature`

- [ ] T135 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T136 [BDD-REFACTOR] 在綠燈下確認角色守衛與 `getMyToday` 的 `CurrentActor` 取法共用同一處

## Phase 4C: ADD Feature File - backend/查房/逐項判定與退回去向.feature

**Goal**: `createInspections` 收斂後的請求形狀成立：逐項判定、回 `InspectionOutcome` 帶去向、項目集合不符回 422 帶現行集合且不推播、單非待查房回 409 只接受先到、冪等重送只判一次。

**Shared Must Read**:
- `specs/truth/features/backend/查房/逐項判定與退回去向.feature` -> `功能: 逐項判定與退回去向`（10 個 Rule）
- `specs/truth/features/backend/查房/dsl.md` -> 本輪 ADD 的四個 Given、三個 When、五個 Then，以及既有 `"{任務}" 的重做次數為 {次數}`、`"{任務}" 沒有任何 SOP 勾記`
- `specs/truth/features/backend/dsl.md` -> `"{員工}" 判定 "{任務}" 查房全部項目通過`（MODIFY）、`這次操作被拒絕，錯誤碼為 "{錯誤碼}"`、`"{房號}" 的房況為 "{房況}"`、`"{員工}" 收到推播 "{標題}"`、`"{員工}" 沒有收到任何推播`
- `truth-delta.md` -> `/dsl-refine` ADD `be/查房/逐項判定與退回去向.feature`、ADD `be/查房/dsl.md`、ADD `be/dsl.md` 兩句、MODIFY `判定…全部項目通過`；`/api-plan` MODIFY `createInspections`、ADD 三個查房 schema、DELETE `inspectionsRequest`、MODIFY `Error` 錯誤碼清單、「破壞性變更」第一與第二點
- `specs/truth/contracts/groups/現場作業/查房.yaml` -> `createInspections` 的 `Idempotency-Key`、201、403、404、409 `state_conflict`、422 `inspection_items_mismatch`
- `specs/truth/contracts/components/schemas/查房.yaml` -> `CreateInspectionRequest`、`InspectionOutcome`（`destination` 三值）
- `specs/truth/contracts/components/schemas/shared.yaml` -> `Error` 的 `inspection_items_mismatch`（`context` 帶 `missing`、`unexpected`、`currentItems`）與 `state_conflict` 補句
- `tms/src/main/java/com/athena/tms/service/InspectionService.java:111` -> `validateCoverage`（現況只拒缺項、不拒多項、不回現行集合）
- `tms/src/main/java/com/athena/tms/controller/InspectionApiController.java` -> T005 改過的 record 與 DTO

**Boundary**:
- 全通過結案、退回原清房者、原清房者下班回待認領三條既有規則不改，只把結果裝進 `InspectionOutcome.destination`。
- 422 只在項目集合不符時回；缺項與多項都算，`currentItems` 是現行啟用集合；被拒時單不變、不寫查房紀錄、不推播。
- 409 只在單已不是待查房時回，兩位主管同時送出只接受先到；後到不多推播。
- 冪等靠既有 `processed_ops`：本 phase 把 T005 預留的 `Idempotency-Key` 參數接進回放，同一把鍵重送回放第一次回應，查房紀錄只一筆。
- 不動 `免查房鎖定與結案.feature`、`免查抽查與合格率.feature` 的行為（NOOP）。

**Test Scope**:
- `specs/truth/features/backend/查房/逐項判定與退回去向.feature`

- [ ] T137 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T138 [BDD-REFACTOR] 在綠燈下讓 `validateCoverage` 回一個帶 `missing`、`unexpected`、`currentItems` 的結果物件，controller 只負責對映成 422；去向判定與推播共用既有退回路徑

## Phase 4D: ADD Feature File - backend/工務單/主管審核與驗收的狀態守衛.feature

**Goal**: 四個既有主管動作端點的狀態守衛被 feature 釘住（退回原因空白 422、撤回後核准 409、重複核准 409、先到先驗收、維修中不能驗收、驗收記時間、驗收無法修復不改房況）；同時承接本輪契約 MODIFY 的後端面：`RepairTicket` 四個新欄位、`RepairTicketSummary` 的公共區域表達、`resolveOnComplete` 落地 `resolution_type`。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/主管審核與驗收的狀態守衛.feature` -> `功能: 主管審核與驗收的狀態守衛`（7 個 Rule）
- `specs/truth/features/backend/工務單/dsl.md` -> 四個主管動作 When（MODIFY）、`"{主管}" 退回 "{單號}" 但沒有寫原因`、`"{主管}" 已先驗收通過 "{單號}"`、`"{員工}" 已撤回 "{單號}"`、`"{單號}" 的驗收時間為現在`、`"{單號}" 的驗收人只有 "{主管}" 一人，驗收只記下一次`、`"{單號}" 已由 "{主管}" 驗收`、`"{單號}" 尚未驗收`
- `specs/truth/features/backend/dsl.md` -> `已有以下工務單：`（MODIFY）、`這次操作被拒絕，錯誤碼為 "{錯誤碼}"`、`"{房號}" 的房況為 "{房況}"`
- `truth-delta.md` -> `/dsl-refine` ADD `主管審核與驗收的狀態守衛.feature`、ADD `be/工務單/dsl.md`、MODIFY `be/工務單/dsl.md` When 四句；`/api-plan` NOOP `approveRepairTicket`／`rejectRepairTicket`／`verifyRepairTicket`、MODIFY `readRepairTicket` description、MODIFY `RepairTicket`、MODIFY `RepairTicketSummary`、「破壞性變更」第三與第四點；`/data-plan` Wave 3 三列
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `readRepairTicket`（可查看者加主管）、`approveRepairTicket`、`rejectRepairTicket`、`verifyRepairTicket` 的 409／422
- `specs/truth/contracts/components/schemas/工務單.yaml` -> `RepairTicket` 的 `resolutionType`、`doneAt`、`reviewRequestedAt`、`origin`、`description` 可為 null
- `specs/truth/contracts/components/schemas/shared.yaml` -> `RepairTicketSummary` 的 `roomNo` 可為 null、`isPublicArea`、`reporterName` 可為 null
- `specs/truth/data/data-model.dbml` -> `Table repair_tickets` 的 `resolution_type` 與「完修三選一」段、`Table task_events` 的 `completed` payload
- `tms/src/main/java/com/athena/tms/repair/RepairService.java:376` -> `resolveOnComplete`（現況丟掉 `resolutionType`）；`read` 的回應組裝
- `tms/src/main/java/com/athena/tms/service/TaskExecutionService.java:1044` -> `engineeringTickets` 的公共區域做法（沿用）

**Boundary**:
- 四個端點的狀態轉移與權限不改（契約 NOOP）；只確認守衛回對的 HTTP 狀態與 `Error.code`。
- 驗收只記一次：第二位主管撞 409，`verified_by`／`verified_at` 不被覆寫；驗收不改房況。
- 本 phase 必做的契約 MODIFY 後端面（沒有後端 feature 直接驗，由 App 端 Phase 4H／4J 間接驗，見 Scope Note 第 2 條）：①`RepairService.read` 補 `origin`、`doneAt`（`tasks.done_at`）、`reviewRequestedAt`（`task_events` 最後一筆 `created` 或 `repair_resubmitted`）、`resolutionType`（讀欄位）；②組 `RepairTicketSummary` 時公共區域把佔位房號換成 null 並補 `isPublicArea`；③`resolveOnComplete` 同一交易寫 `repair_tickets.resolution_type`（省略時 `fixed`）與 `task_events.completed` payload 的 `resolutionType`。
- 退回時推播報修人屬 Phase 4E，本 phase 不寫。

**Test Scope**:
- `specs/truth/features/backend/工務單/主管審核與驗收的狀態守衛.feature`

- [ ] T139 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T140 [BDD-REFACTOR] 在綠燈下把「完修結果」的讀寫收成 `repair_tickets.resolution_type` 唯一出處（`read`、待驗收清單、`resolveOnComplete` 都不翻 `task_events` 的 JSON），並確認 `RepairTicketSummary` 的公共區域組裝與 `engineeringTickets` 共用

## Phase 4E: MODIFY Feature File - backend/工務單/開立工務單與急件.feature

**Goal**: 主管退回一般件時同一交易推播報修人（`repair_rejected`，title 帶房號、單號與「主管未核准」，body 帶退回原因，去處工務單詳情）；核准不推播；補資料再送出後開始等主管的時間是重送時間。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/開立工務單與急件.feature` -> Rule「主管退回一般件時推播報修人，內容帶退回原因，點開進這張工務單詳情」、「主管核准一般件時不推播報修人」、「補資料再送出後，開始等主管的時間是這次重新送出的時間」
- `specs/truth/features/backend/工務單/dsl.md` -> `"{主管}" 以原因 "{原因}" 退回 "{單號}"`、`"{主管}" 核准 "{單號}"`、`"{員工}" 補資料再送出 "{單號}"`、`"{單號}" 開始等主管的時間為現在`
- `specs/truth/features/backend/dsl.md` -> `"{員工}" 收到以下推播：`（`內容` 欄）、`"{員工}" 沒有收到任何推播`
- `specs/truth/features/backend/推播/dsl.md` -> 模組說明（退回報修單的推播歸 `工務單` 模組驗）
- `truth-delta.md` -> `/dsl-refine` ADD `開立工務單與急件.feature` 三個 Rule、MODIFY `"{員工}" 收到以下推播：`、MODIFY `be/推播/dsl.md` 模組說明；`/api-plan` MODIFY `PushPayload.event` 與 `rejectRepairTicket` description；`/data-plan` MODIFY `notifications.event`
- `specs/truth/contracts/components/schemas/shared.yaml` -> `PushPayload.event` 第 18 值 `repair_rejected` 的觸發、收件人、title、body、target
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `rejectRepairTicket` 的「同一交易推播報修人」
- `specs/truth/data/data-model.dbml` -> `Table notifications` 的 `event` 與 `repair_rejected` Note
- `tms/src/main/java/com/athena/tms/repair/RepairService.java` -> 退回流程；`tms/src/main/java/com/athena/tms/service/PushOutboxWriter.java` -> 既有 outbox push 寫法（`kind=ticket` 的 target 組裝見 002 Addendum T491 收斂的唯一出處）

**Boundary**:
- 推播只在退回時寫，收件人只有報修人（`repair_tickets.reporter_id`），與 `task_events.type=repair_rejected` 同一交易。
- 核准與驗收不推播報修人；急件推播（既有）不受影響。
- target 的 `kind=ticket` 沿用既有「任務類別對 `PushTarget.kind`」的唯一出處，不另寫 switch。
- 既有 Rule（一般件進等主管審核、急件直接進待認領、核准後進待認領、退回須附原因等）是 NOOP，Test Scope 是整份檔案，既有場景不得變紅。

**Test Scope**:
- `specs/truth/features/backend/工務單/開立工務單與急件.feature`

- [ ] T141 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T142 [BDD-REFACTOR] 在綠燈下確認退回推播與完修推播共用同一段 payload 組裝（title／body／target），只差 `event` 與文案

## Phase 4F: MODIFY Feature File - ios-app/帳號與登入/登入與登出守衛.feature

**Goal**: 登入成功與強制改密完成兩個入口都走 `landingShellFor`，主管一律進主管首頁（含被設了部門的主管），工務進工務首頁，其餘房務首頁。

**Shared Must Read**:
- `specs/truth/features/ios-app/帳號與登入/登入與登出守衛.feature` -> Rule「登入成功後依角色進首頁，主管一律進主管首頁，不看帳號有沒有部門」、「強制改密完成後的去處與登入相同，依角色決定」，以及改句型後的既有 3 個 Example
- `specs/truth/features/ios-app/帳號與登入/dsl.md` -> `"{員工}" 登入成功，進入 "{首頁}"`
- `specs/truth/features/ios-app/dsl.md` -> `飯店有以下員工：`（`角色`）
- `truth-delta.md` -> `/dsl-refine` ADD `登入與登出守衛.feature` 兩個 Rule、MODIFY 該 Then 句、MODIFY 既有 3 個 Example、MODIFY `ios/dsl.md` `飯店有以下員工：`
- `ui/ui-plan.md` -> 介面 1「進 App 的首頁分流與推播開首頁」的判定順序表與「主管外殼」段
- `app/mobile/lib/core/landing.dart`（T007）、`app/mobile/lib/features/login/login_screen.dart:122`、`app/mobile/lib/features/login/force_password_change_screen.dart:138`、`app/mobile/lib/features/shell/main_shell.dart`

**Boundary**:
- 兩個入口只呼叫 `landingShellFor`，不得各留一份 `dept == 'engineering'` 判斷。
- `ManagerShell` 的首頁分頁可以先是 Phase 4G 之前的空殼，但分頁列四格與通知、我的兩分頁必須是既有的。
- 推播開首頁的第三個入口屬 Phase 4K。

**Test Scope**:
- `specs/truth/features/ios-app/帳號與登入/登入與登出守衛.feature`

- [ ] T143 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T144 [BDD-REFACTOR] 在綠燈下確認 `landingShellFor` 是三入口唯一的分流點，登入畫面與強制改密畫面沒有殘留的部門判斷；純函式可補一支 Flutter 單元測試釘住三條判定順序

## Phase 4G: ADD Feature File - ios-app/主管工作台/主管首頁.feature

**Goal**: 主管首頁三格數字與名下的單對齊雛形：三格等於清單列數、0 照常顯示、四分頁外殼、名下的單沿用房務任務卡、沒有問候列與今日進度、房務員工首頁沒有三格。

**Shared Must Read**:
- `specs/truth/features/ios-app/主管工作台/主管首頁.feature` -> `功能: 主管首頁`（8 個 Rule）
- `specs/truth/features/ios-app/主管工作台/dsl.md` -> `"{主管}" 已在 App 打開主管首頁`、`"{主管}" 在主管首頁點 "{格}"`、`三格依序顯示：`、`"{格}" 格顯示 {數字}`、`清單說明目前沒有等他處理的單，畫面不是空白`、`「我名下的單」依序列出 …`、`主管首頁沒有問候列與今日進度`、`「我名下的單」說明目前沒有名下的單`、`首頁沒有待查房、待審核、待驗收三格`、`底部分頁依序是 {分頁清單}`
- `specs/truth/features/ios-app/dsl.md` -> `飯店有以下員工：`、`"{員工}" 名下有以下任務：`、`已有以下工務單：`、`現在時間是 "{時刻}"`、`"{員工}" 在 App 打開首頁`
- `truth-delta.md` -> `/dsl-refine` ADD `ios/主管工作台/dsl.md`、ADD `主管首頁.feature`；`/api-plan` ADD `getManagerQueues`、MODIFY `getMyToday` 與 `MyToday.poolBadgeCount` description
- `ui/ui-plan.md` -> 介面 2「首頁版面」「三格與清單的資料來源」「我名下的單沿用房務任務列的既有限制」、畫面 1 `主管首頁`、「前端實作切分建議」
- `ui/index.html`、`ui/manager.css`、`ui/manager.js` -> 主管首頁雛形全文（含 `?mine=0` 的名下沒單狀態）
- `specs/truth/contracts/groups/現場作業/主管工作台.yaml`、`specs/truth/contracts/components/schemas/主管工作台.yaml` -> `counts`
- `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `getMyToday` 主管呼叫時的語意（`todayTasks` 照常、`poolBadgeCount` 一律 0、`shift`／`floors`／`engineeringSummary` 為 null）
- `app/mobile/lib/features/manager/manager_home_screen.dart`（T008）、`app/mobile/lib/features/home/home_screen.dart` -> `_TaskRow`、`app/mobile/lib/core/widgets/tms_home_cards.dart`

**Boundary**:
- 首頁載入只打一次 `getManagerQueues` 拿三個數字（NFR-001），名下的單走 `getMyToday.todayTasks`；三格數字直接用回應的 `counts`，不自己算。
- 名下的單抽共用 `_TaskRow` 到 `core/widgets/`，不複製一份。
- 不帶問候列、進度環與下一間卡；分頁列的待認領徽章讀 `poolBadgeCount`（主管為 0）。
- 主管做完自己名下的單不會讓自己的三格變動（FR-011），不得為此加刷新邏輯。

**Test Scope**:
- `specs/truth/features/ios-app/主管工作台/主管首頁.feature`

- [ ] T145 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T146 [BDD-REFACTOR] 在綠燈下把三格磚抽成 `TmsCountTile`，首頁與清單共用同一份 `getManagerQueues` 回應的保存與重取入口

## Phase 4H: ADD Feature File - ios-app/主管工作台/三類清單.feature

**Goal**: 同一個清單元件吃三種類別，每列顯示房號（或單號）、內容、次要字與右欄時間，跨日標日期，排序照後端，動作完成回清單後該單消失、格數減一。

**Shared Must Read**:
- `specs/truth/features/ios-app/主管工作台/三類清單.feature` -> `功能: 三類清單`（9 個 Rule）
- `specs/truth/features/ios-app/主管工作台/dsl.md` -> `"{主管}" 已在 App 打開 "{清單}"`、`"{主管}" 剛送出 "{任務}" 的查房，結果畫面停在畫面上`、`"{主管}" 從結果畫面回到待查房清單`、`清單依序為 {清單}`、`清單上沒有 "{單}"`、`"{單}" 那一列標出日期 … 與時刻 …`、`"{單}" 那一列只顯示時刻 …`、`每一列都看得到房號與內容`、`每一列都看得到工務單號`、`"{單號}" 那一列標示 "{標籤}"`
- `specs/truth/features/ios-app/dsl.md` -> `"{員工}" 名下有以下任務：`（`完成時間`、操作者是別人時只建資料）、`已有以下工務單：`（新欄）、`現在時間是 "{時刻}"`、`畫面回到 "{清單}"，"{單}" 不在清單上`
- `truth-delta.md` -> `/dsl-refine` ADD `三類清單.feature`、MODIFY `ios/dsl.md` `名下有以下任務：`；`/api-plan` ADD `components/schemas/主管工作台.yaml`（`waitingSinceIsToday` 後端給布林、公共區域 `roomNo` 為 null）
- `ui/ui-plan.md` -> 介面 2「清單每一列要顯示什麼」的欄位表、「排序」「空狀態」「刷新時機」、畫面 2 `三類清單`
- `ui/queue.html`、`ui/manager.css`、`ui/manager.js` -> 三種類別的雛形（`?kind=inspection|review|verify`）
- `specs/truth/contracts/components/schemas/主管工作台.yaml` -> 三種 item 的欄位（`waitingSince`、`waitingSinceIsToday`、`isPublicArea`、`resolutionType`、`origin`、`redoCount`）
- `app/mobile/lib/features/manager/manager_queue_screen.dart`（T008）

**Boundary**:
- 排序由後端排好直接呈現，App 不重排；要不要標日期只看 `waitingSinceIsToday`，時刻直接用 `waitingSince` 字串裡的時區位移，不轉手機時區。
- 公共區域的列用 `title` 當地點、不顯示房號；`roomNo` 為 null 不得當成錯誤。
- 完修結果標籤用契約值對照白話（`fixed`＝已修好、`temporary`＝暫時處理、`cannot_fix`＝無法修復），無法修復用琥珀色；`origin=scheduled` 標「定期保養」。
- 回清單時重取 `getManagerQueues`，三格同步更新；不加即時推送。

**Test Scope**:
- `specs/truth/features/ios-app/主管工作台/三類清單.feature`

- [ ] T147 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T148 [BDD-REFACTOR] 在綠燈下讓三種類別共用同一個列元件，只以 item 型別決定第三行與標籤，不寫三份清單畫面

## Phase 4I: ADD Feature File - ios-app/主管工作台/查房.feature

**Goal**: 查房畫面逐項判定、全部判過才能送出、沒有備註欄；送出後結果畫面三種說法；項目集合不符時重新載入重判、單已被處理時回清單；沒有項目時可直接送出。

**Shared Must Read**:
- `specs/truth/features/ios-app/主管工作台/查房.feature` -> `功能: 查房畫面`（8 個 Rule）
- `specs/truth/features/ios-app/主管工作台/dsl.md` -> 查房相關 Given（`飯店目前設定的查房項目如下：`、`飯店目前沒有任何啟用的查房項目`、兩個 `已從待查房清單打開`、`飯店在 … 送出前新增了查房項目`、`"{任務}" 剛被 "{另一位主管}" 查完 …`）、When（`從待查房清單打開`、`把 … 判 …`、`把以下項目判定後送出：`、`送出查房`）、Then（`查房畫面列出以下項目 …`、`查房送出鈕為`、`查房畫面沒有備註欄`、`查房畫面說明目前沒有查房項目 …`、三句結果畫面、`查房畫面重新載入以下項目並清空判定：`）
- `specs/truth/features/ios-app/dsl.md` -> `後端已對 "{員工}" 送出推播 "{標題}"`、`後端沒有對 "{員工}" 送出任何推播`、`後端 "{房號}" 的房況為 "{房況}"`、`畫面顯示 "這張單已被處理"，並回到 "{清單}"，"{單}" 不在清單上`、`"{任務}" 在 App 顯示為 "{狀態}"`
- `truth-delta.md` -> `/dsl-refine` ADD `查房.feature`、ADD `ios/dsl.md` 四句 Then；`/api-plan` MODIFY `createInspections`、ADD 三個查房 schema、MODIFY `Error` 錯誤碼清單、NOOP `listSopItems`
- `ui/ui-plan.md` -> 介面 3「版面」「沒有任何啟用項目」「查房項目的來源」「送出與結果」「送出被拒要分辨兩種拒絕」「離線」、畫面 3 `查房畫面`、畫面 4 `查房結果`
- `ui/inspect.html`、`ui/inspect-result.html`、`ui/manager.css`、`ui/manager.js` -> 雛形全文（含 `?items=0`、303 第一次送出進重判狀態、`&offline=1`）
- `specs/truth/contracts/groups/現場作業/查房.yaml` -> `createInspections` 的 201／409／422
- `specs/truth/contracts/components/schemas/查房.yaml` -> `CreateInspectionRequest`、`InspectionOutcome.destination`
- `specs/truth/contracts/components/schemas/shared.yaml` -> `Error.code` `inspection_items_mismatch` 的 `context.currentItems`、`state_conflict`
- `specs/truth/contracts/groups/知識與通知/SOP與查房項目.yaml` -> `listSopItems` 的 `appliesTo` 與 `active`
- `app/mobile/lib/features/manager/inspection_screen.dart`、`inspection_result_screen.dart`（T008）、`app/mobile/lib/core/api_client.dart` 的 `createInspection`（T009）

**Boundary**:
- 兩種拒絕只靠 `Error.code` 分辨：`inspection_items_mismatch` 留在原畫面、項目換成 `context.currentItems`、判定清空、送出鈕回禁用、不再打 `listSopItems`；`state_conflict` 彈「這張單已被處理」回清單並重取。
- 結案還是退回、退回去了哪裡一律看 `InspectionOutcome.result` 與 `destination`，App 不看班表。
- 送出帶同一把 `Idempotency-Key`，重送不換鍵。
- 沒有備註欄、沒有照片；全部判過之前是禁用態，不是按了才報錯。
- 離線擋在送出之前、不入佇列（場景在 Phase 4M 驗，本 phase 不得讓它進佇列）。

**Test Scope**:
- `specs/truth/features/ios-app/主管工作台/查房.feature`

- [ ] T149 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T150 [BDD-REFACTOR] 在綠燈下把「這張單已被處理」對話框抽成共用元件（查房與 Phase 4J 的四個工務單動作同一個），查房項目列與兩段切換抽成獨立元件

## Phase 4J: ADD Feature File - ios-app/工務單/主管視角.feature

**Goal**: 主管從任何入口打開工務單詳情都看到工務版為底的 `TicketView.manager`，只在等主管審核與已完修尚未驗收兩種狀態出現動作；核准、退回（必填原因，成功後推播報修人）、驗收一鍵；撞 409 顯示已被處理並回清單。

**Shared Must Read**:
- `specs/truth/features/ios-app/工務單/主管視角.feature` -> `功能: 工務單詳情的主管視角`（11 個 Rule，含六狀態場景大綱）
- `specs/truth/features/ios-app/工務單/dsl.md` -> 本輪 ADD 的三個 Given、四個 When、七個 Then，MODIFY 的 `工務單詳情顯示狀態摘要卡`、`工務單詳情顯示退回原因`，以及裁決段改寫後的「主管在 App 的工務單詳情核准、退回、驗收」
- `specs/truth/features/ios-app/dsl.md` -> `已有以下工務單：`、`後端已對 "{員工}" 送出推播 "{標題}"`、`後端 "{房號}" 的房況為 "{房況}"`、`畫面回到 "{清單}"，"{單}" 不在清單上`、`畫面顯示 "這張單已被處理"，並回到 …`
- `truth-delta.md` -> `/dsl-refine` ADD `主管視角.feature`、ADD `ios/工務單/dsl.md`、MODIFY 裁決段 L9、MODIFY 兩句 Then；`/api-plan` MODIFY `readRepairTicket` description、MODIFY `RepairTicket`、MODIFY `RepairTicketSummary`、MODIFY `Error`（`state_conflict` 補句）、MODIFY `PushPayload.event`、NOOP 三個動作端點、「破壞性變更」第三與第四點
- `ui/ui-plan.md` -> 介面 4 全段（狀態對照表、核准、退回、驗收、已被處理、離線、缺欄位表）、畫面 5 `工務單詳情（主管視角）`、畫面 6 `退回原因面板`
- `ui/ticket.html`、`ui/manager.css`、`ui/manager.js` -> 六種狀態的雛形（`?no=W-0201`、`W-0108`、`W-0202`、`W-0112`、`W-0204`、`W-0109`；`W-0110` 按驗收進已被處理）
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `readRepairTicket`、`approveRepairTicket`、`rejectRepairTicket`、`verifyRepairTicket`
- `specs/truth/contracts/components/schemas/工務單.yaml` -> `RepairTicket`（`resolutionType`、`doneAt`、`reviewRequestedAt`、`origin`、`verifiedBy`、`verifiedAt`、`rejectReason`）
- `app/mobile/lib/features/ticket/ticket_screen.dart`、`ticket_detail_engineering.dart`（主管版以此為底）、`app/mobile/lib/core/push_gateway.dart:175-186`（`case 'ticket'` 改先看角色）

**Boundary**:
- 不做第三種版面：`TicketView.manager` 沿用工務版的滿版標題、四步步驟條、狀態列與資訊卡，只換底部動作列；房務版與工務版的呈現不動（`工務視角.feature`、`工務單房務端生命週期.feature` NOOP，不得變紅）。
- 動作列只依 `status` 與 `verifiedAt` 決定：`pending_review` 顯示描邊「退回」加實心「核准」；`done` 且 `verifiedAt` 為 null 顯示實心「驗收」；其餘無動作。
- 退回原因去前後空白為空時送出鈕禁用；驗收不開面板、不填任何東西、畫面上沒有「驗收不通過」「退回」與備註欄。
- 四個動作撞 `state_conflict` 統一用 Phase 4I 抽出的「這張單已被處理」對話框，回來源清單並重取；成功後回對應清單，該單消失，首頁格數減一。
- 主管從推播、通知中心開同一張單走同一版（`push_gateway.dart` 的 `case 'ticket'` 改成先看角色）。
- 狀態列的「送出」時間用 `reviewRequestedAt`，完修結果用 `resolutionType` 對白話，「已驗收 · {主管}」用 `verifiedBy`。

**Test Scope**:
- `specs/truth/features/ios-app/工務單/主管視角.feature`

- [ ] T151 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T152 [BDD-REFACTOR] 在綠燈下把主管動作列與退回原因面板抽成獨立元件，`TicketView` 三個值的選版收在 `ticket_screen.dart` 一處，`push_gateway.dart` 與通知中心不各自判

## Phase 4K: MODIFY Feature File - ios-app/推播/點開推播的去處.feature

**Goal**: 去處為首頁的推播依角色進首頁（工務員工進工務首頁、主管冷啟動進主管首頁）；主管點開帶部門的待認領推播切到該部門並標亮；主管未核准推播停在該工務單詳情。

**Shared Must Read**:
- `specs/truth/features/ios-app/推播/點開推播的去處.feature` -> Rule「去處為首頁的推播，點開後依角色進首頁」、「主管點開去處為待認領且帶部門的推播，切到該部門並標亮那張單」、「修繕任務（工務單）的推播點開後停在工務單詳情，不是任務頁」新增的一例、背景加王主管、既有例子「310 已改派給小陳」畫面改「房務首頁」
- `specs/truth/features/ios-app/推播/dsl.md` -> `"{員工}" 收到一則推播 "{標題}"，去處為 "{去處}"`、`App 停在 "{畫面}"`（兩句 MODIFY 後的值集與必查）
- `specs/truth/features/ios-app/dsl.md` -> `"{員工}" 上次在待認領停在 "{部門}"`、`已有以下工務單：`
- `truth-delta.md` -> `/dsl-refine` ADD `點開推播的去處.feature` 三處、MODIFY 兩句推播 DSL、MODIFY 場景大綱例子；`/api-plan` NOOP `PushTarget`、MODIFY `PushPayload.event`（`repair_rejected` target `kind=ticket`）
- `ui/ui-plan.md` -> 介面 1「推播開工務單詳情也要跟著改」、介面 5「推播」段、畫面 7 的 `?dept=engineering&highlight=306`
- `ui/pool.html` -> 推播帶部門與標亮的雛形
- `specs/truth/contracts/components/schemas/shared.yaml` -> `PushTarget`（`kind=pool` 的 `dept` 必填、`kind=home`）
- `app/mobile/lib/core/push_gateway.dart:161-196`（`case 'pool'`、`case 'ticket'`、`case 'home'`）、`app/mobile/lib/core/landing.dart`

**Boundary**:
- `case 'home'` 改呼叫 `landingShellFor`，順帶修掉工務員工被導進房務首頁的既有錯誤；冷啟動（`getInitialMessage`）同一條路。
- `case 'pool'` 對主管多一步：先把 `PushTarget.dept` 寫進本機部門選擇，再切分頁並標亮；對員工行為不變。
- 標題不驗權威文案（待認領推播文案沿用 truth 句型，急件文案不斷言）。
- 既有 Rule 與場景（直達去處、完修提示恢復、修繕推播開詳情）是 NOOP，Test Scope 是整份檔案，不得變紅。

**Test Scope**:
- `specs/truth/features/ios-app/推播/點開推播的去處.feature`

- [ ] T153 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T154 [BDD-REFACTOR] 在綠燈下確認冷啟動與前景兩條推播路徑都只經過 `push_gateway.dart` 一個 switch，`home`、`pool`、`ticket` 三個分支都不再直接看 `dept`

## Phase 4L: MODIFY Feature File - ios-app/待認領/待認領列表.feature

**Goal**: 主管的待認領可在房務與工務兩個池之間切換，第一次為房務、之後停在上次的部門（只存這台手機），單卡與該部門員工一致但沒有搶單鈕。

**Shared Must Read**:
- `specs/truth/features/ios-app/待認領/待認領列表.feature` -> Rule「主管第一次在這台手機打開待認領時顯示房務的池」、「主管看到的單卡與該部門員工一致，但沒有搶單鈕」、「主管可以在待認領切到另一個部門的池」、「重開 App 後待認領停在上次停留的部門」；既有場景「房務部全員停用，王主管看到空狀態」不動
- `specs/truth/features/ios-app/待認領/dsl.md` -> `"{主管}" 第一次在這台手機打開待認領`、`"{主管}" 在待認領切到 "{部門}"`、`待認領目前的部門為 "{部門}"`、`"{任務}" 的單卡沒有搶單鈕`、MODIFY 的 `"{員工}" 在 App 打開房務待認領`、`待認領列表依序為 {房號清單}`
- `specs/truth/features/ios-app/dsl.md` -> `"{員工}" 上次在待認領停在 "{部門}"`、`已有以下工務單：`
- `truth-delta.md` -> `/dsl-refine` ADD `ios/待認領/dsl.md`、ADD `待認領列表.feature` 4 個 Rule、MODIFY 兩句、ADD `ios/dsl.md` Given；`/api-plan` NOOP `listDeptPool` 與 `PushTarget`
- `ui/ui-plan.md` -> 介面 5 全段（版面、記住部門、查詢、實作切分）、畫面 7 `主管待認領`
- `ui/pool.html`、`ui/manager.css`、`ui/manager.js` -> 雛形全文
- `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `listDeptPool` 的 `dept` 參數與 `dept_required`
- `app/mobile/lib/features/manager/manager_pool_screen.dart`（T008）、`app/mobile/lib/features/pool/pool_screen.dart:75-83`、`pool_widgets.dart`、`app/mobile/lib/features/engineering/eng_pool_screen.dart`

**Boundary**:
- 不複製兩份畫面：把 `PoolScreen` 與 `EngPoolScreen` 的列表與單卡抽成「給定部門與是否可認領」的元件，主管待認領是外層容器持有部門並往下傳 `canClaim: false`。
- 兩個池都明帶 `dept` 呼叫 `listDeptPool`；部門選擇只存 `SharedPreferences` 一把鍵（`housekeeping`／`engineering`，沒有紀錄視為 `housekeeping`），不進後端。
- 單卡 id 沿用員工端（`pool.card.{roomNo}`、`engPool.item.{單號}`），只是沒有 `.claim-button` 節點；分類磚、空狀態、未接標籤都與員工端一致。
- 既有 Rule 是 NOOP，Test Scope 是整份檔案，員工端場景不得變紅。

**Test Scope**:
- `specs/truth/features/ios-app/待認領/待認領列表.feature`

- [ ] T155 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T156 [BDD-REFACTOR] 在綠燈下確認員工端兩個待認領畫面已改用抽出的共用元件，沒有留下第三份列表實作

## Phase 4M: MODIFY Feature File - ios-app/系統層/離線模式與連線異常.feature

**Goal**: 主管的查房送出、核准、退回、驗收在離線時一律擋在送出之前、顯示離線提示、不留在手機上等補送；恢復連線後不補送、單的狀態不變。

**Shared Must Read**:
- `specs/truth/features/ios-app/系統層/離線模式與連線異常.feature` -> Rule「離線時開始、認領、延後、開新工務單，以及主管的查房送出、核准、退回、驗收一律被擋，並顯示離線提示」（第二個場景大綱四列）、「離線被擋下的主管動作不留在手機上等補送」、「恢復連線後，離線時被擋下的主管動作不會補送，單的狀態不變」
- `specs/truth/features/ios-app/系統層/dsl.md` -> `"{員工}" 在 App 離線時嘗試 "{動作}"`（MODIFY）、`"{員工}" 已在 App 登入，停在首頁`、`"{員工}" 在離線時嘗試過 "{動作}" 並被擋下`
- `specs/truth/features/ios-app/dsl.md` -> `手機目前離線`（MODIFY）、`"{員工}" 的手機恢復連線`、`畫面提示 "{提示}"`、`這次操作沒有留在手機上等補送`（搬入）、`"{任務}" 在 App 顯示為 "{狀態}"`（MODIFY，工務單詳情）
- `truth-delta.md` -> `/dsl-refine` ADD `離線模式與連線異常.feature` 三處、ADD `ios/系統層/dsl.md`、MODIFY Rule L19 標題、MODIFY `在 App 離線時嘗試`、MODIFY `手機目前離線`、MODIFY `"{任務}" 在 App 顯示為`、「DSL row 搬移」表
- `ui/ui-plan.md` -> 介面 3「離線」段、介面 4「離線」段（四個動作按下時先檢查連線，不送請求、不入離線佇列）
- `ui/inspect.html`、`ui/ticket.html` -> `&offline=1` 的離線擋下雛形
- `app/mobile/lib/core/system_status.dart` -> `offlineOnlyCompleteMessage`、`app/mobile/lib/core/connection_status.dart`、Phase 4I 與 4J 的送出路徑

**Boundary**:
- 四個主管動作都沿用員工端同一句「目前離線，只能完成進行中的單」與同一個連線檢查入口，不得各寫一份。
- 擋下的動作不得進離線佇列；`這次操作沒有留在手機上等補送` 會驗待同步筆數沒增加，光顯示提示不會變綠。
- 恢復連線後畫面與後端都維持原狀：等主管審核的單仍等主管審核，待查房的單仍待查房。
- 既有 Rule（離線完成、待同步筆數、連線異常列）是 NOOP，Test Scope 是整份檔案，不得變紅。

**Test Scope**:
- `specs/truth/features/ios-app/系統層/離線模式與連線異常.feature`

- [ ] T157 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T158 [BDD-REFACTOR] 在綠燈下確認開始、認領、延後、開單與主管四動作共用同一段「離線擋在送出之前」的判斷，沒有複製貼上

## Phase 4N: ui 雛形 quality gate

**Goal**: 產品對齊本輪回追到的 plan-side 雛形家族。`Test Scope` 全綠不能代替本題。

**Read**:
- `ui/ui-plan.md` -> 視覺方向、介面 1～5、畫面與流程 1～7、互動與假資料原則、狀態與資訊揭露、驗證與錯誤回饋、前端實作切分建議
- `ui/index.html`、`ui/queue.html`、`ui/inspect.html`、`ui/inspect-result.html`、`ui/ticket.html`、`ui/pool.html`、`ui/manager.css`、`ui/manager.js`
- `app/mobile/lib/core/landing.dart`、`app/mobile/lib/features/shell/main_shell.dart`、`app/mobile/lib/features/manager/`、`app/mobile/lib/features/ticket/`、`app/mobile/lib/features/pool/`、`app/mobile/lib/features/engineering/eng_pool_screen.dart`、`app/mobile/lib/core/push_gateway.dart`

**Boundary**:
- 不改 truth、不加 feature、不重跑 GREEN 當通過條件。
- 重點比對：三格磚的大小與三種顏色（待查房品牌色、待審核琥珀色、待驗收品牌深色）、清單右欄等寬數字與跨日日期的琥珀色、查房項目判定後整列變色（通過淡綠、不通過淡紅）與底部固定送出列的進度字、每個畫面只有一顆實心主按鈕（查房送出、驗收、核准實心；退回描邊；回到清單文字按鈕）、工務單詳情六種狀態的狀態列文字與動作列、退回原因面板的標題與佔位字、「這張單已被處理」對話框的副文、待認領的兩段切換與沒有認領鈕、三類空狀態的三句說明與副文、名下沒單的說明與副文。
- 用語以 truth 為準：完修結果顯示「已修好／暫時處理／無法修復」，契約值 `fixed`／`temporary`／`cannot_fix`；畫面上不得出現 `pending_review` 這類內部值或錯誤碼。

- [ ] T159 subagent review (ui 雛形 quality gate)
