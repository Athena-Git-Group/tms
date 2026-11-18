# Handoff: 員工 App（iOS）開工——後端 truth 全綠後的 Wave 2 啟動

**Date**: 2026-11-04 18:30
**Branch**: feature/gherkin
**Repo**: tms-mvp

## 1. 前情提要

本 increment（001-staff-app-increment）的後端半邊已全部交付：四筆產品裁決（待認領用語、未接超時兩段模型、急件系統判定、稱謂您）落進 truth 並實作完成，truth suite **117/117 全綠**（commit `c9b48a1`）。下一步是 Wave 2：在 repo 內新開 `app/` 目錄（與 `tms/` 並列，使用者已拍板）開發 iOS 員工 App，BDD runner 已拍板 Appium＋Cucumber-JVM（已選、未建）。

## 2. 必讀檔案

> 所有路徑相對於專案根。依順序閱讀即可建立 context。

- `specs/plans/001-staff-app-increment/ui/ui-plan.md` — App 的控制平面：13 個畫面、畫面↔feature 歸屬矩陣、accessibility id 約定（約 35 個，Appium 斷言靠它）、純視覺不進 truth 清單
- `specs/truth/features/ios-app/` — 13 支 feature＋各模組 dsl.md，App 的驗收真相（Appium 要直讀這個目錄，比照 tms/ 的 RunTruthFeaturesTest 讀 backend 的做法）
- `specs/plans/001-staff-app-increment/ui/prototype/README.md` — 原型（Main.dc.html）的視覺規格定位與開啟方式（要起本機 http server，file:// 會擋 script）
- `specs/plans/001-staff-app-increment/truth-delta.md` — 本輪 truth 變動總帳（/dsl-refine、/api-plan、/data-plan 三段）＋上游缺口清單
- `specs/plans/001-staff-app-increment/research.md:60-78` — Appium runner 的拍板決策與理由

## 3. 已完成項目

- [x] /ui-plan：原型入庫（含 runtime）、ui-plan.md、index.html「你→您」掃除（commit `6fff7c7`）
- [x] /dsl-refine：四筆裁決落 truth、4 支新 ios-app feature、10 筆用語落差（commit `6fff7c7`）
- [x] /api-plan＋/data-plan：急件判定、inspectedCount、reminderStage 契約與資料模型（commit `c74add2`）
- [x] /tasks＋/implement T204–T239：後端全部實作，truth suite 117/117 全綠（commits `2c56206`、`a0c4c92`、`c9b48a1`）

## 4. 待辦項目

1. **建 `app/` iOS 專案骨架**（SwiftUI；與 `tms/` 並列）——驗收：專案可在 simulator 跑起、登入殼畫面出現（login 規格未寫，先做最薄殼，呼叫後端 `POST /staff-accounts/login`）
2. **建 Appium＋Cucumber-JVM harness**——驗收：runner 直讀 `specs/truth/features/ios-app/`，能跑到任一場景（紅燈可、不可 undefined step）；accessibility id 按 ui-plan.md 約定掛
3. **第一支 feature 端到端綠**——建議從 `查一下/查一下畫面.feature`（4 Rule、原型覆蓋完整）開始
4. **補 App increment 的規格缺口**（走 /dsl-refine）——登入／帳號、通知中心、工務視角（coverage 報告 §4 的 3、4、7 項）
5. **後端遺留小項**（非阻塞）：產品碼 5 處「待接池」文案（StaffTaskService.java:50、TaskExecutionService.java:260/350、InspectionService.java:104、TaskEngine.java:260/284，truth 未斷言）；舊整合套件 5 筆既有壞測試（outbox 順序、QR token 唯一約束、SOP 勾記，歸因已確認非本輪造成）；`PoolItem.urgent` 舊布林與 `isUrgent` 並存待統一；`tms.kafka.topic.pms-status` 未在 application.properties 宣告

## 5. 關鍵決策

- **App 放 repo 內 `app/`，不開新 repo** — 使用者拍板；truth 共用、Appium runner 讀 `../specs` 最順
- **Appium＋Cucumber-JVM 當 App BDD runner** — commit `98d0574` 拍板，理由在 research.md 決策 5；排除了 XCUITest 原生（DSL 不共用）
- **四筆產品裁決＋三筆範圍裁決** — 詳見 memory `tms-prototype-conflict-rulings-2026-11-03`；最大的一筆是廢 due_at／L1-L3 升級鏈改兩段式未接提醒（已全面落地）
- **tenant_config 門檻用單鍵 `unclaimed_reminder_minutes` 存四值 jsonb** — 既有慣例 value 為 jsonb 結構依 key 而異；排除了 4 個獨立 key 的寫法

## 6. 陷阱 / Gotchas

- **跑測試**：`JAVA_HOME=$(/usr/libexec/java_home -v 25)`（pom 要 Java 25，本機預設 17）＋ `TESTCONTAINERS_RYUK_DISABLED=true`，前景阻塞跑；subagent 用背景執行＋等通知會卡死（發生過兩次）
- **truth suite 聚焦跑法**：`-Dtest=RunTruthFeaturesTest -Dcucumber.features=../specs/truth/features/backend/<模組> -Dcucumber.glue=com.athena.tms.bdd`
- **tenant_config 值有雙重 JSON 編碼怪癖**：讀取要用 `value #>> '{}'` 解包（TenantConfigAdminController 用 `to_jsonb(?::text)` 寫入）——UnclaimedReminderScanJob 有現成範例
- **舊整合套件**（tms/src/test/resources/features/01-14、glue integration.stepdefs）獨立於 truth suite、會被裸 `mvn test` 一起跑；它有 5 筆先前就壞的非綠，別當成自己的回歸
- **`disable-model-invocation` 的專案 skill**：使用者點名後由 subagent 讀 SKILL.md 照 SOP 執行；派工 prompt 不要叫 subagent 呼叫 Skill 工具（會被擋並可能拒絕任務）
- **原型 Main.dc.html 非獨立檔**：要 `prototype/support.js`＋`vendor/react*.js` 才渲染得出來，且需 http server
- **「一次只做一間」feature 有偶發 deadlock flaky**，重跑即過，別追

## 7. 環境狀態

- **分支**: feature/gherkin（本 handoff commit 後與 origin 同步）
- **未 commit 變更**: 無（c9b48a1 已收口）
- **相關 PR/MR**: 未開
- **部署/驗證**: truth suite 117/117 全綠（RunTruthFeaturesTest）；無 CI
- **阻塞**: 無；App 開發需本機 Xcode＋simulator＋Appium 環境（尚未確認已安裝）

## 8. 本任務特殊默契

- athena flow 的管線順序：/dsl-refine（truth owner）→ /api-plan → /data-plan → /tasks → /implement；/implement 內 Phase 2（測試對齊）review 零 issue 才解鎖 GREEN，GREEN/REFACTOR 委派 /bdd
- 每個 stage 完成都做 fresh-agent 驗收（驗證不自驗）；commit 要問過使用者
- tasks.md 是唯一執行控制平面，完成一筆立刻回寫 [X]
- App 的 Appium 斷言走 accessibility id（ui-plan.md 的 dot+kebab 約定），不走全文比對（稱謂等文案仍以「您」為準）
