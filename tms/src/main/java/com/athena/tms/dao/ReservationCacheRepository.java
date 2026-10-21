package com.athena.tms.dao;

import com.athena.tms.entity.ReservationCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationCacheRepository extends JpaRepository<ReservationCache, UUID> {

    Optional<ReservationCache> findByTenantIdAndId(Long tenantId, UUID id);

    Optional<ReservationCache> findByTenantIdAndReservationRef(Long tenantId, String reservationRef);

    /**
     * 當前訂單：superseded_at IS NULL＋住期含指定時點（spec §5.1 存取樣式）。
     * EXCLUDE 約束保證同房未作廢住期不重疊，所以至多一筆、Optional 成立。
     * checkout 用嚴格小於：退房時刻起這間房就不屬於這張訂單（半開區間與 DB tstzrange 一致）。
     */
    @Query("""
            select r from ReservationCache r
            where r.tenantId = :tenantId
              and r.roomId = :roomId
              and r.supersededAt is null
              and r.checkinAt <= :at
              and r.checkoutAt > :at
            """)
    Optional<ReservationCache> findCurrentByRoom(
            @Param("tenantId") Long tenantId, @Param("roomId") UUID roomId, @Param("at") OffsetDateTime at);

    /** 今日退房（班末掃描／排房輸入）：checkout 落在 [from, to) 的未作廢訂單。 */
    @Query("""
            select r from ReservationCache r
            where r.tenantId = :tenantId
              and r.supersededAt is null
              and r.checkoutAt >= :from
              and r.checkoutAt < :to
            order by r.checkoutAt, r.roomId
            """)
    List<ReservationCache> findCheckoutsBetween(
            @Param("tenantId") Long tenantId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);
}
