package com.athena.tms.dto.desk;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/**
 * 歷程的一筆。句子由畫面依語言組，後端只給組句子要的資料，
 * 住客與同仁輸入的文字原樣帶出。
 */
public record DeskTimelineEntry(
        int seq,
        OffsetDateTime at,
        String type,
        Actor actor,
        @JsonInclude(JsonInclude.Include.NON_NULL) Detail detail) {

    /** 操作者。{@code kind} 是 guest、system 或 staff；{@code role} 只有 staff 有值。 */
    public record Actor(String kind, String role, String name) {}

    /**
     * 組句子要的內容。每種事件只用得到其中幾項，契約要求用不到的不出現，
     * 所以欄位都用可為 null 的型別，null 的不輸出。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Detail(
            String dept,
            String initialState,
            Boolean guestTyped,
            String ruleLabel,
            String via,
            String answer,
            String assigneeName,
            String previousAssigneeName,
            String reason,
            Boolean bySelf,
            String note,
            String cancelReason,
            String resolutionType,
            Integer rating) {}
}
