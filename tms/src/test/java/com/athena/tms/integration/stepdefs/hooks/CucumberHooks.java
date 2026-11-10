package com.athena.tms.integration.stepdefs.hooks;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.IntegrationSseSupport;
import com.athena.tms.integration.stepdefs.utils.SseSupport;
import com.athena.tms.service.GuestScanService;
import com.athena.tms.service.RoomRequestRateLimiter;
import com.athena.tms.service.ScanIpRateLimiter;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import java.util.List;
import org.slf4j.LoggerFactory;

public class CucumberHooks {

    public static final String ALERT_APPENDER_KEY = "alertAppender";

    private final ScenarioContext scenarioContext;
    private final DbSupport db;
    private final ScanIpRateLimiter scanIpRateLimiter;
    private final RoomRequestRateLimiter roomRequestRateLimiter;

    public CucumberHooks(
            ScenarioContext scenarioContext,
            DbSupport db,
            ScanIpRateLimiter scanIpRateLimiter,
            RoomRequestRateLimiter roomRequestRateLimiter) {
        this.scenarioContext = scenarioContext;
        this.db = db;
        this.scanIpRateLimiter = scanIpRateLimiter;
        this.roomRequestRateLimiter = roomRequestRateLimiter;
    }

    @Before(order = 0)
    public void resetScenarioState() {
        scenarioContext.reset();
    }

    @Before(order = 5)
    public void resetRateLimiters() {
        scanIpRateLimiter.reset();
        roomRequestRateLimiter.reset();
    }

    @Before(order = 10)
    public void cleanBusinessTablesKeepSeeds() {
        db.cleanBusinessTables();
    }

    @After("@sse")
    public void closeSseClients() {
        List<SseSupport.SseStreamClient> clients = scenarioContext.get("sseClients");
        if (clients != null) {
            clients.forEach(SseSupport.SseStreamClient::close);
            clients.clear();
        }
    }

    @After("@pms_integration")
    public void closeIntegrationSseClients() {
        List<IntegrationSseSupport.IntegrationSseClient> clients = scenarioContext.get("integration:sseClients");
        if (clients != null) {
            clients.forEach(IntegrationSseSupport.IntegrationSseClient::close);
            clients.clear();
        }
    }

    @After("@scan-alert")
    public void detachAlertAppender() {
        ListAppender<ILoggingEvent> appender = scenarioContext.get(ALERT_APPENDER_KEY);
        if (appender != null) {
            ((Logger) LoggerFactory.getLogger(GuestScanService.class)).detachAppender(appender);
            appender.stop();
        }
    }
}
