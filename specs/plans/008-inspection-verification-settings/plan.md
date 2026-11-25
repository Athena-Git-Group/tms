# 系統分析規劃：查房與驗收開關，住客的「已完成」跟看板的「已結案」一致

**Plan Package**: `specs/plans/008-inspection-verification-settings`
**Truth Root**: `specs/truth`
**Truth Delta**: `specs/plans/008-inspection-verification-settings/truth-delta.md`

讀者先讀 `spec.md` 的「為什麼這樣定？」。下面四件事都已經定了：住客頁改用看板的結束規則、兩個開關的生效時機、已經在等的單不動、評分跟著「已完成」走。本檔回答實作上的四個問題：結束的規則放在哪裡、設定 API 長什麼樣、開關的值怎麼存、工務單直接結案走哪一條程式路徑。最後列出程式現況的落差與可能出錯的地方。

`research.md` 不建立：本版沒有新技術。`ui/` 也不建立：設定頁沒有設計圖，只用既有元件，畫面規則全部寫在 `spec.md`「設定頁長什麼樣？」。

本檔引用的行號以分支起點 `25bfec0` 為準，以方法名稱為主。後端路徑省略前綴 `tms/src/main/java/com/athena/tms/`，網頁路徑省略前綴 `app/web/src/`。文中的「守衛 UPDATE」是任務引擎改狀態的寫法：一句帶著前一個狀態當條件的 UPDATE，條件不符就一列都不改，呼叫者拿到 409。

## 文件與程式會放在哪裡？

```text
specs/plans/008-inspection-verification-settings/
├── spec.md
├── checklists/requirements.md
├── plan.md                       # 本檔
├── truth-delta.md
└── tasks.md
```

```text
tms/src/main/resources/db/migration/V30__repair_verification_required_key.sql   # tenant_config.key 的 CHECK 加一鍵
tms/src/main/java/com/athena/tms/
├── controller/DeskSettingsController.java       # GET、PATCH /api/v1/desk/settings
├── dto/desk/DeskSettings.java                   # 兩個開關的回應
├── service/ManagerReviewSettings.java           # 讀寫兩個開關，唯一知道鍵名與「值相反」的地方
├── service/TaskExecutionService.java            # completeRepair：closeRepair 改看開關
├── service/TaskEngine.java                      # rate 的守衛加條件；complete 不動
├── entity/Task.java                             # 對應 ended_at 欄位（唯讀）
├── dto/GuestTaskView.java                       # 多帶 endedAt
└── config/SecurityConfig.java                   # /api/v1/desk/settings 只放 manager、duty_manager
app/web/src/
├── lib/awaiting-review.ts                       # awaitsReview：看板與住客頁共用的「還在等主管把關」
├── lib/board-columns.ts                         # done 分欄改用 awaitsReview
├── guest/api/types.ts                           # GuestTask 多 endedAt
├── guest/lib/stage.ts                           # done 還在等把關時是處理中；closed 可評分
├── lib/paths.ts、lib/route-access.ts、router.tsx   # 設定頁路由與角色
├── shell/unopened-entries.ts、shell/DeskShell.tsx # 「設定」改成真的入口
├── pages/settings/SettingsPage.tsx              # 兩個開關
└── i18n/locales/{zh-TW,en,ja,vi}.ts             # 設定頁文字
app/mobile/lib/features/ticket/repair_labels.dart 與工務單詳情、單列元件   # 「已結案 · 免驗收」
```

**結構決策**：不新增資料表或欄位。後端新增一個設定元件與一支控制器；網頁新增一頁和一個共用的判斷函式。

## 要動哪些系統介面？

本版共盤點出 8 個系統介面。

1. 後端：櫃檯設定。新的 `getDeskSettings`、`patchDeskSettings`，依據是 `spec.md`「設定怎麼讀、怎麼改？」。
2. 後端：工務單完修。`tasksComplete` 對修繕任務多看「需要工務主管驗收」，依據是「需要工務主管驗收怎麼運作？」。
3. 後端：打掃單開工。`tasksStart` 的免查鎖定規則不變，只是開關改由設定頁寫入；契約說明改成實際讀的鍵。
4. 後端：住客需求與評分。`GuestRequest` 多 `endedAt`、狀態補 `closed`；評分的守衛改成「已經結束」。
5. 資料：飯店設定。`tenant_config` 多 `repair_verification_required` 一鍵，V30 擴充 CHECK。
6. 住客頁：狀態對照表與評分卡。
7. 櫃檯 Web：側欄「設定」與設定頁。
8. 員工 App：工務單直接結案的說法。

## 結束的規則放在哪裡？

住客頁和看板要用同一條規則判斷結束，而這條規則今天只寫在 `lib/board-columns.ts:25-41` 的 `columnOf` 裡，跟看板欄位的分法綁在一起。

一種做法是在 `guest/lib/stage.ts` 照抄一份：`done` 而且 `endedAt` 有值就是 `COMPLETED`。兩份寫法今天一樣，但它們分別藏在看板欄位與住客對照表裡，下一個改規則的人只會看到其中一份。

本版的做法是把兩邊真正不一樣的那一格抽成一個函式。其他狀態本來就各自對到固定的欄或階段，唯一要看結束時間的是 `done`，所以共用的函式是 `lib/awaiting-review.ts` 的 `awaitsReview`：`state` 是 `done` 而 `endedAt` 是空的，就是還在等主管把關。看板的 `columnOf` 用它決定 `done` 放「已完成」欄還是「已結案」欄；住客頁的 `stageOf` 用它決定 `done` 是 `PROCESSING` 還是 `COMPLETED`。`STAGE_TABLE` 的 `CLOSED` 列刪掉，`closed` 直接對到 `COMPLETED`。評分照舊由 `canRate` 看階段：只有 `COMPLETED` 的 `ratable` 是 true，`CANCELLED` 是 false，再加上還沒評、沒有回覆，所以已取消的單不會出現評分卡。

後端不另外算「結束了沒」，只把 `tasks.ended_at` 原樣回給住客。寫入 `ended_at` 的仍然只有任務引擎進入終點的那一句守衛 UPDATE。評分的守衛直接寫在 SQL 條件裡：`state IN ('done', 'inspected', 'closed') AND ended_at IS NOT NULL AND rating IS NULL`。櫃檯當場回答的單後端跟以前一樣不擋，由住客頁不顯示評分卡。

代價是住客頁的呼叫端都要多傳 `endedAt`。`stageOf`、`canRate`、`showsProgress`、`messageBox`、`stepView`、`homeCardLine`、`latestActive`、`splitSections`、`homeRequestsSection` 的參數型別都要含 `endedAt`，TypeScript 會把漏傳的地方全部標出來。

## 設定 API 為什麼是 PATCH /desk/settings，不沿用租戶設定 API？

後端已經有 `PUT /api/v1/tenant-configs/{key}`（`controller/TenantConfigAdminController.java:24-41`）。直接沿用有三個問題。第一，`SecurityConfig.java:58` 只放 `manager`，值班主管進不去，而使用者要值班主管也能改。第二，它把原始的鍵名與值交給網頁，網頁就得知道「需要房務主管查房」存在 `inspection_waived_enabled` 而且值相反，這種知識不該出現在畫面程式裡。第三，它不檢查值，什麼都存成 JSON 字串。

另一種做法是新開一支整包 `PUT /desk/settings`，一次送兩個開關。問題是兩位主管同時開著設定頁、各改一個不同的開關時，後送出的那一位會把前一位剛改的開關蓋回舊值。

本版用 `PATCH /desk/settings`：body 只帶要改的開關，沒帶的不動。兩個開關分別寫各自的 `tenant_config` 列，兩位主管改不同開關時寫的是不同列，不會互相蓋掉。回應永遠帶兩個開關的現值，畫面用它更新。路徑放在 `/api/v1/desk/**` 底下，再在 `SecurityConfig` 為這一支單獨只放 `manager`、`duty_manager`，排在 `/api/v1/desk/**` 那一條前面。

PATCH 的 body 寫的是結果（開或關），不是切換，重送同一份 body 結果相同。所以它不需要防重送的鍵（`Idempotency-Key`），櫃檯其他寫入都要帶。代價是「沒帶」與「帶 null」要分清楚：沒帶是不改，null 是格式錯誤、回 422。控制器要先讀成 JSON 樹，看欄位在不在、是不是布林，不能直接綁到兩個可為 null 的 `Boolean`。

## 開關的值怎麼存？

兩個開關都存在 `tenant_config`，一個開關一列，值是 JSON 的 `true` 或 `false`，`updated_by` 記呼叫者。讀的時候用 `value #>> '{}'` 取字串，所以舊資料存成 JSON 字串 `"true"` 也讀得懂；這跟 `TaskEngine.tenantConfigText`、`service/SelfInspectionSetting.java` 的讀法相同。

| 畫面開關 | 鍵 | 沒設定時 | 畫面上的開 |
| --- | --- | --- | --- |
| 需要房務主管查房 | `inspection_waived_enabled` | 需要查房 | 鍵不是 `true` |
| 需要工務主管驗收 | `repair_verification_required` | 需要驗收 | 鍵不是 `false` |

「需要房務主管查房」沿用既有鍵，存的值跟畫面相反。另開一個 `inspection_required` 鍵會讓兩個鍵描述同一件事，開工時要決定聽哪一個。沿用既有鍵的代價是畫面與資料差一個「反過來」，所以只讓 `ManagerReviewSettings` 知道這件事。`TaskEngine.start`（`service/TaskEngine.java:190`）照舊讀 `inspection_waived_enabled`，不改。

「需要工務主管驗收」是新鍵。`tenant_config` 的 key 有 CHECK 限制，最近一次在 `V27__self_inspection_allowed_key.sql`，所以要新增 `V30__repair_verification_required_key.sql`：照 V27 的寫法 DROP 再 ADD `chk_tenant_config_key`，在 V27 的清單後面加 `repair_verification_required`。只動 CHECK，不回填資料，沒有列就是「需要驗收」。

`ManagerReviewSettings` 負責兩件事。一是讀兩個開關並換成畫面的布林值。二是收到 PATCH 時只寫有帶的鍵，以 `INSERT … ON CONFLICT (tenant_id, key) DO UPDATE` 寫入。工務完修讀「需要工務主管驗收」也呼叫它，不在 `TaskExecutionService` 另寫一句查詢。

## 工務單直接結案走哪一條路？

`TaskEngine.complete` 已經有一條直接結案的路。第五個參數 `closeRepair` 為 true 時，同一句守衛 UPDATE 把修繕任務寫成 `closed` 並寫 `ended_at`（`service/TaskEngine.java:210-217`）。今天只有「不需處理」走它：`TaskExecutionService.completeRepair`（`service/TaskExecutionService.java:213-224`）傳的是 `resolution.closesOnComplete()`，只有 `NO_ACTION_NEEDED` 是 true（`repair/RepairResolution.java:7-10`）。

一種做法是在 `RepairResolution` 加第五種結果「免驗收的已修好」。可是結果是工務選的，開關是飯店的，兩者混在一起，完修結果欄位就分不出「工務說修好了」和「飯店不驗收」，暫時處理、無法修復也各要再複製一種。

本版只改傳進去的值：`verificationWaived = !resolution.closesOnComplete() && !managerReviewSettings.repairVerificationRequired(tenantId)`，`closeRepair = resolution.closesOnComplete() || verificationWaived`。`verificationWaived` 為 true 時，`completed` 的 payload 多放 `verificationWaived: true`。之後的 `repairService.resolveOnComplete` 不動，所以照片綁定、處理說明、原任務停住原因、推播報修人、暫時處理建追蹤單、無法修復改房況全部照舊。`verified_by`、`verified_at` 本來就只有驗收通過時寫，不必處理。主管待驗收清單（`state = 'done'` 且完修結果不是不需處理）也不用改，直接結案的單從來不會停在 `done`。

代價是事件紀錄要多看一個欄位才能還原狀態：重放 `completed` 時，`resolutionType` 是不需處理或 `verificationWaived` 為 true 都是 `closed`，其他是 `done`。改寫重放邏輯的人要知道這兩個條件。

## 設定頁怎麼接進櫃檯畫面？

- `lib/paths.ts` 加 `settings: '/settings'`。
- `lib/route-access.ts` 多一個判斷「這個角色能不能看設定」，只有 `manager` 與 `duty_manager`；`router.tsx` 在 `DeskShell` 底下加設定頁路由，其他角色導回總覽看板。
- `shell/unopened-entries.ts:15` 刪掉「設定」那一列；`DeskShell` 的側欄改成畫一個真的連結，沿用總覽看板與待派單兩個連結的 `aria-current` 寫法，只對兩種主管角色畫。
- `DeskShell` 的頁首目前用 `pathname.endsWith(PATHS.triage)` 二選一（`shell/DeskShell.tsx:30`），要擴成三頁：設定頁的頁首只有標題「設定」，沒有副標與搜尋房號；工具列的「代開單」跟其他頁一樣保留。
- 設定頁用 TanStack Query 讀 `GET /desk/settings`。改開關時送一次 PATCH，成功時用回應覆蓋快取；失敗時把開關退回，用既有的 `components/AlertDialog.tsx` 顯示「沒有儲存」。讀取失敗沿用 `components/LoadFailed.tsx`，載入中沿用 `components/SkeletonBlock.tsx`。
- 開關本身沒有既有元件，用原生的 `<button role="switch" aria-checked>`，外觀沿用既有的按鈕樣式。

## 程式現況跟本版差在哪裡、由誰補？

| # | 落差 | 現況 | 歸屬 |
| --- | --- | --- | --- |
| 1 | 住客讀不到結束時間 | `dto/GuestTaskView.java:7-10` 只有八欄；`entity/Task.java` 沒有對應 `ended_at` 的欄位 | 後端：`Task` 加唯讀的 `endedAt`，`GuestTaskView` 帶出；契約 `GuestRequest.endedAt` |
| 2 | 住客頁只看狀態 | `guest/lib/stage.ts:115-126` 的 `STAGE_TABLE` 把 `done` 對到 `COMPLETED`、`closed` 對到不可評分的 `CLOSED` | 住客頁 |
| 3 | 評分收等驗收的 `done`、不收 `closed` | `service/TaskEngine.java:411-416` 的條件是 `state IN ('done', 'inspected') AND rating IS NULL` | 後端 |
| 4 | 驗收沒有開關 | `service/TaskExecutionService.java:213-224` 只看完修結果 | 後端；資料 V30 |
| 5 | 免查房開關沒有畫面，值班主管也改不了 | 只有 `PUT /api/v1/tenant-configs/{key}`，`SecurityConfig.java:58` 只放 `manager` | 後端新端點；櫃檯 Web 設定頁 |
| 6 | 契約與資料模型寫錯免查房依據的鍵 | `TaskEngine.start` 讀 `inspection_waived_enabled`（`service/TaskEngine.java:190`）；契約 `tasksStart` 與 `data-model.dbml` 的 `tasks.inspection_waived` 寫的是 `inspection_waived_cleaning_types`，這一鍵後端沒有任何地方讀 | 契約、資料模型，見 `truth-delta.md` |
| 7 | 資料模型說免查決定在退回重做時保留 | `TaskEngine.start`（`service/TaskEngine.java:185-191`）每次開工都依當下的開關重寫 `inspection_waived`；查房與抽查退回都把單送回 `accepted`，重做時會再開工一次 | 資料模型照程式改寫，見 `truth-delta.md` |
| 8 | 住客需求的契約缺 `closed` | `components/schemas/住客掃碼.yaml` 的 `GuestRequest.state` 只列九個值 | 契約 |
| 9 | 「設定」是點不進去的入口 | `shell/unopened-entries.ts:15` | 櫃檯 Web |
| 10 | 員工 App 把所有 `closed` 當成不需處理 | 契約 `TaskCompletion` 寫「closed 是以不需處理直接結案」；`app/mobile/lib/features/ticket/repair_labels.dart` 只有「已結案 · 不需處理」與「已結案 · {主管}驗收」兩種說法 | 契約說明；員工 App |

## 哪裡可能出錯？

- **員工 App 的處理待核對**：員工 App 對工務單直接進 `closed`（不是不需處理）的處理，在 `tasks.md` 的員工 App 核對那一題確認。
- **與 007 的合併衝突**：007 代開單（`feature/main_frontdesk_create_request`，還沒合進 main）已經改了 `specs/truth/features/frontdesk-web/共用外框/尚未開放的入口.feature` 與 `共用外框/dsl.md`，拿掉「代開單」；它的 `tasks.md` 也排了改 `shell/unopened-entries.ts` 與 `DeskShell.tsx`。本版在同幾個檔拿掉「設定」。兩邊合併時要保留兩邊的刪除；文字衝突可以手動解，行為不衝突。
- **後端既有的評分情境會紅**：既有後端或網頁測試若以「`done` 的工務單可以評分」或「`closed` 不能評分」當前提，評分守衛一改就會失敗。這些失敗是預期的，跟著 `truth-delta.md` 的 MODIFY 改，不能為了讓它們綠而放寬守衛。
- **關掉查房連退房房都不回寫 PMS**：這是既有規則的後果，`spec.md` 已寫進設定頁說明與「還有什麼沒定？」。真實飯店上線前要讓產品負責人確認。
- **既有的值可能是 JSON 字串**：`TenantConfigAdminController` 把值存成 JSON 字串（`to_jsonb(?::text)`），測試支援的 `BackendChannel.setTenantConfig` 則存 JSON 值。讀取一律用 `value #>> '{}'` 就兩種都讀得懂；新寫入一律存 JSON 布林。
- **重做時免查會重新決定**：查房或抽查退回重做的打掃單，重新開工時照當下的開關再決定一次。主管關掉查房後，被退回的單重做完會變成免查結案。這是程式現況，本版照寫；產品若要退回的單一律再查，要另外改 `TaskEngine.start`。
