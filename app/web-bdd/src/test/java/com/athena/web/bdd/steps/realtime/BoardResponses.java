package com.athena.web.bdd.steps.realtime;

import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebWorld;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

public class BoardResponses {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebWorld world;

    private final Map<UUID, Integer> stepOverrides = new HashMap<>();

    private final Map<UUID, Optional<ObjectNode>> pushedCards = new HashMap<>();

    private UnaryOperator<JsonNode> holdNextAs;

    private Held held;

    private Page installedOn;

    private record Held(Route route, APIResponse real, String body) {
    }

    public BoardResponses(WebWorld world) {
        this.world = world;
    }

    public JsonNode lastBoard() {
        String body = world.network().lastDeskBoardBody(world.page()).orElseThrow(() -> new IllegalStateException(
                "畫面還沒有讀到過看板資料，不知道畫面上的卡片長什麼樣子"));
        return parse(body);
    }

    public LocalDate businessDate() {
        return LocalDate.parse(lastBoard().path("businessDate").asText());
    }

    public ObjectNode cardOnScreen(UUID taskId) {
        return findCard(lastBoard(), taskId).orElseThrow(() -> new IllegalStateException(
                "畫面最近一次讀到的看板裡沒有這張單（" + taskId + "）"));
    }

    public void overrideStep(UUID taskId, int step) {
        stepOverrides.put(taskId, step);
        install();
    }

    public void rememberPushed(UUID taskId, ObjectNode card) {
        pushedCards.put(taskId, Optional.ofNullable(card).map(ObjectNode::deepCopy));
        install();
    }

    public void holdNext(UnaryOperator<JsonNode> rewrite) {
        holdNextAs = rewrite;
        install();
    }

    public boolean holding() {
        return held != null;
    }

    public void releaseHeld() {
        Held releasing = held;
        if (releasing == null) {
            throw new IllegalStateException("沒有被扣住的看板資料可以放行");
        }
        held = null;
        releasing.route().fulfill(new Route.FulfillOptions().setResponse(releasing.real()).setBody(releasing.body()));
    }

    public void awaitBoard(java.util.function.Predicate<JsonNode> cardMatches, UUID taskId, double timeoutMs,
            String failure) {
        Page page = world.page();
        Network network = world.network();
        long deadline = System.currentTimeMillis() + (long) timeoutMs;
        int checked = 0;
        while (true) {
            int received = network.deskBoardsReceived(page);
            if (received > checked) {
                checked = received;
                if (findCard(lastBoard(), taskId).filter(cardMatches).isPresent()) {
                    return;
                }
            }
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) {
                throw new AssertionError(failure + "（等了 " + (long) (timeoutMs / 1000) + " 秒）");
            }
            int seen = checked;
            try {
                page.waitForCondition(() -> network.deskBoardsReceived(page) > seen,
                        new Page.WaitForConditionOptions().setTimeout(left));
            } catch (com.microsoft.playwright.TimeoutError noNewBoard) {
            }
        }
    }

    public static Optional<ObjectNode> findCard(JsonNode board, UUID taskId) {
        for (JsonNode task : board.path("tasks")) {
            if (taskId.toString().equals(task.path("id").asText())) {
                return Optional.of(task.deepCopy());
            }
        }
        return Optional.empty();
    }

    private void install() {
        Page page = world.page();
        if (installedOn == page) {
            return;
        }
        world.network().handle(page, Network.DESK_BOARD, this::serve);
        installedOn = page;
    }

    private void serve(Route route) {
        APIResponse real = route.fetch();
        if (real.status() != 200) {
            route.fulfill(new Route.FulfillOptions().setResponse(real));
            return;
        }
        JsonNode board = parse(real.text());
        ArrayNode tasks = (ArrayNode) board.path("tasks");
        for (int i = tasks.size() - 1; i >= 0; i--) {
            UUID taskId = UUID.fromString(tasks.get(i).path("id").asText());
            Optional<ObjectNode> pushed = pushedCards.get(taskId);
            Integer step = stepOverrides.get(taskId);
            if (pushed != null && pushed.isEmpty()) {
                tasks.remove(i);
            } else if (pushed != null) {
                tasks.set(i, pushed.get().deepCopy());
            } else if (step != null) {
                ((ObjectNode) tasks.get(i)).put("seq", step);
            }
        }
        if (holdNextAs != null) {
            held = new Held(route, real, write(holdNextAs.apply(board)));
            holdNextAs = null;
            return;
        }
        route.fulfill(new Route.FulfillOptions().setResponse(real).setBody(write(board)));
    }

    private static JsonNode parse(String body) {
        try {
            return JSON.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("看板資料不是合法的 JSON：" + body, e);
        }
    }

    private static String write(JsonNode node) {
        try {
            return JSON.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("看板資料轉不成 JSON", e);
        }
    }
}
