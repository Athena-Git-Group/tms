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

final class CardSignals {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private static final List<String> SITUATION_FIELDS = List.of(
            "waitingSince", "acceptedAt", "acceptedVia", "startedAt", "doneAt", "interruptedAt", "endedAt",
            "inspection", "interruption", "deferral");

    private static final List<String> STATES_WITH_SOP = List.of("accepted", "doing", "interrupted");

    private CardSignals() {
    }

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

    static String withoutCard(UUID taskId, int step) {
        return write(bare(taskId, step));
    }

    static String unreadable(UUID taskId, int step, LocalDate businessDate, ObjectNode card) {
        ObjectNode signal = bare(taskId, step);
        signal.put("board", "replace");
        signal.put("card_seq", step);
        signal.put("business_date", businessDate.toString());
        signal.set("card", card);
        return write(signal);
    }

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

    private static void worked(ObjectNode card, String now, Function<String, CreatedStaff> assigneeOf) {
        accepted(card, TaskSituation.ACCEPTED_BY_CLAIM, now, assigneeOf);
        card.put("startedAt", now);
        card.put("doneAt", now);
    }

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
