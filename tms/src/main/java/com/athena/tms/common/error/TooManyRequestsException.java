package com.athena.tms.common.error;

import com.athena.tms.common.error.ApiException;

public class TooManyRequestsException
extends ApiException {
    public TooManyRequestsException(String message) {
        super(429, "rate_limited", message);
    }
}
