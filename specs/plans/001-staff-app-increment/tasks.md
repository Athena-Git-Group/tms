# Tasks: 房務／工務 App 增量（後端）

**Plan Package**: `specs/plans/001-staff-app-increment`
**Core Inputs**: `spec.md`, `plan.md`, `research.md`, `truth-delta.md`, `specs/truth/techstack.md`, `specs/truth/contracts/**`, `specs/truth/data/**`, `specs/truth/features/backend/**`

**範圍說明**:
- 本輪只排後端。`specs/truth/features/ios-app/**` 依 research 決策 1 只是 App 測項：沒有 runner，App 也不在本 repo，所以不排 Feature phase，也不能宣告 GREEN。
- 沒有 `ui/**`：`/ui-plan`（plan.md Wave 2）已延後，所以本輪沒有 plan-side 產物 quality gate。
- 後端原始碼 `tms/tms` 目前是空的 gitlink。T001 取回後若發現既有 stepdef 已承接某句，該句的 `[BDD-RED]` 改按 `[BDD-ALIGN]` 執行；只改 marker，不改句型與語意。
- 基底套件為 `com.athena.tms`（T001 確認）。原始碼由 target/ 編譯產物反編譯重建：Spring Boot 4.1.1、Java 25、kafka-clients 4.2.1、Flyway V3、JWT 用 Nimbus JOSE；既有 Cucumber 7.20.1 stepdefs 位於 `integration/stepdefs/`（承接基線 01–06、13–15 feature，與本輪 truth DSL 句型不同，Phase 3 維持 `[BDD-RED]`，可參考其 utils）。排程實際用 Spring `@Scheduled`，無 Quartz 程式碼。

## Task Binding Contract

- 每個**開發任務**都必須對應 `truth-delta.md` 中的 ADD / MODIFY / DELETE / NOOP 語意。
- Phase 1 `Setup` 只做本輪新增技術的基礎建設、技術環境與最後的 smoke-test；不寫 DSL 語意、不寫產品行為。
- Phase 2 `Foundational` 只建立後續實作程式、測試共用元件、入口、fixture、helper 與落點骨架。
- Phase 3 `Test Alignment & Implementation` 的目的：在寫產品碼之前，先把本輪所有受影響 DSL 的自動化測試對齊最新版 truth。
- Truth 參照必須使用 `specs/truth/**` 路徑；plan 參照才使用當前 plan package 內相對路徑。
- Feature `Shared Must Read` 除 feature、同模組 DSL、對應 `truth-delta` 列外，還必須列入回追到的 planner 產物。

## Phase 1: Setup

**Goal**: 本輪新增的技術是後端 BDD runner（Cucumber-JVM ＋ Spring Boot Test）與 Testcontainers（PostgreSQL 16、Kafka）。先取回後端原始碼、加入套件與配置，讓 Cucumber 直接讀 `specs/truth/features/backend`，最後用 smoke-test 確認整套跑得起來。不寫 DSL 語意，不寫產品行為。

- [X] T001 取回後端原始碼並確認版本基線
  - Read:
    - `specs/truth/techstack.md` -> 後端
    - `research.md` -> 殘餘風險「後端程式碼目前不在本機」
  - 補上 `.gitmodules` 或改用正確來源，取回 `tms/tms`（gitlink 指向 `1b65c99`），確認 `./mvnw -f tms/pom.xml -q -DskipTests package` 成功。
  - 記下實際的 Spring Boot、Java、Kafka client 版本與 Java 基底套件，回填本檔的基底套件（已回填 `com.athena.tms`）。
  - 盤點既有 `src/test`：有沒有 Cucumber stepdef 與 Flyway migration 的最後版本號。只記錄，不改產品碼。

- [X] T002 加入 Cucumber-JVM 與 Testcontainers 測試依賴
  - Read:
    - `specs/truth/techstack.md` -> 測試與驗證
  - `tms/pom.xml` 的 test scope 加入 `io.cucumber:cucumber-java`、`io.cucumber:cucumber-spring`、`io.cucumber:cucumber-junit-platform-engine`、`org.junit.platform:junit-platform-suite`、`org.springframework.boot:spring-boot-testcontainers`、`org.testcontainers:postgresql`、`org.testcontainers:kafka`、`org.testcontainers:junit-jupiter`。
  - 版本用與 T001 確認的 Spring Boot 版本相容的版本，並在 pom 註明。Cucumber 與 Spring 7 的相容性是 research 列出的風險，不相容時停下回報，不降級 Spring Boot。

- [X] T003 配置 Cucumber 套件入口與 Testcontainers 環境
  - Read:
    - `specs/truth/techstack.md` -> 測試與驗證
    - `tms/src/test/` -> 測試掛載點
  - 建立 `@Suite` 入口與 `junit-platform.properties`：features 路徑指到 repo 的 `specs/truth/features/backend`（不複製 feature 檔），glue 指到 `com.athena.tms.bdd`，語言 zh-TW 由 feature 檔頭決定。
  - 建立 `@CucumberContextConfiguration` ＋ `@SpringBootTest(webEnvironment = RANDOM_PORT)`；PostgreSQL 16 與 Kafka 容器用 `@ServiceConnection`，Flyway 照常在容器上執行。
  - 只開環境；不寫任何 stepdef。

- [X] T004 smoke-test 確認 Cucumber、Spring context 與容器都起得來
  - Read:
    - `specs/truth/techstack.md` -> 測試與驗證
  - 執行 `./mvnw -f tms/pom.xml verify -Dcucumber.filter.name=^$`（不跑任何場景），確認 Spring context 在 Testcontainers 上啟動、Flyway 套用成功、Cucumber 能解析全部 19 個 feature（undefined step 是預期結果）。
  - 不跑本輪 Feature，不寫 stepdef 語意。

## Phase 2: Foundational

**Goal**: 只建立後續 stepdef 與產品碼都會用到的共用元件、入口、fixture、helper 與落點骨架，讓 Phase 3 的 151 句不各自重開檔、不各自發明呼叫或查庫的方式。語意對齊不放這裡。

- [X] T005 建立情境上下文（Scenario World）
  - Read:
    - `truth-delta.md` -> `/dsl-refine` 全部 rows
    - `specs/truth/features/backend/dsl.md` -> 共通實作約定（任務識別、權威狀態、寫入）
  - 只做：建立 `@ScenarioScope` 的 `ScenarioWorld`，記錄姓名到 staff id 與 JWT、房號到 task id、工務單號到 task id、最後一次回應、最後產生的照片 id、回答 id、提問 id。
  - 不做：不寫任何句子的 Given、When、Then 語意。

- [X] T006 建立 API 呼叫 helper
  - Read:
    - `specs/truth/contracts/openapi.yaml` -> servers、paths
    - `specs/truth/contracts/components/parameters.yaml` -> `IdempotencyKey`、`GuestSession`
  - 只做：以 `RestClient` 包裝 RANDOM_PORT 的 `/api/v1`；可指定員工 JWT 或住客 `X-Guest-Session`；每次寫入預設帶新的 `Idempotency-Key`，也可指定重用；回應存進 `ScenarioWorld`；錯誤 body 解析成 `Error`。
  - 不做：不寫任何 operation 的業務斷言。

- [X] T007 建立權威狀態查詢與 outbox 等待 helper
  - Read:
    - `specs/truth/data/data-model.dbml` -> `Table tasks`、`task_events`、`outbox`
    - `specs/truth/contracts/components/schemas/shared.yaml` -> `PushPayload`
  - 只做：`JdbcClient` 讀取 tasks、task_events、outbox 等資料表的 helper；等待 outbox relay 送出與推播消費者寫入資料列的輪詢工具（含逾時）；把 push payload 解析成 `PushPayload`。
  - 不做：不寫任何 Then 的必查清單。

- [X] T008 建立可調時鐘與排程觸發 helper
  - Read:
    - `research.md` -> 決策 4
    - `specs/truth/techstack.md` -> 後端（時間來源）
  - 只做：產品端提供 `java.time.Clock` bean 並讓現有程式改由它取得現在時間；測試端以 `@Primary` 的可調 `MutableClock` 取代，提供「設為今日某時刻」「往前撥 N 分鐘」；提供依 job 名稱立即觸發 Quartz job 並等待完成的 helper。
  - 不做：不實作班末掃描或任何提醒 job 的內容。

- [X] T009 建立狀態與代碼用語對照
  - Read:
    - `specs/truth/features/backend/dsl.md` -> 狀態用語對照、部門用語
    - `specs/truth/features/backend/查房/dsl.md` -> 查房結果用語
  - 只做：把中文狀態、部門、查房結果、來源標籤、工務單類別、PoolCategory 對應到權威值的單一對照類別，供全部 stepdef 共用。
  - 不做：不加 dsl.md 沒寫的用語。

- [X] T010 建立測試資料 fixture 入口
  - Read:
    - `specs/truth/data/data-model.dbml` -> `Table staff_accounts`、`rooms`、`tasks`、`task_events`、`plan_rooms`、`reservations_cache`、`sop_items`、`handovers`、`room_memory`
  - 只做：建立 builder 入口：員工與班次、房間（樓層取房號首位）、任務含可推導狀態的事件歷程、待接池任務、交班單 snapshot、room_memory、訂房快取。事件時間一律以 `MutableClock` 為基準。
  - 不做：不決定任何句子的預設參數；預設值由 Phase 3 依 dsl.md 填入。

- [X] T011 建立模擬 PMS adapter 的測試入口
  - Read:
    - `specs/truth/features/backend/dsl.md` -> `從 PMS 拉今日房態，"{房號}" 的訂房帶 "{屬性}" 屬性` 的怎麼做
  - 只做：測試可以取得模擬 PmsAdapter，直接推送訂單事件並等處理完；也可以讀取 PMS 回寫消費者收到的房況值。
  - 不做：不實作準備工單指派或回寫值的產品規則。

- [X] T012 新增 Flyway migration：本 plan 的欄位、資料表與索引
  - Read:
    - `truth-delta.md` -> `/data-plan` 全部 rows
    - `specs/truth/data/data-model.dbml` -> `Table tasks` 新欄位與索引、8 張新表、新 Ref、CHECK 註記
  - 只做：依 data truth 新增 tasks 欄位、`uq_tasks_one_doing_per_staff`、待接池排序索引、8 張新表與複合外鍵；更新 CHECK（source、cancel_reason、task_events.type、tenant_config.key）。另補 V1 漏建的 3 張基線表 `plan_rooms`、`handovers`、`room_memory`（data truth 已定義、舊 POC 未實作；T010 的 fixture stub 依賴它們）。automation_rules 表（data truth 若已定義）一併檢查。
  - 不做：不寫任何狀態轉移、查詢或 API 行為；不回填既有資料以外的業務值。

- [X] T013 預留產品碼與 stepdef 落點
  - Read:
    - `plan.md` -> 原始碼結構
    - `specs/truth/contracts/openapi.yaml` -> 本 plan 新增的 paths
  - 只做：依 plan.md 建立 `repair`、`handover`、`kb`、`dispatch` 等套件骨架；為新 path 建立 controller 方法殼（回 501）；建立 stepdef 目錄 `tms/src/test/java/com/athena/tms/bdd/steps/{common,taskexecution,shiftend,inspection,repair,pool,today,handover,kb,push}/`。
  - 不做：不寫 controller 的業務邏輯，不寫 stepdef 內容。

## Phase 3: Test Alignment & Implementation

**Goal**: 把本輪 Feature 用到的 DSL 自動化測試對齊最新版 truth；含 truth-delta 有改的句，以及本輪 Feature 用到、尚無 stepdef 的句。不寫產品行為。

**DSL 參照**:
- 每一句只屬於一個權威 `dsl.md`：同模組 `specs/truth/features/{介面}/{模組}/dsl.md`，或介面根 `specs/truth/features/{介面}/dsl.md`。不得掃其他模組。
- 本輪 151 句：22 句在介面根 `specs/truth/features/backend/dsl.md`，129 句分屬 9 個模組的 `specs/truth/features/backend/{模組}/dsl.md`。每個 task 標明它的權威檔。
- 讀法：用 task title 的句型對到該檔那一列，以 `StepDef 實作語意` 當作測試程式碼語意。Given / When 讀 `怎麼做`、`權威狀態落地`、`回寫`；Then 讀 `必查`（`呈現結果`、`權威狀態`、`再讀確認`、`跨視角`、`不該發生`）。
- `truth-delta.md` 只告訴這句是 ADD / MODIFY / DELETE。語意以 `dsl.md` 那一列為準，不得用 feature 措辭或舊 stepdef 自行發明。

**Markers**:
- `[BDD-ALIGN]`：`MODIFY`。既有 stepdef 還在，但語意是舊 truth。依 `dsl.md` 該列改測試，讓它表達最新版 `StepDef 實作語意`。
- `[BDD-REMOVE]`：`DELETE`。此句已不是 truth。移除或改寫仍綁這句的 stepdef / assertion，不得留下保護舊行為的測試。
- `[BDD-RED]`：`ADD`，或本輪 Feature 用到、尚無 stepdef 的句。依 `dsl.md` 該列寫出 stepdef。完成時這句可被跑到，失敗只能是 assertion 或產品行為，不能是 undefined step。
- 三個 marker 都只動測試層，不寫產品碼。
- 本輪 truth-delta 的 `/dsl-refine` 全部是 ADD 或語意不變的搬移/合併（含同輪回補的實作語意），所以 151 句都是 `[BDD-RED]`；若 T001 發現既有 stepdef 承接同句，改按 `[BDD-ALIGN]` 執行。

**Shared Must Read**:
- `specs/truth/features/backend/dsl.md` -> 共通實作約定、狀態用語對照、Given／When／Then 16 列
- `specs/truth/features/backend/{模組}/dsl.md` -> 該 task 那一列
- `truth-delta.md` -> `/dsl-refine` 全部 rows
- `specs/truth/contracts/**`、`specs/truth/data/data-model.dbml` -> 只讀該列 `StepDef 實作語意` 點名的 operation、schema、資料表與欄位
- `tms/src/test/java/com/athena/tms/bdd/steps/` -> T005–T013 建好的 helper 與落點

**Boundary**:
- 一條 DSL 一個 task。stepdef 寫在 `tms/src/test/java/com/athena/tms/bdd/steps/<模組套件>/`；介面根的句寫在 `common/`。
- 只改該句的 stepdef / assertion / 直接依賴的 helper。
- 不寫產品碼。
- review 啟動 subagent；本輪所有 Test Scope 不得再有 undefined step，失敗只能是 assertion 或產品行為。有 issues 就修正再 review，直到沒有任何問題。通過前不解鎖 Phase 4。

**Parallel Hint**:
- T014–T164 各派一個獨立 subagent（同模組的句可由同一個 subagent 依序處理，避免同檔衝突）；T165 等全部回來再啟動 subagent 來 review。

- [X] T014 [P] [BDD-RED] `Given: 飯店有以下員工：`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T015 [P] [BDD-RED] `Given: 現在時間是 "{時刻}"`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T016 [P] [BDD-RED] `Given: "{員工}" 名下有以下任務：`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T017 [P] [BDD-RED] `Given: 房務待接池有以下任務：`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T018 [P] [BDD-RED] `When: "{員工}" 開始 "{任務}"`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T019 [P] [BDD-RED] `When: "{員工}" 恢復 "{任務}"`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T020 [P] [BDD-RED] `When: "{員工}" 完成 "{任務}"`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T021 [P] [BDD-RED] `When: "{員工}" 判定 "{任務}" 查房全部項目通過`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T022 [P] [BDD-RED] `Then: 這次操作被拒絕`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T023 [P] [BDD-RED] `Then: 這次操作被拒絕，提示 "{提示}"`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T024 [P] [BDD-RED] `Then: "{任務}" 的狀態為 "{狀態}"`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T025 [P] [BDD-RED] `Then: "{任務}" 的負責人是 "{員工}"`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T026 [P] [BDD-RED] `Then: "{任務}" 的停住原因為 "{原因}"`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T027 [P] [BDD-RED] `Then: "{任務}" 的已做時間為 {分鐘} 分鐘`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T028 [P] [BDD-RED] `Then: "{員工}" 收到推播 "{標題}"`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T029 [P] [BDD-RED] `Then: "{員工}" 收到以下推播：`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T030 [P] [BDD-RED] `Given: "{員工}" 已停用`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T031 [P] [BDD-RED] `Given: 飯店的逾時升級設定如下：`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T032 [P] [BDD-RED] `When: 時間來到 "{時刻}"`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T033 [P] [BDD-RED] `Then: "{員工}" 沒有收到任何推播`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T034 [P] [BDD-RED] `Given: 打掃 SOP 共有以下項目：`
  - DSL: `specs/truth/features/backend/任務執行/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/taskexecution/`

- [X] T035 [P] [BDD-RED] `Given: "{員工}" 已勾選 "{任務}" 的以下 SOP 項：`
  - DSL: `specs/truth/features/backend/任務執行/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/taskexecution/`

- [X] T036 [P] [BDD-RED] `Given: "{任務}" 已中斷 {分鐘} 分鐘`
  - DSL: `specs/truth/features/backend/任務執行/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/taskexecution/`

- [X] T037 [P] [BDD-RED] `Given: "{任務}" 的進行歷程如下：`
  - DSL: `specs/truth/features/backend/任務執行/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/taskexecution/`

- [X] T038 [P] [BDD-RED] `When: "{員工}" 以原因 "{原因}" 延後 "{任務}"`
  - DSL: `specs/truth/features/backend/任務執行/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/taskexecution/`

- [X] T039 [P] [BDD-RED] `When: "{員工}" 以 {字數} 字的自填原因延後 "{任務}"`
  - DSL: `specs/truth/features/backend/任務執行/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/taskexecution/`

- [X] T040 [P] [BDD-RED] `When: "{員工}" 以原因 "{原因}" 中斷 "{任務}"`
  - DSL: `specs/truth/features/backend/任務執行/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/taskexecution/`

- [X] T041 [P] [BDD-RED] `Then: 房務待接池不包含 "{任務}"`
  - DSL: `specs/truth/features/backend/任務執行/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/taskexecution/`

- [X] T042 [P] [BDD-RED] `Then: "{任務}" 已勾選的 SOP 項為：`
  - DSL: `specs/truth/features/backend/任務執行/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/taskexecution/`

- [X] T043 [P] [BDD-RED] `Then: "{任務}" 的實際工時為 {分鐘} 分鐘`
  - DSL: `specs/truth/features/backend/任務執行/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/taskexecution/`

- [X] T044 [P] [BDD-RED] `Then: "{任務}" 記錄為跳過的 SOP 項為：`
  - DSL: `specs/truth/features/backend/任務執行/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/taskexecution/`

- [X] T045 [P] [BDD-RED] `When: "{員工}" 的班末掃描執行`
  - DSL: `specs/truth/features/backend/班末掃描/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/shiftend/`

- [X] T046 [P] [BDD-RED] `When: "{主管}" 以交代 "{交代}" 把 "{任務}" 退回待接池`
  - DSL: `specs/truth/features/backend/班末掃描/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/shiftend/`

- [X] T047 [P] [BDD-RED] `Then: 房務待接池包含以下任務：`
  - DSL: `specs/truth/features/backend/班末掃描/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/shiftend/`

- [X] T048 [P] [BDD-RED] `Then: "{員工}" 沒有收到關於 "{任務}" 的推播`
  - DSL: `specs/truth/features/backend/班末掃描/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/shiftend/`

- [X] T049 [P] [BDD-RED] `Given: 飯店設定免查房開關為 "{開關}"`
  - DSL: `specs/truth/features/backend/查房/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/inspection/`

- [X] T050 [P] [BDD-RED] `Given: "{員工}" 在免查房開關開啟時開始了 "{任務}" 的續住整理`
  - DSL: `specs/truth/features/backend/查房/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/inspection/`

- [X] T051 [P] [BDD-RED] `Given: "{員工}" 在免查房開關關閉時開始了 "{任務}" 的續住整理`
  - DSL: `specs/truth/features/backend/查房/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/inspection/`

- [X] T052 [P] [BDD-RED] `Given: 今日的打掃查房結果如下：`
  - DSL: `specs/truth/features/backend/查房/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/inspection/`

- [X] T053 [P] [BDD-RED] `When: "{主管}" 抽查 "{任務}"，判定 "{項目}" 不合格`
  - DSL: `specs/truth/features/backend/查房/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/inspection/`

- [X] T054 [P] [BDD-RED] `When: "{主管}" 查看今日查房合格率`
  - DSL: `specs/truth/features/backend/查房/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/inspection/`

- [X] T055 [P] [BDD-RED] `Then: "{任務}" 的查房結果為 "{結果}"`
  - DSL: `specs/truth/features/backend/查房/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/inspection/`

- [X] T056 [P] [BDD-RED] `Then: "{任務}" 回寫 PMS 的房況為 "{房況}"`
  - DSL: `specs/truth/features/backend/查房/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/inspection/`

- [X] T057 [P] [BDD-RED] `Then: "{任務}" 的重做次數為 {次數}`
  - DSL: `specs/truth/features/backend/查房/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/inspection/`

- [X] T058 [P] [BDD-RED] `Then: "{任務}" 沒有任何 SOP 勾記`
  - DSL: `specs/truth/features/backend/查房/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/inspection/`

- [X] T059 [P] [BDD-RED] `Then: 今日查房合格率為 "{比例}"`
  - DSL: `specs/truth/features/backend/查房/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/inspection/`

- [X] T060 [P] [BDD-RED] `Given: 飯店的「回寫房態」開關為 "{開關}"`
  - DSL: `specs/truth/features/backend/查房/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/inspection/`

- [X] T061 [P] [BDD-RED] `Given: 飯店的 PMS 為手動匯入模式`
  - DSL: `specs/truth/features/backend/查房/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/inspection/`

- [X] T062 [P] [BDD-RED] `Given: "{員工}" 的角色沒有查房判定（inspect_submit）權限`
  - DSL: `specs/truth/features/backend/查房/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/inspection/`

- [X] T063 [P] [BDD-RED] `When: "{主管}" 查看查房者名單`
  - DSL: `specs/truth/features/backend/查房/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/inspection/`

- [X] T064 [P] [BDD-RED] `Then: "{任務}" 沒有回寫 PMS，同步紀錄記一筆未回寫`
  - DSL: `specs/truth/features/backend/查房/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/inspection/`

- [X] T065 [P] [BDD-RED] `Then: "{任務}" 的任務事件依序包含 {事件清單}`
  - DSL: `specs/truth/features/backend/查房/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/inspection/`

- [X] T066 [P] [BDD-RED] `Then: 查房者名單包含 "{員工}"`
  - DSL: `specs/truth/features/backend/查房/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/inspection/`

- [X] T067 [P] [BDD-RED] `Then: 查房者名單不包含 "{員工}"`
  - DSL: `specs/truth/features/backend/查房/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/inspection/`

- [X] T068 [P] [BDD-RED] `Given: 本飯店下一張工務單號為 "{單號}"`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T069 [P] [BDD-RED] `Given: 已有以下工務單：`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T070 [P] [BDD-RED] `Given: "{房號}" 今天是 VIP 房，住客 "{時刻}" 入住`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T071 [P] [BDD-RED] `Given: "{員工}" 已對 "{房號}" 選擇工務單類別 "{類別}" 並看到 "{單號}" 的重複提示`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T072 [P] [BDD-RED] `Given: "{員工}" 為 "{房號}" 的工務單準備了 {張數} 張照片，其中 {失敗數} 張上傳失敗`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T073 [P] [BDD-RED] `Given: "{員工}" 已重新上傳失敗的那張照片並成功`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T074 [P] [BDD-RED] `Given: "{單號}" 已由 "{工務}" 完修`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T075 [P] [BDD-RED] `When: "{員工}" 中斷 "{任務}" 並開立以下工務單：`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T076 [P] [BDD-RED] `When: "{員工}" 從 "{任務}" 開立以下工務單：`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T077 [P] [BDD-RED] `When: "{員工}" 從 "{任務}" 開立以下工務單，同一請求共送出 {次數} 次：`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T078 [P] [BDD-RED] `When: "{員工}" 中斷 "{任務}" 並以已準備的照片開立水電工務單`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T079 [P] [BDD-RED] `When: "{員工}" 對 "{房號}" 選擇工務單類別 "{類別}"`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T080 [P] [BDD-RED] `When: "{員工}" 補 {張數} 張照片到 "{單號}"`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T081 [P] [BDD-RED] `When: "{員工}" 撤回 "{單號}"`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T082 [P] [BDD-RED] `When: "{工務}" 附 {張數} 張完修照片、處理說明 "{說明}" 完成 "{單號}"`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T083 [P] [BDD-RED] `When: "{工務}" 不附完修照片完成 "{單號}"`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T084 [P] [BDD-RED] `Then: 系統建立工務單 "{單號}"，房號 "{房號}"、報修人 "{員工}"`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T085 [P] [BDD-RED] `Then: 系統沒有建立工務單`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T086 [P] [BDD-RED] `Then: 工務待接池包含 "{單號}"`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T087 [P] [BDD-RED] `Then: 工務待接池不包含 "{單號}"`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T088 [P] [BDD-RED] `Then: 工務待接池順序為 {單號清單}`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T089 [P] [BDD-RED] `Then: 系統建議設為急件，提示 "{提示}"`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T090 [P] [BDD-RED] `Then: 以下員工都收到 "{單號}" 的急件推播：`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T091 [P] [BDD-RED] `Then: 系統提示同房已有未結案的工務單 "{單號}"，狀態為 "{狀態}"`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T092 [P] [BDD-RED] `Then: "{單號}" 共有 {張數} 張照片`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T093 [P] [BDD-RED] `Then: "{房號}" 未結案的 "{類別}" 工務單只有 {張數} 張`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T094 [P] [BDD-RED] `Then: "{單號}" 的處理說明為 "{說明}"`
  - DSL: `specs/truth/features/backend/工務單/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/repair/`

- [X] T095 [P] [BDD-RED] `Given: "{房號}" 今天沒有打掃負責人`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T096 [P] [BDD-RED] `When: "{員工}" 查看待接池`
  - DSL: `specs/truth/features/backend/待接池/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/pool/`

- [X] T097 [P] [BDD-RED] `When: "{員工}" 只看 "{類別}" 類的待接池`
  - DSL: `specs/truth/features/backend/待接池/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/pool/`

- [X] T098 [P] [BDD-RED] `When: 從 PMS 拉今日房態，"{房號}" 的訂房帶 "{屬性}" 屬性`
  - DSL: `specs/truth/features/backend/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/common/`

- [X] T099 [P] [BDD-RED] `Then: 待接池順序為 {房號清單}`
  - DSL: `specs/truth/features/backend/待接池/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/pool/`

- [X] T100 [P] [BDD-RED] `Then: 標示同樓層的單為 {房號清單}`
  - DSL: `specs/truth/features/backend/待接池/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/pool/`

- [X] T101 [P] [BDD-RED] `Then: 待接池中 "{任務}" 的資訊如下：`
  - DSL: `specs/truth/features/backend/待接池/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/pool/`

- [X] T102 [P] [BDD-RED] `Then: 待接池分類筆數如下：`
  - DSL: `specs/truth/features/backend/待接池/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/pool/`

- [X] T103 [P] [BDD-RED] `Then: "{房號}" 的準備工單在 "{員工}" 名下，狀態為 "{狀態}"`
  - DSL: `specs/truth/features/backend/待接池/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/pool/`

- [X] T104 [P] [BDD-RED] `Then: "{房號}" 的準備工單在房務待接池`
  - DSL: `specs/truth/features/backend/待接池/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/pool/`

- [X] T105 [P] [BDD-RED] `Given: 飯店有以下自動化規則：`
  - DSL: `specs/truth/features/backend/待接池/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/pool/`

- [X] T106 [P] [BDD-RED] `Given: 系統從 "{開始時刻}" 停機到 "{結束時刻}"`
  - DSL: `specs/truth/features/backend/待接池/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/pool/`

- [X] T107 [P] [BDD-RED] `When: 匯入今日房態 CSV，"{房號}" 的訂房帶 "{屬性}" 屬性`
  - DSL: `specs/truth/features/backend/待接池/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/pool/`

- [X] T108 [P] [BDD-RED] `When: "{主管}" 刪除 "{規則名}" 規則`
  - DSL: `specs/truth/features/backend/待接池/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/pool/`

- [X] T109 [P] [BDD-RED] `When: "{主管}" 停用 "{規則名}" 規則`
  - DSL: `specs/truth/features/backend/待接池/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/pool/`

- [X] T110 [P] [BDD-RED] `Then: 待接池中 "{任務}" 的位置顯示為 "公共區域"`
  - DSL: `specs/truth/features/backend/待接池/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/pool/`

- [X] T111 [P] [BDD-RED] `Then: "{房號}" 今天只有一張準備工單`
  - DSL: `specs/truth/features/backend/待接池/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/pool/`

- [X] T112 [P] [BDD-RED] `Then: "{內容}" 的排程工單在房務待接池`
  - DSL: `specs/truth/features/backend/待接池/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/pool/`

- [X] T113 [P] [BDD-RED] `Then: 今天 "{規則名}" 規則只建立一張工單`
  - DSL: `specs/truth/features/backend/待接池/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/pool/`

- [X] T114 [P] [BDD-RED] `Then: "{房號}" 的準備工單的到期時間為 "{時刻}"`
  - DSL: `specs/truth/features/backend/待接池/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/pool/`

- [X] T115 [P] [BDD-RED] `When: "{員工}" 查看今日進度`
  - DSL: `specs/truth/features/backend/今日進度/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/today/`

- [X] T116 [P] [BDD-RED] `Then: 今日進度的 "{項目}" 為 "{值}"`
  - DSL: `specs/truth/features/backend/今日進度/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/today/`

- [X] T117 [P] [BDD-RED] `Then: 待開始的單推薦順序為 {房號清單}`
  - DSL: `specs/truth/features/backend/今日進度/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/today/`

- [X] T118 [P] [BDD-RED] `Then: 今日進度包含連續零退回天數`
  - DSL: `specs/truth/features/backend/今日進度/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/today/`

- [X] T119 [P] [BDD-RED] `Given: "{員工}" {時刻} 凍結的交班單有以下項目：`
  - DSL: `specs/truth/features/backend/房間交辦/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/handover/`

- [X] T120 [P] [BDD-RED] `Given: 櫃檯 "{員工}" 為 "{房號}" 留了備註 "{內容}"`
  - DSL: `specs/truth/features/backend/房間交辦/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/handover/`

- [X] T121 [P] [BDD-RED] `Given: "{員工}" 已接手 "{房號}"，原負責 "{原負責人}" {分鐘} 分鐘前留下交代 "{交代}"`
  - DSL: `specs/truth/features/backend/房間交辦/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/handover/`

- [X] T122 [P] [BDD-RED] `Given: "{員工}" 已開始 "{任務}"`
  - DSL: `specs/truth/features/backend/房間交辦/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/handover/`

- [X] T123 [P] [BDD-RED] `Given: "{房號}" 的退房清潔由 "{原負責人}" 清完，"{主管}" 判定 "{項目}" 不合格`
  - DSL: `specs/truth/features/backend/房間交辦/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/handover/`

- [X] T124 [P] [BDD-RED] `Given: "{員工}" 已下班，"{房號}" 改由 "{接手人}" 接手`
  - DSL: `specs/truth/features/backend/房間交辦/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/handover/`

- [X] T125 [P] [BDD-RED] `When: "{員工}" 打開 "{任務}" 的任務`
  - DSL: `specs/truth/features/backend/房間交辦/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/handover/`

- [X] T126 [P] [BDD-RED] `When: "{員工}" 展開 "{任務}" 的交辦`
  - DSL: `specs/truth/features/backend/房間交辦/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/handover/`

- [X] T127 [P] [BDD-RED] `Then: "{任務}" 的交辦如下：`
  - DSL: `specs/truth/features/backend/房間交辦/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/handover/`

- [X] T128 [P] [BDD-RED] `Then: "{任務}" 的交辦對 "{員工}" 為 "{已讀狀態}"`
  - DSL: `specs/truth/features/backend/房間交辦/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/handover/`

- [X] T129 [P] [BDD-RED] `Then: "{任務}" 對 "{員工}" 的已讀紀錄只有 {筆數} 筆`
  - DSL: `specs/truth/features/backend/房間交辦/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/handover/`

- [X] T130 [P] [BDD-RED] `Then: "{任務}" 的退回資訊為 "{退回資訊}"`
  - DSL: `specs/truth/features/backend/房間交辦/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/handover/`

- [X] T131 [P] [BDD-RED] `Then: "{任務}" 的交辦不包含主管交代`
  - DSL: `specs/truth/features/backend/房間交辦/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/handover/`

- [X] T132 [P] [BDD-RED] `Given: "{房號}" 的房間記錄有以下項目：`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T133 [P] [BDD-RED] `Given: "{房號}" 沒有任何房間記錄`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T134 [P] [BDD-RED] `Given: "{員工}" 在 "{任務}" 剛跳過 SOP 項 "{項目}"`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T135 [P] [BDD-RED] `Given: "{房號}" 是今天的 VIP 房`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T136 [P] [BDD-RED] `Given: SOP "{標題}" 的內容為 "{內容}"`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T137 [P] [BDD-RED] `Given: "{房號}" 目前的住客姓名為 "{姓名}"`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T138 [P] [BDD-RED] `Given: "{員工}" 已在 "{任務}" 問過 "{問題}"`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T139 [P] [BDD-RED] `Given: "{員工}" 已把 "{問題}" 轉問 "{主管}"`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T140 [P] [BDD-RED] `When: "{員工}" 在 "{任務}" 打開查一下`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T141 [P] [BDD-RED] `When: "{員工}" 在 "{任務}" 問 "{問題}"`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T142 [P] [BDD-RED] `When: "{員工}" 對這則回答回饋 "{回饋}"`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T143 [P] [BDD-RED] `When: "{員工}" 對這個問題按問主管`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T144 [P] [BDD-RED] `When: "{主管}" 回覆這個問題 "{回覆}"`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T145 [P] [BDD-RED] `Then: 推薦問題依序為：`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T146 [P] [BDD-RED] `Then: 第一個推薦問題為 "{問題}"`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T147 [P] [BDD-RED] `Then: 回答為 "{回答}"`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T148 [P] [BDD-RED] `Then: 回答附以下出處：`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T149 [P] [BDD-RED] `Then: 系統回覆查不到 "{房號}" 的相關記錄，且沒有任何回答內容`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T150 [P] [BDD-RED] `Then: 查一下稽核紀錄有以下一筆：`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T151 [P] [BDD-RED] `Then: "{主管}" 收到員工提問 "{問題}"`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T152 [P] [BDD-RED] `Then: "{員工}" 收到推播內容 "{內容}"`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T153 [P] [BDD-RED] `Then: 回答與出處都不包含 "{文字}"`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T154 [P] [BDD-RED] `Then: SOP 管理者 "{主管}" 收到通知，需要檢查 "{SOP 標題}"`
  - DSL: `specs/truth/features/backend/查一下/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/kb/`

- [X] T155 [P] [BDD-RED] `When: 住客在 "{房號}" 掃碼要 "{品項}"`
  - DSL: `specs/truth/features/backend/推播/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/push/`

- [X] T156 [P] [BDD-RED] `When: "{主管}" 查房 "{任務}"，判定 "{項目}" 不合格`
  - DSL: `specs/truth/features/backend/推播/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/push/`

- [X] T157 [P] [BDD-RED] `When: "{主管}" 以原因 "{原因}" 作廢 "{任務}"`
  - DSL: `specs/truth/features/backend/推播/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/push/`

- [X] T158 [P] [BDD-RED] `When: "{主管}" 把 "{任務}" 改派給 "{員工}"`
  - DSL: `specs/truth/features/backend/推播/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/push/`

- [X] T159 [P] [BDD-RED] `When: 住客撤回 "{房號}" 的需求`
  - DSL: `specs/truth/features/backend/推播/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/push/`

- [X] T160 [P] [BDD-RED] `When: "{員工}" 請假`
  - DSL: `specs/truth/features/backend/推播/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/push/`

- [X] T161 [P] [BDD-RED] `Given: 住客 10 分鐘內已在 "{房號}" 送出 {筆數} 筆需求`
  - DSL: `specs/truth/features/backend/推播/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/push/`

- [X] T162 [P] [BDD-RED] `Then: 這次請求被擋下，提示住客洽櫃檯`
  - DSL: `specs/truth/features/backend/推播/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/push/`

- [X] T163 [P] [BDD-RED] `Then: "{房號}" 今天沒有 "{品項}" 的需求單`
  - DSL: `specs/truth/features/backend/推播/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/push/`

- [X] T164 [P] [BDD-RED] `Then: "{員工}" 只收到一次推播 "{標題}"`
  - DSL: `specs/truth/features/backend/推播/dsl.md`；寫入 `tms/src/test/java/com/athena/tms/bdd/steps/push/`

- [X] T165 subagent review (phase quality gate)

## Phase 4A: ADD Feature File - backend/任務執行/本人延後與開工.feature

**Goal**: 讓本人延後、一次一間、中斷恢復與分段工時成立。本 phase 只讓「本人延後與開工」全綠。

**Shared Must Read**:
- `specs/truth/features/backend/任務執行/本人延後與開工.feature` -> `功能: 本人延後與開工`
- `specs/truth/features/backend/任務執行/dsl.md` -> `When: "{員工}" 以 {字數} 字的自填原因延後 "{任務}"`、`When: "{員工}" 以原因 "{原因}" 延後 "{任務}"`、`Then: 房務待接池不包含 "{任務}"`
- `specs/truth/features/backend/dsl.md` -> `Given: "{員工}" 名下有以下任務：`、`Given: 飯店有以下員工：`、`When: "{員工}" 開始 "{任務}"`、`Then: "{任務}" 的狀態為 "{狀態}"`、`Then: "{任務}" 的負責人是 "{員工}"`、`Then: 這次操作被拒絕`、`Then: "{任務}" 的停住原因為 "{原因}"`
- `truth-delta.md` -> `/dsl-refine` ADD `specs/truth/features/backend/任務執行/`；`/dsl-refine` MODIFY 實作語意回補（任務執行/dsl.md、班末掃描/dsl.md）
- `specs/truth/contracts/groups/現場作業/任務.yaml` -> `tasksStart`、`tasksDefer`、`tasksInterrupt`、`tasksResume`、`tasksComplete`、`tasksSopCheck`、`readTasks`
- `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `listDeptPool`
- `specs/truth/contracts/components/schemas/任務.yaml` -> `HoldReasonRequest`、`TaskDetail`、`tasks`；`components/schemas/shared.yaml` -> `Error`（doing_task_exists、reason_invalid）
- `specs/truth/data/data-model.dbml` -> `Table tasks`（worked_sec、segment_started_at、hold_reason、deferred_by_self、`uq_tasks_one_doing_per_staff`、Note 的轉移）、`Table task_events`、`Table task_sop_checks`、`Table sop_items`
- `truth-delta.md` -> 本模組對應的 `/api-plan`、`/data-plan` rows

**Boundary**:
- 只實作本人延後、開工、中斷、恢復、勾記、完成與工時；不做班末掃描、工務單、免查。
- 狀態變更一律經任務引擎的條件式 UPDATE，不先 SELECT 再 UPDATE；寫入都走冪等鍵（NFR-005、NFR-006）。

**Test Scope**:
- `specs/truth/features/backend/任務執行/本人延後與開工.feature`

- [X] T166 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T167 [BDD-REFACTOR] 在綠燈下整理本 feature 新增的產品碼與 stepdef 重複，並重跑已完成 phase 的 Test Scope 確認不退化

## Phase 4B: ADD Feature File - backend/任務執行/一次只做一間.feature

**Goal**: 讓本人延後、一次一間、中斷恢復與分段工時成立。本 phase 只讓「一次只做一間」全綠。

**Shared Must Read**:
- `specs/truth/features/backend/任務執行/一次只做一間.feature` -> `功能: 一次只做一間`
- `specs/truth/features/backend/dsl.md` -> `Given: "{員工}" 名下有以下任務：`、`Given: 飯店有以下員工：`、`When: "{員工}" 開始 "{任務}"`、`Then: 這次操作被拒絕，提示 "{提示}"`、`Then: "{任務}" 的狀態為 "{狀態}"`
- `truth-delta.md` -> `/dsl-refine` ADD `specs/truth/features/backend/任務執行/`；`/dsl-refine` MODIFY 實作語意回補（任務執行/dsl.md、班末掃描/dsl.md）
- `specs/truth/contracts/groups/現場作業/任務.yaml` -> `tasksStart`、`tasksDefer`、`tasksInterrupt`、`tasksResume`、`tasksComplete`、`tasksSopCheck`、`readTasks`
- `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `listDeptPool`
- `specs/truth/contracts/components/schemas/任務.yaml` -> `HoldReasonRequest`、`TaskDetail`、`tasks`；`components/schemas/shared.yaml` -> `Error`（doing_task_exists、reason_invalid）
- `specs/truth/data/data-model.dbml` -> `Table tasks`（worked_sec、segment_started_at、hold_reason、deferred_by_self、`uq_tasks_one_doing_per_staff`、Note 的轉移）、`Table task_events`、`Table task_sop_checks`、`Table sop_items`
- `truth-delta.md` -> 本模組對應的 `/api-plan`、`/data-plan` rows

**Boundary**:
- 只實作本人延後、開工、中斷、恢復、勾記、完成與工時；不做班末掃描、工務單、免查。
- 狀態變更一律經任務引擎的條件式 UPDATE，不先 SELECT 再 UPDATE；寫入都走冪等鍵（NFR-005、NFR-006）。

**Test Scope**:
- `specs/truth/features/backend/任務執行/一次只做一間.feature`

- [X] T168 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T169 [BDD-REFACTOR] 在綠燈下整理本 feature 新增的產品碼與 stepdef 重複，並重跑已完成 phase 的 Test Scope 確認不退化

## Phase 4C: ADD Feature File - backend/任務執行/中斷恢復與實際工時.feature

**Goal**: 讓本人延後、一次一間、中斷恢復與分段工時成立。本 phase 只讓「中斷、恢復與實際工時」全綠。

**Shared Must Read**:
- `specs/truth/features/backend/任務執行/中斷恢復與實際工時.feature` -> `功能: 中斷、恢復與實際工時`
- `specs/truth/features/backend/任務執行/dsl.md` -> `Given: "{員工}" 已勾選 "{任務}" 的以下 SOP 項：`、`Given: "{任務}" 已中斷 {分鐘} 分鐘`、`Given: "{任務}" 的進行歷程如下：`、`Given: 打掃 SOP 共有以下項目：`、`When: "{員工}" 以原因 "{原因}" 中斷 "{任務}"`、`Then: "{任務}" 已勾選的 SOP 項為：`、`Then: "{任務}" 記錄為跳過的 SOP 項為：`、`Then: "{任務}" 的實際工時為 {分鐘} 分鐘`
- `specs/truth/features/backend/dsl.md` -> `Given: 現在時間是 "{時刻}"`、`Given: "{員工}" 名下有以下任務：`、`Given: 飯店有以下員工：`、`When: "{員工}" 恢復 "{任務}"`、`When: "{員工}" 完成 "{任務}"`、`Then: "{任務}" 的已做時間為 {分鐘} 分鐘`、`Then: "{任務}" 的狀態為 "{狀態}"`、`Then: "{任務}" 的負責人是 "{員工}"`
- `truth-delta.md` -> `/dsl-refine` ADD `specs/truth/features/backend/任務執行/`；`/dsl-refine` MODIFY 實作語意回補（任務執行/dsl.md、班末掃描/dsl.md）
- `specs/truth/contracts/groups/現場作業/任務.yaml` -> `tasksStart`、`tasksDefer`、`tasksInterrupt`、`tasksResume`、`tasksComplete`、`tasksSopCheck`、`readTasks`
- `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `listDeptPool`
- `specs/truth/contracts/components/schemas/任務.yaml` -> `HoldReasonRequest`、`TaskDetail`、`tasks`；`components/schemas/shared.yaml` -> `Error`（doing_task_exists、reason_invalid）
- `specs/truth/data/data-model.dbml` -> `Table tasks`（worked_sec、segment_started_at、hold_reason、deferred_by_self、`uq_tasks_one_doing_per_staff`、Note 的轉移）、`Table task_events`、`Table task_sop_checks`、`Table sop_items`
- `truth-delta.md` -> 本模組對應的 `/api-plan`、`/data-plan` rows

**Boundary**:
- 只實作本人延後、開工、中斷、恢復、勾記、完成與工時；不做班末掃描、工務單、免查。
- 狀態變更一律經任務引擎的條件式 UPDATE，不先 SELECT 再 UPDATE；寫入都走冪等鍵（NFR-005、NFR-006）。

**Test Scope**:
- `specs/truth/features/backend/任務執行/中斷恢復與實際工時.feature`

- [X] T170 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T171 [BDD-REFACTOR] 在綠燈下整理本 feature 新增的產品碼與 stepdef 重複，並重跑已完成 phase 的 Test Scope 確認不退化

## Phase 4D: ADD Feature File - backend/班末掃描/班末掃描與中斷單交接.feature

**Goal**: 讓班末掃描退池、保留進行中與中斷單、推播員工與主管，以及主管附交代退池成立。本 phase 只讓「班末掃描與中斷單交接」全綠。

**Shared Must Read**:
- `specs/truth/features/backend/班末掃描/班末掃描與中斷單交接.feature` -> `功能: 班末掃描與中斷單交接`
- `specs/truth/features/backend/班末掃描/dsl.md` -> `When: "{主管}" 以交代 "{交代}" 把 "{任務}" 退回待接池`、`When: "{員工}" 的班末掃描執行`、`Then: "{員工}" 沒有收到關於 "{任務}" 的推播`、`Then: 房務待接池包含以下任務：`
- `specs/truth/features/backend/dsl.md` -> `Given: 現在時間是 "{時刻}"`、`Given: "{員工}" 名下有以下任務：`、`Given: 飯店有以下員工：`、`Then: "{任務}" 的狀態為 "{狀態}"`、`Then: "{員工}" 收到推播 "{標題}"`、`Then: "{任務}" 的負責人是 "{員工}"`、`Then: "{員工}" 收到以下推播：`
- `truth-delta.md` -> `/dsl-refine` ADD `specs/truth/features/backend/班末掃描/`；`/dsl-refine` MODIFY 實作語意回補（任務執行/dsl.md、班末掃描/dsl.md）
- `specs/truth/contracts/groups/現場作業/任務.yaml` -> `tasksRequeue`
- `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `listDeptPool`
- `specs/truth/contracts/components/schemas/shared.yaml` -> `PushPayload`、`PushTarget`
- `specs/truth/data/data-model.dbml` -> `Table tasks`（Note 的班末掃描規則、pool_source、priority_notify、requeue_note）、`Table outbox`、`Table tenant_config`（shift_times）、`Table plan_rooms`
- `truth-delta.md` -> 本模組對應的 `/api-plan`、`/data-plan` rows

**Boundary**:
- 只實作班末掃描 job 與主管附交代退池的推播；不改延後、中斷本身的規則。
- 狀態變更一律經任務引擎的條件式 UPDATE，不先 SELECT 再 UPDATE；寫入都走冪等鍵（NFR-005、NFR-006）。

**Test Scope**:
- `specs/truth/features/backend/班末掃描/班末掃描與中斷單交接.feature`

- [X] T172 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T173 [BDD-REFACTOR] 在綠燈下整理本 feature 新增的產品碼與 stepdef 重複，並重跑已完成 phase 的 Test Scope 確認不退化

## Phase 4E: ADD Feature File - backend/查房/免查房鎖定與結案.feature

**Goal**: 讓免查房開工鎖定、OC 回寫、抽查退回與合格率成立。本 phase 只讓「免查房鎖定與結案」全綠。

**Shared Must Read**:
- `specs/truth/features/backend/查房/免查房鎖定與結案.feature` -> `功能: 免查房鎖定與結案`
- `specs/truth/features/backend/查房/dsl.md` -> `Given: 飯店設定免查房開關為 "{開關}"`、`Given: "{員工}" 在免查房開關開啟時開始了 "{任務}" 的續住整理`、`Given: "{員工}" 在免查房開關關閉時開始了 "{任務}" 的續住整理`、`Then: "{任務}" 回寫 PMS 的房況為 "{房況}"`、`Then: "{任務}" 的查房結果為 "{結果}"`
- `specs/truth/features/backend/dsl.md` -> `Given: "{員工}" 名下有以下任務：`、`Given: 飯店有以下員工：`、`When: "{員工}" 判定 "{任務}" 查房全部項目通過`、`When: "{員工}" 完成 "{任務}"`、`Then: "{任務}" 的狀態為 "{狀態}"`
- `truth-delta.md` -> `/dsl-refine` ADD `specs/truth/features/backend/查房/`；`/dsl-refine` MODIFY 實作語意回補（查房/dsl.md、今日進度/dsl.md、待接池/dsl.md）
- `specs/truth/contracts/groups/現場作業/查房.yaml` -> `createInspections`、`createSpotCheck`、`getInspectionPassRate`
- `specs/truth/contracts/groups/營運管理/租戶設定.yaml` -> `updateTenantConfig`（`inspection_waived_cleaning_types`）
- `specs/truth/contracts/groups/現場作業/任務.yaml` -> `tasksStart`、`tasksComplete`
- `specs/truth/contracts/components/schemas/shared.yaml` -> `PmsRoomStatusWriteback`、`PushPayload`
- `specs/truth/data/data-model.dbml` -> `Table tasks`（inspection、inspection_waived）、`Table inspections`、`Table tenant_config`、`Table rooms`（hk_state）、`Table outbox`（pms_status）
- `truth-delta.md` -> 本模組對應的 `/api-plan`、`/data-plan` rows

**Boundary**:
- 只實作免查鎖定、免查結案、PMS 回寫值、抽查與合格率；不改一般查房退回換人規則。
- 狀態變更一律經任務引擎的條件式 UPDATE，不先 SELECT 再 UPDATE；寫入都走冪等鍵（NFR-005、NFR-006）。

**Test Scope**:
- `specs/truth/features/backend/查房/免查房鎖定與結案.feature`

- [X] T174 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T175 [BDD-REFACTOR] 在綠燈下整理本 feature 新增的產品碼與 stepdef 重複，並重跑已完成 phase 的 Test Scope 確認不退化

## Phase 4F: ADD Feature File - backend/查房/免查抽查與合格率.feature

**Goal**: 讓免查房開工鎖定、OC 回寫、抽查退回與合格率成立。本 phase 只讓「免查抽查與查房合格率」全綠。

**Shared Must Read**:
- `specs/truth/features/backend/查房/免查抽查與合格率.feature` -> `功能: 免查抽查與查房合格率`
- `specs/truth/features/backend/查房/dsl.md` -> `Given: 今日的打掃查房結果如下：`、`When: "{主管}" 查看今日查房合格率`、`When: "{主管}" 抽查 "{任務}"，判定 "{項目}" 不合格`、`Then: "{任務}" 的重做次數為 {次數}`、`Then: 今日查房合格率為 "{比例}"`、`Then: "{任務}" 沒有任何 SOP 勾記`、`Then: "{任務}" 的查房結果為 "{結果}"`
- `specs/truth/features/backend/dsl.md` -> `Given: "{員工}" 名下有以下任務：`、`Given: 飯店有以下員工：`、`Then: "{任務}" 的狀態為 "{狀態}"`、`Then: "{任務}" 的已做時間為 {分鐘} 分鐘`、`Then: "{員工}" 收到推播 "{標題}"`、`Then: "{任務}" 的負責人是 "{員工}"`
- `truth-delta.md` -> `/dsl-refine` ADD `specs/truth/features/backend/查房/`；`/dsl-refine` MODIFY 實作語意回補（查房/dsl.md、今日進度/dsl.md、待接池/dsl.md）
- `specs/truth/contracts/groups/現場作業/查房.yaml` -> `createInspections`、`createSpotCheck`、`getInspectionPassRate`
- `specs/truth/contracts/groups/營運管理/租戶設定.yaml` -> `updateTenantConfig`（`inspection_waived_cleaning_types`）
- `specs/truth/contracts/groups/現場作業/任務.yaml` -> `tasksStart`、`tasksComplete`
- `specs/truth/contracts/components/schemas/shared.yaml` -> `PmsRoomStatusWriteback`、`PushPayload`
- `specs/truth/data/data-model.dbml` -> `Table tasks`（inspection、inspection_waived）、`Table inspections`、`Table tenant_config`、`Table rooms`（hk_state）、`Table outbox`（pms_status）
- `truth-delta.md` -> 本模組對應的 `/api-plan`、`/data-plan` rows

**Boundary**:
- 只實作免查鎖定、免查結案、PMS 回寫值、抽查與合格率；不改一般查房退回換人規則。
- 狀態變更一律經任務引擎的條件式 UPDATE，不先 SELECT 再 UPDATE；寫入都走冪等鍵（NFR-005、NFR-006）。

**Test Scope**:
- `specs/truth/features/backend/查房/免查抽查與合格率.feature`

- [X] T176 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T177 [BDD-REFACTOR] 在綠燈下整理本 feature 新增的產品碼與 stepdef 重複，並重跑已完成 phase 的 Test Scope 確認不退化

## Phase 4G: ADD Feature File - backend/工務單/開立工務單與急件.feature

**Goal**: 讓工務單從開單、照片、重複報修、撤回到完修與原任務連動成立。本 phase 只讓「開立工務單與急件」全綠。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/開立工務單與急件.feature` -> `功能: 開立工務單與急件`
- `specs/truth/features/backend/工務單/dsl.md` -> `Given: 已有以下工務單：`、`Given: 本飯店下一張工務單號為 "{單號}"`、`Given: "{房號}" 今天是 VIP 房，住客 "{時刻}" 入住`、`When: "{員工}" 從 "{任務}" 開立以下工務單：`、`When: "{員工}" 對 "{房號}" 選擇工務單類別 "{類別}"`、`When: "{員工}" 中斷 "{任務}" 並開立以下工務單：`、`Then: 工務待接池包含 "{單號}"`、`Then: 系統建立工務單 "{單號}"，房號 "{房號}"、報修人 "{員工}"`、`Then: 系統建議設為急件，提示 "{提示}"`、`Then: 工務待接池順序為 {單號清單}`、`Then: 以下員工都收到 "{單號}" 的急件推播：`
- `specs/truth/features/backend/dsl.md` -> `Given: "{員工}" 名下有以下任務：`、`Given: 飯店有以下員工：`、`Then: "{任務}" 的狀態為 "{狀態}"`、`Then: "{任務}" 的已做時間為 {分鐘} 分鐘`、`Then: "{任務}" 的停住原因為 "{原因}"`
- `truth-delta.md` -> `/dsl-refine` ADD `specs/truth/features/backend/工務單/`；`/dsl-refine` MODIFY 實作語意回補（工務單/dsl.md）
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> 全部 operation
- `specs/truth/contracts/groups/現場作業/任務.yaml` -> `tasksComplete`（`CompleteTaskRequest`）、`tasksResume`
- `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `listDeptPool`（工務池、urgent 排序）
- `specs/truth/contracts/components/schemas/工務單.yaml`、`components/schemas/shared.yaml` -> `RepairTicketSummary`、`Error`（photo_*、already_claimed、ticket_closed、completion_photo_required）
- `specs/truth/data/data-model.dbml` -> `Table repair_tickets`、`repair_ticket_counters`、`repair_ticket_contributors`、`repair_photos`、`Table tasks`（linked_repair_task_id、urgent、cancel_reason）、`Table processed_ops`、`Table reservations_cache`
- `truth-delta.md` -> 本模組對應的 `/api-plan`、`/data-plan` rows

**Boundary**:
- 只實作工務單、照片、重複檢查、急件、撤回、完修與原任務連動；認領與開始沿用既有任務 API。
- 狀態變更一律經任務引擎的條件式 UPDATE，不先 SELECT 再 UPDATE；寫入都走冪等鍵（NFR-005、NFR-006）。

**Test Scope**:
- `specs/truth/features/backend/工務單/開立工務單與急件.feature`

- [X] T178 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T179 [BDD-REFACTOR] 在綠燈下整理本 feature 新增的產品碼與 stepdef 重複，並重跑已完成 phase 的 Test Scope 確認不退化

## Phase 4H: ADD Feature File - backend/工務單/重複報修補照片與撤回.feature

**Goal**: 讓工務單從開單、照片、重複報修、撤回到完修與原任務連動成立。本 phase 只讓「重複報修、補照片與撤回」全綠。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/重複報修補照片與撤回.feature` -> `功能: 重複報修、補照片與撤回`
- `specs/truth/features/backend/工務單/dsl.md` -> `Given: 已有以下工務單：`、`Given: "{員工}" 已對 "{房號}" 選擇工務單類別 "{類別}" 並看到 "{單號}" 的重複提示`、`When: "{員工}" 對 "{房號}" 選擇工務單類別 "{類別}"`、`When: "{員工}" 補 {張數} 張照片到 "{單號}"`、`When: "{員工}" 撤回 "{單號}"`、`Then: 系統提示同房已有未結案的工務單 "{單號}"，狀態為 "{狀態}"`、`Then: "{房號}" 未結案的 "{類別}" 工務單只有 {張數} 張`、`Then: 工務待接池不包含 "{單號}"`、`Then: "{單號}" 共有 {張數} 張照片`
- `specs/truth/features/backend/dsl.md` -> `Given: 飯店有以下員工：`、`Given: "{員工}" 名下有以下任務：`、`Then: 這次操作被拒絕，提示 "{提示}"`、`Then: "{任務}" 的狀態為 "{狀態}"`
- `truth-delta.md` -> `/dsl-refine` ADD `specs/truth/features/backend/工務單/`；`/dsl-refine` MODIFY 實作語意回補（工務單/dsl.md）
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> 全部 operation
- `specs/truth/contracts/groups/現場作業/任務.yaml` -> `tasksComplete`（`CompleteTaskRequest`）、`tasksResume`
- `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `listDeptPool`（工務池、urgent 排序）
- `specs/truth/contracts/components/schemas/工務單.yaml`、`components/schemas/shared.yaml` -> `RepairTicketSummary`、`Error`（photo_*、already_claimed、ticket_closed、completion_photo_required）
- `specs/truth/data/data-model.dbml` -> `Table repair_tickets`、`repair_ticket_counters`、`repair_ticket_contributors`、`repair_photos`、`Table tasks`（linked_repair_task_id、urgent、cancel_reason）、`Table processed_ops`、`Table reservations_cache`
- `truth-delta.md` -> 本模組對應的 `/api-plan`、`/data-plan` rows

**Boundary**:
- 只實作工務單、照片、重複檢查、急件、撤回、完修與原任務連動；認領與開始沿用既有任務 API。
- 狀態變更一律經任務引擎的條件式 UPDATE，不先 SELECT 再 UPDATE；寫入都走冪等鍵（NFR-005、NFR-006）。

**Test Scope**:
- `specs/truth/features/backend/工務單/重複報修補照片與撤回.feature`

- [X] T180 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T181 [BDD-REFACTOR] 在綠燈下整理本 feature 新增的產品碼與 stepdef 重複，並重跑已完成 phase 的 Test Scope 確認不退化

## Phase 4I: ADD Feature File - backend/工務單/照片完整性與冪等送出.feature

**Goal**: 讓工務單從開單、照片、重複報修、撤回到完修與原任務連動成立。本 phase 只讓「照片完整性與冪等送出」全綠。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/照片完整性與冪等送出.feature` -> `功能: 照片完整性與冪等送出`
- `specs/truth/features/backend/工務單/dsl.md` -> `Given: 已有以下工務單：`、`Given: 本飯店下一張工務單號為 "{單號}"`、`Given: "{員工}" 已重新上傳失敗的那張照片並成功`、`Given: "{員工}" 為 "{房號}" 的工務單準備了 {張數} 張照片，其中 {失敗數} 張上傳失敗`、`When: "{員工}" 中斷 "{任務}" 並以已準備的照片開立水電工務單`、`When: "{員工}" 從 "{任務}" 開立以下工務單，同一請求共送出 {次數} 次：`、`When: "{工務}" 不附完修照片完成 "{單號}"`、`When: "{員工}" 從 "{任務}" 開立以下工務單：`、`Then: "{房號}" 未結案的 "{類別}" 工務單只有 {張數} 張`、`Then: 系統建立工務單 "{單號}"，房號 "{房號}"、報修人 "{員工}"`、`Then: "{單號}" 共有 {張數} 張照片`、`Then: 系統沒有建立工務單`
- `specs/truth/features/backend/dsl.md` -> `Given: "{員工}" 名下有以下任務：`、`Given: 飯店有以下員工：`、`Then: 這次操作被拒絕，提示 "{提示}"`、`Then: "{任務}" 的狀態為 "{狀態}"`、`Then: 這次操作被拒絕`
- `truth-delta.md` -> `/dsl-refine` ADD `specs/truth/features/backend/工務單/`；`/dsl-refine` MODIFY 實作語意回補（工務單/dsl.md）
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> 全部 operation
- `specs/truth/contracts/groups/現場作業/任務.yaml` -> `tasksComplete`（`CompleteTaskRequest`）、`tasksResume`
- `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `listDeptPool`（工務池、urgent 排序）
- `specs/truth/contracts/components/schemas/工務單.yaml`、`components/schemas/shared.yaml` -> `RepairTicketSummary`、`Error`（photo_*、already_claimed、ticket_closed、completion_photo_required）
- `specs/truth/data/data-model.dbml` -> `Table repair_tickets`、`repair_ticket_counters`、`repair_ticket_contributors`、`repair_photos`、`Table tasks`（linked_repair_task_id、urgent、cancel_reason）、`Table processed_ops`、`Table reservations_cache`
- `truth-delta.md` -> 本模組對應的 `/api-plan`、`/data-plan` rows

**Boundary**:
- 只實作工務單、照片、重複檢查、急件、撤回、完修與原任務連動；認領與開始沿用既有任務 API。
- 狀態變更一律經任務引擎的條件式 UPDATE，不先 SELECT 再 UPDATE；寫入都走冪等鍵（NFR-005、NFR-006）。

**Test Scope**:
- `specs/truth/features/backend/工務單/照片完整性與冪等送出.feature`

- [X] T182 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T183 [BDD-REFACTOR] 在綠燈下整理本 feature 新增的產品碼與 stepdef 重複，並重跑已完成 phase 的 Test Scope 確認不退化

## Phase 4J: ADD Feature File - backend/工務單/完修與回到原任務.feature

**Goal**: 讓工務單從開單、照片、重複報修、撤回到完修與原任務連動成立。本 phase 只讓「完修與回到原任務」全綠。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/完修與回到原任務.feature` -> `功能: 完修與回到原任務`
- `specs/truth/features/backend/工務單/dsl.md` -> `Given: "{單號}" 已由 "{工務}" 完修`、`Given: 已有以下工務單：`、`When: "{工務}" 附 {張數} 張完修照片、處理說明 "{說明}" 完成 "{單號}"`、`Then: "{單號}" 的處理說明為 "{說明}"`
- `specs/truth/features/backend/dsl.md` -> `Given: "{員工}" 名下有以下任務：`、`Given: 飯店有以下員工：`、`When: "{員工}" 恢復 "{任務}"`、`Then: "{任務}" 的狀態為 "{狀態}"`、`Then: "{任務}" 的已做時間為 {分鐘} 分鐘`、`Then: "{員工}" 收到推播 "{標題}"`、`Then: "{任務}" 的停住原因為 "{原因}"`
- `truth-delta.md` -> `/dsl-refine` ADD `specs/truth/features/backend/工務單/`；`/dsl-refine` MODIFY 實作語意回補（工務單/dsl.md）
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> 全部 operation
- `specs/truth/contracts/groups/現場作業/任務.yaml` -> `tasksComplete`（`CompleteTaskRequest`）、`tasksResume`
- `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `listDeptPool`（工務池、urgent 排序）
- `specs/truth/contracts/components/schemas/工務單.yaml`、`components/schemas/shared.yaml` -> `RepairTicketSummary`、`Error`（photo_*、already_claimed、ticket_closed、completion_photo_required）
- `specs/truth/data/data-model.dbml` -> `Table repair_tickets`、`repair_ticket_counters`、`repair_ticket_contributors`、`repair_photos`、`Table tasks`（linked_repair_task_id、urgent、cancel_reason）、`Table processed_ops`、`Table reservations_cache`
- `truth-delta.md` -> 本模組對應的 `/api-plan`、`/data-plan` rows

**Boundary**:
- 只實作工務單、照片、重複檢查、急件、撤回、完修與原任務連動；認領與開始沿用既有任務 API。
- 狀態變更一律經任務引擎的條件式 UPDATE，不先 SELECT 再 UPDATE；寫入都走冪等鍵（NFR-005、NFR-006）。

**Test Scope**:
- `specs/truth/features/backend/工務單/完修與回到原任務.feature`

- [X] T184 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T185 [BDD-REFACTOR] 在綠燈下整理本 feature 新增的產品碼與 stepdef 重複，並重跑已完成 phase 的 Test Scope 確認不退化

## Phase 4K: ADD Feature File - backend/待接池/待接池排序與篩選.feature

**Goal**: 讓部門待接池排序、標示、篩選與準備工單指派成立。本 phase 只讓「待接池排序與篩選」全綠。

**Shared Must Read**:
- `specs/truth/features/backend/待接池/待接池排序與篩選.feature` -> `功能: 待接池排序與篩選`
- `specs/truth/features/backend/待接池/dsl.md` -> `When: "{員工}" 只看 "{類別}" 類的待接池`、`When: "{員工}" 查看待接池`、`Then: 標示同樓層的單為 {房號清單}`、`Then: 待接池分類筆數如下：`、`Then: 待接池中 "{任務}" 的資訊如下：`、`Then: 待接池順序為 {房號清單}`
- `specs/truth/features/backend/dsl.md` -> `Given: 飯店有以下員工：`、`Given: 房務待接池有以下任務：`
- `truth-delta.md` -> `/dsl-refine` ADD `specs/truth/features/backend/待接池/`；`/dsl-refine` MODIFY 實作語意回補（查房/dsl.md、今日進度/dsl.md、待接池/dsl.md）
- `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `listDeptPool`、`getMyToday`
- `specs/truth/contracts/components/schemas/員工工作台.yaml` -> `DeptPool`、`PoolItem`、`PoolSource`、`PoolCategory`
- `specs/truth/data/data-model.dbml` -> `Table tasks`（pool_source、priority_notify、return_reason、requeue_note、待接池排序索引）、`Table rooms`（floor）、`Table plan_rooms`、`Table reservations_cache`
- `truth-delta.md` -> 本模組對應的 `/api-plan`、`/data-plan` rows

**Boundary**:
- 只實作待接池排序、同樓層、來源標籤、篩選與準備工單指派；不改進池的觸發來源。
- 狀態變更一律經任務引擎的條件式 UPDATE，不先 SELECT 再 UPDATE；寫入都走冪等鍵（NFR-005、NFR-006）。

**Test Scope**:
- `specs/truth/features/backend/待接池/待接池排序與篩選.feature`

- [X] T186 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T187 [BDD-REFACTOR] 在綠燈下整理本 feature 新增的產品碼與 stepdef 重複，並重跑已完成 phase 的 Test Scope 確認不退化

## Phase 4L: ADD Feature File - backend/待接池/系統準備工單派給負責人.feature

**Goal**: 讓部門待接池排序、標示、篩選與準備工單指派成立。本 phase 只讓「系統準備工單派給該房負責人」全綠。

**Shared Must Read**:
- `specs/truth/features/backend/待接池/系統準備工單派給負責人.feature` -> `功能: 系統準備工單派給該房負責人`
- `specs/truth/features/backend/待接池/dsl.md` -> `Given: "{房號}" 今天沒有打掃負責人`、`When: 從 PMS 拉今日房態，"{房號}" 的訂房帶 "{屬性}" 屬性`、`Then: "{房號}" 的準備工單在房務待接池`、`Then: "{房號}" 的準備工單在 "{員工}" 名下，狀態為 "{狀態}"`
- `specs/truth/features/backend/dsl.md` -> `Given: 飯店有以下員工：`、`Given: "{員工}" 名下有以下任務：`、`Given: "{房號}" 今天沒有打掃負責人`、`Given: 現在時間是 "{時刻}"`、`When: 從 PMS 拉今日房態，"{房號}" 的訂房帶 "{屬性}" 屬性`、`When: 時間來到 "{時刻}"`
- `truth-delta.md` -> `/dsl-refine` ADD `specs/truth/features/backend/待接池/`；`/dsl-refine` MODIFY 實作語意回補（查房/dsl.md、今日進度/dsl.md、待接池/dsl.md）
- `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `listDeptPool`、`getMyToday`
- `specs/truth/contracts/components/schemas/員工工作台.yaml` -> `DeptPool`、`PoolItem`、`PoolSource`、`PoolCategory`
- `specs/truth/data/data-model.dbml` -> `Table tasks`（pool_source、priority_notify、return_reason、requeue_note、待接池排序索引）、`Table rooms`（floor）、`Table plan_rooms`、`Table reservations_cache`
- `truth-delta.md` -> 本模組對應的 `/api-plan`、`/data-plan` rows

**Boundary**:
- 只實作待接池排序、同樓層、來源標籤、篩選與準備工單指派；不改進池的觸發來源。
- 狀態變更一律經任務引擎的條件式 UPDATE，不先 SELECT 再 UPDATE；寫入都走冪等鍵（NFR-005、NFR-006）。

**Test Scope**:
- `specs/truth/features/backend/待接池/系統準備工單派給負責人.feature`

- [X] T188 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T189 [BDD-REFACTOR] 在綠燈下整理本 feature 新增的產品碼與 stepdef 重複，並重跑已完成 phase 的 Test Scope 確認不退化

## Phase 4M: ADD Feature File - backend/今日進度/今日進度與下一間.feature

**Goal**: 讓今日進度數字、剩餘工時與下一間推薦成立。本 phase 只讓「今日進度與下一間」全綠。

**Shared Must Read**:
- `specs/truth/features/backend/今日進度/今日進度與下一間.feature` -> `功能: 今日進度與下一間`
- `specs/truth/features/backend/今日進度/dsl.md` -> `When: "{員工}" 查看今日進度`、`Then: 待開始的單推薦順序為 {房號清單}`、`Then: 今日進度的 "{項目}" 為 "{值}"`、`Then: 今日進度包含連續零退回天數`
- `specs/truth/features/backend/dsl.md` -> `Given: 飯店有以下員工：`、`Given: 現在時間是 "{時刻}"`、`Given: "{員工}" 名下有以下任務：`
- `truth-delta.md` -> `/dsl-refine` ADD `specs/truth/features/backend/今日進度/`；`/dsl-refine` MODIFY 實作語意回補（查房/dsl.md、今日進度/dsl.md、待接池/dsl.md）
- `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `getMyToday`
- `specs/truth/contracts/components/schemas/員工工作台.yaml` -> `MyToday`、`NextTask`
- `specs/truth/data/data-model.dbml` -> `Table tasks`（worked_sec、segment_started_at、redo_count、type、state）
- `truth-delta.md` -> 本模組對應的 `/api-plan`、`/data-plan` rows

**Boundary**:
- 只實作今日進度、剩餘工時、下一間與連續零退回欄位；連續零退回的計算定義待 clarify，先回傳合法整數。
- 狀態變更一律經任務引擎的條件式 UPDATE，不先 SELECT 再 UPDATE；寫入都走冪等鍵（NFR-005、NFR-006）。

**Test Scope**:
- `specs/truth/features/backend/今日進度/今日進度與下一間.feature`

- [X] T190 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T191 [BDD-REFACTOR] 在綠燈下整理本 feature 新增的產品碼與 stepdef 重複，並重跑已完成 phase 的 Test Scope 確認不退化

## Phase 4N: ADD Feature File - backend/房間交辦/交辦合併與已讀.feature

**Goal**: 讓任務詳情合併交辦、已讀與查房退回資訊成立。本 phase 只讓「房間交辦合併與已讀」全綠。

**Shared Must Read**:
- `specs/truth/features/backend/房間交辦/交辦合併與已讀.feature` -> `功能: 房間交辦合併與已讀`
- `specs/truth/features/backend/房間交辦/dsl.md` -> `Given: 櫃檯 "{員工}" 為 "{房號}" 留了備註 "{內容}"`、`Given: "{員工}" 已開始 "{任務}"`、`Given: "{員工}" {時刻} 凍結的交班單有以下項目：`、`Given: "{員工}" 已接手 "{房號}"，原負責 "{原負責人}" {分鐘} 分鐘前留下交代 "{交代}"`、`When: "{員工}" 打開 "{任務}" 的任務`、`When: "{員工}" 展開 "{任務}" 的交辦`、`Then: "{任務}" 的交辦如下：`、`Then: "{任務}" 對 "{員工}" 的已讀紀錄只有 {筆數} 筆`、`Then: "{任務}" 的交辦對 "{員工}" 為 "{已讀狀態}"`
- `specs/truth/features/backend/dsl.md` -> `Given: 飯店有以下員工：`、`Given: "{員工}" 名下有以下任務：`、`When: "{員工}" 開始 "{任務}"`
- `truth-delta.md` -> `/dsl-refine` ADD `specs/truth/features/backend/房間交辦/`；`/dsl-refine` MODIFY 實作語意回補（房間交辦/dsl.md、查一下/dsl.md、推播/dsl.md）
- `specs/truth/contracts/groups/現場作業/房間交辦.yaml` -> `createDeskNote`、`markHandoverRead`
- `specs/truth/contracts/groups/現場作業/任務.yaml` -> `readTasks`（`TaskDetail.handovers`、`handoverRead`、`returnInfo`）、`tasksStart`
- `specs/truth/contracts/components/schemas/任務.yaml` -> `HandoverEntry`、`ReturnInfo`
- `specs/truth/data/data-model.dbml` -> `Table handover_reads`、`Table desk_notes`、`Table handovers`（snapshot）、`Table tasks`（requeue_note）、`Table inspections`
- `truth-delta.md` -> 本模組對應的 `/api-plan`、`/data-plan` rows

**Boundary**:
- 只實作交辦合併、櫃檯備註、已讀與退回資訊；不改交班單產生方式。
- 狀態變更一律經任務引擎的條件式 UPDATE，不先 SELECT 再 UPDATE；寫入都走冪等鍵（NFR-005、NFR-006）。

**Test Scope**:
- `specs/truth/features/backend/房間交辦/交辦合併與已讀.feature`

- [X] T192 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T193 [BDD-REFACTOR] 在綠燈下整理本 feature 新增的產品碼與 stepdef 重複，並重跑已完成 phase 的 Test Scope 確認不退化

## Phase 4O: ADD Feature File - backend/房間交辦/查房退回資訊.feature

**Goal**: 讓任務詳情合併交辦、已讀與查房退回資訊成立。本 phase 只讓「查房退回資訊」全綠。

**Shared Must Read**:
- `specs/truth/features/backend/房間交辦/查房退回資訊.feature` -> `功能: 查房退回資訊`
- `specs/truth/features/backend/房間交辦/dsl.md` -> `Given: "{員工}" 已下班，"{房號}" 改由 "{接手人}" 接手`、`Given: "{房號}" 的退房清潔由 "{原負責人}" 清完，"{主管}" 判定 "{項目}" 不合格`、`When: "{員工}" 打開 "{任務}" 的任務`、`Then: "{任務}" 的退回資訊為 "{退回資訊}"`、`Then: "{任務}" 的交辦不包含主管交代`
- `specs/truth/features/backend/dsl.md` -> `Given: 飯店有以下員工：`
- `truth-delta.md` -> `/dsl-refine` ADD `specs/truth/features/backend/房間交辦/`；`/dsl-refine` MODIFY 實作語意回補（房間交辦/dsl.md、查一下/dsl.md、推播/dsl.md）
- `specs/truth/contracts/groups/現場作業/房間交辦.yaml` -> `createDeskNote`、`markHandoverRead`
- `specs/truth/contracts/groups/現場作業/任務.yaml` -> `readTasks`（`TaskDetail.handovers`、`handoverRead`、`returnInfo`）、`tasksStart`
- `specs/truth/contracts/components/schemas/任務.yaml` -> `HandoverEntry`、`ReturnInfo`
- `specs/truth/data/data-model.dbml` -> `Table handover_reads`、`Table desk_notes`、`Table handovers`（snapshot）、`Table tasks`（requeue_note）、`Table inspections`
- `truth-delta.md` -> 本模組對應的 `/api-plan`、`/data-plan` rows

**Boundary**:
- 只實作交辦合併、櫃檯備註、已讀與退回資訊；不改交班單產生方式。
- 狀態變更一律經任務引擎的條件式 UPDATE，不先 SELECT 再 UPDATE；寫入都走冪等鍵（NFR-005、NFR-006）。

**Test Scope**:
- `specs/truth/features/backend/房間交辦/查房退回資訊.feature`

- [X] T194 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T195 [BDD-REFACTOR] 在綠燈下整理本 feature 新增的產品碼與 stepdef 重複，並重跑已完成 phase 的 Test Scope 確認不退化

## Phase 4P: ADD Feature File - backend/查一下/推薦問題.feature

**Goal**: 讓查一下只給有出處的答案、留稽核，並能轉問主管。本 phase 只讓「查一下的推薦問題」全綠。

**Shared Must Read**:
- `specs/truth/features/backend/查一下/推薦問題.feature` -> `功能: 查一下的推薦問題`
- `specs/truth/features/backend/查一下/dsl.md` -> `Given: "{房號}" 是今天的 VIP 房`、`Given: "{員工}" 在 "{任務}" 剛跳過 SOP 項 "{項目}"`、`When: "{員工}" 在 "{任務}" 打開查一下`、`Then: 推薦問題依序為：`、`Then: 第一個推薦問題為 "{問題}"`
- `specs/truth/features/backend/dsl.md` -> `Given: 飯店有以下員工：`、`Given: "{員工}" 名下有以下任務：`
- `truth-delta.md` -> `/dsl-refine` ADD `specs/truth/features/backend/查一下/`；`/dsl-refine` MODIFY 實作語意回補（房間交辦/dsl.md、查一下/dsl.md、推播/dsl.md）
- `specs/truth/contracts/groups/知識與通知/房間記憶與問一下.yaml` -> 全部 operation
- `specs/truth/contracts/components/schemas/房間記憶與問一下.yaml` -> `KbAnswer`、`KbSource`、`KbSuggestion`、`KbEscalation`
- `specs/truth/data/data-model.dbml` -> `Table kb_audit_logs`、`Table kb_escalations`、`Table room_memory`、`Table sop_items`、`Table task_sop_checks`、`Table reservations_cache`（guest_name 為 PII）
- `truth-delta.md` -> 本模組對應的 `/api-plan`、`/data-plan` rows

**Boundary**:
- 只實作推薦問題、有出處回答、稽核、回饋、問主管與回覆；不接 LLM。
- 狀態變更一律經任務引擎的條件式 UPDATE，不先 SELECT 再 UPDATE；寫入都走冪等鍵（NFR-005、NFR-006）。

**Test Scope**:
- `specs/truth/features/backend/查一下/推薦問題.feature`

- [X] T196 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T197 [BDD-REFACTOR] 在綠燈下整理本 feature 新增的產品碼與 stepdef 重複，並重跑已完成 phase 的 Test Scope 確認不退化

## Phase 4Q: ADD Feature File - backend/查一下/有出處的回答與問主管.feature

**Goal**: 讓查一下只給有出處的答案、留稽核，並能轉問主管。本 phase 只讓「有出處的回答與問主管」全綠。

**Shared Must Read**:
- `specs/truth/features/backend/查一下/有出處的回答與問主管.feature` -> `功能: 有出處的回答與問主管`
- `specs/truth/features/backend/查一下/dsl.md` -> `Given: "{房號}" 沒有任何房間記錄`、`Given: "{房號}" 的房間記錄有以下項目：`、`Given: "{員工}" 已把 "{問題}" 轉問 "{主管}"`、`Given: "{房號}" 目前的住客姓名為 "{姓名}"`、`Given: "{員工}" 已在 "{任務}" 問過 "{問題}"`、`When: "{員工}" 對這則回答回饋 "{回饋}"`、`When: "{員工}" 對這個問題按問主管`、`When: "{員工}" 在 "{任務}" 問 "{問題}"`、`When: "{主管}" 回覆這個問題 "{回覆}"`、`Then: "{員工}" 收到推播內容 "{內容}"`、`Then: 回答與出處都不包含 "{文字}"`、`Then: 回答為 "{回答}"`、`Then: 系統回覆查不到 "{房號}" 的相關記錄，且沒有任何回答內容`、`Then: 查一下稽核紀錄有以下一筆：`、`Then: "{主管}" 收到員工提問 "{問題}"`、`Then: 回答附以下出處：`
- `specs/truth/features/backend/dsl.md` -> `Given: 飯店有以下員工：`、`Given: "{員工}" 名下有以下任務：`
- `truth-delta.md` -> `/dsl-refine` ADD `specs/truth/features/backend/查一下/`；`/dsl-refine` MODIFY 實作語意回補（房間交辦/dsl.md、查一下/dsl.md、推播/dsl.md）
- `specs/truth/contracts/groups/知識與通知/房間記憶與問一下.yaml` -> 全部 operation
- `specs/truth/contracts/components/schemas/房間記憶與問一下.yaml` -> `KbAnswer`、`KbSource`、`KbSuggestion`、`KbEscalation`
- `specs/truth/data/data-model.dbml` -> `Table kb_audit_logs`、`Table kb_escalations`、`Table room_memory`、`Table sop_items`、`Table task_sop_checks`、`Table reservations_cache`（guest_name 為 PII）
- `truth-delta.md` -> 本模組對應的 `/api-plan`、`/data-plan` rows

**Boundary**:
- 只實作推薦問題、有出處回答、稽核、回饋、問主管與回覆；不接 LLM。
- 狀態變更一律經任務引擎的條件式 UPDATE，不先 SELECT 再 UPDATE；寫入都走冪等鍵（NFR-005、NFR-006）。

**Test Scope**:
- `specs/truth/features/backend/查一下/有出處的回答與問主管.feature`

- [X] T198 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T199 [BDD-REFACTOR] 在綠燈下整理本 feature 新增的產品碼與 stepdef 重複，並重跑已完成 phase 的 Test Scope 確認不退化

## Phase 4R: ADD Feature File - backend/查一下/沒幫助回饋.feature

**Goal**: 讓查一下只給有出處的答案、留稽核，並能轉問主管。本 phase 只讓「沒幫助的回答轉給 SOP 管理者」全綠。

**Shared Must Read**:
- `specs/truth/features/backend/查一下/沒幫助回饋.feature` -> `功能: 沒幫助的回答轉給 SOP 管理者`
- `specs/truth/features/backend/查一下/dsl.md` -> `Given: "{員工}" 已在 "{任務}" 問過 "{問題}"`、`Given: SOP "{標題}" 的內容為 "{內容}"`、`When: "{員工}" 對這則回答回饋 "{回饋}"`、`Then: SOP 管理者 "{主管}" 收到通知，需要檢查 "{SOP 標題}"`
- `specs/truth/features/backend/dsl.md` -> `Given: 飯店有以下員工：`、`Given: "{員工}" 名下有以下任務：`
- `truth-delta.md` -> `/dsl-refine` ADD `specs/truth/features/backend/查一下/`；`/dsl-refine` MODIFY 實作語意回補（房間交辦/dsl.md、查一下/dsl.md、推播/dsl.md）
- `specs/truth/contracts/groups/知識與通知/房間記憶與問一下.yaml` -> 全部 operation
- `specs/truth/contracts/components/schemas/房間記憶與問一下.yaml` -> `KbAnswer`、`KbSource`、`KbSuggestion`、`KbEscalation`
- `specs/truth/data/data-model.dbml` -> `Table kb_audit_logs`、`Table kb_escalations`、`Table room_memory`、`Table sop_items`、`Table task_sop_checks`、`Table reservations_cache`（guest_name 為 PII）
- `truth-delta.md` -> 本模組對應的 `/api-plan`、`/data-plan` rows

**Boundary**:
- 只實作推薦問題、有出處回答、稽核、回饋、問主管與回覆；不接 LLM。
- 狀態變更一律經任務引擎的條件式 UPDATE，不先 SELECT 再 UPDATE；寫入都走冪等鍵（NFR-005、NFR-006）。

**Test Scope**:
- `specs/truth/features/backend/查一下/沒幫助回饋.feature`

- [X] T200 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T201 [BDD-REFACTOR] 在綠燈下整理本 feature 新增的產品碼與 stepdef 重複，並重跑已完成 phase 的 Test Scope 確認不退化

## Phase 4S: ADD Feature File - backend/推播/員工推播事件.feature

**Goal**: 讓既有行為觸發的員工推播帶正確標題與去處。本 phase 只讓「員工推播事件」全綠。

**Shared Must Read**:
- `specs/truth/features/backend/推播/員工推播事件.feature` -> `功能: 員工推播事件`
- `specs/truth/features/backend/推播/dsl.md` -> `Given: 住客 10 分鐘內已在 "{房號}" 送出 {筆數} 筆需求`、`Then: 這次請求被擋下，提示住客洽櫃檯`、`Then: "{房號}" 今天沒有 "{品項}" 的需求單`、`Then: "{員工}" 只收到一次推播 "{標題}"`、`When: "{員工}" 請假`、`When: 住客撤回 "{房號}" 的需求`、`When: "{主管}" 以原因 "{原因}" 作廢 "{任務}"`、`When: 住客在 "{房號}" 掃碼要 "{品項}"`、`When: "{主管}" 查房 "{任務}"，判定 "{項目}" 不合格`、`When: "{主管}" 把 "{任務}" 改派給 "{員工}"`
- `specs/truth/features/backend/dsl.md` -> `Given: "{員工}" 名下有以下任務：`、`Given: 飯店有以下員工：`、`Given: 房務待接池有以下任務：`、`When: "{員工}" 判定 "{任務}" 查房全部項目通過`、`Then: "{員工}" 收到以下推播：`
- `truth-delta.md` -> `/dsl-refine` ADD `specs/truth/features/backend/推播/`；`/dsl-refine` MODIFY 實作語意回補（房間交辦/dsl.md、查一下/dsl.md、推播/dsl.md）
- `specs/truth/contracts/groups/住客與房間/住客掃碼.yaml` -> `guestSessionsScan`、`createGuestRequest`、`cancelGuestRequest`
- `specs/truth/contracts/groups/現場作業/查房.yaml` -> `createInspections`；`groups/現場作業/任務.yaml` -> `tasksCancel`、`tasksReassign`；`groups/現場作業/請假.yaml` -> `createLeaves`
- `specs/truth/contracts/components/schemas/shared.yaml` -> `PushPayload`、`PushTarget`
- `specs/truth/data/data-model.dbml` -> `Table outbox`（kind push）、`Table tasks`、`Table leaves`、`Table staff_accounts`、`Table plan_rooms`
- `truth-delta.md` -> 本模組對應的 `/api-plan`、`/data-plan` rows

**Boundary**:
- 只補齊既有行為觸發的推播內容與去處；不改觸發動作本身的狀態轉移。
- 狀態變更一律經任務引擎的條件式 UPDATE，不先 SELECT 再 UPDATE；寫入都走冪等鍵（NFR-005、NFR-006）。

**Test Scope**:
- `specs/truth/features/backend/推播/員工推播事件.feature`

- [X] T202 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T203 [BDD-REFACTOR] 在綠燈下整理本 feature 新增的產品碼與 stepdef 重複，並重跑已完成 phase 的 Test Scope 確認不退化
