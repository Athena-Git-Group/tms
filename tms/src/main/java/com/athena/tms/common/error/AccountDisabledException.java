package com.athena.tms.common.error;

public class AccountDisabledException extends ApiException {
    public AccountDisabledException(String message) {
        super(403, "account_disabled", message);
    }
}
