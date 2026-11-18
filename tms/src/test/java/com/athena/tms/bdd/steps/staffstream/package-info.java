/**
 * 「員工事件串流」的句型。
 *
 * <p>一個檔只收一類句型，幾個人同時補句型時才不會改到同一個檔：
 * {@code StaffStreamGivenSteps}、{@code StaffStreamWhenSteps}、{@code StaffStreamThenSteps}
 * 各收本模組 {@code dsl.md} 的 Given、When、Then。
 *
 * <p>連線與收到的訊號都經過 {@link com.athena.tms.bdd.support.StaffStreamClient}，
 * 讓後端進出降級經過 {@link com.athena.tms.bdd.support.StreamFaults}；
 * 兩者都會在案例結束時自己收尾，句型裡不要另外關線或還原。
 */
package com.athena.tms.bdd.steps.staffstream;
