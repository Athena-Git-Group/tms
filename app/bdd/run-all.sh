#!/usr/bin/env bash
#
# 跑 specs/truth/features/ios-app 底下**全部**模組的 truth feature（全量回歸）：
# 流程與 run-f3.sh／run-module.sh 相同（後端就緒檢查 → build ＋安裝 App → 起 Appium
# → 跑 Cucumber → 清理），差別只在跑的是 RunTruthFeaturesTest，而且不帶
# -Dcucumber.features——那個 runner 自己用 @SelectDirectories 選定整個 ios-app 目錄，
# 再給一次 cucumber.features 反而會讓 Surefire 頂層 launcher 重複發現同一批場景。
#
# 用法： app/bdd/run-all.sh
# 一次跑完約 60–90 分鐘，建議背景執行並輪詢 log。
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
