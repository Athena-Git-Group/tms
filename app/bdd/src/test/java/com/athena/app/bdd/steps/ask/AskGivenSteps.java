package com.athena.app.bdd.steps.ask;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.zh_tw.假設;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AskGivenSteps {

    private static final Pattern RECORD =
            Pattern.compile("^(\\d{1,2})/(\\d{1,2})\\s+(.+?)「(.+)」$");

    private final AppiumWorld world;

    public AskGivenSteps(AppiumWorld world) {
        this.world = world;
    }

    @假設("^後端 \"([^\"]+)\" 的房間記錄有 (.+)$")
    public void backendRoomMemoryHas(String roomNo, String record) {
        Matcher matcher = RECORD.matcher(record.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "房間記錄的敘述解不開（預期「{月}/{日} {標題}「{原文}」」）：" + record);
        }
        String title = matcher.group(3).trim();
        OffsetDateTime occurredAt = LocalDate.now()
                .withMonth(Integer.parseInt(matcher.group(1)))
                .withDayOfMonth(Integer.parseInt(matcher.group(2)))
                .atStartOfDay(ZoneId.systemDefault())
                .toOffsetDateTime();
        world.backend().insertRoomMemory(roomNo, memoryKind(title), title, matcher.group(4), occurredAt);
    }

    private static String memoryKind(String title) {
        if (title.startsWith("工務單")) {
            return "repair";
        }
        throw new IllegalArgumentException(
                "找不到標題「" + title + "」在房間記錄種類對照表中的權威值（目前只有「工務單 …」＝修繕）");
    }

    @假設("{string} 在 {string} 剛跳過 SOP 項 {string}")
    public void justSkippedSop(String staffName, String task, String sopItem) {
        UUID taskId = UUID.fromString(String.valueOf(world.taskRowByRoomNo(task).get("id")));
        world.backend().skipSopItem(taskId, world.staff(staffName), sopItem);
    }

    @假設("{string} 已在 {string} 的任務頁打開查一下")
    public void alreadyOpenedAsk(String staffName, String task) {
        world.openAskPanel(world.staff(staffName), task);
    }
}
