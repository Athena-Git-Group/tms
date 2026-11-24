# Truth Delta: 007-frontdesk-create-request

**Plan Package**: `specs/plans/007-frontdesk-create-request`

**Truth Root**: `specs/truth`

> 本檔由 truth owner skill（`/technical-research`、`/api-plan`、`/data-plan`、`/dsl-refine`）透過 `/truth-delta` 追加。
> `/specify` 只建立骨架，不得寫入任何 `specs/truth/**` 的實際改動。
> 本輪的預期影響見 `spec.md`「對既有行為的影響」：代開單面板的畫面行為與櫃檯代開單的後端動作為 ADD；「代開單」不再是尚未開放的入口、代開工務單的照片／來源／權限／標題、住客每房額度不算代開的單為 MODIFY；狀態、部門、類型、來源、修繕分類的值不動。

## /specify 預期影響（待 truth owner 確認，非實際改動）

下表是 `/specify` 依 spec 推得的預期，給後續 truth owner skill 當起點；每一列都要由對應的 skill 核對後，才以正式列寫進下面各段。

| 預期動作 | Truth 規格 | 預期改動 | 依據 | 交給 |
| --- | --- | --- | --- | --- |
| MODIFY | `specs/truth/features/frontdesk-web/共用外框/尚未開放的入口.feature` -> Rule「「通知板」「遺失物」與「代開單」照原型畫出來，點了沒有反應，也不會改到任何資料」 | 從 Rule 名與「例子」拿掉「代開單」；「通知板」「遺失物」照舊點不進去 | FR-001、FR-038 | `/dsl-refine` |
| NOOP（待確認） | `specs/truth/features/frontdesk-web/共用外框/側欄與工具列.feature` | 已斷言工具列有「代開單」，預期不需修改 | FR-001 | `/dsl-refine` |
| ADD | `specs/truth/features/frontdesk-web/代開單/`（新模組，含 `dsl.md`） | 代開單面板：入口與預填、房號欄與建議房號、住客卡、空房提示、常用品項、其他需求、類似單提醒、送出摘要、送出鈕、送出後的畫面、關閉不確認 | 使用者故事 1 至 5、FR-001 至 FR-039 | `/dsl-refine` |
| NOOP（待確認） | `specs/truth/features/frontdesk-web/總覽看板/卡片內容.feature`、`單筆詳情/詳情面板內容.feature`、`單筆詳情/歷程.feature` | 「櫃檯開單」「櫃檯代開（{姓名}）」「櫃檯代開「{標題}」，進{部門}待認領」已定案，預期不需修改 | FR-013 | `/dsl-refine` |
| ADD | `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> 櫃檯代開單 | 新動作：一個房號一次送出多張單（品項與數量、其他需求與部門）；房務開一般單、工務開工務單；同一個交易內開完這一次的所有單（一般單與工務單），任何一張開不成就全部不開；冪等（失敗後不改內容再送算同一次）；錯誤碼沿用 `forbidden`、`not_found`（房號不存在）、`validation_failed`；`frontdesk`、`manager`、`duty_manager` 可用；不算住客每房額度 | FR-011、FR-016、FR-022、FR-023、FR-034 至 FR-037 | `/api-plan` |
| ADD 或 NOOP（待判定） | `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` 或 `住客與房間/房間.yaml`、`訂房檔副本與房態匯入.yaml` | 代開單面板要的讀取：房號是否有效、在住住客（姓名、VIP、入住退房日期）與建議房號、同房號還沒結束的單對應哪個品項（櫃檯代開、住客勾選、住客打字後被對應）、是不是住客打字的；代開 12 品項與住客選單項目鍵（`GuestTenantConfigReader.java:28-32`）的對照方式；看板資料是否已足夠 | FR-005、FR-025 至 FR-027、FR-030 | `/api-plan` |
| MODIFY | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `createRepairTicket`，或共用的開工務單元件；`specs/truth/features/backend/工務單/` | 櫃檯代開時改三處既有規則：一、免照片（既有至少 1 張，`photo_required`，`RepairService.java:495`、`:673-676`）；二、一律記來源 `desk`、待認領來源分流 `desk_forward`，不看開單人部門（既有依部門：工務部門記 `staff`、非櫃檯非工務被擋，`RepairService.java:513-516`、`:571-575`），`frontdesk`、`manager`、`duty_manager` 都放行（值班主管現在連 `POST /api/v1/repair-tickets` 都進不去，`SecurityConfig.java:71-72`）；三、標題是「{品名}有問題」或其他需求原文，不是「{分類名} 工務單」。改在共用元件還是櫃檯自己的動作，由 `/api-plan` 決定 | FR-016、FR-017、FR-034 | `/api-plan`、`/dsl-refine` |
| MODIFY | 住客掃碼的每房額度（`RoomRequestRateLimiter.java:22-23`、`:29`）與 `specs/truth/contracts/groups/住客與房間/住客掃碼.yaml` 的額度說明、對應的後端 feature | 既有計數條件 `source IN ('guest', 'desk')`，代開的單佔住客名額；改成只算住客送的，代開的單不算、也不受限 | FR-037 | `/api-plan`、`/dsl-refine` |
| ADD | `specs/truth/features/backend/櫃檯看板與派單/` -> 代開單 | 後端規則：每個品項一張單、標題組法、部門類型與修繕分類對應、直接進待認領不經 `triage`、全部成立或全部不成立、重送不重開、三種角色可用、不算住客每房額度、歷程操作者與工務單報修人 | FR-011、FR-016 至 FR-019、FR-022、FR-023、FR-034 至 FR-039 | `/dsl-refine` |
| MODIFY（待判定） | `specs/truth/data/data-model.dbml` -> `tasks`、`repair_tickets` 的 note | 記下櫃檯代開的單（來源 `desk`、直接 `pool`）；類似單提醒若要記「從哪個品項開出」需要的資料；主管代開時工務單的報修人。欄位與值域預期不動 | FR-016、FR-030、FR-039 | `/data-plan` |
| NOOP（已由 `/technical-research` 核對，見下方正式列：沒有新套件，有 3 列 MODIFY、1 列 ADD、1 列 NOOP） | `specs/truth/techstack.md` | 沒有引入新技術，預期不需修改 | — | `/technical-research` |

## /technical-research

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| MODIFY | `specs/truth/techstack.md` -> `技術堆疊總覽 / 前端 / 櫃檯 Web 框架與建置`、`櫃檯 Web 換頁` | 用途補上代開單畫面；代開單是蓋在總覽看板與待派單頁上的面板，不新增路由。 | 007 新增代開單面板（FR-001、FR-002、FR-014）；technical-research 決策 1。 |
| MODIFY | `specs/truth/techstack.md` -> `技術堆疊總覽 / 資料與媒體處理 / 冪等` | 一次開多張單的寫入，另在業務交易內以 `pg_advisory_xact_lock` 鎖鍵、在同一個交易裡寫 `processed_ops`。 | 現有過濾器在提交後另寫紀錄、同鍵並發不擋，達不到 FR-036、SC-007 的重複 0 次；technical-research 決策 3。 |
| ADD | `specs/truth/techstack.md` -> `技術堆疊總覽 / 測試與驗證 / 櫃檯 Web 送出失敗的模擬` | Playwright `route.abort`、`route.fetch()` 後中斷、資料庫暫時觸發器三種模擬。 | 驗收全有或全無與回放不重複（FR-035、FR-036、FR-040）；technical-research 決策 8。 |
| MODIFY | `specs/truth/techstack.md` -> `技術堆疊總覽 / 測試與驗證 / Web 單元測試` | 用途的例子補上代開單的純計算（張數、摘要、單標題、房號過濾、建議房號、類似單比對、冪等鍵保留規則）。 | 007 的純計算照既有策略只用 `Vitest` 測。 |
| NOOP | `specs/truth/techstack.md` -> `系統有哪些端`、其餘前端／後端／資料分類、`本次開發不引入的技術` | 已檢查，沿用 004、005 的技術棧，沒有新增或排除任何套件、端與 BDD runner。 | 007 只在既有櫃檯 Web 與 `tms` 上加功能；三題必問題已由 techstack truth 寫明，本輪沒有改判。 |

## /api-plan

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `specs/truth/contracts/openapi.yaml` -> `POST /desk/requests`（`deskCreateRequests`，`groups/現場作業/櫃檯看板與派單.yaml`） | 新增櫃檯代開單：一個房號、品項鍵＋數量、選填其他需求（文字＋部門）；每品項一張、全部 `pool`、`desk`＋`desk_forward`、不經 `triage`；標題由後端依品項鍵組（繁中）；同一交易全有或全無；必帶 `Idempotency-Key`（缺回 400 `idempotency_key_required`），交易鎖＋交易內記冪等、同鍵並發第二個等第一個後回放、4xx 也記下、回放帶 `Idempotency-Replayed`；成功 201；錯誤 403 `forbidden`、404 `not_found`（房號不是客房，含公共區域佔位房，context 帶 `roomNo`）、422 `validation_failed`（details 逐項）、401 `unauthorized`；不算住客每房額度、不回 429。 | 介面 7；FR-011、FR-016、FR-017、FR-022、FR-023、FR-034 至 FR-040；research 決策 2、3、7。 |
| ADD | `specs/truth/contracts/openapi.yaml` -> `GET /desk/rooms`（`listDeskRooms`） | 新增代開單面板的房間清單：每間客房房號、是否在住、在住住客（姓名、VIP、入住／退房日期）；排除公共區域佔位房；在住依訂房副本認定（與 `DeskTask.guestName` 同一規則），住期不重疊約束保證每間最多一位；三種櫃檯角色可讀。不重做 `listRooms`。 | 介面 8；FR-005、FR-025 至 FR-027；research 決策 5；ui-plan 待決 5（同房兩筆在住）由既有不重疊約束解決。 |
| ADD | `specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskMenuKey` | 12 個代開品項鍵（中文品名）列舉，並寫明各鍵的部門、類型、上限、標題組法與工務品項的修繕分類；前端照抄。 | ui-plan「請在契約裡列出這 12 個鍵」；data-plan 的品項鍵決定。 |
| ADD | `specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskCreateRequestsRequest`、`DeskCreateRequestItem`、`DeskCreateRequestOther`、`DeskCreateRequestsResult` | 代開單請求與回應形狀；回應為 `serverTime`＋這次開出的 `DeskTask` 陣列（1–13 張，品項依 `DeskMenuKey` 順序、其他需求在最後）；給工務的其他需求文字同時是標題與工務單 description。 | 介面 7；research 決策 7（回應帶卡片）；ui-plan 送出段。 |
| ADD | `specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskRoom`、`DeskRoomGuest` | 房間清單的一間房與在住住客形狀（`guest.name` 可為 null，照訂房副本）。 | 介面 8。 |
| MODIFY | `specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskTask.menuKey` | 看板卡片加可為 null 的品項鍵（讀 `tasks.note.menu_key`）；值不限 12 鍵（住客選單項目也會出現）。連帶出現在 `getDeskBoard`、`readDeskTask`（`DeskTaskDetail`）、`deskAssignTask`、`deskUndoAssign`、`deskAnswerTask`、`deskCreateRequests` 的回應與 `StaffStreamTask.card`。只增欄位，既有欄位不變。 | 介面 8；FR-030；research 決策 4。 |
| MODIFY | `specs/truth/contracts/openapi.yaml` -> tags `櫃檯看板與派單 API`、`任務 API` | 描述補上房間清單讀取與代開單寫入（櫃檯寫入由三個變四個，都必帶冪等鍵）。 | FR-038。 |
| MODIFY | `specs/truth/contracts/components/schemas/shared.yaml` -> `Error.description`（`idempotency_key_required` 與代開單錯誤） | `idempotency_key_required` 涵蓋四個櫃檯寫入；代開單不另立 code，沿用 `validation_failed`、`not_found`。 | FR-040 錯誤對照。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `createRepairTicket` 描述；`components/schemas/工務單.yaml` -> `CreateRepairTicketRequest.description` | 只補說明、行為不變：寫明「照片必填、路徑②③依部門決定來源、角色放行（值班主管不能呼叫）」只屬於本端點；櫃檯轉單（`deskAssignTask`）與櫃檯代開（`deskCreateRequests`）是另兩條開工務單的路徑，共用取號、急件判定與推播步驟，但規則各自決定，避免兩個權威來源。 | 介面 9；research 決策 2（不放寬 `POST /repair-tickets`）；plan 風險 1。 |
| MODIFY | `specs/truth/contracts/groups/住客與房間/住客掃碼.yaml` -> `createGuestRequest` 每房額度；`components/responses.yaml` -> `TooManyRequests` | 額度改為只數住客送的需求（`source=guest`）；櫃檯代開的單不算、不佔名額。另寫明勾選單與命中帶 `menu_key` 的規則時 note 記 `menu_key`。待 `refactor/main_comments_and_workarounds` 合併時，資料庫計數條件 `source IN ('guest', 'desk')` 要改成只數 `guest`（main 上的記憶體計數已只在住客送需求時呼叫）。 | 介面 10；FR-037、SC-011；research 決策 6。 |
| MODIFY | `specs/truth/contracts/components/schemas/租戶設定.yaml` -> `tenant_config.value`、`tenant_configRequest.value` | 描述補上 `request_menu`、`classification_rules` 的值形狀指向新 schema。 | 介面 10；research 決策 4。 |
| ADD | `specs/truth/contracts/components/schemas/租戶設定.yaml` -> `RequestMenuItem`、`ClassificationRule`（含選填 `menu_key`） | 兩個設定 JSON 值的形狀（欄位名照存放的 snake_case）；`ClassificationRule.menu_key` 選填，命中時寫進 `tasks.note.menu_key`、看板以 `DeskTask.menuKey` 帶出；沒填不影響分流，舊設定不必改。 | 介面 10；FR-030 第三種「住客打字但已對應到品項」；驗收例 220「冷氣不冷」。 |
| NOOP | `specs/truth/contracts/groups/現場作業/員工事件串流.yaml` -> `staffStream`、`StaffStreamTask`；`components/schemas/shared.yaml` -> `PushPayload` | 已核對：代開每張單的 `created` 事件照既有規則送 task 訊號（櫃檯角色收全館＋卡片、房務工務收自己部門），推播沿用 `pool_new_request` 與急件推播；不新增訊號種類或推播事件。卡片因 `DeskTask.menuKey` 多一欄已記在上面。 | NFR-003；ui-plan 事件串流段。 |
| NOOP | `specs/truth/contracts/groups/住客與房間/房間.yaml` -> `listRooms`、`readRooms`；`components/schemas/工務單.yaml` -> `RepairTicket`、`RepairCategory`；`components/parameters.yaml` -> `IdempotencyKey` | 已檢查：房間清單另開在 `/desk/**`，不動房況板端點；修繕分類五值不變；冪等鍵參數沿用。`RepairTicket`（工務單詳情）沒有 title 欄位，本輪不加（見下方風險）。 | 決策 5；spec「值域不動」。 |

資料模型缺口（交 Wave 3 `/data-plan`）：無。契約用到的每一項在 `data-model.dbml` 都有來源（`rooms.room_no`、`reservations_cache` 的 `guest_name`／`attrs.vip`／`checkin_at`／`checkout_at`／`superseded_at`、`tasks.note.menu_key`、`classification_rules.menu_key`、`processed_ops` 交易內寫入、`repair_tickets` 櫃檯代開段）。

已處理：工務 App 的工務單詳情頂部標題用 `description`（`app/mobile/lib/features/ticket/ticket_detail_engineering.dart:222-228`），代開的工務單由後端把與標題相同的文字寫入 `description`，詳情頂端看得到「冷氣有問題」與其他需求原文，App 不改。

## /data-plan

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| MODIFY | `specs/truth/data/data-model.dbml` -> `tenant_config` Note（`classification_rules`、`request_menu` 的值形狀） | 寫明 `request_menu` 每項 `{key, label, dept, type}`、`key` 是中文品名；`classification_rules` 每條 `{rule_id, keywords, dept, type, menu_key?}`，新增選填 `menu_key`（品項鍵），比對到時寫進 `tasks.note`；沒填不影響分流，舊設定不必改；12 個代開品項是程式固定清單、不存本表。 | FR-030 第三種「住客打字但已對應到品項」需要資料來源；research 決策 4。存在設定 JSON，不需 migration。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `tasks.note` | 加兩種 JSON 形狀：櫃檯代開品項（含工務品項）存 `{menu_key, quantity}`，與住客勾選單相同（工務 quantity 一律 1）；住客打字比對到帶 `menu_key` 的規則時另記 `menu_key`（與 `rule_id` 並存）。代開的其他需求 note 為 NULL。看板「對應品項」讀 `menu_key`。 | FR-030、spec「關鍵實體」單對應的品項；research 決策 4。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `tasks.task_no` | 寫明櫃檯代開的工務品項與給工務的其他需求屬「工務單開單路徑直接建成 type=repair」，`task_no` 為 NULL，只有 `W-`。 | FR-016、FR-023 只要 `W-`；`TaskEngine.java:86` 對 type=repair 不取 T-；沿用 tasks Note 單號段「工務單不領 T-」。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `tasks.source` | `desk` 的語意寫明：櫃檯畫面代開（三種櫃檯角色、不看部門）與櫃檯部門直開工務單。值域不變。 | FR-011、FR-016、FR-034。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `tasks.pool_source` | 櫃檯代開的單（房務與工務）建單即進池，記 `desk_forward`。值域不變。 | FR-016 要求工務單 `desk_forward`；房務代開沿用同一個「櫃檯」來源標籤，與工務池由 `source=desk` 換算的結果一致（`TaskExecutionService.java:523`）。 |
| ADD | `specs/truth/data/data-model.dbml` -> `tasks` Note「櫃檯代開」段 | 一次送出的開單規則：每品項一張、直接 pool、不經 triage、無 assignee；四種單的 dept／type／task_no／title／note；12 個品項鍵（毛巾、浴巾、礦泉水、拖鞋、牙刷組、嬰兒床、加床、冰塊、冷氣、熱水、燈具、電視）與「同一個品項＝menu_key 相等」；同一交易全有或全無、取號回滾不跳號；歷程由 `created` 快照（`source=desk`、沒有 `triaged`）組出，不加事件型別；不算住客每房額度。 | FR-011、FR-013、FR-016、FR-017、FR-022、FR-023、FR-030、FR-035、FR-037、FR-039；research 決策 2、4、6。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_tickets.reporter_id` | 櫃檯代開時報修人是送出的那一位（`frontdesk`、`manager`、`duty_manager`），不看部門。 | FR-016、FR-034、FR-039。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_tickets` Note 照片要求 | 「所有照片 uploaded 才能建立」改成依開單路徑：工務單開單端點維持至少 1 張；櫃檯轉單與櫃檯代開不要求照片。 | FR-016 使用者裁決（代開工務單免照片）；既有端點行為不變（plan 風險 1）。 |
| ADD | `specs/truth/data/data-model.dbml` -> `repair_tickets` Note「櫃檯代開」段 | 代開工務單：同交易新建任務並開一列，W- 取號、reporter 為送出者、description 寫入與標題相同的文字（工務品項「{品名}有問題」、其他需求為櫃檯打的文字）、source_task_id 為 NULL、origin=reported、免照片；category 對照冷氣→`air_conditioning`、熱水／燈具→`plumbing_electrical`、電視→`furniture_equipment`、其他需求→`other`；來源一律 `desk`＋`desk_forward`；急件照既有規則。品項鍵記在 `tasks.note`，本表不另存。 | FR-016、FR-017、FR-018、FR-023。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_tickets` Note「工務直開欄位對應」 | 「呼叫者部門決定 tasks.source」限縮為只適用工務單開單端點；櫃檯轉單與櫃檯代開的來源由開單路徑決定。 | FR-016 使用者裁決（不看開單人部門）；plan 落差 4。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_tickets.origin` | `reported` 的涵蓋路徑補上櫃檯轉單與櫃檯代開。值域不變。 | 代開工務單計入工務首頁報修計數，不算定期保養。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `processed_ops` Note | 補寫入時機：一般寫入由過濾器在提交後另寫；櫃檯代開單在業務交易內取 `pg_advisory_xact_lock`、鎖內查、同交易寫入，過濾器之後再寫以 `ON CONFLICT DO NOTHING` 略過。表結構不變。 | FR-036、SC-007；research 決策 3。 |
| NOOP | `specs/truth/data/data-model.dbml` -> `tasks` 各 CHECK 值域、`task_events.type`／`payload`、`repair_tickets.category`、`task_counters`、`repair_ticket_counters`、索引 | 已檢查：不新增狀態、部門、類型、來源、來源分流、事件型別、修繕分類或索引；`created` 快照已含 `source`、`state`、`dept`、`title`、`note`（`TaskEngine.java:508-510`），足以組出代開歷程與重放 `menu_key`；不需要 migration（仍停在 V29）。 | spec「對既有行為的影響」：值域不動；research 決策 4 選 note JSON 不加欄位。 |

## /dsl-refine

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `specs/truth/features/frontdesk-web/代開單/dsl.md` | 新模組 DSL：面板外觀、12 品項表、摘要組法；Given 房間與住客、打開面板（空白／帶房號／帶品項／其他需求）、剛代開、房號被移除、帳號改角色、登入逾時、後端開單出錯、內容不合規定、網路中斷與回應遺失三種送出失敗；When 房號欄（依序打、輸入、刪一碼、點建議）、點品項、按品項底下的鈕、其他需求（打字、空白、超量貼上、清空、改部門）、點遮罩、按送出鈕、連按；Then 面板與遮罩、房號欄、品項樣子、無障礙名稱、建議房號、住客卡、空房提示、其他需求、類似單提醒、摘要、送出後畫面（換頁、高亮 2.6 秒、卡片、無浮條）、後端開出的單、內容保留、回登入頁、只有工具列有代開單、介面語言、兩種尺寸與 44 pt。 | 使用者故事 1 至 5、FR-001 至 FR-040；ui-plan 畫面 1 至 5。 |
| ADD | `specs/truth/features/frontdesk-web/代開單/入口與空白面板.feature` | 工具列兩頁都能打開、唯一實心主按鈕、空白面板內容與順序、只有工具列有入口、詳情面板開著時預填房號、三種角色面板相同。 | FR-001 至 FR-003、FR-028、FR-033、FR-034；決策：iPadOS 26 ✕ 圓鈕（無障礙名稱「取消」）。 |
| ADD | `specs/truth/features/frontdesk-web/代開單/關閉不確認也不保留.feature` | ✕ 與遮罩直接關、不確認、不開單；再打開是空白；不關底下的詳情面板；預填只在那一次。 | FR-014、FR-028。 |
| ADD | `specs/truth/features/frontdesk-web/代開單/房號欄與住客卡.feature` | 只收半形數字、最多 4 碼；空白與打開頭時最多 6 筆住客房建議；點建議出住客卡；VIP 標；刪成無效時住客卡消失；空房提示可送出；不存在的房號什麼都不顯示、不能送、沒有紅字。 | FR-004、FR-005、FR-025 至 FR-029。 |
| ADD | `specs/truth/features/frontdesk-web/代開單/常用品項與送出鈕.feature` | 徽章與步進器、一品項一張、減到 0、上限 2 與 9、冰塊摘要不帶單位、工務品項「移除」與只開一張、摘要一段與兩段、房號無效或沒選時的送出鈕、無障礙名稱。 | FR-006 至 FR-010、FR-015、FR-024。 |
| ADD | `specs/truth/features/frontdesk-web/代開單/其他需求.feature` | 標籤與提示字、有字才出現「這段要給」且預設房務、給房務開 `other` 一般單、改給工務的摘要與工務單（`other`、說明同標題）、排在所選部門段最後、空白不算、60 字上限無計數、去前後空白、英文介面照原文。 | FR-019 至 FR-021、FR-024；決策：工務單 description 同標題。 |
| ADD | `specs/truth/features/frontdesk-web/代開單/類似單提醒.feature` | 同房未結束且對應品項相同（櫃檯代開、住客勾選、住客打字已對應）時提醒；不擋送出；負責人與「剛剛建立」、新到舊；其他需求有字時列住客打字單；已完成、中斷、延後中、別間房不提醒；房號品項其他需求一改立刻重算。 | FR-030 至 FR-032。 |
| ADD | `specs/truth/features/frontdesk-web/代開單/送出成功回到看板.feature` | 面板關、換到總覽看板、清篩選與搜尋、關詳情；新卡片在待認領欄高亮 2.6 秒；沒有浮條與復原；卡片來源「櫃檯開單」；後端房務單 `pool`、`desk`、`T-`；冰塊「桶」；工務單 `W-`、修繕分類、說明同標題、報修人、`desk_forward`；主管與值班主管結果相同；詳情「櫃檯代開（{姓名}）」「等同仁認領」與歷程第一句。 | FR-011 至 FR-013、FR-016 至 FR-018、FR-034、FR-039；決策：工務品項開 W-、三角色可代開。 |
| ADD | `specs/truth/features/frontdesk-web/代開單/送出失敗與重送.feature` | 四種「沒有送出」提示文字、按「好」後內容全留且可再按、網路恢復後再送不重複、回應遺失後再送拿同一次結果、送出中不能再按、登入逾時回登入頁、任一張開不成全不留。 | FR-035、FR-036、FR-040；決策：全有全無、失敗保留內容與四種錯誤文字。 |
| ADD | `specs/truth/features/frontdesk-web/代開單/兩種尺寸與四種語言.feature` | 1280 × 720 與 1180 × 820 下面板內捲動、送出鈕看得到；可點的東西 44 pt；沒有只靠游標的東西；滑鼠與觸控都能送出；日文介面文字；標題一律繁體中文。 | NFR-001、NFR-002、SC-009、SC-012、SC-013。 |
| MODIFY | `specs/truth/features/frontdesk-web/共用外框/尚未開放的入口.feature` -> Rule「「通知板」與「遺失物」照原型畫出來，點了沒有反應，也不會改到任何資料」 | Rule 名拿掉「代開單」，`例子` 刪掉「代開單」一列；「通知板」「遺失物」兩列與其他 Rule 不變。 | FR-001、FR-038：代開單開放（使用者已定案）。 |
| MODIFY | `specs/truth/features/frontdesk-web/共用外框/dsl.md` -> 模組說明 | 說明尚未開放的入口只剩側欄「通知板」「遺失物」，代開單見 `代開單/`。句型不變。 | 同上。 |
| MODIFY | `specs/truth/features/frontdesk-web/dsl.md` -> `Given: 全館有以下的單：` | DataTable 加選填欄 `對應品項`（寫進 `tasks.note.menu_key`）；其他欄與預設值不變。 | 類似單提醒要能準備帶品項鍵的單（FR-030）；只增不改。 |
| MODIFY | `specs/truth/features/frontdesk-web/dsl.md` -> `Given: 接下來的送出會因為網路中斷而失敗` | 斷線的請求加上代開單（`deskCreateRequests`）；派單、復原、當場回答照舊。 | FR-040 網路中斷的提示。 |
| MODIFY | `specs/truth/features/frontdesk-web/單筆詳情/dsl.md` → `specs/truth/features/frontdesk-web/dsl.md` -> `Then: 詳情面板「內容」的 "{列名}" 是 "{內容}"` | 權威位置搬移，語意不變。 | 代開單模組也要驗詳情的「來源」「負責人」。 |
| MODIFY | `specs/truth/features/frontdesk-web/單筆詳情/dsl.md` → `specs/truth/features/frontdesk-web/dsl.md` -> `Then: 詳情面板的歷程最新一筆是 "{句子}"，操作者是 "{操作者}"` | 權威位置搬移，語意不變。 | 代開單模組也要驗歷程第一句。 |
| MODIFY | `specs/truth/features/frontdesk-web/帳號與登入/dsl.md` → `specs/truth/features/frontdesk-web/dsl.md` -> `Given: "{員工}" 已登入櫃檯畫面，介面語言是 "{語言}"，停在 "{頁}"` | 權威位置搬移，語意不變。 | 代開單模組的英文、日文介面案例。 |
| MODIFY | `specs/truth/features/frontdesk-web/共用外框/dsl.md` → `specs/truth/features/frontdesk-web/dsl.md` -> `Then: 這個畫面上每一項內容與每一顆可以按的東西，不用游標移上去就看得到、按得到` | 權威位置搬移，語意不變。 | 代開單面板的觸控驗收。 |
| ADD | `specs/truth/features/frontdesk-web/即時更新/現場變化即時跟上.feature` -> Rule「另一台櫃檯代開的單，這一台的看板不用重新整理就多出那幾張待認領的卡片」 | 新增一條 Rule；既有 Rule 與例子不變。 | NFR-003。 |
| ADD | `specs/truth/features/frontdesk-web/即時更新/dsl.md` -> `When: "{同事}" 在另一台櫃檯代開 "{房號}" 的以下單：` | 以同事身分在後端呼叫 `deskCreateRequests`。 | 同上。 |
| NOOP | `specs/truth/features/frontdesk-web/共用外框/側欄與工具列.feature`、`總覽看板/卡片內容.feature`、`單筆詳情/詳情面板內容.feature`、`單筆詳情/歷程.feature` | 已檢查：工具列已有「代開單」；「櫃檯開單」「櫃檯代開（{姓名}）」「櫃檯代開「{標題}」，進{部門}待認領」已定案，不需修改。 | FR-001、FR-013。 |
| ADD | `specs/truth/features/backend/dsl.md` -> `When: "{櫃檯}" 代開 "{房號}" 的以下單：`、`Given: "{櫃檯}" 已代開 "{房號}" 的以下單：`、`Given: 飯店的客房有 {房號清單}` | 代開單的命令與「已代開」狀態（品項列、其他需求列；權威落地含 `pool`、`desk`＋`desk_forward`、T-／W- 規則、`created` 單筆、工務單欄位、`processed_ops` 同交易、全有全無、錯誤依序 403／422／404）；客房準備。放介面根是因為櫃檯看板與派單、工務單、員工事件串流三個模組都用。 | 介面 7、9；FR-011、FR-016、FR-017、FR-022、FR-023、FR-034 至 FR-039。 |
| MODIFY | `specs/truth/features/backend/推播/dsl.md` → `specs/truth/features/backend/dsl.md` -> `Given: 住客 10 分鐘內已在 "{房號}" 送出 {筆數} 筆需求`、`When: 住客在 "{房號}" 掃碼要 "{品項}"`、`Then: 這次請求被擋下，提示住客洽櫃檯` | 權威位置搬移，語意不變。 | 櫃檯看板與派單模組的住客額度案例也用同一契約。 |
| MODIFY | `specs/truth/features/backend/dsl.md` -> `Then: 這次操作被拒絕，錯誤碼為 "{錯誤碼}"` | 參數說明的例子補上代開單的 `idempotency_key_required`、`validation_failed`、`not_found`；驗證語意不變。 | 介面 7 的錯誤對照。 |
| MODIFY | `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> 模組說明、用語對照、`Given: 以下房間現在有住客：` | 端點由六個變八個（`listDeskRooms`、`deskCreateRequests`）；用語補代開單的類型與「對應品項」；入住日、退房日多收昨天／今天／明天／後天，原本的 `M/D` 照舊。 | 介面 7、8；房間清單案例不依賴固定日期。 |
| ADD | `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> 代開單相關句型 | Given：後端出錯一張沒開、開某標題會出錯、住客已勾選／已打字、帶品項的分類規則、公共區域佔位房、不算在住的訂房。When：其他需求字串／字數、什麼都沒選、不帶鍵、同鍵同內容再送、同鍵同時送兩次、讀房間清單。Then：依序開出、回應卡片與看板相同、歷程只有建立、失敗不留且不跳號、回放、並發只開一次、60 字標題、住客開單成功、原有單不變、房間清單、卡片對應品項。 | 介面 7、8、10；research 決策 3、4、5。 |
| ADD | `specs/truth/features/backend/櫃檯看板與派單/櫃檯代開單.feature` | 每品項一張與標題、冰塊「桶」、工務品項 W-、混合送出、其他需求給房務／工務、其他需求排最後、去前後空白、歷程只有建立、回應卡片、三角色可代開、房務工務老闆 403、不改既有單。 | FR-011、FR-016、FR-019 至 FR-023、FR-034、FR-038、FR-039。 |
| ADD | `specs/truth/features/backend/櫃檯看板與派單/代開單的檢查與拒絕.feature` | 房號 999 與公共區域 404；品項不在清單、超上限、0 份、重複、部門不對、空白、61 字 422（60 字可以）；什麼都沒選 422；沒帶鍵 400。 | 契約 `deskCreateRequests` 檢查與錯誤；FR-040。 |
| ADD | `specs/truth/features/backend/櫃檯看板與派單/代開單全有全無與重送.feature` | 工務單出錯時毛巾也不留、流水號不跳；同鍵再送回放；同鍵並發只開一次；後端出錯後同鍵再送照常開單。 | FR-035、FR-036、SC-007；research 決策 3。 |
| ADD | `specs/truth/features/backend/櫃檯看板與派單/代開的單不算住客額度.feature` | 住客額度滿時代開成功；代開三張後住客仍能送第 3 筆；住客自己第 4 筆照舊被擋。 | FR-037、SC-011；決策：代開不算住客額度。 |
| ADD | `specs/truth/features/backend/櫃檯看板與派單/代開單的房間清單.feature` | 列出全部客房含空房與在住住客（姓名、VIP、入住退房日）；排除公共區域佔位房；已退房／未入住／已取代不算在住；主管與值班主管可讀；房務工務老闆 403。 | 介面 8；FR-005、FR-025 至 FR-027。 |
| ADD | `specs/truth/features/backend/櫃檯看板與派單/看板卡片的對應品項.feature` | 代開品項單（含工務）帶品項鍵、其他需求沒有；住客勾選帶選單鍵；住客打字命中帶品項的規則帶鍵（派給工務後照樣帶）；沒命中沒有。 | FR-030；research 決策 4；決策：品項鍵＝中文品名。 |
| MODIFY | `specs/truth/features/backend/工務單/dsl.md` -> 模組說明 | 補「櫃檯代開」段：依路徑決定規則（三角色、免照片、`desk`＋`desk_forward`、報修人、標題與說明、修繕分類對照）；`createRepairTicket` 的照片、依部門決定來源與角色放行不變。 | 介面 9；plan 風險 1。 |
| ADD | `specs/truth/features/backend/工務單/dsl.md` -> `Then: 代開的工務單如下：` | 驗工務單列（報修人、說明、分類、`origin`、無照片）、任務（`pool`、`task_no` NULL、`desk`＋`desk_forward`、只有 `created`）、`readRepairTicket` 與 `listDeptPool`。 | 同上。 |
| ADD | `specs/truth/features/backend/工務單/櫃檯代開的工務單.feature` | 四個工務品項的分類與標題說明、其他需求給工務是「其他」、不用照片、直接進工務待認領且來源櫃檯、主管（掛工務部門）與值班主管代開來源仍是櫃檯、之後完修與主管驗收照舊結案。 | FR-016 至 FR-018、FR-034、FR-039；決策：工務品項開 W-、description 同標題、三角色可代開。 |
| NOOP | `specs/truth/features/backend/工務單/開立工務單與急件.feature`、`照片完整性與冪等送出.feature`、`櫃檯轉來的工務單.feature` | 已檢查：工務單開單端點與櫃檯轉單的既有例子不動。 | research 決策 2：不放寬 `createRepairTicket`。 |
| MODIFY | `specs/truth/features/backend/員工事件串流/dsl.md` -> 用語對照「訊號」 | 加「建立」＝`created`。既有訊號不變。 | NFR-003。 |
| ADD | `specs/truth/features/backend/員工事件串流/收得到哪些單的訊號.feature` -> Rule「櫃檯代開的單，其他櫃檯與那張單所在部門的同仁都收得到建立的訊號」 | 新增一條 Rule；既有 Rule 與例子不變。 | NFR-003；手機待認領不用重新整理就出現。 |
| NOOP | `specs/truth/features/backend/推播/員工推播事件.feature` | 已檢查：住客第 4 筆被擋的 Rule 不變（三個句型只搬到介面根）。 | FR-037 只改「代開不算」，住客自己的額度規則不變。 |
| NOOP | `specs/truth/features/ios-app/**` | 已檢查：工務待認領的「櫃檯」來源標示與分流磚已有 truth；工務單詳情頂部用 `description`，代開時後端寫入同標題的文字，App 不改。 | plan 前提「手機 App 的畫面不改」；`/api-plan` 已處理段。 |
| MODIFY | `specs/truth/features/guest-web/dsl.md` -> `Given: 櫃檯在 "{時刻}" 替 "{房號}" 房代開了一張單` → `Given: "{房號}" 房的另一支手機在 "{時刻}" 送出了一張單` | 刪除舊句型，改成同一間房另一支手機送出的單：以住客身分用同一枚代碼在後端送出（來源住客、同一筆訂房、算進額度），建立時間是該時刻，不經這支手機的頁面；這支手機的畫面已開著、不重新整理，這一張引發的串流訊號被扣住，清單與估算都不知道它。 | FR-037：代開不算住客額度，舊前提不成立；保留「估算通過、後端拒絕」的覆蓋。 |
| MODIFY | `specs/truth/features/guest-web/設備報修/報修的限流.feature` -> Rule「估算通過但後端拒絕時，顯示同一個提示，不出現成功頁」的例子 | 前提由「櫃檯剛替這間房代開了 1 張」改為「同一間房的另一支手機剛又送了 1 張」，句子移到停在設備報修頁之後；When 與 Then 不變。 | 同上。 |
| MODIFY | `specs/truth/features/guest-web/備品補充/後端在送到一半拒絕.feature` -> 三個例子（第二張被擋、衣架建立礦泉水被擋、這間房已經滿了） | 前提由櫃檯代開改為同一間房的另一支手機在 14:03 送出了一張單，句子移到停在備品補充頁之後；When 與 Then 不變。 | 同上。 |

已處理（原待決）：`specs/truth/features/guest-web/` 以「櫃檯替這間房代開了一張單」製造「住客估算通過、後端拒絕」的 4 個例子與 `dsl.md` 句型，在 FR-037 定案後前提不成立；裁決保留測試意圖，前提改為「同一間房的另一支手機在那個時間送出了一張單」（來源住客、同一筆訂房，算進額度；這支手機的頁面沒重新整理，不知道這筆），改動見上方三列 MODIFY。
