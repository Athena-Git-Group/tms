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

/**
 * 這個分頁讀到的看板資料：讀出畫面上那份看板，以及把之後讀回來的看板改成案例要的樣子。
 *
 * <p>「畫面上 412 是第 3 步」這類前提，後端做不出來：步數是後端照事件一筆一筆數的，測試只能在看板資料送到畫面之前改掉它。
 * 改法是請求照常送到後端，回應交給畫面之前換掉那張卡片的步數，之後每一次讀看板都照改，畫面才會一直認定它是那一步。
 * 「下一次讀取會被扣住」也在同一處做：下一個回應取回、改好之後先不交給畫面，等案例說放行才交。
 * 兩件事必須在同一個攔截裡做，因為同一個分頁的同一支端點只能有一種攔截設定。
 *
 * <p>代替後端送的卡片以畫面上那張單目前的卡片為底，所以底一律取自畫面最近一次真正收到的看板資料。
 * 送出之後，之後讀回來的看板也照那張卡片改（或拿掉那張單），當作後端真的走到了那一步。
 * 不改的話，送了卡片之後的整份重讀會讀回後端原本的樣子，畫面跟著倒回去，案例看不出卡片有沒有換上。
 */
public class BoardResponses {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebWorld world;

    private final Map<UUID, Integer> stepOverrides = new HashMap<>();

    /** 測試代替後端送出過的卡片，以單的 id 為鍵；值是空的表示送的是「已經不在看板上」。 */
    private final Map<UUID, Optional<ObjectNode>> pushedCards = new HashMap<>();

    private UnaryOperator<JsonNode> holdNextAs;

    private Held held;

    private Page installedOn;

    private record Held(Route route, APIResponse real, String body) {
    }

    public BoardResponses(WebWorld world) {
        this.world = world;
    }

    /** 畫面最近一次讀到的整份看板資料；畫面還沒讀到過時直接失敗。 */
    public JsonNode lastBoard() {
        String body = world.network().lastDeskBoardBody(world.page()).orElseThrow(() -> new IllegalStateException(
                "畫面還沒有讀到過看板資料，不知道畫面上的卡片長什麼樣子"));
        return parse(body);
    }

    /** 畫面上那份看板的營業日。 */
    public LocalDate businessDate() {
        return LocalDate.parse(lastBoard().path("businessDate").asText());
    }

    /** 畫面上這張單目前的卡片；畫面上沒有它時直接失敗。 */
    public ObjectNode cardOnScreen(UUID taskId) {
        return findCard(lastBoard(), taskId).orElseThrow(() -> new IllegalStateException(
                "畫面最近一次讀到的看板裡沒有這張單（" + taskId + "）"));
    }

    /** 從現在起，讀回來的看板裡這張卡片的步數一律改成 {@code step}。 */
    public void overrideStep(UUID taskId, int step) {
        stepOverrides.put(taskId, step);
        install();
    }

    /** 記下測試代替後端送出的卡片，{@code card} 是 {@code null} 表示送的是「已經不在看板上」。 */
    public void rememberPushed(UUID taskId, ObjectNode card) {
        pushedCards.put(taskId, Optional.ofNullable(card).map(ObjectNode::deepCopy));
        install();
    }

    /** 下一次讀回來的看板先扣住不交給畫面，內容另外經過 {@code rewrite} 改寫；由 {@link #releaseHeld()} 放行。 */
    public void holdNext(UnaryOperator<JsonNode> rewrite) {
        holdNextAs = rewrite;
        install();
    }

    /** 被扣住的看板資料是不是已經在等放行。 */
    public boolean holding() {
        return held != null;
    }

    /** 把扣住的那一份交給畫面。 */
    public void releaseHeld() {
        Held releasing = held;
        if (releasing == null) {
            throw new IllegalStateException("沒有被扣住的看板資料可以放行");
        }
        held = null;
        releasing.route().fulfill(new Route.FulfillOptions().setResponse(releasing.real()).setBody(releasing.body()));
    }

    /**
     * 等畫面讀回一份看板，裡面這張單的卡片符合條件，逾時就以這段說明失敗。
     * 畫面可能同時有別的重讀在路上（例如剛連上時的那一次），所以不是等到下一份就停，而是一份一份看到符合為止。
     * 回應內容要在等待之外才讀，等待的條件裡只看讀到了幾份。
     */
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
                // 回到迴圈開頭，由上面的期限檢查報出好懂的訊息。
            }
        }
    }

    /** 從這份看板裡找這張單的卡片，回傳可以改的副本。 */
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
