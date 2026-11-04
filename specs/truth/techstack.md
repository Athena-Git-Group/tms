# Phase 0 技術堆疊總結：飯店任務交辦系統（TMS）

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

### 後端

| 類別 | 採用技術 | 用途 |
| --- | --- | --- |
| 語言與執行環境 | `Java 25` | 後端主要語言 |
| 應用框架 | `Spring Boot 4.1.1`（Maven，`./mvnw`） | REST API、狀態機、worker 模組在同一個程序 |
| 認證 | JWT（HS256，TTL 12h） | 員工與主管登入，claims 帶 tenant、staff、role、dept |
| 即時推送 | SSE（`SseEmitter`） | 住客與後台即時更新 |
| 訊息佇列 | `Apache Kafka 3.9`（outbox → relay → Kafka） | 事件分發給 SSE、投影、推播消費者、PMS 回寫消費者 |
| 排程 | `Quartz`（租戶感知）＋ Spring 排程 | 班前排房、班末掃描、到期提醒、清理；relay 輪詢用 Spring 排程 |
| 推播 | 推播消費者（MVP 只寫日誌與資料列） | 員工推播事件；不接真的 APNS/FCM |
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

## 本次開發不引入的技術

- H2 或其他記憶體資料庫替代 PostgreSQL 做驗收
- 記憶體或 embedded Kafka 替代品做驗收
- 真實 APNS/FCM 推播投遞（後端 MVP 只寫日誌與資料列）
- LLM 問答（查一下只做關鍵字比對與出處，介面留椿）
- 後端影像處理函式庫（照片壓縮與浮水印由 App 端處理）
- iOS 專屬 BDD runner（XCUITest ＋ Cucumberish）與 Maestro：雙平台改採 Appium ＋ Cucumber-JVM，不另建平台專屬測試線
- `Skip`、`Swift SDK for Android` 等跨平台工具鏈：隨 Swift 雙平台路線一併放棄，改採 Flutter 單一程式碼庫
- `appium-flutter-driver`、`appium-flutter-integration-driver`：前者建在已棄用的 `flutter_driver` SDK 上且只能測 debug/profile build，後者要把測試 harness 編進 App；兩者都會打破 Appium 黑箱驅動與 App 實作解耦，改採 vanilla XCUITest／UiAutomator2
- 純 Dart `image` 套件做照片壓縮：release build 下 decode/encode 過慢，改用 `flutter_image_compress`
- iOS 26 Liquid Glass、Live Activity／動態島：Flutter 無官方支援，本增量視覺降級，不追求
