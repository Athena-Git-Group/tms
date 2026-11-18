#!/usr/bin/env bash
# 用法：with-lock.sh <鎖名> <指令…>
# 同一個鎖名同時只讓一個指令跑，其餘排隊。給 Phase 3 平行的 agent 共用同一個 target 目錄編譯與跑測試時用。
# 鎖名：tms（tms/ 的 mvn）、webbdd（app/web-bdd 的 mvn 與 run-feature.sh）、bddsupport（app/bdd-support 的 install）。
set -u
name="$1"; shift
dir="/tmp/tms-004-locks/$name"
mkdir -p "$(dirname "$dir")"
waited=0
until mkdir "$dir" 2>/dev/null; do
  holder="$(cat "$dir/pid" 2>/dev/null || true)"
  if [ -n "$holder" ] && ! kill -0 "$holder" 2>/dev/null; then
    rm -rf "$dir"; continue
  fi
  if [ $((waited % 30)) -eq 0 ]; then echo "[with-lock] 等待鎖 ${name}（持有者 PID ${holder:-?}）…" >&2; fi
  sleep 2; waited=$((waited + 2))
  if [ "$waited" -ge 1800 ]; then echo "[with-lock] 等了 30 分鐘還拿不到鎖 ${name}，放棄" >&2; exit 75; fi
done
echo $$ > "$dir/pid"
trap 'rm -rf "$dir"' EXIT INT TERM
"$@"
