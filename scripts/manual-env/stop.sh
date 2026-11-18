#!/usr/bin/env bash
#
# 停手動測試環境。只停 ~/.tms-manual-env 裡 pid 檔記下的行程，不碰驗收測試的 8080／5199。
#
# 用法：
#   bash scripts/manual-env/stop.sh            # 後端和網頁都停
#   bash scripts/manual-env/stop.sh backend    # 只停後端（換新版 jar 時用）
#   bash scripts/manual-env/stop.sh web        # 只停網頁
#
# 資料庫 tms_manual 的資料保留，下次 start.sh 接著用。

set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

stop_one() {
  local name="$1" pid_file="$2"
  if ! pid_alive "$pid_file"; then
    echo "[manual-env] ${name} 沒有在跑"
    rm -f "$pid_file"
    return
  fi
  local pid
  pid="$(cat "$pid_file")"
  kill "$pid"
  for _ in $(seq 1 20); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 1
  done
  kill -0 "$pid" 2>/dev/null && kill -9 "$pid"
  rm -f "$pid_file"
  echo "[manual-env] ${name} 已停（pid ${pid}）"
}

case "${1:-all}" in
  all) stop_one 網頁 "$WEB_PID_FILE"; stop_one 後端 "$BACKEND_PID_FILE" ;;
  backend) stop_one 後端 "$BACKEND_PID_FILE" ;;
  web) stop_one 網頁 "$WEB_PID_FILE" ;;
  *) echo "用法：stop.sh [all|backend|web]" >&2; exit 2 ;;
esac
