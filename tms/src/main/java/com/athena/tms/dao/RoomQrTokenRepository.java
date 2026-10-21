package com.athena.tms.dao;

import com.athena.tms.entity.RoomQrToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoomQrTokenRepository extends JpaRepository<RoomQrToken, UUID> {

    /** 唯一不帶 tenant 的查法：掃碼入口沒有租戶脈絡，token 全域 UNIQUE 本身就是查找鍵。 */
    Optional<RoomQrToken> findByToken(String token);

    /** status='active' 時 partial unique 保證至多一枚，Optional 成立。 */
    Optional<RoomQrToken> findByTenantIdAndRoomIdAndStatus(Long tenantId, UUID roomId, String status);
}
