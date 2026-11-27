# Truth Delta: 009-repair-photo-storage

**Plan Package**: `specs/plans/009-repair-photo-storage`
**Truth Root**: `specs/truth`

> 本版的預期影響總表見 `spec.md`「哪些既有行為會改？」。下面四張表是 `specs/truth/**` 已經寫進去的改動。`/dsl-refine` 段的「改動摘要」寫的是 truth 現在的樣子，步驟實作照 `dsl.md` 的那一列寫。

## /technical-research

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `specs/truth/techstack.md` -> 後端「照片儲存」列 | Google Cloud Storage，官方 Java 程式庫 `google-cloud-storage`（版本跟 `firebase-admin` 間接帶入的相同）；本機與測試接 `fake-gcs-server`；物件名稱 `repair-photos/{tenantId}/{photoId}.jpg`；本機與正式只差 `tms.storage.bucket`、`tms.storage.mode`（本機與測試 `emulator`、正式環境以 `prod` 設定組的 `application-prod.properties` 設成 `gcs`）、`tms.storage.emulator-host`（只有 `emulator` 模式看）、登入憑證；連線、讀取與重試總時限各約 10 秒；模擬服務模式啟動時建立儲存空間，失敗就啟動失敗；照片經後端轉送給 App 與櫃檯 Web。 | spec「照片要存在哪裡，本機跟正式環境才會是同一套程式？」；`research.md`「後端用哪個程式庫接雲端儲存？」；`plan.md`「存照片的程式切在哪裡？」。 |
| ADD | `specs/truth/techstack.md` -> 前端「櫃檯 Web 照片顯示」列 | 瀏覽器內建 `fetch` 帶 `Authorization` 讀照片位元組，`URL.createObjectURL` 給 `<img>`，卸載或換單時釋放；登入憑證不進網址；不加圖片套件。 | spec「櫃檯 Web 要怎麼顯示需要登入才看得到的照片？」；`research.md`「App 與櫃檯 Web 怎麼顯示需要登入才拿得到的照片？」。 |
| MODIFY | `specs/truth/techstack.md` -> 前端「App 相機與照片」列 | 從 `camera` ＋ `image_picker` ＋ `flutter_image_compress` ＋ Canvas 浮水印，改成只用 `image_picker`（系統相機、系統相簿、內建縮放壓縮）；浮水印只疊在畫面；測試版回固定測試照片；照片以 `ApiClient` 讀回、記憶體快取。 | 程式從沒用過 `camera` 與 `flutter_image_compress`，浮水印本版不畫進照片；`research.md`「App 用什麼拍照、選照片與壓縮？」。 |
| MODIFY | `specs/truth/techstack.md` -> 「本機環境」「後端 E2E 依賴」兩列 | 本機 docker-compose 多 `fake-gcs-server`（埠 4443）；Testcontainers 多 `fake-gcs-server`，照片真的寫進模擬服務再讀出比對。 | spec 白話 NFR「後端自動化測試真的把照片寫進模擬的雲端儲存再讀出來比對」。 |
| MODIFY | `specs/truth/techstack.md` -> 「本次開發不引入的技術」 | 後端影像處理那一項改寫成「後端只檢查 JPEG 與大小後原樣存放」；`image` 套件那一項的替代改成 `image_picker`；新增 `camera`、`flutter_image_compress` 與臨時下載網址三項不引入及理由。 | 同上兩列；spec「為什麼照片經後端轉送？」。 |

## /api-plan

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `getRepairPhotoContent`（`GET /repair-photos/{photoId}/content`） | 回原始 JPEG 位元組；同飯店 `housekeeping`、`engineering`、`manager`、`frontdesk`、`duty_manager` 都能讀，`owner` 與住客回 403；不存在、別家飯店、pending、雲端儲存沒有內容（舊版假值）一律 404 `not_found`；一律以 `Authorization` 帶憑證，不放進網址；`Cache-Control`：已附到工務單是 `private, max-age=86400, immutable`，還沒附上是 `private, no-cache`。 | spec「照片怎麼讀回來？」「誰看得到照片？」「櫃檯 Web 要怎麼顯示需要登入才看得到的照片？」。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `uploadRepairPhotoContent`（群組鍵改名為 `uploadRepairPhotoContentAndgetRepairPhotoContent`） | 內容存進雲端儲存後才轉 uploaded、`storage_ref` 記物件名稱；檢查順序與錯誤碼：413 `photo_too_large`、422 `photo_empty`、415 `photo_type_unsupported`、404 `not_found`、403 `forbidden`（不是建立者）、409 `photo_already_attached`；上傳與綁定都鎖照片列，綁定之後才到的上傳回 409；超過 5 MB 時最多讀完 6 MB 再回 413，更大的請求可能直接關連線；request body 加 `maxLength`；回應補 403、409、413、415。請求與成功回應形狀不變。 | spec「照片內容怎麼上傳？」；`plan.md`「上傳怎麼檢查？」；同一路徑多了 GET，依 `api-plan` 拆檔規則用 `{op1}And{op2}` 鍵。 |
| MODIFY | `specs/truth/contracts/openapi.yaml` -> `/repair-photos/{photoId}/content` | `$ref` 指向改名後的群組鍵。 | 同上。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `createRepairPhoto` 說明 | 浮水印只疊畫面；建立者記為 uploadedBy；`purpose` 不合法或沒帶、`takenAt` 格式錯回 422 `validation_failed`（details 指出欄位）。請求與回應形狀不變。 | spec「建立照片時要檢查什麼？」；原本 500 與資料庫錯誤。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `appendRepairTicketPhotos` 說明 | 先檢查單的狀態（`ticket_closed`）、再檢查照片：超過 3 張 `photo_limit`、未上傳或舊版假照片 `photo_not_uploaded`、用途不是 report `photo_purpose_mismatch`、已附在別張或被同時送出的請求先附走 `photo_already_attached`；被拒時一張都不補。 | spec「補照片要符合什麼？」。 |
| MODIFY | `specs/truth/contracts/components/schemas/工務單.yaml` -> `CreateRepairTicketRequest.photoIds`、`AppendRepairPhotosRequest.photoIds`、`CreateRepairPhotoRequest.takenAt`、`RepairPhoto` | 開單照片加 `photo_purpose_mismatch`、`photo_already_attached`（含同時送出）、舊版假照片算 `photo_not_uploaded`；補照片陣列加說明；`takenAt` 寫明格式錯回 422；`RepairPhoto` 寫明 uploaded 就讀得到內容，舊版假照片讀不到也不能再附上。 | spec「開單附的照片要符合什麼？」「用到哪些資料？」。 |
| MODIFY | `specs/truth/contracts/components/schemas/工務單.yaml` -> `RepairTicket.photos`、`completionPhotos` | 兩者改為必填；說明寫明 App 用每張 `id` 呼叫 `getRepairPhotoContent`，看大圖照 `photos` 陣列。不加欄位。 | 後端 `ticketView` 一直有回這兩個欄位；spec「工務單詳情照舊帶…不加新欄位」。 |
| MODIFY | `specs/truth/contracts/components/schemas/任務.yaml` -> `CompleteTaskRequest.photoIds`；`groups/現場作業/任務.yaml` -> `tasksComplete` 說明 | 完修照片逐張檢查：`photo_not_uploaded`（含舊版假照片）、`photo_purpose_mismatch`、`photo_already_attached`（含同時送出）；被拒時工務單、事件、推播都不變。請求與回應形狀不變。 | spec「完修附的照片要符合什麼？」；原說明已寫「每張都要已上傳、purpose=completion」但沒有錯誤碼，後端也沒照做。 |
| MODIFY | `specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskTaskDetail.repair` | `repair` 多兩個必填欄位 `photos`、`completionPhotos`（`RepairPhoto` 陣列），內容、順序與「這一輪」規則同 `RepairTicket.photos`、`completionPhotos`；不是工務單時 `repair` 仍是 null。 | spec「照片怎麼讀回來？」、情境 7；櫃檯 Web 的詳情面板要照片 id，`repair` 本來就是工務單才有的內容。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> `readDeskTask` 說明 | 回的內容多「工務單的報修與完修照片 id」。路徑、參數、角色不變。 | 同上。 |
| ADD | `specs/truth/contracts/components/responses.yaml` -> `PayloadTooLarge`、`UnsupportedMediaType` | 413、415 兩種回應，body 是 `Error`。 | 上傳內容的兩個新狀態碼。 |
| MODIFY | `specs/truth/contracts/components/schemas/shared.yaml` -> `Error` 說明 | `photo_not_uploaded` 說明擴大到不存在、別家飯店與舊版假照片；新增 `photo_purpose_mismatch`、`photo_already_attached`（含同時送出）、`photo_empty`、`photo_type_unsupported`、`photo_too_large`。 | 同上各列。 |
| NOOP | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `readRepairTicket`、`createRepairTicket` 的路徑與參數 | 已檢查，不需修改。 | 契約已涵蓋。 |

## /data-plan

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_photos.storage_ref` note | 從「外存引用」改成雲端儲存的物件名稱 `repair-photos/{tenant_id}/{id}.jpg`，內容寫入成功後與 `status=uploaded` 同一句寫入；讀照片用這個值；舊列可能是假值 `upload:{id}`，讀取回 404，開單、補照片、完修時視同未上傳。 | spec「照片在雲端儲存裡叫什麼名字？」「開單附的照片要符合什麼？」。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_photos.ticket_task_id`、`uploaded_by` note | 綁定後內容不能再上傳、一張照片只綁一張單；`uploaded_by` 是建立者，只有他能上傳內容。 | spec「照片內容怎麼上傳？」。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_photos` Note | 內容是 1 位元組到 5 MB 的 JPEG；重傳同名覆蓋；不變條件「uploaded 就有內容」「綁定的照片一定 uploaded、開單補照片只綁 report、完修只綁 completion」；上傳與綁定都以 `SELECT … FOR UPDATE` 依 id 排序鎖住照片列，綁定改到的筆數少於張數就回滾並回 409。 | spec「用到哪些資料？」「兩個人同時把同一張照片附到不同的工務單會怎樣？」。 |
| NOOP | `specs/truth/data/data-model.dbml` -> 所有資料表的欄位、索引、CHECK、Ref | 已檢查，不需修改。不加欄位、不加 Flyway 遷移；`purpose` 與 `status` 的 CHECK 不變。 | 照片內容不入庫，存放位置沿用既有 `storage_ref`。 |

## /dsl-refine

### 後端

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `specs/truth/features/backend/工務單/照片內容上傳與查看.feature` | 19 條 Rule：上傳 JPEG 後已上傳且內容相符；剛好 5 MB 收得下；未附上前重傳以最後一次為準、第一次上傳時間不變；PNG、標成 JPEG 的 PNG、標成 PNG 的 JPEG 被拒；空白被拒；超過 5 MB 被拒；附上後不能換內容；只有建立者能上傳；別家飯店與不存在的照片上傳回 `not_found`；同飯店看得到；櫃檯、主管、值班主管看得到；老闆看不到；舊版假照片看不到；未上傳看不到；別家飯店看不到；建立照片用途不合法或沒帶被拒；拍攝時間格式錯被拒；開單後工務看得到每張報修照片；完修後主管看得到完修照片。每個 Example 一個 When。 | spec 情境 1、3、5、6；acceptance `報修與完修照片一路看得到真的照片.feature`、`照片不合規定就存不進去也綁不上工務單.feature`。 |
| MODIFY | `specs/truth/features/backend/工務單/照片完整性與冪等送出.feature` | 新增 7 條 Rule：完修照片未上傳、別家飯店、用途是報修、已附在別張單各被拒且工務單仍維修中；開單附完修照片、附已附在別張單的照片、附舊版假照片各被拒且沒有建立工務單。既有 Rule 不動。 | spec 情境 4 的 1 至 4、8、10；acceptance「完修、補照片、開單只能綁上…」。 |
| MODIFY | `specs/truth/features/backend/工務單/重複報修補照片與撤回.feature` | 新增 4 條 Rule：補未上傳的照片被拒；一次補 4 張被拒；補完修照片被拒；已完修、已結案、已撤回的單補照片回 `ticket_closed`（場景大綱）。被拒時照片張數不變。背景多一位主管當驗收人。既有 Rule 不動。 | spec 情境 4 的 5 至 7、9。 |
| MODIFY | `specs/truth/features/backend/櫃檯看板與派單/單筆詳情與歷程.feature` | 新增 2 條 Rule：工務單的櫃檯詳情帶報修與這一輪完修照片的編號，櫃檯與值班主管都讀得到每一張；不是工務單的詳情沒有照片。背景多一位值班主管。 | spec 情境 7；`DeskTaskDetail.repair.photos`、`completionPhotos`。 |
| ADD | `specs/truth/features/backend/工務單/dsl.md` -> Given `"{員工}" 已上傳照片 "{照片}"，用途為 "{用途}"`、`"{員工}" 已建立照片 "{照片}"，用途為 "{用途}"，還沒上傳內容`、`照片 "{照片}" 已附在 "{單號}"`、`別家飯店的員工已上傳照片 "{照片}"，用途為 "{用途}"`、`照片 "{照片}" 不存在`、`照片 "{照片}" 是舊版的假照片` | 以照片標籤準備已上傳、未上傳、已綁定、別家飯店、不存在、舊版假照片六種照片。 | 新 Rule 的 Arrange。 |
| ADD | `specs/truth/features/backend/工務單/dsl.md` -> When `"{員工}" 以 "{內容}" 上傳照片 "{照片}" 的內容`、`"{員工}" 查看照片 "{照片}"`、`"{員工}" 建立照片，用途為 "{用途}"、拍攝時間為 "{拍攝時間}"`、`"{工務}" 附照片 "{照片}" 以已修好完成 "{單號}"`、`"{員工}" 補照片 "{照片}" 到 "{單號}"`、`"{員工}" 從 "{任務}" 附照片 "{照片}" 開立水電工務單` | 上傳（8 種內容對照，原始位元組送出）、讀取、建立照片（用途可「不帶」）、以指定照片完修、補照片、開單。 | 新 Rule 的 Act。 |
| ADD | `specs/truth/features/backend/工務單/dsl.md` -> Then `照片 "{照片}" 已上傳，存的是 "{內容}"`、`照片 "{照片}" 仍未上傳`、`看到的照片是 "{照片}" 存的內容`、`照片 "{照片}" 的第一次上傳時間是 "{時刻}"`、`系統沒有建立照片`、`"{員工}" 打開 "{單號}" 時，"{種類}" 照片 {張數} 張都看得到` | 斷言資料庫狀態、照片儲存內容、讀回的位元組與 `Cache-Control`。 | 新 Rule 的 Assert；拒絕類同時驗照片沒被改。 |
| MODIFY | `specs/truth/features/backend/工務單/dsl.md` -> 模組說明「照片內容與存放」段 | 本模組所有上傳照片的句型（`為 "{房號}" 的工務單準備了 {張數} 張照片…`、`已重新上傳失敗的那張照片並成功`、`已由 "{工務}" 完修`、各開單句、`補 {張數} 張照片到`、各完修句）改送測試用的合法 JPEG 原始位元組，每張內容不同；上傳成功時寫進照片儲存；定義「舊版的假照片」。句型文字不變。 | 後端開始檢查 JPEG，原本送 JSON 字串的步驟會被 415 擋下。 |
| ADD | `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> Then `櫃檯詳情的報修照片有 {報修張數} 張、完修照片有 {完修張數} 張，"{員工}" 每一張都讀得到`、`櫃檯詳情沒有工務單的照片` | 斷言 `DeskTaskDetail.repair.photos`、`completionPhotos` 與 `readRepairTicket` 一致，並以該員工讀每一張照片。 | 新 Rule 的 Assert。 |
| MODIFY | `specs/truth/features/backend/dsl.md` -> Then `這次操作被拒絕，錯誤碼為 "{錯誤碼}"` 的錯誤碼舉例 | 補 `not_found`（讀照片與上傳內容兩種情況）、`photo_not_uploaded`（含舊版假照片）、`photo_purpose_mismatch`、`photo_already_attached`（含同時送出）、`photo_empty`、`photo_type_unsupported`、`photo_too_large`、`photo_limit`、`ticket_closed` 與各自的 HTTP 狀態。句型與必查語意不變。 | 新情境用到這些錯誤碼；步驟實作的錯誤碼對 HTTP 狀態表要跟著補。 |
| MODIFY | `specs/truth/features/backend/dsl.md` -> Given `已有以下工務單：` 的 `怎麼做` | 建立的已上傳照片要把測試 JPEG 寫進照片儲存，`storage_ref` 寫物件名稱。句型與欄位不變。 | 維持「已上傳就讀得到內容」的不變條件。 |
| NOOP | `specs/truth/features/backend/工務單/驗收不通過與重修.feature`、`完修與回到原任務.feature`、`工務視角維修與開單.feature`、`開立工務單與急件.feature`、`櫃檯轉來的工務單.feature`、`櫃檯代開的工務單.feature`、`主管驗收的狀態守衛.feature`；`櫃檯看板與派單/櫃檯介面的角色權限.feature` | 已檢查，不需修改。工務單情境用的上傳句型語意由模組說明段統一改成送合法 JPEG；退回重修要附這一輪照片的規則不變。讀照片的角色規則寫在 `照片內容上傳與查看.feature`，櫃檯介面的角色權限只管 `/desk/**`。 | 句型文字不變。 |
| MODIFY | `specs/truth/features/backend/工務單/照片完整性與冪等送出.feature` | 審查後再加 3 條 Rule：完修一次最多 3 張（`photo_limit`）；完修照片一張合格一張未上傳時整次被拒、合格的也沒附上；以不需處理完成時附了未上傳的照片被拒。 | spec「完修附的照片要符合什麼？」的 3 張上限與不需處理帶照片、邊界情況「完修時同時帶了合法與不合法的照片」原本沒有情境承接。 |
| MODIFY | `specs/truth/features/backend/工務單/重複報修補照片與撤回.feature` | 審查後再加 1 條 Rule：補已附在別張單上的照片回 `photo_already_attached`，兩張單的照片張數都不變。 | spec「補照片要符合什麼？」的已附在別張單上原本沒有情境承接。 |
| ADD | `specs/truth/features/backend/工務單/dsl.md` -> When `"{工務}" 附照片 "{照片}"、"{另一張照片}" 以已修好完成 "{單號}"`、`"{工務}" 附照片 "{照片}" 以完修結果 "{結果}" 完成 "{單號}"` | 一次附兩張指定照片完修；以指定完修結果附一張指定照片完修。 | 上面兩條新 Rule 的 Act。 |
| MODIFY | `specs/truth/features/backend/工務單/dsl.md` -> 模組說明「報修與驗收」段、When `"{工務}" 附 {張數} 張完修照片、處理說明 "{說明}" 完成 "{單號}"` | 補「不需處理帶了照片也照完修照片規則檢查」「一次最多 3 張，超過回 `photo_limit`」。句型文字不變。 | 跟 spec 對齊。 |

### 員工 App

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| MODIFY | `specs/truth/features/ios-app/工務單/主管視角.feature` | 新增 1 條 Rule：主管從待驗收清單打開已完修的單，按通過或不通過之前，報修照片與這一輪完修照片都顯示出真的圖片，主管動作仍是通過、不通過。既有 Rule 不動。 | spec「房務主管、工務主管」故事、情境 2 的 2；使用者追加「工務主管驗收時看得到照片」。 |
| ADD | `specs/truth/features/ios-app/工務單/dsl.md` -> Then `工務單詳情的 "{種類}" 照片 {張數} 張都顯示出圖片` | 照片格子的 accessibility value 是 `loaded`，格數與後端 `photos`／`completionPhotos` 一致。 | 新 Rule 的 Assert。 |
| MODIFY | `specs/truth/features/ios-app/工務單/dsl.md` -> 模組說明 | 寫明後端保存照片內容、詳情與看大圖讀回真的圖片、讀不到顯示「照片無法顯示」，照片格子以 accessibility value 標出 `loaded`、`loading`、`failed`。 | 同上。 |
| MODIFY | `specs/truth/features/ios-app/dsl.md` -> Given `已有以下工務單：` 的 `怎麼做` | 報修與完修照片經 API 建立並上傳不同的合法 JPEG，再綁到單上，維持「已上傳就讀得到內容」。句型與欄位不變。 | 舊的做法直接寫 `storage_ref`=`fixture/{id}.jpg`，照片儲存沒有內容，畫面只會顯示「照片無法顯示」。 |
| NOOP | `specs/truth/features/ios-app/工務單/報修照片與送出.feature`、`工務視角.feature`、`開單提示.feature`、`工務單房務端生命週期.feature` | 已檢查，不需修改。Appium 在模擬器上用 `TEST_BACKDOOR` 測試版，照舊送固定測試照片；張數、上傳失敗重試、離線重送與浮水印疊在畫面上的規則都不變。真相機與相簿只能在實機手動驗證，由 `tasks.md` 的實機驗證段與 Flutter 畫面測試承接。 | 模擬器沒有相機；`research.md`「測試版」。 |

### 櫃檯 Web、住客頁

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `specs/truth/features/frontdesk-web/單筆詳情/工務單照片.feature` | 9 條 Rule：工務單詳情在「內容」與歷程之間有兩排縮圖、依序是這張單的照片；值班主管一樣看得到；沒有照片的那一排不出現；不是工務單沒有照片；點縮圖開看大圖並標出第幾張；看大圖只在同一排切換；讀不到的照片顯示「照片無法顯示」、其他照常；四種介面語言的字句；電腦與 iPad 兩種尺寸不破版、至少 44 × 44。整份標 `@ipad`。 | spec「櫃檯 Web 怎麼顯示照片？」、情境 7；使用者追加「櫃檯 Web 看工務單時看得到照片」。 |
| MODIFY | `specs/truth/features/frontdesk-web/單筆詳情/dsl.md` -> 模組說明、Given、When、Then | 模組說明加「工務單照片」段；新增 Given `"{房號}" 的工務單有 {報修張數} 張報修照片、{完修張數} 張完修照片`、`"{房號}" 的工務單第 {第幾張} 張報修照片是舊版的假照片`、`"{員工}" 開著 "{房號}" 詳情面板 "{區塊}" 第 {第幾張} 張照片的大圖`；新增 When 段與 `"{員工}" 點詳情面板 "{區塊}" 的第 {第幾張} 張縮圖`、`"{員工}" 在看大圖按 "{按鈕}"`；新增 Then `詳情面板的 "{區塊}" 有 {張數} 張縮圖，依序是這張單的照片`、`詳情面板的照片區塊排在 "{前}" 之後、"{後}" 之前`、`詳情面板沒有 "{區塊}" 這一排`、`詳情面板沒有照片`、`看大圖顯示 "{區塊}" 的第 {第幾張} 張，標示 "{計數}"`、`詳情面板 "{區塊}" 的第 {第幾張} 張顯示 "{文字}"`、`詳情面板 "{區塊}" 的第 {第幾張} 張是這張單的照片`、`詳情面板的照片區塊標題依序是 "{報修}"、"{完修}"`、`詳情面板的照片縮圖都在面板寬度內，每張至少 44 × 44`。 | 新 feature 的 Arrange、Act、Assert。 |
| NOOP | `specs/truth/features/frontdesk-web/單筆詳情/詳情面板內容.feature` 與其餘櫃檯 Web feature；`specs/truth/features/guest-web/**` | 已檢查，不需修改。詳情面板既有區塊的順序與內容不變，照片排在「內容」與歷程之間另成一份 feature；住客頁沒有照片功能。 | spec「本版不做」住客頁看照片。 |
