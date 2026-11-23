# 006 計畫審查：派給工務的單一律是工務單

**判定**：`ISSUES: 12`（blocker 1、major 5、minor 6）

**審查對象**：`spec.md`、`plan.md`、`tasks.md`、`truth-delta.md`、四份 acceptance feature，以及本分支未提交的 `specs/truth/**` 改動。

**依據**：`bugfix/main_engineering_pool_repair_ticket` 的原始碼（2026-11-23），不是計畫裡的說法。

使用者的目標是讓核心流程在工務這一段走得通：住客送出、櫃檯派單、同仁在 App 認領、做完、主管驗收。做法已經定了：櫃檯派給工務時後端開一張工務單，讓 2026-11-11 的驗收裁決也管到這些單。整體方向沒問題。單一保證點這個做法站得住，因為正式程式碼裡會把單寫成工務部門的路確實只有計畫列出的那兩條。下面的問題集中在三處：兩份前端 truth 被計畫誤判成 NOOP、V29 補舊資料的細節，以及測試基準與回歸範圍不完整。

---

## Blocker

### B1. 住客頁與櫃檯 Web 的 truth 依賴「命中工務規則直接進工務待認領」，truth-delta 卻記成 NOOP

**證據**

- `specs/truth/features/guest-web/設備報修/組成文字送出.feature:69-88` 有一條 Rule「分類規則命中時這張直接進該部門的待認領」。底下兩個場景都拿工務當例子，期待「冷氣不冷 | 工務待認領」，以及進度頁顯示「已受理」「工務」「工務收到」。這與 FR-013 正面衝突。
- `specs/truth/features/guest-web/設備報修/dsl.md:12` 這句的 fixture 定義是「文字含該關鍵字時分到 engineering、類型修繕」。
- `specs/truth/features/frontdesk-web/待派單/待派單清單.feature:148-153`（「直接進部門待認領的需求不會出現在待派單頁」）的 When 句在 `frontdesk-web/待派單/dsl.md:21` 寫著「兩張都直接進部門待認領」。
- 這句的 stepdef 在 `app/web-bdd/src/test/java/com/athena/web/bdd/steps/triage/TriageWhenSteps.java:106-123`。它設的分類規則是 `dept=engineering`，`requireInPool` 會在單沒進池時丟例外。它還會等頁首「工務 N+1 單待接」（同檔 `:75-83`）。
- `frontdesk-web/待派單/待派單清單.feature:55` 的頁尾文字「勾選單和命中規則的文字會直接進部門待認領」，碰到工務之後就不成立了。
- `truth-delta.md:61` 把 `guest-web/**`、`frontdesk-web/**` 記成「已檢查，不需修改」。
- 補充：005 住客 Web 的 truth 是最近才定的（`aab4283` 之前那幾個 commit），而且明確選了「命中工務直接進工務待認領」。2026-11-05 的「住客不直通工務」裁決，跟 005 寫下的行為互相矛盾。

**後果**：T039 跑 `guest-web/ phone` 與 `待派單` 一定會多出新的失敗。T001 只量了後端與 `flutter test`，T039 又要求「失敗在改動前就存在」，所以這幾個失敗沒有地方可以被接受。

**對計畫的修法**

1. 交付前請使用者確認：005 的那條 Rule 要被「住客不直通工務」蓋過。spec.md「假設」裡要點名 005 的這條 Rule。
2. truth-delta 新增 `/dsl-refine` 的 MODIFY 列，涵蓋下面三處：
   - `guest-web/設備報修/組成文字送出.feature:69-88`：改成命中工務時停在待派單，狀態標籤「已送出」，部門「櫃檯」。或者把例子換成命中房務的規則。
   - `guest-web/設備報修/dsl.md:12`：同步修改。
   - `frontdesk-web/待派單/dsl.md:21`：寫清楚命中的是房務規則。
3. tasks.md 新增兩題，放在 Phase 3 的 BDD-ALIGN：
   - `TriageWhenSteps.classificationRule()` 改成 `housekeeping`／`amenity`，同時拿掉等「工務 +1」的那一行。
   - 住客 Web 對應句的 stepdef 跟著改。
4. `待派單清單.feature:55` 的頁尾文字要改，否則畫面說的是錯的。或者在 spec「不改的地方」裡寫明這次決定不改，並請使用者確認。

---

## Major

### M1. V29 第 2 步退回待派單的住客單沒有 T- 單號，櫃檯看板的 `displayNo` 會是 null

**證據**

- 實際的分類規則 `r-aircon` 設定成 `"type": "repair"`（`tms/src/main/resources/db/migration/V2__seed_dev.sql:47`）。
- `ClassificationRuleEngine.route` 會沿用規則的 type（`ClassificationRuleEngine.java:26`）。
- `TaskEngine.create` 遇到 `type=repair` 就不取 T- 號：`String taskNo = "repair".equals(command.type()) ? null : ...`（`TaskEngine.java:77`）。所以住客經規則直接進工務的單，`task_no` 是 NULL。
- V29 第 2 步把這些單改成 `type=other` 後，`DeskTaskMapper.java:33` 會讀 `task_no`，得到 null。
- 但契約裡 `DeskTask.displayNo` 是必填字串（`specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml:69,96`）。
- acceptance `已經卡在工務的單上線後補好.feature` 假設這張單叫「T-0030」，實際資料不會是這樣。

**修法**

- plan.md「V29 修補舊資料」第 2 步加一句：`task_no IS NULL` 的單照 V25 的做法，從 `task_counters` 依 `(created_at, id)` 接續取號。沒有計數列的飯店要用 upsert 建出來。
- T036 的測試加一條斷言：退回待派單的單有 T- 單號，而且跟既有號碼不撞號。
- 往後的新單不受影響：FR-013 讓住客單一開始就是 `type=other`，建單時就會取號。

### M2. V29 第 2 步沒寫事件，分類也太寬

**證據**

- dbml 的 `tasks` Note 寫明「重放……結果與線上的任務列一致」。這些單的 `created` 快照是 `pool`／`engineering`／`repair`。V29 把任務列改成 `triage`／`frontdesk`／`other`，卻沒有寫任何 `task_events`，也沒有推進 `event_seq`。從事件重放就還原不出現在的樣子。
- 櫃檯詳情的歷程會出現一張「建立就在工務待認領」、現在卻在待派單的單，中間沒有任何轉移。
- outbox、Kafka 與事件串流的下游也收不到這次改動。
- 第 1、2 步用來認「住客經規則直接進來」的條件只有「沒有 triaged、部門工務、沒有工務單」，沒有檢查 `source='guest'`。萬一有一張 source 是 staff、desk 或 system 的孤兒工務部門單（例如手寫 SQL 塞進去的），它會被默默搬進櫃檯的待派單。

**修法**

- 第 2 步的條件加上 `t.source = 'guest'`。
- 第 1 步的檢查改成：凡是部門工務、沒結束、沒有工務單，而且「不是 guest 經規則進來，也不是 triaged 派來」的單，就 `RAISE EXCEPTION`。
- 二選一：
  - 每張被退回的單補一筆事件（`actor_role='system'`，例如 `triage_undone`，payload `{"dept":"engineering","reason":"guest_not_direct_backfill"}`），並同句推進 `event_seq`。
  - 或者在 dbml 的重放段落明寫「V29 退回的單是例外」，並在 spec 記下歷程看不到這次轉移。
- T036 要驗所選的那一種。

### M3. 復原沒有把急件狀態收乾淨，復原後重派的急件推播會被吃掉

**證據**

- T025 要 `open` 寫入 `tasks.urgent`（「與判定一致」）。T027 與 dbml 的復原段落只把 type 還原、刪掉工務單，`urgent` 沒動。
- 待派單的單一律以非急件建立。所以復原後這張單在待派單裡帶著 `urgent=true`。若接著改派給房務，`poolRows` 會照 `urgent DESC` 排序（`TaskExecutionService.java:524-545`），房務待認領就多出一張置頂的急件。
- 急件推播的防重複鍵是 `repair_urgent:{taskId}:{recipient}`（`RepairService.java:754`），走的是 `enqueueIfAbsent`（`PushOutboxWriter.enqueue`）。同一張任務復原後再派給工務，開出的新 W- 單的急件推播會被當成重複而丟掉。第一則推播與通知中心裡寫的是已經撤掉的舊單號。
- `judgeUrgent` 的規則是「這間房今天有人入住」（`RepairService.java:164-190`）。當天入住的住客報修幾乎都會觸發急件，所以這不是罕見情況。

**修法**

- plan.md「undoTriage」與 T027：撤工務單時同句把 `urgent` 設回 false。或者在 `triaged` payload 多記 `former_urgent`，復原時還原。
- `RepairTicketOpener` 的急件推播防重複鍵改用工務單號：`repair_urgent:{ticketNo}:{recipient}`。
- `派出後復原.feature` 加一條 Then：復原後不是急件。T018 的 stepdef 也要查 `tasks.urgent`。

### M4. T026 讓 `TaskEngine.create` 沒帶草稿就丟錯，但測試 fixture 都不帶草稿，計畫裡沒有一題處理

**證據**

下列測試碼直接用 `engine.create(new TaskCreateCommand(... "engineering" ...))` 建工務部門的單，沒有工務單草稿：

- `tms/src/test/java/com/athena/tms/bdd/support/DeskBoardFixtures.java:185-208`，部門由 `:432` 決定（`repair` 一律是 engineering）。`看板範圍內外有以下的單：` 帶「修繕」列時會走到這裡，例如 `backend/櫃檯看板與派單/櫃檯看板讀取.feature:26`。
- `tms/src/test/java/com/athena/tms/integration/stepdefs/when/StreamWhenSteps.java:32,55`。
- `tms/src/test/java/com/athena/tms/service/TaskEngineRowWriteTest.java:118`。

T026 的 Test Scope 只有 `櫃檯派單.feature` 加上 `工務單/` 模組，碰不到這些 fixture。它們會到 Phase 4C（T029）或 T038 才炸，看起來就像跟本題無關的失敗。

**修法**

- Phase 2 加一題：盤點 `tms/src/test` 裡所有用 engineering 建單的 fixture，改成帶草稿。
- 或者讓 fixture 改走 `RepairTicketOpener`，跟正式程式同一條路。
- T026 的 Test Scope 擴大成後端全套，包括單元測試。

### M5. 基準與回歸的範圍不完整，T039 和 T040 要求的「改動前基準」沒有任何一題去量

**證據**

- T001 只跑 `-Dtest=RunTruthFeaturesTest` 加 `flutter test`。
- 後端另外還有 `integration/RunCucumberTest.java`、`relaywake/RunOutboxRelayWakeTest.java`，以及一般單元測試（例如 `service/TaskEngineRowWriteTest.java`）。
- T039、T040 都寫「失敗在改動前就存在」才可以接受，但它們排在 Phase 5，那時程式已經改了。
- T039 只跑 `待派單`、`總覽看板`、`單筆詳情`。實際上派給工務的場景還在 `frontdesk-web/共用外框/支援的視窗與輸入方式.feature:89`、`總覽看板/拖曳派單.feature:56-82`、`總覽看板/等待膠囊與逾時.feature:69`。事件串流送出的卡片 type 也改了，所以 `即時更新/` 也受影響。

**修法**

- T001 改成在改動前量齊下列四組，結果記進 `implement-notes.md`：
  - `./tms/mvnw -f tms/pom.xml test`：全部測試類別，不加 `-Dtest`。
  - `bash app/web-bdd/run-feature.sh --keep frontdesk-web/ desktop`。
  - `bash app/web-bdd/run-feature.sh --keep guest-web/ phone`。
  - `app/bdd/run-module.sh 工務單` 與 `app/bdd/run-module.sh 待認領`。
- 程式已經開始改了的話，就在 HEAD 的乾淨 worktree 量。
- T038 到 T040 用同一組指令，比對同一份基準。

---

## Minor

### m1. T041 的 `up.sh --build` 會失敗

`scripts/manual-env/up.sh:37` 在 repo 根目錄跑 `./mvnw`，但根目錄沒有這支，只有 `tms/mvnw`，而且還要設 JDK 25 的 `JAVA_HOME`。

修法有兩種：

- 修 `up.sh`（它是本分支新增、還沒進版控的檔案），改用 `./tms/mvnw` 並設好 `JAVA_HOME`。
- 或者 T041 先自己打包：`JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./tms/mvnw -f tms/pom.xml -DskipTests package`，再跑 `up.sh --keep-data`。

### m2. 誰把 type 改成 repair，計畫兩處寫法不一致

- plan.md「唯一保證點」寫的是 `assignFromTriage`「同一句更新把任務類型改成 `repair`」。
- T025 寫的是 `open` 要「同句把 `tasks.type` 設為 `repair`、`tasks.urgent` 與判定一致」。

修法：只留一處。建議 type 跟著 `assignFromTriage` 的守衛 UPDATE 一起改，因為那句本來就是條件更新。`open` 只負責取號、寫 `repair_tickets`、寫 `urgent`、送急件推播。

### m3. V29 第 3 步的防呆不夠

- 有的飯店沒有 `repair_ticket_counters` 列，「推到用掉的最後一號」要寫成 upsert。
- `triaged.actor_id` 若為 NULL，會撞上 `reporter_id NOT NULL`，整個應用就啟動不了。應該在第 1 步檢查並帶筆數 `RAISE`。
- 上線順序要寫進 T041 與 spec：舊版本必須先停。Flyway 在啟動時執行，如果舊版本還在服務，有人在第 1 步之後認領，第 5 步的自我檢查就會讓應用起不來。

### m4. 住客看得到的退步要在交付時講清楚

- V29 第 2 步會讓正在看頁面的住客，從「已受理 · 工務收到」退回「已送出 · 櫃檯」。
- 修繕單的 `done` 在 `app/web/src/guest/lib/stage.ts` 的 `STAGE_TABLE` 裡對到 COMPLETED，而且可以評分。所以等驗收的工務單，住客已經看到「已完成」並能評分；驗收不通過時又會退回「已受理」或「處理中」。
- spec.md:228 已經寫了這件事，但以前住客的單派給工務後是 `type=other`，做完就結束。對住客來說這是新的行為，不是既有行為。
- 修法：spec 的「對既有行為的影響」改寫成「新行為」，並列入交付時請使用者確認的事項。

### m5. 櫃檯派給工務時，工務同仁不會收到「有新單」的推播

- `DeskBoardService.assign` 不發任何推播。只有 `GuestRequestService.java:71-74` 與 `AutomationRuleService.java:187-189` 會呼叫 `notifyPoolNewRequest`。
- FR-013 生效後，住客打「冷氣」這條路連原本那則推播也沒了。除非碰到急件，工務只能自己去看待認領。
- `ios-app/推播/點開推播的去處.feature:50`（「櫃檯轉單 · 202 窗簾掛勾鬆脫」）只是 Given 裡的假資料，後端沒有任何路徑會發這則推播。
- 修法：在 spec 的範圍外清單明寫這是已知缺口，或者另開一題，派單時對該部門發 `pool_new_request`。

### m6. 分類規則仍然可以建出「沒有單號、也沒有工務單的 repair」

如果飯店把某條規則設成 `dept=housekeeping`、`type=repair`，`TaskEngine.java:77` 會建出 `task_no` 為 NULL、也沒有工務單的修繕單，櫃檯看板的 `displayNo` 同樣是 null。

修法：在 FR-013 那一段，把住客路徑的 type 也收斂起來：命中的 type 是 repair 時一律改成 `other`。或者在 spec 裡記成已知限制。

---

## 查過、沒問題的部分

1. **路徑盤點（plan.md 那張表）是完整的。** 我在 `tms/src/main/java` 全文找了 `UPDATE tasks`、`SET dept`、`setDept(`、`INSERT INTO tasks`、`new TaskCreateCommand(`、`assignFromTriage(`，也找了 `tms/src/main/resources` 底下所有不在遷移目錄裡的 SQL，結果如下：
   - 會寫 `dept` 的只有 `TaskEngine.java:113`（派單）與 `:121`（復原，寫成 frontdesk）。
   - 建任務只有 `TaskEngine.create` 的 JPA `saveAndFlush`（`:87`）。
   - 呼叫 `create` 的有四處：`GuestRequestService.java:70`；`AutomationRuleService.java:159`（部門寫死 housekeeping，而且 `V6:17-18` 的 CHECK 不允許 repair）；`RepairService.java:357`；`RepairService.java:555`。
   - 其餘的 UPDATE（請假、班末、主管重新排入、解除暫緩、驗收不通過、改派）都不改部門。
   - 計畫沒有漏掉任何路徑。單一保證點在正式程式碼裡成立。繞過它的只有測試 fixture（見 M4）和 `scripts/seed-demo.sh`。seed 我也查了：每張工務部門的單都有工務單（`seed-demo.sh:198-235,279-290`），而且計數推到 110。
2. **復原刪工務單時不會撞外鍵。** 唯一指向 `repair_tickets` 的外鍵是 `fk_repair_tickets_origin_ticket`（`V17:33-35`）。照片與貢獻者的外鍵指向 `tasks`。復原守衛要求 `event_seq` 等於最新一筆 triaged 的 seq，這保證派出後沒有任何事件（補照片、降級、認領都會推進 seq）。`tms_app` 有 DELETE 權限（`V5:223`）。單號跳號在 contract 與 dbml 裡已經寫明。
3. **主管待驗收佇列（003）會接到這些單。** `ManagerQueueService.java:81` 以 inner join 讀 `repair_tickets`。`scheduled` 只在 `source=system` 而且沒有原單時才成立，所以 guest 來源的單會正常顯示報修人。
4. **櫃檯看板的 T- 改成 W-。** `DeskTaskMapper.java:33` 依 type 決定顯示哪一個號碼。復原會推進 `event_seq`，畫面依 seq 換上新卡片。
5. **復原時限。** 後端是 10 秒（`DeskBoardService.java:40`），畫面上只出現 5 秒，計畫寫的是 10 秒，兩者一致。
6. **同一把防重複鍵重送。** `filter/IdempotencyFilter.java` 確實存在，重送拿第一次的回應這個說法有實作支撐。
7. **遷移編號。** 我查了本機所有分支與遠端分支，沒有其他 V29 到 V39。tasks.md 明訂不動 V1 到 V28。
8. **App 單卡的修法。** `eng_pool_screen.dart:235-248`：`ticketNo == null` 就不畫，類別讀的是 `item.category`（任務類型）。計畫的修法對準了這兩點。

---

## 第二輪（2026-11-23，只驗證修正，不改計畫）

**判定**：`PASS`。第一輪 12 件全數修掉，沒有剩下的 blocker 或 major。新發現 2 件 minor，不擋 `/implement`。

**審查對象**：同第一輪，加上修正者改過的 plan package 與 `git diff -- specs/truth`（21 個 truth 檔加 1 個新 feature）。

### 第一輪 12 件的處理

| 編號 | 修了嗎 | 落點 |
| --- | --- | --- |
| B1 | 是 | `spec.md:28-29,243` 點名 005 的 Rule 並記下 2026-11-23 使用者確認；`truth-delta.md:66-73` 補 guest-web、frontdesk-web 的 MODIFY 列；`guest-web/設備報修/組成文字送出.feature:70,80` 拆成兩條 Rule，期待「待派單」「已送出」「櫃檯」；`frontdesk-web/待派單/待派單清單.feature:55,148-152` 改頁尾與房務例子；`tasks.md:156-167`（T028–T031）改 stepdef 與共用測試資料；`tasks.md:358`（T048）改四個語系的頁尾。 |
| M1 | 是 | `plan.md:174` 第 2 步依 `(created_at, id)` 從 `task_counters` 接續取 T- 號、沒有計數列先 upsert；`data-model.dbml:174` 的 `task_no` note 跟著改；acceptance `已經卡在工務的單上線後補好.feature:7,15-16` 改成「沒有單號 → T-0031」；`tasks.md:335-336`（T046）斷言取號接續、不撞號、計數列建出。 |
| M2 | 是 | `plan.md:168-173` 只認兩種單（櫃檯派來的要 `actor_id` 有值，住客的要 `source='guest'`、沒有 triaged、在 `pool`），其餘 `RAISE EXCEPTION`；`plan.md:174` 補 `triage_undone`（system）、同句推進 `event_seq`、寫 kind `task_event` 的 outbox，兩把鍵對得上 `OutboxWriter.java:30-45`；`data-model.dbml:291-294` 記下這個例外；`tasks.md:335,340` 驗事件、outbox 與三種停下的情況。 |
| M3 | 是 | `plan.md:142-145`：triaged payload 記 `former_type`／`former_urgent`，復原同句還原；急件防重複鍵改成 `repair_urgent:{ticketNo}:{recipient}`；`派出後復原.feature` 新增兩條 VIP Rule，含「急件標記回到 否」與「W-0622 照樣推播」；`tasks.md:126-128,143,233`（T011、T022、T036）。 |
| M4 | 是 | `tasks.md:198-206`（T034）列出所有繞過開單步驟的 fixture。我用 grep 核對了 `tms/src/test` 裡 11 處 `new TaskCreateCommand(` 與 3 處 `INSERT INTO repair_tickets`，沒有漏掉的工務部門呼叫點。T034 排在 T035（`create` 開始丟錯）之前、同一輪做。`tasks.md:194` 從 T035 起 Test Scope 是後端全套。 |
| M5 | 是 | `tasks.md:43-53`（T002）在任何產品碼或測試碼改動前量五組，包含後端全套不加 `-Dtest`、`frontdesk-web/`、`guest-web/`、App 兩模組、`flutter test`；`tasks.md:364-369`（T049–T051）用同一組指令比對同一份基準。 |
| m1 | 是 | `tasks.md:370-374`（T052）改用 `stop.sh`，再跑 `JAVA_HOME=… ./tms/mvnw -f tms/pom.xml -DskipTests package`，再跑 `start.sh`，不碰 `up.sh`。我實跑過 `./tms/mvnw -f tms/pom.xml` 加 JDK 25，可以解析出 `tms-0.0.1-SNAPSHOT`，與 `env.sh:24` 的 `SOURCE_JAR` 一致。 |
| m2 | 是 | `plan.md:142` 與 `tasks.md:188,197` 一致：type 只在 `assignFromTriage` 的守衛 UPDATE 改，`open` 不碰 `tasks.type`。 |
| m3 | 是 | 兩個計數表都用 upsert（`plan.md:174-175`）；`actor_id` 為 NULL 在第 1 步就停（`plan.md:173`）；上線順序寫在 `plan.md:179`、`spec.md:238` 與 `tasks.md:372`。 |
| m4 | 是 | `spec.md:234-238` 改成「新行為（交付時請使用者確認）」，寫明住客端會從「已完成」退回，以及上線當下會從「已受理 · 工務收到」退回。 |
| m5 | 是 | `spec.md:37` 與 `plan.md:184` 列為已知缺口，另開一題。 |
| m6 | 是 | FR-013 加上「命中的類型是修繕」（`spec.md:168`）；`plan.md:164`；`tasks.md:251` 補 `housekeeping`＋`repair` 的單元測試。 |

### 一致性核對

- truth 改動與 truth-delta 對得上：`git diff` 動到的 21 個 truth 檔加新檔 `backend/工務單/櫃檯轉來的工務單.feature`，每一個都有對應列。MODIFY 列點名的檔都存在。
- tasks.md 從 T001 到 T053 連號，沒有缺號。題目之間的交叉參照（T002→T049–T051、T030→T039、T031→T033、T034→T035、T043→T034）都指到正確的題。
- tasks.md 引用的 stepdef 位置我抽查了 12 處，例如 `DeskCommonSteps.java:80,103,110`、`DeskWhenSteps.java:133`、`RepairWhenSteps.java:246`、`RepairThenSteps.java:177,203`、`RepairGivenSteps.java:72`、`api_client.dart:1360`、`zh-TW.ts:363`、`01-guest-requests.feature:104-119`、`start.sh` 的 proxy 段。每一處都落在計畫說的句型或程式碼上。
- V29 與 schema 的核對結果如下。
  - `task_events.actor_role` 允許 `system`，`actor_id` 可以是 NULL（`V1:256-258`）。
  - `task_events.type` 沒有設 CHECK。
  - `outbox.kind` 允許 `task_event`（`V1:276-277`）。
  - 「沒結束」以外的狀態值和 `chk_tasks_state` 吻合（`V23:32-33`）。
  - `repair_tickets` 的必填欄位（`room_id`、`category`、`reporter_id`、`ticket_no`、`created_at`）都有來源。
  - 種子遷移 `V2` 沒有建任何任務，所以乾淨資料庫跑 V29 會是空操作。
  - Postgres 上的 Flyway 遷移在同一個交易裡執行，所以 `RAISE` 之後不會留下改到一半的單。
  - V1–V28 沒有被改動，`tasks.md:325` 也明文禁止。

### 新發現（minor）

**n1. 事件重放規則沒有涵蓋 `urgent` 與 `priority_notify`，T046 的重放斷言也沒有指定要比哪些欄位。**

- `data-model.dbml:289` 的重放規則只寫到 type 和 dept。它沒寫 `urgent`：櫃檯派給工務時，`open` 會改 `tasks.urgent`，但 triaged 的 payload 只記 `former_urgent`，不記這次判定出來的值；復原時要還原 `former_urgent`，這一步也沒寫進重放規則。
- `plan.md:174` 的 V29 第 2 步會把 `priority_notify` 設回 false，重放規則同樣沒有這一項。
- `tasks.md:335`（T046）要求「照 dbml 的重放規則從事件重放得出同樣的任務列」。但 repo 裡沒有任何重放程式（`tms/src` 裡的 replay 都是 Idempotency 重送），實作者只能自己猜要比哪些欄位。
- 修法：
  - 在 dbml 的重放段補上：派給工務時 `urgent` 取 `repair_tickets.is_urgent`，或者在 triaged 的 payload 多記一個 `urgent`；復原時 `urgent` 設回 `former_urgent`；V29 寫的 `triage_undone` 會把 `priority_notify` 設為 false。
  - T046 寫明要比的欄位：`state`、`dept`、`type`、`task_no`、`pool_source`、`reminder_stage`、`priority_notify`、`urgent`。

**n2. T052 在停掉舊版之前，沒有先用唯讀查詢預檢 V29 會不會停下。**

- `tasks.md:372-374` 的順序是先停舊版、再打包、再起新版。如果 `tms_manual` 裡有 V29 歸不了類的單（例如住客的單被主管改派而停在 `accepted`），新版就起不來。這時 `start.sh` 已經用新 jar 蓋掉了執行用的那份，手動環境會一直停著，要等改資料或重新打包舊版才能恢復。
- 修法：在備份之後、`stop.sh` 之前，加一步唯讀 `psql`，對 `tms_manual` 跑 V29 第 1 步的分類查詢。結果不是 0 筆時，先處理掉那些單再上線。

### 稽核與解析

- 拓樸稽核（`.claude/skills/gherkin-and-dsl/scripts/audit_feature_dsl_topology.py`）的結果如下。
  - `frontdesk-web` 通過。
  - `backend` 有 3 個 error、`ios-app` 有 6 個、`guest-web` 有 1 個，全部在本輪沒動到的檔。
  - 我用 `git archive HEAD` 取出改動前的版本重跑，error 清單完全一樣，所以都是既有問題，本輪沒有新增。
- Gherkin 解析：用 `@cucumber/gherkin` 實際 parse 並 compile 11 份 feature，全部成功。
  - 7 份 truth：`櫃檯派單` 16 個場景、`櫃檯看板讀取` 21、`派出後復原` 14、`待派單清單` 16、`組成文字送出` 9、`工務視角` 28、`櫃檯轉來的工務單` 5。
  - 4 份 acceptance：2、2、4、2。
