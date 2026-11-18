# 帳號與登入 DSL

這個模組的專屬句型。跨模組共用句型見 [`../dsl.md`](../dsl.md)。

員工用「員工編號」＋密碼登入（`員工編號` 對應 `staff_accounts.login_id`，畫面與推播文案一律用「員工編號」，不用「帳號」）。本模組驗證登入本身的成功、失敗與鎖定；登入成功後其餘模組一律沿用介面根共用 DSL 的 JWT bypass 建置測試資料，不必每個案例都重新走一次登入。

櫃檯 Web 與員工 App 共用 `staffAccountsLogin`；櫃檯 Web 登入時請求多帶 `client`=`frontdesk_web`，後端在停用、鎖定、密碼比對之後再看角色，只放行櫃檯、主管、值班主管。飯店是彼此隔離的單位：登入請求可以帶飯店代碼 `hotelCode`（櫃檯 Web 一律帶），帶了就只在那家飯店找帳號，別家相同員工編號的帳號不受影響；員工 App 目前不帶，維持逐家比對。本模組另外驗設定新密碼（`changeOwnPassword`，`POST /staff-accounts/change-password`）與登入前查飯店名稱（`readPublicHotel`，`GET /public/hotels/{hotelCode}`）。

## Given 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `"{員工}" 的員工編號密碼為 "{員工編號}" / "{密碼}"` | `員工`：字串。<br>`員工編號`：字串。<br>`密碼`：字串。 | 不支援 | 無 | `怎麼做`：把該員工帳號的 `login_id` 與密碼雜湊設定為指定值。`權威狀態落地`：`staff_accounts.login_id`、`password_hash` 相符。 |
| `"{員工}" 的帳號是臨時密碼` | `員工`：字串。 | 不支援 | 無 | `怎麼做`：把該員工帳號標記為需要先設定新密碼；目前的密碼就是他的臨時密碼。`權威狀態落地`：`staff_accounts.must_change_password`=true。 |
| `"{員工}" 已連續輸入錯誤密碼 {次數} 次` | `員工`：字串。<br>`次數`：整數。 | 不支援 | 無 | `怎麼做`：用錯誤密碼呼叫登入 {次數} 次（不帶 `client`）。`權威狀態落地`：帳號已累計該次數的連續錯誤；滿 5 次時帳號鎖定 15 分鐘。 |
| `"{員工}" 已用臨時密碼從櫃檯畫面登入，還沒設定新密碼` | `員工`：字串。 | 不支援 | 無 | `怎麼做`：以該員工的員工編號與目前的臨時密碼呼叫 `staffAccountsLogin`（帶 `client`=`frontdesk_web`），確認回 200 且 `mustChangePassword`=true。`權威狀態落地`：`staff_accounts.must_change_password` 仍為 true。`回寫`：這次登入換得的憑證與臨時密碼。 |
| `"{員工}" 已把臨時密碼改成 "{新密碼}"` | `員工`：字串。<br>`新密碼`：字串。 | 不支援 | 無 | `怎麼做`：先用臨時密碼登入，再呼叫 `changeOwnPassword` 設成新密碼並確認成功。`權威狀態落地`：`password_hash` 對得上新密碼，`must_change_password`=false。 |
| `有一家飯店的識別代碼是 "{代碼}"，名稱是 "{名稱}"` | `代碼`：字串；`tenants.code`。<br>`名稱`：字串；`tenants.name`。 | 不支援 | 無 | `怎麼做`：建立一家飯店（或把預設租戶的代碼與名稱設成指定值）。`權威狀態落地`：`tenants` 有這一列。`回寫`：這家飯店的內部編號 `tenants.id`。 |
| `飯店 "{代碼}" 有房務員工 "{員工}"，員工編號密碼為 "{員工編號}" / "{密碼}"` | `代碼`：字串；先用上一句建好的飯店的 `tenants.code`。<br>`員工`：字串。<br>`員工編號`：字串；可以和「測試飯店」員工的編號相同。<br>`密碼`：字串。 | 不支援 | 無 | `怎麼做`：在這家飯店（不是「測試飯店」）建一位在職的房務員工帳號，`login_id` 與密碼雜湊設成指定值；只建帳號，不排班。`權威狀態落地`：`staff_accounts` 有一列 `tenant_id`＝這家飯店、`login_id`＝該員工編號、`role`＝`housekeeping`、`active`=true、連續錯誤次數 0。`回寫`：員工姓名對應的 staff id 與密碼。 |

## When 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `"{員工}" 用員工編號 "{員工編號}" 密碼 "{密碼}" 登入` | `員工`：字串；僅供案例辨識，不是請求欄位。<br>`員工編號`：字串。<br>`密碼`：字串；明文。 | 不支援 | 無 | `怎麼做`：呼叫 `staffAccountsLogin`（`POST /staff-accounts/login`），body `LoginRequest{loginId, password}`，不帶 `client`，所以不看角色。`權威狀態落地`：成功時不改動員工資料；密碼錯誤時累計連續錯誤次數。`回寫`：回應。 |
| `"{員工}" 帶飯店代碼 "{代碼}" 用員工編號 "{員工編號}" 密碼 "{密碼}" 登入` | `員工`：字串；僅供案例辨識。<br>`代碼`：字串；原樣送出，不修剪也不轉小寫。<br>`員工編號`：字串。<br>`密碼`：字串；明文。 | 不支援 | 無 | `怎麼做`：呼叫 `staffAccountsLogin`，body `LoginRequest{loginId, password, hotelCode}`，不帶 `client`。`權威狀態落地`：只在代碼對到的那家飯店找帳號，停用、鎖定、密碼的規則只作用在那家的帳號；密碼錯誤時只累計那家帳號的連續錯誤次數，別家相同員工編號的帳號不動；代碼對不到飯店時不動任何帳號。`回寫`：回應。 |
| `"{員工}" 從櫃檯畫面用員工編號 "{員工編號}" 密碼 "{密碼}" 登入` | `員工`：字串；僅供案例辨識。<br>`員工編號`：字串。<br>`密碼`：字串；明文。 | 不支援 | 無 | `怎麼做`：呼叫 `staffAccountsLogin`，body `LoginRequest{loginId, password, client: frontdesk_web}`。`權威狀態落地`：檢查順序是停用、鎖定、密碼比對、角色；角色是房務、工務、老闆且密碼不是臨時密碼時不發憑證，連續錯誤次數與鎖定狀態都不動。`回寫`：回應。 |
| `"{員工}" 把新密碼設為 "{新密碼}"` | `員工`：字串。<br>`新密碼`：字串；明文。 | 不支援 | 無 | `怎麼做`：帶 Given 那次登入換得的憑證呼叫 `changeOwnPassword`，body `ChangePasswordRequest{newPassword}`，不帶 `currentPassword`。`權威狀態落地`：成功時重新雜湊寫入 `password_hash`、`must_change_password` 改為 false、換發新憑證；不符合規則或與目前密碼相同時回 422，帳號資料不變。`回寫`：回應。 |
| `沒有登入的人用識別代碼 "{代碼}" 查飯店名稱` | `代碼`：字串。 | 不支援 | 無 | `怎麼做`：不帶任何憑證呼叫 `readPublicHotel`（`GET /public/hotels/{hotelCode}`）。`權威狀態落地`：查詢不寫資料。`回寫`：回應。 |
| `沒有登入的人用 "{代碼}" 這家飯店的內部編號查飯店名稱` | `代碼`：字串；用來指出是哪一家飯店。 | 不支援 | 無 | `怎麼做`：把該飯店的 `tenants.id` 當成路徑參數，不帶憑證呼叫 `readPublicHotel`。`回寫`：回應。 |

## Then 句型

| DSL 句型 | Gherkin 參數 | Data Table 參數 | 預設參數 | StepDef 實作語意 |
| --- | --- | --- | --- | --- |
| `登入成功，取得 "{員工}" 的身分與可用憑證` | `員工`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：回應 200，形狀是 `LoginResponse`：`name`、`role` 與該員工相符，`mustChangePassword`=false，並帶可供後續請求使用的 `token` 與 `expiresAt`。`權威狀態`：沒有建立或改動任何 `staff_accounts` 資料。 |
| `登入失敗，提示 "{提示}"` | `提示`：字串；固定文案 `員工編號或密碼不正確`。 | 不支援 | 無 | `必查`：`呈現結果`：回 401，`Error.code`=`credentials_invalid`，body 的提示文字等於該文案。`權威狀態`：沒有建立任何憑證；密碼錯誤時連續錯誤次數加 1（登入帶了飯店代碼時看那家飯店的帳號，沒帶時看「測試飯店」的），員工編號不存在時沒有帳號可記。 |
| `"{員工}" 的帳號已鎖定 {分鐘} 分鐘` | `員工`：字串。<br>`分鐘`：整數；固定 15。 | 不支援 | 無 | `必查`：`呈現結果`：回 429，`Error.code`=`account_locked`，`context` 帶 `lockedUntil` 與 `remainingMinutes`；不是 401 `credentials_invalid`。`權威狀態`：帳號的鎖定到期時間是最後一次錯誤後的該分鐘數；鎖定期間內即使密碼正確也不發憑證。 |
| `"{員工}" 的帳號已停用，登入被拒` | `員工`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：回 403，`Error.code`=`account_disabled`；不是 401 `credentials_invalid`，也不是鎖定的回應。`權威狀態`：`staff_accounts.active`=false 的帳號登入一律被拒，不因密碼正確而放行；對齊介面根共用 DSL「停用的員工不出現在挑人名單、不收推播」的既有約定，一併擴充為「也不能登入」。 |
| `"{員工}" 必須先設定新密碼才能繼續` | `員工`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：回應 200，`mustChangePassword`=true。`不該發生`：在完成改密前，用這組憑證呼叫其他需要身分的 API 一律被拒。 |
| `登入回應帶員工編號 "{員工編號}" 與飯店名稱 "{飯店}"` | `員工編號`：字串。<br>`飯店`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：回應 200，`LoginResponse.loginId` 等於帳號上存的員工編號、`hotelName` 等於該帳號所屬飯店的 `tenants.name`。`權威狀態`：與 `staff_accounts.login_id`、`tenants.name` 一致。 |
| `登入失敗，回應與密碼錯誤時完全相同，沒有發出憑證` | 無 | 不支援 | 無 | `必查`：`呈現結果`：回 401，`Error.code`=`credentials_invalid`；狀態碼、`code`、`message` 與同一帳號輸入錯誤密碼時的回應逐欄相同，body 沒有 `token`、`role` 或任何看得出密碼其實正確的內容。`權威狀態`：沒有發出憑證。 |
| `"{員工}" 的連續錯誤次數仍是 {次數} 次，帳號沒有被鎖定` | `員工`：字串。<br>`次數`：整數。 | 不支援 | 無 | `必查`：`權威狀態`：該帳號累計的連續錯誤次數等於該數字（沒有增加、也沒有清零），鎖定到期時間是空的。`再讀確認`：接著不帶 `client` 用正確密碼登入會成功。 |
| `"{員工}" 的連續錯誤次數是 {次數} 次` | `員工`：字串；可以是任何一家飯店的員工。<br>`次數`：整數。 | 不支援 | 無 | `必查`：`權威狀態`：該員工帳號的 `staff_accounts.failed_login_count` 等於該數字。只讀資料庫、不送請求，可以接在其他 Then 之後。 |
| `登入成功，標示必須先設定新密碼，角色為 "{角色}"` | `角色`：字串；房務、工務、老闆、櫃檯、主管、值班主管。 | 不支援 | 無 | `必查`：`呈現結果`：回 200，`mustChangePassword`=true，`role` 對應該角色（房務＝`housekeeping`、工務＝`engineering`、老闆＝`owner`），帶 `token`。`不該發生`：回 401 `credentials_invalid`。 |
| `新密碼設定成功，"{員工}" 不必再登入就拿到可用的憑證` | `員工`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：回 200，形狀是 `LoginResponse`，`mustChangePassword`=false，帶換發的 `token`。`權威狀態`：`must_change_password`=false。`再讀確認`：用換發的憑證呼叫 `getDeskBoard` 回 200。 |
| `"{員工}" 的密碼已是 "{密碼}"，原本的臨時密碼登不進去，之後登入不再要求設定新密碼` | `員工`：字串。<br>`密碼`：字串。 | 不支援 | 無 | `必查`：`權威狀態`：`password_hash` 對得上該密碼、對不上原本的臨時密碼；`must_change_password`=false。`再讀確認`：不帶 `client` 用臨時密碼登入回 401 `credentials_invalid`；用該密碼登入回 200 且 `mustChangePassword`=false。 |
| `這次設定新密碼被拒絕，沒過的規則是 "{規則}"` | `規則`：字串；至少 8 碼、包含英文字母、包含數字。 | 不支援 | 無 | `必查`：`呈現結果`：回 422，`Error.code`=`new_password_too_weak`，`context.failedRules` 剛好是對應的那一條（至少 8 碼＝`min_length`、包含英文字母＝`letter_required`、包含數字＝`digit_required`）。 |
| `這次設定新密碼被拒絕，因為新密碼和目前的密碼相同` | 無 | 不支援 | 無 | `必查`：`呈現結果`：回 422，`Error.code`=`new_password_invalid`。 |
| `"{員工}" 的密碼仍是原本的臨時密碼，仍然必須先設定新密碼` | `員工`：字串。 | 不支援 | 無 | `必查`：`權威狀態`：`password_hash` 與這次請求前相同，`must_change_password` 仍為 true。 |
| `設定新密碼的回應帶的角色是 "{角色}"` | `角色`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：回 200，`LoginResponse.role` 對應該角色，`mustChangePassword`=false。 |
| `查到的飯店名稱是 "{名稱}"，回應沒有其他資料` | `名稱`：字串。 | 不支援 | 無 | `必查`：`呈現結果`：回 200，body 只有 `name` 一個欄位，值等於名稱。`權威狀態`：等於 `tenants.name`。 |
| `查不到這家飯店` | 無 | 不支援 | 無 | `必查`：`呈現結果`：回 404 `not_found`，body 沒有任何飯店資料，不同原因的回應彼此相同。 |
