package com.athena.tms.controller;

import com.athena.tms.common.TenantContext;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.dao.RoomRepository;
import com.athena.tms.entity.Room;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 房間查詢 API（contracts readRooms）。目前只有這一個讀取端點——其他房況異動
 * （查房、完修轉停用待確認等）都是現場動作的副作用，不走這裡另開寫入路徑。
 */
@RestController
@RequestMapping("/api/v1")
public class RoomController {

    private final RoomRepository roomRepository;

    public RoomController(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @GetMapping("/rooms/{id}")
    public Map<String, Object> readRooms(@PathVariable UUID id) {
        long tenantId = TenantContext.require();
        Room room = roomRepository
                .findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new NotFoundException("查無房間: " + id));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", room.getId().toString());
        body.put("tenantId", room.getTenantId());
        body.put("roomNo", room.getRoomNo());
        body.put("floor", room.getFloor());
        body.put("roomType", room.getRoomType());
        body.put("hkState", room.getHkState());
        body.put("occupancy", room.getOccupancy());
        body.put("updatedAt", room.getUpdatedAt() == null ? null : room.getUpdatedAt().toString());
        return body;
    }
}
