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
| ADD | `specs/truth/contracts/openapi.yaml` -> `POST /desk/tasks/{id}/assign/undo`（`deskUndoAssign`） | 派出後復原，沒有請求內容。後端擋兩件事：距離那筆 `triaged` 事件超過 10 秒就拒絕；派出後這張單沒有再發生任何事（資料層的守衛條件）。 | FR-054、FR-055、NFR-004；落差表 4。資料層不擋時間，所以由這裡擋：畫面的時限是 5 秒，另留 5 秒給網路往返與逾時後重送一次，合計 10 秒，避免很久之後還把單拉回來。 |
| ADD | `specs/truth/contracts/openapi.yaml` -> `POST /desk/tasks/{id}/answer`（`deskAnswerTask`）、schema `DeskAnswerRequest` | 當場回答並結案，回覆去掉頭尾空白後 1–120 字；成功回結案後的 `DeskTask`。沒有復原。 | FR-067 至 FR-075；落差表 6。 |
| ADD | `specs/truth/contracts/components/schemas/shared.yaml` -> `Error` 的 code `task_not_in_triage` | 409。派單或當場回答送到時單已不是待派單；`context` 帶 `resolution`（`assigned`、`answered`、`guest_withdrew`、`other`）、`state`、`roomNo`，被派走或被回答時另帶 `actorName`，被派走時再帶 `dept`。 | FR-059、FR-075；落差表 5。沿用 `StateConflictException` 帶代碼與附帶資訊的既有機制，畫面才說得出「誰把哪一間派給哪個部門」。 |
| ADD | `specs/truth/contracts/components/schemas/shared.yaml` -> `Error` 的 code `undo_claimed`、`undo_guest_withdrew`、`undo_expired`、`undo_unavailable` | 409。復原失敗的四種原因各一個 code：已有負責人（帶 `assigneeName`、`dept`、`roomNo`）、住客已收回、超過 10 秒、其他。判斷順序是先看單現在的樣子、再看時間。 | FR-055、NFR-004：兩種提示要分得出來，逾時與其他也要有去處。 |
| ADD | `specs/truth/contracts/components/schemas/shared.yaml` -> `Error` 的 code `answer_invalid`、`idempotency_key_required` | `answer_invalid`（422）：回覆是空的或超過 120 字。`idempotency_key_required`（400）：櫃檯的三個寫入沒有帶 `Idempotency-Key`。 | FR-067、FR-068；落差表 N3。三個寫入是新端點、沒有既有呼叫者，強制帶不會影響任何人；其他端點「有帶才生效」的現況不動。 |
| ADD | `specs/truth/contracts/openapi.yaml` -> `GET /staff/stream`（`staffStream`）、tag `員工事件串流 API`、`groups/現場作業/員工事件串流.yaml`、schema `StaffStreamInit`、`StaffStreamResync`、`StaffStreamTask`、`StaffStreamHeartbeat`、`StaffStreamDegraded`、`StaffStreamRecovered` | 把既有的事件串流寫進契約，並定下：`frontdesk`、`manager`、`duty_manager` 收全館，`housekeeping`、`engineering` 維持同部門或自己名下；`triage_undone` 另外送給被收回的部門；`resync.refetch` 對全館角色是 `/api/v1/desk/board`；新增 `recovered` 事件、`heartbeat.degraded`、降級期間新連線補送 `degraded`、降級原因多一個 `kafka_unavailable`。 | FR-092 至 FR-095、NFR-005；落差表 10、N4、N5。收件範圍依角色定，不用連線時聲明用途：手機 App 不是這條串流的客戶端，沒有相容問題，也少一個參數。恢復訊號用事件加心跳兩條路，中途連上或漏接的客戶端也對得齊。 |
| MODIFY | `specs/truth/contracts/groups/營運管理/員工帳號與登入.yaml` -> `staffAccountsLogin`、schema `LoginRequest.client` | 請求多一個選填的 `client`（值 `frontdesk_web`）。帶了才檢查角色：`housekeeping`、`engineering`、`owner` 密碼正確且不是臨時密碼時回與密碼錯誤完全相同的 401 `credentials_invalid`，不計入連續錯誤次數；是臨時密碼時照常登入成功並回 `mustChangePassword=true` 與角色。不帶時行為不變。 | FR-005、FR-006；落差表 N1、N6。使用者已定「登入本身要成功並回角色與必須改密碼」。由後端擋，是因為只靠畫面時回應本身就說出密碼是對的。櫃檯 Web 用這一個登入端點；`/auth/login` 不在契約，櫃檯不用。手機 App 不帶 `client`，不受影響。 |
| ADD | `specs/truth/contracts/components/schemas/員工帳號與登入.yaml` -> `LoginResponse.loginId`、`LoginResponse.hotelName` | 登入與改密碼的回應多帶員工編號與飯店名稱，兩個都是必回。 | FR-011、FR-012、FR-096；落差表 9、N7。只加欄位，手機 App 不讀就沒有影響。 |
| MODIFY | `specs/truth/contracts/components/schemas/員工帳號與登入.yaml` -> `staff_accounts.role`、`staff_accountsRequest.role`、`LoginResponse.role` | 角色的可用值寫明含 `duty_manager`。 | 落差表 11。資料庫本來就允許這個值，是把契約寫成與資料一致。`duty_manager` 能用的介面只有登入、改密碼、櫃檯看板與派單 API、員工事件串流（使用者已定）。 |
| MODIFY | `specs/truth/contracts/groups/營運管理/員工帳號與登入.yaml` -> `changeOwnPassword`、schema `ChangePasswordRequest.newPassword`、`Error` 的 code `new_password_too_weak` | 新密碼必須至少 8 碼、含英文字母、含數字，不符合回 422 `new_password_too_weak`（`context.failedRules`）；拿掉「強度規則尚未定義」的說明。規則對所有呼叫者適用。 | FR-016；落差表 9。使用者已定這三條規則。規則跟著帳號走：同一組密碼兩個端都用，只擋櫃檯 Web 沒有意義。手機 App 的改密碼畫面本來就先檢查同一組規則（`app/mobile/lib/features/login/password_rules.dart`），既有密碼不受影響。 |
| ADD | `specs/truth/contracts/components/schemas/住客掃碼.yaml` -> `GuestRequest.answer` | 住客視角的需求多一個可為空的 `answer`，帶櫃檯當場回答的回覆全文。 | FR-073；落差表 6、N10。使用者已定：住客進度頁只做到後端回得出回覆。 |
| ADD | `specs/truth/contracts/components/schemas/任務.yaml` -> `tasks.taskNo`、`tasks.answer`、`tasks.endedAt` | 任務本體多三個可為空的欄位，對應資料模型新增的 `task_no`、`answer`、`ended_at`。 | 契約的任務本體要與資料模型一致。只加欄位，手機 App 用的 `TaskDetail` 既有欄位都沒動。 |
| MODIFY | `specs/truth/contracts/components/schemas/任務.yaml` -> `tasks.note`、`tasksRequest.note` | 欄位說明拿掉「當場回答」，寫明建單後不因派單、復原或當場回答而改寫。 | `/data-plan` 已把回覆移到 `tasks.answer`；只改說明，形狀不變。 |
| MODIFY | `specs/truth/contracts/components/schemas/任務事件流水帳.yaml` -> `task_events.type`、`task_events.actorRole`、`task_events.payload` | 說明改成 25 個事件值（含 `triage_undone`）、操作者角色含 `duty_manager`、`triaged` 與 `triage_undone`、`answered` 各帶什麼。 | 與 `/data-plan` 更新後的資料模型對齊；只改說明，形狀不變。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/任務.yaml` -> `POST /tasks/{id}/triage`（`tasksTriage`） | 標成不再使用（`deprecated`）並加一句說明：沒有實作、沒有呼叫者，櫃檯處理待派單改用 `deskAssignTask`、`deskUndoAssign`、`deskAnswerTask`。路徑、請求與回應形狀都沒動。 | 落差表 12：契約與程式的路徑不一致要有一個答案。這條路徑原本只有骨架、沒有行為說明，也沒有任何客戶端用它；不刪除是因為刪既有路徑需要使用者確認。 |
| NOOP | `specs/truth/contracts/openapi.yaml` -> `GET /tasks`（`listTasks`）、`GET /task-events`（`listTaskEvents`）、`GET /tenant-configs/guest-home`、`POST /alerts`、`GET /reservations`、`GET /rooms` | 已檢查，不修改。 | 櫃檯的清單、歷程、常用回覆都有自己的端點，用不到這幾個；它們有契約、沒有實作的狀況不在 004。 |
| NOOP | `specs/truth/contracts/openapi.yaml` -> `GET /tasks/{id}`（`readTasks`）、`GET /pool`（`listDeptPool`）、`GET /me/today`、`GET /me/manager-queues`、`POST /tasks/{id}/defer`、`POST /tasks/{id}/cancel`、`POST /guest-requests/{id}/cancel`、`PUT /me/push-token` | 已檢查，不修改；`duty_manager` 也不加進這些端點。 | 手機 App 用的端點維持原狀。延後動作不收「延後到幾點」與原因（N11 屬於 005）。住客收回的既有規則足以支撐復原失敗的判斷。手機 App 的主管功能不對 `duty_manager` 放行（使用者已定）。 |
| NOOP | `specs/truth/contracts/components/parameters.yaml`、`specs/truth/contracts/components/responses.yaml` | 已檢查，不修改。櫃檯的端點沿用既有的 `TaskId`、`IdempotencyKey` 與各種錯誤回應。 | 既有定義夠用。 |

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
| NOOP | `specs/truth/data/data-model.dbml` -> `Table tenants`、`Table tenant_config` | 已檢查，不新增每家飯店的時區設定；櫃檯看板與張數的「今天」沿用後端注入的 Clock 時區。 | 使用者已定：飯店時區沿用伺服器時區。 |
| NOOP | `specs/truth/data/data-model.dbml` -> `tasks.state`、`tasks.cancel_reason`、`Table staff_accounts`、`rooms`、`reservations_cache`、`repair_tickets`、`inspections`、`task_sop_checks` | 已檢查，不需變更。狀態維持十值，取消原因維持四值；房號、樓層、住客名、VIP、SOP 進度、角色與 `duty_manager` 的資料都已存在。 | 畫面缺的是後端回傳，不是資料；由 `/api-plan` 處理。 |

## /dsl-refine

_尚未執行。_
