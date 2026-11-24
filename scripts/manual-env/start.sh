#!/usr/bin/env bash
#
# 起手動測試環境：後端 8083（真實時鐘）＋ 櫃檯 Web 開發伺服器 5300。
#
# 用法：
#   bash scripts/manual-env/start.sh            # 後端和網頁都起
#   bash scripts/manual-env/start.sh backend    # 只起後端
#   bash scripts/manual-env/start.sh web        # 只起網頁
#
# 可重複執行：已經在跑的部分會沿用，不會重起。要換新版後端，先 stop.sh backend 再 start.sh。
# 細節見同目錄 README.md。

set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

WANT_BACKEND=1
WANT_WEB=1
case "${1:-all}" in
  all) ;;
  backend) WANT_WEB=0 ;;
  web) WANT_BACKEND=0 ;;
  *) echo "用法：start.sh [all|backend|web]" >&2; exit 2 ;;
esac

mkdir -p "$STATE_DIR"

port_in_use() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

ensure_database() {
  command -v psql >/dev/null || { echo "[manual-env] 找不到 psql（brew install postgresql@15）" >&2; exit 2; }
  local exists
  exists="$(PGPASSWORD="$DB_ADMIN_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_ADMIN_USER" -d postgres \
    -Atc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'")" \
    || { echo "[manual-env] 連不上 Postgres ${DB_HOST}:${DB_PORT}，先 docker compose up -d" >&2; exit 2; }
  if [[ "$exists" != "1" ]]; then
    echo "[manual-env] 建立 database ${DB_NAME}（表由後端啟動時的 Flyway 建）"
    PGPASSWORD="$DB_ADMIN_PASSWORD" createdb -h "$DB_HOST" -p "$DB_PORT" -U "$DB_ADMIN_USER" -O "$DB_ADMIN_USER" "$DB_NAME"
  fi
}

start_backend() {
  if pid_alive "$BACKEND_PID_FILE"; then
    echo "[manual-env] 後端已在跑（pid $(cat "$BACKEND_PID_FILE")），沿用"
    return
  fi
  if port_in_use "$BACKEND_PORT"; then
    echo "[manual-env] ${BACKEND_PORT} 被別的行程佔用，不是這套環境起的，停下來" >&2; exit 2
  fi
  [[ -x "$JAVA_BIN" ]] || { echo "[manual-env] 找不到 JDK 25：${JAVA_BIN}（裝 JDK 25，或用 MANUAL_JAVA 指定 java 執行檔）" >&2; exit 2; }
  [[ -f "$SOURCE_JAR" ]] || { echo "[manual-env] 找不到後端 jar：${SOURCE_JAR}，先 bash scripts/manual-env/up.sh --build" >&2; exit 2; }

  ensure_database

  # 複製一份到專案外再啟動：驗收測試重新打包時會覆寫 target/ 裡的 jar，不能讓它換掉正在跑的這份。
  cp "$SOURCE_JAR" "$RUN_JAR.tmp" && mv "$RUN_JAR.tmp" "$RUN_JAR"

  echo "[manual-env] 起後端 :${BACKEND_PORT}，資料庫 ${DB_NAME}，Kafka topic 前綴 ${KAFKA_TOPIC_PREFIX}"
  (
    cd "$STATE_DIR"
    # 真實時鐘：明確拿掉 TMS_TEST_CLOCK_ENABLED，避免從外層 shell 繼承到。
    env -u TMS_TEST_CLOCK_ENABLED \
      TMS_DB_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}" \
      TMS_DB_ADMIN_USER="$DB_ADMIN_USER" TMS_DB_ADMIN_PASSWORD="$DB_ADMIN_PASSWORD" \
      TMS_KAFKA_BOOTSTRAP="$KAFKA_BOOTSTRAP" \
      SERVER_PORT="$BACKEND_PORT" \
      nohup "$JAVA_BIN" -jar "$RUN_JAR" \
        --tms.test-clock.enabled=false \
        --tms.kafka.topic.task-events="${KAFKA_TOPIC_PREFIX}tms.task-events" \
        --tms.kafka.topic.push="${KAFKA_TOPIC_PREFIX}tms.push" \
        --tms.kafka.topic.push-dead-letter="${KAFKA_TOPIC_PREFIX}tms.push.DLT" \
        --tms.kafka.topic.pms-status="${KAFKA_TOPIC_PREFIX}tms.pms-status" \
        --tms.stream.consumer-group="${KAFKA_TOPIC_PREFIX}sse" \
        > "$BACKEND_LOG" 2>&1 &
    echo $! > "$BACKEND_PID_FILE"
  )

  local url="http://127.0.0.1:${BACKEND_PORT}/actuator/health"
  for _ in $(seq 1 90); do
    if curl -fsS "$url" 2>/dev/null | grep -q '"UP"'; then
      echo "[manual-env] 後端 UP（pid $(cat "$BACKEND_PID_FILE")），log：$BACKEND_LOG"
      return
    fi
    if ! pid_alive "$BACKEND_PID_FILE"; then
      echo "[manual-env] 後端啟動失敗，看 $BACKEND_LOG 末段：" >&2; tail -30 "$BACKEND_LOG" >&2; exit 1
    fi
    sleep 2
  done
  echo "[manual-env] 後端 180 秒內沒有 UP，看 $BACKEND_LOG" >&2; exit 1
}

start_web() {
  if pid_alive "$WEB_PID_FILE"; then
    echo "[manual-env] 網頁已在跑（pid $(cat "$WEB_PID_FILE")），沿用"
    return
  fi
  if port_in_use "$WEB_PORT"; then
    echo "[manual-env] ${WEB_PORT} 被別的行程佔用（舊預覽？），先停掉它" >&2; exit 2
  fi
  [[ -d "$REPO_ROOT/app/web/node_modules" ]] || { echo "[manual-env] app/web 還沒 npm install" >&2; exit 2; }

  echo "[manual-env] 起網頁 :${WEB_PORT}，/api、/staff/stream 與 /guest 轉給 :${BACKEND_PORT}"
  (
    cd "$REPO_ROOT/app/web"
    # 沿用 vite.config.ts，只在啟動時蓋掉埠與代理目標，不改設定檔。
    MANUAL_WEB_PORT="$WEB_PORT" MANUAL_WEB_HOST="$WEB_HOST" MANUAL_BACKEND_ORIGIN="http://localhost:${BACKEND_PORT}" \
    nohup node --input-type=module -e "
import { createServer } from 'vite';
const target = process.env.MANUAL_BACKEND_ORIGIN;
const streamProxy = {
  target,
  changeOrigin: true,
  configure: (proxy) => {
    proxy.on('proxyRes', (proxyRes, _req, res) => {
      proxyRes.on('close', () => { if (!proxyRes.complete) res.destroy(); });
    });
  },
};
const server = await createServer({
  configFile: 'vite.config.ts',
  server: {
    port: Number(process.env.MANUAL_WEB_PORT),
    host: process.env.MANUAL_WEB_HOST,
    strictPort: true,
    // vite 會把這裡和 vite.config.ts 的 proxy 合併；設定檔有的路徑沒在這裡蓋掉，就還是打 8080。
    proxy: {
      '/api': { target, changeOrigin: true },
      '/staff/stream': streamProxy,
      '/guest/stream': streamProxy,
      '/guest': { target, changeOrigin: true },
    },
  },
});
await server.listen();
server.printUrls();
" > "$WEB_LOG" 2>&1 &
    echo $! > "$WEB_PID_FILE"
  )

  for _ in $(seq 1 30); do
    if curl -fs -o /dev/null "http://localhost:${WEB_PORT}/"; then
      echo "[manual-env] 網頁 UP：http://localhost:${WEB_PORT}/demo-hotel/login（log：${WEB_LOG}）"
      return
    fi
    if ! pid_alive "$WEB_PID_FILE"; then
      echo "[manual-env] 網頁啟動失敗，看 ${WEB_LOG}：" >&2; tail -30 "$WEB_LOG" >&2; exit 1
    fi
    sleep 1
  done
  echo "[manual-env] 網頁 30 秒內沒有回應，看 $WEB_LOG" >&2; exit 1
}

[[ $WANT_BACKEND == 1 ]] && start_backend
[[ $WANT_WEB == 1 ]] && start_web
exit 0
