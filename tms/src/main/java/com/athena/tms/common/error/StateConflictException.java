package com.athena.tms.common.error;

/**
 * 409：狀態轉移的前提條件（目前狀態、操作者是誰等）直接寫在 UPDATE 的 WHERE
 * 裡，條件不符就一列也不改；這個例外代表那次 UPDATE 剛好改動 0 列（認領撞人、
 * 收回太晚、非法轉移等）。預設 code=state_conflict；同為 409 但另有語意的情境
 * 可帶自訂 code（例：room_vacant）。
 */
public class StateConflictException extends ApiException {

    public static final String DEFAULT_CODE = "state_conflict";

    public StateConflictException(String message) {
        this(DEFAULT_CODE, message);
    }

    public StateConflictException(String code, String message) {
        super(409, code, message);
    }
}
