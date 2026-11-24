package com.athena.web.bdd.steps.common;

import com.athena.bddsupport.BackendChannel;
import com.athena.web.bdd.WebProfile;
import com.athena.web.bdd.pages.BoardPage;
import com.athena.web.bdd.pages.DetailPanel;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.BrowserProfiles;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.SessionInjector;
import com.athena.web.bdd.support.WebApp;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Page;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CommonGivenSteps {

    static final String COLLEAGUE_ANSWER = "已經回覆住客了，請住客稍候。";

    private final WebWorld world;

    private final ShellFrame shell;

    private final BoardPage board;

    private final DetailPanel detail;

    public CommonGivenSteps(WebWorld world) {
        this.world = world;
        this.shell = new ShellFrame(world);
        this.board = new BoardPage(world);
        this.detail = new DetailPanel(world);
    }

    @假設("今天是 {string}")
    public void todayIs(String monthDay) {
        String[] parts = monthDay.strip().split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("日期要寫成 M/D，收到「" + monthDay + "」");
        }
        LocalDate date = LocalDate.of(2026, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        world.clocks().moveTo(date);
    }

    @假設("現在時間是 {string}")
    public void nowIs(String time) {
        world.clocks().moveTo(LocalTime.parse(time.strip(), DateTimeFormatter.ofPattern("H:mm")));
    }

    @假設("全館有以下的單：")
    public void tasksInHotel(DataTable table) {
        new TaskTableRows(world).createAll(table.asMaps());
    }

    @假設("{string} 已登入櫃檯畫面，停在 {string}")
    public void loggedInAndOn(String staffName, String pageName) {
        shell.loginAndOpen(world.staff(staffName), pageName);
        shell.waitForSidebar(ShellFrame.sidebarOpenByDefault(pageName));
    }

    @假設("{string} 已自己把側欄打開")
    public void openedSidebarHimself(String staffName) {
        shell.openSidebar();
    }

    @假設("{string} 已從側欄換到待派單頁")
    public void switchedToTriageFromSidebar(String staffName) {
        shell.goToFromSidebar("待派單頁");
    }

    @假設("{string} 用的是 {string}")
    public void usesDevice(String staffName, String device) {
        WebProfile target = BrowserProfiles.ofDevice(device);
        if (target == world.profile()) {
            return;
        }
        boolean carrySession = shell.loggedIn();
        Map<String, String> carried = new LinkedHashMap<>();
        String path = null;
        if (carrySession) {
            Page before = world.page();
            for (String key : List.of(SessionInjector.SESSION_KEY, SessionInjector.BOARD_VIEW_KEY)) {
                Object value = before.evaluate("key => sessionStorage.getItem(key)", key);
                if (value != null) {
                    carried.put(key, (String) value);
                }
            }
            path = URI.create(before.url()).getPath();
        }
        world.useProfile(target);
        if (!carrySession) {
            return;
        }
        Page after = world.page();
        carried.forEach((key, value) -> world.session().putSessionItem(after, key, value));
        if (WebApp.BOARD.equals(path)) {
            shell.openAndWait("總覽看板");
        } else if (WebApp.TRIAGE.equals(path)) {
            shell.openAndWait("待派單頁");
        } else {
            world.open(path);
        }
    }

    @假設("{string} 開著 {string} 的詳情面板")
    public void detailPanelOpen(String staffName, String roomNo) {
        if (!shell.loggedIn()) {
            shell.loginAndOpen(world.staff(staffName), "總覽看板");
        } else if (!world.page().url().split("[?#]")[0].endsWith(WebApp.BOARD)) {
            shell.openAndWait("總覽看板");
        }
        detail.openAndWait(world.tasks().idOf(roomNo), roomNo, () -> shell.press(board.card(roomNo)));
    }

    @假設("{string} 的畫面還沒跟上，{string} 已在另一台櫃檯把 {string} 派給 {string}")
    public void colleagueAssignedUnseen(String staffName, String colleague, String roomNo, String deptTerm) {
        world.network().pauseLiveUpdates(world.page());
        world.backend().deskAssign(
                world.tasks().idOf(roomNo), world.staff(colleague), BackendChannel.dept(deptTerm), null);
    }

    @假設("{string} 的畫面還沒跟上，{string} 已在另一台櫃檯當場回答 {string}")
    public void colleagueAnsweredUnseen(String staffName, String colleague, String roomNo) {
        world.network().pauseLiveUpdates(world.page());
        world.backend().deskAnswer(world.tasks().idOf(roomNo), world.staff(colleague), COLLEAGUE_ANSWER);
    }

    @假設("{string} 的畫面還沒跟上，{string} 的住客已自己收回需求")
    public void guestWithdrewUnseen(String staffName, String roomNo) {
        world.network().pauseLiveUpdates(world.page());
        world.backend().guestWithdrawRequest(roomNo, world.tasks().idOf(roomNo));
    }

    @假設("接下來的送出會因為網路中斷而失敗")
    public void nextWritesFailOffline() {
        Page page = world.page();
        for (Network.Api write : Network.DESK_WRITES) {
            world.network().disconnect(page, write);
        }
    }

    @假設("看板資料的回應被延後")
    public void boardResponseDelayed() {
        world.network().hold(world.page(), Network.DESK_BOARD);
    }

    @假設("看板資料的讀取會失敗")
    public void boardLoadFails() {
        world.network().disconnect(world.page(), Network.DESK_BOARD);
    }
}
