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

    @Given(value="^\u623f 502 \u67e5\u7121\u7576\u524d\u6709\u6548\u8a02\u55ae$")
    public void room502HasNoCurrentReservation() {
        Integer current = (Integer)this.db.admin().queryForObject("SELECT count(*) FROM reservations_cache WHERE tenant_id = 1 AND room_id = ? AND superseded_at IS NULL AND checkin_at <= now() AND checkout_at > now()", Integer.class, new Object[]{this.ctx.entityId("r-502")});
        Assertions.assertEquals((int)0, (int)current, (String)"\u7a7a\u623f\u6383\u78bc\u7684\u524d\u63d0\uff1a\u623f 502 \u4e0d\u8a72\u6709\u7576\u524d\u8a02\u55ae");
    }

    @Given(value="^\u4f4f\u5ba2\u5df2\u4ee5 session \"([^\"]+)\" \u5728 10 \u5206\u9418\u5167\u6210\u529f\u5efa\u7acb 3 \u7b46\u9700\u6c42$")
    public void threeRequestsAlreadyCreated(String sessionSymbol) {
        String session = this.ctx.guestSession(sessionSymbol);
        Assertions.assertEquals((int)201, (int)this.guest.createRequest(session, Map.of("itemId", "towel")).status());
        Assertions.assertEquals((int)201, (int)this.guest.createRequest(session, Map.of("itemId", "water")).status());
        ScenarioContext.HttpResult third = this.guest.createRequest(session, Map.of("itemId", "toothbrush"));
        Assertions.assertEquals((int)201, (int)third.status(), (String)("\u9650\u6d41\u7dda\u5167\u7684\u7b2c 3 \u7b46\u8a72\u6210\u529f: " + third.body()));
        this.ctx.put("thirdResponse", third);
    }

    @Given(value="^\u65b0\u4f4f\u5ba2\u4ee5 session \"([^\"]+)\" \u9001\u51fa\u9700\u6c42 \u9078\u55ae\u9805\u300c([^\u300d]+)\u300d$")
    public void newGuestCreatesMenuRequest(String sessionSymbol, String label) {
        ScenarioContext.HttpResult created = this.guest.createRequest(this.ctx.guestSession(sessionSymbol), Map.of("itemId", GuestFixtures.menuKeyOf(label)));
        Assertions.assertEquals((int)201, (int)created.status(), (String)("\u524d\u7f6e\u5efa\u55ae\u5fc5\u9808\u6210\u529f: " + created.body()));
        this.ctx.put("myTitle", label);
    }

    @Given(value="^\u540c\u4e00 IP \u5728 10 \u5206\u9418\u5167\u5df2\u6709 10 \u6b21\u6383\u78bc\u5931\u6557$")
    public void tenScanFailuresFromSameIp() {
        Logger scanLogger = (Logger)LoggerFactory.getLogger(GuestScanService.class);
        ListAppender alerts = new ListAppender();
        alerts.start();
        scanLogger.addAppender((Appender)alerts);
        this.ctx.put("alertAppender", alerts);
        String attackerIp = "203.0.113.66";
        this.ctx.put("attackerIp", attackerIp);
        for (int attempt = 0; attempt < 10; ++attempt) {
            Assertions.assertEquals((int)409, (int)this.guest.scan("GuessedToken" + attempt + "xxxxxxxx", attackerIp).status(), (String)"\u731c\u6e2c token \u7684\u6383\u78bc\u8a72 409");
        }
    }

    @Given(value="^\u540c\u4e00 IP \u5df2\u6709 (\\d+) \u6b21\u6383\u78bc\u5931\u6557$")
    public void nScanFailuresFromSameIp(int failures) {
        String ip = "203.0.113.99";
        this.ctx.put("attackerIp", ip);
        for (int attempt = 0; attempt < failures; ++attempt) {
            Assertions.assertEquals((int)409, (int)this.guest.scan("tok-502-v1", ip).status());
        }
    }
}
