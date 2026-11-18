# Truth Delta: 002-celebrate-and-resume

**Plan Package**: `specs/plans/002-celebrate-and-resume`
**Truth Root**: `specs/truth`

> 本檔由 truth owner skill（`/api-plan`、`/data-plan`、`/dsl-refine`）透過 `/truth-delta` 追加。
> `/specify` 只建立骨架，不得寫入任何 `specs/truth/**` 的實際改動。

## /technical-research

_尚未執行。_

## /api-plan

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `components/schemas/任務.yaml` -> `NextStep` | 完成之後的接續去向。`kind` 為封閉三值 `next_task`／`unfinished_task`／`all_clear`，前兩者必帶 `taskId` 與 `roomNo`，`all_clear` 兩者都不出現。schema 描述寫明挑選口徑（先 accepted 沿用 `MyToday.nextTasks` 排序，再 doing→interrupted→deferred、同組房號遞增）與「今日」的判定沿用 `MyToday` 那一套。 | FR-001～FR-005。`kind` 是刻意加的：三種去向若靠 `taskId` 是不是 null 推斷，「查不到下一間」與「今天都清完了」在畫面上長得一樣，員工看到的結論卻相反。 |
| ADD | `components/schemas/任務.yaml` -> `TaskCompletion` | `allOf: [TaskDetail, {nextStep, cleaningDoneToday, celebrationEnabled}]`。`nextStep` 必填；`cleaningDoneToday` 只有打掃類帶；`celebrationEnabled` 省略視為 true。 | FR-016、FR-017、FR-024、NFR-001。獨立成新 schema 而不是把欄位塞進 `TaskDetail`，是因為這三個值只在「剛完成」這一刻成立，`readTasks` 回的 `TaskDetail` 不該長出三個永遠為空的欄位。 |
| MODIFY | `groups/現場作業/任務.yaml` -> `tasksComplete` 的 200 回應 | 由 `TaskDetail` 改為 `TaskCompletion`；description 補上兩個新值與「同一次往返內給完」。 | NFR-001 要求完成畫面不為了取得下一間再打一次端點——多一次往返會讓完成畫面在訊號差的樓層卡住，而那正是員工最需要立刻看到回饋的時候。 |
| MODIFY | `groups/現場作業/任務.yaml` -> `tasksResume` 的 description | 補「停住原因在這一步清空（回應 `holdReason` 為 null）」，並把 409 `doing_task_exists` 的 context 與 message 範例寫明（原本只寫「規則同 tasksStart」）。 | FR-010、FR-012。清空 `hold_reason` 原本只寫在 data truth 的欄位註記，API truth 看不到，App 無從得知回應裡這個欄位會變 null。 |
| ADD | `components/schemas/租戶設定.yaml` -> `tenant_config.key`／`tenant_configRequest.key` 封閉鍵集 | 新增 `completion_celebration_enabled`，值為 boolean、預設 true；註記寫明關掉的是氣氛（飾帶、彩屑、入場動態、今日進度）不是接力，且不影響 `nextStep` 與 `cleaningDoneToday` 的計算。 | FR-024。鍵名沿用既有 snake_case 慣例，可修改角色沿用既有 `updateTenantConfig` 的權限，本輪不另立角色規則。 |
| NOOP | `groups/現場作業/任務.yaml` -> `tasksResume` 的 operation 本體 | 路徑、參數、200／403／404／409 回應在 001 增量已完整定義，本輪只動 description。 | FR-009～FR-013 的缺口不在 API truth，而在 feature 與 dsl 完全沒有涵蓋它——那正是 App 那顆按鈕能當空實作躺著沒被任何測試發現的原因，由 `/dsl-refine` 補。 |
| NOOP | `components/schemas/shared.yaml` -> `Error` 錯誤碼清單 | 本輪不新增錯誤碼。恢復處理撞「一次一間」沿用既有 `doing_task_exists`。 | FR-012 明寫「以與開工相同的口徑套用」，另立錯誤碼會讓 App 要為同一件事寫兩套處理。 |

### 第二趟 `/api-plan`（2026-11-09，承接 `/dsl-refine` 第二趟的遺留，非 002 需求範圍）

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| MODIFY | `components/schemas/shared.yaml` -> `PushTarget.kind` 枚舉與 `taskId`、description | 枚舉加第四種值 `ticket`：本人的修繕任務（工務單）推播一律用 ticket、App 開工務單詳情，`task` 從此只用於打掃／備品任務（App 開任務頁）；`taskId` 在 kind=ticket 時必填，帶該修繕任務的 task id。description 明寫「去處種類由後端發推播時依任務類別決定，App 照 kind 機械導頁」，並註明 `NotificationItem.target` 共用同一 schema、點通知項規則相同。 | 使用者 2026-11-09 拍板（clarify gate）。手動試玩發現修繕任務推播被 App 導進任務頁，員工按「完成」被 422 拒收且無回饋；修繕完修入口在工務單詳情（ui-plan §13）。把判斷放後端符合「去處是後端結論、App 不推演」的既有慣例，也省掉 App 導頁前多打一次查任務。 |

## /data-plan

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| NOOP | `specs/truth/data/data-model.dbml` -> `tasks` 欄位與索引 | 不新增欄位、不新增索引。「今天第幾單」與「完成後的接續去向」兩個新值都能由既有欄位（`type`、`state`、`assignee_id`、`work_date`、`done_at`、`inspection_waived`）與既有索引 `(tenant_id, assignee_id, state)` 推導。 | FR-016、FR-017、FR-004；兩者都是查詢期計算，存成欄位反而會和首頁已清完間數各算各的（SC-005 要求兩個畫面的數字一致）。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `tasks` 的 `Note` | 增補「完成當下的兩個衍生值」一段：寫明兩個值的計數與挑選口徑、走哪一個既有索引、以及「今天」的判定沿用既有 `belongsToToday` 口徑（work_date 有值比 work_date，沒值時未結束算今天、已結束看 done_at）。 | 防止後續實作為了這兩個值加計數欄位或另立一套日期判定；FR-004 的挑選順序與 FR-017 的同口徑要求必須在 data truth 留下唯一出處。 |
| NOOP | `specs/truth/data/data-model.dbml` -> `task_events`、`stats_daily` 等其他表 | 恢復處理（`interrupted→doing`）所需的狀態轉移、`hold_reason` 清空、`worked_sec`／`segment_started_at` 的計時接續，在 001 增量已全部定義，本輪無新增。 | FR-009～FR-012 只是補上從未被 feature／dsl 涵蓋的規格與測試，不改資料模型。 |

### 第二趟 `/data-plan`（Wave 2 之後，已完成）

第一趟把 FR-024 的租戶設定鍵留白，原因是鍵名未定（FR-024 的 `NEEDS CLARIFICATION`）、`租戶設定介面` 排在 Wave 2，而 `/api-plan` 不是 data truth owner、不能代寫 `data-model.dbml`。Wave 2 的 `/api-plan` 定出鍵名與預設值之後，本趟補上：

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `specs/truth/data/data-model.dbml` -> `tenant_config.key` 封閉鍵集註記 | 新增 `completion_celebration_enabled`，值為 boolean、預設 true。註記寫明它只影響呈現，不影響 `tasks` 的任何欄位，也不影響「今天第幾單」與「完成後的接續去向」兩個衍生值的計算。 | FR-024；鍵名與預設值由 `/api-plan` 在 `components/schemas/租戶設定.yaml` 定出，兩邊封閉鍵集必須同字串。 |

至此 FR-024 在 data truth 與 API truth 兩邊都已落地，002 對 data truth 再無待辦。

### 本輪發現、不屬於 002 的既有漂移

`tenant_config.key` 的封閉鍵集在 data truth 有 `unclaimed_reminder_minutes` 與 `notification_retention_days` 兩鍵，**契約的 `tenant_config.key`／`tenant_configRequest.key` 描述裡都沒有**。這是 002 之前就存在的落差，方向是「契約落後於 data truth」，修它要由 `/api-plan` 另跑一輪，不在 002 範圍內。記在這裡避免下一個讀 truth 的人以為是本輪造成的。

## /dsl-refine

本輪涉及兩個介面：`backend` 與 `ios-app`。兩邊的拓樸稽核都 PASSED
（`uv run .claude/skills/gherkin-and-dsl/scripts/audit_feature_dsl_topology.py --root specs/truth/features/<介面>`，每個介面各跑一次）。

### backend

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `backend/任務執行/完成之後的接續去向.feature` | 新檔，6 個 Rule 各 1 個場景：還有待開始的單、只剩沒做完的單、沒做完的有多張時的挑選順序、都清完了、打掃單回報第 N 單、非打掃單不回報。 | FR-001～FR-005、FR-016～FR-018。三種去向各一條、且在測試上可分辨，是 FR-001 的核心要求。 |
| ADD | `backend/任務執行/dsl.md` -> 6 個 Then 句型 | `"{任務}" 沒有停住原因`、`完成回報的下一步是待開始的 "{房號}"`、`完成回報的下一步是還沒做完的 "{房號}"`、`完成回報說今天排定的房都清完了`、`完成回報的今日進度為第 {單數} 單`、`完成回報不含今日進度`。 | 承接上列 feature；三種去向寫成三個句型而不是一個帶參數的句型，是為了讓「靠 kind 判定、不可靠 taskId 是不是 null 推斷」這條在測試層就成立。 |
| MODIFY | `backend/任務執行/中斷恢復與實際工時.feature` -> Rule「恢復後從中斷前的已做時間接續」 | 標題補「停住原因跟著清掉」，場景加一條 `並且 "305" 沒有停住原因`。 | FR-010。清空 `hold_reason` 原本只寫在 data truth 的欄位註記，沒有任何測項驗它。 |
| ADD | `backend/任務執行/一次只做一間.feature` -> Rule「已有進行中的單時，也不能把中斷的單恢復回來」 | 新 Rule 與場景：阿美的 305 進行中時恢復 308 被拒，308 維持已中斷且停住原因不變。 | FR-012。既有檔案只驗了「開始」撞一次一間，沒驗「恢復」。 |
| MODIFY | `backend/今日進度/今日進度與下一間.feature` -> Rule 標題、`backend/今日進度/dsl.md` -> `今日進度的 "{項目}" 為 "{值}"` 的實作語意 | 刪掉「供完成慶祝頁的『第 N 單』使用」的說法，改寫為「**不是**完成畫面『第 N 單』的來源——那一個數的是打掃單，由 `tasksComplete` 回應的 `cleaningDoneToday` 提供」。 | **既有 truth 與本輪決議直接衝突**：001 把「第 N 單」綁在 `completedCount`（今天完成的全部單數，含臨時需求），但 `/clarify-over-specs` 第一輪使用者拍板第 N 單是打掃單數、與首頁已清完間數同口徑（FR-017、SC-005）。`completedCount` 這個統計本身保留不動，改掉的只是它被宣告的用途。 |

### ios-app

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `ios-app/任務執行/從任務頁接回中斷的單.feature` | 新檔，6 個 Rule 各 1 個場景：恢復成功、停住原因消失、計時接續、撞一次一間被擋、被擋後狀態不變、離線被擋在送出之前。 | FR-009～FR-013。**這是 002 的核心缺口**——既有 truth 只有一列 `| 310 | 已中斷 | 恢復處理 |` 在驗按鈕文字，從來沒有任何場景真的按下去。斷言 label 不斷言 action，就是那顆按鈕能當空實作躺著沒被發現的原因。 |
| ADD | `ios-app/任務執行/dsl.md` -> When `"{員工}" 在 App 恢復 "{任務}"` | 實作語意明寫「要真的按下那顆按鈕，不是只檢查按鈕文字」，並列出成功、409、離線三條落地語意。 | 同上。句型本身就要把這一輪學到的教訓寫進去。 |
| MODIFY | `ios-app/任務執行/任務頁核心互動.feature` -> Rule「完成後進入完成慶祝頁，顯示本次實際與預估時間」 | 原 Rule（1 個場景、只驗兩個數字）拆成 14 個原子 Rule，並整批移到新檔 `完成畫面.feature`；原檔留下步驟條、主按鈕、SOP、超時共 6 個 Rule。 | FR-014～FR-024。原 Rule 標題同時含「進入頁面」與「顯示數字」兩個結果，依原子化判準必須拆；拆完 20 個 Rule 擠在一個檔案，依功能面向切出 `完成畫面.feature`。 |
| ADD | `ios-app/任務執行/完成畫面.feature` | 14 個 Rule：覆蓋層疊在任務頁上、用時與預估、無預估、標題帶房號、三種說明（場景大綱）、第 N 單、非打掃不顯示、下一步房號、都清完、回首頁出口、按下一步開任務頁、按下一步進待認領、回首頁、關閉氣氛仍保留接力。 | 同上。其中「完成畫面蓋在 "{任務}" 的任務頁上」直接對應 NFR-003。 |
| ADD | `ios-app/任務執行/dsl.md` -> 2 個 Given、3 個 When、15 個 Then 句型 | Given：`飯店關閉了完成慶祝的氣氛呈現`、`"{員工}" 剛在 App 完成 "{任務}"，完成畫面停在畫面上`、`"{任務}" 已中斷 {分鐘} 分鐘`；When：恢復、從完成畫面前往下一步、從完成畫面回到首頁；Then：完成畫面各元素、三種去向、疊加、氣氛關閉、畫面停在哪裡、計時正在走、不再顯示停住原因。 | 承接上列兩個 feature。`"{員工}" 剛在 App 完成 "{任務}"，完成畫面停在畫面上` 是把 Journey 前序操作降為 Given 的那一步——「從完成畫面再往下走」才是那幾條 Rule 的受測動作。 |
| MODIFY | `ios-app/任務執行/dsl.md` -> `完成慶祝頁顯示實際 {分鐘} 分、預估 {分鐘2} 分` | 改名為 `完成畫面顯示這次用時 {分鐘} 分、當初預估 {分鐘2} 分`，實作語意補「實際是主要數字、預估是附註，兩者不是同一級」。 | FR-019，以及術語統一——FR-024 讓這個畫面可以沒有慶祝，叫「完成慶祝頁」就不再成立。 |
| MODIFY | `ios-app/dsl.md` -> `"{員工}" 在 App 完成 "{任務}"` 的回寫欄 | 「完成慶祝頁內容」改為「完成畫面內容」。 | 同上，術語統一。語意不變。 |

### 第二趟 `/dsl-refine`（補離線完成畫面，已完成）

第一趟漏掉 US4／FR-021～FR-023：acceptance 的 `離線做完一間也看得到回饋.feature` 沒有任何 interface feature 承接它的完成畫面那一面，而且第一趟寫下的 Then 句型 `完成畫面說明這一筆還沒送出` 成了沒有任何 feature 使用的孤兒 row。拓樸稽核不抓孤兒，所以第一趟 PASSED 沒有攔住；缺口是 `/tasks` 做 acceptance 覆蓋回推時才發現的。

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `ios-app/任務執行/完成畫面.feature` -> 5 個 Rule | 離線完成時完成畫面照常出現、說明這一筆還沒送出、下一步以手機上的今日進度推算、今日進度以手機上已知的完成數推算、恢復連線後那一筆真的送到後端。 | FR-021、FR-022、FR-023。前四條把第一趟的孤兒句型接起來，第五條補上 FR-023 的「連線恢復後更新」。 |
| ADD | `ios-app/任務執行/dsl.md` -> Given `"{員工}" 已在離線時完成 "{任務}"，這一筆還押在手機上` | 實作語意明寫「**後端該任務狀態還沒有改變**」，並點名它與 `"{員工}" 剛在 App 完成 "{任務}"，完成畫面停在畫面上` 的差別就在這裡、不可互換。 | 「恢復連線後送到後端」那條 Rule 的受測動作是恢復連線，離線完成必須降為 Given；但既有的那個 Given 的權威狀態落地是「後端已轉 done」，拿來用會讓這條 Rule 驗不到東西。 |
| MODIFY | `"{員工}" 的手機恢復連線` 的權威位置 | **舊位置** `ios-app/工務單/dsl.md`（模組專屬）→ **新位置** `ios-app/dsl.md`（介面根共用）。**語意不變**，只在實作語意欄補上 `權威狀態落地` 與 `回寫` 兩段，使它與介面根其他 row 的欄位密度一致。舊位置那一列已刪除，不留 duplicate。 | 這一句本輪起同時被 `工務單` 與 `任務執行` 兩個模組使用，句型、參數、DataTable 支援與實作契約完全一致，符合上提到介面根的條件；留在 `工務單` 會讓 `任務執行` 的 step 查不到唯一 DSL row。 |

兩個介面的拓樸稽核在本趟之後重跑，仍為 PASSED（ios-app：18 feature、12 個根 DSL rows、404 steps、0 warning）。

### 本輪發現、不屬於 `/dsl-refine` 權責的殘留

`contracts` 還有兩處沿用舊說法，宣稱 `completedCount`「供完成慶祝頁『第 N 單』使用」：

- `specs/truth/contracts/groups/現場作業/員工工作台.yaml:10`
- `specs/truth/contracts/components/schemas/員工工作台.yaml:30`

feature truth 這一側已經改正，但 contracts 是 `/api-plan` 的 truth，`/dsl-refine` 不代寫。需要 `/api-plan` 另跑一輪把這兩句對齊（第 N 單的來源是 `tasksComplete` 回應的 `cleaningDoneToday`，不是 `completedCount`）。

### 第二趟 `/dsl-refine`（2026-11-09，現場 bug 驅動，非 002 需求範圍）

手動試玩發現：修繕任務（工務單）的推播 `dest=task` 被 App 一律導到任務頁，員工看到一顆不該存在的裸「完成」主按鈕；按下去送出的完成請求因沒附完修照片被後端 422 `completion_photo_required` 拒收，且畫面無任何回饋。ui-plan §13 規定修繕完修入口是工務單詳情的完修回報面板（缺照片時送出鈕禁用），這兩條行為從未有 feature 承接。本趟補上缺口；記在 002 是因為它是當前 plan package，與 002 的 FR 無涉。

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `ios-app/推播/點開推播的去處.feature` -> Rule「修繕任務（工務單）的推播點開後停在工務單詳情，不是任務頁」 | 新場景：維修中工務單的推播點開後停在該單的工務單詳情；背景補一列工務員工（阿德）。 | 修繕任務的完修入口在工務單詳情（ui-plan §13），導進任務頁會讓員工面對一顆按了沒反應的「完成」；此導頁行為從未有測項。 |
| ADD | `ios-app/工務單/工務視角.feature` -> Rule「完修回報沒附照片時送出鈕禁用」 | 新場景：打開完修回報、選結果、0 張照片 → 送出鈕禁用，且後端沒有收到這次完修。 | ui-plan §13「完修缺照片 → 送出鈕禁用」沒有任何測項；契約規定修繕完修 `photoIds` 至少 1 張（422 `completion_photo_required`）。 |
| MODIFY | `ios-app/推播/dsl.md` -> Given `"{員工}" 收到一則推播 "{標題}"，去處為 "{去處}"` 的去處枚舉 | 枚舉加「X 的工務單詳情（X＝單號）」；實作語意註記契約 `PushTarget.kind` 尚無工務單詳情一種、待 `/api-plan`，在那之前 payload 仍帶修繕任務 id、由 App 依任務類別導向。 | 承接上列 feature；枚舉原本只有任務頁、待認領、首頁。 |
| MODIFY | `ios-app/推播/dsl.md` -> Then `App 停在 "{畫面}"` | 畫面範例補工務單詳情；必查補「畫面是工務單詳情時，顯示該單號的詳情頁、不是任務頁」。 | 同上。 |
| MODIFY | `ios-app/工務單/dsl.md` → `ios-app/dsl.md` -> Given `已有以下工務單：` | 權威位置搬移，語意不變。 | 推播模組開始使用完全相同契約；step 查找只合併介面根與同模組 DSL，依唯一歸屬判準升格到介面根。 |
| ADD | `ios-app/工務單/dsl.md` -> When `"{工務}" 在工務單詳情打開完修回報，選結果 "{結果}"，附 {張數} 張照片` | 打開面板、拍照（0 張代表不拍）、選結果、停在面板上不送出；權威狀態不變。 | 禁用斷言需要「停在面板上」的狀態；既有完修句型含送出，無法承接。 |
| ADD | `ios-app/工務單/dsl.md` -> Then `完修回報的送出鈕為 "{按鈕狀態}"` | 必查送出鈕 enabled/disabled，禁用時無法真的觸發送出。 | 與報修面板的 `送出鈕為` 是同文異義（面板不同），依唯一歸屬判準拆成不同句型。 |
| ADD | `ios-app/工務單/dsl.md` -> Then `後端沒有收到這次完修` | 後端該工務單仍是維修中，沒有新的完修事件、完修照片與完修結果。 | 拒絕類 Then 必須驗權威狀態沒被失敗操作改壞。 |

本趟遺留、不屬於 `/dsl-refine` 權責：①`PushTarget.kind` 沒有「工務單詳情」這種去處——已由第二趟 `/api-plan` 補上 `kind=ticket`（見上方 `/api-plan` 第二趟），ios-app `推播/dsl.md` 的過渡註記已同步改為定案語意；後端 `backend/推播/員工推播事件.feature` 的「主管改派一單給您」當改派的是修繕任務時去處應為工務單詳情，這個場景待下一趟 `/dsl-refine` 補。②App 端兩個實作 bug——`push_gateway.dart` 的導頁沒有 ticket 一種（現況 `dest=task` 不分任務類型一律進任務頁）、`task_screen.dart` 的 `_complete` 對後端拒收無任何錯誤呈現——屬實作而非 truth，交 `/tasks`／`/bdd` 落地。

### 第三趟 `/dsl-refine`（2026-11-09，承接第二趟遺留 ①，非 002 需求範圍）

第二趟 `/api-plan` 定案 `PushTarget.kind` 加 `ticket` 之後，後端 feature truth 跟著補齊「改派修繕任務時去處為工務單詳情」：

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `backend/推播/員工推播事件.feature` -> Rule「別人的單改派給本人時推播新負責人，點開到該單」的第二個場景 | 新場景：王主管把小李的工務單改派給阿德，阿德收到的推播去處為該單的工務單詳情；背景補一列工務員工（小李）。 | 既有場景只驗了打掃任務（去處＝任務頁）；修繕任務改派後去處依 `kind`=ticket 應為工務單詳情，從未有測項。 |
| MODIFY | `backend/dsl.md` -> Then `"{員工}" 收到以下推播：` 的去處枚舉與必查 | 去處加「X 的工務單詳情（X＝單號）」；必查補 ticket 的對應（`kind`=ticket＋修繕任務的 `taskId`），並明寫 task 只用於打掃／備品。 | 對齊第二趟 `/api-plan` 的 `PushTarget` 定案。 |
| MODIFY | `backend/推播/dsl.md` -> When `"{主管}" 把 "{任務}" 改派給 "{員工}"` | `任務` 參數放寬為「房號，修繕任務（工務單）用單號」；權威狀態落地補「新負責人那封的去處依任務類別決定——打掃／備品為 task、修繕為 ticket」。 | 承接上列場景；原句型只收房號。 |
| NOOP | `ios-app/推播/點開推播的去處.feature`、`ios-app/推播/dsl.md` | App 側的工務單詳情去處已在第二趟補齊（場景與 DSL 皆有），本趟只確認與 ticket 定案一致，無需再改。 | 第二趟的過渡註記已在 `/api-plan` 第二趟時改為定案語意。 |

後端拓樸稽核 PASSED（601 steps 全部唯一命中；唯一 warning 是先前就存在的 `"{員工}" 已停用` 歸屬提醒，與本趟無關）。至此這條現場 bug 的 truth 側（契約＋前後端 feature/dsl）全部補齊，剩餘待辦只剩上列遺留 ② 的 App 實作修復。

### 第四趟 `/dsl-refine`（2026-11-09，承接 Addendum Scope Note ①「完成被拒無聲」，非 002 需求範圍）

現場 bug 的第二半：任務頁 `_complete` 吞掉 `ApiException`，完成被後端拒收（打掃類的真實情境是任務在背後被主管作廢／改派，`tasksComplete` 回 409 狀態機衝突）時畫面毫無反應。truth 原本沒有任何場景承接「完成被拒時的畫面提示」，Addendum 的 `/tasks` 依規停止了這個範圍；本趟補上。

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `ios-app/任務執行/任務頁核心互動.feature` -> Rule「完成被後端拒收時顯示後端的原因，不會無聲」 | 新場景：阿美的任務頁開著、310 在背後被主管作廢，按完成 → 完成被擋下、畫面顯示後端拒絕的原因。 | `_complete` 無聲吞錯是原 bug 能存在的直接原因之一；`"{員工}" 在 App 完成 "{任務}"` 的介面根 DSL 早已寫明「失敗時後端不變、回寫畫面提示」，卻沒有任何場景真的走失敗路徑。 |
| ADD | `ios-app/任務執行/dsl.md` -> Given `"{員工}" 已在 App 打開 "{任務}" 的任務頁` | Arrange 版開頁句型：停在任務頁，供「畫面已開著、世界在背後改變」的情境鋪底；與同名 When 區分（開頁是受測動作時用 When）。 | 承接上列場景——完成被拒的前提是畫面停在舊狀態，開頁必須降為 Given，依原子化判準不得在同一場景先開頁再完成兩個 Act。 |
| ADD | `ios-app/任務執行/dsl.md` -> Given `"{任務}" 剛被主管作廢，"{員工}" 的畫面還沒更新` | 後端轉 cancelled（`cancel_reason`=manager_void）、不觸發畫面刷新；畫面仍停在作廢前的任務頁。 | 製造「App 畫面與後端權威狀態不一致」的真實競態，是打掃類完成被拒唯一能穩定重現的入口。 |
| ADD | `ios-app/任務執行/dsl.md` -> Then `完成被擋下，畫面顯示後端拒絕的原因` | 必查：Alert 文字就是後端 4xx 回應的 message（App 不自己湊文案，比照 `task.busy-alert`）；不進完成畫面、任務沒被標成完成；後端維持被拒前狀態、無 completed 事件；**提示沒出現就算失敗，即使後端狀態正確**。 | 拒絕類 Then 的驗證契約；最後一句是這條 bug 的教訓——後端狀態一直是對的，壞的是 App 的沉默。 |
| NOOP | `backend/任務執行/**` | 不加後端場景。完成打在非 doing 任務上的 409 由契約 `tasksComplete` 的 409（狀態機衝突）承接，App 場景的 E2E 會實際打到這條路徑。 | 本缺口的主體是 App 的呈現責任，後端拒收行為契約已定義。 |

ios-app 拓樸稽核 PASSED（18 features、409 steps 全部唯一命中）。交接：這一節的實作可直接比照 Addendum 模式接 `/tasks`——測試層 1 個 ALIGN（`在 App 完成` 既有 stepdef 若未覆蓋失敗路徑則補）＋3 個 RED，Feature phase 的產品碼就是 `task_screen.dart` `_complete` 補 `ApiException` 呈現（比照 `_start`／`_resume` 的 `_showBusyAlert` 模式）。

### 第五趟 `/dsl-refine`（2026-11-09，現場 bug：計時重複累計＋超時提示凍結，非 002 需求範圍）

手動試玩發現任務頁計時顯示 1:20:19 但超時徽章只寫 5 分（預估 35、實際已做約 45）。兩個 App bug：①計時字串拿 `elapsedMinutes` 當基準再加（現在 − `segmentStartedAt`），把正在跑的這一段重複累計——契約 `accumulatedSeconds` 的描述（`任務.yaml`）明文禁止這個算法，且 App 根本沒解析 `accumulatedSeconds`；②超時徽章只用上次向後端取資料時的 `elapsedMinutes`，頁面開著永遠不動。truth 原本只驗「進頁那一刻顯示超時 N 分」與「分鐘接續且計時在走」，兩個缺口都沒有測項。

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `ios-app/任務執行/任務頁核心互動.feature` -> Rule「計時把正在跑的這一段只算一次」 | 新場景：進行中、整段已連續做 25 分（預估 35），打開任務頁 → 顯示已做 25 分鐘且計時正在走（重複累計會顯示 50，當場紅）。 | 缺口 ①。 |
| ADD | `ios-app/任務執行/任務頁核心互動.feature` -> Rule「超時提示跟著目前已做時間走，不停在進頁那一刻」 | 新場景：已做 40／預估 35、任務頁開著，過了 10 分鐘 → 顯示「超時 15 分」。 | 缺口 ②。 |
| ADD | `ios-app/任務執行/dsl.md` -> When `過了 {分鐘} 分鐘` | 裝置時間與後端 `Clock` 一起往前撥，畫面保持開著不操作；時間流逝不產生後端事件。 | 承接凍結場景的唯一 Act。 |
| MODIFY | `ios-app/任務執行/dsl.md` -> Then `任務頁顯示已做 {分鐘} 分鐘，且計時正在走` 的必查 | 補「顯示值要等於後端已做分鐘，正在跑的這一段只能算一次；秒級公式以契約 `accumulatedSeconds + (現在 − segmentStartedAt)` 為準，不得拿 `elapsedMinutes` 當基準再加本段」。 | 把契約的禁令落到測試層的驗證契約；原必查只驗「顯示該分鐘數＋會跳」，抓不到重複累計。 |
| MODIFY | `ios-app/任務執行/dsl.md` -> Then `任務頁顯示 "{超時文字}"` 的必查 | 補「畫面開著不操作時要隨時間跟著變大，不是停在進頁或上次取資料那一刻」。 | 原必查寫「目前已做分鐘」但沒有把「跟著走」寫成可驗證的條件。 |
| MODIFY | `backend/dsl.md` -> Given `"{員工}" 名下有以下任務：` 的怎麼做 | 已做分鐘的落地方式依狀態固定：進行中＝`worked_sec` 0＋`segment_started_at` 往前推（整段都在目前段）；已中斷＝`worked_sec` 換算秒。 | 原寫法只說「累計等於已做分鐘」，fixture 若把時間放進 `worked_sec`、本段從現在起算，重複累計 bug 會隱形；測項要能分辨就必須固定拆法。語意收斂、不改任何既有場景的可見結果。 |
| NOOP | `contracts/components/schemas/任務.yaml` -> `accumulatedSeconds`／`elapsedMinutes` | 契約已定義正確公式與禁令，本輪不動。 | 缺口在 App 實作、feature 覆蓋，**以及後端實作落後契約**——T515 發現 `toDetail` 從未回 `accumulatedSeconds`（實作缺口，不是 truth 缺口；由 Addendum 3 的 GREEN 一併補回應組裝，契約仍不動）。 |

兩個介面拓樸稽核 PASSED（ios-app 416 steps、backend 601 steps 全部唯一命中）。交接 `/tasks`：測試層 1 RED（`過了 {分鐘} 分鐘`）＋2 ALIGN（兩句 MODIFY 的 Then；`名下有以下任務` 的 fixture 落地在 app/bdd 與 tms 兩邊各一次 ALIGN 檢查）；Feature phase 產品碼＝`TaskDetail` 解析 `accumulatedSeconds`、`taskTimerText` 改用契約公式、超時徽章改吃逐秒重算的已做分鐘。
