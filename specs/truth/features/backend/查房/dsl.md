# 查房 DSL

這個模組的專屬句型。跨模組共用句型見 [`../dsl.md`](../dsl.md)。

| 查房結果用語 | 權威值 |
| --- | --- |
| 通過 | inspection `passed` |
| 免查 | inspection `waived` |
| 退回重做 | inspection `failed` |

## Given 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `飯店設定免查房的打掃種類為 "{種類}"` | `種類`：字串；續住整理或「無」。 | 不支援 | 無 | `怎麼做`：以主管身分呼叫 `updateTenantConfig`（`PUT /tenant-configs/{id}`），key 為 `inspection_waived_cleaning_types`。`權威狀態落地`：`tenant_config` 的該鍵已更新，並留下設定變更事件。 |
| `"{員工}" 在免查房種類含 "{種類}" 時開始了 "{任務}" 的續住整理` | `員工`：字串。<br>`種類`：字串。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：先設定免查房種類含該種類，建立該員工名下 accepted 的續住整理任務，再呼叫 `tasksStart`。`權威狀態落地`：任務為 doing，`tasks.inspection_waived`=true。 |
| `"{員工}" 在免查房種類為 "{種類}" 時開始了 "{任務}" 的續住整理` | `員工`：字串。<br>`種類`：字串；「無」代表沒有任何種類免查。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：先把免查房種類設為指定值，建立 accepted 的續住整理任務，再呼叫 `tasksStart`。`權威狀態落地`：任務為 doing，`tasks.inspection_waived` 依當時設定決定。 |
| `今日的打掃查房結果如下：` | 無 | 支援：`房號`（必填，字串）、`內容`（必填，字串）、`負責人`（必填，字串）、`查房結果`（必填，字串；通過、免查、退回重做） | 無 | `怎麼做`：逐列建立今日打掃任務，並寫入產生該查房結果的事件歷程。通過與免查是 inspected，退回重做是 accepted 且 redo 1。`權威狀態落地`：`tasks.inspection` 與 `task_events` 一致。 |

## When 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `"{主管}" 抽查 "{任務}"，判定 "{項目}" 不合格` | `主管`：字串。<br>`任務`：字串；inspection 為 waived 的房號。<br>`項目`：字串；SOP 項目。 | 不支援 | 無 | `怎麼做`：以主管身分呼叫 `createSpotCheck`（`POST /inspections/spot-checks`），該項 pass=false。`權威狀態落地`：任務轉為 accepted，assignee 不變，`redo_count` 加 1，`worked_sec` 歸零、`task_sop_checks` 清空，`inspection` 改為 failed，`rooms.hk_state`=dirty；寫入 `inspection_failed` 事件與負責人的 push outbox。`回寫`：回應。 |
| `"{主管}" 查看今日查房合格率` | `主管`：字串。 | 不支援 | 無 | `怎麼做`：以主管身分呼叫 `getInspectionPassRate`（`GET /inspections/pass-rate`）。`權威狀態落地`：查詢不寫資料。`回寫`：合格率的分子與分母。 |

## Then 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `"{任務}" 的查房結果為 "{結果}"` | `任務`：字串；房號。<br>`結果`：字串；通過、免查、退回重做。 | 不支援 | 無 | `必查`：`權威狀態`：`tasks.inspection` 符合對照表。`再讀確認`：任務詳情回應一致。 |
| `"{任務}" 回寫 PMS 的房況為 "{房況}"` | `任務`：字串；房號。<br>`房況`：字串；如 住客房・已整理（OC）、可售。 | 不支援 | 無 | `必查`：`權威狀態`：`outbox` 有一筆 kind `pms_status`、房號相符、房況值相符的資料。`跨視角`：模擬 PMS adapter 收到同一個房況值。`不該發生`：同一事件沒有另一筆不同房況的回寫。 |
| `"{任務}" 的重做次數為 {次數}` | `任務`：字串；房號。<br>`次數`：整數。 | 不支援 | 無 | `必查`：`權威狀態`：`tasks.redo_count` 等於次數。 |
| `"{任務}" 沒有任何 SOP 勾記` | `任務`：字串；房號。 | 不支援 | 無 | `必查`：`權威狀態`：`task_sop_checks` 中該任務沒有 checked=true 的項目。 |
| `今日查房合格率為 "{比例}"` | `比例`：字串；格式為「分子 / 分母」。 | 不支援 | 無 | `必查`：`呈現結果`：`PassRate.passed` 與 passed＋failed 相符。`權威狀態`：以 `tasks.inspection` 重算，passed 是分子，passed 加 failed 是分母，waived 不計。 |
