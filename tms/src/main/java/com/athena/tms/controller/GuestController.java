package com.athena.tms.controller;

import com.athena.tms.common.TenantContext;
import com.athena.tms.common.error.UnprocessableEntityException;
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
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GuestController {
    private final GuestScanService scanService;
    private final GuestRequestService requestService;
    private final GuestTenantConfigReader configReader;

    public GuestController(GuestScanService scanService, GuestRequestService requestService, GuestTenantConfigReader configReader) {
        this.scanService = scanService;
        this.requestService = requestService;
        this.configReader = configReader;
    }

    @PostMapping(value={"/guest/scan"})
    public ScanResponse scan(@RequestBody ScanRequest request, HttpServletRequest http) {
        if (request.token() == null || request.token().isBlank()) {
            throw new UnprocessableEntityException("token 必填");
        }
        return this.scanService.scan(request.token().trim(), GuestController.clientIp(http));
    }

    @GetMapping(value={"/guest/home"})
    public GuestHomeView home() {
        long tenantId = TenantContext.require();
        return new GuestHomeView(this.configReader.infoPages(tenantId), GuestMenuItemView.fromMenu(this.configReader.requestMenu(tenantId)));
    }

    @PostMapping(value={"/guest/requests"})
    @ResponseStatus(value=HttpStatus.CREATED)
    public GuestTaskView createRequest(@RequestBody GuestRequestCreate request) {
        return this.requestService.create(request);
    }

    @GetMapping(value={"/guest/requests"})
    public List<GuestTaskView> listRequests() {
        return this.requestService.list();
    }

    @PostMapping(value={"/guest/requests/{taskId}/cancel"})
    public GuestTaskView cancelRequest(@PathVariable UUID taskId) {
        return this.requestService.cancel(taskId);
    }

    @PostMapping(value={"/guest/requests/{taskId}/rating"})
    public GuestTaskView rateRequest(@PathVariable UUID taskId, @RequestBody GuestRatingRequest request) {
        return this.requestService.rate(taskId, request);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
