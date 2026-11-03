# 班末掃描 DSL

這個模組的專屬句型。跨模組共用句型見 [`../dsl.md`](../dsl.md)。

## When 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `"{員工}" 的班末掃描執行` | `員工`：字串；班次到期的員工。 | 不支援 | 無 | `怎麼做`：在目前 `Clock`（班次結束時刻）直接觸發該員工班次的 Quartz 班末掃描 job，並等 job 完成。`權威狀態落地`：accepted 與本人延後的任務轉為 pool，`pool_source`=shift_end_return，寫入 `returned_to_pool` 事件；doing 與 interrupted 不變；寫入員工與主管的 push outbox。`回寫`：job 執行結果。 |
| `"{主管}" 以交代 "{交代}" 把 "{任務}" 退回待認領` | `主管`：字串。<br>`交代`：字串；接手進度交代。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：以主管身分呼叫 `tasksRequeue`（`POST /tasks/{id}/requeue`），附交代。`權威狀態落地`：任務轉為 pool，清空 assignee，`priority_notify`=true、`pool_source`=manager_requeue，交代寫入 `requeue_note`，並寫入 `returned_to_pool` 事件與同樓層房務員的 push outbox。`回寫`：回應。 |

## Then 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `房務待認領包含以下任務：` | 無 | 支援：`房號`（必填，字串）、`內容`（選填，字串）、`來源`（選填，字串）、`標記`（選填，字串）、`交代`（選填，字串） | 無 | `必查`：`權威狀態`：每一列都有 dept `housekeeping`、狀態 `pool` 的任務，並且有給的欄位都相符。`跨視角`：房務員呼叫 `listDeptPool`（`GET /pool`）時看得到這些單。 |
| `"{員工}" 沒有收到關於 "{任務}" 的推播` | `員工`：字串。<br>`任務`：字串；房號。 | 不支援 | 無 | `必查`：`不該發生`：`outbox` 沒有任何收件人是該員工、內容帶該任務 id 或該房號的 kind `push` 資料列。 |
