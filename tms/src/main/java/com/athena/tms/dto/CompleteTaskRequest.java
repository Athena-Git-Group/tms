package com.athena.tms.dto;

import java.util.List;
import java.util.UUID;

/**
 * 完成任務的請求（contracts CompleteTaskRequest）。打掃與一般任務不需要 body；
 * 修繕任務（工務單）必須附完修照片，photoIds 至少 1 張。
 */
public record CompleteTaskRequest(List<UUID> photoIds, String resolutionNote) {
}
