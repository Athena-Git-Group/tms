package com.athena.tms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * getManagerQueues（{@code GET /me/manager-queues}）的回應，欄位照 contracts
 * {@code components/schemas/主管工作台.yaml} 的 {@code ManagerQueues}：格子數字與清單
 * 在同一次往返內給完，{@code counts} 的每個數字等於對應陣列的長度。
 *
 * <p>工務主管沒有待查房這一類，回應裡 {@code inspection} 與 {@code counts.inspection} 兩個鍵
 * 整個不出現，而不是空陣列與 0；空陣列的意思是「待查房 0 張」，App 會照樣畫一格 0。
 *
 * <p>清單列共用 {@code QueueItemBase} 的六個欄位（任務 id、房號或地點、標題、開始等
 * 主管的時間與是否今天），各自再加該類主管看得懂這張單所需的欄位。公共區域的單
 * {@code roomNo} 為 null 並以 {@code isPublicArea=true} 表示，地點在 {@code title} 裡。
 */
public record ManagerQueues(
        Counts counts,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<InspectionQueueItem> inspection,
        List<VerifyQueueItem> verify) {

    /**
     * 組出回應的唯一入口：格子數字取各清單的長度，{@code inspection} 傳 null 表示呼叫者沒有
     * 待查房這一類，兩個鍵都會省略。
     */
    public static ManagerQueues of(List<InspectionQueueItem> inspection, List<VerifyQueueItem> verify) {
        return new ManagerQueues(
                new Counts(inspection == null ? null : inspection.size(), verify.size()),
                inspection,
                verify);
    }

    public record Counts(@JsonInclude(JsonInclude.Include.NON_NULL) Integer inspection, int verify) {}

    /** 待查房：別人清好、停在 done 等查房的打掃單。 */
    public record InspectionQueueItem(
            UUID taskId,
            String roomNo,
            boolean isPublicArea,
            String title,
            OffsetDateTime waitingSince,
            boolean waitingSinceIsToday,
            String cleanerName,
            int redoCount) {}

    /** 待驗收：已完修、等主管驗收的工務單，含暫時處理、無法修復與定期保養的單。 */
    public record VerifyQueueItem(
            UUID taskId,
            String roomNo,
            boolean isPublicArea,
            String title,
            OffsetDateTime waitingSince,
            boolean waitingSinceIsToday,
            String ticketNo,
            String category,
            String description,
            String reporterName,
            String assigneeName,
            String origin,
            String resolutionType,
            boolean wasVerificationFailed) {}
}
