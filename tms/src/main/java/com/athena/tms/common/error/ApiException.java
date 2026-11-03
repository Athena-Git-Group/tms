/*
 * Decompiled with CFR 0.152.
 */
package com.athena.tms.common.error;

public abstract class ApiException
extends RuntimeException {
    private final int status;
    private final String code;
    private final java.util.Map<String, Object> context;

    protected ApiException(int status, String code, String message) {
        this(status, code, message, null);
    }

    /** context 對應 contracts Error.context：錯誤的業務脈絡（例如 doing_task_exists 的 taskId、roomNo）。 */
    protected ApiException(int status, String code, String message, java.util.Map<String, Object> context) {
        super(message);
        this.status = status;
        this.code = code;
        this.context = context;
    }

    public java.util.Map<String, Object> getContext() {
        return this.context;
    }

    public int getStatus() {
        return this.status;
    }

    public String getCode() {
        return this.code;
    }
}
