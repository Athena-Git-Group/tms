package com.athena.tms.dto;

public record PushTokenRegisterRequest(String deviceFingerprint, String pushToken, String platform) {}
