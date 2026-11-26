package com.athena.web.bdd.steps.detail;

import com.athena.web.bdd.pages.DetailPanel;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.cucumber.java.zh_tw.當;

public class RepairPhotoWhenSteps {

    private final WebWorld world;

    private final ShellFrame shell;

    private final DetailPanel detail;

    public RepairPhotoWhenSteps(WebWorld world) {
        this.world = world;
        this.shell = new ShellFrame(world);
        this.detail = new DetailPanel(world);
    }

    @當("{string} 點詳情面板 {string} 的第 {int} 張縮圖")
    public void pressThumbnail(String staffName, String rowTitle, int nth) {
        RepairPhotos.openViewer(world, detail, shell, rowTitle, nth);
    }

    @當("{string} 在看大圖按 {string}")
    public void pressViewerButton(String staffName, String buttonName) {
        String name = buttonName.strip();
        Locator button = detail.viewerButton(name);
        if ("關閉".equals(name)) {
            shell.press(button);
            detail.photoViewer().first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
            return;
        }
        if (!"上一張".equals(name) && !"下一張".equals(name)) {
            throw new IllegalArgumentException("看大圖的按鈕只有上一張、下一張、關閉，收到「" + buttonName + "」");
        }
        String before = detail.readViewer().counter();
        String previousSrc = detail.viewerImageSrc();
        shell.press(button);
        world.page().waitForCondition(() -> !detail.readViewer().counter().equals(before));
        detail.waitViewerImageSettled(previousSrc);
    }
}
