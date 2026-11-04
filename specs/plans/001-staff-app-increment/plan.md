# 系統分析規劃

## 專案結構

### 文件結構（本功能）

```text
specs/plans/001-staff-app-increment/
├── spec.md
├── research.md
├── plan.md
├── truth-delta.md
├── checklists/
│   └── requirements.md
├── features/
│   └── acceptance/          # 7 份 PM 驗收旅程
├── ui/                      # Wave 2 /ui-plan 產出（plan-side，非 truth）
└── tasks.md                 # /tasks 產出

specs/truth/                 # 系統真相（由各 truth owner 更新）
├── techstack.md             # /technical-research（已建立）
├── features/
│   ├── backend/             # /dsl-refine（已建立，9 模組）
│   └── ios-app/             # /dsl-refine（已建立，7 模組，只有測項）
├── contracts/               # Wave 1 /api-plan 產出
└── data/                    # Wave 1 /data-plan 產出
```

### 原始碼結構（儲存庫根目錄）

```text
tms/                         # Spring Boot 4.1.1 後端（gitlink，原始碼尚未取回）
├── pom.xml
├── src/main/java/…/
│   ├── api/                 # Staff、Admin、Desk 等 Controller
│   ├── task/                # 任務引擎：狀態變更唯一入口與狀態機守衛
│   ├── repair/              # 本 plan 新增：工務單與照片
│   ├── inspection/          # 查房、免查鎖定、抽查
│   ├── dispatch/            # 待接池排序、派工、準備工單
│   ├── handover/            # 交辦合併、櫃檯備註、已讀
│   ├── kb/                  # 查一下：推薦、出處、稽核、問主管
│   ├── outbox/              # outbox relay → Kafka；推播消費者；PMS 回寫消費者
│   └── scheduling/          # Quartz：班末掃描、提醒
├── src/main/resources/db/migration/   # Flyway
└── src/test/
    ├── java/…/steps/        # Cucumber-JVM step definitions
    └── resources/features/  # 由 specs/truth/features/backend 同步

（員工 App 在本 repo 的 app/，Flutter；BDD harness 規劃於 app/bdd/）
```

**結構決策**：沿用基線的單體 Spring Boot 架構。worker 與 API 在同一個程序，任務引擎是狀態變更的唯一入口。

- **工務單不另起服務。** 它是 dept `engineering`、type `repair` 的任務，照樣走任務引擎。新增的只有工務單專屬欄位與照片。
- **App 在本 repo 的 `app/`（Flutter，iOS＋Android 同一套程式碼）。** 後端以 API 與推播內容跟 App 銜接。
- **套件分層是推定。** 上面的分層依基線類別圖推定。`tms/tms` 子模組取回後，要以實際結構校正本節。

## 分析流程規劃

### 系統介面的盤點

本次需求共盤點出 `3` 個系統介面。

1. `後端員工與主管 API 介面`
   - 端點類型：`後端端點`
   - 主要介面：
     - **員工命令**：延後、中斷、恢復、開工務單（兩種模式）、開單前檢查、照片上傳、補照片、撤回、完修、回饋、問主管、展開交辦。
     - **主管命令**：抽查、附交代退池、回覆問主管、免查房設定。
     - **櫃檯命令**：房間備註。
     - **員工查詢**：今日進度、下一間、部門待接池（排序、篩選、筆數）、任務詳情（交辦、退回資訊、同房工務單）、查一下推薦與回答。
     - **非同步契約**：推播內容的去處格式、PMS 回寫房況值（OC）。
     - **共通**：冪等鍵與 409 守衛的錯誤語意。
   - 需求原文依據：
     - FR-001–021：一次一間、本人延後中斷、工務單生命週期與照片。
     - FR-026：抽查。
     - FR-028–046：進度、待接池、交辦、查一下。
     - FR-047：推播事件，每則帶可直達的任務識別。
     - FR-024：免查回寫 OC。
     - NFR-005、NFR-006。
     - `specs/truth/features/backend/**` 的 When 與 Then 已固定要呼叫的命令和查詢。

2. `任務、工務單與稽核持久化介面`
   - 端點類型：`資料庫端點`
   - 主要介面：
     - **任務擴充欄位**：inspection、免查鎖定值、累計進行時間、停住原因、連結工務單。
     - **新增列舉值**：source `staff`、cancelled 原因 `staff_withdrew`。
     - **工務單**：欄位、租戶內單號序號、照片與完修照片的儲存媒介。
     - **其他新增資料**：交辦已讀、櫃檯備註、查一下稽核紀錄與問主管、租戶設定新鍵。
     - **推定的既有資料**：員工本班負責樓層、交班單項目。
   - 需求原文依據：
     - spec「關鍵實體」全節。
     - FR-013：單號 `W-NNNN`。
     - FR-015：同房同類別未結案。
     - FR-022、FR-023：設定鍵與開工鎖定。
     - FR-038：已讀不重複。
     - FR-045：稽核欄位。
     - NFR-004：事件只記照片張數。
     - research.md 殘餘風險「照片儲存媒介未決」。

3. `iOS 員工 App 介面`
   - 端點類型：`手機端`
   - 主要介面：
     - 首頁進度與下一間、待接池列表與分類。
     - 任務頁：交辦區、退回資訊、計時。
     - 報修畫面：拍照、浮水印、重複與急件提示、上傳失敗重試。
     - 離線待送出佇列、推播點開去處、查一下畫面。
   - 需求原文依據：
     - 原型 artifact（房務 App）。
     - NFR-001–003：壓縮、浮水印、離線冪等。
     - FR-047：推播去處。
     - `specs/truth/features/ios-app/**`。
     - research.md 決策 1：本輪只產出 feature 測項，App 技術選型延後。

### 分析流程的安排

#### Wave 1

- 平行分析介面：
  - `後端員工與主管 API 介面`
  - `任務、工務單與稽核持久化介面`
- 分析重點：
  - **API**：
    - 把 backend DSL 裡抽象的「呼叫○○命令／查詢」逐一對應成 operation，包含路徑、請求與回應形狀、錯誤碼語意（409 守衛、422 驗證，以及拒絕時回傳的業務提示文字）。
    - 定義推播內容的去處欄位（任務 id、部門待接池入口、標亮任務、首頁）。
    - 盤點基線哪些 operation 是 MODIFY，例如開工允許 deferred、cancel 新增原因、reassign 對本人延後的限制。
  - **Data**：
    - 任務表的擴充欄位與狀態轉移需要的守衛條件。
    - 工務單與照片的模型與儲存媒介；同房同類別未結案查詢需要的索引；租戶內單號序號。
    - 已讀的唯一性；稽核紀錄的 PII 邊界（住客身分不落入回答）。
    - 盤點哪些是基線資料的 MODIFY。
- 安排理由：
  - 兩者的輸入都已經收斂：spec 的 FR 與關鍵實體，加上已寫入 truth 的 backend feature 與 DSL。DSL 已經固定每句話要落地的權威狀態，所以不必等對方的結論就能各自分析。
  - 兩者共用一個前提，要在 handoff 中明示：照片是「先逐張上傳取得 id，再送出開單請求並引用 id」的兩段式流程，這是 backend DSL 已採用的模型。

#### Wave 2

- 平行分析介面：
  - `iOS 員工 App 介面`
- 分析重點：
  - 依 Wave 1 的 API 契約，規劃報修畫面的兩段式上傳與單張重試、離線佇列與冪等鍵的保存、推播內容到畫面去處的對應。
  - 規劃首頁、待接池、任務頁、查一下要呈現的狀態與錯誤回饋，對齊 ios-app 的 feature 測項。
- 安排理由：
  - App 的上傳流程、離線重送與推播導頁，都要依賴 Wave 1 定案的 API 形狀與推播內容格式，所以排在後面。
  - 使用者已指示 App 本輪只產出 feature 測項，因此本波**可延後**到 App 技術選型確定後再執行；不影響後端進入 `/tasks`。

#### Wave 1 之後的 truth 回補（非 planner，列出供排程）

- `/dsl-refine`：`/api-plan` 定案後，把 backend DSL 的「呼叫○○命令」實作欄對應到具體 operation。這是語意不變的 MODIFY。
