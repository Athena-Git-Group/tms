package com.athena.tms.dto;

/**
 * verifyRepairTicket 的請求（contracts 工務單.yaml {@code VerifyRepairTicketRequest}）：主管對一張已完修工務單的驗收結果。
 * {@code result} 是 passed 或 failed；failed 時 {@code reason} 必填，passed 時不收。
 */
public record VerifyRepairTicketRequest(String result, String reason) {
}
