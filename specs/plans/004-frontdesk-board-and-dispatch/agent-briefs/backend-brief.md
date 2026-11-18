# Phase 4 後端 Feature phase 的共同規則（派工時附在任務後面）

工作目錄是 git worktree `/Users/Caresys/IdeaProjects/tms-mvp/.claude/worktrees/004-frontdesk-web`（分支 `feature/004-frontdesk-web`）。所有操作都在這裡做，不要 cd 到或修改 `/Users/Caresys/IdeaProjects/tms-mvp` 根目錄。

## 你要照的流程
你是 `/implement` 委派的 `/bdd`，只做派給你的那一個 task 的那一個入口（`green` 或 `refactor`）。先讀並照做：
- `/Users/Caresys/IdeaProjects/tms-mvp/.claude/skills/bdd/SKILL.md`（唯讀，這是 skill 定義；它的 `rules/` 也在同一目錄下）。`green` 讀 `rules/green-最小補碼轉綠判準.md`；`refactor` 讀 `rules/refactor-綠燈保護重構判準.md`；另外讀 `rules/範疇內slice選取與推進順序判準.md`、`rules/上游交付承接與回交判準.md`、`rules/專案測試入口與既有抽象盤點判準.md`。
- `specs/plans/004-frontdesk-board-and-dispatch/tasks.md`：檔案開頭的 Task Binding Contract、「測試指令」、Scope Note；派給你的 Phase 的 Goal、Shared Must Read、Boundary、Test Scope 與 task 本身。
- `specs/plans/004-frontdesk-board-and-dispatch/implement-notes.md`：至少讀 T012–T015 各節、「待使用者決定」。

## 邊界
- `green` 只做讓 Test Scope 全綠的最小產品碼；`refactor` 只在綠燈下做不改行為的整理。不預做其他 Phase 的行為。
- **不改 truth（`specs/truth/**`）、不改 stepdef 的語意**。Test Scope 轉不綠、而原因是 stepdef 寫錯或規格本身有問題時，停下來在回報說明（附 `檔案:行號`），不要自己改測試去配合產品。
- 不 commit、不 push；不改 `tasks.md`（打勾由我做）；要記的事寫在回報裡。
- 註解用繁體中文白話，照主倉庫 `/Users/Caresys/IdeaProjects/tms-mvp/.athena/skills/tms-build/references/code-comments.md`（唯讀）的規範，不用「守衛」「空窗」「投影」，不寫修訂註記。`tms/src/main` 是反編譯還原的程式，風格以現有程式為準。用正常的編輯工具寫檔，被 hook 退回就照它改。
- **Flyway：不改任何既有 migration**（會壞 checksum）；需要新的就寫新檔，版號接在最後一支之後。
- 不碰另一個 session 的東西：8081 後端、`tms-bdd-postgres`（5434）、`tms-bdd-kafka`（9094）、simulator、Appium；不 kill 不是你起的行程；不動容器。

## 編譯與測試：一律經過鎖
```
L=/Users/Caresys/IdeaProjects/tms-mvp/.claude/worktrees/004-frontdesk-web/specs/plans/004-frontdesk-board-and-dispatch/agent-briefs/with-lock.sh
export JAVA_HOME=/Users/Caresys/Library/Java/JavaVirtualMachines/openjdk-25.0.2/Contents/Home
$L tms ./tms/mvnw -q -o -f tms/pom.xml test-compile
$L tms env TESTCONTAINERS_RYUK_DISABLED=true ./tms/mvnw -f tms/pom.xml test -Dtest=RunTruthFeaturesTest -DargLine="-Dapi.version=1.44" -Dsurefire.excludeJUnit5Engines=cucumber "-Dcucumber.features=<worktree 絕對路徑>/specs/truth/features/backend/<模組>/<檔>.feature"
```
worktree 的指令檢查會擋 `bash -c 'cd … && …'`；寫成 scratchpad 裡的腳本再經鎖執行，或用絕對路徑。

## 完成條件
- `green`：Test Scope 全綠（貼出場景數與通過數）。另外跑一次**後端全套**（不帶 `cucumber.features`），和最近一次基準逐場景比較（基準檔：`/private/tmp/claude-501/-Users-Caresys-IdeaProjects-tms-mvp/a99534e0-edb3-4654-a2f3-6f79652617c6/scratchpad/phase3/fix-t439-full.json`，360 個場景、245 通過），**不可以有任何由綠轉紅**；由紅轉綠的逐筆列出。舊測試線 `RunCucumberTest` 也跑一次，失敗不能超過原本那 5 筆（SOP 勾記、查房最後一筆事件、SOP 重複鍵、條碼 token、PMS outbox）。
- `refactor`：Test Scope 仍全綠、後端全套仍無由綠轉紅。

## 回報（精簡）
改了哪些產品碼檔與各自一行說明（新增的 migration 要寫明內容）；Test Scope 結果；後端全套與基準的逐場景比較（新的通過數、由紅轉綠清單、由綠轉紅必須為 0）；舊測試線結果；你做的判斷；需要回交上游的問題（附 `檔案:行號`）。把後端全套的結果檔路徑寫在回報裡，下一題會拿它當新的基準。
