package com.athena.app.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ConfigurationParameters;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectDirectories;
import org.junit.platform.suite.api.Suite;

/**
 * 只跑單一模組目錄的 truth feature，目錄由系統屬性 {@code cucumber.features} 指定：
 *
 * <pre>{@code
 * mvn -f app/bdd/pom.xml test -Dtest=RunModuleFeatureTest \
 *   -Dsurefire.excludeJUnit5Engines=cucumber \
 *   -Dcucumber.features=../../specs/truth/features/ios-app/查一下
 * }</pre>
 *
 * <p>為什麼不用 {@link SelectDirectories} 挑模組：它是編譯期註解，沒辦法在執行時換
 * 目錄。Cucumber 的 junit-platform-engine 另外認得 {@code cucumber.features} 組態屬性
 * （JUnit Platform 會把系統屬性當 configuration parameter 傳進引擎），屬性一旦有值，
 * 引擎就改抓屬性指定的路徑、**忽略所有 discovery selector**——所以下面的
 * {@code @SelectDirectories} 只是沒給屬性時的後備（行為等同
 * {@link RunTruthFeaturesTest} 的全量），給了屬性就只跑那個模組。路徑相對於
 * Maven module 工作目錄（app/bdd/），比照既有 runner。
 *
 * <p>附帶的 {@code -Dsurefire.excludeJUnit5Engines=cucumber} 不能省：屬性讓 Cucumber
 * 引擎無視 selector，所以 Surefire 自己那層 launcher 也會「直接」再發現一次同一批
 * feature（場景被跑兩遍）；把頂層的 cucumber 引擎排除掉，只留 Suite 內嵌的那一份。
 *
 * <p>與 {@link RunF3PushFeatureTest} 同理，單模組實跑才需要 Appium session；
 * 分模組腳本 {@code run-module.sh <模組目錄名>} 跑這個 class。
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
