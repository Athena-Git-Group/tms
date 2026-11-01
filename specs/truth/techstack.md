# Phase 0 技術堆疊總結：飯店任務交辦系統（TMS）

## 技術堆疊總覽

### 前端

| 類別 | 採用技術 | 用途 |
| --- | --- | --- |
| 員工端 | iOS 員工 App（房務／工務） | 員工的接單、執行、工務單、查一下；App 本身的技術選型尚未拍板，不在本 repo 實作 |
| App 端驗收規格 | Gherkin feature files（`# language: zh-TW`） | App 端行為測項，例如照片、離線重送、推播去處；目前只當規格，不執行 |

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
| iOS App BDD techstack | 尚無 runner（只有 feature file 測項） | App 端 Gherkin 目前只當驗收規格，App 技術選型確定後再補 |

## 本次開發不引入的技術

- H2 或其他記憶體資料庫替代 PostgreSQL 做驗收
- 記憶體或 embedded Kafka 替代品做驗收
- 真實 APNS/FCM 推播投遞（MVP 只寫日誌與資料列）
- LLM 問答（查一下只做關鍵字比對與出處，介面留椿）
- 後端影像處理函式庫（照片壓縮與浮水印由 App 端處理）
- iOS App 自動化 BDD runner（本輪只產出 feature file 測項）
