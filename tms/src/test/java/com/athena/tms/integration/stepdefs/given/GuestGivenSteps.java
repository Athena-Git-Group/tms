package com.athena.tms.integration.stepdefs.given;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.GuestFixtures;
import com.athena.tms.service.GuestScanService;
import io.cucumber.java.en.Given;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.slf4j.LoggerFactory;

public class GuestGivenSteps {

    private final ScenarioContext ctx;
    private final DbSupport db;
    private final GuestFixtures guest;

    public GuestGivenSteps(ScenarioContext ctx, DbSupport db, GuestFixtures guest) {
        this.ctx = ctx;
        this.db = db;
        this.guest = guest;
    }

    @Given("^房 502 查無當前有效訂單$")
    public void room502HasNoCurrentReservation() {
        Integer current = db.admin().queryForObject(
                "SELECT count(*) FROM reservations_cache WHERE tenant_id = 1 AND room_id = ? "
                        + "AND superseded_at IS NULL AND checkin_at <= now() AND checkout_at > now()",
                Integer.class, ctx.entityId("r-502"));
        Assertions.assertEquals(0, current, "空房掃碼的前提：房 502 不該有當前訂單");
    }

    @Given("^住客已以 session \"([^\"]+)\" 在 10 分鐘內成功建立 3 筆需求$")
    public void threeRequestsAlreadyCreated(String sessionSymbol) {
        String session = ctx.guestSession(sessionSymbol);
        Assertions.assertEquals(201, guest.createRequest(session, Map.of("itemId", "towel")).status());
        Assertions.assertEquals(201, guest.createRequest(session, Map.of("itemId", "water")).status());
        ScenarioContext.HttpResult third = guest.createRequest(session, Map.of("itemId", "toothbrush"));
        Assertions.assertEquals(201, third.status(), "限流線內的第 3 筆該成功: " + third.body());
        ctx.put("thirdResponse", third);
    }

    @Given("^新住客以 session \"([^\"]+)\" 送出需求 選單項「([^」]+)」$")
    public void newGuestCreatesMenuRequest(String sessionSymbol, String label) {
        ScenarioContext.HttpResult created = guest.createRequest(
                ctx.guestSession(sessionSymbol), Map.of("itemId", GuestFixtures.menuKeyOf(label)));
        Assertions.assertEquals(201, created.status(), "前置建單必須成功: " + created.body());
        ctx.put("myTitle", label);
    }

    @Given("^同一 IP 在 10 分鐘內已有 10 次掃碼失敗$")
    public void tenScanFailuresFromSameIp() {
        Logger scanLogger = (Logger) LoggerFactory.getLogger(GuestScanService.class);
        ListAppender<ILoggingEvent> alerts = new ListAppender<>();
        alerts.start();
        scanLogger.addAppender(alerts);
        ctx.put("alertAppender", alerts);
        String attackerIp = "203.0.113.66";
        ctx.put("attackerIp", attackerIp);
        for (int attempt = 0; attempt < 10; attempt++) {
            Assertions.assertEquals(409, guest.scan("GuessedToken" + attempt + "xxxxxxxx", attackerIp).status(),
                    "猜測 token 的掃碼該 409");
        }
    }

    @Given("^同一 IP 已有 (\\d+) 次掃碼失敗$")
    public void nScanFailuresFromSameIp(int failures) {
        String ip = "203.0.113.99";
        ctx.put("attackerIp", ip);
        for (int attempt = 0; attempt < failures; attempt++) {
            Assertions.assertEquals(409, guest.scan("tok-502-v1", ip).status());
        }
    }
}
