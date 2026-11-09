package com.athena.app.bdd.steps.handover;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 房間交辦模組的 Then。 */
public class HandoverThenSteps {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final AppiumWorld world;

    public HandoverThenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 必查：任務頁交辦區的項目剛好等於表格，沒有多也沒有少。
     * accessibility id：{@code task.handover.{index}.meta}、{@code task.handover.{index}.content}。
     *
     * <p>「來源／作者／時間」合在 meta 一行上（畫面以 {@code · } 串接），內容另一行，
     * 所以逐欄比對是：meta 等於表格那幾欄接起來的字串（表格沒給時間時只比前綴），
     * content 等於內容欄。表格沒有規定順序，所以逐列去找一個還沒被配走的項目。
     */
    @那麼("交辦區顯示以下項目：")
    public void handoverShowsItems(DataTable table) {
        List<Map<String, String>> expectedRows = table.asMaps(String.class, String.class);
        if (!expectedRows.isEmpty()) {
            world.waitForTestId("task.handover.0.meta", TIMEOUT);
        }

        List<String[]> actual = readHandoverRows();
        if (actual.size() != expectedRows.size()) {
            throw new AssertionError("交辦區的項目數預期 " + expectedRows.size() + " 筆，實際 "
                    + actual.size() + " 筆：" + describe(actual));
        }

        List<String[]> remaining = new ArrayList<>(actual);
        for (Map<String, String> row : expectedRows) {
            String source = requireCell(row, "來源");
            String author = requireCell(row, "作者");
            String content = requireCell(row, "內容");
            String time = row.get("時間");
            String metaPrefix = source + " · " + author;
            String expectedMeta = (time == null || time.isBlank()) ? metaPrefix : metaPrefix + " · " + time;

            String[] match = remaining.stream()
                    .filter(pair -> (time == null || time.isBlank())
                            ? pair[0].startsWith(metaPrefix)
                            : expectedMeta.equals(pair[0]))
                    .filter(pair -> content.equals(pair[1]))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("交辦區找不到符合的項目：來源／作者／時間＝「"
                            + expectedMeta + "」、內容＝「" + content + "」（實際＝" + describe(actual) + "）"));
            remaining.remove(match);
        }
    }

    /**
     * 必查：任務頁上看得出這間房還有沒看過的交辦，而且不用文字「未讀／已讀」斷言。
     * 與後端 {@code handover_reads} 一致。
     *
     * <p>設計原型用兩種方式表達同一件事（`Main.dc.html:1268`、`4341`）：開工前還沒看過
     * 的交辦整塊升到頁面上（{@code task.handover.callout}），其餘情況收進「這間房的
     * 資訊」那一列、左邊掛一顆圓點（{@code task.handover.unread-dot}）。所以「顯示」
     * 是兩者任一成立，「隱藏」是兩者都不在。
     */
    @那麼("交辦區的未讀標示為 {string}")
    public void handoverUnreadDotVisibility(String visibleOrHidden) {
        world.waitForTestId("task.screen", TIMEOUT);
        switch (visibleOrHidden) {
            case "顯示" -> {
                if (world.driver().findElements(world.byTestId("task.handover.callout")).isEmpty()) {
                    world.waitForTestId("task.handover.unread-dot", TIMEOUT);
                }
            }
            case "隱藏" -> {
                world.waitForTestIdGone("task.handover.unread-dot", TIMEOUT);
                world.waitForTestIdGone("task.handover.callout", TIMEOUT);
            }
            default -> throw new IllegalArgumentException(
                    "找不到「" + visibleOrHidden + "」在可見狀態對照表中的權威值（顯示／隱藏）");
        }
    }

    /**
     * 必查：任務頁退回資訊區分成標題行與原因行兩段呈現（UI 可自行決定排列與連接符），
     * 各段文字分別等於表格中的值；與後端 {@code returnInfo.summary} 中對應的兩段一致，
     * 不要求與全文逐字相等。accessibility id：{@code task.status-row.return-info}。
     */
    @那麼("任務頁的退回資訊逐欄顯示如下：")
    public void returnInfoShowsColumns(DataTable table) {
        world.waitForTestId("task.status-row.return-info", TIMEOUT);
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String item = requireCell(row, "項目");
            String expected = requireCell(row, "值");
            String id = switch (item) {
                case "標題" -> "task.status-row.return-info.title";
                case "原因" -> "task.status-row.return-info.reason";
                default -> throw new IllegalArgumentException(
                        "找不到「" + item + "」在退回資訊項目對照表中的權威值（標題／原因）");
            };
            String actual = world.waitForLabelOf(id, expected, TIMEOUT);
            if (!expected.equals(actual)) {
                throw new AssertionError("退回資訊的「" + item + "」段預期等於「" + expected
                        + "」，實際為「" + actual + "」");
            }
        }
    }

    /**
     * 必查：任務頁退回資訊區有非空內容；本 truth 不規定提示文案的具體文字（計時與 SOP
     * 勾記歸零已由查房模組的其他 Rule 驗證）。accessibility id：{@code task.status-row.return-info}。
     */
    @那麼("任務頁的退回資訊顯示操作提示內文")
    public void returnInfoShowsActionHint() {
        world.waitForTestId("task.status-row.return-info.body", TIMEOUT);
        String actual = world.labelOf("task.status-row.return-info.body");
        if (actual == null || actual.isBlank()) {
            throw new AssertionError("退回資訊的操作提示內文預期非空，實際為「" + actual + "」");
        }
    }

    /** 必查：交辦區沒有任何來源為主管交代的項目。 */
    @那麼("交辦區沒有主管交代")
    public void handoverHasNoManagerNote() {
        world.waitForTestId("task.screen", TIMEOUT);
        for (String[] pair : readHandoverRows()) {
            if (pair[0].startsWith("主管交代 · ")) {
                throw new AssertionError("交辦區預期沒有主管交代，實際找到一則：" + pair[0] + "／" + pair[1]);
            }
        }
    }

    /**
     * 掃出交辦區目前有哪幾則，逐則回傳 {@code [meta, content]}。
     *
     * <p>項目數是執行期才知道的，所以先用 id 前綴掃出有哪幾個 {@code .meta} 節點，再按
     * 它們的索引去讀同一則的內容——交辦區一則也沒有時掃出來就是空的，呼叫端自己判讀。
     */
    private List<String[]> readHandoverRows() {
        List<String[]> rows = new ArrayList<>();
        for (String metaId : world.findTestIdsWithPrefix("task.handover.")) {
            if (!metaId.endsWith(".meta")) {
                continue;
            }
            String contentId = metaId.substring(0, metaId.length() - ".meta".length()) + ".content";
            rows.add(new String[] {
                    String.valueOf(world.labelOf(metaId)),
                    String.valueOf(world.labelOf(contentId))});
        }
        return rows;
    }

    private static String describe(List<String[]> rows) {
        return rows.stream().map(pair -> "「" + pair[0] + "／" + pair[1] + "」").toList().toString();
    }

    private static String requireCell(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return value;
    }
}
