package com.athena.web.bdd.steps.shell;

import com.athena.web.bdd.pages.BoardPage;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.BoundingBox;
import io.cucumber.java.zh_tw.當;
import java.util.LinkedHashMap;
import java.util.Map;

/** 「共用外框」模組的 When 句型。 */
public class ShellWhenSteps {

    private final WebWorld world;
    private final ShellFrame shell;
    private final BoardPage board;
    private final LayoutBeforeResize layoutBeforeResize;

    public ShellWhenSteps(WebWorld world, LayoutBeforeResize layoutBeforeResize) {
        this.world = world;
        this.shell = new ShellFrame(world);
        this.board = new BoardPage(world);
        this.layoutBeforeResize = layoutBeforeResize;
    }

    /**
     * 以這位員工登入、開這一頁，側欄和這一頁的預設不同時按一次「打開側欄」或「收合側欄」，
     * 等看板依新的可用寬度重排完。
     */
    @當("{string} 登入後打開 {string}，側欄是 {string} 的")
    public void loginAndOpenWithSidebar(String staffName, String pageName, String sidebar) {
        boolean wantOpen = sidebarOpen(sidebar);
        boolean defaultOpen = ShellFrame.sidebarOpenByDefault(pageName);
        shell.loginAndOpen(world.staff(staffName), pageName);
        shell.waitForSidebar(defaultOpen);
        if (wantOpen != defaultOpen) {
            if (wantOpen) {
                shell.openSidebar();
            } else {
                shell.collapseSidebar();
            }
        }
        shell.waitForLayoutSettled();
    }

    @當("{string} 把看板捲到最右邊")
    public void scrollBoardToRightEnd(String staffName) {
        board.scrollBoardToRightEnd();
    }

    /** 先記下調整前各欄的寬度與各區塊的位置，再把網頁顯示區域調成這個尺寸，等版面重排完。 */
    @當("{string} 把視窗調成 {int} × {int}")
    public void resizeViewport(String staffName, int width, int height) {
        layoutBeforeResize.remember(currentLayout());
        world.page().setViewportSize(width, height);
        shell.waitForLayoutSettled();
    }

    private LayoutBeforeResize.Snapshot currentLayout() {
        Map<String, BoundingBox> blocks = new LinkedHashMap<>();
        putIfVisible(blocks, LayoutBeforeResize.SIDEBAR, shell.sidebar());
        putIfVisible(blocks, LayoutBeforeResize.TOOLBAR, shell.toolbar());
        putIfVisible(blocks, LayoutBeforeResize.TILES, board.tileRow());
        Map<String, Double> columnWidths = Map.of();
        if (board.columnHeaders().count() > 0) {
            blocks.put(LayoutBeforeResize.BOARD, board.boardBodyBox());
            columnWidths = board.columnWidths();
        }
        return new LayoutBeforeResize.Snapshot(columnWidths, blocks);
    }

    private static void putIfVisible(Map<String, BoundingBox> blocks, String name, Locator block) {
        if (block.count() > 0 && block.first().isVisible()) {
            blocks.put(name, block.first().boundingBox());
        }
    }

    private static boolean sidebarOpen(String sidebar) {
        return switch (sidebar.strip()) {
            case "打開" -> true;
            case "收起" -> false;
            default -> throw new IllegalArgumentException("側欄只接受「打開」或「收起」，收到「" + sidebar + "」");
        };
    }
}
