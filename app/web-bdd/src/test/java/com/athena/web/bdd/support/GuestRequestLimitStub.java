package com.athena.web.bdd.support;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 住客場景裡「同一間房 10 分鐘內最多 3 筆需求」的替身：住客送出需求時，由測試依後端資料判斷要不要回 {@code 429}。
 *
 * <p>後端真的限流器是記憶體裡的計數，照系統時鐘走，測試沒有辦法清掉或撥動。場景撥了後端時鐘、
 * 或用測試資料直接寫進去的舊需求，它都看不到；而經畫面真的送滿 3 筆，10 分鐘內那間房就再也送不出去。
 * 所以每一筆送出先在這裡攔下：這間房在後端現在往前 10 分鐘內建立的單（不分來源、含已取消）已滿 3 張，
 * 就直接回 {@code 429 rate_limited}，請求不到後端；否則才放行。
 *
 * <p>限制：每間房每個場景最多放行 3 筆。真的限流器不跟著後端時鐘，場景把後端時鐘往後撥超過 10 分鐘時，
 * 替身會判斷額度已經空出來，真的限流器卻仍然擋下。所以第 4 筆不送出，並在那一步結束時讓場景失敗。
 *
 * <p>「後端回覆很慢」也在這裡做：請求照常送到後端，後端的回應先扣在測試這一頭，扣滿 3 秒才交給畫面。
 * 攔截的處理程式不能自己睡著等，放行由步驟輪詢等待時做（{@link WebWorld#whileWaiting(Runnable)}），
 * 場景結束時還扣著的一律放行。
 */
public final class GuestRequestLimitStub {

    private static final int REQUESTS_PER_WINDOW = 3;

    private static final Duration WINDOW = Duration.ofMinutes(10);

    private static final long SLOW_REPLY_NANOS = Duration.ofSeconds(3).toNanos();

    private static final String RATE_LIMITED_BODY =
            "{\"code\":\"rate_limited\",\"message\":\"同一房 10 分鐘內最多 3 筆需求，請洽櫃檯協助\"}";

    private final WebWorld world;

    private final Map<UUID, Integer> forwardedByRoom = new HashMap<>();

    private final List<String> sentKeys = new ArrayList<>();

    private final List<SentCreate> creates = new ArrayList<>();

    private final Set<Page> slowPages = new HashSet<>();

    private final List<HeldReply> heldReplies = new ArrayList<>();

    private String overBudget;

    /** 畫面送出的一筆建立需求的請求：哪個分頁送的、請求內容（JSON 字串，沒有內容時是空字串）。 */
    public record SentCreate(Page page, String body) {
    }

    private record HeldReply(Route route, APIResponse response, long heldAtNanos) {
    }

    public GuestRequestLimitStub(WebWorld world) {
        this.world = world;
    }

    /** 這個場景接著開的每個瀏覽器環境都裝上替身；要在還沒開任何分頁時呼叫。 */
    void install() {
        world.onEveryNewContext(this::routeCreate);
        world.whileWaiting(this::releaseDueReplies);
    }

    /** 這個分頁之後的建立需求，後端的回應扣 3 秒才交給畫面。 */
    public void slowRepliesOn(Page page) {
        slowPages.add(page);
    }

    /** 還扣著、沒交給畫面的回應有幾個。 */
    public int heldReplies() {
        return heldReplies.size();
    }

    /**
     * 放行已經扣滿 3 秒的回應。先從清單拿掉再放行：放行時畫面可能馬上送出下一張，替身在同一條執行緒上接到它、
     * 又往清單裡加。
     */
    public void releaseDueReplies() {
        long now = System.nanoTime();
        List<HeldReply> due = heldReplies.stream().filter(held -> now - held.heldAtNanos() >= SLOW_REPLY_NANOS).toList();
        heldReplies.removeAll(due);
        due.forEach(GuestRequestLimitStub::release);
    }

    /** 不論扣了多久，全部放行；場景結束時用，分頁已經關掉的略過。 */
    void releaseAllReplies() {
        List<HeldReply> all = List.copyOf(heldReplies);
        heldReplies.clear();
        all.forEach(GuestRequestLimitStub::release);
    }

    /** 這個場景放行到後端的每一筆送出所帶的 {@code Idempotency-Key}，依先後。 */
    public List<String> sentKeys() {
        return List.copyOf(sentKeys);
    }

    /** 這個場景各分頁送出的每一筆建立需求的請求，不論有沒有放行到後端，依先後。 */
    public List<SentCreate> creates() {
        return List.copyOf(creates);
    }

    /** 有某間房放行超過 3 筆時讓場景失敗。 */
    void requireWithinBudget() {
        if (overBudget != null) {
            throw new AssertionError(overBudget);
        }
    }

    private void routeCreate(BrowserContext context) {
        context.route(Network.GUEST_CREATE::matchesPath, this::handle);
    }

    private void handle(Route route) {
        Request request = route.request();
        if (!Network.GUEST_CREATE.method().equals(request.method())) {
            route.fallback();
            return;
        }
        String body = request.postData();
        creates.add(new SentCreate(request.frame().page(), body == null ? "" : body));
        Optional<UUID> room = roomOf(request.headerValue("X-Guest-Session"));
        if (room.isEmpty()) {
            // 沒帶或帶了認不得的工作階段時，讓後端照它自己的規則回 401。
            route.resume();
            return;
        }
        if (createdWithinWindow(room.get()) >= REQUESTS_PER_WINDOW) {
            route.fulfill(new Route.FulfillOptions()
                    .setStatus(429)
                    .setContentType("application/json")
                    .setBody(RATE_LIMITED_BODY));
            return;
        }
        int forwarded = forwardedByRoom.merge(room.get(), 1, Integer::sum);
        if (forwarded > REQUESTS_PER_WINDOW) {
            overBudget = "超過真的限流器每間房 3 筆的預算，這個案例要改用另一間房";
            route.abort("failed");
            return;
        }
        String key = request.headerValue("Idempotency-Key");
        if (key != null) {
            sentKeys.add(key);
        }
        if (slowPages.contains(request.frame().page())) {
            heldReplies.add(new HeldReply(route, route.fetch(), System.nanoTime()));
            return;
        }
        route.resume();
    }

    private static void release(HeldReply held) {
        try {
            held.route().fulfill(new Route.FulfillOptions().setResponse(held.response()));
        } catch (com.microsoft.playwright.PlaywrightException pageGone) {
            // 分頁已經關掉，沒有畫面可以交了。
        }
    }

    private Optional<UUID> roomOf(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return Optional.empty();
        }
        return world.backend().queryRows("SELECT room_id FROM guest_sessions WHERE session_token = ?", sessionToken)
                .stream()
                .findFirst()
                .map(row -> (UUID) row.get("room_id"));
    }

    private long createdWithinWindow(UUID roomId) {
        OffsetDateTime now = world.backend().backendNow();
        Object count = world.backend().queryRows("""
                SELECT count(*) AS n FROM tasks
                 WHERE room_id = ? AND created_at > ? AND created_at <= ?
                """, roomId, now.minus(WINDOW), now).get(0).get("n");
        return ((Number) count).longValue();
    }
}
