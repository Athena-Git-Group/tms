package com.athena.tms.common.error;

import com.athena.tms.common.error.ApiException;

public class ForbiddenException
extends ApiException {
    public ForbiddenException(String message) {
        super(403, "forbidden", message);
    }

    public ForbiddenException(String code, String message) {
        super(403, code, message);
    }
}
