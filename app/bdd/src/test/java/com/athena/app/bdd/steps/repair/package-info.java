/**
 * 工務單模組（DSL：{@code specs/truth/features/ios-app/工務單/dsl.md}）。
 * Features：{@code 報修照片與送出.feature}、{@code 工務單房務端生命週期.feature}、
 * {@code 開單提示.feature}。
 *
 * <p>照片壓縮、浮水印、離線暫存與自動重送都是 App 的責任。後端只驗收照片張數、上傳完整性
 * 與冪等。
 */
package com.athena.app.bdd.steps.repair;
