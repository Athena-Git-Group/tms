package com.athena.tms.service;

import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.codes.QrTokenStatus;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.entity.GuestSession;
import com.athena.tms.entity.Room;
import com.athena.tms.entity.RoomQrToken;
import com.athena.tms.dao.GuestSessionRepository;
import com.athena.tms.dao.RoomQrTokenRepository;
import com.athena.tms.dao.RoomRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.athena.tms.dto.QrRotateResponse;
import com.athena.tms.dto.QrTokens;
import com.athena.tms.dto.RoomQrResponse;

/**
 * 房間 QR 的簽發與輪替。
 *
 * <p>spec 只給兩個端點（GET 列印、POST rotate），沒有獨立的「簽發」端點——
 * 簽發因此收進 GET：第一次列印時房裡還沒有 active token，當場簽一枚。
 * 若反過來要求「先打初始化端點才可列印」，新開房就多一個必踩的順序依賴，
 * 漏打的那間房會在櫃檯列印時才爆 404，把設定錯誤拖到最晚才被發現。
 *
 * <p>輪替的三件事必須同一筆交易：舊 token 標 revoked、新 token
 * version+1 生效、該房全部未撤銷 guest_sessions 寫上 revoked_at。拆開任何
 * 一件都開洞——先撤 token 不撤 session，輪替前掃過的人繼續用；先發新 token
 * 再撤舊的中間當機，一房兩枚 active 直接違反 partial unique。
 */
@Service
public class RoomQrService {

    private final RoomRepository roomRepository;
    private final RoomQrTokenRepository roomQrTokenRepository;
    private final GuestSessionRepository guestSessionRepository;
    private final JdbcTemplate jdbcTemplate;

    public RoomQrService(RoomRepository roomRepository,
                         RoomQrTokenRepository roomQrTokenRepository,
                         GuestSessionRepository guestSessionRepository,
                         JdbcTemplate jdbcTemplate) {
        this.roomRepository = roomRepository;
        this.roomQrTokenRepository = roomQrTokenRepository;
        this.guestSessionRepository = guestSessionRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 回列印資料；該房還沒有 active token 時當場簽發第一枚。 */
    @Transactional
    public RoomQrResponse printData(String roomNo) {
        long tenantId = TenantContext.require();
        Room room = findRoom(tenantId, roomNo);
        RoomQrToken token = roomQrTokenRepository
                .findByTenantIdAndRoomIdAndStatus(tenantId, room.getId(), QrTokenStatus.ACTIVE)
                .orElseGet(() -> issue(tenantId, room.getId()));
        return new RoomQrResponse(room.getRoomNo(), token.getToken(), token.getVersion(), token.getCreatedAt());
    }

    /** 輪替：三件事同一筆交易（撤舊、發新、連坐撤銷住客工作階段）。 */
    @Transactional
    public QrRotateResponse rotate(String roomNo) {
        long tenantId = TenantContext.require();
        Room room = findRoom(tenantId, roomNo);
        OffsetDateTime now = OffsetDateTime.now();

        roomQrTokenRepository
                .findByTenantIdAndRoomIdAndStatus(tenantId, room.getId(), QrTokenStatus.ACTIVE)
                .ifPresent(old -> {
                    old.setStatus(QrTokenStatus.REVOKED);
                    old.setRevokedAt(now);
                    // 立刻 flush：Hibernate 預設先 INSERT 後 UPDATE，不先把這筆
                    // UPDATE 送下去，新 token 的 INSERT 會在 flush 時撞上
                    // uq_room_qr_tokens_one_active（舊列當時還是 active）
                    roomQrTokenRepository.saveAndFlush(old);
                });

        RoomQrToken fresh = issue(tenantId, room.getId());

        // 連坐撤銷：輪替前掃過的人也立即失效。逐列寫 revoked_at 而非
        // 批次 UPDATE SQL——該房 session 數以個位數計，省不了什麼；走 JPA
        // 實體路徑讓「誰被撤了」留在持久化上下文裡，rotate 回應才有數字可報
        List<GuestSession> sessions =
                guestSessionRepository.findByTenantIdAndRoomIdAndRevokedAtIsNull(tenantId, room.getId());
        for (GuestSession session : sessions) {
            session.setRevokedAt(now);
        }
        guestSessionRepository.saveAll(sessions);

        return new QrRotateResponse(room.getRoomNo(), fresh.getToken(), fresh.getVersion(),
                fresh.getCreatedAt(), sessions.size());
    }

    /** 跨租戶／不存在的房號一律 404，不洩漏存在性。 */
    private Room findRoom(long tenantId, String roomNo) {
        return roomRepository.findByTenantIdAndRoomNo(tenantId, roomNo)
                .orElseThrow(() -> new NotFoundException("查無房號 " + roomNo));
    }

    /**
     * 簽發新 token。version 取該房歷史最大值 +1 而非只看 active 列：
     * 就算哪天出現「撤銷了卻沒補發」的空檔，版本號也保持單調遞增，
     * 列印在貼紙上的版本才能當「哪張比較新」的依據。
     */
    private RoomQrToken issue(long tenantId, UUID roomId) {
        Integer maxVersion = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version), 0) FROM room_qr_tokens WHERE tenant_id = ? AND room_id = ?",
                Integer.class, tenantId, roomId);
        RoomQrToken token = new RoomQrToken();
        token.setId(UuidV7.generate());
        token.setTenantId(tenantId);
        token.setRoomId(roomId);
        token.setToken(QrTokens.generate());
        token.setVersion((maxVersion == null ? 0 : maxVersion) + 1);
        token.setStatus(QrTokenStatus.ACTIVE);
        token.setCreatedAt(OffsetDateTime.now());
        return roomQrTokenRepository.save(token);
    }
}
