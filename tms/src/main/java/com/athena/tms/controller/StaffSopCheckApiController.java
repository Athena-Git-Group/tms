package com.athena.tms.controller;

import com.athena.tms.dto.SopCheckRequest;
import com.athena.tms.dto.SopCheckResponse;
import com.athena.tms.service.StaffTaskService;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 以 itemKey 定位的 SOP 勾記入口在 {@code /api/v1} 前綴下的對應路由。
 *
 * <p>行動端沿用的 {@link StaffTaskController} 把這個操作掛在沒有前綴的
 * {@code /staff/tasks/{taskId}/sop/{itemKey}}，而走 {@code /api/v1} 的呼叫端
 * （例如查一下開啟前剛跳過一項 SOP 的情境）需要同一個操作也能從前綴路徑進來。
 * 這裡只轉接到同一個 {@link StaffTaskService#sopCheck}，兩條路徑行為完全一致；
 * 若各自實作，勾記規則一旦修改就會兩邊不同步。
 */
@RestController
public class StaffSopCheckApiController {

    private final StaffTaskService staffTaskService;

    public StaffSopCheckApiController(StaffTaskService staffTaskService) {
        this.staffTaskService = staffTaskService;
    }

    @PostMapping("/api/v1/staff/tasks/{taskId}/sop/{itemKey}")
    public SopCheckResponse sopCheck(
            @PathVariable UUID taskId, @PathVariable String itemKey, @RequestBody SopCheckRequest request) {
        return SopCheckResponse.from(staffTaskService.sopCheck(taskId, itemKey, request));
    }
}
