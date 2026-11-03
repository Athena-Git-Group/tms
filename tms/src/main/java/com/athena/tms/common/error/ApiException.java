/*
 * Decompiled with CFR 0.152.
 */
package com.athena.tms.common.error;

public abstract class ApiException
extends RuntimeException {
    private final int status;
    private final String code;

    protected ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return this.status;
    }

    public String getCode() {
        return this.code;
    }
}
