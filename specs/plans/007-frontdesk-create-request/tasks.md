# Tasks: 007-frontdesk-create-request（櫃檯代開單）

**Plan Package**: `specs/plans/007-frontdesk-create-request`
**Core Inputs**: `spec.md`, `plan.md`, `research.md`, `truth-delta.md`, `ui/ui-plan.md`, `ui/*.html`, `ui/desk.css`, `ui/desk.js`, `ui/create-request.css`, `ui/create-request.js`, `specs/truth/techstack.md`, `specs/truth/contracts/**`, `specs/truth/data/data-model.dbml`, `specs/truth/features/backend/**`, `specs/truth/features/frontdesk-web/**`, `specs/truth/features/guest-web/**`

本檔承接 `truth-delta.md` 四節：`/technical-research`（沒有新套件；冪等在業務交易內加鎖、櫃檯 Web 送出失敗的模擬、Vitest 範圍）、`/api-plan`（`POST /desk/requests`、`GET /desk/rooms`、`DeskMenuKey`、`DeskTask.menuKey`、`createRepairTicket` 只補說明、住客每房額度只數住客、分類規則選填 `menu_key`）、`/data-plan`（`tasks.note` 品項鍵、櫃檯代開段、`repair_tickets` 依開單路徑決定照片與來源、`processed_ops` 交易內寫入；沒有 migration，仍停在 V29）、`/dsl-refine`（櫃檯 Web 新模組 `代開單/` 9 份、修改 `共用外框/尚未開放的入口.feature` 與 `即時更新/現場變化即時跟上.feature`；後端新增 7 份、修改 `員工事件串流/收得到哪些單的訊號.feature`；住客頁兩份限流情境改寫）。

## Task Binding Contract

- 每個**開發任務**都必須對應 `truth-delta.md` 的 ADD / MODIFY / DELETE / NOOP 語意。
- Phase 1 `Setup` 只做本輪新增的技術做法（交易內冪等加鎖、資料庫暫時觸發器、`route.fetch()` 後中斷）的三個 spike 與最後的 smoke-test；不寫 DSL 語意、不寫產品行為。本輪沒有新套件。
- Phase 2 `Foundational` 只建立後續實作與測試共用的基準量測、fixture、helper、入口與落點骨架；每則寫「只做／不做」。
- Phase 3 `Test Alignment & Implementation` 在寫產品碼之前，把本輪所有受影響 DSL 的自動化測試對齊最新版 truth。
- Truth 參照使用 `specs/truth/**`；plan 參照才使用本 plan package 內相對路徑（`truth-delta.md`、`plan.md`、`research.md`、`ui/...`）。
- Feature `Shared Must Read` 除 feature、同模組 DSL、對應 `truth-delta` 列外，還必須列入回追到的 planner 產物。
- 程式路徑省略前綴時：後端產品碼是 `tms/src/main/java/com/athena/tms/`，後端 stepdef 是 `tms/src/test/java/com/athena/tms/bdd/`，櫃檯 Web 是 `app/web/src/`，櫃檯 Web 與住客頁的 stepdef 是 `app/web-bdd/src/test/java/com/athena/web/bdd/`，共用後端資料準備是 `app/bdd-support/src/main/java/com/athena/bddsupport/`。
- T 編號從 T001 起，007 自成一份。

**跑測試的方式**（每一題的驗證都用這幾條，不另寫；在 repo 根目錄執行）：

- 後端全套（行為規格、舊整合測試、單元測試；JDK 25、Testcontainers、OrbStack）：
  ```
  JAVA_HOME="$(/usr/libexec/java_home -v 25)" TESTCONTAINERS_RYUK_DISABLED=true \
    ./tms/mvnw -f tms/pom.xml test -DargLine="-Dapi.version=1.44"
  ```
  `-DargLine="-Dapi.version=1.44"` 一定要帶，否則 Testcontainers 連不上 OrbStack 的 Docker。
- 只跑後端行為規格：上一條加 `-Dtest=RunTruthFeaturesTest -Dsurefire.excludeJUnit5Engines=cucumber`；只跑一份或一個模組再加 `"-Dcucumber.features=../specs/truth/features/backend/<模組>/<檔>.feature"`（多份以逗號分隔）；查 undefined step 加 `-Dcucumber.execution.dry-run=true`。只跑單一 feature 時會多一個 CucumberTestEngine discovery 的假 error，忽略。
- 櫃檯 Web 與住客頁行為規格：`bash app/web-bdd/run-feature.sh [--keep] [--rebuild] <路徑> [desktop|ipad|phone]`。路徑以 `frontdesk-web/` 或 `guest-web/` 開頭時相對於 `specs/truth/features/`，不帶根時相對於 `frontdesk-web/`；跑完用 `bash app/web-bdd/run-feature.sh --stop` 收掉。後端產品碼改過要帶 `--rebuild`（腳本用 8080 跑 `tms/target` 的 jar）。
- 櫃檯 Web 與住客頁查 undefined step（不起後端、不開瀏覽器）：
  ```
  cd app/web-bdd && JAVA_HOME="$(/usr/libexec/java_home -v 25)" mvn -q -o test -Dtest=RunFrontdeskWebFeaturesTest \
    -Dsurefire.excludeJUnit5Engines=cucumber -Dcucumber.execution.dry-run=true \
    "-Dcucumber.features=../../specs/truth/features/<frontdesk-web 或 guest-web 下的路徑>"
  ```
- 櫃檯 Web 單元測試（只測純計算）：`cd app/web && npx vitest run`；建置：`cd app/web && npm run build`。
- **同一時間只能有一個 BDD 實跑**：`run-feature.sh` 的櫃檯 Web 與住客頁共用 8080 後端與 5433 的 `tms` 資料庫，每個場景開頭清空業務資料；後端 Testcontainers 每次自己起資料庫，但同時兩個 `mvnw test` 會搶 `tms/target`。多個 agent 平行時，dry-run 與編譯可以平行（各自在 worktree 裡），實跑一律排隊。

## Scope Note（停止範圍、判斷與回交）

1. **專案憲章不存在。** `.agents/constitution/` 沒有 `CONSTITUTION.md` 與 `shared.md`，照 004、006 的做法略過，以專案 `CLAUDE.md` 為準。
2. **沒有回交 `/dsl-refine` 的範圍。** 寫本檔前以 Cucumber dry-run 盤點本輪所有 Test Scope：後端 29 句、櫃檯 Web 81 句、住客頁 1 句沒有 stepdef，每一句都剛好對到一個權威 `dsl.md` 列（後端介面根 3、`櫃檯看板與派單/` 25、`工務單/` 1；櫃檯 Web `代開單/` 80、`即時更新/` 1；住客頁介面根 1），沒有重複定義，也沒有找不到定義的句。`代開單/dsl.md` 的 80 列全部有 feature 用到。
3. **沒有回交 planner 的範圍。** 回追點名的 `ui/ui-plan.md`、`ui/board.html`、`ui/triage.html`、`ui/create-request.css`、`ui/create-request.js`、`ui/desk.css`、`ui/desk.js`，以及 contracts 的 `deskCreateRequests`、`listDeskRooms`、`DeskMenuKey`、`DeskTask.menuKey`、data-model 的 `tasks` Note「櫃檯代開」段、`repair_tickets` Note「櫃檯代開」段都已存在。
4. **住客頁限流句是 `[BDD-REMOVE]` 加 `[BDD-RED]`，不是 `[BDD-ALIGN]`。** `truth-delta.md` 把 `櫃檯在 "{時刻}" 替 "{房號}" 房代開了一張單` 改成 `"{房號}" 房的另一支手機在 "{時刻}" 送出了一張單` 記為 MODIFY，但句型、來源（櫃檯→住客）、是否綁訂房、要不要扣住串流訊號都變了，不是「只搬權威位置、語意不變」；舊句已沒有任何 feature 用，照 Rule 2 拆成刪舊句（T134）與寫新句（T135）。
5. **住客每房額度以本分支程式為準。** 本分支（未合入 `refactor/main_comments_and_workarounds`）的 `service/RoomRequestRateLimiter.java:18-32` 是記憶體計數，只在 `GuestRequestService.java:66` 住客送需求時呼叫；櫃檯代開不經過它，FR-037 不需要改限流程式就成立。Phase 4-B04 的 Boundary 寫明：實作期間若本分支合入該 refactor 分支（資料庫現數、條件 `source IN ('guest', 'desk')`），在同一題把條件改成只數 `guest`。
6. **spike 排在 Setup。** `research.md`「建立功能前要先做的 spike」三項都是本輪新增技術做法（`/technical-research` 的冪等 MODIFY 列與「送出失敗的模擬」ADD 列）的可行性驗證，排在 Phase 1（T001–T003），用既有端點與獨立測試驗證，不等新端點。任一項結論是「做不到」：停下受影響的 Phase 4（T001→4-B03；T002→4-B03、4-W09；T003→4-W09），結論記在 `implement-notes.md`，回交 `/technical-research`。
7. **`contracts`、`data`、`techstack` 不加 quality gate。** 它們都在 `specs/truth/**`，以 Feature 的 Test Scope 全綠當閘門。本輪唯一的 plan-side 產物家族是 `ui/`，在最後一個櫃檯 Web 消費者之後加一題 gate（Phase 4-WG）。`features/acceptance/` 不列入 `Shared Must Read`（回追順序不經過它）。
8. **`ui/ui-plan.md` 的待決事項照目前文字實作，不為它們停下。** 代開單面板用 iPadOS 26 外觀、004 的面板維持 iPadOS 18（待決 1）；不加螢幕報讀專用的狀態文字（待決 2）；房間清單讀取失敗時放寬成有數字就能送（待決 3）；代開單寫入 15 秒逾時（待決 4）。使用者改判時由對應 planner 改產物，再補一輪。
9. **`推播/員工推播事件.feature`、`工務單/` 既有三份、`ios-app/**` 是 NOOP。** 不開 Feature phase；前者的三個句型只搬權威位置（Phase 3 對齊），工務單既有三份放進 Phase 4-B07 的 Test Scope 當回歸，App 不改（`DeskTask.menuKey` 只影響櫃檯端）。

## Phase 1: Setup

**Goal**: 本輪沒有新套件，但有三個新的技術做法：業務交易內以 `pg_advisory_xact_lock` 鎖冪等鍵並在同一交易寫 `processed_ops`、測試在資料庫裝暫時觸發器讓交易中途失敗、Playwright `route.fetch()` 之後再中斷模擬回應遺失。先用三個 spike 把 `research.md` 的推測換成實跑結論，最後用 smoke-test 確認兩條測試線都還起得來。不寫 DSL 語意，不寫產品行為。

- [X] T001 spike：同一把冪等鍵兩個交易同時進來，只做一次、第二個拿到回放
  - Read:
    - `specs/truth/techstack.md` -> 技術堆疊總覽 / 資料與媒體處理 / 冪等
    - `research.md` -> 決策 3、「建立功能前要先做的 spike」第 1 點
    - `tms/src/main/java/com/athena/tms/filter/IdempotencyFilter.java:35-67`、`service/IdempotencyService.java:24-31`
  - 在 `tms/src/test/java/com/athena/tms/spike/` 寫一支 Testcontainers 測試：兩條執行緒各開一個交易，對「飯店＋鍵」取 `pg_advisory_xact_lock`（鍵怎麼換成 bigint 一併定下來，例如 `hashtextextended(tenant_id || ':' || key, 0)`），鎖內查 `processed_ops`，沒有就插一列假的業務資料並在同一交易寫 `processed_ops`；有就讀回。斷言：業務資料只有一列、第二條拿到的是第一條寫的結果、第二條確實等到第一條提交後才繼續；再驗第一條回滾時第二條會自己做一次。
  - 結論（鍵的換算方式、`IdempotencyFilter` 提交後再寫時 `ON CONFLICT DO NOTHING` 不會覆寫）記到 `implement-notes.md`。spike 測試留在 `spike/` 套件、不被產品碼引用；Phase 4-B03 綠了之後刪掉。
  - 不做：新端點、改 `IdempotencyFilter` 或任何產品碼。

- [X] T002 spike：資料庫暫時觸發器讓開單交易中途失敗，整個交易回滾；測試帳號有權限裝拆
  - Read:
    - `specs/truth/techstack.md` -> 技術堆疊總覽 / 測試與驗證 / 櫃檯 Web 送出失敗的模擬
    - `research.md` -> 決策 8、「建立功能前要先做的 spike」第 2 點
    - `tms/src/main/java/com/athena/tms/service/TaskEngine.java:53-113`、`repair/RepairTicketOpener.java:35-131`
  - 用既有的「櫃檯把住客需求派給工務」（`deskAssignTask`，同一交易裡 `RepairTicketOpener.open` 開 `W-`）當受試路徑，兩條線各試一次：
    1. 後端 Testcontainers：以 `JdbcTemplate` 在 `repair_tickets` 裝一個只對本案例飯店丟例外的 `BEFORE INSERT` 觸發器，呼叫派單；斷言回 5xx、任務仍在待派單、`repair_ticket_counters` 沒前進、`task_events` 與 outbox 沒有這次的列；拆掉觸發器後再派一次成功。
    2. 櫃檯 Web 測試線：以 `BackendChannel` 連 5433 的 `tms` 資料庫裝同樣的觸發器（再試一次「寫入標題等於某文字的 `tasks` 時丟例外」的版本，代開單 DSL 用的是這種），確認連線帳號有 `CREATE TRIGGER`／`DROP TRIGGER` 權限，場景結束的清理一定拆得掉（含場景失敗時）。
  - 結論（觸發器寫在哪張表、怎麼只對一個飯店或一個標題生效、清理掛在哪個 hook）記到 `implement-notes.md`。
  - 不做：在產品碼加任何「讓第 N 張失敗」的開關；寫代開單的 stepdef。

- [X] T003 spike：WebKit 設定下 `route.fetch()` 之後中斷，前端收到網路錯誤、後端已提交
  - Read:
    - `specs/truth/techstack.md` -> 技術堆疊總覽 / 測試與驗證 / 櫃檯 Web 送出失敗的模擬
    - `research.md` -> 決策 8、「建立功能前要先做的 spike」第 3 點
    - `app/web-bdd/src/test/java/com/athena/web/bdd/steps/realtime/BoardResponses.java:160`（既有 `route.fetch()` 用法）、`support/Network.java:155-264`
  - 在 `app/web-bdd/src/test/java/com/athena/web/bdd/` 寫一支不走 Cucumber 的 spike 測試（照 `GuestBackendSpikeTest.java` 的寫法）：`ipad`（WebKit）與 `desktop`（Chromium）兩組設定各一次，攔既有的派單請求（`Network.DESK_ASSIGN`），`route.fetch()` 拿到後端回應後 `route.abort()`；斷言頁面上的 `fetch` 以網路錯誤收場（`TypeError`／`NetworkError`，不是拿到 2xx）、後端那張單確實已派出；再用同一把 `Idempotency-Key` 重送一次，回應帶 `Idempotency-Replayed: true`。
  - 結論（兩種瀏覽器各收到什麼錯誤、要不要先 `route.fetch()` 再 `route.abort("failed")`）記到 `implement-notes.md`。
  - 不做：寫代開單畫面或 stepdef；改 `Network` 的既有方法。

- [X] T004 smoke-test：兩條測試線都還起得來
  - Read:
    - `specs/truth/techstack.md` -> 測試與驗證
  - 依序確認並停手：①後端 `test-compile` 通過，T001、T002 兩支 spike 測試綠；②`bash app/web-bdd/run-feature.sh 待派單/待派單清單.feature desktop` 與同一份 `ipad` 各跑一次，結果與 T005 的基準相同（T005 可以先做）；③櫃檯 Web 的 dry-run（「跑測試的方式」第四條）對 `frontdesk-web/代開單` 跑得起來（只會報 undefined，不會編譯失敗）。
  - 不做：跑本輪 Feature 的實跑；寫 stepdef 語意。

## Phase 2: Foundational

**Goal**: 量好改動前的基準，建好後端兩支新端點、櫃檯 Web 代開單面板、測試共用 helper 與 stepdef 檔的落點，讓 Phase 3 的 subagent 各寫各的檔、不各自發明連線與資料準備方式。語意對齊不放這裡。

- [X] T005 量改動前的基準（本次縮小範圍：後端與櫃檯 Web 在調整前已量全套；住客頁只量 `設備報修/`、`備品補充/`；全套回歸留到 Phase 5；結果見 `implement-notes.md`）
  - Read:
    - `specs/truth/techstack.md` -> 測試與驗證
  - 只做：在任何產品碼或測試碼改動之前，跑下面五組，把每一組的失敗清單（feature 檔與行號，或測試類別與方法）與失敗數記到 `implement-notes.md`：
    1. 後端全套（「跑測試的方式」第一條）。
    2. `bash app/web-bdd/run-feature.sh --keep frontdesk-web/ desktop`。
    3. `bash app/web-bdd/run-feature.sh --keep frontdesk-web/ ipad`。
    4. `bash app/web-bdd/run-feature.sh --keep guest-web/ phone`，跑完 `--stop`。
    5. `cd app/web && npx vitest run && npm run build`。
    分支上已經有程式改動時，改在 HEAD 的乾淨 worktree 量。後面每一題說「全綠」都是指「除了這份基準裡既有的失敗之外全綠」；Phase 5 用同一組指令比對同一份基準。
  - 不做：修任何既有失敗；改任何程式。

- [X] T006 後端兩支新端點與回應形狀的落點
  - Read:
    - `truth-delta.md` -> `/api-plan` 的 `POST /desk/requests`、`GET /desk/rooms`、`DeskMenuKey`、`DeskCreateRequests*`、`DeskRoom`、`DeskTask.menuKey` 各列
    - `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> `deskCreateRequests`、`listDeskRooms`
    - `specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskMenuKey`、`DeskCreateRequestsRequest`、`DeskCreateRequestItem`、`DeskCreateRequestOther`、`DeskCreateRequestsResult`、`DeskRoom`、`DeskRoomGuest`、`DeskTask`
    - `tms/src/main/java/com/athena/tms/controller/DeskBoardController.java`、`dto/desk/DeskTask.java`、`service/DeskTaskMapper.java`
  - 只做：在 `dto/desk/` 新增上面幾個 schema 的 record 與 `DeskMenuKey`（12 個中文鍵，依契約順序，帶部門、類型、上限、標題組法、工務品項的修繕分類）；`DeskTask` 多一個可為 null 的 `menuKey`，`DeskTaskMapper` 先一律給 null，既有呼叫點補上；新增 `service/DeskCreateRequestService`（`createRequests`、`listRooms` 先丟 `UnsupportedOperationException`）；`DeskBoardController` 掛 `POST /requests` 與 `GET /rooms` 兩個方法接到它。編譯通過、後端全套結果與 T005 相同（新增欄位為 null 時 JSON 照契約輸出 `menuKey: null`）。
  - 不做：任何開單、檢查、冪等、房間清單的行為；不動 `SecurityConfig`（`/api/v1/desk/**` 已放行三種櫃檯角色）；不動 `RepairService`、`TaskEngine`、`GuestRequestService`。

- [X] T007 後端測試共用 helper：代開單呼叫、暫時觸發器、代開的情境記憶
  - Read:
    - `truth-delta.md` -> `/dsl-refine` 後端介面根與 `櫃檯看板與派單/dsl.md` 的代開單列
    - `specs/truth/features/backend/dsl.md` -> 共通實作約定、`"{櫃檯}" 代開 "{房號}" 的以下單：` 的 Data Table 參數
    - `tms/src/test/java/com/athena/tms/bdd/support/DeskApi.java`、`DeskContext.java`、`LastOperationGuard.java`、`RoomFixtures.java`
    - T001、T002 在 `implement-notes.md` 的結論
  - 只做：`DeskApi` 加 `createRequests(staff, roomNo, items, other, idempotencyKey)`（鍵可為 null 表示不帶）與 `listRooms(staff)`，回傳原始回應；新增 `support/DeskCreateFaults.java`：照 T002 結論裝「開某標題就丟例外」的暫時觸發器，場景結束的 hook 一定拆掉；`DeskContext` 記這一次代開用的鍵、請求內容與回應，給「同一把鍵再送一次」與 Then 句讀；`LastOperationGuard` 能對代開單快照 `tasks`、`repair_tickets`、兩個流水號列與 outbox 的張數。
  - 不做：寫任何 stepdef；寫各句的斷言語意。

- [X] T008 後端代開單 stepdef 檔的落點
  - Read:
    - `tms/src/test/java/com/athena/tms/bdd/steps/desk/`（既有 `DeskCommonSteps`、`DeskGivenSteps`、`DeskWhenSteps`、`DeskThenSteps` 的建構子注入寫法）
  - 只做：在 `steps/desk/` 新增 `DeskCreateRequestCommonSteps.java`（放介面根三句）、`DeskCreateRequestGivenSteps.java`、`DeskCreateRequestWhenSteps.java`、`DeskCreateRequestThenSteps.java` 四個空類別，注入 T007 的 helper；`mvn -q -o test-compile` 通過。
  - 不做：寫任何 `@假設`／`@當`／`@那麼`；改既有四個 Desk 步驟檔。

- [X] T009 `BackendChannel` 加代開單相關的資料準備與後端呼叫
  - Read:
    - `truth-delta.md` -> `/dsl-refine` 櫃檯 Web `代開單/dsl.md`、`即時更新/dsl.md` 的代開單列
    - `specs/truth/features/frontdesk-web/代開單/dsl.md` -> Given 句型各列的 `使用 fixture`、`Arrange 來源`
    - `app/bdd-support/src/main/java/com/athena/bddsupport/BackendChannel.java` -> `ensureRoom`（:607）、`ensurePublicAreaRoom`（:622）、`createReservation`（:2324）、`deskAssign`（:2731）、`createStaff`（:1373）
    - T002 在 `implement-notes.md` 的結論
  - 只做：`deskCreateRequests(actor, roomNo, items, other, idempotencyKey)`、`listDeskRooms(actor)`；「把這間房從房間主檔移除」（有單掛著時照 T002 的清理方式處理外鍵）；「把帳號角色改成房務」；「讓某員工的登入憑證失效」；照 T002 結論裝拆「寫入標題等於某文字的任務時丟例外」的暫時觸發器，並登記到場景結束的清理。每一個方法都要有一行說明它對到哪一句的 `Arrange 來源`。
  - 不做：寫 stepdef；改既有方法的行為（`seedDeskTask` 的去留在 T134 決定）。

- [X] T010 櫃檯 Web 測試線的網路攔截、page object 與 stepdef 檔的落點
  - Read:
    - `specs/truth/features/frontdesk-web/dsl.md` -> 共通實作約定（找畫面元素、畫面斷言、單的識別）
    - `ui/ui-plan.md` -> 畫面與流程 1 至 5（區塊名稱、按鈕與欄位的可及性名稱）
    - `app/web-bdd/src/test/java/com/athena/web/bdd/support/Network.java`、`pages/`、`steps/answer/`（同類面板的檔案切法）
    - T003 在 `implement-notes.md` 的結論
  - 只做：`Network` 加 `DESK_CREATE_REQUESTS`（`POST /api/v1/desk/requests`）與 `DESK_ROOMS`（`GET /api/v1/desk/rooms`）兩個 `Api`，並照 T003 結論加「送到後端、拿到回應後中斷」的攔截方法（記下這次請求的 `Idempotency-Key`）；新增 `pages/CreateRequestPanel.java`（面板、遮罩、房號欄、建議房號、住客卡、空房提示、12 格品項與其加減移除鈕、其他需求框與部門選項、類似單提醒、摘要、送出鈕的定位，一律用角色與看得到的文字）；新增 `steps/createrequest/` 的 `CreateRequestGivenSteps.java`、`CreateRequestWhenSteps.java`、`CreateRequestThenSteps.java` 空類別與 `CreateRequestScenario.java`（這次面板的內容快照、送出用的鍵、後端這次開出的單 id）。`mvn -q -o test-compile` 通過。
  - 不做：寫任何 stepdef；改 `DESK_WRITES` 的內容（T048 決定）。

- [X] T011 櫃檯 Web 代開單面板的落點
  - Read:
    - `ui/ui-plan.md` -> 前端實作切分建議
    - `specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskMenuKey`、`DeskCreateRequests*`、`DeskRoom`、`DeskTask.menuKey`
    - `app/web/src/api/desk.ts`、`api/client.ts:30-33`、`stores/desk-panels.ts`、`shell/DeskShell.tsx`
  - 只做：`api/desk.ts` 補型別（`DeskTask.menuKey` 可為 null、代開單請求與回應、房間清單）與兩個呼叫函式（寫入可傳入指定的冪等鍵）；新增 `features/create-request/` 資料夾與 `CreateRequestPanel.tsx`、`RoomSection.tsx`、`ItemGrid.tsx`、`OtherRequest.tsx`、`SimilarTasksNotice.tsx`、`use-create-request.ts` 空殼，以及純計算模組 `create-request-logic.ts`（函式簽名先丟錯）與對應的空 `*.test.ts`；`stores/desk-panels.ts` 加 `createRequestOpen` 與預填房號欄位（先不接任何按鈕）。`npx vitest run`、`npm run build` 結果與 T005 相同。
  - 不做：工具列「代開單」的行為；任何畫面、文字、樣式；`i18n` 的文字。

## Phase 3: Test Alignment & Implementation

**Goal**: 把本輪 Feature 用到的 DSL 自動化測試對齊最新版 truth；含 truth-delta 有改的句，以及本輪 Feature 用到、尚無 stepdef 的句。不寫產品行為。後端 6 句 `[BDD-ALIGN]`、29 句 `[BDD-RED]`；櫃檯 Web 6 句 `[BDD-ALIGN]`、81 句 `[BDD-RED]`；住客頁 1 句 `[BDD-REMOVE]`、1 句 `[BDD-RED]`。

**DSL 參照**:
- 每一句只屬於一個權威 `dsl.md`：同模組 `specs/truth/features/{介面}/{模組}/dsl.md`，或介面根 `specs/truth/features/{介面}/dsl.md`。不得掃其他模組。
- backend 的句分四處：介面根 `specs/truth/features/backend/dsl.md`（T012–T015、T018–T020）、`櫃檯看板與派單/dsl.md`（T016、T021–T045）、`員工事件串流/dsl.md`（T017）、`工務單/dsl.md`（T046）。
- frontdesk-web 的句分三處：介面根 `specs/truth/features/frontdesk-web/dsl.md`（T047–T052）、`代開單/dsl.md`（T054–T133）、`即時更新/dsl.md`（T053）。
- guest-web 的句在介面根 `specs/truth/features/guest-web/dsl.md`（T134–T135）。
- 只搬權威位置、語意不變的列（T013–T015、T049–T052）仍是 `[BDD-ALIGN]`：只確認 stepdef 與新權威列一致、本輪的 feature 跑得到它；Cucumber glue 是全域的，不必搬檔。
- 讀法：用 task title 的句型對到該檔那一列（title 開頭的 `Given:`／`When:`／`Then:` 是它所在的章節，不是句型的一部分），以最右欄的實作語意當作測試程式碼語意：後端是 `StepDef 實作語意`，櫃檯 Web 與住客頁是 Playwright 那一欄。Given／When 讀 `怎麼做`（網頁是 `使用 fixture`、`Arrange 來源`、`頁面操作`、`等待條件`）、`權威狀態落地`、`回寫`；Then 讀 `必查`（`呈現結果`、`權威狀態`、`再讀確認`、`不該發生`）。同一列的 `Gherkin 參數`、`Data Table 參數`、`預設參數` 一併照做。
- 櫃檯 Web 的每一句另外受 `specs/truth/features/frontdesk-web/dsl.md` 開頭「共通實作約定」約束；代開單的句另外受 `代開單/dsl.md` 開頭的「常用品項」表、「送出摘要的組法」、「房號有效」約束。
- `truth-delta.md` 只告訴這句是 ADD / MODIFY / DELETE。語意以 `dsl.md` 那一列為準，不得用 feature 措辭、雛形或舊 stepdef 自行發明。

**Markers**:
- `[BDD-ALIGN]`：`MODIFY`。既有 stepdef 還在，但語意是舊 truth。依 `dsl.md` 該列改測試，讓它表達最新版實作語意。
- `[BDD-REMOVE]`：`DELETE`。此句已不是 truth。移除或改寫仍綁這句的 stepdef / assertion，不得留下保護舊行為的測試。
- `[BDD-RED]`：`ADD`，或本輪 Feature 用到、尚無 stepdef 的句。依 `dsl.md` 該列寫出 stepdef。完成時這句可被跑到，失敗只能是 assertion 或產品行為，不能是 undefined step。
- 三個 marker 都只動測試層，不寫產品碼。

**Shared Must Read**:
- `specs/truth/features/backend/dsl.md` -> 共通實作約定、狀態與部門用語對照、本題句型
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md`、`員工事件串流/dsl.md`、`工務單/dsl.md` -> 模組說明、用語對照與本題句型
- `specs/truth/features/frontdesk-web/dsl.md` -> 「共通實作約定」、「單的狀況對照」與本題句型
- `specs/truth/features/frontdesk-web/代開單/dsl.md` -> 模組說明、常用品項表、送出摘要的組法、房號有效與本題句型
- `specs/truth/features/frontdesk-web/即時更新/dsl.md`、`specs/truth/features/guest-web/dsl.md` -> 本題句型
- `truth-delta.md` -> `/dsl-refine` 有對應 ADD / MODIFY 的句
- 後端 stepdef 與 helper：`tms/src/test/java/com/athena/tms/bdd/steps/common/`、`steps/desk/`（含 T008 的四個新檔）、`steps/push/PushGuestSteps.java`、`steps/staffstream/`、`steps/repair/`；`bdd/support/`（T007 的 `DeskApi`、`DeskContext`、`DeskCreateFaults`、`LastOperationGuard`）
- 網頁 stepdef 與 helper：`app/web-bdd/src/test/java/com/athena/web/bdd/steps/`（含 T010 的 `steps/createrequest/`）、`pages/`（含 `CreateRequestPanel.java`）、`support/Network.java`；`app/bdd-support/` 的 `BackendChannel`（T009）
- 契約只在句型的實作語意點名操作時才讀：`specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml`（`deskCreateRequests`、`listDeskRooms`、`getDeskBoard`）、`groups/住客與房間/住客掃碼.yaml`（`createGuestRequest`）與對應的 `components/schemas/`

**Boundary**:
- 一條 DSL 一個 task。
- 只改該句的 stepdef / assertion / 直接依賴的 helper 與 page object。
- 不寫產品碼。產品補上之前實跑會紅，這是預期；完成條件看 dry-run 沒有 undefined、斷言表達 `dsl.md` 的語意。
- 櫃檯 Web 的 stepdef 找元素一律用使用者看得到的角色與文字，定位收在 `CreateRequestPanel` page object 內；不為了測試要求產品加 `data-testid`。
- 共用檔（`CommonThenSteps.java`、`DeskApi`、`BackendChannel`、`Network`、`CreateRequestPanel`、`TaskTableRows`）改動前先重新讀檔、只做最小追加、寫入後立即編譯（後端 `./tms/mvnw -q -o -f tms/pom.xml test-compile`，網頁 `cd app/web-bdd && mvn -q -o test-compile`）；不要重排或重寫別人的段落。
- review 啟動 subagent；本輪所有 Test Scope 不得再有 undefined step（後端、櫃檯 Web、住客頁各跑一次 dry-run），失敗只能是 assertion 或產品行為。有 issues 就修正再 review，直到沒有任何問題。通過前不解鎖 Phase 4。

**Parallel Hint**（依檔案衝突分組；組與組平行，組內依序；每組各派一個獨立 subagent，各自在 worktree 裡編譯與 dry-run）：
- 後端 B1（`steps/common/CommonThenSteps.java`）：T012。B2（`steps/push/PushGuestSteps.java`）：T013–T015。B3（`steps/desk/DeskGivenSteps.java`）：T016。B4（`steps/staffstream/StaffStreamThenSteps.java`）：T017。
- 後端 B5（`DeskCreateRequestCommonSteps.java`）：T018–T020，先做；B6–B8 依賴它記下的代開結果。B6（`DeskCreateRequestGivenSteps.java`）：T021–T027。B7（`DeskCreateRequestWhenSteps.java`）：T028–T034。B8（`DeskCreateRequestThenSteps.java`）：T035–T045。B9（`steps/repair/RepairThenSteps.java`）：T046。
- 網頁 W1（`steps/common/CommonGivenSteps.java`、`TaskTableRows.java`）：T047–T048。W2（`steps/detail/DetailThenSteps.java`）：T049–T050。W3（`steps/account/AccountGivenSteps.java`）：T051。W4（`steps/shell/ShellThenSteps.java`）：T052。W5（`steps/realtime/RealtimeWhenSteps.java`）：T053。
- 網頁 W6（`steps/createrequest/CreateRequestGivenSteps.java`）：T054–T071，其中 T054、T056–T058 是其他句的前提，先做。W7（`CreateRequestWhenSteps.java`）：T072–T085。W8（`CreateRequestThenSteps.java`）：T086–T133，數量多，可再依「面板外觀與房號區」T086–T109、「其他需求與類似單提醒」T110–T120、「送出後」T121–T133 拆成三個 subagent，但三者共用同一個檔，必須依序寫入。
- 住客頁 G1（`steps/guest/common/GuestCommonGivenSteps.java`、`BackendChannel`）：T134 再 T135。
- T136 等全部回來再啟動 subagent 來 review。

### 後端：既有句的對齊

- [X] T012 [P] [BDD-ALIGN] `Then: 這次操作被拒絕，錯誤碼為 "{錯誤碼}"`（`backend/dsl.md`）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/common/CommonThenSteps.java:78-126`
  - 錯誤碼與狀態碼對照補 `validation_failed`→422、`not_found`→404（`idempotency_key_required`→400 已有）；`assertAuthoritativeStateUnchanged` 對代開單也要涵蓋 `tasks`、`repair_tickets`、兩個流水號與 outbox（用 T007 的 `LastOperationGuard`）。
- [X] T013 [P] [BDD-ALIGN] `Given: 住客 10 分鐘內已在 "{房號}" 送出 {筆數} 筆需求`（權威位置由 `backend/推播/dsl.md` 搬到 `backend/dsl.md`，語意不變）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/push/PushGuestSteps.java:52-58`
  - 只確認 stepdef 與新權威列一致、`櫃檯看板與派單/代開的單不算住客額度.feature` 跑得到它。
- [X] T014 [P] [BDD-ALIGN] `When: 住客在 "{房號}" 掃碼要 "{品項}"`（權威位置搬到 `backend/dsl.md`，語意不變）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/push/PushGuestSteps.java:60-69`
- [X] T015 [P] [BDD-ALIGN] `Then: 這次請求被擋下，提示住客洽櫃檯`（權威位置搬到 `backend/dsl.md`，語意不變）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/push/PushGuestSteps.java:81-96`
- [X] T016 [P] [BDD-ALIGN] `Given: 以下房間現在有住客：`（`backend/櫃檯看板與派單/dsl.md`）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/desk/DeskGivenSteps.java:131-155`（`monthDay` 的解析）
  - 入住日、退房日除了 `M/D` 之外多收「昨天／今天／明天／後天」（以目前 `Clock` 的營業日換算）；`M/D` 與預設值照舊。
- [X] T017 [P] [BDD-ALIGN] `Then: "{員工}" 的串流收到 "{任務}" 的 "{訊號}" 訊號`（`backend/員工事件串流/dsl.md` 用語對照「訊號」加「建立」）
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/staffstream/StaffStreamThenSteps.java:24-30`、`:234`
  - `SIGNAL_TYPE` 加「建立」＝`created`；其他訊號不變。

### 後端：介面根 `specs/truth/features/backend/dsl.md` 的新句

- [X] T018 [P] [BDD-RED] `Given: 飯店的客房有 {房號清單}`
  - 房號清單以「、」分隔、一間到多間都要對到同一個 stepdef（用正規表示式，不寫三個 Cucumber Expression）。
- [X] T019 [P] [BDD-RED] `Given: "{櫃檯}" 已代開 "{房號}" 的以下單：`
- [X] T020 [P] [BDD-RED] `When: "{櫃檯}" 代開 "{房號}" 的以下單：`

### 後端：`specs/truth/features/backend/櫃檯看板與派單/dsl.md` 的新句

- [X] T021 [P] [BDD-RED] `Given: "{櫃檯}" 剛才代開 "{房號}" 的以下單時後端出錯，一張都沒開出：`
- [X] T022 [P] [BDD-RED] `Given: 開 "{標題}" 這張單時後端會出錯`
- [X] T023 [P] [BDD-RED] `Given: "{房號}" 的住客已勾選服務項目 "{項目}" 送出`
- [X] T024 [P] [BDD-RED] `Given: "{房號}" 的住客已打字送出 "{內容}"`
- [X] T025 [P] [BDD-RED] `Given: 飯店的分類規則把含 "{關鍵字}" 的文字分給 "{部門}"，對應品項 "{品項}"`
- [X] T026 [P] [BDD-RED] `Given: 飯店有代表公共區域的佔位房`
- [X] T027 [P] [BDD-RED] `Given: 以下房間的訂房現在不算在住：`
- [X] T028 [P] [BDD-RED] `When: "{櫃檯}" 代開 "{房號}"，其他需求是 "{內容}"，給 "{部門}"`
- [X] T029 [P] [BDD-RED] `When: "{櫃檯}" 代開 "{房號}"，其他需求是 {字數} 個字，給 "{部門}"`
- [X] T030 [P] [BDD-RED] `When: "{櫃檯}" 代開 "{房號}"，沒有選任何品項也沒有其他需求`
- [X] T031 [P] [BDD-RED] `When: "{櫃檯}" 不帶防重複鍵代開 "{房號}" 的以下單：`
- [X] T032 [P] [BDD-RED] `When: "{櫃檯}" 用同一把鍵、同樣的內容再送一次剛才那次代開`
- [X] T033 [P] [BDD-RED] `When: "{櫃檯}" 用同一把鍵同時送出兩次代開 "{房號}" 的以下單：`
  - 兩個請求要真的同時在後端跑（照 T001 的做法用兩條執行緒、同一把鍵），不是先後送。
- [X] T034 [P] [BDD-RED] `When: "{員工}" 讀取代開單的房間清單`
- [X] T035 [P] [BDD-RED] `Then: 這次代開依序開出以下單：`
- [X] T036 [P] [BDD-RED] `Then: 這次代開開出一張單，標題就是送出的那 {字數} 個字`
- [X] T037 [P] [BDD-RED] `Then: 這次代開回應的卡片和櫃檯看板上的那幾張一模一樣，帶著後端現在的時間`
- [X] T038 [P] [BDD-RED] `Then: 這次代開的每一張單，歷程都只有一筆 "{櫃檯}" 的建立，沒有派單`
- [X] T039 [P] [BDD-RED] `Then: 這次代開失敗，一張單都沒有留下，流水號也沒有被用掉`
- [X] T040 [P] [BDD-RED] `Then: 這次拿到的是第一次的結果，標示為回放，"{房號}" 仍只有第一次開出的 {張數} 張單`
- [X] T041 [P] [BDD-RED] `Then: 兩次送出拿到同一批單，"{房號}" 只開出 {張數} 張單`
- [X] T042 [P] [BDD-RED] `Then: 住客這次送出的需求開單成功`
- [X] T043 [P] [BDD-RED] `Then: 本案例原有的單都沒有任何改動`
- [X] T044 [P] [BDD-RED] `Then: 代開單的房間清單剛好如下：`
- [X] T045 [P] [BDD-RED] `Then: 櫃檯看板上各單的對應品項如下：`

### 後端：`specs/truth/features/backend/工務單/dsl.md` 的新句

- [X] T046 [P] [BDD-RED] `Then: 代開的工務單如下：`
  - Read: `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairThenSteps.java`（同檔既有查 `repair_tickets` 與 `readRepairTicket`、`listDeptPool` 的寫法）

### 櫃檯 Web：介面根 `specs/truth/features/frontdesk-web/dsl.md` 既有句的對齊

- [X] T047 [P] [BDD-ALIGN] `Given: 全館有以下的單：`
  - Read: `app/web-bdd/src/test/java/com/athena/web/bdd/steps/common/CommonGivenSteps.java:69`、`steps/common/TaskTableRows.java:49`（`COLUMNS`）與建單處
  - 加選填欄 `對應品項`：有填就寫進那張單 `tasks.note` 的 `menu_key`（住客勾選單沿用 `{menu_key, quantity}` 形狀，住客打字單與 `rule_id` 並存）；沒填的列行為不變。跑 `bash app/web-bdd/run-feature.sh 單筆詳情/歷程.feature`，失敗只能是 T005 基準裡既有的。
- [X] T048 [P] [BDD-ALIGN] `Given: 接下來的送出會因為網路中斷而失敗`
  - Read: `app/web-bdd/src/test/java/com/athena/web/bdd/steps/common/CommonGivenSteps.java:167-174`、`support/Network.java:71`
  - 斷線的請求加上 `Network.DESK_CREATE_REQUESTS`；派單、復原、當場回答照舊。`DESK_WRITES` 是否一起加，看它的其他使用者（不得讓「讀取照常」的既有句被波及）。
- [X] T049 [P] [BDD-ALIGN] `Then: 詳情面板「內容」的 "{列名}" 是 "{內容}"`（權威位置由 `單筆詳情/dsl.md` 搬到介面根，語意不變）
  - Read: `app/web-bdd/src/test/java/com/athena/web/bdd/steps/detail/DetailThenSteps.java:232`
- [X] T050 [P] [BDD-ALIGN] `Then: 詳情面板的歷程最新一筆是 "{句子}"，操作者是 "{操作者}"`（權威位置搬到介面根，語意不變）
  - Read: `app/web-bdd/src/test/java/com/athena/web/bdd/steps/detail/DetailThenSteps.java:483`
- [X] T051 [P] [BDD-ALIGN] `Given: "{員工}" 已登入櫃檯畫面，介面語言是 "{語言}"，停在 "{頁}"`（權威位置由 `帳號與登入/dsl.md` 搬到介面根，語意不變）
  - Read: `app/web-bdd/src/test/java/com/athena/web/bdd/steps/account/AccountGivenSteps.java:221`
- [X] T052 [P] [BDD-ALIGN] `Then: 這個畫面上每一項內容與每一顆可以按的東西，不用游標移上去就看得到、按得到`（權威位置由 `共用外框/dsl.md` 搬到介面根，語意不變）
  - Read: `app/web-bdd/src/test/java/com/athena/web/bdd/steps/shell/ShellThenSteps.java:578`
  - 確認代開單面板打開時，斷言涵蓋的範圍包含面板上的元素。

### 櫃檯 Web：`specs/truth/features/frontdesk-web/即時更新/dsl.md` 的新句

- [X] T053 [P] [BDD-RED] `When: "{同事}" 在另一台櫃檯代開 "{房號}" 的以下單：`
  - Read: `app/web-bdd/src/test/java/com/athena/web/bdd/steps/realtime/RealtimeWhenSteps.java`（同檔「另一台櫃檯派單」的寫法）

### 櫃檯 Web：`specs/truth/features/frontdesk-web/代開單/dsl.md` 的新句

- [X] T054 [P] [BDD-RED] `Given: 飯店的客房與現在的住客如下：`
- [X] T055 [P] [BDD-RED] `Given: 飯店沒有 "{房號}" 這間房`
- [X] T056 [P] [BDD-RED] `Given: "{員工}" 已打開代開單面板`
- [X] T057 [P] [BDD-RED] `Given: "{員工}" 已打開代開單面板，房號是 "{房號}"`
- [X] T058 [P] [BDD-RED] `Given: "{員工}" 已打開代開單面板，房號是 "{房號}"，選了以下品項：`
- [X] T059 [P] [BDD-RED] `Given: "{員工}" 已打開代開單面板，還沒打房號，選了以下品項：`
- [X] T060 [P] [BDD-RED] `Given: 代開單面板的其他需求是 "{內容}"，要給 "{部門}"`
- [X] T061 [P] [BDD-RED] `Given: "{員工}" 已按 "{按鈕}"`
- [X] T062 [P] [BDD-RED] `Given: "{員工}" 已點代開單面板外面的暗色區域`
- [X] T063 [P] [BDD-RED] `Given: "{員工}" 剛代開了 "{房號}" 的以下單：`
- [X] T064 [P] [BDD-RED] `Given: "{房號}" 這個房號在面板打開之後被從飯店的房間清單移除`
- [X] T065 [P] [BDD-RED] `Given: "{員工}" 的帳號在面板打開之後被改成不能代開單的角色`
- [X] T066 [P] [BDD-RED] `Given: "{員工}" 的登入在面板打開之後逾時`
- [X] T067 [P] [BDD-RED] `Given: 開 "{標題}" 這張單時後端會出錯`
- [X] T068 [P] [BDD-RED] `Given: 接下來的代開單送出會被後端判定內容不符合規定`
- [X] T069 [P] [BDD-RED] `Given: "{員工}" 剛才按送出時網路中斷，「沒有送出」的提示開著`
- [X] T070 [P] [BDD-RED] `Given: "{員工}" 剛才按送出時網路中斷，已按「好」關掉提示，網路已經恢復`
- [X] T071 [P] [BDD-RED] `Given: "{員工}" 剛才按送出時沒有收到回應，但這次的單其實已經開好，已按「好」關掉提示`
  - 照 T003 的結論用 T010 的「送到後端、拿到回應後中斷」攔截。
- [X] T072 [P] [BDD-RED] `When: "{員工}" 在房號欄依序打 "{字元}"`
- [X] T073 [P] [BDD-RED] `When: "{員工}" 在房號欄輸入 "{房號}"`
- [X] T074 [P] [BDD-RED] `When: "{員工}" 刪掉房號欄的最後一碼`
- [X] T075 [P] [BDD-RED] `When: "{員工}" 點建議房號 "{房號}"`
- [X] T076 [P] [BDD-RED] `When: "{員工}" 依序點以下品項：`
- [X] T077 [P] [BDD-RED] `When: "{員工}" 按品項 "{品項}" 底下的 "{鈕}" {次數} 次`
- [X] T078 [P] [BDD-RED] `When: "{員工}" 在其他需求打 "{內容}"`
- [X] T079 [P] [BDD-RED] `When: "{員工}" 在其他需求只打了幾個空白`
- [X] T080 [P] [BDD-RED] `When: "{員工}" 在其他需求連續打 {字數} 個字，再貼上 {貼上字數} 個字`
- [X] T081 [P] [BDD-RED] `When: "{員工}" 清空其他需求`
- [X] T082 [P] [BDD-RED] `When: "{員工}" 把其他需求改給 "{部門}"`
- [X] T083 [P] [BDD-RED] `When: "{員工}" 按代開單面板的送出鈕`
- [X] T084 [P] [BDD-RED] `When: "{員工}" 點代開單面板外面的暗色區域`
- [X] T085 [P] [BDD-RED] `When: "{員工}" 按 "{按鈕}"，在還沒有結果之前又連按 {次數} 次`
- [X] T086 [P] [BDD-RED] `Then: 畫面中間打開代開單面板，面板外面是暗色遮罩`
- [X] T087 [P] [BDD-RED] `Then: 代開單面板左上的關閉鈕名稱是 "{名稱}"`
- [X] T088 [P] [BDD-RED] `Then: 代開單面板由上到下是房號欄、建議房號、"{品項區}"、"{其他需求區}"，送出鈕固定在底部`
- [X] T089 [P] [BDD-RED] `Then: 房號欄是空的，提示字是 "{提示字}"`
- [X] T090 [P] [BDD-RED] `Then: 房號欄是空的`
- [X] T091 [P] [BDD-RED] `Then: 房號欄是 "{房號}"`
- [X] T092 [P] [BDD-RED] `Then: 常用品項依序如下，每一格都沒有徽章：`
- [X] T093 [P] [BDD-RED] `Then: 12 個品項都是 0`
- [X] T094 [P] [BDD-RED] `Then: 其他需求是空的`
- [X] T095 [P] [BDD-RED] `Then: 代開單面板沒有送出摘要`
- [X] T096 [P] [BDD-RED] `Then: 送出摘要是 "{摘要}"`
- [X] T097 [P] [BDD-RED] `Then: 各品項的樣子如下：`
- [X] T098 [P] [BDD-RED] `Then: 各項的無障礙名稱如下：`
- [X] T099 [P] [BDD-RED] `Then: 建議房號依序如下：`
- [X] T100 [P] [BDD-RED] `Then: 建議房號有 {筆數} 筆，依房號由小到大，全部是有住客的房`
- [X] T101 [P] [BDD-RED] `Then: 沒有建議房號`
- [X] T102 [P] [BDD-RED] `Then: 住客卡是頭像 "{頭像}"、"{姓名}"、"{第二行}"，{有沒有} VIP 標`
  - 「有」「沒有」兩種寫法對到同一個 stepdef（正規表示式），不寫兩個。
- [X] T103 [P] [BDD-RED] `Then: 沒有住客卡`
- [X] T104 [P] [BDD-RED] `Then: 空房提示是 "{文字}"`
- [X] T105 [P] [BDD-RED] `Then: 沒有空房提示`
- [X] T106 [P] [BDD-RED] `Then: 代開單面板上沒有任何紅字的錯誤提示`
- [X] T107 [P] [BDD-RED] `Then: 畫面上只有工具列有「代開單」鈕`
- [X] T108 [P] [BDD-RED] `Then: 工具列上的「代開單」是唯一的實心主按鈕`
- [X] T109 [P] [BDD-RED] `Then: 代開單面板是關的`
- [X] T110 [P] [BDD-RED] `Then: 其他需求框的標籤是 "{標籤}"，提示字是 "{提示字}"`
- [X] T111 [P] [BDD-RED] `Then: 其他需求框底下沒有「這段要給」`
- [X] T112 [P] [BDD-RED] `Then: 其他需求框底下是「這段要給」與「房務」「工務」，選中的是 "{部門}"`
- [X] T113 [P] [BDD-RED] `Then: 這組部門選項的無障礙名稱是 "{名稱}"`
- [X] T114 [P] [BDD-RED] `Then: 其他需求框裡只有前 {字數} 個字`
- [X] T115 [P] [BDD-RED] `Then: 其他需求沒有字數計數`
- [X] T116 [P] [BDD-RED] `Then: 類似單提醒如下：`
- [X] T117 [P] [BDD-RED] `Then: 類似單提醒有以下各行：`
- [X] T118 [P] [BDD-RED] `Then: 類似單提醒有一行以 "{開頭}" 開頭`
- [X] T119 [P] [BDD-RED] `Then: 沒有類似單提醒`
- [X] T120 [P] [BDD-RED] `Then: 代開單面板的介面文字都是 "{語言}"`
- [X] T121 [P] [BDD-RED] `Then: 代開單面板上的內容與按送出之前一樣`
- [X] T122 [P] [BDD-RED] `Then: 畫面換到總覽看板，數量卡篩選與搜尋房號是空的，沒有開著任何詳情面板`
- [X] T123 [P] [BDD-RED] `Then: "{欄}" 欄多了以下這次開出的卡片，外框高亮 {秒數} 秒後恢復：`
- [X] T124 [P] [BDD-RED] `Then: 這次開出的卡片如下：`
- [X] T125 [P] [BDD-RED] `Then: 畫面上沒有任何浮條，也沒有「復原」可以按`
- [X] T126 [P] [BDD-RED] `Then: 後端這次開出的單如下：`
- [X] T127 [P] [BDD-RED] `Then: 後端這次一張單都沒有開出`
- [X] T128 [P] [BDD-RED] `Then: 後端 "{房號}" 只有這次開出的 {張數} 張單，沒有重複`
- [X] T129 [P] [BDD-RED] `Then: 後端 "{房號}" 現在有 {張數} 張 "{品項}" 的單`
- [X] T130 [P] [BDD-RED] `Then: 送出還沒有結果之前，送出鈕不能按，最後只送出一次`
- [X] T131 [P] [BDD-RED] `Then: 畫面回到登入頁，沒有跳任何提示`
- [X] T132 [P] [BDD-RED] `Then: 代開單面板完整顯示在視窗內，內容在面板裡捲動，送出鈕一直看得到`
- [X] T133 [P] [BDD-RED] `Then: 代開單面板上的品項格子、加減鈕、移除鈕、建議房號、部門選項與送出鈕都至少 44 × 44`

### 住客頁：介面根 `specs/truth/features/guest-web/dsl.md`

- [X] T134 [P] [BDD-REMOVE] `Given: 櫃檯在 "{時刻}" 替 "{房號}" 房代開了一張單`
  - Read: `app/web-bdd/src/test/java/com/athena/web/bdd/steps/guest/common/GuestCommonGivenSteps.java:208-215`、`app/bdd-support/src/main/java/com/athena/bddsupport/BackendChannel.java:2617`（`seedDeskTask`）
  - 刪掉這個 stepdef 與它的 Javadoc；`grep -rn "seedDeskTask" app tms` 確認沒有其他使用者後一併刪掉 `BackendChannel.seedDeskTask`。不得留下「櫃檯代開的單算住客額度」的任何測試或 helper。
- [X] T135 [P] [BDD-RED] `Given: "{房號}" 房的另一支手機在 "{時刻}" 送出了一張單`
  - Read: `app/web-bdd/src/test/java/com/athena/web/bdd/steps/guest/cancelrate/CancelRateGivenSteps.java:79-85`（先 `holdLiveSignals` 再以另一個住客工作階段在後端操作的寫法）、`BackendChannel.guestScan`、`guestSubmitMenuItem`（:2370）
  - 依 dsl 列：先扣住這個分頁的串流訊號，再以住客身分、同一枚代碼取得另一個工作階段，在後端送出備品補充「吹風機 ×1」（來源住客、綁同一筆訂房、進房務待認領），建立時間是今日該時刻；不經這支手機的頁面、不重新整理。跑 `bash app/web-bdd/run-feature.sh guest-web/設備報修/報修的限流.feature phone` 與 `guest-web/備品補充/後端在送到一半拒絕.feature`，失敗只能是產品行為（預期這兩份直接綠，見 Phase 4-G01、4-G02）。

- [X] T136 subagent review (phase quality gate)
  - 後端以 dry-run 跑 `backend/櫃檯看板與派單`、`backend/工務單/櫃檯代開的工務單.feature`、`backend/員工事件串流/收得到哪些單的訊號.feature`、`backend/推播/員工推播事件.feature`；網頁以 dry-run 跑 `frontdesk-web/代開單`、`frontdesk-web/即時更新/現場變化即時跟上.feature`、`frontdesk-web/共用外框/尚未開放的入口.feature`、`guest-web/設備報修/報修的限流.feature`、`guest-web/備品補充/後端在送到一半拒絕.feature`：undefined step 為 0、ambiguous 為 0；每個 stepdef 的斷言對得上權威列 `必查` 的全部項目（不只看回應或畫面），Given 的資料落地對得上 `權威狀態落地`；`seedDeskTask` 與舊句已不存在。

## Phase 4-B01: ADD Feature File - backend/櫃檯看板與派單/櫃檯代開單.feature

**Goal**: 新增櫃檯代開單：一個房號一次送出多個品項與其他需求，每品項一張、直接進部門待認領、不經待派單；房務開一般單（`T-`），工務品項與給工務的其他需求開工務單（`W-`）；三種櫃檯角色可用，房務、工務、老闆 403；回應帶這次每張單的看板卡片。

**Shared Must Read**:
- `specs/truth/features/backend/櫃檯看板與派單/櫃檯代開單.feature` -> `功能: 櫃檯代開單` 全文（13 條 Rule）
- `specs/truth/features/backend/dsl.md` -> `飯店的客房有 {房號清單}`、`"{櫃檯}" 代開 "{房號}" 的以下單：`、`"{櫃檯}" 已代開 "{房號}" 的以下單：`、`這次操作被拒絕，錯誤碼為 "{錯誤碼}"`
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> 模組說明、用語對照（代開單的類型、對應品項）、本檔用到的其他需求 When 與 Then 各列
- `truth-delta.md` -> `/dsl-refine` 的 `櫃檯代開單.feature` 與 `backend/dsl.md` ADD 列；`/api-plan` 的 `POST /desk/requests`、`DeskMenuKey`、`DeskCreateRequests*`、`DeskTask.menuKey` 列；`/data-plan` 的 `tasks.note`、`tasks.task_no`、`tasks.source`、`tasks.pool_source`、`tasks` Note「櫃檯代開」段、`repair_tickets.reporter_id` 列
- `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> `deskCreateRequests`
- `specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskMenuKey`、`DeskCreateRequestsRequest`、`DeskCreateRequestItem`、`DeskCreateRequestOther`、`DeskCreateRequestsResult`、`DeskTask`
- `specs/truth/data/data-model.dbml` -> `tasks` Note「櫃檯代開」段、`tasks.note`、`tasks.task_no`、`tasks.pool_source`；`repair_tickets` Note「櫃檯代開」段
- 產品碼落點：`controller/DeskBoardController.java`、`service/DeskCreateRequestService.java`（T006）、`service/TaskEngine.java:53-113`、`repair/RepairTicketOpener.java`、`repair/RepairTicketDraft.java`、`service/DeskTaskMapper.java`、`service/DeskBoardService.java:227-280`（回應帶卡片、強制冪等鍵的既有寫法）

**Boundary**:
- 整次送出是一個 `@Transactional` 服務方法：先做完所有檢查，再逐張走 `TaskEngine.create`（房務帶 `source=desk`、`initialState=pool`、`pool_source=desk_forward`、`note={menu_key, quantity}`；工務帶 `dept=engineering`、`type=repair` 與 `RepairTicketDraft`，由 `TaskEngine.create` 在同一交易呼叫 `RepairTicketOpener.open`）。不另寫 `INSERT INTO tasks` 或 `INSERT INTO repair_tickets`。
- 標題一律由後端依 `DeskMenuKey` 組（繁體中文，冰塊帶「桶」）；其他需求的標題是去掉前後空白的原文，排在所選部門的最後。
- 不放寬 `POST /api/v1/repair-tickets` 的角色或照片規則，不動 `SecurityConfig`；`RepairService.create` 的行為不變。
- 交易內加鎖與交易內寫 `processed_ops` 留給 Phase 4-B03；本 phase 只沿用 `IdempotencyFilter` 並強制帶鍵（沒帶回 400 由 4-B02 驗）。
- 不呼叫 `RoomRequestRateLimiter`。

**Test Scope**:
- `specs/truth/features/backend/櫃檯看板與派單/櫃檯代開單.feature`

- [X] T137 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T138 [BDD-REFACTOR] 在綠燈下把「品項鍵 → 部門、類型、上限、標題、修繕分類」收成 `DeskMenuKey` 一處，服務方法裡不再有品項名稱的字面值

## Phase 4-B02: ADD Feature File - backend/櫃檯看板與派單/代開單的檢查與拒絕.feature

**Goal**: 代開單的檢查依序擋下：沒帶鍵 400、角色 403、內容不合 422（逐項 `details`）、房號不是客房 404（含公共區域佔位房），任何一種都一張不開。

**Shared Must Read**:
- `specs/truth/features/backend/櫃檯看板與派單/代開單的檢查與拒絕.feature` -> `功能: 代開單的檢查與拒絕` 全文（7 條 Rule）
- `specs/truth/features/backend/dsl.md` -> `"{櫃檯}" 代開 "{房號}" 的以下單：`（錯誤依序 403／422／404）、`這次操作被拒絕，錯誤碼為 "{錯誤碼}"`
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> `飯店有代表公共區域的佔位房`、其他需求字串與字數、什麼都沒選、不帶防重複鍵、`本案例原有的單都沒有任何改動`
- `truth-delta.md` -> `/dsl-refine` 的 `代開單的檢查與拒絕.feature` 列；`/api-plan` 的 `POST /desk/requests` 列與 `shared.yaml` `Error.description` 列
- `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> `deskCreateRequests` 的錯誤回應
- `specs/truth/contracts/components/schemas/shared.yaml` -> `Error`（`details`、`context.roomNo`）
- 產品碼落點：`service/DeskCreateRequestService.java`、`service/AutomationRuleService.java:22`（`PUBLIC_AREA_ROOM_NO`）、`service/DeskBoardService.java:276-280`

**Boundary**:
- 檢查全部在開第一張單之前做完；失敗時不取號、不寫事件、不寫 outbox。
- 60 字以去掉前後空白後的字數算；61 字 422、60 字可以。
- 不新增錯誤碼，沿用 `idempotency_key_required`、`forbidden`、`validation_failed`、`not_found`。

**Test Scope**:
- `specs/truth/features/backend/櫃檯看板與派單/代開單的檢查與拒絕.feature`
- `specs/truth/features/backend/櫃檯看板與派單/櫃檯代開單.feature`

- [X] T139 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T140 [BDD-REFACTOR] 在綠燈下把檢查整理成一個回傳全部 `details` 的驗證步驟，與開單步驟分開

## Phase 4-B03: ADD Feature File - backend/櫃檯看板與派單/代開單全有全無與重送.feature

**Goal**: 一次送出全有或全無：任一張開不成，整批回滾、流水號不跳；同一把鍵再送拿到回放；同一把鍵同時送兩次只開一批；後端出錯後同鍵再送照常開單。

**Shared Must Read**:
- `specs/truth/features/backend/櫃檯看板與派單/代開單全有全無與重送.feature` -> `功能: 代開單全有全無與重送` 全文（4 條 Rule）
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> `開 "{標題}" 這張單時後端會出錯`、`"{櫃檯}" 剛才代開…時後端出錯，一張都沒開出：`、`用同一把鍵、同樣的內容再送一次剛才那次代開`、`用同一把鍵同時送出兩次代開…`、`這次代開失敗…`、`這次拿到的是第一次的結果…`、`兩次送出拿到同一批單…`
- `truth-delta.md` -> `/dsl-refine` 的 `代開單全有全無與重送.feature` 列；`/api-plan` 的 `POST /desk/requests` 列（交易鎖、交易內記冪等、4xx 也記下、`Idempotency-Replayed`）；`/data-plan` 的 `processed_ops` Note 列；`/technical-research` 的「冪等」與「櫃檯 Web 送出失敗的模擬」列
- `specs/truth/techstack.md` -> 技術堆疊總覽 / 資料與媒體處理 / 冪等；測試與驗證 / 櫃檯 Web 送出失敗的模擬
- `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> `deskCreateRequests` 的冪等說明
- `specs/truth/data/data-model.dbml` -> `processed_ops` Note；`tasks` Note「櫃檯代開」段的「同一交易全有或全無、取號回滾不跳號」
- 產品碼落點：`service/DeskCreateRequestService.java`、`filter/IdempotencyFilter.java:35-67`、`service/IdempotencyService.java:24-31`；T001、T002 在 `implement-notes.md` 的結論

**Boundary**:
- 交易一開始以 T001 定下的換算對「飯店＋鍵」取 `pg_advisory_xact_lock`，鎖內查 `processed_ops`，有就回放；沒有就開單，並在同一個交易裡寫 `processed_ops`（4xx 也寫）。`IdempotencyFilter` 之後的寫入靠 `ON CONFLICT DO NOTHING` 略過，不覆寫。
- 只用在代開單這個端點；其他端點的冪等行為不變。
- 正式程式不加任何「讓第 N 張失敗」的測試開關；失敗只靠 T007 的暫時觸發器製造。
- 綠了之後刪掉 T001、T002 的 spike 測試。

**Test Scope**:
- `specs/truth/features/backend/櫃檯看板與派單/代開單全有全無與重送.feature`
- `specs/truth/features/backend/櫃檯看板與派單/`（整個模組，確認派單、復原、當場回答的冪等沒被改到）

- [X] T141 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T142 [BDD-REFACTOR] 在綠燈下把「交易內鎖鍵、查、記」收成一個只給代開單用的小元件，服務方法只呼叫它一次

## Phase 4-B04: ADD Feature File - backend/櫃檯看板與派單/代開的單不算住客額度.feature

**Goal**: 住客額度滿時櫃檯照樣代開成功；代開的單不佔住客名額；住客自己的第 4 筆照舊被擋。

**Shared Must Read**:
- `specs/truth/features/backend/櫃檯看板與派單/代開的單不算住客額度.feature` -> `功能: 代開的單不算住客的每房額度` 全文（3 條 Rule）
- `specs/truth/features/backend/dsl.md` -> `住客 10 分鐘內已在 "{房號}" 送出 {筆數} 筆需求`、`住客在 "{房號}" 掃碼要 "{品項}"`、`這次請求被擋下，提示住客洽櫃檯`、`"{櫃檯}" 已代開 "{房號}" 的以下單：`
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> `住客這次送出的需求開單成功`
- `truth-delta.md` -> `/dsl-refine` 的 `代開的單不算住客額度.feature` 列與 `backend/dsl.md` 三句搬移列；`/api-plan` 的 `createGuestRequest` 每房額度列
- `specs/truth/contracts/groups/住客與房間/住客掃碼.yaml` -> `createGuestRequest` 每房額度；`components/responses.yaml` -> `TooManyRequests`
- 產品碼落點：`service/RoomRequestRateLimiter.java:18-32`、`service/GuestRequestService.java:66-80`

**Boundary**:
- 本分支的額度是記憶體計數、只在住客送需求時呼叫（Scope Note 第 5 條），預期不改產品碼就綠；代開流程不得呼叫 `RoomRequestRateLimiter`。
- 實作期間本分支若合入 `refactor/main_comments_and_workarounds`（額度改成資料庫現數、條件 `source IN ('guest', 'desk')`），在本題把條件改成只數 `guest`，並在 `implement-notes.md` 記下。

**Test Scope**:
- `specs/truth/features/backend/櫃檯看板與派單/代開的單不算住客額度.feature`
- `specs/truth/features/backend/推播/員工推播事件.feature`（三個句型搬了權威位置，住客第 4 筆被擋的 Rule 必須照舊）

- [X] T143 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T144 [BDD-REFACTOR] 在綠燈下確認代開服務與住客限流之間沒有任何呼叫或共用計數，額度的說明只留在 `GuestRequestService` 一處

## Phase 4-B05: ADD Feature File - backend/櫃檯看板與派單/代開單的房間清單.feature

**Goal**: 新增代開單面板的房間清單：全部客房含空房、在住住客的姓名、VIP、入住退房日；排除公共區域佔位房；已退房、未入住、已取代的訂房不算在住；三種櫃檯角色可讀，房務、工務、老闆 403。

**Shared Must Read**:
- `specs/truth/features/backend/櫃檯看板與派單/代開單的房間清單.feature` -> `功能: 代開單的房間清單` 全文（5 條 Rule）
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> `以下房間現在有住客：`（相對日）、`飯店有代表公共區域的佔位房`、`以下房間的訂房現在不算在住：`、`"{員工}" 讀取代開單的房間清單`、`代開單的房間清單剛好如下：`
- `truth-delta.md` -> `/dsl-refine` 的 `代開單的房間清單.feature` 列與 `櫃檯看板與派單/dsl.md` MODIFY 列；`/api-plan` 的 `GET /desk/rooms`、`DeskRoom`、`DeskRoomGuest` 列
- `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> `listDeskRooms`
- `specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskRoom`、`DeskRoomGuest`
- `specs/truth/data/data-model.dbml` -> `rooms`、`reservations_cache`（`guest_name`、`attrs.vip`、`checkin_at`、`checkout_at`、`superseded_at`）
- 產品碼落點：`service/DeskCreateRequestService.java`、`service/DeskTaskMapper.java`（`DeskTask.guestName` 的在住認定，同一規則）、`service/AutomationRuleService.java:22`

**Boundary**:
- 在住的認定與看板卡片 `guestName` 用同一段查詢或同一個方法，不另寫一份。
- 不實作契約裡既有的 `/rooms` 清單，不動房況板端點。

**Test Scope**:
- `specs/truth/features/backend/櫃檯看板與派單/代開單的房間清單.feature`
- `specs/truth/features/backend/櫃檯看板與派單/櫃檯看板讀取.feature`（共用在住認定）

- [X] T145 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T146 [BDD-REFACTOR] 在綠燈下把在住認定收成房間清單與看板卡片共用的一處

## Phase 4-B06: ADD Feature File - backend/櫃檯看板與派單/看板卡片的對應品項.feature

**Goal**: 看板卡片帶可為 null 的 `menuKey`：代開品項單（含工務）帶品項鍵、其他需求沒有；住客勾選帶選單鍵；住客打字命中帶 `menu_key` 的分類規則帶鍵（派給工務後照樣帶）；沒命中沒有。

**Shared Must Read**:
- `specs/truth/features/backend/櫃檯看板與派單/看板卡片的對應品項.feature` -> `功能: 看板卡片的對應品項` 全文（4 條 Rule）
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> `"{房號}" 的住客已勾選服務項目 "{項目}" 送出`、`"{房號}" 的住客已打字送出 "{內容}"`、`飯店的分類規則把含…，對應品項 "{品項}"`、`櫃檯看板上各單的對應品項如下：`
- `truth-delta.md` -> `/dsl-refine` 的 `看板卡片的對應品項.feature` 列；`/api-plan` 的 `DeskTask.menuKey`、`RequestMenuItem`／`ClassificationRule`、`租戶設定.yaml` 列；`/data-plan` 的 `tenant_config` Note、`tasks.note` 列
- `specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskTask.menuKey`
- `specs/truth/contracts/components/schemas/租戶設定.yaml` -> `ClassificationRule.menu_key`
- `specs/truth/contracts/groups/現場作業/員工事件串流.yaml` -> `StaffStreamTask.card`（同一張卡片多一欄）
- `specs/truth/data/data-model.dbml` -> `tenant_config` Note（`classification_rules`、`request_menu`）、`tasks.note`
- 產品碼落點：`service/DeskTaskMapper.java:44`、`:102-120`（與 `guestTyped` 同一種推法）、`service/GuestTenantConfigReader.java:97`、`service/GuestRequestService.java:113-144`

**Boundary**:
- `menuKey` 只從 `tasks.note.menu_key` 讀，不另存欄位、不加 migration。
- 分類規則沒填 `menu_key` 時分流與 note 都與現在相同；命中時 `menu_key` 與 `rule_id` 並存。
- 派單、復原、當場回答、單筆詳情與串流卡片因同一個 `DeskTask` 多一欄，既有欄位不變。

**Test Scope**:
- `specs/truth/features/backend/櫃檯看板與派單/看板卡片的對應品項.feature`
- `specs/truth/features/backend/櫃檯看板與派單/`（整個模組）
- `specs/truth/features/backend/員工事件串流/`（卡片多一欄，訊號卡片與看板一致的斷言要照舊綠）

- [X] T147 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T148 [BDD-REFACTOR] 在綠燈下把住客勾選、住客打字、櫃檯代開三處寫 `menu_key` 的地方收成同一個 note 組法

## Phase 4-B07: ADD Feature File - backend/工務單/櫃檯代開的工務單.feature

**Goal**: 代開的工務單依路徑決定規則：免照片、一律 `desk`＋`desk_forward`、報修人是送出者（三種角色、不看部門）、標題與說明相同、修繕分類照對照；直接進工務待認領且來源「櫃檯」；之後完修與主管驗收照舊結案。`createRepairTicket` 的照片、依部門決定來源、角色放行全部不變。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/櫃檯代開的工務單.feature` -> `功能: 櫃檯代開的工務單` 全文（6 條 Rule）
- `specs/truth/features/backend/工務單/dsl.md` -> 模組說明「櫃檯代開」段、`代開的工務單如下：`，以及本檔用到的完修、驗收、工務待認領各列
- `specs/truth/features/backend/dsl.md` -> `"{櫃檯}" 代開 "{房號}" 的以下單：`、`"{櫃檯}" 已代開 "{房號}" 的以下單：`
- `truth-delta.md` -> `/dsl-refine` 的 `櫃檯代開的工務單.feature` 與 `工務單/dsl.md` 列；`/api-plan` 的 `createRepairTicket` 只補說明列；`/data-plan` 的 `repair_tickets` 各列（`reporter_id`、照片要求、櫃檯代開段、工務直開欄位對應、`origin`）
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `createRepairTicket` 描述（只屬於本端點的規則）、`readRepairTicket`
- `specs/truth/contracts/groups/現場作業/員工工作台.yaml` -> `listDeptPool`
- `specs/truth/data/data-model.dbml` -> `repair_tickets` Note「櫃檯代開」段、照片要求、「工務直開欄位對應」；`repair_tickets.reporter_id`、`origin`
- 產品碼落點：`repair/RepairTicketOpener.java:35-131`、`repair/RepairTicketDraft.java`、`repair/RepairService.java:468-563`（照片、`directOpenSource`、標題寫死的位置）、`service/DeskCreateRequestService.java`

**Boundary**:
- 照片要求、來源、`pool_source`、標題與說明由開單路徑決定：`RepairTicketDraft` 帶得出這幾項（或由呼叫者在同一交易補上），`RepairService.create` 傳入原本的值，行為不變。
- 修繕分類對照：冷氣→`air_conditioning`、熱水與燈具→`plumbing_electrical`、電視→`furniture_equipment`、其他需求→`other`；寫在 `DeskMenuKey`，不另寫一份。
- 急件判定照既有規則（`RepairTicketOpener`），櫃檯畫面不顯示急件。
- 不放寬 `POST /api/v1/repair-tickets`（`SecurityConfig.java:71-72`）。

**Test Scope**:
- `specs/truth/features/backend/工務單/櫃檯代開的工務單.feature`
- `specs/truth/features/backend/工務單/`（整個模組；`開立工務單與急件.feature`、`照片完整性與冪等送出.feature`、`櫃檯轉來的工務單.feature` 是 NOOP，必須照舊綠）
- `specs/truth/features/backend/待認領/`（工務待認領的來源標示）

- [X] T149 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T150 [BDD-REFACTOR] 在綠燈下確認開工務單的三條路徑（工務單端點、櫃檯轉單、櫃檯代開）共用取號、急件判定與推播，各自的規則只在自己的呼叫端決定；`INSERT INTO repair_tickets` 在 `tms/src/main` 仍只有 `RepairTicketOpener` 一處

## Phase 4-B08: MODIFY Feature File - backend/員工事件串流/收得到哪些單的訊號.feature

**Goal**: 櫃檯代開的單，其他櫃檯與那張單所在部門的同仁都收得到「建立」訊號。

**Shared Must Read**:
- `specs/truth/features/backend/員工事件串流/收得到哪些單的訊號.feature` -> Rule「櫃檯代開的單，其他櫃檯與那張單所在部門的同仁都收得到建立的訊號」
- `specs/truth/features/backend/員工事件串流/dsl.md` -> 用語對照「訊號」、`"{員工}" 的串流收到 "{任務}" 的 "{訊號}" 訊號`
- `specs/truth/features/backend/dsl.md` -> `飯店的客房有 {房號清單}`、`"{櫃檯}" 代開 "{房號}" 的以下單：`
- `truth-delta.md` -> `/dsl-refine` 的 `收得到哪些單的訊號.feature` 與 `員工事件串流/dsl.md` 列；`/api-plan` 的 `staffStream` NOOP 列
- `specs/truth/contracts/groups/現場作業/員工事件串流.yaml` -> `staffStream`、`StaffStreamTask`

**Boundary**:
- 預期產品碼不用改（`created` 事件照既有規則經 outbox 送出）；紅了只查代開的單是否每張都寫了 `created` 事件與 outbox，不新增訊號種類或推播事件。

**Test Scope**:
- `specs/truth/features/backend/員工事件串流/收得到哪些單的訊號.feature`

- [X] T151 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T152 [BDD-REFACTOR] 在綠燈下確認代開的每一張單都只寫一筆 `created` 與一則 task outbox，沒有因為同一次送出而合併或重複

## Phase 4-W01: MODIFY Feature File - frontdesk-web/共用外框/尚未開放的入口.feature

**Goal**: 「代開單」不再是尚未開放的入口；「通知板」「遺失物」照舊點了沒反應。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/共用外框/尚未開放的入口.feature` -> Rule「「通知板」與「遺失物」照原型畫出來，點了沒有反應，也不會改到任何資料」
- `specs/truth/features/frontdesk-web/共用外框/dsl.md` -> 模組說明（尚未開放的入口只剩側欄兩項）
- `truth-delta.md` -> `/dsl-refine` 的 `尚未開放的入口.feature` 與 `共用外框/dsl.md` MODIFY 列
- `ui/ui-plan.md` -> 畫面 1「代開單入口、面板外框與關閉」
- `ui/board.html` -> 工具列「代開單」的雛形
- 產品碼落點：`app/web/src/shell/unopened-entries.ts`、`shell/DeskShell.tsx`

**Boundary**:
- 只從尚未開放的清單拿掉 `shell.createRequest`；工具列按鈕接到打開面板留給 Phase 4-W02。

**Test Scope**:
- `specs/truth/features/frontdesk-web/共用外框/尚未開放的入口.feature`
- `specs/truth/features/frontdesk-web/共用外框/側欄與工具列.feature`（NOOP，必須照舊綠）

- [X] T153 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T154 [BDD-REFACTOR] 在綠燈下確認 `unopened-entries.ts` 不再有任何代開單的鍵或文字

## Phase 4-W02: ADD Feature File - frontdesk-web/代開單/入口與空白面板.feature

**Goal**: 工具列在總覽看板與待派單頁都能打開代開單面板；「代開單」是唯一的實心主按鈕、只有工具列有入口；空白面板的內容與順序；詳情面板開著時預填房號；三種角色看到的面板相同。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/代開單/入口與空白面板.feature` -> `功能: 代開單的入口與空白面板` 全文（7 條 Rule）
- `specs/truth/features/frontdesk-web/代開單/dsl.md` -> 模組說明、常用品項表，本檔用到的 Given／When／Then 各列
- `specs/truth/features/frontdesk-web/dsl.md` -> 共通實作約定、`全館有以下的單：`
- `truth-delta.md` -> `/dsl-refine` 的 `代開單/dsl.md` 與 `入口與空白面板.feature` ADD 列；`/technical-research` 的「櫃檯 Web 框架與建置」「櫃檯 Web 換頁」列
- `specs/truth/techstack.md` -> 前端 / 櫃檯 Web 框架與建置、櫃檯 Web 換頁（面板不新增路由）
- `ui/ui-plan.md` -> 本檔的四個決定、視覺方向、版面尺寸、畫面 1「代開單入口、面板外框與關閉」、畫面 3 的 12 格樣子
- `ui/board.html`、`ui/triage.html`、`ui/create-request.css`、`ui/create-request.js` -> 面板外框、✕ 圓鈕、遮罩、空白狀態的雛形
- `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> `listDeskRooms`（面板打開時讀一次）
- 產品碼落點：`app/web/src/features/create-request/CreateRequestPanel.tsx`、`stores/desk-panels.ts`、`shell/DeskShell.tsx`、`i18n/locales/*`

**Boundary**:
- 面板蓋在頁面上、不新增路由；面板內容放元件自己的狀態，不進 `Zustand`。
- ✕ 圓鈕與全圓角分段選擇做成 `components/` 的共用元件（`ui/ui-plan.md` 前端實作切分建議）。
- 畫面文字一律走 `i18n/` 四份語系檔；繁體中文照 feature。

**Test Scope**:
- `specs/truth/features/frontdesk-web/代開單/入口與空白面板.feature`

- [X] T155 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T156 [BDD-REFACTOR] 在綠燈下把預填房號的取法（從 `detailTaskId` 對到房號）收在 `desk-panels` 一處

## Phase 4-W03: ADD Feature File - frontdesk-web/代開單/關閉不確認也不保留.feature

**Goal**: ✕ 與遮罩直接關、不確認、不開單；再打開是空白；不關底下的詳情面板；預填只在那一次。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/代開單/關閉不確認也不保留.feature` -> `功能: 代開單面板關閉不確認也不保留` 全文（5 條 Rule）
- `specs/truth/features/frontdesk-web/代開單/dsl.md` -> 本檔用到的各列
- `truth-delta.md` -> `/dsl-refine` 的 `關閉不確認也不保留.feature` 列
- `ui/ui-plan.md` -> 畫面 1 的關閉段
- `ui/board.html`、`ui/create-request.js` -> 關閉的互動雛形
- 產品碼落點：`app/web/src/features/create-request/`

**Boundary**:
- 關閉就丟掉面板內容與這次的冪等鍵；不影響 `desk-panels` 的詳情面板狀態。

**Test Scope**:
- `specs/truth/features/frontdesk-web/代開單/關閉不確認也不保留.feature`

- [X] T157 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T158 [BDD-REFACTOR] 在綠燈下確認面板卸載就清空狀態，沒有任何殘留在 store 或模組層變數

## Phase 4-W04: ADD Feature File - frontdesk-web/代開單/房號欄與住客卡.feature

**Goal**: 房號欄只收半形數字、最多 4 碼；空白與打開頭時最多 6 筆住客房建議；點建議出住客卡與 VIP 標；刪成無效時住客卡消失；空房提示可送；不存在的房號什麼都不顯示、不能送、沒有紅字。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/代開單/房號欄與住客卡.feature` -> `功能: 輸入房號時確認是對的房間與住客` 全文（9 條 Rule）
- `specs/truth/features/frontdesk-web/代開單/dsl.md` -> 「房號有效」與本檔用到的各列
- `truth-delta.md` -> `/dsl-refine` 的 `房號欄與住客卡.feature` 列；`/api-plan` 的 `GET /desk/rooms`、`DeskRoom`、`DeskRoomGuest` 列；`/technical-research` 的「Web 單元測試」列
- `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> `listDeskRooms`
- `specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskRoom`、`DeskRoomGuest`
- `specs/truth/techstack.md` -> 測試與驗證 / Web 單元測試
- `ui/ui-plan.md` -> 畫面 2「房號區」（含清單讀取中與讀取失敗）
- `ui/board.html`、`ui/create-request.js` -> 房號區雛形
- 產品碼落點：`app/web/src/features/create-request/RoomSection.tsx`、`create-request-logic.ts`

**Boundary**:
- 房號過濾、房號有效、建議房號（篩選、排序、取 6）、空房與住客判斷是純函式，補 `Vitest`；畫面只呼叫它們。
- 讀取失敗時照 `ui/ui-plan.md` 放寬成有數字就能送（Scope Note 第 8 條）。

**Test Scope**:
- `specs/truth/features/frontdesk-web/代開單/房號欄與住客卡.feature`
- `cd app/web && npx vitest run src/features/create-request`

- [X] T159 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T160 [BDD-REFACTOR] 在綠燈下整理房號區的純函式與元件邊界，元件裡不留判斷邏輯

## Phase 4-W05: ADD Feature File - frontdesk-web/代開單/常用品項與送出鈕.feature

**Goal**: 徽章與步進器、一品項一張、減到 0、上限 2 與 9、冰塊摘要不帶單位、工務品項「移除」且只開一張；摘要一段與兩段；房號無效或沒選時的送出鈕；無障礙名稱。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/代開單/常用品項與送出鈕.feature` -> `功能: 常用品項、送出摘要與送出鈕` 全文（12 條 Rule）
- `specs/truth/features/frontdesk-web/代開單/dsl.md` -> 常用品項表、送出摘要的組法與本檔用到的各列
- `truth-delta.md` -> `/dsl-refine` 的 `常用品項與送出鈕.feature` 列；`/api-plan` 的 `DeskMenuKey` 列；`/technical-research` 的「Web 單元測試」列
- `specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskMenuKey`（前端照抄的 12 鍵、部門、上限）
- `specs/truth/techstack.md` -> 測試與驗證 / Web 單元測試
- `ui/ui-plan.md` -> 畫面 3「常用品項、其他需求、送出摘要與送出鈕」、四種語言的面板文字
- `ui/board.html`、`ui/create-request.css`、`ui/create-request.js` -> 12 格、步進器、摘要與送出鈕雛形
- 產品碼落點：`app/web/src/features/create-request/ItemGrid.tsx`、`CreateRequestPanel.tsx`、`create-request-logic.ts`

**Boundary**:
- n、送出鈕文字與能不能按、摘要（含四種語言的分隔符）是純函式，補 `Vitest`。
- 12 個品項鍵、部門、上限照契約 `DeskMenuKey`；前端不另發明順序。

**Test Scope**:
- `specs/truth/features/frontdesk-web/代開單/常用品項與送出鈕.feature`
- `cd app/web && npx vitest run src/features/create-request`

- [X] T161 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T162 [BDD-REFACTOR] 在綠燈下把 12 品項的前端定義收成一份常數，格子、摘要、上限都讀它

## Phase 4-W06: ADD Feature File - frontdesk-web/代開單/其他需求.feature

**Goal**: 其他需求的標籤與提示字；有字才出現「這段要給」且預設房務；給房務與給工務的摘要與開出的單；排在所選部門段最後；空白不算；60 字上限、無計數；去前後空白；英文介面照原文。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/代開單/其他需求.feature` -> `功能: 常用品項沒有的需求用其他需求交給房務或工務` 全文（9 條 Rule）
- `specs/truth/features/frontdesk-web/代開單/dsl.md` -> 送出摘要的組法與本檔用到的各列
- `specs/truth/features/frontdesk-web/dsl.md` -> `"{員工}" 已登入櫃檯畫面，介面語言是 "{語言}"，停在 "{頁}"`
- `truth-delta.md` -> `/dsl-refine` 的 `其他需求.feature` 列；`/api-plan` 的 `DeskCreateRequestOther` 列
- `specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskCreateRequestOther`
- `ui/ui-plan.md` -> 畫面 3 的其他需求段、四種語言的面板文字
- `ui/board.html`、`ui/create-request.js` -> 其他需求框與部門分段選擇雛形
- 產品碼落點：`app/web/src/features/create-request/OtherRequest.tsx`、`create-request-logic.ts`

**Boundary**:
- 輸入框擋在 60 字（貼上超量只留前 60 字），不顯示計數；送出前去前後空白。
- 部門分段選擇用 Phase 4-W02 的共用元件。

**Test Scope**:
- `specs/truth/features/frontdesk-web/代開單/其他需求.feature`
- `cd app/web && npx vitest run src/features/create-request`

- [X] T163 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T164 [BDD-REFACTOR] 在綠燈下確認摘要與送出請求對其他需求的「有字才算」用同一個判斷

## Phase 4-W07: ADD Feature File - frontdesk-web/代開單/類似單提醒.feature

**Goal**: 同房未結束、對應品項相同（櫃檯代開、住客勾選、住客打字已對應）時提醒；不擋送出；負責人與「剛剛建立」、新到舊；其他需求有字時列住客打字單；已完成、中斷、延後中、別間房不提醒；房號品項其他需求一改立刻重算。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/代開單/類似單提醒.feature` -> `功能: 選品項時提醒這間房已有類似的單` 全文（7 條 Rule）
- `specs/truth/features/frontdesk-web/代開單/dsl.md` -> 類似單提醒各列
- `specs/truth/features/frontdesk-web/dsl.md` -> `全館有以下的單：`（`對應品項` 欄）、單的狀況對照
- `truth-delta.md` -> `/dsl-refine` 的 `類似單提醒.feature` 與介面根 `全館有以下的單：` MODIFY 列；`/api-plan` 的 `DeskTask.menuKey` 列
- `specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskTask`（`menuKey`、`guestTyped`、`state`、`assignee`、`createdAt`）
- `ui/ui-plan.md` -> 畫面 4「類似單提醒」、「多久前」的時鐘做法
- `ui/board.html`、`ui/create-request.js` -> 305 與 220 的類似單示範
- 產品碼落點：`app/web/src/features/create-request/SimilarTasksNotice.tsx`、`create-request-logic.ts`；沿用 `stores/server-clock.ts` 的 `useCorrectedNow`

**Boundary**:
- 從 `TanStack Query` 快取裡的看板資料算，不另打 API；只取待派單、待認領、已接、進行中四種狀態。
- 類似單比對、每一行的文字與排序、「多久前」是純函式，補 `Vitest`。

**Test Scope**:
- `specs/truth/features/frontdesk-web/代開單/類似單提醒.feature`
- `cd app/web && npx vitest run src/features/create-request`

- [X] T165 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T166 [BDD-REFACTOR] 在綠燈下確認提醒元件只依看板快取與面板內容重算，沒有自己的計時器或額外請求

## Phase 4-W08: ADD Feature File - frontdesk-web/代開單/送出成功回到看板.feature

**Goal**: 送出成功：面板關、換到總覽看板、清篩選與搜尋、關詳情；新卡片在待認領欄高亮 2.6 秒；沒有浮條與復原；卡片來源「櫃檯開單」；後端開出的房務單與工務單欄位正確；主管與值班主管結果相同；詳情「櫃檯代開（{姓名}）」與歷程第一句。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/代開單/送出成功回到看板.feature` -> `功能: 送出成功後回到總覽看板` 全文（9 條 Rule）
- `specs/truth/features/frontdesk-web/代開單/dsl.md` -> 送出後各列（換頁、高亮、卡片、無浮條、後端開出的單）
- `specs/truth/features/frontdesk-web/dsl.md` -> `詳情面板「內容」的 "{列名}" 是 "{內容}"`、`詳情面板的歷程最新一筆是 "{句子}"，操作者是 "{操作者}"`
- `truth-delta.md` -> `/dsl-refine` 的 `送出成功回到看板.feature` 與介面根兩句搬移列；`/api-plan` 的 `POST /desk/requests`、`DeskCreateRequestsResult` 列
- `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> `deskCreateRequests` 的 201 回應
- `specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskCreateRequestsResult`、`DeskTask`
- `ui/ui-plan.md` -> 畫面 5「送出中、送出失敗與送出後回到看板」、決定三（新卡片高亮與 004 卡片變化的區分）
- `ui/board.html`、`ui/triage.html`、`ui/desk.js`、`ui/create-request.js` -> 送出後換頁與 2.6 秒高亮雛形
- 產品碼落點：`app/web/src/features/create-request/use-create-request.ts`、看板卡片元件、只放記憶體的「這次新開的卡片與到期時刻」小 store

**Boundary**:
- 回應帶回的卡片直接放進看板快取再高亮；之後的即時訊號照 004 換上同一張卡片，不重抓整份看板。
- 焦點移到第一張新卡片；不加任何浮條或螢幕報讀專用狀態文字（Scope Note 第 8 條）。

**Test Scope**:
- `specs/truth/features/frontdesk-web/代開單/送出成功回到看板.feature`
- `specs/truth/features/frontdesk-web/總覽看板/卡片內容.feature`、`單筆詳情/詳情面板內容.feature`、`單筆詳情/歷程.feature`（NOOP，必須照舊綠）

- [X] T167 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T168 [BDD-REFACTOR] 在綠燈下把高亮的到期清除與 004 卡片閃框收成同一套樣式，只差時長與觸發來源

## Phase 4-W09: ADD Feature File - frontdesk-web/代開單/送出失敗與重送.feature

**Goal**: 四種「沒有送出」提示文字；按「好」後內容全留且可再按；網路恢復後再送不重複；回應遺失後再送拿同一次結果；送出中不能再按；登入逾時回登入頁；任一張開不成全不留。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/代開單/送出失敗與重送.feature` -> `功能: 一次送出全部開成或全部不開，送不成時保留內容` 全文（10 條 Rule）
- `specs/truth/features/frontdesk-web/代開單/dsl.md` -> 失敗前提各列、`代開單面板上的內容與按送出之前一樣`、後端開出的單各列、`送出還沒有結果之前…`、`畫面回到登入頁…`
- `specs/truth/features/frontdesk-web/dsl.md` -> `接下來的送出會因為網路中斷而失敗`
- `truth-delta.md` -> `/dsl-refine` 的 `送出失敗與重送.feature` 與介面根網路中斷 MODIFY 列；`/api-plan` 的 `POST /desk/requests`（4xx 也記下、回放）列；`/technical-research` 的「冪等」「櫃檯 Web 送出失敗的模擬」「Web 單元測試」列
- `specs/truth/techstack.md` -> 資料與媒體處理 / 冪等；測試與驗證 / 櫃檯 Web 送出失敗的模擬、Web 單元測試
- `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> `deskCreateRequests` 的錯誤回應與冪等說明
- `ui/ui-plan.md` -> 畫面 5 的失敗段、驗證與錯誤回饋、待決 4（15 秒逾時）
- `ui/board.html`、`ui/triage.html`（網址參數切換失敗情形）、`ui/create-request.js`
- 產品碼落點：`app/web/src/features/create-request/use-create-request.ts`、`components/AlertDialog.tsx`、`api/client.ts`

**Boundary**:
- 冪等鍵跟著內容走：第一次按送出時產生；沒收到回應、逾時或 5xx 時保留；房號、任一品項數量、其他需求文字或部門一改就丟；收到 4xx 也丟。這組規則與「失敗回應對到哪一種提示」是純函式，補 `Vitest`。
- 提示沿用 004 的 `AlertDialog`；送出中送出鈕不能按（`useMutation` 進行中狀態）。
- 代開單寫入 15 秒逾時只加在這支寫入，不改其他櫃檯寫入。

**Test Scope**:
- `specs/truth/features/frontdesk-web/代開單/送出失敗與重送.feature`
- `specs/truth/features/frontdesk-web/當場回答/撞單與送出失敗.feature`、`待派單/撞單與送出失敗.feature`（共用 `接下來的送出會因為網路中斷而失敗` 與 `AlertDialog`，必須照舊綠）
- `cd app/web && npx vitest run src/features/create-request`

- [X] T169 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T170 [BDD-REFACTOR] 在綠燈下把冪等鍵的保留與丟棄收在 `use-create-request.ts` 一處，元件只看「送出中／失敗種類」

## Phase 4-W10: ADD Feature File - frontdesk-web/代開單/兩種尺寸與四種語言.feature

**Goal**: 1280 × 720 與 1180 × 820 下面板內捲動、送出鈕看得到；可點的東西至少 44 × 44；沒有只靠游標的東西；滑鼠與觸控都能送出；日文介面文字；開出的單標題一律繁體中文。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/代開單/兩種尺寸與四種語言.feature` -> `功能: 代開單面板在兩種尺寸與四種語言下都能用` 全文（6 條 Rule）
- `specs/truth/features/frontdesk-web/代開單/dsl.md` -> `代開單面板的介面文字都是 "{語言}"`、`代開單面板完整顯示在視窗內…`、`…都至少 44 × 44`
- `specs/truth/features/frontdesk-web/dsl.md` -> `這個畫面上每一項內容與每一顆可以按的東西，不用游標移上去就看得到、按得到`、`"{員工}" 已登入櫃檯畫面，介面語言是 "{語言}"，停在 "{頁}"`
- `truth-delta.md` -> `/dsl-refine` 的 `兩種尺寸與四種語言.feature` 與介面根兩句搬移列
- `ui/ui-plan.md` -> 版面尺寸、四種語言的面板文字（含標「自譯」的字）
- `ui/board.html`、`ui/create-request.css` -> 兩種尺寸下的面板高度與捲動
- 產品碼落點：`app/web/src/features/create-request/`、`i18n/locales/zh-TW.ts`、`en.ts`、`ja.ts`、`vi.ts`

**Boundary**:
- 四份語系檔的鍵與參數一致；標題與櫃檯打的其他需求不隨介面語言換。

**Test Scope**:
- `specs/truth/features/frontdesk-web/代開單/兩種尺寸與四種語言.feature`（`desktop` 與 `ipad` 各一次）
- `cd app/web && npx vitest run src/i18n/locales/locales.test.ts`

- [X] T171 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T172 [BDD-REFACTOR] 在綠燈下確認面板的尺寸只用 `@theme` token 與既有斷點，沒有寫死像素的特例

## Phase 4-W11: MODIFY Feature File - frontdesk-web/即時更新/現場變化即時跟上.feature

**Goal**: 另一台櫃檯代開的單，這一台的看板不用重新整理就多出那幾張待認領的卡片。

**Shared Must Read**:
- `specs/truth/features/frontdesk-web/即時更新/現場變化即時跟上.feature` -> Rule「另一台櫃檯代開的單，這一台的看板不用重新整理就多出那幾張待認領的卡片」
- `specs/truth/features/frontdesk-web/即時更新/dsl.md` -> `"{同事}" 在另一台櫃檯代開 "{房號}" 的以下單：`
- `truth-delta.md` -> `/dsl-refine` 的 `現場變化即時跟上.feature` 與 `即時更新/dsl.md` ADD 列；`/api-plan` 的 `staffStream` NOOP 列
- `specs/truth/contracts/groups/現場作業/員工事件串流.yaml` -> `StaffStreamTask.card`
- `ui/ui-plan.md` -> 給 `/api-plan` 的資料需求「事件串流」段
- 產品碼落點：`app/web/src/features/realtime/`

**Boundary**:
- 預期產品碼不用改（`created` 訊號帶卡片照 004 的做法插進看板）；紅了只查訊號帶的卡片是否含 `menuKey` 與看板一致。

**Test Scope**:
- `specs/truth/features/frontdesk-web/即時更新/現場變化即時跟上.feature`

- [X] T173 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T174 [BDD-REFACTOR] 在綠燈下確認即時插入的卡片與送出回應放進快取的卡片走同一個合併函式

## Phase 4-WG: ui quality gate

**Goal**: 產品對齊本輪回追到的 plan-side 產物。`Test Scope` 全綠不能代替本題。

**Read**:
- `ui/ui-plan.md` -> 本檔的四個決定、視覺方向、版面尺寸、畫面與流程 1–5、互動與假資料原則、狀態與資訊揭露、驗證與錯誤回饋、四種語言的面板文字、靜態雛形輸出規劃的主要路徑對照、前端實作切分建議、與原型不同的地方、待決事項
- `ui/index.html`、`ui/board.html`、`ui/triage.html`、`ui/create-request.css`、`ui/create-request.js`、`ui/desk.css`、`ui/desk.js`
- `app/web/src/features/create-request/`、`app/web/src/shell/`、`app/web/src/components/`、`app/web/src/stores/desk-panels.ts`、`app/web/src/i18n/locales/`

**Boundary**:
- 不改 truth、不加 feature、不重跑 GREEN 當通過條件。
- 兩組視窗（1280 × 720 與 1180 × 820）各看一次。重點比對：✕ 圓鈕與遮罩、面板寬度圓角陰影；房號區的建議、住客卡、空房提示、讀取中與讀取失敗；12 格的徽章、選取樣子、步進器與「移除」；其他需求框與全圓角分段選擇；類似單提醒每一行的寫法；摘要與送出鈕四種樣子；四種失敗提示；送出後 2.6 秒高亮與 004 卡片閃框的區分。
- 雛形沒有做出來的狀態（`ui/ui-plan.md`「靜態雛形輸出規劃」最後一段）以 `ui/ui-plan.md` 的文字為準；雛形示範資料與 truth 不同之處以 truth 為準。
- 畫面上不得出現內部值、錯誤碼、急件標示。

- [X] T175 subagent review (ui quality gate)

## Phase 4-G01: MODIFY Feature File - guest-web/設備報修/報修的限流.feature

**Goal**: 「估算通過但後端拒絕」的前提改成同一間房的另一支手機剛又送了 1 張；When 與 Then 不變。

**Shared Must Read**:
- `specs/truth/features/guest-web/設備報修/報修的限流.feature` -> Rule「估算通過但後端拒絕時，顯示同一個提示，不出現成功頁」
- `specs/truth/features/guest-web/dsl.md` -> 飯店與房間說明（另一支手機）、`"{房號}" 房的另一支手機在 "{時刻}" 送出了一張單`
- `truth-delta.md` -> `/dsl-refine` 的 guest-web `dsl.md` 與 `報修的限流.feature` MODIFY 列；`/api-plan` 的 `createGuestRequest` 每房額度列
- `specs/truth/contracts/groups/住客與房間/住客掃碼.yaml` -> `createGuestRequest` 每房額度

**Boundary**:
- 預期產品碼不用改（住客額度照舊數住客送的需求）；紅了先查 T135 的 stepdef 是否真的扣住串流訊號、是否以住客身分送出，不改住客頁。

**Test Scope**:
- `specs/truth/features/guest-web/設備報修/報修的限流.feature`（`phone`）

- [X] T176 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T177 [BDD-REFACTOR] 在綠燈下確認住客頁測試線已沒有任何「櫃檯代開算進住客額度」的前提或註解

## Phase 4-G02: MODIFY Feature File - guest-web/備品補充/後端在送到一半拒絕.feature

**Goal**: 三個例子（第二張被擋、衣架建立礦泉水被擋、這間房已經滿了）的前提改成同一間房的另一支手機在 14:03 送出了一張單；When 與 Then 不變。

**Shared Must Read**:
- `specs/truth/features/guest-web/備品補充/後端在送到一半拒絕.feature` -> `功能: 備品補充後端在送到一半拒絕` 中改了前提的三個例子
- `specs/truth/features/guest-web/dsl.md` -> `"{房號}" 房的另一支手機在 "{時刻}" 送出了一張單`
- `truth-delta.md` -> `/dsl-refine` 的 `後端在送到一半拒絕.feature` MODIFY 列
- `specs/truth/contracts/groups/住客與房間/住客掃碼.yaml` -> `createGuestRequest` 每房額度

**Boundary**:
- 同 Phase 4-G01：預期產品碼不用改，不改住客頁。

**Test Scope**:
- `specs/truth/features/guest-web/備品補充/後端在送到一半拒絕.feature`（`phone`）

- [X] T178 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T179 [BDD-REFACTOR] 在綠燈下確認「另一支手機送出」與「另一支手機已在備品補充送出」兩句共用同一個第二工作階段的取得方式

## Phase 5: 回歸

**Goal**: 確認各 phase 之間沒有互相弄紅，`DeskTask` 多一欄、開工務單的路徑參數化、代開單的冪等加鎖沒有弄壞 004、006 與住客頁。四題都用 T005 同一組指令、比對 T005 的基準。

**Boundary**:
- 只跑、只修回歸；不加 feature、不改 truth。紅了回到對應 phase 修，修完重跑整輪。
- 真機 iPad 的觸控手感（44 pt、面板捲動與送出鈕）不在自動化範圍，人工看一次，結果記在 `implement-notes.md`。

- [X] T180 [REGRESSION] 後端全套（不加 `-Dtest`，含舊整合測試與單元測試）；失敗清單必須是 T005 基準的子集，新增的失敗為 0
- [X] T181 [REGRESSION] `bash app/web-bdd/run-feature.sh --keep frontdesk-web/ desktop` 與 `cd app/web && npx vitest run && npm run build`；失敗清單必須是 T005 基準的子集
- [X] T182 [REGRESSION] `bash app/web-bdd/run-feature.sh --keep frontdesk-web/ ipad`；失敗清單必須是 T005 基準的子集
- [X] T183 [REGRESSION] `bash app/web-bdd/run-feature.sh --keep guest-web/ phone`，跑完 `--stop`；失敗清單必須是 T005 基準的子集
