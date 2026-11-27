package com.athena.web.bdd.steps.detail;

import com.athena.web.bdd.pages.DetailPanel;
import com.athena.web.bdd.pages.DetailPanel.PanelBounds;
import com.athena.web.bdd.pages.DetailPanel.PhotoCell;
import com.athena.web.bdd.pages.DetailPanel.ViewerReading;
import com.athena.web.bdd.support.PhotoRequests;
import com.athena.web.bdd.support.WebWorld;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.BoundingBox;
import io.cucumber.java.zh_tw.那麼;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RepairPhotoThenSteps {

    private static final double MIN_TOUCH = 44;

    private static final double EDGE_TOLERANCE = 0.5;

    private static final Pattern RGB = Pattern.compile("rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)(?:,\\s*([\\d.]+))?\\)");

    private final WebWorld world;

    private final DetailPanel detail;

    private final PhotoRequests photoRequests;

    private final Map<UUID, byte[]> backendBytes = new HashMap<>();

    public RepairPhotoThenSteps(WebWorld world, PhotoRequests photoRequests) {
        this.world = world;
        this.detail = new DetailPanel(world);
        this.photoRequests = photoRequests;
    }

    @那麼("詳情面板的 {string} 有 {int} 張縮圖，依序是這張單的照片")
    public void rowShowsTicketPhotos(String rowTitle, int count) {
        Locator row = detail.photoRow(rowTitle);
        row.first().waitFor();
        world.page().waitForCondition(() -> detail.thumbnails(row).count() == count);
        detail.waitPhotosSettled(row);
        String heading = detail.headingOf(row);
        if (!rowTitle.strip().equals(heading)) {
            throw new AssertionError("照片區塊的標題是「" + heading + "」，不是「" + rowTitle + "」");
        }
        List<PhotoCell> cells = detail.photoCells(row);
        List<UUID> ids = deskPhotoIds(rowTitle);
        if (cells.size() != count || ids.size() != count) {
            throw new AssertionError("「" + rowTitle + "」畫面上有 " + cells.size() + " 張縮圖、後端櫃檯詳情有 "
                    + ids.size() + " 張，規格寫的是 " + count + " 張");
        }
        for (int i = 0; i < count; i++) {
            requireTicketPhoto(rowTitle, i + 1, cells.get(i), ids.get(i));
        }
        requireCredentialsOnlyInHeader();
    }

    @那麼("詳情面板的照片區塊排在 {string} 之後、{string} 之前")
    public void rowsBetween(String before, String after) {
        BoundingBox above = sectionBox(before);
        BoundingBox below = sectionBox(after);
        BoundingBox report = rowBox("報修照片");
        BoundingBox completion = rowBox("完修照片");
        if (report.y < above.y + above.height - EDGE_TOLERANCE) {
            throw new AssertionError("「報修照片」沒有排在「" + before + "」下面");
        }
        if (completion.y < report.y + report.height - EDGE_TOLERANCE) {
            throw new AssertionError("「完修照片」沒有排在「報修照片」下面");
        }
        if (completion.y + completion.height > below.y + EDGE_TOLERANCE) {
            throw new AssertionError("「完修照片」沒有排在「" + after + "」上面");
        }
    }

    @那麼("詳情面板沒有 {string} 這一排")
    public void noSuchRow(String rowTitle) {
        if (detail.photoRow(rowTitle).count() > 0) {
            throw new AssertionError("詳情面板上有「" + rowTitle + "」這一排");
        }
        List<UUID> ids = deskPhotoIds(rowTitle);
        if (!ids.isEmpty()) {
            throw new AssertionError("後端櫃檯詳情的「" + rowTitle + "」有 " + ids.size() + " 張照片，不是空的");
        }
    }

    @那麼("詳情面板沒有照片")
    public void noPhotos() {
        if (detail.photoRows().count() > 0) {
            throw new AssertionError("詳情面板上有照片區塊：" + detail.photoRows().allInnerTexts());
        }
        int images = detail.panel().first().locator("img").count();
        if (images > 0) {
            throw new AssertionError("詳情面板上有 " + images + " 張圖片");
        }
        boolean requested = world.network().sentRequests().stream()
                .anyMatch(sent -> sent.path().startsWith(PhotoRequests.CONTENT_PATH));
        if (requested) {
            throw new AssertionError("頁面送出了讀照片的請求");
        }
    }

    @那麼("看大圖顯示 {string} 的第 {int} 張，標示 {string}")
    public void viewerShows(String rowTitle, int nth, String counter) {
        detail.photoViewer().first().waitFor();
        detail.waitViewerImageSettled();
        ViewerReading viewer = detail.readViewer();
        if (!rowTitle.strip().equals(viewer.title())) {
            throw new AssertionError("看大圖的標題是「" + viewer.title() + "」，不是「" + rowTitle + "」");
        }
        if (!counter.strip().equals(viewer.counter())) {
            throw new AssertionError("看大圖標示「" + viewer.counter() + "」，不是「" + counter + "」");
        }
        if (!viewer.imageLoaded()) {
            throw new AssertionError("看大圖沒有載入成功的圖片：" + viewer.text());
        }
        UUID photoId = nthId(rowTitle, nth);
        requireSameBytes(detail.imageBytes(detail.viewerImage()), photoId, "看大圖");
    }

    @那麼("詳情面板 {string} 的第 {int} 張顯示 {string}")
    public void cellShowsText(String rowTitle, int nth, String text) {
        PhotoCell cell = cell(settledRow(rowTitle), rowTitle, nth);
        if (cell.loaded() || !cell.src().isEmpty()) {
            throw new AssertionError("「" + rowTitle + "」第 " + nth + " 張有載入的圖片，不是讀不到");
        }
        if (!text.strip().equals(cell.text())) {
            throw new AssertionError("「" + rowTitle + "」第 " + nth + " 張顯示「" + cell.text() + "」，不是「" + text + "」");
        }
        if (!isGray(cell.background())) {
            throw new AssertionError("「" + rowTitle + "」第 " + nth + " 張的底色是 " + cell.background() + "，不是灰底");
        }
    }

    @那麼("詳情面板 {string} 的第 {int} 張是這張單的照片")
    public void cellIsTicketPhoto(String rowTitle, int nth) {
        PhotoCell cell = cell(settledRow(rowTitle), rowTitle, nth);
        requireTicketPhoto(rowTitle, nth, cell, nthId(rowTitle, nth));
    }

    @那麼("詳情面板的照片區塊標題依序是 {string}、{string}")
    public void rowTitlesInOrder(String report, String completion) {
        Locator rows = detail.photoRows();
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < rows.count(); i++) {
            titles.add(detail.headingOf(rows.nth(i)));
        }
        List<String> expected = List.of(report.strip(), completion.strip());
        if (!expected.equals(titles)) {
            throw new AssertionError("照片區塊的標題依序是 " + titles + "，規格寫的是 " + expected);
        }
        BoundingBox top = rows.nth(0).boundingBox();
        BoundingBox bottom = rows.nth(1).boundingBox();
        if (top.y + top.height > bottom.y + EDGE_TOLERANCE) {
            throw new AssertionError("「" + report + "」沒有排在「" + completion + "」上面");
        }
    }

    @那麼("詳情面板的照片縮圖都在面板寬度內，每張至少 44 × 44")
    public void thumbnailsFitPanel() {
        Locator rows = detail.photoRows();
        if (rows.count() == 0) {
            throw new AssertionError("詳情面板上沒有照片區塊");
        }
        PanelBounds panel = detail.panelBounds();
        if (panel.scrollsSideways()) {
            throw new AssertionError("詳情面板出現橫向捲軸");
        }
        for (int r = 0; r < rows.count(); r++) {
            Locator row = rows.nth(r);
            detail.waitPhotosSettled(row);
            String title = detail.headingOf(row);
            List<PhotoCell> cells = detail.photoCells(row);
            for (int i = 0; i < cells.size(); i++) {
                PhotoCell cell = cells.get(i);
                String where = "「" + title + "」第 " + (i + 1) + " 張";
                if (cell.left() < panel.left() - EDGE_TOLERANCE || cell.right() > panel.right() + EDGE_TOLERANCE) {
                    throw new AssertionError(where + "超出面板左右邊界：縮圖 " + cell.left() + "–" + cell.right()
                            + "，面板 " + panel.left() + "–" + panel.right());
                }
                if (cell.width() < MIN_TOUCH || cell.height() < MIN_TOUCH) {
                    throw new AssertionError(where + "可點的範圍只有 " + cell.width() + " × " + cell.height());
                }
            }
        }
    }

    private Locator settledRow(String rowTitle) {
        Locator row = detail.photoRow(rowTitle);
        row.first().waitFor();
        detail.waitPhotosSettled(row);
        return row;
    }

    private PhotoCell cell(Locator row, String rowTitle, int nth) {
        List<PhotoCell> cells = detail.photoCells(row);
        if (nth < 1 || nth > cells.size()) {
            throw new AssertionError("「" + rowTitle + "」只有 " + cells.size() + " 張縮圖，沒有第 " + nth + " 張");
        }
        return cells.get(nth - 1);
    }

    private void requireTicketPhoto(String rowTitle, int nth, PhotoCell cell, UUID photoId) {
        String where = "「" + rowTitle + "」第 " + nth + " 張";
        if (!cell.loaded()) {
            throw new AssertionError(where + "沒有載入成功的圖片，顯示「" + cell.text() + "」");
        }
        if (!cell.src().startsWith("blob:")) {
            throw new AssertionError(where + "的圖片網址不是 blob:，是「" + cell.src() + "」");
        }
        Locator img = detail.thumbnail(rowTitle, nth).locator("img");
        requireSameBytes(detail.imageBytes(img), photoId, where);
    }

    private void requireSameBytes(byte[] shown, UUID photoId, String where) {
        byte[] expected = backendBytes.computeIfAbsent(photoId,
                id -> world.backend().photoContent(id, RepairPhotos.reader(world)));
        if (!Arrays.equals(shown, expected)) {
            throw new AssertionError(where + "顯示的圖片不是照片 " + photoId + "：畫面 " + shown.length
                    + " bytes，後端 " + expected.length + " bytes");
        }
    }

    private void requireCredentialsOnlyInHeader() {
        List<PhotoRequests.Sent> sent = photoRequests.sent();
        if (sent.isEmpty()) {
            throw new AssertionError("頁面沒有送出讀照片的請求");
        }
        for (PhotoRequests.Sent request : sent) {
            String authorization = request.authorization();
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                throw new AssertionError("讀照片的請求沒有帶 Authorization 標頭：" + request.url());
            }
            String token = authorization.substring("Bearer ".length());
            if (request.url().contains("?") || request.url().contains(token)) {
                throw new AssertionError("讀照片的請求網址帶了查詢字串或登入憑證：" + request.url());
            }
        }
    }

    private UUID openTicketId() {
        return world.tasks().idOf(detail.readHeader().roomNo());
    }

    private List<UUID> deskPhotoIds(String rowTitle) {
        String field = "report".equals(RepairPhotos.purposeOf(rowTitle)) ? "photos" : "completionPhotos";
        JsonNode photos = world.backend()
                .getJson("/desk/tasks/" + openTicketId(), RepairPhotos.reader(world))
                .path("repair").path(field);
        List<UUID> ids = new ArrayList<>();
        photos.forEach(photo -> ids.add(UUID.fromString(photo.path("id").asText())));
        return ids;
    }

    private UUID nthId(String rowTitle, int nth) {
        List<UUID> ids = deskPhotoIds(rowTitle);
        if (nth < 1 || nth > ids.size()) {
            throw new AssertionError("後端櫃檯詳情的「" + rowTitle + "」只有 " + ids.size() + " 張，沒有第 " + nth + " 張");
        }
        return ids.get(nth - 1);
    }

    private BoundingBox sectionBox(String title) {
        Locator section = detail.panel().getByRole(com.microsoft.playwright.options.AriaRole.REGION,
                new Locator.GetByRoleOptions().setName(title.strip()).setExact(true));
        if (section.count() == 0) {
            throw new AssertionError("詳情面板上找不到「" + title + "」區塊");
        }
        return section.first().boundingBox();
    }

    private BoundingBox rowBox(String rowTitle) {
        Locator row = detail.photoRow(rowTitle);
        if (row.count() == 0) {
            throw new AssertionError("詳情面板上找不到「" + rowTitle + "」這一排");
        }
        return row.first().boundingBox();
    }

    private static boolean isGray(String cssColor) {
        Matcher m = RGB.matcher(cssColor);
        if (!m.find()) {
            return false;
        }
        if (m.group(4) != null && Double.parseDouble(m.group(4)) == 0) {
            return false;
        }
        int red = Integer.parseInt(m.group(1));
        int green = Integer.parseInt(m.group(2));
        int blue = Integer.parseInt(m.group(3));
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        return max - min <= 24 && max < 250;
    }
}
