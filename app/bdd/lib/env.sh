# 用 source 載入；呼叫端要自己 set -euo pipefail 並掛 trap cleanup EXIT。

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

check_backend_ready() {
  log "後端就緒檢查：$BACKEND_URL 與 Postgres ${BACKEND_DB_HOST}:${BACKEND_DB_PORT}"

  # curl 連不上時 http_code 是 000。
  local http_code
  http_code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$BACKEND_URL/api/v1/pool" || true)"
  if [ "$http_code" = "000" ]; then
    # ${} 不能省：macOS 內建的 bash 3.2 會把全形「）」的位元組併進變數名。
    log "後端沒有回應（${BACKEND_URL}）。先起後端："
    log "  docker compose up -d"
    log "  TMS_DB_URL=jdbc:postgresql://localhost:5433/tms TMS_TEST_CLOCK_ENABLED=true JAVA_HOME=\"\$(/usr/libexec/java_home -v 25)\" ./tms/mvnw -f tms/pom.xml spring-boot:run"
    return 1
  fi

  local clock_code
  clock_code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$BACKEND_URL/api/v1/test/clock" || true)"
  if [ "$clock_code" != "200" ]; then
    log "後端沒有開放撥時鐘端點（/api/v1/test/clock 回 HTTP ${clock_code}）。停掉後端、帶旗標重啟："
    log "  TMS_DB_URL=jdbc:postgresql://localhost:5433/tms TMS_TEST_CLOCK_ENABLED=true JAVA_HOME=\"\$(/usr/libexec/java_home -v 25)\" ./tms/mvnw -f tms/pom.xml spring-boot:run"
    return 1
  fi

  if ! (exec 3<>"/dev/tcp/$BACKEND_DB_HOST/$BACKEND_DB_PORT") 2>/dev/null; then
    log "連不上 Postgres（${BACKEND_DB_HOST}:${BACKEND_DB_PORT}）。docker compose 的 postgres 起了嗎？"
    return 1
  fi
  exec 3>&- 2>/dev/null || true
  log "後端就緒"
}

build_and_install_app() {
  log "flutter build（TEST_BACKDOOR=true，必須是這個 dart-define，它是編譯期旗標）"
  (cd "$MOBILE_DIR" && flutter build ios --simulator --debug --dart-define=TEST_BACKDOOR=true --dart-define=API_BASE_URL="$BACKEND_URL/api/v1")

  APP_PATH="$MOBILE_DIR/build/ios/iphonesimulator/Runner.app"
  if [ ! -d "$APP_PATH" ]; then
    log "找不到建置產物：$APP_PATH"
    return 1
  fi

  # 接上硬體鍵盤（下次 boot 生效），否則 simulator 的螢幕鍵盤會蓋住登入頁的密碼欄。
  defaults write com.apple.iphonesimulator ConnectHardwareKeyboard -bool true

  log "boot simulator（${UDID}）＋安裝 App"
  xcrun simctl boot "$UDID" >/dev/null 2>&1 || true
  xcrun simctl bootstatus "$UDID" -b
  xcrun simctl install "$UDID" "$APP_PATH"

  # simctl install 在 boot 剛完成時會靜默失敗，所以裝完要確認、沒裝上就重裝。
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

  local _i
  for _i in $(seq 1 60); do
    if curl -s -o /dev/null "http://127.0.0.1:${APPIUM_PORT}/status"; then
      return 0
    fi
    sleep 0.5
  done
  log "Appium server 沒有如期起來，看 $APPIUM_LOG"
  return 1
}
