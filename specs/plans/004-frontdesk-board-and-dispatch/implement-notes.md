# Implement Notes: 004-frontdesk-board-and-dispatch

實作過程中要留給後面的人知道的事，依題號記。

## T002 `app/bdd-support/`

- 不經 `app/bdd/run-*.sh`、直接下 `mvn -f app/bdd/pom.xml …` 的人，要先跑一次 `mvn -q -f app/bdd-support/pom.xml install`，否則找不到 `com.athena:app-bdd-support`；改過 `app/bdd-support/` 之後也要重跑。
- `app/bdd/lib/env.sh` 被 source 時會直接執行這一步 install，所以三支 `run-*.sh` 不用另外處理。

## T003 `app/web-bdd/`

- 直接下 `mvn -f app/web-bdd/pom.xml …` 之前，要先跑過一次 `mvn -q -f app/bdd-support/pom.xml install`（同 T002）。
- 瀏覽器要自己裝一次：`mvn -q -f app/web-bdd/pom.xml exec:java -Dexec.args="install chromium webkit"`，裝在 Playwright 預設快取（`~/Library/Caches/ms-playwright/`）。跑測試時不會自動下載（surefire 設了 `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`）；改 `playwright.version` 之後要重跑這條。
- 瀏覽器設定用 `-Dweb.profile=desktop|ipad` 選，不給是 `desktop`；定義在 `WebProfile`。`ipad` 目前只設 WebKit、1180 × 820、`hasTouch`，沒有設 `isMobile` 與 UA，要不要補由 T005 的結論決定。
- pom 已排除 surefire 直接叫起的 Cucumber engine，只由 `RunFrontdeskWebFeaturesTest` 跑一遍；指令不必再加 `-Dsurefire.excludeJUnit5Engines=cucumber`。
- dry-run 整個目錄時 surefire 最後的總計列是 240，不是 417：它把同名案例併在一起算，實際載入的是 27 份 feature、417 個案例（看 `in …RunFrontdeskWebFeaturesTest` 那一列或 `target/cucumber-reports/frontdesk-web.json`）。

## T004 拖曳套件選型

**結論：用 `@dnd-kit/react` 0.5.3。** 已裝進 `app/web/` 並鎖版本；同時把它底層的 `@dnd-kit/dom` 0.5.3 列成直接相依，因為設定「移動幾 px／按住多久才起拖」要用的 `PointerActivationConstraints` 只從 `@dnd-kit/dom` 匯出，`@dnd-kit/react` 沒有轉出來。`@dnd-kit/core` 沒有留在 `package.json`。這個結論要回交 `/technical-research`，把 `specs/truth/techstack.md`「櫃檯 Web 拖曳派單」那一列改成確定的套件（Scope Note 第 15 條）。

**怎麼試的。** 同一個最小畫面（一張可拖的卡片、房務與工務兩個拖放區、看板可左右捲、頁面可上下捲）兩個套件各做一次，起拖條件照 `ui/ui-plan.md`：滑鼠移動超過 6px、手指按住 0.25 秒（容許 5px 抖動）。用 Playwright 1.65.0（與 `app/web-bdd/` 同版、同一份瀏覽器）實際開瀏覽器跑，四種輸入方式：

- 滑鼠，Chromium 1280 × 720 與 WebKit 1280 × 720。
- 滑鼠，WebKit 1180 × 820 開觸控（`ipad` 那組設定，但輸入仍是滑鼠）。
- 真的觸控，Chromium 1180 × 820 開觸控，用 CDP 的 `Input.dispatchTouchEvent` 送手指按下、移動、離開。頁面收到的是瀏覽器自己產生的 `pointerType=touch` 指標事件，瀏覽器也會照真的手勢決定要不要捲動。
- 假的觸控，WebKit 1180 × 820 開觸控，在頁面裡用 `dispatchEvent` 送 `pointerType=touch` 的指標事件。Playwright 在 WebKit 上只有「點一下」的觸控 API，沒有拖曳用的。

每一種都驗四個動作：起拖（卡片原位變半透明、出現跟著走的卡片、狀態字變「拖曳中」）、滑進拖放區（說明文字從「放進房務待認領」變「放開就完成派單」，另一區不變）、在拖放區放開（結果變「已派給房務」、卡片出現在房務欄）、在拖放區以外放開（結果維持「尚未派單」、卡片留在待派單欄）。另外驗：滑鼠拖曳中按 Esc 取消、滑鼠只移 4px 不起拖而算點一下、手指短按算點一下、手指不按住直接上下滑或左右滑是捲動而不是起拖。

**每個候選的實測結果。**

| 做法 | 滑鼠（三組瀏覽器設定） | 真的觸控（Chromium） | 手指直接滑能不能捲動 | 假的觸控指標事件（WebKit） |
| --- | --- | --- | --- | --- |
| `@dnd-kit/react` 0.5.3，一個 `PointerSensor` | 四個動作、Esc、4px 不起拖全過 | 四個動作全過；短按是點一下 | 可以（上下、左右都捲得動，沒有誤起拖） | 直接送會被套件取消；測試先把 `setPointerCapture` 換成空函式後四個動作全過 |
| `@dnd-kit/core` 6.3.1，`PointerSensor`，卡片不設 `touch-action: none` | 全過 | **失敗**：按住起拖後一移動，瀏覽器就當成捲動並送出 `pointercancel`，拖曳被取消 | 可以 | 四個動作全過 |
| `@dnd-kit/core` 6.3.1，`PointerSensor`，卡片設 `touch-action: none` | 全過 | 四個動作全過 | **不行**：手指落在卡片上就捲不動看板 | 四個動作全過 |
| `@dnd-kit/core` 6.3.1，`MouseSensor`＋`TouchSensor`（對照組） | 全過 | 四個動作全過 | 可以 | 完全沒反應（它不聽指標事件） |

三組瀏覽器設定下，Playwright 的 `page.mouse` 送出的輸入，頁面都收到成瀏覽器產生的 `pointerdown`、`pointermove`、`pointerup`（`pointerType=mouse`），兩個套件都由它起拖、放下。也就是「測試工具的滑鼠輸入會被當成 Pointer Events 收到」這件事成立。

**為什麼選 `@dnd-kit/react`。**

- 它是唯一一個同時做到這三件事的做法：滑鼠與手指走同一套指標事件的程式、手指按住才起拖、手指直接滑照常捲動看板。這正是 `ui/ui-plan.md` 要分開的三種手勢（短按開詳情、直接滑捲動、按住再拖派單）。
- `@dnd-kit/core` 要做到同樣的手感，得改用滑鼠事件與觸控事件兩個感應器，等於不走 Pointer Events，和 `techstack.md` 的方向不一致；用它的 `PointerSensor` 則要在「按住後拖不動」與「卡片上捲不動」之間二選一。
- `@dnd-kit/core` 另有兩個小麻煩：`PointerSensor` 只能設一種起拖條件，而且兩個感應器不能同時掛在 `onPointerDown`（後掛的蓋掉先掛的，實測滑鼠整個拖不動），要自己寫子類別依指標種類換條件。`@dnd-kit/react` 的起拖條件本來就可以依指標種類給，預設值也剛好是觸控按住 250 毫秒。
- `@dnd-kit/react` 的相依宣告支援 React 19，2026-10-31 還在發版；`@dnd-kit/core` 最後一版是 2024-12-05。
- 代價：`@dnd-kit/react` 還沒到 1.0，介面可能再變。版本鎖死在 0.5.3，升版時要重跑這一頁的四個動作。

**給 T005 的注意事項。**

- spike 畫面留在 `app/web/spikes/dnd/`（`index.html`、`main.tsx`、`log.ts`、`spike.css`），開發伺服器起來後開 `/spikes/dnd/index.html`。它不在 `tsconfig.json` 的檢查範圍、不會被打包、`src/` 沒有引用。落選的 `@dnd-kit/core` 版本沒有留在專案裡（它的套件沒有裝，留著會是壞的程式）。
- 可用的 `data-testid`：`card`、`overlay-card`（拖曳中跟著走的那一張）、`zone-housekeeping`、`zone-engineering`、`zone-housekeeping-hint`、`zone-engineering-hint`、`drag-state`（「平時」／「拖曳中」）、`result`（「尚未派單」／「已派給房務」／「已派給工務」）、`column-pending`、`column-housekeeping`、`column-engineering`、`assigned-card`。拖放區只在拖曳中才存在，要起拖之後才量得到位置。
- `window.__spike` 記下頁面實際收到的事件：`pointer`（種類、`pointerType`、是不是瀏覽器產生的）、`other`（滑鼠、觸控、點擊事件）、`lib`（套件回報的 `dragstart`、`dragover:<部門>`、`dragend:<部門或 none>:<dropped 或 canceled>`）。要確認「套件收到的是 Pointer Events」看這裡。
- 滑鼠：按下後要有中間的移動（實測每段分 8 步）才會起拖與觸發滑進拖放區；一步跳到終點沒有試過。
- 觸控在 WebKit 上沒有現成的拖曳 API。實測可行的一條路是在頁面裡送 `pointerType=touch` 的 `PointerEvent`（按下、等超過 250 毫秒、分段移動、放開），但要先用 `addInitScript` 把 `Element.prototype.setPointerCapture` 與 `releasePointerCapture` 換成空函式：套件起拖時會對那個指標呼叫 `setPointerCapture`，假的指標沒有對應的真實指標，呼叫會丟錯，套件就把拖曳取消（實測 `dragend:none:canceled`）。這樣送的事件不是瀏覽器產生的，瀏覽器不會跟著捲動，所以驗不到「拖曳與捲動互搶」。
- 另一條路是 Chromium 開觸控用 CDP 的 `Input.dispatchTouchEvent`，這是真的觸控，但引擎不是 WebKit，和 `ipad` 那組設定不同。要用哪一條、還是走退路（Scope Note 第 14 條），由 T005 決定；以上是用 Node 版 Playwright 試的，Java 版還沒跑過。

**給之後實作（Phase 4-W24）的注意事項。**

- 起拖條件的寫法照 spike 的 `main.tsx`：`PointerSensor.configure({ activationConstraints(event) { … } })`，依 `event.pointerType` 回 `PointerActivationConstraints.Delay`（觸控）或 `Distance`（滑鼠）；`PointerActivationConstraints` 從 `@dnd-kit/dom` 匯入。
- 放開時在 `onDragEnd` 看 `event.canceled` 與 `event.operation.target?.id`。在拖放區以外放開是 `canceled=false`、沒有 `target`；按 Esc 是 `canceled=true`，而且 `target` 可能還留著拖放區，**一定要先看 `canceled`**，否則 Esc 會被當成派單。
- 卡片不要設 `touch-action: none`，設了手指就不能在卡片上捲動看板。spike 的卡片有 `user-select: none` 與 `-webkit-touch-callout: none`，用來擋長按選字與 iOS 的長按選單，真機上有沒有效還沒驗。
- 跟著游標走的那一張用 `DragOverlay`；原位那一張用 `useDraggable` 回的 `isDragSource` 變半透明。
- 滑鼠移動不到 6px 就放開、手指短按，瀏覽器都照常送 `click`，可以直接拿來開詳情面板。

**沒有驗到的。**

- 真機 iPad 的 Safari：按住 0.25 秒順不順手、拖曳與捲動會不會互搶、長按會不會跳出系統選單。Playwright 的 WebKit 送不出真的觸控拖曳，這部分只在 Chromium 驗過，仍要真機人工驗一次。
- 拖到看板邊緣自動捲動（套件內建，沒有試）、其他狀態的卡片拖不動、拖放區跟著泳道換位置：這些是產品行為，不在這個 spike 裡。

## T005 觸控拖曳自動化

**結論：兩組設定的拖曳都能由 Playwright for Java 驅動，不走退路。** `desktop` 用 `page.mouse()`；`ipad` 用「在頁面裡送 `pointerType=touch` 的指標事件」，這樣才走得到手指那一條起拖條件（按住 0.25 秒）。`WebProfile` 的 `ipad` 不補 `isMobile` 與 UA，`app/web-bdd/` 這一題沒有改任何檔。瀏覽器時鐘用 `page.clock()` 撥得動，「等 N 分」跟著變。這兩個結論要回交 `/technical-research`，把 `specs/truth/techstack.md`「櫃檯 Web 驗收的視窗與輸入」那一列的「由 spike 確認」改成確定的寫法（Scope Note 第 15 條）。

要先講清楚的限制：Playwright 的 WebKit 送不出「瀏覽器自己產生的」觸控拖曳。`ipad` 那一組驗到的是「套件收到觸控種類的指標事件後，按住、移動、放開的邏輯對不對」，驗不到瀏覽器對真手勢的反應（拖曳與捲動互搶、長按選單）。那一塊仍是真機 iPad 人工驗收。

**怎麼試的。** 用 `app/web-bdd/` 的 classpath（Playwright for Java 1.65.0、`WebProfile` 的 `launch` 與 `contextOptions`）寫一支一次性的 Java 程式，開 T004 的 `/spikes/dnd/index.html`。每一條路都驗四個動作：起拖（狀態字變「拖曳中」、跟著走的卡片出現、原位卡片變半透明）、滑進拖放區（房務的說明變「放開就完成派單」、工務的不變）、在拖放區放開（結果「已派給房務」、卡片在房務欄）、在拖放區以外放開（結果維持「尚未派單」、卡片留在待派單欄）；並從 `window.__spike` 看頁面收到的事件種類與套件回報的 `dragstart`／`dragover`／`dragend`。

**每條路的實測結果。**

| 做法 | 瀏覽器設定 | 四個動作 | 頁面收到的事件 |
| --- | --- | --- | --- |
| `page.mouse()`：移到卡片、`down`、`move`、`up` | `desktop`（Chromium 1280 × 720） | 全過 | 瀏覽器產生的 `pointerdown`／`pointermove`／`pointerup`，`pointerType=mouse` |
| `page.mouse()` 同上 | `ipad`（WebKit 1180 × 820 觸控） | 全過 | 同上，`pointerType=mouse`（走的是滑鼠「移動超過 6px」那條起拖條件） |
| 頁面裡 `dispatchEvent` 送 `pointerType=touch` 的 `PointerEvent`，先用 `addInitScript` 讓 `setPointerCapture` 略過假指標 | `ipad` | 全過；手指沒按住就移動不會起拖（確實走「按住 0.25 秒」那條） | 不是瀏覽器產生的 `pointerdown`／`pointermove`／`pointerup`，`pointerType=touch` |
| 同上，但不處理 `setPointerCapture` | `ipad` | **失敗**：沒有起拖，套件回報 `dragend:none:canceled` | 同上 |
| CDP `Input.dispatchTouchEvent`（`touchStart`、`touchMove`、`touchEnd`） | Chromium 1180 × 820 開觸控（不是 `ipad` 那組） | 全過 | 瀏覽器產生的 `touchstart`／`touchend` 與 `pointerType=touch` 的指標事件 |
| CDP | `ipad` | **不能用**：`CDP session is only available in Chromium` | — |
| `locator.dispatchEvent("touchstart"／"touchmove")` 送舊式觸控事件 | `ipad` | **不能用**：頁面只收到 `touchstart`，沒有任何指標事件，套件沒反應 | — |
| `locator.tap()`、`page.touchscreen().tap()` | `ipad` | 只能點一下，沒有拖曳用的 API | 瀏覽器產生的 `pointerType=touch` 的 `pointerdown`／`pointerup`，接著 `click` |
| `locator.dragTo()` | `desktop` | **不能用**：拖放區起拖之後才存在，它等不到目標就逾時 | — |

「測試工具送出的滑鼠與觸控輸入，拖曳套件確實當成 Pointer Events 收到」成立：滑鼠在兩組設定下都是瀏覽器產生的指標事件；觸控在 Chromium 用 CDP 是瀏覽器產生的指標事件，在 WebKit 只能由測試自己送指標事件。

**之後 stepdef 要怎麼驅動拖曳。** 依 `WebProfile.touch()` 分兩條，寫在同一個拖曳 helper 裡，stepdef 不必知道差別。

- `desktop`（滑鼠）：`page.mouse().move(卡片中心)` → `page.mouse().down()` → 先小移一段（實測右 40px、下 10px，超過 6px 就好）讓它起拖 → 等拖放區出現、量它的位置 → `page.mouse().move(拖放區中心)` → `page.mouse().up()`。
- `ipad`（觸控）：開 context 時先 `context.addInitScript(...)`，內容是把 `Element.prototype.setPointerCapture`、`releasePointerCapture` 包一層：指標編號是測試專用的那一個（實測用 9001）就直接返回，其他編號照原本的呼叫；`hasPointerCapture` 對這個編號回 `false`。只略過測試的假指標，真的滑鼠與點一下不受影響。然後用 `page.evaluate` 對 `document.elementFromPoint(x, y)` 送 `new PointerEvent(type, { bubbles: true, cancelable: true, composed: true, pointerId: 9001, pointerType: 'touch', isPrimary: true, clientX, clientY, button, buttons })`：`pointerdown`（`button: 0, buttons: 1`）→ 等超過 250 毫秒（實測等 350）→ `pointermove`（`button: -1, buttons: 1`）小移一段 → 等拖放區出現、量位置 → `pointermove` 到拖放區中心 → `pointerup`（`button: 0, buttons: 0`）。
- 為什麼一定要處理 `setPointerCapture`：套件起拖時會對那個指標呼叫它，假的指標在瀏覽器裡沒有對應的真指標，呼叫會丟錯，套件就把拖曳取消。
- 短按開詳情不用假事件：`ipad` 下 `locator.tap()` 送的是瀏覽器產生的觸控指標事件，後面跟著 `click`。
- 要不要補送中間的移動事件：**至少要兩段**。第一段小移讓它起拖（滑鼠要超過 6px；觸控是按住夠久之後的第一個移動），因為拖放區起拖之後才存在；第二段移到拖放區。每一段一步跳到終點就夠（滑鼠 `steps=1`、觸控一個 `pointermove`、CDP 一個 `touchMove` 都實測通過），不必切成很多小步。每段切 8 步也通過，要保守可以切。
- 放開的位置不要貼近視窗邊緣：實測在離底邊 120px 處放開，套件內建的邊緣自動捲動把頁面捲了 44 到 60px。這不影響派單結果，但之後量位置會偏。
- 斷言一律等畫面狀態（拖放區出現、說明文字、結果），不要用固定秒數；上面的毫秒數只是這次實測用的值。

**為什麼 `ipad` 不退回滑鼠。** 滑鼠在 `ipad` 那組也跑得通，但它走的是滑鼠的起拖條件，等於同一段邏輯在兩組設定各驗一次，手指「按住才起拖」那一條完全沒驗到。假的觸控指標事件多一段 `addInitScript`，換到的是觸控那條邏輯在 WebKit 上有自動化保護。滑鼠留作備案：假指標那條哪天因套件升版失效，`ipad` 的拖曳改用 `page.mouse()` 就是 Scope Note 第 14 條的退路，helper 只要換一個分支。

**為什麼不另開一組 Chromium 觸控。** CDP 是唯一送得出真觸控的路，但引擎不是 WebKit，離 iPad 的 Safari 更遠；而且要多一組瀏覽器設定，整包驗收多跑一輪。這次沒有加。之後若要在自動化裡驗「拖曳與捲動互搶」，這是唯一的辦法，Java 的寫法已實測可用：`page.context().newCDPSession(page).send("Input.dispatchTouchEvent", {type, touchPoints:[{x, y}]})`，放開時 `touchPoints` 給空陣列。

**`WebProfile` 的 `ipad` 為什麼不補 `isMobile` 與 UA。** 實測補上 `isMobile` 與 iPad 的 UA 之後，視窗一樣是 1180 × 820，`pointer: coarse` 與 `hover: none` 本來就因為 `hasTouch` 成立，滑鼠與假觸控兩條路的四個動作結果完全一樣。iPad 上的 Safari 預設送的就是電腦版的 UA，和 WebKit 不設 UA 時一致。補了沒有換到任何東西，所以維持 T003 的設定。附帶觀察：WebKit 開 `hasTouch` 後 `'ontouchstart' in window` 是 `true`，但 `navigator.maxTouchPoints` 是 0；產品不要用 `maxTouchPoints` 判斷是不是觸控裝置。

**瀏覽器時鐘怎麼撥。** 在一個最小畫面（`app/web/spikes/clock/`：記下起算時刻，每秒用 `Date.now()` 重算「等 N 分」）上，`desktop` 與 `ipad` 兩組結果相同：

| 呼叫 | 畫面結果 | 計時器 |
| --- | --- | --- |
| 開頁面前 `page.clock().install(setTime(起點))` | 頁面看到的現在時刻是起點，「等 0 分」 | 之後時間照真實速度走 |
| `page.clock().fastForward("10:00")` | 「等 10 分」 | 到期的計時器只補跑一次 |
| `page.clock().runFor("10:00")` | 「等 20 分」 | 這 10 分鐘內每一次計時都真的跑（每秒一次的計時器跑了 600 次） |
| `page.clock().pauseAt(時刻)` | 跳到那個時刻並停住，「等 N 分」跟著變 | 全部停住，直到 `resume()` |
| `page.clock().setSystemTime(時刻)` | 不會馬上變，等下一次計時器真的跑（一秒內）才變 | 不補跑 |
| `page.clock().setFixedTime(時刻)` | 一秒內變成該時刻，之後不再前進 | 照真實速度跑 |
| 不 `install`，直接 `setFixedTime` | 同上，也有效 | 照真實速度跑 |

- 建議寫法：情境一開始、開頁面之前 `install` 到和後端同一個起點；DSL 的「時間來到…」在撥後端時鐘的同時，對瀏覽器用 `fastForward`（相對量）。`fastForward` 之後時鐘繼續走，畫面上的計時器與拖曳都正常。
- **時鐘停住時拖曳會壞。** `pauseAt` 之後沒有 `resume()` 就拖：滑鼠起得了拖，但滑進拖放區沒反應、放開不會派單；觸控連起拖都不會（按住 0.25 秒的計時器不會到期）。四條拖曳路都實測如此。`install` 後照常走、`pauseAt` 之後 `resume()`、`setFixedTime` 這三種狀態下拖曳都全過。所以 stepdef 不要讓時鐘停在 `pauseAt`；要畫面時間固定不動就用 `setFixedTime`。
- `runFor` 會把期間內每一次計時都跑一遍。產品若有定時重抓資料，撥 20 分鐘就會連打很多次後端；除非情境就是要驗中間每一次，否則用 `fastForward`。
- 撥到絕對時刻時注意整分鐘邊界：頁面的起算時刻會帶幾十毫秒（實測 65 毫秒），撥到剛好 45 分整的那一刻，畫面是「等 44 分」。用相對量 `fastForward`，或把絕對時刻多給一秒。

**這一題動了什麼。** 新增 `app/web/spikes/clock/index.html` 與 `main.ts`（時鐘的最小畫面，和 `spikes/dnd/` 一樣不被 `src/` 引用、不進打包）。`app/web-bdd/` 沒有改。試驗用的 Java 程式是一次性的，沒有留在專案裡；之後的 helper 照上面「怎麼驅動拖曳」那一段寫。

**沒有驗到的。**

- 真機 iPad 的 Safari：按住 0.25 秒的手感、拖曳與捲動會不會互搶、長按會不會跳系統選單。自動化在 WebKit 上驗不到，仍列真機人工驗收。
- 假的觸控指標事件不會讓瀏覽器捲動，所以 `ipad` 那組驗不到「手指直接滑是捲動而不是起拖」；T004 只在 Chromium 用 CDP 驗過。
- 只在無頭模式、macOS 上跑過；沒有試有畫面的模式與 CI 環境。
- 沒有接真的產品畫面與後端：瀏覽器時鐘與後端時鐘一起撥、時鐘被 `install` 之後事件串流的心跳與重連計時，要到 T006 與 Foundational 才看得到。
- 拖到邊緣自動捲動後再放到拖放區、鍵盤操作拖曳，沒有試。
- 測試替換掉指標 capture 之後，下列缺陷自動化看不到，一併列入真機人工驗收：真觸控的 capture 與瀏覽器自己送出的 `pointercancel`、手指滑出視窗或在邊緣自動捲動時放開、遮罩層或 `pointer-events` 擋住觸控、多指觸控。假事件是直接送到指定元素，不經過瀏覽器判斷「手指底下是哪個元素」，而且 `isTrusted` 是 `false`，產品程式不要依賴它。
- `@dnd-kit/react` 升版後，除了 T004 的四個動作，也要重跑這一節的假觸控路線（替換 capture 的做法依賴套件內部行為）。
- 「離底邊 120px 放開時自動捲動 44–60px」這個數字只有實作時量過一次，驗收沒有重跑。

## T006 事件串流連線試驗

**結論：「瀏覽器 → Vite 代理 → 後端串流」這條路走得通，用 `fetch` 讀串流可行，但開發伺服器的代理要補一段設定，否則後端中途消失時畫面不會知道。** `init`、`resync`、每 15 秒的 `heartbeat`、`task` 都照順序收到；`task` 從住客送出到瀏覽器收到，實測 0.06 到 1.74 秒；後端停掉再啟動，補了代理設定之後瀏覽器在 0.1 秒內發現斷線，後端回來後約 1 秒內重新連上。代理不需要關緩衝，也不需要調逾時。另外量到兩件後端的既有狀況，之後的串流客戶端與測試步驟都要顧到：後端剛啟動的頭幾秒內發生的事件不會出現在串流上；後端正常關機時會先安靜 30 秒才斷線。

**怎麼試的。** 後端用 repo 既有的本機做法起在 8080（`docker-compose.yml` 的 Postgres 5433 與 Kafka 9092；等同 `TMS_DB_URL=jdbc:postgresql://localhost:5433/tms ./tms/mvnw -f tms/pom.xml spring-boot:run`），`app/web/` 的開發伺服器起在 5199。資料用 `app/bdd-support` 的 `BackendChannel` 在「測試飯店」建：一個 `role=frontdesk`、`dept=frontdesk` 的帳號（`createStaff`）、今天入住的訂房（`insertTodayReservation`），再打既有端點 `POST /api/v1/guest-sessions/scan` 建住客的連線憑證。瀏覽器用 `app/web-bdd/` 的 Playwright for Java 1.65.0 開（`desktop`＝Chromium、`ipad`＝WebKit 兩組都跑），載入最小頁面 `/spikes/stream/index.html`，頁面裡用 `fetch('/staff/stream', { headers: { Authorization: 'Bearer …' } })` 讀回應串流並自己切事件。住客的需求由測試程式直接打後端 `POST /api/v1/guest-requests`（自由文字、沒有命中分類規則，所以進待派單：回應是 `state=triage`、`dept=frontdesk`）。測試程式與瀏覽器在同一台機器，「送出」取測試程式送出請求前的時刻，「收到」取頁面讀到那一則事件的 `Date.now()`。

**每一項的實測結果。**

| 項目 | 結果 | 數字 |
| --- | --- | --- |
| 連上後依序收到 `init`、`resync` | 通過（兩組瀏覽器） | 發出請求到收到 `init` 43–118 毫秒；`resync` 與 `init` 同一批到（差 0 毫秒），順序 `init` 在前。內容：`init {"stream_pos":0}`、`resync {"reason":"connected","refetch":"/api/v1/pool"}` |
| 每 15 秒的 `heartbeat` | 通過 | 相鄰間隔 14.75–15.42 秒（連續 22 個）。第一個 `heartbeat` 不是連上後 15 秒，而是 0.4–14 秒不等：後端是全部連線共用一個每 15 秒的節拍，不是每條連線各自計時 |
| 長時間連著 | 通過 | 經代理連著 316 秒、22 個 `heartbeat`、沒有斷過。沒有測到 30 分鐘 |
| 住客送需求後收到 `task` | 通過 | `desktop` 8 次：177、1712、923、1442、91、881、395、1700 毫秒（最小 91、中位 923、最大 1712）；`ipad` 4 次：1739、796、63、151 毫秒。事件內容例：`{"stream_pos":1,"task_id":"…","seq":1,"type":"created","occurred_at":"…"}`，`task_id` 與建單回應的 `id` 相符 |
| 後端被強制結束（`kill -9`） | 補代理設定前**不通過**；補了之後通過 | 補之前：頁面完全沒有發現，`fetch` 既不結束也不報錯，直到自己的「35 秒沒資料」計時到期。補之後：55–67 毫秒內 `fetch` 的讀取丟出錯誤（Chromium 是 `TypeError: network error`，WebKit 是 `TypeError: Load failed`） |
| 後端停著時重連 | 如預期失敗 | 開發伺服器的代理回 `502`、`content-type: text/plain`，每次幾毫秒就回來 |
| 後端啟動後重新連上 | 通過 | 頁面每 1 秒重試一次；後端啟動約 5.5–5.9 秒，健康檢查通過後 0.4 秒內就收到新的 `init` 與 `resync`（`stream_pos` 從 0 重新算） |
| 重連後收得到 `task` | 重連後**馬上**送的那一張收不到；6 秒後再送的收得到 | 馬上送（連上後 0.2 秒）：15 秒內沒有 `task`，試了 3 次都一樣。等 6 秒再送：1129 毫秒收到 |
| 後端正常關機（`kill -TERM`） | 通過，但很慢 | 送出訊號後 30.5 秒頁面才發現斷線；這 30 秒內連線開著但沒有任何資料（連 `heartbeat` 都停了） |
| 後端卡住、連線沒斷（`kill -STOP`） | 只能靠前端自己計時發現 | 35 秒沒資料的計時在卡住後 34.8 秒到期；期間 `fetch` 沒有結束也沒有報錯。後端恢復後同一條連線繼續收到 `heartbeat` |
| 後端自己把連線收掉（把連線上限暫時設成 40 秒來試；正式的預設是 30 分鐘） | 通過 | 連上後 40.6 秒 `fetch` 的讀取丟出錯誤（不是正常結束），1 秒後重連成功 |

延遲的數字和 `research.md` 決策 8 的推算一致：後端每 2 秒才把待發送的事件送出一次，所以延遲落在 0 到 2 秒之間、看送出的時刻離下一次轉送多遠，加上不到 0.1 秒的傳遞時間。代理沒有增加看得出來的延遲。

**代理設定與理由。** `app/web/vite.config.ts` 的 `/staff/stream` 加了一段 `configure`：後端那一頭的回應沒有正常收尾就斷了（`proxyRes` 的 `close` 事件到了而 `proxyRes.complete` 是 `false`），就把瀏覽器這一頭的回應也切斷（`res.destroy()`）。

- 為什麼需要：Vite 8.3.8 的代理在後端行程中途消失時，不會把瀏覽器這一頭關掉。用 `curl` 對照過：直接連 8080 的那一條在後端被結束後 1 秒內斷線；經代理的那一條一直掛著，等到 `curl` 自己 40 秒逾時。加了這段之後，經代理的那一條也在 1 秒內斷線。
- 不需要關緩衝：`init` 在回應標頭之後 5 毫秒內到，`heartbeat` 的間隔與後端送出的一致，`task` 的延遲沒有多出來。代理轉回來的標頭是 `content-type: text/event-stream`、`cache-control: no-cache, no-store…`、`Transfer-Encoding: chunked`，沒有壓縮。
- 不需要調逾時：連著 5 分多鐘沒有被代理切斷。
- 這段設定只管開發伺服器。正式環境前面放什麼代理還沒定，它會不會緩衝、後端消失時會不會關連線，要在那個環境再驗一次。

**給之後串流客戶端的注意事項。**

- 讀取的寫法可照 `app/web/spikes/stream/main.ts`：`response.body.pipeThrough(new TextDecoderStream()).getReader()`，以空行切一則事件，每則有 `event:` 與 `data:` 兩行（後端送的冒號後面沒有空白）。Chromium 與 WebKit 都可用。`fetch` 要先看 `response.ok`：後端不在時代理回的是 `502`，不是丟錯誤。
- 斷線有三種樣子，都要當成斷線：讀取丟出錯誤（後端消失、後端 30 分鐘到期收掉連線）、讀取正常結束（這次沒有遇到，但要處理）、回應不是 200。
- **一定要有「太久沒有資料」的計時。** 後端卡住或正常關機的那 30 秒，連線是開著的、不會有任何錯誤，只有這個計時發現得了。`research.md` 寫的兩個心跳週期（30–35 秒）實測可用。計時到期後要主動中止這條連線再重連（`AbortController`）；spike 的頁面只記一筆、沒有中止。
- 後端每 30 分鐘會把連線收掉一次，瀏覽器看到的是讀取錯誤。這是正常現象，1 秒內就重連得上；提醒列若一斷線就出現，櫃檯每半小時會看到它閃一下。要不要等重連失敗一次才顯示，產品規格沒有寫，留給實作那一題照 feature 決定。
- 重新連上後 `stream_pos` 從 0 重新算，後端不會補送斷線期間的事件；`init`、`resync` 一到就要重抓畫面上的資料。`resync` 附的 `refetch` 是 `/api/v1/pool`，櫃檯不要照它抓。
- **剛連上不代表事件馬上會來。** 後端啟動後約 3.4 秒才開始收 Kafka 的事件（後端日誌的 `partitions assigned` 比 `Started TmsApplication` 晚這麼久），這段時間內發生的事件不會出現在任何串流上，之後也不會補。這只發生在後端剛啟動時；後端一直開著、只是瀏覽器重連的情況，不會掉事件。這是後端既有的行為，這一題沒有改，要回交給後端（Phase 4-B12、4-B13 或另外開題）決定要不要補。
- 登入憑證過期或無效時串流端點回什麼（預期 401）、要不要回登入頁，這次沒有試。

**給之後測試步驟的注意事項。**

- 重啟 8080 之後，至少等到後端日誌出現串流消費者的 `partitions assigned`（實測健康檢查通過後約 3.4 秒；這次等 6 秒是夠的）才開始做會產生事件的動作，否則「畫面即時跟上」的步驟會等不到事件。`app/web-bdd/run-feature.sh` 起後端時要把這段等待算進去。
- 「幾秒內畫面跟上」的等待上限不能低於 2 秒；實測最大 1.74 秒，建議抓 5 秒以上，並且等畫面狀態、不要等固定秒數。
- 同一間房 10 分鐘內只能送 3 筆住客需求，第 4 筆回 `429`。要連續造很多張待派單時，每張換一間房，或用 `BackendChannel` 直接建單。
- 住客的連線憑證要長成「飯店編號.任意字串」（例如 `3.abc…`，中間只有一個點）並放在 `Authorization: Bearer`。不符合這個樣子的憑證，後端一律回 401「session 無效」。
- 要模擬斷線，停後端用 `kill -9`（0.1 秒內斷）；用一般的停止訊號要等 30 秒。只想讓「即時連線異常」出現而不停後端，可以在瀏覽器端攔掉 `/staff/stream`（Playwright 的 `route`），這條路這次沒有試。
- 後端停著時，Playwright 的 console 會一直出現 `502` 與 `net::ERR_INCOMPLETE_CHUNKED_ENCODING`；若之後有「console 不得有錯誤」之類的檢查，要把串流這幾種排除。
- 這次瀏覽器的時鐘沒有撥。T005 的 `page.clock().install()` 會接管頁面的 `setTimeout`、`setInterval` 與 `Date`，串流客戶端的「太久沒資料」計時與重連等待都會受影響（`fastForward` 30 分鐘會讓計時立刻到期），兩者一起用時的行為留到 Foundational 接上真的客戶端再驗。

**現行後端的收件範圍，與這次試驗的關係。** 現在後端只把「部門等於連線者部門」或「指派給連線者本人」的單推給員工連線。這次的需求是進待派單的自由文字需求，部門是 `frontdesk`，所以 `frontdesk` 帳號收得到，沒有換角色、也沒有繞路。單派給房務或工務之後的事件 `frontdesk` 收不到，這是 Phase 4-B12 要放寬的範圍，這次沒有試也沒有改。

**這一題動了什麼。** `app/web/vite.config.ts`（`/staff/stream` 的代理加上述 `configure`）；新增 `app/web/spikes/stream/index.html` 與 `main.ts`（最小頁面，和 `spikes/dnd/` 一樣不被 `src/` 引用、不進打包、不是產品的串流客戶端）。後端、`app/web/src/`、`app/web-bdd/`、`app/bdd-support/` 都沒有改。試驗用的 Java 程式與啟停腳本是一次性的，沒有留在專案裡。試驗在本機開發資料庫（5433）的「測試飯店」留下了資料：7 個名叫「T006櫃檯」的帳號、20 張標題以「T006」開頭的待派單，以及對應的房間、訂房與住客連線憑證；沒有動「示範飯店」。

**沒有驗到的。**

- 正式環境的代理與同源部署；真機 iPad 的 Safari（背景分頁、螢幕鎖定、切換 Wi-Fi 之後串流的行為）。
- 連線超過 5 分鐘、到 30 分鐘後端自然收掉的那一次（只用 40 秒的上限模擬過）。後端收掉連線時瀏覽器看到的是讀取錯誤而不是正常結束，是後端本來就這樣、還是這次加的代理設定造成的，沒有分開驗（兩種客戶端都得重連，不影響結論）。
- `degraded` 事件（後端處理落後時的宣告）、Kafka 停掉時的行為、登入憑證過期時的回應。
- 單派出去之後 `frontdesk` 收不到後續事件這件事，只讀了程式（`StreamConnection.wants`），沒有實跑確認。
- 多個分頁同時連、網路很慢或會掉封包的情況。
- 後端是用 `tms/target/classes` 既有的編譯結果直接起的（和 `spring-boot:run` 組出來的指令相同、沒有重新編譯）；容器用的是當時已經開著的 `tms-postgres`、`tms-kafka`，它們跑在 OrbStack 上，不是 Docker Desktop（Docker Desktop 當時沒有開）。這一題沒有用到 Testcontainers，所以沒有影響，但環境和「測試指令」寫的不完全一樣。
- 試驗與驗收在本機開發資料庫（5433）的「測試飯店」留下 10 個「T006櫃檯」帳號與 27 張標題以「T006」開頭的待派單，沒有清。櫃檯 Web 驗收（T021 的 `run-feature.sh`）連的就是這個資料庫，所以它的情境前置必須呼叫 `BackendChannel.resetBusinessData()` 清空業務資料，不能假設待派單一開始是空的。
- 驗收重跑的補充數字（Chromium）：`task` 延遲 1458–1898 毫秒（4 次）；`kill -9` 後 393 毫秒內讀取丟錯；拿掉代理那段設定後，`kill -9` 過了 4 秒頁面仍顯示已連線，後端回來 15 秒也沒有重連。正常關機先安靜 30 秒這件事沒有重跑，依據是後端 log；它大概是 Spring Boot 預設的優雅關機等待，沒有查證設定來源。

## T007 smoke-test

**結論：三條測試線都起得來。** 這一題只證明「連得上、編得過」，沒有驗任何功能。

**① 櫃檯 Web 單元測試與打包。**

- `cd app/web && npx vitest run`：1 個測試檔、1 個測試通過。測試是 `app/web/src/smoke.test.ts`，只斷言 `1 + 1` 等於 2，留在專案裡當這條線的最小檢查。
- `cd app/web && npm run build`（`tsc --noEmit && vite build`，T001 就有這個 script，沒有改 `package.json`）：通過，產出 `dist/index.html`、`dist/assets/` 下一支 JS 與一支 CSS。`dist/` 在 `.gitignore` 裡，不進 repo。

**② 櫃檯 Web 驗收線：瀏覽器 → 開發伺服器 → 代理 → 後端。**

- 新增 `app/web-bdd/src/test/java/com/athena/web/bdd/SmokeTest.java`：一支一般的 JUnit 測試（不是 Cucumber 步驟）。它用 `WebProfile.current()` 開瀏覽器，載入開發伺服器的首頁（要回 200、頁面上有 `#root`），確認視窗大小與是否觸控和該組設定一致，再在頁面裡用 `fetch` 對 `/api/v1/staff-accounts/login` 送一個不存在的帳號，要拿到 401。401 代表請求真的經代理到了後端；後端沒開時代理回的是 502。
- 怎麼跑：先把 8080 後端與 `app/web/` 的開發伺服器開起來，再下
  `cd app/web-bdd && mvn -q test -Dtest=SmokeTest -Dweb.profile=desktop -Dweb.baseUrl=http://localhost:5199`（另一組把 `desktop` 換成 `ipad`）。
  `web.baseUrl` 是開發伺服器的位址，不給時是 `http://localhost:5173`（Vite 的預設埠）。這次開發伺服器用 `cd app/web && npx vite --port 5199 --strictPort` 起，後端照 T006 的做法起在 8080。
- 實測結果（測試會印一行 `[smoke] …`）：

  | `web.profile` | 引擎 | 視窗 | 觸控 | 首頁 | 經代理登入 |
  | --- | --- | --- | --- | --- | --- |
  | `desktop` | Chromium 158.0.8127.18 | 1280 × 720 | 否 | 200 | 401 |
  | `ipad` | WebKit 27.2 | 1180 × 820 | 是 | 200 | 401 |

- 這也實測了 `-Dweb.profile` 與 `-Dweb.baseUrl` 真的傳得進測試的 JVM（先前只有讀 pom 判斷）：兩次跑出來的引擎與視窗不同，位址也是指令給的 5199。
- `app/web-bdd/pom.xml` 改了兩處：加 `org.junit.jupiter:junit-jupiter`（test scope，版本跟著既有的 junit-bom）；surefire 加 `<excludes>` 排除 `**/SmokeTest.java`。排除的理由是它要伺服器開著才跑得過，平常的 `mvn test`（含 dry-run）不該跑到它；指名 `-Dtest=SmokeTest` 時 surefire 不看這個排除，照跑。
- 改完後重跑既有的 dry-run（`mvn test -Dcucumber.execution.dry-run=true -Dcucumber.features=../../specs/truth/features/frontdesk-web`，當時伺服器都已關掉）：和 T003 記的一樣載入 27 份 feature、417 個案例、總計列 240，沒有跑到 `SmokeTest`。這條指令目前本來就是失敗收場，因為櫃檯 Web 還沒有任何 stepdef，每一步都是 undefined；那是 Phase 3 的事。

**③ 員工 App 測試線的 dry-run。**

- 指令是 tasks.md「測試指令」最後一條：`mvn test -Dtest=RunModuleFeatureTest -Dsurefire.excludeJUnit5Engines=cucumber -Dcucumber.execution.dry-run=true -Dcucumber.features=<repo>/specs/truth/features/ios-app`。結果：編譯通過、259 個案例、0 失敗、0 錯誤、沒有 undefined，`BUILD SUCCESS`。
- 這次不是在 `app/bdd/` 原地跑的。當時有另一批員工 App 的驗收正在用 `app/bdd/target`，所以把 `app/bdd/`（不含 `target/`）複製到暫存目錄，在複本上跑，feature 路徑改給絕對路徑。原始碼與 pom 和 `app/bdd/` 當下的內容相同；`app-bdd-support` 用的是 `~/.m2` 裡已經 install 的那一份（沒有重新 install，`app/bdd-support/src` 沒有比它新的檔）。沒有別人在用 `app/bdd/target` 時，直接照 tasks.md 的指令在 `app/bdd/` 跑即可。

**沒有驗到的。**

- `SmokeTest` 只確認首頁回 200 且有 `#root`；`App` 目前回 `null`，畫面是空的，沒有任何可看的內容。
- `app/web-bdd/run-feature.sh`（T021）還不存在，後端與開發伺服器是手動起的；8080 後端不是可撥時鐘的那一種。
- 觸控是用頁面的 `'ontouchstart' in window` 判斷的（T005 記過 WebKit 的 `navigator.maxTouchPoints` 是 0，不能用）。
