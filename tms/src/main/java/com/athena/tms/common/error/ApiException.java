package com.athena.tms.common.error;

public abstract class ApiException
extends RuntimeException {
    private final int status;
    private final String code;
    private final java.util.Map<String, Object> context;

    protected ApiException(int status, String code, String message) {
        this(status, code, message, null);
    }

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
