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

## T012 後端的列舉與型別落點：給 Phase 4 的注意事項

- `ActorRole` 還沒有 `duty_manager`，`tasks.md` 也沒有哪一題指名要加；資料庫（V26）已允許。第一個要讓值班主管寫任務事件的 Feature phase（4-B04 派單）要補上，否則事件會記錯角色或寫不進去。
- `/api/v1/desk/**` 目前套用既有的通用權限規則：`frontdesk` 打三支寫入會先拿到 403，`duty_manager` 全部 403，到不了控制器。放行是 4-B10 的事；在那之前，測試不要把 501 當成這兩種角色的預期結果。
- `DeskTaskDetail` 用 `@JsonUnwrapped` 把 `DeskTask` 攤平到同一層。專案沒有別處這樣用，實際輸出的 JSON 形狀還沒有實跑確認；4-B08（單筆詳情）第一次轉綠時要看一眼回應。
- 歷程每一筆的 `detail` 是「值為 null 的欄位不輸出」，卡片與詳情的可空欄位則是輸出 `null`。前端判斷歷程欄位有沒有值時，不要用 `=== null`。
- 三支寫入的 `Idempotency-Key` 在控制器宣告成非必帶，沒帶時要由功能程式回 `idempotency_key_required`（交給 Spring 會變成 `validation_failed`）。
- 六支都實作完後，把控制器裡過渡用的 `NotImplementedYet` 一起刪掉；`not_implemented` 這個錯誤碼不在契約裡。

## T014 後端「櫃檯看板與派單」的測試落點：給 Phase 3 的注意事項

- stepdef 各寫各的檔：介面根的 5 句（T025–T029）在 `steps/desk/DeskCommonSteps.java`，Given（T054–T068）在 `DeskGivenSteps.java`，When（T069–T080）在 `DeskWhenSteps.java`，Then（T081–T121）在 `DeskThenSteps.java`。四個檔目前是空殼，還沒有被 Cucumber 實際建立過；第一個填步驟的題一跑就會知道注入有沒有問題。
- 呼叫端點一律走 `support/DeskApi`，它會自動把最近一次的派單鍵、看板回應、詳情回應記進 `DeskContext`。
- `DeskApi.guestSessionToken(房號)`：房間沒有入住中的訂房時，會建一筆 30 天前入住、隔天退房的訂房來掛住客連線，房間仍算空房。要留意三件事：有填住客的案例要**先建訂房再送單**（後來才建在住訂房會換一個連線，看不到先前那張單）；這筆舊訂房沒有住客姓名；案例若把後端時鐘撥到 30 天前，同房的在住訂房會和它撞住期。
- 住客的三支呼叫與建連線都會蓋掉 `world.lastResponse()`；在 Then 裡呼叫它們之後，別再讀 `lastResponse` 當成前一個動作的回應。
- `DeskApi.assign` 沒給 `via` 時送的是 `"via": null`，不是省略這一欄；後端對兩者的處理相同。

## T015 後端「員工事件串流」的測試落點：給 Phase 3 與 Phase 4 的注意事項

- stepdef 的落點：Given（T122、T123）在 `steps/staffstream/StaffStreamGivenSteps.java`，When（T124–T128）在 `StaffStreamWhenSteps.java`，Then（T129–T136）在 `StaffStreamThenSteps.java`。
- 連線與收事件走 `support/StaffStreamClient`；讓後端進出降級走 `support/StreamFaults`。`reportLag`／`reportCaughtUp` 只等到 Kafka 收下訊息，**要以「後端已經處理到」為前提時，接著呼叫 `awaitProcessed()`**，否則結果看運氣。
- 心跳不必等 15 秒，用 `JobTrigger.run("stream-heartbeat")` 立刻觸發一次。但真實的 15 秒心跳仍會隨機夾進任何連線的紀錄，比對事件順序前要先濾掉 `heartbeat`。
- 後端要到第二次往連線寫資料才會發現客戶端已關。`StaffStreamClient` 在場景結束時會觸發兩次心跳讓後端放掉連線；場景中途自己關的連線不會，要等之後的寫入。後端寫進已關的連線時會印一段 `response is already committed` 的錯誤，是產品既有行為，不影響結果。
- 場景結束時 `StreamFaults` 會把後端還原成正常。還原失敗只會在 `System.err` 印「場景結束時還原串流狀態失敗」加一行警告，當下那個場景不會因此失敗；後面場景莫名變紅時先找這一行。這條失敗路徑沒有實跑過。
- 現行產品還沒有的行為，要到 4-B13 做完相關的題才會綠：降級期間才連上的連線補收 `degraded`、`recovered`、`heartbeat.degraded`、`kafka_unavailable`。`StreamFaults.disconnectEventSource()` 是停掉事件消費者，能不能觸發產品之後的 `kafka_unavailable` 要看 4-B13 怎麼偵測。
- 產品程式目前讀不到「是否降級」與「目前連線數」；測試只能間接判斷。

## T018 `app/web/` 的骨架：給 Phase 4 各畫面 phase 的指引

- **東西放哪**：頁面內容填進 `src/pages/<頁>/` 的元件（保留根元素的 `data-page`）；側欄、工具列、提醒列位置、對話框放 `src/shell/`；帳號面板與語言在 `features/account/`、詳情面板 `features/detail/`、當場回答 `features/answer/`、派單與復原 `features/dispatch/`、事件串流與輪詢 `features/realtime/`；端點函式在 `src/api/` 下開新檔並一律經 `api/client.ts`，契約型別加在 `api/types.ts`；只存在畫面的狀態放 `src/stores/`；純計算放 `src/lib/`。
- **tasks.md 寫的「路由守門」就是這兩個檔**：規則在 `src/lib/route-access.ts` 的 `decideRoute`（純函式），接線在 `src/router.tsx` 的 `Gate`。不要另起一份。
- **四條路徑**是 `/login`、`/change-password`、`/board`、`/triage`（常數在 `src/lib/paths.ts`）。規格沒有指定字面，取自雛形的檔名。
- **換頁不必自己 navigate**：登入、登出、改完密碼只要更新 `stores/session.ts`，`Gate` 會自動導到該去的頁。
- **規格沒寫、骨架先這樣做的**：已登入、已改密碼、可用櫃檯的人打開 `/login` 或 `/change-password` 會被導去 `/board`。
- **被擋的角色改完密碼之後**：不要先 `setSession` 再導頁。這時他是「不需改密碼、但不是櫃檯角色」，`Gate` 會把他送回登入頁，而登入頁的提示卡要求這個分頁已經沒有登入狀態。設定新密碼的畫面應該 `clearSession()` 再顯示提示卡。
- **登出要多清一樣東西**：`clearSession()` 不會清看板的選擇（泳道、篩選、房號搜尋、側欄），登出時要另外呼叫 `useBoardViewStore.getState().reset()`。
- **儲存的鍵**（驗收測試塞資料要照這些鍵）：登入狀態在 `sessionStorage` 的 `tms.frontdesk.session`，值是登入回應的 JSON 原樣；看板選擇在 `sessionStorage` 的 `tms.frontdesk.board-view`；語言在 `localStorage`，登入頁一筆 `tms.frontdesk.language.login-page`，每人一筆 `tms.frontdesk.language.staff.<員工編號>`，值是語言代碼。
- **語言的決定規則還沒有實作**，初始化固定用繁體中文，留給介面語言那個 phase。英文、日文、越南文的資源檔目前是空的，缺的鍵會退回繁體中文。
- **還沒有處理的**：憑證過期或後端回 401 時要不要回登入頁；轉址時不會保留網址上的查詢參數。

## T019 `app/web/src/lib/` 的純計算殼：給 Phase 4 的注意事項

- 殼的函式呼叫時會丟 `not implemented: lib/<檔> <函式>`；填內容時把參數名前面的底線拿掉，簽章不必改。測試檔裡的 `it.todo` 換成真的測試。
- **等待時間只算一份**：數量卡「等最久」、欄頭「逾 20 分」、待派單頁與詳情的「已等 N 分」、膠囊，都用 `wait-pill.ts` 的 `waitingMinutes` 與 `waitTone`，不要各自再算。4-W09 排在 4-W11 之前，先用到的那一題就把這兩個填起來。
- **純計算不碰翻譯**：要顯示的字一律回 `DisplayText`（哪一句加代入值），由畫面用 `t()` 轉成字。句子裡嵌著另一段要翻譯的字時（例如「派給{部門}」的部門），代入值放另一個 `DisplayText`；負責遞迴轉換的函式還沒有，第一個用到的 phase 要寫。
- `status-card.ts` 是兩個函式：`statusTag` 只要卡片資料，詳情還在載入時就能先顯示；`statusCard` 要詳情資料。`tasks.md` 的 4-W21 寫「一個純函式」，以這裡為準。
- `board-columns.ts` 的 `BoardColumn` 裡 `done`、`closed` 指的是看板的欄，和任務狀態的同名值不是同一件事（例如查房通過、已取消、已回答的單都在 `closed` 這一欄）。欄的顯示順序還沒有常數，4-W08 填的時候加。
- 歷程每一筆下面的「時間 · 操作者」（不是今天的要寫日期）不在 `timelineSentence` 的回傳裡，`tasks.md` 也沒有哪一題指名要抽它。4-W22 做歷程時另開一個純函式並補測試。
- `tasks.md` 其他 REFACTOR 題要抽的純計算（密碼規則、卡片副標、篩選組合、連線狀態機）沒有預留檔，由各自的 phase 在 `src/lib/` 開新檔。

## Phase 3 測試對齊：結果與給 Phase 4 的注意事項

- 417 題由 30 組平行完成，整體審查（T439）三輪後通過。結束時的基準：後端全部 truth feature 360 個場景、245 通過、115 失敗（都是產品還沒做，或「登入選錯飯店」）；櫃檯 Web 417 個場景、員工 App 259 個場景的 dry-run 都沒有 undefined 或 ambiguous；舊測試線 `RunCucumberTest` 仍是原本 5 筆失敗。後端逐場景結果在 scratchpad `phase3/fix-t439-full.json`，Phase 4 每一題的回歸都和它比。
- 共用排隊腳本（`scratchpad/phase3/with-lock.sh`）在 Phase 4 照樣用：所有 mvn 與 `run-feature.sh` 都經過它。
- 後端「狀態為某某」的再讀確認：單沒有負責人時改用同飯店的部門主管（沒有就當場建一位），**不能用值班主管**，因為值班主管只放行櫃檯端點。同檔的房況再讀（`roomHkStateIs`）挑人時還沒有限角色，之後改成同樣做法。
- 櫃檯 Web：同一間房有兩張單時，`BoardPage.card(房號)`、`TriagePage.rowOpener(房號)` 會找到兩個元素，要用標題再分。
- 櫃檯 Web 的時鐘：全專案只有看板「讀取失敗後網路恢復」一處會停住瀏覽器時鐘，由觸發的步驟或場景結束負責放開。

## Phase 4 後端進行中的交接事項

- **4-B04 的 `櫃檯派單.feature:75`「何志偉先當場回答 306，林佩君才派給房務」帶到 4-B06 驗。** 它的前置步驟要打 4-B06 才做的 `POST /api/v1/desk/tasks/{id}/answer`。派單撞上「已被當場回答」的 409 分支已寫好但沒實跑過；4-B06 轉綠時要一併確認這個場景綠，並在 `SecurityConfig` 補 `/answer` 的放行（目前沒有規則，櫃檯會先拿 403）。
- **4-B05 的 `派出後復原.feature:84`「林佩君當場回答 306 之後想復原」也帶到 4-B06 驗。** 前置步驟同樣要打 `/answer`。復原這一側已寫好（已當場回答的單狀態是 done、沒有負責人，落到 `undo_unavailable`，`context` 帶 state 與 roomNo），4-B06 轉綠時一併確認。另外：資料 truth 說「能不能復原的條件不含時間」，實作把後端的 10 秒時限（由注入的時鐘算出）帶進同一句帶條件的更新，對外行為相同、還避免了先查時間再更新的落差；驗收判斷不違反 truth，之後可在 truth 補一句說明。
- **4-B06（T450）完成後，上面兩個延後的場景都已轉綠。** 這一題改了舊行為：`TaskEngine.answer` 改寫 `tasks.answer`、不再把回覆蓋進 `note`，舊 `/desk` 端點同一段程式也跟著改，舊測試線因此多一筆「資訊類問題當場回答」（`03-desk-triage.feature:52`）失敗，合計 6 筆；Scope Note 第 10 條與 4-B06 Boundary 已寫明，4-B16 刪除。驗收搜過全 repo，沒有其他地方從 `note` 讀回答。另外看板與詳情的 `guestText` 改從 `note` 的 JSON 解析；V25 之前當場回答過的單 `note` 是純文字，原本會讓看板 500，已補成解析不了就回 null（主對話直接修，重跑三份櫃檯規格與 T450 結果逐場景相同）。
- **T451 驗收時發現 `answer_invalid` 沒帶契約要的 `context.maxLength`**（`shared.yaml:38`），T450 起就缺，沒有測試或前端讀它。主對話在 `common/DeskAnswerText.require` 補上 `Map.of("maxLength", 120)`，重跑 `當場回答並結案.feature` 16／16。另一個契約規定帶 context 的 422 `new_password_too_weak`（`failedRules`）目前還沒有任何程式丟出，做到那一題時要帶上。
- **4-B07（T452）完成。** 一般單建單時在同一交易向 `task_counters` 取號（`T-%04d`，每家各自編號，工務單不取）；已用一次性資料庫以 `tms_app` 實測 20 條連線同時取號不重號、跨飯店寫入被列層級安全擋下。每條走到終點的路徑都寫 `ended_at`，且與事件時間同一個值（上面「各取一次時鐘」那條已解決）；查房抽查退回時清空。弱綠更新：`櫃檯看板讀取.feature:32` 已是實綠；`單筆詳情與歷程.feature:32` 仍弱綠留給 4-B08；`櫃檯介面的角色權限.feature:26`、`:27` 只驗有出現，4-B10 重驗。附帶：`scripts/seed-demo.sh` 與舊測試線夾具直接 INSERT 的非工務單沒有 `task_no`，看板上單號為 null（不會 500，但不合契約的必填），示範飯店要顯示單號時在 seed-demo 的 INSERT 補 `task_no` 即可。樓層照契約取房間主檔全部樓層；`PLANROOM-F*` 佔位房間只出現在測試夾具，正式環境不會有，對正式行為無影響，下方待決那條維持待決但影響僅限測試。
- **4-B08（T454）完成。** 詳情補齊住客、開單人、到手方式與經手人、取消、工務驗收；歷程略過成對的派單＋復原與 `sop_checked`。`單筆詳情與歷程.feature:32` 已是實綠，弱綠只剩 `櫃檯介面的角色權限.feature:26`、`:27`（4-B10 重驗）。兩個紅場景是 Phase 3 測試側缺陷，已回補（只改測試碼、不改步驟語意）：`TaskFixtures.Builder` 加可選 `createdBy`，「已有以下工務單：」來源為同仁報修時開單事件記在報修人名下，和正式報修流程一致（櫃檯代開的工務單之後若有場景比開單人，要比照補）；`readMissingDeskDetail` 先對不存在的單號拍操作前快照。最小可逆做法（待上游）：`ruleLabel` 一律 null（建單快照沒記命中哪條規則）；`returned_to_pool.reason` 照 payload 原樣帶出代碼 `leave_return`／`shift_end_return`，契約只說「退回的原因」、型別是字串無列舉，畫面要自己對照代碼出句子，或上游改成寫白話；主管延後的事件沒有內容，`bySelf=false`、`reason=null`（延續延後事件 payload 那條待決）。
- **4-B09（T456、T457）完成。** 常用回覆直接呼叫住客端既有的 `GuestTenantConfigReader.infoPages`，兩邊讀法同一段，T457 的目標在 T456 就已達成，驗收確認不需再改程式，直接打勾。`/api/v1/desk/quick-replies` 原本被 `GET /api/v1/**` 放行給房務，已改成只放行三種角色。測試側回補一行：`DeskWhenSteps.readQuickReplies` 先拍操作前快照，與 `DeskCommonSteps` 讀看板同寫法。待上游：契約 `DeskQuickReply` 三欄必填，但 `/tenant-configs` 寫入 `guest_info_pages` 沒有驗證，某頁缺 title／body 時會回 null；可在寫入端擋，或在櫃檯端過濾（代價是櫃檯頁數可能少於住客端），目前沒有場景涵蓋。
- **4-B10（T458）完成。** `/api/v1/desk/**` 收成一條規則放行三種角色，排在 `GET /api/v1/**` 之前；看板與詳情原本被通用讀取規則放行給房務、工務，值班主管反而被擋，已修正。弱綠全部解除（`櫃檯介面的角色權限.feature:26`、`:27` 逐一比房號並對資料庫）。值班主管目前打不到任何員工 App 端點，符合「`duty_manager` 只用於櫃檯 Web」的裁決。**缺口：值班主管改密碼沒有任何 Phase 的 Test Scope 覆蓋**——`SecurityConfig` 的 change-password 規則沒有 `duty_manager`，值班主管用臨時密碼登入後會改不了密碼；4-B10 Boundary 有提到要放行。處理：交給 4-B11（T460）在 change-password 規則補上 `duty_manager`（最小可逆，一行），Test Scope 沒有值班主管場景，靠讀程式與手動請求驗證。`/staff/stream` 的值班主管放行由 4-B12 的 Test Scope（`收得到哪些單的訊號.feature:9,20`）帶出。
- **4-B11（T460）完成。** 新密碼三條規則（至少 8 碼、英文字母、數字），沒過的依序列在 `context.failedRules`（`min_length`、`letter_required`、`digit_required`）；檢查順序是目前密碼 → 強度 → 與目前相同（三者同時發生的先後契約沒定，採這個順序）。值班主管改密碼已放行（`SecurityConfig` change-password 規則加 `duty_manager`），以 scratchpad 一次性 feature 驗證，拿掉這行會回 403；值班主管仍打不到員工 App 其他端點。`ChangeOwnPasswordTest.shortNewPasswordAccepted` 鎖的是「強度門檻規格還沒定義、短密碼照收」，契約已定義，改寫為 `weakNewPasswordRejected`。契約沒寫死的兩點：「英文字母」目前只算 ASCII、「8 碼」以 UTF-16 長度計（與員工 App 的 `password_rules.dart` 一致）。櫃檯 Web 的改密碼頁目前是空殼，前端實作時要對上這三個規則代碼。
- **4-B12（T462）完成。** 串流收件依契約：櫃檯、主管、值班主管收全飯店；房務、工務收本部門、指派給自己，以及從自己部門收回的 `triage_undone`（T448 留下的待驗已由 `收得到哪些單的訊號.feature:54` 驗證）；owner 不能連。`resync.refetch` 三種櫃檯角色是 `/api/v1/desk/board`，房務工務是 `/api/v1/pool`。**主管的收件範圍比以前寬**（原本只收本部門或指派給自己），是照契約；目前員工 App 完全沒有接串流（`app/mobile` 沒有 `/staff/stream`），唯一的客戶端是櫃檯 Web，所以沒有行為影響；日後 App 若要接串流，主管會收到全館事件，屆時要重新評估。`SecurityConfig` 新增 `GET /staff/stream` 放行五種角色、排在 `GET /staff/**` 之前，值班主管只能連串流。
- **4-B13（T464）完成，後端全套首次 360／360。** 降級狀態只有 `StreamConnectionRegistry.degradedReason` 一處，進出降級與新連線補送共用 `degradedLock`；`kafka_unavailable` 由 Spring Kafka 消費者事件（停止、啟動失敗、不回應）觸發，消費者重啟只解除這個原因；事件落後超過 10 秒觸發 `kafka_consumer_lag`，及時處理一則事件就解除。落後判斷用真實時間而非注入的 `Clock`（事件時間戳是送出端真實時間，測試時鐘會被撥快），不違反「業務日期用注入時鐘」的慣例。待上游：（1）契約 `StaffStreamRecovered` 寫「對所有連線送一次」，實作只送員工連線；住客在事件落後時收得到 `degraded` 卻收不到 `recovered`、心跳也不帶旗標（這個缺口原本就存在），請決定補住客恢復訊號或把契約改成員工連線；（2）真實 Kafka broker 掛掉時消費者不一定停、也不一定發出不回應事件，`kafka_unavailable` 可能偵測不到，建議交 `/technical-research` 評估 idle／non-responsive 偵測設定；（3）鎖內對連線送訊號，某條連線 TCP 寫入卡住時，進出降級瞬間會拖住新連線，低風險，記錄即可。
- **4-B14（T466、T467）不需要改程式。** `主管待辦空清單與權限.feature` 在 T465 基準裡 6 個場景已全綠，綠的原因符合規格：值班主管打 `GET /api/v1/me/manager-queues` 先被 `SecurityConfig` 的 `GET /api/v1/**`（不含 `duty_manager`）擋成 403，服務端 `ManagerKind` 也只認 manager，兩層都擋。主管待辦的角色判斷與櫃檯 Web 三角色是兩回事，沒有重複的角色清單要併，T467 也不需改動。
- **4-B15（T468、T469）不需要改程式。** `員工推播事件.feature` 在 T465 基準裡 19／19 全綠（只是背景表用語改名，T022 的 stepdef 已接住）。「值班經理」只剩兩處，都在例外範圍：`V7__duty_manager_role.sql` 的註解（Flyway 不能改）與 `app/bdd-support/.../BackendChannel.java:119-122`（員工 App 測試線的用語對照，刻意保留兩種說法）。
- **4-B16（T470、T471）完成，Phase 4 後端全部結束。** 刪除 `DeskController`、`DeskService`（整個類別只服務舊端點）、`TriageDecisionRequest`、`03-desk-triage.feature`，以及兩個 `StaffDesk*Steps.java` 裡 12 句舊櫃檯步驟與無人呼叫的 helper（檔案保留）；`SecurityConfig` 拿掉 `/desk/**`；測試專用 `/desk/probe` 改掛 `/api/v1/desk/probe`，`05-identity-tenancy.feature` 只改路徑。後端全套 360／360；舊測試線 151 個（158−7），失敗剩 004 開始前就有的 5 筆（查房最後一筆事件、QR token 唯一約束、outbox kinds 兩筆、SOP 勾記數與重複鍵）。`TaskEngine.assignFromTriage` 4 參數多載目前沒有外部呼叫者，超出 Boundary 未刪。順手更正 `DeskBoardFixtures.answerOnTheSpot` 過時的註解（「回答入口會把回覆蓋進 note」已不成立）。
- **4-W02（T474）完成。** 設定新密碼畫面不保留臨時密碼：契約與後端都規定必須先改密碼時只看憑證、不帶目前密碼，前端只送 `newPassword`，兩組密碼都沒有寫進 storage、網址或 log。「不能和臨時密碼相同」交給後端 `new_password_invalid`。**兩件待使用者決定（都不急）：**（1）後端回 422 `new_password_too_weak`（畫面規則被繞過或日後前後端不一致才會發生）時，畫面顯示的是網路錯誤句「沒有更新成功，請檢查網路連線後再試一次。」，會誤導；要改得新增一句畫面文字，例如「新密碼不符合上面的規則。」，四份語言檔與 ui-plan 都要補。（2）房務、工務、老闆改完密碼後回登入頁的提示卡只放記憶體，登入頁重新整理後就消失；建議維持現狀。
- **4-W03（T476）完成。** 登出依序清看板檢視狀態（含 sessionStorage `tms.frontdesk.board-view`）、React Query 快取、登入頁提示、登入狀態（sessionStorage `tms.frontdesk.session`），介面語言（localStorage）不清；已用瀏覽器實測登出後兩個 sessionStorage 鍵都清空、登出後不再打 `/desk/board`、按上一頁回不到看板。**和雛形不同、待回交 `/ui-plan` 補記：** 雛形（`ui/desk.js:710`）在登出確認按「取消」後回到帳號面板，但 `帳號面板與登出.feature:70` 要求取消後畫面上沒有任何對話框，所以實作改成按「登出」時面板先收起、取消後回到原頁；`ui/ui-plan.md` 畫面 4 要補一句。外框 `DeskShell` 先讀一次看板（只讀不畫，讓「已登入櫃檯畫面」前置等得到 `/desk/board`），重試次數、401 處理、資料新鮮度留給看板的 Phase 一起定。
- **4-W05（T480）完成。** 介面語言存 localStorage（登入頁一筆、每人一筆），與登入憑證分開；瀏覽器語言只看第一個，不在四種內用繁中；已登入的人換語言時同時更新本人那筆與登入頁那筆（和雛形 `desk.js` 的 `setUser` 一致，主對話在驗收後補上），登出時登入頁那筆改成剛登出那位的語言。**英、日、越三份資源檔已補齊（四份各 80 個鍵一致），其中大量譯文是暫定的，待母語審稿**：`app.name`、`roles.*`、`login.*` 的錯誤與提示卡、`changePassword.*`、`shell.titles.*`、`board.*`、`detail.*`、`answer.title`；用字參考：工務 Engineering／施設管理／Kỹ thuật，房務日文ハウスキーピング，主管日文上長。帳號面板語言頁的字取自雛形字典。測試共用碼兩處：`WebProfile` 預設瀏覽器語言固定 `zh-TW`（要測其他語言的前置會另開瀏覽器覆蓋）；`AccountWhenSteps.logsInOnLoginPage` 只在分頁還沒開過網頁（`about:blank`）時先打開登入頁。待回交 `/ui-plan`：帳號面板換到語言頁時面板名稱維持「帳號」。
- **飯店隔離完成並通過驗收。** 契約 `LoginRequest.hotelCode`（選填）；後端帶代碼時只在那家找帳號，格式不合、飯店不存在、帳號不存在、密碼錯一律同一個 401；不帶代碼（員工 App）維持逐家比對。已用一次性資料庫以 `tms_app` 實測列層級安全下只看得到那家帳號。後端全套 365／365（新增 `員工登入.feature:57,64,81-83`）；櫃檯 Web `帳號與登入` 89／89；瀏覽器實測 `/athena` 登入後開 `/athena2/board` 被導到 `/athena2/login`、同編號只進二館、回 `/athena/board` 要重登、`/login` 是輸入代碼頁且不列飯店。開發資料庫 5433 有測試用飯店 `athena2`（id 5）與帳號 F0101「二館櫃檯」（建法在 scratchpad `t481/athena2-create.sql`，測試清資料後要重跑帳號 INSERT）。主對話補了輸入代碼頁擋保留字（`lib/hotel-code.ts`，清單與後端 `HotelCode.RESERVED` 一致）。**待回交 `/dsl-refine`：**（a）`frontdesk-web/帳號與登入/登入頁.feature:147`「查不到時照常可以登入」已不成立；（b）`frontdesk-web/帳號與登入/dsl.md:5` 要寫登入帶 `hotelCode`；（c）櫃檯 Web 沒有 feature 覆蓋輸入代碼頁與跨飯店導回登入、換飯店前清乾淨（中）；（d）`員工登入.feature:81-83` 例子表可補「這家飯店沒有這個員工編號」一列。**其他待辦：**帳號不存在時回應較快（約 90ms，原本就有，可補一次假密碼比對讓耗時一致，低）；`api/client.ts` 遇到 401 沒有統一帶回登入頁（憑證過期時畫面不會回登入頁）；即時串流做好時要接進 `logOut`，換飯店登入也會關掉。
- **4-W08（T486）完成。** 欄內排序：待派單 `createdAt` 新到舊、待認領 `waitingSince`（無則 `createdAt`）舊到新、其他欄 `updatedAt` 新到舊。雛形 `desk.js:416` 用「最後一步的時刻」，但 feature Rule、spec FR-023、`ui-plan.md:583`、契約 `updatedAt` 說明都寫「最後更新時間」，判定雛形是示範資料的近似寫法，維持 `updatedAt`；副作用是 SOP 勾一項、住客評分、急件降級、改派、PMS 回寫房態等會讓卡片在欄內往上跳（記下，若使用者要改成最後一步再改契約與 ui-plan）。主對話在驗收後補了兩處：副標月日改用 `Intl` 依語言排（越南文 26/10）、每欄加雛形的灰底圓角格子（`ui/desk.css:161`）；重跑 8／8。「等最久」同一時刻時照後端順序（雛形待派單優先，規格沒定）。新增的英日越譯文暫定，待母語審稿。
- **4-W11（T493）膠囊時刻改用伺服器時區顯示，與使用者裁決「飯店時區＝伺服器時區」一致。** 後端 `serverTime` 帶伺服器時區偏移，任務的 `doneAt`、`endedAt` 卻是 UTC 偏移，原本用裝置時區顯示「時:分」。現在 `stores/server-clock.ts` 收到 `serverTime` 時從字串尾端讀出偏移（`lib/server-clock.ts` 的 `utcOffsetMinutes`，`+08:00`→480、`-05:30`→-330、`Z`→0；讀不出時沿用上一次，初始 0），`wait-pill.ts` 的 `clockTime` 與 `waitPill` 多收這個偏移，由看板卡片從 store 取出傳入。之後詳情、歷程等要顯示「時:分」的地方也照這個偏移，不用 `getHours()`。
- **4-W12（T494）完成。** 泳道真的分列（部門固定三列、樓層照 `floors` 由低到高含空樓層）、泳道副標只算房號搜尋後的單（照雛形 `desk.js:437-454`）、篩選膠囊、房號只收數字 4 碼。提前做了 4-W06 的兩個側欄連結（總覽看板、待派單，`aria-current="page"`），點連結換頁時清數量卡篩選；瀏覽器上一頁／下一頁不清，4-W06 可再處理。
- **4-W13（T496）完成。** 看板資料只有一份查詢設定 `deskBoardQuery`（`retry: false`），每 5 秒再試只掛在外框 `DeskShell` 的查詢上；主對話在驗收後補了「第一次失敗之後重讀期間不閃回骨架」（看 `errorUpdatedAt`），已實測。測試共用碼 `ShellFrame.openAndWait` 改成看板請求結束（成功或失敗）就算等到，否則模擬斷線的場景必定逾時。登入過期目前會顯示成網路錯誤並每 5 秒重打 401，交給下面「Redis Session」那題處理。
- **登入過期另開一題，並改用 Redis 記住 Session（使用者 2026-11-17 裁決）。** 原話：「另外開一題處理登入過期，然後登入的部分要改用 Redis 記住 Session」。再確認三點：（1）保留 JWT，Redis 記有效的 Session——後端每個請求除了驗 JWT，還要確認對應的 Session 在 Redis 裡；登出即失效、主管停用帳號可立刻踢人；（2）閒置 8 小時過期，有動作就延長（數字之後可調）；（3）後端對所有登入（櫃檯 Web、員工 App）都記 Session，員工 App 端程式不動；櫃檯 Web 補上 Session 過期或無效時帶回同一飯店的登入頁並告知。要動的範圍：techstack（新增 Redis）、契約（401 的錯誤碼與「Session 已過期」的區分、登出端點若需要）、後端（登入建立 Session、每次請求驗證並延長、登出／停用時刪除）、櫃檯 Web（`api/client.ts` 統一處理 401、`lib/log-out.ts` 呼叫後端登出）、本機與測試環境的 Redis（後端測試用 Testcontainers；`run-feature.sh` 與預覽要一個本機 Redis）。**使用者 2026-11-17 改口：這題由使用者在另一個 session 做，004 這條線不做**（原話「先把原本要做的事情做完，登入我在另外開 session 做好了」）。004 後續各題不要動登入、Session、401 統一處理、後端認證過濾器與登出端點，避免和那個 session 衝突；碰到需要 401 處理的場景照現狀，在回報說明。
- **（待使用者／另一個 session）住客用 `X-Guest-Session` 標頭的認證在正式資料庫角色下一定 401。** T498 發現：`tms/src/main/java/com/athena/tms/filter/BearerTokenAuthFilter.java:106-109` 的 `authenticateGuestBySessionHeader` 查 `guest_sessions` 之前沒有設定飯店，而這張表有列層級權限（`V1__schema.sql:424,441`），`tms_app` 不能略過，所以永遠查不到；後端測試用超級使用者連線所以沒抓到。走 `Authorization: Bearer {tenantId}.xxx` 的路徑（`:54` 先設定飯店）不受影響。認證過濾器屬使用者另一個 session 的範圍，004 不動；測試共用碼 `app/bdd-support/.../BackendChannel.java:2234,2254`（`guestSubmitRequest`、`guestSubmitMenuItem`）用的就是這個標頭。受影響：`frontdesk-web/待派單/待派單清單.feature:150`（另外它還需要即時更新，4-W25 才有），先延後到 4-W25 一併驗。 `frontdesk-web/待派單/按鈕派單與復原.feature:111`（住客收回後復原失敗）同因延後，畫面側 `undo_guest_withdrew` 已寫好並有 Vitest。 `frontdesk-web/待派單/撞單與送出失敗.feature:43`（住客收回的撞單）同因延後，畫面側提示已寫好並有 Vitest。
- **4-W15（T500）完成，15／16。** 派單樂觀更新（先取消進行中的看板讀取、快取先改、回應後換成後端的單、再重讀看板）；復原不樂觀；浮條 5 秒只放記憶體、掛在外框；畫面 5 秒與後端 10 秒依契約 `櫃檯看板與派單.yaml:128-133`。**留給 4-W17：**派單失敗與撞單（`task_not_in_triage`）、`undo_expired`／`undo_unavailable` 目前不提示；浮條出現後、派單回應回來前按「復原」，復原可能先到後端得 409 而單被派走且無提示（毫秒級，可在回應前把「復原」設為不可按）。
- **4-W16（T502、T503）完成。** 兩種空狀態與今日張數讀後端 `myToday`；置中說明外殼抽成 `components/CenteredNotice.tsx`，`LoadFailed` 與待派單「都派完了／目前沒有要派的單」共用。T503 不需另改：待派單頁與看板已共用 `SkeletonBlock`、`LoadFailed`、`CenteredNotice`、`loadStateOf`，主對話以 grep 確認兩頁沒有重複的載入／失敗邏輯。
- **4-W18（T506）完成，11／14（獨立驗收通過）。** 在 T478 的外殼 `features/answer/AnswerPanel.tsx` 上擴充：住客原話、常用回覆（`aria-pressed`，點了帶入、超過 120 字只帶前 120）、回覆框（`maxLength` 120）、字數、「回覆並結案」；去掉前後空白後空的不能送；送出成功用回傳的單換看板快取再重讀、關面板、沒有浮條；打開時焦點移進回覆框。新增 `api/desk.ts` 的 `listDeskQuickReplies`、`answerDeskTask`，`lib/answer-text.ts`（字數上限與灰字組法，5 個 Vitest），`DeskShell` 給面板加 `key`。面板寬度改照雛形 `.sheet`（`min(640px, 100%-80px)`）。英日越 `answer.*` 8 鍵是暫定譯文。紅三個：`當場回答面板.feature:84`、`:91` 是住客 401（死在最後讀住客進度頁 `AnswerThenSteps.java:190` → `BackendChannel.java:2282`，其餘後端檢查都過），延後同上；`:97` 是規格衝突，見「待使用者決定」。撞單與斷線的對話框留給 4-W19。字數目前用 JS `length`（UTF-16），T507 要和後端同一把尺。
- **4-W18（T507）完成（獨立驗收通過）。** 回覆框規則收進 `features/answer/use-answer-reply.ts`（`useAnswerReply`），面板只管畫面。字數與後端 `DeskAnswerText.java:31-33` 同一把尺：`lib/answer-text.ts` 的 `answerLength` 數 code point、`clampAnswer` 截 120 個 code point 不切半 emoji、`stripAnswer` 比照 Java `strip()`（保留 U+00A0、U+2007、U+202F，去掉 U+001C–U+001F）、`isSendableAnswer` 去空白後 1–120；拿掉 textarea 的 `maxLength`（它算 UTF-16，120 個 emoji 會卡在 60）。Vitest 新增 12 個。Test Scope 仍 11／14，已綠清單不倒退。已知小缺口：框已滿 120 字時，選取一段再貼上較長文字會整筆不收（不會截斷替換），不會送出超長內容，留給之後的打磨。回交上游（不影響產品）：契約 `櫃檯看板與派單.yaml:568-569` 的 `minLength`／`maxLength` 掛在原始欄位，說明與後端卻是去空白後才算。
- **4-W19（T508）完成，5／6（獨立驗收通過）。** `lib/dispatch.ts` 新增 `answerFailureNotice`（撞單帶後端 `context`、401 回 null 交給統一處理、其他是「沒有送出」）與 `withConflictSettled`（依 409 的 `resolution` 先把單移出待派單：assigned→該部門待認領、answered→done、guest_withdrew→cancelled；未知種類或無效部門不動看板）。撞單時先改看板快取再重讀，因為 `:14` 在看板讀取被暫停時就要 402 消失，雛形 `desk.js:672` 也是立即重畫。`use-answer-reply.ts` 加 `notice`／`closeNotice`：撞單按「好」連面板一起關；其他失敗只關對話框，回覆與選取都留，焦點回回覆框。對話框重用 `AlertDialog`、文字重用 `conflictText`（加 `dispatch.conflict.answerTail`）。英日越 `answer.notSent.*`、`dispatch.conflict.answerTail` 是暫定譯文。紅 `撞單與送出失敗.feature:34`（住客收回）在前置 `BackendChannel.java:2271` 就 401，延後同住客 401 那批（4-W25 重驗）。**給 T509：**派單那一側撞單時目前只做 `assumedAssigned`，撞到「已當場回答」也會先樂觀留在待認領；可改用 `withConflictSettled`。
- **4-W19（T509）完成（獨立驗收通過）。** 撞單對話框收成 `features/dispatch/ConflictDialog.tsx`（`action: 'assign'|'answer'`，底下重用 `AlertDialog`，可及性與焦點不變），派單 `DispatchOverlays` 與當場回答 `AnswerPanel` 都用它；組字只剩 `lib/conflict-text.ts`（參數改成必填 `action`）。撞單時的看板快取兩側共用 `features/dispatch/settle-conflict.ts` 的 `settleConflictOnBoard`（派單傳 `before` 先還原再移出）。**唯一的行為變化**：派單撞單時看板立即照後端 `resolution` 移出待派單（被派走→對方部門、已當場回答→done、住客收回→cancelled），原本會先樂觀留在剛選的部門；已綠場景都不受影響。兩側開關狀態沒合成同一個 hook：派單流程不在 React 內、對話框掛外框，狀態留 zustand；當場回答留在面板。
- **4-W20（T510）完成，35／35（獨立驗收通過）。** `features/detail/DetailPanel.tsx` 擴充成完整版面：關閉列與單號（`displayNo`，純文字不是標題）、白卡（房號、VIP、狀態標籤、標題用 `cardTitle`、原文列、副標，空房省略住客）、名稱「狀態訊息」的區塊、清潔 SOP（只限已接或進行中的打掃單）、「內容」六列（房號樓層、住客 M/D–M/D 不含人數、部門 · 類型、負責人、來源、建立 HH:mm），歷程只有標題與空清單。`lib/status-card.ts`：`statusTag` 14 種照 ui-plan 畫面 8 色表全做；`statusCard` 只做「進行中」，其他回 null 留給 4-W21；新增參數 `serverUtcOffsetMinutes`。`shell/DeskShell.tsx`：詳情開著時隱藏側欄（照雛形 `ui/desk.js:350`），不寫入使用者的側欄選擇，按「打開側欄」先關詳情。英日越 `detail.*` 是暫定譯文。待派單的單部門列顯示既有的「未分部門」（feature 沒涵蓋）。C-07 待確認（`詳情面板內容.feature:222`）照寫「同仁報修（{name}）」。風險：`DeskTaskDetail.guest` 為 null 就顯示「空房」，若後端對在住房回 null 會誤判，本次未發生。
- **4-W20（T511）完成（獨立驗收通過）。** 「內容」六列收成 `lib/detail-content.ts` 的 `detailContentRows(task, now, serverUtcOffsetMinutes, t)`，另匯出 `createdAgoText`（白卡灰字與建立列共用）；16 條 Vitest。分鐘數算法統一用 `lib/wait-pill.ts` 的 `minutesSince`（無條件捨去、負數歸 0），`status-card`、`answer-text` 改用它。負責人沿用 `??` 語意（後端回空字串照顯示）。`answer.justNow` 與 `detail.justNow` 是不同鍵，未合併。
- **4-W21（T512）完成，23／25（獨立驗收通過）。** `lib/status-card.ts` 的 `statusCard` 補齊所有狀態（當場回答、待認領、已接四種說明、進行中、已完成三種、查房通過、免查房、已結案、中斷請假／本人暫停、延後主管／本人、已取消四種），待派單回 null；修正 `statusTag` 的「已回答」（後端當場回答後是 `done`＋`answered`，原本只認 `closed`，306 會顯示成已完成；呼叫端只有 `DetailPanel`）。`DetailPanel` 訊息卡全由純函式算出；新增 `useRereadWithBoard`（看板讀成功就重讀開著的詳情，不閃骨架）。`detail.cards` 新增 31 鍵，英日越暫定。待決條目的取法：同仁撤回的報修人先取 `cancellation.byName` 再退 `createdByName`；`interruption.bySelf` 非 true 就當請假，沒交代寫「—」；主管延後說明用 `deferral.byName`。已結案但不是工務單的單規格沒寫，暫時只顯示標題。**延後到 4-W25：**`狀態標籤與狀態訊息卡.feature:161`（412 認領）、`:174`（305 完成）——面板開著時同仁在手機上的動作要即時反映，規格靠即時訊號（`單筆詳情/dsl.md:5`、`dsl.md:78`），看板成功時沒有定期重讀（`api/query-client.ts:13-15`），驗收確認延後合理；4-W25 做完重驗。
- **4-W21（T513）完成（獨立驗收通過）。** `lib/status-card.test.ts` 從 6 條補到 40 條：用真的 i18next 載入 `zh-TW.ts` 翻成中文，比對 feature 字面與 ui-plan 畫面 8 色表；`statusTag` 14 種各一條（已完成另測備品與其他），`statusCard` 每種卡各一條。實作與規格沒有不一致，沒改行為；`statusTag` 的重複字面收成 `tag(key, tone)`。「已結案但不是工務單」規格沒寫，照現行（只有標題、淺綠）寫測試，同 T512 的待決。
- **4-W22（T514）完成，23／25（獨立驗收通過）。** `lib/timeline-sentence.ts` 的 `timelineSentence(entry, task)` 回句子、操作者、點的顏色，feature 沒寫句子的事件種類回 null 不顯示（`rated`、`resumed`、`returned_to_pool`、`inspection_failed`、`repair_verification_failed`、`plan_adjusted`、`checkout_imported`、非系統的 `assigned`、本人 `deferred`、系統 `created`）；`timelineMeta` 寫「HH:mm · 操作者」，不是今天前面加「M/D」（校正後的現在＋伺服器時區偏移）。`DetailPanel` 新增 `Timeline`（`<ol>`，照雛形 `.events`，點色照 `ui/desk.js:324`）。英日越 `detail.events.*` 暫定。取捨：住客打字的句子用 `guestText.original`；請假中斷寫目前負責人（後端事件沒記誰請假）；免查房完成只寫「回報完成」；主管延後只寫「延後」。**延後：**`歷程.feature:90`（派單後復原）的 Given 要在詳情面板按「派給工務」，面板派單區屬 4-W23，**T516 做完要重跑**；`:101`（面板開著時同仁完成）要即時訊號，延後到 4-W25。後端主管延後一律回 `bySelf=false` 且不帶內容（`DeskBoardService.java:443-444`），同既有待決「延後事件的內容說法不同」。
- **4-W22（T515）完成。** `lib/timeline-sentence.test.ts` 從 5 條補到 47 條（只改測試檔，產品碼沒動）：feature 已定案句子每列一條（句子、操作者、點色；點色照 `ui-plan.md:367-384`）、暫定句子 7 條、略過的事件 13 條回 null、`timelineMeta` 今天／非今天／跨日邊界／三種時區偏移／未校正。主對話確認只動了測試檔、47／47 過，並抽查期待值與 `歷程.feature:18-21,79-86` 字面一致；Test Scope 由實作者重跑仍 23／25。回交上游（現在不用修）：`timeline-sentence.ts:83-86` 派單句不看是誰派的，目前後端只有櫃檯派單才寫這個事件（`TaskEngine.java:119`）；之後若規則自動派單也寫這個事件，要由 `/dsl-refine` 定「{規則說明}，進{部門}待認領」等句子（`ui-plan.md:371-372`）。
- **4-W23（T516）完成，32／32（獨立驗收通過）。** 新增 `lib/detail-actions.ts`：`hasDispatchArea`（只有待派單）、`managementActions(role, task)`（照雛形 `ui/desk.js:592-596` 與 `ui-plan.md:437-439`：待認領櫃檯「取消」「延後…」、主管與值班主管多「指派…」；已接、進行中、中斷、延後中只有主管與值班主管看得到「取消」「延後…」「轉派…」；已當場回答、其他狀態、沒有角色都沒有動作列），19 條 Vitest。`DetailPanel` 的 `DetailFooter`：待派單是名稱「派單」的三格，派給房務／工務走待派單頁同一個 `useAssignTask()`（`via: 'button'`，浮條、復原、撞單對話框全沿用；`assignDeskTask` 全專案只有 `use-dispatch.ts:50` 一處呼叫），「當場回答…」開當場回答面板；派單區要詳情與看板快取都還是待派單才顯示，派出當下就收起。動作列按鈕不設 `disabled`（照雛形 `data-inert`，可按但沒反應），沒有寫入呼叫；角色只讀 `session.role`。英日越 6 個新鍵暫定。`歷程.feature:90` 隨之轉綠，`單筆詳情` 目錄 117 中 114 綠（紅只剩要即時訊號的 `狀態標籤:161,:174`、`歷程:101`）。
- **4-W23（T517）完成。** `lib/detail-actions.ts` 的 `managementActions` 改成查 `MANAGEMENT_ACTIONS` 對照表（`Record<TaskState, Record<'frontdesk'|'manager', …>>`，每列一個狀態、附中文註解，型別強迫列出全部狀態；feature 沒列的 `triage`、`interrupted`、`inspected`、`closed` 照 ui-plan 與雛形 `desk.js:592-596` 填）；`VIEWER_GROUP` 把櫃檯、主管／值班主管分兩組，其他角色沒有動作列；函式只處理例外（沒有角色、已當場回答）。測試 18 條，期待值照 feature 寫常數。只動這兩個檔。主對話確認：只改了這兩個檔、18／18 過、表格內容與 T516 驗收確認過的規則一致；Test Scope 32／32、`單筆詳情` 114／117 由實作者重跑。
- **4-W24（T518）完成，21／21（獨立驗收通過）。** 拖曳層 `pages/board/board-drag.tsx`（`@dnd-kit/react` 0.5.3，沒有新增相依）：只掛 `PointerSensor`（滑鼠 6px、手指按住 250ms 容許 5px，照 T004），不掛鍵盤感應器（否則卡片上按 Enter 會起拖而不是開詳情）；放開先看是否取消（Esc），再取落點，走 `useAssignTask(task, dept, 'drag')`，浮條、復原、撞單、送出失敗全沿用；跟著走的卡片 `aria-hidden`、`inert`、不吃點擊。`BoardPage` 只讓待派單卡片註冊成可拖（不是註冊成停用，避免被標 `aria-disabled` 點不開詳情），拖曳中在「待認領」欄最上面放拖放區、其他格變淡。`lib/drag-dispatch.ts`：`canDragToDispatch`、`dropZoneDepts`（不分泳道兩個都有；部門泳道各在自己那列、未分部門沒有；樓層泳道只在這張單那一層）。**產品碼有一個備援：**套件要等下一個畫面更新才判斷落點，手指移到拖放區馬上放開時落點是空的（iPad 場景第一次紅就是這個），所以套件沒給落點時，再看放開那一點底下是不是拖放區（`dropZoneUnder`，看 `data-drop-dept`）；驗收確認拖放區外放開、按 Esc 都不會誤派。拖曳中狀態暫放 React context（`useDraggingTask()`），T519 換成 Zustand。英日越 `board.dragHint`、`board.dropZone` 暫定。**要人工：**真機 iPad 的手感、拖曳與捲動互搶、長按選單，以及上面的備援在真手勢下的表現（自動化只是 T005 的假觸控事件）。
- **4-W24（T519）完成（獨立驗收通過）。** 拖曳中狀態改放 `stores/board-drag.ts`（`useBoardDragStore`：`dragging`、`startDrag`、`endDrag`、`reset`，只放記憶體）；`board-drag.tsx` 拿掉 context，`BoardDragDispatch` 是唯一接點（起拖、放開、Esc、備援落點、`useAssignTask(..., 'drag')`），元件卸下時清拖曳狀態（狀態變全域後，拖到一半換頁或登出套件不會通知放開）。`lib/drag-dispatch.ts` 新增 `cellWhileDragging`（從 `BoardPage` 的 `Cell` 搬來，拖放區沿用 `dropZoneDepts`）與 `dropDeptOf`（取消→套件落點→備援），8 個 Vitest。`lib/persistence.ts` 加「看板上正在拖哪一張卡片」（記憶體、登出清），`log-out.ts` 跟著清。`lib/dispatch.ts` 的 `isAssignDept` 改 export。提示文字與格子變淡都由正在拖的單算出，不另存。
- **4-W25（T520）完成，5／10（獨立驗收通過，紅因屬實）。** `features/realtime/stream-client.ts`：用 `fetch` 讀 `GET /staff/stream`（`Authorization` 唯讀取自 `api/client.ts` 的 `authHeaders()`），純函式 `takeStreamEvents` 解析（`event:`／`data:`、CRLF、跨塊的 `\r\n`、註解行），讀取丟錯、正常結束、非成功回應都當斷線，1 秒後重連、之後每 5 秒，收過資料就重置；7 條 Vitest。`use-live-updates.ts`：`task` 讓 `deskBoardQuery` 失效（進行中的讀取取消重來），`resync` 只在看板讀成功時重抓；詳情沿用 `useRereadWithBoard`。`DeskShell` 呼叫 `useLiveUpdates()`，兩頁共用一條連線；連線綁登入憑證，登出換人時 effect 清理關閉，`log-out.ts` 不用改。**串流遇 401 當斷線重連，不帶回登入頁、沒動 401 統一處理**，等另一個 session 的 Session 那題接上。太久沒資料的計時、提醒列、斷線輪詢屬 T522，所以後端卡住但沒斷線時目前不會自己重連。紅：`:17`、`:26`、`:82`（住客送出 401，`BackendChannel.java:2240-2244`）、`:74`（住客收回 401，`:2271`），延後同住客 401 那批；`:52` 是規格資料矛盾，見「待使用者決定」。**延後的 `狀態標籤:161`、`:174`、`歷程:101` 轉綠，`單筆詳情` 117／117；`待派單清單:150` 仍紅，只剩住客 401。** 串流讓每次變化多一次看板讀取，TRUNCATE 死結可能略增（未證實），本題兩次死結單獨重跑都綠。
- **4-W25（T521）完成。** 兩層：`features/realtime/stream-client.ts`（只引用 `api/client` 的 `authHeaders`、`backendUrl`，不認識 React Query 與看板）——重連時間表抽成純函式 `retryDelayMs`，新增連線狀態 `StreamConnection`（`connecting`／`connected`／`disconnected`／`reconnecting`，經選填回呼 `onConnection` 往外報，關掉後不再報；`connecting` 是內部值，避免 T522 把剛開頁當斷線）；`refetch-on-signal.ts`（新）的 `refetchForSignal` 只認訊號名稱、不碰 fetch 與解析。`use-live-updates.ts` 只剩接線，`onConnection` 留給 T522。Vitest 共 18 條（解析 7、假時鐘重連與狀態 6、重抓對應 5）。主對話確認：改動只在 `features/realtime/`、兩層引用乾淨、18／18 過；Test Scope 5／10 與各目錄由實作者重跑，與 T520 相同。
- **4-W26（T522）完成，8／8（獨立驗收通過）。** `stream-client.ts` 加 35 秒沒資料就中止連線（`SILENCE_LIMIT_MS`，每收到一塊資料重設，照 T006），再照斷線規則重連。新增 `features/realtime/live-health.ts` 的純函式 `nextLiveTrusted`：斷線、`degraded`、heartbeat `degraded=true` 變不可信；`recovered` 或 heartbeat `degraded=false` 才回可信（單純重連不算）；剛開頁算可信；4 條 Vitest。`use-live-updates.ts` 回傳可不可信（跟著登入憑證記）；`refetch-on-signal.ts` 收到 `recovered` 比照 `resync` 重抓。新增 `shell/ConnectionAlertBar.tsx`（`role="status"`、沒有按鈕，樣式照雛形 `.conn-bar`），`DeskShell` 放在工具列與內容之間：不可信或「有資料後重讀失敗」時出現；不可信期間每 4 秒重讀看板（`UNTRUSTED_REFETCH_MS`），回可信就停；重讀失敗沿用 `loadStateOf` 留舊資料。英日越 `shell.connectionAlert.*` 暫定。注意：「太久沒資料」計時用瀏覽器時鐘，之後有撥瀏覽器時鐘超過 35 秒的場景，會觸發一次重連、提醒列可能短暫出現。可信判斷集中在 `live-health.ts`，提醒列與定時重讀只看 `useLiveUpdates()` 回傳的一個布林。
- **4-W26（T523）完成。** 連線狀態收成 `features/realtime/live-connection.ts` 的狀態機（純函式）：`LiveState { link, trusted, failuresInRow, receivedSignal }`，事件 `connected`／`disconnected`／`silence`／`retryDue`／`signal`，`transition` 遇到沒作用的事件原樣交回（每 15 秒的心跳不會讓外框重畫）；讀值 `showsConnectionAlert`、`boardPollMs`（可信 null、不可信 4 秒）、`retryDelayMs`；35／4／1／5 秒常數搬到這裡、數值沒動。`stream-client.ts` 只剩 I/O，把事件餵進 `transition`，`onConnection` 換成 `onState`；舊連線被「太久沒資料」關掉後才報的斷線會略過。`live-health.ts` 併入後刪除。`DeskShell` 用 `boardPollMs(live) ?? retryEveryFiveSecondsAfterFailure` 與 `showsConnectionAlert(live) || 有資料後重讀失敗`。Vitest：轉移表（每個事件 × 4 連線狀態 × 可信／不可信）、讀值、6 個情境（含「剛開頁算可信」「單純重連不回可信」）；realtime 共 62 條。兩條待決（4 秒與 5 秒、提醒列閃一下）現在都只要改這一檔。主對話確認：讀過 `transition` 與讀值函式，規則與 T522 一致、數值沒變、62／62 過；Test Scope 8／8 與已綠清單由實作者重跑，紅的與之前相同。
- **4-W27（T524）完成，desktop 與 ipad 各 22／24（獨立驗收通過，兩個紅屬實為規格／測試問題）。** 新增 `lib/board-layout.ts`（欄最窄 200、篩選後最寬 260、欄距 10、左右 16；`boardContentWidth`、`boardColumnWidth`、`columnsHiddenOnRight` 容許 1px），7 條 Vitest 照 `ui-plan.md:48-54` 四列核對。新增 `pages/board/board-more-states.tsx`：「還有 N 個狀態」按鈕（`min-h-11`）與右緣淡出，照雛形 `desk.js:472-489`，捲動或尺寸改變時重算、按一下平滑往右一欄；名稱用完整的「還有 N 個狀態」。`BoardPage`：看板本體貼齊內容區（`-mx-4`），16px 邊距搬進捲動內容；欄頭、格子、骨架改 `flex-1`（最窄 200、篩選後最寬 260）；泳道標題黏 `left-4`、欄頭加 `data-board-head`。**N 算不攏（Phase 3 記的待決）用這個方式解掉，沒改 ui-plan 數字：**原本 1280 收起時 1248 放不下 6×200＋5×10＝1250（差 2px）而得 3，看板貼齊後第 6 欄右緣 1266 放得進，N＝2，四列都吻合。英日越 `board.moreStates` 暫定。紅的兩個見「待使用者決定」（`:77`、`:102`）。要人工看：「還有 N」按鈕會蓋住部分露出那一欄欄頭右上角（雛形也是）；卡片捲動會到內容區邊界，不停在 16px 內側。
- **4-W27（T525）完成（獨立驗收通過）。** 版面尺寸收進 `app/web/src/index.css` 的 `@theme static`「版面尺寸」（外框最小 1180×720、側欄 300、工具列 64、提醒列 44、數量卡 72、控制列 48、內容邊距 gutter 16、可點最小 touch 44、看板欄最小 200／篩選後最大 260、欄距 10、搜尋框 220、詳情 400、對話框 300、登入表單 420、表單標籤 116、帳號面板與當場回答面板尺寸、圓角 card 14／tile 18／control 10），元件改用 `min-w-shell`、`w-sidebar`、`px-gutter`、`gap-board`、`min-h-touch`、`rounded-card` 等；用 `static` 確保 JS 以 `var()` 讀的變數一定輸出。`lib/board-layout.ts` 拿掉 200、260、10、16 改讀同一份 CSS 變數（`boardColumnWidth` 改回傳 class），數字只剩一個來源。44px 只套在可點的東西。登入、設新密碼、輸入飯店代碼三頁只換尺寸 class。刻意留下的一次性數值與理由見 scratchpad `t525/inventory.md`（元件內部高度 52／54／60、圖示、語言選單 220、拖曳縮圖 184、復原浮條寬、拖放區 72、看板頁整頁 `gap-2.5` 等）。驗收以 build 後 CSS 值逐項對照原值確認像素不變（沒做截圖比對）。
- **（truth 測試資料修正，待 `/dsl-refine` 確認）`frontdesk-web/待派單/撞單與送出失敗.feature` 背景表格補上「時間」欄（305 2 分鐘前、306 3 分鐘前、402 5 分鐘前）。** 原本三張單沒有時間、測試時鐘固定，建立時間相同，後端以隨機 UUID 決定先後，`:53` 要的「305、306、402」每次跑順序不同。照同目錄 `按鈕派單與復原.feature` 背景表格的寫法補時間，讓資料決定順序；場景語意不變，比照使用者「規格前置資料違反時改資料」的原則。補後連跑三次 `:53` 皆綠。
- **執行順序調整（使用者 2026-11-17 同意）：** 飯店隔離驗收完後，先做看板六組 4-W08～4-W13（T486–T497），T481、4-W06、4-W07 延後到看板之後補做。看板做的時候外框沿用現有側欄與工具列雛形，W06 補完前側欄導覽項目可能不完整。
- **4-B04 為了讓再讀確認能跑，先做了看板與詳情的最小回應**（`service/DeskBoardService.java`）：看板回全部的單、不篩範圍；`vip`、`guestTyped` 寫死 false；`guestName`、`guestText`、`assigneeName`、`waitingSince`、`acceptedAt`、`acceptedVia`、`interruptedAt`、`inspection`、`sop`、`interruption`、`deferral` 都是 null；`floors` 空、`myToday` null；詳情的 `answeredByName`、`guest`、`createdByName`、`accepted`、`cancellation`、`repair` 都是 null；歷程列出全部事件、不做成對略過。4-B07、4-B08 補齊。下列場景目前是「弱綠」（只驗了有出現或預期就是空），補齊後要重驗：`櫃檯看板讀取.feature:32`、`單筆詳情與歷程.feature:32`、`櫃檯介面的角色權限.feature:26`、`:27`。
- **4-B04 在 `SecurityConfig` 加的放行**：`POST /api/v1/desk/tasks/*/assign` 給 `frontdesk`、`manager`、`duty_manager`。4-B10 整理櫃檯權限時接手。
- **住客收回已經會寫 `ended_at`**（4-B04 先做，因為測試前置要用）。4-B07 盤點走到終點的路徑時，這一條已完成。寫入時 `ended_at` 與事件時間各取一次時鐘，正式環境可能差幾毫秒；資料模型寫兩者相等，4-B07 可順手改成取同一個值。

## 004 的 worktree：Maven 本機倉庫要和主工作目錄隔開

004 在 git worktree `.claude/worktrees/004-frontdesk-web`（分支 `feature/004-frontdesk-web`）做，主工作目錄同時有別的工作在跑員工 App 的驗收，兩邊都會用到 `com.athena:app-bdd-support:0.0.1-SNAPSHOT`。004 改過的 `BackendChannel` 預期資料庫已套 V24，裝進 `~/.m2` 會弄壞另一邊。所以**這個分支合併之前，worktree 裡凡是會解析或安裝 `app-bdd-support` 的 mvn（`app/bdd-support`、`app/bdd`、`app/web-bdd`，含 `app/bdd/lib/env.sh` 與之後的 `run-feature.sh`）都要先設**：

```
export MAVEN_ARGS="-Dmaven.repo.local=/Users/Caresys/.m2-004/repository -Dmaven.repo.local.tail=/Users/Caresys/.m2/repository -Dmaven.repo.local.tail.ignoreAvailability=true"
```

004 自己 install 的東西進 `~/.m2-004`，其餘相依從 `~/.m2` 唯讀取用。沒設這個變數時行為和平常一樣（會裝進 `~/.m2`）。`tms/` 的測試不依賴 `app-bdd-support`，不需要這組參數。

## T016 `BackendChannel`：給後面的題的注意事項

- 櫃檯 Web 驗收的預設資料用 `ensureFrontdeskDefaults()` 建：飯店「雅典娜酒店」（代碼 `athena`）、12 個帳號（密碼都是 `Tms12345`）、五頁住客資訊頁、2–5 樓每層 01–16 號共 64 間房。每次呼叫都會把帳號寫回預設狀態（未鎖定、啟用、非臨時密碼），可以每個場景開頭都呼叫；先 `resetBusinessData()` 再呼叫也會重建。
- **呼叫 `ensureFrontdeskDefaults()` 之後，同一個 `BackendChannel` 物件之後所有的資料準備都改成對雅典娜酒店做。** 員工 App 測試線不呼叫它，行為不變。
- 預設帳號的員工編號（`F0101`、`M0001`、`D0001`、`H0201`…）和 V2 種子、`scripts/seed-demo.sh`、員工 App 測試飯店的都不相撞。之後新增帳號要維持這一點，原因見「待使用者決定」的登入不帶飯店那一條。
- 住客資訊頁的 `key`（`breakfast`、`wifi`、`checkout`、`gym`、`taxi`）是自取的，truth 沒有規定。

## T017 `BackendChannel` 建各種狀況的單：給 Phase 3（T139 一帶）的注意事項

- **入口只有一個**：`createTaskInSituation(TaskSituation, TaskSituationSpec)`。規格表格的「狀況」欄用 `TaskSituation.ofTerm(字串)` 查，23 種用語和 `dsl.md` 逐字相同；住客、VIP、入住日、退房日用 `createReservation`。
- **狀況是直接寫資料庫造出來的**，因為後端的動作都以「現在」記時間，排不出「25 分鐘前」「昨天結案」，而且有幾種狀況後端沒有對應的動作。直接寫的單不會發即時訊號，開著的櫃檯畫面不會自己更新；「畫面還沒跟上」那幾句要用走後端的五個入口（`deskAssign`、`deskUndoAssign`、`deskAnswer`、`guestSubmitRequest`、`guestWithdrawRequest`）。
- **走後端的五個入口還沒有對真的後端跑過**。派單、復原、當場回答三支要等 4-B04、4-B05、4-B06 做完才不是 501，櫃檯與值班主管還要等 4-B10 放行；`guestSubmitRequest` 建的單在 4-B07 之前沒有 `task_no`。
- **時刻**：`at` 是該狀況的計時起點，沒給時取後端測試時鐘的現在。比它早的時刻沒給就沿用後一個。
- **待認領的單「時間」晚於「建立」時，會被建成退回過的單**：沒指定誰退回時當成班末自動退回，並自動挑該部門員工編號最前面的在職同仁當退回前的負責人，所以這張單的歷程會多一筆那位同仁認領。要主管退回就給 `returnedBy` 加 `handoverNote`；請假退回給 `poolSource("leave_return")` 加 `returnedBy`。
- **「經手人」要由步驟分派到對的欄位**：依狀況對到 `triagedBy`、`assignedBy`、`actedBy` 或 `createdBy`。查房通過、已回答、延後中（主管延後）、已取消（同仁撤回）都必須給人，表格沒寫時步驟要自己定預設（`dsl.md` 沒寫）。「已取消（同仁撤回）」的報修人對到 `createdBy`，這個狀況不收負責人。
- **住客的單要帶在住訂房的 `reservationId`**，之後 `guestWithdrawRequest` 才收得回。
- **打掃單的 SOP「已做／總數」**：總數是飯店啟用了幾項清潔 SOP，要先用 `ensureCleaningSopItemId` 建到那個數，再給 `sopCheckedCount`。
- 同一位同仁同時只能有一張進行中，資料庫會擋；這是正確行為。**表格沒有「負責人」欄而有兩張以上進行中時，步驟要替每一張挑不同的同仁**（照該部門預設名單輪流），不能一律給同一個人。目前有兩處：`總覽看板/數量卡與欄頭.feature:7`（510、511 房）與 `總覽看板/看板分欄與範圍.feature:127`（305、306 房）。
- 主管延後的歷程事件沒有 `bySelf` 這個鍵，讀的一方要把沒有當成否。

## T020 `app/web-bdd/` 的骨架：給 Phase 3 與 Phase 4 的注意事項

- **stepdef 的落點**：`steps/common/Common{Given,When,Then}Steps`（介面根共用句）、`steps/account/`、`steps/shell/`、`steps/board/`、`steps/triage/`、`steps/answer/`、`steps/detail/`、`steps/realtime/`，各有 Given、When、Then 三個類別，建構子只收 `WebWorld`。畫面的操作與讀取寫進 `pages/` 下對應的 page object（`LoginPage`、`ChangePasswordPage`、`ShellFrame`、`BoardPage`、`TriagePage`、`AnswerPanel`、`DetailPanel`、`AccountPanel`）；這幾個檔多組共用，改之前重新讀檔、只做最小追加。`support/` 下六個檔當唯讀。
- **每個場景開頭**會清空開發資料庫（5433）的業務資料、重建雅典娜酒店的預設資料、把後端與瀏覽器時鐘撥到 2026-10-26 14:10；結束時關掉這個場景開的所有分頁並把後端時鐘還原。
- **「已登入」的場景在 4-B02 做完之前一定是紅的，不是注入寫錯。** 後端現在的登入回應還沒有 `loginId` 與 `hotelName`（契約有、4-B02 才補），而櫃檯 Web 檢查登入狀態時兩個都要，所以 `SessionInjector.loginAs` 原樣注入後會被導回登入頁。4-B02 排在所有櫃檯 Web 的 Feature phase 之前，屆時就會通；Phase 3 寫步驟時看到被導回登入頁不要去改 `SessionInjector`。
- **等待一律用 Playwright 的等待**（`page.waitForCondition`、`waitForTimeout`、`network.waitUntilHeld`）。Playwright for Java 只在呼叫它的 API 時才處理瀏覽器事件，用 `Thread.sleep` 迴圈等請求或畫面永遠等不到。
- **時間以秒或整分為界的場景，用 `Clocks.advance`／`moveTo` 撥到位，不要靠真實時間流逝。** 瀏覽器時鐘照真實速度走、後端時鐘固定，場景每多跑一秒瀏覽器就比後端快一秒；`moveTo` 會重新對齊。排「N 分鐘前」的單時避開離整分不到 30 秒的時刻。真的出現跨分抖動時，備案是兩邊都凍結（`setFixedTime`，T005 實測過拖曳仍可用）。不要用 `pauseAt`。
- **已連上的事件串流，一般的路由攔截與離線模擬切不斷**，要用 `Network.cutLiveStream()`／`pauseLiveUpdates()`。它靠每個場景常駐的一段腳本包住 `fetch`；副作用是產品拿到的串流回應 `url` 是空字串，串流客戶端不要依賴 `response.url`。
- **登入憑證的效期跟著後端時鐘走**：後端時鐘固定在 10/26 時簽的憑證，把時鐘撥得很遠之後可能過期；撥大幅度時間的場景在撥完之後再登入。
- **「房號對應到哪一張單」的場景內紀錄還沒有共用類別**，做 `全館有以下的單：` 那一題時要開，其他組都會讀。
- **拖曳的 helper 還沒有寫**。做拖曳派單的步驟時照 T005 的結論：`desktop` 用 `page.mouse()` 分兩段；`ipad` 在頁面裡送 `pointerType=touch` 的指標事件，並先讓 `setPointerCapture`／`releasePointerCapture` 略過測試專用的指標編號。
- 失敗的場景會在 `app/web-bdd/target/failure-screenshots/` 留截圖，每個開著的分頁一張。

## T021 `app/web-bdd/run-feature.sh`：怎麼跑櫃檯 Web 的驗收

- 用法：`bash app/web-bdd/run-feature.sh [選項] [feature 路徑] [desktop|ipad]`。路徑相對於 `specs/truth/features/frontdesk-web/`，可以是一份 feature、`檔.feature:行號`（單一場景）或模組目錄；不給就跑全部。瀏覽器設定預設 `desktop`。
- 選項：`--keep` 跑完留著後端與開發伺服器（連續跑多份時從約 25 秒降到約 8 秒）；`--stop` 只關掉本工作目錄留著的後端與開發伺服器；`--rebuild` 強制重建後端。
- 結束碼：0 通過；2 環境或參數有問題、測試沒開始；130 被中斷；其餘等於 `mvn test` 的結果。打錯行號、一個場景都沒跑到時 Cucumber 會報錯，不會回 0。
- 後端原始碼（含 `db/migration`、`application.properties`、`tms/pom.xml`）比留著的後端新時，腳本會報錯要求 `--stop`，不會拿舊後端測；`--stop` 之後再跑會自動重建。
- 在這個 worktree 跑之前要先設「004 的 worktree」那一節的 `MAVEN_ARGS`，沒設時腳本只會警告。
- 同一個工作目錄一次只能跑一個，兩個同時跑會互相清掉資料庫。
- 每次執行的 log 在 `app/web-bdd/target/run-feature/`（`test.log`、後端與開發伺服器的 log）。

## 待使用者決定

- **V25 回填在三種少見情況的取法，請確認（目前沒有客戶資料，只影響開發資料）。** 回填已用一萬多張單的既有資料實測，與手寫的預期值逐列相符；下面三種是規格沒說、實測量到的行為：
  - 工務單已結案（`closed`）但沒有 `repair_verified` 事件：有 `completed` 事件就取它的時間（也就是完修時間，不是驗收時間），都沒有才用 `updated_at`；不讀 `repair_tickets.verified_at`。若要改成「只有不需處理結案才採用 `completed`，其餘依序取 `verified_at`、`updated_at`」，要改 V25；開發資料庫（5433）已經套過 V25，改檔後那個資料庫要重建或修復 Flyway 紀錄。
  - 查房通過（`inspected`）但沒有 `inspection_passed` 事件：同樣會取任何一筆 `completed` 事件，不分是不是免查結案。
  - 有 `answered` 事件但事件內容沒有回覆文字：`answer` 留空、`ended_at` 照補。現行程式寫這種事件時一定帶回覆文字，實務上不會出現；要不要退而取舊單寫在 `note` 的回覆，請決定。
  - 已結束但找不到任何對應事件的舊單，`ended_at` 用 `updated_at`。
- **V26 上線時會短暫鎖住 `task_events`。** 重建角色約束要在獨占鎖下把整張表掃一遍，建索引期間也會擋寫入。專案既有的遷移都是這種寫法，示範與測試的資料量沒有問題；正式環境的事件量若已經很大，要不要拆成不鎖表的寫法（約束先 `NOT VALID` 再另一支 `VALIDATE`、索引改 `CONCURRENTLY` 且不放在交易裡），請決定。
- **既有狀況，不是 004 造成的：** `staff_accounts.role` 允許 `owner`，但 `task_events.actor_role` 的清單沒有它；`owner` 帳號若走到會寫任務事件的操作，會被資料庫擋下。要不要處理請決定。
- **示範資料裡有一張類型是工務、卻沒有工務單的任務**（「202 浴室置物架鬆動」，部門是房務）。它既沒有 `T-` 單號也沒有 `W-` 單號。這是 T011 之前就有的示範資料，沒有動；要不要修正這一筆，請決定。
- **T011 的示範值班主管帳號用 `dm1`／`demo1234`，沒有照 truth 的 `D0001`／`Tms12345`。** 理由見下一條；示範飯店所有帳號本來就共用 `demo1234`。請確認。
- **不同飯店有相同員工編號是允許的（使用者 2026-11-17 裁決：「就沒關係，因為是兩家不同的飯店，系統可以允許這個問題」）。** 登入還不帶飯店的這段期間，後端不可以把它當錯誤擋掉，以不擋人的方式挑出要登入的那一家；改成登入帶飯店之後自然解決。
- **登入帶飯店：使用者 2026-11-17 裁決「照現在規格先做，之後再改」。** Phase 4-B02、4-B03、4-B11 照現在的契約與 `dsl.md`（登入請求不帶飯店）實作；登入選錯飯店的問題在這三組裡以現行規格可行的方式修到 Test Scope 綠。之後改成帶飯店時，要再走 `/api-plan`、`/dsl-refine`，並定手機 App 登入怎麼帶飯店。這三組排在 4-B04 之後接著做。
- **登入選錯飯店已在 4-B02（T442）修好，但相同員工編號留下一個過渡期的副作用，請知悉。** 根因是查帳號時沒有限定飯店，而後端測試的資料庫帳號不受列層級安全限制；改成每個查詢都帶飯店之後，正式角色 `tms_app` 與測試連線結果一致（已用一次性資料庫以 `tms_app` 實測）。依使用者裁決，不同飯店可以有相同員工編號：登入時逐家比對密碼，登入對得上的那一家；多家都對得上時登入飯店編號最小的；都對不上時回密碼錯誤，**而且每一家同編號的帳號各記一次錯誤**。副作用：A 飯店的人連續打錯 5 次，B 飯店同編號的同仁也會被鎖 15 分鐘；A 每次打錯再打對，B 的錯誤次數也會慢慢累積。改成「只記一家」或「都不記」會讓其他家的帳號可以被無限猜密碼，所以維持現行做法。這只在登入還不帶飯店的過渡期存在；若不能接受，唯一不降低安全的做法是在登入改成帶飯店之前，先禁止兩家飯店用相同員工編號。另外回應時間可以看出「這個編號存在於幾家」，影響低，登入帶飯店後一併消失。這條分支沒有自動化測試保護。
- **登入要帶飯店（使用者 2026-11-16 裁決：帳號以飯店加員工編號識別，`tenant_id` 是複合鍵之一）。** 現況是後端登入不帶飯店，`StaffAccountLoginService.resolveTenantId` 逐家找第一個有這個員工編號的飯店，兩家飯店有相同員工編號時第二家的人登不進自己的飯店。這要改登入的契約、後端登入邏輯、後端與櫃檯 Web 的登入規格，走 `/api-plan` 與 `/dsl-refine`，並在後端登入那一組 Feature phase（4-B02、4-B03）之前定案。還沒決定的是手機 App 登入時怎麼帶飯店（同一支登入端點）。在那之前 004 的測試資料用不相撞的員工編號避開。
- **飯店是隔離單位（使用者 2026-11-17 裁決，取代上面「照現在規格先做」與兩件待決）。** 原話：「我的目的就是一家飯店登入不會影響到另外一家飯店，飯店是一個隔離單位，我們可以使用 /athena/login /athena2/login 這樣去控制」；並選擇「現在一起做」登入帶飯店。據此：（1）飯店代碼確定放在網址路徑第一段 `/{飯店代碼}/...`；（2）櫃檯 Web 的登入綁在網址那家飯店，打開別家飯店的網址要重新登入那一家，不能沿用；不帶飯店代碼的網址不提供登入；（3）登入請求帶飯店代碼，後端只在那家飯店找帳號，同員工編號跨飯店不再互相影響（錯誤次數、鎖定、挑錯飯店都消失）；員工 App 目前不帶飯店代碼，維持現行行為，App 怎麼帶飯店另記待決。要改契約（`LoginRequest` 加飯店代碼）與後端，排在 T480 之後插做。沒帶飯店代碼的入口（`/`、`/login`、`/board` 等）是「輸入飯店代碼」頁，輸入後導到 `/{代碼}/login`，不列飯店名單、不呼叫任何會回傳飯店清單的 API（使用者 2026-11-17 同意）。
  - **使用者 2026-11-17 再裁決三件：**（1）員工 App 登入比照櫃檯 Web，用「飯店代碼＋員工編號」登入（App 要加飯店代碼輸入，屬員工 App 範圍，不在 004 內做，另開工作；做完後後端「不帶代碼逐家比對」的路徑就可以拿掉）；（2）輸入飯店代碼頁的文字同意（標題「輸入飯店代碼」、說明「請輸入飯店提供的代碼，或直接用飯店提供的網址開啟。」、欄位「飯店代碼」、按鈕「前往」）；（3）介面語言的鍵不分飯店，維持 `tms.frontdesk.language.staff.{員工編號}`。
- **飯店代碼放在櫃檯 Web 網址的路徑裡（使用者 2026-11-16 裁決）。** 目前的理解是放在最前面，四條路徑都掛在飯店代碼底下，例如 `/athena/login`、`/athena/board`；位置待使用者確認。T018 建的路由（`src/router.tsx`、`src/lib/paths.ts`、`src/lib/route-access.ts` 的轉址）與 `app/web-bdd` 的 `WebApp` 路徑常數還是沒有飯店代碼的版本，要在登入頁那個 phase（4-W01）之前改，和上一條登入帶飯店一起處理。
  - **4-W01（T472）已照「放在最前面」實作**：`/{飯店代碼}/login`、`/board`、`/triage`、`/change-password`，登入後與被導回登入頁都留在同一代碼底下（已實測 `/athena/login` 登入後完整網址是 `/athena/board`）；測試共用碼 `WebWorld.open` 自動接預設飯店代碼、`LoginPage.openFor` 走 `openPath`。**兩件待使用者決定：**（1）網址上的代碼和登入的飯店沒有綁在一起：已登入雅典娜時打開 `/olympus/board`，網址留著，畫面照樣是雅典娜的看板；建議登入時把網址代碼存進登入狀態、對不上就轉回登入那家，或請契約在登入回應加飯店代碼，可併入「登入帶飯店」一起改。（2）實作者另外保留了一組不帶代碼的路由（`router.tsx` 的 `routesUnder('')`），`/login` 可登入但不顯示飯店名稱；風險是根網址會落到沒有飯店的登入頁、過渡期靠員工編號挑飯店，以及代碼剛好是 `login`、`board`、`triage`、`change-password` 時只打 `/{代碼}` 會被當成頁面。二選一：拿掉這組路由改成「請用飯店的網址開啟」，或保留並規定這四個字不能當飯店代碼。
- **三處規格、資料模型、後端現況互相對不上，要上游裁決（測試資料目前照資料模型與契約寫）：**
  - 工務單的來源欄 `repair_tickets.origin`：`data-model.dbml` 有這一欄，migration 與程式都沒有；後端目前用任務的 `source=system` 推導。要補 migration，還是改資料模型。
  - 主管延後的單有沒有負責人：資料模型說沒有；`狀態標籤與狀態訊息卡.feature` 的 502 房填了負責人；後端現有的延後動作會把負責人設成按延後的主管本人。
  - 員工請假造成的「中斷」：規格與契約有這種狀況（`interruption.bySelf=false`）；後端現有的請假處理是把單退回待認領，沒有由系統中斷的路徑。要改規格，還是補後端。
- **「已取消（同仁撤回）」的報修人，兩份規格填在不同欄**：`狀態標籤與狀態訊息卡.feature:135` 填在「負責人」欄，`歷程.feature:121` 填在「經手人」欄。測試步驟會兩種都接受；要不要統一請決定。
- **「被同仁退回」在產品裡沒有對應的動作。** `等待膠囊與逾時.feature` 有一條規則說待認領的單被同仁退回後重新起算等待。後端會把單退回待認領的路有班末自動退回、主管附交代退回、請假退回等，但沒有「房務同仁自己把還沒開工的單退回」。測試資料目前把它當成班末自動退回。要改規格的用語，還是補這個動作，請決定。
- **資料模型與後端對延後事件的內容說法不同**：`data-model.dbml` 寫 `deferred` 事件帶原因與是否本人延後；後端主管延後時這筆事件不帶內容。測試資料照後端寫。
- **本機開發資料庫（5433）的示範資料已被櫃檯 Web 的驗收測試清空。** 驗收測試連的就是這個資料庫，每個場景開頭都會清掉業務資料。現在示範飯店、示範二店、測試飯店都是 0 個帳號、0 張單，只剩雅典娜酒店的預設資料；資料庫已套到 V26。要示範資料得重跑 `scripts/seed-demo.sh`（T011，還沒有實跑過），而且下一次跑驗收又會被清掉。要不要讓驗收測試改用另一個資料庫，請決定。
- **後端驗收規格的日期與測試時鐘的預設日期不一致（Phase 3 發現，回交 `/dsl-refine`）。** `backend/櫃檯看板與派單/單筆詳情與歷程.feature` 的住期是 10/25–10/27，後端測試時鐘預設是 2026-10-19，照寫建出來的訂房不算在住，住客送不了單。測試步驟目前把時鐘移進住期（時刻不變）來繞過。另外 `backend/dsl.md:10` 寫預設時刻是 10:00，測試時鐘重設後是 12:00。要改規格的日期與預設時刻，還是在 DSL 寫明「現在以住期為準」，請決定。
- **員工的排班佔位房間也算在客房主檔裡（Phase 3 發現）。** 後端測試建員工時會建 `PLANROOM-F{樓層}` 這種佔位房間（被排班資料引用、刪不掉），而櫃檯看板的樓層清單是從客房主檔取的，所以「飯店的客房樓層為 …」排不準。測試步驟目前把佔位房間改到清單裡最低的樓層。比較乾淨的做法是讓佔位房間不算客房，或櫃檯看板查樓層時排除它們；4-B07 做樓層時要決定。
- **共用外框兩句規格與畫面規劃的尺寸算不攏（Phase 3 發現，回交 `/dsl-refine`）。** 「排法與視窗調整前相同」要求每一塊的位置與大小都和調整前一樣，但 `ui/ui-plan.md` 規定外框最小 1180 × 720，從 1280 寬縮到 1024 時工具列與看板一定會變窄約 100px，照規劃做的產品過不了。另外「還有 N 個狀態」的 N：規劃表寫 1280 × 720、側欄收起時是 2，但照雛形的欄寬（200px、間距 10px、左右各 16px）只放得下 5 欄，算出來是 3。測試目前照 `dsl.md` 字面量。要改成只比對排法與高度，還是調整最小寬度或欄寬，請決定；後者在畫面功能階段設計欄寬時也要一併定。
- **「後端仍是待派單、歷程筆數與動作前相同」照字面會在「派出後按復原」的場景失敗（Phase 3 發現，回交 `/dsl-refine`）。** `frontdesk-web/dsl.md:95` 這一句要求歷程筆數不變，但「剛派出又按復原」時，動作前的歷程有那筆派單，復原之後後端會把它從歷程略過，筆數少一。測試目前改成「現在的每一筆都是動作前就有的，少掉的只能是派單那一筆」。建議規格改成「沒有多出新的一筆」。
- **櫃檯 Web 的驗收測試寫死了一批規格裡沒有的畫面約定，要補進規劃（Phase 3 審查發現）。** 產品照 `dsl.md` 做、但不照這些約定做，測試一樣會失敗。目前測試假設的有：瀏覽器儲存的鍵名（見「T018」一節）；載入中用 `aria-busy`、選中用 `aria-pressed`、對話框用 `aria-modal`、開著詳情的卡片用 `aria-expanded`；側欄導覽的名稱「櫃檯功能」；派單浮條是帶「復原」的 `role=status`；詳情面板是 complementary；看板每一欄、每條泳道是以名稱開頭的 region；顏色用 `data-tone`（gray／orange／red／green）。另外這些可及性名稱在英文、日文、越南文介面下叫什麼，要一起定（審查發現側欄開關鈕與詳情面板的找法原本寫死中文，日文、英文介面一定找不到）。建議由 `/ui-plan` 把這張約定表寫進 `ui/ui-plan.md`，畫面功能階段照著做。測試目前寫死的四組暫定譯文（`app/web-bdd/.../pages/UiWords.java`，不是出自雛形，待母語審稿；產品的可及性名稱要一字不差）： 分泳道時欄頭只有一列，每一格的名稱是「{欄名} · {泳道}」（4-W12／T494）。
  | 中文 | 英文 | 日文 | 越南文 |
  | --- | --- | --- | --- |
  | 打開側欄 | Open sidebar | サイドバーを開く | Mở thanh bên |
  | 收合側欄 | Collapse sidebar | サイドバーを閉じる | Thu gọn thanh bên |
  | 櫃檯功能 | Front desk menu | フロントメニュー | Menu lễ tân |
  | 詳情 | Details | 詳細 | Chi tiết |
- **「只寫建立、沒寫時間」的待派單或待認領，等待從哪裡算起，規格沒寫清楚。** 測試目前用建立時間；介面根 `dsl.md` 的預設是「0 分鐘前」。請在 `dsl.md` 註明採哪一個。
- **V25 上線順序：** 遷移套上去之後、新版程式啟動之前，舊版程式建的單不會有 `task_no`。上線時要避免這段時間有人建單，或事後補號。
- **（回交 `/dsl-refine`）`frontdesk-web/當場回答/當場回答面板.feature:97`「畫面上沒有 "復原"」和待派單頁首說明互相衝突（T506 發現，獨立驗收確認）。** 送出 305 的回覆後回到待派單頁，402 還在清單上，所以頁首照常顯示「點部門就送出，5 秒內可復原；當場回答會先開回覆面板」（`待派單/待派單清單.feature:49`、`空狀態與載入.feature:55` 要求）。`畫面上沒有 {string}` 比對整頁可見文字（`ShellFrame.java:389` `hasTextAnywhere`），一定抓到這句裡的「復原」。產品沒有浮條、也沒有「復原」按鈕，畫面是對的。建議把 `:101` 改成「畫面上沒有 "復原" 按鈕」或在 dsl 把範圍限定為派單浮條。在裁決前這個場景紅不算倒退；沒有改 truth 與 stepdef，也沒有藏頁首說明。
- **（使用者 2026-11-17 要求）004 分支 cherry-pick 了 feature/gherkin 的兩個 commit：3afa0c7→0fa58ec（主管可查自己清的房，V27）、fd6be26→cca6b4e（查房通過回寫 inspected、免查結案不回寫）。** 為了在 5433 上用 8080 手動驗查房（5433 已套 V24–V26，feature/gherkin 的後端起不來）。衝突處理：`IntegrationThenSteps.java`、`15-pms-integration.feature` 採 fd6be26 版本（004 原本的「不回寫 pms_status」步驟只有舊場景用，一併移除）；`TaskEngine.java` 未 commit 的 004 改動先存 patch、cherry-pick 後三方合併回去，`complete()` 照 004 的 `reachEnd` 寫法、拿掉免查回寫。後端全套 369／369（新基準 `cherry-regress/full.json`）。5433 已套 V27。這兩個是本機 commit，未 push。
- **（使用者 2026-11-17 同意）再 cherry-pick f085610→ad4c207（修正舊測試線五個過時情境，只改測試）。** 原因：上一條解衝突時 `15-pms-integration.feature` 採 fd6be26 版本，用到的步驟「outbox 排入的訊息 kind 恰為 …」是 f085610 才加的，舊線變成步驟未定義。兩個衝突採 fd6be26 的較新版本；`StaffDeskThenSteps.java` 未 commit 的 004 改動以 patch 合併回去（乾淨套上）。結果：舊線 RunCucumberTest 153／153（004 之前就有的 5 筆失敗一併消失），truth 線 369／369（新基準 `cherry-regress/full2.json`）。本機 commit 共三個（0fa58ec、cca6b4e、ad4c207），未 push。
- **詳情面板的色調要不要統一成 `data-tone` 的四個值（T512 發現）。** 測試約定的 `data-tone`（gray／orange／red／green）只用在等待膠囊與數量卡（`app/web-bdd/.../pages/BoardPage.java:469`），詳情面板沒有測。T510、T512 照 `ui/ui-plan.md` 畫面 8 色表做：標籤六色（yellow、brand-soft、gray、brand、green、red）、訊息卡四色（green、brand-soft、red、white）。要統一成 `data-tone` 四值（方便測試與一致），還是維持畫面 8 色表，請決定。
- **（回交 `/dsl-refine`）`frontdesk-web/即時更新/現場變化即時跟上.feature:52`（小美在手機上開始 208）的前置資料做不到（T520 發現，獨立驗收確認）。** 背景表（`:10-11`）讓小美同時有 208 已接、510 進行中，但後端規定同一人同時只能有一張進行中（`TaskExecutionService.java:144-161` 回 409 `doing_task_exists`，V5 migration:40 也寫明），所以「開始 208」在後端就被擋，畫面還沒參與。建議改成開始 208 之前先讓 510 換人或結束，或讓 208 換一位負責人。裁決前這個場景紅不算倒退；沒有改 truth 與 stepdef。
- **（回交 `/dsl-refine`，T522 發現，獨立驗收確認）連線異常時「每 5 秒自動更新」與「5 秒內跟上」同時成立不了。** `連線異常提醒列.feature:30-34` 要求斷線期間同仁認領後 5 秒內畫面跟上（`RealtimeThenSteps.java:107` 用真實時間量），間隔若真的是 5 秒，加上讀取與畫面更新一定超過。現在實際間隔是 4 秒（`features/realtime/live-health.ts` 的 `UNTRUSTED_REFETCH_MS`），提醒列文字仍寫「每 5 秒」（`ui-plan.md:411`、C-10 `:660`）。建議在 feature 的 Rule 與 C-10 補一句「畫面上說每 5 秒，實際間隔略短，確保 5 秒內跟上」。另外讀取失敗時的再試仍是 5 秒，與提醒列期間的 4 秒不同，目前不影響測試；要不要統一請一併決定。
- **斷線重連後提醒列會閃約 15 秒（T522 照契約做，T006 留下的待決）。** 照契約，提醒列要等到 `recovered` 或 heartbeat `degraded=false` 才消失，單純重連不算；後端心跳每 15 秒一次，後端每 30 分鐘主動收掉連線時，提醒列最多會出現約 15 秒。要不要改成「重連失敗一次才顯示」請決定。
- **（回交 `/dsl-refine`，T524 實作確認）`共用外框/支援的視窗與輸入方式.feature:77`「排法與視窗調整前相同」做不到——就是上面「共用外框兩句規格與畫面規劃的尺寸算不攏」的前半。** 1280×720 調成 1024×600 後，只有寬度變：工具列 1280→1180、數量卡 1248→1148、看板 1280→1180，位置與高度都沒變；`ShellThenSteps.java:519-523` 逐一比寬高（容許 1px），`dsl.md:36` 寫「尺寸相同」，而 `ui-plan.md:29` 外框最小 1180。要三塊都不變只能把最小寬改 1280，iPad 1180 會出整頁捲軸、違反 `:27`、`:29`。兩條路：把 `dsl.md:36` 改成只比對排法與高度（建議），或明定滑鼠裝置最小 1280、觸控裝置最小 1180（改 ui-plan，1180–1279 寬的電腦視窗會有捲軸）。後半（「還有 N 個狀態」的 N）T524 已解掉。desktop、ipad 兩組都紅這一個，裁決前不算倒退。
- **（回交，測試碼）`共用外框/支援的視窗與輸入方式.feature:102`（iPad 看板「沒有只靠游標才看得到的東西」）是 stepdef 量法缺陷（T524 發現，獨立驗收確認）。** `ShellThenSteps.java:565` 對每個元素 `hover(setForce(true))`，Playwright 仍會把元素捲進視窗；目標清單（`:546-554`）含看板右側被捲動範圍裁掉的欄，第一次停游標就把看板橫向捲一欄，「還有 3 個狀態」變「還有 2 個狀態」，之後每停一個都多報這一筆。`:50` 本來就要求數字跟著捲動改變，產品無法避開。建議修 stepdef（不改語意）：每次停游標前記下各捲動區塊位置、停完還原再比對。依規則 Green 不改 stepdef，所以沒動；要由誰修請決定。desktop、ipad 兩組都紅這一個，裁決前不算倒退。
- **詳情面板寬度與 ui-plan 不一致（T525 發現，refactor 不改行為所以沒動）。** 產品固定 400px（`index.css` 的詳情寬度變數）；`ui/ui-plan.md`「版面尺寸與伸縮規則」寫視窗寬於 1440px 時最多到 480px，雛形是 `clamp(400px, 28vw, 480px)`。現行測試只跑 1280 與 1180 寬，看不出差別。要不要另開一題照 ui-plan 改成隨視窗變寬，請決定。
- **4-G（T526）ui quality gate 完成。** 審查（兩種視窗、攔截看板 API 回雛形同一套 32 張示範單；21 個單號的標籤、訊息卡、內容六列逐字與雛形相同）高 0／中 2／低 5，全部修掉並經原審查者複查「已修好、沒有修過頭」：狀態訊息卡圖示依底色換色（`DetailPanel.tsx`，照 `desk.css:252-255`）、「待認領」字色改 `text-brand-on-container`（#2F5357，ui-plan:343）、VIP 標 warm 色＋extrabold、泳道分段鈕形狀、等待膠囊換行時從左排（照雛形 `desk.js:405`；ui-plan:193 只寫靠右，沒先回 `/ui-plan` 定這一句）、「回覆住客」標題樣式、看板右緣淡出寬度補上邊距。複查另找到「還有 N 個狀態」按鈕偏右 16px（伸進頁邊距），改 `right-gutter` 後與「代開單」鈕對齊。修完 `單筆詳情` 117、`總覽看板` 84、`當場回答` 16／20、`待派單` 44／47、`共用外框` 40／42（ipad `支援的視窗` 22／24），沒有倒退。審查沒看到的：觸控拖曳與 WebKit、拖到邊緣自動捲動、換欄閃框動畫、雛形沒做的載入中與送出中狀態、被擋角色改完密碼的提示卡、英日越三語的看板（理由見 scratchpad `t526/review.md`「沒有看到」）。照 truth 與雛形不同、不算問題：歷程沒有「規則沒有命中，等櫃檯派單」、讀取失敗訊息的位置。
- **4-R（T527）後端回歸全綠。** 後端 Test Scope（櫃檯看板與派單 7、員工事件串流 2、帳號與登入 4、主管待辦空清單與權限、員工推播事件）171／171；後端全套 RunTruthFeaturesTest 369／369，逐場景與基準 `cherry-regress/full2.json` 比對由綠轉紅 0；舊測試線 RunCucumberTest 153／153。新基準 scratchpad `t527/full.json`。全程 Testcontainers，沒碰 8080／5433。
- **4-R（T528）櫃檯 Web desktop 回歸：417 中 403 綠，紅的 14 個全是已知、不算倒退。** 住客 401 共 10 個（`待派單清單:150`、`按鈕派單與復原:113`、`待派單/撞單與送出失敗:43`、`當場回答面板:83`、`:90`、`當場回答/撞單與送出失敗:34`、`現場變化即時跟上:17`、`:26`、`:74`、`:82`）；規格待決 4 個（`當場回答面板:97`、`現場變化即時跟上:52`、`支援的視窗與輸入方式:77`、`:102`）。整輪另有 `拖曳派單.feature:56` 一次 TRUNCATE 死結，單獨重跑整份 21／21。Vitest 37 檔 501 條全過，build 成功（只有 chunk 661 kB 超過 500 kB 的警告）。**所以「全綠」要等住客 401（另一個 session）與 4 個規格待決處理完才會成立。** 結果在 scratchpad `t528/`。
- **4-R（T529）櫃檯 Web ipad 回歸：417 中 403 綠，紅的 14 個與 desktop 完全相同（同樣的已知紅、紅因相同），沒有新紅。** 含 iPad 觸控拖曳（照 T005 的假觸控事件）。desktop 整輪那次死結的 `拖曳派單:51` 在 ipad 是綠的。結果在 scratchpad `t529/ipad-full.json`。真機 iPad 的觸控手感（按住起拖延遲、拖曳與捲動互搶、44 pt）仍要人工驗一次。
- **4-R（T530）只做了不需要 simulator／Appium 的部分，T530 沒有打勾。** `app/mobile` 的 `flutter test` 498／498；`app/bdd-support` install 與 `app/bdd` test-compile 通過（`BackendChannel` 搬家後編譯得過）；`app/bdd` 三個模組（帳號與登入、待認領、主管工作台）Cucumber dry-run 77 場景、547 步，沒有 undefined 或 ambiguous。**沒跑的：**三個模組實際連 Appium 與 simulator 的一輪——simulator、Appium、8081、5434、9094 屬另一個 session（handoff「不要動的範圍」），跑的當下也有一台 iPhone 17 simulator 開著。所以 `BackendChannel` 搬家、登入回應多欄位、`duty_manager` 放行對手機 App 的端到端影響還沒驗。
- **（待使用者決定）T530 員工 App 測試線怎麼跑完。** 選項：由另一個 session（擁有 simulator／Appium 的那個）在 004 分支上跑三個模組；或使用者安排一段時間讓這條線獨佔 simulator／Appium 來跑。跑完全綠才能勾 T530，004 才算交付。

### 使用者 2026-11-18 裁決（上面幾條待決）
- **`當場回答面板.feature:97`**：改成「畫面上沒有 "復原" 按鈕」（或在 dsl 註明只看派單浮條與按鈕），意思不變只把範圍講清楚。→ `/dsl-refine`。
- **`現場變化即時跟上.feature:52`**：背景表把 208 換一位負責人（例如阿美），「開始 208」也由她操作；510 與其他列不動。→ `/dsl-refine`。
- **`支援的視窗與輸入方式.feature:77`**：`dsl.md:36` 改成「排法、上下位置與高度與調整前相同；寬度縮到最小寬度為止」，ui-plan 不動。→ `/dsl-refine`。
- **連線異常的 4 秒／5 秒**：規格補一句「畫面上說每 5 秒，實際間隔略短，確保 5 秒內跟上」（feature 的 Rule 與 ui-plan C-10），維持 4 秒；**讀取失敗時的再試也統一成 4 秒**（產品要改）。→ `/dsl-refine`、`/ui-plan`、實作。
- **`支援的視窗與輸入方式.feature:102`**：使用者同意由 agent 修 stepdef 量法（停游標前後還原捲動位置），語意不變。
- **提醒列閃一下**：改成「重連失敗一次才顯示」——斷線後第一次重連就成功時提醒列完全不出現；重連失敗、太久沒資料、後端宣告降級才出現。→ `/ui-plan`（畫面 9）、若 feature 寫「斷線就出現」則 `/dsl-refine`、實作（`live-connection.ts`）。
- **詳情面板色調**：維持 ui-plan 畫面 8 色表，不統一成 `data-tone`；ui-plan 補一句兩者分工（`data-tone` 只給等待膠囊與數量卡）。→ `/ui-plan`。
- **詳情面板寬度**：照 ui-plan 改產品成 `clamp(400px, 28vw, 480px)`，ui-plan 不動。→ 實作。
- **T530**：先不跑，等其他事做完再一起跑測試。
- **員工編號大小寫（使用者 2026-11-18 裁決）**：櫃檯 Web 的員工編號「可以大小寫，要跟員工 App 一樣」，取消 `登入頁.feature:35`「自動轉成大寫」。→ `/dsl-refine` 改櫃檯 Web 帳號與登入規格、產品 `lib/login-form.ts` 不再轉大寫。起因：`scripts/seed-demo.sh` 建的是小寫 `mg1`，櫃檯 Web 送出 `MG1` 登不進去。
  - 已做（agent 回報，未另做獨立驗收）：App 照原樣送（`login_screen.dart:69-71,218,321`）、後端精確比對（`StaffAccountRepository.java:13`），後端不改。truth：`登入頁.feature:35-50` 拆成「打 `f0101` 顯示 `f0101`」與「員工編號設成 `f0101` 打小寫就能登入」，`帳號與登入/dsl.md` 新增 Given「"{員工}" 的員工編號是 "{員工編號}"」、When 改不轉大小寫不去空白、兩個 Then 改驗「送出的和輸入的一字不差」，truth-delta 加四列。產品：`login-form.ts` 只截 12 字、`use-login-form.ts` 不 trim、`LoginPage.tsx` `autoCapitalize="none"`；`canSubmitLogin` 拿掉 trim（只打空白也能按，使用者 2026-11-18 同意跟 App 一樣）。stepdef：`LoginPage.Submission` 記輸入值、`AccountScenario` 記改過的員工編號、`AccountGivenSteps` 新 Given、`AccountThenSteps` 兩個 Then。`帳號與登入` 89／89。ui-plan `:70`、`:465` 與驗收例子 `櫃檯人員在共用裝置上登入改密碼與登出.feature:70-71,87` 另派人改（使用者同意兩處都改）。
- **2026-11-18 裁決的實作（agent 回報，最後整體回歸再獨立驗收）：** 提醒列「重連失敗才顯示」——`live-connection.ts` 新增 `attemptFailed`（還沒連上就斷、或連上後沒收到任何訊號就斷算失敗），只有連線失敗或 `silence` 才變不可信；收過訊號的連線斷掉維持原狀、重連期間不顯示也不定時重讀（重連成功後端送 `resync` 會補漏掉的變化；重連卡住由 35 秒計時接手）。讀取失敗再試與提醒列期間重讀同用 `api/query-client.ts` 的 `REREAD_INTERVAL_MS = 4_000`（`retryEveryFiveSecondsAfterFailure` 改名 `retryAfterFailure`），畫面文字仍寫每 5 秒。`--width-detail-panel` 改 `clamp(400px, 28vw, 480px)`。新 stepdef「即時連線中斷，第一次重連就成功」（`LiveConnection.cutAndAllowFirstReconnect`）、「這段期間提醒列一次也沒有出現」（`MutationObserver` 監看，閃一下也記得到），並以暫時改回舊行為確認新場景會紅。結果：`連線異常提醒列` 9／9、`現場變化即時跟上` 6／10（`:52` 轉綠）、`當場回答` 17／20（`:97` 轉綠）。另由修 `:102` 的 agent 改了 `ShellThenSteps`（`:102` 停游標量法、`:77` 照新 dsl 比排法），`支援的視窗` desktop／ipad 各 24／24。
- **拖曳派錯部門（2026-11-18 發現，修正中）：** 拖到「給房務」放開卻送出工務，`拖曳派單.feature:52` 整份連跑 5 次紅 3 次（`:135` 也拍到同樣誤送）。疑似 `lib/drag-dispatch.ts:74-77` 的 `dropDeptOf` 先信拖曳套件的落點、套件有時記著舊落點。規格明確（放哪就派哪），屬產品錯誤，直接修，不需使用者決定。
  - **已修（agent 回報）：** 根因是 `@dnd-kit/dom` 把滑鼠移動延到下一個畫面才處理（`node_modules/@dnd-kit/dom/index.js:2190-2206`，排程 `:1580-1586`），放開卻馬上處理（`:2210-2230`），移動後同一畫面內放開時套件的位置與落點還停在上一次移動（重現：`:145` 收到 `engineering`，放開點底下是 `housekeeping`）。`lib/drag-dispatch.ts:55-89`：`DragRelease` 加 `targetIsCurrent`，`dropDeptOf` 改成 Esc 取消 → 放開點底下的拖放區 → 那裡沒有才用套件落點且只在 `targetIsCurrent` 時採用（避免「移出拖放區馬上放開」被舊落點派出去）。`board-drag.tsx` 新增 `releasePointOf`、`samePoint`、`dropZoneAt` 取代 `dropZoneUnder`。Vitest `dropDeptOf` 7 條。拖曳派單 desktop 連跑 5 次 21／21、ipad 21／21、`總覽看板` 84／84。**要人工：**真機 iPad 手指移到拖放區馬上放開、從拖放區移出馬上放開。
- **看板「做 N 分」與員工 App 計時不同（使用者 2026-11-18 裁決：維持現行規格）。** 起因：手動測試環境 308 補備品看板「做 110 分」、App 15:33。調查：看板是現在減 `started_at`（`wait-pill.ts:105-108`、`DeskTaskMapper.java:56`），中斷時間算在內；App 是累計工時（`task_screen.dart:1510-1520`）。兩邊都照各自規格。裁決維持「從第一次開始到現在」，有中斷過的單兩邊本來就會不同。補：truth `frontdesk-web/dsl.md:55` 與 ui-plan `:437`、`:602` 寫明中斷時間算在內；`scripts/seed-demo.sh` 補接單／開始／中斷等事件、讓 308 的 `started_at` 與 `worked_sec` 對得起來（另派 agent）。
