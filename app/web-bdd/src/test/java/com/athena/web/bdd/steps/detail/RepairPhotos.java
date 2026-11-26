package com.athena.web.bdd.steps.detail;

import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.web.bdd.pages.DetailPanel;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.UiWords;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;

final class RepairPhotos {

    /** 預設的櫃檯人員；櫃檯、主管、值班主管讀到的照片內容相同，比對位元組時用誰讀都一樣。 */
    private static final String READER = "林佩君";

    private RepairPhotos() {
    }

    static CreatedStaff reader(WebWorld world) {
        return world.staff(READER);
    }

    /** 區塊名稱（任何一種介面語言）對到後端照片的用途。 */
    static String purposeOf(String rowTitle) {
        return switch (UiWords.traditionalChineseOf(rowTitle)) {
            case "報修照片" -> "report";
            case "完修照片" -> "completion";
            default -> throw new IllegalArgumentException("照片區塊只有報修照片、完修照片，收到「" + rowTitle + "」");
        };
    }

    static void openViewer(WebWorld world, DetailPanel detail, ShellFrame shell, String rowTitle, int nth) {
        Locator row = detail.photoRow(rowTitle);
        row.first().waitFor();
        Locator thumbnail = detail.thumbnail(rowTitle, nth);
        thumbnail.waitFor();
        shell.press(thumbnail);
        detail.photoViewer().first().waitFor();
        detail.waitViewerImageSettled();
    }
}
