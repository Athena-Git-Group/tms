# 手動測試環境（manual-env）

給人自己操作員工 App 和櫃檯 Web 用的一套環境，和自動驗收測試完全分開：驗收測試每個場景都會清空
`tms` 資料庫，這套用自己的資料庫，不會被清。

| 項目 | 手動測試環境 | 驗收測試（不要碰） |
|------|-------------|------------------|
| 後端 | `http://localhost:8083`，真實時鐘 | 8080，測試時鐘 |
| 櫃檯 Web | `http://localhost:5300` | 5199 |
| 資料庫 | 同一台 Postgres 5433，database `tms_manual` | 5433 的 `tms` |
| Kafka | 同一台 9092，topic 全部加 `manual.` 前綴 | `tms.*` |
| pid／log／jar | `~/.tms-manual-env/` | `app/web-bdd/target/run-feature/` |

## 用法

```bash
bash scripts/manual-env/start.sh     # 起後端＋網頁；第一次會建 tms_manual，後端啟動時 Flyway 建表
bash scripts/manual-env/seed.sh      # 灌示範資料（會先清掉 tenant 1 再重灌＝重置）
bash scripts/manual-env/stop.sh      # 全停；資料保留
```

- `start.sh backend` / `start.sh web`、`stop.sh backend` / `stop.sh web` 可以只動一半。
- 重複執行沒關係：已經在跑的部分會沿用。
- **換新版後端**：先打包（`./mvnw -f tms/pom.xml -DskipTests package`，或等驗收測試打包完），
  再 `stop.sh backend && start.sh backend`。啟動時會把 `tms/target/tms-0.0.1-SNAPSHOT.jar`
  複製到 `~/.tms-manual-env/tms-manual.jar` 再跑，之後測試重新打包也不會換掉正在跑的這份。
  新版有新的遷移時，啟動時 Flyway 會自動套上去。
- log：`~/.tms-manual-env/backend.log`、`~/.tms-manual-env/web.log`。

## 帳號

飯店代碼 `demo-hotel`，密碼都是 `demo1234`。

- 櫃檯 Web（`http://localhost:5300/demo-hotel/login`）：`fd1` 櫃檯、`mg1` `mg2` 主管、`dm1` 值班主管。
- 員工 App：`hk1` `hk2` 房務、`mt1` `mt2` 工務、`mg1` `mg2` 主管。

## 員工 App 連這套環境

```bash
cd app/mobile
flutter run -d <simulator 名稱或 id> --dart-define=API_BASE_URL=http://localhost:8083/api/v1
```

- `API_BASE_URL` 是編譯期參數，換環境要重新 `flutter run`／`flutter build`，hot reload 不會換。
  不帶它的話 App 預設連 8080（驗收測試的後端），資料會被測試清掉。
- **iOS simulator 上 `localhost` 指得到本機**：simulator 和 Mac 共用網路，App 端到端測試
  （`app/bdd/lib/env.sh`）也是這樣連 `http://localhost:8080`。
- 實機不行：手機上的 `localhost` 是手機自己，要改用 Mac 的區網 IP
  （例如 `http://192.168.x.x:8083/api/v1`；後端有聽所有網卡）。這條沒有實測過。
- 另一個 session 的 App 端到端測試會佔用它自己的 simulator（Appium）。手動測試請另開一台
  simulator，不要用它那台。
- 不需要 `TEST_BACKDOOR=true`，那是給自動測試注入假狀態用的。

## 隔離方式與限制

- **為什麼用另一個 database 而不是另一個 schema**：遷移 `V1__schema.sql` 寫死了
  `GRANT USAGE ON SCHEMA public` 和 `ALL SEQUENCES IN SCHEMA public`。套在別的 schema 時，
  應用帳號 `tms_app` 拿不到那個 schema 的使用權限，V1 建的序號也沒授權，後端寫不進去。
  另開 database 時 `public` 是那個 database 自己的，遷移原封不動就能用。
- **Kafka**：用啟動參數把四個 topic（task-events、push、push.DLT、pms-status）換成
  `manual.tms.*`，事件串流的 consumer group 前綴也換成 `manual.sse`。推播的 consumer group
  `tms-push-sender` 寫死在程式裡改不掉，所以 8083 和 8080 會在同一個 group 裡，但各自只訂自己的
  topic，Kafka 只把 `manual.tms.push` 的分區分給 8083，不會搶到測試的訊息。
  副作用：8083 起停時同一個 group 會重新分配一次分區，8080 的推播消費會停頓幾秒。
- JWT 簽章金鑰和測試後端一樣（都是預設值），8080 發的 token 拿到 8083 也驗得過；兩邊資料不同，
  只是不要混用。
- 網頁只聽本機（`localhost`），iPad 實機連不到。
