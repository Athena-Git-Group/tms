package com.athena.tms.push;

import java.util.UUID;

public record PushDeviceAddress(UUID deviceId, String pushToken, String platform) {

    public boolean isIos() {
        return "ios".equals(platform);
    }
}
