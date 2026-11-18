# Tasks: 004-frontdesk-board-and-dispatch（櫃檯看板與派單）

**Plan Package**: `specs/plans/004-frontdesk-board-and-dispatch`
**Core Inputs**: `spec.md`, `plan.md`, `research.md`, `truth-delta.md`, `ui/ui-plan.md`, `ui/*.html`, `ui/desk.css`, `ui/desk.js`, `specs/truth/techstack.md`, `specs/truth/contracts/**`, `specs/truth/data/data-model.dbml`, `specs/truth/features/backend/**`, `specs/truth/features/frontdesk-web/**`

本檔承接 `truth-delta.md` 四節：`/technical-research`（櫃檯 Web 這一端的技術、兩個新的測試模組）、`/api-plan`（`/api/v1/desk/**` 六支端點、`GET /staff/stream` 入契約、登入與改密碼補強、登入前查飯店名稱、刪除 `POST /tasks/{id}/triage`）、`/data-plan`（派出後復原、`tasks.task_no`／`answer`／`ended_at`、`task_counters`、`triage_undone`、`actor_role` 加 `duty_manager`、`tenants.code`）、`/dsl-refine`（`frontdesk-web` 七個模組 27 份 feature；後端新增 12 份、修改 3 份 feature）。

**待使用者確認的預設值**：truth feature 裡有 56 個 `# [need clarification]` 標記（櫃檯 Web 54 個、後端 `櫃檯看板與派單/櫃檯看板讀取.feature` 2 個）。它們都是 `ui/ui-plan.md` 標「預設，待使用者確認」的畫面文字、秒數或行為。**本檔每一題都照 feature 目前寫的值實作，不為它們停下**；使用者之後改判時，由 `/dsl-refine` 改 truth，再補一輪對齊。各 Feature phase 的 Boundary 會寫出該檔有幾個。

## Task Binding Contract

- 每個**開發任務**都必須對應 `truth-delta.md` 的 ADD / MODIFY / DELETE / NOOP 語意。
- Phase 1 `Setup` 只做本輪新增技術的基礎建設、技術環境、兩個半天的 spike 與最後的 smoke-test；不寫 DSL 語意、不寫產品行為。
- Phase 2 `Foundational` 只建立後續實作與測試共用的 migration、示範資料、fixture、helper、入口與落點骨架；每則寫「只做／不做」。
- Phase 3 `Test Alignment & Implementation` 在寫產品碼之前，把本輪所有受影響 DSL 的自動化測試對齊最新版 truth。
- Truth 參照使用 `specs/truth/**`；plan 參照才使用本 plan package 內相對路徑（`truth-delta.md`、`plan.md`、`research.md`、`ui/...`）。
- Feature `Shared Must Read` 除 feature、同模組 DSL、對應 `truth-delta` 列外，還必須列入回追到的 planner 產物。
- 程式路徑省略前綴時：後端產品碼是 `tms/src/main/java/com/athena/tms/`，後端 stepdef 是 `tms/src/test/java/com/athena/tms/bdd/`，櫃檯 Web 是 `app/web/src/`，櫃檯 Web 的 stepdef 是 `app/web-bdd/src/test/java/com/athena/web/bdd/`。
- T 編號從 T001 起，004 自成一份（003 也是自己從 T001 編到 T155，沒有全專案連號）。

**測試指令**（每個 Feature phase 的 Test Scope 用這幾組跑）：
- 後端（`tms/`，JDK 25、Testcontainers、OrbStack；在 repo 根目錄執行）：
  ```
  JAVA_HOME=/Users/Caresys/Library/Java/JavaVirtualMachines/openjdk-25.0.2/Contents/Home TESTCONTAINERS_RYUK_DISABLED=true \
    ./tms/mvnw -f tms/pom.xml test -Dtest=RunTruthFeaturesTest -DargLine="-Dapi.version=1.44" \
    -Dsurefire.excludeJUnit5Engines=cucumber \
    "-Dcucumber.features=../specs/truth/features/backend/<模組>/<檔名>.feature"
  ```
  undefined 檢查加 `-Dcucumber.execution.dry-run=true`。`-Dapi.version=1.44` 是讓 Testcontainers 連得上 OrbStack 的 Docker；`-Dsurefire.excludeJUnit5Engines=cucumber` 一定要帶，否則 surefire 會多跑一次不受 glue 限制的 Cucumber 引擎，兩組 Spring 設定相撞而以失敗收場。`cucumber.features` 可以給單一檔或整個模組目錄；不帶就跑全套（約 3 分鐘）。Cucumber Expression 中文句含 `/` 要寫 `\\/`。
- 櫃檯 Web 驗收（`app/web-bdd/`，Cucumber-JVM ＋ Playwright for Java）：`bash app/web-bdd/run-feature.sh <frontdesk-web 下的路徑，例如 待派單/待派單清單.feature> [desktop|ipad]`（T021 建立；內含啟動可撥時鐘的 8080 後端、`app/web/` 的開發伺服器與瀏覽器）。第二個參數不給時是 `desktop`（電腦 1280 × 720 滑鼠）；`ipad` 是 iPad 橫向 1180 × 820、WebKit、觸控。後端產品碼改過就要重啟 8080。
  undefined 檢查：`cd app/web-bdd && mvn -q test -Dcucumber.execution.dry-run=true -Dcucumber.features=../../specs/truth/features/frontdesk-web/<路徑>`。
- 櫃檯 Web 單元測試（只測純計算）：`cd app/web && npx vitest run`。
- 員工 App 測試線（只在搬動 `BackendChannel` 後確認沒壞）：`cd app/bdd && mvn -q test -Dtest=RunModuleFeatureTest -Dsurefire.excludeJUnit5Engines=cucumber -Dcucumber.execution.dry-run=true -Dcucumber.features=../../specs/truth/features/ios-app`。

## Scope Note（停止範圍、判斷與回交）

1. **專案憲章不存在。** `.agents/constitution/CONSTITUTION.md` 與 `shared.md` 都沒有這兩個檔，照 001–003 的做法略過，沒有額外的上位約束。
2. **沒有回交 `/dsl-refine` 的範圍。** 盤點 27 份櫃檯 Web feature 與 15 份後端 feature 的每一步：合併介面根與同模組 `dsl.md` 後，每一步都剛好對到一個句型，沒有重複定義，也沒有找不到定義的句；本輪新增的 409 個句型（櫃檯 Web 302 個、後端 107 個）也沒有任何一句沒被用到。
3. **`contracts` 與 `data` 不加 quality gate。** 回追到的 `specs/truth/contracts/**` 與 `specs/truth/data/data-model.dbml` 都在 `specs/truth/**`，以 Feature 的 Test Scope 全綠當閘門。只有 `ui/` 家族是 plan-side 產物，在最後一個櫃檯 Web 消費者之後加一題 gate（Phase 4-G）。
4. **`features/acceptance/` 不列入 `Shared Must Read`。** 它是 `/spec-by-example` 的 plan-side 產物，回追順序不經過它；56 條驗收 Rule 都已由 truth feature 承接（`truth-delta.md`「驗收例子承接對照」）。
5. **DELETE 只有一列，而且沒有 DSL 句。** `truth-delta.md` 的 `/api-plan` 刪除 `POST /tasks/{id}/triage`（`tasksTriage`）。truth feature 與 `dsl.md` 從來沒有描述過它，所以 Phase 3 沒有 `[BDD-REMOVE]`；產品面的移除在 Phase 4-B16 的 `[CODE-REMOVE] -> [REGRESSION]`，連同契約沒收的舊端點 `/desk/triage`、`/desk/triage/{taskId}`、`/desk/tasks` 與保護它們的舊測試一起收掉。
6. **不出題的句（既有 stepdef 的行為已符合現在的語意）。** 後端介面根的 17 句（`"{員工}" 已停用`、`現在時間是 "{時刻}"`、`時間來到 "{時刻}"`、`已有以下工務單：`、`"{員工}" 名下有以下任務：`、`房務待認領有以下任務：`、`"{員工}" 認領 "{任務}"`、`"{員工}" 完成 "{任務}"`、`這次操作被拒絕`、`"{任務}" 的負責人是 "{員工}"`、三句推播 Then 等）、`帳號與登入` 的 3 句 Given 與 1 句 When（`"{員工}" 的員工編號密碼為 "{員工編號}" / "{密碼}"`、`"{員工}" 的帳號是臨時密碼`、`"{員工}" 已連續輸入錯誤密碼 {次數} 次`、`"{員工}" 用員工編號 "{員工編號}" 密碼 "{密碼}" 登入`）：`truth-delta.md` 那一列把其中三列（臨時密碼、連續輸入錯誤密碼 N 次、用員工編號登入）的實作語意改寫成契約現在的樣子，語意文字確實有改；但既有 stepdef 的行為已經符合新語意（打 `/staff-accounts/login`、送的就是 `LoginRequest` 的 `loginId` 與 `password`），所以不出題、`主管工作台` 3 句、`推播` 10 句。`be/dsl.md` 用語對照的改名有落地碼（`Terms.java`），併入 T022 與 T024。
7. **起點是 `bded4f2`。** 規格在 `ff4909e` 定稿；之後 003 收尾的兩個 commit（`ef22e09`、`bded4f2`）動過 `tms/src/main/java/com/athena/tms/config/SecurityConfig.java` 與 `app/bdd/` 的 stepdef，本檔引用的行號以 `bded4f2` 為準。T002（搬 `BackendChannel`）會動 `app/bdd/`，Phase 4-B01、4-B10、4-B16 會動 `SecurityConfig.java`；**開工前先跑 `git status`，工作樹若有別人還沒提交的改動就停下來問使用者**，不要把別人的改動混進 004 的 commit。
8. **Flyway 版號。** 目前最後一支是 `V23__repair_ticket_closed_and_verification_failed.sql`，本檔寫 `V24`、`V25`、`V26`；開工時若版號已被占用就順延，三支的先後不變。**不改任何既有 migration**（含 `V1__schema.sql` 的 `chk_task_events_actor_role` 與 `V2__seed_dev.sql` 的飯店種子；動了會壞 checksum），一律寫新檔。
9. **`tenants.code` 的回填值是本檔的判斷，不是 truth。** truth 只說「代碼由營運逐家指定，不能從飯店名自動產生」。本檔在 T008 給這個 repo 認得的列固定代碼（`V2` 種子的「示範飯店」＝`demo-hotel`、「示範二店」＝`demo-hotel-2`；後端測試建立的「測試飯店」＝`test-hotel`；`athena` 留給櫃檯 Web 驗收的預設飯店「雅典娜酒店」），其他認不得的飯店一律讓 migration 失敗並印出是哪幾家，不替它們編代碼。正式環境的對照表要由營運提供，見回交清單。
10. **舊測試線不是 truth，但會被本輪弄紅。** `tms/src/test/resources/features/`（`RunCucumberTest`）裡的 `03-desk-triage.feature` 保護舊端點與「當場回答寫進 `note`」，`14-sse-stream.feature` 保護舊的串流行為，`05-identity-tenancy.feature` 用 `/desk/probe` 驗角色。處理方式：`03-desk-triage.feature` 在 Phase 4-B16 刪除（Phase 4-B06 之後它會先紅，這是預期，不要回頭修它），同時只刪 `integration/stepdefs/when/StaffDeskWhenSteps.java` 與 `then/StaffDeskThenSteps.java` 裡**只服務櫃檯判讀與舊任務清單查詢的步驟**；這兩個檔案本身保留，因為 `02-staff-pool.feature` 的認領、開始完成、查待認領、勾 SOP 等步驟也定義在裡面、別處沒有；`14-sse-stream.feature` 與 `05-identity-tenancy.feature` 與新 truth 衝突的案例，在對應的 Phase 4-B12、4-B13、4-B10 內改寫成新行為，沒有衝突的不動。
11. **使用者已定、本輪照做不再問的事。** 飯店時區沿用伺服器時區（「今天」用注入的 `Clock`）；住客進度頁顯示回覆只做到後端回得出 `answer`，不做住客頁面；`duty_manager` 只放行登入、改密碼、`/api/v1/desk/**` 與 `/staff/stream`，手機 App 的主管功能不放行；不接翻譯服務，只把單上既有的 `llm_translated` 帶出來；主管延後只顯示誰延後的，不顯示時間與原因（那是 005）；`TaskEventType` 清單漏放 `repair_verification_failed` 的既有狀況不順手改。
12. **雛形的示範資料與 truth 的預設不同，以 truth 為準。** `ui/desk.js` 的示範密碼是 `tms1234`、值班主管叫「周值班」；`specs/truth/features/frontdesk-web/dsl.md` 的預設密碼是 `Tms12345`、值班主管是「吳家豪」。測試資料與產品行為照 truth；雛形只用來對齊版面、文字位置、顏色與互動。
13. **歷程上沒有定案句子的事件先不顯示。** `ui/ui-plan.md` 待決事項 1：`task_events` 25 種事件裡只有一部分有定案的句子。truth feature 有寫的照寫；沒有寫的事件種類，畫面略過那一筆，不自己編句子（雛形裡的暫定句子不算定案，除非 `單筆詳情/歷程.feature` 寫了）。
14. **兩組瀏覽器設定各跑一次才算綠。** 每個櫃檯 Web Feature phase 的 `[BDD-GREEN]` 以 `desktop` 為準先綠；`ipad` 那一組在 Phase 4-W27 與 Phase 4-R 整套再跑。觸控拖曳怎麼由自動化驅動，照 T005 的結論；T005 判定不可行時走 `research.md`「建立專案時要先做的 spike」第 6 點的退路（iPad 尺寸的拖曳改用滑鼠事件驅動，觸控拖曳列為真機人工驗收），並記在 `implement-notes.md`。
15. **spike 的結論要回寫 truth，但不是 `/implement` 寫。** `specs/truth/techstack.md` 的「櫃檯 Web 拖曳派單」「櫃檯 Web 驗收的視窗與輸入」兩列現在寫「由 spike 選定／確認」。T004、T005 的結論先記在 `specs/plans/004-frontdesk-board-and-dispatch/implement-notes.md`，再回交 `/technical-research` 改 truth。

## Phase 1: Setup

**Goal**: 本輪新增櫃檯 Web 這一端與它的驗收測試線。先把三個新目錄（`app/web/`、`app/bdd-support/`、`app/web-bdd/`）的套件、設定與執行環境備好，用兩個半天的 spike 把「拖曳用哪個套件」「觸控拖曳能不能自動化」從文件上的推測換成實跑的結論，最後用 smoke-test 確認三條測試線都起得來。不寫任何 DSL 語意，不寫任何產品行為。

- [X] T001 建立 `app/web/`：`React` ＋ `TypeScript` ＋ `Vite` 單頁應用，並裝好本輪要用的前端套件
  - Read:
    - `specs/truth/techstack.md` -> `前端` 的櫃檯 Web 九列（框架與建置、換頁、後端資料、畫面狀態、樣式與版面、多語系、登入憑證、連後端、即時更新）、`測試與驗證` 的「櫃檯 Web 單元測試」、`本次開發不引入的技術`
    - `research.md` -> 決策 2、決策 7
  - 套件：`react`、`react-dom`、`typescript`、`vite`、`@vitejs/plugin-react`、`react-router`、`@tanstack/react-query`、`zustand`、`tailwindcss`、`i18next`、`react-i18next`、`vitest`。版本一律鎖定建立當下的穩定版（`package.json` 不用 `^`、`~`，並提交 lockfile）。拖曳套件留給 T004 決定後再裝。
  - 設定：`vite.config.ts` 的開發伺服器把 `/api` 與 `/staff/stream` 代理到 `http://localhost:8080`；API 位址用建置時的環境變數設定，預設相對路徑（同源部署）。`tailwind` 接上 `ui/desk.css` 用到的設計變數（顏色、圓角、字級只搬變數，不搬雛形的版面）。
  - 不裝：`next`、任何元件測試套件、`playwright-bdd`、`cucumber-js`、建在 HTML Drag and Drop API 上的拖曳套件、Service Worker 相關套件；不用瀏覽器內建的 `EventSource`。不拿 `~/IdeaProjects/tms-web` 的程式、設定或版本當基底。

- [X] T002 建立 Maven 模組 `app/bdd-support/`，把 `BackendChannel` 從員工 App 測試線搬出來
  - Read:
    - `specs/truth/techstack.md` -> `測試與驗證` 的「BDD 共用的後端資料準備」
    - `research.md` -> 決策 5
    - `app/bdd/pom.xml`、`app/bdd/src/test/java/com/athena/app/bdd/BackendChannel.java`（1386 行）
  - 新模組 artifactId `app-bdd-support`，`BackendChannel` 放 `app/bdd-support/src/main/java/`、套件不在 `com.athena.app.bdd` 底下（例如 `com.athena.bddsupport`）；模組內不得有任何 Cucumber 步驟或 `@Before`。`app/bdd-support/pom.xml` 要帶 `BackendChannel` 用到的相依：`org.postgresql:postgresql`、`jackson-databind`、`spring-security-crypto`，以及執行期相依 `spring-jcl`、`slf4j-simple`。它們現在在 `app/bdd/pom.xml` 約 101–133 行、都是 test scope；`BackendChannel` 搬到 `src/main` 之後要改成 compile scope（只在執行期用到的那兩個用 runtime scope），版本照原樣；`app/bdd/pom.xml` 改成引用 `app-bdd-support`，由它把這些相依帶進來。
  - `app/bdd/` 這一側：`AppiumWorld` 以及其他原本和 `BackendChannel` 同在 `com.athena.app.bdd` 套件、所以不需要 import 的類別，全部要補上新套件的 import；`steps/` 下各套件原有的 import 也改到新位置。改完以 `mvn -q -f app/bdd/pom.xml test-compile` 確認沒有漏。
  - 因為沒有上層 pom，`mvn -q -f app/bdd-support/pom.xml install` 這一步放進 `app/bdd/lib/env.sh`（`run-all.sh`、`run-f3.sh`、`run-module.sh` 三支腳本都會 source 它，放一處就夠）。不經腳本、直接下 `mvn -f app/bdd/pom.xml …` 的人要自己先 install 一次，否則會找不到 `app-bdd-support`；這一點寫進 `env.sh` 的註解與 `implement-notes.md`。
  - 只搬家、不改行為：方法簽章、SQL、用語對照都原樣搬。開工前確認 `app/bdd/` 的工作樹是乾淨的（見 Scope Note 第 7 條）。

- [X] T003 建立 Maven 模組 `app/web-bdd/`：`Cucumber-JVM` ＋ `Playwright for Java`
  - Read:
    - `specs/truth/techstack.md` -> `測試與驗證` 的「櫃檯 Web BDD techstack」「櫃檯 Web 驗收的視窗與輸入」
    - `specs/truth/features/frontdesk-web/dsl.md` -> 開頭的 BDD techstack 與「共通實作約定」的「瀏覽器設定」
    - `app/bdd/pom.xml` -> Cucumber 與 JUnit Platform 的版本（沿用同一組）
  - 相依：`io.cucumber:cucumber-java`、`io.cucumber:cucumber-picocontainer`、`io.cucumber:cucumber-junit-platform-engine`、`org.junit.platform:junit-platform-suite`、`com.microsoft.playwright:playwright`、`app-bdd-support`（T002）。glue 套件是 `com.athena.web.bdd`，不在 `com.athena.app.bdd` 底下，執行時不得載入 `AppiumWorld`。
  - 設定：runner `RunFrontdeskWebFeaturesTest` 讀 `specs/truth/features/frontdesk-web/`（`# language: zh-TW`）；用系統屬性 `web.profile` 選兩組瀏覽器設定：`desktop`＝Chromium、視窗 1280 × 720、滑鼠；`ipad`＝WebKit、視窗 1180 × 820、開啟觸控。安裝 Playwright 的瀏覽器（Chromium 與 WebKit）。

- [X] T004 半天 spike：拖曳函式庫選型（`@dnd-kit/core` 或 `@dnd-kit/react`）
  - Read:
    - `specs/truth/techstack.md` -> `前端` 的「櫃檯 Web 拖曳派單」
    - `research.md` -> 決策 4、「建立專案時要先做的 spike」第 1、3、5、6 點
    - `ui/ui-plan.md` -> 畫面 5「總覽看板」的拖曳派單段
  - 在 `app/web/spikes/dnd/` 做一個最小畫面：一張可拖的卡片、兩個拖放區，兩個套件各做一次。四個動作都要成立：起拖、滑進拖放區（拖放區的說明文字有變）、在拖放區放開、在拖放區以外放開（留在原位）；滑鼠與觸控走同一套程式。
  - 產出：選定其中一個套件，裝進 `app/web/` 並鎖版本；把選擇、理由與版本記在 `specs/plans/004-frontdesk-board-and-dispatch/implement-notes.md`。兩個都不可行時，結論是「自己用 Pointer Events 寫拖曳」，同樣記下來。
  - 只花半天；spike 的程式不進產品（`app/web/spikes/` 不被 `src/` 引用），不寫看板、不寫派單。

- [X] T005 半天 spike：觸控拖曳的自動化測試可行性，以及瀏覽器時鐘
  - Read:
    - `specs/truth/techstack.md` -> `測試與驗證` 的「櫃檯 Web 驗收的視窗與輸入」
    - `research.md` -> 決策 5、「建立專案時要先做的 spike」第 2 至 6 點
    - T004 的 spike 畫面與結論
  - 用 `app/web-bdd/` 的 Playwright for Java 驅動 T004 的 spike 畫面，`desktop` 與 `ipad` 兩組設定都跑，四個動作都要能被測試驅動並驗到結果；確認測試工具送出的滑鼠與觸控輸入，拖曳套件確實當成 Pointer Events 收到。
  - 順便確認：Playwright 撥瀏覽器時鐘之後，畫面上一個「等 N 分」的數字跟著變。
  - 產出：在 `implement-notes.md` 記下觸控拖曳在自動化裡是怎麼驅動的（用哪一組 API、要不要補送中間的移動事件）；不可行時寫明走退路（iPad 尺寸的拖曳改用滑鼠事件驅動，觸控拖曳列為真機人工驗收）。兩個結論都要回交 `/technical-research` 改 `techstack.md`（Scope Note 第 15 條）。
  - 只花半天；不寫任何正式的 stepdef 或 page object。

- [X] T006 事件串流的端到端連線試驗
  - Read:
    - `specs/truth/techstack.md` -> `前端` 的「櫃檯 Web 即時更新」
    - `research.md` -> 決策 8、「建立專案時要先做的 spike」最後兩段
    - `tms/src/main/java/com/athena/tms/service/StreamConnection.java`、`controller/` 下的串流控制器
  - 用 `frontdesk` 帳號，瀏覽器經 Vite 代理以 `fetch` 連上 `GET /staff/stream`（帶 `Authorization: Bearer`），確認依序收到 `init`、`resync`、每 15 秒的 `heartbeat`；由住客送一張會進待派單的需求，確認收到 `task` 並量從送出到收到的秒數；把後端停掉再啟動，確認前端偵測得到斷線並能重新連上。
  - 產出：把量到的延遲、代理要不要關緩衝之類的設定記在 `implement-notes.md`，需要的代理設定直接寫進 `vite.config.ts`。不改後端的收件範圍（那是 Phase 4-B12），不寫產品的串流客戶端。

- [X] T007 smoke-test：三條測試線都起得來
  - Read:
    - `specs/truth/techstack.md` -> `測試與驗證`
  - 依序確認並停手：①`cd app/web && npx vitest run` 跑過一支只斷言 `1 + 1` 的測試、`npm run build` 產出靜態檔；②`app/web-bdd/` 在 `desktop` 與 `ipad` 兩組設定下各開一次瀏覽器，載入 `app/web/` 開發伺服器的首頁，並經代理打到 8080 後端的一支既有端點（例如 `POST /api/v1/staff-accounts/login` 回 401）；③`app/bdd/` 的 dry-run（指令見上）在 `BackendChannel` 搬家後仍然編得過、沒有 undefined。
  - 不跑本輪任何 Feature，不寫 stepdef 語意。

## Phase 2: Foundational

**Goal**: 只建立後續要用的 migration 與回填、示範資料、後端的列舉與型別落點、兩條測試線的 fixture 與 helper、櫃檯 Web 與它的測試模組的骨架。不寫派單、復原、當場回答、看板查詢、收件範圍這些規則，不寫任何畫面行為。

- [X] T008 新增 Flyway migration `V24`：飯店識別代碼 `tenants.code`，並替既有飯店補代碼
  - Read:
    - `truth-delta.md` -> `/data-plan` ADD `tenants.code`（含實作須知的順序）
    - `specs/truth/data/data-model.dbml` -> `Table tenants` 的 `code`（格式、保留字清單、唯一、寫入後不改）
    - `tms/src/main/resources/db/migration/V2__seed_dev.sql:8-12`（種子的兩家飯店）
  - 只做：一支 `V24__tenant_code.sql`，順序固定：①加可為空的 `code text`；②回填：`name='示範飯店'` 寫 `demo-hotel`、`name='示範二店'` 寫 `demo-hotel-2`、`name='測試飯店'` 寫 `test-hotel`；③回填後若還有 `code IS NULL` 的列，`RAISE EXCEPTION` 並列出那些飯店的 `id` 與 `name`（不替它們編代碼）；④加 `NOT NULL`、唯一、格式 CHECK（`^[a-z][a-z0-9]*(-[a-z0-9]+)*$`、長度 3–32）與保留字 CHECK（清單照 dbml 逐字）。
  - 不做：不改 `V2__seed_dev.sql`；不做修改代碼的功能或舊代碼轉址；不寫查飯店名稱的端點（Phase 4-B01）。
  - 備註：這一支一上去，`code` 就是必有值。T013（後端 `TenantFixtures`）與 T016（`BackendChannel` 建飯店）完成前，後端 BDD 與員工 App 測試線會因 `INSERT INTO tenants` 缺 `code` 而紅，這是預期；T008、T013、T016 三題都做完才回到綠，中間不要回頭放寬 `NOT NULL`。

- [X] T009 新增 Flyway migration `V25`：`tasks.task_no`、`task_counters`、`tasks.answer`、`tasks.ended_at`，並回填既有的單
  - Read:
    - `truth-delta.md` -> `/data-plan` ADD `tasks.task_no`、`Table task_counters`、`tasks.answer`、`tasks.ended_at` 四列的「實作須知」
    - `specs/truth/data/data-model.dbml` -> `Table tasks` 的 `task_no`、`answer`、`ended_at` 與兩個索引、`Table task_counters`
    - `tms/src/main/resources/db/migration/V5__staff_app_increment.sql` -> 建 `repair_ticket_counters` 的那一段（照它的寫法）
  - 只做：一支 `V25__task_no_answer_ended_at.sql`。①`task_counters`（每租戶一列、`last_no`）。②`tasks.task_no`：先加欄位；替既有 `type` 不是 `repair` 的單回填（每租戶依 `created_at`、再依 `id` 由舊到新編號，格式 `T-` 加 4 位數）；把 `task_counters.last_no` 設成該租戶回填到的最後一號；再加 `(tenant_id, task_no)` 唯一索引；`type=repair` 維持 NULL。③`tasks.answer`：加欄位；替既有的當場回答單回填（有 `answered` 事件的單，取該事件的 `payload.answer`）。④`tasks.ended_at`：加欄位；回填已結束的單（`inspected`、`cancelled`、`closed` 取進入該狀態那筆事件的 `created_at`；`done` 且 `type` 為 `amenity`、`other` 或有 `answered` 事件的取 `done_at`，沒有時取那筆事件的 `created_at`）；再加 `(tenant_id, ended_at)` 索引。四件事的回填都在這一支裡做完。
  - 不做：不還原舊當場回答單被蓋掉的 `note`（truth 只要求回填 `answer`）；不改建單取號、不改任何狀態轉移寫 `ended_at` 或 `answer`（Phase 4-B06、4-B07）；不讓工務單領 `T-` 單號。

- [X] T010 新增 Flyway migration `V26`：`task_events.actor_role` 加 `duty_manager`，加張數查詢用的索引
  - Read:
    - `truth-delta.md` -> `/data-plan` ADD `task_events.actor_role` 值 `duty_manager`、ADD `task_events` 索引 `(tenant_id, actor_id, created_at)`
    - `specs/truth/data/data-model.dbml` -> `Table task_events` 的 `actor_role` 與索引
    - `tms/src/main/resources/db/migration/V1__schema.sql:256-257`（`chk_task_events_actor_role` 的現行寫法）
  - 只做：一支 `V26__task_events_actor_role_duty_manager.sql`：`DROP CONSTRAINT chk_task_events_actor_role` 後以同名重建，值集加上 `duty_manager`（其餘值照 V1 原樣）；加 `(tenant_id, actor_id, created_at)` 索引。
  - 不做：不動 `V1__schema.sql`；不對 `task_events.type` 加 CHECK（資料庫本來就沒有）；不寫張數查詢（Phase 4-B07）。

- [X] T011 調整 `scripts/seed-demo.sh`：飯店代碼與櫃檯 Web 用得到的示範資料
  - Read:
    - `truth-delta.md` -> `/data-plan` ADD `tenants.code`（「開發種子資料與測試裡建立飯店的地方都要補代碼」）
    - `scripts/seed-demo.sh:20-40`（`INSERT INTO tenants` 與示範帳號段）
    - `ui/ui-plan.md` -> 「靜態雛形輸出規劃」的示範帳號表
  - 只做：`INSERT INTO tenants` 帶 `code='demo-hotel'`（`ON CONFLICT` 不改既有的 `code`）；確認示範飯店有 `frontdesk`、`manager`、`duty_manager` 各一個可登入的帳號與幾頁住客資訊頁（常用回覆的來源），沒有就補。
  - 不做：不放 `tasks`／`task_events` 以外規則的新資料表；不改 seed 的其他段落。它不是任何 Feature 的 Test Scope，驗法是 seed 跑完後三種角色都能從櫃檯 Web 登入、登入頁顯示「示範飯店」。

- [X] T012 後端的列舉與型別落點
  - Read:
    - `truth-delta.md` -> `/api-plan` ADD 櫃檯看板與派單 API 各列、ADD `GET /staff/stream` 的 schema；`/data-plan` ADD `triage_undone`
    - `specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml`、`員工事件串流.yaml`、`shared.yaml`（新的 `Error.code`）
    - `tms/src/main/java/com/athena/tms/common/codes/TaskEventType.java:6-25`、`common/codes/StaffRole.java`、`dto/TaskResponse.java`、`common/error/StateConflictException.java:17`
  - 只做：`TaskEventType` 加 `triage_undone`；`StaffRole` 加常數 `DUTY_MANAGER`；在 `dto/desk/` 建 `DeskBoard`、`DeskMyToday`、`DeskTask`、`DeskGuestText`、`DeskTaskDetail`、`DeskTimelineEntry`、`DeskQuickReply`、`DeskAssignRequest`、`DeskAnswerRequest`、`PublicHotel` 的 record（欄位名與型別照契約）；新的錯誤碼常數；新控制器 `controller/DeskBoardController.java` 掛在 `/api/v1/desk`，六支操作先回 501。
  - 不做：不改 `StaffRole.ALL` 與任何權限規則（Phase 4-B10）；不把 `repair_verification_failed` 補進 `TaskEventType` 的清單；不寫任何查詢或狀態轉移；不動舊的 `controller/DeskController.java`（Phase 4-B16）。

- [X] T013 後端測試 fixture：飯店代碼與三種新角色的建立入口
  - Read:
    - `specs/truth/features/backend/dsl.md` -> 部門用語對照、`飯店有以下員工：`
    - `tms/src/test/java/com/athena/tms/bdd/support/TenantFixtures.java:20-40`、`support/StaffFixtures.java:39-66、95-126`
  - 只做：`TenantFixtures` 建「測試飯店」時帶 `code='test-hotel'`，並加一個「另建一家飯店（名稱、代碼）」的入口；`StaffFixtures` 加建立 `frontdesk`（`dept=frontdesk`）、`duty_manager`、`owner` 帳號的入口與「把帳號設成臨時密碼」之外需要的讀寫（連續錯誤次數、鎖定到期時間）。
  - 不做：不改 `CommonGivenSteps` 的 `飯店有以下員工：`（T022）；不寫任何斷言。

- [X] T014 後端測試 helper 與 stepdef 落點：櫃檯看板與派單
  - Read:
    - `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> 模組說明與用語對照
    - `tms/src/test/java/com/athena/tms/bdd/support/BddApiClient.java:62-90`、`support/TaskFixtures.java`、`support/ReservationFixtures.java`、`support/LastOperationGuard.java`
  - 只做：建 `steps/desk/` 套件（`package-info.java`、`DeskCommonSteps.java` 放介面根的 5 句、`DeskGivenSteps.java`、`DeskWhenSteps.java`、`DeskThenSteps.java` 的空殼）與 `support/DeskContext.java`（記最近一次派單用的 `Idempotency-Key`、最近一次看板與詳情回應）；`support/DeskApi.java` 包六支 `/api/v1/desk/**` 的呼叫（帶或不帶 `Idempotency-Key` 兩種）與以住客 session 呼叫 `createGuestRequest`、`cancelGuestRequest`、`listGuestRequests` 的入口。
  - 不做：不寫任何句型的語意（Phase 3）；不直接寫資料庫假造 `triage` 以外的狀態。

- [X] T015 後端測試 helper 與 stepdef 落點：員工事件串流
  - Read:
    - `specs/truth/features/backend/員工事件串流/dsl.md` -> 模組說明
    - `specs/truth/contracts/groups/現場作業/員工事件串流.yaml`、`specs/truth/contracts/components/schemas/員工事件串流.yaml`
    - `tms/src/test/java/com/athena/tms/integration/` 下讀 `/staff/stream` 的既有測試 client（`14-sse-stream.feature` 的 stepdef）、`tms/src/test/java/com/athena/tms/bdd/support/KafkaGroupAwait.java`
  - 只做：`support/StaffStreamClient.java`：以某位員工的憑證連 `GET /staff/stream`、把收到的事件依序收進清單、提供「等到某種事件出現，上限 N 秒」與關線；scenario 結束自動關線。`support/StreamFaults.java`：讓串流進入與離開兩種降級原因的測試入口（事件落後、`kafka_unavailable`）。建 `steps/staffstream/` 套件與三個空殼檔。
  - 不做：不斷言誰收得到什麼；不改 `StreamConnection` 或 `TaskEventsSseConsumer`。

- [X] T016 `app/bdd-support/` 的 `BackendChannel`：飯店代碼、角色用語與櫃檯 Web 的預設資料
  - Read:
    - `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」的飯店與帳號、時間、常用回覆
    - `app/bdd-support/` 的 `BackendChannel`（T002 搬過來的；建飯店在原檔 219 行附近、角色用語對照在 64 行附近）
  - 只做：建飯店時帶 `code`（員工 App 測試線既有的飯店給固定代碼，維持它原本的行為）；用語對照加「值班主管」＝`duty_manager`、「老闆」＝`owner`（「值班經理」保留給員工 App 測試線）；加 `ensureFrontdeskDefaults()`：預設飯店「雅典娜酒店」（代碼 `athena`、2 至 5 樓的客房）、12 個預設帳號（密碼 `Tms12345`）、住客資訊頁五頁；加「以某位員工身分取得登入回應」的入口。
  - 不做：不寫任何 Cucumber 步驟；不改員工 App 測試線既有方法的行為。

- [X] T017 `app/bdd-support/` 的 `BackendChannel`：各種狀況的單的建立入口
  - Read:
    - `specs/truth/features/frontdesk-web/dsl.md` -> 「單的狀況對照」與 `全館有以下的單：` 那一列
    - `BackendChannel` 既有的建單與推進狀態方法、撥後端時鐘的方法
  - 只做：替「單的狀況對照」的每一種狀況留一個建立入口的方法殼（待派單、待認領、已接四種、進行中、待查房、已完修、已完成、中斷兩種、延後中兩種、查房通過、免查房、已結案兩種、已取消四種、已回答），既有方法做得到的直接接上；加「以櫃檯身分派單／復原／當場回答」「以住客身分送單／收回」的入口。
  - 不做：不解析 Data Table、不決定預設值（那是 T139 `全館有以下的單：` 的事）。後端端點還沒做出來的狀況（已回答）先留殼，等 Phase 4-B06 後由用到它的 Feature phase 接上。

- [X] T018 `app/web/` 的骨架：四條路徑、外框、守門、API client、登入狀態、多語系
  - Read:
    - `ui/ui-plan.md` -> 「前端實作切分建議」（頁面／容器切分、重新整理後保留什麼）、「給 `/api-plan` 的資料需求」
    - `specs/truth/techstack.md` -> `前端` 的櫃檯 Web 各列
    - `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml`、`groups/營運管理/員工帳號與登入.yaml`
  - 只做：目錄 `pages/login/`、`pages/change-password/`、`pages/board/`、`pages/triage/`、`shell/`（外框容器）、`features/{account,detail,answer,dispatch,realtime}/`、`api/`、`stores/`、`i18n/`、`lib/`。四條路徑各接一個空白頁；三條守門的殼（沒登入、還沒改密碼、角色不在三種之內）；`api/client.ts`：以 `fetch` 帶 `Authorization: Bearer`、寫入時產生並帶 `Idempotency-Key`、把契約的 `Error` 轉成型別；`api/types.ts` 照契約寫 `DeskBoard`、`DeskTask`、`DeskTaskDetail` 等型別；`stores/session.ts`（`sessionStorage`）與 `stores/board-view.ts`（泳道、篩選、房號搜尋、側欄）的殼；`i18n/` 四種語言的資源檔殼（繁體中文、英文、日文、越南文）與初始化。
  - 不做：畫面可以是空白；不寫任何規則、文案、看板或面板；登入憑證不放 cookie、`localStorage` 或網址。

- [X] T019 `app/web/` 的純計算落點與 `Vitest` 檔殼
  - Read:
    - `ui/ui-plan.md` -> 「前端實作切分建議」的共用元件或區塊
    - `specs/truth/techstack.md` -> `測試與驗證` 的「櫃檯 Web 單元測試」
  - 只做：在 `lib/` 留出檔案與函式簽章（回傳先丟 `not implemented`）：`board-columns.ts`（單落在哪一欄、未結案張數）、`wait-pill.ts`（等待膠囊的文字與顏色）、`status-card.ts`（狀態標籤與狀態訊息卡）、`timeline-sentence.ts`（歷程句子）、`browser-language.ts`（瀏覽器語言比對）、`server-clock.ts`（用 `serverTime` 校正現在）；每個檔旁邊一支 `*.test.ts` 空殼。
  - 不做：不實作任何一個函式；不寫元件測試。

- [X] T020 `app/web-bdd/` 的骨架：World、兩組瀏覽器設定、時鐘、網路攔截、登入狀態、page object 與 stepdef 落點
  - Read:
    - `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」全部九條
    - `implement-notes.md` -> T005 的結論（觸控拖曳怎麼驅動、瀏覽器時鐘）
    - `app/bdd/src/test/java/com/athena/app/bdd/` 的 World 與 Hooks（照它的分層，不引用它的類別）
  - 只做：`support/WebWorld.java`（每個 scenario 一個瀏覽器 context；可開第二個分頁與第二個 context）；`support/BrowserProfiles.java`（`desktop`、`ipad`；句型寫 `用的是` 時可在 scenario 內換組）；`support/Clocks.java`（後端 `Clock` 與 Playwright 時鐘一起設，預設 2026 年 10/26 週一 14:10）；`support/Network.java`（扣住回應、讓請求斷線、暫停即時訊號與定時重抓的入口）；`support/SessionInjector.java`（把登入回應寫進分頁的 `sessionStorage`）；`pages/` 下每個畫面一個 page object 空殼（登入頁、改密碼畫面、外框、總覽看板、待派單頁、當場回答面板、詳情面板、帳號面板），元素一律用角色與文字找；`steps/{common,account,shell,board,triage,answer,detail,realtime}/` 各三個空殼檔（`*GivenSteps`、`*WhenSteps`、`*ThenSteps`）；`@Before` 呼叫 `BackendChannel.ensureFrontdeskDefaults()` 並清掉上一個案例的單。
  - 不做：不寫任何句型的語意（Phase 3）；不用版面座標找元素；不載入 `com.athena.app.bdd` 的任何類別。

- [X] T021 建立跑單支櫃檯 Web feature 的腳本 `app/web-bdd/run-feature.sh`
  - Read:
    - `app/bdd/run-module.sh`（照它的寫法）
    - 本檔開頭的「測試指令」
  - 只做：腳本接兩個參數（feature 路徑、`desktop` 或 `ipad`）：安裝 `app-bdd-support`、確認 5433 的 Postgres 與 Kafka 在跑、以 `TMS_TEST_CLOCK_ENABLED=true` 啟動 8080 後端（已在跑就沿用）、啟動 `app/web/` 的開發伺服器、跑 `RunFrontdeskWebFeaturesTest`、結束時收掉自己啟動的程序。不給路徑時跑整個 `frontdesk-web/`。
  - 不做：不改 `app/bdd/` 的腳本行為（T002 加的那一步除外）；不跑員工 App 測試線。

## Phase 3: Test Alignment & Implementation

**Goal**: 把本輪 Feature 用到的 DSL 自動化測試對齊最新版 truth；含 truth-delta 有改的句，以及本輪 Feature 用到、尚無 stepdef 的句。不寫產品行為。櫃檯 Web 是全新的測試線，302 句全部是 `[BDD-RED]`；後端有 8 句 `[BDD-ALIGN]`、107 句 `[BDD-RED]`；本輪沒有 `[BDD-REMOVE]`（Scope Note 第 5 條）。

**DSL 參照**:
- 每一句只屬於一個權威 `dsl.md`：同模組 `specs/truth/features/{介面}/{模組}/dsl.md`，或介面根 `specs/truth/features/{介面}/dsl.md`。不得掃其他模組。
- backend 的句分四處：介面根 `specs/truth/features/backend/dsl.md`（T022～T029）、`specs/truth/features/backend/帳號與登入/dsl.md`（T030～T053）、`specs/truth/features/backend/櫃檯看板與派單/dsl.md`（T054～T121）、`specs/truth/features/backend/員工事件串流/dsl.md`（T122～T136）。
- frontdesk-web 的句分八處：介面根 `specs/truth/features/frontdesk-web/dsl.md`（T137～T186）、`帳號與登入/dsl.md`（T187～T271）、`共用外框/dsl.md`（T272～T293）、`總覽看板/dsl.md`（T294～T348）、`待派單/dsl.md`（T349～T369）、`當場回答/dsl.md`（T370～T388）、`單筆詳情/dsl.md`（T389～T419）、`即時更新/dsl.md`（T420～T438）。
- 本輪沒有 DSL row 只搬移權威位置，也沒有改名的句。
- 讀法：用 task title 的句型對到該檔那一列（title 開頭的 `Given:`／`When:`／`Then:` 是它所在的章節，不是句型的一部分），以最右欄的實作語意當作測試程式碼語意：後端是 `StepDef 實作語意`，櫃檯 Web 是 `櫃檯 Web 實作語意（Playwright）`。Given／When 讀 `怎麼做`（櫃檯 Web 是 `使用 fixture`、`Arrange 來源`、`頁面操作`、`等待條件`）、`權威狀態落地`、`回寫`；Then 讀 `必查`（`呈現結果`、`權威狀態`、`再讀確認`）。同一列的 `Gherkin 參數`、`Data Table 參數`、`預設參數` 三欄一併照做。
- 櫃檯 Web 的每一句都另外受 `specs/truth/features/frontdesk-web/dsl.md` 開頭「共通實作約定」九條約束（預設飯店與帳號、時間、常用回覆、瀏覽器設定、找畫面元素、登入狀態、畫面斷言、單的識別、文字比對）。
- `truth-delta.md` 只告訴這句是 ADD / MODIFY / DELETE。語意以 `dsl.md` 那一列為準，不得用 feature 措辭、雛形或舊 stepdef 自行發明。

**Markers**:
- `[BDD-ALIGN]`：`MODIFY`。既有 stepdef 還在，但語意是舊 truth（部門用語還是「值班經理」、沒有老闆、狀態對照沒有待派單、錯誤碼對照少了新的 code、登入失敗只檢查「是 4xx」）。依 `dsl.md` 該列改測試，讓它表達最新版實作語意。
- `[BDD-REMOVE]`：`DELETE`。本輪沒有。
- `[BDD-RED]`：`ADD`，或本輪 Feature 用到、尚無 stepdef 的句。依 `dsl.md` 該列寫出 stepdef。完成時這句可被跑到，失敗只能是 assertion 或產品行為，不能是 undefined step。
- 三個 marker 都只動測試層，不寫產品碼。

**Shared Must Read**:
- `specs/truth/features/backend/dsl.md` -> 狀態用語對照、部門用語對照、本題句型
- `specs/truth/features/backend/帳號與登入/dsl.md`、`櫃檯看板與派單/dsl.md`、`員工事件串流/dsl.md` -> 模組說明、用語對照與本題句型
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」、「單的狀況對照」與本題句型
- `specs/truth/features/frontdesk-web/{帳號與登入、共用外框、總覽看板、待派單、當場回答、單筆詳情、即時更新}/dsl.md` -> 模組說明與本題句型
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web 與後端兩節，有對應 ADD / MODIFY 的句
- 後端 stepdef：`tms/src/test/java/com/athena/tms/bdd/steps/common/`、`steps/account/`、T014 的 `steps/desk/`、T015 的 `steps/staffstream/`、`tms/src/test/java/com/athena/tms/bdd/support/`（`Terms.java`、`StaffFixtures.java`、`TenantFixtures.java`、T014 的 `DeskApi`／`DeskContext`、T015 的 `StaffStreamClient`／`StreamFaults`）
- 櫃檯 Web stepdef：T020 的 `app/web-bdd/src/test/java/com/athena/web/bdd/steps/`、`pages/`、`support/`；`app/bdd-support/` 的 `BackendChannel`（T016、T017）
- 契約只在句型的實作語意點名操作時才讀：`specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml`、`員工事件串流.yaml`、`groups/營運管理/員工帳號與登入.yaml` 與對應的 `components/schemas/`

**Boundary**:
- 一條 DSL 一個 task。
- 只改該句的 stepdef / assertion / 直接依賴的 helper 與 page object。
- 不寫產品碼。產品補上之前實跑會紅，這是預期；完成條件看 dry-run 沒有 undefined、斷言表達 `dsl.md` 的語意。
- 櫃檯 Web 的 stepdef 找元素一律用使用者看得到的角色與文字，定位方式收在 page object 內；page object 需要的可及性名稱（按鈕名稱、欄名、標題）照 feature 的畫面文字，產品在 Phase 4 照著做。不為了測試要求產品加 `data-testid`。
- 共用檔（`CommonThenSteps.java`、`Terms.java`、`BackendChannel`、`WebWorld.java`、page object）改動前先重新讀檔、只做最小追加、寫入後立即編譯（後端 `mvn -q -o test-compile`）；不要重排或重寫別人的段落。
- review 啟動 subagent；本輪所有 Test Scope 不得再有 undefined step（後端與櫃檯 Web 各跑一次 dry-run），失敗只能是 assertion 或產品行為。有 issues 就修正再 review，直到沒有任何問題。通過前不解鎖 Phase 4。

**Parallel Hint**（依檔案衝突分組；組與組平行，組內依序）：
- 後端 B1（`steps/common/CommonGivenSteps.java`、`CommonThenSteps.java`、`support/Terms.java`）：T022～T024。
- 後端 B2（`steps/desk/DeskCommonSteps.java`）：T025～T026、T027～T029。
- 後端 B3（`steps/account/AccountThenSteps.java`）：T030～T034，再 T042～T053。
- 後端 B4（`steps/account/AccountGivenSteps.java`）：T035～T037。後端 B5（`steps/account/AccountWhenSteps.java`）：T038～T041。
- 後端 B6（`steps/desk/DeskGivenSteps.java`）：T054～T068。後端 B7（`steps/desk/DeskWhenSteps.java`）：T069～T080。後端 B8（`steps/desk/DeskThenSteps.java`）：T081～T121。
- 後端 B9（`steps/staffstream/` 三個檔）：T122～T136。
- 櫃檯 Web W1（`steps/common/CommonGivenSteps.java`）：T137～T150，其中 T139 `全館有以下的單：` 最大，先做。W2（`steps/common/CommonWhenSteps.java`）：T151～T157。W3（`steps/common/CommonThenSteps.java`）：T158～T186。
- 櫃檯 Web W4（`steps/account/`，每個檔一組）：`AccountGivenSteps.java` T187～T211；`AccountWhenSteps.java` T212～T231；`AccountThenSteps.java` T232～T271。
- 櫃檯 Web W5（`steps/shell/`，每個檔一組）：`ShellWhenSteps.java` T272～T274；`ShellThenSteps.java` T275～T293。
- 櫃檯 Web W6（`steps/board/`，每個檔一組）：`BoardGivenSteps.java` T294～T298；`BoardWhenSteps.java` T299～T308；`BoardThenSteps.java` T309～T348。
- 櫃檯 Web W7（`steps/triage/`，每個檔一組）：`TriageGivenSteps.java` T349～T352；`TriageWhenSteps.java` T353～T354；`TriageThenSteps.java` T355～T369。
- 櫃檯 Web W8（`steps/answer/`，每個檔一組）：`AnswerGivenSteps.java` T370～T375；`AnswerWhenSteps.java` T376～T380；`AnswerThenSteps.java` T381～T388。
- 櫃檯 Web W9（`steps/detail/`，每個檔一組）：`DetailGivenSteps.java` T389；`DetailThenSteps.java` T390～T419。
- 櫃檯 Web W10（`steps/realtime/`，每個檔一組）：`RealtimeGivenSteps.java` T420～T421；`RealtimeWhenSteps.java` T422～T429；`RealtimeThenSteps.java` T430～T438。
- 每一組各派一個獨立 subagent；T439 等全部回來再啟動 subagent 來 review。

### 後端：介面根 `specs/truth/features/backend/dsl.md`

- [X] T022 [P] [BDD-ALIGN] `Given: 飯店有以下員工：`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/common/CommonGivenSteps.java:52-78`、`support/Terms.java:28-36`、`support/StaffFixtures.java`
  - `部門` 改收「值班主管」（不再收「值班經理」）與「老闆」：櫃檯寫 role `frontdesk`、dept `frontdesk`；值班主管寫 role `duty_manager`、老闆寫 role `owner`，後兩者沒有部門。`Terms.ROLE` 同步改名並加老闆。

- [X] T023 [P] [BDD-ALIGN] `Then: 這次操作被拒絕，錯誤碼為 "{錯誤碼}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/common/CommonThenSteps.java:74-112`
  - 狀態碼對照表加 `idempotency_key_required`→400、`answer_invalid`→422、`task_not_in_triage`→409、`undo_claimed`／`undo_guest_withdrew`／`undo_expired`／`undo_unavailable`→409、`new_password_too_weak`→422。

- [X] T024 [P] [BDD-ALIGN] `Then: "{任務}" 的狀態為 "{狀態}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/support/Terms.java:10-20`、`steps/common/CommonThenSteps.java:146-178`
  - 依「狀態用語對照」加「待派單＝`triage`（`dept` 是 `frontdesk`）」。其餘對照與 `再讀確認` 的做法不動。

- [X] T025 [P] [BDD-RED] `Given: 待派單有以下住客打字的需求：`

- [X] T026 [P] [BDD-RED] `Given: "{櫃檯}" 已把 "{任務}" 派給 "{部門}"`

- [X] T027 [P] [BDD-RED] `When: "{員工}" 讀取櫃檯看板`

- [X] T028 [P] [BDD-RED] `When: "{櫃檯}" 把 "{任務}" 派給 "{部門}"`

- [X] T029 [P] [BDD-RED] `When: "{櫃檯}" 復原 "{任務}" 的派單`

### 後端：`specs/truth/features/backend/帳號與登入/dsl.md`

- [X] T030 [P] [BDD-ALIGN] `Then: 登入成功，取得 "{員工}" 的身分與可用憑證`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/account/AccountThenSteps.java:28-65`
  - 斷言 `LoginResponse` 的形狀：`name`、`role` 相符、`mustChangePassword`=false、帶 `token` 與 `expiresAt`。

- [X] T031 [P] [BDD-ALIGN] `Then: 登入失敗，提示 "{提示}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/account/AccountThenSteps.java:67-89`
  - 由「4xx 即可」收緊成 401 且 `Error.code`=`credentials_invalid`，提示文字相等。

- [X] T032 [P] [BDD-ALIGN] `Then: "{員工}" 的帳號已鎖定 {分鐘} 分鐘`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/account/AccountThenSteps.java:91-136`
  - 由「4xx 即可」收緊成 429 且 `Error.code`=`account_locked`，`context` 帶 `lockedUntil` 與 `remainingMinutes`。

- [X] T033 [P] [BDD-ALIGN] `Then: "{員工}" 的帳號已停用，登入被拒`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/account/AccountThenSteps.java:138-155`
  - 由「4xx 即可」收緊成 403 且 `Error.code`=`account_disabled`。

- [X] T034 [P] [BDD-ALIGN] `Then: "{員工}" 必須先設定新密碼才能繼續`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/account/AccountThenSteps.java:157-184`
  - 確認 200 且 `mustChangePassword`=true；改密前拿這組憑證呼叫 `getDeskBoard` 與既有的員工端點都被拒。

- [X] T035 [P] [BDD-RED] `Given: "{員工}" 已用臨時密碼從櫃檯畫面登入，還沒設定新密碼`

- [X] T036 [P] [BDD-RED] `Given: "{員工}" 已把臨時密碼改成 "{新密碼}"`

- [X] T037 [P] [BDD-RED] `Given: 有一家飯店的識別代碼是 "{代碼}"，名稱是 "{名稱}"`

- [X] T038 [P] [BDD-RED] `When: "{員工}" 從櫃檯畫面用員工編號 "{員工編號}" 密碼 "{密碼}" 登入`

- [X] T039 [P] [BDD-RED] `When: "{員工}" 把新密碼設為 "{新密碼}"`

- [X] T040 [P] [BDD-RED] `When: 沒有登入的人用識別代碼 "{代碼}" 查飯店名稱`

- [X] T041 [P] [BDD-RED] `When: 沒有登入的人用 "{代碼}" 這家飯店的內部編號查飯店名稱`

- [X] T042 [P] [BDD-RED] `Then: 登入回應帶員工編號 "{員工編號}" 與飯店名稱 "{飯店}"`

- [X] T043 [P] [BDD-RED] `Then: 登入失敗，回應與密碼錯誤時完全相同，沒有發出憑證`

- [X] T044 [P] [BDD-RED] `Then: "{員工}" 的連續錯誤次數仍是 {次數} 次，帳號沒有被鎖定`

- [X] T045 [P] [BDD-RED] `Then: 登入成功，標示必須先設定新密碼，角色為 "{角色}"`

- [X] T046 [P] [BDD-RED] `Then: 新密碼設定成功，"{員工}" 不必再登入就拿到可用的憑證`

- [X] T047 [P] [BDD-RED] `Then: "{員工}" 的密碼已是 "{密碼}"，原本的臨時密碼登不進去，之後登入不再要求設定新密碼`

- [X] T048 [P] [BDD-RED] `Then: 這次設定新密碼被拒絕，沒過的規則是 "{規則}"`

- [X] T049 [P] [BDD-RED] `Then: 這次設定新密碼被拒絕，因為新密碼和目前的密碼相同`

- [X] T050 [P] [BDD-RED] `Then: "{員工}" 的密碼仍是原本的臨時密碼，仍然必須先設定新密碼`

- [X] T051 [P] [BDD-RED] `Then: 設定新密碼的回應帶的角色是 "{角色}"`

- [X] T052 [P] [BDD-RED] `Then: 查到的飯店名稱是 "{名稱}"，回應沒有其他資料`

- [X] T053 [P] [BDD-RED] `Then: 查不到這家飯店`

### 後端：`specs/truth/features/backend/櫃檯看板與派單/dsl.md`

- [X] T054 [P] [BDD-RED] `Given: 看板範圍內外有以下的單：`

- [X] T055 [P] [BDD-RED] `Given: "{任務}" 的單在 "{時刻}" 被同仁退回待認領`

- [X] T056 [P] [BDD-RED] `Given: 以下房間現在有住客：`

- [X] T057 [P] [BDD-RED] `Given: 飯店的客房樓層為 {樓層清單}`

- [X] T058 [P] [BDD-RED] `Given: 飯店的分類規則把含 "{關鍵字}" 的文字分給 "{部門}"`

- [X] T059 [P] [BDD-RED] `Given: "{櫃檯}" 已復原 "{任務}" 的派單`

- [X] T060 [P] [BDD-RED] `Given: "{櫃檯}" 已當場回答 "{任務}"，回覆 "{回覆}"`

- [X] T061 [P] [BDD-RED] `Given: "{房號}" 的住客已自己收回這張需求`

- [X] T062 [P] [BDD-RED] `Given: "{員工}" 已認領 "{任務}"`

- [X] T063 [P] [BDD-RED] `Given: "{員工}" 已開始處理 "{任務}"`

- [X] T064 [P] [BDD-RED] `Given: "{任務}" 被 "{員工}" 認領後因請假退回待認領`

- [X] T065 [P] [BDD-RED] `Given: 派出後過了 {秒數} 秒`

- [X] T066 [P] [BDD-RED] `Given: "{員工}" 已在 "{任務}" 勾選 {項數} 項清潔項目`

- [X] T067 [P] [BDD-RED] `Given: 飯店的住客資訊頁依序如下：`

- [X] T068 [P] [BDD-RED] `Given: 飯店沒有設定住客資訊頁`

- [X] T069 [P] [BDD-RED] `When: "{櫃檯}" 以拖曳把 "{任務}" 派給 "{部門}"`

- [X] T070 [P] [BDD-RED] `When: "{櫃檯}" 不帶防重複鍵把 "{任務}" 派給 "{部門}"`

- [X] T071 [P] [BDD-RED] `When: "{櫃檯}" 用同一把鍵把 "{任務}" 派給 "{部門}" 重送一次`

- [X] T072 [P] [BDD-RED] `When: "{櫃檯}" 不帶防重複鍵復原 "{任務}" 的派單`

- [X] T073 [P] [BDD-RED] `When: "{員工}" 當場回答 "{任務}"，回覆 "{回覆}"`

- [X] T074 [P] [BDD-RED] `When: "{櫃檯}" 以 {字數} 個字的回覆當場回答 "{任務}"`

- [X] T075 [P] [BDD-RED] `When: "{櫃檯}" 以只有空白的回覆當場回答 "{任務}"`

- [X] T076 [P] [BDD-RED] `When: "{櫃檯}" 不帶防重複鍵當場回答 "{任務}"，回覆 "{回覆}"`

- [X] T077 [P] [BDD-RED] `When: "{房號}" 的住客打字送出 "{內容}"`

- [X] T078 [P] [BDD-RED] `When: "{員工}" 讀取 "{任務}" 的櫃檯詳情`

- [X] T079 [P] [BDD-RED] `When: "{員工}" 讀取一張不存在的單的櫃檯詳情`

- [X] T080 [P] [BDD-RED] `When: "{員工}" 讀取常用回覆`

- [X] T081 [P] [BDD-RED] `Then: 櫃檯看板剛好有這些房號的單：{房號清單}`

- [X] T082 [P] [BDD-RED] `Then: 櫃檯看板上 "{任務}" 的資料如下：`

- [X] T083 [P] [BDD-RED] `Then: 櫃檯看板上 "{任務}" 的等待從 "{時刻}" 起算`

- [X] T084 [P] [BDD-RED] `Then: 櫃檯看板上各單的到手方式如下：`

- [X] T085 [P] [BDD-RED] `Then: 櫃檯看板上 "{任務}" 沒有任何急件標記`

- [X] T086 [P] [BDD-RED] `Then: 櫃檯看板上住客打字的需求如下：`

- [X] T087 [P] [BDD-RED] `Then: 櫃檯看板上各單的住客如下：`

- [X] T088 [P] [BDD-RED] `Then: 櫃檯看板帶的後端時間是今天 "{時刻}"，今天的日期與後端時鐘的日期相同`

- [X] T089 [P] [BDD-RED] `Then: 櫃檯看板帶的樓層依序為 {樓層清單}`

- [X] T090 [P] [BDD-RED] `Then: 櫃檯看板帶的本人今日張數是已派單 {派單張數} 張、當場回答 {回答張數} 張`

- [X] T091 [P] [BDD-RED] `Then: "{任務}" 仍是待派單，歷程沒有任何派單紀錄`

- [X] T092 [P] [BDD-RED] `Then: "{任務}" 在 "{部門}" 的待認領，沒有負責人`

- [X] T093 [P] [BDD-RED] `Then: 全館沒有因為這次派單多出任何一張單`

- [X] T094 [P] [BDD-RED] `Then: "{任務}" 的歷程最新一筆是 "{操作者}" 以 "{方式}" 派給 "{部門}"`

- [X] T095 [P] [BDD-RED] `Then: "{任務}" 那筆派單記的操作者角色是 "{角色}"`

- [X] T096 [P] [BDD-RED] `Then: "{任務}" 只被派出一次，派單的人是 "{操作者}"`

- [X] T097 [P] [BDD-RED] `Then: 重送拿到與第一次相同的成功回應`

- [X] T098 [P] [BDD-RED] `Then: 這次操作被拒絕，因為 "{任務}" 已被 "{操作者}" 派給 "{部門}"`

- [X] T099 [P] [BDD-RED] `Then: 這次操作被拒絕，因為 "{任務}" 已被 "{操作者}" 當場回答`

- [X] T100 [P] [BDD-RED] `Then: 這次操作被拒絕，因為 "{任務}" 已被住客收回`

- [X] T101 [P] [BDD-RED] `Then: 這次操作被拒絕，因為 "{任務}" 已經不是待派單`

- [X] T102 [P] [BDD-RED] `Then: "{任務}" 回到待派單，還沒分部門`

- [X] T103 [P] [BDD-RED] `Then: "{任務}" 給人看的歷程沒有派單這一步`

- [X] T104 [P] [BDD-RED] `Then: "{任務}" 的事件紀錄保留派給 "{部門}" 與復原各一筆，兩筆相鄰`

- [X] T105 [P] [BDD-RED] `Then: 這次復原被拒絕，因為已經超過可以復原的時間`

- [X] T106 [P] [BDD-RED] `Then: 這次復原被拒絕，因為 "{員工}" 已經認領，這張單留在 "{部門}"`

- [X] T107 [P] [BDD-RED] `Then: 這次復原被拒絕，因為住客已經收回這張需求`

- [X] T108 [P] [BDD-RED] `Then: 這次復原被拒絕，因為這張單已經不是剛派出的樣子`

- [X] T109 [P] [BDD-RED] `Then: "{任務}" 已由 "{櫃檯}" 當場回答結案，回覆為 "{回覆}"`

- [X] T110 [P] [BDD-RED] `Then: "{任務}" 的回覆長度是 {字數} 個字`

- [X] T111 [P] [BDD-RED] `Then: "{任務}" 的住客原文仍是 "{原文}"`

- [X] T112 [P] [BDD-RED] `Then: "{任務}" 仍未分部門，沒有負責人，不算任何部門做掉的單`

- [X] T113 [P] [BDD-RED] `Then: "{任務}" 的歷程最新一筆是 "{操作者}" 的當場回答，帶回覆 "{回覆}"`

- [X] T114 [P] [BDD-RED] `Then: "{房號}" 的住客在進度頁讀到回覆 "{回覆}"`

- [X] T115 [P] [BDD-RED] `Then: 櫃檯詳情的資料如下：`

- [X] T116 [P] [BDD-RED] `Then: 櫃檯詳情的歷程依序為：`

- [X] T117 [P] [BDD-RED] `Then: "{任務}" 的歷程比這一步之前多一筆 "{事件}"，原有各筆都沒有改動`

- [X] T118 [P] [BDD-RED] `Then: 櫃檯詳情的歷程沒有 "{事件}" 這種事件`

- [X] T119 [P] [BDD-RED] `Then: 常用回覆的標題依序為 {標題清單}`

- [X] T120 [P] [BDD-RED] `Then: 常用回覆 "{標題}" 的全文是 "{全文}"`

- [X] T121 [P] [BDD-RED] `Then: 常用回覆是空的`

### 後端：`specs/truth/features/backend/員工事件串流/dsl.md`

- [X] T122 [P] [BDD-RED] `Given: "{員工}" 已連上員工事件串流`

- [X] T123 [P] [BDD-RED] `Given: 後端目前 "{狀態}"`

- [X] T124 [P] [BDD-RED] `When: "{員工}" 連上員工事件串流`

- [X] T125 [P] [BDD-RED] `When: 後端處理事件落後超過 10 秒`

- [X] T126 [P] [BDD-RED] `When: 後端連不上事件來源`

- [X] T127 [P] [BDD-RED] `When: 後端處理事件回到正常`

- [X] T128 [P] [BDD-RED] `When: 串流過了 15 秒`

- [X] T129 [P] [BDD-RED] `Then: "{員工}" 的串流收到 "{任務}" 的 "{訊號}" 訊號`

- [X] T130 [P] [BDD-RED] `Then: "{員工}" 的串流沒有收到 "{任務}" 的任何訊號`

- [X] T131 [P] [BDD-RED] `Then: "{員工}" 的串流依序收到連線起始與重抓通知，建議重抓的是 "{資料}"`

- [X] T132 [P] [BDD-RED] `Then: 這次連線被拒絕`

- [X] T133 [P] [BDD-RED] `Then: "{員工}" 的串流收到降級訊號，原因是 "{原因}"`

- [X] T134 [P] [BDD-RED] `Then: "{員工}" 的串流收到恢復訊號`

- [X] T135 [P] [BDD-RED] `Then: "{員工}" 的串流在重抓通知之後收到降級訊號`

- [X] T136 [P] [BDD-RED] `Then: "{員工}" 的串流收到一則心跳，標示後端目前 "{狀態}"`

### 櫃檯 Web：介面根 `specs/truth/features/frontdesk-web/dsl.md`

- [X] T137 [P] [BDD-RED] `Given: 今天是 "{日期}"`

- [X] T138 [P] [BDD-RED] `Given: 現在時間是 "{時刻}"`

- [X] T139 [P] [BDD-RED] `Given: 全館有以下的單：`

- [X] T140 [P] [BDD-RED] `Given: "{員工}" 已登入櫃檯畫面，停在 "{頁}"`

- [X] T141 [P] [BDD-RED] `Given: "{員工}" 已自己把側欄打開`

- [X] T142 [P] [BDD-RED] `Given: "{員工}" 已從側欄換到待派單頁`

- [X] T143 [P] [BDD-RED] `Given: "{員工}" 用的是 "{裝置}"`

- [X] T144 [P] [BDD-RED] `Given: "{員工}" 開著 "{房號}" 的詳情面板`

- [X] T145 [P] [BDD-RED] `Given: "{員工}" 的畫面還沒跟上，"{同事}" 已在另一台櫃檯把 "{房號}" 派給 "{部門}"`

- [X] T146 [P] [BDD-RED] `Given: "{員工}" 的畫面還沒跟上，"{同事}" 已在另一台櫃檯當場回答 "{房號}"`

- [X] T147 [P] [BDD-RED] `Given: "{員工}" 的畫面還沒跟上，"{房號}" 的住客已自己收回需求`

- [X] T148 [P] [BDD-RED] `Given: 接下來的送出會因為網路中斷而失敗`

- [X] T149 [P] [BDD-RED] `Given: 看板資料的回應被延後`

- [X] T150 [P] [BDD-RED] `Given: 看板資料的讀取會失敗`

- [X] T151 [P] [BDD-RED] `When: "{員工}" 登入後打開 "{頁}"`

- [X] T152 [P] [BDD-RED] `When: "{員工}" 登入後打開 "{頁}"，資料還沒回來`

- [X] T153 [P] [BDD-RED] `When: "{員工}" 按 "{按鈕}"`

- [X] T154 [P] [BDD-RED] `When: "{員工}" 打開 "{房號}" 的詳情面板`

- [X] T155 [P] [BDD-RED] `When: "{員工}" 從側欄換到 "{頁}"`

- [X] T156 [P] [BDD-RED] `When: "{員工}" 在待派單頁按 "{房號}" 這一列的 "{動作}"`

- [X] T157 [P] [BDD-RED] `When: "{員工}" 在手機上{動作} "{房號}"`

- [X] T158 [P] [BDD-RED] `Then: 畫面上看得到以下文字：`

- [X] T159 [P] [BDD-RED] `Then: 畫面上沒有以下文字：`

- [X] T160 [P] [BDD-RED] `Then: 畫面上看得到 "{文字}"`

- [X] T161 [P] [BDD-RED] `Then: 畫面上沒有 "{文字}"`

- [X] T162 [P] [BDD-RED] `Then: 按鈕 "{按鈕}" 可以按`

- [X] T163 [P] [BDD-RED] `Then: 按鈕 "{按鈕}" 不能按`

- [X] T164 [P] [BDD-RED] `Then: 畫面出現對話框，標題 "{標題}"、內文 "{內文}"、按鈕只有 "{按鈕}"`

- [X] T165 [P] [BDD-RED] `Then: 畫面上沒有任何對話框`

- [X] T166 [P] [BDD-RED] `Then: 畫面底部出現浮條 "{文字}" 與 "復原"`

- [X] T167 [P] [BDD-RED] `Then: 畫面上沒有派單浮條`

- [X] T168 [P] [BDD-RED] `Then: 後端 "{房號}" 的單在 "{部門}" 待認領`

- [X] T169 [P] [BDD-RED] `Then: 後端 "{房號}" 的單仍是待派單，歷程沒有多任何一筆`

- [X] T170 [P] [BDD-RED] `Then: 後端 "{房號}" 的派單紀錄是 "{員工}" 以 "{方式}" 派給 "{部門}"`

- [X] T171 [P] [BDD-RED] `Then: 後端 "{房號}" 的單已取消，原因是住客收回`

- [X] T172 [P] [BDD-RED] `Then: 後端 "{房號}" 的單已由 "{員工}" 當場回答，沒有進任何部門的待認領`

- [X] T173 [P] [BDD-RED] `Then: 後端沒有任何資料被改動`

- [X] T174 [P] [BDD-RED] `Then: 側欄 "待派單" 的徽章是 {數字}`

- [X] T175 [P] [BDD-RED] `Then: 側欄是 "{狀態}" 的`

- [X] T176 [P] [BDD-RED] `Then: 側欄的帳號卡顯示頭像 "{頭像}"、姓名 "{姓名}" 與 "{第二行}"`

- [X] T177 [P] [BDD-RED] `Then: "{房號}" 的卡片在 "{欄}" 欄`

- [X] T178 [P] [BDD-RED] `Then: "{房號}" 的卡片副標是 "{副標}"`

- [X] T179 [P] [BDD-RED] `Then: "{房號}" 的詳情面板是打開的`

- [X] T180 [P] [BDD-RED] `Then: 詳情面板是關的`

- [X] T181 [P] [BDD-RED] `Then: 當場回答面板是打開的，標題是 "{標題}"`

- [X] T182 [P] [BDD-RED] `Then: "{員工}" 仍是登入狀態，畫面是 "{頁}"`

- [X] T183 [P] [BDD-RED] `Then: 這段期間 "{員工}" 沒有重新整理網頁`

- [X] T184 [P] [BDD-RED] `Then: 待派單頁的清單沒有 "{房號}"`

- [X] T185 [P] [BDD-RED] `Then: 待派單頁的清單由上到下是 {房號清單}`

- [X] T186 [P] [BDD-RED] `Then: 頁副標是 "{文字}"`

### 櫃檯 Web：`specs/truth/features/frontdesk-web/帳號與登入/dsl.md`

- [X] T187 [P] [BDD-RED] `Given: 登入頁已經打開`

- [X] T188 [P] [BDD-RED] `Given: 登入頁已經打開，介面語言是 "{語言}"`

- [X] T189 [P] [BDD-RED] `Given: 登入頁已經打開，員工編號欄已輸入 "{員工編號}"`

- [X] T190 [P] [BDD-RED] `Given: 登入頁已經打開，密碼欄已輸入 "{密碼}"`

- [X] T191 [P] [BDD-RED] `Given: 登入頁已經打開，密碼欄已輸入 "{密碼}" 並切成明文顯示`

- [X] T192 [P] [BDD-RED] `Given: 登入頁已經打開，忘記密碼的提示開著`

- [X] T193 [P] [BDD-RED] `Given: "{員工}" 的帳號已連續輸錯密碼 {次數} 次，被鎖定 {分鐘} 分鐘`

- [X] T194 [P] [BDD-RED] `Given: "{員工}" 的帳號已被停用`

- [X] T195 [P] [BDD-RED] `Given: "{員工}" 的密碼是主管給的臨時密碼 "{密碼}"`

- [X] T196 [P] [BDD-RED] `Given: "{員工}" 已用臨時密碼登入，停在設定新密碼畫面`

- [X] T197 [P] [BDD-RED] `Given: "{員工}" 已經把臨時密碼改成 "{密碼}"`

- [X] T198 [P] [BDD-RED] `Given: "{員工}" 已登入櫃檯畫面，帳號面板開著`

- [X] T199 [P] [BDD-RED] `Given: "{員工}" 已登入櫃檯畫面，登出確認提示開著`

- [X] T200 [P] [BDD-RED] `Given: "{員工}" 已登入櫃檯畫面，介面語言是 "{語言}"，登出確認提示開著`

- [X] T201 [P] [BDD-RED] `Given: "{員工}" 已登入櫃檯畫面，介面語言是 "{語言}"，停在 "{頁}"`

- [X] T202 [P] [BDD-RED] `Given: "{員工}" 已登入櫃檯畫面，語言頁開著`

- [X] T203 [P] [BDD-RED] `Given: "{員工}" 已登入櫃檯畫面，語言頁開著，已經選了 "{選項}"`

- [X] T204 [P] [BDD-RED] `Given: "{員工}" 已登入櫃檯畫面，"{面板}" 開著`

- [X] T205 [P] [BDD-RED] `Given: "{員工}" 已登入櫃檯畫面，另開的新分頁停在登入頁`

- [X] T206 [P] [BDD-RED] `Given: 原本的分頁是 "{員工}" 登入、新分頁是 "{同事}" 登入`

- [X] T207 [P] [BDD-RED] `Given: "{員工}" 已在總覽看板點了 "{數量卡}" 數量卡、搜尋房號 "{房號}"、把泳道切到 "{泳道}"`

- [X] T208 [P] [BDD-RED] `Given: 這台裝置還沒有任何人選過介面語言，瀏覽器的語言是 "{瀏覽器的語言}"`

- [X] T209 [P] [BDD-RED] `Given: 這台裝置上各人選過的介面語言如下：`

- [X] T210 [P] [BDD-RED] `Given: "{員工}" 在櫃檯原本那台裝置上選過 "{選項}"`

- [X] T211 [P] [BDD-RED] `Given: 上一次的瀏覽器已經關掉，登入頁重新打開`

- [X] T212 [P] [BDD-RED] `When: 有人打開登入頁`

- [X] T213 [P] [BDD-RED] `When: 有人用飯店識別代碼 "{代碼}" 的網址打開登入頁`

- [X] T214 [P] [BDD-RED] `When: "{員工}" 在登入頁的 "{欄位}" 輸入 "{內容}"`

- [X] T215 [P] [BDD-RED] `When: "{員工}" 在登入頁的 "{欄位}" 輸入 {字數} 個字`

- [X] T216 [P] [BDD-RED] `When: "{員工}" 在登入頁輸入員工編號 "{員工編號}" 與密碼 "{密碼}" 後按「登入」`

- [X] T217 [P] [BDD-RED] `When: "{員工}" 在新分頁輸入員工編號 "{員工編號}" 與密碼 "{密碼}" 後按「登入」`

- [X] T218 [P] [BDD-RED] `When: "{員工}" 打開登入頁右上的語言選單`

- [X] T219 [P] [BDD-RED] `When: "{員工}" 在登入頁的語言選單選 "{選項}"`

- [X] T220 [P] [BDD-RED] `When: "{員工}" 直接打開 "{頁}" 的網址`

- [X] T221 [P] [BDD-RED] `When: "{員工}" 輸入新密碼 "{新密碼}"、確認密碼 "{確認密碼}"`

- [X] T222 [P] [BDD-RED] `When: "{員工}" 把新密碼設成 "{新密碼}" 並按「更新密碼」`

- [X] T223 [P] [BDD-RED] `When: "{員工}" 重新整理網頁`

- [X] T224 [P] [BDD-RED] `When: "{員工}" 點側欄的帳號卡`

- [X] T225 [P] [BDD-RED] `When: "{員工}" 點身分卡`

- [X] T226 [P] [BDD-RED] `When: "{員工}" 在登出確認提示按「登出」`

- [X] T227 [P] [BDD-RED] `When: "{員工}" 在語言頁選 "{選項}"`

- [X] T228 [P] [BDD-RED] `When: "{員工}" 從語言頁回到帳號面板`

- [X] T229 [P] [BDD-RED] `When: "{員工}" 沒有登出就{動作}，之後有人再打開櫃檯畫面`

- [X] T230 [P] [BDD-RED] `When: "{員工}" 另開一個新分頁打開櫃檯畫面`

- [X] T231 [P] [BDD-RED] `When: "{員工}" 在原本的分頁登出`

- [X] T232 [P] [BDD-RED] `Then: 登入頁的員工編號欄與密碼欄都是空的`

- [X] T233 [P] [BDD-RED] `Then: 登入頁的 "{欄位}" 欄顯示 "{內容}"`

- [X] T234 [P] [BDD-RED] `Then: 登入頁的 "{欄位}" 欄裡只有 {上限} 個字`

- [X] T235 [P] [BDD-RED] `Then: 密碼欄以明文顯示 "{密碼}"，切換鈕變成 "{按鈕}"`

- [X] T236 [P] [BDD-RED] `Then: 密碼欄的內容被遮住，切換鈕變成 "{按鈕}"`

- [X] T237 [P] [BDD-RED] `Then: "{員工}" 登入成功，畫面是 "{頁}"`

- [X] T238 [P] [BDD-RED] `Then: 這個分頁沒有登入，畫面是 "{頁}"`

- [X] T239 [P] [BDD-RED] `Then: 畫面是 "{頁}"`

- [X] T240 [P] [BDD-RED] `Then: 畫面上看不到任何一張單`

- [X] T241 [P] [BDD-RED] `Then: 登入頁顯示紅字 "{文字}"，密碼欄被清空，員工編號欄保留`

- [X] T242 [P] [BDD-RED] `Then: 登入頁除了 "{文字}" 沒有其他提示`

- [X] T243 [P] [BDD-RED] `Then: 登入頁顯示提示卡，標題 "{標題}"、內文 "{內文}"`

- [X] T244 [P] [BDD-RED] `Then: 登入頁沒有飯店名稱那一行，其餘文字與欄位照常`

- [X] T245 [P] [BDD-RED] `Then: 設定新密碼畫面只有 "{欄一}" 與 "{欄二}" 兩個輸入欄`

- [X] T246 [P] [BDD-RED] `Then: 設定新密碼畫面的兩個輸入欄都是空的`

- [X] T247 [P] [BDD-RED] `Then: 規則 "{規則}" 沒有打勾`

- [X] T248 [P] [BDD-RED] `Then: 四條規則都打勾`

- [X] T249 [P] [BDD-RED] `Then: 後端 "{員工}" 的密碼仍是臨時密碼`

- [X] T250 [P] [BDD-RED] `Then: 後端 "{員工}" 的密碼已是 "{密碼}"，臨時密碼不能再用，也不再需要設定新密碼`

- [X] T251 [P] [BDD-RED] `Then: 標題 "{標題}" 的面板打開，裡面只有身分卡、"{項目一}" 與 "{項目二}" 三項`

- [X] T252 [P] [BDD-RED] `Then: 身分卡顯示頭像 "{頭像}"、姓名 "{姓名}"、"{第二行}" 與 "{飯店}"`

- [X] T253 [P] [BDD-RED] `Then: 帳號面板維持原樣，沒有開任何頁面或對話框`

- [X] T254 [P] [BDD-RED] `Then: 帳號面板是關的`

- [X] T255 [P] [BDD-RED] `Then: 畫面出現確認對話框，標題 "{標題}"、內文 "{內文}"、按鈕是 "{按鈕一}" 與 "{按鈕二}"`

- [X] T256 [P] [BDD-RED] `Then: 登入頁上沒有 "{員工}" 的姓名或員工編號，也沒有可以點選或切換的帳號`

- [X] T257 [P] [BDD-RED] `Then: "{員工}" 仍是登入狀態，沒有任何面板或對話框開著`

- [X] T258 [P] [BDD-RED] `Then: 總覽看板的數量卡篩選是 "{數量卡}"、搜尋房號是 "{房號}"、泳道是 "{泳道}"`

- [X] T259 [P] [BDD-RED] `Then: 新分頁是登入頁，員工編號欄與密碼欄都是空的`

- [X] T260 [P] [BDD-RED] `Then: 原本的分頁仍是 "{員工}" 的登入狀態`

- [X] T261 [P] [BDD-RED] `Then: 新分頁是 "{員工}" 的登入狀態`

- [X] T262 [P] [BDD-RED] `Then: 原本的分頁回到登入頁`

- [X] T263 [P] [BDD-RED] `Then: 登入頁的介面語言是 "{語言}"，語言選單顯示 "{選項}"`

- [X] T264 [P] [BDD-RED] `Then: 語言選單依序列出 "{一}"、"{二}"、"{三}"、"{四}"，打勾的是 "{選項}"`

- [X] T265 [P] [BDD-RED] `Then: 登入後的介面語言是 "{語言}"`

- [X] T266 [P] [BDD-RED] `Then: 新分頁登入後的介面語言是 "{語言}"`

- [X] T267 [P] [BDD-RED] `Then: 這台裝置記下 "{員工}" 的介面語言是 "{語言}"`

- [X] T268 [P] [BDD-RED] `Then: 帳號面板 "{列名}" 那一列的右邊寫著 "{文字}"`

- [X] T269 [P] [BDD-RED] `Then: 語言頁的段落標題是 "{標題}"，四個選項依序如下，打勾的是 "{選項}"：`

- [X] T270 [P] [BDD-RED] `Then: 語言頁打勾的是 "{選項}"`

- [X] T271 [P] [BDD-RED] `Then: 詳情面板的欄名與按鈕是 "{語言}"`

### 櫃檯 Web：`specs/truth/features/frontdesk-web/共用外框/dsl.md`

- [X] T272 [P] [BDD-RED] `When: "{員工}" 登入後打開 "{頁}"，側欄是 "{側欄}" 的`

- [X] T273 [P] [BDD-RED] `When: "{員工}" 把看板捲到最右邊`

- [X] T274 [P] [BDD-RED] `When: "{員工}" 把視窗調成 {寬} × {高}`

- [X] T275 [P] [BDD-RED] `Then: 側欄由上到下是標題 "{標題}"、副標 "{副標}"、功能 "{一}"、"{二}"、"{三}"、"{四}" 與帳號卡`

- [X] T276 [P] [BDD-RED] `Then: 側欄 "{功能}" 沒有徽章`

- [X] T277 [P] [BDD-RED] `Then: 側欄上{有沒有} "{項目}"`

- [X] T278 [P] [BDD-RED] `Then: 側欄的 "{項目}" 底下沒有展開任何項目`

- [X] T279 [P] [BDD-RED] `Then: 畫面是 "{頁}"，頁標題是 "{標題}"`

- [X] T280 [P] [BDD-RED] `Then: 工具列沒有 "{缺少的}"，有 "{有的}"`

- [X] T281 [P] [BDD-RED] `Then: 工具列有 "{一}" 與 "{二}"`

- [X] T282 [P] [BDD-RED] `Then: 畫面仍是 "{頁}"，沒有開任何頁面、面板或對話框`

- [X] T283 [P] [BDD-RED] `Then: 看板的可用寬度比側欄打開時多出側欄的寬度`

- [X] T284 [P] [BDD-RED] `Then: 這個畫面完整顯示在視窗內，沒有整頁的捲軸，也不需要縮放`

- [X] T285 [P] [BDD-RED] `Then: 看板右緣有按鈕 "{按鈕}"`

- [X] T286 [P] [BDD-RED] `Then: 看板右緣沒有「還有 N 個狀態」的按鈕`

- [X] T287 [P] [BDD-RED] `Then: 看板往右捲了一欄，看板右緣有按鈕 "{按鈕}"`

- [X] T288 [P] [BDD-RED] `Then: 看得到 "{欄}" 欄`

- [X] T289 [P] [BDD-RED] `Then: {欄數} 個欄都看得到，每一欄都比視窗調整前寬`

- [X] T290 [P] [BDD-RED] `Then: 看板貼齊內容區的左右兩側，沒有置中留白`

- [X] T291 [P] [BDD-RED] `Then: 畫面出現整頁的捲軸，捲動後看得到其餘的內容`

- [X] T292 [P] [BDD-RED] `Then: 側欄、工具列、數量卡與看板的排法與視窗調整前相同`

- [X] T293 [P] [BDD-RED] `Then: 這個畫面上每一項內容與每一顆可以按的東西，不用游標移上去就看得到、按得到`

### 櫃檯 Web：`specs/truth/features/frontdesk-web/總覽看板/dsl.md`

- [X] T294 [P] [BDD-RED] `Given: "{員工}" 已在總覽看板點了 "{數量卡}" 數量卡`

- [X] T295 [P] [BDD-RED] `Given: "{員工}" 已在總覽看板點了 "{數量卡}" 數量卡、搜尋房號 "{房號}"`

- [X] T296 [P] [BDD-RED] `Given: "{員工}" 已把泳道切到 "{泳道}"`

- [X] T297 [P] [BDD-RED] `Given: "{員工}" 剛在 "{頁}" 把 "{房號}" 拖給 "{部門}"，派單浮條還在`

- [X] T298 [P] [BDD-RED] `Given: "{員工}" 打開總覽看板時讀取失敗，之後網路已經恢復`

- [X] T299 [P] [BDD-RED] `When: "{員工}" 點 "{數量卡}" 數量卡`

- [X] T300 [P] [BDD-RED] `When: "{員工}" 在 "{輸入框}" 輸入 "{內容}"`

- [X] T301 [P] [BDD-RED] `When: "{員工}" 點控制列的篩選膠囊`

- [X] T302 [P] [BDD-RED] `When: "{員工}" 把泳道切到 "{泳道}"`

- [X] T303 [P] [BDD-RED] `When: "{員工}" 按住 "{房號}" 的卡片開始拖`

- [X] T304 [P] [BDD-RED] `When: "{員工}" 拖著 "{房號}" 的卡片滑到 "{拖放區}" 上，還沒放開`

- [X] T305 [P] [BDD-RED] `When: "{員工}" 把 "{房號}" 的卡片拖到 "{拖放區}" 放開`

- [X] T306 [P] [BDD-RED] `When: "{員工}" 拖著 "{房號}" 的卡片在拖放區以外的地方放開`

- [X] T307 [P] [BDD-RED] `When: "{員工}" 試著拖 "{房號}" 的卡片`

- [X] T308 [P] [BDD-RED] `When: 過了 {秒數} 秒，"{員工}" 沒有按任何東西`

- [X] T309 [P] [BDD-RED] `Then: 總覽看板由上到下依序是 {張數} 張數量卡、看板控制列與看板本體`

- [X] T310 [P] [BDD-RED] `Then: 看板由左到右的欄是 "{一}"、"{二}"、"{三}"、"{四}"、"{五}"、"{六}"、"{七}"、"{八}"`

- [X] T311 [P] [BDD-RED] `Then: 看板各欄的單如下：`

- [X] T312 [P] [BDD-RED] `Then: "{欄}" 欄沒有任何卡片`

- [X] T313 [P] [BDD-RED] `Then: 看板上沒有 "{一}"、"{二}"、"{三}" 的卡片`

- [X] T314 [P] [BDD-RED] `Then: 各欄的空欄文字如下：`

- [X] T315 [P] [BDD-RED] `Then: 各欄由上到下的順序如下：`

- [X] T316 [P] [BDD-RED] `Then: 四張數量卡依序如下：`

- [X] T317 [P] [BDD-RED] `Then: "{數量卡}" 數量卡的說明是 "{說明}"，數字是 "{數字}"`

- [X] T318 [P] [BDD-RED] `Then: "{數量卡}" 數量卡的數字顏色是 "{顏色}"`

- [X] T319 [P] [BDD-RED] `Then: 四張數量卡上都沒有 "{文字}"`

- [X] T320 [P] [BDD-RED] `Then: 看板 {欄數} 個欄都在，控制列沒有篩選膠囊`

- [X] T321 [P] [BDD-RED] `Then: {欄數} 個欄頭都有色點、欄名與數量`

- [X] T322 [P] [BDD-RED] `Then: 欄頭的數量與警示如下：`

- [X] T323 [P] [BDD-RED] `Then: 各張卡片的部門、類型、來源與 VIP 標如下：`

- [X] T324 [P] [BDD-RED] `Then: 各張卡片的標題與副標如下：`

- [X] T325 [P] [BDD-RED] `Then: "{房號}" 的卡片顯示 "{文字}" 與進度條`

- [X] T326 [P] [BDD-RED] `Then: "{房號}" 的卡片沒有 SOP 進度`

- [X] T327 [P] [BDD-RED] `Then: 各張卡片的負責人頭像如下：`

- [X] T328 [P] [BDD-RED] `Then: 各張卡片的等待膠囊如下：`

- [X] T329 [P] [BDD-RED] `Then: "{欄}" 欄的卡片上都沒有 "{文字}"`

- [X] T330 [P] [BDD-RED] `Then: "{房號}" 的卡片上沒有急件標記`

- [X] T331 [P] [BDD-RED] `Then: 看板只剩 {欄清單} 這幾欄`

- [X] T332 [P] [BDD-RED] `Then: 控制列只有一顆篩選膠囊，文字是 "{文字}"`

- [X] T333 [P] [BDD-RED] `Then: 看板上只有這些房號的卡片：{房號清單}`

- [X] T334 [P] [BDD-RED] `Then: 看板上只有這些標題的卡片：{標題清單}`

- [X] T335 [P] [BDD-RED] `Then: "{輸入框}" 裡的字是 "{內容}"`

- [X] T336 [P] [BDD-RED] `Then: "{輸入框}" 是空的`

- [X] T337 [P] [BDD-RED] `Then: 頁副標的未結案張數是 {張數}`

- [X] T338 [P] [BDD-RED] `Then: 看板的泳道由上到下是 {泳道清單}`

- [X] T339 [P] [BDD-RED] `Then: 各泳道的副標如下：`

- [X] T340 [P] [BDD-RED] `Then: "{泳道}" 這一列的 "{欄}" 欄有 "{標題}"`

- [X] T341 [P] [BDD-RED] `Then: 看板沒有泳道標題`

- [X] T342 [P] [BDD-RED] `Then: 看板控制列的提示文字是 "{文字}"`

- [X] T343 [P] [BDD-RED] `Then: "{欄}" 欄出現以下拖放區：`

- [X] T344 [P] [BDD-RED] `Then: 拖放區 "{拖放區}" 的說明是 "{說明}"`

- [X] T345 [P] [BDD-RED] `Then: 看板上沒有任何拖放區`

- [X] T346 [P] [BDD-RED] `Then: "{房號}" 的卡片沒有被拖起來，看板上沒有任何拖放區`

- [X] T347 [P] [BDD-RED] `Then: 各泳道 "{欄}" 欄的拖放區如下：`

- [X] T348 [P] [BDD-RED] `Then: 每一欄是灰色的骨架卡片，數量卡的數字位置是骨架`

### 櫃檯 Web：`specs/truth/features/frontdesk-web/待派單/dsl.md`

- [X] T349 [P] [BDD-RED] `Given: "{員工}" 剛在 "{頁}" 把 "{房號}" 派給 "{部門}"，派單浮條還在`

- [X] T350 [P] [BDD-RED] `Given: "{同仁}" 已在手機上認領 "{房號}"，"{員工}" 的畫面還沒跟上`

- [X] T351 [P] [BDD-RED] `Given: "{員工}" 今天已派出 {派單張數} 張單、當場回答 {回答張數} 張單，待派單現在是空的`

- [X] T352 [P] [BDD-RED] `Given: "{員工}" 今天另有 {次數} 次派單被她自己復原，那張單後來由 "{同事}" 派走`

- [X] T353 [P] [BDD-RED] `When: 派單浮條出現後過了 {秒數} 秒`

- [X] T354 [P] [BDD-RED] `When: 一張住客勾選的需求與一張命中規則的打字需求進來`

- [X] T355 [P] [BDD-RED] `Then: 待派單頁由上到下依序是頁首、需求清單與頁尾說明`

- [X] T356 [P] [BDD-RED] `Then: 待派單頁的頁首沒有在班人數`

- [X] T357 [P] [BDD-RED] `Then: "{文字}" 在整頁只出現一次，位置在頁首`

- [X] T358 [P] [BDD-RED] `Then: 待派單頁 "{房號}" 這一列的內容如下：`

- [X] T359 [P] [BDD-RED] `Then: 待派單頁 "{房號}" 這一列的等待時間寫的是 "{文字}"`

- [X] T360 [P] [BDD-RED] `Then: 待派單頁 "{房號}" 這一列沒有 "{文字}"`

- [X] T361 [P] [BDD-RED] `Then: 待派單頁各列的住客如下：`

- [X] T362 [P] [BDD-RED] `Then: 待派單頁每一列右側的動作都只有 "{一}"、"{二}"、"{三}"`

- [X] T363 [P] [BDD-RED] `Then: 待派單頁沒有拆單、代開這張單或派給其他部門的動作`

- [X] T364 [P] [BDD-RED] `Then: 待派單頁的三顆動作都沒有 "{文字}" 小標`

- [X] T365 [P] [BDD-RED] `Then: 待派單頁的空狀態是 "{標題}"，說明是 "{說明}"`

- [X] T366 [P] [BDD-RED] `Then: 清單的位置是灰色的骨架列，頁副標是空的`

- [X] T367 [P] [BDD-RED] `Then: 後端沒有因為這次派單多出任何一張單`

- [X] T368 [P] [BDD-RED] `Then: 後端 "{房號}" 只有一筆派單紀錄，派單的人是 "{員工}"`

- [X] T369 [P] [BDD-RED] `Then: 後端 "{房號}" 的單由 "{同仁}" 負責，狀態是已接`

### 櫃檯 Web：`specs/truth/features/frontdesk-web/當場回答/dsl.md`

- [X] T370 [P] [BDD-RED] `Given: "{員工}" 開著 "{房號}" 的當場回答面板`

- [X] T371 [P] [BDD-RED] `Given: "{員工}" 開著 "{房號}" 的當場回答面板，回覆框裡是 "{回覆}"`

- [X] T372 [P] [BDD-RED] `Given: "{員工}" 開著 "{房號}" 的當場回答面板，已點常用回覆 "{標題}"`

- [X] T373 [P] [BDD-RED] `Given: "{員工}" 已當場回答 "{房號}"，回覆是 "{回覆}"`

- [X] T374 [P] [BDD-RED] `Given: "{員工}" 送出 "{房號}" 的當場回答時撞單，「這單已經被派走了」的提示開著`

- [X] T375 [P] [BDD-RED] `Given: "{員工}" 送出 "{房號}" 的當場回答 "{回覆}" 時網路中斷，「沒有送出」的提示開著`

- [X] T376 [P] [BDD-RED] `When: "{員工}" 點常用回覆 "{標題}"`

- [X] T377 [P] [BDD-RED] `When: "{員工}" 把回覆框改成 "{回覆}"`

- [X] T378 [P] [BDD-RED] `When: "{員工}" 清空回覆框`

- [X] T379 [P] [BDD-RED] `When: "{員工}" 在回覆框只打了幾個空白`

- [X] T380 [P] [BDD-RED] `When: "{員工}" 在回覆框打 {字數} 個字`

- [X] T381 [P] [BDD-RED] `Then: "{欄名}" 輸入框是空的，提示字是 "{提示字}"，字數是 "{字數顯示}"`

- [X] T382 [P] [BDD-RED] `Then: 常用回覆依序是 "{一}"、"{二}"、"{三}"、"{四}"、"{五}"`

- [X] T383 [P] [BDD-RED] `Then: 回覆框的內容是 "{回覆}"，字數是 "{字數顯示}"`

- [X] T384 [P] [BDD-RED] `Then: 回覆框裡只有 {字數} 個字，字數是 "{字數顯示}"`

- [X] T385 [P] [BDD-RED] `Then: 常用回覆 "{標題}" 是選中的樣子`

- [X] T386 [P] [BDD-RED] `Then: 五則常用回覆都不是選中的樣子`

- [X] T387 [P] [BDD-RED] `Then: 當場回答面板是關的`

- [X] T388 [P] [BDD-RED] `Then: 後端 "{房號}" 的單已由 "{員工}" 當場回答，回覆是 "{回覆}"`

### 櫃檯 Web：`specs/truth/features/frontdesk-web/單筆詳情/dsl.md`

- [X] T389 [P] [BDD-RED] `Given: "{員工}" 剛在 "{頁}" 把 "{房號}" 派給 "{部門}" 又按了「復原」`

- [X] T390 [P] [BDD-RED] `Then: 詳情面板貼齊畫面右側`

- [X] T391 [P] [BDD-RED] `Then: 詳情面板由上到下依序是 "{關閉}" 與單號、房號 "{房號}" 與狀態標籤、標題 "{標題}"、"{那一行}"、狀態訊息卡、"{內容標題}"、"{歷程標題}"`

- [X] T392 [P] [BDD-RED] `Then: 詳情面板的單號是 "{單號}"`

- [X] T393 [P] [BDD-RED] `Then: 詳情面板的「內容」如下：`

- [X] T394 [P] [BDD-RED] `Then: 詳情面板「內容」的 "{列名}" 是 "{內容}"`

- [X] T395 [P] [BDD-RED] `Then: 詳情面板上沒有住客人數`

- [X] T396 [P] [BDD-RED] `Then: 詳情面板標題下那一行是 "{文字}"`

- [X] T397 [P] [BDD-RED] `Then: 詳情面板的標題是 "{標題}"`

- [X] T398 [P] [BDD-RED] `Then: 詳情面板的原文列是 "{文字}"`

- [X] T399 [P] [BDD-RED] `Then: 詳情面板沒有原文列`

- [X] T400 [P] [BDD-RED] `Then: 詳情面板的「清潔 SOP」區塊：{有沒有}`

- [X] T401 [P] [BDD-RED] `Then: 詳情面板的清潔 SOP 顯示 "{文字}" 與進度條`

- [X] T402 [P] [BDD-RED] `Then: 詳情面板上沒有急件標記`

- [X] T403 [P] [BDD-RED] `Then: 詳情面板的狀態標籤是 "{標籤}"`

- [X] T404 [P] [BDD-RED] `Then: 詳情面板的狀態訊息卡標題是 "{標題}"，說明是 "{說明}"`

- [X] T405 [P] [BDD-RED] `Then: 詳情面板的狀態訊息卡只有標題 "{標題}"，沒有說明`

- [X] T406 [P] [BDD-RED] `Then: 詳情面板沒有狀態訊息卡`

- [X] T407 [P] [BDD-RED] `Then: 詳情面板的狀態訊息卡沒有 "{文字}"`

- [X] T408 [P] [BDD-RED] `Then: 詳情面板的狀態訊息卡寫出經手的主管是 "{主管}"`

- [X] T409 [P] [BDD-RED] `Then: 詳情面板的歷程標題是 "{標題}"`

- [X] T410 [P] [BDD-RED] `Then: 詳情面板的歷程由舊到新包含以下各筆：`

- [X] T411 [P] [BDD-RED] `Then: 詳情面板的歷程最新一筆是 "{句子}"，操作者是 "{操作者}"`

- [X] T412 [P] [BDD-RED] `Then: 詳情面板的歷程最新一筆的時間與操作者寫的是 "{文字}"`

- [X] T413 [P] [BDD-RED] `Then: 詳情面板的歷程沒有 "{句子}"`

- [X] T414 [P] [BDD-RED] `Then: 詳情面板的歷程比原本多一筆 "{句子}"，原本的各筆都沒有變`

- [X] T415 [P] [BDD-RED] `Then: 詳情面板底部的派單區依序是 "{一}"、"{二}"、"{三}" 三格`

- [X] T416 [P] [BDD-RED] `Then: 派單區的格子裡沒有待接數、在班人數與 "{文字}" 小標`

- [X] T417 [P] [BDD-RED] `Then: 詳情面板沒有「單據管理」動作列`

- [X] T418 [P] [BDD-RED] `Then: 詳情面板沒有派單區，也沒有 "{按鈕}"`

- [X] T419 [P] [BDD-RED] `Then: 詳情面板的「單據管理」動作列是：{動作列}`

### 櫃檯 Web：`specs/truth/features/frontdesk-web/即時更新/dsl.md`

- [X] T420 [P] [BDD-RED] `Given: 即時連線已經中斷，提醒列出現了`

- [X] T421 [P] [BDD-RED] `Given: 即時連線中斷後已經恢復，提醒列消失了`

- [X] T422 [P] [BDD-RED] `When: "{房號}" 的住客送出一張猜不準部門的打字需求 "{內容}"`

- [X] T423 [P] [BDD-RED] `When: "{同事}" 在另一台櫃檯把 "{房號}" 派給 "{部門}"`

- [X] T424 [P] [BDD-RED] `When: "{房號}" 的住客自己收回需求`

- [X] T425 [P] [BDD-RED] `When: 即時連線中斷`

- [X] T426 [P] [BDD-RED] `When: 後端宣告即時訊號可能延遲`

- [X] T427 [P] [BDD-RED] `When: 超過 {秒數} 秒沒有收到後端的任何訊號`

- [X] T428 [P] [BDD-RED] `When: 即時連線恢復`

- [X] T429 [P] [BDD-RED] `When: 後端連不上，重新讀取看板失敗`

- [X] T430 [P] [BDD-RED] `Then: {秒數} 秒內 "{房號}" 的卡片出現在 "{欄}" 欄`

- [X] T431 [P] [BDD-RED] `Then: {秒數} 秒內 "{房號}" 的卡片有負責人頭像 "{頭像}"，等待膠囊是 "{膠囊}"`

- [X] T432 [P] [BDD-RED] `Then: {秒數} 秒內側欄 "{功能}" 的徽章與 "{數量卡}" 數量卡都變成 {數字}`

- [X] T433 [P] [BDD-RED] `Then: {秒數} 秒內待派單頁的清單沒有 "{房號}"，側欄 "{功能}" 的徽章是 {數字}`

- [X] T434 [P] [BDD-RED] `Then: {秒數} 秒內待派單頁的清單第一筆是 "{房號}"`

- [X] T435 [P] [BDD-RED] `Then: 工具列下方出現提醒列 "{標題}"，說明是 "{說明}"`

- [X] T436 [P] [BDD-RED] `Then: 提醒列上沒有任何按鈕`

- [X] T437 [P] [BDD-RED] `Then: 這段期間 "{員工}" 沒有按任何東西`

- [X] T438 [P] [BDD-RED] `Then: 畫面只在收到即時訊號時才重新讀取看板，沒有每 {秒數} 秒定時讀取`

- [X] T439 subagent review (phase quality gate)

## Phase 4-B01: ADD Feature File - backend/帳號與登入/登入前查飯店名稱.feature

**Goal**: 不用登入查得到名稱且只有名稱、對不到一律查不到、拿內部編號查不到。

**Shared Must Read**:
- `specs/truth/features/backend/帳號與登入/登入前查飯店名稱.feature` -> `功能: 登入前查飯店名稱` 全文（3 條 Rule）
- `specs/truth/features/backend/帳號與登入/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 5 句（T037、T040–T041、T052–T053）
- `truth-delta.md` -> `/api-plan` ADD `GET /public/hotels/{hotelCode}`（`readPublicHotel`）；`/data-plan` ADD `tenants.code`
- `specs/truth/contracts/groups/營運管理/員工帳號與登入.yaml` -> `readPublicHotel`
- `specs/truth/contracts/components/parameters.yaml` -> `HotelCode`；`specs/truth/contracts/components/schemas/員工帳號與登入.yaml` -> `PublicHotel`
- `specs/truth/data/data-model.dbml` -> `Table tenants` 的 `code`
- `tms/src/main/java/com/athena/tms/controller/StaffAccountController.java`、`tms/src/main/java/com/athena/tms/config/SecurityConfig.java`（不用登入的路徑清單）

**Boundary**:
- 只回飯店名稱一項；查不到、格式不對、拿內部編號 `tenants.id` 來查，一律 404，回應長得一樣。
- 比對分大小寫、只認小寫，不做修剪或轉小寫後再查。
- 不用登入就能呼叫；不加任何其他公開端點。
- 只讓這個 Test Scope 全綠；不改 truth、不改 stepdef 的語意（stepdef 有錯回 Phase 3 修）。

**Test Scope**:
- `specs/truth/features/backend/帳號與登入/登入前查飯店名稱.feature`

- [X] T440 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T441 [BDD-REFACTOR] 在綠燈下把代碼格式檢查收成一處，和資料庫 CHECK 用同一條規則

## Phase 4-B02: MODIFY Feature File - backend/帳號與登入/員工登入.feature

**Goal**: 新增 Rule「登入成功的回應帶員工編號與飯店名稱，不必另外查」；既有 6 條 Rule 不動。

**Shared Must Read**:
- `specs/truth/features/backend/帳號與登入/員工登入.feature` -> `功能: 員工登入` 全文
- `specs/truth/features/backend/帳號與登入/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 6 句（T030–T034、T042）；既有不出題的句：`"{員工}" 已連續輸入錯誤密碼 {次數} 次`、`"{員工}" 用員工編號 "{員工編號}" 密碼 "{密碼}" 登入`、`"{員工}" 的員工編號密碼為 "{員工編號}" / "{密碼}"`、`"{員工}" 的帳號是臨時密碼`
- `specs/truth/features/backend/dsl.md` -> 用語對照；本檔用到、Phase 3 已對齊的 1 句（T022）；既有不出題的句：`"{員工}" 已停用`
- `truth-delta.md` -> `/dsl-refine` ADD `員工登入.feature` 的那條 Rule、MODIFY `be/帳號與登入/dsl.md`；`/api-plan` ADD `LoginResponse.loginId`、`LoginResponse.hotelName`
- `specs/truth/contracts/components/schemas/員工帳號與登入.yaml` -> `LoginResponse`
- `tms/src/main/java/com/athena/tms/dto/StaffLoginResponse.java:8-15`、`tms/src/main/java/com/athena/tms/service/StaffAccountLoginService.java`

**Boundary**:
- 登入與改密碼兩個回應都帶 `loginId` 與 `hotelName`，兩個都必回；只加欄位，既有欄位不動（手機 App 不讀就沒有影響）。
- T030–T034 收緊後的狀態碼（401、429、403）若產品現況不符，在這裡修到一致。
- 櫃檯 Web 用 `/api/v1/staff-accounts/login`；`/auth/login` 不動。
- 只讓這個 Test Scope 全綠；不改 truth、不改 stepdef 的語意（stepdef 有錯回 Phase 3 修）。

**Test Scope**:
- `specs/truth/features/backend/帳號與登入/員工登入.feature`

- [X] T442 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T443 [BDD-REFACTOR] 在綠燈下把登入與改密碼兩處組回應的程式收成一個方法

## Phase 4-B03: ADD Feature File - backend/帳號與登入/櫃檯畫面登入只放行三種角色.feature

**Goal**: 帶 `client`=`frontdesk_web` 的登入：三種角色成功；房務、工務、老闆密碼正確也回與密碼錯誤相同的回應；錯誤次數不增加也不清零；不帶 `client` 不受影響；臨時密碼先成功並要求改密；鎖定與停用的回應各自不同。

**Shared Must Read**:
- `specs/truth/features/backend/帳號與登入/櫃檯畫面登入只放行三種角色.feature` -> `功能: 櫃檯畫面登入只放行三種角色` 全文（8 條 Rule）
- `specs/truth/features/backend/帳號與登入/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 8 句（T030–T033、T038、T043–T045）；既有不出題的句：`"{員工}" 已連續輸入錯誤密碼 {次數} 次`、`"{員工}" 用員工編號 "{員工編號}" 密碼 "{密碼}" 登入`、`"{員工}" 的員工編號密碼為 "{員工編號}" / "{密碼}"`、`"{員工}" 的帳號是臨時密碼`
- `specs/truth/features/backend/dsl.md` -> 用語對照；本檔用到、Phase 3 已對齊的 1 句（T022）；既有不出題的句：`"{員工}" 已停用`
- `truth-delta.md` -> `/api-plan` MODIFY `staffAccountsLogin`、`LoginRequest.client`
- `specs/truth/contracts/groups/營運管理/員工帳號與登入.yaml` -> `staffAccountsLogin`；`specs/truth/contracts/components/schemas/員工帳號與登入.yaml` -> `LoginRequest`
- `tms/src/main/java/com/athena/tms/service/StaffAccountLoginService.java`（登入流程，現在沒有任何角色判斷）

**Boundary**:
- 只有請求帶 `client`=`frontdesk_web` 才檢查角色；不帶時行為完全不變。
- 檢查順序：停用、鎖定、密碼比對、角色。`housekeeping`、`engineering`、`owner` 密碼正確且不是臨時密碼時，回與密碼錯誤逐欄相同的 401 `credentials_invalid`，連續錯誤次數不增加也不清零。
- 是臨時密碼時照常登入成功，回 `mustChangePassword=true` 與角色。
- 只讓這個 Test Scope 全綠；不改 truth、不改 stepdef 的語意（stepdef 有錯回 Phase 3 修）。

**Test Scope**:
- `specs/truth/features/backend/帳號與登入/櫃檯畫面登入只放行三種角色.feature`

- [X] T444 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T445 [BDD-REFACTOR] 在綠燈下把「這個角色能不能用櫃檯畫面」收成一個函式，Phase 4-B10 的權限規則共用

## Phase 4-B04: ADD Feature File - backend/櫃檯看板與派單/櫃檯派單.feature

**Goal**: 派出後進部門待認領、歷程記部門與方式、拖曳與按鈕結果相同、不拆單、只能派房務或工務、三種撞單的拒絕與說明、被拒的不改單、必須帶防重複鍵、重送不會派兩次。

**Shared Must Read**:
- `specs/truth/features/backend/櫃檯看板與派單/櫃檯派單.feature` -> `功能: 櫃檯派單` 全文（11 條 Rule）
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 14 句（T060–T061、T069–T071、T091–T094、T096–T100）
- `specs/truth/features/backend/dsl.md` -> 用語對照；本檔用到、Phase 3 已對齊的 5 句（T022–T023、T025–T026、T028）；既有不出題的句：`這次操作被拒絕`
- `truth-delta.md` -> `/api-plan` ADD `deskAssignTask`、`task_not_in_triage`、`idempotency_key_required`；`/data-plan` ADD `triaged` 的 `via` 鍵
- `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> `deskAssignTask`；`specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskAssignRequest`、`DeskTask`
- `specs/truth/contracts/components/schemas/shared.yaml` -> `Error` 的 `task_not_in_triage`（`context` 的 `resolution`、`state`、`roomNo`、`actorName`、`dept`）、`idempotency_key_required`
- `specs/truth/data/data-model.dbml` -> `Table tasks` Note「櫃檯處理待派單的三種轉移」、`task_events.payload` 的 `triaged`
- `tms/src/main/java/com/athena/tms/service/TaskEngine.java:93-96、364-375`、`tms/src/main/java/com/athena/tms/service/DeskService.java:46-59`、`tms/src/main/java/com/athena/tms/filter/IdempotencyFilter.java:35-45`、`tms/src/main/java/com/athena/tms/common/error/StateConflictException.java:17`

**Boundary**:
- 新端點 `POST /api/v1/desk/tasks/{id}/assign`，只收 `housekeeping`、`engineering` 與 `via`（`button`、`drag`），其他值 422。派單不拆單、不改 `note`。
- 沒帶 `Idempotency-Key` 回 400 `idempotency_key_required`；同一把鍵重送不會派兩次。**只有櫃檯的三個寫入強制**，其他端點「有帶才生效」的現況不動。
- 撞單回 409 `task_not_in_triage`，`context` 說得出是被派走、被回答、住客收回還是其他；被拒的不改單、不寫事件。
- Then 的再讀確認若用到 `getDeskBoard` 或 `readDeskTask`，這裡先做到讓 Test Scope 綠的最小回應，完整欄位留給 Phase 4-B07、4-B08。
- 舊的 `/desk/triage/{taskId}` 不改也不沿用（Phase 4-B16 刪）。
- 只讓這個 Test Scope 全綠；不改 truth、不改 stepdef 的語意（stepdef 有錯回 Phase 3 修）。

**Test Scope**:
- `specs/truth/features/backend/櫃檯看板與派單/櫃檯派單.feature`

- [X] T446 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T447 [BDD-REFACTOR] 在綠燈下把「單已不是待派單時怎麼說明」收成一個方法，復原與當場回答共用

## Phase 4-B05: ADD Feature File - backend/櫃檯看板與派單/派出後復原.feature

**Goal**: 復原後回到還沒分部門、給人看的歷程回到派單前、事件各留一筆、10 秒內接受、超過拒絕、已被認領、已被住客收回、不是剛派出的不能復原、當場回答沒有復原、必須帶防重複鍵。

**Shared Must Read**:
- `specs/truth/features/backend/櫃檯看板與派單/派出後復原.feature` -> `功能: 派出後復原` 全文（10 條 Rule）
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 15 句（T060–T062、T064–T065、T072、T092、T102–T109）
- `specs/truth/features/backend/dsl.md` -> 用語對照；本檔用到、Phase 3 已對齊的 6 句（T022–T026、T029）；既有不出題的句：`"{任務}" 的負責人是 "{員工}"`
- `truth-delta.md` -> `/api-plan` ADD `deskUndoAssign`、四個 `undo_*`；`/data-plan` ADD 派出後復原 `pool`→`triage`、`triage_undone`、歷程怎麼呈現復原
- `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> `deskUndoAssign`
- `specs/truth/contracts/components/schemas/shared.yaml` -> `undo_claimed`、`undo_guest_withdrew`、`undo_expired`、`undo_unavailable`
- `specs/truth/data/data-model.dbml` -> `Table tasks` Note 的復原轉移與守衛條件、`task_events.type` 的 `triage_undone`、`Table task_events` Note
- `tms/src/main/java/com/athena/tms/service/TaskEngine.java`（新增 `pool`→`triage`）、`tms/src/main/java/com/athena/tms/common/codes/TaskEventType.java`

**Boundary**:
- 守衛條件：`state=pool`、沒有負責人、`event_seq` 等於那筆 `triaged` 的 seq；復原後 `dept` 回 `frontdesk`，`reminder_stage` 與 `pool_source` 清成 NULL；寫一筆緊接在 `triaged` 之後的 `triage_undone`（payload 帶被收回的 `dept`）。
- 後端的時間上限是距離那筆 `triaged` 10 秒（畫面只給 5 秒，多的 5 秒留給網路延遲與重送）。判斷順序：先看單現在的樣子，再看時間。
- 四種失敗各一個 code；不是剛派出的單（請假退回、班末退回、驗收不通過進池的）不能復原。當場回答沒有復原。
- 強制 `Idempotency-Key`。
- 只讓這個 Test Scope 全綠；不改 truth、不改 stepdef 的語意（stepdef 有錯回 Phase 3 修）。

**Test Scope**:
- `specs/truth/features/backend/櫃檯看板與派單/派出後復原.feature`

- [X] T448 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T449 [BDD-REFACTOR] 在綠燈下把復原的四種拒絕判斷整理成一張由上而下的判斷表

## Phase 4-B06: ADD Feature File - backend/櫃檯看板與派單/當場回答並結案.feature

**Goal**: 結案與回覆落地（`tasks.answer`）、狀態仍是既有的完成、不蓋掉住客原文、不算部門做掉的單、歷程、住客進度頁讀得到、去掉前後空白、120 字可以、空的與超過被拒、三種撞單、只有待派單可以回答、必須帶防重複鍵。

**Shared Must Read**:
- `specs/truth/features/backend/櫃檯看板與派單/當場回答並結案.feature` -> `功能: 當場回答並結案` 全文（14 條 Rule）
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 18 句（T060–T061、T073–T076、T091–T092、T098–T101、T109–T114）
- `specs/truth/features/backend/dsl.md` -> 用語對照；本檔用到、Phase 3 已對齊的 5 句（T022–T026）；既有不出題的句：`房務待認領有以下任務：`
- `truth-delta.md` -> `/api-plan` ADD `deskAnswerTask`、`answer_invalid`、`GuestRequest.answer`、MODIFY `listGuestRequests`、MODIFY `tasks.note`；`/data-plan` ADD `tasks.answer`、MODIFY `tasks.note`
- `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> `deskAnswerTask`；`specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskAnswerRequest`
- `specs/truth/contracts/groups/住客與房間/住客掃碼.yaml` -> `listGuestRequests`；`specs/truth/contracts/components/schemas/住客掃碼.yaml` -> `GuestRequest.answer`；`specs/truth/contracts/components/schemas/任務.yaml` -> `tasks.answer`、`tasks.note`
- `specs/truth/data/data-model.dbml` -> `tasks.answer`、`tasks.note`、`tasks.ended_at`
- `tms/src/main/java/com/athena/tms/service/TaskEngine.java:100`（`answer()` 現在把回覆寫進 `note`）、`tms/src/main/java/com/athena/tms/service/DeskService.java:53-55`、`tms/src/main/java/com/athena/tms/dto/GuestTaskView.java:7`、`tms/src/main/java/com/athena/tms/controller/GuestController.java`

**Boundary**:
- `TaskEngine.answer()` 改成把回覆寫進 `tasks.answer`，**不再碰 `tasks.note`**（住客原文、語言、既有翻譯都要留著）；同一步寫 `done_at` 與 `ended_at`，狀態仍是既有的 `done`，`dept` 留在 `frontdesk`，不算任何部門做掉的單。
- 回覆去掉頭尾空白後 1–120 字；空的或超過回 422 `answer_invalid`。只有待派單可以回答，撞單同 Phase 4-B04 的 409。強制 `Idempotency-Key`。
- 住客讀 `listGuestRequests` 時 `answer` 帶回覆全文；只做到後端，不做住客頁面。
- 舊測試線 `03-desk-triage.feature` 斷言回覆寫進 `note` 的案例會在這裡變紅，留給 Phase 4-B16 刪，不要為了它保留舊行為。
- 只讓這個 Test Scope 全綠；不改 truth、不改 stepdef 的語意（stepdef 有錯回 Phase 3 修）。

**Test Scope**:
- `specs/truth/features/backend/櫃檯看板與派單/當場回答並結案.feature`

- [X] T450 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T451 [BDD-REFACTOR] 在綠燈下把回覆的修剪與長度檢查收成一處

## Phase 4-B07: ADD Feature File - backend/櫃檯看板與派單/櫃檯看板讀取.feature

**Goal**: 看板範圍（還沒結束的加今天結束的）、當場回答的標示、單號、等待起算的三種情形、已接的來由、不帶急件、住客原文與既有翻譯、住客與 VIP、後端時間、樓層、本人今日張數、打字需求命中與不命中規則的去向、系統不自己派單。

**Shared Must Read**:
- `specs/truth/features/backend/櫃檯看板與派單/櫃檯看板讀取.feature` -> `功能: 櫃檯看板讀取` 全文（17 條 Rule）
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 20 句（T054–T060、T077、T081–T092）
- `specs/truth/features/backend/dsl.md` -> 用語對照；本檔用到、Phase 3 已對齊的 4 句（T022、T025–T027）；既有不出題的句：`已有以下工務單：`、`時間來到 "{時刻}"`、`現在時間是 "{時刻}"`
- `truth-delta.md` -> `/api-plan` ADD `getDeskBoard`、`DeskTask`／`DeskGuestText`、ADD `tasks.taskNo`／`answer`／`endedAt`；`/data-plan` ADD `tasks.task_no`、`Table task_counters`、`tasks.ended_at`、今日張數的 Note 與索引
- `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> `getDeskBoard`；`specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskBoard`、`DeskMyToday`、`DeskTask`、`DeskGuestText`
- `specs/truth/data/data-model.dbml` -> `Table tasks` 的 `task_no`、`ended_at` 與 Note「櫃檯看板的範圍」「櫃檯的今日已派單與當場回答張數」、`Table task_counters`
- `tms/src/main/java/com/athena/tms/service/DeskService.java:31-39`、`tms/src/main/java/com/athena/tms/dto/TaskResponse.java:8`、`tms/src/main/java/com/athena/tms/service/TaskEngine.java`（每一條走到終點的轉移）、`tms/src/main/java/com/athena/tms/repair/RepairService.java:706-714`（取號的既有寫法）、`tms/src/main/java/com/athena/tms/service/GuestRequestService.java:134-137`（`note` 裡的 `llm_translated`）、`tms/src/main/java/com/athena/tms/service/TaskExecutionService.java:517`（等待起算的既有算法）

**Boundary**:
- 範圍＝`ended_at IS NULL` 加上今天結束的單；「今天」用注入的 `Clock`（伺服器時區）。一次回完、不分頁、不排序。
- **`TaskEngine` 與其他服務裡每一條走到終點的路徑都要寫 `ended_at`**：`inspected`（查房通過、免查房）、`cancelled`（住客收回、主管取消、排房被取代、同仁撤回）、`closed`（驗收通過、不需處理直接結案）、完成即結束的 `done`（`amenity`、`other`）與當場回答；抽查退回時清空。用 `state = 'inspected'`、`'cancelled'`、`'closed'`、`'done'` 全文搜尋 `tms/src/main/`，一條都不能漏。
- **建單時取號**：`type` 不是 `repair` 的單在建單的同一個交易裡向 `task_counters` 取號寫入 `task_no`（`INSERT … ON CONFLICT (tenant_id) DO UPDATE … RETURNING` 一句完成），`created` 事件的建立快照帶 `task_no`；工務單不領 `T-` 單號，`displayNo` 讀 `ticket_no`。
- `waitingSince` 沿用待認領清單與未接提醒的同一把尺；`acceptedVia` 由最近一筆指派或認領事件判定，不用來源猜；不帶急件；延後只給誰延後的。
- 今日已派單與當場回答張數一律從事件算（扣掉緊接著被 `triage_undone` 的派單），不存計數。
- 本檔有 2 個待確認預設值，照 feature 寫的值實作。
- 只讓這個 Test Scope 全綠；不改 truth、不改 stepdef 的語意（stepdef 有錯回 Phase 3 修）。

**Test Scope**:
- `specs/truth/features/backend/櫃檯看板與派單/櫃檯看板讀取.feature`

- [X] T452 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T453 [BDD-REFACTOR] 在綠燈下把「寫 `ended_at`」收成 `TaskEngine` 的一個共用片段，並把 `DeskTask` 的組裝收成一個 mapper

## Phase 4-B08: ADD Feature File - backend/櫃檯看板與派單/單筆詳情與歷程.feature

**Goal**: 詳情帶住客與日期、空房、開單人、回覆與回答者、已接的來由、取消的說明、工務單的完修結果與驗收主管；歷程由舊到新、只增不改、不含逐項勾選；找不到單。

**Shared Must Read**:
- `specs/truth/features/backend/櫃檯看板與派單/單筆詳情與歷程.feature` -> `功能: 單筆詳情與歷程` 全文（11 條 Rule）
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 12 句（T054、T056、T060、T062–T063、T066、T078–T079、T115–T118）
- `specs/truth/features/backend/dsl.md` -> 用語對照；本檔用到、Phase 3 已對齊的 3 句（T022、T025–T026）；既有不出題的句：`"{員工}" 名下有以下任務：`、`"{員工}" 完成 "{任務}"`、`已有以下工務單：`、`這次操作被拒絕`
- `truth-delta.md` -> `/api-plan` ADD `readDeskTask`、`DeskTaskDetail`、`DeskTimelineEntry`；`/data-plan` ADD 歷程怎麼呈現復原
- `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> `readDeskTask`；`specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskTaskDetail`、`DeskTimelineEntry`
- `specs/truth/contracts/components/schemas/任務事件流水帳.yaml` -> `task_events.type`、`payload`
- `specs/truth/data/data-model.dbml` -> `Table task_events` Note、`Table tasks`、`Table repair_tickets`（完修結果與驗收主管）
- `tms/src/main/java/com/athena/tms/service/DeskService.java`、Phase 4-B07 的 `DeskTask` mapper

**Boundary**:
- 歷程併在詳情裡回，不另開按單查事件的端點；由舊到新、只增不改；略過被復原的 `triaged` 與 `triage_undone` 那一對，也略過 `sop_checked`。句子由畫面組，後端只給事件種類、時間、操作者與 `detail`。
- 不帶住客人數。找不到單回 404。
- 只讓這個 Test Scope 全綠；不改 truth、不改 stepdef 的語意（stepdef 有錯回 Phase 3 修）。

**Test Scope**:
- `specs/truth/features/backend/櫃檯看板與派單/單筆詳情與歷程.feature`

- [X] T454 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T455 [BDD-REFACTOR] 在綠燈下把歷程的略過規則整理成一個可單獨測的函式

## Phase 4-B09: ADD Feature File - backend/櫃檯看板與派單/常用回覆.feature

**Goal**: 回住客資訊頁的每一頁與順序、沒有設定時是空的、其他角色讀不到。

**Shared Must Read**:
- `specs/truth/features/backend/櫃檯看板與派單/常用回覆.feature` -> `功能: 常用回覆` 全文（3 條 Rule）
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 6 句（T067–T068、T080、T119–T121）
- `specs/truth/features/backend/dsl.md` -> 用語對照；本檔用到、Phase 3 已對齊的 2 句（T022–T023）
- `truth-delta.md` -> `/api-plan` ADD `listDeskQuickReplies`、`DeskQuickReply`
- `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> `listDeskQuickReplies`；`specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskQuickReply`
- `tms/src/main/java/com/athena/tms/controller/GuestController.java:46`、`tms/src/main/java/com/athena/tms/dto/GuestHomeView.java:7`（住客資訊頁現在的讀法）

**Boundary**:
- 回住客資訊頁的每一頁（鍵、標題、全文），照設定的順序；沒有設定回空陣列。唯讀。
- 不動 `/tenant-configs/**` 與住客端既有的讀取。
- 只讓這個 Test Scope 全綠；不改 truth、不改 stepdef 的語意（stepdef 有錯回 Phase 3 修）。

**Test Scope**:
- `specs/truth/features/backend/櫃檯看板與派單/常用回覆.feature`

- [X] T456 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T457 [BDD-REFACTOR] 在綠燈下讓住客端與櫃檯端共用同一段讀住客資訊頁的程式

## Phase 4-B10: ADD Feature File - backend/櫃檯看板與派單/櫃檯介面的角色權限.feature

**Goal**: 三種角色讀得到看板；房務、工務、老闆讀不到也不能派單與回答；主管與值班主管的派單記本人與當下角色；值班主管可以復原與當場回答。

**Shared Must Read**:
- `specs/truth/features/backend/櫃檯看板與派單/櫃檯介面的角色權限.feature` -> `功能: 櫃檯介面的角色權限` 全文（7 條 Rule）
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 7 句（T073、T081、T091、T094–T095、T102、T109）
- `specs/truth/features/backend/dsl.md` -> 用語對照；本檔用到、Phase 3 已對齊的 7 句（T022–T023、T025–T029）
- `truth-delta.md` -> `/api-plan` ADD tag `櫃檯看板與派單 API`（只有三種角色可以呼叫）、MODIFY 角色的可用值含 `duty_manager`；`/data-plan` ADD `task_events.actor_role` 值 `duty_manager`
- `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> 六支操作的權限說明；`specs/truth/contracts/components/schemas/任務事件流水帳.yaml` -> `task_events.actorRole`
- `specs/truth/data/data-model.dbml` -> `task_events.actor_role`
- `tms/src/main/java/com/athena/tms/config/SecurityConfig.java:44-73`、`tms/src/main/java/com/athena/tms/common/codes/StaffRole.java:11`、`tms/src/main/java/com/athena/tms/common/codes/RolePermissions.java:7`

**Boundary**:
- `/api/v1/desk/**` 一條規則放行 `frontdesk`、`manager`、`duty_manager`；`housekeeping`、`engineering`、`owner` 回 403 `forbidden`。
- `duty_manager` 只多放行這一組、登入、改密碼與 `/staff/stream`；**手機 App 的主管功能（主管待辦、驗收等）不放行**，既有的查房提交維持現況。
- 派單、復原、當場回答的事件記操作者本人與當下的角色；值班主管記 `duty_manager`，不借用 `manager`。
- 改 `SecurityConfig.java` 前先確認工作樹沒有別人未提交的改動（Scope Note 第 7 條）。舊測試線 `05-identity-tenancy.feature` 與新規則衝突的案例在這裡改寫。
- 只讓這個 Test Scope 全綠；不改 truth、不改 stepdef 的語意（stepdef 有錯回 Phase 3 修）。

**Test Scope**:
- `specs/truth/features/backend/櫃檯看板與派單/櫃檯介面的角色權限.feature`

- [X] T458 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T459 [BDD-REFACTOR] 在綠燈下把三種角色的清單收成一個常數，`SecurityConfig`、登入檢查與串流收件範圍共用

## Phase 4-B11: ADD Feature File - backend/帳號與登入/設定新密碼.feature

**Goal**: 不必帶臨時密碼、改完直接拿到憑證、臨時密碼失效、三條強度規則、不能與目前密碼相同、沒改完讀不到看板、被擋角色改完新密碼生效並回角色、之後照常被擋。

**Shared Must Read**:
- `specs/truth/features/backend/帳號與登入/設定新密碼.feature` -> `功能: 設定新密碼` 全文（7 條 Rule）
- `specs/truth/features/backend/帳號與登入/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 11 句（T035–T036、T038–T039、T043、T046–T051）；既有不出題的句：`"{員工}" 的員工編號密碼為 "{員工編號}" / "{密碼}"`、`"{員工}" 的帳號是臨時密碼`
- `specs/truth/features/backend/dsl.md` -> 用語對照；本檔用到、Phase 3 已對齊的 2 句（T022、T027）；既有不出題的句：`這次操作被拒絕`
- `truth-delta.md` -> `/api-plan` MODIFY `changeOwnPassword`、`new_password_too_weak`
- `specs/truth/contracts/groups/營運管理/員工帳號與登入.yaml` -> `changeOwnPassword`；`specs/truth/contracts/components/schemas/員工帳號與登入.yaml` -> `ChangePasswordRequest`、`LoginResponse`；`specs/truth/contracts/components/schemas/shared.yaml` -> `new_password_too_weak`（`context.failedRules`）
- `tms/src/main/java/com/athena/tms/service/StaffAccountLoginService.java:65-79`、`app/mobile/lib/features/login/password_rules.dart`（手機 App 已經先檢查的同一組規則，只讀不改）

**Boundary**:
- 新密碼至少 8 碼、含英文字母、含數字；不符合回 422 `new_password_too_weak`，`context.failedRules` 說出沒過的是哪幾條。規則對所有呼叫者適用，不分櫃檯 Web 或手機 App。
- 拿臨時密碼登入的人不必再帶臨時密碼；改完直接拿到可用的憑證與角色；被擋角色改完新密碼照樣生效，之後從櫃檯畫面登入照常被擋。
- 還沒改完的憑證讀不到看板。
- 只讓這個 Test Scope 全綠；不改 truth、不改 stepdef 的語意（stepdef 有錯回 Phase 3 修）。

**Test Scope**:
- `specs/truth/features/backend/帳號與登入/設定新密碼.feature`

- [X] T460 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T461 [BDD-REFACTOR] 在綠燈下把三條強度規則收成一個回傳「沒過哪幾條」的函式

## Phase 4-B12: ADD Feature File - backend/員工事件串流/收得到哪些單的訊號.feature

**Goal**: 三種角色（`frontdesk`、`manager`、`duty_manager`）收全館的訊號；房務與工務維持同部門或自己名下；派單訊號的收件範圍；復原訊號另外送給被收回的部門；連上後的起始與重抓通知（全館角色的 `resync.refetch` 是 `/api/v1/desk/board`）；老闆不能連線。

**Shared Must Read**:
- `specs/truth/features/backend/員工事件串流/收得到哪些單的訊號.feature` -> `功能: 收得到哪些單的訊號` 全文（6 條 Rule）、`降級與恢復訊號.feature`（5 條 Rule）、`dsl.md`（15 個句型）
- `specs/truth/features/backend/員工事件串流/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 6 句（T122、T124、T129–T132）
- `specs/truth/features/backend/dsl.md` -> 用語對照；本檔用到、Phase 3 已對齊的 5 句（T022、T025–T026、T028–T029）；既有不出題的句：`"{員工}" 認領 "{任務}"`、`已有以下工務單：`
- `truth-delta.md` -> `/api-plan` ADD `GET /staff/stream`（`staffStream`）；`/dsl-refine` ADD `be/員工事件串流/` 三個檔
- `specs/truth/contracts/groups/現場作業/員工事件串流.yaml` -> `staffStream`；`specs/truth/contracts/components/schemas/員工事件串流.yaml` -> `StaffStreamInit`、`StaffStreamResync`、`StaffStreamTask`
- `tms/src/main/java/com/athena/tms/service/StreamConnection.java:57-61、80`、`tms/src/main/java/com/athena/tms/service/TaskEventsSseConsumer.java:155`、`tms/src/main/java/com/athena/tms/controller/StaffStreamController.java`

**Boundary**:
- 收件範圍依角色決定，不用連線時聲明用途。放寬只針對三種角色；房務與工務不得因此收到別部門的事件。
- `triage_undone` 除了全館角色，另外送給被收回的那個部門（單已經回到 `frontdesk`，照「單現在的部門」判斷會漏掉他們）。
- 舊測試線 `14-sse-stream.feature` 與新收件範圍衝突的案例在這裡改寫。
- 只讓這個 Test Scope 全綠；不改 truth、不改 stepdef 的語意（stepdef 有錯回 Phase 3 修）。

**Test Scope**:
- `specs/truth/features/backend/員工事件串流/收得到哪些單的訊號.feature`

- [X] T462 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T463 [BDD-REFACTOR] 在綠燈下把「這個連線收不收這筆事件」收成一個純函式

## Phase 4-B13: ADD Feature File - backend/員工事件串流/降級與恢復訊號.feature

**Goal**: 兩種降級原因（事件落後、`kafka_unavailable`）、恢復時送 `recovered`、降級期間新連線補送 `degraded`、心跳帶 `degraded` 旗標。

**Shared Must Read**:
- `specs/truth/features/backend/員工事件串流/降級與恢復訊號.feature` -> `功能: 降級與恢復訊號` 全文
- `specs/truth/features/backend/員工事件串流/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 11 句（T122–T128、T133–T136）
- `specs/truth/features/backend/dsl.md` -> 用語對照；本檔用到、Phase 3 已對齊的 1 句（T022）
- `truth-delta.md` -> `/api-plan` ADD `GET /staff/stream` 的 `recovered`、`heartbeat.degraded`、`kafka_unavailable`
- `specs/truth/contracts/components/schemas/員工事件串流.yaml` -> `StaffStreamHeartbeat`、`StaffStreamDegraded`、`StaffStreamRecovered`
- `tms/src/main/java/com/athena/tms/service/TaskEventsSseConsumer.java:77-89`（進入降級會廣播，恢復只改內部旗標）、`tms/src/main/java/com/athena/tms/service/StreamConnection.java:114`（心跳）

**Boundary**:
- 恢復訊號走兩條路：`recovered` 事件，加上每次心跳帶目前是不是降級；中途連上或漏接的客戶端也對得齊。
- 不改住客串流與整合串流的行為。
- 只讓這個 Test Scope 全綠；不改 truth、不改 stepdef 的語意（stepdef 有錯回 Phase 3 修）。

**Test Scope**:
- `specs/truth/features/backend/員工事件串流/降級與恢復訊號.feature`

- [X] T464 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T465 [BDD-REFACTOR] 在綠燈下把降級狀態收成一個物件，廣播、心跳與新連線補送都讀它

## Phase 4-B14: MODIFY Feature File - backend/主管工作台/主管待辦空清單與權限.feature

**Goal**: 既有 Rule「主管待辦只開放主管角色查看」多一個例子：值班主管查看主管待辦被拒。既有例子不動。

**Shared Must Read**:
- `specs/truth/features/backend/主管工作台/主管待辦空清單與權限.feature` -> `功能: 主管待辦空清單與權限` 全文
- `specs/truth/features/backend/主管工作台/dsl.md` -> 模組說明與用語對照；既有不出題的句：`"{員工}" 查看主管待辦`、`主管待辦的格子數字為：`、`這次查詢被拒絕，因為呼叫者不是主管`
- `specs/truth/features/backend/dsl.md` -> 用語對照；本檔用到、Phase 3 已對齊的 1 句（T022）；既有不出題的句：`"{員工}" 名下有以下任務：`
- `truth-delta.md` -> `/dsl-refine` ADD Example「值班主管想查看主管待辦」；`/api-plan` NOOP `GET /me/manager-queues`（`duty_manager` 不加進這些端點）
- `specs/truth/contracts/groups/現場作業/主管工作台.yaml` -> `getManagerQueues` 的權限
- `tms/src/main/java/com/athena/tms/config/SecurityConfig.java`、`tms/src/main/java/com/athena/tms/service/ManagerQueueService.java`

**Boundary**:
- 確認 Phase 4-B10 放行 `duty_manager` 時沒有順手把主管待辦也放行。預期不用改產品碼；Test Scope 若已經綠，GREEN 只記錄結果。
- 只讓這個 Test Scope 全綠；不改 truth、不改 stepdef 的語意（stepdef 有錯回 Phase 3 修）。

**Test Scope**:
- `specs/truth/features/backend/主管工作台/主管待辦空清單與權限.feature`

- [X] T466 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T467 [BDD-REFACTOR] 在綠燈下確認沒有需要整理的地方；有重複的角色判斷就併到 Phase 4-B10 的常數

## Phase 4-B15: MODIFY Feature File - backend/推播/員工推播事件.feature

**Goal**: 背景員工表的「值班經理」改寫成「值班主管」；行為與驗證都沒有變。

**Shared Must Read**:
- `specs/truth/features/backend/推播/員工推播事件.feature` -> `功能: 員工推播事件` 全文
- `specs/truth/features/backend/推播/dsl.md` -> 模組說明與用語對照；既有不出題的 10 句（本檔 feature 用到的每一句都對得到這個檔的一列）
- `specs/truth/features/backend/dsl.md` -> 用語對照；本檔用到、Phase 3 已對齊的 1 句（T022）；既有不出題的 13 句（本檔 feature 用到的每一句都對得到這個檔的一列）
- `truth-delta.md` -> `/dsl-refine` MODIFY `be/推播/員工推播事件.feature` 背景員工表、MODIFY `be/dsl.md` 部門用語對照
- `specs/truth/features/backend/dsl.md` -> 部門用語對照、`飯店有以下員工：`

**Boundary**:
- 只是用語改名，靠 T022 的 stepdef 就該綠；不改任何推播的產品碼。紅了先查 T022。
- 只讓這個 Test Scope 全綠；不改 truth、不改 stepdef 的語意（stepdef 有錯回 Phase 3 修）。

**Test Scope**:
- `specs/truth/features/backend/推播/員工推播事件.feature`

- [X] T468 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T469 [BDD-REFACTOR] 在綠燈下確認測試程式與註解裡不再出現「值班經理」（員工 App 測試線除外）

## Phase 4-B16: DELETE Feature / DSL Truth - 舊的待派單處理端點（`POST /tasks/{id}/triage`，連同契約沒收的 `/desk/triage`、`/desk/triage/{taskId}`、`/desk/tasks`）

**Goal**: 櫃檯處理待派單只剩 `deskAssignTask`、`deskUndoAssign`、`deskAnswerTask` 三條路。移除產品碼裡支援舊路徑的端點、請求型別與服務方法，以及只保護它們的舊測試；確認新版 truth 成立、沒有測試繼續保護「當場回答寫進 `note`」或「同一個端點用 `action` 分派單與回答」。

**Shared Must Read**:
- `specs/truth/features/backend/櫃檯看板與派單/櫃檯派單.feature`、`派出後復原.feature`、`當場回答並結案.feature` -> 新版 truth 的三條路
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> 模組說明（櫃檯視角的讀寫只走 `/api/v1/desk/**`）
- `truth-delta.md` -> `/api-plan` DELETE `POST /tasks/{id}/triage`（`tasksTriage`）、ADD tag `櫃檯看板與派單 API`（「舊的 `/desk/*` 不進契約，留或拆由實作決定」）；`/dsl-refine` 後端 NOOP 列（既有 feature 沒有任何一處描述 `/desk/triage`、`/tasks/{id}/triage`）
- `specs/truth/contracts/openapi.yaml`、`specs/truth/contracts/groups/現場作業/任務.yaml` -> 已經沒有 `tasksTriage`；`任務 API` 的 tag 說明指向櫃檯看板與派單 API
- `tms/src/main/java/com/athena/tms/controller/DeskController.java:16-34` -> `/desk/triage`、`/desk/triage/{taskId}`、`/desk/tasks`
- `tms/src/main/java/com/athena/tms/dto/TriageDecisionRequest.java`、`tms/src/main/java/com/athena/tms/service/DeskService.java:31-59` -> 用 `action` 分派單與回答的舊入口
- `tms/src/main/java/com/athena/tms/config/SecurityConfig.java:52` -> `/desk/**` 的舊規則
- `tms/src/test/resources/features/03-desk-triage.feature` -> 保護舊端點的舊測試，整份刪
- `tms/src/test/java/com/athena/tms/integration/stepdefs/when/StaffDeskWhenSteps.java:146-189、199-201` -> 七句櫃檯步驟（`櫃檯以 dept … 判讀任務`、四句 `櫃檯以 action … 判讀任務`、`櫃檯查詢任務清單`、`櫃檯判讀一個不存在的任務`）與只服務它們的 `decideOn`、`decide`；同檔 31–145 行是 `02-staff-pool.feature` 在用的認領、開始完成、勾 SOP、查待認領，**保留**
- `tms/src/test/java/com/athena/tms/integration/stepdefs/then/StaffDeskThenSteps.java:122-165` -> 五句櫃檯步驟（`該任務仍停在 "triage"`、`房務「小美」的待認領立刻看得到這單`、`回應的任務 … 的 doneAt 應有值`、`清單含標題「…」且其狀態為 …`、`待判讀清單只含 triage 的任務`）；同檔 32–121 行是 `02-staff-pool.feature` 在用的，**保留**
- `tms/src/test/resources/features/02-staff-pool.feature:32、39-40、51、80、146、148`、`13-core-backbone.feature` -> 用到上面兩個檔裡要保留的步驟，刪完不得出現 undefined step
- `tms/src/test/java/com/athena/tms/auth/AuthTestEndpointsConfiguration.java:42-50` -> 測試專用的 `/desk/probe`（GET、POST）；`tms/src/test/resources/features/05-identity-tenancy.feature:93-126` -> 引用它的列

**Boundary**:
- 先全文搜尋 `tasksTriage`、`/tasks/` 加 `/triage`、`/desk/triage`、`/desk/tasks`、`TriageDecisionRequest`：`tms/src/main/`、`app/web/`、`app/mobile/lib/`、`app/bdd-support/`、`app/web-bdd/` 都不得再有呼叫者。契約路徑 `POST /tasks/{id}/triage` 本來就沒有實作，確認沒有殘留的骨架即可。
- 刪 `DeskController` 的三支舊端點、`TriageDecisionRequest`、`DeskService` 裡只服務它們的方法；`TaskEngine` 的派單與回答方法已由新端點使用，保留。`SecurityConfig` 的 `/desk/**` 舊權限規則一併拿掉。拿掉之後 `/desk/probe` 會落到最後的 `denyAll`，所以**測試專用端點的路徑本身也要改**：`AuthTestEndpointsConfiguration` 的兩支 `/desk/probe`（GET、POST）改掛到 `/api/v1/desk/probe`，`05-identity-tenancy.feature` 引用 `/desk/probe` 的列跟著改路徑，讓它驗的是 `/api/v1/desk/**` 那一條新規則（櫃檯、主管、值班主管放行，房務、工務、老闆被擋）。
- 舊測試只刪這些：`03-desk-triage.feature` 整份；`StaffDeskWhenSteps.java` 的七句櫃檯步驟與 `decideOn`、`decide`；`StaffDeskThenSteps.java` 的五句櫃檯步驟，以及刪完之後不再有人呼叫的私有 helper（`taskIdInState`、`containsId` 等，逐一確認沒有其他呼叫者才刪；`taskIdByTitle` 與 `json()` 仍被保留的步驟使用，不能刪）。**這兩個檔案本身不刪**：`02-staff-pool.feature` 的認領、同秒搶單、開始完成、勾 SOP、查待認領都定義在裡面，別處沒有。刪之前用每一句的字面全文搜尋 `tms/src/test/resources/features/`，確認只有 `03-desk-triage.feature` 在用；行號以開檔看到的為準。
- 不刪 `tasksRequest`、`tasks` 兩個 schema 對應的型別（還有其他端點在用）。
- 不動 `GET /tasks`、`GET /task-events`、`/alerts`、`/reservations`、`/rooms` 清單（契約有、程式沒有的狀況不在 004）。

**Test Scope**:
- `specs/truth/features/backend/櫃檯看板與派單/櫃檯派單.feature`
- `specs/truth/features/backend/櫃檯看板與派單/派出後復原.feature`
- `specs/truth/features/backend/櫃檯看板與派單/當場回答並結案.feature`
- `specs/truth/features/backend/櫃檯看板與派單/櫃檯介面的角色權限.feature`
- 舊測試線整包：`cd tms && mvn -q test -Dtest=RunCucumberTest`

- [X] T470 [CODE-REMOVE] 移除舊的待派單處理端點、`TriageDecisionRequest`、`/desk/**` 舊權限規則（`/desk/probe` 改掛 `/api/v1/desk/probe`）、`03-desk-triage.feature`，以及兩個 `StaffDesk*Steps.java` 裡只服務櫃檯判讀與舊任務清單查詢的步驟（檔案本身保留）
- [X] T471 [REGRESSION] 跑 Test Scope，確認新版 truth 成立、舊行為沒有測試繼續保護；`RunCucumberTest` 整包跑完，`02-staff-pool.feature`、`13-core-backbone.feature` 不得有 undefined step

## Phase 4-W01: ADD Feature File - frontdesk-web/帳號與登入/登入頁.feature

**Goal**: 登入頁的欄位與按鈕、兩欄有內容才能登入、員工編號轉大寫、欄位長度、顯示與隱藏密碼、三種角色進看板、三種失敗共用一句、鎖定與停用各自的提示卡、忘記密碼只給提示、飯店名稱查不到就不顯示。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/帳號與登入/登入頁.feature` -> `功能: 登入頁` 全文（12 條 Rule）
- `specs/truth/features/frontdesk-web/帳號與登入/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 23 句（T187、T189–T194、T212–T216、T232–T238、T241–T244）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 11 句（T153、T158–T165、T173、T175）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/帳號與登入/登入頁.feature` 那一列
- `ui/ui-plan.md` -> 畫面 1「登入頁」、「驗證與錯誤回饋」、「給 `/api-plan` 的資料需求」的登入前與登入回應
- `ui/index.html`（含 `?hotel=none`）、`ui/desk.css`、`ui/desk.js` 的登入段 -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`readPublicHotel`、`staffAccountsLogin`（帶 `client`=`frontdesk_web`）（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `pages/login/`、`api/`、`stores/session.ts`

**Boundary**:
- 飯店名稱用網址上的飯店識別代碼查，查不到就不顯示那一行；登入後以登入回應的 `hotelName` 為準。
- 三種失敗共用同一句提示；鎖定與停用各有自己的提示卡。忘記密碼只給提示，不做重設流程。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 本檔有 1 個 `# [need clarification]`（待使用者確認的預設值），照 feature 目前寫的值實作，不停下來問。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/帳號與登入/登入頁.feature`

- [X] T472 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T473 [BDD-REFACTOR] 在綠燈下把登入表單的欄位規則（轉大寫、長度、能不能按登入）收成一個 hook

## Phase 4-W02: ADD Feature File - frontdesk-web/帳號與登入/設定新密碼畫面.feature

**Goal**: 拿臨時密碼先進設定新密碼畫面、不必再輸入臨時密碼、改完前進不了其他頁、四條規則、不能與臨時密碼相同、改完直接進看板、被擋角色改完回登入頁並被告知、之後登入照常被擋、可以登出離開、重新整理仍停在這一頁。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/帳號與登入/設定新密碼畫面.feature` -> `功能: 設定新密碼畫面` 全文（12 條 Rule）
- `specs/truth/features/frontdesk-web/帳號與登入/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 22 句（T187、T195–T197、T216、T220–T223、T232、T237–T241、T243、T245–T250）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 7 句（T153、T158、T160–T163、T175）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/帳號與登入/設定新密碼畫面.feature` 那一列
- `ui/ui-plan.md` -> 畫面 2「改密碼畫面」、「驗證與錯誤回饋」
- `ui/change-password.html` -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`changeOwnPassword`（`new_password_too_weak`）（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `pages/change-password/`、三條守門

**Boundary**:
- 改完之前進不了其他頁；重新整理仍停在這一頁、兩欄清空。被擋角色改完回登入頁並被告知。四條規則的即時提示在畫面檢查，後端的 422 是最後一道。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 本檔有 7 個 `# [need clarification]`（待使用者確認的預設值），照 feature 目前寫的值實作，不停下來問。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/帳號與登入/設定新密碼畫面.feature`

- [X] T474 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T475 [BDD-REFACTOR] 在綠燈下把新密碼的四條規則收成 `lib/` 的純函式並補 `Vitest`

## Phase 4-W03: ADD Feature File - frontdesk-web/帳號與登入/帳號面板與登出.feature

**Goal**: 帳號卡與身分卡的文字、面板只有三項、身分卡點了沒反應、完成關閉、登出先確認、取消不登出、登出後不留上一位的資訊。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/帳號與登入/帳號面板與登出.feature` -> `功能: 帳號面板與登出` 全文（8 條 Rule）
- `specs/truth/features/frontdesk-web/帳號與登入/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 13 句（T198–T199、T224–T226、T232、T238、T251–T256）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 7 句（T140、T153、T159、T161、T165、T176、T182）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/帳號與登入/帳號面板與登出.feature` 那一列
- `ui/ui-plan.md` -> 畫面 4「帳號面板」
- `ui/board.html`（點側欄帳號卡） -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：登入回應的 `name`、`loginId`、`role`、`hotelName`（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `features/account/`、`shell/`

**Boundary**:
- 面板只有三項；身分卡點了沒反應。登出先確認；登出後清掉這個分頁的登入狀態與看板檢視狀態，不留上一位的任何資訊。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/帳號與登入/帳號面板與登出.feature`

- [X] T476 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T477 [BDD-REFACTOR] 在綠燈下整理登出時要清掉的東西，集中在一個函式

## Phase 4-W04: ADD Feature File - frontdesk-web/帳號與登入/登入狀態跟著分頁.feature

**Goal**: 重新整理不用再登入並停在原頁、篩選搜尋泳道保留、面板不保留、側欄選擇保留、關掉分頁或瀏覽器要重新登入、每個分頁各自登入與登出。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/帳號與登入/登入狀態跟著分頁.feature` -> `功能: 登入狀態跟著分頁` 全文（9 條 Rule）
- `specs/truth/features/frontdesk-web/帳號與登入/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 18 句（T204–T207、T217、T223、T229–T232、T238、T256–T262）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 4 句（T140–T141、T175、T182）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/帳號與登入/登入狀態跟著分頁.feature` 那一列
- `ui/ui-plan.md` -> 「前端實作切分建議」的「重新整理後保留什麼」表、畫面 1、畫面 3
- `ui/index.html`、`ui/board.html`、`ui/triage.html` -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：登入回應（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `stores/session.ts`、`stores/board-view.ts`、路由守門

**Boundary**:
- 登入憑證只放 `sessionStorage`：重新整理不用重登、關掉分頁或瀏覽器要重登、每個分頁各自登入與登出。篩選、搜尋、泳道、側欄選擇保留；面板、對話框、浮條不保留。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 本檔有 2 個 `# [need clarification]`（待使用者確認的預設值），照 feature 目前寫的值實作，不停下來問。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/帳號與登入/登入狀態跟著分頁.feature`

- [X] T478 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T479 [BDD-REFACTOR] 在綠燈下把「哪些狀態跟著分頁」集中宣告在一處

## Phase 4-W05: ADD Feature File - frontdesk-web/帳號與登入/介面語言.feature

**Goal**: 沒人選過時跟瀏覽器、登入頁語言選單、每人一筆記在裝置上、沒選過的人沿用登入頁語言、登出後停在上一位的語言、不跟到別台裝置、分頁之間共用、關掉瀏覽器後還在、帳號面板語言頁、只換介面文字不換輸入的內容。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/帳號與登入/介面語言.feature` -> `功能: 介面語言` 全文（14 條 Rule）
- `specs/truth/features/frontdesk-web/帳號與登入/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 29 句（T188、T198、T200–T203、T205、T208–T212、T216–T219、T224、T226–T228、T263–T271）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 5 句（T139–T140、T153–T154、T160）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/帳號與登入/介面語言.feature` 那一列
- `ui/ui-plan.md` -> 畫面 1 的語言選單、畫面 4 的語言頁、「重新整理後保留什麼」表的介面語言列
- `ui/index.html`、`ui/board.html`（帳號面板的語言頁） -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：無（純前端）
- 產品碼落點：`app/web/src/` 的 `i18n/`、`lib/browser-language.ts`、`features/account/`

**Boundary**:
- 語言選擇存 `localStorage`，以員工編號為鍵、每人一筆，另記一筆登入頁現在的語言；與登入憑證分開。沒人選過時跟瀏覽器。只換介面文字，不換使用者輸入的內容與單上的文字。
- 四種語言的字串都要有；雛形沒有譯文的句子自己補齊，繁體中文以 feature 的字面為準。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/帳號與登入/介面語言.feature`

- [X] T480 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T481 [BDD-REFACTOR] 在綠燈下替 `browser-language.ts` 補 `Vitest`，並檢查四份資源檔的鍵一致

## Phase 4-W06: ADD Feature File - frontdesk-web/共用外框/側欄與工具列.feature

**Goal**: 側欄的內容、待派單徽章、兩頁切換時外框不變、側欄各頁的預設、打開與收合、自己選過之後維持。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/共用外框/側欄與工具列.feature` -> `功能: 側欄與工具列` 全文（7 條 Rule）
- `specs/truth/features/frontdesk-web/共用外框/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 6 句（T275–T276、T279–T281、T283）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 12 句（T139–T142、T151、T153、T155、T161–T162、T174–T176）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/共用外框/側欄與工具列.feature` 那一列
- `ui/ui-plan.md` -> 畫面 3「共用外框」
- `ui/board.html`、`ui/triage.html` -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`getDeskBoard`（待派單徽章的張數）（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `shell/`

**Boundary**:
- 側欄、工具列掛在外框，兩頁切換時不重建。側欄各頁有預設（總覽看板收起、待派單頁打開），自己按過之後維持自己的選擇。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/共用外框/側欄與工具列.feature`

- [X] T482 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T483 [BDD-REFACTOR] 在綠燈下把側欄開合的預設與個人選擇收成一個 selector

## Phase 4-W07: ADD Feature File - frontdesk-web/共用外框/尚未開放的入口.feature

**Goal**: 「通知板」「遺失物」「代開單」點了沒反應、只有主管與值班主管有「設定」、「設定」點了不展開。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/共用外框/尚未開放的入口.feature` -> `功能: 尚未開放的入口` 全文（3 條 Rule）
- `specs/truth/features/frontdesk-web/共用外框/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 3 句（T277–T278、T282）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 5 句（T139–T141、T153、T173）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/共用外框/尚未開放的入口.feature` 那一列
- `ui/ui-plan.md` -> 畫面 3「共用外框」、「狀態與資訊揭露」
- `ui/board.html`（`?as=F0101` 與 `?as=M0001`） -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：登入回應的 `role`（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `shell/`

**Boundary**:
- 「通知板」「遺失物」「代開單」點了沒反應；「設定」只有 `manager` 與 `duty_manager` 看得到，點了不展開。不做任何「即將推出」之類的提示，除非 feature 有寫。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/共用外框/尚未開放的入口.feature`

- [X] T484 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T485 [BDD-REFACTOR] 在綠燈下把尚未開放的入口集中成一份清單

## Phase 4-W08: ADD Feature File - frontdesk-web/總覽看板/看板分欄與範圍.feature

**Goal**: 8 個欄與版面順序、每種狀況落在哪一欄、當場回答在「已結案」、只顯示今天結束的、還沒結束的不限日期、頁副標未結案張數、空欄文字、欄內順序。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/總覽看板/看板分欄與範圍.feature` -> `功能: 看板分欄與範圍` 全文（8 條 Rule）
- `specs/truth/features/frontdesk-web/總覽看板/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 7 句（T309–T315）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 5 句（T137、T139、T151、T177、T186）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/總覽看板/看板分欄與範圍.feature` 那一列
- `ui/ui-plan.md` -> 畫面 5「總覽看板」、「給 `/api-plan` 的資料需求」的看板清單
- `ui/board.html`（含 `&load=empty`） -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`getDeskBoard`（`DeskBoard`、`DeskTask`）（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `pages/board/`、`lib/board-columns.ts`

**Boundary**:
- 固定 8 欄；每種狀況落在哪一欄照 `specs/truth/features/frontdesk-web/dsl.md` 的「單的狀況對照」。範圍由後端給，畫面不再用日期過濾；欄內順序由畫面排。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/總覽看板/看板分欄與範圍.feature`

- [X] T486 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T487 [BDD-REFACTOR] 在綠燈下替 `board-columns.ts` 補 `Vitest`（每種狀況一條、未結案張數一條）

## Phase 4-W09: ADD Feature File - frontdesk-web/總覽看板/數量卡與欄頭.feature

**Goal**: 四張數量卡、「等最久」的挑選範圍與顏色、點它是開詳情、沒有在等的單時的樣子、欄頭的數量與兩種警示。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/總覽看板/數量卡與欄頭.feature` -> `功能: 數量卡與欄頭` 全文（10 條 Rule）
- `specs/truth/features/frontdesk-web/總覽看板/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 7 句（T299、T316–T318、T320–T322）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 5 句（T139–T140、T151、T179–T180）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/總覽看板/數量卡與欄頭.feature` 那一列
- `ui/ui-plan.md` -> 畫面 5 的數量卡與欄頭
- `ui/board.html` -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`getDeskBoard`（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `pages/board/`、`lib/board-columns.ts`

**Boundary**:
- 四張數量卡、欄頭數量、待派單徽章都從同一份看板資料算。「等最久」點了是開詳情，不是篩選。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/總覽看板/數量卡與欄頭.feature`

- [X] T488 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T489 [BDD-REFACTOR] 在綠燈下把數量卡與欄頭共用的計數收成 selector

## Phase 4-W10: ADD Feature File - frontdesk-web/總覽看板/卡片內容.feature

**Goal**: 部門、類型、來源與 VIP 標、住客打字的標題與翻譯、副標的五種情形、本人暫停與本人延後的副標、SOP 進度、負責人頭像。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/總覽看板/卡片內容.feature` -> `功能: 卡片內容` 全文（9 條 Rule）
- `specs/truth/features/frontdesk-web/總覽看板/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 5 句（T323–T327）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 3 句（T139、T151、T178）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/總覽看板/卡片內容.feature` 那一列
- `ui/ui-plan.md` -> 畫面 5 的卡片
- `ui/board.html` -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`DeskTask`、`DeskGuestText`（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `pages/board/`（卡片元件）

**Boundary**:
- 有既有翻譯才顯示「已翻譯」與原文；不接翻譯服務。主管延後只顯示誰延後的，不顯示時間與原因。不顯示急件。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 本檔有 3 個 `# [need clarification]`（待使用者確認的預設值），照 feature 目前寫的值實作，不停下來問。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/總覽看板/卡片內容.feature`

- [X] T490 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T491 [BDD-REFACTOR] 在綠燈下把卡片副標的五種情形收成一個純函式並補 `Vitest`

## Phase 4-W11: ADD Feature File - frontdesk-web/總覽看板/等待膠囊與逾時.feature

**Goal**: 待認領 10 分起逾時、待派單只寫等幾分、已接與進行中不判斷逾時、等待的起算時間、四處用同一個時間、不顯示急件、後四欄的膠囊。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/總覽看板/等待膠囊與逾時.feature` -> `功能: 等待膠囊與逾時` 全文（9 條 Rule）
- `specs/truth/features/frontdesk-web/總覽看板/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 6 句（T317、T319、T322、T328–T330）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 3 句（T138–T139、T151）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/總覽看板/等待膠囊與逾時.feature` 那一列
- `ui/ui-plan.md` -> 畫面 5 的等待膠囊、「給 `/api-plan` 的資料需求」看板清單（`serverTime`）
- `ui/board.html` -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`DeskTask.waitingSince` 等時刻、`DeskBoard.serverTime`（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `lib/wait-pill.ts`、`lib/server-clock.ts`

**Boundary**:
- 只有待認領判斷逾時（10 分起）；待派單只寫等幾分；已接與進行中不判斷逾時。「現在」用後端的 `serverTime` 校正，不直接信瀏覽器時鐘；四個地方（膠囊、數量卡、頁副標、詳情）用同一個時間。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/總覽看板/等待膠囊與逾時.feature`

- [X] T492 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T493 [BDD-REFACTOR] 在綠燈下替 `wait-pill.ts` 補 `Vitest`（0、1、9、10、12、20、23 分各一條）

## Phase 4-W12: ADD Feature File - frontdesk-web/總覽看板/篩選搜尋與泳道.feature

**Goal**: 數量卡篩選、房號開頭比對、只收數字最多 4 碼、兩種篩選併成一顆膠囊、點膠囊全部清掉、不影響未結案張數、換頁後的保留規則、三種泳道與副標。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/總覽看板/篩選搜尋與泳道.feature` -> `功能: 篩選搜尋與泳道` 全文（15 條 Rule）
- `specs/truth/features/frontdesk-web/總覽看板/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 19 句（T294–T296、T299–T302、T320、T331–T341）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 6 句（T139–T140、T142、T144、T155、T180）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/總覽看板/篩選搜尋與泳道.feature` 那一列
- `ui/ui-plan.md` -> 畫面 5 的看板控制列
- `ui/board.html` -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`DeskBoard.floors`（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `pages/board/`、`stores/board-view.ts`

**Boundary**:
- 篩選、房號搜尋、泳道是畫面狀態，不送後端。兩種篩選併成一顆膠囊、點它全部清掉；不影響未結案張數。房號只收數字、最多 4 碼、開頭比對。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/總覽看板/篩選搜尋與泳道.feature`

- [X] T494 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T495 [BDD-REFACTOR] 在綠燈下把篩選、搜尋、泳道三者的組合收成一個 selector 並補 `Vitest`

## Phase 4-W13: ADD Feature File - frontdesk-web/總覽看板/載入與讀取失敗.feature

**Goal**: 載入中是骨架、讀取失敗的訊息與「重試」、每 5 秒自動再試。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/總覽看板/載入與讀取失敗.feature` -> `功能: 載入與讀取失敗` 全文（4 條 Rule）
- `specs/truth/features/frontdesk-web/總覽看板/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 3 句（T298、T308、T348）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 12 句（T137、T139、T149–T153、T158–T159、T161、T177、T186）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/總覽看板/載入與讀取失敗.feature` 那一列
- `ui/ui-plan.md` -> 畫面 5 的載入中與讀取失敗、「狀態與資訊揭露」
- `ui/board.html`（`&load=loading`、`&load=error`） -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`getDeskBoard`（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `pages/board/`、`api/`（`TanStack Query` 的重試設定）

**Boundary**:
- 載入中是骨架；讀取失敗顯示訊息與「重試」，並每 5 秒自動再試。這兩種情形下畫面上不得出現任何空欄文字或空狀態。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 本檔有 4 個 `# [need clarification]`（待使用者確認的預設值），照 feature 目前寫的值實作，不停下來問。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/總覽看板/載入與讀取失敗.feature`

- [X] T496 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T497 [BDD-REFACTOR] 在綠燈下把骨架與讀取失敗訊息做成看板與待派單頁共用的元件

## Phase 4-W14: ADD Feature File - frontdesk-web/待派單/待派單清單.feature

**Goal**: 清單順序、頁副標、頁首與頁尾、每一列的內容、不到 1 分、翻譯與原文列、VIP 與空房、只有三顆動作、沒有建議文字、點列開詳情、「當場回答…」開面板、直接進部門的需求不會來。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/待派單/待派單清單.feature` -> `功能: 待派單清單` 全文（16 條 Rule）
- `specs/truth/features/frontdesk-web/待派單/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 11 句（T354–T364）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 16 句（T138–T140、T151、T154、T156、T158–T161、T169、T174、T179、T181、T185–T186）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/待派單/待派單清單.feature` 那一列
- `ui/ui-plan.md` -> 畫面 6「待派單頁、派單浮條與復原」、「給 `/api-plan` 的資料需求」的待派單清單
- `ui/triage.html` -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`getDeskBoard`（待派單頁共用同一份）（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `pages/triage/`

**Boundary**:
- 清單最新進來的在最上面，由畫面排。每列只有三顆動作（給房務、給工務、當場回答…）；沒有建議文字、系統不自己派。來源照單的實際來源顯示。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/待派單/待派單清單.feature`

- [X] T498 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T499 [BDD-REFACTOR] 在綠燈下把需求列做成獨立元件，文字組法各自是純函式

## Phase 4-W15: ADD Feature File - frontdesk-web/待派單/按鈕派單與復原.feature

**Goal**: 點部門就送出、浮條、徽章、操作者是登入的人（三種角色）、不拆單、5 秒內復原回原位、超過 5 秒沒有復原、浮條換成新的那一張、復原失敗兩種、復原沒送成功。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/待派單/按鈕派單與復原.feature` -> `功能: 按鈕派單與復原` 全文（12 條 Rule）
- `specs/truth/features/frontdesk-web/待派單/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 5 句（T349–T350、T353、T367、T369）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 18 句（T139–T140、T147–T148、T153、T156、T161、T164–T171、T174、T184–T185）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/待派單/按鈕派單與復原.feature` 那一列
- `ui/ui-plan.md` -> 畫面 6 的派單浮條與復原、「驗證與錯誤回饋」
- `ui/triage.html`（含 `&undo=claimed`、`&undo=withdrawn`、`&undo=net`） -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`deskAssignTask`（`via`=`button`）、`deskUndoAssign`（四個 `undo_*`）（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `features/dispatch/`（三個入口共用的送出、浮條、復原、錯誤處理）

**Boundary**:
- 點部門就送出，不再確認。浮條與「復原」只出現 5 秒；又派了一張就換成新的那一張。復原失敗分已被認領、已被住客收回兩種提示；復原沒送成功另有對話框。每次寫入都帶新的 `Idempotency-Key`。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 本檔有 3 個 `# [need clarification]`（待使用者確認的預設值），照 feature 目前寫的值實作，不停下來問。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/待派單/按鈕派單與復原.feature`

- [X] T500 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T501 [BDD-REFACTOR] 在綠燈下確認按鈕、拖曳、詳情面板三個派單入口走同一個 hook

## Phase 4-W16: ADD Feature File - frontdesk-web/待派單/空狀態與載入.feature

**Goal**: 兩種空狀態與今日張數、被復原的不算、頁副標、空的時候沒有頁首頁尾、載入中與讀取失敗不顯示成沒有單。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/待派單/空狀態與載入.feature` -> `功能: 空狀態與載入` 全文（8 條 Rule）
- `specs/truth/features/frontdesk-web/待派單/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 4 句（T351–T352、T365–T366）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 8 句（T139、T149–T152、T158–T159、T186）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/待派單/空狀態與載入.feature` 那一列
- `ui/ui-plan.md` -> 畫面 6 的兩種空狀態、「狀態與資訊揭露」
- `ui/triage.html`（`&load=done`、`&load=empty`、`&load=loading`、`&load=error`） -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`DeskBoard.myToday`（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `pages/triage/`

**Boundary**:
- 兩種空狀態的文字與今日張數（被復原的派單不算）由後端的 `myToday` 給。載入中與讀取失敗不得顯示成沒有單。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 本檔有 5 個 `# [need clarification]`（待使用者確認的預設值），照 feature 目前寫的值實作，不停下來問。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/待派單/空狀態與載入.feature`

- [X] T502 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T503 [BDD-REFACTOR] 在綠燈下沿用 Phase 4-W13 的共用骨架與讀取失敗元件，拿掉重複

## Phase 4-W17: ADD Feature File - frontdesk-web/待派單/撞單與送出失敗.feature

**Goal**: 被另一台派走、被另一台回答、住客收回三種撞單的對話框與畫面更新；送出失敗時列留在原位、徽章不變。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/待派單/撞單與送出失敗.feature` -> `功能: 撞單與送出失敗` 全文（6 條 Rule）
- `specs/truth/features/frontdesk-web/待派單/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 1 句（T368）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 19 句（T139–T140、T145–T148、T153、T156、T162、T164、T167–T169、T171–T172、T174、T181、T184–T185）
- `specs/truth/features/frontdesk-web/當場回答/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 5 句（T371、T374–T375、T383、T387）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/待派單/撞單與送出失敗.feature` 那一列
- `ui/ui-plan.md` -> 畫面 6 的撞單提示、「驗證與錯誤回饋」
- `ui/triage.html`（`&fail=assigned`、`&fail=answered`、`&fail=withdrawn`、`&fail=net`） -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`deskAssignTask` 的 409 `task_not_in_triage`（`context.resolution`）（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `features/dispatch/`

**Boundary**:
- 三種撞單各有自己的對話框文字，按「好」之後畫面更新成最新的樣子。送出失敗（網路中斷）時列留在原位、徽章不變、沒有浮條。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 本檔有 4 個 `# [need clarification]`（待使用者確認的預設值），照 feature 目前寫的值實作，不停下來問。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/待派單/撞單與送出失敗.feature`

- [X] T504 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T505 [BDD-REFACTOR] 在綠燈下把撞單對話框的文字組法收成一個吃 `context` 的純函式

## Phase 4-W18: ADD Feature File - frontdesk-web/當場回答/當場回答面板.feature

**Goal**: 面板的內容、常用回覆帶入與選中的樣子、空白不能送、最多 120 字、送出後結案並從清單消失、去掉前後空白、沒有復原、看板上的位置與副標、取消不變。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/當場回答/當場回答面板.feature` -> `功能: 當場回答面板` 全文（13 條 Rule）
- `specs/truth/features/frontdesk-web/當場回答/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 17 句（T370–T373、T376–T388）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 18 句（T139–T140、T153、T155–T156、T158、T160–T163、T167、T169、T174、T177–T178、T181、T184–T185）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/當場回答/當場回答面板.feature` 那一列
- `ui/ui-plan.md` -> 畫面 7「當場回答面板」
- `ui/triage.html?answer=T-1054` -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`listDeskQuickReplies`、`deskAnswerTask`（`answer_invalid`）（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `features/answer/`

**Boundary**:
- 常用回覆點了是帶入輸入框。空白不能送、最多 120 字；送出前去掉前後空白。送出後結案、從清單消失，沒有復原。取消不改任何東西。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/當場回答/當場回答面板.feature`

- [X] T506 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T507 [BDD-REFACTOR] 在綠燈下把回覆輸入的規則收成 hook，字數檢查與後端同一把尺

## Phase 4-W19: ADD Feature File - frontdesk-web/當場回答/撞單與送出失敗.feature

**Goal**: 當場回答送出時的三種撞單、按「好」後面板關閉、送出失敗時單不變、面板與已填的回覆都留著。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/當場回答/撞單與送出失敗.feature` -> `功能: 撞單與送出失敗` 全文（6 條 Rule）
- `specs/truth/features/frontdesk-web/當場回答/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 5 句（T371、T374–T375、T383、T387）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 19 句（T139–T140、T145–T148、T153、T156、T162、T164、T167–T169、T171–T172、T174、T181、T184–T185）
- `specs/truth/features/frontdesk-web/待派單/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 1 句（T368）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/當場回答/撞單與送出失敗.feature` 那一列
- `ui/ui-plan.md` -> 畫面 7 的撞單與送出失敗、「驗證與錯誤回饋」
- `ui/triage.html?answer=T-1054`（加 `&fail=`） -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`deskAnswerTask` 的 409 `task_not_in_triage`（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `features/answer/`、`features/dispatch/`

**Boundary**:
- 三種撞單按「好」後面板關閉。送出失敗時單不變，面板與已填的回覆都留著。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 本檔有 6 個 `# [need clarification]`（待使用者確認的預設值），照 feature 目前寫的值實作，不停下來問。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/當場回答/撞單與送出失敗.feature`

- [X] T508 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T509 [BDD-REFACTOR] 在綠燈下與 Phase 4-W17 共用撞單對話框

## Phase 4-W20: ADD Feature File - frontdesk-web/單筆詳情/詳情面板內容.feature

**Goal**: 面板滑出與側欄收起、任何欄的卡片都能點開、版面順序、單號、「內容」六列、不顯示人數、建立時間寫法、負責人與空房、翻譯與原文列、清潔 SOP、來源列、沒有急件標記、關閉、讓位給側欄。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/單筆詳情/詳情面板內容.feature` -> `功能: 詳情面板內容` 全文（16 條 Rule）
- `specs/truth/features/frontdesk-web/單筆詳情/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 13 句（T390–T402）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 12 句（T138–T141、T144、T153–T154、T159、T175、T179–T180、T182）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/單筆詳情/詳情面板內容.feature` 那一列
- `ui/ui-plan.md` -> 畫面 8「單筆詳情面板」、「給 `/api-plan` 的資料需求」的單筆詳情與歷程
- `ui/board.html?task=`（各單號見 `ui/ui-plan.md` 的主要路徑對照） -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`readDeskTask`（`DeskTaskDetail`）（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `features/detail/`

**Boundary**:
- 任何欄的卡片都能點開。單號顯示 `displayNo`。不顯示住客人數、不顯示急件。面板滑出時側欄收起，關閉後讓位回去。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 本檔有 1 個 `# [need clarification]`（待使用者確認的預設值），照 feature 目前寫的值實作，不停下來問。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/單筆詳情/詳情面板內容.feature`

- [X] T510 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T511 [BDD-REFACTOR] 在綠燈下把「內容」六列的文字組法收成純函式

## Phase 4-W21: ADD Feature File - frontdesk-web/單筆詳情/狀態標籤與狀態訊息卡.feature

**Goal**: 十二種已定的標籤與訊息卡、待認領、已接的四種說明、待派單沒有訊息卡、本人暫停、本人延後、另外兩種取消、免查房，以及面板開著時認領與完成的即時更新。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/單筆詳情/狀態標籤與狀態訊息卡.feature` -> `功能: 狀態標籤與狀態訊息卡` 全文（11 條 Rule）
- `specs/truth/features/frontdesk-web/單筆詳情/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 5 句（T403–T407）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 7 句（T138–T140、T144、T154、T157、T183）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/單筆詳情/狀態標籤與狀態訊息卡.feature` 那一列
- `ui/ui-plan.md` -> 畫面 8 的狀態標籤與狀態訊息卡
- `ui/board.html?task=`（21 個示範單號） -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`DeskTaskDetail`（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `features/detail/`、`lib/status-card.ts`

**Boundary**:
- 標籤與訊息卡由一個純函式算出，輸入是單、輸出是文字與色調。面板開著時，認領與完成等變化要即時更新。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 本檔有 4 個 `# [need clarification]`（待使用者確認的預設值），照 feature 目前寫的值實作，不停下來問。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/單筆詳情/狀態標籤與狀態訊息卡.feature`

- [X] T512 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T513 [BDD-REFACTOR] 在綠燈下替 `status-card.ts` 補 `Vitest`（每一種標籤與訊息卡一條）

## Phase 4-W22: ADD Feature File - frontdesk-web/單筆詳情/歷程.feature

**Goal**: 歷程的標題與每筆的樣子、按鈕與拖曳派單的句子、當場回答、住客收回、其他已定案的句子、被復原的派單不出現、只增不改、暫定句子、不是今天的寫日期。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/單筆詳情/歷程.feature` -> `功能: 歷程` 全文（9 條 Rule）
- `specs/truth/features/frontdesk-web/單筆詳情/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 7 句（T389、T409–T414）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 7 句（T137–T140、T144、T154、T157）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/單筆詳情/歷程.feature` 那一列
- `ui/ui-plan.md` -> 畫面 8 的歷程、「找不到出處的文字」、「待決事項」第 1 項
- `ui/board.html?task=` -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`DeskTimelineEntry`（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `features/detail/`、`lib/timeline-sentence.ts`

**Boundary**:
- 句子由畫面依事件種類與 `detail` 組；feature 沒有寫句子的事件種類略過不顯示，不自己編（Scope Note 第 13 條）。被復原的派單後端已經略過，畫面不再處理。不是今天的事件寫日期。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 本檔有 2 個 `# [need clarification]`（待使用者確認的預設值），照 feature 目前寫的值實作，不停下來問。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/單筆詳情/歷程.feature`

- [X] T514 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T515 [BDD-REFACTOR] 在綠燈下替 `timeline-sentence.ts` 補 `Vitest`（每一種已定案的句子一條）

## Phase 4-W23: ADD Feature File - frontdesk-web/單筆詳情/派單區與單據管理動作列.feature

**Goal**: 待派單的派單區三格、從面板派單與之後的樣子、「當場回答…」開面板、其他狀態沒有派單區、動作列依狀態與角色、點了沒反應、主管取消與延後櫃檯只看得到。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/單筆詳情/派單區與單據管理動作列.feature` -> `功能: 派單區與單據管理動作列` 全文（8 條 Rule）
- `specs/truth/features/frontdesk-web/單筆詳情/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 8 句（T403、T408、T411、T415–T419）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 13 句（T139–T140、T144、T153–T154、T159、T165–T166、T168、T170、T173、T179、T181）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/單筆詳情/派單區與單據管理動作列.feature` 那一列
- `ui/ui-plan.md` -> 畫面 8 的派單區與底部動作列
- `ui/board.html?task=T-1053`（待派單）、`?as=M0001&task=`（主管看到的動作列） -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`deskAssignTask`、登入回應的 `role`（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `features/detail/`、`features/dispatch/`

**Boundary**:
- 只有待派單的單有派單區；從面板派單走同一個派單 hook。單據管理動作列依狀態與角色顯示，但都只畫不通：點了沒反應。櫃檯 Web 的寫入只有派單、復原、當場回答三種。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/單筆詳情/派單區與單據管理動作列.feature`

- [X] T516 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T517 [BDD-REFACTOR] 在綠燈下把動作列「誰在什麼狀態看得到哪幾顆」做成一張對照表

## Phase 4-W24: ADD Feature File - frontdesk-web/總覽看板/拖曳派單.feature

**Goal**: 平時與拖曳中的提示、拖放區與說明、放開就派單並註明拖曳、浮條、5 秒內復原、在別處放開沒事、其他欄拖不動、拖放區跟著泳道、用手指拖、撞單、送出失敗。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/總覽看板/拖曳派單.feature` -> `功能: 拖曳派單` 全文（14 條 Rule）
- `specs/truth/features/frontdesk-web/總覽看板/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 13 句（T296–T297、T303–T307、T342–T347）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 14 句（T139–T140、T143、T145、T148、T151、T153、T164、T166–T170、T177）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/總覽看板/拖曳派單.feature` 那一列
- `ui/ui-plan.md` -> 畫面 5 的拖曳派單、「待決事項」第 4 項
- `ui/board.html`（拖 305 的卡片到「給工務」；切泳道再拖一次） -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`deskAssignTask`（`via`=`drag`）、`deskUndoAssign`（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `pages/board/`（拖曳層）、`features/dispatch/`

**Boundary**:
- 用 T004 選定的拖曳套件，滑鼠與觸控同一套程式；不用瀏覽器原生拖放。只有待派單的卡片拖得動；在拖放區以外放開沒事。拖放區跟著泳道。放開就派單（`via`=`drag`），之後的浮條、復原、撞單、送出失敗與按鈕派單相同。
- 觸控那一組怎麼驅動照 T005 的結論（Scope Note 第 14 條）。只看「待派單」時沒有落點，不特別處理。
- 「用手指拖」的案例寫了 `用的是 "iPad 橫向 1180 × 820 觸控"`，不論跑哪一組都會用觸控設定跑，所以本 phase 的 GREEN 就包含它。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 本檔有 1 個 `# [need clarification]`（待使用者確認的預設值），照 feature 目前寫的值實作，不停下來問。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/總覽看板/拖曳派單.feature`

- [X] T518 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T519 [BDD-REFACTOR] 在綠燈下把拖曳層與派單 hook 的接點收成一個元件，拖曳中的提示狀態放 `Zustand`

## Phase 4-W25: ADD Feature File - frontdesk-web/即時更新/現場變化即時跟上.feature

**Goal**: 住客送單、另一台派單、同仁認領開始完成、主管查房通過、住客收回時，看板、徽章、數量卡與待派單清單在 5 秒內跟上。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/即時更新/現場變化即時跟上.feature` -> `功能: 現場變化即時跟上` 全文（8 條 Rule）
- `specs/truth/features/frontdesk-web/即時更新/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 8 句（T422–T424、T430–T434）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 4 句（T139–T140、T157、T183）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/即時更新/現場變化即時跟上.feature` 那一列
- `ui/ui-plan.md` -> 畫面 9「即時更新與連線異常提醒列」、「給 `/api-plan` 的資料需求」的事件串流
- `ui/board.html?live=1` -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`staffStream`（`init`、`resync`、`task`）、`getDeskBoard`、`readDeskTask`（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `features/realtime/`

**Boundary**:
- 以 `fetch` 讀 `GET /staff/stream`（帶 `Authorization`），不用 `EventSource`、不用 `WebSocket`。串流只送「哪張單有變化」，收到就重抓看板（面板開著時連詳情一起），不自己拼資料。5 秒內跟上。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 本檔有 8 個 `# [need clarification]`（待使用者確認的預設值），照 feature 目前寫的值實作，不停下來問。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/即時更新/現場變化即時跟上.feature`

- [X] T520 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T521 [BDD-REFACTOR] 在綠燈下把串流客戶端（解析、重連）與「收到後重抓什麼」分成兩層

## Phase 4-W26: ADD Feature File - frontdesk-web/即時更新/連線異常提醒列.feature

**Goal**: 三種情形出現提醒列、沒有按鈕、期間每 5 秒更新且照常可以派單、恢復後自己消失並回到即時、重讀失敗留著舊資料。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/即時更新/連線異常提醒列.feature` -> `功能: 連線異常提醒列` 全文（7 條 Rule）
- `specs/truth/features/frontdesk-web/即時更新/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 12 句（T420–T421、T425–T430、T435–T438）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 10 句（T139–T140、T144、T153、T157、T161、T166、T168、T177、T183）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/即時更新/連線異常提醒列.feature` 那一列
- `ui/ui-plan.md` -> 畫面 9 的提醒列、「狀態與資訊揭露」
- `ui/board.html?conn=down`、`&conn=recover` -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：`staffStream`（`heartbeat.degraded`、`degraded`、`recovered`）（`specs/truth/contracts/groups/` 與 `components/schemas/` 的同名定義）
- 產品碼落點：`app/web/src/` 的 `features/realtime/`、`shell/`（提醒列的位置）

**Boundary**:
- 三種情形出現提醒列：串流斷線、約兩個心跳週期沒有資料、後端宣告降級。提醒列沒有按鈕；期間每 5 秒重抓，照常可以派單；恢復後提醒列自己消失、停止輪詢。重讀失敗時留著舊資料。
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 本檔有 2 個 `# [need clarification]`（待使用者確認的預設值），照 feature 目前寫的值實作，不停下來問。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/即時更新/連線異常提醒列.feature`

- [X] T522 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T523 [BDD-REFACTOR] 在綠燈下把連線狀態收成一個狀態機並補 `Vitest`

## Phase 4-W27: ADD Feature File - frontdesk-web/共用外框/支援的視窗與輸入方式.feature

**Goal**: 兩種尺寸下五個畫面都不用縮放與整頁捲動、「還有 N 個狀態」與往右捲一欄、捲到底消失、視窗變寬欄跟著變寬、比支援的小就出現捲軸、只用手指能派單、沒有只靠游標才看得到的東西。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/共用外框/支援的視窗與輸入方式.feature` -> `功能: 支援的視窗與輸入方式` 全文（8 條 Rule）
- `specs/truth/features/frontdesk-web/共用外框/dsl.md` -> 模組說明與用語對照；本檔用到、Phase 3 已對齊的 13 句（T272–T274、T284–T293）
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」「單的狀況對照」；本檔用到、Phase 3 已對齊的 8 句（T139–T140、T143、T151、T153、T156、T166、T168）
- `truth-delta.md` -> `/dsl-refine` 櫃檯 Web ADD `fw/共用外框/支援的視窗與輸入方式.feature` 那一列
- `ui/ui-plan.md` -> 「版面尺寸與伸縮規則」、畫面 3、畫面 5 的版面
- `ui/index.html`、`ui/change-password.html`、`ui/board.html`、`ui/triage.html`、`ui/desk.css` -> 對應的雛形（版面、文字位置、顏色、互動）
- 契約：無（版面）
- 產品碼落點：`app/web/src/` 的 `shell/`、`pages/board/`、全域樣式

**Boundary**:
- 電腦 1280 × 720 與 iPad 橫向 1180 × 820 下，五個畫面都不用縮放、不出現整頁捲動；視窗更寬時看板的欄跟著變寬；比支援的小就出現捲軸，不另外設計。可點的東西不小於 44px。沒有只靠游標停留才看得到的東西。
- **本 phase 的 `[BDD-GREEN]` 要 `desktop` 與 `ipad` 兩組都綠。**
- 畫面對齊上面點名的雛形；畫面文字一律走 `i18n/` 資源檔，繁體中文的字面照 feature，不得出現 `triage`、`pool` 這類內部值或錯誤碼。
- 本檔有 1 個 `# [need clarification]`（待使用者確認的預設值），照 feature 目前寫的值實作，不停下來問。
- 只讓這個 Test Scope 全綠（`desktop` 那一組）；不改 truth、不改 stepdef 的語意。後端行為不對回對應的 Phase 4-B 修。

**Test Scope**:
- `specs/truth/features/frontdesk-web/共用外框/支援的視窗與輸入方式.feature`

- [X] T524 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T525 [BDD-REFACTOR] 在綠燈下把尺寸相關的數字收成設計變數，不留散落的固定寬度

## Phase 4-G: ui quality gate

**Goal**: 產品對齊本輪回追到的 plan-side 雛形家族。`Test Scope` 全綠不能代替本題。

**Read**:
- `ui/ui-plan.md` -> 視覺方向、版面尺寸與伸縮規則、畫面與流程 1～9、互動與假資料原則、狀態與資訊揭露、驗證與錯誤回饋、靜態雛形輸出規劃的主要路徑對照、前端實作切分建議、與原型不同的地方
- `ui/index.html`、`ui/change-password.html`、`ui/board.html`、`ui/triage.html`、`ui/desk.css`、`ui/desk.js`
- `app/web/src/pages/`、`app/web/src/shell/`、`app/web/src/features/`、`app/web/src/i18n/`

**Boundary**:
- 不改 truth、不加 feature、不重跑 GREEN 當通過條件。
- 兩組視窗（1280 × 720 與 1180 × 820）各看一次。重點比對：登入頁的飯店名稱、三種提示卡與語言選單；改密碼畫面四條規則的即時提示；側欄、工具列、提醒列的位置與側欄開合；四張數量卡、8 欄的寬度與欄頭警示；卡片上各種標的位置與顏色、等待膠囊的三種色調；拖曳中的提示與拖放區；待派單頁每列的三顆動作、派單浮條與「復原」；四種撞單對話框與兩種復原失敗提示；當場回答面板的常用回覆與字數；詳情面板的版面順序、21 個示範單號各自的狀態標籤與訊息卡、歷程的色點、兩種角色的動作列；骨架、讀取失敗、兩種空狀態；連線異常提醒列。
- 雛形沒有做出來的狀態（`ui/ui-plan.md`「靜態雛形輸出規劃」最後一段列的那幾項）以 `ui/ui-plan.md` 的文字為準。雛形的示範資料與 truth 預設不同之處以 truth 為準（Scope Note 第 12 條），不算沒對齊。
- 用語以 truth 為準；畫面上不得出現內部值、錯誤碼，也不得出現急件、延後到幾點、住客人數這些本輪明定不顯示的東西。

- [X] T526 subagent review (ui quality gate)

## Phase 4-R: REGRESSION - 004 涉及的全部 feature

**Goal**: 後端、櫃檯 Web 兩組瀏覽器設定、員工 App 測試線各跑一輪，確認各 phase 之間沒有互相弄紅，搬動 `BackendChannel` 與改登入回應、角色放行、串流收件範圍沒有弄壞手機 App。

**Boundary**:
- 只跑、只修回歸；不加 feature、不改 truth。紅了回到對應 phase 修，修完重跑整輪。
- 真機 iPad 的觸控手感（按住起拖的延遲、拖曳與捲動會不會互搶、可點的東西是否至少 44 pt 見方）不在自動化範圍，要人工驗一次，結果記在 `implement-notes.md`。

**Test Scope**:
- 後端：`specs/truth/features/backend/櫃檯看板與派單/`（7 份）、`員工事件串流/`（2 份）、`帳號與登入/`（4 份）、`主管工作台/主管待辦空清單與權限.feature`、`推播/員工推播事件.feature`；另跑一次不帶 `-Dcucumber.features` 的後端全套件（含 `待認領/`、`任務執行/`、`工務單/`、`查房/` 這些 NOOP 模組，確認 `ended_at`、`task_no` 與角色放行沒有弄紅它們）與舊測試線 `RunCucumberTest`。
- 櫃檯 Web：`specs/truth/features/frontdesk-web/` 全部 27 份，`desktop` 與 `ipad` 各一輪；另跑 `cd app/web && npx vitest run` 與 `npm run build`。
- 員工 App：`specs/truth/features/ios-app/` 的 `帳號與登入/`、`待認領/`、`主管工作台/` 各跑一輪（登入回應多了欄位、`BackendChannel` 搬了家、`duty_manager` 放行都可能波及）；另跑 `app/mobile` 的 `flutter test`。

- [X] T527 [REGRESSION] 跑後端 Test Scope、後端全套件與舊測試線，確認全綠
- [X] T528 [REGRESSION] 跑櫃檯 Web 27 份 feature 的 `desktop` 一輪與 `Vitest`、建置，確認全綠
- [X] T529 [REGRESSION] 跑櫃檯 Web 27 份 feature 的 `ipad` 一輪，確認全綠；觸控拖曳照 T005 的結論
- [ ] T530 [REGRESSION] 跑員工 App 測試線三個模組與 `flutter test`，確認 `BackendChannel` 搬家與後端改動沒有弄壞手機 App
