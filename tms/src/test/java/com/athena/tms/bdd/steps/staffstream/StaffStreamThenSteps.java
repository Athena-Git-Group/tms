package com.athena.tms.bdd.steps.staffstream;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.DeskApi;
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

public class StaffStreamThenSteps {

    private static final Map<String, String> SIGNAL_TYPE = Map.of(
            "建立", "created",
            "認領", "claimed",
            "開始", "started",
            "派單", "triaged",
            "復原派單", "triage_undone",
            "評分", "rated",
            "班末退回", "returned_to_pool");

    private static final Map<String, String> REFETCH = Map.of(
            "櫃檯看板", "/api/v1/desk/board",
            "部門待認領", "/api/v1/pool");

    private static final Map<String, String> DEGRADED_REASON = Map.of(
            "處理事件落後", "kafka_consumer_lag",
            "連不上事件來源", "kafka_unavailable");

    private static final Map<String, Boolean> HEARTBEAT_DEGRADED = Map.of(
            "正常", false,
            "降級", true);

    private static final Set<String> CARDLESS_TASK_SIGNAL_FIELDS = Set.of("stream_pos", "task_id", "seq", "type", "occurred_at");

    private static final Set<String> HOTEL_WIDE_ROLES = Set.of("frontdesk", "manager", "duty_manager");

    private static final Duration DUPLICATE_SETTLE = Duration.ofSeconds(1);

    private static final Duration ABSENCE_SETTLE = Duration.ofSeconds(2);

    private final ScenarioWorld world;
    private final StaffStreamClient streamClient;
    private final DbProbe dbProbe;
    private final OutboxAwait outboxAwait;
    private final StreamFaults streamFaults;
    private final ObjectMapper objectMapper;
    private final DeskApi deskApi;

    public StaffStreamThenSteps(
            ScenarioWorld world,
            StaffStreamClient streamClient,
            DbProbe dbProbe,
            OutboxAwait outboxAwait,
            StreamFaults streamFaults,
            ObjectMapper objectMapper,
            DeskApi deskApi) {
        this.world = world;
        this.streamClient = streamClient;
        this.dbProbe = dbProbe;
        this.outboxAwait = outboxAwait;
        this.streamFaults = streamFaults;
        this.objectMapper = objectMapper;
        this.deskApi = deskApi;
    }

    @那麼("{string} 收到的 {string} 訊號帶著它在櫃檯看板上的卡片，和他讀到的櫃檯看板上那一張一模一樣")
    public void signalCarriesBoardCard(String staffName, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        JsonNode signal = latestSignal(staffName, taskKey, taskId);
        if (!"upsert".equals(text(signal, "board")) || !signal.path("card").isObject()) {
            throw new AssertionError(taskKey + " 的訊號應帶 board=upsert 與 card：" + signal);
        }
        JsonNode card = signal.get("card");
        int cardSeq = requireInt(signal, "card_seq");
        if (!taskId.toString().equalsIgnoreCase(text(card, "id"))
                || card.path("seq").asInt(-1) != cardSeq
                || cardSeq < signal.path("seq").asInt()
                || text(signal, "business_date") == null) {
            throw new AssertionError(taskKey + " 的訊號卡片應是這張單、card_seq 等於 card.seq 且不小於 seq、帶 business_date：" + signal);
        }
        int eventSeq = taskEventSeq(taskId);
        if (cardSeq != eventSeq) {
            throw new AssertionError(taskKey + " 的卡片步數 " + cardSeq + " 應等於 tasks.event_seq " + eventSeq);
        }

        JsonNode board = readBoard(staffName);
        JsonNode onBoard = boardTask(board, taskId)
                .orElseThrow(() -> new AssertionError("「" + staffName + "」讀到的看板上沒有 " + taskKey + "：" + board));
        if (!onBoard.equals(card)) {
            throw new AssertionError(taskKey + " 的訊號卡片和看板上那一張不同。\n訊號：" + card + "\n看板：" + onBoard);
        }
        if (!text(board, "businessDate").equals(text(signal, "business_date"))) {
            throw new AssertionError("訊號的 business_date " + text(signal, "business_date")
                    + " 應等於看板的 businessDate " + text(board, "businessDate"));
        }
    }

    @那麼("{string} 收到的 {string} 訊號說這張單不在櫃檯看板上")
    public void signalSaysOffBoard(String staffName, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        JsonNode signal = latestSignal(staffName, taskKey, taskId);
        if (!"remove".equals(text(signal, "board")) || signal.has("card") || text(signal, "business_date") == null) {
            throw new AssertionError(taskKey + " 的訊號應帶 board=remove 與 business_date、不帶 card：" + signal);
        }
        int eventSeq = taskEventSeq(taskId);
        if (requireInt(signal, "card_seq") != eventSeq) {
            throw new AssertionError(taskKey + " 的 card_seq 應等於 tasks.event_seq " + eventSeq + "：" + signal);
        }
        JsonNode board = readBoard(staffName);
        if (boardTask(board, taskId).isPresent()) {
            throw new AssertionError("「" + staffName + "」讀到的看板上不該有 " + taskKey + "：" + board);
        }
    }

    @那麼("{string} 收到的 {string} 訊號不帶卡片")
    public void signalCarriesNoCard(String staffName, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        Connection connection = streamClient.connection(staffName);
        List<Event> signals = connection.events().stream().filter(e -> isTaskSignal(e, taskId)).toList();
        if (signals.isEmpty()) {
            throw new AssertionError("「" + staffName + "」沒有收到 " + taskKey + " 的任何訊號：" + connection.describe());
        }
        for (Event signal : signals) {
            List<String> extraFields = new ArrayList<>();
            for (String field : signal.data().propertyNames()) {
                if (!CARDLESS_TASK_SIGNAL_FIELDS.contains(field)) {
                    extraFields.add(field);
                }
            }
            if (!extraFields.isEmpty()) {
                throw new AssertionError("「" + staffName + "」的 task 訊號不該帶卡片，多出欄位 " + extraFields + "：" + signal.data());
            }
        }
    }

    @那麼("{string} 收到的 {string} 訊號一步一步往上加，沒有跳號")
    public void signalsAreConsecutive(String staffName, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        latestSignal(staffName, taskKey, taskId);
        int eventSeq = taskEventSeq(taskId);
        List<JsonNode> signals = streamClient.connection(staffName).events().stream()
                .filter(e -> isTaskSignal(e, taskId))
                .map(Event::data)
                .toList();
        Integer previousSeq = null;
        Integer previousCardSeq = null;
        for (JsonNode signal : signals) {
            int seq = requireInt(signal, "seq");
            int cardSeq = requireInt(signal, "card_seq");
            if (previousSeq != null && seq != previousSeq + 1) {
                throw new AssertionError(taskKey + " 的訊號步數跳號或重複：" + seqsOf(signals));
            }
            if (cardSeq < seq || (previousCardSeq != null && cardSeq < previousCardSeq)) {
                throw new AssertionError(taskKey + " 的 card_seq 應不小於自己的 seq、也不小於前一則：" + signals);
            }
            previousSeq = seq;
            previousCardSeq = cardSeq;
        }
        if (previousSeq == null || previousSeq != eventSeq) {
            throw new AssertionError(taskKey + " 最後一則訊號的步數應是 tasks.event_seq " + eventSeq + "：" + seqsOf(signals));
        }
    }

    private JsonNode latestSignal(String staffName, String taskKey, UUID taskId) {
        int eventSeq = taskEventSeq(taskId);
        Connection connection = streamClient.connection(staffName);
        return connection.awaitEvent(
                        event -> isTaskSignal(event, taskId) && event.data().path("seq").asInt() == eventSeq,
                        StaffStreamClient.DEFAULT_WAIT)
                .map(Event::data)
                .orElseThrow(() -> new AssertionError("「" + staffName + "」的串流 " + StaffStreamClient.DEFAULT_WAIT.toSeconds()
                        + " 秒內沒有收到 " + taskKey + " 第 " + eventSeq + " 步的訊號：" + connection.describe()));
    }

    private JsonNode readBoard(String staffName) {
        ScenarioWorld.ApiResponse response = deskApi.getBoard(staffName);
        if (response.status() != 200 || response.json() == null) {
            throw new AssertionError("「" + staffName + "」讀櫃檯看板預期 200，實際 " + response.status() + "：" + response.body());
        }
        return response.json();
    }

    private static Optional<JsonNode> boardTask(JsonNode board, UUID taskId) {
        for (JsonNode task : board.path("tasks")) {
            if (taskId.toString().equalsIgnoreCase(text(task, "id"))) {
                return Optional.of(task);
            }
        }
        return Optional.empty();
    }

    private int taskEventSeq(UUID taskId) {
        return dbProbe.queryOne("SELECT event_seq FROM tasks WHERE id = ?", taskId)
                .map(row -> ((Number) row.get("event_seq")).intValue())
                .orElseThrow(() -> new AssertionError("查無任務 " + taskId));
    }

    private static int requireInt(JsonNode signal, String field) {
        if (!signal.path(field).isNumber()) {
            throw new AssertionError("訊號缺 " + field + "：" + signal);
        }
        return signal.get(field).asInt();
    }

    private static List<Integer> seqsOf(List<JsonNode> signals) {
        return signals.stream().map(signal -> signal.path("seq").asInt()).toList();
    }

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


        pause(DUPLICATE_SETTLE);
        long copies = connection.events().stream()
                .filter(e -> isTaskSignal(e, taskId) && e.data().path("seq").asInt() == expectedSeq)
                .count();
        if (copies != 1) {
            throw new AssertionError("同一筆事件（" + taskKey + " seq=" + expectedSeq + "）對「" + staffName
                    + "」的連線送了 " + copies + " 次：" + connection.describe());
        }
    }

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
