package com.athena.tms.controller;

import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.StateConflictException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dao.GuestSessionRepository;
import com.athena.tms.dao.ReservationCacheRepository;
import com.athena.tms.dao.RoomRepository;
import com.athena.tms.dto.GuestRequestCreate;
import com.athena.tms.dto.GuestSessionTokens;
import com.athena.tms.dto.GuestTaskView;
import com.athena.tms.entity.GuestSession;
import com.athena.tms.entity.ReservationCache;
import com.athena.tms.service.GuestRequestService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GuestRequestApiController {

    private final GuestRequestService guestRequestService;
    private final GuestSessionRepository guestSessionRepository;
    private final RoomRepository roomRepository;
    private final ReservationCacheRepository reservationCacheRepository;
    private final Clock clock;

    public GuestRequestApiController(
            GuestRequestService guestRequestService, GuestSessionRepository guestSessionRepository,
            RoomRepository roomRepository, ReservationCacheRepository reservationCacheRepository, Clock clock) {
        this.guestRequestService = guestRequestService;
        this.guestSessionRepository = guestSessionRepository;
        this.roomRepository = roomRepository;
        this.reservationCacheRepository = reservationCacheRepository;
        this.clock = clock;
    }

    public record OpenGuestSessionRequest(UUID roomId, UUID reservationId) {}

    @PostMapping("/guest-sessions/scan")
    public GuestSession openGuestSessionUntilCheckout(@RequestBody OpenGuestSessionRequest request) {
        if (request == null || request.roomId() == null) {
            throw new UnprocessableEntityException("roomId 必填");
        }
        Long tenantId = TenantContext.require();
        OffsetDateTime now = OffsetDateTime.now(clock);
        roomRepository.findByTenantIdAndId(tenantId, request.roomId())
                .orElseThrow(() -> new NotFoundException("查無此房間"));
        ReservationCache reservation = request.reservationId() != null
                ? reservationCacheRepository.findByTenantIdAndId(tenantId, request.reservationId())
                        .filter(found -> request.roomId().equals(found.getRoomId()))
                        .orElseThrow(() -> new NotFoundException("查無此訂單"))
                : reservationCacheRepository.findCurrentByRoom(tenantId, request.roomId(), now)
                        .orElseThrow(() -> new StateConflictException("room_vacant", "房間目前無住客"));
        if (!reservation.getCheckoutAt().isAfter(now)) {
            throw new StateConflictException("reservation_ended", "這筆訂單已經過了退房時刻");
        }
        GuestSession session = new GuestSession();
        session.setId(UuidV7.generate());
        session.setTenantId(tenantId);
        session.setSessionToken(GuestSessionTokens.issue(tenantId));
        session.setRoomId(request.roomId());
        session.setReservationId(reservation.getId());
        session.setExpiresAt(reservation.getCheckoutAt());
        session.setCreatedAt(now);
        return guestSessionRepository.save(session);
    }

    @PostMapping("/guest-requests")
    public ResponseEntity<GuestTaskView> create(@RequestBody GuestRequestCreate request) {
        GuestTaskView created = guestRequestService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .headers(GuestRequestQuotaHeaders.of(guestRequestService.quota()))
                .body(created);
    }

    @GetMapping("/guest-requests")
    public ResponseEntity<List<GuestTaskView>> list() {
        return ResponseEntity.ok()
                .headers(GuestRequestQuotaHeaders.of(guestRequestService.quota()))
                .body(guestRequestService.list());
    }

    @PostMapping("/guest-requests/{id}/cancel")
    public GuestTaskView cancel(@PathVariable("id") UUID id) {
        return guestRequestService.cancel(id);
    }
}
