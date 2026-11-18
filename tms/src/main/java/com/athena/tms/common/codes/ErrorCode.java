package com.athena.tms.common.codes;

public final class ErrorCode {
    public static final String VERIFICATION_REASON_REQUIRED = "verification_reason_required";
    public static final String TASK_NOT_IN_TRIAGE = "task_not_in_triage";
    public static final String UNDO_CLAIMED = "undo_claimed";
    public static final String UNDO_GUEST_WITHDREW = "undo_guest_withdrew";
    public static final String UNDO_EXPIRED = "undo_expired";
    public static final String UNDO_UNAVAILABLE = "undo_unavailable";
    public static final String ANSWER_INVALID = "answer_invalid";
    public static final String IDEMPOTENCY_KEY_REQUIRED = "idempotency_key_required";
    public static final String NEW_PASSWORD_TOO_WEAK = "new_password_too_weak";

    private ErrorCode() {
    }
}
