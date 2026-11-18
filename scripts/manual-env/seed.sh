#!/usr/bin/env bash
#
# 對手動測試環境的資料庫（預設 tms_manual）跑 scripts/seed-demo.sh。
# 會先清掉 tenant 1 的資料再重灌示範資料，等於把手動環境重置回初始狀態。
# 資料庫要先套好遷移：先跑一次 start.sh（後端啟動時 Flyway 會建表）。
#
# 用法：bash scripts/manual-env/seed.sh

set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

if [[ "$DB_NAME" == "tms" ]]; then
  echo "[manual-env] 拒絕對 tms 灌資料：那是驗收測試用的 database" >&2
  exit 2
fi

applied="$(PGPASSWORD="$DB_ADMIN_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_ADMIN_USER" -d "$DB_NAME" \
  -Atc "SELECT count(*) FROM flyway_schema_history WHERE success" 2>/dev/null || echo 0)"
if [[ "$applied" == "0" ]]; then
  echo "[manual-env] ${DB_NAME} 還沒套遷移，先跑 bash scripts/manual-env/start.sh backend" >&2
  exit 2
fi

DEMO_DB_HOST="$DB_HOST" DEMO_DB_PORT="$DB_PORT" DEMO_DB_NAME="$DB_NAME" \
DEMO_DB_USER="$DB_ADMIN_USER" DEMO_DB_PASSWORD="$DB_ADMIN_PASSWORD" \
  bash "$REPO_ROOT/scripts/seed-demo.sh"
