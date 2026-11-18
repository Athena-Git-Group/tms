package com.athena.tms.push;

public record PushSendResult(Outcome outcome, String error) {

    public enum Outcome {
        SENT,
        INVALID_TOKEN,
        FAILED
    }

    public static PushSendResult sent() {
        return new PushSendResult(Outcome.SENT, null);
    }

    public static PushSendResult invalidToken(String error) {
        return new PushSendResult(Outcome.INVALID_TOKEN, error);
    }

    public static PushSendResult failed(String error) {
        return new PushSendResult(Outcome.FAILED, error);
    }
}
