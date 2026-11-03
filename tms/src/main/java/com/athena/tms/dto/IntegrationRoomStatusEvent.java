/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.annotation.JsonProperty
 */
package com.athena.tms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public record IntegrationRoomStatusEvent(@JsonProperty(value="schema") String schema, @JsonProperty(value="event_id") String eventId, @JsonProperty(value="room_no") String roomNo, @JsonProperty(value="hk_state") String hkState, @JsonProperty(value="task_id") UUID taskId, @JsonProperty(value="seq") int seq, @JsonProperty(value="occurred_at") OffsetDateTime occurredAt) {
    public static final String SCHEMA_V1 = "room_status.v1";
}
