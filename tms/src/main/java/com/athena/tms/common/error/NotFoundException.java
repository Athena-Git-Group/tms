/*
 * Decompiled with CFR 0.152.
 */
package com.athena.tms.common.error;

import com.athena.tms.common.error.ApiException;

public class NotFoundException
extends ApiException {
    public NotFoundException(String message) {
        super(404, "not_found", message);
    }
}
