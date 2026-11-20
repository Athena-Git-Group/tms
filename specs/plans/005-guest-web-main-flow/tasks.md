# Tasks: 住客掃碼頁（主流程）

**Plan Package**: `specs/plans/005-guest-web-main-flow`
**Core Inputs**: `spec.md`, `plan.md`, `research.md`, `truth-delta.md`, `specs/truth/techstack.md`, `specs/truth/contracts/**`, `specs/truth/data/**`, `specs/truth/features/backend/**`, `specs/truth/features/guest-web/**`, `ui/**`

## Task Binding Contract

- 每個**開發任務**都必須對應 `truth-delta.md` 中的 ADD / MODIFY / DELETE / NOOP 語意。
- Phase 1 `Setup` 只做本輪新增技術的基礎建設、技術環境與最後的 smoke-test；不寫 DSL 語意、不寫產品行為。
- Phase 2 `Foundational` 只建立後續實作程式、測試共用元件、入口、fixture、helper 與落點骨架。
- Phase 3 `Test Alignment & Implementation` 的目的：在寫產品碼之前，先把本輪所有受影響 DSL 的自動化測試對齊最新版 truth。
- Truth 參照必須使用 `specs/truth/**` 路徑；plan 參照才使用當前 plan package 內相對路徑。
- Feature `Shared Must Read` 除 feature、同模組 DSL、對應 `truth-delta` 列外，還必須列入回追到的 planner 產物。

## 測試指令

- 純計算與型別：`cd app/web && npm run typecheck && npx vitest run`（語系檔鍵集合一致性測試 `src/i18n/locales/locales.test.ts` 包含在內）。
- 單支住客 feature（T003 之後才可用）：`bash app/web-bdd/run-feature.sh guest-web/<模組>/<檔名>.feature`（根是 `guest-web/` 時預設 `phone`）。整個模組：`… guest-web/<模組>/`；整個住客根：`… guest-web/`。`--keep` 沿用已起的後端與開發伺服器。
- Phase 3 的 undefined 檢查（dry-run，不起瀏覽器也不打後端）：`mvn -q -f app/web-bdd/pom.xml test -Dcucumber.features=../../specs/truth/features/guest-web -Dcucumber.execution.dry-run=true -Dweb.profile=phone`，輸出不得有 `undefined`。
- 櫃檯 Web：`bash app/web-bdd/run-feature.sh <frontdesk-web 底下的路徑> desktop`。

## Scope Note（停止範圍、判斷與回交）

1. **專案憲章不存在。** `.agents/constitution/CONSTITUTION.md` 與 `shared.md` 都沒有，照 001–004 的做法略過，沒有額外的上位約束。
2. **沒有回交 `/dsl-refine` 而擋住實作的範圍。** 寫本檔時以腳本把 29 份 feature 的 902 個 step 逐一對到介面根與九個模組 `dsl.md` 的 183 條句型：每一步剛好對到一條，0 步找不到定義、0 條句型沒被用到。Phase 3 的 183 題就是這 183 條，一條一題。第 8、11 條記的兩處字面出入只回交改字，不擋 `/implement`。
3. **本輪只有 `[BDD-RED]`。** `truth-delta.md` 的 `/dsl-refine` 段全部是 ADD，`backend/`、`frontdesk-web/`、`ios-app/` 三個介面根都是 NOOP；`/technical-research` 的兩列 MODIFY 改的是 `techstack.md` 的敘述，不是 DSL 句。所以沒有 `[BDD-ALIGN]`、`[BDD-REMOVE]`，Feature phase 也沒有 `[CODE-REMOVE]`。
4. **只有 `ui/` 是 Feature phase 回追到的 plan-side 產物。** `/api-plan`、`/data-plan` 本輪全部 NOOP，`/technical-research` 的結論寫在 `specs/truth/techstack.md`，三者都在 `specs/truth/**`，以 Test Scope 全綠當閘門。`plan.md` 的八個畫面介面都交 `/ui-plan`，所以 Feature phase 的 `Shared Must Read` 只回追 `ui/ui-plan.md` 該畫面的段落與 `ui/*.html` 雛形，不列 `spec.md`、`research.md`；`ui/` 家族在最後一個消費者（Phase 4-29）之後有一題 gate（Phase 4-G）。`spec.md`、`research.md` 只在 Setup 與 Foundational 的 `Read` 出現。
5. **住客介面以程式現況為準，不是契約。** 契約真相沒有住客手機實際要打的 `/guest/*` 這一組（`truth-delta.md` `/api-plan` 的六列「缺口」）。Feature phase 的「後端介面」一律指向那幾列 NOOP 的摘要；不要照契約的 `servers: /api/v1` 把住客路徑寫成 `/api/v1/guest/…`，也不要照契約 `IdempotencyKey` 的 `required: true` 期待後端會回 400。
6. **Feature phase 的順序照依賴，不照模組。** 每個 `[BDD-GREEN]` 要讓整份 feature 全綠，所以斷言跨頁的檔排在它用到的頁之後。寫本檔時逐份查過每一個導頁的 When 與跨頁的 Then：清單列、進度頁、首頁「我的需求」區先做（4-09～4-12，資料全靠 Given 種好、網址直接開，不需要送出）；送出成功頁（4-16）用到這三者；`我的需求/清單兩段與空狀態` 的「從首頁按全部 3 項」與「另一支手機送出」排在 4-12、4-16 之後；`進入層/憑證失效時靜靜重換` 與 `重試狀態` 都要按首頁的「全部 1 項」，排在 4-12 之後；`取消需求` 的「取消後按返回」排在返回（4-22）之後；即時更新的「另一支手機送出／取消」排在 4-16、4-23 之後；`共用外框` 三份斷言七個子頁與四語全文，排最後。各 phase 能不能平行見 Phase 4 開頭的「Parallel Hint」。
7. **狀態對照表與兩個資料 hook 在 Foundational 就寫完。** `ui/ui-plan.md`「狀態對照表怎麼落到三個畫面」要求這張表只寫一次、三個畫面都從同一組純函式拿結果；它被首頁、我的需求、取消與評分、即時更新四個模組共用，本身沒有畫面。所以 T007 把 `guest/lib/stage.ts` 的表、推導函式與 `Vitest` 一次做完，T013 把 `useGuestHome`、`useGuestRequests` 兩個查詢 hook 做好，Feature phase 只消費。住客的語言比對 `language.ts` 也在 T012 做完，因為外框掛上時就要用到它，留成會丟例外的殼會讓每一頁都壞掉。這些都不讓任何 feature 提前變綠。其他純計算在 T008 只留簽章與測試檔殼，由第一個用到它的 Feature phase 實作：`greeting` 在 4-02、`repair-text` 在 4-06、`time` 在 4-09、`rescan` 在 4-14、`quota` 在 4-18。
8. **後端預設服務項目的鍵與 `dsl.md` 的說法不同。** `specs/truth/features/guest-web/dsl.md`「共通實作約定」寫預設五項的鍵是 `hanger`、`towel`、`toothbrush`、`water`、`hairdryer`；後端出廠值的鍵其實和名稱一樣是中文（`tms/src/main/java/com/athena/tms/service/GuestTenantConfigReader.java:47-51`）。沒有任何句型斷言鍵的值，所以不影響本輪：產品送出時用 `GET /guest/home` 回的 `menu[].key`，stepdef 與 `BackendChannel` 一律用後端回的鍵、不寫死英文鍵。這一句的錯字回交 `/dsl-refine`，不擋 `/implement`。
9. **`# [need clarification]` 共 12 處，照 feature 目前的字實作，不停下來問。** 位置：`進入層/無法使用頁.feature:6`（C-01）、`首頁/首頁內容與問候.feature:27,35,77`（C-07、C-08、C-02）、`備品補充/後端在送到一半拒絕.feature:25,61,85`（C-04 與連不上的字）、`設備報修/組成文字送出.feature:92`（類別一律中文）、`設備報修/報修的限流.feature:36`、`我的需求/清單兩段與空狀態.feature:55`（C-05）、`取消與評分/取消需求.feature:92`、`取消與評分/評分.feature:128`（連不上的提示）。使用者改判時只改那一個 Example 與對應的語系字串。
10. **本版不改後端。** `tms/` 一行不動（FR-054）。Feature phase 發現後端行為與 truth 不合時，停在那個 phase、把現象（請求、回應、`檔案:行號`）記進 `specs/plans/005-guest-web-main-flow/implement-notes.md`，不自己改後端；能用前端達成的照 `ui/ui-plan.md`「驗證與錯誤回饋」處理。
11. **後端的限流在測試裡不能直接用，改成測試層的替身。** 讀過 `tms/src/main/java/com/athena/tms/service/RoomRequestRateLimiter.java:12-52`：它是記憶體裡的計數，鍵是「飯店編號:房間 UUID」，只算住客經 `/guest/requests` 送的單，用系統時鐘不用注入的 `Clock`，`reset()` 在 `tms/src/main` 沒有任何呼叫者。兩個推論：①每個案例開頭 `resetBusinessData()` 會清掉 `rooms`、房間重建時拿到新的 UUID，所以**不會跨案例累積**；②同一個案例裡，種在後端的舊需求、櫃檯代開的單、撥後端時鐘都**不會**讓它回 `429`，而真的經畫面送超過 3 筆就會被它擋、而且 10 分鐘內不會恢復。所以本輪的做法是：
    - **Given 種的舊需求一律不經 `/guest/requests`**，用 004 已在用的 `createTaskInSituation`（`app/bdd-support/src/main/java/com/athena/bddsupport/BackendChannel.java:1485`）直接寫進測試資料庫，帶 `source='guest'` 與這筆訂房的 `reservation_id`，`GET /guest/requests` 就列得到（T015）。`dsl.md` 介面根 `"{房號}" 房的住客這次住宿有以下需求：` 那一列對打字的需求寫「以住客身分送出原文再推進狀況」、`櫃檯在 "{時刻}" 替 "{房號}" 房代開了一張單` 寫「把後端時鐘撥到該時刻再以櫃檯身分開單」，本輪改用直接寫入、`created_at` 直接給；畫面與後端看得到的結果相同，只差手段。兩句的手段回交 `/dsl-refine` 改字，不擋 `/implement`。
    - **`429`、連不上、`5xx` 都用 Playwright 的請求攔截在步驟層做出來**，不靠真的限流器：T014 的 `GuestRequestLimitStub` 在每個住客案例攔下 `POST /guest/requests`，以 `BackendChannel` 讀這間房送出時間在後端現在往前 10 分鐘內的單（不分來源、含已取消，正是 truth 寫的「整間房算」），已滿 3 張就直接回 `429 rate_limited`、不放到後端，否則放行。
    - **每間房每個案例最多 3 筆真的送到後端。** 替身會擋下第 4 筆，所以真的限流器碰不到；只有「後端時鐘被撥過 10 分鐘以後又送」這種情形，替身放行而真的限流器（系統時鐘）可能還記著，替身這時直接讓案例失敗並寫「超過真的限流器每間房 3 筆的預算，這個案例要改用另一間房」。29 份 feature 目前沒有案例會碰到。
    - 另外兩處用系統時鐘的是掃碼失敗次數（`ScanIpRateLimiter`）與換 QR 寫的 `revoked_at`（`truth-delta.md` `/data-plan` 段末）：「多次掃碼失敗」走 `dsl.md` 允許的網路攔截退路，「憑證已經失效」用 T015 的 `revokeGuestSession` 直接標撤銷，都不依賴時鐘。
12. **`research.md` 的四項 spike。** 第 1 項（延遲載入）在 T004 做，不需要資料；第 2、3 項（住客串流、冪等鍵回放）要真的房、訂房與代碼，排在 T016，用 T015 的 helper 備資料；第 4 項（關掉分頁再從歷史記錄打開子頁）就是 `進入層/掃碼就進首頁.feature` Rule 3，由 Phase 4-13 的 Test Scope 證明。
13. **只有 `phone` 一組瀏覽器設定。** 住客的 29 份規格只在 `phone`（WebKit、393 × 852、觸控、`zh-TW`）跑；櫃檯的規格不在 `phone` 跑，`desktop`、`ipad` 兩組的行為不改。真機 iPhone 與 Android 的安全區域、工具列收放與 `dvh` 不在自動化範圍，在 Phase 4-G 人工看一次。
14. **住客的預設資料只在住客的案例準備。** 櫃檯 Web 的 feature 也用 311 房，而且它的 `dsl.md` 有「住客：空房」的說法；`ensureGuestDefaults()` 若每個案例都跑會改掉櫃檯的前提。所以它掛在只對 `specs/truth/features/guest-web/` 底下的案例生效的 `@Before`（T014），櫃檯的案例完全不經過它；Phase 5 整套跑一次櫃檯 Web 確認。
15. **深色模式本版不啟用。** `ui/ui-plan.md`「深色模式」定 v1 只做淺色；設計變數照它留出深色的接點（`[data-theme="dark"]`），但外框不依 `prefers-color-scheme` 切換，頁面宣告 `color-scheme: light`。
16. **起點是 `2510cf0`（分支 `feature/main_guest_web`）。** 工作樹目前只有一個未追蹤的 `dbml-error.log`，不是本輪的東西，不要把它加進任何 commit。本檔引用的行號以 `2510cf0` 為準。T001 動 `app/web/vite.config.ts` 與 `src/router.tsx`，T011 動櫃檯共用的 `src/features/realtime/`，T012 動四份語系檔，T014 動 `app/web-bdd/.../support/Network.java`；這幾處改完都要跑櫃檯 Web 的 `Vitest` 與該題點名的櫃檯 feature 確認沒弄紅。
17. **不為測試加 `data-testid`。** 住客頁的元素一律以使用者看得到的角色與文字找（`specs/truth/features/guest-web/dsl.md`「找畫面元素」）；需要的可及性名稱（「返回」「增加{名稱}」「減少{名稱}」「{n} 顆星」）照 `ui/ui-plan.md`「鍵盤與螢幕報讀」那一段，產品在 Phase 4 照著做。
18. **最後一段是 Phase 5 全套回歸，不用 `[REGRESSION]` 標記。** 範本只有 DELETE 才有 `[CODE-REMOVE] -> [REGRESSION]`；本輪沒有 DELETE，但 004 的先例在最後整套跑一次，本輪照做、用沒有標記的 task。

## Phase 1: Setup

**Goal**: 本輪沒有新套件，但有三樣新的技術環境：住客區的掛載點（`/g/:qrToken/*` 的延遲載入路由與 Vite 對 `/guest` 的代理）、驗收測試線的第三組瀏覽器設定 `phone`、以及 `run-feature.sh` 對第二個 feature 根 `guest-web/` 的支援。先把這三樣備好，用一個小 spike 確認延遲載入在手機設定下來得及，最後用 smoke-test 確認測試線起得來。不寫任何 DSL 語意，不寫任何產品行為。

- [X] T001 `app/web/`：`/g/:qrToken/*` 的延遲載入掛載點與 Vite 代理 `/guest`
  - Read:
    - `specs/truth/techstack.md` -> `前端` 的「住客掃碼頁換頁與載入」「住客掃碼頁連後端」兩列
    - `research.md` -> 決策 1（為什麼是 `/g/`）、決策 2（路由樹與延遲載入）、決策 4（代理）
    - `app/web/src/router.tsx:29-72`（`withoutHotelCode` 與 `/:hotelCode` 的路由樹）、`app/web/src/lib/paths.ts`、`app/web/vite.config.ts:11-26`（`/api` 與 `/staff/stream` 的代理，含 `proxyRes` 的斷線處理）
  - 路由：在 `createBrowserRouter` 的陣列裡、`withoutHotelCode` 之後、`/:hotelCode` 之前，加一條 `{ path: '/g/:qrToken/*', lazy: () => import('./guest/routes') }`（React Router 8 的路由 `lazy`，`app/web/package.json` 的 `react-router` 是 8.5.1）。`app/web/src/guest/routes.tsx` 先只匯出一個顯示「guest」字樣的占位元件；`app/web/src/guest/paths.ts` 定義 `guestPath(qrToken, sub)` 與七個子路徑常數（`''`、`supply`、`repair`、`sent`、`requests`、`requests/:id`、`guide/:key`），照 `ui/ui-plan.md`「畫面與流程」的網址表。櫃檯的路由不改成延遲載入。
  - 代理：`vite.config.ts` 加 `/guest` 代理到 `http://localhost:8080`，`/guest/stream` 沿用 `/staff/stream` 那段「後端斷了就把瀏覽器這一頭也切斷」的 `proxyRes` 設定。
  - 驗證：`npm run typecheck` 綠；`npm run build` 的輸出多一個獨立的住客 chunk、主 chunk 不變大；`npm run dev` 後開 `/g/abc` 看到占位字樣、開 `/athena/login` 仍是登入頁。
  - 不做：不寫換 session、不畫任何畫面、不動 `DeskShell`。

- [X] T002 `app/web-bdd/`：第三組瀏覽器設定 `phone`
  - Read:
    - `specs/truth/techstack.md` -> `測試與驗證` 的「住客掃碼頁驗收的視窗與輸入」
    - `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」的「瀏覽器設定」
    - `app/web-bdd/src/test/java/com/athena/web/bdd/WebProfile.java:15-18,36,66,78-83`、`support/BrowserProfiles.java:24-27,42-56`
  - `WebProfile` 加 `PHONE("phone", 393, 852, true)`；第 66 行的引擎選擇改成 `IPAD` 與 `PHONE` 都用 `playwright.webkit()`；`contextOptions()` 維持 `setHasTouch` 與 `setLocale("zh-TW")`，`PHONE` 另加 `setIsMobile(true)` 與 `setDeviceScaleFactor(3)`。`BrowserProfiles` 加 `PHONE_DEVICE = "手機直向 393 × 852 觸控"`，`ofDevice`／`deviceOf` 各加一個分支，錯誤訊息列出三組。
  - WebKit 沒裝時在 repo 根目錄跑一次 `mvn -q -f app/web-bdd/pom.xml exec:java -Dexec.args="install chromium webkit"`（`run-feature.sh:71` 的同一行）。
  - 驗證：`mvn -q -f app/web-bdd/pom.xml test -Dtest=SmokeTest -Dweb.profile=phone` 用 WebKit 開出 393 寬的分頁。
  - 不做：不改 `desktop`、`ipad` 的任何值；不寫任何住客的步驟。

- [X] T003 `app/web-bdd/run-feature.sh`：feature 路徑可以指定根 `guest-web/`，瀏覽器設定接受 `phone`
  - Read:
    - `specs/truth/techstack.md` -> `測試與驗證` 的「住客掃碼頁 BDD techstack」（runner 不用加的理由）
    - `research.md` -> 決策 8 的「`run-feature.sh` 的改法」
    - `app/web-bdd/run-feature.sh:6-13`（用法說明）、`:55`（`FEATURES_ROOT`）、`:211-224`（參數解析與設定檢查）、`:236-258`（feature 路徑檢查與 `CUCUMBER_FEATURES`）、`:343`、`:376-383`（依設定檢查要裝的瀏覽器）、`:574-582`、`:599`
    - `app/web-bdd/src/test/java/com/athena/web/bdd/RunFrontdeskWebFeaturesTest.java`（`@SelectDirectories` 在 `-Dcucumber.features` 有值時被忽略，runner 不改）
  - 改法：feature 路徑若以 `guest-web` 或 `frontdesk-web` 開頭（含只寫根本身，例如 `guest-web/`），就以它為根，`FEATURES_ROOT` 跟著換成 `specs/truth/features/<根>`；其餘維持相對於 `frontdesk-web/`。瀏覽器設定接受 `phone`；根是 `guest-web` 而沒給設定時預設 `phone`，否則維持預設 `desktop`。第 376 行的瀏覽器檢查改成 `ipad` 與 `phone` 都找 `webkit-*`。用法說明、錯誤訊息、`log` 與 `find` 的清單都改成依根輸出。`CUCUMBER_FEATURES` 的組法與其他步驟（建後端、起 Vite、`--keep`）不動。
  - 驗證（四條都要）：`… guest-web/` 與 `… guest-web/進入層/掃碼就進首頁.feature` 都走到「跑測試」那一步、帶 `-Dweb.profile=phone`；`… 待派單/待派單清單.feature` 與 `… frontdesk-web/待派單/待派單清單.feature ipad` 的行為與改前相同；`… guest-web/不存在.feature` 的錯誤訊息列出的是 `guest-web/` 底下的檔。
  - 不做：不改 runner 類別名、不加第二個 runner、不改 `app/bdd/` 的腳本。

- [X] T004 spike：`phone` 設定下延遲載入的住客路由來得及
  - Read:
    - `research.md` -> 「建立住客區時要先做的 spike」第 1 項與「失敗時的退路」
  - 以 `phone` 設定用 Playwright（`app/web-bdd/` 裡一支不進 runner 的 JUnit，照 `SmokeTest.java` 的寫法與排除方式）開 T001 的 `/g/abc`，量從導航到占位字樣出現的秒數，門檻 5 秒。逾時就把住客路由改成不延遲載入（只影響下載量）。
  - 產出：量到的秒數與結論記在 `specs/plans/005-guest-web-main-flow/implement-notes.md`（沒有就建立）。
  - 不做：不需要任何後端資料；不寫 stepdef。

- [X] T005 smoke-test：住客這條測試線起得來
  - Read:
    - `specs/truth/techstack.md` -> `測試與驗證`
  - 依序確認並停手：①`cd app/web && npm run typecheck && npx vitest run` 綠、`npm run build` 產出含獨立住客 chunk 的靜態檔；②`bash app/web-bdd/run-feature.sh --keep guest-web/進入層/無法使用頁.feature` 能起後端與開發伺服器、以 WebKit 393 × 852 載入 `/g/abc` 看到 T001 的占位字樣（步驟全部 undefined 是預期）；③後端與開發伺服器還在時，`curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d '{"token":"abc"}' http://localhost:5199/guest/scan` 回 `409` 或 `422`（經 Vite 代理打到 8080）；④`bash app/web-bdd/run-feature.sh 待派單/待派單清單.feature desktop` 仍然綠。做完 `bash app/web-bdd/run-feature.sh --stop`。
  - 不跑本輪任何 Feature 的綠燈，不寫 stepdef 語意。

## Phase 2: Foundational

**Goal**: 只建立後續要用的型別、狀態對照表、純計算落點、住客 session 與 API 的薄層、共用的串流連線函式、語系子樹與語言記憶、路由樹與外框骨架與兩個資料 hook、驗收測試線的 World／page object／stepdef 落點與限流替身、`BackendChannel` 的住客 helper，最後用兩項 spike 確認住客串流與冪等鍵。不寫無法使用頁、首頁內容、送出、取消、評分、即時更新這些行為，不寫任何句型的語意。

- [X] T006 `app/web/src/guest/api/types.ts`：先把資料形狀定下來
  - Read:
    - `truth-delta.md` -> `/api-plan` 的 `/guest/scan`、`/guest/home`、`GuestRequest`（八欄與 `closed`）、`/guest/stream` 四列摘要
    - `tms/src/main/java/com/athena/tms/dto/ScanResponse.java`、`GuestHomeView.java`、`GuestMenuItemView.java`、`GuestTaskView.java`（只讀形狀，不改）
    - `ui/ui-plan.md` -> 「用到的後端資料」
  - 只做：`RequestState`（十個值的字面型別，全寫不縮寫）、`Dept`（`housekeeping`、`engineering`、`frontdesk`）、`TaskType`（`amenity`、`repair`、`cleaning`、`other`）、`ScanResponse`（`sessionToken`、`expiresAt`、`roomNo`、`infoPages`、`menu`）、`GuestHome`（`infoPages`、`menu`）、`GuestInfoPage`（`key`、`title`、`body`）、`GuestMenuItem`（`key`、`label`）、`GuestTask`（`id`、`type`、`state`、`dept`、`title`、`answer`、`rating`、`createdAt`）、`CreateRequestBody`（`{ itemId; quantity }` 與 `{ text; lang }` 的 discriminated union，兩者不可同送）、`RatingBody`、`GuestStreamEvent`（`init`、`resync`、`task`、`heartbeat`、`degraded` 的 union）；語言用既有 `app/web/src/i18n/languages.ts` 的型別。欄位名照 DTO，後端可能是 null 的（`label`、`title`、`body`、`answer`、`rating`）標成可空。
  - 不做：不寫任何函式、不寫畫面。
  - 驗證：`npm run typecheck`。

- [X] T007 `app/web/src/guest/lib/stage.ts`：狀態對照表與它推出來的純函式，連 `Vitest`
  - Read:
    - `spec.md` -> 使用者故事 3 的狀態對照表、各步副文表、狀態訊息表、首頁進度卡說明表；「狀態對照與額度公式」
    - `ui/ui-plan.md` -> 「狀態對照表怎麼落到三個畫面」整節（含十個狀態值的大表與「打勾／實心／空心」的定義）
    - `specs/truth/features/guest-web/dsl.md` -> 「需求的狀況對照」
    - `research.md` -> 決策 9
  - 只做：一個 `STAGE_TABLE: Record<RequestState, StageRow>`，`StageRow = { labelKey; tone: 'neutral'|'accepted'|'doing'|'done'; step: 0|1|2|3|null; section: 'active'|'ended'; cancellable: boolean; locked: boolean }`，十列照 spec 的表；再從一張 `GuestTask` 推出的純函式：`stageOf(task)`、`canRate(task)`（`done`／`inspected` 且 `rating` 空且 `answer` 空）、`showsProgress(task)`（不是 `cancelled` 且沒有 `answer`）、`messageBox(task)`（依序：已取消 → 櫃檯回覆 → `triage` → 已完成 → `null`，回 `{ kind, titleKey, body? }`）、`stepView(task)`（四步各自的樣子 `done`／`current`／`todo` 與副文鍵）、`homeCardLine(task)`（首頁進度卡說明表）、`deptLabelKey(dept)`（三種字，空值也是「櫃檯」）、`latestActive(tasks)`（進行中裡送出時間最新的一張）、`splitSections(tasks)`（兩段、各自新到舊）。文字一律回語系鍵與參數，不回中文字面。`stage.test.ts` 對十個狀態值逐一驗表的五欄、`messageBox` 的優先序、`canRate` 的三個條件、`homeCardLine` 四種、`stepView` 八種狀況。
  - 不做：不寫任何元件；不做額度、報修文字、問候、時間（T008）。
  - 驗證：`npx vitest run src/guest/lib/stage.test.ts` 綠。

- [X] T008 其餘純計算的落點與 `Vitest` 檔殼
  - Read:
    - `research.md` -> 決策 9 的清單
    - `ui/ui-plan.md` -> 「前端實作切分建議」的純函式一段
  - 只做：在 `app/web/src/guest/lib/` 留出檔案與函式簽章（回傳先丟 `not implemented`）：`quota.ts`（`remainingQuota(tasks, now)`：3 減去 10 分鐘內的張數，含已取消）、`repair-text.ts`（`composeRepairText(category, note)`、`REPAIR_CATEGORIES`、`NOTE_MAX = 200`）、`greeting.ts`（`greetingKey(now)`：11:00 前、18:00 前、其餘）、`time.ts`（`hhmm(createdAt)`：瀏覽器時區 24 小時制）、`rescan.ts`（401 重換一次的狀態機：`idle` → `rescanning` → `retrying`／`unavailable`／`retry-state`，一輪最多一次）；每個檔旁一支同名 `*.test.ts`，只含 `describe.todo`。
  - 不做：不實作任何一個函式（各由第一個用到它的 Feature phase 實作並補測試，見 Scope Note 第 7 條）；`language.ts` 不在這裡（T012 直接做完）。
  - 驗證：`npm run typecheck` 與 `npx vitest run` 綠（todo 不算失敗）。

- [X] T009 住客 session store：`app/web/src/guest/session.ts`
  - Read:
    - `specs/truth/techstack.md` -> `前端` 的「住客 session」
    - `research.md` -> 決策 3
    - `app/web/src/lib/browser-storage.ts`（`readJson`／`writeJson`／`removeItem`，已把儲存空間被封鎖的例外吞掉）、`app/web/src/lib/persistence.ts:13-16`（櫃檯的鍵 `tms.frontdesk.session`，住客不得共用）、`app/web/src/stores/session.ts`（照它的 `zustand` 寫法，不引用它）
  - 只做：`sessionStorage` 鍵 `tms.guest.session.{代碼}`，值是整份 `ScanResponse`；`readGuestSession(qrToken)`、`writeGuestSession(qrToken, scan)`、`clearGuestSession(qrToken)`；儲存空間讀不到時退回只放記憶體（模組內的 `Map`）；一個 `zustand` store `useGuestSessionStore` 提供目前這枚代碼的 session（`qrToken`、`roomNo`、`menu`、`infoPages`、`sessionToken`）給子頁。
  - 不做：不呼叫後端、不處理 401、不讀櫃檯的 `useSessionStore`。
  - 驗證：`npm run typecheck`；`session.test.ts` 以 `src/test-support/memory-storage.ts` 確認鍵帶代碼、兩枚代碼互不覆蓋、儲存空間丟例外時仍讀得回記憶體裡的值。

- [X] T010 住客 API 薄層：`app/web/src/guest/api/client.ts`
  - Read:
    - `specs/truth/techstack.md` -> `前端` 的「住客掃碼頁連後端」
    - `research.md` -> 決策 4
    - `truth-delta.md` -> `/api-plan` 的 `/guest/scan`、`/guest/home`、`/guest/requests` 與 cancel、`/guest/requests/{id}/rating`、`GuestSession`／`Unauthorized`／`TooManyRequests`、`Error` 的 code 清單六列
    - `app/web/src/api/client.ts:5,9,25,49,66,70-73,83,96,102-114`（`ApiError`、`NetworkError`、`newIdempotencyKey`、`backendUrl`、`send` 寫死的 `/api/v1` 與 `authHeaders()`）
  - 只做：`ApiError`、`NetworkError`、`newIdempotencyKey`、`backendUrl` 與錯誤內容的解析搬到 `app/web/src/api/errors.ts`，`api/client.ts` 從那裡 re-export，櫃檯呼叫處不改；住客自己的 `guestSend(path, { method, body, sessionToken, idempotencyKey })`：前綴 `/guest`、標頭 `X-Guest-Session`、寫入時帶 `Idempotency-Key`、`Accept: application/json`，絕不帶 `Authorization`；六個薄函式 `scan(token)`（公開，不帶 session）、`readHome(session)`、`listRequests(session)`、`createRequest(session, body, key)`、`cancelRequest(session, id, key)`、`rateRequest(session, id, rating, key)`，回 T006 的型別。401 原樣丟成 `ApiError`，另留一個 `setUnauthorizedHandler(fn)` 注入點（Phase 4-14 接上 `rescan.ts`）。
  - 不做：不重換 session、不重試、不寫 UI 的錯誤文字。
  - 驗證：`npm run typecheck`；`client.test.ts` 以假 `fetch` 確認路徑前綴、`X-Guest-Session`、冪等鍵有帶、沒有 `Authorization`。

- [X] T011 把串流連線函式抽成可帶網址與標頭，櫃檯與住客共用
  - Read:
    - `specs/truth/techstack.md` -> `前端` 的「住客掃碼頁即時更新」
    - `research.md` -> 決策 5
    - `app/web/src/features/realtime/stream-client.ts:21,63-65,100-111`（`takeStreamEvents`、`openStaffStream` 寫死的 `/staff/stream` 與 `authHeaders()`）、`live-connection.ts:39`（`transition`）、`use-live-updates.ts:6,16`（唯一的產品呼叫處）、`stream-client.test.ts:3,15,36`（測的是 `openStaffStream`）
  - 只做：`stream-client.ts` 新增 `openStream({ url, headers, onEvent, onClose })`，內容就是現在 `openStaffStream` 的實作、只把網址與標頭換成參數；`use-live-updates.ts` 改呼叫 `openStream({ url: backendUrl('/staff/stream'), headers: authHeaders(), … })`；`stream-client.test.ts` 的 `describe('openStaffStream')` 改成 `describe('openStream')`，原有斷言照搬並多驗一次「帶進來的網址與標頭就是 `fetch` 收到的」；然後刪掉 `openStaffStream`（同一波遷移、同一波刪）。解析器 `takeStreamEvents` 與狀態機 `transition` 不動、不複製。`app/web/src/guest/realtime.ts` 只留 `openGuestStream(sessionToken, handlers)`，呼叫 `openStream` 帶 `/guest/stream` 與 `X-Guest-Session`，不接任何重讀。
  - 不做：不改 `SILENCE_LIMIT_MS`、`REREAD_INTERVAL_MS`；不寫住客收到訊號後做什麼。
  - 驗證：`npx vitest run src/features/realtime` 綠；`bash app/web-bdd/run-feature.sh 即時更新/現場變化即時跟上.feature desktop` 與 `… 即時更新/連線異常提醒列.feature desktop` 綠。

- [X] T012 語系檔的 `guest` 子樹、住客的語言比對與語言記憶
  - Read:
    - `specs/truth/techstack.md` -> `前端` 的「住客掃碼頁多語系」
    - `research.md` -> 決策 6
    - `app/web/src/i18n/index.ts:17`、`interface-language.ts:30-52`（`initialLanguage` 讀櫃檯登入狀態、`followSession` 只在櫃檯身分變動時動作）、`language-storage.ts`、`languages.ts`、`locales/zh-TW.ts:434`、`locales/locales.test.ts`、`i18next.d.ts`、`app/web/src/lib/browser-language.ts`（櫃檯的比對，住客不共用）
  - 只做：四份語系檔各加頂層 `guest` 子樹，先只放各頁共用的「返回」「房號 {{roomNo}}」「即將開放」「重試」「連線失敗，請再試一次」，四語都寫好（其餘鍵由各 Feature phase 加）。`app/web/src/guest/lib/language.ts` 做完：`guestLanguageFor(navigatorLanguages)`（主語言 `zh` 的任何變體 → `zh-TW`、`ja`、`vi`、其餘 `en`）與 `language.test.ts`（`zh-TW`、`zh-CN`、`zh-HK`、`ja`、`ja-JP`、`vi`、`en-US`、`ko-KR` 八種，`ko-KR` 是 `en`）。`app/web/src/guest/language-storage.ts`：`localStorage` 鍵 `tms.guest.language`，`readGuestLanguage()`／`writeGuestLanguage(lang)`。`app/web/src/guest/use-guest-language.ts`：外框掛上時依「住客的記憶 → `guestLanguageFor(navigator.languages)`」決定語言並 `i18next.changeLanguage`、同步 `document.documentElement.lang`；回傳 `setLanguage(lang)` 給語言選單用（寫回鍵再切換）。
  - 不做：不畫語言選單（Phase 4-28）；不改櫃檯的 `initialLanguage`、`followSession`、`browser-language.ts`。
  - 驗證：`npx vitest run src/i18n src/guest` 綠（四份鍵集合一致、八種語言比對）；`npm run typecheck`。

- [X] T013 住客區的路由樹、進入容器、外框、七個頁面容器與兩個資料 hook 的骨架
  - Read:
    - `ui/ui-plan.md` -> 「前端實作切分建議」（頁面／容器切分、共用元件、設計變數的命名、返回到進來的地方）、「設計變數」兩張表、「版面尺寸與伸縮規則」、「深色模式」
    - `ui/guest.css`（設計變數的值）、`ui/guest.js`（只看各頁的區塊骨架，不搬示範資料）
    - `specs/truth/techstack.md` -> `前端` 的「住客掃碼頁外框與版面」「住客掃碼頁後端資料」
    - `app/web/src/index.css`（櫃檯的 `@theme`，住客另立命名空間、不覆蓋）、`app/web/src/api/query-client.ts`、T001 的 `guest/routes.tsx` 與 `guest/paths.ts`
  - 只做：`guest/routes.tsx` 匯出路由樹：`GuestEntry`（`/g/:qrToken` 這一層；只做「store 有這枚代碼的 session 就往下、沒有就顯示載入中並呼叫 `scan`、成功寫進 store」；無法使用頁、重試狀態、401 重換分別留給 4-01、4-15、4-14）→ `GuestShell`（掛 `useGuestLanguage`；單欄、最寬 480 置中、`dvh` 高度、安全區域邊距、`color-scheme: light`；提示浮層與對話框的容器）→ 七個頁面容器 `guest/pages/{HomePage,SupplyPage,RepairPage,SentPage,RequestsPage,RequestPage,GuidePage}.tsx` 各接一條路徑、先只畫子頁標題列與空內容。資料 hook `guest/queries.ts`：`useGuestHome(qrToken)`（`queryKey ['guest', qrToken, 'home']`，換 session 回應的 `infoPages`／`menu` 當初值）與 `useGuestRequests(qrToken)`（`['guest', qrToken, 'requests']`），都經 T010 的薄層、與櫃檯共用同一個 `queryClient`。共用元件的檔案殼在 `guest/components/`：`TitleBar`、`StatusTag`、`RequestIcon`、`ProgressCard`、`RequestRow`、`MessageBox`、`FourSteps`、`RatingCard`、`CancelDialog`、`Toast`、`InlineNotice`、`ComingSoonTag`、`BottomBar`、`CenteredNotice`、`LanguageMenu`。Tailwind：住客的設計變數以 `--color-guest-*`、`--radius-guest-*`、`--shadow-guest-*`、`--text-guest-*`、`--font-guest` 放進 `@theme`，名稱後半與值照 `ui/ui-plan.md`「設計變數」與 `ui/guest.css`；深色那組掛 `[data-theme="dark"]`，不啟用。
  - 不做：畫面可以是空白；不寫首頁內容、清單、進度頁、送出、取消、評分的任何規則與文案；不寫即時更新。
  - 驗證：`npm run typecheck`；`npm run build` 住客 chunk 仍獨立；T016 會實際開 `/g/{真的代碼}` 走到首頁容器。

- [X] T014 `app/web-bdd/` 住客的 World 延伸、網址、限流替身、page object 與 stepdef 落點
  - Read:
    - `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」全部十條與「需求的狀況對照」
    - 本檔 Scope Note 第 11、14 條
    - `app/web-bdd/src/test/java/com/athena/web/bdd/support/WebWorld.java:68-72`（`prepareScenario`，`@Before(order = 0)`）、`WebApp.java`、`Network.java:40-77,127-342`（`Api` 常數、`respondWithError`／`disconnect`／`hold`／`handle`、`pauseLiveUpdates`、`cutLiveStream`、`sendLiveSignal`、`holdLiveSignals`、`liveEventsFromBackend`；`LIVE_STREAM_CUT_SCRIPT` 目前只認 `/staff/stream`）、`Clocks.java:33-116`、`WhenStart.java:94`、`pages/PageObject.java`、`steps/common/CommonGivenSteps.java`（照它的分層）
  - 只做：
    - `support/GuestScenario.java`：這個案例的房號 → 代碼、每個分頁手上的 session、標題 → 需求 id、When 送出的時刻、點按與輸入計數、第二支手機的 context。
    - `support/GuestApp.java`：`/g/{代碼}` 與七個子路徑的網址。
    - `support/GuestHooks.java`：`@Before(order = 1)`，只有 `scenario.getUri()` 在 `specs/truth/features/guest-web/` 底下才做事：呼叫 `BackendChannel.ensureGuestDefaults()`（T015）、裝上限流替身、把 `WebWorld` 換成 `phone` 設定。櫃檯的案例不經過它。
    - `support/GuestRequestLimitStub.java`：每個住客案例對所有分頁 `route` 住 `POST /guest/requests`：以 `BackendChannel` 讀這間房 `created_at` 在後端現在往前 10 分鐘內的 `tasks` 張數（不分來源、含 `cancelled`），已滿 3 張就回 `429 {"code":"rate_limited"}` 不放行；否則放行，並記下這個案例真的放到後端的張數與每一次請求帶的 `Idempotency-Key`。放行第 4 張時直接讓案例失敗（Scope Note 第 11 條的訊息）。提供 `sentKeys()` 給「後端這次住宿多了以下需求」的 Then 查冪等鍵。
    - `Network.java`：加 `GUEST_SCAN`、`GUEST_HOME`、`GUEST_REQUESTS`、`GUEST_CREATE`、`GUEST_CANCEL`、`GUEST_RATE`、`GUEST_STREAM` 七個 `Api` 常數；`LIVE_STREAM_CUT_SCRIPT`、`cutLiveStream`、`sendLiveSignal`、`holdLiveSignals`、`liveEventsFromBackend` 同時認得 `/staff/stream` 與 `/guest/stream`（櫃檯既有行為不變）。
    - `pages/guest/`：`GuestEntryPage`（載入中、無法使用、重試）、`GuestHomePage`、`SupplyPage`、`RepairPage`、`SentPage`、`RequestsPage`、`RequestPage`、`GuidePage`、`GuestFrame`（標題列、提示浮層、對話框、語言膠囊與選單）的空殼，元素一律用角色與文字找。
    - `steps/guest/{common,entry,home,supply,repair,requests,cancelrate,realtime,guide,shell}/`：`package-info.java` 與 Phase 3「Parallel Hint」點名的步驟檔空殼（`common` 的類別叫 `GuestCommonGivenSteps`／`GuestCommonWhenSteps`／`GuestCommonThenSteps`，`requests` 與 `guide` 沒有 When）。
  - 不做：不寫任何句型的語意（Phase 3）；不用版面座標找元素；不改櫃檯既有 page object 與 stepdef 的行為；不改 `WebWorld.prepareScenario`。
  - 驗證：`mvn -q -f app/web-bdd/pom.xml test-compile` 過；`bash app/web-bdd/run-feature.sh 即時更新/連線異常提醒列.feature desktop` 仍綠（`Network.java` 的改動沒弄壞櫃檯）。

- [X] T015 `app/bdd-support/` 的 `BackendChannel`：住客的預設資料、種需求與四個 helper
  - Read:
    - `specs/truth/techstack.md` -> `測試與驗證` 的「BDD 後端資料準備補的 helper」
    - `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」的飯店與房間、服務項目與資訊頁、分類規則；「需求的狀況對照」
    - `truth-delta.md` -> `/api-plan` 的 `房間QR憑證` 列（程式是 `/admin/rooms/{roomNo}/qr` 與 `…/rotate`，只放行主管、不在 `/api/v1` 下）、`/guest/requests/{id}/rating` 列；`/data-plan` 的 `room_qr_tokens`、`guest_sessions`、`task_events` 三列
    - `app/bdd-support/src/main/java/com/athena/bddsupport/BackendChannel.java:318`（`ensureFrontdeskDefaults`，已設 `guest_info_pages`）、`:394`（`resetBusinessData` 清掉 `tenants` 以外的全部表）、`:565`（`ensureRoom`）、`:798`（`setTenantConfig`）、`:1013`（`insertTodayReservation`）、`:1485` 與 `:1660-1730`（`createTaskInSituation` 與它寫入的欄位：`source`、`reservation_id`、`created_at`、`answer`、取消原因）、`:2272`（`createReservation`）、`:2358-2387`（私有的 `guestSessionToken`）、`:2469`（`jwt`）、`:2666`（`request()` 會自動加 `/api/v1`）；`TaskSituation.java`、`TaskSituationSpec.java`；`tms/src/main/java/com/athena/tms/service/RoomQrService.java:37-40`（讀列印資料時沒有代碼會自動發一枚）、`GuestTenantConfigReader.java:12-15,47-51`
  - 只做：
    - `ensureGuestDefaults()`：在 `ensureFrontdeskDefaults()` 之上確保 311 房有今天入住、後天退房的訂房，308 房存在但沒有涵蓋現在的訂房；`guest_info_pages` 設成 `dsl.md` 寫的三頁（鍵、標題、內文照字面）；`request_menu` 與 `classification_rules` 不設（重設後本來就沒有）。
    - `roomQrToken(roomNo)`：以 `M0001` 的 JWT 呼叫 `GET /admin/rooms/{roomNo}/qr`（繞過 `request()` 的 `/api/v1` 前綴），回 `token`。`rotateRoomQr(roomNo)`：`POST …/qr/rotate`，回舊代碼與新代碼。
    - `guestScan(token)`：`POST /guest/scan` 換一個新的住客 session（給「另一支手機先評分」這類需要第二個 session 的 Given）；`guestRateRequest(sessionToken, taskId, rating)`：`POST /guest/requests/{id}/rating`；`revokeGuestSession(sessionToken)`：直接把 `guest_sessions.revoked_at` 寫成現在（Scope Note 第 11 條）。
    - `seedGuestRequest(roomNo, GuestSeed)`：Scope Note 第 11 條的種需求入口，不經 `/guest/requests`。用 `createTaskInSituation`，`TaskSituationSpec.of(roomNo, type, "guest")` 帶 `reservationId`（這間房目前那筆訂房）、`title`、`createdAt`、`acceptedAt`／`startedAt`、`assignee`、`answer`（已回答）、`reason`（`guest_withdrew` 或 `manager_void`）；打字的需求另帶 `guestText(原文, "zh-TW", null)`、`dept = frontdesk`。`GuestSeed` 是 `dsl.md` 介面根「需求的狀況對照」十一種狀況到 `TaskSituation` 的對照：待派單 `TRIAGE`、房務／工務待認領 `POOL`、房務／工務已接 `ACCEPTED_BY_CLAIM`、延後中 `DEFERRED_BY_MANAGER`、進行中 `DOING`、中斷 `INTERRUPTED_BY_LEAVE`、已完成 `DONE`、查房通過 `INSPECTION_PASSED`、已結案 `CLOSED_VERIFIED`、已取消兩種 `CANCELLED_BY_GUEST`／`CANCELLED_BY_MANAGER`、已回答 `DONE` 加 `answer`。另加 `setTaskRating(taskId, rating)`：直接寫 `tasks.rating` 並補一筆 `rated` 事件。
    - `seedDeskTask(roomNo, createdAt)`：櫃檯替這間房代開、不綁訂房的一張備品單（`source = 'desk'`、`reservation_id` 空、房務待認領），`created_at` 直接給。
    - `setClassificationRule(keyword, dept, type)`：包 `setTenantConfig(tenantId, "classification_rules", …)`，形狀照 `GuestTenantConfigReader.java` 讀的 `keywords`、`dept`、`type`。
    - 鍵一律用後端回的，不寫死（Scope Note 第 8 條）。
  - 不做：不寫任何 Cucumber 步驟、不解析 Data Table（Phase 3）；不改員工 App 測試線既有方法的簽章與行為；不改後端。
  - 驗證：`mvn -q -f app/bdd-support/pom.xml install` 過；實跑留給 T016。

- [X] T016 spike：住客串流與冪等鍵回放，順便驗 T015 的 helper
  - Read:
    - `research.md` -> 「建立住客區時要先做的 spike」第 2、3 項與「失敗時的退路」
    - `truth-delta.md` -> `/api-plan` 的 `/guest/stream`、`IdempotencyKey` 兩列（程式現況與 `檔案:行號`）
    - T015 的 helper、T004 的 JUnit 寫法
  - 只做：`app/web-bdd/` 裡一支不進 runner 的 JUnit `GuestBackendSpikeTest`（照 `SmokeTest.java` 的排除方式），對 8080 實跑：①`resetBusinessData()` → `ensureGuestDefaults()` → `roomQrToken("311")` → `guestScan` 拿到 session；`rotateRoomQr("311")` 之後舊代碼 `guestScan` 回 `409 room_vacant`、新代碼回 `200`；`seedGuestRequest` 種一張房務待認領，`GET /guest/requests` 列得到、`source` 是 `guest`。②以 Java `HttpClient` 讀 `GET /guest/stream` 帶 `X-Guest-Session`，確認依序收到 `init`、`resync`（`refetch` 是 `/guest/home`）；以阿美的身分用 `postTaskAction` 認領那一張，確認收到 `task`，記下從動作到收到的毫秒數；心跳等 16 秒收到一則、沒有 `degraded` 欄位。③同一把 `Idempotency-Key` 連送兩次 `POST /guest/requests`，第二次是回放、清單只多一張。④用 Playwright `phone` 開 `/g/{新代碼}`，走到 T013 的首頁容器。
  - 產出：四項結果與量到的數字記在 `implement-notes.md`。第 ③ 項不回放時記下退路（送出鈕按下後鎖住、並以清單核對張數），4-16 照退路做。
  - 不做：不寫產品的串流客戶端、不寫 stepdef、不改後端。

## Phase 3: Test Alignment & Implementation

**Goal**: 把本輪 Feature 用到的 DSL 自動化測試對齊最新版 truth；含 truth-delta 有改的句，以及本輪 Feature 用到、尚無 stepdef 的句。不寫產品行為。住客掃碼頁是全新的介面根，183 句全部是 `[BDD-RED]`；本輪沒有 `[BDD-ALIGN]`、`[BDD-REMOVE]`（Scope Note 第 3 條）。

**DSL 參照**:
- 每一句只屬於一個權威 `dsl.md`：同模組 `specs/truth/features/{介面}/{模組}/dsl.md`，或介面根 `specs/truth/features/{介面}/dsl.md`。不得掃其他模組。
- 本輪的句分十處：介面根 `specs/truth/features/guest-web/dsl.md`（T017～T054，38 句）、`specs/truth/features/guest-web/進入層/dsl.md`（T055～T077，23 句）、`specs/truth/features/guest-web/首頁/dsl.md`（T078～T088，11 句）、`specs/truth/features/guest-web/備品補充/dsl.md`（T089～T107，19 句）、`specs/truth/features/guest-web/設備報修/dsl.md`（T108～T122，15 句）、`specs/truth/features/guest-web/我的需求/dsl.md`（T123～T131，9 句）、`specs/truth/features/guest-web/取消與評分/dsl.md`（T132～T147，16 句）、`specs/truth/features/guest-web/即時更新/dsl.md`（T148～T172，25 句）、`specs/truth/features/guest-web/住宿指南/dsl.md`（T173～T182，10 句）、`specs/truth/features/guest-web/共用外框/dsl.md`（T183～T199，17 句）。
- 本輪沒有 DSL row 只搬移權威位置，也沒有改名的句。
- 讀法：用 task title 的句型對到該檔那一列（title 開頭的 `Given:`／`When:`／`Then:` 是它所在的章節，不是句型的一部分），以最右欄「住客掃碼頁實作語意（Playwright）」當作測試程式碼語意：Given／When 讀 `使用 fixture`、`Arrange 來源`、`頁面操作`、`等待條件`、`回寫資料`；Then 讀 `必查`。同一列的 `Gherkin 參數`、`Data Table 參數`、`預設參數` 三欄一併照做。
- 每一句都另外受 `specs/truth/features/guest-web/dsl.md` 開頭「共通實作約定」十條約束，凡寫「後端」的 Then 畫面與後端都要成立。
- `truth-delta.md` 只告訴這句是 ADD。語意以 `dsl.md` 那一列為準，不得用 feature 措辭、雛形或自行發明；唯一例外是下面「測試策略」的兩處手段替換。

**測試策略**（Scope Note 第 11 條的落地，每一題都適用）:
- **種資料不經畫面也不經住客介面。** Given 要的舊需求、櫃檯代開的單、星等，一律用 T015 的 `seedGuestRequest`、`seedDeskTask`、`setTaskRating` 寫進後端，不呼叫 `POST /guest/requests`，所以不吃真的限流器的額度。`dsl.md` 介面根第 2 列（`這次住宿有以下需求：`）與第 13 列（`櫃檯在 … 代開了一張單`）的手段照這裡做，結果照 `dsl.md`。
- **只有 When 與「剛在備品補充送出」「另一支手機送出」這類句子真的經畫面送出**，每間房每個案例最多 3 筆，T014 的 `GuestRequestLimitStub` 把關。
- **`429`、連不上、`5xx` 都在步驟層用 Playwright 攔截做出來**：限流靠 `GuestRequestLimitStub` 依整間房 10 分鐘內的張數回 `429`；連不上用 `Network.disconnect`；後端出錯用 `Network.respondWithError`；掃碼被限流用攔截讓 `POST /guest/scan` 回 `429`。不靠真的限流器、不等 10 分鐘。
- **時間**：「幾秒內」的 Then 用真實時間輪詢；「畫面沒有每 4 秒定時重讀」「超過 N 秒沒有訊號」「現在時間是」用 Playwright 時鐘與後端 `Clock`（`Clocks.java`），兩種不要混用。

**Markers**:
- `[BDD-ALIGN]`：`MODIFY`。本輪沒有。
- `[BDD-REMOVE]`：`DELETE`。本輪沒有。
- `[BDD-RED]`：`ADD`，或本輪 Feature 用到、尚無 stepdef 的句。依 `dsl.md` 該列寫出 stepdef。完成時這句可被跑到，失敗只能是 assertion 或產品行為，不能是 undefined step。
- 三個 marker 都只動測試層，不寫產品碼。

**Shared Must Read**:
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」、`頁`／`底部`／`步` 參數的說法，與本題句型
- `specs/truth/features/guest-web/{進入層、首頁、備品補充、設備報修、我的需求、取消與評分、即時更新、住宿指南、共用外框}/dsl.md` -> 模組說明（開頭那一段寫的是這個模組靠哪些後端介面與規則）與本題句型
- `truth-delta.md` -> `/dsl-refine` 住客掃碼頁那一節；`/api-plan` 的六列「缺口」（住客介面的程式現況）；`/data-plan` 段末的備註（系統時鐘的兩處）
- 測試落點：T014 的 `app/web-bdd/src/test/java/com/athena/web/bdd/steps/guest/`、`pages/guest/`、`support/GuestScenario.java`、`GuestApp.java`、`GuestHooks.java`、`GuestRequestLimitStub.java`、`Network.java`、`Clocks.java`、`BrowserProfiles.java`；T015 的 `app/bdd-support/` `BackendChannel`
- 櫃檯 Web 的既有 stepdef 只當寫法參考（`steps/realtime/LiveConnection.java:170` 的 `waitFor`、`steps/common/`），不呼叫櫃檯的 page object

**Boundary**:
- 一條 DSL 一個 task。
- 只改該句的 stepdef / assertion / 直接依賴的 helper 與 page object。
- 不寫產品碼。產品補上之前實跑會紅，這是預期；完成條件看 dry-run 沒有 undefined、斷言表達 `dsl.md` 的語意。
- 找元素一律用使用者看得到的角色與文字，定位方式收在 page object 內；page object 需要的可及性名稱照 feature 的畫面文字與 `ui/ui-plan.md`「鍵盤與螢幕報讀」，產品在 Phase 4 照著做。不為了測試要求產品加 `data-testid`。
- 共用檔（`GuestCommon*Steps.java`、`GuestScenario.java`、`Network.java`、`BackendChannel`、各 page object）改動前先重新讀檔、只做最小追加、寫入後立即編譯（`mvn -q -f app/web-bdd/pom.xml test-compile`）；不要重排或重寫別人的段落。
- review 啟動 subagent；本輪所有 Test Scope 不得再有 undefined step（以「測試指令」的 dry-run 跑一次），失敗只能是 assertion 或產品行為。review 另查兩件事：沒有任何 Given 經 `POST /guest/requests` 種資料；沒有任何 stepdef 等真的 10 分鐘或靠真的限流器。有 issues 就修正再 review，直到沒有任何問題。通過前不解鎖 Phase 4。

**Parallel Hint**（依檔案衝突分組；組與組平行，組內依序；每組各派一個獨立 subagent，T200 等全部回來再啟動 subagent 來 review）：
- G1 `steps/guest/common/GuestCommonGivenSteps.java`：T017～T029（13 句），其中 T018 `"{房號}" 房的住客這次住宿有以下需求：` 最大（Data Table 八欄、十一種狀況），先做。
- G2 `steps/guest/common/GuestCommonWhenSteps.java`：T030～T034（5 句）。
- G3 `steps/guest/common/GuestCommonThenSteps.java`：T035～T054（20 句）。
- G4 `steps/guest/entry/`：`EntryGivenSteps.java` T055～T066；`EntryWhenSteps.java` T067～T070；`EntryThenSteps.java` T071～T077。
- G5 `steps/guest/home/`：`HomeGivenSteps.java` T078；`HomeWhenSteps.java` T079～T080；`HomeThenSteps.java` T081～T088。
- G6 `steps/guest/supply/`：`SupplyGivenSteps.java` T089～T097；`SupplyWhenSteps.java` T098～T100；`SupplyThenSteps.java` T101～T107。
- G7 `steps/guest/repair/`：`RepairGivenSteps.java` T108～T114；`RepairWhenSteps.java` T115～T117；`RepairThenSteps.java` T118～T122。
- G8 `steps/guest/requests/`：`RequestsGivenSteps.java` T123～T124；`RequestsThenSteps.java` T125～T131。
- G9 `steps/guest/cancelrate/`：`CancelRateGivenSteps.java` T132～T137；`CancelRateWhenSteps.java` T138；`CancelRateThenSteps.java` T139～T147。
- G10 `steps/guest/realtime/`：`RealtimeGivenSteps.java` T148～T151；`RealtimeWhenSteps.java` T152～T158；`RealtimeThenSteps.java` T159～T172。
- G11 `steps/guest/guide/`：`GuideGivenSteps.java` T173～T175；`GuideThenSteps.java` T176～T182。
- G12 `steps/guest/shell/`：`ShellGivenSteps.java` T183～T185；`ShellWhenSteps.java` T186～T187；`ShellThenSteps.java` T188～T199。
- G4～G12 都會用到 G1～G3 的 helper（開網址、找需求 id、等畫面畫完），所以 G1～G3 先起，其餘等 G1 的 T018 與 T021 回來再起。

### 介面根 `specs/truth/features/guest-web/dsl.md`

- [X] T017 [P] [BDD-RED] `Given: 現在時間是 "{時刻}"`

- [X] T018 [P] [BDD-RED] `Given: "{房號}" 房的住客這次住宿有以下需求：`

- [X] T019 [P] [BDD-RED] `Given: "{房號}" 房今天沒有人住`

- [X] T020 [P] [BDD-RED] `Given: "{房號}" 房的住客這次住宿還沒送過任何需求`

- [X] T021 [P] [BDD-RED] `Given: "{房號}" 房的住客已打開首頁`

- [X] T022 [P] [BDD-RED] `Given: "{房號}" 房的住客停在 "{頁}"`

- [X] T023 [P] [BDD-RED] `Given: "{房號}" 房的住客開著 "{標題}" 的進度頁`

- [X] T024 [P] [BDD-RED] `Given: "{房號}" 房的住客開著 "{標題}" 的資訊頁`

- [X] T025 [P] [BDD-RED] `Given: "{房號}" 房的住客在備品補充頁，已把以下項目調到：`

- [X] T026 [P] [BDD-RED] `Given: "{房號}" 房的另一支手機也打開了首頁`

- [X] T027 [P] [BDD-RED] `Given: 這支手機的介面語言是 "{語言}"`

- [X] T028 [P] [BDD-RED] `Given: 這支手機暫時連不上後端`

- [X] T029 [P] [BDD-RED] `Given: 櫃檯在 "{時刻}" 替 "{房號}" 房代開了一張單`

- [X] T030 [P] [BDD-RED] `When: 住客打開 "{房號}" 房 QR Code 的網址`

- [X] T031 [P] [BDD-RED] `When: 住客按 "{按鈕}"`

- [X] T032 [P] [BDD-RED] `When: 住客打開 "{頁}"`

- [X] T033 [P] [BDD-RED] `When: 住客打開 "{標題}" 的進度頁`

- [X] T034 [P] [BDD-RED] `When: 住客重新整理網頁`

- [X] T035 [P] [BDD-RED] `Then: 畫面上看得到以下文字：`

- [X] T036 [P] [BDD-RED] `Then: 畫面上沒有以下文字：`

- [X] T037 [P] [BDD-RED] `Then: 畫面上看得到 "{文字}"`

- [X] T038 [P] [BDD-RED] `Then: 畫面上沒有 "{文字}"`

- [X] T039 [P] [BDD-RED] `Then: 按鈕 "{按鈕}" 可以按`

- [X] T040 [P] [BDD-RED] `Then: 按鈕 "{按鈕}" 不能按`

- [X] T041 [P] [BDD-RED] `Then: 畫面是 "{頁}"`

- [X] T042 [P] [BDD-RED] `Then: 畫面是 "{標題}" 的進度頁`

- [X] T043 [P] [BDD-RED] `Then: 頁面頂端的標題列是返回鈕、"{頁標題}" 與 "房號 {房號}"`

- [X] T044 [P] [BDD-RED] `Then: "{標題}" 的狀態標籤是 "{標籤}"`

- [X] T045 [P] [BDD-RED] `Then: 清單 "{段落}" 由上到下是 "{標題清單}"`

- [X] T046 [P] [BDD-RED] `Then: 狀態訊息框是 "{標題}"，內文 "{內文}"`

- [X] T047 [P] [BDD-RED] `Then: 四步進度走到 "{步}"`

- [X] T048 [P] [BDD-RED] `Then: 四步進度如下：`

- [X] T049 [P] [BDD-RED] `Then: 進度頁底部是 "{底部}"`

- [X] T050 [P] [BDD-RED] `Then: 進度頁沒有評分卡`

- [X] T051 [P] [BDD-RED] `Then: 首頁的進度卡是 "{標題}"`

- [X] T052 [P] [BDD-RED] `Then: 後端這次住宿多了以下需求：`

- [X] T053 [P] [BDD-RED] `Then: 後端這次住宿沒有多任何一張需求`

- [X] T054 [P] [BDD-RED] `Then: 畫面出現提示框 "{標題}"，內文 "{內文}"`

### `specs/truth/features/guest-web/進入層/dsl.md`

- [X] T055 [P] [BDD-RED] `Given: 主管在後台換掉了 "{房號}" 房的 QR Code`

- [X] T056 [P] [BDD-RED] `Given: 這支手機上的住客憑證已經不見`

- [X] T057 [P] [BDD-RED] `Given: 這支手機上的住客憑證已經失效`

- [X] T058 [P] [BDD-RED] `Given: "{房號}" 房這筆訂房已經過了退房時刻`

- [X] T059 [P] [BDD-RED] `Given: 這支手機短時間內多次掃碼失敗，後端暫時不受理它的掃碼`

- [X] T060 [P] [BDD-RED] `Given: 後端暫時出錯，任何掃碼都失敗`

- [X] T061 [P] [BDD-RED] `Given: "{房號}" 房的住客的畫面停在重試狀態`

- [X] T062 [P] [BDD-RED] `Given: 這支手機已恢復連線`

- [X] T063 [P] [BDD-RED] `Given: 後端已恢復`

- [X] T064 [P] [BDD-RED] `Given: "{房號}" 房的住客打開清單時因為後端出錯停在重試狀態`

- [X] T065 [P] [BDD-RED] `Given: "{員工}" 已在這支手機上登入過櫃檯 Web，登入狀態還在`

- [X] T066 [P] [BDD-RED] `Given: "{員工}" 已在這支手機上打開過 "{房號}" 房的住客頁`

- [X] T067 [P] [BDD-RED] `When: 住客打開 "{房號}" 房舊代碼的網址`

- [X] T068 [P] [BDD-RED] `When: 住客打開一個不存在的代碼的網址`

- [X] T069 [P] [BDD-RED] `When: 住客把網頁關掉，再從歷史記錄打開同一個網址`

- [X] T070 [P] [BDD-RED] `When: 住客在同一支手機上打開櫃檯 Web 的網址`

- [X] T071 [P] [BDD-RED] `Then: 從打開網址到看到首頁，住客沒有輸入或按過任何東西`

- [X] T072 [P] [BDD-RED] `Then: 過程中沒有要住客再掃一次 QR Code，也沒有出現錯誤訊息`

- [X] T073 [P] [BDD-RED] `Then: 無法使用頁的文字與空房那一頁完全相同`

- [X] T074 [P] [BDD-RED] `Then: 畫面上沒有任何可以點的東西`

- [X] T075 [P] [BDD-RED] `Then: 住客頁向後端的請求沒有帶員工的登入憑證`

- [X] T076 [P] [BDD-RED] `Then: 櫃檯 Web 仍是 "{員工}" 的登入狀態，畫面是總覽看板`

- [X] T077 [P] [BDD-RED] `Then: 櫃檯 Web 顯示登入頁`

### `specs/truth/features/guest-web/首頁/dsl.md`

- [X] T078 [P] [BDD-RED] `Given: "{房號}" 房的訂房住客是 "{姓名}"，今天入住、後天退房`

- [X] T079 [P] [BDD-RED] `When: 住客點房號膠囊`

- [X] T080 [P] [BDD-RED] `When: 住客點即將開放的入口 "{入口}"`

- [X] T081 [P] [BDD-RED] `Then: 首頁由上到下依序是以下區塊：`

- [X] T082 [P] [BDD-RED] `Then: 頂列右上是房號膠囊 "{房號}" 與語言膠囊 "{短寫法}"`

- [X] T083 [P] [BDD-RED] `Then: 頂列左上是空白的`

- [X] T084 [P] [BDD-RED] `Then: 問候是 "{第一行}" 與 "{第二行}"`

- [X] T085 [P] [BDD-RED] `Then: 畫面沒有任何變化`

- [X] T086 [P] [BDD-RED] `Then: 首頁的進度卡的進度條亮到 "{步}"，說明是 "{說明}"`

- [X] T087 [P] [BDD-RED] `Then: 首頁沒有進度卡`

- [X] T088 [P] [BDD-RED] `Then: 首頁上「即將開放」的標籤有 {數量} 個`

### `specs/truth/features/guest-web/備品補充/dsl.md`

- [X] T089 [P] [BDD-RED] `Given: 飯店沒有另外設定服務項目`

- [X] T090 [P] [BDD-RED] `Given: 飯店設定了以下服務項目：`

- [X] T091 [P] [BDD-RED] `Given: "{房號}" 房的住客在備品補充頁，所有數量是 0`

- [X] T092 [P] [BDD-RED] `Given: "{房號}" 房的住客剛在備品補充送出以下項目，停在送出成功頁：`

- [X] T093 [P] [BDD-RED] `Given: "{房號}" 房的住客剛在備品補充送出以下項目，已回到首頁：`

- [X] T094 [P] [BDD-RED] `Given: "{房號}" 房的住客在備品補充頁，剛被額度估算擋下，提示框顯示中`

- [X] T095 [P] [BDD-RED] `Given: 這支手機會在第一張需求建立之後斷線`

- [X] T096 [P] [BDD-RED] `Given: 後端這時回覆很慢`

- [X] T097 [P] [BDD-RED] `Given: "{房號}" 房的住客剛才送到一半斷線，"{已建立}" 已建立、"{沒建立}" 沒有建立，手機已恢復連線`

- [X] T098 [P] [BDD-RED] `When: 住客按 "{項目}" 的加號 {次數} 次`

- [X] T099 [P] [BDD-RED] `When: 住客按 "{項目}" 的減號 {次數} 次`

- [X] T100 [P] [BDD-RED] `When: 住客連按兩下 "{按鈕}"`

- [X] T101 [P] [BDD-RED] `Then: 備品清單由上到下是 "{項目清單}"`

- [X] T102 [P] [BDD-RED] `Then: 每一列的提示都是 "最多 5"、數量都是 0`

- [X] T103 [P] [BDD-RED] `Then: "{項目}" 的數量是 {數量}`

- [X] T104 [P] [BDD-RED] `Then: "{項目}" 的加號不能按`

- [X] T105 [P] [BDD-RED] `Then: "{項目}" 的減號不能按`

- [X] T106 [P] [BDD-RED] `Then: "{項目}" 的減號可以按`

- [X] T107 [P] [BDD-RED] `Then: 後端送出的順序是 "{標題清單}"`

### `specs/truth/features/guest-web/設備報修/dsl.md`

- [X] T108 [P] [BDD-RED] `Given: 這家飯店的分類規則沒有對 "{關鍵字}" 的規則`

- [X] T109 [P] [BDD-RED] `Given: 這家飯店的分類規則把含 "{關鍵字}" 的文字分給工務`

- [X] T110 [P] [BDD-RED] `Given: "{房號}" 房的住客在設備報修頁`

- [X] T111 [P] [BDD-RED] `Given: "{房號}" 房的住客在設備報修頁，已選類別 "{類別}"`

- [X] T112 [P] [BDD-RED] `Given: "{房號}" 房的住客在設備報修頁，已選類別 "{類別}"，補充說明已輸入 "{補充說明}"`

- [X] T113 [P] [BDD-RED] `Given: "{房號}" 房的住客在設備報修頁，已選類別 "{類別}"，補充說明已有 200 個字`

- [X] T114 [P] [BDD-RED] `Given: "{房號}" 房的住客剛送出報修 "{標題}"，停在送出成功頁`

- [X] T115 [P] [BDD-RED] `When: 住客點類別 "{類別}"`

- [X] T116 [P] [BDD-RED] `When: 住客在補充說明輸入 "{文字}"`

- [X] T117 [P] [BDD-RED] `When: 住客在補充說明輸入 {字數} 個字`

- [X] T118 [P] [BDD-RED] `Then: 只有 "{類別}" 一顆類別是選中的`

- [X] T119 [P] [BDD-RED] `Then: 補充說明的提示字是 "{提示字}"`

- [X] T120 [P] [BDD-RED] `Then: 字數是 "{字數}"`

- [X] T121 [P] [BDD-RED] `Then: 補充說明裡仍是原本的 200 個字`

- [X] T122 [P] [BDD-RED] `Then: 後端這張需求是打字的、帶語言 "{語言}"，沒有帶服務項目`

### `specs/truth/features/guest-web/我的需求/dsl.md`

- [X] T123 [P] [BDD-RED] `Given: 另一支手機已在備品補充送出 "{項目}"`

- [X] T124 [P] [BDD-RED] `Given: "{房號}" 房的住客從 "{入口}" 進了 "{標題}" 的進度頁`

- [X] T125 [P] [BDD-RED] `Then: 清單各列如下：`

- [X] T126 [P] [BDD-RED] `Then: 清單各列的標籤與段落如下：`

- [X] T127 [P] [BDD-RED] `Then: 每列左邊有依需求類型的圖示`

- [X] T128 [P] [BDD-RED] `Then: 各狀態標籤的顏色如下：`

- [X] T129 [P] [BDD-RED] `Then: 進度頁的第一張卡是標籤 "{標籤}"、標題 "{標題}" 與 "{第二行}"`

- [X] T130 [P] [BDD-RED] `Then: 進度頁沒有狀態訊息框`

- [X] T131 [P] [BDD-RED] `Then: 進度頁上唯一的時間是 "{時間}"`

### `specs/truth/features/guest-web/取消與評分/dsl.md`

- [X] T132 [P] [BDD-RED] `Given: "{房號}" 房的住客開著 "{標題}" 的進度頁，取消確認框開著`

- [X] T133 [P] [BDD-RED] `Given: "{房號}" 房的住客開著 "{標題}" 的進度頁，已點第 {星等} 顆星`

- [X] T134 [P] [BDD-RED] `Given: 住客的畫面還沒跟上，"{員工}" 已在手機上認領了 "{標題}"`

- [X] T135 [P] [BDD-RED] `Given: 住客的畫面還沒跟上，另一支手機已替 "{標題}" 送出了 {星等} 顆星`

- [X] T136 [P] [BDD-RED] `Given: "{房號}" 房的住客剛取消了 "{標題}"，停在它的進度頁`

- [X] T137 [P] [BDD-RED] `Given: "{房號}" 房的住客剛替 "{標題}" 送出了 {星等} 顆星，停在它的進度頁`

- [X] T138 [P] [BDD-RED] `When: 住客點第 {星等} 顆星`

- [X] T139 [P] [BDD-RED] `Then: 畫面出現確認框，標題 "{標題}"、內文 "{內文}"、按鈕 "{按鈕一}" 與 "{按鈕二}"`

- [X] T140 [P] [BDD-RED] `Then: 畫面上沒有任何確認框`

- [X] T141 [P] [BDD-RED] `Then: 畫面頂端浮出提示 "{文字}"，幾秒後自己消失`

- [X] T142 [P] [BDD-RED] `Then: 後端 "{標題}" 的需求是 "{狀況}"`

- [X] T143 [P] [BDD-RED] `Then: 後端 "{標題}" 的星等是 {星等}`

- [X] T144 [P] [BDD-RED] `Then: 五顆星都是空心的`

- [X] T145 [P] [BDD-RED] `Then: 只有前 {星等} 顆星是實心的`

- [X] T146 [P] [BDD-RED] `Then: 評分卡上沒有留言欄`

- [X] T147 [P] [BDD-RED] `Then: 進度頁有評分卡`

### `specs/truth/features/guest-web/即時更新/dsl.md`

- [X] T148 [P] [BDD-RED] `Given: 即時連線已經中斷`

- [X] T149 [P] [BDD-RED] `Given: 超過 {秒數} 秒沒有收到後端的任何訊號`

- [X] T150 [P] [BDD-RED] `Given: 後端已宣告即時訊號可能延遲`

- [X] T151 [P] [BDD-RED] `Given: 即時連線中斷後已經恢復`

- [X] T152 [P] [BDD-RED] `When: "{員工}" 在手機上{動作} "{標題}"`

- [X] T153 [P] [BDD-RED] `When: 櫃檯當場回答 "{標題}"，回覆 "{回覆}"`

- [X] T154 [P] [BDD-RED] `When: 櫃檯取消 "{標題}"`

- [X] T155 [P] [BDD-RED] `When: 另一支手機在備品補充送出 "{項目}"`

- [X] T156 [P] [BDD-RED] `When: 另一支手機取消 "{標題}"`

- [X] T157 [P] [BDD-RED] `When: 即時連線中斷`

- [X] T158 [P] [BDD-RED] `When: 後端要求重新同步`

- [X] T159 [P] [BDD-RED] `Then: {秒數} 秒內 "{標題}" 的狀態標籤變成 "{標籤}"`

- [X] T160 [P] [BDD-RED] `Then: {秒數} 秒內 "{步}" 那一步的副文變成 "{副文}"`

- [X] T161 [P] [BDD-RED] `Then: {秒數} 秒內進度頁底部變成 "{底部}"`

- [X] T162 [P] [BDD-RED] `Then: {秒數} 秒內狀態訊息框變成 "{標題}"，內文 "{內文}"`

- [X] T163 [P] [BDD-RED] `Then: {秒數} 秒內進度頁出現評分卡`

- [X] T164 [P] [BDD-RED] `Then: {秒數} 秒內四步進度走到 "{步}"`

- [X] T165 [P] [BDD-RED] `Then: {秒數} 秒內清單 "{段落}" 由上到下是 "{標題清單}"`

- [X] T166 [P] [BDD-RED] `Then: {秒數} 秒內首頁的進度卡是 "{標題}"`

- [X] T167 [P] [BDD-RED] `Then: {秒數} 秒內首頁的進度卡的進度條亮到 "{步}"，說明是 "{說明}"`

- [X] T168 [P] [BDD-RED] `Then: {秒數} 秒內畫面上看得到 "{文字}"`

- [X] T169 [P] [BDD-RED] `Then: {秒數} 秒內畫面重新讀取了首頁資料與需求清單`

- [X] T170 [P] [BDD-RED] `Then: 這段期間住客沒有按任何東西，也沒有重新整理網頁`

- [X] T171 [P] [BDD-RED] `Then: 畫面沒有每 4 秒定時重新讀取需求清單`

- [X] T172 [P] [BDD-RED] `Then: 畫面上沒有任何提示列或錯誤訊息`

### `specs/truth/features/guest-web/住宿指南/dsl.md`

- [X] T173 [P] [BDD-RED] `Given: 飯店設定了以下資訊頁：`

- [X] T174 [P] [BDD-RED] `Given: 飯店沒有設定任何資訊頁`

- [X] T175 [P] [BDD-RED] `Given: 飯店的 "{標題}" 資訊頁內文是兩行 "{第一行}" 與 "{第二行}"`

- [X] T176 [P] [BDD-RED] `Then: 首頁的住宿指南由上到下是 "{標題清單}"`

- [X] T177 [P] [BDD-RED] `Then: 住宿指南的列上沒有圖片、營業時間或地點`

- [X] T178 [P] [BDD-RED] `Then: 首頁 "服務" 下面直接是 "{文字}"`

- [X] T179 [P] [BDD-RED] `Then: 畫面是 "{標題}" 的資訊頁`

- [X] T180 [P] [BDD-RED] `Then: 資訊頁的內文是 "{內文}"`

- [X] T181 [P] [BDD-RED] `Then: 資訊頁的內文是兩行 "{第一行}" 與 "{第二行}"`

- [X] T182 [P] [BDD-RED] `Then: 資訊頁的內文是空白的`

### `specs/truth/features/guest-web/共用外框/dsl.md`

- [X] T183 [P] [BDD-RED] `Given: 這支手機的瀏覽器語言是 "{語言}"，還沒有選過介面語言`

- [X] T184 [P] [BDD-RED] `Given: "{房號}" 房的住客已打開首頁，語言選單開著`

- [X] T185 [P] [BDD-RED] `Given: "{房號}" 房的另一支手機也打開了首頁，它的介面語言是 "{語言}"`

- [X] T186 [P] [BDD-RED] `When: 住客點語言膠囊`

- [X] T187 [P] [BDD-RED] `When: 住客把頁面捲到底`

- [X] T188 [P] [BDD-RED] `Then: 介面語言是 "{語言}"`

- [X] T189 [P] [BDD-RED] `Then: 語言膠囊顯示 "{短寫法}"`

- [X] T190 [P] [BDD-RED] `Then: 語言選單列出 "{選項清單}"，打勾的是 "{選項}"`

- [X] T191 [P] [BDD-RED] `Then: 畫面上沒有語言選單`

- [X] T192 [P] [BDD-RED] `Then: 這支手機記住的介面語言是 "{語言}"`

- [X] T193 [P] [BDD-RED] `Then: 另一支手機的介面語言仍是 "{語言}"`

- [X] T194 [P] [BDD-RED] `Then: 畫面上沒有子頁的標題列`

- [X] T195 [P] [BDD-RED] `Then: 頁面沒有橫向捲動，內容是單欄置中`

- [X] T196 [P] [BDD-RED] `Then: 每一個可以點的東西至少 44 點見方`

- [X] T197 [P] [BDD-RED] `Then: "{標題}" 那一列的標題換行或以「…」截斷，沒有蓋到狀態標籤`

- [X] T198 [P] [BDD-RED] `Then: 進度頁的標題 "{標題}" 完整換行顯示`

- [X] T199 [P] [BDD-RED] `Then: 按鈕 "{按鈕}" 仍釘在頁底`

- [X] T200 subagent review (phase quality gate)

## Phase 4：Feature phases

**共通約定**（每個 Phase 4-xx 都適用，不再逐段重複）:
- 完成判準：該 phase 的 Test Scope 以 `bash app/web-bdd/run-feature.sh guest-web/<模組>/<檔名>.feature` 全綠（`phone`），再跑 `cd app/web && npm run typecheck && npx vitest run` 綠。只有 `phone` 一組（Scope Note 第 13 條）。
- 畫面對齊該 phase 點名的雛形；畫面文字一律走 `app/web/src/i18n/locales/*.ts` 的 `guest` 子樹，四份語系檔都要有鍵（鍵集合一致性測試會擋），繁體中文的字面照 feature；英、日、越的譯文照 `ui/guest.js` 字典已有的，沒有的先放繁體中文並列進 `implement-notes.md` 的「待翻譯」清單，由 Phase 4-28 收齊。畫面上不得出現 `triage`、`pool`、`housekeeping` 這類內部值、錯誤碼，也不得出現「待派單」「待認領」「延後」「中斷」「查房」「驗收」這些飯店內部用語。
- 不改 truth、不改 stepdef 的語意（stepdef 真的寫錯回 Phase 3 那一題修）。後端行為不對照 Scope Note 第 10 條記下，不改後端。
- 失敗情境（`429`、連不上、`5xx`）都是 Phase 3 的步驟以攔截做出來的；產品只看 HTTP 結果。
- `[BDD-REFACTOR]` 在綠燈下做，做完 Test Scope 要再綠一次。
- 每個 phase 的 Boundary 第一條寫它開工前要先綠的 phase 與原因；沒有寫的只依賴前一個 phase。

**Parallel Hint**（依產品檔案衝突與跨頁依賴；同一條線內依序，線與線平行）:
- 先做 4-01 → 4-02。
- 4-02 之後分三條線：
  - 首頁線（`HomePage.tsx`）：4-03 → 4-07。
  - 隔離線（`api/`、`session.ts`、`GuestShell.tsx`）：4-04。
  - 子頁線（`SupplyPage.tsx`、`RepairPage.tsx`、`GuidePage.tsx`；`BottomBar`、`TitleBar` 在 4-05 定型）：4-05 → 4-06 → 4-08。
- 三條線會合後做 4-09，再分兩條線：
  - 請求頁線（`RequestPage.tsx`、`HomePage.tsx`）：4-10 → 4-11 → 4-12 → 4-16。
  - 進入層線（`GuestEntry.tsx`、`rescan.ts`、`api/client.ts`）：4-13；等 4-12 綠了再 4-14 → 4-15。
- 4-16 之後三條線：
  - 清單線（`RequestsPage.tsx`）：4-17。
  - 備品線（`SupplyPage.tsx`）：4-18 → 4-19。
  - 報修線（`RepairPage.tsx`）：4-20；等備品線也綠了再 4-21。
- 4-22 等 4-17 與 4-20。
- 4-23 等 4-22、4-15、4-21 全部綠，之後依序 4-24 → 4-25 → 4-26 → 4-27 → 4-28 → 4-29，全部單線（都碰 `RequestPage.tsx`、`GuestShell.tsx` 或要等全部頁面）。
- Phase 4-G 等 4-29；Phase 5 最後。

## Phase 4-01: ADD Feature File - guest-web/進入層/無法使用頁.feature

**Goal**: 空房、QR Code 已作廢、代碼不存在三種情形打開網址都是同一頁「請洽櫃檯」，文字一字不差、看不出原因；這一頁沒有首頁的任何內容、沒有可以點的東西、沒有標題列。

**Shared Must Read**:
- `specs/truth/features/guest-web/進入層/無法使用頁.feature` -> `功能: 無法使用頁` 全文（2 條 Rule、4 個 Example）
- `specs/truth/features/guest-web/進入層/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 5 句（T055、T067–T068、T073–T074）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 5 句（T019、T030、T035–T036、T041）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/進入層/無法使用頁.feature` 那一列；`/api-plan` 的 `/guest/scan` 缺口列（三種情形同一個 `409 room_vacant`）；`/data-plan` 的 `room_qr_tokens` 列（換 QR 作廢舊代碼）
- `ui/ui-plan.md` -> 畫面 1「進入層」的狀態表（載入中、無法使用）、「待釐清 … 的處理結果」C-01、畫面 10 的網頁標題
- `ui/unavailable.html`（預設與 `?state=loading`）、`ui/guest.css`、`ui/guest.js` 的進入層段 -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/GuestEntry.tsx`、`components/CenteredNotice.tsx`

**Boundary**:
- `scan` 回 `409 room_vacant` 顯示無法使用頁：標題「請洽櫃檯」、C-01 內文、白卡「櫃檯（24 小時）／房內電話撥 9」（不是連結）；載入中只有置中轉圈、沒有字。`429`、連不上、`5xx` 在這個 phase 先停在載入中（重試狀態是 4-15）。
- 無法使用頁沒有語言膠囊、沒有標題列；網頁標題「請洽櫃檯」。
- 本檔有 1 個 `# [need clarification]`，照 feature 目前寫的值實作，不停下來問。

**Test Scope**:
- `specs/truth/features/guest-web/進入層/無法使用頁.feature`

- [X] T201 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T202 [BDD-REFACTOR] 在綠燈下把「換 session 的結果 → 進入層畫面」收成一個 reducer（`loading`／`ready`／`unavailable`，重試與重換的狀態留位），不散成多個 boolean

## Phase 4-02: ADD Feature File - guest-web/首頁/首頁內容與問候.feature

**Goal**: 首頁由上到下的區塊順序、沒有需求時沒有「我的需求」；頂列右上房號膠囊與語言膠囊、左上留白；依時段三種問候、六個時刻；不顯示姓名、飯店名、日期；房號膠囊點了沒有選單；兩張主視覺卡的文字且不提照片；點主視覺卡進備品補充頁與設備報修頁。

**Shared Must Read**:
- `specs/truth/features/guest-web/首頁/首頁內容與問候.feature` -> `功能: 首頁內容與問候` 全文（7 條 Rule、8 個 Example）
- `specs/truth/features/guest-web/首頁/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 7 句（T078–T079、T081–T085）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 9 句（T017、T020–T021、T030–T031、T035–T037、T041）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/首頁/首頁內容與問候.feature` 那一列；`/api-plan` 的 `/guest/home` 缺口列（`infoPages`、`menu` 的形狀）；`/data-plan` 的 `rooms.room_no`／`reservations_cache` 列（`guest_name` 不給住客）
- `ui/ui-plan.md` -> 畫面 2「首頁」全部（九個區塊、狀態表、語言膠囊短寫法）、「視覺重點」、「版面尺寸與伸縮規則」的首頁各列、C-02／C-07／C-08 的處理結果
- `ui/index.html`（含 `?seed=new`、`?at=08:30`、`?at=19:00`）、`ui/guest.css`、`ui/guest.js` 的首頁段 -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/pages/HomePage.tsx`、`lib/greeting.ts`（T008 的殼，在這裡實作並補 `Vitest`）、`components/ComingSoonTag.tsx`、`components/LanguageMenu.tsx`（只畫膠囊，選單是 4-28）

**Boundary**:
- 首頁的 `useGuestHome` 與 `useGuestRequests` 都回來才畫，不畫半頁。「我的需求」整塊在這個 phase 只做「沒有需求時不出現」（有需求時的樣子是 4-12）。「服務」三列與提問列照雛形擺出來並標「即將開放」（點了的行為是 4-03）。「住宿指南」照 `infoPages` 的標題列出（空標題不列、整段不出現的邊界是 4-07）。
- `greeting.ts` 的 `Vitest` 驗 10:59／11:00／17:59／18:00 四個邊界。
- 本檔有 3 個 `# [need clarification]`，照 feature 目前寫的值實作，不停下來問。

**Test Scope**:
- `specs/truth/features/guest-web/首頁/首頁內容與問候.feature`

- [X] T203 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T204 [BDD-REFACTOR] 在綠燈下把首頁的九個區塊拆成各自的小元件，`HomePage` 只剩順序

## Phase 4-03: ADD Feature File - guest-web/首頁/即將開放的入口.feature

**Goal**: 提問列與「服務」三列共四個入口的文字、四個「即將開放」標籤、沒有收費或政策字句；點任何一個入口畫面不變、後端沒有多出需求。

**Shared Must Read**:
- `specs/truth/features/guest-web/首頁/即將開放的入口.feature` -> `功能: 即將開放的入口` 全文（2 條 Rule、2 個 Example）
- `specs/truth/features/guest-web/首頁/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 3 句（T080、T085、T088）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 6 句（T018、T021、T030、T035–T036、T053）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/首頁/即將開放的入口.feature` 那一列
- `ui/ui-plan.md` -> 畫面 2 的第 4 點（提問列）與第 7 點（「服務」）、「審美原則」裡即將開放入口的做法、「鍵盤與螢幕報讀」（入口是純文字，不是按鈕）
- `ui/index.html` -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/pages/HomePage.tsx`、`components/ComingSoonTag.tsx`

**Boundary**:
- 開工前要先綠：Phase 4-02（`首頁/首頁內容與問候.feature`）。
- 四個入口不是按鈕、沒有箭頭、沒有 `href`、沒有任何事件；原型的送出圓鈕與「12:00 前免費」等字句不畫。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/首頁/即將開放的入口.feature`

- [X] T205 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T206 [BDD-REFACTOR] 在綠燈下把四個入口收成一張資料表（文字鍵、插圖色）由同一個元件畫，之後開放時只改表

## Phase 4-04: ADD Feature File - guest-web/進入層/與櫃檯Web互不影響.feature

**Goal**: 登入過櫃檯 Web 的手機掃 311 房看到的是住客首頁、請求只帶 `X-Guest-Session` 不帶 `Authorization`；回到櫃檯 Web 仍是原本的登入狀態與總覽看板；開過住客頁的手機打開櫃檯 Web 仍要登入。

**Shared Must Read**:
- `specs/truth/features/guest-web/進入層/與櫃檯Web互不影響.feature` -> `功能: 與櫃檯 Web 互不影響` 全文（3 條 Rule、3 個 Example）
- `specs/truth/features/guest-web/進入層/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 6 句（T065–T066、T070、T075–T077）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 4 句（T021、T030、T036、T041）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/進入層/與櫃檯Web互不影響.feature` 那一列；`/api-plan` 的 `GuestSession` 列（`X-Guest-Session` 與 `Authorization: Bearer` 效果相同，住客頁只用前者）
- `specs/truth/techstack.md` -> `前端` 的「住客 session」「住客掃碼頁多語系」兩列（不同鍵、不同 store、不同標頭；外框掛上時主動切語言）
- `ui/ui-plan.md` -> 「狀態與資訊揭露」的「角色或權限差異」（住客頁不讀也不寫櫃檯的登入狀態）、畫面 2 的頂列
- `ui/index.html` -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/api/client.ts`、`session.ts`、`GuestShell.tsx`（T012 的 `useGuestLanguage`）

**Boundary**:
- 開工前要先綠：Phase 4-02（`首頁/首頁內容與問候.feature`）。
- 預期產品不用再改什麼；若紅了，只能是住客的請求帶到了 `Authorization`、住客外框讀到了櫃檯的 `sessionStorage` 鍵，或櫃檯的 `initialLanguage` 把住客頁帶走。修在住客這一側，不動櫃檯的 `useSessionStore`、`Gate`、`interface-language.ts`。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/進入層/與櫃檯Web互不影響.feature`

- [X] T207 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T208 [BDD-REFACTOR] 在綠燈下加一條 `Vitest` 靜態檢查：`app/web/src/guest/` 底下沒有任何檔 import `stores/session`、`lib/route-access` 或 `authHeaders`，把這條邊界鎖住

## Phase 4-05: ADD Feature File - guest-web/備品補充/項目清單與數量.feature

**Goal**: 清單列後端預設五項或飯店自訂的項目、照順序；剛打開全部是 0、減號與「選擇數量」不能按；加號加 1、頁底變「送出 {n} 項」；到 5 加號不能按；減到 0 減號不能按。

**Shared Must Read**:
- `specs/truth/features/guest-web/備品補充/項目清單與數量.feature` -> `功能: 備品補充的項目清單與數量` 全文（5 條 Rule、6 個 Example）
- `specs/truth/features/guest-web/備品補充/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 11 句（T089–T091、T098–T099、T101–T106）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 5 句（T021、T025、T032、T037、T040）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/備品補充/項目清單與數量.feature` 那一列；`/api-plan` 的 `/guest/home` 缺口列（`menu[].key`、`label`）；`/data-plan` 的 `tenant_config` `request_menu` 列
- `ui/ui-plan.md` -> 畫面 3「備品補充」的版面與狀態表前三列、「版面尺寸與伸縮規則」的數量增減鈕與頁底操作列、「鍵盤與螢幕報讀」（加減鈕的名稱「減少{名稱}」「增加{名稱}」）
- `ui/supply.html`、`ui/guest.css`、`ui/guest.js` 的備品段 -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/pages/SupplyPage.tsx`、`components/BottomBar.tsx`、`components/TitleBar.tsx`

**Boundary**:
- 開工前要先綠：Phase 4-02（`首頁/首頁內容與問候.feature`）。
- 項目名稱照後端原文、不翻譯；每項上限 5，提示「最多 5」；按鈕下「還沒有同仁接手前，都可以自己取消」、頁首「選好數量一次送出，每一項會分別交給房務。」。送出鈕按了做什麼是 4-16（這裡按了可以什麼都不發生）。
- 鍵用後端回的 `menu[].key`，不寫死（Scope Note 第 8 條）。`BottomBar` 與 `TitleBar` 由這個 phase 定型，之後的頁只用、不改。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/備品補充/項目清單與數量.feature`

- [X] T209 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T210 [BDD-REFACTOR] 在綠燈下把數量狀態收成一個 `useReducer`（`increment`／`decrement`／`reset`，上限與下限在 reducer 裡），按鈕文字由選出的項目數推得

## Phase 4-06: ADD Feature File - guest-web/設備報修/報修頁與送出條件.feature

**Goal**: 設備報修頁的內容與沒有照片；沒選類別不能送；選了就能送；「其他」必填（沒填、只有空白、填了三例）；改選別的類別回到「選填」且只有一顆選中；字數「{n} / 200」、打到 200 字再打進不去。

**Shared Must Read**:
- `specs/truth/features/guest-web/設備報修/報修頁與送出條件.feature` -> `功能: 設備報修頁與送出條件` 全文（6 條 Rule、10 個 Example）
- `specs/truth/features/guest-web/設備報修/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 10 句（T110–T111、T113、T115–T121）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 8 句（T021、T032、T035–T040）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/設備報修/報修頁與送出條件.feature` 那一列
- `ui/ui-plan.md` -> 畫面 4「設備報修」的版面與狀態表前四列、「版面尺寸與伸縮規則」的類別膠囊與輸入框、「鍵盤與螢幕報讀」（類別膠囊是單選群組）
- `ui/repair.html`、`ui/guest.css`、`ui/guest.js` 的報修段 -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/pages/RepairPage.tsx`、`lib/repair-text.ts`（T008 的殼，在這裡實作 `REPAIR_CATEGORIES`、`NOTE_MAX`、`composeRepairText` 並補 `Vitest`）

**Boundary**:
- 開工前要先綠：Phase 4-05（`備品補充/項目清單與數量.feature`）。共用 4-05 定型的 `BottomBar`。
- 六顆類別是畫面上固定的字（膠囊顯示介面語言，送出的文字一律繁體中文，4-20 用到）；補充說明 `maxLength` 200、以字元計；「其他」時標籤「補充說明（必填）」、去掉頭尾空白後空的不能送。頁面上沒有照片的任何東西。送出鈕按了做什麼是 4-20。
- `repair-text.ts` 的 `Vitest` 驗四種組合（只有類別、類別加說明、「其他」只有說明、說明頭尾空白被去掉）。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/設備報修/報修頁與送出條件.feature`

- [X] T211 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T212 [BDD-REFACTOR] 在綠燈下把「能不能送」收成一個純函式 `canSubmitRepair(category, note)` 放進 `repair-text.ts` 並補測試，頁面只呼叫它

## Phase 4-07: ADD Feature File - guest-web/住宿指南/首頁的住宿指南清單.feature

**Goal**: 首頁「住宿指南」是純清單、照後端順序、首頁上沒有內文與「複製」；飯店清空資訊頁時整段不出現、「服務」下面直接是緊急提示；標題空的那一頁不列。

**Shared Must Read**:
- `specs/truth/features/guest-web/住宿指南/首頁的住宿指南清單.feature` -> `功能: 首頁的住宿指南清單` 全文（3 條 Rule、3 個 Example）
- `specs/truth/features/guest-web/住宿指南/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 5 句（T173–T174、T176–T178）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 3 句（T030、T036、T038）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/住宿指南/首頁的住宿指南清單.feature` 那一列；`/api-plan` 的 `/guest/home` 缺口列（`infoPages`）；`/data-plan` 的 `tenant_config` 列與段末備註（標題空白時是 null、後端不過濾）
- `ui/ui-plan.md` -> 畫面 2 的第 8 點、狀態表「飯店沒有任何資訊頁」列、「與原型不同的地方」住宿指南那一列
- `ui/index.html?info=none`、`?info=blank` -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/pages/HomePage.tsx`、`lib/info-pages.ts`（新檔）

**Boundary**:
- 開工前要先綠：Phase 4-03（`首頁/即將開放的入口.feature`）。同樣改 `HomePage.tsx`。
- 標題空的（null 或只有空白）不列；一頁都不剩時整段（標題與白卡）不出現。每列只有標題與箭頭。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/住宿指南/首頁的住宿指南清單.feature`

- [X] T213 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T214 [BDD-REFACTOR] 在綠燈下把「哪些資訊頁可以列」收成 `lib/info-pages.ts` 的純函式 `listableInfoPages(pages)` 並補 `Vitest`

## Phase 4-08: ADD Feature File - guest-web/住宿指南/資訊頁內容.feature

**Goal**: 點一列進內容頁，標題列是返回鈕、該頁標題、「房號 311」，內文照原文；換行照原文保留；返回回首頁；英文介面時標題與內文仍是原文；內文空的頁打得開、內容區留白。

**Shared Must Read**:
- `specs/truth/features/guest-web/住宿指南/資訊頁內容.feature` -> `功能: 資訊頁內容` 全文（5 條 Rule、5 個 Example）
- `specs/truth/features/guest-web/住宿指南/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 6 句（T173、T175、T179–T182）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 7 句（T021、T024、T027、T031、T036、T041、T043）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/住宿指南/資訊頁內容.feature` 那一列
- `ui/ui-plan.md` -> 畫面 8「住宿指南內容」全部、畫面 10 的網頁標題
- `ui/guide.html?key=wifi`（另有 `breakfast`、`checkout`） -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/pages/GuidePage.tsx`

**Boundary**:
- 開工前要先綠：Phase 4-06（`設備報修/報修頁與送出條件.feature`）。排在 4-05、4-06 之後是因為 `TitleBar` 由 4-05 定型；這一頁只用、不改它。
- 網址 `/g/{代碼}/guide/{鍵}`；內文用 `white-space: pre-wrap` 保留原文的換行與空白、不解析任何標記；標題與內文不翻譯；鍵找不到時回首頁。資料用 T013 的 `useGuestHome`，不另發請求。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/住宿指南/資訊頁內容.feature`

- [X] T215 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T216 [BDD-REFACTOR] 在綠燈下把內文區收成只吃一個 `GuestInfoPage` 的元件，「鍵找不到回首頁」放在頁面容器一處

## Phase 4-09: ADD Feature File - guest-web/我的需求/狀態對照表.feature

**Goal**: 十種狀況在清單上的標籤與段落、在進度頁的標籤、四步與底部（十列大綱）；取消鈕的文字、鎖定說明的文字；五種標籤的顏色；清單上沒有飯店內部用語與同仁名字；部門三種字且待派單是「櫃檯」。

**Shared Must Read**:
- `specs/truth/features/guest-web/我的需求/狀態對照表.feature` -> `功能: 狀態對照表` 全文（7 條 Rule、7 個 Example）
- `specs/truth/features/guest-web/我的需求/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 3 句（T125–T126、T128）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 10 句（T018、T032–T033、T035–T038、T044、T047、T049）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/我的需求/狀態對照表.feature` 那一列；`/api-plan` 的 `GuestRequest` 列（`state` 十個值含 `closed`）；`/data-plan` 的 `tasks` 列（`state`、`dept` 的 CHECK）
- `ui/ui-plan.md` -> 「狀態對照表怎麼落到三個畫面」整節、「設計變數」裡五種標籤的色、畫面 6 的清單列、畫面 7 的版面與狀態表前三列（可以取消、鎖定、已結束的底部）、「用到的後端資料」的需求圖示段
- `ui/requests.html?seed=all`、`ui/request.html?seed=all&id=…`（十個值） -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/pages/RequestsPage.tsx`、`pages/RequestPage.tsx`、`components/RequestRow.tsx`、`StatusTag.tsx`、`RequestIcon.tsx`、`FourSteps.tsx`、`lib/time.ts`（T008 的殼，在這裡實作 `hhmm` 並補 `Vitest`）、`lib/stage.ts`（T007；表有錯在這裡修並補測試）

**Boundary**:
- 開工前要先綠：Phase 4-07（`住宿指南/首頁的住宿指南清單.feature`）、Phase 4-08（`住宿指南/資訊頁內容.feature`）、Phase 4-04（`進入層/與櫃檯Web互不影響.feature`）。從這裡開始是清單與進度頁，前面四條平行的線要在這裡會合。
- 這一份只用網址直接開清單與進度頁，資料全靠 Given 種好，不需要送出。清單列整列是進那一筆進度頁的連結（4-11 會點它）。進度頁在這裡做出第一張卡、四步的樣子與底部；各步副文是 4-11、訊息框是 4-10。
- 底部：可以取消的顯示灰底紅字「取消這個需求」與「還沒有同仁接手前，都可以自己取消」（按了做什麼是 4-23）；鎖定的只顯示「同仁已經接手。需要更改或取消，請用房內電話撥 9 找櫃檯。」；已結束什麼都沒有。
- 標籤四種色調：中性（已送出、已取消同色）、已受理色、處理中色（品牌實心白字）、已完成色；部門字由 `deptLabelKey` 出，空值也是「櫃檯」。段落、標籤、底部全部從 `stage.ts` 拿，頁面不看 `state` 的值。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/我的需求/狀態對照表.feature`

- [X] T217 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T218 [BDD-REFACTOR] 在綠燈下確認清單列與進度頁沒有任何一處直接比對 `state` 字串，全部經 `stage.ts`；有就收回去

## Phase 4-10: ADD Feature File - guest-web/我的需求/狀態訊息框.feature

**Goal**: 待派單顯示「櫃檯正在看您的需求／會交給負責的同仁，或直接在這裡回覆您。」且有進度條；三種已完成顯示「已完成／已處理完成。」且四步全亮；櫃檯回覆時顯示「櫃檯回覆」與原文、沒有進度條、沒有評分卡、底部什麼都沒有、清單上在已結束；已取消兩種都是同一句、不顯示原因；其餘四種狀況沒有訊息框。

**Shared Must Read**:
- `specs/truth/features/guest-web/我的需求/狀態訊息框.feature` -> `功能: 狀態訊息框` 全文（5 條 Rule、6 個 Example）
- `specs/truth/features/guest-web/我的需求/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 1 句（T130）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 10 句（T018、T032–T033、T036、T044–T047、T049–T050）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/我的需求/狀態訊息框.feature` 那一列；`/api-plan` 的 `GuestRequest` 列（`answer` 的形狀）；`/data-plan` 的 `tasks` `cancel_reason` 列（不給住客）
- `ui/ui-plan.md` -> 「狀態對照表怎麼落到三個畫面」的五個推導值與大表的「訊息框」欄、畫面 7 的版面、「設計變數」的訊息框色與圖示
- `ui/request.html?seed=all&id=…`（`triage`、`done`、`inspected`、`closed`、`cancelled`、`answer`） -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/components/MessageBox.tsx`、`pages/RequestPage.tsx`、`lib/stage.ts` 的 `messageBox`／`showsProgress`（T007 已有）

**Boundary**:
- 開工前要先綠：Phase 4-09（`我的需求/狀態對照表.feature`）。
- 訊息框依序取第一條符合的：已取消 → 櫃檯有回覆 → `triage` → 已完成 → 沒有；已取消與櫃檯回覆時不顯示四步。評分卡在這個 phase 只需要「不出現」（評分卡本身是 4-24）。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/我的需求/狀態訊息框.feature`

- [X] T219 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T220 [BDD-REFACTOR] 在綠燈下讓 `MessageBox` 只吃 `messageBox(task)` 回的 `{ kind, titleKey, body }`，四種色調與圖示由 `kind` 查表

## Phase 4-11: ADD Feature File - guest-web/我的需求/進度頁內容與四步副文.feature

**Goal**: 進度頁的標題列與第一張卡（圖示、標籤、標題、「{送出時間} 送出 · {部門}」）；八種狀況的四步樣子與副文（含工務收到、中斷沒有內部字、已完成只有第 1 步有副文）；沒有時間與姓名、唯一的時間是送出時間；24 小時制「時:分」沒有日期（進度頁與清單各一）。

**Shared Must Read**:
- `specs/truth/features/guest-web/我的需求/進度頁內容與四步副文.feature` -> `功能: 進度頁內容與四步副文` 全文（4 條 Rule、12 個 Example）
- `specs/truth/features/guest-web/我的需求/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 4 句（T125、T129–T131）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 9 句（T018、T022、T031–T033、T036、T042–T043、T048）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/我的需求/進度頁內容與四步副文.feature` 那一列；`/data-plan` 的 `tasks` `assignee_id`／`started_at`／`done_at` 列（資料存在但不給住客）
- `ui/ui-plan.md` -> 畫面 7「需求進度」的版面、「狀態對照表怎麼落到三個畫面」的十個狀態值大表（四步欄）與「打勾／實心／空心」的定義、「版面尺寸與伸縮規則」的四步進度
- `ui/request.html?seed=all&id=…`（`triage`、`pool`、`accepted`、`doing`、`interrupted`、`done`） -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/pages/RequestPage.tsx`、`components/FourSteps.tsx`、`lib/stage.ts` 的 `stepView`（T007 已有）

**Boundary**:
- 開工前要先綠：Phase 4-10（`我的需求/狀態訊息框.feature`）。同樣改 `RequestPage.tsx`；第一個 Example 從清單點一列進來，用到 4-09 的清單列連結。
- 四步的樣子與副文全部由 `stepView` 推得；畫面上只有「{送出時間} 送出」一處時間，沒有接手人姓名。網址上的需求不在這次住宿的清單裡時換成清單（`ui/ui-plan.md` 畫面 7 最後一列）。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/我的需求/進度頁內容與四步副文.feature`

- [X] T221 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T222 [BDD-REFACTOR] 在綠燈下把進度頁拆成「第一張卡」「訊息框」「四步」「評分卡」「底部」五個只吃一張 `GuestTask` 的元件，`RequestPage` 只負責取資料與排列

## Phase 4-12: ADD Feature File - guest-web/首頁/我的需求區.feature

**Goal**: 有進行中的需求時顯示最新一張的進度卡與「全部 {n} 項」；進度卡的固定內容（眉標、標題、四格進度條、四個步名、一行說明、「查看」）；六種狀況的進度條與說明；全部結束時「目前沒有進行中的需求／查看紀錄」且 n 含已取消；「查看」進進度頁；「查看紀錄」與「全部 {n} 項」進清單。

**Shared Must Read**:
- `specs/truth/features/guest-web/首頁/我的需求區.feature` -> `功能: 首頁的我的需求區` 全文（6 條 Rule、8 個 Example）
- `specs/truth/features/guest-web/首頁/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 2 句（T086–T087）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 9 句（T018、T021、T030–T031、T035、T037、T041–T042、T051）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/首頁/我的需求區.feature` 那一列
- `ui/ui-plan.md` -> 畫面 2 的第 5 點與「首頁進度卡的一行說明」表、狀態表三列、「狀態對照表怎麼落到三個畫面」的「首頁進度卡」欄、「版面尺寸與伸縮規則」的我的需求列
- `ui/index.html`（預設、`?seed=ended`） -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/pages/HomePage.tsx`、`components/ProgressCard.tsx`、`lib/stage.ts` 的 `latestActive`／`homeCardLine`（T007 已有）

**Boundary**:
- 開工前要先綠：Phase 4-11（`我的需求/進度頁內容與四步副文.feature`）。「查看」要看到進度頁的第一張卡，「全部 {n} 項」要進清單。
- 進度卡整張是一個連結，進那一筆的進度頁並在 location state 帶 `from: 'home'`（返回的行為是 4-22）；「全部 {n} 項」與「查看紀錄」進清單。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/首頁/我的需求區.feature`

- [X] T223 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T224 [BDD-REFACTOR] 在綠燈下讓「我的需求」區的三態（不出現、進度卡、沒有進行中那一列）由一個純函式 `homeRequestsSection(tasks)` 決定並補 `Vitest`

## Phase 4-13: ADD Feature File - guest-web/進入層/掃碼就進首頁.feature

**Goal**: 第一次打開床頭卡上的網址不用輸入任何東西就是首頁、0 次輸入、沒有要再掃一次也沒有錯誤；在首頁重新整理還是首頁；關掉再從歷史記錄打開，首頁回首頁、「毛巾」的進度頁回那一頁。

**Shared Must Read**:
- `specs/truth/features/guest-web/進入層/掃碼就進首頁.feature` -> `功能: 掃碼就進首頁` 全文（3 條 Rule、4 個 Example）
- `specs/truth/features/guest-web/進入層/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 3 句（T069、T071–T072）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 8 句（T018、T021、T023、T030、T034–T035、T041–T042）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/進入層/掃碼就進首頁.feature` 那一列
- `specs/truth/techstack.md` -> `前端` 的「住客掃碼頁換頁與載入」「住客 session」兩列（每個子頁都帶代碼；session 存 `sessionStorage`、鍵帶代碼）
- `ui/ui-plan.md` -> 畫面 1 的「進入條件」「成功流轉」、畫面 10 的網頁標題
- `ui/unavailable.html?state=loading` -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/GuestEntry.tsx`、`routes.tsx`、`session.ts`

**Boundary**:
- 開工前要先綠：Phase 4-09（`我的需求/狀態對照表.feature`）。「毛巾」的進度頁由 4-09 做出來。只改 `GuestEntry.tsx`、`routes.tsx`、`session.ts`，與 4-10～4-12 的檔不重疊，可以和它們平行。
- 新分頁（沒有 session）開子頁網址：換 session 後停在那一頁，不跳回首頁；網址裡的代碼不變；過程沒有提示浮層與錯誤字。網頁標題照畫面 10。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/進入層/掃碼就進首頁.feature`

- [X] T225 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T226 [BDD-REFACTOR] 在綠燈下把 T004 量到的延遲載入秒數與這個 phase 在 `phone` 下的實跑秒數並列記進 `implement-notes.md`，刪掉 T004 那支不再需要的 spike 測試

## Phase 4-14: ADD Feature File - guest-web/進入層/憑證失效時靜靜重換.feature

**Goal**: 正在用時 QR Code 被換掉，下一次向後端拿資料就變成無法使用頁；憑證不見（新分頁）或後端不再承認（已撤銷）時，用網址裡的代碼靜靜重換一次並接著原本要做的事（重新整理、點主視覺卡各一例）；退房之後再開是無法使用頁。整個過程不要住客再掃一次、不出現錯誤訊息。

**Shared Must Read**:
- `specs/truth/features/guest-web/進入層/憑證失效時靜靜重換.feature` -> `功能: 憑證失效時靜靜重換` 全文（3 條 Rule、4 個 Example）
- `specs/truth/features/guest-web/進入層/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 6 句（T055–T058、T072–T073）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 5 句（T018、T021、T031、T034、T041）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/進入層/憑證失效時靜靜重換.feature` 那一列；`/api-plan` 的 `GuestSession`／`Unauthorized` 列（過期、撤銷、沒有合法前綴一律 401）；`/data-plan` 的 `guest_sessions` 列（`expires_at` 是退房時刻、`revoked_at`）
- `specs/truth/techstack.md` -> `前端` 的「住客 session」列（401 用網址裡的代碼只重換一次）
- `ui/ui-plan.md` -> 畫面 1 的「進入條件」與「成功流轉」、「驗證與錯誤回饋」狀態衝突表的 `401` 列
- `ui/unavailable.html`（重換失敗時畫在原地） -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/lib/rescan.ts`（T008 的殼，在這裡實作並補 `Vitest`）、`api/client.ts` 的 `setUnauthorizedHandler`、`GuestEntry.tsx`、`session.ts`

**Boundary**:
- 開工前要先綠：Phase 4-12（`首頁/我的需求區.feature`）、Phase 4-13（`進入層/掃碼就進首頁.feature`）。第一個 Example 要按首頁的「全部 1 項」（4-12）；同樣改 `GuestEntry.tsx`（4-13）。
- 任何一次呼叫回 `401`：丟掉手上的 session → 用網址裡的代碼 `scan` → 成功就寫回 store 並重發剛才那一次呼叫（畫面不變）→ 回 `409` 就顯示無法使用頁。同一輪只重換一次；重換期間其他呼叫等它。`rescan.ts` 的 `Vitest` 驗：有 session → 401 → 重換成功重發；401 → 重換 409 → `unavailable`；重換後再 401 不再重換。
- 網址裡的代碼永遠不變；重換過程畫面上沒有提示浮層、沒有錯誤字。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/進入層/憑證失效時靜靜重換.feature`

- [X] T227 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T228 [BDD-REFACTOR] 在綠燈下確認所有住客的查詢與寫入都經同一個 `guestSend`，401 的處理只在一處

## Phase 4-15: ADD Feature File - guest-web/進入層/重試狀態.feature

**Goal**: 連不上、被限流、後端出錯三種情形都顯示「連線失敗，請再試一次」與「重試」、不顯示「請洽櫃檯」；按重試換到 session 就進首頁，後端回無法使用就變無法使用頁，又失敗留在重試狀態；正在用時重換也失敗一樣顯示重試狀態，後端恢復後按重試回到原本要去的那一頁（清單）。

**Shared Must Read**:
- `specs/truth/features/guest-web/進入層/重試狀態.feature` -> `功能: 重試狀態` 全文（5 條 Rule、8 個 Example）
- `specs/truth/features/guest-web/進入層/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 10 句（T055、T057、T059–T064、T072–T073）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 10 句（T018、T021、T028、T030–T031、T036–T039、T041）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/進入層/重試狀態.feature` 那一列；`/api-plan` 的 `/guest/scan` 缺口列（`429 rate_limited`）與 `TooManyRequests` 列
- `ui/ui-plan.md` -> 畫面 1 的狀態表（重試、按了重試還在等、三種結果）
- `ui/unavailable.html?state=retry`（含 `&then=unavailable`） -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/GuestEntry.tsx`、`components/CenteredNotice.tsx`、`lib/rescan.ts`

**Boundary**:
- 開工前要先綠：Phase 4-14（`進入層/憑證失效時靜靜重換.feature`）。用到 4-14 的重換與 4-12 的「全部 1 項」，最後一個 Example 回到 4-09 的清單。
- `scan` 回 `429`、`5xx`、連不上 → 重試狀態（灰色 Wi‑Fi 圖示、標題、滿寬實心「重試」）；按「重試」期間按鈕不能按、轉圈、字不變。重試成功進原本要去的那一頁（子頁網址也要記得）；`409` 變無法使用頁；又失敗留在重試狀態。
- 正在用時 401 重換遇到 `5xx`／連不上也走同一個重試狀態（4-01 的 reducer 加 `retry` 與 `retrying`）。這些失敗全部由 Phase 3 的步驟以攔截做出來。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/進入層/重試狀態.feature`

- [X] T229 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T230 [BDD-REFACTOR] 在綠燈下把「原本要去的那一頁」收成一個值（目前網址）而不是各頁各自記，確認 reducer 的五個狀態之間沒有不可達的轉移

## Phase 4-16: ADD Feature File - guest-web/備品補充/送出鈕與成功頁.feature

**Goal**: 「送出 {n} 項」與按鈕下的字；送出時每項各建一張、帶數量、照清單順序、前一張回來才送下一張；多項的成功頁「已送出 {n} 項需求」、一項的「需求已送出」且標題不帶 ×1；多項「查看進度」進清單、一項進那一筆；「回首頁」後進度卡是剛送的；送出後數量歸零；網路慢時連按兩下只建一次。

**Shared Must Read**:
- `specs/truth/features/guest-web/備品補充/送出鈕與成功頁.feature` -> `功能: 備品補充的送出鈕與成功頁` 全文（9 條 Rule、10 個 Example）
- `specs/truth/features/guest-web/備品補充/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 8 句（T091–T093、T096、T098、T100、T102、T107）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 14 句（T025、T031–T032、T035、T037–T042、T044–T045、T051–T052）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/備品補充/送出鈕與成功頁.feature` 那一列；`/api-plan` 的 `/guest/requests` 缺口列（`itemId` 與 `text` 擇一）、`IdempotencyKey` 兩列（有帶才生效、同鍵同時在途的缺口）
- `specs/truth/techstack.md` -> `前端` 的「住客掃碼頁連後端」列（每個寫入帶 `Idempotency-Key`、多項依序一張一張送）
- `ui/ui-plan.md` -> 畫面 3 的狀態表「有 n 項大於 0」「送出中」「全部建立成功」三列與最後一段、畫面 5「送出成功」全部、「前端實作切分建議」的返回到進來的地方
- `ui/supply.html`、`ui/sent.html?ids=…`（一個與兩個） -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/pages/SupplyPage.tsx`、`pages/SentPage.tsx`、`lib/submit.ts`（新檔）、`api/client.ts` 的 `createRequest`

**Boundary**:
- 開工前要先綠：Phase 4-12（`首頁/我的需求區.feature`）。成功頁的三個去處要用到清單（4-09）、進度頁（4-11）與首頁進度卡（4-12）。只改 `SupplyPage.tsx`、`SentPage.tsx` 與新檔，可以和 4-13～4-15 平行。
- 送出中：頁底按鈕不能按、轉圈、字不變，加減鈕全部不能按；每一張自己一把 `newIdempotencyKey()`；全部成功後數量歸零、換成 `/g/{代碼}/sent?ids=…`。成功頁沒有標題列；「查看進度」一項進那一筆（location state `from: 'sent'`）、多項進清單；「回首頁」回首頁。T016 判定冪等鍵不回放時照它記的退路做。
- 需求標題由後端決定，這一頁不組標題。這一份每個案例真的送到後端最多 2 筆，在限流替身的預算內。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/備品補充/送出鈕與成功頁.feature`

- [X] T231 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T232 [BDD-REFACTOR] 在綠燈下把「依序送、每張一把鍵、中途停」寫成 `lib/submit.ts` 不碰畫面的 `submitSequentially(items, send)` 並補 `Vitest`（全部成功、第二張失敗保留第一張），另匯出 `goToSent(navigate, qrToken, ids)` 給設備報修沿用

## Phase 4-17: ADD Feature File - guest-web/我的需求/清單兩段與空狀態.feature

**Goal**: 清單分「進行中」「已結束」兩段、每段最新送出的在上；每列有依類型的圖示、標題、「{送出時間} 送出 · {部門}」與狀態標籤；沒有內容的段落不出現（兩個方向）；一張都沒有時的空狀態不提客服、底下仍有「狀態會自動更新。退房後這份清單會一併清除。」；另一支手機送的也在清單上。

**Shared Must Read**:
- `specs/truth/features/guest-web/我的需求/清單兩段與空狀態.feature` -> `功能: 我的需求清單的兩段與空狀態` 全文（5 條 Rule、6 個 Example）
- `specs/truth/features/guest-web/我的需求/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 3 句（T123、T125、T127）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 11 句（T018、T020–T021、T026、T031–T032、T035、T037–T038、T041、T045）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/我的需求/清單兩段與空狀態.feature` 那一列；`/api-plan` 的 `/guest/requests` 缺口列與 `GuestRequest` 列（八欄、`closed`）；`/data-plan` 的 `tasks` 八欄列
- `ui/ui-plan.md` -> 畫面 6「我的需求清單」全部、「狀態對照表怎麼落到三個畫面」的「清單列」欄、「版面尺寸與伸縮規則」的清單列、C-05 的處理結果
- `ui/requests.html`（含 `?seed=new`）、`ui/guest.css`、`ui/guest.js` 的清單段 -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/pages/RequestsPage.tsx`、`lib/stage.ts` 的 `splitSections`（T007 已有）

**Boundary**:
- 開工前要先綠：Phase 4-16（`備品補充/送出鈕與成功頁.feature`）。第一個 Example 從首頁按「全部 3 項」（4-12）；最後一個 Example 要另一支手機在備品補充送出並看到成功頁（4-16）。
- 段落、順序全部從 `splitSections` 拿；圖示依 `type` 四種、「已結束」段用中性灰底；空狀態的內文照 C-05。
- 本檔有 1 個 `# [need clarification]`，照 feature 目前寫的值實作，不停下來問。

**Test Scope**:
- `specs/truth/features/guest-web/我的需求/清單兩段與空狀態.feature`

- [X] T233 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T234 [BDD-REFACTOR] 在綠燈下讓兩段的標題、列與空狀態由 `splitSections` 的結果查表畫出，`RequestsPage` 裡沒有依段落寫的 if 分支

## Phase 4-18: ADD Feature File - guest-web/備品補充/十分鐘額度估算.feature

**Goal**: 選的項目數超過 10 分鐘內的剩餘額度時一張都不送並提示「10 分鐘內最多送出 3 項／這次還能送出 {k} 項，請先減少 {n−k} 項，或稍後再送。急事請撥 9。」；一項都不能送時的內文；已取消的也算；最早那筆超過 10 分鐘就可以送；調整數量後提示消失。

**Shared Must Read**:
- `specs/truth/features/guest-web/備品補充/十分鐘額度估算.feature` -> `功能: 備品補充的十分鐘額度估算` 全文（5 條 Rule、5 個 Example）
- `specs/truth/features/guest-web/備品補充/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 2 句（T094、T099）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 10 句（T017–T018、T025、T031、T038–T039、T041、T052–T054）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/備品補充/十分鐘額度估算.feature` 那一列；`/api-plan` 的 `/guest-requests` NOOP 列（同一房 10 分鐘內第 4 筆回 429，已取消的也算）
- `ui/ui-plan.md` -> 畫面 3 狀態表「估算額度不夠」「估算額度是 0」「調整任何一項的數量」三列、「設計變數」的 `brand-accent-container`（暖色提示框）
- `ui/supply.html`（主要路徑第 4 條） -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/lib/quota.ts`（T008 的殼，在這裡實作並補 `Vitest`）、`components/InlineNotice.tsx`、`pages/SupplyPage.tsx`

**Boundary**:
- 開工前要先綠：Phase 4-16（`備品補充/送出鈕與成功頁.feature`）。同樣改 `SupplyPage.tsx`，用到 4-16 的送出流程。
- 估算用畫面上這次住宿的清單（含已取消）與瀏覽器的現在；提示框是不會自己消失的暖色框、不擋頁底按鈕；任何一項數量一變就消失。
- `quota.ts` 的 `Vitest` 驗：空清單剩 3；10 分鐘內兩張剩 1；已取消也算；送出時間在 10 分鐘之前的那一張不算（以 feature「最早那筆已經超過 10 分鐘」的時刻為準）。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/備品補充/十分鐘額度估算.feature`

- [X] T235 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T236 [BDD-REFACTOR] 在綠燈下把提示框的內文選擇（還能送 k 項／一項都不能送／已送出 m 項）收成 `lib/quota.ts` 的純函式 `quotaNotice(...)` 回語系鍵與參數，4-19 與 4-21 共用

## Phase 4-19: ADD Feature File - guest-web/備品補充/後端在送到一半拒絕.feature

**Goal**: 估算通過但第二張被後端限流時停止、已建立的保留、不出現成功頁；被擋的畫面（已建立的歸零、沒送的維持、提示框「已送出 {m} 項，其餘的請稍後再送出，或用房內電話撥 9 找櫃檯。」）；一張都沒建立就被拒時的內文；送到一半斷線的畫面與恢復後清單上只有已建立的那張；按送出時完全連不上的紅色提示與數量保留。

**Shared Must Read**:
- `specs/truth/features/guest-web/備品補充/後端在送到一半拒絕.feature` -> `功能: 備品補充後端在送到一半拒絕` 全文（5 條 Rule、6 個 Example）
- `specs/truth/features/guest-web/備品補充/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 3 句（T095、T097、T103）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 14 句（T017–T018、T025、T028–T029、T031–T032、T038–T039、T041、T045、T052–T054）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/備品補充/後端在送到一半拒絕.feature` 那一列；`/api-plan` 的 `/guest-requests` NOOP 列（限流是整間房算的）、`IdempotencyKey` 列（4xx 也會被回放，被限流後要換新鍵）
- `ui/ui-plan.md` -> 畫面 3 狀態表「估算通過、後端在送到一半拒絕」「送出時連不上」「送到一半連不上」三列、C-04 的處理結果、「驗證與錯誤回饋」的 `429` 與寫入連不上兩列、「與 `spec.md` 對不上的地方」第 3 點（先送哪一張照清單順序）
- `ui/supply.html?fail=midway`、`?fail=net` -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/pages/SupplyPage.tsx`、`components/InlineNotice.tsx`、`lib/submit.ts`

**Boundary**:
- 開工前要先綠：Phase 4-18（`備品補充/十分鐘額度估算.feature`）。同樣改 `SupplyPage.tsx`，用到 4-18 的 `quotaNotice`。
- `429` 與 `NetworkError` 都停在這一頁：已建立的那幾項歸零、其餘維持，頁底按鈕跟著剩下的項目變字；`429` 用暖色框、連不上用紅色框「沒有送出」／「已送出 {m} 項，其餘的沒有送出」。再送時每張換新鍵。
- 這裡的 `429` 是 Phase 3 的限流替身依整間房的張數回的（櫃檯代開的單也算），斷線是攔截做的；產品只認 HTTP 結果，不知道是替身。
- 本檔有 3 個 `# [need clarification]`，照 feature 目前寫的值實作，不停下來問。

**Test Scope**:
- `specs/truth/features/guest-web/備品補充/後端在送到一半拒絕.feature`

- [X] T237 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T238 [BDD-REFACTOR] 在綠燈下讓送出結果收成一個 discriminated union（`all-sent`／`stopped-by-limit {sent}`／`stopped-by-network {sent}`），畫面依它查表，不留 try/catch 裡的分支

## Phase 4-20: ADD Feature File - guest-web/設備報修/組成文字送出.feature

**Goal**: 送出的是自由文字、帶介面語言、不帶服務項目；只有類別、類別加補充說明（全形冒號）、「其他」只有補充說明三種格式；一項的成功頁；分類規則沒命中停在待派單、「查看進度」進那一筆看到「已送出」「櫃檯」；命中分給工務時進度頁「已受理」「工務收到」；日本住客用日文補充說明時類別仍是中文。

**Shared Must Read**:
- `specs/truth/features/guest-web/設備報修/組成文字送出.feature` -> `功能: 設備報修組成文字送出` 全文（8 條 Rule、9 個 Example）
- `specs/truth/features/guest-web/設備報修/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 6 句（T108–T109、T111–T112、T114、T122）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 7 句（T027、T031、T035、T041–T042、T044、T052）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/設備報修/組成文字送出.feature` 那一列；`/api-plan` 的 `/guest-requests` NOOP 列（`text` 先過分類規則）；`/data-plan` 的 `tenant_config` 列（`classification_rules`）
- `ui/ui-plan.md` -> 畫面 4 的最後一段（送出的文字、`lang`）、「用到的後端資料」的需求圖示段（待派單的打字需求圖示是對話泡泡）、「待決事項」第 3、4 點
- `ui/repair.html`（含 `?route=engineering`）、`ui/sent.html?ids=`（一個） -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/pages/RepairPage.tsx`、`lib/repair-text.ts`（4-06 已實作）、`lib/submit.ts` 的 `goToSent`

**Boundary**:
- 開工前要先綠：Phase 4-16（`備品補充/送出鈕與成功頁.feature`）。用 4-16 的 `goToSent` 與成功頁。只改 `RepairPage.tsx`，可以和 4-17～4-19 平行。
- `POST /guest/requests` 帶 `{ text, lang }` 與冪等鍵，不帶 `itemId`；`lang` 是介面語言的語系代碼（`zh-TW`、`en`、`ja`、`vi`）。成功頁是一項的版本，「查看進度」進那一筆。部門與狀態照後端回的顯示，不自己猜。
- 本檔有 1 個 `# [need clarification]`，照 feature 目前寫的值實作，不停下來問。

**Test Scope**:
- `specs/truth/features/guest-web/設備報修/組成文字送出.feature`

- [X] T239 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T240 [BDD-REFACTOR] 在綠燈下確認設備報修沒有自己的導頁邏輯，送出成功只呼叫 `goToSent`

## Phase 4-21: ADD Feature File - guest-web/設備報修/報修的限流.feature

**Goal**: 10 分鐘內已送 3 筆時按「送出報修」一張都不送並提示「10 分鐘內最多送出 3 項／請稍後再送出，或用房內電話撥 9 找櫃檯。」；住客估算還能送但後端拒絕時同一個提示、不出現成功頁；按送出時連不上顯示紅色提示且類別與字都保留。

**Shared Must Read**:
- `specs/truth/features/guest-web/設備報修/報修的限流.feature` -> `功能: 設備報修的限流` 全文（3 條 Rule、3 個 Example）
- `specs/truth/features/guest-web/設備報修/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 4 句（T111–T112、T118、T120）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 8 句（T017–T018、T028–T029、T031、T041、T053–T054）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/設備報修/報修的限流.feature` 那一列；`/api-plan` 的 `/guest-requests` NOOP 列（429）
- `ui/ui-plan.md` -> 畫面 4 狀態表「估算額度是 0」「後端拒絕（限流）」「連不上」「選了類別或改了字」四列
- `ui/repair.html?fail=midway`、`?fail=net` -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/pages/RepairPage.tsx`、`lib/quota.ts`、`components/InlineNotice.tsx`

**Boundary**:
- 開工前要先綠：Phase 4-19（`備品補充/後端在送到一半拒絕.feature`）、Phase 4-20（`設備報修/組成文字送出.feature`）。用到 4-18 的 `quota.ts`／`quotaNotice`；整理時會碰 `SupplyPage.tsx`，排在 4-19 之後。
- 送出前先估算；0 就不送並顯示暖色框；後端 `429`（限流替身）同一個框、不出現成功頁；連不上紅色框「沒有送出／請檢查網路連線後再試一次。」；選了類別或改了字提示消失。
- 本檔有 1 個 `# [need clarification]`，照 feature 目前寫的值實作，不停下來問。

**Test Scope**:
- `specs/truth/features/guest-web/設備報修/報修的限流.feature`

- [X] T241 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T242 [BDD-REFACTOR] 在綠燈下把備品補充與設備報修的「送出前估算 → 擋下提示」收成同一個 hook `useQuotaGuard(tasks)`，兩頁都改用它

## Phase 4-22: ADD Feature File - guest-web/我的需求/返回回到進來的地方.feature

**Goal**: 從首頁進度卡按「查看」進進度頁、按返回回首頁；從清單點一列進進度頁、按返回回清單；送出「毛巾」後從成功頁按「查看進度」進進度頁、按返回回清單。

**Shared Must Read**:
- `specs/truth/features/guest-web/我的需求/返回回到進來的地方.feature` -> `功能: 進度頁的返回回到進來的地方` 全文（3 條 Rule、3 個 Example）
- `specs/truth/features/guest-web/我的需求/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 1 句（T124）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 3 句（T018、T031、T041）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/我的需求/返回回到進來的地方.feature` 那一列
- `ui/ui-plan.md` -> 「前端實作切分建議」的「返回到進來的地方」（location state、沒有紀錄時的預設、捲動位置）、畫面 7 的「成功流轉」
- `ui/request.html?…&from=home|list|sent` -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/pages/RequestPage.tsx`、`components/TitleBar.tsx`（只讀返回去處的參數）、`HomePage.tsx`／`RequestsPage.tsx`／`SentPage.tsx` 進進度頁的連結、`lib/back-target.ts`（新檔）

**Boundary**:
- 開工前要先綠：Phase 4-17（`我的需求/清單兩段與空狀態.feature`）、Phase 4-20（`設備報修/組成文字送出.feature`）。要改首頁、清單、成功頁三處進進度頁的連結，排在最後一個改 `RequestsPage.tsx`（4-17）與用到 `SentPage.tsx`（4-20）的 phase 之後。
- 從哪裡進記在 React Router 的 location state（重新整理後還在）；沒有紀錄時進度頁返回清單、其他子頁返回首頁。回首頁時恢復捲動位置、進子頁捲到最上。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/我的需求/返回回到進來的地方.feature`

- [X] T243 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T244 [BDD-REFACTOR] 在綠燈下把「進來的地方 → 返回的去處」收成 `lib/back-target.ts` 的純函式 `backTargetOf(from, qrToken)` 並補 `Vitest`（三種入口與沒有紀錄）

## Phase 4-23: ADD Feature File - guest-web/取消與評分/取消需求.feature

**Goal**: 按「取消這個需求」先跳確認框且需求沒動；「返回」只關框；「取消需求」向後端撤回並浮出「已取消需求」（房務待認領與待派單各一）；取消後進度頁照對照表顯示已取消、回清單在已結束；確認框開著時同仁剛接手，按「取消需求」被拒、浮出「同仁已經接手，無法取消。請撥 9 找櫃檯。」並更新成接手後的樣子；連不上時浮出提示、畫面不變。

**Shared Must Read**:
- `specs/truth/features/guest-web/取消與評分/取消需求.feature` -> `功能: 取消需求` 全文（7 條 Rule、8 個 Example）
- `specs/truth/features/guest-web/取消與評分/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 7 句（T132、T134、T136、T139–T142）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 13 句（T018、T023、T028、T031、T038、T041–T042、T044–T049）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/取消與評分/取消需求.feature` 那一列；`/api-plan` 的 `cancelGuestRequest` NOOP 列（只有 `triage`、`pool`，其他 `409 state_conflict`）與 `/guest/requests` cancel 缺口列
- `ui/ui-plan.md` -> 畫面 7 的取消確認框一段與狀態表「按『取消需求』、還在等後端」「取消成功」「後端拒絕」「取消時連不上」「確認框開著時即時更新讓這張變成不能取消」五列、畫面 10 的提示浮層、「鍵盤與螢幕報讀」的確認框焦點
- `ui/request.html?seed=all&id=pool`（含 `&fail=claimed`、`&fail=net`） -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/components/CancelDialog.tsx`、`components/Toast.tsx`、`GuestShell.tsx`（浮層容器）、`pages/RequestPage.tsx`、`api/client.ts` 的 `cancelRequest`

**Boundary**:
- 開工前要先綠：Phase 4-22（`我的需求/返回回到進來的地方.feature`）、Phase 4-15（`進入層/重試狀態.feature`）、Phase 4-21（`設備報修/報修的限流.feature`）。「取消後按返回」用到 4-22；三條平行的線在這裡會合。
- 確認框：標題、內文「「{標題}」會從處理清單移除。」、「返回」（灰底藍字）與「取消需求」（灰底紅字）；打開時焦點在「返回」，Esc、遮罩、「返回」都只關閉。送出後撤回鈕不能按、轉圈；成功浮出「已取消需求」並重讀這張；`409` 浮出三角圖示的提示並重讀；連不上浮出「沒有送出，請檢查網路連線後再試一次。」畫面不變。提示浮層 2.6 秒、`role="status"`、不接收點擊。
- 本檔有 1 個 `# [need clarification]`，照 feature 目前寫的值實作，不停下來問。

**Test Scope**:
- `specs/truth/features/guest-web/取消與評分/取消需求.feature`

- [X] T245 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T246 [BDD-REFACTOR] 在綠燈下把提示浮層收成外框層級的 `useToast()`（同時一則、新的蓋掉舊的），評分直接沿用

## Phase 4-24: ADD Feature File - guest-web/取消與評分/評分.feature

**Goal**: 評分卡的內容與沒有留言欄；查房通過的打掃單也可以評；五個星等各自的字樣；可以改點；送出後後端記下星等、浮出「謝謝您的評分」、訊息框與進度條留著；評過的不再出現（重新整理與昨天評過各一）；`closed` 沒有評分卡；櫃檯回答過的沒有評分卡；另一支手機先評時被拒、不說謝謝、評分卡消失；連不上時浮出提示且星等留著。

**Shared Must Read**:
- `specs/truth/features/guest-web/取消與評分/評分.feature` -> `功能: 評分` 全文（9 條 Rule、11 個 Example）
- `specs/truth/features/guest-web/取消與評分/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 10 句（T133、T135、T137–T138、T141、T143–T147）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 15 句（T018、T023、T028、T031、T033–T035、T037–T040、T044、T046–T047、T050）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/取消與評分/評分.feature` 那一列；`/api-plan` 的 `/guest/requests/{id}/rating` 缺口列（1 到 5、`done`／`inspected` 且未評過、後端不擋櫃檯已回答的單）；`/data-plan` 的 `tasks` 列（`rating` 每張只能評一次）
- `ui/ui-plan.md` -> 畫面 7 的評分卡一段與狀態表「按『送出評分』、還在等後端」「評分成功」「後端拒絕評分」「評分時連不上」「評分卡上選了星等時即時更新讓這張不能評了」五列、「鍵盤與螢幕報讀」（星星是單選群組，名稱「{n} 顆星」）、「設計變數」的 `rating-star`
- `ui/request.html?seed=all&id=done`（含 `&fail=rated`、`&fail=net`）、`id=inspected`、`id=closed`、`id=answer` -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/components/RatingCard.tsx`、`pages/RequestPage.tsx`、`api/client.ts` 的 `rateRequest`、`lib/stage.ts` 的 `canRate`（T007 已有）

**Boundary**:
- 開工前要先綠：Phase 4-23（`取消與評分/取消需求.feature`）。同樣改 `RequestPage.tsx`，用到 4-23 的 `useToast`。
- 評分卡只在 `canRate(task)` 時出現；「送出評分」未選星等時不能按，送出中不能按、星星不能改；成功浮出「謝謝您的評分」、評分卡消失、訊息框與四步留著；`409` 不說謝謝、重讀、評分卡依新狀態消失；連不上浮出提示、星等留著。沒有留言欄。
- 本檔有 1 個 `# [need clarification]`，照 feature 目前寫的值實作，不停下來問。

**Test Scope**:
- `specs/truth/features/guest-web/取消與評分/評分.feature`

- [X] T247 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T248 [BDD-REFACTOR] 在綠燈下把取消與評分兩個寫入的「送出中鎖住 → 成功重讀 → 409 重讀 → 連不上提示」收成同一個 `useGuestMutation` 包裝，差別只剩成功與被拒時要不要提示

## Phase 4-25: ADD Feature File - guest-web/即時更新/同仁一動畫面跟上.feature

**Goal**: 停在進度頁時同仁認領、開始、完成各在 5 秒內跟上（標籤、副文、底部、訊息框、評分卡）；停在清單時完成的移到已結束；停在首頁時進度卡亮到處理中、換成下一張、換成「目前沒有進行中的需求」；櫃檯當場回答；櫃檯取消；另一支手機送出與取消；後端要求重新同步時重讀首頁資料與需求清單。住客這段期間沒有按任何東西。

**Shared Must Read**:
- `specs/truth/features/guest-web/即時更新/同仁一動畫面跟上.feature` -> `功能: 同仁一動，住客的畫面跟上` 全文（7 條 Rule、12 個 Example）
- `specs/truth/features/guest-web/即時更新/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 18 句（T152–T156、T158–T170）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 8 句（T018、T022–T023、T026、T037–T038、T045、T047）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/即時更新/同仁一動畫面跟上.feature` 那一列；`/api-plan` 的 `/guest/stream` 缺口列（`init`、`resync{refetch: /guest/home}`、`task` 不帶卡片、只收自己訂房）
- `specs/truth/techstack.md` -> `前端` 的「住客掃碼頁即時更新」「住客掃碼頁後端資料」兩列
- `ui/ui-plan.md` -> 畫面 9「即時更新」全部（跟著變的範圍、不被打斷的東西、不做動畫、捲動位置不變）
- `ui/index.html?live=1`、`ui/requests.html?live=1`、`ui/request.html?live=1`、`?live=answer` -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/realtime.ts`（T011 的殼）、`GuestEntry.tsx`（串流掛在這一層、換頁不重開）、`queries.ts`

**Boundary**:
- 開工前要先綠：Phase 4-24（`取消與評分/評分.feature`）。「另一支手機送出」用到 4-16，「另一支手機取消」用到 4-23，評分卡跟著出現用到 4-24。
- 進首頁後開一條 `GET /guest/stream`，掛在 `/g/:qrToken` 這一層；收到 `task` 就讓需求清單的查詢失效重讀，收到 `resync` 另讓首頁資料失效重讀；三個畫面只訂閱查詢結果。換資料時不做動畫、捲動位置不變；打開的對話框、語言選單、已選的星等、備品的數量、報修的類別與字不被打斷。5 秒內跟上以真實時間量；異常時的定期重讀是 4-26。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/即時更新/同仁一動畫面跟上.feature`

- [X] T249 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T250 [BDD-REFACTOR] 在綠燈下把「收到哪種事件要重讀什麼」收成一張表（`task` → 需求清單；`resync` → 首頁資料與需求清單）並補 `Vitest`，串流客戶端本身不知道查詢的鍵

## Phase 4-26: ADD Feature File - guest-web/即時更新/連線異常時定期重讀.feature

**Goal**: 即時連線中斷時畫面上沒有任何提示；中斷期間、超過 35 秒沒有訊號、後端宣告延遲三種異常都仍在 5 秒內跟上且沒有提示；連線恢復後回到即時、不再定時重讀；連線正常時也不定時重讀。

**Shared Must Read**:
- `specs/truth/features/guest-web/即時更新/連線異常時定期重讀.feature` -> `功能: 連線異常時定期重讀` 全文（6 條 Rule、6 個 Example）
- `specs/truth/features/guest-web/即時更新/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 11 句（T148–T152、T157、T159–T160、T170–T172）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 3 句（T018、T023、T042）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/即時更新/連線異常時定期重讀.feature` 那一列；`/api-plan` 的 `/guest/stream` 缺口列（心跳不帶 `degraded`、沒有 `recovered`、降級期間新連上的不補送）
- `specs/truth/techstack.md` -> `前端` 的「住客掃碼頁即時更新」列（異常三種、恢復只有「重新連上並收到 `init`」）
- `ui/ui-plan.md` -> 畫面 9 的「失敗回饋」、「驗證與錯誤回饋」的「讀取時連不上、畫面上已經有資料」列
- `ui/request.html?live=1`（連線正常時的跟上；異常時畫面不變，`ui/ui-plan.md`「靜態雛形輸出規劃」最後一段列為雛形沒有做出來的狀態） -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/realtime.ts`、`app/web/src/features/realtime/live-connection.ts` 的 `transition`（共用，不改語意）、`api/query-client.ts` 的 `REREAD_INTERVAL_MS`（共用的 4 秒）

**Boundary**:
- 開工前要先綠：Phase 4-25（`即時更新/同仁一動畫面跟上.feature`）。
- 異常三種：串流斷線、超過 `SILENCE_LIMIT_MS`（35 秒）沒有任何資料、收到 `degraded` 事件 → 需求清單的查詢改成每 4 秒重讀；串流重新連上並收到 `init` 才停。畫面上不顯示任何提醒列、提示浮層或錯誤字；重讀失敗時留著舊資料。住客頁不自己切斷重連來假裝恢復。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/即時更新/連線異常時定期重讀.feature`

- [X] T251 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T252 [BDD-REFACTOR] 在綠燈下確認住客的連線狀態完全由共用的 `transition` 狀態機推得（住客只多一條「沒有 `recovered`」的規則），補 `Vitest` 驗 `degraded` → 斷線 → 重連收到 `init` 這條路

## Phase 4-27: ADD Feature File - guest-web/共用外框/子頁標題列.feature

**Goal**: 備品補充、設備報修、我的需求、資訊頁四個子頁用同一種標題列（返回鈕、頁標題、「房號 311」）且沒有飯店名稱；需求進度頁也是；送出成功頁與無法使用頁沒有標題列；三個子頁的返回回首頁。

**Shared Must Read**:
- `specs/truth/features/guest-web/共用外框/子頁標題列.feature` -> `功能: 子頁標題列` 全文（3 條 Rule、5 個 Example）
- `specs/truth/features/guest-web/共用外框/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 1 句（T194）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 12 句（T018–T019、T021–T022、T025、T030–T033、T038、T041、T043）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/共用外框/子頁標題列.feature` 那一列
- `specs/truth/techstack.md` -> `前端` 的「住客掃碼頁外框與版面」列
- `ui/ui-plan.md` -> 畫面 10 的子頁標題列一段、「版面尺寸與伸縮規則」的子頁標題列、「與原型不同的地方」子頁標題列那一列
- `ui/supply.html`、`ui/repair.html`、`ui/requests.html`、`ui/request.html`、`ui/guide.html`（有標題列）、`ui/sent.html`、`ui/unavailable.html`（沒有標題列） -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/components/TitleBar.tsx`、`GuestShell.tsx`、七個頁面容器

**Boundary**:
- 開工前要先綠：Phase 4-26（`即時更新/連線異常時定期重讀.feature`）。斷言七個頁面與返回去處，要等它們全部做好。
- 標題列黏在頂端、返回鈕名稱「返回」、第二行「房號 {房號}」、沒有其他文字；內容捲到標題列底下才出現細線。成功頁與無法使用頁是置中提示頁、沒有標題列。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/共用外框/子頁標題列.feature`

- [X] T253 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T254 [BDD-REFACTOR] 在綠燈下讓每個子頁只宣告「頁標題鍵」與「返回去處」，標題列的畫法只在 `TitleBar` 一處

## Phase 4-28: ADD Feature File - guest-web/共用外框/介面語言.feature

**Goal**: 八種瀏覽器語言第一次打開的對應與語言膠囊的短寫法；語言選單四個選項各自的寫法、打勾、沒有韓文；選了就換並記在這支手機；切換只改介面文字（首頁、備品項目、清單與櫃檯回覆三例維持原文）；關掉再開還是記住的語言；另一支手機不受影響。

**Shared Must Read**:
- `specs/truth/features/guest-web/共用外框/介面語言.feature` -> `功能: 介面語言` 全文（6 條 Rule、8 個 Example）
- `specs/truth/features/guest-web/共用外框/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 10 句（T183–T186、T188–T193）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 9 句（T018、T021、T027、T030–T033、T035–T036）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/共用外框/介面語言.feature` 那一列；`/data-plan` 的 `reservations_cache.lang` 列（介面語言不讀它）
- `specs/truth/techstack.md` -> `前端` 的「住客掃碼頁多語系」列
- `ui/ui-plan.md` -> 畫面 2 狀態表「語言選單打開」列與短寫法、畫面 10 的語言一段、「互動與假資料原則」的內容原則、「找不到出處的文字」最後兩段（哪些句子要另外寫四語）、「版面尺寸與伸縮規則」的問候（最長一行超過 10em 時改 28/34）與語言選單
- `ui/index.html`（語言膠囊與選單）、`ui/guest.js` 的語言字典 -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/components/LanguageMenu.tsx`、`use-guest-language.ts`（T012）、`GuestShell.tsx`、四份語系檔的 `guest` 子樹

**Boundary**:
- 開工前要先綠：Phase 4-27（`共用外框/子頁標題列.feature`）。同樣改 `GuestShell.tsx`；「切換只改介面文字」要看到備品項目與櫃檯回覆，四語的字串要在這裡收齊。
- 第一次依瀏覽器主語言的規則已在 T012 做完；這裡做語言選單：小標「語言」、「繁體中文」「English」「日本語」「Tiếng Việt」，目前的加粗打勾；點選單外或 Esc 收起；選了就呼叫 `setLanguage`。後端給的字（項目名稱、需求標題、櫃檯回覆、資訊頁）原樣顯示。
- 收尾時 `guest` 子樹英、日、越三份不得再有任何繁體中文占位；`implement-notes.md`「待翻譯」清單全部補齊，「找不到出處的文字」那幾句的四語版本也列進 `implement-notes.md` 請使用者確認。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/共用外框/介面語言.feature`

- [X] T255 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T256 [BDD-REFACTOR] 在綠燈下加一條 `Vitest` 檢查 `guest` 子樹裡英、日、越三份沒有任何值與繁體中文那份相同（白名單只放「Wi‑Fi」這類本來就同字的鍵），把占位譯文擋在建置外

## Phase 4-29: ADD Feature File - guest-web/共用外框/手機版面.feature

**Goal**: 393 寬下首頁、備品補充、設備報修、我的需求、需求進度、資訊頁、送出成功七種頁面都沒有橫向捲動、單欄置中、可點的東西至少 44 點；長標題在清單上換行或以「…」截斷不蓋到狀態標籤、在進度頁完整換行；備品補充與設備報修捲到底時送出鈕仍釘在頁底。

**Shared Must Read**:
- `specs/truth/features/guest-web/共用外框/手機版面.feature` -> `功能: 手機版面` 全文（3 條 Rule、9 個 Example）
- `specs/truth/features/guest-web/共用外框/dsl.md` -> 模組說明；本檔用到、Phase 3 已對齊的 6 句（T187、T195–T199）
- `specs/truth/features/guest-web/dsl.md` -> 「共通實作約定」「需求的狀況對照」；本檔用到、Phase 3 已對齊的 10 句（T018、T021–T022、T024–T025、T030–T034）
- `truth-delta.md` -> `/dsl-refine` ADD `gw/共用外框/手機版面.feature` 那一列
- `specs/truth/techstack.md` -> `前端` 的「住客掃碼頁外框與版面」列、`測試與驗證` 的「住客掃碼頁驗收的視窗與輸入」列
- `ui/ui-plan.md` -> 「版面尺寸與伸縮規則」整節、「審美原則」的 44px、「與原型不同的地方」最後幾列（40px 的元件補到 44px）
- `ui/index.html`、`ui/supply.html`、`ui/repair.html`、`ui/requests.html`、`ui/request.html`、`ui/guide.html`、`ui/sent.html`、`ui/guest.css` -> 對應的雛形（版面、文字位置、顏色、互動）
- 產品碼落點：`app/web/src/guest/GuestShell.tsx`、`components/BottomBar.tsx`、`components/RequestRow.tsx`、`RequestPage.tsx` 的第一張卡、`app/web/src/index.css` 的住客變數

**Boundary**:
- 開工前要先綠：Phase 4-28（`共用外框/介面語言.feature`）。斷言全部頁面的最終版面，排最後。
- 內容最寬 480 置中、左右 16px 加安全區域；高度用 `dvh`；頁底操作列貼底、半透明加模糊；清單列標題最多兩行以「…」截斷、標籤不縮；進度頁標題完整換行。可點的一律至少 44 × 44（房號膠囊不是可點的，維持 40）。
- 本檔沒有 `# [need clarification]`。

**Test Scope**:
- `specs/truth/features/guest-web/共用外框/手機版面.feature`

- [X] T257 [BDD-GREEN] 讓 Test Scope 全綠
- [X] T258 [BDD-REFACTOR] 在綠燈下把尺寸相關的數字（44、54、480、16）收成設計變數，不留散落的固定寬度

## Phase 4-G: ui quality gate

**Goal**: 產品對齊本輪回追到的 plan-side 雛形家族。`Test Scope` 全綠不能代替本題。

**Read**:
- `ui/ui-plan.md` -> 視覺方向、設計變數、版面尺寸與伸縮規則、深色模式、狀態對照表怎麼落到三個畫面、畫面與流程 1～10、互動與假資料原則、狀態與資訊揭露、驗證與錯誤回饋、靜態雛形輸出規劃的主要路徑與切換參數、前端實作切分建議、待釐清的處理結果、找不到出處的文字、與原型不同的地方、待決事項
- `ui/index.html`、`ui/unavailable.html`、`ui/supply.html`、`ui/repair.html`、`ui/sent.html`、`ui/requests.html`、`ui/request.html`、`ui/guide.html`、`ui/guest.css`、`ui/guest.js`
- `app/web/src/guest/`（全部）、`app/web/src/i18n/locales/*.ts` 的 `guest` 子樹、`app/web/src/index.css` 的住客變數

**Boundary**:
- 不改 truth、不加 feature、不重跑 GREEN 當通過條件。
- 在 393 × 852 的手機模擬下走一遍雛形「主要路徑」四條與「切換參數」表的每一種狀態，逐頁與雛形並排比對。重點：進入層三態；首頁九個區塊的順序、兩張主視覺卡與服務列的插圖與色、「即將開放」標籤、三種「我的需求」；備品補充的加減鈕、頁底鈕的三種字、暖色與紅色提示框；設備報修的膠囊選中態、字數、必填標籤；送出成功頁一項與多項；清單兩段、圖示方塊兩種底色、五種標籤色；進度頁十個狀態值各一張（第一張卡、訊息框四種色、四步三種樣子、評分卡、底部三種）、確認框、三種提示浮層；資訊頁；語言膠囊與選單。
- 雛形沒有做出來的狀態（`ui/ui-plan.md`「靜態雛形輸出規劃」最後一段）以 `ui/ui-plan.md` 的文字為準；雛形的示範資料與 truth 預設不同之處以 truth 為準。深色只確認變數留好、不啟用。
- 用語以 truth 為準；畫面上不得出現內部值、錯誤碼、飯店內部用語，也不得出現住客姓名、飯店名稱、日期、同仁姓名、各步時間、取消原因。
- 另在真的 iPhone 與 Android 各開一次 `/g/{代碼}`，看安全區域、瀏覽器工具列收放與 `dvh`（Scope Note 第 13 條），結果記在 `implement-notes.md`；看不到真機時記下「未做」與原因，不擋本題。

- [X] T259 subagent review (ui quality gate)

## Phase 5: 全套回歸

**Goal**: 住客的 29 份 feature 在 `phone` 整輪跑一次，確認各 phase 之間沒有互相弄紅；本輪動到的共用程式（`router.tsx`、`vite.config.ts`、串流連線函式、`api/errors.ts`、四份語系檔、`Network.java`、`run-feature.sh`、`BackendChannel`）與只對住客案例生效的 `GuestHooks` 沒有弄壞櫃檯 Web。本 phase 不用 `[REGRESSION]` 標記（Scope Note 第 18 條）。

**Boundary**:
- 只跑、只修回歸；不加 feature、不改 truth。紅了回到對應 phase 修，修完重跑整輪。
- 員工 App 測試線（`app/bdd/`）本輪只多了 `BackendChannel` 的方法、沒有改既有方法；若 T015 動到了既有方法的簽章或 SQL，另跑 `specs/truth/features/ios-app/待認領/` 一輪。

**Test Scope**:
- 住客：`specs/truth/features/guest-web/` 全部 29 份，`phone` 一輪（`bash app/web-bdd/run-feature.sh guest-web/`）。
- 前端：`cd app/web && npm run typecheck && npx vitest run && npm run build`（含語系鍵集合一致性、譯文占位檢查、住客 chunk 仍獨立）。
- 櫃檯 Web：`specs/truth/features/frontdesk-web/` 全部 29 份，`desktop` 一輪（`bash app/web-bdd/run-feature.sh frontdesk-web/ desktop`）；`共用外框/支援的視窗與輸入方式.feature` 另跑 `ipad`。

- [X] T260 跑住客 29 份 feature 的 `phone` 一輪，確認全綠
- [X] T261 跑前端的 `typecheck`、`Vitest`、建置，確認全綠且住客 chunk 獨立
- [X] T262 跑櫃檯 Web 29 份 feature 的 `desktop` 一輪與 1 份 `ipad`，確認本輪的共用改動與住客的預設資料沒有弄壞櫃檯
