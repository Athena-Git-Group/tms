# Truth Delta: 001-staff-app-increment

**Plan Package**: `specs/plans/001-staff-app-increment`
**Truth Root**: `specs/truth`

## /technical-research

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `specs/truth/techstack.md` -> `前端` 員工端與 App 端驗收規格 | 建立 iOS 員工 App 這一端；App 技術未拍板，只以 Gherkin feature file 當測項。 | clarify 第 1 題選「後端 API ＋ iOS 員工 App」，並指示先只產生 feature-file 測項。 |
| ADD | `specs/truth/techstack.md` -> `後端` | 建立後端堆疊：Java 25、Spring Boot 4.1.1、JWT、SSE、Kafka outbox、Quartz、推播消費者只寫日誌、注入式 Clock。 | 首次建立 techstack truth，沿用基線 spec 已定案的整合深度；Clock 支撐本 plan 的計時驗收。 |
| ADD | `specs/truth/techstack.md` -> `資料與媒體處理` | 建立 PostgreSQL 16、Flyway、Idempotency-Key 加 processed_ops、docker-compose。 | 首次建立 techstack truth，沿用基線與 `docker-compose.yml`。 |
| ADD | `specs/truth/techstack.md` -> `測試與驗證` 後端 BDD techstack | 後端 E2E 用 Cucumber-JVM 加 Spring Boot Test，搭配 Testcontainers 的 Postgres 與 Kafka；斷言 API 與權威狀態。 | clarify 第 2 題選 1、第 3 題選 1。 |
| ADD | `specs/truth/techstack.md` -> `測試與驗證` iOS App BDD techstack | App 端暫無 runner，只有 feature file 測項。 | 使用者指示先只產生 feature-file 測項。 |
| ADD | `specs/truth/techstack.md` -> `本次開發不引入的技術` | 排除 H2 或記憶體 Kafka 驗收、真實 APNS/FCM、LLM、後端影像處理、App 自動化 runner。 | clarify 第 3 題排除替代品；基線假設 A1、A6；spec 假設照片在 App 端處理；App runner 延後。 |
| MODIFY | `specs/truth/techstack.md` -> `前端` 員工端 | 員工 App 拍板為 Swift、iOS ＋ Android 雙平台；跨平台工具鏈未拍板。 | 2026-11-02 clarify：使用者拍板「Swift 寫、支援雙平台」。 |
| MODIFY | `specs/truth/techstack.md` -> `測試與驗證` 員工 App BDD techstack | 由「尚無 runner」改為 Appium ＋ Cucumber-JVM（iOS：XCUITest driver；Android：UiAutomator2 driver），Java step definitions 與後端共用生態。 | 2026-11-02 clarify 使用者選 B；回補 research.md 殘餘風險「App 端測項無執行器」。 |
| MODIFY | `specs/truth/techstack.md` -> `本次開發不引入的技術` | 「App 自動化 runner 延後」改為排除 iOS 專屬 runner（XCUITest ＋ Cucumberish）與 Maestro。 | runner 已拍板，排除項改為被淘汰的替代方案。 |

## /api-plan

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `specs/truth/contracts/**` -> 基線 18 個 API group、48 個 path、53 個 schema、3 個 response | 把 `specs/tms-poc-to-backend/api/openapi.yaml` 照原樣遷入，拆成根檔、`groups/<區隔>/<group>.yaml`（18 個 group 超過 10 個，所以開一層：現場作業、住客與房間、知識與通知、營運管理）與 `components/`，語意不變。 | 首次建立 API truth。truth 必須是完整的系統契約，本 plan 的 MODIFY 也需要基線操作當底。 |
| MODIFY | `.../groups/現場作業/任務.yaml` -> `tasksStart` | 允許本人延後的 deferred→doing；開工時鎖定免查並記交辦已讀；另一筆 doing 時回 409 `doing_task_exists`，context 帶 `{taskId, roomNo}`；回應改為 `TaskDetail`。 | FR-001、FR-004、FR-023、FR-038；D2。 |
| MODIFY | `.../任務.yaml` -> `tasksDefer` | 新增負責人本人延後（accepted→deferred，保留負責人）；request 改為 `HoldReasonRequest`（1–60 字，422 `reason_invalid`）。主管延後語意不變。 | FR-002、FR-003、FR-010；D2。 |
| MODIFY | `.../任務.yaml` -> `tasksUndefer` | 限定只能處理主管延後（沒有負責人）的單；本人延後回 409。 | FR-010。 |
| MODIFY | `.../任務.yaml` -> `tasksComplete` | 新增 `CompleteTaskRequest`（修繕任務必附完修照片 photoIds，否則 422 `completion_photo_required`；另有 resolutionNote）；免查直接結案 inspected/waived 並回寫 OC；未勾的 SOP 項記跳過；實際工時改為各段累計；完修推播報修人，原任務停住原因改為工務已修好。 | FR-007、FR-008、FR-019、FR-020、FR-024；D1、D5、D13。 |
| MODIFY | `.../任務.yaml` -> `tasksCancel` | request 改為 `CancelTaskRequest`（reason 為 manager_void 或 plan_superseded，加 note）；明訂推播負責人與工務單維持原狀（推定）。 | FR-047；spec 邊界情況。 |
| MODIFY | `.../任務.yaml` -> `tasksSopCheck` | request 改為 `SopCheckRequest`，result 為 checked 或 skipped。 | FR-008、FR-040（剛跳過的 SOP 項）。 |
| MODIFY | `.../任務.yaml` -> `readTasks` | 回應改為 `TaskDetail`：已做分鐘、勾記與跳過項、合併交辦、已讀、退回資訊、同房工務單。 | FR-021、FR-036、FR-038、FR-039。 |
| ADD | `.../任務.yaml` -> `tasksInterrupt`（`POST /tasks/{id}/interrupt`）、`tasksResume`（`POST /tasks/{id}/resume`） | 本人中斷與恢復，計時接續；恢復時套用一次一間規則。 | FR-005、FR-006；D2。 |
| MODIFY | `components/schemas/任務.yaml` -> `tasks` | 新增 inspection、inspectionWaived、accumulatedMinutes、holdReason、deferredBySelf、linkedRepairTicketId、cancelReason（新增值 `staff_withdrew`）；source 新增 `staff`；doneAt 不再用來推導工時。 | spec 關鍵實體「任務（既有，修改）」。 |
| ADD | `components/schemas/任務.yaml` -> `HoldReasonRequest`、`CompleteTaskRequest`、`CancelTaskRequest`、`SopCheckRequest`、`HandoverEntry`、`ReturnInfo`、`TaskDetail` | 任務命令的 request 與員工視角詳情。 | 同上述各 operation。 |
| ADD | `.../groups/現場作業/工務單.yaml` -> 7 個 path（`/repair-photos`、`/repair-photos/{photoId}/content`、`/repair-tickets`（GET、POST）、`/repair-tickets/precheck`、`/repair-tickets/{ticketId}`、`/photos`、`/withdraw`）與 `components/schemas/工務單.yaml` 8 個 schema | 新增工務單 API：照片兩段式上傳與單張重試；開單前檢查（重複單、急件建議）；開單（兩種模式、免審核進工務池、急件推播、照片 1–3 張且必須都已上傳、必帶 Idempotency-Key）；同房清單、詳情、補照片、撤回（認領前，否則 409 `already_claimed`）。認領、開始、完成沿用任務 API。 | FR-011–021、NFR-003、NFR-004；D9、D10、D12；plan.md 兩段式照片前提。 |
| ADD | `.../groups/現場作業/員工工作台.yaml` -> `getMyToday`（`GET /me/today`）、`listDeptPool`（`GET /pool`）與 6 個 schema | 今日進度、剩餘工時、下一間、連續零退回（待 clarify）；依部門的待接池（急件、優先通知、同樓層、房號排序；來源標籤；分類筆數與篩選）。 | FR-028–034、FR-048；D4。 |
| ADD | `.../groups/現場作業/房間交辦.yaml` -> `createDeskNote`（`POST /desk-notes`）、`markHandoverRead`（`POST /tasks/{id}/handover-read`）與 3 個 schema | 櫃檯房間備註；交辦已讀不重複記錄。 | FR-037、FR-038。 |
| ADD | `.../groups/現場作業/查房.yaml` -> `createSpotCheck`（`POST /inspections/spot-checks`）、`getInspectionPassRate`（`GET /inspections/pass-rate`）與 2 個 schema | 抽查免查的單，不合格就退回原負責人重做並推播；合格率排除 waived。 | FR-026、FR-027；D1。 |
| MODIFY | `.../groups/知識與通知/房間記憶與問一下.yaml` -> `roomMemoryAsk` | request 改為 `KbAskRequest`（taskId、question）；回應改為 `KbAnswer`（found、只用原文、附出處、查不到時明說）；每次提問寫稽核紀錄。 | FR-041、FR-042、FR-045、FR-046；D3。 |
| ADD | `.../房間記憶與問一下.yaml` -> `listKbSuggestions`、`submitKbFeedback`、`escalateKbQuestion`、`listKbEscalations`、`replyKbEscalation` 與 7 個 schema | 推薦問題；有沒有幫助的回饋（沒幫助時通知 SOP 管理者）；轉問主管與主管回覆推播。 | FR-040、FR-043、FR-044。 |
| MODIFY | `components/schemas/shared.yaml` -> `Error` | 新增 `context` 欄位，並列出本 plan 新增的錯誤碼（doing_task_exists、reason_invalid、photo_required、photo_limit、photo_not_uploaded、completion_photo_required、already_claimed、ticket_closed）。 | NFR-006；驗收要求拒絕時帶業務提示文字。 |
| ADD | `components/schemas/shared.yaml` -> `RepairTicketSummary`、`PushPayload`、`PushTarget`、`PmsRoomStatusWriteback` | 跨 group 的工務單摘要；推播的非同步契約（15 種 event，去處為任務、待接池加標亮，或首頁）；PMS 回寫值（vacant_clean、occupied_clean）。 | FR-047（每則推播帶可直達識別）、FR-024（免查回寫 OC）。 |
| ADD | `components/parameters.yaml` -> `TaskId`、`IdempotencyKey`、`PhotoId`、`RepairTicketId`、`AnswerId`、`EscalationId`；`components/responses.yaml` -> `Forbidden`、`UnprocessableEntity` | 新操作共用的參數與錯誤回應。 | NFR-005（冪等寫入）。 |
| MODIFY | `components/schemas/租戶設定.yaml` -> `tenant_config.key`、`tenant_configRequest.key` | 封閉鍵集合新增 `inspection_waived_cleaning_types`（預設 stayover）。 | FR-022；D1。 |
| ADD | `.../groups/住客與房間/住客掃碼.yaml` -> `listGuestRequests`、`createGuestRequest`（`/guest-requests`）、`cancelGuestRequest`（`POST /guest-requests/{id}/cancel`）；`components/schemas/住客掃碼.yaml` -> `CreateGuestRequestRequest`、`GuestRequest` | 補上基線 spec 有定義、但基線 OpenAPI 漏掉的住客需求操作：建單（勾選單直接進 pool，自由文字經規則引擎進 pool 或 triage；同房 10 分鐘第 4 筆回 429；進 pool 時推播部門員工）、查看本次住宿的需求（依 reservation 過濾）、撤回（只限 triage 或 pool，轉 cancelled 且原因 guest_withdrew，否則 409；推播部門員工）。路徑沿用基線 OpenAPI 的資源式命名（`/guest/scan` 對應 `/guest-sessions/scan`）。 | 基線 FR-003、FR-005、FR-006、E-01、E-04；本 plan FR-047 的「新需求」與「住客撤回」推播需要入口。`/dsl-refine` 回補時發現的缺口。 |
| ADD | `components/parameters.yaml` -> `GuestSession`（header `X-Guest-Session`）、`GuestRequestId`；`components/responses.yaml` -> `Unauthorized`、`TooManyRequests` | 住客 session 的傳遞方式與 401、429 回應。 | 基線 E-17（session 驗證）、E-01（同房限流）。 |
| MODIFY | `.../groups/現場作業/任務.yaml` -> `tasksCancel` description | 註明住客撤回改用 `cancelGuestRequest`；request 與 response 不變。 | 讓作廢入口的責任邊界清楚。 |
| NOOP | `.../任務.yaml` -> `tasksClaim`、`tasksRequeue`、`tasksReassign`；`.../請假.yaml`；`.../查房.yaml` -> `createInspections` | 已檢查。工務認領沿用 claim；班末中斷單由主管用既有附交代 requeue 處理；改派限制不變；請假連鎖不變；查房通過與退回的行為由 `tasks.inspection` 欄位承接，request 與 response 不變。 | spec 假設「既有行為不變，只列入推播」。 |

## /data-plan

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `specs/truth/data/data-model.dbml` -> 基線 27 張表、全部 Ref 與檔頭慣例 | 把 `specs/tms-poc-to-backend/db_table/erm.dbml` 原樣遷入，語意不變：tenant_id 首欄、UUIDv7、複合外鍵、text 加 CHECK、task_events 為唯一真相、tasks 是投影。 | 首次建立 data truth；truth 必須是完整的資料模型，本 plan 的 MODIFY 也需要基線當底。 |
| MODIFY | `.../data-model.dbml` -> `Table tasks` 欄位 | 新增 worked_sec 與 segment_started_at（分段累計工時，不含中斷）、inspection（passed、waived、failed）、inspection_waived（開工鎖定）、hold_reason、deferred_by_self、linked_repair_task_id、cancel_reason（新增值 staff_withdrew）、urgent、priority_notify、pool_source、return_reason、requeue_note；source 新增 `staff`；done_at 不再用來推導實際工時。 | FR-002–007、FR-012、FR-017、FR-023–025、FR-032、FR-034；D1、D2、D12。對齊 API truth 的 `tasks`、`PoolItem`。 |
| MODIFY | `.../data-model.dbml` -> `Table tasks` 索引 | 新增 partial UNIQUE `uq_tasks_one_doing_per_staff`（tenant_id、assignee_id，WHERE state='doing'），以及待接池排序索引（dept、state、urgent、priority_notify）。 | FR-001 一次一間，由資料庫做第二道守衛；SC-001。 |
| MODIFY | `.../data-model.dbml` -> `Table tasks` Note 的狀態轉移 | 補上本 plan 的合法轉移：本人延後與開工、本人中斷與恢復、免查結案、抽查退回、工務單撤回；以及班末掃描的規則。 | 基線 GR-001 的 MODIFY（spec「對既有行為的影響」）。 |
| MODIFY | `.../data-model.dbml` -> `Table task_events.type`、`payload` | 事件類型新增 resumed、inspection_waived、repair_photos_appended（18→21）；payload 新增 staff_withdrew、by_self、spot_check，照片只記張數。 | FR-006、FR-024、FR-026、NFR-004。 |
| MODIFY | `.../data-model.dbml` -> `Table outbox.kind` note | 明訂 push 的 payload 形狀等於 contracts 的 PushPayload；pms_status 的值為 vacant_clean 或 occupied_clean（OC）。 | FR-024、FR-047；對齊 API truth。 |
| MODIFY | `.../data-model.dbml` -> `Table tenant_config.key` | 封閉鍵集合新增 `inspection_waived_cleaning_types`（預設 ["stayover"]）。 | FR-022；D1。 |
| MODIFY | `.../data-model.dbml` -> `Table rooms` 不變條件 | 免查結案時 hk_state 改為 cleaned；抽查退回時改為 dirty。 | FR-024、FR-026。 |
| MODIFY | `.../data-model.dbml` -> `Table room_memory` Note | 工務單完修以 kind=repair 寫入；查一下不得帶出住客身分。 | FR-041、FR-046。 |
| ADD | `.../data-model.dbml` -> `Table repair_tickets`、`repair_ticket_counters`、`repair_ticket_contributors`、`repair_photos` | 工務單是修繕任務的一對一延伸（單號 W-NNNN 在租戶內唯一，以 counter 取號）；同房同類別的重複單查詢索引；可補照片的觸發者；照片兩段式（pending→uploaded），本體外存，沿用 lost_items.photo_ref 慣例。 | FR-011–021、NFR-004；research 殘餘風險「照片儲存媒介」依基線慣例收斂為外存引用。 |
| ADD | `.../data-model.dbml` -> `Table desk_notes`、`handover_reads` | 櫃檯房間備註（有效期間待 clarify，先不加失效欄位）；交辦已讀以 PK 防止重複記錄。 | FR-037、FR-038。 |
| ADD | `.../data-model.dbml` -> `Table kb_audit_logs`、`kb_escalations` | 查一下稽核紀錄（引用來源、回答、回饋、是否轉問）；問主管（一則回答只能轉問一次，主管回覆後推播）。 | FR-043–045。 |
| ADD | `.../data-model.dbml` -> 18 條新 Ref | 新表的複合外鍵，以及 tasks.linked_repair_task_id 指向自身。 | 同上。 |
| NOOP | `.../data-model.dbml` -> `rooms.floor`、`plan_rooms`、`handovers.snapshot`、`reservations_cache.attrs`、`staff_accounts`、`leaves` | 已檢查。同樓層用本班 plan_rooms 對應的 rooms.floor 推導（FR-032 推定，待 clarify）；交班單該房項目在 snapshot 內；VIP 與今日入住用 reservations_cache；一員工一部門已由 staff_accounts.dept 承接。 | 不需要新增欄位。 |

## /dsl-refine

| 動作 | Truth 規格 | 改動摘要 | 原因 |
| --- | --- | --- | --- |
| ADD | `specs/truth/features/backend/dsl.md` -> 共用約定、狀態用語對照、16 列共用句型 | 建立後端介面根：租戶、時鐘、任務識別、權威狀態約定；共用的員工、任務、時間 Given；開始、恢復、完成、查房通過 When；拒絕、狀態、負責人、停住原因、已做時間、推播 Then。 | 首次建立 interface truth；這些句型跨 2 個以上模組使用，而且契約一致。 |
| ADD | `specs/truth/features/backend/任務執行/` -> `本人延後與開工.feature`、`一次只做一間.feature`、`中斷恢復與實際工時.feature`、`dsl.md` | 共 11 條規則：延後原因、延後仍歸本人、60 字上限、延後單可開工、一次一間、中斷停表、保留勾記、恢復接續、實際工時、未勾記跳過。 | 承接 acceptance「房務員自行延後中斷與一次一間」規則 1（US1，D2）。 |
| ADD | `specs/truth/features/backend/班末掃描/` -> `班末掃描與中斷單交接.feature`、`dsl.md` | 共 7 條規則：退池並標班末退池、進行中留給本人、中斷維持、員工推播、主管推播、主管附交代退池、同樓層推播。`房務待接池包含以下任務：` 只有本模組使用，所以從介面根下放到這裡，語意不變。 | 承接同一 acceptance 規則 2（FR-009，D11）。 |
| ADD | `specs/truth/features/backend/工務單/` -> `開立工務單與急件.feature`、`重複報修補照片與撤回.feature`、`照片完整性與冪等送出.feature`、`完修與回到原任務.feature`、`dsl.md` | 共 24 條規則：免審核進池、中斷模式、不中斷模式、急件建議、急件排序、急件推播、重複提示、補照片、撤回限認領前、照片 1–3 張、上傳完整性、重試、完修照片、冪等、完修推播、不自動恢復、不需維修結案。 | 承接 acceptance「工務單從報修到完修」（US2，D9、D10、D12、D13）。 |
| ADD | `specs/truth/features/backend/查房/` -> `免查房鎖定與結案.feature`、`免查抽查與合格率.feature`、`dsl.md` | 共 11 條規則：開工鎖定免查、免查結案、PMS 回寫 OC、未免查待查房、通過、PMS 可售、合格率排除免查、抽查退回、歸零、結果改為退回、推播、計入合格率。 | 承接 acceptance「續住清免查房」（US3，D1）。 |
| ADD | `specs/truth/features/backend/今日進度/` -> `今日進度與下一間.feature`、`dsl.md` | 共 6 條規則：完成與總數、完成單數、臨時需求、剩餘工時 135 分、下一間順序、連續零退回（待 clarify）。 | 承接 acceptance「今日進度與待接池排序」規則 1（FR-028–031）。 |
| ADD | `specs/truth/features/backend/待接池/` -> `待接池排序與篩選.feature`、`系統準備工單派給負責人.feature`、`dsl.md` | 共 7 條規則：排序、同樓層標示、來源與接手進度、分類筆數、篩選、準備工單派給負責人或進池。 | 承接同一 acceptance 規則 2 與 3（FR-032–035，D6）。 |
| ADD | `specs/truth/features/backend/房間交辦/` -> `交辦合併與已讀.feature`、`查房退回資訊.feature`、`dsl.md` | 共 8 條規則：交班單只併該房、櫃檯備註、接手進度、打開不算已讀、開始即已讀、不重複記錄、退回資訊合併、不另列主管交代。 | 承接 acceptance「房間交辦合併與已讀」（US5）。 |
| ADD | `specs/truth/features/backend/查一下/` -> `推薦問題.feature`、`有出處的回答與問主管.feature`、`沒幫助回饋.feature`、`dsl.md` | 共 11 條規則：情境推薦、VIP 推薦、附出處、查不到明說、稽核含回饋、查不到也稽核、問主管、主管回覆推播、住客需求原文、不露住客身分、沒幫助通知 SOP 管理者。 | 承接 acceptance「查一下只給有出處的答案」（US6，D3）。 |
| ADD | `specs/truth/features/backend/推播/` -> `員工推播事件.feature`、`dsl.md` | 共 8 條規則：新需求同樓層、查房通過、查房退回、作廢、改派他人、改派本人、住客撤回、請假接手。 | 承接 acceptance「員工推播事件」（FR-047）；其他推播由觸發它的模組驗證。 |
| ADD | `specs/truth/features/ios-app/dsl.md` -> App 介面根與 7 列共用句型 | 建立 App 介面根：runner 未定、測試資料由後端準備、畫面斷言要與後端一致。 | 使用者指示 App 端先只產出 feature-file 測項（technical-research 決策 1、2）。 |
| ADD | `specs/truth/features/ios-app/任務執行/` -> `任務卡狀態與提示.feature`、`dsl.md` | 共 4 條規則：一次一間的提示、中斷停表顯示、60 字上限、完成 · 免查房顯示。 | 承接 US1、US3 的畫面可觀察結果。 |
| ADD | `specs/truth/features/ios-app/工務單/` -> `報修照片與送出.feature`、`開單提示.feature`、`dsl.md` | 共 8 條規則：浮水印、0 張不能送出、第 4 張加不進、上傳失敗單張重試、離線保存、恢復連線冪等送出、重複提示補照片、急件建議。 | 承接 US2 中只屬 App 的責任（NFR-002、NFR-003、FR-014）。 |
| ADD | `specs/truth/features/ios-app/今日進度/`、`待接池/`、`房間交辦/`、`查一下/` -> 共 5 份 feature 與 4 份 `dsl.md` | 首頁進度、推薦順序、零退回天數、待接池列表排序與標示、單卡資訊、分類標籤與篩選、交辦區與未讀、退回資訊、推薦問題、出處卡、查不到與問主管按鈕。 | 承接 US4–US6 的畫面呈現。 |
| ADD | `specs/truth/features/ios-app/推播/` -> `點開推播的去處.feature`、`dsl.md` | 共 2 條規則：8 種推播點開的去處（場景大綱），以及完修推播提示可以恢復。 | 承接 acceptance「員工推播事件」的「點開後去處」欄位與工務單完修提示。 |
| MODIFY | `specs/truth/features/backend/dsl.md` -> 共用 Given、When、Then 的 StepDef 實作語意 | 實作語意對上 API 與 data truth，句型和驗證責任不變：開始、恢復、完成、查房對應 `tasksStart`、`tasksResume`、`tasksComplete`、`createInspections`；登入用 `staffAccountsLogin`；拒絕要符合 `Error`；推播比對 `PushPayload`；已做時間看 `worked_sec`＋`segment_started_at`；停住原因看 `hold_reason`；待接池 Given 寫入 `pool_source`、`priority_notify`、`return_reason`、`requeue_note`。 | `/api-plan` 與 `/data-plan` 已定案；plan.md 列出的「Wave 1 之後的 truth 回補」。 |
| MODIFY | `.../backend/任務執行/dsl.md`、`班末掃描/dsl.md` -> StepDef 實作語意 | 對應 `tasksSopCheck`、`tasksDefer`（`HoldReasonRequest`）、`tasksInterrupt`、`tasksRequeue`、`listDeptPool`；權威狀態改為具體欄位（`task_sop_checks`、`deferred_by_self`、`pool_source`=shift_end_return 等）。句型與驗證責任不變。 | 同上。 |
| MODIFY | `.../backend/工務單/dsl.md` -> StepDef 實作語意 | 對應照片兩段式（`createRepairPhoto`＋`uploadRepairPhotoContent`）、`precheckRepairTicket`、`createRepairTicket`（`interruptSourceTask`）、`appendRepairTicketPhotos`、`withdrawRepairTicket`、`readRepairTicket`、`tasksComplete`（`CompleteTaskRequest`）；權威狀態對到 `repair_tickets`、`repair_photos`、`repair_ticket_contributors`、`repair_ticket_counters`。句型與驗證責任不變。 | 同上。 |
| MODIFY | `.../backend/查房/dsl.md`、`今日進度/dsl.md`、`待接池/dsl.md` -> StepDef 實作語意 | 對應 `updateTenantConfig`、`createSpotCheck`、`getInspectionPassRate`、`getMyToday`（`MyToday` 欄位對照）、`listDeptPool`（`DeptPool`、`sameFloor`、來源代碼）；PMS VIP 訂單直接推給模擬 PmsAdapter（沒有 HTTP 端點）。句型與驗證責任不變。 | 同上。 |
| MODIFY | `.../backend/房間交辦/dsl.md`、`查一下/dsl.md`、`推播/dsl.md` -> StepDef 實作語意 | 對應 `createDeskNote`、`markHandoverRead`、`readTasks`（`TaskDetail.handovers`、`returnInfo`）、`roomMemoryAsk`、`listKbSuggestions`、`submitKbFeedback`、`escalateKbQuestion`、`replyKbEscalation`、`tasksCancel`、`tasksReassign`、`createLeaves`、`guestSessionsScan`；權威狀態對到 `handover_reads`、`desk_notes`、`kb_audit_logs`、`kb_escalations`。住客建單與撤回標示 API truth 缺口。句型與驗證責任不變。 | 同上。 |
| MODIFY | `specs/truth/features/backend/推播/dsl.md` -> `住客在 "{房號}" 掃碼要 "{品項}"`、`住客撤回 "{房號}" 的需求` 的 StepDef 實作語意 | 把「API truth 尚未收錄」換成 `createGuestRequest`（`POST /guest-requests`）與 `cancelGuestRequest`（`POST /guest-requests/{id}/cancel`）；權威狀態補上 `pool_source`=guest_scan、`cancel_reason`=guest_withdrew 與 `PushPayload.event`。句型與驗證責任不變。 | `/api-plan` 已補上住客需求 API，回補先前標示的缺口。 |
| NOOP | `specs/truth/features/backend/**/*.feature`、`specs/truth/features/ios-app/**` | 已檢查。所有 feature 句型不變，稽核仍通過；App 端 DSL 透過後端測試資料準備狀態，不直接綁定 operation，而且 runner 尚未決定，所以本輪不改。 | 本輪只回補實作語意。 |
| MODIFY | `specs/truth/features/ios-app/**/dsl.md`（含介面根，共 8 份） | 實作語意欄由「runner 未定」改掛 Appium；介面根 BDD techstack 段落改為 Appium ＋ Cucumber-JVM（iOS XCUITest driver / Android UiAutomator2 driver），並新增「元素定位用 accessibility id、兩平台共用」共通約定。句型與驗證責任不變。 | `/technical-research` 2026-11-02 拍板 runner，回補先前標示的缺口。 |
| MODIFY | `"{員工}" 在 App 打開首頁` 權威位置：`ios-app/今日進度/dsl.md` -> `ios-app/dsl.md`（介面根 When） | 僅搬移權威位置，語意不變。 | 任務執行與今日進度兩模組共用，依唯一歸屬判準上收；任務執行的 3 個 step 原本找不到 row（稽核 error）。 |
| MODIFY | `"{員工}" 已停用` 權威位置：`ios-app/dsl.md` -> `ios-app/待接池/dsl.md`（Given） | 僅搬移權威位置，語意不變。 | 只有待接池模組使用，依唯一歸屬判準下放（稽核 warning）。 |
| MODIFY | `"{房號}" 今天沒有打掃負責人` 權威位置：`backend/待接池/dsl.md` -> `backend/dsl.md`（介面根 Given） | 僅搬移權威位置，語意不變。 | 待接池與推播兩模組共用；推播的 step 原本找不到 row（稽核 error）。 |
| MODIFY | `"{員工}" 只收到一次推播 "{標題}"` 權威位置：`backend/dsl.md` -> `backend/推播/dsl.md`（Then） | 僅搬移權威位置，語意不變。 | 只有推播模組使用，依唯一歸屬判準下放（稽核 warning）。 |
| MODIFY | `從 PMS 拉今日房態，"{房號}" 的訂房帶 "{屬性}" 屬性` 權威位置：`backend/待接池/dsl.md` ＋ `backend/推播/dsl.md` 雙列 -> 合併為 `backend/dsl.md` 一列（介面根 When） | 把推播列的「進池時寫入部門成員 push outbox」語意併入待接池列的完整定義；合併後語意為兩列聯集，句型不變。待接池的 `匯入今日房態 CSV` 列改引用介面根句型。 | 同一句型有兩個權威位置（稽核 error）；兩模組共用，依唯一歸屬判準上收。 |
| MODIFY | `"{員工}" 判定 "{任務}" 查房全部項目通過` 權威位置：`backend/dsl.md`（`"{主管}"` 版）＋ `backend/查房/dsl.md`（`"{員工}"` 版）雙列 -> 合併為 `backend/dsl.md` 一列 `"{員工}"` 版 | 合併語意：有 `inspect_submit` 權限時同原主管版落地，無權限時被拒；句型形狀不變（參數改名不影響 step 匹配）。 | 同形句型在根與模組各一列造成稽核 ambiguous match（5 個 error）。 |
| MODIFY | `backend/待接池/系統準備工單派給負責人.feature` -> 「刪除或停用規則」場景大綱拆成刪除、停用兩個場景 | 驗收語意不變；`刪除規則`、`停用規則` 兩列 DSL 本就存在。 | 稽核器不展開場景大綱的 `<操作>` 佔位，無法對應 DSL row（稽核 error）。 |
| NOOP | `specs/truth/features/**` 其餘 feature 與 dsl.md | 已檢查。句型與驗證責任不變；ios-app 與 backend 拓樸稽核均 PASSED。 | 本輪只回補 runner 實作語意與修復稽核缺陷。 |
