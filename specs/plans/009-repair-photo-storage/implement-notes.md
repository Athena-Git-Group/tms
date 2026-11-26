# 009 實作紀錄

## T001 改動前的基準（分支起點 f23290e）

- 後端全套：surefire 合計 742 run、1 failure、30 errors；其中 `RunTruthFeaturesTest` 525 run、3 failures、38 errors。
  失敗全是本版新加、步驟還沒實作的情境（undefined step）與兩條舊行為與新 truth 不符的情境：
  - `工務單/照片內容上傳與查看.feature`、`照片完整性與冪等送出.feature`、`重複報修補照片與撤回.feature` 的新情境（undefined）。
  - `重複報修補照片與撤回.feature`「阿美一次補 4 張照片到 W-0410」：補 4 張回 200（後端沒擋超過 3 張）。
  - `櫃檯看板與派單/單筆詳情與歷程.feature`「小美處理中的 305 備品單」等照片句（undefined）。
  - 33 個失敗的測試案例名稱存成清單，跟改動後逐一比對；本版以外的情境沒有任何失敗。
- `flutter test`：527 通過、0 失敗。
- `npx vitest run`（app/web）：771 通過、0 失敗。
- `app/bdd/run-module.sh 工務單` 與 `app/web-bdd/run-feature.sh 單筆詳情 desktop|ipad`：本棒（後端）沒有跑。
  理由：兩支都要連本機共用的後端與資料庫（web-bdd 預設起 8080 並在每個場景清空 5433/tms 的業務資料，
  Appium 要獨佔模擬器），這兩組埠與模擬器此時有別的 session 在用，跑了會清掉別人的資料。
  接手 App 與櫃檯 Web 的那一棒，請以 `git worktree add <路徑> f23290e` 取得改動前的程式跑基準，再與自己的結果比。

## T003 本機 docker-compose

- 映像 `fsouza/fake-gcs-server:1.56.1`（2026-08-24 發布，`docker pull` 確認存在）。映像裡有 `wget`，所以加了 `healthcheck`（打 `/storage/v1/b`）。
- `docker compose config` 通過；只起 `fake-gcs` 一個服務，健康檢查轉成 healthy、`/storage/v1/b` 回 200。

## T004 測試容器接到哪些 Spring 設定

- `tasks.md` 列了四份，實際有八份 Spring context 會載入整個後端：除了那四份，還有 `relayfairness/RelayFairnessSpringConfiguration`、
  `config/KafkaTopologyTest`，以及走 `PostgresSpringTest.wireDatabase` 的 `config/TestClockEnabledTest`、`service/HotelRelayTurnTest`。
  漏接的那幾份會去連本機 4443，`StorageConfig` 起不來就整份失敗，所以八份都接。共用的 `SharedContainers.wireStorage(registry, 儲存空間)` 一行接好。
- 儲存空間名稱：`truth-photos`、`integration-photos`、`relaywake-photos`、`relayfairness-photos`、`kafkatopology-photos`；
  `PostgresSpringTest.wireDatabase` 依資料庫名稱取名（`junit-photos`、`test-support-photos`、`relayturn-photos`），
  所以 `PostgresSpringTest` 本身是 `junit-photos`，不是 `tasks.md` 寫的 `postgres-photos`。名稱只要各份不同即可，不影響行為。

## T005 冒煙測試

- `FakeGcsSmokeTest` 對 Testcontainers 起的模擬服務：寫入、讀出、同名覆寫、讀不存在得 404，全綠。
- 同一支以 `-DargLine="-Dapi.version=1.44 -Dfakegcs.url=http://localhost:4443"` 改打 `docker compose up -d fake-gcs` 起的本機服務：同樣全綠。
- 後端全套：743 run、1 failure、30 errors；失敗清單與 T001 逐筆相同（多的一筆是冒煙測試本身，通過）。

## T010 App 與櫃檯 Web 共用的照片測試資料

- `BackendChannel` 記下自己建立過的員工（編號 → 帳密），`insertReportPhotos(ticketTaskId, uploaderId, count)` 簽名不變，
  以上傳者身分經 API 建立、上傳，再以 SQL 綁定。上傳者必須是同一個 `BackendChannel` 建立的員工，否則丟出說明「不知道他的帳密」。
- 測試照片是 1 × 1 的真 JPEG，在開頭標記後插一段 JPEG 註解寫進序號：每張位元組不同，`sips` 解得開，App 與瀏覽器才顯示得出來
  （只塞亂數的「FF D8 FF … FF D9」過得了後端檢查，但畫面上會是「照片無法顯示」）。
- 新增 `replaceRepairPhotos`、`markLegacyPhoto`、`repairPhotoIds`（排序同工務單詳情：拍攝時間、編號）、`photoContent`。
- 驗證：`mvn -q -f app/bdd-support/pom.xml install -DskipTests` 通過；`app/bdd`、`app/web-bdd` 的 `test-compile` 都通過。
  注意這一步把新版 `app-bdd-support` 裝進了共用的 `~/.m2`。

## T006 至 T009

- T006：`StorageConfigTest` 兩案綠（模擬位址指向沒人聽的埠時 context 起不來、錯誤訊息含位址與儲存空間名稱；`prod` 設定組下 `tms.storage.emulator-host` 是空值）。
  連不上時要等滿 10 秒的重試總時限才失敗，所以這支測試約 10 秒。
- T008：四處既有上傳改送 `PhotoFixtures.jpeg(照片編號)`，`grep -rn "repair-photos" tms/src/test/java` 之外沒有其他送照片內容的地方。
- T008／T009 驗證：後端全套 745 run、1 failure、30 errors，失敗清單與 T001 逐筆相同。

## Phase 3 後端步驟（T011–T031）的解讀

dsl 沒寫死、照最小解讀做的地方：

1. T024「上一步是上傳時回應要是 200」的「上一步」是指緊接在這句之前的那一步（用 `@BeforeStep` 計數）；接在被拒絕的上傳與「這次操作被拒絕」之後時不檢查。
2. T024 以「建立照片的員工」、T030 以「處理人」再讀照片時，用 `JwtService.issue` 依 `staff_accounts` 發登入憑證，別家飯店的員工也一樣。
3. T015「以他的身分登入」直接發登入憑證，不打登入端點；別家飯店是 `TenantFixtures.createTenant("別家飯店", "other-hotel")` 加一位在職工務。
4. T030「這一輪的完修照片」用 `COALESCE(uploaded_at, created_at) > last_verify_failed_at`（沒有不通過紀錄就全算），與 `RepairService.completionPhotos` 同一條規則。
5. T026「查看不改任何資料」比對 `repair_photos`、`tasks` 的 md5 與 `task_events`、`outbox` 的筆數（outbox 會被背景 relay 更新，所以只比筆數）。
6. T031「回應裡沒有照片編號」走訪整個回應，任何非空的 `photos`、`completionPhotos`、`photoIds`、`photoId` 都算失敗。
7. T018、T020 呼叫前的 `repair_photos` 筆數算整張表，不分飯店。
8. T027 的時刻比到分鐘，並要求與 T012 記下的 `uploaded_at` 完全相同。

順手修的既有問題：`已有以下工務單：` 的「已撤回」列原本呼叫 `engine.cancel("staff_withdrew")`，任務引擎不收這個原因；
改走 `engine.withdrawFromPool`（`TaskFixtures`），撤回後補照片的情境才跑得到產品行為。

## T048 審查

- 第一輪審查（獨立 subagent）找到一個缺口：附照片被拒絕時（T021 完修、T022 補照片、T023 開單），沒有檢查照片列不變；
  產品若先綁照片再回 4xx 而沒有回滾，這幾個情境照樣會過。修法：`LastOperationGuard.capturePhotos` 記下照片的
  `ticket_task_id`、`status`、`storage_ref`、`uploaded_at`，`這次操作被拒絕…` 時比對。
- 修正後重跑：`工務單` 模組 120 run、28 failures、0 errors；`單筆詳情與歷程.feature` 16 run、2 failures；
  dry-run undefined 與 ambiguous 都是 0。失敗全是產品還沒改的行為（上傳仍回 200、沒有讀照片端點回 405、
  建立照片輸入錯誤回 500、附照片沒檢查、櫃檯詳情沒有照片），沒有步驟例外。審查判定其餘八項解讀與 dsl 一致。
- 提交分兩次：Phase 1–2 的環境與存照片介面一次，Phase 2–3 的後端測試層一次（`tasks.md` 只寫了後者；
  前者沒有指定提交點，跟著先提交，Phase 4 的提交才只含產品碼）。

## T049 上傳與讀照片

- `照片內容上傳與查看.feature` 25 run 全綠。
- 超大照片提早回 413 的手動檢查改用同一個情境對照：「阿美上傳一張沒壓縮的大照片」送 5,242,881 位元組（只多 1 位元組，卻已超過 Tomcat 預設可讀掉的 2 MB），
  以 `-Dserver.tomcat.max-swallow-size=2MB` 跑，用戶端拿到 I/O 錯誤（連線被切，收不到回應）；用設定檔的 6MB 跑，收到 413 與 `photo_too_large`。
  沒有另起後端用 `curl` 送 5.5 MB：要起後端得連本機共用的 5433 資料庫與 9092 Kafka，而它們正被別的 session 使用；
  5.5 MB 與這個情境走同一條路（`Content-Length` 超過上限就不讀 body，剩下的交給 Tomcat 讀掉），差別只在大小仍在 6 MB 內。
- `Cache-Control` 照 dsl 的字串原樣回（`private, max-age=86400, immutable`、`private, no-cache`）；Spring 的 `CacheControl` 會把順序排成 `no-cache, private`，所以直接寫標頭。

## Phase 4A–4C

- T050：`markUploaded` 與 `"upload:"` 假值已刪；`PhotoStorage` 只有 `GcsPhotoStorage` 一個實作，看模擬位址只有 `StorageConfig` 一處；`readContent` 對舊列只認 `repair-photos/` 前綴。
- T051：`照片完整性與冪等送出.feature` 16 run、`驗收不通過與重修.feature` 9 run 全綠。`RepairPhotoAttachTest`（`PostgresSpringTest` 底下）兩案綠：
  事先把其中一張照片綁到別張單、直接呼叫綁定方法，回 409 `photo_already_attached` 且另一張也沒綁上（交易回滾）；
  兩個執行緒同時以同一張照片開單，恰好一次成功、一次 409。為了讓測試直接呼叫，`bindUnclaimedPhotos` 由 private 改成同套件可見。
- T052：開單（`requireUploadedPhotos` 只留張數，已上傳的判斷交給共用檢查）、補照片、完修三處都只透過 `requireAttachablePhotos`；
  三個呼叫點都在 `@Transactional` 方法裡（`create`、`appendPhotos`、`TaskExecutionService.complete`）。
  `TaskExecutionService` 仍有一句查 `repair_photos`，那是「完修照片不能全是上一輪」的既有規則，照 plan 保留。
- T053：`重複報修補照片與撤回.feature` 12 run 全綠。
- T054 後端全套：767 run、2 failures、0 errors。剩下的兩個都是 `單筆詳情與歷程.feature` 的櫃檯詳情照片（T063 的範圍，T001 基準就已失敗）；
  基準其餘 31 個失敗都轉綠，沒有新失敗。
