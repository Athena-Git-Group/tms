# T200 Phase 3 品質閘門 review

ISSUES: 9（blocker 1、major 2、minor 6）

Phase 3 的 183 句 stepdef 全部定義得到、沒有 ambiguous，住客與櫃檯兩個根的 dry-run 都乾淨；沒有 Given 經 `POST /guest/requests` 種資料，沒有等真的 10 分鐘，也沒有靠真的限流器回 `429`；Phase 3 沒有動 `app/web/src`、`tms/`，`specs/truth/**` 只有被允許的那一處。卡住 Phase 4 的是下面 3 件：1 件測試碼錯誤，會讓產品做對也一定紅；1 件後端順序檢查在固定時鐘下會隨機失敗；1 件 truth 與產品語系衝突，目前沒有人決定。

## Issues

### 1. blocker：語言膠囊的定位用了 `Pattern.quote`，在瀏覽器裡永遠比對不到

- 位置：`app/web-bdd/src/test/java/com/athena/web/bdd/pages/guest/LanguageMenu.java:55`
- 問題：`capsule()` 用 `CAPSULE_NAMES.stream().map(Pattern::quote)` 組 `setName(Pattern)`。Playwright 會把這個 `Pattern` 原封不動交給瀏覽器的 JavaScript。`\Q…\E` 是 Java 才有的寫法，JS 會把它讀成字母 Q 與 E，所以任何按鈕都比對不到。`support/BrowserRegex.java:6-9` 寫的就是這個陷阱，`GuestHomePage` 已經修過一次，這裡是漏網的同一種錯。
- 影響：`capsule()` 永遠是 0 個，`capsuleText()` 永遠是空字串。`ShellWhenSteps.tapLanguageCapsule`（`住客點語言膠囊`，也被 Given `…已打開首頁，語言選單開著` 呼叫）一律丟「畫面上找不到語言膠囊」，`ShellThenSteps.capsuleShows`（`語言膠囊顯示 "{短寫法}"`）一律失敗。`共用外框/介面語言.feature` 第 10、28、39、42、52、93、99 行用到的場景，產品照原型做對了也綠不了。Phase 4-28 會把這個錯當成產品沒做好去追。
- 違反：Phase 3 Boundary「失敗只能是 assertion 或產品行為」；`共用外框/dsl.md`「住客點語言膠囊」「語言膠囊顯示」兩列。
- 修法：改成 `.map(BrowserRegex::literal)`。另外把整個 `support/`、`pages/guest/`、`steps/guest/` 用 grep 掃一次 `Pattern.quote` 與 `Pattern::quote`。`support/Network.java:47` 那一處是在 Java 端比對，不受影響。

### 2. major：`清單 "{段落}" 由上到下是 …` 的後端順序檢查在同一刻建立的需求上會隨機失敗

- 位置：`steps/guest/common/GuestCommonThenSteps.java:165-171`，搭配 `support/GuestStay.java:110`（`ORDER BY created_at, id`）
- 問題：後端時鐘在場景裡是固定的（`tms/.../TestClockController.java` 的 `clock.fixAt`）。同一次送出建立的幾張需求 `created_at` 完全相同。`sorted(Comparator.comparing(Request::createdAt).reversed())` 在同分時保留原本的順序，而原本的順序是 `id`（隨機 UUID）。所以兩張同時刻的需求，後端排出來的先後大約一半機率和清單相反。`備品補充/送出鈕與成功頁.feature:72`（`"礦泉水 ×2、毛巾"`，兩張在同一次 Given 送出）會時綠時紅，畫面再正確也一樣。
- 違反：根 `dsl.md`「清單由上到下是」`必查`「順序等於後端這些需求的送出時間新到舊」。同時刻的需求沒有新舊之分，現在的寫法比 dsl 嚴，而且不穩定。
- 修法：後端這一半改成檢查清單相鄰兩列的 `createdAt` 由新到舊不遞增（`createdAt[i] >= createdAt[i+1]`），同一刻就放行；畫面那一半照舊比字面。另外回交 `/dsl-refine`：後端時鐘固定時，這個場景期待的「礦泉水 ×2 在毛巾上面」，產品要靠什麼次序排出來，dsl 沒有交代。這需要上游定次序（例如同一刻依送出先後），否則 Phase 4-16 的產品無從對齊。

### 3. major：英文介面的資訊頁場景要求返回鈕叫「返回」，產品與原型都是「Back」

- 位置：`specs/truth/features/guest-web/住宿指南/資訊頁內容.feature:43`；`steps/guest/common/GuestCommonThenSteps.java:116-136`（`titleBarIs` 用寫死的 `GuestFrame.BACK = "返回"`，`pages/guest/GuestFrame.java:38`）
- 問題：被允許的那一處 truth 修改只把第二行改成 `"Room 311"`，返回鈕仍以 `setName("返回").setExact(true)` 找。`app/web/src/i18n/locales/en.ts:434` 是 `back: 'Back'`，原型 `ui/guest.js:276` 的返回是 `aria-label=t('返回')`，英文就是「Back」。這個場景在正確的產品上一定紅。implement-notes 已經點名這件事要回交上游，但目前沒有決定，Phase 4-08 會卡在這裡。
- 違反：根 `dsl.md`「頁面頂端的標題列是返回鈕、…」寫「名稱「返回」的按鈕」照字面比，「返回」又不在「跟著語言換字」的清單裡。truth 與產品語系彼此衝突。
- 修法：交 `/dsl-refine` 拍板。建議做法是把「返回」加進根 `dsl.md`「文字比對」那條跟著語言換字的清單，stepdef 改用 `GuestTerms.in(frame.language(), "返回")`（`titleBarIs`、`ShellThenSteps.noSubpageTitleBar:151`、`CancelRateThenSteps:275` 三處一起改）。另一個做法是把這一步改成只比頁標題與房號那一行。Phase 4-08 開工前要先定。

### 4. minor：「後端這時回覆很慢」扣的是請求，不是 dsl 寫的回應，而且只有「連按兩下」會放行

- 位置：`steps/guest/supply/SupplyGivenSteps.java:153`、`steps/guest/supply/SupplyScenario.java:76-91`、`steps/guest/supply/SupplyWhenSteps.java:87-94`
- 問題：dsl 寫的是「回應扣住 3 秒再放行，請求本身照常到達後端」。實作扣住請求 3 秒，而且放行只發生在 `住客連按兩下` 的迴圈裡（`releaseDueSlowReplies`）。這是 tasks.md「測試策略」兩處允許替換以外的第三處手段替換。另外這個 Given 只能接 `連按兩下`：之後接別的 When，請求會一直被扣著。現在只有 `送出鈕與成功頁.feature:113` 這一處用到，所以還沒出事。
- 違反：Phase 3 DSL 參照「語意以 dsl.md 那一列為準 … 唯一例外是測試策略的兩處手段替換」；`備品補充/dsl.md`「後端這時回覆很慢」。
- 修法（擇一）：①在 `GuestRequestLimitStub.handle` 加一個「慢回覆」旗標，記下冪等鍵之後 `route.fetch()`，拿到後端回應、等 3 秒再 `route.fulfill(response)`，放行不再靠 When；②維持現狀，但把這一處補進 tasks.md「測試策略」並回交 `/dsl-refine` 改字。

### 5. minor：進度頁與外框的幾個否定句，在畫面還沒畫出來時也會通過

- 位置：`GuestCommonThenSteps.java:191`（`四步進度走到 "不顯示進度"`）、`:227`（`進度頁底部是 "什麼都沒有"`）、`:242`（`進度頁沒有評分卡`）；`RequestsThenSteps.java:217`（`進度頁沒有狀態訊息框`）；`ShellThenSteps.java:151`（`畫面上沒有子頁的標題列`）、`:193`（`每一個可以點的東西至少 44 點見方`：一個可以點的東西都沒有時也算過）
- 問題：這些句子都包在 `eventually` 裡，第一次看到「沒有」就回傳，事前沒有確認進度頁已經畫完。`不顯示進度` 還要靠 `RequestPage.fourSteps` 的步名逐字對上：產品把四步畫成別的結構時，它就回「沒有四步」而直接通過。現有的場景多半在前一步已經等到進度頁，所以目前不致誤判。但只要前一步換掉，這幾句就會在空白頁上通過。
- 違反：審查重點 4「Then 不得空泛地通過」。`住宿指南` 的 `GuideThenSteps.bodyIsEmpty` 已經先等標題列再判斷，這幾句沒有照做。
- 修法：否定之前先確認那一頁畫完（網址是進度頁、`main` 裡有 `h2`、沒有 `aria-busy`），比照 `GuestPhone.waitUntilRequest`。`不顯示進度` 另外檢查四個步名都不在畫面上。44 點那一句要求至少找到一個可以點的東西。

### 6. minor：進入層兩句「過程中…」沒有確認觀察程式真的記到東西

- 位置：`steps/guest/entry/EntryThenSteps.java:75`（`從打開網址到看到首頁，住客沒有輸入或按過任何東西`）、`:95`（`過程中沒有要住客再掃一次 QR Code，也沒有出現錯誤訊息`）
- 問題：兩句都在 `seenOnThisTab()` 上跑迴圈。`ScreenWatch` 的觀察程式萬一沒掛上（分頁在換頁時 `evaluate` 失敗，`addInitScript` 也沒生效），清單是空的，「過程中沒有出現過輸入框、登入頁、錯誤字」就會空泛地成立。
- 違反：`進入層/dsl.md` 這兩列的 `必查`；審查重點 4。
- 修法：兩句開頭加 `assertThat(!seenOnThisTab().isEmpty(), "從 When 開始沒有記到這個分頁的任何畫面")`。

### 7. minor：幾句 Then 比 dsl 的 `必查` 寬

- `RequestsThenSteps.java:127` `每列左邊有依需求類型的圖示`：只查「同類型同一個圖、不同類型不同圖」，沒有查 dsl 點名的對應（`amenity` 方盒、`repair` 鎚子、`cleaning` 星光、`other` 對話泡泡）。列表全是同一種類型時，任何圖示都能過。
- `HomeThenSteps.java:86` `頂列右上是房號膠囊…`：沒有查房號膠囊「沒有向下箭頭」。`HomeThenSteps` 的 `首頁上「即將開放」的標籤有 {n} 個`、`GuideThenSteps.java:50` `首頁的住宿指南由上到下是`：都沒有查箭頭（前者「沒有箭頭」、後者「只有標題與箭頭」）。
- `RequestPage.steps`：實心、空心、打勾完全由 `aria-current` 推出，畫面實際畫成什麼樣子不看。
- 違反：各句 `dsl.md` 的 `必查`。
- 修法：圖示比照 `RequestPage.starShapes`，以後端 `type` 對到四種固定的圖形內容（從 Phase 4 的圖示元件取參考值）；箭頭以列裡最後一個 `svg` 的有無判斷。若決定不查，把這幾處寫進 implement-notes，當成已知的檢查縮減。

### 8. minor：page object 要求的可及性結構超出 `ui/ui-plan.md`「鍵盤與螢幕報讀」，而且只記在 implement-notes

- 位置：`pages/guest/RequestsPage.java:46`（`h2` 之後的 `xpath=following::ul|ol`）、`pages/guest/GuidePage.java:43`（`section` 的 `following-sibling::*[1]`）、`pages/guest/SupplyPage.java:60`（`main *` 找最內層的列）、`steps/guest/realtime/GuestLive.java:79`（`locator("h1, h2")`）、`RequestsPage.leftIconOf`（比 `svg.innerHTML`）。另有 `role="note"`、`aria-current="step"`、`banner` 標題列、`aria-busy`、語言膠囊可及名稱「語言／Language／言語／Ngôn ngữ」。
- 問題：沒有 `data-testid`，這點合規。但上面這些都是 DOM 結構或角色要求，ui-plan 那一段沒有寫，只寫在 `implement-notes.md:46,54`。Phase 4 的 `Shared Must Read` 回追的是 `ui/ui-plan.md`，不是 implement-notes。另外返回鈕：`titleBarIs`、`noSubpageTitleBar`、`CancelRateThenSteps:275` 只認 `button` 角色，原型 `ui/guest.js:276` 卻是 `<a href>`（角色 `link`）。產品照原型做，正向句會紅，`noSubpageTitleBar` 會空泛地通過。
- 違反：Phase 3 Boundary「page object 需要的可及性名稱照 feature 的畫面文字與 `ui/ui-plan.md`「鍵盤與螢幕報讀」」；審查重點 5。
- 修法：把 implement-notes 那兩段的結構要求搬進 `ui/ui-plan.md`「鍵盤與螢幕報讀」（plan-side 檔，不是 truth）。`GuestLive.headings` 改用 `getByRole(AriaRole.HEADING)`。返回鈕的定位改成 `button` 或 `link` 都接受，和 `GuestFrame.PRESSABLE_ROLES` 一致；或在 ui-plan 明寫返回必須是 `<button>`。

### 9. minor：四組人各寫一份的重複 helper 與定義不一致

- 語言膠囊兩種認法：`GuestHomePage.java:102` `languageCapsule(shortName)` 以看得到的字認、可及名稱不限；`LanguageMenu.java:54` `capsule()` 以可及名稱認。兩者對產品的要求不同，第 1 件的錯也只出在其中一份。
- 住客 session 鍵的字面寫了兩次：`EntryGivenSteps.java:28` 與 `support/GuestPhone.java:147`。
- 遮罩有兩種定義：`RequestPage.java:203` `anyScrim()` 要半透明的底色；`LanguageMenu.java:97` `scrimShown()` 任何蓋滿視窗的固定層都算。
- 提示層有兩種數法：`GuestLive.java:84` `visibleNoticeLayers` 把沒有字的 `role="status"`（轉圈）也算進去；`GuestFrame.toasts()` 只算有字的。畫面背景重讀時若顯示轉圈，`畫面上沒有任何提示列或錯誤訊息` 會誤判。
- 9 份私有 `eventually`：有的把 `RuntimeException` 當成「還沒好」，有的不當，失敗訊息的格式也各不相同。
- 修法：語言膠囊只留 `LanguageMenu` 一份（修好第 1 件之後），`HomeThenSteps.topBarCapsules` 改用它再比字。session 鍵收成 `GuestPhone` 的一個常數。遮罩與提示層各留一個定義，`visibleNoticeLayers` 的 `STATUS` 改用 `frame.toasts()`。`eventually` 收進 `GuestFrame` 一份。

## 檢查了什麼、怎麼查

1. **編譯與 dry-run**（JDK 25，`JAVA_HOME=$(/usr/libexec/java_home -v 25)`）
   - `mvn -q -f app/bdd-support/pom.xml install -DskipTests`；`mvn -q -f app/web-bdd/pom.xml clean test-compile`：過。
   - 住客根：`mvn test -Dcucumber.features=../../specs/truth/features/guest-web -Dcucumber.execution.dry-run=true -Dweb.profile=phone`。236 個場景、1098 個步驟，`undefined` 0、`ambiguous` 0，每個步驟都有 glue 位置。用到的不同 glue 方法剛好 183 個，等於 183 句；其中 11 個在櫃檯的定義裡分流（`AccountThenSteps.screenIs`、`CommonGivenSteps.nowIs`、`CommonThenSteps` 6 句、`CommonWhenSteps.actOnPhone`、`RealtimeWhenSteps.connectionLost` 與 `noSignalFor`）。
   - 櫃檯根：同一個指令換成 `frontdesk-web`、`desktop`。432 個場景、2064 個步驟，`undefined` 0、`ambiguous` 0，沒有任何步驟落到 `steps.guest`。分流的 4 個櫃檯定義都以 `GuestScenario.active()` 分支，非住客場景走原本的路，看 `git diff` 確認過。
2. **Given 不經 `POST /guest/requests` 種資料**：grep `guest/requests` 與 `GUEST_CREATE`，`BackendChannel` 沒有任何建立需求的呼叫，只有 `guestRateRequest`（評分）。經畫面真的送出的 Given 有：`剛在備品補充送出…`（兩句）、`剛才送到一半斷線…`、`剛送出報修…`、`另一支手機已在備品補充送出`、`從 "送出成功頁" 進了…的進度頁`。這幾句的 dsl 列本身就規定要走畫面，屬於 tasks.md 允許的「剛在備品補充送出／另一支手機送出」那一類。`剛被額度估算擋下` 是在前端就被擋，不送出。舊需求、代開單、星等一律走 `seedGuestRequest`、`seedDeskTask`、`setTaskRating`。替身 `GuestRequestLimitStub` 經 `WebWorld.onEveryNewContext` 裝在每個住客 context，第二支手機與 308 房的 context 也有。
3. **不等 10 分鐘、不靠真的限流器**：grep `waitForTimeout|Thread.sleep|ofMinutes|ofSeconds|_000` 找到的等待最長是 `SupplyWhenSteps` 的 20 秒上限（連按兩下的收尾）；其餘 12 秒是推瀏覽器時鐘，不是真的等。`429` 全部由攔截產生：送出需求走替身，掃碼走 `respondWithError(GUEST_SCAN, 429)`。限流相關的 feature（`十分鐘額度估算`、`報修的限流`、`後端在送到一半拒絕`）都是先撥 `現在時間是` 再送，沒有在送出之後把時鐘往後撥再送的案例。
4. **DSL 忠實度**：抽查 61 句，10 份 dsl.md 每份都有，以 Then 為主。
   - 根（21 句）：這次住宿有以下需求、停在、開著進度頁、開著資訊頁、調到、介面語言、連不上、代開單、畫面是進度頁、標題列、狀態標籤、清單由上到下、訊息框、四步走到、四步如下、底部、沒有評分卡、首頁進度卡、後端多了、後端沒多、提示框。
   - 進入層（12 句）：換 QR、憑證不見、憑證失效、退房、掃碼限流、掃碼出錯、停在重試、清單出錯、無法使用頁相同、沒有可點的、請求不帶員工憑證、櫃檯兩句。
   - 首頁（7 句）、備品補充（10 句）、設備報修（6 句）、我的需求（7 句）、取消與評分（6 句）、即時更新（15 句）、住宿指南（6 句）、共用外框（10 句）。
   - 不合的寫在第 2～7 件；其餘照 `實作語意`／`必查` 做。
5. **定位方式**：grep `data-testid|getByTestId` 0 處。結構型 selector 列在第 8 件。
6. **跨組一致性**：`Pattern.quote` 剩 `LanguageMenu.java:55`（第 1 件）。已知的 `GuestHomePage` 改 `BrowserRegex.literal` 確認已修。重複的 helper 列在第 9 件。各組的 `@Before` 順序分別是 GuestHooks 1、GuestStay 2、ScreenWatch／SupplyScenario 3，各自只在自己的根或模組啟用，沒有互相蓋掉的狀態。
7. **產品碼外洩**：以最早的 Phase 3 step 檔（`GuestCommonGivenSteps.java`，11-20 11:39）的時間為界，`find app/web/src tms/src specs/truth -newermt` 只有 `住宿指南/資訊頁內容.feature`。`app/web/src` 最後一次改動在 10:43，屬於 Phase 1–2（T001、T006–T013 的檔）。`git status tms/` 是空的。`git diff specs/truth/` 只有 `資訊頁內容.feature` 一行：`"房號 311"` 改成 `"Room 311"`，就是被允許的那一處。

---

# 第二輪

ISSUES: 1（minor 1，要修；另有 4 條純文字的後續，不擋閘門）

第一輪的 9 件都在程式裡改到了，不只是寫在 implement-notes。編譯、兩個根的 dry-run 都乾淨。真的跑了一份櫃檯 feature，新加的輪詢放行機制在櫃檯場景完全不動作。剩下的 1 件是第一輪第 7 件的同一類，那次沒抽到。評分卡的星星只比「幾顆畫成同一種圖形」，沒有認哪一種是實心。所以 `只有前 5 顆星是實心的` 在五顆畫成同一種樣子時一定通過，即使五顆全是空心。照這次的規則，非文字的 minor 也擋閘門，T200 先不打勾。

## 第一輪 9 件逐件確認

1. **語言膠囊的 `Pattern.quote`：已修。** `pages/guest/LanguageMenu.java:56` 改成 `.map(BrowserRegex::literal)`。全目錄 grep `Pattern.quote` 剩兩處：`support/Network.java:47` 與 `pages/AnswerPanel.java:110`。兩處都是 Java 端的 `Pattern.matcher`，不交給瀏覽器（後者是 004 的櫃檯碼）。
2. **清單順序的後端檢查：已修。** `steps/guest/common/GuestCommonThenSteps.java:166-171` 改成相鄰兩列 `!upper.createdAt().isBefore(lower.createdAt())`，同一刻的放行。畫面那一半仍照字面比（`:157-160`）。根 `dsl.md` 該列已寫明「同一刻送出的後端不分先後，畫面上先送出的在上」，回交上游的那一半也定了。
3. **返回鈕跟著介面語言：已修。** 根 `dsl.md`「文字比對」把「返回」加進換字清單。`support/GuestTerms.java:36` 補上 `Back／戻る／Quay lại`，和 `ui/guest.js:85`、`app/web/src/i18n/locales/en.ts:434` 一致。`pages/guest/GuestFrame.java:86-91` 的 `backControl` 收 `button` 或 `link`，用的地方有三處：`GuestCommonThenSteps.java:121`、`ShellThenSteps.java:158`、`CancelRateThenSteps.java:270`。原型的返回鈕 `<a aria-label>`（`ui/guest.js:276`）認得到。
4. **「後端這時回覆很慢」扣請求：已修。** `support/GuestRequestLimitStub.java:156-158` 先 `route.fetch()` 送到後端，再把回應扣在測試這一頭。`:90-95` 扣滿 3 秒才 `fulfill`，放行的時機有三個：`GuestFrame.eventually`（`GuestFrame.java:301`）、`waitAtMost`（`:333`）與連按兩下的迴圈（`SupplyWhenSteps.java:94`）。場景結束時 `GuestHooks.java:47-52` 全部放行。`SupplyScenario` 已經不扣請求了。
5. **否定句先等畫面畫完：已修。** 新增 `RequestPage.java:81-93` 與 `GuestFrame.java:97-106` 兩個 `drawnProblem`。用到的有 `GuestCommonThenSteps.java:194`（四步）、`:239`（底部）、`:258`（評分卡），`RequestsThenSteps.java:209`（訊息框），`ShellThenSteps.java:150`（子頁標題列）與 `:193`（44 點）。44 點那一句在找不到可點的東西時失敗（`:197`）。`不顯示進度` 另查狀態標籤以外看不到任何步名（`GuestCommonThenSteps.java:201-207`）。
6. **進入層兩句「過程中…」：已修。** `steps/guest/entry/EntryThenSteps.java:78` 與 `:103` 先斷言 `seenOnThisTab()` 不是空的。
7. **比 dsl 寬的檢查：已修。** 圖示的部分是 `pages/guest/RequestIcons.java` 加上 `RequestsPage.java:61-76`，以後端 `type` 對到原型的 path（`RequestsThenSteps.java:119-140`、`:195-199`）。房號膠囊不能有圖示（`HomeThenSteps.java:104`）；即將開放那一列右端不能有箭頭（`:253`）；住宿指南每列都要有箭頭（`GuidePage.java:38`）。四步改看圓點，也要和 `aria-current` 一致（`RequestPage.java:43-67`、`:162-175`）。拿原型 `ui/guest.css:417-431` 與 `ui/guest.js:493` 對過：打勾的圓點裡有勾勾 `svg`，實心的底色和卡不同，空心的底色和卡相同，照原型做會分得出三種。
8. **page object 依賴的結構：已修。** 這些結構搬進了 `ui/ui-plan.md`「鍵盤與螢幕報讀」，有外框、提示、遮罩、首頁、備品、清單、進度頁、即時更新八條。`steps/guest/realtime/GuestLive.java:79-82` 改用角色 `heading`。
9. **重複 helper：已修。** 語言膠囊只留 `LanguageMenu.capsule()`，`GuestHomePage.languageCapsule` 已刪，首頁頂列那一句也改用它（`HomeThenSteps.java:89-101`）。session 鍵只留 `GuestPhone.java:23`，`EntryGivenSteps.java:77` 引用它。遮罩只留 `GuestFrame.scrimShown`，`RequestPage.anyScrim` 已刪。剩下的 `RequestPage.scrimBehind` 查的是「確認框後面」那一層，要有底色，用途不同，不算重複。`GuestLive.java:85-91` 改用 `frame.toasts()`。`steps/guest`、`pages/guest` 底下已經沒有私有的 `eventually`。

## 新問題

### 1. minor：星星的實心與空心沒有對到圖形，五星那一例必定通過

- 位置：`steps/guest/cancelrate/CancelRateThenSteps.java:181-199`（`五顆星都是空心的`）、`:202-217`（`只有前 {int} 顆星是實心的`）；`pages/guest/RequestPage.java:303-312`（`starShapes`）。
- 問題：兩句都只比「這幾顆是不是同一種圖形」，沒有認哪一種是實心、哪一種是空心。
  - `只有前 5 顆星是實心的`（`取消與評分/評分.feature:38`，例子星等 5）：其餘的星是空清單，只要第 5 顆標了選中、五顆畫成同一種圖形就通過。產品點了星卻沒有換圖，五顆仍是空心，這一句照樣綠。
  - `五顆星都是空心的`（`評分.feature:17`）：五顆一開始就畫成實心也會通過。
  - 星等 1 至 4 的例子能抓到「選了不換圖」，因為前後兩段的圖形必須不同。但前段和後段反過來（前 N 顆空心、其餘實心）照樣會過。
- 違反：`取消與評分/dsl.md` 兩列的 `必查`，一列是「五顆星都是未選的樣子」，一列是「前 N 顆是實心、其餘是空心」。也違反審查重點 4。這和第一輪第 7 件「四步的打勾、實心、空心只看 `aria-current`」是同一類。
- 修法：比照 `RequestIcons`，把原型 `ui/guest.js:499` 用的 `star` 與 `star_fill` 兩個圖形的 path 收成常數。`starShapes` 改成讀 `path` 的 `d`，回傳「實心」「空心」或「認不出」。兩句都逐顆比，`五顆星都是空心的` 要五顆都是「空心」，`只有前 N 顆` 要前 N 顆「實心」、其餘「空心」。`ui/ui-plan.md`「進度頁」那一條的「實心與空心是兩種不同的圖形」要補一句：圖形照原型的 `star` 與 `star_fill`。

## 不擋閘門的後續（純文字，不影響任何一句的判斷）

- `ui/ui-plan.md`「外框」那一條仍寫「第二行「房號 {房號}」」，沒寫房號標籤跟著介面語言（英文「Room 311」）。stepdef 照字面比參數，所以不影響判斷，但 Phase 4 讀 ui-plan 的人會漏掉。
- 根 `dsl.md:85` 句型欄仍是 `"房號 {房號}"`，參數說明卻說這個參數是「整段房號標籤」，讀的人會以為參數只是 `311`。Cucumber 表示式是 `{string}`，程式不受影響。下次 `/dsl-refine` 碰到時可以把句型欄改成 `"{房號標籤}"`。
- `住宿指南/資訊頁內容.feature:4` 的 Rule 說明仍寫「房號 {房號}」，和同檔 `:43` 的英文場景不一致。這只是 Rule 的說明文字，不是步驟。
- `implement-notes.md` 前面兩段舊寫法（返回鈕名稱、扣請求）還留著，靠「T200 品質閘門的修正」第一行宣告取代。可以在那兩段原地改掉。

## 新程式的回歸檢查

- **`WebWorld.whileWaiting` 會不會動到櫃檯：不會。** `WebWorld` 由 picocontainer 每個場景各建一份，清單不跨場景。只有 `GuestRequestLimitStub.install` 會登記，而 `install` 只在 `GuestHooks.prepareGuestScenario` 判斷 URI 在 `guest-web/` 底下時才呼叫。`doWaitingChores` 只有 `GuestFrame` 呼叫，櫃檯的 page object 與 step 都不經過它。`onEveryNewContext` 也一樣，櫃檯場景的清單是空的。實跑結果見下面第 6 點。
- **`@After(order = 10)` 會不會卡住收尾：不會。** Cucumber 的 `@After` 由大到小執行，所以它在 `WebWorld.finishScenario`（`@After(order = 0)`，關 context）之前跑，分頁還開著。`route.fetch()` 在攔截處理程式裡就已經拿到回應，放行時只剩 `fulfill`，不會再等網路。分頁已經關掉時 `PlaywrightException` 會被吞掉（`GuestRequestLimitStub.java:163-169`）。場景中途失敗時，扣著的回應也會在這一步放行。剩下一個缺口：後端連不上、`route.fetch()` 本身丟錯時，那個請求不會被放行或中止，要等 context 關掉才結束。這只發生在環境已經壞掉的時候，不列為問題。
- **`GuestFrame.backControl`：** 名稱用目前介面語言的「返回」整串比對，`button` 與 `link` 取聯集，只算看得到的。原型的寫法認得到。
- **`RequestIcons`：** 它要求產品的圖示 `path` 的 `d` 和原型逐字相同，這一點已經寫進 ui-plan。產品若改用別的圖示庫重畫同一個圖形就會紅，Phase 4 照抄原型的 path 即可。這只是提醒，不算問題。
- **`GuestFrame.eventually`：** 規格寫錯的參數（`IllegalArgumentException`、`IllegalStateException`）立刻丟出，其他讀取錯誤當成還沒好，失敗訊息附上畫面文字。沒看到把真的錯誤吞成「還沒好」的地方。

## 檢查了什麼、怎麼查

1. **編譯**（JDK 25）：`mvn -q -f app/bdd-support/pom.xml install -DskipTests` 與 `mvn -q -f app/web-bdd/pom.xml clean test-compile` 都過。
2. **dry-run**（從 `target/cucumber-reports/frontdesk-web.json` 算）
   - 住客根（`phone`）：236 個場景、1098 個步驟，每一步都有 glue，`undefined` 0、`ambiguous` 0。用到的 glue 方法 183 個，住客套件 172 個，櫃檯共用 11 個。
   - 櫃檯根（`desktop`）：432 個場景、2064 個步驟（含 Background），`undefined` 0、`ambiguous` 0，沒有任何一步落到 `steps.guest`。
3. **Given 不經 `POST /guest/requests` 種資料**：grep `guest/requests` 與 `GUEST_CREATE`。建立需求的呼叫只出現在 `GuestBackendSpikeTest`（量測用的 spike，pom 排除在平常的 `mvn test` 之外）與替身的攔截；`BackendChannel` 只有評分。
4. **不等 10 分鐘、不靠真的限流器**：最長的等待是 `SupplyWhenSteps` 的 20 秒上限，其次是 `GuestLive` 連線的 15 秒與 `EntryWhenSteps` 的 10 秒。`429` 全部由替身或攔截產生。
5. **定位方式**：`data-testid`、`getByTestId` 0 處。
6. **實跑**（一次只跑一支 `run-feature.sh`）
   - 櫃檯 `即時更新/現場變化即時跟上.feature`（`desktop`）：11 個場景全過，結束碼 0，約 21 秒。
   - 住客 `備品補充/送出鈕與成功頁.feature:108`（`phone`，就是「後端回覆很慢」那一例）：紅在 Given，`AssertionError: 備品補充頁沒有畫完`，原因是產品的備品頁還沒做（Phase 4）。這屬於 Phase 3 允許的紅。收尾正常，31 秒結束，腳本自己起的後端與開發伺服器都關掉了。因為紅在扣回應之前，扣回應與放行的路徑這次沒有實際跑到。
7. **DSL 忠實度**：再抽 18 句，避開第一輪點名過的那幾句。
   - 首頁 3 句：問候是、畫面沒有任何變化、首頁沒有進度卡。
   - 取消與評分 5 句：畫面上沒有任何確認框、畫面頂端浮出提示、五顆星都是空心的、只有前 N 顆星是實心的、評分卡上沒有留言欄。
   - 住宿指南 2 句：列上沒有圖片時間地點、內文是空白的。
   - 備品補充 3 句：每一列的提示都是、加號不能按、後端送出的順序是。
   - 共用外框 2 句：畫面上沒有語言選單、按鈕仍釘在頁底。
   - 即時更新 2 句：沒有每 4 秒定時重讀、這段期間住客沒有按任何東西。
   - 設備報修 1 句：補充說明裡仍是原本的 200 個字。
   - 不合的寫在新問題第 1 件，其餘照 `必查` 做。另外看了兩處可能空泛通過的地方，判斷不成立：`資訊頁的內文是空白的` 在 `main` 不存在時會過，但頁標題與內文來自同一份 `GET /guest/home`，標題出現時內文已經在同一次畫面更新裡；`畫面上沒有任何確認框` 沒有先等畫完，但前置一定已經開著確認框。
8. **truth 修改的一致性**
   - 根 `dsl.md:85` 的參數說明是房號標籤跟著介面語言，`資訊頁內容.feature:43` 用 `"Room 311"`，中文場景 `子頁標題列.feature:12,26`、`資訊頁內容.feature:15,55`、`進度頁內容與四步副文.feature:13` 用 `"房號 311"`，stepdef 照字面比兩行，三方一致。
   - `住宿指南/dsl.md:5` 的模組說明已同步，`共用外框/dsl.md`「畫面上沒有子頁的標題列」的返回鈕也改成跟著語言。這一句只用在中文場景，所以「房號 」開頭的檢查不受影響。
   - `truth-delta.md` 有三列 MODIFY：「返回」跟著語言、清單同一刻不分先後、房號標籤跟著語言。另外 `資訊頁內容.feature` 那一列 ADD 的說明也同步改了。文字上的小落差列在上面的後續。
9. **產品碼外洩**：以第一輪報告的時間（12:55）為界，`find -newer` 在 `app/web/src` 底下沒有新改動，`git status tms/` 是空的。`specs/truth` 底下只有指揮官改的三份 `dsl.md`，其餘新改的檔案都在 `app/web-bdd` 的住客套件與 `support/WebWorld.java`。

# 第三輪

ISSUES: 0。T200 打勾。

第二輪剩下的 1 件（星星的實心與空心沒有對到圖形）已修，修的人另外把首頁進度卡的四格進度條也改成逐格認顏色。這一輪自己寫了一支咬合測試，用的是測試碼本身的常數與分類方法，在原型上跑：照原型畫的全部通過，故意畫錯的 17 種全部失敗。編譯與兩個根的 dry-run 都乾淨，產品碼與後端沒有新改動。

## 第二輪那 1 件逐條確認

- **每顆星各自分類：是。** `pages/guest/StarShape.java` 把原型 `ui/guest.js:19` 圖示表的 `star_fill` 與 `star` 收成兩個常數，逐字相同（空心是實心的外框再接內框那一段 `M7.38…`）。`StarShape.of` 只在一顆星裡剛好一條 `path`、`d` 等於其中一個常數時回「實心」或「空心」，其餘一律「認不出」。`RequestPage.starShapes`（`:303-313`）逐顆取名稱「{n} 顆星」的 radio，同名的不是剛好一個時給空清單，也算認不出。
- **兩句要的是整串圖形：是。** `CancelRateThenSteps.allStarsEmpty`（`:182-200`）要看得到五顆、沒有任何一顆選中、五顆依序是 `StarShape.pattern(0)`（五顆空心）、字樣「點星星評分」。`firstStarsFilled`（`:203-215`）要選中的剛好是第 N 顆，五顆依序是 `pattern(N)`（前 N 顆實心、其餘空心）。`pattern` 裡不會有「認不出」，比對是整串 `equals`，所以認不出、少一顆、多一條 path 都不可能通過。兩句分別對上 `取消與評分/dsl.md:33` 與 `:34` 的 `必查`。
- **首頁進度條逐格分類：是。** `GuestHomePage.progressBars`（`:217-220`）取卡裡沒有子元素、高 2 至 6 點、寬 16 點以上、和最左一格同一條高度的長條，由左到右讀底色。`BarLook.of`（`:182-188`）只認 `brand-primary` `rgb(12,118,128)` 為亮、`fill` `rgba(118,118,128,0.12)` 為不亮，其餘「認不出」。`HomeThenSteps.progressCardLitUpTo`（`:173-200`）先要卡剛好一張、四個步名清單的 `aria-current` 落在該步，再要四格整串等於 `BarLook.upTo(步)`，最後比說明。找不到長條時是空清單，不等於四格，失敗。這對上 `首頁/dsl.md:29` 的 `必查`。
- **ui-plan 跟著補了：是。** `ui/ui-plan.md:428`「進度頁」寫明每顆星一個 `svg`、一條 `path`，`d` 照原型 `star_fill`／`star` 逐字相同；`:425`「首頁」寫明進度條每一格的尺寸、並排方式與兩種底色。

## 咬合測試

自己寫的拋棄式 Java（`/Users/Caresys/.claude/jobs/bf2b4d32/tmp/verify3/`），放在 `pages.guest` 套件裡，直接呼叫 `RequestPage.STAR_PATHS`、`StarShape.of`／`pattern`、`GuestHomePage.PROGRESS_BAR_COLOURS`、`BarLook.of`／`upTo`；星星的定位照抄 `RequestPage.star(n)`，選中照抄 `checkedStars`。用 `python3 -m http.server` 開 `ui/`，WebKit 393 × 852 觸控，和 `phone` 設定相同。改圖的方式是在原型畫好之後換掉 `path` 的 `d` 或底色的 `data-on`。

| 情況 | 檢查的句子 | 該有的結果 | 實際 |
| --- | --- | --- | --- |
| 原型，還沒點星 | 五顆星都是空心的 | 通過 | 通過 |
| 還沒點星，五顆畫成實心 | 五顆星都是空心的 | 失敗 | 失敗 |
| 原型，點第 3 顆 | 只有前 3 顆星是實心的 | 通過 | 通過 |
| 原型，點第 3 顆 | 只有前 2 顆星是實心的 | 失敗 | 失敗 |
| 點第 3 顆，五顆畫成空心 | 只有前 3 顆 | 失敗 | 失敗 |
| 點第 3 顆，畫成 4 顆實心 | 只有前 3 顆 | 失敗 | 失敗 |
| 點第 3 顆，畫成 2 顆實心 | 只有前 3 顆 | 失敗 | 失敗 |
| 點第 3 顆，前 3 空心、後 2 實心 | 只有前 3 顆 | 失敗 | 失敗 |
| 原型，點第 5 顆 | 只有前 5 顆星是實心的 | 通過 | 通過 |
| 點第 5 顆，五顆畫成空心（第二輪必定通過的那一例） | 只有前 5 顆 | 失敗 | 失敗 |
| 點第 5 顆，`d` 換成別的圖形 | 只有前 5 顆 | 失敗 | 失敗 |
| 點第 5 顆，`svg` 拿掉 | 只有前 5 顆 | 失敗 | 失敗 |
| 還沒點星，每顆兩條 `path` | 五顆星都是空心的 | 失敗 | 失敗 |
| 原型首頁，`triage` 那張卡 | 進度條亮到「已送出」 | 通過 | 通過 |
| 四格都不亮 | 亮到「已送出」 | 失敗 | 失敗 |
| 前兩格亮 | 亮到「已送出」 | 失敗 | 失敗 |
| 前兩格亮 | 亮到「已受理」 | 通過 | 通過 |
| 只有第二格亮 | 亮到「已受理」 | 失敗 | 失敗 |
| 四格全亮 | 亮到「處理中」 | 失敗 | 失敗 |
| 長條全部拿掉 | 亮到「已送出」 | 失敗 | 失敗 |
| 第一格換成別的顏色 | 亮到「已送出」 | 失敗 | 失敗 |

合計：該通過的 5 例全部通過，該失敗的 17 例全部失敗，0 例不符。原型首頁的進度卡沒有 `ol`／`aria-current`，所以這裡只咬進度條那一段；步名那一段是第一輪就確認過的結構，ui-plan「首頁」已要求產品照做。

## 不擋閘門的後續

- 進度條的兩種底色寫死淺色主題的值。Playwright 預設淺色，產品若日後跟著系統切深色，這句會紅而不是誤綠，所以不算問題；要測深色時再加一組值。
- 首頁卡的「四個步名亮到該步」靠 `aria-current` 判斷，沒有看步名的字重或顏色。進度條已經逐格看畫面，ui-plan 也寫了結構，維持現狀。
- 修的人順手改了 `住宿指南/資訊頁內容.feature:4` 的 Rule 說明（房號標籤跟著介面語言），也就是第二輪列的後續之一。只動說明文字，不動步驟，與同檔 `:43` 的英文場景一致。

## 檢查了什麼、怎麼查

1. **編譯**（JDK 25）：`mvn -q -f app/bdd-support/pom.xml install -DskipTests` 與 `mvn -q -f app/web-bdd/pom.xml clean test-compile` 都過，結束碼 0。
2. **dry-run**（從 `target/cucumber-reports/frontdesk-web.json` 算）
   - 住客根（`phone`）：236 個場景、1098 個步驟，`undefined` 0、`ambiguous` 0；glue 方法 183 個，住客套件 172 個。
   - 櫃檯根（`desktop`）：432 個場景、2064 個步驟，`undefined` 0、`ambiguous` 0；沒有任何一步落到 `steps.guest`。
3. **第二輪之後動過的檔案**（`find -newer review-t200.md`，排除 `target`）：`GuestHomePage.java`、`RequestPage.java`、`StarShape.java`、`CancelRateThenSteps.java`、`HomeThenSteps.java`、`ui/ui-plan.md`、`implement-notes.md`、`住宿指南/資訊頁內容.feature`（只改 Rule 說明）。
4. **產品碼外洩**：`app/web/src` 與 `app/web/vite.config.ts` 在第一輪的時間（12:55）之後沒有任何檔案被改，`git status` 列出的改動是 Phase 1–2 的（最晚 10:43）。`git status tms/` 是空的。
