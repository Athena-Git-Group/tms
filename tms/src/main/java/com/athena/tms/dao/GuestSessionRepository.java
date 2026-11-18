package com.athena.tms.dao;

import com.athena.tms.entity.GuestSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestSessionRepository
extends JpaRepository<GuestSession, UUID> {
    public Optional<GuestSession> findBySessionToken(String var1);

    public List<GuestSession> findByTenantIdAndRoomIdAndRevokedAtIsNull(Long var1, UUID var2);
}
