package com.athena.tms.common.error;

public class UnsupportedMediaTypeException
extends ApiException {
    public UnsupportedMediaTypeException(String code, String message) {
        super(415, code, message);
    }
}
