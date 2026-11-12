package com.athena.tms.dto;

import java.util.List;
import java.util.UUID;

/**
 * 完成任務的請求（contracts CompleteTaskRequest）。打掃與一般任務不需要 body；
 * 修繕任務（工務單）帶完修結果 resolutionType（fixed／temporary／cannot_fix／no_action_needed，
 * 省略視為 fixed）。哪幾種結果必須在 photoIds 附這一次的完修照片，以
 * {@link com.athena.tms.repair.RepairResolution} 為準。
 */
public record CompleteTaskRequest(List<UUID> photoIds, String resolutionNote, String resolutionType) {
}
