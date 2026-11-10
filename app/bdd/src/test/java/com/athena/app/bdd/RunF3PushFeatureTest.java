package com.athena.app.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ConfigurationParameters;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectDirectories;
import org.junit.platform.suite.api.Suite;

/**
 * 只跑 {@code specs/truth/features/ios-app/推播/}（F3：點開推播的去處）。
 */
@Suite
@IncludeEngines("cucumber")
@SelectDirectories("../../specs/truth/features/ios-app/推播")
@ConfigurationParameters({
        @ConfigurationParameter(key = "cucumber.glue", value = "com.athena.app.bdd"),
        @ConfigurationParameter(key = "cucumber.plugin",
                value = "pretty, html:target/cucumber-reports/f3-push.html, json:target/cucumber-reports/f3-push.json")
})
public class RunF3PushFeatureTest {
}
