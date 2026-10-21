package com.athena.tms.integration.stepdefs.hooks;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.athena.tms.service.GuestScanService;
import com.athena.tms.service.RoomRequestRateLimiter;
import com.athena.tms.service.ScanIpRateLimiter;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import org.slf4j.LoggerFactory;

/**
 * 場景隔離：context／容器整個 Suite 共用（起一次 Spring＋Flyway 很貴），
 * 隔離靠「每場景開跑前清業務表、保種子」＋ ScenarioContext reset。
 * 清理走容器管理身分（task_events 對 tms_app 沒有 DELETE 權限——那正是
 * 「事件列不可改不可刪」要驗的事，清理不能走會被自己斷言擋掉的通道）。
 *
 * <p>掃碼／建單兩顆限流器是跨請求的程序內狀態，每場景
 * 開跑前重置，否則限流場景會互相污染。
 */
public class CucumberHooks {

    /** 掃碼告警斷言掛的 ListAppender 存這把 ctx key，@scan-alert 收尾拆掉。 */
    public static final String ALERT_APPENDER_KEY = "alertAppender";

    private final ScenarioContext scenarioContext;
    private final DbSupport db;
    private final ScanIpRateLimiter scanIpRateLimiter;
    private final RoomRequestRateLimiter roomRequestRateLimiter;

    public CucumberHooks(ScenarioContext scenarioContext, DbSupport db,
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

    /** SSE 場景開的連線收尾關閉（斷言中途失敗也不留半開連線拖住下一景）。 */
    @After("@sse")
    public void closeSseClients() {
        java.util.List<com.athena.tms.integration.stepdefs.utils.SseSupport.SseStreamClient> clients =
                scenarioContext.get(com.athena.tms.integration.stepdefs.given.StreamGivenSteps.CLIENTS_KEY);
        if (clients != null) {
            clients.forEach(com.athena.tms.integration.stepdefs.utils.SseSupport.SseStreamClient::close);
            clients.clear();
        }
    }

    /** 整合 SSE 連線與員工連線分列收尾：兩者的客戶端型別不同，不能共用同一個清單。 */
    @After("@pms_integration")
    public void closeIntegrationSseClients() {
        java.util.List<com.athena.tms.integration.stepdefs.utils.IntegrationSseSupport.IntegrationSseClient> clients =
                scenarioContext.get(com.athena.tms.integration.stepdefs.given.IntegrationGivenSteps.SSE_CLIENTS_KEY);
        if (clients != null) {
            clients.forEach(com.athena.tms.integration.stepdefs.utils.IntegrationSseSupport.IntegrationSseClient::close);
            clients.clear();
        }
    }

    /** 掃碼告警場景掛上的日誌監聽收尾拆除（斷言中途失敗也不留掛件）。 */
    @After("@scan-alert")
    public void detachAlertAppender() {
        ListAppender<ILoggingEvent> appender = scenarioContext.get(ALERT_APPENDER_KEY);
        if (appender != null) {
            ((Logger) LoggerFactory.getLogger(GuestScanService.class)).detachAppender(appender);
            appender.stop();
        }
    }
}
