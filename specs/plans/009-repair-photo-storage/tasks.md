# Tasks: 工務單照片拍了就是真的，存得住、看得到

**Plan Package**: `specs/plans/009-repair-photo-storage`
**Core Inputs**: `spec.md`, `plan.md`, `research.md`, `truth-delta.md`, `specs/truth/techstack.md`, `specs/truth/contracts/**`, `specs/truth/data/data-model.dbml`, `specs/truth/features/backend/**`, `specs/truth/features/ios-app/**`, `specs/truth/features/frontdesk-web/**`

本檔照實際動工的順序排，每一段結束都有一個檢查點：

1. Phase 1、2：本機與測試環境接上模擬雲端儲存，存照片的程式介面、送收原始位元組的測試客戶端、App 與櫃檯 Web 共用的測試資料就位。檢查點是冒煙測試寫入、讀出、覆寫都成立，既有後端全套照基準。
2. Phase 3：後端、員工 App、櫃檯 Web 三個介面本輪用到的句型都有步驟實作。檢查點是三邊 dry-run 都沒有 undefined。
3. Phase 4A 至 4C：後端照片行為變綠。檢查點是三份工務單 truth feature 全綠，後端全套照基準。
4. Phase 4D、4E：員工 App 拍照、選照片、顯示照片，主管驗收看得到照片。檢查點是 `flutter test` 全綠、Appium 主管視角全綠。
5. Phase 4F：櫃檯 Web 的詳情面板顯示照片。檢查點是後端櫃檯詳情、Vitest 與櫃檯 Web 行為規格全綠。
6. Phase 4G、5：plan-side 審查、Appium 回歸與實機手動驗證。

`ui/` 本版不存在，理由見 `plan.md` 開頭。

## Task Binding Contract

- 每個開發任務都對應 `truth-delta.md` 中的 ADD / MODIFY / DELETE / NOOP 語意。
- Phase 1 `Setup` 只做本輪新增技術的基礎建設、技術環境與最後的冒煙測試；不寫 DSL 語意、不寫產品行為。
- Phase 2 `Foundational` 只建立後續實作程式、測試共用元件、入口、fixture、helper 與落點骨架。
- Phase 3 `Test Alignment & Implementation` 的目的：在寫產品碼之前，先把本輪所有受影響 DSL 的自動化測試對齊最新版 truth。
- Truth 參照使用 `specs/truth/**` 路徑；plan 參照使用當前 plan package 內相對路徑。
- Feature `Shared Must Read` 除 feature、同模組 DSL、對應 `truth-delta` 列外，還列入回追到的 planner 產物。
- 程式路徑省略前綴時：後端產品碼是 `tms/src/main/java/com/athena/tms/`，後端 stepdef 是 `tms/src/test/java/com/athena/tms/bdd/`，員工 App 是 `app/mobile/lib/`，App stepdef 是 `app/bdd/src/test/java/com/athena/app/bdd/`，櫃檯 Web 是 `app/web/src/`，櫃檯 Web stepdef 是 `app/web-bdd/src/test/java/com/athena/web/bdd/`，共用後端資料準備是 `app/bdd-support/src/main/java/com/athena/bddsupport/`。

## 怎麼跑測試？

每一題的驗證都用下面這幾條，不另寫。

- 後端全套（JDK 25、OrbStack）：
  `JAVA_HOME="$(/usr/libexec/java_home -v 25)" TESTCONTAINERS_RYUK_DISABLED=true ./tms/mvnw -f tms/pom.xml test -DargLine="-Dapi.version=1.44"`
- 只跑後端行為規格：上一條加 `-Dtest=RunTruthFeaturesTest`；只跑一份檔再加 `-Dcucumber.features=../specs/truth/features/backend/<模組>/<檔>.feature`；找沒有實作的句子再加 `-Dcucumber.execution.dry-run=true -Dsurefire.failIfNoSpecifiedTests=false`。只跑單一 feature 時多出的 CucumberTestEngine discovery 錯誤可以忽略。
- 員工 App：`cd app/mobile && flutter test`；分析 `cd app/mobile && flutter analyze`。
- 員工 App 行為規格：`app/bdd/run-module.sh 工務單`（模擬器一次只給一個 agent 用）。dry-run：`JAVA_HOME="$(/usr/libexec/java_home -v 25)" mvn -f app/bdd/pom.xml test -Dtest=RunModuleFeatureTest -Dsurefire.excludeJUnit5Engines=cucumber -Dcucumber.execution.dry-run=true -Dcucumber.features=../../specs/truth/features/ios-app/工務單 -Dsurefire.failIfNoSpecifiedTests=false`。
- 櫃檯 Web 單元測試：`cd app/web && npx vitest run`；建置：`cd app/web && npm run build`。
- 櫃檯 Web 行為規格：`bash app/web-bdd/run-feature.sh [--keep] [--rebuild] 單筆詳情/工務單照片.feature [desktop|ipad]`，跑完 `--stop`；後端產品碼改過要帶 `--rebuild`。dry-run：`JAVA_HOME="$(/usr/libexec/java_home -v 25)" mvn -f app/web-bdd/pom.xml test -Dtest=RunFrontdeskWebFeaturesTest -Dsurefire.excludeJUnit5Engines=cucumber -Dcucumber.execution.dry-run=true -Dcucumber.features=../../specs/truth/features/frontdesk-web/單筆詳情 -Dsurefire.failIfNoSpecifiedTests=false`（先 `mvn -q -f app/bdd-support/pom.xml install -DskipTests`）。
- App 與櫃檯 Web 的行為規格都連本機後端，後端從這版起要有 `fake-gcs-server`：先 `docker compose up -d`。
- 同一個 worktree 裡有多個 agent 時，Maven 本機倉庫與模擬器不要共用：各自用 `-Dmaven.repo.local=<自己的路徑>`，模擬器排隊用。

「全綠」都是指「除了 T001 記下的既有失敗之外全綠」。

## Phase 1: Setup

**Goal**: 後端有官方雲端儲存程式庫，本機 docker-compose 與後端測試都有 `fake-gcs-server`，冒煙測試證明寫入、讀出、覆寫在模擬服務上成立。

- [X] T001 量改動前的基準
  - Read:
    - `specs/truth/techstack.md` -> 測試與驗證
  - 在任何產品碼或測試碼改動之前，跑後端全套、`flutter test`、`app/bdd/run-module.sh 工務單`、`cd app/web && npx vitest run`、`bash app/web-bdd/run-feature.sh 單筆詳情 desktop` 與 `ipad`，把每一組的失敗清單（測試類別與方法，或 feature 檔名與行號）與失敗數記到 `implement-notes.md`。
  - 不修任何既有失敗。

- [X] T002 加入雲端儲存程式庫與設定項目
  - Read:
    - `specs/truth/techstack.md` -> 後端「照片儲存」列
    - `research.md` -> 後端用哪個程式庫接雲端儲存？
    - `plan.md` -> 存照片的程式切在哪裡？、上傳怎麼檢查？
  - `tms/pom.xml` 明確宣告 `com.google.cloud:google-cloud-storage`，版本先跑 `JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./tms/mvnw -f tms/pom.xml -q dependency:tree -Dincludes=com.google.cloud:google-cloud-storage` 確認 `firebase-admin` 帶進來的版本（分支起點是 2.64.0），寫同一版。
  - `tms/src/main/resources/application.properties` 加 `tms.storage.bucket=${TMS_STORAGE_BUCKET:tms-repair-photos-local}`、`tms.storage.emulator-host=${TMS_STORAGE_EMULATOR_HOST:http://localhost:4443}` 與 `server.tomcat.max-swallow-size=6MB`（提早回 413 時 Tomcat 讀掉剩下的 body，用戶端才收得到回應，理由見 `plan.md`）；`application-test.properties` 不設儲存項目，由各 Spring 設定動態給。
  - 新增 `tms/src/main/resources/application-prod.properties`，內容只有 `tms.storage.emulator-host=`（空值）：正式環境以 `prod` 設定組啟動就不接模擬服務，不靠把環境變數設成空字串。`README.md` 的部署或設定段補一句。
  - 不寫存照片的程式。

- [X] T003 本機 docker-compose 加 fake-gcs-server
  - Read:
    - `specs/truth/techstack.md` -> 本機環境
    - `docker-compose.yml` -> 檔頭說明與 Conduktor 的寫法
    - `plan.md` -> 本機與測試環境怎麼接？
  - 新增 `fake-gcs` 服務（`fsouza/fake-gcs-server:<確切版本>`，先 `docker pull` 確認該 tag 存在；`-scheme http -port 4443 -public-host localhost:4443 -external-url http://localhost:4443`；`127.0.0.1:4443:4443`；volume `tms-fake-gcs-data:/data`），不放 profile。
  - 檔頭說明加一段：照片存在這個模擬服務、預設儲存空間名稱、`down -v` 會清掉照片。`README.md` 的本機環境段同步一句。
  - `scripts/manual-env/env.sh` 匯出 `TMS_STORAGE_BUCKET=tms-repair-photos-manual`，讓手動測試環境（8083）跟驗收環境用不同儲存空間。
  - 確認 `app/web-bdd/run-feature.sh` 與 `app/bdd/run-module.sh` 起後端前，本機的 `fake-gcs` 已經在跑；沒有就照腳本既有的依賴檢查方式補一句提示（不改腳本的其他行為）。
  - 不改 Postgres、Kafka、Conduktor 的設定。

- [X] T004 後端測試加共用的 fake-gcs-server 容器
  - Read:
    - `specs/truth/techstack.md` -> 後端 E2E 依賴
    - `tms/src/test/java/com/athena/tms/SharedContainers.java`
  - `SharedContainers` 加 `fakeGcs()` 與 `fakeGcsUrl()`：`GenericContainer`，映像版本與 T003 相同，`-scheme http -port 4443`，對外埠由 Testcontainers 對應，整個 JVM 只起一個。
  - 四份 Spring 設定的 `@DynamicPropertySource` 加 `tms.storage.emulator-host` 與各自的 `tms.storage.bucket`：`bdd/BddSpringConfiguration.java`（`truth-photos`）、`integration/stepdefs/CucumberSpringConfiguration.java`（`integration-photos`）、`relaywake/RelayWakeSpringConfiguration.java`（`relaywake-photos`）、`PostgresSpringTest.java`（`postgres-photos`）。
  - 不寫業務測試。

- [X] T005 冒煙測試：在模擬服務上寫入、讀出、覆寫
  - Read:
    - `research.md` -> 本機與自動化測試怎麼模擬雲端儲存？
  - 新增 `tms/src/test/java/com/athena/tms/storage/FakeGcsSmokeTest.java`：用 T004 的容器與官方程式庫（`setHost`、`NoCredentials`）建立儲存空間、`storage.create` 寫一份位元組、`readAllBytes` 讀回相同、同名再寫一份後讀回第二份、讀不存在的名字得到 404。
  - 再以 `docker compose up -d` 起本機服務，對 `http://localhost:4443` 跑同一段（手動或同一測試以系統屬性切換），結果記到 `implement-notes.md`。
  - 驗證：冒煙測試綠；後端全套與 T001 基準比沒有新失敗（此時產品碼還沒用到儲存，四份 Spring 設定只是多了設定值）。

## Phase 2: Foundational

**Goal**: 存照片的介面與唯一實作、送收原始位元組的測試客戶端、測試用照片內容、App 與櫃檯 Web 共用的照片測試資料、新錯誤碼對照就位；既有測試改送合法 JPEG，後端全套仍照基準。

- [X] T006 存照片的介面與實作
  - Read:
    - `truth-delta.md` -> `/technical-research` 後端「照片儲存」列
    - `plan.md` -> 存照片的程式切在哪裡？、上傳怎麼檢查？（交易與連線池的權衡）
  - 只做：
    - `storage/PhotoStorage.java`（`put(String key, byte[] jpeg)`、`Optional<byte[]> open(String key)`）。
    - `storage/GcsPhotoStorage.java`（官方程式庫；`put` 帶 `Content-Type: image/jpeg` 同名覆蓋；`open` 遇 404 回空、其他錯誤往外丟）。
    - `storage/StorageConfig.java`：依 `tms.storage.*` 建 `Storage`；`HttpTransportOptions` 的連線與讀取逾時各 10 秒、`RetrySettings.totalTimeout` 10 秒（模擬服務與正式環境都一樣）。模擬位址有值時用無憑證模式並建立不存在的儲存空間，**建立失敗就讓這個 bean 建立失敗、後端啟動失敗**，錯誤訊息寫出模擬位址與儲存空間名稱；空值時用預設憑證、不建立。啟動時記一行日誌寫出儲存空間名稱與是否為模擬服務。
    - 一支 `StorageConfig` 的測試：模擬位址指向沒有服務的埠時，Spring context 起不來，錯誤訊息含該位址；`prod` 設定組下 `tms.storage.emulator-host` 是空值。
  - 不做：在 `RepairService` 呼叫它；任何上傳或讀取端點。

- [X] T007 送收原始位元組的測試客戶端、測試用照片內容與照片標籤
  - Read:
    - `plan.md` -> 本機與測試環境怎麼接？（「測試的 HTTP 客戶端」段）
    - `specs/truth/features/backend/工務單/dsl.md` -> 「照片內容與存放」段、`"{員工}" 以 "{內容}" 上傳照片 "{照片}" 的內容` 的內容對照
    - `tms/src/test/java/com/athena/tms/bdd/support/BddApiClient.java:95-125`
  - 只做：
    - `bdd/support/BddApiClient.java` 新增 `exchangeBytes(HttpMethod method, String path, Auth auth, byte[] body, String contentType)`：body 原樣送出（`null` 時不送 body）、`Content-Type` 照參數，回應保留狀態碼、所有標頭與原始位元組，不做 JSON 解析；同樣寫進 `world.setLastResponse`（`ScenarioWorld.ApiResponse` 加一個 `byte[] bytes` 欄位，`raw`／`json` 只在回應的 `Content-Type` 是 JSON 時才填），讓既有的 `這次操作被拒絕，錯誤碼為 …` 照樣讀得到錯誤 body。既有的 JSON `exchange` 不改。
    - `bdd/support/PhotoFixtures.java`：依（照片標籤、內容名稱）產生位元組（合法 JPEG 帶標籤、另一張 JPEG、剛好 5 MB、超過 5 MB、PNG、標成 JPEG 的 PNG、標成 PNG 的 JPEG、空白）與對應的 `Content-Type`。
    - `ScenarioWorld` 加照片標籤對照（標籤 → 照片 id、最後一次成功上傳的位元組、`uploaded_at`）。
    - 一個直接讀寫照片儲存物件的測試用 helper（讀物件位元組與 `Content-Type`、刪除物件），給 Then 比對與「舊版的假照片」用。註冊進 `BddSpringConfiguration`。
  - 不做：步驟實作。

- [X] T008 既有上傳步驟改用 `exchangeBytes` 送真的 JPEG 位元組
  - Read:
    - `truth-delta.md` -> `/dsl-refine`「模組說明『照片內容與存放』段」MODIFY 列
    - `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairGivenSteps.java`（約 415、433、476 行）、`RepairWhenSteps.java`（約 385 行）
  - 現況：這四處把字串 `"fixture-jpeg-bytes"`／`"fixture-jpeg-bytes-retry"` 當 body 交給 JSON 的 `exchange`，實際送出的是 JSON 字串、`Content-Type: application/json`；後端今天不讀 body 才沒出事。
  - 只做：四處改成 `exchangeBytes(PUT, "/repair-photos/{id}/content", auth, PhotoFixtures 的合法 JPEG, "image/jpeg")`，每張內容不同；其他模組（`steps/`、`integration/`）若有送照片內容的地方一併改，以 `grep -rn "repair-photos" tms/src/test/java` 盤點。
  - 不做：改句型或斷言。
  - 驗證：後端全套照基準（產品碼還不檢查格式，這一步只是讓下一段不會整批被 415 擋下）。

- [X] T009 錯誤碼對 HTTP 狀態表
  - Read:
    - `specs/truth/features/backend/dsl.md` -> `這次操作被拒絕，錯誤碼為 "{錯誤碼}"`
    - `specs/truth/contracts/components/schemas/shared.yaml` -> `Error`
  - 只做：`bdd/steps/common/CommonThenSteps.java` 的 `ERROR_CODE_HTTP_STATUS` 加 `photo_purpose_mismatch`=422、`photo_already_attached`=409、`photo_empty`=422、`photo_type_unsupported`=415、`photo_too_large`=413，以及 `not_found`=404、`validation_failed`=422、`photo_limit`=422、`ticket_closed`=409（若還沒有）。
  - 不做：改斷言邏輯。

- [X] T010 App 與櫃檯 Web 共用的照片測試資料
  - Read:
    - `plan.md` -> 櫃檯 Web 怎麼接？（「測試資料」段）
    - `specs/truth/features/ios-app/dsl.md` -> `已有以下工務單：`
    - `specs/truth/features/frontdesk-web/單筆詳情/dsl.md` -> 模組說明「工務單照片」段
    - `app/bdd-support/src/main/java/com/athena/bddsupport/BackendChannel.java`（`insertReportPhotos` 約 1379 行、`createCompletionPhoto` 約 1444 行）
  - 只做：
    - `insertReportPhotos(ticketTaskId, uploaderId, count)` 改成以上傳者身分呼叫 `createRepairPhoto` 與 `uploadRepairPhotoContent`（每張是不同的合法 JPEG），再以 SQL 把 `ticket_task_id` 設成這張單；不再寫 `fixture/{id}.jpg`。
    - `createCompletionPhoto` 改成每張內容不同的合法 JPEG（序號寫進內容）。
    - 新增 `replaceRepairPhotos(ticketTaskId, reportCount, completionCount)`（先刪這張單已綁的照片列，再以報修人與處理人身分建立、上傳、綁定）、`markLegacyPhoto(photoId)`（只把 `storage_ref` 改成 `upload:{id}`）、`repairPhotoIds(ticketTaskId, purpose)`、`photoContent(photoId, staff)`（以該員工呼叫 `getRepairPhotoContent` 回位元組）。
  - 不做：任何 stepdef。
  - 驗證：`mvn -q -f app/bdd-support/pom.xml install -DskipTests` 通過；這一步之後到 Phase 4A 完成之前，App 與櫃檯 Web 的行為規格因為後端還沒存內容會看到「照片無法顯示」，屬預期。

## Phase 3: Test Alignment & Implementation

**Goal**: 把本輪 Feature 用到的 DSL 自動化測試對齊最新版 truth；含 truth-delta 有改的句，以及本輪 Feature 用到、尚無 stepdef 的句。不寫產品行為。

**DSL 參照**:
- 每一句只屬於一個權威 `dsl.md`。後端：同模組 `specs/truth/features/backend/工務單/dsl.md`、`specs/truth/features/backend/櫃檯看板與派單/dsl.md`，或介面根 `specs/truth/features/backend/dsl.md`。員工 App：`specs/truth/features/ios-app/工務單/dsl.md` 或介面根 `specs/truth/features/ios-app/dsl.md`。櫃檯 Web：`specs/truth/features/frontdesk-web/單筆詳情/dsl.md`。不得掃其他模組。
- 讀法：用 task title 的句型對到該檔那一列，以 `StepDef 實作語意`（櫃檯 Web 是 `櫃檯 Web 實作語意（Playwright）`）當作測試程式碼語意。Given / When 讀 `怎麼做`、`權威狀態落地`、`回寫`（櫃檯 Web 讀 `Arrange 來源`、`頁面操作`、`等待條件`）；Then 讀 `必查`。
- `truth-delta.md` 只告訴這句是 ADD / MODIFY / DELETE。語意以 `dsl.md` 那一列為準，不得用 feature 措辭或舊 stepdef 自行發明。
- 凡是上傳照片內容或讀照片內容的步驟，一律用 T007 的 `BddApiClient.exchangeBytes`，不得用 JSON 的 `exchange`。

**Markers**:
- `[BDD-ALIGN]`：`MODIFY`。既有 stepdef 還在，但語意是舊 truth。依 `dsl.md` 該列改測試，讓它表達最新版 `StepDef 實作語意`。
- `[BDD-RED]`：`ADD`，或本輪 Feature 用到、尚無 stepdef 的句。依 `dsl.md` 該列寫出 stepdef。完成時這句可被跑到，失敗只能是 assertion 或產品行為，不能是 undefined step。
- 兩個 marker 都只動測試層，不寫產品碼。本輪沒有 `[BDD-REMOVE]`。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/dsl.md` -> 「照片內容與存放」段，以及下列每一句
- `specs/truth/features/backend/dsl.md` -> `已有以下工務單：`、`這次操作被拒絕，錯誤碼為 "{錯誤碼}"`
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> 兩句櫃檯詳情照片句
- `specs/truth/features/ios-app/dsl.md` -> `已有以下工務單：`；`specs/truth/features/ios-app/工務單/dsl.md` -> 模組說明、`工務單詳情的 "{種類}" 照片 {張數} 張都顯示出圖片`
- `specs/truth/features/frontdesk-web/單筆詳情/dsl.md` -> 模組說明「工務單照片」段與下列每一句
- `truth-delta.md` -> `/dsl-refine` 後端、員工 App、櫃檯 Web 各列
- `tms/src/test/java/com/athena/tms/bdd/steps/repair/`、`bdd/support/PhotoFixtures.java`、`bdd/support/BddApiClient.java`；`app/bdd-support/.../BackendChannel.java`

**Boundary**:
- 一條 DSL 一個 task。
- 只改該句的 stepdef / assertion / 直接依賴的 helper。後端新句放在 `steps/repair/` 新檔 `RepairPhotoSteps.java`（Given、When、Then 各自分檔也可，照既有 `RepairGivenSteps`／`RepairWhenSteps`／`RepairThenSteps` 的分法）；櫃檯詳情句放在 `steps/desk/` 既有的詳情步驟檔旁。App 句放在 `app/bdd/.../steps/repair/`。櫃檯 Web 句放在 `app/web-bdd/.../steps/detail/`，畫面操作收在 `pages/DetailPanel.java`（新增照片區塊與看大圖的定位）。
- 不寫產品碼。
- review 啟動 subagent；本輪所有 Test Scope 不得再有 undefined step，失敗只能是 assertion 或產品行為。有 issues 就修正再 review，直到沒有任何問題。通過前不解鎖 Phase 4。

**Parallel Hint**:
- 後端 T011–T031 各派一個獨立 subagent（共用 `PhotoFixtures`、`exchangeBytes` 與 `ScenarioWorld` 的照片標籤對照，T007 已建好）。
- 員工 App T032–T033 用模擬器，排隊做。櫃檯 Web T034–T047 共用 `pages/DetailPanel.java`，同一個 subagent 依序做或先由一人加好 page object 再平行。
- T048 等全部回來再啟動 subagent 來 review。

- [X] T011 [P] [BDD-ALIGN] 後端 `已有以下工務單：`
  - Read: `tms/src/test/java/com/athena/tms/bdd/support/` 裡建立 `repair_photos` 測試資料的 helper（以 `grep -rn "INSERT INTO repair_photos" tms/src/test/java` 找）
  - 建立已上傳照片時，把 `PhotoFixtures` 的合法 JPEG（每張不同）寫進照片儲存，`storage_ref` 寫 `repair-photos/{tenantId}/{photoId}.jpg`。
- [X] T012 [P] [BDD-RED] `"{員工}" 已上傳照片 "{照片}"，用途為 "{用途}"`
- [X] T013 [P] [BDD-RED] `"{員工}" 已建立照片 "{照片}"，用途為 "{用途}"，還沒上傳內容`
- [X] T014 [P] [BDD-RED] `照片 "{照片}" 已附在 "{單號}"`
- [X] T015 [P] [BDD-RED] `別家飯店的員工已上傳照片 "{照片}"，用途為 "{用途}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/support/TenantFixtures.java`、`StaffFixtures.java`（另建一家飯店與員工的既有做法，見 `帳號與登入/員工登入.feature` 的別家飯店情境步驟）
- [X] T016 [P] [BDD-RED] `照片 "{照片}" 不存在`
- [X] T017 [P] [BDD-RED] `照片 "{照片}" 是舊版的假照片`
  - 用 T007 的照片儲存 helper 刪物件，再以 SQL 改 `storage_ref`。
- [X] T018 [P] [BDD-RED] `"{員工}" 以 "{內容}" 上傳照片 "{照片}" 的內容`
  - 用 `exchangeBytes(PUT, …, PhotoFixtures 位元組, 對照的 Content-Type)`。
- [X] T019 [P] [BDD-RED] `"{員工}" 查看照片 "{照片}"`
  - 用 `exchangeBytes(GET, …, null, null)`，回寫狀態碼、`Content-Type`、`Cache-Control` 與位元組。
- [X] T020 [P] [BDD-RED] `"{員工}" 建立照片，用途為 "{用途}"、拍攝時間為 "{拍攝時間}"`
- [X] T021 [P] [BDD-RED] `"{工務}" 附照片 "{照片}" 以已修好完成 "{單號}"`
- [X] T022 [P] [BDD-RED] `"{員工}" 補照片 "{照片}" 到 "{單號}"`
- [X] T023 [P] [BDD-RED] `"{員工}" 從 "{任務}" 附照片 "{照片}" 開立水電工務單`
- [X] T024 [P] [BDD-RED] `照片 "{照片}" 已上傳，存的是 "{內容}"`
  - 「再讀確認」用 `exchangeBytes(GET, …)`。
- [X] T025 [P] [BDD-RED] `照片 "{照片}" 仍未上傳`
- [X] T026 [P] [BDD-RED] `看到的照片是 "{照片}" 存的內容`
  - 讀 T019 回寫的 `exchangeBytes` 回應，含 `Cache-Control` 依照片是否已附上。
- [X] T027 [P] [BDD-RED] `照片 "{照片}" 的第一次上傳時間是 "{時刻}"`
- [X] T028 [P] [BDD-RED] `系統沒有建立照片`
- [X] T029 [P] [BDD-RED] `"{員工}" 打開 "{單號}" 時，"{種類}" 照片 {張數} 張都看得到`
  - 讀照片用 `exchangeBytes(GET, …)`。
- [X] T030 [P] [BDD-RED] `櫃檯詳情的報修照片有 {報修張數} 張、完修照片有 {完修張數} 張，"{員工}" 每一張都讀得到`
  - 讀照片用 `exchangeBytes(GET, …)`。
- [X] T031 [P] [BDD-RED] `櫃檯詳情沒有工務單的照片`
- [ ] T032 [BDD-ALIGN] 員工 App `已有以下工務單：`
  - 確認 `app/bdd/.../steps/repair/RepairGivenSteps.java:163` 呼叫的 `insertReportPhotos` 已是 T010 的新版（經 API 上傳內容），完修照片走 `completeRepairTask`；不需要改 stepdef 時在 review 記下理由。
- [ ] T033 [BDD-RED] 員工 App `工務單詳情的 "{種類}" 照片 {張數} 張都顯示出圖片`
  - 依 `ticket.photo.{序號}`／`ticket.completion-photo.{序號}` 取格子，讀 accessibility value（iOS 是 `value` 屬性）等於 `loaded`；格數與 `BackendChannel` 讀到的 `photos`／`completionPhotos` 長度一致。
- [ ] T034 [BDD-RED] 櫃檯 Web `"{房號}" 的工務單有 {報修張數} 張報修照片、{完修張數} 張完修照片`
- [ ] T035 [BDD-RED] 櫃檯 Web `"{房號}" 的工務單第 {第幾張} 張報修照片是舊版的假照片`
- [ ] T036 [BDD-RED] 櫃檯 Web `"{員工}" 開著 "{房號}" 詳情面板 "{區塊}" 第 {第幾張} 張照片的大圖`
- [ ] T037 [BDD-RED] 櫃檯 Web `"{員工}" 點詳情面板 "{區塊}" 的第 {第幾張} 張縮圖`
- [ ] T038 [BDD-RED] 櫃檯 Web `"{員工}" 在看大圖按 "{按鈕}"`
- [ ] T039 [BDD-RED] 櫃檯 Web `詳情面板的 "{區塊}" 有 {張數} 張縮圖，依序是這張單的照片`
  - 圖片位元組在頁面裡以 `fetch(img.src)` 取 `blob:` 內容回傳，與 `BackendChannel.photoContent` 比對；攔頁面的網路請求，確認照片請求帶 `Authorization`、網址不含憑證。
- [ ] T040 [BDD-RED] 櫃檯 Web `詳情面板的照片區塊排在 "{前}" 之後、"{後}" 之前`
- [ ] T041 [BDD-RED] 櫃檯 Web `詳情面板沒有 "{區塊}" 這一排`
- [ ] T042 [BDD-RED] 櫃檯 Web `詳情面板沒有照片`
- [ ] T043 [BDD-RED] 櫃檯 Web `看大圖顯示 "{區塊}" 的第 {第幾張} 張，標示 "{計數}"`
- [ ] T044 [BDD-RED] 櫃檯 Web `詳情面板 "{區塊}" 的第 {第幾張} 張顯示 "{文字}"`
- [ ] T045 [BDD-RED] 櫃檯 Web `詳情面板 "{區塊}" 的第 {第幾張} 張是這張單的照片`
- [ ] T046 [BDD-RED] 櫃檯 Web `詳情面板的照片區塊標題依序是 "{報修}"、"{完修}"`
- [ ] T047 [BDD-RED] 櫃檯 Web `詳情面板的照片縮圖都在面板寬度內，每張至少 44 × 44`

- [X] T048 subagent review (phase quality gate)
  - 驗證：後端三份工務單 feature 與 `櫃檯看板與派單/單筆詳情與歷程.feature`、App `工務單` 模組、櫃檯 Web `單筆詳情/工務單照片.feature` 各跑 dry-run，undefined step 為 0；實跑時新情境的失敗只能是斷言或產品行為（例如上傳仍回 200、讀照片回 404 因為端點不存在、詳情面板沒有照片區塊），不能是步驟找不到。確認上傳與讀照片的後端步驟都走 `exchangeBytes`。
  - 通過後只加 Phase 2 至 3 的測試檔，提交一次，提交訊息寫明這一批是先紅的測試。

## Phase 4A: ADD Feature File - backend/工務單/照片內容上傳與查看.feature

**Goal**: 上傳內容真的存進雲端儲存並檢查格式、大小、建立者與是否已附上；新增讀照片端點，櫃檯、主管、值班主管讀得到、老闆讀不到；建立照片的輸入錯誤回 422。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/照片內容上傳與查看.feature` -> `功能: 照片內容上傳與查看`
- `specs/truth/features/backend/工務單/dsl.md` -> 「照片內容與存放」段與 Phase 3 各句
- `truth-delta.md` -> `/dsl-refine` ADD `照片內容上傳與查看.feature` 列；`/api-plan` `getRepairPhotoContent`、`uploadRepairPhotoContent`、`createRepairPhoto`、`responses.yaml`、`shared.yaml` 列；`/data-plan` `repair_photos` 各列
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `createRepairPhoto`、`uploadRepairPhotoContentAndgetRepairPhotoContent`
- `specs/truth/data/data-model.dbml` -> `repair_photos`
- `plan.md` -> 上傳怎麼檢查？、讀照片怎麼回？、建立照片的輸入怎麼檢查？

**Boundary**:
- 不在 `@PutMapping` 宣告 `consumes`；內容檢查集中在 `repair/RepairPhotoContent.java`。
- 先寫雲端儲存、後寫資料庫，同一交易內 `SELECT … FOR UPDATE` 照片列。
- 讀照片不加逐人權限檢查；`SecurityConfig` 只加一條讀照片的 matcher。
- 不加 Flyway 遷移。

**Test Scope**:
- `specs/truth/features/backend/工務單/照片內容上傳與查看.feature`

- [X] T049 [BDD-GREEN] 讓 Test Scope 全綠
  - 落點：`repair/RepairController.java`（PUT 讀 `Content-Type`、`Content-Length` 與輸入串流；新增 `GET /repair-photos/{photoId}/content` 回 `ResponseEntity<byte[]>`、`image/jpeg`，`Cache-Control` 照照片是否已附上：已附上 `private, max-age=86400, immutable`、還沒附上 `private, no-cache`；建立照片檢查 `purpose`、`takenAt` 回 `validation_failed`）、`repair/RepairPhotoContent.java`、`repair/RepairService.java`（`markUploaded` 換成 `storeContent`；新增 `readContent`，`storage_ref` 不是 `repair-photos/` 開頭或儲存裡沒有物件都回 404）、`config/SecurityConfig.java`（在 `GET /api/v1/**` 之前加 `requestMatchers(HttpMethod.GET, "/api/v1/repair-photos/*/content").hasAnyRole("housekeeping", "engineering", "manager", "frontdesk", "duty_manager")`）、`common/error/` 視需要新增 413、415 的例外類別（沿用 `ApiException`）。
  - 另跑一次手動檢查並記到 `implement-notes.md`：以 `curl` 送 5.5 MB 的內容，收到 413 與 `photo_too_large` 的 body，不是連線被重設。
- [X] T050 [BDD-REFACTOR] 刪掉 `markUploaded` 與 `"upload:"` 假值的所有殘留（`readContent` 對舊列的判斷只認前綴），確認存照片只有 `GcsPhotoStorage` 一個實作、看模擬位址只有 `StorageConfig` 一處

## Phase 4B: MODIFY Feature File - backend/工務單/照片完整性與冪等送出.feature

**Goal**: 完修與開單的照片逐張檢查：已上傳、不是舊版假照片、本飯店、用途相符、沒附在別張單；兩個請求同時附同一張照片時只有一個成立。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/照片完整性與冪等送出.feature` -> `功能: 照片完整性與冪等送出`
- `specs/truth/features/backend/工務單/dsl.md` -> `"{工務}" 附照片 "{照片}" 以已修好完成 "{單號}"`、`"{員工}" 從 "{任務}" 附照片 "{照片}" 開立水電工務單`、`照片 "{照片}" 是舊版的假照片`
- `truth-delta.md` -> `/dsl-refine` MODIFY `照片完整性與冪等送出.feature` 列；`/api-plan` `CompleteTaskRequest.photoIds`、`tasksComplete`、`CreateRepairTicketRequest.photoIds` 列；`/data-plan` `repair_photos` Note 列
- `specs/truth/contracts/components/schemas/任務.yaml` -> `CompleteTaskRequest`
- `specs/truth/contracts/components/schemas/工務單.yaml` -> `CreateRepairTicketRequest`
- `plan.md` -> 四處照片檢查怎麼共用？

**Boundary**:
- 共用檢查只有 `RepairService.requireAttachablePhotos` 一處；張數上下限留在各路徑。
- `requireAttachablePhotos` 以 `SELECT … WHERE tenant_id = ? AND id IN (…) ORDER BY id FOR UPDATE` 取並鎖照片列，與綁定在同一個交易；`storage_ref` 不是 `repair-photos/` 開頭視同未上傳（`photo_not_uploaded`）。
- `bindUnclaimedPhotos` 改成 `UPDATE … WHERE … AND (ticket_task_id IS NULL OR ticket_task_id = :ticket)` 並回傳改到的筆數；呼叫者比對 `photoIds` 去重後的張數，不相等丟 409 `photo_already_attached`（沿用既有錯誤碼，不新增）。
- 退回重修要附這一輪照片的既有檢查保留。

**Test Scope**:
- `specs/truth/features/backend/工務單/照片完整性與冪等送出.feature`
- `specs/truth/features/backend/工務單/驗收不通過與重修.feature`（回歸：這一輪照片的規則）

- [X] T051 [BDD-GREEN] 讓 Test Scope 全綠
  - 落點：`repair/RepairService.java`（`requireAttachablePhotos`；`requireUploadedPhotos` 改用它；`bindUnclaimedPhotos` 回傳筆數並比對）、`service/TaskExecutionService.java`（`requireCompletionPhotos` 呼叫它；綁定完修照片處同樣比對筆數）。
  - 加一支不走 Cucumber 的整合測試（`PostgresSpringTest` 底下）：直接呼叫綁定方法，讓其中一張照片事先綁到別張單（模擬漏鎖），確認回 409 `photo_already_attached`、交易回滾；另一支用兩個執行緒同時以同一張照片開單，確認恰好一次成功、一次 409。
- [X] T052 [BDD-REFACTOR] 確認開單、完修兩處不再各自查 `repair_photos.status`，只透過共用檢查；三個呼叫點都在 `@Transactional` 方法裡

## Phase 4C: MODIFY Feature File - backend/工務單/重複報修補照片與撤回.feature

**Goal**: 補照片先擋已完修、已結案、已撤回的單，再檢查張數與每張照片。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/重複報修補照片與撤回.feature` -> `功能: 重複報修、補照片與撤回`
- `specs/truth/features/backend/工務單/dsl.md` -> `"{員工}" 補照片 "{照片}" 到 "{單號}"`、`"{員工}" 補 {張數} 張照片到 "{單號}"`
- `truth-delta.md` -> `/dsl-refine` MODIFY `重複報修補照片與撤回.feature` 列；`/api-plan` `appendRepairTicketPhotos`、`AppendRepairPhotosRequest` 列
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `appendRepairTicketPhotos`
- `plan.md` -> 四處照片檢查怎麼共用？

**Boundary**:
- 檢查順序：單的狀態（`ticket_closed`）→ 補照片資格（403）→ 張數（`photo_limit`）→ `requireAttachablePhotos`。
- 被拒時一張都不綁。

**Test Scope**:
- `specs/truth/features/backend/工務單/重複報修補照片與撤回.feature`

- [X] T053 [BDD-GREEN] 讓 Test Scope 全綠
  - 落點：`repair/RepairService.java` 的 `appendPhotos`；`repair/RepairController.java` 只留空陣列的 422，超過 3 張交給服務。
- [X] T054 [BDD-REFACTOR] 後端全套與 T001 基準比沒有新失敗；只加 Phase 4A 至 4C 的檔案，提交一次

## Phase 4D: 員工 App 拍照、選照片與顯示照片

**Goal**: 「拍照」打開相機、「從相簿選擇」打開相簿，照片縮小壓縮後上傳；工務單詳情與看大圖顯示真的照片，主管從待驗收清單進來時兩種照片都看得到；測試版照舊送固定測試照片。

**Shared Must Read**:
- `truth-delta.md` -> `/technical-research`「App 相機與照片」列；`/dsl-refine` 員工 App 各列
- `specs/truth/techstack.md` -> App 相機與照片
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `getRepairPhotoContent`；`components/schemas/工務單.yaml` -> `RepairTicket.photos`、`completionPhotos`
- `spec.md` -> App 怎麼拍照、選照片、顯示照片？；「房務主管、工務主管」故事
- `research.md` -> App 用什麼拍照、選照片與壓縮？、App 與櫃檯 Web 怎麼顯示需要登入才拿得到的照片？
- `plan.md` -> App 怎麼接？（含「主管驗收」段）

**Boundary**:
- 只加 `image_picker`；不加 `camera`、`flutter_image_compress`、`cached_network_image`。
- 測試版（`TEST_BACKDOOR`）的判斷只在 `core/repair_photo_capture.dart` 一處。
- 主管視角不另寫照片元件，沿用 `_engineeringLayout` 的照片格子。
- 不改 Android `AndroidManifest.xml`。
- 不改離線佇列。

**Test Scope**:
- `cd app/mobile && flutter test`

- [ ] T055 加 `image_picker` 與 iOS 權限說明
  - `app/mobile/pubspec.yaml` 加 `image_picker`（`flutter pub add image_picker`，鎖當下穩定版）；`app/mobile/ios/Runner/Info.plist` 加 `NSCameraUsageDescription`「拍下要報修或已修好的地方，附在工務單上」、`NSPhotoLibraryUsageDescription`「選擇要附在工務單上的照片」；`cd app/mobile/ios && pod install` 後 `flutter build ios --simulator --debug` 能建置。
- [ ] T056 先寫會紅的畫面與單元測試
  - 新增 `app/mobile/test/repair_photo_capture_test.dart`：選「拍照」呼叫拍照函式時帶相機來源、選「從相簿選擇」帶相簿來源；取消時不呼叫 `createRepairPhoto`；拿到的位元組不是 JPEG 時丟出照片不能用，不上傳；拍攝時間用檔案時間；測試版回固定測試照片。
  - 擴充 `app/mobile/test/photo_test.dart`：看大圖依照片清單逐張顯示圖片（以假的 `ApiClient` 回固定 JPEG）、讀照片失敗時顯示「照片無法顯示」。
  - 新增或擴充工務單詳情的畫面測試（`engineering_contract_fields_test.dart` 或新檔）：報修照片與完修照片格子各自拿到 `image`，數量等於回應陣列長度；格子的 `Semantics` value 在載入後是 `loaded`、失敗是 `failed`。
  - 主管視角案例：以 `TicketEntry.verifyQueue`、主管身分、`state`=done 的工務單打開 `TicketScreen`（同 `features/manager/manager_queue_screen.dart:101` 的進入方式），假的 `ApiClient` 回 2 張報修、1 張完修照片；斷言兩個區塊的格子都拿到圖片、底部同時有「通過」「不通過」，按下之前不必捲到別處就看得到照片區塊。
  - 提交一次，訊息寫明先紅。
- [ ] T057 拍照與選照片
  - `core/repair_photo_capture.dart`：`captureAndUploadRepairPhoto` 加 `source` 參數與可替換的拍照函式；正式版 `ImagePicker().pickImage(source, maxWidth: 1600, maxHeight: 1600, imageQuality: 80)`；檢查 `FF D8 FF`；拍攝時間 `XFile.lastModified()`，拿不到用 `deviceNow()`。`core/api_client.dart` 的 `createRepairPhoto` 加 `takenAt` 參數。
  - 四個呼叫者把照片選單的結果傳進去，拍到的位元組存在 `RepairPhoto` 上供縮圖：`features/task/repair_sheet.dart`、`features/ticket/repair_ticket_form_sheet.dart`、`features/ticket/ticket_complete_sheet.dart`、`features/ticket/ticket_screen.dart`。
  - 權限被拒（`camera_access_denied`、`photo_access_denied`）與照片不能用，照 spec 的提示文字顯示，用既有的提示框元件。
- [ ] T058 顯示照片
  - 新增 `core/repair_photo_image.dart`（`ApiClient.instance.fetchRepairPhotoContent(id)`、依 id 記憶體快取、`MemoryImage`、失敗狀態）；`core/api_client.dart` 加 `fetchRepairPhotoContent`。
  - `features/ticket/ticket_detail_engineering.dart`、`features/ticket/ticket_detail_housekeeping.dart` 的 `TmsPhotoCell` 給 `image:`，`Semantics` 帶 value（`loaded`、`loading`、`failed`）；`features/ticket/ticket_screen.dart` 的看大圖改依 `photos`／`completionPhotos` 建 `TmsViewerPhoto(image: …)`。
- [ ] T059 [BDD-GREEN] `flutter test` 與 `flutter analyze` 全綠
- [ ] T060 [BDD-REFACTOR] 刪掉不再被正式版呼叫的程式路徑（`takePhotoJpeg` 只留給測試版）；提交一次

## Phase 4E: MODIFY Feature File - ios-app/工務單/主管視角.feature

**Goal**: 主管從待驗收清單打開已完修的工務單，按通過或不通過之前，報修照片與這一輪完修照片都顯示出真的圖片。

**Shared Must Read**:
- `specs/truth/features/ios-app/工務單/主管視角.feature` -> Rule「驗收前，報修照片與這一輪完修照片都顯示出真的圖片」
- `specs/truth/features/ios-app/工務單/dsl.md` -> 模組說明、`工務單詳情的 "{種類}" 照片 {張數} 張都顯示出圖片`
- `specs/truth/features/ios-app/dsl.md` -> `已有以下工務單：`
- `truth-delta.md` -> `/dsl-refine` 員工 App MODIFY `主管視角.feature`、ADD Then、MODIFY `已有以下工務單：` 列
- `plan.md` -> App 怎麼接？（「主管驗收」段）

**Boundary**:
- 不改其他 Appium 情境；後端要連有 `fake-gcs-server` 的本機環境。

**Test Scope**:
- `specs/truth/features/ios-app/工務單/主管視角.feature`

- [ ] T061 [BDD-GREEN] 讓 Test Scope 全綠
  - `app/bdd/run-module.sh 工務單`，只看主管視角這份；失敗時先確認 T010 的測試資料真的存了內容（以 `BackendChannel.photoContent` 讀得到）。
- [ ] T062 [BDD-REFACTOR] 主管視角整份與 T001 基準比沒有新失敗；提交一次

## Phase 4F: 櫃檯 Web 的工務單詳情顯示照片

**Goal**: 櫃檯詳情帶工務單的報修與完修照片編號；櫃檯 Web 的詳情面板在「內容」與歷程之間顯示兩排縮圖、點開看大圖，讀不到時顯示「照片無法顯示」，電腦與 iPad、四種語言都成立。

**Shared Must Read**:
- `specs/truth/features/backend/櫃檯看板與派單/單筆詳情與歷程.feature` -> 兩條照片 Rule
- `specs/truth/features/backend/櫃檯看板與派單/dsl.md` -> 兩句櫃檯詳情照片句
- `specs/truth/features/frontdesk-web/單筆詳情/工務單照片.feature` -> 全文
- `specs/truth/features/frontdesk-web/單筆詳情/dsl.md` -> 模組說明「工務單照片」段與照片各句
- `truth-delta.md` -> `/dsl-refine` 後端 MODIFY `單筆詳情與歷程.feature` 列、櫃檯 Web ADD／MODIFY 列；`/api-plan` `DeskTaskDetail.repair`、`readDeskTask`、`getRepairPhotoContent` 列；`/technical-research`「櫃檯 Web 照片顯示」列
- `specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskTaskDetail.repair`
- `spec.md` -> 櫃檯 Web 怎麼顯示照片？、櫃檯 Web 要怎麼顯示需要登入才看得到的照片？、情境 7
- `research.md` -> App 與櫃檯 Web 怎麼顯示需要登入才拿得到的照片？
- `plan.md` -> 櫃檯 Web 怎麼接？
- `specs/plans/007-frontdesk-create-request/ui/board.html`、`ui/desk.css` -> 詳情面板既有的區塊樣式（原型沒有照片，只參考區塊標題、卡片底色與間距）

**Boundary**:
- 照片清單放在 `DeskTaskDetail.repair`，用 `RepairService` 組 `RepairTicket.photos`／`completionPhotos` 的同一段查詢，不另寫 SQL。
- 登入憑證只放 `Authorization` 標頭；不得把憑證放進任何網址。
- 不加圖片相關套件；Vitest 照既有慣例只測純邏輯（`environment: 'node'`），不加 jsdom。
- 不改詳情面板既有區塊的順序與內容。

**Test Scope**:
- `specs/truth/features/backend/櫃檯看板與派單/單筆詳情與歷程.feature`
- `cd app/web && npx vitest run`
- `specs/truth/features/frontdesk-web/單筆詳情/工務單照片.feature`（`desktop` 與 `ipad` 各一次）
- `specs/truth/features/frontdesk-web/單筆詳情/詳情面板內容.feature`（回歸：既有區塊不變）

- [X] T063 [BDD-GREEN] 後端櫃檯詳情帶照片
  - 落點：`dto/desk/DeskTaskDetail.java`（`repair` 加 `photos`、`completionPhotos`，型別沿用工務單的照片 DTO）、`service/DeskBoardService.java`（工務單時呼叫 `RepairService` 組照片清單的方法；必要時把它從 `ticketView` 抽成可共用的方法）。
  - 驗證：`單筆詳情與歷程.feature` 全綠；`照片內容上傳與查看.feature` 仍全綠（值班主管讀照片）。
- [ ] T064 先寫會紅的 Vitest
  - 新增 `api/repair-photos.test.ts`：`fetchPhotoBlob(id)` 以 `vi.stubGlobal('fetch', …)` 驗證請求網址是 `/api/v1/repair-photos/{id}/content`、不含任何查詢字串、帶 `Authorization: Bearer <session token>`；200 回 `Blob`；404 丟出 `ApiError`（`code`=`not_found`）；`signal` 中止時不再回傳。
  - 新增 `lib/photo-urls.test.ts`：純函式 `diffPhotoUrls(previous, nextIds)` 回傳要新建與要釋放的 id；換單時舊的全部釋放；同一個 id 不重抓；配合注入的 `create`／`revoke` 函式驗證卸載時每個建過的網址都被釋放一次。
  - 新增 `lib/repair-photo-rows.test.ts`：依 `DeskTaskDetail` 算出要顯示的排（`repair` 為 null 沒有任何排；陣列空的那一排不出現；順序與回應相同）。
  - 提交一次，訊息寫明先紅。
- [ ] T065 讀照片與照片元件
  - `api/types.ts`：`DeskTaskDetail.repair` 加 `photos`、`completionPhotos`（`{ id: string; status: 'pending' | 'uploaded'; takenAt?: string }[]`）。
  - `api/repair-photos.ts`：`fetchPhotoBlob(photoId, signal)`，用 `client.ts` 的 `authHeaders()` 與 `backendUrl()`，非 200 轉成既有的 `ApiError`。
  - `lib/photo-urls.ts`、`lib/repair-photo-rows.ts`：T064 測的兩個純函式。
  - `features/detail/RepairPhotoStrip.tsx`：兩排縮圖與看大圖，照 `plan.md`「櫃檯 Web 怎麼接？」的版面；`<img>` 用 `blob:` 網址，卸載、換單或 id 清單變了時 `URL.revokeObjectURL` 並中止請求；失敗格顯示 `detail.photos.unavailable`；看大圖有「上一張」「下一張」「關閉」、Esc 關閉、只在同一排切換、沿用縮圖已拿到的網址。
  - `features/detail/DetailPanel.tsx:120-132`：「內容」區塊之後、歷程之前放 `<RepairPhotoStrip task={task} />`。
  - `i18n/locales/zh-TW.ts`、`en.ts`、`ja.ts`、`vi.ts` 加 `detail.photos.report`、`detail.photos.completion`、`detail.photos.unavailable`、`detail.photos.viewer.previous`、`detail.photos.viewer.next`、`detail.photos.viewer.close`、`detail.photos.viewer.counter`，文字照 `spec.md`「櫃檯 Web 怎麼顯示照片？」；`i18n/locales/locales.test.ts` 照樣綠（四種語言的鍵一致）。
- [ ] T066 [BDD-GREEN] Vitest 與櫃檯 Web 行為規格全綠
  - `cd app/web && npx vitest run`、`npm run build` 綠。
  - `bash app/web-bdd/run-feature.sh --rebuild 單筆詳情/工務單照片.feature desktop`、同一份 `ipad`、`單筆詳情/詳情面板內容.feature desktop` 全綠。
- [ ] T067 [BDD-REFACTOR] 確認 `app/web/src` 裡沒有任何把登入憑證接進網址的寫法（`grep -rn "token=" app/web/src` 沒有結果）；`單筆詳情` 整個模組 `desktop` 與 `ipad` 與 T001 基準比沒有新失敗；只加 Phase 4F 的檔案，提交一次

## Phase 4G: plan-side quality gate

**Goal**: 產品對齊本輪回追到的 plan-side 產物。`Test Scope` 全綠不能代替本題。

**Read**:
- `spec.md`、`plan.md`、`research.md`、`features/acceptance/*.feature`

**Boundary**:
- 不改 truth、不加 feature、不重跑 GREEN 當通過條件。

- [ ] T068 subagent review（`plan.md` quality gate）：逐項對 `plan.md`「程式現況跟本版差在哪裡」23 列與落點清單，確認每一列都已處理或有理由不處理；特別確認 `server.tomcat.max-swallow-size`、`application-prod.properties`、`StorageConfig` 的逾時與啟動失敗、`FOR UPDATE` 加筆數比對、`SecurityConfig` 的讀照片 matcher 都在。
- [ ] T069 subagent review（`spec.md` 與 acceptance quality gate）：逐條對 spec 的系統規則、邊界情況、白話 NFR 與三份 acceptance feature，確認都有 truth 情境、Flutter 測試、Vitest、整合測試或 Phase 5 的實機步驟承接；「同時附同一張照片」由 T051 的整合測試承接。
- [ ] T070 subagent review（`research.md` quality gate）：確認 `research.md` 列的設定項目、模擬服務參數、逾時與「還沒查證的風險」都已在實作或 `implement-notes.md` 有結論。

## Phase 5: Appium 回歸與實機驗證

**Goal**: 測試版在模擬器上的行為不變；真相機、相簿與顯示在實機上成立；櫃檯 Web 看得到同一張單的照片。

- [ ] T071 Appium 回歸
  - 跑 `app/bdd/run-module.sh 工務單`（測試版，送固定測試照片；後端要連有 `fake-gcs-server` 的本機環境）。結果與 T001 基準比沒有新失敗。
  - 注意：測試版送的 1 × 1 固定照片是合法 JPEG，會通過後端檢查；若 Appium 情境改由後端讀照片斷言，用它的位元組比對。

- [ ] T072 實機手動驗證（iPhone 實機與櫃檯 Web，連本機手動測試環境 8083／5300）
  - 起環境：`docker compose up -d`；`bash scripts/manual-env/up.sh`；確認後端啟動日誌寫出儲存空間 `tms-repair-photos-manual` 與模擬服務。
  - 以非測試版建置裝到實機：`cd app/mobile && flutter run --release --dart-define=API_BASE_URL=http://<電腦區網 IP>:8083/api/v1`（`API_BASE_URL` 是 `core/api_client.dart` 讀的變數，預設 `http://localhost:8080/api/v1`）。
  - 逐項走並截圖存到 `implement-notes.md`：
    1. 第一次按「拍照」跳出相機權限詢問，文字正確；允許後打開相機，拍 2 張，格子出現縮圖。
    2. 按「從相簿選擇」挑一張 HEIC 照片與一張 PNG 截圖：能轉成 JPEG 的上傳成功；轉不成的跳「這張照片不能用」（記下實際結果，作為 `research.md` 風險的結論）。
    3. 送出 W 單後，用另一支手機或模擬器以工務帳號打開，縮圖與看大圖都是剛才的照片，左右切換順序正確。
    4. 工務附 1 張完修照片完成；工務主管帳號從待驗收清單打開，按「通過」「不通過」之前同一個畫面看得到報修與完修照片；驗收不通過、重修再完修後只看到新的完修照片。
    5. 到 iOS 設定關掉相機權限，再按「拍照」，跳「沒有相機權限」。
    6. 打開一張這版之前開的舊單，照片格子顯示「照片無法顯示」，其他內容正常。
    7. 以 `curl` 確認雲端儲存裡有物件：`curl -s http://localhost:4443/storage/v1/b/tms-repair-photos-manual/o | jq '.items[].name'`，名稱是 `repair-photos/{飯店編號}/{照片編號}.jpg`。
    8. 在電腦瀏覽器以櫃檯帳號、iPad Safari 以值班主管帳號打開手動環境的櫃檯 Web（5300），點同一張 W 單：「內容」下面有「報修照片」「完修照片」兩排縮圖，是手機拍的那幾張；點開看大圖、按「下一張」只在同一排切換；切換到英文介面標題跟著換；開發者工具的網路分頁裡照片請求的網址沒有登入憑證，`Authorization` 在標頭；關掉面板後 `blob:` 網址失效。打開舊單顯示「照片無法顯示」。
  - 照片大小：從 `curl` 列出的 `size` 確認一般照片在 1 MB 以下、長邊不超過 1600 像素（下載一張用 `sips -g pixelWidth -g pixelHeight` 看）。

- [ ] T073 收尾
  - `implement-notes.md` 記下 T072 每一項的結果、`research.md` 風險的結論，以及 spec「還有什麼沒定？」五題的現況。
  - 後端全套、`flutter test`、`npx vitest run` 最後再各跑一次，與 T001 基準比沒有新失敗。

## 新句型清單（給 Phase 3）

以下句型在本分支 dry-run 時是 undefined，都是本版新增。

後端（權威位置 `specs/truth/features/backend/工務單/dsl.md`，最後兩句在 `specs/truth/features/backend/櫃檯看板與派單/dsl.md`）：

- Given：`"{員工}" 已上傳照片 "{照片}"，用途為 "{用途}"`；`"{員工}" 已建立照片 "{照片}"，用途為 "{用途}"，還沒上傳內容`；`照片 "{照片}" 已附在 "{單號}"`；`別家飯店的員工已上傳照片 "{照片}"，用途為 "{用途}"`；`照片 "{照片}" 不存在`；`照片 "{照片}" 是舊版的假照片`
- When：`"{員工}" 以 "{內容}" 上傳照片 "{照片}" 的內容`；`"{員工}" 查看照片 "{照片}"`；`"{員工}" 建立照片，用途為 "{用途}"、拍攝時間為 "{拍攝時間}"`；`"{工務}" 附照片 "{照片}" 以已修好完成 "{單號}"`；`"{員工}" 補照片 "{照片}" 到 "{單號}"`；`"{員工}" 從 "{任務}" 附照片 "{照片}" 開立水電工務單`
- Then：`照片 "{照片}" 已上傳，存的是 "{內容}"`；`照片 "{照片}" 仍未上傳`；`看到的照片是 "{照片}" 存的內容`；`照片 "{照片}" 的第一次上傳時間是 "{時刻}"`；`系統沒有建立照片`；`"{員工}" 打開 "{單號}" 時，"{種類}" 照片 {張數} 張都看得到`；`櫃檯詳情的報修照片有 {報修張數} 張、完修照片有 {完修張數} 張，"{員工}" 每一張都讀得到`；`櫃檯詳情沒有工務單的照片`

員工 App（`specs/truth/features/ios-app/工務單/dsl.md`）：

- Then：`工務單詳情的 "{種類}" 照片 {張數} 張都顯示出圖片`

櫃檯 Web（`specs/truth/features/frontdesk-web/單筆詳情/dsl.md`）：

- Given：`"{房號}" 的工務單有 {報修張數} 張報修照片、{完修張數} 張完修照片`；`"{房號}" 的工務單第 {第幾張} 張報修照片是舊版的假照片`；`"{員工}" 開著 "{房號}" 詳情面板 "{區塊}" 第 {第幾張} 張照片的大圖`
- When：`"{員工}" 點詳情面板 "{區塊}" 的第 {第幾張} 張縮圖`；`"{員工}" 在看大圖按 "{按鈕}"`
- Then：`詳情面板的 "{區塊}" 有 {張數} 張縮圖，依序是這張單的照片`；`詳情面板的照片區塊排在 "{前}" 之後、"{後}" 之前`；`詳情面板沒有 "{區塊}" 這一排`；`詳情面板沒有照片`；`看大圖顯示 "{區塊}" 的第 {第幾張} 張，標示 "{計數}"`；`詳情面板 "{區塊}" 的第 {第幾張} 張顯示 "{文字}"`；`詳情面板 "{區塊}" 的第 {第幾張} 張是這張單的照片`；`詳情面板的照片區塊標題依序是 "{報修}"、"{完修}"`；`詳情面板的照片縮圖都在面板寬度內，每張至少 44 × 44`
