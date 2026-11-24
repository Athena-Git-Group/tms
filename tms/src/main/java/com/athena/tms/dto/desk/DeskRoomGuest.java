package com.athena.tms.dto.desk;

import java.time.LocalDate;

public record DeskRoomGuest(String name, boolean vip, LocalDate checkinDate, LocalDate checkoutDate) {}
