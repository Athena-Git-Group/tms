# 手動測試環境的共用設定，給 start.sh／stop.sh／seed.sh 用 source 載入。
# 每一項都可以用同名環境變數蓋掉。

MANUAL_ENV_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd "$MANUAL_ENV_DIR/../.." && pwd -P)"

# pid、log、複製出來的 jar 都放這裡，不放專案內。
STATE_DIR="${MANUAL_ENV_STATE_DIR:-$HOME/.tms-manual-env}"

DB_HOST="${MANUAL_DB_HOST:-127.0.0.1}"
DB_PORT="${MANUAL_DB_PORT:-5433}"
# 跟驗收測試共用同一台 Postgres，但用另一個 database，測試清資料不會清到這裡。
DB_NAME="${MANUAL_DB_NAME:-tms_manual}"
DB_ADMIN_USER="${MANUAL_DB_ADMIN_USER:-tms}"
DB_ADMIN_PASSWORD="${MANUAL_DB_ADMIN_PASSWORD:-tms}"

BACKEND_PORT="${MANUAL_BACKEND_PORT:-8083}"
WEB_PORT="${MANUAL_WEB_PORT:-5300}"
KAFKA_BOOTSTRAP="${MANUAL_KAFKA_BOOTSTRAP:-localhost:9092}"
# 這套環境用的 Kafka topic 一律加這個前綴，跟驗收測試的 tms.* 分開。
KAFKA_TOPIC_PREFIX="${MANUAL_KAFKA_TOPIC_PREFIX:-manual.}"

JAVA_BIN="${MANUAL_JAVA:-/Users/Caresys/Library/Java/JavaVirtualMachines/openjdk-25.0.2/Contents/Home/bin/java}"
SOURCE_JAR="${MANUAL_SOURCE_JAR:-$REPO_ROOT/tms/target/tms-0.0.1-SNAPSHOT.jar}"
RUN_JAR="$STATE_DIR/tms-manual.jar"

BACKEND_PID_FILE="$STATE_DIR/backend.pid"
WEB_PID_FILE="$STATE_DIR/web.pid"
BACKEND_LOG="$STATE_DIR/backend.log"
WEB_LOG="$STATE_DIR/web.log"

pid_alive() {
  local f="$1"
  [[ -f "$f" ]] && kill -0 "$(cat "$f")" 2>/dev/null
}
