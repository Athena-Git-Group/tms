package com.athena.tms.common.error;

public class CurrentPasswordInvalidException extends ApiException {
    public CurrentPasswordInvalidException(String message) {
        super(401, "current_password_invalid", message);
    }
}
