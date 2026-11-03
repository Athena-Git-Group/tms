/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.athena.tms.common.CurrentActor
 *  com.athena.tms.common.TenantContext
 *  com.athena.tms.common.error.NotFoundException
 *  com.athena.tms.dao.RoomRepository
 *  com.athena.tms.entity.Room
 *  org.springframework.boot.test.context.TestConfiguration
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PathVariable
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.RestController
 */
package com.athena.tms.auth;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.dao.RoomRepository;
import com.athena.tms.entity.Room;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@TestConfiguration(proxyBeanMethods=false)
public class AuthTestEndpointsConfiguration {

    @RestController
    static class ProbeController {
        private final RoomRepository roomRepository;
        private final JdbcTemplate jdbcTemplate;

        ProbeController(RoomRepository roomRepository, DataSource dataSource) {
            this.roomRepository = roomRepository;
            this.jdbcTemplate = new JdbcTemplate(dataSource);
        }

        @GetMapping(value={"/staff/probe"})
        Map<String, String> staffRead() {
            return Map.of("probe", "staff-read");
        }

        @PostMapping(value={"/staff/probe"})
        Map<String, String> staffWrite() {
            return Map.of("probe", "staff-write");
        }

        @GetMapping(value={"/desk/probe"})
        Map<String, String> deskRead() {
            return Map.of("probe", "desk-read");
        }

        @PostMapping(value={"/desk/probe"})
        Map<String, String> deskWrite() {
            return Map.of("probe", "desk-write");
        }

        @GetMapping(value={"/admin/probe"})
        Map<String, String> adminRead() {
            return Map.of("probe", "admin-read");
        }

        @PostMapping(value={"/admin/probe"})
        Map<String, String> adminWrite() {
            return Map.of("probe", "admin-write");
        }

        @GetMapping(value={"/owner/probe"})
        Map<String, String> ownerRead() {
            return Map.of("probe", "owner-read");
        }

        @PostMapping(value={"/owner/probe"})
        Map<String, String> ownerWrite() {
            return Map.of("probe", "owner-write");
        }

        @PostMapping(value={"/guest/probe"})
        Map<String, String> guestWrite() {
            return Map.of("probe", "guest-write");
        }

        @GetMapping(value={"/guest/context"})
        Map<String, Object> guestContext() {
            CurrentActor actor = CurrentActor.require();
            return Map.of("tenantId", actor.tenantId(), "roomId", String.valueOf(actor.roomId()), "reservationId", String.valueOf(actor.reservationId()));
        }

        @GetMapping(value={"/staff/rooms/{id}"})
        Map<String, String> room(@PathVariable UUID id) {
            Room room = (Room)this.roomRepository.findByTenantIdAndId(TenantContext.require(), id).orElseThrow(() -> new NotFoundException("\u623f\u9593\u4e0d\u5b58\u5728"));
            return Map.of("id", room.getId().toString(), "roomNo", room.getRoomNo());
        }

        @GetMapping(value={"/staff/rls-room-count"})
        Map<String, Object> rlsRoomCount() {
            Integer visible = (Integer)this.jdbcTemplate.queryForObject("SELECT count(*) FROM rooms", Integer.class);
            return Map.of("visibleRooms", visible);
        }
    }
}
