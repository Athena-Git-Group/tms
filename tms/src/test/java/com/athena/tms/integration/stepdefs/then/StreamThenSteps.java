/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.cucumber.java.en.Then
 *  org.junit.jupiter.api.Assertions
 *  tools.jackson.databind.JsonNode
 */
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

    @Then(value="^\u4f4f\u5ba2 A \u6536\u5230\u81ea\u5df1\u4efb\u52d9 seq (\\d+) \u7684 \"created\"$")
    public void guestAReceivesOwnCreated(int seq) throws InterruptedException {
        JsonNode created = this.guest("A").awaitTask((UUID)this.ctx.get("sseTask:A"), "created", AWAIT);
        Assertions.assertEquals((int)seq, (int)created.get("seq").asInt(), (String)"created \u662f\u8a72\u4efb\u52d9\u7b2c\u4e00\u7b46\u4e8b\u4ef6");
    }

    @Then(value="^\u4f4f\u5ba2 B \u6536\u5230\u81ea\u5df1\u4efb\u52d9\u7684 \"created\"$")
    public void guestBReceivesOwnCreated() throws InterruptedException {
        UUID taskB = (UUID)this.ctx.get("sseTask:B");
        JsonNode created = this.guest("B").awaitTask(taskB, "created", AWAIT);
        Assertions.assertEquals((Object)taskB.toString(), (Object)created.get("task_id").asText());
    }

    @Then(value="^\u5f7c\u6b64\u7684\u4e32\u6d41\u4e0d\u51fa\u73fe\u5c0d\u65b9\u4efb\u52d9\u7684\u4efb\u4f55\u4e8b\u4ef6$")
    public void guestStreamsAreIsolated() throws InterruptedException {
        Thread.sleep(1500L);
        Assertions.assertTrue((boolean)this.guest("A").taskEventsFor((UUID)this.ctx.get("sseTask:B")).isEmpty(), (String)"\u4f4f\u5ba2 A \u7684\u4e32\u6d41\u4e0d\u5f97\u51fa\u73fe\u5225\u8a02\u55ae\uff08B\uff09\u7684\u4efb\u52d9\uff08FR-008\uff09");
        Assertions.assertTrue((boolean)this.guest("B").taskEventsFor((UUID)this.ctx.get("sseTask:A")).isEmpty(), (String)"\u4f4f\u5ba2 B \u7684\u4e32\u6d41\u4e0d\u5f97\u51fa\u73fe\u5225\u8a02\u55ae\uff08A\uff09\u7684\u4efb\u52d9\uff08FR-008\uff09");
    }

    @Then(value="^\u300c\u5c0f\u7f8e\u300d\u4f9d\u5e8f\u6536\u5230\u8a72\u55ae\u7684 \"created\"\u3001\"claimed\"\u3001\"started\"$")
    public void housekeeperReceivesOwnChain() throws InterruptedException {
        SseSupport.SseStreamClient housekeeper = this.staff("\u5c0f\u7f8e");
        UUID taskId = (UUID)this.ctx.get("sseTask:hk");
        housekeeper.awaitTask(taskId, "created", AWAIT);
        housekeeper.awaitTask(taskId, "claimed", AWAIT);
        housekeeper.awaitTask(taskId, "started", AWAIT);
    }

    @Then(value="^\u300c\u963f\u5f37\u300d\u6536\u5230\u5de5\u52d9\u65b0\u55ae\u7684 \"created\"$")
    public void engineerReceivesDeptPoolCreated() throws InterruptedException {
        this.staff("\u963f\u5f37").awaitTask((UUID)this.ctx.get("sseTask:eng"), "created", AWAIT);
    }

    @Then(value="^\u5169\u4eba\u7684\u4e32\u6d41\u4e92\u4e0d\u51fa\u73fe\u5c0d\u65b9\u90e8\u9580\u7684\u4efb\u52d9\u4e8b\u4ef6$")
    public void staffStreamsAreIsolatedByDept() throws InterruptedException {
        Thread.sleep(1500L);
        Assertions.assertTrue((boolean)this.staff("\u963f\u5f37").taskEventsFor((UUID)this.ctx.get("sseTask:hk")).isEmpty(), (String)"\u5de5\u52d9\u4e32\u6d41\u4e0d\u5f97\u51fa\u73fe\u623f\u52d9\u90e8\u9580\u7684\u4efb\u52d9\uff08\u975e\u672c\u90e8\u9580\u3001\u975e\u672c\u4eba\uff09");
        Assertions.assertTrue((boolean)this.staff("\u5c0f\u7f8e").taskEventsFor((UUID)this.ctx.get("sseTask:eng")).isEmpty(), (String)"\u623f\u52d9\u4e32\u6d41\u4e0d\u5f97\u51fa\u73fe\u5de5\u52d9\u6c60\u7684\u4efb\u52d9");
    }

    @Then(value="^\u4f4f\u5ba2 A \u7684\u9023\u7dda\u4f9d\u5e8f\u6536\u5230 \"init\"\u3001\"resync\"$")
    public void guestConnectionReceivesInitThenResync() throws InterruptedException {
        this.assertInitThenResync(this.guest("A"));
    }

    @Then(value="^\u4f4f\u5ba2 A \u7684 resync \u7684 refetch \u6307\u5411 \"([^\"]+)\"$")
    public void guestResyncRefetchTarget(String expectedRefetch) {
        this.assertResyncRefetch(this.guest("A"), expectedRefetch);
    }

    @Then(value="^\u300c\u5c0f\u7f8e\u300d\u7684\u9023\u7dda\u4f9d\u5e8f\u6536\u5230 \"init\"\u3001\"resync\"$")
    public void staffConnectionReceivesInitThenResync() throws InterruptedException {
        this.assertInitThenResync(this.staff("\u5c0f\u7f8e"));
    }

    @Then(value="^\u300c\u5c0f\u7f8e\u300d\u7684 resync \u7684 refetch \u6307\u5411 \"([^\"]+)\"$")
    public void staffResyncRefetchTarget(String expectedRefetch) {
        this.assertResyncRefetch(this.staff("\u5c0f\u7f8e"), expectedRefetch);
    }

    private void assertInitThenResync(SseSupport.SseStreamClient client) throws InterruptedException {
        client.awaitNamed("resync", Duration.ofSeconds(5L));
        Assertions.assertEquals(List.of("init", "resync"), client.eventNames().subList(0, 2), (String)"\u9023\u7dda\u4e00\u958b\u5c31\u8a72\u4f9d\u5e8f\u6536\u5230\u9019\u5169\u7b46\uff0c\u4e0d\u5fc5\u7b49\u4efb\u4f55\u4efb\u52d9\u4e8b\u4ef6");
    }

    private void assertResyncRefetch(SseSupport.SseStreamClient client, String expectedRefetch) {
        Assertions.assertEquals((Object)expectedRefetch, (Object)client.resyncData().get("refetch").asText());
    }

    @Then(value="^\u9023\u7e8c\u6536\u5230\u5169\u6b21 \"heartbeat\" \u4e14 stream_pos \u7dad\u6301 0$")
    public void heartbeatsArriveWithoutTakingPosition() throws InterruptedException {
        SseSupport.SseStreamClient staff = this.staff("\u5c0f\u7f8e");
        JsonNode first = staff.awaitNamed("heartbeat", Duration.ofSeconds(5L));
        JsonNode second = staff.awaitNamed("heartbeat", Duration.ofSeconds(5L));
        Assertions.assertTrue((first.has("stream_pos") && second.has("stream_pos") ? 1 : 0) != 0, (String)"\u5fc3\u8df3\u5e36\u7576\u524d stream_pos\uff0c\u5b89\u975c\u671f\u4e5f\u80fd\u6838\u5c0d\u6f0f\u6c92\u6f0f");
        Assertions.assertEquals((long)0L, (long)first.get("stream_pos").asLong(), (String)"\u6c92\u63a8\u904e\u4efb\u52d9\u4e8b\u4ef6\u524d\u5fc3\u8df3\u7684 stream_pos \u7dad\u6301 0\u2014\u2014\u5fc3\u8df3\u4e0d\u4f54\u865f");
    }

    @Then(value="^\u4f4f\u5ba2 A \u4f9d\u5e8f\u6536\u5230 seq 1 \u5230 4 \u7684\u56db\u7b46\u4e8b\u4ef6\u4e14 stream_pos \u9010\u4e00\u905e\u589e$")
    public void fullChainDeliveredInOrder() throws InterruptedException {
        SseSupport.SseStreamClient guest = this.guest("A");
        guest.awaitTask(this.ctx.taskId(), "completed", AWAIT);
        List<JsonNode> delivered = guest.taskEventsFor(this.ctx.taskId());
        Assertions.assertEquals(List.of("created", "claimed", "started", "completed"), delivered.stream().map(e -> e.get("type").asText()).toList(), (String)"\u540c\u9375\u540c\u5206\u5340\uff0c\u9001\u9054\u9806\u5e8f\uff1d\u4e8b\u4ef6\u9806\u5e8f");
        Assertions.assertEquals(List.of(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3), Integer.valueOf(4)), delivered.stream().map(e -> e.get("seq").asInt()).toList());
        List<Long> positions = delivered.stream().map(e -> e.get("stream_pos").asLong()).toList();
        for (int i = 1; i < positions.size(); ++i) {
            Assertions.assertEquals((long)(positions.get(i - 1) + 1L), (Long)positions.get(i), (String)("\u9023\u7dda\u5c64\u6d41\u6c34\u865f\u9010\u4e00\u905e\u589e\u3001\u7121\u8df3\u865f: " + String.valueOf(positions)));
        }
    }

    @Then(value="^\u8a72\u4efb\u52d9\u63a8\u7d66\u4f4f\u5ba2 A \u7684\u4e8b\u4ef6\u7dad\u6301 (\\d+) \u7b46\u3001\u96f6\u91cd\u63a8$")
    public void duplicatesAbsorbed(int expected) {
        Assertions.assertEquals((int)expected, (int)this.guest("A").taskEventsFor(this.ctx.taskId()).size(), (String)"\u91cd\u8907\u4e8b\u4ef6\uff08\u540c task_id+seq\uff09\u4e0d\u5f97\u518d\u63a8\u7d66\u5ba2\u6236\u7aef");
    }

    @Then(value="^\u4e32\u6d41\u6536\u5230 \"degraded\" \u4e8b\u4ef6\u3001reason \u70ba \"([a-z_]+)\"$")
    public void degradedEventArrives(String reason) throws InterruptedException {
        JsonNode degraded = this.staff("\u5c0f\u7f8e").awaitNamed("degraded", Duration.ofSeconds(15L));
        Assertions.assertEquals((Object)reason, (Object)degraded.get("reason").asText(), (String)"degraded \u4e8b\u4ef6\u81ea\u8ff0\u539f\u56e0\uff0c\u5ba2\u6236\u7aef\u64da\u6b64\u8f49 5 \u79d2\u8f2a\u8a62");
    }

    private SseSupport.SseStreamClient guest(String label) {
        SseSupport.SseStreamClient client = (SseSupport.SseStreamClient)this.ctx.get("sse:guest:" + label);
        if (client == null) {
            throw new IllegalStateException("\u4f4f\u5ba2 " + label + " \u5c1a\u672a\u958b\u6d41\u2014\u2014Given \u6b65\u9a5f\u6f0f\u4e86");
        }
        return client;
    }

    private SseSupport.SseStreamClient staff(String name) {
        SseSupport.SseStreamClient client = (SseSupport.SseStreamClient)this.ctx.get("sse:staff:" + name);
        if (client == null) {
            throw new IllegalStateException("\u54e1\u5de5 " + name + " \u5c1a\u672a\u958b\u6d41\u2014\u2014Given \u6b65\u9a5f\u6f0f\u4e86");
        }
        return client;
    }
}
