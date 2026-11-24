# 實作紀錄：派給工務的單一律是工務單

## T002 改動前的基準

量的版本：`b1a406c`（HEAD）。量的時候工作目錄只有 T001 改的 `scripts/manual-env/start.sh` 與使用者自己的 `scripts/manual-env/` 改動，沒有產品碼或測試碼改動。後端全套在 HEAD 的乾淨 worktree（`.claude/worktrees/006-baseline`）量，其他四組在主工作目錄量。員工 App 行為規格用 `iPhone 18 Pro`（`B07951C2-DF41-4AF6-9C98-0DFC7413ED8C`，`run-module.sh` 預設的 UDID 已不存在）與 8081 的 BDD 後端堆疊（5434／9094，HEAD 版 jar，`TMS_TEST_CLOCK_ENABLED=true`）。五組有一部分是同時跑的，機器負載偏高。

`truth` 在 `b1a406c` 已經改成新版，所以基準裡會有本輪新句的 undefined 與新行為的失敗；這些是 Phase 3 與 Phase 4 要處理的，不算既有毛病。

| 組 | 指令 | 場景／測試數 | 失敗 |
| --- | --- | --- | --- |
| 1 後端全套 | 「跑測試的方式」第一條 | 656 支（行為規格 397 場景、舊整合 172、relay-wake 3、其餘單元） | 14（3 failure、11 error），全部在行為規格 |
| 2 櫃檯 Web | `run-feature.sh --keep frontdesk-web/ desktop` | 432 場景 | 5（4 failed、1 undefined） |
| 3 住客頁 | `run-feature.sh --keep guest-web/ phone` | 236 場景 | 3 failed |
| 4a 員工 App 工務單 | `app/bdd/run-module.sh 工務單` | 73 次執行（含重跑） | 11（10 failed、1 undefined） |
| 4b 員工 App 待認領 | `app/bdd/run-module.sh 待認領` | 21 | 0 |
| 5 App 單元 | `flutter test` | 517 | 0 |

**1 後端全套的失敗**（舊整合 172、relay-wake 3、單元測試全綠）：

- undefined `specs/truth/features/backend/工務單/櫃檯轉來的工務單.feature:31`、`:39`、`:48`
- failed `specs/truth/features/backend/工務單/櫃檯轉來的工務單.feature:18`、`:25`（工務待認領沒有 W-0621）
- undefined `specs/truth/features/backend/櫃檯看板與派單/櫃檯派單.feature:104`、`:113`、`:121`
- failed `specs/truth/features/backend/櫃檯看板與派單/櫃檯看板讀取.feature:219`（`tasks.state` 預期 triage，實際 pool）
- undefined `specs/truth/features/backend/櫃檯看板與派單/櫃檯看板讀取.feature:226`
- undefined `specs/truth/features/backend/櫃檯看板與派單/派出後復原.feature:100`、`:108`、`:119`
- failed `specs/truth/features/backend/櫃檯看板與派單/派出後復原.feature:129`（找不到工務單 W-0622）

**2 櫃檯 Web 的失敗**：

- failed `specs/truth/features/frontdesk-web/即時更新/只換有變化的那張卡片.feature:14`
- failed `specs/truth/features/frontdesk-web/即時更新/連線異常提醒列.feature:37`、`:63`
- failed `specs/truth/features/frontdesk-web/待派單/待派單清單.feature:53`（頁尾說明，T048 才改）
- undefined `specs/truth/features/frontdesk-web/待派單/待派單清單.feature:150`（T028 改名的句）

前三筆是即時更新的時序案例，都是「小美在手機上認領 412」之後等卡片變化，負載高時容易逾時；本輪不碰即時更新。

**3 住客頁的失敗**：

- failed `specs/truth/features/guest-web/即時更新/連線異常時定期重讀.feature:17`
- failed `specs/truth/features/guest-web/設備報修/組成文字送出.feature:72`、`:82`（「冷氣」命中工務規則，T039 之後該綠）

**4a 員工 App 工務單的失敗**：

- failed `specs/truth/features/ios-app/工務單/主管視角.feature:40`（例子第 3 列）、`:107`、`:179`
- failed `specs/truth/features/ios-app/工務單/報修照片與送出.feature:33`
- failed `specs/truth/features/ios-app/工務單/工務視角.feature:30`、`:41`、`:61`、`:120`（例子第 3 列）、`:133`、`:152`
- undefined `specs/truth/features/ios-app/工務單/工務視角.feature:50`（T027 的句）

其中 6 筆的錯誤是「等測試後門（127.0.0.1:9099）就緒逾時」（`主管視角:40`、`工務視角:30`、`:41`、`:61`、`:120`、`:133`），4 筆是等不到畫面元素（`主管視角:107` 的 `managerTicket.result`、`:179` 的 `manager.conflict-alert`、`報修照片與送出:33` 的 `ticket.screen`、`工務視角:152` 的 `home.screen`）。後門逾時是環境訊號，不是產品行為；T051 比對時這幾筆若轉綠不算問題，若有新的後門逾時要重跑一次再判斷。

報告與摘要留在 `/tmp/006-baseline/`（`truth.json`、`frontdesk-failures.txt`、`guest-failures.txt`、`app-repair.html`）；摘要腳本 `/tmp/006-tools/cuke_failures.py <cucumber json>` 印出每份報告的場景數與非通過清單。

## 審查第二輪的兩個小項怎麼處理

- n1（T046「照 dbml 的重放規則從事件重放得出同樣的任務列」沒有重放程式、也沒點名欄位）：比對的欄位定為 dbml `tasks` Note 重放規則點名的 `state`、`dept`、`type`、`reminder_stage`、`pool_source`、`task_no`，再加 `urgent`。T004 在 `V29EngineeringTaskBackfillTest` 放了兩個 helper：`projection(tenantId, taskId)` 讀任務列這七欄，`replay(tenantId, taskId)` 只認 `created`、`triaged`、`triage_undone` 三種事件，照重放規則疊出同樣七欄（`triaged` 派給工務時 `type` 設為 `repair`；`triage_undone` 回到 `triage`／`frontdesk`、清 `reminder_stage` 與 `pool_source`，被收回的 `triaged` 帶 `former_type`／`former_urgent` 時照還原，自己帶 `type`／`task_no` 時照設）。T046 對每張被 V29 改到的單斷言 `projection == replay`。`created` 快照不帶 `urgent`，所以 `replay` 的 `urgent` 從 false 起算；V29 只動非急件的單，這個限制不影響 T046。派單與復原那兩條路的同樣欄位，由後端行為規格 T018–T022 的 `必查`（類別、急件、工務待認領）直接驗，不另寫重放。
- n2（T052 停舊後端前先做唯讀預檢）：T052 在 `pg_dump` 之後、`stop.sh` 之前，以唯讀交易（`BEGIN READ ONLY`）對手動環境的 `tms_manual` 跑 V29 第 1 步與第 5 步的兩支查詢：不屬於「櫃檯派來的」與「住客經規則直接進來的」兩種的工務單（列出 id、`source`、`state`、最新 `triaged` 的 `actor_id`），以及「部門工務、沒結束、沒有工務單」的總數與兩種各自的張數。第一支不是 0 筆就不停舊後端、不上線，先回報那幾張；這樣舊後端不會為了一個起不來的新版被停掉。兩支查詢在 T046 寫 V29 時一併放進本檔，T052 照抄。

## 自主決策

- T003–T005 的程式在剩下的基準（住客頁、員工 App）還在跑時，先在 `.claude/worktrees/006-baseline`（HEAD 的乾淨 worktree）寫好並驗證，T002 回寫之後才搬進主工作目錄、依序回寫。基準跑的是 HEAD 的 jar 與 App 建置，不受影響；搬進主工作目錄後 T004、T005 各自重跑一次驗證。T003 的後端全套在 worktree 跑：失敗清單與 T002 完全相同（656 支，14 筆）。
- T001 用 `python3 -m http.server 8083` 當假後端驗證：`/guest/abc`、`/guest/stream`、`/api/health`、`/staff/stream` 經 5300 都打到 8083。
- T005 的類別文字 identifier 掛在 `TmsRoomSquare` 新增的可選 `labelId` 上（有值時用 `TmsSemantics` 包住類別那行字），房務待認領的呼叫點不帶，畫面不變。
- T027 實跑時發現：T005 的 `TmsRoomSquare._labelWithId` 用 `TmsSemantics(label: label)` 包同字的 `Text`，iOS 無障礙標籤會變成同一個字重複兩行（`repair\nrepair`）；`PoolCardSubtitle`（`app/mobile/lib/features/pool/pool_widgets.dart:134`）也是同樣寫法。Phase 3 不改產品碼，所以 T027 的斷言改成「每一行都等於該類別」，兩種寫法都能驗；Phase 4E 做 T044 時順手把 `_labelWithId` 的 `label:` 拿掉，讓讀屏只念一次。

## Phase 3 測試對齊

四條線平行：後端櫃檯側（T006–T008、T012、T016–T022）、後端工務單側（T009–T011、T013–T015、T023–T026）、App（T027）、網頁（T028–T031）。後端兩條各自在 `/tmp` 的副本跑 Maven，不在主工作目錄的 `tms/` 建置。

**Dry-run 的 undefined 數**（T032 審查者自己跑）：`backend/櫃檯看板與派單` 97 場景 0、`backend/工務單` 78 場景 0、`ios-app/工務單/工務視角.feature` 28 場景 0、`frontdesk-web/待派單/待派單清單.feature` 16 場景 0、`guest-web/設備報修/組成文字送出.feature` 9 場景 0。`run-feature.sh` 吃 `MAVEN_ARGS="-Dcucumber.execution.dry-run=true"`（Maven 3.9 自己讀這個變數），但仍會先起後端與 vite。

**實跑結果與基準的差**（失敗都是斷言或產品行為，Phase 4 修好之前預期紅）：

- 後端全套行為規格：397 場景，26 failed、5 skipped。基準的 `櫃檯派單.feature:121`（派給房務不是工務單）轉綠。新增的紅：17 筆停在 T006 的 Given「已把…派給工務」（`tasks.type` 預期 repair、實際 other），分布在 `櫃檯看板與派單` 各檔與 `員工事件串流/收得到哪些單的訊號.feature:54`；`櫃檯轉來的工務單.feature` 五個場景的 Background 用同一句，所以 skipped；`開立工務單與急件.feature:121` 停在 T011 的推播鍵（產品仍以任務 id 當鍵，`RepairService.java:754`，Phase 4 改成工務單號）。
- 櫃檯 Web desktop：432 場景，2 failed，都在基準內（`只換有變化的那張卡片:14`、`待派單清單:53`）；`待派單清單:150` 與兩筆 `連線異常提醒列` 轉綠。
- 住客頁 phone：236 場景，7 failed。`組成文字送出:72`、`:82` 在基準內；新增 5 筆都是 T031 鋪「已結案」的工務單時 `POST /repair-tickets/{id}/verify` 回 404（派單還不開工務單），`評分:93`、`狀態對照表:6`、`:54`、`:101`、`狀態訊息框:28`，T033 之後該綠。
- App `工務視角.feature:50`：停在類別標示斷言，畫面是 `repair`。

**Phase 3 的取捨與待回交上游的缺口**：

- T018：`backend/櫃檯看板與派單/dsl.md` 這列的 `再讀確認` 要求 `readRepairTicket` 回傳的標題，但 `RepairTicket` 契約（`specs/truth/contracts/components/schemas/工務單.yaml`、`shared.yaml` 的 `RepairTicketSummary`）沒有 `title`。stepdef 改驗 `tasks.title` 與工務待認領 `PoolItem.title`（契約有這欄）。待回交 `/dsl-refine` 修正這列的 `再讀確認`，或由 `/api-plan` 決定工務單讀取要不要帶標題。
- T016：句型只給部門，服務項目的類型一律給 `amenity`（不給 `repair`），讓 `櫃檯看板讀取.feature:226` 證明「單靠部門是工務」就留在待派單。原本跟部門給 `repair` 會同時命中「類型是修繕」那一半，產品只看類型也會過；T032 審查抓到後改正，重跑 `:226` 仍停在 `tasks.state 預期 triage，實際 pool`。新增的項目加在住客頁目前顯示的清單上，不覆蓋出廠清單。
- T015：這列要求「`dept=engineering` 的 `triaged` 恰好一筆」，復原後再派給工務會有兩筆。目前沒有場景在這種單上用這句，照列實作；以後有這種場景要先回交上游改列。
- T029：`TaskTableRows`／`BackendChannel` 直接寫 SQL 鋪的櫃檯派出工務列，`created` 事件的類型是 `repair`、沒有 `task_no`，`triaged` 的 payload 沒有 `former_type`／`former_urgent`。網頁沒有場景對鋪好的列做復原或讀它的 T- 號，所以不影響本輪；但 T036 之後，鋪出來的工務列若被復原，類型還原不了。是已知的簡化。
- T026：櫃檯轉來的工務單.feature 列出的七句都已有 stepdef，dry-run 0 undefined，沒有補。
- T030：stepdef 本身不必改；共用的 `BackendChannel.setClassificationRule` 註解原本說命中就進該部門待認領，改成房務直進、工務先到待派單。它整份取代規則清單而不是加一條，住客案例開場都沒有規則，結果相同，不改。
- T031：住客那一步沒有真的打住客端點，`BackendChannel.seedEngineeringGuestRequest` 以 SQL 寫出住客來源的待派單列，再以林佩君呼叫 `deskAssign` 派給工務。沿用這個類別既有的做法（Javadoc 有寫）：住客端點每房 10 分鐘只收 3 張，鋪多列會被擋。結果的任務狀態相同，工務單由後端在派單時開。
- T027：比對類別標示時逐行比，原因見上面「自主決策」的 T005 那條。
- 仍保護舊行為的測試只剩 `tms/src/test/resources/features/01-guest-requests.feature:114`（住客打「冷氣」直接進工務待認領），歸 Phase 4C 的 T038 處理。

**T032 審查**：第一輪 FAIL，兩項：T016 的類型（見上）與本節沒寫。修正後第二輪由另一位審查者重審：PASS，沒有問題；六個範圍 dry-run 的 undefined 都是 0（另加 `backend/待認領` 21 場景 0）。

## Phase 4 實作紀錄

後端 Test Scope 都在主工作目錄跑（「跑測試的方式」那幾條）；員工 App 行為規格用 `iPhone 18 Pro`（`B07951C2-…`）與 8081 的 BDD 後端（5434／9094，由本輪的 jar 複本 `/tmp/006-p4/tms-app-8081.jar` 以 `TMS_TEST_CLOCK_ENABLED=true` 啟動）。

- T033：4A 的 Test Scope 含 `櫃檯派單.feature`「成為工務單」那條（要求待認領單項的 `repairCategory` 與來源「櫃檯」）與整個 `工務單` 模組（`開立工務單與急件.feature:121` 要求以工務單號當推播鍵、`櫃檯轉來的工務單.feature` 要求來源與類別），所以 T033 一併做了：`deptPool` 回 `repairCategory`、`source` 是 `guest` 也算 `desk_forward`；`RepairService.notifyUrgent` 的防重複鍵改成工務單號。這些原本列在 T036／T041，到那兩題只剩驗證。
- T033：Phase 3 的 `DeskCommonSteps.assertBecameRepairTicket` 用 jsonb 的 `?` 運算子，JDBC 把它當參數佔位，查詢直接報「未設定參數值 2」；Phase 3 時這句沒跑到（前面的 Given 就停了）。改成 `jsonb_exists(payload, 'former_type')`，斷言的意思不變。
- T034 盤點（`grep -rn "new TaskCreateCommand(" tms/src/test` 與 `grep -rn "INSERT INTO repair_tickets" tms/src/test`）：會以工務建單的是 `bdd/support/DeskBoardFixtures`（三處建單，原本由 `insertRepairTicket` 補單）、`bdd/support/TaskFixtures.Builder`（`RepairGivenSteps` 的「已有以下工務單：」與工務今日工作量兩處，原本自己 `INSERT`）、`integration/.../EntityTableGivenSteps:304`、`integration/.../StreamWhenSteps` 兩處、`service/TaskEngineRowWriteTest`。全部改成建單帶 `RepairTicketDraft`；要指定單號的用新的 `TaskFixtures.withRepairTicketNo`（先把計數設成前一號、建完放回原值，鋪單不影響之後實際開單的號碼），建立時間、急件、驗收等欄位建單後 `UPDATE`。`tms/src/test` 已沒有 `INSERT INTO repair_tickets`。其餘建單呼叫（`RelayWakeSteps`、`integration/.../TaskFixtures`、`BackboneWhenSteps`）都不是工務。`EntityTableGivenSteps.insertTaskDirectly`（住客來源沒有訂單時直接寫 SQL）不經過 `TaskEngine`，`02-staff-pool.feature:145` 那列用它鋪一張沒有工務單的工務單，只驗部門隔離，沒改。
- T035：舊整合測試每個場景開頭的 `DbSupport.cleanBusinessTables` 只刪 `tasks`，工務單由建單一起開之後撞外鍵；補上先刪 `repair_tickets`、`repair_ticket_counters`。`TaskEngineRowWriteTest` 改用 502 房（seed 裡 301 今天有入住，開單時會被判成急件，讓「急件旗標由建單指令寫入」那支測試失準）。追蹤單（草稿帶原單）不做急件判定，與搬動前一樣。
- T035 後端全套（611 支，6 failed＋27 error 之中舊整合的 31 筆是上面的外鍵，修正後重跑 `RunCucumberTest`＋`TaskEngineRowWriteTest`）：行為規格 397 場景 6 筆紅，都在 T002 基準內（`派出後復原` 4 筆留給 4B、`櫃檯看板讀取.feature:219`、`:226` 留給 4C）；基準的 `櫃檯轉來的工務單` 5 筆與 `櫃檯派單.feature:104`、`:113`、`:121` 轉綠。舊整合 172 支剩 2 筆紅且不在基準：`01-guest-requests.feature` 建單路徑「冷氣不冷」那列與「規則引擎命中留痕可解釋」，住客打冷氣仍以工務建單、現在沒帶草稿被 `TaskEngine.create` 擋下（500）；這是 T035 要求的程式錯誤，由 4C 的 T038／T039 改成進待派單後轉綠。
- T036：`undoTriage` 的守衛 UPDATE 以 `FROM task_events e`（`e.seq = t.event_seq AND e.type = 'triaged'`，與原本「最新事件就是最近一筆派單」等價）同一句讀出那筆派單的 `dept`、`former_type`、`former_urgent`，在同句還原類型與急件；`dept` 是工務時呼叫 `RepairTicketOpener.discard`（刪掉這張任務的工務單）。防重複鍵在 T033 已改成工務單號，T036 只驗證。Test Scope 全綠（`派出後復原.feature`、`開立工務單與急件.feature`）。
- T037：復原只有一次往返（守衛 UPDATE 同時回傳派單的 `dept`），不需再整理；Test Scope 維持全綠，沒有改碼。
- T038：`01-guest-requests.feature` 建單路徑「冷氣不冷」那列改成 `other`／`frontdesk`／`triage`，註解改成命中工務的規則仍停在待派單；「規則引擎命中留痕可解釋」不改。
- T039：`GuestRequestService` 兩條住客路徑在部門是工務或類型是修繕時改成 `other`／`frontdesk`／`triage`；補 `tms/src/test/java/com/athena/tms/service/GuestRequestRoutingTest.java`（規則設成房務＋修繕，單進待派單、有 T- 號；Spring Boot＋Testcontainers，照 `TaskEngineRowWriteTest` 的寫法，因為 T- 號是建單時取的）。Test Scope：`櫃檯看板讀取.feature` 21 場景全綠（基準的 `:219`、`:226` 轉綠）；舊整合 172 支全綠（T035 那 2 筆轉綠）；`guest-web/設備報修/組成文字送出.feature` phone 9 場景全綠（基準的 `:72`、`:82` 轉綠）；`待派單清單.feature` desktop 16 場景只剩基準的 `:53` 頁尾說明（T048 才改）。
- T040：兩條路徑收進同一個 `guestCommand`，「工務或修繕改進待派單」只判斷一次；重跑上面的後端與住客頁範圍仍全綠。
- T041：Test Scope（`櫃檯轉來的工務單.feature`＋`待認領` 模組，26 場景）一跑就全綠，讀端的 `repairCategory` 與來源換算在 T033 已為 `櫃檯派單.feature` 做了，本題沒有改碼。
- T042：來源換算收成 `TaskExecutionService.repairTicketSource`，`deptPool` 與工務首頁 `engineeringTickets` 共用；工務首頁的住客來源工務單因此也標「櫃檯」（與 `EngineeringTicket.source` 契約一致）。`工務單`＋`待認領` 兩模組 99 場景全綠。
- T043 之前補了 `tms/src/test/java/com/athena/tms/repair/RepairTicketOpenerTest.java`（同一張任務 `open` 兩次只有一張工務單、計數不動），這是 T043 的通過條件之一，原本沒有任何一題負責寫。
- T043 審查（另一個 agent，新開的上下文）：PASS，七項都過。採納兩個 Low：`assignFromTriage` 非工務時的類型改寫 `ELSE t.type`（原本取自 JOIN 的舊列）並註明自我 JOIN 的用意；`DeskBoardFixtures.settleRepairTicket` 同時把 `tasks.urgent` 歸零。不採納兩個 Low：`RepairService.create` 回應的 `createdAt` 沿用改動前的 `now(clock)` 寫法（改讀資料庫會讓時區格式變成 UTC），`open` 只在判定急件時把 `tasks.urgent` 設成 true（照 plan，不把非急件的判定反寫成 false）。改完重跑 `櫃檯看板與派單`＋`工務單` 175 場景全綠。
- T044：工務待認領單卡房號方塊改讀 `repairCategoryShortText(item.repairCategory ?? 'other')`（`ticketNo == null` 不畫的那行保留；有工務單號就一定有類別，`?? 'other'` 只為型別）；`TmsRoomSquare._labelWithId` 拿掉重複的 `label:`，讀屏只念一次（T005 那條自主決策）。補 `app/mobile/test/eng_pool_category_label_test.dart`（冷氣／其他、沒有 `repair`）。模擬器跑 `工務單` 模組第一輪 73 場景 2 紅：`工務視角.feature:50` 是 `app/bdd-support` 的 `BackendChannel.createRepairTicket` 把任務標題寫成「類別代碼＋工務單」（`air_conditioning 工務單`），T027 的「單卡不該有英文代碼」抓到的是測試資料；改成跟後端開單同一套中文標題（`RepairService.CATEGORY_LABELS`）。`:41` 是等不到 `engHome.screen` 的逾時（基準裡也是環境類逾時）。改完單跑 `工務視角.feature` 28 場景全綠；`flutter test` 518 全綠。
- T045：工務首頁不顯示類別；工務單詳情、房務詳情、報修單等讀的都是工務單 API 的 `category`，沒有把任務類型當類別的地方，沒有改碼。
- 4E 之後的模組結果：`工務單` 73 場景 71 綠（剩的 2 筆如上，`:50` 已修、`:41` 單跑轉綠），基準 11 筆紅全部轉綠（含 6 筆後門逾時）；`待認領` 21 場景全綠（基準 0 紅）。
- T046：`V29__engineering_tasks_get_repair_tickets.sql` 先把「部門工務、沒結束、沒有工務單」的單分成櫃檯派來的（最新派單是工務且有操作者、狀態 pool／accepted／doing／deferred／interrupted）與住客直接進來的（住客來源、沒有派單紀錄、狀態 pool），其他一律 `RAISE EXCEPTION` 帶張數與前 10 張的 id、來源、狀態；Flyway 整支在一個交易，停下時什麼都沒改。住客那種退回待派單、補 T- 號、寫 `triage_undone`（system）與對應 outbox；櫃檯那種補工務單、類型改 repair、不寫事件；最後自我檢查 0 筆。自主決策：補號的起點取「計數列」與「已用掉的最大號」較大者，計數列不在或落後時不撞號（實測：刪掉計數列的飯店原本會撞 `uq_tasks_tenant_task_no`）。`V29EngineeringTaskBackfillTest` 9 支全綠：櫃檯派來（待認領＋已認領，單號接續、欄位、不寫事件、重放一致）、住客直接進來（退回、補號、事件、outbox 兩把鍵與信封、重放一致）、沒有計數列、兩間飯店各自接續、已結束不動、三種讓遷移停下且一張都沒改。重放 helper 多認 `claimed`（狀態 accepted），讓已認領的單也能比對。
- T047：每種單由 `v29_missing.kind` 的 `CASE` 判定、各步以 `WHERE kind = …` 取用；註解只留為什麼（住客單為什麼退回而不補工務單、櫃檯單為什麼不寫事件、為什麼要有派單的人、補號為什麼取較大者）。測試仍 9 支全綠。
- T048：`zh-TW.ts` 頁尾照 feature 原文；`en.ts`、`ja.ts`、`vi.ts` 同義翻譯（給工務的住客需求也會來這裡、只有給房務的才直接進房務待認領）。`locales.test.ts` 12 支全綠。
- T048 實跑：`待派單清單.feature:31`（版面由上到下）靠頁尾文字開頭找頁尾，page object `TriagePage.FOOTER_LEAD` 還是舊文案「只有住客打字」；改成新文案的開頭。改完 desktop 16 場景全綠（基準的 `:53` 轉綠）。
- 4F 之後後端全套：667 支，0 失敗（基準 656 支、14 紅全部轉綠；多出的 11 支是 V29 9 支、`RepairTicketOpenerTest`、`GuestRequestRoutingTest`）。8080 的開發堆疊重啟時 Flyway 已在 5433 的 `tms` 套上 V29（沒有停下）。
- 4G 之後：櫃檯 Web desktop 的 `待派單` 47、`總覽看板` 85、`單筆詳情` 117 場景全綠（基準在這三個模組的 `待派單清單:53`、`:150` 轉綠；`即時更新` 的三筆時序案例不在這三個模組，本輪沒跑）；住客頁 phone 全部 236 場景全綠（基準 3 筆紅全部轉綠：`組成文字送出:72`、`:82` 是本輪要修的，`連線異常時定期重讀:17` 是時序案例，這次沒逾時）。

## Phase 5 回歸

獨立驗收者量的（沒有寫本輪程式）。工作目錄是 `b1a406c` 加上 Phase 1–4 未提交的改動；後端 jar 是 `./tms/mvnw -DskipTests package` 的產物（含 V29 與 `RepairTicketOpener`），複本 `/tmp/006-p5/tms-p5.jar` 給 8081 用。員工 App 用 `iPhone 18 Pro`（`B07951C2-…`）、8081（5434／9094，`TMS_TEST_CLOCK_ENABLED=true`）。

| 組 | 基準（T002） | 這次第一輪 | 重跑 |
| --- | --- | --- | --- |
| 1 後端全套 | 656 支、14 紅 | 667 支、0 紅（行為規格 397、舊整合 172、relay-wake 3 全綠） | 不需要 |
| 2 櫃檯 Web desktop | 432 場景、5 紅 | 432 場景、0 紅 | 不需要 |
| 3 住客頁 phone | 236 場景、3 紅 | 236 場景、0 紅 | 不需要 |
| 4a 員工 App 工務單 | 73、11 紅 | 73、1 紅：`工務單/工務單房務端生命週期.feature:29`（例子第 7 列「已結案 · 不需處理」） | 整份 13 場景全綠 |
| 4b 員工 App 待認領 | 21、0 紅 | 21、0 紅 | 不需要 |
| 5 App 單元 | 517、0 紅 | 518、0 紅 | 不需要 |
| 網頁 typecheck＋vitest | 未量 | tsc 0 錯；59 檔 729 支全綠 | 不需要 |

**4a 那一筆不在基準內，所以 T051 不打勾。** 錯誤是「等不到畫面元素 home.task.305.card（逾時 8s）」，停在「"阿美" 在 App 打開 "W-0412" 的工務單詳情」；失敗當下的截圖是 Flutter 的紅底錯誤畫面：`package:flutter/src/widgets/framework.dart: Failed assertion: line 6281 pos 12: '_dependents.isEmpty': is not true.`。同一份 feature 重跑一次 13 場景全綠，所以是會時好時壞的失敗，不是穩定重現。本輪 App 改動只有 `eng_pool_screen.dart`、`TmsRoomSquare.labelId`（沒帶 id 時畫面不變）與 `PoolItem.repairCategory`，房務首頁到工務單詳情這條路沒有用到（推論，未證明）。證據在 `app/bdd/target/verify-evidence/006-p5/` 與 `006-p5-repair-run1/`（含 appium.log、失敗截圖與元素樹）。要不要把它算成既有毛病，由人決定。

**T052 手動測試環境上線 V29**：

- 備份：`~/.tms-manual-env/backup-20261124-024647-tms_manual.sql`、`…-tms.sql`。本機 `pg_dump` 是 15、伺服器是 16，版本不合拒絕執行，改用容器內的 `pg_dump`（`docker --context orbstack exec tms-postgres pg_dump`）。
- 唯讀預檢（`BEGIN READ ONLY`，照抄 V29 第 1 步與第 5 步的分類；`psql -f` 被專案的寫入防護擋下，改成 `-c`，腳本 `/tmp/006-p5/precheck.sh`）：無法歸類 0 筆；總數 2，櫃檯派來的 2（`T-0020` 306 房、`T-0024` 407 房，派單人都是 `fd1`），住客直接進來的 0。輸出在 `evidence/precheck-before.out`。
- 舊後端在預檢時本來就沒在跑（11-20 已停），`stop.sh` 兩個都回「沒有在跑」。
- `start.sh` 後 Flyway 套上 V29（77ms）。之後：`T-0020` → `W-0111`、`T-0024` → `W-0112`，兩張都是 `repair`／`pool`、類別 `other`、報修人 `fd1`；`repair_ticket_counters` 112；沒有 system 的 `triage_undone`（沒有住客直接進來的單）；`部門工務、沒結束、沒有工務單` 0 筆（`evidence/precheck-after.out`）。
- `mt1` 工務待認領（`evidence/e2e-08`、`e2e-09`）：`W-0112` 在列表上，類別「其他」、來源「櫃檯」；分流磚「櫃檯 4」＋「房務報修 4」＝ 8，列表 8 張（W-0101..0105、W-0111、W-0112、W-0113，這時已含 T053 剛派出的 W-0113）。

**T053 實機走一次**（截圖 `evidence/e2e-01`…`e2e-26`）：

- 前置：手動環境沒有任何一間房今天有住客，新增一筆訂房 `reservations_cache` `E2E-006-407`（407 房，11/23 15:00–11/25 11:00，id `7f2d36bb-46ac-4cfd-a372-ee4bfd581bad`）；`tenant_config` 沒有 `classification_rules`，照 `V2__seed_dev.sql` 補上同一組規則（冷氣／毛巾／吵），否則「冷氣不冷」根本不會命中工務規則。兩筆都是直接 SQL 寫的設定資料，不是流程中的補資料。
- 住客（393×852）送出「其他：窗簾掛勾鬆脫，拉不動」→ `T-0027` `frontdesk`／`other`／`triage`；送出「冷氣不冷」→ `T-0028` `frontdesk`／`other`／`triage`，`created` 事件帶 `rule_id: r-aircon`；住客頁顯示「已送出」「02:49 送出 · 櫃檯」（e2e-03、e2e-04）。
- 櫃檯 `fd1`：兩張都在待派單（e2e-05）；`T-0027` 按「派給工務」→ 詳情改成 `W-0113`、「工務 · 修繕」、工務待認領（e2e-06）；資料庫 `engineering`／`repair`／`pool`，`repair_tickets` `W-0113`／`other`，`triaged` 帶 `former_type: other`。`T-0028` 一直留在待派單，`mt1` 的工務待認領沒有它。
- `mt1`：`W-0113` 在工務待認領，房號方塊「其他」、來源「櫃檯」（e2e-09）；認領 → `accepted`（e2e-10）。按開始被擋：「305 還在進行中」（種子資料的 `W-0106` 是 `mt1` 名下維修中，e2e-12-…-blocked）；在 App 把 `W-0106` 以「不需處理」完修（結案、不需驗收）後再開始 → `doing`；住客頁即時變「處理中 · 工務收到」（e2e-13）；附 1 張照片、「已修好」完修 → `done`、「已完修 · 等主管驗收」（e2e-14、e2e-15）。
- `mt1` 登出沒有被擋；`mg1` 王主管登入，待驗收 3 張含 `W-0113` → 驗收通過 → `closed`、`verified_by` = 王主管（e2e-17…e2e-19）。住客頁沒有重新整理就變「已完成 · 已處理完成。」（e2e-20）；照 `guest-web/取消與評分/評分.feature:91`，驗收過的工務單不出評分卡，畫面也沒有。櫃檯看板即時移到「已結案」，歷程有派單、認領、開始、完成、王主管驗收（e2e-21）。
- 房務那條也走了一次：住客「其他：床單有污漬，麻煩幫忙更換」→ `T-0029` 待派單 → 櫃檯「派給房務」→ `housekeeping`／`other`／`pool`、沒有工務單（e2e-22）→ `hk1` 小美在房務待認領看到「407 其他 · 櫃檯轉單」、認領、開始、完成 → `done`（e2e-23…e2e-25）→ 住客頁出現評分卡，給 5 星送出，`rated` 事件 `{"rating": 5}`（e2e-26）。
- 順帶看到（不在本輪範圍）：小美的任務頁「這間房的資訊」寫 407「工務單 3 張・3 張未結案」，但 `W-0113` 已 `closed`。`app/mobile/lib/features/task/task_screen.dart:950-951` 的 `_isOpenTicket` 只把 `done`、`cancelled` 當結案，`closed` 被算成未結案；這行來自 `76707a7`，不是本輪改的。

手動環境裡本次新增或改動的資料列：上面的訂房與 `classification_rules`；任務 `T-0027`（`W-0113`，含 1 張完修照片）、`T-0028`、`T-0029` 與它們的事件、1 筆 `guest_sessions`；`W-0106` 被以「不需處理」結案；V29 補的 `W-0111`、`W-0112`。
