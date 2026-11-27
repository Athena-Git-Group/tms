package com.athena.web.bdd.support;

import com.microsoft.playwright.Request;
import io.cucumber.java.Before;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PhotoRequests {

    public static final String CONTENT_PATH = "/api/v1/repair-photos/";

    public record Sent(String url, String authorization) {
    }

    private final WebWorld world;

    private final List<Request> requests = new CopyOnWriteArrayList<>();

    public PhotoRequests(WebWorld world) {
        this.world = world;
    }

    @Before(order = 0)
    public void recordFromFirstBrowserContext() {
        world.onEveryNewContext(context -> context.onRequest(this::record));
    }

    private void record(Request request) {
        if (request.url().contains(CONTENT_PATH)) {
            requests.add(request);
        }
    }

    public List<Sent> sent() {
        return requests.stream().map(request -> new Sent(request.url(), request.headerValue("authorization"))).toList();
    }
}
