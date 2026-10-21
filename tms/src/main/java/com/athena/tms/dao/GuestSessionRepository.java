package com.athena.tms.dao;

import com.athena.tms.entity.GuestSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuestSessionRepository extends JpaRepository<GuestSession, UUID> {

    /** 不帶 tenant：住客請求沒有租戶脈絡，session_token 全域 UNIQUE；驗證規則為未過期且未撤銷。 */
    Optional<GuestSession> findBySessionToken(String sessionToken);

    /** QR 輪替連坐撤銷的對象集合：該房全部未撤銷的住客工作階段。 */
    List<GuestSession> findByTenantIdAndRoomIdAndRevokedAtIsNull(Long tenantId, UUID roomId);
}
