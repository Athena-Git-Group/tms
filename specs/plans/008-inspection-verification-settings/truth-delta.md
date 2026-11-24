# Truth Delta: 008-inspection-verification-settings

**Plan Package**: `specs/plans/008-inspection-verification-settings`

**Truth Root**: `specs/truth`

> `/api-plan` 與 `/data-plan` 兩段是 `specs/truth/contracts/**` 與 `specs/truth/data/data-model.dbml` 的改動。`/dsl-refine` 段列每一份 feature 與 DSL 要改的地方；「預期改動」欄寫的是要寫成什麼樣子，寫 feature 的人照著改。
> 本版的預期影響總表見 `spec.md`「哪些既有行為會改？」。

## /technical-research

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| NOOP | `specs/truth/techstack.md` | 已檢查，不需修改。後端、櫃檯 Web、住客頁、員工 App 的框架與測試線都不變；設定鍵沿用新增一支 Flyway 遷移擴充 CHECK 的既有做法。 | 本版沒有引入新技術。 |

## /api-plan

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `specs/truth/contracts/groups/營運管理/櫃檯設定.yaml` -> `getDeskSettings`、`patchDeskSettings`（`/desk/settings`） | 讀與改兩個開關。PATCH 只改有帶的開關、回兩個開關的現值；兩個都沒帶或值不是布林回 422 `validation_failed`；只放 `manager`、`duty_manager`，其他回 403 `forbidden`；不需要 `Idempotency-Key`。說明寫明兩鍵的存法與值相反的那一鍵。 | spec「設定怎麼讀、怎麼改？」；plan「設定 API 為什麼是 PATCH」。 |
| ADD | `specs/truth/contracts/components/schemas/櫃檯設定.yaml` -> `DeskSettings`、`DeskSettingsPatch` | 兩個布林欄位 `inspectionRequired`、`repairVerificationRequired`，各自寫明沒設定時是 true、生效時機與存在哪一鍵。`DeskSettingsPatch` 以 `anyOf` 要求至少帶一個開關，其他欄位不理會。 | 同上。 |
| MODIFY | `specs/truth/contracts/openapi.yaml` | 新增 tag「櫃檯設定 API」、path `/desk/settings`、兩個 schema 的索引。 | 同上。 |
| MODIFY | `specs/truth/contracts/components/schemas/住客掃碼.yaml` -> `GuestRequest` | 新增必填、可為 null 的 `endedAt`，說明結束規則與看板相同、評分也看它；`state` 補上 `closed` 並加說明；描述寫明住客頁用的 `/guest/requests` 回這裡全部欄位，另外多 `type`、`dept`、`rating`。 | spec「一張單什麼時候算結束？」；契約原本少了 `closed`，也沒寫兩條路徑的欄位差別。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/任務.yaml` -> `tasksStart` 說明 | 免查鎖定依據從 `inspection_waived_cleaning_types` 改成實際讀的 `inspection_waived_enabled`，即設定頁的「需要房務主管查房」；寫明不分打掃種類，退回重做的單重新開工時再決定一次。 | `TaskEngine.start` 讀的是 `inspection_waived_enabled`，每次開工都重算；`inspection_waived_cleaning_types` 後端沒有任何地方讀。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/任務.yaml` -> `tasksComplete` 說明 | 飯店關掉「需要工務主管驗收」時，三種要照片的完修結果直接 `closed`、寫結束時間、不進待驗收、驗收人留空、完成紀錄帶 `verificationWaived: true`；開關在按完成時讀；追蹤單與房況照做。 | spec「需要工務主管驗收怎麼運作？」。 |
| MODIFY | `specs/truth/contracts/groups/現場作業/任務.yaml` -> `tasksRate` | 新增說明：只在已經結束、狀態是 `done`、`inspected` 或 `closed` 時收評分，其他回 409 `state_conflict`；櫃檯當場回答的單後端不擋；住客頁用的 `/guest/requests/{id}/rating` 同規則。請求與回應形狀不變。 | spec「住客什麼時候可以評分？」。 |
| MODIFY | `specs/truth/contracts/components/schemas/任務.yaml` -> `TaskCompletion` 說明、`CompleteTaskRequest.resolutionType` 說明 | `closed` 不再只代表不需處理：App 看自己送出的完修結果分辨不需處理與免驗收；三種要照片的結果在免驗收時也直接 `closed`。 | 舊說明叫 App 把所有 `closed` 寫成「已結案 · 不需處理」，免驗收時會寫錯。 |
| MODIFY | `specs/truth/contracts/components/schemas/工務單.yaml` -> `RepairTicket` 說明、`verifiedBy` 說明 | 多一種已結案：其他完修結果而驗收人是空的，是免驗收結案。 | 讓三種視角分得出驗收通過與免驗收。 |
| MODIFY | `specs/truth/contracts/components/schemas/租戶設定.yaml` -> `tenant_config.key`、`tenant_configRequest.key` | 鍵集補上 `inspection_waived_enabled`、`repair_verification_required`，以及原本漏列的 `pms.writeback.room_status`、`pms.provider`、`unclaimed_reminder_minutes`、`notification_retention_days`；標明 `inspection_waived_cleaning_types` 後端不讀；`tenant_configRequest` 的鍵集改成指向 `tenant_config.key`，不再複製一份。 | 讓鍵集與 V30 之後的 CHECK 一致；兩份清單各改各的會走樣。 |
| NOOP | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `verifyRepairTicket`；`groups/現場作業/主管工作台.yaml`；`components/schemas/主管工作台.yaml` | 已檢查，不需修改。`closed` 的單本來就回 409；免驗收的單完成時不會停在 `done`，待驗收的條件不必改。 | spec「已經直接結案的工務單不能再驗收」。 |
| NOOP | `specs/truth/contracts/components/schemas/櫃檯看板與派單.yaml` -> `DeskTask.endedAt`、`DeskTaskDetail.repair` | 已檢查，不需修改。看板的分欄規則就是住客頁要對齊的規則；免驗收的工務單照「沒有驗收人、也不是不需處理」的既有寫法顯示「已結案」。 | spec「這份規格假設了什麼？」。 |

## /data-plan

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| MODIFY | `specs/truth/data/data-model.dbml` -> `tenant_config.key` note | 鍵集補上 `inspection_waived_enabled`（未設定或不是 true 視為需要查房，設定頁的反向開關）、`repair_verification_required`（未設定或不是 false 視為需要驗收，按完成時讀，V30 加進 CHECK）、`pms.writeback.room_status`、`pms.provider`（V8 起就在 CHECK 裡，note 漏列）；`inspection_waived_cleaning_types` 標明後端不讀。 | 讓 note 的鍵集與 V30 之後的 CHECK 一致；spec「設定怎麼讀、怎麼改？」。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `tasks.inspection_waived` note | 鎖定依據改成 `inspection_waived_enabled`，只有打掃單會是 true，不分打掃種類；「退回重做時保留原鎖定值」改成重做的單重新開工時依當下的開關重寫。 | 與 `TaskEngine.start` 的實際行為一致：每次開工都重算。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `tasks.state`、`tasks.ended_at`、`tasks.rating` note | `closed` 多一個來由（飯店不需要工務主管驗收）；`ended_at` 寫明看板「已結案」與住客「已完成」共用同一條結束規則；`rating` 寫明只在已經結束、狀態是 `done`、`inspected`、`closed` 時寫入，櫃檯當場回答的單後端不擋。 | spec「一張單什麼時候算結束？」「住客什麼時候可以評分？」。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `tasks` Note 的 `doing→closed` 與待驗收段 | `doing→closed` 多一個條件：以三種要照片的結果完修而完成那一刻 `repair_verification_required` 為 false；寫明開關不鎖在任務上、已在 `done` 的單不受影響、追蹤單房況推播照做；待驗收段補上免驗收的單不會停在 `done`。 | spec「需要工務主管驗收怎麼運作？」。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `task_events.payload` note | 工務單的 `completed` 以三種要照片的結果完修、而飯店不需要工務主管驗收時另帶 `verificationWaived=true`（不需處理不帶），這一筆就是結案事件，重放時依它把狀態設為 `closed`。 | 事件是真相，重放要還原得出 `closed`。 |
| MODIFY | `specs/truth/data/data-model.dbml` -> `repair_tickets.verified_by` note、`repair_tickets` Note 的狀態列與完修四選一段 | 免驗收結案時驗收人與驗收時間為 NULL；三種要照片的結果在免驗收時同一交易直接 `closed`。 | 同上。 |
| NOOP | `specs/truth/data/data-model.dbml` -> 所有資料表的欄位、索引、Ref | 已檢查，不需修改。沒有新增或刪除欄位；`tenant_config` 的 CHECK 在 dbml 只以 note 表示，實際由 V30 擴充。 | 開關存成 `tenant_config` 一列一鍵，不需要新欄位。 |

## /dsl-refine

### 後端

| 動作 | Truth 規格 | 預期改動 | 依據 |
| --- | --- | --- | --- |
| ADD | `specs/truth/features/backend/櫃檯設定/查房與驗收開關.feature`、`backend/櫃檯設定/dsl.md` | 新模組：沒設定過兩個都是開；主管改一個開關另一個不受影響；值班主管也能改；關掉的可以再打開；櫃檯人員讀不到也改不了；沒帶開關或值不是布林被拒且設定不變；記下最後修改的人。新句型 `"{員工}" 已在櫃檯設定把「{設定}」設為 "{開關}"` 給 `查房` 與 `工務單` 模組共用，要真的打 `PATCH /desk/settings`。 | spec 情境 4 的後端部分。 |
| MODIFY | `specs/truth/features/backend/查房/免查房鎖定與結案.feature`、`backend/查房/dsl.md` | 新增兩條 Rule：主管在櫃檯設定關掉需要房務主管查房後才開工的單，完成即免查結案；把需要房務主管查房打開後才開工的單，完成後要等查房。`dsl.md` 新增 `"{員工}" 開始了 "{任務}" 的續住整理`（不動開關，依當下設定鎖定）。既有的鎖定與不回寫 PMS 的 Rule 不動。 | spec 情境 3。 |
| MODIFY | `specs/truth/features/backend/工務單/櫃檯轉來的工務單.feature`、`backend/工務單/dsl.md` | 新增 Rule：等主管驗收的單住客讀到還沒結束、工務單在待驗收清單；關掉需要工務主管驗收後完修即結案、不進待驗收清單；開工後才關掉，完修當下照新設定結案；住客要等需求結束才能評分（等驗收時被拒、驗收通過後可以、免驗收結案後可以）。既有「驗收通過後結案」補「住客讀到這張需求已結束」。 | spec 情境 1、2、5 的後端部分。 |
| MODIFY | `specs/truth/features/backend/工務單/完修與回到原任務.feature` | 新增 Rule：不需要工務主管驗收時，完修照樣通知報修人、原任務的停住原因照樣改為工務已修好。 | spec 情境 2 的 5。 |
| MODIFY | `specs/truth/features/backend/工務單/工務視角維修與開單.feature` | 新增兩條 Rule：不需要工務主管驗收時，暫時處理照樣建立追蹤單、原單直接結案；無法修復照樣把房況轉為停用待確認、原單直接結案。 | spec 情境 2 的 3、4。 |
| MODIFY | `specs/truth/features/backend/工務單/主管驗收的狀態守衛.feature` | 新增一條 Rule：免驗收結案的單不能再驗收，照「以不需處理結案的單不能再驗收」的寫法。 | spec 情境 2 的 8。 |
| NOOP | `specs/truth/features/backend/主管工作台/**` | 已檢查，不需修改。待查房、待驗收清單的條件不變；「不在待驗收清單」由 `工務單` 模組的情境斷言。 | 待驗收只看 `done`。 |

### 住客頁

| 動作 | Truth 規格 | 預期改動 | 依據 |
| --- | --- | --- | --- |
| MODIFY | `specs/truth/features/guest-web/dsl.md` -> 需求的狀況對照 | 新增兩種狀況：「完成等查房」（打掃單 `done`、`ended_at` 為空）與「已完修等驗收」（修繕單 `done`、`ended_at` 為空），顯示處理中、進行中、鎖定說明、不可評分。「已結案」改成可評分。「已完成」的後端樣子不變（`done` 且 `ended_at` 有值）。 | spec「一張單什麼時候算結束？」。 |
| MODIFY | `specs/truth/features/guest-web/我的需求/狀態對照表.feature` | 清單與進度頁兩個表各加「完成等查房」「已完修等驗收」兩列：標籤處理中、段落進行中、走到處理中、底部鎖定說明。場景名從「十種狀況」改成「十二種狀況」。 | spec 情境 1 的 1、2。 |
| MODIFY | `specs/truth/features/guest-web/我的需求/狀態訊息框.feature` | 新增場景大綱：完成了還在等主管查房或驗收的單沒有「已完成」訊息框。 | spec 情境 1。 |
| MODIFY | `specs/truth/features/guest-web/我的需求/進度頁內容與四步副文.feature` | 新增兩個場景：打掃完等查房、修好等驗收，四步進度和進行中一樣，「已完成」那一步是空心、副文「完成後可以給我們評分」。 | spec 情境 1 的 2。 |
| MODIFY | `specs/truth/features/guest-web/即時更新/同仁一動畫面跟上.feature`、`guest-web/即時更新/dsl.md` | 新增 Rule：打掃與修繕完成後還要等主管查房或驗收，主管通過後進度頁才變「已完成」並出現評分卡。`"{員工}" 在手機上{動作} "{標題}"` 的動作加上查房通過、驗收通過。 | spec 情境 1 的 4。 |
| MODIFY | `specs/truth/features/guest-web/取消與評分/評分.feature`、`guest-web/取消與評分/dsl.md` | Rule「主管驗收過的工務單只顯示「已完成」，不出現評分卡」改成「主管驗收通過、已結案的工務單也可以評分」；新增 Rule「完成了還在等主管查房或驗收的單還是「處理中」，不出現評分卡」。`進度頁有評分卡` 的必查改成後端是 `done`、`inspected` 或 `closed` 且 `ended_at` 有值。 | spec「工務單驗收過之後，住客為什麼可以評分？」、情境 5。 |
| NOOP | `specs/truth/features/guest-web/我的需求/清單兩段與空狀態.feature`、`備品補充/**`、`設備報修/**` | 已檢查，不需修改。用到「已完成」的例子都是備品單，完成當下就結束，顯示不變。 | 備品單的 `done` 一定有 `ended_at`。 |

### 櫃檯 Web

| 動作 | Truth 規格 | 預期改動 | 依據 |
| --- | --- | --- | --- |
| ADD | `specs/truth/features/frontdesk-web/設定/查房與驗收開關.feature`、`frontdesk-web/設定/dsl.md` | 新模組：按「設定」打開設定頁、頁首只有標題；兩個開關、名稱與說明照 spec；沒設定過都是開；切換後重新打開仍是新值；兩位主管各改一個；儲存失敗退回並顯示「沒有儲存」；櫃檯人員直接輸入網址回到總覽看板；四種語言、兩種裝置。 | spec 情境 4、「設定頁長什麼樣？」。 |
| MODIFY | `specs/truth/features/frontdesk-web/共用外框/尚未開放的入口.feature` | 刪掉 Rule「「設定」點了不會展開，也不會開任何頁面」；Rule「只有主管與值班主管的側欄上有「設定」」搬到同模組的 `側欄與工具列.feature`，句型仍在 `共用外框/dsl.md`。「通知板」「遺失物」「代開單」照舊。 | 「設定」不再是尚未開放的入口。007 也改這份檔（拿掉「代開單」），合併時兩邊的刪除都要保留。 |
| MODIFY | `specs/truth/features/frontdesk-web/共用外框/側欄與工具列.feature` | 收下上一列搬來的 Rule。 | 同上。 |
| MODIFY | `specs/truth/features/frontdesk-web/單筆詳情/狀態標籤與狀態訊息卡.feature` | 修繕單的例子加一列「已結案（免驗收）」：狀態標籤「已結案」、狀態訊息卡「已結案」。 | spec「這份規格假設了什麼？」；把既有的顯示釘住。 |
| NOOP | `specs/truth/features/frontdesk-web/總覽看板/**` | 已檢查，不需修改。`done` 有結束時間、`inspected`、`closed` 本來就在「已結案」欄。 | 看板規則就是本版要對齊的規則。 |

### 員工 App

| 動作 | Truth 規格 | 預期改動 | 依據 |
| --- | --- | --- | --- |
| MODIFY | `specs/truth/features/ios-app/工務單/dsl.md`、`ios-app/工務單/工務視角.feature`、`ios-app/工務單/工務單房務端生命週期.feature` | `dsl.md` 的三種視角對照與 `工務單詳情顯示狀態摘要卡` 句型加「已結案 · 免驗收」：`closed`、完修結果不是不需處理、`RepairTicket.verifiedBy` 為 null。兩份 feature 的已結案例子各加一列免驗收結案。 | spec 本版做的員工 App 一條；`tasks.md` T019。 |
