package com.athena.tms.relaywake;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ConfigurationParameters;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

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
