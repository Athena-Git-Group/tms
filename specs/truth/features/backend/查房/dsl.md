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
| `飯店設定免查房開關為 "{開關}"` | `開關`：字串；開、關。 | 不支援 | 無 | `怎麼做`：以主管身分呼叫 `updateTenantConfig`（`PUT /tenant-configs/{id}`），key 為 `inspection_waived_enabled`。免查房是全域開關，不分打掃種類。`權威狀態落地`：`tenant_config` 的該鍵已更新，並留下設定變更事件。 |
| `"{員工}" 在免查房開關開啟時開始了 "{任務}" 的續住整理` | `員工`：字串。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：先把免查房開關設為開，建立該員工名下 accepted 的續住整理任務，再呼叫 `tasksStart`。`權威狀態落地`：任務為 doing，`tasks.inspection_waived`=true（開工當下鎖定）。 |
| `"{員工}" 在免查房開關關閉時開始了 "{任務}" 的續住整理` | `員工`：字串。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：先把免查房開關設為關，建立 accepted 的續住整理任務，再呼叫 `tasksStart`。`權威狀態落地`：任務為 doing，`tasks.inspection_waived`=false（開工當下鎖定）。 |
| `飯店的「回寫房態」開關為 "{開關}"` | `開關`：字串；開、關。 | 不支援 | 無 | `怎麼做`：以主管身分更新 PMS 整合設定的房態回寫開關（`pms.writeback.room_status`）。`權威狀態落地`：`tenant_config` 的該鍵已更新。 |
| `飯店的 PMS 為手動匯入模式` | 無 | 不支援 | 無 | `怎麼做`：以主管身分把 PMS 整合設定的供應商設為手動匯入。`權威狀態落地`：`tenant_config` 的 PMS 供應商為 `manual`，沒有回寫對象。 |
| `"{員工}" 的角色沒有查房判定（inspect_submit）權限` | `員工`：字串。 | 不支援 | 無 | `怎麼做`：把該員工的角色權限中 `inspect_submit` 設為未勾選。`權威狀態落地`：角色權限已更新；該員工的查房判定會被拒絕，查房卡也不對其顯示。 |
| `今日的打掃查房結果如下：` | 無 | 支援：`房號`（必填，字串）、`內容`（必填，字串）、`負責人`（必填，字串）、`查房結果`（必填，字串；通過、免查、退回重做） | 無 | `怎麼做`：逐列建立今日打掃任務，並寫入產生該查房結果的事件歷程。通過與免查是 inspected，退回重做是 accepted 且 redo 1。`權威狀態落地`：`tasks.inspection` 與 `task_events` 一致。 |
| `飯店目前設定的查房項目如下：` | 無 | 支援：`查房項目`（必填，字串；顯示名稱 label） | 沒有宣告此句型時，沿用租戶出廠的查房項目全集。 | `怎麼做`：把租戶 `sop_items` 中 `applies_to`=inspection 的啟用項目整批換成表格列出的這些（依表格順序寫 `sort_order`，item key 由 label 產生）。與 `任務執行` 模組的「打掃 SOP 共有以下項目：」是兩套項目，不可互換。`權威狀態落地`：`sop_items` 中 applies_to=inspection 且 active=true 的集合剛好等於表格。`回寫`：label 對應的 item key。 |
| `飯店停用了全部查房項目` | 無 | 不支援 | 無 | `怎麼做`：把租戶 `sop_items` 中 `applies_to`=inspection 的項目全部設為 active=false。`權威狀態落地`：現行啟用的查房項目集合是空的。 |
| `飯店新增了查房項目 "{項目}"` | `項目`：字串；顯示名稱。 | 不支援 | 無 | `怎麼做`：在現行查房項目集合之後新增一個 `applies_to`=inspection、active=true 的 `sop_items` 列。用在「主管判完才被加項」的案例：Given 先建立五項，再用這一句加第六項，When 只送原本五項。`權威狀態落地`：現行啟用集合多了這一項。 |
| `"{主管}" 已判定 "{任務}" 查房全部項目通過` | `主管`：字串。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：以該主管身分呼叫 `createInspections`，items 為現行全集且都通過，當作 Arrange。`權威狀態落地`：任務已是 inspected、inspection=passed，`inspections` 已有一列，清房者的 push outbox 已有一筆「{房號} 查房通過」。用在「另一位主管先送出」的撞單案例。 |

## When 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `"{主管}" 抽查 "{任務}"，判定 "{項目}" 不合格` | `主管`：字串。<br>`任務`：字串；inspection 為 waived 的房號。<br>`項目`：字串；SOP 項目。 | 不支援 | 無 | `怎麼做`：以主管身分呼叫 `createSpotCheck`（`POST /inspections/spot-checks`），該項 pass=false。`權威狀態落地`：任務轉為 accepted，assignee 不變，`redo_count` 加 1，`worked_sec` 歸零、`task_sop_checks` 清空，`inspection` 改為 failed，`rooms.hk_state`=dirty；寫入 `inspection_failed` 事件與負責人的 push outbox。`回寫`：回應。 |
| `"{主管}" 查看今日查房合格率` | `主管`：字串。 | 不支援 | 無 | `怎麼做`：以主管身分呼叫 `getInspectionPassRate`（`GET /inspections/pass-rate`）。`權威狀態落地`：查詢不寫資料。`回寫`：合格率的分子與分母。 |
| `"{主管}" 查看查房者名單` | `主管`：字串。 | 不支援 | 無 | `怎麼做`：以主管身分查詢可選的查房者名單。`權威狀態落地`：查詢不寫資料。`回寫`：名單內容。 |
| `"{主管}" 判定 "{任務}" 查房如下：` | `主管`：字串。<br>`任務`：字串；待查房的房號。 | 支援：`查房項目`（必填，字串；顯示名稱）、`判定`（必填，字串；通過、不通過） | 無 | `怎麼做`：把每一列的顯示名稱解析成 item key，以該主管身分呼叫 `createInspections`，body `CreateInspectionRequest{taskId, items[{key, pass}]}`，items 剛好等於表格（表格少於或多於現行全集時照送，讓後端拒絕），帶新的 `Idempotency-Key`。`權威狀態落地`：items 等於現行全集且全部通過時同「判定查房全部項目通過」；有任一項不通過時任務轉為 accepted、`redo_count` 加 1、`worked_sec` 歸零、`task_sop_checks` 清空、`inspection`=failed、`rooms.hk_state`=dirty，寫入 `inspections` 一列與 `inspection_failed` 事件，推播清房者「{房號} 查房退回 · {項目}不合格」；清房者班次已結束或請假時改依既有換人規則轉為 pool、assignee 清空。items 與現行全集不符時回 422 `inspection_items_mismatch`，單不是待查房時回 409 `state_conflict`，兩者都不改任何權威資料、不推播。`回寫`：回應 `InspectionOutcome` 或 `Error`。 |
| `"{主管}" 以空的項目清單判定 "{任務}" 查房` | `主管`：字串。<br>`任務`：字串；房號。 | 不支援 | 無 | `怎麼做`：以該主管身分呼叫 `createInspections`，`items` 為空陣列，帶新的 `Idempotency-Key`。`權威狀態落地`：飯店沒有任何啟用的查房項目時視為全部通過，同「判定查房全部項目通過」；飯店有啟用項目時回 422 `inspection_items_mismatch`。`回寫`：回應。 |
| `"{主管}" 判定 "{任務}" 查房全部項目通過，同一請求共送出 {次數} 次` | `主管`：字串。<br>`任務`：字串；房號。<br>`次數`：整數；包含第一次。 | 不支援 | 無 | `怎麼做`：用同一個 `Idempotency-Key`、同一份 body 連續呼叫 `createInspections` 該次數。`權威狀態落地`：只有第一次真的判定，之後由 `processed_ops` 回放第一次的回應，`inspections` 只有一列、事件與推播只各一筆。`回寫`：每次回應。 |

## Then 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `"{任務}" 的查房結果為 "{結果}"` | `任務`：字串；房號。<br>`結果`：字串；通過、免查、退回重做。 | 不支援 | 無 | `必查`：`權威狀態`：`tasks.inspection` 符合對照表。`再讀確認`：任務詳情回應一致。 |
| `"{任務}" 回寫 PMS 的房況為 "{房況}"` | `任務`：字串；房號。<br>`房況`：字串；已整理（Inspected）。免查結案與查房通過都回寫 Inspected，不分住客房與可售。 | 不支援 | 無 | `必查`：`權威狀態`：`outbox` 有一筆 kind `pms_status`、房號相符、房況值相符的資料。`跨視角`：模擬 PMS adapter 收到同一個房況值。`不該發生`：同一事件沒有另一筆不同房況的回寫。 |
| `"{任務}" 沒有回寫 PMS，同步紀錄記一筆未回寫` | `任務`：字串；房號。 | 不支援 | 無 | `必查`：`權威狀態`：`outbox` 沒有該房這次結案的 kind `pms_status` 資料，同步紀錄有一筆標記未回寫（含原因：回寫開關關閉或手動匯入模式）。`跨視角`：模擬 PMS adapter 沒有收到任何房況值。 |
| `"{任務}" 的任務事件依序包含 {事件清單}` | `任務`：字串；房號。<br>`事件清單`：字串；以 `、` 分隔、加引號的事件 kind，如 `"inspection_passed"、"pms_writeback"`。 | 不支援 | 無 | `必查`：`權威狀態`：`task_events` 中這些事件都存在，且發生順序與清單一致。 |
| `查房者名單包含 "{員工}"` | `員工`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：名單中有該員工。`權威狀態`：該員工為在職。 |
| `查房者名單不包含 "{員工}"` | `員工`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：名單中沒有該員工。`權威狀態`：該員工為停用或待開通。 |
| `"{任務}" 的重做次數為 {次數}` | `任務`：字串；房號。<br>`次數`：整數。 | 不支援 | 無 | `必查`：`權威狀態`：`tasks.redo_count` 等於次數。 |
| `"{任務}" 沒有任何 SOP 勾記` | `任務`：字串；房號。 | 不支援 | 無 | `必查`：`權威狀態`：`task_sop_checks` 中該任務沒有 checked=true 的項目。 |
| `今日查房合格率為 "{比例}"` | `比例`：字串；格式為「分子 / 分母」。 | 不支援 | 無 | `必查`：`呈現結果`：`PassRate.passed` 與 passed＋failed 相符。`權威狀態`：以 `tasks.inspection` 重算，passed 是分子，passed 加 failed 是分母，waived 不計。 |
| `這次查房的結果為 "{結果}"，去向為 "{去向}"` | `結果`：字串；通過、退回重做。<br>`去向`：字串；結案、原清房者、待認領。對照 `InspectionOutcome`：通過＝result passed、退回重做＝failed；結案＝destination closed、原清房者＝cleaner、待認領＝pool。 | 不支援 | 無 | `必查`：`呈現結果`：上一次 `createInspections` 回應 201，`result` 與 `destination` 等於對照值；退回時 `failedItems` 剛好是被判不通過的項目（依飯店排序）、`cleanerName` 是原清房者；通過時 `failedItems` 為空。`權威狀態`：結案＝任務 inspected；原清房者＝任務 accepted 且 assignee 不變；待認領＝任務 pool 且 assignee 為空。三者必須靠 `destination` 分辨，不可改用 assignee 是不是 null 推斷。 |
| `這次查房被拒絕，原因是項目集合不符，回應帶現行項目 {項目清單}` | `項目清單`：字串；以 `、` 分隔的顯示名稱，依飯店排序。 | 不支援 | 無 | `必查`：`呈現結果`：回應 422，`Error.code`=inspection_items_mismatch，`context.currentItems` 的 label 順序剛好等於清單，`context.missing`／`unexpected` 分別是少送與多送的 key。`權威狀態`：任務仍為 done（待查房）、沒有新增 `inspections` 列與事件。`不該發生`：清房者沒有因這次送出收到任何推播。 |
| `"{任務}" 在房務待認領` | `任務`：字串；房號。 | 不支援 | 無 | `必查`：`權威狀態`：該任務 `state`=pool、dept housekeeping、assignee 為空，`pool_source` 為查房退回。`跨視角`：房務員呼叫 `listDeptPool`（`GET /pool`）時看得到這間。 |
| `"{任務}" 的查房紀錄只有 {筆數} 筆` | `任務`：字串；房號。<br>`筆數`：整數。 | 不支援 | 無 | `必查`：`權威狀態`：`inspections` 中該任務的列數等於筆數，`task_events` 中 `inspection_passed` 與 `inspection_failed` 合計也等於筆數。用於撞單與冪等重送，證明只被判過一次。 |
| `"{員工}" 沒有收到查房退回的推播` | `員工`：字串；清房者。 | 不支援 | 無 | `必查`：`權威狀態`：`outbox` 中該員工的 kind `push` 資料沒有任何一筆 `PushPayload.event`=inspection_failed。用於後到的退回判定被 409 擋下之後，證明清房者只收到先到那一次的結果。 |
