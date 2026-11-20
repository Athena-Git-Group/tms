package com.athena.web.bdd.support;

import io.cucumber.java.Before;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 後端眼中「這次住宿」的需求：某間房目前那筆訂房名下的任務，以及它們依「需求的狀況對照」
 * （{@code specs/truth/features/guest-web/dsl.md}）該算哪一種狀況、住客頁該顯示哪個標籤。
 *
 * <p>凡是寫「後端」的結果句都從這裡讀權威狀態。另外在前置走完的那一刻記下所有訂房名下任務的內容摘要，
 * 給「後端這次住宿多了以下需求」「沒有多任何一張需求」和 When 之前比。
 */
public final class GuestStay {

    /** 後端一張住客需求裡，判斷狀況與排序要用到的欄位。 */
    public record Request(UUID id, String title, String state, String dept, String answer, String inspection,
            String cancelReason, Integer rating, OffsetDateTime createdAt) {

        /** 這張需求在「需求的狀況對照」裡的說法，例如「房務待認領」。 */
        public String situation() {
            return switch (state) {
                case "triage" -> "待派單";
                case "pool" -> "engineering".equals(dept) ? "工務待認領" : "房務待認領";
                case "accepted" -> "engineering".equals(dept) ? "工務已接" : "房務已接";
                case "deferred" -> "延後中";
                case "doing" -> "進行中";
                case "interrupted" -> "中斷";
                case "done" -> answer != null ? "已回答" : "已完成";
                case "inspected" -> "查房通過";
                case "closed" -> "已結案";
                case "cancelled" -> "guest_withdrew".equals(cancelReason) ? "已取消（住客收回）" : "已取消（櫃檯取消）";
                default -> throw new IllegalStateException("「需求的狀況對照」沒有後端狀態 " + state);
            };
        }

        /** 住客頁上的狀態標籤（繁體中文）。 */
        public String tag() {
            return switch (state) {
                case "triage" -> "已送出";
                case "pool", "accepted", "deferred" -> "已受理";
                case "doing", "interrupted" -> "處理中";
                case "done", "inspected", "closed" -> "已完成";
                case "cancelled" -> "已取消";
                default -> throw new IllegalStateException("「需求的狀況對照」沒有後端狀態 " + state);
            };
        }

        /** 還在進行中（清單的「進行中」段落），不是已結束。 */
        public boolean active() {
            return !List.of("done", "inspected", "closed", "cancelled").contains(state);
        }
    }

    private static final String REQUEST_COLUMNS = """
            id, title, state, dept, answer, inspection, cancel_reason, rating, created_at
            """;

    private final WebWorld world;

    private final GuestScenario scenario;

    private final WhenStart whenStart;

    private Map<UUID, String> digestsBeforeWhen;

    private RuntimeException captureFailure;

    public GuestStay(WebWorld world, GuestScenario scenario, WhenStart whenStart) {
        this.world = world;
        this.scenario = scenario;
        this.whenStart = whenStart;
    }

    @Before(order = 2)
    public void captureBeforeWhen() {
        whenStart.alsoCapture(() -> {
            if (!scenario.active()) {
                return;
            }
            try {
                digestsBeforeWhen = digestsNow();
            } catch (RuntimeException e) {
                captureFailure = e;
            }
        });
    }

    /** 這間房現在那筆有效訂房的 id；沒有人住時是空的。 */
    public Optional<UUID> reservationId(String roomNo) {
        return world.backend().queryRows("""
                SELECT r.id FROM reservations_cache r JOIN rooms m ON m.id = r.room_id
                 WHERE r.tenant_id = ? AND m.room_no = ? AND r.superseded_at IS NULL
                   AND r.checkin_at <= ? AND r.checkout_at > ?
                """, world.defaults().tenantId(), roomNo, world.backend().backendNow(), world.backend().backendNow())
                .stream()
                .findFirst()
                .map(row -> (UUID) row.get("id"));
    }

    /** 這間房這次住宿名下的需求，依送出時間由舊到新。 */
    public List<Request> requests(String roomNo) {
        UUID reservation = reservationId(roomNo)
                .orElseThrow(() -> new AssertionError(roomNo + " 房現在沒有人住，沒有「這次住宿」"));
        return world.backend().queryRows("SELECT " + REQUEST_COLUMNS
                        + " FROM tasks WHERE tenant_id = ? AND reservation_id = ? ORDER BY created_at, id",
                        world.defaults().tenantId(), reservation)
                .stream()
                .map(GuestStay::toRequest)
                .toList();
    }

    /** 某一張需求現在的樣子。 */
    public Request request(UUID taskId) {
        return world.backend().queryRows("SELECT " + REQUEST_COLUMNS + " FROM tasks WHERE tenant_id = ? AND id = ?",
                        world.defaults().tenantId(), taskId)
                .stream()
                .findFirst()
                .map(GuestStay::toRequest)
                .orElseThrow(() -> new AssertionError("後端沒有這張需求：" + taskId));
    }

    /** 這次住宿裡標題是這個、送出時間最新的那一張，給經畫面真的送出之後找回剛建立的需求用。 */
    public Request newest(String roomNo, String title) {
        return requests(roomNo).stream()
                .filter(request -> request.title().equals(title.strip()))
                .reduce((older, newer) -> newer)
                .orElseThrow(() -> new AssertionError("後端" + roomNo + " 房這次住宿沒有標題是「" + title + "」的需求，現在有 "
                        + requests(roomNo).stream().map(Request::title).toList()));
    }

    /** 這間房這次住宿裡，前置走完之後才出現的需求。 */
    public List<Request> addedSinceWhen(String roomNo) {
        Map<UUID, String> before = beforeWhen();
        return requests(roomNo).stream().filter(request -> !before.containsKey(request.id())).toList();
    }

    /** 這間房這次住宿的需求，張數與每一張的內容是不是都和前置走完時一樣；不一樣時回差異的說明。 */
    public Optional<String> changedSinceWhen(String roomNo) {
        Map<UUID, String> before = beforeWhen();
        UUID reservation = reservationId(roomNo).orElseThrow();
        Map<UUID, String> beforeOfStay = new LinkedHashMap<>();
        Map<UUID, String> now = new LinkedHashMap<>();
        digestsNow().forEach((id, digest) -> {
            if (digest.startsWith(reservation + "|")) {
                now.put(id, digest);
            }
        });
        before.forEach((id, digest) -> {
            if (digest.startsWith(reservation + "|")) {
                beforeOfStay.put(id, digest);
            }
        });
        if (beforeOfStay.equals(now)) {
            return Optional.empty();
        }
        return Optional.of("When 之前 " + beforeOfStay.size() + " 張、現在 " + now.size() + " 張；現在是 "
                + requests(roomNo).stream().map(r -> r.title() + "（" + r.situation() + "）").toList());
    }

    /**
     * 這張需求是不是帶著住客送出的冪等鍵建立的：後端記住了其中一把鍵的回應，回應裡就是這張需求。
     *
     * @param sentKeys 這個場景放行到後端的 {@code Idempotency-Key}
     */
    public boolean createdWithOneOf(UUID taskId, List<String> sentKeys) {
        if (sentKeys.isEmpty()) {
            return false;
        }
        String placeholders = String.join(",", sentKeys.stream().map(key -> "?").toList());
        Object[] params = new Object[sentKeys.size() + 2];
        params[0] = world.defaults().tenantId();
        for (int i = 0; i < sentKeys.size(); i++) {
            params[i + 1] = sentKeys.get(i);
        }
        params[params.length - 1] = "%" + taskId + "%";
        return !world.backend().queryRows("SELECT op_id FROM processed_ops WHERE tenant_id = ? AND op_id IN ("
                + placeholders + ") AND result_ref::text LIKE ?", params).isEmpty();
    }

    private Map<UUID, String> beforeWhen() {
        if (captureFailure != null) {
            throw new AssertionError("前置走完時沒記下後端的需求，無法和 When 之前比較：" + captureFailure, captureFailure);
        }
        if (digestsBeforeWhen == null) {
            throw new AssertionError("這個場景沒有記到 When 之前的需求：前置之後沒有任何「當」或「那麼」步驟");
        }
        return digestsBeforeWhen;
    }

    /** 所有掛在訂房上的任務：id → 「訂房 id|整列內容的摘要」。 */
    private Map<UUID, String> digestsNow() {
        Map<UUID, String> digests = new LinkedHashMap<>();
        for (Map<String, Object> row : world.backend().queryRows("""
                SELECT t.id, t.reservation_id::text || '|' || md5(t::text) AS digest
                  FROM tasks t WHERE t.tenant_id = ? AND t.reservation_id IS NOT NULL
                """, world.defaults().tenantId())) {
            digests.put((UUID) row.get("id"), (String) row.get("digest"));
        }
        return digests;
    }

    private static Request toRequest(Map<String, Object> row) {
        Object created = row.get("created_at");
        OffsetDateTime createdAt = created instanceof OffsetDateTime odt ? odt
                : ((java.sql.Timestamp) created).toInstant().atOffset(OffsetDateTime.now().getOffset());
        Object rating = row.get("rating");
        return new Request((UUID) row.get("id"), (String) row.get("title"), (String) row.get("state"),
                (String) row.get("dept"), (String) row.get("answer"), (String) row.get("inspection"),
                (String) row.get("cancel_reason"), rating == null ? null : ((Number) rating).intValue(), createdAt);
    }
}
