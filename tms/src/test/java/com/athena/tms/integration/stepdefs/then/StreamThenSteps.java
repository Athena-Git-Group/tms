package com.athena.tms.integration.stepdefs.then;

import com.athena.tms.common.codes.TaskEventType;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.SseSupport.SseStreamClient;
import io.cucumber.java.en.Then;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StreamThenSteps {

    private static final Duration AWAIT = Duration.ofSeconds(20);

    private final ScenarioContext ctx;

    public StreamThenSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Then("^住客 A 收到自己任務 seq (\\d+) 的 \"created\"$")
    public void guestAReceivesOwnCreated(int seq) throws InterruptedException {
        JsonNode created = guest("A").awaitTask(ctx.get("sseTask:A"), TaskEventType.CREATED, AWAIT);
        assertEquals(seq, created.get("seq").asInt(), "created 是該任務第一筆事件");
    }

    @Then("^住客 B 收到自己任務的 \"created\"$")
    public void guestBReceivesOwnCreated() throws InterruptedException {
        UUID taskB = ctx.get("sseTask:B");
        JsonNode created = guest("B").awaitTask(taskB, TaskEventType.CREATED, AWAIT);
        assertEquals(taskB.toString(), created.get("task_id").asText());
    }

    @Then("^彼此的串流不出現對方任務的任何事件$")
    public void guestStreamsAreIsolated() throws InterruptedException {
        // 兩邊都已收到自己的鈴＝輸送帶已走完一輪；再留一拍，確認錯投的鈴真的不會來
        Thread.sleep(1500);
        assertTrue(guest("A").taskEventsFor(ctx.get("sseTask:B")).isEmpty(),
                "住客 A 的串流不得出現別訂單（B）的任務（FR-008）");
        assertTrue(guest("B").taskEventsFor(ctx.get("sseTask:A")).isEmpty(),
                "住客 B 的串流不得出現別訂單（A）的任務（FR-008）");
    }

    @Then("^「小美」依序收到該單的 \"created\"、\"claimed\"、\"started\"$")
    public void housekeeperReceivesOwnChain() throws InterruptedException {
        SseStreamClient housekeeper = staff("小美");
        UUID taskId = ctx.get("sseTask:hk");
        housekeeper.awaitTask(taskId, TaskEventType.CREATED, AWAIT);
        housekeeper.awaitTask(taskId, TaskEventType.CLAIMED, AWAIT);
        housekeeper.awaitTask(taskId, TaskEventType.STARTED, AWAIT);
    }

    @Then("^「阿強」收到工務新單的 \"created\"$")
    public void engineerReceivesDeptPoolCreated() throws InterruptedException {
        staff("阿強").awaitTask(ctx.get("sseTask:eng"), TaskEventType.CREATED, AWAIT);
    }

    @Then("^兩人的串流互不出現對方部門的任務事件$")
    public void staffStreamsAreIsolatedByDept() throws InterruptedException {
        Thread.sleep(1500);
        assertTrue(staff("阿強").taskEventsFor(ctx.get("sseTask:hk")).isEmpty(),
                "工務串流不得出現房務部門的任務（非本部門、非本人）");
        assertTrue(staff("小美").taskEventsFor(ctx.get("sseTask:eng")).isEmpty(),
                "房務串流不得出現工務池的任務");
    }

    @Then("^住客 A 的連線依序收到 \"init\"、\"resync\"$")
    public void guestConnectionReceivesInitThenResync() throws InterruptedException {
        assertInitThenResync(guest("A"));
    }

    @Then("^住客 A 的 resync 的 refetch 指向 \"([^\"]+)\"$")
    public void guestResyncRefetchTarget(String expectedRefetch) {
        assertResyncRefetch(guest("A"), expectedRefetch);
    }

    @Then("^「小美」的連線依序收到 \"init\"、\"resync\"$")
    public void staffConnectionReceivesInitThenResync() throws InterruptedException {
        assertInitThenResync(staff("小美"));
    }

    @Then("^「小美」的 resync 的 refetch 指向 \"([^\"]+)\"$")
    public void staffResyncRefetchTarget(String expectedRefetch) {
        assertResyncRefetch(staff("小美"), expectedRefetch);
    }

    /**
     * 先用 awaitNamed 確保 resync 真的到了（不必等任何任務事件，連線一開就會收到），
     * 再看 everything 的順序——awaitNamed 只是「等到」，順序斷言要看完整歷史。
     */
    private void assertInitThenResync(SseStreamClient client) throws InterruptedException {
        client.awaitNamed("resync", Duration.ofSeconds(5));
        assertEquals(List.of("init", "resync"), client.eventNames().subList(0, 2),
                "連線一開就該依序收到這兩筆，不必等任何任務事件");
    }

    /** resync 在上一個 Then 已經被 awaitNamed 從佇列取走，這裡要查的是歷史、不能再 await 一次。 */
    private void assertResyncRefetch(SseStreamClient client, String expectedRefetch) {
        assertEquals(expectedRefetch, client.resyncData().get("refetch").asText());
    }

    @Then("^連續收到兩次 \"heartbeat\" 且 stream_pos 維持 0$")
    public void heartbeatsArriveWithoutTakingPosition() throws InterruptedException {
        SseStreamClient staff = staff("小美");
        JsonNode first = staff.awaitNamed("heartbeat", Duration.ofSeconds(5));
        JsonNode second = staff.awaitNamed("heartbeat", Duration.ofSeconds(5));
        assertTrue(first.has("stream_pos") && second.has("stream_pos"),
                "心跳帶當前 stream_pos，安靜期也能核對漏沒漏");
        assertEquals(0, first.get("stream_pos").asLong(),
                "沒推過任務事件前心跳的 stream_pos 維持 0——心跳不佔號");
    }

    @Then("^住客 A 依序收到 seq 1 到 4 的四筆事件且 stream_pos 逐一遞增$")
    public void fullChainDeliveredInOrder() throws InterruptedException {
        SseStreamClient guest = guest("A");
        guest.awaitTask(ctx.taskId(), TaskEventType.COMPLETED, AWAIT);
        List<JsonNode> delivered = guest.taskEventsFor(ctx.taskId());
        assertEquals(List.of(TaskEventType.CREATED, TaskEventType.CLAIMED,
                        TaskEventType.STARTED, TaskEventType.COMPLETED),
                delivered.stream().map(e -> e.get("type").asText()).toList(),
                "同鍵同分區，送達順序＝事件順序");
        assertEquals(List.of(1, 2, 3, 4),
                delivered.stream().map(e -> e.get("seq").asInt()).toList());
        List<Long> positions = delivered.stream().map(e -> e.get("stream_pos").asLong()).toList();
        for (int i = 1; i < positions.size(); i++) {
            assertEquals(positions.get(i - 1) + 1, positions.get(i),
                    "連線層流水號逐一遞增、無跳號: " + positions);
        }
    }

    @Then("^該任務推給住客 A 的事件維持 (\\d+) 筆、零重推$")
    public void duplicatesAbsorbed(int expected) {
        assertEquals(expected, guest("A").taskEventsFor(ctx.taskId()).size(),
                "重複事件（同 task_id+seq）不得再推給客戶端");
    }

    @Then("^串流收到 \"degraded\" 事件、reason 為 \"([a-z_]+)\"$")
    public void degradedEventArrives(String reason) throws InterruptedException {
        JsonNode degraded = staff("小美").awaitNamed("degraded", Duration.ofSeconds(15));
        assertEquals(reason, degraded.get("reason").asText(),
                "degraded 事件自述原因，客戶端據此轉 5 秒輪詢");
    }

    private SseStreamClient guest(String label) {
        SseStreamClient client = ctx.get("sse:guest:" + label);
        if (client == null) {
            throw new IllegalStateException("住客 " + label + " 尚未開流——Given 步驟漏了");
        }
        return client;
    }

    private SseStreamClient staff(String name) {
        SseStreamClient client = ctx.get("sse:staff:" + name);
        if (client == null) {
            throw new IllegalStateException("員工 " + name + " 尚未開流——Given 步驟漏了");
        }
        return client;
    }
}
