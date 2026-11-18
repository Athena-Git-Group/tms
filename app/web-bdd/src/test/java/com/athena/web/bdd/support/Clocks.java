package com.athena.web.bdd.support;

import com.athena.bddsupport.BackendChannel;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Clock;
import com.microsoft.playwright.Page;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 場景裡的「現在」：後端的時鐘與瀏覽器的時鐘一起撥，兩邊永遠指向同一個時刻。
 *
 * <p>畫面上的「已等 N 分」是瀏覽器用自己的時間算的，單的時間是後端寫的；只撥其中一邊，
 * 畫面算出來的分鐘數就會和案例寫的對不上。所以步驟一律經過這裡撥時間，不各自去動其中一邊。
 *
 * <p>兩邊走法不同，這是刻意的：
 * <ul>
 *   <li>後端被固定在指定的時刻，不會自己前進，資料準備寫出來的時間才會每次都一樣。</li>
 *   <li>瀏覽器從同一個時刻起照真實速度往前走。瀏覽器的時鐘一旦停住，計時器就不會到期，
 *       拖曳派單會起不了拖、放不下去，所以這裡不提供把瀏覽器時鐘停住的做法。</li>
 * </ul>
 * 場景跑了幾秒，瀏覽器就比後端快幾秒；{@link #moveTo(OffsetDateTime)} 每次都會把兩邊重新對齊。
 *
 * <p>每個場景開始時是 2026 年 10 月 26 日週一 14:10，時區跟著執行測試的機器，後端也在同一台機器上。
 */
public final class Clocks {

    public static final LocalDate DEFAULT_DATE = LocalDate.of(2026, 10, 26);

    public static final LocalTime DEFAULT_TIME = LocalTime.of(14, 10);

    private final BackendChannel backend;

    private final List<BrowserContext> contexts = new ArrayList<>();

    private OffsetDateTime now = at(DEFAULT_DATE, DEFAULT_TIME);

    Clocks(BackendChannel backend) {
        this.backend = backend;
    }

    /** 把某一天的某個時刻換成帶時差的時刻，時區用執行測試的機器的。 */
    public static OffsetDateTime at(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    /** 場景目前的「現在」，也就是後端被固定的那個時刻。 */
    public OffsetDateTime now() {
        return now;
    }

    public LocalDate today() {
        return now.toLocalDate();
    }

    /**
     * 把兩邊的時鐘撥到指定的時刻，往前往後都可以。
     *
     * <p>已經開著的畫面：往前撥時，到期的計時器會補跑一次，畫面上的分鐘數馬上重算；
     * 往回撥時瀏覽器不會補跑計時器，畫面要等自己下一次計時才更新。還沒開的畫面在開啟時直接從這個時刻起算。
     */
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

    /** 今天的某個時刻。 */
    public void moveTo(LocalTime time) {
        moveTo(at(today(), time));
    }

    /** 換一天，時刻不變。 */
    public void moveTo(LocalDate date) {
        moveTo(at(date, now.toLocalTime()));
    }

    /** 從場景目前的「現在」再往後撥一段時間。 */
    public void advance(Duration duration) {
        moveTo(now.plus(duration));
    }

    /** 場景開始：後端固定在預設時刻。這時還沒有任何畫面。 */
    void startAtDefault() {
        now = at(DEFAULT_DATE, DEFAULT_TIME);
        backend.setBackendClock(now);
    }

    /** 新開的 context 要在載入任何頁面之前接上假時鐘，頁面一開始看到的就是場景的「現在」。 */
    void install(BrowserContext context) {
        context.clock().install(new Clock.InstallOptions().setTime(now.toInstant().toEpochMilli()));
        contexts.add(context);
    }

    void forget(BrowserContext context) {
        contexts.remove(context);
    }

    /** 場景結束：後端回到系統時間，免得同一個後端接著給人手動操作時還停在場景的時刻。 */
    void reset() {
        contexts.clear();
        backend.resetBackendClock();
    }

    /** 瀏覽器此刻認定的時間。context 裡沒有分頁時問不到，退回場景的「現在」。 */
    private long browserMillis(BrowserContext context) {
        for (Page page : context.pages()) {
            if (!page.isClosed()) {
                return ((Number) page.evaluate("Date.now()")).longValue();
            }
        }
        return now.toInstant().toEpochMilli();
    }
}
