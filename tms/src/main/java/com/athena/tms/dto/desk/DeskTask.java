package com.athena.tms.dto.desk;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 櫃檯視角的一張單，看板卡片與待派單列用。
 *
 * <p>時間一律給時刻，「等 N 分」「做 N 分」由畫面用校正後的現在去算，後端不先換算。
 * 沒有急件欄位：櫃檯畫面不顯示急件，也不依急件改變規則。
 * 沒有值的欄位照樣以 null 輸出，畫面靠 null 判斷「空房」「沒有負責人」。
 */
public record DeskTask(
        UUID id,
        int seq,
        String displayNo,
        String state,
        boolean answered,
        String dept,
        String type,
        String source,
        String roomNo,
        int floor,
        boolean vip,
        String guestName,
        String title,
        boolean guestTyped,
        DeskGuestText guestText,
        UUID assigneeId,
        String assigneeName,
        OffsetDateTime createdAt,
        OffsetDateTime waitingSince,
        OffsetDateTime acceptedAt,
        String acceptedVia,
        OffsetDateTime startedAt,
        OffsetDateTime doneAt,
        OffsetDateTime interruptedAt,
        OffsetDateTime endedAt,
        OffsetDateTime updatedAt,
        String inspection,
        Sop sop,
        Interruption interruption,
        Deferral deferral) {

    /** 清潔 SOP 的進度；跳過的項目不算已勾選。 */
    public record Sop(int done, int total) {}

    /** 中斷的說明。{@code bySelf} 為 false 表示不是負責人自己暫停（例如請假）。 */
    public record Interruption(boolean bySelf, String reason, String handoverNote) {}

    /** 延後的說明。主管延後只帶「誰延後的」，不帶時間與原因。 */
    public record Deferral(boolean bySelf, String byName, String reason) {}
}
