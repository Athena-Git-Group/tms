package com.athena.tms.bdd.steps.staffstream;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.OutboxAwait;
import com.athena.tms.bdd.support.StaffStreamClient;
import com.athena.tms.bdd.support.StaffStreamClient.Connection;
import com.athena.tms.bdd.support.StaffStreamClient.Event;
import com.athena.tms.bdd.support.StreamFaults;
import io.cucumber.java.zh_tw.那麼;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 本模組 {@code 員工事件串流/dsl.md} 的 Then 句型。
 * 多帶一個 {@link DbProbe}：訊號的 {@code seq} 要和 {@code task_events} 裡那一筆事件對得上。
 *
 * <p>所有等待都有上限，預設是模組規定的 10 秒；等不到就以斷言失敗收場，不會無限等。
 * 後端每 15 秒對所有連線送的真實心跳可能夾在任何位置，比對先後順序前一律先拿掉。
 */
public class StaffStreamThenSteps {

    /** 模組用語對照：訊號名稱對到 {@code task} 訊號的 {@code type}。 */
    private static final Map<String, String> SIGNAL_TYPE = Map.of(
            "認領", "claimed",
            "派單", "triaged",
            "復原派單", "triage_undone");

    /** 模組用語對照：建議重抓的資料對到 {@code resync.refetch}。 */
    private static final Map<String, String> REFETCH = Map.of(
            "櫃檯看板", "/api/v1/desk/board",
            "部門待認領", "/api/v1/pool");

    /** 模組用語對照：降級原因對到 {@code degraded.reason}。 */
    private static final Map<String, String> DEGRADED_REASON = Map.of(
            "處理事件落後", "kafka_consumer_lag",
            "連不上事件來源", "kafka_unavailable");

    /** 模組用語對照：後端目前的狀態對到 {@code heartbeat.degraded}。 */
    private static final Map<String, Boolean> HEARTBEAT_DEGRADED = Map.of(
            "正常", false,
            "降級", true);

    /** 契約裡 {@code task} 訊號允許出現的欄位；多出任何一欄就代表帶了單的內容。 */
    private static final Set<String> TASK_SIGNAL_FIELDS = Set.of("stream_pos", "task_id", "seq", "type", "occurred_at");

    /** 收得到全館每一張單訊號的角色。 */
    private static final Set<String> HOTEL_WIDE_ROLES = Set.of("frontdesk", "manager", "duty_manager");

    /**
     * 確認「沒有多送一次」時，收到第一則之後再看多久。
     * 重複送出的那一則和第一則出自同一次處理或同一輪重送，本機上一秒內一定到得了。
     */
    private static final Duration DUPLICATE_SETTLE = Duration.ofSeconds(1);

    /** 模組規定：確認收不到時，在全館範圍已經收到之後再多等的時間。 */
    private static final Duration ABSENCE_SETTLE = Duration.ofSeconds(2);

    private final ScenarioWorld world;
    private final StaffStreamClient streamClient;
    private final DbProbe dbProbe;
    private final OutboxAwait outboxAwait;
    private final StreamFaults streamFaults;
    private final ObjectMapper objectMapper;

    public StaffStreamThenSteps(
            ScenarioWorld world,
            StaffStreamClient streamClient,
            DbProbe dbProbe,
            OutboxAwait outboxAwait,
            StreamFaults streamFaults,
            ObjectMapper objectMapper) {
        this.world = world;
        this.streamClient = streamClient;
        this.dbProbe = dbProbe;
        this.outboxAwait = outboxAwait;
        this.streamFaults = streamFaults;
        this.objectMapper = objectMapper;
    }

    /**
     * 保證員工收到的是那一筆事件本身，而且只收到一次、不夾帶單的內容。
     * 先等連線上出現這張單、這種類型的訊號，再拿它的 {@code seq} 回頭對 {@code task_events}；
     * 只看類型的話，同一張單較早的同類事件也會被誤當成這一次的訊號。
     */
    @那麼("{string} 的串流收到 {string} 的 {string} 訊號")
    public void receivedTaskSignal(String staffName, String taskKey, String signal) {
        String type = lookup(SIGNAL_TYPE, signal, "訊號");
        UUID taskId = resolveTaskId(taskKey);
        Connection connection = streamClient.connection(staffName);

        Event received = connection.awaitEvent(
                        event -> isTaskSignal(event, taskId) && type.equals(text(event.data(), "type")),
                        StaffStreamClient.DEFAULT_WAIT)
                .orElseThrow(() -> new AssertionError("「" + staffName + "」的串流 "
                        + StaffStreamClient.DEFAULT_WAIT.toSeconds() + " 秒內沒有收到 " + taskKey + " 的 " + type
                        + "：" + connection.describe()));

        Map<String, Object> event = latestEventOfType(taskId, type)
                .orElseThrow(() -> new AssertionError("task_events 沒有 " + taskKey + " 的 " + type + " 事件"));
        int expectedSeq = ((Number) event.get("seq")).intValue();
        if (!received.data().path("seq").isNumber() || received.data().path("seq").asInt() != expectedSeq) {
            throw new AssertionError(taskKey + " 的 " + type + " 訊號 seq 預期 " + expectedSeq
                    + "（task_events 那一筆），實際 " + received.data());
        }

        List<String> extraFields = new ArrayList<>();
        for (String field : received.data().propertyNames()) {
            if (!TASK_SIGNAL_FIELDS.contains(field)) {
                extraFields.add(field);
            }
        }
        if (!extraFields.isEmpty()) {
            throw new AssertionError("task 訊號不該帶單的內容，多出欄位 " + extraFields + "：" + received.data());
        }

        pause(DUPLICATE_SETTLE);
        long copies = connection.events().stream()
                .filter(e -> isTaskSignal(e, taskId) && e.data().path("seq").asInt() == expectedSeq)
                .count();
        if (copies != 1) {
            throw new AssertionError("同一筆事件（" + taskKey + " seq=" + expectedSeq + "）對「" + staffName
                    + "」的連線送了 " + copies + " 次：" + connection.describe());
        }
    }

    /**
     * 保證這位員工的連線看不到這張單最近的那筆事件。
     * 「沒收到」只有在確定後端已經把這筆事件送給該送的連線之後才有意義，否則只是還沒到。
     *
     * <p>確定送完的方法：這筆事件已從待送清單送進 Kafka，而且串流的事件消費者已經處理完它；
     * 後端是在處理那一則的當下逐一寫給每條連線的，處理完就代表送完。
     * 場景裡若另外開著全館範圍的連線，也等它真的收到這筆事件。之後再多等 2 秒才檢查。
     */
    @那麼("{string} 的串流沒有收到 {string} 的任何訊號")
    public void receivedNothingForTask(String staffName, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        Connection connection = streamClient.connection(staffName);

        Map<String, Object> latest = latestEvent(taskId)
                .orElseThrow(() -> new AssertionError("task_events 沒有 " + taskKey + " 的任何事件，無從確認送出時點"));
        int seq = ((Number) latest.get("seq")).intValue();
        String idempotencyKey = "task:" + taskId + ":seq:" + seq;
        outboxAwait.waitForSent(
                "task_event",
                row -> idempotencyKey.equals(row.get("idempotency_key")),
                taskKey + " seq=" + seq + " 的串流事件");
        streamFaults.awaitProcessed();

        for (Map.Entry<String, Connection> other : streamClient.latestConnections().entrySet()) {
            if (other.getKey().equals(staffName) || !isHotelWide(other.getKey()) || other.getValue().status() != 200) {
                continue;
            }
            boolean seen = other.getValue().awaitEvent(
                            event -> isTaskSignal(event, taskId) && event.data().path("seq").asInt() == seq,
                            StaffStreamClient.DEFAULT_WAIT)
                    .isPresent();
            if (!seen) {
                throw new AssertionError("全館範圍的「" + other.getKey() + "」" + StaffStreamClient.DEFAULT_WAIT.toSeconds()
                        + " 秒內也沒收到 " + taskKey + " seq=" + seq + "，無從確認送出時點：" + other.getValue().describe());
            }
        }

        pause(ABSENCE_SETTLE);
        List<Event> leaked = connection.events().stream().filter(e -> isTaskSignal(e, taskId)).toList();
        if (!leaked.isEmpty()) {
            throw new AssertionError("「" + staffName + "」的串流不該收到 " + taskKey + " 的訊號，卻收到 "
                    + leaked.size() + " 則：" + connection.describe());
        }
    }

    @那麼("{string} 的串流依序收到連線起始與重抓通知，建議重抓的是 {string}")
    public void receivedInitThenResync(String staffName, String data) {
        String refetch = lookup(REFETCH, data, "建議重抓的資料");
        Connection connection = streamClient.connection(staffName);
        if (connection.status() != 200) {
            throw new AssertionError("「" + staffName + "」連線預期 200：" + connection.describe());
        }
        List<Event> firstTwo = awaitNonHeartbeat(connection, 2);

        Event init = firstTwo.get(0);
        if (!"init".equals(init.name()) || !init.data().path("stream_pos").isNumber()
                || init.data().path("stream_pos").asLong() != 0) {
            throw new AssertionError("第一則預期 init（stream_pos=0）：" + connection.describe());
        }
        Event resync = firstTwo.get(1);
        if (!"resync".equals(resync.name())
                || !"connected".equals(text(resync.data(), "reason"))
                || !refetch.equals(text(resync.data(), "refetch"))) {
            throw new AssertionError("第二則預期 resync（reason=connected，refetch=" + refetch + "）："
                    + connection.describe());
        }
    }

    /** 被拒絕的連線拿不到串流，所以「沒有收到任何訊號」就是紀錄是空的。 */
    @那麼("這次連線被拒絕")
    public void connectionRejected() {
        Connection connection = streamClient.lastAttempt();
        if (connection.status() != 403) {
            throw new AssertionError("連線預期 403：" + connection.describe());
        }
        String code = errorCode(connection.errorBody());
        if (!"forbidden".equals(code)) {
            throw new AssertionError("錯誤碼預期 forbidden，實際 " + code + "：" + connection.describe());
        }
        if (!connection.events().isEmpty()) {
            throw new AssertionError("被拒絕的連線不該收到任何訊號：" + connection.describe());
        }
    }

    /** 等到降級訊號出現後再多看一下，確認同一次進入降級沒有送第二則。 */
    @那麼("{string} 的串流收到降級訊號，原因是 {string}")
    public void receivedDegraded(String staffName, String reason) {
        String expected = lookup(DEGRADED_REASON, reason, "降級原因");
        Connection connection = streamClient.connection(staffName);
        Event degraded = connection.awaitEvent("degraded", StaffStreamClient.DEFAULT_WAIT)
                .orElseThrow(() -> new AssertionError("「" + staffName + "」的串流 "
                        + StaffStreamClient.DEFAULT_WAIT.toSeconds() + " 秒內沒有收到 degraded：" + connection.describe()));
        if (!expected.equals(text(degraded.data(), "reason"))) {
            throw new AssertionError("degraded.reason 預期 " + expected + "：" + connection.describe());
        }
        pause(DUPLICATE_SETTLE);
        long count = connection.events().stream().filter(e -> e.name().equals("degraded")).count();
        if (count != 1) {
            throw new AssertionError("同一次進入降級預期只送 1 則 degraded，實際 " + count + " 則：" + connection.describe());
        }
    }

    @那麼("{string} 的串流收到恢復訊號")
    public void receivedRecovered(String staffName) {
        Connection connection = streamClient.connection(staffName);
        boolean arrived = connection.awaitEvent("recovered", StaffStreamClient.DEFAULT_WAIT).isPresent();
        List<String> names = connection.events().stream().map(Event::name).toList();
        int degradedAt = names.indexOf("degraded");
        int recoveredAt = names.lastIndexOf("recovered");
        if (!arrived || degradedAt < 0 || recoveredAt < degradedAt) {
            throw new AssertionError("「" + staffName + "」的串流預期在 degraded 之後收到 recovered（上限 "
                    + StaffStreamClient.DEFAULT_WAIT.toSeconds() + " 秒）：" + connection.describe());
        }
    }

    @那麼("{string} 的串流在重抓通知之後收到降級訊號")
    public void receivedDegradedAfterResync(String staffName) {
        Connection connection = streamClient.connection(staffName);
        List<String> firstThree = awaitNonHeartbeat(connection, 3).stream().map(Event::name).toList();
        if (!firstThree.equals(List.of("init", "resync", "degraded"))) {
            throw new AssertionError("「" + staffName + "」的串流預期依序收到 init、resync、degraded，實際 "
                    + firstThree + "：" + connection.describe());
        }
    }

    /**
     * 真實的定時心跳也可能落在這次觸發的前後；它們反映的是同一個後端狀態，
     * 所以只要求等到一則 {@code degraded} 對得上的心跳，不指定是哪一則。
     */
    @那麼("{string} 的串流收到一則心跳，標示後端目前 {string}")
    public void receivedHeartbeat(String staffName, String state) {
        boolean expected = lookup(HEARTBEAT_DEGRADED, state, "後端狀態");
        Connection connection = streamClient.connection(staffName);
        Optional<Event> heartbeat = connection.awaitEvent(
                event -> event.name().equals("heartbeat")
                        && event.data() != null
                        && event.data().path("degraded").isBoolean()
                        && event.data().path("degraded").asBoolean() == expected,
                StaffStreamClient.DEFAULT_WAIT);
        if (heartbeat.isEmpty()) {
            throw new AssertionError("「" + staffName + "」的串流 " + StaffStreamClient.DEFAULT_WAIT.toSeconds()
                    + " 秒內沒有收到 degraded=" + expected + " 的 heartbeat：" + connection.describe());
        }
    }

    /** 等到連線上拿掉心跳之後至少有 {@code count} 則，回傳前 {@code count} 則；等不到就斷言失敗。 */
    private static List<Event> awaitNonHeartbeat(Connection connection, int count) {
        int[] seen = {0};
        connection.awaitEvent(
                event -> !event.name().equals("heartbeat") && ++seen[0] >= count,
                StaffStreamClient.DEFAULT_WAIT);
        List<Event> nonHeartbeat = connection.events().stream()
                .filter(event -> !event.name().equals("heartbeat"))
                .toList();
        if (nonHeartbeat.size() < count) {
            throw new AssertionError("連線 " + StaffStreamClient.DEFAULT_WAIT.toSeconds() + " 秒內不到 " + count
                    + " 則心跳以外的訊號：" + connection.describe());
        }
        return nonHeartbeat.subList(0, count);
    }

    private static boolean isTaskSignal(Event event, UUID taskId) {
        return event.name().equals("task")
                && event.data() != null
                && taskId.toString().equalsIgnoreCase(text(event.data(), "task_id"));
    }

    private boolean isHotelWide(String staffName) {
        return dbProbe.queryOne("SELECT role FROM staff_accounts WHERE id = ?", UUID.fromString(world.staffId(staffName)))
                .map(row -> HOTEL_WIDE_ROLES.contains(String.valueOf(row.get("role"))))
                .orElse(false);
    }

    private Optional<Map<String, Object>> latestEventOfType(UUID taskId, String type) {
        return dbProbe.queryOne(
                "SELECT seq FROM task_events WHERE task_id = ? AND type = ? ORDER BY seq DESC LIMIT 1", taskId, type);
    }

    private Optional<Map<String, Object>> latestEvent(UUID taskId) {
        return dbProbe.queryOne("SELECT seq FROM task_events WHERE task_id = ? ORDER BY seq DESC LIMIT 1", taskId);
    }

    private UUID resolveTaskId(String key) {
        if (key.startsWith("W-")) {
            return UUID.fromString(world.repairTicketTaskId(key));
        }
        return UUID.fromString(world.taskId(key));
    }

    private String errorCode(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return text(objectMapper.readTree(body), "code");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static <V> V lookup(Map<String, V> table, String chinese, String what) {
        V value = table.get(chinese);
        if (value == null) {
            throw new IllegalArgumentException("未知的" + what + "：" + chinese + "；已知：" + table.keySet());
        }
        return value;
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待串流訊號時被中斷", e);
        }
    }
}
