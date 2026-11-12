package com.athena.tms.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
