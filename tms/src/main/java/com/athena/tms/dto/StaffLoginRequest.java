package com.athena.tms.dto;

public record StaffLoginRequest(String loginId, String password, String client, String hotelCode) {}
