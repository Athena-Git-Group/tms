# 後端欄位需求：把 App 補到與原型等齊

2026-11-08。來源是 `.athena/handoff/2026-11-06-design-system-from-prototype.md` 的「留白彙整」表——
七段視覺實作裡「原型有、App 手上沒有資料來源」的每一項。**版面、色彩、字級、元件規格都已經到位，
這份清單是把畫面填滿所需要的資料。**

每一項都標了：契約現況（`/api-plan` 要不要先補）、後端要做什麼、App 要改多少。
**大多數是既有端點多回一兩個欄位，不是新端點。**

## 第一優先：一個欄位換掉一整頁

### 1. 通知分類 `event` — 契約已補，等後端落地

- **契約**：✅ 已補（`components/schemas/通知中心.yaml` 的 `notifications.event`，必填，
  值域直接 `$ref` 到 `shared.yaml#/PushPayload/properties/event` 的 17 種事件值）
- **後端**：`notifications` 表加欄位＋migration；在把 outbox 的 `kind=push` 展開成一人一封時，
  把當時的 `PushPayload.event` 一併落地；清單回應帶出來
- **App**：`notification_screen.dart` 的 `_kindOf` 改成讀這個欄位，**版面零改動**
- **效果**：通知中心每一列現在都是同一個灰色鈴鐺；補完就變成七種分類七種顏色
  （急件紅閃電／超時橘碼錶／完成綠勾／交辦品牌色對話框／新單品牌色收件匣／保養紫月曆／一般灰鈴鐺），
  共用元件 `TmsNotificationKindIcon` 已經寫好在等

## 第二優先：動態感與提示感

### 2. 任務頁計時走秒

- **契約**：要補。`TaskDetail` 只有 `elapsedMinutes`（分鐘）
- **後端**：回這一段的起算時刻（`segmentStartedAt`）或累計秒數，App 才算得出逐秒跳動的 `10:23`
- **App**：標題區的 56pt 計時改成每秒重畫
- **效果**：原型裡最有存在感的動態元素。現在顯示「10 分」，看起來是靜止的

### 3. Tab bar 的紅色數量徽章

- **契約**：要補。外殼層現在沒有現成的池內單數與未讀數
- **後端**：最省的做法是在 `GET /me/today` 既有回應裡多兩個計數（待認領可搶單數、通知未讀數），
  不要讓 App 為了畫兩個徽章多打兩支讀取端點
- **App**：`main_shell.dart` 的四格掛上 `TmsCountBadge`（元件已存在）
- **效果**：少了「有事等你」的提示感；這是 Tab bar 唯一沒做的部分

### 4. 待認領單卡的等待時間

- **契約**：要補。`PoolItem` 只有 `createdAt`
- **後端**：回 `waitedMinutes`（或明確授權 App 自己依 `createdAt` 算）
- **App**：單卡加一個 `TmsTag`
- **備註**：當初沒做是因為自己算會隨時鐘跳動、且沒有權威口徑；若產品接受 App 端算，這項不必動後端

### 5. 工務待認領分流磚的數量徽章

- **契約**：要補。`GET /pool` 的 `counts` 只有房務的任務類別維度（`all`／`cleaning`／`amenity`／`repair`／`other`），
  沒有急件與來源這兩個維度
- **後端**：`counts` 增加工務視角的維度，或另開一組
- **App**：分流磚掛 `TmsCountBadge`

## 第三優先：把空著的區塊填滿

| # | 畫面 | 缺什麼 | 契約 | 後端要做 |
| --- | --- | --- | --- | --- |
| 6 | 房務首頁問候列 | 班別、負責樓層 | 要補 | `GET /me/today` 多兩個欄位 |
| 7 | 任務頁標題區 meta | 任務來源 | 要補 | `TaskDetail` 補 `source` |
| 8 | 工務首頁統計 | 今日保養 已完成／總數 | 要補 | `engineeringSummary` 補保養維度，目前整格不畫 |
| 9 | 工務首頁主卡 | 維修中那張的已做分鐘 | 要補 | `EngineeringTicket` 補已做分鐘 |
| 10 | 工務首頁進度環分母 | 「今天該修幾張」 | 要補 | **已裁決接受現況**：App 端用「今天已完修＋手上」相加。要精確就補這個欄位 |
| 11 | 「我的」工作磚 | 交班單簽收狀態、遺失物件數 | 要補 | 兩者都沒有端點 |
| 12 | 「我的」身分卡 | 職稱、員編、頭像 | 要補 | 身分資訊只有姓名與部門 |
| 13 | 登入頁身分卡 | 職稱 · 員編 | 要補 | `RememberedAccount` 只有姓名與登入帳號 |
| 14 | 任務頁「這間房的資訊」 | 交辦、住客要求等多列 | 要補 | 目前只有同房工務單有契約（`listRepairTickets?roomId=`） |

## 不是後端的事（列出來以免重複討論）

- **完成慶祝頁的「下一間：{房號}」**——資料其實在 `MyToday.nextTasks` 裡，是 App 側的接線，不必動後端
- **任務頁教學提示**（TipView 與「查一下」說明泡泡）——缺的是「這個人看過沒有」的本機持久化，不是後端
- **多語系、「用其他帳號登入」、移除照片、密碼眼睛鈕**——產品行為本來就沒有，要先立需求
- **「我的」六個子頁**（語言／文字大小／推播／個人資料／交班單／遺失物）——屬另一個 increment
- **報修面板照片縮圖**——照片是送出那一刻才拍的，App 手上沒有影像；要縮圖得改拍照流程
- **分類磚數量為 0 時藏起來**——原型要藏，我們照顯示，因為那個數字正是
  `待認領分類標籤顯示如下` 這句斷言要讀的節點。要照原型就得先換斷言的定位點（`/dsl-refine`）

## 怎麼開工

1. 第 1 項（通知 `event`）契約已經就緒，可以直接進後端實作
2. 第 2–5 項與第三優先那張表都要先經 `/api-plan` 把欄位寫進契約，才有東西可實作
3. 契約補完之後走既有流程：`/tasks` 排 task → `/implement` 或 `/bdd` 推進

---

## 實作批次表（2026-11-08 排定）

契約已於 `7be1b03`（通知 `event`）與 `1e559c8`（其餘九個 ADD）備齊。以下批次依
**檔案爭用**與**驗收模組**切，不是依畫面切。

### 批次 1 — 通知 `event` 落地〔進行中〕

後端加欄位＋migration＋outbox 展開時落地；App `_kindOf` 改讀它。
驗收：後端 144/148、`推播` 18/18、實際截圖確認七色出現。

### 批次 2 — 後端欄位〔**必須序列，一個 agent**〕

九個 ADD 裡有八個落在**同一支檔案** `tms/.../service/TaskExecutionService.java`：

| 端點 | 欄位 |
| --- | --- |
| `myToday` | `shift`、`floors`、`poolBadgeCount`、`unreadNotificationBadgeCount` |
| `myToday` → `engineeringSummary` | `plannedToday`、`maintenanceDone`、`maintenanceTotal` |
| `myToday` → `engineeringTickets` | `workedMinutes` |
| `deptPool` | `PoolItem.waitedMinutes`、`counts.urgent`／`staffReport`／`deskForward` |
| 任務詳情 | `TaskDetail.segmentStartedAt` |

`myToday` 的四組共用同一次 rows 查詢、`deptPool` 的兩組共用同一段組裝碼——
**平行會互相覆蓋**（T341–T345 已經踩過這個坑，見 `tasks.md` 的序列註記）。

其中兩項要跨出 `TaskExecutionService`：`unreadNotificationBadgeCount` 要查通知表、
`poolBadgeCount` 要與 `deptPool` 的 items 筆數同口徑。

驗收：後端 `bdd.RunTruthFeaturesTest` 144/148 不退化；以 curl 逐欄實測九個新欄位
（比照 T341–T345 的做法，不是只看綠燈）。

### 批次 3 — App 接線〔可平行三個 worktree，驗收排隊〕

三組檔案互不重疊，可同時派；但 simulator 與 4723 port 全機唯一，**BDD 驗收必須排隊**。

| 子批 | 畫面 | 欄位 | 驗收模組 |
| --- | --- | --- | --- |
| 3A | 房務首頁問候列、分頁列 | `shift`／`floors`／兩個徽章計數 | `今日進度` 3/3 |
| 3B | 工務首頁、房務與工務待認領 | `plannedToday`／`maintenance*`／`workedMinutes`／`waitedMinutes`／`counts` 三個 | `待認領` 9/9、`工務單` 29/31 |
| 3C | 任務頁 | `segmentStartedAt` 走秒、`source` meta（契約本來就有，App 沒解析） | `任務執行` 21/21 |

3A 的徽章元件 `TmsCountBadge`、3B 的 `TmsTag`／`TmsCategoryTile`、3C 的標題區版面
**都已經做好在等資料**，這一批主要是接線不是做版面。

### 批次 4 — 收尾

全量 `app/bdd/run-all.sh` 回歸（基準 **106/108**，紅的只能是 #11、#35），
加上實機截圖逐頁對照原型。

### 不在批次內

「我的」工作磚（交班單／遺失物）、身分卡職稱員編、任務頁住客要求——
三項都沒有 feature 承接也沒有端點，要做得先立需求，不在這一輪。
