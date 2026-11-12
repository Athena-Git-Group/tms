# Tasks: 003-manager-sign-off-queues（主管在 App 上處理待查房與待驗收，取消主管審核）

**Plan Package**: `specs/plans/003-manager-sign-off-queues`
**Core Inputs**: `spec.md`, `plan.md`, `truth-delta.md`, `ui/ui-plan.md`, `ui/*.html`, `ui/manager.css`, `ui/manager.js`, `specs/truth/techstack.md`, `specs/truth/contracts/**`, `specs/truth/data/data-model.dbml`, `specs/truth/features/backend/**`, `specs/truth/features/ios-app/**`

本輪沒有 `research.md`（`/technical-research` 未執行，本輪不引入新技術）。
本檔承接 `truth-delta.md` 三節：`/api-plan`（收回審核三支端點與相關欄位、`verifyRepairTicket` 帶結果、`RepairVerificationOutcome`、`no_action_needed`、主管待辦依主管種類、`createInspections` 擋工務主管）、`/data-plan`（`closed`、收回 `pending_review`／`rejected`、`last_verify_failed_*`、`verification_return`、`repair_verification_failed`、既有資料處置）、`/dsl-refine`（後端與 iOS App 的句型與 feature 改動、三支改名的 feature）。

## Task Binding Contract

- 每個**開發任務**都必須對應 `truth-delta.md` 的 ADD / MODIFY / DELETE / NOOP 語意。
- 本輪沒有新增技術，**省略 Setup phase**。
- Phase 2 `Foundational` 只建立後續實作與測試共用的元件、入口、fixture、helper 與落點骨架；每則寫「只做／不做」。
- Phase 3 `Test Alignment & Implementation` 在寫產品碼之前，把本輪所有受影響 DSL 的自動化測試對齊最新版 truth。
- Truth 參照使用 `specs/truth/**`；plan 參照才使用本 plan package 內相對路徑。
- Feature `Shared Must Read` 除 feature、同模組 DSL、對應 `truth-delta` 列外，還必須列入回追到的 planner 產物。
- **起點是工作樹現況，不是 HEAD。** 舊審核版 `/implement` 已做到 159 題中的 152 題，程式碼都在工作樹、未 commit（後端主管待辦含待審核、查房 `InspectionOutcome` 與 409／422、`resolution_type`（V22）、`repair_rejected` 推播、可撥時鐘；App 登入分流、主管首頁三格、三類清單、查房、待認領）。本檔每一題都以「現況已經有什麼」為前提，只寫要改的差距。

**測試指令**（每個 Feature phase 的 Test Scope 用這兩組跑）：
- 後端（`tms/`，JDK 25、Testcontainers、Docker Desktop）：
  ```
  export JAVA_HOME=/Users/Caresys/Library/Java/JavaVirtualMachines/openjdk-25.0.2/Contents/Home
  export DOCKER_HOST=unix:///Users/Caresys/.docker/run/docker.sock
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
  cd tms && mvn -q test -Dtest=RunTruthFeaturesTest -Dcucumber.features=../specs/truth/features/backend/<模組>/<檔名>.feature
  ```
  undefined 檢查加 `-Dcucumber.execution.dry-run=true`。帶 `-Dcucumber.features` 時會多 1 個 CucumberTestEngine discovery 假影 error，忽略。Cucumber Expression 中文句含 `/` 要寫 `\\/`。
- App BDD（Appium、iOS 模擬器）：先以撥時鐘旗標啟動 8080 後端
  `TMS_DB_URL=jdbc:postgresql://localhost:5433/tms TMS_TEST_CLOCK_ENABLED=true JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./tms/mvnw -f tms/pom.xml spring-boot:run`，
  再跑 `FEATURE=<ios-app 下路徑，例如 主管工作台/主管首頁.feature> bash /private/tmp/claude-501/-Users-Caresys-IdeaProjects-tms-mvp/3c9b6266-e9f4-42c8-9264-f2d79669856a/scratchpad/run-4i.sh`（內含 build、install、Appium、收 WDA）。後端產品碼改過就要重啟 8080。模擬器同一時間只能跑一支。
  App 的 undefined 檢查：`cd app/bdd && JAVA_HOME="$(/usr/libexec/java_home -v 25)" mvn -q test -Dtest=RunModuleFeatureTest -Dsurefire.excludeJUnit5Engines=cucumber -Dcucumber.execution.dry-run=true -Dcucumber.features=../../specs/truth/features/ios-app/<路徑>`。

## Scope Note（停止範圍、判斷與回交）

1. **沒有回交 `/dsl-refine` 的範圍。** 盤點 20 支本輪 feature 的每一步，都剛好對到一個 `dsl.md` 句型，沒有重複定義，也沒有找不到定義的句；DELETE 的句型在所有 feature 裡都已不再出現。acceptance 旅程由 truth feature 承接，含 `truth-delta.md`「刻意不承接」五條的處理。
2. **`contracts` 與 `data` 不加 quality gate。** 回追到的 `specs/truth/contracts/**` 與 `specs/truth/data/data-model.dbml` 都在 `specs/truth/**`，以 Feature 的 Test Scope 全綠當閘門。只有 `ui/` 家族是 plan-side 產物，在最後一個 App 消費者之後加一題 gate（Phase 4V）。
3. **`features/acceptance/` 不列入 `Shared Must Read`。** 它是 `/spec-by-example` 的 plan-side 產物，回追順序不經過它。
4. **truth-delta 的兩條待決已有裁決，照裁決寫：** ①每次完修都推播報修人，完修推播的冪等鍵要帶事件序號（`repair/RepairService.java:424` 現在是 `"repair_resolved:" + ticketTaskId`）；②工務人員「班已結束」只看請假與飯店所有班別時段，DSL 以 `Clock` 落在所有班別時段之外表達，不另建工務排班。驗收不通過原因不設長度上限。
5. **不改 V17、V22。** `V22__repair_ticket_resolution_type_and_repair_rejected_event.sql` 雖然還沒 commit，仍視為已發布；收回與加值一律寫在新的 V23。
6. **NOOP 的句不出題（工作樹已對齊或語意沒變）：** 後端 `飯店有以下員工：`、`"{員工}" 判定 "{任務}" 查房全部項目通過`、`"{員工}" 收到以下推播：`、`"{員工}" 查看主管待辦`、`待驗收清單不包含 "{單號}"`、`待驗收清單中 "{單號}" 的完修結果為 "{結果}"、來源為 "{來源}"`、三句開單 When（`中斷…並開立以下工務單：`、`從…開立以下工務單：`、`中斷…並以已準備的照片開立水電工務單`）、`"{員工}" 撤回 "{單號}"`、`"{主管}" 判定 "{任務}" 查房如下：`（共 11 句，都只呼叫 API 或比對通用欄位，新行為是產品碼）；iOS `飯店有以下員工：`、`這次操作沒有留在手機上等補送`、`後端已對 "{員工}" 送出推播 "{標題}"`、`「我名下的單」依序列出 {房號清單}，卡片與房務首頁的任務卡一致`、`完修回報的送出鈕為 "{按鈕狀態}"`、`"{任務}" 的單卡沒有搶單鈕`（共 6 句）。另外 `"{員工}" 在 App 打開 "{單號}" 的工務單詳情`（When）與 T055 的 Given 共用 `RepairContext` 的開啟路由，T055 修好即一併對齊，不另出題。說明文字類的 MODIFY（`be/dsl.md` 部門用語對照、`be/工務單/dsl.md` 模組說明與刪除「truth 領先現況」段、`be/推播/dsl.md` 模組說明、`ios/工務單/dsl.md` 與 `ios/主管工作台/dsl.md` 模組說明）沒有句型可對齊；其中對照表有落地碼的（`Terms.java` 狀態對照）併入 T008。
7. **`repair_tickets.origin` 欄位在 truth 早就有、資料庫還沒有**（不是 003 的改動）。`待驗收清單中…來源為` 沿用現行由 `tasks.source` 換算的做法，本輪不補欄位。
8. **示範資料不是 truth，但 FR-041 要求列為任務**，放 Foundational T002；它不是任何 Feature 的 Test Scope，驗法是 seed 跑完後王主管登入兩格、陳主管登入一格都不是 0。
9. **兩句不在 truth-delta、但工作樹 stepdef 會讓本輪劇本紅的句，照 `[BDD-ALIGN]` 出題：** `ios/dsl.md` 的 `已有以下工務單：`（T054；ios 表寫「同後端共用 DSL 的同名句型」，後端那列本輪 MODIFY）與 `ios/系統層/dsl.md` 的 `"{員工}" 已在 App 登入，停在首頁`（T109；主管分支寫死等待審核格）。
10. **刪除的產品行為（審核三動作、待審核清單、`repair_rejected`）放在後端 Phase 4J 與 App Phase 4U 的 `[CODE-REMOVE]`。** 前面的 GREEN 為了讓 Test Scope 綠，可能已經順手拿掉一部分（例如 `counts` 剛好是表格的格子就得拿掉 `review`）；CODE-REMOVE 負責清掉剩下的、確認不殘留。

## Phase 2: Foundational

**Goal**: 只建立後續要用的 migration、示範資料、測試共用 helper、後端與 App 的型別與方法落點、App BDD 的 fixture 殼。不寫驗收不通過的判定、不寫依主管種類的查詢、不寫任何畫面行為。

- [X] T001 新增 Flyway migration `V23`，收回審核狀態並加入結案、驗收不通過的欄位與值
  - Read:
    - `truth-delta.md` -> `/data-plan`「既有資料處置」四段、MODIFY `tasks.state`、`tasks.pool_source`、`repair_tickets.resolution_type`、`notifications.event`，DELETE `reject_reason`，ADD `last_verify_failed_*`
    - `specs/truth/data/data-model.dbml` -> `Table tasks` 的 `state`、`pool_source` CHECK；`Table repair_tickets` 的 `resolution_type`、`last_verify_failed_*` 與複合外鍵；`Table notifications` 的 `event` 十八值
    - `tms/src/main/resources/db/migration/` -> `V17__repair_ticket_review_and_verification.sql`（`pending_review`／`rejected`、`reject_reason` 的出處）、`V22__repair_ticket_resolution_type_and_repair_rejected_event.sql`（兩個 CHECK 的現行寫法）
    - `tms/src/main/java/com/athena/tms/repair/RepairService.java:497-520` -> `ticketView` 直接 SELECT `rt.reject_reason`
  - 只做：一支 `V23__...sql`，順序為先拿掉舊 CHECK、處理資料列、再加新 CHECK。①`type=repair` 且 `state IN (pending_review, rejected)` 轉 `pool`、`assignee_id`=NULL、`pool_source=staff_report`、`reminder_stage`=NULL；`type=repair` 且 `state=done` 且 `repair_tickets.verified_at` 有值轉 `closed`；換上十值 `chk_tasks_state`；`chk_tasks_pool_source` 加 `verification_return`。②`repair_tickets` 刪 `reject_reason`；加 `last_verify_failed_reason`、`last_verify_failed_by`、`last_verify_failed_at` 三個 nullable 欄與 `(tenant_id, last_verify_failed_by)` 複合外鍵；`chk_repair_tickets_resolution_type` 加 `no_action_needed`。③刪 `notifications.event='repair_rejected'` 的列與 `outbox` 裡尚未送出、事件為 `repair_rejected` 的 push，再換上以 `repair_verification_failed` 取代 `repair_rejected` 的十八值 CHECK。④同題把 `RepairService` 的 `ticketView` SELECT 拿掉 `rt.reject_reason`（`rejectReason` 先回 null），讓既有讀取在欄位刪掉後不壞。
  - 不做：不改 V17、V22；不動 `task_events`（資料庫對 `type` 沒有 CHECK）；不回填舊完修單的 `resolution_type`；不刪 `RepairService.reject` 寫 `reject_reason` 的 UPDATE、不改任何狀態轉移或讀取端（Phase 4A～4J）。

- [X] T002 調整 `scripts/seed-demo.sh`，對齊 FR-041
  - Read:
    - `spec.md` -> FR-041、「舉例說明用的人物與示範資料」（陳主管 `mg2`）
    - `truth-delta.md` -> `/data-plan` NOOP `scripts/seed-demo.sh 所需欄位` 那一列
    - `scripts/seed-demo.sh` -> 檔頭說明 :37-39、帳號 :117 一帶與結尾帳號清單 :492、202 窗簾單 `tasks` :333-339 與 `repair_tickets` W-0110 :366、取號計數器 :369、W-0108 :304／:374-389、W-0109 :308／:380-382、檢查查詢 :476-483
  - 只做：①加工務主管陳主管（`mg2`、`role=manager`、`dept=engineering`），結尾帳號清單補上；②拿掉阿芳報修、等主管審核的 202 窗簾掛勾單（`tasks` 列、`repair_tickets` W-0110 列、前一列結尾逗號改分號、取號計數器改回 109、待審核張數檢查），房務待認領裡櫃檯轉單的 202 照舊；③W-0109 改 `state=closed`、`verified_by` 王主管、`verified_at` 有值；④新增一張以不需處理結案的工務單（`state=closed`、`resolution_type=no_action_needed`、沒有 completion 照片、`verified_by` 為 NULL，`task_events` 一筆 `completed` 帶 `resolutionType=no_action_needed`）；⑤檢查查詢改成王主管看待查房與待驗收、陳主管只看待驗收，兩人都不是 0。
  - 不做：不改 W-0108（維持 `done`、`fixed`、一張完修照片）、小美的 301、昨天完成的 205 待查房單、王主管名下的 403 與 404；不加 ui-plan 雛形裡 FR-041 沒要求的單；不碰 truth。

- [X] T003 建立後端測試共用的驗收送出 helper、房況快照與「班別時段之外」時刻
  - Read:
    - `truth-delta.md` -> `/dsl-refine` 後端 MODIFY `"{主管}" 驗收通過 "{單號}"`、ADD 兩句驗收不通過 When、ADD `"{工務}" 今天的班已經結束`、ADD `"{房號}" 的房況沒有改變`
    - `specs/truth/contracts/components/schemas/工務單.yaml` -> `VerifyRepairTicketRequest`、`RepairVerificationOutcome`
    - `tms/src/test/java/com/athena/tms/bdd/support/InspectionFixtures.java`（送 body＋`Idempotency-Key` 的寫法）、`support/LastOperationGuard.java`、`support/MutableClock.java`、`ScenarioWorld.java`（`lastInspectionResponse` 的保存方式）
    - `tms/src/main/java/com/athena/tms/service/InspectionService.java:218` -> `cleanerUnavailable` 用的飯店班別時段
  - 只做：`support/` 加驗收 helper（例如 `VerifyFixtures.send(world, managerName, ticketNo, result, reason)`），送 `{result, reason}` 並帶新的 `Idempotency-Key`，回傳整個 HTTP 回應；`ScenarioWorld` 加 `lastVerifyResponse` 的保存與讀取；`LastOperationGuard` 加「指定房號的 `rooms` 房況快照」欄位殼；`MutableClock` 旁加一個算「飯店所有班別時段之外的下一個時刻」的函式。
  - 不做：不改任何既有 stepdef（Phase 3）；不寫結果、去處或房況的斷言。

- [X] T004 建立後端驗收與結案的型別與常數落點
  - Read:
    - `truth-delta.md` -> `/api-plan` MODIFY `verifyRepairTicket`、ADD `VerifyRepairTicketRequest`／`RepairVerificationOutcome`、MODIFY `Error`（`verification_reason_required`）；`/data-plan` 讀取端清單 `common/codes/TaskState.java:15`
    - `specs/truth/contracts/components/schemas/工務單.yaml`、`shared.yaml` -> 兩個新 schema、`Error.code`
    - `tms/src/main/java/com/athena/tms/common/codes/`、`tms/src/main/java/com/athena/tms/dto/`、`tms/src/main/java/com/athena/tms/repair/RepairController.java:178-181`
  - 只做：`common/codes/` 加 `closed`（`TaskState`）、`no_action_needed`（完修結果）、`repair_verification_failed`（推播與事件）、`verification_return`（進池來源）、`verification_reason_required`（錯誤碼）的常數；`dto/` 定義 `VerifyRepairTicketRequest`（`result`、`reason`）與 `RepairVerificationOutcome`（`taskId`、`ticketNo`、`roomNo`、`isPublicArea`、`result`、`destination`、`assigneeName`、`reason`、`decidedBy`、`decidedAt`）兩個 record。
  - 不做：不改 `RepairController.verify` 的簽章與回應、不寫不通過分支、不改任何讀取端認得 `closed`（Phase 4A～4I）；不刪審核相關碼（Phase 4J）。

- [X] T005 擴充 App `api_client.dart` 的驗收方法與工務單新欄位解析
  - Read:
    - `truth-delta.md` -> `/api-plan` MODIFY `RepairTicket`、`VerifyQueueItem`、ADD `EngineeringTicket.verificationReturnReason`／`PoolItem.verificationReturnReason`、ADD `RepairVerificationOutcome`；「破壞性變更清單」App 那一點
    - `specs/truth/contracts/components/schemas/工務單.yaml`、`主管工作台.yaml`、`員工工作台.yaml`
    - `app/mobile/lib/core/api_client.dart` -> `RepairTicketDetail`（:1076 起）、`ManagerQueues`（:1289 起）、`VerifyQueueItem`（:1481、:1497 的 `?? 'fixed'`）、`completeTask`（:181-203）
  - 只做：加 `verifyRepairTicket(taskId, result, reason, idempotencyKey)` 回 `RepairVerificationOutcome` 模型；`RepairTicketDetail` 加 `lastVerifyFailedReason`、`lastVerifyFailedBy`、`lastVerifyFailedAt`、`viewerIsAssignee`；`VerifyQueueItem.resolutionType` 改可為 null、加 `wasVerificationFailed`；`EngineeringTicket`、`PoolItem` 加 `verificationReturnReason`；`ManagerQueues` 對 `inspection`、`review` 兩鍵缺席時解析不炸（缺席視為沒有這一類）。
  - 不做：不刪 `ReviewQueueItem`、`resubmitRepairTicket`、`rejectReason`、`reviewRequestedAt`（Phase 4U）；不接畫面、不改 `completeTask` 的照片規則（Phase 4P）。

- [X] T006 建立 App 的主管種類判定、新 id 與驗收畫面元件殼
  - Read:
    - `ui/ui-plan.md` -> 介面 1 判定順序、介面 4 不通過面板與結果卡、「前端實作切分建議」
    - `specs/truth/features/ios-app/主管工作台/dsl.md`、`specs/truth/features/ios-app/工務單/dsl.md` -> 本輪 ADD／MODIFY 句實作語意點名的 id（`managerTicket.pass-button`、`managerTicket.fail-button`、不通過面板、結果卡與回清單鈕、工務待認領的驗收退回標籤、步驟條第四步、工務主管寬磚）
    - `app/mobile/lib/core/landing.dart`、`app/mobile/lib/features/manager/manager_ids.dart`、`app/mobile/lib/features/ticket/ticket_screen.dart:28-29`
  - 只做：`core/landing.dart` 加主管種類純函式（`role=manager` 且 `dept=engineering` 為工務主管，其餘主管為房務主管）；`manager_ids.dart` 加上述新 id 常數；`features/ticket/` 建不通過面板與驗收結果卡兩個空殼元件。
  - 不做：不刪核准、退回的 id（Phase 4U）；不把主管外殼依種類分流、不接資料、不寫版面（Phase 4K～4T）。

- [X] T007 建立 App BDD 的主管首頁等待 helper 與 `BackendChannel` 驗收 fixture 殼
  - Read:
    - `truth-delta.md` -> `/dsl-refine` iOS MODIFY `"{員工}" 在 App 打開首頁`、ADD 三個驗收 Given、ADD `"{工務}" 今天的班已經結束`
    - `app/bdd/src/test/java/com/athena/app/bdd/BackendChannel.java` -> `verifyRepairTicket`（:1040，送空 body）、撥時鐘（:347-366）
    - 「等三格含 review」重複的 6 處：`steps/common/CommonWhenSteps.java:39`、`steps/system/SystemGivenSteps.java:41`、`steps/push/PushThenSteps.java:147`、`steps/account/AccountThenSteps.java:177`、`steps/manager/ManagerGivenSteps.java:160`、`steps/system/SystemWhenSteps.java` 的 `openManagerQueueRow`
  - 只做：加一個共用 helper（例如 `ManagerHomeWait.await(world, staffName)`），依該員工後端的 `role`／`dept` 決定要等哪幾格（房務主管等待查房與待驗收、工務主管只等待驗收）；`BackendChannel` 加方法殼：送驗收帶 `result`／`reason`、撥時鐘到飯店所有班別時段之外。
  - 不做：不把 6 處切到 helper（各句的 Phase 3 task 自己切）；不改 `createRepairTicket` 與任何 stepdef 的斷言（Phase 3）。

## Phase 3: Test Alignment & Implementation

**Goal**: 把本輪 Feature 用到的 DSL 自動化測試對齊最新版 truth；含 truth-delta 有改的句，以及本輪 Feature 用到、尚無 stepdef 的句。不寫產品行為。起點是工作樹的 stepdef（舊審核版已做過一輪 ALIGN／RED），已對齊新 truth 的句列在 Scope Note 第 6 條，不出題。

**DSL 參照**:
- 每一句只屬於一個權威 `dsl.md`：同模組 `specs/truth/features/{介面}/{模組}/dsl.md`，或介面根 `specs/truth/features/{介面}/dsl.md`。不得掃其他模組。
- backend 的句分三處：介面根 `specs/truth/features/backend/dsl.md`（T008～T010）、`specs/truth/features/backend/工務單/dsl.md`（T011～T040）、`specs/truth/features/backend/主管工作台/dsl.md`（T041～T049）。
- ios-app 的句分七處：介面根 `specs/truth/features/ios-app/dsl.md`（T050～T054）、`specs/truth/features/ios-app/工務單/dsl.md`（T055～T088）、`specs/truth/features/ios-app/主管工作台/dsl.md`（T089～T104）、`specs/truth/features/ios-app/推播/dsl.md`（T105）、`specs/truth/features/ios-app/帳號與登入/dsl.md`（T106）、`specs/truth/features/ios-app/系統層/dsl.md`（T107～T109）。
- 本輪沒有 DSL row 只搬移權威位置。句型改名但位置不變的六句（`主管待辦的格子數字為：`、`"{工務}" 以完修結果 "{結果}" 完成 "{單號}"，處理說明 "{說明}"，不附照片`、`首頁的格子依序顯示：`、`每一列都看得到工務單號與完修結果`、`首頁沒有主管的格子`、`"{主管}" 在工務單詳情按通過`）是 `[BDD-ALIGN]`：改既有 stepdef 的句型與語意，不拆成 REMOVE 加 RED。
- 讀法：用 task title 的句型對到該檔那一列，以 `StepDef 實作語意`（ios-app 為 `App 實作語意（Appium）`）當作測試程式碼語意。Given／When 讀 `怎麼做`、`權威狀態落地`、`回寫`；Then 讀 `必查`（`呈現結果`、`權威狀態`、`再讀確認`）。
- `truth-delta.md` 只告訴這句是 ADD / MODIFY / DELETE。語意以 `dsl.md` 那一列為準，不得用 feature 措辭或舊 stepdef 自行發明。

**Markers**:
- `[BDD-ALIGN]`：`MODIFY`。既有 stepdef 還在，但語意是舊 truth（送空 body、還有 `pending_review`／`rejected`／`review` 分支、Data Table 少欄、狀態對照沒有已結案、句型名是舊的）。依 `dsl.md` 該列改測試。
- `[BDD-REMOVE]`：`DELETE`。此句已不是 truth。移除仍綁這句的 stepdef、只服務它的 helper 與 `BackendChannel` 方法，不得留下保護審核行為的測試。
- `[BDD-RED]`：`ADD`，或本輪 Feature 用到、尚無 stepdef 的句。依 `dsl.md` 該列寫出 stepdef。完成時這句可被跑到，失敗只能是 assertion 或產品行為，不能是 undefined step。
- 三個 marker 都只動測試層，不寫產品碼。

**Shared Must Read**:
- `specs/truth/features/backend/dsl.md` -> 狀態用語對照、`已有以下工務單：`、`"{任務}" 的狀態為 "{狀態}"`、`這次操作被拒絕，錯誤碼為 "{錯誤碼}"`
- `specs/truth/features/backend/工務單/dsl.md` -> 模組說明「報修與驗收」與本題句型
- `specs/truth/features/backend/主管工作台/dsl.md` -> 模組說明與本題句型
- `specs/truth/features/ios-app/dsl.md` -> `已有以下工務單：`、`"{員工}" 在 App 打開首頁`、`"{任務}" 在 App 顯示為 "{狀態}"`、兩句回清單 Then
- `specs/truth/features/ios-app/工務單/dsl.md` -> 模組說明「三種視角對各狀態的呈現表」與本題句型
- `specs/truth/features/ios-app/主管工作台/dsl.md`、`推播/dsl.md`、`帳號與登入/dsl.md`、`系統層/dsl.md` -> 本題句型
- `truth-delta.md` -> `/dsl-refine` 後端與 iOS App 兩節，有對應 ADD / MODIFY / DELETE 的句
- 後端 stepdef：`tms/src/test/java/com/athena/tms/bdd/steps/repair/`、`steps/managerqueue/`、`steps/common/`、`tms/src/test/java/com/athena/tms/bdd/support/`（`TaskFixtures.java`、`Terms.java`、T003 的 helper）
- App stepdef：`app/bdd/src/test/java/com/athena/app/bdd/steps/repair/`、`steps/manager/`、`steps/common/`、`steps/push/`、`steps/account/`、`steps/system/`、`BackendChannel.java`、T007 的 `ManagerHomeWait`

**Boundary**:
- 一條 DSL 一個 task。
- 只改該句的 stepdef / assertion / 直接依賴的 helper。
- 不寫產品碼。ALIGN 改完的句在產品補上之前實跑仍會紅，這是預期；完成條件看 dry-run 沒有 undefined、斷言表達新語意。
- 刪 `BackendChannel`、`TaskFixtures` 裡呼叫產品審核方法的分支（例如 `TaskFixtures.java:365-375` 呼叫 `engine.rejectRepairTicket`）必須在 Phase 3 完成，Phase 4J／4U 刪產品碼時測試才編得過。
- 共用檔（`BackendChannel.java`、`CommonThenSteps.java`、`RepairContext.java`、`ScenarioWorld.java`）改動前先重新讀檔、只做最小追加、寫入後立即 `mvn -q -o test-compile`；不要重排或重寫別人的段落。
- review 啟動 subagent；本輪所有 Test Scope 不得再有 undefined step（後端與 App 各跑一次 dry-run），失敗只能是 assertion 或產品行為。有 issues 就修正再 review，直到沒有任何問題。通過前不解鎖 Phase 4。

**Parallel Hint**（依檔案衝突分組；組與組平行，組內依序）：
- 後端 B1（`steps/common/CommonThenSteps.java`、`support/Terms.java`）：T008、T009。
- 後端 B2（`steps/repair/RepairGivenSteps.java`、`support/TaskFixtures.java`）：T010 先做，再 T011～T015。
- 後端 B3（`steps/repair/RepairWhenSteps.java`）：T016～T024。
- 後端 B4（`steps/repair/RepairThenSteps.java`）：T025～T040。
- 後端 B5（`steps/managerqueue/ManagerQueueThenSteps.java`）：T041～T049。
- App A1（`steps/common/CommonWhenSteps.java`、`CommonThenSteps.java`）：T050～T053。
- App A2（`steps/repair/RepairGivenSteps.java`、`RepairContext.java`、`BackendChannel.java` 的工務單段）：T054 先做，再 T055～T060。
- App A3（`steps/repair/RepairWhenSteps.java`）：T061～T069。
- App A4（`steps/repair/RepairThenSteps.java`）：T070～T088。
- App A5（`steps/manager/*`）：T089～T104。
- App A6（`steps/push/`、`steps/account/`、`steps/system/`）：T105～T109。
- 十一組各派一個獨立 subagent；T110 等全部回來再啟動 subagent 來 review。

### 後端：介面根 `specs/truth/features/backend/dsl.md`

- [X] T008 [P] [BDD-ALIGN] `"{任務}" 的狀態為 "{狀態}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/support/Terms.java:33-39`、`steps/common/CommonThenSteps.java:189-202`
  - 依「狀態用語對照」：刪「等主管審核」「主管未核准」，加「已結案＝`closed`」；「已完修」是等主管驗收。

- [X] T009 [P] [BDD-ALIGN] `這次操作被拒絕，錯誤碼為 "{錯誤碼}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/common/CommonThenSteps.java:88、102-124`
  - 對照表加 `verification_reason_required`→422、`forbidden`→403，刪 `reject_reason_required`。

- [X] T010 [P] [BDD-ALIGN] `已有以下工務單：`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairGivenSteps.java:95-338`、`support/TaskFixtures.java:365-375`、`support/LastOperationGuard.java:23`
  - 刪 `送出時間`、`退回原因` 欄與 `reject_reason` INSERT、`pending_review`／`rejected` 分支（含呼叫 `engine.rejectRepairTicket`）；狀態加已結案與待開始；`處理結果` 加不需處理（完修照片預設 0）；已結案（非不需處理）寫 `closed`＋`verified_*`＋`repair_verified` 事件；加 `不通過原因`、`不通過判定人`、`不通過時間` 三欄，寫 `last_verify_failed_*` 與一筆 `repair_verification_failed`，退回重修中的單清空完修欄位，待認領時 `pool_source=verification_return`。

### 後端：`specs/truth/features/backend/工務單/dsl.md`

- [X] T011 [P] [BDD-ALIGN] `"{主管}" 已先驗收通過 "{單號}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairGivenSteps.java:438`
  - 改用 T003 helper 帶 `{result: passed}`；落地為 `closed`。

- [X] T012 [P] [BDD-RED] `"{主管}" 已先以原因 "{原因}" 判定 "{單號}" 驗收不通過`

- [X] T013 [P] [BDD-RED] `"{工務}" 今天的班已經結束`

- [X] T014 [P] [BDD-RED] `"{單號}" 已由 "{工務}" 以不需處理結案`

- [X] T015 [P] [BDD-REMOVE] `"{員工}" 已撤回 "{單號}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairGivenSteps.java:453`

- [X] T016 [P] [BDD-REMOVE] `"{主管}" 核准 "{單號}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairWhenSteps.java:204-220`

- [X] T017 [P] [BDD-REMOVE] `"{主管}" 以原因 "{原因}" 退回 "{單號}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairWhenSteps.java:244-254、265-275`（helper `postReject` 一併刪）

- [X] T018 [P] [BDD-REMOVE] `"{主管}" 退回 "{單號}" 但沒有寫原因`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairWhenSteps.java:256-263`

- [X] T019 [P] [BDD-REMOVE] `"{員工}" 補資料再送出 "{單號}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairWhenSteps.java:334-349`

- [X] T020 [P] [BDD-ALIGN] `"{主管}" 驗收通過 "{單號}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairWhenSteps.java:276-292`
  - body 改為 `{result: passed}`（T003 helper），回應存進 `lastVerifyResponse`；Javadoc 拿掉「狀態值仍是 done」。

- [X] T021 [P] [BDD-RED] `"{主管}" 以原因 "{原因}" 判定 "{單號}" 驗收不通過`

- [X] T022 [P] [BDD-RED] `"{主管}" 判定 "{單號}" 驗收不通過但沒有寫原因`

- [X] T023 [P] [BDD-ALIGN] `"{工務}" 附 {張數} 張完修照片、處理說明 "{說明}" 完成 "{單號}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairWhenSteps.java:351-361`
  - body 明帶 `resolutionType: fixed`，照片是這一次上傳的。

- [X] T024 [P] [BDD-ALIGN] `"{工務}" 以完修結果 "{結果}" 完成 "{單號}"，處理說明 "{說明}"，不附照片`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairWhenSteps.java:394-401`（舊句型 `"{工務}" 不附完修照片完成 "{單號}"`）
  - 改句型；body 帶 `resolutionType` 四選一與 `resolutionNote`、空 `photoIds`；送出前先 `repairCountGuard.capture()` 與 T003 的房況快照。

- [X] T025 [P] [BDD-REMOVE] `"{單號}" 的退回原因為 "{原因}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairThenSteps.java:416-431`

- [X] T026 [P] [BDD-REMOVE] `"{單號}" 開始等主管的時間為現在`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairThenSteps.java:533-591、716-725`（只有這句在用的 `managerJwt` 一併刪）

- [X] T027 [P] [BDD-ALIGN] `"{單號}" 已由 "{主管}" 驗收`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairThenSteps.java:433-452`
  - `tasks.state` 改斷 `closed`；加 `repair_verified` 事件與 `readRepairTicket.state` 再讀確認。

- [X] T028 [P] [BDD-ALIGN] `"{單號}" 的驗收人只有 "{主管}" 一人，驗收只記下一次`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairThenSteps.java:514`
  - 加斷 `state=closed` 與 `repair_verification_failed` 為 0 筆。

- [X] T029 [P] [BDD-ALIGN] `"{單號}" 尚未驗收`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairThenSteps.java:463`
  - 加斷 `state` 不是 `closed`、沒有 `repair_verified` 事件、`verified_at` 為 null。

- [X] T030 [P] [BDD-RED] `"{單號}" 的驗收不通過只記下一次`

- [X] T031 [P] [BDD-RED] `這次驗收的結果為 "{結果}"，去處為 "{去處}"`

- [X] T032 [P] [BDD-RED] `"{單號}" 最近一次驗收不通過的原因為 "{原因}"，判定人為 "{主管}"`

- [X] T033 [P] [BDD-RED] `"{單號}" 的完修結果為 "{結果}"`

- [X] T034 [P] [BDD-RED] `系統沒有為 "{單號}" 建立追蹤單`
  - 可重用 `RepairThenSteps` 的 `assertNoRepairTicketCreatedSinceGuard`。

- [X] T035 [P] [BDD-RED] `"{房號}" 的房況沒有改變`
  - 讀 T003 的房況快照。

- [X] T036 [P] [BDD-RED] `工務待認領中 "{單號}" 帶驗收退回原因 "{原因}"`

- [X] T037 [P] [BDD-RED] `主管待辦的任何一類都不包含 "{單號}"`

- [X] T038 [P] [BDD-ALIGN] `工務待認領包含 "{單號}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairThenSteps.java:203-219`
  - 拿掉「沒有主管審核事件」的 `manager_review` 檢查。

- [X] T039 [P] [BDD-ALIGN] `"{房號}" 未結案的 "{類別}" 工務單只有 {張數} 張`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairThenSteps.java:316-326`
  - 排除條件改 `NOT IN ('done','closed','cancelled')`。

- [X] T040 [P] [BDD-ALIGN] `"{工務}" 的工務視角今日進度為：`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairThenSteps.java:652`
  - 呈現結果對 `getMyToday.engineeringSummary`；加權威狀態重算，今天已完修＝今天完修且現在 `done` 或 `closed`。

### 後端：`specs/truth/features/backend/主管工作台/dsl.md`

- [X] T041 [P] [BDD-ALIGN] `主管待辦的格子數字為：`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/managerqueue/ManagerQueueThenSteps.java:25-37、68、95-108`（舊句型 `主管待辦的三格數字為：`）
  - 改句型；`counts` 的鍵必須剛好是表格列出的格子；刪「待審核→review」與 recount 的 review 分支；待驗收重算改為 `state=done` 且 `resolution_type IS DISTINCT FROM 'no_action_needed'`。

- [X] T042 [P] [BDD-RED] `主管待辦沒有待查房這一類`

- [X] T043 [P] [BDD-ALIGN] `待驗收清單依序為 {單號清單}`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/managerqueue/ManagerQueueThenSteps.java:165`
  - `requireFields` 加 `wasVerificationFailed`；不再斷 `verified_at` 為空，改斷完修結果不是 `no_action_needed`；同一時刻依房號再依單號。

- [X] T044 [P] [BDD-RED] `待驗收清單中 "{單號}" 標為重修後再驗`

- [X] T045 [P] [BDD-REMOVE] `待審核清單依序為 {單號清單}`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/managerqueue/ManagerQueueThenSteps.java:140、359-372`（helper `lastEnteredPendingReview` 一併刪）

- [X] T046 [P] [BDD-REMOVE] `待審核清單不包含 "{單號}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/managerqueue/ManagerQueueThenSteps.java:160`

- [X] T047 [P] [BDD-ALIGN] `"{單}" 在清單上標為不是今天開始等`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/managerqueue/ManagerQueueThenSteps.java:217、285-290`
  - `anyQueueRow` 拿掉 `review`，回應沒有 `inspection` 鍵時（工務主管）不得丟例外。

- [X] T048 [P] [BDD-ALIGN] `"{單}" 在清單上標為今天開始等`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/managerqueue/ManagerQueueThenSteps.java:230`
  - 與 T047 共用 `anyQueueRow`，T047 先做。

- [X] T049 [P] [BDD-ALIGN] `這次查詢被拒絕，因為呼叫者不是主管`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/managerqueue/ManagerQueueThenSteps.java:243`
  - 加斷 `code`=`forbidden`。

### App：介面根 `specs/truth/features/ios-app/dsl.md`

- [X] T050 [P] [BDD-ALIGN] `"{員工}" 在 App 打開首頁`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/common/CommonWhenSteps.java:33-39`
  - 主管分支改用 T007 的 `ManagerHomeWait`，依主管種類等格子。

- [X] T051 [P] [BDD-ALIGN] `"{任務}" 在 App 顯示為 "{狀態}"`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/common/CommonThenSteps.java:80-86、144、259`
  - `TICKET_STATUS_TEXT` 刪等主管審核、主管未核准，加已結案與退回重修；主管看工務單時，結果卡停在畫面上要先進主管視角詳情再讀狀態列。

- [X] T052 [P] [BDD-ALIGN] `畫面回到 "{清單}"，"{單}" 不在清單上`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/common/CommonThenSteps.java:91-95、326`
  - `QUEUE_KEY` 刪待審核清單；Javadoc 改為按通過、查房結果畫面與驗收結果卡回清單。

- [X] T053 [P] [BDD-ALIGN] `畫面顯示 "這張單已被處理"，並回到 "{清單}"，"{單}" 不在清單上`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/common/CommonThenSteps.java:350、420-445`
  - `assertProcessedExactlyOnce` 刪 review 分支；verify 分支改為「單已離開 `done`、驗收或不通過恰好一次」，涵蓋先不通過後按通過；加驗不通過面板一起收起。

- [X] T054 [P] [BDD-ALIGN] `已有以下工務單：`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairGivenSteps.java:34-40、152-158、180-252`、`BackendChannel.java:163-179、947-1012、1021、1105、1245-1260、1470`
  - 對齊後端同名句：狀態對照刪等主管審核、主管未核准，加待開始、已結案；處理結果加不需處理；加 `不通過原因`、`不通過判定人`、`不通過時間` 三欄；有驗收人時落地 `closed`；刪 `rejectRepairTicket`、送出時間與 `repair_resubmitted` 回推、`insertTaskAtCreatableState` 的 `pending_review`。

### App：`specs/truth/features/ios-app/工務單/dsl.md`

- [X] T055 [P] [BDD-ALIGN] `"{員工}" 已在 App 打開 "{單號}" 的工務單詳情`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairGivenSteps.java:377`、`RepairContext.java:99-102、128-141`、`RepairWhenSteps.java:106-109`
  - 主管入口刪 `pending_review`→review；`done` 一律走待驗收清單，不再看 `verified_at`。

- [X] T056 [P] [BDD-ALIGN] `"{單號}" 剛被 "{主管}" 驗收，"{員工}" 的畫面還沒更新`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairGivenSteps.java:390`、`BackendChannel.java:1040`
  - 改帶 `{result: passed}`，後端已結案。

- [X] T057 [P] [BDD-RED] `"{單號}" 剛被 "{主管}" 以原因 "{原因}" 判定驗收不通過，"{員工}" 的畫面還沒更新`

- [X] T058 [P] [BDD-RED] `"{工務}" 今天的班已經結束`

- [X] T059 [P] [BDD-RED] `"{主管}" 剛送出 "{單號}" 的驗收不通過，結果卡停在畫面上`

- [X] T060 [P] [BDD-REMOVE] `"{單號}" 剛被 "{員工}" 撤回，"{主管}" 的畫面還沒更新`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairGivenSteps.java:402`、`BackendChannel.java:1049`（`withdrawRepairTicket` 只有這句在用，一併刪）

- [X] T061 [P] [BDD-REMOVE] `"{主管}" 在工務單詳情按核准`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairWhenSteps.java:305`

- [X] T062 [P] [BDD-REMOVE] `"{主管}" 在工務單詳情按退回，寫下原因 "{原因}" 並送出`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairWhenSteps.java:319`

- [X] T063 [P] [BDD-REMOVE] `"{主管}" 在工務單詳情打開退回面板，不寫原因`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairWhenSteps.java:333、352`（helper `openRejectSheet` 與 `RepairContext.java:38-42` 的核准、退回、退回面板 id 一併刪）

- [X] T064 [P] [BDD-ALIGN] `"{主管}" 在工務單詳情按通過`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairWhenSteps.java:345`（舊句型 `"{主管}" 在工務單詳情按驗收`）、`RepairContext.java:40`
  - 改句型；按鈕 id 改 `managerTicket.pass-button`；成功落地為已結案。

- [X] T065 [P] [BDD-RED] `"{主管}" 在工務單詳情按不通過，寫下原因 "{原因}" 並送出`

- [X] T066 [P] [BDD-RED] `"{主管}" 在工務單詳情打開不通過面板，不寫原因`

- [X] T067 [P] [BDD-RED] `"{主管}" 從驗收結果卡回到待驗收清單`

- [X] T068 [P] [BDD-ALIGN] `"{工務}" 在工務單詳情完修，回報結果 "{結果}"，附 {張數} 張照片`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairWhenSteps.java:36-39、184`
  - `RESOLUTION_TYPE_BY_LABEL` 加不需處理＝`no_action_needed`；張數可為 0。

- [X] T069 [P] [BDD-ALIGN] `"{工務}" 在工務單詳情打開完修回報，選結果 "{結果}"，附 {張數} 張照片`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairWhenSteps.java:212`
  - 同 T068 的對照，T068 先做。

- [X] T070 [P] [BDD-REMOVE] `工務單詳情顯示退回原因 "{原因}"`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairThenSteps.java:754-780`

- [X] T071 [P] [BDD-REMOVE] `工務單詳情顯示撤回按鈕`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairThenSteps.java:789`（`工務單詳情沒有撤回按鈕` :413 保留）

- [X] T072 [P] [BDD-REMOVE] `工務單詳情顯示「補資料再送出」按鈕`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairThenSteps.java:801`

- [X] T073 [P] [BDD-REMOVE] `工務單詳情沒有驗收不通過、退回與備註欄`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairThenSteps.java:972`

- [X] T074 [P] [BDD-REMOVE] `退回面板的送出鈕為 "{按鈕狀態}"`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairThenSteps.java:1003`

- [X] T075 [P] [BDD-REMOVE] `後端沒有收到這次退回`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairThenSteps.java:1033`

- [X] T076 [P] [BDD-ALIGN] `工務單詳情顯示狀態摘要卡 "{摘要卡文案}"`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairThenSteps.java:317-394、1118-1123、1157-1163`
  - 刪等主管審核（`reviewRequestedAt`）、主管未核准（`rejectReason`）、「已驗收 · {主管}」斷 `done` 三支；依模組說明三視角表補已修好 · 等主管驗收、退回重修（對 `lastVerifyFailedAt`）、已結案 · {驗收人}驗收（`closed`＋`verifiedBy`）、已結案 · 不需處理。

- [X] T077 [P] [BDD-ALIGN] `工務單詳情的主管動作為 "{動作}"`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairThenSteps.java:830`
  - 值改為「通過、不通過」（pass、fail 兩顆）與「無」；只看 `state=done`，不看 `verifiedAt`。

- [X] T078 [P] [BDD-ALIGN] `工務單詳情的步驟條依序為 "{步驟清單}"，目前在 "{目前步驟}"`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairThenSteps.java:702`
  - 對照改為待開始＝`accepted`（含退回重修）、驗收＝`done`；已結案四步全完成交 T080。

- [X] T079 [P] [BDD-ALIGN] `"{單號}" 已由 "{主管}" 驗收，驗收時間為現在`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairThenSteps.java:1077`
  - 狀態改斷 `closed`。

- [X] T080 [P] [BDD-RED] `工務單詳情的步驟條第四步「驗收」為 "{完成狀態}"`

- [X] T081 [P] [BDD-RED] `工務單詳情顯示最近一次不通過的原因 "{原因}" 與判定人 "{主管}"`

- [X] T082 [P] [BDD-RED] `工務單詳情沒有任何主管審核的狀態或動作`

- [X] T083 [P] [BDD-RED] `工務單詳情沒有通過或不通過的動作`

- [X] T084 [P] [BDD-RED] `工務待認領中 "{單號}" 標示驗收退回，原因 "{原因}"`

- [X] T085 [P] [BDD-RED] `不通過面板的送出鈕為 "{按鈕狀態}"`

- [X] T086 [P] [BDD-RED] `後端沒有收到這次驗收不通過`

- [X] T087 [P] [BDD-RED] `驗收結果卡說明 "{單號}" 退回重修，回到 "{處理人}" 名下`

- [X] T088 [P] [BDD-RED] `驗收結果卡說明 "{單號}" 退回重修，"{處理人}" 已下班，回到工務待認領`

### App：`specs/truth/features/ios-app/主管工作台/dsl.md`

- [X] T089 [P] [BDD-ALIGN] `"{主管}" 已在 App 打開主管首頁`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/manager/ManagerGivenSteps.java:55、160`
  - `ensureOnManagerHome` 改用 `ManagerHomeWait`。

- [X] T090 [P] [BDD-ALIGN] `"{主管}" 已在 App 打開 "{清單}"`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/manager/ManagerGivenSteps.java:35-38、61、230`
  - `QUEUE_KEYS` 刪待審核清單。

- [X] T091 [P] [BDD-ALIGN] `"{主管}" 在主管首頁點 "{格}"`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/manager/ManagerWhenSteps.java:33-37、59`
  - 刪待審核→review。

- [X] T092 [P] [BDD-ALIGN] `首頁的格子依序顯示：`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/manager/ManagerThenSteps.java:48、89、611-625`（舊句型 `三格依序顯示：`）、`ManagerContext.java:4、10`
  - 改句型；格子必須剛好是表格列出的這幾格，不再寫死三格。

- [X] T093 [P] [BDD-RED] `首頁只有待驗收一格，數字為 {數字}`

- [X] T094 [P] [BDD-RED] `首頁沒有待查房格，也沒有任何進待查房清單的入口`

- [X] T095 [P] [BDD-RED] `「我名下的單」列出 "{單號}"，卡片與工務首頁的工務單卡一致`

- [X] T096 [P] [BDD-RED] `"{主管}" 在「我名下的單」點 "{單號}"`

- [X] T097 [P] [BDD-RED] `App 開啟 "{單號}" 的工務單詳情，底部動作與工務員工相同`

- [X] T098 [P] [BDD-ALIGN] `"{格}" 格顯示 {數字}`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/manager/ManagerThenSteps.java:51-54、106`
  - 對照表刪待審核；工務主管的寬磚用同一個 id。

- [X] T099 [P] [BDD-ALIGN] `清單說明目前沒有等他處理的單，畫面不是空白`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/manager/ManagerThenSteps.java:57-60、387`
  - `EMPTY_TITLE_BY_QUEUE` 刪待審核那句。

- [X] T100 [P] [BDD-ALIGN] `「我名下的單」說明目前沒有名下的單`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/manager/ManagerThenSteps.java:170`
  - 工務主管改查 `engineeringSummary.tickets`、數工務單卡；房務主管照舊查 `todayTasks`。

- [X] T101 [P] [BDD-ALIGN] `主管首頁沒有問候列與今日進度`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/manager/ManagerThenSteps.java:154`
  - 另查工務首頁的問候、`engHome.progress.*`、急件與保養區都不在。

- [X] T102 [P] [BDD-ALIGN] `每一列都看得到工務單號與完修結果`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/manager/ManagerThenSteps.java:343`（舊句型 `每一列都看得到工務單號`）、`steps/manager/package-info.java:3`
  - 改句型；加完修結果標籤，對 `resolutionType`，null 時不畫。順手把 `package-info.java:3` 的舊檔名 `三類清單.feature` 改成 `主管清單.feature`。

- [X] T103 [P] [BDD-ALIGN] `"{單號}" 那一列標示 "{標籤}"`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/manager/ManagerThenSteps.java:63-66、364`
  - `TAG_FIELD_BY_LABEL` 加已修好＝`fixed`、重修後再驗＝`wasVerificationFailed`。

- [X] T104 [P] [BDD-ALIGN] `首頁沒有主管的格子`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/manager/ManagerThenSteps.java:189`（舊句型 `首頁沒有待查房、待審核、待驗收三格`）
  - 改句型；迴圈刪 review 與「待審核」文字，涵蓋工務首頁。

### App：推播、帳號與登入、系統層

- [X] T105 [P] [BDD-ALIGN] `App 停在 "{畫面}"`（`specs/truth/features/ios-app/推播/dsl.md`）
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/push/PushThenSteps.java:70、146-149`
  - 主管首頁分支改用 `ManagerHomeWait`。

- [X] T106 [P] [BDD-ALIGN] `"{員工}" 登入成功，進入 "{首頁}"`（`specs/truth/features/ios-app/帳號與登入/dsl.md`）
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/account/AccountThenSteps.java:145、153、176-180`
  - 主管首頁分支改用 `ManagerHomeWait`：房務主管兩格、工務主管一格。

- [X] T107 [P] [BDD-ALIGN] `"{員工}" 在 App 離線時嘗試 "{動作}"`（`specs/truth/features/ios-app/系統層/dsl.md`）
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/system/SystemWhenSteps.java:22-26、43-51、58、93-104`、`steps/taskexecution/TaskExecutionThenSteps.java:824-830`
  - 主管動作值改為送出查房結果、驗收通過、驗收不通過；清單列改依單號點（不再要求清單剛好一列）；不通過點 fail 鈕、走不通過面板並保留原因；刪核准、退回兩個 case 與 `REJECT_REASON`；兩處 Javadoc 跟著改。

- [X] T108 [P] [BDD-ALIGN] `"{員工}" 在離線時嘗試過 "{動作}" 並被擋下`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/system/SystemGivenSteps.java:67`
  - 委派 T107 的 When，T107 先做；值集跟著改。

- [X] T109 [P] [BDD-ALIGN] `"{員工}" 已在 App 登入，停在首頁`
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/system/SystemGivenSteps.java:35-41`
  - 主管分支改用 `ManagerHomeWait`（本句不在 truth-delta，但背景含陳主管時舊寫法會等 review 格逾時）。

- [X] T110 subagent review (phase quality gate)

## Phase 4A: MODIFY Feature File - backend/主管工作台/主管待辦清單.feature

**Goal**: `getManagerQueues` 依主管種類回類別：房務主管回待查房與待驗收，工務主管只回待驗收、沒有 `inspection` 兩個鍵；待驗收改為 `done` 且完修結果不是不需處理、含本人完修、每列帶 `wasVerificationFailed`、依最近一次完修時間排序；沒有待審核。

**Shared Must Read**:
- `specs/truth/features/backend/主管工作台/主管待辦清單.feature` -> `功能: 主管待辦清單`（14 個 Rule；檔名由 `主管待辦三類清單.feature` 改名）
- `specs/truth/features/backend/主管工作台/dsl.md` -> 模組說明、`"{員工}" 查看主管待辦`、`主管待辦的格子數字為：`、`主管待辦沒有待查房這一類`、待查房兩句、待驗收四句、兩句開始等標記
- `specs/truth/features/backend/dsl.md` -> `飯店有以下員工：`、`已有以下工務單：`、部門用語對照（房務主管、工務主管）
- `truth-delta.md` -> `/dsl-refine` MODIFY `主管待辦清單.feature` 與 `be/主管工作台/dsl.md` 各列；`/api-plan` MODIFY `getManagerQueues`／`ManagerQueues`、`VerifyQueueItem`／`QueueItemBase`、DELETE `ReviewQueueItem`；`/data-plan` MODIFY `tasks` Note 主管待辦判定段、`staff_accounts.dept` note
- `specs/truth/contracts/groups/現場作業/主管工作台.yaml` -> `getManagerQueues`（依主管種類、待驗收公式、`waitingSince`）
- `specs/truth/contracts/components/schemas/主管工作台.yaml` -> `ManagerQueues`（`required` 只剩 `counts`、`verify`）、`VerifyQueueItem`（`wasVerificationFailed`、`resolutionType` nullable）、`QueueItemBase`
- `specs/truth/data/data-model.dbml` -> `Table tasks` Note「主管待辦判定」段、`Table staff_accounts` 的 `dept` note
- `tms/src/main/java/com/athena/tms/service/ManagerQueueService.java:51-70、148-187`、`tms/src/main/java/com/athena/tms/dto/ManagerQueues.java`

**Boundary**:
- 主管種類由 `role` 與 `dept` 當場推得，不存欄位；工務主管的回應**省略** `inspection` 與 `counts.inspection`，不是回空陣列或 0。
- 待驗收公式用 `resolution_type IS DISTINCT FROM 'no_action_needed'`（舊單為 NULL 要收），不看 `verified_at`；排序鍵 `done_at`，同一時刻依房號再依單號；`wasVerificationFailed` 讀 `last_verify_failed_at` 有沒有值。
- 回應不再帶 `review` 與 `counts.review`；`counts` 直接取各陣列長度。`reviewQueue` 方法本體若還有人引用，留給 Phase 4J 刪。
- 待查房的公式、排除本人、跨日標記不動。

**Test Scope**:
- `specs/truth/features/backend/主管工作台/主管待辦清單.feature`

- [X] T111 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T112 [BDD-REFACTOR] 在綠燈下把主管種類判定收成一個函式（主管待辦與 Phase 4I 的查房權限共用），兩類查詢各一個方法，回應組裝只有一處決定省略哪些鍵

## Phase 4B: MODIFY Feature File - backend/主管工作台/主管待辦空清單與權限.feature

**Goal**: 沒有待辦時房務主管兩格為 0、工務主管唯一一格為 0；非主管呼叫回 403 `forbidden`。

**Shared Must Read**:
- `specs/truth/features/backend/主管工作台/主管待辦空清單與權限.feature` -> `功能: 主管待辦空清單與權限`（2 個 Rule）
- `specs/truth/features/backend/主管工作台/dsl.md` -> `主管待辦的格子數字為：`、`這次查詢被拒絕，因為呼叫者不是主管`
- `truth-delta.md` -> `/dsl-refine` MODIFY `主管待辦空清單與權限.feature`、MODIFY `這次查詢被拒絕…`；`/api-plan` MODIFY `getManagerQueues`、MODIFY `Error`（403 一律 `forbidden`）
- `specs/truth/contracts/groups/現場作業/主管工作台.yaml` -> `getManagerQueues` 的 403
- `specs/truth/contracts/components/schemas/shared.yaml` -> `Error.code` 的 `forbidden`

**Boundary**:
- 只處理 0 格與 403 的 `code`；不改 4A 的公式。

**Test Scope**:
- `specs/truth/features/backend/主管工作台/主管待辦空清單與權限.feature`

- [X] T113 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T114 [BDD-REFACTOR] 在綠燈下確認非主管的 403 與其他端點共用同一個 `forbidden` 出口，沒有另寫錯誤組裝

## Phase 4C: MODIFY Feature File - backend/工務單/主管驗收的狀態守衛.feature

**Goal**: `verifyRepairTicket` 改為一支帶 `{result, reason}`、回 `RepairVerificationOutcome`：只開放主管（含工務主管、含自己完修的單）、只接受 `done`、先到先決；通過 `done→closed` 並寫驗收人與時間；不通過原因空白 422；把 `done` 當已結束的讀取端一律認得 `closed`。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/主管驗收的狀態守衛.feature` -> `功能: 主管驗收的狀態守衛`（12 個 Rule；檔名由 `主管審核與驗收的狀態守衛.feature` 改名）
- `specs/truth/features/backend/工務單/dsl.md` -> 模組說明「報修與驗收」、`"{主管}" 驗收通過 "{單號}"`、`"{主管}" 判定 "{單號}" 驗收不通過但沒有寫原因`、`"{主管}" 已先驗收通過 "{單號}"`、`"{主管}" 已先以原因 "{原因}" 判定 "{單號}" 驗收不通過`、`"{單號}" 已由 "{工務}" 以不需處理結案`、`"{單號}" 已由 "{主管}" 驗收`、`"{單號}" 的驗收時間為現在`、`"{單號}" 的驗收人只有 "{主管}" 一人，驗收只記下一次`、`"{單號}" 尚未驗收`、`"{單號}" 的驗收不通過只記下一次`
- `specs/truth/features/backend/dsl.md` -> `已有以下工務單：`、`"{任務}" 的狀態為 "{狀態}"`、`這次操作被拒絕，錯誤碼為 "{錯誤碼}"`、`"{員工}" 收到以下推播：`
- `truth-delta.md` -> `/dsl-refine` MODIFY `主管驗收的狀態守衛.feature` 與上列句；`/api-plan` MODIFY `verifyRepairTicket`、ADD `VerifyRepairTicketRequest`／`RepairVerificationOutcome`、MODIFY `RepairTicket`、MODIFY `RepairTicketSummary.state`、MODIFY `Error`；`/data-plan` MODIFY `tasks.state`、`tasks` Note 工務單狀態轉移段與已結束狀態段、`repair_tickets.verified_*` note、`task_events.type` note、「把 `done` 當作已結束、要認得 `closed` 的讀取端」清單
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `verifyRepairTicket`（200／403／409／422、`Idempotency-Key`）、`readRepairTicket`
- `specs/truth/contracts/components/schemas/工務單.yaml` -> `VerifyRepairTicketRequest`、`RepairVerificationOutcome`、`RepairTicket`（`verifiedBy`／`verifiedAt`、`resolutionType`、`viewerIsAssignee`）
- `specs/truth/contracts/components/schemas/shared.yaml` -> `RepairTicketSummary.state`、`Error`（`state_conflict`、`verification_reason_required`、`forbidden`）
- `specs/truth/data/data-model.dbml` -> `Table tasks` Note 工務單狀態轉移段、已結束的狀態段；`Table repair_tickets` 的 `verified_by`、`verified_at`、`Note`
- `tms/src/main/java/com/athena/tms/repair/RepairController.java:178-181`、`repair/RepairService.java:846-881`、`service/TaskEngine.java:502-516`、T004 的兩個 record

**Boundary**:
- 通過與不通過共用同一道 `state='done'` 守衛 UPDATE；後到的一律 409 `state_conflict`，不覆寫 `verified_*`、不另推播。
- 非主管 403 `forbidden`；`no_action_needed` 結案的單已是 `closed`，驗收回 409。
- 回應一律 `RepairVerificationOutcome`；`Idempotency-Key` 必帶，接既有 `processed_ops` 回放。
- 不通過的落地與推播在 Phase 4D；本 phase 只需讓不通過的 422 與撞單成立。
- 讀取端認得 `closed`（`common/codes/TaskState.java:15`、`service/TaskExecutionService.java:295、822、837、871、1015-1019、1061、1087`、`repair/RepairService.java:182`）在本 phase 一次做完。
- `RepairService.read` 補 `viewerIsAssignee`、`lastVerifyFailed*`（讀欄位），拿掉 `reviewRequestedAt`、`rejectReason`。

**Test Scope**:
- `specs/truth/features/backend/工務單/主管驗收的狀態守衛.feature`

- [X] T115 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T116 [BDD-REFACTOR] 在綠燈下把「工務單已結束」收成 `TaskState` 一處的判斷（`done`、`inspected`、`closed`），讀取端不各自列值；驗收守衛與結果組裝留在 `RepairService.verify` 一處

## Phase 4D: ADD Feature File - backend/工務單/驗收不通過與重修.feature

**Goal**: 驗收不通過寫 `last_verify_failed_*`、清空完修三欄、只寫一筆 `repair_verification_failed`；原處理人在班回 `accepted` 並推播他，接不了回 `pool`（`pool_source=verification_return`）不推播；工務待認領帶驗收退回原因；重修再完修回到 `done`、推播報修人、仍要附這一次的照片。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/驗收不通過與重修.feature` -> `功能: 驗收不通過與重修`（9 個 Rule）
- `specs/truth/features/backend/工務單/dsl.md` -> `"{主管}" 以原因 "{原因}" 判定 "{單號}" 驗收不通過`、`"{工務}" 今天的班已經結束`、`這次驗收的結果為 "{結果}"，去處為 "{去處}"`、`"{單號}" 最近一次驗收不通過的原因為 "{原因}"，判定人為 "{主管}"`、`工務待認領中 "{單號}" 帶驗收退回原因 "{原因}"`、`工務待認領包含 "{單號}"`、`"{工務}" 附 {張數} 張完修照片、處理說明 "{說明}" 完成 "{單號}"`、`"{工務}" 以完修結果 "{結果}" 完成 "{單號}"，處理說明 "{說明}"，不附照片`
- `specs/truth/features/backend/dsl.md` -> `已有以下工務單：`（不通過三欄）、`"{員工}" 收到以下推播：`、`"{員工}" 沒有收到任何推播`、`"{任務}" 的狀態為 "{狀態}"`
- `truth-delta.md` -> `/dsl-refine` ADD `驗收不通過與重修.feature` 與上列句；`/api-plan` MODIFY `verifyRepairTicket`、MODIFY `PushPayload.event`、ADD `PoolItem.verificationReturnReason`／`EngineeringTicket.verificationReturnReason`、NOOP `PoolSource`；`/data-plan` 工務單狀態轉移段、ADD 下班或請假判定段、`last_verify_failed_*`、`tasks.done_at`、`tasks.pool_source`、`repair_tickets.resolution_type`／`resolution_note`、`repair_photos` Note、`task_events.payload`、`notifications.event`、「驗收不通過的存法」
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `verifyRepairTicket` 不通過段
- `specs/truth/contracts/components/schemas/工務單.yaml` -> `RepairVerificationOutcome`（`destination=pool` 仍帶 `assigneeName`）、`RepairTicket.completionPhotos`（只回最近一次不通過之後）
- `specs/truth/contracts/components/schemas/shared.yaml` -> `PushPayload.event` 的 `repair_verification_failed`（title 帶房號、單號與「驗收不通過」，body 是原因，`kind=ticket`）
- `specs/truth/contracts/components/schemas/員工工作台.yaml`、`groups/現場作業/員工工作台.yaml` -> `PoolItem`／`EngineeringTicket.verificationReturnReason`、`listDeptPool`
- `specs/truth/data/data-model.dbml` -> `Table tasks` Note 工務單狀態轉移段與下班或請假判定段；`Table repair_tickets` 三個 `last_verify_failed_*`；`Table notifications` Note
- `tms/src/main/java/com/athena/tms/service/InspectionService.java:218`（`cleanerUnavailable`，直接沿用）、`repair/RepairService.java:424`（完修推播冪等鍵）、`service/TaskExecutionService.java:556-589`（`PoolItem` 組裝）

**Boundary**:
- 不通過的兩條路在同一交易：清空 `done_at`、`resolution_type`、`resolution_note`，寫 `last_verify_failed_*`，一筆 `repair_verification_failed`（payload 帶 `reason`、`destination`、`assigneeId`）；`worked_sec`、`started_at`、`redo_count` 不動。
- 換人判定直接沿用 `InspectionService.cleanerUnavailable`，不為工務另建排班。
- 回 `pool` 時不推播原處理人，也不發 `pool_new_request`；推播只在回 `accepted` 時給原處理人。
- 完修推播報修人的冪等鍵改帶事件序號，重修再完修照樣推播。
- 完修照片「這一次請求至少 1 張」：舊照片不能抵用；`completionPhotos` 依 `last_verify_failed_at` 只回這一輪。
- `PoolSource` 不加 `verification_return`；工務池的來源照舊由 `tasks.source` 換算。

**Test Scope**:
- `specs/truth/features/backend/工務單/驗收不通過與重修.feature`

- [X] T117 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T118 [BDD-REFACTOR] 在綠燈下讓通過與不通過共用 4C 的守衛與 `RepairVerificationOutcome` 組裝，只分落地分支；推播 payload 與完修推播共用同一段 title／body／target 組裝

## Phase 4E: MODIFY Feature File - backend/工務單/完修與回到原任務.feature

**Goal**: 完修四選一；`no_action_needed` 不附照片也能完成、`doing→closed` 一步落地、保留處理說明、不建追蹤單、不改房況、同樣推播報修人、原任務的停住原因改為工務已修好且可以恢復；驗收通過後單已結案。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/完修與回到原任務.feature` -> `功能: 完修與回到原任務`（12 個 Rule）
- `specs/truth/features/backend/工務單/dsl.md` -> `"{工務}" 以完修結果 "{結果}" 完成 "{單號}"，處理說明 "{說明}"，不附照片`、`"{單號}" 已由 "{工務}" 以不需處理結案`、`"{單號}" 的完修結果為 "{結果}"`、`系統沒有為 "{單號}" 建立追蹤單`、`"{房號}" 的房況沒有改變`、`"{單號}" 已由 "{主管}" 驗收`
- `specs/truth/features/backend/dsl.md` -> `"{任務}" 的狀態為 "{狀態}"`、`"{員工}" 收到以下推播：`
- `truth-delta.md` -> `/dsl-refine` MODIFY `完修與回到原任務.feature` 與上列句；`/api-plan` MODIFY `CompleteTaskRequest`、`tasksComplete`／`TaskCompletion`；`/data-plan` 工務單狀態轉移段（`doing→closed`）、`repair_tickets.resolution_type`／`resolution_note`、`repair_photos` Note、`task_events.payload`（`completed` 就是結案事件）
- `specs/truth/contracts/groups/現場作業/任務.yaml` -> `tasksComplete`
- `specs/truth/contracts/components/schemas/任務.yaml` -> `CompleteTaskRequest`（`photoIds` `minItems` 0 與欄位說明）、`TaskCompletion.state`
- `specs/truth/data/data-model.dbml` -> `Table repair_tickets` 的 `resolution_type`、`resolution_note` 與 `Note` 的完修四選一；`Table task_events` 的 `payload`
- `tms/src/main/java/com/athena/tms/service/TaskEngine.java:177`、`service/TaskExecutionService.java:408-415`、`repair/RepairService.java:71、376、448`、`dto/CompleteTaskRequest.java:8`

**Boundary**:
- 完成的守衛 UPDATE 依完修結果直接寫 `closed` 或 `done`（完修結果傳進這句），不經 `done` 再轉。
- `no_action_needed` 不寫 `repair_verified`，`verified_*` 為 NULL；不建追蹤單、不轉停用、不改 `rooms`。
- 原任務恢復與工時接續沿用既有已修好的路徑，不另開分支。

**Test Scope**:
- `specs/truth/features/backend/工務單/完修與回到原任務.feature`

- [X] T119 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T120 [BDD-REFACTOR] 在綠燈下把四種完修結果的後續動作（追蹤單、停用、照片規則、落地狀態）收成一張對照，`resolveOnComplete` 不再用 if 串

## Phase 4F: MODIFY Feature File - backend/工務單/照片完整性與冪等送出.feature

**Goal**: 以已修好、暫時處理或無法修復完成時，這一次請求不附照片被拒 422 `completion_photo_required`。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/照片完整性與冪等送出.feature` -> Rule「以已修好、暫時處理或無法修復完成時，必須附完修照片」（場景大綱）；其餘 6 個 Rule 照舊
- `specs/truth/features/backend/工務單/dsl.md` -> `"{工務}" 以完修結果 "{結果}" 完成 "{單號}"，處理說明 "{說明}"，不附照片`
- `specs/truth/features/backend/dsl.md` -> `這次操作被拒絕，錯誤碼為 "{錯誤碼}"`
- `truth-delta.md` -> `/dsl-refine` MODIFY `照片完整性與冪等送出.feature`；`/api-plan` MODIFY `CompleteTaskRequest`
- `specs/truth/contracts/components/schemas/任務.yaml` -> `CompleteTaskRequest.photoIds` 欄位說明

**Boundary**:
- 照片檢查只在三種結果時要求；`no_action_needed` 的例外在 4E。既有 Rule 是 NOOP，不得變紅。

**Test Scope**:
- `specs/truth/features/backend/工務單/照片完整性與冪等送出.feature`

- [X] T121 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T122 [BDD-REFACTOR] 在綠燈下確認照片規則只寫在 4E 的對照一處

## Phase 4G: MODIFY Feature File - backend/工務單/開立工務單與急件.feature

**Goal**: 房務報修不分一般件或急件、送出後直接進工務待認領；報修送出後不出現在任何主管的待辦；開單時系統建立工務單並記下報修人。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/開立工務單與急件.feature` -> Rule「房務報修不分一般件或急件，送出後直接進工務待認領」、「報修送出後不經主管，不會出現在任何主管的待辦」、「開單時系統建立工務單並記下報修人」；其餘 6 個 Rule 照舊
- `specs/truth/features/backend/工務單/dsl.md` -> 三句開單 When、`工務待認領包含 "{單號}"`、`主管待辦的任何一類都不包含 "{單號}"`、`系統建立工務單 "{單號}"，房號 "{房號}"、報修人 "{員工}"`
- `truth-delta.md` -> `/dsl-refine` MODIFY `開立工務單與急件.feature`、MODIFY 三句開單 When；`/api-plan` MODIFY `createRepairTicket`／`CreateRepairTicketRequest.assigneeId`；`/data-plan` 工務單狀態轉移段（開單一律 `pool`）
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `createRepairTicket` 的去向說明
- `tms/src/main/java/com/athena/tms/repair/RepairService.java:611-616、658-680`（`sourceTaskId!=null && !isUrgent → pending_review` 那一支）

**Boundary**:
- 開單去向只剩「有 `assigneeId` 為 `accepted`，其餘 `pool`」；`pool_source` 設定照舊。
- 急件判定、排序、推播、降級是 NOOP，不得變紅。

**Test Scope**:
- `specs/truth/features/backend/工務單/開立工務單與急件.feature`

- [X] T123 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T124 [BDD-REFACTOR] 在綠燈下確認三條開單路徑共用同一個去向判定

## Phase 4H: MODIFY Feature File - backend/工務單/工務視角維修與開單.feature

**Goal**: 工務自開單沒有指定處理人時直接進工務待認領、不出現在任何主管待辦；工務視角今日進度的今天已完修算 `done` 或 `closed`。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/工務視角維修與開單.feature` -> Rule「工務開單時沒有指定處理人，單直接進工務待認領，不經主管」、「工務視角今日進度顯示手上張數、今天已完修與急件數」；其餘 9 個 Rule 照舊
- `specs/truth/features/backend/工務單/dsl.md` -> `"{工務}" 為 "{房號}" 開立以下工務單，不經過任何房務任務：`、`主管待辦的任何一類都不包含 "{單號}"`、`"{工務}" 的工務視角今日進度為：`
- `truth-delta.md` -> `/dsl-refine` ADD Rule、MODIFY `"{工務}" 的工務視角今日進度為：`；`/api-plan` MODIFY `getMyToday`／`MyToday.engineeringSummary`、`EngineeringTicket.state`
- `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `getMyToday`
- `specs/truth/contracts/components/schemas/員工工作台.yaml` -> `MyToday.engineeringSummary`（`completedToday`）、`EngineeringTicket`

**Boundary**:
- 讀取端認得 `closed` 已在 4C 做完；本 phase 只補這支 feature 揭露的缺口。既有 Rule 是 NOOP，不得變紅。

**Test Scope**:
- `specs/truth/features/backend/工務單/工務視角維修與開單.feature`

- [X] T125 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T126 [BDD-REFACTOR] 在綠燈下確認工務單列排序與今天已完修都用 4C 收斂的已結束判斷

## Phase 4I: MODIFY Feature File - backend/查房/逐項判定與退回去向.feature

**Goal**: 工務主管送出查房回 403 `forbidden`（message「你沒有查房的權限」），單仍待查房、清房者沒有收到推播。

**Shared Must Read**:
- `specs/truth/features/backend/查房/逐項判定與退回去向.feature` -> Rule「工務主管不能送出查房，即使單停在待查房」、「工務主管的查房送出被拒時，清房者沒有收到任何推播」；其餘 10 個 Rule 照舊
- `specs/truth/features/backend/查房/dsl.md` -> `"{主管}" 判定 "{任務}" 查房如下：`
- `specs/truth/features/backend/dsl.md` -> `"{員工}" 判定 "{任務}" 查房全部項目通過`、`這次操作被拒絕，錯誤碼為 "{錯誤碼}"`、`"{員工}" 沒有收到任何推播`
- `truth-delta.md` -> `/dsl-refine` ADD 兩個 Rule、MODIFY 兩句查房 When；`/api-plan` MODIFY `createInspections`
- `specs/truth/contracts/groups/現場作業/查房.yaml` -> `createInspections` 的 403
- `tms/src/main/java/com/athena/tms/controller/InspectionApiController.java:63-68`

**Boundary**:
- 權限檢查用 4A 收斂的主管種類函式，與自清自查、無權限角色共用 `forbidden`；不另立錯誤碼。既有 Rule 是 NOOP，不得變紅。

**Test Scope**:
- `specs/truth/features/backend/查房/逐項判定與退回去向.feature`

- [X] T127 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T128 [BDD-REFACTOR] 在綠燈下確認查房的三種 403 走同一個檢查出口

## Phase 4J: DELETE Feature / DSL Truth - 後端主管審核（核准、退回、補資料再送出、待審核清單、`repair_rejected`）

**Goal**: 移除後端支援主管審核的產品碼，確認新版 truth 成立、沒有測試繼續保護審核行為。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/重複報修補照片與撤回.feature` -> `功能: 重複報修、補照片與撤回`（已刪 Rule「等主管審核時，報修人也可以撤回工務單」）
- `specs/truth/features/backend/工務單/dsl.md` -> `"{員工}" 撤回 "{單號}"`；DELETE 的審核三動作與兩句 Then
- `specs/truth/features/backend/主管工作台/dsl.md` -> DELETE 的待審核兩句
- `truth-delta.md` -> `/dsl-refine` DELETE 各列；`/api-plan` DELETE 各列與「破壞性變更清單」後端兩點；`/data-plan` 讀取端清單末「收回相關、與 `done` 無關但同一批要動」
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `withdrawRepairTicket`（只剩 `pool→cancelled`）
- 過期產品碼：`tms/src/main/java/com/athena/tms/repair/RepairController.java:159-176`（approve、`RejectRepairTicketRequest`、reject、resubmit）；`repair/RepairService.java:60-70`（`LAST_REVIEW_SUBMISSION_AT`）、`:257`、`:348-350`、`:784-844`（approve、reject 含 `reject_reason_required` 與 `repair_rejected` 推播、resubmit）；`service/TaskEngine.java:30-36`（`CREATABLE_STATES` 的 `pending_review`）、`:403-423`（撤回 `IN ('pool','pending_review')`）、`:458-500`（三個轉移，含寫 `repair_rejected` 事件）；`service/ManagerQueueService.java:10、57、60、64-66、105-146`（`reviewQueue`）；`dto/ManagerQueues.java:19-53`（`review`、`Counts.review`、`ReviewQueueItem`）；`service/TaskExecutionService.java:1015` 說明

**Boundary**:
- 只移除審核行為與它的型別、常數、說明；不動驗收、開單去向（4G 已改）與撤回 `pool` 的既有行為。
- 前面 GREEN 已經拿掉的部分只確認不殘留；`tms/src/main` 對 `pending_review|rejected|repair_rejected|reject_reason|review_requested|reviewRequested|resubmit|approve` 的 grep 應只剩 migration 檔。

**Test Scope**:
- `specs/truth/features/backend/工務單/重複報修補照片與撤回.feature`
- `specs/truth/features/backend/工務單/開立工務單與急件.feature`
- `specs/truth/features/backend/工務單/主管驗收的狀態守衛.feature`
- `specs/truth/features/backend/主管工作台/主管待辦清單.feature`

- [X] T129 [CODE-REMOVE] 移除後端主管審核的端點、服務方法、狀態轉移、`repair_rejected` 推播與事件、待審核清單與型別
- [X] T130 [REGRESSION] 跑 Test Scope，確認新版 truth 成立

## Phase 4K: MODIFY Feature File - ios-app/帳號與登入/登入與登出守衛.feature

**Goal**: 登入與強制改密完成後，房務主管進兩格的主管首頁、工務主管進一格的主管首頁；分流入口仍只有 `landingShellFor`，主管外殼內依主管種類決定格子。

**Shared Must Read**:
- `specs/truth/features/ios-app/帳號與登入/登入與登出守衛.feature` -> `功能: 登入與登出守衛`（11 個 Rule；本輪改的是登入與強制改密兩個場景大綱各加陳主管一列、強制改密大綱補 `角色` 欄）
- `specs/truth/features/ios-app/帳號與登入/dsl.md` -> `"{員工}" 登入成功，進入 "{首頁}"`
- `specs/truth/features/ios-app/dsl.md` -> `飯店有以下員工：`
- `truth-delta.md` -> `/dsl-refine` MODIFY `登入與登出守衛.feature`、MODIFY `"{員工}" 登入成功，進入 "{首頁}"`、MODIFY `ios/dsl.md` `飯店有以下員工：`
- `ui/ui-plan.md` -> 介面 1「進 App 的首頁分流與主管種類判定」、畫面 1 `主管首頁`
- `ui/index.html`（王主管）、`ui/index.html?as=mg2`（陳主管）、`ui/manager.css`、`ui/manager.js`
- `app/mobile/lib/core/landing.dart`（T006 的主管種類函式）、`app/mobile/lib/features/shell/main_shell.dart:238-248`（`ManagerShell`）

**Boundary**:
- 進主管外殼的判定不變（看 `role`）；主管種類只決定外殼內首頁的格子，由外殼往下傳，不在登入與改密畫面各判一次。
- 首頁的格子內容屬 Phase 4L；本 phase 只需格子集合依種類正確。

**Test Scope**:
- `specs/truth/features/ios-app/帳號與登入/登入與登出守衛.feature`

- [X] T131 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T132 [BDD-REFACTOR] 在綠燈下確認主管種類只在 `core/landing.dart` 判一次，主管外殼、首頁與推播都讀同一個值

## Phase 4L: MODIFY Feature File - ios-app/主管工作台/主管首頁.feature

**Goal**: 房務主管兩格（待查房、待驗收）、工務主管一格寬磚（待驗收）；格子數字等於清單列數、0 照常顯示；房務主管「我名下的單」沿用房務任務卡、工務主管改用工務單卡，點開是工務單詳情且底部動作與工務員工相同；沒有問候列與今日進度；員工首頁沒有主管的格子。

**Shared Must Read**:
- `specs/truth/features/ios-app/主管工作台/主管首頁.feature` -> `功能: 主管首頁`（11 個 Rule）
- `specs/truth/features/ios-app/主管工作台/dsl.md` -> 模組說明、`"{主管}" 已在 App 打開主管首頁`、`"{主管}" 在主管首頁點 "{格}"`、`"{主管}" 在「我名下的單」點 "{單號}"`、`首頁的格子依序顯示：`、`首頁只有待驗收一格，數字為 {數字}`、`"{格}" 格顯示 {數字}`、`「我名下的單」依序列出…`、`「我名下的單」列出 "{單號}"，卡片與工務首頁的工務單卡一致`、`App 開啟 "{單號}" 的工務單詳情，底部動作與工務員工相同`、`「我名下的單」說明目前沒有名下的單`、`主管首頁沒有問候列與今日進度`、`首頁沒有主管的格子`、`底部分頁依序是 {分頁清單}`
- `specs/truth/features/ios-app/dsl.md` -> `飯店有以下員工：`、`"{員工}" 名下有以下任務：`、`已有以下工務單：`、`"{員工}" 在 App 打開首頁`
- `truth-delta.md` -> `/dsl-refine` MODIFY `主管首頁.feature`、`ios/主管工作台/dsl.md` 各列、MODIFY `ios/dsl.md` 打開首頁；`/api-plan` MODIFY `getManagerQueues`、MODIFY `getMyToday`／`MyToday.engineeringSummary`
- `ui/ui-plan.md` -> 介面 2「主管首頁與主管清單」、畫面 1 `主管首頁`、「前端實作切分建議」（`TmsCountTile` 寬版、工務單卡抽到 `core/widgets/`）
- `ui/index.html`、`ui/index.html?as=mg2`、`ui/manager.css`、`ui/manager.js`
- `specs/truth/contracts/components/schemas/主管工作台.yaml` -> `ManagerQueues.counts`（工務主管沒有 `inspection`）
- `specs/truth/contracts/components/schemas/員工工作台.yaml` -> `MyToday.todayTasks`、`engineeringSummary.tickets`
- `app/mobile/lib/features/manager/manager_home_screen.dart:16、27-31、120-160`、`manager_queues_store.dart`、`app/mobile/lib/core/widgets/tms_count_tile.dart`、`app/mobile/lib/features/engineering/`（工務首頁的工務單卡）

**Boundary**:
- 格子集合跟著回應有哪些鍵走，數字直接用 `counts`，不自己算；工務主管名下的單讀 `engineeringSummary.tickets`、房務主管讀 `todayTasks`。
- 工務單卡抽到 `core/widgets/` 給工務首頁與工務主管首頁共用，不複製一份；點卡開工務單詳情，主管做自己名下的單時底部動作與工務員工一致（處理人視角，`viewerIsAssignee`）。
- 不帶問候列、進度環與急件保養區。

**Test Scope**:
- `specs/truth/features/ios-app/主管工作台/主管首頁.feature`

- [X] T133 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T134 [BDD-REFACTOR] 在綠燈下讓 `TmsCountTile` 一份元件涵蓋一般與寬版，首頁格子由同一份清單定義產生，不再寫死三格

## Phase 4M: MODIFY Feature File - ios-app/主管工作台/主管清單.feature

**Goal**: 清單只剩待查房與待驗收兩類；待驗收每一列看得到單號與完修結果，不含已結案、等工務接單與以不需處理結案的單；工務主管與房務主管待驗收相同；重修再完修重新出現並標「重修後再驗」。

**Shared Must Read**:
- `specs/truth/features/ios-app/主管工作台/主管清單.feature` -> `功能: 主管清單`（10 個 Rule；檔名由 `三類清單.feature` 改名）
- `specs/truth/features/ios-app/主管工作台/dsl.md` -> `"{主管}" 已在 App 打開 "{清單}"`、`清單依序為 {清單}`、`清單上沒有 "{單}"`、`每一列都看得到房號與內容`、`每一列都看得到工務單號與完修結果`、`"{單號}" 那一列標示 "{標籤}"`、`清單說明目前沒有等他處理的單，畫面不是空白`、兩句日期與時刻
- `specs/truth/features/ios-app/dsl.md` -> `已有以下工務單：`、`畫面回到 "{清單}"，"{單}" 不在清單上`
- `truth-delta.md` -> `/dsl-refine` MODIFY `主管清單.feature`、MODIFY 兩句列 Then；`/api-plan` MODIFY `VerifyQueueItem`
- `ui/ui-plan.md` -> 介面 2 清單每一列的欄位、排序、空狀態、刷新時機；畫面 2 `主管清單`
- `ui/queue.html?kind=inspection`、`ui/queue.html?kind=verify`、`ui/manager.css`、`ui/manager.js`
- `specs/truth/contracts/components/schemas/主管工作台.yaml` -> `VerifyQueueItem`（`resolutionType` nullable、`wasVerificationFailed`）
- `app/mobile/lib/features/manager/manager_queue_screen.dart:23-37、86-120`、`manager_queue_row.dart`、`manager_queues_store.dart`

**Boundary**:
- 完修結果標籤用契約值對白話（已修好、暫時處理、無法修復），`resolutionType` 為 null 時不畫標籤；重修後再驗只看 `wasVerificationFailed`。
- 待驗收列點下去要能開工務單詳情（現況 `_onTapRow` 只開放待查房）；詳情內容屬 Phase 4O。
- 排序照後端，不重排。

**Test Scope**:
- `specs/truth/features/ios-app/主管工作台/主管清單.feature`

- [X] T135 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T136 [BDD-REFACTOR] 在綠燈下讓兩類清單共用同一個列元件，只以 item 型別決定第三行與標籤

## Phase 4N: MODIFY Feature File - ios-app/主管工作台/查房.feature

**Goal**: 工務主管的首頁沒有待查房格，也沒有任何進待查房清單的入口；房務主管的查房行為照舊。

**Shared Must Read**:
- `specs/truth/features/ios-app/主管工作台/查房.feature` -> Rule「查房只開放給房務主管，工務主管的首頁沒有待查房格，也沒有任何進待查房清單的入口」、背景的陳主管；其餘 8 個 Rule 照舊
- `specs/truth/features/ios-app/主管工作台/dsl.md` -> `首頁沒有待查房格，也沒有任何進待查房清單的入口`
- `truth-delta.md` -> `/dsl-refine` ADD `查房.feature` 該 Rule、「刻意不承接」第一條（403 防禦分支不從 App 驗）
- `ui/ui-plan.md` -> 介面 3「查房畫面與房務主管限定」、畫面 3 `查房畫面` 與 4 `查房結果`
- `ui/index.html?as=mg2`、`ui/inspect.html`、`ui/inspect-result.html`
- `app/mobile/lib/features/manager/manager_home_screen.dart`、`inspection_screen.dart`

**Boundary**:
- 入口不存在就是全部：首頁、清單切換與推播都不得帶工務主管進待查房。既有查房 Rule 是 NOOP，不得變紅。

**Test Scope**:
- `specs/truth/features/ios-app/主管工作台/查房.feature`

- [X] T137 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T138 [BDD-REFACTOR] 在綠燈下確認待查房入口只從主管種類一處決定

## Phase 4O: MODIFY Feature File - ios-app/工務單/主管視角.feature

**Goal**: 主管從待驗收清單打開工務單詳情，看得到報修內容、完修照片、完修結果與處理說明；只在已完修出現「不通過」「通過」；通過後單結案、記驗收人並回清單；不通過面板原因空白不能送，送出後結果卡說明回到原處理人名下或處理人已下班回工務待認領；撞單（通過或不通過先到）顯示已被處理並回清單；已結案與退回重修中看得到驗收人、最近一次不通過的原因與判定人；工務主管可驗收自己完修的單；沒有核准、退回、補資料再送出。

**Shared Must Read**:
- `specs/truth/features/ios-app/工務單/主管視角.feature` -> `功能: 工務單詳情的主管視角`（16 個 Rule）
- `specs/truth/features/ios-app/工務單/dsl.md` -> 模組說明（三種視角對各狀態的呈現表、結果卡）、`"{員工}" 已在 App 打開 "{單號}" 的工務單詳情`、三句驗收 Given、`"{主管}" 在工務單詳情按通過`、兩句不通過 When、`"{主管}" 從驗收結果卡回到待驗收清單`、`工務單詳情顯示狀態摘要卡`、`工務單詳情的主管動作為`、`工務單詳情顯示報修內容：`、`工務單詳情顯示完修照片…`、`工務單詳情顯示最近一次不通過的原因…`、`工務單詳情沒有任何主管審核的狀態或動作`、`不通過面板的送出鈕為`、`後端沒有收到這次驗收不通過`、兩句結果卡 Then、`"{單號}" 已由 "{主管}" 驗收，驗收時間為現在`
- `specs/truth/features/ios-app/dsl.md` -> `已有以下工務單：`、`"{任務}" 在 App 顯示為 "{狀態}"`、`後端已對 "{員工}" 送出推播 "{標題}"`、兩句回清單 Then
- `truth-delta.md` -> `/dsl-refine` MODIFY `主管視角.feature` 與 `ios/工務單/dsl.md` 各列；`/api-plan` MODIFY `verifyRepairTicket`、ADD `RepairVerificationOutcome`、MODIFY `RepairTicket`、MODIFY `readRepairTicket`、MODIFY `Error`（`state_conflict`、`verification_reason_required`）
- `ui/ui-plan.md` -> 介面 4「工務單詳情的驗收動作與狀態呈現」全段、畫面 5 `工務單詳情`、「靜態雛形輸出規劃」網址表
- `ui/ticket.html?no=W-0108`（按通過、按不通過回原處理人）、`?no=W-0111`（處理人已下班回待認領）、`?no=W-0110`（已被處理）、`?no=W-0109`、`?no=W-0114`、`?no=W-0117`、`?no=W-0115&as=mg2`、`ui/manager.css`、`ui/manager.js`
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `readRepairTicket`、`verifyRepairTicket`
- `specs/truth/contracts/components/schemas/工務單.yaml` -> `RepairTicket`、`VerifyRepairTicketRequest`、`RepairVerificationOutcome`
- `app/mobile/lib/features/ticket/ticket_screen.dart:28-29、272`（`TicketView.manager` 目前直接共用工務版）、`ticket_detail_engineering.dart`、T006 的不通過面板與結果卡、`app/mobile/lib/features/manager/manager_conflict_alert.dart`、`app/mobile/lib/core/api_client.dart` 的 `verifyRepairTicket`（T005）

**Boundary**:
- 不做第三種版面：`TicketView.manager` 沿用工務版的標題、步驟條、狀態列與資訊卡，只換底部動作列；動作列只看 `state=done`（不通過描邊、通過實心），其他狀態無動作。
- 去處說明一律看 `RepairVerificationOutcome.destination` 與 `assigneeName`，App 不看班表。
- 送出帶同一把 `Idempotency-Key`；兩種結果撞 `state_conflict` 共用 `manager_conflict_alert.dart`，不通過面板一起收起，回待驗收清單並重取。
- 狀態列與摘要卡依模組說明三視角表，讀 `state`、`lastVerifyFailed*`、`verifiedBy`、`resolutionType`。

**Test Scope**:
- `specs/truth/features/ios-app/工務單/主管視角.feature`

- [X] T139 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T140 [BDD-REFACTOR] 在綠燈下把主管動作列、不通過面板與結果卡收成獨立元件，`TicketView` 的選版只在 `ticket_screen.dart` 一處

## Phase 4P: MODIFY Feature File - ios-app/工務單/工務視角.feature

**Goal**: 完修回報四選一：不需處理不附照片可以送出、送出後顯示已結案，其他三種沒照片送出鈕禁用；第四步「驗收」在結案時才完成；處理人沒有通過或不通過；退回重修時狀態列寫明判定人、步驟條回到待開始、看得到原因與判定人；退回工務待認領的單卡標示驗收退回；驗收通過後顯示已結案與驗收人。

**Shared Must Read**:
- `specs/truth/features/ios-app/工務單/工務視角.feature` -> `功能: 工務視角`（20 個 Rule）
- `specs/truth/features/ios-app/工務單/dsl.md` -> 模組說明三視角表、兩句完修 When、`完修回報的送出鈕為`、`工務單詳情顯示狀態摘要卡`、`工務單詳情的步驟條依序為`、`工務單詳情的步驟條第四步「驗收」為`、`工務單詳情沒有通過或不通過的動作`、`工務單詳情顯示最近一次不通過的原因…`、`工務待認領中 "{單號}" 標示驗收退回，原因 "{原因}"`
- `specs/truth/features/ios-app/dsl.md` -> `已有以下工務單：`、`"{任務}" 在 App 顯示為 "{狀態}"`
- `truth-delta.md` -> `/dsl-refine` MODIFY `工務視角.feature` 與相關句；`/api-plan` MODIFY `CompleteTaskRequest`、`TaskCompletion`、ADD `PoolItem.verificationReturnReason`／`EngineeringTicket.verificationReturnReason`、MODIFY `RepairTicket`
- `ui/ui-plan.md` -> 介面 4 三視角表、介面 5「報修與完修的員工端流程」、介面 6 工務待認領單卡
- `ui/ticket.html?no=W-0116&as=mg2`（完修選不需處理）、`?no=W-0109&as=mt1`、`?no=W-0114&as=mt1`、`?no=W-0117&as=mt1`、`ui/pool.html?dept=engineering`
- `specs/truth/contracts/components/schemas/任務.yaml` -> `CompleteTaskRequest`、`TaskCompletion.state`
- `app/mobile/lib/features/ticket/ticket_complete_sheet.dart:120`、`repair_labels.dart:39-74`、`ticket_detail_engineering.dart`、`app/mobile/lib/features/engineering/eng_pool_screen.dart`、`app/mobile/lib/core/api_client.dart:181-203`

**Boundary**:
- 送出鈕可按條件依結果區分，不需處理不看照片數；送出後依 `TaskCompletion.state` 決定顯示已結案還是等驗收，不自己推。
- 驗收退回標籤只看 `verificationReturnReason` 有沒有值。
- 退回重修提示卡（琥珀，原因、判定人、時間）三種視角共用一個元件。

**Test Scope**:
- `specs/truth/features/ios-app/工務單/工務視角.feature`

- [X] T141 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T142 [BDD-REFACTOR] 在綠燈下把四種完修結果的白話與照片規則收在 `repair_labels.dart` 一處，步驟條第四步的完成條件與摘要卡共用同一個狀態判斷

## Phase 4Q: MODIFY Feature File - ios-app/工務單/工務單房務端生命週期.feature

**Goal**: 報修人看到的摘要卡改為三視角表（已修好 · 等主管驗收、退回重修中、已結案 · 驗收人、已結案 · 不需處理）；報修送出後沒有任何主管審核的狀態或動作、步驟條停在待開始；報修人沒有通過或不通過；看得到最近一次不通過的原因與判定人。

**Shared Must Read**:
- `specs/truth/features/ios-app/工務單/工務單房務端生命週期.feature` -> `功能: 工務單房務端生命週期`（7 個 Rule）
- `specs/truth/features/ios-app/工務單/dsl.md` -> 模組說明三視角表、`工務單詳情顯示狀態摘要卡`、`工務單詳情沒有任何主管審核的狀態或動作`、`工務單詳情沒有通過或不通過的動作`、`工務單詳情顯示最近一次不通過的原因…`、`工務單詳情的步驟條依序為`、`工務單詳情沒有撤回按鈕`
- `specs/truth/features/ios-app/dsl.md` -> `已有以下工務單：`
- `truth-delta.md` -> `/dsl-refine` MODIFY `工務單房務端生命週期.feature`、DELETE 被退回補資料與等主管審核撤回兩個 Rule
- `ui/ui-plan.md` -> 介面 4 三視角表、介面 5 報修送出後的去向
- `ui/ticket.html?no=W-0109&as=hk2`、`?no=W-0114&as=hk2`、`?no=W-0117&as=hk2`
- `app/mobile/lib/features/ticket/ticket_detail_housekeeping.dart:225-247、310-343、489-529`、`repair_labels.dart:84-87、134`

**Boundary**:
- 房務版畫面結構不動，只換摘要卡、時間軸與動作列的狀態分支；撤回鈕只在 `pool` 出現。
- 審核分支的刪除若 GREEN 需要就做，剩下的留給 4U。

**Test Scope**:
- `specs/truth/features/ios-app/工務單/工務單房務端生命週期.feature`

- [ ] T143 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T144 [BDD-REFACTOR] 在綠燈下確認房務版、工務版、主管版的摘要卡文案出自同一張三視角對照

## Phase 4R: MODIFY Feature File - ios-app/推播/點開推播的去處.feature

**Goal**: 阿強點開驗收不通過推播停在工務單詳情；去處為首頁的推播依主管種類進首頁（陳主管進一格的主管首頁）；沒有主管未核准的推播。

**Shared Must Read**:
- `specs/truth/features/ios-app/推播/點開推播的去處.feature` -> `功能: 點開推播的去處`（5 個 Rule；背景加阿強、陳主管）
- `specs/truth/features/ios-app/推播/dsl.md` -> `"{員工}" 收到一則推播 "{標題}"，去處為 "{去處}"`、`App 停在 "{畫面}"`
- `truth-delta.md` -> `/dsl-refine` MODIFY `點開推播的去處.feature`、MODIFY `App 停在 "{畫面}"`；`/api-plan` MODIFY `PushPayload.event`、NOOP `PushTarget`
- `ui/ui-plan.md` -> 介面 1 推播開首頁、介面 4 點開驗收不通過推播
- `ui/ticket.html?no=W-0117&as=mt1`、`ui/index.html?as=mg2`
- `app/mobile/lib/core/push_gateway.dart`（`case 'home'`、`case 'ticket'`）、`app/mobile/lib/core/landing.dart`

**Boundary**:
- `kind=ticket` 的處理人點開走工務版，主管走主管版，選版沿用 4O 收斂的一處；`kind=home` 走 `landingShellFor`。既有 Rule 是 NOOP，不得變紅。

**Test Scope**:
- `specs/truth/features/ios-app/推播/點開推播的去處.feature`

- [ ] T145 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T146 [BDD-REFACTOR] 在綠燈下確認冷啟動與前景兩條推播路徑只經過 `push_gateway.dart` 一個 switch

## Phase 4S: MODIFY Feature File - ios-app/待認領/待認領列表.feature

**Goal**: 陳主管第一次打開待認領顯示房務的池、切到工務時單卡沒有搶單鈕；主管不論部門都只看不搶。

**Shared Must Read**:
- `specs/truth/features/ios-app/待認領/待認領列表.feature` -> Rule「主管第一次在這台手機打開待認領時顯示房務的池」、「主管看到的單卡與該部門員工一致，但沒有搶單鈕」各加陳主管一例；其餘 10 個 Rule 照舊
- `specs/truth/features/ios-app/待認領/dsl.md` -> `"{任務}" 的單卡沒有搶單鈕`、主管切部門與第一次打開兩句
- `specs/truth/features/ios-app/dsl.md` -> `"{員工}" 上次在待認領停在 "{部門}"`
- `truth-delta.md` -> `/dsl-refine` MODIFY `待認領列表.feature`、MODIFY `"{任務}" 的單卡沒有搶單鈕`、`/implement` 回饋修正第一列（主管沒有負責樓層的排序）；`/api-plan` MODIFY `listDeptPool`
- `ui/ui-plan.md` -> 介面 6「待認領切換部門只看不搶」、畫面 6 `主管待認領`
- `ui/pool.html`、`ui/pool.html?dept=engineering&highlight=306`
- `app/mobile/lib/features/manager/manager_pool_screen.dart`、`pool_dept_preference.dart`

**Boundary**:
- 是否可認領只看 `role`，不看 `dept`；工務主管不得因部門是工務就出現搶單鈕。既有 Rule 是 NOOP，不得變紅。

**Test Scope**:
- `specs/truth/features/ios-app/待認領/待認領列表.feature`

- [ ] T147 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T148 [BDD-REFACTOR] 在綠燈下確認員工端與主管端待認領共用同一份列表元件

## Phase 4T: MODIFY Feature File - ios-app/系統層/離線模式與連線異常.feature

**Goal**: 主管的送出查房結果、驗收通過、驗收不通過在離線時一律擋在送出之前、顯示離線提示、不留在手機上等補送；恢復連線後不補送，301 仍待查房、W-0108 與 W-0110 仍已完修。

**Shared Must Read**:
- `specs/truth/features/ios-app/系統層/離線模式與連線異常.feature` -> `功能: 離線模式與連線異常`（6 個 Rule；被擋的主管動作改三種、工務單表加 W-0110）
- `specs/truth/features/ios-app/系統層/dsl.md` -> `"{員工}" 在 App 離線時嘗試 "{動作}"`、`"{員工}" 在離線時嘗試過 "{動作}" 並被擋下`、`"{員工}" 已在 App 登入，停在首頁`
- `specs/truth/features/ios-app/dsl.md` -> `這次操作沒有留在手機上等補送`、`"{任務}" 在 App 顯示為 "{狀態}"`
- `truth-delta.md` -> `/dsl-refine` MODIFY `離線模式與連線異常.feature`、MODIFY `ios/系統層/dsl.md` 離線時嘗試、MODIFY `ios/dsl.md` 兩句
- `ui/ui-plan.md` -> 介面 3「離線」、介面 4「離線」
- `ui/inspect.html`、`ui/ticket.html`（`&offline=1`）
- `app/mobile/lib/core/system_status.dart`、`app/mobile/lib/core/connection_status.dart`、`app/mobile/lib/features/ticket/repair_ticket_form_sheet.dart:476`（目前唯一的離線判斷）、4N 的查房送出與 4O 的兩個驗收送出路徑

**Boundary**:
- 三個主管動作沿用員工端同一句離線提示與同一個連線檢查入口，不送請求、不入離線佇列；不通過面板的原因在被擋後保留。既有 Rule 是 NOOP，不得變紅。

**Test Scope**:
- `specs/truth/features/ios-app/系統層/離線模式與連線異常.feature`

- [ ] T149 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T150 [BDD-REFACTOR] 在綠燈下確認員工動作與主管三動作共用同一段「離線擋在送出之前」的判斷

## Phase 4U: DELETE Feature / DSL Truth - App 主管審核（核准、退回、補資料再送出、待審核格與清單、主管未核准）

**Goal**: 移除 App 支援主管審核的產品碼，確認新版 truth 成立、沒有畫面或模型繼續保留審核。

**Shared Must Read**:
- `specs/truth/features/ios-app/工務單/主管視角.feature`、`工務單房務端生命週期.feature`、`ios-app/主管工作台/主管首頁.feature`、`主管清單.feature`
- `specs/truth/features/ios-app/工務單/dsl.md` -> DELETE 的十句（撤回撞單 Given、按核准、按退回、打開退回面板、顯示退回原因、補資料再送出鈕、沒有驗收不通過退回與備註欄、退回面板送出鈕、後端沒有收到這次退回、顯示撤回按鈕）
- `truth-delta.md` -> `/dsl-refine` iOS DELETE 列；`/api-plan` DELETE 各列與「破壞性變更清單」App 那一點
- 過期產品碼：`app/mobile/lib/core/api_client.dart:240、304-314、1076-1077、1100、1147-1148、1179-1180、1289-1334、1387-1436、1982`；`core/widgets/tms_callout.dart:94`、`core/widgets/tms_count_tile.dart:9`；`features/manager/manager_home_screen.dart`、`manager_queues_store.dart:27、35`、`manager_queue_screen.dart:23-37、86-87`、`manager_queue_row.dart:51-68`、`manager_ids.dart:14、47、52-58`；`features/ticket/ticket_screen.dart:28、119-121、267`、`ticket_detail_housekeeping.dart:31、42、225-247、310-343、489-529`、`ticket_detail_engineering.dart:380-382`、`repair_labels.dart:84-87、134`；`features/task/room_info_screen.dart:233、290`、`task_screen.dart:1105-1114`（`rejected` 上紅色）

**Boundary**:
- 只移除審核相關的模型、方法、分支、id 與文案；不動驗收、查房與工務版的既有行為。
- `app/mobile/lib` 對 `review|reject|resubmit|approve|pending_review|主管未核准|待審核` 的 grep 不得再有審核語意的命中。
- 移除後 `flutter analyze` 與 `flutter test` 要過。

**Test Scope**:
- `specs/truth/features/ios-app/工務單/主管視角.feature`
- `specs/truth/features/ios-app/工務單/工務單房務端生命週期.feature`
- `specs/truth/features/ios-app/主管工作台/主管首頁.feature`
- `specs/truth/features/ios-app/主管工作台/主管清單.feature`

- [ ] T151 [CODE-REMOVE] 移除 App 主管審核的模型、方法、待審核格與清單、核准退回的 id 與畫面分支、主管未核准文案
- [ ] T152 [REGRESSION] 跑 Test Scope，確認新版 truth 成立

## Phase 4V: ui quality gate

**Goal**: 產品對齊本輪回追到的 plan-side 雛形家族。`Test Scope` 全綠不能代替本題。

**Read**:
- `ui/ui-plan.md` -> 視覺方向、介面 1～6、畫面與流程 1～6、互動與假資料原則、狀態與資訊揭露、驗證與錯誤回饋、靜態雛形輸出規劃的網址表、前端實作切分建議
- `ui/index.html`、`ui/queue.html`、`ui/inspect.html`、`ui/inspect-result.html`、`ui/ticket.html`、`ui/pool.html`、`ui/manager.css`、`ui/manager.js`
- `app/mobile/lib/core/landing.dart`、`app/mobile/lib/features/shell/main_shell.dart`、`app/mobile/lib/features/manager/`、`app/mobile/lib/features/ticket/`、`app/mobile/lib/features/engineering/`、`app/mobile/lib/features/pool/`、`app/mobile/lib/core/push_gateway.dart`、`app/mobile/lib/core/widgets/`

**Boundary**:
- 不改 truth、不加 feature、不重跑 GREEN 當通過條件。
- 重點比對：房務主管兩格與工務主管一格寬磚的大小與顏色；待驗收列的單號與完修結果標籤、重修後再驗標籤；工務主管名下的工務單卡與工務首頁一致；工務單詳情各狀態的狀態列、摘要卡、步驟條第四步與動作列（不通過描邊、通過實心）；不通過面板的標題、佔位字與禁用態；結果卡兩種去處說明；退回重修提示卡；完修回報第四個結果與照片豁免；工務待認領的驗收退回標籤；已被處理與離線對話框。
- 用語以 truth 為準，畫面上不得出現 `closed`、`no_action_needed` 這類內部值或錯誤碼，也不得殘留等主管審核、主管未核准、待審核。

- [ ] T153 subagent review (ui quality gate)

## Phase 4W: REGRESSION - 003 涉及的全部 feature

**Goal**: 後端與 App 各跑一輪 003 涉及的全部 feature，確認各 phase 之間沒有互相弄紅。

**Boundary**:
- 只跑、只修回歸；不加 feature、不改 truth。紅了回到對應 phase 修，修完重跑整輪。

**Test Scope**:
- 後端：`specs/truth/features/backend/主管工作台/`（主管待辦清單、主管待辦空清單與權限）、`specs/truth/features/backend/工務單/`（主管驗收的狀態守衛、驗收不通過與重修、完修與回到原任務、照片完整性與冪等送出、開立工務單與急件、工務視角維修與開單、重複報修補照片與撤回）、`specs/truth/features/backend/查房/逐項判定與退回去向.feature`、`specs/truth/features/backend/推播/`（NOOP，確認推播事件值集收回後不紅）；另跑一次不帶 `-Dcucumber.features` 的後端全套件。
- App：`帳號與登入/登入與登出守衛.feature`、`主管工作台/主管首頁.feature`、`主管工作台/主管清單.feature`、`主管工作台/查房.feature`、`工務單/主管視角.feature`、`工務單/工務視角.feature`、`工務單/工務單房務端生命週期.feature`、`推播/點開推播的去處.feature`、`待認領/待認領列表.feature`、`系統層/離線模式與連線異常.feature`，逐支以 `FEATURE=<路徑>` 跑；另跑 `app/mobile` 的 `flutter test`。

- [ ] T154 [REGRESSION] 跑後端 Test Scope 與後端全套件，確認全綠
- [ ] T155 [REGRESSION] 跑 App Test Scope 與 `flutter test`，確認全綠
