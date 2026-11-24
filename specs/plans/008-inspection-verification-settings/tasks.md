# Tasks: 查房與驗收開關，住客的「已完成」跟看板的「已結案」一致

**Plan Package**: `specs/plans/008-inspection-verification-settings`
**Core Inputs**: `spec.md`, `plan.md`, `truth-delta.md`, `specs/truth/contracts/**`, `specs/truth/data/data-model.dbml`, `specs/truth/features/backend/**`, `specs/truth/features/guest-web/**`, `specs/truth/features/frontdesk-web/**`

本檔照實際動工的順序排：每一段先把會紅的測試提交，再寫讓它變綠的產品碼。後端先、住客頁次之、櫃檯設定頁再次之，最後是網頁行為規格、實機驗收與員工 App 核對。`research.md` 與 `ui/` 本版不存在，理由見 `plan.md` 開頭。

## 怎麼跑測試？

每一題的驗證都用下面這幾條，不另寫。

- 後端全套（JDK 25、OrbStack）：
  `JAVA_HOME="$(/usr/libexec/java_home -v 25)" TESTCONTAINERS_RYUK_DISABLED=true ./tms/mvnw -f tms/pom.xml test -DargLine="-Dapi.version=1.44"`。
- 只跑後端行為規格：上一條加 `-Dtest=RunTruthFeaturesTest`；只跑一個模組或一份檔再加 `-Dcucumber.features=../specs/truth/features/backend/<模組>/<檔>.feature`；找沒有實作的句子加 `-Dcucumber.execution.dry-run=true`。只跑單一 feature 時多出的 CucumberTestEngine discovery 錯誤可以忽略。
- 網頁單元測試與型別：`cd app/web && npm test`，`cd app/web && npm run typecheck`。
- 櫃檯 Web 與住客頁行為規格：`bash app/web-bdd/run-feature.sh [--keep] <路徑> [desktop|ipad|phone]`，路徑以 `frontdesk-web/` 或 `guest-web/` 開頭時相對於 `specs/truth/features/`；跑完用 `bash app/web-bdd/run-feature.sh --stop` 收掉。
- 員工 App：`cd app/mobile && flutter test`；行為規格 `app/bdd/run-module.sh 工務單`（模擬器一次只給一個 agent 用）。
- 同一個 worktree 裡有多個 agent 時，Maven 本機倉庫與模擬器不要共用：各自用 `-Dmaven.repo.local=<自己的路徑>`，模擬器排隊用。

「全綠」都是指「除了 T001 記下的既有失敗之外全綠」。

## Phase 1：量改動前的基準

- [ ] T001 量基準
  - 只做：在任何產品碼或測試碼改動之前，跑後端全套、`npm test`、`bash app/web-bdd/run-feature.sh --keep frontdesk-web/ desktop`、`bash app/web-bdd/run-feature.sh --keep guest-web/ phone`、`flutter test`、`app/bdd/run-module.sh 工務單`，把每一組的失敗清單（檔名與行號，或測試類別與方法）與失敗數記到 `implement-notes.md`。分支上已經有別人的改動時，改在 `25bfec0` 的乾淨 worktree 量。
  - 不做：修任何既有失敗。

## Phase 2：後端行為規格先紅，先提交

**目標**：把 `truth-delta.md` `/dsl-refine` 段的後端 feature 與句型寫好、接上步驟實作，讓它們能跑而且照預期失敗，單獨提交一次。

- [ ] T002 寫後端 feature 與 DSL
  - Read：`spec.md`「系統要守哪些規則？」與情境 1 至 3、5；`truth-delta.md` `/dsl-refine` 段的 backend 各列。
  - 只做：新增 `specs/truth/features/backend/櫃檯設定/查房與驗收開關.feature` 與同目錄 `dsl.md`；在 `backend/查房/免查房鎖定與結案.feature` 加「關掉需要房務主管查房後才開工」「再打開後才開工」兩條 Rule；在 `backend/工務單/櫃檯轉來的工務單.feature` 加等驗收時住客讀到還沒結束、關掉驗收後完修即結案且不進待驗收、開工後才關掉照新設定結案、住客要等結束才能評分；在 `backend/工務單/完修與回到原任務.feature` 加免驗收結案時推播報修人、原任務停住原因照做；在 `backend/工務單/工務視角維修與開單.feature` 加免驗收結案時暫時處理照建追蹤單、無法修復照轉停用待確認；在 `backend/工務單/主管驗收的狀態守衛.feature` 加免驗收結案的單不能再驗收。新句子寫進權威的 `dsl.md`。
  - 不做：改既有 Rule 的語意，只有 truth-delta 列為 MODIFY 的才改。

- [ ] T003 接上後端步驟實作
  - Read：T002 的 `dsl.md` 各列；`tms/src/test/java/com/athena/tms/bdd/steps/inspection/InspectionGivenSteps.java`（既有的免查房開關寫法）、`tms/src/test/java/com/athena/tms/bdd/steps/repair/`。
  - 只做：每一句新句子一個步驟實作；`"{員工}" 已在櫃檯設定把「{設定}」設為 "{開關}"` 要真的打 `PATCH /desk/settings`，不直接寫 `tenant_config`。
  - 驗證：對 `backend/櫃檯設定`、`backend/查房`、`backend/工務單` 跑 dry-run，沒有實作的句子為 0；實跑時新情境的失敗只能是斷言或產品行為（例如 404、狀態不對），不能是步驟找不到。

- [ ] T004 提交會紅的後端測試
  - 只做：只加 T002、T003 的檔案，提交一次。提交訊息寫明這一批是先紅的測試。

## Phase 3：後端

**目標**：讓 Phase 2 的情境變綠，既有後端全套照基準。

- [ ] T005 V30 擴充設定鍵
  - Read：`tms/src/main/resources/db/migration/V27__self_inspection_allowed_key.sql`；`specs/truth/data/data-model.dbml` 的 `tenant_config.key`。
  - 只做：新增 `V30__repair_verification_required_key.sql`，照 V27 的寫法 DROP 再 ADD `chk_tenant_config_key`，清單是 V27 的清單加上 `repair_verification_required`。不回填資料。
  - 不做：改任何既有遷移的任何一個字，包括註解，否則 Flyway checksum 會壞。

- [ ] T006 櫃檯設定端點
  - Read：契約 `specs/truth/contracts/groups/營運管理/櫃檯設定.yaml`、`components/schemas/櫃檯設定.yaml`；`plan.md`「設定 API 為什麼是 PATCH」「開關的值怎麼存？」。
  - 只做：`service/ManagerReviewSettings.java`（讀兩個開關、只寫有帶的鍵、記 `updated_by`）與 `controller/DeskSettingsController.java`（`GET`、`PATCH /api/v1/desk/settings`）；`SecurityConfig` 讓這一支只放 `manager`、`duty_manager`，排在 `/api/v1/desk/**` 前面。PATCH 先讀成 JSON 樹，分得出欄位沒帶、帶 null、帶非布林值。
  - 驗證：`backend/櫃檯設定/查房與驗收開關.feature` 全綠。

- [ ] T007 工務完修讀驗收開關
  - Read：`plan.md`「工務單直接結案走哪一條路？」；`service/TaskExecutionService.java` 的 `completeRepair`。
  - 只做：`closeRepair` 改成「完修結果本來就直接結案，或飯店不需要工務主管驗收」；後者（不是不需處理而直接結案）在完成紀錄的 payload 加 `verificationWaived: true`。讀開關走 `ManagerReviewSettings`。
  - 不做：改 `TaskEngine.complete`、`RepairService.resolveOnComplete`、`RepairResolution`。
  - 驗證：`backend/工務單` 全綠；`backend/查房` 全綠（開工鎖定沒被碰到）。

- [ ] T008 住客需求帶結束時間
  - 只做：`entity/Task.java` 加對應 `ended_at` 的唯讀欄位與 getter（寫入仍只由任務引擎的 SQL 做）；`GuestTaskView` 多帶 `endedAt`。`/guest/requests` 與 `/api/v1/guest-requests` 共用它，兩邊一起有。
  - 驗證：`backend/工務單/櫃檯轉來的工務單.feature` 裡「住客讀到這張需求已結束、還沒結束」的情境綠。

- [ ] T009 評分只收已經結束的需求
  - Read：`service/TaskEngine.java` 的 `rate`。
  - 只做：守衛條件改成 `state IN ('done', 'inspected', 'closed') AND ended_at IS NOT NULL AND rating IS NULL`；不符合時照舊回 409 `state_conflict`。櫃檯當場回答的單後端照舊不擋。
  - 驗證：評分相關情境全綠。

- [ ] T010 後端回歸並提交
  - 驗證：後端全套與 T001 的基準比，沒有新的失敗。
  - 只做：提交 T005 至 T009。

## Phase 4：住客頁單元測試先紅，先提交

- [ ] T011 寫住客對照的單元測試
  - Read：`plan.md`「結束的規則放在哪裡？」；`app/web/src/guest/lib/stage.test.ts`、`app/web/src/lib/board-columns.ts`。
  - 只做：新增 `app/web/src/lib/awaiting-review.test.ts`，涵蓋十個狀態各配有、沒有 `endedAt`；在 `stage.test.ts` 加 `done` 沒有 `endedAt` 是「處理中」、四步走到第三步、在進行中段落、不能評分；`done` 有 `endedAt` 是「已完成」；`closed` 是「已完成」且能評分；有 `answer` 的不能評分；`cancelled` 不能評分。
  - 驗證：`npm test` 只有這些新測試失敗，失敗原因是行為不對或函式不存在。
  - 只做：提交這些測試。

## Phase 5：住客頁

- [ ] T012 住客頁改用結束規則
  - 只做：`GuestTask` 型別加 `endedAt`；新增 `lib/awaiting-review.ts` 的 `awaitsReview`（`done` 而且 `endedAt` 是空的），`board-columns.ts` 的 `columnOf` 改用它；`guest/lib/stage.ts` 的 `stageOf` 在 `awaitsReview` 時回 `PROCESSING`，刪掉 `CLOSED` 列讓 `closed` 對到 `COMPLETED`；各函式與元件把 `endedAt` 傳進去。
  - 驗證：`npm test` 全綠、`npm run typecheck` 沒有錯誤。
  - 只做：提交。

## Phase 6：櫃檯設定頁

- [ ] T013 設定頁的路由與權限
  - 只做：`lib/paths.ts` 加 `settings`；`lib/route-access.ts` 加只有 `manager`、`duty_manager` 能看設定頁的判斷，配單元測試；`router.tsx` 掛上設定頁，其他角色導回總覽看板。
  - 驗證：`npm test` 全綠。

- [ ] T014 側欄的「設定」改成真的入口
  - 只做：`shell/unopened-entries.ts` 刪掉「設定」，更新 `unopened-entries.test.ts`；`DeskShell` 側欄對兩種主管角色畫「設定」連結，所在頁時標成目前頁；頁首支援第三頁，設定頁只有標題「設定」。
  - 驗證：`npm test` 全綠。

- [ ] T015 設定頁本體
  - Read：`spec.md`「設定頁長什麼樣？」；`app/web/src/components/`。
  - 只做：`pages/settings/SettingsPage.tsx`：讀取、骨架、讀不到資料與重試、兩個開關、按下後等回覆、失敗退回並顯示「沒有儲存」；API 呼叫加在櫃檯的 API 層；四種語言的文字加進 `i18n/locales/`。
  - 驗證：`npm test`、`npm run typecheck` 全綠；本機打開設定頁，兩個開關能切換，重新整理後值還在。
  - 只做：提交 T013 至 T015。

## Phase 7：網頁行為規格

- [ ] T016 住客頁行為規格
  - Read：`truth-delta.md` `/dsl-refine` 段 guest-web 各列。
  - 只做：改 `guest-web/dsl.md` 的狀況對照（新增等驗收、等查房、免驗收結案三種狀況，已結案改成可評分），改 `我的需求/狀態對照表.feature`、`我的需求/狀態訊息框.feature`、`取消與評分/評分.feature`；補新句子的步驟實作。先跑一次確認新情境照預期紅或綠，再提交。
  - 驗證：`bash app/web-bdd/run-feature.sh guest-web/ phone` 全綠。

- [ ] T017 櫃檯 Web 行為規格
  - Read：`truth-delta.md` `/dsl-refine` 段 frontdesk-web 各列。
  - 只做：新增 `frontdesk-web/設定/查房與驗收開關.feature` 與 `dsl.md`；改 `共用外框/尚未開放的入口.feature`，把「只有主管與值班主管的側欄上有「設定」」搬到 `共用外框/側欄與工具列.feature`；在 `單筆詳情/狀態標籤與狀態訊息卡.feature` 加免驗收結案的工務單；補步驟實作。
  - 驗證：`bash app/web-bdd/run-feature.sh frontdesk-web/ desktop` 與 `ipad` 全綠。
  - 只做：提交 T016、T017。

## Phase 8：實機驗收與員工 App

- [ ] T018 手動測試環境走一次
  - Read：`scripts/manual-env/README.md`。
  - 只做：起手動測試環境，照 `spec.md` 情境 1 至 4 各走一次：住客報修、櫃檯派工務、工務完修、住客看到處理中、主管驗收、住客看到已完成並評分；主管關掉驗收後再走一次；主管關掉查房後開一間續住整理；櫃檯人員看不到設定。每一步截圖放進 `evidence/`，結果記到 `implement-notes.md`。

- [ ] T019 核對員工 App
  - Read：契約 `components/schemas/任務.yaml` 的 `TaskCompletion`；`app/mobile/lib/features/ticket/repair_labels.dart`、`ticket_detail_engineering.dart`、`ticket_detail_housekeeping.dart`、`app/mobile/lib/features/task/task_screen.dart`、`app/mobile/lib/core/widgets/tms_repair_ticket_row.dart`；`truth-delta.md` 員工 App 那一列。
  - 只做：確認工務以已修好、暫時處理、無法修復完修而後端回 `closed` 時，完成畫面、工務單詳情與單列都寫「已結案 · 免驗收」，不寫成「不需處理」；確認工務首頁「今天已完修」計數與主管待驗收照常。需要改程式時，先補會紅的 `flutter test` 再改。照 `truth-delta.md` 員工 App 那一列，在 `ios-app/工務單` 的 truth 補上這個說法。
  - 驗證：`flutter test` 全綠；`app/bdd/run-module.sh 工務單` 與基準比沒有新的失敗。

- [ ] T020 全套回歸
  - 驗證：後端全套、`npm test`、`frontdesk-web/` 兩種裝置、`guest-web/`、`flutter test`、`app/bdd/run-module.sh 工務單` 與 T001 的基準比，沒有新的失敗。結果記到 `implement-notes.md`。
