package com.athena.tms.dao;

import com.athena.tms.entity.Room;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository
extends JpaRepository<Room, UUID> {
    public Optional<Room> findByTenantIdAndId(Long var1, UUID var2);

    public Optional<Room> findByTenantIdAndRoomNo(Long var1, String var2);

    public List<Room> findByTenantIdOrderByFloorAscRoomNoAsc(Long var1);
}
