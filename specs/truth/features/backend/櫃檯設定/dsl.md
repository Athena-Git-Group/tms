# 櫃檯設定 DSL

這個模組的專屬句型。跨模組共用句型見 [`../dsl.md`](../dsl.md)。

櫃檯 Web 的設定頁有兩個飯店層級的開關，讀寫走 `GET /desk/settings` 與 `PATCH /desk/settings`，只有主管與值班主管可以呼叫，櫃檯人員回 403 `forbidden`。PATCH 的 body 只帶要改的開關，沒帶的維持原值；兩個都沒帶、或值不是是非值時回 422 `validation_failed`，設定不變。成功時回應帶兩個開關的現值。

**用語對照**：

| 用語 | API 欄位 | 權威值 |
| --- | --- | --- |
| 需要房務主管查房 | `inspectionRequired` | `tenant_config` 的 `inspection_waived_enabled`，開＝未設定或不是 true，關＝true。開工當下鎖定到 `tasks.inspection_waived`，所以只影響之後才開工的打掃單。 |
| 需要工務主管驗收 | `repairVerificationRequired` | `tenant_config` 的 `repair_verification_required`，開＝未設定或不是 false，關＝false。在工務完修當下讀取。 |

`開`、`關` 對照 API 的 `true`、`false`。

## Given 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `"{員工}" 已在櫃檯設定把「{設定}」設為 "{開關}"` | `員工`：字串；主管或值班主管。<br>`設定`：字串；需要房務主管查房、需要工務主管驗收。<br>`開關`：字串；開、關。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `PATCH /desk/settings`，body 只帶這一個開關，當作 Arrange，回應不是 200 時直接讓場景失敗。`權威狀態落地`：`tenant_config` 對應的鍵已寫入，`updated_by` 是該員工。這一句在 `查房` 與 `工務單` 模組共用。 |

## When 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `"{員工}" 讀取查房與驗收設定` | `員工`：字串。 | 不支援 | 無 | `怎麼做`：以該員工身分呼叫 `GET /desk/settings`。`權威狀態落地`：查詢不寫資料。`回寫`：回應。 |
| `"{員工}" 在櫃檯設定把「{設定}」設為 "{開關}"` | 同 Given。 | 不支援 | 無 | `怎麼做`：先記下 `tenant_config` 兩個鍵的現值，再以該員工身分呼叫 `PATCH /desk/settings`，body 只帶這一個開關。`回寫`：回應。 |
| `"{員工}" 送出沒有任何開關的查房與驗收設定` | `員工`：字串。 | 不支援 | 無 | `怎麼做`：先記下現值，再以該員工身分呼叫 `PATCH /desk/settings`，body 是 `{}`。`回寫`：回應。 |
| `"{員工}" 把查房與驗收設定的「{設定}」送成 "{值}"` | `員工`：字串。<br>`設定`：同上。<br>`值`：字串；原樣當 JSON 字串送出。 | 不支援 | 無 | `怎麼做`：先記下現值，再以該員工身分呼叫 `PATCH /desk/settings`，該欄位的值是這個字串。`回寫`：回應。 |

## Then 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `查房與驗收設定為：` | 無 | 支援：`設定`（必填，字串）、`開關`（必填，字串；開、關） | 無 | `必查`：`呈現結果`：上一次回應 200，兩個欄位等於表格。`再讀確認`：以主管身分再呼叫 `GET /desk/settings`，結果一致。`權威狀態`：`tenant_config` 兩個鍵依用語對照換算後等於表格。 |
| `「{設定}」最後由 "{員工}" 修改` | `設定`：字串。<br>`員工`：字串。 | 不支援 | 無 | `必查`：`權威狀態`：`tenant_config` 該鍵的 `updated_by` 是該員工。 |
| `這次設定操作被拒絕，錯誤碼為 "{錯誤碼}"` | `錯誤碼`：字串；forbidden、validation_failed。 | 不支援 | 無 | `必查`：`呈現結果`：上一次回應的狀態碼與錯誤碼相符（forbidden＝403、validation_failed＝422），body 有 `code` 與 `message`。 |
| `查房與驗收設定沒有被改動` | 無 | 不支援 | 無 | `必查`：`權威狀態`：`tenant_config` 兩個鍵與 When 之前記下的值相同（包含沒設定）。 |
