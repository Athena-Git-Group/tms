/**
 * 介面根共通句：Given/When/Then 共用的詞彙與通用斷言。
 * DSL：{@code specs/truth/features/ios-app/dsl.md}
 *
 * <p>落點預留：本套件所有 step body 先丟 {@link io.cucumber.java.PendingException}，
 * 等 App 骨架（決策 7 的測試後門）與 Appium driver（決策 5 spike）就位後逐句補上。
 */
package com.athena.app.bdd.steps.common;
