package com.athena.tms.bdd.steps.common;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.RoomFixtures;
import com.athena.tms.bdd.support.StaffFixtures;
import com.athena.tms.bdd.support.TaskFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpMethod;

public class CommonGivenSteps {

    private final ScenarioWorld world;
    private final StaffFixtures staffFixtures;
    private final TaskFixtures taskFixtures;
    private final RoomFixtures roomFixtures;
    private final TenantFixtures tenantFixtures;
    private final MutableClock clock;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;

    public CommonGivenSteps(
            ScenarioWorld world,
            StaffFixtures staffFixtures,
            TaskFixtures taskFixtures,
            RoomFixtures roomFixtures,
            TenantFixtures tenantFixtures,
            MutableClock clock,
            BddApiClient apiClient,
            DbProbe dbProbe) {
        this.world = world;
        this.staffFixtures = staffFixtures;
        this.taskFixtures = taskFixtures;
        this.roomFixtures = roomFixtures;
        this.tenantFixtures = tenantFixtures;
        this.clock = clock;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
    }

    @假設("飯店有以下員工：")
    public void hotelHasStaff(DataTable table) {
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String name = requireCell(row, "姓名");
            String deptChinese = requireCell(row, "部門");
            Integer floor = parseIntOrNull(row.get("負責樓層"));
            String shiftEnd = blankToNull(row.get("班次結束"));
            String stateCode = Terms.staffState(row.getOrDefault("狀態", "在職"));
            String roleChinese = blankToNull(row.get("角色"));
            boolean managerLikeDept = "主管".equals(deptChinese) || "值班經理".equals(deptChinese);
            if (managerLikeDept && "員工".equals(roleChinese)) {
                throw new IllegalArgumentException("部門寫「" + deptChinese + "」時角色一定是主管，不能寫員工：" + row);
            }
            if (roleChinese != null && !"主管".equals(roleChinese) && !"員工".equals(roleChinese)) {
                throw new IllegalArgumentException("角色只支援 主管／員工，收到「" + roleChinese + "」：" + row);
            }

            StaffFixtures.CreatedStaff created;
            if (managerLikeDept) {
                created = staffFixtures.createManagerLikeStaff(name, Terms.role(deptChinese), stateCode);
            } else if ("主管".equals(roleChinese)) {
                created = staffFixtures.createDeptManager(name, Terms.dept(deptChinese), floor, shiftEnd, stateCode);
            } else {
                created = staffFixtures.createFieldStaff(name, Terms.dept(deptChinese), floor, shiftEnd, stateCode);
            }
            world.rememberStaff(name, created.id().toString(), created.jwt());
        }
    }

    @假設("現在時間是 {string}")
    public void nowIs(String hhmm) {
        clock.setTodayAt(hhmm);
    }

    @假設("{string} 名下有以下任務：")
    public void staffHasTasks(String staffName, DataTable table) {
        UUID assigneeId = UUID.fromString(world.staffId(staffName));
        java.util.List<Map<String, String>> rows = new java.util.ArrayList<>(table.asMaps(String.class, String.class));
        rows.sort(java.util.Comparator.comparingInt(r -> "進行中".equals(r.get("狀態")) ? 1 : 0));
        for (Map<String, String> row : rows) {
            String roomNo = requireCell(row, "房號");
            String content = requireCell(row, "內容");
            roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));

            String categoryChinese = row.getOrDefault("類別", inferCategoryChinese(content));
            String type = Terms.poolCategory(categoryChinese);
            Integer estMin = parseIntOrNull(row.get("預估分鐘"));
            if (estMin == null) {
                estMin = defaultEstMin(content);
            }
            String stateChinese = requireCell(row, "狀態");
            String state = Terms.taskState(stateChinese);
            Integer workedMinutes = parseIntOrNull(row.get("已做分鐘"));
            String holdReason = blankToNull(row.get("停住原因"));
            Integer redoCount = parseIntOrNull(row.get("重做次數"));

            TaskFixtures.Builder builder = taskFixtures.builder()
                    .roomNo(roomNo)
                    .title(content)
                    .type(type)
                    .dept("housekeeping")
                    .source("desk")
                    .estMin(estMin)
                    .state(state)
                    .assignee(assigneeId);

            if ("doing".equals(state) || "interrupted".equals(state)) {
                builder.workedMinutes(workedMinutes != null ? workedMinutes : 0);
            }
            if (holdReason != null) {
                builder.holdReason(holdReason);
            }
            if (redoCount != null) {
                builder.redoCount(redoCount);
            }
            if ("deferred".equals(state)) {
                builder.deferredBySelf(true);
            }
            if ("inspected".equals(state) && "完成 · 免查房".equals(stateChinese)) {
                builder.inspectionWaived(true);
            }
            String doneAtChinese = blankToNull(row.get("完成時間"));
            if (doneAtChinese != null) {
                if (!"done".equals(state) && !"inspected".equals(state)) {
                    throw new IllegalArgumentException(
                            "「完成時間」只用於待查房／已送達／查房通過／完成 · 免查房，收到狀態「" + stateChinese + "」：" + row);
                }
                String[] parts = doneAtChinese.trim().split("\\s+", 2);
                if (parts.length != 2 || !("今天".equals(parts[0]) || "昨天".equals(parts[0]))) {
                    throw new IllegalArgumentException("「完成時間」格式須為 `今天 HH:mm` 或 `昨天 HH:mm`，收到「" + doneAtChinese + "」");
                }
                LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
                LocalDate doneDate = "昨天".equals(parts[0]) ? today.minusDays(1) : today;
                Instant doneAt = ZonedDateTime.of(doneDate, LocalTime.parse(parts[1]), clock.getZone()).toInstant();
                builder.doneAt(doneAt).workDate(doneDate);
            }

            UUID taskId = builder.create();
            world.rememberTask(roomNo, taskId.toString());
        }
    }

    @假設("房務待認領有以下任務：")
    public void poolHasTasks(DataTable table) {
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String roomNo = blankToNull(row.get("房號"));
            String content = requireCell(row, "內容");
            String effectiveRoomNo = roomNo != null ? roomNo : "公共區域";

            Integer explicitFloor = parseIntOrNull(row.get("樓層"));
            int floor = explicitFloor != null ? explicitFloor
                    : (roomNo != null ? RoomFixtures.floorFromRoomNo(roomNo) : 0);
            roomFixtures.ensureRoom(effectiveRoomNo, floor);

            String categoryChinese = row.getOrDefault("類別", inferCategoryChinese(content));
            String type = Terms.poolCategory(categoryChinese);

            String sourceChinese = row.getOrDefault("來源", "住客掃碼");
            String poolSource = Terms.poolSource(sourceChinese);
            boolean automationRule = "自動化規則".equals(sourceChinese);

            boolean priorityNotify = "優先通知".equals(blankToNull(row.get("標記")));
            String reminderStageChinese = blankToNull(row.get("提醒狀態"));

            String noteOrReason = blankToNull(row.get("交代或原因"));
            String ruleHit = null;
            String returnReason = null;
            String requeueNote = null;
            if (noteOrReason != null) {
                String rulePrefix = "規則引擎命中：";
                if (noteOrReason.startsWith(rulePrefix)) {
                    ruleHit = noteOrReason.substring(rulePrefix.length());
                } else {
                    String[] parts = noteOrReason.split("；", 2);
                    returnReason = parts[0];
                    requeueNote = parts.length > 1 ? parts[1] : null;
                }
            }

            TaskFixtures.Builder builder = taskFixtures.builder()
                    .roomNo(effectiveRoomNo)
                    .title(content)
                    .type(type)
                    .dept("housekeeping")
                    .source(automationRule ? "system" : "desk")
                    .state("pool")
                    .poolSource(poolSource)
                    .priorityNotify(priorityNotify);
            if (ruleHit != null) {
                builder.ruleHit(ruleHit);
            }
            if (returnReason != null) {
                builder.returnReason(returnReason);
            }
            if (requeueNote != null) {
                builder.requeueNote(requeueNote);
            }
            if (reminderStageChinese != null) {
                builder.reminderStage(Terms.reminderStage(reminderStageChinese));
            }

            UUID taskId = builder.create();
            world.rememberTask(roomNo != null ? roomNo : content, taskId.toString());
        }
    }

    @假設("{string} 已停用")
    public void staffDeactivated(String staffName) {
        staffFixtures.deactivate(UUID.fromString(world.staffId(staffName)));
    }

    @假設("飯店的未接提醒門檻如下：")
    public void unclaimedReminderThresholds(DataTable table) {
        StaffFixtures.CreatedStaff manager = staffFixtures.createManagerLikeStaff(
                "系統管理者-" + UUID.randomUUID(), "manager", "active");
        long tenantId = tenantFixtures.testTenantId();

        int cleaningStaffReminded = 10;
        int cleaningManagerNotified = 20;
        int urgentStaffReminded = 3;
        int urgentManagerNotified = 8;

        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String item = requireCell(row, "項目");
            int minutes = Integer.parseInt(requireCell(row, "值").replaceAll("[^0-9]", ""));
            switch (item) {
                case "一般單提醒同仁" -> cleaningStaffReminded = minutes;
                case "一般單通知主管" -> cleaningManagerNotified = minutes;
                case "急件提醒同仁" -> urgentStaffReminded = minutes;
                case "急件通知主管" -> urgentManagerNotified = minutes;
                default -> throw new IllegalArgumentException(
                        "找不到「" + item + "」在未接提醒門檻項目對照表中的權威欄位（原詞：" + item + "）");
            }
        }

        String key = "unclaimed_reminder_minutes";
        String value = String.format(
                "{\"cleaning\":{\"staffReminded\":%d,\"managerNotified\":%d},"
                        + "\"urgent\":{\"staffReminded\":%d,\"managerNotified\":%d}}",
                cleaningStaffReminded, cleaningManagerNotified, urgentStaffReminded, urgentManagerNotified);
        Map<String, Object> body = Map.of("tenantId", tenantId, "key", key, "value", value);
        apiClient.exchange(
                HttpMethod.PUT, "/tenant-configs/" + key, BddApiClient.Auth.staff(manager.jwt()), body, null);
    }

    @假設("{string} 今天沒有打掃負責人")
    public void roomHasNoCleaningAssignee(String roomNo) {
        roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
        long tenantId = tenantFixtures.testTenantId();
        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        long count = dbProbe.count(
                """
                SELECT count(*) FROM tasks t
                  JOIN rooms r ON r.id = t.room_id AND r.tenant_id = t.tenant_id
                 WHERE t.tenant_id = ? AND r.room_no = ? AND t.type = 'cleaning'
                   AND t.work_date = ? AND t.assignee_id IS NOT NULL
                """,
                tenantId, roomNo, today);
        if (count > 0) {
            throw new IllegalStateException(
                    "「" + roomNo + "」今天已經有 " + count + " 筆已指派的打掃任務，與「今天沒有打掃負責人」矛盾");
        }
    }

    private static String inferCategoryChinese(String content) {
        return (content.contains("退房清潔") || content.contains("續住整理")) ? "打掃" : "備品";
    }

    private static int defaultEstMin(String content) {
        if (content.contains("退房清潔")) {
            return 35;
        }
        if (content.contains("續住整理")) {
            return 20;
        }
        return 5;
    }

    private static Integer parseIntOrNull(String s) {
        return (s == null || s.isBlank()) ? null : Integer.parseInt(s.trim());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String requireCell(Map<String, String> row, String column) {
        String v = row.get(column);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return v;
    }
}
