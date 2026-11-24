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
| ADD | `specs/truth/data/data-model.dbml` -> `repair_tickets` Note「櫃檯代開」段 | 代開工務單：同交易新建任務並開一列，W- 取號、reporter 為送出者、description 與 source_task_id 為 NULL、origin=reported、免照片；category 對照冷氣→`air_conditioning`、熱水／燈具→`plumbing_electrical`、電視→`furniture_equipment`、其他需求→`other`；來源一律 `desk`＋`desk_forward`；急件照既有規則。品項鍵記在 `tasks.note`，本表不另存。 | FR-016、FR-017、FR-018、FR-023。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_tickets` Note「工務直開欄位對應」 | 「呼叫者部門決定 tasks.source」限縮為只適用工務單開單端點；櫃檯轉單與櫃檯代開的來源由開單路徑決定。 | FR-016 使用者裁決（不看開單人部門）；plan 落差 4。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_tickets.origin` | `reported` 的涵蓋路徑補上櫃檯轉單與櫃檯代開。值域不變。 | 代開工務單計入工務首頁報修計數，不算定期保養。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `processed_ops` Note | 補寫入時機：一般寫入由過濾器在提交後另寫；櫃檯代開單在業務交易內取 `pg_advisory_xact_lock`、鎖內查、同交易寫入，過濾器之後再寫以 `ON CONFLICT DO NOTHING` 略過。表結構不變。 | FR-036、SC-007；research 決策 3。 |
| NOOP | `specs/truth/data/data-model.dbml` -> `tasks` 各 CHECK 值域、`task_events.type`／`payload`、`repair_tickets.category`、`task_counters`、`repair_ticket_counters`、索引 | 已檢查：不新增狀態、部門、類型、來源、來源分流、事件型別、修繕分類或索引；`created` 快照已含 `source`、`state`、`dept`、`title`、`note`（`TaskEngine.java:508-510`），足以組出代開歷程與重放 `menu_key`；不需要 migration（仍停在 V29）。 | spec「對既有行為的影響」：值域不動；research 決策 4 選 note JSON 不加欄位。 |

## /dsl-refine
