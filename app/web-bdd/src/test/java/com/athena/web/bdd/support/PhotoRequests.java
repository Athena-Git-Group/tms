package com.athena.web.bdd.support;

import com.microsoft.playwright.Request;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 記下頁面送出的每一個讀照片請求；Network 的請求紀錄不帶標頭。 */
public final class PhotoRequests {

    public static final String CONTENT_PATH = "/api/v1/repair-photos/";

    public record Sent(String url, String authorization) {
    }

    private final List<Request> requests = new CopyOnWriteArrayList<>();

    public PhotoRequests(WebWorld world) {
        world.onEveryNewContext(context -> context.onRequest(this::record));
        if (world.hasPage()) {
            world.page().context().onRequest(this::record);
        }
    }

    // 事件處理裡不能讀標頭：讀完整標頭要再跟瀏覽器來回一次，在事件處理裡等它會卡死，所以先存下來，斷言時才讀。
    private void record(Request request) {
        if (request.url().contains(CONTENT_PATH)) {
            requests.add(request);
        }
    }

    public List<Sent> sent() {
        return requests.stream().map(request -> new Sent(request.url(), request.headerValue("authorization"))).toList();
    }
}
