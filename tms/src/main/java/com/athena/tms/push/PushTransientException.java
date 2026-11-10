package com.athena.tms.push;

/** 推播服務暫時送不出去（不可用、內部錯誤、額度用盡）。消費者收到這個例外會退避後重試同一筆。 */
public class PushTransientException extends RuntimeException {

    public PushTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
