package com.athena.app.bdd.steps.system;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.appium.java_client.AppiumBy;
import io.cucumber.java.zh_tw.當;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class SystemWhenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    /** 首頁任務卡的 accessibility id 前綴，卡 id 是 {@code home.task.{房號}.card}。 */
    private static final String HOME_TASK_PREFIX = "home.task.";

    /** 房務待認領單卡的 accessibility id 前綴，卡 id 是 {@code pool.card.{房號}}。 */
    private static final String POOL_CARD_PREFIX = "pool.card.";

    /**
     * 主管三類清單上一列的 accessibility id 前綴，列 id 是 {@code manager.queue.row.{房號｜單號}}
     * （{@code app/mobile/lib/features/manager/manager_ids.dart}）。
     */
    private static final String QUEUE_ROW_PREFIX = "manager.queue.row.";

    /** 查房畫面項目列的 accessibility id 前綴，「通過」那顆是 {@code inspect.item.{key}.pass-button}。 */
    private static final String INSPECT_ITEM_PREFIX = "inspect.item.";

    /** 驗收不通過時寫的原因（dsl.md 系統層 When 的固定文案）；離線擋下後面板裡要保留這段字。 */
    private static final String VERIFY_FAIL_REASON = "水龍頭仍在滴";

    private final AppiumWorld world;

    public SystemWhenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 怎麼做：在離線狀態下觸發對應的畫面操作。權威狀態落地：操作一律被 App 擋下，
     * 不會呼叫後端對應 API（{@code createInspections}、{@code verifyRepairTicket}）；後端任務與
     * 工務單資料不變；不寫入離線佇列；不通過面板裡已寫的原因保留。回寫：畫面提示。
     *
     * <p>員工四個動作各自的入口：開始與延後在任務頁（延後點一列預設原因就是送出），
     * 認領在房務待認領的單卡上，開立新工務單走任務頁的報修。
     *
     * <p>主管三個動作都從主管首頁的格子進清單、依房號或單號點 Given 鋪的那一列：送出查房結果
     * 是待查房清單那一間，把每一項判通過後點送出；驗收通過是待驗收清單上第一張已完修的單，
     * 點實心「通過」；驗收不通過是待驗收清單上完修結果為暫時處理的那一張，點描邊「不通過」
     * 升起面板、寫原因後點送出不通過。待驗收清單同時鋪了兩張單，所以不能拿「清單上唯一那一列」
     * 指認。
     *
     * <p>開立新工務單被擋在入口而不是送出那一步：表單必須先有照片才送得出去，而照片
     * 要上傳到後端才有 id，離線時連第一張都加不進去。擋在入口的話員工一點就知道現在
     * 做不了這件事，不會拍完三張才發現送不出去。
     */
    @當("{string} 在 App 離線時嘗試 {string}")
    public void attemptActionWhileOffline(String staffName, String action) {
        BackendChannel.CreatedStaff staff = world.staff(staffName);
        switch (action) {
            case "開始任務" -> {
                world.ensureOnTaskDetail(staff, onlyTaskRoomOnHome(staff));
                world.tapTestId("task.primary-button", SCREEN_TIMEOUT);
            }
            case "認領待認領的單" -> {
                // 劇本只給了這位員工名下的一筆任務，待認領池是空的，沒有東西可以認領。
                // 池裡有沒有單不是這條 Rule 在驗的事，所以由這裡補一張進去。
                world.ensurePoolTask("309", "退房清潔");
                world.ensureOnPool(staff);
                world.tapTestId(onlyPoolCardId() + ".claim-button", SCREEN_TIMEOUT);
            }
            case "延後任務" -> {
                world.ensureOnTaskDetail(staff, onlyTaskRoomOnHome(staff));
                world.tapTestId("task.defer-button", SCREEN_TIMEOUT);
                // 點一列預設原因就是送出，面板上沒有第二顆確認鈕
                // （`Main.dc.html:4954`）；離線擋在送出那一刻。
                world.tapTestId("hold.reason.請勿打擾", SCREEN_TIMEOUT);
            }
            case "開立新工務單" -> {
                world.ensureOnTaskDetail(staff, onlyTaskRoomOnHome(staff));
                world.tapTestId("task.repair-button", SCREEN_TIMEOUT);
            }
            case "送出查房結果" -> {
                openManagerQueueRow(staff, "inspection", onlyInspectionRoom(), "inspect");
                world.waitForTestId("inspect.submit-button", SCREEN_TIMEOUT);
                for (String passButton : passButtonsOnInspection()) {
                    world.tapTestId(passButton, SCREEN_TIMEOUT);
                }
                world.tapTestId("inspect.submit-button", SCREEN_TIMEOUT);
            }
            case "驗收通過" -> {
                openManagerQueueRow(staff, "verify", firstResolvedTicketNo(), "managerTicket");
                world.tapTestId("managerTicket.pass-button", SCREEN_TIMEOUT);
            }
            case "驗收不通過" -> {
                openManagerQueueRow(staff, "verify", temporaryResolvedTicketNo(), "managerTicket");
                world.tapTestId("managerTicket.fail-button", SCREEN_TIMEOUT);
                world.typeInto("managerTicket.fail-sheet.reason-field", SCREEN_TIMEOUT, VERIFY_FAIL_REASON);
                world.tapTestId("managerTicket.fail-sheet.submit-button", SCREEN_TIMEOUT);
            }
            default -> throw new IllegalArgumentException(
                    "離線時嘗試的動作只支援員工的開始任務｜認領待認領的單｜延後任務｜開立新工務單，"
                            + "與主管的送出查房結果｜驗收通過｜驗收不通過，收到：" + action);
        }
    }

    /**
     * 首頁上那一筆任務的房號。句型沒有帶房號，而劇本只給這位員工一筆任務，所以用畫面上
     * 唯一那張卡指認它；不是剛好一張就代表劇本或測試資料有問題，直接噴錯而不是挑一張用。
     */
    private String onlyTaskRoomOnHome(BackendChannel.CreatedStaff staff) {
        world.ensureLoggedInAs(staff);
        world.waitForTestId("home.screen", SCREEN_TIMEOUT);
        world.waitForTestId("home.progress.cleaned-ratio", SCREEN_TIMEOUT);
        List<String> cards = world.findTestIdsWithPrefix(HOME_TASK_PREFIX).stream()
                .filter(id -> id.endsWith(".card"))
                .toList();
        if (cards.size() != 1) {
            throw new IllegalStateException(
                    "首頁上有 " + cards.size() + " 張任務卡，無法指認劇本說的那一筆：" + cards);
        }
        String id = cards.get(0);
        return id.substring(HOME_TASK_PREFIX.length(), id.length() - ".card".length());
    }

    /** 待認領池裡那一張單卡的 id，理由同 {@link #onlyTaskRoomOnHome}。 */
    private String onlyPoolCardId() {
        List<String> cards = world.findTestIdsWithPrefix(POOL_CARD_PREFIX).stream()
                .filter(id -> !id.substring(POOL_CARD_PREFIX.length()).contains("."))
                .toList();
        if (cards.size() != 1) {
            throw new IllegalStateException(
                    "待認領池裡有 " + cards.size() + " 張單卡，無法指認要認領哪一張：" + cards);
        }
        return cards.get(0);
    }

    /**
     * 從主管首頁點某一類的格子（{@code manager.home.tile.{queue}}）進清單，再點清單上鍵為
     * {@code rowKey}（待查房是房號、待驗收是單號）的那一列，等目標畫面（查房畫面或主管視角的
     * 工務單詳情）出現。
     */
    private void openManagerQueueRow(BackendChannel.CreatedStaff manager, String queueKey, String rowKey,
                                     String targetScreenId) {
        ensureOnManagerHome(manager);
        world.tapUntilVisible("manager.home.tile." + queueKey, "manager.queue", SCREEN_TIMEOUT);
        world.tapUntilVisible(QUEUE_ROW_PREFIX + rowKey, targetScreenId, SCREEN_TIMEOUT);
    }

    /**
     * Given 裡那一間待查房的房號。句型沒帶房號，劇本只鋪一間待查房（清潔任務 state＝done），
     * 不是剛好一間就代表劇本或測試資料有問題，直接噴錯而不是挑一間用。
     */
    private String onlyInspectionRoom() {
        List<Map<String, Object>> rows = world.backend().queryRows("""
                SELECT r.room_no FROM tasks t
                JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                WHERE t.tenant_id = ? AND t.type = 'cleaning' AND t.state = 'done'
                """, world.backend().ensureTenant());
        if (rows.size() != 1) {
            throw new IllegalStateException("後端待查房的房間應恰有 1 間，實際 " + rows.size() + " 間：" + rows);
        }
        return String.valueOf(rows.get(0).get("room_no"));
    }

    /** Given 裡第一張已完修的單（單號最小者），驗收通過點的是它。 */
    private String firstResolvedTicketNo() {
        List<Map<String, Object>> rows = resolvedTicketRows("");
        if (rows.isEmpty()) {
            throw new IllegalStateException("後端沒有已完修的工務單，無從驗收通過");
        }
        return String.valueOf(rows.get(0).get("ticket_no"));
    }

    /** Given 裡完修結果為暫時處理的那一張已完修的單，驗收不通過點的是它；不是剛好一張就噴錯。 */
    private String temporaryResolvedTicketNo() {
        List<Map<String, Object>> rows = resolvedTicketRows(" AND rt.resolution_type = 'temporary'");
        if (rows.size() != 1) {
            throw new IllegalStateException(
                    "後端完修結果為暫時處理的已完修工務單應恰有 1 張，實際 " + rows.size() + " 張：" + rows);
        }
        return String.valueOf(rows.get(0).get("ticket_no"));
    }

    /** 後端已完修（工務單的任務 state＝done）的工務單，依單號排序。 */
    private List<Map<String, Object>> resolvedTicketRows(String extraCondition) {
        return world.backend().queryRows("""
                SELECT rt.ticket_no FROM repair_tickets rt
                JOIN tasks t ON t.tenant_id = rt.tenant_id AND t.id = rt.task_id
                WHERE rt.tenant_id = ? AND t.state = 'done'""" + extraCondition + " ORDER BY rt.ticket_no",
                world.backend().ensureTenant());
    }

    /**
     * 確保 App 已以這位主管登入並停在主管首頁（{@code manager.home}）。登入後就落在這一頁，
     * 前面的句子把畫面推進去（清單、查房、工務單詳情）時照返回鈕退回來，退不回去就點底部
     * 分頁的「首頁」。
     */
    private void ensureOnManagerHome(BackendChannel.CreatedStaff manager) {
        world.ensureLoggedInAs(manager);
        if (!isVisible("manager.home") && !hasBackButton()) {
            world.waitForLabelContaining("首頁", SCREEN_TIMEOUT).click();
        }
        world.ensureOnScreenRoot("manager.home", SCREEN_TIMEOUT);
    }

    private boolean hasBackButton() {
        return !world.driver().findElements(world.byTestId("nav.back-button")).isEmpty()
                || !world.driver().findElements(AppiumBy.accessibilityId("Back")).isEmpty();
    }

    /** 查房畫面上每一個項目列的「通過」鈕 id，照畫面順序。 */
    private List<String> passButtonsOnInspection() {
        return world.findTestIdsWithPrefix(INSPECT_ITEM_PREFIX).stream()
                .filter(id -> id.endsWith(".pass-button"))
                .toList();
    }

    private boolean isVisible(String testId) {
        return !world.driver().findElements(world.byTestId(testId)).isEmpty();
    }

}
