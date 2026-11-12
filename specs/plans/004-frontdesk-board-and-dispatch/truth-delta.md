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

_尚未執行。_

## /data-plan

_尚未執行。_

## /dsl-refine

_尚未執行。_
