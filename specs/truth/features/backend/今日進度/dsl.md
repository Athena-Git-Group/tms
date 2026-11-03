# 今日進度 DSL

這個模組的專屬句型。跨模組共用句型見 [`../dsl.md`](../dsl.md)。

## When 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `"{員工}" 查看今日進度` | `員工`：字串。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `getMyToday`（`GET /me/today`）。`權威狀態落地`：查詢不寫資料。`回寫`：回應。 |

## Then 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `今日進度的 "{項目}" 為 "{值}"` | `項目`：字串；打掃完成／總數、查房通過、今天完成單數、臨時需求完成、預估剩餘工時。<br>`值`：字串；顯示值，例如 `2 / 7`、`135 分`。 | 不支援 | 無 | `必查`：`呈現結果`：`MyToday` 對應欄位相符：打掃完成／總數＝`cleaningDone` / `cleaningTotal`，查房通過＝`inspectedCount`（契約待新增欄位，見 truth-delta 程式碼待改清單；計算口徑為 `inspection`=passed 的今日任務數），今天完成單數＝`completedCount`（首頁四格不顯示，供完成慶祝頁「第 N 單」使用），臨時需求完成＝`adHocCompleted`，預估剩餘工時＝`remainingMinutes` 分。`權威狀態`：用 `tasks` 以 FR-028、FR-029 的公式重算，結果一致，已做分鐘以目前 `Clock` 計算。 |
| `待開始的單推薦順序為 {房號清單}` | `房號清單`：字串；以 `、` 分隔的房號。 | 不支援 | 無 | `必查`：`呈現結果`：`nextTasks[].roomNo` 的順序剛好等於清單，而且都是 accepted。 |
| `今日進度包含連續零退回天數` | 無 | 不支援 | 無 | `必查`：`呈現結果`：`zeroReturnStreakDays` 是 0 以上的整數。計算定義待 clarify，目前不驗證數值。 |
