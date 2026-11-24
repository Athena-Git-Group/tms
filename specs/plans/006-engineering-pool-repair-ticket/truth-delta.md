# Truth Delta: 006-engineering-pool-repair-ticket

**Plan Package**: `specs/plans/006-engineering-pool-repair-ticket`

**Truth Root**: `specs/truth`

> 本檔由 truth owner skill（`/technical-research`、`/api-plan`、`/data-plan`、`/dsl-refine`）透過 `/truth-delta` 追加。
> `/specify` 只建立骨架，不得寫入任何 `specs/truth/**` 的實際改動。
> 本輪的預期影響見 `spec.md`「對既有行為的影響」。

## /technical-research

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| NOOP | `specs/truth/techstack.md` -> 全部分類 | 已檢查，不需修改。後端、App、櫃檯 Web、住客頁的框架與測試線都不變；資料修補沿用 Flyway 新增一支遷移的既有做法。 | 本輪是既有行為的錯誤修正，沒有引入新技術。 |

## /api-plan

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| MODIFY | `specs/truth/contracts/components/schemas/員工工作台.yaml` -> `PoolItem.repairCategory` | 新增欄位：工務單類別（`RepairCategory`，nullable）。工務池每張單都有值，房務池為 null；`category` 在工務池仍是 `repair`。 | FR-011、FR-012：App 單卡把任務類型 `repair` 當類別畫出代碼（`eng_pool_screen.dart:247`，實機截圖 09）。 |
| MODIFY | `specs/truth/contracts/components/schemas/員工工作台.yaml` -> `EngineeringTicket.ticketNo` | 說明改成「部門為工務的任務一律有工務單，單號實際上都有值，nullable 只為相容」，刪掉「排程規則可建出沒有工務單的 repair 任務」。型別不變。 | FR-018 不變式。 |
| MODIFY | `specs/truth/contracts/components/schemas/員工工作台.yaml` -> `EngineeringTicket.source` | 補說明：只會是 `desk_forward`（櫃檯代開或櫃檯從待派單轉來）或 `staff_report`，口徑與工務池相同。值域不變。 | FR-005；櫃檯轉來的單 `tasks.source` 是 `guest`，舊換算會標成房務報修（`TaskExecutionService.java:808`）。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `listDeptPool` 說明 | 補「工務池每張都是工務單，ticketNo 與 repairCategory 必有值」與工務池 source 換算（`desk`、`guest` 都是 `desk_forward`，其餘 `staff_report`）。參數與回應形狀不變。 | FR-005、FR-018；`TaskExecutionService.java:461-463`。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> `deskAssignTask` 說明 | 派給工務時同一交易：type 改 `repair`、triaged payload 記 `former_type` 與 `former_urgent`、開一列工務單（下一號、類別 `other`、報修人＝操作者、急件依規則判定、不要求照片）；急件推播的防重複鍵以工務單號為單位，復原後再派的新單照樣推播；不另建任務；之後走工務單驗收；重送不會開第二張。請求與回應形狀不變。 | FR-001～FR-008、NFR-001；使用者選定的修法。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> `deskUndoAssign` 說明 | 被復原的派單是派給工務時，同一交易刪掉那張工務單，type 與急件標記回到 `former_type` 與 `former_urgent`；撤掉的單號不再使用；已送出的急件推播不收回。錯誤碼不變。 | FR-009、FR-010；審查 M3：復原後留著 `urgent=true` 會讓改派房務的單在房務待認領置頂。 |
| MODIFY | `specs/truth/contracts/groups/住客與房間/住客掃碼.yaml` -> `createGuestRequest` 說明 | 住客不直通工務：勾選項目或自由文字命中工務、或命中的類型是 repair 時改進 `triage`（`dept`=frontdesk、`type`=other、照常取 T- 號），由櫃檯派單；命中的規則照樣記在 note。請求與回應形狀不變。 | FR-013、FR-014；2026-11-05「住客不直通工務」裁決，2026-11-23 使用者確認蓋過 005 住客 Web 與原型；審查 m6：類型 repair 的命中會建出沒有單號的單。 |
| MODIFY | `specs/truth/contracts/components/schemas/shared.yaml` -> `RepairTicketSummary.state` 說明 | `cancelled` 除了 `staff_withdrew`，也可能是 `guest_withdrew`（櫃檯轉來的住客需求在工務認領前被住客收回）。值域不變。 | 邊界情況「住客在工務認領前收回」。 |
| NOOP | `specs/truth/contracts/components/schemas/工務單.yaml`、`verifyRepairTicket`、`tasksComplete`、`createRepairTicket` | 已檢查，不需修改。櫃檯轉來的工務單照用既有完修與驗收契約。 | FR-006；2026-11-11 裁決不變。 |

## /data-plan

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| MODIFY | `specs/truth/data/data-model.dbml` -> `tasks.dept` note | 寫入不變式：`dept`=engineering 的任務一律 `type`=repair 且恰好一列 `repair_tickets`；讓任務成為工務部門的只有建單與從待派單派給工務，兩者同交易開工務單；住客需求不以 engineering 或 `type`=repair 建單。 | FR-013、FR-014、FR-018；審查 m6。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `tasks.task_no` note | 「type=repair 為 NULL」改成只限工務單開單路徑直接建的單；櫃檯派給工務的單保留 T-、對外顯示 W-；住客的單都有 T-，V29 退回待派單的舊單補取號。 | FR-016；審查 M1：住客經規則建的修繕單沒有 T-，退回待派單後看板 `displayNo` 會是 null。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `tasks` Note「櫃檯處理待派單」：派單、派出後復原、重放、單號 | 派給工務：守衛 UPDATE 同句 type 改 repair、payload 記 `former_type` 與 `former_urgent`、同交易開工務單並寫 `tasks.urgent`，T- 單號保留但對外顯示 W-；復原：type 與 urgent 回到派單前、刪掉那張工務單、單號不重用；重放規則跟著補；新增「V29 退回待派單」段：每張補一筆 system 的 `triage_undone`（帶 type、task_no、reason）與對應 outbox；單號段落改成「對外只顯示一個」。 | FR-001、FR-009、FR-010、FR-016；DeskTaskMapper 依 type 選單號（`DeskTaskMapper.java:33`）；審查 M2、M3。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `task_events.payload` note | `triaged` 在 `dept`=engineering 時另帶 `former_type` 與 `former_urgent`；V29 由 system 寫的 `triage_undone` 另帶 type、task_no 與 reason。 | FR-009：復原要還原派單前的類型與急件標記；FR-016：退回待派單要能重放。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `task_events.type` note | `triage_undone` 只接在 `triaged` 之後，唯一例外是 V29 由 system 寫的退回待派單。 | 審查 M2：退回待派單的舊單要有事件，重放才對得上任務列。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_tickets.task_id`、`repair_tickets.reporter_id` note | `task_id` 對應的任務 source 放寬為 staff／desk／guest／system，並寫明與 tasks 一對一；`reporter_id` 補「櫃檯轉單時是按派單的人」。欄位型別與必填不變。 | FR-003、FR-018。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_tickets` Note：急件段、「櫃檯轉單」與「舊資料修補（V29）」 | 急件段補防重複鍵以工務單為單位（`repair_urgent:{ticket_no}:{收件人}`）；新增櫃檯轉單的欄位規則與冪等（已有工務單原樣沿用）；V29 只認兩種單、其餘讓遷移停下：櫃檯派來的（最新 triaged 的操作者有值）補工務單、計數列沒有就先建；住客經規則直接進來的（`source`=guest、沒有 triaged、仍在 pool）退回待派單、補 T- 號、寫事件與 outbox；已結束的不動；上線前先停舊版本。 | FR-002、FR-003、FR-015～FR-017、FR-019；審查 M1、M2、M3、m3。 |
| NOOP | `specs/truth/data/data-model.dbml` -> 所有資料表的欄位、CHECK、索引、Ref | 已檢查，不需修改。沒有新增或刪除欄位，`reporter_id` 仍必填，`tasks.type`、`pool_source` 值域不變。 | 選擇「住客不直通工務」後，櫃檯轉單一定有報修人，不需放寬欄位。 |

## /dsl-refine

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| MODIFY | `specs/truth/features/backend/櫃檯看板與派單/櫃檯派單.feature` -> 新增 Rule「派給工務的單在同一個動作裡成為一張工務單…」「同一次派單重送時，只開一張工務單、只用掉一個單號」「派給房務的單不是工務單，不用掉工務單號」 | 三條原子 Rule，各一個 Example。 | acceptance `櫃檯把住客報修派給工務後一路走到驗收結案.feature` 的 Rule 1 前段、Rule 2、Rule 3。 |
| MODIFY | `specs/truth/features/backend/櫃檯看板與派單/派出後復原.feature` -> 新增 Rule「派給工務後復原，那次派單開出的工務單一起撤掉…」「復原後再派給工務，開的是下一個號碼的新工務單…」「派給工務時判定為急件的單，復原後急件標記回到派單前」「復原後再派給工務又判定為急件時，新的工務單照樣送出急件推播」 | 四條原子 Rule，各一個 Example；後兩條以 305 今天有 VIP 入住觸發急件。 | acceptance `派給工務後五秒內復原連工務單一起撤掉.feature`；審查 M3。 |
| MODIFY | `specs/truth/features/backend/櫃檯看板與派單/櫃檯看板讀取.feature` -> 新增 Rule「住客打的字命中工務的分類規則時仍停在待派單…」「住客勾選的服務項目設定交給工務時，單停在待派單…」 | 兩條原子 Rule。既有「命中分類規則時直接進那個部門的待認領」以房務為例，維持不變。 | acceptance `住客的報修先到櫃檯不直通工務.feature`。 |
| ADD | `specs/truth/features/backend/工務單/櫃檯轉來的工務單.feature` | 新 feature：在工務待認領且來源櫃檯、櫃檯分流篩得到、完修後等驗收、驗收通過結案且住客讀到結案、待認領每張帶工務單類別，共五條 Rule。 | acceptance `櫃檯把住客報修派給工務後一路走到驗收結案.feature` 的 Rule 1 後段與 Rule 4（後端責任）。 |
| MODIFY | `specs/truth/features/ios-app/工務單/工務視角.feature` -> 新增 Rule「工務待認領的單卡在房號方塊顯示工務單類別的中文，不顯示代碼」 | 一條 Rule、一個 Example。 | acceptance Rule 4（App 責任）；實機截圖 09。 |
| MODIFY | `specs/truth/features/backend/dsl.md` -> `Given: "{櫃檯}" 已把 "{任務}" 派給 "{部門}"`、`When: "{櫃檯}" 把 "{任務}" 派給 "{部門}"`、`When: "{櫃檯}" 復原 "{任務}" 的派單` | 權威狀態落地補上派給工務時守衛 UPDATE 同句 type 改 repair、開工務單（已有則沿用）並寫 `tasks.urgent`、payload 記 `former_type` 與 `former_urgent`、急件推播鍵以工務單號為單位；復原派給工務時刪工務單、還原 type 與 urgent。 | FR-001、FR-004、FR-009。 |
| MODIFY | `specs/truth/features/backend/工務單/dsl.md` -> `Given: "{房號}" 今天是 VIP 房，住客 "{時刻}" 入住` 搬到 `specs/truth/features/backend/dsl.md` | 搬移權威位置，語意不變。舊位置：`工務單/dsl.md` Given 表；新位置：介面根 `dsl.md` Given 表。 | `櫃檯看板與派單/派出後復原.feature` 要用它觸發急件。 |
| MODIFY | `specs/truth/features/backend/工務單/dsl.md` -> `Then: 以下員工都收到 "{單號}" 的急件推播：` 搬到 `specs/truth/features/backend/dsl.md` | 搬移權威位置，並加嚴：只認本案例觸發動作之後寫入的推播、推播文字含這個單號、防重複鍵以工務單為單位。舊位置：`工務單/dsl.md` Then 表；新位置：介面根 `dsl.md` Then 表。 | 同一張任務復原後再派，舊斷言只看 `highlightTaskId` 會被第一則推播騙過；審查 M3。 |
| MODIFY | `specs/truth/features/backend/工務單/dsl.md` -> `Given: 本飯店下一張工務單號為 "{單號}"` 搬到 `specs/truth/features/backend/dsl.md` | 搬移權威位置，語意不變。舊位置：`工務單/dsl.md` Given 表；新位置：介面根 `dsl.md` Given 表。 | 櫃檯看板與派單與工務單兩個模組都要用。 |
| MODIFY | `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> `When: "{房號}" 的住客打字送出 "{內容}"` | 權威狀態落地改成：只有命中房務、類型不是修繕才直接進房務待認領；命中工務、命中類型修繕、沒命中或命中多部門都進 triage、type other、照常取 T- 號；命中的 `rule_id` 照記。 | FR-013；審查 m6。 |
| ADD | `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> `Then: "{任務}" 的急件標記回到 "{急件}"` | 新句型：查 `tasks.urgent` 等於最新 `triaged` payload 的 `former_urgent`。 | FR-009；審查 M3。 |
| ADD | `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> `Given: 飯店的服務項目 "{項目}" 設定交給 "{部門}"`、`When: "{房號}" 的住客勾選服務項目 "{項目}" 送出` | 新句型；勾選的權威狀態落地同樣把類型修繕的項目送進 triage。 | FR-014；審查 m6。 |
| ADD | `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> `Then: "{任務}" 成為工務單 "{單號}"，內容如下：`、`Then: "{任務}" 只有一張工務單 "{單號}"，本飯店下一張工務單號為 "{下一號}"`、`Then: "{任務}" 不是工務單，本飯店下一張工務單號仍為 "{單號}"`、`Then: "{任務}" 不再是工務單，類別回到 "{類別}"，工務待認領沒有 "{單號}"` | 新句型，必查權威狀態、再讀與跨視角。 | FR-001～FR-010。 |
| MODIFY | `specs/truth/features/backend/工務單/dsl.md` -> 模組說明首段與「本輪裁決（櫃檯直通工務）」段 | 工務單的 source 放寬為 staff／desk／guest／system，寫入不變式；住客不直通工務改為「命中工務停在待派單，由櫃檯派給工務時才成為工務單」。 | FR-013、FR-018。 |
| MODIFY | `specs/truth/features/backend/工務單/dsl.md` -> `When: "{工務}" 只看 "{分流}" 的工務待認領`、`Then: "{單號}" 的來源為 "{來源}"`、`Then: 工務待認領包含 "{單號}"` | 櫃檯分流與來源「櫃檯」含 source guest；「沒有 triaged」改成櫃檯轉來的單除外，並多查 `PoolItem.ticketNo`。 | FR-005；舊句與櫃檯轉單互相矛盾。 |
| ADD | `specs/truth/features/backend/工務單/dsl.md` -> `Given: "{工務}" 已認領並開始處理 "{單號}"`、`Then: 工務待認領中 "{單號}" 的類別為 "{類別}"`、`Then: "{房號}" 的住客讀到這張需求的狀態為 "{狀態}"` | 新句型。 | FR-006、FR-011；acceptance「住客看到已完成」的後端責任。 |
| ADD | `specs/truth/features/ios-app/工務單/dsl.md` -> `Then: 工務待認領中 "{單號}" 的類別標示為 "{類別}"` | 新句型，以 `engPool.item.{單號}.category-label` 定位。 | FR-012。 |
| NOOP | `specs/truth/features/backend/工務單/開立工務單與急件.feature`、`驗收不通過與重修.feature`、`主管驗收的狀態守衛.feature`、`完修與回到原任務.feature` | 已檢查，不需修改。急件判定、驗收與重修規則不變；櫃檯轉單共用同一個開單與判定元件，由既有急件情境把關判定邏輯。 | FR-004、FR-006。 |
| MODIFY | `specs/truth/features/guest-web/設備報修/組成文字送出.feature` -> Rule「分類規則命中時這張直接進該部門的待認領…」 | 拆成兩條原子 Rule：「分類規則把報修分給工務時，這張仍停在待派單等櫃檯派給工務」（後端狀況是待派單）與「…住客頁照後端回的狀態顯示「已送出」與「櫃檯」，不依選的類別自己猜工務」（進度頁是已送出、櫃檯、櫃檯正在看您的需求）。原本期待「工務待認領」「已受理」「工務收到」。 | FR-013；2026-11-23 使用者裁決：住客不直通工務蓋過 005 住客 Web 與原型；審查 B1。 |
| MODIFY | `specs/truth/features/guest-web/設備報修/dsl.md` -> `Given: 這家飯店的分類規則把含 "{關鍵字}" 的文字分給工務` | Arrange 不變（加一條 engineering、修繕的規則），補寫後端依住客不直通工務把命中的需求放在待派單，這一句只負責設定規則。 | FR-013；審查 B1。 |
| MODIFY | `specs/truth/features/guest-web/dsl.md` -> 共通實作約定「分類規則」 | 補：宣告了分給工務的規則也停在待派單，只有分給房務、類型不是修繕的規則會讓打字需求直接進房務待認領。 | FR-013、FR-014。 |
| MODIFY | `specs/truth/features/guest-web/dsl.md` -> 需求的狀況對照「工務待認領」「工務已接」與 `Given: "{房號}" 房的住客這次住宿有以下需求：` | 工務的狀況是櫃檯派給工務之後的工務單（`type`=repair、有工務單）；`類型` 預設在工務待認領、工務已接、已結案改為修繕；Arrange 先以林佩君呼叫 `deskAssignTask` 派給工務再推進，不直接把住客的單寫成工務部門。 | FR-018 不變式：預設的備品＋工務部門在新版是不可能的狀態。 |
| MODIFY | `specs/truth/features/frontdesk-web/待派單/待派單清單.feature` -> Rule「頁尾說明哪些需求會來待派單頁」 | 頁尾文字改成「住客打字、系統又猜不準部門的需求，和住客要給工務的需求，都會來這裡。給房務的勾選單和命中規則的文字會直接進房務待認領。也可以在總覽看板把卡片拖到房務或工務。」 | FR-013、FR-014：原文「命中規則的文字會直接進部門待認領」碰到工務不再成立；審查 B1。 |
| MODIFY | `specs/truth/features/frontdesk-web/待派單/待派單清單.feature` -> Rule「直接進部門待認領的需求不會出現在待派單頁」 | Rule 與場景名改成「房務」，When 改用新句型 `一張住客勾選的需求與一張命中房務規則的打字需求進來`。 | 審查 B1。 |
| MODIFY | `specs/truth/features/frontdesk-web/待派單/dsl.md` -> `When: 一張住客勾選的需求與一張命中規則的打字需求進來` 改為 `When: 一張住客勾選的需求與一張命中房務規則的打字需求進來` | 規則改分給房務、類型備品，兩張都直接進房務待認領；等待條件改成頁首「房務 N 單待接」多兩張，不再等工務。 | FR-013；`TriageWhenSteps.java:106-123` 設的是工務規則，新版會讓那張停在待派單；審查 B1。 |
| MODIFY | `specs/truth/features/frontdesk-web/dsl.md` -> `Given: 全館有以下的單：` | `類型` 預設：部門是工務時是修繕；櫃檯派給工務的待認領單同時是工務單（類別其他、報修人是經手人），對外顯示 W-；部門是工務的單一律有工務單。 | FR-018 不變式：預設「住客原話→其他」＋工務部門在新版是不可能的狀態。 |
| NOOP | `specs/truth/features/guest-web/**` 其餘 | 已檢查，不需修改。`我的需求/狀態對照表.feature:127-139`、`進度頁內容與四步副文.feature:43-51`、`狀態訊息框.feature:83` 的「工務待認領」是櫃檯派給工務之後的狀態，顯示不變；`取消與評分/評分.feature:91` 已是「驗收過的工務單不評分」；`app/web/src/guest/lib/stage.ts` 只看狀態值，不假設住客的單會直接進工務。 | 住客頁只照後端回的狀態與部門顯示。 |
| NOOP | `specs/truth/features/frontdesk-web/**` 其餘 | 已檢查，不需修改。派給工務的場景（`共用外框/支援的視窗與輸入方式.feature:89`、`總覽看板/拖曳派單.feature:56-82`、`總覽看板/等待膠囊與逾時.feature:69`、`待派單/按鈕派單與復原.feature`、`單筆詳情/歷程.feature`、`即時更新/`）只斷言浮條、歷程句子、部門與等待時間，不斷言派出後的單號或類別；`dsl.md:96` 的 `後端 "{房號}" 的單在 "{部門}" 待認領` 照舊成立。 | 由 Phase 5 的櫃檯 Web 全套回歸對照基準確認。 |
| NOOP | acceptance `已經卡在工務的單上線後補好.feature` | 不建介面 feature：這是上線時一次性的資料修補，沒有使用者操作的介面，兩條 Rule（補好、遇到無法歸類的單就停下）都由 data truth 的 V29 note 與 `tasks.md` 的遷移測試、上線後查核承接。 | FR-015～FR-017、FR-019、SC-001。 |
