package com.athena.tms.dao;

import com.athena.tms.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    Optional<Room> findByTenantIdAndId(Long tenantId, UUID id);

    Optional<Room> findByTenantIdAndRoomNo(Long tenantId, String roomNo);

    /** 樓層優先排序＝v1 排房演算法的輸入順序。 */
    List<Room> findByTenantIdOrderByFloorAscRoomNoAsc(Long tenantId);
}
