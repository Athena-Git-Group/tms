# Phase 4 櫃檯 Web（4-W**）的共同規則（派工時附在任務後面）

工作目錄是 git worktree `/Users/Caresys/IdeaProjects/tms-mvp/.claude/worktrees/004-frontdesk-web`（分支 `feature/004-frontdesk-web`）。所有操作都在這裡做，不要 cd 到或修改 `/Users/Caresys/IdeaProjects/tms-mvp` 根目錄。

## 先讀
- 後端 Phase 的共同規則 `/Users/Caresys/IdeaProjects/tms-mvp/.claude/worktrees/004-frontdesk-web/specs/plans/004-frontdesk-board-and-dispatch/agent-briefs/backend-brief.md`：流程（`/bdd` 的 `green`／`refactor` 入口、要讀的 rules）、邊界（不改 truth、不改 stepdef 語意、不 commit、不改 tasks.md、註解規範、不碰另一個 session）都照它。**它的「完成條件」一節換成本檔的版本。**
- Phase 3 時寫的櫃檯 Web 測試線說明 `/private/tmp/claude-501/-Users-Caresys-IdeaProjects-tms-mvp/a99534e0-edb3-4654-a2f3-6f79652617c6/scratchpad/phase3/web-brief.md（Phase 3 的舊說明，若已不存在就略過）`：page object 與共用步驟的位置、測試假設的畫面結構（h1、側欄名稱「櫃檯功能」、卡片是以房號開頭的按鈕、詳情是名稱以「詳情」結尾的 `aside`、骨架以 `aria-busy="true"`）。產品畫面要符合這些假設，測試才找得到。
- `specs/plans/004-frontdesk-board-and-dispatch/implement-notes.md` 的「待使用者決定」：裡面記了測試線隱含的畫面約定（aria 屬性、`data-tone`、storage key、四個暫定譯文等），照它做。
- `tasks.md` 檔頭的 Task Binding Contract、「測試指令」、Scope Note；派給你的 Phase 的 Goal、Shared Must Read、Boundary、Test Scope。設計來源是 plan 的 `ui/ui-plan.md` 與 `ui/index.html`、`ui/desk.css`、`ui/desk.js` 雛形（使用者裁決：櫃檯以原型為準）。

## 產品碼
- `app/web/`：Vite＋React 19＋TypeScript＋Tailwind 4＋React Query＋zustand＋react-router＋i18next。`npm` 指令在 `app/web` 下執行：`npm run typecheck`、`npm test`（Vitest）、`npm run build`。不要升級或新增相依套件，除非 Boundary 明寫；真的需要時停下回報。
- 畫面文字一律走 `src/i18n/` 資源檔（繁中、英、日、越四份），繁中字面照 feature；不得出現內部值（`triage`、`pool` 等）或錯誤碼。
- 後端行為不對時，不在前端繞過；寫進回報，指出對應的 Phase 4-B。後端 4-B 各組已全部完成，後端全套 360／360。

## 跑驗收測試（一律經鎖）
```
L=/Users/Caresys/IdeaProjects/tms-mvp/.claude/worktrees/004-frontdesk-web/specs/plans/004-frontdesk-board-and-dispatch/agent-briefs/with-lock.sh
export JAVA_HOME=/Users/Caresys/Library/Java/JavaVirtualMachines/openjdk-25.0.2/Contents/Home
export MAVEN_ARGS="-Dmaven.repo.local=/Users/Caresys/.m2-004/repository -Dmaven.repo.local.tail=/Users/Caresys/.m2/repository -Dmaven.repo.local.tail.ignoreAvailability=true"
$L tms $L webbdd bash /Users/Caresys/IdeaProjects/tms-mvp/.claude/worktrees/004-frontdesk-web/app/web-bdd/run-feature.sh <相對 frontdesk-web/ 的路徑> desktop
```
- `run-feature.sh` 會在 8080 起本 worktree 的後端、5199 起開發伺服器，跑完自己收掉；`MAVEN_ARGS` 必須帶上面那串（不能裝進 `~/.m2`）。它會清空本機開發資料庫 5433 的業務資料，這是既定行為。連續跑很多次可用 `--keep`，**做完一定要 `--stop`**，並確認 8080、5199 沒有殘留你起的行程。
- 測試只跑 `desktop`（Test Scope 只要求這組），除非 Boundary 另有要求。
- 不碰另一個 session 的 8081、5434、9094、simulator、Appium；不用 8080 以外的方式起後端。
- worktree 會擋 `bash -c 'cd … && …'` 與含執行期變數的複雜指令：寫成 scratchpad 裡的腳本，用絕對路徑。臨時檔放 scratchpad 的 `t<題號>/`。
- 跑完用 `tms` 端 `target` 或 `app/web-bdd/target` 下的 cucumber JSON（找 `cucumber-reports`）存一份到 scratchpad，回報附路徑。

## 完成條件
- `green`：
  1. Test Scope 全綠（貼出場景數與通過數）。
  2. `npm run typecheck`、`npm test`、`npm run build` 全過。
  3. **櫃檯 Web 已綠清單不倒退**：之前 W 組已轉綠的 feature 全部重跑（清單見本檔最後一節，由主對話維護），不可有由綠轉紅。
  4. 整個 `frontdesk-web` 的 dry-run 仍 418（2026-11-18 新增提醒列一個場景後）、無 undefined／ambiguous（`$L webbdd` 下 `mvn -f <worktree>/app/web-bdd/pom.xml test -Dcucumber.execution.dry-run=true -Dcucumber.features=<worktree>/specs/truth/features/frontdesk-web`，帶 `MAVEN_ARGS`）。
  5. 若動了後端產品碼：後端全套與最近基準逐場景比較，由綠轉紅 0（後端基準：`/private/tmp/claude-501/-Users-Caresys-IdeaProjects-tms-mvp--claude-worktrees-004-frontdesk-web/b6a6af07-1d9c-48df-9944-85fbbcd68eeb/scratchpad/cherry-regress/full2.json`，369／369，2026-11-17 cherry-pick 3afa0c7、fd6be26、f085610 之後；舊測試線 RunCucumberTest 同時轉為 153／153 全綠）。
- `refactor`：Test Scope 仍全綠、typecheck／test／build 全過、已綠清單不倒退；有新增純函式就補 Vitest。

## 回報（精簡）
改了哪些檔與各自一行說明；Test Scope 結果；已綠清單重跑結果；typecheck／test／build；dry-run；你做的判斷（特別是畫面和雛形、feature 的取捨）；需要回交上游或使用者決定的事（附 `檔案:行號`）；結果檔路徑；收尾確認 8080／5199 無殘留。

## 櫃檯 Web 已綠清單（主對話維護）
- `帳號與登入/登入頁.feature`（T472，25 個場景）
- `帳號與登入/設定新密碼畫面.feature`（T474，17 個場景）
- `帳號與登入/帳號面板與登出.feature`（T476，12 個場景）
- `帳號與登入/登入狀態跟著分頁.feature`（T478，13 個場景）
- `帳號與登入/介面語言.feature`（T480，22 個場景）
- `總覽看板/看板分欄與範圍.feature`（T486，8 個場景）
- `總覽看板/數量卡與欄頭.feature`（T488，13 個場景）
- `總覽看板/卡片內容.feature`（T490，9 個場景）
- `總覽看板/等待膠囊與逾時.feature`（T492，10 個場景）
- `總覽看板/篩選搜尋與泳道.feature`（T494，19 個場景）
- `總覽看板/載入與讀取失敗.feature`（T496，4 個場景）
- `總覽看板/拖曳派單.feature`（T518，21 個場景，含 iPad 觸控；`總覽看板` 目錄一次跑應 84／84）
- `共用外框/側欄與工具列.feature`（T482，10 個場景）
- `共用外框/尚未開放的入口.feature`（T484，8 個場景）
- `待派單/待派單清單.feature`（T498，16 個場景中 15 綠；`:150`（Rule「直接進部門待認領的需求不會出現在待派單頁」）延後到 4-W25，紅因是住客 `X-Guest-Session` 401＋需要即時更新，重跑時它紅不算倒退）
- `待派單/空狀態與載入.feature`（T502，9 個場景）
- `待派單/撞單與送出失敗.feature`（T504，6 個場景中 5 綠；`:43` 住客 401，紅不算倒退）

（`待派單` 目錄一次跑應 47 場景 44 綠，紅的只有 `:150`、`:113`、`:43` 三個住客 401。）
- `待派單/按鈕派單與復原.feature`（T500，16 個場景中 15 綠；`:113` 同為住客 401，紅不算倒退）
- `當場回答/當場回答面板.feature`（T506，14 個場景中 11 綠；`:84`、`:91` 住客 401，`:97` 是「畫面上沒有 "復原"」抓到待派單頁首說明的規格衝突（待 `/dsl-refine`），三個紅都不算倒退）
- `當場回答/撞單與送出失敗.feature`（T508，6 個場景中 5 綠；`:34` 住客 401，紅不算倒退）

- `單筆詳情/詳情面板內容.feature`（T510，35 個場景）
- `單筆詳情/狀態標籤與狀態訊息卡.feature`（T512，25 個場景中 23 綠；`:161`、`:174` 要即時訊號，延後到 4-W25，紅不算倒退）
- `單筆詳情/歷程.feature`（T514，25 個場景中 24 綠；`:90` 已於 T516 轉綠；`:101` 要即時訊號，延後到 4-W25，紅不算倒退）
- `單筆詳情/派單區與單據管理動作列.feature`（T516，32 個場景）

（`單筆詳情` 目錄一次跑應 117／117 全綠：原本延後的 `狀態標籤:161`、`:174`、`歷程:101` 已於 T520 轉綠。）
- `即時更新/現場變化即時跟上.feature`（T520，10 個場景中 5 綠；`:17`、`:26`、`:74`、`:82` 住客 401，`:52` 規格資料矛盾待 `/dsl-refine`；紅不算倒退）
- `即時更新/連線異常提醒列.feature`（T522，8 個場景）
- `共用外框/支援的視窗與輸入方式.feature`（T524，desktop 與 ipad 各 24 個場景中 22 綠；`:77` 尺寸規格衝突、`:102` stepdef 量法缺陷，都待決，紅不算倒退；`共用外框` 目錄 desktop 一次跑應 42 中 40 綠）

（`當場回答` 目錄一次跑應 20 場景 16 綠，紅的只有 `當場回答面板` 的 `:83`、`:90`、`:97` 與 `撞單與送出失敗` 的 `:34`。）

（以上 `總覽看板` 已綠六份可一次跑：`run-feature.sh 總覽看板 desktop`，但目錄裡若還有未做的 feature（例如拖曳派單）會紅，那些不算倒退。）

（T492 起，看板頁的「校正後的現在」只有一份：`stores/server-clock.ts` 的 `useCorrectedNow()`，數量卡、欄頭、膠囊、詳情、待派單頁都讀它，不要自己 `new Date()`。）

（以上整個 `帳號與登入` 模組一次跑：`run-feature.sh 帳號與登入 desktop`，應 89／89。）

（T479 起，所有 storage 鍵與「狀態去留」集中在 `app/web/src/lib/persistence.ts`；新增要保留的狀態先在那裡宣告，跟著分頁的要接進 `lib/log-out.ts`。）

（重跑已綠清單時，可以用模組目錄一次跑：`帳號與登入` 底下已綠的檔逐一列在 `cucumber.features` 或分次跑。）

## 已知的測試共用碼修正（T472）
- 交給瀏覽器比對的正規表示式一律用 `support/BrowserRegex.literal`，不要用 `Pattern.quote`（JS 不認得 Java 的 quote 寫法）。
- `WebWorld.open(path)` 會自動接預設飯店代碼；要指定別家飯店用 `openPath(fullPath)`。產品網址形式是 `/{飯店代碼}/{頁}`。
- 測試只比對網址結尾（`endsWith`），代碼有沒有留住要另外確認。

## T478 提前建好的看板骨架（之後各組在上面擴充，不要重做）
- `pages/board/BoardPage.tsx`：三張數量卡（名稱「{數字} {說明}」、`aria-pressed`，再點取消）、泳道分段鈕（名稱「泳道」，**目前只記選擇、看板不分列**）、工具列「搜尋房號」（`RoomSearch`，依房號開頭篩）、8 欄 region（欄頭「{欄名} {張數}」）、卡片按鈕（名稱「{房號} {標題}」，點開詳情）。
- `lib/board-columns.ts`：`columnOf`（已對齊「單的狀況對照」並有 Vitest）、`BOARD_COLUMNS`、`columnsShownFor`；`countOpenTasks` 仍 not implemented。
- `stores/desk-panels.ts`：詳情與當場回答面板開哪張單，只放記憶體，登出會清。
- `features/detail/DetailPanel.tsx`（名稱「{單號} 詳情」的 aside，`aria-busy`，最小內容）、`features/answer/AnswerPanel.tsx`（名稱「當場回答 · {房號}」的 dialog，最小內容）、`api/desk.ts`（`readDeskBoard`、`readDeskTask`）。
- 驗收記下尚未做、之後各組要補的：泳道分列；英日越三語的看板字；搜尋框只收數字最多 4 碼、寬 220px、圓角 10、放大鏡圖示（`ui/desk.css:123`）；換到待派單頁要清掉數量卡篩選（`篩選搜尋與泳道.feature:88`）；卡片 `aria-expanded`；當場回答面板打開時焦點移進去；「等最久」數量卡、骨架、讀取失敗、篩選膠囊、欄頭色點與警示、空欄文字、卡片膠囊與 VIP 等。

## 預覽伺服器
- 使用者 2026-11-17 下午已不需要預覽，8082／5300 已關閉。不要自己起預覽伺服器。
- 英、日、越資源檔四份鍵必須一致；新增畫面文字時四份都要補（暫定譯文可以，回報列出）。

## 本機開發資料庫建資料
- `db-write-guard` hook 會擋 `psql -f`；不要繞過它。需要看有資料的畫面時，可以像 T486 驗收那樣在瀏覽器攔截看板 API 回假資料，或用 `app/bdd-support` 的既有方式建資料。

- 測試共用碼 `ShellFrame.openAndWait` 等的是「看板請求結束」（成功或失敗），模擬斷線時也等得到。
- 看板資料的查詢只用 `api/desk.ts` 的 `deskBoardQuery`；每 5 秒再試只掛在外框。

## 不要動的範圍（使用者 2026-11-17）
- 登入過期、Redis Session、`api/client.ts` 的 401 統一處理、後端認證過濾器（`BearerTokenAuthFilter` 等）、登出端點：使用者在另一個 session 做。這條線不要改這些；需要時照現狀，在回報說明。

- 已知環境問題：跑驗收時偶爾在場景準備的 `TRUNCATE` 出現 PostgreSQL 死結（疑似使用者 8082 預覽後端的排程工作同時在寫 5433）。遇到時單獨重跑那個場景；重跑仍綠就不算倒退，回報註明。
