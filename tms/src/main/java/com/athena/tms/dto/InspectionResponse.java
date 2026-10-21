package com.athena.tms.dto;

import java.util.UUID;

/**
 * 查房結果回應。taskState 回「這一刀落下後任務去了哪」——全過是 inspected、
 * 退回是 accepted、退回且連動退池是 pool；主管看回應就知道下一步是誰的事，
 * 不必再查一次任務。
 */
public record InspectionResponse(
        UUID taskId,
        String result,
        String taskState,
        int redoCount,
        boolean returnedToPool) {
}
