package com.athena.tms.service;

import com.athena.tms.dto.GuestSessionTokens;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.codes.QrTokenStatus;
import com.athena.tms.common.error.StateConflictException;
import com.athena.tms.common.error.TooManyRequestsException;
import com.athena.tms.entity.GuestSession;
import com.athena.tms.entity.ReservationCache;
import com.athena.tms.entity.Room;
import com.athena.tms.entity.RoomQrToken;
import com.athena.tms.entity.Tenant;
import com.athena.tms.dao.GuestSessionRepository;
import com.athena.tms.dao.ReservationCacheRepository;
import com.athena.tms.dao.RoomQrTokenRepository;
import com.athena.tms.dao.RoomRepository;
import com.athena.tms.dao.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import com.athena.tms.dto.GuestMenuItemView;
import com.athena.tms.dto.ScanResponse;

/**
 * 掃碼簽發住客工作階段。掃碼是全系統唯一「連租戶都還不知道」的入口（員工有 JWT
 * claim、住客後續請求有 token 前綴），而 room_qr_tokens 有 RLS——無租戶脈絡
 * 查 token 必然零列。解法是逐租戶以 TenantScopedExecutor 試查：token 全域
 * UNIQUE，至多一個租戶命中。線性掃租戶在多租戶量大時是已知成本，MVP 兩個
 * 租戶下換「QR token 不必編碼租戶、換格式不用重印貼紙」划算。
 *
 * <p>失敗一律 409 `room_vacant`＋「請洽櫃檯」語意，不區分「token 不存在／
 * 已作廢／房無住客」三種失敗：對拿著撿來貼紙的人，差異化錯誤訊息
 * 就是枚舉提示。
 */
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

    public GuestScanService(TenantRepository tenantRepository,
                            RoomQrTokenRepository roomQrTokenRepository,
                            ReservationCacheRepository reservationCacheRepository,
                            RoomRepository roomRepository,
                            GuestSessionRepository guestSessionRepository,
                            GuestTenantConfigReader configReader,
                            TenantScopedExecutor tenantScopedExecutor,
                            ScanIpRateLimiter ipRateLimiter,
                            Clock clock) {
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
        TokenHit hit = resolveToken(qrToken);

        // 門檻在限流判定之前解析：門檻是租戶可調的（A7），而租戶只有 token
        // 命中才知道；查不到 token 的請求（正是攻擊的長相）沿用該 IP 最近
        // 一次已知的租戶門檻——否則攻擊者混入猜測 token 就能繞開調低的門檻——
        // 全無紀錄時才退回預設門檻
        int threshold = hit != null
                ? hit.threshold()
                : ipRateLimiter.lastKnownThreshold(clientIp, GuestTenantConfigReader.DEFAULT_SCAN_FAIL_THRESHOLD);
        int recentFailures = ipRateLimiter.recentFailures(clientIp);
        if (recentFailures >= threshold) {
            // 掃碼失敗率告警的落點：MVP 的告警通道就是 WARN 日誌（運維在
            // 日誌面收斂），不另建告警資料表
            log.warn("[alert][scan-rate-limit] 掃碼限流觸發：ip={} 十分鐘內已失敗 {} 次（門檻 {}），本次請求被擋",
                    clientIp, recentFailures, threshold);
            throw new TooManyRequestsException("掃碼失敗次數過多，請稍後再試或洽櫃檯");
        }

        if (hit == null || !QrTokenStatus.ACTIVE.equals(hit.token().getStatus())) {
            if (hit != null) {
                ipRateLimiter.recordFailure(clientIp, threshold);
            } else {
                ipRateLimiter.recordFailure(clientIp);
            }
            throw new StateConflictException(ROOM_VACANT, "QR 無效或房間目前無住客，請洽櫃檯");
        }

        long tenantId = hit.token().getTenantId();
        return tenantScopedExecutor.callAsTenant(tenantId,
                () -> issueSession(tenantId, hit.token(), threshold, clientIp));
    }

    /**
     * 查當前訂單→簽 session→組資訊頁與選單，全程在已注入租戶的交易內。
     * 查無當前訂單時先記失敗再丟 409：丟例外會讓交易回滾，但 IP 計數器是
     * 程序內狀態、不隨交易回滾——順序反過來失敗就不被計數，限流被空房掃穿。
     */
    private ScanResponse issueSession(long tenantId, RoomQrToken qr, int threshold, String clientIp) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        ReservationCache current = reservationCacheRepository
                .findCurrentByRoom(tenantId, qr.getRoomId(), now)
                .orElse(null);
        if (current == null) {
            ipRateLimiter.recordFailure(clientIp, threshold);
            throw new StateConflictException(ROOM_VACANT, "房間目前無住客，請洽櫃檯");
        }
        Room room = roomRepository.findByTenantIdAndId(tenantId, qr.getRoomId())
                .orElseThrow(() -> new IllegalStateException(
                        "QR token 指向的房間不存在（資料不一致）: " + qr.getRoomId()));

        GuestSession session = new GuestSession();
        session.setId(UuidV7.generate());
        session.setTenantId(tenantId);
        // token 格式唯一出處是 GuestSessionTokens：租戶前綴
        // 是後續請求在 RLS 下找回這列 session 的唯一線索
        session.setSessionToken(GuestSessionTokens.issue(tenantId));
        session.setRoomId(qr.getRoomId());
        session.setReservationId(current.getId());
        session.setExpiresAt(current.getCheckoutAt());
        session.setCreatedAt(now);
        session.setLastSeenAt(now);
        guestSessionRepository.save(session);

        return new ScanResponse(
                session.getSessionToken(),
                session.getExpiresAt(),
                room.getRoomNo(),
                configReader.infoPages(tenantId),
                GuestMenuItemView.fromMenu(configReader.requestMenu(tenantId)));
    }

    /** @return token 命中的租戶脈絡（含該租戶門檻）；全租戶都沒有這枚 token 時為 null。 */
    private TokenHit resolveToken(String qrToken) {
        for (Tenant tenant : tenantRepository.findAll()) {
            long tenantId = tenant.getId();
            TokenHit hit = tenantScopedExecutor.callAsTenant(tenantId,
                    () -> roomQrTokenRepository.findByToken(qrToken)
                            .map(row -> new TokenHit(row, configReader.scanFailThreshold(tenantId)))
                            .orElse(null));
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private record TokenHit(RoomQrToken token, int threshold) {
    }
}
