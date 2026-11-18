# Phase 0 研究：房務／工務 App 增量

依據：`spec.md`（US1–US6、FR-001–048、NFR-001–006）、基線 `specs/tms-poc-to-backend/specify/spec.md`、本輪 technical-research clarify（2026-10-23，使用者回覆「2 1 1，但是先只要產生 feature-file 測項就好」）。

## 決策 1：本次交付的系統端

- **Decision**: 系統有兩個端：
  - **後端 API**：Spring Boot，延續基線。這次要實作，也要用自動化 E2E 驗收。
  - **iOS 員工 App**：這次只產出 App 端 Gherkin feature file 測項。不選 runner、不寫 step definitions，也不實作 App。
- **Rationale**: 使用者在 clarify 第 1 題選了「後端 API ＋ iOS 員工 App」，又補充「先只要產生 feature-file 測項」。
  - 原型裡有不少只發生在 App 上的行為，例如照片壓縮與浮水印、離線暫存與重送、推播點開後的去處、查一下的推薦問題排序。這些需要 App 端的 Gherkin 承接，不能只放在後端假設裡。
  - App 的技術選型與自動化方式這次刻意延後，所以 App 端測項目前只當作 PM 與 App 開發線的驗收依據，不會被執行。
- **Alternatives considered**:
  - 只有後端 API，App 另外排：較精簡，但原型的 App 端行為就沒有驗收落點。使用者未採用。
  - 後端 API ＋ 主管 Web 後台：主管作廢、退池附交代、回覆問主管需要畫面，但原型只有員工 App。使用者未採用。主管操作仍走後端 admin API。

## 決策 2：各端 BDD techstack

- **Decision**:
  - **後端**：`Cucumber-JVM` ＋ `Spring Boot Test`。step definitions 用 Java，放在後端 Maven 專案的測試源碼；feature 檔放在測試資源；用 `mvn verify` 執行。
  - **員工 App**：Gherkin feature file（`# language: zh-TW`）為驗收真相；runner 為 `Appium` ＋ `Cucumber-JVM`，細節見決策 5。
- **Rationale**:
  - 後端是 Java 25 ＋ Spring Boot 4.1.1，同語言同專案就能直接用 Spring 元件準備資料與斷言。基線 Gherkin handoff 也建議 Cucumber-JVM。
  - App 端 runner 與後端共用 Cucumber-JVM 生態，同一套中文 Gherkin DSL 不必翻譯；driver 與元素定位策略的取捨在決策 5、6。
- **Alternatives considered**:
  - `Karate`：不必寫 Java step，但自有語法跟 aixbdd 的 DSL 分層難以對齊。
  - `behave`（Python）從外部打 API：測試與後端解耦，但要多維護一套環境，也不能用 Java 領域物件準備資料。

## 決策 3：後端測試策略

- **Decision**: 後端驗收採完整 E2E：
  - 以 `@SpringBootTest(webEnvironment = RANDOM_PORT)` 啟動整個應用程式，透過真實 HTTP 打 API。
  - 用 `Testcontainers` 啟動 PostgreSQL 16 與 Kafka。
  - 斷言 API 回應，也查資料庫的權威狀態：`tasks`、`task_events`、`outbox`、`processed_ops`，以及本 plan 新增的工務單與照片紀錄。
- **Rationale**: 本 plan 依賴的核心保證都需要真的 Postgres 與 Kafka 才驗得出來：
  - NFR-005 冪等重送，例如離線送出重送 3 次只建 1 張工務單。
  - NFR-006 的 409 守衛。
  - 一次一間（FR-003），會用到條件式 UPDATE 或 partial unique。
  - 工務單同房同類別不重開（FR-015）。
  - 推播事件經 outbox → Kafka → 推播消費者（FR-047）。
  - 依基線假設 A1，推播消費者在 MVP 只寫日誌與資料列，所以 E2E 用 outbox 的 `push` 列與推播消費者的資料列當推播已發出的證據，不接真的 APNS/FCM。
- **Alternatives considered**:
  - MockMvc ＋ H2 ＋ 記憶體 Kafka：跑得快，但 partial unique、條件式 UPDATE、同鍵訊息順序跟正式環境不一致。使用者未採用。
  - E2E 再加單元測試：這輪使用者選純 E2E。若排房或計時公式（FR-029、FR-031、FR-032）在實作時需要大量組合驗證，可以再提出來。

## 決策 4：時間與排程在 E2E 中的控制方式

- **Decision**: 後端透過注入的 `java.time.Clock` 取得「現在」，E2E 改用可調整的測試時鐘。有時間相關的排程，例如延後提醒、班末掃描、中斷 30 分鐘提醒，由 step 直接觸發對應的 Quartz job，不等真實時間。
- **Rationale**: 驗收裡有很多時間依賴：
  - 已做 15 分鐘接續計時（FR-007）
  - 剩餘工時 95 分（FR-029）
  - 班末自行中斷留在原狀並推播主管（D11）
  - 下一間排序依退房與入住時間（FR-031）
  
  用真實時間無法重現，也會讓測試不穩定。
- **Alternatives considered**:
  - 以資料庫 `now()` 為準，測試改寫資料列時間：Java 端的計算就無法控制，step 也會耦合 schema。
  - 真實時間 ＋ 等待：太慢且不穩定，不採用。

## 決策 5：員工 App 的框架與 BDD runner

- **Decision**:
  - **員工 App**：以 `Flutter`（stable 3.47）開發，單一程式碼庫支援 iOS 與 Android 雙平台。**有條件採用**：先在 Xcode 就緒後做一日 Appium spike——五種代表性 widget 形態（列表卡片動態 id、帶 `--state` 修飾的 chip、`onPressed: null` 的禁用按鈕、照片格、iOS Alert/Dialog），在實機 iOS 與一台低階 Android 上逐一驗證 id 定位、文字讀取（content-desc／label）、`enabled` 屬性三者皆可。spike 是採用 Flutter 的閘門，失敗就改採 React Native。
  - **App 端 BDD runner**：維持 `Appium` ＋ `Cucumber-JVM`，直讀 `specs/truth/features/ios-app/`。driver 改為 vanilla **XCUITest（iOS）＋ UiAutomator2（Android）**，讀取 Flutter 透過 `Semantics(identifier:)` 曝露到原生 accessibility API 的節點；明確排除 `appium-flutter-driver`（建在已棄用的 `flutter_driver` SDK 上，只能測 debug/profile build，測不了 TestFlight／Firebase App Distribution 發出的 release build）與 `appium-flutter-integration-driver`（要把測試 harness 編進 App 內的 Dart VM）。
- **Rationale**: 使用者 2026-11-04 拍板放棄 Swift 雙平台路線——那條路線的 Android 側完全押在 Skip、Swift SDK for Android 這類使用者基數極小、生態幾乎為零的跨平台工具鏈上，而本專案需要的 FCM、CameraX、MLKit 掃碼全在 Android 原生生態。
  - 獨立技術審查（四方案比較：Flutter、React Native、原生雙寫、Kotlin Multiplatform）判定 Flutter **有條件成立**：單人＋AI 協作下，Flutter 與 React Native 都只需實作一套 UI（13 個邏輯畫面、35 個 accessibility id），比原生雙寫或 KMP 少一半工作量；且 Flutter 的同一套 widget 在兩平台 pixel-identical，正好對應 `ui-plan.md` 只定義一套視覺規格給兩平台的前提，不需要像 React Native 那樣為兩邊原生元件的外觀差異額外調校。
  - Appium 走 accessibility id 的機制在 Flutter 官方存在且有文件：`SemanticsProperties.identifier` 會讓 Android 落成 `AccessibilityNodeInfo.setViewIdResourceName`（即 `resource-id`）、iOS 落成 `UIAccessibilityElement.accessibilityIdentifier`；設了 `identifier` 的節點會強制產生自己的 `SemanticsNode`，不會被祖先的 `MergeSemantics` 吞掉，這正是 `ui-plan.md` 大量使用「同一元素不同狀態共用同一 id」與列表動態 id 所依賴的語意。但傘狀 issue `flutter/flutter#18060` 自 2018 開到現在仍是 open、P2，沒有第一方 CI 保護這個互動不會壞，所以只能給「有條件成立」，不是無條件採用——spike 把「機制存在」轉換成「這個版本、這個裝置矩陣上真的可行」的證據。
  - 走 vanilla XCUITest／UiAutomator2 而非 appium-flutter-driver 系列，才能保住決策 5（原版）買到的性質：Appium 黑箱驅動已編譯的 App，runner 決策與 App 實作技術解耦。
- **Alternatives considered**:
  - `React Native`：Appium 風險明顯更低（`testID` 直接落成真的原生 `resource-id`／`accessibilityIdentifier`，不經 semantics 包裝），是本案唯一旗鼓相當的對手，也是本輪 spike 失敗時的現成退路；未選為主案是因為它用原生元件渲染，兩平台外觀天然不同，達不到與原型「單一視覺規格套兩平台」對齊的效果。
  - 原生雙寫（SwiftUI ＋ Kotlin）：Appium 風險最低（原生 accessibility identifier 一等公民），但 13 個畫面要在兩套語言各實作一次，單人開發下跟決策 2 否決「各平台原生 runner」的理由相同（兩份會各自漂移），只是從測試層移到了實作層，不採用。
  - Kotlin Multiplatform：共用商業邏輯自 2023 年穩定，但本案複雜度集中在 UI（列表、Sheet、計時 hero、照片上傳格的狀態回饋），KMP 只能共用這部分工作量裡較小的一塊，仍要同時維護 Swift 與 Kotlin 兩套 UI；若改走 Compose Multiplatform 共用 UI，其 iOS 支援 2025/05 才 Stable，比 Flutter 晚一年多且社群踩坑資料少得多，風險更高，不採用。

## 決策 6：accessibility id 的雙平台定位策略（兩平台不共用同一個 Appium locator）

- **Decision**: 沿用 `ui-plan.md` 的 dot 分段＋kebab-case id 命名約定，App 側一律經由 `Semantics(identifier: ...)` 掛上這個 id（實作上收斂成一個共用的 `TmsSemantics` 包裝元件，禁止用 `Key`／`ValueKey` 當測試標的）。`TmsSemantics` 必須同時負責 `enabled` 狀態的顯式曝露（`Semantics(enabled: onPressed != null, ...)`）——`CupertinoButton` 等元件在 `onPressed: null` 時**不會**自動把禁用狀態寫進 accessibility 樹，漏掉這層包裝的按鈕在 Appium 端讀起來永遠是可用的。App 進入點呼叫 `SemanticsBinding.instance.ensureSemantics()` 強制啟用語意樹，且必須在 `WidgetsFlutterBinding.ensureInitialized()` 之後呼叫，順序反了 App 會在啟動時白屏崩潰。兩平台的 Appium locator **不是同一個**：iOS 用 `AppiumBy.accessibilityId(id)`；Android 的 id 落在 `resource-id`，要用 `AppiumBy.id(appPackage + ":id/" + id)` 或等效的 resource-id 比對，不能用 `AppiumBy.accessibilityId`。Java step 層封裝一個 `byTestId(String id)` helper 依平台分流，feature 檔與 dsl 層仍然只看到一個 id。
- **Rationale**: Flutter 官方文件明確寫 `identifier` 在 Android 對應 `resource-id`、在 iOS 對應 `accessibilityIdentifier`，這是兩個不同的原生概念，單一個 `accessibilityId()` locator 呼叫沒辦法同時命中兩平台。若 step 層假設兩平台共用同一個 locator，Android 端會定位失敗。
- **Alternatives considered**:
  - 假設兩平台都用 `accessibilityId()` 定位：在 Android 會因為 id 落在 `resource-id` 而非 `content-desc` 而定位失敗，查證官方文件後排除。
  - 用 `ValueKey`／`Key` 當測試標的：完全不會曝露給原生 accessibility API，有真實 Flutter issue（`flutter/flutter#179333`）回報試過但失敗，不採用。

## 決策 7：App 端測試後門與第一支金絲雀 feature

- **Decision**: App 骨架第一波就要內建測試後門，提供裝置時間注入、強制離線、強制第 N 張照片上傳失敗三項能力；以 `--dart-define` 隔離成只在測試 build 生效，對外曝露方式用 localhost debug HTTP endpoint，讓 Java step definitions 直接打（不做成隱藏 UI）。第一支落地的 Appium 情境是 F3 推播（`推播/點開推播的去處.feature`），覆蓋冷啟動／背景／前景三種點開情境，當 `firebase_messaging` 的金絲雀。
- **Rationale**: `報修照片與送出.feature` 已經要求注入裝置時間、強制離線、強制第 2 張上傳失敗，後端的可調測試時鐘（決策 4）在 App 端沒有對應機制，Appium 黑箱也無法從外部塞這些狀態，這件事必須在 step definitions 開始寫之前決定。`firebase_messaging` 長年存在 iOS `getInitialMessage()` 回 `null`（尤其搭 `content_available`）與 terminated 狀態啟動後再進背景時 `onMessageOpenedApp` 非預期觸發這兩類問題，恰好打在 TMS 最依賴的推播導頁行為上，把 F3 當第一支金絲雀能在骨架初期就曝露這個風險，而不是等到大部分 App 都蓋完才發現。
- **Alternatives considered**:
  - 用隱藏手勢或按鈕做測試注入：對 Appium 自動化不如獨立 debug HTTP endpoint可靠，也有滲進 release build 的風險。
  - 把推播排到後面做：會讓 `firebase_messaging` 的歷史不穩定問題在骨架已經蓋得差不多時才被發現，屆時重新設計成本更高。

## 決策 8：iOS 視覺降級與 App 程式碼位置

- **Decision**: iOS 視覺目標降級為 Flutter 自訂的 Cupertino 風主題，以原型 `ui/prototype/Main.dc.html` 的視覺規格為準；本增量不追 iOS 26 Liquid Glass，也不做 Live Activity／動態島。App 程式碼在本 repo 內的 `app/`，BDD harness 規劃在 `app/bdd/`；開發機 Android SDK 尚未安裝，iOS 先行，Android SDK 裝妥後再補上 Android 端驗證。
- **Rationale**: 使用者 2026-11-04 已簽核這個視覺降級。Flutter 以 Impeller 自行渲染，無法重現 Apple Metal／Core Animation 的玻璃材質，目前只有 shader 近似或 platform view 包原生 UIKit 兩種繞法，官方沒有時程承諾；Live Activity 在 Flutter 也只有第三方套件、仍要手寫原生 Swift Widget Extension，模擬器測不了。把 App 程式碼放進本 repo（而非另開 repo），能讓它與要一致比對的 `specs/truth/features/ios-app/` 和後端 API／推播內容格式放在一起維護。
- **Alternatives considered**:
  - 用 platform view 包原生 UIKit 繞出 Liquid Glass：等於把原生程式碼寫回 Flutter 原本要避免重複實作的地方，本增量不採用。
  - App 另開獨立 repo：可讓 App release 節奏與後端／truth 脫鉤，但會讓 App 程式碼與它要對齊的 `specs/truth/features/ios-app/`、後端契約分散在兩處，不採用。

## 殘餘風險與後續驗證

- **後端程式碼目前不在本機**：`tms/tms` 是 gitlink（160000，指向 `1b65c99`），沒有 `.gitmodules`，所以目錄是空的。這輪的技術堆疊依據是基線 spec 與 `docker-compose.yml`。進入 `/system-analysis` 或 `/implement` 前要先取回子模組，確認 Spring Boot、Java、Kafka 實際版本，以及 Cucumber 與 Testcontainers 跟 Spring Boot 4.1.1 的相容性。
- **照片儲存媒介未決**：工務單照片每張 1–3 張、完修照片必填、可補照片，這是本 plan 新增的大量二進位資料。存資料庫、本機檔案系統，或物件儲存介面，交由 `/data-plan` 決定。浮水印與壓縮依 spec 假設在 App 端處理，後端不引入影像處理函式庫。
- **spike 閘門已過 iOS simulator 半邊，實機 iOS 與低階 Android 半邊未補**：五種 widget 形態在 iOS simulator 上 id 定位（含多 dot、`--` 修飾）、文字讀取（`getText()` 與 `label` 屬性皆可）、`enabled` 屬性（`isEnabled()` 與 `enabled` 屬性皆可；`accessible` 屬性恆為 true、不可用於分辨）全數通過。實機 iOS（歷史問題正是 real device 上 page source 不完整）與低階 Android 要在硬體到位後補驗；失敗時改採 React Native 的退路維持有效。另一個已知版本釘：`io.appium:java-client:9.5.0` 與 Selenium ≥4.34.0 不相容（`org.openqa.selenium.html5.*` 被移除），selenium-bom 必須釘在 `4.33.0`。
- **推播 E2E 的對接方式：以 `xcrun simctl push` 注入真 APNs payload 為準，`firebase_messaging` 後補**：後端推播消費者 MVP 只寫日誌與資料列、不接真的 APNS/FCM，App 端 F3 情境改由 harness 用 `simctl push` 把 APNs payload（`dest`／`room`／`highlight` 欄位）直送 simulator——走的是真 iOS 通知管線與點開導頁路徑，不經 Firebase。`firebase_messaging` 的接入（與它的 `getInitialMessage()` 歷史病灶驗證）留待 Firebase 專案建立後進行，屆時 PushGateway 只換內部實作、不動呼叫端與 payload 欄位約定。後端真的發 FCM/APNS 時，payload 欄位必須沿用同一套 `dest`／`room`／`highlight` 約定。
- **浮水印若燒進圖片 pixel，Appium 讀不到**：`報修照片與送出.feature` 要斷言浮水印文字（如「305 · 10/22 14:32」），若浮水印是用 `dart:ui` Canvas 直接畫進縮圖像素，Appium 永遠讀不到那段文字。App 必須在縮圖上額外渲染一個帶同樣字串、掛了 accessibility id 的可及節點，這要在實作時就設計好，不能等寫 step definitions 才發現。
- **中低階 Android 相機與渲染負擔**：Flutter 3.44 起 Android 10+ 移除 Skia fallback，Impeller/Vulkan 成為強制渲染器，沒有退路；同時工務單照片要跑相機預覽、壓縮、Canvas 浮水印。壓縮要用 `flutter_image_compress`（原生 Kotlin／ObjC）而非純 Dart `image` 套件（release build decode/encode 過慢），壓縮與浮水印要丟進 `compute()`／isolate，否則中低階機型會掉幀甚至 OOM；第一個月要買兩台低階 Android 實機進測試矩陣，不能只用模擬器。
- **QR 掃碼黑屏風險**：`mobile_scanner` 是目前維護中的選擇，但從 Sheet／面板進入後黑屏是已知高發路徑（`juliansteenbakker/mobile_scanner#605`、`#1378`）。實作時要鎖版本、嚴格 dispose／re-init，並在進入掃碼前先驗相機權限狀態。
- **ui-plan.md 與 plan.md 有未修正的 Swift 前提殘留**：`ui-plan.md` 第 5 行寫「iOS，Swift；Android 另案」、第 236 行把 Android locator 錯寫成 `contentDescription`（應為 `resource-id`，呼應決策 6）；`plan.md` 第 49、55 行寫「iOS 員工 App 不在本 repo」，與決策 8 的「App 程式碼在本 repo `app/`」已不一致。這兩份文件的修正不在本輪 `/technical-research` 範圍內，由委派方另行處理。
- **Testcontainers 需要 Docker**：開發機與 CI 都要能跑 Docker。這台 Windows 機器已用 docker-compose 跑 Postgres 與 Kafka，推定可行，但尚未實測。
