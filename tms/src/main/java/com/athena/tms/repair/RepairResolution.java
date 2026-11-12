package com.athena.tms.repair;

import com.athena.tms.common.codes.RepairResolutionType;
import com.athena.tms.common.error.UnprocessableEntityException;

public enum RepairResolution {
    FIXED(RepairResolutionType.FIXED, true, false, AfterComplete.NOTHING),
    TEMPORARY(RepairResolutionType.TEMPORARY, true, false, AfterComplete.FOLLOW_UP_TICKET),
    CANNOT_FIX(RepairResolutionType.CANNOT_FIX, true, false, AfterComplete.ROOM_OUT_OF_ORDER),
    NO_ACTION_NEEDED(RepairResolutionType.NO_ACTION_NEEDED, false, true, AfterComplete.NOTHING);

    public enum AfterComplete {
        NOTHING,
        FOLLOW_UP_TICKET,
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

    public String code() {
        return code;
    }

    public boolean requiresPhotos() {
        return requiresPhotos;
    }

    public boolean closesOnComplete() {
        return closesOnComplete;
    }

    public AfterComplete afterComplete() {
        return afterComplete;
    }
}
