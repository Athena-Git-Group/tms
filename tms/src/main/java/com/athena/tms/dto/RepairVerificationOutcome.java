package com.athena.tms.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * verifyRepairTicket 的回應（contracts 工務單.yaml {@code RepairVerificationOutcome}）：這次驗收的結果與單的去處，
 * 供 App 結果畫面使用。{@code result} 與 {@code destination} 一起看：passed 時 destination 一定是 closed；
 * failed 時是 assignee（回到原處理人名下待開始）或 pool（原處理人已下班或請假，回到工務待認領）。
 * {@code assigneeName} 是原處理人，去處是 pool 時也帶，因為那時工務單本身的處理人已清空。
 */
public record RepairVerificationOutcome(
        UUID taskId,
        String ticketNo,
        String roomNo,
        boolean isPublicArea,
        String result,
        String destination,
        String assigneeName,
        String reason,
        String decidedBy,
        OffsetDateTime decidedAt) {
}
