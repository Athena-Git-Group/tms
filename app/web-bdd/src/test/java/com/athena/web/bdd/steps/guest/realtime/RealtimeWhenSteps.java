package com.athena.web.bdd.steps.guest.realtime;

import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.RequestPage;
import com.athena.web.bdd.pages.guest.SupplyPage;
import com.athena.web.bdd.support.GuestPhone;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.GuestScreen;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Page;
import io.cucumber.java.zh_tw.當;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class RealtimeWhenSteps {

    private static final String FRONTDESK = "林佩君";

    private static final String MANAGER = "張主管";

    private static final String CONFIRM_CANCEL = "取消需求";

    private final WebWorld world;

    private final GuestScenario scenario;

    private final GuestPhone phone;

    private final GuestLive live;

    private final GuestFrame frame;

    public RealtimeWhenSteps(WebWorld world, GuestScenario scenario, GuestLive live) {
        this.world = world;
        this.scenario = scenario;
        this.live = live;
        this.phone = new GuestPhone(world, scenario);
        this.frame = new GuestFrame(world);
    }

    public void staffActs(String staffName, String action, String title) {
        CreatedStaff staff = world.staff(staffName);
        UUID taskId = scenario.request(title);
        List<String> steps = switch (action) {
            case "認領" -> List.of("claim");
            case "開始" -> List.of("start");
            case "完成" -> switch (state(taskId)) {
                case "pool" -> List.of("claim", "start", "complete");
                case "accepted" -> List.of("start", "complete");
                default -> List.of("complete");
            };
            case "查房通過", "驗收通過" -> List.of();
            default -> throw new IllegalArgumentException(
                    "住客規格裡同仁在手機上的動作只有認領、開始、完成、查房通過、驗收通過，收到「" + action + "」");
        };
        live.markWhen();
        switch (action) {
            case "查房通過" -> world.backend().passInspectionAs(taskId, staff);
            case "驗收通過" -> world.backend().verifyRepairTicket(taskId, staff, "passed", null);
            default -> steps.forEach(step -> world.backend().postTaskAction(taskId, step, staff));
        }
    }

    private String state(UUID taskId) {
        return String.valueOf(world.backend().queryRows("SELECT state FROM tasks WHERE tenant_id = ? AND id = ?",
                world.defaults().tenantId(), taskId).get(0).get("state"));
    }

    @當("櫃檯當場回答 {string}，回覆 {string}")
    public void frontdeskAnswers(String title, String answer) {
        UUID taskId = scenario.request(title);
        live.markWhen();
        world.backend().deskAnswer(taskId, world.staff(FRONTDESK), answer);
    }

    @當("櫃檯取消 {string}")
    public void frontdeskCancels(String title) {
        UUID taskId = scenario.request(title);
        live.markWhen();
        world.backend().postTaskAction(taskId, "cancel", world.staff(MANAGER), Map.of("reason", "manager_void"));
    }

    @當("另一支手機在備品補充送出 {string}")
    public void secondPhoneSubmits(String item) {
        onSecondPhone(guestPage -> {
            phone.openScreen(scenario.phoneRoom(), GuestScreen.SUPPLY);
            frame.press("增加" + item);
            live.markWhen(guestPage);
            frame.press(SupplyPage.submitLabel(1));
        });
    }

    @當("另一支手機取消 {string}")
    public void secondPhoneCancels(String title) {
        onSecondPhone(guestPage -> {
            phone.openRequest(scenario.phoneRoom(), title);
            frame.press(RequestPage.CANCEL_BUTTON);
            frame.waitForPressable(CONFIRM_CANCEL);
            live.markWhen(guestPage);
            frame.press(CONFIRM_CANCEL);
        });
    }

    public void connectionLost() {
        live.markWhen();
        live.cutAndBlockReconnect();
    }

    @當("後端要求重新同步")
    public void backendAsksResync() {
        live.markWhen();
        live.sendSignal("resync", "{\"stream_pos\":0,\"reason\":\"server\",\"refetch\":\"/guest/home\"}");
    }

    private void onSecondPhone(Consumer<Page> action) {
        Page guestPage = world.page();
        world.switchTo(scenario.secondPhone());
        try {
            action.accept(guestPage);
        } finally {
            world.switchTo(guestPage);
        }
    }
}
