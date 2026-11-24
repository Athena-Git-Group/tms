# Truth Delta: 005-guest-web-main-flow

**Plan Package**: `specs/plans/005-guest-web-main-flow`

**Truth Root**: `specs/truth`

> 本檔由 truth owner skill（`/technical-research`、`/api-plan`、`/data-plan`、`/dsl-refine`）透過 `/truth-delta` 追加。
> `/specify` 只建立骨架，不得寫入任何 `specs/truth/**` 的實際改動。
> 本輪的預期影響見 `spec.md`「對既有行為的影響」：住客畫面行為為 ADD，契約、後端規則、櫃檯 Web、手機 App 預期不動（本版不改後端）。

## /technical-research

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| MODIFY | `specs/truth/techstack.md` -> `系統有哪些端` | 三個端改成四個：新增住客掃碼頁，與櫃檯 Web 同一個程式 `app/web/`、自己的路由 `/g/{代碼}` 與手機版面、程式集中在 `app/web/src/guest/`。既有三個端的敘述不變。 | 使用者原話「放在既有 app/web，新路由 /g/<token>」；`spec.md`「對既有行為的影響」ADD 列（research 決策 1）。 |
| ADD | `specs/truth/techstack.md` -> `前端` 住客掃碼頁框架與建置 | 新增：與櫃檯 Web 同一個 `React` ＋ `TypeScript` ＋ `Vite` 專案，直接呼叫 `tms` 的住客介面 `/guest/*`，不另起後端。 | research 決策 1、4。 |
| ADD | `specs/truth/techstack.md` -> `前端` 住客掃碼頁換頁與載入 | 新增：`React Router` 路由 `lazy` 延遲載入整棵 `/g/:qrToken/*`；每個子頁都帶代碼。 | FR-005（重新整理、從歷史記錄再開不用再掃）；櫃檯程式不進住客手機（research 決策 2）。 |
| ADD | `specs/truth/techstack.md` -> `前端` 住客掃碼頁外框與版面 | 新增：獨立外框、手機直向單欄、最寬 480 置中、44 點可點區、釘底送出鈕、`dvh` 與安全區域；`Tailwind CSS` 另立住客的設計變數命名空間。 | NFR-005；櫃檯與住客的版面前提相反（research 決策 2、7）。 |
| ADD | `specs/truth/techstack.md` -> `前端` 住客掃碼頁後端資料 | 新增：`TanStack Query`，與櫃檯共用 query client；收到訊號重讀、異常時每 4 秒重讀。 | FR-045、FR-046（research 決策 5）。 |
| ADD | `specs/truth/techstack.md` -> `前端` 住客 session | 新增：換來的 session 存 `sessionStorage`、鍵帶代碼；以 `X-Guest-Session` 標頭帶出；401 時用網址裡的代碼重換一次。與櫃檯登入狀態不同鍵、不同 store、不同標頭。 | FR-004、FR-005、FR-055；`spec.md`「假設」把存法交 `/technical-research`（research 決策 3）。 |
| ADD | `specs/truth/techstack.md` -> `前端` 住客掃碼頁連後端 | 新增：住客自己的薄薄一層 API（前綴 `/guest`、住客標頭、401 重換），共用 `ApiError`、`NetworkError`、`newIdempotencyKey`；Vite 代理加 `/guest`；每個寫入帶 `Idempotency-Key`；備品多項依序一張一張送。 | FR-015、FR-017、FR-054、NFR-002；後端只在帶了冪等鍵時才擋重複（research 決策 4）。 |
| ADD | `specs/truth/techstack.md` -> `前端` 住客掃碼頁即時更新 | 新增：以 `fetch` 讀 `GET /guest/stream`（帶住客標頭），解析器與連線狀態機與櫃檯共用；`task` 重讀清單、`resync` 另重讀首頁；斷線、35 秒沒資料、收到 `degraded` 時每 4 秒重讀，串流重新連上並收到 `init` 才停止；不顯示提示列。 | FR-045 至 FR-047、NFR-003；後端給住客的串流沒有任何恢復訊號（`recovered` 與心跳的 `degraded` 旗標都只給員工，`StreamConnection.java:122-124`），`degraded` 之後最長輪詢 30 分鐘到重連為止（research 決策 5）。 |
| ADD | `specs/truth/techstack.md` -> `前端` 住客掃碼頁多語系 | 新增：同一個 `i18next` 實例，語系檔各加 `guest` 子樹；第一次依手機瀏覽器語言（`zh*` 繁體中文、`ja`、`vi`、其餘英文）；選過的語言存 `localStorage` 一支手機一筆，鍵與櫃檯不同。不加韓文。 | NFR-004；住客與櫃檯對不認得語言的預設不同（research 決策 6）。 |
| ADD | `specs/truth/techstack.md` -> `前端` 住客掃碼頁驗收規格 | 新增住客掃碼頁的 Gherkin 介面根 `specs/truth/features/guest-web/`，自己的 `dsl.md`。 | 新的端需要自己的行為規格；004 命名 `frontdesk-web` 時已為此預留（research 決策 1、8）。 |
| ADD | `specs/truth/techstack.md` -> `測試與驗證` 住客掃碼頁 BDD techstack | 新增：與櫃檯 Web 同一個 `app/web-bdd/` 模組與 glue 套件，住客的步驟與頁面物件各自一個子套件；跑 `guest-web/` 的 Gherkin；`run-feature.sh` 的 feature 路徑可用 `guest-web/…` 指定根；runner 不用加。 | 必問題「住客掃碼頁的 BDD techstack」，使用者本輪指示沿用 web-bdd；腳本一律傳 `-Dcucumber.features`，引擎在此屬性有值時忽略 runner 寫死的目錄（research 決策 8）。 |
| ADD | `specs/truth/techstack.md` -> `測試與驗證` 住客掃碼頁驗收的視窗與輸入 | 新增第三組瀏覽器設定 `phone`：WebKit、393 × 852、觸控、繁體中文；住客規格只在這一組跑。 | 使用者本輪指示加手機設定；SC-012 的 393 寬（research 決策 8）。 |
| ADD | `specs/truth/techstack.md` -> `測試與驗證` BDD 後端資料準備補的 helper | 新增：`BackendChannel` 加取房間 QR 代碼、換 QR（以主管身分呼叫 `/admin/rooms/{roomNo}/qr`、`…/rotate`，不在 `/api/v1` 下）與住客評分的 helper。 | 住客驗收要用真的代碼開頁；換 QR 走正式 API 才有連坐撤銷（research 決策 8）。 |
| MODIFY | `specs/truth/techstack.md` -> `測試與驗證` 櫃檯 Web 單元測試 → Web 單元測試 | 類別改名為「Web 單元測試」，用途補上住客的純計算範圍（狀態對照表、訊息框優先序、額度估算、報修文字組合、問候時段、住客語言比對、401 重換狀態機）。採用技術 `Vitest` 與「驗收仍以 E2E 為主線」不變。 | 必問題「測試策略」沿用 004 決策 6，本輪沒有改判（research 決策 9）。 |
| ADD | `specs/truth/techstack.md` -> `本次開發不引入的技術` 住客頁另起前端專案、驗收模組或 Node 測試線 | 新增排除項。 | 使用者指定同一個程式；研究決策 1、8 的替代方案。 |
| ADD | `specs/truth/techstack.md` -> `本次開發不引入的技術` 住客 session 放 `localStorage`、`Authorization: Bearer`、網址 | 新增排除項。 | research 決策 3 的替代方案。 |
| ADD | `specs/truth/techstack.md` -> `本次開發不引入的技術` 住客頁用 `EventSource` | 新增排除項。 | 不能帶住客標頭（research 決策 5）。 |
| ADD | `specs/truth/techstack.md` -> `本次開發不引入的技術` 韓文、安裝才能用的功能、照片拍攝與上傳 | 新增排除項。 | `spec.md`「本版不做」與 NFR-004。 |
| ADD | `specs/truth/techstack.md` -> `本次開發不引入的技術` 網頁路由放在 `/guest/**` 底下 | 新增排除項。 | 與後端 API 路徑衝突（research 決策 1）。 |
| NOOP | `specs/truth/techstack.md` -> `前端` 員工 App 各列、櫃檯 Web 各列 | 已檢查，不需修改。櫃檯 Web 的框架、換頁、資料、狀態、樣式、多語系、拖曳、登入憑證、連後端、即時更新、驗收規格十一列的敘述與現況相符；住客頁的差異都另列新行。 | 本版不改櫃檯 Web 與手機 App（`spec.md`「本版不做」）。 |
| NOOP | `specs/truth/techstack.md` -> `後端`、`資料與媒體處理` | 已檢查全部列，不需修改。住客頁只用後端既有的住客介面、session 與事件串流。 | FR-054 本版不改後端。 |
| NOOP | `specs/truth/techstack.md` -> `測試與驗證` 後端、員工 App、櫃檯 Web BDD techstack 與視窗設定、`BackendChannel` 模組各列 | 已檢查，不需修改；櫃檯的兩組瀏覽器設定維持，住客另加一組。 | 後端與手機 App 的 BDD techstack 與測試策略沒有改判。 |

## /api-plan

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| NOOP | `specs/truth/contracts/groups/住客與房間/住客掃碼.yaml` -> `POST /guest-sessions/scan`（`guestSessionsScan`）、schema `guest_sessions`、`guest_sessionsRequest` | 已檢查，與程式相符，不修改。這是櫃檯替住客開 session 的介面：只收 `roomId`，只放行 `frontdesk`、`manager`、`duty_manager`；空房回 409 `room_vacant`，過了退房時刻回 409 `reservation_ended`。它不收 QR 代碼，住客頁不呼叫它。 | `controller/GuestRequestApiController.java:61-88`、`config/SecurityConfig.java:59`；research 決策 4。 |
| NOOP | `groups/住客與房間/住客掃碼.yaml` -> `GET /guest-requests`（`listGuestRequests`）、`POST /guest-requests`（`createGuestRequest`）、schema `CreateGuestRequestRequest` | 已檢查，行為與程式相符，不修改。`/api/v1/guest-requests` 與 `/guest/requests` 呼叫同一個 `GuestRequestService`：清單只用 session 綁定的訂房過濾、新的在前；`itemId` 與 `text` 必須剛好擇一，否則回 422；同一房 10 分鐘內第 4 筆回 429，已取消的也算。契約沒寫的現況：`itemId` 不在服務項目裡時，後端靜靜改當自由文字送出。住客頁不打這條路徑，改用 `/guest/requests`。 | `controller/GuestRequestApiController.java:90-99` 與 `controller/GuestController.java:52-61` 委派同一個 service；`service/GuestRequestService.java:60-62,66-67,89-93,114-117`；`service/RoomRequestRateLimiter.java:14-15`。FR-015、FR-016、FR-034。 |
| NOOP | `groups/住客與房間/住客掃碼.yaml` -> `POST /guest-requests/{id}/cancel`（`cancelGuestRequest`） | 已檢查，與程式和 `spec.md` 相符，不修改。只有 `triage`、`pool` 能撤回，其他狀態回 409 `state_conflict`；不是本次住宿的單回 404 `not_found`。程式另外放行「單沒有訂房而且同一間房」的情形，契約沒寫，住客頁碰不到。 | `service/TaskEngine.java:350-354`、`:444-445`、`common/error/StateConflictException.java:7`；`service/GuestRequestService.java:150-160`。FR-039、FR-040。 |
| NOOP | `components/schemas/住客掃碼.yaml` -> `GuestRequest`（含 `answer`） | 已檢查，契約比程式少，本輪照使用者限定不補。程式 `GuestTaskView` 有八欄：`id`、`type`、`state`、`dept`、`title`、`answer`、`rating`、`createdAt`；契約只寫其中五欄，少了 `type`、`dept`、`rating`。`state` 契約只列九個值，少了 `closed`，但住客自由文字分到工務、主管驗收後清單上就會出現 `closed`。`answer` 的形狀與說明相符。住客頁照程式現況讀 `type`、`dept`、`rating` 與 `closed`。 | `dto/GuestTaskView.java`；`specs/truth/data/data-model.dbml:180` 是十個值；契約 `components/schemas/住客掃碼.yaml:80-116`。FR-041 要用 `rating` 與 `closed`；狀態對照表要十個值。 |
| NOOP | `components/parameters.yaml` -> `IdempotencyKey`（`createGuestRequest`、`cancelGuestRequest` 引用） | 已檢查，不修改。兩件事：一、契約寫 `required: true`，程式是有帶才生效，沒帶照常處理、不回 400；`idempotency_key_required` 只用在櫃檯的三個寫入。二、住客寫入的回放靠讀程式推得成立、沒有實跑：Spring Security 的過濾器鏈用預設順序 -100，`IdempotencyFilter` 是 `@Order(0)`，在鏈裡面執行；那時 `BearerTokenAuthFilter` 已從 `X-Guest-Session` 把 `tms.tenant_id` 寫進請求屬性，`RequestAttributeTenantResolver` 讀得到。`/guest/scan` 走沒有認證過濾器的另一條鏈，不受冪等保護，但它不建需求。 | `filter/IdempotencyFilter.java:21,40-45`；`filter/BearerTokenAuthFilter.java:60-67,71-74`；`service/RequestAttributeTenantResolver.java:12-20`；`config/SecurityConfig.java:80`；`config/GuestScanSecurityConfig.java:15-17`。 |
| NOOP | `components/parameters.yaml` -> `IdempotencyKey` 對 NFR-002 是否足夠 | 已檢查，不修改，也不改成強制。research 決策 4 定了每個寫入都帶鍵、按下到回覆之間鎖住按鈕；搭配這兩點，循序的重送會回放第一次的結果，足以支撐 NFR-002。兩個缺口：一、先查後做沒有鎖，同一把鍵的兩個請求同時在途時兩個都會執行、各建一張單；二、4xx（含 429）也會存下來回放，被限流後要換新鍵才能再送（research 已是每次送出換新鍵）。 | `filter/IdempotencyFilter.java:46-57`、`service/IdempotencyService.java:24-30`（`ON CONFLICT DO NOTHING`）。後端測試只驗過員工的循序重送（`tms/src/test/resources/features/13-core-backbone.feature:17-28`），住客路徑交 research spike 第 3 項實跑。 |
| NOOP | `components/parameters.yaml` -> `GuestSession`；`components/responses.yaml` -> `Unauthorized`、`TooManyRequests` | 已檢查，與程式相符，不修改。`X-Guest-Session` 與 `Authorization: Bearer` 效果相同；沒有合法前綴、過期、已撤銷一律回 401 `unauthorized`。 | `filter/BearerTokenAuthFilter.java:50-67,109-115`。FR-004。 |
| NOOP | `components/schemas/shared.yaml` -> `Error` 的 code 清單 | 已檢查，不修改。住客會碰到的代碼在 Error 說明裡都沒有逐一列出：`room_vacant` 只出現在 tag 說明與 `guestSessionsScan`；`rate_limited` 全契約查無；`validation_failed` 只以大寫 `VALIDATION_FAILED` 當 example 出現，程式發的是小寫；`state_conflict` 的說明只講工務單與查房。住客頁依程式的小寫代碼判斷。 | `common/error/TooManyRequestsException.java:8`、`common/error/UnprocessableEntityException.java:8`、`common/error/StateConflictException.java:7`；契約 `components/schemas/shared.yaml:1-52`。 |
| NOOP | `openapi.yaml` -> tag `住客掃碼 API` 的說明 | 已檢查，不修改。說明寫「掃房間 QR 換住客 session…空房或 token 已作廢回 409 room_vacant」，但這一組沒有任何操作收 QR 代碼；它描述的其實是不在契約裡的 `POST /guest/scan`，容易讓下游以為掃碼入口已經寫進契約。 | `openapi.yaml:15-16`；`service/GuestScanService.java:54-93`。 |
| NOOP | `openapi.yaml` -> path `/guest/scan`（契約沒有） | 缺口，使用者決定本輪不補。程式現況：公開端點，走自己的安全鏈；請求 `{token}`，回 `ScanResponse`（`sessionToken`、`expiresAt`、`roomNo`、`infoPages`、`menu`），形狀和 `guest_sessions` 不同。錯誤三種：代碼空白回 422 `validation_failed`；代碼不存在、已作廢、空房都回同一個 409 `room_vacant`；同一 IP 10 分鐘內失敗次數達到 `scan_fail_threshold`（預設 10）回 429 `rate_limited`。 | `controller/GuestController.java:38-44`；`service/GuestScanService.java:56-61,62-68,77-79,92`；`dto/ScanResponse.java`。FR-001 至 FR-004；plan 落差表 1。 |
| NOOP | `openapi.yaml` -> path `/guest/home`（契約沒有）；`groups/營運管理/租戶設定.yaml` -> `GET /tenant-configs/guest-home`（`tenantConfigGuestHome`） | 缺口，使用者決定本輪不補。程式的 `GET /guest/home` 回 `GuestHomeView`：`infoPages` 每筆 `key`、`title`、`body`，`menu` 每筆 `key`、`label`。契約裡的 `tenantConfigGuestHome` 程式沒有實作，而且 `/api/v1/tenant-configs/**` 只放行 `manager`，住客讀不到，不能當住客首頁的來源。 | `controller/GuestController.java:46-50`；`dto/GuestHomeView.java`、`dto/GuestMenuItemView.java`；`config/SecurityConfig.java:58`；契約 `groups/營運管理/租戶設定.yaml:47-61`、`openapi.yaml:44,175-176`。 |
| NOOP | `openapi.yaml` -> paths `/guest/requests`、`/guest/requests/{id}/cancel`（契約沒有） | 缺口，使用者決定本輪不補。這三個操作和契約裡 `/api/v1/guest-requests` 那三個是同一個 service、同一套規則，只差路徑前綴與安全規則那一行。住客頁用 `/guest/requests`，因為只有這一組另外有首頁資料、評分與串流。 | `controller/GuestController.java:52-66`；`config/SecurityConfig.java:49,67`；research 決策 4。 |
| NOOP | `openapi.yaml` -> path `/guest/requests/{id}/rating`（契約沒有）；`groups/現場作業/任務.yaml` -> `POST /tasks/{id}/rate`（`tasksRate`）、tag `任務 API` 說明的「住客收回與評分」 | 缺口，使用者決定本輪不補。程式的評分：請求 `{rating}`；空的或不在 1 到 5 回 422 `validation_failed`；狀態不是 `done`、`inspected` 或已經評過回 409 `state_conflict`；不是本次住宿回 404。後端不擋「櫃檯已回答」的單，FR-041 只靠畫面不顯示評分卡。契約的 `tasksRate` 程式查無實作，也沒有住客 session 參數，請求還是用 `tasksRequest`。 | `controller/GuestController.java:68-71`；`service/GuestRequestService.java:103-111`；`service/TaskEngine.java:383-389`；`controller/` 搜尋 `/rate` 只找到 `/pass-rate`。契約 `groups/現場作業/任務.yaml:207-237`、`openapi.yaml:42,159`。FR-041 至 FR-044。 |
| NOOP | `openapi.yaml` -> path `/guest/stream`（契約沒有）；`groups/現場作業/員工事件串流.yaml` -> `staffStream`；`components/schemas/員工事件串流.yaml` -> `StaffStreamResync.refetch`、`StaffStreamHeartbeat.degraded`、`StaffStreamDegraded`、`StaffStreamRecovered` | 缺口，使用者決定本輪不補。住客串流在 truth 裡沒有任何地方寫明，只有後端測試 `tms/src/test/resources/features/14-sse-stream.feature` 寫到（不在 truth）。程式現況：連上後依序 `init{stream_pos}`、`resync{reason: connected, refetch: /guest/home}`；`task{stream_pos, task_id, seq, type, occurred_at}` 不帶卡片；只收自己這筆訂房的單。`heartbeat` 每 15 秒一則，只帶 `{stream_pos}`，沒有 `degraded` 欄位。`degraded` 只在 `kafka_consumer_lag` 時送給住客；降級期間新連上的住客不補送；`recovered` 從不送給住客。員工串流說明裡的「對所有連線送 degraded／recovered」「每則 heartbeat 帶降級旗標」都只適用員工連線。 | `controller/GuestStreamController.java:17-21`；`service/StreamSubscriber.java:27,47-54`；`service/StreamConnection.java:75-90,114-125`（只有 `isStaff()` 時才加 `degraded`）；`service/StreamConnectionRegistry.java:76,128-131,145-149`。FR-045 至 FR-047、NFR-003；plan 落差表 5。 |
| NOOP | `groups/住客與房間/房間QR憑證.yaml` -> `listRoomQrTokens`、`readRoomQrTokens`、`roomQrTokensRotate`、schema `room_qr_tokens` | 已檢查，契約與程式對不上，本輪不改。契約的 `/room-qr-tokens` 系列程式沒有實作。程式是 `GET /admin/rooms/{roomNo}/qr` 與 `POST /admin/rooms/{roomNo}/qr/rotate`：只放行 `manager`、不在 `/api/v1` 下，回 `{roomNo, token, version, issuedAt}`，換 QR 另回 `revokedSessions`。住客頁不呼叫；BDD helper 照 techstack 用 `/admin` 路徑。 | `controller/RoomQrController.java:19-27`；`config/SecurityConfig.java:55`；`dto/RoomQrResponse.java`、`dto/QrRotateResponse.java`；plan 落差表 9、10。 |

`/api-plan` 檢查時另外記下、本輪不處理的事（契約不改）：

- 契約 `IdempotencyKey` 的 `required: true` 會誤導 `/dsl-refine` 寫出「沒帶鍵回 400」的住客情境；`servers: /api/v1` 也會讓 `/tasks` 把住客路徑寫成 `/api/v1/guest/...`。下游以程式現況與 `specs/_inputs/guest-web-findings-2026-11-18.md` §2 為準。
- 同一把鍵同時在途時會重複建單；NFR-002 實際上靠鎖住按鈕撐住，影響低。
- 下一輪建議的 ADD：新增 `groups/住客與房間/住客手機.yaml`，操作層用 `servers: /`（照 `staffStream` 的做法），放 `guestScan`（`ScanRequest`、`ScanResponse`）、`readGuestHome`（`GuestHome`、`GuestInfoPage`、`GuestMenuItem`）、`/guest/requests` 的清單、建立、撤回、`rateGuestRequest`（`GuestRatingRequest`），以及 `guestStream` 配 `GuestStreamInit`、`GuestStreamResync`、`GuestStreamTask`、`GuestStreamHeartbeat`、`GuestStreamDegraded` 五個 schema。
- 同一輪要一起改的 MODIFY 與待決項：`GuestRequest` 補 `type`、`dept`、`rating` 與 `closed`；`Error` 說明補 `room_vacant`、`rate_limited`、`validation_failed`、`state_conflict` 的住客用法；`IdempotencyKey` 改成只有櫃檯寫入必帶；`/room-qr-tokens` 改成 `/admin/rooms/{roomNo}/qr`。重複的 `/api/v1/guest-requests`、沒有實作的 `tasksRate` 與 `tenantConfigGuestHome` 各自要不要刪，等使用者決定。
- `/technical-research` 原本寫「每則心跳帶 `degraded` 旗標給所有連線」是錯的（旗標只給員工，`StreamConnection.java:122-124`）；已改正 `techstack.md` 住客掃碼頁即時更新列與 `research.md` 決策 5，恢復規則改成「串流重新連上並收到 `init` 才停止重讀」。

## /data-plan

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| NOOP | `specs/truth/data/data-model.dbml` -> `Table room_qr_tokens`：`token`、`status`、`version`、partial UNIQUE `(tenant_id, room_id) WHERE status='active'`、Note 輪替不變條件 | 已檢查，不需修改。網址 `/g/{代碼}` 裡的代碼就是 `token`，22 個 base62 字元，放進網址是安全的（`tms/src/main/java/com/athena/tms/dto/QrTokens.java:6-18`）。輪替在同一個交易裡作廢舊代碼、取現有最大 `version` 加 1 發新代碼、把該房所有未撤銷的 session 標上 `revoked_at`（`service/RoomQrService.java:44-61`）。DDL 與文件一致（`db/migration/V1__schema.sql:98-116`）。舊代碼的 `status` 是 `revoked`，再掃一次回 `409 room_vacant`（`service/GuestScanService.java:62-68`）。 | FR-001 至 FR-004；FR-054 本版不改後端。 |
| NOOP | `specs/truth/data/data-model.dbml` -> `Table guest_sessions`：`session_token`、`reservation_id`、`expires_at`、`revoked_at`、Note E-17 | 已檢查，不需修改。驗證條件是「未撤銷而且未過期」，缺一個就回 401，與 Note 一致；程式另外比對 token 前綴的租戶（`filter/BearerTokenAuthFilter.java:109-116`）。`expires_at` 等於當下那筆訂房的退房時刻（`service/GuestScanService.java:76-88`），也是住客頁 `expiresAt` 的來源。session 存在瀏覽器 `sessionStorage`，不需要新增欄位。 | FR-004、FR-005；research 決策 3。 |
| NOOP | `specs/truth/data/data-model.dbml` -> `Table rooms` -> `room_no`；`Table reservations_cache`：`checkin_at`、`checkout_at`、`superseded_at`、`guest_name` | 已檢查，不需修改。住客頁的 `roomNo` 來自 `rooms.room_no`（`service/GuestScanService.java:81,92`）。換 session 時找「入住時間不晚於現在、退房時間在現在之後、沒有作廢」的那筆訂房（`dao/ReservationCacheRepository.java:18-19`），找不到就回 `room_vacant`。`guest_name` 不給住客頁。 | FR-002、FR-007、FR-053。 |
| NOOP | `specs/truth/data/data-model.dbml` -> `Table tasks`：`id`、`type`、`state`、`dept`、`title`、`answer`、`rating`、`created_at`，索引 `(tenant_id, reservation_id, created_at)` | 已檢查，不需修改。住客需求的八個欄位都直接投影自 `tasks`（`dto/GuestTaskView.java:7-9`）。`state` 十個值、`type` 四個值、`dept` 三個值的 CHECK 與 spec 的狀態對照表一致。清單依 `reservation_id` 篩選、新的在前（`service/GuestRequestService.java:92`），走既有索引。`rating` 只收 1 到 5，每張單只能評一次（`service/TaskEngine.java:387`）。 | FR-019 至 FR-044；SC-006。 |
| NOOP | `specs/truth/data/data-model.dbml` -> `Table tasks`：`assignee_id`、`started_at`、`done_at`、`ended_at`、`cancel_reason`、`note` | 已檢查，不需修改。這些資料都存在，只是給住客的 DTO 不帶；是後端選擇不給住客看，不是資料缺漏。 | FR-024、FR-026、FR-053。 |
| NOOP | `specs/truth/data/data-model.dbml` -> `Table task_events` | 已檢查，不需修改。住客頁不讀事件，四步進度由 `state` 推得，也不顯示各步時間。取消與評分沿用既有的 `cancelled`（`guest_withdrew`）與 `rated`。 | FR-026、FR-039、FR-043。 |
| NOOP | `specs/truth/data/data-model.dbml` -> `Table tenant_config` -> `key` 的 `request_menu`、`guest_info_pages` | 已檢查，不需修改。服務項目的鍵與名稱（`key`、`label`）與資訊頁的鍵、標題、內文（`key`、`title`、`body`）都來自這兩個設定鍵的 jsonb 值（`service/GuestTenantConfigReader.java:25-51`）。設定裡每個項目還有 `dept`、`type`，給住客的 DTO 只帶 `key`、`label`（`dto/GuestMenuItemView.java:6-8`）；是後端選擇不給住客看，不是資料缺漏。 | FR-012、FR-013、FR-048 至 FR-050。 |
| NOOP | `specs/truth/data/data-model.dbml` -> `Table tenants` -> `code` | 已檢查，不需修改。飯店代碼最短 3 個字（`data-model.dbml:19`），`g` 不可能是飯店代碼，`/g/` 與飯店網址 `/{飯店代碼}/…` 不會撞，不需要加保留字。 | research 決策 1。 |
| NOOP | `specs/truth/data/data-model.dbml` -> `Table tasks` Note（衍生值） | 已檢查，不新增說明。顯示階段（由 `state` 查表）、可否取消、鎖定說明、可否評分、進度條、10 分鐘剩餘額度（由住客自己清單的 `created_at` 算）都只在前端計算，不存、也不需要後端提供，不寫進資料真相。 | spec「關鍵實體」把顯示階段標為衍生值；FR-016、FR-021。 |
| NOOP | `specs/truth/data/data-model.dbml` -> `reservations_cache.lang`、`Table guest_sessions` | 已檢查，不新增欄位。住客頁的介面語言只存在手機的 `localStorage`，不讀也不寫 `reservations_cache.lang`（那是訂房帶來的住客語言，與介面語言無關）。 | NFR-004；research 決策 6。 |
| NOOP | `specs/truth/data/data-model.dbml` -> 其餘資料表、全部 `Ref` 與索引 | 已檢查，不需修改。 | FR-054；使用者限定本輪只記 NOOP。 |

`/data-plan` 檢查時另外發現、本輪不處理的事（都不改資料真相）：

- `guest_sessions.revoked_at` 的說明寫「QR 輪替、提前退房、訂單取消時整批標上」，但程式只有 QR 輪替會寫 `revoked_at`（`RoomQrService.java:55-59`）；`tms/src/main` 沒有任何程式寫入 `reservations_cache`，提前退房的人 session 會一直有效到原本的退房時刻。說明領先現況，之後另開一輪 MODIFY。
- `request_menu`、`guest_info_pages` 兩個設定鍵在 DBML 沒有寫值的結構與預設（其他鍵都有）。程式：缺 `key`、`dept` 或 `type` 的項目會被丟掉；全部被丟光或沒設定時回出廠五項；`label` 可能是 null；資訊頁標題空白時是 null、後端不過濾。之後另開一輪 MODIFY 補上。
- `scan_fail_threshold` 的計數器與 `rotate` 寫的 `revoked_at` 都用系統時鐘，不用注入的 Clock（`ScanIpRateLimiter.java:13-60`、`RoomQrService.java:55-59`），撥 BDD 後端時鐘影響不到它們；交 `/tasks` 排驗收時注意。
- spec「關鍵實體」說部門「待派單時可能沒有」，但 `tasks.dept` 是 NOT NULL，待派單時固定是 `frontdesk`（`V1__schema.sql:205-206`）；畫面兩種情況都顯示「櫃檯」，沒有影響。
- **高影響、要使用者決定**：QR 代碼等於整間房的長期通行證，只有主管手動能換（`RoomQrController.java:24` 是唯一入口）。上一位住客退房後，若下一位已入住、QR 還沒換，他從歷史記錄打開舊網址，FR-004、FR-005 的靜默重換會換到下一位住客的訂房，看得到對方的需求清單、也能替對方送出或取消。這是後端既有設計，住客頁讓它變成點一下就發生；退房時自動換 QR 要改後端，超出 FR-054。本輪不處理，列為待決。

## /dsl-refine

路徑縮寫：`gw/`＝`specs/truth/features/guest-web/`。依據欄引用驗收例子的檔名（省略 `.feature`）、FR 編號或 `ui-plan.md` 的題號。全部是新增；本版不改後端，`backend/`、`frontdesk-web/`、`ios-app/` 三個介面根只檢查、不改動，各記一列 NOOP。依賴 `ui-plan.md`「預設，待使用者確認」的 Example 上方都有 `# [need clarification]` 並註明題號（C-01、C-02、C-04、C-05、C-07、C-08、ui-plan 待決事項 1、4），共 12 處。機械稽核（`audit_feature_dsl_topology.py`）：29 個 feature、9 個模組、38 個根 DSL rows、145 個模組 DSL rows、902 個 step，PASSED、沒有 warning；`@cucumber/gherkin` 解析 0 錯誤；145 條 Rule、236 個可執行 Example，每個 Example 恰好一個 When。

### 住客掃碼頁

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `gw/dsl.md` -> 共通實作約定、「需求的狀況對照」、38 個共用句型 | 新的介面根。定下預設飯店與 311 房的訂房、308 空房、員工帳號、時間（10/26 14:10）、預設五項服務項目與三頁資訊頁、分類規則預設不命中、只有一組瀏覽器設定「手機直向 393 × 852 觸控」、住客憑證一律真的用代碼換、畫面與後端都要成立的斷言方式；跨模組共用的 Given（住宿有以下需求、已打開首頁、停在某頁、開著進度頁、介面語言、連不上、櫃檯代開單）、When（打開網址、按、打開頁、重新整理）、Then（看得到／沒有文字、按鈕可不可按、畫面是某頁、標題列、狀態標籤、清單段落、訊息框、四步進度、底部、進度卡、後端多了／沒多需求、提示框）。 | `techstack.md` 住客掃碼頁驗收規格列（介面根 `guest-web`、自己的 `dsl.md`）、住客掃碼頁驗收的視窗與輸入列；`research.md` 決策 8。 |
| ADD | `gw/進入層/dsl.md` -> 23 個模組句型 | 空房、換 QR、憑證不見／失效、過了退房時刻、掃碼被限流、後端出錯、停在重試狀態、恢復連線、櫃檯 Web 登入狀態；打開舊代碼／不存在的代碼／歷史記錄／櫃檯 Web；0 次輸入、沒有要再掃、與空房那一頁文字相同、沒有可點的東西、請求不帶員工憑證、櫃檯 Web 的狀態。 | FR-001 至 FR-005、FR-055、NFR-001；`research.md` 決策 3。 |
| ADD | `gw/進入層/掃碼就進首頁.feature`（3 條 Rule） | 打開網址不用輸入就是首頁、重新整理還是首頁、關掉再從歷史記錄打開回到原本那一頁（首頁與進度頁各一）。 | `掃房間的QR Code不用登入就進首頁` Rule 1；FR-001、FR-005、NFR-001、SC-001。 |
| ADD | `gw/進入層/無法使用頁.feature`（2 條 Rule） | 空房、QR 已作廢、代碼不存在三種情形同一頁「請洽櫃檯」且文字一模一樣、看不出原因；這一頁沒有首頁的內容與可點的東西。 | 同檔 Rule 2；FR-002、FR-003、SC-002；C-01。 |
| ADD | `gw/進入層/憑證失效時靜靜重換.feature`（3 條 Rule） | 正在用時 QR 被換掉下一次拿資料就變無法使用頁；憑證不見或後端不再承認時用代碼靜靜重換接著用（重新整理、點主視覺卡各一）；退房後再開是無法使用頁。 | 同檔 Rule 3；FR-004、FR-005。 |
| ADD | `gw/進入層/重試狀態.feature`（5 條 Rule） | 連不上、被限流、後端出錯三種都顯示「連線失敗，請再試一次」與「重試」、不顯示「請洽櫃檯」；重試換到就進首頁；重試後回無法使用就變無法使用頁；又失敗留在重試狀態；正在用時重換也失敗顯示重試狀態、後端恢復後按重試回到原本要去的那一頁。 | 同檔 Rule 4；`spec.md` 邊界情況第 2 條；`ui-plan.md` 畫面 1。 |
| ADD | `gw/進入層/與櫃檯Web互不影響.feature`（3 條 Rule） | 登入過櫃檯 Web 的手機掃碼看到住客首頁、請求不帶員工憑證；回到櫃檯 Web 登入狀態照舊；開過住客頁的手機櫃檯 Web 仍要登入。 | 同檔 Rule 5；FR-055；`research.md` 決策 3。 |
| ADD | `gw/首頁/dsl.md` -> 11 個模組句型 | 訂房住客姓名；點房號膠囊、點即將開放的入口；首頁由上到下的區塊順序、頂列膠囊與左上留白、問候兩行、畫面沒有變化、進度卡的進度條與說明、沒有進度卡、「即將開放」標籤數。 | FR-006 至 FR-011、FR-028、FR-051。 |
| ADD | `gw/首頁/首頁內容與問候.feature`（7 條 Rule） | 區塊順序與沒有需求時沒有「我的需求」、頂列右上兩顆膠囊與左上留白、問候三段六個時刻、不顯示姓名飯店名日期、房號膠囊點了沒選單、兩張主視覺卡的文字且不提照片、點主視覺卡進兩個子頁。 | `首頁的內容與即將開放的入口` Rule 1；FR-006 至 FR-010、FR-053；C-02、C-07、C-08。 |
| ADD | `gw/首頁/我的需求區.feature`（6 條 Rule） | 有進行中的顯示最新一張進度卡與「全部 n 項」、進度卡的固定內容、六種狀況的進度條與說明、全部結束時「目前沒有進行中的需求／查看紀錄」且 n 含已取消、「查看」進進度頁、「查看紀錄」與「全部 n 項」進清單。 | 同檔 Rule 2；`我的需求清單與進度頁照同一張狀態對照表` Rule 6；FR-009、FR-028。 |
| ADD | `gw/首頁/即將開放的入口.feature`（2 條 Rule） | 四個入口的文字、四個「即將開放」標籤、沒有收費政策字句；點了畫面不變、後端沒有多需求（四個入口各一）。 | 同檔 Rule 3；FR-051、SC-010。 |
| ADD | `gw/備品補充/dsl.md` -> 19 個模組句型 | 飯店有沒有設定服務項目、在備品補充頁、剛送出停在成功頁／已回到首頁、剛被估算擋下、第一張之後斷線、後端回覆很慢、送到一半斷線後恢復；按加號／減號 N 次、連按兩下；清單順序、每列「最多 5」與 0、數量、加減號可不可按、後端送出的順序。 | FR-012 至 FR-018、NFR-002；`research.md` 決策 4。 |
| ADD | `gw/備品補充/項目清單與數量.feature`（5 條 Rule） | 預設五項與飯店自訂項目照順序、剛打開全 0 且減號與「選擇數量」不能按、加號加 1、到 5 加號不能按、減到 0 減號不能按。 | `備品補充選好數量一次送出` Rule 1、2；FR-012 至 FR-014。 |
| ADD | `gw/備品補充/送出鈕與成功頁.feature`（9 條 Rule） | 「送出 n 項」與按鈕下的字、每項各建一張帶數量且照清單順序、多項成功頁、一項成功頁標題不帶 ×1、多項「查看進度」進清單、一項進那一筆、「回首頁」且進度卡是剛送的、送出後數量歸零、連按兩下只建一次。 | 同檔 Rule 1、3；FR-014、FR-015、FR-018、NFR-002、SC-005。 |
| ADD | `gw/備品補充/十分鐘額度估算.feature`（5 條 Rule） | 超過剩餘額度一張不送並提示還能送幾項、一項都不能送時的內文、已取消的也算、過了 10 分鐘可以送、調整數量後提示消失。 | 同檔 Rule 4；FR-016；`spec.md` 額度公式。 |
| ADD | `gw/備品補充/後端在送到一半拒絕.feature`（5 條 Rule） | 估算通過但第二張被擋時停止、已建立的保留、不顯示成功頁；被擋的畫面（已建立的歸零、提示框說已送出幾項）；一張都沒建立就被拒的內文；送到一半斷線的畫面與恢復後清單上只有已建立的；送出時完全連不上的提示與數量保留。 | 同檔 Rule 5；FR-017；`spec.md` 邊界情況第 6 條；C-04；`ui-plan.md` 待決事項 1。 |
| ADD | `gw/設備報修/dsl.md` -> 15 個模組句型 | 分類規則有沒有命中、在設備報修頁、已選類別、已輸入補充說明、已有 200 字、剛送出報修停在成功頁；點類別、輸入文字、輸入 N 個字；只有一顆選中、提示字、字數、仍是原本 200 字、後端這張是打字的帶語言不帶服務項目。 | FR-030 至 FR-036、SC-008。 |
| ADD | `gw/設備報修/報修頁與送出條件.feature`（6 條 Rule） | 頁面內容與沒有照片、沒選類別不能送、選了就能送、「其他」必填（沒填、只有空白、填了）、改選別的類別回到選填、字數與 200 字上限。 | `設備報修選類別補幾個字就送出` Rule 1 至 3；FR-030 至 FR-033。 |
| ADD | `gw/設備報修/組成文字送出.feature`（8 條 Rule） | 自由文字帶介面語言不帶服務項目、只有類別、類別加補充說明、「其他」只有補充說明、一項的成功頁、沒命中停在待派單且「查看進度」進那一筆看到「已送出」「櫃檯」、命中分給工務且進度頁「已受理」「工務收到」、日文補充說明。 | 同檔 Rule 1；FR-034、FR-036；`ui-plan.md` 畫面 4 與待決事項 4。 |
| ADD | `gw/設備報修/報修的限流.feature`（3 條 Rule） | 送滿 3 筆估算擋下、後端拒絕時同一個提示不出現成功頁、連不上時提示且類別與字保留。 | 同檔 Rule 4；FR-035；`ui-plan.md` 待決事項 1。 |
| ADD | `gw/我的需求/dsl.md` -> 9 個模組句型 | 另一支手機已送出、從某入口進了進度頁；清單各列、各列的標籤與段落、依類型的圖示、標籤顏色、進度頁第一張卡、沒有訊息框、唯一的時間。 | FR-019 至 FR-029。 |
| ADD | `gw/我的需求/清單兩段與空狀態.feature`（5 條 Rule） | 兩段且最新在上、每列的圖示標題第二行標籤與底下那一行、沒有內容的段落不出現（兩個方向）、空狀態不提客服、另一支手機送的也在清單上。 | `我的需求清單與進度頁照同一張狀態對照表` Rule 1；FR-019、FR-020；C-05。 |
| ADD | `gw/我的需求/狀態對照表.feature`（7 條 Rule） | 十種狀況在清單上的標籤與段落、進度頁的標籤四步底部（十列大綱）、取消鈕的文字、鎖定說明的文字、五種標籤的顏色、沒有飯店內部用語與同仁名字、部門三種字且待派單是「櫃檯」。 | 同檔 Rule 2；FR-021、FR-022、FR-027、FR-037、FR-053、SC-006、SC-007。 |
| ADD | `gw/我的需求/進度頁內容與四步副文.feature`（4 條 Rule） | 標題列與第一張卡、八種狀況的四步樣子與副文（含工務收到、中斷無內部字、已完成只有第 1 步副文）、沒有時間與姓名且唯一時間是送出時間、24 小時制「時:分」沒有日期（進度頁與清單各一）。 | 同檔 Rule 3、5；FR-023、FR-025、FR-026。 |
| ADD | `gw/我的需求/狀態訊息框.feature`（5 條 Rule） | 待派單「櫃檯正在看您的需求」且有進度條、三種已完成「已完成／已處理完成。」四步全亮、櫃檯回覆沒有進度條評分卡與底部且清單在已結束、已取消兩種同一句不顯示原因、其他四種狀況沒有訊息框。 | 同檔 Rule 4；FR-024。 |
| ADD | `gw/我的需求/返回回到進來的地方.feature`（3 條 Rule） | 從首頁進度卡進的回首頁、從清單進的回清單、從成功頁進的回清單。 | 同檔 Rule 7；FR-029。 |
| ADD | `gw/取消與評分/dsl.md` -> 16 個模組句型 | 取消確認框開著、已點第 N 顆星、畫面還沒跟上時同仁認領／另一支手機評分、剛取消／剛評分停在進度頁；點第 N 顆星；確認框、沒有確認框、頂端提示浮出後消失、後端需求的狀況、後端星等、五顆空心、只有前 N 顆實心、沒有留言欄、有評分卡。 | FR-037 至 FR-044、NFR-006。 |
| ADD | `gw/取消與評分/取消需求.feature`（7 條 Rule） | 按取消先跳確認框且需求沒動、「返回」只關框、「取消需求」撤回並提示（待認領與待派單各一）、取消後依對照表顯示已取消、回清單在已結束、同仁剛接手時被拒並更新成接手後的樣子、連不上時提示畫面不變。 | `還沒有人接手前自己取消需求` 全部 3 條 Rule；FR-038 至 FR-040；`ui-plan.md` 待決事項 1。 |
| ADD | `gw/取消與評分/評分.feature`（9 條 Rule） | 評分卡的內容與沒有留言欄、查房通過可評、五個星等的字樣、可以改點、送出後後端記下星等並提示且訊息框進度條留著、評過的不再出現（重新整理與昨天評過各一）、`closed` 沒有評分卡、櫃檯回答沒有評分卡、另一支手機先評時被拒不說謝謝、連不上時提示且星等留著。 | `做完之後給星等` 全部 3 條 Rule；FR-041 至 FR-044；`ui-plan.md` 待決事項 1。 |
| ADD | `gw/即時更新/dsl.md` -> 25 個模組句型 | 連線已中斷、超過 N 秒沒訊號、後端宣告延遲、中斷後已恢復；同仁在手機上認領／開始／完成、櫃檯當場回答、櫃檯取消、另一支手機送出／取消、即時連線中斷、後端要求重新同步；N 秒內的標籤、副文、底部、訊息框、評分卡、四步、清單段落、進度卡、進度條與說明、看得到文字、重讀首頁與清單；沒有按任何東西、沒有每 4 秒定時重讀、沒有任何提示列或錯誤。 | FR-045 至 FR-047、NFR-003；`research.md` 決策 5。 |
| ADD | `gw/即時更新/同仁一動畫面跟上.feature`（7 條 Rule） | 停在進度頁的認領、開始、完成各 5 秒內跟上；停在清單時完成的移到已結束；停在首頁時進度卡亮到處理中、換成下一張、換成「目前沒有進行中的需求」；櫃檯當場回答；櫃檯取消；另一支手機送出與取消；後端要求重新同步時重讀首頁與清單。 | `同仁一動住客的畫面自己跟上` Rule 1；`spec.md` 邊界情況第 4 條；FR-045、FR-047、SC-009。 |
| ADD | `gw/即時更新/連線異常時定期重讀.feature`（6 條 Rule） | 中斷時沒有提示；中斷期間、35 秒沒訊號、後端宣告延遲三種異常都在 5 秒內跟上且沒有提示；恢復後回到即時不再定時重讀；連線正常時也不定時重讀。 | 同檔 Rule 2；FR-046、NFR-003；`research.md` 決策 5。 |
| ADD | `gw/住宿指南/dsl.md` -> 10 個模組句型 | 飯店設定了資訊頁、沒有資訊頁、某頁內文兩行；首頁住宿指南的順序、列上沒有圖片時間地點、「服務」下面直接是某行、畫面是某資訊頁、內文、內文兩行、內文空白。 | FR-048 至 FR-050。 |
| ADD | `gw/住宿指南/首頁的住宿指南清單.feature`（3 條 Rule） | 純清單照順序且首頁上沒有內文與「複製」、沒有資訊頁整段不出現、標題空的不列。 | `看飯店的住宿指南` Rule 1、2；FR-048、FR-050；`spec.md` 邊界情況最後一條。 |
| ADD | `gw/住宿指南/資訊頁內容.feature`（5 條 Rule） | 點一列進內容頁的標題列與內文原文、換行照原文、返回回首頁、英文介面時標題與內文仍是原文而房號標籤跟著介面語言、內文空的頁留白。 | 同檔 Rule 1、2；FR-049、NFR-004。 |
| ADD | `gw/共用外框/dsl.md` -> 17 個模組句型 | 瀏覽器語言且沒選過、語言選單開著、另一支手機的語言；點語言膠囊、捲到底；介面語言、膠囊短寫法、選單內容與打勾、沒有選單、手機記住的語言、另一支手機的語言、沒有子頁標題列、沒有橫向捲動單欄置中、44 點、長標題截斷不蓋標籤、進度頁標題完整換行、按鈕釘在頁底。 | FR-052、NFR-004、NFR-005；`research.md` 決策 6、7。 |
| ADD | `gw/共用外框/子頁標題列.feature`（3 條 Rule） | 四個子頁同一種標題列且沒有飯店名稱、成功頁與無法使用頁沒有標題列、三個子頁的返回回首頁。 | `首頁的內容與即將開放的入口` Rule 4；FR-052。 |
| ADD | `gw/共用外框/手機版面.feature`（3 條 Rule） | 393 寬下七種頁面沒有橫向捲動單欄置中且可點的至少 44 點、長標題在清單截斷不蓋標籤在進度頁完整換行、兩個送出鈕釘在頁底。 | 同檔 Rule 5；NFR-005、SC-012；`spec.md` 邊界情況「標題超過一行」。 |
| ADD | `gw/共用外框/介面語言.feature`（6 條 Rule） | 八種瀏覽器語言的第一次對應與膠囊、選單四種各自寫法且打勾沒有韓文、選了就換並記住、切換只改介面文字（首頁、備品項目、清單與櫃檯回覆三例）、關掉再開還是記住的語言、另一支手機不受影響。 | `介面語言跟著瀏覽器與這支手機上的選擇` 全部 2 條 Rule；NFR-004、SC-011；`research.md` 決策 6。 |
| MODIFY | `gw/dsl.md` -> 共通實作約定「文字比對」、`頁面頂端的標題列是返回鈕、"{頁標題}" 與 "房號 {房號}"`；`gw/共用外框/dsl.md` -> `畫面上沒有子頁的標題列` | 「返回」加進跟著介面語言換字的清單（英文「Back」）；兩列的返回鈕改以介面語言的「返回」認，按鈕或連結都算。 | `住宿指南/資訊頁內容.feature` 的英文場景；原型 `ui/guest.js` 的字典把「返回」譯成「Back」、返回鈕是連結；產品語系 `en.ts` 的 `back` 是「Back」。 |
| MODIFY | `gw/dsl.md` -> `清單 "{段落}" 由上到下是 "{標題清單}"` | 後端那一半改成由上到下送出時間新到舊、同一刻送出的不分先後；畫面上同一刻送出的照送出先後，後送出的在上（與新到舊一致）。 | 後端時鐘在場景裡固定，同一次送出的幾張 `created_at` 相同，原本的「新到舊」在同分時沒有定義；`備品補充/送出鈕與成功頁.feature` 的「礦泉水 ×2、毛巾」照 FR-015 的送出順序。 |
| MODIFY | `gw/dsl.md` -> `頁面頂端的標題列是返回鈕、"{頁標題}" 與 "房號 {房號}"`；`gw/住宿指南/dsl.md` -> 模組說明 | 第二行的房號標籤跟著介面語言（繁體中文「房號 311」、英文「Room 311」），參數說明與必查改寫；飯店設定的頁標題與內文仍照原文。 | `住宿指南/資訊頁內容.feature` 英文場景期待「Room 311」；房號標籤是介面文字，不是飯店內容。 |
| MODIFY | `gw/dsl.md` -> 共通實作約定「文字比對」 | 「送出報修」加進跟著介面語言換字的清單。 | `設備報修` 的日文介面場景按「送出報修」時，畫面上是日文的按鈕名。 |

### 其他介面根

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| NOOP | `specs/truth/features/backend/**`（含 `dsl.md` 與 15 個模組） | 已檢查，不新增住客掃碼的後端模組，也不改任何既有 feature 與 DSL。住客頁用到的後端規則（勾選單直接進待認領、自由文字先過分類規則、撤回只在 `triage`、`pool`、評分只在 `done`、`inspected`、限流 10 分鐘 3 筆、換 QR 連坐撤銷）只在住客頁的 Then 以「後端」句型驗證，不另寫後端 feature。 | FR-054 本版不改後端；`plan.md` 落差表第 8 項（住客的後端規則只在後端測試的 feature，本版不補）。 |
| NOOP | `specs/truth/features/frontdesk-web/**` | 已檢查，不改動。住客頁與櫃檯 Web 的隔離寫在 `gw/進入層/與櫃檯Web互不影響.feature`，用的是住客這一側的句型；櫃檯那一側的登入頁與登入狀態行為沿用既有規格。 | FR-055；`spec.md`「對既有行為的影響」不動列。 |
| NOOP | `specs/truth/features/ios-app/**` | 已檢查，不改動。同仁在手機 App 上認領、開始、完成只作為住客頁情境的觸發（以 `BackendChannel` 在後端做），不改員工 App 的規格。 | `spec.md`「本版不做」房務、工務、主管手機 App 的任何改動。 |
