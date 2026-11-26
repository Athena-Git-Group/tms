# Truth Delta: 009-repair-photo-storage

**Plan Package**: `specs/plans/009-repair-photo-storage`
**Truth Root**: `specs/truth`

> 本版的預期影響總表見 `spec.md`「哪些既有行為會改？」。下面四張表是 `specs/truth/**` 已經寫進去的改動。`/dsl-refine` 段的「改動摘要」寫的是 truth 現在的樣子，步驟實作照 `dsl.md` 的那一列寫。

## /technical-research

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `specs/truth/techstack.md` -> 後端「照片儲存」列 | Google Cloud Storage，官方 Java 程式庫 `google-cloud-storage`（版本跟 `firebase-admin` 間接帶入的相同）；本機與測試接 `fake-gcs-server`；物件名稱 `repair-photos/{tenantId}/{photoId}.jpg`；本機與正式只差 `tms.storage.bucket`、`tms.storage.emulator-host`、登入憑證；照片經後端轉送。 | spec「照片要存在哪裡，本機跟正式環境才會是同一套程式？」；`research.md`「後端用哪個程式庫接雲端儲存？」。 |
| MODIFY | `specs/truth/techstack.md` -> 前端「App 相機與照片」列 | 從 `camera` ＋ `image_picker` ＋ `flutter_image_compress` ＋ Canvas 浮水印，改成只用 `image_picker`（系統相機、系統相簿、內建縮放壓縮）；浮水印只疊在畫面；測試版回固定測試照片；照片以 `ApiClient` 讀回、記憶體快取。 | 程式從沒用過 `camera` 與 `flutter_image_compress`，浮水印本版不畫進照片；`research.md`「App 用什麼拍照、選照片與壓縮？」。 |
| MODIFY | `specs/truth/techstack.md` -> 「本機環境」「後端 E2E 依賴」兩列 | 本機 docker-compose 多 `fake-gcs-server`（埠 4443）；Testcontainers 多 `fake-gcs-server`，照片真的寫進模擬服務再讀出比對。 | spec 白話 NFR「後端自動化測試真的把照片寫進模擬的雲端儲存再讀出來比對」。 |
| MODIFY | `specs/truth/techstack.md` -> 「本次開發不引入的技術」 | 後端影像處理那一項改寫成「後端只檢查 JPEG 與大小後原樣存放」；`image` 套件那一項的替代改成 `image_picker`；新增 `camera`、`flutter_image_compress` 與臨時下載網址三項不引入及理由。 | 同上兩列；spec「為什麼照片經後端轉送？」。 |

## /api-plan

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `getRepairPhotoContent`（`GET /repair-photos/{photoId}/content`） | 回原始 JPEG 位元組；同飯店 `housekeeping`、`engineering`、`manager`、`frontdesk` 都能讀；不存在、別家飯店、pending、雲端儲存沒有內容一律 404 `not_found`；`Cache-Control: private, max-age=3600`。 | spec「照片怎麼讀回來？」「誰看得到照片？」。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `uploadRepairPhotoContent`（群組鍵改名為 `uploadRepairPhotoContentAndgetRepairPhotoContent`） | 內容存進雲端儲存後才轉 uploaded、`storage_ref` 記物件名稱；檢查順序與錯誤碼：413 `photo_too_large`、422 `photo_empty`、415 `photo_type_unsupported`、404 `not_found`、403 `forbidden`（不是建立者）、409 `photo_already_attached`；request body 加 `maxLength`；回應補 403、409、413、415。請求與成功回應形狀不變。 | spec「照片內容怎麼上傳？」；同一路徑多了 GET，依 `api-plan` 拆檔規則用 `{op1}And{op2}` 鍵。 |
| MODIFY | `specs/truth/contracts/openapi.yaml` -> `/repair-photos/{photoId}/content` | `$ref` 指向改名後的群組鍵。 | 同上。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `createRepairPhoto` 說明 | 浮水印只疊畫面；建立者記為 uploadedBy；`purpose` 不合法或沒帶、`takenAt` 格式錯回 422 `validation_failed`（details 指出欄位）。請求與回應形狀不變。 | spec「建立照片時要檢查什麼？」；原本 500 與資料庫錯誤。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `appendRepairTicketPhotos` 說明 | 先檢查單的狀態（`ticket_closed`）、再檢查照片：超過 3 張 `photo_limit`、未上傳 `photo_not_uploaded`、用途不是 report `photo_purpose_mismatch`、已附在別張 `photo_already_attached`；被拒時一張都不補。 | spec「補照片要符合什麼？」。 |
| MODIFY | `specs/truth/contracts/components/schemas/工務單.yaml` -> `CreateRepairTicketRequest.photoIds`、`AppendRepairPhotosRequest.photoIds`、`CreateRepairPhotoRequest.takenAt`、`RepairPhoto` | 開單照片加 `photo_purpose_mismatch`、`photo_already_attached`；補照片陣列加說明；`takenAt` 寫明格式錯回 422；`RepairPhoto` 寫明 uploaded 就讀得到內容。 | spec「開單附的照片要符合什麼？」「用到哪些資料？」。 |
| MODIFY | `specs/truth/contracts/components/schemas/工務單.yaml` -> `RepairTicket.photos`、`completionPhotos` | 兩者改為必填；說明寫明 App 用每張 `id` 呼叫 `getRepairPhotoContent`，看大圖照 `photos` 陣列。不加欄位。 | 後端 `ticketView` 一直有回這兩個欄位；spec「工務單詳情照舊帶…不加新欄位」。 |
| MODIFY | `specs/truth/contracts/components/schemas/任務.yaml` -> `CompleteTaskRequest.photoIds`；`groups/現場作業/任務.yaml` -> `tasksComplete` 說明 | 完修照片逐張檢查：`photo_not_uploaded`、`photo_purpose_mismatch`、`photo_already_attached`；被拒時工務單、事件、推播都不變。請求與回應形狀不變。 | spec「完修附的照片要符合什麼？」；原說明已寫「每張都要已上傳、purpose=completion」但沒有錯誤碼，後端也沒照做。 |
| ADD | `specs/truth/contracts/components/responses.yaml` -> `PayloadTooLarge`、`UnsupportedMediaType` | 413、415 兩種回應，body 是 `Error`。 | 上傳內容的兩個新狀態碼。 |
| MODIFY | `specs/truth/contracts/components/schemas/shared.yaml` -> `Error` 說明 | `photo_not_uploaded` 說明擴大到不存在與別家飯店；新增 `photo_purpose_mismatch`、`photo_already_attached`、`photo_empty`、`photo_type_unsupported`、`photo_too_large`。 | 同上各列。 |
| NOOP | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `readRepairTicket`、`createRepairTicket` 的路徑與參數；`config` 權限 | 已檢查，不需修改。讀照片沿用 `GET /api/v1/**` 已放行的四個角色。 | 契約已涵蓋。 |

## /data-plan

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_photos.storage_ref` note | 從「外存引用」改成雲端儲存的物件名稱 `repair-photos/{tenant_id}/{id}.jpg`，內容寫入成功後與 `status=uploaded` 同一句寫入；讀照片用這個值；舊列可能是假值 `upload:{id}`。 | spec「照片在雲端儲存裡叫什麼名字？」。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_photos.ticket_task_id`、`uploaded_by` note | 綁定後內容不能再上傳、一張照片只綁一張單；`uploaded_by` 是建立者，只有他能上傳內容。 | spec「照片內容怎麼上傳？」。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_photos` Note | 內容是 1 位元組到 5 MB 的 JPEG；重傳同名覆蓋；不變條件「uploaded 就有內容」「綁定的照片一定 uploaded、開單補照片只綁 report、完修只綁 completion」。 | spec「用到哪些資料？」。 |
| NOOP | `specs/truth/data/data-model.dbml` -> 所有資料表的欄位、索引、CHECK、Ref | 已檢查，不需修改。不加欄位、不加 Flyway 遷移；`purpose` 與 `status` 的 CHECK 不變。 | 照片內容不入庫，存放位置沿用既有 `storage_ref`。 |

## /dsl-refine

### 後端

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `specs/truth/features/backend/工務單/照片內容上傳與查看.feature` | 15 條 Rule：上傳 JPEG 後已上傳且內容相符；剛好 5 MB 收得下；未附上前重傳以最後一次為準；PNG 與標成 JPEG 的 PNG 被拒；空白被拒；超過 5 MB 被拒；附上後不能換內容；只有建立者能上傳；同飯店看得到；未上傳看不到；別家飯店看不到；建立照片用途不合法被拒；拍攝時間格式錯被拒；開單後工務看得到每張報修照片；完修後主管看得到完修照片。每個 Example 一個 When。 | spec 情境 1、3、5、6；acceptance `報修與完修照片一路看得到真的照片.feature`、`照片不合規定就存不進去也綁不上工務單.feature`。 |
| MODIFY | `specs/truth/features/backend/工務單/照片完整性與冪等送出.feature` | 新增 6 條 Rule：完修照片未上傳、別家飯店、用途是報修、已附在別張單各被拒且工務單仍維修中；開單附完修照片、附已附在別張單的照片各被拒且沒有建立工務單。既有 Rule 不動。 | spec 情境 4 的 1 至 4、8；acceptance「完修、補照片、開單只能綁上…」。 |
| MODIFY | `specs/truth/features/backend/工務單/重複報修補照片與撤回.feature` | 新增 4 條 Rule：補未上傳的照片被拒；一次補 4 張被拒；補完修照片被拒；已完修的單補照片回 `ticket_closed`。被拒時照片張數不變。既有 Rule 不動。 | spec 情境 4 的 5 至 7。 |
| ADD | `specs/truth/features/backend/工務單/dsl.md` -> Given `"{員工}" 已上傳照片 "{照片}"，用途為 "{用途}"`、`"{員工}" 已建立照片 "{照片}"，用途為 "{用途}"，還沒上傳內容`、`照片 "{照片}" 已附在 "{單號}"`、`別家飯店的員工已上傳照片 "{照片}"，用途為 "{用途}"` | 以照片標籤準備已上傳、未上傳、已綁定、別家飯店四種照片。 | 新 Rule 的 Arrange。 |
| ADD | `specs/truth/features/backend/工務單/dsl.md` -> When `"{員工}" 以 "{內容}" 上傳照片 "{照片}" 的內容`、`"{員工}" 查看照片 "{照片}"`、`"{員工}" 建立照片，用途為 "{用途}"、拍攝時間為 "{拍攝時間}"`、`"{工務}" 附照片 "{照片}" 以已修好完成 "{單號}"`、`"{員工}" 補照片 "{照片}" 到 "{單號}"`、`"{員工}" 從 "{任務}" 附照片 "{照片}" 開立水電工務單` | 上傳（7 種內容對照）、讀取、建立照片、以指定照片完修、補照片、開單。 | 新 Rule 的 Act。 |
| ADD | `specs/truth/features/backend/工務單/dsl.md` -> Then `照片 "{照片}" 已上傳，存的是 "{內容}"`、`照片 "{照片}" 仍未上傳`、`看到的照片是 "{照片}" 存的內容`、`系統沒有建立照片`、`"{員工}" 打開 "{單號}" 時，"{種類}" 照片 {張數} 張都看得到` | 斷言資料庫狀態、照片儲存內容與讀回的位元組。 | 新 Rule 的 Assert；拒絕類同時驗照片沒被改。 |
| MODIFY | `specs/truth/features/backend/工務單/dsl.md` -> 模組說明「照片內容與存放」段 | 本模組所有上傳照片的句型（`為 "{房號}" 的工務單準備了 {張數} 張照片…`、`已重新上傳失敗的那張照片並成功`、`已由 "{工務}" 完修`、各開單句、`補 {張數} 張照片到`、各完修句）改送測試用的合法 JPEG，每張內容不同；上傳成功時寫進照片儲存。句型文字不變。 | 後端開始檢查 JPEG，原本送任意字串的步驟會被 415 擋下。 |
| MODIFY | `specs/truth/features/backend/dsl.md` -> Then `這次操作被拒絕，錯誤碼為 "{錯誤碼}"` 的錯誤碼舉例 | 補 `not_found`、`photo_not_uploaded`、`photo_purpose_mismatch`、`photo_already_attached`、`photo_empty`、`photo_type_unsupported`、`photo_too_large`、`photo_limit`、`ticket_closed` 與各自的 HTTP 狀態。句型與必查語意不變。 | 新情境用到這些錯誤碼；步驟實作的錯誤碼對 HTTP 狀態表要跟著補。 |
| MODIFY | `specs/truth/features/backend/dsl.md` -> Given `已有以下工務單：` 的 `怎麼做` | 建立的已上傳照片要把測試 JPEG 寫進照片儲存，`storage_ref` 寫物件名稱。句型與欄位不變。 | 維持「已上傳就讀得到內容」的不變條件。 |
| NOOP | `specs/truth/features/backend/工務單/驗收不通過與重修.feature`、`完修與回到原任務.feature`、`工務視角維修與開單.feature`、`開立工務單與急件.feature`、`櫃檯轉來的工務單.feature`、`櫃檯代開的工務單.feature`、`主管驗收的狀態守衛.feature` | 已檢查，不需修改。這些情境用的上傳句型語意由模組說明段統一改成送合法 JPEG；退回重修要附這一輪照片的規則不變。 | 句型文字不變。 |

### 員工 App

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| NOOP | `specs/truth/features/ios-app/工務單/報修照片與送出.feature`、`工務視角.feature`、`主管視角.feature`、`開單提示.feature`、`dsl.md` | 已檢查，不需修改。Appium 在模擬器上用 `TEST_BACKDOOR` 測試版，照舊送固定測試照片；張數、上傳失敗重試、離線重送與浮水印疊在畫面上的規則都不變。真相機與相簿只能在實機手動驗證，由 `tasks.md` 的實機驗證段與 Flutter 畫面測試承接，不寫進 Appium truth。 | 模擬器沒有相機；`research.md`「測試版」。 |

### 櫃檯 Web、住客頁

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| NOOP | `specs/truth/features/frontdesk-web/**`、`specs/truth/features/guest-web/**` | 已檢查，不需修改。櫃檯 Web 與住客頁沒有照片功能，櫃檯代開的工務單不帶照片。 | spec「本版不做」。 |
