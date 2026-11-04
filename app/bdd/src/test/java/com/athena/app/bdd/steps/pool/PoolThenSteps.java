package com.athena.app.bdd.steps.pool;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.那麼;

/** 待認領模組的 Then。 */
public class PoolThenSteps {

    private final AppiumWorld world;

    public PoolThenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 必查：列表中的單卡順序剛好等於清單，沒有多也沒有少，並與後端回應一致。房號清單以
     * 「、」分隔，純文字（非 quoted string），故用 regex 形式的 step definition 擷取。
     */
    @那麼("^待認領列表依序為 (.+)$")
    public void poolListOrderIs(String roomListText) {
        throw new PendingException();
    }

    /**
     * 必查：有同樓層標示的單卡剛好是這些房號。
     * accessibility id：{@code pool.card.{roomNo}.same-floor-tag}。
     */
    @那麼("^待認領列表中標示同樓層的單為 (.+)$")
    public void poolSameFloorTagsAre(String roomListText) {
        throw new PendingException();
    }

    /**
     * 必查：該單卡逐項顯示表格中的值；「說明」為「規則引擎命中：{規則名}」，與後端
     * {@code ruleHit} 一致。accessibility id：{@code pool.card.{roomNo}.source-tag}、
     * {@code pool.card.{roomNo}.handoff-progress}、{@code pool.card.{roomNo}.rule-hit-tag}。
     */
    @那麼("{string} 的單卡顯示以下資訊：")
    public void poolCardShowsInfo(String task, DataTable table) {
        throw new PendingException();
    }

    /**
     * 必查：該單卡的位置顯示「公共區域」而不是房號；後端該任務沒有房號。
     * accessibility id：{@code pool.card.common-area-label}（ui-plan.md 標記「待原型補畫面」）。
     */
    @那麼("{string} 的單卡位置顯示 {string}")
    public void poolCardLocationShows(String task, String location) {
        throw new PendingException();
    }

    /**
     * 必查：分類標籤逐列顯示類別與筆數，並與後端回應一致。
     * accessibility id：{@code pool.category.{category}.chip}、{@code pool.category.{category}.count}。
     */
    @那麼("待認領分類標籤顯示如下：")
    public void poolCategoryChipsShow(DataTable table) {
        throw new PendingException();
    }

    /**
     * 必查：待認領列表區顯示該空狀態文字，搶單動作不可用；後端該部門沒有在職員工
     * （待開通與停用的員工不能搶單）。
     * accessibility id：{@code pool.empty-state--no-active-staff}（ui-plan.md 標記「待原型補畫面」）。
     */
    @那麼("待認領顯示空狀態 {string}")
    public void poolShowsEmptyState(String message) {
        throw new PendingException();
    }

    /**
     * 必查：該單卡顯示此標籤文字，與後端未接提醒所到達的階段一致。
     * accessibility id：{@code pool.card.{roomNo}.status-tag--unaccepted-notified-peer}／
     * {@code pool.card.{roomNo}.status-tag--unaccepted-notified-manager}。
     */
    @那麼("{string} 的單卡標示為 {string}")
    public void poolCardLabeledAs(String task, String label) {
        throw new PendingException();
    }
}
