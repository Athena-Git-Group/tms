# Tasks: 001-staff-app-increment（/dsl-refine 待認領改名／未接超時兩段模型／急件系統判定／ios-app 新規格 回補／ios-app 員工 App 實作）

**Plan Package**: `specs/plans/001-staff-app-increment`
**Core Inputs**: `spec.md`, `plan.md`, `research.md`, `truth-delta.md`（`## /dsl-refine` 段，commit `6fff7c7` 新增的列）, `specs/truth/techstack.md`, `specs/truth/contracts/**`, `specs/truth/data/data-model.dbml`, `specs/truth/features/backend/**`, `specs/truth/features/ios-app/**`

本輪承接 commit `6fff7c7`（owner `/dsl-refine`）落下的四項裁決：A「待接池／待接」全面改「待認領」、B 廢 L1/L2/L3＋`due_at` 升級鏈改「未接超時」兩段提醒、C 急件改系統判定、D 新增 4 支 ios-app feature；以及 10 筆用語落差。上一輪 T165–T203（116 場景全綠，commit `175ca16`）已完成之前的 truth 內容，本輪只處理 `truth-delta.md` `/dsl-refine` 段新增的列。

T204–T239（全數 [X]）已完成上述四項裁決的 backend 部分。T240 起新增的 Phase 1c／2c／3H–3M 承接 `truth-delta.md` `/dsl-refine` 段「第二輪」（commit `4e7f9dd`：登入帳號、通知中心、工務視角三缺口）、「第三輪」（commit `bd9ab9a`：櫃檮直通工務）、「第四輪」（commit `d27487b`：主管審核與驗收），以及對應解鎖的 `/api-plan`（commit `42683ae`）、`/data-plan`（commit `fdb5a2e`）內容，全部範圍為 backend。T299 起新增的 Phase 1d／2d／3N–3X／4 是第一輪 ios-app 任務，承接上一輪 Scope Note 明文留下的決議（下一輪針對 ios-app 的 ADD feature 另開 Phase 3/4，沿用 backend 已解鎖的 contracts），範圍是 `app/mobile/`（Flutter App）與 `app/bdd/`（Appium harness）兩個目錄，驗收真相為 `specs/truth/features/ios-app/` 的 16 支 feature。

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

## Scope Note（本輪新增範圍：ios-app 員工 App 實作，全部 App／harness 側）

本輪承接上一輪 Scope Note 的明文決議（「下一輪 `/tasks` 應針對 ios-app 這批 ADD feature 另開一輪 Phase 3/4（沿用本輪 backend 已解鎖的 contracts）」）。範圍是 `app/mobile/`（Flutter App）與 `app/bdd/`（Appium＋Cucumber-JVM harness）兩個目錄，不改 `tms/`、不改 `specs/truth/**`。

驗收真相是 `specs/truth/features/ios-app/` 的 16 支 feature／108 個場景（`RunTruthFeaturesTest` dry-run 現況：108 scenarios、30 errors 全為 undefined step）。其中 `推播/點開推播的去處.feature`（12 個場景，含 Scenario Outline 展開）已於 App 骨架期經 `app/bdd/run-f3.sh` 真跑全綠（F3 推播金絲雀），本輪不重做，只在 Phase 4 回歸；其餘 14 支 feature／96 個場景是本輪的 Test Scope。（場景數一律以 runner 展開後為準，不是 feature 檔裡的 `場景` 行數。）

**現況盤點（本輪的起點，`/implement` 不必重查）**：

- `app/mobile/lib/`：`main.dart`＋`core/{push_gateway,test_backdoor,tms_semantics,theme}.dart`＋4 個畫面（`features/{login,home,pool,task}`）。13 個邏輯畫面中已有 4 個；`login_screen.dart` 是唯一真的打後端的畫面，`home`／`pool`／`task` 全部從測試後門 `/seed` 灌進來的 fixture 渲染，沒有 API client。
- `app/bdd/`：harness 可跑（`AppiumWorld` 有 session 生命週期、`byTestId()` 雙平台分流、`waitForTestId`、`sendPush`／`backgroundApp`／`tapPushNotification`、`/seed` 通道）。step 包 9 個（`ask`、`common`、`handover`、`pool`、`push`、`repair`、`system`、`taskexecution`、`today`），其中只有 `push` 是真實作；其餘 8 包共 118 處 `PendingException` stub。`steps/account/` 不存在。
- 測試後門（`core/test_backdoor.dart`）現有端點：`GET /state`、`POST /clock`、`POST /offline`、`POST /upload-fail`、`POST /seed`。
- undefined step 共 **49 句**（30 個場景）：帳號與登入 19 句（整個模組，無 stepdef 包）、通知中心 9 句、工務單（工務視角＋房務端生命週期）21 句。其餘 104 句 dsl 已有 stepdef，但多為 `PendingException`。

**任務邊界取捨（記錄，供下一輪檢視）**：Phase 2d 不採「一條 DSL 一個 task」（那會是 49 個 task，與本輪 Phase 3 的 14 支 feature GREEN 合計逾 80 項）。改以「模組 × Given／When／Then」切 9 個 task，每個 task 把它負責的句型逐句列出，驗收仍以 `dry-run` 的 undefined 句數為機械判準。這是本輪為了讓 Phase 3 能進入同一份清單所做的取捨，不是認定這些句型不必逐句對照 `dsl.md`。

**`工務單/dsl.md` 有 1 句沒有任何 feature 使用**（`工務待認領分類標籤顯示如下：`，dsl.md:72）：`工務視角.feature` 的分流場景改用 `工務待認領依序為 {單號清單}` 驗，這句沒有 Test Scope 承接，本輪不排 RED 任務，列入下方上游缺口。

## Phase 1d: Foundational（App↔後端通道、stepdef 落點、分模組 runner）

**Goal**: 只建立 Phase 2d／3N–3X 會用到的資料通道、落點骨架與執行工具，不實作任何畫面行為或斷言語意。比照 T204、T226、T240 先例：共用結構先進 Foundational 當機械補遺。

- [X] T299 App 端 API client：把畫面資料來源從測試後門 fixture 換成真後端
  - Read:
    - `specs/truth/features/ios-app/dsl.md` -> 共通實作約定「Given 透過後端測試資料準備權威狀態」「每個 Then 除了檢查畫面，還要確認畫面與後端權威狀態一致」
    - `app/mobile/lib/features/login/login_screen.dart:9-14`（`API_BASE_URL` 的 `--dart-define` 既有慣例）、`:18-24`（登入 request body 欄位名的 TODO）
    - `app/mobile/lib/core/test_backdoor.dart:31-51`（`/seed` 目前存什麼）
    - `specs/truth/contracts/components/schemas/員工帳號與登入.yaml` -> `LoginRequest`／`LoginResponse`（T287 已實作，欄位為 `loginId`／`password`，回應帶 `mustChangePassword`）
    - `specs/plans/001-staff-app-increment/ui/ui-plan.md:305`（App 端依賴的後端契約清單，不得自創欄位）
  - 只做：新增 `app/mobile/lib/core/api_client.dart`——以 `API_BASE_URL` 為基底、帶登入取得的 JWT，封裝 `GET /me/today`、`GET /pool`、`GET /tasks/{id}` 三個讀取端點與登入端點；`login_screen.dart` 的 request body 欄位名改為契約的 `loginId`／`password`（移除該檔 TODO）。`home`／`pool`／`task` 三個畫面改從 `api_client` 取資料，測試後門 `/seed` 退回只負責裝置側注入（時間、離線、上傳失敗），不再當資料來源。
  - 不做：不新增 `ui-plan.md:305` 清單以外的端點呼叫；不實作任何本輪 feature 的畫面行為（留給 Phase 3 的 `[BDD-GREEN]`）；不碰 `push_gateway.dart`（F3 已綠）。

- [X] T300 harness 端後端通道：真資料 fixture 與「後端權威狀態」斷言入口
  - Read:
    - `app/bdd/src/test/java/com/athena/app/bdd/AppiumWorld.java:255-345`（`seedStaff`／`seedTasks`／`ensurePoolTask`／`postSeed` 目前只打 App 後門）
    - `app/bdd/run-f3.sh`（啟動流程：flutter build → boot simulator → appium → mvn；本輪要在前面加「後端就緒」一段）
    - `tms/src/test/java/com/athena/tms/bdd/support/`（後端 BDD 的 fixture 建法，供對照欄位與 enum 值，不得自創）
    - `.athena/handoff/2026-11-04-183000-staff-app-ios-kickoff.md` §6 -> 跑測試的 `JAVA_HOME`／`TESTCONTAINERS_RYUK_DISABLED` 陷阱
  - 只做：在 `AppiumWorld`（或同層新 helper）加一組對後端 REST 的 fixture／讀回通道：建員工與帳號、建任務與工務單、讀回任務與工務單權威狀態；後端位址用系統屬性（比照 `appium.server.url` 既有風格）設定，預設 `http://127.0.0.1:8080`。`run-f3.sh` 抽出共用的環境準備片段供 T304 的分模組腳本沿用。
  - 不做：不在 harness 內啟動後端容器（後端由 `docker-compose`＋`tms` 既有方式先起好，腳本只檢查就緒並在未就緒時明確失敗）；不寫任何 Given／Then 語意。

- [X] T301 測試後門補兩個注入開關：登入情境與連線不穩
  - Read:
    - `app/mobile/lib/core/test_backdoor.dart:140-180`（`case 'POST /clock'`／`'POST /offline'`／`'POST /upload-fail'` 既有寫法）
    - `specs/truth/features/ios-app/帳號與登入/dsl.md` -> Given `這支手機已記住 "{員工}" 的帳號`、`這支手機是共用手機，沒有記住任何帳號`
    - `specs/truth/features/ios-app/系統層/dsl.md` -> Given `"{員工}" 的連線目前不穩定`
  - 只做：新增 `POST /remembered-account`（設定／清除「這支手機記住的帳號」）與 `POST /flaky-connection`（把即時連線切成異常態），兩者都沿用既有 `TEST_BACKDOOR=true` 編譯期旗標與 JSON 風格。
  - 不做：不實作登入畫面兩種模式的版面差異與連線異常列（留給 Phase 3N／3X 的 `[BDD-GREEN]`）；不新增 dsl 未要求的注入能力。

- [X] T302 建立帳號與登入 stepdef 落點骨架
  - Read:
    - `specs/truth/features/ios-app/帳號與登入/dsl.md`（19 句的歸屬）
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/push/`（既有包的 Given／When／Then 分檔與 `package-info.java` 慣例）
  - 只做：新建 `app/bdd/src/test/java/com/athena/app/bdd/steps/account/`（`AccountGivenSteps.java`、`AccountWhenSteps.java`、`AccountThenSteps.java`、`package-info.java`），每個檔案留空殼（package、class、建構子注入 `AppiumWorld`、必要 import），確認 Phase 2d 的 T306–T308 只寫進這三個檔。
  - 不做：不寫任何 Given／When／Then 語意。

- [X] T303 通知中心與工務視角的 stepdef 落點分段
  - Read:
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/push/PushGivenSteps.java`、`PushWhenSteps.java`、`PushThenSteps.java`（F3 的真實作，本輪不得改動其行為）
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/RepairGivenSteps.java`、`RepairWhenSteps.java`、`RepairThenSteps.java`
  - 只做：在 `steps/push/` 三個檔用註解分出「通知中心」區塊、在 `steps/repair/` 三個檔用註解分出「工務視角」與「房務端生命週期」區塊，留出 T309–T314 的落點，不與 F3 推播去處的既有實作混寫。
  - 不做：不寫任何語意；不動 `push` 包既有的 3 個已實作 step。

- [X] T304 分模組 runner 與執行腳本
  - Read:
    - `app/bdd/src/test/java/com/athena/app/bdd/RunTruthFeaturesTest.java`（`@SelectDirectories` 寫死全目錄）、`RunF3PushFeatureTest.java`（只選推播目錄的理由：避免 pending 場景逐一重開模擬器）
    - `app/bdd/run-f3.sh`（五段式流程）
  - 只做：新增 `RunModuleFeatureTest`，要跑的目錄改由系統屬性 `cucumber.features` 指定（不寫死）；新增 `app/bdd/run-module.sh <模組目錄名>`，沿用 T300 抽出的環境準備片段，跑 `-Dtest=RunModuleFeatureTest -Dcucumber.features=../../specs/truth/features/ios-app/<模組>`。
  - 不做：不改 `RunTruthFeaturesTest`／`RunF3PushFeatureTest` 的既有選擇範圍（Phase 4 要靠它們做全量與 F3 回歸）。
  - 驗收：`app/bdd/run-module.sh 查一下` 只跑 `查一下/查一下畫面.feature` 的 4 個場景，不觸發其他模組。

- [X] T305 建立缺的畫面與路由骨架
  - Read:
    - `specs/plans/001-staff-app-increment/ui/ui-plan.md:69-184`（13 個邏輯畫面的目的、進入條件、流轉）、`:230-276`（accessibility id 命名規則與逐項 id 表）
    - `app/mobile/lib/core/tms_semantics.dart`（`Semantics(identifier:)` 與 `enabled` 顯式曝露的共用包裝，所有新畫面一律經它掛 id）
    - `app/mobile/lib/features/pool/pool_screen.dart`（既有畫面的檔案結構與 id 掛法範本）
  - 只做：建立本輪 Test Scope 需要但尚不存在的畫面檔與路由骨架——`features/notification/`（通知中心）、`features/ticket/`（工務單詳情）、`features/celebrate/`（完成慶祝）、`features/me/`（我的，只需到「登出」入口）、`features/engineering/`（工務首頁 `engHome`、工務待認領 `engPool`）、以及任務頁的三個 sheet（`hold`／`repair`／`ask`）落點；每個畫面只放版面骨架與按 ui-plan 約定掛好的 accessibility id，不接資料、不實作互動。
  - 不做：不實作任何 feature 行為；不建 ui-plan 標「待原型補畫面」的 id（`home.streak-badge`、`task.card.due-badge`、`pool.card.common-area-label`、`pool.empty-state--no-active-staff`——見下方上游缺口）；不碰「情境模擬」操作面板（ui-plan:12 明訂不進 App）。

## Phase 2d: Test Alignment（ios-app 49 句 undefined step → RED）

**Goal**: 把 16 支 ios-app feature 用到、尚無 stepdef 的 49 句 DSL 依各模組 `dsl.md` 的「App 實作語意（Appium）」落成可被跑到的 step definitions。不實作畫面行為。

**DSL 參照**:
- 帳號與登入 19 句（T306–T308）在 `specs/truth/features/ios-app/帳號與登入/dsl.md`（新模組，本輪首次承接）。
- 通知中心 9 句（T309–T311）在 `specs/truth/features/ios-app/推播/dsl.md`（通知中心段落）。
- 工務單 21 句（T312–T314）在 `specs/truth/features/ios-app/工務單/dsl.md`（工務視角 11 句、房務端生命週期 3 句、共用 7 句）。
- 讀法：用 task 列出的句型對到該檔那一列，以「App 實作語意（Appium）」當實作語意。Given／When 讀 `怎麼做`、`權威狀態落地`、`回寫`；Then 讀 `必查`。元素一律以 dsl 該列指定的 accessibility id 定位（經 `byTestId()` 分流），不用文字座標或截圖比對。
- `specs/truth/features/ios-app/dsl.md` 的共通實作約定（Given 經後端準備權威狀態、Then 同時查畫面與後端）對 49 句全部適用。

**Markers**:
- `[BDD-RED]`：本輪 feature 用到、尚無 stepdef 的句。依 `dsl.md` 該列寫出 stepdef。完成時這句可被跑到，失敗只能是定位不到元素或產品行為，不能是 undefined step。
- 本輪 ios-app feature 沒有 MODIFY／DELETE，故無 `[BDD-ALIGN]`／`[BDD-REMOVE]`。
- marker 只動 `app/bdd/` 測試層，不動 `app/mobile/` 產品碼。

**Shared Must Read**:
- `specs/truth/features/ios-app/dsl.md` -> 共通實作約定與共用 Given／When／Then
- `specs/truth/features/ios-app/帳號與登入/dsl.md`、`推播/dsl.md`、`工務單/dsl.md` -> 全部 Given／When／Then
- `app/bdd/src/test/java/com/athena/app/bdd/AppiumWorld.java` -> `byTestId()`、`waitForTestId()`、`waitForLabelContaining()`、T300 新增的後端通道
- `app/bdd/src/test/java/com/athena/app/bdd/steps/push/`（F3 真實作，唯一可參考的完成範本）
- `specs/plans/001-staff-app-increment/ui/ui-plan.md:230-276` -> accessibility id 命名規則與逐項 id 表
- `specs/plans/001-staff-app-increment/research.md:74-81` -> 兩平台 locator 不同、不得用 `Key`／`ValueKey` 當測試標的
- `specs/truth/contracts/components/schemas/員工帳號與登入.yaml`、`通知中心.yaml`、`工務單.yaml` -> Then 查後端權威狀態時的欄位名（不得自創）

**Boundary**:
- 一個 task 負責「一個模組的一種 step 類型」，task 內逐句列出；只改該些句的 stepdef／直接依賴的 helper。
- 不寫 `app/mobile/` 產品碼（畫面行為留給 Phase 3N–3X 的 `[BDD-GREEN]`）。
- `通知中心「{分組}」分組依序包含 {標題清單}`、`工務待認領依序為 {單號清單}` 這類帶清單參數的句，要比照既有 `待認領列表依序為 (.+)` 用正則註記，不能讓 Cucumber 把中文清單切成 `{int}` 參數（dry-run 產生的 snippet 正是這樣切錯的）。
- T306–T308 依賴 T299、T300、T301、T302；T309–T311 依賴 T299、T300、T303、T305；T312–T314 依賴 T299、T300、T303、T305。
- review 啟動 subagent；通過前不解鎖 Phase 3N–3X。

**Parallel Hint**:
- T306–T314 各派一個獨立 subagent；T315 等全部回來再啟動 subagent 來 review。

- [X] T306 [P] [BDD-RED] 帳號與登入 Given 8 句
  - `這支手機已記住 "{員工}" 的帳號`、`這支手機是共用手機，沒有記住任何帳號`、`"{員工}" 的員工編號密碼為 "{員工編號}" / "{密碼}"`、`"{員工}" 已在 App 登入，目前手機離線`、`"{員工}" 已在 App 登入，名下有進行中的任務 "{任務}"`、`"{員工}" 已用臨時密碼登入，正在強制改密畫面`、`"{員工}" 已連續輸入錯誤密碼 {次數} 次`、`"{員工}" 的帳號是臨時密碼`
  - Read: `app/bdd/.../steps/account/AccountGivenSteps.java`（T302 骨架）；`specs/truth/contracts/components/schemas/員工帳號與登入.yaml`；後端 `staff_accounts.must_change_password`／`failed_login_count`／`locked_until` 三欄由 T240 建立、登入語意由 T287 實作，Given 經後端準備這三欄的值，不在 App 端假造
  - 註：斜線句型 `"{員工編號}" / "{密碼}"` 的 Cucumber expression 要寫成 `\\/`（Cucumber 把 `/` 當替代字元分隔符）

- [X] T307 [P] [BDD-RED] 帳號與登入 When 4 句
  - `"{員工}" 在記住帳號畫面輸入密碼 "{密碼}" 並登入`、`"{員工}" 在共用手機畫面輸入員工編號 "{員工編號}" 密碼 "{密碼}" 並登入`、`"{員工}" 設定新密碼 "{新密碼}" 並確認`、`"{員工}" 在「我的」點登出`
  - Read: `app/bdd/.../steps/account/AccountWhenSteps.java`（T302 骨架）、`app/mobile/lib/features/login/login_screen.dart`（既有欄位與送出鈕的 id）

- [X] T308 [P] [BDD-RED] 帳號與登入 Then 7 句
  - `登入畫面顯示 "{員工}" 的身分卡，只有密碼欄位`、`登入畫面顯示員工編號與密碼兩個欄位`、`登入畫面顯示錯誤 "{提示}"`、`登入畫面顯示帳號已鎖定，剩餘 {分鐘} 分鐘`、`App 進入強制改密畫面，完成前無法使用其他畫面`、`"{員工}" 登入成功，進入首頁`、`App 顯示登出被攔住，原因為 "{原因}"`
  - Read: `app/bdd/.../steps/account/AccountThenSteps.java`（T302 骨架）；`必查` 的「後端權威狀態一致」半邊走 T300 的後端讀回通道（登入成功要確認後端 `failed_login_count` 已清零、鎖定要確認 `locked_until`）

- [X] T309 [P] [BDD-RED] 通知中心 Given 2 句
  - `"{員工}" 的通知中心已有以下通知：`、`"{員工}" 已在 App 打開通知中心`
  - Read: `specs/truth/features/ios-app/推播/dsl.md`（通知中心段落）、`app/bdd/.../steps/push/PushGivenSteps.java`（T303 分段）；通知資料經後端 `notifications` 表（T241 建表、T289 實作 `listNotifications`）準備，不在 App 端假造

- [X] T310 [P] [BDD-RED] 通知中心 When 3 句
  - `"{員工}" 在 App 打開通知中心`、`"{員工}" 在通知中心點選 "{標題}"`、`"{員工}" 在通知中心點「全部標為已讀」`
  - Read: `app/bdd/.../steps/push/PushWhenSteps.java`（T303 分段）、`specs/truth/contracts/groups/知識與通知/通知中心.yaml`（`markNotificationRead`／`markAllNotificationsRead`，Then 查權威狀態用）

- [X] T311 [P] [BDD-RED] 通知中心 Then 4 句
  - `通知中心「{分組}」分組依序包含 {標題清單}`、`"{標題}" 的通知顯示未讀藍點`、`"{標題}" 的通知沒有未讀藍點`、`通知中心顯示空狀態 "{文案}"`
  - Read: `app/bdd/.../steps/push/PushThenSteps.java`（T303 分段）、`specs/plans/001-staff-app-increment/ui/ui-plan.md:274`（`notification.item.{id}.unread-dot`、`notification.mark-all-read-button`）
  - 註：`分組依序包含 {標題清單}` 要用正則註記（見 Boundary），未讀藍點以 id 的可見性驗、不要求畫面有「未讀」文字（ui-plan §5 #9 的驗法）

- [X] T312 [P] [BDD-RED] 工務單模組 Given 2 句
  - `"{工務}" 手上有 {張數} 張維修中的工務單，今天已完修 {已完修張數} 張，其中 {急件數} 張是急件`、`"{工務}" 已在 App 打開工務待認領`
  - Read: `app/bdd/.../steps/repair/RepairGivenSteps.java`（T303 分段）；資料經後端 `repair_tickets` 準備（對應後端 T262 同名 Given 的既有建法，欄位與 enum 值沿用，不自創）

- [X] T313 [P] [BDD-RED] 工務單模組 When 7 句
  - `"{工務}" 在 App 打開工務首頁`、`"{工務}" 在 App 打開工務待認領`、`"{工務}" 點選工務待認領分類 "{來源}"`、`"{工務}" 在 App 認領 "{單號}"`、`"{工務}" 在工務單詳情點「放回待認領」`、`"{工務}" 在工務單詳情完修，回報結果 "{結果}"，附 {張數} 張照片`、`"{工務}" 在待認領點「開工單」，填寫以下資訊並送出：`
  - Read: `app/bdd/.../steps/repair/RepairWhenSteps.java`（T303 分段）、`specs/truth/contracts/groups/現場作業/工務單.yaml`（`releaseRepairTicketToPool`）、`specs/truth/contracts/components/schemas/任務.yaml`（`CompleteTaskRequest.resolutionType` 的 `fixed`／`temporary`／`cannot_fix`）、`specs/truth/contracts/components/schemas/工務單.yaml`（`CreateRepairTicketRequest` 的 `roomId`／`isPublicArea`／`assigneeId`）

- [X] T314 [P] [BDD-RED] 工務單模組 Then 12 句
  - 工務視角 9 句：`工務首頁顯示手上 {張數} 張、今天已完修 {已完修張數} 張、急件 {急件數} 張`、`工務待認領中 "{單號}" 的來源標示為 "{來源}"`、`工務待認領依序為 {單號清單}`、`工務認領撞單，畫面顯示「已被 "{員工}" 認領」`、`工務單詳情沒有「放回待認領」按鈕`、`"{單號}" 回到工務待認領，不再指派給任何人`、`工務單詳情顯示已完修，結果為 "{結果}"`、`工務單詳情的步驟條依序為 "{步驟清單}"，目前在 "{目前步驟}"`、`App 停在新建工務單的詳情`
  - 房務端生命週期 3 句：`工務單詳情顯示退回原因 "{原因}"`、`工務單詳情顯示撤回按鈕`、`工務單詳情顯示「補資料再送出」按鈕`
  - Read: `app/bdd/.../steps/repair/RepairThenSteps.java`（T303 分段）、`specs/truth/features/ios-app/工務單/dsl.md`（每列的 `engPool.*`／`ticket.*` id）
  - 註：`工務待認領依序為 {單號清單}` 要用正則註記（見 Boundary）

- [X] T315 subagent review (phase quality gate)
  - 驗收：`JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -f app/bdd/pom.xml test -Dtest=RunTruthFeaturesTest -Dcucumber.execution.dry-run=true` 回報 **108 scenarios、0 undefined step**（本輪起點是 30 個 undefined errors）。有 issues 就修正再 review，直到沒有任何問題。

## Phase 3N: ADD Feature File - ios-app/帳號與登入/登入與登出守衛.feature

**Goal**: 實作登入畫面兩種模式（記住帳號／共用手機）、錯誤文案與剩餘次數、連錯 5 次鎖定剩餘時間、臨時密碼強制改密守衛、以及登出的兩個攔阻（離線、名下有進行中的任務），讓 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/ios-app/帳號與登入/登入與登出守衛.feature` -> 整檔（9 Rule／10 場景）
- `specs/truth/features/ios-app/帳號與登入/dsl.md` -> 全部 19 列
- `specs/plans/001-staff-app-increment/ui/ui-plan.md:159-166`（登入畫面）、`:150-157`（我的，登出入口與攔阻）
- `specs/truth/contracts/components/schemas/員工帳號與登入.yaml`、`specs/truth/contracts/groups/營運管理/員工帳號與登入.yaml` -> `staffAccountsLogin`（401／403／429 路徑與 `mustChangePassword`）
- `app/mobile/lib/features/login/login_screen.dart`、`app/mobile/lib/features/me/`（T305 骨架）、`app/mobile/lib/core/api_client.dart`（T299）

**Boundary**:
- 鎖定剩餘時間、失敗次數、臨時密碼旗標一律以後端回應為準（後端 T287 已實作），App 不自己算鎖定時間、不在本機累計失敗次數。
- 「我的」頁只做到登出入口與兩個攔阻 Alert；推播設定／文字大小／語言／個人資料／遺失物無 feature 承接，不實作（見上游缺口）。
- 離線登出攔阻讀 T301 的離線注入開關，不另做網路偵測機制。

**Test Scope**:
- `specs/truth/features/ios-app/帳號與登入/登入與登出守衛.feature`

- [ ] T316 [BDD-GREEN] 讓 Test Scope 全綠（`app/bdd/run-module.sh 帳號與登入`，10 個場景全綠）
  - 現況 9/10 綠（2026-11-05）：唯一紅燈是行 59「設定新密碼後進入首頁」——契約無「設定新密碼」端點（`員工帳號與登入.yaml` 只有 `staffAccountsLogin`），且後端 `BearerTokenAuthFilter` 在 `must_change_password` 清掉前對 `/me/today` 一律 403，App 邊界內無合法路徑轉綠。見上游缺口 #14；端點補齊後用既有 stepdef 即可收綠，勿假綠。
- [X] T317 [BDD-REFACTOR] 在綠燈下整理登入／改密／登出三個流程共用的錯誤呈現與守衛判斷，清掉 `login_screen.dart` 的過時註解

## Phase 3O: ADD Feature File - ios-app/推播/通知中心.feature

**Goal**: 實作通知中心畫面——今天／更早分組、未讀藍點、點選導頁並轉已讀、全部標為已讀、空狀態，讓 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/ios-app/推播/通知中心.feature` -> 整檔（6 Rule／6 場景）
- `specs/truth/features/ios-app/推播/dsl.md` -> 通知中心段落全部列（含 `App 停在 "{畫面}"` 的去處對照）
- `specs/plans/001-staff-app-increment/ui/ui-plan.md:141-148`（通知中心畫面）、`:274`（`notification.item.{id}.unread-dot`、`notification.mark-all-read-button`）
- `specs/truth/contracts/groups/知識與通知/通知中心.yaml` -> `listNotifications`／`markNotificationRead`／`markAllNotificationsRead`（後端 T289 已實作）
- `app/mobile/lib/features/notification/`（T305 骨架）、`app/mobile/lib/core/push_gateway.dart`（F3 既有的 `dest`／`room`／`highlight` 導頁約定，通知中心點選要走同一套去處解析）

**Boundary**:
- 分組（今天／更早）在 App 端依通知的 `createdAt` 與裝置時間切，後端只回單一列表（後端 T290 已記錄「分組屬 App 端」）。
- 點選通知的導頁重用 `push_gateway.dart` 既有的去處解析，不另寫第二套對照。
- 不動 F3（`點開推播的去處.feature`）的既有實作與 `run-f3.sh`。

**Test Scope**:
- `specs/truth/features/ios-app/推播/通知中心.feature`

- [X] T318 [BDD-GREEN] 讓 Test Scope 全綠（`app/bdd/run-module.sh 推播`，18 個場景全綠＝通知中心 6＋F3 既有 12）
- [X] T319 [BDD-REFACTOR] 在綠燈下整理通知列表項與去處解析的共用邏輯，確認 F3 的 12 個場景沒有因重構轉紅

## Phase 3P: ADD Feature File - ios-app/工務單/工務視角.feature

**Goal**: 實作工務視角——工務首頁彙總（手上張數／今天已完修／急件數）、工務待認領的來源分流與來源標示、認領撞單、放回待認領（限公共區域）、完修三選一回報、工務自開工單、工務單詳情四步步驟條與驗收人，讓 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/ios-app/工務單/工務視角.feature` -> 整檔（11 Rule／13 場景）
- `specs/truth/features/ios-app/工務單/dsl.md` -> 工務視角相關全部列（含 `engHome.*`／`engPool.*`／`ticket.*` id）
- `specs/plans/001-staff-app-increment/ui/ui-plan.md:177-184`（工務視角畫面）、`:11`（部門切換不是獨立產品）
- `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `listDeptPool`（`urgent`／`source` 參數）、`getMyToday`（`engineeringSummary`）；`groups/現場作業/工務單.yaml` -> `releaseRepairTicketToPool`、`verifyRepairTicket`；`components/schemas/任務.yaml` -> `CompleteTaskRequest.resolutionType`（後端 T291／T297 已實作）
- `app/mobile/lib/features/engineering/`、`features/ticket/`（T305 骨架）、`app/mobile/lib/core/api_client.dart`（T299）

**Boundary**:
- 分流與排序一律用後端 `listDeptPool` 的回應順序與篩選參數，App 不在本機重排、不在本機過濾。
- 完修三選一只呼叫 `tasksComplete` 的 `resolutionType`；追蹤單自動建立與房況轉停用待確認都是後端行為（T291 已實作），App 只呈現結果。
- 放回待認領按鈕的有無依後端回應的公共區域旗標決定；客房單的 409 `room_ticket_not_requeueable` 不該被使用者看到（按鈕直接不出現，比照 ui-plan:227 撤回按鈕的同一原則）。
- 「主管驗收通過後顯示驗收人」只讀 `verifiedBy`／`verifiedAt`，App 沒有驗收動作（無主管視角）。

**Test Scope**:
- `specs/truth/features/ios-app/工務單/工務視角.feature`

- [ ] T320 [BDD-GREEN] 讓 Test Scope 全綠（13 個場景）
  - 現況 5/13 綠（2026-11-06，連兩輪結果一致非 flaky）：首頁彙總、急件／櫃檯分流、來源標示、待認領開單已綠；其餘 8 個場景被上游缺口 #10（7 個）與 #11（1 個）阻塞，端點／Given 補齊後以既有實作續推。
- [ ] T321 [BDD-REFACTOR] 在綠燈下整理工務／房務兩個視角共用的待認領單卡與工務單詳情元件，避免兩套平行實作
  - 暫緩（2026-11-06）：T320 僅 5/13 綠，重構缺乏綠燈保護（`refactor-綠燈保護重構判準` Rule 1）。待缺口 #10／#11 解除、T320 收全綠後再做；屆時連同 T323 一起處理共用元件。

## Phase 3Q: ADD Feature File - ios-app/工務單（房務端三支）

**Goal**: 實作房務端報修全程——照片浮水印、張數上下限與按鈕禁用、單張上傳失敗重試、離線暫存與恢復連線後的冪等重送、開單提示（重複報修、系統判定急件）、工務單詳情的狀態摘要卡與撤回／退回／補資料再送出，讓 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/ios-app/工務單/報修照片與送出.feature`（6 Rule／6 場景）、`開單提示.feature`（2 Rule／3 場景）、`工務單房務端生命週期.feature`（5 Rule／9 場景）
- `specs/truth/features/ios-app/工務單/dsl.md` -> 房務端相關全部列
- `specs/plans/001-staff-app-increment/ui/ui-plan.md:114-131`（報修面板與工務單詳情）、`:267-272`（`repair.photo.*`／`repair.duplicate-ticket.*`／`repair.urgent-judgement.*`／`ticket.summary.offline-queued-label`）
- `specs/plans/001-staff-app-increment/research.md:104`（浮水印若燒進 pixel，Appium 讀不到——必須額外渲染一個帶同樣字串且掛 id 的可及節點）、`:105`（壓縮用 `flutter_image_compress`，浮水印丟 `compute()`／isolate）
- `specs/truth/contracts/components/schemas/工務單.yaml` -> `CreateRepairTicketRequest`（無 `urgent`）、`RepairTicket.urgentJudgement`／`urgentDowngradeReason`／`rejectReason`；`groups/現場作業/工務單.yaml` -> `withdrawRepairTicket`、`resubmitRepairTicket`（後端 T236／T293／T295 已實作）
- `specs/truth/techstack.md:44-46` -> `Idempotency-Key` ＋ `processed_ops` 的冪等約定
- `app/mobile/lib/core/test_backdoor.dart`（`/clock` 注裝置時間、`/offline` 強制離線、`/upload-fail` 強制第 N 張失敗）

**Boundary**:
- 急件判定卡只呈現後端回的 `urgentJudgement`（judgement／rule），畫面明示「房務不能更改」；App 不送 `urgent` 欄位、不自行判定。
- 照片張數限制以「送出鈕禁用＋欄位標題提示」表達（ui-plan §5 #14 已裁決為等效判定），不做事後 toast。
- 離線重送走 `Idempotency-Key`：同一張工務單的重送必須攜帶同一個 key，`後端只有 {張數} 張 ... 工務單` 這句由 T300 的後端讀回通道驗。
- 浮水印字串必須掛在可及節點上（`repair.photo.{index}.watermark-label`），不得只畫進圖片像素。

**Test Scope**:
- `specs/truth/features/ios-app/工務單/報修照片與送出.feature`
- `specs/truth/features/ios-app/工務單/開單提示.feature`
- `specs/truth/features/ios-app/工務單/工務單房務端生命週期.feature`

- [ ] T322 [BDD-GREEN] 讓 Test Scope 全綠（18 個場景）
  - 現況 0/18 綠（2026-11-06）：三支 feature 都卡在同一個入口缺口——房務的受測任務是「進行中」（doing），而 `/me/today` 的 `nextTasks` 只收 accepted、`/pool` 只回 pool，實跑對該情境的員工打這兩個端點都查不到那張單，App 沒有任何資料來源可以把人導到任務頁，報修面板（6＋3 個場景）因此進不去；工務單房務端生命週期（9 個場景）走同一條任務頁入口，後面 `listRepairTickets?roomId=` 後端也還回 501。端點補齊後以既有實作續推。
- [ ] T323 [BDD-REFACTOR] 在綠燈下整理照片上傳佇列（壓縮、浮水印、單張重試、離線重送）的共用邏輯與 Phase 3P 共用的工務單詳情元件
  - 暫緩（2026-11-06）：T322 0/18，無綠燈保護可重構。待缺口 #12／#13 解除、T322 收綠後，連同 T321 一起處理工務／房務共用元件。

## Phase 3R: ADD Feature File - ios-app/待認領/待認領列表.feature

**Goal**: 實作房務待認領列表——後端排序、同樓層標示、單卡資訊（來源／原因／接手進度）、分類標籤與筆數、分類篩選、未接超時兩段標籤，讓 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/ios-app/待認領/待認領列表.feature` -> 整檔（8 Rule／9 場景）
- `specs/truth/features/ios-app/待認領/dsl.md` -> 全部 13 列
- `specs/plans/001-staff-app-increment/ui/ui-plan.md:78-86`（待認領畫面）、`:254-259`（`pool.card.*`／`pool.category.*`／未接超時兩段標籤 id）
- `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `listDeptPool`（後端 T220 排序、T209 提醒狀態已實作）
- `app/mobile/lib/features/pool/pool_screen.dart`（既有畫面，本輪要接上 T299 的 API client）

**Boundary**:
- 列表順序與分類筆數一律用後端回應，App 不重排、不重算筆數。
- 未接超時標籤只有「已提醒同仁」「已通知主管」兩段（裁決 2），不做橫幅、不做徽章、不做 L 層。
- 公共區域單卡與「部門沒有在職員工」空狀態的視覺規格缺件，相關場景的處理見下方上游缺口。

**Test Scope**:
- `specs/truth/features/ios-app/待認領/待認領列表.feature`

- [ ] T324 [BDD-GREEN] 讓 Test Scope 全綠（9 個場景）
  - 現況 6/9 綠（2026-11-06）：後端排序、同樓層標示、退回單卡（來源／原因／接手進度）、分類筆數、分類篩選、公共區域規則單都已綠。其餘 3 個場景被上游缺口阻塞：未接超時兩段標籤（2 個）卡在缺口 #15（`GET /pool` 不回 `reminderStage`），部門全員停用空狀態（1 個）卡在缺口 #16（主管無部門池可看、後端也沒有「沒有在職員工」的訊號）。端點補齊後以既有實作與既有 stepdef 續推。
- [X] T325 [BDD-REFACTOR] 在綠燈下整理池內單卡與分類磚元件，與 Phase 3P 的工務待認領共用同一組元件

## Phase 3S: ADD Feature File - ios-app/任務執行（任務頁核心互動＋任務卡狀態與提示）

**Goal**: 實作任務頁核心互動——打掃四段／備品三段步驟條、一狀態一主按鈕、SOP 勾選與跳過、超時 N 分純提示、完成慶祝頁；以及一次一間提示、中斷停住時間、完成·免查房 chip，讓 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/ios-app/任務執行/任務頁核心互動.feature`（7 Rule／9 場景）、`任務卡狀態與提示.feature`（3 Rule／3 場景）
- `specs/truth/features/ios-app/任務執行/dsl.md` -> 全部 17 列
- `specs/plans/001-staff-app-increment/ui/ui-plan.md:88-104`（任務頁與完成慶祝）、`:247-251`（`task.busy-alert.*`／`task.card.paused-elapsed`／`task.status-chip--inspected-waived`）、`:273`（`task.status-chip` 的 `--state` 修飾）
- `specs/truth/features/backend/dsl.md` -> 狀態用語對照表（App 的 chip 文字與它一致）
- `app/mobile/lib/features/task/task_screen.dart`、`features/celebrate/`（T305 骨架）

**Boundary**:
- 「超時 N 分」是純畫面提示，不觸發任何推播（feature 有一句專門斷言後端沒送推播）；超時基準是預估工時，不是已廢除的 `due_at`。
- 計時與已做分鐘以後端回的任務狀態與時間為準，裝置時間由 T301 的 `/clock` 注入；App 不自己持久化計時。
- 「一次一間」的拒絕由後端 409 決定，App 只呈現 Alert 並維持另一間為待開始。

**Test Scope**:
- `specs/truth/features/ios-app/任務執行/任務頁核心互動.feature`
- `specs/truth/features/ios-app/任務執行/任務卡狀態與提示.feature`

- [ ] T326 [BDD-GREEN] 讓 Test Scope 全綠（12 個場景）
  - 現況 3/12 綠（2026-11-06 重新計數，T328 的在 App 內按開始路徑上線後）：綠的是「一次一間提示」（`任務卡狀態與提示.feature:11`）、「308 待開始時主按鈕為開始」與「305 進行中時主按鈕為完成」（`任務頁核心互動.feature` 場景大綱前兩列）。最後那一列是本次新轉綠的——場景沒有指定已做分鐘，任務種成待開始後可以在 App 內按開始讓後端翻成 `doing`，不必靠首頁列出進行中的單。
  - 其餘 9 紅的歸因：6 個卡在「等不到 `home.task.{房號}.card`」——受測任務在 Given 就被種成 `doing`＋已做分鐘／`interrupted`＋停住原因／`done`／`inspected`，這些值只能由測試資料落地，走不了在 App 內開始的路，而 `GET /me/today` 的 `nextTasks` 只收 `accepted`，首頁沒有卡可點（缺口 #12；「超時 5 分」與「完成慶祝頁」兩個另外還卡著缺口 #17 的 `estMin`）。另外 3 個是 stepdef 仍為 `PendingException`：「步驟條停在進行中」現在進得了任務頁，紅在步驟條本身還沒做；兩個 SOP 場景則是 App 取不到 SOP 項名稱（缺口 #18），`ui-plan.md` 也還沒替 SOP 列與跳過鈕命名 accessibility id。
- [ ] T327 [BDD-REFACTOR] 在綠燈下整理步驟條、主按鈕與狀態 chip 的狀態映射，收斂成單一狀態→呈現對照表
  - 暫緩（2026-11-06）：T326 僅 2/12 綠，狀態映射的多數分支（進行中／已中斷／免查房）根本沒被綠燈覆蓋過，重構缺乏保護（`refactor-綠燈保護重構判準` Rule 1）。待缺口 #12／#17／#18 解除、T326 收綠後再做。

## Phase 3T: ADD Feature File - ios-app/任務執行/延後與中斷原因面板.feature

**Goal**: 實作延後／中斷原因面板——預設原因清單、選後直接送出、「其他原因」60 字以內可送出、超過 60 字送出鈕禁用並即時顯示字數，讓 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/ios-app/任務執行/延後與中斷原因面板.feature` -> 整檔（4 Rule／9 場景）
- `specs/truth/features/ios-app/任務執行/dsl.md` -> 延後／中斷相關列（含 `延後面板的送出鈕為 "{按鈕狀態}"`、`延後面板顯示字數 "{字數文字}"`）
- `specs/plans/001-staff-app-increment/ui/ui-plan.md:106-112`（延後／中斷面板，兩組預設原因逐字清單）、`:226`（60 字上限即時提示）
- `specs/plans/001-staff-app-increment/research.md:76`（`TmsSemantics` 必須顯式曝露 `enabled`，`onPressed: null` 的 Cupertino 按鈕在 Appium 端讀起來永遠是可用的）
- `app/mobile/lib/core/tms_semantics.dart`、`app/mobile/lib/features/task/`（`holdSheet` 落點由 T305 建立）

**Boundary**:
- 兩組預設原因文字以 ui-plan:110 的清單為準，不自行增刪。
- 送出鈕禁用狀態必須經 `TmsSemantics` 曝露，Appium 讀得到；不得只靠視覺灰階。
- 原因與說明的落地語意（`hold_reason`／`deferred_by_self`）是後端既有行為，本 phase 不改後端。

**Test Scope**:
- `specs/truth/features/ios-app/任務執行/延後與中斷原因面板.feature`

- [X] T328 [BDD-GREEN] 讓 Test Scope 全綠（9 個場景）
  - 9/9 綠（2026-11-06，`Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`）。中斷那 4 個場景的 Given 要求「進行中」，首頁列不出進行中的單（缺口 #12），改走合法路徑：任務種成待開始 → 從首頁進任務頁 → 在 App 按「開始」讓後端自己翻成 `doing`，再開中斷面板。這條路只在場景沒有指定「已做分鐘」「停住原因」時啟用（那兩個值只能由測試資料落地），所以 T326 那些帶已做分鐘的場景仍然卡在 #12，但不帶那兩欄的場景現在有路可走，下次重測 T326 要重新計數。
  - 實跑打出兩個 App 側真缺陷：(1) `setState(() => _taskFuture = Future…)` 的箭頭函式把 Future 當回傳值交給 `setState`，Flutter 在 debug 版拒絕這個回呼，送出後畫面一次也不會更新（`_start` 也有同一個寫法，一併改成區塊寫法）；(2) 面板貼在畫面最下緣，鍵盤升起時整個蓋住說明欄與送出鈕，中文說明根本貼不進去。
- [X] T329 [BDD-REFACTOR] 在綠燈下把延後與中斷兩個面板收斂成同一個元件（差異只在預設原因清單與送出端點）
  - 元件本身 T328 就已收斂成單一 `HoldSheet`＋`HoldSheetMode`，本輪只把散在三處的分支收到同一個地方：新增 `HoldSheetModeCopy` extension，標題、預設原因清單、送出端點各一個 `switch`（`app/mobile/lib/features/task/hold_sheet.dart:22-56`），面板本體不再逐處判斷 mode（`:116` 送出、`:147` 標題、`:121` 選項清單）。順手移除只有自己用的公開靜態 `HoldSheet.presetReasons`。行為零改動。
  - 驗收：`flutter analyze` 無 error（3 則既有 `use_null_aware_elements` info，都在 `api_client.dart`）、`flutter test` 4/4；重構前後三個基準逐場景一致——`任務執行` 模組 `Tests run: 19, Failures: 6, Errors: 3, Skipped: 0`（延後與中斷原因面板 9/9 綠，T326 的 12 個場景 3 綠 9 紅，紅的身分與歸因不變）、`推播` 模組 `Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`。

## Phase 3U: ADD Feature File - ios-app/今日進度/首頁今日進度.feature

**Goal**: 實作首頁今日進度——四格數值、待開始的單依推薦順序列出、連續零退回天數，讓 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/ios-app/今日進度/首頁今日進度.feature` -> 整檔（3 Rule／3 場景）
- `specs/truth/features/ios-app/今日進度/dsl.md` -> 全部 3 列
- `specs/plans/001-staff-app-increment/ui/ui-plan.md:69-76`（首頁）、`:243-245`（`home.progress.*` 四格與 `home.streak-badge`）
- `specs/truth/contracts/components/schemas/員工工作台.yaml` -> `MyToday`（含 `inspectedCount`，後端 T238 已實作）
- `app/mobile/lib/features/home/home_screen.dart`、`app/mobile/lib/core/api_client.dart`（T299）

**Boundary**:
- 四格數值與待開始順序全部用 `GET /me/today` 的回應，App 不自己算、不自己排。
- 第四格是「查房通過」（`inspectedCount`，裁決已落地於後端 T238），不是已廢除的「今天完成單數」。
- 連續零退回天數的畫面節點（`home.streak-badge`）在 ui-plan 標為「待補畫面」，本 phase 依 feature 的 Rule 掛上這個 id 實作最薄呈現；視覺規格缺件見上游缺口。

**Test Scope**:
- `specs/truth/features/ios-app/今日進度/首頁今日進度.feature`

- [X] T330 [BDD-GREEN] 讓 Test Scope 全綠（3 個場景）
  - 3/3 綠（2026-11-06，`Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`）。四格與待開始順序全部讀 `GET /me/today`，App 只負責畫：第四格掛 `inspectedCount`，待開始的卡沿用首頁既有的 `home.task.{房號}.card`（它本來就是 `nextTasks` 照後端順序畫出來的）。`home.streak-badge` 依 feature 的 Rule 以同一套格子做最薄呈現（上游缺口 #1）。每一條斷言都先比後端回應、再比畫面，避免「App 自己湊出正確數字」也算過。
  - 產品碼只動 `app/mobile/lib/features/home/home_screen.dart`：四格＋連續零退回天數，抽一個 `_ProgressCell`。實跑打出一個真行為：把格子名稱和值放進同一個 `TmsSemantics` 時，節點的 label 會變成「顯式 label ＋子節點文字」兩段（讀到 `2 / 7\n打掃完成／總數 2 / 7`），所以名稱改放在 id 節點外面當兄弟節點，掛 id 的節點只包住值。
  - 測試層修正三筆，都是 fixture 表達不出背景狀態的真缺陷：(1) `BackendChannel.createTask` 不支援「已取消」「已延後」，補走正式端點的 `cancel`（原因 `manager_void`）與 `defer`；(2)「查房通過」與「完成 · 免查房」原本都只落 `state=inspected`，但今日進度的「查房通過」那格只數 `inspection='passed'`，補成兩條路（免查房照正式流程先鎖 `inspection_waived`，由產品碼自己落成 waived）；(3)「名下有以下任務」的表新增吃「類別／預估分鐘／重做次數」三欄（`tasks.type`／`est_min`／`redo_count`），否則臨時需求、剩餘工時、推薦順序都沒有真相可算。另外 `seedTasks` 把「終點就是進行中」的那筆挪到最後才建——後端「一次只做一間」會讓排在它後面、需要經過開工的狀態拿到 409 `doing_task_exists`，而表上的順序本來就只是在描述一組狀態。
  - 回歸：`run-module.sh 推播` `Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`；`run-module.sh 任務執行` `Tests run: 19, Failures: 6, Errors: 3, Skipped: 0`，與 T329 基準逐場景一致（延後與中斷原因面板 9/9 綠，紅的 9 個仍是 T326 那批，身分與歸因不變）。
- [ ] T331 [BDD-REFACTOR] 在綠燈下整理進度環與今日進度四格的資料繫結，與 Phase 3P 的工務首頁彙總共用同一個 `MyToday` 取用路徑

## Phase 3V: ADD Feature File - ios-app/房間交辦（兩支）

**Goal**: 實作任務頁交辦區（來源／作者／時間／內容、未讀標示、開始後轉已讀）與查房退回資訊（逐欄顯示、換人原因不另列主管交代），讓 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/ios-app/房間交辦/任務頁交辦區.feature`（3 Rule／3 場景）、`查房退回資訊.feature`（2 Rule／2 場景）
- `specs/truth/features/ios-app/房間交辦/dsl.md` -> 全部 7 列
- `specs/plans/001-staff-app-increment/ui/ui-plan.md:263-266`（`task.handover.*`／`task.status-row.return-info`）、`:58`（未讀以 id 的狀態／可見性驗，不要求畫面有「未讀」文字）
- `specs/truth/features/backend/房間交辦/dsl.md` -> Then `"{任務}" 的退回資訊逐欄顯示如下：`（後端 T223 已對齊逐欄語意，App 的逐欄驗法與它同一口徑）
- `app/mobile/lib/features/task/task_screen.dart`

**Boundary**:
- 退回資訊逐欄顯示（標題／原因／內文三段分別驗），不是一句全文比對；不合格項帶 SOP 項全名。
- 未讀以 `task.handover.unread-dot` 的可見性驗，不加「未讀」文字。
- 換人原因不進交辦列表（後端已併入狀態列），App 不得另列一條主管交代。

**Test Scope**:
- `specs/truth/features/ios-app/房間交辦/任務頁交辦區.feature`
- `specs/truth/features/ios-app/房間交辦/查房退回資訊.feature`

- [ ] T332 [BDD-GREEN] 讓 Test Scope 全綠（5 個場景）
- [ ] T333 [BDD-REFACTOR] 在綠燈下整理交辦 callout 與狀態列退回資訊兩個區塊的共用版面元件

## Phase 3W: ADD Feature File - ios-app/查一下/查一下畫面.feature

**Goal**: 實作查一下面板——依任務情境列出推薦問題與範圍、回答下方出處卡、查不到時明說且不顯示任何回答、查不到時提供問主管，讓 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/ios-app/查一下/查一下畫面.feature` -> 整檔（4 Rule／4 場景）
- `specs/truth/features/ios-app/查一下/dsl.md` -> 全部 10 列
- `specs/plans/001-staff-app-increment/ui/ui-plan.md:133-139`（查一下面板）、`:260-262`（`ask.suggestion.{index}`／`ask.answer.source-card.*`／`ask.not-found-message`／`ask.ask-manager-button`）、`:220`（不得帶出住客身分）
- `app/mobile/lib/features/task/`（`askSheet` 落點由 T305 建立）

**Boundary**:
- 推薦問題順序與出處卡內容全部來自後端，App 不自己排序、不自己生成答案。
- 查不到時畫面不得出現任何回答區塊（不是顯示空字串）。
- 不顯示住客身分相關欄位，即使後端回應含有。

**Test Scope**:
- `specs/truth/features/ios-app/查一下/查一下畫面.feature`

- [ ] T334 [BDD-GREEN] 讓 Test Scope 全綠（4 個場景）
- [ ] T335 [BDD-REFACTOR] 在綠燈下整理推薦問題列與出處卡的清單渲染（NOOP 可接受，但要寫明判斷依據）

## Phase 3X: ADD Feature File - ios-app/系統層/離線模式與連線異常.feature

**Goal**: 實作跨畫面系統層——離線時進行中的單仍可完成、離線時開始／認領／延後／開新工務單一律被擋並顯示離線提示、離線列顯示待同步筆數、即時連線異常時顯示降級列且每 5 秒自動更新，讓 Test Scope 全綠。

**Shared Must Read**:
- `specs/truth/features/ios-app/系統層/離線模式與連線異常.feature` -> 整檔（4 Rule／7 場景）
- `specs/truth/features/ios-app/系統層/dsl.md` -> 全部 6 列
- `specs/plans/001-staff-app-increment/ui/ui-plan.md:168-175`（系統層跨畫面元件）
- `app/mobile/lib/core/test_backdoor.dart`（`/offline` 既有、`/flaky-connection` 由 T301 新增）
- `app/mobile/lib/core/api_client.dart`（T299；離線佇列與待同步筆數的落點）

**Boundary**:
- 離線可完成的只有「進行中的單」；其餘四個動作一律擋在 App 端並顯示離線提示，不送出請求。
- 待同步筆數是 App 端離線佇列的長度，恢復連線後走 `Idempotency-Key` 重送（與 Phase 3Q 共用同一個佇列實作）。
- 連線異常列的 5 秒輪詢是降級行為，不改後端 SSE 契約。

**Test Scope**:
- `specs/truth/features/ios-app/系統層/離線模式與連線異常.feature`

- [ ] T336 [BDD-GREEN] 讓 Test Scope 全綠（7 個場景）
- [ ] T337 [BDD-REFACTOR] 在綠燈下把離線佇列、離線列、連線異常列收斂成單一連線狀態來源，供全部畫面共用

## Phase 4: 整合驗收（ios-app 全量）

**Goal**: 確認 14 支新 feature 全綠之後，16 支 feature 一次跑不互相干擾，且 F3 推播金絲雀沒有因本輪改動轉紅。

**Boundary**: 只跑既有 runner 與腳本、只修本輪造成的非綠；不新增 feature、不改 truth。

- [ ] T338 全量 ios-app truth suite 真跑全綠
  - 驗收：`RunTruthFeaturesTest`（非 dry-run，走 `app/bdd/run-module.sh` 抽出的環境準備）回報 **108 scenarios 全綠、0 undefined、0 failure**。場景間的 App 狀態殘留（`AppiumWorld` 的 session 重用與 `resetAppState`）若造成偶發紅燈，修在 harness，不改 feature。
- [ ] T339 F3 推播金絲雀回歸
  - 驗收：`app/bdd/run-f3.sh` 回報 12/12 綠（冷啟動／背景／前景三種點開情境），與本輪起點一致。
- [ ] T340 subagent review (phase quality gate)
  - 驗收：T299–T339 全部 `[X]`；`flutter analyze` 在 `app/mobile/` 無 error；本輪沒有改到 `tms/`、`specs/truth/**` 與 T204–T298 的任何內容。有 issues 就修正再 review，直到沒有任何問題。

## 上游缺口（本輪不排實作任務，回交對象列在各項）

以下 20 項是本輪**不產生** `[BDD-GREEN]` 任務、或實跑後確認無法在 App 邊界內轉綠的部分。#1–#4 是原型／規格缺件（`ui/prototype-feature-coverage.md:188` 的「feature 全缺的 Rule」與 `ui/ui-plan.md:308-317` 的剩餘風險），#5–#8 是環境與硬體未到位的 blocked 項，#9–#20 是 Phase 3 實跑逐一打出來的上游缺口（回交對象與證據逐條列在該項內）：

1. **`home.streak-badge`（F1-R3 連續零退回天數）的視覺規格**：feature 有 Rule、dsl 有句型，但原型沒有這個元素，`ui-plan.md:245` 標「待補畫面後才掛」。T330 依 feature 掛上這個 id 做最薄呈現即可過綠，版面與文案細節回交 `/ui-plan`。
2. **`pool.card.common-area-label`／`pool.card.{roomNo}.rule-hit-tag`（F4-R6 公共區域與規則命中）**：`coverage:188`／`:229` 判定原型未實作（房務池全有房號、「規則命中」字樣只出現在工務單歷程），`ui-plan.md:257` 標「待原型補畫面」。T324 以 dsl 指定的 id 做最薄呈現過綠，呈現規格回交 `/ui-plan`。
3. **`pool.empty-state--no-active-staff`（F4-R8 部門沒有在職員工空狀態）**：`coverage:230` 判定原型無員工停用概念，`ui-plan.md:258` 標「待原型補畫面」。同上，文案回交 `/ui-plan`。
4. **`工務單/dsl.md:72` 的 `工務待認領分類標籤顯示如下：` 沒有任何 feature 使用**：`工務視角.feature` 的分流場景改用 `工務待認領依序為 {單號清單}` 驗。這句沒有 Test Scope 承接，本輪不排 RED 任務，回交 `/dsl-refine` 決定是補場景還是刪句。
5. **Firebase 正式接入（`firebase_messaging`）**：`research.md:103` 明訂等 Firebase 專案建立後再做，F3 現以 `xcrun simctl push` 注入真 APNs payload 驗收。blocked，不排成可執行任務；接入時 `PushGateway` 只換內部實作，不動 `dest`／`room`／`highlight` 欄位約定。
6. **Flutter spike 閘門的實機 iOS 半邊**：`research.md:102` 記錄 iOS simulator 半邊已全數通過，實機 iOS（歷史問題正是 real device 上 page source 不完整）待硬體到位後補驗。blocked；失敗時改採 React Native 的退路仍然有效。
7. **Android 端全部驗證**：`research.md:92`、`techstack.md:9` 記錄開發機 Android SDK 尚未安裝、低階 Android 實機未採購。本輪全部 Test Scope 只在 iOS simulator 驗收；Android locator 走 resource-id 的分流已在 `byTestId()` 就位，但未實測。blocked。
8. **兩筆未裁決的用語落差**：`ui-plan.md:313-315` 的 #4（`due_at`／到期概念是否以其他形式保留）與 #10（`PushPayload.title` 的括號入題 vs 獨立 chip）。目前 16 支 feature 都沒有承接這兩項的 Rule，不影響本輪綠燈；回交產品決議。另記 `ui-plan.md:5,236` 仍有 Swift 前提與 Android locator 寫成 `contentDescription` 的文件殘留（`research.md:107` 已盤點），屬文件修正，回交 `/ui-plan`。
9. **`V16__notifications.sql` 漏 GRANT（T318 實跑發現，2026-11-05）**：`notifications` 表沒有 `GRANT ... TO tms_app`，執行期 `GET /notifications` 500 permission denied（後端 BDD 以超級使用者跑，抓不到）。T318 已對跑著的 DB 手動 GRANT 解鎖；`tms/` 需補一支 GRANT migration，回交後端。
10. **工務「我手上的工務單」無端點可列舉（T320 實跑發現，2026-11-06）**：`工務視角.feature` 有 7 個場景要從工務首頁點進維修中／已完修的工務單詳情，但現有端點都到不了——`/pool` 只回 `state=pool`，`/me/today` 的 `nextTasks` 只收 `accepted`（`TaskExecutionService:498`），契約的 `listRepairTickets` 是「依房間列出同房工務單」（必帶 `roomId`，非本人視角）且後端回 501，`listTasks` 實際掛在 `/desk` 僅限櫃檯／主管。回交 `/api-plan`：`MyToday.engineeringSummary` 補手上單清單，或新增 `GET /repair-tickets?assignee=me`（含 doing／今日 done）。
11. **撞單場景的 Given 順序產生不出被測狀態（T320 實跑發現，2026-11-06）**：`工務視角.feature` 的「小李剛接走」場景，Given 先把單建成「已接單／小李」才打開待認領，該單此時已不在池中、依規格本就不該顯示，無法表達 Rule 要的「列表過期」語意。回交 `/dsl-refine`：Given 應改為先進池、開池後才被他人接走。
12. **App 無端點可列舉「進行中（doing）」的任務（T322 實跑發現，2026-11-06）**：房務端三支 feature 的受測任務都是 doing，但 `MyToday.nextTasks` 只收 `accepted`（`tms/.../TaskExecutionService.java:498` 逐字確認），`/pool` 只回 `state=pool`，因此 App 進不了任務頁，報修面板與工務單詳情全部到不了——**擋住 Phase 3Q 全部 18 個場景**。ui-plan 畫面 1 的「繼續處理」本來就需要這份清單。回交 `/api-plan`：`MyToday` 補進行中的單。與缺口 #10 同一族（App 看不見自己手上的工作）。
13. **`listRepairTickets` 後端未實作（T322 實跑發現，2026-11-06）**：`GET /api/v1/repair-tickets?roomId=` 回 501（`tms/.../repair/RepairController.java:118` 明寫佔位），且契約無「我報的工務單」清單端點；即使 #12 解除，房務端仍進不了工務單詳情。回交後端實作＋`/api-plan` 評估是否補本人視角清單。
14. **「設定新密碼」端點缺席（T316 實跑發現，2026-11-05）**：`登入與登出守衛.feature:59` 要求設定新密碼後進入首頁，但 `員工帳號與登入.yaml` 只有 `staffAccountsLogin`，無改密端點；後端在 `must_change_password` 清掉前對業務端點一律 403。回交 `/api-plan` 補「設定新密碼」端點（重雜湊密碼＋清 `must_change_password`）→ 後端實作後，T316 的最後 1 個場景用既有 stepdef 即可轉綠。在此之前 T316 收 9/10，不假綠。
15. **`GET /pool` 不回 `reminderStage`（T324 實跑發現，2026-11-06）**：契約 `員工工作台.yaml` 的 `PoolItem.reminderStage` 已定義（`staff_reminded`／`manager_notified`），`tasks.reminder_stage` 也確實落地（V13；實跑以 SQL 確認 fixture 寫入成功），但 `TaskExecutionService.deptPool`（`tms/.../TaskExecutionService.java:369`）只把它當 `unclaimed_overdue` 排序旗標用，組裝 `PoolItem` 時（:405-419）沒有放進回應，App 永遠讀到 null，未接超時兩段標籤畫不出來——**擋住 Phase 3R 的 2 個場景**。回交後端：`deptPool` 補 `item.put("reminderStage", …)`。與缺口 #10／#12 同一族（App 看不見後端已有的資料）。
16. **主管看不到任何部門待認領池，後端也沒有「部門沒有在職員工」的訊號（T324 實跑發現，2026-11-06）**：`待認領列表.feature:88` 要王主管打開房務待認領看到空狀態，但 (a) `listDeptPool` 只依登入者自己的 `dept` 分流、沒有部門查詢參數，主管的 `dept` 是 NULL，`TaskExecutionService.deptPool:357` 直接回 422「此帳號未設部門」；(b) 整個後端沒有任何地方判斷「這個部門沒有在職員工」，`GET /pool` 對這個情況只會回一般的空 `items`，App 無從分辨「沒有單」與「沒有人可以搶單」。回交 `/api-plan`：補部門參數（或主管視角的部門池端點）＋在回應中帶出部門可搶單人力的狀態。
17. **`readTasks` 回應缺 `estMin`／`skippedSopItems`／`inspectionWaived`（T326 實跑發現，2026-11-06）**：契約 `TaskDetail` 以 `allOf` 併入 `tasks`（含 `estMin`、`inspectionWaived`）並另外定義 `skippedSopItems`，但 `TaskExecutionService.toDetail`（`tms/.../TaskExecutionService.java:584-607`）逐欄組裝回應時這三欄都沒放。App 因此算不出「超時 N 分」（＝已做分鐘 − 預估分鐘）、慶祝頁印不出「預估 35 分」、SOP 進度的 `x / y` 也少了跳過的那一半——在缺口 #12 之後接著擋住 Phase 3S 的超時與慶祝頁 2 個場景。回交後端：`toDetail` 補這三欄。
18. **`GET /sop-items` 後端未實作（T326 實跑發現，2026-11-06）**：契約 `openapi.yaml:119` 有 `/sop-items`（`listSopItems`），但 `tms/` 沒有任何 controller 掛這條路由（全專案 grep `sop-items` 無結果），而 `TaskDetail.checkedSopItems` 只給 id、不給名稱，App 沒有任何方式取得「擦拭桌面與鏡面」這類 SOP 項名稱來畫清單與勾／跳過——在缺口 #12 之後接著擋住 Phase 3S 的 2 個 SOP 場景。回交後端實作 `listSopItems`。
19. **延後／中斷面板的預設原因文字兩份規格不一致，面板元素也沒有命名 accessibility id（T328 實跑發現，2026-11-06）**：`ui-plan.md:110` 寫「缺備品去拿」與「其他」，`任務執行/dsl.md` 與 `延後與中斷原因面板.feature` 寫「缺備品，去拿」與「其他原因」；原因文字同時是送到後端的 `hold_reason` 內容與測試指認選項的依據，兩份不同會讓實作與驗收各挑一份。本次以 truth feature 的寫法為準落地。另外 `ui-plan.md` 的 accessibility id 表沒有延後／中斷面板任何一列，本次自行命名 `hold.reason.{原因}`、`hold.other-reason-field`、`hold.char-count`、`hold.confirm-button`、`task.defer-button`、`task.interrupt-button`、`task.status-row.hold-reason`。回交 `/ui-plan`：統一原因文字，並把這七個 id 補進約定表。
20. **`MyToday.zeroReturnStreakDays` 後端寫死 0（T330 實跑發現，2026-11-06）**：契約要求這個欄位，`TaskExecutionService.myToday` 的 Javadoc 也明寫「連續零退回天數的計算定義尚未拍板，先固定回 0」，回應裡永遠是 0。`首頁今日進度.feature` 自己帶著 `[need clarification]`（以有出勤的工作日連續計還是日曆日？當天有一筆退回就歸零嗎？），場景只驗「首頁顯示得出這個數值且與後端一致」，所以本輪綠燈成立，但畫面上的數字目前沒有業務意義。回交 `/dsl-refine` 定義口徑 → 後端實作計算。與缺口 #1（這塊的視覺規格待補）同一處畫面。
