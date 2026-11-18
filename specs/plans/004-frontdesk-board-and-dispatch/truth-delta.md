# Truth Delta: 004-frontdesk-board-and-dispatch

**Plan Package**: `specs/plans/004-frontdesk-board-and-dispatch`
**Truth Root**: `specs/truth`

> 本檔由 truth owner skill（`/api-plan`、`/data-plan`、`/dsl-refine`）透過 `/truth-delta` 追加。
> `/specify` 只建立骨架，不得寫入任何 `specs/truth/**` 的實際改動。

## /technical-research

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `specs/truth/techstack.md` -> `系統有哪些端` | 新增一段，明寫系統三個端：後端 API（`tms/`）、員工 App（`app/mobile/`）、櫃檯 Web（`app/web/`）。 | 技術堆疊真相需要直接寫出系統有哪些端；原本只能從各表推得（research 決策 1）。 |
| ADD | `specs/truth/techstack.md` -> `前端` 櫃檯 Web 框架與建置 | 新增櫃檯 Web 這一端：`React` ＋ `TypeScript` ＋ `Vite` 單頁應用，程式在 `app/web/`，直接呼叫 `tms` 後端、不另起後端；版本在建立專案時鎖定。 | 使用者原話「REACT Web」「後端直接共用目前的 tms」；程式位置照 001 把手機 App 放進本 repo 的先例（research 決策 1、2）。 |
| ADD | `specs/truth/techstack.md` -> `前端` 櫃檯 Web 換頁 | 新增 `React Router`，負責登入、改密碼、總覽看板、待派單四頁的切換。 | research 決策 2。 |
| ADD | `specs/truth/techstack.md` -> `前端` 櫃檯 Web 後端資料 | 新增 `TanStack Query`，負責抓資料、重抓、5 秒輪詢、送出失敗時回復畫面。 | FR-092–095、SC-017 需要的重抓與回復機制（research 決策 2）。 |
| ADD | `specs/truth/techstack.md` -> `前端` 櫃檯 Web 畫面狀態 | 新增 `Zustand`，存登入身分、泳道、篩選、房號搜尋、拖曳提示。 | research 決策 2。 |
| ADD | `specs/truth/techstack.md` -> `前端` 櫃檯 Web 樣式與版面 | 新增 `Tailwind CSS` 與伸縮版面：基準 1180×820，主要環境電腦瀏覽器、同時支援 iPad 橫向，電腦最小 1280×720，滑鼠與觸控都能操作。 | 使用者 2026-11-12 拍板主要使用環境是電腦瀏覽器（research 決策 3）。 |
| ADD | `specs/truth/techstack.md` -> `前端` 櫃檯 Web 多語系 | 新增 `i18next` ＋ `react-i18next`，四種介面語言；語言選擇存 `localStorage`、每人一筆、與登入憑證分開。 | FR-010、NFR-008；使用者拍板語言記在裝置上、每人一筆（research 決策 2、9）。 |
| ADD | `specs/truth/techstack.md` -> `前端` 櫃檯 Web 拖曳派單 | 新增以 Pointer Events 實作的拖曳函式庫（dnd-kit 系列），確切套件由半日 spike 選定。 | FR-087–091 要滑鼠與觸控都能拖；兩個候選套件尚未實跑（research 決策 4）。 |
| ADD | `specs/truth/techstack.md` -> `前端` 櫃檯 Web 登入憑證 | 新增：JWT 存 `sessionStorage`，以 `Authorization: Bearer` 帶出；重新整理不用重登，關掉分頁或瀏覽器要重登。 | 使用者 2026-11-12 拍板（research 決策 9）。 |
| ADD | `specs/truth/techstack.md` -> `前端` 櫃檯 Web 連後端 | 新增：開發時 Vite 代理、正式環境同源部署、API 位址建置時可設定；同源標明為假設。 | 後端沒有跨網域設定，同源時後端不用改；repo 內查不到部署方式（research 決策 7）。 |
| ADD | `specs/truth/techstack.md` -> `前端` 櫃檯 Web 即時更新 | 新增：以 `fetch` 讀取員工事件串流 `GET /staff/stream`，收到訊號就重抓；斷線、心跳逾時或後端宣告降級時每 5 秒輪詢。 | FR-092–095；後端已有事件串流、心跳與降級宣告，櫃檯 Web 是它的客戶端（research 決策 8）。 |
| ADD | `specs/truth/techstack.md` -> `前端` 櫃檯 Web 驗收規格 | 新增櫃檯 Web 的 Gherkin 介面根 `specs/truth/features/frontdesk-web/`。 | 新的端需要自己的行為規格；命名避開之後住客掃碼頁的 Web（research 決策 1）。 |
| ADD | `specs/truth/techstack.md` -> `測試與驗證` 櫃檯 Web BDD techstack | 新增 `Cucumber-JVM` ＋ `Playwright for Java`，獨立 Maven 模組 `app/web-bdd/`，自己的 glue 套件，跑 `frontdesk-web` 的 Gherkin 做 E2E。 | 必問題「櫃檯 Web 的 BDD techstack」，使用者 2026-11-12 拍板；獨立模組是為了不觸發手機測試線 `AppiumWorld` 的 `@Before`（research 決策 5）。 |
| ADD | `specs/truth/techstack.md` -> `測試與驗證` 櫃檯 Web 驗收的視窗與輸入 | 新增兩組瀏覽器設定：1280×720 滑鼠、1180×820 WebKit 觸控；觸控拖曳的自動化待 spike，真機另需人工驗。 | 使用者要求驗收至少涵蓋電腦最小尺寸與 iPad 橫向（research 決策 5）。 |
| ADD | `specs/truth/techstack.md` -> `測試與驗證` BDD 共用的後端資料準備 | 新增 Maven 模組 `app/bdd-support/`，放 `BackendChannel`，由手機 App 與櫃檯 Web 兩條測試線共用。 | 使用者拍板把 `BackendChannel` 抽出來共用；它現在在 `app/bdd/src/test/java` 且位於手機線的 glue 套件內，不搬出來無法被另一個模組引用（research 決策 5）。 |
| ADD | `specs/truth/techstack.md` -> `測試與驗證` 櫃檯 Web 單元測試 | 新增 `Vitest`，只測純計算；驗收仍以 E2E 為主線，不做元件測試。 | 必問題「櫃檯 Web 的測試策略」，使用者 2026-11-12 拍板（research 決策 6）。 |
| ADD | `specs/truth/techstack.md` -> `本次開發不引入的技術` `Next.js` 等需要 Node 伺服器的框架 | 新增排除項。 | 內部工具建置成靜態檔即可（research 決策 2）。 |
| ADD | `specs/truth/techstack.md` -> `本次開發不引入的技術` 以 `tms-web` 的程式當基底 | 新增排除項。 | 它是觀摩用專案：假資料、沒有測試、沒有越南文（research 決策 1、2）。 |
| ADD | `specs/truth/techstack.md` -> `本次開發不引入的技術` Node 的 Web 驗收測試線 | 新增排除項：`Playwright Test` ＋ `playwright-bdd`、`cucumber-js`。 | 被淘汰的替代方案；資料準備要重寫一份（research 決策 5）。 |
| ADD | `specs/truth/techstack.md` -> `本次開發不引入的技術` 以 Appium 驅動 iPad Safari 當 Web 驗收主線 | 新增排除項。 | 被淘汰的替代方案；最慢、最不穩（research 決策 5）。 |
| ADD | `specs/truth/techstack.md` -> `本次開發不引入的技術` 櫃檯 Web 元件測試層 | 新增排除項。 | 使用者拍板不做元件測試（research 決策 6）。 |
| ADD | `specs/truth/techstack.md` -> `本次開發不引入的技術` 瀏覽器原生拖放 | 新增排除項：HTML Drag and Drop API 與建在它上面的函式庫。 | 觸控行為難控制、難自動化，也會卡住之後的 App 外殼（research 決策 4、10）。 |
| ADD | `specs/truth/techstack.md` -> `本次開發不引入的技術` 瀏覽器內建的 `EventSource` | 新增排除項。 | 不能帶 `Authorization` 標頭（research 決策 8）。 |
| ADD | `specs/truth/techstack.md` -> `本次開發不引入的技術` `WebSocket` | 新增排除項。 | 沿用後端既有的事件串流（research 決策 8）。 |
| ADD | `specs/truth/techstack.md` -> `本次開發不引入的技術` cookie、`localStorage`、網址存放登入憑證 | 新增排除項。 | 使用者拍板憑證跟著分頁走；cookie 會卡住之後的 App 外殼（research 決策 9、10）。 |
| ADD | `specs/truth/techstack.md` -> `本次開發不引入的技術` 後端跨網域設定（CORS） | 新增排除項，並標明同源部署是假設、第一次部署前要確認，不成立時需要後端加跨網域設定。 | 同源部署時不需要（research 決策 7）。 |
| ADD | `specs/truth/techstack.md` -> `本次開發不引入的技術` 依賴 Service Worker 的功能 | 新增排除項。 | 避免堵死之後包成 iPad App 的路（research 決策 10）。 |
| ADD | `specs/truth/techstack.md` -> `本次開發不引入的技術` iPad App 外殼 | 新增排除項：PWA、WKWebView、`Capacitor` 的路線未定，目前只交付 Web。 | 使用者拍板這一包不決定（research 決策 10）。 |
| NOOP | `specs/truth/techstack.md` -> `後端` | 已檢查全部 8 列，不需修改。櫃檯 Web 沿用現有的 JWT 認證與事件串流（`即時推送`、`訊息佇列` 兩列的敘述與現況相符）。 | 使用者指定後端共用 `tms`；後端要補的行為屬於契約與行為規格，由 `/api-plan`、`/dsl-refine` 處理。 |
| NOOP | `specs/truth/techstack.md` -> `資料與媒體處理` | 已檢查全部 4 列，不需修改。 | 這一包不新增資料庫或媒體處理技術。 |
| NOOP | `specs/truth/techstack.md` -> `前端` 員工 App 各列、`測試與驗證` 後端與員工 App 各列、`本次開發不引入的技術` 既有 10 項 | 已檢查，不需修改。 | 這一包不動手機 App；後端與手機 App 的 BDD techstack 與測試策略沒有改判。 |

## /api-plan

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `specs/truth/contracts/openapi.yaml` -> tag `櫃檯看板與派單 API`、`groups/現場作業/櫃檯看板與派單.yaml`、`components/schemas/櫃檯看板與派單.yaml` | 新增一個 api group，櫃檯 Web 的讀寫全部放在 `/api/v1/desk/` 底下，只有 `frontdesk`、`manager`、`duty_manager` 可以呼叫。 | 程式現有的 `/desk/*` 不在 `/api/v1` 下、也不在契約裡，呼叫者只有後端自己的測試（`app/mobile/`、`app/bdd/` 都沒用到）。新路徑集中在同一個前綴，權限一條規則就寫得完，也最容易做到「`duty_manager` 只放行櫃檯 Web 用到的介面」。舊的 `/desk/*` 不進契約，留或拆由實作決定。 |
| ADD | `specs/truth/contracts/openapi.yaml` -> `GET /desk/board`（`getDeskBoard`）、schema `DeskBoard`、`DeskMyToday` | 全館看板一次讀取，不分頁、沒有查詢參數：範圍是 `ended_at` 為空加上今天結束的單；另帶飯店樓層 `floors`、本人今日已派單與當場回答張數 `myToday`、後端時間 `serverTime` 與後端認定的今天 `businessDate`。排序由畫面排。待派單頁共用這一份。 | FR-017 至 FR-044、FR-061；落差表 1、7、N8。數量卡、欄頭、徽章都從同一份算才對得起來；張數併在同一次回應，空狀態不用多一次往返；等待時間要用後端時間校正，放在內容裡比 HTTP `Date` 標頭可靠（標頭只到秒，也可能被代理改寫）。 |
| ADD | `specs/truth/contracts/openapi.yaml` -> schema `DeskTask`、`DeskGuestText` | 櫃檯視角的卡片：顯示用單號 `displayNo`（`repair` 讀 `ticket_no`，其餘讀 `task_no`）、十個狀態值、`answered`、部門、類型、來源、房號、樓層、`vip`、住客姓名、標題、`guestTyped`、住客原文與既有翻譯、負責人、各個時刻（`createdAt`、`waitingSince`、`acceptedAt`、`startedAt`、`doneAt`、`interruptedAt`、`endedAt`、`updatedAt`）、`acceptedVia`、`inspection`、SOP 進度、中斷與延後的說明。不帶急件。 | 落差表 2、13；`ui-plan.md`「看板清單」逐項。`waitingSince` 沿用待認領清單與未接提醒的同一把尺。`acceptedVia` 回答「今日排房怎麼判斷」：不能用來源是不是 `system` 猜，改由最近一筆指派或認領事件判定。延後只給「誰延後的」，不帶延後到幾點與主管延後的原因（N11，使用者已定）。 |
| ADD | `specs/truth/contracts/openapi.yaml` -> `GET /desk/tasks/{id}`（`readDeskTask`）、schema `DeskTaskDetail`、`DeskTimelineEntry` | 單筆詳情：卡片資料加上回覆與回答者、住客與入住日期、開單人、已接的來由、取消與工務單結案的說明，以及歷程。歷程略過被復原的派單與復原那一對，也略過 `sop_checked`；句子由畫面依事件種類與 `detail` 組。 | FR-076 至 FR-085；落差表 3。歷程併進詳情，不另開按單查事件的端點：面板本來就要整份一起顯示，少一次往返。既有的 `/task-events` 不分單、沒有實作，櫃檯用不到，維持原狀。不帶住客人數：訂房資料沒有這一欄。 |
| ADD | `specs/truth/contracts/openapi.yaml` -> `GET /desk/quick-replies`（`listDeskQuickReplies`）、schema `DeskQuickReply` | 回飯店住客資訊頁的每一頁（鍵、標題、全文），照設定的順序；沒有設定回空陣列。 | FR-066；落差表 7。住客資訊頁原本只有住客身分讀得到，`/tenant-configs/**` 又只開放 `manager`，所以給櫃檯一個唯讀入口。 |
| ADD | `specs/truth/contracts/openapi.yaml` -> `POST /desk/tasks/{id}/assign`（`deskAssignTask`）、schema `DeskAssignRequest` | 派給房務或工務，帶 `dept` 與 `via`（`button`、`drag`）；成功回派出後的 `DeskTask`。派單與當場回答拆成兩個端點，不沿用程式現況「同一個端點用 `action` 分」的形狀。 | FR-053 至 FR-060、FR-087 至 FR-091；落差表 4、12。兩個動作的請求內容與驗證規則不同，分開後各自的錯誤才說得清楚。 |
| ADD | `specs/truth/contracts/openapi.yaml` -> `POST /desk/tasks/{id}/assign/undo`（`deskUndoAssign`） | 派出後復原，沒有請求內容。後端擋兩件事：距離那筆 `triaged` 事件超過 10 秒就拒絕；派出後這張單沒有再發生任何事（資料層的守衛條件）。 | FR-054、FR-055、NFR-004；落差表 4。資料層不擋時間，所以由這裡擋：使用者能復原的時間是 5 秒（畫面上的復原按鈕只出現 5 秒）；後端多留 5 秒只是給網路延遲與重送用，所以後端的上限是 10 秒，避免很久之後還把單拉回來。 |
| ADD | `specs/truth/contracts/openapi.yaml` -> `POST /desk/tasks/{id}/answer`（`deskAnswerTask`）、schema `DeskAnswerRequest` | 當場回答並結案，回覆去掉頭尾空白後 1–120 字；成功回結案後的 `DeskTask`。沒有復原。 | FR-067 至 FR-075；落差表 6。 |
| ADD | `specs/truth/contracts/components/schemas/shared.yaml` -> `Error` 的 code `task_not_in_triage` | 409。派單或當場回答送到時單已不是待派單；`context` 帶 `resolution`（`assigned`、`answered`、`guest_withdrew`、`other`）、`state`、`roomNo`，被派走或被回答時另帶 `actorName`，被派走時再帶 `dept`。 | FR-059、FR-075；落差表 5。沿用 `StateConflictException` 帶代碼與附帶資訊的既有機制，畫面才說得出「誰把哪一間派給哪個部門」。 |
| ADD | `specs/truth/contracts/components/schemas/shared.yaml` -> `Error` 的 code `undo_claimed`、`undo_guest_withdrew`、`undo_expired`、`undo_unavailable` | 409。復原失敗的四種原因各一個 code：已有負責人（帶 `assigneeName`、`dept`、`roomNo`）、住客已收回、超過 10 秒、其他。判斷順序是先看單現在的樣子、再看時間。 | FR-055、NFR-004：兩種提示要分得出來，逾時與其他也要有去處。 |
| ADD | `specs/truth/contracts/components/schemas/shared.yaml` -> `Error` 的 code `answer_invalid`、`idempotency_key_required` | `answer_invalid`（422）：回覆是空的或超過 120 字。`idempotency_key_required`（400）：櫃檯的三個寫入沒有帶 `Idempotency-Key`。 | FR-067、FR-068；落差表 N3。三個寫入是新端點、沒有既有呼叫者，強制帶不會影響任何人；其他端點「有帶才生效」的現況不動。 |
| ADD | `specs/truth/contracts/openapi.yaml` -> `GET /staff/stream`（`staffStream`）、tag `員工事件串流 API`、`groups/現場作業/員工事件串流.yaml`、schema `StaffStreamInit`、`StaffStreamResync`、`StaffStreamTask`、`StaffStreamHeartbeat`、`StaffStreamDegraded`、`StaffStreamRecovered` | 把既有的事件串流寫進契約，並定下：`frontdesk`、`manager`、`duty_manager` 收全館，`housekeeping`、`engineering` 維持同部門或自己名下；`triage_undone` 另外送給被收回的部門；`resync.refetch` 對全館角色是 `/api/v1/desk/board`；新增 `recovered` 事件、`heartbeat.degraded`、降級期間新連線補送 `degraded`、降級原因多一個 `kafka_unavailable`。 | FR-092 至 FR-095、NFR-005；落差表 10、N4、N5。收件範圍依角色定，不用連線時聲明用途：手機 App 不是這條串流的客戶端，沒有相容問題，也少一個參數。恢復訊號用事件加心跳兩條路，中途連上或漏接的客戶端也對得齊。 |
| MODIFY | `specs/truth/contracts/groups/營運管理/員工帳號與登入.yaml` -> `staffAccountsLogin`、schema `LoginRequest.client` | 請求多一個選填的 `client`（值 `frontdesk_web`）。帶了才檢查角色：`housekeeping`、`engineering`、`owner` 密碼正確且不是臨時密碼時回與密碼錯誤完全相同的 401 `credentials_invalid`，連續錯誤次數不增加也不清零；是臨時密碼時照常登入成功並回 `mustChangePassword=true` 與角色。不帶時行為不變。 | FR-005、FR-006；落差表 N1、N6。使用者已定「登入本身要成功並回角色與必須改密碼」。由後端擋，是因為只靠畫面時回應本身就說出密碼是對的。櫃檯 Web 用這一個登入端點；`/auth/login` 不在契約，櫃檯不用。手機 App 不帶 `client`，不受影響。次數不增加的理由：`client` 只是讓櫃檯 Web 擋錯角色的便利機制，不是安全邊界（同一組帳密不帶 `client` 就登得進去），不值得為了看不出來而鎖住同仁在手機 App 的帳號。 |
| ADD | `specs/truth/contracts/components/schemas/員工帳號與登入.yaml` -> `LoginResponse.loginId`、`LoginResponse.hotelName` | 登入與改密碼的回應多帶員工編號與飯店名稱，兩個都是必回。 | FR-011、FR-012、FR-096；落差表 9、N7。只加欄位，手機 App 不讀就沒有影響。 |
| ADD | `specs/truth/contracts/openapi.yaml` -> `GET /public/hotels/{hotelCode}`（`readPublicHotel`）、parameter `HotelCode`、schema `PublicHotel` | 新增不用登入就能呼叫的端點：以飯店識別代碼查詢，只回飯店名稱（`tenants.name`）一項；查不到或格式不對回 404。識別代碼是飯店主檔的公開代碼 `tenants.code`（例如 `athena`），不是內部編號 `tenants.id`；參數的長度（3–32）與格式限制和資料模型一致。 | FR-001：登入頁要顯示飯店名稱，但登入前沒有身分。登入頁以網址上的飯店識別代碼查名稱；代碼怎麼放進網址屬於前端實作，契約不規定。登入後以登入回應的 `hotelName` 為準。內部編號是流水號，可以被逐一猜出，拿來查等於不登入就能列出所有飯店名稱，所以用 `/data-plan` 在飯店主檔新增的公開代碼。 |
| MODIFY | `specs/truth/contracts/components/schemas/員工帳號與登入.yaml` -> `staff_accounts.role`、`staff_accountsRequest.role`、`LoginResponse.role` | 角色的可用值寫明含 `duty_manager`。 | 落差表 11。資料庫本來就允許這個值，是把契約寫成與資料一致。`duty_manager` 能用的介面只有登入、改密碼、櫃檯看板與派單 API、員工事件串流（使用者已定）。 |
| MODIFY | `specs/truth/contracts/groups/營運管理/員工帳號與登入.yaml` -> `changeOwnPassword`、schema `ChangePasswordRequest.newPassword`、`Error` 的 code `new_password_too_weak` | 新密碼必須至少 8 碼、含英文字母、含數字，不符合回 422 `new_password_too_weak`（`context.failedRules`）；拿掉「強度規則尚未定義」的說明。規則對所有呼叫者適用。 | FR-016；落差表 9。使用者已定這三條規則。規則跟著帳號走：同一組密碼兩個端都用，只擋櫃檯 Web 沒有意義。手機 App 的改密碼畫面本來就先檢查同一組規則（`app/mobile/lib/features/login/password_rules.dart`），既有密碼不受影響。 |
| ADD | `specs/truth/contracts/components/schemas/住客掃碼.yaml` -> `GuestRequest.answer` | 住客視角的需求多一個可為空的 `answer`，帶櫃檯當場回答的回覆全文。 | FR-073；落差表 6、N10。使用者已定：住客進度頁只做到後端回得出回覆。 |
| MODIFY | `specs/truth/contracts/groups/住客與房間/住客掃碼.yaml` -> `GET /guest-requests`（`listGuestRequests`） | 操作說明補一句：櫃檯當場回答的需求，回應的 `answer` 帶回覆全文。路徑、參數、既有回應欄位都沒動。 | FR-073。讓讀這個端點的人知道回覆從哪裡讀；行為的改變就是上一列多回的那個欄位。 |
| ADD | `specs/truth/contracts/components/schemas/任務.yaml` -> `tasks.taskNo`、`tasks.answer`、`tasks.endedAt` | 任務本體多三個可為空的欄位，對應資料模型新增的 `task_no`、`answer`、`ended_at`。 | 契約的任務本體要與資料模型一致。只加欄位，手機 App 用的 `TaskDetail` 既有欄位都沒動。 |
| MODIFY | `specs/truth/contracts/components/schemas/任務.yaml` -> `tasks.note`、`tasksRequest.note` | 欄位說明拿掉「當場回答」，寫明建單後不因派單、復原或當場回答而改寫。 | `/data-plan` 已把回覆移到 `tasks.answer`；只改說明，形狀不變。 |
| MODIFY | `specs/truth/contracts/components/schemas/任務事件流水帳.yaml` -> `task_events.type`、`task_events.actorRole`、`task_events.payload` | 說明改成 25 個事件值（含 `triage_undone`）、操作者角色含 `duty_manager`、`triaged` 與 `triage_undone`、`answered` 各帶什麼。 | 與 `/data-plan` 更新後的資料模型對齊；只改說明，形狀不變。 |
| DELETE | `specs/truth/contracts/openapi.yaml` -> `POST /tasks/{id}/triage`（`tasksTriage`）、`groups/現場作業/任務.yaml` 的同名操作 | 移除這個端點；`任務 API` 的 tag 說明改成指向櫃檯看板與派單 API。它用到的 `tasksRequest`、`tasks` 兩個 schema 還有其他端點在用，保留不動。 | 使用者決定刪除。這條路徑只有骨架、沒有實作；`app/`、`tms/`、`specs/truth/features/` 都查無呼叫者與引用（程式的 `/desk/triage/{taskId}` 是另一條路徑）。櫃檯處理待派單一律用 `deskAssignTask`、`deskUndoAssign`、`deskAnswerTask`，契約與程式路徑不一致的問題（落差表 12）到此只剩一個答案。 |
| NOOP | `specs/truth/contracts/openapi.yaml` -> `GET /tasks`（`listTasks`）、`GET /task-events`（`listTaskEvents`）、`GET /tenant-configs/guest-home`、`POST /alerts`、`GET /reservations`、`GET /rooms` | 已檢查，不修改。 | 櫃檯的清單、歷程、常用回覆都有自己的端點，用不到這幾個；它們有契約、沒有實作的狀況不在 004。 |
| NOOP | `specs/truth/contracts/openapi.yaml` -> `GET /tasks/{id}`（`readTasks`）、`GET /pool`（`listDeptPool`）、`GET /me/today`、`GET /me/manager-queues`、`POST /tasks/{id}/defer`、`POST /tasks/{id}/cancel`、`POST /guest-requests/{id}/cancel`、`PUT /me/push-token` | 已檢查，不修改；`duty_manager` 也不加進這些端點。 | 手機 App 用的端點維持原狀。延後動作不收「延後到幾點」與原因（N11 屬於 005）。住客收回的既有規則足以支撐復原失敗的判斷。手機 App 的主管功能不對 `duty_manager` 放行（使用者已定）。 |
| NOOP | `specs/truth/contracts/components/parameters.yaml` 的既有參數、`specs/truth/contracts/components/responses.yaml` | 已檢查，既有定義不修改（`parameters.yaml` 只多了上面那列的 `HotelCode`）。櫃檯的端點沿用既有的 `TaskId`、`IdempotencyKey` 與各種錯誤回應。 | 既有定義夠用。 |

## /data-plan

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `specs/truth/data/data-model.dbml` -> `Table tasks` Note「櫃檯處理待派單的三種轉移」：派出後復原 `pool`→`triage` | 新增一條狀態轉移：`dept` 回到 `frontdesk`，`reminder_stage` 與 `pool_source` 清成 NULL。守衛條件是 `state='pool'`、`assignee_id IS NULL`、`event_seq` 等於那筆 `triaged` 的 seq。不新增狀態值；寫明重放規則。 | FR-054、FR-055、NFR-004。只比對 `pool` 會把請假退回、班末退回、驗收不通過的單也拉回待派單，所以加上「派出後沒有再發生任何事件」。 |
| ADD | `specs/truth/data/data-model.dbml` -> `task_events.type` 值 `triage_undone` | 事件值清單由 24 值變 25 值；`triage_undone` 只接在 `triaged` 之後，seq 相差 1，payload 帶 `dept`（被收回的部門）。 | 復原是獨立的動作（FR-054），事件只進不改，不能刪掉那筆派單，所以另記一筆。 |
| ADD | `specs/truth/data/data-model.dbml` -> `task_events.payload`：`triaged` 的 `via` 鍵 | `triaged` 的 payload 寫明帶 `dept` 與 `via`（`button`、`drag`）；沒有 `via` 的舊列視為 `button`。`answered` 帶 `answer` 一併寫明。 | FR-056、FR-091 的歷程文字要分按鈕與拖曳。否決新事件值：同一種狀態變化會讓每個讀 `triaged` 的地方多認一個值。 |
| ADD | `specs/truth/data/data-model.dbml` -> `Table task_events` Note：歷程怎麼呈現復原 | 寫明 `triaged` 與緊接的 `triage_undone` 都留在表裡，給人看的歷程把這一對一起略過。 | FR-054、FR-082：畫面的歷程回到派單前的樣子，同時保留誰派出又收回的紀錄。 |
| ADD | `specs/truth/data/data-model.dbml` -> `tasks.answer` | 新增欄位：櫃檯當場回答的回覆全文（1–120 字），只在 `triage`→`done` 時寫入；有值即代表這張單是當場回答結案的。實作須知：新 migration 要替既有的當場回答單回填 `answer`（取 `answered` 事件的 `payload.answer`），「有值＝有 `answered` 事件」才對舊單成立；這些舊單的 `note` 已被回覆蓋掉，原文可從 `created` 事件的建立快照取回。 | FR-070 至 FR-073、FR-077。回覆原本寫進 `tasks.note`，會蓋掉住客原文、語言與翻譯。否決塞進 `note` 的 JSON（`note` 不一定是 JSON，且要讀出再改寫）與只留在事件（清單與住客進度頁每張單都要翻事件）。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `tasks.note` | 欄位說明拿掉「當場回答」，寫明住客打字與點選單時 JSON 裡有哪些鍵（含 `llm_translated`），建單後不因派單、復原或當場回答而改寫。 | 使用者已定：當場回答不得蓋掉住客原文；翻譯沿用 `note` 裡的 `llm_translated` 鍵，不為它加欄位。 |
| ADD | `specs/truth/data/data-model.dbml` -> `tasks.task_no`、索引 `(tenant_id, task_no)` unique | 新增欄位：非工務單的單號，`T-` 加 4 位數，建單時取號、寫入後不改；`type=repair` 為 NULL，工務單的單號仍只有 `repair_tickets.ticket_no`。`created` 事件的建立快照帶 `task_no`。實作須知：新 migration 加欄位與唯一索引時，要替既有的非工務單回填 `task_no`（每租戶依 `created_at`、再依 `id` 由舊到新編號），並把 `task_counters.last_no` 設成該租戶回填到的最後一號；回填後 dbml 的「amenity、cleaning、other 必有值」才成立。 | FR-076、FR-077 詳情面板要顯示單號；格式跟原型（`T-1040`）。否決讓工務單也領 `T-` 單號（一張單兩個號碼）、借用工務單序號、拿 `id` 片段當單號。 |
| ADD | `specs/truth/data/data-model.dbml` -> `Table task_counters` | 新增資料表：每租戶一列的單號序號，供 `tasks.task_no` 取號，與 `repair_ticket_counters` 各自計數。取號用 `INSERT … ON CONFLICT (tenant_id) DO UPDATE … RETURNING` 一句完成，沒有列的飯店第一次取號時自動建列，與工務單取號的既有寫法（`tms/src/main/java/com/athena/tms/repair/RepairService.java:706-714`）相同。 | `task_no` 需要與建單同交易的取號來源；照 `repair_ticket_counters` 的既有做法。 |
| ADD | `specs/truth/data/data-model.dbml` -> `tasks.ended_at`、索引 `(tenant_id, ended_at)` | 新增欄位：這張單走到終點的時間（`inspected`、`cancelled`、`closed`、完成即結束的 `done` 與當場回答），抽查退回時清空。Note 寫明櫃檯看板的範圍＝`ended_at IS NULL` 加上今天結束的單。實作須知：新 migration 要替既有已結束的單回填 `ended_at`（`inspected`、`cancelled`、`closed` 取進入該狀態那筆事件的 `created_at`；`done` 且 `type` 為 `amenity`、`other` 或有 `answered` 事件的取 `done_at`，沒有時取那筆事件的 `created_at`），否則歷史單全部會被 `ended_at IS NULL` 當成未結束、出現在看板上。回填在同一支 migration 內完成，上線後「還沒結束為 NULL」才成立。 | FR-041、FR-076 至 FR-082。查房通過、取消、結案三個時間原本只在事件或 `inspections`。選擇加欄位而不從事件推導：看板每次重抓都要判斷「是不是今天結束」，事件沒有可用索引。三種終點互斥，所以一個欄位而不是三個。 |
| ADD | `specs/truth/data/data-model.dbml` -> `task_events.actor_role` 值 `duty_manager` | 可用值加上 `duty_manager`，並標明是 CHECK（資料庫本來就有這條限制：`tms/src/main/resources/db/migration/V1__schema.sql:256-257` 的 `chk_task_events_actor_role`，是如實描述）；記操作者當下的角色，不借用 `manager`。實作須知：需要一支新 migration 重建這條 CHECK 加入 `duty_manager`（不動 V1），不補的話值班主管派單、復原、當場回答寫事件會被資料庫擋下。 | FR-007、FR-057、FR-100：值班主管在櫃檯畫面派單、復原、當場回答，歷程要記得下來。 |
| ADD | `specs/truth/data/data-model.dbml` -> `Table tasks` Note「櫃檯的今日已派單與當場回答張數」、`task_events` 索引 `(tenant_id, actor_id, created_at)` | 寫明兩個張數一律從事件算：今日已派單＝本人今天的 `triaged` 扣掉緊接著被 `triage_undone` 的；今日當場回答＝本人今天的 `answered`。新增一個索引支撐這條查詢，不新增計數欄位。 | FR-061、FR-054。既有事件就夠；計數欄位要在復原時減回、跨日歸零，會與事件變成兩份數字。 |
| ADD | `specs/truth/data/data-model.dbml` -> `tenants.code`（全系統唯一） | 新增欄位：飯店識別代碼，對外公開、放在發給飯店的網址裡，登入前用它查飯店名稱。必有值、全系統唯一；長度 3–32，小寫英文字母、數字與連字號，第一個字是英文字母，不以連字號結尾、不連用連字號；只存小寫、比對分大小寫；有一份保留字清單；寫入後不改。欄位說明寫明 `id` 只在系統內使用、對外一律用 `code`。實作須知：需要一支新的 migration，不動舊的 migration 檔；既有飯店要回填代碼（代碼由營運逐家指定，不能從飯店名自動產生），順序是先加可為空的欄位、回填、再加上必有值、唯一與格式和保留字的 CHECK；開發種子資料與測試裡建立飯店的地方都要補代碼。 | FR-001：登入頁在登入前要用網址上的代碼查飯店名稱。使用者已定：飯店主檔加一個公開的識別代碼。否決沿用內部編號（流水號可被逐一猜出，不登入就能列出所有飯店名稱）。代碼放在網址的路徑或子網域由前端與部署決定，所以格式取兩邊都能用的寫法。 |
| NOOP | `specs/truth/data/data-model.dbml` -> `Table tenants`、`Table tenant_config` | 已檢查，不新增每家飯店的時區設定；櫃檯看板與張數的「今天」沿用後端注入的 Clock 時區。 | 使用者已定：飯店時區沿用伺服器時區。 |
| NOOP | `specs/truth/data/data-model.dbml` -> `tasks.state`、`tasks.cancel_reason`、`Table staff_accounts`、`rooms`、`reservations_cache`、`repair_tickets`、`inspections`、`task_sop_checks` | 已檢查，不需變更。狀態維持十值，取消原因維持四值；房號、樓層、住客名、VIP、SOP 進度、角色與 `duty_manager` 的資料都已存在。 | 畫面缺的是後端回傳，不是資料；由 `/api-plan` 處理。 |

## /dsl-refine

路徑縮寫：`fw/`＝`specs/truth/features/frontdesk-web/`；`be/`＝`specs/truth/features/backend/`。依據欄引用驗收例子的檔名（省略 `.feature`）、FR 編號、契約的操作名稱或 `ui-plan.md` 的題號。`specs/truth/features/ios-app/**` 不在這一趟範圍，沒有檢查也沒有改動。依賴 `ui-plan.md`「預設，待使用者確認」的 Example 上方都有 `# [need clarification]` 並註明題號。

### 櫃檯 Web

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `fw/dsl.md` -> 共通實作約定、「單的狀況對照」、50 個共用句型 | 新的介面根。定下預設飯店與帳號、時間、兩組瀏覽器設定（電腦 1280 × 720 滑鼠、iPad 橫向 1180 × 820 觸控，整套各跑一次）、畫面與後端都要成立的斷言方式，以及跨模組共用的 Given／When／Then。 | `techstack.md` 已定名介面根與 `Cucumber-JVM` ＋ `Playwright for Java`；FR-103 的滑鼠與觸控以兩組設定各跑一次驗收。 |
| ADD | `fw/帳號與登入/登入頁.feature`（12 條 Rule） | 登入頁的欄位與按鈕、兩欄有內容才能登入、員工編號轉大寫、欄位長度、顯示與隱藏密碼、三種角色進看板、三種失敗共用一句、鎖定與停用各自的提示卡、忘記密碼只給提示、飯店名稱查不到就不顯示。 | `櫃檯人員在共用裝置上登入改密碼與登出` Rule 1 至 3；FR-001 至 FR-009；使用者決定的飯店名稱查法；C-01。 |
| ADD | `fw/帳號與登入/設定新密碼畫面.feature`（12 條 Rule） | 拿臨時密碼先進設定新密碼畫面、不必再輸入臨時密碼、改完前進不了其他頁、四條規則、不能與臨時密碼相同、改完直接進看板、被擋角色改完回登入頁並被告知、之後登入照常被擋、可以登出離開、重新整理仍停在這一頁。 | 同檔 Rule 4；FR-014 至 FR-016；C-02、C-16、C-18。 |
| ADD | `fw/帳號與登入/帳號面板與登出.feature`（8 條 Rule） | 帳號卡與身分卡的文字、面板只有三項、身分卡點了沒反應、完成關閉、登出先確認、取消不登出、登出後不留上一位的資訊。 | 同檔 Rule 5、6；FR-010 至 FR-013、NFR-001。 |
| ADD | `fw/帳號與登入/登入狀態跟著分頁.feature`（9 條 Rule） | 重新整理不用再登入並停在原頁、篩選搜尋泳道保留、面板不保留、側欄選擇保留、關掉分頁或瀏覽器要重新登入、每個分頁各自登入與登出。 | 同檔 Rule 7、8；FR-008；`research.md` 決策 9；C-18。 |
| ADD | `fw/帳號與登入/介面語言.feature`（14 條 Rule） | 沒人選過時跟瀏覽器、登入頁語言選單、每人一筆記在裝置上、沒選過的人沿用登入頁語言、登出後停在上一位的語言、不跟到別台裝置、分頁之間共用、關掉瀏覽器後還在、帳號面板語言頁、只換介面文字不換輸入的內容。 | `介面語言跟著瀏覽器與個人在這台裝置上的選擇` 全部 4 條 Rule；NFR-008。 |
| ADD | `fw/共用外框/側欄與工具列.feature`（7 條 Rule） | 側欄的內容、待派單徽章、兩頁切換時外框不變、側欄各頁的預設、打開與收合、自己選過之後維持。 | `共用外框與尚未開放的入口` Rule 1；FR-038、FR-039、FR-102。 |
| ADD | `fw/共用外框/尚未開放的入口.feature`（3 條 Rule） | 「通知板」「遺失物」「代開單」點了沒反應、只有主管與值班主管有「設定」、「設定」點了不展開。 | 同檔 Rule 4；FR-096、FR-097。 |
| ADD | `fw/共用外框/支援的視窗與輸入方式.feature`（8 條 Rule） | 兩種尺寸下五個畫面都不用縮放與整頁捲動、「還有 N 個狀態」與往右捲一欄、捲到底消失、視窗變寬欄跟著變寬、比支援的小就出現捲軸、只用手指能派單、沒有只靠游標才看得到的東西。 | 同檔 Rule 2、3；FR-098 至 FR-103、NFR-006；`ui-plan.md` 版面尺寸表。 |
| ADD | `fw/總覽看板/看板分欄與範圍.feature`（8 條 Rule） | 8 個欄與版面順序、每種狀況落在哪一欄、當場回答在「已結案」、只顯示今天結束的、還沒結束的不限日期、頁副標未結案張數、空欄文字、欄內順序。 | `總覽看板把全館的單依狀態分欄並算出未結案` Rule 1、2、5；FR-017 至 FR-025、FR-041。 |
| ADD | `fw/總覽看板/數量卡與欄頭.feature`（10 條 Rule） | 四張數量卡、「等最久」的挑選範圍與顏色、點它是開詳情、沒有在等的單時的樣子、欄頭的數量與兩種警示。 | 同檔 Rule 3、4；FR-026 至 FR-028、FR-037。 |
| ADD | `fw/總覽看板/卡片內容.feature`（9 條 Rule） | 部門、類型、來源與 VIP 標、住客打字的標題與翻譯、副標的五種情形、本人暫停與本人延後的副標、SOP 進度、負責人頭像。 | 同檔 Rule 6；FR-030 至 FR-035；C-03、C-04、C-07。 |
| ADD | `fw/總覽看板/等待膠囊與逾時.feature`（9 條 Rule） | 待認領 10 分起逾時、待派單只寫等幾分、已接與進行中不判斷逾時、等待的起算時間、四處用同一個時間、不顯示急件、後四欄的膠囊。 | `等待時間與逾時只判斷待認領` 全部 5 條 Rule；FR-033、FR-034、FR-036。 |
| ADD | `fw/總覽看板/篩選搜尋與泳道.feature`（15 條 Rule） | 數量卡篩選、房號開頭比對、只收數字最多 4 碼、兩種篩選併成一顆膠囊、點膠囊全部清掉、不影響未結案張數、換頁後的保留規則、三種泳道與副標。 | `用數量卡房號與泳道在看板上找到已存在的單` Rule 1、2；FR-029、FR-040。 |
| ADD | `fw/總覽看板/拖曳派單.feature`（14 條 Rule） | 平時與拖曳中的提示、拖放區與說明、放開就派單並註明拖曳、浮條、5 秒內復原、在別處放開沒事、其他欄拖不動、拖放區跟著泳道、用手指拖、撞單、送出失敗。 | `在看板拖曳待派單卡片完成派單` 全部 4 條 Rule；`同一張單同時被處理時先送出的為準` Rule 1、3 的拖曳例子；FR-087 至 FR-091；C-11。 |
| ADD | `fw/總覽看板/載入與讀取失敗.feature`（4 條 Rule） | 載入中是骨架、讀取失敗的訊息與「重試」、每 5 秒自動再試。 | `總覽看板…` Rule 7；FR-042；C-08、C-09、C-10。 |
| ADD | `fw/待派單/待派單清單.feature`（16 條 Rule） | 清單順序、頁副標、頁首與頁尾、每一列的內容、不到 1 分、翻譯與原文列、VIP 與空房、只有三顆動作、沒有建議文字、點列開詳情、「當場回答…」開面板、直接進部門的需求不會來。 | `在待派單頁派給房務或工務並在五秒內復原` Rule 1、5；FR-043 至 FR-052、FR-058。 |
| ADD | `fw/待派單/按鈕派單與復原.feature`（12 條 Rule） | 點部門就送出、浮條、徽章、操作者是登入的人（三種角色）、不拆單、5 秒內復原回原位、超過 5 秒沒有復原、浮條換成新的那一張、復原失敗兩種、復原沒送成功。 | 同檔 Rule 2、3、4；`櫃檯人員在共用裝置上…` Rule 1 的主管與值班主管；FR-053 至 FR-057；C-15；`ui-plan.md` 待決事項 2。 |
| ADD | `fw/待派單/空狀態與載入.feature`（8 條 Rule） | 兩種空狀態與今日張數、被復原的不算、頁副標、空的時候沒有頁首頁尾、載入中與讀取失敗不顯示成沒有單。 | 同檔 Rule 6；`總覽看板…` Rule 7；FR-061、FR-042；C-08 至 C-10。 |
| ADD | `fw/待派單/撞單與送出失敗.feature`（6 條 Rule） | 被另一台派走、被另一台回答、住客收回三種撞單的對話框與畫面更新；送出失敗時列留在原位、徽章不變。 | `同一張單同時被處理時先送出的為準` 全部 3 條 Rule；FR-059、FR-060；C-11、C-13、C-14。 |
| ADD | `fw/當場回答/當場回答面板.feature`（13 條 Rule） | 面板的內容、常用回覆帶入與選中的樣子、空白不能送、最多 120 字、送出後結案並從清單消失、去掉前後空白、沒有復原、看板上的位置與副標、取消不變。 | `資訊類問題當場回答住客並結案` Rule 1、2、3；FR-062 至 FR-074。 |
| ADD | `fw/當場回答/撞單與送出失敗.feature`（6 條 Rule） | 當場回答送出時的三種撞單、按「好」後面板關閉、送出失敗時單不變、面板與已填的回覆都留著。 | `同一張單同時被處理…` Rule 2、3；FR-075；C-11 至 C-14。 |
| ADD | `fw/單筆詳情/詳情面板內容.feature`（16 條 Rule） | 面板滑出與側欄收起、任何欄的卡片都能點開、版面順序、單號、「內容」六列、不顯示人數、建立時間寫法、負責人與空房、翻譯與原文列、清潔 SOP、來源列、沒有急件標記、關閉、讓位給側欄。 | `住客來電時打開單筆詳情照現況回答` Rule 1、3；`用數量卡…` Rule 3；FR-076 至 FR-078、FR-085；使用者決定的單號規則；C-07。 |
| ADD | `fw/單筆詳情/狀態標籤與狀態訊息卡.feature`（11 條 Rule） | 十二種已定的標籤與訊息卡、待認領、已接的四種說明、待派單沒有訊息卡、本人暫停、本人延後、另外兩種取消、免查房，以及面板開著時認領與完成的即時更新。 | 同檔 Rule 1、2；FR-079 至 FR-081、FR-084；C-03 至 C-06。 |
| ADD | `fw/單筆詳情/歷程.feature`（9 條 Rule） | 歷程的標題與每筆的樣子、按鈕與拖曳派單的句子、當場回答、住客收回、其他已定案的句子、被復原的派單不出現、只增不改、暫定句子、不是今天的寫日期。 | 同檔 Rule 3；FR-054、FR-056、FR-071、FR-082、FR-091；`ui-plan.md` 待決事項 1 與「找不到出處的文字」。沒有句子的事件種類不寫行為。 |
| ADD | `fw/單筆詳情/派單區與單據管理動作列.feature`（8 條 Rule） | 待派單的派單區三格、從面板派單與之後的樣子、「當場回答…」開面板、其他狀態沒有派單區、動作列依狀態與角色、點了沒反應、主管取消與延後櫃檯只看得到。 | 同檔 Rule 4、5；`共用外框…` Rule 5；`資訊類問題…` Rule 4；FR-083、FR-086。 |
| ADD | `fw/即時更新/現場變化即時跟上.feature`（8 條 Rule） | 住客送單、另一台派單、同仁認領開始完成、主管查房通過、住客收回時，看板、徽章、數量卡與待派單清單在 5 秒內跟上。 | `現場變化即時跟上與連線異常提醒` Rule 1；FR-092；C-17（上限照 `ui-plan.md` 建議的 5 秒）。 |
| ADD | `fw/即時更新/連線異常提醒列.feature`（7 條 Rule） | 三種情形出現提醒列、沒有按鈕、期間每 5 秒更新且照常可以派單、恢復後自己消失並回到即時、重讀失敗留著舊資料。 | 同檔 Rule 2；FR-093 至 FR-095；`staffStream` 契約；C-09。 |
| ADD | `fw/{帳號與登入、共用外框、總覽看板、待派單、當場回答、單筆詳情、即時更新}/dsl.md` -> 模組專屬句型共 252 個 | 七個模組各自的 Given／When／Then 與實作語意。 | 每個 step 合併介面根與同模組 DSL 後恰好命中一個句型。 |

### 後端

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| MODIFY | `be/dsl.md` -> 狀態用語對照、部門用語對照 | 新增「待派單＝`triage`」。「值班經理」改稱「值班主管」，權威值仍是 role `duty_manager`；新增「老闆＝role `owner`」。 | 使用者已定 `duty_manager` 的中文是「值班主管」；被擋角色的案例需要老闆帳號。只改用語，權威值不變。 |
| MODIFY | `be/dsl.md` -> Given `飯店有以下員工：` | `部門` 的可用值改成含值班主管與老闆，並寫明櫃檯、值班主管、老闆各自寫入的 role。句型與其他欄位不變。 | 同上。 |
| MODIFY | `be/推播/員工推播事件.feature` -> 背景員工表 | 「李經理」的部門由「值班經理」改寫成「值班主管」。行為與驗證都沒有變。 | 跟著上一列的用語一次改完。 |
| ADD | `be/dsl.md` -> Given `待派單有以下住客打字的需求：`、`"{櫃檯}" 已把 "{任務}" 派給 "{部門}"`；When `"{員工}" 讀取櫃檯看板`、`"{櫃檯}" 把 "{任務}" 派給 "{部門}"`、`"{櫃檯}" 復原 "{任務}" 的派單` | 五個跨模組共用句型（櫃檯看板與派單、員工事件串流、帳號與登入都用到）。 | `getDeskBoard`、`deskAssignTask`、`deskUndoAssign` 契約。 |
| MODIFY | `be/dsl.md` -> Then `這次操作被拒絕，錯誤碼為 "{錯誤碼}"` | 舉例多列 `idempotency_key_required`、`answer_invalid`。契約不變。 | 櫃檯三個寫入的新錯誤碼。 |
| ADD | `be/櫃檯看板與派單/櫃檯看板讀取.feature`（17 條 Rule） | 看板範圍（還沒結束的加今天結束的）、當場回答的標示、單號、等待起算的三種情形、已接的來由、不帶急件、住客原文與既有翻譯、住客與 VIP、後端時間、樓層、本人今日張數、打字需求命中與不命中規則的去向、系統不自己派單。 | `總覽看板…` Rule 1、2；`等待時間…` Rule 3、4；`在待派單頁…` Rule 1、5、6；`getDeskBoard`、`DeskTask`、`DeskMyToday`。 |
| ADD | `be/櫃檯看板與派單/櫃檯介面的角色權限.feature`（7 條 Rule） | 三種角色讀得到看板；房務、工務、老闆讀不到也不能派單與回答；主管與值班主管的派單記本人與當下角色；值班主管可以復原與當場回答。 | `櫃檯人員在共用裝置上…` Rule 1；FR-007、FR-057、FR-100；`actor_role` 加 `duty_manager`。 |
| ADD | `be/櫃檯看板與派單/櫃檯派單.feature`（11 條 Rule） | 派出後進部門待認領、歷程記部門與方式、拖曳與按鈕結果相同、不拆單、只能派房務或工務、三種撞單的拒絕與說明、被拒的不改單、必須帶防重複鍵、重送不會派兩次。 | `在待派單頁…` Rule 2、5；`在看板拖曳…` Rule 1；`同一張單同時被處理…` Rule 1、2；`deskAssignTask`、`task_not_in_triage`。 |
| ADD | `be/櫃檯看板與派單/派出後復原.feature`（10 條 Rule） | 復原後回到還沒分部門、給人看的歷程回到派單前、事件各留一筆、10 秒內接受、超過拒絕、已被認領、已被住客收回、不是剛派出的不能復原、當場回答沒有復原、必須帶防重複鍵。 | `在待派單頁…` Rule 3、4；FR-054、FR-055、NFR-004；`deskUndoAssign` 與四個 `undo_*` 錯誤碼。 |
| ADD | `be/櫃檯看板與派單/當場回答並結案.feature`（14 條 Rule） | 結案與回覆落地（`tasks.answer`）、狀態仍是既有的完成、不蓋掉住客原文、不算部門做掉的單、歷程、住客進度頁讀得到、去掉前後空白、120 字可以、空的與超過被拒、三種撞單、只有待派單可以回答、必須帶防重複鍵。 | `資訊類問題…` 全部 4 條 Rule；`同一張單同時被處理…` Rule 2；FR-067 至 FR-075；`deskAnswerTask`、`GuestRequest.answer`。 |
| ADD | `be/櫃檯看板與派單/單筆詳情與歷程.feature`（11 條 Rule） | 詳情帶住客與日期、空房、開單人、回覆與回答者、已接的來由、取消的說明、工務單的完修結果與驗收主管；歷程由舊到新、只增不改、不含逐項勾選；找不到單。 | `住客來電時…` Rule 1 至 3；FR-076 至 FR-082；`readDeskTask`、`DeskTaskDetail`、`DeskTimelineEntry`。 |
| ADD | `be/櫃檯看板與派單/常用回覆.feature`（3 條 Rule） | 回住客資訊頁的每一頁與順序、沒有設定時是空的、其他角色讀不到。 | `資訊類問題…` Rule 1；FR-066；`listDeskQuickReplies`。 |
| ADD | `be/櫃檯看板與派單/dsl.md` -> 用語對照與 68 個句型 | 新模組的專屬句型。新增模組的理由：這是櫃檯視角的讀寫，既有的 `待認領`（部門同仁視角）、`任務執行`（負責人視角）、`主管工作台` 都承接不了待派單與看板。 | 契約 tag `櫃檯看板與派單 API`。 |
| ADD | `be/員工事件串流/收得到哪些單的訊號.feature`（6 條 Rule）、`降級與恢復訊號.feature`（5 條 Rule）、`dsl.md`（15 個句型） | 三種角色收全館、房務與工務的範圍不變、派單訊號的收件範圍、復原訊號送給被收回的部門、連上後的起始與重抓通知、老闆不能連線；兩種降級原因、恢復訊號、降級期間新連線補送、心跳帶降級旗標。新增模組的理由：串流原本沒有任何行為規格。 | `現場變化即時跟上與連線異常提醒` 全部 2 條 Rule 的後端責任；FR-092 至 FR-095；`staffStream` 契約。 |
| ADD | `be/帳號與登入/櫃檯畫面登入只放行三種角色.feature`（8 條 Rule） | 帶 `client`=`frontdesk_web` 的登入：三種角色成功；房務、工務、老闆密碼正確也回與密碼錯誤相同的回應；錯誤次數不增加也不清零；不帶 `client` 不受影響；臨時密碼先成功並要求改密；鎖定與停用的回應各自不同。 | `櫃檯人員在共用裝置上…` Rule 1、2、4；FR-005、FR-006；使用者已定；`staffAccountsLogin`。 |
| ADD | `be/帳號與登入/設定新密碼.feature`（7 條 Rule） | 不必帶臨時密碼、改完直接拿到憑證、臨時密碼失效、三條強度規則、不能與目前密碼相同、沒改完讀不到看板、被擋角色改完新密碼生效並回角色、之後照常被擋。 | 同檔 Rule 4；FR-014 至 FR-016；`changeOwnPassword`、`new_password_too_weak`。既有 truth 沒有任何改密碼的後端行為，所以是新增。 |
| ADD | `be/帳號與登入/登入前查飯店名稱.feature`（3 條 Rule） | 不用登入查得到名稱且只有名稱、對不到一律查不到、拿內部編號查不到。 | FR-001；使用者決定；`readPublicHotel`。 |
| ADD | `be/帳號與登入/員工登入.feature` -> Rule「登入成功的回應帶員工編號與飯店名稱，不必另外查」 | 既有 6 條 Rule 不動，多一條。 | `LoginResponse.loginId`、`hotelName`。 |
| ADD | `be/主管工作台/主管待辦空清單與權限.feature` -> Example「值班主管想查看主管待辦」 | 既有 Rule「主管待辦只開放主管角色查看」多一個例子；背景多一位值班主管。既有例子不動。 | 使用者已定：`duty_manager` 只放行櫃檯 Web 用到的介面，手機 App 的主管功能不對它放行（`/api-plan` 的 NOOP 列）。 |
| MODIFY | `be/帳號與登入/dsl.md` -> 模組說明、既有 3 個 Given、1 個 When、5 個 Then 的實作語意 | 拿掉「已知的 truth 領先現況」一段與各列的「待 `/api-plan`」字樣，改寫成契約現在的樣子：`LoginRequest`、`LoginResponse`、401 `credentials_invalid`、429 `account_locked`、403 `account_disabled`、`mustChangePassword`。句型文字與驗證的業務事實都沒有變，驗證只有變得更明確。 | 那些落差契約都已補齊（003 與這一包的 `/api-plan`）；不屬於削弱驗證。 |
| ADD | `be/帳號與登入/dsl.md` -> 3 個 Given、4 個 When、12 個 Then | 櫃檯畫面登入、設定新密碼、查飯店名稱用的句型。 | 上面三份新 feature。 |
| NOOP | `be/待認領/**`、`be/任務執行/**`、`be/工務單/**`、`be/查房/**`、`be/推播/dsl.md` 與其餘模組 | 已檢查，不修改。既有 feature 沒有任何一處描述 `/desk/triage`、`/tasks/{id}/triage`、當場回答寫 `note` 或改密碼，所以沒有要對齊的舊行為。`be/待認領` 背景資料裡的來源值「櫃檯轉單」維持原樣。 | 以 `/desk/triage`、`triage`、`當場回答`、`改密`、`新密碼` 全文搜尋 `be/` 的結果。 |

### 櫃檯 Web（2026-11-18 實作期修正，依使用者裁決）

裁決全文見 `implement-notes.md`「使用者 2026-11-18 裁決」。

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| MODIFY | `fw/當場回答/當場回答面板.feature` -> Rule「當場回答沒有復原，送出後畫面上沒有浮條也沒有「復原」按鈕」 | Rule 標題補「按鈕」，加一行說明只看派單浮條與「復原」按鈕、頁首說明的字樣不算；拿掉 Then `畫面上沒有 "復原"`（整頁文字比對會抓到待派單頁首說明）。「沒有復原按鈕」改由同場景的 `畫面上沒有派單浮條` 承接，它的 DSL 本來就驗「也沒有「復原」可以按」。意思不變。 | 使用者裁決：只把範圍講清楚。原句與 `待派單清單.feature:49` 的頁首說明衝突（T506）。 |
| MODIFY | `fw/即時更新/現場變化即時跟上.feature` -> 背景 208 列、Rule「同仁認領、開始、完成時，卡片跟著移到對應的欄」的場景大綱 | 208 的負責人由小美改成阿美；場景大綱多一欄 `同仁`，「開始 208」由阿美操作，認領 412、完成 510 仍是小美。 | 使用者裁決。後端規定同一人同時只能有一張進行中（`TaskExecutionService.java:144-161`），原資料讓小美在 510 進行中時開始 208，做不到（T520）。 |
| MODIFY | `fw/共用外框/dsl.md` -> Then `側欄、工具列、數量卡與看板的排法與視窗調整前相同`；When `"{員工}" 把視窗調成 {寬} × {高}` 的回寫 | 必查由「相對位置與尺寸相同」改成「排法、上下位置與高度相同；寬度只會縮窄，縮到外框最小寬度 1180 為止」。When 多回寫各區塊的高度與寬度。feature 句子沒有寫「尺寸」，不用改。 | 使用者裁決，ui-plan 不動。原語意與 `ui-plan.md:29` 外框最小 1180 算不攏（T524）。 |
| MODIFY | `fw/即時更新/連線異常提醒列.feature` -> Rule「提醒列出現期間，畫面改成每 5 秒自動更新一次」；`fw/即時更新/dsl.md` -> 模組說明 | Rule 補說明「畫面上說每 5 秒，實際間隔略短，確保 5 秒內跟上」；模組說明改成「定時重讀，實際間隔略短（目前 4 秒）」。驗證不變。 | 使用者裁決：維持 4 秒（T522）。 |
| MODIFY | `fw/即時更新/連線異常提醒列.feature` -> Rule「即時連線中斷而且重連失敗時，工具列下方出現提醒列…」；`fw/即時更新/dsl.md` -> When `即時連線中斷`、模組說明 | Rule 標題與場景名稱補「重連失敗」；When 的實作語意寫明擋住之後每一次重連、第一次重連一定失敗；模組說明的出現條件改成「斷線後重連失敗、太久沒訊號、後端宣告降級、已有資料之後重讀失敗」。句型文字與測試做法不變。 | 使用者裁決：提醒列改成重連失敗一次才顯示。 |
| ADD | `fw/即時更新/連線異常提醒列.feature` -> Rule「即時連線中斷後第一次重連就成功時，提醒列完全不出現」；`fw/即時更新/dsl.md` -> When `即時連線中斷，第一次重連就成功`、Then `這段期間提醒列一次也沒有出現` | 新規則一個場景、兩個新句型。stepdef 尚未實作。 | 使用者裁決的新行為；取代原本斷線就閃約 15 秒的提醒列。 |
| NOOP | `fw/共用外框/支援的視窗與輸入方式.feature:82`、`fw/dsl.md`、`ui/ui-plan.md` | 已檢查，不修改。feature 句子沒有寫「尺寸相同」；介面根 `畫面上沒有 "{文字}"` 維持整頁比對；ui-plan 由另一個工作處理。 | 範圍限定在裁決的五件。 |
| MODIFY | `fw/帳號與登入/登入頁.feature` -> Rule「員工編號輸入的當下自動轉成大寫，拿去登入的也是大寫」拆成兩條：「員工編號欄照打的樣子顯示，不轉大小寫」「員工編號照輸入的樣子拿去登入，不轉大小寫」 | 打 `f0101` 欄裡顯示 `f0101`（原本是 `F0101`）；登入場景改成「員工編號是小寫的帳號，打小寫就能登入」，先把林佩君的員工編號設成 `f0101`。原本「打小寫的 f0101 登入 F0101 的帳號會成功」的行為不再成立。 | 使用者 2026-11-18 裁決：員工編號可以有大小寫，跟員工 App 一樣。App 照原樣送（`app/mobile/lib/features/login/login_screen.dart:69-71`、`:218` `uppercase: false`），後端精確比對（`StaffAccountRepository.findByTenantIdAndLoginId`）。小寫帳號 `mg1` 原本在櫃檯 Web 登不進去。 |
| MODIFY | `fw/帳號與登入/dsl.md` -> When `"{員工}" 在登入頁輸入員工編號 "{員工編號}" 與密碼 "{密碼}" 後按「登入」`；Then `"{員工}" 登入成功，畫面是 "{頁}"`、`登入頁顯示紅字 "{文字}"，密碼欄被清空，員工編號欄保留` | When 寫明不轉大小寫、不去空白；兩個 Then 的必查由「送出的 `loginId` 是大寫」改成「送出的 `loginId` 與輸入照字面相同」。句型文字不變。 | 同上；去不去前後空白也跟 App 一致（App 不去）。 |
| ADD | `fw/帳號與登入/dsl.md` -> Given `"{員工}" 的員工編號是 "{員工編號}"` | 在後端把該帳號的員工編號改成指定值，之後的帳號卡與登入憑證以它為準。 | 新場景要一個小寫員工編號的帳號；預設帳號全是大寫。 |
| NOOP | `fw/帳號與登入/` 其餘 feature、`fw/dsl.md`、`be/帳號與登入/**` | 已檢查，不修改。其餘檔案沒有寫轉大寫；後端本來就精確比對，不用改。 | 以「大寫」「小寫」「uppercase」全文搜尋 `fw/`。驗收例子 `features/acceptance/櫃檯人員在共用裝置上登入改密碼與登出.feature:70-71、:87` 與 `ui/ui-plan.md:70、:465` 仍寫轉大寫，不屬 `/dsl-refine`，另外處理。 |
| MODIFY | `fw/dsl.md` -> Given `全館有以下的單：` 的 `時間` 參數；`fw/總覽看板/dsl.md` -> Then `各張卡片的等待膠囊如下：` | 進行中的計時起點由「開始」寫明為「第一次開始」：中途中斷、停下的時間也算在內，不是累計工時，和員工 App 的計時不同。Then 的必查補「進行中的『做 N 分』用 `startedAt`」。只是說明，句型、語意、驗證不變，不加場景。 | 使用者 2026-11-18 裁決：「做 N 分」維持現行規格。手動環境 308 看板「做 110 分」、App 15:33 引發疑問，兩邊都沒算錯（看板 `app/web/src/lib/wait-pill.ts:105-108`、`DeskTaskMapper.java:56`；App 累計工時 `task_screen.dart:1510-1520`）。不需 `/clarify`。 |
| NOOP | `fw/總覽看板/等待膠囊與逾時.feature`、其餘寫到「進行中」的 feature、`be/**` | 已檢查，不修改。現有場景的進行中單都沒有中斷過，起點寫「第一次開始」或「開始」結果相同；後端 `startedAt` 本來就是第一次開始（`TaskEngine.start` 用 `COALESCE(started_at, ?)`）。 | 只是說明，不需新場景。 |

### 驗收例子承接對照

每一列是 `features/acceptance/` 的一條 Rule（依檔內順序編號），右邊是承接它的介面 feature。56 條都有承接，沒有被漏掉的。

| 驗收例子 | Rule | 櫃檯 Web | 後端 |
| --- | --- | --- | --- |
| 櫃檯人員在共用裝置上登入改密碼與登出 | 1 只有三種角色進得來 | `帳號與登入/登入頁`、`待派單/按鈕派單與復原`、`共用外框/尚未開放的入口` | `帳號與登入/櫃檯畫面登入只放行三種角色`、`櫃檯看板與派單/櫃檯介面的角色權限` |
| 同上 | 2 鎖定與停用 | `帳號與登入/登入頁` | `帳號與登入/櫃檯畫面登入只放行三種角色`、`員工登入` |
| 同上 | 3 登入頁的欄位與忘記密碼 | `帳號與登入/登入頁` | — |
| 同上 | 4 臨時密碼先改密碼 | `帳號與登入/設定新密碼畫面` | `帳號與登入/設定新密碼`、`櫃檯畫面登入只放行三種角色` |
| 同上 | 5 帳號卡與帳號面板 | `帳號與登入/帳號面板與登出` | `帳號與登入/員工登入`（回應帶員工編號與飯店名稱） |
| 同上 | 6 登出 | `帳號與登入/帳號面板與登出` | — |
| 同上 | 7 重新整理與關掉分頁 | `帳號與登入/登入狀態跟著分頁`、`介面語言` | — |
| 同上 | 8 每個分頁各自登入 | `帳號與登入/登入狀態跟著分頁`、`介面語言` | — |
| 介面語言跟著瀏覽器與個人在這台裝置上的選擇 | 1 至 4 | `帳號與登入/介面語言` | — |
| 住客來電時打開單筆詳情照現況回答 | 1 點卡片滑出詳情、現場有變化自己更新 | `單筆詳情/詳情面板內容`、`狀態標籤與狀態訊息卡`、`歷程` | `櫃檯看板與派單/單筆詳情與歷程` |
| 同上 | 2 狀態標籤與狀態訊息卡 | `單筆詳情/狀態標籤與狀態訊息卡` | `櫃檯看板與派單/單筆詳情與歷程`、`櫃檯看板讀取` |
| 同上 | 3 內容、原文與歷程 | `單筆詳情/詳情面板內容`、`歷程` | `櫃檯看板與派單/單筆詳情與歷程` |
| 同上 | 4 待派單的派單區 | `單筆詳情/派單區與單據管理動作列` | `櫃檯看板與派單/櫃檯派單` |
| 同上 | 5 單據管理動作列 | `單筆詳情/派單區與單據管理動作列` | — |
| 共用外框與尚未開放的入口 | 1 同一個外框 | `共用外框/側欄與工具列` | — |
| 同上 | 2 兩種尺寸不破版 | `共用外框/支援的視窗與輸入方式` | — |
| 同上 | 3 滑鼠與觸控 | `共用外框/支援的視窗與輸入方式`、`總覽看板/拖曳派單`；`dsl.md` 的兩組瀏覽器設定 | — |
| 同上 | 4 尚未開放的入口 | `共用外框/尚未開放的入口` | — |
| 同上 | 5 櫃檯只有派單與當場回答兩種寫入 | `單筆詳情/派單區與單據管理動作列` | — |
| 同一張單同時被處理時先送出的為準 | 1 兩台同時派 | `待派單/撞單與送出失敗`、`總覽看板/拖曳派單` | `櫃檯看板與派單/櫃檯派單` |
| 同上 | 2 已不在待派單時不生效 | `待派單/撞單與送出失敗`、`當場回答/撞單與送出失敗` | `櫃檯看板與派單/櫃檯派單`、`當場回答並結案` |
| 同上 | 3 送出失敗 | `待派單/撞單與送出失敗`、`總覽看板/拖曳派單`、`當場回答/撞單與送出失敗` | `櫃檯看板與派單/櫃檯派單`（重送不會派兩次） |
| 在待派單頁派給房務或工務並在五秒內復原 | 1 清單 | `待派單/待派單清單` | `櫃檯看板與派單/櫃檯看板讀取` |
| 同上 | 2 點部門就送出 | `待派單/按鈕派單與復原`、`待派單清單` | `櫃檯看板與派單/櫃檯派單` |
| 同上 | 3 5 秒內復原 | `待派單/按鈕派單與復原`、`單筆詳情/歷程` | `櫃檯看板與派單/派出後復原`、`櫃檯看板讀取`（今日張數） |
| 同上 | 4 復原失敗 | `待派單/按鈕派單與復原` | `櫃檯看板與派單/派出後復原` |
| 同上 | 5 只有三種處理方式、不建議不自派不拆單 | `待派單/待派單清單`、`按鈕派單與復原` | `櫃檯看板與派單/櫃檯派單`、`櫃檯看板讀取` |
| 同上 | 6 空狀態 | `待派單/空狀態與載入` | `櫃檯看板與派單/櫃檯看板讀取` |
| 在看板拖曳待派單卡片完成派單 | 1 至 4 | `總覽看板/拖曳派單`、`單筆詳情/歷程` | `櫃檯看板與派單/櫃檯派單` |
| 現場變化即時跟上與連線異常提醒 | 1 不用重新整理就跟著變 | `即時更新/現場變化即時跟上`、`單筆詳情/狀態標籤與狀態訊息卡` | `員工事件串流/收得到哪些單的訊號` |
| 同上 | 2 連線異常提醒 | `即時更新/連線異常提醒列` | `員工事件串流/降級與恢復訊號` |
| 用數量卡房號與泳道在看板上找到已存在的單 | 1 數量卡與房號篩選 | `總覽看板/篩選搜尋與泳道` | — |
| 同上 | 2 三種泳道 | `總覽看板/篩選搜尋與泳道` | `櫃檯看板與派單/櫃檯看板讀取`（樓層） |
| 同上 | 3 點卡片開詳情 | `單筆詳情/詳情面板內容` | — |
| 等待時間與逾時只判斷待認領 | 1 待認領逾時、待派單只是等 | `總覽看板/等待膠囊與逾時`、`數量卡與欄頭` | — |
| 同上 | 2 已接與進行中不判斷逾時 | `總覽看板/等待膠囊與逾時`、`單筆詳情/狀態標籤與狀態訊息卡` | `櫃檯看板與派單/櫃檯看板讀取` |
| 同上 | 3 等待從哪裡起算 | `總覽看板/等待膠囊與逾時` | `櫃檯看板與派單/櫃檯看板讀取` |
| 同上 | 4 不顯示急件 | `總覽看板/等待膠囊與逾時`、`單筆詳情/詳情面板內容` | `櫃檯看板與派單/櫃檯看板讀取` |
| 同上 | 5 後四欄的膠囊 | `總覽看板/等待膠囊與逾時` | — |
| 總覽看板把全館的單依狀態分欄並算出未結案 | 1 固定 8 個欄 | `總覽看板/看板分欄與範圍` | `櫃檯看板與派單/櫃檯看板讀取` |
| 同上 | 2 只顯示今天結束的 | `總覽看板/看板分欄與範圍` | `櫃檯看板與派單/櫃檯看板讀取` |
| 同上 | 3 四張數量卡 | `總覽看板/數量卡與欄頭` | — |
| 同上 | 4 欄頭數量與警示 | `總覽看板/數量卡與欄頭` | — |
| 同上 | 5 欄內順序 | `總覽看板/看板分欄與範圍` | — |
| 同上 | 6 卡片上的文字 | `總覽看板/卡片內容` | `櫃檯看板與派單/櫃檯看板讀取` |
| 同上 | 7 載入與讀取失敗 | `總覽看板/載入與讀取失敗`、`待派單/空狀態與載入`、`即時更新/連線異常提醒列` | — |
| 資訊類問題當場回答住客並結案 | 1 送出後結案 | `當場回答/當場回答面板`、`單筆詳情/狀態標籤與狀態訊息卡`、`歷程` | `櫃檯看板與派單/當場回答並結案`、`常用回覆` |
| 同上 | 2 回覆的規則 | `當場回答/當場回答面板` | `櫃檯看板與派單/當場回答並結案` |
| 同上 | 3 取消不變 | `當場回答/當場回答面板` | — |
| 同上 | 4 只有待派單可以回答 | `單筆詳情/派單區與單據管理動作列` | `櫃檯看板與派單/當場回答並結案` |
