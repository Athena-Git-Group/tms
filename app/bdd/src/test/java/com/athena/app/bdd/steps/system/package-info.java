/**
 * 系統層模組（DSL：{@code specs/truth/features/ios-app/系統層/dsl.md}），涵蓋跨畫面的系統
 * 狀態元件（離線列、連線異常列）。Feature：{@code 離線模式與連線異常.feature}。
 *
 * <p>離線時工務單本身的待送出佇列行為（保存、冪等重送）由「工務單」模組驗證；本模組只驗證
 * 「離線／連線異常」這個跨畫面狀態本身的呈現與操作封鎖規則。
 */
package com.athena.app.bdd.steps.system;
