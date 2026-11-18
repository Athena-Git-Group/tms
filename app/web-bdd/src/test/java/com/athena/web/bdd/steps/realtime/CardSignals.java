package com.athena.web.bdd.steps.realtime;

import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.bddsupport.TaskSituation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * 代替後端送的 {@code task} 訊號，形狀照契約的 {@code StaffStreamTask}，卡片照 {@code DeskTask}。
 *
 * <p>卡片一律以畫面上那張單目前的卡片為底，只改介面根「單的狀況對照」裡那個狀況需要的欄位：狀態、負責人、時間與步數。
 * 其他欄位（房號、標題、住客、類型）沿用原樣，畫面換上之後除了狀況以外看起來是同一張卡片，結果句才比得出是不是換上了。
 * 和前一個狀況才有關的欄位（例如中斷原因、延後說明、結束時間）先清掉，不然換上的卡片會同時帶著兩種狀況的說明。
 */
final class CardSignals {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 秒數為 0 時也寫出秒，和後端送的時間同一種寫法。 */
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /** 換狀況時一律先清掉、再依新狀況補上的欄位。 */
    private static final List<String> SITUATION_FIELDS = List.of(
            "waitingSince", "acceptedAt", "acceptedVia", "startedAt", "doneAt", "interruptedAt", "endedAt",
            "inspection", "interruption", "deferral");

    /** 只有這幾個狀態會帶清潔 SOP 進度，其餘狀態的卡片不帶。 */
    private static final List<String> STATES_WITH_SOP = List.of("accepted", "doing", "interrupted");

    private CardSignals() {
    }

    /**
     * 把畫面上這張卡片改成這個狀況、這一步的樣子。
     *
     * @param base 畫面上這張單目前的卡片；會被直接改掉，呼叫端給副本
     * @param at 卡片上新的時間，例如到手、開工、結束的時刻
     * @param assigneeOf 給部門代碼、回那個部門的預設同仁；案例沒寫負責人、狀況又一定要有人時用它
     */
    static ObjectNode situated(ObjectNode base, TaskSituation situation, int step, OffsetDateTime at,
            Function<String, CreatedStaff> assigneeOf) {
        String now = at.truncatedTo(ChronoUnit.SECONDS).format(TIME);
        ObjectNode card = base;
        String waitingSince = card.path("waitingSince").isTextual() ? card.path("waitingSince").asText() : now;
        card.put("seq", step);
        card.put("updatedAt", now);
        card.put("answered", false);
        SITUATION_FIELDS.forEach(card::putNull);

        switch (situation) {
            case TRIAGE -> {
                card.put("state", "triage");
                card.put("dept", "frontdesk");
                card.set("waitingSince", card.get("createdAt"));
                clearAssignee(card);
            }
            case POOL -> {
                card.put("state", "pool");
                leaveFrontdesk(card);
                card.put("waitingSince", waitingSince);
                clearAssignee(card);
            }
            case ACCEPTED_BY_PLAN, ACCEPTED_BY_CLAIM, ACCEPTED_BY_MANAGER, ACCEPTED_BY_REASSIGN -> {
                card.put("state", "accepted");
                accepted(card, situation, now, assigneeOf);
            }
            case DOING -> {
                card.put("state", "doing");
                accepted(card, TaskSituation.ACCEPTED_BY_CLAIM, now, assigneeOf);
                card.put("startedAt", now);
            }
            case DONE_AWAITING_INSPECTION, DONE_AWAITING_VERIFICATION, DONE -> {
                card.put("state", "done");
                worked(card, now, assigneeOf);
                if (situation == TaskSituation.DONE) {
                    card.put("endedAt", now);
                }
            }
            case INTERRUPTED_BY_LEAVE, INTERRUPTED_BY_SELF -> {
                card.put("state", "interrupted");
                accepted(card, TaskSituation.ACCEPTED_BY_CLAIM, now, assigneeOf);
                card.put("startedAt", now);
                card.put("interruptedAt", now);
                ObjectNode interruption = card.putObject("interruption");
                interruption.put("bySelf", situation == TaskSituation.INTERRUPTED_BY_SELF);
                interruption.putNull("reason");
                interruption.putNull("handoverNote");
            }
            case DEFERRED_BY_MANAGER, DEFERRED_BY_SELF -> {
                card.put("state", "deferred");
                leaveFrontdesk(card);
                ensureAssignee(card, assigneeOf);
                boolean bySelf = situation == TaskSituation.DEFERRED_BY_SELF;
                ObjectNode deferral = card.putObject("deferral");
                deferral.put("bySelf", bySelf);
                deferral.put("byName", bySelf ? card.path("assigneeName").asText() : "張主管");
                deferral.putNull("reason");
            }
            case INSPECTION_PASSED, INSPECTION_WAIVED -> {
                card.put("state", "inspected");
                worked(card, now, assigneeOf);
                card.put("inspection", situation == TaskSituation.INSPECTION_PASSED ? "passed" : "waived");
                card.put("endedAt", now);
            }
            case CLOSED_VERIFIED, CLOSED_NO_ACTION_NEEDED -> {
                card.put("state", "closed");
                worked(card, now, assigneeOf);
                card.put("endedAt", now);
            }
            case CANCELLED_BY_GUEST, CANCELLED_BY_MANAGER, CANCELLED_PLAN_SUPERSEDED, CANCELLED_BY_REPORTER -> {
                card.put("state", "cancelled");
                card.put("endedAt", now);
            }
            case ANSWERED -> {
                card.put("state", "done");
                card.put("dept", "frontdesk");
                card.put("answered", true);
                card.put("doneAt", now);
                card.put("endedAt", now);
                clearAssignee(card);
            }
        }
        if (!STATES_WITH_SOP.contains(card.path("state").asText())) {
            card.putNull("sop");
        }
        return card;
    }

    /** 帶卡片的 {@code task}：{@code card} 為 {@code null} 時是「這張單已經不在看板上」。 */
    static String withCard(UUID taskId, int step, LocalDate businessDate, ObjectNode card) {
        ObjectNode signal = bare(taskId, step);
        signal.put("board", card == null ? "remove" : "upsert");
        signal.put("card_seq", step);
        signal.put("business_date", businessDate.toString());
        if (card != null) {
            signal.set("card", card);
        }
        return write(signal);
    }

    /** 沒有帶卡片的 {@code task}，和房務、工務連線收到的一樣；畫面收到只能重讀整份看板。 */
    static String withoutCard(UUID taskId, int step) {
        return write(bare(taskId, step));
    }

    /** {@code board} 不是 {@code upsert} 也不是 {@code remove} 的 {@code task}，其餘欄位都齊全。 */
    static String unreadable(UUID taskId, int step, LocalDate businessDate, ObjectNode card) {
        ObjectNode signal = bare(taskId, step);
        signal.put("board", "replace");
        signal.put("card_seq", step);
        signal.put("business_date", businessDate.toString());
        signal.set("card", card);
        return write(signal);
    }

    /** 畫面不看事件種類，種類只要是契約裡有的值；用評分這種不改變卡片狀況的事件。 */
    private static ObjectNode bare(UUID taskId, int step) {
        ObjectNode signal = JSON.createObjectNode();
        signal.put("stream_pos", 0);
        signal.put("task_id", taskId.toString());
        signal.put("seq", step);
        signal.put("type", "rated");
        return signal;
    }

    private static void accepted(ObjectNode card, TaskSituation via, String now,
            Function<String, CreatedStaff> assigneeOf) {
        leaveFrontdesk(card);
        ensureAssignee(card, assigneeOf);
        card.put("acceptedAt", now);
        card.put("acceptedVia", switch (via) {
            case ACCEPTED_BY_PLAN -> "plan_assigned";
            case ACCEPTED_BY_MANAGER -> "manager_assigned";
            case ACCEPTED_BY_REASSIGN -> "reassigned";
            default -> "self_claimed";
        });
    }

    /** 做過的單：到手、開工、完成三個時刻都有。 */
    private static void worked(ObjectNode card, String now, Function<String, CreatedStaff> assigneeOf) {
        accepted(card, TaskSituation.ACCEPTED_BY_CLAIM, now, assigneeOf);
        card.put("startedAt", now);
        card.put("doneAt", now);
    }

    /** 從櫃檯手上進到部門的單，部門跟著類型走：修繕給工務，其餘給房務。 */
    private static void leaveFrontdesk(ObjectNode card) {
        if ("frontdesk".equals(card.path("dept").asText())) {
            card.put("dept", "repair".equals(card.path("type").asText()) ? "engineering" : "housekeeping");
        }
    }

    private static void ensureAssignee(ObjectNode card, Function<String, CreatedStaff> assigneeOf) {
        if (card.path("assigneeId").isTextual()) {
            return;
        }
        CreatedStaff staff = assigneeOf.apply(card.path("dept").asText());
        card.put("assigneeId", staff.id().toString());
        card.put("assigneeName", staff.name());
    }

    private static void clearAssignee(ObjectNode card) {
        card.putNull("assigneeId");
        card.putNull("assigneeName");
    }

    private static String write(JsonNode node) {
        try {
            return JSON.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("訊號轉不成 JSON", e);
        }
    }
}
