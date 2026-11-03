/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.junit.platform.suite.api.ConfigurationParameter
 *  org.junit.platform.suite.api.ConfigurationParameters
 *  org.junit.platform.suite.api.IncludeEngines
 *  org.junit.platform.suite.api.SelectClasspathResource
 *  org.junit.platform.suite.api.Suite
 */
package com.athena.tms.integration;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ConfigurationParameters;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines(value={"cucumber"})
@SelectClasspathResource(value="features")
@ConfigurationParameters(value={@ConfigurationParameter(key="cucumber.glue", value="com.athena.tms.integration.stepdefs"), @ConfigurationParameter(key="cucumber.plugin", value="pretty, html:target/cucumber-reports/cucumber.html, json:target/cucumber-reports/cucumber.json")})
public class RunCucumberTest {
}
