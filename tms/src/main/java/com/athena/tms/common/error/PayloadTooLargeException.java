package com.athena.tms.common.error;

public class PayloadTooLargeException
extends ApiException {
    public PayloadTooLargeException(String code, String message) {
        super(413, code, message);
    }
}
