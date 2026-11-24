#!/usr/bin/env bash
#
# 產生手動測試環境的住客掃碼網址。
# 以主管 mg1 登入後端取房間的 QR 代碼；代碼第一次取時才建立，重灌資料後會換新的。
#
# 用法：
#   bash scripts/manual-env/qr.sh 407                # 印出 localhost 網址（電腦瀏覽器直接開）
#   bash scripts/manual-env/qr.sh 407 --phone        # 改用這台 Mac 的區網 IP，存成 QR 圖並打開（手機掃）
#   bash scripts/manual-env/qr.sh --all [--phone]    # 所有在住房各一張 QR 圖，加一張可列印的總表並打開
#
# --phone 需要網頁聽所有網卡：bash scripts/manual-env/up.sh --keep-data --phone
# QR 圖需要 qrencode（brew install qrencode）。
# 房間要有「現在在住」的訂單才掃得進去，否則住客頁會顯示「請洽櫃檯」。

set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

usage() { die "用法：qr.sh <房號> [--phone]，或 qr.sh --all [--phone]"; }

room=""
all=0
phone=0
for arg in "$@"; do
  case "$arg" in
    --all) all=1 ;;
    --phone) phone=1 ;;
    -*) usage ;;
    *) [[ -z "$room" ]] || usage; room="$arg" ;;
  esac
done
[[ $all == 1 && -z "$room" || $all == 0 && -n "$room" ]] || usage

need_qrencode() {
  command -v qrencode >/dev/null || die "缺 qrencode，先 brew install qrencode"
}

host="localhost"
if [[ $phone == 1 ]]; then
  host="$(lan_ip)"
  [[ -n "$host" ]] || die "找不到這台 Mac 的區網 IP（Wi-Fi 或有線網路有連上嗎？）"
  web_listens_all || die "網頁只聽本機，手機連不到；先執行 bash scripts/manual-env/up.sh --keep-data --phone"
fi
[[ $phone == 1 || $all == 1 ]] && need_qrencode
mkdir -p "$STATE_DIR"

backend="http://localhost:${BACKEND_PORT}"
auth="$(curl -sf -X POST "$backend/auth/login" -H 'Content-Type: application/json' \
  -d '{"tenantId":1,"loginId":"mg1","password":"demo1234"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])' 2>/dev/null)" \
  || die "以 mg1 登入 ${backend} 失敗：後端沒在跑，或還沒灌示範資料（bash scripts/manual-env/seed.sh）"

guest_url() {
  local body
  body="$(curl -sf "$backend/admin/rooms/$1/qr" -H "Authorization: Bearer $auth")" \
    || die "取不到 $1 房的 QR 代碼：房號不存在？"
  echo "http://${host}:${WEB_PORT}/g/$(python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])' <<<"$body")"
}

if [[ $all == 0 ]]; then
  url="$(guest_url "$room")"
  echo "$url"
  if [[ $phone == 1 ]]; then
    png="$STATE_DIR/qr-${room}.png"
    qrencode -o "$png" -s 10 "$url"
    echo "$png"
    open "$png"
  fi
  exit 0
fi

# 跟後端判斷「現在在住」同一條規則：訂單沒被取代，且入住時間 <= 現在 < 退房時間。
rooms="$(psql_manual -Atc "
  SELECT DISTINCT r.room_no
    FROM rooms r
    JOIN reservations_cache rc ON rc.tenant_id = r.tenant_id AND rc.room_id = r.id
   WHERE r.tenant_id = 1 AND rc.superseded_at IS NULL
     AND rc.checkin_at <= now() AND rc.checkout_at > now()
   ORDER BY r.room_no")" || die "查不到在住房：資料庫 ${DB_HOST}:${DB_PORT}/${DB_NAME} 連不上"
[[ -n "$rooms" ]] || die "現在沒有任何在住房；重灌示範資料：bash scripts/manual-env/seed.sh"

sheet="$STATE_DIR/qr-sheet.html"
cards=""
for r in $rooms; do
  url="$(guest_url "$r")"
  qrencode -o "$STATE_DIR/qr-${r}.png" -s 10 "$url"
  echo "${r}  ${url}"
  cards+="<figure><h2>${r}</h2><img src=\"qr-${r}.png\" alt=\"${r} 房 QR\"><figcaption><a href=\"${url}\">${url}</a></figcaption></figure>"
done

note="用手機相機掃碼即可開啟住客頁。"
[[ $phone == 1 ]] || note="這份是 localhost 網址，只能在這台 Mac 的瀏覽器點開；手機要掃請改跑 qr.sh --all --phone。"

cat > "$sheet.tmp" <<EOF
<!doctype html>
<html lang="zh-Hant">
<head>
<meta charset="utf-8">
<title>住客 QR 總表</title>
<style>
  body { font-family: -apple-system, "PingFang TC", sans-serif; margin: 24px; color: #111; background: #fff; }
  header p { margin: 4px 0; color: #444; }
  main { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 16px; margin-top: 16px; }
  figure { margin: 0; padding: 12px; border: 1px solid #ccc; border-radius: 8px; text-align: center; break-inside: avoid; }
  h2 { margin: 0 0 8px; font-size: 28px; }
  img { width: 180px; height: 180px; image-rendering: pixelated; }
  figcaption { font-size: 11px; word-break: break-all; }
  @media print { body { margin: 0; } a { color: #111; text-decoration: none; } }
</style>
</head>
<body>
<header>
<h1>住客 QR 總表（在住房）</h1>
<p>產生時間 $(date '+%Y-%m-%d %H:%M')。${note}</p>
<p>重灌示範資料（seed.sh、up.sh）後代碼會換新，這張就失效，要重新產生。</p>
</header>
<main>${cards}</main>
</body>
</html>
EOF
mv "$sheet.tmp" "$sheet"
log "總表：${sheet}"
open "$sheet"
