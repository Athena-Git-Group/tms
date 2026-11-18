package com.athena.tms.common.error;

import com.athena.tms.common.error.ApiException;

public class UnauthorizedException
extends ApiException {
    public UnauthorizedException(String message) {
        super(401, "unauthorized", message);
    }
}
