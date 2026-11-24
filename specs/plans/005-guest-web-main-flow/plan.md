# 系統分析規劃：住客掃碼頁（主流程）

**Plan Package**: `specs/plans/005-guest-web-main-flow`
**Truth Root**: `specs/truth`
**Truth Delta**: `specs/plans/005-guest-web-main-flow/truth-delta.md`
**輸入**: `spec.md`（8 個使用者故事、FR-001 至 FR-055、NFR-001 至 NFR-006、SC-001 至 SC-012、邊界情況、待釐清 C-01、C-02、C-04、C-05、C-07、C-08）、`research.md`（決策 1 至 9）、`features/acceptance/` 下 10 份驗收劇本、`specs/_inputs/guest-web-findings-2026-11-18.md`（設計圖與後端欄位的對照，下稱「對照」）、設計原型 `Guest.dc.html`

這份文件回答三件事：這一包需求要動到哪些畫面、資料與後端介面；這些介面分幾輪分析、每一輪交給哪一個規劃 skill、吃什麼產出什麼；後端現況與需求之間有哪些落差、各由誰處理。它只排分析的工作，不決定變數名稱、路由的英文段或畫面的尺寸，那些由各輪的規劃 skill 決定。

四個前提先講清楚，後面不再重複：

- **本版不改後端**（FR-054）。所有畫面都用後端現有的住客介面；契約與資料真相這一輪只檢查、不改，預期各記 NOOP。
- **需求以 `spec.md` 為準，畫面以原型為準**。原型有畫、後端沒有資料的地方，`spec.md`「設計圖與後端現況的出入」已逐項裁定；`/ui-plan` 照裁定縮減，不回頭補。
- **資料值跟後端現況**：狀態、部門、類型全寫不縮寫（`triage`、`housekeeping`、`amenity`）。
- **住客頁與櫃檯 Web 互不影響**（FR-055）：同一個程式、不同的路由、store、儲存鍵、標頭。

專案憲章 `.agents/constitution/CONSTITUTION.md` 不存在，這一輪略過憲章檢查（與 001 至 004 相同）。

---

## 專案結構

### 文件結構（本功能）

```text
specs/plans/005-guest-web-main-flow/
├── spec.md                      # /specify 產出：需求原文（含設計圖與後端現況的出入）
├── research.md                  # /technical-research 產出：技術決策 1 至 9
├── plan.md                      # 本檔，/system-analysis 產出
├── truth-delta.md               # 各 truth owner skill 追加：這一包改了哪些 truth
├── checklists/
│   └── requirements.md          # /specify 產出：需求品質檢查
├── features/
│   └── acceptance/              # /spec-by-example 產出：10 份驗收劇本
└── ui/                          # /ui-plan 產出（Wave 1）：ui-plan.md 與靜態雛形

specs/truth/
├── techstack.md                 # 已由 /technical-research 補上住客掃碼頁這一端
├── data/
│   └── data-model.dbml          # /data-plan 檢查（Wave 1）；預期 NOOP
├── contracts/                   # /api-plan 檢查（Wave 1）；預期 NOOP，並記下缺口
└── features/
    ├── backend/                 # /dsl-refine：本版不改後端，預期不動
    ├── frontdesk-web/           # 不動
    └── guest-web/               # /dsl-refine 新增：住客掃碼頁的行為規格（目前還沒有這個目錄）
```

### 原始碼結構（儲存庫根目錄）

```text
app/
├── web/                         # 既有：櫃檯 Web；本包在裡面加住客區
│   └── src/
│       ├── guest/               # 新增：住客掃碼頁（路由、外框、各頁、API、session、純計算）
│       ├── features/realtime/   # 既有：串流解析器與連線狀態機；連線函式抽成可帶網址與標頭
│       ├── i18n/locales/        # 既有：四份語系檔各加 guest 子樹
│       └── router.tsx           # 既有：加 /g/:qrToken 的延遲載入路由
├── web-bdd/                     # 既有：加 phone 瀏覽器設定、住客的步驟與頁面物件、run-feature.sh 的根選擇
├── bdd-support/                 # 既有：BackendChannel 加取 QR 代碼、換 QR、住客評分的 helper
├── bdd/                         # 既有：手機 App 的驗收測試；不動
└── mobile/                      # 既有：員工 App；不動

tms/                             # 既有後端；本包不動（只引用現況）
└── src/main/java/com/athena/tms/
    ├── controller/              # GuestController、GuestStreamController、RoomQrController
    ├── service/                 # GuestScanService、GuestRequestService、StreamSubscriber、StreamConnectionRegistry
    ├── dto/                     # ScanResponse、GuestHomeView、GuestTaskView、GuestMenuItemView
    └── filter/                  # BearerTokenAuthFilter、IdempotencyFilter
```

**結構決策**: 住客掃碼頁是第四個端，程式放在既有的 `app/web/` 裡自己的目錄，用延遲載入的路由樹與獨立外框與櫃檯分開；驗收測試沿用 `app/web-bdd/`，加第三組瀏覽器設定與住客的步驟；行為規格放新的 `specs/truth/features/guest-web/`。後端一行不改（`research.md` 決策 1、2、8）。

---

## 分析流程規劃

### 系統介面的盤點

本次需求共盤點出 `11` 個系統介面：8 個畫面介面、1 個資料介面、2 個後端介面。畫面介面依「住客在做哪一件事」切；後端介面依「分析時要回答的問題是不是同一類」切，請求回應一組、事件推送一組。資料介面只有一個，因為住客頁不新增任何資料。

#### 畫面介面（承接：`/ui-plan`）

1. **掃碼進入、無法使用頁與重試狀態**
   - 端點類型：畫面（住客掃碼頁，`/g/:qrToken` 入口層）
   - 主要介面：打開網址就換 session 進首頁；空房、代碼作廢、代碼不存在統一的無法使用頁；限流、離線、後端出錯的重試狀態；進首頁後被回 401 的靜靜重換
   - 需求原文依據：FR-001 至 FR-005、NFR-001；SC-001、SC-002；邊界情況第 1 至 3 條；待釐清 C-01
   - 既有 truth：無（`specs/truth/features/guest-web/` 尚未建立）；後端規則可參照 `tms/src/test/resources/features/01-guest-requests.feature`（不在 truth 內）
   - truth 變更風險：低。畫面全新

2. **首頁**
   - 端點類型：畫面（住客掃碼頁）
   - 主要介面：頂列（左上字標、房號膠囊、語言膠囊）、「{房號} 號房」與依時段的問候、提問列（即將開放）、「我的需求」區（進度卡／沒有進行中／不出現三態）、兩張主視覺卡、「服務」三列（即將開放）、「住宿指南」清單、緊急狀況提示
   - 需求原文依據：FR-006 至 FR-011、FR-028、FR-048、FR-050、FR-051；SC-003、SC-010；待釐清 C-02、C-07、C-08
   - 既有 truth：無
   - truth 變更風險：低

3. **備品補充與送出成功頁**
   - 端點類型：畫面（住客掃碼頁）
   - 主要介面：項目清單與加減鈕、上限 5、頁底送出鈕的三種文字、送出前的額度估算與提示框、依序一張一張送、送到一半被擋的呈現、成功頁（一項／多項）
   - 需求原文依據：FR-012 至 FR-018、NFR-002；SC-004、SC-005；邊界情況第 5、6 條；待釐清 C-04
   - 既有 truth：無
   - truth 變更風險：低。但「一張一張送」與冪等鍵的做法由 `research.md` 決策 4 定，畫面要照它呈現

4. **設備報修**
   - 端點類型：畫面（住客掃碼頁）
   - 主要介面：六顆單選類別膠囊、補充說明（選填／「其他」必填、200 字、字數）、頁底送出鈕的可按條件、組成自由文字送出、限流提示
   - 需求原文依據：FR-030 至 FR-036；SC-008
   - 既有 truth：無
   - truth 變更風險：低

5. **我的需求清單、單筆需求進度、取消與評分**
   - 端點類型：畫面（住客掃碼頁）
   - 主要介面：清單兩段與空狀態、狀態對照表驅動的標籤／進度／段落／取消鈕／鎖定說明、進度頁的卡片／狀態訊息框／四步進度／各步副文、取消確認框與兩種結果提示、評分卡與送出後的提示、返回回到進來的地方
   - 需求原文依據：FR-019 至 FR-029、FR-037 至 FR-044；SC-006、SC-007；邊界情況第 7 至 10 條；待釐清 C-05
   - 既有 truth：無
   - truth 變更風險：低。但這是對照表的唯一依據，首頁進度卡（介面 2）要與它共用

6. **即時更新**
   - 端點類型：畫面（住客掃碼頁，橫跨首頁、清單、進度頁）
   - 主要介面：收到訊號就重讀、三個畫面跟著變、連線異常時定期重讀且不顯示提示列、恢復後停止
   - 需求原文依據：FR-045 至 FR-047、NFR-003；SC-009；邊界情況第 4 條
   - 既有 truth：`specs/truth/techstack.md` 住客掃碼頁即時更新列（已由 `/technical-research` 寫入）
   - truth 變更風險：低

7. **住宿指南內容頁**
   - 端點類型：畫面（住客掃碼頁）
   - 主要介面：標題列、標題與內文原文（換行照原文）、返回回首頁
   - 需求原文依據：FR-048 至 FR-050；邊界情況最後一條
   - 既有 truth：無
   - truth 變更風險：低

8. **共用外框、子頁標題列、提示浮層與介面語言**
   - 端點類型：畫面（住客掃碼頁）
   - 主要介面：手機版面（單欄、最寬 480、44 點、釘底）、子頁統一的「返回鈕、頁標題、房號 {房號}」、浮在頂端幾秒後消失的提示、語言膠囊與四種語言的選單、第一次打開的語言、深色模式要不要做
   - 需求原文依據：FR-052、FR-053、NFR-004 至 NFR-006；SC-011、SC-012；邊界情況「瀏覽器語言不在四種之內」
   - 既有 truth：`specs/truth/techstack.md` 住客掃碼頁外框與版面、多語系兩列
   - truth 變更風險：低

#### 資料介面（承接：`/data-plan`）

9. **住客視角會讀到的資料**
   - 端點類型：資料（`specs/truth/data/data-model.dbml`）
   - 主要介面：`room_qr_tokens`、`guest_sessions`、`reservations_cache`、`tasks`（住客讀得到的欄位：`type`、`state`、`dept`、`title`、`answer`、`rating`、`created_at`）、`tenant_config` 的 `request_menu` 與 `guest_info_pages`
   - 需求原文依據：關鍵實體全部（房間 QR Code、住客 session、需求（住客視角）、服務項目、資訊頁）；FR-053、FR-054（不向後端要新欄位）；「顯示階段」與「這支手機上的介面語言」是衍生或只在瀏覽器的資料，不進資料真相
   - 既有 truth：`data-model.dbml:112-150`（`room_qr_tokens`、`guest_sessions`）、`tasks`、`tenant_config`
   - truth 變更風險：無。預期 NOOP；只要確認住客頁用到的每個欄位都在資料真相裡有來源

#### 後端介面（承接：`/api-plan`）

10. **住客的請求回應介面**
    - 端點類型：API（住客身分；換 session 公開）
    - 主要介面：`POST /guest/scan`、`GET /guest/home`、`POST /guest/requests`、`GET /guest/requests`、`POST /guest/requests/{id}/cancel`、`POST /guest/requests/{id}/rating`；錯誤 `409 room_vacant`、`401 unauthorized`、`429 rate_limited`、`409 state_conflict`、`422 validation_failed`；`Idempotency-Key`
    - 需求原文依據：FR-001 至 FR-004、FR-015 至 FR-018、FR-034 至 FR-036、FR-039 至 FR-041、FR-043、FR-044、FR-054；「對照」§2
    - 既有 truth：`specs/truth/contracts/groups/住客與房間/住客掃碼.yaml`（只有 `/api/v1/guest-sessions/scan`、`/api/v1/guest-requests` 列出與建立、撤回三個操作）、`components/schemas/住客掃碼.yaml`（`GuestRequest.answer` 等）
    - truth 變更風險：本輪 NOOP（使用者限定）。但契約與程式現況有缺口，見落差表第 1 項

11. **住客事件串流**
    - 端點類型：事件（伺服器推送）
    - 主要介面：`GET /guest/stream` 的事件種類（`init`、`resync`、`task`、`heartbeat`、`degraded`）、住客只收自己訂房的任務事件、`resync.refetch` 是 `/guest/home`、心跳帶 `degraded` 旗標、住客收不到 `recovered`
    - 需求原文依據：FR-045 至 FR-047、NFR-003
    - 既有 truth：契約沒有 `/guest/stream`；`groups/現場作業/員工事件串流.yaml` 是員工那一條（004 補的）
    - truth 變更風險：本輪 NOOP（使用者限定）；缺口同上

### 分析流程的安排

#### Wave 1

- **平行分析介面**：
  - 介面 1 至 8（八個畫面介面）→ 合併成一次 `/ui-plan` 委派。八個介面寫進同一份畫面規劃（`ui/ui-plan.md`）與同一組靜態雛形，分開委派會互相覆寫。
  - 介面 9（資料）→ 一次 `/data-plan` 委派。
  - 介面 10、11（兩個後端介面）→ 合併成一次 `/api-plan` 委派。兩個介面都在 `specs/truth/contracts/`，共用同一批 schema。
- **輸入**：`spec.md`、`research.md`、`truth-delta.md`（`/technical-research` 段）、`features/acceptance/`、「對照」、原型 `Guest.dc.html`（`/ui-plan` 用）；`/data-plan` 另讀 `specs/truth/data/data-model.dbml`；`/api-plan` 另讀 `specs/truth/contracts/**` 與後端的控制器、DTO 現況。
- **輸出**：`ui/ui-plan.md` 與 `ui/*.html`（含共用的樣式與腳本）；`truth-delta.md` 的 `/data-plan` 段（預期 NOOP 列）；`truth-delta.md` 的 `/api-plan` 段（預期 NOOP 列，並以 NOOP 列的摘要記下契約缺口）。
- **分析重點**（`/ui-plan`）：
  - C-01、C-02、C-04、C-05、C-07、C-08 各自的畫面文字或呈現是什麼？沒有依據時給預設並標「預設，待使用者確認」。
  - 每一頁的版面尺寸、設計變數（照「對照」§4 與原型）、深色模式要不要做；393 寬下每一頁的樣子。
  - 狀態對照表（使用者故事 3）怎麼落成首頁進度卡、清單列、進度頁三處共用的呈現；十個狀態值各畫出一種。
  - 備品補充送到一半被擋（C-04）與限流提示框的兩種內文；送出鈕鎖住的樣子。
  - 進度頁的返回回到進來的地方（FR-029）在雛形裡怎麼表現。
  - 語言膠囊的短寫法與選單；即將開放的四個入口的標示。
  - 提示浮層的位置與消失時間（NFR-006）。
  - 可點的東西至少 44 點（NFR-005）：原型哪些元素不到，雛形要補到。
- **分析重點**（`/data-plan`）：
  - 住客頁用到的每一個欄位（`roomNo`、`expiresAt`、服務項目的鍵與名稱、資訊頁的鍵標題內文、需求的七個欄位）在資料真相裡是否都有來源？
  - `room_qr_tokens` 輪替與 `guest_sessions` 連坐撤銷的不變條件是否足以支撐 FR-004（換掉 QR 之後舊 session 失效、舊代碼換不到新 session）？
  - 不新增任何表、欄位、enum；「顯示階段」是衍生值、「介面語言」只在瀏覽器，確認兩者都不該進資料真相。
- **分析重點**（`/api-plan`）：
  - 契約真相目前寫的住客介面（`/api/v1/guest-sessions/scan`、`/api/v1/guest-requests`）與住客頁實際要用的 `/guest/*` 一組之間的對應：哪些操作契約有、哪些沒有（換 session 公開版、首頁資料、評分、串流）。本輪只記錄，不補。
  - 既有契約的 `GuestRequest` schema（含 004 加的 `answer`）與程式的 `GuestTaskView` 是否一致；`cancelGuestRequest` 的 409 條件是否與 `spec.md` 的「只在 `triage`、`pool`」相符。
  - 冪等鍵對住客寫入「有帶才生效」的現況是否足以支撐 NFR-002；本輪不改成強制。
  - 住客串流的事件種類與員工串流的差異（不帶卡片、`resync.refetch` 是 `/guest/home`、沒有 `recovered`、`degraded` 只在消費者落後時送給住客）是否都在 truth 的某處寫明；沒有的話記在缺口裡。
- **安排理由**：三個 planner 彼此不依賴。本版不改後端，所以後端契約不取決於畫面要什麼（畫面只能用現有的）；資料真相也不取決於契約。三者同時做，Wave 1 就是唯一的一輪。

#### 三輪之後（本包只有一輪）

1. `/dsl-refine`：把 `features/acceptance/` 的 10 份驗收劇本整理成行為規格，全部寫進新的 `specs/truth/features/guest-web/`（含介面根 `dsl.md` 與各模組 `dsl.md`）；後端不補規格（FR-054）；`frontdesk-web/`、`ios-app/` 不動。輸入是 Wave 1 的全部產出。畫面文字依 `ui/ui-plan.md`「預設，待使用者確認」的 Example 上方標 `# [need clarification]` 並註明題號（與 004 相同的做法）。
2. `/tasks`：依 truth 與 `research.md` 排出實作工作，含 `research.md`「建立住客區時要先做的 spike」四項、`app/web-bdd/` 的 `phone` 設定與 `run-feature.sh` 的根選擇、`BackendChannel` 的三個 helper。

Wave 1 完成之前不能跑 `/dsl-refine` 或 `/tasks`。

---

## 後端現況的落差與歸屬

以下逐項對過原始碼。後端路徑省略前綴 `tms/src/main/java/com/athena/tms/`。本版不改後端，所以「歸屬」寫的是誰要知道、誰記下來，不是誰去改。

| # | 落差 | 現況（檔案:行號） | 歸屬 |
| --- | --- | --- | --- |
| 1 | 契約真相缺住客手機實際要打的 `/guest/*` 這一組 | 契約只有 `/api/v1/guest-sessions/scan`（櫃檯替住客開 session，要員工身分）、`/api/v1/guest-requests` 列出與建立、撤回（`specs/truth/contracts/groups/住客與房間/住客掃碼.yaml:1-103`、`openapi.yaml:89-94`）。程式的住客手機介面是 `controller/GuestController.java:38-68` 的 `/guest/scan`、`/guest/home`、`/guest/requests`、`…/cancel`、`…/rating` 與 `controller/GuestStreamController.java:17` 的 `/guest/stream`；`/api/v1/guest-requests` 那一組（`controller/GuestRequestApiController.java:61-101`）沒有首頁資料與評分 | `/api-plan`（介面 10、11）本輪記 NOOP 並在摘要寫明缺口；之後另開一輪 ADD（把既有行為寫進契約，不改後端）。`/dsl-refine`、`/tasks` 在補之前以程式現況與「對照」§2 為準 |
| 2 | 空房、代碼作廢、代碼不存在都回同一個 `409 room_vacant` | `service/GuestScanService.java:31`；三種情形在同一個方法裡回同一個代碼 | 已拍板統一文字（FR-002、C-01 交 `/ui-plan`）；不改 |
| 3 | 給住客的服務項目沒有類型、上限、單位、圖示 | `dto/GuestMenuItemView.java` 只有 `key`、`label` | 已拍板（FR-012、FR-013）；`/ui-plan` 的圖示依需求類型四種，不依項目 |
| 4 | 給住客的需求沒有接手人、各步時間、取消原因 | `dto/GuestTaskView.java` 八個欄位 | 已拍板（FR-024、FR-026、FR-053）；不改 |
| 5 | 住客串流沒有任何恢復訊號：`recovered` 只送員工，心跳的 `degraded` 旗標也只加在員工連線；`degraded` 只在消費者落後時送給住客，降級期間新連上的也不補送 | `service/StreamConnectionRegistry.java:128-131`（`everyone` 只在 `CONSUMER_LAG`）、`:145-149`（`isStaff()` 才送 `recovered`）、`service/StreamConnection.java:122-124`（`isStaff()` 才加 `degraded`）；連線逾時 30 分鐘（`StreamConnectionRegistry.java:31`） | `research.md` 決策 5：`degraded` 之後一直每 4 秒重讀到重連為止，最長 30 分鐘；Kafka 整個停掉時住客頁看不出來，本版不處理；要縮短只能改後端一行（違反 FR-054），列為使用者可改判的點；`/api-plan` 在缺口裡記一筆 |
| 6 | 限流是整間房算的，住客頁只看得到自己訂房的需求 | `service/GuestRequestService.java:66-67` | FR-016、FR-017 已處理；`/ui-plan` 定 C-04 的呈現 |
| 7 | 冪等鍵有帶才生效 | `filter/IdempotencyFilter.java:41-46`；租戶從住客 session 解析 | `research.md` 決策 4：住客頁每個寫入都帶；本版不改成強制；spike 第 3 項實跑回放 |
| 8 | 住客的後端行為規格不在 `specs/truth/features/backend/` | 住客規則只在後端測試的 `tms/src/test/resources/features/01-guest-requests.feature`；`backend/` 底下沒有住客掃碼的模組 | 本版不補（FR-054）；`/dsl-refine` 只寫 `guest-web/` |
| 9 | 後端給主管的列印資料只有代碼，沒有網址 | `controller/RoomQrController.java:19` 回 `{roomNo, token, version, issuedAt}` | 網址格式 `/g/{代碼}` 由 `spec.md` 假設與 `research.md` 決策 1 定；印製不在本版，要通知負責印製的人 |
| 10 | 取 QR 代碼的端點不在 `/api/v1` 底下，只放行主管 | `controller/RoomQrController.java:19,24`（`/admin/rooms/{roomNo}/qr`、`…/rotate`）；`config/SecurityConfig.java:55` | `/tasks`：`BackendChannel` 的 helper 要繞過它自動加的 `/api/v1` 前綴（`app/bdd-support/src/main/java/com/athena/bddsupport/BackendChannel.java:2666`） |

---

## 已定的事

以下各項規劃 skill 自己定不了，已由使用者或上游文件決定，各 planner 照此進行：

1. **本版不改後端，契約與資料真相這一輪只記 NOOP**（使用者 2026-11-18 指示；FR-054）。`/api-plan` 發現的缺口記在 NOOP 列的摘要裡，不寫進契約。
2. **技術棧是同一個 React 程式、同一條 web-bdd 測試線加手機設定**（使用者指示；`research.md` 決策 1、2、8）。
3. **六題待釐清交 `/ui-plan` 以預設定案**（`spec.md`「待釐清一覽」）。沒有依據的字句給預設並標「預設，待使用者確認」，`/dsl-refine` 照 004 的做法在對應 Example 上標 `# [need clarification]`。
4. **語言四種、不加韓文；不顯示姓名、飯店名、日期、同仁姓名、各步時間、取消原因**（NFR-004、FR-053）。
