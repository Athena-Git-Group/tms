package com.athena.tms.bdd.steps.desk;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.DeskApi;
import com.athena.tms.bdd.support.DeskContext;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.RoomFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import io.cucumber.java.zh_tw.當;
import java.util.Arrays;
import java.util.UUID;

public class DeskCreateRequestCommonSteps {

    private final DeskApi deskApi;
    private final DeskContext deskContext;
    private final ScenarioWorld world;
    private final RoomFixtures roomFixtures;
    private final TenantFixtures tenantFixtures;
    private final LastOperationGuard lastOperationGuard;

    public DeskCreateRequestCommonSteps(
            DeskApi deskApi,
            DeskContext deskContext,
            ScenarioWorld world,
            RoomFixtures roomFixtures,
            TenantFixtures tenantFixtures,
            LastOperationGuard lastOperationGuard) {
        this.deskApi = deskApi;
        this.deskContext = deskContext;
        this.world = world;
        this.roomFixtures = roomFixtures;
        this.tenantFixtures = tenantFixtures;
        this.lastOperationGuard = lastOperationGuard;
    }

    @假設("^飯店的客房有 ([^、\\s]+(?:、[^、\\s]+)*)$")
    public void hotelRoomsAre(String roomList) {
        for (String roomNo : Arrays.stream(roomList.split("、")).map(String::trim).toList()) {
            roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
        }
    }

    @假設("{string} 已代開 {string} 的以下單：")
    public void alreadyCreatedRequests(String staffName, String roomNo, DataTable table) {
        DeskCreateRequestRows.Request request = DeskCreateRequestRows.parse(table);
        ScenarioWorld.ApiResponse response = deskApi.createRequests(
                staffName, roomNo, request.items(), request.other(), UUID.randomUUID().toString());
        DeskCreateRequestRows.requireStatus(response, 201, "「" + staffName + "」代開「" + roomNo + "」");
        DeskCreateRequestRows.rememberCreated(world, roomNo, response);
    }

    @當("{string} 代開 {string} 的以下單：")
    public void createRequests(String staffName, String roomNo, DataTable table) {
        DeskCreateRequestRows.Request request = DeskCreateRequestRows.parse(table);
        lastOperationGuard.captureCreateRequest(tenantFixtures.testTenantId());
        ScenarioWorld.ApiResponse response = deskApi.createRequests(
                staffName, roomNo, request.items(), request.other(), UUID.randomUUID().toString());
        DeskCreateRequestRows.rememberCreated(world, roomNo, response);
    }
}
