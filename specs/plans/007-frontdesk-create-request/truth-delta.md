# Truth Delta: 007-frontdesk-create-request

**Plan Package**: `specs/plans/007-frontdesk-create-request`

**Truth Root**: `specs/truth`

> 本檔由 truth owner skill（`/technical-research`、`/api-plan`、`/data-plan`、`/dsl-refine`）透過 `/truth-delta` 追加。
> `/specify` 只建立骨架，不得寫入任何 `specs/truth/**` 的實際改動。
> 本輪的預期影響見 `spec.md`「對既有行為的影響」：代開單面板的畫面行為與櫃檯代開單的後端動作為 ADD；「代開單」不再是尚未開放的入口、代開工務單的照片／來源／權限／標題、住客每房額度不算代開的單為 MODIFY；狀態、部門、類型、來源、修繕分類的值不動。

## /specify 預期影響（待 truth owner 確認，非實際改動）

下表是 `/specify` 依 spec 推得的預期，給後續 truth owner skill 當起點；每一列都要由對應的 skill 核對後，才以正式列寫進下面各段。

| 預期動作 | Truth 規格 | 預期改動 | 依據 | 交給 |
| --- | --- | --- | --- | --- |
| MODIFY | `specs/truth/features/frontdesk-web/共用外框/尚未開放的入口.feature` -> Rule「「通知板」「遺失物」與「代開單」照原型畫出來，點了沒有反應，也不會改到任何資料」 | 從 Rule 名與「例子」拿掉「代開單」；「通知板」「遺失物」照舊點不進去 | FR-001、FR-038 | `/dsl-refine` |
| NOOP（待確認） | `specs/truth/features/frontdesk-web/共用外框/側欄與工具列.feature` | 已斷言工具列有「代開單」，預期不需修改 | FR-001 | `/dsl-refine` |
| ADD | `specs/truth/features/frontdesk-web/代開單/`（新模組，含 `dsl.md`） | 代開單面板：入口與預填、房號欄與建議房號、住客卡、空房提示、常用品項、其他需求、類似單提醒、送出摘要、送出鈕、送出後的畫面、關閉不確認 | 使用者故事 1 至 5、FR-001 至 FR-039 | `/dsl-refine` |
| NOOP（待確認） | `specs/truth/features/frontdesk-web/總覽看板/卡片內容.feature`、`單筆詳情/詳情面板內容.feature`、`單筆詳情/歷程.feature` | 「櫃檯開單」「櫃檯代開（{姓名}）」「櫃檯代開「{標題}」，進{部門}待認領」已定案，預期不需修改 | FR-013 | `/dsl-refine` |
| ADD | `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` -> 櫃檯代開單 | 新動作：一個房號一次送出多張單（品項與數量、其他需求與部門）；房務開一般單、工務開工務單；同一個交易內開完這一次的所有單（一般單與工務單），任何一張開不成就全部不開；冪等（失敗後不改內容再送算同一次）；錯誤碼沿用 `forbidden`、`not_found`（房號不存在）、`validation_failed`；`frontdesk`、`manager`、`duty_manager` 可用；不算住客每房額度 | FR-011、FR-016、FR-022、FR-023、FR-034 至 FR-037 | `/api-plan` |
| ADD 或 NOOP（待判定） | `specs/truth/contracts/groups/現場作業/櫃檯看板與派單.yaml` 或 `住客與房間/房間.yaml`、`訂房檔副本與房態匯入.yaml` | 代開單面板要的讀取：房號是否有效、在住住客（姓名、VIP、入住退房日期）與建議房號、同房號還沒結束的單對應哪個品項（櫃檯代開、住客勾選、住客打字後被對應）、是不是住客打字的；代開 12 品項與住客選單項目鍵（`GuestTenantConfigReader.java:28-32`）的對照方式；看板資料是否已足夠 | FR-005、FR-025 至 FR-027、FR-030 | `/api-plan` |
| MODIFY | `specs/truth/contracts/groups/現場作業/工務單.yaml` -> `createRepairTicket`，或共用的開工務單元件；`specs/truth/features/backend/工務單/` | 櫃檯代開時改三處既有規則：一、免照片（既有至少 1 張，`photo_required`，`RepairService.java:495`、`:673-676`）；二、一律記來源 `desk`、待認領來源分流 `desk_forward`，不看開單人部門（既有依部門：工務部門記 `staff`、非櫃檯非工務被擋，`RepairService.java:513-516`、`:571-575`），`frontdesk`、`manager`、`duty_manager` 都放行（值班主管現在連 `POST /api/v1/repair-tickets` 都進不去，`SecurityConfig.java:71-72`）；三、標題是「{品名}有問題」或其他需求原文，不是「{分類名} 工務單」。改在共用元件還是櫃檯自己的動作，由 `/api-plan` 決定 | FR-016、FR-017、FR-034 | `/api-plan`、`/dsl-refine` |
| MODIFY | 住客掃碼的每房額度（`RoomRequestRateLimiter.java:22-23`、`:29`）與 `specs/truth/contracts/groups/住客與房間/住客掃碼.yaml` 的額度說明、對應的後端 feature | 既有計數條件 `source IN ('guest', 'desk')`，代開的單佔住客名額；改成只算住客送的，代開的單不算、也不受限 | FR-037 | `/api-plan`、`/dsl-refine` |
| ADD | `specs/truth/features/backend/櫃檯看板與派單/` -> 代開單 | 後端規則：每個品項一張單、標題組法、部門類型與修繕分類對應、直接進待認領不經 `triage`、全部成立或全部不成立、重送不重開、三種角色可用、不算住客每房額度、歷程操作者與工務單報修人 | FR-011、FR-016 至 FR-019、FR-022、FR-023、FR-034 至 FR-039 | `/dsl-refine` |
| MODIFY（待判定） | `specs/truth/data/data-model.dbml` -> `tasks`、`repair_tickets` 的 note | 記下櫃檯代開的單（來源 `desk`、直接 `pool`）；類似單提醒若要記「從哪個品項開出」需要的資料；主管代開時工務單的報修人。欄位與值域預期不動 | FR-016、FR-030、FR-039 | `/data-plan` |
| NOOP（待確認） | `specs/truth/techstack.md` | 沒有引入新技術，預期不需修改 | — | `/technical-research` |

## /technical-research

## /api-plan

## /data-plan

## /dsl-refine
