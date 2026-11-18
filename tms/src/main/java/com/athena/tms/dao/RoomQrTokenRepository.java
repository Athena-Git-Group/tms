package com.athena.tms.dao;

import com.athena.tms.entity.RoomQrToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomQrTokenRepository
extends JpaRepository<RoomQrToken, UUID> {
    public Optional<RoomQrToken> findByToken(String var1);

    public Optional<RoomQrToken> findByTenantIdAndRoomIdAndStatus(Long var1, UUID var2, String var3);
}
