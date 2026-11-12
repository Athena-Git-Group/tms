package com.athena.app.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ConfigurationParameters;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectDirectories;
import org.junit.platform.suite.api.Suite;

/**
 * Cucumber 的 junit-platform-engine 在 {@code cucumber.features} 有值時會忽略所有 discovery selector，
 * 所以下面的 {@code @SelectDirectories} 只在沒給這個屬性時生效。
 */
@Suite
@IncludeEngines("cucumber")
@SelectDirectories("../../specs/truth/features/ios-app")
@ConfigurationParameters({
        @ConfigurationParameter(key = "cucumber.glue", value = "com.athena.app.bdd"),
        @ConfigurationParameter(key = "cucumber.plugin",
                value = "pretty, html:target/cucumber-reports/module.html, json:target/cucumber-reports/module.json")
})
public class RunModuleFeatureTest {
}
