#!/usr/bin/env bash
#
# 跑 ios-app 全部模組的 Cucumber 情境，一次約 60–90 分鐘。
# 用法： app/bdd/run-all.sh
# 要先裝好： flutter、xcrun（Xcode command line tools）、appium 3.x（含 xcuitest driver）。
# 可覆寫的環境變數見 app/bdd/lib/env.sh。

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/env.sh"
trap cleanup EXIT

log "1/5 後端就緒檢查"
check_backend_ready

log "2/5 flutter build ＋ boot simulator ＋ 安裝 App"
build_and_install_app

log "3/5 起 Appium server"
start_appium

log "4/5 跑全量 ios-app 的 Cucumber 情境（RunTruthFeaturesTest）"
set +e
JAVA_HOME="$(/usr/libexec/java_home -v 25)" \
  mvn -f "$BDD_DIR/pom.xml" test \
  -Dtest=RunTruthFeaturesTest \
  -Dappium.server.url="http://127.0.0.1:${APPIUM_PORT}" \
  -Dappium.ios.udid="$UDID" \
  -Dappium.ios.bundle-id="$BUNDLE_ID" \
  -Dbackend.base.url="$BACKEND_URL" \
  -Dbackend.db.url="jdbc:postgresql://${BACKEND_DB_HOST}:${BACKEND_DB_PORT}/tms"
TEST_EXIT=$?
set -e

log "5/5 完成，exit=${TEST_EXIT}（cucumber report：${BDD_DIR}/target/cucumber-reports/truth.html）"
exit $TEST_EXIT
