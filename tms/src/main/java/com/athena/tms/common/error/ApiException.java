package com.athena.tms.common.error;

public abstract class ApiException
extends RuntimeException {
    private final int status;
    private final String code;
    private final java.util.Map<String, Object> context;
    private final java.util.List<ApiError.Detail> details;

    protected ApiException(int status, String code, String message) {
        this(status, code, message, null);
    }

    protected ApiException(int status, String code, String message, java.util.Map<String, Object> context) {
        this(status, code, message, context, null);
    }

    protected ApiException(
            int status, String code, String message, java.util.Map<String, Object> context,
            java.util.List<ApiError.Detail> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.context = context;
        this.details = details;
    }

    public java.util.List<ApiError.Detail> getDetails() {
        return this.details;
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
