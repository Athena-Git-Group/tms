package com.athena.app.bdd.steps.handover;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.那麼;

/** 房間交辦模組的 Then。 */
public class HandoverThenSteps {

    private final AppiumWorld world;

    public HandoverThenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 必查：任務頁交辦區的項目剛好等於表格，沒有多也沒有少。
     * accessibility id：{@code task.handover.{index}.meta}。
     */
    @那麼("交辦區顯示以下項目：")
    public void handoverShowsItems(DataTable table) {
        throw new PendingException();
    }

    /**
     * 必查：以 accessibility id {@code task.handover.unread-dot} 定位未讀圓點，其可見性符合
     * 可見狀態（顯示＝元件存在且可見，隱藏＝不存在或不可見）；不用文字「未讀／已讀」斷言。
     * 與後端 {@code handover_reads} 一致。
     */
    @那麼("交辦區的未讀標示為 {string}")
    public void handoverUnreadDotVisibility(String visibleOrHidden) {
        throw new PendingException();
    }

    /**
     * 必查：任務頁退回資訊區分成標題行與原因行兩段呈現（UI 可自行決定排列與連接符），
     * 各段文字分別等於表格中的值；與後端 {@code returnInfo.summary} 中對應的兩段一致，
     * 不要求與全文逐字相等。accessibility id：{@code task.status-row.return-info}。
     */
    @那麼("任務頁的退回資訊逐欄顯示如下：")
    public void returnInfoShowsColumns(DataTable table) {
        throw new PendingException();
    }

    /**
     * 必查：任務頁退回資訊區有非空內容；本 truth 不規定提示文案的具體文字（計時與 SOP
     * 勾記歸零已由查房模組的其他 Rule 驗證）。accessibility id：{@code task.status-row.return-info}。
     */
    @那麼("任務頁的退回資訊顯示操作提示內文")
    public void returnInfoShowsActionHint() {
        throw new PendingException();
    }

    /** 必查：交辦區沒有任何來源為主管交代的項目。 */
    @那麼("交辦區沒有主管交代")
    public void handoverHasNoManagerNote() {
        throw new PendingException();
    }
}
