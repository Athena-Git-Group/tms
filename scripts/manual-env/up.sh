#!/usr/bin/env bash
#
# 一鍵重建手動測試環境：（可選）重新打包後端 → 停掉舊的 → 起後端＋網頁 → 灌示範資料 →（可選）裝員工 App。
#
# 用法：
#   bash scripts/manual-env/up.sh               # 用現有的 jar，重起並重灌資料
#   bash scripts/manual-env/up.sh --build       # 先用目前的程式碼重新打包後端（跳過測試）再重起；配 --app 時 App 也重編
#   bash scripts/manual-env/up.sh --keep-data   # 重起但不重灌資料（保留你手動測到一半的資料和房間 QR）
#   bash scripts/manual-env/up.sh --phone       # 網頁聽所有網卡，同一個區網的手機可以掃碼
#   bash scripts/manual-env/up.sh --app         # 最後也把員工 App 裝到 simulator 並打開（見 app.sh）
#
# 參數可以一起用，例如第一次：up.sh --build --phone --app。細節見同目錄 README.md。

set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

BUILD=0
SEED=1
APP=0
for arg in "$@"; do
  case "$arg" in
    --build) BUILD=1 ;;
    --keep-data) SEED=0 ;;
    --phone) export MANUAL_WEB_HOST=0.0.0.0 ;;
    --app) APP=1 ;;
    *) die "用法：up.sh [--build] [--keep-data] [--phone] [--app]" ;;
  esac
done

for port in "$DB_PORT" "${KAFKA_BOOTSTRAP##*:}"; do
  if ! lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    command -v docker >/dev/null || die "Postgres／Kafka 沒起，也找不到 docker：先裝 OrbStack 或 Docker Desktop"
    log ":${port} 沒人在聽，執行 docker compose up -d"
    (cd "$REPO_ROOT" && docker compose up -d --wait)
    break
  fi
done

if [[ $BUILD == 1 ]]; then
  log "重新打包後端（跳過測試）"
  java_home="$(/usr/libexec/java_home -v 25 2>/dev/null)" || die "找不到 JDK 25（/usr/libexec/java_home -v 25 沒結果）"
  (cd "$REPO_ROOT" && JAVA_HOME="$java_home" ./tms/mvnw -q -f tms/pom.xml -DskipTests clean package)
fi

bash "$MANUAL_ENV_DIR/stop.sh"
bash "$MANUAL_ENV_DIR/start.sh"
if [[ $SEED == 1 ]]; then
  bash "$MANUAL_ENV_DIR/seed.sh"
fi

if [[ $APP == 1 ]]; then
  if [[ $BUILD == 0 ]] && app_targets_backend "$APP_BUILD"; then
    bash "$MANUAL_ENV_DIR/app.sh" --no-build
  else
    bash "$MANUAL_ENV_DIR/app.sh"
  fi
  app_line="已裝到 simulator 並打開"
else
  app_line="bash scripts/manual-env/app.sh（裝到 simulator 並打開）"
fi

if web_listens_all; then
  guest_line="bash scripts/manual-env/qr.sh 407 --phone（手機掃）｜qr.sh --all --phone（全部在住房一張總表）"
else
  guest_line="$(bash "$MANUAL_ENV_DIR/qr.sh" 407 2>/dev/null || echo "bash scripts/manual-env/qr.sh 407")（407 房，電腦瀏覽器開）"
fi

cat <<EOF

[manual-env] 好了
  櫃檯 Web：${DESK_URL}
            飯店代碼 ${TENANT_CODE}；fd1 櫃檯、mg1／mg2 主管、dm1 值班主管
  住客頁：  ${guest_line}
  員工 App：${app_line}
            新走一遍用 hk2 房務、mt2 工務，mg1／mg2 主管驗收工務單
            （hk1、mt1、mg1 身上已有進行中的工作，一人同時只能做一件）
  密碼都是 demo1234。看狀態：bash scripts/manual-env/status.sh　停止：bash scripts/manual-env/stop.sh
EOF
