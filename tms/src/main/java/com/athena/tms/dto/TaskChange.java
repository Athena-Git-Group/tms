package com.athena.tms.dto;

import java.util.UUID;

/**
 * 員工串流與住客串流用它決定一張單剛剛的變化要送給哪些連線。
 *
 * <p>{@code recalledFromDept} 是櫃檯復原派單時被收回的部門，讓那個部門的連線也收得到復原的訊號。
 * 不是復原派單的事件，這個欄位是 null。
 */
public record TaskChange(long tenantId, UUID taskId, int seq, String type, String occurredAt, String dept, UUID assigneeId, UUID reservationId, String recalledFromDept) {
}
