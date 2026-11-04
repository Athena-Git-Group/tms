package com.athena.app.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ConfigurationParameters;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectDirectories;
import org.junit.platform.suite.api.Suite;

/**
 * 跑 specs/truth/features/ios-app 的 truth feature（zh-TW Gherkin，不複製進 classpath）。
 * 路徑相對於 Maven module 工作目錄（app/bdd/），比照 tms/ 的
 * {@code com.athena.tms.bdd.RunTruthFeaturesTest}。
 *
 * <p>本輪（iOS simulator runtime 還在下載）只驗收 dry-run 全綠（0 undefined step）：
 *
 * <pre>{@code
 * JAVA_HOME=$(/usr/libexec/java_home -v 25) \
 *   mvn -f app/bdd/pom.xml test -Dcucumber.execution.dry-run=true
 * }</pre>
 */
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
