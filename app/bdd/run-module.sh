#!/usr/bin/env bash
#
# 只跑單一模組的 Cucumber 情境。
# 用法： app/bdd/run-module.sh <模組目錄名>
# 要先裝好： flutter、xcrun（Xcode command line tools）、appium 3.x（含 xcuitest driver）。
# 可覆寫的環境變數見 app/bdd/lib/env.sh。

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/env.sh"
trap cleanup EXIT

MODULE="${1:-}"
FEATURES_ROOT="$REPO_ROOT/specs/truth/features/ios-app"
if [ -z "$MODULE" ] || [ ! -d "$FEATURES_ROOT/$MODULE" ]; then
  log "用法：app/bdd/run-module.sh <模組目錄名>；可選的模組："
  (cd "$FEATURES_ROOT" && find . -mindepth 1 -maxdepth 1 -type d | sed 's|^\./|  |')
  exit 1
fi

log "1/5 後端就緒檢查"
check_backend_ready

log "2/5 flutter build ＋ boot simulator ＋ 安裝 App"
build_and_install_app

log "3/5 起 Appium server"
start_appium

log "4/5 跑模組「${MODULE}」的 Cucumber 情境（RunModuleFeatureTest）"
# cucumber.features 讓 Cucumber 引擎忽略 selector，Surefire 頂層 launcher 會再發現一次同一批
# feature；排除頂層的 cucumber 引擎，場景才不會跑兩遍。
set +e
JAVA_HOME="$(/usr/libexec/java_home -v 25)" \
  mvn -f "$BDD_DIR/pom.xml" test \
  -Dtest=RunModuleFeatureTest \
  -Dsurefire.excludeJUnit5Engines=cucumber \
  -Dcucumber.features="../../specs/truth/features/ios-app/${MODULE}" \
  -Dappium.server.url="http://127.0.0.1:${APPIUM_PORT}" \
  -Dappium.ios.udid="$UDID" \
  -Dappium.ios.bundle-id="$BUNDLE_ID" \
  -Dbackend.base.url="$BACKEND_URL" \
  -Dbackend.db.url="jdbc:postgresql://${BACKEND_DB_HOST}:${BACKEND_DB_PORT}/tms"
TEST_EXIT=$?
set -e

log "5/5 完成，exit=${TEST_EXIT}（cucumber report：${BDD_DIR}/target/cucumber-reports/module.html）"
exit $TEST_EXIT
