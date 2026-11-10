# 系統分析規劃

## 專案結構

### 文件結構（本功能）

```text
specs/plans/003-manager-sign-off-queues/
├── spec.md                  # /specify ＋ /clarify-over-specs（已完成，Ready）
├── plan.md                  # 本檔
├── truth-delta.md           # 骨架已建，由 truth owner 透過 /truth-delta 追加
├── checklists/
│   └── requirements.md      # 已回刷，Ready
├── features/
│   └── acceptance/          # /spec-by-example 產出，6 支驗收旅程（plan-side，非 truth）
├── ui/                      # Wave 1 /ui-plan 產出（plan-side，非 truth）
└── tasks.md                 # /tasks 產出

specs/truth/                 # 系統真相（由各 truth owner 更新）
├── contracts/               # Wave 2 /api-plan 更新：新增主管待辦查詢、補 readRepairTicket 可查看者、
│                            #   補 createInspections 的契約缺口（見介面 7、8）
├── data/                    # Wave 1 /data-plan 更新（預期 NOOP 或只補 tasks／repair_tickets 的 Note）
└── features/
    ├── backend/             # /dsl-refine：ADD 主管待辦場景（三類收哪些單、排除本人、跨日、排序）
    └── ios-app/             # /dsl-refine：ADD 主管首頁、三類清單、查房畫面；MODIFY 登入去處、
                             #   工務單 dsl 裁決、待認領列表、點開推播的去處、離線擋下的動作
```

### 原始碼結構（儲存庫根目錄）

```text
app/
├── mobile/                          # Flutter 員工 App
│   ├── lib/core/
│   │   ├── push_gateway.dart        # 推播去處導頁（dest 為 home 時目前不分角色）
│   │   ├── push_target.dart         # PushTarget 解析（kind、dept、highlightTaskId）
│   │   ├── api_client.dart          # 登入 session（role、dept）、各端點呼叫
│   │   └── widgets/                 # tms_category_tile、tms_home_cards、tms_segmented_control 等共用元件
│   ├── lib/features/login/          # login_screen、force_password_change_screen：目前只依 dept 分流
│   ├── lib/features/shell/          # main_shell：HousekeepingShell／EngineeringShell 四分頁外殼
│   ├── lib/features/home/           # 房務首頁（任務卡的既有來源）
│   ├── lib/features/pool/           # 房務待認領（主管目前只到得了這裡，帶 dept=housekeeping）
│   ├── lib/features/engineering/    # 工務首頁、工務待認領
│   ├── lib/features/ticket/         # 工務單詳情：房務端與工務端兩種版面，尚無主管動作
│   ├── lib/features/task/           # 任務頁（主管做自己名下的單時沿用）
│   ├── lib/features/manager/        # 本功能新增：主管首頁、三類清單、查房畫面（目錄名由 /ui-plan 定）
│   └── test/                        # Flutter widget 測試
└── bdd/src/test/java/               # Appium ＋ Cucumber-JVM，驗 ios-app feature

tms/
├── src/main/java/com/athena/tms/
│   ├── controller/InspectionApiController.java      # createInspections 入口
│   ├── controller/TaskExecutionController.java      # getMyToday、listDeptPool
│   ├── service/InspectionService.java               # 查房通過與退回的既有規則
│   ├── service/TaskExecutionService.java            # myToday、deptPool 口徑
│   └── (repair 套件)                                # approve／reject／verify／readRepairTicket 既有實作
├── src/main/resources/db/migration/                 # Flyway
└── src/test/java/com/athena/tms/bdd/                # 後端 BDD step definitions

scripts/seed-demo.sh                                  # 示範資料：目前沒有 pending_review 的單、沒有完修照片
```

**結構決策**: 後端四個主管動作端點（`createInspections`、`approveRepairTicket`、`rejectRepairTicket`、`verifyRepairTicket`）都已存在，本功能在後端只新增「哪些單在等主管」的查詢能力，落點由 `/api-plan` 決定是新 controller 還是掛在既有的 repair／inspection 入口。App 端是本功能的主體：登入與推播分流改為先看角色，外殼沿用 `main_shell.dart` 既有的「同一個外殼換首頁與待認領兩個分頁」模式再長出第三種（主管）外殼，主管首頁、三類清單與查房畫面是全新畫面，工務單詳情在既有兩種版面之上加主管動作。資料層不預期新增資料表，三類待辦都是查詢期由 `tasks` 與 `repair_tickets` 既有欄位算出。示範資料不是 truth，但 FR-034 要求它能重現本文舉例，`/tasks` 要把 seed 調整列為任務。

## 分析流程規劃

### 系統介面的盤點

本次需求共盤點出 `8` 個系統介面。

1. `進 App 的首頁分流與推播開首頁介面`
   - 端點類型：`手機端端點`
   - 主要介面：登入成功、強制改密完成、點開去處為首頁的推播三個時機的首頁判定；主管外殼（四分頁、只換首頁與待認領）；既有的工務員工在「點開推播開首頁」時被導進房務首頁的錯誤一併修正
   - 需求原文依據：FR-001「系統 MUST 在登入成功、強制改密完成、依推播開啟首頁（含 App 原本沒在執行）這三個時機，用同一套規則決定進哪個首頁。角色是主管的帳號一律進主管首頁，不看帳號有沒有部門」、FR-002、NFR-001、SC-001、邊界情況「主管帳號帶有部門時仍依角色進主管首頁」；acceptance `主管登入後進自己的首頁.feature`
   - 既有 truth：`features/ios-app/帳號與登入/登入與登出守衛.feature`（MODIFY，目前只寫「登入成功，進入首頁」）、`features/ios-app/推播/點開推播的去處.feature`（MODIFY，去處為首頁那一列）；契約 `PushTarget.kind=home` 不動
   - truth 變更風險：低。契約與資料都不動，只改 App 的判定依據與 ios-app feature 的措辭
   - 承接 planner：`/ui-plan`

2. `主管首頁與三類清單介面`
   - 端點類型：`手機端端點`
   - 主要介面：三格（待查房、待審核、待驗收）與數字、點格進清單、三類清單每一列的識別資訊（房號與內容、工務單另帶單號）、開始等主管的時間與跨日標日期、最舊在前的排序、每類的空狀態說明、「我名下的單」沿用房務任務卡與任務頁、名下沒單的說明、動作完成後清單與格數同步刷新
   - 需求原文依據：FR-003「主管首頁 MUST 在最上方顯示待查房、待審核、待驗收三格」、FR-004、FR-005、FR-012「收哪些單的口徑與房務首頁的任務列相同」、FR-013、FR-014、FR-011「自己清的房 MUST NOT 出現在他自己的待查房清單與數字裡」、FR-027、FR-028、FR-029、FR-033、NFR-001、SC-002、SC-004、SC-006；acceptance `主管登入後進自己的首頁.feature` 的三格 2／1／3 場景與 303 跨日場景
   - 既有 truth：無主管畫面（ADD）。「我名下的單」沿用 `features/ios-app/今日進度/首頁今日進度.feature` 與 `getMyToday` 的 `todayTasks`
   - truth 變更風險：中。全新的 ios-app 模組；清單每一列要顯示什麼，直接決定介面 7 的回應要帶什麼
   - 承接 planner：`/ui-plan`

3. `查房畫面介面`
   - 端點類型：`手機端端點`
   - 主要介面：列出飯店目前啟用的查房項目、每項只能判通過或不通過、全部判過才能送出、沒有備註欄、沒有任何啟用項目時可直接送出、送出被拒（項目剛被調整）時重新載入項目讓主管重判、結果畫面三種說法（結案、退回原清房者、退回待認領）、離線時擋在送出之前
   - 需求原文依據：FR-007「查房畫面 MUST 列出飯店目前設定的全部查房項目，每一項只能判通過或不通過。每一項都判過之前 MUST NOT 能送出」、FR-009、FR-010、FR-031、邊界情況「送出時查房項目剛被調整」「飯店沒有設定任何查房項目」；acceptance `從待查房清單當場查房.feature`
   - 既有 truth：無（ADD，ios-app 目前沒有查房模組）。查房項目來源為 `sop_items` 中 `applies_to = inspection` 的啟用項目（`contracts/groups/知識與通知/SOP與查房項目.yaml`）
   - truth 變更風險：中。畫面需要「送出被拒時知道是項目集合變了」這個訊號，目前契約只寫缺項 422，沒有定義主管判定的項目多於現行集合時的回應，也沒有「單已不是待查房」的 409；這些要在介面 8 補
   - 承接 planner：`/ui-plan`

4. `工務單詳情的主管動作介面`
   - 端點類型：`手機端端點`
   - 主要介面：主管打開任何狀態的工務單詳情都看得到報修內容；只在「等主管審核」顯示核准與退回、只在「已完修尚未驗收」顯示驗收，其他狀態沒有主管動作；退回必填原因、空白不能送出；驗收一鍵、不填任何東西、沒有驗收不通過；單已被處理（409）時顯示「這張單已被處理」並刷新清單；離線時四種動作擋在送出之前；主管看到的工務單詳情是第三種版面還是在既有兩種版面上疊加
   - 需求原文依據：FR-016「主管打開一張等主管審核的工務單詳情時，MUST 看得到報修內容（照片、類別、說明、報修人），並 MUST 能核准或退回」、FR-018、FR-021、FR-023、FR-030、FR-031、FR-032「主管 MUST 能打開任一張工務單的詳情，不論入口是待審核、待驗收、推播或其他畫面」；acceptance `從待審核清單核准或退回報修.feature`、`從待驗收清單驗收完修工務單.feature`、`主管動作先到先處理且離線一律擋下.feature`
   - 既有 truth：`features/ios-app/工務單/dsl.md` 的裁決「主管審核與驗收本身都是主管端（後台／情境模擬面板）的動作，不是這個模組要承接的 App 互動」（MODIFY，裁決翻案）；`features/ios-app/系統層/離線模式與連線異常.feature` 的 Rule「離線時開始、認領、延後、開新工務單一律被擋」（MODIFY，加四種主管動作）；`工務單房務端生命週期.feature`、`工務視角.feature` 的房務端與工務端呈現不動
   - truth 變更風險：中高。翻案一條明文裁決，且離線 Rule 的場景大綱要從員工一人擴到主管；主管動作所需的詳情欄位（完修結果是已修好、暫時處理還是無法修復；驗收人的顯示名稱）要回頭看契約 `RepairTicket` 是否齊全
   - 承接 planner：`/ui-plan`

5. `待認領切換部門只看不搶介面`
   - 端點類型：`手機端端點`
   - 主要介面：主管的待認領可在房務與工務兩個池之間切換、這台手機第一次為房務、之後停在上次停留的部門（只存在這台手機，值為 `housekeeping` 或 `engineering`）、單卡內容與該部門員工一致但沒有搶單鈕、點開去處為待認領且帶部門的推播切到該部門並沿用標亮
   - 需求原文依據：FR-024「主管的待認領 MUST 能在房務與工務兩個池之間切換。這台手機第一次打開時 MUST 為房務，之後 MUST 停在上次停留的部門」、FR-025、FR-026、SC-008；關鍵實體「待認領的部門選擇（新，只存在這台手機）」；acceptance `待認領切換房務與工務只看不搶.feature`
   - 既有 truth：`features/ios-app/待認領/待認領列表.feature`（MODIFY，既有場景「房務部全員停用，王主管看到空狀態」不動）、`features/ios-app/推播/點開推播的去處.feature`（MODIFY，加帶部門的去處）；契約 `listDeptPool` 已有 `dept` 參數與 `activeStaffCount`，`PushTarget.dept` 在 `kind=pool` 時已必填，兩者都不動
   - truth 變更風險：低。後端能力已在前一輪補齊（`listDeptPool` 的 `dept` 參數），本介面只有 App 端行為與 ios-app feature
   - 承接 planner：`/ui-plan`

6. `主管待辦的資料口徑介面`
   - 端點類型：`資料庫端點`
   - 主要介面：三條判定公式能否完全由 `tasks`（`type`、`state`、`assignee_id`、`done_at`）與 `repair_tickets`（`verified_at`、`verified_by`、`reject_reason`、`origin`）既有欄位推導；三類「開始等主管的時間」的取值來源，特別是待審核的「最近一次進入等主管審核的時間」（補資料再送出會重新進入，目前沒有欄位直接存它，只有 `task_events` 的 `repair_resubmitted` 與 `created`）；「今天」依系統時區（注入的 Clock）認定的口徑是否沿用既有 `belongsToToday`；查詢走哪一個既有索引、是否需要新索引；示範資料要補的狀態（等主管審核的一般件、前一天完成的待查房單、W-0108 的完修照片）是否都能用現有欄位種出來
   - 需求原文依據：關鍵實體「主管待辦（新，衍生）：不新增資料表，由任務與工務單現有的欄位即時算出」、「任務（既有，不改欄位）」、「工務單（既有，不改欄位）」、三類待辦的判定公式表、FR-027「清單 MUST 依『開始等主管的時間』由舊到新排列」、FR-028「『今天』以系統時區認定日界線」、FR-034、SC-002
   - 既有 truth：`data/data-model.dbml` 的 `tasks`、`repair_tickets`、`task_events`、`sop_items`、`inspections`（預期 NOOP 或只補 Note）
   - truth 變更風險：低到中。若待審核的排序鍵無法由既有欄位或事件穩定推導，會變成 ADD 欄位，並回頭影響介面 7 的回應與 seed
   - 承接 planner：`/data-plan`

7. `主管待辦查詢介面`
   - 端點類型：`後端端點`
   - 主要介面：提供三類清單與三格數字的查詢能力，形狀待定（一支帶類別參數、三支各一、或放寬既有查詢）；每列要帶的識別資訊與「開始等主管的時間」；只開放主管角色；待查房要排除查詢者本人清的房；不帶日期條件、由舊到新排序；`listInspections` 的查房紀錄語意與 `listRepairTickets` 的同房語意都不得被改掉
   - 需求原文依據：影響表「ADD 新增主管待辦查詢，提供三類清單與數字，判定公式見『三類待辦的判定公式』。端點形狀交 `/api-plan`」、「不動 `listInspections`」、「不動 `listRepairTickets` 的同房語意」、FR-004、FR-006、FR-011「主管 MUST NOT 能查自己清的房。自己清的房 MUST NOT 出現在他自己的待查房清單與數字裡（既有規則 E-07）」、FR-015、FR-020、FR-027、FR-028、FR-033、交給 `/api-plan` 的備忘「主管待辦查詢是新增的能力」「待審核的排序鍵是最近一次進入等主管審核的時間」
   - 既有 truth：無對應端點（ADD）；鄰近的 `contracts/groups/現場作業/查房.yaml`、`contracts/groups/現場作業/工務單.yaml`、`contracts/groups/現場作業/員工工作台.yaml`
   - truth 變更風險：中。新增 operation 與 schema；風險在於會不會為了省事去放寬 `listRepairTickets` 而破壞任務頁「同房工務單」的唯一來源
   - 承接 planner：`/api-plan`

8. `主管動作與工務單詳情的契約補齊介面`
   - 端點類型：`後端端點`
   - 主要介面：四個既有主管動作端點行為與權限都不改，但契約有幾處主管端呼叫會踩到的落差要補：`createInspections` 沒有 `Idempotency-Key`、request body 要 App 自帶 `tenantId`、`inspectorId` 與 `result`、`items` 的 schema 型別是字串而實際是陣列、沒有定義「單已不是待查房」的 409 與「主管判定的項目集合與現行不符」的回應；`readRepairTicket` 的可查看者說明加上主管；`RepairTicket` 是否帶完修結果三選一與驗收人顯示名稱，供待驗收清單與詳情呈現；FR-019 定案為主管退回時推播報修人，推播事件新增 `repair_rejected` 一值
   - 需求原文依據：影響表「MODIFY `readRepairTicket` 的可查看者」、「不動後端 `createInspections`、`approveRepairTicket`、`rejectRepairTicket`、`verifyRepairTicket`」、FR-008「查房送出 MUST 沿用既有後端規則」、FR-017、FR-022、FR-030、FR-032、SC-005、交給 `/api-plan` 的備忘全部六點
   - 既有 truth：`contracts/groups/現場作業/查房.yaml`、`contracts/components/schemas/查房.yaml`、`contracts/groups/現場作業/工務單.yaml` 的 `readRepairTicket`、`approveRepairTicket`、`rejectRepairTicket`、`verifyRepairTicket`、`contracts/components/schemas/工務單.yaml` 的 `RepairTicket`、`contracts/components/schemas/shared.yaml` 的推播事件值（MODIFY，只補說明與缺口，不改行為）
   - truth 變更風險：中。改的是契約的完整性而不是語意，但 `createInspections` 的 body 若要收斂成由權杖推導 `tenantId` 與 `inspectorId`，等於改既有 operation 的請求形狀，會牽動後端既有 stepdef
   - 承接 planner：`/api-plan`

### 分析流程的安排

#### Wave 1

- 平行分析介面：
  - `進 App 的首頁分流與推播開首頁介面`
  - `主管首頁與三類清單介面`
  - `查房畫面介面`
  - `工務單詳情的主管動作介面`
  - `待認領切換部門只看不搶介面`
  - `主管待辦的資料口徑介面`
- 分析重點：
  - 首頁分流：三個入口（登入、強制改密完成、點開推播開首頁）要怎麼共用同一條「先看角色」的判定，主管外殼如何在既有兩種外殼之外長出而不動通知與我的兩個分頁。
  - 主管首頁與三類清單：每一列要讓主管認得單需要哪些資訊、三格與清單的空狀態與刷新時機要怎麼呈現，「我名下的單」沿用房務任務列時有哪些既有限制（含缺口 #39）要揭露給 Wave 2。
  - 查房畫面：逐項判定到送出的操作節奏要怎麼設計，以及「送出被拒時重新載入項目」需要後端提供哪種區分訊號，寫成給 Wave 2 的需求而不是假設錯誤碼。
  - 工務單詳情的主管動作：主管看到的詳情該是第三種版面還是在既有版面上疊加，核准、退回、驗收、已被處理、離線各自要什麼回饋；詳情呈現需要而契約未必有的欄位列給 Wave 2 比對。
  - 待認領切換部門：部門切換、本機記住部門、不出現搶單鈕、帶部門的推播切到該部門，這些行為要怎麼在既有待認領畫面上成立。待認領未接提醒文案定案沿用 truth 句型「{房號} {任務名} 已等 {N} 分，還沒人接」；「今天」的日界線定案沿用後端單一時區（注入的 Clock），本輪不加租戶時區設定。
  - 資料口徑：三類判定公式與三個排序鍵能否由既有欄位推導、「今天」的口徑沿用哪一套、示範資料三處調整能否用現有欄位種出；推不出的要明寫給 Wave 2。
- 安排理由：五個 UI 介面都是「主管看見什麼、能按什麼」，彼此不等待對方結論，而且全部寫入同一份 `ui/ui-plan.md`，依委派判準合併成一次 `/ui-plan` handoff，prompt 內逐一列出五個介面與各自分析重點。資料口徑只問「既有欄位夠不夠」，不需要等畫面定案，可與 UI 平行。這一波的結論（清單每列要什麼、詳情要什麼、查房畫面要分辨哪兩種拒絕、排序鍵從哪裡取）是 Wave 2 決定端點形狀的全部輸入；反過來若先做 API，就會重蹈 `listInspections` 被誤當待查房清單的那一步。

#### Wave 2

- 平行分析介面：
  - `主管待辦查詢介面`
  - `主管動作與工務單詳情的契約補齊介面`
- 分析重點：
  - 主管待辦查詢：依 Wave 1 的清單欄位需求與資料口徑結論，決定查詢是一支帶類別參數、三支各一、還是放寬既有查詢；三格數字是否與清單同一次往返取得（NFR-001 要求進 App 就看到數字，不應為三格另打三次）；每列帶的欄位與「開始等主管的時間」的來源；排除查詢者本人清的房如何表達；只開放主管角色的 403；明寫不碰 `listInspections` 與 `listRepairTickets` 的既有語意。
  - 主管動作與詳情契約補齊：對照 Wave 1 查房畫面的需求，補 `createInspections` 的 `Idempotency-Key`、把 body 中可由權杖推導的欄位與 `items` 的真實型別寫對、補「單已不是待查房」的 409 與「項目集合不符」的回應；對照 Wave 1 詳情需求，確認 `RepairTicket` 有沒有完修結果三選一與驗收人顯示名稱，沒有就補；`readRepairTicket` 的可查看者加主管；FR-019 定案新增推播報修人，落點在推播事件值 `repair_rejected`（去處為工務單詳情、文案帶退回原因）與通知事件，由 `/api-plan` 與 `/data-plan` 納入。兩個介面共用 `contracts/` 這一份主產物，合併成一次 `/api-plan` handoff。
- 安排理由：查詢的回應形狀完全取決於畫面每一列要顯示什麼與資料層能提供什麼，所以必須在 Wave 1 之後；契約補齊的範圍取決於查房畫面與詳情畫面實際需要哪些訊號與欄位，同樣依賴 Wave 1。兩個介面彼此不依賴，可平行，但都寫 `contracts/`，依委派判準合併為單次委派，避免同一輪兩次覆寫 `openapi.yaml` 與同一組 group 檔。

#### Wave 3（條件式）

- 平行分析介面：
  - `主管待辦的資料口徑介面`（第二趟）
- 分析重點：
  - 只有在 Wave 2 的 `/api-plan` 定出的回應欄位或排序鍵，是 Wave 1 判定無法由既有欄位推導的（例如待審核要一個直接存「最近一次進入等主管審核的時間」的欄位），才需要這一趟把 data truth 補齊，與契約的欄位名對齊。
- 安排理由：沿用 002 的做法，`/api-plan` 不是 data truth 的 owner、不能代寫 `data-model.dbml`；若 Wave 1 的資料口徑結論是全部可推導、Wave 2 也沒有長出新欄位，這一波直接略過。
