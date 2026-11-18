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

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| （待 `/dsl-refine` 追加） | | | |
