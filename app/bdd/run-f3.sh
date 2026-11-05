#!/usr/bin/env bash
#
# 跑 F3（點開推播的去處）端到端：建置 TEST_BACKDOOR=true 的 Flutter App、灌進
# simulator、起 Appium server、只跑 specs/truth/features/ios-app/推播/ 底下的
# feature，跑完清理（kill appium、shutdown simulator）。
#
# 用法： app/bdd/run-f3.sh
# 要先裝好： flutter、xcrun（Xcode command line tools）、appium 3.x（含 xcuitest driver）。
#
# 可覆寫的環境變數（給別台機器、別顆 simulator 用）：
#   UDID               iOS simulator UDID（預設下面寫的那顆 iPhone 17）
#   BUNDLE_ID          App 的 bundle id
#   APPIUM_PORT        Appium server 聽的 port

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MOBILE_DIR="$REPO_ROOT/app/mobile"
BDD_DIR="$REPO_ROOT/app/bdd"

UDID="${UDID:-79F32D5C-7A71-47D6-AA8D-8142E68CFF48}"
BUNDLE_ID="${BUNDLE_ID:-com.athena.tms.tmsStaffApp}"
APPIUM_PORT="${APPIUM_PORT:-4723}"

LOG_DIR="$(mktemp -d /tmp/tms-f3-XXXXXX)"
APPIUM_LOG="$LOG_DIR/appium.log"
APPIUM_PID=""

log() {
  echo "[run-f3] $*"
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
trap cleanup EXIT

log "1/5 flutter build（TEST_BACKDOOR=true，必須是這個 dart-define，它是編譯期旗標）"
(cd "$MOBILE_DIR" && flutter build ios --simulator --debug --dart-define=TEST_BACKDOOR=true)

APP_PATH="$MOBILE_DIR/build/ios/iphonesimulator/Runner.app"
if [ ! -d "$APP_PATH" ]; then
  log "找不到建置產物：$APP_PATH"
  exit 1
fi

log "2/5 boot simulator（${UDID}）＋安裝 App"
xcrun simctl boot "$UDID" >/dev/null 2>&1 || true
xcrun simctl bootstatus "$UDID" -b
xcrun simctl install "$UDID" "$APP_PATH"

log "3/5 起 Appium server（port ${APPIUM_PORT}，log 在 ${APPIUM_LOG}）"
nohup appium --port "$APPIUM_PORT" > "$APPIUM_LOG" 2>&1 &
APPIUM_PID=$!

# Appium server 要花一兩秒把 REST 介面開起來，用輪詢代替固定 sleep。
for _ in $(seq 1 20); do
  if curl -s -o /dev/null "http://127.0.0.1:${APPIUM_PORT}/status"; then
    break
  fi
  sleep 0.5
done
if ! curl -s -o /dev/null "http://127.0.0.1:${APPIUM_PORT}/status"; then
  log "Appium server 沒有如期起來，看 $APPIUM_LOG"
  exit 1
fi

log "4/5 跑 F3 的 Cucumber 情境（RunF3PushFeatureTest，只選 specs/truth/features/ios-app/推播/）"
# -Dtest 只跑這一個 Suite class，不要連 RunTruthFeaturesTest 一起跑——後者選的是
# 全部 truth feature，裡面十個模組的 step 都還是 PendingException 的 stub，若跟
# F3 共用 AppiumWorld 的 @Before／@After，會逼每個 pending 情境都重開一次模擬器。
set +e
JAVA_HOME="$(/usr/libexec/java_home -v 25)" \
  mvn -f "$BDD_DIR/pom.xml" test \
  -Dtest=RunF3PushFeatureTest \
  -Dappium.server.url="http://127.0.0.1:${APPIUM_PORT}" \
  -Dappium.ios.udid="$UDID" \
  -Dappium.ios.bundle-id="$BUNDLE_ID"
TEST_EXIT=$?
set -e

log "5/5 完成，exit=${TEST_EXIT}（cucumber report：${BDD_DIR}/target/cucumber-reports/f3-push.html）"
exit $TEST_EXIT
