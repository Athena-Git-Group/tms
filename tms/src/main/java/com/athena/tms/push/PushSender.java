package com.athena.tms.push;

public interface PushSender {

    PushSendResult send(PushMessage message, PushDeviceAddress device);
}
