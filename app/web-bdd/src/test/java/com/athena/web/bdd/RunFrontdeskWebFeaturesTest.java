package com.athena.web.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ConfigurationParameters;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectDirectories;
import org.junit.platform.suite.api.Suite;

/**
 * Cucumber 的 junit-platform-engine 在 {@code cucumber.features} 有值時會忽略所有 discovery selector，
 * 所以下面的 {@code @SelectDirectories} 只在沒給這個屬性時生效。
 *
 * <p>外掛裡的 {@code StepPhaseListener} 不是報表：它讓「後端沒有任何資料被改動」這類要和 When 之前比的結果句
 * 拿得到前置剛走完時的樣子。命令列另外指定 {@code cucumber.plugin} 時要把它一起帶上，否則那幾句一定失敗。
 */
@Suite
@IncludeEngines("cucumber")
@SelectDirectories("../../specs/truth/features/frontdesk-web")
@ConfigurationParameters({
        @ConfigurationParameter(key = "cucumber.glue", value = "com.athena.web.bdd"),
        @ConfigurationParameter(key = "cucumber.plugin",
                value = "pretty, html:target/cucumber-reports/frontdesk-web.html, json:target/cucumber-reports/frontdesk-web.json, "
                        + "com.athena.web.bdd.support.StepPhaseListener")
})
public class RunFrontdeskWebFeaturesTest {
}
