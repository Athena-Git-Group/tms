# 系統分析規劃

## 專案結構

### 文件結構（本功能）

```text
specs/plans/002-celebrate-and-resume/
├── spec.md                  # /specify ＋ /clarify-over-specs（已完成）
├── plan.md                  # 本檔
├── truth-delta.md           # 由 truth owner 透過 /truth-delta 追加
├── checklists/
│   └── requirements.md      # 已回刷，Ready
├── features/
│   └── acceptance/          # /spec-by-example 產出（plan-side）
├── ui/                      # Wave 1 /ui-plan 產出（plan-side，非 truth）
└── tasks.md                 # /tasks 產出

specs/truth/                 # 系統真相（由各 truth owner 更新）
├── contracts/               # Wave 2 /api-plan 更新
├── data/                    # Wave 1 /data-plan 更新（預期 NOOP）
└── features/
    ├── backend/             # /dsl-refine
    └── ios-app/任務執行/     # /dsl-refine：既有 Rule MODIFY ＋ 恢復處理 ADD
```

### 原始碼結構（儲存庫根目錄）

```text
app/
├── mobile/                          # Flutter 員工 App
│   ├── lib/core/                    # api_client、connection_status、today_refresh、theme
│   ├── lib/features/task/           # 任務頁：主按鈕、恢復處理、完成後導頁
│   ├── lib/features/celebrate/      # 完成畫面本體
│   ├── lib/features/today/          # MyTodayScope：今日進度的共用取用路徑
│   ├── lib/features/home/           # 首頁主卡（「下一間」的既有呈現）
│   └── test/                        # Flutter widget 測試
└── bdd/src/test/java/               # Appium ＋ Cucumber-JVM，驗 ios-app feature

tms/
├── src/main/java/com/athena/tms/
│   ├── controller/TaskExecutionController.java   # tasksComplete、tasksResume 入口
│   └── service/TaskExecutionService.java         # 完成與恢復的狀態轉移、myToday 口徑
├── src/main/resources/db/migration/              # Flyway
└── src/test/java/com/athena/tms/bdd/             # 後端 BDD step definitions

Claude Design/Main.dc.html                        # 完成畫面的視覺來源（設計參考，非實作來源）
```

**結構決策**: 本輪不新增任何原始碼目錄。完成畫面與任務頁都已存在於 `app/mobile/lib/features/`，後端兩個命令也都已有 controller 與 service 入口；所有改動都落在既有檔案內。測試面則一定會新增：`specs/truth/features/ios-app/任務執行/` 底下的 feature 與 dsl 目前完全沒有涵蓋恢復處理，完成慶祝頁也只驗了兩個數字。

## 分析流程規劃

### 系統介面的盤點

本次需求共盤點出 `4` 個系統介面。

1. `員工 App 完成畫面與任務頁介面`
   - 端點類型：`手機端端點`
   - 主要介面：完成畫面的版面與三種接續狀態（下一間、回到未結束的那一間、全部清完）、房號標題與三種副標、本次用時與今日進度兩塊統計、離線未送出提示、慶祝氣氛關閉後的精簡樣態；任務頁「恢復處理」主按鈕與離線擋下的提示
   - 需求原文依據：FR-006「使用者 MUST 能從完成畫面一步進入下一間的任務頁，不必經過首頁」、FR-008、FR-014、FR-015、FR-018「備品類任務時完成畫面 MUST 不呈現今日進度」、FR-019、FR-020、FR-021、FR-022、FR-024、NFR-002「版面與視覺值 MUST 以設計原型的 celebrate 覆蓋層為準」、NFR-003「MUST 疊在剛完成的那張任務頁之上」

2. `今日進度與任務狀態的資料口徑介面`
   - 端點類型：`資料庫端點`
   - 主要介面：「今天第幾單」與「今天仍未結束的任務」兩個衍生值所依賴的既有欄位（任務的狀態、類別、查房結果、負責人、日期歸屬），以及它們與首頁「已清完間數」共用口徑的可行性；判定是否需要新欄位或新資料表
   - 需求原文依據：FR-017「該計數 MUST 計入今天完成的打掃類任務，含鎖定免查房而直接結案的單，並與首頁『已清完間數』採同一套口徑」、FR-004 的挑選順序「進行中 > 已中斷 > 已延後，同一組內依房號遞增」、SC-005「完成畫面上的『今天第幾單』與同一時間首頁『已清完間數』的分子一致」

3. `任務完成與恢復的後端命令介面`
   - 端點類型：`後端端點`
   - 主要介面：`tasksComplete` 回應擴充出「接續去向」（三種擇一，含識別與房號）與「今天第幾單」；`tasksResume` 的契約落定——回到進行中、清除停住原因、計時自累計值接續、套用一次一間並回 409 指出是哪一間
   - 需求原文依據：FR-001「系統 MUST 在任務完成的回應中指出員工接下來該去哪裡，並能分辨三種情況」、FR-002、FR-003、FR-004、FR-005、FR-009、FR-010、FR-011、FR-012、FR-013、FR-016、NFR-001「MUST 在送出完成的同一次往返內就能決定要呈現哪一種接續去向」

4. `租戶設定介面`
   - 端點類型：`後端端點`
   - 主要介面：租戶設定封閉鍵集合新增「是否顯示完成慶祝的氣氛呈現」一鍵，含預設值與可修改角色；該鍵 MUST NOT 影響接續去向與第 N 單的計算
   - 需求原文依據：FR-024「租戶 MUST 能關閉完成慶祝的**氣氛呈現**……這個設定 MUST NOT 影響員工一步進入下一間的能力」（仍帶 `NEEDS CLARIFICATION`：鍵名、預設值、可修改角色）

### 分析流程的安排

#### Wave 1

- 平行分析介面：
  - `員工 App 完成畫面與任務頁介面`
  - `今日進度與任務狀態的資料口徑介面`
- 分析重點：
  - 把設計原型的 celebrate 覆蓋層落成 plan-side 設計：三種接續狀態各自的版面、主次按鈕的視覺層級、兩塊統計的內容與配色、離線提示行的位置、氣氛關閉後哪些元素消失；一併釘住恢復處理在任務頁上的位置與離線提示
  - 釐清完成畫面實際需要後端供給哪些值、哪些可由手機端既有的今日進度推算（這是 Wave 2 決定回應形狀的輸入）
  - 判定「今天第幾單」與「今天仍未結束的任務」能否完全由既有欄位推導；若能，記為 NOOP 並說明推導路徑，若不能，指出缺哪一個欄位
- 安排理由：畫面決定後端要供給什麼，不是反過來。資料口徑的分析只問「既有欄位夠不夠」，不需要等畫面定案，因此兩者可平行；但兩者的結論都是 Wave 2 的必要輸入。

#### Wave 2

- 平行分析介面：
  - `任務完成與恢復的後端命令介面`
  - `租戶設定介面`
- 分析重點：
  - 依 Wave 1 收斂出的畫面需求，決定 `tasksComplete` 回應中「接續去向」的形狀——三種狀態如何在同一個欄位結構下被明確分辨（而不是讓 App 靠「欄位是不是 null」猜），以及「今天第幾單」的欄位位置
  - 落定 `tasksResume` 的契約：成功回應沿用 `TaskDetail`、停住原因清除的語意、一次一間的 409 錯誤碼與 context 是否與 `tasksStart` 共用
  - 依 Wave 1 對「氣氛呈現」涵蓋哪些元素的結論，決定租戶設定鍵的語意邊界與命名
- 安排理由：回應形狀依賴 Wave 1 的畫面結論（NFR-001 要求一次往返就決定接續去向，所以畫面需要什麼必須先定）；設定鍵的語意依賴 Wave 1 對「關閉氣氛呈現後長什麼樣」的定義。兩者彼此不依賴，可平行。
