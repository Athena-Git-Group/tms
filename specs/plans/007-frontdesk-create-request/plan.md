# 系統分析規劃：櫃檯代開單

**Plan Package**: `specs/plans/007-frontdesk-create-request`
**Truth Root**: `specs/truth`
**Truth Delta**: `specs/plans/007-frontdesk-create-request/truth-delta.md`
**輸入**: `spec.md`（5 個使用者故事、FR-001 至 FR-040、NFR-001 至 NFR-003、SC-001 至 SC-013，待釐清 0 題）、`research.md`（決策 1 至 8、spike 3 項）、`truth-delta.md`（`/specify` 預期影響、`/technical-research` 正式列）、`features/acceptance/` 下 8 份驗收劇本（49 例）

這份文件回答三件事：代開單這一包要動到哪些畫面、資料與後端介面；這些介面分幾輪分析、每一輪交給哪一個規劃 skill、吃什麼產出什麼；後端現況與需求之間有哪些落差、各由誰處理。它只排分析的工作，不決定欄位名稱、端點路徑或資料表長相，那些由各輪的規劃 skill 決定。

四個前提先講清楚，後面不再重複：

- **畫面以設計圖為準**。設計圖是線上 Design artifact 的 `project/Desk.dc.html`（不在 repo 裡）；資料值（狀態、部門、類型、來源、修繕分類、角色）跟後端現況，本包不新增任何值。
- **spec 與 research 的決定不重開**。工務品項開成真正的工務單（`W-`）、三種櫃檯角色都能代開含工務單、代開的工務單免照片且一律記 `desk`＋`desk_forward`、一次送出全有或全無、重送不重開、代開的單不算住客額度、類似單在前端用看板資料算、房間清單一次讀完在前端判斷，這些都已定，各輪只決定「怎麼寫進契約與資料模型」。
- **PMS 斷線版的代開單不在這一包**。提醒列「掃碼被擋的需求」的代開單鈕與斷線版空房提示，隨 PMS 整合一起做。
- **住客掃碼頁與手機 App 的畫面不改**。代開的單照既有規則出現在房務、工務的待認領。

專案憲章 `.agents/constitution/` 不存在，這一輪略過憲章檢查。

---

## 專案結構

### 文件結構（本功能）

```text
specs/plans/007-frontdesk-create-request/
├── spec.md                      # /specify 產出：需求原文
├── research.md                  # /technical-research 產出：技術決策與後端現況
├── plan.md                      # 本檔，/system-analysis 產出
├── truth-delta.md               # 各 truth owner skill 追加：這一包改了哪些 truth
├── checklists/
│   └── requirements.md          # /specify 產出：需求品質檢查
├── features/
│   └── acceptance/              # /spec-by-example 產出：8 份驗收劇本
└── ui/                          # /ui-plan 產出（Wave 1）：代開單面板的畫面規劃與靜態雛形

specs/truth/
├── techstack.md                 # 已由 /technical-research 補上（冪等加鎖、送出失敗模擬、Vitest 範圍）
├── data/
│   └── data-model.dbml          # /data-plan 更新（Wave 1；Wave 3 視情況再一趟）
├── contracts/
│   ├── openapi.yaml             # /api-plan 更新（Wave 2）：/desk 底下的新路徑
│   ├── components/schemas/      # /api-plan 更新：櫃檯看板與派單、工務單、住客掃碼、租戶設定
│   └── groups/                  # /api-plan 更新：現場作業（櫃檯看板與派單、工務單）、住客與房間（住客掃碼）、營運管理（租戶設定）
└── features/
    ├── backend/                 # /dsl-refine 更新：櫃檯看板與派單（新增代開單）、工務單（開單規則修改）
    └── frontdesk-web/           # /dsl-refine 更新：新增 代開單/ 模組；共用外框/尚未開放的入口 拿掉「代開單」
```

### 原始碼結構（儲存庫根目錄）

```text
app/
├── web/src/                     # 既有：櫃檯 Web
│   ├── api/desk.ts              # 加代開單的寫入與房間清單讀取
│   ├── features/                # 新增 create-request/（面板、純計算、送出）；沿用 dispatch、detail、realtime
│   ├── shell/                   # 工具列「代開單」從尚未開放改成打開面板
│   └── i18n/                    # 四種語言的面板文字
├── web-bdd/                     # 既有：櫃檯 Web 驗收（Cucumber-JVM ＋ Playwright for Java）
└── bdd-support/                 # 既有：共用後端資料準備（暫時觸發器的裝與拆放這裡）

tms/src/main/
├── java/com/athena/tms/
│   ├── controller/              # DeskBoardController（/api/v1/desk）
│   ├── service/                 # DeskBoardService、DeskTaskMapper、TaskEngine、GuestRequestService、
│   │                            # GuestTenantConfigReader、RoomRequestRateLimiter、IdempotencyService
│   ├── repair/                  # RepairService、RepairTicketOpener（開工務單抽成共用步驟）
│   ├── filter/                  # IdempotencyFilter
│   ├── dto/desk/                # DeskTask 與新的代開單請求、回應
│   └── config/                  # SecurityConfig（/api/v1/desk/** 已放行三種櫃檯角色）
└── resources/db/migration/      # 目前到 V29；預期不需要 migration，若 /data-plan 判定要改表則從 V30 接
```

**結構決策**: 不新增模組、測試線或路由。代開單面板是蓋在總覽看板與待派單頁上的面板，程式放 `app/web/src/features/` 下的新資料夾，與詳情、當場回答同一種做法。後端只在 `tms` 既有套件內增修：新寫入與新讀取都放 `/api/v1/desk/**` 底下，沿用既有的三角色放行，不放寬 `POST /api/v1/repair-tickets`；開工務單的規則從 `RepairService` 抽成可帶參數的共用步驟，讓工務直開與櫃檯代開共用。櫃檯 Web 的行為規格新增 `specs/truth/features/frontdesk-web/代開單/`，後端規則寫進既有的 `features/backend/櫃檯看板與派單/` 與 `features/backend/工務單/`。

---

## 分析流程規劃

### 系統介面的盤點

本次需求共盤點出 `10` 個系統介面：5 個畫面介面、1 個資料介面、4 個後端介面。畫面介面依「櫃檯人員在面板上做哪一件事」切；後端介面依「分析時要回答的問題是不是同一類」切：新的寫入、新的讀取、既有工務單開單規則的修改、住客端連帶的修改各一。

#### 畫面介面（承接：`/ui-plan`）

1. **代開單入口、面板外框與關閉**
   - 端點類型：畫面（櫃檯 Web）
   - 主要介面：總覽看板與待派單頁工具列的「代開單」、詳情面板開著時的房號預填、面板標題與左上取消（文字或圖示兩種外觀）、暗色遮罩、底部固定的送出鈕、關閉不確認不保留、關閉不影響底下的詳情面板、兩種視窗尺寸下的捲動、四種語言
   - 需求原文依據：FR-001 至 FR-003、FR-014、FR-028、FR-033、FR-038；NFR-001、NFR-002；SC-009、SC-012、SC-013；驗收劇本「住客來電要備品時代開單直接進房務待認領」的入口與關閉兩條 Rule、「代開單面板在兩種尺寸與四種語言下都能用」
   - 既有 truth：`features/frontdesk-web/共用外框/側欄與工具列.feature`、`尚未開放的入口.feature`、`支援的視窗與輸入方式.feature`
   - truth 變更風險：低。只把「代開單」移出尚未開放的入口，其他入口不動

2. **房號欄、建議房號、住客卡與空房提示**
   - 端點類型：畫面（櫃檯 Web）
   - 主要介面：只收半形數字最多 4 碼的房號欄、最多 6 筆的建議房號、住客卡（頭像、姓名、日期、VIP）、空房提示、無效房號什麼都不顯示
   - 需求原文依據：FR-004、FR-005、FR-025 至 FR-029；SC-003、SC-010；驗收劇本「輸入房號時確認是對的房間與住客」
   - 既有 truth：無；住客卡日期寫法沿用 `features/frontdesk-web/單筆詳情/詳情面板內容.feature` 的「住客」列
   - truth 變更風險：低。但這一區要的資料（房間清單、在住住客的姓名、VIP、入住退房日期）直接決定後端新讀取要回什麼

3. **常用品項、其他需求、送出摘要與送出鈕**
   - 端點類型：畫面（櫃檯 Web）
   - 主要介面：12 格品項（徽章、選取樣子、「−」「+」、工務的「移除」、上限）、無障礙名稱、其他需求 60 字框與「這段要給」分段選擇、送出摘要、「送出 {n} 張單」與能不能按
   - 需求原文依據：FR-006 至 FR-010、FR-015、FR-019 至 FR-021、FR-024；SC-002、SC-003；spec「張數、摘要、標題與提醒的公式」；驗收劇本「住客來電要備品…」的品項 Rule、「住客報修時代開成工務單…」的「移除」Rule、「常用品項沒有的需求用其他需求交給房務或工務」
   - 既有 truth：無
   - truth 變更風險：低。純畫面與純計算

4. **類似單提醒**
   - 端點類型：畫面（櫃檯 Web）
   - 主要介面：提醒的出現時機、每一行「{單標題}：{狀態標籤}[ · {負責人}]，{多久前}建立」、結尾句、不擋送出、房號品項其他需求一改立刻重算
   - 需求原文依據：FR-030 至 FR-032；SC-008；驗收劇本「選品項時提醒這間房已有類似的單」
   - 既有 truth：狀態標籤沿用 `features/frontdesk-web/單筆詳情/狀態標籤與狀態訊息卡.feature`
   - truth 變更風險：低。但它要看板卡片多帶「對應哪個品項」，這是後端讀取與資料模型的需求來源

5. **送出中、送出失敗提示與送出後回到看板**
   - 端點類型：畫面（櫃檯 Web）
   - 主要介面：送出中鈕不能再按、四種失敗提示（房號不存在、帳號不能代開、內容不符、網路或伺服器）、登入逾時回登入頁、失敗後內容全部保留、成功後關面板換到總覽看板並清篩選、新卡片在「待認領」欄高亮 2.6 秒、沒有浮條與復原、卡片來源與歷程沿用 004 文字
   - 需求原文依據：FR-012、FR-013、FR-035、FR-036、FR-039、FR-040；NFR-003；SC-004、SC-005、SC-007；驗收劇本「一次送出全部開成或全部不開且送不成時保留內容」與「住客來電要備品…」的送出後 Rule
   - 既有 truth：提示對話框沿用 `features/frontdesk-web/當場回答/撞單與送出失敗.feature`；來源與歷程文字沿用 `總覽看板/卡片內容.feature`、`單筆詳情/詳情面板內容.feature`、`單筆詳情/歷程.feature`
   - truth 變更風險：低

#### 資料介面（承接：`/data-plan`）

6. **代開的單、工務單與品項對應的資料**
   - 端點類型：資料（`specs/truth/data/data-model.dbml`）
   - 主要介面：`tasks`（`note` 的 JSON 形狀、`source`、`task_no`、`pool_source`）、`repair_tickets`（`reporter_id`、`category`、開單路徑與來源的對應、照片要求）、`task_events`（`created` 的快照）、`tenant_config`（`classification_rules` 與 `request_menu` 的值形狀）、`processed_ops`（寫入時機）
   - 需求原文依據：FR-011、FR-016、FR-022、FR-023（代開的單與工務單長什麼樣）、FR-030 與「關鍵實體」的「單對應的品項」（類似單要知道單對應哪個品項、是不是住客打字的）、FR-036（重送只開一次）、FR-039（記在送出者名下、主管與值班主管也可以是報修人）
   - 既有 truth：`data-model.dbml` 的 `tasks`（`note` 第 184 行、`task_no` 第 174 行、`source` 第 176 行）、`repair_tickets`（第 631 行起，表 Note 末段「呼叫者部門決定 tasks.source」）、`task_events`（第 316、319 行）、`tenant_config`（第 26 行）、`processed_ops`（第 363 行）
   - truth 變更風險：中。欄位與值域預期不動，但 `tasks.note` 的 JSON 形狀是看板、住客頁、手機 App 共用的讀法；`repair_tickets` 表 Note「呼叫者部門決定來源」與「至少 1 張照片」兩條既有規則要改寫成依開單路徑決定

#### 後端介面（承接：`/api-plan`）

7. **櫃檯代開單寫入介面**
   - 端點類型：API（寫入）
   - 主要介面：一個房號一次送出多個品項（含數量）與一段其他需求（含部門）；房務開一般單、工務開工務單；同一個交易全有或全無；強制冪等鍵且交易內加鎖、交易內記下結果；回應帶回這次每一張單的看板卡片；錯誤 `not_found`（房號）、`forbidden`、`validation_failed`、`unauthorized`；三種櫃檯角色可用；建單後的推播與即時訊號走 outbox
   - 需求原文依據：FR-011、FR-016、FR-017、FR-022、FR-023、FR-034 至 FR-040；NFR-003；SC-001、SC-002、SC-006、SC-007；research 決策 2、3、7
   - 既有 truth：`contracts/groups/現場作業/櫃檯看板與派單.yaml`（`deskAssignTask`、`deskUndoAssign`、`deskAnswerTask` 的冪等與錯誤寫法）、`openapi.yaml` 第 215 至 227 行的 `/desk/**`、`components/parameters.yaml` 的 `IdempotencyKey`
   - truth 變更風險：中。全新動作，但與既有派單共用看板卡片 schema 與錯誤回應

8. **代開單面板的讀取介面**
   - 端點類型：API（讀取）
   - 主要介面：這家飯店的房間清單與每間目前在住的住客（姓名、VIP、入住與退房日期），排除公共區域佔位房；看板卡片多帶「對應品項鍵」（可為空），讓前端判斷類似單
   - 需求原文依據：FR-005、FR-025 至 FR-027、FR-030；SC-003、SC-008；research 決策 4、5
   - 既有 truth：`櫃檯看板與派單.yaml` 的 `getDeskBoard`、`components/schemas/櫃檯看板與派單.yaml`；相鄰的 `groups/住客與房間/房間.yaml`、`訂房檔副本與房態匯入.yaml`（`/rooms` 清單在契約有、程式沒有）
   - truth 變更風險：中。看板卡片 schema 加欄位會連帶派單、復原、當場回答的回應

9. **工務單開單規則的修改**
   - 端點類型：API（既有規則修改）
   - 主要介面：開工務單抽成共用步驟後，櫃檯代開這條路徑的照片要求（免）、來源與待認領來源分流（一律 `desk`＋`desk_forward`，不看部門）、標題（「{品名}有問題」或其他需求原文，而非「{分類名} 工務單」）、報修人（三種角色皆可）；既有 `createRepairTicket` 的行為維持不變
   - 需求原文依據：FR-016、FR-017、FR-018、FR-034；SC-006；spec「對既有行為的影響」第 5 列、「設計圖與後端現況的出入」第 2 至 4、11 列
   - 既有 truth：`contracts/groups/現場作業/工務單.yaml` 的 `createRepairTicket`（第 107 至 124 行，三種開單路徑）；`features/backend/工務單/開立工務單與急件.feature`、`櫃檯轉來的工務單.feature`、`照片完整性與冪等送出.feature`
   - truth 變更風險：高。`createRepairTicket` 是手機 App 房務報修與工務自開共用的契約；改寫時員工 App 的照片必填、依部門決定來源、角色放行都不能跟著變

10. **住客端連帶的修改**
    - 端點類型：API（既有規則修改）
    - 主要介面：住客每房額度只數住客送的需求（代開的不算、不受限）；分類規則加選填的品項鍵，住客打字比對到有品項鍵的規則時記下對應品項
    - 需求原文依據：FR-037（額度）；FR-030 第一條的第三種「住客打字但已對應到這個品項」；SC-008、SC-011；research 決策 4、6
    - 既有 truth：`contracts/groups/住客與房間/住客掃碼.yaml`（第 78 行「同一房 10 分鐘內第 4 筆回 429」）；`groups/營運管理/租戶設定.yaml`（分類規則的設定形狀）
    - truth 變更風險：中。額度在 main 上是記憶體計數，`refactor/main_comments_and_workarounds`（已 push、未合併）改成資料庫現數且條件含 `desk`；兩條線的合併順序會影響這一條

### 分析流程的安排

#### Wave 1

- **平行分析介面**：
  - 介面 1 至 5（五個畫面介面）→ 合併成一次 `/ui-plan` 委派。五個介面寫進同一份 `ui/ui-plan.md` 與同一組雛形，分開委派會互相覆寫。
  - 介面 6（資料）→ 一次 `/data-plan` 委派。
- **輸入**：`spec.md`、`research.md`、`truth-delta.md`、`features/acceptance/`、設計圖（線上 Design artifact 的 `project/Desk.dc.html`）、004 的 `specs/plans/004-frontdesk-board-and-dispatch/ui/`（沿用的外框、提示對話框與卡片樣式）；`/data-plan` 另讀 `specs/truth/data/data-model.dbml` 與本檔「後端現況的落差」。
- **輸出**：`ui/ui-plan.md` 與靜態雛形；更新後的 `data-model.dbml` 與 `truth-delta.md` 的 `/data-plan` 段。
- **分析重點**（`/ui-plan`）：
  - 左上取消用文字「取消」還是圖示鈕（iPadOS 18 與 26 兩種外觀），要與 004 已選的外觀一致；面板寬度、圓角、陰影與 1280 × 720、1180 × 820 下的高度與捲動。
  - 新卡片高亮 2.6 秒的樣子，與 004 拖曳派單、即時更新的卡片變化怎麼區分。
  - 面板逐區要哪些資料：房號區要房間清單與在住住客的哪些欄位；類似單提醒要看板卡片的哪些欄位（房號、狀態、標題、負責人、建立時間、對應品項鍵、是否住客打字）。請列成清單，Wave 2 拿它對後端回傳。
  - 房間清單讀取中與讀取失敗時，房號區怎麼呈現（spec 沒有寫，只能沿用 004 的載入與失敗做法，不能新增會擋送出的規則）。
  - 品項格子在四種語言下的品名與部門字是否放得下；「冰塊 ×2」與標題「冰塊 ×2 桶」的單位只在單標題出現。
  - 送出中的鈕長什麼樣；四種失敗提示沿用 004「沒有送出」對話框的哪一個元件。
  - 「多久前」的時鐘：沿用 004 等待時間的校正做法，不要出現負數。
- **分析重點**（`/data-plan`）：
  - 櫃檯代開的一般單在 `tasks.note` 記什麼：沿用住客勾選單的 `menu_key`＋`quantity` 形狀（research 決策 4），其他需求的單不記；`note` 欄位說明裡「住客打字／住客點選單」兩種形狀要加上櫃檯代開這一種。
  - 櫃檯代開的工務單（`type=repair`、`dept=engineering`）在 `tasks` 上有沒有 `T-`：`task_no` 說明寫「工務單開單路徑直接建成 type=repair 的單為 NULL」，代開的工務單是否同屬這一類、只顯示 `W-`（spec FR-016 只要求 `W-`）。它的 `menu_key` 記在 `tasks.note` 還是 `repair_tickets.description`，看板讀取時從哪裡讀得到。
  - `repair_tickets` 表 Note「呼叫者部門決定 tasks.source」與「所有照片 uploaded 才能建立」改寫成依開單路徑決定：櫃檯代開路徑一律 `source=desk`、`pool_source=desk_forward`、不要求照片；`reporter_id` 可以是 `frontdesk`、`manager`、`duty_manager`。
  - `tenant_config.classification_rules` 的值形狀加選填的品項鍵；住客打字比對到有品項鍵的規則時，`tasks.note` 加記 `menu_key`（與既有 `rule_id` 並存）。品項鍵的命名沿用住客選單的中文鍵（research 決策 4），`request_menu` 預設五項以外的品項（浴巾、冷氣等）怎麼取鍵。
  - `created` 事件快照要不要多帶「這是櫃檯代開」的標記，還是 `source=desk`＋沒有 `triaged` 就足以讓歷程寫出「櫃檯代開「{標題}」，進{部門}待認領」（004 已定案的文字，確認資料來源不變）。
  - `processed_ops` 對這個端點改成在業務交易內寫入（research 決策 3）：表說明要不要補「有的端點在交易內寫、與業務資料同進退」。
  - 一次送出最多 13 張單，`task_counters` 與 `repair_ticket_counters` 回滾時不跳號（research 決策 2），確認資料層說明一致。
- **安排理由**：畫面規劃與資料模型彼此不依賴，可以同時進行；research 已決定類似單的品項對應記在 `note` 的 JSON 裡、不改表，資料模型不必等畫面。兩者都是 Wave 2 的輸入：後端要回什麼取決於畫面要什麼與資料存在哪裡，先定這兩件，契約才不會改兩次。

#### Wave 2

- **平行分析介面**：介面 7、8、9、10（四個後端介面）→ 合併成一次 `/api-plan` 委派。四個介面都寫進 `specs/truth/contracts/`，共用看板卡片 schema、錯誤回應與工務單 schema，分開委派會互相覆寫。
- **輸入**：Wave 1 的 `ui/ui-plan.md`（每一區要的資料清單）與更新後的 `data-model.dbml`；`research.md` 決策 2 至 7 與「後端為這一包要補的事」；本檔「後端現況的落差」；`truth-delta.md` 的 `/specify` 預期列。
- **輸出**：更新後的 `specs/truth/contracts/`（`openapi.yaml`、`components/`、`groups/`）與 `truth-delta.md` 的 `/api-plan` 段；一份「資料模型還缺什麼」的清單（有缺才列）。
- **分析重點**：
  - 代開單寫入（介面 7）：請求怎麼表達「品項鍵＋數量」與「其他需求文字＋部門」；上限、60 字、品項鍵合法、至少一張由後端擋時回什麼（`validation_failed`）；房號查不到回 `not_found`；回應帶回每張新單的看板卡片（與 `getDeskBoard` 同一個 schema）。標題（「{品名} ×{n}」「冰塊 ×{n} 桶」「{品名}有問題」）由後端依品項鍵組，還是前端送標題、後端只驗？spec 要求標題一律繁體中文、不隨介面語言變。
  - 冪等（介面 7）：沿用必帶 `Idempotency-Key`；契約怎麼描述「同鍵並發時第二個等第一個結束後回放」「4xx 也會被記下，前端換鍵」；回放時回應帶 `Idempotency-Replayed`。
  - 房間讀取（介面 8）：放 `/desk/**` 底下還是補實作契約既有的 `/rooms` 清單？research 決策 5 已定放 `/desk/**`、三種櫃檯角色可讀、房務工務讀不到住客資料，請照此寫；排除公共區域佔位房（`AutomationRuleService.PUBLIC_AREA_ROOM_NO`）。
  - 看板卡片的對應品項鍵（介面 8）：欄位放在既有看板卡片上，會一起出現在派單、復原、當場回答的回應；工務單的卡片從哪裡讀到品項鍵（依 Wave 1 `/data-plan` 的決定）。
  - 工務單開單規則（介面 9）：改在 `createRepairTicket` 的契約（第四種開單路徑）還是只在櫃檯代開的動作描述裡寫清楚、`createRepairTicket` 原樣不動？research 決策 2 已定不放寬 `POST /api/v1/repair-tickets` 的角色與照片，請照此寫；但 `createRepairTicket` 描述裡「③櫃檯交辦開單」與本包的關係要說清楚，避免兩個權威來源。
  - 住客額度（介面 10）：`住客掃碼.yaml` 的額度說明改成只數住客送的；`refactor/main_comments_and_workarounds` 的資料庫計數條件 `source IN ('guest', 'desk')` 要改成只數 `guest`，在 truth-delta 註明待該分支合併時處理。
  - 分類規則的品項鍵（介面 10）：`租戶設定.yaml` 的分類規則形狀加選填欄位；若決定不擴充，「住客打字但已對應到冷氣」的驗收例無法通過，必須停下來回報指揮官，不可自行刪改驗收例。
  - 即時更新（NFR-003）：代開的單建立時寫的 outbox 事件，櫃檯事件串流與手機待認領是否已經收得到（004 已讓三種櫃檯角色收全館事件）；預期 NOOP，請核對後在 truth-delta 記 NOOP。
- **安排理由**：後端契約依賴 Wave 1 的兩項產出：卡片與房間讀取要回哪些欄位來自畫面清單，品項鍵記在哪裡來自資料模型。四個後端介面共用 schema 與錯誤回應，合併成一次委派。

#### Wave 3（有條件）

- **平行分析介面**：介面 6（資料）→ 第二趟 `/data-plan`。
- **啟動條件**：Wave 2 的 `/api-plan` 列出資料模型還缺的東西（例如契約需要的欄位、設定鍵或值形狀在 `data-model.dbml` 裡沒有）。清單是空的就跳過這一輪。
- **輸入**：Wave 2 的缺口清單與更新後的契約。
- **輸出**：補齊後的 `data-model.dbml` 與 `truth-delta.md` 的 `/data-plan` 段追加。
- **分析重點**：契約要的每一個欄位在資料模型裡是否都有來源；看板讀取帶出品項鍵時，是否仍走得到既有索引。
- **安排理由**：`/api-plan` 不能改資料 truth，它發現的資料缺口只能回頭交給 `/data-plan`。

#### 三輪之後

1. `/dsl-refine`：把 `features/acceptance/` 的 8 份驗收劇本整理成行為規格。櫃檯 Web 的寫進新的 `specs/truth/features/frontdesk-web/代開單/`，並修改 `共用外框/尚未開放的入口.feature`（拿掉「代開單」）、核對 `側欄與工具列.feature` 與三處來源文字是否 NOOP；後端的寫進 `features/backend/櫃檯看板與派單/`（新增代開單）與 `features/backend/工務單/`（櫃檯代開路徑的照片、來源、標題），以及住客額度所在的後端 feature。
2. `/tasks`：依 truth 與 `research.md` 排出實作工作，含 research 的三個 spike（同鍵並發、暫時觸發器回滾、WebKit 下 `route.fetch()` 後中斷）。

Wave 2 完成之前（Wave 3 若啟動，則連同 Wave 3）不能跑 `/dsl-refine` 或 `/tasks`。

---

## 委派交接

以下是各輪交給規劃 skill 的內容。每一次委派都要帶 plan package、truth root、truth-delta path、介面名稱與分析重點（見上一節）。

| 輪 | 規劃 skill | 介面 | Plan Package | Truth Root | Truth Delta | 主要產物 |
| --- | --- | --- | --- | --- | --- | --- |
| Wave 1 | `/ui-plan` | 1 至 5 | `specs/plans/007-frontdesk-create-request` | `specs/truth` | `specs/plans/007-frontdesk-create-request/truth-delta.md` | `ui/ui-plan.md`、`ui/*.html` |
| Wave 1 | `/data-plan` | 6 | 同上 | 同上 | 同上 | `specs/truth/data/data-model.dbml`、truth-delta `/data-plan` 段 |
| Wave 2 | `/api-plan` | 7 至 10 | 同上 | 同上 | 同上 | `specs/truth/contracts/**`、truth-delta `/api-plan` 段、資料缺口清單 |
| Wave 3（有條件） | `/data-plan` | 6 | 同上 | 同上 | 同上 | 補齊的 `data-model.dbml` |

---

## 後端現況的落差與歸屬

以下逐項對過原始碼。後端路徑省略前綴 `tms/src/main/java/com/athena/tms/`。

| # | 落差 | 現況（檔案:行號） | 歸屬 |
| --- | --- | --- | --- |
| 1 | 櫃檯沒有建單的動作 | `controller/DeskBoardController.java:23-66` 只有讀看板、讀單、常用回覆、派單、復原、當場回答 | `/api-plan`（介面 7） |
| 2 | 一般單與工務單各走各的入口，無法同一個交易開完 | `service/TaskEngine.java:53-102` 的 `create` 可在同一個交易裡帶工務單草稿開出 `W-`（`:100-101` 呼叫 `repair/RepairTicketOpener.java:35-69`）；`repair/RepairService.java` 的直開把照片、來源、標題寫死在方法裡 | 開單共用步驟 → `/api-plan`（介面 9）；交易邊界 → `/api-plan`（介面 7） |
| 3 | 開工務單至少要 1 張照片 | `repair/RepairService.java:661` 回 `photo_required` | `/api-plan`（介面 9）；資料說明 → `/data-plan`（介面 6） |
| 4 | 直開工務單的來源看部門、其他部門被擋 | `repair/RepairService.java:500` 呼叫 `:557-562` 的 `directOpenSource`：`engineering`→`staff`、`frontdesk`→`desk`、其他丟 `forbidden`；`data-model.dbml` 的 `repair_tickets` 表 Note 末段同樣寫「呼叫者部門決定 tasks.source」 | `/api-plan`（介面 9）；表 Note → `/data-plan`（介面 6） |
| 5 | 值班主管進不了開工務單的端點 | `config/SecurityConfig.java:71-72` 的 `POST /api/v1/repair-tickets` 只放 `housekeeping`、`engineering`、`manager`、`frontdesk`；`/api/v1/desk/**` 已放三種櫃檯角色（`:70`） | 走 `/desk/**` 新端點即解決，不放寬既有端點（research 決策 2）→ `/api-plan`（介面 7） |
| 6 | 直開工務單的標題是「{分類名} 工務單」 | `repair/RepairService.java:512` | `/api-plan`（介面 9） |
| 7 | 冪等紀錄在交易提交後另寫，同鍵並發兩個都會做 | `filter/IdempotencyFilter.java:56-61` 寫入失敗只記警告；`service/IdempotencyService.java:24-31`；櫃檯寫入強制帶鍵在 `service/DeskBoardService.java:276-280` | `/api-plan`（介面 7）；`processed_ops` 說明 → `/data-plan`（介面 6） |
| 8 | 看板卡片沒有「對應品項」 | `dto/desk/DeskTask.java:6-35` 有 `guestTyped`（`:20`，由 `service/DeskTaskMapper.java` 推得），沒有品項鍵；住客勾選單的 `menu_key` 在 `service/GuestRequestService.java:120` 寫入 `note` | 卡片欄位 → `/api-plan`（介面 8）；`note` 形狀 → `/data-plan`（介面 6） |
| 9 | 住客打字的單沒有對應品項 | `service/GuestRequestService.java:131` 只記 `rule_id`；分類規則 `service/GuestTenantConfigReader.java:97` 只有關鍵字、部門、類型 | 設定形狀 → `/data-plan`（介面 6）；契約 → `/api-plan`（介面 10） |
| 10 | 沒有房間清單＋在住住客的讀取 | 契約有 `/rooms` 清單但程式沒有（004 plan 落差 12）；公共區域佔位房 `service/AutomationRuleService.java:22` 要排除 | `/api-plan`（介面 8） |
| 11 | 住客額度在兩條分支上算法不同 | main：記憶體計數，只有住客送需求時呼叫（`service/GuestRequestService.java:66`、`service/RoomRequestRateLimiter.java:18-32`），代開不經過，FR-037 在 main 上已成立；`refactor/main_comments_and_workarounds`（未合併進本分支）改成資料庫現數、條件 `source IN ('guest', 'desk')` | `/api-plan`（介面 10）記下規則；合併時的程式調整留給實作 |
| 12 | 住客選單只有預設五項、鍵是中文品名 | `service/GuestTenantConfigReader.java:48` 起（例「毛巾」），`:94` 的 `MenuItem` 沒有上限、單位、修繕分類 | 12 品項是櫃檯固定清單，不讀飯店設定（spec 假設）；對照鍵命名 → `/data-plan`（介面 6）、`/api-plan`（介面 8、10） |

---

## 風險

- **`createRepairTicket` 被連帶改壞**（介面 9）：開工務單抽成共用步驟時，員工 App 的照片必填、依部門決定來源、角色放行都必須維持。`/dsl-refine` 要保留 `features/backend/工務單/` 既有例子不動，實作時跑全套工務單驗收。
- **住客額度的合併順序**（介面 10）：refactor 分支合進 main 時要把條件改成只數 `guest`，否則 `代開的單不算進住客的每房額度.feature` 會紅。
- **分類規則不擴充就過不了一個驗收例**（介面 10）：若 `/api-plan` 或 `/data-plan` 判定不加品項鍵，「住客打字但已對應到冷氣」那一例無法通過，要回報指揮官、由使用者決定，不可自行改驗收例。
- **代開的工務單有沒有 `T-` 號**（介面 6）：資料模型目前寫直開工務單的 `task_no` 為 NULL；若 `/data-plan` 判定代開的工務單也要 `T-`，會與 spec「單號 `W-` 開頭」的畫面顯示規則衝突，需在 Wave 1 就定。
- **看板卡片 schema 加欄位**（介面 8）：派單、復原、當場回答的回應會一起多一個欄位，004 的驗收與前端型別要跟著更新。
