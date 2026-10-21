package com.athena.tms.service;

import com.athena.tms.common.codes.TaskEventType;

import java.util.Map;
import java.util.Set;

/**
 * 整合面外流事件的封閉白名單：task_events.type → 對外 hk_state 的對映。
 * 不在這張表裡的事件種類一律不出現在整合 API（拉取與 SSE 共用同一張表），
 * 內部新增事件種類時預設就是「不外流」，不會因為忘了改整合層而把內部
 * 事件漏給 PMS。v1 只外流查房通過一種：inspection_passed → inspected。
 */
public final class IntegrationRoomStatusWhitelist {

    /** key = task_events.type，value = 對外 payload 的 hk_state 值。 */
    public static final Map<String, String> TYPE_TO_HK_STATE =
            Map.of(TaskEventType.INSPECTION_PASSED, "inspected");

    /** 查詢用的 type 集合（TYPE_TO_HK_STATE 的 key 檢視）。 */
    public static final Set<String> OUTBOUND_TYPES = TYPE_TO_HK_STATE.keySet();

    private IntegrationRoomStatusWhitelist() {
    }

    /** 命中白名單回對外 hk_state，未命中回 null（代表不外流）。 */
    public static String hkStateFor(String taskEventType) {
        return TYPE_TO_HK_STATE.get(taskEventType);
    }
}
