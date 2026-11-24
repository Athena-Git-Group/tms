package com.athena.web.bdd.support;

import com.athena.bddsupport.BackendChannel;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Clock;
import com.microsoft.playwright.Page;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class Clocks {

    public static final LocalDate DEFAULT_DATE = LocalDate.of(2026, 10, 26);

    public static final LocalTime DEFAULT_TIME = LocalTime.of(14, 10);

    private final BackendChannel backend;

    private final List<BrowserContext> contexts = new ArrayList<>();

    private OffsetDateTime now = at(DEFAULT_DATE, DEFAULT_TIME);

    private Page pausedPage;

    Clocks(BackendChannel backend) {
        this.backend = backend;
    }

    public static OffsetDateTime at(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    public OffsetDateTime now() {
        return now;
    }

    public LocalDate today() {
        return now.toLocalDate();
    }

    public void moveTo(OffsetDateTime target) {
        backend.setBackendClock(target);
        long targetMillis = target.toInstant().toEpochMilli();
        for (BrowserContext context : contexts) {
            long behind = targetMillis - browserMillis(context);
            if (behind > 0) {
                context.clock().fastForward(behind);
            } else if (behind < 0) {
                context.clock().setSystemTime(targetMillis);
            }
        }
        now = target;
    }

    public void moveToInSteps(OffsetDateTime target, Duration step, Runnable afterEachStep) {
        backend.setBackendClock(target);
        long targetMillis = target.toInstant().toEpochMilli();
        for (BrowserContext context : contexts) {
            long behind = targetMillis - browserMillis(context);
            if (behind < 0) {
                context.clock().setSystemTime(targetMillis);
            }
            while (behind > 0) {
                context.clock().fastForward(Math.min(behind, step.toMillis()));
                afterEachStep.run();
                behind = targetMillis - browserMillis(context);
            }
        }
        now = target;
    }

    /**
     * 推進這個分頁的瀏覽器時鐘，讓畫面自己的計時器照時間到期；分段推是因為一次推完時，週期性的計時器只會觸發一次。
     * 推完把後端撥到瀏覽器推到的那一刻：瀏覽器時鐘從裝上起照真實速度在走，比後端快了場景跑過的秒數，
     * 若改用 {@link #moveTo(OffsetDateTime)} 以後端時刻為準，瀏覽器實際推進的會比要求的少。
     */
    public void elapse(Page page, Duration total, Duration step) {
        for (long left = total.toMillis(); left > 0; left -= step.toMillis()) {
            page.clock().fastForward(Math.min(left, step.toMillis()));
        }
        OffsetDateTime reached = toDateTime(browserMillis(page));
        if (reached.isAfter(now)) {
            backend.setBackendClock(reached);
            now = reached;
        }
    }

    public void elapse(Page page, Duration total) {
        elapse(page, total, total);
    }

    /**
     * 把這個分頁的瀏覽器時鐘停在稍後一點的時刻，計時器不再到期，畫面的自動再試不會在兩個步驟之間自己發生；
     * 後端撥到同一刻，之後「過了 N 秒」從兩邊相同的時刻往後推。
     * 停住的時鐘只能經由 {@link #releaseAfter(Runnable)} 或 {@link #releaseIfTriggered(BooleanSupplier)} 放開，
     * 都沒放開時場景結束時放開：畫面框架讀到資料後排在 {@code setTimeout(0)} 才更新畫面，時鐘一直停著畫面就不會換。
     */
    public void pauseBrowser(Page page, Duration margin) {
        releasePaused();
        long at = browserMillis(page) + margin.toMillis();
        page.clock().pauseAt(at);
        pausedPage = page;
        moveTo(toDateTime(at));
    }

    public boolean browserPaused() {
        return pausedPage != null && !pausedPage.isClosed();
    }

    public void releaseAfter(Runnable trigger) {
        try {
            trigger.run();
        } finally {
            releasePaused();
        }
    }

    /** 觸發沒有成功時不放開，讓時鐘停到場景結束：放開的話畫面自己的自動再試會補上，分不出是不是這一步引發的。 */
    public void releaseIfTriggered(BooleanSupplier trigger) {
        if (trigger.getAsBoolean()) {
            releasePaused();
        }
    }

    void releasePaused() {
        Page paused = pausedPage;
        pausedPage = null;
        if (paused != null && !paused.isClosed()) {
            paused.clock().resume();
        }
    }

    public void moveTo(LocalTime time) {
        moveTo(at(today(), time));
    }

    public void moveTo(LocalDate date) {
        moveTo(at(date, now.toLocalTime()));
    }

    public void advance(Duration duration) {
        moveTo(now.plus(duration));
    }

    void startAtDefault() {
        now = at(DEFAULT_DATE, DEFAULT_TIME);
        backend.setBackendClock(now);
    }

    void install(BrowserContext context) {
        context.clock().install(new Clock.InstallOptions().setTime(now.toInstant().toEpochMilli()));
        contexts.add(context);
    }

    void forget(BrowserContext context) {
        contexts.remove(context);
    }

    void reset() {
        pausedPage = null;
        contexts.clear();
        backend.resetBackendClock();
    }

    private static OffsetDateTime toDateTime(long epochMillis) {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private static long browserMillis(Page page) {
        return JsResult.number(page.evaluate("Date.now()")).longValue();
    }

    private long browserMillis(BrowserContext context) {
        for (Page page : context.pages()) {
            if (!page.isClosed()) {
                return browserMillis(page);
            }
        }
        return now.toInstant().toEpochMilli();
    }
}
