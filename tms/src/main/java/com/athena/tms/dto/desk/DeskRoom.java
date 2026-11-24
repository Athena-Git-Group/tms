package com.athena.tms.dto.desk;

public record DeskRoom(String roomNo, boolean occupied, DeskRoomGuest guest) {}
