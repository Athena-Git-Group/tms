package com.athena.tms.push;

public interface PushSender {

    /**
     * @return 永久性的結果（送出、地址失效、其他失敗）
     * @throws PushTransientException 推播服務暫時不可用，呼叫端應重試
     */
    PushSendResult send(PushMessage message, PushDeviceAddress device);
}
