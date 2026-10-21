STATUS: RESOLVED

# 釐清後的結構化需求（clarify 輸出；specify 的輸入）

釐清方式：headless——score 缺口 G1–G6 逐項比對 docs/11、docs/12 原文與 flow 派工指示。
flow 已明文授權：「API 形狀、MVP 範圍切分、認證/授權、Kafka/Quartz 整合深度、
POC 頁面是否由後端 serve」由 spec 定案（或列入 Unresolved）。本檔所有定案均附依據；
無依據者標「假設」並將由編排器轉入最終 handoff 的 Risks。**無高影響的不可答缺口，
故不追寫 questions.md。**

## 1. 業務目標

把 docs/11 設計的「飯店任務交辦系統」後端在 `tms/`（Spring Boot 4.1.1、Java 25）落地：
多租戶 SaaS、單體應用＋背景 worker、一個 Postgres、Kafka 事件輸送帶。
v1 功能範圍 = docs/11 §1 的角色故事＋系統規則（「不在範圍內」清單除外）。
本次交付 = 後端 REST API＋狀態機＋outbox/relay/Kafka 骨幹＋排程 worker；
前端（Flutter/Web）不在本次範圍。

## 2. 角色與認證（G5 定案）

| 角色 | 入口 | 認證 |
|------|------|------|
| 住客 guest | `/guest/**` | 掃 QR 換 guest session（opaque token，存 `guest_sessions`，驗「未過期且未撤銷」）；不登入 |
| 櫃檯 frontdesk | `/desk/**` | `POST /auth/login` 帳密換員工憑證 |
| 房務 housekeeping／工務 engineering | `/staff/**` | 同上 |
| 主管 manager | `/admin/**` | 同上 |
| 老闆 owner | `/owner/**`（只讀） | 同上 |

**員工憑證落地（工程定案）**：JWT（HS256、TTL 12 小時），claims 含 tenant_id、staff_id、
role、dept；每請求另查 `staff_accounts.active` 擋停用帳號。依據：docs/11 §2「員工登入、
住客不登入」「每個請求都驗登入態與角色×動作」；docs/12 無員工 session 表 → 無狀態 token
不加表。登入時順帶登記推播裝置（staff_devices）。裝置不綁定（docs/11 §1 明文）。

**角色×動作矩陣**：docs/11 稱「定義在程式／設定，不在表裡」→ spec.md §授權矩陣逐項列出
（高風險項：清房者不能查自己清的房 403；跨部門不能認領 409；跨租戶一律 404）。

## 3. 行為清單（輸入/輸出/規則可驗證；API 形狀=G2 定案方向）

docs/11 §2 已給形狀的端點照抄為準；附錄 A 僅列名者由 api phase 依附錄 A 的
「動的表＋運作重點」補齊 I/O（工程推導，無 PM 歧義）。清單（依附錄 A 15 條需求）：

1. QR 簽發/輪替：`GET /admin/rooms/{no}/qr`、`POST /admin/rooms/{no}/qr/rotate`（輪替同交易撤銷該房全部 guest session）
2. 掃碼與需求：`POST /guest/scan`、`GET /guest/home`、`POST /guest/requests`、`GET /guest/requests`、`GET /guest/stream`（SSE）、`POST /guest/requests/{id}/cancel`、`POST /guest/requests/{id}/rating`
3. 資訊頁：tenant_config `guest_info_pages`，不進任務流
4. 櫃檯判讀：`GET /desk/triage`、`POST /desk/triage/{id}`（assign→pool / answer→done）
5. 通知板：`POST /desk/notices`、`GET /desk/notices/{id}/reads`（＋清單 `GET /desk/notices`）
6. 員工任務：`GET /staff/pool`、`POST /staff/tasks/{id}/claim|start|complete`、`POST /staff/tasks/{id}/sop/{item}`、`GET /staff/stream`（SSE）
7. 班表：`GET /staff/schedule`
8. 房態來源：worker PMS 同步（MVP 用模擬 adapter）＋`POST /admin/occupancy/import`
9. 人力缺口：`GET /admin/capacity`
10. 排房：`GET/PUT /admin/plans/{date}`（If-Match 版本）、重產＋發佈、`POST /admin/tasks/{id}/reassign|requeue|defer|undefer|cancel`、`POST /admin/leaves`
11. 交班：`GET /staff/handovers`、`POST /staff/handovers/{id}/sign`
12. 問一下：`POST /staff/kb/ask`（MVP 降級版，見 §7 假設 A6）
13. 遺失物：`POST /desk/lost-items`、`POST /desk/lost-items/{id}/return`
14. 租戶設定：`GET /admin/settings`、`PUT /admin/settings/{key}`
15. 登入：`POST /auth/login`
16. 查房：`POST /admin/inspections/{taskId}`
17. 老闆：`GET /owner/dashboard`

共同約定（docs/11 §2，全端點適用）：POST 一律帶 `Idempotency-Key`（processed_ops 去重、
重送回放上次結果）；路徑不帶店別、跨店 404；狀態轉移=一句條件式 UPDATE 守衛（0 列→409
state_conflict）＋event_seq 取號。

## 4. 核心規則（界線帶觸發值；gherkin 直接素材）

### 狀態機（九態，docs/11 §3；值域全名 docs/12）
`triage | pool | accepted | doing | done | inspected | deferred | interrupted | cancelled`
合法轉移（其餘一律 409 state_conflict）：
- triage →（櫃檯 assign）pool；triage →（櫃檯 answer）done（事件 `answered`）
- pool →（員工 claim，限同部門）accepted；排房發佈：建立即 accepted（唯一不經池的起點）
- accepted →（start）doing；doing →（complete）done
- done →（查房全過）inspected；done →（任一項不過）accepted（redo_count+1、清 started_at/done_at/SOP 勾記、房況回 dirty）
- 查房退回時原清房者已下班或請假 → 同交易連動 accepted → pool（兩筆事件：`inspection_failed`＋`returned_to_pool`）
- 請假：accepted → pool（自動）；doing → interrupted（不自動退，主管 `requeue` 附 progressNote → pool）
- 班末掃描：accepted（未 start）→ pool（事件 `returned_to_pool`，原因班末退池）
- pool →（主管 defer）deferred；deferred →（undefer 或 worker 隔日班前）pool
- triage/pool →（住客 cancel）cancelled（原因 guest_withdrew）；任何未完成態 →（主管 cancel，必附原因）cancelled
- 備品/修繕/其他到 done 即可收評分；只有 cleaning 走 inspected

### 邊界條目（觸發值）
- 同房限流：10 分鐘內第 4 筆 `POST /guest/requests` → 429（第 3 筆仍成功）
- 掃碼限流：`/guest/scan` 按 IP 另計；連續失敗達門檻（假設 A7：10 次/10 分鐘）→ 擋＋告警
- 空房或 token 已作廢掃碼 → 409 room_vacant，不簽 session、不建單
- 住客收回：state ∈ {triage, pool} 可收回；state=accepted 起 → 409 state_conflict
- 評分：int 1–5（CHECK）；0 或 6 → 422；僅 done/inspected 後可評；同單重評 → 409（假設 A5）
- 認領：state=pool 且 task.dept=員工 dept 才成功；跨部門 → 409；已被認領（state=accepted）→ 409
- 查房：actor=原清房者 → 403；task.state ≠ done → 409；items 必須涵蓋租戶設定的全部查房項目 → 否則 422
- 排房調整：`PUT /admin/plans/{date}` If-Match ≠ 現行 version → 409＋回最新內容；每次調整 version+1
- 一房一天僅一筆未完成打掃單：partial unique `(tenant_id, room_id, work_date) WHERE type='cleaning' AND state NOT IN ('done','inspected','cancelled')` → 第二筆插入被 DB 拒絕
- 同房住期重疊且皆未作廢的訂單 → EXCLUDE 約束拒絕；「當前訂單」查詢一律 `superseded_at IS NULL`
- 同一 (tenant, kind, idempotency_key) outbox 唯一；同 Idempotency-Key 重送 → 回放上次結果（狀態碼＋body 一致）
- 跨租戶資源存取 → 一律 404（不是 403）
- task_events 禁 UPDATE/DELETE（DB 權限層）；唯一例外=受控去識別化函式（執行本身記事件）
- relay 同鍵串行：同 aggregate_key 最早一筆未 sent（含等重試）→ 不送後續列
- 取消單不進任何統計（完成數、平均工時、評分、儀表板卡片）

### 事件類型（封閉集合，docs/12，可擴充）
`created | triaged | assigned | claimed | started | completed | inspection_passed |
inspection_failed | reassigned | returned_to_pool | interrupted | deferred | answered |
rated | sop_checked | plan_adjusted | checkout_imported | cancelled`
（cancelled payload 原因：`guest_withdrew | manager_void | plan_superseded`）

### 其他 enum（canonical，POC 用詞一律映射，G6 定案）
- 部門 dept：`housekeeping | engineering | frontdesk`（POC `maintenance`→engineering、`desk`→frontdesk）
- 任務種類 type：`amenity | repair | cleaning | other`
- 來源 source：`guest | desk | system`
- 角色 role：`frontdesk | housekeeping | engineering | manager | owner`
- 房況 hk_state：`dirty | cleaned | inspected | out_of_order`（TMS 擁有）；occupancy：`occupied | vacant`（PMS 擁有）
- outbox kind：`task_event | push | pms_status`；status：`pending | sent | failed`
- 計畫 status：`draft | published | superseded`
- QR status：`active | revoked`

## 5. 資料維度（backend 必檢清單逐條）

- 身分與唯一性：實體表 PK=UUIDv7（docs/12 §1）；task_events/outbox 用 bigint 序號（例外一）；tenants.id bigint（例外二）。唯一性條件全列於 docs/12 各表（token 全域 UNIQUE、(tenant_id, room_no)、(tenant_id, login_id)、(tenant_id, reservation_ref)、一房一枚 active QR、一班一份 published 計畫、一房一天一筆未完成打掃）。
- 關係與基數：tasks N:1 rooms/reservations_cache/staff_accounts/cleaning_plans；task_events N:1 tasks（UNIQUE (tenant_id, task_id, seq)）；plan_rooms N:1 cleaning_plans、1:0..1 tasks（發佈後回填）；notice_reads N:M（notices×staff）；全部複合外鍵 (tenant_id, xxx_id)。
- 欄位語意：docs/12 已逐欄位（17 張）；待補 9 張（§7）由 data_model phase 依 docs/11 §5 職責表推導（G1 工程定案）。
- 生命週期：狀態機見 §4；歷史=task_events 只進不改；投影可清空重放（CI 影子庫驗證為驗收標準）。
- PII 與保存：PII=住客姓名/自由文字敘述/員工姓名；保留期限到→去識別化（不刪列）、路徑受控且記事件；processed_ops 7 天清、outbox sent 3 天清。
- 量級：單店幾百房、每日數千事件、同時在線員工數十；全平台隨店數線性成長（docs/11 §1）。
- 多租戶：同庫同表＋tenant_id 首欄；應用層強制 scope＋Postgres RLS 第二道；Kafka topic 共用、事件帶 tenant_id、消費端過租戶授權。
- 存取樣式：待接池 (tenant, dept, state)；個人在手 (tenant, assignee, state)；單房歷程 (tenant, room_id, created_at)；住宿需求 (tenant, reservation_id, created_at)；relay partial index (aggregate_key, id) WHERE pending。

## 6. 範例資料（每核心實體 ≥3 筆；取自 POC seed 房號慣例＋docs 值域）

**rooms**（tenant_id=1）
| room_no | floor | room_type | hk_state | occupancy |
|---|---|---|---|---|
| 301 | 3 | standard_double | dirty | occupied |
| 305 | 3 | standard_double | cleaned | occupied |
| 502 | 5 | suite | dirty | vacant |

**staff_accounts**
| name | login_id | role | dept |
|---|---|---|---|
| 小美 | hk1 | housekeeping | housekeeping |
| 阿強 | mt1 | engineering | engineering |
| 王主管 | mg1 | manager | （空） |

**tasks**
| type | source | room | dept | state | assignee | 其他 |
|---|---|---|---|---|---|---|
| amenity | guest | 305 | housekeeping | pool | （空） | title=毛巾×2, reservation 綁當前訂單 |
| repair | guest | 305 | engineering | triage→pool | （空） | note=「冷氣不冷」規則引擎命中 |
| cleaning | system | 301 | housekeeping | accepted | hk1 | plan_id 回鏈、work_date=today、est_min=30 |

**task_events**（task=301 打掃單）
| seq | type | actor_role | payload |
|---|---|---|---|
| 1 | created | system | {type:cleaning, source:system, room, plan, est_min:30, title:退房清} |
| 2 | assigned | system | {assignee: hk1} |
| 3 | started | housekeeping | （空） |

**reservations_cache**
| reservation_ref | room | source | 住期 | attrs |
|---|---|---|---|---|
| PMS-88321 | 305 | pms | 10-18→10-20 | {vip:true} |
| excel:2026-10-19:301 | 301 | excel | 10-19→10-19 | （空） |
| PMS-88347 | 502 | pms | 10-17→10-19 | {pet:true} |

**cleaning_plans / plan_rooms**：today×早班 published v2（301→hk1 est30 seq1、305→hk2 est10 seq2）；today×早班 draft（重產對照）；昨日 superseded。

## 7. 定案與假設（依據標註；A 開頭=假設，轉入 Risks）

**MVP 範圍切分（G5 定案，flow 授權）**——spec.md 以「核心骨幹 → 營運閉環 → 周邊協作」
三個 phase 組織（phase 拆解細節是 plan stage 的事，spec 只給邊界）：
- P1 核心骨幹：多租戶/認證/授權、rooms/tasks/task_events/outbox/processed_ops、狀態機全守衛、guest 掃碼→建單→認領→完成全流程、櫃檯判讀、outbox→relay→Kafka→SSE、查房與退回
- P2 營運閉環：排房（產生/調整/發佈/重產對帳）、請假與中斷、班末掃描、deferred 回池、Excel 匯入、人力試算、儀表板投影、QR 輪替
- P3 周邊協作：通知板、遺失物、交班、問一下（降級版）、租戶設定 UI 面、去識別化排程
- 明確不做（docs/11 原文）：SLA 升級鏈（留欄位）、minibar、候補調度、MICE、自助開通、裝置綁定、欄位級離線合併

**Kafka 整合深度（G5 定案，使用者已拍板用 Kafka）**：outbox→relay→Kafka→
{SSE 推送、衍生投影、推播消費者、PMS 回寫消費者} 全鏈落地；topic 全租戶共用；
本機/測試用 testcontainers 或 embedded 替代。**推播消費者 MVP 僅落日誌＋資料列
（不接真 APNS/FCM）**（A1）；**PMS 回寫消費者打到模擬 adapter 介面**（A2，SaaS 開放
非自家 PMS 的決策以 adapter 介面承接，真 adapter 逐家後補）。

**Quartz 整合深度（G5 定案）**：租戶感知排程跑 Quartz（班前排房、班末掃描、deferred
隔日回池、統計/投影刷新、outbox sent 清理、processed_ops 清理、去識別化）；relay 輪詢用
Spring 排程短週期（不經 Quartz）。**MVP 單一 Spring Boot 程序內含 worker 模組**
（profile 可拆），與 docs/11「一台應用＋一個 worker」的部署形狀差異記入 Risks（A3）。

**POC 頁面是否由後端 serve（G5 定案）**：**不 serve、不改寫**。POC 以 localStorage
引擎運作、與 REST 契約不相容；後端互動介面=springdoc OpenAPI UI。POC 僅作語意參考（A4）。

**其他假設**：
- A5 評分一次性、不可改；done 或 inspected 後、session 有效期內可評（docs 未禁重評，取嚴）
- A6 `/staff/kb/ask` MVP 不接 LLM：查 room_memory（本房過往事件摘要）＋sop_items 關鍵字比對回傳；LLM 翻譯/分類建議同樣以可插拔介面留椿（docs/11 定位 LLM 僅輔助）
- A7 掃碼 IP 限流門檻 10 次失敗/10 分鐘（docs 只說「連續失敗到一定次數就告警」，值進 tenant_config 可調）
- A8 交班單由班末掃描同一排程產生快照（docs 未指明觸發者；主管亦可手動觸發）
- A9 Excel/CSV 匯入欄位：room_no, action(checkin|checkout), guest_name?, checkin_at, checkout_at, attrs?；逐列驗房號存在，reservation_ref 合成 `excel:日期:房號`（docs/12 已定合成規則）
- A10 規則引擎 v1=租戶設定的關鍵字→部門映射表（docs/11「關鍵字命中」），命中記 rule_id 可解釋；命中 0 或 >1 部門→triage

## 8. POC 與權威文件落差表（G6；一律以 docs/11、12 為準）

| POC | 權威 | 處置 |
|---|---|---|
| dept：maintenance / desk | engineering / frontdesk | 映射，canonical 入庫 |
| 事件名 claim/start/complete | claimed/started/completed（docs/12 全名） | 用全名 |
| settings.flow 開關（requireInspection、freetextNeedsTriage、allowSelfInspect） | 查房必走、自清自查必擋（硬規則） | 不做開關；自由文字進 triage 為固定行為 |
| slaByType 生效 | SLA 升級鏈 v1 不做（留欄位） | 不實作，sla_min/esc_level 留欄 |
| deferred→reactivate→accepted、interrupted→resume→doing | deferred/undefer→pool；interrupted→requeue→pool | 照權威狀態機 |
| 打掃「kind」（退房清/續住清/夜床/空房巡） | docs 無此欄；tenant_config cleaning 標準工時 | 以 task.title＋tenant_config `standard_cleaning_minutes` 鍵值承接，不加 enum 欄位 |
| 單租戶、無 triage 限流 | 多租戶、429/409 全套 | 照權威 |

## 9. 完成判準自檢

- 每個行為有輸入/輸出/規則：§3＋§4 ✅
- 術語收斂單一定義：§4 enum＋§8 映射 ✅
- 無阻斷缺漏/矛盾：矛盾全在 §8 以權威優先裁決；缺漏以 A1–A10 標假設 ✅
- 資料維度（含 ★ 四條）：§5、§6 ✅
