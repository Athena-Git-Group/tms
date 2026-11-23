# Tasks: 派給工務的單一律是工務單

**Plan Package**: `specs/plans/006-engineering-pool-repair-ticket`
**Core Inputs**: `spec.md`, `plan.md`, `truth-delta.md`, `specs/truth/techstack.md`, `specs/truth/contracts/**`, `specs/truth/data/**`, `specs/truth/features/backend/**`, `specs/truth/features/ios-app/**`, `specs/truth/features/guest-web/**`, `specs/truth/features/frontdesk-web/**`

`research.md` 與 `ui/**` 本輪不存在（`/technical-research` 與 `/ui-plan` 都是 NOOP，見 `plan.md`）。`.agents/constitution/` 不存在，憲法步驟略過，以專案 `CLAUDE.md` 為準。

## Task Binding Contract

- 每個**開發任務**都必須對應 `truth-delta.md` 中的 ADD / MODIFY / DELETE / NOOP 語意。
- Phase 1 `Setup` 只做本輪新增技術的基礎建設、技術環境與最後的 smoke-test；不寫 DSL 語意、不寫產品行為。
- Phase 2 `Foundational` 只建立後續實作程式、測試共用元件、入口、fixture、helper 與落點骨架。
- Phase 3 `Test Alignment & Implementation` 的目的：在寫產品碼之前，先把本輪所有受影響 DSL 的自動化測試對齊最新版 truth。
- Truth 參照必須使用 `specs/truth/**` 路徑；plan 參照才使用當前 plan package 內相對路徑。
- Feature `Shared Must Read` 除 feature、同模組 DSL、對應 `truth-delta` 列外，還必須列入回追到的 planner 產物。

**跑測試的方式**（每一題的驗證都用這幾條，不另寫）：

- 後端全套（行為規格、舊整合測試 `integration/RunCucumberTest`、`relaywake/RunOutboxRelayWakeTest` 與單元測試，JDK 25、OrbStack）：
  `JAVA_HOME="$(/usr/libexec/java_home -v 25)" TESTCONTAINERS_RYUK_DISABLED=true ./tms/mvnw -f tms/pom.xml test -DargLine="-Dapi.version=1.44"`。
- 只跑後端行為規格：上一條加 `-Dtest=RunTruthFeaturesTest`；只跑一份或一個模組再加 `-Dcucumber.features=../specs/truth/features/backend/<模組>/<檔>.feature`；查 undefined step 加 `-Dcucumber.execution.dry-run=true`。
  只跑單一 feature 時會多一個 CucumberTestEngine discovery 的假 error，忽略。
- 員工 App 單元：`cd app/mobile && flutter test`。
- 員工 App 行為規格（模擬器，一次只能一個 agent 用）：`app/bdd/run-module.sh <模組>`，例如 `工務單`、`待認領`；跑之前要有 8081 的 BDD 後端堆疊（見 `specs/truth/techstack.md` 員工 App BDD techstack）。
- 櫃檯 Web 與住客頁行為規格：`bash app/web-bdd/run-feature.sh [--keep] <路徑> [desktop|ipad|phone]`。路徑以 `frontdesk-web/` 或 `guest-web/` 開頭時相對於 `specs/truth/features/`；跑完用 `bash app/web-bdd/run-feature.sh --stop` 收掉。
- 多個 agent 同時動後端遷移或測試共用程式時，改到 git worktree 做；Maven 本機倉庫與模擬器不要兩個 agent 共用同一份。

## Phase 1: Setup

**Goal**: 讓最後的實機驗收在手動測試環境就走得到住客頁。

- [ ] T001 手動測試環境的網頁代理補上住客頁
  - Read:
    - `scripts/manual-env/start.sh:103-127`（啟動 vite 時蓋掉的 `server.proxy`）
    - `app/web/vite.config.ts:28-34`（`/guest/stream` 要排在 `/guest` 前面）
  - 只做：在 `start.sh` 蓋掉的 proxy 裡補上 `'/guest/stream'`（沿用 `/staff/stream` 那段 `configure`）與 `'/guest'`，target 都是手動環境的後端；啟動訊息那一行跟著寫出 `/guest`。改完 `bash scripts/manual-env/stop.sh web && bash scripts/manual-env/start.sh web`，用 `curl -s -o /dev/null -w '%{http_code}' http://localhost:5300/guest/...` 之類確認請求打到 8083 而不是 8080（8080 沒開時回 502 就代表還在打 8080）。
  - 不做：改 `vite.config.ts`；碰 `scripts/manual-env/up.sh` 與 `README.md`（使用者還沒提交的工作）。

## Phase 2: Foundational

**Goal**: 量好改動前的基準，建好開工務單元件、遷移測試與 App 欄位的落點，讓 Phase 3 與 Phase 4 有地方寫。

- [ ] T002 量改動前的基準
  - Read:
    - `specs/truth/techstack.md` -> 測試與驗證
  - 只做：在任何產品碼或測試碼改動之前，跑下面五組並把每一組的失敗清單（feature 檔與行號，或測試類別與方法）與失敗數記到 `implement-notes.md`：
    1. 後端全套（「跑測試的方式」第一條，不加 `-Dtest`）。
    2. `bash app/web-bdd/run-feature.sh --keep frontdesk-web/ desktop`。
    3. `bash app/web-bdd/run-feature.sh --keep guest-web/ phone`，跑完 `--stop`。
    4. `app/bdd/run-module.sh 工務單` 與 `app/bdd/run-module.sh 待認領`。
    5. `cd app/mobile && flutter test`。
    分支上已經有程式改動時，改在 HEAD 的乾淨 worktree 量。後面每一題說「全綠」都是指「除了這份基準裡既有的失敗之外全綠」；T049–T051 用同一組指令比對同一份基準。
  - 不做：修任何既有失敗；改任何程式。

- [ ] T003 建 `RepairTicketOpener` 與工務單草稿的落點
  - Read:
    - `plan.md` -> 已定的事（唯一保證點、`open` 的職責、冪等、櫃檯轉單欄位表）
    - `tms/src/main/java/com/athena/tms/repair/RepairService.java` -> `judgeUrgent`（:164）、`nextTicketNo`（:730）、`notifyUrgent`（:741）、兩段 `INSERT INTO repair_tickets`（:365、:569）
    - `tms/src/main/java/com/athena/tms/dto/TaskCreateCommand.java`
  - 只做：在 `com.athena.tms.repair` 新增 `RepairTicketOpener`（Spring 元件，只依賴 `JdbcTemplate`、`Clock`、推播寫入；不依賴 `TaskEngine`）與 `RepairTicketDraft` record（類別、說明、報修人、原任務、追蹤單的原單）；`open(tenantId, taskId, draft)` 與 `discard(tenantId, taskId)` 先丟 `UnsupportedOperationException`。`TaskCreateCommand` 多一個可為 null 的草稿欄位，既有建構子呼叫點補 null，編譯通過、後端全套結果與 T002 相同。
  - 不做：任何開單、取號、急件判定的行為；不動 `TaskEngine`、`RepairService` 的邏輯。

- [ ] T004 建 V29 遷移的測試骨架
  - Read:
    - `specs/truth/data/data-model.dbml` -> `repair_tickets` Note「舊資料修補（V29，一次性）」、`tasks` Note「V29 退回待派單」
    - `tms/src/main/resources/db/migration/`（最新是 `V28__push_outbox_own_key.sql`）
  - 只做：新增 `tms/src/test/java/com/athena/tms/integration/V29EngineeringTaskBackfillTest.java`：以 Testcontainers 起 Postgres，Flyway 只遷到 V28，提供插入「飯店、員工、房間、任務、事件、`task_counters`、`repair_ticket_counters`」的 helper（計數列可以不插，用來測沒有計數列的飯店），再遷到最新版。遷移失敗時要拿得到 Flyway 的例外訊息，給「讓遷移停下」的案例斷言。先放一個只確認流程跑得通的測試。
  - 不做：寫 V29 本身；寫 V29 的行為斷言。

- [ ] T005 App 的 `PoolItem.repairCategory` 與單卡類別標示的 identifier
  - Read:
    - `specs/truth/contracts/components/schemas/員工工作台.yaml` -> `PoolItem.repairCategory`
    - `app/mobile/lib/core/api_client.dart` -> `class PoolItem`（:1360）
    - `app/mobile/lib/features/engineering/eng_pool_screen.dart` -> `_engPoolCard`（:234-262）
  - 只做：`PoolItem` 多解析可為 null 的 `repairCategory`；工務待認領單卡房號方塊的類別文字掛上 `Semantics(identifier: 'engPool.item.{單號}.category-label')`（沿用 `TmsSemantics` 慣例）。`flutter test` 結果與 T002 相同。
  - 不做：改單卡顯示的文字來源（Phase 4E 才做）。

## Phase 3: Test Alignment & Implementation

**Goal**: 把本輪 Feature 用到的 DSL 自動化測試對齊最新版 truth；含 truth-delta 有改的句，以及本輪 Feature 用到、尚無 stepdef 的句。不寫產品行為。

**DSL 參照**:
- 每一句只屬於一個權威 `dsl.md`：同模組 `specs/truth/features/{介面}/{模組}/dsl.md`，或介面根 `specs/truth/features/{介面}/dsl.md`。不得掃其他模組。
- 本輪的句分在：`specs/truth/features/backend/dsl.md`（介面根）、`specs/truth/features/backend/櫃檯看板與派單/dsl.md`、`specs/truth/features/backend/工務單/dsl.md`、`specs/truth/features/ios-app/工務單/dsl.md`、`specs/truth/features/frontdesk-web/dsl.md`（介面根）、`specs/truth/features/frontdesk-web/待派單/dsl.md`、`specs/truth/features/guest-web/dsl.md`（介面根）、`specs/truth/features/guest-web/設備報修/dsl.md`。每題標了它的權威檔。
- 讀法：用 task title 的句型對到該檔那一列，以 `StepDef 實作語意`（網頁是 `櫃檯 Web 實作語意`）當作測試程式碼語意。Given / When 讀 `怎麼做`、`權威狀態落地`、`Arrange 來源`、`回寫`；Then 讀 `必查`（`呈現結果`、`權威狀態`、`再讀確認`、`跨視角`、`不該發生`）。
- `truth-delta.md` 只告訴這句是 ADD / MODIFY / DELETE。語意以 `dsl.md` 那一列為準，不得用 feature 措辭或舊 stepdef 自行發明。

**Markers**:
- `[BDD-ALIGN]`：`MODIFY`。既有 stepdef 還在，但語意是舊 truth。依 `dsl.md` 該列改測試，讓它表達最新版 `StepDef 實作語意`。
- `[BDD-REMOVE]`：`DELETE`。本輪沒有。
- `[BDD-RED]`：`ADD`，或本輪 Feature 用到、尚無 stepdef 的句。依 `dsl.md` 該列寫出 stepdef。完成時這句可被跑到，失敗只能是 assertion 或產品行為，不能是 undefined step。
- 三個 marker 都只動測試層，不寫產品碼。

**Shared Must Read**:
- `specs/truth/features/backend/dsl.md` -> 共通實作約定、狀態用語對照，以及下面各題點名的列
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> 用語對照與下面各題點名的列
- `specs/truth/features/backend/工務單/dsl.md` -> 模組說明與下面各題點名的列
- `specs/truth/features/ios-app/工務單/dsl.md` -> 下面點名的列
- `specs/truth/features/frontdesk-web/dsl.md`、`specs/truth/features/guest-web/dsl.md` -> 共通實作約定與下面點名的列
- `truth-delta.md` -> `/dsl-refine` 有對應 ADD / MODIFY 的句
- `tms/src/test/java/com/athena/tms/bdd/steps/desk/`、`tms/src/test/java/com/athena/tms/bdd/steps/repair/`、`app/bdd/src/test/java/com/athena/app/bdd/steps/repair/`、`app/web-bdd/src/test/java/com/athena/web/bdd/steps/`、`app/bdd-support/src/main/java/com/athena/bddsupport/`

**Boundary**:
- 一條 DSL 一個 task。
- 只改該句的 stepdef / assertion / 直接依賴的 helper。
- 不寫產品碼。
- review 啟動 subagent；本輪所有 Test Scope 不得再有 undefined step，失敗只能是 assertion 或產品行為。有 issues 就修正再 review，直到沒有任何問題。通過前不解鎖 Phase 4。

**Parallel Hint**:
- T006–T026 是後端測試線，各派一個獨立 subagent（共用 `tms/src/test`，同一個檔的題請同一個 subagent 依序做，或改到各自的 worktree）；T027 是 App 測試線；T028–T031 是網頁測試線（共用 `app/web-bdd` 與 `app/bdd-support`，同一個 subagent 依序做）。三條線彼此平行；T032 等全部回來再啟動 subagent 來 review。

後端測試線：

- [ ] T006 [P] [BDD-ALIGN] `Given: "{櫃檯}" 已把 "{任務}" 派給 "{部門}"`（`backend/dsl.md`）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/desk/DeskCommonSteps.java:80`
- [ ] T007 [P] [BDD-ALIGN] `When: "{櫃檯}" 把 "{任務}" 派給 "{部門}"`（`backend/dsl.md`）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/desk/DeskCommonSteps.java:103`
- [ ] T008 [P] [BDD-ALIGN] `When: "{櫃檯}" 復原 "{任務}" 的派單`（`backend/dsl.md`）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/desk/DeskCommonSteps.java:110`
- [ ] T009 [P] [BDD-ALIGN] `Given: 本飯店下一張工務單號為 "{單號}"`（權威位置搬到 `backend/dsl.md`，語意不變）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairGivenSteps.java:72`
  - 只確認 stepdef 與新權威列一致、`櫃檯看板與派單` 模組的 feature 跑得到它；Cucumber glue 是全域的，不必搬檔。
- [ ] T010 [P] [BDD-ALIGN] `Given: "{房號}" 今天是 VIP 房，住客 "{時刻}" 入住`（權威位置搬到 `backend/dsl.md`，語意不變）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairGivenSteps.java` 裡這句的 stepdef
  - 只確認 stepdef 與新權威列一致、`派出後復原.feature` 跑得到它。
- [ ] T011 [P] [BDD-ALIGN] `Then: 以下員工都收到 "{單號}" 的急件推播：`（權威位置搬到 `backend/dsl.md`，必查加嚴）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairThenSteps.java` 裡這句的 stepdef
  - 依新列加嚴：只認本案例觸發動作之後寫入的推播、推播文字含這個單號；同一張任務先後兩張工務單時，第一張的推播不能讓第二張的斷言通過。
- [ ] T012 [P] [BDD-ALIGN] `When: "{房號}" 的住客打字送出 "{內容}"`（`backend/櫃檯看板與派單/dsl.md`）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/desk/DeskWhenSteps.java:133`
- [ ] T013 [P] [BDD-ALIGN] `When: "{工務}" 只看 "{分流}" 的工務待認領`（`backend/工務單/dsl.md`）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairWhenSteps.java:246`
- [ ] T014 [P] [BDD-ALIGN] `Then: "{單號}" 的來源為 "{來源}"`（`backend/工務單/dsl.md`）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairThenSteps.java:177`
- [ ] T015 [P] [BDD-ALIGN] `Then: 工務待認領包含 "{單號}"`（`backend/工務單/dsl.md`）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairThenSteps.java:203`
- [ ] T016 [P] [BDD-RED] `Given: 飯店的服務項目 "{項目}" 設定交給 "{部門}"`（`backend/櫃檯看板與派單/dsl.md`）
- [ ] T017 [P] [BDD-RED] `When: "{房號}" 的住客勾選服務項目 "{項目}" 送出`（`backend/櫃檯看板與派單/dsl.md`）
- [ ] T018 [P] [BDD-RED] `Then: "{任務}" 成為工務單 "{單號}"，內容如下：`（`backend/櫃檯看板與派單/dsl.md`）
- [ ] T019 [P] [BDD-RED] `Then: "{任務}" 只有一張工務單 "{單號}"，本飯店下一張工務單號為 "{下一號}"`（`backend/櫃檯看板與派單/dsl.md`）
- [ ] T020 [P] [BDD-RED] `Then: "{任務}" 不是工務單，本飯店下一張工務單號仍為 "{單號}"`（`backend/櫃檯看板與派單/dsl.md`）
- [ ] T021 [P] [BDD-RED] `Then: "{任務}" 不再是工務單，類別回到 "{類別}"，工務待認領沒有 "{單號}"`（`backend/櫃檯看板與派單/dsl.md`）
- [ ] T022 [P] [BDD-RED] `Then: "{任務}" 的急件標記回到 "{急件}"`（`backend/櫃檯看板與派單/dsl.md`）
- [ ] T023 [P] [BDD-RED] `Given: "{工務}" 已認領並開始處理 "{單號}"`（`backend/工務單/dsl.md`）
- [ ] T024 [P] [BDD-RED] `Then: 工務待認領中 "{單號}" 的類別為 "{類別}"`（`backend/工務單/dsl.md`）
- [ ] T025 [P] [BDD-RED] `Then: "{房號}" 的住客讀到這張需求的狀態為 "{狀態}"`（`backend/工務單/dsl.md`）
- [ ] T026 [P] [BDD-RED] 檢查 `specs/truth/features/backend/工務單/櫃檯轉來的工務單.feature` 用到的既有句在 dry-run 都有 stepdef（`已有以下工務單：`、`"{單號}" 已由 "{工務}" 完修`、`"{工務}" 附 {張數} 張完修照片、處理說明 "{說明}" 完成 "{單號}"`、`"{主管}" 驗收通過 "{單號}"`、`"{單號}" 尚未驗收`、`"{單號}" 已由 "{主管}" 驗收`、`"{任務}" 的狀態為 "{狀態}"` 以工務單號指單）；缺的依權威列補上，不缺就只記錄 dry-run 結果

App 測試線：

- [ ] T027 [P] [BDD-RED] `Then: 工務待認領中 "{單號}" 的類別標示為 "{類別}"`（`ios-app/工務單/dsl.md`）
  - Read: `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairThenSteps.java`（同檔既有的「來源標示為」寫法）

網頁測試線：

- [ ] T028 [P] [BDD-ALIGN] `When: 一張住客勾選的需求與一張命中房務規則的打字需求進來`（`frontdesk-web/待派單/dsl.md`，句型改名）
  - Read: `app/web-bdd/src/test/java/com/athena/web/bdd/steps/triage/TriageWhenSteps.java:22-34`（常數）、`:62-84`（步驟本體與等頁首張數）、`:106-123`（`classificationRule()` 與 `requireInPool`）
  - 註解改成新句型；`classificationRule()` 改成 `dept=housekeeping`、`type=amenity`，關鍵字與 `RULE_HIT_TEXT` 換成不含「冷氣」的房務字眼（例如「毛巾」「想多一條毛巾」）；拿掉等「工務 N+1 單待接」的那一行，改等「房務」多兩張；`requireInPool` 保留（它現在要證明的是房務規則真的直接進了待認領）。
- [ ] T029 [P] [BDD-ALIGN] `Given: 全館有以下的單：`（`frontdesk-web/dsl.md`）
  - Read: `app/web-bdd/src/test/java/com/athena/web/bdd/steps/common/TaskTableRows.java:170-190`（部門與類型的預設）、`app/bdd-support/src/main/java/com/athena/bddsupport/BackendChannel.java:888-1000`（`createTask`、`createRepairTicket`）、`:2200-2215`（已經離開櫃檯的單）
  - `類型` 預設改成「部門是工務時是修繕」；部門是工務的每一列都要有一張工務單：櫃檯派出的（有 `經手人` 與 `派出時間`）報修人是經手人、類別其他、單號用 `單號` 欄或下一個 `W-` 號；其餘照既有修繕列的做法。跑 `bash app/web-bdd/run-feature.sh frontdesk-web/單筆詳情/歷程.feature` 與 `frontdesk-web/總覽看板/等待膠囊與逾時.feature`，失敗只能是 T002 基準裡既有的。
- [ ] T030 [P] [BDD-ALIGN] `Given: 這家飯店的分類規則把含 "{關鍵字}" 的文字分給工務`（`guest-web/設備報修/dsl.md`）
  - Read: `app/web-bdd/src/test/java/com/athena/web/bdd/steps/guest/repair/RepairGivenSteps.java:70-80`
  - 這句只負責設規則（`engineering`、`repair`），預期不必改程式；確認 stepdef 註解與新列一致（不再說命中後直接進工務待認領），記錄結果。`組成文字送出.feature` 這條 Rule 的 Then 都用既有句，會在 T039 之前紅、之後綠。
- [ ] T031 [P] [BDD-ALIGN] `Given: "{房號}" 房的住客這次住宿有以下需求：`（`guest-web/dsl.md`）
  - Read: `app/web-bdd/src/test/java/com/athena/web/bdd/steps/guest/common/GuestCommonGivenSteps.java:208-252`（`defaultType`、負責人）、`app/bdd-support/src/main/java/com/athena/bddsupport/GuestSeed.java:30-80`
  - `類型` 預設在工務待認領、工務已接、已結案改成修繕；這三種狀況改成先以住客身分送出原文，再以林佩君呼叫 `deskAssignTask` 派給工務（讓後端自己開工務單），再往下推進；不再直接把住客的單寫成工務部門。T033 之前後端派給工務還不會開工務單，這幾列的場景在那之前跑出來的失敗只能是產品行為。

- [ ] T032 subagent review (phase quality gate)
  - 後端以 dry-run 跑 `backend/櫃檯看板與派單`、`backend/工務單` 兩個模組，App 以 dry-run 跑 `ios-app/工務單/工務視角.feature`，網頁以 `MAVEN_ARGS="-Dcucumber.execution.dry-run=true" bash app/web-bdd/run-feature.sh <路徑>` 跑 `frontdesk-web/待派單/待派單清單.feature`、`guest-web/設備報修/組成文字送出.feature`（runner 不吃這個參數時就實跑，失敗只能是產品行為）：undefined step 為 0；每個 stepdef 的斷言對得上權威列的 `必查` 全部項目，不只看回應。

## Phase 4A: MODIFY Feature File - backend/櫃檯看板與派單/櫃檯派單.feature

**Goal**: 櫃檯派給工務的同一個交易，讓同一張單成為工務單（不變式的第一個入口）；`RepairService` 的開單也收斂到同一個步驟。

**Shared Must Read**:
- `specs/truth/features/backend/櫃檯看板與派單/櫃檯派單.feature` -> `功能: 櫃檯派單`
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> Then 句型新增的列
- `specs/truth/features/backend/dsl.md` -> 派單的 Given／When、`本飯店下一張工務單號為`
- `truth-delta.md` -> `/dsl-refine` 的 `櫃檯派單.feature` 列與 `backend/dsl.md` 列
- `truth-delta.md` -> `/api-plan` 的 `deskAssignTask` 列；`/data-plan` 的 `tasks.dept`、`tasks` Note、`task_events.payload`、`repair_tickets` 各列
- `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> `deskAssignTask`
- `specs/truth/data/data-model.dbml` -> `tasks.dept` note、`tasks` Note「櫃檯處理待派單」、`repair_tickets` Note 急件段與「櫃檯轉單」
- `plan.md` -> 已定的事（不變式、唯一保證點、`open` 的職責、冪等、櫃檯轉單欄位表）

**Boundary**:
- 讓單成為工務部門的只有 `TaskEngine.create` 與 `TaskEngine.assignFromTriage`，兩者都呼叫 `RepairTicketOpener.open`；不在 `DeskBoardService` 或其他服務另寫開單。
- 任務類型只在 `assignFromTriage` 的守衛 UPDATE 同句改成 `repair`；`open` 不碰 `tasks.type`。
- `open` 對已有工務單的任務原樣回傳，不取號、不改欄位。

**Test Scope**:
- `specs/truth/features/backend/櫃檯看板與派單/櫃檯派單.feature`
- `specs/truth/features/backend/工務單/`（整個模組，REFACTOR 搬動 `RepairService` 開單後必須仍全綠）
- T035 起：後端全套（含舊整合測試與單元測試）

- [ ] T033 [BDD-GREEN] 讓 Test Scope 全綠
  - 實作 `RepairTicketOpener.open`（已有工務單就回傳；否則取號、寫 `repair_tickets`、把急件判定寫進 `repair_tickets` 與 `tasks.urgent`、判定為急件時寫急件推播）；`TaskEngine.assignFromTriage` 目標部門是工務時，守衛 UPDATE 同句改 type，`triaged` payload 加 `former_type` 與 `former_urgent`，再以草稿（類別 `other`、報修人＝操作者）呼叫 `open`。
- [ ] T034 [FIXTURE] 盤點並改掉繞過開單步驟建工務部門單的測試資料（與 T035 同一輪做，本題先做）
  - Read: `plan.md` -> 不改的地方「測試資料繞過唯一保證點的地方」
  - 先跑 `grep -rn "new TaskCreateCommand(" tms/src/test` 與 `grep -rn "INSERT INTO repair_tickets" tms/src/test`，把每一個可能以 `engineering` 建單的呼叫點列進 `implement-notes.md`。已知的有：
    - `tms/src/test/java/com/athena/tms/bdd/support/DeskBoardFixtures.java:185-208` 建單後由 `insertRepairTicket`（:295-322）自己補工務單；部門由 `:432` 決定。
    - `tms/src/test/java/com/athena/tms/bdd/support/TaskFixtures.java:385-410` 的 builder，`bdd/steps/repair/RepairGivenSteps.java` 在 `:199` 與 `:565` 一帶用它建工務部門單，再於 `:220`、`:575` 自己 `INSERT INTO repair_tickets`。
    - `tms/src/test/java/com/athena/tms/integration/stepdefs/given/EntityTableGivenSteps.java:304`（`tms/src/test/resources/features/02-staff-pool.feature:145` 那一列 `repair`／`guest`／`engineering`）。
    - `tms/src/test/java/com/athena/tms/integration/stepdefs/when/StreamWhenSteps.java:54`、`:91`（`14-sse-stream.feature` 的工務池新單）。
    - `tms/src/test/java/com/athena/tms/service/TaskEngineRowWriteTest.java:117`。
  - 每一處改成：建單時帶 `RepairTicketDraft`，讓 `TaskEngine.create` 經 `open` 開工務單；原本自己 `INSERT INTO repair_tickets` 的拿掉，要指定單號就在建單前設好 `repair_ticket_counters`，要指定建立時間、急件等欄位就在建單後 `UPDATE`，不再 `INSERT`。
  - 不做：改產品碼；改任何 feature 的期待。
- [ ] T035 [BDD-REFACTOR] 把 `RepairService` 的開單收斂到同一個步驟
  - `judgeUrgent`、`nextTicketNo`、`notifyUrgent` 搬進 `RepairTicketOpener`，櫃檯轉單同樣做急件判定與急件推播；`TaskEngine.create` 的部門是工務時必須帶草稿（沒帶丟 `IllegalArgumentException`），並在同一交易呼叫 `open`；`RepairService.create` 與 `createFollowUpTicket` 改帶草稿，刪掉自己的兩段 `INSERT INTO repair_tickets`，只留補 `pool_source`、綁照片、中斷原任務等它自己的事。搬完跑後端全套，失敗清單是 T002 基準的子集。

## Phase 4B: MODIFY Feature File - backend/櫃檯看板與派單/派出後復原.feature

**Goal**: 復原派給工務的單時，工務單一起撤掉，類型與急件標記都回到派單前；再派開出的新工務單照樣推急件。

**Shared Must Read**:
- `specs/truth/features/backend/櫃檯看板與派單/派出後復原.feature` -> `功能: 派出後復原`
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> `"{任務}" 不再是工務單…`、`"{任務}" 的急件標記回到…`、`"{任務}" 成為工務單…`、`"{櫃檯}" 已復原 "{任務}" 的派單`
- `specs/truth/features/backend/dsl.md` -> `"{櫃檯}" 復原 "{任務}" 的派單`、`"{房號}" 今天是 VIP 房…`、`以下員工都收到 "{單號}" 的急件推播：`
- `truth-delta.md` -> `/dsl-refine` 的 `派出後復原.feature` 列；`/api-plan` 的 `deskUndoAssign` 列；`/data-plan` 的 `tasks` Note 與 `repair_tickets` Note 列
- `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> `deskUndoAssign`
- `plan.md` -> 已定的事（`undoTriage` 一段、`open` 的職責）

**Boundary**:
- 只在被復原的那筆 `triaged` 是派給工務時撤工務單；刪除只發生在復原守衛成立的同一交易。
- 撤掉的單號不回收，不動 `repair_ticket_counters`。
- 急件推播的防重複鍵只改成 `repair_urgent:{ticketNo}:{recipient}`，推播內容與收件人不變。

**Test Scope**:
- `specs/truth/features/backend/櫃檯看板與派單/派出後復原.feature`
- `specs/truth/features/backend/工務單/開立工務單與急件.feature`（防重複鍵改了，既有急件推播必須照舊）

- [ ] T036 [BDD-GREEN] 讓 Test Scope 全綠
  - `TaskEngine.undoTriage` 成功後，若前一筆 `triaged` 的 payload `dept` 是 `engineering`：呼叫 `RepairTicketOpener.discard` 刪掉工務單，同一句 UPDATE 把 `type`、`urgent` 設回 payload 的 `former_type`、`former_urgent`。`RepairTicketOpener` 的急件推播防重複鍵改用工務單號。
- [ ] T037 [BDD-REFACTOR] 在綠燈下整理復原的分支，確認 `undoTriage` 讀 `former_type`、`former_urgent` 與讀 `dept` 走同一次查詢，不多一次來回

## Phase 4C: MODIFY Feature File - backend/櫃檯看板與派單/櫃檯看板讀取.feature

**Goal**: 住客的需求不直通工務：命中工務或修繕的分類規則、服務項目，一律停在待派單；住客頁與櫃檯 Web 跟著後端。

**Shared Must Read**:
- `specs/truth/features/backend/櫃檯看板與派單/櫃檯看板讀取.feature` -> `功能: 櫃檯看板讀取`
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> `"{房號}" 的住客打字送出 "{內容}"`、`飯店的服務項目 "{項目}" 設定交給 "{部門}"`、`"{房號}" 的住客勾選服務項目 "{項目}" 送出`
- `specs/truth/features/guest-web/設備報修/組成文字送出.feature` -> 「分類規則把報修分給工務時…」兩條 Rule
- `specs/truth/features/frontdesk-web/待派單/待派單清單.feature` -> Rule「直接進房務待認領的需求不會出現在待派單頁」
- `truth-delta.md` -> `/dsl-refine` 的 `櫃檯看板讀取.feature`、guest-web、frontdesk-web 各列；`/api-plan` 的 `createGuestRequest` 列
- `specs/truth/contracts/groups/住客與房間/住客掃碼.yaml` -> `createGuestRequest`
- `plan.md` -> 已定的事（住客不直通工務）

**Boundary**:
- 只改 `GuestRequestService` 組指令的地方（`menuCommand`、`freeTextCommand`）：部門是工務或類型是 `repair` 就改成 `triage`、`frontdesk`、`other`；`ClassificationRuleEngine` 的比對不動，命中的 `rule_id` 照記在 `note`，命中房務、類型不是修繕的行為不變。
- 補 `GuestRequestService` 的單元測試：規則設成 `dept=housekeeping`、`type=repair` 時，單進 `triage`、有 T- 單號。

**Test Scope**:
- `specs/truth/features/backend/櫃檯看板與派單/櫃檯看板讀取.feature`
- `tms/src/test/resources/features/01-guest-requests.feature`（`-Dtest=RunCucumberTest`）
- `bash app/web-bdd/run-feature.sh guest-web/設備報修/組成文字送出.feature phone`
- `bash app/web-bdd/run-feature.sh 待派單/待派單清單.feature desktop`

- [ ] T038 [BDD-ALIGN] 舊整合測試裡「住客打冷氣直接進工務待認領」的期待
  - Read: `tms/src/test/resources/features/01-guest-requests.feature:104-119`
  - 「建單路徑」場景大綱的「自由文字「冷氣不冷」」那一列改成 `other`／`frontdesk`／`triage`，上方註解改成「命中工務的規則仍停在待派單」；「規則引擎命中留痕可解釋」不改（命中的 `rule_id` 照記）。
- [ ] T039 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T040 [BDD-REFACTOR] 在綠燈下把兩條住客路徑的「工務或修繕改進待派單」收成一個判斷，不各寫一次

## Phase 4D: ADD Feature File - backend/工務單/櫃檯轉來的工務單.feature

**Goal**: 櫃檯轉來的工務單在工務待認領標「櫃檯」、帶工務單類別，並照工務單規則完修、驗收、結案。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/櫃檯轉來的工務單.feature` -> `功能: 櫃檯轉來的工務單`
- `specs/truth/features/backend/工務單/dsl.md` -> 模組說明、`只看…的工務待認領`、`的來源為`、`工務待認領包含`、`工務待認領中…的類別為`、`住客讀到這張需求的狀態為`、`已認領並開始處理`
- `truth-delta.md` -> `/dsl-refine` 的 `櫃檯轉來的工務單.feature` 與 `工務單/dsl.md` 各列；`/api-plan` 的 `PoolItem.repairCategory`、`listDeptPool`、`EngineeringTicket.source`、`EngineeringTicket.ticketNo` 列
- `specs/truth/contracts/components/schemas/員工工作台.yaml` -> `PoolItem`、`EngineeringTicket`
- `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `listDeptPool`
- `plan.md` -> 後端現況的落差與歸屬（讀端的兩處落差）

**Boundary**:
- 工務單的來源換算只寫一處，`deptPool`（`TaskExecutionService.java:461-463`）與 `engineeringTickets`（`:808`）都用它：`source` 是 `desk` 或 `guest` 為 `desk_forward`，其餘為 `staff_report`。房務池的來源換算不動。
- `poolRows` 多選 `rt.category`，`deptPool` 回 `repairCategory`；房務池為 null。

**Test Scope**:
- `specs/truth/features/backend/工務單/櫃檯轉來的工務單.feature`
- `specs/truth/features/backend/待認領/`（整個模組，確認房務池不受影響）

- [ ] T041 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T042 [BDD-REFACTOR] 在綠燈下刪掉兩處重複的來源換算，只留共用的那一個

- [ ] T043 subagent review (plan.md 已定的事 quality gate)
  - Read: `plan.md` -> 已定的事、後端現況的落差與歸屬；已實作的 `TaskEngine.java`、`RepairTicketOpener.java`、`RepairService.java`、`GuestRequestService.java`、`TaskExecutionService.java`
  - 通過條件：路徑盤點表的每一列都照「修好之後」那一欄落地；`INSERT INTO repair_tickets` 在整個 `tms/src/main` 只剩 `RepairTicketOpener` 一處；會把任務的 `dept` 寫成 `engineering` 的 SQL 只剩 `TaskEngine` 的建單與派單兩處，且兩處都在同一交易呼叫 `open`；`tasks.type` 改成 `repair` 只在 `assignFromTriage` 的守衛 UPDATE；急件推播的防重複鍵以工務單號為單位；`open` 有一個「同一張任務呼叫兩次只開一張」的單元測試；T034 盤點出的每一個 fixture 都不再自己 `INSERT INTO repair_tickets`。`Test Scope` 全綠不能代替本題。

## Phase 4E: MODIFY Feature File - ios-app/工務單/工務視角.feature

**Goal**: 工務待認領單卡房號方塊顯示工務單類別的中文。

**Shared Must Read**:
- `specs/truth/features/ios-app/工務單/工務視角.feature` -> `功能: 工務視角`
- `specs/truth/features/ios-app/工務單/dsl.md` -> `工務待認領中 "{單號}" 的類別標示為 "{類別}"`
- `truth-delta.md` -> `/dsl-refine` 的 `工務視角.feature` 列與 ios-app `dsl.md` 列；`/api-plan` 的 `PoolItem.repairCategory` 列
- `specs/truth/contracts/components/schemas/員工工作台.yaml` -> `PoolItem.repairCategory`

**Boundary**:
- 只改 `eng_pool_screen.dart` 單卡房號方塊的類別文字：`repairCategoryShortText(item.repairCategory)`；`ticketNo == null` 不畫的那一行保留。
- 補一支 widget 測試：`repairCategory` 是 `air_conditioning` 的單卡顯示「冷氣」、`other` 顯示「其他」，任何單卡文字都不是 `repair`。

**Test Scope**:
- `specs/truth/features/ios-app/工務單/工務視角.feature`
- `cd app/mobile && flutter test`

- [ ] T044 [BDD-GREEN] 讓 Test Scope 全綠（模擬器跑 `app/bdd/run-module.sh 工務單`，後端用含 Phase 4A–4D 的版本）
- [ ] T045 [BDD-REFACTOR] 在綠燈下確認工務首頁與工務單詳情讀類別的地方沒有同樣把任務類型當類別的寫法；有就一起改成讀工務單類別

## Phase 4F: 舊資料修補 V29

**Goal**: 上線時把卡在工務、沒有工務單的單補好（對應 truth-delta `/data-plan` 的 `repair_tickets` Note「舊資料修補（V29）」、`tasks` Note「V29 退回待派單」與 `/dsl-refine` 的 acceptance NOOP 列）。這一題沒有介面 feature，驗證是遷移測試加上線後的查核。

**Shared Must Read**:
- `specs/truth/data/data-model.dbml` -> `repair_tickets` Note「櫃檯轉單」「舊資料修補（V29，一次性）」；`tasks` Note「V29 退回待派單」；`tasks.task_no` note
- `plan.md` -> 已定的事「V29 修補舊資料」五個步驟與上線順序
- `tms/src/main/resources/db/migration/V25__task_no_answer_ended_at.sql:36-56`（T- 補號的做法）
- `tms/src/main/java/com/athena/tms/service/OutboxWriter.java` -> `enqueueTaskEvent`（:30-45）（outbox envelope 與兩把鍵）
- `specs/plans/006-engineering-pool-repair-ticket/features/acceptance/已經卡在工務的單上線後補好.feature`

**Boundary**:
- 只新增 `tms/src/main/resources/db/migration/V29__engineering_tasks_get_repair_tickets.sql`；既有 V1–V28 一個字都不改（含註解，否則 Flyway checksum 會壞）。
- 已結束（`done`、`inspected`、`closed`、`cancelled`）的任務不動。
- 只認「櫃檯派來的」與「住客經規則直接進來的」兩種；其餘任何一張讓遷移以例外停下，不搬動。

**Test Scope**:
- `tms/src/test/java/com/athena/tms/integration/V29EngineeringTaskBackfillTest.java`

- [ ] T046 [BDD-GREEN] 寫 V29 並讓 `V29EngineeringTaskBackfillTest` 全綠
  - 測試要涵蓋：
    - 櫃檯派來、待認領的單補成工務單（單號接續、類別 `other`、報修人＝`triaged` 操作者、建立時間＝`triaged` 時間、`type`=repair、非急件），沒有另寫事件；櫃檯派來、已認領還沒做完的單也補。
    - 住客經規則直接進來、待認領、`task_no` 是 NULL 的單：退回 `triage`／`frontdesk`／`other`，領到接續 `task_counters` 的 T- 號、不和既有號碼撞；`event_seq` 加一、多一筆 `triage_undone`（`actor_role`=system、payload 帶 dept、type、task_no、reason）、多一筆 kind `task_event` 的 outbox，兩把鍵與 `OutboxWriter.enqueueTaskEvent` 的寫法一致；照 dbml 的重放規則從事件重放得出同樣的任務列。
    - 沒有 `task_counters` 列、也沒有 `repair_ticket_counters` 列的飯店，遷移後兩個計數列都建出來、推到用掉的最後一號。
    - 兩間飯店各自接續自己的流水號。
    - 已完成的歷史單不動。
    - 遷移末段「部門工務、沒結束、沒有工務單」為 0。
    - 讓遷移以例外停下、而且任何一張單都沒被改的三種：`source` 是 staff、沒有派單紀錄的工務部門單；最新 `triaged` 的 `actor_id` 是 NULL 的單；住客經規則直接進來而狀態不是待認領的單。
- [ ] T047 [BDD-REFACTOR] 在綠燈下整理 V29 的 SQL：每一步處理哪一種單要從 WHERE 條件看得出來；註解只留程式看不出來的「為什麼」（例如住客經規則直接進來的單為什麼退回待派單而不是補工務單、為什麼櫃檯派來的單不另寫事件）

## Phase 4G: MODIFY Feature File - frontdesk-web/待派單/待派單清單.feature

**Goal**: 待派單頁頁尾的說明符合住客不直通工務。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/待派單/待派單清單.feature` -> Rule「頁尾說明哪些需求會來待派單頁」
- `truth-delta.md` -> `/dsl-refine` 的 `待派單清單.feature` 頁尾列

**Boundary**:
- 只改待派單頁的 `footer`：`app/web/src/i18n/locales/zh-TW.ts:363-364` 照 feature 原文；`en.ts`、`ja.ts`、`vi.ts` 的同一個鍵（:364-365）照同樣的意思翻譯，說清楚「給工務的住客需求也會來這裡、只有給房務的才直接進房務待認領」。

**Test Scope**:
- `bash app/web-bdd/run-feature.sh 待派單/待派單清單.feature desktop`
- `cd app/web && npx vitest run src/i18n/locales/locales.test.ts`（四個語系的鍵與參數一致）

- [ ] T048 [BDD-GREEN] 讓 Test Scope 全綠

## Phase 5: 回歸與實機驗收

**Goal**: 證明修好的是實機那條斷掉的流程，而且其他端沒有被拖垮。三題回歸都用 T002 同一組指令、比對 T002 那份基準。

- [ ] T049 後端全套與 App 單元
  - 跑後端全套（不加 `-Dtest`）與 `flutter test`；失敗清單必須是 T002 基準的子集，新增的失敗為 0。
- [ ] T050 櫃檯 Web 與住客頁
  - `bash app/web-bdd/run-feature.sh --keep frontdesk-web/ desktop`、`bash app/web-bdd/run-feature.sh --keep guest-web/ phone`，跑完 `--stop`。失敗清單必須是 T002 基準的子集。
- [ ] T051 員工 App 的工務單與待認領模組
  - `app/bdd/run-module.sh 工務單`、`app/bdd/run-module.sh 待認領`（模擬器，後端是本分支版本）。失敗清單必須是 T002 基準的子集。
- [ ] T052 手動測試環境上線 V29 並查核
  - 先備份：`pg_dump` 5433 的 `tms_manual` 與 `tms` 兩個資料庫到 `~/.tms-manual-env/backup-<時間>.sql`。
  - 先停舊版本：`bash scripts/manual-env/stop.sh`（後端與網頁一起停，網頁要重起才吃得到 T001 的代理）。
  - 打包：`JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./tms/mvnw -q -f tms/pom.xml -DskipTests package`。
  - 起新版：`bash scripts/manual-env/start.sh`。不跑 `seed.sh`，也不用 `up.sh`（`up.sh` 是使用者還沒提交的工作，不依賴它）；示範資料重灌會把卡住的 T-0024 洗掉，量不到修補。後端起不來時看 `start.sh` 印出的 log，V29 以例外停下的訊息會列出無法歸類的單。
  - 查核：`部門工務、沒結束、沒有工務單` 為 0 筆；T-0024 有工務單、出現在 `mt1` 的工務待認領；退回待派單的住客單都有 T- 單號、各有一筆 system 的 `triage_undone`；阿強的工務待認領分流磚總張數等於列表張數（修前 7 對 5）。截圖存到本 plan package 的 `evidence/`。
- [ ] T053 手動測試環境實機走一次完整流程
  - 住客在住客頁（5300 的 `/g/<代碼>`，請求要打到 8083）設備報修打一句不含「冷氣、空調、毛巾、吵」的話（例如「窗簾掛勾鬆脫」）送出 → 櫃檯 Web（5300）在待派單按「派給工務」→ 看板上這張單改顯示 `W-` 單號 → `mt1` 在 App 工務待認領看到它（房號方塊寫「其他」、來源「櫃檯」），認領、開始、附 1 張照片完成 → 主管帳號在 App 待驗收看到並驗收通過 → 住客頁這張需求顯示「已完成」。
  - 另走一次「住客打『冷氣不冷』」：單停在待派單、住客頁顯示「已送出 · 櫃檯」，不出現在工務待認領。
  - 每一步截圖存到 `evidence/`，在 `implement-notes.md` 記下每一步看到的畫面與單號。任何一步要靠人工補資料才能走下去就算失敗。
