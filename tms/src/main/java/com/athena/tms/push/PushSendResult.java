package com.athena.tms.push;

/**
 * 對一台裝置送一次推播的結果。只有「確定不會再成功」的結果走這裡回傳；
 * 暫時性錯誤（推播服務暫時不可用、額度用盡）改丟 {@link PushTransientException}，
 * 讓消費者整筆重試，因為重試有機會成功，而這裡列的三種結果重試也不會改變。
 */
public record PushSendResult(Outcome outcome, String error) {

    public enum Outcome {
        /** 推播服務已收下這筆訊息。 */
        SENT,
        /** 推播地址已失效（裝置解除安裝、權限被關、地址格式不對），應把該裝置標為已撤銷。 */
        INVALID_TOKEN,
        /** 其他永久性失敗，例如憑證與 App 的專案對不上。 */
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
