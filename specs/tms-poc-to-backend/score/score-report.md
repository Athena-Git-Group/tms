VERDICT: PASS-WITH-GAPS

# 可譯性評分（target: backend）

評分對象 = 需求語料整體：`source/requirement.md` ＋ 其明文指定的權威來源
`docs/11-requirements-architecture.html`（系統設計）、`docs/12-data-model.html`（資料模型）。
POC（`docs/13-poc/`）依需求原文屬「參考、非權威」，落差以 docs/11、12 為準。

## 評分表（rubric-backend，0–3）

| # | 維度 | 必備 | 分數 | 證據 |
|---|------|------|------|------|
| 1 | Entity Identifiability | ✅ | 2 | docs/12 對 17 張表「逐欄位：型別、必填、預設、值域」（12 §開頭）；但 12 §7 待補清單明列 sop_items、task_sop_checks、inspections、handovers＋handover_signs、lost_items、leaves、room_memory、stats_daily、phrase_counter 尚未逐欄位展開，僅 11 §5 有職責與關鍵欄位描述 |
| 2 | Relationship & State | — | 3 | 11 §3 任務狀態機九態含全部轉移與例外（排房發佈即 accepted、查房退回連動退池、班末掃描）；12 各表外鍵與複合外鍵 (tenant_id, xxx_id) 明確 |
| 3 | API Boundary | ✅ | 2 | 11 §2 對七條核心需求給出端點與請求/回應示例（含 409/429 語意）；但附錄 A 其餘端點（notices、lost-items、handovers、kb/ask、settings、capacity、QR 列印/輪替、occupancy/import）只有路徑名，輸入輸出未定 |
| 4 | Business Rule Completeness | ✅ | 2 | unhappy/edge 覆蓋度高（弱網冪等、空房掃碼 409 room_vacant、同房限流 429、退回給已下班者連動退池、重產對帳三分支、同鍵串行保序）；缺：交班單由誰/何時建立（11 只有 GET /staff/handovers 與簽收）、評分可否重評/時限、/staff/kb/ask 的 v1 行為深度 |
| 5 | Validation & Acceptance | — | 2 | 驗證多有定（CHECK、partial unique、EXCLUDE、If-Match、Idempotency-Key）；但各行為無形式化驗收判準（本 pack gherkin 層補），Excel 匯入檔格式/欄位未定 |
| 6 | Scope & Integration | ✅ | 2 | in/out 範圍明確（11 §1 兩份「不在範圍內」清單）、外部依賴三個（PMS adapter、APNS/FCM、LLM API）明確；缺：本次後端實作的 MVP 切分、Kafka/Quartz 整合深度（flow 已授權 spec 定案）、認證 token 形式與角色×動作矩陣逐項（11 §15 稱「定義在程式／設定，不在表裡」）、POC 頁面是否由後端 serve |
| 7 | Terminology Consistency | — | 2 | 權威語料內部高度一致（12 明言以它為準、06 作廢）；但 POC 用詞漂移：部門 maintenance/desk vs 權威 engineering/frontdesk、事件 claim/start/complete vs claimed/started/completed、POC 有 slaByType 與 flow 開關（requireInspection 等）而權威明言 SLA 延後且查房規則不可關 |

## 裁決

無必備維度 = 0；非全 3 → **PASS-WITH-GAPS**。

## 缺口清單（交給 clarify 當議程）

- G1（維度 1）：docs/12 §7 待補的 9–10 張表需逐欄位定案（spec 結構層可依 11 §5 職責推導，屬工程定案而非 PM 待答）
- G2（維度 3）：附錄 A 僅列名端點的輸入/輸出形狀（spec 契約層定案）
- G3（維度 4）：交班單建立觸發者與時機；評分規則細節（一次性？時限？）；/staff/kb/ask v1 行為範圍
- G4（維度 5）：Excel 匯入檔格式；各行為驗收判準（gherkin 層產出）
- G5（維度 6）：MVP 範圍切分與 phase 結構；Kafka 與 Quartz 整合深度；認證/授權落地形式（token 機制、角色×動作矩陣逐項）；POC 頁面是否由後端 serve
- G6（維度 7）：POC 與權威文件的用詞對照表（canonical enum 定案，POC 詞彙映射或棄用）
