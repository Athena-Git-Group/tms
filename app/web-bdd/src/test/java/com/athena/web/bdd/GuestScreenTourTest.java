package com.athena.web.bdd;

import com.athena.bddsupport.BackendChannel;
import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.bddsupport.BackendChannel.FrontdeskDefaults;
import com.athena.bddsupport.GuestSeed;
import com.athena.bddsupport.GuestSeed.Situation;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitUntilState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class GuestScreenTourTest {

    private static final String ROOM = "311";
    private static final String BASE = System.getProperty("web.baseUrl", "http://localhost:5199").replaceAll("/+$", "");
    private static final Path OUT = Path.of(System.getProperty("tour.out", "/Users/Caresys/.claude/jobs/bf2b4d32/tmp/4g-shots"));
    private static final String PROTO = Path.of(System.getProperty("user.dir")).resolve(
            System.getProperty("user.dir").endsWith("web-bdd") ? "../../specs/plans/005-guest-web-main-flow/ui" : "specs/plans/005-guest-web-main-flow/ui")
            .normalize().toUri().toString();
    private static final String STATE_CONFLICT = "{\"code\":\"state_conflict\",\"message\":\"already claimed\"}";

    private static final String DUMP_JS = """
            () => {
              const out = [];
              const keep = ['fontSize','lineHeight','fontWeight','letterSpacing','color','backgroundColor',
                'borderTopLeftRadius','boxShadow','paddingTop','paddingRight','paddingBottom','paddingLeft',
                'borderTopWidth','borderTopColor','gap','marginTop','marginBottom','textAlign','opacity'];
              for (const el of document.querySelectorAll('body *')) {
                const r = el.getBoundingClientRect();
                if (r.width === 0 || r.height === 0) continue;
                const cs = getComputedStyle(el);
                if (cs.visibility === 'hidden' || cs.display === 'none') continue;
                const own = [...el.childNodes].filter(n => n.nodeType === 3).map(n => n.textContent.trim()).join(' ').trim();
                const boxy = cs.backgroundColor !== 'rgba(0, 0, 0, 0)' || cs.boxShadow !== 'none'
                  || parseFloat(cs.borderTopLeftRadius) > 0 || parseFloat(cs.borderTopWidth) > 0;
                const svg = el.tagName.toLowerCase() === 'svg';
                if (!own && !boxy && !svg) continue;
                const o = { tag: el.tagName.toLowerCase(), text: own.slice(0, 80),
                  inner: (el.innerText || '').replace(/\\s+/g, ' ').trim().slice(0, 80),
                  label: el.getAttribute('aria-label'), role: el.getAttribute('role'),
                  cls: (el.className && el.className.baseVal === undefined ? String(el.className) : '').slice(0, 200),
                  x: Math.round(r.x * 10) / 10, y: Math.round((r.y + scrollY) * 10) / 10,
                  w: Math.round(r.width * 10) / 10, h: Math.round(r.height * 10) / 10 };
                for (const k of keep) o[k] = cs[k];
                if (svg) o.fill = cs.fill;
                out.push(o);
              }
              return JSON.stringify({ title: document.title, lang: document.documentElement.lang,
                bodyBg: getComputedStyle(document.body).backgroundColor, scrollWidth: document.documentElement.scrollWidth,
                scrollHeight: document.documentElement.scrollHeight, items: out });
            }
            """;

    private static final Pattern[] FORBIDDEN = {
            Pattern.compile("\\b(triage|pool|accepted|deferred|doing|interrupted|done|inspected|closed|cancelled|housekeeping|engineering|frontdesk|amenity|repair|cleaning|other)\\b"),
            Pattern.compile("(rate_limited|state_conflict|room_vacant|validation_failed|\\b[45]\\d\\d\\b)"),
            Pattern.compile("(待派單|待認領|延後|中斷|查房|驗收)"),
            Pattern.compile("(阿美|阿強|張主管|測試住客|雅典娜)"),
            Pattern.compile("(20\\d\\d|\\d{1,2}/\\d{1,2}|\\d{1,2}月\\d{1,2}日)"),
    };
    private static final Pattern TIME = Pattern.compile("\\d{1,2}:\\d{2}");

    private final BackendChannel channel = new BackendChannel();
    private final Random random = new Random();
    private final StringBuilder jargon = new StringBuilder();
    private final List<String> skipped = new ArrayList<>();
    private Browser browser;
    private OffsetDateTime base;
    private int seq = 0;
    private String lastPrefix;
    private Page proto;

    @Test
    void 住客頁與雛形逐狀態截圖() throws Exception {
        Files.createDirectories(OUT);
        try (Playwright playwright = Playwright.create()) {
            browser = playwright.webkit().launch();
            BrowserContext protoContext = newContext();
            proto = protoContext.newPage();
            proto.navigate(PROTO + "index.html?reset=1");

            phaseAll();
            phaseEnded();
            phaseNew();
            phaseQuota();
            protoContext.close();
        } finally {
            channel.resetBackendClock();
            Files.writeString(OUT.resolve("jargon.txt"), jargon.toString(), StandardCharsets.UTF_8);
            Files.writeString(OUT.resolve("skipped.txt"), String.join("\n", skipped), StandardCharsets.UTF_8);
        }
    }

    private void phaseAll() throws IOException {
        FrontdeskDefaults defaults = freshStay();
        CreatedStaff amei = defaults.staff("阿美");
        CreatedStaff aqiang = defaults.staff("阿強");
        Map<String, UUID> ids = new LinkedHashMap<>();
        ids.put("closed", seed(Situation.CLOSED, "燈具故障", "repair", "08:50", aqiang, null));
        ids.put("inspected", seed(Situation.INSPECTION_PASSED, "打掃", "cleaning", "09:40", amei, null));
        ids.put("done", seed(Situation.DONE, "拖鞋", "amenity", "10:15", amei, null));
        ids.put("cancelled", seed(Situation.CANCELLED_BY_GUEST, "枕頭", "amenity", "10:40", null, null));
        ids.put("interrupted", seed(Situation.INTERRUPTED, "浴袍", "amenity", "11:30", amei, null));
        ids.put("answer", seed(Situation.ANSWERED, "冷氣不冷：風量很小", "other", "12:05", null, "已請工務上去看"));
        ids.put("deferred", seed(Situation.DEFERRED, "吹風機", "amenity", "12:50", amei, null));
        ids.put("accepted", seed(Situation.HOUSEKEEPING_ACCEPTED, "牙刷組", "amenity", "13:20", amei, null));
        ids.put("triage", seed(Situation.TRIAGE, "毛巾", "other", "13:40", null, null));
        ids.put("pool", seed(Situation.HOUSEKEEPING_POOL, "衣架", "amenity", "13:45", null, null));
        ids.put("doing", seed(Situation.DOING, "礦泉水 ×2", "amenity", "13:55", amei, null));
        ids.put("long", seed(Situation.TRIAGE, "冷氣不冷：房間冷氣開到最強還是不冷，而且一直有滴水聲，床邊地毯都濕了", "other", "14:01", null, null));
        String token = channel.roomQrToken(ROOM);

        try (BrowserContext context = newContext()) {
            Page page = context.newPage();
            List<Route> held = new ArrayList<>();
            page.route("**/guest/scan", held::add);
            page.navigate(BASE + "/g/" + token, new Page.NavigateOptions().setWaitUntil(WaitUntilState.COMMIT));
            try {
                page.waitForCondition(() -> !held.isEmpty(), new Page.WaitForConditionOptions().setTimeout(15_000));
                page.waitForTimeout(600);
                shotApp(page, "entry-loading");
            } catch (RuntimeException e) {
                skipped.add("entry-loading: " + e.getMessage());
            }
            held.forEach(Route::resume);
            page.unroute("**/guest/scan");
        }
        shotProto("entry-loading", "unavailable.html?state=loading");

        try (BrowserContext context = newContext()) {
            Page page = context.newPage();
            page.navigate(BASE + "/g/not-a-real-token-" + random.nextInt(1_000_000));
            settle(page);
            shotApp(page, "entry-unavailable");
        }
        shotProto("entry-unavailable", "unavailable.html");

        try (BrowserContext context = newContext()) {
            Page page = context.newPage();
            String vacant = channel.roomQrToken("308");
            page.navigate(BASE + "/g/" + vacant);
            settle(page);
            shotApp(page, "entry-unavailable-vacant308");
        } catch (RuntimeException e) {
            skipped.add("entry-unavailable-vacant308: " + e.getMessage());
        }

        try (BrowserContext context = newContext()) {
            Page page = context.newPage();
            page.route("**/guest/scan", route -> route.abort("failed"));
            page.navigate(BASE + "/g/" + token);
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("重試")).waitFor();
            settle(page);
            shotApp(page, "entry-retry");
        }
        shotProto("entry-retry", "unavailable.html?state=retry");

        try (BrowserContext context = newContext()) {
            Page page = context.newPage();
            openHome(page, token);
            shotApp(page, "home-active");
            shotProto("home-active", "index.html?reset=1&seed=all");

            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("語言").setExact(true)).click();
            page.getByRole(AriaRole.MENU).waitFor();
            page.waitForTimeout(400);
            shotApp(page, "home-language-menu");
            proto.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("語言").setExact(true)).click();
            proto.waitForTimeout(400);
            shotProtoNow("home-language-menu");
            page.keyboard().press("Escape");

            go(page, token, "requests");
            shotApp(page, "requests-both");
            shotProto("requests-both", "requests.html?seed=all");

            for (String state : List.of("triage", "pool", "accepted", "deferred", "doing", "interrupted",
                    "done", "inspected", "closed", "cancelled", "answer", "long")) {
                go(page, token, "requests/" + ids.get(state));
                shotApp(page, "progress-" + state);
                shotProto("progress-" + state, "request.html?seed=all&id=" + state);
            }

            go(page, token, "requests/" + ids.get("done"));
            page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName("4 顆星")).click();
            page.waitForTimeout(300);
            shotApp(page, "rating-4-stars");
            proto.navigate(PROTO + "request.html?seed=all&id=done");
            protoSettle();
            proto.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName("4 顆星")).click();
            proto.waitForTimeout(300);
            shotProtoNow("rating-4-stars");

            go(page, token, "requests/" + ids.get("pool"));
            openCancelDialog(page);
            shotApp(page, "cancel-dialog");
            proto.navigate(PROTO + "request.html?seed=all&id=pool");
            protoSettle();
            openCancelDialog(proto);
            shotProtoNow("cancel-dialog");

            page.route("**/guest/requests/*/cancel", route -> route.fulfill(new Route.FulfillOptions()
                    .setStatus(409).setContentType("application/json").setBody(STATE_CONFLICT)));
            confirmCancel(page);
            shotToast(page, "toast-too-late");
            page.unroute("**/guest/requests/*/cancel");
            proto.navigate(PROTO + "request.html?seed=all&id=pool&fail=claimed");
            protoSettle();
            openCancelDialog(proto);
            confirmCancel(proto);
            shotToastProto("toast-too-late");

            go(page, token, "requests/" + ids.get("pool"));
            page.route("**/guest/requests/*/cancel", route -> route.abort("failed"));
            openCancelDialog(page);
            confirmCancel(page);
            shotToast(page, "toast-not-sent");
            page.unroute("**/guest/requests/*/cancel");
            proto.navigate(PROTO + "request.html?reset=1&seed=all&id=pool&fail=net");
            protoSettle();
            openCancelDialog(proto);
            confirmCancel(proto);
            shotToastProto("toast-not-sent");

            go(page, token, "requests/" + ids.get("pool"));
            openCancelDialog(page);
            confirmCancel(page);
            shotToast(page, "toast-cancelled");
            proto.navigate(PROTO + "request.html?reset=1&seed=all&id=pool");
            protoSettle();
            openCancelDialog(proto);
            confirmCancel(proto);
            shotToastProto("toast-cancelled");
            page.waitForTimeout(3_000);
            shotApp(page, "progress-after-cancel");
            proto.waitForTimeout(3_000);
            shotProtoNow("progress-after-cancel");

            go(page, token, "guide/wifi");
            shotApp(page, "guide-wifi");
            shotProto("guide-wifi", "guide.html?key=wifi");
            go(page, token, "guide/breakfast");
            shotApp(page, "guide-breakfast");
            shotProto("guide-breakfast", "guide.html?key=breakfast");

            go(page, token, "sent?ids=" + ids.get("triage"));
            shotApp(page, "sent-one");
            shotProto("sent-one", "sent.html?reset=1&seed=all&ids=triage");
            go(page, token, "sent?ids=" + ids.get("doing") + "," + ids.get("accepted"));
            shotApp(page, "sent-many");
            shotProto("sent-many", "sent.html?seed=all&ids=doing,accepted");
        }
    }

    private void phaseEnded() throws IOException {
        FrontdeskDefaults defaults = freshStay();
        UUID towel = seed(Situation.DONE, "毛巾", "amenity", "09:30", defaults.staff("阿美"), null);
        channel.setTaskRating(towel, 5);
        seed(Situation.CANCELLED_BY_GUEST, "枕頭", "amenity", "10:02", null, null);
        String token = channel.roomQrToken(ROOM);
        try (BrowserContext context = newContext()) {
            Page page = context.newPage();
            openHome(page, token);
            shotApp(page, "home-all-ended");
            shotProto("home-all-ended", "index.html?reset=1&seed=ended");
            go(page, token, "requests/" + towel);
            shotApp(page, "progress-done-rated");
            shotProto("progress-done-rated", "request.html?seed=ended&id=r1");
            go(page, token, "requests");
            shotApp(page, "requests-ended-only");
            shotProto("requests-ended-only", "requests.html?seed=ended");
        }
    }

    private void phaseNew() throws IOException {
        FrontdeskDefaults defaults = freshStay();
        String token = channel.roomQrToken(ROOM);
        try (BrowserContext context = newContext()) {
            Page page = context.newPage();
            openHome(page, token);
            shotApp(page, "home-no-requests");
            shotProto("home-no-requests", "index.html?reset=1&seed=new");

            go(page, token, "requests");
            shotApp(page, "requests-empty");
            shotProto("requests-empty", "requests.html?seed=new");

            go(page, token, "supply");
            shotApp(page, "supply-zero");
            shotProto("supply-zero", "supply.html?seed=new");

            inc(page, "礦泉水", 2);
            inc(page, "毛巾", 1);
            shotApp(page, "supply-some");
            proto.navigate(PROTO + "supply.html?seed=new");
            protoSettle();
            inc(proto, "礦泉水", 2);
            inc(proto, "毛巾", 1);
            shotProtoNow("supply-some");

            inc(page, "毛巾", 4);
            shotApp(page, "supply-max");
            inc(proto, "毛巾", 4);
            shotProtoNow("supply-max");

            go(page, token, "supply");
            inc(page, "礦泉水", 2);
            inc(page, "毛巾", 1);
            page.route("**/guest/requests", route -> {
                if ("POST".equals(route.request().method())) route.abort("failed"); else route.resume();
            });
            submit(page, "送出 2 項");
            page.getByRole(AriaRole.ALERT).waitFor();
            settle(page);
            shotApp(page, "supply-net-red");
            page.unroute("**/guest/requests");
            proto.navigate(PROTO + "supply.html?reset=1&seed=new&fail=net");
            protoSettle();
            inc(proto, "礦泉水", 2);
            inc(proto, "毛巾", 1);
            submit(proto, "送出 2 項");
            protoAlert();
            shotProtoNow("supply-net-red");

            go(page, token, "supply");
            inc(page, "毛巾", 1);
            inc(page, "礦泉水", 2);
            BackendChannel.GuestScan otherPhone = channel.guestScan(token);
            String otherPhoneItem = otherPhone.menu().get(0).key();
            channel.guestSessionSubmitMenuItem(otherPhone.sessionToken(), otherPhoneItem, 1);
            channel.guestSessionSubmitMenuItem(otherPhone.sessionToken(), otherPhoneItem, 1);
            submit(page, "送出 2 項");
            page.getByRole(AriaRole.ALERT).waitFor();
            settle(page);
            shotApp(page, "supply-midway-warm");
            proto.navigate(PROTO + "supply.html?reset=1&seed=new&fail=midway");
            protoSettle();
            inc(proto, "毛巾", 1);
            inc(proto, "礦泉水", 2);
            submit(proto, "送出 2 項");
            protoAlert();
            shotProtoNow("supply-midway-warm");

            go(page, token, "repair");
            shotApp(page, "repair-none");
            shotProto("repair-none", "repair.html?reset=1&seed=new");

            chip(page, "冷氣不冷");
            page.getByRole(AriaRole.TEXTBOX).fill("風量很小");
            page.waitForTimeout(200);
            shotApp(page, "repair-category");
            chip(proto, "冷氣不冷");
            proto.getByRole(AriaRole.TEXTBOX).fill("風量很小");
            proto.waitForTimeout(200);
            shotProtoNow("repair-category");

            go(page, token, "repair");
            chip(page, "其他");
            shotApp(page, "repair-other-required");
            proto.navigate(PROTO + "repair.html?seed=new");
            protoSettle();
            chip(proto, "其他");
            shotProtoNow("repair-other-required");

            go(page, token, "repair");
            chip(page, "燈具故障");
            page.route("**/guest/requests", route -> {
                if ("POST".equals(route.request().method())) route.abort("failed"); else route.resume();
            });
            submit(page, "送出報修");
            page.getByRole(AriaRole.ALERT).waitFor();
            settle(page);
            shotApp(page, "repair-net-red");
            page.unroute("**/guest/requests");
            proto.navigate(PROTO + "repair.html?seed=new&fail=net");
            protoSettle();
            chip(proto, "燈具故障");
            submit(proto, "送出報修");
            protoAlert();
            shotProtoNow("repair-net-red");

            channel.setTenantConfig(defaults.tenantId(), "guest_info_pages", List.of());
            openHome(page, token);
            shotApp(page, "home-no-info-pages");
            shotProto("home-no-info-pages", "index.html?reset=1&seed=new&info=none");
        }
    }

    private void phaseQuota() throws IOException {
        freshStay();
        seed(Situation.HOUSEKEEPING_POOL, "毛巾", "amenity", "14:24", null, null);
        seed(Situation.HOUSEKEEPING_POOL, "衣架", "amenity", "14:26", null, null);
        String token = channel.roomQrToken(ROOM);
        try (BrowserContext context = newContext()) {
            Page page = context.newPage();
            openHome(page, token);
            go(page, token, "supply");
            inc(page, "礦泉水", 1);
            inc(page, "牙刷組", 1);
            submit(page, "送出 2 項");
            page.getByRole(AriaRole.ALERT).waitFor();
            settle(page);
            shotApp(page, "supply-quota-left-1");
        }
        proto.navigate(PROTO + "supply.html?reset=1&seed=three&at=14:14");
        protoSettle();
        inc(proto, "礦泉水", 1);
        inc(proto, "牙刷組", 1);
        submit(proto, "送出 2 項");
        protoAlert();
        shotProtoNow("supply-quota-left-1");

        seed(Situation.HOUSEKEEPING_POOL, "牙刷組", "amenity", "14:28", null, null);
        try (BrowserContext context = newContext()) {
            Page page = context.newPage();
            openHome(page, token);
            go(page, token, "supply");
            inc(page, "礦泉水", 1);
            submit(page, "送出 1 項");
            page.getByRole(AriaRole.ALERT).waitFor();
            settle(page);
            shotApp(page, "supply-quota-zero");
            go(page, token, "repair");
            chip(page, "冷氣不冷");
            submit(page, "送出報修");
            page.getByRole(AriaRole.ALERT).waitFor();
            settle(page);
            shotApp(page, "repair-quota-zero");
        }
        proto.navigate(PROTO + "supply.html?reset=1&seed=new");
        protoSettle();
        inc(proto, "毛巾", 1);
        inc(proto, "衣架", 1);
        inc(proto, "牙刷組", 1);
        submit(proto, "送出 3 項");
        proto.waitForURL("**/sent.html*");
        proto.navigate(PROTO + "supply.html");
        protoSettle();
        inc(proto, "礦泉水", 1);
        submit(proto, "送出 1 項");
        protoAlert();
        lastPrefix = null;
        shotProtoNamed("supply-quota-zero");
        proto.navigate(PROTO + "repair.html");
        protoSettle();
        chip(proto, "冷氣不冷");
        submit(proto, "送出報修");
        protoAlert();
        shotProtoNamed("repair-quota-zero");
    }

    private FrontdeskDefaults freshStay() {
        channel.resetBusinessData();
        FrontdeskDefaults defaults = channel.ensureFrontdeskDefaults();
        channel.ensureGuestDefaults();
        channel.resetBackendClock();
        OffsetDateTime now = channel.backendNow();
        OffsetDateTime demo = now.withHour(14).withMinute(30).withSecond(0).withNano(0);
        base = demo.isAfter(now.minusMinutes(1)) ? now.minusMinutes(1).withSecond(0).withNano(0) : demo;
        // 後端的送單名額以後端時鐘數 10 分鐘內的單，跟瀏覽器一起定在 base，seed 的送出時間才落在同一個窗裡。
        channel.setBackendClock(base);
        return defaults;
    }

    private UUID seed(Situation situation, String title, String type, String hhmm, CreatedStaff assignee, String answer) {
        long minutesBefore = (LocalTime.of(14, 30).toSecondOfDay() - LocalTime.parse(hhmm).toSecondOfDay()) / 60;
        OffsetDateTime createdAt = base.minusMinutes(minutesBefore);
        OffsetDateTime acceptedAt = assignee == null ? null : createdAt.plusMinutes(1);
        boolean started = situation == Situation.DOING || situation == Situation.INTERRUPTED || situation == Situation.DONE
                || situation == Situation.INSPECTION_PASSED || situation == Situation.CLOSED;
        OffsetDateTime startedAt = started ? createdAt.plusMinutes(2) : null;
        return channel.seedGuestRequest(ROOM, new GuestSeed(situation, title, type, createdAt, assignee, acceptedAt, startedAt, answer));
    }

    private BrowserContext newContext() {
        BrowserContext context = browser.newContext(WebProfile.PHONE.contextOptions().setDeviceScaleFactor(2));
        // 每個畫面從乾淨的掃碼限流器開始，前一個畫面的掃碼不會把這個畫面擋下；送單名額跟著 freshStay 清掉的業務資料歸零。
        channel.resetRateLimits();
        if (base != null) {
            context.clock().setFixedTime(base.toInstant().toEpochMilli());
        }
        return context;
    }

    private void openHome(Page page, String token) {
        page.navigate(BASE + "/g/" + token);
        page.locator("main h1").first().waitFor();
        settle(page);
    }

    private void go(Page page, String token, String sub) {
        page.navigate(BASE + "/g/" + token + "/" + sub);
        page.locator("main").first().waitFor();
        settle(page);
    }

    private void settle(Page page) {
        page.waitForLoadState();
        try {
            page.waitForFunction("() => document.querySelector('main, [role=status]') && !document.querySelector('[aria-busy=\"true\"]')",
                    null, new Page.WaitForFunctionOptions().setTimeout(10_000));
        } catch (RuntimeException e) {
            skipped.add("settle timeout on " + page.url());
        }
        page.waitForTimeout(500);
    }

    private void protoAlert() {
        proto.locator("[role=alert]").first().waitFor(new Locator.WaitForOptions().setTimeout(10_000));
        proto.waitForTimeout(300);
    }

    private void protoSettle() {
        proto.waitForLoadState();
        proto.waitForTimeout(300);
    }

    private void inc(Page page, String item, int times) {
        Locator plus = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("增加" + item).setExact(true));
        for (int i = 0; i < times; i++) {
            plus.click();
        }
        page.waitForTimeout(150);
    }

    private void chip(Page page, String name) {
        page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName(name).setExact(true)).click();
        page.waitForTimeout(150);
    }

    private void submit(Page page, String name) {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true)).click();
    }

    private void openCancelDialog(Page page) {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("取消這個需求").setExact(true)).click();
        page.locator("[role=alertdialog], [role=dialog]").first().waitFor();
        page.waitForTimeout(400);
    }

    private void confirmCancel(Page page) {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("取消需求").setExact(true)).click();
    }

    private void shotToast(Page page, String name) throws IOException {
        page.locator("[role=status]:not(:empty)").first().waitFor(new Locator.WaitForOptions().setTimeout(5_000));
        page.waitForTimeout(350);
        shotApp(page, name);
    }

    private void shotToastProto(String name) throws IOException {
        proto.locator("#toast:not([hidden])").waitFor(new Locator.WaitForOptions().setTimeout(5_000));
        proto.waitForTimeout(350);
        shotProtoNow(name);
    }

    private void shotApp(Page page, String state) throws IOException {
        String prefix = String.format("%02d-%s", ++seq, state);
        page.screenshot(new Page.ScreenshotOptions().setPath(OUT.resolve(prefix + "-app.png")).setFullPage(true));
        Files.writeString(OUT.resolve(prefix + "-app.json"), (String) page.evaluate(DUMP_JS), StandardCharsets.UTF_8);
        scanText(prefix, page);
        lastPrefix = prefix;
    }

    private void shotProto(String state, String query) throws IOException {
        proto.navigate(PROTO + query);
        protoSettle();
        shotProtoNow(state);
    }

    private void shotProtoNow(String state) throws IOException {
        String prefix = lastPrefix != null && lastPrefix.endsWith("-" + state) ? lastPrefix : String.format("%02d-%s", ++seq, state);
        proto.screenshot(new Page.ScreenshotOptions().setPath(OUT.resolve(prefix + "-proto.png")).setFullPage(true));
        Files.writeString(OUT.resolve(prefix + "-proto.json"), (String) proto.evaluate(DUMP_JS), StandardCharsets.UTF_8);
    }

    private void shotProtoNamed(String state) throws IOException {
        try (var files = Files.list(OUT)) {
            lastPrefix = files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith("-" + state + "-app.png"))
                    .map(n -> n.substring(0, n.length() - "-app.png".length()))
                    .findFirst().orElse(null);
        }
        shotProtoNow(state);
    }

    private void scanText(String prefix, Page page) {
        String text = page.evaluate("() => document.body.innerText + '\\n' + document.title").toString();
        List<String> hits = new ArrayList<>();
        for (Pattern pattern : FORBIDDEN) {
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                hits.add(m.group());
            }
        }
        if (!prefix.contains("guide")) {
            Matcher t = TIME.matcher(text);
            while (t.find()) {
                String after = text.substring(t.end(), Math.min(text.length(), t.end() + 3));
                if (!after.startsWith(" 送出")) {
                    hits.add("time " + t.group() + " followed by '" + after.replace("\n", "⏎") + "'");
                }
            }
        }
        if (!hits.isEmpty()) {
            jargon.append(prefix).append(": ").append(hits).append('\n');
        }
    }
}
