package com.athena.tms.bdd.steps.staffstream;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.ReservationFixtures;
import com.athena.tms.bdd.support.RoomFixtures;
import com.athena.tms.bdd.support.StaffStreamClient;
import com.athena.tms.bdd.support.StreamFaults;
import com.athena.tms.bdd.support.TaskFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.service.PushOutboxWriter;
import com.athena.tms.service.TenantScopedExecutor;
import io.cucumber.java.zh_tw.假設;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** 本模組 {@code 員工事件串流/dsl.md} 的 Given 句型。 */
public class StaffStreamGivenSteps {

    /** 模組用語「處理事件落後超過 10 秒」：送一則時間印在 11 秒前的事件，比門檻多一秒才不會落在邊界上。 */
    static final Duration LAG_OVER_THRESHOLD = Duration.ofSeconds(11);

    private final ScenarioWorld world;
    private final StaffStreamClient streamClient;
    private final StreamFaults streamFaults;
    private final TaskFixtures taskFixtures;
    private final RoomFixtures roomFixtures;
    private final ReservationFixtures reservationFixtures;
    private final TenantFixtures tenantFixtures;
    private final MutableClock clock;
    private final DbProbe dbProbe;
    private final JdbcClient jdbcClient;
    private final PushOutboxWriter pushOutboxWriter;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final ObjectMapper objectMapper;

    public StaffStreamGivenSteps(
            ScenarioWorld world,
            StaffStreamClient streamClient,
            StreamFaults streamFaults,
            TaskFixtures taskFixtures,
            RoomFixtures roomFixtures,
            ReservationFixtures reservationFixtures,
            TenantFixtures tenantFixtures,
            MutableClock clock,
            DbProbe dbProbe,
            JdbcClient jdbcClient,
            PushOutboxWriter pushOutboxWriter,
            TenantScopedExecutor tenantScopedExecutor,
            ObjectMapper objectMapper) {
        this.world = world;
        this.streamClient = streamClient;
        this.streamFaults = streamFaults;
        this.taskFixtures = taskFixtures;
        this.roomFixtures = roomFixtures;
        this.reservationFixtures = reservationFixtures;
        this.tenantFixtures = tenantFixtures;
        this.clock = clock;
        this.dbProbe = dbProbe;
        this.jdbcClient = jdbcClient;
        this.pushOutboxWriter = pushOutboxWriter;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.objectMapper = objectMapper;
    }

    /**
     * 保證後面的步驟開始時，這位員工的連線已經在後端登記好、收得到之後發生的事件。
     * 後端是先送 {@code init} 與 {@code resync} 才算連線就緒，所以兩則都收到才往下走；
     * 不等的話，緊接著的動作可能搶在連線登記之前發生，訊號就永遠收不到。
     */
    @假設("{string} 已連上員工事件串流")
    public void connected(String staffName) {
        StaffStreamClient.Connection connection = streamClient.connect(staffName);
        if (connection.status() != 200) {
            throw new IllegalStateException("「" + staffName + "」連不上員工事件串流：" + connection.describe());
        }
        for (String name : new String[] {"init", "resync"}) {
            if (connection.awaitEvent(name, StaffStreamClient.DEFAULT_WAIT).isEmpty()) {
                throw new IllegalStateException("「" + staffName + "」連上串流後 "
                        + StaffStreamClient.DEFAULT_WAIT.toSeconds() + " 秒內沒有收到 " + name + "：" + connection.describe());
            }
        }
    }

    /**
     * 把後端擺到指定狀態。降級是讓事件消費者看到一則落後超過門檻的事件，並等它處理完，
     * 之後的步驟才能以「後端已在降級」為前提；正常是每個場景開始時的狀態，不必做事。
     */
    @假設("後端目前 {string}")
    public void backendState(String state) {
        switch (state) {
            case "降級" -> {
                streamFaults.reportLag(LAG_OVER_THRESHOLD);
                streamFaults.awaitProcessed();
            }
            case "正常" -> {
                // 場景開始時後端一定是正常的，上一個場景動過的狀態會在它結束時還原。
            }
            default -> throw new IllegalArgumentException("未知的後端狀態：" + state + "；只支援 正常、降級");
        }
    }

    /**
     * 備品單走完建立、認領、開始、完成，完成時間撥到昨天那個時刻；結束時間跟著完成時間，這張單才落在看板範圍之外。
     * 房間另外有一筆昨天入住、明天退房的訂房，單掛在這筆訂房上，之後同房住客才評得了分。
     */
    @假設("{string} 有一張昨天 {string} 就已送達的備品單 {string}")
    public void deliveredYesterday(String roomNo, String hhmm, String content) {
        roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        LocalDate yesterday = today.minusDays(1);
        UUID reservationId = reservationFixtures.upsertReservation(
                roomNo, List.of(), yesterday, LocalTime.of(15, 0), today.plusDays(1), null);
        Instant doneAt = ZonedDateTime.of(yesterday, LocalTime.parse(hhmm), clock.getZone()).toInstant();
        UUID taskId = taskFixtures.builder()
                .roomNo(roomNo)
                .title(content)
                .type("amenity")
                .dept("housekeeping")
                .source("guest")
                .reservationId(reservationId)
                .state("done")
                .assignee(anyActiveStaff("housekeeping"))
                .doneAt(doneAt)
                .workDate(yesterday)
                .create();
        jdbcClient.sql("UPDATE tasks SET ended_at = done_at WHERE id = ?").params(taskId).update();
        world.rememberTask(roomNo, taskId.toString());
    }

    /**
     * 推播經過後端寫推播的同一段程式寫進待送匣，排隊鍵由後端決定；寫入與標成重試用完在同一個交易裡，
     * 送出的一方在交易提交前看不到這一筆，不會搶先把它送掉。
     */
    @假設("{string} 有一則推播重試用完，停在送不出去")
    public void pushExhausted(String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        long tenantId = tenantFixtures.testTenantId();
        String idempotencyKey = "fx-exhausted-push:" + taskId;
        ObjectNode payload = PushOutboxWriter.payload(
                objectMapper, anyActiveStaff("housekeeping"), "unclaimed_reminder_staff", "fx 送不出去的推播");
        PushOutboxWriter.targetPool(payload, "housekeeping");
        tenantScopedExecutor.runAsTenant(tenantId, () -> {
            pushOutboxWriter.enqueue(tenantId, "task:" + taskId, payload, idempotencyKey);
            jdbcClient
                    .sql("UPDATE outbox SET status = 'failed' WHERE kind = 'push' AND idempotency_key = ?")
                    .params(idempotencyKey)
                    .update();
        });
        Map<String, Object> failed = dbProbe
                .queryOne("SELECT aggregate_key, status FROM outbox WHERE kind = 'push' AND idempotency_key = ?", idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("待送匣沒有 " + taskKey + " 的那一則推播"));
        if (!"failed".equals(failed.get("status")) || ("task:" + taskId).equals(failed.get("aggregate_key"))) {
            throw new IllegalStateException(taskKey + " 的推播應是 failed、排隊鍵和單的事件不同，實際 " + failed);
        }
    }

    private UUID anyActiveStaff(String dept) {
        return dbProbe
                .queryOne(
                        "SELECT id FROM staff_accounts WHERE tenant_id = ? AND dept = ? AND active ORDER BY name LIMIT 1",
                        tenantFixtures.testTenantId(), dept)
                .map(row -> (UUID) row.get("id"))
                .orElseThrow(() -> new IllegalStateException("背景沒有在職的 " + dept + " 員工"));
    }

    private UUID resolveTaskId(String key) {
        if (key.startsWith("W-")) {
            return UUID.fromString(world.repairTicketTaskId(key));
        }
        return UUID.fromString(world.taskId(key));
    }
}
