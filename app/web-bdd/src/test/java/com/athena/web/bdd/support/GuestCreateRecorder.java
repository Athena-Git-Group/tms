package com.athena.web.bdd.support;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 記下住客頁送出的每一個建立需求請求（內文與冪等鍵），需要時把後端的回覆壓幾秒再交給頁面。
 * 請求一律照原樣送到真的後端，限流也由後端自己判斷，這裡不代答。
 */
public final class GuestCreateRecorder {

    private static final long SLOW_REPLY_NANOS = Duration.ofSeconds(3).toNanos();

    private final WebWorld world;

    private final List<String> sentKeys = new ArrayList<>();

    private final List<SentCreate> creates = new ArrayList<>();

    private final Set<Page> slowPages = new HashSet<>();

    private final List<HeldReply> heldReplies = new ArrayList<>();

    public record SentCreate(Page page, String body) {
    }

    private record HeldReply(Route route, APIResponse response, long heldAtNanos) {
    }

    public GuestCreateRecorder(WebWorld world) {
        this.world = world;
    }

    void install() {
        world.onEveryNewContext(this::routeCreate);
        world.whileWaiting(this::releaseDueReplies);
    }

    public void slowRepliesOn(Page page) {
        slowPages.add(page);
    }

    public int heldReplies() {
        return heldReplies.size();
    }

    public void releaseDueReplies() {
        long now = System.nanoTime();
        List<HeldReply> due = heldReplies.stream().filter(held -> now - held.heldAtNanos() >= SLOW_REPLY_NANOS).toList();
        heldReplies.removeAll(due);
        due.forEach(GuestCreateRecorder::release);
    }

    void releaseAllReplies() {
        List<HeldReply> all = List.copyOf(heldReplies);
        heldReplies.clear();
        all.forEach(GuestCreateRecorder::release);
    }

    public List<String> sentKeys() {
        return List.copyOf(sentKeys);
    }

    public List<SentCreate> creates() {
        return List.copyOf(creates);
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
        }
    }
}
