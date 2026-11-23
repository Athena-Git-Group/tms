# 系統分析規劃：派給工務的單一律是工務單

**Plan Package**: `specs/plans/006-engineering-pool-repair-ticket`
**Truth Root**: `specs/truth`
**Truth Delta**: `specs/plans/006-engineering-pool-repair-ticket/truth-delta.md`

讀者先讀 `spec.md` 的「範圍」：這是後端違反既有 truth 的錯誤，修法已由使用者選定（派給工務時後端開工務單）。本檔做三件事：盤點會讓單進入工務的每一條路、決定由哪一個地方保證「屬於工務的單一定是工務單」、安排後續 truth owner 的分析順序。

`research.md` 不建立：本輪沒有新技術，`/technical-research` 在 truth-delta 記 NOOP。原本該寫在 research 的技術決定集中在本檔「已定的事」。

`.agents/constitution/` 不存在（CONSTITUTION.md、shared.md、skills/system-analysis/plan.md 都沒有），本輪各 skill 讀憲法的步驟一律略過，改以專案根目錄 `CLAUDE.md` 為準。

## 專案結構

### 文件結構（本功能）

```text
specs/plans/006-engineering-pool-repair-ticket/
├── spec.md
├── checklists/requirements.md
├── features/acceptance/          # 4 份 Journey
├── plan.md                       # 本檔
├── truth-delta.md
└── tasks.md
```

`ui/` 不建立：`/ui-plan` 本輪 NOOP，理由見 Wave 2。

### 原始碼結構（儲存庫根目錄）

```text
tms/src/main/java/com/athena/tms/
├── service/TaskEngine.java              # 單進入工務的唯一兩個入口：create、assignFromTriage；復原 undoTriage
├── service/DeskBoardService.java        # 櫃檯派單、復原（呼叫 TaskEngine，不改）
├── service/GuestRequestService.java     # 住客建立需求：命中工務改進待派單
├── service/TaskExecutionService.java    # 部門待認領 deptPool、工務首頁 engineeringTickets 的來源換算與類別欄位
├── dto/TaskCreateCommand.java           # 多帶一份工務單草稿
└── repair/
    ├── RepairService.java               # 既有三條開單路徑改走同一個開單步驟，自己不再 INSERT
    └── RepairTicketOpener.java          # 新：開工務單（取號、急件判定、寫入、急件推播）與撤掉工務單
tms/src/main/resources/db/migration/V29__engineering_tasks_get_repair_tickets.sql   # 補舊資料
tms/src/test/java/com/athena/tms/bdd/steps/{desk,repair,pool}/                     # 後端 BDD 步驟
app/mobile/lib/core/api/…PoolItem 模型                                               # 多解析 repairCategory
app/mobile/lib/features/engineering/eng_pool_screen.dart                           # 單卡類別改讀 repairCategory
app/bdd/src/test/java/…/steps/                                                     # App BDD 步驟
app/web/src/i18n/locales/zh-TW.ts                                                  # 櫃檯 Web 待派單頁尾說明
app/web-bdd/src/test/java/com/athena/web/bdd/steps/{triage,guest}/                 # 櫃檯 Web 與住客頁 BDD 步驟
app/bdd-support/src/main/java/com/athena/bddsupport/                               # 兩條網頁測試線共用的測試資料（工務部門的單一律帶工務單）
scripts/manual-env/start.sh                                                        # 手動環境的網頁代理補上 /guest
```

**結構決策**: 不新增模組、不新增資料表或欄位。新增一個後端元件 `RepairTicketOpener` 放在 `repair` 套件，只依賴資料庫存取、時鐘與推播寫入，不依賴 `TaskEngine`，所以 `TaskEngine` 與 `RepairService` 都能用它而不形成循環依賴。

## 分析流程規劃

### 系統介面的盤點

本次需求共盤點出 `7` 個系統介面。

1. `後端：櫃檯派單與復原`
   - 端點類型：`後端`
   - 主要介面：`deskAssignTask`、`deskUndoAssign` 的狀態轉移語意（派給工務即開工務單、復原即撤掉）
   - 需求原文依據：`FR-001`～`FR-010`、`FR-018`、`NFR-001`
2. `後端：住客建立需求`
   - 端點類型：`後端`
   - 主要介面：`createGuestRequest` 的分流語意（命中工務改進待派單）
   - 需求原文依據：`FR-013`、`FR-014`
3. `後端：部門待認領與工務首頁的工務單欄位`
   - 端點類型：`後端`
   - 主要介面：`listDeptPool` 的 `PoolItem`（新增工務單類別、來源換算）、工務首頁 `EngineeringTicket.source` 的來源換算、`RepairTicketSummary.state=cancelled` 的說明
   - 需求原文依據：`FR-005`、`FR-011`、邊界情況「住客在工務認領前收回」
4. `資料：任務與工務單的不變式與舊資料修補`
   - 端點類型：`資料庫`
   - 主要介面：`tasks` 與 `repair_tickets` 的生命週期註記；V29 遷移
   - 需求原文依據：`FR-015`～`FR-018`、關鍵實體、`SC-001`
5. `手機 App：工務待認領單卡的類別文字`
   - 端點類型：`手機端`
   - 主要介面：工務待認領單卡房號方塊的標示
   - 需求原文依據：`FR-012`、`SC-004`、實機截圖 09
6. `櫃檯 Web：待派單頁`
   - 端點類型：`前端`
   - 主要介面：頁尾說明文字；「命中規則的打字需求直接進待認領」的例子改用房務規則
   - 需求原文依據：`FR-013`、`FR-014`
7. `住客頁：設備報修與我的需求`
   - 端點類型：`前端`
   - 主要介面：命中工務規則的報修送出後的狀況與進度頁文字；畫面程式不改，行為跟著後端
   - 需求原文依據：`FR-013`；2026-11-23 使用者確認蓋過 005 住客 Web 與原型

### 分析流程的安排

#### Wave 1

- 平行分析介面：
  - `後端：櫃檯派單與復原`、`後端：住客建立需求`、`後端：部門待認領與工務首頁的工務單欄位`（合併一次 `/api-plan`，共用主產物 `specs/truth/contracts/**`）
  - `資料：任務與工務單的不變式與舊資料修補`（`/data-plan`）
- 分析重點：
  - `/api-plan`：三個介面的對外說明跟著不變式改；待認領單項多一個工務單類別欄位；來源換算把櫃檯轉單納入「櫃檯」。
  - `/data-plan`：把「部門為工務的任務恰好一張工務單」寫進 `tasks`、`repair_tickets` 的註記；`repair_tickets.task_id` 註記的來源值放寬；記錄 V29 的修補範圍。
- 安排理由：兩者都只依賴本檔「已定的事」裡已拍板的不變式與欄位決定，彼此不等對方的結論，可以平行。

#### Wave 2

- 平行分析介面：
  - `手機 App：工務待認領單卡的類別文字`（`/ui-plan`）
- 分析重點：
  - 單卡版面、狀態與流程都不變，只把房號方塊的類別文字改讀 Wave 1 新增的工務單類別。
- 安排理由：要等 `/api-plan` 定下待認領單項的類別欄位名稱與值域。`/ui-plan` 的結論是 NOOP：沒有新畫面、新狀態、新流程或新錯誤狀態，單卡既有設計本來就寫類別中文，錯的是資料來源，所以不產出 `ui/ui-plan.md` 與靜態雛形，也就沒有 plan-side 產物需要 quality gate。App 可觀察的結果由 `/dsl-refine` 在 `ios-app/工務單` 補一條 Rule 承接。

#### 兩輪之後

`/dsl-refine` 依 acceptance 把結果拆進 `features/backend/櫃檯看板與派單`、`features/backend/工務單`、`features/ios-app/工務單`，並修正 `features/guest-web/設備報修`、`features/frontdesk-web/待派單` 與兩條網頁測試線的測試資料約定，再由 `/tasks` 產出可執行的 `tasks.md`。

## 後端現況的落差與歸屬

會讓一張單屬於工務的每一條路（以 2026-11-23 的 `bugfix/main_engineering_pool_repair_ticket` 原始碼為準）：

| 路徑 | 程式位置 | 今天有工務單嗎 | 修好之後 |
| --- | --- | --- | --- |
| 房務中斷開單、房務報修、工務自開、櫃檯代開工務單 | `RepairService.create`（`RepairService.java:509`，寫入在 `:569`） | 有 | 改由 `TaskEngine.create` 帶工務單草稿，經 `RepairTicketOpener` 開單；`RepairService` 不再自己寫入 |
| 完修「暫時處理」自動建追蹤單 | `RepairService.createFollowUpTicket`（`RepairService.java:350-370`） | 有 | 同上 |
| 櫃檯從待派單派給工務 | `DeskBoardService.assign`（`DeskBoardService.java:241`）→ `TaskEngine.assignFromTriage`（`TaskEngine.java:112-115`） | **沒有**（本錯誤） | 派單同一交易經 `RepairTicketOpener` 開單；單的類別改為修繕 |
| 住客自由文字命中工務的分類規則 | `GuestRequestService.freeTextCommand`（`:123-143`）、`ClassificationRuleEngine.route`（`:24-27`）；實機設定 `r-aircon` 把「冷氣、空調、air con」分給工務、類型 `repair`（`V2__seed_dev.sql:47`），所以這些單連 T- 單號都沒有（`TaskEngine.java:77`） | **沒有**，而且直接進工務待認領 | 改進待派單（類別 `other`，照常取 T- 號），由櫃檯派單時才開工務單 |
| 住客勾選服務項目被設定給工務 | `GuestRequestService.menuCommand`（`:113-120`） | **沒有**（預設設定沒有這種項目，飯店可以設） | 同上 |
| 自動化規則排程工單 | `AutomationRuleService.createRuleTaskOnce`（`:159-172`） | 不適用：部門寫死房務 | 不變 |
| 同部門回到待認領：主管重新排入、放回待認領、解除暫緩、請假退池、班末退池、查房退回、驗收不通過退回 | `TaskEngine.requeueToPool`、`releaseToPool`、`undefer`、`returnToPoolForLeave`、`returnNotStartedAtShiftEnd`、`returnToPool`、`failRepairVerification` | 部門不變，沿用進工務時那張 | 不變 |
| 改派、轉派（換負責人） | `TaskEngine.reassign`（`:298`） | 部門不變 | 不變 |
| 跨部門轉派 | 不存在 | — | — |
| 櫃檯復原（離開工務） | `TaskEngine.undoTriage`（`:118-133`） | 不適用 | 撤掉這次派單開的工務單，類別回到派單前 |

讀端的兩處落差：

- `TaskExecutionService.deptPool`（`:461-463`）與 `engineeringTickets`（`:808`）各寫一次「`source` 是 `desk` 才算櫃檯，其餘算房務報修」。櫃檯轉來的單 `source` 是 `guest`，照這條會被標成房務報修。改成一個共用的換算：工務單的 `source` 是 `desk` 或 `guest` 算「櫃檯」（`desk_forward`），其餘算「房務報修」（`staff_report`）。
- `deptPool` 的 `category` 放的是任務類型，工務池一律是 `repair`；App `eng_pool_screen.dart:247` 把它丟給 `repairCategoryShortText`，畫出代碼。`poolRows` 已經 JOIN `repair_tickets`，多選 `rt.category` 回傳成新欄位即可。

## 已定的事

**不變式**：任務的部門是工務，就恰好有一張工務單（`repair_tickets` 一列，`task_id` 等於任務 id），任務類型是 `repair`。反過來，工務單只掛在部門為工務的任務上（復原時撤掉）。

**唯一保證點**：`TaskEngine`。部門會變成工務的只有 `TaskEngine.create`（部門參數）與 `TaskEngine.assignFromTriage`（派單）。兩者在寫入任務的同一交易內呼叫 `RepairTicketOpener.open(tenantId, taskId, 草稿)`：

- `create`：部門是工務時，`TaskCreateCommand` 必須帶工務單草稿（類別、說明、報修人、原任務、追蹤單的原單）；沒帶就丟程式錯誤，不靜默放行。`RepairService` 的兩條開單路徑改帶草稿，刪掉自己的兩段 `INSERT INTO repair_tickets`（遷移呼叫者後刪掉舊寫法，不並存）。住客路徑在故事 4 之後不會再以工務建單。
- `assignFromTriage`：目標部門是工務時，守衛 UPDATE（`WHERE state = 'triage'` 那一句）同句把任務類型改成 `repair`，`triaged` 事件的 payload 多記 `former_type` 與 `former_urgent`（派單前的類型與急件標記），再以草稿「類別 `other`、報修人＝派單的人、說明空白、沒有原任務」開單。類型只在這一句改，`open` 不碰 `tasks.type`。
- `undoTriage`：復原的那筆 `triaged` 是派給工務時，呼叫 `RepairTicketOpener.discard` 刪掉這張工務單，同一句 UPDATE 把類型與急件標記改回 `former_type` 與 `former_urgent`。不還原急件標記的話，復原後改派房務的單會帶著 `urgent=true` 被房務待認領排在最前面（`TaskExecutionService.java:524-545` 依 `urgent DESC` 排序）。能復原的前提（還在待認領、沒有負責人、最新事件就是那筆派單、10 秒內）保證這張工務單還沒有照片、貢獻者、追蹤單指向它，刪除不會撞外鍵。

**`open` 的職責**：取號、寫 `repair_tickets`、把急件判定寫進 `repair_tickets.is_urgent`／`urgent_rule` 與 `tasks.urgent`、判定為急件時寫急件推播。急件推播的防重複鍵改成以工務單號為單位：`repair_urgent:{ticketNo}:{recipient}`（原本是 `repair_urgent:{taskId}:{recipient}`，`RepairService.java:754`）。outbox 的唯一鍵是 `(tenant_id, kind, idempotency_key)`，單號在飯店內不重複，所以同一張任務復原後再派開出的新單會再推一次，第一次那則不收回。

**冪等**：`open` 先看這張任務有沒有工務單，有就原樣回傳，不取號、不改欄位；沒有才取號寫入。同一張任務的兩個並行請求在前一步的條件更新（`WHERE state = 'triage'` 等）就已經被任務列的鎖排成先後，後到的被既有規則拒絕，走不到 `open`。派單的防重複鍵重送拿的是第一次的回應，根本不會再執行一次。

**櫃檯轉單開出的工務單欄位**：

| 欄位 | 值 | 依據 |
| --- | --- | --- |
| 單號 | `repair_ticket_counters` 下一號，`W-` 加 4 位數，與其他路徑同一個流水號 | FR-002 |
| 類別 | `other`（其他） | FR-003；truth 例子 W-0620「其他／櫃檯」 |
| 標題 | 沿用任務原本的標題（住客原話或翻譯） | FR-003 |
| 說明 | 空白 | 標題已是住客原話 |
| 報修人 | 按派單的人（櫃檯、主管或值班主管） | FR-003；`reporter_id` 必填不變 |
| 原任務 | 空白：工務單就是這張任務本身 | — |
| 照片 | 不要求 | FR-003 |
| 急件 | `judgeUrgent` 從 `RepairService` 搬進 `RepairTicketOpener`，三條路徑共用；判定為急件時同交易寫急件推播 | FR-004 |
| 來源標示 | 讀端換算：`source` 是 `desk` 或 `guest` 都是「櫃檯」（`desk_forward`） | FR-005 |
| 驗收 | 類型是 `repair`，完成時停在已完修等驗收，既有 `verifyRepairTicket` 原樣適用 | FR-006 |

**住客不直通工務**：`GuestRequestService` 組指令時，命中的部門是工務、或命中的類型是 `repair`（自由文字或服務項目）就改成 `triage`、部門 `frontdesk`、類型 `other`，與沒有命中規則的文字同一個樣子；類型是 `other` 所以建單時照常取 T- 號。命中的 `rule_id` 照樣記在 `note`（`tms/src/test/resources/features/01-guest-requests.feature` 的「規則引擎命中留痕可解釋」）。類型 `repair` 也收斂，是因為飯店若把規則設成房務＋修繕，`TaskEngine.java:77` 會建出沒有 T- 號、也沒有工務單的修繕單，櫃檯看板的 `displayNo` 會是 null。依 2026-11-05 使用者裁決「住客不直通工務」。另一條路（住客的單直接成為工務單）要放寬 `reporter_id` 必填、新增「住客」來源標示與 App 分流磚，範圍較大，交付時請使用者確認。

**V29 修補舊資料**（只新增遷移，不動既有遷移的任何一個字，包括註解，否則 Flyway checksum 會壞）。對象是部門工務、沒結束（狀態不是 `done`、`inspected`、`closed`、`cancelled`）、沒有工務單的單，只認兩種：

- **櫃檯派來的**：最新一筆 `triaged` 的 payload `dept` 是 `engineering`，而且它的 `actor_id` 有值。狀態可以是 `pool`、`accepted`、`doing`、`deferred`、`interrupted`。
- **住客經規則直接進來的**：`source = 'guest'`、沒有任何 `triaged` 事件、狀態是 `pool`。

步驟：

1. 先檢查：有任何一張不屬於這兩種（派單紀錄沒有操作者、住客來源卻不在 `pool`、非住客來源又沒有派單紀錄，例如手寫 SQL 塞進去的單），就 `RAISE EXCEPTION` 帶筆數與前幾張的 id 停下，不猜怎麼處理。
2. 住客經規則直接進來的：回到 `triage`、部門 `frontdesk`、類型 `other`，`pool_source`、`reminder_stage` 清成 NULL，`priority_notify` 設回 false。`task_no` 是 NULL 的（`r-aircon` 的類型是 `repair`，建單時沒取號）照 V25 的做法依 `(created_at, id)` 從 `task_counters` 接續取 T- 號，沒有計數列的飯店先 upsert 建出來，最後把計數推到用掉的最後一號。每張同句推進 `event_seq`，寫一筆 `triage_undone`（`actor_role='system'`、`actor_id` NULL、payload `{"dept":"engineering","type":"other","task_no":…,"reason":"guest_not_direct_backfill"}`），再寫一筆對應的 outbox（`kind='task_event'`、`aggregate_key='task:<id>'`、`idempotency_key='task:<id>:seq:<n>'`、envelope 欄位同 `OutboxWriter.enqueueTaskEvent`）。這樣事件重放還原得出現在的任務列，Kafka 與事件串流（`TaskEventsSseConsumer` 讀 `triage_undone` 的 `dept` 判斷從哪個部門收回）也收得到。櫃檯歷程畫面不為這一筆組句子（`app/web/src/lib/timeline-sentence.ts` 對沒認得的事件回 null），今天的派單張數只數沒被收回的 `triaged`（`DeskBoardService.java:188`），都不受影響。
3. 櫃檯派來的：類型改成 `repair`，補一列工務單：單號依那筆 `triaged` 的時間、再依 id 接續各飯店的流水號，沒有 `repair_ticket_counters` 列的飯店先 upsert 建出來；類別 `other`、報修人是那筆 `triaged` 的操作者、建立時間是那筆 `triaged` 的時間、非急件；最後把 `repair_ticket_counters` 推到用掉的最後一號。不另寫事件：既有那筆 `triaged` 重放時本來就把類型設為 `repair`。這些舊派單的 payload 沒有 `former_type`，但它們早就過了 10 秒的復原時限，不會被復原。
4. 已結束（`done`、`inspected`、`closed`、`cancelled`）的歷史單一律不動。
5. 跑完後「部門工務、沒結束、沒有工務單」為 0 筆，作為遷移末段的自我檢查（不是 0 就 `RAISE EXCEPTION`）。

上線順序：先停舊版本後端，再起新版。Flyway 在新版啟動時執行 V29；舊版還在服務時有人在第 1 步之後認領，第 5 步就會讓新版起不來。步驟 2、3 改到的單，已開著的櫃檯 Web 與 App 在重連後重讀就會看到。

**App**：`PoolItem` 多解析 `repairCategory`；工務待認領單卡房號方塊改用 `repairCategoryShortText(item.repairCategory)`。`ticketNo == null` 就不畫的那一行保留不動：不變式成立後它不會再觸發，拿掉不是本輪範圍。

**不改的地方，記下理由**：
- 櫃檯派單不發 `pool_new_request`：房務與工務今天都沒有，故事 4 之後住客打「冷氣」原本會有的這則推播也沒了。列為已知缺口（見 `spec.md`「本版不做」），另開一題。
- 住客頁的進度階段對照表：見 `spec.md`「新行為」。
- 測試資料繞過唯一保證點的地方（正式程式碼沒有）：`tms/src/test` 裡直接以工務部門呼叫 `TaskEngine.create` 再自己 `INSERT INTO repair_tickets` 或根本不開工務單的 fixture，見 `tasks.md` 的 fixture 盤點題；網頁測試線的 `app/bdd-support` 直接寫 SQL，不會撞到程式錯誤，但照新版 truth 也要讓工務部門的單帶工務單。
- 工務單完修推播給報修人：櫃檯轉單的報修人是櫃檯人員，多半沒有登記手機，推播會記成沒有裝置而略過，這是既有推播的正常行為。
