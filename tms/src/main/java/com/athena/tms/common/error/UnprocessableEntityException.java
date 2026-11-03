/*
 * Decompiled with CFR 0.152.
 */
package com.athena.tms.common.error;

import com.athena.tms.common.error.ApiException;

public class UnprocessableEntityException
extends ApiException {
    public static final String DEFAULT_CODE = "validation_failed";

    public UnprocessableEntityException(String message) {
        this(DEFAULT_CODE, message);
    }

    public UnprocessableEntityException(String code, String message) {
        super(422, code, message);
    }
}
