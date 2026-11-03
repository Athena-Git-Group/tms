/*
 * Decompiled with CFR 0.152.
 */
package com.athena.tms.service;

import com.athena.tms.common.error.ApiException;

public class IntegrationBadRequestException
extends ApiException {
    public IntegrationBadRequestException(String message) {
        super(400, "bad_request", message);
    }
}
