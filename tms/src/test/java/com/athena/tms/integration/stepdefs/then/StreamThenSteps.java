package com.athena.tms.integration.stepdefs.then;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.SseSupport;
import io.cucumber.java.en.Then;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import tools.jackson.databind.JsonNode;

public class StreamThenSteps {
    private static final Duration AWAIT = Duration.ofSeconds(20L);
    private final ScenarioContext ctx;

    public StreamThenSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Then(value="^住客 A 收到自己任務 seq (\\d+) 的 \"created\"$")
    public void guestAReceivesOwnCreated(int seq) throws InterruptedException {
        JsonNode created = this.guest("A").awaitTask((UUID)this.ctx.get("sseTask:A"), "created", AWAIT);
        Assertions.assertEquals((int)seq, (int)created.get("seq").asInt(), (String)"created 是該任務第一筆事件");
    }

    @Then(value="^住客 B 收到自己任務的 \"created\"$")
    public void guestBReceivesOwnCreated() throws InterruptedException {
        UUID taskB = (UUID)this.ctx.get("sseTask:B");
        JsonNode created = this.guest("B").awaitTask(taskB, "created", AWAIT);
        Assertions.assertEquals((Object)taskB.toString(), (Object)created.get("task_id").asText());
    }

    @Then(value="^彼此的串流不出現對方任務的任何事件$")
    public void guestStreamsAreIsolated() throws InterruptedException {
        Thread.sleep(1500L);
        Assertions.assertTrue((boolean)this.guest("A").taskEventsFor((UUID)this.ctx.get("sseTask:B")).isEmpty(), (String)"住客 A 的串流不得出現別訂單（B）的任務（FR-008）");
        Assertions.assertTrue((boolean)this.guest("B").taskEventsFor((UUID)this.ctx.get("sseTask:A")).isEmpty(), (String)"住客 B 的串流不得出現別訂單（A）的任務（FR-008）");
    }

    @Then(value="^「小美」依序收到該單的 \"created\"、\"claimed\"、\"started\"$")
    public void housekeeperReceivesOwnChain() throws InterruptedException {
        SseSupport.SseStreamClient housekeeper = this.staff("小美");
        UUID taskId = (UUID)this.ctx.get("sseTask:hk");
        housekeeper.awaitTask(taskId, "created", AWAIT);
        housekeeper.awaitTask(taskId, "claimed", AWAIT);
        housekeeper.awaitTask(taskId, "started", AWAIT);
    }

    @Then(value="^「阿強」收到工務新單的 \"created\"$")
    public void engineerReceivesDeptPoolCreated() throws InterruptedException {
        this.staff("阿強").awaitTask((UUID)this.ctx.get("sseTask:eng"), "created", AWAIT);
    }

    @Then(value="^兩人的串流互不出現對方部門的任務事件$")
    public void staffStreamsAreIsolatedByDept() throws InterruptedException {
        Thread.sleep(1500L);
        Assertions.assertTrue((boolean)this.staff("阿強").taskEventsFor((UUID)this.ctx.get("sseTask:hk")).isEmpty(), (String)"工務串流不得出現房務部門的任務（非本部門、非本人）");
        Assertions.assertTrue((boolean)this.staff("小美").taskEventsFor((UUID)this.ctx.get("sseTask:eng")).isEmpty(), (String)"房務串流不得出現工務池的任務");
    }

    @Then(value="^住客 A 的連線依序收到 \"init\"、\"resync\"$")
    public void guestConnectionReceivesInitThenResync() throws InterruptedException {
        this.assertInitThenResync(this.guest("A"));
    }

    @Then(value="^住客 A 的 resync 的 refetch 指向 \"([^\"]+)\"$")
    public void guestResyncRefetchTarget(String expectedRefetch) {
        this.assertResyncRefetch(this.guest("A"), expectedRefetch);
    }

    @Then(value="^「小美」的連線依序收到 \"init\"、\"resync\"$")
    public void staffConnectionReceivesInitThenResync() throws InterruptedException {
        this.assertInitThenResync(this.staff("小美"));
    }

    @Then(value="^「小美」的 resync 的 refetch 指向 \"([^\"]+)\"$")
    public void staffResyncRefetchTarget(String expectedRefetch) {
        this.assertResyncRefetch(this.staff("小美"), expectedRefetch);
    }

    private void assertInitThenResync(SseSupport.SseStreamClient client) throws InterruptedException {
        client.awaitNamed("resync", Duration.ofSeconds(5L));
        Assertions.assertEquals(List.of("init", "resync"), client.eventNames().subList(0, 2), (String)"連線一開就該依序收到這兩筆，不必等任何任務事件");
    }

    private void assertResyncRefetch(SseSupport.SseStreamClient client, String expectedRefetch) {
        Assertions.assertEquals((Object)expectedRefetch, (Object)client.resyncData().get("refetch").asText());
    }

    @Then(value="^連續收到兩次 \"heartbeat\" 且 stream_pos 維持 0$")
    public void heartbeatsArriveWithoutTakingPosition() throws InterruptedException {
        SseSupport.SseStreamClient staff = this.staff("小美");
        JsonNode first = staff.awaitNamed("heartbeat", Duration.ofSeconds(5L));
        JsonNode second = staff.awaitNamed("heartbeat", Duration.ofSeconds(5L));
        Assertions.assertTrue((first.has("stream_pos") && second.has("stream_pos") ? 1 : 0) != 0, (String)"心跳帶當前 stream_pos，安靜期也能核對漏沒漏");
        Assertions.assertEquals((long)0L, (long)first.get("stream_pos").asLong(), (String)"沒推過任務事件前心跳的 stream_pos 維持 0——心跳不佔號");
    }

    @Then(value="^住客 A 依序收到 seq 1 到 4 的四筆事件且 stream_pos 逐一遞增$")
    public void fullChainDeliveredInOrder() throws InterruptedException {
        SseSupport.SseStreamClient guest = this.guest("A");
        guest.awaitTask(this.ctx.taskId(), "completed", AWAIT);
        List<JsonNode> delivered = guest.taskEventsFor(this.ctx.taskId());
        Assertions.assertEquals(List.of("created", "claimed", "started", "completed"), delivered.stream().map(e -> e.get("type").asText()).toList(), (String)"同鍵同分區，送達順序＝事件順序");
        Assertions.assertEquals(List.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3), Integer.valueOf(4)), delivered.stream().map(e -> e.get("seq").asInt()).toList());
        List<Long> positions = delivered.stream().map(e -> e.get("stream_pos").asLong()).toList();
        for (int i = 1; i < positions.size(); ++i) {
            Assertions.assertEquals((long)(positions.get(i - 1) + 1L), (Long)positions.get(i), (String)("連線層流水號逐一遞增、無跳號: " + String.valueOf(positions)));
        }
    }

    @Then(value="^該任務推給住客 A 的事件維持 (\\d+) 筆、零重推$")
    public void duplicatesAbsorbed(int expected) {
        Assertions.assertEquals((int)expected, (int)this.guest("A").taskEventsFor(this.ctx.taskId()).size(), (String)"重複事件（同 task_id+seq）不得再推給客戶端");
    }

    @Then(value="^串流收到 \"degraded\" 事件、reason 為 \"([a-z_]+)\"$")
    public void degradedEventArrives(String reason) throws InterruptedException {
        JsonNode degraded = this.staff("小美").awaitNamed("degraded", Duration.ofSeconds(15L));
        Assertions.assertEquals((Object)reason, (Object)degraded.get("reason").asText(), (String)"degraded 事件自述原因，客戶端據此轉 5 秒輪詢");
    }

    private SseSupport.SseStreamClient guest(String label) {
        SseSupport.SseStreamClient client = (SseSupport.SseStreamClient)this.ctx.get("sse:guest:" + label);
        if (client == null) {
            throw new IllegalStateException("住客 " + label + " 尚未開流——Given 步驟漏了");
        }
        return client;
    }

    private SseSupport.SseStreamClient staff(String name) {
        SseSupport.SseStreamClient client = (SseSupport.SseStreamClient)this.ctx.get("sse:staff:" + name);
        if (client == null) {
            throw new IllegalStateException("員工 " + name + " 尚未開流——Given 步驟漏了");
        }
        return client;
    }
}
