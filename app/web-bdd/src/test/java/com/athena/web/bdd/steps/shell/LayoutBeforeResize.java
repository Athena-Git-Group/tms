package com.athena.web.bdd.steps.shell;

import com.microsoft.playwright.options.BoundingBox;
import java.util.Map;

public class LayoutBeforeResize {

    public static final String SIDEBAR = "側欄";
    public static final String TOOLBAR = "工具列";
    public static final String TILES = "數量卡";
    public static final String BOARD = "看板";

    public record Snapshot(Map<String, Double> columnWidths, Map<String, BoundingBox> blocks) {
    }

    private Snapshot snapshot;

    void remember(Snapshot taken) {
        snapshot = taken;
    }

    public Snapshot snapshot() {
        if (snapshot == null) {
            throw new IllegalStateException("這個場景還沒有「把視窗調成」的步驟，沒有調整前的版面可以比");
        }
        return snapshot;
    }
}
