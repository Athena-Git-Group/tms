package com.athena.tms.repair;

import com.athena.tms.common.codes.RepairResolutionType;
import com.athena.tms.common.error.UnprocessableEntityException;

/**
 * 工務完修時選的完修結果，連同每一種結果完成時要做的事放在同一張對照裡：
 * 要不要附完修照片、完成後落在 done（等主管驗收）還是直接 closed（已結案），
 * 以及完成後要不要另外建追蹤單或把房間轉停用。
 *
 * <p>規則只寫在這裡。完修照片驗證、完成的狀態轉移與完修收尾都查這張表，
 * 加一種結果或改一條規則時不會有哪一處漏改、各說各話。
 *
 * <table>
 *   <tr><th>結果</th><th>完修照片</th><th>完成後</th><th>後續</th></tr>
 *   <tr><td>fixed（已修好）</td><td>必附</td><td>done</td><td>無</td></tr>
 *   <tr><td>temporary（暫時處理）</td><td>必附</td><td>done</td><td>建追蹤單進工務待認領</td></tr>
 *   <tr><td>cannot_fix（無法修復）</td><td>必附</td><td>done</td><td>房間轉停用待確認</td></tr>
 *   <tr><td>no_action_needed（不需處理）</td><td>不用</td><td>closed</td><td>無</td></tr>
 * </table>
 */
public enum RepairResolution {
    FIXED(RepairResolutionType.FIXED, true, false, AfterComplete.NOTHING),
    TEMPORARY(RepairResolutionType.TEMPORARY, true, false, AfterComplete.FOLLOW_UP_TICKET),
    CANNOT_FIX(RepairResolutionType.CANNOT_FIX, true, false, AfterComplete.ROOM_OUT_OF_ORDER),
    NO_ACTION_NEEDED(RepairResolutionType.NO_ACTION_NEEDED, false, true, AfterComplete.NOTHING);

    /** 完修收尾時，工務單本身之外還要動的東西。 */
    public enum AfterComplete {
        NOTHING,
        /** 另建一張房號、類別都相同的追蹤單進工務待認領。 */
        FOLLOW_UP_TICKET,
        /** 該房 rooms.hk_state 轉為 out_of_order（停用待確認）。 */
        ROOM_OUT_OF_ORDER
    }

    private final String code;
    private final boolean requiresPhotos;
    private final boolean closesOnComplete;
    private final AfterComplete afterComplete;

    RepairResolution(String code, boolean requiresPhotos, boolean closesOnComplete, AfterComplete afterComplete) {
        this.code = code;
        this.requiresPhotos = requiresPhotos;
        this.closesOnComplete = closesOnComplete;
        this.afterComplete = afterComplete;
    }

    /**
     * 請求帶來的完修結果：省略視為已修好；不在四種之內的值整個完修請求拒絕（422），
     * 不讓一個看不懂的結果寫進單與事件。
     */
    public static RepairResolution of(String code) {
        if (code == null) {
            return FIXED;
        }
        for (RepairResolution resolution : values()) {
            if (resolution.code.equals(code)) {
                return resolution;
            }
        }
        throw new UnprocessableEntityException(
                "resolutionType 必須是 fixed、temporary、cannot_fix 或 no_action_needed");
    }

    /** 寫進 repair_tickets.resolution_type 與 completed 事件 payload 的值。 */
    public String code() {
        return code;
    }

    /** 完成這一次請求是否必須附完修照片。 */
    public boolean requiresPhotos() {
        return requiresPhotos;
    }

    /** 完成時是否直接結案（closed），不進待驗收。 */
    public boolean closesOnComplete() {
        return closesOnComplete;
    }

    public AfterComplete afterComplete() {
        return afterComplete;
    }
}
