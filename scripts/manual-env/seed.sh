#!/usr/bin/env bash
#
# 對手動測試環境的資料庫（預設 tms_manual）跑 scripts/seed-demo.sh，再補上住客需求的分流規則。
# 會先清掉 tenant 1 的資料再重灌示範資料，等於把手動環境重置回初始狀態；房間的 QR 代碼也會換新。
# 資料庫要先套好遷移：先跑一次 start.sh（後端啟動時 Flyway 會建表）。
#
# 用法：bash scripts/manual-env/seed.sh

set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

[[ "$DB_NAME" != "tms" ]] || die "拒絕對 tms 灌資料：那是驗收測試用的 database"

applied="$(psql_manual -Atc "SELECT count(*) FROM flyway_schema_history WHERE success" 2>/dev/null || echo 0)"
[[ "$applied" != "0" ]] || die "${DB_NAME} 還沒套遷移，先跑 bash scripts/manual-env/start.sh backend"

DEMO_DB_HOST="$DB_HOST" DEMO_DB_PORT="$DB_PORT" DEMO_DB_NAME="$DB_NAME" \
DEMO_DB_USER="$DB_ADMIN_USER" DEMO_DB_PASSWORD="$DB_ADMIN_PASSWORD" \
  bash "$REPO_ROOT/scripts/seed-demo.sh"

dev_seed="$REPO_ROOT/tms/src/main/resources/db/migration/V2__seed_dev.sql"
rules="$(sed -nE "s/^[[:space:]]*\(1, 'classification_rules',[[:space:]]*'([^']*)'.*/\1/p" "$dev_seed")"
[[ -n "$rules" ]] || die "在 ${dev_seed} 找不到 tenant 1 的 classification_rules，分流規則沒補上"

psql_manual --quiet -v rules="$rules" <<'SQL'
INSERT INTO tenant_config (tenant_id, key, value, updated_at, updated_by)
VALUES (1, 'classification_rules', :'rules'::jsonb, now(), NULL)
ON CONFLICT (tenant_id, key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at;
SQL
log "已從 V2__seed_dev.sql 補上住客需求的分流規則（classification_rules）"
