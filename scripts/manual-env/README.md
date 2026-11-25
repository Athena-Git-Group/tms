# 手動測試環境（manual-env）

這個資料夾讓你在自己的 Mac 上，用手把整條核心流程走一遍。住客用手機（或電腦瀏覽器）掃房間 QR 提出需求，
櫃檯在網頁上看到並派給房務或工務，員工在 iOS simulator 上的員工 App 認領、開始、完成，主管驗收工務單，
最後住客看到「已完成」並給評分。

## 這套環境跟自動驗收測試有什麼不同？

驗收測試每個場景開始前都會清空 `tms` 資料庫，資料留不住。這套環境用自己的資料庫和埠號，
測到一半的資料不會被測試清掉，兩邊可以同時跑。

| 項目 | 手動測試環境 | 驗收測試（不要碰） |
|------|-------------|------------------|
| 後端 | `http://localhost:8083`，真實時鐘 | 8080，測試時鐘 |
| 櫃檯 Web | `http://localhost:5300` | 5199 |
| 資料庫 | 同一台 Postgres 5433，database `tms_manual` | 5433 的 `tms` |
| Kafka | 同一台 9092，topic 全部加 `manual.` 前綴 | `tms.*` |
| 員工 App | 編譯成連 8083 的版本 | 連 8080／8081 的版本 |
| pid／log／jar／QR 圖 | `~/.tms-manual-env/` | `app/web-bdd/target/run-feature/` |

## 第一次用，要先裝好什麼？

- **Docker**：OrbStack 或 Docker Desktop 都可以，用來跑 Postgres 和 Kafka（專案根目錄的 `docker-compose.yml`）。
- **JDK 25**：`/usr/libexec/java_home -v 25` 要找得到。找不到又裝在別處時，用 `MANUAL_JAVA=/路徑/bin/java` 指定。
- **psql**：`brew install postgresql@15`，灌資料和查房間用。
- **Node.js**（20.19 以上或 22.12 以上），並且先在 `app/web` 跑過一次 `npm install`。
- **Flutter 和 Xcode**：`flutter doctor` 的 iOS 那項要過，Xcode 裡要下載好 iOS 平台。
- **qrencode**：`brew install qrencode`，產生房間 QR 圖用。

## 怎麼一個指令把環境起起來？

第一次（或程式碼有更新時）在專案根目錄跑：

```bash
bash scripts/manual-env/up.sh --build --phone --app
```

這一行依序做的事：Postgres／Kafka 沒起就 `docker compose up -d`，用目前程式碼打包後端（跳過測試），
停掉舊的並起後端 8083 和網頁 5300，灌示範資料，最後編譯員工 App、裝到 simulator 並打開。
跑完會印出櫃檯網址、住客 QR 的指令和該用哪些帳號。

四個參數可以自由組合：

| 參數 | 作用 |
|------|------|
| `--build` | 先用目前的程式碼重新打包後端；配 `--app` 時 App 也重新編譯 |
| `--keep-data` | 不重灌資料，保留你測到一半的資料和房間 QR |
| `--phone` | 網頁聽所有網卡，同一個區網的手機才連得進來 |
| `--app` | 最後把員工 App 裝到 simulator 並打開；沒加 `--build` 而且上次編好的就是連 8083，就不重編 |

不加參數的 `up.sh` 會**重灌資料**，等於把整套環境重置。只想重起又不想丟資料，記得加 `--keep-data`。

其他單獨的指令：

```bash
bash scripts/manual-env/status.sh            # 看後端、網頁、資料庫、App 各自的狀態和連到哪裡
bash scripts/manual-env/app.sh               # 重新編譯員工 App → 裝到 simulator → 打開
bash scripts/manual-env/app.sh --no-build    # 沿用上次編好的 App，只重裝並打開（幾秒鐘）
bash scripts/manual-env/app.sh --run         # 用 flutter run 前景執行，改 App 程式時可以 hot reload
bash scripts/manual-env/qr.sh 407            # 407 房的住客網址（電腦瀏覽器直接開）
bash scripts/manual-env/qr.sh 407 --phone    # 407 房的 QR 圖，給手機掃
bash scripts/manual-env/qr.sh --all --phone  # 所有在住房的 QR，加一張可列印的總表
bash scripts/manual-env/seed.sh              # 重灌示範資料（＝重置）
bash scripts/manual-env/start.sh             # 起後端＋網頁；已經在跑的會沿用
bash scripts/manual-env/stop.sh              # 全停；資料保留
```

`app.sh` 會自己挑 simulator：有開機中的 iPhone 就用它，沒有就挑最新 iOS 版本的第一台 iPhone 開機。
想指定就加 `--device "iPhone Air"` 或 UDID。`start.sh` 和 `stop.sh` 都可以只帶 `backend` 或 `web`，只動一半。

## 怎麼看後端、網頁、App 的 log？

後端和網頁是 `start.sh` 在背景起的，輸出都寫進 `~/.tms-manual-env/` 底下的檔案，用 `tail -f` 跟著看就好：

```bash
tail -f ~/.tms-manual-env/backend.log                                     # 後端（8083）
tail -f ~/.tms-manual-env/backend.log | grep -iE 'error|exception|warn'   # 只看出錯的
tail -f ~/.tms-manual-env/web.log                                         # 網頁（5300）
```

`web.log` 只記網頁開發伺服器（Vite）自己的輸出，例如編譯錯誤、改檔後的重新載入。
畫面上的錯誤、網頁打出去的 API 和回應不在這裡，要在瀏覽器按 ⌥⌘I 打開開發者工具，看 Console 和 Network 分頁。
住客頁在手機上出問題時，先在電腦瀏覽器開 `qr.sh <房號>` 印出的網址重現，比較好看。

員工 App 要看 log 有兩種方式，差在 App 是怎麼開起來的：

- **用 `app.sh --run` 開（建議）**：App 在前景跑，log 直接印在這個終端機，按 `r` 可以 hot reload。
  終端機裡也會印出一個 Flutter DevTools 網址，要看網路請求或畫面元件樹時打開它。
- **用 `app.sh` 或 `up.sh --app` 開**：App 是 `xcrun simctl launch` 在背景開的，終端機看不到輸出。
  另開一個終端機跑 `cd app/mobile && flutter logs` 接上 simulator 的 log；有好幾台 simulator 開著時加 `-d <UDID>` 指定。

一邊操作一邊對照時，開三個終端機分頁最方便：一個 `tail -f backend.log`、一個 `tail -f web.log`、
一個 `app.sh --run`，在 App 或網頁上按下去，就能同時看到前端和後端各自的反應。

## 要用哪些帳號？

密碼都是 `demo1234`。櫃檯 Web 的網址裡帶飯店代碼 `demo-hotel`；員工 App 登入只填員工編號和密碼。

| 帳號 | 角色 | 在哪裡用 | 新走一遍時 |
|------|------|---------|-----------|
| `fd1` | 櫃檯 | 櫃檯 Web | 用它派單 |
| `hk2` | 房務 | 員工 App | 用它做房務 |
| `mt2` | 工務 | 員工 App | 用它做工務單 |
| `mg1`、`mg2` | 主管 | 櫃檯 Web、員工 App | 用來驗收工務單 |
| `dm1` | 值班主管 | 櫃檯 Web | |
| `hk1`、`mt1` | 房務、工務 | 員工 App | 先不要用 |

示範資料裡 `hk1`（302 打掃）、`mt1`（502 修繕）、`mg1`（403）身上已經有進行中的工作，
App 規定一個人同時只能做一件，用他們會在「開始」那一步被擋下來。主管驗收不受這個限制，`mg1` 照樣可以驗收。

## 完整流程怎麼走一遍？

先跑 `up.sh --phone --app`（已經起過就 `up.sh --keep-data --phone --app`），再照下面的順序：

1. **住客提出需求。** 跑 `bash scripts/manual-env/qr.sh 407 --phone`，用手機相機掃螢幕上的 QR，
   或在電腦瀏覽器開 `qr.sh 407` 印出的網址。住客頁首頁有「備品補充」和「設備報修」兩張卡片。
   - 報修：點「設備報修」，打一句話送出，例如「冷氣不冷」或「窗簾掛勾鬆脫」。
   - 備品：點「備品補充」，選加毛巾或礦泉水送出。
2. **櫃檯看到並派單。** 電腦開 `http://localhost:5300/demo-hotel/login`，用 `fd1` 登入，看「總覽看板」。
   - 報修的文字需求會進「待派單」，要櫃檯決定給誰。把卡片拖到工務，系統就開一張工務單，卡片上的單號從 `T-` 開頭變成 `W-` 開頭。
   - 備品選單裡的項目不經過待派單，直接進「待認領」的房務。
3. **工務在 App 上做完。** simulator 上的員工 App 用 `mt2` 登入，到工務的待認領找到這張 `W-` 單，
   認領 → 開始 → 完成（完成時附上照片）。按下開始後，住客頁會即時變成「處理中」。
4. **主管驗收工務單。** App 登出，改用 `mg1` 登入，到驗收佇列打開這張單，按通過。驗收完這張單才算結案。
5. **房務在 App 上做完。** App 改用 `hk2` 登入，認領備品需求（或櫃檯派給房務的那張），開始 → 完成。
6. **住客看結果並評分。** 回到住客頁，不用重新整理，兩筆需求都會顯示「已完成」。
   房務那筆可以給星等；工務單驗收結案後不提供評分，這是規格的設計，不是壞掉。
   櫃檯看板上也看得到這兩張單結案。

## 手機要怎麼掃得到？

手機和 Mac 要在同一個 Wi-Fi，網頁要聽所有網卡。`up.sh --phone` 會用 `0.0.0.0` 起網頁；
已經用一般模式起了，就再跑一次 `up.sh --keep-data --phone`。

`qr.sh 407 --phone` 會抓這台 Mac 的區網 IP（先看 en0，再看 en1），把 `http://<區網 IP>:5300/g/<代碼>`
存成 `~/.tms-manual-env/qr-407.png` 並打開。`qr.sh --all --phone` 會替每一間「現在在住」的房間各做一張，
再做一份總表 `~/.tms-manual-env/qr-sheet.html`，上面每間房有房號、QR 和網址，可以直接印出來貼。
不加 `--phone` 時總表上是 localhost 網址，只能在這台 Mac 的瀏覽器點，手機掃了也打不開。

「現在在住」的判斷跟後端一樣：房間有一筆沒被取代的訂單，而且入住時間已到、退房時間還沒到。
示範資料灌的在住房是 205、306、405、407、508。

## 遇到問題怎麼辦？

**員工 App 帳號密碼都對，卻登不進去。**
App 連哪個後端是編譯時決定的，不是登入時。沒指定時預設連 8080；跑過 App 的驗收測試（`app/bdd`）後，
simulator 上的 App 會被換成連 8080 或 8081 的版本，它問的是驗收測試的後端，當然找不到手動環境的帳號。
跑 `bash scripts/manual-env/status.sh` 看「員工 App」那行；說不是連 8083，就跑 `bash scripts/manual-env/app.sh` 重裝。
App 登入打的是 `POST /api/v1/staff-accounts/login`，送的只有員工編號和密碼，沒有飯店代碼欄位。

**simulator 開機了卻看不到視窗。**
有些 Xcode 版本沒有獨立的 Simulator.app，`open -a Simulator` 會失敗。這時從 Xcode 選單
Open Developer Tool → Simulator 打開視窗。simulator 本身用 `xcrun simctl boot <UDID>` 就能開機，
`app.sh` 會幫你做；`xcrun simctl list devices available` 可以看有哪些機型。

**`flutter run` 不知道要填哪個裝置。**
用 `bash scripts/manual-env/app.sh --run`，它會挑好 simulator 並帶上連 8083 的參數。

**住客頁顯示「請洽櫃檯」。**
最常見的原因是重灌過資料：每次 `seed.sh`（或不加 `--keep-data` 的 `up.sh`）都會清掉房間的 QR 代碼，
舊網址和印出來的總表就全部失效，重跑 `qr.sh` 拿新的。另一個原因是那間房現在沒有在住的訂單，
`status.sh` 會列出目前的在住房。

**手機掃了 QR 卻打不開。**
先跑 `status.sh`，網頁那行要是 `*:5300`；不是的話跑 `up.sh --keep-data --phone`。
第一次用 `0.0.0.0` 起網頁時，macOS 可能跳出「是否允許 node 接受連入連線」，要按允許。
公司或咖啡店的 Wi-Fi 有時會擋裝置之間互連，這時改用手機熱點讓 Mac 和手機都連上去。

**按了「開始」卻被擋下來。**
一個人同時只能有一件進行中的工作。`hk1`、`mt1`、`mg1` 在示範資料裡已經各有一件，換 `hk2`、`mt2`。

**住客打的文字沒有被認成工務。**
`seed.sh` 灌完示範資料後，會從 `tms/src/main/resources/db/migration/V2__seed_dev.sql` 讀出分流規則
（例如「冷氣」「空調」算工務報修、「毛巾」算房務備品）寫進資料庫。報修的文字需求不管有沒有對中規則，
都會先進待派單，由櫃檯確認後再派。

**後端起不來或打包失敗。**
看 `~/.tms-manual-env/backend.log` 的最後幾十行。打包要 JDK 25，`up.sh --build` 會用
`/usr/libexec/java_home -v 25` 找；`8083` 被別的程式佔用時 `start.sh` 會停下來，不會去殺它。

## 換新版後端時會發生什麼事？

`up.sh --build` 打包完會重起後端。啟動時會把 `tms/target/tms-0.0.1-SNAPSHOT.jar` 複製到
`~/.tms-manual-env/tms-manual.jar` 再跑，之後驗收測試重新打包也不會換掉正在跑的這份。
新版有新的資料庫遷移時，後端啟動時 Flyway 會自動套上去，資料保留。

## 這套環境是怎麼跟驗收測試隔開的？

- **為什麼用另一個 database 而不是另一個 schema**：遷移 `V1__schema.sql` 寫死了
  `GRANT USAGE ON SCHEMA public` 和 `ALL SEQUENCES IN SCHEMA public`。套在別的 schema 時，
  應用帳號 `tms_app` 拿不到那個 schema 的使用權限，V1 建的序號也沒授權，後端寫不進去。
  另開 database 時 `public` 是那個 database 自己的，遷移原封不動就能用。
- **Kafka**：用啟動參數把四個 topic（task-events、push、push.DLT、pms-status）換成
  `manual.tms.*`，事件串流的 consumer group 前綴也換成 `manual.sse`。推播的 consumer group
  `tms-push-sender` 寫死在程式裡改不掉，所以 8083 和 8080 會在同一個 group 裡，但各自只訂自己的
  topic，Kafka 只把 `manual.tms.push` 的分區分給 8083，不會搶到測試的訊息。
  副作用：8083 起停時同一個 group 會重新分配一次分區，8080 的推播消費會停頓幾秒。
- **登入憑證**：JWT 簽章金鑰和測試後端一樣（都是預設值），8080 發的 token 拿到 8083 也驗得過；
  兩邊資料不同，只是不要混用。
- **simulator**：另一個 session 的 App 端到端測試會用 Appium 佔住它自己的 simulator。
  手動測試請用另一台，`app.sh --device <名稱>` 可以指定。
- **員工 App 連實機**：iOS simulator 和 Mac 共用網路，App 裡的 `localhost` 就是這台 Mac。
  實體手機上的 `localhost` 是手機自己，要裝到實機得把 `API_BASE_URL` 換成 Mac 的區網 IP，這條沒有實測過。
