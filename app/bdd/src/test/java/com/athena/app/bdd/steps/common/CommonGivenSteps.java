package com.athena.app.bdd.steps.common;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CommonGivenSteps {

    private final AppiumWorld world;

    public CommonGivenSteps(AppiumWorld world) {
        this.world = world;
    }

    @假設("飯店有以下員工：")
    public void hotelHasStaff(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            world.seedStaff(
                    requireCell(row, "姓名"),
                    requireCell(row, "部門"),
                    blankToNull(row.get("角色")),
                    optionalInt(row, "負責樓層"),
                    blankToNull(row.get("班次結束")),
                    blankToNull(row.get("狀態")));
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static Integer optionalInt(Map<String, String> row, String column) {
        String value = row.get(column);
        return (value == null || value.isBlank()) ? null : Integer.valueOf(value.trim());
    }

    private static String requireCell(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return value;
    }

    @假設("{string} 名下有以下任務：")
    public void staffHasTasks(String staffName, DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        List<AppiumWorld.TaskFixture> tasks = rows.stream()
                .map(CommonGivenSteps::toTaskFixture)
                .toList();
        world.seedTasks(staffName, tasks);

        LocalDate today = world.deviceNow().toLocalDate();
        for (Map<String, String> row : rows) {
            String doneAtText = blankToNull(row.get("完成時間"));
            if (doneAtText == null) {
                continue;
            }
            if (!DONE_STATES.contains(row.get("狀態"))) {
                throw new IllegalArgumentException("「完成時間」只用於已完成的狀態（" + DONE_STATES
                        + "），「" + row.get("房號") + "」的狀態是「" + row.get("狀態") + "」");
            }
            Map<String, Object> task = world.taskRowByRoomNo(row.get("房號"));
            world.backend().setTaskDoneAt((UUID) task.get("id"), parseMoment(doneAtText, today));
        }
    }

    private static final List<String> DONE_STATES =
            List.of("待查房", "已送達", "查房通過", "完成 · 免查房");

    private static OffsetDateTime parseMoment(String text, LocalDate today) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("完成時間格式須為「今天 HH:mm」或「昨天 HH:mm」，實際=" + text);
        }
        LocalDate date = switch (parts[0]) {
            case "今天" -> today;
            case "昨天" -> today.minusDays(1);
            default -> throw new IllegalArgumentException("完成時間的日期只收「今天」或「昨天」，實際=" + text);
        };
        return ZonedDateTime.of(date, LocalTime.parse(parts[1]), ZoneId.systemDefault()).toOffsetDateTime();
    }

    private static AppiumWorld.TaskFixture toTaskFixture(Map<String, String> row) {
        Integer elapsedMinutes = optionalInt(row, "已做分鐘");
        String pausedReason = row.get("停住原因");
        if (pausedReason != null && pausedReason.isBlank()) {
            pausedReason = null;
        }
        String category = row.get("類別");
        if (category != null && category.isBlank()) {
            category = null;
        }
        return new AppiumWorld.TaskFixture(
                row.get("房號"),
                row.get("內容"),
                category,
                optionalInt(row, "預估分鐘"),
                row.get("狀態"),
                elapsedMinutes == null ? 0 : elapsedMinutes,
                optionalInt(row, "重做次數"),
                pausedReason,
                "是".equals(row.get("免查房")));
    }

    @假設("現在時間是 {string}")
    public void nowIs(String hhmm) {
        LocalTime time = LocalTime.parse(hhmm);
        OffsetDateTime moment = ZonedDateTime
                .of(LocalDate.now(), time, ZoneId.systemDefault())
                .toOffsetDateTime();
        world.backend().setBackendClock(moment);
        world.postBackdoorJson("/clock", "{\"isoTime\":\"" + moment + "\"}");
    }

    @假設("手機目前離線")
    public void phoneOffline() {
        world.settleDeferredStarts();
        world.goOffline();
    }

    public static final String POOL_DEPT_PREFERENCE_KEY = "pool.dept";

    @假設("{string} 上次在待認領停在 {string}")
    public void lastPoolDeptWas(String managerName, String deptChinese) {
        world.writeLocalPreference(POOL_DEPT_PREFERENCE_KEY, BackendChannel.dept(deptChinese));
        world.restartApp();
        world.ensureLoggedInAs(world.staff(managerName));
    }
}
