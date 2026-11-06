package com.athena.app.bdd.steps.system;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.java.zh_tw.當;

import java.time.Duration;
import java.util.List;

/** 系統層模組的 When。 */
public class SystemWhenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    /** 首頁任務卡的 accessibility id 前綴，卡 id 是 {@code home.task.{房號}.card}。 */
    private static final String HOME_TASK_PREFIX = "home.task.";

    /** 房務待認領單卡的 accessibility id 前綴，卡 id 是 {@code pool.card.{房號}}。 */
    private static final String POOL_CARD_PREFIX = "pool.card.";

    private final AppiumWorld world;

    public SystemWhenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 怎麼做：在離線狀態下觸發對應的畫面操作。權威狀態落地：操作一律被 App 擋下，
     * 不會呼叫後端對應 API；後端任務與工務單資料不變。回寫：畫面提示。
     *
     * <p>四個動作各自的入口：開始與延後在任務頁（延後要先選一個原因才按得下確認），
     * 認領在房務待認領的單卡上，開立新工務單走任務頁的報修。
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
                world.tapTestId("hold.reason.請勿打擾", SCREEN_TIMEOUT);
                world.tapTestId("hold.confirm-button", SCREEN_TIMEOUT);
            }
            case "開立新工務單" -> {
                world.ensureOnTaskDetail(staff, onlyTaskRoomOnHome(staff));
                world.tapTestId("task.repair-button", SCREEN_TIMEOUT);
            }
            default -> throw new IllegalArgumentException(
                    "離線時嘗試的動作只支援開始任務｜認領待認領的單｜延後任務｜開立新工務單，收到：" + action);
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
}
