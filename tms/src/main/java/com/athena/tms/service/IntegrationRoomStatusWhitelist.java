/*
 * Decompiled with CFR 0.152.
 */
package com.athena.tms.service;

import java.util.Map;
import java.util.Set;

public final class IntegrationRoomStatusWhitelist {
    public static final Map<String, String> TYPE_TO_HK_STATE = Map.of("inspection_passed", "inspected");
    public static final Set<String> OUTBOUND_TYPES = TYPE_TO_HK_STATE.keySet();

    private IntegrationRoomStatusWhitelist() {
    }

    public static String hkStateFor(String taskEventType) {
        return TYPE_TO_HK_STATE.get(taskEventType);
    }
}
