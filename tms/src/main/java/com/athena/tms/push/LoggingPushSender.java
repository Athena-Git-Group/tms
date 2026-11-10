package com.athena.tms.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 沒有 FCM 憑證時的替身：把每一筆推播寫進日誌，一律回報「已送出」。
 * 送達紀錄因此在開發機上也會寫出 sent 的列，整條「outbox → Kafka → 消費者 → 紀錄」的路徑
 * 都走得到，只有最後一哩沒有真的打到手機。
 */
public class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public PushSendResult send(PushMessage message, PushDeviceAddress device) {
        log.info("[推播未啟用 FCM，只記日誌] 收件人={} 裝置={} 平台={} 事件={} 標題={} 去處={} 冪等鍵={}",
                message.recipientStaffId(), device.deviceId(), device.platform(), message.event(),
                message.title(), message.targetJson(), message.idempotencyKey());
        return PushSendResult.sent();
    }
}
