# Tasks: 工務單照片拍了就是真的，存得住、看得到

**Plan Package**: `specs/plans/009-repair-photo-storage`
**Core Inputs**: `spec.md`, `plan.md`, `research.md`, `truth-delta.md`, `specs/truth/techstack.md`, `specs/truth/contracts/**`, `specs/truth/data/data-model.dbml`, `specs/truth/features/backend/**`

本檔照實際動工的順序排，每一段結束都有一個檢查點：

1. Phase 1、2：本機與測試環境接上模擬雲端儲存，存照片的程式介面就位。檢查點是冒煙測試寫入、讀出、覆寫都成立，既有後端全套照基準。
2. Phase 3、4A 至 4C：後端行為規格先紅、再寫產品碼變綠。檢查點是三份 truth feature 全綠，後端全套照基準。
3. Phase 4D：員工 App 拍照、選照片與顯示照片。檢查點是 `flutter test` 全綠。
4. Phase 5：Appium 回歸與實機手動驗證。

`ui/` 本版不存在，理由見 `plan.md` 開頭。

## Task Binding Contract

- 每個開發任務都對應 `truth-delta.md` 中的 ADD / MODIFY / DELETE / NOOP 語意。
- Phase 1 `Setup` 只做本輪新增技術的基礎建設、技術環境與最後的冒煙測試；不寫 DSL 語意、不寫產品行為。
- Phase 2 `Foundational` 只建立後續實作程式、測試共用元件、入口、fixture、helper 與落點骨架。
- Phase 3 `Test Alignment & Implementation` 的目的：在寫產品碼之前，先把本輪所有受影響 DSL 的自動化測試對齊最新版 truth。
- Truth 參照使用 `specs/truth/**` 路徑；plan 參照使用當前 plan package 內相對路徑。
- Feature `Shared Must Read` 除 feature、同模組 DSL、對應 `truth-delta` 列外，還列入回追到的 planner 產物。

## 怎麼跑測試？

每一題的驗證都用下面這幾條，不另寫。

- 後端全套（JDK 25、OrbStack）：
  `JAVA_HOME="$(/usr/libexec/java_home -v 25)" TESTCONTAINERS_RYUK_DISABLED=true ./tms/mvnw -f tms/pom.xml test -DargLine="-Dapi.version=1.44"`
- 只跑後端行為規格：上一條加 `-Dtest=RunTruthFeaturesTest`；只跑一份檔再加 `-Dcucumber.features=../specs/truth/features/backend/工務單/<檔>.feature`；找沒有實作的句子再加 `-Dcucumber.execution.dry-run=true`。只跑單一 feature 時多出的 CucumberTestEngine discovery 錯誤可以忽略。
- 員工 App：`cd app/mobile && flutter test`；分析 `cd app/mobile && flutter analyze`。
- 員工 App 行為規格：`app/bdd/run-module.sh 工務單`（模擬器一次只給一個 agent 用）。
- 同一個 worktree 裡有多個 agent 時，Maven 本機倉庫與模擬器不要共用：各自用 `-Dmaven.repo.local=<自己的路徑>`，模擬器排隊用。

「全綠」都是指「除了 T001 記下的既有失敗之外全綠」。

## Phase 1: Setup

**Goal**: 後端有官方雲端儲存程式庫，本機 docker-compose 與後端測試都有 `fake-gcs-server`，冒煙測試證明寫入、讀出、覆寫在模擬服務上成立。

- [ ] T001 量改動前的基準
  - Read:
    - `specs/truth/techstack.md` -> 測試與驗證
  - 在任何產品碼或測試碼改動之前，跑後端全套、`flutter test`、`app/bdd/run-module.sh 工務單`，把每一組的失敗清單（測試類別與方法，或 feature 檔名與行號）與失敗數記到 `implement-notes.md`。
  - 不修任何既有失敗。

- [ ] T002 加入雲端儲存程式庫與設定項目
  - Read:
    - `specs/truth/techstack.md` -> 後端「照片儲存」列
    - `research.md` -> 後端用哪個程式庫接雲端儲存？
  - `tms/pom.xml` 明確宣告 `com.google.cloud:google-cloud-storage`，版本先跑 `JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./tms/mvnw -f tms/pom.xml -q dependency:tree -Dincludes=com.google.cloud:google-cloud-storage` 確認 `firebase-admin` 帶進來的版本（分支起點是 2.64.0），寫同一版。
  - `tms/src/main/resources/application.properties` 加 `tms.storage.bucket=${TMS_STORAGE_BUCKET:tms-repair-photos-local}` 與 `tms.storage.emulator-host=${TMS_STORAGE_EMULATOR_HOST:http://localhost:4443}`；`application-test.properties` 不設，由各 Spring 設定動態給。
  - 不寫存照片的程式。

- [ ] T003 本機 docker-compose 加 fake-gcs-server
  - Read:
    - `specs/truth/techstack.md` -> 本機環境
    - `docker-compose.yml` -> 檔頭說明與 Conduktor 的寫法
    - `plan.md` -> 本機與測試環境怎麼接？
  - 新增 `fake-gcs` 服務（`fsouza/fake-gcs-server:<確切版本>`，先 `docker pull` 確認該 tag 存在；`-scheme http -port 4443 -public-host localhost:4443 -external-url http://localhost:4443`；`127.0.0.1:4443:4443`；volume `tms-fake-gcs-data:/data`），不放 profile。
  - 檔頭說明加一段：照片存在這個模擬服務、預設儲存空間名稱、`down -v` 會清掉照片。`README.md` 的本機環境段同步一句。
  - `scripts/manual-env/env.sh` 匯出 `TMS_STORAGE_BUCKET=tms-repair-photos-manual`，讓手動測試環境（8083）跟驗收環境用不同儲存空間。
  - 不改 Postgres、Kafka、Conduktor 的設定。

- [ ] T004 後端測試加共用的 fake-gcs-server 容器
  - Read:
    - `specs/truth/techstack.md` -> 後端 E2E 依賴
    - `tms/src/test/java/com/athena/tms/SharedContainers.java`
  - `SharedContainers` 加 `fakeGcs()` 與 `fakeGcsUrl()`：`GenericContainer`，映像版本與 T003 相同，`-scheme http -port 4443`，對外埠由 Testcontainers 對應，整個 JVM 只起一個。
  - 四份 Spring 設定的 `@DynamicPropertySource` 加 `tms.storage.emulator-host` 與各自的 `tms.storage.bucket`：`bdd/BddSpringConfiguration.java`（`truth-photos`）、`integration/stepdefs/CucumberSpringConfiguration.java`（`integration-photos`）、`relaywake/RelayWakeSpringConfiguration.java`（`relaywake-photos`）、`PostgresSpringTest.java`（`postgres-photos`）。
  - 不寫業務測試。

- [ ] T005 冒煙測試：在模擬服務上寫入、讀出、覆寫
  - Read:
    - `research.md` -> 本機與自動化測試怎麼模擬雲端儲存？
  - 新增 `tms/src/test/java/com/athena/tms/storage/FakeGcsSmokeTest.java`：用 T004 的容器與官方程式庫（`setHost`、`NoCredentials`）建立儲存空間、`storage.create` 寫一份位元組、`readAllBytes` 讀回相同、同名再寫一份後讀回第二份、讀不存在的名字得到 404。
  - 再以 `docker compose up -d` 起本機服務，對 `http://localhost:4443` 跑同一段（手動或同一測試以系統屬性切換），結果記到 `implement-notes.md`。
  - 驗證：冒煙測試綠；後端全套與 T001 基準比沒有新失敗（此時產品碼還沒用到儲存，四份 Spring 設定只是多了設定值）。

## Phase 2: Foundational

**Goal**: 存照片的介面與唯一實作、測試用照片內容、新錯誤碼對照就位，既有測試改送合法 JPEG，後端全套仍照基準。

- [ ] T006 存照片的介面與實作
  - Read:
    - `truth-delta.md` -> `/technical-research` 後端「照片儲存」列
    - `plan.md` -> 存照片的程式切在哪裡？
  - 只做：`tms/src/main/java/com/athena/tms/storage/PhotoStorage.java`（`put(String key, byte[] jpeg)`、`Optional<byte[]> open(String key)`）、`GcsPhotoStorage.java`（官方程式庫；`put` 帶 `Content-Type: image/jpeg` 同名覆蓋；`open` 遇 404 回空、其他錯誤往外丟）、`StorageConfig.java`（依 `tms.storage.*` 建 `Storage`；模擬位址有值時用無憑證模式並建立不存在的儲存空間，空值時用預設憑證、不建立；啟動時記一行日誌寫出儲存空間名稱與是否為模擬服務）。
  - 不做：在 `RepairService` 呼叫它；任何上傳或讀取端點。

- [ ] T007 測試用照片內容與照片標籤
  - Read:
    - `specs/truth/features/backend/工務單/dsl.md` -> 「照片內容與存放」段、`"{員工}" 以 "{內容}" 上傳照片 "{照片}" 的內容` 的內容對照
  - 只做：`tms/src/test/java/com/athena/tms/bdd/support/PhotoFixtures.java`：依（照片標籤、內容名稱）產生位元組（合法 JPEG 帶標籤、另一張 JPEG、剛好 5 MB、超過 5 MB、PNG、標成 JPEG 的 PNG、空白）與對應的 `Content-Type`；`ScenarioWorld` 加照片標籤對照（標籤 → 照片 id、最後一次成功上傳的位元組）；一個直接讀照片儲存物件的測試用 helper（給 Then 比對）。註冊進 `BddSpringConfiguration`。
  - 不做：步驟實作。

- [ ] T008 既有上傳步驟改送合法 JPEG
  - Read:
    - `truth-delta.md` -> `/dsl-refine`「模組說明『照片內容與存放』段」MODIFY 列
    - `tms/src/test/java/com/athena/tms/bdd/steps/repair/RepairGivenSteps.java`（約 415、433、476 行）、`RepairWhenSteps.java`（約 385 行）
  - 只做：四處 `"fixture-jpeg-bytes"`／`"fixture-jpeg-bytes-retry"` 改成 `PhotoFixtures` 的合法 JPEG，`Content-Type` 帶 `image/jpeg`；其他模組（`steps/`、`integration/`）若有送照片內容的地方一併改，以 `grep -rn "repair-photos" tms/src/test/java` 盤點。
  - 不做：改句型或斷言。
  - 驗證：後端全套照基準（產品碼還不檢查格式，這一步只是讓下一段不會整批被 415 擋下）。

- [ ] T009 錯誤碼對 HTTP 狀態表
  - Read:
    - `specs/truth/features/backend/dsl.md` -> `這次操作被拒絕，錯誤碼為 "{錯誤碼}"`
    - `specs/truth/contracts/components/schemas/shared.yaml` -> `Error`
  - 只做：`tms/src/test/java/com/athena/tms/bdd/steps/common/CommonThenSteps.java` 的 `ERROR_CODE_HTTP_STATUS` 加 `photo_purpose_mismatch`=422、`photo_already_attached`=409、`photo_empty`=422、`photo_type_unsupported`=415、`photo_too_large`=413，以及 `not_found`=404、`validation_failed`=422（若還沒有）。
  - 不做：改斷言邏輯。

## Phase 3: Test Alignment & Implementation

**Goal**: 把本輪 Feature 用到的 DSL 自動化測試對齊最新版 truth；含 truth-delta 有改的句，以及本輪 Feature 用到、尚無 stepdef 的句。不寫產品行為。

**DSL 參照**:
- 每一句只屬於一個權威 `dsl.md`：同模組 `specs/truth/features/backend/工務單/dsl.md`，或介面根 `specs/truth/features/backend/dsl.md`。不得掃其他模組。
- 本輪新句都在同模組 `specs/truth/features/backend/工務單/dsl.md`；`已有以下工務單：` 在介面根 `specs/truth/features/backend/dsl.md`。
- 讀法：用 task title 的句型對到該檔那一列，以 `StepDef 實作語意` 當作測試程式碼語意。Given / When 讀 `怎麼做`、`權威狀態落地`、`回寫`；Then 讀 `必查`（`呈現結果`、`權威狀態`、`再讀確認`、`跨視角`、`不該發生`）。
- `truth-delta.md` 只告訴這句是 ADD / MODIFY / DELETE。語意以 `dsl.md` 那一列為準，不得用 feature 措辭或舊 stepdef 自行發明。

**Markers**:
- `[BDD-ALIGN]`：`MODIFY`。既有 stepdef 還在，但語意是舊 truth。依 `dsl.md` 該列改測試，讓它表達最新版 `StepDef 實作語意`。
- `[BDD-RED]`：`ADD`，或本輪 Feature 用到、尚無 stepdef 的句。依 `dsl.md` 該列寫出 stepdef。完成時這句可被跑到，失敗只能是 assertion 或產品行為，不能是 undefined step。
- 兩個 marker 都只動測試層，不寫產品碼。本輪沒有 `[BDD-REMOVE]`。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/dsl.md`
  -> 「照片內容與存放」段，以及下列每一句
- `specs/truth/features/backend/dsl.md`
  -> `已有以下工務單：`、`這次操作被拒絕，錯誤碼為 "{錯誤碼}"`
- `truth-delta.md` -> `/dsl-refine` 後端各列
- `tms/src/test/java/com/athena/tms/bdd/steps/repair/`、`tms/src/test/java/com/athena/tms/bdd/support/PhotoFixtures.java`

**Boundary**:
- 一條 DSL 一個 task。
- 只改該句的 stepdef / assertion / 直接依賴的 helper。新句放在 `steps/repair/` 新檔 `RepairPhotoSteps.java`（Given、When、Then 各自分檔也可，照既有 `RepairGivenSteps`／`RepairWhenSteps`／`RepairThenSteps` 的分法）。
- 不寫產品碼。
- review 啟動 subagent；本輪所有 Test Scope 不得再有 undefined step，失敗只能是 assertion 或產品行為。有 issues 就修正再 review，直到沒有任何問題。通過前不解鎖 Phase 4。

**Parallel Hint**:
- T010–T025 各派一個獨立 subagent（共用 `PhotoFixtures` 與 `ScenarioWorld` 的照片標籤對照，T007 已建好）；T026 等全部回來再啟動 subagent 來 review。

- [ ] T010 [P] [BDD-ALIGN] `已有以下工務單：`
  - Read: `tms/src/test/java/com/athena/tms/bdd/support/` 裡建立 `repair_photos` 測試資料的 helper（以 `grep -rn "INSERT INTO repair_photos" tms/src/test/java` 找）
  - 建立已上傳照片時，把 `PhotoFixtures` 的合法 JPEG 寫進照片儲存，`storage_ref` 寫 `repair-photos/{tenantId}/{photoId}.jpg`。

- [ ] T011 [P] [BDD-RED] `"{員工}" 已上傳照片 "{照片}"，用途為 "{用途}"`
- [ ] T012 [P] [BDD-RED] `"{員工}" 已建立照片 "{照片}"，用途為 "{用途}"，還沒上傳內容`
- [ ] T013 [P] [BDD-RED] `照片 "{照片}" 已附在 "{單號}"`
- [ ] T014 [P] [BDD-RED] `別家飯店的員工已上傳照片 "{照片}"，用途為 "{用途}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/support/TenantFixtures.java`、`StaffFixtures.java`（另建一家飯店與員工的既有做法，見 `帳號與登入/員工登入.feature` 的別家飯店情境步驟）
- [ ] T015 [P] [BDD-RED] `"{員工}" 以 "{內容}" 上傳照片 "{照片}" 的內容`
- [ ] T016 [P] [BDD-RED] `"{員工}" 查看照片 "{照片}"`
  - Read: `tms/src/test/java/com/athena/tms/bdd/support/BddApiClient.java`（要能拿回二進位 body 與回應標頭；不夠就擴充 `exchange` 回傳位元組）
- [ ] T017 [P] [BDD-RED] `"{員工}" 建立照片，用途為 "{用途}"、拍攝時間為 "{拍攝時間}"`
- [ ] T018 [P] [BDD-RED] `"{工務}" 附照片 "{照片}" 以已修好完成 "{單號}"`
- [ ] T019 [P] [BDD-RED] `"{員工}" 補照片 "{照片}" 到 "{單號}"`
- [ ] T020 [P] [BDD-RED] `"{員工}" 從 "{任務}" 附照片 "{照片}" 開立水電工務單`
- [ ] T021 [P] [BDD-RED] `照片 "{照片}" 已上傳，存的是 "{內容}"`
- [ ] T022 [P] [BDD-RED] `照片 "{照片}" 仍未上傳`
- [ ] T023 [P] [BDD-RED] `看到的照片是 "{照片}" 存的內容`
- [ ] T024 [P] [BDD-RED] `系統沒有建立照片`
- [ ] T025 [P] [BDD-RED] `"{員工}" 打開 "{單號}" 時，"{種類}" 照片 {張數} 張都看得到`

- [ ] T026 subagent review (phase quality gate)
  - 驗證：對三份 Test Scope 跑 dry-run，undefined step 為 0；實跑時新情境的失敗只能是斷言或產品行為（例如上傳仍回 200、讀照片回 404 因為端點不存在），不能是步驟找不到。
  - 通過後只加 Phase 2 至 3 的測試檔，提交一次，提交訊息寫明這一批是先紅的測試。

## Phase 4A: ADD Feature File - backend/工務單/照片內容上傳與查看.feature

**Goal**: 上傳內容真的存進雲端儲存並檢查格式、大小、建立者與是否已附上；新增讀照片端點；建立照片的輸入錯誤回 422。

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
- 讀照片不加逐人權限檢查，不改 `SecurityConfig`。
- 不加 Flyway 遷移。

**Test Scope**:
- `specs/truth/features/backend/工務單/照片內容上傳與查看.feature`

- [ ] T027 [BDD-GREEN] 讓 Test Scope 全綠
  - 落點：`repair/RepairController.java`（PUT 讀 `Content-Type`、`Content-Length` 與輸入串流；新增 `GET /repair-photos/{photoId}/content` 回 `ResponseEntity<byte[]>`、`image/jpeg`、`Cache-Control: private, max-age=3600`；建立照片檢查 `purpose`、`takenAt` 回 `validation_failed`）、`repair/RepairPhotoContent.java`、`repair/RepairService.java`（`markUploaded` 換成 `storeContent`；新增 `readContent`）、`common/error/` 視需要新增 413、415 的例外類別（沿用 `ApiException`）。
- [ ] T028 [BDD-REFACTOR] 刪掉 `markUploaded` 與 `"upload:"` 假值的所有殘留，確認存照片只有 `GcsPhotoStorage` 一個實作、看模擬位址只有 `StorageConfig` 一處

## Phase 4B: MODIFY Feature File - backend/工務單/照片完整性與冪等送出.feature

**Goal**: 完修與開單的照片逐張檢查：已上傳、本飯店、用途相符、沒附在別張單。

**Shared Must Read**:
- `specs/truth/features/backend/工務單/照片完整性與冪等送出.feature` -> `功能: 照片完整性與冪等送出`
- `specs/truth/features/backend/工務單/dsl.md` -> `"{工務}" 附照片 "{照片}" 以已修好完成 "{單號}"`、`"{員工}" 從 "{任務}" 附照片 "{照片}" 開立水電工務單`
- `truth-delta.md` -> `/dsl-refine` MODIFY `照片完整性與冪等送出.feature` 列；`/api-plan` `CompleteTaskRequest.photoIds`、`tasksComplete`、`CreateRepairTicketRequest.photoIds` 列
- `specs/truth/contracts/components/schemas/任務.yaml` -> `CompleteTaskRequest`
- `specs/truth/contracts/components/schemas/工務單.yaml` -> `CreateRepairTicketRequest`
- `plan.md` -> 四處照片檢查怎麼共用？

**Boundary**:
- 共用檢查只有 `RepairService.requireAttachablePhotos` 一處；張數上下限留在各路徑。
- `bindUnclaimedPhotos` 不改。
- 退回重修要附這一輪照片的既有檢查保留。

**Test Scope**:
- `specs/truth/features/backend/工務單/照片完整性與冪等送出.feature`
- `specs/truth/features/backend/工務單/驗收不通過與重修.feature`（回歸：這一輪照片的規則）

- [ ] T029 [BDD-GREEN] 讓 Test Scope 全綠
  - 落點：`repair/RepairService.java`（`requireAttachablePhotos`；`requireUploadedPhotos` 改用它）、`service/TaskExecutionService.java`（`requireCompletionPhotos` 呼叫它）。
- [ ] T030 [BDD-REFACTOR] 確認開單、完修兩處不再各自查 `repair_photos.status`，只透過共用檢查

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

- [ ] T031 [BDD-GREEN] 讓 Test Scope 全綠
  - 落點：`repair/RepairService.java` 的 `appendPhotos`；`repair/RepairController.java` 只留空陣列的 422，超過 3 張交給服務。
- [ ] T032 [BDD-REFACTOR] 後端全套與 T001 基準比沒有新失敗；只加 Phase 4A 至 4C 的檔案，提交一次

## Phase 4D: 員工 App 拍照、選照片與顯示照片

**Goal**: 「拍照」打開相機、「從相簿選擇」打開相簿，照片縮小壓縮後上傳；工務單詳情與看大圖顯示真的照片；測試版照舊送固定測試照片。

**Shared Must Read**:
- `truth-delta.md` -> `/technical-research`「App 相機與照片」列；`/dsl-refine` 員工 App NOOP 列
- `specs/truth/techstack.md` -> App 相機與照片
- `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `getRepairPhotoContent`；`components/schemas/工務單.yaml` -> `RepairTicket.photos`、`completionPhotos`
- `spec.md` -> App 怎麼拍照、選照片、顯示照片？
- `research.md` -> App 用什麼拍照、選照片與壓縮？、App 怎麼顯示需要登入才拿得到的照片？
- `plan.md` -> App 怎麼接？

**Boundary**:
- 只加 `image_picker`；不加 `camera`、`flutter_image_compress`、`cached_network_image`。
- 測試版（`TEST_BACKDOOR`）的判斷只在 `core/repair_photo_capture.dart` 一處。
- 不改 Android `AndroidManifest.xml`。
- 不改離線佇列。

**Test Scope**:
- `cd app/mobile && flutter test`

- [ ] T033 加 `image_picker` 與 iOS 權限說明
  - `app/mobile/pubspec.yaml` 加 `image_picker`（`flutter pub add image_picker`，鎖當下穩定版）；`app/mobile/ios/Runner/Info.plist` 加 `NSCameraUsageDescription`「拍下要報修或已修好的地方，附在工務單上」、`NSPhotoLibraryUsageDescription`「選擇要附在工務單上的照片」；`cd app/mobile/ios && pod install` 後 `flutter build ios --simulator --debug` 能建置。
- [ ] T034 先寫會紅的畫面與單元測試
  - 新增 `app/mobile/test/repair_photo_capture_test.dart`：選「拍照」呼叫拍照函式時帶相機來源、選「從相簿選擇」帶相簿來源；取消時不呼叫 `createRepairPhoto`；拿到的位元組不是 JPEG 時丟出照片不能用，不上傳；拍攝時間用檔案時間；測試版回固定測試照片。
  - 擴充 `app/mobile/test/photo_test.dart`：看大圖依照片清單逐張顯示圖片（以假的 `ApiClient` 回固定 JPEG）、讀照片失敗時顯示「照片無法顯示」。
  - 新增或擴充工務單詳情的畫面測試（`engineering_contract_fields_test.dart` 或新檔）：報修照片與完修照片格子各自拿到 `image`，數量等於回應陣列長度。
  - 提交一次，訊息寫明先紅。
- [ ] T035 拍照與選照片
  - `core/repair_photo_capture.dart`：`captureAndUploadRepairPhoto` 加 `source` 參數與可替換的拍照函式；正式版 `ImagePicker().pickImage(source, maxWidth: 1600, maxHeight: 1600, imageQuality: 80)`；檢查 `FF D8 FF`；拍攝時間 `XFile.lastModified()`，拿不到用 `deviceNow()`。`core/api_client.dart` 的 `createRepairPhoto` 加 `takenAt` 參數。
  - 四個呼叫者把照片選單的結果傳進去，拍到的位元組存在 `RepairPhoto` 上供縮圖：`features/task/repair_sheet.dart`、`features/ticket/repair_ticket_form_sheet.dart`、`features/ticket/ticket_complete_sheet.dart`、`features/ticket/ticket_screen.dart`。
  - 權限被拒（`camera_access_denied`、`photo_access_denied`）與照片不能用，照 spec 的提示文字顯示，用既有的提示框元件。
- [ ] T036 顯示照片
  - 新增 `core/repair_photo_image.dart`（`ApiClient.instance.fetchRepairPhotoContent(id)`、依 id 記憶體快取、`MemoryImage`、失敗狀態）；`core/api_client.dart` 加 `fetchRepairPhotoContent`。
  - `features/ticket/ticket_detail_engineering.dart`、`features/ticket/ticket_detail_housekeeping.dart` 的 `TmsPhotoCell` 給 `image:`；`features/ticket/ticket_screen.dart` 的看大圖改依 `photos`／`completionPhotos` 建 `TmsViewerPhoto(image: …)`。
- [ ] T037 [BDD-GREEN] `flutter test` 與 `flutter analyze` 全綠
- [ ] T038 [BDD-REFACTOR] 刪掉不再被正式版呼叫的程式路徑（`takePhotoJpeg` 只留給測試版）；提交一次

## Phase 4E: plan-side quality gate

**Goal**: 產品對齊本輪回追到的 plan-side 產物。`Test Scope` 全綠不能代替本題。

**Read**:
- `spec.md`、`plan.md`、`research.md`、`features/acceptance/*.feature`

**Boundary**:
- 不改 truth、不加 feature、不重跑 GREEN 當通過條件。

- [ ] T039 subagent review（`plan.md` quality gate）：逐項對 `plan.md`「程式現況跟本版差在哪裡」15 列與落點清單，確認每一列都已處理或有理由不處理。
- [ ] T040 subagent review（`spec.md` 與 acceptance quality gate）：逐條對 spec 的系統規則、邊界情況、白話 NFR 與三份 acceptance feature，確認都有 truth 情境、Flutter 測試或 Phase 5 的實機步驟承接。
- [ ] T041 subagent review（`research.md` quality gate）：確認 `research.md` 列的設定項目、模擬服務參數與「還沒查證的風險」都已在實作或 `implement-notes.md` 有結論。

## Phase 5: Appium 回歸與實機驗證

**Goal**: 測試版在模擬器上的行為不變；真相機、相簿與顯示在實機上成立。

- [ ] T042 Appium 回歸
  - 跑 `app/bdd/run-module.sh 工務單`（測試版，送固定測試照片；後端要連有 `fake-gcs-server` 的本機環境）。結果與 T001 基準比沒有新失敗。
  - 注意：測試版送的 1 × 1 固定照片是合法 JPEG，會通過後端檢查；若 Appium 情境改由後端讀照片斷言，用它的位元組比對。

- [ ] T043 實機手動驗證（iPhone 實機，連本機手動測試環境 8083）
  - 起環境：`docker compose up -d`；`bash scripts/manual-env/up.sh`；確認後端啟動日誌寫出儲存空間 `tms-repair-photos-manual` 與模擬服務。
  - 以非測試版建置裝到實機：`cd app/mobile && flutter run --release --dart-define=API_BASE_URL=http://<電腦區網 IP>:8083/api/v1`（`API_BASE_URL` 是 `core/api_client.dart` 讀的變數，預設 `http://localhost:8080/api/v1`）。
  - 逐項走並截圖存到 `implement-notes.md`：
    1. 第一次按「拍照」跳出相機權限詢問，文字正確；允許後打開相機，拍 2 張，格子出現縮圖。
    2. 按「從相簿選擇」挑一張 HEIC 照片與一張 PNG 截圖：能轉成 JPEG 的上傳成功；轉不成的跳「這張照片不能用」（記下實際結果，作為 `research.md` 風險的結論）。
    3. 送出 W 單後，用另一支手機或模擬器以工務帳號打開，縮圖與看大圖都是剛才的照片，左右切換順序正確。
    4. 工務附 1 張完修照片完成；主管帳號打開看得到完修照片；驗收不通過、重修再完修後只看到新的完修照片。
    5. 到 iOS 設定關掉相機權限，再按「拍照」，跳「沒有相機權限」。
    6. 打開一張這版之前開的舊單，照片格子顯示「照片無法顯示」，其他內容正常。
    7. 以 `curl` 確認雲端儲存裡有物件：`curl -s http://localhost:4443/storage/v1/b/tms-repair-photos-manual/o | jq '.items[].name'`，名稱是 `repair-photos/{飯店編號}/{照片編號}.jpg`。
  - 照片大小：從 `curl` 列出的 `size` 確認一般照片在 1 MB 以下、長邊不超過 1600 像素（下載一張用 `sips -g pixelWidth -g pixelHeight` 看）。

- [ ] T044 收尾
  - `implement-notes.md` 記下 T043 每一項的結果、`research.md` 風險的結論，以及 spec「還有什麼沒定？」四題的現況。
  - 後端全套、`flutter test` 最後再各跑一次，與 T001 基準比沒有新失敗。

## 新句型清單（給 Phase 3）

以下 15 句在本分支 dry-run 時是 undefined，都是本版新增，權威位置都是 `specs/truth/features/backend/工務單/dsl.md`：

- Given：`"{員工}" 已上傳照片 "{照片}"，用途為 "{用途}"`；`"{員工}" 已建立照片 "{照片}"，用途為 "{用途}"，還沒上傳內容`；`照片 "{照片}" 已附在 "{單號}"`；`別家飯店的員工已上傳照片 "{照片}"，用途為 "{用途}"`
- When：`"{員工}" 以 "{內容}" 上傳照片 "{照片}" 的內容`；`"{員工}" 查看照片 "{照片}"`；`"{員工}" 建立照片，用途為 "{用途}"、拍攝時間為 "{拍攝時間}"`；`"{工務}" 附照片 "{照片}" 以已修好完成 "{單號}"`；`"{員工}" 補照片 "{照片}" 到 "{單號}"`；`"{員工}" 從 "{任務}" 附照片 "{照片}" 開立水電工務單`
- Then：`照片 "{照片}" 已上傳，存的是 "{內容}"`；`照片 "{照片}" 仍未上傳`；`看到的照片是 "{照片}" 存的內容`；`系統沒有建立照片`；`"{員工}" 打開 "{單號}" 時，"{種類}" 照片 {張數} 張都看得到`
