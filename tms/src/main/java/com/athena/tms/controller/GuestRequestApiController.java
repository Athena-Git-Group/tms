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

    public record GuestSessionScanRequest(UUID roomId, UUID reservationId) {}

    /**
     * 櫃檯替住客開 session：飯店只取自呼叫者的登入，權杖只由伺服器產生。
     * 房間與訂單都限定在呼叫者的飯店裡找，別家飯店的資料一律回 404，
     * 否則呼叫者能用回應碼探出別家飯店有哪些房間與訂單。
     *
     * <p>到期時間一律是綁定訂單的退房時刻，和住客自己掃房間 QR 開的 session 同一條規則，請求帶來的到期時間不採用。
     * 讓呼叫端指定的話，櫃檯開出來的 session 可以活過退房，下一位住客入住後前一位住客還能看到這間房的需求。
     * 訂單已經過了退房時刻就回 409，不開一個一出生就過期的 session。
     */
    @PostMapping("/guest-sessions/scan")
    public GuestSession scan(@RequestBody GuestSessionScanRequest request) {
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
