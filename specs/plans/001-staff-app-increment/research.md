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
  - **iOS App**：只有 Gherkin feature file（`# language: zh-TW`），runner 未選。
- **Rationale**:
  - 後端是 Java 25 ＋ Spring Boot 4.1.1，同語言同專案就能直接用 Spring 元件準備資料與斷言。基線 Gherkin handoff 也建議 Cucumber-JVM。
  - App 依使用者指示只產出測項。等 App 的技術選型確定，再回到 `/technical-research` 決定 runner，例如 XCUITest 搭配 Gherkin 橋接，或 Appium ＋ Cucumber。
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

## 殘餘風險與後續驗證

- **後端程式碼目前不在本機**：`tms/tms` 是 gitlink（160000，指向 `1b65c99`），沒有 `.gitmodules`，所以目錄是空的。這輪的技術堆疊依據是基線 spec 與 `docker-compose.yml`。進入 `/system-analysis` 或 `/implement` 前要先取回子模組，確認 Spring Boot、Java、Kafka 實際版本，以及 Cucumber 與 Testcontainers 跟 Spring Boot 4.1.1 的相容性。
- **照片儲存媒介未決**：工務單照片每張 1–3 張、完修照片必填、可補照片，這是本 plan 新增的大量二進位資料。存資料庫、本機檔案系統，或物件儲存介面，交由 `/data-plan` 決定。浮水印與壓縮依 spec 假設在 App 端處理，後端不引入影像處理函式庫。
- **App 端測項無執行器**：App 的 Gherkin 目前不會被自動驗證，可能隨實作慢慢失真。App 技術選型確定後，要回到本 skill 補 runner。
- **Testcontainers 需要 Docker**：開發機與 CI 都要能跑 Docker。這台 Windows 機器已用 docker-compose 跑 Postgres 與 Kafka，推定可行，但尚未實測。
