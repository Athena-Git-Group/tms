# 櫃檯看板：訊號直接帶卡片 — 設計筆記

範圍：只改規格（`specs/truth/`），不含實作。程式碼引用以 `tms/src/main/java/com/athena/tms/` 為根，下文簡寫。

## 一、決定了什麼、為什麼

1. **訊號帶整張卡片，不帶欄位差異。** 卡片就是 `getDeskBoard` 的 `DeskTask`，由同一句查詢（`DeskBoardService.TASK_SELECT`）組成。差異需要畫面按順序一步一步套，漏一步就錯；整張卡片晚到、重複都只要比新舊。
2. **兩個步數，各管一件事。**
   - `seq`（已經有的欄位）＝觸發這則訊號的事件是第幾步。**只拿來抓漏掉的訊號**：比畫面記的步數多 2 以上就重讀整份看板。
   - `card_seq`（新增，等於 `card.seq`）＝卡片內容是第幾步的樣子，也就是後端組卡片當下讀到的 `tasks.event_seq`。**只拿來決定要不要換上**：比畫面記的大才換。
   - 為什麼要拆成兩個：後端是在 Kafka 收到事件之後才回頭讀資料庫組卡片，讀到的可能已經比這則事件新。例子：查房通過（第 N 步）和回寫 PMS（第 N+1 步）在同一個交易寫入（`service/InspectionService.java:92`、`:117`–`:121`），處理第 N 步的訊號時資料庫已經是第 N+1 步。如果只用一個「卡片步數」同時做比新舊和抓跳號，每次查房通過都會誤判成漏訊號、重讀整份看板。拆開後：第 N 步的訊號帶第 N+1 步的卡片 → 換上；第 N+1 步的訊號 → 卡片不比畫面新，不理，也不算跳號。
3. **`DeskTask` 加 `seq`。** 看板、單筆詳情、派單、復原、當場回答的回應都帶，畫面拿到哪一份都用同一條規則更新那張卡片。
4. **離開看板要明講。** `board` 有兩個值：`upsert`（帶 `card`）、`remove`（不帶 `card`）。看板範圍只寫在後端一個地方，組訊號時判斷，畫面不自己猜。
5. **帶 `business_date`。** 後端判斷看板範圍時用的「今天」。和畫面上的 `businessDate` 不同就重讀整份。
6. **整份重讀照常是保底。** 剛連上（`resync`）、恢復（`recovered`）、`seq` 跳號、營業日換了、訊號沒帶 `board` 或看不懂，都重讀整份。後端組卡片失敗時就不帶 `board`，畫面自動退回重讀，所以失敗不會讓畫面卡住。
7. **重讀和訊號同時發生時，新的贏。** 整份看板讀回來時，如果某張卡片在重讀期間已經從訊號拿到更新的步數（含已拿掉的），保留訊號那份。否則「訊號先到、比它舊的看板後到」會把畫面倒回舊狀態，而且之後不會再有訊號來糾正。
8. **房務、工務的連線不帶卡片**，維持只有訊號。理由：
   - `DeskTask` 是櫃檯視角（含住客姓名、VIP），這些角色呼叫 `getDeskBoard` 會被拒絕（403），串流不該從旁邊送出同樣的內容。
   - 他們的連線本來就只收自己部門或自己名下的事件，同一張單的 `seq` 天生會跳號（例如單從房務派到工務），跳號規則對他們沒意義。
   - 員工 App 目前沒有用這條串流（`app/mobile/lib` 沒有引用），沒有使用者需要它。
   - 住客串流與對接系統串流也共用 `broadcastTask`（`service/StreamConnectionRegistry.java:91`–`:96`），同樣不帶卡片。

實作提示（不寫進規格）：每則事件只組一次卡片（同一家飯店的櫃檯角色看到的卡片完全相同，沒有因人而異的欄位），而且只在該飯店有櫃檯角色的連線時才組；`board()` 的範圍條件（`service/DeskBoardService.java:154`–`:155`）抽成常數，看板和單張卡片共用。

## 二、程式碼調查

### 1. 每一次 `tasks.event_seq` 加 1，都會變成串流上的訊號嗎？

**不是全部。**

- `TaskEngine` 裡每一個加步數的地方都走 `recordEvent` → `outboxWriter.enqueueTaskEvent`（`service/TaskEngine.java:460`–`:471`，送出在 `:470`）。回寫 PMS 也一樣：`writebackRoomStatusIfEnabled` 在 `:248` 加步數、`:251` 走 `recordEvent`，**會**送出 `pms_writeback` 訊號。
- **例外：班末掃描。** `service/ShiftEndScanJob.java:76`–`:82` 把單退回待認領並加步數，`:89`–`:93` 直接 `INSERT INTO task_events`，**沒有寫 outbox**。所以班末退回今天在串流上完全沒有訊號：櫃檯看板不會跟著動，直到這張單下一次有變化或整份重讀。改成帶卡片後，下一則訊號還會被判成跳號。
- **修法（已寫進規格）**：班末掃描改成和其他轉換一樣寫 outbox（走 `OutboxWriter.enqueueTaskEvent`，或改走 `TaskEngine`）。契約寫明「每一筆 `task_events` 都會送出一則訊號，同一張單的 `seq` 不跳號」，並加了班末退回的後端場景。修好之後，櫃檯角色看到的跳號就只剩真的漏訊號，例如消費端處理失敗被略過（`service/TaskEventsSseConsumer.java:76`–`:78`）、查不到單（`:137`–`:140`）。這些情形重讀整份是對的。
- 不需要另外做「看板版本號」：櫃檯角色收得到全館每一則事件，跳號只看 `seq`，而且用的是事件步數，不是卡片步數（理由見第一節第 2 點）。
- **卡片內容會變、步數卻不變**的情形（今天的訊號也一樣抓不到，不是這次造成的，列出來供參考）：
  - `repair/RepairService.java:309`–`:313` 工務修好時改原單的停住原因（卡片 `interruption.reason`），沒有加步數。
  - 住客姓名、VIP 來自訂房快取，PMS 同步時沒有單的事件。
  - 清潔 SOP 總項數來自飯店設定。
  - 這些只能等這張單下一次有變化，或整份重讀時才會跟上。

### 2. 哪些單在看板上？單張卡片怎麼共用同一條規則？

- 規則在 `service/DeskBoardService.java:154`–`:155`：同一家飯店，而且 `ended_at` 是空的，或落在「今天」。「今天」用後端時鐘所在時區的日期（`:148`–`:150`）。
- 另外有一條隱藏條件：`TASK_SELECT` 用 inner join 接房間（`:71`），所以沒有房間的單（例如公共區域的排程單）永遠不在看板上。
- 單張卡片已經有共用的讀法 `requireTaskRow`（`:300`–`:305`）：同一句 `TASK_SELECT`，只差 `WHERE t.id = ?`，但它不檢查範圍。推送時用同一句 `TASK_SELECT`，加上同一段範圍條件和同一個「今天」：讀得到就是 `upsert`，讀不到（已經結束而且不是今天、沒有房間、查無此單）就是 `remove`。
- 因為「今天」會跟著時間變，昨天結束的卡片在午夜之後沒有事件也不會自己消失。後端送出的 `business_date` 換日了，畫面就會重讀；午夜後全館都沒有任何事件的情形，由畫面自己在換日時刻重讀處理（見第四節第 3 點）。
- 「今天」的精確定義：`OffsetDateTime.now(clock)` 換成 `clock.getZone()` 的日曆日期，`clock` 是 `Clock.systemDefaultZone()`（`config/ClockConfig.java:14`），也就是後端 JVM 預設時區，半夜 00:00 換日；飯店沒有自己的時區或換日時刻設定。`serverTime` 序列化時保留這個時區的時差，網頁已經從它尾端讀出時差（`app/web/src/stores/server-clock.ts:16`）。

### 3. 現在的契約，以及重讀間隔不一致

- `StaffStreamTask`：`specs/truth/contracts/components/schemas/員工事件串流.yaml`
- `DeskTask`：`specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml`
- `openapi.yaml:498`、`:516` 只是指過去，不用改。
- **不一致（已對齊，見第四節第 4 點）**：
  - 串流契約（`groups/現場作業/員工事件串流.yaml`「櫃檯 Web 的用法」）和 `getDeskBoard` 說明都寫「每 5 秒重抓一次」。
  - 網頁實際是 4 秒（`app/web/src/api/query-client.ts:14` `REREAD_INTERVAL_MS = 4_000`，`query-client.test.ts:7` 也這樣斷言）。
  - 網頁規格 `features/frontdesk-web/即時更新/dsl.md` 的說明有寫明「實際間隔略短（目前 4 秒），確保 5 秒內跟上」。
  - 所以是刻意的，只是兩份契約沒有寫到這一層。已照第四節第 4 點對齊。

### 4. 順序：同一張單會不會先收到第 5 步，再收到第 4 步？

- **一般情況下不會。** 同一張單的訊息在 Kafka 用同一把鍵 `task:<id>`（`service/OutboxWriter.java:38`、`service/OutboxRelay.java:128`）；relay 依 `aggregate_key, id` 排序（`:95`），同一把鍵前一筆送不出去就不送後面的（`:79`–`:90`）。每台後端用各自隨機的消費者群組，自己讀完整份事件（`TaskEventsSseConsumer.java:70`），預設只有一個處理執行緒，所以同一台後端對同一張單是按順序處理。客戶端只連一台，換台就是重連，會收到 `resync` 並重讀整份。
- **還是有可能發生：**
  - relay 送出成功但標記失敗時會重送（`OutboxRelay.java:121`–`:122`），Kafka 上就出現 4、5、4。消費端用「每張單送到第幾步」去重（`TaskEventsSseConsumer.java:132`、`:170`–`:180`），但那張表最多記 10000 張單、超過就丟最舊的（`:45`–`:51`）。被丟掉之後再收到晚到的重複訊號，就會再送一次。
  - 重讀整份看板和訊號互相交錯，也算一種「舊的晚到」。
- **為什麼無害：**
  - 晚到的重複訊號，卡片是送出當下現讀的，`card_seq` 不會比畫面舊，所以不理。
  - 它的 `seq` 比畫面記的小，不算跳號。
  - 重讀和訊號交錯，由第一節第 7 點處理。
- **順帶發現（今天就存在）**：推播 outbox 也用 `task:<id>` 當 `aggregate_key`（`service/OutboxWriter.java:46`；`PushOutboxWriter.enqueue` 由呼叫端傳入，例如未接提醒傳的就是 `task:<id>`）。relay 只要看到同一把鍵有一筆 `failed`，就整把鍵都不送（`OutboxRelay.java:95` 的 `NOT EXISTS ... failed`）。所以**某張單的推播重試用完之後，那張單之後的串流事件也全部停住**，畫面不會發現（沒有下一則訊號可以看出跳號）。推播改用自己的鍵，見第四節第 2 點。

## 三、改了哪些規格檔

- `specs/truth/contracts/groups/現場作業/員工事件串流.yaml`：操作說明（櫃檯角色帶卡片、每則事件都有訊號、「櫃檯 Web 的用法」整段改寫）
- `specs/truth/contracts/components/schemas/員工事件串流.yaml`：`StaffStreamTask` 加 `board`、`card_seq`、`business_date`、`card`；`seq`、`type` 說明補齊
- `specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml`：`DeskTask` 加必填的 `seq`
- `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml`：`getDeskBoard` 說明與 `x-realtime-note`
- `specs/truth/features/backend/員工事件串流/dsl.md`：說明、用語對照、新句型（Given 二句、When 一句、Then 四句；原有「收到訊號」那句拿掉「不帶單的內容」）
- `specs/truth/features/backend/員工事件串流/訊號帶的卡片.feature`（新）：帶卡片、房務工務不帶、不在看板上、步數連續、推播送不出去不擋訊號、班末退回也有訊號
- `specs/truth/features/frontdesk-web/即時更新/dsl.md`：說明、用語對照、新句型（Given 二、When 六、Then 四）；換日自己重讀；定時重讀 4 秒
- `specs/truth/features/frontdesk-web/即時更新/只換有變化的那張卡片.feature`（新）：直接換上不重讀、放上新卡片、重複或晚到不理、拿掉、跳號就重讀、沒帶卡片／看不懂／換日就重讀、重連就重讀、重讀不蓋掉新卡片
- `specs/truth/features/frontdesk-web/待派單/dsl.md`：一句等待條件從「重抓完成」改成「卡片已放上」
- `specs/truth/features/frontdesk-web/即時更新/營業日換了自己重新讀取.feature`（新）：午夜過後沒有任何變化，畫面自己重讀、昨天結案的卡片拿掉
- `specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml`：`DeskBoard.businessDate` 補換日規則
- `specs/truth/features/frontdesk-web/即時更新/連線異常提醒列.feature`、`總覽看板/載入與讀取失敗.feature`、`specs/truth/techstack.md`：定時重讀寫成 4 秒（畫面文案仍是 5 秒）
- `specs/truth/features/backend/班末掃描/dsl.md`：`班末掃描執行` 寫明事件同交易寫進待送匣
- `specs/truth/features/backend/推播/dsl.md`、`specs/truth/data/data-model.dbml`：推播用自己的排隊鍵（`push:` 開頭）

`連線異常提醒列.feature` 的「畫面只在收到即時訊號時才重新讀取看板，沒有每 N 秒定時讀取」句型不變（它檢查的是「沒有定時讀取」，現在仍然成立），只把參數從 5 改成 4。

## 四、決定

使用者對下列五點都回「照建議」。

1. **班末退回要有訊號，後端一起修。** `ShiftEndScanJob.requeueNotStarted`（`service/ShiftEndScanJob.java:76`–`:93`）寫 `task_events` 時同一個交易也要寫 outbox（走 `OutboxWriter.enqueueTaskEvent`，或改走 `TaskEngine`），每一筆事件都會送到串流。規格：
   - 契約 `groups/現場作業/員工事件串流.yaml`：「每一則事件都送出訊號」那段點名班末掃描。
   - `features/backend/班末掃描/dsl.md` 的 `班末掃描執行`：事件同交易寫進待送匣。
   - `features/backend/員工事件串流/訊號帶的卡片.feature`：班末退回的場景（原本就有）。
2. **推播用自己的排隊鍵，這次一起改。** 今天推播與單的事件共用 `task:<id>`（`service/OutboxWriter.java:52`，以及各處 `pushOutboxWriter.enqueue(tenantId, "task:" + …)`），推播重試用完就擋住那張單之後的事件。改成推播的 `aggregate_key` 一律以 `push:` 開頭（`push:task:<id>`、`push:staff:<id>`、`push:leave:<id>`），在寫入推播的共用入口加前綴即可，不必改每個呼叫端。規格：
   - `data/data-model.dbml` 的 `outbox.aggregate_key` 說明與不變條件。
   - 契約 `groups/現場作業/員工事件串流.yaml`：推播卡住不擋訊號。
   - `features/backend/推播/dsl.md` 說明一句。
   - `features/backend/員工事件串流/`：新 Given 句型與「推播送不出去不擋訊號」的場景。
   - 實作注意：Kafka 推播 topic 的分區鍵跟著變，同一張單的推播仍在同一個分區、順序不變；已在線上的 `failed` 推播列鍵名不會自動改，需要的話由人工處理。
3. **營業日換了，畫面自己重讀整份看板。** 不靠後端心跳，畫面從看板回應自己算：換日時刻＝`businessDate` 的隔天 00:00，時差照 `serverTime`；用後端時間校正後的現在去等；讀回來的 `businessDate` 還沒換就一分鐘後再讀一次；每讀到一份新的看板就重新算。規格：
   - 契約 `components/schemas/櫃檯看板與派單.yaml` 的 `DeskBoard.businessDate`（換日規則與畫面的算法）、`getDeskBoard` 說明與 `x-realtime-note`、`staffStream` 的「櫃檯 Web 的用法」第五條。
   - `features/frontdesk-web/即時更新/dsl.md` 說明與新 When 句型；新場景檔 `營業日換了自己重新讀取.feature`。
4. **重讀間隔照程式寫成 4 秒。** 連線異常時的定時重讀與讀取失敗後的自動再試都是 4 秒（`app/web/src/api/query-client.ts:14`）；畫面上的字「每 5 秒自動更新」「畫面每 5 秒會自動再試一次」不變，意思是資料最多落後 5 秒。規格改了契約兩處、`techstack.md` 兩處、`即時更新/dsl.md`、`連線異常提醒列.feature`、`總覽看板/載入與讀取失敗.feature` 的規則說明。「5 秒內 … 出現」這類跟上時限、畫面文案、派單復原的 5 秒，和員工 App（`features/ios-app/`）的 5 秒輪詢都不是這個間隔，沒有動。
5. **房務、工務的連線維持不帶卡片。** 照第一節第 8 點。之後員工 App 要接這條串流時，另外設計他們看的卡片形狀，不沿用 `DeskTask`。
