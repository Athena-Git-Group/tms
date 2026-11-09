# Tasks: 002-celebrate-and-resume（完成畫面接力與恢復處理）

**Plan Package**: `specs/plans/002-celebrate-and-resume`
**Core Inputs**: `spec.md`, `plan.md`, `truth-delta.md`, `ui/ui-plan.md`, `ui/*.html`, `specs/truth/techstack.md`, `specs/truth/contracts/**`, `specs/truth/data/data-model.dbml`, `specs/truth/features/backend/**`, `specs/truth/features/ios-app/**`

本輪沒有 `research.md`（`/technical-research` 未執行，本輪不引入新技術）。
本檔承接 `truth-delta.md` `/dsl-refine` 對 002 本體的**三趟**內容：第一趟（backend 與 ios-app 兩張表）、第二趟（補離線完成畫面）與第三趟（補四個缺口：沒有待開始時下一步指向還沒做完的那一間、離線恢復不進佇列且任務維持已中斷、下一間被改派走、完修回報不走完成畫面）。第三趟的 truth 已落在 feature 與 `dsl.md`，但**尚未寫進 `truth-delta.md`**，見 Scope Note 第 4 條。

編號採**追加制**：已完成的 T401–T441 一律不重新編號，第三趟新增的測試層任務編號接在 T442 之後，但**執行順序上 T442 仍是 Phase 3 的最後一題**。

## Task Binding Contract

- 每個**開發任務**都必須對應 `truth-delta.md` 的 ADD / MODIFY / DELETE 語意。
- 本輪沒有新增技術，**省略 Setup phase**。
- Phase 2 `Foundational` 只建立後續實作與測試共用的元件、fixture 與落點骨架；每則寫「只做／不做」。
- Phase 3 `Test Alignment & Implementation` 在寫產品碼之前，把本輪所有受影響 DSL 的自動化測試對齊最新版 truth。
- Truth 參照使用 `specs/truth/**`；plan 參照才使用本 plan package 內相對路徑。
- Feature `Shared Must Read` 除 feature、同模組 DSL、對應 `truth-delta` 列外，還必須列入回追到的 planner 產物。

## 工作區既有改動（不重複實作，但測試一條都不能少）

工作區已有兩筆先行完成的產品碼改動，**未提交**：

| 檔案 | 已做了什麼 | 對應 |
| --- | --- | --- |
| `app/mobile/lib/features/task/task_screen.dart` | 完成畫面改用 `opaque: false` 的覆蓋層路由推上去（原本是不透明路由，半透明遮罩後面是一片黑） | NFR-003、`完成畫面蓋在 "{任務}" 的任務頁上` |
| `app/mobile/lib/core/api_client.dart` ＋ `task_screen.dart` | 補 `resumeTask`，主按鈕接上 `'恢復處理' => _resume(task)`（原本落在 `_ => () {}` 空實作） | FR-009～FR-013 |

`/implement` 走到 Phase 4D／4E 的 `[BDD-GREEN]` 時，**先跑 Test Scope 確認這兩筆是否真的讓對應場景變綠**，綠的就標記完成、不重寫；但 Phase 3 的測試層任務一個都不能跳——這兩個行為原本就是「沒有測試涵蓋」才會壞掉的。

## Scope Note（停止範圍與回交）

1. **`contracts` 兩處用語殘留回交 `/api-plan`**（不阻塞本輪）。
   `specs/truth/contracts/groups/現場作業/員工工作台.yaml:10` 與 `specs/truth/contracts/components/schemas/員工工作台.yaml:30` 仍寫著 `completedCount`「供完成慶祝頁『第 N 單』使用」。feature truth 已改正為「第 N 單來自 `tasksComplete` 回應的 `cleaningDoneToday`」。另有 `tenant_config.key` 契約缺 `unclaimed_reminder_minutes`、`notification_retention_days` 兩鍵，可一併處理。

2. **`backend/今日進度/今日進度與下一間.feature` 的 MODIFY 沒有 Feature phase。**
   本輪只改 Rule 標題與 `今日進度/dsl.md` 的說明文字（刪掉「供完成慶祝頁『第 N 單』使用」的宣告），`今日進度的 "{項目}" 為 "{值}"` 這句的斷言語意與既有 stepdef 都沒有變，無產品碼與測試層影響。

3. 上一版 tasks.md 把「離線完成畫面」列為回交 `/dsl-refine` 的停止範圍。**該缺口已於 `/dsl-refine` 第二趟補齊**（`完成畫面.feature` 新增 5 個 Rule、新增一個離線完成 Given、`"{員工}" 的手機恢復連線` 上提到介面根），本輪已納入正常範圍，不再是停止範圍。

4. **第三趟 `/dsl-refine`（002 本體）沒有寫進 `truth-delta.md`，回交 `/dsl-refine` 補登（不阻塞本輪）。**
   truth 本身已完整落地並通過拓樸稽核——`完成畫面.feature` 22 個 Rule、`從任務頁接回中斷的單.feature` 8 個 Rule、`工務單/工務視角.feature` 新增 1 個 Rule、三句新 DSL（`任務執行/dsl.md` 的 Then `這次操作沒有留在手機上等補送` 與 Given `"{任務}" 剛被主管改派給別人，"{員工}" 的畫面還沒更新`、`工務單/dsl.md` 的 Then `不出現完成畫面`）。本檔的任務因此直接以 `dsl.md` 與 feature 為權威來源寫出；`truth-delta.md` 缺的是那張變更表，補登之後 Phase 3 新題與 Phase 4D／4E／4F 的 `Shared Must Read` 要把該列加進去。

5. **`ui/ui-plan.md` 自我矛盾，回交 `/ui-plan`（不阻塞本輪）。**
   第 35 行（畫面 2 的失敗回饋）寫「這一間已經不在你名下了」，第 132 行（未另外做成頁面的狀態）卻寫沿用任務頁既有的「找不到這個房號的任務」。兩句不一致，且第 132 行弱於 spec 驗收要求（要讓員工知道單被改派，不是看起來像個 bug）。feature truth 依 acceptance 與第 35 行寫成 `畫面提示 "這一間已經不在你名下了"`，本檔據此落地；第 132 行待 `/ui-plan` 修正。

7. **`"{員工}" 的手機有 {張數} 張待送出的 {房號} 冷氣工務單` 的張數上限回交 `/dsl-refine`（不阻塞本輪）。**
   `工務單/dsl.md` 的參數寫「張數：整數」，但這一句物理上一次只押得出 1 張——面板必須在切離線之前打開並填好（離線時點報修一律被擋），第一張送出之後手機已經離線，第二張連面板都打不開。T442 修正時把原本的 `for` 迴圈（第二圈起必定逾時、且錯誤訊息指向錯的地方）改成「張數不是 1 就快速失敗並說明原因」。唯一使用處 `報修照片與送出.feature:73` 傳的就是 1 張，行為不變；要改的是 dsl 那一列的參數說明。

8. **`工務單/報修照片與送出.feature` 有一個 `When` 為 0 的場景**，屬既有檔案、不在本輪三趟範圍內，本檔不產生任務，記在此避免下一個讀 truth 的人以為是本輪造成的。

## Phase 2: Foundational

**Goal**: 只建立 Phase 3／Phase 4 會用到的租戶設定鍵、完成回應保存落點與 App 端元素落點，不寫任何接力判斷或完成畫面版面。

- [X] T401 擴充 `tenant_config.key` CHECK，新增 `completion_celebration_enabled`
  - Read:
    - `truth-delta.md` -> `/api-plan` ADD `components/schemas/租戶設定.yaml` 封閉鍵集、`/data-plan` 第二趟 ADD
    - `specs/truth/data/data-model.dbml` -> `Table tenant_config.key` 封閉鍵集
    - `tms/src/main/resources/db/migration/` -> 既有 CHECK 擴充慣例（`DROP CONSTRAINT` 再 `ADD CONSTRAINT` 整個鍵集）
  - 只做：新增 migration，仿既有寫法重建 `chk_tenant_config_key` 為 dbml 現行的封閉鍵集，加入 `completion_celebration_enabled`（value 為 boolean，預設 true）。
  - 不做：不寫任何讀這個鍵的程式；關閉氣氛的呈現行為屬 Phase 4D。

- [X] T402 建立後端測試保存「上一次完成回應」的落點
  - Read:
    - `specs/truth/features/backend/任務執行/dsl.md` -> 6 個 `完成回報…` Then 句型的 `必查`
    - `tms/src/test/java/com/athena/tms/bdd/` -> `ScenarioWorld` 既有保存回應的方式
  - 只做：確認或擴充 world，讓 `"{員工}" 完成 "{任務}"` 之後的回應能被後續 Then 讀到 `nextStep` 與 `cleaningDoneToday`。
  - 不做：不寫任何 `完成回報…` 的 assertion，那是 Phase 3；不改 `tasksComplete` 產品碼。

- [X] T403 補完成畫面與任務頁恢復按鈕的 accessibility id 落點
  - Read:
    - `ui/ui-plan.md` -> 畫面 1～10 的「主要操作」
    - `app/mobile/lib/features/celebrate/celebrate_screen.dart`、`app/mobile/lib/features/task/task_screen.dart`
    - `app/bdd/src/test/java/com/athena/app/bdd/` -> `byTestId` helper
  - 只做：在既有元件上補齊本輪 Then 句型要定位的 accessibility id（完成畫面的標題、說明、兩塊統計、主按鈕、回首頁按鈕、氣氛元素容器、離線未送出提示；任務頁的恢復主按鈕沿用既有 `task.primary-button`）。
  - 不做：不改版面、不接任何新資料、不動 `celebrate.title` 等既有 id 的名稱（既有 stepdef 還在用）。

## Phase 3: Test Alignment & Implementation

**Goal**: 把本輪 Feature 用到的 DSL 自動化測試對齊最新版 truth；含 truth-delta 有改的句，以及本輪 Feature 用到、尚無 stepdef 的句。不寫產品行為。

**DSL 參照**:
- 每一句只屬於一個權威 `dsl.md`：同模組 `specs/truth/features/{介面}/{模組}/dsl.md`，或介面根 `specs/truth/features/{介面}/dsl.md`。不得掃其他模組。
- backend 的句都在 `specs/truth/features/backend/任務執行/dsl.md`。
- ios-app 的句大多在 `specs/truth/features/ios-app/任務執行/dsl.md`；**另有兩句在介面根** `specs/truth/features/ios-app/dsl.md`（`"{員工}" 在 App 完成 "{任務}"`、`"{員工}" 的手機恢復連線`），見下方 T416／T417。
- **另有一句在 `specs/truth/features/ios-app/工務單/dsl.md`**（`不出現完成畫面`），見下方 T445。它是工務單模組的句，不得拿任務執行模組的措辭代寫。
- 讀法：用 task title 的句型對到該檔那一列，以 `StepDef 實作語意` 當作測試程式碼語意。Given / When 讀 `怎麼做`、`權威狀態落地`、`回寫`；Then 讀 `必查`。
- `truth-delta.md` 只告訴這句是 ADD / MODIFY / DELETE。語意以 `dsl.md` 那一列為準，不得用 feature 措辭或舊 stepdef 自行發明。

**Markers**:
- `[BDD-ALIGN]`：`MODIFY`。既有 stepdef 還在，但語意是舊 truth，或這句的唯一權威位置被搬移。
- `[BDD-RED]`：`ADD`，或本輪 Feature 用到、尚無 stepdef 的句。完成時這句可被跑到，失敗只能是 assertion 或產品行為，不能是 undefined step。
- 本輪沒有 `DELETE`，不使用 `[BDD-REMOVE]`。
- 兩個 marker 都只動測試層，不寫產品碼。

**Shared Must Read**:
- `specs/truth/features/backend/任務執行/dsl.md` -> 本檔 Then 句型
- `specs/truth/features/ios-app/任務執行/dsl.md` -> 本檔 Given／When／Then 句型
- `specs/truth/features/ios-app/dsl.md` -> `"{員工}" 在 App 完成 "{任務}"`、`"{員工}" 的手機恢復連線`
- `specs/truth/features/ios-app/工務單/dsl.md` -> `不出現完成畫面`
- `truth-delta.md` -> `/dsl-refine` 的 backend 表、ios-app 表與第二趟表（第三趟尚未補登，見 Scope Note 第 4 條；該三句以 `dsl.md` 那一列為唯一權威）
- `tms/src/test/java/com/athena/tms/bdd/steps/taskexecution/`
- `app/bdd/src/test/java/com/athena/app/bdd/steps/taskexecution/`、`.../steps/common/`

**Boundary**:
- 一條 DSL 一個 task。
- 只改該句的 stepdef / assertion / 直接依賴的 helper。
- 不寫產品碼。
- review 啟動 subagent；本輪所有 Test Scope 不得再有 undefined step，失敗只能是 assertion 或產品行為。有 issues 就修正再 review，直到沒有任何問題。通過前不解鎖 Phase 4。

**Parallel Hint**:
- T410–T441 已交付；本輪只剩 T443–T445 三題，各派一個獨立 subagent，**T442 等這三題全部回來再啟動 subagent 來 review**（T442 的編號雖早於它們，執行順序以本節列出的先後為準）。
- T443 落在 `app/bdd/.../steps/taskexecution/`，T444 落在同目錄的 Given，T445 落在 `.../steps/repair/`。T443 與 T444 可能同檔，後寫入者自己 merge；T445 與前兩題不衝突。
- 已交付期間的既有提醒（保留備查）：backend（T410–T415）與 ios-app（T416–T441）落在不同 repo 目錄；T416 與 T425 都會動到 `app/bdd/.../steps/common/CommonThenSteps.java` 附近的完成畫面處理，不要同時派。

### backend / 任務執行

- [X] T410 [P] [BDD-RED] `"{任務}" 沒有停住原因`
- [X] T411 [P] [BDD-RED] `完成回報的下一步是待開始的 "{房號}"`
- [X] T412 [P] [BDD-RED] `完成回報的下一步是還沒做完的 "{房號}"`
- [X] T413 [P] [BDD-RED] `完成回報說今天排定的房都清完了`
- [X] T414 [P] [BDD-RED] `完成回報的今日進度為第 {單數} 單`
- [X] T415 [P] [BDD-RED] `完成回報不含今日進度`

### ios-app / 介面根共用 DSL

- [X] T416 [BDD-ALIGN] `"{員工}" 在 App 完成 "{任務}"`
  - Read:
    - `specs/truth/features/ios-app/dsl.md` -> 該列的 `回寫`（「完成慶祝頁」已改為「完成畫面」）
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/common/CommonWhenSteps.java:54`
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/common/CommonThenSteps.java:74-84` -> 既有「完成後會被帶到完成慶祝頁，點 `celebrate.back-home-button` 關掉」的處理
  - 本輪完成畫面改成**疊在任務頁上的覆蓋層**，且「回到首頁」降為底部文字按鈕、主按鈕改成接力。既有 stepdef 把完成畫面當成一整頁、固定點回首頁關掉的假設要跟著對齊。

- [X] T417 [BDD-ALIGN] `"{員工}" 的手機恢復連線`
  - Read:
    - `truth-delta.md` -> `/dsl-refine` 第二趟 MODIFY 權威位置列（舊位置 `ios-app/工務單/dsl.md`，新位置 `ios-app/dsl.md`，語意不變）
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/` -> 既有 stepdef 位置
  - 只對齊精確參照與既有測試入口（這句現在同時服務 `工務單` 與 `任務執行`），不得拆成移除再新增。

### ios-app / 任務執行 — Given

- [X] T418 [P] [BDD-RED] `飯店關閉了完成慶祝的氣氛呈現`
- [X] T419 [P] [BDD-RED] `"{員工}" 剛在 App 完成 "{任務}"，完成畫面停在畫面上`
- [X] T420 [P] [BDD-RED] `"{員工}" 已在離線時完成 "{任務}"，這一筆還押在手機上`
- [X] T421 [P] [BDD-RED] `"{任務}" 已中斷 {分鐘} 分鐘`

### ios-app / 任務執行 — When

- [X] T422 [P] [BDD-RED] `"{員工}" 在 App 恢復 "{任務}"`
- [X] T423 [P] [BDD-RED] `"{員工}" 在 App 從完成畫面前往下一步`
- [X] T424 [P] [BDD-RED] `"{員工}" 在 App 從完成畫面回到首頁`

### ios-app / 任務執行 — Then

- [X] T425 [BDD-ALIGN] `完成畫面顯示這次用時 {分鐘} 分、當初預估 {分鐘2} 分`
  - Read:
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/taskexecution/TaskExecutionThenSteps.java:220` -> `@那麼("完成慶祝頁顯示實際 {int} 分、預估 {int} 分")`
    - `specs/truth/features/ios-app/任務執行/dsl.md` -> 新列的 `必查`（實際是主要數字、預估是附註，兩者不是同一級）
- [X] T426 [P] [BDD-RED] `完成畫面只顯示這次用時 {分鐘} 分`
- [X] T427 [P] [BDD-RED] `完成畫面的標題帶出房號 "{房號}"`
- [X] T428 [P] [BDD-RED] `完成畫面說明 "{說明}"`
- [X] T429 [P] [BDD-RED] `完成畫面的下一步指向 "{房號}"`
- [X] T430 [P] [BDD-RED] `完成畫面提示今天排定的房都清完了，下一步是去待認領`
- [X] T431 [P] [BDD-RED] `完成畫面顯示今日進度為第 {單數} 單`
- [X] T432 [P] [BDD-RED] `完成畫面不顯示今日進度`
- [X] T433 [P] [BDD-RED] `完成畫面說明這一筆還沒送出`
- [X] T434 [P] [BDD-RED] `完成畫面蓋在 "{任務}" 的任務頁上`
- [X] T435 [P] [BDD-RED] `完成畫面不出現慶祝的氣氛`
- [X] T436 [P] [BDD-RED] `完成畫面底部有回到首頁的出口，且它不是最強調的動作`
- [X] T437 [P] [BDD-RED] `畫面停在 "{任務}" 的任務頁`
- [X] T438 [P] [BDD-RED] `畫面停在待認領`
- [X] T439 [P] [BDD-RED] `畫面停在首頁`
- [X] T440 [P] [BDD-RED] `任務頁顯示已做 {分鐘} 分鐘，且計時正在走`
- [X] T441 [P] [BDD-RED] `任務頁不再顯示停住原因`

### ios-app / 任務執行 — 第三趟新增

- [X] T443 [P] [BDD-RED] `這次操作沒有留在手機上等補送`
  - Read:
    - `specs/truth/features/ios-app/任務執行/dsl.md` -> 該列的 `必查`
    - `app/mobile/lib/core/connection_status.dart`、`app/bdd/src/test/java/com/athena/app/bdd/steps/common/` -> 離線列待同步筆數 `system.offline-bar.pending-count` 的既有定位方式
  - 必查有兩面，缺一不可：①App 本機待送出佇列沒有因這次操作而增加（`system.offline-bar.pending-count` 與操作前相同；本來就沒有待同步時這一列不出現，等同 0）②後端該任務狀態與操作前一樣。
  - **只驗畫面提示不算數**——提示出現但動作偷偷進了佇列，連線恢復後就會送出一個員工以為沒有發生的操作，這一句存在的全部理由就是擋這件事。
  - 這句只用於**離線被擋在送出之前**的動作（恢復處理、開始、認領、延後）；「離線完成」相反，它會押進佇列等補送，兩者不可互相沿用。

- [X] T444 [P] [BDD-RED] `"{任務}" 剛被主管改派給別人，"{員工}" 的畫面還沒更新`
  - Read:
    - `specs/truth/features/ios-app/任務執行/dsl.md` -> 該列的 `怎麼做`、`權威狀態落地`
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/taskexecution/TaskExecutionGivenSteps.java` -> 同表「剛被主管作廢」那一句的既有落地手法
  - 另建一位同部門員工，以主管身分呼叫 `tasksReassign`（`POST /tasks/{id}/reassign`）改派過去；不點開任何推播、不觸發畫面刷新。
  - 與同表的「剛被主管作廢」是兩件事：作廢把任務轉 cancelled，改派只換 `assignee_id`、狀態不變。**不得共用 annotation 或互相沿用落地碼。**

### ios-app / 工務單 — 第三趟新增

- [X] T445 [P] [BDD-RED] `不出現完成畫面`
  - Read:
    - `specs/truth/features/ios-app/工務單/dsl.md` -> 該列的 `必查`
    - `app/mobile/lib/features/celebrate/celebrate_screen.dart` -> `celebrate.screen` 覆蓋層
    - `app/bdd/src/test/java/com/athena/app/bdd/steps/repair/` -> 既有完修回報 stepdef 位置
  - 必查：畫面上沒有 `celebrate.screen` 這一層覆蓋層，員工留在工務單自己的回報流程上。**只檢查「有沒有跳走」不夠**，要確認那一層根本沒出現。

- [X] T442 subagent review (phase quality gate) — **Phase 3 的最後一題**，等 T443–T445 全部回來再啟動

## Phase 4A: ADD Feature File - backend/任務執行/完成之後的接續去向.feature

**Goal**: 讓 `tasksComplete` 的回應帶出接續去向與今日進度，三種去向靠 `kind` 明確分辨。

**Shared Must Read**:
- `specs/truth/features/backend/任務執行/完成之後的接續去向.feature` -> `功能: 完成之後的接續去向`
- `specs/truth/features/backend/任務執行/dsl.md` -> 5 個 `完成回報…` Then 句型
- `specs/truth/features/backend/dsl.md` -> 本檔實際用到的介面根共用 rows：`飯店有以下員工：`、`"{員工}" 名下有以下任務：`、`"{員工}" 完成 "{任務}"`
- `truth-delta.md` -> `/dsl-refine` backend 表 ADD 兩列
- `specs/truth/contracts/components/schemas/任務.yaml` -> `NextStep`、`TaskCompletion`
- `specs/truth/contracts/groups/現場作業/任務.yaml` -> `tasksComplete` 的 200 回應
- `specs/truth/data/data-model.dbml` -> `Table tasks` 的 `Note`「完成當下的兩個衍生值」

**Boundary**:
- 只動 `tasksComplete` 的回應組裝與其依賴的查詢；不改狀態轉移、不改 `tasksResume`。
- 兩個衍生值一律查詢期計算，不得新增欄位或計數器（data truth 已判定 NOOP）。

**Test Scope**:
- `specs/truth/features/backend/任務執行/完成之後的接續去向.feature`

- [X] T450 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T451 [BDD-REFACTOR] 在綠燈下整理接續去向查詢與 `getMyToday` 的共用排序，確保兩處指向同一間

## Phase 4B: MODIFY Feature File - backend/任務執行/一次只做一間.feature

**Goal**: 恢復處理套用與開工相同的「一次只做一間」判定。

**Shared Must Read**:
- `specs/truth/features/backend/任務執行/一次只做一間.feature` -> Rule「已有進行中的單時，也不能把中斷的單恢復回來」
- `specs/truth/features/backend/dsl.md` -> `"{員工}" 恢復 "{任務}"`、`這次操作被拒絕，提示 "{提示}"`、`"{任務}" 的停住原因為 "{原因}"`
- `truth-delta.md` -> `/dsl-refine` backend 表 ADD `一次只做一間.feature` 列
- `specs/truth/contracts/groups/現場作業/任務.yaml` -> `tasksResume` 的 409 語意
- `specs/truth/data/data-model.dbml` -> `uq_tasks_one_doing_per_staff`

**Boundary**:
- 只確認恢復路徑有沒有走到既有的一次一間守衛；不得為恢復另立一套判定或新錯誤碼。

**Test Scope**:
- `specs/truth/features/backend/任務執行/一次只做一間.feature`

- [ ] T452 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T453 [BDD-REFACTOR] 在綠燈下確認開工與恢復共用同一個守衛入口

## Phase 4C: MODIFY Feature File - backend/任務執行/中斷恢復與實際工時.feature

**Goal**: 恢復時清空停住原因。

**Shared Must Read**:
- `specs/truth/features/backend/任務執行/中斷恢復與實際工時.feature` -> Rule「恢復後從中斷前的已做時間接續，不含中斷期間，停住原因跟著清掉」
- `specs/truth/features/backend/任務執行/dsl.md` -> `"{任務}" 沒有停住原因`
- `truth-delta.md` -> `/dsl-refine` backend 表 MODIFY `中斷恢復與實際工時.feature` 列
- `specs/truth/contracts/groups/現場作業/任務.yaml` -> `tasksResume` 的 description
- `specs/truth/data/data-model.dbml` -> `tasks.hold_reason`（離開 deferred 或 interrupted 時清空）

**Boundary**:
- 只處理 `hold_reason` 的清空；計時接續行為既有測試已覆蓋，不得改動。

**Test Scope**:
- `specs/truth/features/backend/任務執行/中斷恢復與實際工時.feature`

- [ ] T454 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T455 [BDD-REFACTOR] 在綠燈下確認所有離開 deferred／interrupted 的路徑都走同一段清空

## Phase 4D: ADD Feature File - ios-app/任務執行/完成畫面.feature

**Goal**: 完成畫面對齊設計原型，三種接續去向都能走、離線照常給回饋、下一間被改派走時誠實說明，且它是疊在任務頁上的覆蓋層。

**Shared Must Read**:
- `specs/truth/features/ios-app/任務執行/完成畫面.feature` -> `功能: 完成畫面`（22 個 Rule，含 5 個離線 Rule；第三趟新增「沒有待開始的單時，下一步指向還沒做完的那一間」、「下一間在按下之前被改派走時，誠實說它已經不在名下」，並把原本「少了慶祝，但接力照舊」那一條拆成「關閉氣氛呈現時，完成畫面不出現慶祝的元素」與「關閉氣氛呈現時，接力照舊」兩個 Rule）
- `specs/truth/features/ios-app/任務執行/dsl.md` -> 本檔 Given／When／Then 句型
- `specs/truth/features/ios-app/dsl.md` -> `"{員工}" 在 App 完成 "{任務}"`、`"{員工}" 的手機恢復連線`、`手機目前離線`、`"{任務}" 在 App 顯示為 "{狀態}"`
- `truth-delta.md` -> `/dsl-refine` ios-app 表 ADD `完成畫面.feature`、ADD dsl 句型、MODIFY 用時句型三列，以及第二趟全部三列（第三趟尚未補登，見 Scope Note 第 4 條）
- `specs/truth/features/ios-app/任務執行/dsl.md` -> Given `"{任務}" 剛被主管改派給別人，"{員工}" 的畫面還沒更新`
- `specs/truth/features/ios-app/dsl.md` -> `畫面提示 "{提示}"`
- `ui/ui-plan.md` -> 畫面 2～8 的主要目的、主要操作、成功流轉，以及「視覺方向」與「前端實作切分建議」
- `ui/celebrate-next.html`、`ui/celebrate-resume.html`、`ui/celebrate-all-done.html`、`ui/celebrate-waived.html`、`ui/celebrate-amenity.html`、`ui/celebrate-offline.html`、`ui/celebrate-quiet.html`、`ui/celebrate.css`
- `specs/truth/contracts/components/schemas/任務.yaml` -> `NextStep`、`TaskCompletion`

**Boundary**:
- 完成畫面維持單一元件，七種狀態由傳入值決定，不拆成七個畫面類別。
- 三種去向一律看 `nextStep.kind`，不得用「欄位是不是 null」推斷。
- 離線時的下一步與第 N 單以手機上已取得的今日進度推算；不得為了拿這兩個值在離線時阻擋完成畫面。
- 「沒有待開始的單」時下一步指向還沒做完的那一間，走的是 `nextStep.kind` = `unfinished_task`，與 `all_clear` 必須在畫面上可分辨——員工看到的結論相反，不得讓兩者長一樣。
- 下一間在按下之前被改派走時，提示用 `ui/ui-plan.md` 第 35 行的「這一間已經不在你名下了」，**不是**任務頁既有的「找不到這個房號的任務」（`ui-plan.md` 第 132 行與第 35 行矛盾，以第 35 行與 acceptance 為準，見 Scope Note 第 5 條）。

**Test Scope**:
- `specs/truth/features/ios-app/任務執行/完成畫面.feature`

- [ ] T456 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T457 [BDD-REFACTOR] 在綠燈下把接續按鈕抽成依 `nextStep.kind` 決定文字與去處的子元件，並讓線上與離線共用同一條組裝路徑

## Phase 4E: ADD Feature File - ios-app/任務執行/從任務頁接回中斷的單.feature

**Goal**: 任務頁的「恢復處理」真的能把中斷的單接回來，並在撞一次一間與離線時被正確擋下。

**Shared Must Read**:
- `specs/truth/features/ios-app/任務執行/從任務頁接回中斷的單.feature` -> `功能: 從任務頁接回中斷的單`（8 個 Rule；第三趟新增「離線被擋下的恢復不會留在手機上等補送」、「離線被擋下之後任務維持已中斷」，並把原本「恢復成功」那一條的標題收成單一結果）
- `specs/truth/features/ios-app/任務執行/dsl.md` -> `"{員工}" 在 App 恢復 "{任務}"`、`任務頁不再顯示停住原因`、`任務頁顯示已做 {分鐘} 分鐘，且計時正在走`、`"{任務}" 已中斷 {分鐘} 分鐘`、`這次操作沒有留在手機上等補送`
- `specs/truth/features/ios-app/dsl.md` -> `畫面提示 "{提示}"`、`"{任務}" 在 App 顯示為 "{狀態}"`、`手機目前離線`
- `truth-delta.md` -> `/dsl-refine` ios-app 表 ADD `從任務頁接回中斷的單.feature`、ADD When 恢復句型兩列（第三趟尚未補登，見 Scope Note 第 4 條）
- `ui/ui-plan.md` -> 畫面 9、10 的主要操作與失敗回饋
- `ui/task-interrupted.html`、`ui/task-busy-alert.html`
- `specs/truth/contracts/groups/現場作業/任務.yaml` -> `tasksResume`

**Boundary**:
- 離線一律擋在送出之前，不得進離線佇列（與「完成」的離線行為相反，不要沿用）。這條現在有測項盯著：`這次操作沒有留在手機上等補送` 會驗待同步筆數沒增加，光顯示提示不會變綠。
- 離線與撞一次一間兩種擋下都不得改動任務狀態，任務維持已中斷、停住原因不變。
- 撞一次一間的提示文字用後端 409 的 message，App 不自己湊。

**Test Scope**:
- `specs/truth/features/ios-app/任務執行/從任務頁接回中斷的單.feature`

- [ ] T458 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T459 [BDD-REFACTOR] 在綠燈下讓開工與恢復共用同一段離線擋下與 409 提示處理

## Phase 4F: MODIFY Feature File - ios-app/工務單/工務視角.feature

**Goal**: 工務單的完修回報留在工務自己的流程上，不跳出房務的完成畫面。

**Shared Must Read**:
- `specs/truth/features/ios-app/工務單/工務視角.feature` -> Rule「完修回報不走房務的完成畫面」
- `specs/truth/features/ios-app/工務單/dsl.md` -> `不出現完成畫面`、`"{工務}" 在工務單詳情完修，回報結果 "{結果}"，附 {張數} 張照片`
- `specs/truth/features/ios-app/dsl.md` -> `已有以下工務單：`
- `truth-delta.md` -> `/dsl-refine` 第三趟（尚未補登，見 Scope Note 第 4 條；語意以 `dsl.md` 那一列為準）
- `ui/ui-plan.md` -> 第 120 行「工務單的完修回報不走這張卡」
- `app/mobile/lib/features/ticket/ticket_screen.dart` -> 完修回報面板的送出後流程
- `app/mobile/lib/features/celebrate/celebrate_screen.dart` -> `celebrate.screen` 覆蓋層的推上去路徑

**Boundary**:
- 只確認完修送出後沒有任何路徑把完成畫面推上來；**不得**為了讓測試變綠而在完成畫面裡加「如果是工務單就不顯示」的分支——完修本來就不該走到那段程式碼，分支代表推上去的路徑還在。
- 不改完修回報的其他行為（照片必填、結果枚舉、追蹤單建立）。
- 本檔的另外兩個 Rule 已於 Addendum Phase 4C 交付；Test Scope 是整份檔案，既有場景不得變紅。

**Test Scope**:
- `specs/truth/features/ios-app/工務單/工務視角.feature`

- [ ] T461 [BDD-GREEN] 讓 Test Scope 全綠
- [ ] T462 [BDD-REFACTOR] 在綠燈下確認「完成畫面要不要推上去」只由房務完成那一條路徑決定，工務單側沒有任何殘留的導頁呼叫

## Phase 4G: UI quality gate

**Goal**: 產品對齊本輪回追到的 plan-side 雛形家族。`Test Scope` 全綠不能代替本題。

**Read**:
- `ui/ui-plan.md` -> 視覺方向、畫面與流程、前端實作切分建議
- `ui/index.html`、`ui/celebrate-next.html`、`ui/celebrate-resume.html`、`ui/celebrate-all-done.html`、`ui/celebrate-waived.html`、`ui/celebrate-amenity.html`、`ui/celebrate-offline.html`、`ui/celebrate-quiet.html`、`ui/task-interrupted.html`、`ui/task-busy-alert.html`、`ui/celebrate.css`
- `app/mobile/lib/features/celebrate/`、`app/mobile/lib/features/task/task_screen.dart`

**Boundary**:
- 不改 truth、不加 feature、不重跑 GREEN 當通過條件。
- 重點比對：卡片圓角 28、實心主按鈕 52 高只給「往下一步走」、回到首頁是 44 高透明文字按鈕、統計磚左右配色與主副字級、彩屑浮動與入場動態、離線提示行的位置與顏色、氣氛關閉時消失的是哪幾個元素。
- 另比對第三趟新增的兩種樣態：`unfinished_task` 的主按鈕文案與 `next_task` 可分辨（`ui/celebrate-resume.html` 對 `ui/celebrate-next.html`）、下一間被改派走時的提示用「這一間已經不在你名下了」。`ui-plan.md` 第 132 行與第 35 行矛盾（Scope Note 第 5 條），本題只比對第 35 行那一句，矛盾本身回交 `/ui-plan`，不在這裡改。

- [ ] T463 subagent review (ui 雛形 quality gate)

---

# Addendum: 修繕推播導頁與完修照片（現場 bug，非 002 FR）

**對應 truth**：`truth-delta.md` 的「第二趟／第三趟 `/dsl-refine`（現場 bug 驅動）」與「第二趟 `/api-plan`」三節（2026-11-09）。與 002 本體（T401–T463）互不依賴，可獨立實作；編號自 T480 起。

**背景**：修繕任務（工務單）的推播被 App 一律導到任務頁，員工按裸「完成」被後端 422 `completion_photo_required` 拒收且無回饋。truth 已定案：`PushTarget.kind` 加 `ticket`（修繕任務由後端發 ticket、App 機械導向工務單詳情），完修缺照片時送出鈕禁用。

本 Addendum 無新增技術（省略 Setup），也無 Foundational——沒有跨 phase 共用的新元件或落點骨架：推播 payload 組裝與工務單 fixture 都已存在，完修回報面板與送出鈕已在產品碼裡。

## Addendum 工作區既有行為（不重複實作，但測試一條都不能少）

| 檔案 | 現況 | 對應 |
| --- | --- | --- |
| `app/mobile/lib/features/ticket/ticket_screen.dart`（完修回報面板） | 沒照片或沒選結果時送出鈕已是禁用（`canSubmit`） | `完修回報沒附照片時送出鈕禁用` 的 Rule——GREEN 時先跑 Test Scope 確認，綠的就標記完成、不重寫 |

## Addendum Scope Note（停止範圍與回交）

1. **任務頁「完成」被後端拒收時的錯誤呈現回交 `/dsl-refine`。** `task_screen.dart` 的 `_complete` 吞掉 `ApiException`（打掃類撞 409 等也無聲），但 `ios-app` truth 目前沒有任何場景承接「完成被拒時畫面提示」。本 Addendum **不產生**這個範圍的任務；待 `/dsl-refine` 補場景後再排。
2. 修繕任務經 ticket 導頁修正後不再進任務頁，任務頁裸「完成」的路徑對修繕任務自然消失，不另立隱藏按鈕的任務。
3. T488 review 發現的既有低度缺口（非本輪引入）：`tms/src/test/java/com/athena/tms/bdd/steps/common/CommonThenSteps.java:279` 的「待認領」去處只驗 `kind`=pool、沒驗 dsl 要求的 `dept`——回交下一輪 BDD-ALIGN，不擋本 gate。
4. T491 盤點發現、回交 `/dsl-refine`／`/api-plan` 裁決（非本輪引入）：①`RepairService.resolveOnComplete`（`RepairService.java:404`）完修推播報修人時 target 是修繕單的 task id＋`kind`=task，但 truth 只驗這封的標題、未定去處——報修人要回的是「自己原任務的任務頁」還是「該工務單詳情」待上游定；②契約允許主管作廢任何未完成態任務且去處固定任務頁，作廢工務單時是否該改 ticket 同屬上游。
5. T496 gate 的 low 級觀察（通過、不擋）：①ui-plan §7「已修好」與用語表定案的「已完修」不一致，下次動 ui-plan 時一併改；②`工務單/dsl.md:79` 要求已完修詳情顯示追蹤單提示／房況停用說明，App 未呈現——與「詳情沒回 resolutionType」的既有上游缺口同源，一起回交；③`notification_screen.dart:95` 的 `openTarget` 無 try/catch，task/pool 路徑的 `getTask` 失敗會無聲（ticket 路徑不受影響），留待後續。

## Addendum Phase 3: Test Alignment & Implementation

**Goal**: 把本輪三個 MODIFY feature file 用到的 DSL 自動化測試對齊最新版 truth。不寫產品行為。

**DSL 參照**:
- ios-app 推播句在 `specs/truth/features/ios-app/推播/dsl.md`；ios-app 工務單句在 `specs/truth/features/ios-app/工務單/dsl.md`；`已有以下工務單：` 在介面根 `specs/truth/features/ios-app/dsl.md`（本輪由工務單模組升格，語意不變）。
- backend 句：`"{員工}" 收到以下推播：` 在介面根 `specs/truth/features/backend/dsl.md`；改派 When 在 `specs/truth/features/backend/推播/dsl.md`。
- 讀法同本檔 Phase 3：Given／When 讀 `怎麼做`、`權威狀態落地`、`回寫`；Then 讀 `必查`。

**Markers**:
- `[BDD-ALIGN]`：MODIFY，既有 stepdef 在但語意是舊 truth（去處枚舉沒有工務單詳情、改派只收房號、權威位置搬移的參照）。
- `[BDD-RED]`：ADD 的三句完修回報句型。完成時失敗只能是 assertion 或產品行為，不能是 undefined step。
- 本輪沒有 DELETE。

**Shared Must Read**:
- `specs/truth/features/ios-app/推播/dsl.md`、`specs/truth/features/ios-app/工務單/dsl.md`、`specs/truth/features/ios-app/dsl.md` -> `已有以下工務單：`
- `specs/truth/features/backend/dsl.md` -> `"{員工}" 收到以下推播：`、`specs/truth/features/backend/推播/dsl.md` -> 改派 When
- `truth-delta.md` -> 第二趟／第三趟 `/dsl-refine`（現場 bug 驅動）兩張表、第二趟 `/api-plan` 表
- `specs/truth/contracts/components/schemas/shared.yaml` -> `PushTarget`
- `app/bdd/src/test/java/com/athena/app/bdd/steps/push/`、`app/bdd/src/test/java/com/athena/app/bdd/steps/repair/`
- `tms/src/test/java/com/athena/tms/bdd/steps/push/`、`tms/src/test/java/com/athena/tms/bdd/steps/repair/`

**Boundary**: 一條 DSL 一個 task；只改該句 stepdef／assertion／直接依賴的 helper；不寫產品碼。review 由 subagent 執行，通過前不解鎖 Addendum Phase 4。

**Parallel Hint**: T480–T487 各派一個獨立 subagent；app/bdd 與 tms 落在不同目錄互不衝突。T488 等全部回來再 review。

### ios-app / 推播

- [X] T480 [P] [BDD-ALIGN] `"{員工}" 收到一則推播 "{標題}"，去處為 "{去處}"`——去處新增「X 的工務單詳情（X＝單號）」形式，payload 帶 ticket 去處與該修繕任務 id（`PushGivenSteps` 目前認不得會直接丟例外）
- [X] T481 [P] [BDD-ALIGN] `App 停在 "{畫面}"`——畫面新增工務單詳情形式：斷言停在該單號的工務單詳情頁，不是任務頁（`PushThenSteps`）

### ios-app / 工務單

- [X] T482 [P] [BDD-ALIGN] `已有以下工務單：`——權威位置由 `工務單/dsl.md` 升格 `ios-app/dsl.md`，語意不變；只對齊 stepdef 註解的 dsl 參照與推播模組場景可用性（`RepairGivenSteps`）
- [X] T483 [P] [BDD-RED] `"{工務}" 在工務單詳情打開完修回報，選結果 "{結果}"，附 {張數} 張照片`（0 張代表不拍；停在面板上不送出）
- [X] T484 [P] [BDD-RED] `完修回報的送出鈕為 "{按鈕狀態}"`（禁用時無法真的觸發送出）
- [X] T485 [P] [BDD-RED] `後端沒有收到這次完修`（後端該單仍維修中，無完修事件／照片／結果）

### backend / 推播

- [X] T486 [P] [BDD-ALIGN] `"{主管}" 把 "{任務}" 改派給 "{員工}"`——任務參數放寬收工務單單號（解析 W- 單號為 task id）
- [X] T487 [P] [BDD-ALIGN] `"{員工}" 收到以下推播：`——去處新增「X 的工務單詳情」：驗 `kind`=ticket＋該修繕任務 `taskId`；並確認 task 形式只對打掃／備品成立

- [X] T488 subagent review (addendum phase quality gate)

## Addendum Phase 4A: MODIFY Feature File - backend/推播/員工推播事件.feature

**Goal**: 改派修繕任務時，新負責人那封推播的去處是 `kind`=ticket＋該單 task id。

**Shared Must Read**:
- `specs/truth/features/backend/推播/員工推播事件.feature` -> Rule「別人的單改派給本人時推播新負責人，點開到該單」
- `specs/truth/features/backend/推播/dsl.md` -> 改派 When 的 `權威狀態落地`
- `truth-delta.md` -> 第三趟 `/dsl-refine` backend 列、第二趟 `/api-plan` 列
- `specs/truth/contracts/components/schemas/shared.yaml` -> `PushTarget`

**Boundary**:
- 只動推播 outbox 寫入端的 target 組裝；不改 `tasksReassign` 的狀態轉移與收件人規則。
- 「任務類別 → PushTarget.kind」的判斷收在單一處；其他發推播的入口本輪不主動改——它們的目標任務本來就不是修繕類。

**Test Scope**:
- `specs/truth/features/backend/推播/員工推播事件.feature`

- [X] T490 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T491 [BDD-REFACTOR] 在綠燈下把「任務類別 → PushTarget.kind」的對應收斂成唯一出處

## Addendum Phase 4B: MODIFY Feature File - ios-app/推播/點開推播的去處.feature

**Goal**: App 收到 ticket 去處時導向該單的工務單詳情；照 kind 機械導頁，不查任務類別。

**Shared Must Read**:
- `specs/truth/features/ios-app/推播/點開推播的去處.feature` -> Rule「修繕任務（工務單）的推播點開後停在工務單詳情，不是任務頁」
- `specs/truth/features/ios-app/推播/dsl.md` -> 兩句 MODIFY 的實作語意
- `truth-delta.md` -> 第二趟 `/dsl-refine`（現場 bug 驅動）ios-app 表
- `specs/truth/contracts/components/schemas/shared.yaml` -> `PushTarget`
- `specs/plans/001-staff-app-increment/ui/ui-plan.md` -> §13 工務視角（完修入口在工務單詳情）
- `app/mobile/lib/core/push_gateway.dart`、`app/mobile/lib/features/ticket/ticket_screen.dart`

**Boundary**:
- 只動 `push_gateway.dart` 的導頁分支（加 ticket 一種，導 `TicketScreen`）；不改 task／pool／home 的既有行為。
- App 不得為了導頁先打 `GET /tasks/{id}` 查類別——kind 是後端結論。

**Test Scope**:
- `specs/truth/features/ios-app/推播/點開推播的去處.feature`

- [X] T492 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T493 [BDD-REFACTOR] 在綠燈下確認通知中心點開同一筆（`NotificationItem.target` 共用 `PushTarget`）走同一段導頁，不是各寫各的 switch

## Addendum Phase 4C: MODIFY Feature File - ios-app/工務單/工務視角.feature

**Goal**: 完修回報沒附照片時送出鈕禁用，且後端收不到這次完修。

**Shared Must Read**:
- `specs/truth/features/ios-app/工務單/工務視角.feature` -> Rule「完修回報沒附照片時送出鈕禁用」
- `specs/truth/features/ios-app/工務單/dsl.md` -> 三句 ADD 的實作語意
- `truth-delta.md` -> 第二趟 `/dsl-refine`（現場 bug 驅動）ios-app 表
- `specs/plans/001-staff-app-increment/ui/ui-plan.md` -> §13「完修缺照片 → 送出鈕禁用」
- `app/mobile/lib/features/ticket/ticket_screen.dart` -> 完修回報面板（`canSubmit` 既有禁用行為）

**Boundary**:
- 先跑 Test Scope——既有 `canSubmit` 大概率已綠，綠的不重寫；只補真的缺的（例如送出鈕的 accessibility id 若測試定位不到）。
- 不改完修送出後的任何流程。

**Test Scope**:
- `specs/truth/features/ios-app/工務單/工務視角.feature`

- [X] T494 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T495 [BDD-REFACTOR] 在綠燈下確認完修回報與報修面板的「缺照片禁用」判斷語意一致、沒有複製貼上殘留

## Addendum Phase 4D: ui-plan quality gate

**Goal**: 產品對齊回追到的 plan-side 產物。`Test Scope` 全綠不能代替本題。

**Read**:
- `specs/plans/001-staff-app-increment/ui/ui-plan.md` -> §13 工務視角、§7 工務單詳情
- `app/mobile/lib/core/push_gateway.dart`、`app/mobile/lib/features/ticket/ticket_screen.dart`

**Boundary**: 不改 truth、不加 feature、不重跑 GREEN 當通過條件。重點比對：修繕推播與通知中心點開都落在工務單詳情；完修回報面板的照片欄與送出鈕狀態語意。

- [X] T496 subagent review (ui-plan 工務視角 quality gate)

---

# Addendum 2: 任務頁完成被拒不得無聲（現場 bug 第二半，非 002 FR）

**對應 truth**：`truth-delta.md` 的「第四趟 `/dsl-refine`」（2026-11-09）。與 002 本體及 Addendum（T480–T496，已全數交付）互不依賴；編號自 T500 起。

**背景**：任務頁 `_complete` 吞掉 `ApiException`——完成被後端拒收（打掃類的真實競態：任務在背後被主管作廢，`tasksComplete` 回 409）時畫面毫無反應。truth 第四趟已補 Rule「完成被後端拒收時顯示後端的原因，不會無聲」與三句新 DSL。

本 Addendum 無新增技術（省略 Setup），也無 Foundational——`task.busy-alert` 的提示元件與 `_showBusyAlert` 已存在於 `task_screen.dart`，無跨 phase 共用的新落點。

**T506 gate 的回交項與觀察（通過、不擋）**：①001 ui-plan F2-R1 的 id 表仍寫 `task.busy-alert.go-to-task-button`，實作、002 ui-plan 與 BDD 三方一致用 `dismiss-button` 且沒有「去看那間」——落伍的是 001 的表，下次動 001 ui-plan 時改；②`_complete` 只接 `ApiException`，「送出後收不到回應」的 `TimeoutException`／socket 錯誤仍會無聲（與缺口 #35 注入開關同路徑）——與本條 bug 教訓同型，待下一輪 truth 補場景。

**T504 發現、回交後端的缺口**：`TaskEngine.cancel`（`tms/src/main/java/com/athena/tms/service/TaskEngine.java:376-381`）作廢時只把原因記進 `task_events` payload，沒把 `tasks.cancel_reason` 落地（`staff_withdrew` 那條路有寫）。dsl 宣告主管作廢的權威狀態含 `cancel_reason=manager_void`，目前靠 harness 的 `supplementCancelReason` 補欄撐住；後端補上後應刪掉該 helper。

## Addendum 2 Phase 3: Test Alignment & Implementation

**Goal**: 把第四趟三句 ADD 的 DSL 寫成 stepdef。不寫產品行為。

**DSL 參照**:
- 三句都在 `specs/truth/features/ios-app/任務執行/dsl.md`（兩個 Given、一個 Then）；讀法同本檔 Phase 3。
- `"{員工}" 在 App 完成 "{任務}"`（介面根）與 `"{員工}" 名下有以下任務：` 語意沒變且 stepdef 已在，不列任務。

**Markers**: 三句皆 `[BDD-RED]`（ADD）。完成時失敗只能是 assertion 或產品行為，不能是 undefined step。本輪沒有 MODIFY／DELETE。

**Shared Must Read**:
- `specs/truth/features/ios-app/任務執行/dsl.md` -> 三句新列
- `truth-delta.md` -> 第四趟 `/dsl-refine` 表
- `app/bdd/src/test/java/com/athena/app/bdd/steps/taskexecution/`、`.../steps/common/`

**Boundary**: 一條 DSL 一個 task；只動測試層。review 通過前不解鎖 Feature phase。

**Parallel Hint**: T500–T502 各派一個獨立 subagent（T501 的作廢落地可參考 tms 後端測試資料或既有主管操作後門；T502 與 T500 可能同檔，後寫入者自己 merge）；T503 等全部回來再 review。

- [X] T500 [P] [BDD-RED] `"{員工}" 已在 App 打開 "{任務}" 的任務頁`（Arrange 版：停在任務頁；開頁是受測動作時用同名 When，不共用 annotation）
- [X] T501 [P] [BDD-RED] `"{任務}" 剛被主管作廢，"{員工}" 的畫面還沒更新`（後端轉 cancelled、`cancel_reason`=manager_void；不點推播、不觸發刷新）
- [X] T502 [P] [BDD-RED] `完成被擋下，畫面顯示後端拒絕的原因`（Alert 文字＝後端 message；不進完成畫面；後端無 completed 事件；提示沒出現就算失敗）

- [X] T503 subagent review (addendum 2 phase quality gate)

## Addendum 2 Phase 4A: MODIFY Feature File - ios-app/任務執行/任務頁核心互動.feature

**Goal**: 完成被後端拒收時顯示後端的原因，不會無聲。

**Shared Must Read**:
- `specs/truth/features/ios-app/任務執行/任務頁核心互動.feature` -> Rule「完成被後端拒收時顯示後端的原因，不會無聲」
- `specs/truth/features/ios-app/任務執行/dsl.md` -> 三句新列
- `specs/truth/features/ios-app/dsl.md` -> `"{員工}" 在 App 完成 "{任務}"`（失敗時後端不變、回寫畫面提示）
- `truth-delta.md` -> 第四趟 `/dsl-refine` 表
- `specs/plans/001-staff-app-increment/ui/ui-plan.md` -> F2-R1 `task.busy-alert`（提示訊息直接用後端 message，App 不自己湊話）
- `app/mobile/lib/features/task/task_screen.dart` -> `_complete`（現況吞例外）、`_start`／`_resume` 的 `_showBusyAlert` 模式

**Boundary**:
- 只動 `_complete` 的錯誤處理：補 `on ApiException` 呈現後端 message（沿用 `task.busy-alert` 語意）；不改完成成功路徑、不改離線佇列行為（`sent=false` 的待同步呈現照舊）。
- 不改後端。

**Test Scope**:
- `specs/truth/features/ios-app/任務執行/任務頁核心互動.feature`（既有場景不得變紅）

- [X] T504 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T505 [BDD-REFACTOR] 在綠燈下確認開工／恢復／完成三處的被拒提示走同一段處理，不是三份複製

## Addendum 2 Phase 4B: ui-plan quality gate

**Goal**: 產品對齊回追到的 plan-side 產物。`Test Scope` 全綠不能代替本題。

**Read**:
- `specs/plans/001-staff-app-increment/ui/ui-plan.md` -> F2-R1 busy-alert 語意、§3 任務頁失敗回饋
- `app/mobile/lib/features/task/task_screen.dart`

**Boundary**: 不改 truth、不加 feature。重點比對：被拒提示的文案來源（後端 message）、Alert 樣式與既有 busy-alert 一致、完成被拒後畫面不假裝成功。

- [X] T506 subagent review (ui-plan busy-alert quality gate)

---

# Addendum 3: 任務頁計時重複累計＋超時提示凍結（現場 bug，非 002 FR）

**對應 truth**：`truth-delta.md` 的「第五趟 `/dsl-refine`」（2026-11-09）。與前兩個 Addendum 互不依賴；編號自 T510 起。

**背景**：計時字串拿 `elapsedMinutes` 當基準再加（現在 − `segmentStartedAt`），把正在跑的這一段重複累計（契約 `accumulatedSeconds` 描述明文禁止；App 未解析 `accumulatedSeconds`）；超時徽章只用上次取資料時的 `elapsedMinutes`，頁面開著永遠不動。

本 Addendum 無新增技術（省略 Setup）、無 Foundational——時間推撥 helper（`已中斷 {分鐘} 分鐘` 的裝置＋Clock 撥動）與計時 hero 元件都已存在。

**交付後遺留（T515–T517 發現，均低度、不擋）**：①停住 hero 的超時死分支（`task_screen.dart:778,805-815`——`_overtimeMinutes` 對非 doing 一律 null，truth 的超時 Rule 只定義進行中）建議開 cleanup 刪除，連同 `:720-724` 「停住時已寫預估」的失真註解一併校正，停住單 meta 要不要補「預估 N 分」待產品確認；②`過了 {分鐘} 分鐘` 只撥裝置側——後端無測試用 Clock 端點（tms BDD 是同 JVM 撥 MutableClock，harness 構不到），等後端開端點時 stepdef 要改成兩邊一起撥；③整分精度的期望值對 fixture→斷言之間的真實耗時敏感（與既有「超時 5 分」場景同模式），偶發假紅時先懷疑這裡。

## Addendum 3 Phase 3: Test Alignment & Implementation

**Goal**: 把第五趟的 1 ADD＋3 MODIFY 對齊到測試層。不寫產品行為。

**DSL 參照**:
- `過了 {分鐘} 分鐘`、兩句 MODIFY 的 Then 都在 `specs/truth/features/ios-app/任務執行/dsl.md`；`"{員工}" 名下有以下任務：` 在 `specs/truth/features/backend/dsl.md`（ios-app 介面根引用同名句型）。讀法同本檔 Phase 3。

**Markers**:
- `[BDD-RED]`：`過了 {分鐘} 分鐘`（ADD）。
- `[BDD-ALIGN]`：兩句 Then 的必查收緊（MODIFY）；`名下有以下任務：` fixture 落地方式固定（MODIFY，app/bdd 與 tms 兩邊的 fixture 實作各檢查一次，合併為一題——同一句型的同一次語意變更）。
- 本輪沒有 DELETE。

**Shared Must Read**:
- `specs/truth/features/ios-app/任務執行/dsl.md` -> 新 When 與兩句 MODIFY Then
- `specs/truth/features/backend/dsl.md` -> `名下有以下任務：` 的怎麼做
- `truth-delta.md` -> 第五趟 `/dsl-refine` 表
- `specs/truth/contracts/components/schemas/任務.yaml` -> `accumulatedSeconds`、`elapsedMinutes`
- `app/bdd/src/test/java/com/athena/app/bdd/steps/taskexecution/`、`.../steps/common/`
- `tms/src/test/java/com/athena/tms/bdd/steps/`（`名下有以下任務` fixture）

**Boundary**: 一條 DSL 一個 task；只動測試層；不寫產品碼。review 通過前不解鎖 Feature phase。

**Parallel Hint**: T510–T513 各派一個獨立 subagent（T511 與 T512 可能同檔，後寫入者自己 merge）；T514 等全部回來再 review。

- [X] T510 [P] [BDD-RED] `過了 {分鐘} 分鐘`（裝置時間與後端 Clock 一起往前撥、畫面保持開著；推撥手法參考 `"{任務}" 已中斷 {分鐘} 分鐘` 的既有 stepdef）
- [X] T511 [P] [BDD-ALIGN] `任務頁顯示已做 {分鐘} 分鐘，且計時正在走`——必查收緊：顯示值＝後端已做分鐘、本段只算一次（重複累計顯示約兩倍時必須紅）
- [X] T512 [P] [BDD-ALIGN] `任務頁顯示 "{超時文字}"`——必查收緊：斷言當下的超時分鐘數要等於「目前已做分鐘−預估」，不接受停在舊值
- [X] T513 [P] [BDD-ALIGN] `"{員工}" 名下有以下任務：` fixture（app/bdd 與 tms 兩邊）——進行中＝worked_sec 0＋segment_started_at 往前推整段；已中斷＝worked_sec 換算秒；不符就修 fixture

- [X] T514 subagent review (addendum 3 phase quality gate)

## Addendum 3 Phase 4A: MODIFY Feature File - ios-app/任務執行/任務頁核心互動.feature

**Goal**: 計時把正在跑的這一段只算一次；超時提示跟著目前已做時間走。

**Shared Must Read**:
- `specs/truth/features/ios-app/任務執行/任務頁核心互動.feature` -> 兩個新 Rule
- `specs/truth/features/ios-app/任務執行/dsl.md` -> 新 When 與兩句 MODIFY Then
- `truth-delta.md` -> 第五趟 `/dsl-refine` 表
- `specs/truth/contracts/components/schemas/任務.yaml` -> `accumulatedSeconds` 的公式與禁令
- `specs/plans/001-staff-app-increment/ui/ui-plan.md` -> §3 任務頁計時 hero
- `app/mobile/lib/features/task/task_screen.dart` -> `taskTimerText`、`_overtimeMinutes`、`TaskTimerHero`
- `app/mobile/lib/core/api_client.dart` -> `TaskDetail`（未解析 `accumulatedSeconds`）

**Boundary**:
- 產品碼落點：`TaskDetail` 補解析 `accumulatedSeconds`；`taskTimerText` 改用 `accumulatedSeconds + (現在 − segmentStartedAt)`；超時分鐘數改吃逐秒重算的已做分鐘（整分才變，避免既有註解說的忽隱忽現）。
- 後端：T515 實作中發現 `toDetail`（`TaskExecutionService.java`）從未回契約既有的 `accumulatedSeconds` 欄——truth-delta 第五趟的契約 NOOP 是基於「後端已回這欄」的誤判。**允許**在本題補上回應組裝這一行（`worked_sec` 即「進入本段之前的累計秒數」，語意同契約），不碰狀態轉移與其他欄位；除此之外不改後端。
- 不改 `elapsedMinutes` 的其他用途（停住單的「已做 N 分」照舊）。
- 注意並行 002 的 task_screen.dart 區塊不要動。

**Test Scope**:
- `specs/truth/features/ios-app/任務執行/任務頁核心互動.feature`（既有場景不得變紅）

- [X] T515 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T516 [BDD-REFACTOR] 在綠燈下確認計時與超時共用同一個「目前已做秒數」來源，不是各算各的

## Addendum 3 Phase 4B: ui-plan quality gate

**Goal**: 產品對齊回追到的 plan-side 產物。

**Read**:
- `specs/plans/001-staff-app-increment/ui/ui-plan.md` -> §3 任務頁（計時 hero 與超時提示）
- `app/mobile/lib/features/task/task_screen.dart`

**Boundary**: 不改 truth、不加 feature。重點比對：計時與超時兩個數字永遠彼此一致（同一口徑）；停住狀態的「已做 N 分」不受影響。

- [X] T517 subagent review (ui-plan 計時 quality gate)
