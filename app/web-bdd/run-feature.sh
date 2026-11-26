#!/usr/bin/env bash
#
# 跑櫃檯 Web 或住客掃碼頁的驗收規格：一支指令把後端、開發伺服器與瀏覽器起好，跑完收掉自己起的東西。
#
# 用法：
#   bash app/web-bdd/run-feature.sh [選項] [feature 路徑] [desktop|ipad|phone]
#
#   feature 路徑  以 guest-web/ 或 frontdesk-web/ 開頭時，就是相對於 specs/truth/features/ 的路徑，
#                 開頭那一段是根（只寫 guest-web/ 就是整個住客掃碼頁）；不帶根時相對於
#                 specs/truth/features/frontdesk-web/。可以是一份 feature（待派單/待派單清單.feature）、
#                 其中一個場景（待派單/待派單清單.feature:18，冒號後面是場景那一行的行號）、
#                 或一個模組目錄（待派單）。不給就跑整個 frontdesk-web/。
#   desktop|ipad|phone
#                 瀏覽器設定。desktop 是電腦 1280 × 720 滑鼠（Chromium）；ipad 是 iPad 橫向
#                 1180 × 820 觸控（WebKit）；phone 是手機直向 393 × 852 觸控（WebKit）。
#                 不給時，根是 guest-web/ 用 phone，其餘用 desktop。
#                 ipad 跑整個目錄時只跑標了 @ipad 的場景：畫面差異只出在少數幾份規格，
#                 其餘行為與 desktop 相同，整套重跑一次要多十幾分鐘卻抓不到新問題。
#                 指定單一份 feature 時照樣全跑；要整套都跑，設 IPAD_ALL_SCENARIOS=1。
#
# 選項：
#   --keep     跑完不關這一次起的後端與開發伺服器。之後每一次執行都會直接沿用它們，
#              連續跑很多份 feature 時不必每次重啟。用完要自己下 --stop。
#   --stop     不跑測試，只把這個工作目錄留著的後端（BACKEND_PORT）與開發伺服器關掉。
#   --rebuild  不管後端 jar 新舊，一律重建。
#   -h, --help 印出這段說明。
#
# 環境變數（都可以不設）：
#   WEB_PORT             開發伺服器的埠，預設 5199。
#   BACKEND_PORT         後端的埠，預設 8080。開發伺服器透過 TMS_BACKEND_ORIGIN 轉送到這個埠。
#   TMS_DB_URL           後端與測試資料準備要連的資料庫，預設 jdbc:postgresql://localhost:5433/tms。
#   TMS_KAFKA_BOOTSTRAP  後端要連的 Kafka，預設 localhost:9092。
#   MAVEN_ARGS           原樣交給 mvn。本腳本不指定 Maven 本機倉庫的位置，要換位置由這裡給。
#   PLAYWRIGHT_BROWSERS_PATH  Playwright 瀏覽器的安裝位置，預設 ~/Library/Caches/ms-playwright。
#
# 結束碼：
#   0    指定的場景全部通過。
#   2    環境或參數有問題，測試沒有開始跑；訊息會說明卡在哪一步。
#   130  執行中被中斷（Ctrl-C）；收到停止訊號時是 143。
#   其他 mvn test 的結束碼（有場景失敗或有步驟沒有定義時是 1）。
#
# 它依序做的事：
#   1. 檢查參數、JDK 25、node、Playwright 瀏覽器。
#   2. 確認資料庫與 Kafka 連得上。這兩個容器不歸本腳本管，連不上就停下來。
#   3. 把 app-bdd-support 裝進 Maven 本機倉庫。
#   4. 準備 BACKEND_PORT 的後端：已經有本工作目錄的後端在跑就沿用，沒有就建置並啟動。
#   5. 準備開發伺服器：已經有本工作目錄的在跑就沿用，沒有就啟動。
#   6. 跑 RunFrontdeskWebFeaturesTest。
#   7. 關掉這一次自己起的行程（加了 --keep 就留著）。
#
# 會動到的資料：每個場景開頭都會清空 TMS_DB_URL 那個資料庫的業務資料。
#
# 要先裝好：JDK 25、node 與 npm、mvn，以及 Playwright 的瀏覽器
#   mvn -q -f app/web-bdd/pom.xml exec:java -Dexec.args="install chromium webkit"
# 資料庫與 Kafka 用 repo 根目錄的 docker compose up -d 起。

set -euo pipefail

WEB_BDD_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd "$WEB_BDD_DIR/../.." && pwd -P)"
WEB_DIR="$REPO_ROOT/app/web"
FEATURES_BASE="$REPO_ROOT/specs/truth/features"
BACKEND_JAR="$REPO_ROOT/tms/target/tms-0.0.1-SNAPSHOT.jar"
STATE_DIR="$WEB_BDD_DIR/target/run-feature"
BACKEND_LOG="$STATE_DIR/backend.log"
WEB_LOG="$STATE_DIR/web.log"
TEST_LOG="$STATE_DIR/test.log"
LOCK_DIR="$STATE_DIR/lock"
WEB_ORIGIN_FILE="$STATE_DIR/web-backend-origin"

BACKEND_PORT="${BACKEND_PORT:-8080}"
BACKEND_URL="http://127.0.0.1:${BACKEND_PORT}"
WEB_PORT="${WEB_PORT:-5199}"
WEB_URL="http://localhost:${WEB_PORT}"
TMS_DB_URL="${TMS_DB_URL:-jdbc:postgresql://localhost:5433/tms}"
TMS_KAFKA_BOOTSTRAP="${TMS_KAFKA_BOOTSTRAP:-localhost:9092}"
BROWSERS_DIR="${PLAYWRIGHT_BROWSERS_PATH:-$HOME/Library/Caches/ms-playwright}"
INSTALL_BROWSERS_HINT='mvn -q -f app/web-bdd/pom.xml exec:java -Dexec.args="install chromium webkit"'

KEEP=0
STOP_ONLY=0
FORCE_REBUILD=0
FEATURE_ARG=""
PROFILE=""

STARTED_BACKEND_PID=""
STARTED_WEB_PID=""
FOREGROUND_PID=""
LOCK_HELD=0
SERVICES_READY=0
START_SECONDS=$SECONDS

# 訊息裡的變數一律寫成 ${…}：macOS 內建的 bash 3.2 會把緊跟在後面的全形標點併進變數名。
log() {
  echo "[run-feature] $*"
}

fail() {
  echo "[run-feature] 錯誤：$*" >&2
  exit 2
}

usage() {
  sed -n '3,52p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

listener_pid() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null | head -n 1 || true
}

describe_process() {
  ps -o pid=,command= -p "$1" 2>/dev/null | cut -c1-220 || true
}

process_cwd() {
  lsof -a -p "$1" -d cwd -Fn 2>/dev/null | sed -n 's/^n//p' | head -n 1 || true
}

# 行程已經跑了幾秒。用 ps 的 etime（格式 [[日-]時:]分:秒）來算，因為它不隨系統語言改變。
uptime_seconds() {
  local etime days=0 first second third
  etime="$(ps -o etime= -p "$1" 2>/dev/null | tr -d ' ')"
  [ -n "$etime" ] || return 1
  case "$etime" in
    *-*) days="${etime%%-*}"; etime="${etime#*-}" ;;
  esac
  IFS=: read -r first second third <<EOF
$etime
EOF
  if [ -n "${third:-}" ]; then
    echo $(( 10#$days * 86400 + 10#$first * 3600 + 10#$second * 60 + 10#$third ))
  else
    echo $(( 10#$days * 86400 + 10#$first * 60 + 10#$second ))
  fi
}

# 印出某個行程與它所有子孫的編號。要在送訊號之前先收集：父行程一結束，就查不到子行程是誰生的。
process_tree() {
  local child
  for child in $(pgrep -P "$1" 2>/dev/null || true); do
    process_tree "$child"
  done
  echo "$1"
}

stop_tree() {
  local root="$1" name="$2" grace="${3:-5}" pids pid waited=0 alive
  kill -0 "$root" 2>/dev/null || return 0
  pids="$(process_tree "$root")"
  for pid in $pids; do
    kill -TERM "$pid" 2>/dev/null || true
  done
  while [ "$waited" -lt $(( grace * 5 )) ]; do
    alive=0
    for pid in $pids; do
      if kill -0 "$pid" 2>/dev/null; then alive=1; fi
    done
    [ "$alive" -eq 1 ] || break
    sleep 0.2
    waited=$(( waited + 1 ))
  done
  # 只對還在的送強制結束：已經結束的編號可能已被系統配給別的行程。
  for pid in $pids; do
    if kill -0 "$pid" 2>/dev/null; then kill -KILL "$pid" 2>/dev/null || true; fi
  done
  wait "$root" 2>/dev/null || true
  log "已關閉${name}（PID ${root}）"
}

# 讓耗時的指令跑到一半也能被中斷。bash 要等前景指令結束才處理收到的訊號，
# 所以把指令放到背景再等它；直接在前景跑的話，中斷要等 mvn 跑完才會開始收尾。
run_interruptible() {
  local rc=0
  "$@" &
  FOREGROUND_PID=$!
  wait "$FOREGROUND_PID" || rc=$?
  FOREGROUND_PID=""
  return "$rc"
}

cleanup() {
  local rc=$?
  trap - EXIT INT TERM HUP
  if [ -n "$FOREGROUND_PID" ]; then
    stop_tree "$FOREGROUND_PID" "執行中的指令" 3
  fi
  if [ "$KEEP" -eq 1 ] && [ "$SERVICES_READY" -eq 1 ]; then
    if [ -n "$STARTED_BACKEND_PID" ] || [ -n "$STARTED_WEB_PID" ]; then
      log "--keep：後端與開發伺服器留著，下一次執行會直接沿用。用完請下：bash app/web-bdd/run-feature.sh --stop"
    fi
  else
    if [ -n "$STARTED_WEB_PID" ]; then
      stop_tree "$STARTED_WEB_PID" "開發伺服器" 3
    fi
    if [ -n "$STARTED_BACKEND_PID" ]; then
      stop_tree "$STARTED_BACKEND_PID" "後端" 5
    fi
  fi
  if [ "$LOCK_HELD" -eq 1 ]; then
    rm -rf "$LOCK_DIR"
  fi
  exit "$rc"
}

for arg in "$@"; do
  case "$arg" in
    -h|--help) usage; exit 0 ;;
    --keep) KEEP=1 ;;
    --stop) STOP_ONLY=1 ;;
    --rebuild) FORCE_REBUILD=1 ;;
    -*) fail "不認得的選項「${arg}」。用法見 bash app/web-bdd/run-feature.sh --help" ;;
    *)
      if [ -z "$FEATURE_ARG" ] && [ -z "$PROFILE" ] && [ "$arg" != "desktop" ] && [ "$arg" != "ipad" ] \
        && [ "$arg" != "phone" ]; then
        FEATURE_ARG="$arg"
      elif [ -z "$PROFILE" ]; then
        PROFILE="$arg"
      else
        fail "參數太多：最多給一個 feature 路徑與一個瀏覽器設定，多出來的是「${arg}」。"
      fi
      ;;
  esac
done
case "$FEATURE_ARG" in
  guest-web|guest-web/*) FEATURES_ROOT_NAME="guest-web" ;;
  frontdesk-web|frontdesk-web/*) FEATURES_ROOT_NAME="frontdesk-web" ;;
  *) FEATURES_ROOT_NAME="" ;;
esac
if [ -n "$FEATURES_ROOT_NAME" ]; then
  FEATURE_ARG="${FEATURE_ARG#"$FEATURES_ROOT_NAME"}"
  FEATURE_ARG="${FEATURE_ARG#/}"
else
  FEATURES_ROOT_NAME="frontdesk-web"
fi
FEATURES_ROOT="$FEATURES_BASE/$FEATURES_ROOT_NAME"

if [ "$FEATURES_ROOT_NAME" = "guest-web" ]; then
  PROFILE="${PROFILE:-phone}"
else
  PROFILE="${PROFILE:-desktop}"
fi

if [ "$PROFILE" != "desktop" ] && [ "$PROFILE" != "ipad" ] && [ "$PROFILE" != "phone" ]; then
  fail "瀏覽器設定只接受 desktop、ipad 或 phone，收到的是「${PROFILE}」。"
fi

case "$WEB_PORT" in
  ''|*[!0-9]*) fail "WEB_PORT 要是數字，收到的是「${WEB_PORT}」。" ;;
esac
case "$BACKEND_PORT" in
  ''|*[!0-9]*) fail "BACKEND_PORT 要是數字，收到的是「${BACKEND_PORT}」。" ;;
esac
if [ "$WEB_PORT" = "$BACKEND_PORT" ]; then
  fail "WEB_PORT 不能和後端同一個埠（${BACKEND_PORT}）。"
fi

# 路徑結尾的「:行號」是 Cucumber 指定單一場景的寫法，不是檔名的一部分，檢查檔案在不在之前要先拿掉。
FEATURE_FILE_PART="$(printf '%s' "$FEATURE_ARG" | sed -E 's/(:[0-9]+)+$//')"
if [ -n "$FEATURE_ARG" ]; then
  case "$FEATURE_FILE_PART" in
    /*|..|../*|*/..|*/../*)
      fail "feature 路徑要寫成相對於 specs/truth/features/${FEATURES_ROOT_NAME}/ 的路徑，或以 guest-web/、frontdesk-web/ 開頭（例如 待派單/待派單清單.feature），收到的是「${FEATURE_ARG}」。"
      ;;
  esac
  if [ ! -e "$FEATURES_ROOT/$FEATURE_FILE_PART" ]; then
    echo "[run-feature] 錯誤：找不到 feature「${FEATURE_FILE_PART}」（在 specs/truth/features/${FEATURES_ROOT_NAME}/ 底下找）。可以用的有：" >&2
    (cd "$FEATURES_ROOT" && find . -name '*.feature' | sed 's|^\./|  |' | sort) >&2
    exit 2
  fi
  if [ -f "$FEATURES_ROOT/$FEATURE_FILE_PART" ]; then
    case "$FEATURE_FILE_PART" in
      *.feature) ;;
      *) fail "「${FEATURE_FILE_PART}」不是 .feature 檔。" ;;
    esac
  elif [ "$FEATURE_FILE_PART" != "$FEATURE_ARG" ]; then
    fail "行號只能接在 .feature 檔後面，「${FEATURE_FILE_PART}」是目錄。"
  fi
  CUCUMBER_FEATURES="../../specs/truth/features/${FEATURES_ROOT_NAME}/${FEATURE_ARG}"
else
  CUCUMBER_FEATURES="../../specs/truth/features/${FEATURES_ROOT_NAME}"
fi

TAG_FILTER=""
if [ "$PROFILE" = "ipad" ] && [ ! -f "$FEATURES_ROOT/$FEATURE_FILE_PART" ] && [ -z "${IPAD_ALL_SCENARIOS:-}" ]; then
  TAG_FILTER="@ipad"
fi

is_backend_of_this_checkout() {
  local pid="$1" command cwd
  command="$(ps -o command= -p "$pid" 2>/dev/null || true)"
  case "$command" in
    *java*) ;;
    *) return 1 ;;
  esac
  case "$command" in
    *"$REPO_ROOT/tms/target/"*) return 0 ;;
  esac
  cwd="$(process_cwd "$pid")"
  [ "$cwd" = "$REPO_ROOT" ] || [ "$cwd" = "$REPO_ROOT/tms" ]
}

is_web_of_this_checkout() {
  local pid="$1" command
  command="$(ps -o command= -p "$pid" 2>/dev/null || true)"
  case "$command" in
    *vite*) ;;
    *) return 1 ;;
  esac
  [ "$(process_cwd "$pid")" = "$WEB_DIR" ]
}

http_status() {
  curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$1" 2>/dev/null || true
}

backend_is_healthy() {
  curl -s --max-time 3 "$BACKEND_URL/actuator/health" 2>/dev/null | grep -q '"status":"UP"'
}

# readiness 群組含即時推送的 Kafka 消費者：它分到 partition 之後才會回 UP。
backend_is_ready() {
  curl -s --max-time 3 "$BACKEND_URL/actuator/health/readiness" 2>/dev/null | grep -q '"status":"UP"'
}

first_source_newer_than() {
  find "$REPO_ROOT/tms/src/main" "$REPO_ROOT/tms/pom.xml" -type f -newer "$1" -print -quit 2>/dev/null || true
}

take_lock() {
  mkdir -p "$STATE_DIR"
  if ! mkdir "$LOCK_DIR" 2>/dev/null; then
    local owner
    owner="$(cat "$LOCK_DIR/pid" 2>/dev/null || true)"
    if [ -n "$owner" ] && kill -0 "$owner" 2>/dev/null; then
      fail "這個工作目錄已經有另一個 run-feature.sh 在跑（PID ${owner}）。兩邊共用同一個資料庫與後端，同時跑會互相清掉資料；等它結束再跑。"
    fi
    rm -rf "$LOCK_DIR"
    mkdir "$LOCK_DIR"
  fi
  echo $$ > "$LOCK_DIR/pid"
  LOCK_HELD=1
}

trap cleanup EXIT
trap 'log "收到中斷，開始收尾"; exit 130' INT
trap 'log "收到停止訊號，開始收尾"; exit 143' TERM HUP

if [ "$STOP_ONLY" -eq 1 ]; then
  take_lock
  pid="$(listener_pid "$BACKEND_PORT")"
  if [ -z "$pid" ]; then
    log "${BACKEND_PORT} 沒有東西在跑。"
  elif is_backend_of_this_checkout "$pid"; then
    stop_tree "$pid" "後端" 5
  else
    log "${BACKEND_PORT} 上的不是這個工作目錄的後端，不動它：$(describe_process "$pid")"
  fi
  pid="$(listener_pid "$WEB_PORT")"
  if [ -z "$pid" ]; then
    log "${WEB_PORT} 沒有東西在跑。"
  elif is_web_of_this_checkout "$pid"; then
    stop_tree "$pid" "開發伺服器" 3
  else
    log "${WEB_PORT} 上的不是這個工作目錄的開發伺服器，不動它：$(describe_process "$pid")"
  fi
  exit 0
fi

log "1/6 檢查工具（瀏覽器設定：${PROFILE}；要跑的規格：${FEATURES_ROOT_NAME}/${FEATURE_ARG}）"

git_dir="$(cd "$REPO_ROOT" && cd "$(git rev-parse --git-dir 2>/dev/null || echo /nonexistent)" 2>/dev/null && pwd -P || true)"
git_common_dir="$(cd "$REPO_ROOT" && cd "$(git rev-parse --git-common-dir 2>/dev/null || echo /nonexistent)" 2>/dev/null && pwd -P || true)"
if [ -n "$git_dir" ] && [ -n "$git_common_dir" ] && [ "$git_dir" != "$git_common_dir" ]; then
  case "${MAVEN_ARGS:-}" in
    *-Dmaven.repo.local=*) ;;
    *)
      cat >&2 <<EOF
[run-feature] ============================== 警告 ==============================
[run-feature] 這裡是 git worktree，而 MAVEN_ARGS 沒有指定另一個 Maven 本機倉庫。
[run-feature] 接下來會把這個分支的 app-bdd-support 裝進 ~/.m2，
[run-feature] 主工作目錄或其他 worktree 正在跑的測試會拿到這一份而壞掉。
[run-feature] 要隔開，先設（路徑換成這個分支專用的目錄）再重跑：
[run-feature]   export MAVEN_ARGS="-Dmaven.repo.local=\$HOME/.m2-<分支>/repository -Dmaven.repo.local.tail=\$HOME/.m2/repository -Dmaven.repo.local.tail.ignoreAvailability=true"
[run-feature] =====================================================================
EOF
      ;;
  esac
fi

JAVA_HOME="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"
if [ -z "$JAVA_HOME" ] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "25'; then
  fail "找不到 JDK 25（/usr/libexec/java_home -v 25 給的是「${JAVA_HOME:-沒有結果}」）。後端與測試都要用 JDK 25，不會改用別的版本。"
fi
export JAVA_HOME

for tool in mvn node npm curl lsof nc; do
  command -v "$tool" >/dev/null 2>&1 || fail "PATH 上找不到 ${tool}。"
done

if [ "$PROFILE" = "desktop" ]; then
  browser_pattern="chromium*"
else
  browser_pattern="webkit-*"
fi
if ! compgen -G "${BROWSERS_DIR}/${browser_pattern}" >/dev/null; then
  fail "${PROFILE} 要用的 Playwright 瀏覽器還沒有裝（在 ${BROWSERS_DIR} 找不到 ${browser_pattern}）。在 repo 根目錄跑一次：${INSTALL_BROWSERS_HINT}"
fi

take_lock

log "2/6 確認資料庫與 Kafka 連得上"

db_host_port="$(printf '%s' "$TMS_DB_URL" | sed -n -E 's|^jdbc:postgresql://([^/?]+).*$|\1|p')"
[ -n "$db_host_port" ] || fail "TMS_DB_URL 看不懂（要長成 jdbc:postgresql://主機:埠/資料庫）：${TMS_DB_URL}"
db_host="${db_host_port%%:*}"
db_port="${db_host_port##*:}"
[ "$db_port" != "$db_host_port" ] || db_port=5432
if ! nc -z -w 3 "$db_host" "$db_port" >/dev/null 2>&1; then
  fail "連不上資料庫 ${db_host}:${db_port}（TMS_DB_URL=${TMS_DB_URL}）。本腳本不會啟動容器；請在 repo 根目錄跑 docker compose up -d，確認 tms-postgres 起來了再重跑。"
fi

kafka_host="${TMS_KAFKA_BOOTSTRAP%%:*}"
kafka_port="${TMS_KAFKA_BOOTSTRAP##*:}"
if ! nc -z -w 3 "$kafka_host" "$kafka_port" >/dev/null 2>&1; then
  fail "連不上 Kafka ${TMS_KAFKA_BOOTSTRAP}。本腳本不會啟動容器；請在 repo 根目錄跑 docker compose up -d，確認 tms-kafka 起來了再重跑。"
fi

log "3/6 把 app-bdd-support 裝進 Maven 本機倉庫"
if ! run_interruptible mvn -q -f "$REPO_ROOT/app/bdd-support/pom.xml" install; then
  fail "app-bdd-support 建置失敗，錯誤在上面的 mvn 輸出。"
fi

log "4/6 準備 ${BACKEND_PORT} 的後端"

build_backend_jar() {
  log "建置後端 jar（約半分鐘）"
  if ! run_interruptible "$REPO_ROOT/tms/mvnw" -q -f "$REPO_ROOT/tms/pom.xml" -DskipTests package; then
    fail "後端建置失敗，錯誤在上面的 mvn 輸出。"
  fi
  [ -f "$BACKEND_JAR" ] || fail "後端建置完卻找不到 ${BACKEND_JAR}。"
}

start_backend() {
  if [ "$FORCE_REBUILD" -eq 1 ]; then
    build_backend_jar
  elif [ ! -f "$BACKEND_JAR" ]; then
    build_backend_jar
  else
    local newer
    newer="$(first_source_newer_than "$BACKEND_JAR")"
    if [ -n "$newer" ]; then
      log "後端原始碼比 jar 新（例如 ${newer#"$REPO_ROOT"/}），重建"
      build_backend_jar
    fi
  fi

  log "啟動後端（log：${BACKEND_LOG}）"
  TMS_DB_URL="$TMS_DB_URL" TMS_KAFKA_BOOTSTRAP="$TMS_KAFKA_BOOTSTRAP" SPRING_PROFILES_ACTIVE=test-support \
    SERVER_PORT="$BACKEND_PORT" \
    nohup "$JAVA_HOME/bin/java" -jar "$BACKEND_JAR" > "$BACKEND_LOG" 2>&1 &
  STARTED_BACKEND_PID=$!

  local waited=0
  until backend_is_healthy; do
    if ! kill -0 "$STARTED_BACKEND_PID" 2>/dev/null; then
      echo "[run-feature] 後端啟動後就結束了，log 最後 30 行：" >&2
      tail -n 30 "$BACKEND_LOG" >&2 || true
      STARTED_BACKEND_PID=""
      fail "後端起不來，完整的 log 在 ${BACKEND_LOG}。"
    fi
    if [ "$waited" -ge 120 ]; then
      echo "[run-feature] 後端 log 最後 30 行：" >&2
      tail -n 30 "$BACKEND_LOG" >&2 || true
      fail "等了 120 秒，後端的健康檢查（${BACKEND_URL}/actuator/health）還是沒有通過，完整的 log 在 ${BACKEND_LOG}。"
    fi
    sleep 1
    waited=$(( waited + 1 ))
  done

  waited=0
  until backend_is_ready; do
    if [ "$waited" -ge 30 ]; then
      log "警告：等了 30 秒，後端的 readiness（${BACKEND_URL}/actuator/health/readiness）還不是 UP：事件串流消費者還沒分到 partition。照常往下跑，但即時更新的場景可能等不到事件。"
      break
    fi
    sleep 1
    waited=$(( waited + 1 ))
  done
  log "後端就緒（PID ${STARTED_BACKEND_PID}）"
}

reuse_backend() {
  local pid="$1" uptime started_at reference newer
  if ! is_backend_of_this_checkout "$pid"; then
    fail "${BACKEND_PORT} 已經被別的行程占用，而且不是這個工作目錄（${REPO_ROOT}）的後端：$(describe_process "$pid")。本腳本不會動它；請它的主人停掉，或等它結束再跑。"
  fi
  if ! backend_is_healthy; then
    fail "${BACKEND_PORT} 上有這個工作目錄的後端（PID ${pid}），但健康檢查沒有通過。它可能還在啟動或已經壞了；稍等再跑，或下 bash app/web-bdd/run-feature.sh --stop 關掉它再重跑。"
  fi
  if [ "$(http_status "$BACKEND_URL/api/v1/test/clock")" != "200" ]; then
    fail "${BACKEND_PORT} 上的後端（PID ${pid}）沒有開放撥時鐘（啟動時沒帶 test-support profile），驗收測試不能用。下 bash app/web-bdd/run-feature.sh --stop 關掉它再重跑，本腳本會用對的設定啟動。"
  fi
  if [ "$FORCE_REBUILD" -eq 1 ]; then
    fail "給了 --rebuild，但 ${BACKEND_PORT} 上的後端（PID ${pid}）還在跑。先下 bash app/web-bdd/run-feature.sh --stop 再重跑。"
  fi

  uptime="$(uptime_seconds "$pid")" || fail "查不到後端（PID ${pid}）已經跑了多久。"
  started_at=$(( $(date +%s) - uptime ))
  reference="$STATE_DIR/backend-started-at"
  touch -t "$(date -r "$started_at" +%Y%m%d%H%M.%S)" "$reference"
  newer="$(first_source_newer_than "$reference")"
  if [ -n "$newer" ]; then
    fail "${BACKEND_PORT} 上的後端（PID ${pid}）比後端原始碼舊（例如 ${newer#"$REPO_ROOT"/} 在它啟動之後改過），拿它測會測到舊的程式。先下 bash app/web-bdd/run-feature.sh --stop 再重跑，本腳本會重建並重新啟動。"
  fi

  if [ "$uptime" -lt 10 ]; then
    sleep $(( 10 - uptime ))
  fi
  log "沿用已經在跑的後端（PID ${pid}，已跑 ${uptime} 秒）"
}

refuse_foreign_web_listener() {
  local pid="$1"
  if [ -n "$pid" ] && ! is_web_of_this_checkout "$pid"; then
    fail "${WEB_PORT} 已經被別的行程占用，而且不是這個工作目錄的開發伺服器：$(describe_process "$pid")。本腳本不會動它；請停掉它，或用 WEB_PORT=<另一個埠> 重跑。"
  fi
}
refuse_foreign_web_listener "$(listener_pid "$WEB_PORT")"

pid="$(listener_pid "$BACKEND_PORT")"
if [ -n "$pid" ]; then
  reuse_backend "$pid"
else
  start_backend
fi

log "5/6 準備開發伺服器（${WEB_URL}）"

install_web_dependencies() {
  cd "$WEB_DIR"
  npm ci
}

start_web() {
  if [ ! -d "$WEB_DIR/node_modules" ]; then
    log "app/web/node_modules 不存在，先 npm ci"
    if ! run_interruptible install_web_dependencies; then
      fail "npm ci 失敗，錯誤在上面的輸出。"
    fi
  fi
  [ -x "$WEB_DIR/node_modules/.bin/vite" ] || fail "找不到 app/web/node_modules/.bin/vite。刪掉 app/web/node_modules 再重跑，本腳本會重新 npm ci。"

  log "啟動開發伺服器（log：${WEB_LOG}）"
  (cd "$WEB_DIR" && TMS_BACKEND_ORIGIN="$BACKEND_URL" exec nohup node_modules/.bin/vite --port "$WEB_PORT" --strictPort) \
    > "$WEB_LOG" 2>&1 &
  STARTED_WEB_PID=$!
  echo "$BACKEND_URL" > "$WEB_ORIGIN_FILE"

  local waited=0
  until [ "$(http_status "$WEB_URL/")" = "200" ]; do
    if ! kill -0 "$STARTED_WEB_PID" 2>/dev/null; then
      echo "[run-feature] 開發伺服器啟動後就結束了，log 最後 20 行：" >&2
      tail -n 20 "$WEB_LOG" >&2 || true
      STARTED_WEB_PID=""
      fail "開發伺服器起不來，完整的 log 在 ${WEB_LOG}。"
    fi
    if [ "$waited" -ge 60 ]; then
      fail "等了 60 秒，開發伺服器（${WEB_URL}）還是沒有回應，log 在 ${WEB_LOG}。"
    fi
    sleep 1
    waited=$(( waited + 1 ))
  done
  log "開發伺服器就緒（PID ${STARTED_WEB_PID}）"
}

pid="$(listener_pid "$WEB_PORT")"
if [ -n "$pid" ]; then
  refuse_foreign_web_listener "$pid"
  if [ "$(http_status "$WEB_URL/")" != "200" ]; then
    fail "${WEB_PORT} 上有這個工作目錄的開發伺服器（PID ${pid}），但首頁沒有回 200。下 bash app/web-bdd/run-feature.sh --stop 關掉它再重跑。"
  fi
  if [ "$(cat "$WEB_ORIGIN_FILE" 2>/dev/null || true)" != "$BACKEND_URL" ]; then
    fail "${WEB_PORT} 上的開發伺服器（PID ${pid}）不是轉送到 ${BACKEND_URL} 啟動的。下 bash app/web-bdd/run-feature.sh --stop 關掉它再重跑。"
  fi
  log "沿用已經在跑的開發伺服器（PID ${pid}）"
else
  start_web
fi

SERVICES_READY=1

log "6/6 跑 RunFrontdeskWebFeaturesTest（${CUCUMBER_FEATURES#../../}，${PROFILE}${TAG_FILTER:+，只跑標了 ${TAG_FILTER} 的場景}）"

run_tests() {
  set -o pipefail
  cd "$WEB_BDD_DIR"
  mvn test \
    -Dcucumber.features="$CUCUMBER_FEATURES" \
    -Dweb.profile="$PROFILE" \
    ${TAG_FILTER:+"-Dcucumber.filter.tags=$TAG_FILTER"} \
    -Dweb.baseUrl="$WEB_URL" \
    -Dbackend.base.url="$BACKEND_URL" \
    -Dbackend.db.url="$TMS_DB_URL" 2>&1 | tee "$TEST_LOG"
}

TEST_EXIT=0
run_interruptible run_tests || TEST_EXIT=$?

if [ "$TEST_EXIT" -ne 0 ] && grep -q "Executable doesn't exist" "$TEST_LOG" 2>/dev/null; then
  log "Playwright 找不到瀏覽器的執行檔：版本和已安裝的不合。在 repo 根目錄跑一次：${INSTALL_BROWSERS_HINT}"
fi

elapsed=$(( SECONDS - START_SECONDS ))
if [ "$TEST_EXIT" -eq 0 ]; then
  log "結果：通過（mvn 結束碼 0，共 ${elapsed} 秒）"
else
  log "結果：沒有通過（mvn 結束碼 ${TEST_EXIT}，共 ${elapsed} 秒）。失敗或沒有定義的步驟在上面的輸出。"
  log "失敗場景的截圖：app/web-bdd/target/failure-screenshots/；後端 log：${BACKEND_LOG}"
fi
log "報表：app/web-bdd/target/cucumber-reports/frontdesk-web.html；完整輸出：${TEST_LOG}"
exit "$TEST_EXIT"
