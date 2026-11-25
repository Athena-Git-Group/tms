# Rule 1 - Feature Shared Must Read 必須從 plan 介面回追 planner 產物

- Level: `MUST`
- 寫每個 Feature phase 時，不得只複製 feature、同模組 `dsl.md` 與 `/dsl-refine` 列。必須從該列回追 `plan.md` 的系統介面，把**該介面 planner 已經寫下、且約束這個 feature** 的產物列入 `Shared Must Read`。
- 回追順序固定，不得改成掃全部 Core Inputs 或只憑介面名稱猜檔案：
  1. 看 `/dsl-refine` 列的 Truth 路徑，取出介面與模組（例如 `frontend/工位終端`、`backend/工位執行`）。
  2. 到 `plan.md`「系統介面的盤點」找涵蓋這條路徑的系統介面。
  3. 看該介面在 Wave 裡交給哪個 planner（`/ui-plan`、`/api-plan`、`/data-plan`；techstack 對 `/technical-research`）。
  4. 把那個 planner **現在已產出、且約束這個模組** 的檔列入 `Shared Must Read`，並寫到具體部位。
  5. 只綁這個模組。不得把全部 `ui/*.html`、全部 api group 或整份 `data-model.dbml` 塞進每一個 Feature phase。
- truth 產物用同份 `truth-delta.md` 的 `/api-plan`、`/data-plan`、`/technical-research` 列，對到這個模組。
- plan-side 產物（路徑不在 `specs/truth/**`）用該 planner 自己的 plan 點名，例如 `ui/ui-plan.md` 該畫面的「對應雛形檔案」。
- 不得為個別技術（MUI、CSS、雛形檔名）另開規則。Foundational 仍可空白；這條只約束 Feature phase。

## Good Example

- 這個例子是好的，因為它從 `/dsl-refine` 列回追到 UI 介面，只綁該畫面的 plan 與雛形。

```md
truth-delta 有 ADD `specs/truth/features/frontend/工位終端/登入操作與動態表單.feature`。
plan.md 對到 `現場三畫面 UI 介面`，planner 是 `/ui-plan`。
ui/ui-plan.md 寫工位終端對應 `ui/index.html`。

**Shared Must Read**:
- `specs/truth/features/frontend/工位終端/登入操作與動態表單.feature` -> Feature 全文
- `specs/truth/features/frontend/工位終端/dsl.md` -> 本檔 Given／When／Then
- `truth-delta.md` -> 該 /dsl-refine 列
- `ui/ui-plan.md` -> 工位終端的視覺方向、畫面與流程
- `ui/index.html` -> 工位終端雛形全文
```

## Bad Example

- 這個例子是壞的，因為 Core Inputs 有 `ui/**` 卻沒有回追，GREEN 只綁行為三件套。

```md
**Shared Must Read**:
- feature / dsl / truth-delta
（plan.md 有 UI 介面，ui/index.html 也在，但不列）
```

# Rule 2 - 回追點名的產物不存在時必須停止並回交該 planner

- Level: `MUST`
- 回追點名的檔不存在、`plan.md` 找不到涵蓋該路徑的系統介面、或 planner plan 沒有對應列：停止這個介面的 Feature phase，回交該 planner。
- 不得把 Goal 改成「畫面可空白」或「先不綁產物」後繼續輸出 `[BDD-GREEN]`。
- Foundational 的「畫面可空白」不得拿來覆蓋 Feature phase。
- 回交對象依缺失產物決定：缺雛形或 `ui-plan.md` 回交 `/ui-plan`；缺契約回交 `/api-plan`；缺資料模型回交 `/data-plan`；缺 techstack 回交 `/technical-research`。

## Good Example

- 這個例子是好的，因為點名檔不在就停，不改寫完成條件。

```md
ui/ui-plan.md 寫工位終端對應 `ui/index.html`，但該檔不存在。
停止 frontend/工位終端 的 Feature phases，回交 /ui-plan。
不輸出 T480 [BDD-GREEN]。
```

## Bad Example

- 這個例子是壞的，因為它用 Foundational 的空白條件讓 GREEN 過關。

```md
雛形還沒產出。
把 Goal 改成「畫面可空白」，仍寫 T480 [BDD-GREEN]。
```

# Rule 3 - plan-side 產物加 quality gate；truth 產物以 GREEN 為閘門

- Level: `MUST`
- 回追到的產物若在 `specs/truth/**`：該 Feature 的 `Test Scope` 全綠就是閘門，不再加題。
- 回追到的產物若不在 `specs/truth/**`：在**最後一個**用到它的 Feature phase 之後，剛好加一題 `T### subagent review (<產物名> quality gate)`。同一產物家族本輪只加一題，不按 feature 各加一題。
- 這題的 `Read` 是本輪用到的該家族 plan-side 產物，以及已實作的對應產品面。
- 通過條件是產品對齊該 plan-side 產物。`Test Scope` 全綠不能代替。這題不改 truth、不加 feature、不重跑 GREEN 當通過條件。
- 本輪沒有 plan-side 產物，整題省略。不得發明新 marker。

## Good Example

- 這個例子是好的，因為三個 frontend phase 共用雛形家族，只在最後加一題 gate；後端只綁契約與資料模型，不加 ui gate。

```md
（frontend/工位終端、狀態板與追溯、系統設定 之後）

## Phase 4R: UI quality gate

**Read**:
- `ui/ui-plan.md` -> 視覺方向
- `ui/index.html`、`ui/status-board.html`、`ui/settings.html`
- `frontend/src/pages/`

- [ ] T492 subagent review (ui quality gate)

後端 `工位執行` 回追到 `/api-plan`、`/data-plan` 時，只加對應 contract group 與 data-model 列，不加 ui quality gate。
```

## Bad Example

- 這個例子是壞的，因為它用 Gherkin 全綠代替雛形對齊，或為每個 frontend feature 各加一題 gate。

```md
T480–T491 全綠，所以不寫 ui quality gate。

或：

T481 後加一題、T483 後再加一題、T491 後再加一題。
```
