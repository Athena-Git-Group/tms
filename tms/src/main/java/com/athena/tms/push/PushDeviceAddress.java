package com.athena.tms.push;

import java.util.UUID;

/** 一台可以收推播的裝置：staff_devices 的 id、推播地址（push_token）與平台（ios、android 或 null）。 */
public record PushDeviceAddress(UUID deviceId, String pushToken, String platform) {

    public boolean isIos() {
        return "ios".equals(platform);
    }
}
