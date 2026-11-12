package com.athena.tms.bdd.steps.kb;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import io.cucumber.java.zh_tw.當;
import java.util.Map;
import java.util.UUID;

public class KbWhenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;

    public KbWhenSteps(ScenarioWorld world, BddApiClient apiClient) {
        this.world = world;
        this.apiClient = apiClient;
    }

    @當("{string} 在 {string} 打開查一下")
    public void openKb(String staffName, String taskKey) {
        UUID taskId = UUID.fromString(world.taskId(taskKey));
        apiClient.get(
                "/room-memories/suggestions?taskId=" + taskId, BddApiClient.Auth.staff(world.jwt(staffName)));
    }

    @當("{string} 在 {string} 問 {string}")
    public void ask(String staffName, String taskKey, String question) {
        UUID taskId = UUID.fromString(world.taskId(taskKey));
        askAndRememberAnswerId(staffName, taskId, question);
    }

    @當("{string} 對這則回答回饋 {string}")
    public void submitFeedback(String staffName, String feedbackChinese) {
        String answerId = requireAnswerId();
        apiClient.post(
                "/room-memories/answers/" + answerId + "/feedback",
                BddApiClient.Auth.staff(world.jwt(staffName)),
                Map.of("helpful", feedbackHelpful(feedbackChinese)));
    }

    @當("{string} 對這個問題按問主管")
    public void escalate(String staffName) {
        String answerId = requireAnswerId();
        ScenarioWorld.ApiResponse resp = apiClient.post(
                "/room-memories/answers/" + answerId + "/escalate",
                BddApiClient.Auth.staff(world.jwt(staffName)),
                null);
        rememberEscalationId(resp);
    }

    @當("{string} 回覆這個問題 {string}")
    public void managerReplies(String managerName, String reply) {
        String escalationId = requireEscalationId();
        apiClient.post(
                "/room-memories/escalations/" + escalationId + "/reply",
                BddApiClient.Auth.staff(world.jwt(managerName)),
                Map.of("reply", reply));
    }

    private void askAndRememberAnswerId(String staffName, UUID taskId, String question) {
        Map<String, Object> body = Map.of("taskId", taskId.toString(), "question", question);
        ScenarioWorld.ApiResponse resp =
                apiClient.post("/room-memories/ask", BddApiClient.Auth.staff(world.jwt(staffName)), body);
        rememberAnswerId(resp);
    }

    private void rememberAnswerId(ScenarioWorld.ApiResponse resp) {
        if (resp.json() != null && resp.json().has("answerId") && !resp.json().get("answerId").isNull()) {
            world.setLastAnswerId(resp.json().get("answerId").asText());
        }
    }

    private void rememberEscalationId(ScenarioWorld.ApiResponse resp) {
        if (resp.json() != null && resp.json().has("id") && !resp.json().get("id").isNull()) {
            world.setLastEscalationId(resp.json().get("id").asText());
        }
    }

    private String requireAnswerId() {
        String answerId = world.lastAnswerId();
        if (answerId == null) {
            throw new IllegalStateException("尚未有回答可回饋／轉問主管：請先在 Given/When 問過一次問題");
        }
        return answerId;
    }

    private String requireEscalationId() {
        String escalationId = world.lastEscalationId();
        if (escalationId == null) {
            throw new IllegalStateException("尚未有待回覆的提問：請先按過問主管");
        }
        return escalationId;
    }

    private static boolean feedbackHelpful(String chinese) {
        return switch (chinese) {
            case "有幫助" -> true;
            case "沒幫助" -> false;
            default -> throw new IllegalArgumentException("查一下回饋只支援 有幫助｜沒幫助，收到: " + chinese);
        };
    }
}
