# 003 /implement 回交上游清單（重建版）

2026-11-16 重建：原紀錄放在 session scratchpad（/private/tmp），系統清理 tmp 後遺失。
本檔只保留「要回交上游決定」與「跨 phase 須知」的部分；逐題執行紀錄不重建。

## 已完成並驗證（4K–4Q）

| Phase | Feature | 結果 |
|---|---|---|
| 4K | 主管工作台/主管首頁 | 18/18 |
| 4L | 主管工作台/主管清單 | 15/15（後續回歸 13/13，情境數有調整） |
| 4M | 主管工作台（格子順序） | 13/13 |
| 4N | 主管工作台/查房 | 11/11 |
| 4O | 工務單/主管視角 | 23/23（開工前 17 紅） |
| 4P | 工務單/工務視角 | 27/27 |
| 4Q | 工務單/工務單房務端生命週期 | 13/13 |

後端 truth：`RunTruthFeaturesTest` 224/224。legacy `RunCucumberTest` 維持 5 紅基準（003 之前就有）。
`flutter test` 最後一次 484 綠、`flutter analyze` 0 error。

## 實作期間改過的 truth（已登記在 truth-delta 的「/implement 回饋修正」）

1. `ios-app/工務單/dsl.md` 步驟條「待開始」含 pool。
2. `backend/工務單/dsl.md`「尚未驗收」：已結案時必須是 no_action_needed。
3. `ios-app/主管工作台/dsl.md` 寬磚沒有副文（使用者 2026-11-12 要求移除「與房務主管看同一份」）。
4. `ios-app/待認領/待認領列表.feature:105` 主管排序修正。

## 後端 bug 修正（契約未改）

- `RepairService.ticketView` 漏填契約既有的 `assigneeName`，補 `LEFT JOIN staff_accounts`；`create` 的回應同樣補上。
  症狀：房務版「工務 阿德 已接單」一直退成「已接單」。已隨 4Q 修復並重跑 224/224。

## 待使用者決定（回交上游）

1. `repair_tickets.origin` 沒有 DB 欄位，目前由 `tasks.source` 推導。
2. 排程保養單沒有報修人，目前以處理人充當報修人。
3. 工務員工沒有個人班次（known risk 4）。
4. `forbidden_dept_pool` 錯誤碼的歸屬。
5. 公區工務單的推播對象。
6. 驗收退回後 `waitedMinutes` 是否重新計算。
7. truth 有暫停按鈕，App 沒有。
8. `no_action_needed` 的推播文案。
9. 後端 `readRepairTicket` 只回 `photoCount`，沒有 `photos` 明細 → 房務版詳情照片恆 0（無情境驗）。
10. 工務單還沒人接時，工務／主管狀態列寫「待認領」，ui-plan 寫「等工務接單」（無情境驗，交 4V）。
11. `ui/ticket.html` 房務版沒有步驟條，但 truth 要求 `ticket.stepper`；實作依 truth 放進既有時間軸。
12. 後端 403 時畫面顯示「你沒有查房的權限」未做：truth-delta 列為刻意不承接、App 到不了、無情境驗。

## 指揮官已裁決、併入 4U 的兩件

1. `me_screen.dart` 登出被攔的「前往」：主管改用主管視角開工務單。
2. `ticket_complete_sheet.dart` 照片提示「修繕完成必須拍照…」：選「不需處理」時改用 `repair_labels` 的照片規則換句或隱藏。

## 環境須知

- App BDD 需要 Docker（Postgres 5434 + Kafka 9094 + 隔離後端 8081）。原本的 `bdd-backend.sh`／`run-4i.sh` 放在 scratchpad，已隨 tmp 清理遺失，需重建。
- simulator udid `79F32D5C-7A71-47D6-AA8D-8142E68CFF48`，一次只能跑一個測試。
- 絕不停止或重啟使用者的 8080。

## 4R（T145、T146）結果

| 項目 | 結果 |
|---|---|
| `ios-app/推播/點開推播的去處.feature` | 19/19 綠 |
| 回歸 `ios-app/主管工作台/主管首頁.feature` | 15/15 綠 |
| 回歸 `ios-app/工務單/主管視角.feature` | 23/23 綠 |
| `flutter analyze` | 0 error、0 warning（9 個 info 是 `api_client.dart` 的 `use_null_aware_elements` 建議） |
| `flutter test` | 498 綠 |

4R 沒有改任何 App 程式碼。四個推播入口（冷啟動 `main.dart:37`、前景／背景 MethodChannel `push_gateway.dart:33`、FCM `firebase_push_messaging.dart:87`、通知中心列 `notification_screen.dart:76`）全部收斂到 `push_gateway.dart` 的 `_navigate` 單一 switch，T146 成立。

### 新增一筆回交上游

13. ~~`/actuator/health` 回 401~~ **已裁決並實作**：2026-11-16 使用者裁示開放 actuator。`SecurityConfig.java:46` 加入 `GET /actuator/health`、`/actuator/health/**` 的 `permitAll()`，只開 health，其餘 actuator 端點維持被 `anyRequest().denyAll()` 擋住（actuator starter 的預設 HTTP 曝光本來就只有 health）。待 4W 重啟後端時實測確認。

### 已知測試環境 flake

- `AppiumWorld.tapPushNotification()` 的通知橫幅點擊＋通知中心備援偶爾失手（19 個場景中約 1 次），症狀是 App 停在 iOS 的 `SBCoverSheetWindow`／`NotificationShortLookView`。重跑即綠，不是產品缺陷。
- `test/offline_queue_retry_test.dart` 在與 `flutter analyze`／BDD build 搶資源時會 timing flake，單獨重跑 4/4 綠。

## 4S（T147、T148）、4T（T149、T150）結果

| Feature | 結果 |
|---|---|
| `ios-app/待認領/待認領列表.feature` | 16/16 綠，一次就過，不需要改任何程式 |
| `ios-app/系統層/離線模式與連線異常.feature` | 16/16 綠（第三輪；前兩輪的紅全是測試層缺口，產品碼一行沒改） |
| 回歸 `ios-app/工務單/主管視角.feature` | 23/23 綠 |
| `flutter analyze` / `flutter test` | 0 error 0 warning / 498 綠 |

4S 本來就綠的原因：`landing.dart:6` 的 `landingShellFor` 只看 `role == 'manager'`，`dept` 只決定 `ManagerKind`（首頁格數），不影響待認領；`ManagerPoolScreen` 對兩個部門都傳 `canClaim: false`（`manager_pool_screen.dart:81、89`）。T148 成立：房務池員工端與主管端共用 `HousekeepingPoolList`、工務池共用 `EngineeringPoolList`，`canClaim` 各自只控制一個 `onClaim`／`if`。

T150 成立：員工四個動作（`task_screen.dart:256、284、301`、`pool_screen.dart:165`、`hold_sheet.dart:130`）與主管三個動作（`inspection_screen.dart:93`、`ticket_screen.dart:209`、`ticket_verify_fail_sheet.dart:49`）走的都是 `ConnectionStatus.instance.isOffline` + `showOfflineBlockedAlert()`（`system_status.dart:218`）同一個入口。

## /implement 期間的第五筆產品修正

**驗收不通過送出後偶發 Flutter 紅屏** `'_dependents.isEmpty': is not true`（`framework.dart:6281`，`InheritedElement.debugDeactivated()` 的 assert）。線上與離線路徑都會發生，間歇性。

成因：`ticket_screen.dart` 原本在 `_sendVerify` 裡就 `setState(() => _verifyOutcome = outcome)`，那時不通過面板還蓋在上面、原因輸入框還有 focus，底下那層卻已經被換成 `TicketVerifyResultCard`，面板才 `Navigator.pop()`。

修法（commit `ef22e09`）：新增 `_pendingVerifyOutcome` 欄位，`_sendVerify` 只暫存不 `setState`；`_openFailSheet` 等 `TmsSheetRoute.show` 回來、面板確定關掉之後，才 `setState` 套用。契約與 truth 都沒變。

## 本輪裁決的三件事

1. **殘留「主管審核」文案要改，而且本來就在 003 範圍內**（依據 `plan.md:108`），不是待決上游。`repair_sheet.dart:240` 的送出鈕改成「送出給工務」／「送出急件」（急件那句的「直接給工務」拿掉「直接」——沒有審核之後不存在「不直接」的另一條路）；`repair_ticket_form_sheet.dart:499、507` 兩句說明拿掉「不經主管審核」「不用主管審核」。連帶改 `task_sheets_parity_test.dart`、`eng_create_ticket_test.dart` 的對應斷言。
2. **主管版工務單詳情不該有自己的畫面根 id**。truth 寫明主管視角「以工務視角的版面為底，只換底部動作列」，是同一張畫面同一個根。所以刪掉宣告了卻從未畫出來的 `ManagerIds.ticket = 'managerTicket'`，`PushThenSteps.java:82` 等它的那段一併拿掉（改前那段跑不到，所以 4R 才會綠；truth 一旦加主管點推播進工務單的場景就會必紅）。
3. **開放 actuator**（使用者 2026-11-16 裁示）。`SecurityConfig.java:46` 加 `GET /actuator/health`、`/actuator/health/**` 的 `permitAll()`，其餘 actuator 端點仍被 `anyRequest().denyAll()` 擋住。

## 後端回歸（T154）：Testcontainers 的真正死因與 003 的判定

**Testcontainers 連不上 Docker 的根因不是 OrbStack 不相容。** 錯誤訊息是 `client version 1.32 is too old. Minimum supported API version is 1.40`：OrbStack 帶的 Docker 是 29.8.2，已移除 API 1.40 以下，而 docker-java 在沒被告知版本時會退回 1.32。試過 `DOCKER_HOST`、`DOCKER_API_VERSION`、升 Testcontainers 到 1.21.3 都無效，**解法是把 `-Dapi.version=1.44` 傳進 surefire 的 forked JVM**：

```
JAVA_HOME="$(/usr/libexec/java_home -v 25)" TESTCONTAINERS_RYUK_DISABLED=true \
  ./tms/mvnw -f tms/pom.xml test -Dtest=RunTruthFeaturesTest \
  -DargLine="-Dapi.version=1.44"
```

**不能用 `-Dcucumber.features` 只跑 003 的目錄。** `RunTruthFeaturesTest` 用 `@SelectDirectories` 選檔，外部一旦塞 `cucumber.features`，glue 限制就失效，`BddSpringConfiguration` 與 `integration.stepdefs.CucumberSpringConfiguration` 兩個 `@CucumberContextConfiguration` 會相撞。只能跑全套再過濾。

**003 的後端範圍判定：乾淨。** 全套 97 個紅場景裡，落在 003 Test Scope 的只有 4 個，而且同一個根因——004 的 commit `ff4909e` 在 `主管待辦空清單與權限.feature:11` 與 `推播/員工推播事件.feature:5` 加了「值班主管」，但 `Terms.java:140` 的部門對照表還不認識它，Background 就炸了，整支檔被拖下水。truth 那邊其實已經寫好（`backend/dsl.md:44` 訂了「值班主管 → role `duty_manager`」、`:59` 說明它沒有部門），只是 stepdef 沒跟上。其餘 93 個紅全在 004 新建的 `櫃檯看板與派單/`、`員工事件串流/` 底下。**這 4 筆是 004 的待辦，不是 003 的退步。**

## 回交上游：BDD harness 的測試時鐘 bug

`首頁今日進度.feature` 的「預估剩餘工時」會紅（期望 135、實際 145），**不是產品缺陷**。

算術：未完成的單扣掉已做分鐘是 135，完全不扣是 150，實際 145 = 150 − 5。也就是 310（已中斷，已做 5 分）有扣，305（進行中，已做 10 分）沒扣。

根因：`app/bdd-support/.../BackendChannel.java:668` 在建 `doing` 狀態的任務時，用 **`OffsetDateTime.now()` 真實牆鐘**回推 `segment_started_at`；而後端 `TaskExecutionService.java:807` 的 `remainingMinutesOf()` 用的是被 `/api/v1/test/clock` 撥到劇本時間（10:00）的 `Clock`。於是 `segmentStartedAt.isBefore(now)` 為 false，那段 live segment 整個被跳過。310 是 `interrupted`，走 `:671` 那一支（`worked_sec = ?, segment_started_at = NULL`），不依賴時鐘，所以正常。

**原本推論「只要牆鐘晚於劇本的 10:00 就必紅」已被實測推翻。** 2026-11-16 傍晚（牆鐘遠晚於 10:00）用 `run-all-app.sh` 跑同一支，5/5 綠。程式碼位置與算術觀察照留，但判定改為**間歇性、觸發條件未明**：可能 `run-all-app.sh` 這條路徑有重設測試時鐘的步驟，或建資料與讀進度之間的時鐘關係與推論不同。要動 harness 之前得先找出它何時會紅，不要照「必紅」去改。這支 feature 不在 003 Test Scope 內，`app/bdd-support/**` 又是 004 的地盤，所以 003 不處理。

## T155（App 回歸）結果：兩輪全綠

第一輪 11 支、176 個場景，零紅。binary 是 `2aaa2bb` 之後重新 build 的。

| Feature | 場景 |
|---|---|
| `帳號與登入/登入與登出守衛` | 18 |
| `主管工作台/主管首頁` | 15 |
| `主管工作台/主管清單` | 13 |
| `主管工作台/查房` | 11 |
| `工務單/主管視角` | 23 |
| `工務單/工務視角` | 27 |
| `工務單/工務單房務端生命週期` | 13 |
| `推播/點開推播的去處` | 19 |
| `待認領/待認領列表` | 16 |
| `系統層/離線模式與連線異常` | 16 |
| `今日進度/首頁今日進度`（範圍外，另行確認） | 5 |

第二輪只跑 `工務單/主管視角`（23）與 `系統層/離線模式與連線異常`（16），仍然全綠。兩輪的 log 裡 `grep _dependents.isEmpty` 都是零命中，所以 `ef22e09` 對驗收不通過紅屏的修法成立（那條是間歇性的，一輪綠不足以判定，故跑兩輪）。

`flutter analyze` 0 error、0 warning、9 個既有 info；`flutter test` 498 綠。

## T154 的狀態：未勾，不是 003 的問題

003 的後端範圍乾淨，但 Test Scope 裡有 4 個場景被 004 的 `值班主管` 卡住（見上一節），所以「全綠」這個條件在 004 補上 `Terms.java` 的對照之前達不到。不自行放寬判準、也不代為修改 004 的地盤，留白等上游。

## T153（ui quality gate）：第二次判定通過

第一次不通過的四件（D1 格子數字、D3 已結案狀態列、D4 通知中心分類、D6 工務主管空狀態）由 fresh-context agent 逐項複驗，全部成立且與雛形逐值對齊：`displayTileCount` 44／`displayTileCountWide` 56（雛形 `manager.css:49`、`:56`），`brandPrimaryDark = #0A5C64` 與 `manager.css:2` 逐字相同。

**最高風險的那題已排除**：為了做「已結案實底白字」而在 `TmsCalloutTone` 加的 `title`／`body` 欄位，預設值與原本寫死的 `TmsColors.label`／`secondaryLabel` 完全相同，所以既有六個 tone 的渲染結果等價，沒有任何畫面變色。全專案也沒有任何地方依賴 `TmsCalloutTone` 的 index 或 `.values`，把 `closed` 插在第二位是安全的。`TmsText.displayStat` 未被動到，房務／工務首頁與兩個釘樁不受影響。

複驗後補上的三件（commit 見下）：
- `callout_test.dart` 的 tone 色表補 `closed` 一列
- `manager_home_tiles_test.dart` 補格子數字 44／56 與兩個分色的斷言（原本這兩項視覺要求完全沒有測試釘樁，下次很容易退回去）
- `tms_callout.dart` 的 closed 圖示改用自己的 `Paint`，不再在 switch 裡就地改共用的 `stroke`

## 回交上游：UI 尾巴待辦（003 刻意不做，需要裁決或設計定案）

1. **驗收通過完全沒有成功回饋。** `ticket_screen.dart:226` 通過後只 `Navigator.maybePop()`，API 回的 `outcome` 在 `result == 'passed'` 時被丟掉（`:193-196`），主管只能靠清單少一列推測。ui-plan:108 要的「頂部短暫提示『W-0108 已結案』」在現有元件庫沒有低成本解：`core/widgets/` 34 支裡只有 modal 對話框、錨定教學泡泡與面板，沒有任何 transient banner。**而且不能改用全頁結果卡**——`ticket_verify_result_card.dart` 的標題寫死「退回重修」，套到通過上會違反 truth。
2. **ui-plan:108 後半「從推播或通知中心進來的，成功後留在詳情、狀態列換成已結案」未實作**，`_pass()` 一律 pop。要做得在畫面之間傳入口來源。
3. **文案衝突待裁決**：ui-plan:51 要工務單卡標籤寫「已完修 · 等驗收」，truth 與 `repair_labels.dart` 只寫「已完修」，`status_chip_test.dart:152-157` 釘著短版。
4. **「已結案」在狀態列是品牌深色實底、在卡片標籤卻是灰底**（`repair_labels.dart:152-157` 走 `neutralContainer`，`status_chip_test.dart:158-163` 釘著）。雛形 `manager.css:76` 兩處都是深底。這是 CLAUDE.md「風格修改要全文一次掃完」的同一組。
5. **「無法修復」與「重修後再驗」目前同為琥珀**（`manager_queue_row.dart:56-69`），同一列同時出現會是兩顆一樣的色；雛形對這兩者是分色的（`.chip.cannot` vs `.chip.redo`）。
6. **定期保養標籤的紫色**（雛形 `manager.css:69` 的 `#EDE7FF`/`#4B2FA6`）未採用，App 色票沒有紫色 token。ui-plan 本體只要求「有一個標籤」，沒寫顏色，等設計定案。
7. **ui-plan:20 四種語氣裡的「等待（灰）」仍未落地**：雛形對 `pool` 與未重修的 `accepted` 用灰（`ui/ticket.html:112-113`），App 的 pool 用品牌淺底（`ticket_detail_engineering.dart:325`）、`accepted` 根本不畫狀態列。

## 2026-11-16 使用者裁決：UI 尾巴七項的去留

裁決原則：**先看雛形，雛形沒畫的才照指揮官建議**。雛形在 `ui/`。

| # | 項目 | 裁決 | 依據 |
|---|---|---|---|
| 1 | 驗收通過的成功回饋 | 做，照雛形 | `manager.css:166` 的 `.toast`、`ticket.html:59-66` |
| 2 | 從推播進來的留在詳情頁 | 做 | `ticket.html:169-175` 的 `doPass()` 依 `from` 分流 |
| 3 | 「已完修」vs「已完修 · 等驗收」 | 用短版「已完修」 | 使用者裁決；**ui-plan:51 那句要改**，程式不動 |
| 4 | 已結案兩處同色 | 統一深底白字 | `manager.css:73` `.chip.closed` |
| 5 | 重修後再驗／無法修復撞色 | 拆色，用雛形的 redo 色票 | 見下方更正 |
| 6 | 定期保養紫色 | 採用 | `manager.css:69` `#EDE7FF`/`#4B2FA6` |
| 7 | 等待的灰 | 只做 pool；accepted 照 truth 不做（已裁決） | 見下方 |

### 更正：第 5 項先前的說法有誤

先前寫「雛形對這兩者是分色的」**不正確**。雛形 `queue.html:62` 的「重修後再驗」用的是 `.chip.returned`，和 `.chip.cannot` 同為琥珀，**雛形自己也撞色**。採用的是雛形另外定義、用在房務查房佇列「重做後再查」（`queue.html:50`）的 `.chip.redo{background:#FFF1C2;color:#6B4E00}`（`manager.css:71`）——那是「重修後再驗」的同義情境，所以色票有雛形依據，不是憑空調色。

### 第 7 項：pool 做了，accepted 做不了（truth 擋住）

- **pool**：`ticket_detail_engineering.dart:325` 的 tone 由 `info`（品牌淺底）改 `neutral`（灰），對上雛形 `ticket.html:112` 的 `.statusline.neutral`。文案「等工務接單」本來就對（`repair_labels.dart:242`），沒有動。補了釘樁 `ticket_detail_test.dart`「還沒有人接的單用等待的灰色」。
- **accepted**：雛形 `ticket.html:113` 要求未重修的 accepted 畫一條灰色狀態列「待開始 · {處理人}」，**但 truth 明文不允許**。`ios-app/工務單/dsl.md:76` 把工務端與主管視角的狀態列文案窮舉為五種：等工務接單、已完修 · 等主管驗收、退回重修 · {判定人} 判定不通過、已結案 · {驗收人}驗收、已結案 · 不需處理——**沒有待開始／已接單**。`ticket_detail_test.dart:363-367、497-502` 兩條釘樁也釘著「accepted 時工務視角沒有狀態列」。

  實作過一版再整個退掉（新增 `TmsCalloutKind.ticketAccepted` + `_acceptedCallout`），退掉的理由是 truth 與實作衝突時不自行改 truth。

  **2026-11-16 使用者裁決：truth 為準，雛形 `ticket.html:113` 的這一格不採用。** 理由：那條狀態列只寫「待開始 · {處理人}」，而步驟條已經停在「待開始」、資訊卡也有處理人，資訊重複，truth 把它排除掉是合理的取捨。`dsl.md:76` 的五種窮舉維持不動，`ticket_detail_test.dart:363-367、497-502` 兩條釘樁維持不動。**這是刻意的偏離，不是漏做**——未來有人拿雛形逐格對照時不要「修」它。

## 2026-11-16 後端兩項修正（使用者指定要修）

### 排程保養單的報修人

查證後**事實與原本的記錄不同**，據實更正：production 根本沒有「建立排程保養單」的路徑。`tasks.source='system'` 的修繕任務在 main 裡只有一處會產生——`RepairService.java:355-369` 的追蹤單（完修回報「暫時處理」時自動建的），它的 `reporter_id` 複製自原單，是**真實的原報修人**。塞替身的是 BDD fixture（`tms/src/test/.../RepairGivenSteps.java:131-137`，來源為定期保養且報修人留空時塞處理人）。

而契約其實早就站在正確那邊：`specs/truth/contracts/components/schemas/shared.yaml:772-776` 與 `主管工作台.yaml:124-127` 都已寫明 `reporterName` nullable、「定期保養單由排程建立、沒有報修人時為 null」。**所以這是實作沒跟上契約，不是契約缺口。**

修法：`RepairService.java` 新增 `isScheduled()`，`reporterName` 在排程單時回 null（同時涵蓋 `readRepairTicket`、`listByRoom`、`precheck.duplicate` 三個出口），`ManagerQueueService.java:88-98` 待驗收清單同樣。沒動 `reporter_id` 的 NOT NULL、沒有新 migration、推播收件人仍用 `reporter_id`。

**順帶修掉一個既有的語意錯**：追蹤單的 `source` 也是 `system`，照原本「source=system 即 scheduled」的推導，追蹤單會被報成定期保養——在待驗收清單上會掛紫色的「定期保養」標籤，改完之後還會連報修人一起吐 null。所以 `isScheduled()` 加上 `origin_ticket_task_id IS NULL`：真正的定期保養才算 scheduled。三個 SQL 都補了這個欄位。truth 對追蹤單只斷言 DB 欄位，沒有場景釘 origin，所以這是純修正。

### `readRepairTicket` 的 photos 明細

`RepairService.java` 的 `ticketView` 與 `create` 都加回 `photos` 陣列（`purpose='report'`，ORDER BY taken_at, id），結構與既有 `completionPhotos` 一致（`id`/`status`/`takenAt`），`photoCount` 原封不動。契約 `工務單.yaml` 的 `RepairTicket` 本來就有 `photos`，同樣是實作沒跟上。

### App 端

新增 `repairReporterText()` 與 `repairReporterMetaText()`（`repair_labels.dart`）：`origin == 'scheduled'` 時顯示「系統排程」，對上雛形 `ui/ticket.html:90`。套用在工務視角標題列與資訊卡「報修人」、房務視角時間軸首筆與報修人欄。補了兩則釘樁（定期保養寫系統排程／一般單照常寫報修人）。

### 驗證

後端全套 truth：`Tests run: 360, Failures: 0, Errors: 160`，**AssertionError 零筆**。與改動前的 baseline 逐場景名稱 diff：97 個失敗場景名稱**完全相同**（新增 0、消失 0），失敗全是 004 新 feature 的 UndefinedStep 與「值班主管」部門對照缺口。

## 回交上游：truth 缺口（這兩個缺陷都沒有測試保護）

1. `backend/工務單/dsl.md:79` 的「`"{單號}" 共有 {張數} 張照片`」只查 `photoCount`，不查 `photos` 明細——所以「只回 count 不回明細」這個缺陷跑再多次 truth 都不會紅。
2. `工務單/` 底下**完全沒有定期保養的場景**，`reporterName` 為 null 這件事同樣沒有任何斷言（只有 `主管工作台/主管待辦清單.feature:31,67` 有一張 scheduled 單，但只斷言 origin 與完修結果）。
3. `backend/dsl.md:65`「已有以下工務單：」沒有寫明 fixture 在定期保養單上掛替身報修人這件事（`RepairGivenSteps.java:131-137`）。
