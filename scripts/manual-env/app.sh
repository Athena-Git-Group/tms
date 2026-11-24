#!/usr/bin/env bash
#
# 把員工 App 裝到 iOS simulator 並打開，連手動測試環境的後端（預設 :8083）。
# 沒有開著的 simulator 就自己挑一台 iPhone 開機。
#
# 用法：
#   bash scripts/manual-env/app.sh                     # 重新編譯（約一兩分鐘）→ 安裝 → 打開
#   bash scripts/manual-env/app.sh --no-build          # 沿用上次編好、而且是連這個後端的版本，只安裝並打開
#   bash scripts/manual-env/app.sh --run               # 改用 flutter run 前景執行，可以 hot reload（改 App 程式時用）
#   bash scripts/manual-env/app.sh --device "iPhone Air"   # 指定 simulator（名稱或 UDID）
#
# 為什麼一定要重新編譯：App 連哪個後端是編譯時決定的。跑過 App 的驗收測試後，simulator 上會換成
# 連 8080／8081 的版本，這時帳號密碼都對也登不進手動環境，重跑這支就好。

set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

usage() { die "用法：app.sh [--no-build | --run] [--device <名稱或 UDID>]"; }

mode=build
device_arg=""
while (($#)); do
  case "$1" in
    --no-build) mode=reuse ;;
    --run) mode=run ;;
    --device) device_arg="${2:-}"; [[ -n "$device_arg" ]] || usage; shift ;;
    *) usage ;;
  esac
  shift
done

xcrun --find simctl >/dev/null 2>&1 \
  || die "找不到 Xcode 的 simctl：先從 App Store 裝 Xcode，再跑 sudo xcode-select -s /Applications/Xcode.app"
if [[ $mode != reuse ]]; then
  command -v flutter >/dev/null || die "找不到 flutter：裝好 Flutter SDK 並把它的 bin 加進 PATH（flutter doctor 要過 iOS 那項）"
fi

pick_device() {
  xcrun simctl list devices available -j | python3 -c '
import json, re, sys
wanted = sys.argv[1]
rows = []
for runtime, devices in json.load(sys.stdin)["devices"].items():
    m = re.search(r"iOS-(\d+)-(\d+)(.*)$", runtime)
    if not m:
        continue
    plain = 0 if m.group(3) else 1
    for d in devices:
        rows.append(((int(m.group(1)), int(m.group(2)), plain), d))
def emit(d):
    print("\t".join([d["udid"], d["name"], d["state"]]))
    sys.exit(0)
if wanted:
    hits = [d for _, d in rows if wanted in (d["udid"], d["name"])]
    hits.sort(key=lambda d: d["state"] != "Booted")
    if hits:
        emit(hits[0])
    sys.exit(1)
iphones = [(v, d) for v, d in rows if d["name"].startswith("iPhone")]
for _, d in iphones:
    if d["state"] == "Booted":
        emit(d)
if iphones:
    newest = max(v for v, _ in iphones)
    emit(next(d for v, d in iphones if v == newest))
sys.exit(1)
' "$device_arg"
}

if ! device="$(pick_device)"; then
  [[ -z "$device_arg" ]] || die "找不到 simulator「${device_arg}」；用 xcrun simctl list devices available 看有哪些"
  die "沒有任何 iPhone simulator：Xcode → Settings → Components 下載 iOS 平台"
fi
IFS=$'\t' read -r udid name state <<<"$device"

if [[ "$state" != "Booted" ]]; then
  log "開機 simulator：${name}（${udid}）"
  xcrun simctl boot "$udid"
  xcrun simctl bootstatus "$udid" -b >/dev/null
fi
log "使用 simulator：${name}（${udid}）"
open -a Simulator 2>/dev/null \
  || log "沒看到 simulator 視窗的話：Xcode → Open Developer Tool → Simulator"

if [[ $mode == run ]]; then
  log "flutter run，App 連 ${APP_API_BASE_URL}；按 r hot reload、q 結束"
  cd "$APP_DIR"
  exec flutter run -d "$udid" --dart-define=API_BASE_URL="$APP_API_BASE_URL"
fi

if [[ $mode == build ]]; then
  log "編譯員工 App（debug、simulator 版），連 ${APP_API_BASE_URL}"
  (cd "$APP_DIR" && flutter build ios --simulator --debug --dart-define=API_BASE_URL="$APP_API_BASE_URL")
  app_targets_backend "$APP_BUILD" || die "編好的 App 裡找不到 ${APP_API_BASE_URL}，編譯結果不對，看上面 flutter 的輸出"
else
  [[ -d "$APP_BUILD" ]] || die "還沒有編好的 App（${APP_BUILD}），拿掉 --no-build 重跑"
  app_targets_backend "$APP_BUILD" \
    || die "上次編好的 App 不是連 ${APP_API_BASE_URL}（多半是驗收測試編的 8080 版），拿掉 --no-build 重跑"
  log "沿用上次編好的 App（連 ${APP_API_BASE_URL}）"
fi

xcrun simctl install "$udid" "$APP_BUILD"
xcrun simctl terminate "$udid" "$APP_BUNDLE_ID" 2>/dev/null || true
xcrun simctl launch "$udid" "$APP_BUNDLE_ID" >/dev/null
log "員工 App 已打開；密碼都是 demo1234，App 登入不用填飯店代碼（新走一遍用 hk2 房務、mt2 工務、mg1 主管）"
