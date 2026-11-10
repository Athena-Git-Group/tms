package com.athena.tms.integration.stepdefs.given;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.Appender;
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

    @Given(value="^房 502 查無當前有效訂單$")
    public void room502HasNoCurrentReservation() {
        Integer current = (Integer)this.db.admin().queryForObject("SELECT count(*) FROM reservations_cache WHERE tenant_id = 1 AND room_id = ? AND superseded_at IS NULL AND checkin_at <= now() AND checkout_at > now()", Integer.class, new Object[]{this.ctx.entityId("r-502")});
        Assertions.assertEquals((int)0, (int)current, (String)"空房掃碼的前提：房 502 不該有當前訂單");
    }

    @Given(value="^住客已以 session \"([^\"]+)\" 在 10 分鐘內成功建立 3 筆需求$")
    public void threeRequestsAlreadyCreated(String sessionSymbol) {
        String session = this.ctx.guestSession(sessionSymbol);
        Assertions.assertEquals((int)201, (int)this.guest.createRequest(session, Map.of("itemId", "towel")).status());
        Assertions.assertEquals((int)201, (int)this.guest.createRequest(session, Map.of("itemId", "water")).status());
        ScenarioContext.HttpResult third = this.guest.createRequest(session, Map.of("itemId", "toothbrush"));
        Assertions.assertEquals((int)201, (int)third.status(), (String)("限流線內的第 3 筆該成功: " + third.body()));
        this.ctx.put("thirdResponse", third);
    }

    @Given(value="^新住客以 session \"([^\"]+)\" 送出需求 選單項「([^」]+)」$")
    public void newGuestCreatesMenuRequest(String sessionSymbol, String label) {
        ScenarioContext.HttpResult created = this.guest.createRequest(this.ctx.guestSession(sessionSymbol), Map.of("itemId", GuestFixtures.menuKeyOf(label)));
        Assertions.assertEquals((int)201, (int)created.status(), (String)("前置建單必須成功: " + created.body()));
        this.ctx.put("myTitle", label);
    }

    @Given(value="^同一 IP 在 10 分鐘內已有 10 次掃碼失敗$")
    public void tenScanFailuresFromSameIp() {
        Logger scanLogger = (Logger)LoggerFactory.getLogger(GuestScanService.class);
        ListAppender alerts = new ListAppender();
        alerts.start();
        scanLogger.addAppender((Appender)alerts);
        this.ctx.put("alertAppender", alerts);
        String attackerIp = "203.0.113.66";
        this.ctx.put("attackerIp", attackerIp);
        for (int attempt = 0; attempt < 10; ++attempt) {
            Assertions.assertEquals((int)409, (int)this.guest.scan("GuessedToken" + attempt + "xxxxxxxx", attackerIp).status(), (String)"猜測 token 的掃碼該 409");
        }
    }

    @Given(value="^同一 IP 已有 (\\d+) 次掃碼失敗$")
    public void nScanFailuresFromSameIp(int failures) {
        String ip = "203.0.113.99";
        this.ctx.put("attackerIp", ip);
        for (int attempt = 0; attempt < failures; ++attempt) {
            Assertions.assertEquals((int)409, (int)this.guest.scan("tok-502-v1", ip).status());
        }
    }
}
