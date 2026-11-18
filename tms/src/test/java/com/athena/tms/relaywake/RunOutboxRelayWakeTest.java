package com.athena.tms.relaywake;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ConfigurationParameters;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * relay 提交即送的測試套件。定時輪詢間隔要拉長到一小時，才分得出訊息是被提交喚醒送出的，
 * 而這個設定會讓其他套件裡直接用 SQL 寫進 outbox 的列送不出去，所以獨立成一個 Spring context。
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("relay-wake")
@ConfigurationParameters({
        @ConfigurationParameter(key = "cucumber.glue", value = "com.athena.tms.relaywake"),
        @ConfigurationParameter(key = "cucumber.plugin",
                value = "pretty, html:target/cucumber-reports/relay-wake.html, json:target/cucumber-reports/relay-wake.json")
})
public class RunOutboxRelayWakeTest {
}
