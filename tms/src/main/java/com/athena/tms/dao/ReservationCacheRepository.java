package com.athena.tms.dao;

import com.athena.tms.entity.ReservationCache;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationCacheRepository
extends JpaRepository<ReservationCache, UUID> {
    public Optional<ReservationCache> findByTenantIdAndId(Long var1, UUID var2);

    public Optional<ReservationCache> findByTenantIdAndReservationRef(Long var1, String var2);

    @Query(value="select r from ReservationCache r\nwhere r.tenantId = :tenantId\n  and r.roomId = :roomId\n  and r.supersededAt is null\n  and r.checkinAt <= :at\n  and r.checkoutAt > :at\n")
    public Optional<ReservationCache> findCurrentByRoom(@Param(value="tenantId") Long var1, @Param(value="roomId") UUID var2, @Param(value="at") OffsetDateTime var3);

    @Query(value="select r from ReservationCache r\nwhere r.tenantId = :tenantId\n  and r.supersededAt is null\n  and r.checkoutAt >= :from\n  and r.checkoutAt < :to\norder by r.checkoutAt, r.roomId\n")
    public List<ReservationCache> findCheckoutsBetween(@Param(value="tenantId") Long var1, @Param(value="from") OffsetDateTime var2, @Param(value="to") OffsetDateTime var3);
}
