package com.athena.web.bdd.steps.shell;

import com.microsoft.playwright.options.BoundingBox;
import java.util.Map;

/**
 * 「把視窗調成 {寬} × {高}」在調整之前記下的版面，給同一個場景的 Then 比對「欄有沒有變寬」「排法有沒有換」。
 * Cucumber 每個場景給一個新的，所以 When 與 Then 的步驟類別在建構子收這個類別，拿到的是同一份。
 */
public class LayoutBeforeResize {

    /** 側欄、工具列、數量卡、看板這四個區塊在 {@link Snapshot#blocks()} 裡的名稱。 */
    public static final String SIDEBAR = "側欄";
    public static final String TOOLBAR = "工具列";
    public static final String TILES = "數量卡";
    public static final String BOARD = "看板";

    /**
     * 調整前的版面。
     *
     * @param columnWidths 各欄的寬度，依欄名由左到右
     * @param blocks 看得到的區塊在視窗上的位置與大小；調整前看不到的區塊（例如收起的側欄）不在裡面
     */
    public record Snapshot(Map<String, Double> columnWidths, Map<String, BoundingBox> blocks) {
    }

    private Snapshot snapshot;

    void remember(Snapshot taken) {
        snapshot = taken;
    }

    /** 調整前記下的版面；這個場景還沒調過視窗時直接失敗。 */
    public Snapshot snapshot() {
        if (snapshot == null) {
            throw new IllegalStateException("這個場景還沒有「把視窗調成」的步驟，沒有調整前的版面可以比");
        }
        return snapshot;
    }
}
