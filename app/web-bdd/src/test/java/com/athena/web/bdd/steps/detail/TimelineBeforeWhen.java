package com.athena.web.bdd.steps.detail;

import com.athena.web.bdd.pages.DetailPanel;
import com.athena.web.bdd.pages.DetailPanel.TimelineRow;
import com.athena.web.bdd.support.WebWorld;
import com.athena.web.bdd.support.WhenStart;
import com.microsoft.playwright.Page;
import io.cucumber.java.Before;
import java.util.List;

public final class TimelineBeforeWhen {

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
