#!/usr/bin/env bash
#
# 看手動測試環境現在的狀態：後端、網頁、資料庫、simulator 上的員工 App 各連到哪裡。只讀不改。
#
# 用法：bash scripts/manual-env/status.sh

set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

listen_addrs() {
  lsof -nP -a -p "$1" -iTCP:"$2" -sTCP:LISTEN 2>/dev/null | awk 'NR > 1 { print $9 }' | sort -u | paste -sd ' ' -
}

show_process() {
  local name="$1" pid_file="$2" port="$3"
  if pid_alive "$pid_file"; then
    local pid addrs
    pid="$(cat "$pid_file")"
    addrs="$(listen_addrs "$pid" "$port")"
    log "${name}：在跑，pid ${pid}，聽 ${addrs:-（還沒開始聽 :${port}）}"
  elif lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    log "${name}：沒在跑，但 :${port} 被別的行程佔用"
  else
    log "${name}：沒在跑"
  fi
}

show_process "後端" "$BACKEND_PID_FILE" "$BACKEND_PORT"
show_process "網頁" "$WEB_PID_FILE" "$WEB_PORT"

if in_house="$(psql_manual -Atc "
    SELECT string_agg(DISTINCT r.room_no, ' ' ORDER BY r.room_no)
      FROM rooms r
      JOIN reservations_cache rc ON rc.tenant_id = r.tenant_id AND rc.room_id = r.id
     WHERE r.tenant_id = 1 AND rc.superseded_at IS NULL
       AND rc.checkin_at <= now() AND rc.checkout_at > now()" 2>/dev/null)"; then
  log "資料庫：${DB_HOST}:${DB_PORT}/${DB_NAME} 連得上；現在在住房：${in_house:-沒有（要重灌資料 seed.sh）}"
else
  log "資料庫：${DB_HOST}:${DB_PORT}/${DB_NAME} 連不上，或還沒建（docker compose up -d，再 start.sh）"
fi

booted="$(xcrun simctl list devices booted 2>/dev/null | sed -nE 's/^ +(.+) \(([0-9A-F-]{36})\) \(Booted\).*/\2\t\1/p' || true)"
if [[ -z "$booted" ]]; then
  log "員工 App：沒有開機中的 simulator（bash scripts/manual-env/app.sh）"
else
  while IFS=$'\t' read -r udid name; do
    if ! container="$(xcrun simctl get_app_container "$udid" "$APP_BUNDLE_ID" 2>/dev/null)"; then
      log "員工 App：${name} 上沒裝（bash scripts/manual-env/app.sh）"
    elif app_targets_backend "$container"; then
      log "員工 App：${name} 上有裝，連 ${APP_API_BASE_URL}"
    else
      log "員工 App：${name} 上有裝，但不是連 ${APP_API_BASE_URL}（多半是驗收測試的 8080 版，登不進手動環境；bash scripts/manual-env/app.sh 重裝）"
    fi
  done <<<"$booted"
fi

log "櫃檯 Web：${DESK_URL}"
ip="$(lan_ip)"
if web_listens_all && [[ -n "$ip" ]]; then
  log "手機（同一個區網）：網頁在 http://${ip}:${WEB_PORT}；房間 QR 用 bash scripts/manual-env/qr.sh <房號> --phone"
else
  log "手機掃碼：網頁只聽本機，要先 bash scripts/manual-env/up.sh --keep-data --phone"
fi
