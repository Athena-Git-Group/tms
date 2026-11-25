# 007 實作筆記

## T005 改動前的基準（2026-11-24，HEAD ec9a1f8，工作樹乾淨）

量測範圍：使用者把基準縮到本包會動到的模組，全套回歸留到 Phase 5。後端與櫃檯 Web 在範圍調整前已跑完全套，結果照記（是縮小範圍的超集合）；住客頁只量 `設備報修/`、`備品補充/`。下列失敗全部落在本輪新增或修改、尚無 stepdef 的 feature（undefined step），不是既有程式壞掉。

### 1. 後端全套（`./tms/mvnw -f tms/pom.xml test`）

- `RunTruthFeaturesTest` 461 個場景，64 個失敗；`integration.RunCucumberTest` 172 全過；`relaywake` 3 全過；其餘 JUnit 測試全過。
- 失敗清單（feature 與場景行號）：
  - `backend/員工事件串流/收得到哪些單的訊號.feature`（2）：64, 65
  - `backend/工務單/櫃檯代開的工務單.feature`（10）：26, 27, 28, 29, 33, 43, 51, 74, 75, 79
  - `backend/櫃檯看板與派單/代開單全有全無與重送.feature`（4）：14, 24, 35, 44
  - `backend/櫃檯看板與派單/代開單的房間清單.feature`（8）：17, 31, 42, 67, 68, 78, 79, 80
  - `backend/櫃檯看板與派單/代開單的檢查與拒絕.feature`（15）：22, 23, 36, 37, 38, 39, 40, 41, 45, 54, 62, 66, 70, 76, 82
  - `backend/櫃檯看板與派單/代開的單不算住客額度.feature`（3）：13, 24, 36
  - `backend/櫃檯看板與派單/櫃檯代開單.feature`（17）：17, 29, 39, 51, 65, 75, 85, 99, 107, 116, 136, 137, 138, 150, 151, 152, 156
  - `backend/櫃檯看板與派單/看板卡片的對應品項.feature`（5）：14, 29, 38, 46, 57

### 2. 櫃檯 Web 全套 desktop（`run-feature.sh --keep frontdesk-web/ desktop`）

534 個場景，103 個失敗：
- `frontdesk-web/代開單/入口與空白面板.feature`（10）：6, 14, 21, 45, 49, 58, 66, 76, 113, 114
- `frontdesk-web/代開單/兩種尺寸與四種語言.feature`（9）：37, 38, 53, 54, 58, 82, 83, 87, 97
- `frontdesk-web/代開單/其他需求.feature`（11）：12, 21, 31, 44, 52, 62, 71, 86, 95, 103, 111
- `frontdesk-web/代開單/常用品項與送出鈕.feature`（14）：12, 21, 34, 45, 60, 75, 86, 95, 106, 119, 130, 142, 153, 161
- `frontdesk-web/代開單/房號欄與住客卡.feature`（11）：21, 28, 35, 42, 55, 64, 71, 87, 93, 100, 111
- `frontdesk-web/代開單/送出失敗與重送.feature`（10）：16, 24, 32, 40, 52, 59, 68, 77, 86, 94
- `frontdesk-web/代開單/送出成功回到看板.feature`（17）：13, 23, 37, 50, 59, 74, 87, 98, 120, 121, 139, 140, 144, 153, 171, 172, 174
- `frontdesk-web/代開單/關閉不確認也不保留.feature`（6）：11, 22, 33, 44, 55, 67
- `frontdesk-web/代開單/類似單提醒.feature`（14）：9, 25, 36, 46, 59, 76, 90, 98, 108, 119, 136, 146, 156, 166
- `frontdesk-web/即時更新/現場變化即時跟上.feature`（1）：27

### 3. 櫃檯 Web 全套 ipad（`run-feature.sh --keep frontdesk-web/ ipad`）

534 個場景，103 個失敗，清單與 desktop 完全相同。

### 4. 住客頁 phone（只量 `guest-web/設備報修`、`guest-web/備品補充`）

- `設備報修/`：22 個場景，1 個失敗：`guest-web/設備報修/報修的限流.feature`：21
- `備品補充/`：27 個場景，3 個失敗：`guest-web/備品補充/後端在送到一半拒絕.feature`：6, 26, 44

### 5. 櫃檯 Web 單元測試與建置

`npx vitest run`：59 個檔、729 個測試全過；`npm run build`：成功（只有 chunk 大於 500 kB 的既有警告）。

## T001 spike：交易內冪等鎖（結論：做得到）

測試：`tms/src/test/java/com/athena/tms/spike/IdempotencyLockInTransactionSpikeTest.java`，4 個測試全綠（Testcontainers PostgreSQL，資料來源用執行期帳號 `tms_app`）。

- 鍵的換算：`pg_advisory_xact_lock(hashtextextended(tenant_id::text || ':' || key, 0))`。`hashtextextended` 回 bigint，正好是 `pg_advisory_xact_lock(bigint)` 的參數；飯店編號放進雜湊，不同飯店同一把鍵拿到不同的鎖（測試驗過值不同）。`tms_app` 不需要額外權限就能取 advisory lock。
- 業務交易用 `TenantScopedExecutor.callAsTenant`（會加入外層交易）包住：取鎖 → `IdempotencyService.findResult` → 沒有就做事並在同一交易呼叫 `IdempotencyService.store`。`findResult`／`store` 內部的 `callAsTenant` 加入同一個交易，不另開。
- 同一把鍵兩個交易同時進來：第二個在取鎖處等了約 800 ms（第一個持鎖時間），第一個提交後才繼續；READ COMMITTED 下鎖後的下一個查詢看得到第一個寫的 `processed_ops`，回放第一個的內容；業務資料只有一列。
- 第一個交易在做完之後丟例外而回滾：`processed_ops` 與業務資料一起退掉，第二個拿到鎖後查不到紀錄，自己做一次；最後業務資料一列、紀錄是第二個的結果。
- 提交之後 `IdempotencyFilter` 再呼叫 `store`（狀態 201、不同內容）因 `ON CONFLICT (tenant_id, op_id) DO NOTHING` 被略過，交易內寫的那份不被覆寫。
- 實作提醒：`IdempotencyFilter` 在進入控制器前已先 `findResult` 一次，鎖內那次是第二道；控制器回 5xx 時過濾器不存，交易回滾也不會留下紀錄，所以 5xx 後同一把鍵重送會重做。

## T002 spike：資料庫暫時觸發器讓交易中途失敗（結論：做得到，兩條線都有權限）

測試：後端 `tms/src/test/java/com/athena/tms/spike/TemporaryTriggerRollbackSpikeTest.java`（1 個測試綠）；櫃檯 Web 測試線 `app/web-bdd/src/test/java/com/athena/web/bdd/spike/TemporaryTriggerSpikeTest.java`（3 個測試綠，對 8080 後端與 5433 `tms` 實跑；`app/web-bdd/pom.xml` 加了 `**/spike/**` 排除，平常的 `mvn test` 不跑）。

- 受試路徑：`deskAssignTask` 派給工務，同一交易裡 `RepairTicketOpener.open` 開 `W-`。在 `repair_tickets` 裝 `BEFORE INSERT ... FOR EACH ROW` 觸發器，函式裡 `IF NEW.tenant_id = <本案例飯店> THEN RAISE EXCEPTION`：後端回 500（Spring 預設錯誤內容，不是契約 `Error` 形狀），任務仍在 `triage`、`task_events` 張數不變、沒有工務單列、`repair_ticket_counters` 沒前進、outbox 沒有新列；拆掉後同一張派給工務回 200、開出 1 張工務單、流水號 +1。
- 只對一個飯店生效：函式裡比 `NEW.tenant_id`。只對一個標題生效：裝在 `tasks`，比 `NEW.title = '<標題>'`；其他標題照常寫入、那個標題整筆被擋（`ERROR: <訊息>`）。代開單 DSL 兩句的對應：「剛才代開…後端出錯，一張都沒開出」用 `tasks` 上比飯店的版本；「開 "{標題}" 這張單時後端會出錯」用 `tasks` 上比標題的版本（工務單的任務也寫 `tasks`，同樣擋得到）。
- 權限：後端 Testcontainers 的執行期帳號 `tms_app` 不是表的擁有者，裝不了觸發器；改用容器的管理帳號（`POSTGRES.getUsername()`，BDD 的 `@ServiceConnection` 資料來源與 `DbProbe` 用的就是它）。櫃檯 Web 測試線的 `BackendChannel` 連 5433 的 `tms` 帳號是 superuser，`has_table_privilege(current_user, 'tasks', 'TRIGGER')` 為 true。
- 命名與清理：觸發器與函式一律用 `bdd_fault_` 開頭。`resetBusinessData()` 的 `TRUNCATE` 不會拆掉觸發器（測試驗過），所以清理要自己做：①掛在 Cucumber `@After`（場景失敗也會跑）以 `DROP TRIGGER IF EXISTS` ＋ `DROP FUNCTION IF EXISTS` 拆掉本場景裝的；②保險起見在場景開頭的重設（櫃檯 Web 是 `WebWorld` 的 `@Before(order = 0)`，後端是 `steps/common/ResetHooks`）先拆掉任何殘留的 `bdd_fault_%`，避免上一次被強制中斷時留下的觸發器讓後面的場景全部失敗。

## T003 spike：`route.fetch()` 之後中斷（結論：做得到，兩種瀏覽器都是網路錯誤）

測試：`app/web-bdd/src/test/java/com/athena/web/bdd/spike/RouteFetchAbortSpikeTest.java`，`desktop`（Chromium）與 `ipad`（WebKit）各一個測試，兩個都綠（對 8080 後端、5199 開發伺服器、5433 `tms` 實跑）。

- 做法：分頁以 `page.route("**" + Network.DESK_ASSIGN.path(), ...)` 攔派單請求，handler 裡先 `route.fetch()` 讓請求真的到後端並拿到回應，再 `route.abort(...)`；頁面上用 `fetch` 打同一支端點。
- 收到的錯誤：Chromium 是 `TypeError: Failed to fetch`，WebKit 是 `TypeError: Load failed`；`route.abort()` 不帶錯誤碼與 `route.abort("failed")` 結果相同。兩者都是 `fetch` 被拒（不是拿到 2xx），櫃檯 Web 的 `fetchJson` 會把它包成 `NetworkError`（`app/web/src/api/errors.ts:70-72`）。建議沿用 `route.abort("failed")`。
- 後端：`route.fetch()` 拿到 200，那張單在資料庫已是 `pool`（已提交）。之後以同一把 `Idempotency-Key`、同樣內容重送，回 200 且帶 `Idempotency-Replayed: true`。
- 注意：`route.fetch()` 送出的請求帶著頁面原本的標頭（含 `Idempotency-Key`），T010 的攔截方法要從 `route.request().headers()` 記下這把鍵。

## T004 smoke-test

- ①後端 `test-compile` 通過；T001（4 個）、T002（1 個）spike 測試綠。
- ②`待派單/待派單清單.feature`：desktop 16 個場景全過、ipad 16 個場景全過；T005 基準裡這份沒有失敗，結果相同。
- ③櫃檯 Web dry-run 對 `frontdesk-web/代開單`：編譯通過，102 個場景都只因 undefined step 失敗（undefined 24 句、其餘 skipped），沒有編譯錯誤。

## T006 後端端點落點

- 新增 `dto/desk/DeskMenuKey`（英文常數＋中文鍵，`@JsonValue` 輸出中文鍵；帶部門、類型、上限、標題組法、修繕分類）與 `DeskCreateRequestsRequest`、`DeskCreateRequestItem`、`DeskCreateRequestOther`、`DeskCreateRequestsResult`、`DeskRoom`、`DeskRoomGuest` 六個 record。請求裡的 `menuKey`、`quantity` 用 `String`／`Integer` 收，讓不在清單裡的鍵與缺數量能在服務裡回 422，而不是在反序列化就變成 400。
- `DeskTask` 在 `guestText` 之後多 `menuKey`，`DeskTaskMapper` 先給 null；`DeskBoardController` 掛 `GET /rooms` 與 `POST /requests`（201），接到 `DeskCreateRequestService`（兩個方法先丟 `UnsupportedOperationException`）。
- 驗證：後端全套 664 個測試（多了 5 個 spike），`RunTruthFeaturesTest` 失敗清單與 T005 基準逐行相同（64 個，全在本輪新 feature）。

## T007、T008 後端測試共用 helper 與 stepdef 落點

- `DeskApi.createRequests(staff, roomNo, items, other, idempotencyKey)`（`items` 是 `{menuKey, quantity}` 的 Map 清單，原樣送出；鍵為 null 不帶標頭）、`DeskApi.listRooms(staff)`。
- `DeskContext` 記這一次代開的人、鍵、請求內容，回應依收到順序全記（同時送兩次也都在）。
- `LastOperationGuard.captureCreateRequest(tenantId)`／`createRequestCounts`／`currentCreateRequest`：飯店的任務、事件、工務單張數，`task_counters`、`repair_ticket_counters` 的 last_no，outbox 張數與最大 id。
- `support/DeskCreateFaults`：`failTaskTitled(title)`、`failEveryTaskOf(tenantId)` 在 `tasks` 裝 `bdd_fault_*` 觸發器；在 `BddSpringConfiguration` 註冊成 `@ScenarioScope`、`destroyMethod = "removeAll"`，場景結束（含失敗）一定拆掉（與既有 `StreamFaults` 同一做法）。後端 BDD 的 `JdbcClient` 是容器管理帳號，有權限裝拆。
- `steps/desk/DeskCreateRequest{Common,Given,When,Then}Steps` 四個空類別，注入上述 helper。
- 驗證：`test-compile` 通過；`櫃檯看板與派單/` 模組實跑 149 個場景，失敗清單與 T005 基準相同（都是新 feature 的 undefined step）。

## T009 `BackendChannel` 的代開單資料準備

- 新增 `deskCreateRequests(actor, roomNo, items, other, idempotencyKey)`（要求 2xx）、`listDeskRooms(actor)`、`removeRoom(roomNo)`、`changeRoleToHousekeeping(staff)`、`invalidateLogin(staff)`、`failTaskTitled(title)`、`removeFaultTriggers()`；每個方法的 Javadoc 第一句寫它對到哪一句的 Arrange 來源。
- `removeRoom`：房間還掛著單、訂房或住客工作階段時外鍵會擋；在同一交易 `SET LOCAL session_replication_role = replica` 暫時略過外鍵檢查只刪房間列（`tms` 是 superuser），殘留資料由下一個場景開頭的清空一起清掉。
- `invalidateLogin`：用同一組密碼重算 bcrypt 雜湊（新鹽），登入憑證裡的密碼雜湊指紋對不上，後端回 401；並清掉 `BackendChannel` 快取的該員工憑證。
- 暫時觸發器照 T002：`tasks` 上 `bdd_fault_task_title_*`，比 `NEW.title`。清理掛在 `WebWorld`：`@Before(order = 0)` 清空業務資料前、`@After(order = 0)` 的 finally 裡各呼叫一次 `removeFaultTriggers()`（查 `pg_trigger` 拆掉所有 `bdd_fault_%`，含上次中斷留下的）。
- 驗證：`app/bdd-support` install、`app/web-bdd` test-compile 通過。這段期間 8080／5433 被另一個 session 的 web-bdd 實跑占用，沒有對真後端實跑這幾個方法；`deskCreateRequests`／`listDeskRooms` 要等 Phase 4 端點完成才會成功，第一次實跑在 Phase 3 的 dry-run／Phase 4。

## T010 櫃檯 Web 測試線的落點

- `Network`：`DESK_CREATE_REQUESTS`、`DESK_ROOMS` 兩個 `Api`；`deliverThenLoseResponse(page, api)` 照 T003 結論對下一個請求先 `route.fetch()` 再 `route.abort("failed")`，之後的請求照常放行；`lostResponseKey(page, api)` 取那個請求的 `Idempotency-Key`（Playwright 的標頭名是小寫）。`DESK_WRITES` 沒動。
- `pages/CreateRequestPanel`：面板（名稱「代開單」的 dialog）、遮罩（「關閉」）、✕（「取消」）、房號欄、建議房號、住客卡、空房提示、品項格子與「−」「+」「移除」、其他需求框與部門分段、類似單提醒（status）、摘要、送出鈕，全部用角色與看得到的文字定位，名稱照 `ui/ui-plan.md`。
- `steps/createrequest/`：`CreateRequest{Given,When,Then}Steps` 空類別、`CreateRequestScenario`（送出前面板快照、送出用的鍵、後端開出的單 id）、`package-info`。
- 驗證：`test-compile` 通過；`frontdesk-web/代開單` dry-run 102 個場景只報 undefined（24 句），與 T004 相同。

## T011 櫃檯 Web 代開單面板的落點

- 型別照既有慣例放在 `api/types.ts`（`DeskTask.menuKey`、`DeskMenuKey`、代開單請求與回應、`DeskRoom`／`DeskRoomGuest`）；`api/desk.ts` 加 `listDeskRooms`、`createDeskRequests(request, idempotencyKey)`（鍵由呼叫端傳入；回放時 `serverTime` 是第一次的時間，所以不拿它校正時鐘）。
- `features/create-request/`：五個元件空殼（回 null）、`use-create-request.ts` 空殼、`create-request-logic.ts`（九個純函式簽名，先丟錯）、`create-request-logic.test.ts`（一筆 `it.todo`，空檔會讓 Vitest 報「沒有測試」而失敗）、`index.ts`。
- `stores/desk-panels.ts`：`createRequestOpen`、`createRequestRoomNo`、`openCreateRequest(roomNo?)`、`closeCreateRequest()`，`reset` 一併清掉；沒有接任何按鈕。
- 驗證：`npx vitest run` 729 通過、1 todo（基準 729 通過）；`npm run build` 成功。

## Phase 4-B01–B08 後端實作紀錄

- 4-B01／B02：`service/DeskCreateRequestService` 先檢查完整個請求（`validate` 回全部 `details`，`ApiException` 新增選填的 `details`，`GlobalExceptionHandler` 原樣輸出；`NotFoundException` 可帶 `context`，房號 404 帶 `roomNo`），再依 `DeskMenuKey` 順序逐張走 `TaskEngine.create`，其他需求排最後；建單後 `UPDATE tasks SET pool_source = 'desk_forward'`。回應卡片用 `DeskBoardService.card`（與看板同一句查詢）。
- 4-B03：`service/DeskCreateRequestIdempotency.runOnce` 在業務交易裡取 `pg_advisory_xact_lock(hashtextextended(tenant_id||':'||key,0))`、鎖內查 `processed_ops`、沒有就開單並在同一交易寫入 201 結果；回放由控制器帶 `Idempotency-Replayed: true` 原樣送回。4xx 在交易內是例外、會回滾，所以 4xx 仍由 `IdempotencyFilter` 在回應後寫入（同一把鍵同時送兩個會出 4xx 的請求，第二個重新檢查得到相同錯誤，結果一致）。T001、T002 的後端 spike 測試已刪除（`tms/src/test/java/com/athena/tms/spike/`）；櫃檯 Web 的 spike 留給 4-W09。
- 4-B04：照預期沒有改產品碼就綠；代開流程沒有任何地方呼叫 `RoomRequestRateLimiter`。
- 4-B05：`listRooms` 與看板卡片的在住認定共用 `service/InHouseGuest`（欄位與條件兩段 SQL）。測試層修正：`StaffFixtures` 為了讓員工在班表上會建 `PLANROOM-F{樓層}` 佔位房，`代開單的房間清單剛好如下：` 比對時排除這個前綴（`StaffFixtures.PLAN_ROOM_PREFIX`）。
- 4-B06：`DeskTask.menuKey` 讀 `tasks.note.menu_key`；分類規則選填 `menu_key`（`ClassificationRule.menuKey`、`Routing.menuKey`），命中時住客打字的 note 另記 `menu_key`。**T147 停住**：`看板卡片的對應品項.feature:29`（305 的住客勾選毛巾）預期看板標題 `毛巾 ×1`，產品對住客勾選數量 1 的標題不帶「×1」，而 005 的 `guest-web/備品補充/送出鈕與成功頁.feature`「只送一項時…需求的標題不帶「×1」」明確驗收後端標題是「吹風機」。兩份 truth 互相矛盾，未改產品也未改 truth，待上游裁決；T148 依賴 T147 一併未做。上游裁決以 005 為準：該例子的標題改為「毛巾」（truth-delta `/dsl-refine` 已記），產品不動即轉綠。T148 把住客勾選、住客打字、櫃檯代開三處 note 組法收成 `service/TaskNote`（`menuItem`、`guestText`、`menuKey`），`menu_key` 只在這裡寫入。
- 4-B07：新增 `V31__repair_ticket_origin.sql`，補 `repair_tickets.origin`（NOT NULL DEFAULT 'reported'，CHECK reported|scheduled；既有列取預設值 reported，程式目前沒有排程保養單）。代開的工務單走 `TaskEngine.create` → `RepairTicketOpener.open`，不經 `RepairService.create`，照片、來源與角色規則因此不變；`INSERT INTO repair_tickets` 仍只有 `RepairTicketOpener` 一處。
- 4-B08：沒有改產品碼；每張代開的單由 `TaskEngine.create` 寫一筆 `created` 與一則 task outbox。

## Phase 4-W01–W11 櫃檯 Web 實作紀錄

- 4-W01：`shell/unopened-entries.ts` 只剩側欄三項；工具列的「代開單」改成 `DeskShell` 裡一顆真正的按鈕（W02 接上打開面板）。`unopened-entries.test.ts` 拿掉工具列那一筆的測試，清單改為三個入口。
- 4-W02／W03：面板 `features/create-request/CreateRequestPanel.tsx`（遮罩是面板外層裡的一顆「關閉」按鈕，面板 `role="dialog"`、`aria-modal`）；✕ 圓鈕與全圓角分段選擇放 `components/CircleCloseButton.tsx`、`components/SegmentedControl.tsx`。面板尺寸用 `index.css` 的 `@theme` token（`--width-create-request-panel`、`--height-create-request-panel`、`--radius-sheet`、`--min-height-item-tile`）。預填房號由 `stores/desk-panels.ts` 的 `openCreateRequest()` 從 `detailTaskId` 對到房號（先看看板快取，再看詳情快取）。
- 測試層修正（page object 與斷言的誤判，沒有改語意）：①`CreateRequestPanel.backdrop()` 原本找整頁名稱「關閉」的按鈕，會把 004 詳情面板的「關閉」一起算進去，改成只找面板外層裡的那一顆；②「其他需求沒有字數計數」的正規式 `\d+/\d+` 會把住客卡的「10/26」當成計數，改成只認「N / 60」；③「後端這次一張單都沒有開出」數看板卡片時用整頁範圍，會把三張數量卡（名稱「0 待派單」等）當成卡片，改成只數八個欄裡的卡片。
- 4-W05：送出摘要放在面板底部固定區、送出鈕正上方（不在可捲動的內容裡）。品項選很多時內容區要捲，摘要若放在內容最後會被捲到送出鈕下方，與「送出鈕上方的摘要」不符。
- 4-W06：其他需求已滿 60 字時，多打或貼上的整段都不收（`nextOtherText`）；只截前 60 字的做法在游標不在最後時會把貼上的字插進中間。
- 4-W08：送出成功的卡片以 `api/desk.ts` 的 `receiveCreatedCards` 放進看板快取，內部走即時訊號同一個 `receiveCardPush`；高亮存在 `stores/created-highlights.ts`（2.6 秒到期清掉，登出時重設），看板卡片帶 `data-highlight="created"`。004 的產品碼沒有卡片閃框，T168 沒有可合併的既有樣式。
- 4-W09：登入逾時（401）呼叫 `logOut`，路由守衛回登入頁；15 秒逾時用 `AbortController`，逾時、斷線、5xx 保留冪等鍵，4xx 丟掉；面板內容一改就丟掉冪等鍵。
