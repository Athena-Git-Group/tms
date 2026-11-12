package com.athena.tms.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * createInspections 的回應（contracts 查房.yaml {@code InspectionOutcome}）：這次查房的結果與
 * 單的去向，供 App 結果畫面的三種說法使用。{@code result} 與 {@code destination} 一起看：
 * passed 時 destination 一定是 closed；failed 時是 cleaner（回到原清房者名下）或 pool
 * （原清房者已下班或請假，回到房務待認領）。退回的原因就是 {@code failedItems}，沒有另外的備註。
 */
public record InspectionOutcome(
        UUID inspectionId,
        UUID taskId,
        String roomNo,
        String result,
        String destination,
        String cleanerName,
        List<FailedItem> failedItems,
        OffsetDateTime createdAt) {

    /** 被判不通過的項目，依飯店設定的排序；label 是項目顯示名稱，例如「浴室無水痕」。 */
    public record FailedItem(String key, String label) {}
}
