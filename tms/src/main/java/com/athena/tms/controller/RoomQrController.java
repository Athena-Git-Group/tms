package com.athena.tms.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import com.athena.tms.dto.QrRotateResponse;
import com.athena.tms.dto.RoomQrResponse;
import com.athena.tms.service.RoomQrService;

/**
 * 房間 QR 的主管端點。路徑用房號不用房 uuid——主管手上拿的是
 * 「305 的貼紙被撕了」這種資訊，要求他先查 uuid 才能輪替是折磨人；
 * 房號在租戶內唯一，配上 TenantContext 定位不出歧義。
 */
@RestController
public class RoomQrController {

    private final RoomQrService roomQrService;

    public RoomQrController(RoomQrService roomQrService) {
        this.roomQrService = roomQrService;
    }

    @GetMapping("/admin/rooms/{roomNo}/qr")
    public RoomQrResponse printData(@PathVariable String roomNo) {
        return roomQrService.printData(roomNo);
    }

    @PostMapping("/admin/rooms/{roomNo}/qr/rotate")
    public QrRotateResponse rotate(@PathVariable String roomNo) {
        return roomQrService.rotate(roomNo);
    }
}
