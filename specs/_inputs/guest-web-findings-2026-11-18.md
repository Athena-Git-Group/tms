# Guest web: design-to-backend mapping and implementation brief

Goal restated. A hotel guest scans the room QR code, a phone web page opens, and the guest can ask for amenities (with quantity) and repairs (no photos in v1), follow progress, cancel, and rate (the backend supports rating). Scope is the main flow. AI chat, late checkout, luggage, and taxi show on the home page as 即將開放 and are not wired.

Sources read in this session:
- Backend: `tms/src/main/java/com/athena/tms/` GuestController, GuestStreamController, GuestRequestApiController, RoomQrController/Service, GuestScanService, GuestRequestService, GuestTenantConfigReader, TaskEngine (cancelByGuest, rate), BearerTokenAuthFilter, SecurityConfig, GuestScanSecurityConfig, IdempotencyFilter, StreamConnection/Subscriber/Registry, guest DTOs, `common/codes/*`, `common/error/*`.
- Design: `Guest.dc.html` (template, the `S` dictionary, and the logic class). The DS files it links (`ds/tms-web/tokens.css`, `components/bundle.css`) are not next to it. A copy exists at `/private/tmp/claude-501/-Users-Caresys-IdeaProjects-tms-mvp/a99534e0-edb3-4654-a2f3-6f79652617c6/scratchpad/artifact-files/project/ds/tms-web/` (another session's scratchpad, so it may disappear; copy it into the repo before you depend on it).
- Web: `app/web` (router, api/client, realtime/stream-client, i18n, index.css, vite.config, package.json), `app/web-bdd/run-feature.sh` + support, `app/bdd-support/BackendChannel.java`, `specs/truth/features/frontdesk-web/dsl.md`, `specs/plans/004-*/ui/ui-plan.md`, `tms/src/test/resources/features/01-guest-requests.feature`.

---

## 1. QR URL, route, tenant, token header

**What the backend puts in the QR.** It encodes only a token. `GET /admin/rooms/{roomNo}/qr` (manager) returns `{roomNo, token, version, issuedAt}`. `token` is 22 random base62 characters (`QrTokens.generate`). The token has no tenant prefix and no room number. No URL template exists anywhere in the backend, the specs, or the web app. I grepped `tms/src`, `specs`, `docs`, and `app`. The only precedent is the old POC `docs/13-poc/guest.html`, which read `?token=` from the page URL. **The QR URL format is undecided. We have to define it.**

**Recommended URL.** `https://<web origin>/g/<qrToken>`. Reasons:
- The deployment assumption is same origin (`specs/truth/techstack.md` line 31), and the backend already owns `/guest/**` (scan, home, requests, stream). An SPA route under `/guest/...` would collide with the API paths and with any dev proxy rule for `/guest`.
- `/g/...` cannot collide with a hotel code. Hotel codes are at least 3 characters (`V24__tenant_code.sql` CHECK), and `guest` is a reserved code.
- React Router ranks the static `g` segment above `/:hotelCode/*`, so the existing `ToBoard` catch-all will not swallow it.
- A short path keeps the QR less dense.

**How the tenant resolves.**
- On scan, `GuestScanService.resolveToken` loops over **every tenant** and looks the token up in each one. The client sends no hotel code.
- The scan returns a session token shaped `"<tenantId>.<base64url 32 bytes>"` (`GuestSessionTokens.issue`). After that, the tenant comes from the token's prefix (`parseTenantId`).

**Session token on later calls.** Send either of these headers. `BearerTokenAuthFilter` checks Authorization first:
- `Authorization: Bearer <sessionToken>`. If the token has a numeric tenant prefix it is treated as a guest token, otherwise as a staff JWT.
- `X-Guest-Session: <sessionToken>`.
- On failure it returns 401 `{code:"unauthorized", message:"session 無效、過期或已撤銷"}`. Causes are an unknown token, an expired one (`expiresAt` = reservation `checkoutAt`), or a revoked one. QR rotation (`/admin/rooms/{no}/qr/rotate`) revokes all of the room's sessions.

**SSE.** Native `EventSource` cannot send headers. The desk already reads its stream with `fetch` plus its own parser (`app/web/src/features/realtime/stream-client.ts`, `takeStreamEvents`). Reuse that for `/guest/stream` with the guest header.

**Dev proxy.** `vite.config.ts` proxies only `/api` and `/staff/stream`. Add `/guest` (scan, home, requests, stream) with the same `proxyRes close → res.destroy()` hook that `/staff/stream` uses. The SPA does not use `/guest` paths, so no conflict.

## 2. Backend guest endpoints

Errors always come back as `ApiError {code, message, details?, context?}` (`GlobalExceptionHandler`).

| Endpoint | Auth | Request | Response | Errors |
|---|---|---|---|---|
| `POST /guest/scan` | public (separate filter chain, `GuestScanSecurityConfig`) | `{token: string}` (trimmed) | `ScanResponse {sessionToken, expiresAt (ISO offset), roomNo, infoPages: InfoPage[], menu: MenuItem[]}` | 422 `validation_failed` (blank token). **409 `room_vacant`** for all three cases: unknown token, revoked token ("QR 無效或房間目前無住客"), and vacant room ("房間目前無住客"). 429 `rate_limited`: per-IP failure count over 10 min ≥ tenant `scan_fail_threshold` (default 10) |
| `GET /guest/home` | guest | none | `GuestHomeView {infoPages: [{key, title, body}], menu: [{key, label}]}` | 401 |
| `POST /guest/requests` | guest | `GuestRequestCreate {itemId?, quantity?, text?, lang?}`. **Exactly one** of `itemId` or `text` | 201 `GuestTaskView` | 422 `validation_failed` (both or neither). 429 `rate_limited` "同一房 10 分鐘內最多 3 筆需求…" (per room, per task) |
| `GET /guest/requests` | guest | none | `GuestTaskView[]`, all tasks of the session's **reservation**, newest first | 401 |
| `POST /guest/requests/{id}/cancel` | guest | none | `GuestTaskView` (state `cancelled`) | 404 `not_found` (not yours). **409 `state_conflict`** when the state is not `triage` or `pool` ("任務目前狀態不允許「收回」") |
| `POST /guest/requests/{id}/rating` | guest | `{rating: 1..5}` | `GuestTaskView` | 422 (null or out of range). 409 `state_conflict` unless state is `done` or `inspected` **and** rating is null. **`closed` cannot be rated** |
| `GET /guest/stream` | guest | header auth | SSE (see below) | 401 |
| `/api/v1/guest-requests` (POST, GET, `/{id}/cancel`) | guest | same as above | same | Duplicate surface. It has no home and no rating, so use `/guest/*` everywhere |

`GuestTaskView`:

```
{ id: uuid, type, state, dept, title, answer: string|null, rating: int|null, createdAt }
```

- `type`: `amenity | repair | cleaning | other`.
- `state`: `triage | pool | accepted | doing | done | inspected | closed | deferred | interrupted | cancelled`.
- `dept`: `housekeeping | engineering | frontdesk`. It is set by the menu item or by a classification rule, and may be null in triage (inferred, not checked).
- `title`: for a menu item with qty > 1 the backend formats `"<label> ×<qty>"`. For free text it is the text, cut to 120 characters plus "…".
- `answer`: the desk's on-the-spot reply.

Create semantics (`GuestRequestService`):
- `itemId` must be a `request_menu` key. An unknown key silently becomes free text. A known key creates the task in **`pool`** with the item's dept and type, and `quantity` defaults to 1.
- `text` goes through `classification_rules`. It lands in `pool` with a dept when a rule matches, otherwise in `triage`.
- `lang` is stored on the note.
- `request_menu` items are `{key, label, dept, type}`. If the tenant configured none, the factory menu is 衣架, 毛巾, 牙刷組, 礦泉水, 吹風機, all `housekeeping`/`amenity`, with **the Chinese label as the key**. Labels are single-language.

`Idempotency-Key` header is optional on any POST (`IdempotencyFilter`). It replays the stored response when the same key repeats and the tenant can be resolved from the request attribute set by the bearer filter. I did not check the filter order, so verify a replay once before relying on it.

**SSE `/guest/stream`.** Guest connections get only tasks whose `reservationId` matches the session's reservation. Event names and payloads:
- `init {stream_pos}`.
- `resync {reason:"connected", refetch:"/guest/home"}`. Treat it as "refetch requests and home".
- `task {stream_pos, task_id, seq, type, occurred_at}`. `type` is a TaskEventType: `created, triaged, triage_undone, assigned, claimed, started, completed, inspection_passed, inspection_failed, reassigned, returned_to_pool, interrupted, deferred, answered, rated, sop_checked, plan_adjusted, checkout_imported, cancelled`. **Guests get no card or state payload**, so refetch `GET /guest/requests` on every `task` event.
- `heartbeat {stream_pos}`.
- `degraded {reason}`. Guests receive it only for consumer lag. Fall back to polling.
- Guests never receive `recovered`. Leave polling when a heartbeat arrives.

## 3. Screens: what each shows, the backend field behind it, gaps

Guest-visible status (from the design's `badge()`, `stepIdx()`, `isActive()`, and the `deskIn()` MAP, which maps the backend enum the same way):

| backend `state` | badge (zh-TW) | badge class | progress step 0–3 | section | can cancel | locked note |
|---|---|---|---|---|---|---|
| `triage` | 已送出 (st0) | neutral | 0 | 進行中 | yes | no |
| `pool` | 已受理 (st1) | `tw-badge--accepted` | 1 | 進行中 | yes | no |
| `accepted`, `deferred` | 已受理 | accepted | 1 | 進行中 | no | yes (lockedNote) |
| `doing`, `interrupted` | 處理中 (st2) | `tw-badge--doing` | 2 | 進行中 | no | yes |
| `done`, `inspected`, `closed` | 已完成 (st3) | `tw-badge--done` | 3 | 已結束 | no | no |
| `cancelled` | 已取消 (stX) | neutral | track hidden | 已結束 | no | no |

The steps are 已送出 → 已受理 → 處理中 → 已完成.

| Screen | Shows (design) | Backend source | Gaps |
|---|---|---|---|
| Unavailable (`blk`) | Two variants. Vacant: 請洽櫃檯 + vacantB. Expired QR: 這個 QR Code 已失效 + expiredB. Both show the info row 櫃檯（24 小時）/房內電話撥 9 | scan 409 `room_vacant`. Session 401 after a rotation | **Backend cannot tell vacant from voided.** Same code for unknown, revoked, and vacant. Options: show the vacant copy for 409, or ask the backend for a `qr_revoked` code. 401 on a later call is the closest signal for "QR rotated" (show expired). 429 has no design screen. Reuse vacant layout with the rate message |
| Home header | Wordmark "Athena", room pill `311` with a room-status menu (MUR/DND), language pill, stay line `{room} 號房 · 11/10 – 11/12 · 2 晚`, greeting `早安，{張先生}` plus 今天需要什麼？ | `roomNo` and `expiresAt` from the scan only. `/guest/home` has no roomNo, so store it client-side with the token | **No guest name, check-in date, or hotel name** in any guest response. Fallback: generic greeting without a name, and stay line shows room plus checkout only. The room-status menu (請即打掃/請勿打擾) has no backend endpoint, so leave it out of v1 (not in the agreed scope) |
| Home Wi-Fi banner | `密碼 <b>athena2026</b>` + 複製 | `infoPages` has key `wifi` (bdd seed: title "Wi-Fi", body is free text "Wi-Fi 名稱 Athena-Guest，密碼是房號加姓氏拼音。") | **No structured SSID or password**, so the banner cannot be built faithfully. Per scope, skip it. The wifi page could appear in the guide list as plain text |
| Home ask bar | 問問看：製冰機在哪裡？ → AI chat | none | Out of scope. Render it disabled with 即將開放, or drop it |
| Home 我的需求 | Idle card 目前沒有進行中的需求 / 查看紀錄, or the newest active request: eyebrow 進行中的需求, title, 4-segment progress bar with labels, line text, 查看. Link 全部 {n} 項 | `GET /guest/requests`, filter active states | Line variants need data we lack. `lnPool={dept}已收到 · {createdAt} 送出` works (dept + createdAt). **`lnAcc` and `lnDoing` need the staff name and the start time**, which are not exposed. Use name-less copy (needs new strings, see §6) |
| Home hero cards | 毛巾、礦泉水 / 馬上送到房間 → supply. 東西壞了？/拍照就能報修 → repair | static | Hero copy says 拍照. Since v1 has no photos, change it (for example 告訴我們哪裡壞了). That is a copy decision |
| Home 服務 rows | 備品補充, 設備報修, 延遲退房, 行李寄放, 叫車 | static | The last three get a 即將開放 badge and no tap. 設備報修 subtitle "可以附照片" needs v1 copy |
| Home 住宿指南 | Category grid (早餐/餐廳/泳池/健身房/退房), each with illustration, hours, location, body, steps, video | `infoPages [{key,title,body}]` | Only title and body exist. No meta, facts, or images. Per scope, render a plain list of title → body detail, or skip |
| Home footer | 緊急狀況請用房內電話撥 9 找櫃檯 | static | none |
| Supply (數量) | Lead text, list rows (icon, name, hint `最多 {max}{unit}`), stepper per row, caution status on rate limit, action bar 送出 {n} 項 + note 還沒有同仁接手前，都可以自己取消 | `menu` filtered by `type==='amenity'` from scan/home. Submit = **one POST per item with qty > 0** (`{itemId:key, quantity}`) | **No per-item max, unit, or icon** in the menu. The design caps 6/4/4/2/1. Default max (for example 5), unit omitted, icon by type. Labels are not translated. The rate-limit body "這次還能送出 {n} 項" needs the remaining count, which the backend does not expose. Compute it client-side from own list `createdAt` within 10 min (approximate: the limiter is per room, not per reservation). A 429 mid-batch means partial success. Send sequentially, stop on 429, show success for what went through plus the caution status |
| Repair | 哪裡有問題？ chips (冷氣不冷, 電視無訊號, 燈具故障, 熱水不熱, 網路連不上, 其他), 補充說明 textarea (optional, **required for 其他**, max 200, counter, 可以用任何語言輸入), photos (out of v1), 送出報修 | The design chips are hardcoded. The backend has repair items only if the tenant's `request_menu` has `type:'repair'` entries. The factory menu has none | **(a) Chip + note cannot be sent together.** `itemId` XOR `text`, so 422 if both. **(b) Where the chips come from is undecided.** Options: chips from `menu` type `repair`, sent as `{itemId}`, and the note is dropped or blocked. Or send chip label + note as `text` so classification routes it (may land in triage). A backend change to accept `text` as a note with `itemId` is the clean fix. Needs a decision |
| Submit success (`sent`) | Green check, 需求已送出 / 已送出 {n} 項需求, 同仁接手後，進度會自動更新。, 查看進度 (one item → detail, more → list), 回首頁 | Created `GuestTaskView` ids | none |
| My requests | Sections 進行中 / 已結束, rows: icon, title, sub `{hh:mm} 送出 · {dept name}`, badge. Empty state 還沒有送出任何需求 + body. Footer 狀態會自動更新。退房後這份清單會一併清除。 | list: `title`, `createdAt`, `dept`, `state` | The empty body mentions 「客服」 (out of scope), so adjust the copy. Icon per menu item needs the menu key, which is not in the view. Use an icon by `type` |
| Progress (detail) | Header card: icon, badge, title (22px bold), meta `{hh:mm} 送出 · {dept}`. Status message (see next row). 4-step track with per-step meta. Rating card. Cancel button (gray-danger) + note, or locked note | `title, state, dept, createdAt, answer, rating` | **Step meta needs event times and the assignee name**: `tkRecv {time} · {dept}收到`, `tkDoing {time} · {who} 正在處理`, `tkTaken {who} 已接手`, done time. Only `createdAt` exists. Fallback: step 0 meta = created time. Later steps use the time-less copy (`tkWaitDesk`, `tkLater`, `tkDone`) or dept-only copy. A backend option is adding `assigneeName` plus `acceptedAt`/`startedAt`/`doneAt` |
| Detail status message | cancelled: 這個需求已取消 + (by desk: 櫃檯取消了這個需求，有疑問請撥 9 / by guest: 如果還需要，可以回首頁重新送出。). Reply: 櫃檯回覆 + reply. triage: 櫃檯正在看您的需求. done: 已完成 + `{who} 已處理完成。` or 已處理完成。 | `answer` → 櫃檯回覆. `state` | **`cancel_reason` is not exposed**, so desk cancel and guest withdraw look the same. A client-side "ids I cancelled" set can fill the gap only on this device. The backend option is exposing `cancelReason`. The done message uses `msgDoneB0` (no name). When `answer` is set, the design hides the track and the rating |
| Cancel confirm dialog | 取消這個需求？ / 「{title}」會從處理清單移除。 / 返回 / 取消需求 (destructive). Then toast 已取消需求. On 409 toast 同仁已經接手，無法取消。請撥 9 找櫃檯。 | `POST …/cancel`, 409 `state_conflict` | none. The backend rule (triage or pool) matches the design exactly |
| Rating | Shown when done, no rating, no reply. 5 stars (`--rating-star`), word labels 很不滿意…非常滿意, optional textarea 哪裡可以做得更好？/想補充的話, 送出評分. After: 謝謝您的評分 / 您給了 {n} 顆星。 (≤2 → 飯店主管會看到…) | `POST …/rating {rating}`. Show when `state ∈ {done, inspected}` and `rating == null` and `answer == null` | **No comment field.** The backend stores the rating only, so drop the textarea or add a backend field. A `closed` task (repair verified) cannot be rated (409). The design's `done` includes `closed`, so the client must hide rating for `closed` or the backend must allow it |
| Toast | Top, dark (`--toast-bg`) | local | none |
| Language | 5 languages: zh-Hant, en, ja, **ko**, vi. Auto-detected from the browser, with a toggle | none | The web app's i18n has zh-TW/en/ja/vi and no `ko`. Adding `ko` touches `LANGUAGES` (the desk would get it too) unless the guest area has its own language list |

## 4. Design values to copy (DS `tms-web` tokens; light shown, dark exists under `[data-theme="dark"]`)

| Token or element | Value |
|---|---|
| Palette B, page `--bg` | `#f2f2f7`. Surface `#ffffff`. Text `#000`. Secondary `#6c6c70`. Placeholder `#737378` |
| Brand | `--brand-primary #0c7680`, on-primary `#fff`, container `#c8e8ed`, on-container `#4c696d` / strong `#2f5357` |
| Warm accent (supply icons, caution status) | container `#ffdcc5` / on `#703700`. Accent `#944b00` |
| Status | success `#ddf3e4`/`#1a6b34`, error `#ffe4e1`/`#b42318`, neutral `#e5e5ea`/`#3c3c43`, other category `#fff1c2`/`#6b4e00` |
| Danger text (cancel) | `#d70015` on `--fill-button rgba(120,120,128,.12)` |
| Rating star | `#c46a00` |
| Illustration colors (hero/service tiles) | teal `#2a9aa5`, peach `#d4702f`, mint `#36945f`, sky `#5b8fdc`, lilac `#9a80d0`, sand `#a97f50`. Guide grid containers sky `#dfeafb`, lilac `#ece6f8`, sand `#f3e8da` |
| Bars, overlays | bar `rgba(249,249,249,.94)` + 20px blur, scrim `rgba(0,0,0,.32)`, toast `rgba(30,30,30,.92)`, shadow-card `0 1px 2px rgba(0,0,0,.04), 0 6px 20px rgba(0,0,0,.05)`, shadow-float (ask bar) `…0 10px 28px rgba(0,0,0,.09)` |
| Type | large-title 700 34/41. title1 700 28/34 (result pages). title2 700 22/28 (section h2, detail title). title3 600 20/26 (dialog, empty). headline 600 17/24 (buttons, row names). body 400 17/24. callout 400 16/22. subheadline 400 15/21 (stay line, detail meta, status body). footnote 400 13/18 (labels, notes). caption 400 12/16 (timestamps, counters). Greeting display drops to 28px when the longest line is over 10em |
| Radii | sm 10, md 14 (lists, inputs), card 16 (status, toast), lg 20 (dialogs, sheets), xl 24 (hero, service, guide tiles), pill 9999 (all buttons, badges, steppers, language pill) |
| Spacing | 4 / 8 / 12 (button gap) / 16 (page gutter + safe-area, card padding) / 24 (section gap) / 32 / 48 (result icon top) |
| Sizes | touch 44, btn-lg 54 (bottom action bar), btn-md 44, btn-sm 34, icon 24, status icon 26, **content max-width 480 centered** |
| z-index | bar 20, overlay 40, toast 50 |
| Layout | Single column. Home is one page with **no bottom tab bar**. Sub-pages have a sticky header with back at top-left, title, and `{hotel} · 房號 {room}`. Order: Wi-Fi promo strip → top row (wordmark, room pill, language pill) → stay line + greeting → ask bar → 我的需求 card → 2 hero cards → 服務 rows → 住宿指南 grid → emergency note. Supply and repair end in an action bar pinned to the bottom (54px filled pill). Success and unavailable screens are centered empty-states |

Component class names used (from `bundle.css`): `tw-page`, `tw-card`, `tw-list`, `tw-li`, `tw-badge(--accepted|--doing|--done)`, `tw-status(--caution|--success|--neutral)`, `tw-track`, `tw-stepper`, `tw-chip(s)`, `tw-field`, `tw-actionbar`, `tw-btn(--filled|--plain|--gray-danger|--tinted|--lg|--block)`, `tw-empty(--caution|--success)`, `tw-pcard`, `tw-rcard`, `tw-hcard`, `tw-progress`, `tw-rating`, `tw-header`, `tw-promo`, `tw-pill`, `tw-lang`, `tw-menu`, `tw-toast`. Port these into the guest area's Tailwind theme or a scoped CSS file. Note that the desk's `index.css` `@theme` is a different, desk-sized token set (`--radius-card 14px`, min shell 1180px).

## 5. Web app conventions and recommendation

**Recommendation: same app (`app/web`), new route tree `/g/:qrToken` (+ `/g/...` sub-pages), its own shell, loaded lazily.** Reasons:
- One build, one dev server, one deployment origin (matches the same-origin assumption).
- Reuses the `fetch`-based SSE parser and backoff (`features/realtime/stream-client.ts`, `live-connection.ts`), `ApiError`/`NetworkError`, `newIdempotencyKey` (with its non-https fallback, which guests on hotel Wi-Fi may need), i18next setup, `browser-storage.ts`, and the web-bdd harness (`run-feature.sh` already starts the backend and Vite).
- Lazy loading (`React.lazy` / route `lazy`) keeps desk code (dnd-kit etc.) out of the guest bundle on phones.

What has to differ, and where:
- **API client.** `api/client.ts` hardcodes `API_PREFIX='/api/v1'` and staff `authHeaders()` from `useSessionStore`. Add a small guest client (`src/guest/api.ts`) on the same `send` core with prefix `/guest` and header `X-Guest-Session` from a guest store. Keep the guest token separate from the staff session store so a desk login on a shared device never mixes with it.
- **Token storage.** The staff session uses `sessionStorage`, and techstack forbids `localStorage` for desk credentials. For the guest, keep the session token in `sessionStorage` and keep the QR token in the URL. A new tab or a lost session re-scans from `/g/<qrToken>` (each scan creates a new `guest_sessions` row, which is cheap). After a scan, `replaceState` to `/g/<qrToken>` stays as-is so reload works.
- **Layout and tokens.** The desk shell enforces `--min-width-shell: 1180px`. The guest shell must be mobile (max 480 centered). Put guest tokens in a scoped stylesheet or a second `@theme` namespace to avoid clashing with desk token names (`--color-bg` etc. already exist with the same values, `--radius-card` differs: desk 14, guest 16).
- **i18n.** Pattern: `src/i18n/locales/<lang>.ts` exports a nested object typed via `i18next.d.ts`, and `locales.test.ts` checks key parity. Add a `guest` namespace (key subtree) in each locale. Language detection for guests comes from `navigator.language` (design `detectLang`: zh* → zh, ja, ko, vi, else en). The desk's `interface-language.ts` follows the staff session, so the guest area needs its own initial language + toggle. Decide about `ko` (§3).
- **Router.** Add `{ path: '/g/:qrToken', lazy: … }` before `withoutHotelCode`. A static first segment outranks `/:hotelCode/*`, and hotel codes are 3+ characters, so `g` is safe.

Tests and commands (run from `app/web` unless noted):
- **Unit tests: vitest**, `environment: 'node'`, `include: src/**/*.test.ts`. Only pure logic (no DOM, no component tests). Pattern: logic in `src/lib/*.ts` + sibling `*.test.ts`. Run `npm test` (= `vitest run`).
- **Typecheck:** `npm run typecheck` (= `tsc --noEmit`, TS 7, strict + `noUncheckedIndexedAccess`). `npm run build` also runs tsc.
- **Lint: none configured.** No eslint, biome, or oxlint in `package.json` or `node_modules/.bin`. There is no lint command to run.
- **Acceptance (web-bdd):** Cucumber-JVM 7.22 + Playwright for Java 1.65, Maven module `app/web-bdd`, glue `com.athena.web.bdd.{steps/<module>, pages, support}`, runner `RunFrontdeskWebFeaturesTest`.
  - Features live in **`specs/truth/features/frontdesk-web/<模組>/*.feature`** with a shared `dsl.md` plus a per-module `dsl.md`. The runner and script are hardwired to `frontdesk-web/`. Guest features need either a sibling root (for example `specs/truth/features/guest-web/`) plus a parameterized features root in `run-feature.sh` and a second runner, or a module folder under `frontdesk-web/` (misnamed).
  - Profiles are `desktop` (Chromium 1280×720) and `ipad` (WebKit 1180×820). **A phone profile does not exist yet.** Add one (for example iPhone WebKit 393×852 touch) in `BrowserProfiles`/`WebProfile`.
  - Data prep: `WebWorld` resets business data at the start of each scenario and seeds the default hotel `athena`, staff, rooms, and info pages via `app/bdd-support` `BackendChannel`. It already has `createReservation`, `insertTodayReservation`, `guestSubmitMenuItem`, `guestSubmitRequest`, `guestWithdrawRequest`, `guestRequests` (desk-issued `X-Guest-Session`), `setTenantConfig(tenantId, "request_menu", …)`, and `createTask` situations. **Missing:** a helper to get or rotate a room's QR token (call `GET /admin/rooms/{no}/qr` as the manager `M0001`, or insert into `room_qr_tokens`) so a step can open `/g/<token>`.
  - Command (repo root): `bash app/web-bdd/run-feature.sh [--keep] [<feature path relative to frontdesk-web>] [desktop|ipad]`, and `--stop` to tear down. Install browsers once: `mvn -q -f app/web-bdd/pom.xml exec:java -Dexec.args="install chromium webkit"`.
  - Infra: JDK 25, node ≥20.19, mvn, Postgres at `localhost:5433/tms` and Kafka at `localhost:9092` (`docker compose up -d` at the repo root, which the script does not start). Backend on **8080**, built from `tms/target/tms-0.0.1-SNAPSHOT.jar` and started with the Spring profile `test-support` (`SPRING_PROFILES_ACTIVE=test-support`), which registers the test-only clock, rate-limit reset and session-revoke endpoints. Vite on `WEB_PORT` (default 5199). The script locks per checkout and wipes the DB each scenario. In a worktree, set `MAVEN_ARGS=-Dmaven.repo.local=…` (the script warns about this). The manual-test environment (8083 / `tms_manual`, web 5300, `scripts/manual-env/`) is separate (from memory notes, not re-read).
- **Backend guest cucumber:** `tms/src/test/resources/features/01-guest-requests.feature` (scan rules, session, info pages, routing, rate limit, list by reservation, cancel only before claim, rating, IP limit, desk-issued session), `06-qr-lifecycle.feature`, `14-sse-stream.feature`.
- **Spec convention (004):** `specs/plans/NNN-<slug>/` holds `spec.md`, `checklists/`, `research.md`, `plan.md`, `ui/ui-plan.md` + static prototype (`ui/*.html`, `desk.css`, `desk.js`), `features/acceptance/*.feature`, `tasks.md`, `truth-delta.md`, `implement-notes.md`. The pipeline skills are specify → (clarify) → technical-research → system-analysis → api-plan / data-plan / ui-plan → spec-by-example → dsl-refine → tasks → implement/bdd. The ui-plan style quotes on-screen text in 「」, values in `code`, and lists differences from the prototype explicitly.

## 6. Recommended data shape

```ts
// Wire types (parse once at the boundary; reject unknown enum values to the "unknown" bucket).
type TaskState = 'triage'|'pool'|'accepted'|'doing'|'done'|'inspected'|'closed'|'deferred'|'interrupted'|'cancelled';
type TaskType  = 'amenity'|'repair'|'cleaning'|'other';
type Dept      = 'housekeeping'|'engineering'|'frontdesk';
type GuestTask = { id: string; type: TaskType; state: TaskState; dept: Dept|null; title: string;
                   answer: string|null; rating: number|null; createdAt: string };
type MenuItem  = { key: string; label: string };          // type is NOT on the wire (see gap)
type InfoPage  = { key: string; title: string|null; body: string|null };
type ScanResult = { sessionToken: string; expiresAt: string; roomNo: string; infoPages: InfoPage[]; menu: MenuItem[] };
type GuestEntry =                                          // what the /g/:qrToken loader resolves to
  | { kind: 'ready'; session: ScanResult }
  | { kind: 'vacant' }            // 409 room_vacant (also covers voided/unknown token)
  | { kind: 'expired' }           // 401 on a later call after rotation
  | { kind: 'throttled' }         // 429 on scan
  | { kind: 'offline' };
```

Gap inside the shape. `GuestMenuItemView` exposes only `{key, label}`. **`type` and `dept` are dropped**, so the client **cannot split the menu into amenity vs repair**. Ask the backend to add `type` (one-line DTO change), or keep a client allowlist of keys. That is fragile, because factory keys are Chinese labels.

One table keyed by status drives the badge, track, sections, and actions (Model the Domain: one record per state instead of branching in each component):

```ts
type GuestStage = 'sent'|'received'|'working'|'finished'|'cancelled';
const STAGE_OF: Record<TaskState, GuestStage> = {
  triage: 'sent', pool: 'received', accepted: 'received', deferred: 'received',
  doing: 'working', interrupted: 'working',
  done: 'finished', inspected: 'finished', closed: 'finished', cancelled: 'cancelled',
};
const STAGE: Record<GuestStage, { badgeKey: string; badgeTone: 'neutral'|'accepted'|'doing'|'done';
                                  step: 0|1|2|3|null; active: boolean }> = {
  sent:      { badgeKey: 'guest.status.sent',      badgeTone: 'neutral',  step: 0,    active: true  },
  received:  { badgeKey: 'guest.status.received',  badgeTone: 'accepted', step: 1,    active: true  },
  working:   { badgeKey: 'guest.status.working',   badgeTone: 'doing',    step: 2,    active: true  },
  finished:  { badgeKey: 'guest.status.finished',  badgeTone: 'done',     step: 3,    active: false },
  cancelled: { badgeKey: 'guest.status.cancelled', badgeTone: 'neutral',  step: null, active: false },
};
// Per-state facts that follow backend guards, not stage:
const canCancel = (s: TaskState) => s === 'triage' || s === 'pool';          // TaskEngine.cancelByGuest
const canRate   = (t: GuestTask) => (t.state === 'done' || t.state === 'inspected') && t.rating === null && t.answer === null; // TaskEngine.rate + design rt.ask
const isLocked  = (s: TaskState) => STAGE[STAGE_OF[s]].active && !canCancel(s);  // accepted/deferred/doing/interrupted
```

Detail status message, as a pure function `statusMessage(task)` in priority order: cancelled → reply (`answer`) → triage → finished → none. This is the design's order. Put `STAGE_OF`, `canCancel`, `canRate`, `statusMessage`, `progressLine`, and `remainingQuota(tasks, now)` in `src/guest/lib/*.ts` with vitest tests. That is where the unit-test convention applies.

Copy that needs new strings because the backend lacks names and times (no-name variants):
- `lnAcc` → 「{dept}已接手，馬上過去」.
- `lnDoing` → 「{dept}正在處理」.
- `tkTaken` and `tkDoing` → dept-only versions.
- `msgDoneB0` already exists.

## Decisions needed (product or backend)

1. **QR URL format.** Recommend `/g/<token>`. Printing must use it.
2. **Repair chips and note.** Backend `itemId`+`text` together vs free text vs menu-only.
3. **Expose on `GuestTaskView`:** `cancelReason`, `assigneeName`, step timestamps. Expose `type` on the menu item. Or accept the reduced copy.
4. **Distinct scan code for a revoked QR** vs a vacant room.
5. **Rating:** a comment field, and whether `closed` can be rated.
6. **Guest name, hotel name, and stay dates** for the home header. Ship a generic greeting otherwise.
7. **Korean** in the language list.
8. **Wi-Fi banner and guide.** The data is unstructured. Recommend skipping the banner in v1 and showing info pages as a plain list.
