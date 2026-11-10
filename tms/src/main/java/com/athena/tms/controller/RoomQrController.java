package com.athena.tms.controller;

import com.athena.tms.dto.QrRotateResponse;
import com.athena.tms.dto.RoomQrResponse;
import com.athena.tms.service.RoomQrService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoomQrController {
    private final RoomQrService roomQrService;

    public RoomQrController(RoomQrService roomQrService) {
        this.roomQrService = roomQrService;
    }

    @GetMapping(value={"/admin/rooms/{roomNo}/qr"})
    public RoomQrResponse printData(@PathVariable String roomNo) {
        return this.roomQrService.printData(roomNo);
    }

    @PostMapping(value={"/admin/rooms/{roomNo}/qr/rotate"})
    public QrRotateResponse rotate(@PathVariable String roomNo) {
        return this.roomQrService.rotate(roomNo);
    }
}
