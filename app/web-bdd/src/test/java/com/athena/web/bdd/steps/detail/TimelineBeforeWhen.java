package com.athena.web.bdd.steps.detail;

import com.athena.web.bdd.pages.DetailPanel;
import com.athena.web.bdd.pages.DetailPanel.TimelineRow;
import com.athena.web.bdd.support.WebWorld;
import com.athena.web.bdd.support.WhenStart;
import com.microsoft.playwright.Page;
import io.cucumber.java.Before;
import java.util.List;

/**
 * 給比較歷程前後差異的結果句用：它需要知道案例的動作發生之前，詳情面板上列著哪幾筆。
 *
 * <p>場景開始時向 {@link WhenStart} 登記，前置走完、第一個「當」或「那麼」開始之前讀一次面板上的歷程。
 * 那時沒有開著詳情面板的場景不會留任何東西，其他場景只多花一次找面板的時間。
 */
public final class TimelineBeforeWhen {

    /** 面板開著但還在載入時，最多等這麼久再讀。 */
    private static final double LOAD_TIMEOUT_MS = 5_000;

    private final WebWorld world;

    private final WhenStart whenStart;

    private List<TimelineRow> rows;

    private RuntimeException failure;

    public TimelineBeforeWhen(WebWorld world, WhenStart whenStart) {
        this.world = world;
        this.whenStart = whenStart;
    }

    @Before(order = 11)
    public void register() {
        whenStart.alsoCapture(this::capture);
    }

    /** 前置走完時面板上的歷程；那時沒有開著詳情面板，或讀的時候出錯，就直接失敗並說明原因。 */
    public List<TimelineRow> rows() {
        if (failure != null) {
            throw new AssertionError("前置走完時沒讀到詳情面板的歷程：" + failure, failure);
        }
        if (rows == null) {
            throw new AssertionError("前置走完時沒有開著詳情面板，無法比較動作前後的歷程");
        }
        return rows;
    }

    private void capture() {
        try {
            if (!world.hasPage()) {
                return;
            }
            DetailPanel detail = new DetailPanel(world);
            if (detail.panel().count() == 0 || !detail.panel().first().isVisible()) {
                return;
            }
            world.page().waitForCondition(detail::loaded,
                    new Page.WaitForConditionOptions().setTimeout(LOAD_TIMEOUT_MS));
            rows = List.copyOf(detail.timelineRows());
        } catch (RuntimeException e) {
            failure = e;
        }
    }
}
