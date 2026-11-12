package com.athena.tms.common.error;

import java.util.Map;

public class AccountLockedException extends ApiException {
    public AccountLockedException(String message, Map<String, Object> context) {
        super(429, "account_locked", message, context);
    }
}
