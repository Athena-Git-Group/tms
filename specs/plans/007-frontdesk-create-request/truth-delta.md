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
