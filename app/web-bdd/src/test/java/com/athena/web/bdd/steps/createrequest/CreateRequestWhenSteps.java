package com.athena.web.bdd.steps.createrequest;

import com.athena.web.bdd.pages.CreateRequestPanel;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.BoundingBox;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.當;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CreateRequestWhenSteps {

    private static final String A_FEW_SPACES = "   ";

    private static final String CHINESE_POOL =
            "天地玄黃宇宙洪荒日月盈昃辰宿列張寒來暑往秋收冬藏閏餘成歲律呂調陽雲騰致雨露結為霜金生麗水玉出崑岡劍號巨闕珠稱夜光"
                    + "果珍李柰菜重芥薑海鹹河淡鱗潛羽翔龍師火帝鳥官人皇始制文字乃服衣裳推位讓國有虞陶唐弔民伐罪周發殷湯坐朝問道垂拱平章";

    private static final double HOLD_RESPONSE_MS = 2_000;

    private final WebWorld world;

    private final CreateRequestScenario scenario;

    private final CreateRequestPanel panel;

    private final ShellFrame shell;

    public CreateRequestWhenSteps(WebWorld world, CreateRequestScenario scenario) {
        this.world = world;
        this.scenario = scenario;
        this.panel = new CreateRequestPanel(world);
        this.shell = new ShellFrame(world);
    }

    @當("{string} 在房號欄依序打 {string}")
    public void typeRoomChars(String staffName, String chars) {
        panel.typeRoomChars(chars);
    }

    @當("{string} 在房號欄輸入 {string}")
    public void enterRoom(String staffName, String roomNo) {
        panel.enterRoom(roomNo);
    }

    @當("{string} 刪掉房號欄的最後一碼")
    public void deleteLastRoomChar(String staffName) {
        panel.deleteLastRoomChar();
    }

    @當("{string} 點建議房號 {string}")
    public void pickSuggestion(String staffName, String roomNo) {
        panel.pickSuggestion(roomNo);
    }

    @當("{string} 依序點以下品項：")
    public void tapItems(String staffName, DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            String item = row.get("品項");
            if (item == null || item.isBlank()) {
                throw new IllegalArgumentException("「依序點以下品項：」每一列都要有「品項」");
            }
            String times = row.get("次數");
            panel.tapItem(item.strip(), times == null || times.isBlank() ? 1 : Integer.parseInt(times.strip()));
        }
    }

    @當("{string} 按品項 {string} 底下的 {string} {int} 次")
    public void pressUnderItem(String staffName, String item, String button, int times) {
        panel.pressUnder(item.strip(), button, times);
    }

    @當("{string} 在其他需求打 {string}")
    public void typeOtherRequest(String staffName, String text) {
        panel.typeOtherRequest(text);
    }

    @當("{string} 在其他需求只打了幾個空白")
    public void typeOnlySpaces(String staffName) {
        panel.typeOtherRequest(A_FEW_SPACES);
    }

    @當("{string} 在其他需求連續打 {int} 個字，再貼上 {int} 個字")
    public void typeThenPaste(String staffName, int typedCount, int pastedCount) {
        if (typedCount + pastedCount > CHINESE_POOL.length()) {
            throw new IllegalArgumentException("打的與貼上的字數加起來最多 " + CHINESE_POOL.length() + " 個字");
        }
        String typed = CHINESE_POOL.substring(0, typedCount);
        String pasted = CHINESE_POOL.substring(typedCount, typedCount + pastedCount);
        scenario.rememberTypedOtherText(typed);
        panel.typeOtherRequest(typed);
        panel.pasteIntoOtherRequest(pasted);
    }

    @當("{string} 清空其他需求")
    public void clearOtherRequest(String staffName) {
        panel.clearOtherRequest();
    }

    @當("{string} 把其他需求改給 {string}")
    public void chooseDept(String staffName, String dept) {
        panel.chooseDept(dept);
    }

    @當("{string} 按代開單面板的送出鈕")
    public void submit(String staffName) {
        panel.submit();
    }

    @當("{string} 點代開單面板外面的暗色區域")
    public void clickBackdrop(String staffName) {
        panel.clickBackdrop();
    }

    @當("{string} 按 {string}，在還沒有結果之前又連按 {int} 次")
    public void pressRepeatedlyBeforeResult(String staffName, String buttonText, int extraPresses) {
        Page page = world.page();
        List<Route> held = new ArrayList<>();
        world.network().handle(page, Network.DESK_CREATE_REQUESTS, held::add);
        int requestsBefore = world.network().sentRequestCount();
        BoundingBox box = panel.submitButton().boundingBox();
        if (box == null || !panel.submitButton().innerText().strip().equals(buttonText.strip())) {
            throw new AssertionError("送出鈕不是「" + buttonText + "」：" + (box == null ? "找不到送出鈕" : panel.submitButton().innerText()));
        }
        double x = box.x + box.width / 2;
        double y = box.y + box.height / 2;
        long start = System.nanoTime();
        pressAt(page, x, y);
        page.waitForCondition(() -> !held.isEmpty(), new Page.WaitForConditionOptions().setTimeout(5_000));
        List<Boolean> disabledAtEachPress = new ArrayList<>();
        for (int i = 0; i < extraPresses; i++) {
            disabledAtEachPress.add(panel.submitLooksDisabled());
            pressAt(page, x, y);
            page.waitForTimeout(100);
        }
        double remaining = HOLD_RESPONSE_MS - (System.nanoTime() - start) / 1_000_000.0;
        if (remaining > 0) {
            page.waitForTimeout(remaining);
        }
        boolean busyWhileWaiting = panel.isOpen() && panel.submitBusy();
        world.network().passThrough(page, Network.DESK_CREATE_REQUESTS);
        for (Route route : held) {
            try {
                route.resume();
            } catch (RuntimeException alreadyGone) {
            }
        }
        shell.settleAfter(() -> { });
        panel.settle();
        int sent = (int) world.network().sentRequestsSince(requestsBefore).stream()
                .filter(request -> request.is(Network.DESK_CREATE_REQUESTS))
                .count();
        scenario.rememberRapidPresses(new CreateRequestScenario.RapidPresses(sent, disabledAtEachPress, busyWhileWaiting));
    }

    private void pressAt(Page page, double x, double y) {
        if (world.profile().touch()) {
            page.touchscreen().tap(x, y);
        } else {
            page.mouse().click(x, y);
        }
    }
}
