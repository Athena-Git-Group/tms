package com.athena.tms.controller;

import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.config.TestSupportConfig;
import com.athena.tms.dao.GuestSessionRepository;
import com.athena.tms.dao.ReservationCacheRepository;
import com.athena.tms.dao.RoomRepository;
import com.athena.tms.dto.GuestSessionTokens;
import com.athena.tms.entity.GuestSession;
import com.athena.tms.entity.ReservationCache;
import com.athena.tms.entity.Room;
import com.athena.tms.service.RoomQrService;
import com.athena.tms.service.ScanIpRateLimiter;
import com.athena.tms.service.TenantScopedExecutor;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test")
@Profile(TestSupportConfig.PROFILE)
public class TestSupportController {

    private final ScanIpRateLimiter scanIpRateLimiter;
    private final GuestSessionRepository guestSessionRepository;
    private final ReservationCacheRepository reservationCacheRepository;
    private final RoomRepository roomRepository;
    private final RoomQrService roomQrService;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final Clock clock;

    public TestSupportController(
            ScanIpRateLimiter scanIpRateLimiter,
            GuestSessionRepository guestSessionRepository,
            ReservationCacheRepository reservationCacheRepository,
            RoomRepository roomRepository,
            RoomQrService roomQrService,
            TenantScopedExecutor tenantScopedExecutor,
            Clock clock) {
        this.scanIpRateLimiter = scanIpRateLimiter;
        this.guestSessionRepository = guestSessionRepository;
        this.reservationCacheRepository = reservationCacheRepository;
        this.roomRepository = roomRepository;
        this.roomQrService = roomQrService;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.clock = clock;
    }

    public record RevokeGuestSessionRequest(String sessionToken) {}

    public record InHouseRoom(String roomNo, UUID roomId, UUID reservationId, String guestName, String qrToken) {}

    /** 只清掃碼失敗次數；住客每間房的送單名額是從 tasks 現數的，沒有另外的狀態可清，要歸零就清業務資料或撥時鐘。 */
    @DeleteMapping("/rate-limits")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetRateLimits() {
        scanIpRateLimiter.reset();
    }

    @PostMapping("/guest-sessions/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeGuestSession(@RequestBody RevokeGuestSessionRequest request) {
        Long tenantId = request == null ? null : GuestSessionTokens.parseTenantId(request.sessionToken());
        if (tenantId == null) {
            throw new UnprocessableEntityException("sessionToken 必填，格式是「飯店編號.隨機字串」");
        }
        tenantScopedExecutor.runAsTenant(tenantId, () -> {
            GuestSession session = guestSessionRepository.findBySessionToken(request.sessionToken())
                    .orElseThrow(() -> new NotFoundException("查無這個住客工作階段"));
            session.setRevokedAt(OffsetDateTime.now(clock));
            guestSessionRepository.save(session);
        });
    }

    /** 現在有人入住的房間，各帶一枚可掃的房間 QR 權杖。「有人入住」的條件和住客掃房間 QR 開工作階段時相同。 */
    @GetMapping("/tenants/{tenantId}/in-house-rooms")
    public List<InHouseRoom> inHouseRooms(@PathVariable long tenantId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return tenantScopedExecutor.callAsTenant(tenantId, () -> reservationCacheRepository.findCurrent(tenantId, now)
                .stream()
                .map(reservation -> inHouseRoom(tenantId, reservation))
                .sorted(Comparator.comparing(InHouseRoom::roomNo))
                .toList());
    }

    private InHouseRoom inHouseRoom(long tenantId, ReservationCache reservation) {
        Room room = roomRepository.findByTenantIdAndId(tenantId, reservation.getRoomId()).orElseThrow();
        return new InHouseRoom(
                room.getRoomNo(),
                room.getId(),
                reservation.getId(),
                reservation.getGuestName(),
                roomQrService.activeToken(tenantId, room.getId()).getToken());
    }
}
