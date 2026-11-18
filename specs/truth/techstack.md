# Phase 0 技術堆疊總結：飯店任務交辦系統（TMS）

## 系統有哪些端

系統有三個端：

- 後端 API：`tms/`
- 員工 App（房務、工務員工的手機 App）：`app/mobile/`
- 櫃檯 Web（櫃檯人員、主管、值班主管的網頁）：`app/web/`

## 技術堆疊總覽

### 前端

| 類別 | 採用技術 | 用途 |
| --- | --- | --- |
| 員工端 App 框架 | `Flutter`（stable 3.47），單一程式碼庫支援 iOS ＋ Android 雙平台 | 房務／工務員工的接單、執行、工務單、查一下；App 程式碼在本 repo `app/`，BDD harness 在 `app/bdd/`；iOS 先行，Android 待本機裝 SDK 後跟進；**有條件採用**——gate 在一日 Appium spike，失敗則改採 React Native |
| App 推播 | `firebase_messaging` | 員工推播事件的收件與導頁；已知 iOS `getInitialMessage()` 歷史缺陷，F3 推播為第一支落地的 Appium 金絲雀情境 |
| App 相機與照片 | `camera` ＋ `image_picker`（拍攝與選取）、`flutter_image_compress`（壓縮）、`dart:ui` Canvas（浮水印，於 isolate／`compute()` 中執行） | 工務單照片 1–3 張的拍攝、壓縮與浮水印 |
| App 掃碼 | `mobile_scanner` | 住客 QR 掃碼進需求；已知從 Sheet 進入後黑屏風險，需鎖版本並做 dispose／re-init |
| App 視覺 | Flutter 自訂 Cupertino 風主題，以 `ui/prototype/Main.dc.html` 視覺規格為準 | iOS 不追 Liquid Glass／Live Activity，本增量視覺降級已簽核 |
| App 端驗收規格 | Gherkin feature files（`# language: zh-TW`） | App 端行為測項，例如照片、離線重送、推播去處；runner 見「測試與驗證」 |
| 櫃檯 Web 框架與建置 | `React` ＋ `TypeScript`，以 `Vite` 建置成單頁應用（登入後由瀏覽器自己換頁，建置結果是一包靜態檔） | 櫃檯人員、主管、值班主管用的總覽看板、待派單、派單與當場回答畫面；程式在本 repo `app/web/`；直接呼叫 `tms` 後端，不另起後端；套件版本在建立 `app/web/` 時鎖定當下的穩定版 |
| 櫃檯 Web 換頁 | `React Router` | 登入、改密碼、總覽看板、待派單四個頁面之間的切換；詳情、當場回答、帳號是蓋在頁面上的面板 |
| 櫃檯 Web 後端資料 | `TanStack Query` | 從後端抓資料、收到即時訊號後重抓、連線異常時每 5 秒輪詢、送出失敗時把畫面回復原狀 |
| 櫃檯 Web 畫面狀態 | `Zustand` | 只存在畫面上的狀態：登入身分、泳道、篩選、房號搜尋、拖曳中的提示 |
| 櫃檯 Web 樣式與版面 | `Tailwind CSS`；一份隨視窗伸縮的版面，以設計圖 1180×820 為基準 | 主要使用環境是電腦瀏覽器，同時支援 iPad 橫向；視窗更寬時看板的欄跟著變寬；電腦最小支援視窗 1280×720，iPad 橫向 1180×820 同樣支援，更小就出現捲軸、不另外設計；滑鼠與觸控都能完成所有操作 |
| 櫃檯 Web 多語系 | `i18next` ＋ `react-i18next` | 繁體中文、英文、日文、越南文四種介面語言；每個人選的語言存在 `localStorage`（瀏覽器長期保存的儲存空間），以員工編號為鍵、每人一筆，記在這台裝置上，與登入憑證分開 |
| 櫃檯 Web 拖曳派單 | 以 Pointer Events（瀏覽器把滑鼠與手指統一成同一種事件的介面）實作的拖曳函式庫，dnd-kit 系列 | 把待派單卡片拖到房務或工務，滑鼠與觸控走同一套程式；確切套件（`@dnd-kit/core` 或 `@dnd-kit/react`）由建立專案時的半日 spike（正式動工前花半天實際跑一次，確認某個做法行不行得通）選定 |
| 櫃檯 Web 登入憑證 | 後端發的 JWT 存在 `sessionStorage`（每個分頁各一份、分頁關掉就清掉的儲存空間），請求以 `Authorization: Bearer` 標頭帶出 | 重新整理網頁不用重新登入；關掉分頁或瀏覽器就要重新登入；登出時清掉 |
| 櫃檯 Web 連後端 | 開發時由 Vite 開發伺服器代理到本機後端；正式環境與後端同源部署（同一個網址底下）；API 位址建置時可設定，預設相對路徑 | 同源部署時後端不需要跨網域設定（CORS，後端宣告允許哪些網站來呼叫它）；同源部署是假設，第一次部署前要確認；若不成立，需要後端加跨網域設定 |
| 櫃檯 Web 即時更新 | 以 `fetch` 讀取後端的員工事件串流 `GET /staff/stream`（可帶 `Authorization` 標頭）；異常時每 5 秒輪詢 | 櫃檯 Web 是員工事件串流的客戶端：串流只送「哪張單有變化」的訊號，收到就重抓資料；串流斷線、約兩個心跳週期沒有資料、或後端宣告降級時，顯示提醒列並改成每 5 秒重抓，恢復後停止輪詢 |
| 櫃檯 Web 驗收規格 | Gherkin feature files（`# language: zh-TW`），在 `specs/truth/features/frontdesk-web/` | 櫃檯 Web 的畫面行為測項；runner 見「測試與驗證」 |

### 後端

| 類別 | 採用技術 | 用途 |
| --- | --- | --- |
| 語言與執行環境 | `Java 25` | 後端主要語言 |
| 應用框架 | `Spring Boot 4.1.1`（Maven，`./mvnw`） | REST API、狀態機、worker 模組在同一個程序 |
| 認證 | JWT（HS256，TTL 12h） | 員工與主管登入，claims 帶 tenant、staff、role、dept |
| 即時推送 | SSE（`SseEmitter`） | 住客與後台即時更新 |
| 訊息佇列 | `Apache Kafka 3.9`（outbox → relay → Kafka） | 事件分發給 SSE、投影、推播消費者、PMS 回寫消費者 |
| 排程 | `Quartz`（租戶感知）＋ Spring 排程 | 班前排房、班末掃描、到期提醒、清理；relay 輪詢用 Spring 排程 |
| 推播 | 推播消費者（Kafka `tms.push` → Firebase Cloud Messaging，`firebase-admin` SDK） | 員工推播事件送到登記的手機（iOS 經 FCM 轉 APNS）；以 outbox 冪等鍵去重並寫 `push_deliveries` 送達紀錄；暫時性錯誤指數退避重試 3 次後進 `tms.push.DLT`；未設定 `tms.push.fcm.credentials-path` 時只寫日誌與送達紀錄 |
| 時間來源 | 注入的 `java.time.Clock` | 計時、排序、提醒都以同一個可替換時鐘為準 |

### 資料與媒體處理

| 類別 | 採用技術 | 用途 |
| --- | --- | --- |
| 資料庫 | `PostgreSQL 16` | 權威狀態，包括 tasks、task_events、outbox、processed_ops 等 |
| Schema 遷移 | `Flyway` | 資料庫版本管理 |
| 冪等 | `Idempotency-Key` ＋ `processed_ops` | 重送回放，含離線重送 |
| 本機環境 | `docker-compose`（Postgres 埠 5433、Kafka） | 開發用依賴服務 |

### 測試與驗證

| 類別 | 採用技術 | 用途 |
| --- | --- | --- |
| 後端 BDD techstack | `Cucumber-JVM` ＋ `Spring Boot Test` | 後端 E2E：跑後端 Gherkin，以真實 HTTP 打 API，並斷言資料庫權威狀態與 outbox |
| 後端 E2E 依賴 | `Testcontainers`（PostgreSQL、Kafka） | 測試時啟動真的資料庫與訊息佇列 |
| 員工 App BDD techstack | `Appium` ＋ `Cucumber-JVM`（iOS：vanilla `XCUITest` driver；Android：vanilla `UiAutomator2` driver） | 一套 Gherkin ＋ 一套 Java step definitions 驅動兩平台；driver 讀取 Flutter `Semantics(identifier:)` 曝露到原生 accessibility API 的節點；與後端共用 Cucumber-JVM 生態 |
| App 元素定位 | `Semantics(identifier:)` 掛 dot＋kebab accessibility id，Java step 層 `byTestId()` helper 分流 | iOS 以 `AppiumBy.accessibilityId(id)` 定位（對應 `accessibilityIdentifier`）；Android 以 resource-id 比對定位（`identifier` 落成 `resource-id`，不是 `content-desc`），兩平台 locator 不同，不可共用同一個呼叫 |
| App 測試後門 | `--dart-define` 隔離的 debug build ＋ localhost debug HTTP endpoint | 可注入裝置時間、強制離線、強制第 N 張照片上傳失敗；Wave 1 App 骨架即內建，不得後補；Java step definitions 直接打，不做成隱藏 UI |
| 櫃檯 Web BDD techstack | `Cucumber-JVM` ＋ `Playwright for Java`，獨立 Maven 模組 `app/web-bdd/` | 櫃檯 Web E2E：跑 `specs/truth/features/frontdesk-web/` 的 Gherkin，開真的瀏覽器操作畫面、連真的後端與資料庫；Java step definitions 放在自己的 glue 套件（Cucumber 用來找每句 Gherkin 對應哪段 Java 程式的 Java 套件；不在手機 App 測試線的 `com.athena.app.bdd` 底下），不會啟動 Appium |
| 櫃檯 Web 驗收的視窗與輸入 | Playwright 的兩組瀏覽器設定：1280×720 以滑鼠操作；1180×820、WebKit 引擎（Safari 所用的瀏覽器引擎，用來近似 iPad 上的 Safari，但不等於真機）、開啟觸控 | 驗收至少涵蓋電腦最小尺寸與 iPad 橫向；觸控拖曳能否由自動化驅動由建立專案時的半日 spike 確認，真機 iPad 的觸控手感另需人工驗一次 |
| BDD 共用的後端資料準備 | `BackendChannel`，獨立 Maven 模組 `app/bdd-support/`（放在 `src/main/java`，不含任何 Cucumber 步驟或 `@Before`） | 手機 App 與櫃檯 Web 兩條測試線共用：直接寫資料庫建飯店、員工、各狀態的單、訂房，撥後端時鐘，以某位員工的身分呼叫正式 API；`app/bdd/` 與 `app/web-bdd/` 各自引用它 |
| 櫃檯 Web 單元測試 | `Vitest` | 只測沒有畫面的純計算，例如看板分欄與未結案張數、等待 10 分與 20 分的門檻、瀏覽器語言比對；驗收仍以 E2E 為主線 |

## 本次開發不引入的技術

- H2 或其他記憶體資料庫替代 PostgreSQL 做驗收
- 記憶體或 embedded Kafka 替代品做驗收
- 直接對接 APNS：iOS 推播一律經 FCM 轉送，後端只維護一種推播地址
- LLM 問答（查一下只做關鍵字比對與出處，介面留椿）
- 後端影像處理函式庫（照片壓縮與浮水印由 App 端處理）
- iOS 專屬 BDD runner（XCUITest ＋ Cucumberish）與 Maestro：雙平台改採 Appium ＋ Cucumber-JVM，不另建平台專屬測試線
- `Skip`、`Swift SDK for Android` 等跨平台工具鏈：隨 Swift 雙平台路線一併放棄，改採 Flutter 單一程式碼庫
- `appium-flutter-driver`、`appium-flutter-integration-driver`：前者建在已棄用的 `flutter_driver` SDK 上且只能測 debug/profile build，後者要把測試 harness 編進 App；兩者都會打破 Appium 黑箱驅動與 App 實作解耦，改採 vanilla XCUITest／UiAutomator2
- 純 Dart `image` 套件做照片壓縮：release build 下 decode/encode 過慢，改用 `flutter_image_compress`
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
