# 系統分析規劃：櫃檯看板與派單

**Plan Package**: `specs/plans/004-frontdesk-board-and-dispatch`
**Truth Root**: `specs/truth`
**Truth Delta**: `specs/plans/004-frontdesk-board-and-dispatch/truth-delta.md`
**輸入**: `spec.md`（7 個使用者故事、FR-001 至 FR-103、NFR-001 至 NFR-008、SC-001 至 SC-019、待釐清 C-01 至 C-18）、`research.md`、`features/acceptance/` 下 12 份驗收劇本

這份文件回答三件事：這一包需求要動到哪些畫面、資料與後端介面；這些介面分幾輪分析、每一輪交給哪一個規劃 skill、吃什麼產出什麼；後端現況與需求之間有哪些落差、各由誰處理。它只排分析的工作，不決定欄位名稱、端點路徑或資料表長相，那些由各輪的規劃 skill 決定。

三個前提先講清楚，後面不再重複：

- **需求以原型為準**。`spec.md` 已照原型寫定；資料值（狀態、部門、類型、來源、角色）一律跟後端現況用的值，全寫不縮寫。
- **PMS 斷線與恢復提醒不在這一包**。故事 7 只處理「即時連線異常」提醒列（FR-093 至 FR-095）。
- **不接翻譯服務**。單上已經有翻譯資料時，才顯示「已翻譯」與原文列（FR-076）；沒有就只顯示住客原文。這一包不排任何翻譯服務的工作。

專案憲章 `.agents/constitution/CONSTITUTION.md` 不存在，這一輪略過憲章檢查。

---

## 專案結構

### 文件結構（本功能）

```text
specs/plans/004-frontdesk-board-and-dispatch/
├── spec.md                      # /specify、/clarify 產出：需求原文
├── research.md                  # /technical-research 產出：技術選型與後端現況
├── plan.md                      # 本檔，/system-analysis 產出
├── truth-delta.md               # 各 truth owner skill 追加：這一包改了哪些 truth
├── checklists/
│   └── requirements.md          # /specify 產出：需求品質檢查
├── features/
│   └── acceptance/              # /spec-by-example 產出：12 份驗收劇本
└── ui/                          # /ui-plan 產出（Wave 1）：畫面清單、畫面文字、畫面與資料的對應

specs/truth/
├── techstack.md                 # 已由 /technical-research 補上櫃檯 Web 這一端
├── data/
│   └── data-model.dbml          # /data-plan 更新（Wave 1；Wave 3 視情況再一趟）
├── contracts/
│   ├── openapi.yaml             # /api-plan 更新（Wave 2）
│   ├── components/              # /api-plan 更新：共用參數、回應、schemas
│   └── groups/                  # /api-plan 更新：現場作業、營運管理、住客與房間
└── features/
    ├── backend/                 # /dsl-refine 更新：後端行為規格
    └── frontdesk-web/           # /dsl-refine 新增：櫃檯 Web 的行為規格（目前還沒有這個目錄）
```

### 原始碼結構（儲存庫根目錄）

```text
app/
├── web/                         # 新增：櫃檯 Web（React ＋ TypeScript ＋ Vite 單頁應用）
├── web-bdd/                     # 新增：櫃檯 Web 的驗收測試（Cucumber-JVM ＋ Playwright for Java）
├── bdd-support/                 # 新增：兩條測試線共用的後端資料準備（BackendChannel 從 app/bdd 搬出）
├── bdd/                         # 既有：手機 App 的驗收測試；只因 BackendChannel 搬家而調整引用
└── mobile/                      # 既有：員工 App；這一包不動

tms/                             # 既有後端，這一包直接共用
└── src/main/
    ├── java/com/athena/tms/
    │   ├── controller/          # DeskController、StaffAccountController、StaffStreamController、GuestController
    │   ├── service/             # DeskService、TaskEngine、StaffAccountLoginService、StreamConnection、TaskEventsSseConsumer
    │   ├── dto/                 # TaskResponse、TriageDecisionRequest、StaffLoginResponse、GuestTaskView
    │   ├── common/codes/        # StaffRole、TaskEventType
    │   └── config/              # SecurityConfig
    └── resources/db/migration/  # 目前到 V23；資料有變動時從 V24 接著編
```

**結構決策**: 櫃檯 Web 是新的一端，程式放 `app/web/`，不另起後端，直接呼叫 `tms`。驗收測試獨立成 `app/web-bdd/`，避免啟動手機測試線；兩條測試線都要用的後端資料準備抽到 `app/bdd-support/`。櫃檯 Web 的行為規格放在新的 `specs/truth/features/frontdesk-web/`，後端為這一包補的行為寫進既有的 `specs/truth/features/backend/`。後端只在現有套件內增修，不新增模組。

---

## 分析流程規劃

### 系統介面的盤點

本次需求共盤點出 `12` 個系統介面：7 個畫面介面、1 個資料介面、4 個後端介面。畫面介面依「櫃檯人員在做哪一件事」切；後端介面依「分析時要回答的問題是不是同一類」切，讀取、寫入、登入、事件推送各一。

#### 畫面介面（承接：`/ui-plan`）

1. **登入、改密碼與帳號面板**
   - 端點類型：畫面（櫃檯 Web）
   - 主要介面：登入頁、忘記密碼提示、改密碼畫面、側欄帳號卡、帳號面板（身分卡、語言頁、登出確認）
   - 需求原文依據：FR-001 至 FR-016、NFR-001、NFR-007；SC-001、SC-002、SC-015、SC-017、SC-019；待釐清 C-01、C-02、C-16
   - 既有 truth：無（`specs/truth/features/frontdesk-web/` 尚未建立）；登入規則可參照 `specs/truth/features/backend/帳號與登入`
   - truth 變更風險：低。畫面全新，不影響其他端

2. **共用外框與尚未開放的入口**
   - 端點類型：畫面（櫃檯 Web）
   - 主要介面：側欄、工具列、提醒列的位置、側欄徽章、「通知板」「遺失物」「設定」「代開單」等只畫不通的入口、兩種視窗尺寸下的伸縮
   - 需求原文依據：FR-096 至 FR-103、NFR-002、NFR-006、NFR-008；SC-012、SC-013、SC-018；待釐清 C-18
   - 既有 truth：無
   - truth 變更風險：低

3. **總覽看板（含拖曳派單）**
   - 端點類型：畫面（櫃檯 Web）
   - 主要介面：4 張數量卡、看板控制列（泳道、篩選、房號搜尋）、8 欄看板、卡片、等待膠囊、拖曳派單的提示與落點
   - 需求原文依據：FR-017 至 FR-042、FR-087 至 FR-091、NFR-002；SC-003、SC-004、SC-005；待釐清 C-07、C-08、C-09、C-10
   - 既有 truth：無
   - truth 變更風險：低。但卡片要的資料會直接決定後端讀取介面要回什麼

4. **待派單頁、派單浮條與復原**
   - 端點類型：畫面（櫃檯 Web）
   - 主要介面：頁首負荷、需求清單與三顆動作、派單浮條與 5 秒內的「復原」、撞單提示、兩種空狀態
   - 需求原文依據：FR-043 至 FR-063、NFR-003、NFR-004；SC-005、SC-006、SC-007、SC-010、SC-016；待釐清 C-11、C-14、C-15
   - 既有 truth：無
   - truth 變更風險：低

5. **當場回答面板**
   - 端點類型：畫面（櫃檯 Web）
   - 主要介面：面板標題與住客原話、常用回覆、回覆輸入與 120 字上限、「回覆並結案」「取消」
   - 需求原文依據：FR-064 至 FR-075；SC-009、SC-016；待釐清 C-12、C-13
   - 既有 truth：無
   - truth 變更風險：低

6. **單筆詳情面板**
   - 端點類型：畫面（櫃檯 Web）
   - 主要介面：單號、房號、「VIP」、狀態標籤、標題與原文列、狀態訊息卡、資訊列、歷程、底部動作列（待派單的單可派；其餘只畫不通）
   - 需求原文依據：FR-076 至 FR-086；SC-012、SC-014；待釐清 C-03、C-04、C-05、C-06
   - 既有 truth：無
   - truth 變更風險：低。但面板要的欄位最多，是後端讀取介面的主要需求來源

7. **即時更新與連線異常提醒列**
   - 端點類型：畫面（櫃檯 Web）
   - 主要介面：「即時連線異常」提醒列、每 5 秒自動更新、恢復後自動消失、各畫面跟著變化的範圍
   - 需求原文依據：FR-092 至 FR-095、NFR-005；SC-011；待釐清 C-17（驗收門檻）
   - 既有 truth：無
   - truth 變更風險：低

#### 資料介面（承接：`/data-plan`）

8. **單、歷程、飯店與帳號的資料**
   - 端點類型：資料（`specs/truth/data/data-model.dbml`）
   - 主要介面：`tasks`、`task_events`、`tenants`、`tenant_config`、`staff_accounts`；讀取時會用到 `rooms`、`reservations_cache`、`repair_tickets`、`inspections`、`task_sop_checks`
   - 需求原文依據：FR-041（今天以飯店當地日期為界）、FR-053 至 FR-058 與 NFR-004（派出後復原）、FR-067 至 FR-073（回覆與住客原文並存）、FR-076 至 FR-082（單號、各種時間、歷程）、FR-091（拖曳派單在歷程上的記法）、FR-100（記在登入者名下）、FR-006 與 FR-007（`duty_manager`）
   - 既有 truth：`specs/truth/data/data-model.dbml` 的 `tasks`（第 170 行起）、`task_events`（第 271 行起）、`tenants`（第 16 行起）、`staff_accounts`（第 35 行起）
   - truth 變更風險：高。`tasks` 的狀態轉移與 `task_events` 的事件值是手機 App、推播、統計共用的；新增「`pool` 回到 `triage`」會影響待認領、未接提醒與事件重放

#### 後端介面（承接：`/api-plan`）

9. **櫃檯讀取介面**
   - 端點類型：API（讀取）
   - 主要介面：看板清單、待派單清單、單筆詳情與歷程、今日已派單與當場回答張數、常用回覆
   - 需求原文依據：FR-017 至 FR-042、FR-043 至 FR-050、FR-061、FR-064 至 FR-066、FR-076 至 FR-085；SC-003、SC-004、SC-014
   - 既有 truth：`specs/truth/contracts/openapi.yaml` 的 `/tasks`、`/tasks/{id}`、`/task-events`、`/tenant-configs/guest-home`；`groups/現場作業/任務.yaml`
   - truth 變更風險：中。契約裡現有的 `/tasks` 清單與 `/task-events` 沒有實作，補上時要避免與手機 App 用的 `/pool`、`/me/today` 的回傳互相矛盾

10. **櫃檯寫入介面（派單、復原、當場回答）**
    - 端點類型：API（寫入）
    - 主要介面：派給房務或工務、派出後復原、當場回答並結案；撞單與復原失敗的回應；住客讀得到回覆
    - 需求原文依據：FR-051 至 FR-060、FR-067 至 FR-075、FR-087 至 FR-091、FR-099、FR-100、NFR-003、NFR-004；SC-006、SC-007、SC-008、SC-009、SC-016
    - 既有 truth：`openapi.yaml` 的 `/tasks/{id}/triage`（第 150 行）；`specs/truth/features/backend/待認領`、`任務執行`
    - truth 變更風險：高。契約路徑與程式現況不一致，且復原是全新的動作

11. **登入與身分介面**
    - 端點類型：API（登入、改密碼、權限）
    - 主要介面：登入回應、改密碼規則、三種角色的放行範圍
    - 需求原文依據：FR-001、FR-004 至 FR-007、FR-011、FR-012、FR-015、FR-016、FR-096；SC-001、SC-002
    - 既有 truth：`openapi.yaml` 的 `/staff-accounts/login`、`/staff-accounts/change-password`；`groups/營運管理/員工帳號與登入.yaml`；`specs/truth/features/backend/帳號與登入`
    - truth 變更風險：中。登入回應與改密碼規則是手機 App 共用的，改動要讓手機 App 不受影響

12. **員工事件串流**
    - 端點類型：事件（伺服器推送）
    - 主要介面：`GET /staff/stream` 的收件範圍、連上時的重抓提示、降級與恢復訊號
    - 需求原文依據：FR-092 至 FR-095、NFR-005；SC-011
    - 既有 truth：`specs/truth/techstack.md` 的即時推送；`specs/truth/features/backend/推播`（僅相鄰，不是同一件事）
    - truth 變更風險：中。收件範圍放寬只能針對櫃檯畫面的三種角色，不能讓房務與工務收到別部門的事件

### 分析流程的安排

#### Wave 1

- **平行分析介面**：
  - 介面 1 至 7（七個畫面介面）→ 合併成一次 `/ui-plan` 委派。七個介面寫進同一份畫面規劃（`ui/`），分開委派會互相覆寫。
  - 介面 8（資料）→ 一次 `/data-plan` 委派。
- **輸入**：`spec.md`、`research.md`、`features/acceptance/`、原型；`/data-plan` 另讀 `specs/truth/data/data-model.dbml` 與本檔「後端現況的落差」。
- **輸出**：`ui/`（畫面清單、每個畫面的文字與狀態、每個畫面要哪些資料）；更新後的 `data-model.dbml` 與 `truth-delta.md` 的 `/data-plan` 段。
- **分析重點**（`/ui-plan`）：
  - C-01 至 C-16、C-18 各自的畫面文字與行為是什麼？（C-17 是驗收門檻，不在這裡定字句）
  - 看板卡片、待派單列、當場回答面板、詳情面板各自要顯示哪些資料？請逐畫面列成清單，Wave 2 要拿它對後端回傳。
  - 等待時間與「N 分鐘前」的「現在」用哪一個時鐘？瀏覽器時鐘與後端時鐘有落差時，畫面怎麼算才不會出現負數或提早逾時？
  - 載入中、讀取失敗、真的沒有單三種情形（FR-042）在看板、待派單頁、詳情面板各長什麼樣？
  - 拖曳派單在滑鼠與觸控下的提示、落點與失敗回位（FR-087 至 FR-091、FR-060）怎麼呈現？
  - 重新整理後保留哪些畫面狀態（FR-008、C-18）？
- **分析重點**（`/data-plan`）：
  - 「派出後復原」要新增 `pool` 回到 `triage` 的轉移。復原後這張單的部門、未接提醒階段、進池來源各回到什麼值？事件重放後結果是否一致？
  - 復原在歷程上是哪一個事件值？拖曳派單與按鈕派單在歷程上怎麼區分（FR-091）：新事件值，還是既有派單事件多帶一個標記？
  - 當場回答的回覆放在哪裡，才不會蓋掉住客原文、語言與既有的翻譯資料（FR-072、FR-076）？
  - 查房通過、取消、結案三個時間現在只存在事件裡，詳情面板要顯示（FR-076 至 FR-082）：從事件讀就夠，還是需要存成欄位？
  - 非工務單沒有單號，詳情面板要顯示單號（FR-076）：單號從哪裡來？
  - 「今天」以飯店當地日期為界（FR-041）：飯店的時區記在哪裡？見「需要使用者決定的事」第 1 題。
  - `duty_manager` 當操作者時，`task_events.actor_role` 的可用值要不要加上它？
  - 今日已派單與當場回答張數（FR-061）能不能直接從事件算出來，不另存計數？
- **安排理由**：畫面規劃與資料模型彼此不依賴，可以同時進行。兩者都是 Wave 2 的輸入：後端介面要回什麼，取決於畫面要什麼與資料存在哪裡；先定這兩件，後端契約才不會改兩次。

#### Wave 2

- **平行分析介面**：介面 9、10、11、12（四個後端介面）→ 合併成一次 `/api-plan` 委派。四個介面都寫進 `specs/truth/contracts/`，共用同一批 schema。
- **輸入**：Wave 1 的 `ui/`（每個畫面要的資料清單）與更新後的 `data-model.dbml`；`research.md` 的「後端為這一包要補的事」；本檔「後端現況的落差」。
- **輸出**：更新後的 `specs/truth/contracts/`（`openapi.yaml`、`components/`、`groups/`）與 `truth-delta.md` 的 `/api-plan` 段；一份「資料模型還缺什麼」的清單（有缺才列）。
- **分析重點**：
  - 契約寫的是 `/tasks/{id}/triage`，程式是 `/desk/triage/{taskId}` 且不在 `/api/v1` 下。以哪一邊為準？手機 App 有沒有用到任何一邊？
  - 看板清單怎麼只回「還沒結束的單＋今天結束的單」？一次回完還是分段？排序由後端還是畫面決定？
  - 卡片、待派單列與詳情面板要的資料（房號、樓層、住客名、VIP、負責人姓名、取消原因與取消人、SOP 進度、單號、來源與開單人、延後與中斷資訊、翻譯資料）由清單一次帶出，還是清單精簡、詳情另抓？
  - 歷程從哪一個介面讀？契約既有的 `/task-events` 不分單，要改成按單讀取還是併進單筆詳情？
  - 撞單時，回應要帶哪些資訊才能讓畫面說出「誰派給了哪個部門」（FR-059）？復原失敗時怎麼分出「已被同仁認領」與「已被住客收回」（FR-055）？
  - 派單、復原、當場回答都要防重送。冪等鍵現在是有帶才生效，櫃檯 Web 的三個寫入要不要改成必帶？
  - 回覆上限 120 字（FR-067）由後端把關時，超過的回應是什麼？
  - 常用回覆（FR-066）與飯店名稱（FR-001、FR-096）從哪個介面讀？登入前的登入頁要顯示飯店名稱，沒有登入身分時怎麼知道是哪一家飯店？
  - 角色不能使用櫃檯畫面的帳號（FR-006）由後端擋還是畫面擋？後端擋的話，怎麼不影響同一組帳號登入手機 App？
  - 新密碼規則（至少 8 碼、含英文字母、含數字，FR-016）加到後端後，手機 App 的改密碼會不會被影響？
  - 事件串流對 `frontdesk`、`manager`、`duty_manager` 要推全館的事件。收件範圍依角色決定，還是連線時聲明用途？連上時的重抓提示現在寫死成手機 App 的待認領，櫃檯 Web 要重抓什麼？
  - 後端宣告降級之後沒有恢復訊號，畫面怎麼知道可以回到即時更新（FR-095）？
  - 回應要不要帶後端現在的時間，讓畫面校正等待時間？
- **安排理由**：後端契約依賴 Wave 1 的兩項產出，不能提前。四個後端介面彼此共用 schema 與錯誤回應，分開委派會互相覆寫，所以合併成一次。

#### Wave 3（有條件）

- **平行分析介面**：介面 8（資料）→ 第二趟 `/data-plan`。
- **啟動條件**：Wave 2 的 `/api-plan` 列出資料模型還缺的東西（例如契約需要的欄位、索引或設定鍵在 `data-model.dbml` 裡沒有）。清單是空的就跳過這一輪。
- **輸入**：Wave 2 的缺口清單與更新後的契約。
- **輸出**：補齊後的 `data-model.dbml` 與 `truth-delta.md` 的 `/data-plan` 段追加。
- **分析重點**：契約要的每一個欄位在資料模型裡是否都有來源？看板清單的篩選（狀態＋今天結束）是否走得到既有索引？
- **安排理由**：`/api-plan` 不能改資料 truth，它發現的資料缺口只能回頭交給 `/data-plan`。

#### 三輪之後

1. `/dsl-refine`：把 `features/acceptance/` 的 12 份驗收劇本整理成行為規格，櫃檯 Web 的寫進 `specs/truth/features/frontdesk-web/`，後端補的行為（復原、撞單細節、全館事件、角色放行、密碼規則）寫進 `specs/truth/features/backend/`。輸入是三輪的全部產出。
2. `/tasks`：依 truth 與 `research.md` 排出實作工作，含兩個半日 spike（拖曳套件選型、觸控拖曳的自動化）。

Wave 1 完成前不能跑 `/dsl-refine` 或 `/tasks`。

---

## 後端現況的落差與歸屬

以下逐項對過原始碼。後端路徑省略前綴 `tms/src/main/java/com/athena/tms/`。

### 已知的 13 項

| # | 落差 | 現況（檔案:行號） | 歸屬 |
| --- | --- | --- | --- |
| 1 | 看板查詢不分日期；飯店沒有時區設定 | `service/DeskService.java:36-39` 回全館所有單、新的在上；`service/DeskService.java:31-34` 待派單最舊在上。時區：程式與 migration 都沒有每家飯店的時區，`config/AdjustableClock.java:13` 用伺服器預設時區；`specs/truth/data/data-model.dbml:267` 明寫資料層沒有每租戶時區 | 查詢範圍 → `/api-plan`（介面 9）；時區 → `/data-plan`（介面 8），見待決第 1 題 |
| 2 | 回傳的單缺少畫面要的資料 | `dto/TaskResponse.java:8` 只有 20 個欄位，沒有房號、樓層、住客名、VIP、負責人姓名、取消原因、SOP 進度、單號。資料本身大多已存在：`data-model.dbml:150,152,193,590` | `/api-plan`（介面 9） |
| 3 | 單的歷程沒有讀取端點 | `controller/` 下沒有任何讀取 `task_events` 的端點。契約有 `/task-events`（`specs/truth/contracts/openapi.yaml:134`），但不分單、也沒有實作 | `/api-plan`（介面 9） |
| 4 | 沒有派出後復原 | `dto/TriageDecisionRequest.java:3` 只有 `action`、`dept`、`note`；`service/DeskService.java:46-59` 只接受 `assign` 與 `answer`；`service/TaskEngine.java:93-96` 只有 `triage` 到 `pool`；`common/codes/TaskEventType.java:6-25` 沒有復原的事件值，也沒有拖曳標記 | 轉移與事件值 → `/data-plan`（介面 8）；動作與回應 → `/api-plan`（介面 10） |
| 5 | 撞單回 409 但不帶細節 | `service/TaskEngine.java:364-375` 只回「任務目前狀態不允許」。帶細節的機制已經有：`common/error/StateConflictException.java:17` 可帶代碼與附帶資訊，只是派單這條路沒用 | `/api-plan`（介面 10） |
| 6 | 當場回答蓋掉住客原文；沒有 120 字上限；住客拿不到回覆 | `service/TaskEngine.java:100` 把回覆整個寫進 `tasks.note`，原本存在裡面的原文、語言、翻譯一起被蓋掉；`service/DeskService.java:53-55` 只檢查不是空白；`dto/GuestTaskView.java:7` 沒有回覆欄位 | 回覆存哪裡 → `/data-plan`（介面 8）；上限與住客讀取 → `/api-plan`（介面 10） |
| 7 | 常用回覆只有住客讀得到；今日張數沒有端點 | 住客資訊頁只經 `controller/GuestController.java:46` 回傳（`dto/GuestHomeView.java:7`），`config/SecurityConfig.java:47` 限住客身分。`controller/TaskExecutionController.java:102` 的 `/me/today` 是同仁自己的單，不是派單張數 | `/api-plan`（介面 9）；張數怎麼算由 `/data-plan` 先確認 |
| 8 | 非工務單沒有單號；三個時間只存在事件裡 | 單號只有工務單有：`data-model.dbml:590`。`tasks` 的時間欄位只有開工與完成（`data-model.dbml:185`）；查房時間在 `inspections`，取消與結案時間只在 `task_events` | `/data-plan`（介面 8） |
| 9 | 登入回應沒有飯店名稱；新密碼規則後端沒有 | `dto/StaffLoginResponse.java:8-15` 七個欄位，沒有飯店名稱（資料在 `data-model.dbml:18`）。`service/StaffAccountLoginService.java:65-67,77-79` 只檢查必填與不能和目前密碼相同，沒有長度與字元規則 | `/api-plan`（介面 11） |
| 10 | 事件串流不推全館事件 | `service/StreamConnection.java:57-61` 只推「同部門」或「被指派者」。**與線索的出入**：櫃檯不是完全收不到，待派單的單掛在 `frontdesk` 部門，所以櫃檯收得到住客新送單；但單一派出去，部門就換了，收件判斷讀的是單現在的部門（`service/TaskEventsSseConsumer.java:155`），連「派出」這一筆櫃檯自己都收不到，之後的認領、完成也收不到。主管的部門欄可以是空的，空的時候一筆都收不到 | `/api-plan`（介面 12） |
| 11 | `duty_manager` 沒有放行 | `common/codes/StaffRole.java:11` 的清單沒有它；`config/SecurityConfig.java:48-51,59-60,69-71` 的每一條規則都沒有它。資料庫允許這個值：`tms/src/main/resources/db/migration/V7__duty_manager_role.sql:6` | `/api-plan`（介面 11），見待決第 3 題 |
| 12 | 櫃檯端點不在契約裡；契約有而程式沒有的端點 | `controller/DeskController.java:16,24,29,34` 是 `/desk/triage`、`/desk/triage/{taskId}`、`/desk/tasks`，不在 `/api/v1` 下；契約寫的是 `/tasks/{id}/triage`（`openapi.yaml:150`）。契約有而程式沒有：`/alerts`（`openapi.yaml:64`）、`/tasks` 清單（`:136`）、`/reservations`（`:106`）、`/rooms` 清單（`:120`）；`/rooms/{id}` 有實作（`controller/RoomController.java:25`） | 櫃檯會用到的 → `/api-plan`（介面 9、10）。`/alerts`、`/reservations`、`/rooms` 清單這一包用不到，**不在 004** |
| 13 | 翻譯沒有來源 | `service/NoopGuestTextAdvisor.java:11-13` 永遠回空。**與線索的出入**：資料模型沒有翻譯欄位，但有位置：翻譯若存在，會以 `llm_translated` 這個鍵存在 `tasks.note` 的 JSON 裡（`service/GuestRequestService.java:134-137`），目前不會有任何一張單帶這個鍵 | 讓回傳帶出既有翻譯資料 → `/api-plan`（介面 9）；翻譯服務本身**不在 004** |

### 這一輪新發現的落差

| # | 落差 | 現況（檔案:行號） | 歸屬 |
| --- | --- | --- | --- |
| N1 | 登入不看角色 | `service/StaffAccountLoginService.java` 的登入流程沒有任何角色判斷，`housekeeping`、`engineering`、`owner` 都登得進去。FR-006 要擋 | `/api-plan`（介面 11）決定後端擋或畫面擋 |
| N2 | 歷程的操作者角色沒有 `duty_manager` | `data-model.dbml:277` 的 `actor_role` 可用值沒有它；值班主管派單時歷程記不下來 | `/data-plan`（介面 8） |
| N3 | 冪等鍵有帶才生效 | `filter/IdempotencyFilter.java:40-45` 沒帶標頭就直接放行 | `/api-plan`（介面 10） |
| N4 | 降級之後沒有恢復訊號 | `service/TaskEventsSseConsumer.java:82-88` 進入降級時會廣播，恢復時只改內部旗標、不通知。FR-095 要提醒列自動消失 | `/api-plan`（介面 12） |
| N5 | 連上時的重抓提示寫死成手機 App 的待認領 | `service/StreamConnection.java:80` 對所有員工都回 `/api/v1/pool` | `/api-plan`（介面 12） |
| N6 | 有兩個登入端點 | `controller/AuthController.java:20`（`/auth/login`）與 `controller/StaffAccountController.java:41`（`/api/v1/staff-accounts/login`）。契約只有後者 | `/api-plan`（介面 11）指定櫃檯 Web 用哪一個 |
| N7 | 登入回應沒有員工編號 | `dto/StaffLoginResponse.java:8-15`。帳號卡與身分卡要顯示（FR-011、FR-012）；畫面可以用登入時輸入的值，但重新整理後要有來源 | `/api-plan`（介面 11）；`/ui-plan` 註明畫面怎麼保留 |
| N8 | 回應沒有後端現在的時間 | 等待時間以瀏覽器時鐘計算時，櫃檯電腦時間不準就會算錯 | `/ui-plan` 先定算法，`/api-plan`（介面 9）決定要不要回傳 |
| N9 | 事件值清單漏了工務單驗收的事件值 | `common/codes/TaskEventType.java:24-25` 宣告了 `repair_verification_failed` 卻沒放進清單。新增復原事件值時會動到同一份清單 | 不屬於 004 的需求；`/data-plan` 新增事件值時一併留意，實作時不要順手改掉既有行為 |
| N10 | 住客進度頁的前端不在這個儲存庫 | `app/` 下只有 `bdd` 與 `mobile`。FR-073 要住客看得到回覆 | 後端回傳 → `/api-plan`（介面 10）；住客頁面本身見待決第 2 題 |

---

## 需要使用者決定的事

以下三題規劃 skill 自己定不了。沒有答覆時，各輪照「預設」進行。

1. **飯店的時區要不要在這一包做成每家飯店一個設定？** FR-041 的「今天」以飯店當地日期為界，但後端目前所有「今天」都用同一個伺服器時區（`data-model.dbml:267`），003 的主管待辦也是這把尺。預設：沿用伺服器時區，不新增設定；多家飯店跨時區時再處理。
2. **住客進度頁顯示回覆（FR-073）這一包做到哪裡？** 住客頁面的前端不在這個儲存庫。預設：這一包只做到「後端回給住客的資料帶得出回覆」，並以後端層級的驗收證明；住客頁面的顯示不排工作。
3. **`duty_manager` 放行到哪裡？** FR-007 說值班主管在櫃檯畫面的權限與主管相同。預設：只放行櫃檯 Web 用到的介面（登入、改密碼、櫃檯讀寫、事件串流）；手機 App 的主管功能（查房、驗收、主管待辦）不在這一包放行。
