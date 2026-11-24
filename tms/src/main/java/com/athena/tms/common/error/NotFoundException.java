package com.athena.tms.common.error;

import com.athena.tms.common.error.ApiException;

public class NotFoundException
extends ApiException {
    public NotFoundException(String message) {
        super(404, "not_found", message);
    }

    public NotFoundException(String message, java.util.Map<String, Object> context) {
        super(404, "not_found", message, context);
    }
}
