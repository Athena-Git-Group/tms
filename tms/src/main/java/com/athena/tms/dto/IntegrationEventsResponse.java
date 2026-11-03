/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.annotation.JsonProperty
 */
package com.athena.tms.dto;

import com.athena.tms.dto.IntegrationRoomStatusEvent;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record IntegrationEventsResponse(@JsonProperty(value="events") List<IntegrationRoomStatusEvent> events, @JsonProperty(value="next_cursor") String nextCursor) {
}
