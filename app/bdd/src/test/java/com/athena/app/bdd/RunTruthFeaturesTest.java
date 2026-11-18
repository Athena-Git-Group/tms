package com.athena.app.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ConfigurationParameters;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectDirectories;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectDirectories("../../specs/truth/features/ios-app")
@ConfigurationParameters({
        @ConfigurationParameter(key = "cucumber.glue", value = "com.athena.app.bdd"),
        @ConfigurationParameter(key = "cucumber.plugin",
                value = "pretty, html:target/cucumber-reports/truth.html, json:target/cucumber-reports/truth.json")
})
public class RunTruthFeaturesTest {
}
