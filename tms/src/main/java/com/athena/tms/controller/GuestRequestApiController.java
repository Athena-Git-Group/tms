package com.athena.tms.controller;

import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.error.StateConflictException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dao.GuestSessionRepository;
import com.athena.tms.dto.GuestRequestCreate;
import com.athena.tms.dto.GuestTaskView;
import com.athena.tms.entity.GuestSession;
import com.athena.tms.service.GuestRequestService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GuestRequestApiController {

    private final GuestRequestService guestRequestService;
    private final GuestSessionRepository guestSessionRepository;
    private final Clock clock;

    public GuestRequestApiController(
            GuestRequestService guestRequestService, GuestSessionRepository guestSessionRepository, Clock clock) {
        this.guestRequestService = guestRequestService;
        this.guestSessionRepository = guestSessionRepository;
        this.clock = clock;
    }

    public record GuestSessionScanRequest(
            Long tenantId, String sessionToken, UUID roomId, UUID reservationId, OffsetDateTime expiresAt) {}

    @PostMapping("/guest-sessions/scan")
    public GuestSession scan(@RequestBody GuestSessionScanRequest request) {
        if (request == null || request.tenantId() == null || request.sessionToken() == null
                || request.sessionToken().isBlank() || request.roomId() == null
                || request.reservationId() == null || request.expiresAt() == null) {
            throw new UnprocessableEntityException(
                    "tenantId、sessionToken、roomId、reservationId、expiresAt 皆必填");
        }
        if (guestSessionRepository.findBySessionToken(request.sessionToken()).isPresent()) {
            throw new StateConflictException("session_token 已被使用（全域 UNIQUE）");
        }
        TenantContext.set(request.tenantId());
        try {
            GuestSession session = new GuestSession();
            session.setId(UuidV7.generate());
            session.setTenantId(request.tenantId());
            session.setSessionToken(request.sessionToken());
            session.setRoomId(request.roomId());
            session.setReservationId(request.reservationId());
            session.setExpiresAt(request.expiresAt());
            session.setCreatedAt(OffsetDateTime.now(clock));
            return guestSessionRepository.save(session);
        } finally {
            TenantContext.clear();
        }
    }

    @PostMapping("/guest-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public GuestTaskView create(@RequestBody GuestRequestCreate request) {
        return guestRequestService.create(request);
    }

    @GetMapping("/guest-requests")
    public List<GuestTaskView> list() {
        return guestRequestService.list();
    }

    @PostMapping("/guest-requests/{id}/cancel")
    public GuestTaskView cancel(@PathVariable("id") UUID id) {
        return guestRequestService.cancel(id);
    }
}
