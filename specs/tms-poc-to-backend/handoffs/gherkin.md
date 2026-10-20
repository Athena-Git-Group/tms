# Handoff: gherkin

產 13 支 zh-TW .feature（邊界優先、判定值溯源 spec §5.2 與 E-01~E-18），覆蓋全部 FR/GR 與 SC-002~008；2 個待釐清缺口以 @待釐清 標記。

## Stage
gherkin（規格層，backend 變體）

## Inputs Used
- specs/tms-poc-to-backend/specify/spec.md（STATUS: READY；範例資料與邊界為判定值來源）
- specs/tms-poc-to-backend/api/openapi.yaml、db_table/erm.dbml（步驟對齊）

## Artifacts Produced
- specs/tms-poc-to-backend/gherkin/01-guest-requests.feature
- specs/tms-poc-to-backend/gherkin/02-staff-pool.feature
- specs/tms-poc-to-backend/gherkin/03-desk-triage.feature
- specs/tms-poc-to-backend/gherkin/04-inspection.feature
- specs/tms-poc-to-backend/gherkin/05-identity-tenancy.feature
- specs/tms-poc-to-backend/gherkin/06-qr-lifecycle.feature
- specs/tms-poc-to-backend/gherkin/07-scheduling.feature
- specs/tms-poc-to-backend/gherkin/08-leave-interrupt.feature
- specs/tms-poc-to-backend/gherkin/09-occupancy.feature
- specs/tms-poc-to-backend/gherkin/10-owner-dashboard.feature
- specs/tms-poc-to-backend/gherkin/11-collaboration.feature
- specs/tms-poc-to-backend/gherkin/12-kb-settings.feature
- specs/tms-poc-to-backend/gherkin/13-core-backbone.feature

## Gate Verdict
PASS — 全 FR/GR 有對應 Rule；邊界場景排前；判定值可溯源

## 覆蓋矩陣（規則 × feature × 來源）
| spec 條目 | feature（Rule） | 判定值來源 |
|---|---|---|
| FR-001~008 | 01（各 Rule）＋E-01/03/04/05/17 | spec §5.2 rooms/reservations、E 條目 |
| FR-010~014 | 02＋E-06/16 | §5.2 staff/tasks |
| FR-020~022 | 03 | 故事 3 驗收情境 |
| FR-030~032 | 04＋E-07；SC-004 | 查房項目（POC 承接）、E-07 |
| FR-040~043 | 05＋E-12；SC-005 | 故事 5 |
| FR-050~052 | 06＋E-02/18 | 故事 6、A7 |
| FR-060~062、FR-100 | 07＋E-08/09 | §5.2 cleaning_plans、E-08/09 |
| FR-070~071 | 08 | 故事 8 |
| FR-080~081 | 09＋E-10；SC-008 | §5.2 reservations、A9 |
| FR-090~091 | 10＋E-15；SC-007 | 故事 10、A1/A11 |
| FR-110/120/130 | 11 | 故事 12–14、A8 |
| FR-140/150 | 12 | 故事 15–16、A6 |
| GR-001~006 | 13（各 Rule）＋02/04 補強；SC-002/003/006 | GR 條目、E-11/13/14 |

## 未覆蓋項（含理由）
- SC-001（掃碼到送出 ≤3 步）：前端互動步數，後端 API 場景無法驗，留給前端驗收
- GR-NFR-003 部署形狀、GR-NFR-004 OpenTelemetry：部署/觀測性非 .feature 可斷言，驗收屬 plan/verify 階段
- GR-NFR-005「什麼都可調」：僅以 12 的 request_menu 場景抽驗，未逐鍵窮舉

## 回饋訊號（待釐清缺口——本 run 不回退，答案經 clarify/answers.md 進下一輪）
1. [10-owner-dashboard @待釐清] 儀表板「平均回應分鐘」的起訖點是什麼？建單到被認領，還是建單到完成？（影響投影 worker 的計算公式與老闆看到的數字語意）
2. [10-owner-dashboard @待釐清] 「可賣房數」怎麼算？hk_state=inspected 的房間數，還是要再排除住人中（occupied）的房？（影響同一張卡片的語意）

## 建議驗證機制
backend：Cucumber-JVM（Spring Boot 測試整合）或 red/green agent 以 API＋DB 斷言執行；frontend_verify 未指定、本 track 無前端 .feature。

## Next Recommended Stage
（pack 內終點）→ 編排器收尾
