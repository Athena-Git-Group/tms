# 手動測試環境的共用設定與小工具，給同目錄的腳本用 source 載入。
# 每一項設定都可以用同名的 MANUAL_* 環境變數蓋掉。

MANUAL_ENV_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd "$MANUAL_ENV_DIR/../.." && pwd -P)"

STATE_DIR="${MANUAL_ENV_STATE_DIR:-$HOME/.tms-manual-env}"

DB_HOST="${MANUAL_DB_HOST:-127.0.0.1}"
DB_PORT="${MANUAL_DB_PORT:-5433}"
# 跟驗收測試共用同一台 Postgres，但用另一個 database，測試清資料不會清到這裡。
DB_NAME="${MANUAL_DB_NAME:-tms_manual}"
DB_ADMIN_USER="${MANUAL_DB_ADMIN_USER:-tms}"
DB_ADMIN_PASSWORD="${MANUAL_DB_ADMIN_PASSWORD:-tms}"

BACKEND_PORT="${MANUAL_BACKEND_PORT:-8083}"
WEB_PORT="${MANUAL_WEB_PORT:-5300}"
WEB_HOST="${MANUAL_WEB_HOST:-localhost}"
KAFKA_BOOTSTRAP="${MANUAL_KAFKA_BOOTSTRAP:-localhost:9092}"
# 這套環境用的 Kafka topic 一律加這個前綴，跟驗收測試的 tms.* 分開。
KAFKA_TOPIC_PREFIX="${MANUAL_KAFKA_TOPIC_PREFIX:-manual.}"
export TMS_STORAGE_BUCKET="${MANUAL_STORAGE_BUCKET:-tms-repair-photos-manual}"

if [[ -n "${MANUAL_JAVA:-}" ]]; then
  JAVA_BIN="$MANUAL_JAVA"
else
  JAVA_BIN="$(/usr/libexec/java_home -v 25 2>/dev/null || true)/bin/java"
fi
SOURCE_JAR="${MANUAL_SOURCE_JAR:-$REPO_ROOT/tms/target/tms-0.0.1-SNAPSHOT.jar}"
RUN_JAR="$STATE_DIR/tms-manual.jar"

BACKEND_PID_FILE="$STATE_DIR/backend.pid"
WEB_PID_FILE="$STATE_DIR/web.pid"
BACKEND_LOG="$STATE_DIR/backend.log"
WEB_LOG="$STATE_DIR/web.log"

APP_DIR="$REPO_ROOT/app/mobile"
APP_BUNDLE_ID="com.athena.tms.tmsStaffApp"
APP_API_BASE_URL="http://localhost:${BACKEND_PORT}/api/v1"
APP_BUILD="$APP_DIR/build/ios/iphonesimulator/Runner.app"

TENANT_CODE="demo-hotel"
DESK_URL="http://localhost:${WEB_PORT}/${TENANT_CODE}/login"

log() { echo "[manual-env] $*"; }
die() { echo "[manual-env] $*" >&2; exit 1; }

pid_alive() {
  local f="$1"
  [[ -f "$f" ]] && kill -0 "$(cat "$f")" 2>/dev/null
}

psql_manual() {
  PGPASSWORD="$DB_ADMIN_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_ADMIN_USER" -d "$DB_NAME" \
    --no-psqlrc --set ON_ERROR_STOP=1 "$@"
}

lan_ip() {
  ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true
}

web_listens_all() {
  lsof -nP -iTCP:"$WEB_PORT" -sTCP:LISTEN 2>/dev/null | grep "\*:${WEB_PORT}" >/dev/null
}

# Flutter debug 版把 API 位址以字串存在 kernel_blob.bin；預設值 8080 也一定在裡面，
# 所以只能問「有沒有這個位址」，不能問「是哪個位址」。grep 不加 -q：提早結束會讓 strings 收到 SIGPIPE，pipefail 下整條變失敗。
app_targets_backend() {
  local blob="$1/Frameworks/App.framework/flutter_assets/kernel_blob.bin"
  [[ -f "$blob" ]] && strings "$blob" | grep -F "$APP_API_BASE_URL" >/dev/null
}
