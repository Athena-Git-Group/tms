# 規格品質檢查清單：pms-sse-integration（PMS 出站訂閱整合）

**建立日期**: 2026-10-21
**Spec 路徑**: `specs/pms-sse-integration/specify/spec.md`

## 內容完整性

- [x] `spec-structure.md` 列出的 8 個必含節都存在且非空殼
- [x] 功能主題、範圍與主要流程已表達清楚
- [x] 沒有把實作技術寫成需求 — **不適用（附理由）**：本案是 brownfield 既有 codebase
  的整合替換需求，需求原文（point report）即以 repo 符號（OutboxWriter、
  TaskEventsSseConsumer…）敘述範圍；FR 保留落點符號是忠實承載，非偷渡實作。
  對外契約（payload、cursor、錯誤碼）本身技術中立。
- [x] 邊界情況已涵蓋主要高風險情境，且每條都帶具體觸發值（節 2 共 15 條）
- [x] 關鍵實體與成功標準已補齊

## 使用者故事與需求歸戶

- [x] 故事依商業價值與交付依賴排序（US1 認證先行 → US2 基載 → US3 即時 → US4 退場同釋出），每個都寫了優先級原因
- [x] 每個故事都有「獨立驗證方式」
- [x] 每個故事 ≥ 1 條驗收情境（各 2–3 條）
- [x] 可歸屬單一故事的 FR / NFR 已掛在故事底下（FR-1xx/2xx/3xx/4xx）
- [x] 全域需求只剩跨故事者（payload 版本策略、at-least-once 語意、錯誤格式、rate limiting 豁免）
- [x] 無重複編號；故事區與全域區不重複列出

## 承載完整性

- [x] 「資料維度與範例資料」段存在，逐項承載 clarified.md 的資料維度（IntegrationApiKey 十維度全列；TaskEvent 標明僅新增讀取樣式；RoomStatusEvent 欄位表逐欄）
- [x] 核心實體範例資料 ≥ 3 筆且與上游逐筆一致（IntegrationApiKey ×3、RoomStatusEvent ×3、白名單對映 ×3——值未改）
- [x] 邊界情況觸發值與上游一致（clarified 12 條 → spec 15 條：拆分 8/9 的複合條並補退場驗證第 15 條，值逐字搬）
- [x] 承載覆蓋帳本涵蓋 clarified.md 每個標題段 / 規則 / 範例資料
- [x] 帳本無「未處理」「待補」

## 缺口與澄清策略

- [x] 無高影響缺口升級（clarify 已以 Assumption A1–A7 收斂全部 score 缺口）
- [x] 本輪升級題數 = 0（未追寫 questions.md）
- [x] 低風險未定細節已入「假設」段（A1–A8）
- [x] 無殘留 `[NEEDS CLARIFICATION]` 標記 → 不阻塞結構層

## 可驗證性與成功標準

- [x] 驗收情境足以驗證主要成功路徑（認證、拉取、SSE 即時＋回放、退場）
- [x] SC-001–005 可量測、技術中立
- [x] 假設只表達前提與邊界（A1–A8 皆為工程預設或範圍宣告）
- [x] 需求、邊界、實體、資料維度與成功標準彼此一致（去重鍵 (task_id,seq) 於 FR-002/304/邊界13/SC-003 同值；3 秒遲滯於 FR-203/邊界12/SC-001 同值）

## 問題與修正紀錄

- 無

## Ready 判定

- [x] 已可作為結構層的唯一需求真源（→ `spec.md` 首行 `STATUS: READY`）
- [ ] 仍需先補高影響需求缺口 — 否

**備註**: 全部 Assumption 已同步列入 spec.md 節 7，將由編排器轉錄至最終 handoff Risks。
