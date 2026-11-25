# Phase 0 研究：櫃檯代開單

依據：本包 `spec.md`（使用者故事 1–5、FR-001–040、SC-001–013、「對既有行為的影響」「設計圖與後端現況的出入」「假設」）、`features/acceptance/*.feature`、`truth-delta.md` 的 `/specify` 預期、現有的 `specs/truth/techstack.md`、前一包 `specs/plans/004-frontdesk-board-and-dispatch/research.md`，以及後端、櫃檯 Web 與測試線的原始碼（引用處附 `檔案:行號`，路徑以 repo 根為準）。

櫃檯 Web 的技術棧在 004 已定案（`Vite`＋`React`＋`TypeScript`＋`React Router`＋`TanStack Query`＋`Zustand`＋`Tailwind CSS`＋`i18next`，驗收用 `Cucumber-JVM`＋`Playwright for Java`，後端共用 `tms`）。本包沒有引入任何新套件，只研究真正新的技術點：一次送出多張單怎麼放進同一個交易、重送怎麼不重複開單、類似單提醒與房號檢查的資料從哪裡來、送出失敗怎麼在驗收裡重現。

本文用到的幾個詞：

- **交易**：資料庫把一串寫入當成一件事，要嘛全部生效、要嘛全部當作沒發生（回滾）。
- **冪等鍵（`Idempotency-Key`）**：前端每次寫入時附上的一串隨機字。後端記下「這把鍵已經處理過、結果是什麼」，同一把鍵再送來就直接回放上次的結果，不再做一次。
- **outbox**：要發出去的事件（推播、即時訊號）先寫進資料庫的一張表，和業務資料在同一個交易裡；交易成功才會被送出，回滾就跟著消失。
- **advisory lock**：PostgreSQL 提供的「以一個數字當鎖」的機制；交易版的鎖會在交易結束時自動放掉。

## 三題必問的處理

- **系統有哪些端**：不是起始專案。`specs/truth/techstack.md` 已寫明四個端（後端 `tms/`、員工 App、櫃檯 Web、住客掃碼頁），本包只動櫃檯 Web 與後端，沒有改判，不重問。
- **BDD techstack**：techstack truth 已寫明櫃檯 Web 用 `Cucumber-JVM`＋`Playwright for Java`（`app/web-bdd/`）、後端用 `Cucumber-JVM`＋`Spring Boot Test`＋`Testcontainers`；呼叫者這輪也重申沿用，不重問。
- **測試策略**：techstack truth 已寫明各端以 E2E 為主線、Web 只替純計算加 `Vitest` 單元測試；本輪沒有改判，不重問。

其餘缺口都不會改變決策集合或推翻 spec 的裁決，留在各決策的 `Rationale` 與「殘餘風險」揭露，不升級 `/clarify`。

`.agents/constitution/` 不存在，SOP Phase 1 第 4 步要讀的憲法檔全部略過。

## 決策 1：技術棧與測試線全部沿用 004、005

- **Decision**: 不引入任何新套件、新模組或新測試線。代開單面板寫在 `app/web/` 既有的櫃檯程式裡，是蓋在總覽看板與待派單頁上的面板（與詳情、當場回答同一種做法，不新增路由）；資料讀寫用 `TanStack Query`，面板上的暫存內容（房號、品項數量、其他需求與部門、這次送出的冪等鍵）放在面板元件自己的狀態，不進 `Zustand`；介面文字進 `i18next` 四份語系檔。驗收規格進 `specs/truth/features/frontdesk-web/代開單/` 與 `features/backend/櫃檯看板與派單/`，分別由 `app/web-bdd/` 與 `tms` 的 Cucumber 跑。
- **Rationale**:
  - spec 的每一項需求都落在 004 已有的能力上：面板與遮罩、提示對話框（004 當場回答的「沒有送出」）、看板卡片高亮、即時更新、四種語言、兩種視窗尺寸。
  - 面板關閉就丟掉內容（FR-014），放元件自己的狀態最自然；放 `Zustand` 反而要記得在關閉時清掉。
  - 新路由會讓「關閉面板回到原頁、詳情面板還開著」（FR-014、驗收例 16）變複雜；面板蓋在頁面上，底下的頁面與詳情面板完全不動。
- **Alternatives considered**:
  - 代開單做成獨立頁面（`/desk/new-request`）：要處理返回時恢復原本的頁面與詳情面板，與 FR-014 的「關閉不影響底下」相反。
  - 引入表單函式庫（`react-hook-form` 之類）：面板只有一個房號欄、一個 60 字文字框與 12 個計數器，沒有需要表單函式庫的驗證流程，增加依賴不划算。

## 決策 2：一次送出的多張單怎麼放進同一個交易

- **要決定什麼**: FR-035 要求一次送出的一般單與工務單全部開成或全部不開。一般單與工務單現在各走各的入口（住客需求、工務單開單），要怎麼放進同一個交易。
- **Decision**:
  - 新增一個櫃檯專用的寫入端點，放在 `/api/v1/desk/**` 底下（確切路徑與請求、回應的形狀交 `/api-plan`）；後端用一個標了 `@Transactional` 的服務方法處理整次送出。
  - 方法內先做完所有檢查（房號存在、品項鍵合法、數量在上限內、其他需求長度），再逐張開單。每一張都走 `TaskEngine.create`（`tms/src/main/java/com/athena/tms/service/TaskEngine.java:53-102`）：房務品項與給房務的其他需求帶 `source = desk`、`initialState = pool`、不帶工務單草稿；工務品項與給工務的其他需求帶 `dept = engineering`、`type = repair` 與一份 `RepairTicketDraft`，由 `TaskEngine.create` 在同一個交易裡呼叫 `RepairTicketOpener.open` 開出 `W-` 工務單（`TaskEngine.java:99-101`、`tms/src/main/java/com/athena/tms/repair/RepairTicketOpener.java:35-69`）。
  - 工務單不直接呼叫 `RepairService.create`：它把「至少 1 張照片」「依開單人部門決定來源」「標題是 `{分類名} 工務單`」寫死在方法裡（`tms/src/main/java/com/athena/tms/repair/RepairService.java:468-555`、`:557-563`），正好是 FR-016、FR-017 要改的三件事。後端要把「開一張工務單並設好待認領來源分流」抽成可帶參數（照片是否必填、來源、`pool_source`、標題）的共用步驟，讓既有的 `POST /api/v1/repair-tickets` 與櫃檯代開共用；抽的方式交 `/api-plan`。
  - 開單後要寫的推播（新需求進待認領、工務急件）都經 outbox，與單在同一個交易裡（`tms/src/main/java/com/athena/tms/service/PushNotifier.java:26-41`、`RepairTicketOpener.java:115-131`）。任何一張失敗，整個交易回滾：已開的單、歷程、`T-`／`W-` 流水號的遞增、outbox 裡的推播與即時訊號全部消失。
  - 放在 `/api/v1/desk/**` 底下，權限直接沿用 `RolePermissions.frontdeskWebRoles()`（`tms/src/main/java/com/athena/tms/config/SecurityConfig.java:70`），`frontdesk`、`manager`、`duty_manager` 都能用（FR-034）；不放寬 `POST /api/v1/repair-tickets` 的角色（`SecurityConfig.java:71-72`）。
- **Rationale**:
  - `TaskEngine.create` 與 `TenantScopedExecutor` 都用 Spring 預設的「有交易就加入」（`TaskEngine.java:53`、`tms/src/main/java/com/athena/tms/service/TenantScopedExecutor.java:25-30`），外層一個交易就能把多張單、工務單明細、推播全部包進去，不需要分散式交易或補償。
  - 現在「部門是工務的單一定要帶工務單草稿」這條規則就在 `TaskEngine.create` 裡（`TaskEngine.java:65-68`），走同一條路自然滿足 spec「工務品項開成真正的工務單」的裁決。
  - 先檢查完再開單，失敗大多在第一張之前就發生，不會白白佔用流水號與鎖。
  - 流水號用 `INSERT … ON CONFLICT DO UPDATE` 遞增（`TaskEngine.java:104-113`、`RepairTicketOpener.java:103-112`），這一列的鎖會持有到交易結束，同一家飯店同時開單會排隊。一次送出最多 13 張（12 個品項各一張，加上其他需求一張），交易在毫秒級，影響可忽略；回滾時流水號也一起退回，不會跳號。
- **Alternatives considered**:
  - 前端逐張呼叫（住客掃碼頁的做法，`specs/plans/005-guest-web-main-flow/research.md` 決策 4）：每張各自一個交易，中途失敗會留下前幾張，違反 FR-035。
  - 一般單與工務單各打一個端點、失敗時再刪掉已開的（補償）：刪不掉已送出的推播與即時訊號，看板會閃出又消失的卡片，也違反「一張都不留」。
  - 直接把值班主管加進 `POST /api/v1/repair-tickets`、照片改選填：會連帶改到員工 App 開工務單的規則，超出本包範圍。

## 決策 3：重送怎麼不重複開單

- **要決定什麼**: FR-036 與 SC-007 要求連按、網路重送、沒收到回應後內容不改再送，都只開一次；後端其實已經開好時，再送要拿到同一次的結果。
- **後端現況**:
  - `IdempotencyFilter`（`tms/src/main/java/com/athena/tms/filter/IdempotencyFilter.java:35-67`）攔所有帶 `Idempotency-Key` 的 POST：先查 `processed_ops`，查到就回放（回應帶 `Idempotency-Replayed: true`）；查不到就往下執行，回應狀態小於 500 時把狀態與內容寫進 `processed_ops`（`tms/src/main/java/com/athena/tms/service/IdempotencyService.java:28-31`）。鍵的範圍是「飯店＋鍵」。
  - 櫃檯既有的三個寫入（派單、復原、當場回答）在服務裡強制要帶鍵，沒帶回 400 `idempotency_key_required`（`tms/src/main/java/com/athena/tms/service/DeskBoardService.java:276-280`）。
  - 前端 `apiWrite` 預設每次產生新鍵，也能由呼叫者傳入指定的鍵（`app/web/src/api/client.ts:30-33`）。
  - 回放在住客頁的 spike 實跑過（`app/web-bdd/src/test/java/com/athena/web/bdd/GuestBackendSpikeTest.java:90`）。
  - 現有機制有兩個縫：一、寫進 `processed_ops` 是在業務交易提交之後、另一個交易裡做的，提交成功但寫入失敗或程序剛好掛掉時（`IdempotencyFilter.java:56-61` 只記一行警告），重送會再做一次；二、同一把鍵的兩個請求同時在跑時，兩個都查不到紀錄，兩個都會開單。
- **Decision**:
  - 新端點沿用 `IdempotencyFilter`＋`processed_ops`，並與櫃檯其他寫入一樣強制帶鍵。
  - 前端在面板狀態裡保存「這次送出」的鍵：第一次按送出時產生；送出後沒收到回應（網路中斷、逾時）或回 5xx 時保留，內容沒改就用同一把鍵再送；房號、任一品項數量、其他需求文字或部門一改就丟掉，下次送出重新產生；後端明確拒絕（4xx）時也丟掉，因為這次什麼都沒開。送出到有結果之前，送出鈕不能按（`TanStack Query` 的 `useMutation` 進行中狀態）。
  - 後端補上兩個縫，只用在這個一次開多張單的端點：在業務交易一開始，以「飯店＋鍵」取交易版的 advisory lock（`pg_advisory_xact_lock`），在鎖內再查一次 `processed_ops`，有就直接回放；沒有就開單，並在**同一個交易**裡把回應寫進 `processed_ops`。這樣「單開好了」與「這把鍵已處理」一起提交、一起回滾；同一把鍵的第二個請求會等第一個結束，然後拿到它的結果。過濾器之後的寫入會因主鍵衝突而略過（`ON CONFLICT DO NOTHING`），不會覆寫。具體寫法交 `/api-plan`。
- **Rationale**:
  - 一次送出可能有十幾張單、含工務單號與急件推播，重複一次的代價比單張派單高得多；spec 對重複的要求是 0 次。
  - advisory lock 的寫法在本 repo 已有先例：`refactor/main_comments_and_workarounds` 分支的住客額度以 `pg_advisory_xact_lock` 鎖房間後再數（該分支 `RoomRequestRateLimiter.java`，尚未合進 main）。
  - 鍵跟著「內容」走，正好對上 spec 對「同一次送出」的定義（FR-036：內容沒改就算同一次）。後端明確拒絕時換新鍵，避免同一把鍵永遠回放上次的 404 或 403（過濾器會把小於 500 的結果都存起來）。
  - 只在這個端點加，不動其他端點的行為，影響面最小。
- **Alternatives considered**:
  - 只靠現有過濾器、不補縫：多數情況夠用，但「提交後寫紀錄失敗」與「慢網路下使用者重按、第一個請求還在跑」兩種情況會開出第二份，達不到 SC-007 的 0 次。
  - 以內容判重（同房號、同品項、幾分鐘內視為重複）：會擋掉櫃檯「真的要再加一份」的正常送出（FR-032：類似單只提醒、不擋）。
  - 在單上加一個「批次編號」欄位並設唯一限制：要新的 migration，重送時還得自己把當初的回應組回來；`processed_ops` 已經存了完整回應，不需要另起一套。

## 決策 4：類似單提醒的資料從哪裡來、在哪裡算

- **要決定什麼**: FR-030 要在房號、品項、其他需求一改變時立刻重算「同房號、還沒結束、對應到這次選的品項或是住客打字送出的單」。要知道每張單「對應哪個品項」「是不是住客打字的」。
- **後端現況**:
  - 看板資料已經涵蓋所有還沒結束的單（不限日期，`DeskBoardService.java:43` 的看板範圍），每張卡片有房號、狀態、標題、負責人、建立時間（`tms/src/main/java/com/athena/tms/dto/desk/DeskTask.java:6-35`），也已經有「是不是住客打字的」（`guestTyped`，由來源是住客且備註裡有原文推得，`tms/src/main/java/com/athena/tms/service/DeskTaskMapper.java:44`、`:102-120`）。
  - 住客勾選選單項目的單，備註 JSON 裡記著 `menu_key` 與數量（`tms/src/main/java/com/athena/tms/service/GuestRequestService.java:113-121`）；選單項目的鍵是中文品名，例如「毛巾」「礦泉水」（`tms/src/main/java/com/athena/tms/service/GuestTenantConfigReader.java:46-51`）。
  - 住客打字的單只記了比對到的分類規則編號 `rule_id`（`GuestRequestService.java:123-144`）；分類規則只有關鍵字、部門與類型，沒有對到哪個品項（`GuestTenantConfigReader.java:97`）。
  - 看板卡片目前沒有「對應的品項」這個欄位。
- **Decision**:
  - 在前端算：類似單直接從已經在 `TanStack Query` 快取裡、隨即時訊號更新的看板資料篩出來，房號、品項、其他需求一改就重算，不另外打 API。篩選時只取 `triage`、`pool`、`accepted`、`doing` 四種狀態（看板資料也含中斷、延後中與今天結束的單，要排除）。
  - 看板卡片加一個可為空的「對應品項鍵」欄位，由後端從備註推得，與 `guestTyped` 同一種做法：
    - 住客勾選的單：備註裡既有的 `menu_key`。
    - 櫃檯代開的單：開單時在備註寫入與住客相同格式的 `menu_key`（品項表的對照鍵）與數量，給其他需求的單不寫。
    - 住客打字的單：分類規則加一個選填的品項鍵（存在飯店設定的 JSON 裡，不需要改資料表），住客打字比對到有品項鍵的規則時，把它一併寫進備註的 `menu_key`。
  - 欄位名稱、是否放在看板卡片上、分類規則的設定形狀，交 `/api-plan`；品項表 12 項的對照鍵用住客選單的中文鍵（「毛巾」「礦泉水」「牙刷組」等），住客預設選單沒有的品項（浴巾、冷氣等）也照同一個命名方式取鍵。
- **Rationale**:
  - spec 要求「一改就立刻重算」（FR-030、驗收例 7），在前端用現成的快取算沒有網路延遲，也不用防抖。
  - 看板資料已經即時更新（004 決策 8），別人剛認領、剛完成的單會自動進出提醒，不用自己處理過期。
  - 「對應品項」記在備註 JSON 裡，沿用住客勾選單已有的格式，不需要新的資料表欄位或 migration；看板對照時讀同一個鍵。
  - 前端的比對是純計算，照 techstack 的做法用 `Vitest` 測，E2E 只驗幾個代表例。
- **Alternatives considered**:
  - 另開一個「查這間房的類似單」端點，每次改品項就打一次：每次變動都多一趟網路，與「立刻」的要求相反，而且看板上已經有這些資料。
  - 在 `tasks` 加一個「品項鍵」欄位：查詢較直接，但要新的 migration 並回填住客勾選單的舊資料；目前備註 JSON 已經足夠，等之後真的要在資料庫裡依品項統計時再改。
  - 住客打字的單用關鍵字即時比對品項（不改分類規則）：比對規則會在前後端各寫一份，容易不一致。

## 決策 5：房號是否有效、住客卡與建議房號的資料

- **Decision**:
  - 後端在 `/api/v1/desk/**` 底下提供一個讀取：這家飯店的房間清單，每間帶目前在住的住客（姓名、是否 VIP、入住與退房日期）；排除系統內部代表公共區域的那一間（`AutomationRuleService.PUBLIC_AREA_ROOM_NO`，見 `RepairService.java:565-583`）。確切形狀交 `/api-plan`。
  - 前端面板打開時讀一次並快取（`TanStack Query`），房號是否有效、住客卡、空房提示、建議房號（有住客、以打的字開頭、依房號由小到大取 6 筆）全部在前端算。
  - 送出時後端再檢查一次房號；面板打開後房號才被移除時，送出回 `not_found`，面板照 FR-040 顯示「不是這家飯店的房號」。
- **Rationale**:
  - 一家飯店的房間是幾十到幾百間，一次讀完的資料量很小，打字時不用等網路。
  - 房間清單與在住住客變動慢；就算快取稍舊，送出時後端仍會擋，驗收例 21 正好涵蓋這種情況。
  - 放在 `/api/v1/desk/**` 底下，三種櫃檯角色都能讀，也不會讓房務、工務讀到住客資料。
- **Alternatives considered**:
  - 每打一個字就打後端查詢：多了延遲與防抖處理，對幾百筆資料不划算。
  - 從看板資料推房號與住客：看板只含有單的房間，空房與沒有單的在住房間查不到。

## 決策 6：代開的單不算進住客的每房額度

- **Decision**: 櫃檯代開的流程不呼叫住客的每房額度檢查；額度只數住客送的需求。
- **Rationale**:
  - main 上的額度是記憶體裡的計數，只有住客送需求時才呼叫（`tms/src/main/java/com/athena/tms/service/RoomRequestRateLimiter.java:18-32`、`GuestRequestService.java:66-80`）；櫃檯代開不經過它，FR-037 在 main 上不用改程式就成立。
  - 但 `refactor/main_comments_and_workarounds` 分支（已 push、未合併）把額度改成從資料庫現數，條件是 `source IN ('guest', 'desk')`；spec 引用的就是這個版本。兩條線誰先合進 main，另一條就要跟著調整：該分支合併後，要把條件改成只數 `guest`。列入殘餘風險。
- **Alternatives considered**:
  - 代開的單照樣算進額度、只是不受它擋：住客會因櫃檯代開而少了能送的筆數，違反 FR-037 與 SC-011。

## 決策 7：送出成功後畫面怎麼立刻出現新卡片

- **Decision**: 寫入端點的回應帶回這次開出的每一張單的看板卡片（與看板讀取同一種形狀），前端收到後直接放進看板快取並高亮 2.6 秒；之後的即時訊號照 004 的做法換上同一張卡片。
- **Rationale**: FR-012 要求面板一關、新卡片就在「待認領」欄高亮；等即時訊號或重抓整份看板會有可見的空檔，而且高亮需要知道是哪幾張。004 的派單已經用「回應帶回整張卡片」的做法（`DeskBoardService.java:227-245`）。
- **Alternatives considered**:
  - 送出後重抓整份看板，再用房號與建立時間找出新卡片：多一趟網路，也可能把同時段別人開的單誤認成這次的。

## 決策 8：送出失敗與回應遺失在驗收裡怎麼重現

- **Decision**:
  - 網路中斷、沒有送到後端：Playwright 攔下這個 POST 直接中斷（`route.abort`），住客頁的驗收已有同樣做法（`app/web-bdd/src/test/java/com/athena/web/bdd/GuestScreenTourTest.java:345`）。
  - 後端其實已經開好、只是回應沒回來：Playwright 先把請求轉給真的後端（`route.fetch()`），拿到回應後再對瀏覽器中斷；看板驗收已有 `route.fetch()` 的用法（`app/web-bdd/src/test/java/com/athena/web/bdd/steps/realtime/BoardResponses.java:160`）。再送一次時，斷言回應是回放（`Idempotency-Replayed`）且資料庫裡只有一份。
  - 「開工務單那一步失敗」：測試在資料庫對 `repair_tickets` 裝一個只對這個情境的飯店丟出例外的暫時觸發器，情境結束時移除；斷言資料庫裡這次的一般單也不存在、流水號沒有前進、outbox 沒有這次的事件。後端 Cucumber 以 `JdbcTemplate`、櫃檯 Web 驗收以 `BackendChannel`（`app/bdd-support/`，直接連資料庫）裝與拆。
  - 角色被改掉後送出：直接改資料庫裡的帳號角色即可，後端每個請求都重讀帳號的角色（`tms/src/main/java/com/athena/tms/filter/BearerTokenAuthFilter.java:90-105`），不必等憑證過期。
  - 純計算（張數、摘要、單標題、房號輸入過濾、建議房號排序、類似單比對、多久前）用 `Vitest`。
- **Rationale**:
  - 觸發器讓失敗真的發生在資料庫、在同一個交易的中途，證明的是「真的回滾」，而不是「程式走了另一條分支」；正式程式不需要為了測試多開後門。
  - 回應遺失如果只用「中斷請求」模擬，後端根本沒收到，測不到「已開好再送要回放」這條最容易出錯的路。
- **Alternatives considered**:
  - 在正式程式加一個只在測試設定下生效的「讓第 N 張失敗」開關：要在開單路徑放測試用的分支，容易被誤開，也只能證明那條分支有回滾。
  - 讓工務單因資料不合法而失敗（例如故意送錯的修繕分類）：會在「先檢查」階段就被擋下、根本沒開第一張，測不到中途回滾。

## 後端為這一包要補的事（只列清單，交後續步驟）

| 項目 | 現況位置 | 交給 |
| --- | --- | --- |
| 櫃檯代開單的寫入端點（`/api/v1/desk/**`、強制冪等鍵、同一交易、回傳新卡片） | `DeskBoardController.java:48-75`、`DeskBoardService.java:227-280` | `/api-plan` |
| 開工務單抽成可帶參數的共用步驟（照片選填、來源 `desk`＋`desk_forward`、標題） | `RepairService.java:468-563` | `/api-plan` |
| 這個端點的交易內鎖鍵與交易內寫 `processed_ops` | `IdempotencyFilter.java:46-61`、`IdempotencyService.java:24-31` | `/api-plan` |
| 看板卡片的「對應品項鍵」、代開單在備註寫 `menu_key`、分類規則加選填的品項鍵 | `DeskTask.java`、`DeskTaskMapper.java:102-120`、`GuestRequestService.java:113-144`、`GuestTenantConfigReader.java:97` | `/api-plan`、`/data-plan` |
| 房間清單＋在住住客的讀取（排除公共區域） | `SecurityConfig.java:70`、`RepairService.java:565-583` | `/api-plan` |
| 住客額度只數 `guest`（待 refactor 分支合併時） | `RoomRequestRateLimiter.java`（refactor 分支版） | `/api-plan`、實作 |

## 建立功能前要先做的 spike

1. 同一把冪等鍵、兩個請求同時送進新端點，確認只開一批、第二個拿到回放。
2. 在 `repair_tickets` 裝暫時觸發器，送一次含房務與工務的代開，確認一般單、工務單、歷程、流水號、outbox 全部回滾；同時確認測試用的資料庫帳號有權限建、拆觸發器。
3. WebKit 設定下 `route.fetch()` 之後 `route.abort()`，確認前端收到的是網路錯誤（`NetworkError`），而後端確實已提交。

## 殘餘風險與後續驗證

- **住客額度的合併順序**（決策 6）：`refactor/main_comments_and_workarounds` 合進 main 時，額度條件要從 `source IN ('guest', 'desk')` 改成只數 `guest`；若漏改，FR-037、SC-011 會壞，`代開的單不算進住客的每房額度.feature` 會抓到。
- **住客打字對應品項要先有設定**（決策 4）：分類規則要有品項鍵，「住客打字但已對應到品項」的類似單才會出現（FR-030 第一條的第三種）。若 `/api-plan` 決定不擴充分類規則，`選品項時提醒這間房已有類似的單.feature` 中靠這種對應的例子無法通過，需回頭與使用者確認；這不改 spec 的行為，只影響資料怎麼來。
- **同一飯店的開單會短暫排隊**（決策 2）：流水號那一列的鎖持有到交易結束；一次最多十幾張單、毫秒級，預期無感，量測留給實作後的驗收資料集。
- **4xx 結果會被存起來回放**（決策 3）：前端在後端明確拒絕時換新鍵就不會碰到；若前端漏做，同一把鍵重送會一直回放上次的 404 或 403。`Vitest` 測「什麼情況保留鍵、什麼情況丟掉」。
- **代開的工務單照樣做急件判定**（FR-018）：房間今天有住客入住時，代開的工務單會被判成急件並推播給工務與主管；這是既有規則，櫃檯畫面照 004 不顯示急件。
- **憲法缺席**：`.agents/constitution/` 不存在，本文沒有對照任何憲法條文。
