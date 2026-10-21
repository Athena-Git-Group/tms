# 需求原文（自 points/tms-poc-to-backend.md 抄錄，不可變）

依 docs/11-requirements-architecture.html 的系統設計，把 docs/13-poc/ 的 10 個
HTML POC 頁面（index、desk、manager、schedule、guest、owner、events、demo、settings、
housekeeping ＋ shared/）實作為真正的後端程式。目標位置 `tms/`（Spring Boot 4.1.1 +
Maven 骨架，Java 25；依賴含 WebMVC、Data JPA、Kafka、Quartz、OpenTelemetry、
H2/PostgreSQL、springdoc OpenAPI），目前零業務程式碼（僅 `TmsApplication.java`
與一支 context-load 測試），greenfield 全系統後端實作。

## 補充輸入（flow 派工時附帶）

- 需求權威來源：`docs/11-requirements-architecture.html`（系統設計）、
  `docs/12-data-model.html`（資料模型，逐欄位規格以它為準）
- UI POC（參考、非權威）：`docs/13-poc/`；POC 與設計文件有落差時以 docs/11、12 為準，
  差異記入規格
- 已拍板產品決策（2026-10-07 使用者決議）：走 SaaS、開放非自家 PMS 的客戶使用；
  訊息匯流排採 Kafka（MVP 階段整合深度由 spec 依設計文件與 MVP 原則定案，可分階段）
- Point report 點名必須定案（或明確列入 Unresolved）的事項：API 形狀、MVP 範圍切分、
  認證/授權、Kafka 與 Quartz 整合深度、POC 頁面是否由後端 serve
