/**
 * 推播模組（DSL：{@code specs/truth/features/ios-app/推播/dsl.md}）。
 * Feature：{@code 點開推播的去處.feature}。
 *
 * <p>推播要發給誰、什麼時候發，由後端各模組驗證。App 只驗證點開推播後去哪裡，以及去處畫面
 * 的提示。
 *
 * <p>TODO（決策 7，第一支金絲雀 feature）：本模組是 App 骨架第一波落地 Appium 情境時的
 * 金絲雀對象，用於曝露 {@code firebase_messaging} 在 iOS {@code getInitialMessage()}／
 * terminated 狀態啟動後進背景等已知風險；需覆蓋冷啟動／背景／前景三種點開情境。
 */
package com.athena.app.bdd.steps.push;
