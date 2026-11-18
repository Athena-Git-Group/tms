package com.athena.tms.service;

import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.dao.GuestSessionRepository;
import com.athena.tms.dao.RoomQrTokenRepository;
import com.athena.tms.dao.RoomRepository;
import com.athena.tms.dto.QrRotateResponse;
import com.athena.tms.dto.QrTokens;
import com.athena.tms.dto.RoomQrResponse;
import com.athena.tms.entity.GuestSession;
import com.athena.tms.entity.Room;
import com.athena.tms.entity.RoomQrToken;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomQrService {
    private final RoomRepository roomRepository;
    private final RoomQrTokenRepository roomQrTokenRepository;
    private final GuestSessionRepository guestSessionRepository;
    private final JdbcTemplate jdbcTemplate;

    public RoomQrService(RoomRepository roomRepository, RoomQrTokenRepository roomQrTokenRepository, GuestSessionRepository guestSessionRepository, JdbcTemplate jdbcTemplate) {
        this.roomRepository = roomRepository;
        this.roomQrTokenRepository = roomQrTokenRepository;
        this.guestSessionRepository = guestSessionRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public RoomQrResponse printData(String roomNo) {
        long tenantId = TenantContext.require();
        Room room = this.findRoom(tenantId, roomNo);
        RoomQrToken token = this.roomQrTokenRepository.findByTenantIdAndRoomIdAndStatus(tenantId, room.getId(), "active").orElseGet(() -> this.issue(tenantId, room.getId()));
        return new RoomQrResponse(room.getRoomNo(), token.getToken(), token.getVersion(), token.getCreatedAt());
    }

    @Transactional
    public QrRotateResponse rotate(String roomNo) {
        long tenantId = TenantContext.require();
        Room room = this.findRoom(tenantId, roomNo);
        OffsetDateTime now = OffsetDateTime.now();
        this.roomQrTokenRepository.findByTenantIdAndRoomIdAndStatus(tenantId, room.getId(), "active").ifPresent(old -> {
            old.setStatus("revoked");
            old.setRevokedAt(now);
            this.roomQrTokenRepository.saveAndFlush(old);
        });
        RoomQrToken fresh = this.issue(tenantId, room.getId());
        List<GuestSession> sessions = this.guestSessionRepository.findByTenantIdAndRoomIdAndRevokedAtIsNull(tenantId, room.getId());
        for (GuestSession session : sessions) {
            session.setRevokedAt(now);
        }
        this.guestSessionRepository.saveAll(sessions);
        return new QrRotateResponse(room.getRoomNo(), fresh.getToken(), fresh.getVersion(), fresh.getCreatedAt(), sessions.size());
    }

    private Room findRoom(long tenantId, String roomNo) {
        return this.roomRepository.findByTenantIdAndRoomNo(tenantId, roomNo).orElseThrow(() -> new NotFoundException("查無房號 " + roomNo));
    }

    private RoomQrToken issue(long tenantId, UUID roomId) {
        Integer maxVersion = (Integer)this.jdbcTemplate.queryForObject("SELECT COALESCE(MAX(version), 0) FROM room_qr_tokens WHERE tenant_id = ? AND room_id = ?", Integer.class, new Object[]{tenantId, roomId});
        RoomQrToken token = new RoomQrToken();
        token.setId(UuidV7.generate());
        token.setTenantId(tenantId);
        token.setRoomId(roomId);
        token.setToken(QrTokens.generate());
        token.setVersion((maxVersion == null ? 0 : maxVersion) + 1);
        token.setStatus("active");
        token.setCreatedAt(OffsetDateTime.now());
        return (RoomQrToken)this.roomQrTokenRepository.save(token);
    }
}
