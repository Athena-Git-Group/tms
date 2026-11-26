# 系統分析規劃：工務單照片拍了就是真的，存得住、看得到

**Plan Package**: `specs/plans/009-repair-photo-storage`
**Truth Root**: `specs/truth`
**Truth Delta**: `specs/plans/009-repair-photo-storage/truth-delta.md`

讀者先讀 `spec.md` 的「為什麼這樣定？」，技術選型讀 `research.md`。下面這些事已經定了，本檔只寫理由與落點：一套用官方雲端儲存程式庫的存照片程式、本機與測試接 `fake-gcs-server`、物件名稱 `repair-photos/{飯店編號}/{照片編號}.jpg`、上傳端點與 body 不變、讀照片經後端轉送、補上四處照片檢查、App 用 `image_picker`、櫃檯 Web 的工務單詳情面板與主管驗收畫面都顯示真的照片。

本檔回答六個實作問題：存照片的程式切在哪裡、上傳怎麼檢查、讀照片怎麼回、四處照片檢查怎麼共用、App 怎麼接、櫃檯 Web 怎麼接。最後列出程式現況的落差與可能出錯的地方。

`ui/` 不建立：App 沿用既有的照片格子、照片選單與看大圖元件，只是第一次真的給它們圖片，沒有新畫面；權限被拒與照片不能用的提示用既有的提示框元件，文字寫在 `spec.md`。櫃檯 Web 的詳情面板多兩排縮圖與一個看大圖，櫃檯原型（`specs/plans/007-frontdesk-create-request/ui/`）沒有畫照片；版面沿用面板既有的區塊樣式，規則與四種語言的字句寫在 `spec.md`「櫃檯 Web 怎麼顯示照片？」，列為假設，不另出原型。

本檔引用的行號以分支起點 `7420c55` 為準，以方法名稱為主。後端路徑省略前綴 `tms/src/main/java/com/athena/tms/`，App 路徑省略前綴 `app/mobile/lib/`。

## 專案結構

### 文件結構（本功能）

```text
specs/plans/009-repair-photo-storage/
├── spec.md
├── checklists/requirements.md
├── features/acceptance/            # 三份總驗收 Gherkin
├── research.md
├── plan.md                         # 本檔
├── truth-delta.md
└── tasks.md
```

### 原始碼結構（儲存庫根目錄）

```text
docker-compose.yml                                   # 加 fake-gcs-server 服務與 volume
README.md                                            # 本機環境多一個服務的說明
scripts/manual-env/env.sh                            # 手動測試環境用自己的儲存空間名稱
tms/pom.xml                                          # 明確宣告 google-cloud-storage
tms/src/main/resources/application.properties        # tms.storage.*、server.tomcat.max-swallow-size
tms/src/main/resources/application-prod.properties   # 新檔：tms.storage.emulator-host= （正式環境不接模擬服務）
tms/src/main/java/com/athena/tms/
├── storage/PhotoStorage.java                        # put(key, bytes)、open(key) 兩個動作的介面
├── storage/GcsPhotoStorage.java                     # 唯一的實作，官方程式庫
├── storage/StorageConfig.java                       # 依 tms.storage.* 建 Storage；有模擬位址時建立儲存空間
├── repair/RepairPhotoContent.java                   # 上傳內容的檢查：空、JPEG 開頭、5 MB
├── repair/RepairController.java                     # PUT 讀 body；新增 GET /repair-photos/{id}/content；建立照片的輸入檢查
├── repair/RepairService.java                        # storeContent、readContent、requireAttachablePhotos；appendPhotos 補檢查
├── service/TaskExecutionService.java                # requireCompletionPhotos 改呼叫 RepairService 的共用檢查
├── config/SecurityConfig.java                       # GET /api/v1/repair-photos/*/content 多放行 duty_manager
├── dto/desk/DeskTaskDetail.java                     # repair 多 photos、completionPhotos
└── service/DeskBoardService.java                    # 組櫃檯詳情時帶工務單的照片清單
tms/src/test/java/com/athena/tms/
├── SharedContainers.java                            # 第三個共用容器 fake-gcs-server
├── bdd/BddSpringConfiguration.java 等四份 Spring 設定 # 各自的儲存空間名稱
├── bdd/support/BddApiClient.java                     # exchangeBytes：送收原始位元組
├── bdd/support/PhotoFixtures.java                   # 測試用 JPEG、PNG、空白、超大內容與照片標籤對照
└── bdd/steps/repair/                                # 新句型的步驟實作
app/mobile/
├── pubspec.yaml                                     # image_picker
├── ios/Runner/Info.plist                            # 相機與相簿權限說明
└── lib/
    ├── core/repair_photo_capture.dart               # 依選單打開相機或相簿；測試版回固定照片
    ├── core/api_client.dart                         # createRepairPhoto 帶拍攝時間；fetchRepairPhotoContent
    ├── core/repair_photo_image.dart                 # 依照片編號讀內容、記憶體快取、包成 MemoryImage
    ├── features/task/repair_sheet.dart              # 傳入照片選單的選擇
    ├── features/ticket/repair_ticket_form_sheet.dart
    ├── features/ticket/ticket_complete_sheet.dart
    ├── features/ticket/ticket_screen.dart           # 補照片傳入選擇；看大圖照照片清單
    ├── features/ticket/ticket_detail_engineering.dart   # 照片格子給圖片
    └── features/ticket/ticket_detail_housekeeping.dart
app/web/src/
├── api/repair-photos.ts                             # fetchPhotoBlob：帶 Authorization 讀照片位元組
├── api/types.ts                                     # DeskTaskDetail.repair.photos、completionPhotos
├── features/detail/RepairPhotoStrip.tsx             # 兩排縮圖與看大圖
├── features/detail/DetailPanel.tsx                  # 在「內容」與歷程之間放照片
└── i18n/locales/{zh-TW,en,ja,vi}.ts                 # detail.photos.*
app/bdd-support/.../BackendChannel.java               # 報修照片改經 API 建立並上傳真的 JPEG；舊版假照片
app/web-bdd/.../steps/detail/                        # 櫃檯 Web 照片句型
app/bdd/.../steps/repair/                            # 員工 App 照片顯示句型
```

**結構決策**：不新增資料表與欄位，不新增 Flyway 遷移。後端新增一個 `storage` 套件，只有一個介面與一個實作；照片的業務規則留在 `repair` 套件。App 不新增畫面。櫃檯 Web 新增一個照片元件，掛在既有的詳情面板裡。

## 分析流程規劃

### 系統介面的盤點

本次需求共盤點出 `11` 個系統介面。

1. `照片儲存與本機、測試環境`
   - 端點類型：`外部服務（雲端儲存）與開發環境`
   - 主要介面：`PhotoStorage` 的 put／open、`docker-compose.yml` 的 `fake-gcs-server`、`SharedContainers` 的測試容器、`tms.storage.*` 設定
   - 需求原文依據：`spec.md`「照片要存在哪裡，本機跟正式環境才會是同一套程式？」「照片在雲端儲存裡叫什麼名字？」

2. `資料：工務單照片`
   - 端點類型：`資料表`
   - 主要介面：`repair_photos.storage_ref`、`status` 的意義
   - 需求原文依據：`spec.md`「用到哪些資料？」

3. `後端：上傳照片內容`
   - 端點類型：`HTTP API`
   - 主要介面：`uploadRepairPhotoContent`（`PUT /repair-photos/{photoId}/content`）
   - 需求原文依據：`spec.md`「照片內容怎麼上傳？」、情境 3

4. `後端：讀照片內容`
   - 端點類型：`HTTP API`（新增）
   - 主要介面：`getRepairPhotoContent`（`GET /repair-photos/{photoId}/content`）
   - 需求原文依據：`spec.md`「照片怎麼讀回來？」、情境 1、5

5. `後端：建立照片`
   - 端點類型：`HTTP API`
   - 主要介面：`createRepairPhoto`（`POST /repair-photos`）
   - 需求原文依據：`spec.md`「建立照片時要檢查什麼？」、情境 6

6. `後端：完修`
   - 端點類型：`HTTP API`
   - 主要介面：`tasksComplete`（`POST /tasks/{id}/complete`）的完修照片
   - 需求原文依據：`spec.md`「完修附的照片要符合什麼？」、情境 2、4

7. `後端：補照片`
   - 端點類型：`HTTP API`
   - 主要介面：`appendRepairTicketPhotos`（`POST /repair-tickets/{ticketId}/photos`）
   - 需求原文依據：`spec.md`「補照片要符合什麼？」、情境 4

8. `後端：開單`
   - 端點類型：`HTTP API`
   - 主要介面：`createRepairTicket`（`POST /repair-tickets`）的照片
   - 需求原文依據：`spec.md`「開單附的照片要符合什麼？」、情境 4

9. `員工 App：拍照、選照片與顯示照片`
   - 端點類型：`行動 App 畫面`
   - 主要介面：照片選單、照片格子、看大圖、工務單詳情的兩個照片區塊
   - 需求原文依據：`spec.md`「App 怎麼拍照、選照片、顯示照片？」、情境 1、2

10. `後端：櫃檯詳情`
   - 端點類型：`HTTP API`
   - 主要介面：`readDeskTask`（`GET /desk/tasks/{id}`）的 `DeskTaskDetail.repair.photos`、`completionPhotos`；`getRepairPhotoContent` 的角色多一個 `duty_manager`
   - 需求原文依據：`spec.md`「照片怎麼讀回來？」「誰看得到照片？」、情境 7

11. `櫃檯 Web：詳情面板的照片`
   - 端點類型：`網頁畫面`
   - 主要介面：詳情面板的「報修照片」「完修照片」兩排縮圖與看大圖
   - 需求原文依據：`spec.md`「櫃檯 Web 怎麼顯示照片？」「櫃檯 Web 要怎麼顯示需要登入才看得到的照片？」、情境 7

### 分析流程的安排

#### Wave 1

- 平行分析介面：
  - `照片儲存與本機、測試環境`
  - `資料：工務單照片`
- 分析重點：
  - 物件名稱怎麼從飯店與照片編號組出來，`storage_ref` 存什麼
  - 模擬服務與正式雲端儲存只差哪幾個設定
- 安排理由：上傳與讀取都依賴物件名稱與存照片的介面；名稱定了，`storage_ref` 的意義才定得下來。

#### Wave 2

- 平行分析介面：
  - `後端：上傳照片內容`
  - `後端：讀照片內容`
  - `後端：建立照片`
- 分析重點：
  - 上傳的檢查順序與四個錯誤碼、HTTP 狀態
  - 讀照片的權限與找不到時一律 404
  - 建立照片的輸入錯誤改回 422
- 安排理由：三支都是照片本身的端點，只依賴 Wave 1 的儲存介面，彼此獨立。

#### Wave 3

- 平行分析介面：
  - `後端：完修`
  - `後端：補照片`
  - `後端：開單`
- 分析重點：
  - 三處共用一個「這些照片能不能附上」的檢查，差在用途與張數
  - 補照片補上 `ticket_closed`
- 安排理由：三處都要先知道「已上傳」在 Wave 2 之後代表內容真的存在，檢查才有意義。

#### Wave 4

- 平行分析介面：
  - `員工 App：拍照、選照片與顯示照片`
- 分析重點：
  - 測試版與正式版怎麼分流
  - 照片格子與看大圖怎麼拿到圖片
- 安排理由：App 讀照片依賴 Wave 2 的讀照片端點，上傳失敗的提示依賴 Wave 2 的錯誤碼。

#### Wave 5

- 平行分析介面：
  - `後端：櫃檯詳情`
  - `櫃檯 Web：詳情面板的照片`
- 分析重點：
  - 照片清單放在 `DeskTaskDetail.repair` 裡，跟工務單詳情同一套排序與「這一輪」規則
  - 瀏覽器怎麼帶登入憑證讀照片
- 安排理由：櫃檯詳情的照片清單沿用 Wave 3 之後的照片規則，櫃檯 Web 讀照片依賴 Wave 2 的端點與 Wave 5 的照片清單。

### 委派

- `/api-plan`：介面 3 至 8 與 10，改 `specs/truth/contracts/**`。
- `/data-plan`：介面 2，改 `specs/truth/data/data-model.dbml`。
- `/technical-research`：介面 1 與 9 的技術選型，改 `specs/truth/techstack.md`，研究在 `research.md`。
- `/ui-plan`：不委派。介面 9 沒有新畫面；介面 11 只在既有面板加一個區塊，規則與字句在 `spec.md`，櫃檯原型沒有畫照片，版面列為假設。
- `/dsl-refine`：介面 3 至 8 與 10 的後端行為規格；介面 9 在 Appium 主管視角加一條「驗收前看得到兩種照片」；介面 11 的櫃檯 Web 行為規格。

## 存照片的程式切在哪裡？

一種做法是在 `RepairService` 裡直接呼叫雲端儲存程式庫。程式最短，但業務規則（誰能上傳、已附上不能改）和連線細節（儲存空間、物件 API）混在同一個方法，測試業務規則時一定要有雲端儲存。

另一種做法是做一個通用的「檔案儲存」抽象，帶列出、刪除、中繼資料、臨時網址。這版只需要寫與讀，其他動作沒有呼叫者，做了只是沒人用的程式。

本版的介面只有兩個動作：

```java
public interface PhotoStorage {
    void put(String key, byte[] jpeg);
    Optional<byte[]> open(String key);
}
```

`GcsPhotoStorage` 是唯一的實作：`put` 用 `storage.create(BlobInfo.newBuilder(bucket, key).setContentType("image/jpeg").build(), jpeg)`，同名覆蓋；`open` 用 `storage.readAllBytes(bucket, key)`，找不到（程式庫回 404）時回 `Optional.empty()`，其他錯誤往外丟成 5xx。照片最大 5 MB，整張讀進記憶體再回給手機，比串流簡單，也讓「找不到」能在送出任何回應位元組之前判斷。

物件名稱由 `RepairService` 組：`"repair-photos/" + tenantId + "/" + photoId + ".jpg"`，跟 `storage_ref` 存同一個字串。讀照片時用資料庫裡的 `storage_ref`，不重新組名字，日後改命名規則時舊照片仍讀得到。

`StorageConfig` 依 `tms.storage.bucket` 與 `tms.storage.emulator-host` 建出 `com.google.cloud.storage.Storage`：模擬位址有值時用無憑證模式並在啟動時建立不存在的儲存空間，建立失敗（模擬服務沒起來、位址錯）就讓這個 bean 建立失敗，後端啟動失敗並在錯誤訊息寫出位址與儲存空間名稱；空值時用預設憑證、不建立儲存空間。這是全程式唯一一處看模擬位址的地方。

連線逾時與讀取逾時都設 10 秒，重試的總時限也是 10 秒（`HttpTransportOptions` 的 connect／read timeout 與 `RetrySettings.totalTimeout`）。程式庫預設的總時限是幾十秒，雲端儲存卡住時一個上傳請求會佔著資料庫連線那麼久；10 秒對一張 5 MB 以內的照片綽綽有餘。

```properties
# application.properties
tms.storage.bucket=${TMS_STORAGE_BUCKET:tms-repair-photos-local}
tms.storage.emulator-host=${TMS_STORAGE_EMULATOR_HOST:http://localhost:4443}
server.tomcat.max-swallow-size=6MB

# application-prod.properties
tms.storage.emulator-host=
```

預設值指向本機模擬服務，跟資料庫網址預設指向本機 5433 的做法一樣，開發者不用設任何環境變數。正式環境不靠「把環境變數設成空字串」：有些部署工具會把空字串的環境變數當成沒設，`${TMS_STORAGE_EMULATOR_HOST:…}` 就會落回預設值。改成新增 `application-prod.properties`，裡面寫 `tms.storage.emulator-host=`（空值），正式環境以 `prod` 設定組啟動，`TMS_STORAGE_BUCKET` 設成正式名稱。

預設值指向模擬服務是比較安全的方向：正式環境若漏了 `prod` 設定組，後端啟動時去連本機的 4443 埠建立儲存空間，連不上就啟動失敗，不會悄悄把照片寫到別處，也不會帶著壞掉的照片功能上線。後端啟動時記一行日誌寫出目前用的儲存空間與是否為模擬服務。

## 上傳怎麼檢查？

`uploadRepairPhotoContent` 今天沒有 body 參數（`repair/RepairController.java:64-67`），`RepairService.markUploaded` 把 `storage_ref` 寫成 `"upload:" + photoId`（`repair/RepairService.java:113-130`）。

不在 `@PutMapping` 宣告 `consumes = "image/jpeg"`：宣告了，格式不符時 Spring 自己回 415，body 是 Spring 的預設格式，不是本專案的 `Error`。改成控制器讀 `Content-Type` 標頭與 `HttpServletRequest` 的輸入串流，交給 `RepairPhotoContent.read(contentType, contentLength, inputStream)` 依序檢查：

1. `Content-Length` 有值且大於 5,242,880 → 413 `photo_too_large`，不讀 body。
2. 最多讀 5,242,881 位元組；讀到第 5,242,881 個 → 413 `photo_too_large`（沒有 `Content-Length` 的分塊傳送走這一條）。
3. 讀到 0 位元組 → 422 `photo_empty`。
4. `Content-Type` 不是 `image/jpeg`（忽略參數與大小寫），或前三個位元組不是 `FF D8 FF` → 415 `photo_type_unsupported`。

第 1、2 條提早回 413 時，請求 body 還沒讀完。Tomcat 回應之後會把沒讀完的 body 讀掉丟棄，上限是 `maxSwallowSize`，預設 2 MB；超過就直接關閉連線，用戶端看到的是連線被重設，收不到 413 的 body。所以 `application.properties` 設 `server.tomcat.max-swallow-size=6MB`：6 MB 以內的超大照片都收得到 `photo_too_large`，更大的請求才關連線，不讓惡意的大請求一直佔著連線。

檢查都過了才進 `RepairService.storeContent(photoId, bytes)`，同一個交易裡：

1. `SELECT … FOR UPDATE` 取照片列（飯店、編號）；沒有 → 404 `not_found`。
2. `uploaded_by` 不是呼叫者 → 403 `forbidden`。
3. `ticket_task_id` 不是空的 → 409 `photo_already_attached`。
4. `photoStorage.put(key, bytes)`。
5. `UPDATE repair_photos SET status = 'uploaded', storage_ref = key, uploaded_at = COALESCE(uploaded_at, now)`。

先寫雲端儲存、後寫資料庫：寫完雲端儲存後資料庫失敗，照片仍是 `pending`，重試寫同一個名字，結果一樣。反過來先標已上傳，雲端儲存失敗時就會出現「已上傳卻讀不到」，違反 spec 的不變條件。`FOR UPDATE` 讓同一張照片的上傳與開單綁定排隊，不會出現綁定之後才被換掉內容。

代價是寫雲端儲存的那段時間在交易裡，佔著一條 Hikari 資料庫連線。一張照片幾百 KB，寫進雲端儲存通常幾十到幾百毫秒；最壞情況被上面的 10 秒逾時擋住。連線池預設 10 條，同時有 10 張照片卡在雲端儲存時，其他請求要等連線。這版的流量（一家飯店同時上傳的照片個位數）承受得起；把寫雲端儲存移到交易外要另外處理「寫完雲端、交易前照片被別人附走」的情況，換來的只是少佔一點連線時間，不值得。日後流量真的上來，先加大連線池或把上傳改成先寫雲端、再開短交易確認。

錯誤碼要加進 `shared.yaml` 的 `Error` 說明，以及後端測試 `CommonThenSteps.ERROR_CODE_HTTP_STATUS` 的對照表。413、415 在 `responses.yaml` 新增 `PayloadTooLarge`、`UnsupportedMediaType` 兩個回應。

## 讀照片怎麼回？

新增 `GET /api/v1/repair-photos/{photoId}/content`。`GET /api/v1/**` 已經放行 `housekeeping`、`engineering`、`manager`、`frontdesk`（`config/SecurityConfig.java:76-77`），可是 `duty_manager` 是櫃檯 Web 的角色（`RolePermissions.frontdeskWebRoles()`），會被那條擋成 403，看得到櫃檯詳情卻看不到照片。所以在 `GET /api/v1/**` 之前加一條 `requestMatchers(HttpMethod.GET, "/api/v1/repair-photos/*/content").hasAnyRole("housekeeping", "engineering", "manager", "frontdesk", "duty_manager")`。`owner` 不在名單裡，回 403。

`RepairService.readContent(photoId)` 以目前飯店與編號查 `status = 'uploaded'` 的照片，查不到、或 `photoStorage.open(storage_ref)` 是空的，都回 404 `not_found`。回應 200、`Content-Type: image/jpeg`，body 是原始位元組。

`Cache-Control` 看照片有沒有附到工務單：附上了內容就不會再變，回 `private, max-age=86400, immutable`；還沒附上的照片建立者仍可以重傳，若也標成可以快取，重傳前後同一個網址會拿到舊內容，所以回 `private, no-cache`，每次都向後端確認。`private` 讓共用的代理伺服器不存。App 本來就依照片編號放記憶體快取，主要受益的是櫃檯 Web。

不另做「看得到這張工務單才看得到照片」的檢查，理由見 `spec.md`「誰看得到照片？」：`RepairService.read`（`repair/RepairService.java:178-181`）讀工務單時也只看飯店。

工務單詳情不加欄位。`ticketView` 已經回 `photos` 與 `completionPhotos`，每張有 `id`、`status`、`takenAt`（`repair/RepairService.java:386-390`、`417-452`），App 的 `RepairTicketDetail` 也已解析成 `RepairTicketPhoto`（`core/api_client.dart:918-928`、`937-949`）。契約把這兩個欄位改成必填，說明寫明 App 用 `id` 讀照片。

## 四處照片檢查怎麼共用？

開單、補照片、完修各自檢查照片，寫法不一：開單的 `requireUploadedPhotos` 檢查張數與已上傳（`repair/RepairService.java:673-690`）；補照片完全沒檢查（`appendPhotos`，`227-247`）；完修只看非空與不全是上一輪（`service/TaskExecutionService.java:335-356`）。三處最後都呼叫 `bindUnclaimedPhotos`，它對已附在別張單的照片什麼都不做（`474-480`），所以開單時張數會悄悄變少。

一種做法是三處各自補上缺的檢查。三份 SQL 會繼續分岔，下次再改照片規則又要記得改三處。

本版在 `RepairService` 收成一個方法：

```java
void requireAttachablePhotos(long tenantId, List<UUID> photoIds, String purpose, UUID ticketTaskId)
```

一句 `SELECT id, status, purpose, ticket_task_id, storage_ref FROM repair_photos WHERE tenant_id = ? AND id IN (…) ORDER BY id FOR UPDATE` 取出並鎖住這些照片，依序判斷：有編號查不到、不是 `uploaded`，或 `storage_ref` 不是 `repair-photos/` 開頭（這版之前的假照片）→ 422 `photo_not_uploaded`；有 `purpose` 不符 → 422 `photo_purpose_mismatch`；有 `ticket_task_id` 不是空也不是這張單 → 409 `photo_already_attached`。張數上下限仍由各路徑自己判斷，因為三處不同：開單 1 到 3、補照片 1 到 3、完修 0 到 3 且以要照片的結果完修時至少 1。

- 開單：`requireUploadedPhotos` 留張數檢查，已上傳的檢查換成 `requireAttachablePhotos(…, "report", null)`。
- 補照片：先查工務單狀態，`done`、`closed`、`cancelled` → 409 `ticket_closed`；再檢查 1 到 3 張與 `requireAttachablePhotos(…, "report", ticketTaskId)`。控制器原本只擋空陣列（`repair/RepairController.java:124-126`），超過 3 張的檢查放在服務裡，跟開單同一處。
- 完修：`TaskExecutionService.requireCompletionPhotos` 保留「至少 1 張」與「不能全是上一輪」，中間加 `repairService.requireAttachablePhotos(…, "completion", taskId)`。

鎖住照片列是為了兩個請求同時附同一張照片。只查不鎖時，兩個請求可能都查到「還沒附上」，各自綁定；後綁的那一次 `ticket_task_id IS NULL` 條件不成立、改到 0 筆，卻照樣回成功，那張單少一張照片，App 也不知道。`ORDER BY id` 讓多張照片永遠依同一個順序上鎖，兩個請求各拿一半的鎖互等（死結）不會發生。`requireAttachablePhotos` 與綁定必須在同一個交易裡，鎖才會留到綁定寫完；三個呼叫點（開單、補照片、完修）都已經在 `@Transactional` 方法裡。

`bindUnclaimedPhotos` 改成回傳改到的筆數：`UPDATE repair_photos SET ticket_task_id = :ticket WHERE tenant_id = ? AND id IN (…) AND (ticket_task_id IS NULL OR ticket_task_id = :ticket)`，呼叫者比對改到的筆數與 `photoIds` 去重後的張數，不相等就丟 409 `photo_already_attached`，整筆交易回滾。有鎖的時候這個比對不會失敗；它是第二道防線，日後有人漏了上鎖，結果仍是明確的 409，不是默默少一張。上傳內容的 `storeContent` 也是 `SELECT … FOR UPDATE` 同一列，所以「綁定」與「綁定前最後一次上傳」排隊進行。

舊版假照片用 `storage_ref` 的前綴判斷，不去雲端儲存查有沒有內容：查詢已經拿到 `storage_ref`，不用多一趟網路；正常流程產生的 `storage_ref` 一定是 `repair-photos/` 開頭。

補照片的順序是「先看單的狀態、再看照片」：單已修好時不論照片對不對都回 `ticket_closed`，App 只需要提示一件事。

## 建立照片的輸入怎麼檢查？

`createPhoto` 對 `takenAt` 直接 `OffsetDateTime.parse`（`repair/RepairService.java:98`），格式錯丟出 `DateTimeParseException` 變成 500；`purpose` 直接寫進資料庫，靠 V5 的 CHECK 擋，變成資料庫錯誤。

控制器在呼叫服務之前檢查：`purpose` 不是 `report` 或 `completion` → `UnprocessableEntityException("validation_failed", …)`，`details` 帶 `{field: purpose, issue: …}`；`takenAt` 有值但解析失敗 → 同樣 422，`details` 帶 `takenAt`。沿用 `GlobalExceptionHandler` 對 bean validation 的 `validation_failed` 格式，不新增錯誤碼。`purpose` 沒帶的既有 422 訊息改成同一個 code。

## App 怎麼接？

**拍照與選照片**：`core/repair_photo_capture.dart` 的 `captureAndUploadRepairPhoto` 多一個 `TmsPhotoSource source` 參數。測試版（`TEST_BACKDOOR`）照舊回固定的測試照片；正式版呼叫 `ImagePicker().pickImage(source: …, maxWidth: 1600, maxHeight: 1600, imageQuality: 80)`。使用者取消時回傳 `null`，呼叫者不建立照片。拿到檔案後檢查前三個位元組，不是 JPEG 就丟出新的 `RepairPhotoUnsupported`，呼叫者顯示「這張照片不能用」。拍攝時間用 `XFile.lastModified()`，拿不到用 `deviceNow()`，傳給 `ApiClient.createRepairPhoto(purpose:, takenAt:)`。

為了讓畫面測試能換掉相機，`captureAndUploadRepairPhoto` 改讀一個可替換的拍照函式（預設是上面的 `image_picker` 呼叫），測試替換成回傳固定位元組或 `null` 的函式；跟測試裡替換 `ApiClient.instance` 的做法一樣。

**四個呼叫者**：`features/task/repair_sheet.dart:109`、`features/ticket/ticket_complete_sheet.dart:63`、`features/ticket/repair_ticket_form_sheet.dart:121`、`features/ticket/ticket_screen.dart:363` 都已經拿到 `showTmsPhotoActionSheet` 的結果但沒用，改成傳進 `captureAndUploadRepairPhoto`；拍到的位元組同時存在 `RepairPhoto` 上，照片格子在上傳前就用 `MemoryImage` 顯示。

**權限被拒**：`image_picker` 在權限被拒時丟出 `PlatformException`，code 是 `camera_access_denied` 或 `photo_access_denied`。呼叫者接住後顯示 spec 的提示；其他平台錯誤照既有的上傳失敗處理。

**顯示照片**：新增 `core/repair_photo_image.dart`，提供 `repairPhotoImage(String photoId)` 回傳 `ImageProvider`。它向 `ApiClient.instance.fetchRepairPhotoContent(id)` 讀位元組，依照片編號放在記憶體快取，包成 `MemoryImage`；讀失敗時元件顯示「照片無法顯示」。`ticket_detail_engineering.dart:410-470` 與 `ticket_detail_housekeeping.dart:412` 的 `TmsPhotoCell` 給 `image:`；`ticket_screen.dart:347-356` 的看大圖改成依 `ticket.photos`／`completionPhotos` 逐張建 `TmsViewerPhoto(image: …)`，不再依 `photoCount` 畫空白頁。

**權限說明文字**：`ios/Runner/Info.plist` 加 `NSCameraUsageDescription`、`NSPhotoLibraryUsageDescription`。Android 不改 `AndroidManifest.xml`，理由見 `research.md`。

**離線佇列**：`core/connection_status.dart` 的佇列仍放在記憶體，照片位元組跟著閉包留到送出為止；App 關掉就沒了，這是 spec 列在本版不做的項目。

**主管驗收**：主管從待驗收清單（`features/manager/manager_queue_screen.dart:101`）以 `TicketEntry.verifyQueue` 打開 `TicketScreen`；`_managerLayout()`（`features/ticket/ticket_screen.dart` 約 419-437 行）沿用 `_engineeringLayout`，所以報修與完修的照片格子（`ticket_detail_engineering.dart` 約 410-470 行）和看大圖在「顯示照片」做完後自動有真的圖片，不另寫主管版。畫面測試要多一個主管從待驗收清單進來、兩種照片都有圖片的案例。照片格子的 `Semantics` 多帶 value（`loaded`、`loading`、`failed`），Appium 才分得出「有圖片」與「灰框」。

## 櫃檯 Web 怎麼接？

**照片清單**：`DeskTaskDetail`（`dto/desk/DeskTaskDetail.java`）的 `repair` 多 `photos` 與 `completionPhotos`，型別沿用 `RepairPhoto`。`DeskBoardService` 組詳情時，工務單呼叫 `RepairService` 組 `RepairTicket.photos`／`completionPhotos` 的同一段查詢（`repair/RepairService.java` 約 417-452 行），不另寫一份 SQL，排序與「這一輪」的規則自然一致。照片清單放在 `repair` 裡而不是 `DeskTaskDetail` 頂層：`repair` 本來就是「工務單才有的內容」，不是工務單時整個是 null，不必另外約定「非工務單回空陣列」。

**讀照片**：櫃檯 Web 的登入憑證在 session store，`api/client.ts:18-19` 的 `authHeaders()` 把它放進 `Authorization` 標頭；`<img src>` 帶不了標頭。新增 `api/repair-photos.ts` 的 `fetchPhotoBlob(photoId, signal)`：用 `fetch` 帶 `authHeaders()` 呼叫 `GET /api/v1/repair-photos/{id}/content`，回 `Blob`；非 200 丟出既有的 `ApiError`。元件拿到 `Blob` 後 `URL.createObjectURL` 給 `<img>`，元件卸載、換單或照片清單變了就 `URL.revokeObjectURL` 並中止還沒回來的請求。登入憑證不進任何網址。

**畫面**：新增 `features/detail/RepairPhotoStrip.tsx`，`DetailPanel.tsx:120-132` 的「內容」區塊之後、歷程之前放它；`task.repair` 為 null 時不渲染。兩排各自一個 `section`，標題沿用「內容」「歷程」那種小灰字（`text-[13px] text-text-2`），縮圖用面板的圓角卡片底色，每列 3 張、正方形、至少 44 × 44，放不下換列；讀取中是灰底，失敗是灰底加「照片無法顯示」。點縮圖打開看大圖：蓋住畫面的半透明底、照片置中、標題是那一排的名稱、「n / 總數」、「上一張」「下一張」「關閉」三顆按鈕，Esc 也能關；只在同一排切換。大圖沿用縮圖已經拿到的 `blob:` 網址，不重抓。字句加在 `i18n/locales/{zh-TW,en,ja,vi}.ts` 的 `detail.photos.report`、`detail.photos.completion`、`detail.photos.unavailable`、`detail.photos.viewer.*`，文字見 `spec.md`。

**即時更新**：面板開著時收到這張單的即時訊號就重讀 `readDeskTask`（既有行為），照片清單變了，元件依新的 id 清單重抓新增的照片、釋放移除的。

**測試資料**：`app/bdd-support` 的 `BackendChannel.insertReportPhotos` 今天直接寫 `repair_photos` 列、`storage_ref` 是 `fixture/{id}.jpg`，照片儲存裡沒有內容，櫃檯 Web 與 Appium 都會看到「照片無法顯示」。改成以報修人身分呼叫 `createRepairPhoto` 與 `uploadRepairPhotoContent` 上傳每張不同的合法 JPEG，再以 SQL 綁到工務單；完修照片本來就經 API 上傳（`createCompletionPhoto`），改成每張內容不同。另加「把某張照片改成舊版假照片」的方法（只改 `storage_ref`）。

## 本機與測試環境怎麼接？

**docker-compose**：照 Conduktor 那次的寫法，在檔頭說明加一段「照片存放（模擬雲端儲存）」，新增服務：

```yaml
  fake-gcs:
    image: fsouza/fake-gcs-server:<確切版本>
    container_name: tms-fake-gcs
    command: ["-scheme", "http", "-port", "4443", "-public-host", "localhost:4443", "-external-url", "http://localhost:4443"]
    ports:
      - "127.0.0.1:4443:4443"
    volumes:
      - tms-fake-gcs-data:/data
```

跟 Postgres、Kafka 一樣不放 profile，`docker compose up -d` 就起來，理由見 `spec.md` 假設。`scripts/manual-env/up.sh` 已經用 `docker compose up -d --wait` 帶起依賴服務，會一併帶起它；`env.sh` 加 `TMS_STORAGE_BUCKET=tms-repair-photos-manual`，跟驗收環境分開。健康檢查要用映像裡有的工具，實作時確認；沒有可用的工具就不寫 `healthcheck`，後端啟動建立儲存空間失敗會直接報錯。

**Testcontainers**：`SharedContainers` 加 `fakeGcs()` 與 `fakeGcsUrl()`，`GenericContainer` 用跟 compose 同一個映像版本、`-scheme http -port 4443`，對外埠由 Testcontainers 隨機對應。`-external-url` 只影響分段上傳，而存照片的程式只用一次送完的 `storage.create(BlobInfo, byte[])`，所以測試容器不設它。四份 Spring 設定在 `@DynamicPropertySource` 加：

```java
registry.add("tms.storage.emulator-host", SharedContainers::fakeGcsUrl);
registry.add("tms.storage.bucket", () -> "truth-photos");   // 每份設定一個名字
```

**測試用照片內容**：`bdd/support/PhotoFixtures.java` 產生幾種內容：合法 JPEG（`FF D8 FF E0` 開頭、`FF D9` 結尾，中間塞入照片標籤讓每張內容不同）、PNG（`89 50 4E 47` 開頭）、空白、5,242,881 位元組的 JPEG 開頭內容。

**測試的 HTTP 客戶端**：`bdd/support/BddApiClient.java:109-118` 只會送 JSON：body 一律 `objectMapper.writeValueAsString` 後以 `application/json` 送出，回應一律當 JSON 解析。今天送照片內容的四處步驟（`RepairGivenSteps.java:415`、`433`、`476`，`RepairWhenSteps.java:385`）傳的 `"fixture-jpeg-bytes"` 其實被包成 JSON 字串 `"\"fixture-jpeg-bytes\""`、標成 `application/json`，後端不讀 body 所以一直沒出事。新增 `exchangeBytes(method, path, auth, byte[] body, String contentType)`：body 原樣送出、`Content-Type` 照參數，回應保留狀態碼、標頭與原始位元組，不做 JSON 解析（錯誤回應的 JSON 由呼叫者需要時再解析）。所有上傳與讀照片的步驟都改用它，四處既有步驟改送 `PhotoFixtures` 的合法 JPEG，否則新的格式檢查會讓全部開單情境失敗。

## 程式現況跟本版差在哪裡、由誰補？

| # | 落差 | 現況 | 歸屬 |
| --- | --- | --- | --- |
| 1 | 照片內容沒存 | `RepairController.uploadRepairPhotoContent` 沒有 body 參數；`markUploaded` 寫 `"upload:" + photoId` | 後端；契約 |
| 2 | 讀不回照片 | 沒有讀照片內容的端點 | 後端新端點；契約 |
| 3 | 不檢查上傳內容 | 沒有格式、大小、空白的檢查；`application.properties` 沒有任何 body 大小設定 | 後端；契約 |
| 4 | 任何人都能替別人的照片上傳 | `markUploaded` 只看飯店與編號 | 後端；契約 |
| 5 | 已附上的照片還能換內容 | 同上 | 後端；契約 |
| 6 | 完修照片只看非空 | `TaskExecutionService.requireCompletionPhotos` | 後端；契約說明已寫「每張都要已上傳、purpose=completion」，後端沒照做 |
| 7 | 補照片不檢查 | `RepairService.appendPhotos` 只檢查是不是報修人或貢獻者；契約已寫 `ticket_closed` 與「都必須已上傳」，後端沒照做 | 後端；契約補用途與已附上的錯誤碼 |
| 8 | 開單會略過已附在別張單的照片 | `bindUnclaimedPhotos` 的 `ticket_task_id IS NULL` 條件 | 後端；契約 |
| 9 | 建立照片的輸入錯誤變 500 或資料庫錯誤 | `RepairService.createPhoto` 直接 parse 與寫入 | 後端；契約 |
| 10 | 沒有雲端儲存的依賴與設定 | `google-cloud-storage` 只由 `firebase-admin` 間接帶入 | 後端 `pom.xml`；techstack |
| 11 | 本機與測試沒有模擬雲端儲存 | `docker-compose.yml` 只有 Postgres、Kafka、Conduktor；`SharedContainers` 只有 Postgres、Kafka | 開發環境；techstack |
| 12 | App 送固定小圖 | `core/repair_photo_capture.dart` 的 `takePhotoJpeg` 永遠回 `_placeholderJpeg`；`pubspec.yaml` 沒有 `image_picker` | App |
| 13 | App 照片格子與看大圖沒有圖片 | `TmsPhotoCell` 與 `TmsViewerPhoto` 的 `image` 從沒被給值；看大圖照 `photoCount` 畫 | App |
| 14 | 沒有相機與相簿的權限說明 | `ios/Runner/Info.plist` 沒有兩個 `UsageDescription` | App |
| 15 | 契約與 techstack 寫了浮水印由 App 畫進照片、`flutter_image_compress` 壓縮 | 程式沒有這兩件事；這版也不做浮水印 | techstack、契約說明照現況改 |
| 16 | 同時附同一張照片可能兩邊都成功 | `requireUploadedPhotos` 只查不鎖；`bindUnclaimedPhotos` 不看改到幾筆 | 後端；契約說明 |
| 17 | 舊版假照片仍可附到新單 | 檢查只看 `status` | 後端；契約說明 |
| 18 | 提早回 413 可能變成連線被重設 | Tomcat `maxSwallowSize` 預設 2 MB | 後端設定 |
| 19 | 雲端儲存卡住時請求佔著資料庫連線很久 | 程式庫預設逾時幾十秒 | 後端 `StorageConfig` |
| 20 | 測試客戶端送不了原始位元組 | `BddApiClient` 一律 JSON | 後端測試 |
| 21 | 櫃檯詳情沒有照片 | `DeskTaskDetail` 沒有照片欄位；`DetailPanel` 沒有照片區塊 | 後端；契約；櫃檯 Web |
| 22 | 值班主管讀不到照片 | `GET /api/v1/**` 沒有 `duty_manager` | 後端 `SecurityConfig`；契約 |
| 23 | App 與櫃檯 Web 的測試資料沒有照片內容 | `BackendChannel.insertReportPhotos` 寫假的 `storage_ref` | `app/bdd-support` |

## 哪裡可能出錯？

- **既有測試會一起紅**：新的格式檢查讓送 `"fixture-jpeg-bytes"` 的步驟全部被 415 擋下，所有開單、補照片、完修的既有情境都會失敗。`tasks.md` 把換成合法 JPEG 排在 Phase 2，不能用放寬檢查讓它們變綠。
- **四份 Spring 設定都要接儲存設定**：漏掉任一份，那個套件啟動時會去連本機 4443 埠。四份都在 `tms/src/test/java/com/athena/tms/` 下，`tasks.md` 逐份列出。
- **模擬服務的網址設定**：`fake-gcs-server` 的 `-external-url` 與實際對應埠不一致時，分段上傳會失敗；程式只用一次送完的上傳就不受影響，日後若有人改用分段上傳，測試會先紅。實作時以 Setup 的冒煙測試確認寫入、讀出、覆寫三件事。
- **相簿照片的格式**：見 `research.md` 的風險。App 先檢查，再決定要不要加套件。
- **Appium 測試版仍送 1 × 1 圖**：模擬器上的行為測試照舊通過，但它們證明不了相機真的打得開。相機與相簿只能在實機上手動驗證，`tasks.md` 的最後一段列出驗證步驟。
- **正式環境的設定組**：正式環境要以 `prod` 設定組啟動才會關掉模擬服務；沒帶時後端啟動失敗（連不上 4443），不會悄悄壞掉。部署文件要寫明。
- **同時附同一張照片**：鎖定的效果很難用自動化情境穩定重現，以程式碼審查確認 `FOR UPDATE`、`ORDER BY id` 與改到筆數的比對都在；單元測試可以直接呼叫綁定方法、讓改到的筆數少於張數，確認回 409。
- **櫃檯 Web 的記憶體**：每張照片轉成 `blob:` 網址後佔著記憶體，忘了釋放會越用越多；Vitest 驗證卸載與換單時都有釋放。
- **舊的假照片**：手動測試環境與驗收環境裡已經有 `storage_ref` 是 `upload:…` 的照片，讀取一律 404，App 顯示「照片無法顯示」。這是預期結果；要乾淨的畫面就重置環境。
