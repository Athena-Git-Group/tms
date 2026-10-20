STATUS: READY

# TMS 後端實作規格（spec.md——結構層以後唯一需求真源）

來源：`clarify/clarified.md`（STATUS: RESOLVED）。權威上游：docs/11（系統設計）、
docs/12（資料模型，逐欄位以它為準）。POC 僅參考，落差裁決見假設 §範圍。
target = backend；本次交付 = `tms/` Spring Boot 後端（REST API＋狀態機＋
outbox/relay/Kafka 骨幹＋Quartz worker），前端不在範圍。

---

## 1. 使用者故事

### 使用者故事 1 — 住客掃碼提需求、看進度、評分 (Priority: P1)

作為住客，我希望掃房間 QR 就能提需求（勾選單或打字）、看處理進度、完成後評分，以便不用打電話也不用裝 App。

**為何為此優先級**: 這是整個產品的入口迴路；沒有它其餘任何角色都沒有單可處理。

**獨立驗證方式**: 以種子房間與訂單啟動服務，從 `POST /guest/scan` 到評分全程走 API 驗證，不需任何員工端功能以外的實作（認領由測試直接以員工身分呼叫）。

**驗收情境**:
1. **Given** 房 305 有現行 QR token 且住客在住（當前訂單存在），**When** `POST /guest/scan`，**Then** 回 session＋房號＋資訊頁＋選單。
2. **Given** 房 502 空房（occupancy=vacant、無當前訂單），**When** 掃碼，**Then** 409 `room_vacant`、不簽 session、不建單。
3. **Given** 住客勾「毛巾×2」送出，**Then** 建 amenity 任務、state=pool、dept=housekeeping、reservation_id 綁當前訂單，且事件 `created` 帶建立快照。
4. **Given** 住客打字「冷氣不冷」且規則引擎命中工務，**Then** state=pool、dept=engineering、命中 rule_id 留痕；**Given** 命中 0 或多個部門，**Then** state=triage、dept=frontdesk。

**功能需求（FR）**:
- **FR-001**: `POST /guest/scan` MUST 驗 token 存在且未作廢，以房號查當前訂單（`superseded_at IS NULL`）；查無 → 409 `room_vacant`；查得到 → 簽發 guest session（綁房＋訂單、expires_at=退房時間）並回資訊頁與選單。
- **FR-002**: 資訊頁內容 MUST 來自 tenant_config `guest_info_pages`，不建任務（`GET /guest/home` 重讀）。
- **FR-003**: `POST /guest/requests` MUST 支援勾選單（itemId→預對應部門直接入 pool）與自由文字（lang 可帶）；自由文字過規則引擎：命中單一部門→pool，否則→triage；LLM 僅翻譯與建議（可插拔介面、留稽核，不直接改歸屬）。
- **FR-004**: 同一房 10 分鐘內第 4 筆需求 MUST 回 429（第 3 筆成功）。
- **FR-005**: `GET /guest/requests` MUST 以 session 綁的 reservation_id 過濾，不得按房號撈。
- **FR-006**: `POST /guest/requests/{id}/cancel` MUST 僅在 state ∈ {triage, pool} 成功（→cancelled，原因 `guest_withdrew`）；否則 409 `state_conflict`。
- **FR-007**: `POST /guest/requests/{id}/rating` MUST 收 1–5 整數；0/6 → 422；僅 done 或 inspected 後可評；同單重評 → 409（假設 A5）。
- **FR-008**: `GET /guest/stream`（SSE）MUST 只推本 session 綁定房／訂單的任務變化。

### 使用者故事 2 — 員工在待接池認領、開始、完成 (Priority: P1)

作為房務員／工務，我希望在部門待接池認領臨時需求、用「開始」「完成」兩段回報、清潔時逐項打勾 SOP，以便不靠對講機交辦。

**為何為此優先級**: 與故事 1 合成最小可用迴路（提出→處理）。

**獨立驗證方式**: 以種子任務直接驗 claim/start/complete/sop 四端點與守衛。

**驗收情境**:
1. **Given** pool 中任務 dept=housekeeping，**When** 房務 claim，**Then** pool→accepted、事件 `claimed`；**When** 工務 claim 同單，**Then** 409。
2. **Given** 兩員工同秒 claim 同單，**Then** 恰一人成功、另一人 409（條件式 UPDATE 守衛）。
3. **Given** doing 中打掃單，**When** complete，**Then** done、實際分鐘=done_at−started_at。

**功能需求（FR）**:
- **FR-010**: `GET /staff/pool` MUST 只回本部門 pool 任務。
- **FR-011**: claim MUST 限 state=pool 且 task.dept=員工 dept；違反 → 409 `state_conflict`。
- **FR-012**: start MUST 限 accepted→doing 並記 started_at；complete MUST 限 doing→done 並記 done_at。
- **FR-013**: `POST /staff/tasks/{id}/sop/{item}` MUST 記勾選或跳過（`{"checked":true}`／`{"skipped":true}`），跳過不擋 complete，皆寫事件 `sop_checked`。
- **FR-014**: `GET /staff/stream`（SSE）MUST 推本部門待接池與本人任務的變化。

### 使用者故事 3 — 櫃檯判讀自由文字 (Priority: P1)

作為櫃檯，我希望看到待判讀清單並三選一（給房務／給工務／當場回答），以便猜不準的需求有人裁決。

**為何為此優先級**: triage 態是故事 1 自由文字路徑的必要出口。

**獨立驗證方式**: 建一筆 triage 任務，分別驗 assign 與 answer 兩分支。

**驗收情境**:
1. **Given** triage 任務，**When** `POST /desk/triage/{id}` `{"action":"assign","dept":"housekeeping"}`，**Then** triage→pool、事件 `triaged`。
2. **When** `{"action":"answer","note":"早餐 07:00–10:00"}`，**Then** triage→done、事件 `answered`、回覆存任務 note、住客端可見。

**功能需求（FR）**:
- **FR-020**: `GET /desk/triage` MUST 回本店全部 triage 任務。
- **FR-021**: assign 的 dept 值域 MUST 與任務 dept 同一套（housekeeping|engineering）。
- **FR-022**: 櫃檯 MUST 看得到本店所有任務（防重複建單）。

### 使用者故事 4 — 主管查房與退回 (Priority: P1)

作為主管，我希望逐項判定查房、任一項不過整間退回、全過才回寫 PMS，以便守住品質關卡。

**為何為此優先級**: cleaning 任務的終點閘門，也是 outbox/PMS 回寫鏈的第一個真實用例。

**獨立驗證方式**: 對 done 打掃單送查房結果，驗兩分支＋下班連動＋自查禁止。

**驗收情境**:
1. **Given** done 打掃單，**When** 全項 pass，**Then** done→inspected、rooms.hk_state=inspected、同交易排一筆 `pms_status` 進 outbox。
2. **When** 任一項 fail，**Then** done→accepted、redo_count+1、清 started_at/done_at/SOP 勾記、hk_state=dirty、事件 `inspection_failed`。
3. **Given** 退回時原清房者已下班（照租戶班次設定）或已請假，**Then** 同交易連動 accepted→pool，寫第二筆事件 `returned_to_pool`，優先推播同樓層。
4. **Given** 查房者=原清房者，**Then** 403。

**功能需求（FR）**:
- **FR-030**: `POST /admin/inspections/{taskId}` 的 items MUST 涵蓋租戶設定的全部查房項目，缺項 → 422；task.state ≠ done → 409。
- **FR-031**: 全過與任一不過的兩分支行為如驗收情境 1–3；查房結果存 inspections 表。
- **FR-032**: 自清自查 MUST 擋（403），伺服器端強制。

### 使用者故事 5 — 員工登入、住客 session 與零跨店洩漏 (Priority: P1)

作為平台營運者，我希望員工帳密登入、住客憑 session、所有 API 伺服器端強制「角色×動作」與租戶隔離，以便多店共用一庫而零跨店洩漏。

**為何為此優先級**: 其餘所有故事的前置；greenfield 必須先立此樁。

**獨立驗證方式**: 兩個種子租戶交叉請求，驗 404／角色矩陣逐格。

**驗收情境**:
1. **Given** 租戶 A 員工憑證，**When** 存取租戶 B 的任務 id，**Then** 404。
2. **Given** 房務憑證，**When** 呼叫 `/admin/**`，**Then** 403。
3. **Given** guest session 已被輪替撤銷（revoked_at 非空），**When** 任一 /guest 請求，**Then** 401。

**功能需求（FR）**:
- **FR-040**: `POST /auth/login` MUST 驗帳密（bcrypt/argon2 雜湊）核發 JWT（HS256、TTL 12h、claims: tenant_id/staff_id/role/dept），並登記推播裝置（staff_devices，同裝置去重）；登入不綁裝置。
- **FR-041**: 每請求 MUST 驗登入態＋`staff_accounts.active`＋角色×動作矩陣：guest→`/guest`；frontdesk→`/desk`＋`/staff` 讀；housekeeping/engineering→`/staff`（限本部門動作）；manager→`/admin`＋`/staff`＋`/desk`；owner→`/owner` 只讀。前綴外一律 403。
- **FR-042**: /guest 請求 MUST 驗 session `expires_at > now()` 且 `revoked_at IS NULL`，缺一即 401。
- **FR-043**: 資料層 MUST 應用層強制 tenant scope＋Postgres RLS 第二道＋複合外鍵 `(tenant_id, xxx_id)`；跨店資源一律 404。

### 使用者故事 6 — 房間 QR 簽發與輪替 (Priority: P1)

作為主管，我希望列印房間 QR、貼紙被撕走時重產，以便住客入口安全可控。

**為何為此優先級**: 故事 1 的掃碼依賴現行 token；輪替是安全閉環。

**獨立驗證方式**: 簽發→掃碼→輪替→舊 token 與舊 session 同時失效。

**驗收情境**:
1. **When** `POST /admin/rooms/{no}/qr/rotate`，**Then** 同一筆交易：舊 token 標 revoked、新 token version+1 生效、該房全部未撤銷 guest session 寫上 revoked_at。
2. **Given** 舊 token，**When** 掃碼，**Then** 409 `room_vacant` 語意之「請洽櫃檯」（token 已作廢）。

**功能需求（FR）**:
- **FR-050**: token MUST ≥128 bit 隨機（base62 約 22 字元）、不含房號資訊、全域唯一；一房同時僅一枚 active（partial unique）。
- **FR-051**: `GET /admin/rooms/{no}/qr` 回列印資料；rotate 行為如驗收情境 1。
- **FR-052**: `/guest/scan` MUST 另設 IP 層級限流與失敗計數：10 次失敗/10 分鐘（tenant_config 可調，假設 A7）→ 擋並告警。

### 使用者故事 7 — 班前自動排房、手動調整、重產發佈 (Priority: P2)

作為主管，我希望系統班前排好清潔房、我可調整、人力劇變時重產對照後發佈，以便整天的打掃有秩序。

**為何為此優先級**: 營運閉環核心，但依賴 P1 的任務引擎先存在。

**獨立驗證方式**: 以今日房態種子跑排房 job→調整→發佈→重產→再發佈，驗任務對帳三分支。

**驗收情境**:
1. **Given** 今日待掃清單，**When** worker 班前排房，**Then** 產 draft 計畫：樓層×工時、一層一人、est 取 cleaning_time_stats（樣本 <20 用租戶標準工時）、algo_version=v1。
2. **When** `PUT /admin/plans/{date}` 帶過期 If-Match，**Then** 409＋最新內容；成功則 version+1、事件 `plan_adjusted`。
3. **When** 發佈，**Then** 逐間開 cleaning 任務（source=system、建立即 accepted、指派到人、work_date=計畫日、plan_rooms.task_id 回填）。
4. **Given** 已發佈計畫存在，**When** 重產，**Then** 產新 draft 與現行並存；**When** 發佈新 draft，**Then** 同交易：新計畫 published、舊標 superseded、對帳——兩份都有的房未開始單改掛新計畫換負責人（事件 `reassigned`）；舊有新無的房未開始單 cancelled（原因 `plan_superseded`）；doing 中的單不動、列清單給主管。
5. **Given** 同房同日已有未完成打掃單，**When** 任何路徑再開第二筆，**Then** 被 DB partial unique 擋下。

**功能需求（FR）**:
- **FR-060**: 排房為 Quartz 租戶感知排程（班前時刻依租戶設定），演算法為可替換元件。
- **FR-061**: `GET /staff/schedule` 回本人今日負責房；`GET /admin/plans/{date}` 回當日計畫（含 draft 與 published）。
- **FR-062**: deferred 單由 worker 隔日班前自動回池（事件 `returned_to_pool`，payload 註原因）。

### 使用者故事 8 — 請假、中斷與班末掃描 (Priority: P2)

作為主管，我希望登記請假時系統自動退單、進行中的列給我交代後退池、班末自動清掉掛名單，以便人不在單不卡。

**為何為此優先級**: 人力變動是飯店日常；依賴任務引擎與排房。

**驗收情境**:
1. **When** `POST /admin/leaves`（staffId＋起訖），**Then** 該員 accepted 單自動→pool、doing 單→interrupted，回傳 `{requeued:[…], interrupted:[…]}`。
2. **When** `POST /admin/tasks/{id}/requeue` `{"progressNote":"浴室洗好了，床還沒鋪"}`，**Then** interrupted→pool、交代跟單走、優先推播同樓層。
3. **Given** 班次結束時仍有 accepted 且從未 start 的單，**Then** 班末掃描一律退回 pool（事件 `returned_to_pool`，原因班末退池）。

**獨立驗證方式**: 建請假與班末兩情境種子，驗三條自動化路徑與事件。

**功能需求（FR）**:
- **FR-070**: 請假連鎖、requeue、班末掃描行為如驗收情境；班末掃描為 Quartz 排程（照租戶班次時間）。
- **FR-071**: `POST /admin/tasks/{id}/reassign` MUST 限 pool/accepted；`defer` 限 pool→deferred；`undefer` 限 deferred→pool；`cancel` 可從任何未完成態→cancelled 且必附原因（`manager_void`|`plan_superseded`）。

### 使用者故事 9 — 房態來源：PMS 同步與 Excel 匯入 (Priority: P2)

作為主管，我希望 PMS 接不上時匯 Excel 今日房態（入住＋退房），照常排房、住客照常掃碼，以便斷線不停業。

**為何為此優先級**: 排房輸入與住客入口的生死線；SaaS 開放非自家 PMS 由 adapter 介面承接。

**獨立驗證方式**: 以模擬 adapter 餵訂房檔；再走匯入路徑驗同一份「今日待掃清單」殊途同歸。

**驗收情境**:
1. **Given** 模擬 PMS adapter 推來含 `{vip:true}` 的訂單，**Then** reservations_cache 更新且自動開準備工單（type=amenity、source=system）。
2. **When** `POST /admin/occupancy/import`（CSV 欄位見假設 A9），**Then** 逐列驗房號存在才收、不存在列報錯退回；reservation_ref 合成 `excel:日期:房號`、source=excel；匯入留痕事件 `checkout_imported`。
3. **Given** PMS 恢復同步且真訂單與匯入列同房住期重疊，**Then** 匯入列自動標 superseded_at；「當前訂單」查詢一律排除已作廢列。

**功能需求（FR）**:
- **FR-080**: PMS 對接 MUST 走 adapter 介面（事件訂閱優先、輪詢兜底）；MVP 僅實作模擬 adapter（假設 A2），真 adapter 後補不改介面。
- **FR-081**: 同房住期重疊且皆未作廢 MUST 被 DB EXCLUDE 約束拒絕。

### 使用者故事 10 — 老闆儀表板與「出事才響」 (Priority: P2)

作為老闆，我希望打開就看到當日數字卡片與一行摘要、只在出事時收推播。

**為何為此優先級**: 讀側投影，依賴事件流累積。

**獨立驗證方式**: 重放種子事件→worker 刷新 stats_daily→`GET /owner/dashboard` 比對卡片值；低分評分觸發 push outbox 列。

**驗收情境**:
1. **When** `GET /owner/dashboard`，**Then** 回四卡（sellable、avgResponseMin、rating、inspectionReturns）＋摘要，值來自預算好的 stats_daily，API 不現算。
2. **Given** 取消單（任一原因），**Then** 不進完成數、平均工時、評分與任何卡片。
3. **Given** 住客評 2 星（≤ 租戶門檻），**Then** 排 owner push 進 outbox→Kafka→推播消費者（MVP 落日誌＋資料列，假設 A1）。

**功能需求（FR）**:
- **FR-090**: 推播來源 v1 僅兩個：低分評分、主管手動上報（`POST /admin/alerts`，假設 A11）；SLA 升級鏈不實作、欄位保留。
- **FR-091**: stats_daily/room_memory/phrase_counter/cleaning_time_stats 由 worker 自 Kafka 事件流刷新，皆可清空重放重建。

### 使用者故事 11 — 人力缺口試算 (Priority: P2)

作為主管，缺人時我希望看到工時缺口試算，自己決定延後哪些空房巡。

**為何為此優先級**: 只讀試算，依賴排房資料。

**獨立驗證方式**: 給定計畫＋班次容量種子，驗算式輸出。

**驗收情境**:
1. **When** `GET /admin/capacity`，**Then** 回「待掃工時合計 vs 班次容量」缺口；系統只試算，不自動延後。

**功能需求（FR）**:
- **FR-100**: 試算 MUST 讀 cleaning_plans/plan_rooms×cleaning_time_stats×tenant_config `shift_capacity`；平均未累積夠（<20 樣本）用標準工時。

### 使用者故事 12 — 通知板與已讀 (Priority: P3)

作為櫃檯，我希望發通知給部門或個人並看誰已讀。

**為何為此優先級**: 協作外圍，不阻塞核心迴路。

**獨立驗證方式**: 發→讀→已讀名單三步 API 驗證。

**驗收情境**:
1. **When** `POST /desk/notices`（recipients 部門/個人混選），**Then** 推播經 outbox 展開成一人一封；收件人打開即記已讀、重開不重記（PK (notice_id, staff_id)）。

**功能需求（FR）**:
- **FR-110**: 通知 CRUD＋`GET /desk/notices/{id}/reads`；逾時未讀升級不做（SLA 家族）。

### 使用者故事 13 — 遺失物登記與歸還 (Priority: P3)

作為櫃檯，我希望登記遺失物與歸還結案。

**為何為此優先級**: 獨立 CRUD，無狀態機耦合。

**獨立驗證方式**: 登記→歸還兩端點。

**驗收情境**:
1. **When** `POST /desk/lost-items`，**Then** 記房號、物品、拾獲人、status=held；照片僅掛紀錄、事件只記 hasPhoto。**When** `/return`，**Then** 結案。

**功能需求（FR）**:
- **FR-120**: 如上；狀態 `held`→`returned`。

### 使用者故事 14 — 交班單凍結快照與簽收 (Priority: P3)

作為員工，我希望看交班當下凍結的快照並簽收。

**為何為此優先級**: 讀側快照，依賴班末掃描時點。

**獨立驗證方式**: 班末產快照→任務再變→快照不變→簽收寫事件。

**驗收情境**:
1. **Given** 班末掃描產生交班單（假設 A8），**When** 其後任務再變化，**Then** 交班單內容不變；**When** `POST /staff/handovers/{id}/sign`，**Then** 寫簽收列與事件。

**功能需求（FR）**:
- **FR-130**: 快照由班末排程產生（主管亦可手動觸發）；簽收記 handover_signs。

### 使用者故事 15 — 員工「問一下」（降級版） (Priority: P3)

作為房務，我希望查 SOP 或這間房的過往記錄。

**為何為此優先級**: 知識庫讀側；MVP 不接 LLM（假設 A6）。

**獨立驗證方式**: 對種子 room_memory 與 sop_items 查詢比對。

**驗收情境**:
1. **When** `POST /staff/kb/ask`（房號或關鍵字），**Then** 回該房 room_memory 摘要＋sop_items 關鍵字命中項；LLM 介面留椿不啟用。

**功能需求（FR）**:
- **FR-140**: 如上；知識庫只讀事件流（Kafka），不寫任務。

### 使用者故事 16 — 租戶設定管理 (Priority: P2)

作為主管，我希望標準工時、SOP 與查房項目、班次容量、資訊頁、選單都可自店調整。

**為何為此優先級**: 「什麼都可調」NFR 的落地；P1 功能讀它（先以種子資料餵），寫入 API 可後行。

**獨立驗證方式**: `PUT /admin/settings/{key}` 改值→對應行為跟著變（如選單項停用後住客端不出現）。

**驗收情境**:
1. **When** `PUT /admin/settings/{key}`，**Then** 僅接受封閉鍵集合（見資料維度 tenant_config），記 updated_by/updated_at。

**功能需求（FR）**:
- **FR-150**: `GET /admin/settings`、`PUT /admin/settings/{key}`；值為 jsonb、結構依 key。

---

## 2. 邊界情況（gherkin 判定值來源；觸發值逐字承載）

- E-01 同房限流：10 分鐘內第 4 筆 `POST /guest/requests` → 429（第 3 筆仍成功）。
- E-02 掃碼限流：`/guest/scan` 按 IP 計，10 次失敗/10 分鐘（可調）→ 擋＋告警。
- E-03 空房或 token 已作廢掃碼 → 409 `room_vacant`，不簽 session、不建單。
- E-04 住客收回：state ∈ {triage, pool} 可收回；state=accepted 起 → 409 `state_conflict`。
- E-05 評分：1–5 整數；0 或 6 → 422；僅 done/inspected 後可評；重評 → 409。
- E-06 認領：state=pool 且 dept 相符才成功；跨部門或已被認領 → 409。
- E-07 查房：actor=原清房者 → 403；task.state ≠ done → 409；items 未涵蓋全部租戶查房項目 → 422。
- E-08 計畫調整：If-Match ≠ 現行 version → 409＋最新內容；成功 version+1。
- E-09 一房一天僅一筆未完成打掃單：`(tenant_id, room_id, work_date) WHERE type='cleaning' AND state NOT IN ('done','inspected','cancelled')` 第二筆被 DB 拒絕。
- E-10 同房住期重疊且皆未作廢訂單 → EXCLUDE 拒絕；當前訂單查詢一律 `superseded_at IS NULL`。
- E-11 冪等：同 `Idempotency-Key` 重送 → 回放上次結果（狀態碼＋body 一致，不重做）；outbox `(tenant, kind, idempotency_key)` 唯一。
- E-12 跨租戶資源 → 404（非 403）。
- E-13 task_events 禁 UPDATE/DELETE（DB 權限）；唯一例外=受控去識別化（執行記事件）。
- E-14 relay 同鍵串行：同 aggregate_key 最早一筆未 sent（含等重試）→ 不送後續列；別鍵照跑。
- E-15 取消單不進任何統計（完成數、平均工時、評分、卡片）。
- E-16 非法狀態轉移一律 409 `state_conflict`（守衛=一句條件式 UPDATE，0 列即衝突）。
- E-17 guest session 驗證：`expires_at > now()` 且 `revoked_at IS NULL`，缺一即 401。
- E-18 QR 輪替連坐：同交易撤銷該房全部未撤銷 session——輪替前掃過的人也立即失效。

---

## 3. 全域需求

- **GR-001 任務狀態機**: 九態 `triage|pool|accepted|doing|done|inspected|deferred|interrupted|cancelled`；合法轉移全集=資料維度 §狀態；任務狀態變更唯一入口=任務引擎模組；守衛為一句條件式 UPDATE＋event_seq 取號（禁止先 SELECT 再 UPDATE、禁止 SELECT max(seq)+1）。
- **GR-002 冪等寫入**: 所有 POST 帶 `Idempotency-Key`；processed_ops 記 (tenant_id, op_id)→result_ref，7 天清。
- **GR-003 outbox→relay→Kafka 骨幹**: 狀態變更＋task_events＋outbox 同一筆 Postgres 交易；relay（短週期輪詢）依 aggregate_key 串行搬上 Kafka（分區鍵：task_event/push 用 `task:<uuid>`、pms_status 用 `room:<tenant_id>:<uuid>`）；下游（SSE 推送、衍生投影、推播消費者、PMS 回寫消費者）只從 Kafka 訂閱；**Kafka 下游一律不回寫 tasks**；topic 全租戶共用、事件帶 tenant_id、消費端過租戶授權；失敗指數退避、`sent` 3 天清、`failed` 等人工。
- **GR-004 事件流唯一真相**: 會改 tasks 欄位的事件 payload 必填帶齊（created 帶建立快照：種類/來源/房號/訂單/標題/預估分鐘）；投影（tasks、room_memory、stats_daily、phrase_counter、cleaning_time_stats）可清空由 task_events 重放重建；CI 影子庫重放比對為驗收機制；增量訂閱一律走 Kafka、不拿 task_events.id 當書籤。
- **GR-005 SSE 即時推送**: 連線層流水號 stream_pos 跳號→客戶端重拉快照；推送模組每 15 秒自發心跳（不經 Kafka）；Kafka 消費延遲超門檻→推 `degraded` 事件（客戶端轉 5 秒輪詢）；SSE 容許掉事件，真相在資料庫。
- **GR-006 去識別化**: 個人層級工時與 SOP 統計按租戶保留期限去識別化（抹 payload 個資欄＋離職帳號代號化），不刪列、執行記事件、路徑受控（Quartz 排程）。
- **GR-NFR-001 即時**: 住客送出 2 秒內出現在內部看板；內部完成住客畫面自動更新。
- **GR-NFR-002 不掉單**: 弱網重送不重複；推播 best-effort、PMS 回寫重試到成功。
- **GR-NFR-003 部署形狀**: 單體應用＋worker 模組＋一個 Postgres＋代管 Kafka；MVP 單程序含 worker（profile 可拆，假設 A3）。
- **GR-NFR-004 可觀測**: OpenTelemetry trace 覆蓋 API→DB→Kafka 鏈路（pom 已含依賴）。
- **GR-NFR-005 可調**: 標準工時、SOP/查房項目、班次容量、資訊頁、選單、限流門檻皆 tenant_config，不寫死。

---

## 4. 關鍵實體

tenants（飯店租戶）、staff_accounts（員工帳號）、staff_devices（推播地址簿）、
rooms（房間，hk_state TMS 管／occupancy PMS 管）、room_qr_tokens（房間 QR 憑證）、
guest_sessions（住客 session）、reservations_cache（訂房檔唯讀副本）、
tasks（任務投影）、task_events（事件流水帳=唯一真相）、outbox（待送匣）、
processed_ops（冪等去重簿）、cleaning_plans＋plan_rooms（排房計畫與明細）、
cleaning_time_stats（房型平均打掃分鐘）、inspections（查房記錄）、
sop_items＋task_sop_checks（SOP 項目與勾記）、handovers＋handover_signs（交班快照與簽收）、
notices＋notice_reads（通知與已讀）、lost_items（遺失物）、leaves（請假）、
room_memory／stats_daily／phrase_counter（投影三張）、tenant_config（租戶設定）。

---

## 5. 資料維度與範例資料（自 clarified.md 逐筆承載）

### 5.1 資料維度

- **身分與唯一性**：實體表 PK=UUIDv7（app 層產號）；task_events/outbox 用 bigint 自動遞增（docs/12 例外一：id 不作訂閱書籤）；tenants.id bigint（例外二）。唯一性：token 全域 UNIQUE；(tenant_id, room_no)；(tenant_id, login_id)；(tenant_id, reservation_ref)；一房一枚 active QR（partial unique）；一班一份 published 計畫（partial unique）；一房一天一筆未完成打掃（partial unique，條件見 E-09）；(tenant_id, task_id, seq)；(tenant_id, kind, idempotency_key)；(tenant_id, staff_id, device_fingerprint)；(tenant_id, plan_id, room_id)；notice_reads PK (notice_id, staff_id)；processed_ops PK (tenant_id, op_id)；(tenant_id, key) on tenant_config。
- **關係與基數**：tasks N:1 rooms／reservations_cache（可空，guest 來源必填）／staff_accounts（可空）／cleaning_plans（可空）；task_events N:1 tasks；plan_rooms N:1 cleaning_plans、1:0..1 tasks（發佈後回填）；guest_sessions N:1 rooms＋reservations_cache；room_qr_tokens N:1 rooms；notice_reads N:M；全部外鍵為複合 (tenant_id, xxx_id)，被引用表補 UNIQUE (tenant_id, id)。刪除行為：業務上不硬刪（事件不滅、帳號停用不刪）。
- **optionality／值域**：以 docs/12 的 17 張逐欄位表為準；時間 timestamptz、純日期 date；值域 text＋CHECK（不用原生 ENUM）；「值域由 DB 擋、轉移由程式擋」。待補 9 張（sop_items、task_sop_checks、inspections、handovers、handover_signs、lost_items、leaves、room_memory、stats_daily、phrase_counter）由 data_model phase 依 docs/11 §5 職責表推導。
- **列舉（封閉集合）**：state 九值（預設依路徑：選單→pool、自由文字→pool/triage、排房→accepted）；事件 type 18 值 `created|triaged|assigned|claimed|started|completed|inspection_passed|inspection_failed|reassigned|returned_to_pool|interrupted|deferred|answered|rated|sop_checked|plan_adjusted|checkout_imported|cancelled`（可擴充，加值不改表；cancelled payload 原因 `guest_withdrew|manager_void|plan_superseded`）；dept `housekeeping|engineering|frontdesk`；type `amenity|repair|cleaning|other`；source `guest|desk|system`；role `frontdesk|housekeeping|engineering|manager|owner`；actor_role 另含 `guest|system`；hk_state `dirty|cleaned|inspected|out_of_order`；occupancy `occupied|vacant`；outbox kind `task_event|push|pms_status`、status `pending|sent|failed`（預設 pending）；計畫 status `draft|published|superseded`；QR status `active|revoked`；reservation source `pms|excel`。
- **狀態（合法轉移全集）**：triage→pool（櫃檯 assign）；triage→done（櫃檯 answer）；pool→accepted（同部門 claim）；（排房發佈）→accepted 起點；accepted→doing（start）；doing→done（complete）；done→inspected（查房全過）；done→accepted（查房退回）；accepted→pool（請假自動退、班末掃描、查房退回連動）；doing→interrupted（請假）；interrupted→pool（主管 requeue 附交代）；pool→deferred（主管）；deferred→pool（undefer／隔日自動）；triage|pool→cancelled（住客）；任何未完成態→cancelled（主管附原因）。終點=inspected、cancelled。備品/修繕/其他至 done 收評分即止。
- **保存**：歷史=task_events 只進不改；保留期限到→去識別化不刪列；processed_ops 7 天、outbox sent 3 天由排程清；交班單永久凍結。
- **PII**：住客姓名、自由文字敘述、員工姓名與登入帳號；去識別化路徑受控；password_hash 絕不存明文。
- **量級**：單店幾百房、每日數千事件、同時在線員工數十；平台事件量隨店數線性成長。
- **多租戶**：同庫同表、tenant_id 首欄；應用層 scope＋RLS＋複合外鍵三道牆；Kafka topic 共用、事件帶 tenant_id。
- **存取樣式**：待接池 (tenant, dept, state)；個人在手 (tenant, assignee_id, state)；單房歷程 (tenant, room_id, created_at)；住宿需求 (tenant, reservation_id, created_at)；任務歷程 (tenant, task_id, seq)；全序重放 (tenant, id)；relay partial index (aggregate_key, id) WHERE status='pending'；今日退房 (tenant, room_id, checkout_at)。

### 5.2 範例資料（逐筆，與 clarified.md 一致）

#### rooms（tenant_id=1）
| room_no | floor | room_type | hk_state | occupancy |
|---|---|---|---|---|
| 301 | 3 | standard_double | dirty | occupied |
| 305 | 3 | standard_double | cleaned | occupied |
| 502 | 5 | suite | dirty | vacant |

#### staff_accounts
| name | login_id | role | dept |
|---|---|---|---|
| 小美 | hk1 | housekeeping | housekeeping |
| 阿強 | mt1 | engineering | engineering |
| 王主管 | mg1 | manager | （空） |

#### tasks
| type | source | room | dept | state | assignee | 其他 |
|---|---|---|---|---|---|---|
| amenity | guest | 305 | housekeeping | pool | （空） | title=毛巾×2, reservation 綁當前訂單 |
| repair | guest | 305 | engineering | triage→pool | （空） | note=「冷氣不冷」規則引擎命中 |
| cleaning | system | 301 | housekeeping | accepted | hk1 | plan_id 回鏈、work_date=today、est_min=30 |

#### task_events（task=301 打掃單）
| seq | type | actor_role | payload |
|---|---|---|---|
| 1 | created | system | {type:cleaning, source:system, room, plan, est_min:30, title:退房清} |
| 2 | assigned | system | {assignee: hk1} |
| 3 | started | housekeeping | （空） |

#### reservations_cache
| reservation_ref | room | source | 住期 | attrs |
|---|---|---|---|---|
| PMS-88321 | 305 | pms | 10-18→10-20 | {vip:true} |
| excel:2026-10-19:301 | 301 | excel | 10-19→10-19 | （空） |
| PMS-88347 | 502 | pms | 10-17→10-19 | {pet:true} |

#### cleaning_plans／plan_rooms
- today×早班 published v2：301→hk1（est 30、route_seq 1）、305→hk2（est 10、route_seq 2）
- today×早班 draft（重產對照用，與 published 並存）
- 昨日×早班 superseded（歷史）

---

## 6. 成功標準

- **SC-001**: 住客從掃碼到送出需求 ≤ 3 步、免安裝 App。
- **SC-002**: 需求送出後 2 秒內出現在對應部門看板；內部完成後住客畫面免重整自動更新。
- **SC-003**: 同一操作弱網重送任意次，業務效果與一次相同（零重複單、零重複推播、零重複回寫）。
- **SC-004**: 查房退回後，第一輪「誰在幾點勾過什麼」仍可完整回溯。
- **SC-005**: 跨租戶資料存取成功次數 = 0（任何角色、任何端點）。
- **SC-006**: 投影表清空後由事件流重建，與線上資料逐列一致（CI 自動驗證）。
- **SC-007**: 取消單對當日所有統計數字的影響 = 0。
- **SC-008**: PMS 斷線整天時，憑 Excel 匯入即可完成排房，且新入住住客掃碼成功率不受影響。

---

## 7. 假設（前提與範圍邊界；不含新需求）

**範圍邊界（MVP phase 結構，plan stage 的切分輸入）**：
- P1=核心骨幹（故事 1–6＋GR 全部）；P2=營運閉環（故事 7–11、16）；P3=周邊協作（故事 12–15）。
- 明確不做（docs/11 原文）：SLA 升級鏈（sla_min/esc_level 留欄）、minibar、候補調度、欄位級離線合併（v1 先到先贏）、MICE／門鎖／OTA／BI、自助開通計費、裝置綁定。
- 前端（Flutter／內部 Web／住客 Web）不在本次範圍；後端互動介面=springdoc OpenAPI UI。

**定案依據（flow 授權 spec 定案）**：target=backend（交付物為 tms/ 後端；POC 為輸入非產出）；
Kafka 全鏈落地（使用者已拍板）；Quartz 跑租戶感知排程、relay 用短週期輪詢。

**假設清單（轉入最終 handoff Risks）**：
- A1 推播消費者 MVP 僅落日誌＋資料列，不接真 APNS/FCM。
- A2 PMS 以模擬 adapter 介面承接；真 adapter 逐家後補。
- A3 MVP 單一 Spring Boot 程序內含 worker 模組（profile 可拆）；與 docs/11「一台應用＋一個 worker」部署形狀的差異待 plan/ship 階段復核。
- A4 POC 頁面不由後端 serve、不改寫（localStorage 引擎與 REST 不相容）。
- A5 評分一次性、不可改。
- A6 `/staff/kb/ask` MVP 不接 LLM：room_memory＋sop_items 關鍵字比對；LLM（翻譯/分類建議/問答）一律可插拔介面留椿。
- A7 掃碼 IP 限流門檻 10 次失敗/10 分鐘，tenant_config 可調。
- A8 交班單由班末掃描排程產生快照；主管可手動觸發。
- A9 匯入 CSV 欄位：room_no, action(checkin|checkout), guest_name?, checkin_at, checkout_at, attrs?。
- A10 規則引擎 v1=租戶設定的關鍵字→部門映射，命中記 rule_id；命中 0 或 >1 部門→triage。
- A11 主管手動上報=`POST /admin/alerts {message}`→owner push（docs/11 提及來源但未給端點，本 phase 補形狀）。

---

## 8. 承載覆蓋帳本

| clarified.md 來源項 | 類型 | 在 spec.md 的去向 |
|---|---|---|
| §1 業務目標 | 標題段 | 文件導言＋SC-001～008＋假設 §範圍邊界 |
| §2 角色與認證表＋JWT 定案＋角色矩陣 | 標題段 | 故事 5（FR-040～043）＋故事 1（guest session） |
| §3 行為清單 1–17＋共同約定 | 標題段 | 1→故事 6；2→故事 1；3→FR-002；4→故事 3；5→故事 12；6→故事 2；7→FR-061；8→故事 9；9→故事 11；10→故事 7＋FR-071；11→故事 14；12→故事 15；13→故事 13；14→故事 16；15→FR-040；16→故事 4；17→故事 10；共同約定→GR-001/002＋E-11/12 |
| §4 狀態機＋合法轉移 | 規則 | GR-001＋資料維度 §狀態 |
| §4 邊界條目（18 條） | 規則 | E-01～E-18（同序逐條） |
| §4 事件類型 18 值 | 規則 | 資料維度 §列舉 |
| §4 其他 enum 8 組 | 規則 | 資料維度 §列舉 |
| §5 資料維度（8 項） | 標題段 | §5.1 逐項 |
| §6 範例資料 rooms ×3 | 範例資料 | §5.2 rooms（逐筆） |
| §6 範例資料 staff_accounts ×3 | 範例資料 | §5.2 staff_accounts（逐筆） |
| §6 範例資料 tasks ×3 | 範例資料 | §5.2 tasks（逐筆） |
| §6 範例資料 task_events ×3 | 範例資料 | §5.2 task_events（逐筆） |
| §6 範例資料 reservations_cache ×3 | 範例資料 | §5.2 reservations_cache（逐筆） |
| §6 範例資料 cleaning_plans/plan_rooms ×3 | 範例資料 | §5.2 cleaning_plans（逐筆） |
| §7 MVP 切分 P1/P2/P3 | 定案 | 故事 Priority＋假設 §範圍邊界 |
| §7 Kafka 深度定案 | 定案 | GR-003＋假設 §定案依據＋A1/A2 |
| §7 Quartz 深度定案 | 定案 | FR-060/070＋GR-006＋假設 §定案依據＋A3 |
| §7 POC serve 定案 | 定案 | 假設 A4 |
| §7 假設 A5–A10 | 假設 | 假設清單 A5–A10（FR-007/052/080、故事 14/15、E-02 引用） |
| §8 POC 落差表（7 列） | 規則 | 資料維度 §列舉（canonical enum）＋GR-001（狀態機照權威）＋假設 §範圍（SLA 不做、開關不做：查房必走／自由文字必 triage 為固定行為）＋FR-060（打掃 kind 以 title＋標準工時鍵承接） |
| §9 完成判準自檢 | 標題段 | 本檔 requirements-checklist.md |
