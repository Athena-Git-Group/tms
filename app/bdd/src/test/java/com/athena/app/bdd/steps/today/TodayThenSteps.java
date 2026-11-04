package com.athena.app.bdd.steps.today;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.那麼;

/** 今日進度模組的 Then（Rule：首頁顯示後端算出的今日進度／依推薦順序列出待開始的單／顯示連續零退回天數）。 */
public class TodayThenSteps {

    private final AppiumWorld world;

    public TodayThenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 必查：首頁進度區逐項顯示表格中的值，並與後端今日進度回應一致。
     * accessibility id：{@code home.progress.cleaned-ratio}、{@code home.progress.remaining-minutes}、
     * {@code home.progress.inspected-count}、{@code home.progress.ad-hoc-count}。
     */
    @那麼("首頁的今日進度顯示如下：")
    public void homeProgressShows(DataTable table) {
        throw new PendingException();
    }

    /**
     * 必查：首頁待開始區的任務卡順序剛好等於清單，並與後端推薦順序一致。
     * accessibility id：{@code home.next-task.card}。房號清單以「、」分隔，純文字（非 quoted
     * string），故用 regex 形式的 step definition 擷取。
     */
    @那麼("^首頁待開始的單依序為 (.+)$")
    public void homeNextTasksOrder(String roomListText) {
        throw new PendingException();
    }

    /**
     * 必查：首頁有連續零退回天數，數值與後端回應一致。計算定義待 clarify（見 feature 內
     * 「[need clarification]」註記：連續 N 天零退回怎麼算）。
     * accessibility id：{@code home.streak-badge}（ui-plan.md 標記「待補畫面後才掛」）。
     */
    @那麼("首頁顯示連續零退回天數")
    public void homeShowsZeroReturnStreak() {
        throw new PendingException();
    }
}
