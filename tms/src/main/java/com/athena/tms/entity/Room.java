package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 房間主檔，兩系統各管一半：hk_state 僅由任務引擎改（TMS 擁有）、
 * occupancy 僅由 PMS 同步改（同步唯讀）——分兩欄讓兩邊寫入永不打架。
 */
@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
public class Room {

    @Id
    private UUID id;

    private Long tenantId;

    private String roomNo;

    /** v1 排房演算法排序鍵。 */
    private Integer floor;

    /** 平均打掃分鐘按它統計。 */
    private String roomType;

    /** 值域=common.codes.RoomHkState。 */
    private String hkState;

    /** 值域=common.codes.RoomOccupancy。 */
    private String occupancy;

    private OffsetDateTime updatedAt;
}
