# Phase 0 研究：住客掃碼頁（主流程）

依據：`spec.md`（使用者故事 1–8、FR-001–055、NFR-001–006、SC-001–012、「對既有行為的影響」「設計圖與後端現況的出入」「假設」「待釐清一覽」）、`specs/_inputs/guest-web-findings-2026-11-18.md`（設計圖與後端欄位的對照，下稱「對照」）、現有的 `specs/truth/techstack.md`、`app/web/`、`app/web-bdd/`、`app/bdd-support/` 與後端的原始碼（引用處附 `檔案:行號`，路徑以 repo 根為準），以及使用者 2026-11-18 的原話與已拍板的事：

- 原話：「住客用手機掃房間 QR code，打開免安裝的手機網頁（放在既有 app/web，新路由 /g/<token>），用 /guest/scan 換住客 session，之後呼叫 /guest/home、/guest/requests、/guest/stream。本輪不改後端，只做前端與驗收測試。」
- 已拍板：技術棧＝同一個 React 程式（`app/web/`）；驗收測試走既有櫃檯 Web 的測試線並加一個手機尺寸的設定；語言跟櫃檯 Web 的清單、不加韓文；本版不改後端（FR-054）。

這是本專案第四個端，也是第一次在同一個前端程式裡放兩種使用者的畫面。後端、員工手機 App、櫃檯 Web 的技術決策都不變；本文只記錄因住客掃碼頁而新增或要特別交代的決策。本文沒有查任何外部網站，所有依據都是 repo 內的檔案。

本文用到的幾個詞：

- **代碼**：QR Code 裡那串 22 碼的字（`room_qr_tokens.token`），一間房一枚，主管可以換。
- **住客 session**：用代碼向後端換來的憑證，綁這間房目前入住的訂房，退房時刻到期；格式是「飯店編號.隨機字串」。
- **事件串流（SSE）**：瀏覽器開一條不關的連線，後端有事就沿這條線推一則訊息過來。
- **延遲載入**：某一段程式等到使用者真的走到那個路由才下載，首頁不用先載整個程式。
- **純計算**：沒有畫面、只吃資料吐結果的函式，例如狀態值對到標籤。

`.agents/constitution/` 不存在，照 001–004 的做法略過。

## 三題必問的處理

`rules/AIxBDD必問問題與起始專案介面澄清判準.md` 的三題，本輪都已有答案，不另外開 `/clarify`：

| 題目 | 答案 | 依據 |
| --- | --- | --- |
| 系統有哪些端 | 四個：後端 API、員工 App、櫃檯 Web、住客掃碼頁。住客掃碼頁是新的端，程式放在既有的 `app/web/` 裡 | 使用者原話「放在既有 app/web」；`spec.md`「對既有行為的影響」ADD 列 |
| 住客掃碼頁的 BDD techstack | 沿用櫃檯 Web 的 `Cucumber-JVM` ＋ `Playwright for Java`（`app/web-bdd/`），加一組手機尺寸的瀏覽器設定；住客頁的 Gherkin 放自己的介面根 | 使用者本輪指示「web-bdd phone browser profile and a guest feature folder」；`spec.md`「假設」倒數第三條 |
| 測試策略 | 驗收測試（開真的瀏覽器、連真的後端）是主線；純計算另加 `Vitest` 單元測試；不做元件測試 | 既有 `techstack.md`「櫃檯 Web 單元測試」列與 004 決策 6，本輪沒有改判；使用者原話「只做前端與驗收測試」 |

`spec.md` 的六題待釐清（C-01、C-02、C-04、C-05、C-07、C-08）都是畫面文字或單一畫面的呈現，不改變決策集合，依 `spec.md`「待釐清一覽」的指定交 `/ui-plan` 決定，本文不升級成 `/clarify`。

## 決策 1：住客掃碼頁放在哪裡、網址長什麼樣

- **要決定什麼**: 住客頁是不是獨立的前端專案；QR Code 印的網址；與櫃檯 Web 的飯店網址（`/{飯店代碼}/…`）怎麼不撞。
- **Decision**:
  - 住客掃碼頁是系統的第四個端，程式放在既有的櫃檯 Web 專案 `app/web/` 裡，同一個建置、同一個開發伺服器、同一個部署來源；它與櫃檯 Web 是同一個網站上的兩個區域，不是兩個網站。
  - QR Code 的網址是 `{網站來源}/g/{代碼}`。住客頁所有子頁都在 `/g/{代碼}/…` 底下（見決策 2）。
  - 行為規格放新的介面根 `specs/truth/features/guest-web/`，自己有一份 `dsl.md`，由 `/dsl-refine` 建立。
- **Rationale**:
  - 使用者原話指定放既有 `app/web`。放同一個專案能直接沿用：以 `fetch` 讀事件串流的程式與連線狀態機（`app/web/src/features/realtime/stream-client.ts:21,65`、`live-connection.ts:39`）、`ApiError`／`NetworkError`／`newIdempotencyKey`（`app/web/src/api/client.ts:9,25,49`）、把儲存空間被封鎖時的例外攔住的 `browser-storage.ts`、`i18next` 的設定與四份語系檔的鍵集合一致性測試（`app/web/src/i18n/locales/locales.test.ts`）、以及已經會起後端與開發伺服器的 `app/web-bdd/run-feature.sh`。
  - 網址用 `/g/` 而不是 `/guest/`：後端已經佔了 `/guest/**`（`tms/src/main/java/com/athena/tms/controller/GuestController.java:38-68`、`GuestStreamController.java:17`），同源部署時網頁路由與 API 路徑不能共用同一段前綴，開發伺服器的代理規則也會撞到。
  - `/g/` 不會被當成飯店代碼：飯店代碼至少 3 個字（`specs/truth/data/data-model.dbml` 的 `tenants.code`，004 `/data-plan` 定的長度 3–32），而且 React Router 先比對固定路徑再比對帶參數的路徑（`app/web/src/router.tsx:31-33` 的註解就是靠這一點讓 `/login` 不被當成飯店），所以 `/g/…` 不會落進 `/:hotelCode/*` 的 `ToBoard`。
  - 代碼留在網址上，是 FR-004、FR-005 的前提：session 不見了（新分頁、到期、QR 被換）時，頁面用網址裡的代碼重新換一次，住客不用再掃。
  - 介面根叫 `guest-web`，與 004 當初替 `frontdesk-web` 命名時預留的理由一致（004 research 決策 1：「住客掃碼頁也是 Web，之後進 truth 時單叫 `web/` 會撞名」）。
- **Alternatives considered**:
  - 另起一個前端專案（例如 `app/guest-web/`）：住客頁與櫃檯 Web 的相依不同（住客頁不需要拖曳套件），分開可以各自瘦身。但要再養一份建置設定、代理設定、語系機制與驗收測試線的啟動腳本，使用者已指定同一個程式；瘦身用延遲載入就做得到（決策 2）。不採用。
  - 網址用查詢參數 `/?token=…`（舊的概念驗證頁 `docs/13-poc/guest.html` 的做法）：根路徑現在是「缺少飯店代碼」頁（`app/web/src/router.tsx:36`），而且查詢參數在子頁之間要一直帶著，容易漏。不採用。
  - 網址帶飯店代碼 `/{飯店代碼}/g/{代碼}`：後端換 session 時不收飯店代碼，它自己對每一家飯店找這枚代碼（`tms/src/main/java/com/athena/tms/service/GuestScanService.java:96`），之後的 session 已含飯店編號。多一段只會讓 QR Code 更密，沒有用處。不採用。
- **殘餘風險**: 印製 QR Code 的那一方要改用 `/g/{代碼}` 這個格式；後端給主管的列印資料只有代碼本身（`RoomQrController.java:19` 回 `{roomNo, token, version, issuedAt}`），網址由印的人組。印製不在本版，但要讓負責的人知道。

## 決策 2：路由樹、延遲載入與獨立的手機外框

- **要決定什麼**: 住客頁有哪些路由、子頁的網址要不要一直帶代碼；住客的程式要不要和櫃檯的分開下載；手機版面與櫃檯的桌面外框怎麼共存。
- **Decision**:
  - 路由樹掛在 `/g/:qrToken` 底下，所有子頁都帶著代碼：首頁 `/g/{代碼}`、備品補充 `/g/{代碼}/supply`、設備報修 `/g/{代碼}/repair`、送出成功 `/g/{代碼}/sent`、我的需求清單 `/g/{代碼}/requests`、單筆需求進度 `/g/{代碼}/requests/{需求識別碼}`、資訊頁內容 `/g/{代碼}/guide/{資訊頁的鍵}`。無法使用頁與重試狀態不是路由，是 `/g/:qrToken` 這一層在換 session 失敗時畫的畫面。這些路徑是給 `/ui-plan` 與實作的起點，確切的英文段由實作定，但「每一頁都帶代碼」不變。
  - 整棵 `/g/…` 路由用 React Router 的 `lazy` 延遲載入：住客走到 `/g/…` 才下載住客的程式；櫃檯的程式（含拖曳套件 `@dnd-kit`）不會進住客手機。櫃檯的路由維持現狀、不改成延遲載入。
  - 住客頁有自己的外框（`GuestShell`）：單欄、內容最寬 480 點置中、`dvh` 高度單位與安全區域邊距；不走 `DeskShell`（它強制最小寬 1180px，`app/web/src/index.css` 的 `--min-width-shell: 1180px`）。首頁沒有標題列，子頁共用一種「返回鈕、頁標題、房號 {房號}」的標題列（FR-052）。
  - 住客區的程式集中在 `app/web/src/guest/` 一個目錄（路由、外框、各頁、API、儲存、純計算），不散進櫃檯的 `features/`、`pages/`。
- **Rationale**:
  - 子頁帶代碼：FR-005 要求「重新整理、關掉再從歷史記錄打開，都回到首頁或原本那一頁」。代碼只在首頁網址上的話，子頁重新整理就找不到代碼、無法重新換 session。
  - 延遲載入：住客用的是手機與飯店 Wi‑Fi，首頁要快；櫃檯那包程式對住客完全沒用。React Router 8 的路由 `lazy` 屬性就是做這件事的，不用另外引入套件。
  - 獨立外框：兩種畫面的版面前提相反（櫃檯最小 1180 寬、整頁不捲；住客最寬 480、整頁可捲、按鈕釘在頁底），硬塞進同一個外框只會各自破版。
  - 集中在一個目錄：之後要把住客頁搬出去、或要查「住客頁用到哪些東西」時，邊界一眼看得到；也讓 `/tasks` 排工作時路徑穩定。
- **Alternatives considered**:
  - 子頁不帶代碼、代碼只記在儲存空間：網址短一點，但新分頁打開子頁的連結就沒有代碼可用，違反 FR-005。不採用。
  - 櫃檯與住客共用一個外框、靠 CSS 切換：少寫一個元件，但兩邊的最小寬度、捲動方式、標題列都不同，共用的只剩背景色。不採用。
  - 全部路由都延遲載入：櫃檯沒有這個需要，改了只是多一次無意義的改動（Laziness Protocol）。不採用。
- **殘餘風險**: `lazy` 路由在 Playwright 驅動下第一次進頁會多一次下載，驗收等待畫面的上限（既有 DSL 是 5 秒）夠用；若在手機設定下看到逾時，先確認是不是這個原因。

## 決策 3：住客 session 存在瀏覽器的哪裡、失效時怎麼重換、怎麼與櫃檯登入隔開

- **要決定什麼**: 換來的住客 session 放記憶體、`sessionStorage` 還是 `localStorage`；後端回 401 時的處理順序；同一支手機開過櫃檯 Web 時兩邊的憑證怎麼不互相干擾（FR-055）。
- **Decision**:
  - 住客 session 放 `sessionStorage`（每個分頁各一份、分頁關掉就清掉），鍵帶代碼（例如 `guest.session.{代碼}`），值是換 session 的整份回應（`sessionToken`、`expiresAt`、`roomNo`、`infoPages`、`menu`）。代碼永遠在網址上，不另外存。儲存空間被封鎖時退回只放記憶體（`browser-storage.ts` 已經把例外吞掉，讀不到就當沒有）。
  - 進 `/g/{代碼}` 的流程：有這枚代碼的 session 就直接用；沒有就用代碼呼叫換 session；成功寫進 `sessionStorage` 進首頁；後端回 `409 room_vacant` 顯示無法使用頁；回 429、連不上、5xx 顯示重試狀態（`spec.md`「邊界情況」第 2 條）。
  - 進首頁之後任何一次呼叫被回 401：丟掉手上的 session，用網址裡的代碼重新換一次，換到就重發剛才那一次呼叫並繼續；換不到就依上一條顯示無法使用頁或重試狀態。這個重換一輪最多做一次，避免 401 與 409 之間打轉（FR-004）。
  - 住客 session 走自己的 store（`app/web/src/guest/` 內），請求以 `X-Guest-Session` 標頭帶出；絕不讀櫃檯的 `useSessionStore`，櫃檯的 `authHeaders()` 也不會被住客的請求用到。櫃檯那邊維持讀自己的鍵，看不到住客的鍵。
- **Rationale**:
  - 為什麼不是只放記憶體：重新整理就要再換一次 session，每次換都在後端多一列 `guest_sessions`；進度頁、清單頁住客會常按重新整理（這正是「我的毛巾呢」的動作）。
  - 為什麼不是 `localStorage`：住客 session 的壽命由後端管（退房時刻到期、QR 換掉就撤銷），留在裝置上沒有好處；而且 `techstack.md` 已把「憑證放 `localStorage`」列為不引入的做法，住客沒有理由例外。關掉分頁再從歷史記錄打開會是新分頁、沒有 session，但網址有代碼，重換一次就回到原本那一頁，FR-005 仍成立。
  - 為什麼鍵要帶代碼：同一支手機同一個分頁先後開了兩間房的網址（例如換房）時，不會拿上一間房的 session 去打下一間房；也讓「QR 換掉之後舊代碼」的情境自然走到重換、失敗、無法使用頁。
  - 401 只重換一次：後端對 QR 換掉、到期、撤銷都回同一個 401（`tms/src/main/java/com/athena/tms/filter/BearerTokenAuthFilter.java:63,114`），重換失敗時後端回 409，兩者分得開；但若某個狀況讓換 session 成功、下一次呼叫又 401（理論上不會），不設上限就會無限循環。
  - 隔離：櫃檯 Web 的登入狀態也在 `sessionStorage`（`techstack.md`「櫃檯 Web 登入憑證」列），鍵不同、store 不同、標頭不同，彼此讀不到對方；這就是 FR-055 要的。後端那邊 `Authorization: Bearer` 帶住客 session 也會被當住客（`BearerTokenAuthFilter.java:49-54`），但住客頁一律用 `X-Guest-Session`，避免任何人把兩種憑證混在同一個標頭名下。
  - 這一項沒有另外問使用者：`spec.md`「假設」已寫「記在這支手機的瀏覽器裡、跟著這個分頁；存法交 `/technical-research`」，本文只是把存法定下來。
- **Alternatives considered**:
  - 只放記憶體：最保險，但每次重新整理都重換，見上。不採用。
  - `localStorage`：跨分頁共用，但壽命管不住，且違反既有的排除項。不採用。
  - 用 `Authorization: Bearer` 帶住客 session：後端收得了，但與櫃檯的標頭同名，將來任何共用的程式碼（例如錯誤攔截）都要先分辨是誰的憑證。不採用。
- **殘餘風險**: iOS Safari 把分頁「還原」時 `sessionStorage` 可能跟著回來（004 決策 9 已列同一個風險）；對住客來說這只是少換一次 session，沒有壞處。

## 決策 4：住客頁怎麼呼叫後端

- **要決定什麼**: 用現有的 `api/client.ts` 還是另寫；呼叫哪一組住客端點；重複送出怎麼防；開發伺服器的代理要加什麼；契約真相與程式現況的落差怎麼處理。
- **Decision**:
  - 住客頁有自己的薄薄一層 API（`app/web/src/guest/api.ts`）：前綴 `/guest`、標頭 `X-Guest-Session`、401 時走決策 3 的重換。它與 `api/client.ts` 共用的只有 `ApiError`、`NetworkError`、`newIdempotencyKey` 與錯誤內容的解析；`api/client.ts` 的 `send` 寫死了 `/api/v1` 與櫃檯的 `authHeaders()`（`client.ts:5,96,102`），要共用就得把前綴與標頭抽成參數，由實作決定抽或複製（兩者都小）。
  - 用的端點一律是 `/guest/*`：`POST /guest/scan`（公開）、`GET /guest/home`、`POST /guest/requests`、`GET /guest/requests`、`POST /guest/requests/{id}/cancel`、`POST /guest/requests/{id}/rating`、`GET /guest/stream`（`GuestController.java:38-68`、`GuestStreamController.java:17`）。不用 `/api/v1/guest-requests`（它沒有首頁資料與評分，`GuestRequestApiController.java:90-101`），也不用 `/api/v1/guest-sessions/scan`（那是櫃檯替住客開 session 的介面，要員工身分，`SecurityConfig.java:59`）。
  - 每個寫入都帶 `Idempotency-Key`（`newIdempotencyKey()`），一次送出內的每一張需求各一把鍵；按鈕按下到後端回覆之間不能再按（NFR-002）。
  - 備品補充一次送多項時，依清單順序一張一張送（FR-015），前一張回覆了才送下一張；中途被後端拒絕（429 限流）就停，已建立的保留（FR-017）。
  - 開發伺服器的代理加 `/guest`，並沿用 `/staff/stream` 那段「後端斷了就把瀏覽器這一頭也切斷」的設定（`app/web/vite.config.ts:12-24`），因為 `/guest/stream` 也是一條不關的連線。
  - 契約真相（`specs/truth/contracts/groups/住客與房間/住客掃碼.yaml`）目前只寫了 `/api/v1/guest-sessions/scan`、`/api/v1/guest-requests`（列出、建立）與撤回三個操作；住客手機實際要打的 `POST /guest/scan`、`GET /guest/home`、`POST /guest/requests/{id}/rating`、`GET /guest/stream` 與 `/guest/requests` 這一組都不在契約裡。**本輪不補契約**（使用者限定契約與資料真相這一輪只記 NOOP），住客頁照程式現況呼叫；這個缺口交 `/system-analysis` 寫進 `plan.md` 的落差表，由 `/api-plan` 在 truth-delta 記下「已檢查、未補」，之後另開一輪補進契約（只是把既有行為寫進契約，不改後端）。
- **Rationale**:
  - 另寫薄層而不是改 `api/client.ts` 的介面：櫃檯的呼叫方有十幾處，為了住客改它的簽名會動到不相干的程式；住客要的只是前綴、標頭、401 處理三件事。
  - 用 `/guest/*`：這組是完整的（有首頁資料、評分、串流），`spec.md` FR-054 也只允許用後端現有的住客介面。
  - 冪等鍵：後端只在請求帶了 `Idempotency-Key` 時才擋重複（`tms/src/main/java/com/athena/tms/filter/IdempotencyFilter.java:41-46`，租戶從住客 session 解析），所以要擋網路重送就一定要帶。SC-005 要求重複按或重送多建立的張數是 0。
  - 一張一張送：後端的建立需求只收一項（`itemId` 與 `text` 二選一，`spec.md`「對既有行為的影響」），沒有批次介面；一起發出去的話被限流擋下的會是隨機的幾張，住客看到的「已送出 n 項」會與實際不符。
  - 這一項沒有另外問使用者：使用者原話已指定用 `/guest/scan`、`/guest/home`、`/guest/requests`、`/guest/stream`。
- **Alternatives considered**:
  - 把 `send` 改成吃前綴與標頭，櫃檯與住客共用：共用的程式多一點，但櫃檯那邊所有呼叫處都要跟著改簽名；留給實作視大小決定。
  - 多項一起並行送：快一點，但限流時結果不可預期，見上。不採用。
  - 本輪把 `/guest/*` 補進契約：是對的事（004 就這樣把 `/staff/stream` 補進契約），但使用者這一輪明確限定契約只記 NOOP。不在本輪做。
- **殘餘風險**:
  - 契約真相與住客頁實際呼叫的介面對不上，之後 `/dsl-refine`、`/tasks` 只能以程式現況與「對照」§2 為準，而不是契約；補契約前這是 truth 的已知缺口。
  - `IdempotencyFilter` 的順序（`@Order(0)`，`IdempotencyFilter.java:21`）與認證過濾器的先後本文沒有實跑過；「對照」§2 也建議實作時先驗一次重送會回放。列入 spike。
  - 10 分鐘內 3 筆的限流是「整間房」算的（`tms/src/main/java/com/athena/tms/service/GuestRequestService.java:66-67`），住客頁只看得到自己訂房的需求（兩者在一間房一筆訂房時相同），估算可能偏高，被拒絕時依 FR-017、FR-035。

## 決策 5：畫面怎麼跟上同仁的動作

- **要決定什麼**: 住客頁用什麼機制做到 FR-045–047、NFR-003（5 秒內跟上）；連線異常時怎麼辦；後端給住客的串流與給員工的有什麼不同。
- **Decision**:
  - 進首頁之後開一條 `GET /guest/stream`，用 `fetch` 讀、自己解析，帶 `X-Guest-Session` 標頭；瀏覽器內建的 `EventSource` 不能帶標頭，不用（與櫃檯 Web 相同）。解析器 `takeStreamEvents` 與連線狀態機 `transition`（`stream-client.ts:21`、`live-connection.ts:39`）直接沿用；`openStaffStream` 寫死了 `/staff/stream` 與櫃檯標頭（`stream-client.ts:65,100-101`），實作時把網址與標頭抽成參數，櫃檯與住客各傳自己的，抽完就沒有兩份連線程式。
  - 收到 `task` 訊號就重讀 `GET /guest/requests`；收到 `resync` 重讀需求清單與 `GET /guest/home`（後端給住客的 `resync` 寫的重抓位置就是 `/guest/home`，`tms/src/main/java/com/athena/tms/service/StreamSubscriber.java:52-54`）。訊號不帶內容，所以沒有「直接換上那一張」的路，一律重讀；清單是一次住宿的量，很小。
  - 異常時改成每 4 秒重讀需求清單（與櫃檯 Web 同一個間隔 `REREAD_INTERVAL_MS`，`app/web/src/api/query-client.ts:5`），畫面上不顯示任何提示列（FR-046）。異常的判定三種：串流斷線；超過 35 秒沒有收到任何資料（心跳每 15 秒一次，`StreamHeartbeat.java:15`，既有的 `SILENCE_LIMIT_MS`）；收到 `degraded` 事件。
  - 恢復的判定只有一種：串流（重新）連上並收到 `init`。後端給住客的串流沒有任何「已恢復」的訊號：`recovered` 事件只送給員工連線（`StreamConnectionRegistry.java:145-149` 的 `isStaff()` 條件），心跳的 `degraded` 旗標也只加在員工連線上（`StreamConnection.java:122-124`，住客的心跳只有 `stream_pos`）。所以收到 `degraded` 之後，住客頁一直每 4 秒重讀，直到這條連線被後端或網路切斷、重連成功為止；後端對閒置連線的逾時是 30 分鐘（`StreamConnectionRegistry.java:31`），這是最長的輪詢時間。住客頁不自己切斷重連來「假裝恢復」：重連後的新連線不會補送 `degraded`（`StreamConnectionRegistry.java:128-131` 只對當時在線的連線送），看起來健康不代表後端已恢復。
  - 頁面之間切換不重開串流：串流掛在 `/g/:qrToken` 這一層，子頁只訂閱重讀後的資料。
- **Rationale**:
  - 後端已經為住客做好了這條串流：住客連線只收到自己那筆訂房的任務事件（`StreamSubscriber.java:27`）；004 做櫃檯 Web 時已實跑過這條鏈，`app/web/spikes/stream/` 留有當時的試驗。
  - 重讀而不是直接換上：給住客的 `task` 訊號不帶卡片（只有櫃檯角色帶，`StreamSubscriber.java:44-47`）。
  - 4 秒：NFR-003 要「連線有問題時仍在定期讀取的一個週期內跟上」，`spec.md` 把間隔交給後續規劃；用與櫃檯相同的值，少一個要解釋的數字。
  - 這一項沒有另外問使用者：行為由 FR-045–047 限定，後端機制現成。
- **Alternatives considered**:
  - 平常就只輪詢、不接串流：最簡單，但 NFR-003 的 5 秒要靠 4 秒輪詢去撐，每支手機每 4 秒打一次後端；後端已有串流，沒有理由不用。不採用。
  - 直接拿 `useLiveUpdates` 來用：它綁著櫃檯的 `useSessionStore` 與看板的 `followBoard`（`use-live-updates.ts:10,15`），住客頁要的只是「有訊號就重讀」。不採用，改抽共用的連線函式。
- **殘餘風險**:
  - 事件從發生到串流大約晚 2 秒（outbox 轉送每 2 秒一次，004 決策 8），加上重讀的往返；5 秒的門檻在本機環境成立，經過反向代理的正式環境要再量一次。
  - 住客收不到任何恢復訊號（`recovered` 與心跳的 `degraded` 旗標都只給員工）。收到 `degraded` 之後最長會每 4 秒重讀 30 分鐘（到後端把連線逾時切斷、重連為止）；清單是一次住宿的量、降級又少見，代價可以接受，NFR-003 在降級期間仍由輪詢撐住。要縮短只能改後端（在住客心跳也加旗標是一行的事，但違反 FR-054），這是使用者可以改判的點：若接受改後端，`/api-plan` 另開一輪把住客串流補進契約時一併加上，本文的恢復規則再改回「心跳帶 `degraded: false`」。
  - `degraded` 只有「消費者落後」這個原因會送給住客（`StreamConnectionRegistry.java:128-131`，`everyone` 只在 `CONSUMER_LAG` 時為真）；Kafka 整個停掉時後端對員工連線會宣告 `kafka_unavailable`、對住客不會，住客頁那時看到的是心跳正常、沒有任何訊號。降級期間才連上的住客也不會補送 `degraded`。這是後端的既有行為，本版不改。

## 決策 6：介面語言

- **要決定什麼**: 住客頁的四種語言怎麼掛進既有的 `i18next`；第一次打開時怎麼挑語言；住客選過的語言記在哪裡；與櫃檯 Web 的語言記憶怎麼互不影響。
- **Decision**:
  - 同一個 `i18next` 實例、同一組四份語系檔（`zh-TW`、`en`、`ja`、`vi`），住客頁的字放在每份語系檔的 `guest` 子樹底下；既有的鍵集合一致性測試（`locales.test.ts`）自動涵蓋新子樹。不加韓文（NFR-004）。
  - 第一次打開的語言依手機瀏覽器：主語言是 `zh` 的任何變體一律繁體中文、`ja` 日文、`vi` 越南文、其餘一律英文（NFR-004）。這條規則與櫃檯 Web 的不同（櫃檯對不認得的語言用繁體中文，`app/web/src/lib/browser-language.ts:12`），所以住客頁有自己的比對函式，不改櫃檯那一支。
  - 住客選過的語言記在 `localStorage`，一支手機一筆（鍵例如 `guest.language`），不分房、不分代碼；下次打開任何住客頁都用它。與櫃檯的語言鍵（登入頁語言、每位員工一筆）完全不同名。
  - 住客區的外框在掛上時依「住客的語言記憶 → 瀏覽器語言」決定語言並切換；右上的語言膠囊切換時寫回住客的鍵並切換。後端給的字（服務項目名稱、需求標題、櫃檯回覆、資訊頁）原樣顯示，不經過翻譯（NFR-004）。
- **Rationale**:
  - 同一個實例：`i18next` 的型別宣告以 `zh-TW` 的整棵資源樹為準（`app/web/src/i18n/i18next.d.ts`），加子樹就能拿到型別檢查與鍵集合一致性測試；另開一個實例要再複製一套初始化與型別宣告。
  - 住客自己的比對函式：規則不同（不認得的語言用英文），住客面對的是外國旅客，櫃檯面對的是本地員工，`spec.md`「假設」已說明兩邊不強求一致。
  - `localStorage` 一支手機一筆：住客沒有帳號，手機就是這個人；`spec.md` NFR-004 要「記在這支手機上，下次打開還是它」。
  - 既有的 `initialLanguage()` 在程式啟動時讀櫃檯的登入狀態來決定語言（`app/web/src/i18n/index.ts:17`、`interface-language.ts:30`）。住客區的外框掛上時主動再切一次語言，就不會被同一支手機上殘留的櫃檯登入狀態帶走（FR-055）。`followSession` 只在櫃檯登入身分變動時才動作（`interface-language.ts:34-52`），住客流程裡不會觸發。
- **Alternatives considered**:
  - 住客頁另開一個 `i18next` 實例、用 `I18nextProvider` 包住：隔離最徹底，但多一套初始化與型別宣告，而兩邊的語言清單本來就一樣。不採用。
  - 住客語言也存 `sessionStorage`：關掉分頁就忘了，下次掃碼又要重選。不採用。
- **殘餘風險**: 語系檔會變大（櫃檯與住客的字在同一份檔裡），但延遲載入切的是程式不是語系檔；四份語系檔都是純文字，大小不是問題。要分開載入語系是之後的最佳化，本版不做。

## 決策 7：樣式與版面

- **要決定什麼**: 住客頁的設計變數（色彩、字級、圓角、間距）與櫃檯的 Tailwind 主題怎麼共存；手機版面的硬性規則。
- **Decision**:
  - 沿用 `Tailwind CSS`。住客頁的設計變數照「對照」§4（設計系統 `tms-web` 的 token、配色 B）另立一組命名空間，不覆蓋櫃檯既有的 `@theme`（兩邊的 `--color-*` 多數值相同，但例如卡片圓角櫃檯 14、住客 16，字級與間距也不同）。確切的變數名稱與每一頁的尺寸由 `/ui-plan` 依原型 `Guest.dc.html` 定。
  - 版面硬性規則（NFR-005）：手機直向為基準（驗收用 393 寬）、單欄、內容最寬 480 點置中；可點的東西至少 44 點見方；文字放不下就換行或「…」截斷；備品補充與設備報修的送出鈕釘在頁底；高度用 `dvh` 與安全區域變數（沿用 004 決策 10 的避免事項）。
  - 深色模式：原型有深色版，要不要做交 `/ui-plan`；本文不替它決定，但樣式變數要留得出深色的接點（用 CSS 變數而不是把色碼寫死在元件裡）。
- **Rationale**:
  - 同一個 Tailwind 設定裡放兩組變數，比再養一份樣式工具鏈便宜；命名空間分開是為了不讓住客的圓角、字級改到櫃檯。
  - 44 點、480 寬、釘底按鈕都是 `spec.md` 寫死的，不是本文的選擇。
- **Alternatives considered**:
  - 住客頁改用獨立的 CSS 檔、不經 Tailwind：原型的 class 名稱（`tw-*`）直接搬得過來，但專案裡會有兩套寫樣式的方式。不採用；原型的 class 當對照用，實作仍用 Tailwind。
  - 直接用原型引用的設計系統檔（`ds/tms-web/tokens.css`、`bundle.css`）：那兩個檔不在 repo 裡（「對照」開頭已說明只剩另一個 session 的暫存副本），不能當相依。不採用；值照「對照」§4 與原型抄。
- **殘餘風險**: 原型的 token 值與 `/ui-plan` 實際定下來的可能有細微出入；以 `ui/ui-plan.md` 為準。

## 決策 8：住客頁的 BDD techstack 與測試線怎麼切

- **要決定什麼**: 住客頁的 Gherkin 用什麼跑、放哪裡；既有 `app/web-bdd/` 要改哪些地方才跑得動手機尺寸與住客的規格；後端資料準備缺什麼。
- **Decision**（使用者已拍板沿用 web-bdd 並加手機設定；細節是本文定的）:
  - 執行 Gherkin：`Cucumber-JVM` ＋ `Playwright for Java`，就在既有的 `app/web-bdd/` 模組裡，同一個 glue 套件 `com.athena.web.bdd`，住客的步驟放 `steps/guest/`、頁面物件放 `pages/guest/`。不另起模組，也不另起 Node 測試線。
  - 瀏覽器設定加第三組 `phone`：WebKit、393 × 852、開啟觸控、語言預設繁體中文；規格句型裡的寫法是「手機直向 393 × 852 觸控」。既有的 `WebProfile` 列舉與 `BrowserProfiles.ofDevice` 各加一項（`app/web-bdd/src/test/java/com/athena/web/bdd/WebProfile.java:14-18`、`support/BrowserProfiles.java:24-27,43-52`）。住客的規格只在 `phone` 跑；櫃檯的規格不在 `phone` 跑。
  - 住客的 Gherkin 放 `specs/truth/features/guest-web/`，與 `frontdesk-web/` 並列，由 `/dsl-refine` 建立；它的 `dsl.md` 自己定共通約定（預設飯店、預設房與訂房、預設資訊頁與服務項目、瀏覽器設定、畫面與後端都要成立的斷言方式）。
  - `run-feature.sh` 的改法：它現在把 feature 根寫死成 `frontdesk-web/`（`app/web-bdd/run-feature.sh:55,234-258`），瀏覽器設定只接受 `desktop`、`ipad`（`:221-225`）。改成：feature 路徑可以用 `guest-web/…` 或 `frontdesk-web/…` 開頭指定根（不帶根時維持 `frontdesk-web/`），`phone` 加進可接受的設定；住客的規格沒指定設定時預設 `phone`。Runner 不用加：腳本一律傳 `-Dcucumber.features`（`:577-578`），而 Cucumber 的 JUnit 引擎在這個屬性有值時忽略 `@SelectDirectories`（`RunFrontdeskWebFeaturesTest.java:9-11` 的註解），所以同一個 runner 跑得了兩個根。Runner 名稱保留；要不要改名由 `/tasks` 定。
  - 後端資料準備：`BackendChannel`（`app/bdd-support/`）要加兩個 helper，`roomQrToken(roomNo)` 以主管 `M0001` 的身分呼叫 `GET /admin/rooms/{roomNo}/qr` 取代碼（`/admin/**` 只放行主管，`SecurityConfig.java:55`；`jwt(CreatedStaff)` 已有，`BackendChannel.java:2469`），`rotateRoomQr(roomNo)` 呼叫 `POST /admin/rooms/{roomNo}/qr/rotate`。這兩個端點不在 `/api/v1` 底下，而 `BackendChannel.request()` 會自動加 `/api/v1` 前綴（`BackendChannel.java:2666` 與 `guestRequests` 的用法），helper 要繞過前綴。既有的 `createReservation`、`insertTodayReservation`、`setTenantConfig`（`request_menu`、`guest_info_pages`）、`guestSubmitRequest`、`guestSubmitMenuItem`、`guestWithdrawRequest`、`guestRequests`、`deskAnswer`、`createTaskInSituation`、`setBackendClock` 都用得上（`BackendChannel.java:798,1013,1485,2272-2342,2439`）。「另一台裝置先評了分」的情境還需要 `guestRateRequest`，一併加。
  - 每個情境開頭的資料重設與預設飯店（`WebWorld.prepareScenario`，`support/WebWorld.java:68-73`）照用；住客的 Given 在它之上建房、訂房、資訊頁、服務項目與代碼。
  - 驗收以手機設定跑一輪為基準；同一支規格不在桌面設定重跑。
- **Rationale**:
  - 沿用同一個模組：住客情境的前提與觸發幾乎都是「飯店那邊的動作」（阿美認領、開始、完成；櫃檯當場回答；主管換 QR；撥時鐘到 10 分鐘後），這些 `BackendChannel` 都已經有或一個 helper 就補得上；另起測試線就要重寫一遍。
  - 第三組設定而不是改既有兩組：櫃檯的規格依賴 1280 與 1180 兩個尺寸；住客要的是手機直向。WebKit 近似 iPhone 上的 Safari（不等於真機），與 `ipad` 設定用同一個引擎。
  - 獨立的介面根：004 決策 1 命名 `frontdesk-web` 時就是為了給住客頁留 `guest-web`；兩個根的共通約定不同（櫃檯要登入與角色，住客要房、訂房、代碼），塞進同一份 `dsl.md` 會互相干擾。
  - 不改 runner：少一個檔、少一條啟動邏輯；`-Dcucumber.features` 已是腳本唯一的指定方式。
  - 取代碼走主管的正式 API 而不是直接讀資料庫：`room_qr_tokens` 的輪替規則（同交易作廢舊代碼並撤銷該房 session，`specs/truth/data/data-model.dbml:112-128`）由後端保證，測試直接寫表會漏掉連坐撤銷，「主管換掉 QR Code 之後舊代碼無法使用」的情境就測不到真的行為。
- **Alternatives considered**:
  - 把住客的 feature 塞在 `frontdesk-web/` 底下當一個模組：腳本與 runner 都不用改，但名字就是錯的，`dsl.md` 的共通約定也混在一起。不採用。
  - 另起 `app/guest-web-bdd/` 模組：隔離乾淨，但 World、瀏覽器設定、步驟基底類別都要再複製一份。不採用。
  - 用真機或模擬器的 Safari（Appium）驗手機：最接近現場，但最慢、最不穩，`techstack.md` 已排除。不採用；真機手感人工驗一次。
- **殘餘風險**:
  - WebKit 393 × 852 不是真的 iPhone；安全區域、瀏覽器工具列收放、`dvh` 的實際行為要在真機上看一次。
  - 腳本改成兩個根之後，`--keep` 沿用的後端與開發伺服器對兩個根都適用，但資料重設每個情境都做，兩個根的規格不能同時跑（與既有限制相同）。
  - 住客情境大量依賴「撥後端時鐘」（10 分鐘限流、退房時刻到期），瀏覽器時鐘也要跟著撥（既有 `Clocks` 支援已在 004 實跑過）。

## 決策 9：單元測試的範圍

- **要決定什麼**: 住客頁哪些東西用 `Vitest` 測、哪些只靠驗收。
- **Decision**: 只測純計算，放 `app/web/src/guest/lib/*.ts` 與同名 `*.test.ts`：
  - 後端十個狀態值對到五個顯示階段，以及每一個狀態的標籤、進度步數、清單段落、可否取消、要不要鎖定說明（使用者故事 3 的對照表，一張表驅動三個畫面）。
  - 狀態訊息框的優先序（已取消 → 櫃檯回覆 → 待派單 → 已完成 → 無）、各步副文、首頁進度卡的一行說明。
  - 10 分鐘內剩餘額度的估算（含已取消的）。
  - 設備報修的文字組合（`{類別}`、`{類別}：{補充說明}`、「其他」只有補充說明）與 200 字上限。
  - 問候語的時段分界、送出時間的「時:分」格式。
  - 住客的瀏覽器語言比對（`zh*` → 繁體中文、不認得 → 英文）。
  - 401 重換一次的狀態機（有 session → 401 → 重換 → 成功重發／失敗顯示）。
  - 不做元件測試；每一種計算至少要有一個驗收情境在畫面上看到結果，由 `/dsl-refine` 與 `/tasks` 把關。
- **Rationale**: 與 004 決策 6 同一個理由：對照表與公式的組合多，開瀏覽器逐一驗太慢；這些函式不碰畫面。
- **Alternatives considered**: 只做驗收、不寫單元測試：組合太多；全部驗收化會把十個狀態 × 三個畫面都開瀏覽器跑。不採用。
- **殘餘風險**: 單元測試只證明計算對，不證明畫面用了這個計算。

## 後端現況要注意的事（本版不改，只列給後續步驟）

| 事項 | 依據 | 由哪個步驟處理 |
| --- | --- | --- |
| 契約真相缺住客手機實際要打的 `/guest/*` 這一組（換 session、首頁資料、建立、清單、撤回、評分、串流）；契約現有的是櫃檯替住客開 session 與 `/api/v1/guest-requests` 那一組 | `specs/truth/contracts/groups/住客與房間/住客掃碼.yaml:1-103`、`openapi.yaml:89-94`；程式 `GuestController.java:38-68`、`GuestStreamController.java:17` | `/system-analysis` 寫進落差表；`/api-plan` 本輪記 NOOP 並標明缺口；之後另開一輪 ADD（不改後端） |
| 空房、代碼作廢、代碼不存在後端都回 `409 room_vacant`，分不出來 | `GuestScanService.java:31`；「對照」§2 | 已拍板統一文字（FR-002），不處理 |
| 住客串流收不到 `recovered`，心跳也沒有 `degraded` 旗標（兩者都只給員工）；Kafka 整個停掉時住客收不到 `degraded` | `StreamConnectionRegistry.java:128-131,145-149`、`StreamConnection.java:122-124` | 決策 5：收到 `degraded` 後一直輪詢到重連為止；Kafka 停掉的情形本版不處理；要縮短輪詢得改後端，列為使用者可改判的點 |
| 給住客的服務項目只有鍵與名稱，沒有類型、上限、單位 | `tms/src/main/java/com/athena/tms/dto/GuestMenuItemView.java` | 已拍板（FR-012、FR-013），不處理 |
| 給住客的需求沒有接手人、各步時間、取消原因 | `tms/src/main/java/com/athena/tms/dto/GuestTaskView.java` | 已拍板（FR-026、FR-024、FR-053），不處理 |
| 限流是整間房算的，住客頁只看得到自己訂房的需求 | `GuestRequestService.java:66-67` | FR-016、FR-017 已處理 |
| 後端的住客行為規格在後端測試的 feature（`tms/src/test/resources/features/01-guest-requests.feature`），不在 `specs/truth/features/backend/`；`backend/` 底下沒有住客掃碼的模組 | `specs/truth/features/backend/` 目錄 | 本版不補後端規格（FR-054）；之後補契約時一併考慮 |

## 建立住客區時要先做的 spike

半日以內，在第一個住客路由能顯示時做：

1. 在 `phone` 設定（WebKit 393 × 852 觸控）下，用 Playwright 開 `/g/{代碼}`，確認延遲載入的路由在 5 秒內顯示首頁。
2. 用 `fetch` 連 `GET /guest/stream`（帶 `X-Guest-Session`），確認依序收到 `init`、`resync`、每 15 秒的 `heartbeat`；由阿美認領一張單，確認收到 `task` 並量從動作到畫面重讀完成的秒數（門檻 5 秒）。
3. 同一把 `Idempotency-Key` 連送兩次 `POST /guest/requests`，確認第二次是回放、不多建一張。
4. 關掉分頁再從歷史記錄打開子頁的網址，確認重換 session 後停在原本那一頁。

失敗時的退路：第 1 項逾時就把住客路由改成不延遲載入（只影響下載量）；第 3 項不回放就在送出鈕按下後鎖住、並以 `GET /guest/requests` 的結果核對張數（NFR-002 仍靠畫面鎖住按鈕達成）。

## 殘餘風險與後續驗證

- **契約真相與住客頁實際呼叫的介面對不上**（決策 4）：這一輪不補，`/dsl-refine` 與 `/tasks` 以程式現況與「對照」§2 為準。
- **住客收不到任何恢復訊號、Kafka 停掉時也收不到 `degraded`**（決策 5）：`degraded` 之後最長輪詢 30 分鐘到重連為止；Kafka 停掉的情形住客頁看不出來。縮短輪詢要改後端一行，違反 FR-054，由使用者決定要不要破例。
- **5 秒門檻**在本機成立，經過反向代理的正式環境要再量（決策 5）。
- **WebKit 393 × 852 不是真機**（決策 8）：安全區域、工具列、`dvh` 要在真的 iPhone 與 Android 手機上各看一次。
- **冪等鍵回放未實跑**（決策 4、spike 3）。
- **印 QR Code 的格式**要通知負責印製的人（決策 1）。
- **C-01、C-02、C-04、C-05、C-07、C-08** 交 `/ui-plan` 定字句與呈現。
- `.agents/constitution/` 不存在，照 001–004 的做法略過。
