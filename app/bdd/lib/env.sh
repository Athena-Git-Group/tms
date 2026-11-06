# app/bdd 執行腳本共用的環境準備片段（run-f3.sh 與後續的分模組腳本一起用）。
# 用 source 載入，不能直接執行；呼叫端要自己 set -euo pipefail 並掛 trap cleanup EXIT。
#
# 提供的函式（依建議呼叫順序）：
#   check_backend_ready   後端（HTTP）與資料庫（TCP）就緒檢查——只檢查、不啟動，
#                         未就緒時印出啟動指令後直接失敗
#   build_and_install_app flutter build（TEST_BACKDOOR=true）→ boot simulator → 安裝
#   start_appium          起 Appium server 並等它的 REST 介面就緒
#   cleanup               kill appium、shutdown simulator（掛在呼叫端的 trap EXIT）
#
# 可覆寫的環境變數（給別台機器、別顆 simulator、別個後端位址用）：
#   UDID               iOS simulator UDID（預設下面寫的那顆 iPhone 17）
#   BUNDLE_ID          App 的 bundle id
#   APPIUM_PORT        Appium server 聽的 port
#   BACKEND_URL        後端 base URL（harness 的 backend.base.url 同值）
#   BACKEND_DB_HOST／BACKEND_DB_PORT  後端 Postgres 位址（docker-compose 對外開 5433）

# 路徑以本檔自身為基準（app/bdd/lib/），呼叫端放哪裡都不影響；log 前綴取呼叫端檔名。
ENV_SH_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BDD_DIR="$(cd "$ENV_SH_DIR/.." && pwd)"
REPO_ROOT="$(cd "$BDD_DIR/../.." && pwd)"
MOBILE_DIR="$REPO_ROOT/app/mobile"
RUNNER_NAME="$(basename "${BASH_SOURCE[1]:-$0}" .sh)"

UDID="${UDID:-79F32D5C-7A71-47D6-AA8D-8142E68CFF48}"
BUNDLE_ID="${BUNDLE_ID:-com.athena.tms.tmsStaffApp}"
APPIUM_PORT="${APPIUM_PORT:-4723}"
BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8080}"
BACKEND_DB_HOST="${BACKEND_DB_HOST:-127.0.0.1}"
BACKEND_DB_PORT="${BACKEND_DB_PORT:-5433}"

LOG_DIR="$(mktemp -d /tmp/tms-app-bdd-XXXXXX)"
APPIUM_LOG="$LOG_DIR/appium.log"
APPIUM_PID=""

log() {
  echo "[$RUNNER_NAME] $*"
}

cleanup() {
  log "清理：kill appium server（如果還在跑）、shutdown simulator"
  if [ -n "$APPIUM_PID" ] && kill -0 "$APPIUM_PID" 2>/dev/null; then
    kill "$APPIUM_PID" 2>/dev/null || true
    wait "$APPIUM_PID" 2>/dev/null || true
  fi
  xcrun simctl shutdown "$UDID" >/dev/null 2>&1 || true
  log "appium server log：$APPIUM_LOG"
}

# 後端由 docker-compose＋tms 既有方式先起好，這裡只檢查就緒；在 harness 裡偷起容器
# 會把「測試失敗」和「環境沒起來」兩種訊號混在一起，所以未就緒時直接失敗並印出指令。
check_backend_ready() {
  log "後端就緒檢查：$BACKEND_URL 與 Postgres ${BACKEND_DB_HOST}:${BACKEND_DB_PORT}"

  # 任何 HTTP 狀態碼（含 401）都代表後端活著；curl 連不上時回 000。
  local http_code
  http_code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$BACKEND_URL/api/v1/pool" || true)"
  if [ "$http_code" = "000" ]; then
    # ${} 不能省：系統 bash 3.2 會把全形「）」的位元組吞進變數名，set -u 下直接炸
    log "後端沒有回應（${BACKEND_URL}）。先起後端："
    log "  docker compose up -d"
    log "  TMS_DB_URL=jdbc:postgresql://localhost:5433/tms JAVA_HOME=\"\$(/usr/libexec/java_home -v 25)\" ./tms/mvnw -f tms/pom.xml spring-boot:run"
    return 1
  fi

  # harness 的 fixture 走 JDBC 直連資料庫，所以 Postgres 的對外埠也要通。
  if ! (exec 3<>"/dev/tcp/$BACKEND_DB_HOST/$BACKEND_DB_PORT") 2>/dev/null; then
    log "連不上 Postgres（${BACKEND_DB_HOST}:${BACKEND_DB_PORT}）。docker compose 的 postgres 起了嗎？"
    return 1
  fi
  exec 3>&- 2>/dev/null || true
  log "後端就緒"
}

build_and_install_app() {
  log "flutter build（TEST_BACKDOOR=true，必須是這個 dart-define，它是編譯期旗標）"
  (cd "$MOBILE_DIR" && flutter build ios --simulator --debug --dart-define=TEST_BACKDOOR=true)

  APP_PATH="$MOBILE_DIR/build/ios/iphonesimulator/Runner.app"
  if [ ! -d "$APP_PATH" ]; then
    log "找不到建置產物：$APP_PATH"
    return 1
  fi

  # 接上硬體鍵盤（下次 boot 生效）。不接的話 simulator 會彈出螢幕鍵盤，而中文
  # 注音鍵盤高到會蓋住登入頁的密碼欄：點密碼欄的那一下會打在鍵盤上，焦點留在
  # 員工編號欄，接著 sendKeys 的密碼就被接到員工編號後面（實測看到欄位值是
  # 「fx-xxxxxxxxxxxxdemo1234」），登入失敗，表現成「等不到 home.screen」。
  defaults write com.apple.iphonesimulator ConnectHardwareKeyboard -bool true

  log "boot simulator（${UDID}）＋安裝 App"
  xcrun simctl boot "$UDID" >/dev/null 2>&1 || true
  xcrun simctl bootstatus "$UDID" -b
  xcrun simctl install "$UDID" "$APP_PATH"

  # boot 剛完成時 install 會靜默失敗（405／204 競態），要到下一步建 Appium
  # session 才以「App with bundle identifier … unknown」爆出來，看起來像 Appium
  # 壞掉。裝完先確認真的裝上去了，沒有就重裝。
  local _try
  for _try in 1 2 3; do
    if xcrun simctl get_app_container "$UDID" "$BUNDLE_ID" >/dev/null 2>&1; then
      return 0
    fi
    log "App 沒有真的裝上去（第 ${_try} 次），重裝"
    xcrun simctl bootstatus "$UDID" -b
    xcrun simctl install "$UDID" "$APP_PATH"
  done
  log "App 重裝三次仍不在 simulator 上（${BUNDLE_ID}）"
  return 1
}

start_appium() {
  log "起 Appium server（port ${APPIUM_PORT}，log 在 ${APPIUM_LOG}）"
  nohup appium --port "$APPIUM_PORT" > "$APPIUM_LOG" 2>&1 &
  APPIUM_PID=$!

  # Appium server 要花一兩秒把 REST 介面開起來，用輪詢代替固定 sleep。
  local _i
  for _i in $(seq 1 20); do
    if curl -s -o /dev/null "http://127.0.0.1:${APPIUM_PORT}/status"; then
      return 0
    fi
    sleep 0.5
  done
  log "Appium server 沒有如期起來，看 $APPIUM_LOG"
  return 1
}
