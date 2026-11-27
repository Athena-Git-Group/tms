# TMS 專案守則（每個 session 必讀）

本專案有兩類產出：①設計文件 ②`app/mobile/` 的 Flutter App 程式碼（含測試）。
文件產出 = `docs/*.html`（本機快照）＋線上 Artifact
（連結在 INDEX.md，同一 URL 可更新）。改完本機文件要同步兩件事：
①更新對應的線上 Artifact ②更新 INDEX.md 的該列。

## 改到 BDD 時要照專案 skill 做（鐵則）

改到 `*.feature`、`dsl.md`、step 定義、`specs/plans/**` 或 `specs/truth/**` 都算。
開工前先讀 `.agents/skills/` 對應的 `SKILL.md`（連同它指到的 templates／references），
照它的步驟與寫入範圍做，不要自己另起一套格式：

- 新需求或改需求：依序 `specify` → `spec-by-example` → `system-analysis` →
  `api-plan`／`data-plan`（用 `truth-delta` 記錄）→ `dsl-refine` → `tasks` → `implement`。
- 只改 gherkin 或 `dsl.md`：`gherkin-and-dsl`，動到 `specs/truth/features/**` 再加 `dsl-refine`。
- 寫 step 定義、讓劇本變綠：`bdd`（紅 → 綠 → 重構）。
- 需求有缺漏或沒拍板：`clarify` 問人，不要自己腦補。

這些 skill 設成不會自動觸發：人可以打 `/<skill名>` 叫用；agent 要自己用 Read 讀 `SKILL.md`。
派 subagent 做上述工作時，派工 prompt 要點名要讀哪幾個 skill。

## docs/ 文件的寫法（鐵則）

**讀者永遠是第一次讀的人**（含 PM／老闆，不只 RD）。
結構範本：`docs/14-handover/`。

### 結構
- 章節標題用**問題句**（「怎麼保證推播一定送達、而且不重複？」），不用名詞片語。
- 設計題走「**問題 → 壞做法 → 好做法 → 權衡**」再給結論，不要直接倒結論。
- 用散文段落黏合，資訊不要全壓進表格與 bullet。
- 需求寫法：**角色別使用者故事**（每條「可以…」一句話）＋**系統規則**＋**白話 NFR**。
  不用 FR/NFR 編號表格。

### 用語
- 行話一律換白話；必要術語第一次重度使用處給一句白話定義。
- enum 與縮寫一律全寫（`housekeeping`，不是 `hk`），且與資料欄位共用同一套值。
- 風格修改要**全文一次掃完**——標題、同節表格、圖上標籤全部跟著改，不做半套。

### 嚴禁入文
- 修訂註記（「2026-xx-xx 修訂」「原為…」）、版本沿革、對 INDEX.md 的 § 交叉引用——
  這些**只記在 INDEX.md**，不進文件本體。文件永遠寫成「現在就是這樣」。
- 文件之間純指路可以（例：「資料表定義見 Flyway 遷移檔」）。

### 維護
- 同一份文件補丁超過 5 輪 → 下次改動**整份重寫**，不再補丁。
- 架構圖用手繪白板風（Excalidraw 風：圓角框服務、圓形資料庫、帶語意標籤的箭頭、
  實體欄位側註）。
