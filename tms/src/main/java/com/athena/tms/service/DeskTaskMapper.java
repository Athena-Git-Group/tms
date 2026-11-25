package com.athena.tms.service;

import com.athena.tms.dto.desk.DeskGuestText;
import com.athena.tms.dto.desk.DeskTask;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class DeskTaskMapper {
    private final ObjectMapper objectMapper;

    public DeskTaskMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DeskTask toDeskTask(Map<String, Object> row) {
        String state = (String) row.get("state");
        String type = (String) row.get("type");
        JsonNode noteFields = this.noteFields(row.get("note"));
        DeskGuestText guestText = guestText(noteFields);
        OffsetDateTime createdAt = (OffsetDateTime) row.get("created_at");
        boolean interrupted = "interrupted".equals(state);
        boolean deferred = "deferred".equals(state);
        boolean deferredBySelf = Boolean.TRUE.equals(row.get("deferred_by_self"));
        return new DeskTask(
                (UUID) row.get("id"),
                ((Number) row.get("event_seq")).intValue(),
                (String) row.get("repair".equals(type) ? "ticket_no" : "task_no"),
                state,
                row.get("answer") != null,
                (String) row.get("dept"),
                type,
                (String) row.get("source"),
                (String) row.get("room_no"),
                ((Number) row.get("floor")).intValue(),
                Boolean.TRUE.equals(row.get("vip")),
                (String) row.get("guest_name"),
                (String) row.get("title"),
                "guest".equals(row.get("source")) && guestText != null,
                guestText,
                textOrNull(noteFields, "menu_key"),
                (UUID) row.get("assignee_id"),
                (String) row.get("assignee_name"),
                createdAt,
                waitingSince(state, createdAt, (OffsetDateTime) row.get("waiting_event_at")),
                (OffsetDateTime) row.get("accepted_at"),
                acceptedVia((String) row.get("accepted_type"), (String) row.get("accepted_actor_role")),
                (OffsetDateTime) row.get("started_at"),
                (OffsetDateTime) row.get("done_at"),
                interrupted ? (OffsetDateTime) row.get("interrupted_at") : null,
                (OffsetDateTime) row.get("ended_at"),
                (OffsetDateTime) row.get("updated_at"),
                (String) row.get("inspection"),
                sop(row.get("sop_done"), row.get("sop_total")),
                interrupted
                        ? new DeskTask.Interruption(
                                Boolean.TRUE.equals(row.get("interrupted_by_self")),
                                (String) row.get("hold_reason"),
                                (String) row.get("requeue_note"))
                        : null,
                deferred
                        ? new DeskTask.Deferral(
                                deferredBySelf,
                                (String) row.get("deferred_by_name"),
                                deferredBySelf ? (String) row.get("hold_reason") : null)
                        : null);
    }

    private static OffsetDateTime waitingSince(String state, OffsetDateTime createdAt, OffsetDateTime waitingEventAt) {
        if ("triage".equals(state)) {
            return createdAt;
        }
        if ("pool".equals(state)) {
            return waitingEventAt == null ? createdAt : waitingEventAt;
        }
        return null;
    }

    private static String acceptedVia(String eventType, String actorRole) {
        if (eventType == null) {
            return null;
        }
        return switch (eventType) {
            case "claimed" -> "self_claimed";
            case "assigned" -> "system".equals(actorRole) ? "plan_assigned" : "manager_assigned";
            case "reassigned" -> "reassigned";
            default -> null;
        };
    }

    private static DeskTask.Sop sop(Object done, Object total) {
        if (total == null || ((Number) total).intValue() == 0) {
            return null;
        }
        return new DeskTask.Sop(((Number) done).intValue(), ((Number) total).intValue());
    }

    private JsonNode noteFields(Object note) {
        if (note == null) {
            return null;
        }
        JsonNode fields;
        try {
            fields = this.objectMapper.readTree(String.valueOf(note));
        } catch (JacksonException notJson) {
            return null;
        }
        return fields != null && fields.isObject() ? fields : null;
    }

    private static DeskGuestText guestText(JsonNode fields) {
        if (fields == null) {
            return null;
        }
        String original = textOrNull(fields, "text");
        if (original == null) {
            return null;
        }
        return new DeskGuestText(original, textOrNull(fields, "lang"), textOrNull(fields, "llm_translated"));
    }

    private static String textOrNull(JsonNode fields, String name) {
        if (fields == null) {
            return null;
        }
        JsonNode value = fields.get(name);
        return value == null || value.isNull() ? null : value.asString();
    }
}
