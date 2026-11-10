package com.athena.tms.push;

/**
 * 把一筆推播送到一台裝置。實作有兩個：{@link FcmPushSender}（真的打 Firebase Cloud Messaging）
 * 與 {@link LoggingPushSender}（沒有憑證時只寫日誌）。由 {@link PushSenderConfig} 依設定擇一。
 */
public interface PushSender {

    /**
     * @return 永久性的結果（送出、地址失效、其他失敗）
     * @throws PushTransientException 推播服務暫時不可用，呼叫端應重試
     */
    PushSendResult send(PushMessage message, PushDeviceAddress device);
}
