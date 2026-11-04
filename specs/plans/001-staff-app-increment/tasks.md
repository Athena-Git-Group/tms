# Tasks: 001-staff-app-increment（/dsl-refine 待認領改名／未接超時兩段模型／急件系統判定／ios-app 新規格 回補）

**Plan Package**: `specs/plans/001-staff-app-increment`
**Core Inputs**: `spec.md`, `plan.md`, `research.md`, `truth-delta.md`（`## /dsl-refine` 段，commit `6fff7c7` 新增的列）, `specs/truth/techstack.md`, `specs/truth/contracts/**`, `specs/truth/data/data-model.dbml`, `specs/truth/features/backend/**`, `specs/truth/features/ios-app/**`

本輪承接 commit `6fff7c7`（owner `/dsl-refine`）落下的四項裁決：A「待接池／待接」全面改「待認領」、B 廢 L1/L2/L3＋`due_at` 升級鏈改「未接超時」兩段提醒、C 急件改系統判定、D 新增 4 支 ios-app feature；以及 10 筆用語落差。上一輪 T165–T203（116 場景全綠，commit `175ca16`）已完成之前的 truth 內容，本輪只處理 `truth-delta.md` `/dsl-refine` 段新增的列。

## Task Binding Contract

- 每個**開發任務**都必須對應 `truth-delta.md` 中 `/dsl-refine` 段的 ADD / MODIFY / DELETE 語意。
- 本輪沒有新增技術，省略 Setup phase。
- Phase 1 `Foundational` 只建立後續實作程式、測試共用元件、落點骨架。
- Phase 2 `Test Alignment & Implementation` 的目的：在寫產品碼之前，先把本輪所有受影響 DSL 的自動化測試對齊最新版 truth。
- Truth 參照必須使用 `specs/truth/**` 路徑；plan 參照才使用當前 plan package 內相對路徑。
- Feature `Shared Must Read` 除 feature、同模組 DSL、對應 `truth-delta` 列外，還必須列入回追到的 planner 產物；本輪回追到的產物全部落在 `specs/truth/**`，因此沒有 plan-side quality gate phase。

## Scope Note（停止範圍與回交，全部非 /dsl-refine 定義歧義）

以下子範圍本輪**不產生** `[BDD-RED]`/`[BDD-GREEN]` 任務，原因與回交對象列在下方；DSL 句本身在 truth 都有唯一權威定義，缺口是下游 planner 產物未跟上：

1. **ios-app 全部本輪 /dsl-refine 列**（Group B 的 ios-app 任務卡/待認領/今日進度/推播去處 feature、Group C 的 `ios-app/工務單/開單提示.feature`、Group D 4 支新 feature、10 筆用語落差中涉及 ios-app 的部分）：`plan.md:49,55` 與 `research.md:60-78` 已明文決議「App 不在本 repo」、Appium＋Cucumber-JVM runner「已選、尚未落地」，Wave 2（iOS 員工 App 介面）可延後、不影響後端進入 `/tasks`。本輪沒有 App 專案與 Appium harness 可執行 GREEN，依 plan.md 既有範圍決議全數排除，非回交 `/dsl-refine`。
2. **急件改系統判定（裁決 C）全部內容**（`backend/工務單/開立工務單與急件.feature` 的「系統依飯店設定規則判定急件」「主管可將急件降級為一般件」2 個 Rule，以及 `工務單/dsl.md` 新增的 `系統判定…為急件／一般件`、`…不是急件`、`…的急件降級原因為`、`"{主管}" 以原因…將…降級為一般件` 句型）：**回交 `/api-plan`**（`RepairTicketSummary`/`RepairTicket` 缺 `isUrgent` 之外的 `urgentRule`／`urgentDowngradeReason`、`openapi.yaml` 缺 `downgradeRepairTicketUrgency` 端點、precheck 回應的 `urgentSuggestion` 語意需從建議改判定結果；`CreateRepairTicketRequest.urgent` 目前仍是必填的客戶端輸入，裁決 C 要求「請求不帶急件欄位」與現況直接衝突）**與 `/data-plan`**（`repair_tickets` 表沒有 `is_urgent`／`urgent_rule`／`urgent_downgrade_reason` 欄位；dsl.md 的 StepDef 語意寫 `repair_tickets.is_urgent`，但現有 `tasks.urgent` 才是實際承接急件排序的欄位，兩者歸屬需裁決）。此缺口已記在 `truth-delta.md` 自己的「程式碼待改清單」第 4、5 項。`開立工務單與急件.feature` 中「送出就進工務待認領不經審核」「中斷/不中斷模式」3 個既有 Rule 只是移除 Data Table 的 `急件` 輸入欄位，不依賴新欄位，但因 Test Scope 以整個 feature file 為單位、同檔仍有被擋的 Rule 無法全綠，本輪整檔不排入 Phase 3/4。
3. **今日進度「查房通過」四格（用語落差表 #11 的 backend 半邊）**（`backend/今日進度/今日進度與下一間.feature` 新 Rule「查房通過數只計通過查房的單」、`今日進度/dsl.md` 的 `項目` enum 新增「查房通過」）：**回交 `/api-plan`**——`員工工作台.yaml` 的 `MyToday` 缺 `inspectedCount` 欄位，已記在 `truth-delta.md` 程式碼待改清單第 3 項。同檔其餘既有 Rule 未變，但因新 Rule 與既有 Rule 同檔、Test Scope 要求整檔全綠，本輪整檔不排入 Phase 3/4。

以下項目**不停止**，已內含在下方 Phase 1/2 任務中，理由一併說明：

- `tenant_config.key` 封閉鍵集目前沒有「未接提醒門檻」4 個新鍵（一般單提醒同仁／通知主管、急件提醒同仁／通知主管），`飯店的未接提醒門檻如下：` 這個 Given 寫下去會撞 Postgres CHECK。這不是新結構（沒有新表、新回應欄位），而是比照 `V7__duty_manager_role.sql`（dbml 與 CHECK 漏列 `duty_manager`，Phase 3 建測試資料時觸發 CHECK violation 發現並直接補 migration）的既有先例——封閉鍵集加值屬於實作時的機械補遺，排進 Phase 1 Foundational，不回交 `/data-plan`。

## Phase 1: Foundational

**Goal**: 只建立 Phase 2 會用到的 tenant_config 鍵骨架與未接提醒 fixture 落點，不寫門檻比對或推播判斷邏輯。

- [ ] T204 擴充 `tenant_config.key` CHECK，新增未接提醒門檻鍵 `unclaimed_reminder_minutes`
  - Read:
    - `truth-delta.md` -> `/dsl-refine` ADD `飯店的未接提醒門檻如下：`
    - `specs/truth/data/data-model.dbml` -> `Table tenant_config.key` 封閉鍵集
    - `tms/src/main/resources/db/migration/V7__duty_manager_role.sql`、`V8__inspection_waiver_and_pms_writeback_keys.sql` -> CHECK 擴充慣例（`DROP CONSTRAINT` 再 `ADD CONSTRAINT` 整個鍵集）
  - 只做：新增 `V12__unclaimed_reminder_thresholds.sql`，仿 V8 寫法重建 `chk_tenant_config_key` 為 dbml 的封閉鍵集——加入單一鍵 `unclaimed_reminder_minutes`（value 為 jsonb，結構 `{"cleaning":{"staffReminded":10,"managerNotified":20},"urgent":{"staffReminded":3,"managerNotified":8}}`，與 dbml 註解一致）、移除已隨裁決 B 廢除的 `sla_minutes_cleaning|sla_minutes_amenity|sla_minutes_other|escalation_step_minutes|escalation_chain_enabled` 五鍵（migration 內先 DELETE 這五鍵的既有資料列再 ADD CONSTRAINT，否則約束加不上）。dbml 的 `tenant_config.key` 註解已是新鍵集，不需回補。
  - 不做：不寫讀取門檻值的 StepDef 或 Job 邏輯。

- [ ] T205 建立待認領「提醒狀態」staging fixture 落點
  - Read:
    - `truth-delta.md` -> `/dsl-refine` MODIFY `房務待認領有以下任務：` 新增選填欄位 `提醒狀態`
    - `tms/src/test/java/com/athena/tms/bdd/steps/pool/PoolGivenSteps.java`
    - `tms/src/test/java/com/athena/tms/bdd/support/TaskFixtures.java`
  - 只做：在 `TaskFixtures`（或同層 helper）留一個函式殼，能把一筆待認領任務直接 staging 到「已提醒同仁」或「已通知主管」階段（寫入對應的未接提醒事件歷程與 push outbox），不需要真的推進 `Clock` 到門檻分鐘數。
  - 不做：不寫階段判斷的門檻比對規則（由 Phase 2 的 RED/ALIGN 任務填入）；不改 `PoolGivenSteps` 既有欄位解析邏輯以外的欄位。

## Phase 2: Test Alignment & Implementation

**Goal**: 把本輪 `/dsl-refine` 新增列用到的 DSL 自動化測試對齊最新版 truth；含 truth-delta 有改的句，以及本輪 Feature（Phase 3A–3C）用到、尚無 stepdef 的句。不寫產品行為。急件系統判定與今日進度查房通過相關句型因回交 `/api-plan`／`/data-plan`（見 Scope Note），不在本清單。

**DSL 參照**:
- 每一句只屬於一個權威 `dsl.md`：介面根 `specs/truth/features/backend/dsl.md`，或模組 `specs/truth/features/backend/{模組}/dsl.md`。
- 本輪 11 句（T206–T214、T222、T223）中 8 句在介面根 `specs/truth/features/backend/dsl.md`；1 句在 `specs/truth/features/backend/待認領/dsl.md`（`待認領順序為 {房號清單}`，T213）；T222 對照的是介面根 `dsl.md` 的「狀態用語對照」表（工務單狀態半段，非獨立 Given/When/Then 句型，但 Terms.java 的映射值必須與此表一致）；T223 的 2 句在 `specs/truth/features/backend/房間交辦/dsl.md`。以上每一句／每一個對照表在其所屬 `dsl.md` 都只有唯一一列，沒有第二個權威位置。
- 讀法：用 task title 的句型對到該檔那一列，以 `StepDef 實作語意` 當作測試程式碼語意。Given / When 讀 `怎麼做`、`權威狀態落地`、`回寫`；Then 讀 `必查`（`呈現結果`、`權威狀態`、`再讀確認`）。
- `truth-delta.md` 只告訴這句是 ADD / MODIFY / DELETE。語意以 `dsl.md` 那一列為準，不得用 feature 措辭或舊 stepdef 自行發明。

**Markers**:
- `[BDD-ALIGN]`：`MODIFY`。既有 stepdef 還在，但語意是舊 truth。依 `dsl.md` 該列改測試，讓它表達最新版 `StepDef 實作語意`。
- `[BDD-REMOVE]`：`DELETE`。此句已不是 truth。移除或改寫仍綁這句的 stepdef / assertion，不得留下保護舊行為的測試。
- `[BDD-RED]`：`ADD`，或本輪 Feature 用到、尚無 stepdef 的句。依 `dsl.md` 該列寫出 stepdef。完成時這句可被跑到，失敗只能是 assertion 或產品行為，不能是 undefined step。
- 三個 marker 都只動測試層，不寫產品碼。

**Shared Must Read**:
- `specs/truth/features/backend/dsl.md`
  -> Given `飯店的未接提醒門檻如下：`
  -> Given `"{員工}" 名下有以下任務：`
  -> Given `房務待認領有以下任務：`
  -> Given `已有以下工務單：`
  -> When `"{員工}" 認領 "{任務}"`
  -> When `時間來到 "{時刻}"`
- `specs/truth/features/backend/待認領/dsl.md` -> Then `待認領順序為 {房號清單}`
- `specs/truth/features/backend/房間交辦/dsl.md` -> Then `"{任務}" 的退回資訊逐欄顯示如下：`、`"{任務}" 的退回資訊包含操作提示內文`
- `truth-delta.md` -> `/dsl-refine` 裁決 A（待接→待認領全面置換）與裁決 B（廢 L1/L2/L3、新增未接提醒兩段）對應列；用語落差表 #6（工務單狀態：已認領→已接單、處理中→維修中）、#8（退回資訊改逐欄顯示）
- `tms/src/test/java/com/athena/tms/bdd/steps/common/CommonGivenSteps.java`、`CommonWhenSteps.java`
- `tms/src/test/java/com/athena/tms/bdd/steps/pool/PoolGivenSteps.java`、`PoolThenSteps.java`
- `tms/src/test/java/com/athena/tms/bdd/support/Terms.java`
- `tms/src/test/java/com/athena/tms/bdd/steps/handover/HandoverThenSteps.java`

**Boundary**:
- 一條 DSL 一個 task（T209 的全面用語置換因屬同一個 truth-delta 列的單一搬移/置換動作，不拆成多個 per-sentence task；T223 同屬一個 truth-delta MODIFY 列，一次處理舊句型移除與兩個新句型落地，不拆成 REMOVE＋RED）。
- 只改該句的 stepdef / assertion / 直接依賴的 helper。
- 不寫產品碼（`EscalationScanJob` 的實際改寫留給 Phase 3A 的 `[BDD-GREEN]`）。
- review 啟動 subagent；本輪所有 Test Scope 不得再有 undefined step，失敗只能是 assertion 或產品行為。有 issues 就修正再 review，直到沒有任何問題。通過前不解鎖 Phase 3。

**Parallel Hint**:
- T206–T214、T222、T223 各派一個獨立 subagent；T215 等全部回來（含 T222、T223）再啟動 subagent 來 review。
- 編號說明：T222、T223 是驗收後補列的任務，為避免引用錯位沿用既有編號接續（不重編 T204–T221），因此編號大於 T215，但實際執行順序排在 T215 review 之前；T215 的任務編號維持不變。

- [ ] T206 [P] [BDD-REMOVE] `Given: 飯店的逾時升級設定如下：`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/common/CommonGivenSteps.java`

- [ ] T207 [P] [BDD-RED] `Given: 飯店的未接提醒門檻如下：`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/common/CommonGivenSteps.java`（依賴 T204 的 tenant_config 鍵）

- [ ] T208 [P] [BDD-ALIGN] `Given: "{員工}" 名下有以下任務：`（移除到期時間／已升級欄位）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/common/CommonGivenSteps.java`

- [ ] T209 [P] [BDD-ALIGN] `Given: 房務待認領有以下任務：`（待接池→待認領置換＋新增提醒狀態欄位）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/pool/PoolGivenSteps.java`（依賴 T205 的 fixture 落點）

- [ ] T210 [P] [BDD-ALIGN] `Given: 已有以下工務單：`（權威位置搬移到介面根，語意不變，只對齊參照）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairGivenSteps.java`、`tms/src/test/java/com/athena/tms/bdd/steps/common/CommonGivenSteps.java`

- [ ] T211 [P] [BDD-RED] `When: "{員工}" 認領 "{任務}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/pool/PoolWhenSteps.java`

- [ ] T212 [P] [BDD-ALIGN] `When: 時間來到 "{時刻}"`（排程掃描語意由逾時升級改為未接提醒兩段）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/common/CommonWhenSteps.java`、`tms/src/test/java/com/athena/tms/bdd/support/JobTrigger.java`

- [ ] T213 [P] [BDD-ALIGN] `Then: 待認領順序為 {房號清單}`（排序條件由已逾期／已升級改為未接超時）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/pool/PoolThenSteps.java`

- [ ] T214 [P] [BDD-ALIGN] 全面用語置換：既有 stepdef 的 Given/When/Then 比對字串與 `Terms.java` 顯示字串，`待接`／`待接池` 改為 `待認領`（不含 T207–T213 已列的語意變更句）
  - Read: `tms/src/test/java/com/athena/tms/bdd/support/Terms.java`、`tms/src/test/java/com/athena/tms/bdd/steps/pool/PoolGivenSteps.java`、`PoolWhenSteps.java`、`PoolThenSteps.java`、`tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairThenSteps.java`

- [ ] T222 [P] [BDD-ALIGN] `Terms.java` 工務單狀態用語映射：`已認領`→`已接單`、`處理中`→`維修中`（鍵不變，顯示字串對齊介面根 `dsl.md` 狀態用語對照表）
  - Read: `tms/src/test/java/com/athena/tms/bdd/support/Terms.java:34-35`、`specs/truth/features/backend/dsl.md` 狀態用語對照表（工務單狀態半段）
  - 一併改掉其餘仍引用舊中文值 `已認領`／`處理中`（工務單語境）的 stepdef／fixture；不影響任務狀態對照表的 `已延後`／`已中斷` 等既有鍵。

- [ ] T223 [P] [BDD-ALIGN] 查房退回資訊 Then 句型改為逐欄顯示：移除舊句型 `"{任務}" 的退回資訊為 "{退回資訊}"`，實作 `"{任務}" 的退回資訊逐欄顯示如下：`與`"{任務}" 的退回資訊包含操作提示內文`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/handover/HandoverThenSteps.java:117-118`、`specs/truth/features/backend/房間交辦/dsl.md`

- [ ] T215 subagent review (phase quality gate)

## Phase 3A: MODIFY Feature File - backend/推播/員工推播事件.feature

**Goal**: 移除 `EscalationScanJob` 的 L1/L2/L3 升級掃描邏輯，改寫為兩段式未接提醒（一般單 10/20 分、急件 3/8 分），讓更新後的 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/backend/推播/員工推播事件.feature` -> `Feature: 員工推播事件`（整檔，含已刪除的升級鏈 3 Rule／7 場景與新增的未接提醒兩段 4 Rule／6 場景）
- `specs/truth/features/backend/推播/dsl.md` -> 模組說明段落（待認領未接提醒改為本模組負責的推播事件）
- `specs/truth/features/backend/dsl.md` -> Given `飯店的未接提醒門檻如下：`、When `時間來到 "{時刻}"`、When `"{員工}" 認領 "{任務}"`
- `truth-delta.md` -> `/dsl-refine` DELETE 升級鏈 3 Rule／ADD 未接超時兩段提醒 4 Rule／MODIFY 推播標題文案修正（`已等 {N} 分，還沒人接`／`已通知主管`）
- `tms/src/main/java/com/athena/tms/service/EscalationScanJob.java` -> 現行 L1/L2/L3 升級掃描邏輯（整段改寫）
- `tms/src/main/java/com/athena/tms/entity/Task.java` -> `escLevel`／`slaMin`（保留欄位，本輪停止由 job 寫入，不刪欄位結構）

**Boundary**:
- 只改未接提醒推播與其排程掃描邏輯；不動 `AutomationRuleService` 的建單邏輯本身（它建立的 pool 單會自然進入新的未接提醒掃描，不需要另外改建單規則）。
- 不新增 `tenant_config` 以外的結構；門檻鍵沿用 Phase 1 T204 新增的 4 鍵。
- 推播標題文案逐段對齊 feature 當前文字（`{房號或單號} {內容} 已等 {N} 分，還沒人接` / `{房號或單號} {內容} 已通知主管`），不得沿用池單卡標籤格式。

**Test Scope**:
- `specs/truth/features/backend/推播/員工推播事件.feature`

- [ ] T216 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T217 [BDD-REFACTOR] 在綠燈下整理兩段式未接提醒掃描與推播文案組裝的共用邏輯，清掉已停用的 L1/L2/L3 升級分支

## Phase 3B: MODIFY Feature File - backend/任務執行/本人延後與開工.feature

**Goal**: 確認已延後、已中斷的單不進入未接提醒掃描範圍，讓更新後的 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/backend/任務執行/本人延後與開工.feature` -> `Rule: 已延後或已中斷的單不在待認領，不會收到未接提醒推播`
- `specs/truth/features/backend/dsl.md` -> Given `飯店的未接提醒門檻如下：`、When `時間來到 "{時刻}"`
- `truth-delta.md` -> `/dsl-refine` MODIFY 本人延後與開工.feature Rule（原「已延後或已中斷的單不逾時升級」2 場景改為「不會收到未接提醒推播」1 場景）

**Boundary**:
- 只驗證已延後／已中斷單不進入 Phase 3A 新建的未接提醒掃描範圍，不改延後或中斷本身的既有邏輯。

**Test Scope**:
- `specs/truth/features/backend/任務執行/本人延後與開工.feature`

- [ ] T218 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T219 [BDD-REFACTOR] 在綠燈下整理「不在 pool 的單不掃描」判斷與 Phase 3A 共用的掃描入口

## Phase 3C: MODIFY Feature File - backend/待認領/待認領排序與篩選.feature

**Goal**: 待認領排序改讀「提醒狀態」欄位，未接超時（已進入提醒同仁或通知主管階段）的單排在最前，讓更新後的 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/backend/待認領/待認領排序與篩選.feature` -> `Rule: 未接超時（已進入提醒同仁或通知主管階段）的單排在最前，彼此間依房號，其餘維持既有排序`
- `specs/truth/features/backend/待認領/dsl.md` -> Then `待認領順序為 {房號清單}`
- `specs/truth/features/backend/dsl.md` -> Given `房務待認領有以下任務：`（`提醒狀態` 欄位）
- `truth-delta.md` -> `/dsl-refine` MODIFY 待認領排序與篩選.feature Rule（原「已逾期或已升級」改為「未接超時」）

**Boundary**:
- 只改排序第一鍵（未接超時優先）讀取 Phase 3A 的提醒狀態投影，不改其餘排序鍵（優先通知、同樓層、房號）。

**Test Scope**:
- `specs/truth/features/backend/待認領/待認領排序與篩選.feature`

- [ ] T220 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T221 [BDD-REFACTOR] 在綠燈下整理排序鍵計算的共用邏輯
