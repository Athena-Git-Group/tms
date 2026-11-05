#!/usr/bin/env bash
#
# 跑 F3（點開推播的去處）端到端：先確認後端就緒（docker-compose＋tms 要先起好，
# 這裡只檢查不啟動），再建置 TEST_BACKDOOR=true 的 Flutter App、灌進 simulator、
# 起 Appium server，只跑 specs/truth/features/ios-app/推播/ 底下的 feature，
# 跑完清理（kill appium、shutdown simulator）。
#
# 用法： app/bdd/run-f3.sh
# 要先裝好： flutter、xcrun（Xcode command line tools）、appium 3.x（含 xcuitest driver）。
# 環境準備片段與可覆寫的環境變數（UDID、BACKEND_URL……）見 app/bdd/lib/env.sh。

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/env.sh"
trap cleanup EXIT

log "1/5 後端就緒檢查"
check_backend_ready

log "2/5 flutter build ＋ boot simulator ＋ 安裝 App"
build_and_install_app

log "3/5 起 Appium server"
start_appium

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
  -Dappium.ios.bundle-id="$BUNDLE_ID" \
  -Dbackend.base.url="$BACKEND_URL" \
  -Dbackend.db.url="jdbc:postgresql://${BACKEND_DB_HOST}:${BACKEND_DB_PORT}/tms"
TEST_EXIT=$?
set -e

log "5/5 完成，exit=${TEST_EXIT}（cucumber report：${BDD_DIR}/target/cucumber-reports/f3-push.html）"
exit $TEST_EXIT
