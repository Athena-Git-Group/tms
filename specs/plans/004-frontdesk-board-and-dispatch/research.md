# Phase 0 研究：櫃檯看板與派單

依據：`spec.md`（使用者故事 1–7、FR-001–103、NFR-001–008、「對既有行為的影響」「設計圖與後端現況的出入」「假設」）、現有的 `specs/truth/techstack.md`、後端與測試線的原始碼（引用處附 `檔案:行號`，路徑以 repo 根為準），以及使用者 2026-11-12 的原話與回覆：

- 原話：「REACT Web 測試可以考慮用 playwright 或者是你有更好的建議，後端直接共用目前的 tms 就好。」
- 回覆的六項決定：前端用 React、後端共用 `tms`；主要使用環境是電腦瀏覽器、同時支援 iPad 橫向；驗收測試沿用 Java 的 Cucumber-JVM、瀏覽器用 Playwright for Java 驅動；驗收測試為主線、只替純計算加單元測試；重新整理不用重新登入、關掉分頁或瀏覽器就要重新登入；這一包不決定之後包成 iPad App 的路線。

這是本專案第一次加入 Web 前端。既有的後端與員工手機 App 的技術決策不變，本文只記錄因櫃檯 Web 而新增的決策。外部套件的版本與維護現況是 2026-11-12 查得的，來源列在文末。

本文用到的幾個詞：

- **同源**：網頁與它呼叫的後端在同一個網址（同一個網域與埠）底下。瀏覽器對同源的請求不設限；不同源時後端要另外宣告「允許哪些網站來呼叫」，這個宣告叫跨網域設定（CORS）。
- **事件串流（SSE）**：瀏覽器開一條不關的連線，後端有事就沿這條線推一則訊息過來。
- **登入憑證**：登入成功後後端發的一串字（JWT），之後每個請求放在 `Authorization` 標頭裡帶給後端，後端靠它認人。
- **glue 套件**：Cucumber 用來找「每一句 Gherkin 對應哪段 Java 程式」的 Java 套件；套件裡標了 `@Before` 的程式會在每個情境開始前自動執行。
- **World**：一個情境執行期間共用的物件，握著瀏覽器或 App 的連線與這個情境建出來的資料。
- **spike**：正式動工前，花固定的短時間實際跑一次，確認某個做法行不行得通。

## 決策 1：系統有哪些端，櫃檯 Web 的程式與規格放哪裡

- **要決定什麼**: 這一包新增哪個端、後端要不要另起；新的端的程式放哪個 repo、哪個目錄；它的行為規格（truth 的介面根）叫什麼。
- **Decision**:
  - 系統現在有三個端：後端 API（`tms/`，沿用）、員工手機 App（`app/mobile/`，這一包不動）、櫃檯 Web（新增）。
  - 櫃檯 Web 直接呼叫現有的 `tms` 後端，不另起後端，也不加一層轉接服務。
  - 櫃檯 Web 的程式放本 repo 的 `app/web/`；行為規格放 `specs/truth/features/frontdesk-web/`，自己有一份 `dsl.md`。
- **Rationale**:
  - 端的組成是使用者原話指定的（「REACT Web」「後端直接共用目前的 tms」）。
  - 放本 repo 的理由與 001 把手機 App 放進本 repo 相同：畫面、行為規格、API 契約、後端可以在同一個 commit 一起改。這一包後端要補的東西不少（見「後端為這一包要補的事」），分開兩個 repo 會讓每一條規則都要跨 repo 對齊。
  - 介面根叫 `frontdesk-web` 而不是 `web`：住客掃碼頁也是 Web，之後進 truth 時單叫 `web/` 會撞名；`frontdesk` 用全寫，與資料上的角色值、部門值同一個字。
  - 這一項沒有另外問使用者，是照 001 的先例直接定的。
- **Alternatives considered**:
  - 另開一個 repo 放 Web：Web 可以自己發版，但契約與行為規格分散兩處，不採用。
  - 直接拿 `~/IdeaProjects/tms-web` 當基底：省掉起專案，但它是觀摩競品用的專案，資料全是假的、沒有測試、只有三種語言（沒有越南文），不採用，理由見決策 2。
  - 替 Web 另起一個專用後端（只負責把 `tms` 的資料整理成畫面要的樣子）：多一個要部署與維護的服務，使用者已指定共用 `tms`，不採用。
- **殘餘風險**: `app/` 底下會有 `mobile/`、`web/` 與測試模組並列；現有 techstack 把手機 App 的位置寫成「本 repo `app/`」，實際在 `app/mobile/`，兩者不衝突，但讀的人要知道 `app/` 是容器目錄。

## 決策 2：櫃檯 Web 的前端技術棧

- **要決定什麼**: 用什麼建置工具、語言與函式庫組出櫃檯 Web；要不要沿用 `tms-web`。
- **Decision**: `Vite` ＋ `React` ＋ `TypeScript`，做成單頁應用（登入之後由瀏覽器自己換頁，不由伺服器產生畫面）。搭配：
  - `React Router`：登入、改密碼、總覽看板、待派單四個頁面之間的切換。詳情面板、當場回答面板、帳號面板是蓋在頁面上的面板，不是頁面。
  - `TanStack Query`：管「從後端抓回來的資料」，負責重抓、每 5 秒輪詢、送出失敗時把畫面回復原狀（SC-016）。
  - `Zustand`：管只存在畫面上的狀態，例如登入身分、泳道、篩選、房號搜尋、拖曳中的提示。
  - `Tailwind CSS`：樣式；尺寸與色彩由 `/ui-plan` 依設計原型定成共用的設計變數。
  - `i18next` ＋ `react-i18next`：繁體中文、英文、日文、越南文四種介面語言（FR-010、NFR-007）。
  - 版本取建立 `app/web/` 當下的穩定版並鎖定。2026-11-12 查到的現行版本：vite 8.3.8、react 19.3.1、react-router 8.5.1、@tanstack/react-query 5.107.1、zustand 5.1.0、i18next 27.1.1、react-i18next 18.0.1、tailwindcss 4.3.5、typescript 7.0.2。
  - `tms-web` 只借這個「組合」，不借它的程式、設定與版本。
- **Rationale**:
  - 這是登入後才看得到的內部工具，不需要讓搜尋引擎讀到，也不需要伺服器先把畫面算好；單頁應用建出來只是一包靜態檔，部署時不用多一個 Node 伺服器。
  - 畫面要做三件自己寫很容易出錯的事：收到即時訊號就重抓、連線異常時每 5 秒重抓、派單送出失敗時卡片回到原位。`TanStack Query` 內建這三件事的機制。
  - `tms-web` 用的正是這個組合（`~/IdeaProjects/tms-web/package.json`），團隊已經看過它的寫法，但它不能當基底：語系只有 `en`、`ja`、`zh-TW` 三份（`~/IdeaProjects/tms-web/src/i18n/locales/`）；語言記憶是整台裝置一筆（`~/IdeaProjects/tms-web/src/i18n/index.ts:37-39`），不符合 FR-010 的「每人一筆」；沒有任何測試相依；套件版本落後現行版一到三個大版。
  - 這一項沒有另外問使用者，依據是使用者原話「REACT Web」與先前「只借 `tms-web` 的技術棧」的評估。
- **Alternatives considered**:
  - `Next.js`：有伺服器端產生畫面與檔案式路由，但這一包用不到，還要多部署一個 Node 伺服器，不採用。
  - 不用 `TanStack Query`，自己用 `fetch` 加 `useEffect` 管資料：少一個相依，但重抓、輪詢、失敗回復都要自己寫、自己測，不採用。
  - 用一套現成的元件庫（例如 MUI、Ant Design）：起步快，但設計原型的看板、卡片、膠囊、拖放區都是自訂樣式，套元件庫之後還要一一蓋掉它的外觀，不採用。
- **殘餘風險**:
  - 上列版本是查詢當天的現行版，沒有實際裝起來跑過。其中 TypeScript 7 與 Vite 8 是大版本，若建立專案時發現與其他套件不相容，退回上一個大版本，並把實際鎖定的版本寫回 `specs/truth/techstack.md`。
  - 每人一筆的語言記憶 `i18next` 沒有現成做法，要自己寫（決策 9）。

## 決策 3：版面支援哪些視窗尺寸與輸入方式

- **要決定什麼**: 櫃檯 Web 主要在什麼裝置上用、版面怎麼隨視窗變化、最小支援到多大、滑鼠與觸控要支援哪些。
- **Decision**（使用者已拍板）:
  - 主要使用環境是電腦瀏覽器，同時支援 iPad 橫向。
  - 版面隨視窗伸縮，以設計圖的 1180×820 為基準；視窗更寬時，看板的欄跟著變寬。
  - 電腦最小支援視窗是 1280×720；iPad 橫向（1180×820）同樣支援。比這兩個尺寸更小時畫面出現捲軸，不另外設計版面。
  - 滑鼠與觸控都要能完成所有操作，包含拖曳派單。
- **Rationale**:
  - 使用者的說明：客戶一定有電腦，不一定有 iPad。
  - 設計圖只有一份（1180×820），用「同一份版面伸縮」而不是「替電腦另畫一份」，才不會出現兩套版面各自走樣。
  - 兩種輸入都要支援，所以任何功能都不能只靠滑鼠停留或鍵盤才做得到；這也正好是之後包成 iPad App 要守的限制（決策 10）。
- **Alternatives considered**:
  - 只支援 iPad 橫向、固定 1180×820：設計與驗收最單純，但沒有 iPad 的客戶無法使用，使用者未採用。
  - 替電腦寬螢幕另外設計一套版面（例如多欄並排、詳情面板常駐）：寬螢幕的空間用得更好，但設計圖沒有畫，等於新增一批需求，這一包不做。
  - 固定 1180×820 的畫面，在大螢幕上置中或整體等比放大：最省事，但大螢幕兩側留白或字被放大，看板一次看得到的欄數沒有變多，不採用。
- **殘餘風險**:
  - 1280×720 比設計圖矮 100 px。看板的欄、待派單清單、詳情面板在這個高度下要靠各自區塊內的捲動容納，細節由 `/ui-plan` 定。
  - 設計圖登入頁那句「這台 iPad 是櫃檯共用的，請用自己的帳號登入」在電腦上不成立；`spec.md` 已定案改成「這台裝置是櫃檯共用的，請用自己的帳號登入」（FR-001）。

## 決策 4：拖曳派單用什麼做

- **要決定什麼**: 把「待派單」卡片拖到「給房務」「給工務」（FR-087–091）用哪一類技術實作，才能同時支援滑鼠與觸控、能被自動化測試驅動、之後進 App 外殼不用重寫。
- **Decision**: 用以 Pointer Events 實作的拖曳函式庫（Pointer Events 是瀏覽器把滑鼠、手指、觸控筆統一成同一種事件的標準介面），候選是 dnd-kit 系列。確切用哪一個套件，由建立專案時的 spike 決定：
  - `@dnd-kit/core` 6.3.1：穩定，但最後一版是 2024-12-05，已近兩年沒有發版。內建指標、滑鼠、觸控、鍵盤四種感應方式，觸控可設「按住多久才起拖」與「容許手指移動多少」。
  - `@dnd-kit/react` 0.5.3：2026-10-31 還在發版，但還沒到 1.0。
- **Rationale**:
  - 這類函式庫的拖曳過程（被拖的卡片、拖放區的反應）全部由網頁自己畫，可以照設計圖做；滑鼠與手指走同一套程式。
  - 測試工具送出的滑鼠事件能不能被這類函式庫當成 Pointer Events 收到、因而驅動得了，目前沒有查到來源，由建立專案時的 spike 實跑確認（見「建立專案時要先做的 spike」第 3 點；觸控的部分見決策 5）。
  - 兩個候選各有一個弱點（一個久未發版、一個還沒到 1.0），光看文件分不出哪個在「iPad 尺寸＋觸控＋自動化測試」下可行，所以留給 spike 用實跑結果決定。
- **Alternatives considered**:
  - 瀏覽器原生拖放（HTML Drag and Drop API），以及建在它上面的 `@atlaskit/pragmatic-drag-and-drop` 4.0.0：在觸控裝置上要長按才起拖，拖曳中的影像由系統畫，難以照設計圖控制，自動化測試也較難模擬；App 外殼裡的網頁元件行為與 Safari 不完全一致。不採用。
  - `@hello-pangea/dnd` 18.0.1：為「清單內排序」設計，不適合「卡片拖進固定的兩個拖放區」。不採用。
  - 不用函式庫，自己用 Pointer Events 寫：只有一種拖曳，寫得出來，但捲動中起拖、手指滑出視窗、放開位置判定這些邊角要自己處理。列為 spike 兩個候選都失敗時的退路。
- **殘餘風險**: 兩個候選都還沒有在本專案實跑。iPad 上「按住卡片起拖」與「手指上下滑動捲動看板」會搶同一個手勢，按住延遲要設多久才順手，只能在真機上調。

## 決策 5：櫃檯 Web 的 BDD techstack，以及測試模組怎麼切

- **要決定什麼**: 用哪一套工具執行 `specs/truth/features/frontdesk-web/` 的中文 Gherkin、用什麼驅動瀏覽器；它與現有的手機 App 測試線（`app/bdd/`）怎麼共用後端資料準備又不互相干擾；驗收要涵蓋哪些視窗尺寸。
- **Decision**（使用者已拍板工具與方向；模組名稱與套件名稱是本文定的）:
  - 執行 Gherkin：`Cucumber-JVM`，與後端、手機 App 同一套。驅動瀏覽器：`Playwright for Java`（查詢當天最新版 1.64.0）。
  - 測試模組切成三個，各自是獨立的 Maven 模組：
    - `app/bdd-support/`（新增）：只放後端資料準備 `BackendChannel` 與它用到的資料庫驅動、JSON、密碼雜湊相依。放在 `src/main/java`，Java 套件名不落在任何一條測試線的 glue 套件底下。裡面不放任何 Cucumber 的步驟或 `@Before`。
    - `app/bdd/`（既有，手機 App 測試線）：改成引用 `app/bdd-support/`，其餘不變。glue 套件仍是 `com.athena.app.bdd`，讀 `specs/truth/features/ios-app/`。
    - `app/web-bdd/`（新增，櫃檯 Web 測試線）：引用 `app/bdd-support/` 與 Playwright；自己的 glue 套件（例 `com.athena.web.bdd`，不在 `com.athena.app.bdd` 底下）；讀 `specs/truth/features/frontdesk-web/`。握著瀏覽器的 World 與所有 Web 步驟都放這裡。
  - 驗收涵蓋的視窗：電腦最小尺寸 1280×720（滑鼠操作）與 iPad 橫向 1180×820（開啟觸控）。本文建議全部情境在 1280×720 跑一輪，iPad 橫向至少跑版面與拖曳相關的情境；兩組設定各跑哪些情境由 `/tasks` 依整包執行時間排定。
  - 觸控拖曳能不能被自動化測試驅動還沒有實跑過，列為建立專案時的半日 spike（見「建立專案時要先做的 spike」）。不論 spike 結果如何，真機 iPad 上的觸控手感都要人工驗一次。
- **Rationale**:
  - **為什麼沿用 Java**：這一包的情境大量需要「別人的動作」當前提或觸發，例如住客送單、另一台櫃檯先派走、同仁認領／開始／完成、主管查房、把時鐘撥到單已經等了 10 分與 20 分。這些 `BackendChannel` 都已經有（建飯店、建員工、建各狀態的單、建訂房含 VIP、撥後端時鐘、以某位員工的身分呼叫正式 API；`app/bdd/src/test/java/com/athena/app/bdd/BackendChannel.java:245,770,1190`）。它的資料準備是直接寫資料庫的 SQL，因為後端沒有建測試資料用的 API（`app/bdd/pom.xml:97-98`）；另起一條 Node 測試線就要用 TypeScript 再寫一份，之後資料庫每改一次欄位要改兩份。
  - **為什麼不能只在 `app/bdd/` 裡加一個瀏覽器 World**：`app/bdd/` 的三個執行入口都把 glue 設成整個 `com.athena.app.bdd`（`RunTruthFeaturesTest.java:13`），而 `AppiumWorld` 就在這個套件裡，它的 `@Before(order = 0)` 會啟動 Appium 連線（`AppiumWorld.java:94-99`）。瀏覽器 World 放進同一個 glue 套件，每個 Web 情境都會連帶啟動 Appium 與模擬器。
  - **為什麼要多一個 `app/bdd-support/`**：`BackendChannel` 現在放在 `app/bdd/src/test/java`，Maven 的測試程式不會被包進給別人引用的 jar；而且它的 Java 套件就是手機線的 glue 根。搬到獨立模組的 `src/main/java` 之後，兩條測試線都能引用，而且 Cucumber 掃 glue 時不會掃到它。`BackendChannel` 本身不依賴 Appium（只用 `java.net.http`、JDBC、Jackson、BCrypt，`BackendChannel.java:3-28`），可以整個搬走。
  - **能共用的只到 `BackendChannel` 為止**。回原始碼確認後，`app/bdd/` 現有的中文步驟（例「飯店有以下員工：」）是寫在依賴 `AppiumWorld` 的類別裡（`steps/common/CommonGivenSteps.java:19-36`），「姓名對到哪一筆員工」這類情境內的記錄也由 `AppiumWorld` 保管。所以 Web 測試線要有自己的 World 與自己的步驟定義，內容是薄薄一層、轉呼叫 `BackendChannel`；同一句中文步驟在兩條 Java 測試線會各有一個定義。這比 Node 路線「連 SQL 都重寫」的重複少得多，但不是零。
  - **速度**：瀏覽器直接連本機的 Web 與後端，單一情境以秒計；現有手機線整包要 60–90 分鐘（`app/bdd/run-all.sh:3`）。兩條線共用同一個後端與資料庫，情境只能一個接一個跑。
- **Alternatives considered**:
  - 另起一條 Node 測試線（`Playwright Test` ＋ `playwright-bdd`，或 `cucumber-js`）：測試與前端同一種語言，除錯工具最完整，也能直接執行中文 Gherkin（`playwright-bdd` 支援 `# language` 指令）。但資料準備要用 TypeScript 重寫並長期與 Java 那份同步；它最大的好處是多個情境同時跑，在共用一個後端資料庫的前提下用不上。使用者未採用。
  - 沿用 Appium，直接驅動 iPad 模擬器上的 Safari：最接近現場的真觸控，之後包成 App 可以接著測。但最慢、最不穩，模擬器一次只能給一條測試線用；而且主要使用環境已經改成電腦瀏覽器。不當主線，留作之後包成 iPad App 時的少量檢查。
  - 把 `BackendChannel` 留在 `app/bdd/`，用 Maven 的 test-jar 匯出給 Web 測試線：不用搬檔案，但 Web 測試線會把手機線的步驟類別與 Appium 相依一起帶進來，glue 一設錯就會啟動 Appium。不採用。
- **殘餘風險**:
  - 搬動 `BackendChannel` 會改到 `app/bdd/` 裡所有引用它的檔案的 `import`，是機械式修改，但搬完要把手機線整包重跑一次確認沒有壞。
  - 本 repo 沒有把各模組串起來的上層 `pom.xml`（`app/bdd/` 自己就是獨立專案）。`app/bdd-support/` 要先安裝到本機的 Maven 倉庫，或補一個上層 `pom.xml`，兩條測試線才引用得到；做法由 `/tasks` 定。
  - Playwright 的 WebKit 不是 iPad 上的 Safari，Playwright 也沒有內建的觸控拖曳（要手動送觸控事件，而且送出的事件不被瀏覽器當成真人操作）。所以「自動化測試在 iPad 尺寸下通過」不等於「真機上好用」。
  - Playwright for Java 的版本通常比 Node 版晚一點（查詢當天 Java 版 1.64.0、Node 版 1.65.0）。
  - Web 測試線要先把 `app/web/` 建置並啟動、後端啟動並打開可撥的測試時鐘（`tms/src/main/resources/application.properties:45-47`）才能跑；啟動順序與指令由 `/tasks` 定。

## 決策 6：測試策略

- **要決定什麼**: 櫃檯 Web 除了「開真的瀏覽器、連真的後端走完整流程」的驗收測試之外，要不要再加其他層的測試。
- **Decision**（使用者已拍板）:
  - 驗收測試是主線：`frontdesk-web` 的每一個 Gherkin 情境都開真的瀏覽器、連真的後端與資料庫執行。
  - 另外只替「沒有畫面的純計算」加單元測試，用 `Vitest`，放在 `app/web/`。範圍例如：單落在看板哪一欄與未結案張數的公式、等待 10 分與 20 分的門檻與膠囊文字、瀏覽器語言的比對、每人一筆的語言記憶。
  - 不做元件測試層（把單一畫面元件單獨畫出來檢查的測試）。
  - 後端與手機 App 的測試策略不變。
- **Rationale**:
  - 分欄對照表、門檻、語言比對的組合很多，全部開瀏覽器逐一驗會讓驗收變慢；這些計算不碰畫面，單元測試幾秒就跑完。
  - 元件測試與驗收測試檢查的東西重疊多，而且這一包的畫面文字還有待釐清題目（C-01–C-18，多數是畫面文字與版面）交 `/ui-plan` 決定，元件測試會跟著反覆改。
- **Alternatives considered**:
  - 只做驗收測試：只有一層，最單純，但組合很多的計算都要開瀏覽器驗，整包變慢。使用者未採用。
  - 驗收測試＋單元測試＋元件測試：保護最多，但重疊多、維護成本高。使用者未採用。
- **殘餘風險**: 單元測試只證明計算對，不證明畫面用了這個計算；每一種計算至少要有一個驗收情境在畫面上看到結果。這條由 `/dsl-refine` 與 `/tasks` 把關。

## 決策 7：瀏覽器怎麼連到後端

- **要決定什麼**: 櫃檯 Web 與後端 API 的網址關係，開發時與正式環境各怎麼接；後端要不要為此加跨網域設定。
- **Decision**:
  - 開發時由 Vite 的開發伺服器代理：瀏覽器只連開發伺服器，開發伺服器把 `/api`、`/desk`、`/staff`、`/auth` 這些路徑的請求轉給本機的後端。
  - 正式環境同源部署（這是假設，第一次部署前要確認；若不成立，需要後端加跨網域設定）：靜態檔與後端 API 由前面的反向代理（站在最前面、依路徑把請求分給不同服務的那一層）放在同一個網址底下。
  - 前端的 API 位址做成建置時可設定，預設是相對路徑。
  - 這一包不替後端加跨網域設定。
- **Rationale**:
  - 後端現在沒有任何跨網域設定（在 `tms/src/main` 搜尋 `cors`、`CrossOrigin` 沒有結果），登入憑證只從標頭讀（`tms/src/main/java/com/athena/tms/filter/BearerTokenAuthFilter.java:49-65`）。同源的話後端一行都不用改，瀏覽器也不會在每個請求前多送一次詢問用的預檢請求，事件串流走同一條路。
  - API 位址可設定，是為了之後若外殼從自己的來源載入畫面（決策 10）時，只要改設定並在那時加跨網域設定，不用改程式。
  - 這一項沒有另外問使用者，因為反悔成本低：之後要改成不同源，只要後端加設定、前端改一個建置參數。
- **Alternatives considered**:
  - 前端與 API 放不同網域，後端加跨網域設定：部署最有彈性，但要改後端的安全設定；多飯店共用時還要維護「允許哪些網站」的清單。現在沒有這個需要，不採用。
  - 把建置好的靜態檔放進後端一起提供（`tms/src/main/resources/static/`，現在是空的）：同源、最省事，但前端每改一次版都要重新部署後端。不採用；若正式環境最後沒有反向代理，這是現成的退路。
- **殘餘風險**:
  - **「正式環境同源」是假設，沒有依據可查**。repo 內沒有專案自己的 Dockerfile、沒有 CI 流程（`.github/` 底下只有 `modernize/`），查不到正式環境長什麼樣子。若實際部署不是同源，後端就要加跨網域設定，這件事要在第一次部署前向負責部署的人確認。
  - 事件串流是一條很久不關的連線（後端設定 30 分鐘才逾時，`tms/src/main/java/com/athena/tms/service/StreamConnectionRegistry.java:28`）。反向代理若把回應先存起來再一次送出、或自己較早切斷閒置連線，即時更新會失效並一直停在每 5 秒輪詢。部署時要一併確認。
  - 櫃檯的端點在 `/desk` 底下，不在其他端點所在的 `/api/v1` 底下（`tms/src/main/java/com/athena/tms/controller/DeskController.java:16`）。代理要轉哪些路徑，取決於 `/api-plan` 最後把櫃檯端點放在哪裡。

## 決策 8：畫面怎麼跟上現場的變化

- **要決定什麼**: 看板、待派單頁、詳情面板、側欄徽章要不用重新整理就跟上現場（FR-092）；即時連線異常時改成每 5 秒更新並出現提醒列（FR-093、FR-094）；恢復後回到即時（FR-095）。用什麼機制做到。
- **Decision**:
  - 平常：櫃檯 Web 讀後端既有的員工事件串流 `GET /staff/stream`。串流送來的只是「哪一張單有變化」的訊號，不含單的內容；收到訊號就讓 `TanStack Query` 重抓受影響的資料。剛連上時後端會送 `init` 與 `resync`，收到也重抓一次。
  - 讀串流的方式：用 `fetch` 讀取回應串流，這樣可以帶 `Authorization` 標頭。瀏覽器內建的 `EventSource` 不能帶自訂標頭，不用。解析串流格式的程式自己寫，或用還在維護的 `eventsource` 套件（5.1.5），建立專案時二選一。
  - 異常：串流斷線、超過兩個心跳週期（後端每 15 秒送一次心跳，所以約 30–35 秒）沒有收到任何資料、或收到後端的 `degraded` 事件（後端宣告自己的事件處理落後），三者任一發生就顯示「即時連線異常」提醒列，並每 5 秒重抓一次。
  - 恢復：串流重新連上並收到資料後，停止輪詢、提醒列消失。
- **Rationale**:
  - 後端已經有這條串流（`tms/src/main/java/com/athena/tms/controller/StaffStreamController.java:17-21`），後端自己的行為規格在 `tms/src/test/resources/features/14-sse-stream.feature`。以下路徑都在 `tms/src/main/java/com/athena/tms/` 底下。
  - **事件怎麼走到串流**（逐檔確認過）：任務狀態一改變，後端在呼叫端的資料庫交易內把事件寫進待發送表（outbox；`recordEvent` 是 private 方法，寫入在 `TaskEngine.java:386-388`，呼叫它的各公開方法自己帶 `@Transactional`，例如建單 `TaskEngine.java:48`、指派部門 `TaskEngine.java:92`；`OutboxWriter.java:24-38`）→ 轉送程式每 2 秒把待發送表的內容送進 Kafka 的 `tms.task-events`（`OutboxRelay.java:62,106,128`）→ 串流的消費者從 Kafka 收到事件（`service/TaskEventsSseConsumer.java:66-91`；每個後端執行個體用隨機的群組名稱，所以每一個都收得到全部事件）→ 推給符合條件的連線（`service/StreamConnection.java:84-97`）。串流的事件只有這一個來源，沒有「交易一完成就直接推」的路。
  - **串流送什麼**（`service/StreamConnection.java:51-124`）：連上先送 `init`（帶 `stream_pos`，這條連線的事件序號），再送 `resync`；之後每有任務事件送一則 `task`，只帶 `stream_pos`、`task_id`、`seq`、`type`、`occurred_at`，沒有單的內容，所以客戶端一定要自己重抓，正好配「收到訊號就重抓」；每 15 秒送一則 `heartbeat`（`service/StreamHeartbeat.java:15`）；消費者發現事件落後超過 10 秒時送 `degraded`，原因是 `kafka_consumer_lag`（`service/TaskEventsSseConsumer.java:77-89`、`service/StreamConnectionRegistry.java:96-102`），設計本意就是「客戶端轉 5 秒輪詢」。
  - 櫃檯帳號現在就連得上這條串流：`GET /staff/**` 已放行 `frontdesk`（`tms/src/main/java/com/athena/tms/config/SecurityConfig.java:48-49`）。
  - 用 `fetch` 讀串流，後端的認證方式完全不用改。
  - 這一項沒有另外問使用者，因為行為已由 FR-092–095 限定，後端也已有對應的機制。
- **Alternatives considered**:
  - 平常就只做輪詢，不接串流：最簡單，但 FR-095 要求恢復後「不再是每 5 秒更新一次」，平常與異常就分不出來；而且 `GET /desk/tasks` 現在一次回傳整個飯店的全部任務、沒有日期範圍也沒有分頁（`service/DeskService.java:36-39`），頻繁輪詢負擔大。不採用。
  - WebSocket（瀏覽器與後端可以雙向傳訊息的連線）：後端要新做一套；這一包沒有需要由瀏覽器即時推給後端的訊息。不採用。
  - 為了能用內建的 `EventSource`，把登入憑證改放 cookie 或放在網址上：前者要替後端多開一條認證的路並重新處理跨站偽造請求的防護（現在是關的，`SecurityConfig.java:41`）；後者會讓憑證進存取記錄與瀏覽器歷史。都不採用。
- **殘餘風險**:
  - **後端現在送給櫃檯的事件範圍不夠，光靠前端做不到 FR-092**。員工連線的過濾條件是：同一家飯店，而且「任務的部門等於連線者的部門」或「任務指派給連線者本人」；不看角色（`service/StreamConnection.java:57-61`，部門取任務當下的部門，`service/TaskEventsSseConsumer.java:155`）。結果：
    - `frontdesk` 帳號只收得到部門是 `frontdesk` 的單，也就是待派單（`service/ClassificationRuleEngine.java:28`）；單一派給房務或工務，後續的認領、開始、完成就收不到。
    - `manager` 這類沒有部門的帳號幾乎收不到任何事件（測試資料裡主管類帳號的部門是空的，`app/bdd/src/test/java/com/athena/app/bdd/BackendChannel.java:407-409`）。
    - `duty_manager` 連不上串流（見「後端為這一包要補的事」）。
    - 這時心跳照常送達，前端判定連線正常、不會轉輪詢，畫面卻不會更新。全館看板需要後端把櫃檯、主管、值班主管連線的事件範圍擴大到全館，補齊之前這一包的即時更新驗收過不了。
  - **「幾秒內跟上」的實際下限約 2 秒**。轉送程式每 2 秒才送一次，所以事件從發生到出現在串流上本來就可能晚 2 秒，再加上 Kafka 傳遞與前端重抓的時間。`spec.md` 的 C-17（即時連線正常時的秒數上限）還沒有定，定的時候不能低於這個數字；要更快就得改後端的轉送間隔。本文不替它定上限。
  - **Kafka 停掉時前端看不出來**。串流的事件只來自 Kafka，Kafka 停掉後串流只剩 `init`、`resync`、`heartbeat`；`degraded` 是消費者「收到一則落後的事件」時才送的，完全收不到事件就不會送。這時心跳正常、沒有降級宣告，前端不會轉輪詢。要不要由後端在這種情形主動宣告降級，列入後端要補的事。
  - **後端沒有「已恢復」的事件**。`degraded` 只在開始落後時送一次，恢復正常時後端只是內部記下、不通知客戶端（`service/TaskEventsSseConsumer.java:82-88`）。收到 `degraded` 之後前端憑什麼判定恢復（FR-095），要由 `/api-plan` 定：後端補一則恢復事件，或前端自訂規則。
  - 剛連上時 `resync` 附的重抓提示對員工連線寫死成待認領清單 `/api/v1/pool`（`service/StreamConnection.java:80`），對櫃檯不適用；櫃檯 Web 不看這個提示，一律重抓自己畫面上的資料。
  - **櫃檯 Web 會是這條串流的第一個客戶端**。手機 App 不是、也從來不是它的客戶端：`app/mobile/lib` 內沒有任何讀串流的程式；每 5 秒輪詢只有測試後門會打開（`app/mobile/lib/core/test_backdoor.dart:133-135`、`app/mobile/lib/features/today/my_today_scope.dart:56`、`app/mobile/lib/core/connection_status.dart:43`），正式環境的 App 沒有自動更新，只在進畫面時、自己做完動作後、離線暫存的動作送出後重抓；推播只負責點開之後帶到對應的畫面。所以這條鏈在後端有測試，但沒有被任何真實的前端驗證過，長時間連線、斷線重連、經過代理之後的行為都是未知。建立專案時先做一次端到端的連線試驗（見「建立專案時要先做的 spike」）。
  - 等待時間的分鐘數要拿「現在」去減建立時間。驗收測試會撥後端的時鐘，瀏覽器的時鐘也要跟著撥才對得上；Playwright 有撥瀏覽器時鐘的功能，但在本專案還沒有實跑，列入 spike。「現在」以瀏覽器時鐘還是後端回覆的時間為準，交 `/system-analysis`。

## 決策 9：登入憑證與介面語言存在瀏覽器的哪裡

- **要決定什麼**: 登入之後不小心重新整理、關掉分頁、關掉瀏覽器，各要不要重新登入；登入憑證因此放在哪裡；每個人選的介面語言放在哪裡。
- **Decision**:
  - 登入憑證（使用者已拍板行為）：重新整理網頁不用重新登入；關掉分頁或瀏覽器就要重新登入。做法是放在 `sessionStorage`（瀏覽器替每個分頁各留一份、分頁關掉就清掉的儲存空間）。每個請求用 `Authorization: Bearer` 標頭帶給後端。按「登出」時清掉。
  - 後端發的登入憑證 12 小時到期（`tms/src/main/resources/application.properties:38`）；到期後後端拒絕請求，櫃檯 Web 回到登入頁。
  - 介面語言：放在 `localStorage`（同一個瀏覽器長期保存、關掉也還在的儲存空間），以員工編號為鍵、每人一筆；另存一筆「登入頁目前的語言」。與登入憑證分開存，登出不會清掉語言。
- **Rationale**:
  - 櫃檯的裝置是多人共用的。放 `sessionStorage`，人離開把分頁關掉，下一位一定要自己登入，派單不會記到上一位名下（FR-100、NFR-001）；手滑按到重新整理又不會被踢出去。
  - 介面語言的規則是「記在這台裝置上、每人一筆，不記在員工帳號上；清掉瀏覽器資料等同一台新裝置」（FR-010 與「假設」），`localStorage` 的保存範圍正好就是這樣。
  - 兩者分開存，是因為保存期限不同：憑證跟著分頁走，語言跟著裝置走。
- **Alternatives considered**:
  - 憑證放 `localStorage`：12 小時內都不用重新登入，最省事；但上一位忘了登出，下一位打開瀏覽器就是上一位的帳號。使用者未採用。
  - 憑證只放在記憶體：重新整理也要重新登入，最保險；但畫面卡住時重新整理一下就得再輸入一次。使用者未採用。
  - 憑證放瀏覽器腳本讀不到的 cookie：網頁上的惡意程式碼讀不到憑證，但後端要多一條認證的路並處理跨站偽造請求；之後進 App 外殼時 cookie 的限制也最多（決策 10）。不採用。
- **殘餘風險**:
  - **「關掉分頁就要重新登入」有瀏覽器層級的例外**。瀏覽器把分頁「還原」時（重新開啟剛關閉的分頁、設定成啟動時還原上次的分頁、複製分頁），`sessionStorage` 可能跟著回來。這在目標瀏覽器上的實際行為要實測；若不能接受，補救方式是登入狀態另加閒置逾時，屬於新需求，要回 `spec.md`。
  - 放在 `sessionStorage` 的憑證，網頁上執行的任何腳本都讀得到。櫃檯 Web 不載入第三方腳本，住客與同仁輸入的內容一律當純文字顯示。
  - 多家飯店共用同一個網址時，不同飯店可能有相同的員工編號，語言的鍵要再加上飯店的識別；交 `/system-analysis`。
  - 後端有兩個登入端點：`POST /auth/login` 與 `POST /api/v1/staff-accounts/login`（`controller/AuthController.java:20`、`controller/StaffAccountController.java:19,41`），手機 App 與測試用的是後者。櫃檯 Web 用哪一個由 `/api-plan` 定。

## 決策 10：之後包成 iPad App 的路線現在要不要定

- **要決定什麼**: 這一包要不要先選定之後包成 iPad App 的方式；不選的話，現在要避開哪些做法才不會堵死那條路。
- **Decision**（使用者已拍板不在這一包決定）: 現在只交付 Web。下列做法現在就避免：
  - 不用 cookie 當登入憑證；用標頭帶憑證、存在瀏覽器儲存空間。
  - API 位址不寫死，做成建置時可設定（決策 7）。
  - 不用瀏覽器原生拖放；用 Pointer Events 類的拖曳函式庫（決策 4）。
  - 任何功能都不能只靠滑鼠停留或鍵盤快速鍵才做得到。
  - 版面高度用會隨瀏覽器工具列收放而變的動態視窗高度單位（`dvh`）與安全區域變數（避開圓角與系統手勢區的邊距），不假設瀏覽器工具列的高度。
  - 不做「一定要有 Service Worker（瀏覽器在背景替網頁攔截請求、做離線快取的機制）才能運作」的功能。
- **Rationale**:
  - 三條可能的路吃的都是同一份 Web 建置結果，現在選哪一條都不影響這一包的交付，所以可以延後。
  - 上列每一項都是「某一條路上會壞」的做法：外殼裡的 cookie 限制最多；畫面打包進 App 時來源會變，寫死的相對路徑會失效；外殼裡的網頁元件對原生拖放與 Service Worker 的支援不一致。現在避開，三條路都還走得通。
- **Alternatives considered**（之後再選，現在不定）:
  - 加到主畫面的網頁（PWA）：不上架，改版立即生效；全螢幕與鎖定橫向做得到。
  - 原生外殼載入線上網址（WKWebView）：可上架或用企業簽發；畫面仍從伺服器載入，同源不變。
  - `Capacitor`：把畫面打包進 App，可接原生功能；畫面從外殼自己的來源載入，那時後端要加跨網域設定。
- **殘餘風險**: 主要使用環境改成電腦瀏覽器之後，iPad App 還做不做、什麼時候做，沒有人定；上面的避免事項成本很低，先照做。

## 後端為這一包要補的事（只列清單，交後續步驟）

這些是櫃檯 Web 要能運作、後端必須先有的東西。本文不展開設計，只標明依據與由哪個步驟處理。

| 要補的事 | 依據 | 由哪個步驟處理 |
| --- | --- | --- |
| 把櫃檯端點補進契約，並解決契約與程式的路徑不一致：契約寫 `/tasks/{id}/triage`，程式是 `/desk/triage/{taskId}`，而且 `/desk/*` 不在 `/api/v1` 底下 | `specs/truth/contracts/openapi.yaml:150`、`controller/DeskController.java:16,29` | `/api-plan` |
| 派單後復原（含兩種失敗原因）、撞單時回覆是誰派給哪個部門、派單是否用拖曳完成 | `spec.md`「設計圖與後端現況的出入」第 1 項；後端現在只收派部門與回答兩種動作（`service/DeskService.java:46-59`） | `/api-plan`；要不要另留稽核紀錄交 `/data-plan` |
| 看板查詢：全館還沒結束的單加上今天結束的單；待派單最新的在上 | 現況回傳全館全部任務、待派單最早的在上（`service/DeskService.java:33,36-39`）；`spec.md` 出入第 2 項 | `/api-plan` |
| 事件串流的範圍：`frontdesk`、`manager`、`duty_manager` 的連線要收得到全館的任務事件；剛連上時的重抓提示不適用櫃檯；Kafka 停掉時要不要主動宣告降級；降級之後要不要有恢復事件 | `service/StreamConnection.java:57-61,80`、`service/TaskEventsSseConsumer.java:77-89` | `/api-plan` |
| `duty_manager` 的授權：後端的權限規則沒有任何一條列它，所以它現在登入後什麼都做不了，包含連事件串流、看單、派單、設定新密碼；角色清單也沒有列它 | `config/SecurityConfig.java:44-72`、`common/codes/StaffRole.java:11`；資料庫允許這個值（`db/migration/V7__duty_manager_role.sql:6`） | `/api-plan`（契約上的角色）；`/implement`（權限規則） |
| 櫃檯畫面只放行 `frontdesk`、`manager`、`duty_manager` 登入 | `spec.md`「對既有行為的影響」、FR-006 | `/api-plan`、`/dsl-refine` |
| 新密碼規則：至少 8 碼、含英文字母、含數字 | `spec.md` 出入第 5 項；後端現況：設定新密碼只擋空白（`service/StaffAccountLoginService.java:65-67`）與和目前密碼相同（`service/StaffAccountLoginService.java:77-79`），沒有長度或字元的檢查 | `/api-plan` |
| 櫃檯 Web 用兩個登入端點中的哪一個 | `controller/AuthController.java:20`、`controller/StaffAccountController.java:19,41` | `/api-plan` |
| 重複送出的防護：後端只在請求帶了 `Idempotency-Key` 標頭時才擋重複，沒帶就直接放行。櫃檯 Web 每次寫入都要帶；後端要不要改成強制，一併決定 | `filter/IdempotencyFilter.java:35-44` | `/api-plan` |
| PMS 斷線與恢復的提醒不在本包 | `spec.md`「本版不做」與「設計圖與後端現況的出入」第 9 項 | 本包不處理 |
| 把櫃檯派單與當場回答的後端規則收進 `features/backend/`；新增 `features/frontdesk-web/` 與它的 `dsl.md`；「值班經理」這個舊稱要不要改成「值班主管」 | `spec.md`「範圍」與出入第 6 項 | `/dsl-refine` |
| 待釐清題 C-01–C-18（畫面文字與版面為主；C-17 是驗收門檻） | `spec.md`「待釐清一覽」 | `/ui-plan` |
| 契約裡有、但程式沒有實作的端點：例如契約的 `/alerts`（主管手動上報），`tms/src/main/java/com/athena/tms/` 底下沒有任何對應的端點（搜尋 `alerts` 沒有結果）；`/cleaning-plans` 等其他路徑也沒有對應的控制器。本文只確認了這一個實例，完整盤點待 `/api-plan` | `specs/truth/contracts/openapi.yaml:64-65` | `/api-plan` |
| 已知限制，本包不補：任務資料沒有系統排定的開始時間，所以櫃檯看板不顯示「已接逾時未開始」（已接卡片的「逾時 · {N} 分」膠囊與狀態訊息卡的「已逾時 {N} 分未開始」）；若之後要做，要先替任務資料加這個欄位 | `spec.md`「本版不做」（已接的單的逾時）與「設計圖與後端現況的出入」第 7 項 | 本包不處理；之後要做時交 `/data-plan` |
| 跨網域設定 | 決策 7 的同源假設成立時不用做；同源是假設，不成立時後端要加跨網域設定 | 不處理（假設不成立時交 `/api-plan`） |

## 建立專案時要先做的 spike

半日，在建立 `app/web/` 與 `app/web-bdd/` 的第一天做。目的是把「文件上說可以」換成「在這個版本組合下實際跑得通」。

1. 做一個最小畫面：一張可拖的卡片、兩個拖放區，分別用 `@dnd-kit/core` 與 `@dnd-kit/react` 各做一次。
2. 用 Playwright for Java 驅動，兩組設定都跑：1280×720 以滑鼠操作；1180×820、WebKit 引擎、開啟觸控。
3. 四個動作都要能被測試驅動並驗到結果：起拖、滑進拖放區（拖放區的說明文字有變）、在拖放區放開（派出）、在拖放區以外放開（留在原位）。同時確認：測試工具送出的滑鼠事件，拖曳套件確實當成 Pointer Events 收到。
4. 順便確認：Playwright 撥瀏覽器時鐘之後，畫面上的等待分鐘數跟著變。
5. 產出：選定拖曳套件；記下觸控拖曳在自動化測試裡是怎麼驅動的。
6. 失敗時的退路：iPad 尺寸的拖曳情境改用滑鼠事件驅動，觸控拖曳列為人工驗收；兩個套件都不可行時，自己用 Pointer Events 寫拖曳。

另外做一次事件串流的端到端連線試驗（不必等後端擴大事件範圍，用 `frontdesk` 帳號與一張待派單的單就能做）：瀏覽器經開發伺服器的代理用 `fetch` 連上 `GET /staff/stream`，確認依序收到 `init`、`resync`、每 15 秒的 `heartbeat`；由住客送一張會進待派單的需求，確認收到 `task` 並量從送出到收到的秒數；把後端停掉再啟動，確認前端偵測得到斷線並能重新連上。

不論 spike 結果如何，真機 iPad 上要人工驗一次：按住起拖的延遲順不順手、拖曳與捲動看板會不會互搶、可點的東西是否至少 44 pt 見方（NFR-006）。

## 殘餘風險與後續驗證

- **即時更新卡在後端的事件範圍**：後端沒有補之前，主管類帳號的畫面不會自動更新，櫃檯帳號在單派出去之後也收不到後續（決策 8）。
- **事件串流沒有被任何真實前端用過**，櫃檯 Web 是第一個客戶端；事件到畫面至少晚約 2 秒；Kafka 停掉時前端看不出來；後端沒有恢復事件（決策 8）。
- **`duty_manager` 現在在後端等於沒有任何權限**，缺口比 `spec.md` 寫的「不能派單與當場回答」大（見上表）。
- **正式環境同源是假設**，部署方式在 repo 內查不到（決策 7）。
- **觸控拖曳的自動化、拖曳套件的選擇、瀏覽器時鐘**都還沒有實跑，靠 spike 確認；真機觸控仍要人工驗。
- **`sessionStorage` 在瀏覽器還原分頁時可能保留登入狀態**，要在目標瀏覽器實測（決策 9）。
- **前端套件版本是查詢當天的現行版**，沒有實際安裝驗證過彼此相容（決策 2）。
- **搬動 `BackendChannel` 之後手機測試線要整包重跑一次**（決策 5）。
- **PMS 斷線與恢復的提醒不在本包**。
- `.agents/constitution/` 不存在，照 001–003 的做法略過。

## 外部來源

- `playwright-bdd` 支援非英文 Gherkin（`# language` 指令與 `language` 設定）：https://raw.githubusercontent.com/vitalets/playwright-bdd/main/docs/writing-features/i18n.md
- Playwright for Java 的觸控事件（沒有內建觸控拖曳，要手動送事件，送出的事件不被當成真人操作）：https://playwright.dev/java/docs/touch-events
- Playwright for Java 撥瀏覽器時鐘（本專案未實跑）：https://playwright.dev/java/docs/clock
- Playwright for Java 最新版 1.64.0（2026-10-21）：https://repo1.maven.org/maven2/com/microsoft/playwright/playwright/maven-metadata.xml
- dnd-kit 的感應方式（指標、滑鼠、觸控、鍵盤；觸控可設延遲與容許位移；此頁是 `@dnd-kit/core` 的文件）：https://dndkit.com/api-documentation/sensors
- `sessionStorage` 的保存範圍（分頁關閉即清除；重新整理與分頁還原時保留）：https://developer.mozilla.org/en-US/docs/Web/API/Window/sessionStorage
- npm 上的版本與發版日期（2026-11-12 以 `npm view` 查得）：`@playwright/test` 1.65.0；`playwright-bdd` 9.2.1；`@cucumber/cucumber` 13.3.0；`@dnd-kit/core` 6.3.1（2024-12-05）；`@dnd-kit/react` 0.5.3（2026-10-31）；`@atlaskit/pragmatic-drag-and-drop` 4.0.0；`@hello-pangea/dnd` 18.0.1（2025-02-09）；`@microsoft/fetch-event-source` 2.0.1（2021-04-25，已久未更新，不採用）；`eventsource` 5.1.5；`vitest` 5.1.0；`@capacitor/core` 8.6.0；決策 2 所列各前端套件。
- 沒有找到同時涵蓋「iPad 尺寸＋觸控＋dnd-kit＋Playwright 拖曳」的單一來源，所以列為 spike。
