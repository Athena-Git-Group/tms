# Phase 0 技術堆疊總結：飯店任務交辦系統（TMS）

## 系統有哪些端

系統有四個端：

- 後端 API：`tms/`
- 員工 App（房務、工務員工的手機 App）：`app/mobile/`
- 櫃檯 Web（櫃檯人員、主管、值班主管的網頁）：`app/web/`
- 住客掃碼頁（住客掃房間 QR Code 後用的手機網頁，不登入、不安裝）：與櫃檯 Web 同一個程式 `app/web/`，自己的路由 `/g/{代碼}` 與手機版面，程式集中在 `app/web/src/guest/`

## 技術堆疊總覽

### 前端

| 類別 | 採用技術 | 用途 |
| --- | --- | --- |
| 員工端 App 框架 | `Flutter`（stable 3.47），單一程式碼庫支援 iOS ＋ Android 雙平台 | 房務／工務員工的接單、執行、工務單、查一下；App 程式碼在本 repo `app/`，BDD harness 在 `app/bdd/`；iOS 先行，Android 待本機裝 SDK 後跟進；**有條件採用**——gate 在一日 Appium spike，失敗則改採 React Native |
| App 推播 | `firebase_messaging` | 員工推播事件的收件與導頁；已知 iOS `getInitialMessage()` 歷史缺陷，F3 推播為第一支落地的 Appium 金絲雀情境 |
| App 相機與照片 | `image_picker`（打開系統相機拍攝、打開系統相簿選取；以 `maxWidth`／`maxHeight` 1600、`imageQuality` 80 縮放並壓成 JPEG） | 工務單照片 1–3 張的拍攝、選取與壓縮；「房號 · 拍攝時間」浮水印只疊在畫面上，不畫進照片；`--dart-define=TEST_BACKDOOR=true` 的測試版不打開相機，回固定的測試照片；照片以 `ApiClient` 讀回、依照片 id 放記憶體快取顯示 |
| App 掃碼 | `mobile_scanner` | 住客 QR 掃碼進需求；已知從 Sheet 進入後黑屏風險，需鎖版本並做 dispose／re-init |
| App 視覺 | Flutter 自訂 Cupertino 風主題，以 `ui/prototype/Main.dc.html` 視覺規格為準 | iOS 不追 Liquid Glass／Live Activity，本增量視覺降級已簽核 |
| App 端驗收規格 | Gherkin feature files（`# language: zh-TW`） | App 端行為測項，例如照片、離線重送、推播去處；runner 見「測試與驗證」 |
| 櫃檯 Web 框架與建置 | `React` ＋ `TypeScript`，以 `Vite` 建置成單頁應用（登入後由瀏覽器自己換頁，建置結果是一包靜態檔） | 櫃檯人員、主管、值班主管用的總覽看板、待派單、派單、當場回答與代開單畫面；程式在本 repo `app/web/`；直接呼叫 `tms` 後端，不另起後端；套件版本在建立 `app/web/` 時鎖定當下的穩定版 |
| 櫃檯 Web 換頁 | `React Router` | 登入、改密碼、總覽看板、待派單四個頁面之間的切換；詳情、當場回答、代開單、帳號是蓋在頁面上的面板 |
| 櫃檯 Web 照片顯示 | 瀏覽器內建 `fetch` ＋ `URL.createObjectURL` | 工務單詳情面板的照片：帶 `Authorization` 標頭讀照片位元組，轉成 `blob:` 網址給 `<img>`，卸載或換單時釋放；登入憑證不進網址；不加圖片套件 |
| 櫃檯 Web 後端資料 | `TanStack Query` | 從後端抓資料、收到即時訊號後換上那張卡片或重抓、連線異常或讀取失敗時每 4 秒輪詢（畫面上寫每 5 秒）、送出失敗時把畫面回復原狀 |
| 櫃檯 Web 畫面狀態 | `Zustand` | 只存在畫面上的狀態：登入身分、泳道、篩選、房號搜尋、拖曳中的提示 |
| 櫃檯 Web 樣式與版面 | `Tailwind CSS`；一份隨視窗伸縮的版面，以設計圖 1180×820 為基準 | 主要使用環境是電腦瀏覽器，同時支援 iPad 橫向；視窗更寬時看板的欄跟著變寬；電腦最小支援視窗 1280×720，iPad 橫向 1180×820 同樣支援，更小就出現捲軸、不另外設計；滑鼠與觸控都能完成所有操作 |
| 櫃檯 Web 多語系 | `i18next` ＋ `react-i18next` | 繁體中文、英文、日文、越南文四種介面語言；每個人選的語言存在 `localStorage`（瀏覽器長期保存的儲存空間），以員工編號為鍵、每人一筆，記在這台裝置上，與登入憑證分開 |
| 櫃檯 Web 拖曳派單 | 以 Pointer Events（瀏覽器把滑鼠與手指統一成同一種事件的介面）實作的拖曳函式庫，dnd-kit 系列 | 把待派單卡片拖到房務或工務，滑鼠與觸控走同一套程式；確切套件（`@dnd-kit/core` 或 `@dnd-kit/react`）由建立專案時的半日 spike（正式動工前花半天實際跑一次，確認某個做法行不行得通）選定 |
| 櫃檯 Web 登入憑證 | 後端發的 JWT 存在 `sessionStorage`（每個分頁各一份、分頁關掉就清掉的儲存空間），請求以 `Authorization: Bearer` 標頭帶出 | 重新整理網頁不用重新登入；關掉分頁或瀏覽器就要重新登入；登出時清掉 |
| 櫃檯 Web 連後端 | 開發時由 Vite 開發伺服器代理到本機後端；正式環境與後端同源部署（同一個網址底下）；API 位址建置時可設定，預設相對路徑 | 同源部署時後端不需要跨網域設定（CORS，後端宣告允許哪些網站來呼叫它）；同源部署是假設，第一次部署前要確認；若不成立，需要後端加跨網域設定 |
| 櫃檯 Web 即時更新 | 以 `fetch` 讀取後端的員工事件串流 `GET /staff/stream`（可帶 `Authorization` 標頭）；異常時每 4 秒輪詢 | 櫃檯 Web 是員工事件串流的客戶端：櫃檯角色收到的訊號帶著那張單在看板上的整張卡片，畫面直接換上那一張；剛連上、恢復、漏了訊號、營業日換了時才重抓整份看板；串流斷線、約兩個心跳週期沒有資料、或後端宣告降級時，顯示提醒列（上面寫每 5 秒自動更新）並改成每 4 秒重抓，恢復後停止輪詢 |
| 櫃檯 Web 驗收規格 | Gherkin feature files（`# language: zh-TW`），在 `specs/truth/features/frontdesk-web/` | 櫃檯 Web 的畫面行為測項；runner 見「測試與驗證」 |
| 住客掃碼頁框架與建置 | 與櫃檯 Web 同一個 `React` ＋ `TypeScript` ＋ `Vite` 專案（`app/web/`），同一次建置、同一個部署來源 | 住客掃碼後的首頁、備品補充、設備報修、送出成功、我的需求、需求進度、資訊頁；直接呼叫 `tms` 後端的住客介面 `/guest/*`，不另起後端 |
| 住客掃碼頁換頁與載入 | `React Router` 的路由 `lazy` 延遲載入：整棵 `/g/:qrToken/*` 等住客走到才下載 | QR Code 的網址是 `/g/{代碼}`，每一個子頁都帶著代碼（重新整理、從歷史記錄再開都不用再掃）；櫃檯的程式（含拖曳套件）不進住客的手機 |
| 住客掃碼頁外框與版面 | 自己的外框（不走櫃檯的 `DeskShell`）：手機直向為基準、單欄、內容最寬 480 點置中、可點的東西至少 44 點見方、送出鈕釘在頁底；高度用 `dvh` 與安全區域變數；`Tailwind CSS` 另立一組住客的設計變數命名空間 | 櫃檯最小寬 1180、整頁不捲；住客最寬 480、整頁可捲，兩套前提相反，各自一個外框 |
| 住客掃碼頁後端資料 | `TanStack Query`（與櫃檯共用同一個 query client） | 首頁資料與需求清單的讀取、收到即時訊號後重讀、連線異常時每 4 秒重讀 |
| 住客 session | 用網址裡的代碼向 `POST /guest/scan` 換來的 session 存 `sessionStorage`，鍵帶代碼；請求以 `X-Guest-Session` 標頭帶出；任何一次呼叫被回 401 就用網址裡的代碼重換一次（只重換一次），換到就重發、換不到就顯示無法使用頁 | 重新整理不用再掃；關掉分頁就清掉，下次從網址的代碼重換；與櫃檯的登入狀態用不同的鍵、不同的 store、不同的標頭，同一支手機上兩邊互不影響 |
| 住客掃碼頁連後端 | 自己的薄薄一層 API（前綴 `/guest`、住客標頭、401 重換），與櫃檯共用 `ApiError`、`NetworkError`、`newIdempotencyKey`；開發時 Vite 代理 `/guest`（串流用與 `/staff/stream` 相同的斷線切斷設定）；正式環境同源部署 | 每個寫入都帶 `Idempotency-Key`；備品多項依清單順序一張一張送，被限流就停、已建立的保留 |
| 住客掃碼頁即時更新 | 以 `fetch` 讀取後端的住客事件串流 `GET /guest/stream`（帶 `X-Guest-Session`），解析器與連線狀態機與櫃檯共用；收到 `task` 重讀需求清單、收到 `resync` 另重讀首頁資料；斷線、35 秒沒資料、收到 `degraded` 事件時改成每 4 秒重讀，串流重新連上並收到 `init` 才停止 | 住客的訊號不帶內容，一律重讀；畫面上不顯示連線異常的提示列；後端給住客的串流和員工串流一樣會送 `degraded`、`recovered`，心跳也帶 `degraded` 旗標；畫面目前還沒用 `recovered`，`degraded` 之後會一直重讀到連線被切斷重連為止，最長 30 分鐘（後端的連線逾時） |
| 住客掃碼頁多語系 | 同一個 `i18next` 實例，四份語系檔各加 `guest` 子樹；第一次打開依手機瀏覽器語言（`zh*` 一律繁體中文、`ja`、`vi` 各自對應、其餘英文）；選過的語言存 `localStorage`、一支手機一筆，鍵與櫃檯的語言鍵不同 | 繁體中文、英文、日文、越南文；不加韓文；後端給的字（服務項目、需求標題、櫃檯回覆、資訊頁）原樣顯示 |
| 住客掃碼頁驗收規格 | Gherkin feature files（`# language: zh-TW`），在 `specs/truth/features/guest-web/`，自己的 `dsl.md` | 住客掃碼頁的畫面行為測項；runner 見「測試與驗證」 |

### 後端

| 類別 | 採用技術 | 用途 |
| --- | --- | --- |
| 語言與執行環境 | `Java 25` | 後端主要語言 |
| 應用框架 | `Spring Boot 4.1.1`（Maven，`./mvnw`） | REST API、狀態機、worker 模組在同一個程序 |
| 認證 | JWT（HS256，TTL 12h） | 員工與主管登入，claims 帶 tenant、staff、role、dept |
| 即時推送 | SSE（`SseEmitter`） | 住客與後台即時更新 |
| 訊息佇列 | `Apache Kafka 3.9`（outbox → relay → Kafka） | 事件分發給 SSE、投影、推播消費者、PMS 回寫消費者 |
| 排程 | `Quartz`（租戶感知）＋ Spring 排程 | 班前排房、班末掃描、到期提醒、清理；relay 輪詢用 Spring 排程 |
| 照片儲存 | Google Cloud Storage，以官方 Java 程式庫 `com.google.cloud:google-cloud-storage`（版本與 `firebase-admin` 間接帶入的相同）存取；本機與測試接 `fake-gcs-server` 模擬服務 | 工務單照片內容，物件名稱 `repair-photos/{tenantId}/{photoId}.jpg`；只有一套存取程式，本機與正式環境只差設定 `tms.storage.bucket`、`tms.storage.emulator-host`（正式環境以 `prod` 設定組的 `application-prod.properties` 設成空值）與登入憑證（正式環境用 Application Default Credentials）；連線、讀取與重試總時限各約 10 秒；模擬服務模式啟動時建立儲存空間，失敗就啟動失敗；照片經後端轉送給 App 與櫃檯 Web，不發直接下載的網址 |
| 推播 | 推播消費者（Kafka `tms.push` → Firebase Cloud Messaging，`firebase-admin` SDK） | 員工推播事件送到登記的手機（iOS 經 FCM 轉 APNS）；以 outbox 冪等鍵去重並寫 `push_deliveries` 送達紀錄；暫時性錯誤指數退避重試 3 次後進 `tms.push.DLT`；未設定 `tms.push.fcm.credentials-path` 時只寫日誌與送達紀錄 |
| 時間來源 | 注入的 `java.time.Clock` | 計時、排序、提醒都以同一個可替換時鐘為準 |

### 資料與媒體處理

| 類別 | 採用技術 | 用途 |
| --- | --- | --- |
| 資料庫 | `PostgreSQL 16` | 權威狀態，包括 tasks、task_events、outbox、processed_ops 等 |
| Schema 遷移 | `Flyway` | 資料庫版本管理 |
| 冪等 | `Idempotency-Key` ＋ `processed_ops`；一次開多張單的寫入另在業務交易內以 `pg_advisory_xact_lock` 鎖住這把鍵，並在同一個交易裡寫 `processed_ops` | 重送回放，含離線重送；一次開多張單時，提交與「這把鍵已處理」同進退，同一把鍵同時送兩次也只開一批 |
| 本機環境 | `docker-compose`（Postgres 埠 5433、Kafka、`fake-gcs-server` 埠 4443） | 開發用依賴服務；`fake-gcs-server` 是照片儲存的模擬服務，後端啟動時在模擬服務裡建立儲存空間 |

### 測試與驗證

| 類別 | 採用技術 | 用途 |
| --- | --- | --- |
| 後端 BDD techstack | `Cucumber-JVM` ＋ `Spring Boot Test` | 後端 E2E：跑後端 Gherkin，以真實 HTTP 打 API，並斷言資料庫權威狀態與 outbox |
| 後端 E2E 依賴 | `Testcontainers`（PostgreSQL、Kafka、`fake-gcs-server`） | 測試時啟動真的資料庫、訊息佇列與照片儲存模擬服務；照片真的寫進模擬服務再讀出比對 |
| 員工 App BDD techstack | `Appium` ＋ `Cucumber-JVM`（iOS：vanilla `XCUITest` driver；Android：vanilla `UiAutomator2` driver） | 一套 Gherkin ＋ 一套 Java step definitions 驅動兩平台；driver 讀取 Flutter `Semantics(identifier:)` 曝露到原生 accessibility API 的節點；與後端共用 Cucumber-JVM 生態 |
| App 元素定位 | `Semantics(identifier:)` 掛 dot＋kebab accessibility id，Java step 層 `byTestId()` helper 分流 | iOS 以 `AppiumBy.accessibilityId(id)` 定位（對應 `accessibilityIdentifier`）；Android 以 resource-id 比對定位（`identifier` 落成 `resource-id`，不是 `content-desc`），兩平台 locator 不同，不可共用同一個呼叫 |
| App 測試後門 | `--dart-define` 隔離的 debug build ＋ localhost debug HTTP endpoint | 可注入裝置時間、強制離線、強制第 N 張照片上傳失敗；Wave 1 App 骨架即內建，不得後補；Java step definitions 直接打，不做成隱藏 UI |
| 櫃檯 Web BDD techstack | `Cucumber-JVM` ＋ `Playwright for Java`，獨立 Maven 模組 `app/web-bdd/` | 櫃檯 Web E2E：跑 `specs/truth/features/frontdesk-web/` 的 Gherkin，開真的瀏覽器操作畫面、連真的後端與資料庫；Java step definitions 放在自己的 glue 套件（Cucumber 用來找每句 Gherkin 對應哪段 Java 程式的 Java 套件；不在手機 App 測試線的 `com.athena.app.bdd` 底下），不會啟動 Appium |
| 櫃檯 Web 驗收的視窗與輸入 | Playwright 的兩組瀏覽器設定：1280×720 以滑鼠操作；1180×820、WebKit 引擎（Safari 所用的瀏覽器引擎，用來近似 iPad 上的 Safari，但不等於真機）、開啟觸控 | 驗收至少涵蓋電腦最小尺寸與 iPad 橫向；觸控拖曳能否由自動化驅動由建立專案時的半日 spike 確認，真機 iPad 的觸控手感另需人工驗一次 |
| BDD 共用的後端資料準備 | `BackendChannel`，獨立 Maven 模組 `app/bdd-support/`（放在 `src/main/java`，不含任何 Cucumber 步驟或 `@Before`） | 手機 App 與櫃檯 Web 兩條測試線共用：直接寫資料庫建飯店、員工、各狀態的單、訂房，撥後端時鐘，以某位員工的身分呼叫正式 API；`app/bdd/` 與 `app/web-bdd/` 各自引用它 |
| 住客掃碼頁 BDD techstack | 與櫃檯 Web 同一個 `app/web-bdd/` 模組（`Cucumber-JVM` ＋ `Playwright for Java`），同一個 glue 套件，住客的步驟與頁面物件各自一個子套件；跑 `specs/truth/features/guest-web/` 的 Gherkin 做 E2E；`run-feature.sh` 的 feature 路徑可用 `guest-web/…` 指定根 | 住客情境的前提與觸發多是飯店那邊的動作（同仁認領、櫃檯回答、主管換 QR、撥時鐘），`BackendChannel` 都有或一個 helper 就補得上；不另起模組或 Node 測試線。runner 不用加：腳本一律以 `-Dcucumber.features` 指定，Cucumber 的 JUnit 引擎在這個屬性有值時忽略 runner 寫死的目錄 |
| 住客掃碼頁驗收的視窗與輸入 | Playwright 的第三組瀏覽器設定 `phone`：WebKit、393 × 852、開啟觸控、語言預設繁體中文；規格句型寫「手機直向 393 × 852 觸控」 | 住客的規格只在 `phone` 跑；WebKit 近似 iPhone 上的 Safari，不等於真機，安全區域與工具列收放要在真機看一次 |
| BDD 後端資料準備補的 helper | `BackendChannel` 加取房間 QR 代碼與換 QR 的 helper（以主管身分呼叫 `GET /admin/rooms/{roomNo}/qr`、`POST /admin/rooms/{roomNo}/qr/rotate`，這兩個端點不在 `/api/v1` 下），以及住客評分的 helper | 住客驗收要用真的代碼開 `/g/{代碼}`；換 QR 走正式 API 才會連坐撤銷 session，直接寫表測不到真的行為 |
| 櫃檯 Web 送出失敗的模擬 | Playwright 攔請求：`route.abort` 模擬沒送到後端；`route.fetch()` 轉給真的後端後再中斷，模擬後端已提交但回應遺失；資料庫暫時觸發器（測試直接連資料庫裝上、情境結束移除）讓交易在中途真的失敗 | 驗收一次送出多張單的全有或全無、重送回放不重複；正式程式不為測試開後門 |
| Web 單元測試 | `Vitest` | 只測沒有畫面的純計算；櫃檯例如看板分欄與未結案張數、等待 10 分與 20 分的門檻、瀏覽器語言比對，代開單的張數、摘要、單標題、房號輸入過濾、建議房號、類似單比對與冪等鍵保留規則；住客例如狀態值對顯示階段的對照表、狀態訊息框的優先序、10 分鐘額度估算、報修文字組合、問候時段、住客的瀏覽器語言比對、401 重換一次的狀態機；驗收仍以 E2E 為主線 |

## 本次開發不引入的技術

- H2 或其他記憶體資料庫替代 PostgreSQL 做驗收
- 記憶體或 embedded Kafka 替代品做驗收
- 直接對接 APNS：iOS 推播一律經 FCM 轉送，後端只維護一種推播地址
- LLM 問答（查一下只做關鍵字比對與出處，介面留椿）
- 後端影像處理函式庫（照片壓縮由 App 端處理，後端只檢查是不是 JPEG 與大小後原樣存放）
- iOS 專屬 BDD runner（XCUITest ＋ Cucumberish）與 Maestro：雙平台改採 Appium ＋ Cucumber-JVM，不另建平台專屬測試線
- `Skip`、`Swift SDK for Android` 等跨平台工具鏈：隨 Swift 雙平台路線一併放棄，改採 Flutter 單一程式碼庫
- `appium-flutter-driver`、`appium-flutter-integration-driver`：前者建在已棄用的 `flutter_driver` SDK 上且只能測 debug/profile build，後者要把測試 harness 編進 App；兩者都會打破 Appium 黑箱驅動與 App 實作解耦，改採 vanilla XCUITest／UiAutomator2
- 純 Dart `image` 套件做照片壓縮：release build 下 decode/encode 過慢，改用 `image_picker` 本身的縮放與壓縮
- `camera`、`flutter_image_compress`：員工只需要系統相機與系統相簿，不需要 App 內嵌取景畫面；浮水印不畫進照片，就不需要另外重新編碼
- 讓 App 直接向雲端儲存下載照片的臨時網址（signed URL）：照片經後端轉送，等流量成為問題再改
- iOS 26 Liquid Glass、Live Activity／動態島：Flutter 無官方支援，本增量視覺降級，不追求
- `Next.js` 等需要另外部署 Node 伺服器來產生畫面的框架：櫃檯 Web 是登入後才使用的內部工具，建置成靜態檔即可
- 以 `~/IdeaProjects/tms-web` 的程式當櫃檯 Web 的基底：只借它的函式庫組合，不借程式、設定與版本
- 櫃檯 Web 另起一條 Node 的驗收測試線（`Playwright Test` ＋ `playwright-bdd`、`cucumber-js`）：後端資料準備要用另一種語言重寫一份，改採 Cucumber-JVM ＋ Playwright for Java
- 以 Appium 驅動 iPad 上的 Safari 當櫃檯 Web 的驗收主線：最慢、最不穩，且主要使用環境是電腦瀏覽器
- 櫃檯 Web 的元件測試層（把單一畫面元件單獨畫出來檢查的測試）：與 E2E 重疊，只保留純計算的單元測試
- 瀏覽器原生拖放（HTML Drag and Drop API）與建在它上面的拖曳函式庫：觸控裝置上的行為由系統決定，難以照設計圖控制，也難以自動化
- 瀏覽器內建的 `EventSource`：不能帶 `Authorization` 標頭，改用 `fetch` 讀取事件串流
- `WebSocket`：櫃檯 Web 沒有需要即時推給後端的訊息，沿用後端既有的事件串流
- 以 cookie 當櫃檯 Web 的登入憑證、把登入憑證存在 `localStorage`、把登入憑證放在網址上
- 後端的跨網域設定（CORS）：櫃檯 Web 與後端同源部署（同源部署是假設，第一次部署前要確認；若不成立，需要後端加跨網域設定）
- 櫃檯 Web 一定要有 Service Worker 才能運作的功能
- 把櫃檯 Web 包成 iPad App 的外殼（PWA、WKWebView、`Capacitor`）：路線未定，目前只交付 Web
- 住客掃碼頁另起前端專案、另起驗收測試模組或 Node 測試線：與櫃檯 Web 同一個程式、同一條測試線，用延遲載入與第三組瀏覽器設定分開
- 住客 session 放 `localStorage`、放 `Authorization: Bearer` 標頭、或放在網址上：壽命由後端管，存分頁即可；標頭與櫃檯分開
- 住客掃碼頁用瀏覽器內建的 `EventSource` 讀串流：不能帶住客標頭，與櫃檯同樣用 `fetch`
- 住客掃碼頁的韓文介面、要安裝或加到主畫面才能用的功能（PWA、原生 App）、照片拍攝與上傳：本版不做
- 住客頁的網址放在後端的 `/guest/**` 底下：那是 API 的路徑，網頁路由用 `/g/`
