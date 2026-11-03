/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.stereotype.Service
 */
package com.athena.tms.service;

import com.athena.tms.common.UuidV7;
import com.athena.tms.common.error.StateConflictException;
import com.athena.tms.common.error.TooManyRequestsException;
import com.athena.tms.dao.GuestSessionRepository;
import com.athena.tms.dao.ReservationCacheRepository;
import com.athena.tms.dao.RoomQrTokenRepository;
import com.athena.tms.dao.RoomRepository;
import com.athena.tms.dao.TenantRepository;
import com.athena.tms.dto.GuestMenuItemView;
import com.athena.tms.dto.GuestSessionTokens;
import com.athena.tms.dto.ScanResponse;
import com.athena.tms.entity.GuestSession;
import com.athena.tms.entity.ReservationCache;
import com.athena.tms.entity.Room;
import com.athena.tms.entity.RoomQrToken;
import com.athena.tms.entity.Tenant;
import com.athena.tms.service.GuestTenantConfigReader;
import com.athena.tms.service.ScanIpRateLimiter;
import com.athena.tms.service.TenantScopedExecutor;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GuestScanService {
    private static final Logger log = LoggerFactory.getLogger(GuestScanService.class);
    static final String ROOM_VACANT = "room_vacant";
    private final TenantRepository tenantRepository;
    private final RoomQrTokenRepository roomQrTokenRepository;
    private final ReservationCacheRepository reservationCacheRepository;
    private final RoomRepository roomRepository;
    private final GuestSessionRepository guestSessionRepository;
    private final GuestTenantConfigReader configReader;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final ScanIpRateLimiter ipRateLimiter;
    private final Clock clock;

    public GuestScanService(TenantRepository tenantRepository, RoomQrTokenRepository roomQrTokenRepository, ReservationCacheRepository reservationCacheRepository, RoomRepository roomRepository, GuestSessionRepository guestSessionRepository, GuestTenantConfigReader configReader, TenantScopedExecutor tenantScopedExecutor, ScanIpRateLimiter ipRateLimiter, Clock clock) {
        this.tenantRepository = tenantRepository;
        this.roomQrTokenRepository = roomQrTokenRepository;
        this.reservationCacheRepository = reservationCacheRepository;
        this.roomRepository = roomRepository;
        this.guestSessionRepository = guestSessionRepository;
        this.configReader = configReader;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.ipRateLimiter = ipRateLimiter;
        this.clock = clock;
    }

    public ScanResponse scan(String qrToken, String clientIp) {
        TokenHit hit = this.resolveToken(qrToken);
        int threshold = hit != null ? hit.threshold() : this.ipRateLimiter.lastKnownThreshold(clientIp, 10);
        int recentFailures = this.ipRateLimiter.recentFailures(clientIp);
        if (recentFailures >= threshold) {
            log.warn("[alert][scan-rate-limit] \u6383\u78bc\u9650\u6d41\u89f8\u767c\uff1aip={} \u5341\u5206\u9418\u5167\u5df2\u5931\u6557 {} \u6b21\uff08\u9580\u6abb {}\uff09\uff0c\u672c\u6b21\u8acb\u6c42\u88ab\u64cb", new Object[]{clientIp, recentFailures, threshold});
            throw new TooManyRequestsException("\u6383\u78bc\u5931\u6557\u6b21\u6578\u904e\u591a\uff0c\u8acb\u7a0d\u5f8c\u518d\u8a66\u6216\u6d3d\u6ac3\u6aaf");
        }
        if (hit == null || !"active".equals(hit.token().getStatus())) {
            if (hit != null) {
                this.ipRateLimiter.recordFailure(clientIp, threshold);
            } else {
                this.ipRateLimiter.recordFailure(clientIp);
            }
            throw new StateConflictException(ROOM_VACANT, "QR \u7121\u6548\u6216\u623f\u9593\u76ee\u524d\u7121\u4f4f\u5ba2\uff0c\u8acb\u6d3d\u6ac3\u6aaf");
        }
        long tenantId = hit.token().getTenantId();
        return this.tenantScopedExecutor.callAsTenant(tenantId, () -> this.issueSession(tenantId, hit.token(), threshold, clientIp));
    }

    private ScanResponse issueSession(long tenantId, RoomQrToken qr, int threshold, String clientIp) {
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        ReservationCache current = this.reservationCacheRepository.findCurrentByRoom(tenantId, qr.getRoomId(), now).orElse(null);
        if (current == null) {
            this.ipRateLimiter.recordFailure(clientIp, threshold);
            throw new StateConflictException(ROOM_VACANT, "\u623f\u9593\u76ee\u524d\u7121\u4f4f\u5ba2\uff0c\u8acb\u6d3d\u6ac3\u6aaf");
        }
        Room room = this.roomRepository.findByTenantIdAndId(tenantId, qr.getRoomId()).orElseThrow(() -> new IllegalStateException("QR token \u6307\u5411\u7684\u623f\u9593\u4e0d\u5b58\u5728\uff08\u8cc7\u6599\u4e0d\u4e00\u81f4\uff09: " + String.valueOf(qr.getRoomId())));
        GuestSession session = new GuestSession();
        session.setId(UuidV7.generate());
        session.setTenantId(tenantId);
        session.setSessionToken(GuestSessionTokens.issue(tenantId));
        session.setRoomId(qr.getRoomId());
        session.setReservationId(current.getId());
        session.setExpiresAt(current.getCheckoutAt());
        session.setCreatedAt(now);
        session.setLastSeenAt(now);
        this.guestSessionRepository.save(session);
        return new ScanResponse(session.getSessionToken(), session.getExpiresAt(), room.getRoomNo(), this.configReader.infoPages(tenantId), GuestMenuItemView.fromMenu(this.configReader.requestMenu(tenantId)));
    }

    private TokenHit resolveToken(String qrToken) {
        for (Tenant tenant : this.tenantRepository.findAll()) {
            long tenantId = tenant.getId();
            TokenHit hit = this.tenantScopedExecutor.callAsTenant(tenantId, () -> this.roomQrTokenRepository.findByToken(qrToken).map(row -> new TokenHit((RoomQrToken)row, this.configReader.scanFailThreshold(tenantId))).orElse(null));
            if (hit == null) continue;
            return hit;
        }
        return null;
    }

    private record TokenHit(RoomQrToken token, int threshold) {
    }
}
