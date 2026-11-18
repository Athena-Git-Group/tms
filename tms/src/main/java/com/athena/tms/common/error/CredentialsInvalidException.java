package com.athena.tms.common.error;

public class CredentialsInvalidException extends ApiException {
    public CredentialsInvalidException(String message) {
        super(401, "credentials_invalid", message);
    }
}
