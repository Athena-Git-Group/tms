# Tasks: 001-staff-app-increment（/dsl-refine 待認領改名／未接超時兩段模型／急件系統判定／ios-app 新規格 回補）

**Plan Package**: `specs/plans/001-staff-app-increment`
**Core Inputs**: `spec.md`, `plan.md`, `research.md`, `truth-delta.md`（`## /dsl-refine` 段，commit `6fff7c7` 新增的列）, `specs/truth/techstack.md`, `specs/truth/contracts/**`, `specs/truth/data/data-model.dbml`, `specs/truth/features/backend/**`, `specs/truth/features/ios-app/**`

本輪承接 commit `6fff7c7`（owner `/dsl-refine`）落下的四項裁決：A「待接池／待接」全面改「待認領」、B 廢 L1/L2/L3＋`due_at` 升級鏈改「未接超時」兩段提醒、C 急件改系統判定、D 新增 4 支 ios-app feature；以及 10 筆用語落差。上一輪 T165–T203（116 場景全綠，commit `175ca16`）已完成之前的 truth 內容，本輪只處理 `truth-delta.md` `/dsl-refine` 段新增的列。

T204–T239（全數 [X]）已完成上述四項裁決的 backend 部分。T240 起新增的 Phase 1c／2c／3H–3M 承接 `truth-delta.md` `/dsl-refine` 段「第二輪」（commit `4e7f9dd`：登入帳號、通知中心、工務視角三缺口）、「第三輪」（commit `bd9ab9a`：櫃檮直通工務）、「第四輪」（commit `d27487b`：主管審核與驗收），以及對應解鎖的 `/api-plan`（commit `42683ae`）、`/data-plan`（commit `fdb5a2e`）內容，全部範圍為 backend。

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
2. ~~**急件改系統判定（裁決 C）全部內容**（`backend/工務單/開立工務單與急件.feature` 的「系統依飯店設定規則判定急件」「主管可將急件降級為一般件」2 個 Rule，以及 `工務單/dsl.md` 新增的 `系統判定…為急件／一般件`、`…不是急件`、`…的急件降級原因為`、`"{主管}" 以原因…將…降級為一般件` 句型）：回交 `/api-plan` 與 `/data-plan`。~~ **已解鎖**：`/api-plan`（`RepairTicketSummary.isUrgent`、`RepairTicket.urgentJudgement`{suggested,rule}、`RepairTicket.urgentDowngradeReason`、`RepairTicketPrecheckResponse.urgentJudgement`、`CreateRepairTicketRequest` 移除 `urgent`、`downgradeRepairTicketUrgency` 端點）與 `/data-plan`（`repair_tickets.is_urgent`／`urgent_rule`／`urgent_downgrade_reason`、`task_events.type` 新增 `urgent_downgraded`）已完成（commit `c74add2`，見 `truth-delta.md` `/api-plan` 行 49–56、程式碼待改清單行 189–192，`/data-plan` 行 86–87）。由下方 **Phase 1b（V14 migration）、Phase 2b（T226–T232 相關任務）、Phase 3F** 承接。
3. ~~**今日進度「查房通過」四格（用語落差表 #11 的 backend 半邊）**（`backend/今日進度/今日進度與下一間.feature` 新 Rule「查房通過數只計通過查房的單」、`今日進度/dsl.md` 的 `項目` enum 新增「查房通過」）：回交 `/api-plan`。~~ **已解鎖**：`/api-plan` 已補 `MyToday.inspectedCount`（commit `c74add2`，見 `truth-delta.md` 行 57–58）。`tasks.inspection` 欄位本身在 V5 就已存在，不需額外 migration。由下方 **Phase 2b（T233）、Phase 3G** 承接。

以下項目**不停止**，已內含在下方 Phase 1/2 任務中，理由一併說明：

- `tenant_config.key` 封閉鍵集目前沒有「未接提醒門檻」4 個新鍵（一般單提醒同仁／通知主管、急件提醒同仁／通知主管），`飯店的未接提醒門檻如下：` 這個 Given 寫下去會撞 Postgres CHECK。這不是新結構（沒有新表、新回應欄位），而是比照 `V7__duty_manager_role.sql`（dbml 與 CHECK 漏列 `duty_manager`，Phase 3 建測試資料時觸發 CHECK violation 發現並直接補 migration）的既有先例——封閉鍵集加值屬於實作時的機械補遺，排進 Phase 1 Foundational，不回交 `/data-plan`。

## Phase 1: Foundational

**Goal**: 只建立 Phase 2 會用到的 tenant_config 鍵骨架與未接提醒 fixture 落點，不寫門檻比對或推播判斷邏輯。

- [X] T204 擴充 `tenant_config.key` CHECK，新增未接提醒門檻鍵 `unclaimed_reminder_minutes`
  - Read:
    - `truth-delta.md` -> `/dsl-refine` ADD `飯店的未接提醒門檻如下：`
    - `specs/truth/data/data-model.dbml` -> `Table tenant_config.key` 封閉鍵集
    - `tms/src/main/resources/db/migration/V7__duty_manager_role.sql`、`V8__inspection_waiver_and_pms_writeback_keys.sql` -> CHECK 擴充慣例（`DROP CONSTRAINT` 再 `ADD CONSTRAINT` 整個鍵集）
  - 只做：新增 `V12__unclaimed_reminder_thresholds.sql`，仿 V8 寫法重建 `chk_tenant_config_key` 為 dbml 的封閉鍵集——加入單一鍵 `unclaimed_reminder_minutes`（value 為 jsonb，結構 `{"cleaning":{"staffReminded":10,"managerNotified":20},"urgent":{"staffReminded":3,"managerNotified":8}}`，與 dbml 註解一致）、移除已隨裁決 B 廢除的 `sla_minutes_cleaning|sla_minutes_amenity|sla_minutes_other|escalation_step_minutes|escalation_chain_enabled` 五鍵（migration 內先 DELETE 這五鍵的既有資料列再 ADD CONSTRAINT，否則約束加不上）。dbml 的 `tenant_config.key` 註解已是新鍵集，不需回補。
  - 不做：不寫讀取門檻值的 StepDef 或 Job 邏輯。

- [X] T205 建立待認領「提醒狀態」staging fixture 落點
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

- [X] T206 [P] [BDD-REMOVE] `Given: 飯店的逾時升級設定如下：`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/common/CommonGivenSteps.java`

- [X] T207 [P] [BDD-RED] `Given: 飯店的未接提醒門檻如下：`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/common/CommonGivenSteps.java`（依賴 T204 的 tenant_config 鍵）

- [X] T208 [P] [BDD-ALIGN] `Given: "{員工}" 名下有以下任務：`（移除到期時間／已升級欄位）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/common/CommonGivenSteps.java`

- [X] T209 [P] [BDD-ALIGN] `Given: 房務待認領有以下任務：`（待接池→待認領置換＋新增提醒狀態欄位）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/pool/PoolGivenSteps.java`（依賴 T205 的 fixture 落點）

- [X] T210 [P] [BDD-ALIGN] `Given: 已有以下工務單：`（權威位置搬移到介面根，語意不變，只對齊參照）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairGivenSteps.java`、`tms/src/test/java/com/athena/tms/bdd/steps/common/CommonGivenSteps.java`

- [X] T211 [P] [BDD-RED] `When: "{員工}" 認領 "{任務}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/pool/PoolWhenSteps.java`

- [X] T212 [P] [BDD-ALIGN] `When: 時間來到 "{時刻}"`（排程掃描語意由逾時升級改為未接提醒兩段）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/common/CommonWhenSteps.java`、`tms/src/test/java/com/athena/tms/bdd/support/JobTrigger.java`

- [X] T213 [P] [BDD-ALIGN] `Then: 待認領順序為 {房號清單}`（排序條件由已逾期／已升級改為未接超時）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/pool/PoolThenSteps.java`

- [X] T214 [P] [BDD-ALIGN] 全面用語置換：既有 stepdef 的 Given/When/Then 比對字串與 `Terms.java` 顯示字串，`待接`／`待接池` 改為 `待認領`（不含 T207–T213 已列的語意變更句）
  - Read: `tms/src/test/java/com/athena/tms/bdd/support/Terms.java`、`tms/src/test/java/com/athena/tms/bdd/steps/pool/PoolGivenSteps.java`、`PoolWhenSteps.java`、`PoolThenSteps.java`、`tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairThenSteps.java`

- [X] T222 [P] [BDD-ALIGN] `Terms.java` 工務單狀態用語映射：`已認領`→`已接單`、`處理中`→`維修中`（鍵不變，顯示字串對齊介面根 `dsl.md` 狀態用語對照表）
  - Read: `tms/src/test/java/com/athena/tms/bdd/support/Terms.java:34-35`、`specs/truth/features/backend/dsl.md` 狀態用語對照表（工務單狀態半段）
  - 一併改掉其餘仍引用舊中文值 `已認領`／`處理中`（工務單語境）的 stepdef／fixture；不影響任務狀態對照表的 `已延後`／`已中斷` 等既有鍵。

- [X] T223 [P] [BDD-ALIGN] 查房退回資訊 Then 句型改為逐欄顯示：移除舊句型 `"{任務}" 的退回資訊為 "{退回資訊}"`，實作 `"{任務}" 的退回資訊逐欄顯示如下：`與`"{任務}" 的退回資訊包含操作提示內文`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/handover/HandoverThenSteps.java:117-118`、`specs/truth/features/backend/房間交辦/dsl.md`

- [X] T215 subagent review (phase quality gate)

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

- [X] T216 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T217 [BDD-REFACTOR] 在綠燈下整理兩段式未接提醒掃描與推播文案組裝的共用邏輯，清掉已停用的 L1/L2/L3 升級分支

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

- [X] T218 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T219 [BDD-REFACTOR] 在綠燈下整理「不在 pool 的單不掃描」判斷與 Phase 3A 共用的掃描入口（NOOP：語意已顯性、入口已單一）

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

- [X] T220 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T221 [BDD-REFACTOR] 在綠燈下整理排序鍵計算的共用邏輯（NOOP：無重複邏輯、殘留已清、註解已對齊）

## Phase 3D: MODIFY Feature File - backend/班末掃描/班末掃描與中斷單交接.feature（T215 review 補列）

**Goal**: 裁決 A 的產品碼殘留——`ShiftEndScanJob.java:122` 推播文案硬編碼「待接池」，truth feature 已斷言「待認領」，讓 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/backend/班末掃描/班末掃描與中斷單交接.feature`
- `truth-delta.md` -> `/dsl-refine` 裁決 A（待接→待認領全面置換）
- `tms/src/main/java/com/athena/tms/service/ShiftEndScanJob.java:122`

**Boundary**: 只改使用者可見文案字串（待接池→待認領），不動班末掃描邏輯。

**Test Scope**:
- `specs/truth/features/backend/班末掃描/班末掃描與中斷單交接.feature`

- [X] T224 [BDD-GREEN] 讓 Test Scope 全綠

## Phase 3E: 舊整合測試套件用語對齊（T215 review 補列）

**Goal**: `tms/src/test/resources/features/{01,02,03,04,13,14}*.feature`＋glue `com.athena.tms.integration.stepdefs` 是獨立舊整合套件，仍用「待接池」保護舊用語；Phase 3A–3D 改產品字串後 `mvn test`（RunCucumberTest 在 surefire 預設範圍內）會被它擋下。對齊其用語至「待認領」。

**Boundary**: 只做 待接／待接池→待認領 的字串對齊（feature 文字與 stepdef 比對字串），不改該套件的測試結構與資料表風格。

**Test Scope**:
- `RunCucumberTest`（tms/src/test/resources/features 全套）

- [X] T225 [BDD-ALIGN] 舊整合套件用語置換，置於 Phase 3A–3D 完成後執行。完成條件＝用語對齊後 RunCucumberTest 無「因本輪產品改動或用語」造成的非綠；該套件既有的 4 failures＋1 error（outbox 順序、唯一約束、SOP 勾記筆數，T224 時已存在且與用語無關）逐筆歸因——若查明是本輪產品改動（認領路由、未接提醒、排序）造成就修到綠，若是先前就壞的既有缺陷則列清單回報上游，不在本任務修。

## Phase 1b: Foundational（急件系統判定與查房通過的資料側骨架）

**Goal**: 只建立 Phase 2b／3F 會用到的 `repair_tickets` 欄位骨架，不寫判定規則引擎或降級業務邏輯。比照 T204 先例：封閉結構變動（新欄位）先進 Foundational 當機械補遺，業務邏輯留給後面的 RED/GREEN。

- [X] T226 新增 `V14__repair_ticket_urgent_judgement.sql`：`repair_tickets` 補 `is_urgent`／`urgent_rule`／`urgent_downgrade_reason` 三欄
  - Read:
    - `truth-delta.md` -> `/data-plan` 段 `Table repair_tickets` 欄位 ADD（`is_urgent`、`urgent_rule`、`urgent_downgrade_reason`）、`Table task_events.type` MODIFY（新增 `urgent_downgraded`）
    - `specs/truth/data/data-model.dbml:532-535` -> `repair_tickets.is_urgent`／`urgent_rule`／`urgent_downgrade_reason` 欄位定義與型別、預設值、nullable
    - `tms/src/main/resources/db/migration/V5__staff_app_increment.sql:66-87`（`repair_tickets` 現有欄位與 FK，新欄位加在這張表上）、`V5__staff_app_increment.sql:9`（`task_events.type` 刻意不設 CHECK 的既有決定，本次沿用同一決定）
    - `tms/src/main/resources/db/migration/V13__unclaimed_reminder_stage.sql`（最近一次對 `tasks` 加欄位＋調整索引的寫法慣例）
  - 只做：`ALTER TABLE repair_tickets ADD COLUMN is_urgent boolean NOT NULL DEFAULT false`、`ADD COLUMN urgent_rule text`（nullable）、`ADD COLUMN urgent_downgrade_reason text`（nullable），欄位語意與型別對齊 dbml 的三行註解。既有資料列（若有）用預設值 `false`／`NULL` 補齊，不需要額外 backfill 邏輯（本輪之前建立的工務單都沒有急件判定資料可回填）。
  - 不做：`task_events.type` **不需要** migration——V1 建表時刻意不對 `type` 加 CHECK（見 `V5__staff_app_increment.sql:9` 註解「加值不改表」），dbml 新增的列舉值 `urgent_downgraded` 純粹是文件列舉，沒有對應的 DB 結構變動；不建索引（`is_urgent` 的排序需求已由既有 `tasks.urgent` 承接，`idx_tasks_pool_order` 不需要再加這張表的欄位）；不寫 `tasks.urgent` 與 `repair_tickets.is_urgent` 的同步觸發器或程式邏輯（留給 Phase 3F `[BDD-GREEN]` 在應用層同一交易內寫兩邊）。

## Phase 2b: Test Alignment（急件系統判定與查房通過）

**Goal**: 把 `/api-plan`、`/data-plan` 解鎖後，本輪兩個 feature 用到、尚無 stepdef 或語意過時的 DSL 句對齊最新版 truth。不寫產品行為。

**DSL 參照**:
- 下列 8 句分屬兩個模組 dsl.md：7 句在 `specs/truth/features/backend/工務單/dsl.md`（T227–T233），1 句在 `specs/truth/features/backend/今日進度/dsl.md`（T234）。每句在其所屬 `dsl.md` 都只有唯一一列。
- 讀法同 Phase 2：Given/When 讀 `怎麼做`、`權威狀態落地`、`回寫`；Then 讀 `必查`。`truth-delta.md` 只告訴 ADD/MODIFY/DELETE，語意以 `dsl.md` 該列為準。

**Markers**：定義同 Phase 2（`[BDD-ALIGN]`=MODIFY、`[BDD-REMOVE]`=DELETE、`[BDD-RED]`=ADD 或本輪 Feature 用到尚無 stepdef 的句）。三個 marker 都只動測試層，不寫產品碼——T232 的 Data Table 欄位移除會讓目前必填的 `urgent` 欄位送不出去，預期結果是 422 業務拒絕（`request.urgent() == null`），不是 undefined step，產品端移除這個必填限制留給 Phase 3F `[BDD-GREEN]`。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/dsl.md`
  -> Then `系統判定 "{單號}" 為急件，理由 "{理由}"`、`系統判定 "{單號}" 為一般件`、`"{單號}" 不是急件`、`"{單號}" 的急件降級原因為 "{原因}"`
  -> When `"{主管}" 以原因 "{原因}" 將 "{單號}" 降級為一般件`、`"{員工}" 中斷 "{任務}" 並開立以下工務單：`、`"{員工}" 從 "{任務}" 開立以下工務單：`
- `specs/truth/features/backend/今日進度/dsl.md` -> Then `今日進度的 "{項目}" 為 "{值}"`（`項目` enum 新增「查房通過」）
- `truth-delta.md` -> `/api-plan` 行 49–58（`isUrgent`／`urgentJudgement`／`urgentDowngradeReason`／`downgradeRepairTicketUrgency`／`MyToday.inspectedCount`）；`/dsl-refine` 後的本輪補遺行 158–164、173（dsl.md 與 feature 的 ADD/MODIFY/DELETE 列表）
- `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairGivenSteps.java`、`RepairWhenSteps.java`、`RepairThenSteps.java`
- `tms/src/test/java/com/athena/tms/bdd/steps/today/TodaySteps.java`
- `tms/src/main/java/com/athena/tms/repair/RepairController.java:40-47`（`CreateTicketRequest` 目前仍把 `urgent` 列為必填）—— T232 只改測試，這裡只是讓 subagent 知道 422 從哪來

**Boundary**:
- 一條 DSL 一個 task；T232 因屬同一個 truth-delta 列（行 161）的單一「移除急件輸入欄位」動作、同時涵蓋 3 個 When 句型（`中斷…並開立`、`從…開立`、`中斷…並以已準備的照片開立水電工務單`），比照既有 T209／T223 先例不拆成 3 個 task。
- 只改該句的 stepdef／assertion／直接依賴的 helper；不改產品碼（`RepairService`／`RepairController`／`TaskExecutionService` 的實際改寫留給 Phase 3F／3G 的 `[BDD-GREEN]`）。
- T227–T230、T231 都依賴 T226 新增的 `repair_tickets` 三欄位才能查得到權威狀態，實作前先確認 T226 已完成。
- review 啟動 subagent；本輪所有 Test Scope 不得再有 undefined step，失敗只能是 assertion 或產品行為。有 issues 就修正再 review，直到沒有任何問題。通過前不解鎖 Phase 3F／3G。

**Parallel Hint**:
- T227–T234 各派一個獨立 subagent；T235 等全部回來再啟動 subagent 來 review。

- [X] T227 [P] [BDD-RED] `Then: 系統判定 "{單號}" 為急件，理由 "{理由}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairThenSteps.java`（依賴 T226 的 `repair_tickets.is_urgent`／`urgent_rule`）

- [X] T228 [P] [BDD-RED] `Then: 系統判定 "{單號}" 為一般件`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairThenSteps.java`（依賴 T226）

- [X] T229 [P] [BDD-RED] `Then: "{單號}" 不是急件`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairThenSteps.java`（依賴 T226；`再讀確認` 需呼叫 `readRepairTicket` 比對 `isUrgent`）

- [X] T230 [P] [BDD-RED] `Then: "{單號}" 的急件降級原因為 "{原因}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairThenSteps.java`（依賴 T226；`再讀確認` 需呼叫 `readRepairTicket` 比對 `urgentDowngradeReason`）

- [X] T231 [P] [BDD-RED] `When: "{主管}" 以原因 "{原因}" 將 "{單號}" 降級為一般件`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairWhenSteps.java`（依賴 T226；呼叫 `POST /repair-tickets/{ticketId}/downgrade-urgency`，body 的 `note` 帶原因文字，auth 用 `"{主管}"` 的 JWT）

- [X] T232 [P] [BDD-ALIGN] 開單 When 句型移除 Data Table／請求的 `急件` 欄位：`"{員工}" 中斷 "{任務}" 並開立以下工務單：`、`"{員工}" 從 "{任務}" 開立以下工務單：`、`"{員工}" 中斷 "{任務}" 並以已準備的照片開立水電工務單`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairWhenSteps.java`（私有 `createTicket` 方法目前 `requireCell(row, "急件")` 且把 `urgent` 塞進 request body；`interruptAndOpenTicketWithPreparedPhotos` 方法目前硬編 `"urgent", false`）
  - 不讀 Data Table 的 `急件` 欄位（目前 feature 的 Data Table 已經沒有這欄，`requireCell` 現在就是今天 11 筆非綠之一的來源）；request body 不再放 `urgent` 鍵。完成後這 3 句可被跑到，預期失敗是產品端 422（`CreateTicketRequest.urgent` 仍必填），不是 undefined step 或 Data Table 缺欄位的 IllegalArgumentException。

- [X] T233 [P] [BDD-REMOVE] `Then: 系統建議設為急件，提示 "{提示}"`（precheck 的急件判定結果語意由「建議」改為「判定」，舊斷言綁 `urgentSuggestion` 欄位）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairThenSteps.java:163-172`（`systemSuggestsUrgent` 方法）、`specs/truth/features/backend/工務單/dsl.md`（Then 區已無此句，`回立即建議` 相關敘述已全部改為「系統判定」）
  - 這句 Gherkin 文字在目前所有 `specs/truth/features/backend/工務單/*.feature` 都已不存在（已確認 grep 無結果），stepdef 是保護已廢除行為的殘留——直接移除 `systemSuggestsUrgent` 方法，不得改寫成繼續斷言 `urgentSuggestion`。

- [X] T234 [P] [BDD-RED] `Then: 今日進度的 "{項目}" 為 "{值}"`（`項目` enum 新增「查房通過」）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/today/TodaySteps.java:63-99`（`todayFieldIs` 的 `switch (item)`，目前缺「查房通過」分支，會落入 `default` 丟 `IllegalArgumentException`）、`specs/truth/features/backend/今日進度/dsl.md`（`inspectedCount`／`inspection=passed` 口徑）
  - 新增 `case "查房通過"`：比對 `MyToday.inspectedCount`，並在 `recomputeTotals`／`TodayTotals`（同檔 `tms/src/test/java/com/athena/tms/bdd/steps/today/TodaySteps.java` 其餘私有方法）加一個以 `tasks.inspection = 'passed'` 且 `work_date` 為今天的本人任務數重算的欄位，口徑與 `MyToday.cleaningDone` 同一批資料來源，不另外查表。

- [X] T235 subagent review (phase quality gate)（PASS；一筆暫態 422 紅燈由 Phase 3F 收斂，T236 回歸須確認 照片完整性與冪等送出.feature 轉綠）

## Phase 3F: MODIFY Feature File - backend/工務單/開立工務單與急件.feature

**Goal**: 急件判定全面改由系統依飯店設定規則判定（裁決 C）：開單請求不再接受 `urgent` 欄位，系統依 VIP／今日入住規則判定並寫入 `repair_tickets.is_urgent`／`urgent_rule`（同步 `tasks.urgent`），主管可附原因事後降級；讓更新後的 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/開立工務單與急件.feature` -> `Feature: 開立工務單與急件`（整檔，含 7 個 Rule：3 個既有 Rule 的 Data Table 已移除 `急件` 欄位、「系統依飯店設定規則判定急件」、「急件在工務待認領排在所有非急件之前」、「急件推播給所有工務與主管」、「主管可將急件降級為一般件，須附原因」）
- `specs/truth/features/backend/工務單/dsl.md` -> Given `"{房號}" 今天是 VIP 房，住客 "{時刻}" 入住`；When `"{員工}" 中斷 "{任務}" 並開立以下工務單：`、`"{員工}" 從 "{任務}" 開立以下工務單：`、`"{主管}" 以原因 "{原因}" 將 "{單號}" 降級為一般件`；Then `系統判定 "{單號}" 為急件，理由 "{理由}"`、`系統判定 "{單號}" 為一般件`、`"{單號}" 不是急件`、`"{單號}" 的急件降級原因為 "{原因}"`
- `truth-delta.md` -> `/api-plan` 行 49–56（`isUrgent`／`urgentJudgement`／`urgentDowngradeReason`／`downgradeRepairTicketUrgency`）、`/data-plan` 行 86–87（`repair_tickets` 三欄位、`task_events.type` 新值）、本輪補遺行 158–164（Rule 內容的 MODIFY/ADD 明細）
- `tms/src/main/java/com/athena/tms/repair/RepairService.java:148-215`（`precheck` 方法，`urgentSuggestion` 要改名並改語意為 `urgentJudgement`）、`:401-466`（`create` 方法，`urgent` 參數要改為系統計算、同步寫 `repair_tickets.is_urgent`／`urgent_rule`）
- `tms/src/main/java/com/athena/tms/repair/RepairController.java:40-47,76-90`（`CreateTicketRequest` 移除 `urgent` 欄位與必填檢查；新增 `downgradeRepairTicketUrgency` 端點路由）
- `specs/truth/contracts/components/schemas/工務單.yaml`（`UrgentJudgement`、`RepairTicketPrecheckResponse.urgentJudgement`、`CreateRepairTicketRequest` 無 `urgent`、`RepairTicket.urgentJudgement`/`urgentDowngradeReason`、`DowngradeRepairTicketUrgencyRequest`）、`specs/truth/contracts/components/schemas/shared.yaml`（`RepairTicketSummary.isUrgent`）、對應的 `groups/現場作業/工務單.yaml`（`downgradeRepairTicketUrgency` 端點）

**Boundary**:
- 急件判定規則固定讀「原任務房間今天是否有訂單、`vip=true`」（沿用 `precheck` 既有的 `reservations_cache` 查詢條件與理由文案組法 `(vip ? "VIP · " : "") + checkinTime + " 入住"`），不新增其他判定維度；規則本身不可設定（tenant_config 不新增判定規則鍵）。
- 開單交易內同步寫 `tasks.urgent` 與 `repair_tickets.is_urgent`／`urgent_rule`（同一個 `@Transactional` 方法內完成，不拆成兩次呼叫）；降級只改 `is_urgent`／`urgent_downgrade_reason`，`urgent_rule` 保留原判定理由供追溯。
- 降級寫入 `task_events.type = 'urgent_downgraded'`，`payload.downgradeReason` 帶原因文字；不新增 `task_events.type` 以外的事件結構。
- 不動既有「免審核進池」「中斷/不中斷模式」兩個 Rule 的狀態轉移邏輯，只配合 Data Table 移除 `急件` 欄位調整呼叫參數。

**Test Scope**:
- `specs/truth/features/backend/工務單/開立工務單與急件.feature`

- [X] T236 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T237 [BDD-REFACTOR] 在綠燈下整理急件判定規則的計算邏輯（`precheck`／`create` 共用同一個判定函式，不重複查 `reservations_cache` 兩次），清掉 `urgentSuggestion` 殘留命名

## Phase 3G: MODIFY Feature File - backend/今日進度/今日進度與下一間.feature

**Goal**: 首頁今日進度第四格改採「查房通過數」（`inspectedCount`，口徑為本人今日 `inspection=passed` 的任務數），讓更新後的 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/backend/今日進度/今日進度與下一間.feature` -> `Feature: 今日進度與下一間`（整檔，含新 Rule「查房通過數只計通過查房的單」與既有 5 個 Rule）
- `specs/truth/features/backend/今日進度/dsl.md` -> Then `今日進度的 "{項目}" 為 "{值}"`（`項目` enum 含「查房通過」、`inspectedCount` 計算口徑）
- `truth-delta.md` -> `/api-plan` 行 57–58（`MyToday.inspectedCount` ADD、`getMyToday` description MODIFY）、本輪補遺行 173（Rule 標題與 enum 異動明細）
- `tms/src/main/java/com/athena/tms/service/TaskExecutionService.java:412-493`（`myToday` 方法：查詢 SQL 未選 `inspection` 欄位、迴圈統計未計 `inspectedCount`、回應 body 未放這個鍵）
- `specs/truth/contracts/components/schemas/員工工作台.yaml`（`MyToday.inspectedCount` 欄位定義）

**Boundary**:
- 只在既有查詢與迴圈裡加入 `inspection` 欄位與 `inspectedCount` 計數，計算口徑比照既有 `cleaningDone`／`completedCount`（同一個 `work_date`＝今天、同一批 `rows` 迴圈），不另開新查詢。
- 不改 `cleaningDone`／`completedCount`／`adHocCompleted`／`remainingMinutes`／`nextTasks` 既有欄位的計算邏輯與既有 Rule 的驗收結果。

**Test Scope**:
- `specs/truth/features/backend/今日進度/今日進度與下一間.feature`

- [X] T238 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T239 [BDD-REFACTOR] 在綠燈下檢查 `myToday` 方法的欄位計算是否可共用既有迴圈變數命名與註解風格，無重複邏輯則 NOOP（NOOP：風格一致、無重複邏輯）

## Scope Note（本輪新增範圍：登入／通知中心／工務視角／審核與驗收，全部 backend）

本輪承接 `truth-delta.md` `/dsl-refine` 段「第二輪」「第三輪」「第四輪」（commit `4e7f9dd`、`bd9ab9a`、`d27487b`）落下的七項缺口，以及 `/api-plan`（commit `42683ae`）、`/data-plan`（commit `fdb5a2e`）的對應解鎖：登入帳號、通知中心、工務視角（直開、放回、池篩選、首頁彙總）、櫃檮直通工務、工務單審核與驗收、完修三選一。

**ios-app 本輪不產生 Feature phase，原因與上一輪不同（記錄取捨，供下一輪 `/tasks` 檢視）**：上一輪排除 ios-app 的理由是「App 不在本 repo、Appium 尚未落地」；該理由已不成立——`app/mobile/`、`app/bdd/` 現已存在且 F3 推播金絲雀已 Appium 真跑 12/12 綠。本輪改為範圍取捨：orchestrator 本輪指派的背景（七項缺口、5 個新端點、`/data-plan` 8 列遷移清單）全部指向 backend／data 側，`truth-delta.md` 第二～四輪新增的 ios-app feature（`帳號與登入/登入與登出守衛.feature`、`推播/通知中心.feature`、`工務單/工務視角.feature` 的對應 App 行為）本輪未排入——這不是「DSL 無法唯一承接」而是本輪任務邊界的取捨，下一輪 `/tasks` 應針對 ios-app 這批 ADD feature 另開一輪 Phase 3/4（沿用本輪 backend 已解鎖的 contracts）。

**task_events 新增 4 個事件型別（`repair_approved`／`repair_rejected`／`repair_resubmitted`／`repair_verified`）盤點結果：不回交 `/dsl-refine`**——`backend/工務單/dsl.md` 本輪已有對應的 4 個 When／3 個 Then 句型（`"{主管}" 核准`、`"{主管}" 以原因退回`、`"{員工}" 補資料再送出`、`"{主管}" 驗收通過`；`"{單號}" 的退回原因為`、`"{單號}" 已由驗收`、`"{單號}" 尚未驗收`），一一對應這 4 個事件型別，唯一 DSL 定義存在，收斂進下方 Phase 2c。`task_events.type` 本身依 V5 既有決定不加 CHECK，故無對應 migration task。

**`backend/工務單/dsl.md`、`backend/帳號與登入/dsl.md` 多處仍殘留「待 `/api-plan` 補」字樣的取捨**：這些句型是 `/dsl-refine` 在 `/api-plan` 解鎖前寫的，字面上還沒更新；但逐一核對後，這些句型預先假設的欄位名與端點（`LoginRequest{loginId,password}`、`approveRepairTicket` 等 4 端點、`RepairTicket.rejectReason`/`verifiedBy`/`verifiedAt`）與 `truth-delta.md` `/api-plan` 段落已解鎖的實際契約逐字相符，判斷為唯一 DSL 定義仍成立、只是文件未來得及回頭補一輪 `/dsl-refine` 清掉「待」字樣，不構成停止條件。下方各 Feature phase 的 `Shared Must Read` 另外列出對應 `specs/truth/contracts/**` 檔案，供 subagent 核對確切欄位名稱。

## Phase 1c: Foundational（登入鎖定／通知中心／審核驗收的資料側骨架）

**Goal**: 只建立 Phase 2c／3H–3M 會用到的資料結構與 stepdef 落點骨架，不寫判定規則或業務邏輯。比照 T204、T226 先例：封閉結構變動（新欄位、新表、CHECK 擴值）先進 Foundational 當機械補遺。

- [X] T240 新增 `V15__staff_account_login_lockout.sql`：`staff_accounts` 補 `must_change_password`／`failed_login_count`／`locked_until` 三欄
  - Read:
    - `truth-delta.md` -> `/api-plan` 行 79–82（`LoginRequest`／`LoginResponse`／`staff_accounts.mustChangePassword`等）、`/data-plan` 行 131（`staff_accounts` 新增三欄）
    - `specs/truth/data/data-model.dbml` -> `staff_accounts.must_change_password`／`failed_login_count`／`locked_until` 欄位定義、型別、預設值、nullable
    - `tms/src/main/resources/db/migration/V11__staff_account_status.sql` -> 對 `staff_accounts` 加欄位的既有寫法慣例
  - 只做：`ALTER TABLE staff_accounts ADD COLUMN must_change_password boolean NOT NULL DEFAULT false`、`ADD COLUMN failed_login_count integer NOT NULL DEFAULT 0`、`ADD COLUMN locked_until timestamptz`（nullable），型別與預設值對齊 dbml 註解。
  - 不做：不寫登入成功清零／錯誤累加／鎖定 15 分的業務邏輯（留給 Phase 3H `[BDD-GREEN]`）；不建索引。

- [X] T241 新增 `V16__notifications.sql`：新建 `notifications` 表＋索引，`tenant_config.key` CHECK 新增 `notification_retention_days`
  - Read:
    - `truth-delta.md` -> `/api-plan` 行 83–84（`通知中心.yaml`／`listNotifications`等）、`/data-plan` 行 132–133（`notifications` 新表、`notification_retention_days` 新鍵）
    - `specs/truth/data/data-model.dbml` -> `Table notifications` 欄位定義、索引、`tenant_config.key` 註解新增值
    - `tms/src/main/resources/db/migration/V12__unclaimed_reminder_thresholds.sql` -> `chk_tenant_config_key` 重建寫法（`DROP CONSTRAINT` 再 `ADD CONSTRAINT` 整個鍵集，本次只新增不移除任何既有鍵）
  - 只做：`CREATE TABLE notifications`（`recipient_staff_id`、`title`、`body`、`target` jsonb、`created_at`、`read_at` nullable，FK 到 `staff_accounts`）＋索引 `(tenant_id, recipient_staff_id, created_at)`；重建 `chk_tenant_config_key` 加入 `notification_retention_days`（既有鍵集全部保留）。
  - 不做：不寫 outbox push 展開時同步寫入本表的程式邏輯（留給 Phase 3I `[BDD-GREEN]`）；不寫保留天數的排程清除 job；不幫 `notification_retention_days` 塞初始值（沒有 DSL 句斷言這個值，使用者裁決「不進行為 truth」，程式讀不到值時的預設落在應用層，非本輪 migration 範圍）。

- [X] T242 新增 `V17__repair_ticket_review_and_verification.sql`：`tasks.state` CHECK 九值→十一值、`repair_tickets` 補 `reject_reason`／`verified_by`／`verified_at`／`origin_ticket_task_id` 四欄
  - Read:
    - `truth-delta.md` -> `/api-plan` 行 85–92（`tasks.state`／`RepairTicketSummary.state`、4 個新端點、`RepairTicket.rejectReason`等）、`/data-plan` 行 134–136（CHECK 擴值、`repair_tickets` 四欄、2 條新 Ref）
    - `specs/truth/data/data-model.dbml` -> `Table tasks.state` CHECK 十一值定義、`Table repair_tickets` 新增四欄定義與兩條 Ref（`verified_by`→`staff_accounts`、`origin_ticket_task_id`→`repair_tickets` 自參照）
    - `tms/src/main/resources/db/migration/V1__schema.sql:207-209` -> `chk_tasks_state` 現有九值
    - `tms/src/main/resources/db/migration/V14__repair_ticket_urgent_judgement.sql` -> 對 `repair_tickets` 加欄位的既有寫法慣例
  - 只做：`ALTER TABLE tasks DROP CONSTRAINT chk_tasks_state` 再 `ADD CONSTRAINT`，九值加入 `pending_review`、`rejected`（十一值）；`ALTER TABLE repair_tickets ADD COLUMN reject_reason text`（nullable）、`ADD COLUMN verified_by uuid`（nullable）、`ADD COLUMN verified_at timestamptz`（nullable）、`ADD COLUMN origin_ticket_task_id uuid`（nullable）；加 FK `verified_by`→`staff_accounts(tenant_id, id)`、`origin_ticket_task_id`→`repair_tickets(tenant_id, task_id)`（自參照）。
  - 不做：不寫審核／退回／重送／驗收四個動作的狀態轉移程式邏輯（留給 Phase 3K／3M `[BDD-GREEN]`）；`task_events.type` 不建 CHECK（沿用 V5 既有決定，`repair_approved`／`repair_rejected`／`repair_resubmitted`／`repair_verified` 純文件列舉）。

- [X] T243 建立帳號與登入 stepdef 落點骨架
  - Read:
    - `specs/truth/features/backend/帳號與登入/dsl.md`
    - `tms/src/test/java/com/athena/tms/bdd/steps/common/CommonGivenSteps.java`（既有 package 慣例）
  - 只做：新建 `tms/src/test/java/com/athena/tms/bdd/steps/account/`（`AccountGivenSteps.java`、`AccountWhenSteps.java`、`AccountThenSteps.java`），每個檔案留空殼（package、class、必要 import），確認 Phase 2c 只寫進這三個檔。
  - 不做：不寫任何 Given／When／Then 語意（由 Phase 2c 的 RED 任務填入）。

- [X] T244 建立通知中心 stepdef 落點骨架
  - Read:
    - `specs/truth/features/backend/推播/dsl.md`（通知中心段落）
    - `tms/src/test/java/com/athena/tms/bdd/steps/push/`（既有推播 package，確認是否已有可擴充的 Given/When/Then 檔案）
  - 只做：在既有 `steps/push/` package 下新建（或確認既有檔案可擴充）`PushGivenSteps.java`／`PushWhenSteps.java`／`PushThenSteps.java` 的通知中心函式殼，不與既有升級鏈／未接提醒的 stepdef 混寫在同一區塊（用註解分段）。
  - 不做：不寫任何通知中心語意（由 Phase 2c 的 RED 任務填入）。

## Phase 2c: Test Alignment & Implementation（登入／通知中心／工務視角／審核與驗收）

**Goal**: 把本輪新增 Feature（Phase 3H–3M）用到、尚無 stepdef 的 DSL 句對齊最新版 truth；以及兩句既有 Given 因新增選填欄位需要的 ALIGN。不寫產品行為。

**DSL 參照**:
- 帳號與登入 9 句（T245–T253）在 `specs/truth/features/backend/帳號與登入/dsl.md`（新模組，本輪首次承接）。
- 通知中心 8 句（T254–T261）在 `specs/truth/features/backend/推播/dsl.md`（通知中心段落）。
- 工務視角／審核／驗收／櫃檮直通 22 句（T262–T283）在 `specs/truth/features/backend/工務單/dsl.md`。
- 介面根 `specs/truth/features/backend/dsl.md` 有 1 句本輪新增選填欄位的 ALIGN（T284：`已有以下工務單：` 新增 `來源`／`退回原因`／`驗收人` 三個選填欄位）。
- 讀法同 Phase 2／2b：Given/When 讀 `怎麼做`、`權威狀態落地`、`回寫`；Then 讀 `必查`。`truth-delta.md` 只告訴 ADD/MODIFY，語意以 `dsl.md` 該列為準。

**Markers**：定義同 Phase 2（`[BDD-ALIGN]`=MODIFY、`[BDD-RED]`=ADD 或本輪 Feature 用到尚無 stepdef 的句）。本輪沒有 DELETE。三個 marker 都只動測試層，不寫產品碼。

**Shared Must Read**:
- `specs/truth/features/backend/帳號與登入/dsl.md` -> 全部 Given／When／Then
- `specs/truth/features/backend/推播/dsl.md` -> 通知中心段落全部 Given／When／Then
- `specs/truth/features/backend/工務單/dsl.md` -> 工務視角、審核與驗收、櫃檮直通相關 Given／When／Then（見上方 DSL 參照）
- `specs/truth/features/backend/dsl.md` -> Given `已有以下工務單：`、狀態用語對照表新增 2 列（等主管審核／主管未核准）
- `truth-delta.md` -> `/dsl-refine` 第二輪（行 225–233）、第三輪（行 260–268）、第四輪（行 280–294）
- `specs/truth/contracts/components/schemas/員工帳號與登入.yaml`（`LoginRequest`／`LoginResponse`）、`groups/營運管理/員工帳號與登入.yaml`（`staffAccountsLogin`）
- `specs/truth/contracts/components/schemas/通知中心.yaml`、`groups/知識與通知/通知中心.yaml`
- `specs/truth/contracts/components/schemas/工務單.yaml`（`RepairTicket.rejectReason`/`verifiedBy`/`verifiedAt`/`originTicketNo`）、`groups/現場作業/工務單.yaml`（4 個審核/驗收端點、`releaseRepairTicketToPool`）、`groups/現場作業/員工工作台.yaml`（`listDeptPool` 篩選、`MyToday.engineeringSummary`）
- `tms/src/test/java/com/athena/tms/bdd/steps/account/`（T243 骨架）
- `tms/src/test/java/com/athena/tms/bdd/steps/push/`（T244 骨架）
- `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairGivenSteps.java`、`RepairWhenSteps.java`、`RepairThenSteps.java`
- `tms/src/test/java/com/athena/tms/bdd/support/Terms.java`

**Boundary**:
- 一條 DSL 一個 task。
- 只改該句的 stepdef／assertion／直接依賴的 helper；不寫產品碼（登入鎖定、通知中心寫入、審核/驗收/直開/放回的實際改寫留給 Phase 3H–3M 的 `[BDD-GREEN]`）。
- T245–T253 依賴 T240、T243；T254–T261 依賴 T241、T244；T262–T283、T284 依賴 T242（審核/驗收/來源相關）與既有 `repair` stepdef 骨架；T285 不依賴 migration，只改 `Terms.java` 對照表。
- review 啟動 subagent；本輪所有 Test Scope 不得再有 undefined step，失敗只能是 assertion 或產品行為。有 issues 就修正再 review，直到沒有任何問題。通過前不解鎖 Phase 3H–3M。

**Parallel Hint**:
- T245–T285 各派一個獨立 subagent；T286 等全部回來再啟動 subagent 來 review。

- [X] T245 [P] [BDD-RED] `Given: "{員工}" 的員工編號密碼為 "{員工編號}" / "{密碼}"`
- [X] T246 [P] [BDD-RED] `Given: "{員工}" 的帳號是臨時密碼`
- [X] T247 [P] [BDD-RED] `Given: "{員工}" 已連續輸入錯誤密碼 {次數} 次`
- [X] T248 [P] [BDD-RED] `When: "{員工}" 用員工編號 "{員工編號}" 密碼 "{密碼}" 登入`
- [X] T249 [P] [BDD-RED] `Then: 登入成功，取得 "{員工}" 的身分與可用憑證`
- [X] T250 [P] [BDD-RED] `Then: 登入失敗，提示 "{提示}"`
- [X] T251 [P] [BDD-RED] `Then: "{員工}" 的帳號已鎖定 {分鐘} 分鐘`
- [X] T252 [P] [BDD-RED] `Then: "{員工}" 的帳號已停用，登入被拒`
- [X] T253 [P] [BDD-RED] `Then: "{員工}" 必須先設定新密碼才能繼續`

- [X] T254 [P] [BDD-RED] `Given: "{員工}" 在 "{時刻}" 收到以下通知：`
- [X] T255 [P] [BDD-RED] `When: "{員工}" 查看通知中心`
- [X] T256 [P] [BDD-RED] `When: "{員工}" 將 "{標題}" 的通知標為已讀`
- [X] T257 [P] [BDD-RED] `When: "{員工}" 將所有通知標為已讀`
- [X] T258 [P] [BDD-RED] `Then: "{員工}" 的通知中心依序包含 {標題清單}`
- [X] T259 [P] [BDD-RED] `Then: "{員工}" 的通知中心「{分組}」分組包含 {標題清單}`
- [X] T260 [P] [BDD-RED] `Then: "{員工}" 的 "{標題}" 通知已讀`
- [X] T261 [P] [BDD-RED] `Then: "{員工}" 的所有通知都已讀`

- [X] T262 [P] [BDD-RED] `Given: "{工務}" 手上有 {張數} 張維修中的工務單，今天已完修 {已完修張數} 張，其中 {急件數} 張是急件`
- [X] T263 [P] [BDD-RED] `When: "{主管}" 核准 "{單號}"`
- [X] T264 [P] [BDD-RED] `When: "{主管}" 以原因 "{原因}" 退回 "{單號}"`
- [X] T265 [P] [BDD-RED] `When: "{員工}" 補資料再送出 "{單號}"`
- [X] T266 [P] [BDD-RED] `When: "{主管}" 驗收通過 "{單號}"`
- [X] T267 [P] [BDD-RED] `When: "{工務}" 附 {張數} 張完修照片，回報 "{單號}" 暫時處理，說明 "{說明}"`
- [X] T268 [P] [BDD-RED] `When: "{工務}" 附 {張數} 張完修照片，回報 "{單號}" 無法修復，說明 "{說明}"`
- [X] T269 [P] [BDD-RED] `When: "{工務}" 為 "{房號}" 開立以下工務單，不經過任何房務任務：`
- [X] T270 [P] [BDD-RED] `When: "{員工}" 為 "{房號}" 交辦工務部門，開立以下工務單：`
- [X] T271 [P] [BDD-RED] `When: "{工務}" 把 "{單號}" 放回工務待認領`
- [X] T272 [P] [BDD-RED] `When: "{工務}" 查看工務視角今日進度`
- [X] T273 [P] [BDD-RED] `When: "{工務}" 只看 "{分流}" 的工務待認領`
- [X] T274 [P] [BDD-RED] `Then: "{單號}" 的來源為 "{來源}"`
- [X] T275 [P] [BDD-RED] `Then: 工務待認領包含 "{單號}"`
- [X] T276 [P] [BDD-RED] `Then: 工務待認領不包含 "{單號}"`
- [X] T277 [P] [BDD-RED] `Then: 工務待認領順序為 {單號清單}`
- [X] T278 [P] [BDD-RED] `Then: "{單號}" 的退回原因為 "{原因}"`
- [X] T279 [P] [BDD-RED] `Then: "{單號}" 已由 "{主管}" 驗收`
- [X] T280 [P] [BDD-RED] `Then: "{單號}" 尚未驗收`
- [X] T281 [P] [BDD-RED] `Then: "{房號}" 的房況轉為停用待確認`
- [X] T282 [P] [BDD-RED] `Then: 系統為 "{單號}" 自動建立一張追蹤單，進工務待認領`
- [X] T283 [P] [BDD-RED] `Then: "{工務}" 的工務視角今日進度為：`

- [X] T284 [P] [BDD-ALIGN] `Given: 已有以下工務單：`（新增選填欄位 `來源`、`退回原因`、`驗收人`）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairGivenSteps.java:93`

- [X] T285 [P] [BDD-ALIGN] `Terms.java` 工務單狀態用語對照新增：`等主管審核`→`pending_review`、`主管未核准`→`rejected`
  - Read: `tms/src/test/java/com/athena/tms/bdd/support/Terms.java`（`REPAIR_TICKET_STATE` map）、`specs/truth/features/backend/dsl.md` 狀態用語對照表

- [X] T286 subagent review (phase quality gate)

## Phase 3H: ADD Feature File - backend/帳號與登入/員工登入.feature

**Goal**: 實作登入（`staffAccountsLogin`）的成功、密碼錯誤、鎖定、停用、臨時密碼強制改密 5 種結果，讓 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/backend/帳號與登入/員工登入.feature` -> `Feature: 員工登入`（6 Rule／6 場景）
- `specs/truth/features/backend/帳號與登入/dsl.md` -> 本檔 Given／When／Then 全部列
- `truth-delta.md` -> `/dsl-refine` 第二輪 ADD 員工登入（行 227）；`/api-plan` 行 79–82（`LoginRequest`／`LoginResponse`／`staffAccountsLogin`／`staff_accounts` 三欄）
- `specs/truth/contracts/components/schemas/員工帳號與登入.yaml` -> `LoginRequest`、`LoginResponse`
- `specs/truth/contracts/groups/營運管理/員工帳號與登入.yaml` -> `staffAccountsLogin`（401/403/429 錯誤路徑）
- `tms/src/main/java/com/athena/tms/`（既有員工帳號 controller/service，供定位 `staffAccountsLogin` 實作落點）

**Boundary**:
- 只處理登入本身（成功、密碼錯、員編不存在、鎖定、停用、臨時密碼強制改密），不處理改密 API 本身是否存在（若 truth 未定義改密端點，以回應標示 `mustChangePassword` 為準，不另外開發改密流程）。
- 連續 5 次錯誤鎖定 15 分鐘、成功登入清零失敗次數，依 T240 新增的三欄實作。

**Test Scope**:
- `specs/truth/features/backend/帳號與登入/員工登入.feature`

- [X] T287 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T288 [BDD-REFACTOR] 在綠燈下整理登入失敗計數、鎖定判斷與密碼雜湊比對的共用邏輯（NOOP：結構已最小、無重複邏輯）

## Phase 3I: ADD Feature File - backend/推播/通知中心.feature

**Goal**: 實作通知中心（`listNotifications`、`markNotificationRead`、`markAllNotificationsRead`）與 outbox push 展開時同步寫入持久通知紀錄，讓 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/backend/推播/通知中心.feature` -> `Feature: 通知中心`（4 Rule／4 場景）
- `specs/truth/features/backend/推播/dsl.md` -> 通知中心段落 Given／When／Then
- `truth-delta.md` -> `/dsl-refine` 第二輪 ADD 通知中心（行 229）；`/api-plan` 行 83–84（`通知中心.yaml`、3 個端點）；`/data-plan` 行 132–133（`notifications` 新表、保留天數鍵）
- `specs/truth/contracts/components/schemas/通知中心.yaml` -> `notifications`
- `specs/truth/contracts/groups/知識與通知/通知中心.yaml` -> `listNotifications`、`markNotificationRead`、`markAllNotificationsRead`
- `tms/src/main/java/com/athena/tms/`（既有 outbox push 展開程式碼落點，供新增同交易寫入 `notifications` 的改動點）

**Boundary**:
- 只處理通知中心本身的查詢、分組（今天／更早）、標記已讀；不寫保留天數排程清除 job（`notification_retention_days` 只是鍵存在，沒有 DSL 句斷言排程清除行為，不在 Test Scope 內）。
- outbox push 展開時同一交易寫入一筆 `notifications`；不改動既有 outbox 本身的 relay 語意。

**Test Scope**:
- `specs/truth/features/backend/推播/通知中心.feature`

- [X] T289 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T290 [BDD-REFACTOR] 在綠燈下整理通知分組（今天／更早）計算與已讀標記的共用邏輯（查詢欄位常數抽共用；分組屬 App 端，後端無可整理）

## Phase 3J: ADD Feature File - backend/工務單/工務視角維修與開單.feature

**Goal**: 實作工務視角能力——完修三選一（已修好／暫時處理自動建追蹤單／無法修復轉房況停用待確認）、工務自開與櫃檮交辦直開（`sourceTaskId` 選填＋`roomId`／`isPublicArea`／`assigneeId`）、放回待認領（限公共區域）、`listDeptPool` 急件／來源篩選、工務視角今日進度彙總，讓 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/工務視角維修與開單.feature` -> `Feature: 工務視角維修與開單`（整檔，含第二輪 9 Rule 與第三輪櫃檮直通新增內容）
- `specs/truth/features/backend/工務單/dsl.md` -> 工務視角相關 Given／When／Then（見 Phase 2c T262–T283 清單）
- `truth-delta.md` -> `/dsl-refine` 第二輪 ADD 工務視角維修與開單（行 231）、第三輪 ADD 櫃檮直通（行 262–265）；`/api-plan` 行 93–98（`CompleteTaskRequest.resolutionType`、`CreateRepairTicketRequest` 三欄、`releaseRepairTicketToPool`、`listDeptPool` 篩選、`MyToday.engineeringSummary`）
- `specs/truth/contracts/components/schemas/任務.yaml` -> `CompleteTaskRequest.resolutionType`
- `specs/truth/contracts/components/schemas/工務單.yaml` -> `CreateRepairTicketRequest`（`roomId`／`isPublicArea`／`assigneeId`、`sourceTaskId` 選填）
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `releaseRepairTicketToPool`
- `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `listDeptPool`（`urgent`／`source` 參數）、`getMyToday`（`engineeringSummary`）
- `tms/src/main/java/com/athena/tms/repair/RepairService.java`、`RepairController.java`、`tms/src/main/java/com/athena/tms/service/TaskExecutionService.java`（`tasksComplete`、`myToday` 現行實作）

**Boundary**:
- 完修三選一：`fixed`（既有行為不變）、`temporary`（自動建立追蹤單，房號類別同原單，狀態 pool）、`cannot_fix`（`rooms.hk_state`=`out_of_order`）。
- 工務自開／櫃檮交辦開單共用同一個 `createRepairTicket` 路徑，差異只在呼叫者部門決定 `tasks.source`（staff／desk）；不新增第二個開單端點。
- 放回待認領只允許公共區域（無房號）的工務單；客房工務單維持既有「只能暫停」，回 409 `room_ticket_not_requeueable`。
- 不處理本輪另立的審核／驗收狀態轉移（`pending_review`／`rejected`／驗收），那是 Phase 3K／3M 的範圍；本檔場景若涉及開單去向（急件/工務自開/櫃檮直接進 pool vs 一般件進審核），只驗證「直接進 pool」這三條路徑，不驗證審核路徑本身的核准/退回細節。

**Test Scope**:
- `specs/truth/features/backend/工務單/工務視角維修與開單.feature`

- [X] T291 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T292 [BDD-REFACTOR] 在綠燈下整理完修三選一分支與工務視角今日統計查詢的共用邏輯，避免與 `myToday` 既有統計邏輯重複查表（NOOP：統計已共用單次 rows 迴圈、三選一分支已抽共用方法）

## Phase 3K: MODIFY Feature File - backend/工務單/開立工務單與急件.feature（第四輪：補主管審核與退回/重送）

**Goal**: 在既有急件判定（上一輪已綠）基礎上，實作主管審核流程——房務報修一般件送出後進 `pending_review`，急件/工務自開/櫃檮交辦直接進 pool；主管核准（`approveRepairTicket`）、退回附原因（`rejectRepairTicket`）、房務補資料重送（`resubmitRepairTicket`），讓更新後的 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/開立工務單與急件.feature` -> 本輪新增／改寫的 Rule（一般件進審核、急件直接進池、核准、退回須附原因、補資料重送）
- `specs/truth/features/backend/工務單/dsl.md` -> When `"{主管}" 核准`、`"{主管}" 以原因退回`、`"{員工}" 補資料再送出`；Then `"{單號}" 的退回原因為`
- `truth-delta.md` -> `/dsl-refine` 第四輪（行 280–289）；`/api-plan` 行 85–92（`tasks.state` 十一值、4 個新端點）
- `specs/truth/contracts/components/schemas/shared.yaml` -> `RepairTicketSummary.state`（`pending_review`／`rejected`）
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `approveRepairTicket`、`rejectRepairTicket`、`resubmitRepairTicket`
- `tms/src/main/java/com/athena/tms/repair/RepairService.java:401-466`（`create` 方法，開單去向判斷需加入一般件/急件分流）、`RepairController.java`

**Boundary**:
- 只改開單去向分流與審核三個端點的狀態轉移；不動 Phase 3J 已實作的工務自開／櫃檮交辦判斷呼叫者部門的邏輯本身（只是這裡要確認這兩條路徑確實跳過審核直接進 pool）。
- 審核只套用在房務報修（`source`=staff）且非急件的單；急件不論來源都不經審核。

**Test Scope**:
- `specs/truth/features/backend/工務單/開立工務單與急件.feature`

- [X] T293 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T294 [BDD-REFACTOR] 在綠燈下整理開單去向判斷（審核 vs 直接進池）與審核三動作的共用狀態轉移邏輯（NOOP：轉移已共用 guard+recordEvent 骨架、分流單一出口、權限已抽 requireManager）

## Phase 3L: MODIFY Feature File - backend/工務單/重複報修補照片與撤回.feature

**Goal**: 審核前（`pending_review`、`rejected`）報修人仍可撤回工務單，讓更新後的 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/重複報修補照片與撤回.feature` -> 新 Rule「等主管審核時，報修人也可以撤回工務單」
- `specs/truth/features/backend/工務單/dsl.md` -> When `"{員工}" 撤回 "{單號}"`
- `truth-delta.md` -> `/dsl-refine` 第四輪（行 286）
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `withdrawRepairTicket` description（`pending_review` 也可撤回）

**Boundary**:
- 只擴充撤回允許的來源狀態（`pending_review`、`rejected`、pool），不改既有「認領後不可撤回」409 語意。

**Test Scope**:
- `specs/truth/features/backend/工務單/重複報修補照片與撤回.feature`

- [X] T295 [BDD-GREEN] 讓 Test Scope 全綠（rejected 依 dsl.md／contracts 權威文字維持 409，未納入可撤回集合）
- [X] T296 [BDD-REFACTOR] 在綠燈下整理撤回允許狀態集合的判斷（NOOP：四個轉移各自單一狀態條件、共用 guard helper，無重複狀態表）

## Phase 3M: MODIFY Feature File - backend/工務單/完修與回到原任務.feature

**Goal**: 完修後新增「等主管驗收」子狀態（不開新狀態值，只補驗收人／驗收時間），主管驗收通過後記錄驗收人，讓更新後的 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/完修與回到原任務.feature` -> 新 Rule「主管驗收通過已完修的工務單後，記錄驗收人並結案」
- `specs/truth/features/backend/工務單/dsl.md` -> When `"{主管}" 驗收通過 "{單號}"`；Then `"{單號}" 已由 "{主管}" 驗收`、`"{單號}" 尚未驗收`
- `truth-delta.md` -> `/dsl-refine` 第四輪（行 287–288）；`/api-plan` 行 92（`verifyRepairTicket`）
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `verifyRepairTicket`
- `specs/truth/contracts/components/schemas/工務單.yaml` -> `RepairTicket.verifiedBy`／`verifiedAt`

**Boundary**:
- 驗收不開新狀態值，工務單狀態仍是 `done`；只寫 `verified_by`／`verified_at`。
- 不實作「驗收退回」分支（原型未描繪，上游缺口，見 `truth-delta.md` 行 304）。

**Test Scope**:
- `specs/truth/features/backend/工務單/完修與回到原任務.feature`

- [X] T297 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T298 [BDD-REFACTOR] 在綠燈下整理驗收人／驗收時間寫入與既有完修共用事件歷程邏輯（NOOP：已共用 guard+recordEvent 骨架，寫入模式與 reject 一致）
