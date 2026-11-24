# 實作筆記：住客掃碼頁（主流程）

實作期間量到的數字、spike 結論與要回交上游的現象。

## 延遲載入的住客路由在手機設定下來得及（T004）

- 做法：`app/web-bdd/src/test/java/com/athena/web/bdd/GuestLazyRouteSpikeTest.java`，`phone` 設定（WebKit 393 × 852 觸控），每一次都開全新的瀏覽器狀態打開 `/g/abc`，從導航開始量到「guest」占位字樣出現，共五次。
- 環境：本機開發伺服器（Vite，`http://localhost:5199`），後端 8080 開著但這一項不打後端。
- 結果：第一次 602 ms（含開發伺服器第一次轉譯住客的程式），之後 202–237 ms。最慢 602 ms，門檻 5000 ms。
- 結論：通過，住客路由維持延遲載入，不用退路。
- 正式建置另外看了下載量：`npm run build` 多出一個獨立的住客 chunk（`routes-*.js`，0.12 kB），主 chunk 從 665.30 kB 變成 667.00 kB。多出的 1.7 kB 是 Vite 為任何延遲載入都會放進主 chunk 的預先載入小程式（`modulepreload`、`vite:preloadError`），住客的程式本身不在主 chunk。

- 和 Phase 4-13 實跑並列（T226）：`進入層/掃碼就進首頁.feature` 在 `phone` 下 4 個場景全綠，單一場景從開始到結束（含清空業務資料、種資料、開分頁）是 4.73 s（第一個場景，含瀏覽器環境啟動）、1.59 s（首頁重新整理）、1.87 s（關掉首頁再從歷史記錄打開）、2.32 s（關掉「毛巾」的進度頁再從歷史記錄打開，新分頁沒有 session，要先換一次再停在原本的進度頁）。T004 只量延遲載入一段：602 ms（第一次）、202–237 ms。兩者都遠低於 5000 ms 的門檻，住客路由維持延遲載入。T004 的 spike 測試 `GuestLazyRouteSpikeTest.java` 已刪除，`app/web-bdd/pom.xml` 裡排除它的那一行一起拿掉。

## 住客測試線的 smoke-test（T005）

- `npm run typecheck`、`npx vitest run`（41 檔 569 項）綠；`npm run build` 有獨立的住客 chunk。
- `run-feature.sh --keep guest-web/進入層/無法使用頁.feature`：以 `phone` 跑到第 6 步，4 個場景都是 `UndefinedStepException`，是預期。
- 第一次跑住客的 feature 時，場景還沒開始就因 `StepPhaseListener` 收集步驟 pattern 遇到 `null`（沒有步驟定義的句子沒有 pattern）丟出 `NullPointerException`，看不到哪幾句沒有定義。已在 `app/web-bdd/src/test/java/com/athena/web/bdd/support/StepPhaseListener.java` 濾掉 `null`，櫃檯的場景每句都有定義，行為不變。
- 經開發伺服器代理打 `POST /guest/scan`（代碼 `abc`）回 `409 room_vacant`。
- `run-feature.sh 待派單/待派單清單.feature desktop` 16 個場景全綠。

## 串流連線函式抽成共用之後的櫃檯回歸（T011）

- `即時更新/連線異常提醒列.feature` 9 個場景綠。
- `即時更新/現場變化即時跟上.feature` 第一次跑 8 次有 3 次在「林佩君停在待派單頁時 306 的住客收回需求」逾時失敗。失敗截圖上 306 已經消失、徽章是 1，畫面是對的；卡住的是測試讀清單的方式：`TriagePage.roomsInOrder()` 先數列數再逐列讀，即時更新在兩次讀取之間拿掉一列，讀第 2 列就等到 30 秒逾時。這個讀法在改動前就存在，同一個場景單獨跑改前改後各 3 次都綠；改前整份跑 4 次沒有碰到，但機率性的失敗無法排除只是沒輪到。
- 處理：`app/web-bdd/src/test/java/com/athena/web/bdd/pages/TriagePage.java` 的 `roomsInOrder()` 改成一次讀整頁的無障礙樹摘要再找出每一組「{房號} 派單」，不再逐列讀。之後整份連跑 4 次全綠，`待派單/` 整個模組 47 個場景綠。

## 住客串流、冪等鍵回放與 T015 helper（T016）

- 做法：`app/web-bdd/src/test/java/com/athena/web/bdd/GuestBackendSpikeTest.java`，pom 照 `SmokeTest` 排除在平常的 `mvn test` 之外。對 8080 實跑，開頭 `resetBusinessData()` → `ensureFrontdeskDefaults()` → `ensureGuestDefaults()`，四項在同一個測試方法裡依序跑；第 ③ 項不回放時先記下結果、跑完第 ④ 項才判失敗。
- 環境：後端 8080（`run-feature.sh --keep` 起的）、開發伺服器 `http://localhost:5199`，第 ④ 項用 `phone` 設定（WebKit 393 × 852 觸控）。共跑 3 次；後端每間房 10 分鐘只收 3 筆住客需求、計數在記憶體裡，每跑一次真的送出 1 筆，所以 10 分鐘內最多連跑 3 次。
- ① 掃碼與換 QR：`roomQrToken("311")` 換得到 session，需求選單 5 項（出廠選單）。`rotateRoomQr("311")` 之後舊代碼 `guestScan` 回 `409 room_vacant`、新代碼回 `200`。`seedGuestRequest` 種 3 張房務待認領，用新 session 讀 `GET /guest/requests` 都列得到、`state` 是 `pool`、`dept` 是 `housekeeping`。住客清單的回應沒有 `source` 欄位（欄位是 `id`、`type`、`state`、`dept`、`title`、`answer`、`rating`、`createdAt`），來源改到資料表 `tasks.source` 看，3 張都是 `guest`。
- ② 串流：Java `HttpClient` 讀 `GET /guest/stream` 帶 `X-Guest-Session` 回 `200`，依序收到 `init {"stream_pos":0}`、`resync {"reason":"connected","refetch":"/guest/home"}`。阿美以 `postTaskAction(…, "claim", …)` 逐張認領 3 張，每張都收到自己的 `task`（`type` 是 `claimed`，帶 `stream_pos`、`task_id`、`seq`、`occurred_at`，不帶卡片）。從送出認領到收到 `task`，後兩次跑 9 個樣本是 23–62 ms，到住客重讀 `GET /guest/requests` 完成是 31–82 ms，門檻 5000 ms。第一次跑第一張量到 462 ms，原因是 `postTaskAction` 第一次替阿美取通行憑證要登入、算密碼雜湊，這段不屬於即時更新；之後先取好憑證再計時。量的是後端直連、沒有經過開發伺服器代理與瀏覽器。
- ② 心跳：連上後 16 秒內每次都收到一則 `heartbeat {"stream_pos":3}`，沒有 `degraded` 欄位。三次收到的時間是連上後 13390、5313、11673 ms：心跳是後端對全部連線共用一個 15 秒的計時（`StreamHeartbeat.java:15` 的 `@Scheduled(fixedDelay)` 呼叫 `StreamConnectionRegistry.java:114` 廣播），不從這條連線連上時起算，所以 16 秒內可能碰到兩則。前端「35 秒沒資料算斷線」的判斷因此要以兩則心跳最長間隔 15 秒加網路延遲來算，不能假設連上後第 15 秒一定有一則。
- ③ 冪等鍵回放：同一把 `Idempotency-Key` 連送兩次 `POST /guest/requests`（打字的需求），兩次都是 `201`、回應內容一字不差（同一個 `id`），第二次帶 `Idempotency-Replayed: true`；清單從 3 張變 4 張，只多一張。3 次都一樣。住客寫入的回放成立，不用退路。
- ④ 畫面：`phone` 設定打開 `/g/{新代碼}`，畫面經代理打一次 `POST /guest/scan` 回 `200`，等到首頁的 `main` 掛上、載入中的 `role="status"` 消失，花 1030、471、502 ms（第一次含開發伺服器轉譯），門檻 5000 ms。
- T015 helper：`ensureGuestDefaults`、`roomQrToken`、`rotateRoomQr`、`guestScan`（含 `GuestScanRejected` 的狀態碼與代碼）、`seedGuestRequest` 實跑都照預期，沒有發現錯誤，`app/bdd-support` 沒有改。`guestRateRequest`、`revokeGuestSession`、`setTaskRating`、`seedDeskTask`、`setClassificationRule` 這一輪沒有跑到。
- 結論：四項都通過，住客路由、串流與冪等鍵照 research 的做法往下做，第 ③ 項不用退路（送出鈕仍照決策 4 在按下到回覆之間鎖住）。

## 介面根共用句型 T017–T054（Phase 3 G1–G3）

- **住客與櫃檯有字面相同的句子，同一個 glue 只能有一個定義。** dry-run 量到住客的步驟原本就有 11 個方法被櫃檯的定義接走：本輪範圍內 8 句（`現在時間是`、`畫面上看得到／沒有`（單句與表格）、`按鈕 … 可以按／不能按`、`畫面是 "{頁}"`），G10 範圍 3 句（`"{員工}" 在手機上{動作} "{標題}"` 接到 `CommonWhenSteps.actOnPhone`、`超過 … 秒沒有收到後端的任何訊號`、`即時連線中斷`）。在住客這邊再定義一次會讓兩邊的規格都變成 ambiguous。`dsl.md` 寫明「與櫃檯 Web 同一個 glue 套件」，所以不拆 glue，改成一句一個定義：
  - 語意兩邊相同的 7 句直接共用櫃檯的定義（`steps/common/CommonGivenSteps`、`CommonThenSteps`）。`按鈕 … 不能按` 原本不算看板讀取與員工串流，這次加上不算住客的 `GET /guest/home`、`GET /guest/requests`、`GET /guest/stream`，櫃檯行為不變。
  - `畫面是 "{頁}"` 兩邊的頁不同：`AccountThenSteps.screenIs` 在 `GuestScenario.active()` 時交給 `GuestCommonThenSteps.screenIs`。`GuestHooks` 在住客場景開始時標 `active`。
  - G10 碰到的 3 句要照同樣做法在櫃檯的定義裡分流，不能在住客這邊另寫。
  - 驗證：住客根 dry-run 0 個 ambiguous；櫃檯根 dry-run 0 個 undefined／ambiguous；`帳號與登入/登入頁.feature`（desktop）25 個場景綠。
- **page object 對產品要求的可及性結構**（Phase 4 照做；都是角色與看得到的字，沒有 `data-testid`，和原型 `ui/guest.js` 一致）：子頁標題列是頁面最外層的 `<header>`（banner），裡面是返回鈕（按鈕或連結，名稱是介面語言的「返回」）、`h1` 頁標題、介面語言的房號標籤（「房號 311」「Room 311」）；載入中標 `aria-busy="true"`；提示框是 `role="alert"`、提示浮層是 `role="status"`；進度頁第一張卡的標題是 `main` 裡第一個 `h2`，**狀態訊息框是 `role="note"`**（原型沒有角色，這是本輪唯一新加的要求）；四步進度是 `ol`，目前那一步 `aria-current="step"`，四步全亮時沒有任何一步標；清單每段是 `h2` 段落名加緊接的 `ul`／`li`，列的第一行字是標題；首頁每個區段是 `section` 包 `h2`，進度卡是名稱以「進行中的需求」開頭的連結或按鈕，主視覺卡是名稱含「備品補充」「設備報修」的連結或按鈕。各類別的 javadoc 有完整寫法。
- **`這次住宿有以下需求：` 的到手、開始時間預設。** `dsl.md` 寫「送出之後 1、2 分鐘」，但延後中、中斷、已完成、查房通過、已結案在後端停在「現在」那一刻，最後一列的送出時間又是現在，照字面算會比現在晚、後端記的先後顛倒而拒寫。這幾種狀況的兩個預設時刻改成不晚於現在；其餘照字面。14 種狀況實跑種進後端，狀態、部門、負責人、取消原因、查房結果、回覆、星等都和「需求的狀況對照」相符。
- **`櫃檯在 "{時刻}" 替 … 代開了一張單`** 照 Scope Note 第 11 條直接寫入建立時間，不撥時鐘；實跑得到 `source='desk'`、不綁訂房、房務待認領、建立時間 14:03。
- **標題列的房號與返回鈕跟著介面語言**：標題列第二行那一句的參數是整段房號標籤，feature 依介面語言寫（`資訊頁內容.feature:43` 的 English 場景是 `"Room 311"`，和產品 T012 的 `Room {{roomNo}}` 一致），stepdef 照字面比；「返回」在 `dsl.md` 跟著語言換字的清單裡。
- **給 G4–G12 的共用入口**：`support/GuestPhone`（開網址、等畫面畫完、另一支手機）、`support/GuestStay`（後端這次住宿的需求、狀況與標籤、When 之前的快照）、`support/GuestScreen`（頁名對照）、`support/GuestTerms`（跟著語言換字的標籤、段落、步名）、`pages/guest/GuestFrame`（標題列、最上層可按的東西、`press`、`settleAfter`、提示框、目前語言）、`Network.cutOffBackend`／`restoreBackend`（「暫時連不上後端」與「恢復連線」）。

## 進入層、首頁、備品補充的句型 T055–T107（Phase 3 G4–G6）

- **page object 對產品追加的可及性結構**（Phase 4 照做）：首頁問候是 `main` 裡的 `h1`，兩行（早安／午安／晚安、「今天需要什麼？」）；首頁進度卡的四個步名是卡裡的清單（`ol`／`li`，第一行是步名），目前那一步標 `aria-current="step"`，和進度頁同一種寫法；頂列語言膠囊是看得到的字剛好是短寫法的按鈕；即將開放的入口與房號膠囊不在任何按鈕或連結裡；備品補充每一列的數字是列裡唯一一行純數字，列以加減鈕的名稱「增加{名稱}」「減少{名稱}」認；提示浮層是明寫 `role="status"` 的元素（`<output>` 的數字不算）。
- **「過程中沒有…」兩句看整段過程**：`steps/guest/entry/ScreenWatch` 只在「進入層」的場景、從 When 開始在瀏覽器環境掛一段觀察畫面變動的程式，每次變動交回網址路徑、可見文字與輸入欄數，並記下分頁送出的後端請求（給「沒有帶員工的登入憑證」看標頭）。在重試狀態按「重試」時，重試狀態的字留到下一個畫面出現之前不算新出現的錯誤。
- **「住客在同一支手機上打開櫃檯 Web 的網址」**：已經有員工登入的櫃檯 Web 分頁時就在那個分頁開，否則開新分頁。櫃檯 Web 的登入狀態存在單一分頁的 `sessionStorage`，另開分頁一定是未登入，`櫃檯 Web 仍是 … 的登入狀態` 只能在那個分頁上檢查。
- **`後端送出的順序是`**：後端時鐘在場景裡是固定的，同一次送出的幾張 `created_at` 相同，照 `created_at` 排會打平；打平時以建立請求的送出先後排。「前一張回覆之後才送下一張」以瀏覽器回報的請求開始、結束事件先後判斷。
- **`這支手機短時間內多次掃碼失敗`** 照 `dsl.md` 允許的退路，用攔截讓這個分頁的 `POST /guest/scan` 回 `429`，不碰後端的 `ScanIpRateLimiter`（系統時鐘、整台共用）。
- dry-run：`進入層`、`首頁`、`備品補充` 三個模組 0 個 undefined、0 個 ambiguous。

## T200 品質閘門的修正

- **語言膠囊認不到**：`LanguageMenu.capsule()` 的名稱改用 `BrowserRegex.literal` 組；整個 `support/`、`pages/guest/`、`steps/guest/` 已經沒有交給瀏覽器的 `Pattern.quote`（`Network.java` 與櫃檯的 `AnswerPanel.java` 那兩處在 Java 端比對）。
- **返回鈕跟著介面語言**：`dsl.md` 把「返回」加進跟著語言換字的清單，`GuestTerms` 補上四種寫法。`GuestFrame.backControl` 以目前語言的「返回」找返回鈕，按鈕或連結都算（原型是連結）；標題列、沒有子頁標題列、提示浮層不擋返回鈕三處都用它。
- **清單順序的後端檢查**：後端時鐘固定時同一次送出的幾張 `created_at` 相同。後端那一半改成相鄰兩列的送出時間由上到下不變新，同一刻的不分先後；畫面那一半照字面比。`dsl.md` 那一列寫明同一刻送出的畫面上先送出的在上，產品在 Phase 4 要給同一刻的需求一個依送出先後的次序。
- **`後端這時回覆很慢` 扣的是回應，不是請求**：`GuestRequestLimitStub` 記下冪等鍵後 `route.fetch()` 送到後端，回應扣在測試這一頭，扣滿 3 秒才 `fulfill`。攔截的處理程式不能睡著等，所以放行掛在 `WebWorld.whileWaiting`：`GuestFrame.eventually`、`waitAtMost`（`press`、`settleAfter` 都經過它）與「連按兩下」的迴圈每一輪都放行到時間的回應。場景結束時 `GuestHooks` 把還扣著的全部放行。
- **否定句先等畫面畫完**：`GuestFrame.drawnProblem`（住客網址、沒有 `aria-busy`、看得到有字的標題）與 `RequestPage.drawnProblem`（進度頁網址、標題列 `h1`、第一張卡的 `h2`、沒有 `aria-busy`）。「四步進度走到 不顯示進度」另查狀態標籤以外看不到任何步名；「進度頁底部是」「進度頁沒有評分卡」「進度頁沒有狀態訊息框」「畫面上沒有子頁的標題列」都先等畫完；44 點那一句要求至少有一個可以點的東西；進入層兩句「過程中…」先確認觀察程式記到了畫面。
- **比 dsl 寬的檢查收緊**：清單列與進度頁第一張卡的圖示以 `RequestIcons` 對到後端 `type`（圖形照原型 `ui/guest.js` 的 `cube_box`、`hammer`、`sparkles`、`bubble_left`）。房號膠囊不能有圖示；即將開放的四個入口那一列右端不能有箭頭；住宿指南每一列右端要有箭頭（列裡最後一個 `svg` 在右邊三分之一）。四步的打勾、實心、空心改看畫面上的圓點，畫成實心的那一步要和 `aria-current="step"` 是同一步。評分卡的星星逐顆認圖形（`StarShape`，`path` 的 `d` 照原型的 `star_fill` 與 `star`），「五顆星都是空心的」要五顆都是空心，「只有前 N 顆星是實心的」要前 N 顆實心、其餘空心，兩句另查單選群組選中的是哪一顆。首頁進度卡的四格進度條逐格認底色（`GuestHomePage.BarLook`，亮的是 `brand-primary`、不亮的是 `fill`），要和四個步名的 `aria-current="step"` 亮到同一步。
- **page object 依賴的結構** 搬進 `ui/ui-plan.md`「鍵盤與螢幕報讀」，Phase 4 以那裡為準。`GuestLive.headings` 改用角色 `heading`。
- **重複的 helper 收成一份**：語言膠囊只留 `LanguageMenu.capsule()`，首頁頂列那一句用它再比字；房號膠囊只留 `GuestHomePage.roomCapsule`；住客 session 鍵只留 `GuestPhone.SESSION_KEY_PREFIX`；遮罩只留 `GuestFrame.scrimShown`（固定、蓋滿視窗、接得到點按，透明的也算）；提示浮層只留 `GuestFrame.toasts()`，`visibleNoticeLayers` 改用它；九份私有 `eventually` 收成 `GuestFrame.eventually`，規格寫錯的參數立刻丟出，其他讀取錯誤當成還沒好，失敗訊息附畫面文字。

## Phase 4 的測試層修正

- **掃碼失敗次數跨場景累積（4-01）**：`無法使用頁.feature` 第一次全綠，緊接著再跑一次 4 個場景全在「住客頁一直沒有畫完」，畫面停在載入中，分頁的 `POST /guest/scan` 回 `429`。原因是後端把掃碼失敗按來源 IP 記在記憶體、照系統時鐘算 10 分鐘（`tms/src/main/java/com/athena/tms/service/ScanIpRateLimiter.java:12-41`，門檻預設 10，`GuestScanService.java:56-66`），瀏覽器經開發伺服器代理都是本機，打開無法使用頁的場景一多，之後所有場景連有效的代碼都換不到 session。產品照 truth 把 `429` 當成「不是這間房不能用」，行為正確。處理：`support/GuestHooks` 在住客場景開頭替這個場景的每個瀏覽器環境加上 `X-Forwarded-For`（後端認的來源，`GuestController.java:73-78`），每個場景一個隨機的 `10.x.y.z`，失敗次數不跨場景累積。沒有改任何 stepdef；「多次掃碼失敗」那一句本來就以攔截做出 `429`，不受影響；Java 端 `guestScan` 只換有效代碼，不會累積失敗。之後同一份連跑兩次都綠，而當時本機那一格仍在回 `429`。
- **兩個只對某模組生效的掛勾從來沒有生效（4-04）**：`與櫃檯Web互不影響.feature` 第一個場景在「住客頁向後端的請求沒有帶員工的登入憑證」報「這個場景沒有從 When 開始記錄畫面」。原因是 `steps/guest/entry/ScreenWatch.java:85` 與 `steps/guest/supply/SupplyScenario.java:58` 用 `getUri().toString()` 比對含中文的目錄（`進入層/`、`備品補充/`），而 Cucumber 給的 URI 是百分比編碼的（`guest-web/%E9%80%B2…`），永遠比不到，`GuestHooks` 比的 `guest-web/` 是英文所以沒事。兩處改成比 `getUri().getPath()`（解碼後的路徑），掛勾照原本的意圖只在該模組的場景生效；句子的語意沒變。

## 狀態對照表與同一刻的先後（4-09）

- **對照表是唯一的登錄處**：`app/web/src/guest/lib/stage.ts` 的 `STAGE_TABLE` 以後端十個狀態為鍵，每一列帶標籤、色調、走到第幾步、清單段落、可以取消、鎖定說明、這個狀態能不能評分、只看狀態的訊息框、四步副文、首頁進度卡說明。`canRate`、`messageBox`、`stepView`、`homeCardLine` 只讀這一列再疊上回覆與星等，沒有任何函式或畫面再比對狀態值。修繕結案（`closed`）另成一列，只差不能評分。`stage.test.ts` 對十個狀態逐一比對整列。
- **同一刻送出的先後**：後端時鐘固定時同一次送出的幾張 `created_at` 相同、需求 id 是亂數，後端給不出先後。產品在送出成功時把 id 依送出先後記進這個分頁的 `sessionStorage`（`app/web/src/guest/sent-order.ts`，鍵 `tms.guest.sent.{代碼}`），清單與首頁進度卡排序時同一刻的照這份紀錄排；不是這支手機送的排在同一刻的最後、維持後端給的順序。
- **方向照 feature，不照 `dsl.md` 那一列的字**：`specs/truth/features/guest-web/dsl.md` 第 87 列「清單 … 由上到下是」寫「畫面上照送出的先後，先送出的在上」；但 `備品補充/送出鈕與成功頁.feature:72` 送出順序是「毛巾、礦泉水 ×2」（`:31`），清單卻要「礦泉水 ×2、毛巾」，也就是後送出的在上，和整份清單新到舊、原型 `ui/guest.js:218` 的排法一致。照 feature 做（後送出的在上），`dsl.md` 那一句回交 `/dsl-refine` 改成「後送出的在上」。
- **測試層修正**：`狀態對照表.feature` 每個場景都是先「"311" 房的住客這次住宿有以下需求：」就直接「住客打開 "我的需求清單"」或進度頁，沒有先開首頁；`GuestScenario.phoneRoom()` 因此丟出「住客還沒打開任何一間房的網址」。`GuestCommonGivenSteps.stayHasRequests` 結尾加上 `rememberPhoneRoomIfAbsent(roomNo)`：還沒開過任何房時，「住客」就是 Given 那間房的住客；已經開過時不動。句子語意不變。

- **4-11 與 4-13 一開跑就綠**：四步副文、第一張卡的「{送出時間} 送出 · {部門}」都由 4-09 的對照表與 `hhmm` 出，進入層的 session 與子頁網址在 Phase 2 與 4-01 已做好，兩題沒有紅燈可補，沒有改產品碼；4-11 的整理（T222）把進度頁拆成五個只吃一張需求的元件後再跑一次仍綠。

## 首頁我的需求區、靜靜重換、重試狀態、備品送出（4-12～4-16）

- **按卡上的字（4-12，測試層修正）**：ui-plan 要求進度卡整張是一個連結、名稱以「進行中的需求」開頭，「全部結束」那一列也是整張一個連結；`住客按 "查看"`、`住客按 "查看紀錄"` 要按的是卡裡的一行字，不是連結的名稱，`GuestFrame.pressableIn` 只比名稱，找不到。產品若在連結裡再放一個可按的元素，會變成巢狀的可按元素，報讀軟體與鍵盤都會重複。改在 `GuestFrame.pressableIn` 最後加一條退路 `labelInsidePressable`：名稱與開頭都比不到時，在名稱含這幾個字的可按元素裡找字剛好相符的那一行，點在那一行上。只在原本就找不到時才走，原本找得到的句子行為不變；只影響住客的 `GuestFrame`，櫃檯的 `ShellFrame` 沒動。
- **401 只在一處處理（4-14）**：`app/web/src/guest/api/client.ts` 的 `guestSend` 被回 401 時把被拒的那一把 session 交給 `setUnauthorizedHandler` 註冊的函式換一把新的，拿到就用新的重發一次；換不到時那個錯誤就是這次呼叫的錯誤。換的那一步在 `lib/rescan.ts` 的 `createRenewal`：同時被拒的呼叫共用同一次重換，已經換過了就直接拿手上的新的，剛換到的那一把又被拒時不再重換、交給進入層。進入層（`GuestEntry.tsx`）在畫面就緒時註冊它：先清掉這個分頁存的 session、用網址上的代碼換，成功寫回 store，失敗交給同一個 reducer（`409` 是無法使用頁、其他是重試狀態）。T008 留的 `nextRescanState` 狀態機殼沒有呼叫者，換成 `createRenewal`。
- **重試狀態（4-15）**：進入層的 reducer 變成五個狀態（載入中、就緒、無法使用、重試、重試中），`entry.test.ts` 逐條列出轉移並檢查五個狀態都走得到。「原本要去的那一頁」就是目前的網址：換 session 的過程網址不變，重試成功後直接畫當下網址那一頁，不另外記。這一題的紅燈沒有另外實跑，改前的 reducer 對限流、連不上、後端出錯都停在載入中（4-01 的 Boundary 寫明），失敗的樣子是已知的。
- **備品送出（4-16）**：`lib/submit.ts` 的 `submitSequentially` 依清單順序一張一張送、每張一把 `newIdempotencyKey()`、有一張失敗就停並交回已建立的 id；`goToSent` 給設備報修沿用。按下到回覆之間用 `useRef` 鎖住（同一個 tick 的第二下也擋得住），按鈕 `disabled` 加 `aria-busy`、字不變，加減鈕也停用。建立成功的 id 依送出先後記進 `sent-order.ts`（見 4-09）。送出前取 store 當下的 session（`session.ts` 的 `currentSessionToken`），中途被重換過時用新的那一把。送到一半失敗的提示是 4-18、4-19，這一題失敗時只解鎖、留在原頁。這一題同樣沒有另外跑紅燈：改前送出鈕沒有任何動作、成功頁是空的 `main`。
- **自己組的表格沒有轉換器（4-16，測試層修正）**：`SupplyGivenSteps.quantityTable` 用 `DataTable.create(rows)` 組表格交給共用的「已把以下項目調到」，那一句 `asMaps()` 時丟出「DataTable was created without a converter」，「剛在備品補充送出以下項目」四個場景還沒碰到產品就失敗。改成帶 `DataTableTypeRegistryTableConverter`（`Locale.ROOT`）建表格，句子語意不變。

## 清單空狀態、額度、送到一半、報修送出、返回（4-17～4-22）

- **清單（4-17）**：紅燈只有空狀態一個場景（另五個改前就綠，資料靠 4-09 的對照表）。`splitSections` 改成依「進行中、已結束」的順序回非空段落的陣列，一張都沒有時是空陣列；`RequestsPage` 以段落查標題表畫出，空陣列畫空狀態（原型 `tw-empty`：灰色清單圖示、標題、C-05 內文），沒有依段落寫的分支。`latestActive` 改成直接在新到舊的清單裡找第一張進行中的。
- **額度估算（4-18）**：`lib/quota.ts` 的 `remainingQuota` 以送出時間不早於「現在往前 10 分鐘」計，剛好 10 分鐘前送出的仍算，和後端 `RoomRequestRateLimiter.java:23-26` 只丟掉早於界線的一致。`quotaNotice` 依擋下的原因選內文（還能送 k 項／一項都不能送／已送出 m 項），`estimateQuota` 回要顯示的提示框或 `null`。提示框的每一行是語系鍵加參數（參數一律帶齊 `left`、`over`、`sent`，型別檢查才接受同一個呼叫裡的不同鍵），畫面上換語言時跟著換字。備品補充頁等首頁資料與需求清單都讀到才畫。
- **送到一半（4-19）**：`submitSequentially` 回 `all-sent`／`stopped-by-limit`／`stopped-by-network` 三種結果，三種都帶已建立的 id。`429` 是限流，連不上、`5xx` 與其他意外的拒絕都算沒送成（ui-plan「驗證與錯誤回饋」寫入連不上或 `5xx` 同一個紅色框）。`resultNotice` 查表回提示框。已建立的那幾項依清單順序歸零。
- **兩頁共用的送出（4-20、4-21）**：`use-guest-submit.ts` 管鎖住、記下送出先後、重讀清單與全部成功時換到成功頁，兩頁都不自己導頁；`use-quota-guard.ts` 管提示框（送出前估算、停下來的結果、改了選擇就收掉）。停下來時不等清單重讀：送到一半斷線那一題第一次跑紅，原因是重讀在連不上時會一直重試，提示框排在它後面一直沒出現；改成只有全部成功時才等重讀再換頁。設備報修的清單還沒讀到時估算照樣放行，由後端限流（測試裡是替身）擋。
- **返回（4-22）**：`lib/back-target.ts` 是唯一知道來處的地方：`requestLink(代碼, id, 來處)` 給首頁進度卡、清單列、一項的成功頁三處連結（網址加 location state），`cameFrom` 在進度頁讀 state（不認得的當成沒有紀錄），`backTargetOf` 查表（首頁回首頁，清單與成功頁回清單，沒有紀錄回清單）。另外在住客路由放 `ScrollRestoration`：首頁以網址記捲動位置，子頁以每次進入的 key 記，回首頁回到原處、進子頁從最上面開始。這一段沒有驗收場景，也沒有另外在畫面上實測。
- **測試層修正**：
  - `GuestCommonGivenSteps.stayHasNoRequests` 結尾加 `rememberPhoneRoomIfAbsent(roomNo)`，和 4-09 對 `stayHasRequests` 的修正相同（「還沒送過任何需求」之後直接「住客打開 "我的需求清單"」）。句子語意不變。
  - `住客按 "送出報修"` 在日本語介面（`設備報修/組成文字送出.feature` 最後一個場景）找不到按鈕，因為 `dsl.md` 跟著語言換字的清單沒有它，stepdef 照字面找。`RepairPage.pressableIn` 在介面不是繁體中文時把「送出報修」換成該語言的送出鈕名稱（照原型字典，同一檔案已有類別膠囊的對照），`GuestCommonWhenSteps.guestPresses` 經過它；其他名稱照原字。`dsl.md` 介面根「文字比對」那一條的清單要加上「送出報修」，回交 `/dsl-refine`。

## 取消（4-23）

- **確認框與提示浮層**：確認框照原型 `tw-dialog` 是遮罩加 `role="alertdialog"`，畫進外框的浮層容器；打開時焦點在「返回」，Tab 只在兩顆按鈕之間，Esc、點遮罩、「返回」都只關閉並把焦點還給「取消這個需求」。撤回成功時直接用後端回的這張蓋掉清單裡的舊資料再重讀，`409` 浮出三角圖示的「同仁已經接手…」並等重讀完，其他失敗（連不上、`5xx`）只浮出「沒有送出…」、不動資料。
- **測試層修正**：`"{房號}" 房的住客剛取消了 "{標題}"，停在它的進度頁`（`取消需求.feature:64`）那個場景前面沒有任何一句種過「毛巾」，stepdef 直接開進度頁就丟出「這個場景沒有標題是「毛巾」的需求」。`dsl.md` 這一句只寫頁面操作、預設這張需求已在；`CancelRateGivenSteps.justCancelled` 改成這張還沒種過時先替這間房種一張房務待認領的備品（`GuestScenario.knowsRequest`），已經種過時照舊。句子語意不變。

## 評分（4-24）

- **評分卡**：選的星等存在評分卡自己的狀態裡，即時更新重畫時不洗掉；`canRate` 為否時整張卡不畫（評過、`closed`、櫃檯回答過）。成功時用後端回的這張蓋掉清單裡的舊資料再重讀，浮出「謝謝您的評分」；`409` 不提示、等重讀完讓卡依新狀態消失；連不上浮出「沒有送出…」、星等留著。
- **測試層修正**：`"{房號}" 房的住客剛替 "{標題}" 送出了 {星等} 顆星，停在它的進度頁`（`評分.feature:73`）和 4-23 同一個缺口：場景前面沒有種過「礦泉水 ×2」。`CancelRateGivenSteps.justRated` 改成還沒種過時先種一張阿美完成、還沒評過的備品，句子語意不變。

## 即時更新：同仁一動畫面跟上（4-25）

- **串流掛在進入層**：`GuestEntry` 在畫面就緒後以 `useGuestLiveUpdates` 開 `GET /guest/stream`（共用 `openStream`，帶 `X-Guest-Session`），換頁不重開，session 被靜靜重換時才換一條。收到 `task` 讓需求清單失效重讀，收到 `resync` 另讓首頁資料失效重讀；三個畫面只訂閱查詢結果，沒有另寫畫面分支。
- **測試層修正**：`同仁一動畫面跟上.feature:74`「阿美也完成了最後一張進行中的「毛巾」」的 Given 把毛巾種成房務待認領，When 是「阿美 在手機上完成 "毛巾"」；`dsl.md` 那一列只寫打 `tasksComplete`，後端對待認領的單回 `409 state_conflict`（只接受依序推進），場景還沒碰到畫面就失敗。`RealtimeWhenSteps.staffActs` 在「完成」一張還沒接手或還沒開始的需求時，先替同一位同仁認領、開始再完成；其他動作不變。住客看到的結果和同仁一路做完相同。回交 `/dsl-refine`：那個場景的 Given 改成「進行中」，或 `dsl.md` 那一列寫明「完成」會依序推進。

## 即時更新：連線異常時定期重讀（4-26）

- **一台狀態機**：住客的連線狀態由櫃檯共用的 `transition` 推（`features/realtime/live-connection.ts`，語意沒改），只多一條規則，寫在 `guest/lib/live.ts` 的 `guestTransition`：住客的串流沒有 `recovered`，重新連上之後收到 `init` 就恢復信任。`guestRereadMs` 從狀態算出需求清單要不要每 4 秒重讀：連著、收過訊號、沒被宣告延遲時不重讀；斷線、35 秒沒有資料（共用的 `SILENCE_LIMIT_MS`，串流客戶端會切斷重連）、收到 `degraded`、重新連上還沒收到 `init` 都重讀。進入層把間隔放進 `GuestRereadContext`，`useGuestRequests` 拿它當 `refetchInterval`；畫面上沒有任何連線提示，重讀失敗時查詢保留舊資料。重連的 1 秒、之後每 5 秒照共用的 `retryDelayMs`；後端在每次連上時送的 `resync` 照 4-25 的表讓首頁資料與清單整份重讀。
- **共用串流客戶端的改動**：`openStream` 多一個可選的 `transition`，不給時就是原本的 `transition`，櫃檯不傳，行為不變。`lib/live.test.ts` 逐條驗：連上收到 `init` 不重讀、只連上還沒訊號要重讀、斷線、沉默與 `degraded` 三種都重讀、`degraded` 之後的心跳不會恢復、`degraded` → 斷線 → 重連 → `init` 才回到即時、重連失敗幾次後收到 `init` 也回到即時、還沒連上時的 `init` 不算。
- **櫃檯回歸**：`frontdesk-web/即時更新/` 四份 32 個場景在 `desktop` 全綠。

## 子頁標題列（4-27）

- **一開跑就綠**：五個子頁的標題列在 4-05～4-22 已經照 ui-plan 做好，送出成功頁與無法使用頁本來就沒有標題列；紅燈那一次 9 個場景全綠，沒有改產品碼。
- **整理（T254）**：`TitleBar` 的 `backTo` 不給時回首頁，備品補充、設備報修、我的需求清單、資訊頁只宣告頁標題；只有進度頁傳入依來處算出的去處。資訊頁的頁標題是飯店設定的原文，所以標題列收的是字，不是語系鍵。

## 介面語言（4-28）

- **語言選單**：照原型 `tw-menu`：語言膠囊下方 8px 彈出、靠右，小標「語言」，四個選項是各自的寫法（`LANGUAGE_OWN_NAMES`），目前的加粗並打勾；點選單外的透明遮罩、Esc 都收起，焦點回到膠囊。選了就呼叫 `useSetGuestLanguage`：記進這支手機、換語言、改網頁的 `lang`。原本的 `useGuestLanguage` 拆成只在外框掛上時決定語言的那一半，設定的那一半另成 `useSetGuestLanguage`，選單不用再跑一次決定語言的流程。
- **擋住占位譯文（T256）**：`src/i18n/locales/guest-translations.test.ts` 檢查英、日、越的 `guest` 子樹沒有任何值和繁體中文相同；拿掉插值後沒有任何文字的格式字串（`{{count}} / {{max}}`、`{{title}} · {{room}}`）天生各語言相同，以規則排除、不列白名單。故意把英文的 `toast.notSent` 換回繁體中文時這條測試會紅。
- **測試層修正**：`介面語言.feature` 的「日文介面的清單與櫃檯回覆」開的是櫃檯回答過的進度頁，這種需求不顯示四步，`ShellThenSteps.languageProblem` 只看四步步名判斷進度頁的介面語言，永遠判斷不出來。`dsl.md` 模組說明寫「子頁用標題列的頁標題」，進度頁看不到四步時改看標題列的「需求進度」（`LanguageMenu.FIXED_PHRASES` 加上它的四語，取自原型字典）；有四步時照舊。回交 `/dsl-refine`：模組說明加上「進度頁沒有四步時看標題列」。

## 手機版面（4-29）

- **一開跑就綠**：11 個場景在紅燈那一次全綠，版面在各頁的 phase 已照 ui-plan 做好，沒有改產品碼。
- **整理（T258）**：尺寸收成 `guest/theme.css` 的設計變數：`--spacing-guest-touch` 44、`--spacing-guest-bar-button` 54、`--spacing-guest-title-bar` 56、`--spacing-guest-gutter` 16、`--container-guest-content` 480；左右邊距加安全區域是 `:root` 的 `--guest-gutter-left`／`--guest-gutter-right`，外框、頁底操作列、提示浮層共用；提示浮層最寬 448 由 480 減兩邊邊距算出。正式建置的 CSS 裡每個新類別都有對應的規則，五個變數都在。`共用外框/` 三份 35 個場景重跑全綠。

## ui quality gate（4-G）

- **做法**：fresh-context 的 subagent 寫了截圖巡覽 `app/web-bdd/src/test/java/com/athena/web/bdd/GuestScreenTourTest.java`（被 pom 排除在平常的 `mvn test` 之外，會清空 8080 的業務資料），在 `phone` 設定（WebKit 393 × 852、觸控、時鐘 14:30）下把 4-G Boundary 列的每一種狀態各拍一張產品畫面與一張雛形畫面（`ui/*.html` 加切換參數），共 47 組、93 張；每張旁邊存一份計算後樣式的 JSON，逐元素比字級、行高、字重、字距、顏色、底色、圓角、陰影、內距與位置。重跑：`mvn -q -f app/web-bdd/pom.xml test -Dtest=GuestScreenTourTest -Dsurefire.failIfNoSpecifiedTests=false`（JDK 25）。截圖在 `/Users/Caresys/.claude/jobs/bf2b4d32/tmp/4g-shots/`，比對腳本在同一層的 `4g-tools/diff2.py`（都不在 repo 裡）。
- **修掉的差異**：
  1. 頁底送出鈕、重試、「查看進度」高 44 不是 54：T258 把 44、54 收成同一層的具名類別後，產生的 CSS 裡 `min-h-guest-touch` 排在 `min-h-guest-bar-button` 後面而蓋過它（T258 自己引入的回歸）。`buttonClass` 改成高度與左右內距各只給一個類別。
  2. 「回首頁」文字鈕左右內距 20 不是 8：同一個原因，`px-5` 蓋過 `px-2`；一併收進 `buttonClass`，確認框兩顆並排鈕用 `tight`（12）。
  3. 首頁「我的需求」標題列與下方卡片的間距 2 不是 10：拿掉 `-mb-2`。
  4. 清單空狀態的內距 24／12／20 不是原型 `tw-empty` 的 48／16／32。
  5. 子頁標題列只有內容寬（369），原型是滿版（393）：捲動時出現的細線兩端各短 16 點。改成左右延伸到邊距外。
  6. 首頁「全部 n 項」少了原型 `.tw-btn` 的 1.5 點透明框，寬少 3 點。
  7. 進入層載入中時分頁標題露出櫃檯 Web 的「TMS 櫃檯」：改成不放字。
  8. 設備報修的類別膠囊與語言選單的選項沿用外框的 −0.43 字距，原型是 normal。
  9. `[data-theme="dark"]` 少了 `on-toast`、`on-illo`、`shadow-popover`、`shadow-toast` 四個變數，補上（深色仍不啟用）。
  修完重跑巡覽，除了下面接受的差異之外沒有任何尺寸、內距、間距、顏色或字的差異，393 寬下每頁都沒有橫向捲動。
- **接受的差異**：
  - 首頁房號膠囊沒有床鋪圖示（原型有）。ui-plan 畫面 2 寫「品牌色床鋪圖示」，「鍵盤與螢幕報讀」寫「房號膠囊只有房號這幾個字…沒有圖示」，T200 的測試照後者檢查不能有圖示；產品照後者。兩段互相矛盾，請使用者裁定。
  - 載入中的分頁標題是空的、重試狀態是「連線失敗，請再試一次」，原型是「311 號房」：第一次換 session 之前不知道房號，原型的房號是示範資料。
  - 早餐資訊頁內文一行（後端預設值「一樓餐廳 06:30–10:00」），原型是兩行示範資料；truth 優先。
  - 雛形用 `fail=` 參數模擬的幾種失敗（送到一半斷線、同仁剛好接手），巡覽以攔截做出、時機不同，提示框標題依情境不同；提示框本身的樣式一致。
  - ui-plan「與原型不同的地方」已列的：可點的東西補到 44、警告類提示浮層用三角圖示、不畫裝置外框。
- **內部用語掃描**：每一張產品畫面的字與分頁標題都沒有內部值、錯誤碼、飯店內部用語、同仁姓名、住客姓名、飯店名稱或日期。
- **沒拍到的**：送出中、重試中、撤回中、評分中的轉圈（只有一瞬間，原型也只在動畫中途）；即時更新的畫面（和靜態狀態長得一樣）；設備報修打滿 200 字。
- **真機**：未做。這一輪沒有 iPhone 或 Android 實機可用（作業環境只有本機瀏覽器），安全區域、瀏覽器工具列收放與 `dvh` 的表現待有實機時看一次。

## 待翻譯（4-28 已補齊，請以母語者確認）

原型字典沒有這幾句。Phase 4-01 到 4-23 期間英、日、越先放繁體中文，4-28 由實作的 agent 自己翻譯補上（translated by agent, needs native review）。英文與越南文沿用原型字典對同一個畫面元件的叫法（例如「Amenities」「Report a problem」「Bổ sung vật dụng」），日文的「フロント」「9 番」照原型的寫法。

| 語系鍵 | 繁體中文 | English | 日本語 | Tiếng Việt | 加入的 phase |
| --- | --- | --- | --- | --- | --- |
| `guest.unavailable.body` | 目前無法在這裡提出需求，需要什麼請直接找櫃檯。 | You can’t send requests from here right now. For anything you need, please contact the front desk. | 現在こちらからはリクエストを送信できません。ご用の際はフロントへ直接お問い合わせください。 | Hiện không thể gửi yêu cầu tại đây. Quý khách cần gì xin vui lòng liên hệ trực tiếp lễ tân. | 4-01 |
| `guest.home.repairLead` | 點一下就報修 | tap to report | タップで連絡 | chạm để báo | 4-02 |
| `guest.requests.emptyBody` | 在首頁選「備品補充」或「設備報修」，送出後進度會顯示在這裡。 | Choose “Amenities” or “Report a problem” on Home. Once sent, progress shows here. | ホームで「アメニティ」か「故障の連絡」を選んでください。送信後、進捗がここに表示されます。 | Chọn “Bổ sung vật dụng” hoặc “Báo sửa thiết bị” ở Trang chủ. Sau khi gửi, tiến độ sẽ hiển thị tại đây. | 4-17 |
| `guest.limit.partial` | 已送出 {{sent}} 項，其餘的請稍後再送出，或用房內電話撥 9 找櫃檯。 | {{sent}} sent. Please send the rest later, or dial 9 for the front desk. | {{sent}} 件を送信しました。残りはしばらくしてから送信するか、9 番でフロントへ。 | Đã gửi {{sent}} món. Vui lòng gửi phần còn lại sau, hoặc bấm 9 để gọi lễ tân. | 4-18 |
| `guest.notSent.title` | 沒有送出 | Not sent | 送信できませんでした | Chưa gửi được | 4-19 |
| `guest.notSent.partialTitle` | 已送出 {{sent}} 項，其餘的沒有送出 | {{sent}} sent, the rest weren’t | {{sent}} 件は送信済み、残りは送信できませんでした | Đã gửi {{sent}} món, phần còn lại chưa gửi được | 4-19 |
| `guest.notSent.body` | 請檢查網路連線後再試一次。 | Check your connection and try again. | ネットワーク接続を確認して、もう一度お試しください。 | Vui lòng kiểm tra kết nối mạng rồi thử lại. | 4-19 |
| `guest.toast.notSent` | 沒有送出，請檢查網路連線後再試一次。 | Not sent. Check your connection and try again. | 送信できませんでした。ネットワーク接続を確認して、もう一度お試しください。 | Chưa gửi được. Vui lòng kiểm tra kết nối mạng rồi thử lại. | 4-23 |

ui-plan「找不到出處的文字」另外幾句，四語版本在更早的 phase 已經寫好、原型字典同樣沒有出處，一併請確認：

| 語系鍵 | 繁體中文 | English | 日本語 | Tiếng Việt |
| --- | --- | --- | --- | --- |
| `guest.comingSoon` | 即將開放 | Coming soon | 近日公開 | Sắp ra mắt |
| `guest.connectionFailed` | 連線失敗，請再試一次 | Connection failed. Please try again. | 接続できませんでした。もう一度お試しください。 | Kết nối thất bại, vui lòng thử lại. |
| `guest.pageTitle`（網頁標題） | {頁標題} · {房號} 號房 | {頁標題} · Room {房號} | {頁標題} · {房號}号室 | {頁標題} · Phòng {房號} |

網頁標題「{房號} 號房」與無法使用時的「請洽櫃檯」用的是原型字典已有的 `{1} 號房`、`請洽櫃檯` 譯文。英、日、越三份 `guest` 子樹和繁體中文相同的值現在只剩兩個格式字串（`repair.noteCount`、`pageTitle`）。

## Phase 5 回歸

2026-11-20 由沒有寫過本輪程式的驗收 agent 獨立跑，全部第一次就綠，沒有任何場景需要重跑，所以沒有不穩定的場景。

- **T260 住客 `phone`**：`bash app/web-bdd/run-feature.sh --keep guest-web/ phone`，236／236 通過、0 失敗、0 未定義，510 秒。
- **T261 前端**：`npm run typecheck` 綠；`npx vitest run` 59 檔 729 項綠；`npm run build` 綠。住客 chunk 仍獨立（`routes-*.js` 68.03 kB），主 chunk `index-*.js` 從 T001 量的 667.00 kB 變成 691.84 kB。多出的約 25 kB 主要是四份語系檔各多出的 `guest` 子樹（每份 181 行），另有 `index.css` 匯入的 `guest/theme.css`。語系與外框樣式會跟著櫃檯一起下載，住客的程式本身不在主 chunk。
- **T262 櫃檯 Web**：`frontdesk-web/ desktop` 432／432 通過（664 秒）；`frontdesk-web/ ipad` 整套 432／432 通過（783 秒），涵蓋 `共用外框/支援的視窗與輸入方式.feature`。
- **員工 App 測試線**：沒有另跑 `ios-app/待認領/`。`BackendChannel` 既有方法的簽章沒變；`guestSessionToken` 的查訂房 SQL 抽成 `stayingReservationId`、字面一樣，`request()` 改成呼叫 `requestOutsideApi(BASE_PATH + path, …)`、組出的網址一樣，不算動到 SQL。
- **後端**：本輪工作樹 `git status -- tms/` 沒有變動，`514d3a2^..HEAD` 沒有碰 `tms/`。`git diff main...HEAD -- tms/` 不是空的，是因為本分支建在 `feature/gherkin` 之上，本機 `main`（20e77df）落後 `origin/main`，差異是先前 004 與 gherkin 的後端 commit。
- **人工煙霧測試**：在 5433 的資料庫替「雅典娜酒店」311 房補一筆今天入住的訂房，以 `M0001` 讀 `GET /admin/rooms/311/qr` 取得 QR 權杖，用 Chrome 393 × 852 行動模擬開 `/g/<token>`：首頁、備品補充把礦泉水加到 2 送出、需求已送出、`F0101` 登入的櫃檯看板在「待認領」看到「311 礦泉水 ×2」、我的需求、進度頁取消並確認、看板即時移到「已結案」、Wi‑Fi 資訊頁，都正常。主控台沒有錯誤；每次直接載入 `/g/…` 有一則 React Router 警告「No `HydrateFallback` element provided to render during initial hydration」（延遲載入的路由沒有給載入中的元素），櫃檯登入頁有一則「表單欄位沒有 id 或 name」的提示。截圖在本機 `app/web-bdd/target/phase5-smoke/`。
