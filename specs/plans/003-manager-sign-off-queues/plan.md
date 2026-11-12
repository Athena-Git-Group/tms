# 系統分析規劃

## 專案結構

### 文件結構（本功能）

```text
specs/plans/003-manager-sign-off-queues/
├── spec.md                  # /specify ＋ /clarify-over-specs：FR-001 至 FR-041、對既有行為的影響表、要退掉的實作範圍
├── plan.md                  # 本檔
├── truth-delta.md           # 由 truth owner 透過 /truth-delta 追加；本輪各 owner 依新版 spec 重寫自己的段落
├── checklists/
│   └── requirements.md
├── features/
│   └── acceptance/          # /spec-by-example 產出，6 支驗收旅程（plan-side，非 truth）
├── ui/                      # Wave 1 /ui-plan 產出：ui-plan.md 與靜態雛形（plan-side，非 truth）
└── tasks.md                 # /tasks 產出

specs/truth/                 # 系統真相（由各 truth owner 更新）
├── contracts/               # Wave 2 /api-plan：收回審核三支端點與相關欄位、驗收加不通過、完修加不需處理、
│                            #   主管待辦拿掉待審核並依主管種類回類別、新增驗收不通過推播事件值
├── data/                    # Wave 1 /data-plan：工務單狀態值集、結案狀態、驗收不通過紀錄、完修結果新值、
│                            #   事件與通知值集、主管待辦判定段（必要時 Wave 3 第二趟）
└── features/
    ├── backend/             # /dsl-refine：工務單、主管工作台、推播三個模組依影響表 MODIFY／DELETE
    └── ios-app/             # /dsl-refine：主管工作台、工務單、推播、系統層、待認領依影響表 MODIFY
```

### 原始碼結構（儲存庫根目錄）

```text
tms/
├── src/main/java/com/athena/tms/
│   ├── repair/
│   │   ├── RepairController.java            # approve／reject／resubmit 三支要收回；verify 要加不通過
│   │   └── RepairService.java               # 開單去向、核准退回補資料、resolveOnComplete、驗收、repair_rejected 推播
│   ├── controller/
│   │   ├── ManagerQueueController.java      # getManagerQueues 入口
│   │   └── InspectionApiController.java     # createInspections 入口
│   └── service/
│       ├── ManagerQueueService.java         # 三類待辦（含要收回的待審核）
│       ├── InspectionService.java           # 查房結果與「原清房者接不接得了」的換人判定
│       ├── TaskEngine.java                  # 狀態轉移守衛與 task_events 寫入
│       └── TaskExecutionService.java        # 今日進度、待認領；多處把 done 當作已結束
├── src/main/resources/db/migration/         # Flyway；V17、V22 加入的審核狀態與事件值以新 migration 收回，不改舊檔
└── src/test/java/com/athena/tms/bdd/        # 後端 BDD step definitions

app/
├── mobile/lib/
│   ├── core/                                # api_client（ReviewQueueItem、resubmitRepairTicket、rejectReason 要退）、推播去處
│   ├── features/login/                      # 登入與強制改密後的首頁分流（已依角色分流）
│   ├── features/shell/                      # 四分頁外殼
│   ├── features/manager/                    # 主管首頁、主管清單、查房、主管待認領、本機記住部門
│   ├── features/ticket/                     # 工務單詳情（房務端、工務端、主管動作）、完修回報、報修表單
│   ├── features/pool/、features/engineering/ # 員工待認領與工務首頁
│   └── features/notification/               # 通知中心的事件分類
└── bdd/src/test/java/                       # Appium ＋ Cucumber-JVM，驗 ios-app feature

scripts/seed-demo.sh                          # 示範資料：拿掉等主管審核的 202、新增工務主管 mg2、補不需處理的結案單
```

**結構決策**: 工作樹裡已有一套含主管審核的主管工作台實作與對應 truth，所以這一輪不是從零設計，而是在現有實作上「收回一段、補上一段」。收回的是整條主管審核：核准、退回、補資料再送出三支端點，`pending_review` 與 `rejected` 兩個狀態值，`reject_reason` 欄位，`repair_rejected` 事件與推播，`reviewRequestedAt` 欄位，以及 App 上的待審核格、待審核清單與核准退回按鈕。補上的是驗收的另一半與兩條新規則：驗收不通過（附原因、退回原處理人或工務待認領、推播原處理人）、驗收通過改為進入新的結案狀態、完修結果「不需處理」直接結案、主管分房務與工務兩種、工務主管首頁只有待驗收一格、查房只開放給房務主管。保留不動的是首頁依角色分流、四分頁外殼、待查房清單與查房畫面、主管待認領切換部門。資料層是這一輪的變動核心：既有 truth 與實作是「驗收不開新狀態值」，現在要開一個，而且要收回兩個，所以狀態值集與轉移必須先在資料層定案，契約才有東西可以對齊。

## 分析流程規劃

### 系統介面的盤點

本次需求共盤點出 `11` 個系統介面：手機端 6 個、資料 2 個、後端 3 個。每個介面標出它對既有實作是「保留」、「退掉」還是「新增」；多數介面兩者兼有。

1. `進 App 的首頁分流與主管種類判定介面`
   - 端點類型：`手機端端點`
   - 主要介面：登入成功、強制改密完成、點開去處為首頁的推播三個時機的首頁判定；在「角色是主管就進主管首頁」之上，再依部門判定房務主管或工務主管；沒有部門或部門不是工務的主管算房務主管；主管種類只決定首頁格子，不影響誰能驗收
   - 需求原文依據：FR-001「角色是主管的帳號一律進主管首頁，不看帳號有沒有部門」、FR-002「部門為工務的主管是工務主管，其餘主管是房務主管」、FR-003、NFR-001、SC-001、邊界情況「當主管帳號帶有部門時，App MUST 仍依角色進主管首頁，再依部門決定是房務主管還是工務主管」；acceptance `主管登入後進自己的首頁.feature` 前四個場景
   - 既有 truth：`features/ios-app/帳號與登入/dsl.md` 的「登入成功，進入 {首頁}」句型（MODIFY，主管首頁必查欄位由三格改為依主管種類）；`features/ios-app/帳號與登入/` 各 feature 的登入後去處（不動）
   - 對既有實作：保留依角色分流與四分頁外殼；新增主管種類判定
   - truth 變更風險：低。契約與資料都不動，主管種類由既有的角色與部門推得
   - 承接 planner：`/ui-plan`

2. `主管首頁與主管清單介面`
   - 端點類型：`手機端端點`
   - 主要介面：房務主管首頁兩格（待查房、待驗收）、工務主管首頁一格（待驗收）；點格進清單；清單每一列的識別資訊（打掃單房號與內容，工務單另帶單號與完修結果）、開始等主管的時間與跨日標日期、最舊在前；空狀態說明；「我名下的單」依主管種類沿用房務首頁任務卡或工務首頁工務單卡；動作完成後清單與格數同步減少
   - 需求原文依據：FR-004「房務主管首頁 MUST 在最上方顯示待查房、待驗收兩格；工務主管首頁 MUST 只顯示待驗收一格」、FR-005、FR-006、FR-013「房務主管同房務首頁的任務列，工務主管同工務首頁的工務單清單」、FR-014、FR-015、FR-033、FR-034「工務單另外顯示單號與完修結果」、FR-035、SC-002、SC-004；acceptance `主管登入後進自己的首頁.feature` 的 2／3 與 3 場景、303 跨日場景、陳主管名下維修中工務單場景
   - 既有 truth：`features/ios-app/主管工作台/主管首頁.feature`、`主管清單.feature`、`dsl.md`（MODIFY，刪待審核 Rule 與格子值，新增工務主管首頁與名下的單）；`features/ios-app/dsl.md` 的「畫面回到 {清單}」與「這張單已被處理」清單值（MODIFY）
   - 對既有實作：退掉待審核格與待審核清單列；新增工務主管首頁、工務主管名下的單沿用工務單卡、待驗收列顯示完修結果
   - truth 變更風險：中。三格變兩種首頁，ios-app 主管工作台模組要大面積改寫；工務主管名下的單改沿用工務首頁口徑，會牽動介面 11 是否要回名下的單
   - 承接 planner：`/ui-plan`

3. `查房畫面與房務主管限定介面`
   - 端點類型：`手機端端點`
   - 主要介面：逐項判通過或不通過、全部判過才能送出、沒有備註欄、項目集合被調整時重新載入、沒有啟用項目時直接送出、結果畫面三種說法；工務主管在 App 裡沒有任何查房入口
   - 需求原文依據：FR-007「待查房清單 MUST 只開放給房務主管……工務主管 MUST NOT 看到待查房，也 MUST NOT 能送出查房」、FR-008 至 FR-012、SC-003、SC-014、邊界情況「送出時查房項目剛被調整」「飯店沒有設定任何查房項目」；acceptance `從待查房清單當場查房.feature`
   - 既有 truth：`features/ios-app/主管工作台/查房.feature`（MODIFY，查房只開放房務主管）；契約 `createInspections` 已有 `InspectionOutcome`、409 `state_conflict`、422 `inspection_items_mismatch`
   - 對既有實作：保留查房畫面與結果畫面；新增工務主管沒有入口
   - truth 變更風險：低。畫面行為不變，只收窄可見者
   - 承接 planner：`/ui-plan`

4. `工務單詳情的驗收動作與狀態呈現介面`
   - 端點類型：`手機端端點`
   - 主要介面：主管打開待驗收的工務單時看得到完修照片、完修結果與處理說明，有「通過」與「不通過」兩個動作；不通過的原因面板（空白不能送出）與送出後去處說明（回原處理人名下或工務待認領）；其他狀態沒有主管動作；報修人、處理人與主管三種視角都顯示待驗收、已結案與驗收人、最近一次不通過的原因與判定人；步驟條第四步「驗收」在結案時才完成；已被處理（409）與離線擋下；點開驗收不通過推播進工務單詳情、通知中心的分類；收回主管未核准的推播去處與文案
   - 需求原文依據：FR-023、FR-024、FR-025「不通過 MUST 要求填寫原因；原因空白時 MUST NOT 能送出」、FR-026「主管 MUST 看到這張單去了哪裡」、FR-027、FR-028「退回重修的單 MUST 顯示最近一次不通過的原因與判定人。步驟條第四步『驗收』MUST 在結案時才完成」、FR-029、FR-036、FR-037、FR-038、FR-040、SC-005、SC-007、SC-011、SC-013；acceptance `從待驗收清單驗收完修工務單.feature`、`主管動作先到先處理且離線一律擋下.feature`
   - 既有 truth：`features/ios-app/工務單/主管視角.feature`、`工務單房務端生命週期.feature`、`工務視角.feature`、`dsl.md`（MODIFY）；`features/ios-app/推播/點開推播的去處.feature`（MODIFY，刪主管未核准場景、加驗收不通過）；`features/ios-app/系統層/離線模式與連線異常.feature` 與 `系統層/dsl.md`（MODIFY，被擋動作改為查房送出、驗收通過、驗收不通過）
   - 對既有實作：退掉核准與退回按鈕、退回原因表單、補資料再送出、房務端與工務端的 `pending_review`／`rejected` 分支與「主管未核准」文案、`repair_rejected` 的推播去處與通知分類；新增驗收不通過動作與原因面板、去處說明、已結案狀態、最近一次不通過的原因與判定人
   - truth 變更風險：中高。同一張詳情有三種視角都要改，步驟條與狀態列的語意從「驗收在已完修內打勾」變成「結案是獨立狀態」
   - 承接 planner：`/ui-plan`

5. `報修與完修的員工端流程介面`
   - 端點類型：`手機端端點`
   - 主要介面：報修送出後的去向說明不再出現等主管審核，一般件與急件都直接進工務待認領；工務完修回報的結果多一個「不需處理」，選它時不用附照片、送出鈕可按、送出後直接顯示已結案；其他三種結果沒附照片仍不能送出
   - 需求原文依據：FR-016「不論一般件或急件，送出後 MUST 直接成立並進工務待認領」、FR-017、FR-018「『不需處理』MUST NOT 要求附完修照片，其他三種照舊要求至少一張」、FR-019、SC-009、SC-012；acceptance `報修直接進工務待認領且不需處理直接結案.feature`
   - 既有 truth：`features/ios-app/工務單/工務視角.feature` 的「完修回報沒附照片時送出鈕禁用」（MODIFY，加不需處理例外）；`工務單房務端生命週期.feature`（MODIFY）；`報修照片與送出.feature`（待 /ui-plan 確認是否寫到等主管審核）
   - 對既有實作：退掉報修送出後的等主管審核說明；新增完修結果「不需處理」與照片豁免
   - truth 變更風險：中。完修回報表單的照片必填規則出現第一個例外
   - 承接 planner：`/ui-plan`

6. `待認領切換部門只看不搶介面`
   - 端點類型：`手機端端點`
   - 主要介面：主管的待認領在房務與工務兩個池之間切換、這台手機第一次為房務、之後停在上次停留的部門（值為 `housekeeping` 或 `engineering`）、單卡與該部門員工一致但沒有搶單鈕、點開帶部門的推播切到該部門並標亮；工務主管即使部門是工務也一樣只看不搶
   - 需求原文依據：FR-030、FR-031「主管看到的待認領 MUST 只能看，房務主管與工務主管都一樣」、FR-032、SC-008；acceptance `待認領切換房務與工務只看不搶.feature` 的陳主管場景
   - 既有 truth：`features/ios-app/待認領/待認領列表.feature`（MODIFY，只看不搶同樣適用工務主管）；契約 `listDeptPool` 與 `PushTarget.dept` 不動
   - 對既有實作：保留切換部門與本機記住部門；新增工務主管的只看不搶
   - truth 變更風險：低。風險在既有實作若以部門判斷顯不顯示搶單鈕，部門為工務的主管會被當成工務員工
   - 承接 planner：`/ui-plan`

7. `工務單狀態機與驗收結果持久化介面`
   - 端點類型：`資料庫端點`
   - 主要介面：`tasks.state` 值集收回 `pending_review`、`rejected`，加入工務單的結案狀態；工務單的狀態轉移（開單一律進 `pool` 或指定處理人、完修進 `done`、驗收通過進結案、「不需處理」完修直接結案、驗收不通過回 `accepted` 或 `pool`、重修後再完修回 `done`）；`repair_tickets.reject_reason` 收回、最近一次驗收不通過的原因、判定人與時間的存法；`resolution_type` 加「不需處理」的值與重修後再完修時能不能改寫；`task_events.type` 收回 `repair_approved`、`repair_rejected`、`repair_resubmitted` 並加驗收不通過；`notifications.event` 收回 `repair_rejected` 並加驗收不通過；既有把工務單 `done` 當成已結束的讀取端如何認得新的結案狀態；已存在於資料庫的 `pending_review`／`rejected` 列如何處置；新 migration 收回 V17、V22 的值而不改舊檔
   - 需求原文依據：影響表 `data/data-model.dbml` 一列（「`tasks.state` 的 CHECK 刪除 `pending_review`、`rejected`，加上工務單結案的狀態值……」）；關鍵實體「任務（既有）……多一個『已結案』，值由 `/data-plan` 決定」「工務單（既有）……另記最近一次驗收不通過的原因、判定人與時間，存法交 `/data-plan`」；FR-017、FR-019、FR-020、FR-024、FR-025、FR-029、FR-040；「003 已經實作、要退掉的程式範圍」的資料庫段
   - 既有 truth：`data/data-model.dbml` 的 `tasks`（state CHECK 與轉移 Note）、`task_events`（type 與 payload Note）、`repair_tickets`（`reject_reason`、`resolution_type`、`verified_by`、`verified_at` 與 Note）、`notifications`（event CHECK 與 Note）
   - 對既有實作：退掉兩個狀態值、`reject_reason`、三個審核事件與一個通知值；新增結案狀態、驗收不通過紀錄、驗收不通過事件與通知值、完修結果新值
   - truth 變更風險：高。既有 truth 寫明「驗收不開新狀態值」，現在要改成結案獨立成一個狀態；`tasks.state` 是全系統共用的值集，新增值會波及今日進度、待認領、同房工務單、重複報修提示、原任務恢復等讀取端，收回值則牽動已存在的資料列
   - 承接 planner：`/data-plan`

8. `主管待辦判定口徑介面`
   - 端點類型：`資料庫端點`
   - 主要介面：刪除待審核判定段；待驗收公式改為以「狀態為完修且完修結果不是不需處理」判定，不再看 `verified_at`；待查房公式不變；主管種類由 `staff_accounts.role` 與 `dept` 推得、不另存欄位；兩類排序鍵與「今天」口徑沿用既有；示範資料的新狀態（工務主管帳號、不需處理的結案單、驗收通過的結案單、驗收不通過退回中的單）能否用定案後的欄位種出
   - 需求原文依據：「主管待辦的判定公式」表與其下三點說明；關鍵實體「主管待辦（新，衍生）」「主管種類（新，衍生）……由帳號的角色與部門推得，不另存欄位」；FR-022、FR-033、FR-034、FR-039、FR-041、SC-002、SC-004、SC-006
   - 既有 truth：`data/data-model.dbml` 的 `tasks` Note「主管待辦的三類判定」段、`repair_tickets` Note 的待審核與待驗收段、`task_events` Note 的待審核排序鍵段
   - 對既有實作：退掉待審核判定與它的排序鍵來源；新增工務主管只看待驗收的口徑、待驗收排除不需處理
   - truth 變更風險：低到中。只改 Note 與衍生口徑，不加欄位；但它依賴介面 7 定出的結案狀態值
   - 承接 planner：`/data-plan`

9. `工務單審核契約收回介面`
   - 端點類型：`後端端點`
   - 主要介面：刪除 `approveRepairTicket`、`rejectRepairTicket`、`resubmitRepairTicket` 與 `openapi.yaml` 的三條路徑；收回 `RepairTicket.reviewRequestedAt`、`rejectReason`、`reject_reason_required` 錯誤碼、`PushPayload.event` 的 `repair_rejected`、任務狀態值 `pending_review` 與 `rejected`；開單、直接指定處理人、定期保養與員工工作台各處提到審核的說明一併改寫
   - 需求原文依據：影響表全部 DELETE 列與「直接指定處理人的說明」「`員工工作台.yaml`」「`state_conflict` 說明」三個 MODIFY 列；FR-016、FR-017；「交給 /api-plan 的備忘」第一點
   - 既有 truth：`contracts/groups/現場作業/工務單.yaml`、`contracts/openapi.yaml`、`contracts/components/schemas/工務單.yaml`、`shared.yaml`、`任務.yaml`、`員工工作台.yaml`、`groups/現場作業/員工工作台.yaml`
   - 對既有實作：退掉
   - truth 變更風險：中。純刪除，但刪的是已實作、已有 App 呼叫端與 stepdef 的 operation，`openapi.yaml` 根檔與多個 schema 檔要一起動
   - 承接 planner：`/api-plan`

10. `驗收與完修契約介面`
    - 端點類型：`後端端點`
    - 主要介面：`verifyRepairTicket` 改為可送通過或不通過、不通過帶原因（空白 422）、房務主管與工務主管都能呼叫、員工被拒、單不在待驗收回 409，並回傳足以讓 App 說明去處的結果；`CompleteTaskRequest.resolutionType` 加「不需處理」、選它時 `photoIds` 可為空；`RepairTicket` 的狀態說明加上結案、加上最近一次驗收不通過的原因、判定人與時間；`PushPayload.event` 新增驗收不通過的值、收件人為原處理人、去處為工務單詳情
    - 需求原文依據：影響表 `verifyRepairTicket`、`RepairTicket`、`CompleteTaskRequest`、`PushPayload.event` 四列；FR-018、FR-019、FR-021、FR-023 至 FR-029、FR-036、FR-038、FR-040；「交給 /api-plan 的備忘」第二至五點
    - 既有 truth：`contracts/groups/現場作業/工務單.yaml` 的 `verifyRepairTicket`、`readRepairTicket`；`contracts/groups/現場作業/任務.yaml` 的完成任務；`contracts/components/schemas/工務單.yaml` 的 `RepairTicket`；`contracts/components/schemas/shared.yaml` 的 `PushPayload`、`Error`、任務狀態值
    - 對既有實作：新增（`verifyRepairTicket` 由單一驗收改為通過或不通過）
    - truth 變更風險：中高。`verifyRepairTicket` 的請求與回應形狀都會變，完成任務的照片必填規則出現例外，推播事件值集與資料層通知值集要同名
    - 承接 planner：`/api-plan`

11. `主管待辦查詢與查房權限介面`
    - 端點類型：`後端端點`
    - 主要介面：`getManagerQueues` 拿掉待審核清單與數字、依主管種類只回看得到的類別（工務主管不回待查房）、待驗收對所有主管相同且排除不需處理、待驗收列帶完修結果；`createInspections` 對工務主管的處理
    - 需求原文依據：影響表 `getManagerQueues` 與「主管工作台 API」標籤說明兩列；FR-004、FR-007「工務主管 MUST NOT 能送出查房」、FR-022、FR-034、FR-039「依主管種類只回該種類看得到的類別」、SC-002、SC-003；「交給 /api-plan 的備忘」第六點；acceptance `從待查房清單當場查房.feature` 陳主管場景「這次操作被拒絕」。影響表另一列寫「不動 後端 `createInspections`，行為與權限都不改」，與 FR-007 及該場景不一致，`createInspections` 是否納入本介面待 `/clarify`
    - 既有 truth：`contracts/groups/現場作業/主管工作台.yaml`、`contracts/components/schemas/主管工作台.yaml`、`contracts/openapi.yaml` 的標籤說明；`contracts/groups/現場作業/查房.yaml` 的 `createInspections`
    - 對既有實作：退掉待審核（`review`、`ReviewQueueItem` 與對應數字）；新增依主管種類回類別
    - truth 變更風險：中。回應形狀從固定三類變成依呼叫者而異
    - 承接 planner：`/api-plan`

### 分析流程的安排

#### Wave 1

- 平行分析介面：
  - `進 App 的首頁分流與主管種類判定介面`
  - `主管首頁與主管清單介面`
  - `查房畫面與房務主管限定介面`
  - `工務單詳情的驗收動作與狀態呈現介面`
  - `報修與完修的員工端流程介面`
  - `待認領切換部門只看不搶介面`
  - `工務單狀態機與驗收結果持久化介面`
  - `主管待辦判定口徑介面`
- 分析重點：
  - 首頁分流：主管種類要在哪一個時機判定，才能讓三個進 App 的入口與首頁格子用同一個結果？部門欄位缺值或是工務以外的值時，畫面上有沒有任何地方會把主管誤當成員工？
  - 主管首頁與清單：工務主管只有一格時，首頁版面與「我名下的單」改沿用工務單卡後，要怎麼呈現才與房務主管一致又不空洞？待驗收列加上完修結果之後，每一列還缺哪些資訊要列給 Wave 2？待審核退掉後，既有的清單切換、空狀態與刷新還有哪些地方綁著三類的假設？
  - 查房：工務主管在 App 裡有哪些路徑可能碰到查房（首頁、推播、通知中心、深連結），每一條要怎麼收掉？
  - 工務單詳情：「不通過」的原因面板、送出後的去處說明、已被處理與離線的回饋，各自要什麼資訊，哪些必須由後端在回應裡給、不能由 App 自己推？報修人、處理人、主管三種視角對「待驗收」「退回重修中」「已結案」三種狀態各要顯示什麼？步驟條與狀態列在重修後再完修時要怎麼呈現？驗收不通過推播的文案與點開去處，以及通知中心要怎麼分類這個新事件？
  - 報修與完修：「不需處理」放進完修結果選項後，照片必填、處理說明、送出鈕與送出後畫面要怎麼變？報修送出後原本出現等主管審核的畫面與文案有哪些要收回？
  - 待認領：只看不搶目前是依角色還是依部門判定，部門為工務的主管會不會拿到搶單鈕？
  - 工務單狀態機：結案要用新的狀態值，還是沿用既有某個值？它與 `done`、`inspected`、`cancelled` 在「已結束」語意上的關係是什麼？既有哪些讀取端把工務單的 `done` 當成已結束，換成結案後各自要認得哪一個？驗收不通過回到 `accepted` 或 `pool` 時，`done_at`、`resolution_type`、`resolution_note`、`verified_by`、完修照片要清空、保留還是覆寫？最近一次不通過的原因、判定人與時間放在 `repair_tickets` 欄位還是只靠 `task_events`？「原處理人已下班或請假」對工務人員用什麼資料判定，查房的換人判定能不能直接沿用？資料庫裡現存的 `pending_review`／`rejected` 列與 `reject_reason` 值，收回時要轉成什麼？事件值、通知值與完修結果新值要叫什麼，才能讓 Wave 2 的契約直接沿用？
  - 主管待辦口徑：待驗收改以完修結果排除不需處理後，還需要 `verified_at` 條件嗎？工務主管只看待驗收的判定落在資料層的哪一段說明？示範資料的新情境能不能用定案後的欄位全部種出？
- 安排理由：六個手機端介面都在回答「主管與員工看見什麼、能按什麼」，彼此不需要等對方的結論，而且全部寫入同一份 `ui/ui-plan.md`，依委派判準合併成一次 `/ui-plan` handoff，prompt 內逐一列出六個介面與分析重點，並註明哪些已實作要退、哪些要新增。兩個資料介面放在 Wave 1、而且先於契約，是這一輪最關鍵的排序：結案狀態值、收回的兩個狀態值、驗收不通過的事件值與通知值、完修結果新值，都是契約裡任務狀態列舉、`PushPayload.event`、`CompleteTaskRequest.resolutionType` 要逐字對齊的值集；若讓契約先取名、資料再跟著加值，只適合「加一值」這種小變動；這裡要推翻既有的「驗收不開新狀態值」並收回既有值，牽動全系統讀取 `tasks.state` 的地方，必須由資料 owner 先把狀態機定下來。資料分析不依賴畫面結論（需要的狀態與欄位語意 spec 已寫明），所以與 UI 平行。兩個資料介面都寫 `data/data-model.dbml`，合併成一次 `/data-plan` handoff，介面 7 先於介面 8。

#### Wave 2

- 平行分析介面：
  - `工務單審核契約收回介面`
  - `驗收與完修契約介面`
  - `主管待辦查詢與查房權限介面`
- 分析重點：
  - 審核契約收回：刪掉三支端點與相關欄位、錯誤碼、事件值、狀態值後，契約裡還有哪些說明文字或 `$ref` 指向它們？`openapi.yaml` 根檔、schema 檔與 group 檔要一起改哪些地方，才不會留下斷掉的參照？
  - 驗收與完修契約：`verifyRepairTicket` 要用同一支帶結果、還是通過與不通過分成兩支？不通過的回應要帶什麼，才能滿足 Wave 1 畫面「說明單去了哪裡」的需求？`RepairTicket` 要怎麼表達結案、最近一次不通過的原因、判定人與時間，欄位名與 Wave 1 資料定案的值如何對齊？「不需處理」放寬 `photoIds` 的規則要寫在請求 schema 還是完成任務的說明裡？驗收不通過推播的觸發、收件人、文案與去處，與資料層通知值如何同名？員工呼叫驗收時的拒絕語意是什麼？
  - 主管待辦查詢與查房權限：回應形狀要如何表達「依主管種類只回看得到的類別」，是省略欄位、回空陣列還是另帶種類？待驗收列要補哪些 Wave 1 畫面列出的欄位？`createInspections` 對工務主管要不要拒、用哪一種錯誤語意（待 `/clarify` 收斂影響表與 FR-007 的不一致後才定）？
- 安排理由：三個介面都依賴 Wave 1 的兩類結論：資料層定出的狀態值、事件值與欄位存法，決定契約要對齊的名稱；畫面定出的每列欄位、去處說明與動作回饋，決定回應要帶什麼。三個介面彼此不等對方結論，但都寫 `contracts/` 這一份主產物（共用 `shared.yaml`、`工務單.yaml` 與 `openapi.yaml`），依委派判準合併成一次 `/api-plan` handoff。收回介面本身不需要等任何分析，但它與新增介面改的是同一批檔案與同一組列舉，放到同一波一起改，才不會讓 `/api-plan` 在不同波段重複覆寫同一組檔案。

#### Wave 3（條件式）

- 平行分析介面：
  - `工務單狀態機與驗收結果持久化介面`（第二趟）
- 分析重點：
  - Wave 2 的契約有沒有要求 Wave 1 資料定案沒有的欄位或值（例如驗收結果的去處要能事後重讀、驗收不通過要保留歷次紀錄而不只最近一次）？若有，資料層的落點與寫入時機是什麼？
- 安排理由：`/api-plan` 不是 data truth 的 owner，不能代寫 `data-model.dbml`。若 Wave 2 沒有長出新的欄位或值，這一波直接略過。
