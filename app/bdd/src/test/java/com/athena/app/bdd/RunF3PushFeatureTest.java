package com.athena.app.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ConfigurationParameters;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectDirectories;
import org.junit.platform.suite.api.Suite;

/**
 * 只跑 {@code specs/truth/features/ios-app/推播/}（F3：點開推播的去處）。
 *
 * <p>{@link AppiumWorld} 的 {@code @Before} 會建 Appium session、terminate+launch App、
 * 等測試後門就緒——這些事只有真的要操作模擬器的情境才需要，{@link RunTruthFeaturesTest}
 * 選的是全部 truth feature，其中仍有模組的 step 是 {@code PendingException} 的 stub；
 * 如果把 driver 生命週期掛在共用的 {@code @Before}／{@code @After} 上，每個 pending 情境
 * 都會被迫重開一次模擬器，又慢又沒意義。獨立一個只選推播目錄的 Suite，{@code run-f3.sh}
 * 跑這個 class（{@code -Dtest=RunF3PushFeatureTest}），其他模組的 dry-run 驗收不受影響。
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
