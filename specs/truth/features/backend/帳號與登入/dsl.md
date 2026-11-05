# 帳號與登入 DSL

這個模組的專屬句型。跨模組共用句型見 [`../dsl.md`](../dsl.md)。

員工用「員工編號」＋密碼登入（`員工編號` 對應 `staff_accounts.login_id`，畫面與推播文案一律用「員工編號」，不用「帳號」）。本模組驗證登入本身的成功、失敗與鎖定；登入成功後其餘模組一律沿用介面根共用 DSL 的 JWT bypass 建置測試資料，不必每個案例都重新走一次登入。

**已知的 truth 領先現況**：`staffAccountsLogin` 目前的 requestBody／response 直接沿用「建立員工帳號」的 `staff_accountsRequest`／`staff_accounts` schema（欄位是 `loginId`／`passwordHash`，且 `passwordHash` 的說明是「bcrypt/argon2，絕不存明文」），沒有為登入場景定義專屬的請求／回應形狀，也沒有失敗次數、鎖定到期、強制改密等欄位。本模組的 When／Then 先確立登入應該成立的業務語意，實作語意欄逐項標出目前契約缺的欄位，回交 `/api-plan` 補 `LoginRequest`（`loginId`、`password` 明文送出，後端雜湊比對）與對應回應形狀。

## Given 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `"{員工}" 的員工編號密碼為 "{員工編號}" / "{密碼}"` | `員工`：字串。<br>`員工編號`：字串。<br>`密碼`：字串。 | 不支援 | 無 | `怎麼做`：把該員工帳號的 `login_id` 與密碼雜湊設定為指定值。`權威狀態落地`：`staff_accounts.login_id`、`password_hash` 相符。 |
| `"{員工}" 的帳號是臨時密碼` | `員工`：字串。 | 不支援 | 無 | `怎麼做`：把該員工帳號標記為需要強制改密。`權威狀態落地`：待 `/api-plan`／`/data-plan` 補 `staff_accounts.must_change_password`（目前沒有這個欄位，`createdAt`／`updatedAt` 也無法推導「這組密碼是臨時發的」）；本句型先假設該欄位存在並為 true。 |
| `"{員工}" 已連續輸入錯誤密碼 {次數} 次` | `員工`：字串。<br>`次數`：整數。 | 不支援 | 無 | `怎麼做`：用錯誤密碼呼叫登入 {次數} 次。`權威狀態落地`：待 `/api-plan`／`/data-plan` 補失敗次數欄位（目前沒有）；本句型先假設系統已累計該次數。 |

## When 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `"{員工}" 用員工編號 "{員工編號}" 密碼 "{密碼}" 登入` | `員工`：字串；僅供案例辨識，不是請求欄位。<br>`員工編號`：字串。<br>`密碼`：字串；明文。 | 不支援 | 無 | `怎麼做`：呼叫 `staffAccountsLogin`（`POST /staff-accounts/login`），帶 `{loginId: 員工編號, password: 密碼}`。待 `/api-plan`：目前 requestBody 是 `staff_accountsRequest`，沒有獨立的 `password` 明文欄位（只有 `passwordHash`，語意是「已經雜湊好的密碼」，登入場景不該由前端自己雜湊）——本句型假設 `/api-plan` 會補一個登入專屬的 `LoginRequest{loginId, password}`。`權威狀態落地`：成功時不改動員工資料；失敗時累計失敗次數（待補欄位）。`回寫`：回應。 |

## Then 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `登入成功，取得 "{員工}" 的身分與可用憑證` | `員工`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：回應 200，身分的姓名、部門與該員工相符，並附帶可供後續請求使用的憑證。待 `/api-plan`：目前 response 是 `staff_accounts`，沒有 token／憑證欄位，與 techstack 已拍板的 JWT 機制銜接不上——假設回應會補 `token`／`expiresAt`。`權威狀態`：沒有建立或改動任何 `staff_accounts` 資料。 |
| `登入失敗，提示 "{提示}"` | `提示`：字串；固定文案 `員工編號或密碼不正確`。 | 不支援 | 無 | `必查`：`呈現結果`：回應 4xx（待 `/api-plan` 確認用 401，目前契約只定義 400／409），body 的提示文字等於該文案。`權威狀態`：沒有建立任何憑證；失敗次數累計（待補欄位）。 |
| `"{員工}" 的帳號已鎖定 {分鐘} 分鐘` | `員工`：字串。<br>`分鐘`：整數；固定 15。 | 不支援 | 無 | `必查`：`權威狀態`：待 `/api-plan`／`/data-plan` 補鎖定到期時間欄位；鎖定期間內即使密碼正確，登入仍回 4xx（建議 429，與既有 `TooManyRequests` 回應沿用同一語意）。 |
| `"{員工}" 的帳號已停用，登入被拒` | `員工`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：回應 4xx。`權威狀態`：`staff_accounts.active`=false 的帳號登入一律被拒，不因密碼正確而放行；對齊介面根共用 DSL「停用的員工不出現在挑人名單、不收推播」的既有約定，一併擴充為「也不能登入」。 |
| `"{員工}" 必須先設定新密碼才能繼續` | `員工`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：登入回應標示需要強制改密（待補 `mustChangePassword` 欄位）。`不該發生`：在完成改密前，用這組憑證呼叫其他需要身分的 API 一律被拒。 |
