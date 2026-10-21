package com.athena.tms.controller;

import com.athena.tms.common.TenantContext;
import com.athena.tms.common.error.UnprocessableEntityException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import com.athena.tms.dto.GuestHomeView;
import com.athena.tms.dto.GuestMenuItemView;
import com.athena.tms.dto.GuestRatingRequest;
import com.athena.tms.dto.GuestRequestCreate;
import com.athena.tms.dto.GuestTaskView;
import com.athena.tms.dto.ScanRequest;
import com.athena.tms.dto.ScanResponse;
import com.athena.tms.service.GuestRequestService;
import com.athena.tms.service.GuestScanService;
import com.athena.tms.service.GuestTenantConfigReader;

/**
 * 住客入口的 HTTP 面。URL 照 spec 字面，/guest 前綴即角色矩陣放給
 * guest 的那扇門；body 與回應欄位一律 camelCase。
 * 除 /guest/scan 外全部端點都站在 auth filter 之後：TenantContext／
 * CurrentActor 已就位，controller 不自己解 token。
 */
@RestController
public class GuestController {

    private final GuestScanService scanService;
    private final GuestRequestService requestService;
    private final GuestTenantConfigReader configReader;

    public GuestController(GuestScanService scanService,
                           GuestRequestService requestService,
                           GuestTenantConfigReader configReader) {
        this.scanService = scanService;
        this.requestService = requestService;
        this.configReader = configReader;
    }

    @PostMapping("/guest/scan")
    public ScanResponse scan(@RequestBody ScanRequest request, HttpServletRequest http) {
        if (request.token() == null || request.token().isBlank()) {
            // 空 token 是客戶端 bug（422），不是掃碼失敗——不進 IP 失敗計數，
            // 否則前端一個迴圈 bug 就把整個飯店 Wi-Fi 的出口 IP 鎖死
            throw new UnprocessableEntityException("token 必填");
        }
        return scanService.scan(request.token().trim(), clientIp(http));
    }

    @GetMapping("/guest/home")
    public GuestHomeView home() {
        long tenantId = TenantContext.require();
        return new GuestHomeView(
                configReader.infoPages(tenantId),
                GuestMenuItemView.fromMenu(configReader.requestMenu(tenantId)));
    }

    @PostMapping("/guest/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public GuestTaskView createRequest(@RequestBody GuestRequestCreate request) {
        return requestService.create(request);
    }

    @GetMapping("/guest/requests")
    public List<GuestTaskView> listRequests() {
        return requestService.list();
    }

    @PostMapping("/guest/requests/{taskId}/cancel")
    public GuestTaskView cancelRequest(@PathVariable UUID taskId) {
        return requestService.cancel(taskId);
    }

    @PostMapping("/guest/requests/{taskId}/rating")
    public GuestTaskView rateRequest(@PathVariable UUID taskId, @RequestBody GuestRatingRequest request) {
        return requestService.rate(taskId, request);
    }

    /**
     * 掃碼失敗計數的 IP 來源。信 X-Forwarded-For 首值：部署形狀是單體加
     * 前置反向代理，remoteAddr 永遠是代理自己，按它計數會把
     * 全部住客算成同一人，一個攻擊者就能把所有人鎖在門外。代價是直連時
     * 此 header 可偽造，屬 MVP 已知限制（正式部署由邊緣層覆寫該 header）。
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
